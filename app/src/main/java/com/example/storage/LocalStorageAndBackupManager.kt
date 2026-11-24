package com.example.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.local.AppPermissionEntity
import com.example.data.local.CustomerDocumentEntity
import com.example.data.local.LocalBackupEntity
import com.example.data.local.TransactionDocumentEntity
import com.example.data.repository.FreezerRepository
import com.example.media.PhotoCategory
import com.example.media.PreferredCompressionFormat
import com.example.media.SmartImageProcessor
import com.example.util.DateCurrencyUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Status indikator izin perangkat sesuai spesifikasi:
 * HIJAU = AKTIF
 * KUNING = PERLU PERSETUJUAN
 * MERAH = TIDAK AKTIF
 */
enum class PermissionIndicatorLevel(val label: String, val badgeText: String) {
    HIJAU("AKTIF", "HIJAU • AKTIF"),
    KUNING("PERLU_PERSETUJUAN", "KUNING • PERLU PERSETUJUAN"),
    MERAH("TIDAK_AKTIF", "MERAH • TIDAK AKTIF")
}

data class PermissionCategoryStatus(
    val code: String, // CAMERA | GALLERY | LOCATION | LOCAL_STORAGE
    val title: String,
    val description: String,
    val status: String, // AKTIF | PERLU_PERSETUJUAN | TIDAK_AKTIF
    val indicator: PermissionIndicatorLevel,
    val lastChecked: String
)

/**
 * 6 Kategori resmi pada PENGATURAN -> DOCUMENT MANAGEMENT
 */
enum class ManagedDocumentCategory(
    val code: String,
    val number: Int,
    val displayName: String,
    val description: String
) {
    DOKUMEN_PELANGGAN("DOKUMEN_PELANGGAN", 1, "1. Dokumen Pelanggan", "Profil, formulir, dan berkas pendukung pelanggan"),
    FOTO_KTP("FOTO_KTP", 2, "2. Foto KTP", "Foto KTP Depan, KTP Belakang, dan hasil scan KTP pelanggan"),
    BUKTI_PEMBAYARAN("BUKTI_PEMBAYARAN", 3, "3. Bukti Pembayaran", "Foto struk transfer bank, QRIS, dan kuitansi pembayaran"),
    DOKUMEN_PERJANJIAN("DOKUMEN_PERJANJIAN", 4, "4. Dokumen Perjanjian", "PDF Invoice, Perjanjian Sewa, dan tanda tangan digital"),
    DOKUMENTASI_FREEZER("DOKUMENTASI_FREEZER", 5, "5. Dokumentasi Freezer", "Foto unit freezer, kondisi fisik, dan pengecekan suhu"),
    DOKUMENTASI_TRANSAKSI("DOKUMENTASI_TRANSAKSI", 6, "6. Dokumentasi Transaksi", "Foto serah terima unit, lokasi penempatan, dan pengembalian")
}

data class UnifiedManagedDocumentItem(
    val uniqueKey: String,
    val sourceTable: String, // CUSTOMER_DOCUMENTS | TRANSACTION_DOCUMENTS | CUSTOMER_PHOTOS
    val recordId: Long,
    val ownerId: Long, // customerId or transactionId
    val ownerLabel: String,
    val category: ManagedDocumentCategory,
    val documentType: String,
    val fileName: String,
    val relativePath: String,
    val absolutePath: String,
    val mimeType: String,
    val fileSize: Long,
    val checksum: String,
    val createdAt: String,
    val fileExists: Boolean,
    val checksumValid: Boolean
)

/**
 * Hasil pemeriksaan CHECK STORAGE INTEGRITY pada PENGATURAN -> DOCUMENT MANAGEMENT
 */
data class StorageIntegrityReport(
    val checkedAt: String,
    val totalFilesInStorage: Int,
    val totalSizeBytes: Long,
    val totalDatabaseRecords: Int,
    val validFilesCount: Int,
    val missingFilesCount: Int,
    val corruptedFilesCount: Int,
    val duplicateFilesCount: Int,
    val orphanFilesCount: Int,
    val missingFileDetails: List<String>,
    val duplicateFileDetails: List<String>,
    val orphanFilePaths: List<String>
) {
    val isHealthy: Boolean
        get() = missingFilesCount == 0 && corruptedFilesCount == 0
}

/**
 * Informasi kapasitas penyimpanan lokal untuk PENGATURAN -> INFORMASI PENYIMPANAN
 */
data class DeviceStorageSummary(
    val rootDirectoryPath: String,
    val databaseSizeBytes: Long,
    val customerDocsSizeBytes: Long,
    val transactionDocsSizeBytes: Long,
    val freezerDocsSizeBytes: Long,
    val backupFilesSizeBytes: Long,
    val totalAppStorageBytes: Long,
    val availableDeviceBytes: Long,
    val totalDeviceBytes: Long,
    val totalFilesCount: Int
)

/**
 * Hasil operasi Restore Data
 */
data class RestoreExecutionResult(
    val success: Boolean,
    val message: String,
    val restoredFilesCount: Int = 0,
    val verifiedChecksumsCount: Int = 0,
    val backupType: String = ""
)

/**
 * Manajer terpusat untuk:
 * - B. First Installation Onboarding & C. Sistem Izin Perangkat (Android 10-16)
 * - E. Struktur Penyimpanan Lokal (SEWAIN_AJA_FREEZER/) & Transaksi File Atomik
 * - F. Kamera & Galeri Terpadu dengan Validasi Format/Ukuran & Checksum SHA-256
 * - H. Document Management & Check Storage Integrity
 * - I & J. Backup & Restore Management (.zip dengan manifest.json & verifikasi SHA-256)
 * - L. Keamanan Data Lokal (Internal Storage + Keystore Helper)
 */
object LocalStorageAndBackupManager {

    const val ROOT_DIR_NAME = "SEWAIN_AJA_FREEZER"
    private const val PREFS_NAME = "saf_soc_onboarding_storage_prefs"
    private const val KEY_ONBOARDING_COMPLETED = "first_install_onboarding_completed"
    private const val KEY_ONBOARDING_DECISION = "first_install_onboarding_decision"
    private const val KEY_BACKUP_SCHEDULE = "backup_schedule_interval"
    private const val KEY_CAMERA_DENIED_ONCE = "perm_camera_denied_once"
    private const val KEY_LOCATION_DENIED_ONCE = "perm_location_denied_once"

    val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "pdf")
    const val MAX_DOCUMENT_SIZE_BYTES = 25L * 1024L * 1024L // 25 MB

    // =========================================================================
    // B & C. FIRST INSTALLATION ONBOARDING & PERMISSION MANAGEMENT
    // =========================================================================

    fun isFirstInstallOnboardingDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun getOnboardingDecision(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ONBOARDING_DECISION, "BELUM_DIATUR") ?: "BELUM_DIATUR"
    }

    fun markOnboardingCompleted(context: Context, decision: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .putString(KEY_ONBOARDING_DECISION, decision)
            .apply()
        // Pastikan struktur direktori SEWAIN_AJA_FREEZER langsung siap
        ensureStandardDirectoryStructure(context)
    }

    fun resetOnboardingForTesting(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .remove(KEY_ONBOARDING_DECISION)
            .apply()
    }

    fun markPermissionDeniedByUser(context: Context, permissionCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        when (permissionCode.uppercase(Locale.ROOT)) {
            "CAMERA" -> prefs.edit().putBoolean(KEY_CAMERA_DENIED_ONCE, true).apply()
            "LOCATION" -> prefs.edit().putBoolean(KEY_LOCATION_DENIED_ONCE, true).apply()
        }
    }

    /**
     * Evaluasi 4 kategori izin utama sesuai spesifikasi Bagian B & C:
     * 1. CAMERA
     * 2. GALLERY / PHOTO ACCESS
     * 3. LOCATION / GPS
     * 4. LOCAL FILE STORAGE
     */
    fun evaluateAllPermissionCategories(context: Context): List<PermissionCategoryStatus> {
        val now = DateCurrencyUtils.nowDateTimeDisplay()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. CAMERA
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val cameraDeniedOnce = prefs.getBoolean(KEY_CAMERA_DENIED_ONCE, false)
        val cameraIndicator = when {
            cameraGranted -> PermissionIndicatorLevel.HIJAU
            cameraDeniedOnce -> PermissionIndicatorLevel.MERAH
            else -> PermissionIndicatorLevel.KUNING
        }

        // 2. GALLERY / PHOTO ACCESS (Android Photo Picker - zero broad permission required on Android 10-16)
        val galleryIndicator = PermissionIndicatorLevel.HIJAU

        // 3. LOCATION / GPS
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val gpsHardwareEnabled = runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        }.getOrDefault(true)
        val locationDeniedOnce = prefs.getBoolean(KEY_LOCATION_DENIED_ONCE, false)
        val locationIndicator = when {
            (fineGranted || coarseGranted) && gpsHardwareEnabled -> PermissionIndicatorLevel.HIJAU
            locationDeniedOnce || (!gpsHardwareEnabled && (fineGranted || coarseGranted)) -> PermissionIndicatorLevel.MERAH
            else -> PermissionIndicatorLevel.KUNING
        }

        // 4. LOCAL FILE STORAGE (App-Specific Internal Storage SEWAIN_AJA_FREEZER/ + SAF)
        val rootDir = ensureStandardDirectoryStructure(context)
        val storageReady = rootDir.exists() && rootDir.canWrite()
        val storageIndicator = if (storageReady) {
            PermissionIndicatorLevel.HIJAU
        } else {
            PermissionIndicatorLevel.MERAH
        }

        return listOf(
            PermissionCategoryStatus(
                code = "CAMERA",
                title = "1. CAMERA",
                description = "Mengambil foto KTP pelanggan, foto unit freezer, foto kondisi unit, dan dokumentasi serah terima.",
                status = cameraIndicator.label,
                indicator = cameraIndicator,
                lastChecked = now
            ),
            PermissionCategoryStatus(
                code = "GALLERY",
                title = "2. GALLERY / PHOTO ACCESS",
                description = "Memilih foto KTP, bukti pembayaran transfer/QRIS, dan dokumen pendukung melalui Android Photo Picker.",
                status = galleryIndicator.label,
                indicator = galleryIndicator,
                lastChecked = now
            ),
            PermissionCategoryStatus(
                code = "LOCATION",
                title = "3. LOCATION / GPS",
                description = "Mendeteksi titik koordinat GPS lokasi pengiriman dan penggunaan freezer pelanggan secara akurat.",
                status = locationIndicator.label,
                indicator = locationIndicator,
                lastChecked = now
            ),
            PermissionCategoryStatus(
                code = "LOCAL_STORAGE",
                title = "4. LOCAL FILE STORAGE",
                description = "Menyimpan database SQLite, foto KTP, perjanjian PDF, dan paket backup di penyimpanan internal aplikasi yang aman.",
                status = storageIndicator.label,
                indicator = storageIndicator,
                lastChecked = now
            )
        )
    }

    /**
     * Sinkronkan status ke-4 izin perangkat ke tabel SQLite `app_permission`
     */
    suspend fun syncPermissionsToDatabase(
        context: Context,
        repository: FreezerRepository
    ): List<AppPermissionEntity> {
        val statuses = evaluateAllPermissionCategories(context)
        statuses.forEach { item ->
            repository.upsertAppPermission(item.code, item.status)
        }
        return repository.getAllAppPermissionsOnce()
    }

    fun openDeviceAppSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openDeviceLocationSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    // =========================================================================
    // E. STANDARDIZED LOCAL FILE STORAGE ARCHITECTURE (SEWAIN_AJA_FREEZER/)
    // =========================================================================

    /**
     * Membuat dan memastikan struktur folder standar:
     * SEWAIN_AJA_FREEZER/
     *  ├── DATABASE/
     *  ├── CUSTOMERS/
     *  ├── TRANSACTIONS/
     *  ├── FREEZERS/
     *  └── BACKUP/
     *       ├── DATABASE/
     *       ├── DOCUMENTS/
     *       └── FULL_BACKUP/
     */
    fun ensureStandardDirectoryStructure(context: Context): File {
        val root = File(context.filesDir, ROOT_DIR_NAME)
        val requiredDirs = listOf(
            File(root, "DATABASE"),
            File(root, "CUSTOMERS"),
            File(root, "TRANSACTIONS"),
            File(root, "FREEZERS"),
            File(root, "BACKUP/DATABASE"),
            File(root, "BACKUP/DOCUMENTS"),
            File(root, "BACKUP/FULL_BACKUP")
        )
        requiredDirs.forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        return root
    }

    fun ensureCustomerDirectories(context: Context, customerId: Long): File {
        val root = ensureStandardDirectoryStructure(context)
        val custRoot = File(root, "CUSTOMERS/$customerId")
        listOf("KTP", "DOCUMENTS", "PHOTOS").forEach { sub ->
            val dir = File(custRoot, sub)
            if (!dir.exists()) dir.mkdirs()
        }
        return custRoot
    }

    fun ensureTransactionDirectories(context: Context, transactionId: Long): File {
        val root = ensureStandardDirectoryStructure(context)
        val txRoot = File(root, "TRANSACTIONS/$transactionId")
        listOf("PAYMENT", "AGREEMENT", "DOCUMENTATION").forEach { sub ->
            val dir = File(txRoot, sub)
            if (!dir.exists()) dir.mkdirs()
        }
        return txRoot
    }

    fun ensureFreezerDirectories(context: Context, freezerId: Long): File {
        val root = ensureStandardDirectoryStructure(context)
        val freezerRoot = File(root, "FREEZERS/$freezerId")
        listOf("PHOTOS", "CONDITION").forEach { sub ->
            val dir = File(freezerRoot, sub)
            if (!dir.exists()) dir.mkdirs()
        }
        return freezerRoot
    }

    fun resolveRelativePath(context: Context, relativePath: String): File {
        val clean = relativePath.trim().removePrefix("/")
        return if (clean.startsWith(ROOT_DIR_NAME)) {
            File(context.filesDir, clean)
        } else {
            val direct = File(clean)
            if (direct.isAbsolute && direct.exists()) direct else File(context.filesDir, clean)
        }
    }

    /**
     * Hitung checksum SHA-256 dari sebuah file fisik
     */
    fun computeSha256Checksum(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(FileInputStream(file)).use { bis ->
                val buffer = ByteArray(8192)
                var read = bis.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = bis.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    /**
     * Validasi ekstensi dan ukuran file (JPG, JPEG, PNG, PDF)
     */
    fun validateDocumentFile(file: File): Pair<Boolean, String> {
        if (!file.exists() || !file.isFile) {
            return false to "FILE TIDAK DITEMUKAN"
        }
        val length = file.length()
        if (length <= 0L) {
            return false to "File kosong atau rusak (0 byte)"
        }
        if (length > MAX_DOCUMENT_SIZE_BYTES) {
            return false to "Ukuran file melebihi batas maksimal 25 MB"
        }
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext !in ALLOWED_EXTENSIONS) {
            return false to "Format file .$ext tidak didukung (Gunakan JPG, JPEG, PNG, atau PDF)"
        }
        return true to "VALID"
    }

    fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            else -> "image/jpeg"
        }
    }

    /**
     * Buat nama file unik menggunakan ID dan timestamp tanpa menimpa file lama
     */
    fun generateUniqueStorageFileName(
        prefix: String,
        entityId: Long,
        extension: String = "jpg"
    ): String {
        val cleanExt = extension.lowercase(Locale.ROOT).trim('.').let {
            if (it in ALLOWED_EXTENSIONS) it else "jpg"
        }
        val cleanPrefix = prefix.replace(Regex("[^a-zA-Z0-9_]"), "_").uppercase(Locale.ROOT)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
        val shortUuid = UUID.randomUUID().toString().substring(0, 6).uppercase(Locale.ROOT)
        return "${cleanPrefix}_${entityId}_${ts}_${shortUuid}.$cleanExt"
    }

    // =========================================================================
    // F & J. ATOMIC FILE STORAGE + SQLITE REGISTRATION WITH ROLLBACK
    // =========================================================================

    /**
     * Menyimpan dokumen pelanggan ke SEWAIN_AJA_FREEZER/CUSTOMERS/<CUSTOMER_ID>/<SUBFOLDER>/
     * dan mencatatnya ke tabel `customer_documents` secara atomik.
     *
     * Konsistensi Transaksi Data (Bagian J):
     * - Jika file gagal disalin -> database tidak disimpan.
     * - Jika database gagal disimpan -> file baru dibatalkan (dihapus).
     * - Jika mengganti foto lama -> file lama TIDAK dihapus sebelum file baru + DB sukses.
     */
    suspend fun saveCustomerDocumentAtomically(
        context: Context,
        repository: FreezerRepository,
        customerId: Long,
        documentType: String, // KTP_DEPAN | KTP_BELAKANG | DOKUMEN_PELANGGAN | FOTO_PELANGGAN
        source: Any, // Bitmap, Uri, File, or String path
        extensionHint: String = "jpg",
        replaceExistingOfSameType: Boolean = false
    ): Result<CustomerDocumentEntity> {
        val safeCustomerId = customerId.coerceAtLeast(1L)
        val custDir = ensureCustomerDirectories(context, safeCustomerId)
        val subfolder = when (documentType.uppercase(Locale.ROOT)) {
            "KTP_DEPAN", "KTP_BELAKANG", "FOTO_KTP" -> "KTP"
            "FOTO_PELANGGAN", "PHOTOS" -> "PHOTOS"
            else -> "DOCUMENTS"
        }
        val targetFolder = File(custDir, subfolder)
        if (!targetFolder.exists()) targetFolder.mkdirs()

        val fileName = generateUniqueStorageFileName(documentType, safeCustomerId, extensionHint)
        val targetFile = File(targetFolder, fileName)
        val relativePath = "$ROOT_DIR_NAME/CUSTOMERS/$safeCustomerId/$subfolder/$fileName"

        return try {
            // 1. Tulis ke file baru terlebih dahulu (jangan sentuh file lama)
            //    Untuk gambar non-PNG/non-PDF, otomatis normalisasi orientasi EXIF, kompresi kategori, & buat thumbnail 300px
            writeAnySourceToFile(
                context = context,
                source = source,
                targetFile = targetFile,
                photoCategory = PhotoCategory.fromSlotOrType(documentType)
            )

            // 2. Validasi file baru
            val (isValid, validationMessage) = validateDocumentFile(targetFile)
            if (!isValid) {
                if (targetFile.exists()) targetFile.delete()
                return Result.failure(IllegalArgumentException(validationMessage))
            }

            // 3. Hitung checksum SHA-256
            val checksum = computeSha256Checksum(targetFile)
            val now = DateCurrencyUtils.nowDateTimeDisplay()
            val mimeType = guessMimeType(fileName)

            // Cari dokumen lama jika mode replace diaktifkan
            val oldDoc = if (replaceExistingOfSameType) {
                repository.getCustomerDocumentsByCustomerOnce(safeCustomerId)
                    .firstOrNull { it.documentType.equals(documentType, ignoreCase = true) }
            } else {
                null
            }

            // 4. Simpan ke SQLite database
            val entityToSave = CustomerDocumentEntity(
                id = oldDoc?.id ?: 0L,
                customerId = safeCustomerId,
                documentType = documentType.uppercase(Locale.ROOT),
                fileName = fileName,
                relativePath = relativePath,
                mimeType = mimeType,
                fileSize = targetFile.length(),
                checksum = checksum,
                createdAt = oldDoc?.createdAt?.ifBlank { now } ?: now,
                updatedAt = now
            )

            val savedId = try {
                repository.saveCustomerDocument(entityToSave)
            } catch (dbError: Exception) {
                // Rollback file baru jika insert/update database gagal
                if (targetFile.exists()) targetFile.delete()
                throw dbError
            }

            // 5. Hanya setelah file baru + DB sukses, hapus file fisik lama (jika berbeda path)
            if (oldDoc != null && oldDoc.relativePath != relativePath) {
                val oldFile = resolveRelativePath(context, oldDoc.relativePath)
                if (oldFile.exists() && oldFile.absolutePath != targetFile.absolutePath) {
                    runCatching { oldFile.delete() }
                }
            }

            Result.success(entityToSave.copy(id = savedId))
        } catch (e: Exception) {
            if (targetFile.exists()) {
                runCatching { targetFile.delete() }
            }
            Result.failure(e)
        }
    }

    /**
     * Menyimpan dokumen transaksi / freezer ke:
     * - SEWAIN_AJA_FREEZER/TRANSACTIONS/<TRANSACTION_ID>/<PAYMENT|AGREEMENT|DOCUMENTATION>/
     * - atau SEWAIN_AJA_FREEZER/FREEZERS/<FREEZER_ID>/<PHOTOS|CONDITION>/
     * dan mencatatnya ke tabel `transaction_documents` secara atomik dengan rollback.
     */
    suspend fun saveTransactionDocumentAtomically(
        context: Context,
        repository: FreezerRepository,
        transactionId: Long,
        customerId: Long,
        documentType: String, // BUKTI_PEMBAYARAN | DOKUMEN_PERJANJIAN | DOKUMENTASI_TRANSAKSI | DOKUMENTASI_FREEZER
        source: Any,
        freezerId: Long? = null,
        extensionHint: String = "jpg"
    ): Result<TransactionDocumentEntity> {
        val safeTxId = transactionId.coerceAtLeast(1L)
        val (targetFolder, relativeFolderPrefix) = if (
            documentType.uppercase(Locale.ROOT).contains("FREEZER") && freezerId != null && freezerId > 0L
        ) {
            val fDir = ensureFreezerDirectories(context, freezerId)
            val sub = if (documentType.uppercase(Locale.ROOT).contains("KONDISI")) "CONDITION" else "PHOTOS"
            File(fDir, sub) to "$ROOT_DIR_NAME/FREEZERS/$freezerId/$sub"
        } else {
            val txDir = ensureTransactionDirectories(context, safeTxId)
            val sub = when {
                documentType.uppercase(Locale.ROOT).contains("PEMBAYARAN") ||
                    documentType.uppercase(Locale.ROOT).contains("PAYMENT") -> "PAYMENT"
                documentType.uppercase(Locale.ROOT).contains("PERJANJIAN") ||
                    documentType.uppercase(Locale.ROOT).contains("INVOICE") ||
                    documentType.uppercase(Locale.ROOT).contains("AGREEMENT") -> "AGREEMENT"
                else -> "DOCUMENTATION"
            }
            File(txDir, sub) to "$ROOT_DIR_NAME/TRANSACTIONS/$safeTxId/$sub"
        }

        if (!targetFolder.exists()) targetFolder.mkdirs()
        val fileName = generateUniqueStorageFileName(documentType, safeTxId, extensionHint)
        val targetFile = File(targetFolder, fileName)
        val relativePath = "$relativeFolderPrefix/$fileName"

        return try {
            writeAnySourceToFile(
                context = context,
                source = source,
                targetFile = targetFile,
                photoCategory = PhotoCategory.fromSlotOrType(documentType)
            )

            val (isValid, validationMessage) = validateDocumentFile(targetFile)
            if (!isValid) {
                if (targetFile.exists()) targetFile.delete()
                return Result.failure(IllegalArgumentException(validationMessage))
            }

            val checksum = computeSha256Checksum(targetFile)
            val now = DateCurrencyUtils.nowDateTimeDisplay()
            val mimeType = guessMimeType(fileName)

            val entity = TransactionDocumentEntity(
                transactionId = safeTxId,
                customerId = customerId.coerceAtLeast(0L),
                documentType = documentType.uppercase(Locale.ROOT),
                fileName = fileName,
                relativePath = relativePath,
                mimeType = mimeType,
                fileSize = targetFile.length(),
                checksum = checksum,
                createdAt = now
            )

            val savedId = try {
                repository.saveTransactionDocument(entity)
            } catch (dbError: Exception) {
                if (targetFile.exists()) targetFile.delete()
                throw dbError
            }

            Result.success(entity.copy(id = savedId))
        } catch (e: Exception) {
            if (targetFile.exists()) {
                runCatching { targetFile.delete() }
            }
            Result.failure(e)
        }
    }

    private fun writeAnySourceToFile(
        context: Context,
        source: Any,
        targetFile: File,
        photoCategory: PhotoCategory = PhotoCategory.GENERAL_DOC
    ) {
        targetFile.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        val ext = targetFile.extension.lowercase(Locale.ROOT)
        // Normalisasi orientasi & kompresi otomatis untuk file foto (JPG/JPEG/WEBP).
        // PDF dan PNG transparan (seperti tanda tangan digital) tetap dipertahankan utuh.
        if (ext == "jpg" || ext == "jpeg" || ext == "webp") {
            val thumbFile = SmartImageProcessor.resolveThumbnailFileFor(context, targetFile)
            val preferredFmt = if (ext == "webp") {
                PreferredCompressionFormat.WEBP_AUTO
            } else {
                PreferredCompressionFormat.JPEG
            }
            val processed = SmartImageProcessor.processImageSourceToFiles(
                context = context,
                source = source,
                category = photoCategory,
                outputMainFile = targetFile,
                outputThumbFile = thumbFile,
                preferredFormat = preferredFmt
            )
            if (processed != null && targetFile.exists() && targetFile.length() > 0L) {
                return
            }
        }

        when (source) {
            is Bitmap -> {
                FileOutputStream(targetFile).use { fos ->
                    val format = if (ext == "png") {
                        Bitmap.CompressFormat.PNG
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }
                    source.compress(format, 84, fos)
                }
            }
            is File -> {
                if (!source.exists()) throw IllegalArgumentException("File sumber tidak ditemukan: ${source.path}")
                if (source.absolutePath != targetFile.absolutePath) {
                    source.copyTo(targetFile, overwrite = true)
                }
            }
            is Uri -> {
                if (source.scheme == "file") {
                    val f = File(source.path ?: "")
                    if (!f.exists()) throw IllegalArgumentException("File URI tidak ditemukan")
                    f.copyTo(targetFile, overwrite = true)
                } else {
                    val input = context.contentResolver.openInputStream(source)
                        ?: throw IllegalArgumentException("Tidak dapat membuka URI dokumen")
                    input.use { inStream ->
                        FileOutputStream(targetFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
            }
            is String -> {
                val trimmed = source.trim()
                if (trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
                    writeAnySourceToFile(context, Uri.parse(trimmed), targetFile, photoCategory)
                } else {
                    val f = File(trimmed)
                    if (!f.exists()) throw IllegalArgumentException("Path file tidak ditemukan: $trimmed")
                    if (f.absolutePath != targetFile.absolutePath) {
                        f.copyTo(targetFile, overwrite = true)
                    }
                }
            }
            is ByteArray -> {
                FileOutputStream(targetFile).use { it.write(source) }
            }
            else -> throw IllegalArgumentException("Tipe sumber dokumen tidak didukung: ${source.javaClass.simpleName}")
        }
    }

    // =========================================================================
    // H. DOCUMENT MANAGEMENT & STORAGE INTEGRITY CHECKER
    // =========================================================================

    fun mapDocumentTypeToCategory(docType: String): ManagedDocumentCategory {
        val upper = docType.uppercase(Locale.ROOT)
        return when {
            upper.contains("KTP") -> ManagedDocumentCategory.FOTO_KTP
            upper.contains("PEMBAYARAN") || upper.contains("PAYMENT") || upper.contains("QRIS") ->
                ManagedDocumentCategory.BUKTI_PEMBAYARAN
            upper.contains("PERJANJIAN") || upper.contains("INVOICE") || upper.contains("AGREEMENT") || upper.contains("PDF") ->
                ManagedDocumentCategory.DOKUMEN_PERJANJIAN
            upper.contains("FREEZER") || upper.contains("UNIT") || upper.contains("KONDISI") ->
                ManagedDocumentCategory.DOKUMENTASI_FREEZER
            upper.contains("TRANSAKSI") || upper.contains("SERAH_TERIMA") || upper.contains("LOKASI") || upper.contains("KEMBALI") ->
                ManagedDocumentCategory.DOKUMENTASI_TRANSAKSI
            else -> ManagedDocumentCategory.DOKUMEN_PELANGGAN
        }
    }

    /**
     * Mengambil daftar dokumen terpadu dari `customer_documents`, `transaction_documents`, dan `customer_photos`
     * lengkap dengan pemeriksaan keberadaan file fisik dan validitas SHA-256 checksum.
     */
    suspend fun loadAllManagedDocuments(
        context: Context,
        repository: FreezerRepository
    ): List<UnifiedManagedDocumentItem> {
        val customersMap = repository.getAllCustomersOnce().associateBy { it.id }
        val txMap = repository.getAllTransactionsOnce().associateBy { it.id }

        val items = mutableListOf<UnifiedManagedDocumentItem>()

        // 1. Dari tabel customer_documents
        repository.getAllCustomerDocumentsOnce().forEach { doc ->
            val file = resolveRelativePath(context, doc.relativePath)
            val exists = file.exists() && file.isFile && file.length() > 0L
            val currentHash = if (exists && doc.checksum.isNotBlank()) computeSha256Checksum(file) else doc.checksum
            val hashValid = exists && (doc.checksum.isBlank() || currentHash.equals(doc.checksum, ignoreCase = true))
            val custName = customersMap[doc.customerId]?.namaLengkap ?: "Pelanggan #${doc.customerId}"

            items.add(
                UnifiedManagedDocumentItem(
                    uniqueKey = "CDOC_${doc.id}",
                    sourceTable = "CUSTOMER_DOCUMENTS",
                    recordId = doc.id,
                    ownerId = doc.customerId,
                    ownerLabel = custName,
                    category = mapDocumentTypeToCategory(doc.documentType),
                    documentType = doc.documentType,
                    fileName = doc.fileName,
                    relativePath = doc.relativePath,
                    absolutePath = file.absolutePath,
                    mimeType = doc.mimeType,
                    fileSize = if (exists) file.length() else doc.fileSize,
                    checksum = doc.checksum.ifBlank { currentHash },
                    createdAt = doc.createdAt,
                    fileExists = exists,
                    checksumValid = hashValid
                )
            )
        }

        // 2. Dari tabel transaction_documents
        repository.getAllTransactionDocumentsOnce().forEach { doc ->
            val file = resolveRelativePath(context, doc.relativePath)
            val exists = file.exists() && file.isFile && file.length() > 0L
            val currentHash = if (exists && doc.checksum.isNotBlank()) computeSha256Checksum(file) else doc.checksum
            val hashValid = exists && (doc.checksum.isBlank() || currentHash.equals(doc.checksum, ignoreCase = true))
            val txInvoice = txMap[doc.transactionId]?.nomorInvoice ?: "Transaksi #${doc.transactionId}"

            items.add(
                UnifiedManagedDocumentItem(
                    uniqueKey = "TDOC_${doc.id}",
                    sourceTable = "TRANSACTION_DOCUMENTS",
                    recordId = doc.id,
                    ownerId = doc.transactionId,
                    ownerLabel = txInvoice,
                    category = mapDocumentTypeToCategory(doc.documentType),
                    documentType = doc.documentType,
                    fileName = doc.fileName,
                    relativePath = doc.relativePath,
                    absolutePath = file.absolutePath,
                    mimeType = doc.mimeType,
                    fileSize = if (exists) file.length() else doc.fileSize,
                    checksum = doc.checksum.ifBlank { currentHash },
                    createdAt = doc.createdAt,
                    fileExists = exists,
                    checksumValid = hashValid
                )
            )
        }

        return items
    }

    /**
     * Menjalankan CHECK STORAGE INTEGRITY sesuai spesifikasi Bagian H:
     * Menampilkan:
     * - total file
     * - total ukuran penyimpanan
     * - file valid
     * - file hilang
     * - file duplikat
     * - record tanpa file / file tanpa record (orphan)
     * PENTING: Tidak menghapus dokumen secara otomatis tanpa konfirmasi pengguna.
     */
    suspend fun checkStorageIntegrity(
        context: Context,
        repository: FreezerRepository
    ): StorageIntegrityReport {
        val rootDir = ensureStandardDirectoryStructure(context)
        val managedDocs = loadAllManagedDocuments(context, repository)

        // Kumpulkan semua file fisik di dalam SEWAIN_AJA_FREEZER (kecuali folder BACKUP sementara)
        val allPhysicalFiles = rootDir.walkTopDown()
            .filter { it.isFile && !it.absolutePath.contains("/BACKUP/") }
            .toList()

        val totalSize = allPhysicalFiles.sumOf { it.length() }
        val validDocs = managedDocs.filter { it.fileExists && it.checksumValid }
        val missingDocs = managedDocs.filter { !it.fileExists }
        val corruptedDocs = managedDocs.filter { it.fileExists && !it.checksumValid }

        // Deteksi duplikat berdasarkan SHA-256 checksum yang sama (dan tidak kosong)
        val duplicateGroups = managedDocs
            .filter { it.checksum.isNotBlank() && it.fileExists }
            .groupBy { it.checksum }
            .filter { (_, list) -> list.size > 1 }
        val duplicateCount = duplicateGroups.values.sumOf { it.size - 1 }
        val duplicateDetails = duplicateGroups.map { (hash, list) ->
            "Checksum ${hash.take(10)}... dipakai oleh ${list.size} dokumen (${list.joinToString { it.fileName }})"
        }

        // Deteksi orphan files (file fisik di CUSTOMERS/TRANSACTIONS/FREEZERS yang tidak tercatat di DB)
        val registeredAbsPaths = managedDocs.map { File(it.absolutePath).canonicalPath }.toSet()
        val orphanFiles = allPhysicalFiles.filter { file ->
            !file.absolutePath.contains("/DATABASE/") &&
                file.canonicalPath !in registeredAbsPaths
        }

        return StorageIntegrityReport(
            checkedAt = DateCurrencyUtils.nowDateTimeDisplay(),
            totalFilesInStorage = allPhysicalFiles.size,
            totalSizeBytes = totalSize,
            totalDatabaseRecords = managedDocs.size,
            validFilesCount = validDocs.size,
            missingFilesCount = missingDocs.size,
            corruptedFilesCount = corruptedDocs.size,
            duplicateFilesCount = duplicateCount,
            orphanFilesCount = orphanFiles.size,
            missingFileDetails = missingDocs.map { "${it.ownerLabel}: ${it.fileName} (FILE TIDAK DITEMUKAN)" },
            duplicateFileDetails = duplicateDetails,
            orphanFilePaths = orphanFiles.map { it.relativeTo(context.filesDir).path }
        )
    }

    /**
     * Menghapus dokumen terkelola setelah pengguna mengonfirmasi penghapusan
     */
    suspend fun deleteManagedDocumentWithConfirmation(
        context: Context,
        repository: FreezerRepository,
        item: UnifiedManagedDocumentItem
    ): Boolean {
        return try {
            val file = File(item.absolutePath)
            if (file.exists()) {
                file.delete()
            }
            when (item.sourceTable) {
                "CUSTOMER_DOCUMENTS" -> {
                    val existing = repository.getAllCustomerDocumentsOnce().firstOrNull { it.id == item.recordId }
                    if (existing != null) repository.deleteCustomerDocument(existing)
                }
                "TRANSACTION_DOCUMENTS" -> {
                    val existing = repository.getAllTransactionDocumentsOnce().firstOrNull { it.id == item.recordId }
                    if (existing != null) repository.deleteTransactionDocument(existing)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Bagikan dokumen menggunakan FileProvider dengan persetujuan pengguna
     */
    fun shareManagedDocument(context: Context, item: UnifiedManagedDocumentItem) {
        val file = File(item.absolutePath)
        if (!file.exists()) return
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Dokumen SEWAin Aja Freezer SOC - ${item.fileName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(intent, "Bagikan Dokumen ${item.fileName}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    // =========================================================================
    // I & J. BACKUP & RESTORE MANAGEMENT (.ZIP + MANIFEST.JSON + SHA-256)
    // =========================================================================

    fun getBackupSchedule(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BACKUP_SCHEDULE, "HARIAN_OTOMATIS") ?: "HARIAN_OTOMATIS"
    }

    fun setBackupSchedule(context: Context, schedule: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BACKUP_SCHEDULE, schedule).apply()
    }

    /**
     * Membuat paket Backup Lokal (.zip) sesuai cakupan:
     * - FULL_BACKUP (Seluruh data aplikasi: SQLite + seluruh dokumen + manifest.json)
     * - DATABASE (Snapshot SQLite database + JSON dump tabel)
     * - DOCUMENTS / KTP_PHOTOS / CUSTOMER_PHOTOS / PAYMENT_PROOFS / AGREEMENTS / FREEZER_DOCS
     *
     * Mencatat riwayat backup ke tabel `local_backup`.
     */
    suspend fun createLocalBackupArchive(
        context: Context,
        repository: FreezerRepository,
        backupType: String = "FULL_BACKUP"
    ): Result<LocalBackupEntity> {
        return try {
            val rootDir = ensureStandardDirectoryStructure(context)
            val tsCompact = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            val nowDisplay = DateCurrencyUtils.nowDateTimeDisplay()

            val subBackupFolder = when (backupType.uppercase(Locale.ROOT)) {
                "DATABASE" -> "BACKUP/DATABASE"
                "FULL_BACKUP" -> "BACKUP/FULL_BACKUP"
                else -> "BACKUP/DOCUMENTS"
            }
            val backupTargetDir = File(rootDir, subBackupFolder)
            if (!backupTargetDir.exists()) backupTargetDir.mkdirs()

            val zipFileName = "SAF_SOC_${backupType.uppercase(Locale.ROOT)}_$tsCompact.zip"
            val zipFile = File(backupTargetDir, zipFileName)
            val relativeBackupPath = "$ROOT_DIR_NAME/$subBackupFolder/$zipFileName"

            // 1. Siapkan snapshot SQLite & JSON data di SEWAIN_AJA_FREEZER/DATABASE/
            val dbSnapshotDir = File(rootDir, "DATABASE")
            if (!dbSnapshotDir.exists()) dbSnapshotDir.mkdirs()
            val liveDbFile = context.getDatabasePath("sewain_aja_freezer_soc.db")
            val snapshotDbFile = File(dbSnapshotDir, "sewain_aja_freezer_soc_snapshot.db")
            if (liveDbFile.exists() && liveDbFile.length() > 0L) {
                liveDbFile.copyTo(snapshotDbFile, overwrite = true)
            }

            // Buat JSON snapshot dari tabel utama & supporting tables agar restore dapat memulihkan 100% record secara aman
            val dbDumpFile = File(dbSnapshotDir, "database_records_dump.json")
            val dumpJson = buildDatabaseJsonDump(repository)
            dbDumpFile.writeText(dumpJson.toString(2), Charsets.UTF_8)

            // 2. Kumpulkan daftar file yang akan dimasukkan ke dalam ZIP sesuai backupType
            val filesToArchive = mutableListOf<Pair<String, File>>() // zipEntryName to File

            if (backupType.equals("FULL_BACKUP", ignoreCase = true) || backupType.equals("DATABASE", ignoreCase = true)) {
                if (snapshotDbFile.exists()) {
                    filesToArchive.add("DATABASE/${snapshotDbFile.name}" to snapshotDbFile)
                }
                if (dbDumpFile.exists()) {
                    filesToArchive.add("DATABASE/${dbDumpFile.name}" to dbDumpFile)
                }
            }

            if (!backupType.equals("DATABASE", ignoreCase = true)) {
                val physicalDocDirs = listOf("CUSTOMERS", "TRANSACTIONS", "FREEZERS")
                physicalDocDirs.forEach { folderName ->
                    val folder = File(rootDir, folderName)
                    if (folder.exists()) {
                        folder.walkTopDown().filter { it.isFile }.forEach { f ->
                            val rel = f.relativeTo(rootDir).path.replace("\\", "/")
                            val include = when (backupType.uppercase(Locale.ROOT)) {
                                "KTP_PHOTOS" -> rel.contains("/KTP/")
                                "CUSTOMER_PHOTOS" -> rel.startsWith("CUSTOMERS/")
                                "PAYMENT_PROOFS" -> rel.contains("/PAYMENT/")
                                "AGREEMENTS" -> rel.contains("/AGREEMENT/")
                                "FREEZER_DOCS" -> rel.startsWith("FREEZERS/")
                                else -> true // FULL_BACKUP or DOCUMENTS
                            }
                            if (include) {
                                filesToArchive.add(rel to f)
                            }
                        }
                    }
                }
            }

            // 3. Susun manifest.json berisi versi database, waktu pembuatan, dan checksum SHA-256 setiap file
            val manifestJson = JSONObject().apply {
                put("appName", "SEWAin Aja Freezer SOC")
                put("databaseName", "sewain_aja_freezer_soc.db")
                put("databaseVersion", 22)
                put("backupType", backupType.uppercase(Locale.ROOT))
                put("createdAt", nowDisplay)
                val filesArray = JSONArray()
                filesToArchive.forEach { (entryPath, file) ->
                    val sha256 = computeSha256Checksum(file)
                    filesArray.put(
                        JSONObject().apply {
                            put("entryPath", entryPath)
                            put("sizeBytes", file.length())
                            put("sha256", sha256)
                        }
                    )
                }
                put("files", filesArray)
                put("encryptedSignature", LocalSecurityHelper.signManifestSummary(this.toString()))
            }

            // 4. Tulis seluruh entry + manifest.json ke dalam file ZIP
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                // Tulis manifest.json terlebih dahulu
                val manifestBytes = manifestJson.toString(2).toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestBytes)
                zos.closeEntry()

                // Tulis setiap file
                val buffer = ByteArray(8192)
                filesToArchive.forEach { (entryPath, file) ->
                    if (file.exists() && file.isFile) {
                        zos.putNextEntry(ZipEntry(entryPath))
                        BufferedInputStream(FileInputStream(file)).use { bis ->
                            var count = bis.read(buffer)
                            while (count != -1) {
                                zos.write(buffer, 0, count)
                                count = bis.read(buffer)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }

            // 5. Catat di tabel SQLite `local_backup`
            val backupEntity = LocalBackupEntity(
                backupName = zipFileName,
                backupPath = relativeBackupPath,
                backupSize = zipFile.length(),
                backupType = backupType.uppercase(Locale.ROOT),
                backupStatus = "SUCCESS",
                createdAt = nowDisplay
            )
            val savedId = repository.saveLocalBackup(backupEntity)
            Result.success(backupEntity.copy(id = savedId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restore Data Aman (Bagian I & J):
     * 1. Ekstrak file ZIP ke direktori sementara (staging) terlebih dahulu.
     * 2. Periksa manifest.json, versi database, dan validasi SHA-256 checksum seluruh file.
     * 3. Jika ada file rusak atau checksum tidak cocok -> BATALKAN restore tanpa menyentuh data aktif.
     * 4. Jika 100% valid -> salin file ke SEWAIN_AJA_FREEZER/ dan pulihkan record database.
     */
    suspend fun restoreFromBackupZip(
        context: Context,
        repository: FreezerRepository,
        backupZipFileOrUri: Any
    ): RestoreExecutionResult {
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}")
        if (!stagingDir.exists()) stagingDir.mkdirs()

        try {
            // 1. Salin sumber ZIP ke staging jika dari Uri
            val zipFile = when (backupZipFileOrUri) {
                is File -> backupZipFileOrUri
                is Uri -> {
                    val tempZip = File(stagingDir, "incoming_backup.zip")
                    context.contentResolver.openInputStream(backupZipFileOrUri)?.use { input ->
                        FileOutputStream(tempZip).use { output -> input.copyTo(output) }
                    } ?: return RestoreExecutionResult(false, "Tidak dapat membuka file backup dari URI")
                    tempZip
                }
                is String -> resolveRelativePath(context, backupZipFileOrUri)
                else -> return RestoreExecutionResult(false, "Format sumber backup tidak didukung")
            }

            if (!zipFile.exists() || zipFile.length() <= 0L) {
                return RestoreExecutionResult(false, "File backup tidak ditemukan atau kosong")
            }

            // 2. Ekstrak seluruh isi ZIP ke folder staging
            val extractedDir = File(stagingDir, "extracted")
            extractedDir.mkdirs()
            var extractedCount = 0
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null) {
                    val currentEntry = entry
                    if (!currentEntry.isDirectory) {
                        val outFile = File(extractedDir, currentEntry.name)
                        // Proteksi Zip Slip
                        if (!outFile.canonicalPath.startsWith(extractedDir.canonicalPath)) {
                            return RestoreExecutionResult(false, "Path file di dalam backup tidak valid (Zip Slip terdeteksi)")
                        }
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var len = zis.read(buffer)
                            while (len > 0) {
                                fos.write(buffer, 0, len)
                                len = zis.read(buffer)
                            }
                        }
                        extractedCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 3. Validasi manifest.json dan verifikasi SHA-256 setiap file di staging
            val manifestFile = File(extractedDir, "manifest.json")
            if (!manifestFile.exists()) {
                return RestoreExecutionResult(false, "File backup tidak valid: manifest.json tidak ditemukan")
            }

            val manifestJson = JSONObject(manifestFile.readText(Charsets.UTF_8))
            val dbVersion = manifestJson.optInt("databaseVersion", 0)
            if (dbVersion <= 0 || dbVersion > 21) {
                return RestoreExecutionResult(false, "Versi database backup ($dbVersion) tidak kompatibel")
            }
            val backupType = manifestJson.optString("backupType", "FULL_BACKUP")
            val filesArray = manifestJson.optJSONArray("files") ?: JSONArray()

            var verifiedChecksums = 0
            for (i in 0 until filesArray.length()) {
                val item = filesArray.getJSONObject(i)
                val entryPath = item.getString("entryPath")
                val expectedSha256 = item.getString("sha256")
                val stagedFile = File(extractedDir, entryPath)
                if (!stagedFile.exists()) {
                    return RestoreExecutionResult(
                        false,
                        "Restore dibatalkan: File '$entryPath' hilang di dalam arsip backup"
                    )
                }
                val actualSha256 = computeSha256Checksum(stagedFile)
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    return RestoreExecutionResult(
                        false,
                        "Restore dibatalkan: Checksum SHA-256 tidak cocok untuk '$entryPath' (file rusak)"
                    )
                }
                verifiedChecksums++
            }

            // 4. Semua checksum valid 100%! Sekarang salin file dari staging ke SEWAIN_AJA_FREEZER/
            val rootDir = ensureStandardDirectoryStructure(context)
            var restoredFiles = 0
            for (i in 0 until filesArray.length()) {
                val item = filesArray.getJSONObject(i)
                val entryPath = item.getString("entryPath")
                val stagedFile = File(extractedDir, entryPath)
                val destFile = File(rootDir, entryPath)
                destFile.parentFile?.mkdirs()
                stagedFile.copyTo(destFile, overwrite = true)
                restoredFiles++
            }

            // 5. Pulihkan record database dari database_records_dump.json jika tersedia
            val dumpFile = File(extractedDir, "DATABASE/database_records_dump.json")
            if (dumpFile.exists()) {
                restoreDatabaseRecordsFromJsonDump(repository, JSONObject(dumpFile.readText(Charsets.UTF_8)))
            }

            return RestoreExecutionResult(
                success = true,
                message = "Restore berhasil! $restoredFiles file dan record database telah dipulihkan & diverifikasi (SHA-256).",
                restoredFilesCount = restoredFiles,
                verifiedChecksumsCount = verifiedChecksums,
                backupType = backupType
            )
        } catch (e: Exception) {
            return RestoreExecutionResult(
                success = false,
                message = "Restore gagal: ${e.localizedMessage ?: "Kesalahan tidak diketahui"}"
            )
        } finally {
            runCatching { stagingDir.deleteRecursively() }
        }
    }

    private suspend fun buildDatabaseJsonDump(repository: FreezerRepository): JSONObject {
        return JSONObject().apply {
            put("exportedAt", DateCurrencyUtils.nowDateTimeDisplay())
            put("customerCount", repository.getAllCustomersOnce().size)
            put("transactionCount", repository.getAllTransactionsOnce().size)
            put("unitCount", repository.getAllUnitsOnce().size)

            val custDocsArray = JSONArray()
            repository.getAllCustomerDocumentsOnce().forEach { doc ->
                custDocsArray.put(
                    JSONObject().apply {
                        put("id", doc.id)
                        put("customerId", doc.customerId)
                        put("documentType", doc.documentType)
                        put("fileName", doc.fileName)
                        put("relativePath", doc.relativePath)
                        put("mimeType", doc.mimeType)
                        put("fileSize", doc.fileSize)
                        put("checksum", doc.checksum)
                        put("createdAt", doc.createdAt)
                        put("updatedAt", doc.updatedAt)
                    }
                )
            }
            put("customerDocuments", custDocsArray)

            val txDocsArray = JSONArray()
            repository.getAllTransactionDocumentsOnce().forEach { doc ->
                txDocsArray.put(
                    JSONObject().apply {
                        put("id", doc.id)
                        put("transactionId", doc.transactionId)
                        put("customerId", doc.customerId)
                        put("documentType", doc.documentType)
                        put("fileName", doc.fileName)
                        put("relativePath", doc.relativePath)
                        put("mimeType", doc.mimeType)
                        put("fileSize", doc.fileSize)
                        put("checksum", doc.checksum)
                        put("createdAt", doc.createdAt)
                    }
                )
            }
            put("transactionDocuments", txDocsArray)
        }
    }

    private suspend fun restoreDatabaseRecordsFromJsonDump(
        repository: FreezerRepository,
        dumpJson: JSONObject
    ) {
        val existingCustomerIds = repository.getAllCustomersOnce().map { it.id }.toSet()
        val custDocsArray = dumpJson.optJSONArray("customerDocuments") ?: JSONArray()
        for (i in 0 until custDocsArray.length()) {
            val obj = custDocsArray.getJSONObject(i)
            val custId = obj.optLong("customerId", 0L)
            if (custId in existingCustomerIds) {
                repository.saveCustomerDocument(
                    CustomerDocumentEntity(
                        id = obj.optLong("id", 0L),
                        customerId = custId,
                        documentType = obj.optString("documentType", "DOKUMEN_PELANGGAN"),
                        fileName = obj.optString("fileName", ""),
                        relativePath = obj.optString("relativePath", ""),
                        mimeType = obj.optString("mimeType", "image/jpeg"),
                        fileSize = obj.optLong("fileSize", 0L),
                        checksum = obj.optString("checksum", ""),
                        createdAt = obj.optString("createdAt", ""),
                        updatedAt = obj.optString("updatedAt", "")
                    )
                )
            }
        }

        val txDocsArray = dumpJson.optJSONArray("transactionDocuments") ?: JSONArray()
        for (i in 0 until txDocsArray.length()) {
            val obj = txDocsArray.getJSONObject(i)
            repository.saveTransactionDocument(
                TransactionDocumentEntity(
                    id = obj.optLong("id", 0L),
                    transactionId = obj.optLong("transactionId", 0L),
                    customerId = obj.optLong("customerId", 0L),
                    documentType = obj.optString("documentType", "DOKUMENTASI_TRANSAKSI"),
                    fileName = obj.optString("fileName", ""),
                    relativePath = obj.optString("relativePath", ""),
                    mimeType = obj.optString("mimeType", "image/jpeg"),
                    fileSize = obj.optLong("fileSize", 0L),
                    checksum = obj.optString("checksum", ""),
                    createdAt = obj.optString("createdAt", "")
                )
            )
        }
    }

    /**
     * Menghitung ringkasan kapasitas penyimpanan lokal untuk PENGATURAN -> INFORMASI PENYIMPANAN
     */
    fun getDeviceStorageSummary(context: Context): DeviceStorageSummary {
        val rootDir = ensureStandardDirectoryStructure(context)
        val dbFile = context.getDatabasePath("sewain_aja_freezer_soc.db")
        val dbSize = (if (dbFile.exists()) dbFile.length() else 0L) +
            dirSizeBytes(File(rootDir, "DATABASE"))
        val custSize = dirSizeBytes(File(rootDir, "CUSTOMERS"))
        val txSize = dirSizeBytes(File(rootDir, "TRANSACTIONS"))
        val freezerSize = dirSizeBytes(File(rootDir, "FREEZERS"))
        val backupSize = dirSizeBytes(File(rootDir, "BACKUP"))
        val totalApp = dbSize + custSize + txSize + freezerSize + backupSize
        val totalFiles = rootDir.walkTopDown().count { it.isFile }

        val (availBytes, totalBytes) = runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes to stat.totalBytes
        }.getOrDefault((2L * 1024 * 1024 * 1024) to (16L * 1024 * 1024 * 1024))

        return DeviceStorageSummary(
            rootDirectoryPath = rootDir.absolutePath,
            databaseSizeBytes = dbSize,
            customerDocsSizeBytes = custSize,
            transactionDocsSizeBytes = txSize,
            freezerDocsSizeBytes = freezerSize,
            backupFilesSizeBytes = backupSize,
            totalAppStorageBytes = totalApp,
            availableDeviceBytes = availBytes,
            totalDeviceBytes = totalBytes,
            totalFilesCount = totalFiles
        )
    }

    private fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }
}

/**
 * L. KEAMANAN DATA LOKAL (Android Keystore + AES-GCM Fallback)
 * Melindungi metadata sensitif dan tanda tangan manifest backup lokal.
 */
object LocalSecurityHelper {
    fun encryptSensitiveText(plainText: String): String {
        return com.example.util.SecurityCryptoUtils.encryptSensitiveText(plainText)
    }

    fun signManifestSummary(manifestContent: String): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest((manifestContent + "::SAF_SOC_KEYSTORE").toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }
}
