package com.example.drive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.DataConsistencyAuditReport
import com.example.data.local.DriveCloudManifestState
import com.example.data.local.DriveCloudQuarantineEntity
import com.example.data.local.DriveMirrorDashboardState
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.SyncTechnicalLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class MirrorSyncBatchResult(
    val processedCount: Int = 0,
    val syncedCount: Int = 0,
    val skippedIdempotentCount: Int = 0,
    val staleSkippedCount: Int = 0,
    val failedCount: Int = 0,
    val remainingPendingCount: Int = 0,
    val manifestUpdated: Boolean = false,
    val errorMessage: String = "",
    val overallSyncStatus: String = GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS,
    val totalPendingInDb: Int = 0,
    val totalFailedInDb: Int = 0,
    val totalFailedAuthInDb: Int = 0,
    val totalConflictInDb: Int = 0,
    val recoveredCount: Int = 0,
    val alreadyResolvedOnCloudCount: Int = 0,
    val requiresReAuth: Boolean = false
)

data class MirrorResetResult(
    val resetMode: String, // LOCAL_AND_CLOUD | LOCAL_ONLY
    val deletedLocalRecordsCount: Int,
    val deletedCloudFilesCount: Int,
    val newDatasetId: String,
    val resetTimestampMillis: Long,
    val freshManifestFileId: String = "",
    val preResetBackupCreated: Boolean = false,
    val datasetGeneration: String = "GENERATION_001"
)

/**
 * Facade & Compatibility Coordinator over the 7-class Synchronization Architecture:
 * 1. SyncWorker
 * 2. GoogleDriveService
 * 3. SyncRepository
 * 4. SyncQueueManager
 * 5. SyncConflictResolver
 * 6. SyncConsistencyChecker
 * 7. DatabaseGenerationManager
 */
class GoogleDriveMirrorSyncEngine(
    private val context: Context,
    private val dao: FreezerDao,
    driveClient: GoogleDriveRestClient = GoogleDriveRestClient(),
    accessTokenOverride: String? = null
) {
    companion object {
        const val PREFS_NAME = "sewain_drive_cloud_mirror_prefs"
        const val APP_PREFS_NAME = "saf_soc_prefs"
        const val APPLICATION_ID = "com.aistudio.sewainajafreezer.soc"
        const val CLOUD_MANIFEST_FILE_NAME = "manifest.json"
        const val CLOUD_MANIFEST_RECORD_KEY = "SYSTEM:CLOUD_MANIFEST"

        const val MIRROR_ROOT_SECTION = "SEWAIN_AJA_FREEZER_SOC"
        const val SUBFOLDER_ACTIVE_DATA = "ACTIVE_DATA"
        const val SUBFOLDER_DOCUMENTATION = "DOCUMENTATION"
        const val SUBFOLDER_HISTORICAL_BACKUP = "HISTORICAL_BACKUP"
        const val SUBFOLDER_SYNC_LOGS = "SYNC_LOGS"

        const val SUBFOLDER_CUSTOMERS = "CUSTOMERS"
        const val SUBFOLDER_UNITS = "UNITS"
        const val SUBFOLDER_TRANSACTIONS = "TRANSACTIONS"
        const val SUBFOLDER_PAYMENTS = "PAYMENTS"
        const val SUBFOLDER_SETTINGS = "SETTINGS"
        const val SUBFOLDER_AGREEMENTS = "AGREEMENTS"
        const val SUBFOLDER_TOMBSTONES = "TOMBSTONES"
        const val SUBFOLDER_PHOTOS = "PHOTOS"

        const val UNIQUE_MIRROR_SYNC_WORK = "saf_soc_drive_mirror_sync_work"

        // Record Categories
        const val CATEGORY_CUSTOMER = "CUSTOMER"
        const val CATEGORY_UNIT = "UNIT"
        const val CATEGORY_TRANSACTION = "TRANSACTION"
        const val CATEGORY_PAYMENT = "PAYMENT"
        const val CATEGORY_SETTINGS = "SETTINGS"
        const val CATEGORY_PHOTO_KTP = "PHOTO_KTP"
        const val CATEGORY_PHOTO_CUSTOMER = "PHOTO_CUSTOMER"
        const val CATEGORY_PHOTO_FREEZER = "PHOTO_FREEZER"
        const val CATEGORY_PHOTO_PAYMENT = "PHOTO_PAYMENT"
        const val CATEGORY_PHOTO_TRANSACTION = "PHOTO_TRANSACTION"
        const val CATEGORY_PHOTO_AGREEMENT = "PHOTO_AGREEMENT"
        const val CATEGORY_CLOUD_MANIFEST = "CLOUD_MANIFEST"
        const val CATEGORY_SNAPSHOT = "CATEGORY_SNAPSHOT"

        // Operation Types
        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
        const val OP_REPLACE_PHOTO = "REPLACE_PHOTO"
        const val OP_RESET_ALL = "RESET_ALL"

        // Sync Statuses
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PENDING_CREATE = MirrorOutboxCodec.STATUS_PENDING_CREATE
        const val STATUS_PENDING_UPDATE = MirrorOutboxCodec.STATUS_PENDING_UPDATE
        const val STATUS_PENDING_DELETE = MirrorOutboxCodec.STATUS_PENDING_DELETE
        const val STATUS_PENDING_SYNC = "PENDING_SYNC"
        const val STATUS_UPLOADING = "UPLOADING"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_FAILED_AUTH = "FAILED_AUTH"
        const val STATUS_DELETE_PENDING = "DELETE_PENDING"
        const val STATUS_RESETTING = "RESETTING"
        const val STATUS_STALE_OPERATION = "STALE_OPERATION"
        const val STATUS_STALE = "STALE"
        const val STATUS_CONFLICT = "CONFLICT"

        // Explicit High-Level Dashboard & Notification Sync Status Codes
        const val SYNC_STATUS_SUCCESS = "SUCCESS"
        const val SYNC_STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS"
        const val SYNC_STATUS_FAILED = "FAILED"
        const val SYNC_STATUS_RUNNING = "RUNNING"
        const val SYNC_STATUS_WAITING_AUTH = "WAITING_AUTH"
        const val SYNC_STATUS_WAITING_NETWORK = "WAITING_NETWORK"

        fun buildDeterministicPhotoSlotFileNameStatic(
            category: String,
            ownerId: Long
        ): String {
            val cleanCat = category.uppercase(Locale.US).replace(Regex("[^A-Z0-9]+"), "_")
            return "${cleanCat}_ID_${ownerId}.jpg"
        }
    }

    val generationManager = DatabaseGenerationManager(context)
    val driveService = GoogleDriveService(
        context = context,
        driveClient = driveClient,
        accessTokenOverride = accessTokenOverride
    )
    val queueManager = SyncQueueManager(dao, generationManager)
    val conflictResolver = SyncConflictResolver(dao, generationManager)
    val consistencyChecker = SyncConsistencyChecker(
        context = context,
        dao = dao,
        driveService = driveService,
        generationManager = generationManager,
        conflictResolver = conflictResolver
    )
    val syncRepository = SyncRepository(
        context = context,
        dao = dao,
        generationManager = generationManager,
        driveService = driveService,
        queueManager = queueManager,
        conflictResolver = conflictResolver,
        consistencyChecker = consistencyChecker
    )

    var driveClient: GoogleDriveRestClient
        get() = driveService.driveClient
        set(value) {
            driveService.driveClient = value
        }

    var accessTokenOverride: String?
        get() = driveService.accessTokenOverride
        set(value) {
            driveService.accessTokenOverride = value
        }

    var simulatedOfflineOverride: Boolean?
        get() = syncRepository.simulatedOfflineOverride
        set(value) {
            syncRepository.simulatedOfflineOverride = value
        }

    var autoTriggerSyncOnMutation: Boolean
        get() = syncRepository.autoTriggerSyncOnMutation
        set(value) {
            syncRepository.autoTriggerSyncOnMutation = value
        }

    val dashboardState: StateFlow<DriveMirrorDashboardState>
        get() = syncRepository.dashboardState

    fun observeTechnicalLogs(): Flow<List<SyncTechnicalLogEntity>> =
        syncRepository.observeTechnicalLogs()

    fun observeQuarantineItems(): Flow<List<DriveCloudQuarantineEntity>> =
        syncRepository.observeQuarantineItems()

    fun getDatasetGenerationNumber(): Int = generationManager.getGenerationSequenceNumber()

    fun getCurrentDatasetGenerationId(): String = generationManager.getCurrentGenerationId()

    fun getCurrentDatasetId(): String = generationManager.getCurrentDatasetId()

    fun getLastResetTimestampMillis(): Long = generationManager.getLastResetTimestampMillis()

    fun setSimulatedOfflineMode(isOffline: Boolean?) =
        syncRepository.setSimulatedOfflineMode(isOffline)

    fun isNetworkOnline(): Boolean = syncRepository.isNetworkOnline()

    fun startConnectivityMonitor() = syncRepository.startConnectivityMonitor()

    fun stopConnectivityMonitor() = syncRepository.stopConnectivityMonitor()

    fun enqueueWorkManagerMirrorSync() = syncRepository.enqueueWorkManagerMirrorSync()

    fun computeSha256(text: String): String = MirrorOutboxCodec.computeSha256(text)

    fun computeFileSha256(file: File): String = MirrorOutboxCodec.computeFileSha256(file)

    suspend fun onOutboxCommitted() = syncRepository.onOutboxCommitted()

    suspend fun onCustomerOutboxCommitted(
        customer: CustomerEntity,
        isNew: Boolean,
        previousKtpUri: String = "",
        previousCustomerPhotoUri: String = ""
    ) = syncRepository.onCustomerOutboxCommitted(customer, isNew, previousKtpUri, previousCustomerPhotoUri)

    suspend fun onCustomerDeletedOutboxCommitted(
        customer: CustomerEntity,
        relatedPhotoSlotKeys: List<String> = emptyList(),
        relatedDriveFileIds: List<String> = emptyList()
    ) = syncRepository.onCustomerDeletedOutboxCommitted(customer, relatedPhotoSlotKeys, relatedDriveFileIds)

    suspend fun onUnitOutboxCommitted(
        unit: FreezerUnitEntity,
        isNew: Boolean,
        previousPhotoUri: String = ""
    ) = syncRepository.onUnitOutboxCommitted(unit, isNew, previousPhotoUri)

    suspend fun onUnitDeletedOutboxCommitted(
        unit: FreezerUnitEntity,
        relatedDriveFileIds: List<String> = emptyList()
    ) = syncRepository.onUnitDeletedOutboxCommitted(unit, relatedDriveFileIds)

    suspend fun onTransactionOutboxCommitted(
        tx: RentalTransactionEntity,
        isNew: Boolean
    ) = syncRepository.onTransactionOutboxCommitted(tx, isNew)

    suspend fun onTransactionDeletedOutboxCommitted(
        tx: RentalTransactionEntity,
        relatedDriveFileIds: List<String> = emptyList()
    ) = syncRepository.onTransactionDeletedOutboxCommitted(tx, relatedDriveFileIds)

    suspend fun onPaymentOutboxCommitted(
        proof: PaymentProofEntity,
        isNew: Boolean,
        previousPhotoUri: String = ""
    ) = syncRepository.onPaymentOutboxCommitted(proof, isNew, previousPhotoUri)

    suspend fun enqueueCustomerChange(
        customer: CustomerEntity,
        isNew: Boolean,
        previousKtpUri: String = "",
        previousCustomerPhotoUri: String = ""
    ) = syncRepository.enqueueCustomerChange(customer, isNew, previousKtpUri, previousCustomerPhotoUri)

    suspend fun enqueueCustomerDeletion(
        customer: CustomerEntity,
        relatedPhotoSlotKeys: List<String> = emptyList(),
        relatedDriveFileIds: List<String> = emptyList()
    ) = syncRepository.enqueueCustomerDeletion(customer, relatedPhotoSlotKeys, relatedDriveFileIds)

    suspend fun enqueueUnitChange(
        unit: FreezerUnitEntity,
        isNew: Boolean,
        previousPhotoUri: String = ""
    ) = syncRepository.enqueueUnitChange(unit, isNew, previousPhotoUri)

    suspend fun enqueueUnitDeletion(
        unit: FreezerUnitEntity,
        relatedDriveFileIds: List<String> = emptyList()
    ) = syncRepository.enqueueUnitDeletion(unit, relatedDriveFileIds)

    suspend fun enqueueTransactionChange(
        tx: RentalTransactionEntity,
        isNew: Boolean
    ) = syncRepository.enqueueTransactionChange(tx, isNew)

    suspend fun enqueueTransactionDeletion(
        tx: RentalTransactionEntity,
        relatedDriveFileIds: List<String> = emptyList()
    ) = syncRepository.enqueueTransactionDeletion(tx, relatedDriveFileIds)

    suspend fun enqueuePaymentChange(
        proof: PaymentProofEntity,
        isNew: Boolean,
        previousPhotoUri: String = ""
    ) = syncRepository.enqueuePaymentChange(proof, isNew, previousPhotoUri)

    suspend fun enqueuePaymentDeletion(proof: PaymentProofEntity) =
        syncRepository.enqueuePaymentDeletion(proof)

    suspend fun enqueueAppSettingsChange(settingsJson: JSONObject) =
        syncRepository.enqueueAppSettingsChange(settingsJson)

    suspend fun enqueuePhotoSlotSync(
        slotKey: String,
        photoCategory: String,
        ownerId: Long,
        photoPath: String,
        targetFileName: String = "",
        isReplace: Boolean = false,
        previousLocalPhotoPath: String = ""
    ) = syncRepository.enqueuePhotoSlotSync(
        slotKey, photoCategory, ownerId, photoPath, targetFileName, isReplace, previousLocalPhotoPath
    )

    suspend fun enqueuePhotoDeletionBySlotKey(
        slotKey: String,
        photoCategory: String,
        ownerId: Long
    ) = syncRepository.enqueuePhotoDeletionBySlotKey(slotKey, photoCategory, ownerId)

    suspend fun processSyncQueue(
        forceRetryFailed: Boolean = false,
        ignoreBackoffTimers: Boolean = false
    ): Result<MirrorSyncBatchResult> =
        syncRepository.processSyncQueue(forceRetryFailed, ignoreBackoffTimers)

    suspend fun recoverFailedSyncOperations(): Result<MirrorSyncBatchResult> =
        syncRepository.recoverFailedSyncOperations()

    suspend fun onAuthRecoveredAndResumeQueue(): Result<MirrorSyncBatchResult> =
        syncRepository.onAuthRecoveredAndResumeQueue()

    suspend fun resolveConflict(
        localRecordKey: String,
        strategy: ConflictResolutionStrategy = ConflictResolutionStrategy.PREFER_LOCAL_FORCE_PUSH,
        syncNow: Boolean = true
    ): Result<Boolean> =
        syncRepository.resolveConflict(localRecordKey, strategy, syncNow)

    suspend fun executeResetLocalAndCloud(
        preResetBackupCallback: (suspend () -> Boolean)? = null
    ): Result<MirrorResetResult> =
        syncRepository.executeResetLocalAndCloud(preResetBackupCallback)

    suspend fun executeResetLocalOnly(): Result<MirrorResetResult> =
        syncRepository.executeResetLocalOnly()

    suspend fun canApplyRemoteRecordWithoutResurrection(
        localRecordKey: String,
        remoteDatasetId: String,
        remoteTimestampMillis: Long,
        remoteVersion: Long,
        recordUuid: String = "",
        remoteGenerationId: String = ""
    ): Boolean = syncRepository.canApplyRemoteRecordWithoutResurrection(
        localRecordKey = localRecordKey,
        remoteDatasetId = remoteDatasetId,
        remoteTimestampMillis = remoteTimestampMillis,
        remoteVersion = remoteVersion,
        recordUuid = recordUuid,
        remoteGenerationId = remoteGenerationId
    )

    suspend fun verifyConsistencyOnAppStartup(): Result<DriveMirrorDashboardState> =
        syncRepository.verifyConsistencyOnAppStartup()

    suspend fun runDataConsistencyAudit(): Result<DataConsistencyAuditReport> =
        syncRepository.runDataConsistencyAudit()

    suspend fun scanAndQuarantineStaleCloudFiles(): Result<List<DriveCloudQuarantineEntity>> =
        syncRepository.scanAndQuarantineStaleCloudFiles()

    suspend fun purgeVerifiedQuarantinedFiles(): Result<Int> =
        syncRepository.purgeVerifiedQuarantinedFiles()

    suspend fun cleanOrphanCloudAndResync(): Result<OrphanCleanupAndResyncReport> =
        syncRepository.cleanOrphanCloudAndResync()

    suspend fun buildCurrentManifestState(
        datasetStatusOverride: String? = null
    ): DriveCloudManifestState =
        syncRepository.buildCurrentManifestState(datasetStatusOverride)

    suspend fun updateCloudManifestOnDrive(
        accessToken: String,
        datasetStatusOverride: String? = null
    ): Result<String> =
        syncRepository.updateCloudManifestOnDrive(accessToken, datasetStatusOverride)

    suspend fun refreshDashboardStateSnapshot(
        statusOverride: String? = null,
        errorMessage: String? = null
    ): DriveMirrorDashboardState =
        syncRepository.refreshDashboardStateSnapshot(statusOverride, errorMessage)

    fun isPhotoCategory(category: String): Boolean = driveService.isPhotoCategory(category)

    fun buildDeterministicRecordFileName(
        recordCategory: String,
        recordId: Long,
        recordUuid: String = ""
    ): String = MirrorOutboxCodec.buildDeterministicRecordFileName(recordCategory, recordId, recordUuid)

    fun buildDeterministicRecordFileName(recordCategory: String, recordId: Long): String =
        buildDeterministicRecordFileName(recordCategory, recordId, "")

    fun buildDeterministicPhotoSlotFileName(
        category: String,
        ownerId: Long,
        label: String
    ): String = buildDeterministicPhotoSlotFileNameStatic(category, ownerId)
}

class DriveMirrorSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        @Volatile
        var testEngineFactory: ((Context) -> GoogleDriveMirrorSyncEngine)? = null
    }

    override suspend fun doWork(): Result {
        val repository = SyncWorker.testRepositoryFactory?.invoke(applicationContext)
            ?: testEngineFactory?.invoke(applicationContext)?.syncRepository
            ?: SyncRepository(
                context = applicationContext,
                dao = AppDatabase.getInstance(applicationContext).freezerDao()
            )
        val outcome = repository.processSyncQueue(forceRetryFailed = false, ignoreBackoffTimers = true)
        return outcome.fold(
            onSuccess = { batch ->
                when {
                    batch.requiresReAuth ||
                        batch.overallSyncStatus == GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH -> {
                        Result.failure()
                    }
                    batch.overallSyncStatus == GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_NETWORK ||
                        batch.failedCount > 0 ||
                        batch.totalFailedInDb > 0 ||
                        batch.remainingPendingCount > 0 ||
                        batch.totalPendingInDb > 0 -> {
                        if (runAttemptCount < 5) Result.retry() else Result.failure()
                    }
                    else -> Result.success()
                }
            },
            onFailure = { err ->
                if (DriveApiException.isAuthFailure(err)) {
                    Result.failure()
                } else if (runAttemptCount < 5) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        )
    }
}
