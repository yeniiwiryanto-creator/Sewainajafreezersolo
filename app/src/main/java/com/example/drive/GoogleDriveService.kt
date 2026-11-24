package com.example.drive

import android.content.Context
import com.example.data.local.DriveCloudFileMapEntity
import com.example.data.local.DriveCloudManifestState
import com.example.data.local.DrivePhotoMetadataEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class DriveWipeOutcome(
    val deletedCount: Int,
    val failures: List<DriveDeleteFailure>,
    val fatalError: String? = null
)

/**
 * 2. GoogleDriveService
 * High-level Google Drive REST v3 service managing:
 * - Unified OAuth 2.0 token resolution & automatic single-flight 401 refresh wiring
 * - Folder hierarchy (`SEWAIN_AJA_FREEZER_SOC/{ACTIVE_DATA,DOCUMENTATION}`)
 * - Searching & matching by `appProperties` (`record_uuid`, `appId`, etc.)
 * - Metadata reading & `appProperties` updates by exact Google Drive `fileId`
 * - Idempotent PATCH/POST uploads (only falling back to POST on HTTP 404, NEVER on 401/5xx)
 *   + Read-after-Write SHA-256 + UUID + revision verification
 * - Strict delete + 404/trashed verification + UUID/folder zero-copy verification
 * - Cloud `manifest.json` read/write and Factory Reset Drive wipe
 */
class GoogleDriveService(
    private val context: Context,
    driveClient: GoogleDriveRestClient = GoogleDriveRestClient(),
    var accessTokenOverride: String? = null
) {
    private val folderIdCache = ConcurrentHashMap<String, String>()

    @Volatile
    var lastEffectiveAccessToken: String = ""
        private set

    var driveClient: GoogleDriveRestClient = driveClient
        set(value) {
            field = value
            attachAuthRefreshInterceptor(value)
        }

    init {
        attachAuthRefreshInterceptor(this.driveClient)
    }

    private fun attachAuthRefreshInterceptor(client: GoogleDriveRestClient) {
        client.tokenRefreshHandler = { rejectedToken ->
            refreshAccessTokenAfterUnauthorized(rejectedToken)
        }
        client.onTokenRefreshed = { freshToken ->
            lastEffectiveAccessToken = freshToken
            if (!accessTokenOverride.isNullOrBlank()) {
                accessTokenOverride = freshToken
            }
        }
    }

    suspend fun resolveValidAccessTokenResult(): DriveTokenResult {
        accessTokenOverride?.trim()?.takeIf { it.isNotBlank() }?.let { overrideTok ->
            lastEffectiveAccessToken = overrideTok
            return DriveTokenResult.Valid(overrideTok, Long.MAX_VALUE)
        }
        val status = GoogleDriveAuthManager.getConnectionStatus(context)
        val result = GoogleDriveAuthManager.getValidAccessTokenResult(context, status.accountEmail)
        if (result is DriveTokenResult.Valid) {
            lastEffectiveAccessToken = result.accessToken
        }
        return result
    }

    suspend fun resolveValidAccessToken(): Result<String> {
        return when (val res = resolveValidAccessTokenResult()) {
            is DriveTokenResult.Valid -> Result.success(res.accessToken)
            is DriveTokenResult.RequiresReAuth -> Result.failure(
                DriveApiException(
                    httpCode = 401,
                    errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                    message = res.reason
                )
            )
            is DriveTokenResult.TransientError -> Result.failure(IllegalStateException(res.reason))
        }
    }

    suspend fun refreshAccessTokenAfterUnauthorized(rejectedToken: String): Result<String> {
        // When legacy tests pass an explicit static override token without installing a custom
        // OAuth transport, fail cleanly with FAILED_AUTH instead of calling Play Services.
        if (!accessTokenOverride.isNullOrBlank() &&
            GoogleDriveAuthManager.oauthTransport === PlayServicesDriveOAuthTransport
        ) {
            return Result.failure(
                DriveApiException(
                    httpCode = 401,
                    errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                    message = "HTTP 401 UNAUTHENTICATED — token override ditolak dan tidak ada transport refresh OAuth."
                )
            )
        }
        val status = GoogleDriveAuthManager.getConnectionStatus(context)
        return when (val refreshed = GoogleDriveAuthManager.invalidateAndRefreshAccessToken(
            context = context,
            rejectedToken = rejectedToken,
            accountEmail = status.accountEmail
        )) {
            is DriveTokenResult.Valid -> {
                lastEffectiveAccessToken = refreshed.accessToken
                Result.success(refreshed.accessToken)
            }
            is DriveTokenResult.RequiresReAuth -> Result.failure(
                DriveApiException(
                    httpCode = 401,
                    errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                    message = refreshed.reason
                )
            )
            is DriveTokenResult.TransientError -> Result.failure(IllegalStateException(refreshed.reason))
        }
    }

    fun currentActiveAccessToken(fallback: String): String {
        return lastEffectiveAccessToken.ifBlank { fallback }
    }

    fun clearFolderCache() {
        folderIdCache.clear()
    }

    fun currentServerReferenceTimeMillis(): Long = driveClient.currentServerReferenceTimeMillis()

    val lastServerTimeMillis: Long
        get() = driveClient.lastServerTimeMillis

    fun isPhotoCategory(category: String): Boolean {
        return category in setOf(
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_KTP,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_CUSTOMER,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_FREEZER,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_PAYMENT,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_AGREEMENT
        )
    }

    suspend fun ensureRootAppFolderId(accessToken: String): Result<String> {
        folderIdCache["ROOT"]?.let { return Result.success(it) }
        val res = driveClient.ensureNamedFolderId(
            accessToken = accessToken,
            folderName = GoogleDriveRestClient.BACKUP_FOLDER_NAME,
            parentFolderId = null
        )
        res.onSuccess { folderIdCache["ROOT"] = it }
        return res
    }

    suspend fun ensureNamedChildFolderId(
        accessToken: String,
        parentFolderId: String,
        folderName: String,
        cacheKey: String
    ): Result<String> {
        folderIdCache[cacheKey]?.let { return Result.success(it) }
        val res = driveClient.ensureNamedFolderId(
            accessToken = accessToken,
            folderName = folderName,
            parentFolderId = parentFolderId
        )
        res.onSuccess { folderIdCache[cacheKey] = it }
        return res
    }

    suspend fun ensureCategoryMirrorFolderId(
        accessToken: String,
        recordCategory: String
    ): Result<String> {
        val cacheKey = "CAT:$recordCategory"
        folderIdCache[cacheKey]?.let { return Result.success(it) }

        val rootId = ensureRootAppFolderId(accessToken).getOrElse { return Result.failure(it) }
        val tokenAfterRoot = currentActiveAccessToken(accessToken)
        val isPhoto = isPhotoCategory(recordCategory)
        val sectionName = if (isPhoto) {
            GoogleDriveMirrorSyncEngine.SUBFOLDER_DOCUMENTATION
        } else {
            GoogleDriveMirrorSyncEngine.SUBFOLDER_ACTIVE_DATA
        }
        val sectionId = ensureNamedChildFolderId(tokenAfterRoot, rootId, sectionName, "SEC:$sectionName")
            .getOrElse { return Result.failure(it) }
        val tokenAfterSec = currentActiveAccessToken(tokenAfterRoot)

        val subName = when (recordCategory) {
            GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_KTP,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_CUSTOMER -> GoogleDriveMirrorSyncEngine.SUBFOLDER_CUSTOMERS
            GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_FREEZER -> GoogleDriveMirrorSyncEngine.SUBFOLDER_UNITS
            GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION -> GoogleDriveMirrorSyncEngine.SUBFOLDER_TRANSACTIONS
            GoogleDriveMirrorSyncEngine.CATEGORY_PAYMENT,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_PAYMENT -> GoogleDriveMirrorSyncEngine.SUBFOLDER_PAYMENTS
            GoogleDriveMirrorSyncEngine.CATEGORY_SETTINGS -> GoogleDriveMirrorSyncEngine.SUBFOLDER_SETTINGS
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_AGREEMENT -> GoogleDriveMirrorSyncEngine.SUBFOLDER_AGREEMENTS
            else -> GoogleDriveMirrorSyncEngine.SUBFOLDER_CUSTOMERS
        }

        val folderRes = driveClient.ensureNamedFolderId(
            accessToken = tokenAfterSec,
            folderName = subName,
            parentFolderId = sectionId
        )
        folderRes.onSuccess { folderIdCache[cacheKey] = it }
        return folderRes
    }

    suspend fun getFileMetadataById(
        accessToken: String,
        fileId: String
    ): Result<DriveBackupFileItem?> = driveClient.getFileMetadataById(accessToken, fileId)

    suspend fun updateFileAppProperties(
        accessToken: String,
        fileId: String,
        appProperties: Map<String, String>,
        newName: String? = null,
        description: String? = null
    ): Result<DriveBackupFileItem> =
        driveClient.updateFileAppProperties(accessToken, fileId, appProperties, newName, description)

    suspend fun downloadFileToLocal(
        accessToken: String,
        fileId: String,
        destinationFile: File
    ): Result<File> = driveClient.downloadBackupZip(accessToken, fileId, destinationFile)

    suspend fun findFilesByRecordUuid(
        accessToken: String,
        recordUuid: String
    ): Result<List<DriveBackupFileItem>> = driveClient.findFilesByRecordUuid(accessToken, recordUuid)

    suspend fun findFilesByAppId(
        accessToken: String
    ): Result<List<DriveBackupFileItem>> = driveClient.listFilesByAppId(accessToken, MirrorOutboxCodec.APP_ID_VALUE)

    suspend fun findAllFilesInFolderByName(
        accessToken: String,
        folderId: String,
        fileName: String
    ): Result<List<DriveBackupFileItem>> = driveClient.findAllFilesInFolderByName(accessToken, folderId, fileName)

    suspend fun findActiveFilesMatchingUuidOrName(
        accessToken: String,
        folderIds: List<String>,
        recordUuid: String,
        deterministicNames: List<String> = emptyList()
    ): Result<List<DriveBackupFileItem>> =
        driveClient.findActiveFilesMatchingUuidOrName(accessToken, folderIds, recordUuid, deterministicNames)

    suspend fun uploadOrUpdateJsonRecordWithVerification(
        accessToken: String,
        folderId: String,
        existingTargetFileId: String,
        fileName: String,
        payloadJson: String,
        contentHash: String,
        recordUuid: String,
        revision: Long,
        description: String,
        appProperties: Map<String, String>
    ): Result<DriveBackupFileItem> {
        val uploadRes = if (existingTargetFileId.isNotBlank()) {
            val patchRes = driveClient.updateTextContentInPlace(
                accessToken = accessToken,
                fileId = existingTargetFileId,
                targetFileName = fileName,
                jsonOrTextContent = payloadJson,
                mimeType = "application/json",
                checksumSha256 = contentHash,
                description = description,
                customAppProperties = appProperties
            )
            if (patchRes.isSuccess) {
                patchRes
            } else {
                val err = patchRes.exceptionOrNull()
                // Only fall back from PATCH -> POST if the existing fileId is genuinely 404 Not Found.
                // Never mask 401/403/5xx with a blind POST that could create duplicates or hide auth errors.
                if (DriveApiException.isNotFoundFailure(err)) {
                    driveClient.uploadTextContentToFolderId(
                        accessToken = currentActiveAccessToken(accessToken),
                        parentFolderId = folderId,
                        targetFileName = fileName,
                        jsonOrTextContent = payloadJson,
                        mimeType = "application/json",
                        checksumSha256 = contentHash,
                        description = description,
                        customAppProperties = appProperties
                    )
                } else {
                    patchRes
                }
            }
        } else {
            driveClient.uploadTextContentToFolderId(
                accessToken = accessToken,
                parentFolderId = folderId,
                targetFileName = fileName,
                jsonOrTextContent = payloadJson,
                mimeType = "application/json",
                checksumSha256 = contentHash,
                description = description,
                customAppProperties = appProperties
            )
        }
        val uploaded = uploadRes.getOrElse { return Result.failure(it) }
        driveClient.verifyUploadedJsonContentStrict(
            accessToken = currentActiveAccessToken(accessToken),
            fileId = uploaded.id,
            expectedSha256 = contentHash,
            expectedUuid = recordUuid,
            expectedVersion = revision,
            expectedDatasetGeneration = ""
        ).getOrElse { return Result.failure(it) }
        return Result.success(uploaded)
    }

    suspend fun uploadOrReplacePhotoWithVerification(
        accessToken: String,
        folderId: String,
        existingDriveFileId: String?,
        localFile: File,
        targetFileName: String,
        contentHash: String,
        description: String,
        appProperties: Map<String, String>
    ): Result<DriveBackupFileItem> {
        val uploadRes = if (!existingDriveFileId.isNullOrBlank()) {
            val patchRes = driveClient.updateFileInPlace(
                accessToken = accessToken,
                fileId = existingDriveFileId,
                localFile = localFile,
                targetFileName = targetFileName,
                mimeType = "image/jpeg",
                checksumSha256 = contentHash,
                description = description,
                customAppProperties = appProperties
            )
            if (patchRes.isSuccess) {
                patchRes
            } else {
                val err = patchRes.exceptionOrNull()
                if (DriveApiException.isNotFoundFailure(err)) {
                    driveClient.uploadFileToSpecificFolderId(
                        accessToken = currentActiveAccessToken(accessToken),
                        localFile = localFile,
                        targetFileName = targetFileName,
                        parentFolderId = folderId,
                        mimeType = "image/jpeg",
                        checksumSha256 = contentHash,
                        description = description,
                        customAppProperties = appProperties
                    )
                } else {
                    patchRes
                }
            }
        } else {
            driveClient.uploadFileToSpecificFolderId(
                accessToken = accessToken,
                localFile = localFile,
                targetFileName = targetFileName,
                parentFolderId = folderId,
                mimeType = "image/jpeg",
                checksumSha256 = contentHash,
                description = description,
                customAppProperties = appProperties
            )
        }
        val item = uploadRes.getOrElse { return Result.failure(it) }
        driveClient.verifyRemoteFileIntegrity(
            accessToken = currentActiveAccessToken(accessToken),
            fileId = item.id,
            expectedSha256 = contentHash
        ).getOrElse { return Result.failure(it) }
        return Result.success(item)
    }

    suspend fun deduplicateFiles(
        accessToken: String,
        candidates: List<DriveBackupFileItem>,
        keepFileId: String
    ) {
        candidates.filter { it.id != keepFileId && it.id.isNotBlank() }.forEach { dup ->
            driveClient.deleteFileStrict(currentActiveAccessToken(accessToken), dup.id, dup.name)
        }
    }

    suspend fun deleteFileStrictAndVerify(
        accessToken: String,
        fileId: String,
        fileName: String
    ): Result<Unit> {
        val failure = driveClient.deleteFileStrict(accessToken, fileId, fileName)
        if (failure != null) {
            if (failure.errorCode == DriveApiException.ERROR_CODE_FAILED_AUTH ||
                failure.errorCode == "HTTP_401"
            ) {
                return Result.failure(
                    DriveApiException(
                        httpCode = 401,
                        errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                        message = "Gagal menghapus file Drive ${failure.fileName} (${failure.fileId}) [${failure.errorCode}]: ${failure.errorMessage}"
                    )
                )
            }
            return Result.failure(
                IllegalStateException("Gagal menghapus file Drive ${failure.fileName} (${failure.fileId}) [${failure.errorCode}]: ${failure.errorMessage}")
            )
        }
        driveClient.verifyFileDeletedOrTrashedStrict(currentActiveAccessToken(accessToken), fileId).getOrElse { err ->
            if (DriveApiException.isAuthFailure(err)) {
                return Result.failure(
                    DriveApiException(
                        httpCode = 401,
                        errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                        message = "Verifikasi penghapusan Drive gagal autentikasi untuk $fileName ($fileId): ${err.message}",
                        cause = err
                    )
                )
            }
            return Result.failure(
                IllegalStateException("Verifikasi penghapusan Drive gagal untuk $fileName ($fileId): ${err.message}", err)
            )
        }
        return Result.success(Unit)
    }

    suspend fun listMirrorActiveAndDocObjects(
        accessToken: String
    ): Result<Pair<List<DriveRecursiveObject>, List<DriveRecursiveObject>>> {
        val rootId = ensureRootAppFolderId(accessToken).getOrElse { return Result.failure(it) }
        val tok1 = currentActiveAccessToken(accessToken)
        val activeDataId = ensureNamedChildFolderId(
            tok1,
            rootId,
            GoogleDriveMirrorSyncEngine.SUBFOLDER_ACTIVE_DATA,
            "CACHE_ACTIVE_DATA"
        ).getOrElse { return Result.failure(it) }
        val tok2 = currentActiveAccessToken(tok1)
        val docId = ensureNamedChildFolderId(
            tok2,
            rootId,
            GoogleDriveMirrorSyncEngine.SUBFOLDER_DOCUMENTATION,
            "CACHE_DOC_ROOT"
        ).getOrElse { return Result.failure(it) }
        val tok3 = currentActiveAccessToken(tok2)

        val activeObjects = driveClient.listAllObjectsRecursively(tok3, activeDataId)
            .getOrElse { return Result.failure(it) }
        val docObjects = driveClient.listAllObjectsRecursively(currentActiveAccessToken(tok3), docId)
            .getOrElse { return Result.failure(it) }
        return Result.success(activeObjects to docObjects)
    }

    suspend fun uploadManifest(
        accessToken: String,
        manifest: DriveCloudManifestState,
        existingManifestFileId: String = ""
    ): Result<Pair<DriveBackupFileItem, String>> {
        val activeRecordsArray = JSONArray().apply {
            manifest.activeRecords.forEach { rec ->
                put(
                    JSONObject().apply {
                        put("localRecordKey", rec.localRecordKey)
                        put("recordCategory", rec.recordCategory)
                        put("recordId", rec.recordId)
                        put("recordUuid", rec.recordUuid)
                        put("revision", rec.revision)
                        put("driveFileId", rec.driveFileId)
                        put("contentHash", rec.contentHash)
                        put("updatedAtMillis", rec.updatedAtMillis)
                        put("fileName", rec.fileName)
                    }
                )
            }
        }
        val tombstonesArray = JSONArray().apply {
            manifest.tombstones.forEach { t ->
                put(
                    JSONObject().apply {
                        put("localRecordKey", t.localRecordKey)
                        put("recordCategory", t.recordCategory)
                        put("recordId", t.recordId)
                        put("recordUuid", t.recordUuid)
                        put("deletedVersion", t.deletedVersion)
                        put("deletedAtMillis", t.deletedAtMillis)
                        put("uuidVerifiedGone", t.uuidVerifiedGone)
                    }
                )
            }
        }
        val manifestJson = JSONObject().apply {
            put("applicationId", manifest.applicationId)
            put("userId", manifest.userId)
            put("datasetId", manifest.datasetId)
            put("datasetGeneration", manifest.datasetGeneration)
            put("generation_id", manifest.datasetGeneration)
            put("databaseVersion", manifest.databaseVersion)
            put("schema_version", manifest.databaseVersion)
            put("lastSyncTimestamp", manifest.lastSyncTimestamp)
            put("lastResetTimestamp", manifest.lastResetTimestamp)
            put("resetCompleted", manifest.resetCompleted)
            put("totalCustomers", manifest.totalCustomers)
            put("totalUnits", manifest.totalUnits)
            put("totalTransactions", manifest.totalTransactions)
            put("totalPayments", manifest.totalPayments)
            put("totalPhotos", manifest.totalPhotos)
            put("syncVersion", manifest.syncVersion)
            put("datasetStatus", manifest.datasetStatus)
            put("activeRecords", activeRecordsArray)
            put("tombstones", tombstonesArray)
        }.toString(2)

        val contentHash = MirrorOutboxCodec.computeSha256(manifestJson)
        val rootFolderId = ensureRootAppFolderId(accessToken).getOrElse { return Result.failure(it) }
        val tok1 = currentActiveAccessToken(accessToken)
        val activeDataId = ensureNamedChildFolderId(
            tok1,
            rootFolderId,
            GoogleDriveMirrorSyncEngine.SUBFOLDER_ACTIVE_DATA,
            "CACHE_ACTIVE_DATA"
        ).getOrElse { return Result.failure(it) }
        val tok2 = currentActiveAccessToken(tok1)

        var manifestFileId = existingManifestFileId
        val foundManifestsRes = driveClient.findAllFilesInFolderByName(
            tok2,
            activeDataId,
            GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME
        )
        if (foundManifestsRes.isFailure && DriveApiException.isAuthFailure(foundManifestsRes.exceptionOrNull())) {
            return Result.failure(foundManifestsRes.exceptionOrNull()!!)
        }
        val foundManifests = foundManifestsRes.getOrDefault(emptyList())
        if (manifestFileId.isBlank() && foundManifests.isNotEmpty()) {
            manifestFileId = foundManifests.first().id
        }
        if (foundManifests.size > 1) {
            val keepId = manifestFileId.ifBlank { foundManifests.first().id }
            deduplicateFiles(currentActiveAccessToken(tok2), foundManifests, keepId)
            manifestFileId = keepId
        }

        val props = MirrorOutboxCodec.buildRecordAppProperties(
            recordUuid = "MANIFEST_${manifest.datasetGeneration}",
            entityType = GoogleDriveMirrorSyncEngine.CATEGORY_CLOUD_MANIFEST,
            generationId = manifest.datasetGeneration,
            revision = manifest.syncVersion,
            checksum = contentHash,
            syncTimestamp = manifest.lastSyncTimestamp,
            datasetId = manifest.datasetId,
            localRecordKey = GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_RECORD_KEY
        ) + mapOf(
            "syncVersion" to manifest.syncVersion.toString(),
            "datasetStatus" to manifest.datasetStatus
        )

        val tok3 = currentActiveAccessToken(tok2)
        val uploadRes = if (manifestFileId.isNotBlank()) {
            val patchRes = driveClient.updateTextContentInPlace(
                accessToken = tok3,
                fileId = manifestFileId,
                targetFileName = GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME,
                jsonOrTextContent = manifestJson,
                mimeType = "application/json",
                checksumSha256 = contentHash,
                description = "Cloud Manifest SEWAin Aja Freezer SOC",
                customAppProperties = props
            )
            if (patchRes.isSuccess) {
                patchRes
            } else if (DriveApiException.isNotFoundFailure(patchRes.exceptionOrNull())) {
                driveClient.uploadTextContentToFolderId(
                    accessToken = currentActiveAccessToken(tok3),
                    parentFolderId = activeDataId,
                    targetFileName = GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME,
                    jsonOrTextContent = manifestJson,
                    mimeType = "application/json",
                    checksumSha256 = contentHash,
                    description = "Cloud Manifest SEWAin Aja Freezer SOC",
                    customAppProperties = props
                )
            } else {
                patchRes
            }
        } else {
            driveClient.uploadTextContentToFolderId(
                accessToken = tok3,
                parentFolderId = activeDataId,
                targetFileName = GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME,
                jsonOrTextContent = manifestJson,
                mimeType = "application/json",
                checksumSha256 = contentHash,
                description = "Cloud Manifest SEWAin Aja Freezer SOC",
                customAppProperties = props
            )
        }
        val item = uploadRes.getOrElse { return Result.failure(it) }
        return Result.success(item to contentHash)
    }

    suspend fun wipeAllAppActiveObjectsStrict(
        accessToken: String,
        localMappings: List<DriveCloudFileMapEntity>,
        localPhotoMeta: List<DrivePhotoMetadataEntity>
    ): DriveWipeOutcome {
        var deletedCount = 0
        val failures = mutableListOf<DriveDeleteFailure>()
        val deletedIds = mutableSetOf<String>()

        suspend fun deleteObjectStrict(fileId: String, fileName: String) {
            if (fileId.isBlank() || fileId in deletedIds) return
            val err = driveClient.deleteFileStrict(currentActiveAccessToken(accessToken), fileId, fileName)
            if (err == null) {
                deletedIds.add(fileId)
                deletedCount++
            } else {
                failures.add(err)
            }
        }

        for (map in localMappings) {
            if (map.driveFileId.isNotBlank()) {
                deleteObjectStrict(map.driveFileId, map.fileName)
            }
        }
        for (meta in localPhotoMeta) {
            if (meta.driveFileId.isNotBlank()) {
                deleteObjectStrict(meta.driveFileId, meta.fileName)
            }
        }

        val preservedFolderIds = mutableSetOf<String>()
        val primaryRootFolders = driveClient.findFoldersByName(currentActiveAccessToken(accessToken), GoogleDriveRestClient.BACKUP_FOLDER_NAME)
            .getOrElse { err ->
                return DriveWipeOutcome(deletedCount, failures, "Gagal mencari folder root Google Drive: ${err.message}")
            }
        for (rootFolder in primaryRootFolders) {
            val topChildren = driveClient.listFilesInFolder(currentActiveAccessToken(accessToken), rootFolder.id)
                .getOrElse { err ->
                    return DriveWipeOutcome(deletedCount, failures, "Gagal membaca isi folder ${rootFolder.name}: ${err.message}")
                }
            for (child in topChildren) {
                val isBackupDir = child.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_HISTORICAL_BACKUP ||
                    child.name == GoogleDrivePhotoCenterManager.FOLDER_SECTION_BACKUP_DOKUMENTASI
                val isBackupZip = child.name.endsWith(".zip", ignoreCase = true)
                if (isBackupDir) {
                    preservedFolderIds.add(child.id)
                    continue
                }
                if (isBackupZip) continue

                if (child.mimeType == GoogleDriveRestClient.FOLDER_MIME) {
                    val nestedObjects = driveClient.listAllObjectsRecursively(currentActiveAccessToken(accessToken), child.id)
                        .getOrElse { err ->
                            return DriveWipeOutcome(deletedCount, failures, "Gagal menelusuri subfolder ${child.name}: ${err.message}")
                        }
                    for (nested in nestedObjects) {
                        if (!nested.item.name.endsWith(".zip", ignoreCase = true)) {
                            deleteObjectStrict(nested.item.id, nested.item.name)
                        }
                    }
                    if (nestedObjects.all { it.item.id in deletedIds }) {
                        deleteObjectStrict(child.id, child.name)
                    }
                } else {
                    deleteObjectStrict(child.id, child.name)
                }
            }
        }

        val legacyRootFolders = driveClient.findFoldersByName(currentActiveAccessToken(accessToken), GoogleDriveRestClient.DOC_ROOT_FOLDER_NAME)
            .getOrElse { err ->
                return DriveWipeOutcome(deletedCount, failures, "Gagal mencari folder legacy Google Drive: ${err.message}")
            }
        for (legacyRoot in legacyRootFolders) {
            val topChildren = driveClient.listFilesInFolder(currentActiveAccessToken(accessToken), legacyRoot.id)
                .getOrElse { err ->
                    return DriveWipeOutcome(deletedCount, failures, "Gagal membaca folder legacy ${legacyRoot.name}: ${err.message}")
                }
            for (child in topChildren) {
                val isBackupDir = child.name == GoogleDrivePhotoCenterManager.FOLDER_SECTION_BACKUP_DOKUMENTASI ||
                    child.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_HISTORICAL_BACKUP
                val isBackupZip = child.name.endsWith(".zip", ignoreCase = true)
                if (isBackupDir) {
                    preservedFolderIds.add(child.id)
                    continue
                }
                if (isBackupZip) continue

                if (child.mimeType == GoogleDriveRestClient.FOLDER_MIME) {
                    val nestedObjects = driveClient.listAllObjectsRecursively(currentActiveAccessToken(accessToken), child.id)
                        .getOrElse { err ->
                            return DriveWipeOutcome(deletedCount, failures, "Gagal menelusuri subfolder legacy ${child.name}: ${err.message}")
                        }
                    for (nested in nestedObjects) {
                        if (!nested.item.name.endsWith(".zip", ignoreCase = true)) {
                            deleteObjectStrict(nested.item.id, nested.item.name)
                        }
                    }
                    deleteObjectStrict(child.id, child.name)
                } else {
                    deleteObjectStrict(child.id, child.name)
                }
            }
        }

        val appOwnedFilesRes = findFilesByAppId(currentActiveAccessToken(accessToken))
        if (appOwnedFilesRes.isFailure && DriveApiException.isAuthFailure(appOwnedFilesRes.exceptionOrNull())) {
            return DriveWipeOutcome(deletedCount, failures, "Gagal membaca file milik aplikasi (401): ${appOwnedFilesRes.exceptionOrNull()?.message}")
        }
        val appOwnedFiles = appOwnedFilesRes.getOrDefault(emptyList())
        for (appFile in appOwnedFiles) {
            if (appFile.trashed || appFile.mimeType == GoogleDriveRestClient.FOLDER_MIME) continue
            if (appFile.name.endsWith(".zip", ignoreCase = true)) continue
            if (appFile.parentIds.any { it in preservedFolderIds }) continue
            if (appFile.id !in deletedIds) {
                deleteObjectStrict(appFile.id, appFile.name)
            }
        }

        return DriveWipeOutcome(deletedCount, failures, null)
    }
}
