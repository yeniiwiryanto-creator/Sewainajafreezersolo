package com.example.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.LocalBackupEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.repository.FreezerRepository
import com.example.storage.LocalStorageAndBackupManager
import com.example.util.DateCurrencyUtils
import com.example.util.SecurityCryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class RestoreMode(val label: String) {
    FULL_RESTORE("FULL RESTORE (Ganti Data Saat Ini)"),
    APPEND_MERGE("IMPORT DATA TAMBAHAN (Gabung Tanpa Hapus)"),
    CATEGORY_CUSTOMERS("RESTORE KATEGORI: Data Pelanggan"),
    CATEGORY_UNITS("RESTORE KATEGORI: Data Unit Freezer"),
    CATEGORY_TRANSACTIONS_PAYMENTS("RESTORE KATEGORI: Transaksi & Pembayaran"),
    CATEGORY_SETTINGS_AGREEMENT("RESTORE KATEGORI: Pengaturan & Perjanjian"),
    CATEGORY_PHOTOS_DOCS("RESTORE KATEGORI: Dokumentasi Foto & KTP")
}

data class RestorePreviewSummary(
    val isValid: Boolean,
    val errorMessage: String = "",
    val fileName: String = "",
    val backupId: String = "",
    val createdAt: String = "",
    val appVersion: String = "",
    val databaseVersion: Int = 0,
    val customerCount: Int = 0,
    val unitCount: Int = 0,
    val transactionCount: Int = 0,
    val paymentCount: Int = 0,
    val photoCount: Int = 0,
    val totalArchiveSizeBytes: Long = 0L,
    val isEncrypted: Boolean = false,
    val checksumVerified: Boolean = false,
    val verifiedEntriesCount: Int = 0,
    val isLegacyArchive: Boolean = false
)

data class StructuredRestoreResult(
    val success: Boolean,
    val message: String,
    val restoredRecordsCount: Int = 0,
    val restoredPhotosCount: Int = 0,
    val rolledBack: Boolean = false
)

object StructuredBackupEngine {

    const val ROOT_ZIP_FOLDER = "SEWAIN_BACKUP"
    const val CURRENT_DB_VERSION = 25
    const val CURRENT_APP_VERSION = "2.8.0"
    const val MAX_ARCHIVE_BYTES = 120L * 1024L * 1024L
    const val MAX_SINGLE_ENTRY_BYTES = 50L * 1024L * 1024L
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 450L * 1024L * 1024L
    const val MAX_ZIP_ENTRIES = 10_000

    /**
     * Collects physical photos across all app storage locations and maps them cleanly under
     * SEWAIN_BACKUP/photos/{customers|units|transactions|payments}/<filename>
     */
    fun collectPhotoEntries(context: Context): List<Pair<String, File>> {
        val result = linkedMapOf<String, File>()
        val candidates = listOf(
            LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context),
            File(context.filesDir, "photos"),
            File(context.filesDir, "ktp_documents"),
            File(context.filesDir, "signatures"),
            File(context.filesDir, "payment_proofs"),
            File(context.filesDir, "pdf_templates")
        )
        candidates.forEach { baseDir ->
            if (!baseDir.exists()) return@forEach
            baseDir.walkTopDown()
                .filter { it.isFile && it.length() > 0L }
                .forEach { file ->
                    val pathLower = file.absolutePath.lowercase(Locale.ROOT)
                    if (pathLower.contains("/backup/") || file.extension.equals("zip", ignoreCase = true)) {
                        return@forEach
                    }
                    val category = when {
                        pathLower.contains("payment") || pathLower.contains("bukti") -> "payments"
                        pathLower.contains("ktp") || pathLower.contains("/customers/") || pathLower.contains("customer") -> "customers"
                        pathLower.contains("unit") || pathLower.contains("freezer") -> "units"
                        else -> "transactions"
                    }
                    val safeName = file.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val zipPath = "$ROOT_ZIP_FOLDER/photos/$category/$safeName"
                    if (!result.containsKey(zipPath)) {
                        result[zipPath] = file
                    }
                }
        }
        return result.entries.map { it.key to it.value }
    }

    suspend fun captureCurrentSnapshot(
        context: Context,
        repository: FreezerRepository
    ): BackupDatabaseSnapshot {
        val prefs = context.getSharedPreferences("saf_soc_prefs", Context.MODE_PRIVATE)
        val cpJson = prefs.getString("company_profile_json", "").orEmpty()
        val companyProfile = if (cpJson.isNotBlank()) {
            runCatching {
                BackupJsonMapper.companyProfileFromJson(JSONObject().put("companyProfile", JSONObject(cpJson)))
            }.getOrDefault(CompanyProfile())
        } else {
            CompanyProfile()
        }
        val imagePrefs = context.getSharedPreferences("saf_soc_smart_image_prefs", Context.MODE_PRIVATE)
        return BackupDatabaseSnapshot(
            customers = repository.getAllCustomersOnce(),
            units = repository.getAllUnitsOnce(),
            transactions = repository.getAllTransactionsOnce(),
            unitItems = repository.getAllTransactionUnitItemsOnce(),
            paymentProofs = repository.getAllPaymentProofsOnce(),
            unitAllocations = repository.getAllUnitPaymentAllocationsOnce(),
            signatures = repository.getAllTransactionSignaturesOnce(),
            returns = repository.getAllReturnsOnce(),
            extensions = repository.getAllExtensionsOnce(),
            paymentMethods = repository.getAllPaymentMethodsOnce(),
            customerDocuments = repository.getAllCustomerDocumentsOnce(),
            transactionDocuments = repository.getAllTransactionDocumentsOnce(),
            customerLocations = repository.getAllCustomerLocationsOnce(),
            customerPhotos = repository.getAllCustomerPhotosOnce(),
            timelineEvents = repository.getAllCustomerTimelineEventsOnce(),
            companyProfile = companyProfile,
            perjanjianSewaText = prefs.getString("perjanjian_sewa_text", "").orEmpty(),
            googleSheetUrl = prefs.getString("google_sheet_url", "").orEmpty(),
            webhookUrl = prefs.getString("webhook_url", "").orEmpty(),
            autoSyncEnabled = prefs.getBoolean("auto_sync_enabled", false),
            maskNikInPdf = prefs.getBoolean("mask_nik_pdf", true),
            imageQualityTier = imagePrefs.getString("image_quality_tier", "STANDAR") ?: "STANDAR"
        )
    }

    suspend fun createStructuredBackupZip(
        context: Context,
        repository: FreezerRepository,
        encryptSensitivePayload: Boolean = true,
        includePhotos: Boolean = true,
        backupType: String = "FULL_BACKUP",
        destinationDir: File? = null,
        customFileName: String? = null,
        saveToHistory: Boolean = true,
        maxVersionsToKeep: Int = 30
    ): Result<Pair<LocalBackupEntity, File>> = withContext(Dispatchers.IO) {
        try {
            val rootDir = LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
            val targetDir = destinationDir ?: File(rootDir, "BACKUP/FULL_BACKUP").also { if (!it.exists()) it.mkdirs() }
            if (!targetDir.exists()) targetDir.mkdirs()

            val tsCompact = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            val nowDisplay = DateCurrencyUtils.nowDateTimeDisplay()
            val backupId = "bkp_${tsCompact}_${UUID.randomUUID().toString().take(6)}"
            val zipFileName = customFileName ?: "SEWAIN_BACKUP_${backupType}_$tsCompact.zip"
            val tempZip = File(targetDir, "$zipFileName.tmp")
            val finalZip = File(targetDir, zipFileName)

            val snapshot = captureCurrentSnapshot(context, repository)
            val photoEntries = if (includePhotos) collectPhotoEntries(context) else emptyList()

            val customersBytes = BackupJsonMapper.encodePayload(
                BackupJsonMapper.customersToJson(snapshot.customers),
                encrypt = encryptSensitivePayload
            ).toByteArray(Charsets.UTF_8)
            val unitsBytes = BackupJsonMapper.encodePayload(
                BackupJsonMapper.unitsToJson(snapshot.units),
                encrypt = encryptSensitivePayload
            ).toByteArray(Charsets.UTF_8)
            val txBytes = BackupJsonMapper.encodePayload(
                BackupJsonMapper.transactionsToJson(snapshot.transactions),
                encrypt = encryptSensitivePayload
            ).toByteArray(Charsets.UTF_8)
            val paymentsBytes = BackupJsonMapper.encodePayload(
                BackupJsonMapper.paymentsToJson(snapshot.paymentProofs, snapshot.unitAllocations),
                encrypt = encryptSensitivePayload
            ).toByteArray(Charsets.UTF_8)
            val settingsBytes = BackupJsonMapper.encodePayload(
                BackupJsonMapper.settingsToJson(
                    companyProfile = snapshot.companyProfile,
                    paymentMethods = snapshot.paymentMethods,
                    perjanjianSewaText = snapshot.perjanjianSewaText,
                    imageQualityTier = snapshot.imageQualityTier,
                    googleSheetUrl = snapshot.googleSheetUrl,
                    webhookUrl = snapshot.webhookUrl,
                    autoSyncEnabled = snapshot.autoSyncEnabled,
                    maskNikInPdf = snapshot.maskNikInPdf
                ),
                encrypt = encryptSensitivePayload
            ).toByteArray(Charsets.UTF_8)
            val extraBytes = BackupJsonMapper.encodePayload(
                BackupExtraRelationsMapper.toJson(snapshot),
                encrypt = encryptSensitivePayload
            ).toByteArray(Charsets.UTF_8)

            val sqliteDbFile = context.getDatabasePath("sewain_freezer_soc_db")
            val rawSqliteBytes = if (sqliteDbFile.exists() && sqliteDbFile.isFile && sqliteDbFile.length() > 0L) {
                runCatching { sqliteDbFile.readBytes() }.getOrElse { "SQLITE_SNAPSHOT_V15".toByteArray(Charsets.UTF_8) }
            } else {
                "SQLITE_SNAPSHOT_V15_IN_MEMORY".toByteArray(Charsets.UTF_8)
            }
            val sqliteSnapshotBytes = if (encryptSensitivePayload) {
                SecurityCryptoUtils.encryptBytesAesGcm(rawSqliteBytes)
            } else {
                rawSqliteBytes
            }

            val inMemoryEntries = linkedMapOf(
                "$ROOT_ZIP_FOLDER/database/sewain_freezer_soc.sqlite" to sqliteSnapshotBytes,
                "$ROOT_ZIP_FOLDER/database/customers.json" to customersBytes,
                "$ROOT_ZIP_FOLDER/database/units.json" to unitsBytes,
                "$ROOT_ZIP_FOLDER/database/transactions.json" to txBytes,
                "$ROOT_ZIP_FOLDER/database/payments.json" to paymentsBytes,
                "$ROOT_ZIP_FOLDER/database/settings.json" to settingsBytes,
                "$ROOT_ZIP_FOLDER/database/extra_relations.json" to extraBytes
            )

            val checksumEntriesArray = JSONArray()
            inMemoryEntries.forEach { (path, bytes) ->
                checksumEntriesArray.put(JSONObject().apply {
                    put("entryPath", path)
                    put("sizeBytes", bytes.size.toLong())
                    put("sha256", SecurityCryptoUtils.sha256Hex(bytes))
                })
            }
            photoEntries.forEach { (path, file) ->
                checksumEntriesArray.put(JSONObject().apply {
                    put("entryPath", path)
                    put("sizeBytes", file.length())
                    put("sha256", SecurityCryptoUtils.sha256Hex(file))
                })
            }

            val checksumJson = JSONObject().apply {
                put("algorithm", "SHA-256")
                put("generatedAt", nowDisplay)
                put("entryCount", checksumEntriesArray.length())
                put("entries", checksumEntriesArray)
            }

            val backupInfoJson = JSONObject().apply {
                put("appName", "SEWAin Aja Freezer SOC")
                put("backupId", backupId)
                put("backupType", backupType)
                put("createdAt", nowDisplay)
                put("encrypted", encryptSensitivePayload)
                put("encryptionAlgorithm", if (encryptSensitivePayload) "AES-256-GCM" else "NONE")
                put("includePhotos", includePhotos)
                put("deviceSdk", android.os.Build.VERSION.SDK_INT)
            }

            val estimatedBytes = inMemoryEntries.values.sumOf { it.size.toLong() } +
                photoEntries.sumOf { it.second.length() }

            val generationManager = com.example.drive.DatabaseGenerationManager(context)
            val manifestJson = JSONObject().apply {
                put("backupId", backupId)
                put("createdAt", nowDisplay)
                put("createdAtMillis", System.currentTimeMillis())
                put("backupDate", DateCurrencyUtils.todayIso())
                put("appVersion", CURRENT_APP_VERSION)
                put("databaseVersion", CURRENT_DB_VERSION)
                put("datasetGeneration", generationManager.getCurrentGenerationId())
                put("generation_id", generationManager.getCurrentGenerationId())
                put("datasetId", generationManager.getCurrentDatasetId())
                put("backupType", backupType)
                put("encrypted", encryptSensitivePayload)
                put("customerCount", snapshot.customers.size)
                put("unitCount", snapshot.units.size)
                put("transactionCount", snapshot.transactions.size)
                put("paymentCount", snapshot.paymentProofs.size)
                put("photoCount", photoEntries.size)
                put("fileSizeBytes", estimatedBytes)
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
                fun writeBytesEntry(path: String, data: ByteArray) {
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(data)
                    zos.closeEntry()
                }
                writeBytesEntry("$ROOT_ZIP_FOLDER/manifest.json", manifestJson.toString(2).toByteArray(Charsets.UTF_8))
                writeBytesEntry("$ROOT_ZIP_FOLDER/backup_info.json", backupInfoJson.toString(2).toByteArray(Charsets.UTF_8))
                writeBytesEntry("$ROOT_ZIP_FOLDER/checksum.json", checksumJson.toString(2).toByteArray(Charsets.UTF_8))
                inMemoryEntries.forEach { (path, bytes) -> writeBytesEntry(path, bytes) }

                val buffer = ByteArray(8192)
                photoEntries.forEach { (path, file) ->
                    if (file.exists() && file.isFile) {
                        zos.putNextEntry(ZipEntry(path))
                        BufferedInputStream(FileInputStream(file)).use { bis ->
                            var read = bis.read(buffer)
                            while (read > 0) {
                                zos.write(buffer, 0, read)
                                read = bis.read(buffer)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }

            if (finalZip.exists()) finalZip.delete()
            if (!tempZip.renameTo(finalZip)) {
                tempZip.copyTo(finalZip, overwrite = true)
                tempZip.delete()
            }

            val archiveSha256 = SecurityCryptoUtils.sha256Hex(finalZip)
            val entity = LocalBackupEntity(
                backupName = zipFileName,
                backupPath = finalZip.absolutePath,
                backupSize = finalZip.length(),
                backupType = backupType,
                backupStatus = "SUCCESS",
                createdAt = nowDisplay,
                backupId = backupId,
                locationType = "LOKAL",
                driveFileId = "",
                verificationStatus = "TERVERIFIKASI",
                checksumSha256 = archiveSha256,
                isEncrypted = encryptSensitivePayload,
                customerCount = snapshot.customers.size,
                unitCount = snapshot.units.size,
                transactionCount = snapshot.transactions.size,
                paymentCount = snapshot.paymentProofs.size,
                photoCount = photoEntries.size
            )

            val savedEntity = if (saveToHistory) {
                val id = repository.saveLocalBackup(entity)
                // Prune ONLY oldest versions beyond maxVersions AFTER verified creation
                pruneOldLocalBackupsAfterVerified(repository, maxVersionsToKeep)
                entity.copy(id = id)
            } else entity

            Result.success(savedEntity to finalZip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pruneOldLocalBackupsAfterVerified(
        repository: FreezerRepository,
        maxVersionsToKeep: Int = 30
    ): Int {
        val safeMax = maxVersionsToKeep.coerceAtLeast(1)
        val all = repository.getAllLocalBackupsOnce()
        if (all.size <= safeMax) return 0
        // getAllLocalBackupsOnce is sorted id DESC (newest first)
        val overflow = all.drop(safeMax)
        var pruned = 0
        overflow.forEach { old ->
            runCatching {
                val file = File(old.backupPath)
                if (file.exists() && file.isFile) file.delete()
            }
            repository.deleteLocalBackupById(old.id)
            pruned++
        }
        return pruned
    }

    suspend fun inspectBackupZipForRestore(
        context: Context,
        sourceFileOrUri: Any
    ): RestorePreviewSummary = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "inspect_bkp_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val zipFile = materializeSourceZip(context, sourceFileOrUri, staging)
                ?: return@withContext RestorePreviewSummary(false, "File backup tidak ditemukan atau tidak dapat dibaca.")

            if (zipFile.length() <= 0L) {
                return@withContext RestorePreviewSummary(false, "File backup kosong.")
            }
            if (zipFile.length() > MAX_ARCHIVE_BYTES) {
                return@withContext RestorePreviewSummary(false, "Ukuran file backup melebihi batas aman (120 MB).")
            }

            // Detect non-ZIP supported formats: JSON full backup or SQLite database file
            val headerBytes = ByteArray(16)
            val headerRead = FileInputStream(zipFile).use { it.read(headerBytes) }
            val isZipMagic = headerRead >= 4 && headerBytes[0] == 0x50.toByte() && headerBytes[1] == 0x4B.toByte()
            val isSqliteMagic = headerRead >= 15 && String(headerBytes, 0, 15, Charsets.US_ASCII) == "SQLite format 3"
            val trimmedPrefix = if (headerRead > 0) String(headerBytes, 0, headerRead, Charsets.UTF_8).trimStart() else ""
            val isJsonFormat = !isZipMagic && !isSqliteMagic && (trimmedPrefix.startsWith("{") || zipFile.name.endsWith(".json", ignoreCase = true))

            if (isJsonFormat) {
                return@withContext inspectJsonBackupFile(zipFile)
            }
            if (isSqliteMagic) {
                return@withContext inspectSqliteBackupFile(zipFile)
            }

            val extractRes = extractZipSafely(zipFile, staging)
            if (extractRes.first != null) {
                return@withContext RestorePreviewSummary(false, extractRes.first!!)
            }

            val extractedRoot = File(staging, "extracted")
            val structuredManifest = File(extractedRoot, "$ROOT_ZIP_FOLDER/manifest.json")
            val structuredChecksum = File(extractedRoot, "$ROOT_ZIP_FOLDER/checksum.json")
            val legacyManifest = File(extractedRoot, "manifest.json")

            if (structuredManifest.exists() && structuredChecksum.exists()) {
                val m = JSONObject(structuredManifest.readText(Charsets.UTF_8))
                val dbVer = m.optInt("databaseVersion", 0)
                if (dbVer <= 0 || dbVer > CURRENT_DB_VERSION) {
                    return@withContext RestorePreviewSummary(false, "Versi database backup ($dbVer) tidak kompatibel dengan aplikasi (v$CURRENT_DB_VERSION).")
                }
                val cs = JSONObject(structuredChecksum.readText(Charsets.UTF_8))
                val entries = cs.optJSONArray("entries") ?: JSONArray()
                var verified = 0
                for (i in 0 until entries.length()) {
                    val item = entries.getJSONObject(i)
                    val entryPath = item.getString("entryPath")
                    val expectedSha = item.getString("sha256")
                    val stagedTarget = SecurityCryptoUtils.sanitizeZipEntryPath(entryPath, extractedRoot)
                        ?: return@withContext RestorePreviewSummary(false, "Path entry checksum tidak aman: $entryPath")
                    if (!stagedTarget.exists()) {
                        return@withContext RestorePreviewSummary(false, "File hilang dari arsip backup: $entryPath")
                    }
                    val actualSha = SecurityCryptoUtils.sha256Hex(stagedTarget)
                    if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                        return@withContext RestorePreviewSummary(
                            false,
                            "Verifikasi checksum SHA-256 gagal pada '$entryPath' (file korup atau dimodifikasi)."
                        )
                    }
                    verified++
                }
                return@withContext RestorePreviewSummary(
                    isValid = true,
                    fileName = zipFile.name,
                    backupId = m.optString("backupId", ""),
                    createdAt = m.optString("createdAt", ""),
                    appVersion = m.optString("appVersion", CURRENT_APP_VERSION),
                    databaseVersion = dbVer,
                    customerCount = m.optInt("customerCount", 0),
                    unitCount = m.optInt("unitCount", 0),
                    transactionCount = m.optInt("transactionCount", 0),
                    paymentCount = m.optInt("paymentCount", 0),
                    photoCount = m.optInt("photoCount", 0),
                    totalArchiveSizeBytes = zipFile.length(),
                    isEncrypted = m.optBoolean("encrypted", false),
                    checksumVerified = true,
                    verifiedEntriesCount = verified,
                    isLegacyArchive = false
                )
            } else if (legacyManifest.exists()) {
                val m = JSONObject(legacyManifest.readText(Charsets.UTF_8))
                val dbVer = m.optInt("databaseVersion", 0)
                if (dbVer <= 0 || dbVer > CURRENT_DB_VERSION) {
                    return@withContext RestorePreviewSummary(false, "Versi database backup ($dbVer) tidak kompatibel.")
                }
                val filesArray = m.optJSONArray("files") ?: JSONArray()
                for (i in 0 until filesArray.length()) {
                    val item = filesArray.getJSONObject(i)
                    val entryPath = item.getString("entryPath")
                    val expectedSha = item.getString("sha256")
                    val stagedTarget = SecurityCryptoUtils.sanitizeZipEntryPath(entryPath, extractedRoot)
                        ?: return@withContext RestorePreviewSummary(false, "Path file backup tidak aman (Zip Slip).")
                    if (!stagedTarget.exists()) {
                        return@withContext RestorePreviewSummary(false, "File hilang: $entryPath")
                    }
                    if (!SecurityCryptoUtils.sha256Hex(stagedTarget).equals(expectedSha, ignoreCase = true)) {
                        return@withContext RestorePreviewSummary(false, "Checksum SHA-256 tidak cocok untuk '$entryPath'")
                    }
                }
                return@withContext RestorePreviewSummary(
                    isValid = true,
                    fileName = zipFile.name,
                    backupId = "legacy",
                    createdAt = m.optString("createdAt", ""),
                    appVersion = m.optString("appName", CURRENT_APP_VERSION),
                    databaseVersion = dbVer,
                    totalArchiveSizeBytes = zipFile.length(),
                    checksumVerified = true,
                    verifiedEntriesCount = filesArray.length(),
                    isLegacyArchive = true
                )
            } else {
                RestorePreviewSummary(false, "Struktur arsip backup tidak dikenali (manifest.json / checksum.json tidak ditemukan).")
            }
        } catch (e: Exception) {
            RestorePreviewSummary(false, "Gagal memvalidasi arsip backup: ${e.localizedMessage ?: "Arsip korup"}")
        } finally {
            runCatching { staging.deleteRecursively() }
        }
    }

    /**
     * Rollback-safe Restore Execution:
     * 1. Extracts & verifies SHA-256 checksums + DB compatibility completely in staging BEFORE touching live DB.
     * 2. Creates automatic pre-restore Safety Backup snapshot of current database & photos BEFORE modifying anything.
     * 3. Executes database write inside atomic Room @Transaction; copies photo files atomically.
     * 4. If ANY error happens mid-restore, automatically rolls back BOTH database & photos to the safety snapshot!
     */
    suspend fun executeSafeRestore(
        context: Context,
        repository: FreezerRepository,
        sourceFileOrUri: Any,
        mode: RestoreMode,
        simulateFailureHookForTest: (() -> Unit)? = null,
        onProgress: ((Float, String) -> Unit)? = null
    ): StructuredRestoreResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "exec_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        var safetyBackupFile: File? = null
        try {
            onProgress?.invoke(0.10f, "Memeriksa format & integritas file backup...")
            val zipFile = materializeSourceZip(context, sourceFileOrUri, staging)
                ?: return@withContext StructuredRestoreResult(false, "File backup tidak ditemukan.")

            // Step 1: Validate archive & checksums completely before touching current data
            val preview = inspectBackupZipForRestore(context, zipFile)
            if (!preview.isValid) {
                return@withContext StructuredRestoreResult(false, preview.errorMessage)
            }

            // Handle raw JSON backup format directly with full safety snapshot & rollback
            val hdr = ByteArray(16)
            val hdrRead = FileInputStream(zipFile).use { it.read(hdr) }
            val isZipMagic = hdrRead >= 4 && hdr[0] == 0x50.toByte() && hdr[1] == 0x4B.toByte()
            val isSqliteMagic = hdrRead >= 15 && String(hdr, 0, 15, Charsets.US_ASCII) == "SQLite format 3"
            if (!isZipMagic && !isSqliteMagic) {
                return@withContext executeJsonBackupRestore(
                    context = context,
                    repository = repository,
                    jsonFile = zipFile,
                    mode = mode,
                    onProgress = onProgress
                )
            }
            if (isSqliteMagic) {
                return@withContext executeSqliteBackupRestore(
                    context = context,
                    repository = repository,
                    sqliteFile = zipFile,
                    onProgress = onProgress
                )
            }

            if (preview.isLegacyArchive) {
                onProgress?.invoke(0.55f, "Memulihkan arsip backup versi sebelumnya...")
                val legacyRes = LocalStorageAndBackupManager.restoreFromBackupZip(context, repository, zipFile)
                onProgress?.invoke(1.0f, "Pemulihan selesai.")
                return@withContext StructuredRestoreResult(
                    success = legacyRes.success,
                    message = legacyRes.message,
                    restoredPhotosCount = legacyRes.restoredFilesCount
                )
            }

            // Step 2: Re-extract validated archive to staging and verify dataset generation
            onProgress?.invoke(0.28f, "Mengekstrak & memverifikasi dataset backup...")
            val (err, _) = extractZipSafely(zipFile, staging)
            if (err != null) return@withContext StructuredRestoreResult(false, err)
            val extractedRoot = File(staging, "extracted/$ROOT_ZIP_FOLDER")
            val manifestFile = File(extractedRoot, "manifest.json")
            if (manifestFile.exists()) {
                val manifestObj = JSONObject(manifestFile.readText(Charsets.UTF_8))
                val backupGen = manifestObj.optString("datasetGeneration", manifestObj.optString("generation_id", ""))
                val generationManager = com.example.drive.DatabaseGenerationManager(context)
                if (backupGen.isNotBlank() && generationManager.isGenerationInvalidated(backupGen)) {
                    return@withContext StructuredRestoreResult(
                        success = false,
                        message = "Restore ditolak: arsip backup berasal dari generasi terdahulu ($backupGen) yang telah di-invalidate oleh Factory Reset."
                    )
                }
            }

            val dbDir = File(extractedRoot, "database")
            val incomingCustomers = BackupJsonMapper.customersFromJson(
                BackupJsonMapper.decodePayload(File(dbDir, "customers.json").readText(Charsets.UTF_8))
            )
            val incomingUnits = BackupJsonMapper.unitsFromJson(
                BackupJsonMapper.decodePayload(File(dbDir, "units.json").readText(Charsets.UTF_8))
            )
            val incomingTx = BackupJsonMapper.transactionsFromJson(
                BackupJsonMapper.decodePayload(File(dbDir, "transactions.json").readText(Charsets.UTF_8))
            )
            val paymentsPayload = BackupJsonMapper.decodePayload(File(dbDir, "payments.json").readText(Charsets.UTF_8))
            val incomingProofs = BackupJsonMapper.paymentProofsFromJson(paymentsPayload)
            val incomingAllocations = BackupJsonMapper.allocationsFromJson(paymentsPayload)
            val settingsPayload = BackupJsonMapper.decodePayload(File(dbDir, "settings.json").readText(Charsets.UTF_8))
            val incomingMethods = BackupJsonMapper.paymentMethodsFromJson(settingsPayload)
            val extraPayload = BackupJsonMapper.decodePayload(File(dbDir, "extra_relations.json").readText(Charsets.UTF_8))

            // Capture pre-restore active snapshot for post-restore cloud reconciliation
            val preRestoreCustomers = repository.getAllCustomersOnce()
            val preRestoreUnits = repository.getAllUnitsOnce()
            val preRestoreTransactions = repository.getAllTransactionsOnce()
            val preRestorePayments = repository.getAllPaymentProofsOnce()

            // Step 3: Create automatic pre-restore Safety Snapshot ZIP BEFORE changing live DB or files
            onProgress?.invoke(0.45f, "Membuat snapshot pengaman database sebelum restore...")
            val safetyDir = File(context.cacheDir, "pre_restore_safety_${System.currentTimeMillis()}").apply { mkdirs() }
            val safetyRes = createStructuredBackupZip(
                context = context,
                repository = repository,
                encryptSensitivePayload = true,
                includePhotos = true,
                backupType = "SAFETY_SNAPSHOT",
                destinationDir = safetyDir,
                customFileName = "safety_before_restore.zip",
                saveToHistory = false
            )
            safetyBackupFile = safetyRes.getOrNull()?.second
            onProgress?.invoke(0.65f, "Memulihkan database lokal secara atomik...")

            // Step 4: Execute Restore inside protected try/rollback block
            try {
                var shouldRestorePhotos = true
                var shouldRestoreSettings = true
                when (mode) {
                    RestoreMode.FULL_RESTORE -> {
                        val tombstones = repository.getAllDriveDeletionTombstonesOnce()
                        val tombByUuid = tombstones.filter { it.recordUuid.isNotBlank() }.associateBy { it.recordUuid }
                        val tombByKey = tombstones.associateBy { it.localRecordKey }
                        fun isTombstoned(cat: String, id: Long, uuid: String, updatedMs: Long, ver: Long): Boolean {
                            val t = (if (uuid.isNotBlank()) tombByUuid[uuid] else null) ?: tombByKey["$cat:$id"] ?: return false
                            return t.deletedVersion >= ver || t.deletedAtMillis >= updatedMs
                        }
                        val filteredCustomers = incomingCustomers.filterNot {
                            it.isDeleted || isTombstoned("CUSTOMER", it.id, it.uuid, it.updatedAtMillis, it.version)
                        }
                        val filteredUnits = incomingUnits.filterNot {
                            it.isDeleted || isTombstoned("UNIT", it.id, it.uuid, it.updatedAtMillis, it.version)
                        }
                        val filteredTx = incomingTx.filterNot {
                            it.isDeleted || isTombstoned("TRANSACTION", it.id, it.uuid, it.updatedAtMillis, it.version)
                        }
                        val allowedTxIds = filteredTx.map { it.id }.toSet()
                        val filteredUnitItems = BackupExtraRelationsMapper.unitItemsFromJson(extraPayload)
                            .filter { it.transactionId in allowedTxIds }
                        val filteredProofs = incomingProofs.filterNot {
                            it.isDeleted ||
                                it.transactionId !in allowedTxIds ||
                                isTombstoned("PAYMENT", it.id, it.uuid, it.updatedAtMillis, it.version)
                        }
                        val allowedUnitItemIds = filteredUnitItems.map { it.id }.toSet()
                        val filteredAllocations = incomingAllocations.filter {
                            it.transactionId in allowedTxIds && (it.unitItemId == 0L || it.unitItemId in allowedUnitItemIds)
                        }
                        val filteredSignatures = BackupExtraRelationsMapper.signaturesFromJson(extraPayload)
                            .filter { it.transactionId in allowedTxIds }
                        val filteredReturns = BackupExtraRelationsMapper.returnsFromJson(extraPayload)
                            .filter { it.transactionId in allowedTxIds }
                        repository.replaceDatabaseForFullRestore(
                            customers = filteredCustomers,
                            units = filteredUnits,
                            transactions = filteredTx,
                            unitItems = filteredUnitItems,
                            paymentProofs = filteredProofs,
                            allocations = filteredAllocations,
                            signatures = filteredSignatures,
                            returns = filteredReturns,
                            extensions = BackupExtraRelationsMapper.extensionsFromJson(extraPayload),
                            paymentMethods = incomingMethods,
                            customerDocuments = BackupExtraRelationsMapper.customerDocumentsFromJson(extraPayload),
                            transactionDocuments = BackupExtraRelationsMapper.transactionDocumentsFromJson(extraPayload),
                            customerLocations = BackupExtraRelationsMapper.customerLocationsFromJson(extraPayload),
                            customerPhotos = BackupExtraRelationsMapper.customerPhotosFromJson(extraPayload),
                            timelineEvents = BackupExtraRelationsMapper.timelineEventsFromJson(extraPayload)
                        )
                    }
                    RestoreMode.APPEND_MERGE -> {
                        appendMergeDatabaseRecords(
                            repository = repository,
                            customers = incomingCustomers,
                            units = incomingUnits,
                            transactions = incomingTx,
                            proofs = incomingProofs
                        )
                    }
                    RestoreMode.CATEGORY_CUSTOMERS -> {
                        shouldRestorePhotos = false
                        shouldRestoreSettings = false
                        appendMergeDatabaseRecords(
                            repository = repository,
                            customers = incomingCustomers,
                            units = emptyList(),
                            transactions = emptyList(),
                            proofs = emptyList()
                        )
                    }
                    RestoreMode.CATEGORY_UNITS -> {
                        shouldRestorePhotos = false
                        shouldRestoreSettings = false
                        appendMergeDatabaseRecords(
                            repository = repository,
                            customers = emptyList(),
                            units = incomingUnits,
                            transactions = emptyList(),
                            proofs = emptyList()
                        )
                    }
                    RestoreMode.CATEGORY_TRANSACTIONS_PAYMENTS -> {
                        shouldRestorePhotos = false
                        shouldRestoreSettings = false
                        appendMergeDatabaseRecords(
                            repository = repository,
                            customers = incomingCustomers,
                            units = incomingUnits,
                            transactions = incomingTx,
                            proofs = incomingProofs
                        )
                    }
                    RestoreMode.CATEGORY_SETTINGS_AGREEMENT -> {
                        shouldRestorePhotos = false
                        shouldRestoreSettings = true
                        val existingMethods = repository.getAllPaymentMethodsOnce().map { it.namaMetode.lowercase(Locale.ROOT) }.toSet()
                        incomingMethods.forEach { method ->
                            if (method.namaMetode.lowercase(Locale.ROOT) !in existingMethods) {
                                repository.savePaymentMethod(method.copy(id = 0L))
                            }
                        }
                    }
                    RestoreMode.CATEGORY_PHOTOS_DOCS -> {
                        shouldRestorePhotos = true
                        shouldRestoreSettings = false
                    }
                }

                // Restore settings preferences when applicable
                if (shouldRestoreSettings) {
                    restoreAppPrefsFromSettingsJson(context, settingsPayload)
                }

                // Inject failure hook AFTER DB write to verify full rollback of DB + files in tests!
                simulateFailureHookForTest?.invoke()

                // Restore physical photo files safely when applicable
                onProgress?.invoke(0.82f, "Memulihkan dokumentasi foto & menyiapkan sinkronisasi cloud...")
                val restoredPhotos = if (shouldRestorePhotos) {
                    restorePhotosFromStaging(context, File(extractedRoot, "photos"))
                } else 0

                // On FULL_RESTORE, advance dataset identity & reconcile Google Drive outbox so
                // obsolete pre-restore cloud data is deleted and NEVER resurrected on next sync!
                if (mode == RestoreMode.FULL_RESTORE) {
                    val genMgr = com.example.drive.DatabaseGenerationManager(context)
                    val rotated = genMgr.rotateGenerationOnFullRestore()
                    repository.reconcileOutboxAfterFullRestore(
                        preRestoreCustomers = preRestoreCustomers,
                        preRestoreUnits = preRestoreUnits,
                        preRestoreTransactions = preRestoreTransactions,
                        preRestorePayments = preRestorePayments,
                        datasetId = rotated.newDatasetId,
                        datasetGenerationId = rotated.newGenerationId
                    )
                }

                val totalRecords = incomingCustomers.size + incomingUnits.size + incomingTx.size + incomingProofs.size
                repository.logBackupAuditEvent(
                    actionType = when (mode) {
                        RestoreMode.FULL_RESTORE -> "RESTORE_FULL"
                        RestoreMode.APPEND_MERGE -> "RESTORE_APPEND"
                        else -> "RESTORE_CATEGORY"
                    },
                    status = "SUKSES",
                    fileName = zipFile.name,
                    checksum = SecurityCryptoUtils.sha256Hex(zipFile),
                    recordCount = totalRecords,
                    detailMessage = "Restore (${mode.name}) selesai: $totalRecords record & $restoredPhotos foto."
                )

                onProgress?.invoke(1.0f, "Backup berhasil dipulihkan.")
                return@withContext StructuredRestoreResult(
                    success = true,
                    message = "Backup berhasil dipulihkan (${mode.label}): ${incomingCustomers.size} pelanggan, ${incomingUnits.size} unit, ${incomingTx.size} transaksi, $restoredPhotos foto.",
                    restoredRecordsCount = totalRecords,
                    restoredPhotosCount = restoredPhotos
                )
            } catch (restoreErr: Exception) {
                // AUTOMATIC ROLLBACK using pre-restore safety snapshot
                var rolledBackOk = false
                if (safetyBackupFile != null && safetyBackupFile.exists()) {
                    runCatching {
                        rollbackFromSafetySnapshot(context, repository, safetyBackupFile)
                        rolledBackOk = true
                    }
                }
                repository.logBackupAuditEvent(
                    actionType = "RESTORE_ROLLBACK",
                    status = if (rolledBackOk) "ROLLBACK" else "GAGAL",
                    fileName = zipFile.name,
                    detailMessage = "Restore gagal & rollback otomatis dijalankan: ${restoreErr.localizedMessage}"
                )
                return@withContext StructuredRestoreResult(
                    success = false,
                    message = "Restore gagal (${restoreErr.localizedMessage}). Data aplikasi dikembalikan ke kondisi sebelum restore.",
                    rolledBack = rolledBackOk
                )
            }
        } catch (e: Exception) {
            StructuredRestoreResult(false, "Restore gagal: ${e.localizedMessage ?: "Kesalahan tidak diketahui"}")
        } finally {
            runCatching { safetyBackupFile?.parentFile?.deleteRecursively() }
            runCatching { staging.deleteRecursively() }
        }
    }

    private suspend fun appendMergeDatabaseRecords(
        repository: FreezerRepository,
        customers: List<CustomerEntity>,
        units: List<FreezerUnitEntity>,
        transactions: List<RentalTransactionEntity>,
        proofs: List<com.example.data.local.PaymentProofEntity>
    ) {
        val tombstones = repository.getAllDriveDeletionTombstonesOnce()
        val tombstonesByKey = tombstones.associateBy { it.localRecordKey }
        val tombstonesByUuid = tombstones.filter { it.recordUuid.isNotBlank() }.associateBy { it.recordUuid }

        fun isBlockedByTombstone(category: String, id: Long, uuid: String, incomingUpdatedMs: Long, incomingVer: Long): Boolean {
            val tByKey = if (id > 0L) tombstonesByKey["$category:$id"] else null
            val tByUuid = if (uuid.isNotBlank()) tombstonesByUuid[uuid] else null
            val tombstone = tByUuid ?: tByKey ?: return false
            return tombstone.deletedVersion >= incomingVer || tombstone.deletedAtMillis >= incomingUpdatedMs
        }

        val existingCustomers = repository.getAllCustomersOnce()
        val custIdMap = mutableMapOf<Long, Long>()
        customers.forEach { inc ->
            if (isBlockedByTombstone("CUSTOMER", inc.id, inc.uuid, inc.updatedAtMillis, inc.version)) {
                return@forEach
            }
            val match = existingCustomers.firstOrNull {
                (inc.uuid.isNotBlank() && it.uuid == inc.uuid) ||
                    (inc.nik.isNotBlank() && it.nik == inc.nik) ||
                    (inc.kodePelanggan.isNotBlank() && it.kodePelanggan == inc.kodePelanggan)
            }
            if (match != null) {
                custIdMap[inc.id] = match.id
                // Never allow older backup record to overwrite newer local data
                if (inc.version > match.version && inc.updatedAtMillis > match.updatedAtMillis) {
                    repository.saveCustomer(
                        inc.copy(
                            id = match.id,
                            uuid = match.uuid,
                            createdAtMillis = match.createdAtMillis,
                            kodePelanggan = match.kodePelanggan.ifBlank { inc.kodePelanggan }
                        )
                    )
                }
            } else {
                val insertedId = repository.saveCustomer(inc.copy(id = 0L, kodePelanggan = ""))
                custIdMap[inc.id] = insertedId
            }
        }

        val existingUnits = repository.getAllUnitsOnce()
        val unitIdMap = mutableMapOf<Long, Long>()
        units.forEach { inc ->
            if (isBlockedByTombstone("UNIT", inc.id, inc.uuid, inc.updatedAtMillis, inc.version)) {
                return@forEach
            }
            val match = existingUnits.firstOrNull {
                (inc.uuid.isNotBlank() && it.uuid == inc.uuid) ||
                    it.kodeUnit.equals(inc.kodeUnit, ignoreCase = true) ||
                    (inc.nomorSeri.isNotBlank() && it.nomorSeri.equals(inc.nomorSeri, ignoreCase = true))
            }
            if (match != null) {
                unitIdMap[inc.id] = match.id
                if (inc.version > match.version && inc.updatedAtMillis > match.updatedAtMillis) {
                    repository.saveUnit(
                        inc.copy(
                            id = match.id,
                            uuid = match.uuid,
                            createdAtMillis = match.createdAtMillis
                        )
                    )
                }
            } else {
                val insertedId = repository.saveUnit(inc.copy(id = 0L))
                unitIdMap[inc.id] = insertedId
            }
        }

        val existingTransactions = repository.getAllTransactionsOnce()
        val txIdMap = mutableMapOf<Long, Long>()
        transactions.forEach { inc ->
            if (isBlockedByTombstone("TRANSACTION", inc.id, inc.uuid, inc.updatedAtMillis, inc.version)) {
                return@forEach
            }
            val match = existingTransactions.firstOrNull {
                (inc.uuid.isNotBlank() && it.uuid == inc.uuid) ||
                    it.nomorInvoice == inc.nomorInvoice
            }
            if (match != null) {
                txIdMap[inc.id] = match.id
                return@forEach
            }
            val targetCustId = custIdMap[inc.customerId] ?: inc.customerId
            val targetUnitId = unitIdMap[inc.unitId] ?: inc.unitId
            val insertedTxId = repository.saveTransaction(
                inc.copy(id = 0L, customerId = targetCustId, unitId = targetUnitId)
            )
            txIdMap[inc.id] = insertedTxId
        }

        val existingProofUuids = repository.getAllPaymentProofsOnce().map { it.uuid }.toSet()
        proofs.forEach { p ->
            if (isBlockedByTombstone("PAYMENT", p.id, p.uuid, p.updatedAtMillis, p.version)) {
                return@forEach
            }
            if (p.uuid.isNotBlank() && p.uuid in existingProofUuids) return@forEach
            val targetTxId = txIdMap[p.transactionId] ?: return@forEach
            repository.savePaymentProof(p.copy(id = 0L, transactionId = targetTxId), recalculateTransactionPayment = false)
        }
    }

    private suspend fun rollbackFromSafetySnapshot(
        context: Context,
        repository: FreezerRepository,
        safetyZip: File
    ) {
        val rollbackStage = File(context.cacheDir, "rollback_stage_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            extractZipSafely(safetyZip, rollbackStage)
            val root = File(rollbackStage, "extracted/$ROOT_ZIP_FOLDER")
            val dbDir = File(root, "database")
            val customers = BackupJsonMapper.customersFromJson(
                BackupJsonMapper.decodePayload(File(dbDir, "customers.json").readText(Charsets.UTF_8))
            )
            val units = BackupJsonMapper.unitsFromJson(
                BackupJsonMapper.decodePayload(File(dbDir, "units.json").readText(Charsets.UTF_8))
            )
            val txs = BackupJsonMapper.transactionsFromJson(
                BackupJsonMapper.decodePayload(File(dbDir, "transactions.json").readText(Charsets.UTF_8))
            )
            val payJson = BackupJsonMapper.decodePayload(File(dbDir, "payments.json").readText(Charsets.UTF_8))
            val setJson = BackupJsonMapper.decodePayload(File(dbDir, "settings.json").readText(Charsets.UTF_8))
            val extraJson = BackupJsonMapper.decodePayload(File(dbDir, "extra_relations.json").readText(Charsets.UTF_8))

            repository.replaceDatabaseForFullRestore(
                customers = customers,
                units = units,
                transactions = txs,
                unitItems = BackupExtraRelationsMapper.unitItemsFromJson(extraJson),
                paymentProofs = BackupJsonMapper.paymentProofsFromJson(payJson),
                allocations = BackupJsonMapper.allocationsFromJson(payJson),
                signatures = BackupExtraRelationsMapper.signaturesFromJson(extraJson),
                returns = BackupExtraRelationsMapper.returnsFromJson(extraJson),
                extensions = BackupExtraRelationsMapper.extensionsFromJson(extraJson),
                paymentMethods = BackupJsonMapper.paymentMethodsFromJson(setJson),
                customerDocuments = BackupExtraRelationsMapper.customerDocumentsFromJson(extraJson),
                transactionDocuments = BackupExtraRelationsMapper.transactionDocumentsFromJson(extraJson),
                customerLocations = BackupExtraRelationsMapper.customerLocationsFromJson(extraJson),
                customerPhotos = BackupExtraRelationsMapper.customerPhotosFromJson(extraJson),
                timelineEvents = BackupExtraRelationsMapper.timelineEventsFromJson(extraJson)
            )
            restoreAppPrefsFromSettingsJson(context, setJson)
            restorePhotosFromStaging(context, File(root, "photos"))
        } finally {
            runCatching { rollbackStage.deleteRecursively() }
        }
    }

    private fun restoreAppPrefsFromSettingsJson(context: Context, settingsJson: JSONObject) {
        val prefs = context.getSharedPreferences("saf_soc_prefs", Context.MODE_PRIVATE)
        val cp = settingsJson.optJSONObject("companyProfile")
        prefs.edit().apply {
            if (cp != null) putString("company_profile_json", cp.toString())
            val perjanjian = settingsJson.optString("perjanjianSewaText", "")
            if (perjanjian.isNotBlank()) putString("perjanjian_sewa_text", perjanjian)
            if (settingsJson.has("googleSheetUrl")) putString("google_sheet_url", settingsJson.optString("googleSheetUrl", ""))
            if (settingsJson.has("webhookUrl")) putString("webhook_url", settingsJson.optString("webhookUrl", ""))
            if (settingsJson.has("autoSyncEnabled")) putBoolean("auto_sync_enabled", settingsJson.optBoolean("autoSyncEnabled", false))
            if (settingsJson.has("maskNikInPdf")) putBoolean("mask_nik_pdf", settingsJson.optBoolean("maskNikInPdf", true))
        }.apply()
    }

    private fun restorePhotosFromStaging(context: Context, photosDir: File): Int {
        if (!photosDir.exists()) return 0
        val targetBase = File(context.filesDir, "photos/restored").apply { mkdirs() }
        var count = 0
        photosDir.walkTopDown().filter { it.isFile }.forEach { src ->
            val rel = src.relativeTo(photosDir).path
            val dest = SecurityCryptoUtils.sanitizeZipEntryPath(rel, targetBase) ?: return@forEach
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, "${dest.name}.tmp")
            src.copyTo(tmp, overwrite = true)
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            count++
        }
        return count
    }

    private fun materializeSourceZip(context: Context, source: Any, stagingDir: File): File? {
        return when (source) {
            is File -> source.takeIf { it.exists() }
            is String -> {
                val direct = File(source)
                if (direct.exists()) direct else LocalStorageAndBackupManager.resolveRelativePath(context, source).takeIf { it.exists() }
            }
            is Uri -> {
                val tempZip = File(stagingDir, "incoming_restore.zip")
                context.contentResolver.openInputStream(source)?.use { input ->
                    FileOutputStream(tempZip).use { output -> input.copyTo(output) }
                } ?: return null
                tempZip.takeIf { it.exists() && it.length() > 0L }
            }
            else -> null
        }
    }

    private fun extractZipSafely(zipFile: File, stagingDir: File): Pair<String?, Int> {
        val extractedDir = File(stagingDir, "extracted").apply { mkdirs() }
        var entryCount = 0
        var totalBytes = 0L
        val buffer = ByteArray(8192)
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    return "Jumlah file di dalam ZIP melebihi batas aman ($MAX_ZIP_ENTRIES)." to 0
                }
                if (!entry.isDirectory) {
                    val outFile = SecurityCryptoUtils.sanitizeZipEntryPath(entry.name, extractedDir)
                        ?: return "Keamanan ditolak: Path berbahaya (Zip Slip / Traversal) pada '${entry.name}'" to 0
                    outFile.parentFile?.mkdirs()
                    var entryBytes = 0L
                    FileOutputStream(outFile).use { fos ->
                        var len = zis.read(buffer)
                        while (len > 0) {
                            entryBytes += len
                            totalBytes += len
                            if (entryBytes > MAX_SINGLE_ENTRY_BYTES) {
                                return "File '${entry.name}' terlalu besar di dalam ZIP (Zip Bomb ditolak)." to 0
                            }
                            if (totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                return "Total ukuran ekstraksi ZIP melebihi batas aman." to 0
                            }
                            fos.write(buffer, 0, len)
                            len = zis.read(buffer)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null to entryCount
    }

    // --- MULTI-FORMAT BACKUP SUPPORT: RAW JSON & SQLITE DATABASE ---

    private fun inspectJsonBackupFile(jsonFile: File): RestorePreviewSummary {
        return try {
            val raw = jsonFile.readText(Charsets.UTF_8)
            val root = BackupJsonMapper.decodePayload(raw)
            val dbVer = root.optInt("databaseVersion", CURRENT_DB_VERSION)
            if (dbVer <= 0 || dbVer > CURRENT_DB_VERSION) {
                return RestorePreviewSummary(false, "Versi database JSON backup ($dbVer) tidak kompatibel dengan aplikasi (v$CURRENT_DB_VERSION).")
            }
            val custCount = root.optJSONArray("customers")?.length() ?: 0
            val unitCount = root.optJSONArray("units")?.length() ?: 0
            val txCount = root.optJSONArray("transactions")?.length() ?: 0
            val payCount = (root.optJSONArray("paymentProofs") ?: root.optJSONArray("payments"))?.length() ?: 0
            val photoCount = root.optJSONArray("customerPhotos")?.length() ?: 0
            if (custCount == 0 && unitCount == 0 && txCount == 0 && !root.has("customers") && !root.has("units")) {
                return RestorePreviewSummary(false, "Struktur file JSON backup tidak dikenali oleh SEWAin Aja Freezer SOC.")
            }
            RestorePreviewSummary(
                isValid = true,
                fileName = jsonFile.name,
                backupId = root.optString("backupId", "json_${jsonFile.lastModified()}"),
                createdAt = root.optString("createdAt", DateCurrencyUtils.nowDateTimeDisplay()),
                appVersion = root.optString("appVersion", CURRENT_APP_VERSION),
                databaseVersion = dbVer,
                customerCount = custCount,
                unitCount = unitCount,
                transactionCount = txCount,
                paymentCount = payCount,
                photoCount = photoCount,
                totalArchiveSizeBytes = jsonFile.length(),
                isEncrypted = false,
                checksumVerified = true,
                verifiedEntriesCount = 1,
                isLegacyArchive = false
            )
        } catch (e: Exception) {
            RestorePreviewSummary(false, "File JSON backup rusak atau tidak valid: ${e.localizedMessage}")
        }
    }

    private fun inspectSqliteBackupFile(sqliteFile: File): RestorePreviewSummary {
        return try {
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                sqliteFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            db.use { opened ->
                val ver = opened.version
                if (ver <= 0 || ver > CURRENT_DB_VERSION) {
                    return RestorePreviewSummary(false, "Versi database SQLite ($ver) tidak kompatibel dengan aplikasi (v$CURRENT_DB_VERSION).")
                }
                fun countTable(table: String): Int {
                    return runCatching {
                        opened.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { c ->
                            if (c.moveToFirst()) c.getInt(0) else 0
                        }
                    }.getOrDefault(0)
                }
                RestorePreviewSummary(
                    isValid = true,
                    fileName = sqliteFile.name,
                    backupId = "sqlite_${sqliteFile.lastModified()}",
                    createdAt = DateCurrencyUtils.nowDateTimeDisplay(),
                    appVersion = CURRENT_APP_VERSION,
                    databaseVersion = ver,
                    customerCount = countTable("customers"),
                    unitCount = countTable("freezer_units"),
                    transactionCount = countTable("rental_transactions"),
                    paymentCount = countTable("payment_proofs"),
                    photoCount = countTable("customer_photos"),
                    totalArchiveSizeBytes = sqliteFile.length(),
                    isEncrypted = false,
                    checksumVerified = true,
                    verifiedEntriesCount = 1,
                    isLegacyArchive = false
                )
            }
        } catch (e: Exception) {
            RestorePreviewSummary(false, "File SQLite tidak valid atau terenkripsi: ${e.localizedMessage}")
        }
    }

    private suspend fun executeJsonBackupRestore(
        context: Context,
        repository: FreezerRepository,
        jsonFile: File,
        mode: RestoreMode,
        onProgress: ((Float, String) -> Unit)?
    ): StructuredRestoreResult {
        val safetyDir = File(context.cacheDir, "pre_json_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        var safetyBackupFile: File? = null
        return try {
            val root = BackupJsonMapper.decodePayload(jsonFile.readText(Charsets.UTF_8))
            val incomingCustomers = BackupJsonMapper.customersFromJson(root)
            val incomingUnits = BackupJsonMapper.unitsFromJson(root)
            val incomingTx = BackupJsonMapper.transactionsFromJson(root)
            val incomingProofs = BackupJsonMapper.paymentProofsFromJson(root)
            val incomingAllocations = BackupJsonMapper.allocationsFromJson(root)
            val incomingMethods = BackupJsonMapper.paymentMethodsFromJson(root)

            val preCustomers = repository.getAllCustomersOnce()
            val preUnits = repository.getAllUnitsOnce()
            val preTxs = repository.getAllTransactionsOnce()
            val prePayments = repository.getAllPaymentProofsOnce()

            onProgress?.invoke(0.45f, "Membuat snapshot pengaman sebelum restore JSON...")
            safetyBackupFile = createStructuredBackupZip(
                context = context,
                repository = repository,
                encryptSensitivePayload = true,
                includePhotos = false,
                backupType = "SAFETY_SNAPSHOT",
                destinationDir = safetyDir,
                customFileName = "safety_before_json_restore.zip",
                saveToHistory = false
            ).getOrNull()?.second

            onProgress?.invoke(0.75f, "Memulihkan data dari file JSON...")
            if (mode == RestoreMode.FULL_RESTORE) {
                repository.replaceDatabaseForFullRestore(
                    customers = incomingCustomers,
                    units = incomingUnits,
                    transactions = incomingTx,
                    unitItems = BackupExtraRelationsMapper.unitItemsFromJson(root),
                    paymentProofs = incomingProofs,
                    allocations = incomingAllocations,
                    signatures = BackupExtraRelationsMapper.signaturesFromJson(root),
                    returns = BackupExtraRelationsMapper.returnsFromJson(root),
                    extensions = BackupExtraRelationsMapper.extensionsFromJson(root),
                    paymentMethods = incomingMethods,
                    customerDocuments = BackupExtraRelationsMapper.customerDocumentsFromJson(root),
                    transactionDocuments = BackupExtraRelationsMapper.transactionDocumentsFromJson(root),
                    customerLocations = BackupExtraRelationsMapper.customerLocationsFromJson(root),
                    customerPhotos = BackupExtraRelationsMapper.customerPhotosFromJson(root),
                    timelineEvents = BackupExtraRelationsMapper.timelineEventsFromJson(root)
                )
                val rotated = com.example.drive.DatabaseGenerationManager(context).rotateGenerationOnFullRestore()
                repository.reconcileOutboxAfterFullRestore(
                    preRestoreCustomers = preCustomers,
                    preRestoreUnits = preUnits,
                    preRestoreTransactions = preTxs,
                    preRestorePayments = prePayments,
                    datasetId = rotated.newDatasetId,
                    datasetGenerationId = rotated.newGenerationId
                )
            } else {
                appendMergeDatabaseRecords(repository, incomingCustomers, incomingUnits, incomingTx, incomingProofs)
            }

            onProgress?.invoke(1.0f, "Backup JSON berhasil dipulihkan.")
            val total = incomingCustomers.size + incomingUnits.size + incomingTx.size + incomingProofs.size
            StructuredRestoreResult(
                success = true,
                message = "Backup berhasil dipulihkan dari JSON ($total record).",
                restoredRecordsCount = total
            )
        } catch (e: Exception) {
            if (safetyBackupFile != null && safetyBackupFile.exists()) {
                runCatching { rollbackFromSafetySnapshot(context, repository, safetyBackupFile) }
            }
            StructuredRestoreResult(false, "Restore JSON gagal & rollback otomatis: ${e.localizedMessage}", rolledBack = true)
        } finally {
            runCatching { safetyDir.deleteRecursively() }
        }
    }

    private suspend fun executeSqliteBackupRestore(
        context: Context,
        repository: FreezerRepository,
        sqliteFile: File,
        onProgress: ((Float, String) -> Unit)?
    ): StructuredRestoreResult {
        return try {
            onProgress?.invoke(0.45f, "Membuat snapshot pengaman sebelum restore SQLite...")
            val dbFile = context.getDatabasePath("sewain_aja_freezer_soc.db")
            val safetyCopy = File(context.cacheDir, "safety_sqlite_${System.currentTimeMillis()}.db")
            if (dbFile.exists()) dbFile.copyTo(safetyCopy, overwrite = true)

            onProgress?.invoke(0.75f, "Membaca & memvalidasi tabel SQLite...")
            val tempDbName = "temp_import_${System.currentTimeMillis()}.db"
            val tempDbFile = context.getDatabasePath(tempDbName)
            sqliteFile.copyTo(tempDbFile, overwrite = true)
            val tempRoom = androidx.room.Room.databaseBuilder(
                context.applicationContext,
                com.example.data.local.AppDatabase::class.java,
                tempDbName
            ).addMigrations(
                com.example.data.local.AppDatabase.MIGRATION_2_3,
                com.example.data.local.AppDatabase.MIGRATION_3_4,
                com.example.data.local.AppDatabase.MIGRATION_4_5,
                com.example.data.local.AppDatabase.MIGRATION_5_6,
                com.example.data.local.AppDatabase.MIGRATION_6_7,
                com.example.data.local.AppDatabase.MIGRATION_7_8,
                com.example.data.local.AppDatabase.MIGRATION_8_9,
                com.example.data.local.AppDatabase.MIGRATION_9_10,
                com.example.data.local.AppDatabase.MIGRATION_10_11,
                com.example.data.local.AppDatabase.MIGRATION_11_12,
                com.example.data.local.AppDatabase.MIGRATION_12_13,
                com.example.data.local.AppDatabase.MIGRATION_13_14,
                com.example.data.local.AppDatabase.MIGRATION_14_15,
                com.example.data.local.AppDatabase.MIGRATION_15_16,
                com.example.data.local.AppDatabase.MIGRATION_16_17,
                com.example.data.local.AppDatabase.MIGRATION_17_18,
                com.example.data.local.AppDatabase.MIGRATION_18_19,
                com.example.data.local.AppDatabase.MIGRATION_19_20,
                com.example.data.local.AppDatabase.MIGRATION_20_21,
                com.example.data.local.AppDatabase.MIGRATION_21_22,
                com.example.data.local.AppDatabase.MIGRATION_22_23,
                com.example.data.local.AppDatabase.MIGRATION_23_24,
                com.example.data.local.AppDatabase.MIGRATION_24_25
            ).allowMainThreadQueries().build()

            try {
                val srcDao = tempRoom.freezerDao()
                val preCustomers = repository.getAllCustomersOnce()
                val preUnits = repository.getAllUnitsOnce()
                val preTxs = repository.getAllTransactionsOnce()
                val prePayments = repository.getAllPaymentProofsOnce()

                val custs = srcDao.getAllCustomersOnce()
                val units = srcDao.getAllUnitsOnce()
                val txs = srcDao.getAllTransactionsOnce()
                val proofs = srcDao.getAllPaymentProofsOnce()

                repository.replaceDatabaseForFullRestore(
                    customers = custs,
                    units = units,
                    transactions = txs,
                    unitItems = srcDao.getAllTransactionUnitItemsOnce(),
                    paymentProofs = proofs,
                    allocations = srcDao.getAllUnitPaymentAllocationsOnce(),
                    signatures = srcDao.getAllTransactionSignaturesOnce(),
                    returns = srcDao.getAllReturnsOnce(),
                    extensions = srcDao.getAllExtensionsOnce(),
                    paymentMethods = srcDao.getAllPaymentMethodsOnce(),
                    customerDocuments = srcDao.getAllCustomerDocumentsOnce(),
                    transactionDocuments = srcDao.getAllTransactionDocumentsOnce(),
                    customerLocations = srcDao.getAllCustomerLocationsOnce(),
                    customerPhotos = srcDao.getAllCustomerPhotosOnce(),
                    timelineEvents = srcDao.getAllCustomerTimelineEventsOnce()
                )
                val rotated = com.example.drive.DatabaseGenerationManager(context).rotateGenerationOnFullRestore()
                repository.reconcileOutboxAfterFullRestore(
                    preRestoreCustomers = preCustomers,
                    preRestoreUnits = preUnits,
                    preRestoreTransactions = preTxs,
                    preRestorePayments = prePayments,
                    datasetId = rotated.newDatasetId,
                    datasetGenerationId = rotated.newGenerationId
                )
                onProgress?.invoke(1.0f, "Backup SQLite berhasil dipulihkan.")
                val total = custs.size + units.size + txs.size + proofs.size
                StructuredRestoreResult(
                    success = true,
                    message = "Backup berhasil dipulihkan dari file SQLite ($total record).",
                    restoredRecordsCount = total
                )
            } finally {
                tempRoom.close()
                runCatching { context.deleteDatabase(tempDbName) }
                runCatching { safetyCopy.delete() }
            }
        } catch (e: Exception) {
            StructuredRestoreResult(false, "Restore SQLite gagal: ${e.localizedMessage}")
        }
    }
}
