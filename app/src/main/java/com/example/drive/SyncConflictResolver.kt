package com.example.drive

import com.example.data.local.DriveCloudFileMapEntity
import com.example.data.local.DriveLocalSyncQueueEntity
import com.example.data.local.FreezerDao
import com.example.data.local.SyncTechnicalLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConflictResolutionStrategy {
    KEEP_HIGHER_REVISION,
    PREFER_LOCAL_FORCE_PUSH,
    PREFER_CLOUD_IF_NEWER
}

data class SyncConflictRecord(
    val localRecordKey: String,
    val recordCategory: String,
    val recordId: Long,
    val recordUuid: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val localHash: String,
    val remoteHash: String,
    val driveFileId: String,
    val detectedAtMillis: Long,
    val reason: String
)

/**
 * 5. SyncConflictResolver
 * Revision-based conflict detector and deterministic resolver.
 * Never blindly overwrites newer cloud or local records; maintains persistent conflict logs.
 */
class SyncConflictResolver(
    private val dao: FreezerDao,
    private val generationManager: DatabaseGenerationManager
) {
    companion object {
        const val CONFLICT_ERROR_PREFIX = "VERSION_CONFLICT:"
    }

    fun detectConflict(
        queueItem: DriveLocalSyncQueueEntity,
        resolvedUuid: String,
        remoteCandidate: DriveBackupFileItem?
    ): SyncConflictRecord? {
        if (remoteCandidate == null) return null
        val sameUuidOrBlankRemote = remoteCandidate.recordUuid.isBlank() ||
            resolvedUuid.isBlank() ||
            remoteCandidate.recordUuid == resolvedUuid
        if (!sameUuidOrBlankRemote) return null

        val remoteVer = remoteCandidate.remoteVersion
        if (remoteVer > queueItem.version) {
            val reason = "$CONFLICT_ERROR_PREFIX Versi Cloud (v$remoteVer) lebih baru daripada lokal (v${queueItem.version}) untuk ${queueItem.localRecordKey}."
            return SyncConflictRecord(
                localRecordKey = queueItem.localRecordKey,
                recordCategory = queueItem.recordCategory,
                recordId = queueItem.recordId,
                recordUuid = resolvedUuid,
                localRevision = queueItem.version,
                remoteRevision = remoteVer,
                localHash = queueItem.contentHash,
                remoteHash = remoteCandidate.sha256Checksum,
                driveFileId = remoteCandidate.id,
                detectedAtMillis = System.currentTimeMillis(),
                reason = reason
            )
        }
        return null
    }

    suspend fun getPersistentConflictLogs(): List<SyncTechnicalLogEntity> {
        return dao.getAllSyncTechnicalLogsOnce().filter {
            it.errorCode == "VERSION_CONFLICT" || it.localStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT
        }
    }

    private fun parseRemoteRevisionFromError(errorText: String): Long {
        val m = Regex("""Versi Cloud \(v(\d+)\)""").find(errorText)
        return m?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    }

    suspend fun getActiveConflictEntries(): List<SyncConflictRecord> {
        val queueConflicts = dao.getAllDriveLocalSyncQueueOnce()
            .filter { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT }
        val mapByKey = dao.getAllDriveCloudFileMappingsOnce().associateBy { it.localRecordKey }
        val result = mutableListOf<SyncConflictRecord>()
        for (q in queueConflicts) {
            val map = mapByKey[q.localRecordKey]
            val parsedRemote = parseRemoteRevisionFromError(q.lastError)
            result.add(
                SyncConflictRecord(
                    localRecordKey = q.localRecordKey,
                    recordCategory = q.recordCategory,
                    recordId = q.recordId,
                    recordUuid = q.recordUuid.ifBlank { map?.recordUuid.orEmpty() },
                    localRevision = q.version,
                    remoteRevision = maxOf(parsedRemote, q.version + 1L, map?.version ?: (q.version + 1L)),
                    localHash = q.contentHash,
                    remoteHash = map?.contentHash.orEmpty(),
                    driveFileId = map?.driveFileId ?: q.previousDriveFileId,
                    detectedAtMillis = q.timestampMillis,
                    reason = q.lastError
                )
            )
        }
        return result
    }

    suspend fun resolveConflictForceLocalPush(
        localRecordKey: String,
        targetRevision: Long,
        queueManager: SyncQueueManager
    ): Boolean {
        val q = dao.getLatestDriveSyncQueueForKey(localRecordKey) ?: return false
        val bumpedVersion = maxOf(targetRevision, q.version + 1L)
        val now = System.currentTimeMillis()
        when (q.recordCategory) {
            GoogleDriveMirrorSyncEngine.CATEGORY_UNIT -> {
                val u = dao.getUnitByIdIncludingDeleted(q.recordId) ?: return false
                val updated = u.copy(version = bumpedVersion, updatedAtMillis = now, syncStatus = MirrorOutboxCodec.STATUS_PENDING_UPDATE)
                dao.updateUnit(updated)
                val payload = MirrorOutboxCodec.serializeUnitJson(updated, generationManager.getCurrentDatasetId(), generationManager.getCurrentGenerationId(), now)
                dao.deleteDriveSyncQueueById(q.id)
                queueManager.enqueueRecordOperation(
                    localRecordKey = localRecordKey,
                    recordCategory = q.recordCategory,
                    recordId = q.recordId,
                    operationType = GoogleDriveMirrorSyncEngine.OP_UPDATE,
                    payloadJson = payload,
                    contentHash = MirrorOutboxCodec.computeSha256(payload),
                    recordUuid = updated.uuid,
                    entityVersion = bumpedVersion,
                    updatedAtMillis = now
                )
            }
            GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER -> {
                val c = dao.getCustomerByIdIncludingDeleted(q.recordId) ?: return false
                val updated = c.copy(version = bumpedVersion, updatedAtMillis = now, syncStatus = MirrorOutboxCodec.STATUS_PENDING_UPDATE)
                dao.updateCustomer(updated)
                val payload = MirrorOutboxCodec.serializeCustomerJson(updated, generationManager.getCurrentDatasetId(), generationManager.getCurrentGenerationId(), now)
                dao.deleteDriveSyncQueueById(q.id)
                queueManager.enqueueRecordOperation(
                    localRecordKey = localRecordKey,
                    recordCategory = q.recordCategory,
                    recordId = q.recordId,
                    operationType = GoogleDriveMirrorSyncEngine.OP_UPDATE,
                    payloadJson = payload,
                    contentHash = MirrorOutboxCodec.computeSha256(payload),
                    recordUuid = updated.uuid,
                    entityVersion = bumpedVersion,
                    updatedAtMillis = now
                )
            }
            GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION -> {
                val tx = dao.getTransactionByIdIncludingDeleted(q.recordId) ?: return false
                val updated = tx.copy(version = bumpedVersion, updatedAtMillis = now, syncStatus = MirrorOutboxCodec.STATUS_PENDING_UPDATE)
                dao.updateTransaction(updated)
                val payload = MirrorOutboxCodec.serializeTransactionJson(updated, generationManager.getCurrentDatasetId(), generationManager.getCurrentGenerationId(), now)
                dao.deleteDriveSyncQueueById(q.id)
                queueManager.enqueueRecordOperation(
                    localRecordKey = localRecordKey,
                    recordCategory = q.recordCategory,
                    recordId = q.recordId,
                    operationType = GoogleDriveMirrorSyncEngine.OP_UPDATE,
                    payloadJson = payload,
                    contentHash = MirrorOutboxCodec.computeSha256(payload),
                    recordUuid = updated.uuid,
                    entityVersion = bumpedVersion,
                    updatedAtMillis = now
                )
            }
            GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT -> {
                val p = dao.getPaymentProofByIdIncludingDeleted(q.recordId) ?: return false
                val updated = p.copy(version = bumpedVersion, updatedAtMillis = now, syncStatus = MirrorOutboxCodec.STATUS_PENDING_UPDATE)
                dao.updatePaymentProof(updated)
                val payload = MirrorOutboxCodec.serializePaymentJson(updated, generationManager.getCurrentDatasetId(), generationManager.getCurrentGenerationId(), now)
                dao.deleteDriveSyncQueueById(q.id)
                queueManager.enqueueRecordOperation(
                    localRecordKey = localRecordKey,
                    recordCategory = q.recordCategory,
                    recordId = q.recordId,
                    operationType = GoogleDriveMirrorSyncEngine.OP_UPDATE,
                    payloadJson = payload,
                    contentHash = MirrorOutboxCodec.computeSha256(payload),
                    recordUuid = updated.uuid,
                    entityVersion = bumpedVersion,
                    updatedAtMillis = now
                )
            }
            else -> return false
        }
        dao.insertSyncTechnicalLog(
            SyncTechnicalLogEntity(
                operationTimeMillis = now,
                operationTimeDisplay = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID")).format(Date(now)),
                operationType = "CONFLICT_RESOLVED",
                recordId = localRecordKey,
                datasetGenerationId = generationManager.getCurrentGenerationId(),
                localStatus = "BUMPED_TO_v$bumpedVersion",
                cloudStatus = "PENDING_OVERWRITE",
                errorCode = "",
                errorMessage = "Konflik diselesaikan dengan PREFER_LOCAL_FORCE_PUSH -> v$bumpedVersion"
            )
        )
        return true
    }
}
