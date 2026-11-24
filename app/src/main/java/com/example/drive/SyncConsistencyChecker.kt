package com.example.drive

import android.content.Context
import com.example.data.local.DataConsistencyAuditReport
import com.example.data.local.DriveCloudQuarantineEntity
import com.example.data.local.FreezerDao
import com.example.data.local.SyncTechnicalLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of cleaning ORPHAN_CLOUD files, deduplicating Drive records, resetting Stale/Duplicate markers,
 * and re-running full synchronization + consistency audit.
 */
data class OrphanCleanupAndResyncReport(
    val deletedOrphanCloudFiles: Int = 0,
    val deletedDuplicateCloudFiles: Int = 0,
    val clearedStaleQueueRows: Int = 0,
    val clearedQuarantineRows: Int = 0,
    val clearedOrphanMappings: Int = 0,
    val resyncedPendingRecords: Int = 0,
    val postCleanupAudit: DataConsistencyAuditReport? = null,
    val summaryMessage: String = ""
)

/**
 * 6. SyncConsistencyChecker
 * Performs strict non-mutating audits between Local SQLite and Google Drive Cloud Mirror:
 * - Missing in Cloud
 * - Duplicate Cloud Files & Duplicate UUIDs (local & cloud)
 * - Stale / Orphan cloud files
 * - UUID Mismatches
 * - Revision / Version Mismatches
 * - SHA-256 Checksum Mismatches
 * - Deleted / Tombstone Violations
 * - Generation Mismatches (invalidated post-Factory-Reset generations & manifest checks)
 * - Sync Conflicts & Active Cloud Quarantine
 */
class SyncConsistencyChecker(
    private val context: Context,
    private val dao: FreezerDao,
    private val driveService: GoogleDriveService,
    private val generationManager: DatabaseGenerationManager,
    private val conflictResolver: SyncConflictResolver
) {
    private val ignoredMetaFiles = setOf(
        GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME,
        "APP_SETTINGS.json",
        "customers.json",
        "units.json",
        "transactions.json",
        "payments.json"
    )

    private fun formatDisplayTime(millis: Long): String {
        if (millis <= 0L) return "Belum pernah disinkronkan"
        return SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID")).format(Date(millis))
    }

    suspend fun runDataConsistencyAudit(): Result<DataConsistencyAuditReport> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val nowDisplay = formatDisplayTime(now)
            val localCustomers = dao.getAllCustomersOnce()
            val localUnits = dao.getAllUnitsOnce()
            val localTx = dao.getAllTransactionsOnce()
            val localPayments = dao.getAllPaymentProofsOnce()
            val queueItems = dao.getAllDriveLocalSyncQueueOnce()
            val mappings = dao.getAllDriveCloudFileMappingsOnce()
            val tombstones = dao.getAllDriveDeletionTombstonesOnce()
            val pendingCount = queueItems.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC }
            val failedCount = queueItems.count { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_FAILED }
            val localGen = generationManager.getCurrentGenerationId()
            val invalidatedGens = generationManager.getInvalidatedGenerations()
            val lastResetMs = generationManager.getLastResetTimestampMillis()
            val localPhotoCount = mappings.count { driveService.isPhotoCategory(it.recordCategory) }

            val conflictRecords = mutableListOf<String>()
            queueItems.filter { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT }.forEach {
                conflictRecords.add("${it.localRecordKey}: ${it.lastError}")
            }
            mappings.filter { it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT }.forEach { m ->
                if (conflictRecords.none { it.startsWith("${m.localRecordKey}:") }) {
                    conflictRecords.add("${m.localRecordKey}: ${m.conflictReason}")
                }
            }

            // Detect any duplicate UUIDs inside Local SQLite first
            val localAllUuids = buildList {
                localCustomers.forEach { if (it.uuid.isNotBlank()) add("CUSTOMER:${it.id}" to it.uuid) }
                localUnits.forEach { if (it.uuid.isNotBlank()) add("UNIT:${it.id}" to it.uuid) }
                localTx.forEach { if (it.uuid.isNotBlank()) add("TRANSACTION:${it.id}" to it.uuid) }
                localPayments.forEach { if (it.uuid.isNotBlank()) add("PAYMENT:${it.id}" to it.uuid) }
            }
            val duplicateUuids = mutableListOf<String>()
            localAllUuids.groupBy { it.second }
                .filter { it.value.size > 1 }
                .forEach { (uuid, entries) ->
                    duplicateUuids.add("LOCAL_DUPLICATE_UUID:$uuid (${entries.joinToString { it.first }})")
                }

            val token = driveService.resolveValidAccessToken().getOrElse { err ->
                val report = DataConsistencyAuditReport(
                    localCustomersCount = localCustomers.size,
                    localUnitsCount = localUnits.size,
                    localTransactionsCount = localTx.size,
                    localPaymentsCount = localPayments.size,
                    localPhotosCount = localPhotoCount,
                    conflictRecords = conflictRecords,
                    pendingOperationsCount = pendingCount,
                    failedOperationsCount = failedCount,
                    localDatasetGeneration = localGen,
                    isConsistent = false,
                    auditedAtDisplay = nowDisplay,
                    errorMessage = err.message ?: "Google Drive belum terhubung",
                    duplicateUuids = duplicateUuids
                )
                return@withContext Result.success(report)
            }

            val (activeObjects, docObjects) = driveService.listMirrorActiveAndDocObjects(token)
                .getOrElse { return@withContext Result.failure(it) }

            val activeFiles = activeObjects.filter { !it.isFolder }.map { it.item }
            val docFiles = docObjects.filter { !it.isFolder }.map { it.item }

            val custFolderIds = activeObjects.filter { it.isFolder && it.item.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_CUSTOMERS }.map { it.item.id }.toSet()
            val unitFolderIds = activeObjects.filter { it.isFolder && it.item.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_UNITS }.map { it.item.id }.toSet()
            val txFolderIds = activeObjects.filter { it.isFolder && it.item.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_TRANSACTIONS }.map { it.item.id }.toSet()
            val payFolderIds = activeObjects.filter { it.isFolder && it.item.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_PAYMENTS }.map { it.item.id }.toSet()

            val localCustUuids = localCustomers.map { it.uuid }.filter { it.isNotBlank() }.toSet()
            val localUnitUuids = localUnits.map { it.uuid }.filter { it.isNotBlank() }.toSet()
            val localTxUuids = localTx.map { it.uuid }.filter { it.isNotBlank() }.toSet()
            val localPayUuids = localPayments.map { it.uuid }.filter { it.isNotBlank() }.toSet()

            val operationalJsonObjs = activeObjects.filter { obj ->
                !obj.isFolder &&
                    !obj.item.trashed &&
                    obj.item.name.endsWith(".json", ignoreCase = true) &&
                    obj.item.name !in ignoredMetaFiles
            }

            val cloudCustomerFiles = operationalJsonObjs.filter { obj ->
                obj.parentFolderId in custFolderIds ||
                    obj.item.name.startsWith("CUSTOMER_") ||
                    (obj.item.recordUuid.isNotBlank() && obj.item.recordUuid in localCustUuids)
            }.map { it.item }
            val cloudUnitFiles = operationalJsonObjs.filter { obj ->
                obj.parentFolderId in unitFolderIds ||
                    obj.item.name.startsWith("UNIT_") ||
                    (obj.item.recordUuid.isNotBlank() && obj.item.recordUuid in localUnitUuids)
            }.map { it.item }
            val cloudTxFiles = operationalJsonObjs.filter { obj ->
                obj.parentFolderId in txFolderIds ||
                    obj.item.name.startsWith("TRANSACTION_") ||
                    (obj.item.recordUuid.isNotBlank() && obj.item.recordUuid in localTxUuids)
            }.map { it.item }
            val cloudPaymentFiles = operationalJsonObjs.filter { obj ->
                obj.parentFolderId in payFolderIds ||
                    obj.item.name.startsWith("PAYMENT_") ||
                    (obj.item.recordUuid.isNotBlank() && obj.item.recordUuid in localPayUuids)
            }.map { it.item }

            val allActiveNames = (activeFiles + docFiles).map { it.name }
            val duplicateNames = allActiveNames.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toList()

            // Detect duplicate UUIDs across active cloud operational files
            operationalJsonObjs.map { it.item }
                .filter { it.recordUuid.isNotBlank() }
                .groupBy { it.recordUuid }
                .filter { it.value.size > 1 }
                .forEach { (uuid, files) ->
                    duplicateUuids.add("CLOUD_DUPLICATE_UUID:$uuid (${files.joinToString { "${it.name}:${it.id}" }})")
                }

            val expectedNames = buildSet {
                localCustomers.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER, it.id, it.uuid)) }
                localUnits.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_UNIT, it.id, it.uuid)) }
                localTx.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION, it.id, it.uuid)) }
                localPayments.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT, it.id, it.uuid)) }
            }

            data class LocalExpectedRecord(
                val localRecordKey: String,
                val uuid: String,
                val version: Long,
                val expectedHash: String
            )

            val mapByKey = mappings.associateBy { it.localRecordKey }
            val localExpectedByFile = buildMap<String, LocalExpectedRecord> {
                localCustomers.forEach {
                    val key = "CUSTOMER:${it.id}"
                    put(
                        MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER, it.id, it.uuid),
                        LocalExpectedRecord(key, it.uuid, it.version, mapByKey[key]?.contentHash.orEmpty())
                    )
                }
                localUnits.forEach {
                    val key = "UNIT:${it.id}"
                    put(
                        MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_UNIT, it.id, it.uuid),
                        LocalExpectedRecord(key, it.uuid, it.version, mapByKey[key]?.contentHash.orEmpty())
                    )
                }
                localTx.forEach {
                    val key = "TRANSACTION:${it.id}"
                    put(
                        MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION, it.id, it.uuid),
                        LocalExpectedRecord(key, it.uuid, it.version, mapByKey[key]?.contentHash.orEmpty())
                    )
                }
                localPayments.forEach {
                    val key = "PAYMENT:${it.id}"
                    put(
                        MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT, it.id, it.uuid),
                        LocalExpectedRecord(key, it.uuid, it.version, mapByKey[key]?.contentHash.orEmpty())
                    )
                }
            }
            val expectedMetaByUuid = localExpectedByFile.values.filter { it.uuid.isNotBlank() }.associateBy { it.uuid }

            val activeFileNamesSet = activeFiles.map { it.name }.toSet()
            val activeCloudUuidSet = activeFiles.map { it.recordUuid }.filter { it.isNotBlank() }.toSet()
            val missingInCloud = expectedNames.filter { expectedName ->
                val expectedUuid = localExpectedByFile[expectedName]?.uuid.orEmpty()
                expectedName !in activeFileNamesSet && (expectedUuid.isBlank() || expectedUuid !in activeCloudUuidSet)
            }

            val staleRecords = mutableListOf<String>()
            val uuidMismatches = mutableListOf<String>()
            val versionMismatches = mutableListOf<String>()
            val checksumMismatches = mutableListOf<String>()
            val generationMismatches = mutableListOf<String>()
            val tombstoneViolations = mutableListOf<String>()
            val tombstoneKeys = tombstones.map { it.localRecordKey }.toSet()
            val tombstoneUuids = tombstones.map { it.recordUuid }.filter { it.isNotBlank() }.toSet()

            val recordCloudFiles = operationalJsonObjs.map { it.item }
            for (cf in recordCloudFiles) {
                val expectedMeta = localExpectedByFile[cf.name]
                    ?: cf.recordUuid.takeIf { it.isNotBlank() }?.let { expectedMetaByUuid[it] }
                val inferredKey = cf.name.removeSuffix(".json").replaceFirst("_", ":")
                if (inferredKey in tombstoneKeys || (cf.recordUuid.isNotBlank() && cf.recordUuid in tombstoneUuids)) {
                    tombstoneViolations.add("TOMBSTONE_STILL_ON_CLOUD:${cf.name} (${cf.id})")
                }
                val matchesActive = cf.name in expectedNames || (cf.recordUuid.isNotBlank() && cf.recordUuid in expectedMetaByUuid)
                if (!matchesActive) {
                    staleRecords.add("ORPHAN_CLOUD:${cf.name} (${cf.id})")
                    if (cf.datasetGeneration.isNotBlank() &&
                        (cf.datasetGeneration in invalidatedGens || cf.datasetGeneration != localGen)
                    ) {
                        generationMismatches.add("ORPHAN_GEN_MISMATCH:${cf.name} (local=$localGen vs cloud=${cf.datasetGeneration})")
                    }
                } else if (expectedMeta != null) {
                    if (expectedMeta.uuid.isBlank()) {
                        uuidMismatches.add("LOCAL_UUID_EMPTY:${cf.name}")
                    } else if (cf.recordUuid.isNotBlank() && cf.recordUuid != expectedMeta.uuid) {
                        uuidMismatches.add("UUID_MISMATCH:${cf.name} (local=${expectedMeta.uuid} vs cloud=${cf.recordUuid})")
                    }
                    if (cf.remoteVersion > 0L && cf.remoteVersion != expectedMeta.version) {
                        versionMismatches.add("VERSION_MISMATCH:${cf.name} (local=v${expectedMeta.version} vs cloud=v${cf.remoteVersion})")
                    }
                    if (cf.sha256Checksum.isNotBlank() &&
                        expectedMeta.expectedHash.isNotBlank() &&
                        !cf.sha256Checksum.equals(expectedMeta.expectedHash, ignoreCase = true)
                    ) {
                        checksumMismatches.add("CHECKSUM_MISMATCH:${cf.name} (local=${expectedMeta.expectedHash.take(12)} vs cloud=${cf.sha256Checksum.take(12)})")
                    }
                    // Strictly flag invalidated generation (after Factory Reset) while keeping UUID-matched drift tolerance before reset
                    if (cf.datasetGeneration.isNotBlank() && cf.datasetGeneration in invalidatedGens) {
                        generationMismatches.add("INVALIDATED_GEN_MATCHED:${cf.name} (${cf.datasetGeneration} revoked; current=$localGen)")
                    }
                }
            }
            if (localCustomers.any { it.uuid.isBlank() } ||
                localUnits.any { it.uuid.isBlank() } ||
                localTx.any { it.uuid.isBlank() } ||
                localPayments.any { it.uuid.isBlank() }
            ) {
                uuidMismatches.add("EMPTY_LOCAL_UUID_DETECTED")
            }

            var cloudGen = ""
            var cloudStatus = ""
            val manifestFile = activeFiles.firstOrNull { it.name == GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME }
            if (manifestFile != null) {
                val manifestText = driveService.driveClient.downloadFileText(token, manifestFile.id).getOrNull()
                if (!manifestText.isNullOrBlank()) {
                    val mJson = runCatching { JSONObject(manifestText) }.getOrNull()
                    if (mJson != null) {
                        cloudGen = mJson.optString("datasetGeneration", mJson.optString("generation_id", ""))
                        cloudStatus = mJson.optString("datasetStatus", "")
                    }
                }
            }
            if (cloudGen.isNotBlank() && (cloudGen in invalidatedGens || (lastResetMs > 0L && cloudGen != localGen))) {
                generationMismatches.add("MANIFEST_GEN_MISMATCH:local=$localGen vs cloud=$cloudGen")
            }

            // Stale Cloud Scan & Quarantine (never auto-imports or deletes unverified files)
            scanAndQuarantineStaleCloudFilesInternal(token, activeObjects, docObjects)
            val activeQuarantine = dao.getActiveDriveCloudQuarantineOnce()
            val quarantineSummaries = activeQuarantine.map { q ->
                val trustTag = if (q.ownershipVerified) "Milik App Terverifikasi" else "Kepemilikan Tidak Pasti"
                "${q.classification}: ${q.fileName} ($trustTag)"
            }

            val totalCloudOpsCount = cloudCustomerFiles.size + cloudUnitFiles.size + cloudTxFiles.size + cloudPaymentFiles.size
            val serverRefMs = driveService.currentServerReferenceTimeMillis()
            generationManager.recordAuditedCloudState(totalCloudOpsCount, serverRefMs)

            val isConsistent = missingInCloud.isEmpty() &&
                duplicateNames.isEmpty() &&
                duplicateUuids.isEmpty() &&
                staleRecords.isEmpty() &&
                uuidMismatches.isEmpty() &&
                versionMismatches.isEmpty() &&
                checksumMismatches.isEmpty() &&
                generationMismatches.isEmpty() &&
                conflictRecords.isEmpty() &&
                tombstoneViolations.isEmpty() &&
                activeQuarantine.isEmpty() &&
                pendingCount == 0 &&
                failedCount == 0 &&
                cloudCustomerFiles.size == localCustomers.size &&
                cloudUnitFiles.size == localUnits.size &&
                cloudTxFiles.size == localTx.size &&
                cloudPaymentFiles.size == localPayments.size

            val report = DataConsistencyAuditReport(
                localCustomersCount = localCustomers.size,
                localUnitsCount = localUnits.size,
                localTransactionsCount = localTx.size,
                localPaymentsCount = localPayments.size,
                localPhotosCount = localPhotoCount,
                cloudCustomersCount = cloudCustomerFiles.size,
                cloudUnitsCount = cloudUnitFiles.size,
                cloudTransactionsCount = cloudTxFiles.size,
                cloudPaymentsCount = cloudPaymentFiles.size,
                cloudPhotosCount = docFiles.size,
                missingInCloud = missingInCloud,
                duplicateCloudFiles = duplicateNames,
                staleRecords = staleRecords,
                uuidMismatches = uuidMismatches,
                versionMismatches = versionMismatches,
                conflictRecords = conflictRecords,
                tombstoneViolations = tombstoneViolations,
                pendingOperationsCount = pendingCount,
                failedOperationsCount = failedCount,
                localDatasetGeneration = localGen,
                cloudDatasetGeneration = cloudGen,
                cloudManifestStatus = cloudStatus,
                serverTimeMillis = serverRefMs,
                quarantinedFilesCount = activeQuarantine.size,
                quarantinedItemsSummary = quarantineSummaries,
                isConsistent = isConsistent,
                auditedAtDisplay = nowDisplay,
                duplicateUuids = duplicateUuids,
                checksumMismatches = checksumMismatches,
                generationMismatches = generationMismatches
            )

            dao.insertSyncTechnicalLog(
                SyncTechnicalLogEntity(
                    operationTimeMillis = now,
                    operationTimeDisplay = nowDisplay,
                    operationType = "CONSISTENCY_AUDIT",
                    datasetGenerationId = localGen,
                    localStatus = "C=${localCustomers.size},U=${localUnits.size},T=${localTx.size},P=${localPayments.size}",
                    cloudStatus = "C=${cloudCustomerFiles.size},U=${cloudUnitFiles.size},T=${cloudTxFiles.size},P=${cloudPaymentFiles.size},Q=${activeQuarantine.size}",
                    errorCode = if (isConsistent) "" else "CONSISTENCY_DRIFT",
                    errorMessage = if (isConsistent) {
                        "Konsisten 100%"
                    } else {
                        "Missing=${missingInCloud.size}, DupName=${duplicateNames.size}, DupUuid=${duplicateUuids.size}, Stale=${staleRecords.size}, Quarantine=${activeQuarantine.size}, UUID=${uuidMismatches.size}, Ver=${versionMismatches.size}, Checksum=${checksumMismatches.size}, Gen=${generationMismatches.size}, Conflict=${conflictRecords.size}, Tombstone=${tombstoneViolations.size}"
                    }
                )
            )

            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanAndQuarantineStaleCloudFiles(): Result<List<DriveCloudQuarantineEntity>> = withContext(Dispatchers.IO) {
        try {
            val token = driveService.resolveValidAccessToken().getOrElse { return@withContext Result.failure(it) }
            val (activeObjects, docObjects) = driveService.listMirrorActiveAndDocObjects(token)
                .getOrElse { return@withContext Result.failure(it) }
            val quarantined = scanAndQuarantineStaleCloudFilesInternal(token, activeObjects, docObjects)
            Result.success(quarantined)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanAndQuarantineStaleCloudFilesInternal(
        accessToken: String,
        activeObjects: List<DriveRecursiveObject>,
        docObjects: List<DriveRecursiveObject>
    ): List<DriveCloudQuarantineEntity> {
        val customers = dao.getAllCustomersOnce()
        val units = dao.getAllUnitsOnce()
        val txs = dao.getAllTransactionsOnce()
        val payments = dao.getAllPaymentProofsOnce()
        val mappings = dao.getAllDriveCloudFileMappingsOnce()
        val tombstones = dao.getAllDriveDeletionTombstonesOnce()
        val photoMeta = dao.getAllDrivePhotoMetadataOnce()

        val localGen = generationManager.getCurrentGenerationId()
        val invalidatedGens = generationManager.getInvalidatedGenerations()
        val localDatasetId = generationManager.getCurrentDatasetId()

        val expectedJsonFiles = buildSet {
            customers.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER, it.id, it.uuid)) }
            units.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_UNIT, it.id, it.uuid)) }
            txs.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION, it.id, it.uuid)) }
            payments.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT, it.id, it.uuid)) }
            add("APP_SETTINGS.json")
            add(GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME)
            if (customers.isNotEmpty() || mappings.any { it.localRecordKey == "SNAPSHOT:CUSTOMERS" }) add("customers.json")
            if (units.isNotEmpty() || mappings.any { it.localRecordKey == "SNAPSHOT:UNITS" }) add("units.json")
            if (txs.isNotEmpty() || mappings.any { it.localRecordKey == "SNAPSHOT:TRANSACTIONS" }) add("transactions.json")
            if (payments.isNotEmpty() || mappings.any { it.localRecordKey == "SNAPSHOT:PAYMENTS" }) add("payments.json")
        }
        val activeUuidSet = buildSet {
            customers.forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
            units.forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
            txs.forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
            payments.forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
        }
        val tombstoneKeys = tombstones.map { it.localRecordKey }.toSet()
        val tombstoneUuids = tombstones.map { it.recordUuid }.filter { it.isNotBlank() }.toSet()
        val mapByFileName = mappings.filter {
            it.fileName.isNotBlank() && it.syncStatus != GoogleDriveMirrorSyncEngine.STATUS_DELETE_PENDING
        }.associateBy { it.fileName }
        val knownMappedDriveIds = buildSet {
            mappings.forEach { if (it.driveFileId.isNotBlank()) add(it.driveFileId) }
            photoMeta.forEach { if (it.driveFileId.isNotBlank()) add(it.driveFileId) }
            tombstones.forEach { if (it.driveFileIdAtDeletion.isNotBlank()) add(it.driveFileIdAtDeletion) }
        }
        val validPhotoFileNames = buildSet {
            mappings.filter {
                driveService.isPhotoCategory(it.recordCategory) && it.syncStatus != GoogleDriveMirrorSyncEngine.STATUS_DELETE_PENDING
            }.forEach { if (it.fileName.isNotBlank()) add(it.fileName) }
            photoMeta.forEach { if (it.fileName.isNotBlank()) add(it.fileName) }
        }

        val extraLegacyObjects = mutableListOf<DriveRecursiveObject>()
        runCatching {
            val legacyRoots = driveService.driveClient.findFoldersByName(accessToken, GoogleDriveRestClient.DOC_ROOT_FOLDER_NAME)
                .getOrDefault(emptyList())
            for (legacyRoot in legacyRoots) {
                val topChildren = driveService.driveClient.listFilesInFolder(accessToken, legacyRoot.id).getOrDefault(emptyList())
                for (child in topChildren) {
                    if (child.name == GoogleDrivePhotoCenterManager.FOLDER_SECTION_BACKUP_DOKUMENTASI ||
                        child.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_HISTORICAL_BACKUP ||
                        child.name.endsWith(".zip", ignoreCase = true)
                    ) continue
                    if (child.mimeType == GoogleDriveRestClient.FOLDER_MIME) {
                        extraLegacyObjects.addAll(driveService.driveClient.listAllObjectsRecursively(accessToken, child.id).getOrDefault(emptyList()))
                    } else {
                        extraLegacyObjects.add(DriveRecursiveObject(child, legacyRoot.id, false, 1))
                    }
                }
            }
        }

        val allScannedFileObjects = (activeObjects + docObjects + extraLegacyObjects)
            .filter { !it.isFolder && !it.item.trashed && !it.item.name.endsWith(".zip", ignoreCase = true) }

        val groupedByName = allScannedFileObjects.groupBy { it.item.name }
        val now = System.currentTimeMillis()
        val quarantinedIdsSeen = mutableSetOf<String>()

        for (obj in allScannedFileObjects) {
            val item = obj.item
            val name = item.name
            val inferredKey = when {
                name.startsWith("CUSTOMER_") && name.endsWith(".json") ->
                    "CUSTOMER:${name.removePrefix("CUSTOMER_").removeSuffix(".json")}"
                name.startsWith("UNIT_") && name.endsWith(".json") ->
                    "UNIT:${name.removePrefix("UNIT_").removeSuffix(".json")}"
                name.startsWith("TRANSACTION_") && name.endsWith(".json") ->
                    "TRANSACTION:${name.removePrefix("TRANSACTION_").removeSuffix(".json")}"
                name.startsWith("PAYMENT_") && name.endsWith(".json") ->
                    "PAYMENT:${name.removePrefix("PAYMENT_").removeSuffix(".json")}"
                else -> ""
            }

            val sameNameSiblings = groupedByName[name].orEmpty()
            val preferredMappedId = mapByFileName[name]?.driveFileId.orEmpty()
            val primaryIdForName = if (preferredMappedId.isNotBlank() && sameNameSiblings.any { it.item.id == preferredMappedId }) {
                preferredMappedId
            } else {
                sameNameSiblings.maxByOrNull { it.item.remoteVersion }?.item?.id.orEmpty()
            }
            val isDuplicateCopy = sameNameSiblings.size > 1 && item.id != primaryIdForName

            val isInTombstone = (inferredKey.isNotBlank() && inferredKey in tombstoneKeys) ||
                (item.recordUuid.isNotBlank() && item.recordUuid in tombstoneUuids)

            val isValidActiveJson = (name in expectedJsonFiles) ||
                (item.recordUuid.isNotBlank() && item.recordUuid in activeUuidSet)
            val isValidActivePhoto = obj in docObjects && (name in validPhotoFileNames || item.id in knownMappedDriveIds)
            val isActiveRecordFile = isValidActiveJson || isValidActivePhoto
            val isNoDbReference = !isActiveRecordFile

            // Strictly quarantine invalidated post-Factory-Reset generations OR orphan files from a different generation.
            // Keeps active UUID-matched records with non-invalidated generation drift unquarantined (per testK).
            val isInvalidatedGen = item.datasetGeneration.isNotBlank() && item.datasetGeneration in invalidatedGens
            val isPreviousGen = isInvalidatedGen || (
                !isActiveRecordFile &&
                    item.datasetGeneration.isNotBlank() &&
                    item.datasetGeneration != localGen
                )

            if (!isDuplicateCopy && !isInTombstone && !isPreviousGen && !isNoDbReference) {
                continue
            }

            val classification = when {
                isInTombstone -> "DELETED_IN_LOCAL_TOMBSTONE"
                isPreviousGen -> "PREVIOUS_GENERATION"
                isDuplicateCopy -> "DUPLICATE_FILE"
                name.startsWith("CUSTOMER_") && name.endsWith(".json") -> "OLD_CUSTOMER_FILE"
                name.startsWith("UNIT_") && name.endsWith(".json") -> "OLD_UNIT_FILE"
                else -> "NO_DB_REFERENCE"
            }

            val reason = when (classification) {
                "DELETED_IN_LOCAL_TOMBSTONE" -> "File cocok dengan tombstone penghapusan lokal (${inferredKey.ifBlank { item.recordUuid }})."
                "PREVIOUS_GENERATION" -> "File berasal dari generasi terdahulu/terinvalidasi (${item.datasetGeneration} != $localGen)."
                "DUPLICATE_FILE" -> "Terdapat ${sameNameSiblings.size} salinan bernama '$name' di Google Drive."
                "OLD_CUSTOMER_FILE" -> "File pelanggan '$name' tidak memiliki referensi pelanggan aktif di SQLite."
                "OLD_UNIT_FILE" -> "File unit '$name' tidak memiliki referensi unit aktif di SQLite."
                else -> "File '$name' tidak memiliki referensi aktif di SQLite."
            }

            val ownershipVerified = item.appId == MirrorOutboxCodec.APP_ID_VALUE ||
                item.applicationId == GoogleDriveMirrorSyncEngine.APPLICATION_ID ||
                (item.datasetId.isNotBlank() && item.datasetId == localDatasetId) ||
                (item.recordUuid.isNotBlank() && (item.recordUuid in activeUuidSet || item.recordUuid in tombstoneUuids)) ||
                item.id in knownMappedDriveIds

            quarantinedIdsSeen.add(item.id)
            dao.upsertDriveCloudQuarantine(
                DriveCloudQuarantineEntity(
                    driveFileId = item.id,
                    driveParentFolderId = obj.parentFolderId,
                    fileName = name,
                    classification = classification,
                    localRecordKey = inferredKey,
                    recordUuid = item.recordUuid,
                    datasetGenerationId = item.datasetGeneration,
                    ownershipVerified = ownershipVerified,
                    quarantineReason = reason,
                    status = "QUARANTINED",
                    quarantinedAtMillis = now,
                    resolvedAtMillis = 0L
                )
            )
        }

        val currentActiveQuarantine = dao.getActiveDriveCloudQuarantineOnce()
        for (existing in currentActiveQuarantine) {
            if (existing.driveFileId !in quarantinedIdsSeen) {
                dao.updateDriveCloudQuarantineStatus(existing.driveFileId, "PURGED", now)
            }
        }

        return dao.getActiveDriveCloudQuarantineOnce()
    }

    suspend fun purgeVerifiedQuarantinedFiles(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val token = driveService.resolveValidAccessToken().getOrElse { return@withContext Result.failure(it) }
            val activeQuarantine = dao.getActiveDriveCloudQuarantineOnce()
            val eligible = activeQuarantine.filter { it.ownershipVerified }
            var purgedCount = 0
            val now = System.currentTimeMillis()
            for (q in eligible) {
                driveService.deleteFileStrictAndVerify(token, q.driveFileId, q.fileName)
                    .getOrElse { return@withContext Result.failure(it) }
                dao.updateDriveCloudQuarantineStatus(q.driveFileId, "PURGED", now)
                purgedCount++
            }
            dao.insertSyncTechnicalLog(
                SyncTechnicalLogEntity(
                    operationTimeMillis = now,
                    operationTimeDisplay = formatDisplayTime(now),
                    operationType = "PURGE_VERIFIED_QUARANTINE",
                    datasetGenerationId = generationManager.getCurrentGenerationId(),
                    localStatus = "PURGED=$purgedCount",
                    cloudStatus = "VERIFIED",
                    errorCode = "",
                    errorMessage = "Dihapus=$purgedCount, Dipertahankan (tidak pasti)=${activeQuarantine.size - eligible.size}"
                )
            )
            Result.success(purgedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Automatic Quarantine Self-Healing Recovery:
     * Prevents valid records from being permanently trapped in `QUARANTINED` status after transient sync/network glitches.
     * - If a quarantined entry's `recordUuid` matches an active (non-deleted) local record AND its generation is NOT in
     *   `invalidatedGens` (post-factory-reset), it is automatically released (`AUTO_RECOVERED`) so normal sync resumes.
     * - If `autoPurgeVerifiedTombstones = true`, verified app-owned entries matching a local deletion tombstone or duplicate
     *   are cleaned up automatically while leaving unverified third-party files (`ownershipVerified == false`) untouched.
     */
    suspend fun autoRecoverEligibleQuarantineItems(
        autoPurgeVerifiedTombstones: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val activeQuarantine = dao.getActiveDriveCloudQuarantineOnce()
            if (activeQuarantine.isEmpty()) return@withContext Result.success(0)

            val invalidatedGens = generationManager.getInvalidatedGenerations()
            val activeUuids = buildSet {
                dao.getAllCustomersOnce().forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
                dao.getAllUnitsOnce().forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
                dao.getAllTransactionsOnce().forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
                dao.getAllPaymentProofsOnce().forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
            }
            val tombstoneUuids = dao.getAllDriveDeletionTombstonesOnce()
                .map { it.recordUuid }
                .filter { it.isNotBlank() }
                .toSet()

            val now = System.currentTimeMillis()
            var recoveredCount = 0

            val token = if (autoPurgeVerifiedTombstones) {
                driveService.resolveValidAccessToken().getOrNull()
            } else null

            for (q in activeQuarantine) {
                val isInvalidatedGen = q.datasetGenerationId.isNotBlank() && q.datasetGenerationId in invalidatedGens
                val matchesActiveLocal = q.recordUuid.isNotBlank() &&
                    q.recordUuid in activeUuids &&
                    q.recordUuid !in tombstoneUuids &&
                    !isInvalidatedGen

                if (matchesActiveLocal && q.classification != "DUPLICATE_FILE") {
                    dao.updateDriveCloudQuarantineStatus(q.driveFileId, "AUTO_RECOVERED", now)
                    recoveredCount++
                    continue
                }

                if (autoPurgeVerifiedTombstones &&
                    token != null &&
                    q.ownershipVerified &&
                    (q.classification == "DELETED_IN_LOCAL_TOMBSTONE" || q.classification == "DUPLICATE_FILE")
                ) {
                    val delRes = driveService.deleteFileStrictAndVerify(token, q.driveFileId, q.fileName)
                    if (delRes.isSuccess) {
                        dao.updateDriveCloudQuarantineStatus(q.driveFileId, "AUTO_PURGED", now)
                        recoveredCount++
                    }
                }
            }

            if (recoveredCount > 0) {
                dao.insertSyncTechnicalLog(
                    SyncTechnicalLogEntity(
                        operationTimeMillis = now,
                        operationTimeDisplay = formatDisplayTime(now),
                        operationType = "AUTO_RECOVER_QUARANTINE",
                        datasetGenerationId = generationManager.getCurrentGenerationId(),
                        localStatus = "RECOVERED=$recoveredCount",
                        cloudStatus = "SELF_HEALED",
                        errorCode = "",
                        errorMessage = "Pemulihan otomatis karantina menyelesaikan $recoveredCount item tanpa intervensi manual."
                    )
                )
            }
            Result.success(recoveredCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Automatic Cleanup for `ORPHAN_CLOUD`, Duplicate Cloud Files, and Stale/Quarantine States:
     * 1. Deletes every `.json` file in Google Drive active folders (`TRANSACTIONS`, `CUSTOMERS`, `UNITS`, `PAYMENTS`)
     *    that has NO matching active `uuid` (or deterministic record key) in local SQLite (`ORPHAN_CLOUD`).
     * 2. Deduplicates any duplicate cloud files (same deterministic filename OR same `record_uuid`),
     *    retaining only the single canonical file mapped in SQLite or with highest revision.
     * 3. Clears `STALE` / `STALE_OPERATION` queue items, orphan `drive_cloud_file_map` rows,
     *    and resolved quarantine records so `Duplikat` and `Stale` reset strictly to 0.
     */
    suspend fun cleanOrphanCloudAndReconcile(): Result<OrphanCleanupAndResyncReport> = withContext(Dispatchers.IO) {
        try {
            val token = driveService.resolveValidAccessToken().getOrElse { return@withContext Result.failure(it) }
            val (activeObjects, docObjects) = driveService.listMirrorActiveAndDocObjects(token)
                .getOrElse { return@withContext Result.failure(it) }

            val localCustomers = dao.getAllCustomersOnce()
            val localUnits = dao.getAllUnitsOnce()
            val localTx = dao.getAllTransactionsOnce()
            val localPayments = dao.getAllPaymentProofsOnce()
            val mappings = dao.getAllDriveCloudFileMappingsOnce()
            val now = System.currentTimeMillis()
            val nowDisplay = formatDisplayTime(now)

            // Record all local invoice numbers into monotonic floor before cleanup
            localTx.forEach { if (it.nomorInvoice.isNotBlank()) generationManager.recordObservedInvoiceNumber(it.nomorInvoice) }

            val activeUuidSet = buildSet {
                localCustomers.forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
                localUnits.forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
                localTx.forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
                localPayments.forEach { if (it.uuid.isNotBlank()) add(it.uuid) }
            }
            val expectedNames = buildSet {
                localCustomers.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER, it.id, it.uuid)) }
                localUnits.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_UNIT, it.id, it.uuid)) }
                localTx.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION, it.id, it.uuid)) }
                localPayments.forEach { add(MirrorOutboxCodec.buildDeterministicRecordFileName(GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT, it.id, it.uuid)) }
            }
            val activeKeys = buildSet {
                localCustomers.forEach { add("CUSTOMER:${it.id}") }
                localUnits.forEach { add("UNIT:${it.id}") }
                localTx.forEach { add("TRANSACTION:${it.id}") }
                localPayments.forEach { add("PAYMENT:${it.id}") }
            }
            val mappedIdByName = mappings
                .filter { it.fileName.isNotBlank() && it.driveFileId.isNotBlank() }
                .associate { it.fileName to it.driveFileId }
            val mappedIdByUuid = mappings
                .filter { it.recordUuid.isNotBlank() && it.driveFileId.isNotBlank() }
                .associate { it.recordUuid to it.driveFileId }

            val operationalJsonFiles = activeObjects.filter { obj ->
                !obj.isFolder &&
                    !obj.item.trashed &&
                    obj.item.name.endsWith(".json", ignoreCase = true) &&
                    obj.item.name !in ignoredMetaFiles
            }.map { it.item }

            val deletedIds = mutableSetOf<String>()
            var deletedOrphans = 0
            var deletedDuplicates = 0

            // Step 1: Delete every ORPHAN_CLOUD `.json` file that has no matching active local SQLite UUID/key
            for (cf in operationalJsonFiles) {
                val matchesByUuid = cf.recordUuid.isNotBlank() && cf.recordUuid in activeUuidSet
                val matchesByName = cf.name in expectedNames
                if (!matchesByUuid && !matchesByName) {
                    val activeToken = driveService.currentActiveAccessToken(token)
                    // Before deleting an orphan transaction JSON, inspect its invoice number so sequence never regresses to 0001
                    if (cf.name.startsWith("TRANSACTION_") || cf.entityType == GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION) {
                        runCatching {
                            val raw = driveService.driveClient.downloadFileText(activeToken, cf.id).getOrNull()
                            if (!raw.isNullOrBlank()) {
                                val inv = JSONObject(raw).optString("nomorInvoice", "")
                                if (inv.isNotBlank()) generationManager.recordObservedInvoiceNumber(inv)
                            }
                        }
                    }
                    driveService.deleteFileStrictAndVerify(driveService.currentActiveAccessToken(activeToken), cf.id, cf.name)
                        .getOrElse { return@withContext Result.failure(it) }
                    deletedIds.add(cf.id)
                    deletedOrphans++
                }
            }

            // Step 2: Deduplicate remaining active operational files by deterministic filename AND by record_uuid
            val remainingOps = operationalJsonFiles.filter { it.id !in deletedIds }
            val byNameGroups = remainingOps.groupBy { it.name }.filter { it.value.size > 1 }
            for ((name, group) in byNameGroups) {
                val preferredId = mappedIdByName[name]
                val keepFile = group.firstOrNull { it.id == preferredId }
                    ?: group.maxByOrNull { it.remoteVersion }
                    ?: group.first()
                for (dup in group) {
                    if (dup.id != keepFile.id && dup.id !in deletedIds) {
                        driveService.deleteFileStrictAndVerify(driveService.currentActiveAccessToken(token), dup.id, dup.name)
                            .getOrElse { return@withContext Result.failure(it) }
                        deletedIds.add(dup.id)
                        deletedDuplicates++
                    }
                }
            }

            val remainingAfterNameDedup = remainingOps.filter { it.id !in deletedIds }
            val byUuidGroups = remainingAfterNameDedup
                .filter { it.recordUuid.isNotBlank() }
                .groupBy { it.recordUuid }
                .filter { it.value.size > 1 }
            for ((uuid, group) in byUuidGroups) {
                val preferredId = mappedIdByUuid[uuid]
                val keepFile = group.firstOrNull { it.id == preferredId }
                    ?: group.maxByOrNull { it.remoteVersion }
                    ?: group.first()
                for (dup in group) {
                    if (dup.id != keepFile.id && dup.id !in deletedIds) {
                        driveService.deleteFileStrictAndVerify(driveService.currentActiveAccessToken(token), dup.id, dup.name)
                            .getOrElse { return@withContext Result.failure(it) }
                        deletedIds.add(dup.id)
                        deletedDuplicates++
                    }
                }
            }

            // Also deduplicate duplicate doc files by exact filename
            val activeDocFiles = docObjects.filter { !it.isFolder && !it.item.trashed }.map { it.item }
            activeDocFiles.groupBy { it.name }.filter { it.value.size > 1 }.forEach { (name, group) ->
                val preferredId = mappedIdByName[name]
                val keepFile = group.firstOrNull { it.id == preferredId }
                    ?: group.maxByOrNull { it.remoteVersion }
                    ?: group.first()
                for (dup in group) {
                    if (dup.id != keepFile.id && dup.id !in deletedIds) {
                        val res = driveService.deleteFileStrictAndVerify(driveService.currentActiveAccessToken(token), dup.id, dup.name)
                        if (res.isSuccess) {
                            deletedIds.add(dup.id)
                            deletedDuplicates++
                        }
                    }
                }
            }

            // Step 3: Clear Stale queue rows & orphan mappings & purge verified quarantine entries
            var clearedStaleQueue = 0
            val allQueue = dao.getAllDriveLocalSyncQueueOnce()
            for (q in allQueue) {
                if (q.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_STALE_OPERATION ||
                    q.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_STALE
                ) {
                    dao.deleteDriveSyncQueueById(q.id)
                    clearedStaleQueue++
                }
            }

            var clearedOrphanMaps = 0
            val operationalCats = setOf(
                GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
                GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION,
                GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT
            )
            for (m in mappings) {
                if (m.recordCategory in operationalCats &&
                    m.localRecordKey !in activeKeys &&
                    (m.recordUuid.isBlank() || m.recordUuid !in activeUuidSet)
                ) {
                    dao.deleteDriveCloudFileMapByKey(m.localRecordKey)
                    clearedOrphanMaps++
                }
            }

            var clearedQuarantine = 0
            val activeQuarantine = dao.getActiveDriveCloudQuarantineOnce()
            for (q in activeQuarantine) {
                if (q.driveFileId in deletedIds) {
                    dao.updateDriveCloudQuarantineStatus(q.driveFileId, "PURGED", now)
                    clearedQuarantine++
                } else if (q.ownershipVerified) {
                    val delRes = driveService.deleteFileStrictAndVerify(driveService.currentActiveAccessToken(token), q.driveFileId, q.fileName)
                    if (delRes.isSuccess) {
                        dao.updateDriveCloudQuarantineStatus(q.driveFileId, "PURGED", now)
                        deletedIds.add(q.driveFileId)
                        clearedQuarantine++
                    }
                }
            }

            val summary = "Pembersihan selesai: $deletedOrphans file ORPHAN_CLOUD dihapus, $deletedDuplicates duplikat dibersihkan, $clearedStaleQueue status Stale direset."
            dao.insertSyncTechnicalLog(
                SyncTechnicalLogEntity(
                    operationTimeMillis = now,
                    operationTimeDisplay = nowDisplay,
                    operationType = "CLEAN_ORPHAN_AND_RECONCILE",
                    datasetGenerationId = generationManager.getCurrentGenerationId(),
                    localStatus = "STALE_CLEARED=$clearedStaleQueue,MAP_CLEARED=$clearedOrphanMaps",
                    cloudStatus = "ORPHAN_DELETED=$deletedOrphans,DUP_DELETED=$deletedDuplicates",
                    errorCode = "",
                    errorMessage = summary
                )
            )

            Result.success(
                OrphanCleanupAndResyncReport(
                    deletedOrphanCloudFiles = deletedOrphans,
                    deletedDuplicateCloudFiles = deletedDuplicates,
                    clearedStaleQueueRows = clearedStaleQueue,
                    clearedQuarantineRows = clearedQuarantine,
                    clearedOrphanMappings = clearedOrphanMaps,
                    summaryMessage = summary
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
