package com.example.drive

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.WorkManager
import com.example.backup.AutoBackupWorkScheduler
import com.example.data.local.BackupAuditLogEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.DataConsistencyAuditReport
import com.example.data.local.DriveCloudFileMapEntity
import com.example.data.local.DriveCloudManifestState
import com.example.data.local.DriveCloudQuarantineEntity
import com.example.data.local.DriveLocalSyncQueueEntity
import com.example.data.local.DriveManifestRecordEntry
import com.example.data.local.DriveManifestTombstoneEntry
import com.example.data.local.DriveMirrorDashboardState
import com.example.data.local.DrivePhotoMetadataEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.SyncTechnicalLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 3. SyncRepository
 * Central synchronization coordinator bridging:
 * - Local SQLite DAOs & Transactional Outbox (`SyncQueueManager`)
 * - Google Drive REST API (`GoogleDriveService`)
 * - Generation lifecycle (`DatabaseGenerationManager`)
 * - Revision conflict detection & resolution (`SyncConflictResolver`)
 * - 100% SQLite <-> Drive parity auditing (`SyncConsistencyChecker`)
 * - Background WorkManager scheduling (`SyncWorker`)
 */
class SyncRepository(
    private val context: Context,
    private val dao: FreezerDao,
    val generationManager: DatabaseGenerationManager = DatabaseGenerationManager(context),
    val driveService: GoogleDriveService = GoogleDriveService(context),
    val queueManager: SyncQueueManager = SyncQueueManager(dao, generationManager),
    val conflictResolver: SyncConflictResolver = SyncConflictResolver(dao, generationManager),
    val consistencyChecker: SyncConsistencyChecker = SyncConsistencyChecker(
        context = context,
        dao = dao,
        driveService = driveService,
        generationManager = generationManager,
        conflictResolver = conflictResolver
    )
) {
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)

    private val syncMutex = Mutex()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeBackgroundSyncJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _isResetLocked = MutableStateFlow(false)
    val isResetLocked: StateFlow<Boolean> = _isResetLocked.asStateFlow()

    @Volatile
    var simulatedOfflineOverride: Boolean? = null

    @Volatile
    var autoTriggerSyncOnMutation: Boolean = true

    private val _dashboardState = MutableStateFlow(loadInitialDashboardState())
    val dashboardState: StateFlow<DriveMirrorDashboardState> = _dashboardState.asStateFlow()

    fun observeTechnicalLogs(): Flow<List<SyncTechnicalLogEntity>> =
        dao.observeRecentSyncTechnicalLogs()

    fun observeQuarantineItems(): Flow<List<DriveCloudQuarantineEntity>> =
        dao.observeAllDriveCloudQuarantine()

    fun setSimulatedOfflineMode(isOffline: Boolean?) {
        simulatedOfflineOverride = isOffline
    }

    fun isNetworkOnline(): Boolean {
        simulatedOfflineOverride?.let { isOffline -> return !isOffline }
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            @Suppress("DEPRECATION")
            val activeInfo = cm.activeNetworkInfo
            if (activeInfo != null) {
                return activeInfo.isConnected
            }
            val net = cm.activeNetwork ?: return true
            val caps = cm.getNetworkCapabilities(net) ?: return true
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun startConnectivityMonitor() {
        if (networkCallback != null) return
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!_isResetLocked.value && autoTriggerSyncOnMutation) {
                        backgroundScope.launch {
                            val runnable = dao.getRunnableDriveSyncQueueItemsOnce()
                            if (runnable.isNotEmpty()) {
                                processSyncQueue(ignoreBackoffTimers = true)
                            }
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    fun stopConnectivityMonitor() {
        val cb = networkCallback ?: return
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        }
        networkCallback = null
    }

    fun enqueueWorkManagerMirrorSync() {
        if (_isResetLocked.value) return
        SyncWorker.enqueueImmediateSync(context)
    }

    fun triggerBackgroundSyncIfEligible() {
        enqueueWorkManagerMirrorSync()
        if (!autoTriggerSyncOnMutation || _isResetLocked.value || !isNetworkOnline()) return
        if (activeBackgroundSyncJob?.isActive == true) return
        activeBackgroundSyncJob = backgroundScope.launch {
            processSyncQueue(forceRetryFailed = false, ignoreBackoffTimers = false)
        }
    }

    private fun loadInitialDashboardState(): DriveMirrorDashboardState {
        val lastSyncMs = generationManager.getLastSyncTimestampMillis()
        val lastServerMs = generationManager.getLastServerTimestampMillis()
        val lastResetMs = generationManager.getLastResetTimestampMillis()
        val datasetId = generationManager.getCurrentDatasetId()
        val generation = generationManager.getCurrentGenerationId()
        val syncVersion = generationManager.getSyncVersion()
        val datasetStatus = generationManager.getDatasetStatus()
        val auth = GoogleDriveAuthManager.getConnectionStatus(context)
        val initialOverall = if (auth.requiresReAuth) {
            GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH
        } else {
            prefs.getString(DatabaseGenerationManager.KEY_OVERALL_STATUS, GoogleDriveMirrorSyncEngine.STATUS_SYNCED)
                ?: GoogleDriveMirrorSyncEngine.STATUS_SYNCED
        }
        val initialSyncCode = when {
            auth.requiresReAuth -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH
            !isNetworkOnline() && initialOverall != GoogleDriveMirrorSyncEngine.STATUS_SYNCED ->
                GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_NETWORK
            initialOverall == GoogleDriveMirrorSyncEngine.STATUS_FAILED ||
                initialOverall == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT ->
                GoogleDriveMirrorSyncEngine.SYNC_STATUS_FAILED
            initialOverall == GoogleDriveMirrorSyncEngine.STATUS_UPLOADING ||
                initialOverall == GoogleDriveMirrorSyncEngine.STATUS_DOWNLOADING ->
                GoogleDriveMirrorSyncEngine.SYNC_STATUS_RUNNING
            else -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS
        }
        return DriveMirrorDashboardState(
            isDriveConnected = auth.isConnected || !driveService.accessTokenOverride.isNullOrBlank(),
            connectedEmail = auth.accountEmail,
            overallStatus = initialOverall,
            syncStatusCode = initialSyncCode,
            requiresReAuth = auth.requiresReAuth,
            lastErrorMessage = auth.authErrorReason,
            lastSyncTimeDisplay = if (lastSyncMs > 0L) formatDisplayTime(lastSyncMs) else "Belum pernah disinkronkan",
            lastSyncTimestampMillis = lastSyncMs,
            lastServerTimeMillis = lastServerMs,
            serverTimeDisplay = if (lastServerMs > 0L) formatDisplayTime(lastServerMs) else "Belum ada referensi server",
            datasetGeneration = generation,
            isResetLocked = false,
            manifestState = DriveCloudManifestState(
                applicationId = GoogleDriveMirrorSyncEngine.APPLICATION_ID,
                userId = auth.accountEmail.ifBlank { "owner_default" },
                datasetId = datasetId,
                datasetGeneration = generation,
                databaseVersion = MirrorOutboxCodec.CURRENT_SCHEMA_VERSION,
                lastSyncTimestamp = lastSyncMs,
                lastResetTimestamp = lastResetMs,
                resetCompleted = datasetStatus == "EMPTY_AFTER_RESET",
                syncVersion = syncVersion,
                datasetStatus = datasetStatus
            )
        )
    }

    private fun formatDisplayTime(millis: Long): String {
        if (millis <= 0L) return "Belum pernah disinkronkan"
        return SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID")).format(Date(millis))
    }

    private fun extractErrorCode(errText: String): String {
        if (errText.contains("FAILED_AUTH", ignoreCase = true) ||
            errText.contains("HTTP 401", ignoreCase = true) ||
            errText.contains("HTTP_401", ignoreCase = true) ||
            errText.contains("UNAUTHENTICATED", ignoreCase = true)
        ) {
            return DriveApiException.ERROR_CODE_FAILED_AUTH
        }
        val match = Regex("""HTTP[_\s](\d{3})""").find(errText)
        if (match != null) return "HTTP_${match.groupValues[1]}"
        if (errText.contains("offline", ignoreCase = true)) return "NETWORK_OFFLINE"
        if (errText.contains("STALE", ignoreCase = true)) return "STALE_OPERATION"
        return "SYNC_ERROR"
    }

    private fun resolveLocalPhotoFile(pathOrUri: String): File? {
        if (pathOrUri.isBlank()) return null
        val direct = File(pathOrUri)
        if (direct.exists() && direct.isFile) return direct
        if (pathOrUri.startsWith("file://")) {
            val f = File(pathOrUri.removePrefix("file://"))
            if (f.exists() && f.isFile) return f
        }
        val relInRoot = File(File(context.filesDir, "SEWAinAjaFreezer"), pathOrUri)
        if (relInRoot.exists() && relInRoot.isFile) return relInRoot
        return null
    }

    private suspend fun recordTechnicalLog(
        operationType: String,
        recordId: String = "",
        driveFileId: String = "",
        localStatus: String = "",
        cloudStatus: String = "",
        errorCode: String = "",
        errorMessage: String = "",
        retryCount: Int = 0
    ) {
        val now = System.currentTimeMillis()
        runCatching {
            dao.insertSyncTechnicalLog(
                SyncTechnicalLogEntity(
                    operationTimeMillis = now,
                    operationTimeDisplay = formatDisplayTime(now),
                    operationType = operationType,
                    recordId = recordId,
                    driveFileId = driveFileId,
                    datasetGenerationId = generationManager.getCurrentGenerationId(),
                    localStatus = localStatus,
                    cloudStatus = cloudStatus,
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                    retryCount = retryCount
                )
            )
        }
    }

    // =========================================================================
    // OUTBOX COMMIT & ENQUEUE HOOKS
    // =========================================================================

    suspend fun onOutboxCommitted() = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        generationManager.markDatasetActive()
        refreshDashboardStateSnapshot()
        triggerBackgroundSyncIfEligible()
    }

    suspend fun onCustomerOutboxCommitted(
        customer: CustomerEntity,
        isNew: Boolean,
        previousKtpUri: String = "",
        previousCustomerPhotoUri: String = ""
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        enqueueCustomerPhotosInternal(customer, previousKtpUri, previousCustomerPhotoUri)
        onOutboxCommitted()
    }

    suspend fun onCustomerDeletedOutboxCommitted(
        customer: CustomerEntity,
        relatedPhotoSlotKeys: List<String> = emptyList(),
        relatedDriveFileIds: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        for (slotKey in relatedPhotoSlotKeys) {
            if (dao.getLatestDriveSyncQueueForKey(slotKey)?.operationType != GoogleDriveMirrorSyncEngine.OP_DELETE) {
                val cat = if (slotKey.startsWith("PHOTO_KTP")) GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_KTP else GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_CUSTOMER
                enqueuePhotoDeletionBySlotKeyInternal(slotKey, cat, customer.id)
            }
        }
        relatedDriveFileIds.filter { it.isNotBlank() }.distinct().forEachIndexed { idx, fileId ->
            queueManager.enqueueDeleteOperation(
                localRecordKey = "PHOTO_EXTRA:CUSTOMER:${customer.id}:$idx:$fileId",
                recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_CUSTOMER,
                recordId = customer.id,
                explicitDriveFileId = fileId,
                serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
            )
        }
        onOutboxCommitted()
    }

    suspend fun onUnitOutboxCommitted(
        unit: FreezerUnitEntity,
        isNew: Boolean,
        previousPhotoUri: String = ""
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        enqueueUnitPhotosInternal(unit, previousPhotoUri)
        onOutboxCommitted()
    }

    suspend fun onUnitDeletedOutboxCommitted(
        unit: FreezerUnitEntity,
        relatedDriveFileIds: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        relatedDriveFileIds.filter { it.isNotBlank() }.distinct().forEachIndexed { idx, fileId ->
            queueManager.enqueueDeleteOperation(
                localRecordKey = "PHOTO_EXTRA:UNIT:${unit.id}:$idx:$fileId",
                recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_FREEZER,
                recordId = unit.id,
                explicitDriveFileId = fileId,
                serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
            )
        }
        onOutboxCommitted()
    }

    suspend fun onTransactionOutboxCommitted(
        tx: RentalTransactionEntity,
        isNew: Boolean
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        enqueueTransactionPhotosInternal(tx, isNew)
        onOutboxCommitted()
    }

    suspend fun onTransactionDeletedOutboxCommitted(
        tx: RentalTransactionEntity,
        relatedDriveFileIds: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        relatedDriveFileIds.filter { it.isNotBlank() }.distinct().forEachIndexed { idx, fileId ->
            queueManager.enqueueDeleteOperation(
                localRecordKey = "PHOTO_EXTRA:TX:${tx.id}:$idx:$fileId",
                recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                recordId = tx.id,
                explicitDriveFileId = fileId,
                serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
            )
        }
        onOutboxCommitted()
    }

    suspend fun onPaymentOutboxCommitted(
        proof: PaymentProofEntity,
        isNew: Boolean,
        previousPhotoUri: String = ""
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        if (proof.fotoBuktiUri.isNotBlank()) {
            val replaced = previousPhotoUri.isNotBlank() && previousPhotoUri != proof.fotoBuktiUri
            enqueuePhotoSlotSyncInternal(
                slotKey = "PHOTO_PAYMENT:PAYMENT:${proof.id}",
                photoCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_PAYMENT,
                ownerId = proof.id,
                photoPath = proof.fotoBuktiUri,
                targetFileName = GoogleDriveMirrorSyncEngine.buildDeterministicPhotoSlotFileNameStatic(
                    category = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_PAYMENT,
                    ownerId = proof.id
                ),
                isReplace = replaced,
                previousLocalPhotoPath = if (replaced) previousPhotoUri else ""
            )
        }
        onOutboxCommitted()
    }

    private suspend fun enqueueCustomerPhotosInternal(
        customer: CustomerEntity,
        previousKtpUri: String,
        previousCustomerPhotoUri: String
    ) {
        val currentKtp = customer.fotoKtpDepanUri.trim()
        if (currentKtp.isNotBlank()) {
            val ktpReplaced = previousKtpUri.isNotBlank() && previousKtpUri != currentKtp
            enqueuePhotoSlotSyncInternal(
                slotKey = "PHOTO_KTP:CUSTOMER:${customer.id}",
                photoCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_KTP,
                ownerId = customer.id,
                photoPath = currentKtp,
                targetFileName = GoogleDriveMirrorSyncEngine.buildDeterministicPhotoSlotFileNameStatic(
                    GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_KTP,
                    customer.id
                ),
                isReplace = ktpReplaced,
                previousLocalPhotoPath = if (ktpReplaced) previousKtpUri else ""
            )
        } else if (previousKtpUri.isNotBlank()) {
            enqueuePhotoDeletionBySlotKeyInternal("PHOTO_KTP:CUSTOMER:${customer.id}", GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_KTP, customer.id)
        }

        val currentCustPhoto = customer.fotoPelangganUri.trim()
        if (currentCustPhoto.isNotBlank()) {
            val custPhotoReplaced = previousCustomerPhotoUri.isNotBlank() && previousCustomerPhotoUri != currentCustPhoto
            enqueuePhotoSlotSyncInternal(
                slotKey = "PHOTO_CUSTOMER:CUSTOMER:${customer.id}",
                photoCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_CUSTOMER,
                ownerId = customer.id,
                photoPath = currentCustPhoto,
                targetFileName = GoogleDriveMirrorSyncEngine.buildDeterministicPhotoSlotFileNameStatic(
                    GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_CUSTOMER,
                    customer.id
                ),
                isReplace = custPhotoReplaced,
                previousLocalPhotoPath = if (custPhotoReplaced) previousCustomerPhotoUri else ""
            )
        } else if (previousCustomerPhotoUri.isNotBlank()) {
            enqueuePhotoDeletionBySlotKeyInternal("PHOTO_CUSTOMER:CUSTOMER:${customer.id}", GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_CUSTOMER, customer.id)
        }
    }

    private suspend fun enqueueUnitPhotosInternal(unit: FreezerUnitEntity, previousPhotoUri: String) {
        val currentPhoto = unit.fotoUnitUri.trim()
        if (currentPhoto.isNotBlank()) {
            val replaced = previousPhotoUri.isNotBlank() && previousPhotoUri != currentPhoto
            enqueuePhotoSlotSyncInternal(
                slotKey = "PHOTO_FREEZER:UNIT:${unit.id}",
                photoCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_FREEZER,
                ownerId = unit.id,
                photoPath = currentPhoto,
                targetFileName = GoogleDriveMirrorSyncEngine.buildDeterministicPhotoSlotFileNameStatic(
                    GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_FREEZER,
                    unit.id
                ),
                isReplace = replaced,
                previousLocalPhotoPath = if (replaced) previousPhotoUri else ""
            )
        } else if (previousPhotoUri.isNotBlank()) {
            enqueuePhotoDeletionBySlotKeyInternal("PHOTO_FREEZER:UNIT:${unit.id}", GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_FREEZER, unit.id)
        }
    }

    private suspend fun enqueueTransactionPhotosInternal(tx: RentalTransactionEntity, isNew: Boolean) {
        if (tx.fotoSerahTerimaUri.isNotBlank()) {
            enqueuePhotoSlotSyncInternal(
                slotKey = "PHOTO_TRANSACTION:TX:${tx.id}:SERAH_TERIMA",
                photoCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                ownerId = tx.id,
                photoPath = tx.fotoSerahTerimaUri,
                targetFileName = GoogleDriveMirrorSyncEngine.buildDeterministicPhotoSlotFileNameStatic(
                    GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                    tx.id
                ),
                isReplace = !isNew
            )
        }
        if (tx.tandaTanganPelangganUri.isNotBlank()) {
            enqueuePhotoSlotSyncInternal(
                slotKey = "PHOTO_AGREEMENT:TX:${tx.id}:SIGNATURE",
                photoCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_AGREEMENT,
                ownerId = tx.id,
                photoPath = tx.tandaTanganPelangganUri,
                targetFileName = GoogleDriveMirrorSyncEngine.buildDeterministicPhotoSlotFileNameStatic(
                    GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_AGREEMENT,
                    tx.id
                ),
                isReplace = !isNew
            )
        }
    }

    private suspend fun enqueuePhotoSlotSyncInternal(
        slotKey: String,
        photoCategory: String,
        ownerId: Long,
        photoPath: String,
        targetFileName: String,
        isReplace: Boolean,
        previousLocalPhotoPath: String = ""
    ) {
        val localFile = resolveLocalPhotoFile(photoPath) ?: return
        queueManager.enqueuePhotoSlotSync(
            slotKey = slotKey,
            photoCategory = photoCategory,
            ownerId = ownerId,
            localFile = localFile,
            targetFileName = targetFileName,
            isReplace = isReplace,
            previousLocalPhotoPath = previousLocalPhotoPath
        )
    }

    private suspend fun enqueuePhotoDeletionBySlotKeyInternal(
        slotKey: String,
        photoCategory: String,
        ownerId: Long
    ) {
        val existingMap = dao.getDriveCloudFileMapByKey(slotKey)
        if (existingMap != null) {
            dao.updateDriveCloudFileMapStatus(
                localRecordKey = slotKey,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_DELETE_PENDING,
                lastModifiedMillis = System.currentTimeMillis()
            )
        }
        queueManager.enqueueDeleteOperation(
            localRecordKey = slotKey,
            recordCategory = photoCategory,
            recordId = ownerId,
            explicitDriveFileId = existingMap?.driveFileId.orEmpty(),
            serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
        )
    }

    suspend fun enqueueCustomerChange(
        customer: CustomerEntity,
        isNew: Boolean,
        previousKtpUri: String = "",
        previousCustomerPhotoUri: String = ""
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        val serverRef = driveService.currentServerReferenceTimeMillis()
        val payloadJson = MirrorOutboxCodec.serializeCustomerJson(
            customer,
            generationManager.getCurrentDatasetId(),
            generationManager.getCurrentGenerationId(),
            serverRef
        )
        queueManager.enqueueRecordOperation(
            localRecordKey = "CUSTOMER:${customer.id}",
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
            recordId = customer.id,
            operationType = if (isNew) GoogleDriveMirrorSyncEngine.OP_CREATE else GoogleDriveMirrorSyncEngine.OP_UPDATE,
            payloadJson = payloadJson,
            contentHash = MirrorOutboxCodec.computeSha256(payloadJson),
            recordUuid = customer.uuid,
            entityVersion = customer.version,
            updatedAtMillis = customer.updatedAtMillis,
            serverTimestampMillis = serverRef
        )
        enqueueCustomerPhotosInternal(customer, previousKtpUri, previousCustomerPhotoUri)
        onOutboxCommitted()
    }

    suspend fun enqueueCustomerDeletion(
        customer: CustomerEntity,
        relatedPhotoSlotKeys: List<String> = emptyList(),
        relatedDriveFileIds: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        queueManager.enqueueDeleteOperation(
            localRecordKey = "CUSTOMER:${customer.id}",
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
            recordId = customer.id,
            recordUuid = customer.uuid,
            serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
        )
        val allSlotKeys = buildSet {
            add("PHOTO_KTP:CUSTOMER:${customer.id}")
            add("PHOTO_CUSTOMER:CUSTOMER:${customer.id}")
            addAll(relatedPhotoSlotKeys)
        }
        for (slotKey in allSlotKeys) {
            val cat = if (slotKey.startsWith("PHOTO_KTP")) GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_KTP else GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_CUSTOMER
            enqueuePhotoDeletionBySlotKeyInternal(slotKey, cat, customer.id)
        }
        onCustomerDeletedOutboxCommitted(customer, emptyList(), relatedDriveFileIds)
    }

    suspend fun enqueueUnitChange(
        unit: FreezerUnitEntity,
        isNew: Boolean,
        previousPhotoUri: String = ""
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        val serverRef = driveService.currentServerReferenceTimeMillis()
        val payloadJson = MirrorOutboxCodec.serializeUnitJson(
            unit,
            generationManager.getCurrentDatasetId(),
            generationManager.getCurrentGenerationId(),
            serverRef
        )
        queueManager.enqueueRecordOperation(
            localRecordKey = "UNIT:${unit.id}",
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
            recordId = unit.id,
            operationType = if (isNew) GoogleDriveMirrorSyncEngine.OP_CREATE else GoogleDriveMirrorSyncEngine.OP_UPDATE,
            payloadJson = payloadJson,
            contentHash = MirrorOutboxCodec.computeSha256(payloadJson),
            recordUuid = unit.uuid,
            entityVersion = unit.version,
            updatedAtMillis = unit.updatedAtMillis,
            serverTimestampMillis = serverRef
        )
        enqueueUnitPhotosInternal(unit, previousPhotoUri)
        onOutboxCommitted()
    }

    suspend fun enqueueUnitDeletion(
        unit: FreezerUnitEntity,
        relatedDriveFileIds: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        queueManager.enqueueDeleteOperation(
            localRecordKey = "UNIT:${unit.id}",
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
            recordId = unit.id,
            recordUuid = unit.uuid,
            serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
        )
        enqueuePhotoDeletionBySlotKeyInternal(
            "PHOTO_FREEZER:UNIT:${unit.id}",
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_FREEZER,
            unit.id
        )
        onUnitDeletedOutboxCommitted(unit, relatedDriveFileIds)
    }

    suspend fun enqueueTransactionChange(
        tx: RentalTransactionEntity,
        isNew: Boolean
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        val serverRef = driveService.currentServerReferenceTimeMillis()
        val payloadJson = MirrorOutboxCodec.serializeTransactionJson(
            tx,
            generationManager.getCurrentDatasetId(),
            generationManager.getCurrentGenerationId(),
            serverRef
        )
        queueManager.enqueueRecordOperation(
            localRecordKey = "TRANSACTION:${tx.id}",
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION,
            recordId = tx.id,
            operationType = if (isNew) GoogleDriveMirrorSyncEngine.OP_CREATE else GoogleDriveMirrorSyncEngine.OP_UPDATE,
            payloadJson = payloadJson,
            contentHash = MirrorOutboxCodec.computeSha256(payloadJson),
            recordUuid = tx.uuid,
            entityVersion = tx.version,
            updatedAtMillis = tx.updatedAtMillis,
            serverTimestampMillis = serverRef
        )
        enqueueTransactionPhotosInternal(tx, isNew)
        onOutboxCommitted()
    }

    suspend fun enqueueTransactionDeletion(
        tx: RentalTransactionEntity,
        relatedDriveFileIds: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        queueManager.enqueueDeleteOperation(
            localRecordKey = "TRANSACTION:${tx.id}",
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION,
            recordId = tx.id,
            recordUuid = tx.uuid,
            serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
        )
        enqueuePhotoDeletionBySlotKeyInternal(
            "PHOTO_TRANSACTION:TX:${tx.id}:SERAH_TERIMA",
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
            tx.id
        )
        enqueuePhotoDeletionBySlotKeyInternal(
            "PHOTO_AGREEMENT:TX:${tx.id}:SIGNATURE",
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_AGREEMENT,
            tx.id
        )
        onTransactionDeletedOutboxCommitted(tx, relatedDriveFileIds)
    }

    suspend fun enqueuePaymentChange(
        proof: PaymentProofEntity,
        isNew: Boolean,
        previousPhotoUri: String = ""
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        val serverRef = driveService.currentServerReferenceTimeMillis()
        val payloadJson = MirrorOutboxCodec.serializePaymentJson(
            proof,
            generationManager.getCurrentDatasetId(),
            generationManager.getCurrentGenerationId(),
            serverRef
        )
        queueManager.enqueueRecordOperation(
            localRecordKey = "PAYMENT:${proof.id}",
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT,
            recordId = proof.id,
            operationType = if (isNew) GoogleDriveMirrorSyncEngine.OP_CREATE else GoogleDriveMirrorSyncEngine.OP_UPDATE,
            payloadJson = payloadJson,
            contentHash = MirrorOutboxCodec.computeSha256(payloadJson),
            recordUuid = proof.uuid,
            entityVersion = proof.version,
            updatedAtMillis = proof.updatedAtMillis,
            serverTimestampMillis = serverRef
        )
        onPaymentOutboxCommitted(proof, isNew, previousPhotoUri)
    }

    suspend fun enqueuePaymentDeletion(proof: PaymentProofEntity) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        queueManager.enqueueDeleteOperation(
            localRecordKey = "PAYMENT:${proof.id}",
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT,
            recordId = proof.id,
            recordUuid = proof.uuid,
            serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
        )
        enqueuePhotoDeletionBySlotKeyInternal(
            "PHOTO_PAYMENT:PAYMENT:${proof.id}",
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_PAYMENT,
            proof.id
        )
        onOutboxCommitted()
    }

    suspend fun enqueueAppSettingsChange(settingsJson: JSONObject) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        val payloadStr = settingsJson.toString()
        queueManager.enqueueRecordOperation(
            localRecordKey = "SETTINGS:COMPANY",
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_SETTINGS,
            recordId = 1L,
            operationType = GoogleDriveMirrorSyncEngine.OP_UPDATE,
            payloadJson = payloadStr,
            contentHash = MirrorOutboxCodec.computeSha256(payloadStr),
            serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
        )
        onOutboxCommitted()
    }

    suspend fun enqueuePhotoSlotSync(
        slotKey: String,
        photoCategory: String,
        ownerId: Long,
        photoPath: String,
        targetFileName: String = "",
        isReplace: Boolean = false,
        previousLocalPhotoPath: String = ""
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        enqueuePhotoSlotSyncInternal(
            slotKey = slotKey,
            photoCategory = photoCategory,
            ownerId = ownerId,
            photoPath = photoPath,
            targetFileName = targetFileName.ifBlank {
                GoogleDriveMirrorSyncEngine.buildDeterministicPhotoSlotFileNameStatic(photoCategory, ownerId)
            },
            isReplace = isReplace,
            previousLocalPhotoPath = previousLocalPhotoPath
        )
        onOutboxCommitted()
    }

    suspend fun enqueuePhotoDeletionBySlotKey(
        slotKey: String,
        photoCategory: String,
        ownerId: Long
    ) = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) return@withContext
        enqueuePhotoDeletionBySlotKeyInternal(slotKey, photoCategory, ownerId)
        onOutboxCommitted()
    }

    // =========================================================================
    // REAL-TIME SYNC QUEUE PROCESSOR (WORKMANAGER & IMMEDIATELY AFTER COMMIT)
    // =========================================================================

    suspend fun recoverFailedSyncOperations(): Result<MirrorSyncBatchResult> = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) {
            return@withContext Result.success(MirrorSyncBatchResult())
        }
        processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true)
    }

    suspend fun onAuthRecoveredAndResumeQueue(): Result<MirrorSyncBatchResult> = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) {
            return@withContext Result.success(MirrorSyncBatchResult())
        }
        GoogleDriveAuthManager.clearReAuthRequiredFlag(context)
        queueManager.resetStuckUploadingItemsToPending()
        queueManager.resetFailedAuthItemsToPending()
        processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true)
    }

    suspend fun processSyncQueue(
        forceRetryFailed: Boolean = false,
        ignoreBackoffTimers: Boolean = false
    ): Result<MirrorSyncBatchResult> = withContext(Dispatchers.IO) {
        if (_isResetLocked.value) {
            return@withContext Result.success(MirrorSyncBatchResult())
        }

        syncMutex.withLock {
            if (_isResetLocked.value) {
                return@withLock Result.success(MirrorSyncBatchResult())
            }

            // Crash / force-close recovery: any queue row stranded in UPLOADING becomes runnable again
            queueManager.resetStuckUploadingItemsToPending()

            if (!isNetworkOnline()) {
                val allQueueOffline = dao.getAllDriveLocalSyncQueueOnce()
                val pendingOffline = allQueueOffline.count {
                    it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC ||
                        it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_UPLOADING
                }
                val failedOffline = allQueueOffline.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED }
                val failedAuthOffline = allQueueOffline.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH }
                val conflictOffline = allQueueOffline.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT }
                val unfinishedOffline = pendingOffline + failedOffline + failedAuthOffline + conflictOffline
                val offlineStatus = if (unfinishedOffline > 0) {
                    GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_NETWORK
                } else {
                    GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS
                }
                refreshDashboardStateSnapshot(
                    statusOverride = if (unfinishedOffline > 0) GoogleDriveMirrorSyncEngine.STATUS_PENDING else GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
                    syncStatusCodeOverride = offlineStatus,
                    errorMessage = if (unfinishedOffline > 0) {
                        "Perangkat offline. $unfinishedOffline operasi tersimpan aman di SQLite Outbox & otomatis tersinkron saat online."
                    } else ""
                )
                return@withLock Result.success(
                    MirrorSyncBatchResult(
                        remainingPendingCount = unfinishedOffline,
                        overallSyncStatus = offlineStatus,
                        totalPendingInDb = pendingOffline,
                        totalFailedInDb = failedOffline,
                        totalFailedAuthInDb = failedAuthOffline,
                        totalConflictInDb = conflictOffline,
                        errorMessage = if (unfinishedOffline > 0) "Offline" else ""
                    )
                )
            }

            val initialTokenResult = driveService.resolveValidAccessTokenResult()
            val initialToken = when (initialTokenResult) {
                is DriveTokenResult.Valid -> initialTokenResult.accessToken
                is DriveTokenResult.RequiresReAuth -> {
                    val reason = initialTokenResult.reason
                    val allQueueAuth = dao.getAllDriveLocalSyncQueueOnce()
                    val pendingAuth = allQueueAuth.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC }
                    val failedAuthOnly = allQueueAuth.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH }
                    val failedOther = allQueueAuth.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED }
                    val conflictCountAuth = allQueueAuth.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT }
                    recordTechnicalLog(
                        operationType = "OAUTH_TOKEN_VALIDATION",
                        localStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH,
                        cloudStatus = "WAITING_AUTH",
                        errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                        errorMessage = reason
                    )
                    refreshDashboardStateSnapshot(
                        statusOverride = GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH,
                        syncStatusCodeOverride = GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH,
                        errorMessage = reason
                    )
                    return@withLock Result.success(
                        MirrorSyncBatchResult(
                            failedCount = failedOther + failedAuthOnly,
                            remainingPendingCount = pendingAuth + failedOther + failedAuthOnly,
                            errorMessage = reason,
                            overallSyncStatus = GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH,
                            totalPendingInDb = pendingAuth,
                            totalFailedInDb = failedOther,
                            totalFailedAuthInDb = failedAuthOnly,
                            totalConflictInDb = conflictCountAuth,
                            requiresReAuth = true
                        )
                    )
                }
                is DriveTokenResult.TransientError -> {
                    val reason = initialTokenResult.reason
                    refreshDashboardStateSnapshot(
                        statusOverride = GoogleDriveMirrorSyncEngine.STATUS_PENDING,
                        syncStatusCodeOverride = GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_NETWORK,
                        errorMessage = reason
                    )
                    return@withLock Result.failure(IllegalStateException(reason))
                }
            }

            var activeToken = initialToken

            try {
                if (forceRetryFailed) {
                    queueManager.resetFailedItemsToPending()
                }

                val currentGeneration = generationManager.getCurrentGenerationId()
                val currentDatasetId = generationManager.getCurrentDatasetId()
                queueManager.reconcilePendingCoreEntitiesIntoOutbox(
                    datasetId = currentDatasetId,
                    generationId = currentGeneration,
                    serverTimestampMillis = driveService.currentServerReferenceTimeMillis()
                )

                val runnableItems = queueManager.getRunnableQueueItems(ignoreBackoffTimers)
                if (runnableItems.isEmpty()) {
                    val allQueueNow = dao.getAllDriveLocalSyncQueueOnce()
                    val pendingDb = allQueueNow.count {
                        it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC ||
                            it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_UPLOADING
                    }
                    val failedDb = allQueueNow.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED }
                    val failedAuthDb = allQueueNow.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH }
                    val conflictDb = allQueueNow.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT }
                    val exactStatus = computeOverallSyncStatus(
                        authHalted = failedAuthDb > 0 || GoogleDriveAuthManager.getConnectionStatus(context).requiresReAuth,
                        totalPendingInDb = pendingDb,
                        totalFailedInDb = failedDb,
                        totalFailedAuthInDb = failedAuthDb,
                        totalConflictInDb = conflictDb,
                        batchSyncedCount = 0
                    )
                    val refreshed = refreshDashboardStateSnapshot(syncStatusCodeOverride = exactStatus)
                    return@withLock Result.success(
                        MirrorSyncBatchResult(
                            failedCount = failedDb + failedAuthDb + conflictDb,
                            remainingPendingCount = pendingDb + failedDb + failedAuthDb,
                            overallSyncStatus = exactStatus,
                            totalPendingInDb = pendingDb,
                            totalFailedInDb = failedDb,
                            totalFailedAuthInDb = failedAuthDb,
                            totalConflictInDb = conflictDb,
                            requiresReAuth = exactStatus == GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH,
                            errorMessage = refreshed.lastErrorMessage
                        )
                    )
                }

                var processedCount = 0
                var syncedCount = 0
                var skippedIdempotent = 0
                var alreadyResolvedOnCloud = 0
                var staleSkipped = 0
                var failedCount = 0
                var conflictCount = 0
                var authHalted = false
                var lastErrorMsg = ""
                val totalToProcess = runnableItems.size

                for ((index, item) in runnableItems.withIndex()) {
                    if (_isResetLocked.value) break

                    // Always adopt the latest refreshed access token if a previous call refreshed it
                    activeToken = driveService.currentActiveAccessToken(activeToken)

                    if (queueManager.isStaleGenerationOperation(item, currentGeneration)) {
                        staleSkipped++
                        val errMsg = "Operasi dibatalkan karena berasal dari generasi lama (${item.datasetGenerationId} != $currentGeneration)."
                        queueManager.markOperationStale(item, errMsg)
                        recordTechnicalLog(
                            operationType = item.operationType,
                            recordId = item.localRecordKey,
                            localStatus = GoogleDriveMirrorSyncEngine.STATUS_STALE_OPERATION,
                            cloudStatus = "SKIPPED",
                            errorCode = GoogleDriveMirrorSyncEngine.STATUS_STALE_OPERATION,
                            errorMessage = errMsg
                        )
                        continue
                    }

                    processedCount++
                    val percent = (((index + 1).toFloat() / totalToProcess.toFloat()) * 100f).toInt().coerceIn(5, 99)
                    _dashboardState.value = _dashboardState.value.copy(
                        overallStatus = GoogleDriveMirrorSyncEngine.STATUS_UPLOADING,
                        syncStatusCode = GoogleDriveMirrorSyncEngine.SYNC_STATUS_RUNNING,
                        uploadProgressPercent = percent,
                        activeOperationLabel = "Menyinkronkan ${item.localRecordKey} (${item.operationType})..."
                    )

                    val existingMap = dao.getDriveCloudFileMapByKey(item.localRecordKey)
                    if (item.operationType != GoogleDriveMirrorSyncEngine.OP_DELETE &&
                        existingMap != null &&
                        existingMap.datasetGenerationId == currentGeneration &&
                        existingMap.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED &&
                        existingMap.driveFileId.isNotBlank() &&
                        existingMap.contentHash == item.contentHash
                    ) {
                        val verifyExisting = driveService.driveClient.verifyRemoteFileIntegrity(
                            accessToken = activeToken,
                            fileId = existingMap.driveFileId,
                            expectedSha256 = item.contentHash
                        )
                        activeToken = driveService.currentActiveAccessToken(activeToken)
                        if (verifyExisting.isSuccess) {
                            skippedIdempotent++
                            alreadyResolvedOnCloud++
                            queueManager.markOperationSynced(item, existingMap.driveFileId)
                            continue
                        } else if (DriveApiException.isAuthFailure(verifyExisting.exceptionOrNull())) {
                            val authErr = verifyExisting.exceptionOrNull()?.message
                                ?: "HTTP 401 UNAUTHENTICATED — login ulang Google Drive diperlukan."
                            failedCount++
                            lastErrorMsg = authErr
                            authHalted = true
                            queueManager.markOperationFailedAuth(item, existingMap, authErr)
                            recordTechnicalLog(
                                operationType = item.operationType,
                                recordId = item.localRecordKey,
                                driveFileId = existingMap.driveFileId,
                                localStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH,
                                cloudStatus = "WAITING_AUTH",
                                errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                                errorMessage = authErr,
                                retryCount = item.retryCount
                            )
                            break
                        }
                    }

                    queueManager.markOperationUploading(item)

                    val stepResult = try {
                        withTimeout(15_000L) {
                            executeSingleQueueItem(activeToken, item, existingMap)
                        }
                    } catch (e: TimeoutCancellationException) {
                        Result.failure(
                            IllegalStateException("Timeout 15 detik saat sinkronisasi ${item.localRecordKey}: ${e.message}", e)
                        )
                    }

                    activeToken = driveService.currentActiveAccessToken(activeToken)

                    if (stepResult.isSuccess) {
                        val stepOutcome = stepResult.getOrDefault(StepSyncOutcome.EXECUTED)
                        syncedCount++
                        if (stepOutcome == StepSyncOutcome.ALREADY_RESOLVED_ON_CLOUD) {
                            alreadyResolvedOnCloud++
                        }
                        val updatedMap = dao.getDriveCloudFileMapByKey(item.localRecordKey)
                        val resolvedDriveId = updatedMap?.driveFileId ?: item.previousDriveFileId
                        queueManager.markOperationSynced(item, resolvedDriveId)
                        recordTechnicalLog(
                            operationType = item.operationType,
                            recordId = item.localRecordKey,
                            driveFileId = resolvedDriveId,
                            localStatus = GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
                            cloudStatus = when {
                                item.operationType == GoogleDriveMirrorSyncEngine.OP_DELETE -> "DELETED"
                                stepOutcome == StepSyncOutcome.ALREADY_RESOLVED_ON_CLOUD -> "RECONCILED_EXISTING"
                                else -> "VERIFIED"
                            },
                            retryCount = item.retryCount
                        )
                    } else {
                        val err = stepResult.exceptionOrNull()
                        val errText = err?.message ?: "Gagal sinkronisasi item ${item.localRecordKey}"
                        val isAuthErr = DriveApiException.isAuthFailure(err)
                        val isConflict = errText.startsWith(SyncConflictResolver.CONFLICT_ERROR_PREFIX)
                        if (isAuthErr) {
                            failedCount++
                            lastErrorMsg = errText
                            authHalted = true
                            queueManager.markOperationFailedAuth(item, existingMap, errText)
                            recordTechnicalLog(
                                operationType = item.operationType,
                                recordId = item.localRecordKey,
                                driveFileId = existingMap?.driveFileId ?: item.previousDriveFileId,
                                localStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH,
                                cloudStatus = "WAITING_AUTH",
                                errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                                errorMessage = errText,
                                retryCount = item.retryCount
                            )
                            // Halt batch immediately on persistent 401 so remaining items do not cascade into 25x auth failures
                            break
                        } else if (isConflict) {
                            conflictCount++
                            lastErrorMsg = errText
                            queueManager.markOperationConflict(item, existingMap, errText)
                            recordTechnicalLog(
                                operationType = item.operationType,
                                recordId = item.localRecordKey,
                                driveFileId = existingMap?.driveFileId ?: item.previousDriveFileId,
                                localStatus = GoogleDriveMirrorSyncEngine.STATUS_CONFLICT,
                                cloudStatus = "CONFLICT_PROTECTED",
                                errorCode = "VERSION_CONFLICT",
                                errorMessage = errText,
                                retryCount = item.retryCount
                            )
                        } else {
                            failedCount++
                            lastErrorMsg = errText
                            val nextRetryCount = queueManager.markOperationFailedWithBackoff(item, existingMap, errText)
                            recordTechnicalLog(
                                operationType = item.operationType,
                                recordId = item.localRecordKey,
                                driveFileId = existingMap?.driveFileId ?: item.previousDriveFileId,
                                localStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                                cloudStatus = "ERROR",
                                errorCode = extractErrorCode(errText),
                                errorMessage = errText,
                                retryCount = nextRetryCount
                            )
                        }
                    }
                }

                queueManager.purgeCompletedSyncedItems()
                val manifestOk = if (!authHalted && (syncedCount > 0 || skippedIdempotent > 0)) {
                    val tokenForManifest = driveService.currentActiveAccessToken(activeToken)
                    updateCategorySnapshotsOnDrive(tokenForManifest)
                    val manifestRes = updateCloudManifestOnDrive(driveService.currentActiveAccessToken(tokenForManifest))
                    if (manifestRes.isFailure && DriveApiException.isAuthFailure(manifestRes.exceptionOrNull())) {
                        authHalted = true
                        lastErrorMsg = manifestRes.exceptionOrNull()?.message ?: "HTTP 401 saat memperbarui manifest.json"
                    }
                    manifestRes.isSuccess
                } else {
                    false
                }

                val allQueueNow = dao.getAllDriveLocalSyncQueueOnce()
                val totalPendingInDb = allQueueNow.count {
                    it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC ||
                        it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_UPLOADING
                }
                val totalFailedInDb = allQueueNow.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED }
                val totalFailedAuthInDb = allQueueNow.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH }
                val totalConflictInDb = allQueueNow.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT }

                val overallSyncStatus = computeOverallSyncStatus(
                    authHalted = authHalted || totalFailedAuthInDb > 0 || GoogleDriveAuthManager.getConnectionStatus(context).requiresReAuth,
                    totalPendingInDb = totalPendingInDb,
                    totalFailedInDb = totalFailedInDb,
                    totalFailedAuthInDb = totalFailedAuthInDb,
                    totalConflictInDb = totalConflictInDb,
                    batchSyncedCount = syncedCount + skippedIdempotent
                )

                val finalLegacyStatus = when {
                    overallSyncStatus == GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH ->
                        GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH
                    totalConflictInDb > 0 -> GoogleDriveMirrorSyncEngine.STATUS_CONFLICT
                    totalFailedInDb > 0 || failedCount > 0 -> GoogleDriveMirrorSyncEngine.STATUS_FAILED
                    totalPendingInDb > 0 -> GoogleDriveMirrorSyncEngine.STATUS_PENDING
                    else -> GoogleDriveMirrorSyncEngine.STATUS_SYNCED
                }

                val finishedMillis = System.currentTimeMillis()
                val serverRefMs = driveService.currentServerReferenceTimeMillis()
                generationManager.recordSyncBatchMetadata(finishedMillis, serverRefMs, finalLegacyStatus)

                // Automatic quarantine self-healing: release valid active-UUID entries that were transiently quarantined
                runCatching {
                    consistencyChecker.autoRecoverEligibleQuarantineItems(autoPurgeVerifiedTombstones = false)
                }

                refreshDashboardStateSnapshot(
                    statusOverride = finalLegacyStatus,
                    syncStatusCodeOverride = overallSyncStatus,
                    errorMessage = if (overallSyncStatus != GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS) lastErrorMsg else ""
                )

                Result.success(
                    MirrorSyncBatchResult(
                        processedCount = processedCount,
                        syncedCount = syncedCount,
                        skippedIdempotentCount = skippedIdempotent,
                        staleSkippedCount = staleSkipped,
                        failedCount = failedCount + conflictCount,
                        remainingPendingCount = totalPendingInDb + totalFailedInDb + totalFailedAuthInDb,
                        manifestUpdated = manifestOk,
                        errorMessage = lastErrorMsg,
                        overallSyncStatus = overallSyncStatus,
                        totalPendingInDb = totalPendingInDb,
                        totalFailedInDb = totalFailedInDb,
                        totalFailedAuthInDb = totalFailedAuthInDb,
                        totalConflictInDb = totalConflictInDb,
                        recoveredCount = if (forceRetryFailed) syncedCount + skippedIdempotent else 0,
                        alreadyResolvedOnCloudCount = alreadyResolvedOnCloud,
                        requiresReAuth = overallSyncStatus == GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH
                    )
                )
            } catch (e: Exception) {
                val msg = e.message ?: "Kesalahan sinkronisasi cloud mirror"
                val isAuth = DriveApiException.isAuthFailure(e)
                recordTechnicalLog(
                    operationType = "SYNC_QUEUE_BATCH",
                    localStatus = if (isAuth) GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH else GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    cloudStatus = if (isAuth) "WAITING_AUTH" else "ERROR",
                    errorCode = extractErrorCode(msg),
                    errorMessage = msg
                )
                refreshDashboardStateSnapshot(
                    statusOverride = if (isAuth) GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH else GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    syncStatusCodeOverride = if (isAuth) GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH else GoogleDriveMirrorSyncEngine.SYNC_STATUS_FAILED,
                    errorMessage = msg
                )
                Result.failure(e)
            }
        }
    }

    private fun computeOverallSyncStatus(
        authHalted: Boolean,
        totalPendingInDb: Int,
        totalFailedInDb: Int,
        totalFailedAuthInDb: Int,
        totalConflictInDb: Int,
        batchSyncedCount: Int
    ): String {
        val totalFailures = totalFailedInDb + totalConflictInDb
        return when {
            authHalted || totalFailedAuthInDb > 0 -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH
            !isNetworkOnline() && (totalPendingInDb + totalFailures > 0) -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_NETWORK
            totalFailures > 0 && batchSyncedCount > 0 -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_PARTIAL_SUCCESS
            totalFailures > 0 -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_FAILED
            totalPendingInDb > 0 -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_PARTIAL_SUCCESS
            else -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS
        }
    }

    private enum class StepSyncOutcome {
        EXECUTED,
        ALREADY_RESOLVED_ON_CLOUD
    }

    private suspend fun executeSingleQueueItem(
        accessToken: String,
        item: DriveLocalSyncQueueEntity,
        existingMap: DriveCloudFileMapEntity?
    ): Result<StepSyncOutcome> {
        return when (item.operationType) {
            GoogleDriveMirrorSyncEngine.OP_CREATE, GoogleDriveMirrorSyncEngine.OP_UPDATE -> {
                if (driveService.isPhotoCategory(item.recordCategory)) {
                    executePhotoUpsertOrReplace(accessToken, item, existingMap)
                } else {
                    executeJsonRecordUpsert(accessToken, item, existingMap)
                }
            }
            GoogleDriveMirrorSyncEngine.OP_REPLACE_PHOTO -> {
                executePhotoUpsertOrReplace(accessToken, item, existingMap)
            }
            GoogleDriveMirrorSyncEngine.OP_DELETE -> {
                executeDeletePropagation(accessToken, item, existingMap)
            }
            else -> Result.success(StepSyncOutcome.EXECUTED)
        }
    }

    private suspend fun executeJsonRecordUpsert(
        accessToken: String,
        item: DriveLocalSyncQueueEntity,
        existingMap: DriveCloudFileMapEntity?
    ): Result<StepSyncOutcome> {
        val folderId = driveService.ensureCategoryMirrorFolderId(accessToken, item.recordCategory)
            .getOrElse { return Result.failure(it) }
        val activeToken = driveService.currentActiveAccessToken(accessToken)

        val generationId = generationManager.getCurrentGenerationId()
        val datasetId = generationManager.getCurrentDatasetId()
        val coreFallback = queueManager.getCoreUuidAndDriveIdIncludingDeleted(item.recordCategory, item.recordId)
        val isUuidCategory = item.recordCategory != GoogleDriveMirrorSyncEngine.CATEGORY_SETTINGS &&
            item.recordCategory != GoogleDriveMirrorSyncEngine.CATEGORY_CLOUD_MANIFEST
        val resolvedUuid = if (isUuidCategory) {
            MirrorOutboxCodec.ensureValidUuidV4(
                item.recordUuid
                    .ifBlank { existingMap?.recordUuid.orEmpty() }
                    .ifBlank { coreFallback?.first.orEmpty() }
            )
        } else {
            item.recordUuid.ifBlank { existingMap?.recordUuid.orEmpty() }
        }
        val fileName = MirrorOutboxCodec.buildDeterministicRecordFileName(item.recordCategory, item.recordId, resolvedUuid)

        val customProps = MirrorOutboxCodec.buildRecordAppProperties(
            recordUuid = resolvedUuid,
            entityType = item.recordCategory,
            generationId = generationId,
            revision = item.version,
            checksum = item.contentHash,
            syncTimestamp = item.timestampMillis,
            datasetId = datasetId,
            localRecordKey = item.localRecordKey,
            recordId = item.recordId
        )

        var targetDriveFileId = existingMap?.driveFileId.orEmpty()
            .ifBlank { coreFallback?.second.orEmpty() }
        val sameNamedFilesRes = driveService.findAllFilesInFolderByName(activeToken, folderId, fileName)
        if (sameNamedFilesRes.isFailure && DriveApiException.isAuthFailure(sameNamedFilesRes.exceptionOrNull())) {
            return Result.failure(sameNamedFilesRes.exceptionOrNull()!!)
        }
        val sameNamedFiles = sameNamedFilesRes.getOrDefault(emptyList())
        if (targetDriveFileId.isBlank() && sameNamedFiles.isNotEmpty()) {
            targetDriveFileId = sameNamedFiles.first().id
        }
        if (targetDriveFileId.isBlank() && resolvedUuid.isNotBlank()) {
            val uuidMatchesRes = driveService.findActiveFilesMatchingUuidOrName(
                accessToken = driveService.currentActiveAccessToken(activeToken),
                folderIds = listOf(folderId),
                recordUuid = resolvedUuid,
                deterministicNames = listOf(fileName)
            )
            if (uuidMatchesRes.isFailure && DriveApiException.isAuthFailure(uuidMatchesRes.exceptionOrNull())) {
                return Result.failure(uuidMatchesRes.exceptionOrNull()!!)
            }
            val uuidMatches = uuidMatchesRes.getOrDefault(emptyList())
            if (uuidMatches.isNotEmpty()) {
                targetDriveFileId = uuidMatches.first().id
            }
        }

        // Audit remote file state BEFORE retry/upload to prevent duplicate uploads & detect version conflicts
        val remoteCandidate = sameNamedFiles.firstOrNull { it.id == targetDriveFileId }
            ?: sameNamedFiles.firstOrNull()
            ?: if (targetDriveFileId.isNotBlank()) {
                val metaRes = driveService.getFileMetadataById(
                    driveService.currentActiveAccessToken(activeToken),
                    targetDriveFileId
                )
                if (metaRes.isFailure && DriveApiException.isAuthFailure(metaRes.exceptionOrNull())) {
                    return Result.failure(metaRes.exceptionOrNull()!!)
                }
                metaRes.getOrNull()?.takeIf { !it.trashed }
            } else {
                null
            }

        conflictResolver.detectConflict(item, resolvedUuid, remoteCandidate)?.let { conflict ->
            return Result.failure(IllegalStateException(conflict.reason))
        }

        if (sameNamedFiles.size > 1) {
            val keepId = targetDriveFileId.ifBlank { sameNamedFiles.first().id }
            driveService.deduplicateFiles(driveService.currentActiveAccessToken(activeToken), sameNamedFiles, keepId)
            targetDriveFileId = keepId
        }

        // Pre-retry cloud reconciliation: if remote file ALREADY matches exact contentHash + UUID + revision,
        // do NOT duplicate upload — reconcile local mapping cleanly!
        if (remoteCandidate != null &&
            !remoteCandidate.trashed &&
            remoteCandidate.id.isNotBlank() &&
            item.contentHash.isNotBlank() &&
            remoteCandidate.sha256Checksum.equals(item.contentHash, ignoreCase = true) &&
            (resolvedUuid.isBlank() || remoteCandidate.recordUuid.isBlank() || remoteCandidate.recordUuid == resolvedUuid) &&
            (remoteCandidate.remoteVersion == 0L || remoteCandidate.remoteVersion == item.version)
        ) {
            val now = System.currentTimeMillis()
            val serverMs = driveService.currentServerReferenceTimeMillis()
            dao.upsertDriveCloudFileMap(
                DriveCloudFileMapEntity(
                    localRecordKey = item.localRecordKey,
                    recordCategory = item.recordCategory,
                    localRecordId = item.recordId,
                    driveFileId = remoteCandidate.id,
                    driveFolderId = folderId,
                    fileName = fileName,
                    contentHash = item.contentHash,
                    version = item.version,
                    lastModifiedMillis = now,
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
                    datasetGenerationId = generationId,
                    recordUuid = resolvedUuid,
                    serverTimestampMillis = serverMs,
                    conflictReason = ""
                )
            )
            return Result.success(StepSyncOutcome.ALREADY_RESOLVED_ON_CLOUD)
        }

        val uploaded = driveService.uploadOrUpdateJsonRecordWithVerification(
            accessToken = driveService.currentActiveAccessToken(activeToken),
            folderId = folderId,
            existingTargetFileId = targetDriveFileId,
            fileName = fileName,
            payloadJson = item.payloadJson,
            contentHash = item.contentHash,
            recordUuid = resolvedUuid,
            revision = item.version,
            description = "Mirror ${item.localRecordKey} v${item.version}",
            appProperties = customProps
        ).getOrElse { return Result.failure(it) }

        val now = System.currentTimeMillis()
        val serverMs = driveService.currentServerReferenceTimeMillis()
        dao.upsertDriveCloudFileMap(
            DriveCloudFileMapEntity(
                localRecordKey = item.localRecordKey,
                recordCategory = item.recordCategory,
                localRecordId = item.recordId,
                driveFileId = uploaded.id,
                driveFolderId = folderId,
                fileName = fileName,
                contentHash = item.contentHash,
                version = item.version,
                lastModifiedMillis = now,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
                datasetGenerationId = generationId,
                recordUuid = resolvedUuid,
                serverTimestampMillis = serverMs,
                conflictReason = ""
            )
        )
        return Result.success(StepSyncOutcome.EXECUTED)
    }

    private suspend fun executePhotoUpsertOrReplace(
        accessToken: String,
        item: DriveLocalSyncQueueEntity,
        existingMap: DriveCloudFileMapEntity?
    ): Result<StepSyncOutcome> {
        val payloadObj = runCatching { JSONObject(item.payloadJson) }.getOrNull() ?: JSONObject()
        val photoPath = item.newPhotoPath.ifBlank { payloadObj.optString("photoPath", "") }
        val previousLocalPhotoPath = payloadObj.optString("previousLocalPhotoPath", "")
        val localFile = resolveLocalPhotoFile(photoPath)
            ?: return Result.failure(IllegalStateException("File foto lokal tidak ditemukan: $photoPath"))
        if (!localFile.exists() || localFile.length() <= 0L) {
            return Result.failure(IllegalStateException("File foto lokal kosong: ${localFile.absolutePath}"))
        }

        val folderId = driveService.ensureCategoryMirrorFolderId(accessToken, item.recordCategory)
            .getOrElse { return Result.failure(it) }
        val activeToken = driveService.currentActiveAccessToken(accessToken)

        val targetFileName = payloadObj.optString("targetFileName", "")
            .ifBlank { existingMap?.fileName.orEmpty() }
            .ifBlank { GoogleDriveMirrorSyncEngine.buildDeterministicPhotoSlotFileNameStatic(item.recordCategory, item.recordId) }

        val generationId = generationManager.getCurrentGenerationId()
        val resolvedUuid = item.recordUuid.ifBlank { existingMap?.recordUuid.orEmpty() }
        val customProps = MirrorOutboxCodec.buildRecordAppProperties(
            recordUuid = resolvedUuid.ifBlank { item.localRecordKey },
            entityType = item.recordCategory,
            generationId = generationId,
            revision = item.version,
            checksum = item.contentHash,
            syncTimestamp = item.timestampMillis,
            datasetId = generationManager.getCurrentDatasetId(),
            localRecordKey = item.localRecordKey,
            recordId = item.recordId
        )

        var existingDriveFileId = existingMap?.driveFileId?.ifBlank { null }
            ?: item.previousDriveFileId.ifBlank { null }
        val sameNamedFilesRes = driveService.findAllFilesInFolderByName(activeToken, folderId, targetFileName)
        if (sameNamedFilesRes.isFailure && DriveApiException.isAuthFailure(sameNamedFilesRes.exceptionOrNull())) {
            return Result.failure(sameNamedFilesRes.exceptionOrNull()!!)
        }
        val sameNamedFiles = sameNamedFilesRes.getOrDefault(emptyList())
        if (existingDriveFileId.isNullOrBlank() && sameNamedFiles.isNotEmpty()) {
            existingDriveFileId = sameNamedFiles.first().id
        }

        // Pre-retry cloud check: if exact photo with matching SHA-256 already exists on Google Drive,
        // avoid duplicate upload and reconcile mapping directly.
        val alreadyMatchingRemote = sameNamedFiles.firstOrNull { remote ->
            !remote.trashed &&
                remote.id.isNotBlank() &&
                item.contentHash.isNotBlank() &&
                remote.sha256Checksum.equals(item.contentHash, ignoreCase = true)
        }

        val outcome: StepSyncOutcome
        val driveFileItem = if (alreadyMatchingRemote != null) {
            outcome = StepSyncOutcome.ALREADY_RESOLVED_ON_CLOUD
            alreadyMatchingRemote
        } else {
            outcome = StepSyncOutcome.EXECUTED
            driveService.uploadOrReplacePhotoWithVerification(
                accessToken = driveService.currentActiveAccessToken(activeToken),
                folderId = folderId,
                existingDriveFileId = existingDriveFileId,
                localFile = localFile,
                targetFileName = targetFileName,
                contentHash = item.contentHash,
                description = "Foto Mirror ${item.localRecordKey} v${item.version}",
                appProperties = customProps
            ).getOrElse { return Result.failure(it) }
        }

        val latestToken = driveService.currentActiveAccessToken(activeToken)
        if (!existingDriveFileId.isNullOrBlank() && existingDriveFileId != driveFileItem.id) {
            driveService.driveClient.deleteFileStrict(latestToken, existingDriveFileId, targetFileName)
        }
        driveService.deduplicateFiles(latestToken, sameNamedFiles, driveFileItem.id)

        if (item.recordCategory == GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_KTP ||
            item.recordCategory == GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_CUSTOMER
        ) {
            val catCode = if (item.recordCategory == GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_KTP) "KTP" else "DOKUMENTASI_PELANGGAN"
            val existingMetaRows = dao.getDrivePhotoMetadataByCustomerOnce(item.recordId)
                .filter { it.categoryCode == catCode }
            for (oldMeta in existingMetaRows) {
                if (oldMeta.driveFileId.isNotBlank() && oldMeta.driveFileId != driveFileItem.id) {
                    driveService.driveClient.deleteFileStrict(driveService.currentActiveAccessToken(latestToken), oldMeta.driveFileId, oldMeta.fileName)
                }
                dao.deleteDrivePhotoMetadataById(oldMeta.id)
            }
            dao.upsertDrivePhotoMetadata(
                DrivePhotoMetadataEntity(
                    customerId = item.recordId,
                    fileName = targetFileName,
                    categoryCode = catCode,
                    driveRootSection = GoogleDriveMirrorSyncEngine.SUBFOLDER_DOCUMENTATION,
                    driveYearFolder = SimpleDateFormat("yyyy", Locale.US).format(Date()),
                    driveCategoryFolder = "CUSTOMERS",
                    driveFileId = driveFileItem.id,
                    driveFolderId = folderId,
                    originalSizeBytes = localFile.length(),
                    compressedSizeBytes = localFile.length(),
                    uploadDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                    syncStatus = "BERHASIL",
                    accessUrlOrPath = "https://drive.google.com/file/d/${driveFileItem.id}/view",
                    localCompressedPath = localFile.absolutePath,
                    sourceChecksumSha256 = item.contentHash
                )
            )
        } else if (item.recordCategory == GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_FREEZER) {
            val existingMetaRows = dao.getDrivePhotoMetadataByUnitOnce(item.recordId)
                .filter { it.categoryCode == "FOTO_UNIT" }
            for (oldMeta in existingMetaRows) {
                if (oldMeta.driveFileId.isNotBlank() && oldMeta.driveFileId != driveFileItem.id) {
                    driveService.driveClient.deleteFileStrict(driveService.currentActiveAccessToken(latestToken), oldMeta.driveFileId, oldMeta.fileName)
                }
                dao.deleteDrivePhotoMetadataById(oldMeta.id)
            }
            dao.upsertDrivePhotoMetadata(
                DrivePhotoMetadataEntity(
                    unitId = item.recordId,
                    fileName = targetFileName,
                    categoryCode = "FOTO_UNIT",
                    driveRootSection = GoogleDriveMirrorSyncEngine.SUBFOLDER_DOCUMENTATION,
                    driveYearFolder = SimpleDateFormat("yyyy", Locale.US).format(Date()),
                    driveCategoryFolder = "UNITS",
                    driveFileId = driveFileItem.id,
                    driveFolderId = folderId,
                    originalSizeBytes = localFile.length(),
                    compressedSizeBytes = localFile.length(),
                    uploadDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                    syncStatus = "BERHASIL",
                    accessUrlOrPath = "https://drive.google.com/file/d/${driveFileItem.id}/view",
                    localCompressedPath = localFile.absolutePath,
                    sourceChecksumSha256 = item.contentHash
                )
            )
        }

        if (previousLocalPhotoPath.isNotBlank() && previousLocalPhotoPath != localFile.absolutePath) {
            resolveLocalPhotoFile(previousLocalPhotoPath)?.let { oldLocal ->
                if (oldLocal.exists() && oldLocal.absolutePath != localFile.absolutePath) {
                    runCatching { oldLocal.delete() }
                }
            }
        }

        val now = System.currentTimeMillis()
        val serverMs = driveService.currentServerReferenceTimeMillis()
        dao.upsertDriveCloudFileMap(
            DriveCloudFileMapEntity(
                localRecordKey = item.localRecordKey,
                recordCategory = item.recordCategory,
                localRecordId = item.recordId,
                driveFileId = driveFileItem.id,
                driveFolderId = folderId,
                fileName = targetFileName,
                contentHash = item.contentHash,
                version = item.version,
                lastModifiedMillis = now,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
                datasetGenerationId = generationId,
                recordUuid = resolvedUuid,
                serverTimestampMillis = serverMs,
                conflictReason = ""
            )
        )
        return Result.success(outcome)
    }

    private suspend fun executeDeletePropagation(
        accessToken: String,
        item: DriveLocalSyncQueueEntity,
        existingMap: DriveCloudFileMapEntity?
    ): Result<StepSyncOutcome> {
        val payloadObj = runCatching { JSONObject(item.payloadJson) }.getOrNull() ?: JSONObject()
        val coreFallback = queueManager.getCoreUuidAndDriveIdIncludingDeleted(item.recordCategory, item.recordId)
        val resolvedUuid = item.recordUuid
            .ifBlank { coreFallback?.first.orEmpty() }
            .ifBlank { existingMap?.recordUuid.orEmpty() }
            .ifBlank { payloadObj.optString("recordUuid", "") }
        val idsToDelete = linkedSetOf<String>()

        listOf(
            item.previousDriveFileId,
            coreFallback?.second.orEmpty(),
            existingMap?.driveFileId.orEmpty(),
            payloadObj.optString("driveFileId", "")
        ).filter { it.isNotBlank() }.forEach { idsToDelete.add(it) }

        val isPhoto = driveService.isPhotoCategory(item.recordCategory)
        val folderRes = driveService.ensureCategoryMirrorFolderId(accessToken, item.recordCategory)
        if (folderRes.isFailure && DriveApiException.isAuthFailure(folderRes.exceptionOrNull())) {
            return Result.failure(folderRes.exceptionOrNull()!!)
        }
        val folderId = folderRes.getOrNull()
        val activeToken1 = driveService.currentActiveAccessToken(accessToken)

        val deterministicName = if (isPhoto) {
            existingMap?.fileName?.ifBlank { null }
                ?: GoogleDriveMirrorSyncEngine.buildDeterministicPhotoSlotFileNameStatic(item.recordCategory, item.recordId)
        } else {
            MirrorOutboxCodec.buildDeterministicRecordFileName(item.recordCategory, item.recordId, resolvedUuid)
        }
        val legacyName = if (isPhoto) "" else "${item.recordCategory}_${item.recordId}.json"
        val searchNames = listOfNotNull(
            deterministicName.takeIf { it.isNotBlank() },
            legacyName.takeIf { it.isNotBlank() },
            existingMap?.fileName?.takeIf { it.isNotBlank() }
        ).distinct()

        val rootFolderRes = driveService.ensureRootAppFolderId(activeToken1)
        if (rootFolderRes.isFailure && DriveApiException.isAuthFailure(rootFolderRes.exceptionOrNull())) {
            return Result.failure(rootFolderRes.exceptionOrNull()!!)
        }
        val rootFolderId = rootFolderRes.getOrNull()
        val activeToken2 = driveService.currentActiveAccessToken(activeToken1)

        val sectionName = if (isPhoto) GoogleDriveMirrorSyncEngine.SUBFOLDER_DOCUMENTATION else GoogleDriveMirrorSyncEngine.SUBFOLDER_ACTIVE_DATA
        val sectionFolderId = if (!rootFolderId.isNullOrBlank()) {
            val secRes = driveService.ensureNamedChildFolderId(activeToken2, rootFolderId, sectionName, "SEC:$sectionName")
            if (secRes.isFailure && DriveApiException.isAuthFailure(secRes.exceptionOrNull())) {
                return Result.failure(secRes.exceptionOrNull()!!)
            }
            secRes.getOrNull()
        } else null
        val activeToken3 = driveService.currentActiveAccessToken(activeToken2)
        val searchFolders = listOfNotNull(sectionFolderId, folderId).filter { it.isNotBlank() }.distinct()

        if (searchFolders.isNotEmpty()) {
            val folderMatchesRes = driveService.findActiveFilesMatchingUuidOrName(
                accessToken = activeToken3,
                folderIds = searchFolders,
                recordUuid = resolvedUuid,
                deterministicNames = searchNames
            )
            if (folderMatchesRes.isFailure && DriveApiException.isAuthFailure(folderMatchesRes.exceptionOrNull())) {
                return Result.failure(folderMatchesRes.exceptionOrNull()!!)
            }
            folderMatchesRes.getOrDefault(emptyList()).forEach { match ->
                if (match.id.isNotBlank()) idsToDelete.add(match.id)
            }
        }

        if (!isPhoto || resolvedUuid.isNotBlank()) {
            val appMatchesRes = driveService.findFilesByAppId(driveService.currentActiveAccessToken(activeToken3))
            if (appMatchesRes.isFailure && DriveApiException.isAuthFailure(appMatchesRes.exceptionOrNull())) {
                return Result.failure(appMatchesRes.exceptionOrNull()!!)
            }
            appMatchesRes.getOrDefault(emptyList())
                .filter { !it.trashed && it.mimeType != GoogleDriveRestClient.FOLDER_MIME }
                .filter { f ->
                    (resolvedUuid.isNotBlank() && f.recordUuid == resolvedUuid) || (f.name in searchNames)
                }
                .forEach { f ->
                    if (f.id.isNotBlank()) idsToDelete.add(f.id)
                }
        }

        val hadLiveCloudFile = idsToDelete.isNotEmpty()
        for (fileId in idsToDelete) {
            driveService.deleteFileStrictAndVerify(
                accessToken = driveService.currentActiveAccessToken(activeToken3),
                fileId = fileId,
                fileName = deterministicName
            ).getOrElse { return Result.failure(it) }
        }

        updateCloudManifestOnDrive(driveService.currentActiveAccessToken(activeToken3)).getOrElse { manifestErr ->
            if (DriveApiException.isAuthFailure(manifestErr)) {
                return Result.failure(manifestErr)
            }
            return Result.failure(
                IllegalStateException("File terhapus, namun gagal memperbarui manifest.json setelah hapus: ${manifestErr.message}", manifestErr)
            )
        }

        if (searchFolders.isNotEmpty()) {
            val remainingMatches = driveService.findActiveFilesMatchingUuidOrName(
                accessToken = driveService.currentActiveAccessToken(activeToken3),
                folderIds = searchFolders,
                recordUuid = resolvedUuid,
                deterministicNames = searchNames
            ).getOrElse { scanErr ->
                if (DriveApiException.isAuthFailure(scanErr)) {
                    return Result.failure(scanErr)
                }
                return Result.failure(
                    IllegalStateException("Gagal verifikasi pasca-hapus di folder mirror: ${scanErr.message}", scanErr)
                )
            }
            if (remainingMatches.isNotEmpty()) {
                return Result.failure(
                    IllegalStateException(
                        "Verifikasi hapus gagal: masih tersisa ${remainingMatches.size} salinan aktif di Drive (${remainingMatches.joinToString { "${it.name}:${it.id}" }})."
                    )
                )
            }
        }

        dao.deleteDriveCloudFileMapByKey(item.localRecordKey)
        dao.markDriveDeletionTombstoneVerified(
            localRecordKey = item.localRecordKey,
            synced = true,
            uuidVerifiedGone = true,
            manifestUpdatedAtDeletion = true
        )
        return Result.success(
            if (hadLiveCloudFile) StepSyncOutcome.EXECUTED else StepSyncOutcome.ALREADY_RESOLVED_ON_CLOUD
        )
    }

    // =========================================================================
    // CONFLICT RESOLUTION API
    // =========================================================================

    suspend fun resolveConflict(
        localRecordKey: String,
        strategy: ConflictResolutionStrategy = ConflictResolutionStrategy.PREFER_LOCAL_FORCE_PUSH,
        syncNow: Boolean = true
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resolved = when (strategy) {
                ConflictResolutionStrategy.PREFER_LOCAL_FORCE_PUSH,
                ConflictResolutionStrategy.KEEP_HIGHER_REVISION,
                ConflictResolutionStrategy.PREFER_CLOUD_IF_NEWER -> {
                    val active = conflictResolver.getActiveConflictEntries()
                        .firstOrNull { it.localRecordKey == localRecordKey }
                    val targetRev = (active?.remoteRevision ?: 1L) + 1L
                    conflictResolver.resolveConflictForceLocalPush(localRecordKey, targetRev, queueManager)
                }
            }
            if (resolved && syncNow && isNetworkOnline()) {
                processSyncQueue(forceRetryFailed = false, ignoreBackoffTimers = true)
            } else {
                refreshDashboardStateSnapshot()
            }
            Result.success(resolved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // SNAPSHOTS & CLOUD MANIFEST
    // =========================================================================

    private suspend fun updateCategorySnapshotsOnDrive(accessToken: String): Boolean {
        return try {
            val rootId = driveService.ensureRootAppFolderId(accessToken).getOrElse { return false }
            val activeDataId = driveService.ensureNamedChildFolderId(
                accessToken,
                rootId,
                GoogleDriveMirrorSyncEngine.SUBFOLDER_ACTIVE_DATA,
                "CACHE_ACTIVE_DATA"
            ).getOrElse { return false }

            val datasetId = generationManager.getCurrentDatasetId()
            val gen = generationManager.getCurrentGenerationId()
            val serverRef = driveService.currentServerReferenceTimeMillis()

            val snapshots = listOf(
                Triple("customers", "customers.json", JSONArray().apply {
                    dao.getAllCustomersOnce().forEach {
                        put(JSONObject(MirrorOutboxCodec.serializeCustomerJson(it, datasetId, gen, serverRef)))
                    }
                }.toString()),
                Triple("units", "units.json", JSONArray().apply {
                    dao.getAllUnitsOnce().forEach {
                        put(JSONObject(MirrorOutboxCodec.serializeUnitJson(it, datasetId, gen, serverRef)))
                    }
                }.toString()),
                Triple("transactions", "transactions.json", JSONArray().apply {
                    dao.getAllTransactionsOnce().forEach {
                        put(JSONObject(MirrorOutboxCodec.serializeTransactionJson(it, datasetId, gen, serverRef)))
                    }
                }.toString()),
                Triple("payments", "payments.json", JSONArray().apply {
                    dao.getAllPaymentProofsOnce().forEach {
                        put(JSONObject(MirrorOutboxCodec.serializePaymentJson(it, datasetId, gen, serverRef)))
                    }
                }.toString())
            )

            var anyUpdated = false
            for ((catKey, fileName, jsonStr) in snapshots) {
                val mapKey = "SNAPSHOT:${catKey.uppercase(Locale.US)}"
                val existingMap = dao.getDriveCloudFileMapByKey(mapKey)
                val hash = MirrorOutboxCodec.computeSha256(jsonStr)
                val prevHash = prefs.getString("snapshot_hash_$catKey", "").orEmpty()

                val hasData = jsonStr != "[]"
                val existedBefore = existingMap != null && existingMap.driveFileId.isNotBlank() && existingMap.datasetGenerationId == gen
                if (!hasData && !existedBefore) continue
                if (hash == prevHash && existedBefore) continue

                var targetId = if (existedBefore) existingMap!!.driveFileId else ""
                val foundAll = driveService.findAllFilesInFolderByName(accessToken, activeDataId, fileName)
                    .getOrDefault(emptyList())
                if (targetId.isBlank() && foundAll.isNotEmpty()) {
                    targetId = foundAll.first().id
                }
                if (foundAll.size > 1) {
                    val keepId = targetId.ifBlank { foundAll.first().id }
                    driveService.deduplicateFiles(accessToken, foundAll, keepId)
                    targetId = keepId
                }

                val props = MirrorOutboxCodec.buildRecordAppProperties(
                    recordUuid = "SNAPSHOT_${catKey.uppercase(Locale.US)}_$gen",
                    entityType = GoogleDriveMirrorSyncEngine.CATEGORY_SNAPSHOT,
                    generationId = gen,
                    revision = generationManager.getSyncVersion(),
                    checksum = hash,
                    syncTimestamp = serverRef,
                    datasetId = datasetId,
                    localRecordKey = mapKey
                )
                val uploadRes = if (targetId.isNotBlank()) {
                    val patchRes = driveService.driveClient.updateTextContentInPlace(
                        accessToken = driveService.currentActiveAccessToken(accessToken),
                        fileId = targetId,
                        targetFileName = fileName,
                        jsonOrTextContent = jsonStr,
                        mimeType = "application/json",
                        checksumSha256 = hash,
                        description = "Category snapshot $fileName",
                        customAppProperties = props
                    )
                    if (patchRes.isSuccess) {
                        patchRes
                    } else if (DriveApiException.isNotFoundFailure(patchRes.exceptionOrNull())) {
                        driveService.driveClient.uploadTextContentToFolderId(
                            accessToken = driveService.currentActiveAccessToken(accessToken),
                            parentFolderId = activeDataId,
                            targetFileName = fileName,
                            jsonOrTextContent = jsonStr,
                            mimeType = "application/json",
                            checksumSha256 = hash,
                            description = "Category snapshot $fileName",
                            customAppProperties = props
                        )
                    } else {
                        patchRes
                    }
                } else {
                    driveService.driveClient.uploadTextContentToFolderId(
                        accessToken = driveService.currentActiveAccessToken(accessToken),
                        parentFolderId = activeDataId,
                        targetFileName = fileName,
                        jsonOrTextContent = jsonStr,
                        mimeType = "application/json",
                        checksumSha256 = hash,
                        description = "Category snapshot $fileName",
                        customAppProperties = props
                    )
                }

                uploadRes.onSuccess { uploaded ->
                    anyUpdated = true
                    prefs.edit().putString("snapshot_hash_$catKey", hash).apply()
                    dao.upsertDriveCloudFileMap(
                        DriveCloudFileMapEntity(
                            localRecordKey = mapKey,
                            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_SNAPSHOT,
                            localRecordId = 0L,
                            driveFileId = uploaded.id,
                            driveFolderId = activeDataId,
                            fileName = fileName,
                            contentHash = hash,
                            version = 1L,
                            lastModifiedMillis = System.currentTimeMillis(),
                            syncStatus = GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
                            datasetGenerationId = gen
                        )
                    )
                }
            }
            anyUpdated
        } catch (_: Exception) {
            false
        }
    }

    suspend fun buildCurrentManifestState(
        datasetStatusOverride: String? = null
    ): DriveCloudManifestState = withContext(Dispatchers.IO) {
        val customers = dao.getAllCustomersOnce()
        val units = dao.getAllUnitsOnce()
        val txs = dao.getAllTransactionsOnce()
        val payments = dao.getAllPaymentProofsOnce()
        val mappings = dao.getAllDriveCloudFileMappingsOnce()
        val mapByKey = mappings.associateBy { it.localRecordKey }
        val tombstones = dao.getAllDriveDeletionTombstonesOnce()

        val syncedPhotoMappings = mappings.count {
            driveService.isPhotoCategory(it.recordCategory) && it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED
        }
        val drivePhotoMetaCount = dao.getAllDrivePhotoMetadataOnce().count { it.syncStatus == "BERHASIL" }
        val totalPhotos = maxOf(syncedPhotoMappings, drivePhotoMetaCount)

        val activeRecords = buildList {
            customers.forEach { c ->
                val key = "CUSTOMER:${c.id}"
                val m = mapByKey[key]
                add(
                    DriveManifestRecordEntry(
                        localRecordKey = key,
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
                        recordId = c.id,
                        recordUuid = c.uuid,
                        revision = c.revision,
                        driveFileId = m?.driveFileId.orEmpty(),
                        contentHash = m?.contentHash.orEmpty(),
                        updatedAtMillis = c.updatedAtMillis,
                        fileName = m?.fileName?.ifBlank { null }
                            ?: MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER, c.id, c.uuid)
                    )
                )
            }
            units.forEach { u ->
                val key = "UNIT:${u.id}"
                val m = mapByKey[key]
                add(
                    DriveManifestRecordEntry(
                        localRecordKey = key,
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                        recordId = u.id,
                        recordUuid = u.uuid,
                        revision = u.revision,
                        driveFileId = m?.driveFileId.orEmpty(),
                        contentHash = m?.contentHash.orEmpty(),
                        updatedAtMillis = u.updatedAtMillis,
                        fileName = m?.fileName?.ifBlank { null }
                            ?: MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_UNIT, u.id, u.uuid)
                    )
                )
            }
            txs.forEach { tx ->
                val key = "TRANSACTION:${tx.id}"
                val m = mapByKey[key]
                add(
                    DriveManifestRecordEntry(
                        localRecordKey = key,
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION,
                        recordId = tx.id,
                        recordUuid = tx.uuid,
                        revision = tx.revision,
                        driveFileId = m?.driveFileId.orEmpty(),
                        contentHash = m?.contentHash.orEmpty(),
                        updatedAtMillis = tx.updatedAtMillis,
                        fileName = m?.fileName?.ifBlank { null }
                            ?: MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION, tx.id, tx.uuid)
                    )
                )
            }
            payments.forEach { p ->
                val key = "PAYMENT:${p.id}"
                val m = mapByKey[key]
                add(
                    DriveManifestRecordEntry(
                        localRecordKey = key,
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT,
                        recordId = p.id,
                        recordUuid = p.uuid,
                        revision = p.revision,
                        driveFileId = m?.driveFileId.orEmpty(),
                        contentHash = m?.contentHash.orEmpty(),
                        updatedAtMillis = p.updatedAtMillis,
                        fileName = m?.fileName?.ifBlank { null }
                            ?: MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT, p.id, p.uuid)
                    )
                )
            }
        }

        val tombstoneEntries = tombstones.map { t ->
            DriveManifestTombstoneEntry(
                localRecordKey = t.localRecordKey,
                recordCategory = t.recordCategory,
                recordId = t.recordId,
                recordUuid = t.recordUuid,
                deletedVersion = t.deletedVersion,
                deletedAtMillis = t.deletedAtMillis,
                uuidVerifiedGone = t.uuidVerifiedGone
            )
        }

        val now = System.currentTimeMillis()
        val lastResetMs = generationManager.getLastResetTimestampMillis()
        val datasetId = generationManager.getCurrentDatasetId()
        val generation = generationManager.getCurrentGenerationId()
        val status = datasetStatusOverride
            ?: if (customers.isEmpty() && units.isEmpty() && txs.isEmpty() && payments.isEmpty() && lastResetMs > 0L) {
                "EMPTY_AFTER_RESET"
            } else {
                "ACTIVE"
            }
        val syncVer = generationManager.getSyncVersion()
        val email = GoogleDriveAuthManager.getConnectionStatus(context).accountEmail.ifBlank { "owner_default" }

        DriveCloudManifestState(
            applicationId = GoogleDriveMirrorSyncEngine.APPLICATION_ID,
            userId = email,
            datasetId = datasetId,
            datasetGeneration = generation,
            databaseVersion = MirrorOutboxCodec.CURRENT_SCHEMA_VERSION,
            lastSyncTimestamp = now,
            lastResetTimestamp = lastResetMs,
            resetCompleted = status == "EMPTY_AFTER_RESET",
            totalCustomers = customers.size,
            totalUnits = units.size,
            totalTransactions = txs.size,
            totalPayments = payments.size,
            totalPhotos = totalPhotos,
            syncVersion = syncVer,
            datasetStatus = status,
            activeRecords = activeRecords,
            tombstones = tombstoneEntries
        )
    }

    suspend fun updateCloudManifestOnDrive(
        accessToken: String,
        datasetStatusOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val nextVer = generationManager.nextSyncVersion()
            val manifest = buildCurrentManifestState(datasetStatusOverride).copy(syncVersion = nextVer)
            val existingManifestMap = dao.getDriveCloudFileMapByKey(GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_RECORD_KEY)
            val manifestFileId = if (existingManifestMap?.datasetGenerationId == manifest.datasetGeneration) {
                existingManifestMap.driveFileId
            } else {
                ""
            }
            val (item, contentHash) = driveService.uploadManifest(
                accessToken = accessToken,
                manifest = manifest,
                existingManifestFileId = manifestFileId
            ).getOrElse { return@withContext Result.failure(it) }

            val activeDataId = item.parentIds.firstOrNull().orEmpty()
            dao.upsertDriveCloudFileMap(
                DriveCloudFileMapEntity(
                    localRecordKey = GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_RECORD_KEY,
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_CLOUD_MANIFEST,
                    localRecordId = 0L,
                    driveFileId = item.id,
                    driveFolderId = activeDataId,
                    fileName = GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME,
                    contentHash = contentHash,
                    version = manifest.syncVersion,
                    lastModifiedMillis = manifest.lastSyncTimestamp,
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
                    datasetGenerationId = manifest.datasetGeneration
                )
            )
            generationManager.recordManifestSyncSuccess(manifest.lastSyncTimestamp, manifest.datasetStatus)
            Result.success(item.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // FACTORY RESET (LOCAL + CLOUD AND LOCAL ONLY)
    // =========================================================================

    suspend fun executeResetLocalAndCloud(
        preResetBackupCallback: (suspend () -> Boolean)? = null
    ): Result<MirrorResetResult> = withContext(Dispatchers.IO) {
        _isResetLocked.value = true
        activeBackgroundSyncJob?.cancel()
        cancelPendingWorkManagerJobs()

        syncMutex.withLock {
            try {
                _dashboardState.value = _dashboardState.value.copy(
                    overallStatus = GoogleDriveMirrorSyncEngine.STATUS_RESETTING,
                    isResetLocked = true,
                    activeOperationLabel = "Mengunci & mereset data lokal serta Google Drive..."
                )

                var backupCreated = false
                if (preResetBackupCallback != null) {
                    backupCreated = runCatching { preResetBackupCallback.invoke() }.getOrDefault(false)
                }

                val localCustomerCount = dao.getAllCustomersOnce().size
                val localUnitCount = dao.getAllUnitsOnce().size
                val localTxCount = dao.getAllTransactionsOnce().size
                val localPaymentCount = dao.getAllPaymentProofsOnce().size
                val localPhotoMeta = dao.getAllDrivePhotoMetadataOnce()
                val localMappings = dao.getAllDriveCloudFileMappingsOnce()
                val totalLocalRecords = localCustomerCount + localUnitCount + localTxCount + localPaymentCount + localPhotoMeta.size

                if (!isNetworkOnline()) {
                    val offlineErr = "Reset Data Lokal dan Cloud gagal: perangkat sedang offline. Hubungkan internet terlebih dahulu agar Google Drive dapat dibersihkan."
                    recordTechnicalLog(
                        operationType = "RESET_LOCAL_AND_CLOUD",
                        localStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                        cloudStatus = "OFFLINE",
                        errorCode = "NETWORK_OFFLINE",
                        errorMessage = offlineErr
                    )
                    _isResetLocked.value = false
                    refreshDashboardStateSnapshot(statusOverride = GoogleDriveMirrorSyncEngine.STATUS_FAILED, errorMessage = offlineErr)
                    return@withLock Result.failure(IllegalStateException(offlineErr))
                }

                val token = driveService.resolveValidAccessToken().getOrElse { err ->
                    val tokenErr = "Reset Data Lokal dan Cloud gagal: ${err.message}"
                    recordTechnicalLog(
                        operationType = "RESET_LOCAL_AND_CLOUD",
                        localStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                        cloudStatus = "AUTH_REQUIRED",
                        errorCode = "OAUTH_TOKEN_MISSING",
                        errorMessage = tokenErr
                    )
                    _isResetLocked.value = false
                    refreshDashboardStateSnapshot(statusOverride = GoogleDriveMirrorSyncEngine.STATUS_FAILED, errorMessage = tokenErr)
                    return@withLock Result.failure(IllegalStateException(tokenErr, err))
                }

                val deleteOutcome = driveService.wipeAllAppActiveObjectsStrict(
                    accessToken = token,
                    localMappings = localMappings,
                    localPhotoMeta = localPhotoMeta
                )
                if (deleteOutcome.failures.isNotEmpty() || !deleteOutcome.fatalError.isNullOrBlank()) {
                    val detailFailures = deleteOutcome.failures.joinToString("; ") { f ->
                        "${f.fileName} (ID=${f.fileId}, ${f.errorCode}: ${f.errorMessage})"
                    }
                    val fullErr = buildString {
                        append("Reset Cloud Drive GAGAL: tidak semua file berhasil dihapus. ")
                        if (!deleteOutcome.fatalError.isNullOrBlank()) append(deleteOutcome.fatalError).append(". ")
                        if (detailFailures.isNotBlank()) append("File gagal: ").append(detailFailures)
                    }
                    recordTechnicalLog(
                        operationType = "RESET_LOCAL_AND_CLOUD",
                        driveFileId = deleteOutcome.failures.joinToString(",") { it.fileId },
                        localStatus = "ABORTED",
                        cloudStatus = "DELETE_FAILED",
                        errorCode = deleteOutcome.failures.firstOrNull()?.errorCode ?: "DRIVE_RESET_FAILED",
                        errorMessage = fullErr
                    )
                    _isResetLocked.value = false
                    refreshDashboardStateSnapshot(statusOverride = GoogleDriveMirrorSyncEngine.STATUS_FAILED, errorMessage = fullErr)
                    return@withLock Result.failure(IllegalStateException(fullErr))
                }

                deleteLocalAppPhotosOnDisk()
                dao.clearAllCoreAndSyncTablesForFullReset(
                    keepTombstones = false,
                    clearLocalBackupHistory = false
                )
                driveService.clearFolderCache()
                GoogleDrivePhotoCenterManager.clearFolderCache(context)
                clearSnapshotHashes()

                val rotation = generationManager.rotateGenerationOnFactoryReset(isCloudAlsoReset = true)
                val resetMillis = rotation.resetTimestampMillis
                val newGeneration = rotation.newGenerationId
                val newDatasetId = rotation.newDatasetId

                val freshManifestFileId = updateCloudManifestOnDrive(
                    accessToken = token,
                    datasetStatusOverride = "EMPTY_AFTER_RESET"
                ).getOrElse { manifestErr ->
                    val errText = "Data lokal & cloud terhapus, namun gagal menulis manifest.json baru: ${manifestErr.message}"
                    recordTechnicalLog(
                        operationType = "RESET_LOCAL_AND_CLOUD",
                        localStatus = "EMPTY",
                        cloudStatus = "MANIFEST_FAILED",
                        errorCode = extractErrorCode(errText),
                        errorMessage = errText
                    )
                    _isResetLocked.value = false
                    refreshDashboardStateSnapshot(statusOverride = GoogleDriveMirrorSyncEngine.STATUS_FAILED, errorMessage = errText)
                    return@withLock Result.failure(IllegalStateException(errText, manifestErr))
                }

                val remainingLocalTotal = dao.getAllCustomersOnce().size +
                    dao.getAllUnitsOnce().size +
                    dao.getAllTransactionsOnce().size +
                    dao.getAllPaymentProofsOnce().size +
                    dao.getAllDriveLocalSyncQueueOnce().size
                require(remainingLocalTotal == 0) {
                    "Verifikasi reset lokal gagal: masih tersisa $remainingLocalTotal baris di SQLite."
                }

                dao.insertBackupAuditLog(
                    BackupAuditLogEntity(
                        actionType = "RESET_LOCAL_AND_CLOUD",
                        status = "SUKSES",
                        fileName = GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME,
                        recordCount = totalLocalRecords,
                        actorEmail = GoogleDriveAuthManager.getConnectionStatus(context).accountEmail,
                        detailMessage = "Reset Total Lokal ($totalLocalRecords item) & Cloud (${deleteOutcome.deletedCount} objek) [$newGeneration]. Dataset baru: $newDatasetId",
                        timestampDisplay = formatDisplayTime(resetMillis),
                        timestampMillis = resetMillis
                    )
                )
                recordTechnicalLog(
                    operationType = "RESET_LOCAL_AND_CLOUD",
                    recordId = newDatasetId,
                    driveFileId = freshManifestFileId,
                    localStatus = "EMPTY_VERIFIED",
                    cloudStatus = "EMPTY_VERIFIED",
                    errorMessage = "Dihapus: lokal=$totalLocalRecords, cloud=${deleteOutcome.deletedCount}"
                )

                _isResetLocked.value = false
                refreshDashboardStateSnapshot(statusOverride = GoogleDriveMirrorSyncEngine.STATUS_SYNCED, errorMessage = "")

                Result.success(
                    MirrorResetResult(
                        resetMode = "LOCAL_AND_CLOUD",
                        deletedLocalRecordsCount = totalLocalRecords,
                        deletedCloudFilesCount = deleteOutcome.deletedCount,
                        newDatasetId = newDatasetId,
                        resetTimestampMillis = resetMillis,
                        freshManifestFileId = freshManifestFileId,
                        preResetBackupCreated = backupCreated,
                        datasetGeneration = newGeneration
                    )
                )
            } catch (e: Exception) {
                val msg = e.message ?: "Gagal mereset data lokal dan cloud"
                recordTechnicalLog(
                    operationType = "RESET_LOCAL_AND_CLOUD",
                    localStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    cloudStatus = "ERROR",
                    errorCode = extractErrorCode(msg),
                    errorMessage = msg
                )
                _isResetLocked.value = false
                refreshDashboardStateSnapshot(statusOverride = GoogleDriveMirrorSyncEngine.STATUS_FAILED, errorMessage = msg)
                Result.failure(e)
            }
        }
    }

    suspend fun executeResetLocalOnly(): Result<MirrorResetResult> = withContext(Dispatchers.IO) {
        _isResetLocked.value = true
        activeBackgroundSyncJob?.cancel()
        cancelPendingWorkManagerJobs()

        syncMutex.withLock {
            try {
                _dashboardState.value = _dashboardState.value.copy(
                    overallStatus = GoogleDriveMirrorSyncEngine.STATUS_RESETTING,
                    isResetLocked = true,
                    activeOperationLabel = "Mereset seluruh data lokal..."
                )
                val totalLocalRecords = dao.getAllCustomersOnce().size +
                    dao.getAllUnitsOnce().size +
                    dao.getAllTransactionsOnce().size +
                    dao.getAllPaymentProofsOnce().size

                deleteLocalAppPhotosOnDisk()
                dao.clearAllCoreAndSyncTablesForFullReset(
                    keepTombstones = false,
                    clearLocalBackupHistory = false
                )
                driveService.clearFolderCache()
                GoogleDrivePhotoCenterManager.clearFolderCache(context)
                clearSnapshotHashes()

                val rotation = generationManager.rotateGenerationOnFactoryReset(isCloudAlsoReset = false)
                val resetMillis = rotation.resetTimestampMillis
                val newGeneration = rotation.newGenerationId
                val newDatasetId = rotation.newDatasetId

                dao.insertBackupAuditLog(
                    BackupAuditLogEntity(
                        actionType = "RESET_LOCAL_ONLY",
                        status = "SUKSES",
                        fileName = "sewain_aja_freezer_soc.db",
                        recordCount = totalLocalRecords,
                        actorEmail = GoogleDriveAuthManager.getConnectionStatus(context).accountEmail,
                        detailMessage = "Reset Data Lokal Saja ($totalLocalRecords item) [$newGeneration]. File Google Drive dipertahankan.",
                        timestampDisplay = formatDisplayTime(resetMillis),
                        timestampMillis = resetMillis
                    )
                )
                recordTechnicalLog(
                    operationType = "RESET_LOCAL_ONLY",
                    recordId = newDatasetId,
                    localStatus = "EMPTY_VERIFIED",
                    cloudStatus = "PRESERVED"
                )

                _isResetLocked.value = false
                refreshDashboardStateSnapshot(statusOverride = GoogleDriveMirrorSyncEngine.STATUS_SYNCED, errorMessage = "")

                Result.success(
                    MirrorResetResult(
                        resetMode = "LOCAL_ONLY",
                        deletedLocalRecordsCount = totalLocalRecords,
                        deletedCloudFilesCount = 0,
                        newDatasetId = newDatasetId,
                        resetTimestampMillis = resetMillis,
                        datasetGeneration = newGeneration
                    )
                )
            } catch (e: Exception) {
                val msg = e.message ?: "Gagal mereset data lokal"
                recordTechnicalLog(
                    operationType = "RESET_LOCAL_ONLY",
                    localStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    errorCode = extractErrorCode(msg),
                    errorMessage = msg
                )
                _isResetLocked.value = false
                refreshDashboardStateSnapshot(statusOverride = GoogleDriveMirrorSyncEngine.STATUS_FAILED, errorMessage = msg)
                Result.failure(e)
            }
        }
    }

    private fun cancelPendingWorkManagerJobs() {
        runCatching {
            val wm = WorkManager.getInstance(context)
            SyncWorker.cancelScheduledSync(context)
            wm.cancelUniqueWork(GoogleDrivePhotoCenterManager.UNIQUE_DRIVE_PHOTO_SYNC_WORK)
            wm.cancelUniqueWork(AutoBackupWorkScheduler.UNIQUE_IMMEDIATE_BACKUP_WORK)
        }
    }

    private fun deleteLocalAppPhotosOnDisk() {
        val dirsToClean = listOf(
            File(context.filesDir, "SEWAinAjaFreezer"),
            File(context.filesDir, "drive_photos_compressed"),
            File(context.filesDir, "drive_photos_thumbs"),
            File(context.filesDir, "drive_compressed"),
            File(context.filesDir, "drive_temp_originals"),
            File(context.filesDir, "customer_photos"),
            File(context.filesDir, "photos"),
            File(context.filesDir, "invoices"),
            File(context.filesDir, "returns"),
            File(context.cacheDir, "drive_photo_temp"),
            File(context.cacheDir, "drive_thumbnails")
        )
        for (dir in dirsToClean) {
            if (dir.exists()) {
                runCatching { dir.deleteRecursively() }
            }
        }
    }

    private fun clearSnapshotHashes() {
        prefs.edit()
            .remove("snapshot_hash_customers")
            .remove("snapshot_hash_units")
            .remove("snapshot_hash_transactions")
            .remove("snapshot_hash_payments")
            .remove("snapshot_hash_settings")
            .apply()
    }

    // =========================================================================
    // ANTI-RESURRECTION & CONSISTENCY CHECKS
    // =========================================================================

    suspend fun canApplyRemoteRecordWithoutResurrection(
        localRecordKey: String,
        remoteDatasetId: String,
        remoteTimestampMillis: Long,
        remoteVersion: Long,
        recordUuid: String = "",
        remoteGenerationId: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (remoteGenerationId.isNotBlank() && generationManager.isGenerationInvalidated(remoteGenerationId)) {
            return@withContext false
        }

        val lastResetMs = generationManager.getLastResetTimestampMillis()
        val currentDatasetId = generationManager.getCurrentDatasetId()

        if (lastResetMs > 0L && (remoteTimestampMillis == 0L || remoteTimestampMillis < lastResetMs)) {
            return@withContext false
        }
        if (lastResetMs > 0L && remoteDatasetId.isNotBlank() && remoteDatasetId != currentDatasetId) {
            return@withContext false
        }

        val tombstone = dao.getDriveDeletionTombstoneByKey(localRecordKey)
        if (tombstone != null && remoteVersion <= tombstone.deletedVersion) {
            return@withContext false
        }
        if (recordUuid.isNotBlank()) {
            val uuidTombstone = dao.getDriveDeletionTombstoneByUuid(recordUuid)
            if (uuidTombstone != null && remoteVersion <= uuidTombstone.deletedVersion) {
                return@withContext false
            }
        }

        val localMap = dao.getDriveCloudFileMapByKey(localRecordKey)
        if (localMap != null && localMap.version > remoteVersion) {
            return@withContext false
        }

        if (recordUuid.isNotBlank()) {
            val localVersionAndUpdate = when {
                localRecordKey.startsWith("UNIT:") ->
                    dao.getUnitByUuid(recordUuid)?.let { it.version to it.updatedAtMillis }
                localRecordKey.startsWith("CUSTOMER:") ->
                    dao.getCustomerByUuid(recordUuid)?.let { it.version to it.updatedAtMillis }
                localRecordKey.startsWith("TRANSACTION:") ->
                    dao.getTransactionByUuid(recordUuid)?.let { it.version to it.updatedAtMillis }
                localRecordKey.startsWith("PAYMENT:") ->
                    dao.getPaymentProofByUuid(recordUuid)?.let { it.version to it.updatedAtMillis }
                else -> null
            }
            if (localVersionAndUpdate != null) {
                val (localVer, localUpdatedMs) = localVersionAndUpdate
                if (localVer > remoteVersion) return@withContext false
                if (localVer == remoteVersion && localUpdatedMs > remoteTimestampMillis) return@withContext false
            }
        }

        true
    }

    suspend fun verifyConsistencyOnAppStartup(): Result<DriveMirrorDashboardState> = withContext(Dispatchers.IO) {
        try {
            refreshDashboardStateSnapshot()
            if (isNetworkOnline() && !_isResetLocked.value) {
                val runnable = dao.getRunnableDriveSyncQueueItemsOnce()
                if (runnable.isNotEmpty()) {
                    processSyncQueue(ignoreBackoffTimers = true)
                }
            }
            Result.success(_dashboardState.value)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runDataConsistencyAudit(): Result<DataConsistencyAuditReport> {
        val res = consistencyChecker.runDataConsistencyAudit()
        refreshDashboardStateSnapshot()
        return res
    }

    suspend fun scanAndQuarantineStaleCloudFiles(): Result<List<DriveCloudQuarantineEntity>> {
        val res = consistencyChecker.scanAndQuarantineStaleCloudFiles()
        refreshDashboardStateSnapshot()
        return res
    }

    suspend fun purgeVerifiedQuarantinedFiles(): Result<Int> {
        val res = consistencyChecker.purgeVerifiedQuarantinedFiles()
        refreshDashboardStateSnapshot()
        return res
    }

    /**
     * Computes the highest observed Cloud invoice sequence for `INV/SAF/YYYY/XXXX`
     * from persisted high-water metadata + active Google Drive transaction files (best-effort when online).
     */
    suspend fun fetchCloudMaxInvoiceSequenceForYear(year: Int): Int = withContext(Dispatchers.IO) {
        var maxSeq = generationManager.getObservedMaxInvoiceSequenceForYear(year)
        if (!isNetworkOnline()) return@withContext maxSeq
        val token = driveService.resolveValidAccessToken().getOrNull() ?: return@withContext maxSeq
        runCatching {
            val (activeObjects, _) = driveService.listMirrorActiveAndDocObjects(token).getOrNull() ?: return@runCatching
            val txFolderIds = activeObjects
                .filter { it.isFolder && it.item.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_TRANSACTIONS }
                .map { it.item.id }
                .toSet()
            val txJsonFiles = activeObjects.filter { obj ->
                !obj.isFolder &&
                    !obj.item.trashed &&
                    obj.item.name.endsWith(".json", ignoreCase = true) &&
                    (obj.parentFolderId in txFolderIds || obj.item.name.startsWith("TRANSACTION_"))
            }.map { it.item }

            for (file in txJsonFiles) {
                val raw = driveService.driveClient.downloadFileText(driveService.currentActiveAccessToken(token), file.id).getOrNull()
                if (!raw.isNullOrBlank()) {
                    val inv = runCatching { JSONObject(raw).optString("nomorInvoice", "") }.getOrDefault("")
                    if (inv.isNotBlank()) {
                        generationManager.recordObservedInvoiceNumber(inv)
                        val parsed = generationManager.parseInvoiceYearAndSequence(inv)
                        if (parsed != null && parsed.first == year && parsed.second > maxSeq) {
                            maxSeq = parsed.second
                        }
                    }
                }
            }
        }
        maxSeq
    }

    /**
     * Full "Bersihkan Orphan & Sinkron Ulang" workflow:
     * 1. Deletes ORPHAN_CLOUD `.json` files without local SQLite UUID match, deduplicates duplicate cloud files,
     *    and resets Stale/Quarantine markers.
     * 2. Reconciles active local records into outbox and runs full sync + manifest update.
     * 3. Re-audits consistency so Orphan, Duplikat, and Stale all return to 0.
     */
    suspend fun cleanOrphanCloudAndResync(): Result<OrphanCleanupAndResyncReport> = withContext(Dispatchers.IO) {
        val cleanupRes = consistencyChecker.cleanOrphanCloudAndReconcile()
        val cleanup = cleanupRes.getOrElse { return@withContext Result.failure(it) }

        val currentGeneration = generationManager.getCurrentGenerationId()
        val currentDatasetId = generationManager.getCurrentDatasetId()
        val serverRef = driveService.currentServerReferenceTimeMillis()

        // Ensure every active local record missing from cloud is re-enqueued cleanly
        val preAudit = consistencyChecker.runDataConsistencyAudit().getOrNull()
        if (preAudit != null && preAudit.missingInCloud.isNotEmpty()) {
            val allCustomers = dao.getAllCustomersOnce()
            val allUnits = dao.getAllUnitsOnce()
            val allTx = dao.getAllTransactionsOnce()
            val allPay = dao.getAllPaymentProofsOnce()

            allCustomers.forEach { c ->
                val fName = MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER, c.id, c.uuid)
                if (fName in preAudit.missingInCloud) {
                    val payload = MirrorOutboxCodec.serializeCustomerJson(c, currentDatasetId, currentGeneration, serverRef)
                    queueManager.enqueueRecordOperation(
                        localRecordKey = "CUSTOMER:${c.id}",
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
                        recordId = c.id,
                        operationType = GoogleDriveMirrorSyncEngine.OP_CREATE,
                        payloadJson = payload,
                        contentHash = MirrorOutboxCodec.computeSha256(payload),
                        recordUuid = c.uuid,
                        entityVersion = c.version,
                        updatedAtMillis = c.updatedAtMillis
                    )
                }
            }
            allUnits.forEach { u ->
                val fName = MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_UNIT, u.id, u.uuid)
                if (fName in preAudit.missingInCloud) {
                    val payload = MirrorOutboxCodec.serializeUnitJson(u, currentDatasetId, currentGeneration, serverRef)
                    queueManager.enqueueRecordOperation(
                        localRecordKey = "UNIT:${u.id}",
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                        recordId = u.id,
                        operationType = GoogleDriveMirrorSyncEngine.OP_CREATE,
                        payloadJson = payload,
                        contentHash = MirrorOutboxCodec.computeSha256(payload),
                        recordUuid = u.uuid,
                        entityVersion = u.version,
                        updatedAtMillis = u.updatedAtMillis
                    )
                }
            }
            allTx.forEach { tx ->
                val fName = MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION, tx.id, tx.uuid)
                if (fName in preAudit.missingInCloud) {
                    val payload = MirrorOutboxCodec.serializeTransactionJson(tx, currentDatasetId, currentGeneration, serverRef)
                    queueManager.enqueueRecordOperation(
                        localRecordKey = "TRANSACTION:${tx.id}",
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION,
                        recordId = tx.id,
                        operationType = GoogleDriveMirrorSyncEngine.OP_CREATE,
                        payloadJson = payload,
                        contentHash = MirrorOutboxCodec.computeSha256(payload),
                        recordUuid = tx.uuid,
                        entityVersion = tx.version,
                        updatedAtMillis = tx.updatedAtMillis
                    )
                }
            }
            allPay.forEach { p ->
                val fName = MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT, p.id, p.uuid)
                if (fName in preAudit.missingInCloud) {
                    val payload = MirrorOutboxCodec.serializePaymentJson(p, currentDatasetId, currentGeneration, serverRef)
                    queueManager.enqueueRecordOperation(
                        localRecordKey = "PAYMENT:${p.id}",
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT,
                        recordId = p.id,
                        operationType = GoogleDriveMirrorSyncEngine.OP_CREATE,
                        payloadJson = payload,
                        contentHash = MirrorOutboxCodec.computeSha256(payload),
                        recordUuid = p.uuid,
                        entityVersion = p.version,
                        updatedAtMillis = p.updatedAtMillis
                    )
                }
            }
        }

        val syncBatch = processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true).getOrNull()
        val token = driveService.resolveValidAccessToken().getOrNull()
        if (token != null) {
            runCatching { updateCloudManifestOnDrive(token) }
        }

        val postAudit = consistencyChecker.runDataConsistencyAudit().getOrNull()
        refreshDashboardStateSnapshot()

        val finalSummary = buildString {
            append("Orphan dihapus: ${cleanup.deletedOrphanCloudFiles}, Duplikat dihapus: ${cleanup.deletedDuplicateCloudFiles}, Stale direset: ${cleanup.clearedStaleQueueRows}.")
            if (postAudit != null) {
                append(" Audit akhir: Orphan/Stale=${postAudit.staleRecords.size}, Duplikat=${postAudit.duplicateCloudFiles.size}, Missing=${postAudit.missingInCloud.size}.")
            }
        }
        Result.success(
            cleanup.copy(
                resyncedPendingRecords = (syncBatch?.syncedCount ?: 0) + (syncBatch?.skippedIdempotentCount ?: 0),
                postCleanupAudit = postAudit,
                summaryMessage = finalSummary
            )
        )
    }

    suspend fun refreshDashboardStateSnapshot(
        statusOverride: String? = null,
        errorMessage: String? = null,
        syncStatusCodeOverride: String? = null
    ): DriveMirrorDashboardState = withContext(Dispatchers.IO) {
        val mappings = dao.getAllDriveCloudFileMappingsOnce()
        val queueItems = dao.getAllDriveLocalSyncQueueOnce()
        val photoMeta = dao.getAllDrivePhotoMetadataOnce()
        val generation = generationManager.getCurrentGenerationId()
        val operationalCategories = setOf(
            GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
            GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
            GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION,
            GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT
        )

        val totalLocalCount = dao.getAllCustomersOnce().size +
            dao.getAllUnitsOnce().size +
            dao.getAllTransactionsOnce().size +
            dao.getAllPaymentProofsOnce().size

        val syncedCloudMappingsCount = mappings.count {
            it.recordCategory in operationalCategories &&
                it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED &&
                it.driveFileId.isNotBlank()
        }
        val auditedCloudCount = generationManager.getLastAuditedCloudCount()
        val totalCloudCount = if (auditedCloudCount >= 0) auditedCloudCount else syncedCloudMappingsCount

        val syncedDataCount = mappings.count {
            !driveService.isPhotoCategory(it.recordCategory) &&
                it.recordCategory != GoogleDriveMirrorSyncEngine.CATEGORY_CLOUD_MANIFEST &&
                it.recordCategory != GoogleDriveMirrorSyncEngine.CATEGORY_SNAPSHOT &&
                it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED
        }
        val syncedPhotoMappings = mappings.count {
            driveService.isPhotoCategory(it.recordCategory) && it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED
        }
        val syncedPhotoMeta = photoMeta.count { it.syncStatus == "BERHASIL" }
        val totalSyncedPhotos = maxOf(syncedPhotoMappings, syncedPhotoMeta)

        val pendingQueueCount = queueItems.count {
            it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC ||
                it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_UPLOADING
        }
        val failedGeneralCount = queueItems.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED }
        val failedAuthQueueCount = queueItems.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH }
        val totalFailedQueueCount = failedGeneralCount + failedAuthQueueCount
        val staleDataCount = queueItems.count {
            it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_STALE_OPERATION ||
                it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_STALE
        }
        val conflictKeys = buildSet {
            queueItems.filter { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT }.forEach { add(it.localRecordKey) }
            mappings.filter { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT }.forEach { add(it.localRecordKey) }
        }
        val conflictDataCount = conflictKeys.size

        val auth = GoogleDriveAuthManager.getConnectionStatus(context)
        val needsReAuth = auth.requiresReAuth || failedAuthQueueCount > 0 ||
            statusOverride == GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH ||
            syncStatusCodeOverride == GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH

        val computedStatus = statusOverride ?: when {
            _isResetLocked.value -> GoogleDriveMirrorSyncEngine.STATUS_RESETTING
            needsReAuth -> GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH
            conflictDataCount > 0 -> GoogleDriveMirrorSyncEngine.STATUS_CONFLICT
            totalFailedQueueCount > 0 -> GoogleDriveMirrorSyncEngine.STATUS_FAILED
            pendingQueueCount > 0 -> GoogleDriveMirrorSyncEngine.STATUS_PENDING
            else -> GoogleDriveMirrorSyncEngine.STATUS_SYNCED
        }

        val computedSyncStatusCode = syncStatusCodeOverride ?: when {
            _isResetLocked.value ||
                computedStatus == GoogleDriveMirrorSyncEngine.STATUS_UPLOADING ||
                computedStatus == GoogleDriveMirrorSyncEngine.STATUS_DOWNLOADING ->
                GoogleDriveMirrorSyncEngine.SYNC_STATUS_RUNNING
            needsReAuth -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH
            !isNetworkOnline() && (pendingQueueCount + totalFailedQueueCount + conflictDataCount > 0) ->
                GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_NETWORK
            totalFailedQueueCount > 0 || conflictDataCount > 0 ->
                GoogleDriveMirrorSyncEngine.SYNC_STATUS_FAILED
            pendingQueueCount > 0 ->
                GoogleDriveMirrorSyncEngine.SYNC_STATUS_PARTIAL_SUCCESS
            else -> GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS
        }

        val lastSyncMs = generationManager.getLastSyncTimestampMillis()
        val serverRefMs = maxOf(driveService.lastServerTimeMillis, generationManager.getLastServerTimestampMillis())
        val quarantinedCount = dao.getActiveDriveCloudQuarantineOnce().size
        val manifest = buildCurrentManifestState()
        val resolvedErrorMsg = errorMessage
            ?: auth.authErrorReason.takeIf { needsReAuth && it.isNotBlank() }
            ?: queueItems.lastOrNull { it.lastError.isNotBlank() }?.lastError.orEmpty()
        val updated = DriveMirrorDashboardState(
            isDriveConnected = (auth.isConnected && !needsReAuth) || !driveService.accessTokenOverride.isNullOrBlank(),
            connectedEmail = auth.accountEmail,
            overallStatus = computedStatus,
            syncStatusCode = computedSyncStatusCode,
            totalLocalDataCount = totalLocalCount,
            totalCloudDataCount = totalCloudCount,
            totalSyncedDataCount = syncedDataCount,
            totalSyncedPhotosCount = totalSyncedPhotos,
            pendingSyncCount = pendingQueueCount,
            failedSyncCount = totalFailedQueueCount,
            failedAuthCount = failedAuthQueueCount,
            staleDataCount = staleDataCount,
            conflictDataCount = conflictDataCount,
            quarantinedFilesCount = quarantinedCount,
            requiresReAuth = needsReAuth,
            lastSyncTimeDisplay = formatDisplayTime(lastSyncMs),
            lastSyncTimestampMillis = lastSyncMs,
            lastServerTimeMillis = serverRefMs,
            serverTimeDisplay = if (serverRefMs > 0L) formatDisplayTime(serverRefMs) else "Belum ada referensi server",
            uploadProgressPercent = if (computedStatus == GoogleDriveMirrorSyncEngine.STATUS_UPLOADING) _dashboardState.value.uploadProgressPercent else if (computedSyncStatusCode == GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS) 100 else 0,
            downloadProgressPercent = _dashboardState.value.downloadProgressPercent,
            activeOperationLabel = if (computedStatus in listOf(
                    GoogleDriveMirrorSyncEngine.STATUS_UPLOADING,
                    GoogleDriveMirrorSyncEngine.STATUS_DOWNLOADING,
                    GoogleDriveMirrorSyncEngine.STATUS_RESETTING
                )
            ) {
                _dashboardState.value.activeOperationLabel
            } else {
                ""
            },
            lastErrorMessage = resolvedErrorMsg,
            datasetGeneration = generation,
            isResetLocked = _isResetLocked.value,
            manifestState = manifest
        )
        _dashboardState.value = updated
        updated
    }
}
