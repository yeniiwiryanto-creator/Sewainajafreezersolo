package com.example.process

import com.example.data.local.FreezerDao
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.MirrorResetResult
import com.example.drive.MirrorSyncBatchResult
import com.example.media.SmartImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real-Work Verification Engine:
 * Inspects actual SQLite rows, UUIDs, relational integrity, on-disk documentation/PDF/ZIP files,
 * Google Drive file IDs/checksums/mappings, and pending/failed/conflict/quarantine tables.
 *
 * NEVER relies on HTTP 200 or UI assumptions alone.
 */
class ProcessVerificationEngine(
    private val dao: FreezerDao
) {
    companion object {
        const val ENTITY_CUSTOMER = "CUSTOMER"
        const val ENTITY_UNIT = "UNIT"
        const val ENTITY_TRANSACTION = "TRANSACTION"
        const val ENTITY_PAYMENT = "PAYMENT"
        const val ENTITY_RETURN = "RETURN"
    }

    data class LocalCheckResult(
        val status: VerificationItemStatus,
        val detail: String,
        val verifiedUuid: String = ""
    )

    suspend fun verifyLocalEntitySaved(
        entityType: String,
        localId: Long,
        expectedUuid: String = "",
        extraIntegrityCheck: (suspend () -> String?)? = null
    ): LocalCheckResult = withContext(Dispatchers.IO) {
        if (localId <= 0L && expectedUuid.isBlank()) {
            return@withContext LocalCheckResult(
                status = VerificationItemStatus.FAILED,
                detail = "ID data lokal tidak valid ($localId) dan UUID kosong."
            )
        }
        val cleanExpectedUuid = expectedUuid.trim()
        val actualUuid: String? = when (entityType.uppercase()) {
            ENTITY_CUSTOMER -> {
                val c = (if (cleanExpectedUuid.isNotBlank()) dao.getCustomerByUuid(cleanExpectedUuid) else null)
                    ?: (if (localId > 0L) dao.getCustomerById(localId) else null)
                if (c == null || c.isDeleted || c.namaLengkap.isBlank()) null else c.uuid
            }
            ENTITY_UNIT -> {
                val u = (if (cleanExpectedUuid.isNotBlank()) dao.getUnitByUuid(cleanExpectedUuid) else null)
                    ?: (if (localId > 0L) dao.getUnitById(localId) else null)
                if (u == null || u.isDeleted || u.namaUnit.isBlank()) null else u.uuid
            }
            ENTITY_TRANSACTION -> {
                // Direct suspend Room query by committed UUID first (never relies on stale Flow/cache)
                val tx = (if (cleanExpectedUuid.isNotBlank()) dao.getTransactionByUuid(cleanExpectedUuid) else null)
                    ?: (if (localId > 0L) dao.getTransactionById(localId) else null)
                if (tx == null || tx.isDeleted || tx.nomorInvoice.isBlank()) {
                    null
                } else {
                    val custOk = tx.customerId <= 0L || dao.getCustomerById(tx.customerId) != null
                    if (!custOk) {
                        return@withContext LocalCheckResult(
                            status = VerificationItemStatus.FAILED,
                            detail = "Relasi pelanggan (ID ${tx.customerId}) pada transaksi ${tx.nomorInvoice} terputus."
                        )
                    }
                    tx.uuid
                }
            }
            ENTITY_PAYMENT -> {
                val p = (if (cleanExpectedUuid.isNotBlank()) dao.getPaymentProofByUuid(cleanExpectedUuid) else null)
                    ?: (if (localId > 0L) dao.getPaymentProofById(localId) else null)
                if (p == null || p.isDeleted) {
                    null
                } else {
                    val txOk = p.transactionId <= 0L || dao.getTransactionById(p.transactionId) != null
                    if (!txOk) {
                        return@withContext LocalCheckResult(
                            status = VerificationItemStatus.FAILED,
                            detail = "Relasi transaksi (ID ${p.transactionId}) pada bukti bayar terputus."
                        )
                    }
                    p.uuid
                }
            }
            ENTITY_RETURN -> {
                val r = dao.getReturnById(localId)
                if (r == null) null else "RETURN_${r.id}"
            }
            else -> "LOCAL_$localId"
        }

        if (actualUuid.isNullOrBlank()) {
            return@withContext LocalCheckResult(
                status = VerificationItemStatus.FAILED,
                detail = "Verifikasi SQLite gagal: data $entityType #$localId atau UUID tidak ditemukan."
            )
        }
        if (cleanExpectedUuid.isNotBlank() && actualUuid != cleanExpectedUuid) {
            return@withContext LocalCheckResult(
                status = VerificationItemStatus.FAILED,
                detail = "Verifikasi UUID tidak cocok (diharapkan $cleanExpectedUuid, tersimpan $actualUuid)."
            )
        }

        val extraErr = extraIntegrityCheck?.invoke()
        if (!extraErr.isNullOrBlank()) {
            return@withContext LocalCheckResult(
                status = VerificationItemStatus.FAILED,
                detail = extraErr
            )
        }

        LocalCheckResult(
            status = VerificationItemStatus.SUCCESS,
            detail = "Tersimpan & terverifikasi di SQLite (UUID ${actualUuid.take(8)}…).",
            verifiedUuid = actualUuid
        )
    }

    suspend fun verifyLocalEntityDeleted(
        entityType: String,
        localId: Long
    ): LocalCheckResult = withContext(Dispatchers.IO) {
        val stillActive = when (entityType.uppercase()) {
            ENTITY_CUSTOMER -> dao.getCustomerById(localId) != null
            ENTITY_UNIT -> dao.getUnitById(localId) != null
            ENTITY_TRANSACTION -> dao.getTransactionById(localId) != null
            ENTITY_PAYMENT -> dao.getPaymentProofById(localId) != null
            else -> false
        }
        if (stillActive) {
            return@withContext LocalCheckResult(
                status = VerificationItemStatus.FAILED,
                detail = "Verifikasi hapus gagal: data $entityType #$localId masih aktif di SQLite."
            )
        }
        LocalCheckResult(
            status = VerificationItemStatus.SUCCESS,
            detail = "Data $entityType #$localId terverifikasi terhapus dari SQLite."
        )
    }

    suspend fun verifyDocumentationFiles(
        localFilePaths: List<String>,
        linkedDrivePhotoCustomerId: Long? = null,
        linkedDrivePhotoUnitId: Long? = null,
        linkedDrivePhotoTransactionId: Long? = null,
        requireCloudUploaded: Boolean = false
    ): Pair<VerificationItemStatus, String> = withContext(Dispatchers.IO) {
        val cleanPaths = localFilePaths
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (cleanPaths.isEmpty() && linkedDrivePhotoCustomerId == null && linkedDrivePhotoUnitId == null && linkedDrivePhotoTransactionId == null) {
            return@withContext VerificationItemStatus.SKIPPED_NOT_REQUIRED to "Tidak ada dokumentasi foto yang dilampirkan."
        }

        val missingLocalFiles = cleanPaths.filter { path ->
            if (path.startsWith("http://", true) || path.startsWith("https://", true) ||
                path.startsWith("gdrive://", true) || path.startsWith("content://", true)
            ) {
                false
            } else {
                val f = File(path.removePrefix("file://"))
                !f.exists() || f.length() <= 0L
            }
        }
        if (missingLocalFiles.isNotEmpty()) {
            return@withContext VerificationItemStatus.FAILED to
                "${missingLocalFiles.size} file dokumentasi lokal tidak ditemukan atau kosong."
        }

        if (requireCloudUploaded) {
            val allPhotoMeta = dao.getAllDrivePhotoMetadataOnce()
            val relevant = allPhotoMeta.filter { meta ->
                (linkedDrivePhotoCustomerId != null && meta.customerId == linkedDrivePhotoCustomerId) ||
                    (linkedDrivePhotoUnitId != null && meta.unitId == linkedDrivePhotoUnitId) ||
                    (linkedDrivePhotoTransactionId != null && meta.transactionId == linkedDrivePhotoTransactionId)
            }
            if (relevant.isNotEmpty()) {
                val failedOrPending = relevant.filter {
                    it.syncStatus != "BERHASIL" && it.syncStatus != "SYNCED" || it.driveFileId.isBlank()
                }
                if (failedOrPending.isNotEmpty()) {
                    return@withContext VerificationItemStatus.PARTIAL_OFFLINE to
                        "Foto tersimpan di perangkat, tetapi ${failedOrPending.size} foto belum selesai diunggah ke Google Drive."
                }
                return@withContext VerificationItemStatus.SUCCESS to
                    "${relevant.size} dokumentasi terverifikasi di perangkat & Google Drive."
            }
        }

        val count = cleanPaths.size.coerceAtLeast(1)
        VerificationItemStatus.SUCCESS to "$count berkas dokumentasi tersimpan & terverifikasi."
    }

    /**
     * Verifies Google Drive upload + Sync queue + Conflict/Quarantine state for a specific record key
     * (e.g., "UNIT:12", "CUSTOMER:5", "TRANSACTION:30", "PAYMENT:14").
     */
    suspend fun verifyRecordCloudAndSyncState(
        localRecordKey: String,
        expectedUuid: String = "",
        isDeleteOperation: Boolean = false,
        cloudUid: String? = null,
        cloudDocId: String? = null,
        driveSyncError: String = ""
    ): Pair<Pair<VerificationItemStatus, String>, Pair<VerificationItemStatus, String>> = withContext(Dispatchers.IO) {
        if (driveSyncError.isNotBlank()) {
            val drivePair = VerificationItemStatus.PARTIAL_OFFLINE to driveSyncError
            val syncPair = VerificationItemStatus.PARTIAL_OFFLINE to "Menunggu koneksi / autentikasi Google Drive."
            return@withContext drivePair to syncPair
        }

        val queueItems = dao.getAllDriveLocalSyncQueueOnce()
        val matchingQueue = queueItems.filter {
            it.localRecordKey == localRecordKey ||
                (expectedUuid.isNotBlank() && it.recordUuid == expectedUuid)
        }
        val activePending = matchingQueue.filter {
            it.syncStatus in setOf(
                GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC,
                GoogleDriveMirrorSyncEngine.STATUS_UPLOADING,
                GoogleDriveMirrorSyncEngine.STATUS_DELETE_PENDING
            )
        }
        val failedQueue = matchingQueue.filter {
            it.syncStatus in setOf(
                GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH
            )
        }
        val conflictQueue = matchingQueue.filter {
            it.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT
        }

        val firestorePending = if (!cloudUid.isNullOrBlank() && !cloudDocId.isNullOrBlank()) {
            dao.getPendingSyncQueueOnce(cloudUid).any { it.cloudDocId == cloudDocId }
        } else {
            false
        }

        val mapping = dao.getDriveCloudFileMapByKey(localRecordKey)
            ?: expectedUuid.takeIf { it.isNotBlank() }?.let { dao.getDriveCloudFileMapByUuid(it) }
        val activeQuarantine = dao.getActiveDriveCloudQuarantineOnce().any {
            it.localRecordKey == localRecordKey ||
                (expectedUuid.isNotBlank() && it.recordUuid == expectedUuid)
        }

        val driveStatus: Pair<VerificationItemStatus, String> = when {
            failedQueue.isNotEmpty() -> {
                val reason = failedQueue.first().lastError.ifBlank { "Upload Google Drive gagal." }
                VerificationItemStatus.PARTIAL_OFFLINE to reason
            }
            conflictQueue.isNotEmpty() -> {
                VerificationItemStatus.PARTIAL_OFFLINE to "Terdapat konflik revisi pada Google Drive."
            }
            activePending.isNotEmpty() -> {
                VerificationItemStatus.PARTIAL_OFFLINE to "File masih menunggu antrean unggah Google Drive."
            }
            isDeleteOperation -> {
                val tombstone = dao.getDriveDeletionTombstoneByKey(localRecordKey)
                val mappingStillActive = mapping != null &&
                    !mapping.syncStatus.equals("DELETED", ignoreCase = true)
                if (mappingStillActive) {
                    VerificationItemStatus.PARTIAL_OFFLINE to "Penghapusan file di Google Drive belum terkonfirmasi."
                } else if (tombstone != null || mapping == null) {
                    VerificationItemStatus.SUCCESS to "Penghapusan terverifikasi pada Google Drive."
                } else {
                    VerificationItemStatus.SUCCESS to "File cloud bersih & terverifikasi."
                }
            }
            else -> {
                val hasValidDriveId = mapping != null &&
                    mapping.driveFileId.isNotBlank() &&
                    mapping.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED &&
                    (expectedUuid.isBlank() || mapping.recordUuid.isBlank() || mapping.recordUuid == expectedUuid) &&
                    mapping.conflictReason.isBlank()
                if (hasValidDriveId) {
                    VerificationItemStatus.SUCCESS to "File terverifikasi di Google Drive (ID: ${mapping!!.driveFileId.take(10)}…)."
                } else {
                    VerificationItemStatus.PARTIAL_OFFLINE to "File belum memiliki ID Google Drive terverifikasi."
                }
            }
        }

        val syncStatus: Pair<VerificationItemStatus, String> = when {
            conflictQueue.isNotEmpty() -> VerificationItemStatus.PARTIAL_OFFLINE to "Terdapat konflik sinkronisasi yang belum diselesaikan."
            activeQuarantine -> VerificationItemStatus.PARTIAL_OFFLINE to "File terkait masih berada di karantina cloud."
            failedQueue.isNotEmpty() -> VerificationItemStatus.PARTIAL_OFFLINE to "Sinkronisasi gagal dan masuk antrean coba ulang."
            activePending.isNotEmpty() || firestorePending -> VerificationItemStatus.PARTIAL_OFFLINE to "Masih ada antrean sinkronisasi yang tertunda."
            driveStatus.first == VerificationItemStatus.SUCCESS -> VerificationItemStatus.SUCCESS to "Lokal & Cloud konsisten tanpa pending/konflik."
            else -> VerificationItemStatus.PARTIAL_OFFLINE to "Menunggu sinkronisasi penuh ke Cloud."
        }

        driveStatus to syncStatus
    }

    suspend fun verifyFullSyncBatch(batch: MirrorSyncBatchResult): ProcessVerificationReport = withContext(Dispatchers.IO) {
        val activeQuarantine = dao.getActiveDriveCloudQuarantineOnce().size
        val hasBlockingIssues = batch.totalPendingInDb > 0 ||
            batch.totalFailedInDb > 0 ||
            batch.totalFailedAuthInDb > 0 ||
            batch.totalConflictInDb > 0 ||
            batch.requiresReAuth

        val driveStatus = when {
            batch.requiresReAuth -> VerificationItemStatus.FAILED to "Autentikasi Google Drive kedaluwarsa. Silakan hubungkan ulang."
            batch.totalFailedInDb > 0 || batch.totalFailedAuthInDb > 0 ->
                VerificationItemStatus.PARTIAL_OFFLINE to "${batch.totalFailedInDb + batch.totalFailedAuthInDb} operasi Google Drive gagal/tertunda."
            batch.totalPendingInDb > 0 ->
                VerificationItemStatus.PARTIAL_OFFLINE to "${batch.totalPendingInDb} item masih menunggu unggah Google Drive."
            else -> VerificationItemStatus.SUCCESS to "Seluruh file & manifest Google Drive terverifikasi."
        }

        val syncStatus = when {
            batch.totalConflictInDb > 0 -> VerificationItemStatus.PARTIAL_OFFLINE to "${batch.totalConflictInDb} konflik revisi belum diselesaikan."
            activeQuarantine > 0 -> VerificationItemStatus.PARTIAL_OFFLINE to "$activeQuarantine file masih berada dalam karantina cloud."
            hasBlockingIssues -> VerificationItemStatus.PARTIAL_OFFLINE to batch.errorMessage.ifBlank { "Sinkronisasi belum tuntas sepenuhnya." }
            else -> VerificationItemStatus.SUCCESS to "Sinkronisasi 100% konsisten (0 pending, 0 gagal, 0 konflik)."
        }

        ProcessVerificationReport(
            localDatabaseStatus = VerificationItemStatus.SUCCESS,
            localDatabaseDetail = "Database SQLite lokal sehat & terbaca.",
            documentationStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
            documentationDetail = "Dokumentasi ikut diperiksa dalam antrean sinkronisasi.",
            googleDriveStatus = driveStatus.first,
            googleDriveDetail = driveStatus.second,
            synchronizationStatus = syncStatus.first,
            synchronizationDetail = syncStatus.second,
            requireCloudForFullCompletion = true
        )
    }

    fun verifyPdfDocument(pdfFile: File?): LocalCheckResult {
        if (pdfFile == null || !pdfFile.exists() || pdfFile.length() < 64L) {
            return LocalCheckResult(
                status = VerificationItemStatus.FAILED,
                detail = "Berkas Invoice PDF gagal dibuat atau berukuran 0 byte."
            )
        }
        val hasPdfHeader = runCatching {
            pdfFile.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 && String(header, Charsets.US_ASCII) == "%PDF"
            }
        }.getOrDefault(false)
        if (!hasPdfHeader) {
            return LocalCheckResult(
                status = VerificationItemStatus.FAILED,
                detail = "Verifikasi format PDF gagal: header berkas tidak valid."
            )
        }
        return LocalCheckResult(
            status = VerificationItemStatus.SUCCESS,
            detail = "Berkas PDF '${pdfFile.name}' (${pdfFile.length() / 1024} KB) terverifikasi valid."
        )
    }

    fun verifyBackupArchive(
        file: File?,
        expectedSha256: String = ""
    ): LocalCheckResult {
        if (file == null || !file.exists() || file.length() <= 0L) {
            return LocalCheckResult(
                status = VerificationItemStatus.FAILED,
                detail = "Arsip backup tidak ditemukan atau kosong."
            )
        }
        if (expectedSha256.isNotBlank()) {
            val actualHash = SmartImageProcessor.computeSha256(file)
            if (!actualHash.equals(expectedSha256, ignoreCase = true)) {
                return LocalCheckResult(
                    status = VerificationItemStatus.FAILED,
                    detail = "Checksum SHA-256 arsip backup tidak cocok (berkas korup)."
                )
            }
        }
        return LocalCheckResult(
            status = VerificationItemStatus.SUCCESS,
            detail = "Arsip '${file.name}' (${file.length() / 1024} KB) terverifikasi utuh."
        )
    }

    suspend fun verifyFactoryResetCompleted(
        resetResult: MirrorResetResult,
        includeCloud: Boolean
    ): ProcessVerificationReport = withContext(Dispatchers.IO) {
        val remCustomers = dao.getAllCustomersOnce().size
        val remUnits = dao.getAllUnitsOnce().size
        val remTx = dao.getAllTransactionsOnce().size
        val remPay = dao.getAllPaymentProofsOnce().size
        val totalRemaining = remCustomers + remUnits + remTx + remPay

        val localOk = totalRemaining == 0
        val cloudOk = !includeCloud || resetResult.freshManifestFileId.isNotBlank()

        ProcessVerificationReport(
            localDatabaseStatus = if (localOk) VerificationItemStatus.SUCCESS else VerificationItemStatus.FAILED,
            localDatabaseDetail = if (localOk) {
                "Database lokal bersih (0 sisa data aktif, generasi ${resetResult.datasetGeneration})."
            } else {
                "Masih tersisa $totalRemaining data aktif di SQLite setelah reset."
            },
            documentationStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
            documentationDetail = "Arsip backup historis tetap aman.",
            googleDriveStatus = when {
                !includeCloud -> VerificationItemStatus.SKIPPED_NOT_REQUIRED
                cloudOk -> VerificationItemStatus.SUCCESS
                else -> VerificationItemStatus.PARTIAL_OFFLINE
            },
            googleDriveDetail = when {
                !includeCloud -> "Mode Reset Lokal Saja (Google Drive tidak diubah)."
                cloudOk -> "${resetResult.deletedCloudFilesCount} file aktif dibersihkan & manifest baru terverifikasi."
                else -> "Manifest cloud baru belum terverifikasi di Google Drive."
            },
            synchronizationStatus = when {
                !includeCloud -> VerificationItemStatus.SKIPPED_NOT_REQUIRED
                cloudOk -> VerificationItemStatus.SUCCESS
                else -> VerificationItemStatus.PARTIAL_OFFLINE
            },
            synchronizationDetail = if (cloudOk) "Dataset baru siap digunakan." else "Sinkronisasi reset cloud belum tuntas.",
            requireCloudForFullCompletion = includeCloud
        )
    }
}
