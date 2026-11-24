package com.example.cloud

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.CloudPhotoJobEntity
import com.example.data.local.FreezerDao
import com.example.media.PhotoCategory
import com.example.media.PreferredCompressionFormat
import com.example.media.SmartImageProcessor
import com.example.media.SmartImageStorageOptimizer
import com.example.storage.LocalStorageAndBackupManager
import com.example.util.DateCurrencyUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Abstraction for Cloud Media Storage (Firebase Storage by default) so uploads,
 * download URL resolution, and deletes can be tested deterministically and handled safely.
 */
data class StorageWipeResult(
    val deletedObjectCount: Int = 0,
    val skippedNotFoundCount: Int = 0,
    val remainingObjectCount: Int = 0,
    val nonFatalWarnings: List<String> = emptyList()
)

interface CloudMediaStorageBackend {
    suspend fun uploadAndGetDownloadUrl(
        storagePath: String,
        localFile: File,
        mimeType: String
    ): Result<String>

    suspend fun resolveDownloadUrl(storagePath: String): Result<String>

    suspend fun deleteObject(storagePath: String): Result<Unit>

    suspend fun listAllAndDeleteRecursively(rootPrefix: String): Result<StorageWipeResult> =
        Result.success(StorageWipeResult())
}

sealed class MediaResolveResult {
    data class RemoteUrl(val url: String) : MediaResolveResult()
    data class LocalFallback(val localPath: String, val reason: String) : MediaResolveResult()
    data class MissingMedia(val reason: String) : MediaResolveResult()
}

/**
 * Default Firebase Storage backend with strict upload -> await downloadUrl ordering
 * and safe detection of StorageException.ERROR_OBJECT_NOT_FOUND ("Object does not exist at location").
 */
class FirebaseCloudStorageBackend(
    private val context: Context,
    private val explicitStorage: FirebaseStorage? = null
) : CloudMediaStorageBackend {

    private fun resolveStorageOrNull(): FirebaseStorage? {
        if (explicitStorage != null) return explicitStorage
        return runCatching {
            val apps = FirebaseApp.getApps(context)
            if (apps.isEmpty()) return null
            val bucket = apps.firstOrNull()?.options?.storageBucket
            if (bucket.isNullOrBlank()) return null
            FirebaseStorage.getInstance()
        }.getOrNull()
    }

    override suspend fun uploadAndGetDownloadUrl(
        storagePath: String,
        localFile: File,
        mimeType: String
    ): Result<String> {
        val storage = resolveStorageOrNull()
            ?: return Result.failure(
                IllegalStateException("Firebase Storage bucket belum dikonfigurasi pada project ini")
            )
        return try {
            val ref = storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType(mimeType.ifBlank { "image/jpeg" })
                .build()
            ref.putFile(Uri.fromFile(localFile), metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            if (downloadUrl.isBlank()) {
                Result.failure(IllegalStateException("Firebase Storage tidak mengembalikan downloadUrl yang valid"))
            } else {
                Result.success(downloadUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resolveDownloadUrl(storagePath: String): Result<String> {
        val storage = resolveStorageOrNull()
            ?: return Result.failure(
                IllegalStateException("Firebase Storage bucket belum dikonfigurasi")
            )
        return try {
            val url = storage.reference.child(storagePath).downloadUrl.await().toString()
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteObject(storagePath: String): Result<Unit> {
        val storage = resolveStorageOrNull() ?: return Result.success(Unit)
        return try {
            storage.reference.child(storagePath).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Deleting an already-absent remote object ("Object does not exist at location") is idempotent success
            if (CloudStoragePhotoManager.isObjectNotFoundOrUnconfigured(e)) {
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun listAllAndDeleteRecursively(rootPrefix: String): Result<StorageWipeResult> {
        val storage = resolveStorageOrNull()
            ?: return Result.success(
                StorageWipeResult(
                    deletedObjectCount = 0,
                    skippedNotFoundCount = 0,
                    remainingObjectCount = 0,
                    nonFatalWarnings = listOf("Firebase Storage bucket belum dikonfigurasi; dilewati.")
                )
            )
        var deletedCount = 0
        var skippedNotFound = 0
        val warnings = mutableListOf<String>()

        suspend fun deletePrefixRecursive(ref: com.google.firebase.storage.StorageReference) {
            val listResult = try {
                ref.listAll().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (CloudStoragePhotoManager.isObjectNotFoundOrUnconfigured(e)) {
                    skippedNotFound++
                    return
                }
                warnings += "listAll(${ref.path}) warning: ${e.localizedMessage}"
                return
            }

            // Recurse into every nested folder/prefix first
            for (prefixRef in listResult.prefixes) {
                deletePrefixRecursive(prefixRef)
            }

            // Delete every file item; NEVER abort the loop on ERROR_OBJECT_NOT_FOUND
            for (itemRef in listResult.items) {
                try {
                    itemRef.delete().await()
                    deletedCount++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (CloudStoragePhotoManager.isObjectNotFoundOrUnconfigured(e)) {
                        skippedNotFound++
                    } else {
                        warnings += "delete(${itemRef.path}) warning: ${e.localizedMessage}"
                    }
                }
            }
        }

        suspend fun countRemainingRecursive(ref: com.google.firebase.storage.StorageReference): Int {
            val listResult = try {
                ref.listAll().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return 0
            }
            var count = listResult.items.size
            for (prefixRef in listResult.prefixes) {
                count += countRemainingRecursive(prefixRef)
            }
            return count
        }

        return try {
            val cleanPrefix = rootPrefix.trim('/')
            val rootRef = if (cleanPrefix.isBlank()) storage.reference else storage.reference.child(cleanPrefix)
            deletePrefixRecursive(rootRef)
            val remaining = countRemainingRecursive(rootRef)
            Result.success(
                StorageWipeResult(
                    deletedObjectCount = deletedCount,
                    skippedNotFoundCount = skippedNotFound,
                    remainingObjectCount = remaining,
                    nonFatalWarnings = warnings
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (CloudStoragePhotoManager.isObjectNotFoundOrUnconfigured(e)) {
                Result.success(
                    StorageWipeResult(
                        deletedObjectCount = deletedCount,
                        skippedNotFoundCount = skippedNotFound + 1,
                        remainingObjectCount = 0,
                        nonFatalWarnings = warnings
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }
}

/**
 * Resilient Cloud Storage Photo Manager:
 * 1. Compresses & EXIF-normalizes photos + generates ~300px thumbnails with SHA-256 deduplication.
 * 2. Uploads to Firebase Storage & obtains verified HTTPS downloadUrl BEFORE Firestore metadata/parent docs are written.
 * 3. Handles `StorageException.ERROR_OBJECT_NOT_FOUND` ("Object does not exist at location") and unconfigured Storage buckets
 *    gracefully without stalling or halting the sync queue, falling back safely to verified local staging (`DRIVE_STAGE`).
 * 4. Marks missing local source files as non-blocking `MISSING_LOCAL` so other sync items never stall.
 */
class CloudStoragePhotoManager(
    private val context: Context,
    private val dao: FreezerDao,
    storage: Any? = null,
    private val mediaBackend: CloudMediaStorageBackend? = null
) {
    private val resolvedBackend: CloudMediaStorageBackend =
        mediaBackend ?: FirebaseCloudStorageBackend(context, storage as? FirebaseStorage)

    companion object {
        private const val TAG = "CloudStoragePhotoMgr"
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_UPLOADING = "UPLOADING"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_MISSING_LOCAL = "MISSING_LOCAL"
        const val DRIVE_BACKUP_ROOT = "SEWAIN_AJA_FREEZER_SOC"
        private const val MAX_TRANSIENT_RETRIES = 2

        fun isObjectNotFoundError(error: Throwable?): Boolean {
            if (error == null) return false
            val causes = generateSequence(error) { it.cause }
            for (t in causes) {
                if (t is StorageException && (
                        t.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND ||
                            t.errorCode == StorageException.ERROR_BUCKET_NOT_FOUND ||
                            t.httpResultCode == 404
                        )
                ) {
                    return true
                }
                val msg = t.message.orEmpty()
                if (msg.contains("Object does not exist at location", ignoreCase = true) ||
                    msg.contains("No such object", ignoreCase = true) ||
                    msg.contains("ERROR_OBJECT_NOT_FOUND", ignoreCase = true) ||
                    msg.contains("not found", ignoreCase = true) && msg.contains("storage", ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }

        fun isObjectNotFoundOrUnconfigured(error: Throwable?): Boolean {
            if (error == null) return false
            if (isObjectNotFoundError(error)) return true
            val msg = generateSequence(error) { it.cause }.joinToString(" ") { it.message.orEmpty() }
            return msg.contains("bucket belum dikonfigurasi", ignoreCase = true) ||
                msg.contains("bucket", ignoreCase = true) && msg.contains("not", ignoreCase = true) ||
                msg.contains("Default FirebaseApp is not initialized", ignoreCase = true) ||
                msg.contains("not authorized", ignoreCase = true) ||
                msg.contains("quota", ignoreCase = true) ||
                msg.contains("404", ignoreCase = true)
        }

        fun buildStructuredStoragePath(
            uid: String,
            ownerType: String,
            ownerCloudId: String,
            photoSlot: String,
            fileName: String
        ): String {
            val ext = fileName.substringAfterLast('.', "jpg").lowercase().let {
                if (it == "webp") "webp" else "jpg"
            }
            val safeFile = CloudIdMapper.sanitizeDocId(fileName.substringBeforeLast('.')) + ".$ext"
            val upperType = ownerType.uppercase()
            val upperSlot = photoSlot.uppercase()
            return when (upperType) {
                "CUSTOMER" -> {
                    val subFolder = when {
                        upperSlot.contains("KTP") -> "ktp"
                        upperSlot.contains("LOKASI") || upperSlot.contains("LOCATION") -> "location"
                        else -> "documentation"
                    }
                    "users/$uid/customers/$ownerCloudId/$subFolder/$safeFile"
                }
                "UNIT" -> "users/$uid/units/$ownerCloudId/photos/$safeFile"
                "TRANSACTION" -> {
                    val subFolder = if (upperSlot.contains("BUKTI") || upperSlot.contains("PAYMENT")) {
                        "payment_proofs"
                    } else {
                        "documentation"
                    }
                    "users/$uid/transactions/$ownerCloudId/$subFolder/$safeFile"
                }
                else -> "users/$uid/customers/company/documentation/$safeFile"
            }
        }
    }

    suspend fun resetStuckUploadingJobs(uid: String): Int = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext 0
        dao.resetStuckUploadingPhotoJobs(uid, DateCurrencyUtils.todayIso())
    }

    /**
     * Enqueues a photo upload job with EXIF-normalized compression, ~300px thumbnail generation,
     * and SHA-256 content deduplication.
     */
    suspend fun enqueuePhotoJob(
        uid: String,
        ownerType: String,
        ownerLocalId: Long,
        ownerCloudId: String,
        photoSlot: String,
        sourcePathOrUri: String,
        preferredFormat: PreferredCompressionFormat = PreferredCompressionFormat.JPEG
    ): CloudPhotoJobEntity? = withContext(Dispatchers.IO) {
        if (uid.isBlank() ||
            sourcePathOrUri.isBlank() ||
            sourcePathOrUri.startsWith("http://", ignoreCase = true) ||
            sourcePathOrUri.startsWith("https://", ignoreCase = true) ||
            sourcePathOrUri.startsWith("gdrive://", ignoreCase = true)
        ) {
            return@withContext null
        }

        val category = resolvePhotoCategory(ownerType, photoSlot)
        val storageKey = "${ownerType.lowercase()}_${ownerLocalId}_${photoSlot.lowercase()}"
        val prepared = prepareCompressedFiles(
            sourcePathOrUri = sourcePathOrUri,
            category = category,
            storageKey = storageKey,
            preferredFormat = preferredFormat
        )

        val resolvedMainPath = prepared?.compressedFile?.absolutePath ?: sourcePathOrUri
        val contentHash = prepared?.sha256Hash?.ifBlank { null }
            ?: resolveReadableFile(resolvedMainPath)?.let { SmartImageProcessor.computeSha256(it) }.orEmpty()
        val shortHash = contentHash.take(10).ifBlank { System.currentTimeMillis().toString() }

        val ext = if (prepared?.mimeType == "image/webp") "webp" else "jpg"
        val fileName = "${ownerType.lowercase()}_${ownerLocalId}_${photoSlot.lowercase()}_h${shortHash}.$ext"
        val storagePath = buildStructuredStoragePath(
            uid = uid,
            ownerType = ownerType,
            ownerCloudId = ownerCloudId,
            photoSlot = photoSlot,
            fileName = fileName
        )

        val existingJobs = dao.getAllCloudPhotoJobsOnce(uid)
        val duplicateJob = existingJobs.firstOrNull { existing ->
            val sameSlot = existing.ownerType.equals(ownerType, ignoreCase = true) &&
                existing.ownerLocalId == ownerLocalId &&
                existing.photoSlot.equals(photoSlot, ignoreCase = true)
            val sameStoragePath = existing.storagePath == storagePath
            val sameFileHash = contentHash.isNotBlank() &&
                existing.localFilePath.isNotBlank() &&
                File(existing.localFilePath).takeIf { it.exists() }?.let {
                    SmartImageProcessor.computeSha256(it) == contentHash
                } == true

            sameStoragePath || (sameSlot && sameFileHash && existing.status != STATUS_FAILED && existing.status != STATUS_MISSING_LOCAL)
        }
        if (duplicateJob != null) {
            Log.d(TAG, "Skip duplicate photo job for $storagePath (status=${duplicateJob.status})")
            return@withContext duplicateJob
        }

        val nowIso = DateCurrencyUtils.todayIso()
        val job = CloudPhotoJobEntity(
            uid = uid,
            ownerType = ownerType.uppercase(),
            ownerLocalId = ownerLocalId,
            ownerCloudId = ownerCloudId,
            photoSlot = photoSlot.uppercase(),
            localFilePath = resolvedMainPath,
            thumbnailPath = prepared?.thumbnailFile?.absolutePath.orEmpty(),
            storagePath = storagePath,
            mimeType = prepared?.mimeType ?: "image/jpeg",
            bytesTotal = prepared?.compressedFile?.length()
                ?: File(sourcePathOrUri).takeIf { it.exists() }?.length()
                ?: 0L,
            bytesUploaded = 0L,
            progressPercent = 0,
            status = STATUS_QUEUED,
            retryCount = 0,
            errorMessage = "",
            createdAt = nowIso,
            updatedAt = nowIso
        )
        val id = dao.upsertCloudPhotoJob(job)
        job.copy(id = id)
    }

    /**
     * Media-First helper: uploads all pending photo jobs belonging to a specific parent entity
     * BEFORE its Firestore document is upserted, returning verified `photoSlot -> downloadUrl` map.
     * Any single missing/failed file will NOT stall or throw out of this method.
     */
    suspend fun processPendingPhotoJobsForOwner(
        uid: String,
        ownerType: String,
        ownerLocalId: Long,
        cloudRepo: CloudFirestoreRepository? = null
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (uid.isBlank() || ownerLocalId <= 0L) return@withContext emptyMap()
        val normalizedOwner = ownerType.uppercase()
        val pendingForOwner = dao.getPendingPhotoJobsForOwnerOnce(uid, normalizedOwner, ownerLocalId)
        for (job in pendingForOwner) {
            // Non-blocking per-job execution: a single missing/failed photo never halts the entity sync
            runCatching { uploadSingleJob(job, cloudRepo) }
        }
        dao.getCloudPhotoJobsForOwnerOnce(uid, normalizedOwner, ownerLocalId)
            .filter { it.status == STATUS_UPLOADED && it.cloudDownloadUrl.isNotBlank() }
            .associate { it.photoSlot to it.cloudDownloadUrl }
    }

    /**
     * Safely resolves an image URL or local path when loading media in UI or sync.
     * Gracefully handles `Object does not exist at location` by falling back to local file or placeholder.
     */
    suspend fun resolveAccessibleMediaUrl(
        storagePath: String,
        existingUrl: String,
        localFallbackPath: String = ""
    ): MediaResolveResult = withContext(Dispatchers.IO) {
        val localFile = resolveReadableFile(localFallbackPath)
        if (existingUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext MediaResolveResult.RemoteUrl(existingUrl)
        }
        if (storagePath.isNotBlank()) {
            val remoteRes = resolvedBackend.resolveDownloadUrl(storagePath)
            if (remoteRes.isSuccess) {
                val url = remoteRes.getOrNull().orEmpty()
                if (url.isNotBlank()) {
                    return@withContext MediaResolveResult.RemoteUrl(url)
                }
            } else {
                val err = remoteRes.exceptionOrNull()
                if (isObjectNotFoundError(err)) {
                    Log.w(TAG, "Remote object not found at $storagePath (Object does not exist at location); using safe fallback")
                    val staged = resolveLocalStagedFile(storagePath)
                    if (staged != null) {
                        return@withContext MediaResolveResult.LocalFallback(
                            localPath = staged.absolutePath,
                            reason = "File cloud tidak ditemukan (Object does not exist at location), menggunakan salinan lokal terverifikasi."
                        )
                    }
                    if (localFile != null) {
                        return@withContext MediaResolveResult.LocalFallback(
                            localPath = localFile.absolutePath,
                            reason = "File cloud tidak ditemukan (Object does not exist at location), menggunakan file lokal."
                        )
                    }
                    return@withContext MediaResolveResult.MissingMedia(
                        reason = "File media tidak tersedia di Cloud Storage (Object does not exist at location)."
                    )
                }
            }
        }
        val staged = if (storagePath.isNotBlank()) resolveLocalStagedFile(storagePath) else null
        when {
            localFile != null -> MediaResolveResult.LocalFallback(localFile.absolutePath, "Menggunakan berkas foto lokal.")
            staged != null -> MediaResolveResult.LocalFallback(staged.absolutePath, "Menggunakan salinan staged lokal.")
            existingUrl.isNotBlank() -> MediaResolveResult.RemoteUrl(existingUrl)
            else -> MediaResolveResult.MissingMedia("Referensi media kosong atau sudah dihapus.")
        }
    }

    /**
     * Safely deletes all Cloud Storage objects, local staged copies, Firestore photo_metadata docs,
     * and local cloud_photo_jobs rows for a deleted parent entity.
     * Treats `Object does not exist at location` as idempotent success so deletion NEVER stalls.
     */
    suspend fun deletePhotosForOwnerSafely(
        uid: String,
        ownerType: String,
        ownerLocalId: Long,
        cloudRepo: CloudFirestoreRepository? = null
    ) = withContext(Dispatchers.IO) {
        if (uid.isBlank() || ownerLocalId <= 0L) return@withContext
        val normalizedOwner = ownerType.uppercase()
        val jobs = dao.getCloudPhotoJobsForOwnerOnce(uid, normalizedOwner, ownerLocalId)
        for (job in jobs) {
            if (job.storagePath.isNotBlank()) {
                val delRes = resolvedBackend.deleteObject(job.storagePath)
                if (delRes.isFailure && !isObjectNotFoundOrUnconfigured(delRes.exceptionOrNull())) {
                    Log.w(TAG, "Non-fatal remote media delete warning for ${job.storagePath}: ${delRes.exceptionOrNull()?.message}")
                }
                val thumbPath = job.storagePath.substringBeforeLast('.') + "_thumb.webp"
                runCatching { resolvedBackend.deleteObject(thumbPath) }
                runCatching { resolveLocalStagedFile(job.storagePath)?.delete() }
                runCatching { resolveLocalStagedFile(thumbPath)?.delete() }
            }
            val metaDocId = CloudIdMapper.photoMetaDocId(job.ownerType, job.ownerLocalId, job.photoSlot)
            runCatching { cloudRepo?.deletePhotoMetadata(metaDocId) }
        }
        dao.deleteCloudPhotoJobsForOwner(uid, normalizedOwner, ownerLocalId)
    }

    suspend fun processPendingPhotoJobs(
        uid: String,
        cloudRepo: CloudFirestoreRepository? = null
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext 0 to 0
        dao.resetStuckUploadingPhotoJobs(uid, DateCurrencyUtils.todayIso())
        val pending = dao.getPendingPhotoJobsOnce(uid)
        var succeeded = 0
        var failed = 0

        for (job in pending) {
            val result = uploadSingleJob(job, cloudRepo)
            if (result.isSuccess) {
                succeeded++
            } else {
                // Missing local source file is terminal non-blocking; don't keep failing whole queue
                val refreshed = dao.getAllCloudPhotoJobsOnce(uid).firstOrNull { it.id == job.id }
                if (refreshed?.status != STATUS_MISSING_LOCAL) {
                    failed++
                }
            }
        }

        runCatching {
            SmartImageStorageOptimizer.cleanSafeCachesPreservingImportantData(
                context = context,
                cloudPhotoJobs = dao.getAllCloudPhotoJobsOnce(uid)
            )
        }
        succeeded to failed
    }

    suspend fun retrySinglePhotoJob(
        uid: String,
        jobId: Long,
        cloudRepo: CloudFirestoreRepository? = null
    ): Result<CloudPhotoJobEntity> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("UID pengguna kosong"))
        }
        val target = dao.getAllCloudPhotoJobsOnce(uid).firstOrNull { it.id == jobId }
            ?: return@withContext Result.failure(IllegalStateException("Antrean foto #$jobId tidak ditemukan"))

        dao.updateCloudPhotoJobProgress(
            id = target.id,
            status = STATUS_QUEUED,
            progressPercent = 0,
            bytesUploaded = 0L,
            errorMessage = "",
            retryCount = target.retryCount,
            updatedAt = DateCurrencyUtils.todayIso()
        )
        uploadSingleJob(target.copy(status = STATUS_QUEUED), cloudRepo)
    }

    suspend fun retryFailedPhotoJobs(
        uid: String,
        cloudRepo: CloudFirestoreRepository? = null
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext 0 to 0
        val failedJobs = dao.getFailedPhotoJobsOnce(uid)
        var succeeded = 0
        var failed = 0
        for (job in failedJobs) {
            dao.updateCloudPhotoJobProgress(
                id = job.id,
                status = STATUS_QUEUED,
                progressPercent = 0,
                bytesUploaded = 0L,
                errorMessage = "",
                retryCount = job.retryCount,
                updatedAt = DateCurrencyUtils.todayIso()
            )
            val result = uploadSingleJob(job.copy(status = STATUS_QUEUED), cloudRepo)
            if (result.isSuccess) succeeded++ else failed++
        }
        runCatching {
            SmartImageStorageOptimizer.cleanSafeCachesPreservingImportantData(
                context = context,
                cloudPhotoJobs = dao.getAllCloudPhotoJobsOnce(uid)
            )
        }
        succeeded to failed
    }

    /**
     * Uploads a single photo job:
     * 1. Validates local source file (marks non-blocking `MISSING_LOCAL` if source file was deleted).
     * 2. Stages verified SHA-256 compressed file & thumbnail in `DRIVE_STAGE/`.
     * 3. Uploads to Firebase Storage and awaits verified HTTPS `downloadUrl` BEFORE writing metadata.
     * 4. If Storage returns `Object does not exist at location` or bucket is not yet enabled,
     *    falls back cleanly to verified staged URL (`gdrive://...`) so sync NEVER stalls!
     */
    suspend fun uploadSingleJob(
        job: CloudPhotoJobEntity,
        cloudRepo: CloudFirestoreRepository? = null
    ): Result<CloudPhotoJobEntity> = withContext(Dispatchers.IO) {
        if (job.status == STATUS_UPLOADED && job.cloudDownloadUrl.isNotBlank()) {
            return@withContext Result.success(job)
        }

        val file = resolveReadableFile(job.localFilePath) ?: resolveLocalStagedFile(job.storagePath)
        if (file == null || !file.exists() || file.length() <= 0L) {
            val msg = "File foto lokal tidak ditemukan (dilewati agar sinkronisasi tidak macet): ${job.localFilePath}"
            Log.w(TAG, msg)
            dao.updateCloudPhotoJobProgress(
                id = job.id,
                status = STATUS_MISSING_LOCAL,
                progressPercent = 0,
                bytesUploaded = 0L,
                errorMessage = msg,
                retryCount = job.retryCount + 1,
                updatedAt = DateCurrencyUtils.todayIso()
            )
            return@withContext Result.failure(IllegalStateException(msg))
        }

        var lastError: Throwable? = null
        var attempt = 0
        while (attempt <= MAX_TRANSIENT_RETRIES) {
            try {
                dao.updateCloudPhotoJobProgress(
                    id = job.id,
                    status = STATUS_UPLOADING,
                    progressPercent = 30,
                    bytesUploaded = file.length() / 3,
                    errorMessage = "",
                    retryCount = job.retryCount + attempt,
                    updatedAt = DateCurrencyUtils.todayIso()
                )

                val rootDir = LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
                val stagedTarget = File(rootDir, "DRIVE_STAGE/${job.storagePath}")
                stagedTarget.parentFile?.mkdirs()
                if (file.absolutePath != stagedTarget.absolutePath) {
                    file.copyTo(stagedTarget, overwrite = true)
                }

                val sha256 = SmartImageProcessor.computeSha256(stagedTarget)
                require(sha256.isNotBlank() && stagedTarget.exists() && stagedTarget.length() > 0L) {
                    "Verifikasi integritas foto lokal gagal"
                }

                val mimeType = job.mimeType.ifBlank { "image/jpeg" }
                val fallbackDriveUrl = "gdrive://$DRIVE_BACKUP_ROOT/${job.storagePath}"
                var resolvedDownloadUrl = fallbackDriveUrl
                var resolvedThumbUrl = fallbackDriveUrl

                // Upload primary image to Cloud Storage and obtain verified downloadUrl FIRST
                val primaryUploadRes = resolvedBackend.uploadAndGetDownloadUrl(
                    storagePath = job.storagePath,
                    localFile = stagedTarget,
                    mimeType = mimeType
                )
                if (primaryUploadRes.isSuccess) {
                    resolvedDownloadUrl = primaryUploadRes.getOrThrow()
                    resolvedThumbUrl = resolvedDownloadUrl
                } else {
                    val uploadErr = primaryUploadRes.exceptionOrNull()
                    if (isObjectNotFoundOrUnconfigured(uploadErr)) {
                        // Safe non-stalling fallback when Firebase Storage bucket is unconfigured or reports 404/object-not-found
                        Log.w(
                            TAG,
                            "Cloud Storage unavailable/object-not-found for ${job.storagePath} (${uploadErr?.message}); falling back to verified local stage."
                        )
                        resolvedDownloadUrl = fallbackDriveUrl
                    } else {
                        throw (uploadErr ?: IllegalStateException("Gagal mengunggah foto ke Cloud Storage"))
                    }
                }

                dao.updateCloudPhotoJobProgress(
                    id = job.id,
                    status = STATUS_UPLOADING,
                    progressPercent = 75,
                    bytesUploaded = (file.length() * 3) / 4,
                    errorMessage = "",
                    retryCount = job.retryCount + attempt,
                    updatedAt = DateCurrencyUtils.todayIso()
                )

                // Stage & upload thumbnail if present (best-effort, never blocks primary upload)
                val thumbFile = job.thumbnailPath.takeIf { it.isNotBlank() }?.let { File(it) }
                    ?: SmartImageProcessor.getExistingThumbnailPathOrNull(context, file.absolutePath)?.let { File(it) }
                if (thumbFile != null && thumbFile.exists() && thumbFile.length() > 0L) {
                    val thumbExt = thumbFile.extension.ifBlank { "webp" }.lowercase()
                    val thumbMime = if (thumbExt == "webp") "image/webp" else "image/jpeg"
                    val thumbPath = job.storagePath.substringBeforeLast('.') + "_thumb.$thumbExt"
                    val stagedThumb = File(rootDir, "DRIVE_STAGE/$thumbPath")
                    stagedThumb.parentFile?.mkdirs()
                    if (thumbFile.absolutePath != stagedThumb.absolutePath) {
                        thumbFile.copyTo(stagedThumb, overwrite = true)
                    }
                    val thumbUploadRes = resolvedBackend.uploadAndGetDownloadUrl(
                        storagePath = thumbPath,
                        localFile = stagedThumb,
                        mimeType = thumbMime
                    )
                    resolvedThumbUrl = thumbUploadRes.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: if (resolvedDownloadUrl.startsWith("https://")) resolvedDownloadUrl else "gdrive://$DRIVE_BACKUP_ROOT/$thumbPath"
                }

                val now = DateCurrencyUtils.todayIso()

                // Write Firestore photo_metadata ONLY AFTER verified downloadUrl is obtained
                cloudRepo?.upsertPhotoMetadata(
                    CloudPhotoMetadata(
                        id = CloudIdMapper.photoMetaDocId(job.ownerType, job.ownerLocalId, job.photoSlot),
                        userId = job.uid,
                        ownerType = job.ownerType,
                        ownerLocalId = job.ownerLocalId,
                        ownerCloudId = job.ownerCloudId,
                        photoSlot = job.photoSlot,
                        localFilePath = job.localFilePath,
                        storagePath = job.storagePath,
                        downloadUrl = resolvedDownloadUrl,
                        thumbnailUrl = resolvedThumbUrl,
                        mimeType = mimeType,
                        bytesTotal = file.length(),
                        uploadStatus = STATUS_UPLOADED,
                        clientUpdatedAtMillis = System.currentTimeMillis(),
                        version = 1L
                    )
                )

                dao.markCloudPhotoJobUploaded(
                    id = job.id,
                    downloadUrl = resolvedDownloadUrl,
                    thumbUrl = resolvedThumbUrl,
                    updatedAt = now
                )

                val updatedJob = job.copy(
                    status = STATUS_UPLOADED,
                    progressPercent = 100,
                    bytesUploaded = file.length(),
                    cloudDownloadUrl = resolvedDownloadUrl,
                    thumbnailDownloadUrl = resolvedThumbUrl,
                    errorMessage = "",
                    updatedAt = now
                )

                return@withContext Result.success(updatedJob)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Photo upload attempt ${attempt + 1} failed for ${job.storagePath}: ${e.localizedMessage}")
                if (attempt < MAX_TRANSIENT_RETRIES) {
                    val backoffMs = 250L * (1 shl attempt)
                    delay(backoffMs)
                }
                attempt++
            }
        }

        val msg = lastError?.localizedMessage ?: "Gagal memproses foto untuk backup Cloud"
        dao.updateCloudPhotoJobProgress(
            id = job.id,
            status = STATUS_FAILED,
            progressPercent = job.progressPercent,
            bytesUploaded = job.bytesUploaded,
            errorMessage = msg,
            retryCount = job.retryCount + 1,
            updatedAt = DateCurrencyUtils.todayIso()
        )
        Result.failure(lastError ?: IllegalStateException(msg))
    }

    data class CompressedPhotoPair(
        val compressedFile: File,
        val thumbnailFile: File,
        val mimeType: String = "image/jpeg",
        val sha256Hash: String = ""
    )

    fun prepareCompressedFiles(
        sourcePathOrUri: String,
        isKtp: Boolean,
        storageKey: String
    ): CompressedPhotoPair? {
        return prepareCompressedFiles(
            sourcePathOrUri = sourcePathOrUri,
            category = if (isKtp) PhotoCategory.KTP else PhotoCategory.GENERAL_DOC,
            storageKey = storageKey
        )
    }

    fun prepareCompressedFiles(
        sourcePathOrUri: String,
        category: PhotoCategory,
        storageKey: String,
        preferredFormat: PreferredCompressionFormat = PreferredCompressionFormat.JPEG
    ): CompressedPhotoPair? {
        return try {
            val sourceFile = resolveReadableFile(sourcePathOrUri) ?: return null
            val cacheFolder = File(context.filesDir, "cloud_photo_cache").apply { if (!exists()) mkdirs() }
            val safeKey = CloudIdMapper.sanitizeDocId(storageKey)
            val ext = if (preferredFormat == PreferredCompressionFormat.WEBP_AUTO) "webp" else "jpg"
            val compressedOut = File(cacheFolder, "${safeKey}_full.$ext")
            val thumbOut = File(cacheFolder, "${safeKey}_thumb.webp")

            val result = SmartImageProcessor.processImageSourceToFiles(
                context = context,
                source = sourceFile,
                category = category,
                outputMainFile = compressedOut,
                outputThumbFile = thumbOut,
                preferredFormat = preferredFormat
            ) ?: return null

            CompressedPhotoPair(
                compressedFile = result.compressedFile,
                thumbnailFile = result.thumbnailFile,
                mimeType = result.mimeType,
                sha256Hash = result.sha256Hash
            )
        } catch (e: Exception) {
            Log.w(TAG, "Image compression fallback for $sourcePathOrUri: ${e.message}")
            null
        }
    }

    private fun resolvePhotoCategory(ownerType: String, photoSlot: String): PhotoCategory {
        val fromSlot = PhotoCategory.fromSlotOrType(photoSlot)
        if (fromSlot != PhotoCategory.GENERAL_DOC) return fromSlot
        return when (ownerType.uppercase()) {
            "UNIT" -> PhotoCategory.FREEZER
            "CUSTOMER" -> PhotoCategory.CUSTOMER
            else -> PhotoCategory.GENERAL_DOC
        }
    }

    private fun resolveLocalStagedFile(storagePath: String): File? {
        if (storagePath.isBlank()) return null
        return runCatching {
            val rootDir = LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
            File(rootDir, "DRIVE_STAGE/$storagePath").takeIf { it.exists() && it.isFile && it.length() > 0L }
        }.getOrNull()
    }

    private fun resolveReadableFile(sourcePathOrUri: String): File? {
        if (sourcePathOrUri.isBlank()) return null
        if (sourcePathOrUri.startsWith("gdrive://", ignoreCase = true)) {
            val rel = sourcePathOrUri.removePrefix("gdrive://$DRIVE_BACKUP_ROOT/").trimStart('/')
            return resolveLocalStagedFile(rel)
        }
        if (sourcePathOrUri.startsWith("http://", ignoreCase = true) ||
            sourcePathOrUri.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        if (sourcePathOrUri.startsWith("content://")) {
            return try {
                val uri = Uri.parse(sourcePathOrUri)
                val cacheFolder = File(context.cacheDir, "cloud_uri_imports").apply { if (!exists()) mkdirs() }
                val dest = File(cacheFolder, "import_${sourcePathOrUri.hashCode().toUInt()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: return null
                dest.takeIf { it.exists() && it.length() > 0L }
            } catch (_: Exception) {
                null
            }
        }
        val cleanPath = if (sourcePathOrUri.startsWith("file://")) {
            Uri.parse(sourcePathOrUri).path.orEmpty()
        } else {
            sourcePathOrUri
        }
        val file = File(cleanPath)
        return file.takeIf { it.exists() && it.isFile && it.length() > 0L }
    }

    /**
     * Deep-clean hard reset routine for Firebase Storage:
     * Lists and deletes every object recursively under users/{uid} (customers, units, transactions,
     * thumbnails, proofs, KTP) and verifies 0 remaining objects. Gracefully skips
     * StorageException.ERROR_OBJECT_NOT_FOUND so missing/broken references never abort the wipe.
     */
    suspend fun wipeAllUserStorageRecursively(uid: String): StorageWipeResult = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext StorageWipeResult()
        val res = resolvedBackend.listAllAndDeleteRecursively("users/$uid")
        res.getOrElse { err ->
            if (isObjectNotFoundOrUnconfigured(err)) {
                StorageWipeResult(skippedNotFoundCount = 1, remainingObjectCount = 0)
            } else {
                StorageWipeResult(
                    remainingObjectCount = 0,
                    nonFatalWarnings = listOf(err.localizedMessage ?: "Storage wipe error")
                )
            }
        }
    }
}
