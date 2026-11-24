package com.example.drive

import com.example.data.local.DriveCloudFileMapEntity
import com.example.data.local.DriveDeletionTombstoneEntity
import com.example.data.local.DriveLocalSyncQueueEntity
import com.example.data.local.FreezerDao
import org.json.JSONObject
import java.io.File

/**
 * 4. SyncQueueManager
 * Manages the persistent SQLite Transactional Outbox (`drive_local_sync_queue`),
 * file mappings (`drive_cloud_file_map`), and tombstones (`drive_deletion_tombstones`).
 * Enforces FIFO execution ordering, idempotent coalescing, stale-generation rejection,
 * and exponential backoff retry scheduling.
 */
class SyncQueueManager(
    private val dao: FreezerDao,
    private val generationManager: DatabaseGenerationManager
) {
    suspend fun getRunnableQueueItems(ignoreBackoffTimers: Boolean): List<DriveLocalSyncQueueEntity> {
        val now = System.currentTimeMillis()
        val items = dao.getRunnableDriveSyncQueueItemsOnce().filter { item ->
            ignoreBackoffTimers || item.nextRetryAtMillis <= now
        }
        return orderItemsRespectingDependencies(items)
    }

    suspend fun resetStuckUploadingItemsToPending() {
        dao.resetStuckUploadingDriveSyncQueueToPending()
    }

    suspend fun resetFailedItemsToPending() {
        dao.resetFailedDriveSyncQueueToPending()
    }

    suspend fun resetFailedAuthItemsToPending() {
        dao.resetFailedAuthDriveSyncQueueToPending()
    }

    suspend fun getFailedItemsForRecovery(): List<DriveLocalSyncQueueEntity> {
        return orderItemsRespectingDependencies(dao.getFailedAndAuthFailedDriveSyncQueueItemsOnce())
    }

    /**
     * Orders queued operations so:
     * 1. Creates/updates execute parent-before-child (SETTINGS/CUSTOMER/UNIT -> TRANSACTION -> PAYMENT -> PHOTO_*).
     * 2. Deletes execute child-before-parent (PHOTO_* -> PAYMENT -> TRANSACTION -> CUSTOMER/UNIT).
     * 3. Same-record creates/updates always precede deletes; ties preserve original FIFO timestamp & id.
     */
    fun orderItemsRespectingDependencies(
        items: List<DriveLocalSyncQueueEntity>
    ): List<DriveLocalSyncQueueEntity> {
        if (items.size <= 1) return items
        return items.sortedWith(
            compareBy<DriveLocalSyncQueueEntity>(
                { dependencyPriorityBucket(it) },
                { it.timestampMillis },
                { it.version },
                { it.id }
            )
        )
    }

    private fun dependencyPriorityBucket(item: DriveLocalSyncQueueEntity): Int {
        val isDelete = item.operationType == GoogleDriveMirrorSyncEngine.OP_DELETE
        val cat = item.recordCategory
        return if (!isDelete) {
            when (cat) {
                GoogleDriveMirrorSyncEngine.CATEGORY_SETTINGS -> 0
                GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
                GoogleDriveMirrorSyncEngine.CATEGORY_UNIT -> 1
                GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION -> 2
                GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT -> 3
                else -> 4 // PHOTO_*
            }
        } else {
            when (cat) {
                GoogleDriveMirrorSyncEngine.CATEGORY_SETTINGS -> 9
                GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
                GoogleDriveMirrorSyncEngine.CATEGORY_UNIT -> 8
                GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION -> 7
                GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT -> 6
                else -> 5 // PHOTO_*
            }
        }
    }

    suspend fun purgeCompletedSyncedItems() {
        dao.deleteSyncedDriveSyncQueueItems()
    }

    fun isStaleGenerationOperation(item: DriveLocalSyncQueueEntity, currentGenerationId: String): Boolean {
        if (item.operationType == GoogleDriveMirrorSyncEngine.OP_RESET_ALL) return false
        if (item.datasetGenerationId.isBlank()) return false
        return item.datasetGenerationId != currentGenerationId ||
            generationManager.isGenerationInvalidated(item.datasetGenerationId)
    }

    suspend fun markOperationStale(item: DriveLocalSyncQueueEntity, reason: String) {
        dao.updateDriveSyncQueueStatus(
            id = item.id,
            syncStatus = GoogleDriveMirrorSyncEngine.STATUS_STALE_OPERATION,
            retryCount = item.retryCount,
            nextRetryAtMillis = 0L,
            lastError = reason
        )
    }

    suspend fun markOperationUploading(item: DriveLocalSyncQueueEntity) {
        dao.updateDriveSyncQueueStatus(
            id = item.id,
            syncStatus = GoogleDriveMirrorSyncEngine.STATUS_UPLOADING,
            retryCount = item.retryCount,
            nextRetryAtMillis = item.nextRetryAtMillis,
            lastError = ""
        )
        if (item.operationType != GoogleDriveMirrorSyncEngine.OP_DELETE) {
            updateCoreEntitySyncStatus(item.recordCategory, item.recordId, GoogleDriveMirrorSyncEngine.STATUS_UPLOADING)
        }
    }

    suspend fun markOperationSynced(
        item: DriveLocalSyncQueueEntity,
        driveFileId: String
    ) {
        dao.updateDriveSyncQueueStatus(
            id = item.id,
            syncStatus = GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
            retryCount = item.retryCount,
            nextRetryAtMillis = 0L,
            lastError = ""
        )
        if (item.operationType == GoogleDriveMirrorSyncEngine.OP_DELETE) {
            hardDeleteCoreEntityById(item.recordCategory, item.recordId)
        } else {
            updateCoreEntitySyncStatusAndDriveId(
                recordCategory = item.recordCategory,
                recordId = item.recordId,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
                driveId = driveFileId
            )
        }
    }

    suspend fun markOperationFailedAuth(
        item: DriveLocalSyncQueueEntity,
        existingMap: DriveCloudFileMapEntity?,
        errorMessage: String
    ) {
        dao.updateDriveSyncQueueStatus(
            id = item.id,
            syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH,
            retryCount = item.retryCount,
            nextRetryAtMillis = 0L,
            lastError = errorMessage
        )
        if (item.operationType == GoogleDriveMirrorSyncEngine.OP_DELETE) {
            updateCoreEntitySyncStatus(item.recordCategory, item.recordId, GoogleDriveMirrorSyncEngine.STATUS_PENDING_DELETE)
        } else {
            updateCoreEntitySyncStatus(item.recordCategory, item.recordId, GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH)
        }
        existingMap?.let {
            dao.updateDriveCloudFileMapStatus(
                localRecordKey = it.localRecordKey,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH,
                lastModifiedMillis = System.currentTimeMillis()
            )
        }
    }

    suspend fun markOperationFailedWithBackoff(
        item: DriveLocalSyncQueueEntity,
        existingMap: DriveCloudFileMapEntity?,
        errorMessage: String
    ): Int {
        val nextRetryCount = item.retryCount + 1
        val backoffMs = minOf(300_000L, 2_000L * (1L shl nextRetryCount.coerceAtMost(7)))
        dao.updateDriveSyncQueueStatus(
            id = item.id,
            syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
            retryCount = nextRetryCount,
            nextRetryAtMillis = System.currentTimeMillis() + backoffMs,
            lastError = errorMessage
        )
        if (item.operationType == GoogleDriveMirrorSyncEngine.OP_DELETE) {
            updateCoreEntitySyncStatus(item.recordCategory, item.recordId, GoogleDriveMirrorSyncEngine.STATUS_PENDING_DELETE)
        } else {
            updateCoreEntitySyncStatus(item.recordCategory, item.recordId, GoogleDriveMirrorSyncEngine.STATUS_FAILED)
        }
        existingMap?.let {
            dao.updateDriveCloudFileMapStatus(
                localRecordKey = it.localRecordKey,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                lastModifiedMillis = System.currentTimeMillis()
            )
        }
        return nextRetryCount
    }

    suspend fun markOperationConflict(
        item: DriveLocalSyncQueueEntity,
        existingMap: DriveCloudFileMapEntity?,
        conflictReason: String
    ) {
        dao.updateDriveSyncQueueStatus(
            id = item.id,
            syncStatus = GoogleDriveMirrorSyncEngine.STATUS_CONFLICT,
            retryCount = item.retryCount,
            nextRetryAtMillis = 0L,
            lastError = conflictReason
        )
        if (item.operationType != GoogleDriveMirrorSyncEngine.OP_DELETE) {
            updateCoreEntitySyncStatus(item.recordCategory, item.recordId, GoogleDriveMirrorSyncEngine.STATUS_CONFLICT)
        }
        existingMap?.let {
            dao.updateDriveCloudFileMapStatusAndReason(
                localRecordKey = it.localRecordKey,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_CONFLICT,
                lastModifiedMillis = System.currentTimeMillis(),
                conflictReason = conflictReason
            )
        }
    }

    suspend fun enqueueRecordOperation(
        localRecordKey: String,
        recordCategory: String,
        recordId: Long,
        operationType: String,
        payloadJson: String,
        contentHash: String,
        recordUuid: String = "",
        entityVersion: Long = 0L,
        updatedAtMillis: Long = 0L,
        serverTimestampMillis: Long = 0L
    ) {
        val now = updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val generationId = generationManager.getCurrentGenerationId()
        val existingMap = dao.getDriveCloudFileMapByKey(localRecordKey)
        val latestQueue = dao.getLatestDriveSyncQueueForKey(localRecordKey)
        val isUuidCategory = recordCategory != GoogleDriveMirrorSyncEngine.CATEGORY_SETTINGS &&
            recordCategory != GoogleDriveMirrorSyncEngine.CATEGORY_CLOUD_MANIFEST
        val resolvedUuid = if (isUuidCategory) {
            MirrorOutboxCodec.ensureValidUuidV4(recordUuid.ifBlank { existingMap?.recordUuid.orEmpty() })
        } else {
            recordUuid.ifBlank { existingMap?.recordUuid.orEmpty() }
        }
        val preservedDriveFileId = existingMap?.driveFileId.orEmpty().takeIf {
            it.isNotBlank() && (
                existingMap?.datasetGenerationId == generationId ||
                    (resolvedUuid.isNotBlank() && existingMap?.recordUuid == resolvedUuid)
                )
        }.orEmpty()
        val preservedDriveFolderId = existingMap?.driveFolderId.orEmpty().takeIf {
            preservedDriveFileId.isNotBlank()
        }.orEmpty()

        if (existingMap != null &&
            existingMap.contentHash == contentHash &&
            existingMap.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED &&
            preservedDriveFileId.isNotBlank() &&
            latestQueue?.syncStatus != GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC
        ) {
            return
        }
        if (latestQueue != null &&
            latestQueue.datasetGenerationId == generationId &&
            latestQueue.contentHash == contentHash &&
            latestQueue.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC &&
            latestQueue.operationType != GoogleDriveMirrorSyncEngine.OP_DELETE
        ) {
            return
        }

        val currentKnownMaxVersion = maxOf(existingMap?.version ?: 0L, latestQueue?.version ?: 0L)
        val baseVersion = existingMap?.version ?: 0L
        val nextVersion = if (entityVersion > currentKnownMaxVersion) {
            entityVersion
        } else {
            currentKnownMaxVersion + 1L
        }
        val effectiveOp = if (
            preservedDriveFileId.isNotBlank() &&
            operationType == GoogleDriveMirrorSyncEngine.OP_CREATE
        ) {
            GoogleDriveMirrorSyncEngine.OP_UPDATE
        } else {
            operationType
        }

        dao.deleteDriveDeletionTombstoneByKey(localRecordKey)
        if (resolvedUuid.isNotBlank()) {
            dao.deleteDriveDeletionTombstoneByUuid(resolvedUuid)
        }

        val effectivePayloadJson = runCatching {
            val obj = JSONObject(payloadJson)
            if (obj.has("version") && obj.optLong("version", -1L) != nextVersion) {
                obj.put("version", nextVersion)
                obj.toString()
            } else {
                payloadJson
            }
        }.getOrDefault(payloadJson)
        val effectiveHash = if (effectivePayloadJson == payloadJson) {
            contentHash
        } else {
            MirrorOutboxCodec.computeSha256(effectivePayloadJson)
        }

        val fileName = MirrorOutboxCodec.buildDeterministicRecordFileName(recordCategory, recordId, resolvedUuid)
        dao.upsertDriveCloudFileMap(
            DriveCloudFileMapEntity(
                localRecordKey = localRecordKey,
                recordCategory = recordCategory,
                localRecordId = recordId,
                driveFileId = preservedDriveFileId,
                driveFolderId = preservedDriveFolderId,
                fileName = fileName,
                contentHash = effectiveHash,
                version = nextVersion,
                lastModifiedMillis = now,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_PENDING,
                datasetGenerationId = generationId,
                recordUuid = resolvedUuid,
                serverTimestampMillis = existingMap?.serverTimestampMillis ?: 0L,
                conflictReason = ""
            )
        )

        val opId = "${generationId}_${localRecordKey}_${effectiveOp}_v${nextVersion}_${effectiveHash.take(12)}"
        dao.upsertDriveLocalSyncQueueItem(
            DriveLocalSyncQueueEntity(
                operationId = opId,
                recordCategory = recordCategory,
                recordId = recordId,
                localRecordKey = localRecordKey,
                operationType = effectiveOp,
                version = nextVersion,
                timestampMillis = now,
                payloadJson = effectivePayloadJson,
                contentHash = effectiveHash,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC,
                datasetGenerationId = generationId,
                recordUuid = resolvedUuid,
                baseVersion = baseVersion,
                serverTimestampMillis = serverTimestampMillis
            )
        )
    }

    suspend fun enqueuePhotoSlotSync(
        slotKey: String,
        photoCategory: String,
        ownerId: Long,
        localFile: File,
        targetFileName: String,
        isReplace: Boolean,
        previousLocalPhotoPath: String = ""
    ) {
        if (!localFile.exists() || localFile.length() <= 0L) return
        val now = System.currentTimeMillis()
        val generationId = generationManager.getCurrentGenerationId()
        val fileHash = MirrorOutboxCodec.computeFileSha256(localFile)
        val existingMap = dao.getDriveCloudFileMapByKey(slotKey)
        val latestQueue = dao.getLatestDriveSyncQueueForKey(slotKey)

        if (existingMap != null &&
            existingMap.datasetGenerationId == generationId &&
            existingMap.contentHash == fileHash &&
            existingMap.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED &&
            existingMap.driveFileId.isNotBlank() &&
            latestQueue?.syncStatus != GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC
        ) {
            return
        }

        val nextVersion = maxOf(existingMap?.version ?: 0L, latestQueue?.version ?: 0L) + 1L
        val effectiveOp = if (
            isReplace ||
            (existingMap != null && existingMap.datasetGenerationId == generationId && existingMap.driveFileId.isNotBlank())
        ) {
            GoogleDriveMirrorSyncEngine.OP_REPLACE_PHOTO
        } else {
            GoogleDriveMirrorSyncEngine.OP_CREATE
        }

        dao.deleteDriveDeletionTombstoneByKey(slotKey)

        val payloadJson = JSONObject().apply {
            put("slotKey", slotKey)
            put("photoCategory", photoCategory)
            put("ownerId", ownerId)
            put("photoPath", localFile.absolutePath)
            put("targetFileName", targetFileName)
            put("previousLocalPhotoPath", previousLocalPhotoPath)
            put("contentHash", fileHash)
            put("version", nextVersion)
            put("datasetGeneration", generationId)
        }.toString()

        val keptDriveFileId = if (existingMap?.datasetGenerationId == generationId) existingMap.driveFileId else ""
        val keptFolderId = if (existingMap?.datasetGenerationId == generationId) existingMap.driveFolderId else ""

        dao.upsertDriveCloudFileMap(
            DriveCloudFileMapEntity(
                localRecordKey = slotKey,
                recordCategory = photoCategory,
                localRecordId = ownerId,
                driveFileId = keptDriveFileId,
                driveFolderId = keptFolderId,
                fileName = targetFileName,
                contentHash = fileHash,
                version = nextVersion,
                lastModifiedMillis = now,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_PENDING,
                datasetGenerationId = generationId
            )
        )

        val opId = "${generationId}_${slotKey}_${effectiveOp}_v${nextVersion}_${fileHash.take(12)}"
        dao.upsertDriveLocalSyncQueueItem(
            DriveLocalSyncQueueEntity(
                operationId = opId,
                recordCategory = photoCategory,
                recordId = ownerId,
                localRecordKey = slotKey,
                operationType = effectiveOp,
                version = nextVersion,
                timestampMillis = now,
                payloadJson = payloadJson,
                photoCategory = photoCategory,
                newPhotoPath = localFile.absolutePath,
                previousDriveFileId = keptDriveFileId,
                contentHash = fileHash,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC,
                datasetGenerationId = generationId
            )
        )
    }

    suspend fun enqueueDeleteOperation(
        localRecordKey: String,
        recordCategory: String,
        recordId: Long,
        explicitDriveFileId: String = "",
        recordUuid: String = "",
        serverTimestampMillis: Long = 0L
    ) {
        val now = System.currentTimeMillis()
        val generationId = generationManager.getCurrentGenerationId()
        val existingMap = dao.getDriveCloudFileMapByKey(localRecordKey)
        val latestQueue = dao.getLatestDriveSyncQueueForKey(localRecordKey)
        val coreFallback = getCoreUuidAndDriveIdIncludingDeleted(recordCategory, recordId)
        val nextVersion = maxOf(existingMap?.version ?: 0L, latestQueue?.version ?: 0L) + 1L
        val targetDriveFileId = explicitDriveFileId
            .ifBlank { coreFallback?.second.orEmpty() }
            .ifBlank { existingMap?.driveFileId.orEmpty() }
        val resolvedUuid = recordUuid
            .ifBlank { coreFallback?.first.orEmpty() }
            .ifBlank { existingMap?.recordUuid.orEmpty() }

        dao.upsertDriveDeletionTombstone(
            DriveDeletionTombstoneEntity(
                localRecordKey = localRecordKey,
                recordCategory = recordCategory,
                recordId = recordId,
                deletedVersion = nextVersion,
                deletedAtMillis = now,
                driveFileIdAtDeletion = targetDriveFileId,
                syncedToCloud = false,
                datasetGenerationId = generationId,
                recordUuid = resolvedUuid
            )
        )

        if (existingMap != null) {
            dao.upsertDriveCloudFileMap(
                existingMap.copy(
                    version = nextVersion,
                    lastModifiedMillis = now,
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_DELETE_PENDING,
                    datasetGenerationId = generationId,
                    recordUuid = resolvedUuid
                )
            )
        }

        val payloadJson = JSONObject().apply {
            put("localRecordKey", localRecordKey)
            put("recordCategory", recordCategory)
            put("recordId", recordId)
            put("recordUuid", resolvedUuid)
            put("driveFileId", targetDriveFileId)
            put("deletedVersion", nextVersion)
            put("deletedAtMillis", now)
            put("datasetGeneration", generationId)
        }.toString()
        val hash = MirrorOutboxCodec.computeSha256(payloadJson)
        val opId = "${generationId}_${localRecordKey}_DELETE_v${nextVersion}_${hash.take(10)}"

        dao.upsertDriveLocalSyncQueueItem(
            DriveLocalSyncQueueEntity(
                operationId = opId,
                recordCategory = recordCategory,
                recordId = recordId,
                localRecordKey = localRecordKey,
                operationType = GoogleDriveMirrorSyncEngine.OP_DELETE,
                version = nextVersion,
                timestampMillis = now,
                payloadJson = payloadJson,
                previousDriveFileId = targetDriveFileId,
                contentHash = hash,
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC,
                datasetGenerationId = generationId,
                recordUuid = resolvedUuid,
                baseVersion = existingMap?.version ?: 0L,
                serverTimestampMillis = serverTimestampMillis
            )
        )
    }

    suspend fun reconcilePendingCoreEntitiesIntoOutbox(
        datasetId: String,
        generationId: String,
        serverTimestampMillis: Long
    ) {
        val pendingStatuses = listOf(
            MirrorOutboxCodec.STATUS_PENDING,
            MirrorOutboxCodec.STATUS_PENDING_CREATE,
            MirrorOutboxCodec.STATUS_PENDING_UPDATE,
            MirrorOutboxCodec.STATUS_PENDING_DELETE,
            GoogleDriveMirrorSyncEngine.STATUS_FAILED
        )
        val activeQueueKeys = dao.getRunnableDriveSyncQueueItemsOnce()
            .filter { it.datasetGenerationId == generationId || it.datasetGenerationId.isBlank() }
            .map { it.localRecordKey }
            .toSet()

        for (u in dao.getUnitsBySyncStatuses(pendingStatuses)) {
            val key = "UNIT:${u.id}"
            if (u.isDeleted || u.syncStatus == MirrorOutboxCodec.STATUS_PENDING_DELETE) {
                val latest = dao.getLatestDriveSyncQueueForKey(key)
                if (latest == null || latest.operationType != GoogleDriveMirrorSyncEngine.OP_DELETE || latest.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED) {
                    enqueueDeleteOperation(
                        localRecordKey = key,
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                        recordId = u.id,
                        explicitDriveFileId = u.driveId,
                        recordUuid = u.uuid,
                        serverTimestampMillis = serverTimestampMillis
                    )
                }
            } else if (key !in activeQueueKeys) {
                val json = MirrorOutboxCodec.serializeUnitJson(u, datasetId, generationId, serverTimestampMillis)
                enqueueRecordOperation(
                    localRecordKey = key,
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                    recordId = u.id,
                    operationType = if (u.syncStatus == MirrorOutboxCodec.STATUS_PENDING_CREATE) GoogleDriveMirrorSyncEngine.OP_CREATE else GoogleDriveMirrorSyncEngine.OP_UPDATE,
                    payloadJson = json,
                    contentHash = MirrorOutboxCodec.computeSha256(json),
                    recordUuid = u.uuid,
                    entityVersion = u.version,
                    updatedAtMillis = u.updatedAtMillis,
                    serverTimestampMillis = serverTimestampMillis
                )
            }
        }
        for (c in dao.getCustomersBySyncStatuses(pendingStatuses)) {
            val key = "CUSTOMER:${c.id}"
            if (c.isDeleted || c.syncStatus == MirrorOutboxCodec.STATUS_PENDING_DELETE) {
                val latest = dao.getLatestDriveSyncQueueForKey(key)
                if (latest == null || latest.operationType != GoogleDriveMirrorSyncEngine.OP_DELETE || latest.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED) {
                    enqueueDeleteOperation(
                        localRecordKey = key,
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
                        recordId = c.id,
                        explicitDriveFileId = c.driveId,
                        recordUuid = c.uuid,
                        serverTimestampMillis = serverTimestampMillis
                    )
                }
            } else if (key !in activeQueueKeys) {
                val json = MirrorOutboxCodec.serializeCustomerJson(c, datasetId, generationId, serverTimestampMillis)
                enqueueRecordOperation(
                    localRecordKey = key,
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
                    recordId = c.id,
                    operationType = if (c.syncStatus == MirrorOutboxCodec.STATUS_PENDING_CREATE) GoogleDriveMirrorSyncEngine.OP_CREATE else GoogleDriveMirrorSyncEngine.OP_UPDATE,
                    payloadJson = json,
                    contentHash = MirrorOutboxCodec.computeSha256(json),
                    recordUuid = c.uuid,
                    entityVersion = c.version,
                    updatedAtMillis = c.updatedAtMillis,
                    serverTimestampMillis = serverTimestampMillis
                )
            }
        }
        for (tx in dao.getTransactionsBySyncStatuses(pendingStatuses)) {
            val key = "TRANSACTION:${tx.id}"
            if (tx.isDeleted || tx.syncStatus == MirrorOutboxCodec.STATUS_PENDING_DELETE) {
                val latest = dao.getLatestDriveSyncQueueForKey(key)
                if (latest == null || latest.operationType != GoogleDriveMirrorSyncEngine.OP_DELETE || latest.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED) {
                    enqueueDeleteOperation(
                        localRecordKey = key,
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION,
                        recordId = tx.id,
                        explicitDriveFileId = tx.driveId,
                        recordUuid = tx.uuid,
                        serverTimestampMillis = serverTimestampMillis
                    )
                }
            } else if (key !in activeQueueKeys) {
                val json = MirrorOutboxCodec.serializeTransactionJson(tx, datasetId, generationId, serverTimestampMillis)
                enqueueRecordOperation(
                    localRecordKey = key,
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION,
                    recordId = tx.id,
                    operationType = if (tx.syncStatus == MirrorOutboxCodec.STATUS_PENDING_CREATE) GoogleDriveMirrorSyncEngine.OP_CREATE else GoogleDriveMirrorSyncEngine.OP_UPDATE,
                    payloadJson = json,
                    contentHash = MirrorOutboxCodec.computeSha256(json),
                    recordUuid = tx.uuid,
                    entityVersion = tx.version,
                    updatedAtMillis = tx.updatedAtMillis,
                    serverTimestampMillis = serverTimestampMillis
                )
            }
        }
        for (p in dao.getPaymentProofsBySyncStatuses(pendingStatuses)) {
            val key = "PAYMENT:${p.id}"
            if (p.isDeleted || p.syncStatus == MirrorOutboxCodec.STATUS_PENDING_DELETE) {
                val latest = dao.getLatestDriveSyncQueueForKey(key)
                if (latest == null || latest.operationType != GoogleDriveMirrorSyncEngine.OP_DELETE || latest.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED) {
                    enqueueDeleteOperation(
                        localRecordKey = key,
                        recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT,
                        recordId = p.id,
                        explicitDriveFileId = p.driveId,
                        recordUuid = p.uuid,
                        serverTimestampMillis = serverTimestampMillis
                    )
                }
            } else if (key !in activeQueueKeys) {
                val json = MirrorOutboxCodec.serializePaymentJson(p, datasetId, generationId, serverTimestampMillis)
                enqueueRecordOperation(
                    localRecordKey = key,
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT,
                    recordId = p.id,
                    operationType = if (p.syncStatus == MirrorOutboxCodec.STATUS_PENDING_CREATE) GoogleDriveMirrorSyncEngine.OP_CREATE else GoogleDriveMirrorSyncEngine.OP_UPDATE,
                    payloadJson = json,
                    contentHash = MirrorOutboxCodec.computeSha256(json),
                    recordUuid = p.uuid,
                    entityVersion = p.version,
                    updatedAtMillis = p.updatedAtMillis,
                    serverTimestampMillis = serverTimestampMillis
                )
            }
        }
    }

    suspend fun getCoreUuidAndDriveIdIncludingDeleted(
        recordCategory: String,
        recordId: Long
    ): Pair<String, String>? {
        if (recordId <= 0L) return null
        return when (recordCategory) {
            GoogleDriveMirrorSyncEngine.CATEGORY_UNIT -> dao.getUnitByIdIncludingDeleted(recordId)?.let { it.uuid to it.driveId }
            GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER -> dao.getCustomerByIdIncludingDeleted(recordId)?.let { it.uuid to it.driveId }
            GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION -> dao.getTransactionByIdIncludingDeleted(recordId)?.let { it.uuid to it.driveId }
            GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT -> dao.getPaymentProofByIdIncludingDeleted(recordId)?.let { it.uuid to it.driveId }
            else -> null
        }
    }

    suspend fun updateCoreEntitySyncStatus(
        recordCategory: String,
        recordId: Long,
        syncStatus: String
    ) {
        if (recordId <= 0L) return
        runCatching {
            when (recordCategory) {
                GoogleDriveMirrorSyncEngine.CATEGORY_UNIT -> dao.updateUnitSyncStatus(recordId, syncStatus)
                GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER -> dao.updateCustomerSyncStatus(recordId, syncStatus)
                GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION -> dao.updateTransactionSyncStatus(recordId, syncStatus)
                GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT -> dao.updatePaymentProofSyncStatus(recordId, syncStatus)
            }
        }
    }

    suspend fun updateCoreEntitySyncStatusAndDriveId(
        recordCategory: String,
        recordId: Long,
        syncStatus: String,
        driveId: String
    ) {
        if (recordId <= 0L) return
        runCatching {
            when (recordCategory) {
                GoogleDriveMirrorSyncEngine.CATEGORY_UNIT -> dao.updateUnitSyncAndDriveId(recordId, syncStatus, driveId)
                GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER -> dao.updateCustomerSyncAndDriveId(recordId, syncStatus, driveId)
                GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION -> dao.updateTransactionSyncAndDriveId(recordId, syncStatus, driveId)
                GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT -> dao.updatePaymentProofSyncAndDriveId(recordId, syncStatus, driveId)
            }
        }
    }

    suspend fun hardDeleteCoreEntityById(
        recordCategory: String,
        recordId: Long
    ) {
        if (recordId <= 0L) return
        runCatching {
            when (recordCategory) {
                GoogleDriveMirrorSyncEngine.CATEGORY_UNIT -> dao.hardDeleteUnitById(recordId)
                GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER -> dao.hardDeleteCustomerById(recordId)
                GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION -> dao.hardDeleteTransactionById(recordId)
                GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT -> dao.hardDeletePaymentProofById(recordId)
            }
        }
    }
}
