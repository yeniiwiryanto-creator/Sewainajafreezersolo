package com.example.drive

import android.content.Context
import android.content.Intent
import com.example.data.local.LocalBackupEntity
import com.example.data.repository.FreezerRepository
import com.example.util.DateCurrencyUtils
import com.example.util.SecurityCryptoUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class DriveUploadProgressState(
    val isUploading: Boolean = false,
    val fileName: String = "",
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Int = 0,
    val statusText: String = "Siap"
)

/**
 * Unified Google Drive API Service Layer (`drive.file` scope) for SEWAin Aja Freezer SOC.
 *
 * Responsibilities:
 * 1. OAuth 2.0 Authorization using the owner's Google Account and `https://www.googleapis.com/auth/drive.file`
 *    scope with explicit user consent and zero hardcoded secrets/passwords.
 * 2. Provisioning and managing the `SEWAIN_AJA_FREEZER_SOC/` root backup folder on Google Drive.
 * 3. Streaming encrypted backup ZIP archives and compressed documentation files with live progress updates.
 * 4. Verifying SHA-256 checksum integrity before and after upload/download so local files are never
 *    pruned or replaced until the cloud copy is verified.
 * 5. Draining offline-queued local backups (`QUEUED_OFFLINE`) automatically once connectivity is available.
 */
class GoogleDriveBackupService(
    private val context: Context,
    private val restClient: GoogleDriveRestClient = GoogleDriveRestClient()
) {
    init {
        GoogleDriveAuthManager.attachAuthRefresh(context, restClient)
    }

    private val _uploadProgress = MutableStateFlow(DriveUploadProgressState())
    val uploadProgress: StateFlow<DriveUploadProgressState> = _uploadProgress.asStateFlow()

    fun getConnectionStatus(): DriveConnectionStatus {
        return GoogleDriveAuthManager.getConnectionStatus(context)
    }

    suspend fun authorizeWithDriveFileScope(
        preferredEmail: String? = null
    ): DriveAuthorizationOutcome {
        val emailToUse = preferredEmail?.takeIf { it.isNotBlank() }
            ?: runCatching { Firebase.auth.currentUser?.email }.getOrNull()
            ?: GoogleDriveAuthManager.getConnectionStatus(context).accountEmail.takeIf { it.isNotBlank() }
        return GoogleDriveAuthManager.authorizeDriveAccess(context, emailToUse)
    }

    fun handleConsentResult(
        resultCode: Int,
        data: Intent?,
        accountEmail: String = "",
        displayName: String = "",
        photoUrl: String = ""
    ): DriveAuthorizationOutcome {
        val resolvedEmail = accountEmail.ifBlank {
            runCatching { Firebase.auth.currentUser?.email.orEmpty() }.getOrDefault("")
        }
        val resolvedName = displayName.ifBlank {
            runCatching { Firebase.auth.currentUser?.displayName.orEmpty() }.getOrDefault("")
        }
        val resolvedPhoto = photoUrl.ifBlank {
            runCatching { Firebase.auth.currentUser?.photoUrl?.toString().orEmpty() }.getOrDefault("")
        }
        return GoogleDriveAuthManager.handleAuthorizationResultIntent(
            context = context,
            resultCode = resultCode,
            data = data,
            accountEmail = resolvedEmail,
            displayName = resolvedName,
            photoUrl = resolvedPhoto
        )
    }

    suspend fun resolveAccessToken(explicitAccessToken: String? = null): String? {
        if (!explicitAccessToken.isNullOrBlank()) return explicitAccessToken
        val email = runCatching { Firebase.auth.currentUser?.email }.getOrNull()
            ?: GoogleDriveAuthManager.getConnectionStatus(context).accountEmail
        return GoogleDriveAuthManager.getValidAccessTokenOrNull(context, email)
    }

    suspend fun uploadBackupArchiveWithVerification(
        zipFile: File,
        backupEntity: LocalBackupEntity,
        explicitAccessToken: String? = null,
        maxVersionsToKeep: Int = GoogleDriveRestClient.MAX_BACKUP_VERSIONS,
        verifyRemoteMetadataAfterUpload: Boolean = false
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        val token = resolveAccessToken(explicitAccessToken)
            ?: return@withContext Result.failure(
                IllegalStateException("Akses Google Drive (drive.file) belum diotorisasi.")
            )

        val localSha256 = backupEntity.checksumSha256.ifBlank {
            SecurityCryptoUtils.sha256Hex(zipFile)
        }

        _uploadProgress.value = DriveUploadProgressState(
            isUploading = true,
            fileName = zipFile.name,
            bytesTransferred = 0L,
            totalBytes = zipFile.length(),
            progressPercent = 1,
            statusText = "Mengunggah ${zipFile.name} ke folder ${GoogleDriveRestClient.BACKUP_FOLDER_NAME}/..."
        )

        val uploadRes = restClient.uploadBackupZip(
            accessToken = token,
            zipFile = zipFile,
            backupId = backupEntity.backupId,
            backupType = backupEntity.backupType,
            checksumSha256 = localSha256,
            customerCount = backupEntity.customerCount,
            unitCount = backupEntity.unitCount,
            transactionCount = backupEntity.transactionCount,
            paymentCount = backupEntity.paymentCount,
            photoCount = backupEntity.photoCount,
            onProgress = { sent, total ->
                val pct = ((sent * 100L) / total.coerceAtLeast(1L)).toInt().coerceIn(1, 99)
                _uploadProgress.value = DriveUploadProgressState(
                    isUploading = true,
                    fileName = zipFile.name,
                    bytesTransferred = sent,
                    totalBytes = total,
                    progressPercent = pct,
                    statusText = "Mengunggah ke Google Drive ($pct%)..."
                )
            }
        )

        uploadRes.fold(
            onSuccess = { uploadedItem ->
                if (verifyRemoteMetadataAfterUpload && uploadedItem.id.isNotBlank()) {
                    val verifyRes = restClient.verifyRemoteFileIntegrity(
                        accessToken = token,
                        fileId = uploadedItem.id,
                        expectedSha256 = localSha256
                    )
                    if (verifyRes.isFailure) {
                        _uploadProgress.value = DriveUploadProgressState(
                            isUploading = false,
                            fileName = zipFile.name,
                            statusText = "Verifikasi checksum Google Drive gagal"
                        )
                        return@withContext verifyRes
                    }
                }

                GoogleDriveAuthManager.updateLastDriveSyncAt(context, DateCurrencyUtils.nowDateTimeDisplay())
                restClient.pruneOldDriveBackupsAfterVerifiedUpload(token, maxVersionsToKeep)

                _uploadProgress.value = DriveUploadProgressState(
                    isUploading = false,
                    fileName = zipFile.name,
                    bytesTransferred = zipFile.length(),
                    totalBytes = zipFile.length(),
                    progressPercent = 100,
                    statusText = "Backup terverifikasi di Google Drive (${GoogleDriveRestClient.BACKUP_FOLDER_NAME}/)"
                )
                Result.success(uploadedItem)
            },
            onFailure = { err ->
                GoogleDriveAuthManager.setPendingOfflineUpload(context, true)
                _uploadProgress.value = DriveUploadProgressState(
                    isUploading = false,
                    fileName = zipFile.name,
                    progressPercent = 0,
                    statusText = "Upload tertunda: ${err.localizedMessage}"
                )
                Result.failure(err)
            }
        )
    }

    suspend fun uploadDocumentationFile(
        localFile: File,
        mimeType: String = "image/jpeg",
        categorySubfolder: String = "DOCUMENTS",
        description: String = "",
        explicitAccessToken: String? = null
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        val token = resolveAccessToken(explicitAccessToken)
            ?: return@withContext Result.failure(
                IllegalStateException("Akses Google Drive (drive.file) belum diotorisasi.")
            )
        val sha256 = SecurityCryptoUtils.sha256Hex(localFile)
        restClient.uploadFileToDriveFolder(
            accessToken = token,
            localFile = localFile,
            mimeType = mimeType,
            subfolderName = categorySubfolder,
            checksumSha256 = sha256,
            description = description
        )
    }

    suspend fun drainPendingOfflineBackups(
        repository: FreezerRepository,
        explicitAccessToken: String? = null,
        maxVersionsToKeep: Int = GoogleDriveRestClient.MAX_BACKUP_VERSIONS
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val token = resolveAccessToken(explicitAccessToken) ?: return@withContext 0 to 0
        val pendingBackups = repository.getAllLocalBackupsOnce()
            .filter { it.backupStatus == "QUEUED_OFFLINE" && it.driveFileId.isBlank() }
        if (pendingBackups.isEmpty()) {
            GoogleDriveAuthManager.setPendingOfflineUpload(context, false)
            return@withContext 0 to 0
        }

        var succeeded = 0
        var failed = 0
        for (entity in pendingBackups) {
            val zipFile = File(entity.backupPath)
            if (!zipFile.exists() || zipFile.length() <= 0L) {
                failed++
                continue
            }
            val res = uploadBackupArchiveWithVerification(
                zipFile = zipFile,
                backupEntity = entity,
                explicitAccessToken = token,
                maxVersionsToKeep = maxVersionsToKeep
            )
            res.onSuccess { driveItem ->
                repository.saveLocalBackup(
                    entity.copy(
                        backupStatus = "SUCCESS",
                        locationType = "LOKAL_DAN_DRIVE",
                        driveFileId = driveItem.id,
                        verificationStatus = "TERVERIFIKASI"
                    )
                )
                repository.logBackupAuditEvent(
                    actionType = "BACKUP_DRIVE",
                    status = "SUKSES",
                    fileName = entity.backupName,
                    checksum = entity.checksumSha256,
                    recordCount = entity.customerCount + entity.unitCount + entity.transactionCount,
                    detailMessage = "Antrean backup offline berhasil diunggah ke Google Drive (${driveItem.id})."
                )
                succeeded++
            }.onFailure {
                failed++
            }
        }
        GoogleDriveAuthManager.setPendingOfflineUpload(context, failed > 0)
        succeeded to failed
    }

    suspend fun listRemoteBackups(explicitAccessToken: String? = null): Result<List<DriveBackupFileItem>> {
        val token = resolveAccessToken(explicitAccessToken)
            ?: return Result.failure(IllegalStateException("Google Drive belum terhubung."))
        return restClient.listBackups(token)
    }

    suspend fun downloadAndVerifyBackup(
        fileId: String,
        destinationFile: File,
        expectedSha256: String = "",
        explicitAccessToken: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val token = resolveAccessToken(explicitAccessToken)
            ?: return@withContext Result.failure(IllegalStateException("Google Drive belum terhubung."))
        val dlRes = restClient.downloadBackupZip(token, fileId, destinationFile)
        dlRes.fold(
            onSuccess = { file ->
                if (expectedSha256.isNotBlank() && expectedSha256.length == 64) {
                    val actualSha = SecurityCryptoUtils.sha256Hex(file)
                    if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                        runCatching { file.delete() }
                        return@withContext Result.failure(
                            IllegalStateException("Checksum SHA-256 file hasil unduhan tidak cocok.")
                        )
                    }
                }
                Result.success(file)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun disconnectAndRevoke() {
        GoogleDriveAuthManager.disconnectDrive(context)
        _uploadProgress.value = DriveUploadProgressState()
    }
}
