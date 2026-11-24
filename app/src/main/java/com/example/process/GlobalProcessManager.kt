package com.example.process

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.FreezerDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Global Process Manager — Single Source of Truth for all Smart Loading & Verified Completion workflows.
 *
 * Anti-Fake-Loading Guarantees:
 * 1. Progress NEVER advances via timers; percentage ONLY moves when real work calls `updateProgress`.
 * 2. Progress is strictly capped at <= 95% until `ProcessVerificationReport.allRequiredPassed == true`.
 * 3. Only `GlobalProcessStatus.COMPLETED` is permitted to display 100% and `✓ SELESAI!`.
 * 4. Duplicate taps with the same `idempotencyKey` are blocked while a process is active.
 * 5. Completed stage checkpoints (`completedStageIds`) are preserved across retries so "COBA LAGI"
 *    skips already-succeeded stages (no duplicate records or redundant compression).
 * 6. Active process state is persisted to SharedPreferences so an unexpected app kill mid-process
 *    is detected on next launch instead of silently being treated as complete.
 */
class GlobalProcessManager(
    private val context: Context,
    val verificationEngine: ProcessVerificationEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val defaultAutoDismissMillis: Long = 1300L
) {
    constructor(context: Context, dao: FreezerDao, defaultAutoDismissMillis: Long = 1300L) : this(
        context = context,
        verificationEngine = ProcessVerificationEngine(dao),
        defaultAutoDismissMillis = defaultAutoDismissMillis
    )

    companion object {
        private const val PREFS_NAME = "sewain_global_process_manager_prefs"
        private const val KEY_ACTIVE_PROCESS_JSON = "active_in_flight_process_json"
        private const val MAX_PRE_VERIFICATION_PERCENT = 95
        private const val MAX_PARTIAL_SUCCESS_PERCENT = 85
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _processState = MutableStateFlow<GlobalProcessState?>(null)
    val processState: StateFlow<GlobalProcessState?> = _processState.asStateFlow()

    private val _interruptedProcessNotice = MutableStateFlow<GlobalProcessState?>(null)
    val interruptedProcessNotice: StateFlow<GlobalProcessState?> = _interruptedProcessNotice.asStateFlow()

    private val activeIdempotencyKeys: MutableSet<String> =
        java.util.Collections.newSetFromMap(ConcurrentHashMap())

    // Preserves completed stages & cached artifacts across retries for the same idempotencyKey
    private val checkpointStore = ConcurrentHashMap<String, MutableSet<String>>()
    private val artifactStore = ConcurrentHashMap<String, ConcurrentHashMap<String, Any>>()

    private var activeRetryAction: (() -> Unit)? = null
    private var activeCancelJob: Job? = null
    private var autoDismissJob: Job? = null

    init {
        checkInterruptedProcessOnStartup()
    }

    /**
     * Detects if the app was force-closed or crashed while a critical process was still running.
     * Ensures an interrupted process is NEVER silently considered complete.
     */
    fun checkInterruptedProcessOnStartup(): GlobalProcessState? {
        val raw = prefs.getString(KEY_ACTIVE_PROCESS_JSON, "").orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            val interrupted = GlobalProcessState(
                processId = obj.optString("processId", UUID.randomUUID().toString()),
                idempotencyKey = obj.optString("idempotencyKey", ""),
                processName = obj.optString("processName", "Proses Sebelumnya"),
                currentStep = obj.optInt("currentStep", 1),
                totalSteps = obj.optInt("totalSteps", 1).coerceAtLeast(1),
                progressPercentage = obj.optInt("progressPercentage", 0).coerceIn(0, MAX_PRE_VERIFICATION_PERCENT),
                isIndeterminate = false,
                currentActivityMessage = obj.optString("currentActivityMessage", "Proses terhenti sebelum selesai"),
                processStatus = GlobalProcessStatus.FAILED,
                stageBadge = ProcessStageBadge.FAILED,
                failedStageName = obj.optString("currentActivityMessage", "Tahap tidak tuntas"),
                errorMessage = "Aplikasi sempat ditutup saat proses '${obj.optString("processName")}' sedang berlangsung (${obj.optInt("progressPercentage")}%). Silakan periksa data & lakukan sinkronisasi ulang agar konsisten.",
                startedAt = obj.optLong("startedAt", System.currentTimeMillis()),
                completedAt = System.currentTimeMillis(),
                canRetry = false,
                canCancel = false,
                isVisible = false
            )
            clearPersistedInFlightState()
            _interruptedProcessNotice.value = interrupted
            interrupted
        }.getOrNull()
    }

    fun dismissInterruptedProcessNotice() {
        _interruptedProcessNotice.value = null
    }

    fun isKeyInFlight(idempotencyKey: String): Boolean =
        idempotencyKey.isNotBlank() && activeIdempotencyKeys.contains(idempotencyKey)

    fun isAnyProcessActive(): Boolean = _processState.value?.isActive == true

    inner class ProcessHandle internal constructor(
        val processId: String,
        val idempotencyKey: String,
        val processName: String,
        val totalSteps: Int
    ) {
        @Volatile
        var lastStageLabel: String = "Inisialisasi proses"
            private set

        fun isStageCompleted(stageId: String): Boolean =
            checkpointStore[idempotencyKey]?.contains(stageId) == true

        fun markStageCompleted(stageId: String) {
            checkpointStore.computeIfAbsent(idempotencyKey) {
                java.util.Collections.newSetFromMap(ConcurrentHashMap())
            }.add(stageId)
            val cur = _processState.value
            if (cur != null && cur.processId == processId) {
                val updated = cur.copy(completedStageIds = checkpointStore[idempotencyKey]?.toSet().orEmpty())
                _processState.value = updated
                persistInFlightState(updated)
            }
        }

        fun putArtifact(key: String, value: Any) {
            artifactStore.computeIfAbsent(idempotencyKey) { ConcurrentHashMap() }[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        fun <A : Any> getArtifact(key: String): A? =
            artifactStore[idempotencyKey]?.get(key) as? A

        /**
         * Updates real progress during PROCESSING.
         * Strictly clamps percentage to [0..95] so 100% can NEVER be shown prematurely.
         */
        fun updateProgress(
            step: Int,
            percentage: Int,
            message: String,
            badge: ProcessStageBadge = ProcessStageBadge.PROCESSING,
            isIndeterminate: Boolean = false
        ) {
            lastStageLabel = message
            val clampedPct = percentage.coerceIn(0, MAX_PRE_VERIFICATION_PERCENT)
            val clampedStep = step.coerceIn(0, totalSteps)
            val cur = _processState.value
            if (cur == null || cur.processId != processId) return
            val updated = cur.copy(
                currentStep = clampedStep,
                progressPercentage = clampedPct,
                isIndeterminate = isIndeterminate,
                currentActivityMessage = message,
                processStatus = GlobalProcessStatus.PROCESSING,
                stageBadge = badge,
                completedStageIds = checkpointStore[idempotencyKey]?.toSet().orEmpty()
            )
            _processState.value = updated
            persistInFlightState(updated)
        }

        /**
         * Enters explicit verification stage (90..95%) before any terminal status can be decided.
         */
        fun enterVerification(
            step: Int = totalSteps,
            percentage: Int = 92,
            message: String = "Memverifikasi konsistensi data lokal & cloud..."
        ) {
            lastStageLabel = message
            val clampedPct = percentage.coerceIn(90, MAX_PRE_VERIFICATION_PERCENT)
            val cur = _processState.value
            if (cur == null || cur.processId != processId) return
            val updated = cur.copy(
                currentStep = step.coerceIn(0, totalSteps),
                progressPercentage = clampedPct,
                isIndeterminate = false,
                currentActivityMessage = message,
                processStatus = GlobalProcessStatus.VERIFYING,
                stageBadge = ProcessStageBadge.VERIFYING,
                completedStageIds = checkpointStore[idempotencyKey]?.toSet().orEmpty()
            )
            _processState.value = updated
            persistInFlightState(updated)
        }
    }

    /**
     * Executes a tracked, idempotent, verified process.
     * Returns `null` if an identical `idempotencyKey` is already running (anti double-submit guard).
     */
    suspend fun <T> executeTrackedProcess(
        processName: String,
        idempotencyKey: String,
        totalSteps: Int,
        initialMessage: String = "Mempersiapkan proses...",
        initialIndeterminate: Boolean = false,
        canCancel: Boolean = false,
        autoDismissDelayMillis: Long = defaultAutoDismissMillis,
        retryAction: (() -> Unit)? = null,
        onVerifiedComplete: ((T?) -> Unit)? = null,
        block: suspend (ProcessHandle) -> ProcessExecutionOutcome<T>
    ): GlobalProcessState? {
        val cleanKey = idempotencyKey.ifBlank { "proc_${UUID.randomUUID()}" }
        if (!activeIdempotencyKeys.add(cleanKey)) {
            // Anti double-submit: ignore duplicate trigger while same operation is still running
            return null
        }

        autoDismissJob?.cancel()
        val processId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val handle = ProcessHandle(
            processId = processId,
            idempotencyKey = cleanKey,
            processName = processName,
            totalSteps = totalSteps.coerceAtLeast(1)
        )
        activeRetryAction = retryAction

        val initialState = GlobalProcessState(
            processId = processId,
            idempotencyKey = cleanKey,
            processName = processName,
            currentStep = 0,
            totalSteps = totalSteps.coerceAtLeast(1),
            progressPercentage = 0,
            isIndeterminate = initialIndeterminate,
            currentActivityMessage = initialMessage,
            processStatus = GlobalProcessStatus.PROCESSING,
            stageBadge = ProcessStageBadge.PROCESSING,
            startedAt = startedAt,
            completedStageIds = checkpointStore[cleanKey]?.toSet().orEmpty(),
            canRetry = false,
            canCancel = canCancel,
            autoDismissDelayMillis = autoDismissDelayMillis,
            isVisible = true
        )
        _processState.value = initialState
        persistInFlightState(initialState)

        return try {
            val outcome = block(handle)
            val report = outcome.verificationReport
            val finishedAt = System.currentTimeMillis()

            val finalState = when {
                report.allRequiredPassed && outcome.failureMessage.isBlank() -> {
                    // Clear checkpoints & artifacts only after 100% verified completion
                    checkpointStore.remove(cleanKey)
                    artifactStore.remove(cleanKey)
                    activeRetryAction = null
                    GlobalProcessState(
                        processId = processId,
                        idempotencyKey = cleanKey,
                        processName = processName,
                        currentStep = totalSteps.coerceAtLeast(1),
                        totalSteps = totalSteps.coerceAtLeast(1),
                        progressPercentage = 100,
                        isIndeterminate = false,
                        currentActivityMessage = "Seluruh data berhasil disimpan dan diverifikasi.",
                        processStatus = GlobalProcessStatus.COMPLETED,
                        stageBadge = ProcessStageBadge.COMPLETED,
                        startedAt = startedAt,
                        completedAt = finishedAt,
                        completedStageIds = emptySet(),
                        verificationReport = report,
                        canRetry = false,
                        canCancel = false,
                        autoDismissDelayMillis = autoDismissDelayMillis,
                        isVisible = true
                    )
                }

                report.isLocalSavedCloudIncomplete && outcome.failureMessage.isBlank() -> {
                    val curPct = (_processState.value?.progressPercentage ?: 70)
                        .coerceIn(50, MAX_PARTIAL_SUCCESS_PERCENT)
                    GlobalProcessState(
                        processId = processId,
                        idempotencyKey = cleanKey,
                        processName = processName,
                        currentStep = (_processState.value?.currentStep ?: 1).coerceAtMost((totalSteps - 1).coerceAtLeast(1)),
                        totalSteps = totalSteps.coerceAtLeast(1),
                        progressPercentage = curPct,
                        isIndeterminate = false,
                        currentActivityMessage = outcome.partialSummaryMessage.ifBlank {
                            "Sinkronisasi Google Drive belum berhasil. Data tetap aman di perangkat dan akan dicoba disinkronkan kembali."
                        },
                        processStatus = GlobalProcessStatus.PARTIAL_SUCCESS,
                        stageBadge = ProcessStageBadge.PARTIAL_LOCAL,
                        failedStageName = outcome.failureStageName.ifBlank { "Sinkronisasi Cloud / Google Drive" },
                        errorMessage = outcome.partialSummaryMessage.ifBlank {
                            report.googleDriveDetail.ifBlank { report.synchronizationDetail }
                        },
                        startedAt = startedAt,
                        completedAt = finishedAt,
                        completedStageIds = checkpointStore[cleanKey]?.toSet().orEmpty(),
                        verificationReport = report,
                        canRetry = retryAction != null,
                        canCancel = false,
                        autoDismissDelayMillis = 0L, // NEVER auto-dismiss on partial success
                        isVisible = true
                    )
                }

                else -> {
                    val curPct = (_processState.value?.progressPercentage ?: 0)
                        .coerceIn(0, MAX_PRE_VERIFICATION_PERCENT - 5)
                    val reason = outcome.failureMessage.ifBlank {
                        when {
                            report.localDatabaseStatus == VerificationItemStatus.FAILED -> report.localDatabaseDetail
                            report.documentationStatus == VerificationItemStatus.FAILED -> report.documentationDetail
                            report.googleDriveStatus == VerificationItemStatus.FAILED -> report.googleDriveDetail
                            else -> "Proses belum lulus verifikasi akhir."
                        }
                    }
                    GlobalProcessState(
                        processId = processId,
                        idempotencyKey = cleanKey,
                        processName = processName,
                        currentStep = _processState.value?.currentStep ?: 0,
                        totalSteps = totalSteps.coerceAtLeast(1),
                        progressPercentage = curPct,
                        isIndeterminate = false,
                        currentActivityMessage = "Gagal pada tahap: ${outcome.failureStageName.ifBlank { handle.lastStageLabel }}",
                        processStatus = GlobalProcessStatus.FAILED,
                        stageBadge = ProcessStageBadge.FAILED,
                        failedStageName = outcome.failureStageName.ifBlank { handle.lastStageLabel },
                        errorMessage = reason,
                        startedAt = startedAt,
                        completedAt = finishedAt,
                        completedStageIds = checkpointStore[cleanKey]?.toSet().orEmpty(),
                        verificationReport = report,
                        canRetry = retryAction != null,
                        canCancel = false,
                        autoDismissDelayMillis = 0L, // NEVER auto-dismiss on failure
                        isVisible = true
                    )
                }
            }

            clearPersistedInFlightState()
            _processState.value = finalState

            if (finalState.processStatus == GlobalProcessStatus.COMPLETED) {
                onVerifiedComplete?.invoke(outcome.result)
                if (autoDismissDelayMillis > 0L) {
                    autoDismissJob = scope.launch {
                        delay(autoDismissDelayMillis)
                        val latest = _processState.value
                        if (latest != null && latest.processId == processId && latest.processStatus == GlobalProcessStatus.COMPLETED) {
                            _processState.value = latest.copy(isVisible = false)
                        }
                    }
                }
            } else if (finalState.processStatus == GlobalProcessStatus.PARTIAL_SUCCESS) {
                // Local data is safely verified on device; caller may still update local UI state
                onVerifiedComplete?.invoke(outcome.result)
            }

            finalState
        } catch (ce: CancellationException) {
            clearPersistedInFlightState()
            val cancelledState = GlobalProcessState(
                processId = processId,
                idempotencyKey = cleanKey,
                processName = processName,
                currentStep = _processState.value?.currentStep ?: 0,
                totalSteps = totalSteps.coerceAtLeast(1),
                progressPercentage = (_processState.value?.progressPercentage ?: 0).coerceIn(0, MAX_PRE_VERIFICATION_PERCENT - 5),
                isIndeterminate = false,
                currentActivityMessage = "Proses dibatalkan oleh pengguna.",
                processStatus = GlobalProcessStatus.CANCELLED,
                stageBadge = ProcessStageBadge.CANCELLED,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                completedStageIds = checkpointStore[cleanKey]?.toSet().orEmpty(),
                canRetry = retryAction != null,
                canCancel = false,
                autoDismissDelayMillis = 0L,
                isVisible = true
            )
            _processState.value = cancelledState
            throw ce
        } catch (e: Exception) {
            clearPersistedInFlightState()
            val curPct = (_processState.value?.progressPercentage ?: 0).coerceIn(0, MAX_PRE_VERIFICATION_PERCENT - 5)
            val errMsg = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Terjadi kesalahan tidak terduga saat memproses data."
            val failedState = GlobalProcessState(
                processId = processId,
                idempotencyKey = cleanKey,
                processName = processName,
                currentStep = _processState.value?.currentStep ?: 0,
                totalSteps = totalSteps.coerceAtLeast(1),
                progressPercentage = curPct,
                isIndeterminate = false,
                currentActivityMessage = "Gagal pada tahap: ${handle.lastStageLabel}",
                processStatus = GlobalProcessStatus.FAILED,
                stageBadge = ProcessStageBadge.FAILED,
                failedStageName = handle.lastStageLabel,
                errorMessage = errMsg,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                completedStageIds = checkpointStore[cleanKey]?.toSet().orEmpty(),
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = if (handle.isStageCompleted("LOCAL_DB")) {
                        VerificationItemStatus.SUCCESS
                    } else {
                        VerificationItemStatus.FAILED
                    },
                    localDatabaseDetail = if (handle.isStageCompleted("LOCAL_DB")) {
                        "Data lokal sudah tersimpan aman sebelum kegagalan tahap lanjutan."
                    } else {
                        errMsg
                    },
                    documentationStatus = if (handle.isStageCompleted("DOCS")) {
                        VerificationItemStatus.SUCCESS
                    } else {
                        VerificationItemStatus.PENDING
                    },
                    googleDriveStatus = VerificationItemStatus.FAILED,
                    googleDriveDetail = "Tahap dihentikan karena kegagalan sebelumnya.",
                    synchronizationStatus = VerificationItemStatus.FAILED,
                    synchronizationDetail = "Belum tersinkronisasi."
                ),
                canRetry = retryAction != null,
                canCancel = false,
                autoDismissDelayMillis = 0L,
                isVisible = true
            )
            _processState.value = failedState
            failedState
        } finally {
            activeIdempotencyKeys.remove(cleanKey)
            activeCancelJob = null
        }
    }

    fun attachCancellableJob(job: Job) {
        activeCancelJob = job
    }

    fun cancelActiveProcess() {
        val current = _processState.value ?: return
        if (current.isActive && current.canCancel) {
            activeCancelJob?.cancel()
        }
    }

    fun retryActiveProcess() {
        val current = _processState.value ?: return
        if (current.isActive) return
        val retry = activeRetryAction ?: return
        retry.invoke()
    }

    /**
     * Dismisses the dialog ONLY when the process has reached a terminal state
     * (COMPLETED, PARTIAL_SUCCESS, FAILED, or CANCELLED).
     * Never allows closing while backend work is still actively running.
     */
    fun dismissDialog() {
        val current = _processState.value ?: return
        if (current.isActive) return
        autoDismissJob?.cancel()
        if (current.processStatus == GlobalProcessStatus.COMPLETED ||
            current.processStatus == GlobalProcessStatus.CANCELLED
        ) {
            checkpointStore.remove(current.idempotencyKey)
            artifactStore.remove(current.idempotencyKey)
        }
        _processState.value = current.copy(isVisible = false)
    }

    fun clearCheckpointsForKey(idempotencyKey: String) {
        checkpointStore.remove(idempotencyKey)
        artifactStore.remove(idempotencyKey)
    }

    private fun persistInFlightState(state: GlobalProcessState) {
        runCatching {
            val obj = JSONObject().apply {
                put("processId", state.processId)
                put("idempotencyKey", state.idempotencyKey)
                put("processName", state.processName)
                put("currentStep", state.currentStep)
                put("totalSteps", state.totalSteps)
                put("progressPercentage", state.progressPercentage)
                put("currentActivityMessage", state.currentActivityMessage)
                put("startedAt", state.startedAt)
                put("completedStages", JSONArray(state.completedStageIds.toList()))
            }
            prefs.edit().putString(KEY_ACTIVE_PROCESS_JSON, obj.toString()).commit()
        }
    }

    private fun clearPersistedInFlightState() {
        runCatching {
            prefs.edit().remove(KEY_ACTIVE_PROCESS_JSON).commit()
        }
    }
}
