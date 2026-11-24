package com.example.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import com.example.data.local.CloudMigrationStateEntity
import com.example.data.local.CloudSyncQueueEntity
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerReturnEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentMethodEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.SmartNotificationSettings
import com.example.data.local.TransactionSignatureEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitPaymentAllocationEntity
import com.example.drive.DatabaseGenerationManager
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.MirrorOutboxCodec
import com.example.util.DateCurrencyUtils
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class CloudSyncAndMigrationManager(
    private val context: Context,
    private val dao: FreezerDao,
    private val cloudRepo: CloudFirestoreRepository,
    private val photoManager: CloudStoragePhotoManager,
    private val networkAvailabilityOverride: (() -> Boolean)? = null
) {
    companion object {
        private const val TAG = "CloudSyncManager"

        // User-facing overall sync banner states
        const val STATUS_SYNCED = "Tersinkronisasi"
        const val STATUS_SYNCING = "Sedang menyinkronkan"
        const val STATUS_WAITING_NETWORK = "Menunggu koneksi internet"
        const val STATUS_FAILED = "Sinkronisasi gagal"

        // Explicit per-record & outbox sync states
        const val SYNC_STATE_PENDING_SYNC = "PENDING_SYNC"
        const val SYNC_STATE_SYNCING = "SYNCING"
        const val SYNC_STATE_SYNCED = "SYNCED"
        const val SYNC_STATE_PENDING_DELETE = "PENDING_DELETE"
        const val SYNC_STATE_FAILED = "FAILED"

        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"

        const val TYPE_CUSTOMER = "CUSTOMER"
        const val TYPE_UNIT = "UNIT"
        const val TYPE_TRANSACTION = "TRANSACTION"
        const val TYPE_TRANSACTION_UNIT_ITEM = "TRANSACTION_UNIT_ITEM"
        const val TYPE_PAYMENT_PROOF = "PAYMENT_PROOF"
        const val TYPE_UNIT_PAYMENT_ALLOCATION = "UNIT_PAYMENT_ALLOCATION"
        const val TYPE_SIGNATURE = "SIGNATURE"
        const val TYPE_RETURN = "RETURN"
        const val TYPE_COMPANY_SETTINGS = "COMPANY_SETTINGS"

        private const val ECHO_GUARD_WINDOW_MS = 10_000L
        private const val BASE_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L

        fun computeExponentialBackoffMillis(retryCount: Int): Long {
            if (retryCount <= 0) return 0L
            val shift = (retryCount - 1).coerceIn(0, 6)
            return (BASE_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
        }

        fun stepDescription(step: Int): String = when (step) {
            1 -> "1/10 Sinkronisasi profil akun Google"
            2 -> "2/10 Migrasi pengaturan perusahaan & perjanjian sewa"
            3 -> "3/10 Antrean, kompresi & unggah foto pelanggan dan unit"
            4 -> "4/10 Migrasi master data pelanggan (dengan URL foto terverifikasi)"
            5 -> "5/10 Migrasi inventaris unit freezer (dengan URL foto terverifikasi)"
            6 -> "6/10 Antrean & unggah foto dokumentasi transaksi dan bukti bayar"
            7 -> "7/10 Migrasi transaksi & daftar unit kontrak"
            8 -> "8/10 Migrasi bukti bayar, alokasi, tanda tangan & pengembalian"
            9 -> "9/10 Finalisasi antrean foto Cloud Storage & metadata"
            10 -> "10/10 Verifikasi jumlah data SQLite vs Cloud"
            else -> "Menunggu memulai migrasi"
        }
    }

    private val _syncStatus = MutableStateFlow(STATUS_SYNCED)
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow("")
    val lastSyncTimestamp: StateFlow<String> = _lastSyncTimestamp.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _isOnline = MutableStateFlow(checkNetworkConnected())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /**
     * Single-flight mutex shared between Push (local -> cloud) and Pull (cloud -> local)
     * so concurrent CRUD writes and Firestore snapshot callbacks NEVER race or corrupt Room rows.
     */
    private val syncMutex = Mutex()
    private val migrationMutex = Mutex()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeDrainJob: Job? = null

    /**
     * Anti-echo tracking: records currently being pushed or just pushed locally
     * so immediate Firestore snapshot callbacks never bounce back into local SQLite.
     */
    private val inFlightPushKeys: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val recentlyPushedAtMillis = ConcurrentHashMap<String, Long>()
    private val nextRetryAtMillisByItemId = ConcurrentHashMap<Long, Long>()

    private var activeUid: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var listenerJobs: MutableList<Job> = mutableListOf()

    private fun pushKey(entityType: String, cloudDocId: String): String = "$entityType:$cloudDocId"

    private fun isSuppressedByRecentPush(entityType: String, cloudDocId: String, remoteEffectiveTs: Long): Boolean {
        val key = pushKey(entityType, cloudDocId)
        if (key in inFlightPushKeys) return true
        val pushedAt = recentlyPushedAtMillis[key] ?: return false
        val now = System.currentTimeMillis()
        if (now - pushedAt > ECHO_GUARD_WINDOW_MS) {
            recentlyPushedAtMillis.remove(key)
            return false
        }
        // Ignore echo if remote timestamp is not strictly newer than our just-pushed mutation
        return remoteEffectiveTs <= pushedAt
    }

    fun isNetworkConnected(): Boolean {
        val override = networkAvailabilityOverride
        if (override != null) return override()
        return checkNetworkConnected()
    }

    private fun checkNetworkConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    fun startConnectivityMonitor(scope: CoroutineScope) {
        if (networkCallback != null) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    val uid = activeUid
                    if (!uid.isNullOrBlank()) {
                        nextRetryAtMillisByItemId.clear()
                        scope.launch(Dispatchers.IO) {
                            drainPendingQueue(uid, ignoreBackoff = true)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    val connected = checkNetworkConnected()
                    _isOnline.value = connected
                    if (!connected && activeUid != null) {
                        scope.launch(Dispatchers.IO) {
                            val pending = dao.getPendingSyncQueueOnce(activeUid.orEmpty())
                            if (pending.isNotEmpty()) {
                                _syncStatus.value = STATUS_WAITING_NETWORK
                            }
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity monitor registration skipped: ${e.message}")
        }
    }

    fun stopConnectivityMonitor() {
        val cb = networkCallback ?: return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    private val hardResetPaused = java.util.concurrent.atomic.AtomicBoolean(false)

    fun isPausedForHardReset(): Boolean = hardResetPaused.get()

    fun pauseAllSyncForHardReset() {
        hardResetPaused.set(true)
        clearSessionBindings()
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(CloudFirestoreSyncWorker.UNIQUE_WORK_NAME)
        }
        _syncStatus.value = "RESETTING"
    }

    fun resumeSyncAfterHardReset() {
        hardResetPaused.set(false)
        _syncStatus.value = STATUS_SYNCED
        _lastSyncError.value = null
    }

    /**
     * Non-blocking background queue drain so UI CRUD/Delete operations return immediately
     * without waiting for network I/O, while WorkManager guarantees delivery across process restarts.
     */
    fun triggerBackgroundDrain(uid: String, ignoreBackoff: Boolean = false) {
        if (hardResetPaused.get() || uid.isBlank()) return
        CloudFirestoreSyncWorker.enqueueSync(context, uid)
        if (!isNetworkConnected()) {
            _syncStatus.value = STATUS_WAITING_NETWORK
            return
        }
        if (activeDrainJob?.isActive == true && !ignoreBackoff) return
        activeDrainJob = backgroundScope.launch {
            drainPendingQueue(uid, ignoreBackoff = ignoreBackoff)
        }
    }

    fun bindAuthenticatedSession(
        user: FirebaseUser,
        scope: CoroutineScope,
        companyProfileProvider: () -> CompanyProfile,
        perjanjianProvider: () -> String,
        paymentMethodsProvider: () -> List<PaymentMethodEntity>,
        smartNotifProvider: () -> SmartNotificationSettings,
        enableAutoRemoteMergeOnStartup: Boolean = true
    ) {
        if (hardResetPaused.get()) {
            Log.i(TAG, "bindAuthenticatedSession deferred: Hard Reset is currently active.")
            return
        }
        val uid = user.uid
        if (activeUid == uid && (!enableAutoRemoteMergeOnStartup || listenerJobs.isNotEmpty())) return
        clearSessionBindings()
        activeUid = uid

        startConnectivityMonitor(scope)

        // Always recover any stranded items and drain durable outbox when an authenticated session binds
        scope.launch(Dispatchers.IO) {
            dao.resetStuckSyncingQueueItems(uid, DateCurrencyUtils.todayIso())
            photoManager.resetStuckUploadingJobs(uid)
            drainPendingQueue(uid, ignoreBackoff = true)
        }

        if (enableAutoRemoteMergeOnStartup) {
            // Incremental Real-Time Snapshot Listeners (ADDED / MODIFIED / REMOVED)
            // Protected by syncMutex, hasPendingWrites filtering, tombstone guards, reset timestamp guards, and LWW.
            listenerJobs += scope.launch(Dispatchers.IO) {
                try {
                    cloudRepo.observeCustomerChanges(uid).collect { changes ->
                        applyRemoteCustomerChanges(uid, changes)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Customer incremental listener stopped: ${e.message}")
                }
            }

            listenerJobs += scope.launch(Dispatchers.IO) {
                try {
                    cloudRepo.observeUnitChanges(uid).collect { changes ->
                        applyRemoteUnitChanges(uid, changes)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Units incremental listener stopped: ${e.message}")
                }
            }

            listenerJobs += scope.launch(Dispatchers.IO) {
                try {
                    cloudRepo.observeTransactionChanges(uid).collect { changes ->
                        applyRemoteTransactionChanges(uid, changes)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Transactions incremental listener stopped: ${e.message}")
                }
            }

            listenerJobs += scope.launch(Dispatchers.IO) {
                try {
                    cloudRepo.observePaymentProofChanges(uid).collect { changes ->
                        applyRemotePaymentProofChanges(uid, changes)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "PaymentProofs incremental listener stopped: ${e.message}")
                }
            }

            scope.launch(Dispatchers.IO) {
                runCatching {
                    cloudRepo.upsertUserProfile(
                        CloudUserProfile(
                            id = uid,
                            userId = uid,
                            displayName = user.displayName.orEmpty().ifBlank { user.email?.substringBefore("@") ?: "Admin" },
                            email = user.email.orEmpty(),
                            photoUrl = user.photoUrl?.toString().orEmpty(),
                            lastActiveDevice = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                        )
                    )
                }
                val migrationState = dao.getCloudMigrationStateOnce(uid)
                if (migrationState == null || migrationState.status != "COMPLETED") {
                    runOrResumeMigration(
                        user = user,
                        companyProfile = companyProfileProvider(),
                        perjanjianText = perjanjianProvider(),
                        paymentMethods = paymentMethodsProvider(),
                        smartNotif = smartNotifProvider()
                    )
                }
                drainPendingQueue(uid, ignoreBackoff = true)
            }
        }
    }

    fun clearSessionBindings() {
        activeUid = null
        activeDrainJob?.cancel()
        activeDrainJob = null
        listenerJobs.forEach { it.cancel() }
        listenerJobs.clear()
        inFlightPushKeys.clear()
        recentlyPushedAtMillis.clear()
        nextRetryAtMillisByItemId.clear()
        stopConnectivityMonitor()
        _syncStatus.value = STATUS_SYNCED
        _lastSyncError.value = null
    }

    // --- Enqueue Local Mutations (UPSERT & DELETE) into Durable Room Outbox ---

    suspend fun enqueueMutation(
        uid: String,
        entityType: String,
        entityLocalId: Long,
        cloudDocId: String,
        payloadJson: String = "",
        operation: String = OP_UPSERT,
        autoDrainIfOnline: Boolean = true
    ) = withContext(Dispatchers.IO) {
        if (hardResetPaused.get() || uid.isBlank()) return@withContext
        val now = DateCurrencyUtils.todayIso()
        val nowMillis = System.currentTimeMillis()
        val normalizedOp = operation.uppercase()
        val existingQueueItem = dao.getCloudSyncQueueByDocId(uid, entityType, cloudDocId)
        val itemToStore = CloudSyncQueueEntity(
            id = existingQueueItem?.id ?: 0L,
            uid = uid,
            entityType = entityType,
            entityLocalId = entityLocalId,
            cloudDocId = cloudDocId,
            operation = normalizedOp,
            payloadJson = payloadJson,
            status = "PENDING",
            retryCount = 0,
            lastError = "",
            clientUpdatedAtMillis = nowMillis,
            createdAt = existingQueueItem?.createdAt?.ifBlank { now } ?: now,
            updatedAt = now
        )
        val storedId = dao.upsertCloudSyncQueueItem(itemToStore)
        nextRetryAtMillisByItemId.remove(storedId)
        existingQueueItem?.id?.let { nextRetryAtMillisByItemId.remove(it) }

        if (!isNetworkConnected()) {
            _syncStatus.value = STATUS_WAITING_NETWORK
            CloudFirestoreSyncWorker.enqueueSync(context, uid)
        } else if (autoDrainIfOnline) {
            drainPendingQueue(uid, ignoreBackoff = true)
        } else {
            CloudFirestoreSyncWorker.enqueueSync(context, uid)
        }
    }

    suspend fun enqueueCustomer(uid: String, customer: CustomerEntity, autoDrain: Boolean = true) {
        val docId = CloudIdMapper.customerDocId(customer.id)
        if (customer.fotoKtpDepanUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(uid, "CUSTOMER", customer.id, docId, "KTP_DEPAN", customer.fotoKtpDepanUri)
        }
        if (customer.fotoKtpBelakangUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(uid, "CUSTOMER", customer.id, docId, "KTP_BELAKANG", customer.fotoKtpBelakangUri)
        }
        if (customer.fotoPelangganUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(uid, "CUSTOMER", customer.id, docId, "FOTO_PELANGGAN", customer.fotoPelangganUri)
        }
        enqueueMutation(uid, TYPE_CUSTOMER, customer.id, docId, operation = OP_UPSERT, autoDrainIfOnline = autoDrain)
    }

    suspend fun enqueueDeleteCustomer(uid: String, customer: CustomerEntity, autoDrain: Boolean = false) {
        val docId = CloudIdMapper.customerDocId(customer.id)
        enqueueMutation(uid, TYPE_CUSTOMER, customer.id, docId, operation = OP_DELETE, autoDrainIfOnline = autoDrain)
    }

    suspend fun enqueueUnit(uid: String, unit: FreezerUnitEntity, autoDrain: Boolean = true) {
        val docId = CloudIdMapper.unitDocId(unit.id)
        if (unit.fotoUnitUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(uid, "UNIT", unit.id, docId, "FOTO_UNIT", unit.fotoUnitUri)
        }
        if (unit.fotoKondisiFisikUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(uid, "UNIT", unit.id, docId, "KONDISI_FISIK", unit.fotoKondisiFisikUri)
        }
        if (unit.fotoBagianDalamUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(uid, "UNIT", unit.id, docId, "BAGIAN_DALAM", unit.fotoBagianDalamUri)
        }
        if (unit.fotoMerekSeriUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(uid, "UNIT", unit.id, docId, "MEREK_SERI", unit.fotoMerekSeriUri)
        }
        enqueueMutation(uid, TYPE_UNIT, unit.id, docId, operation = OP_UPSERT, autoDrainIfOnline = autoDrain)
    }

    suspend fun enqueueDeleteUnit(uid: String, unit: FreezerUnitEntity, autoDrain: Boolean = false) {
        val docId = CloudIdMapper.unitDocId(unit.id)
        enqueueMutation(uid, TYPE_UNIT, unit.id, docId, operation = OP_DELETE, autoDrainIfOnline = autoDrain)
    }

    suspend fun enqueueTransaction(uid: String, tx: RentalTransactionEntity, autoDrain: Boolean = true) {
        val docId = CloudIdMapper.transactionDocId(tx.id)
        if (tx.fotoSerahTerimaUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(uid, "TRANSACTION", tx.id, docId, "SERAH_TERIMA", tx.fotoSerahTerimaUri)
        }
        if (tx.fotoFreezerDiLokasiUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(uid, "TRANSACTION", tx.id, docId, "FREEZER_DI_LOKASI", tx.fotoFreezerDiLokasiUri)
        }
        enqueueMutation(uid, TYPE_TRANSACTION, tx.id, docId, operation = OP_UPSERT, autoDrainIfOnline = autoDrain)
    }

    suspend fun enqueueDeleteTransaction(uid: String, tx: RentalTransactionEntity, autoDrain: Boolean = false) {
        val docId = CloudIdMapper.transactionDocId(tx.id)
        enqueueMutation(uid, TYPE_TRANSACTION, tx.id, docId, operation = OP_DELETE, autoDrainIfOnline = autoDrain)
    }

    suspend fun enqueuePaymentProof(uid: String, proof: PaymentProofEntity, autoDrain: Boolean = true) {
        val docId = CloudIdMapper.paymentProofDocId(proof.id)
        if (proof.fotoBuktiUri.isNotBlank()) {
            photoManager.enqueuePhotoJob(
                uid = uid,
                ownerType = "TRANSACTION",
                ownerLocalId = proof.transactionId,
                ownerCloudId = CloudIdMapper.transactionDocId(proof.transactionId),
                photoSlot = "BUKTI_BAYAR_${proof.id}",
                sourcePathOrUri = proof.fotoBuktiUri
            )
        }
        enqueueMutation(uid, TYPE_PAYMENT_PROOF, proof.id, docId, operation = OP_UPSERT, autoDrainIfOnline = autoDrain)
    }

    suspend fun enqueueDeletePaymentProof(uid: String, proof: PaymentProofEntity, autoDrain: Boolean = false) {
        val docId = CloudIdMapper.paymentProofDocId(proof.id)
        enqueueMutation(uid, TYPE_PAYMENT_PROOF, proof.id, docId, operation = OP_DELETE, autoDrainIfOnline = autoDrain)
    }

    suspend fun enqueueCompanySettings(
        uid: String,
        profile: CompanyProfile,
        perjanjianText: String,
        paymentMethods: List<PaymentMethodEntity>,
        smartNotif: SmartNotificationSettings,
        autoDrain: Boolean = true
    ) {
        val payload = JSONObject().apply {
            put("namaPerusahaan", profile.namaPerusahaan)
            put("alamat", profile.alamat)
            put("nomorHp", profile.nomorHp)
            put("email", profile.email)
            put("instagram", profile.instagram)
            put("facebook", profile.facebook)
            put("identitasUsaha", profile.identitasUsaha)
            put("informasiTambahan", profile.informasiTambahan)
            put("ownerName", profile.ownerName)
            put("ownerSignatureUri", profile.ownerSignatureUri)
            put("perjanjianSewaText", perjanjianText)
            put("paymentMethodsJson", serializePaymentMethods(paymentMethods))
            put("smartNotifJson", serializeSmartNotif(smartNotif))
        }.toString()
        enqueueMutation(uid, TYPE_COMPANY_SETTINGS, 1L, "primary", payloadJson = payload, autoDrainIfOnline = autoDrain)
    }

    suspend fun drainPendingQueue(uid: String): Boolean = drainPendingQueue(uid, ignoreBackoff = true)

    suspend fun drainPendingQueue(uid: String, ignoreBackoff: Boolean): Boolean = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            if (hardResetPaused.get() || uid.isBlank()) return@withContext false

            // Crash-safety recovery: reset any rows stranded in SYNCING / UPLOADING from a prior process kill
            val today = DateCurrencyUtils.todayIso()
            dao.resetStuckSyncingQueueItems(uid, today)
            photoManager.resetStuckUploadingJobs(uid)

            val pending = dao.getPendingSyncQueueOnce(uid)
            val pendingPhotos = dao.getPendingPhotoJobsOnce(uid)
            if (pending.isEmpty() && pendingPhotos.isEmpty()) {
                _syncStatus.value = STATUS_SYNCED
                _lastSyncError.value = null
                return@withContext true
            }

            if (!isNetworkConnected()) {
                _syncStatus.value = STATUS_WAITING_NETWORK
                CloudFirestoreSyncWorker.enqueueSync(context, uid)
                return@withContext false
            }

            _syncStatus.value = STATUS_SYNCING
            _lastSyncError.value = null
            var hadFailure = false
            var maxBackoffDelaySec = 0L

            for (item in pending) {
                val nowMs = System.currentTimeMillis()
                val nextAllowedAt = nextRetryAtMillisByItemId[item.id] ?: 0L
                if (!ignoreBackoff && nextAllowedAt > nowMs) {
                    hadFailure = true
                    val remainingSec = ((nextAllowedAt - nowMs) / 1000L).coerceAtLeast(1L)
                    if (remainingSec > maxBackoffDelaySec) maxBackoffDelaySec = remainingSec
                    continue
                }

                val key = pushKey(item.entityType, item.cloudDocId)
                inFlightPushKeys.add(key)
                try {
                    dao.updateCloudSyncQueueStatus(
                        id = item.id,
                        status = SYNC_STATE_SYNCING,
                        lastError = "",
                        retryCount = item.retryCount,
                        updatedAt = DateCurrencyUtils.todayIso()
                    )
                    processSingleQueueItem(uid, item)
                    recentlyPushedAtMillis[key] = maxOf(item.clientUpdatedAtMillis, System.currentTimeMillis())
                    nextRetryAtMillisByItemId.remove(item.id)
                    dao.deleteCloudSyncQueueItemById(item.id)
                } catch (e: CancellationException) {
                    // Restore to PENDING on cancellation so it isn't stuck in SYNCING
                    runCatching {
                        dao.updateCloudSyncQueueStatus(
                            id = item.id,
                            status = "PENDING",
                            lastError = "",
                            retryCount = item.retryCount,
                            updatedAt = DateCurrencyUtils.todayIso()
                        )
                    }
                    throw e
                } catch (e: Exception) {
                    hadFailure = true
                    val nextRetry = item.retryCount + 1
                    val backoffMs = computeExponentialBackoffMillis(nextRetry)
                    nextRetryAtMillisByItemId[item.id] = System.currentTimeMillis() + backoffMs
                    val backoffSec = (backoffMs / 1000L).coerceAtLeast(2L)
                    if (backoffSec > maxBackoffDelaySec) maxBackoffDelaySec = backoffSec

                    val errMsg = e.localizedMessage ?: "Gagal menyinkronkan ${item.entityType}"
                    _lastSyncError.value = errMsg
                    dao.updateCloudSyncQueueStatus(
                        id = item.id,
                        status = SYNC_STATE_FAILED,
                        lastError = errMsg,
                        retryCount = nextRetry,
                        updatedAt = DateCurrencyUtils.todayIso()
                    )
                } finally {
                    inFlightPushKeys.remove(key)
                }
            }

            // Process any remaining standalone queued/failed photo jobs
            val remainingPhotos = dao.getPendingPhotoJobsOnce(uid)
            if (remainingPhotos.isNotEmpty()) {
                val (_, failedPhotos) = photoManager.processPendingPhotoJobs(uid, cloudRepo)
                if (failedPhotos > 0) {
                    hadFailure = true
                    _lastSyncError.value = "$failedPhotos foto menunggu unggah ulang ke Cloud Storage"
                    if (maxBackoffDelaySec == 0L) maxBackoffDelaySec = 15L
                }
            }

            if (hadFailure) {
                CloudFirestoreSyncWorker.enqueueSync(context, uid, initialDelaySeconds = maxBackoffDelaySec)
            }

            _syncStatus.value = when {
                !isNetworkConnected() -> STATUS_WAITING_NETWORK
                hadFailure -> STATUS_FAILED
                else -> {
                    _lastSyncTimestamp.value = DateCurrencyUtils.nowIsoDateTime()
                    STATUS_SYNCED
                }
            }
            !hadFailure
        }
    }

    private fun collectionNameForEntityType(entityType: String): String? = when (entityType) {
        TYPE_CUSTOMER -> "customers"
        TYPE_UNIT -> "freezer_units"
        TYPE_TRANSACTION -> "transactions"
        TYPE_TRANSACTION_UNIT_ITEM -> "transaction_unit_items"
        TYPE_PAYMENT_PROOF -> "payment_proofs"
        TYPE_UNIT_PAYMENT_ALLOCATION -> "unit_payment_allocations"
        TYPE_SIGNATURE -> "transaction_signatures"
        TYPE_RETURN -> "freezer_returns"
        else -> null
    }

    private suspend fun processSingleQueueItem(uid: String, item: CloudSyncQueueEntity) {
        val ts = item.clientUpdatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()

        // 1. Handle DELETE operations cleanly without stalling
        if (item.operation.equals(OP_DELETE, ignoreCase = true)) {
            processDeleteQueueItem(uid, item)
            return
        }

        // 2. Handle UPSERT operations with Media-First ordering (upload photo & obtain downloadUrl BEFORE writing parent doc)
        when (item.entityType) {
            TYPE_CUSTOMER -> {
                val c = dao.getCustomerById(item.entityLocalId) ?: return
                val jobUrls = photoManager.processPendingPhotoJobsForOwner(uid, "CUSTOMER", c.id, cloudRepo)
                cloudRepo.upsertCustomer(
                    c.toCloudCustomer(
                        userId = uid,
                        timestampMillis = ts,
                        version = c.version.coerceAtLeast(1L),
                        fotoKtpDepanUrl = jobUrls["KTP_DEPAN"].orEmpty(),
                        fotoKtpBelakangUrl = jobUrls["KTP_BELAKANG"].orEmpty(),
                        fotoPelangganUrl = jobUrls["FOTO_PELANGGAN"].orEmpty()
                    )
                )
                markLocalEntitySyncedIfNoDrivePending(TYPE_CUSTOMER, c.id)
            }
            TYPE_UNIT -> {
                val u = dao.getUnitById(item.entityLocalId) ?: return
                val jobUrls = photoManager.processPendingPhotoJobsForOwner(uid, "UNIT", u.id, cloudRepo)
                cloudRepo.upsertFreezerUnit(
                    u.toCloudUnit(
                        userId = uid,
                        timestampMillis = ts,
                        version = u.version.coerceAtLeast(1L),
                        fotoUnitUrl = jobUrls["FOTO_UNIT"].orEmpty(),
                        fotoKondisiFisikUrl = jobUrls["KONDISI_FISIK"].orEmpty(),
                        fotoBagianDalamUrl = jobUrls["BAGIAN_DALAM"].orEmpty(),
                        fotoMerekSeriUrl = jobUrls["MEREK_SERI"].orEmpty()
                    )
                )
                markLocalEntitySyncedIfNoDrivePending(TYPE_UNIT, u.id)
            }
            TYPE_TRANSACTION -> {
                val tx = dao.getTransactionById(item.entityLocalId) ?: return
                // Upload transaction documentation photos BEFORE writing transaction doc
                photoManager.processPendingPhotoJobsForOwner(uid, "TRANSACTION", tx.id, cloudRepo)
                cloudRepo.upsertRentalTransaction(tx.toCloudTransaction(uid, ts, version = tx.version.coerceAtLeast(1L)))
                dao.getTransactionUnitItemsByTxOnce(tx.id).forEach { unitItem ->
                    cloudRepo.upsertTransactionUnitItem(unitItem.toCloudUnitItem(uid, ts))
                }
                markLocalEntitySyncedIfNoDrivePending(TYPE_TRANSACTION, tx.id)
            }
            TYPE_TRANSACTION_UNIT_ITEM -> {
                val ui = dao.getTransactionUnitItemById(item.entityLocalId) ?: return
                cloudRepo.upsertTransactionUnitItem(ui.toCloudUnitItem(uid, ts))
            }
            TYPE_PAYMENT_PROOF -> {
                val proof = dao.getPaymentProofById(item.entityLocalId) ?: return
                // Upload payment proof photo BEFORE writing payment_proofs doc so fotoBuktiUrl is populated
                val txPhotoUrls = photoManager.processPendingPhotoJobsForOwner(uid, "TRANSACTION", proof.transactionId, cloudRepo)
                val url = txPhotoUrls["BUKTI_BAYAR_${proof.id}"].orEmpty()
                cloudRepo.upsertPaymentProof(
                    proof.toCloudPaymentProof(
                        userId = uid,
                        timestampMillis = ts,
                        version = proof.version.coerceAtLeast(1L),
                        fotoBuktiUrl = url
                    )
                )
                markLocalEntitySyncedIfNoDrivePending(TYPE_PAYMENT_PROOF, proof.id)
            }
            TYPE_UNIT_PAYMENT_ALLOCATION -> {
                val alloc = dao.getAllUnitPaymentAllocationsOnce().firstOrNull { it.id == item.entityLocalId } ?: return
                cloudRepo.upsertUnitPaymentAllocation(alloc.toCloudAllocation(uid, ts))
            }
            TYPE_SIGNATURE -> {
                val sig = dao.getAllTransactionSignaturesOnce().firstOrNull { it.id == item.entityLocalId } ?: return
                cloudRepo.upsertTransactionSignature(sig.toCloudSignature(uid, ts))
            }
            TYPE_RETURN -> {
                val ret = dao.getReturnById(item.entityLocalId) ?: return
                cloudRepo.upsertFreezerReturn(ret.toCloudReturn(uid, ts))
            }
            TYPE_COMPANY_SETTINGS -> {
                val obj = runCatching { JSONObject(item.payloadJson) }.getOrNull() ?: JSONObject()
                val settings = CloudCompanySettings(
                    id = "primary",
                    userId = uid,
                    namaPerusahaan = obj.optString("namaPerusahaan", "SEWAin Aja Freezer SOC"),
                    alamat = obj.optString("alamat", ""),
                    nomorHp = obj.optString("nomorHp", ""),
                    email = obj.optString("email", ""),
                    instagram = obj.optString("instagram", ""),
                    facebook = obj.optString("facebook", ""),
                    identitasUsaha = obj.optString("identitasUsaha", ""),
                    informasiTambahan = obj.optString("informasiTambahan", ""),
                    ownerName = obj.optString("ownerName", ""),
                    ownerSignatureUrl = obj.optString("ownerSignatureUrl", ""),
                    perjanjianSewaText = obj.optString("perjanjianSewaText", ""),
                    paymentMethodsJson = obj.optString("paymentMethodsJson", ""),
                    smartNotifJson = obj.optString("smartNotifJson", ""),
                    clientUpdatedAtMillis = ts,
                    version = 1L
                )
                cloudRepo.upsertCompanySettings(settings)
            }
        }
    }

    private suspend fun processDeleteQueueItem(uid: String, item: CloudSyncQueueEntity) {
        val collectionName = collectionNameForEntityType(item.entityType) ?: return
        // Delete main Firestore document (idempotent in Firestore even if document no longer exists)
        cloudRepo.deleteDocument(collectionName, item.cloudDocId)

        // Safely clean up linked Cloud Storage objects & photo_metadata without stalling on Object-Not-Found
        when (item.entityType) {
            TYPE_CUSTOMER -> {
                photoManager.deletePhotosForOwnerSafely(uid, "CUSTOMER", item.entityLocalId, cloudRepo)
            }
            TYPE_UNIT -> {
                photoManager.deletePhotosForOwnerSafely(uid, "UNIT", item.entityLocalId, cloudRepo)
            }
            TYPE_TRANSACTION -> {
                photoManager.deletePhotosForOwnerSafely(uid, "TRANSACTION", item.entityLocalId, cloudRepo)
                // Clean up associated child transaction unit items in Firestore
                val localUnitItems = dao.getTransactionUnitItemsByTxOnce(item.entityLocalId)
                for (ui in localUnitItems) {
                    runCatching {
                        cloudRepo.deleteDocument("transaction_unit_items", CloudIdMapper.transactionUnitItemDocId(ui.id))
                    }
                }
            }
            TYPE_PAYMENT_PROOF -> {
                val proof = dao.getPaymentProofByIdIncludingDeleted(item.entityLocalId)
                val txId = proof?.transactionId ?: 0L
                if (txId > 0L) {
                    val slot = "BUKTI_BAYAR_${item.entityLocalId}"
                    val jobs = dao.getCloudPhotoJobsForOwnerOnce(uid, "TRANSACTION", txId)
                        .filter { it.photoSlot == slot }
                    for (j in jobs) {
                        val metaDocId = CloudIdMapper.photoMetaDocId(j.ownerType, j.ownerLocalId, j.photoSlot)
                        runCatching { cloudRepo.deletePhotoMetadata(metaDocId) }
                    }
                }
            }
        }
    }

    private suspend fun markLocalEntitySyncedIfNoDrivePending(entityType: String, localId: Long) {
        val driveKey = when (entityType) {
            TYPE_CUSTOMER -> "CUSTOMER:$localId"
            TYPE_UNIT -> "UNIT:$localId"
            TYPE_TRANSACTION -> "TRANSACTION:$localId"
            TYPE_PAYMENT_PROOF -> "PAYMENT:$localId"
            else -> return
        }
        val driveQueueItem = dao.getLatestDriveSyncQueueForKey(driveKey)
        val hasPendingDriveOp = driveQueueItem != null &&
            driveQueueItem.syncStatus in setOf("PENDING_SYNC", "UPLOADING", "FAILED", "FAILED_AUTH")
        if (hasPendingDriveOp) return

        when (entityType) {
            TYPE_CUSTOMER -> dao.updateCustomerSyncStatusOnly(localId, SYNC_STATE_SYNCED)
            TYPE_UNIT -> dao.updateUnitSyncStatusOnly(localId, SYNC_STATE_SYNCED)
            TYPE_TRANSACTION -> dao.updateTransactionSyncStatusOnly(localId, SYNC_STATE_SYNCED)
            TYPE_PAYMENT_PROOF -> dao.updatePaymentProofSyncStatusOnly(localId, SYNC_STATE_SYNCED)
        }
    }

    // --- Real-Time Incremental Two-Way Sync & LWW Conflict Resolution (Guarded by syncMutex) ---

    private fun isRemoteTimestampBlockedByReset(remoteTimestampMillis: Long): Boolean {
        val mirrorPrefs = context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)
        val appPrefs = context.getSharedPreferences(GoogleDriveMirrorSyncEngine.APP_PREFS_NAME, Context.MODE_PRIVATE)
        val lastResetMs = mirrorPrefs.getLong("last_reset_timestamp_ms", 0L)
        val intentionallyCleared = appPrefs.getBoolean("intentionally_cleared_db", false)
        if (lastResetMs > 0L && (remoteTimestampMillis == 0L || remoteTimestampMillis < lastResetMs)) {
            return true
        }
        if (intentionallyCleared && lastResetMs > 0L && remoteTimestampMillis <= lastResetMs) {
            return true
        }
        return false
    }

    private fun parseLocalIdFromDocId(docId: String, prefix: String): Long =
        docId.removePrefix(prefix).toLongOrNull() ?: 0L

    suspend fun applyRemoteCustomerChanges(
        uid: String,
        changes: List<CloudDocumentChange<CloudCustomer>>
    ) = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            if (changes.isEmpty()) return@withContext
            val pendingByDocId = dao.getPendingSyncQueueOnce(uid)
                .filter { it.entityType == TYPE_CUSTOMER }
                .associateBy { it.cloudDocId }

            for (change in changes) {
                // Skip latency-compensated local writes to prevent self-echo loop
                if (change.hasPendingWrites) continue
                val remote = change.data
                val effectiveTs = remote?.effectiveUpdatedAtMillis() ?: 0L
                if (isSuppressedByRecentPush(TYPE_CUSTOMER, change.docId, effectiveTs)) continue

                when (change.type) {
                    CloudChangeType.ADDED, CloudChangeType.MODIFIED -> {
                        if (remote == null) continue
                        mergeSingleRemoteCustomerLocked(remote, pendingByDocId[remote.id])
                    }
                    CloudChangeType.REMOVED -> {
                        val pending = pendingByDocId[change.docId]
                        if (pending != null && pending.operation == OP_UPSERT) {
                            // Local device has a newer pending upsert; do not delete it
                            continue
                        }
                        val localId = remote?.localId?.takeIf { it > 0L }
                            ?: parseLocalIdFromDocId(change.docId, "cust_")
                        val existing = if (localId > 0L) dao.getCustomerById(localId) else null
                        if (existing != null && !existing.isDeleted) {
                            dao.softDeleteCustomer(
                                id = existing.id,
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = SYNC_STATE_SYNCED
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun applyRemoteUnitChanges(
        uid: String,
        changes: List<CloudDocumentChange<CloudFreezerUnit>>
    ) = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            if (changes.isEmpty()) return@withContext
            val pendingByDocId = dao.getPendingSyncQueueOnce(uid)
                .filter { it.entityType == TYPE_UNIT }
                .associateBy { it.cloudDocId }

            for (change in changes) {
                if (change.hasPendingWrites) continue
                val remote = change.data
                val effectiveTs = remote?.effectiveUpdatedAtMillis() ?: 0L
                if (isSuppressedByRecentPush(TYPE_UNIT, change.docId, effectiveTs)) continue

                when (change.type) {
                    CloudChangeType.ADDED, CloudChangeType.MODIFIED -> {
                        if (remote == null) continue
                        mergeSingleRemoteUnitLocked(remote, pendingByDocId[remote.id])
                    }
                    CloudChangeType.REMOVED -> {
                        val pending = pendingByDocId[change.docId]
                        if (pending != null && pending.operation == OP_UPSERT) continue
                        val localId = remote?.localId?.takeIf { it > 0L }
                            ?: parseLocalIdFromDocId(change.docId, "unit_")
                        val existing = if (localId > 0L) dao.getUnitById(localId) else null
                        if (existing != null && !existing.isDeleted) {
                            dao.softDeleteUnit(
                                id = existing.id,
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = SYNC_STATE_SYNCED
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun applyRemoteTransactionChanges(
        uid: String,
        changes: List<CloudDocumentChange<CloudRentalTransaction>>
    ) = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            if (changes.isEmpty()) return@withContext
            val pendingByDocId = dao.getPendingSyncQueueOnce(uid)
                .filter { it.entityType == TYPE_TRANSACTION }
                .associateBy { it.cloudDocId }

            for (change in changes) {
                if (change.hasPendingWrites) continue
                val remote = change.data
                val effectiveTs = remote?.effectiveUpdatedAtMillis() ?: 0L
                if (isSuppressedByRecentPush(TYPE_TRANSACTION, change.docId, effectiveTs)) continue

                when (change.type) {
                    CloudChangeType.ADDED, CloudChangeType.MODIFIED -> {
                        if (remote == null) continue
                        mergeSingleRemoteTransactionLocked(remote, pendingByDocId[remote.id])
                    }
                    CloudChangeType.REMOVED -> {
                        val pending = pendingByDocId[change.docId]
                        if (pending != null && pending.operation == OP_UPSERT) continue
                        val localId = remote?.localId?.takeIf { it > 0L }
                            ?: parseLocalIdFromDocId(change.docId, "tx_")
                        val existing = if (localId > 0L) dao.getTransactionById(localId) else null
                        if (existing != null && !existing.isDeleted) {
                            dao.softDeleteTransaction(
                                id = existing.id,
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = SYNC_STATE_SYNCED
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun applyRemotePaymentProofChanges(
        uid: String,
        changes: List<CloudDocumentChange<CloudPaymentProof>>
    ) = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            if (changes.isEmpty()) return@withContext
            val pendingByDocId = dao.getPendingSyncQueueOnce(uid)
                .filter { it.entityType == TYPE_PAYMENT_PROOF }
                .associateBy { it.cloudDocId }

            for (change in changes) {
                if (change.hasPendingWrites) continue
                val remote = change.data
                val effectiveTs = remote?.effectiveUpdatedAtMillis() ?: 0L
                if (isSuppressedByRecentPush(TYPE_PAYMENT_PROOF, change.docId, effectiveTs)) continue

                when (change.type) {
                    CloudChangeType.ADDED, CloudChangeType.MODIFIED -> {
                        if (remote == null) continue
                        mergeSingleRemotePaymentProofLocked(remote, pendingByDocId[remote.id])
                    }
                    CloudChangeType.REMOVED -> {
                        val pending = pendingByDocId[change.docId]
                        if (pending != null && pending.operation == OP_UPSERT) continue
                        val localId = remote?.localId?.takeIf { it > 0L }
                            ?: parseLocalIdFromDocId(change.docId, "pay_")
                        val existing = if (localId > 0L) dao.getPaymentProofById(localId) else null
                        if (existing != null && !existing.isDeleted) {
                            dao.softDeletePaymentProof(
                                id = existing.id,
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = SYNC_STATE_SYNCED
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Backward-compatible batch merge guarded by syncMutex with full LWW & tombstone protection.
     */
    suspend fun mergeRemoteCustomersIntoLocal(uid: String, remoteList: List<CloudCustomer>) = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            if (remoteList.isEmpty()) return@withContext
            val pendingIds = dao.getPendingSyncQueueOnce(uid)
                .filter { it.entityType == TYPE_CUSTOMER }
                .associateBy { it.cloudDocId }

            for (remote in remoteList) {
                mergeSingleRemoteCustomerLocked(remote, pendingIds[remote.id])
            }
        }
    }

    /**
     * Backward-compatible batch merge guarded by syncMutex with full LWW & tombstone protection.
     */
    suspend fun mergeRemoteUnitsIntoLocal(uid: String, remoteList: List<CloudFreezerUnit>) = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            if (remoteList.isEmpty()) return@withContext
            val pendingIds = dao.getPendingSyncQueueOnce(uid)
                .filter { it.entityType == TYPE_UNIT }
                .associateBy { it.cloudDocId }

            for (remote in remoteList) {
                mergeSingleRemoteUnitLocked(remote, pendingIds[remote.id])
            }
        }
    }

    private suspend fun mergeSingleRemoteCustomerLocked(
        remote: CloudCustomer,
        pending: CloudSyncQueueEntity?
    ) {
        val remoteEffectiveTs = remote.effectiveUpdatedAtMillis()
        if (isRemoteTimestampBlockedByReset(remoteEffectiveTs)) return
        if (remote.localId > 0L && dao.getDriveDeletionTombstoneByKey("CUSTOMER:${remote.localId}") != null) return
        if (pending != null) {
            if (pending.operation == OP_DELETE) return
            if (pending.clientUpdatedAtMillis >= remoteEffectiveTs) return
        }

        val existingIncludingDeleted = when {
            remote.localId > 0L -> dao.getCustomerByIdIncludingDeleted(remote.localId)
            remote.kodePelanggan.isNotBlank() -> dao.getCustomerByKodeIncludingDeleted(remote.kodePelanggan)
            else -> null
        }
        // Never resurrect a locally soft-deleted customer unless a strictly newer remote creation/edit arrives without tombstone
        if (existingIncludingDeleted != null && existingIncludingDeleted.isDeleted) return
        // Accurate Last-Write-Wins against existing synced local timestamp
        if (existingIncludingDeleted != null && existingIncludingDeleted.updatedAtMillis >= remoteEffectiveTs) return

        val merged = remote.toLocalEntity(existingIncludingDeleted)
        if (existingIncludingDeleted == null || merged != existingIncludingDeleted) {
            dao.insertCustomer(merged)
        }
    }

    private suspend fun mergeSingleRemoteUnitLocked(
        remote: CloudFreezerUnit,
        pending: CloudSyncQueueEntity?
    ) {
        val remoteEffectiveTs = remote.effectiveUpdatedAtMillis()
        if (isRemoteTimestampBlockedByReset(remoteEffectiveTs)) return
        if (remote.localId > 0L && dao.getDriveDeletionTombstoneByKey("UNIT:${remote.localId}") != null) return
        if (pending != null && pending.operation == OP_DELETE) return

        val existingIncludingDeleted = when {
            remote.localId > 0L -> dao.getUnitByIdIncludingDeleted(remote.localId)
            remote.kodeUnit.isNotBlank() -> dao.getUnitByKodeIncludingDeleted(remote.kodeUnit)
            else -> null
        }
        if (existingIncludingDeleted != null && existingIncludingDeleted.isDeleted) return

        // Detect authoritative Cloud price update on Unit (cicilanPerBulan / hargaPembelian).
        // Cloud unit price changes MUST overwrite local cache immediately without being held back
        // by Stale flags or minor clock-skew timestamp conflicts when no local price edit is pending.
        val hasCloudPriceChange = existingIncludingDeleted != null && (
            (remote.cicilanPerBulan > 0L && remote.cicilanPerBulan != existingIncludingDeleted.cicilanPerBulan) ||
                (remote.hargaPembelian > 0L && remote.hargaPembelian != existingIncludingDeleted.hargaPembelian)
            )
        val isLocalStaleOrSynced = existingIncludingDeleted == null ||
            existingIncludingDeleted.syncStatus.equals("STALE", ignoreCase = true) ||
            existingIncludingDeleted.syncStatus.equals(SYNC_STATE_SYNCED, ignoreCase = true) ||
            existingIncludingDeleted.syncStatus.equals(MirrorOutboxCodec.STATUS_SYNCED, ignoreCase = true)

        if (!hasCloudPriceChange) {
            if (pending != null && pending.clientUpdatedAtMillis >= remoteEffectiveTs) return
            if (existingIncludingDeleted != null && existingIncludingDeleted.updatedAtMillis >= remoteEffectiveTs) return
        } else if (pending != null && !isLocalStaleOrSynced && pending.clientUpdatedAtMillis > remoteEffectiveTs) {
            // Even when a local non-price change is pending, still adopt the newer cloud unit price if remote is authoritative
            // unless the local device explicitly has a strictly newer pending write.
            return
        }

        val merged = remote.toLocalEntity(existingIncludingDeleted).let { base ->
            if (hasCloudPriceChange && existingIncludingDeleted != null) {
                base.copy(
                    updatedAtMillis = maxOf(base.updatedAtMillis, existingIncludingDeleted.updatedAtMillis + 1L),
                    syncStatus = SYNC_STATE_SYNCED
                )
            } else {
                base
            }
        }
        if (existingIncludingDeleted == null || merged != existingIncludingDeleted) {
            if (existingIncludingDeleted != null) {
                dao.updateUnit(merged)
            } else {
                dao.insertUnit(merged)
            }
            // Clear any STALE/CONFLICT Drive queue marker on this unit so UI reflects fresh cloud price immediately
            val key = "UNIT:${merged.id}"
            dao.getAllDriveLocalSyncQueueOnce()
                .filter { q ->
                    q.localRecordKey == key && (
                        q.syncStatus.equals("STALE", ignoreCase = true) ||
                            q.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_STALE_OPERATION ||
                            q.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT
                        )
                }
                .forEach { q ->
                    dao.deleteDriveSyncQueueById(q.id)
                }
        }
    }

    private suspend fun mergeSingleRemoteTransactionLocked(
        remote: CloudRentalTransaction,
        pending: CloudSyncQueueEntity?
    ) {
        val remoteEffectiveTs = remote.effectiveUpdatedAtMillis()
        if (isRemoteTimestampBlockedByReset(remoteEffectiveTs)) return
        if (remote.localId > 0L && dao.getDriveDeletionTombstoneByKey("TRANSACTION:${remote.localId}") != null) return
        if (pending != null) {
            if (pending.operation == OP_DELETE) return
            if (pending.clientUpdatedAtMillis >= remoteEffectiveTs) return
        }

        val existingIncludingDeleted = when {
            remote.localId > 0L -> dao.getTransactionByIdIncludingDeleted(remote.localId)
            remote.nomorInvoice.isNotBlank() -> dao.getTransactionByInvoiceIncludingDeleted(remote.nomorInvoice)
            else -> null
        }
        if (existingIncludingDeleted != null && existingIncludingDeleted.isDeleted) return
        if (existingIncludingDeleted != null && existingIncludingDeleted.updatedAtMillis >= remoteEffectiveTs) return

        // Verify foreign-key parents exist locally before inserting to prevent SQLite FK crash
        val resolvedCustId = existingIncludingDeleted?.customerId
            ?: dao.getCustomerById(remote.customerId)?.id
            ?: return
        val resolvedUnitId = existingIncludingDeleted?.unitId
            ?: dao.getUnitById(remote.unitId)?.id
            ?: return

        val merged = remote.toLocalEntity(
            existing = existingIncludingDeleted,
            resolvedCustomerId = resolvedCustId,
            resolvedUnitId = resolvedUnitId
        )
        if (existingIncludingDeleted == null || merged != existingIncludingDeleted) {
            if (existingIncludingDeleted != null) {
                dao.updateTransaction(merged)
            } else {
                dao.insertTransaction(merged)
            }
        }
        if (merged.nomorInvoice.isNotBlank()) {
            DatabaseGenerationManager(context).recordObservedInvoiceNumber(merged.nomorInvoice)
        }
    }

    private suspend fun mergeSingleRemotePaymentProofLocked(
        remote: CloudPaymentProof,
        pending: CloudSyncQueueEntity?
    ) {
        val remoteEffectiveTs = remote.effectiveUpdatedAtMillis()
        if (isRemoteTimestampBlockedByReset(remoteEffectiveTs)) return
        if (remote.localId > 0L && dao.getDriveDeletionTombstoneByKey("PAYMENT:${remote.localId}") != null) return
        if (pending != null) {
            if (pending.operation == OP_DELETE) return
            if (pending.clientUpdatedAtMillis >= remoteEffectiveTs) return
        }

        val existingIncludingDeleted = if (remote.localId > 0L) {
            dao.getPaymentProofByIdIncludingDeleted(remote.localId)
        } else null
        if (existingIncludingDeleted != null && existingIncludingDeleted.isDeleted) return
        if (existingIncludingDeleted != null && existingIncludingDeleted.updatedAtMillis >= remoteEffectiveTs) return

        val resolvedTxId = existingIncludingDeleted?.transactionId
            ?: dao.getTransactionById(remote.transactionId)?.id
            ?: return

        val merged = remote.toLocalEntity(
            existing = existingIncludingDeleted,
            resolvedTransactionId = resolvedTxId
        )
        if (existingIncludingDeleted == null || merged != existingIncludingDeleted) {
            dao.insertPaymentProof(merged)
        }
    }

    // --- Resumable 10-Step SQLite -> Cloud Migration (Media-First Ordering!) ---

    suspend fun runOrResumeMigration(
        user: FirebaseUser,
        companyProfile: CompanyProfile,
        perjanjianText: String,
        paymentMethods: List<PaymentMethodEntity>,
        smartNotif: SmartNotificationSettings,
        stopAfterStepForTest: Int? = null
    ): CloudMigrationStateEntity = migrationMutex.withLock {
        withContext(Dispatchers.IO) {
            val uid = user.uid
            val nowIso = DateCurrencyUtils.nowIsoDateTime()
            val existingState = dao.getCloudMigrationStateOnce(uid)
            if (existingState?.status == "COMPLETED") {
                return@withContext existingState
            }

            var state = (existingState ?: CloudMigrationStateEntity(
                uid = uid,
                currentStep = 1,
                stepLabel = stepDescription(1),
                status = "IN_PROGRESS",
                startedAt = nowIso,
                updatedAt = nowIso
            )).copy(
                status = "IN_PROGRESS",
                lastError = "",
                updatedAt = nowIso
            )

            val startStep = state.currentStep.coerceIn(1, 10)
            _syncStatus.value = STATUS_SYNCING

            try {
                val customers = dao.getAllCustomersOnce()
                val units = dao.getAllUnitsOnce()
                val transactions = dao.getAllTransactionsOnce()
                val unitItems = dao.getAllTransactionUnitItemsOnce()
                val proofs = dao.getAllPaymentProofsOnce()
                val allocations = dao.getAllUnitPaymentAllocationsOnce()
                val signatures = dao.getAllTransactionSignaturesOnce()
                val returns = dao.getAllReturnsOnce()
                val totalItems = (customers.size + units.size + transactions.size + proofs.size + 2).coerceAtLeast(1)

                state = state.copy(
                    totalItems = totalItems,
                    localCustomersCount = customers.size,
                    localUnitsCount = units.size,
                    localTxCount = transactions.size
                )
                saveMigrationCheckpoint(uid, state)

                var processed = state.processedItems

                for (step in startStep..10) {
                    state = state.copy(
                        currentStep = step,
                        stepLabel = stepDescription(step),
                        updatedAt = DateCurrencyUtils.nowIsoDateTime()
                    )
                    saveMigrationCheckpoint(uid, state)

                    when (step) {
                        1 -> {
                            cloudRepo.upsertUserProfile(
                                CloudUserProfile(
                                    id = uid,
                                    userId = uid,
                                    displayName = user.displayName.orEmpty().ifBlank { user.email?.substringBefore("@") ?: "Admin" },
                                    email = user.email.orEmpty(),
                                    photoUrl = user.photoUrl?.toString().orEmpty(),
                                    lastActiveDevice = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                                )
                            )
                            processed++
                        }
                        2 -> {
                            cloudRepo.upsertCompanySettings(
                                companyProfile.toCloudSettings(
                                    userId = uid,
                                    perjanjianSewaText = perjanjianText,
                                    paymentMethodsJson = serializePaymentMethods(paymentMethods),
                                    smartNotifJson = serializeSmartNotif(smartNotif)
                                )
                            )
                            processed++
                        }
                        3 -> {
                            // Media-First: Enqueue AND upload customer & unit photos BEFORE steps 4 & 5 write parent docs!
                            customers.forEach { c ->
                                val cid = CloudIdMapper.customerDocId(c.id)
                                if (c.fotoKtpDepanUri.isNotBlank()) photoManager.enqueuePhotoJob(uid, "CUSTOMER", c.id, cid, "KTP_DEPAN", c.fotoKtpDepanUri)
                                if (c.fotoKtpBelakangUri.isNotBlank()) photoManager.enqueuePhotoJob(uid, "CUSTOMER", c.id, cid, "KTP_BELAKANG", c.fotoKtpBelakangUri)
                                if (c.fotoPelangganUri.isNotBlank()) photoManager.enqueuePhotoJob(uid, "CUSTOMER", c.id, cid, "FOTO_PELANGGAN", c.fotoPelangganUri)
                            }
                            units.forEach { u ->
                                val uDocId = CloudIdMapper.unitDocId(u.id)
                                if (u.fotoUnitUri.isNotBlank()) photoManager.enqueuePhotoJob(uid, "UNIT", u.id, uDocId, "FOTO_UNIT", u.fotoUnitUri)
                                if (u.fotoKondisiFisikUri.isNotBlank()) photoManager.enqueuePhotoJob(uid, "UNIT", u.id, uDocId, "KONDISI_FISIK", u.fotoKondisiFisikUri)
                                if (u.fotoBagianDalamUri.isNotBlank()) photoManager.enqueuePhotoJob(uid, "UNIT", u.id, uDocId, "BAGIAN_DALAM", u.fotoBagianDalamUri)
                                if (u.fotoMerekSeriUri.isNotBlank()) photoManager.enqueuePhotoJob(uid, "UNIT", u.id, uDocId, "MEREK_SERI", u.fotoMerekSeriUri)
                            }
                            photoManager.processPendingPhotoJobs(uid, cloudRepo)
                        }
                        4 -> {
                            val allUploadedJobs = dao.getAllCloudPhotoJobsOnce(uid)
                                .filter { it.ownerType == "CUSTOMER" && it.status == CloudStoragePhotoManager.STATUS_UPLOADED }
                                .groupBy { it.ownerLocalId }
                            customers.forEach { c ->
                                val urls = allUploadedJobs[c.id].orEmpty().associate { it.photoSlot to it.cloudDownloadUrl }
                                cloudRepo.upsertCustomer(
                                    c.toCloudCustomer(
                                        userId = uid,
                                        version = c.version.coerceAtLeast(1L),
                                        fotoKtpDepanUrl = urls["KTP_DEPAN"].orEmpty(),
                                        fotoKtpBelakangUrl = urls["KTP_BELAKANG"].orEmpty(),
                                        fotoPelangganUrl = urls["FOTO_PELANGGAN"].orEmpty()
                                    )
                                )
                                processed++
                            }
                        }
                        5 -> {
                            val allUploadedJobs = dao.getAllCloudPhotoJobsOnce(uid)
                                .filter { it.ownerType == "UNIT" && it.status == CloudStoragePhotoManager.STATUS_UPLOADED }
                                .groupBy { it.ownerLocalId }
                            units.forEach { u ->
                                val urls = allUploadedJobs[u.id].orEmpty().associate { it.photoSlot to it.cloudDownloadUrl }
                                cloudRepo.upsertFreezerUnit(
                                    u.toCloudUnit(
                                        userId = uid,
                                        version = u.version.coerceAtLeast(1L),
                                        fotoUnitUrl = urls["FOTO_UNIT"].orEmpty(),
                                        fotoKondisiFisikUrl = urls["KONDISI_FISIK"].orEmpty(),
                                        fotoBagianDalamUrl = urls["BAGIAN_DALAM"].orEmpty(),
                                        fotoMerekSeriUrl = urls["MEREK_SERI"].orEmpty()
                                    )
                                )
                                processed++
                            }
                        }
                        6 -> {
                            // Media-First: Enqueue AND upload transaction & payment proof photos BEFORE steps 7 & 8!
                            transactions.forEach { tx ->
                                val txDocId = CloudIdMapper.transactionDocId(tx.id)
                                if (tx.fotoSerahTerimaUri.isNotBlank()) photoManager.enqueuePhotoJob(uid, "TRANSACTION", tx.id, txDocId, "SERAH_TERIMA", tx.fotoSerahTerimaUri)
                                if (tx.fotoFreezerDiLokasiUri.isNotBlank()) photoManager.enqueuePhotoJob(uid, "TRANSACTION", tx.id, txDocId, "FREEZER_DI_LOKASI", tx.fotoFreezerDiLokasiUri)
                            }
                            proofs.forEach { p ->
                                if (p.fotoBuktiUri.isNotBlank()) {
                                    photoManager.enqueuePhotoJob(uid, "TRANSACTION", p.transactionId, CloudIdMapper.transactionDocId(p.transactionId), "BUKTI_BAYAR_${p.id}", p.fotoBuktiUri)
                                }
                            }
                            photoManager.processPendingPhotoJobs(uid, cloudRepo)
                        }
                        7 -> {
                            transactions.forEach { tx ->
                                cloudRepo.upsertRentalTransaction(tx.toCloudTransaction(uid, version = tx.version.coerceAtLeast(1L)))
                                processed++
                            }
                            unitItems.forEach { ui ->
                                cloudRepo.upsertTransactionUnitItem(ui.toCloudUnitItem(uid))
                            }
                        }
                        8 -> {
                            val txPhotoJobs = dao.getAllCloudPhotoJobsOnce(uid)
                                .filter { it.ownerType == "TRANSACTION" && it.status == CloudStoragePhotoManager.STATUS_UPLOADED }
                                .associateBy { "${it.ownerLocalId}:${it.photoSlot}" }
                            proofs.forEach { p ->
                                val url = txPhotoJobs["${p.transactionId}:BUKTI_BAYAR_${p.id}"]?.cloudDownloadUrl.orEmpty()
                                cloudRepo.upsertPaymentProof(
                                    p.toCloudPaymentProof(uid, version = p.version.coerceAtLeast(1L), fotoBuktiUrl = url)
                                )
                                processed++
                            }
                            allocations.forEach { a -> cloudRepo.upsertUnitPaymentAllocation(a.toCloudAllocation(uid)) }
                            signatures.forEach { s -> cloudRepo.upsertTransactionSignature(s.toCloudSignature(uid)) }
                            returns.forEach { r -> cloudRepo.upsertFreezerReturn(r.toCloudReturn(uid)) }
                        }
                        9 -> {
                            val allJobs = dao.getAllCloudPhotoJobsOnce(uid)
                            state = state.copy(localPhotosCount = allJobs.size)
                            photoManager.processPendingPhotoJobs(uid, cloudRepo)
                        }
                        10 -> {
                            val cloudCustomers = cloudRepo.countCollection("customers")
                            val cloudUnits = cloudRepo.countCollection("freezer_units")
                            val cloudTx = cloudRepo.countCollection("transactions")
                            val cloudPhotos = cloudRepo.countCollection("photo_metadata")
                            val allLocalPhotos = dao.getAllCloudPhotoJobsOnce(uid).size

                            val countsMatched = cloudCustomers >= customers.size &&
                                cloudUnits >= units.size &&
                                cloudTx >= transactions.size

                            state = state.copy(
                                processedItems = totalItems,
                                localCustomersCount = customers.size,
                                cloudCustomersCount = cloudCustomers,
                                localUnitsCount = units.size,
                                cloudUnitsCount = cloudUnits,
                                localTxCount = transactions.size,
                                cloudTxCount = cloudTx,
                                localPhotosCount = allLocalPhotos,
                                cloudPhotosCount = cloudPhotos,
                                status = if (countsMatched) "COMPLETED" else "PAUSED",
                                stepLabel = if (countsMatched) "Migrasi terverifikasi 100% selesai" else "Jumlah data belum cocok, siap dilanjutkan",
                                completedAt = if (countsMatched) DateCurrencyUtils.nowIsoDateTime() else "",
                                updatedAt = DateCurrencyUtils.nowIsoDateTime()
                            )
                            saveMigrationCheckpoint(uid, state)
                            _syncStatus.value = if (countsMatched) {
                                _lastSyncTimestamp.value = DateCurrencyUtils.nowIsoDateTime()
                                STATUS_SYNCED
                            } else {
                                STATUS_FAILED
                            }
                            return@withContext state
                        }
                    }

                    state = state.copy(processedItems = processed.coerceAtMost(totalItems))
                    saveMigrationCheckpoint(uid, state)

                    if (stopAfterStepForTest != null && step >= stopAfterStepForTest) {
                        state = state.copy(status = "PAUSED", currentStep = (step + 1).coerceAtMost(10))
                        dao.upsertCloudMigrationState(state)
                        return@withContext state
                    }
                }
                state
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Migrasi terhenti, progres tersimpan untuk dilanjutkan"
                Log.e(TAG, "Migration paused at step ${state.currentStep}: $msg", e)
                val failedState = state.copy(
                    status = if (isNetworkConnected()) "FAILED" else "PAUSED",
                    lastError = msg,
                    updatedAt = DateCurrencyUtils.nowIsoDateTime()
                )
                dao.upsertCloudMigrationState(failedState)
                _syncStatus.value = if (isNetworkConnected()) STATUS_FAILED else STATUS_WAITING_NETWORK
                _lastSyncError.value = msg
                failedState
            }
        }
    }

    private suspend fun saveMigrationCheckpoint(uid: String, state: CloudMigrationStateEntity) {
        dao.upsertCloudMigrationState(state)
        runCatching {
            cloudRepo.upsertMigrationState(
                CloudMigrationState(
                    id = "sqlite_v14",
                    userId = uid,
                    currentStep = state.currentStep,
                    stepLabel = state.stepLabel,
                    status = state.status,
                    processedItems = state.processedItems,
                    totalItems = state.totalItems,
                    localCustomersCount = state.localCustomersCount,
                    cloudCustomersCount = state.cloudCustomersCount,
                    localUnitsCount = state.localUnitsCount,
                    cloudUnitsCount = state.cloudUnitsCount,
                    localTxCount = state.localTxCount,
                    cloudTxCount = state.cloudTxCount,
                    localPhotosCount = state.localPhotosCount,
                    cloudPhotosCount = state.cloudPhotosCount,
                    lastError = state.lastError,
                    clientUpdatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private fun serializePaymentMethods(methods: List<PaymentMethodEntity>): String {
        val arr = JSONArray()
        methods.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("namaMetode", m.namaMetode)
                    put("kategoriMetode", m.kategoriMetode)
                    put("namaBankAtauPenyedia", m.namaBankAtauPenyedia)
                    put("nomorRekening", m.nomorRekening)
                    put("namaPemilikRekening", m.namaPemilikRekening)
                    put("isAktif", m.isAktif)
                }
            )
        }
        return arr.toString()
    }

    private fun serializeSmartNotif(s: SmartNotificationSettings): String = JSONObject().apply {
        put("enableDueSoonReminder", s.enableDueSoonReminder)
        put("dueReminderDaysBefore", s.dueReminderDaysBefore)
        put("enableOverdueReminder", s.enableOverdueReminder)
        put("enableUnitAddedNotification", s.enableUnitAddedNotification)
        put("enableContractCompletedNotification", s.enableContractCompletedNotification)
        put("enableIncompletePhotoReminder", s.enableIncompletePhotoReminder)
        put("enableUnreturnedUnitReminder", s.enableUnreturnedUnitReminder)
    }.toString()
}
