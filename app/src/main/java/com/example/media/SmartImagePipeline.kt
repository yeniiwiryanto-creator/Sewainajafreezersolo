package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.data.local.CloudPhotoJobEntity
import com.example.ocr.KtpImageProcessor
import com.example.ocr.KtpImageQualityReport
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 3 Pilihan Kualitas Kompresi Gambar Global (Settings -> Pengaturan Kualitas Foto).
 * Catatan: Kualitas & resolusi merupakan batas maksimum (tidak memaksa ukuran/distorsi).
 * Foto KTP selalu menggunakan profil OCR khusus (max 2000 px, JPEG 88-92%).
 */
enum class ImageQualityPreset(
    val key: String,
    val title: String,
    val description: String,
    val maxDimensionPx: Int,
    val jpegQuality: Int,
    val minQualityFloor: Int,
    val estimatedSizeRange: String
) {
    HEMAT(
        key = "HEMAT",
        title = "Hemat",
        description = "Resolusi maks 1280 px, kualitas JPEG 70%. Prioritas ukuran file kecil & hemat kuota.",
        maxDimensionPx = 1280,
        jpegQuality = 70,
        minQualityFloor = 68,
        estimatedSizeRange = "~70–160 KB / foto"
    ),
    STANDAR(
        key = "STANDAR",
        title = "Standar (Default)",
        description = "Resolusi maks 1600 px, kualitas JPEG 82%. Keseimbangan optimal antara ketajaman & ukuran.",
        maxDimensionPx = 1600,
        jpegQuality = 82,
        minQualityFloor = 78,
        estimatedSizeRange = "~160–350 KB / foto"
    ),
    HD(
        key = "HD",
        title = "HD",
        description = "Resolusi maks 2400 px, kualitas JPEG 90%. Prioritas detail tinggi untuk dokumentasi.",
        maxDimensionPx = 2400,
        jpegQuality = 90,
        minQualityFloor = 85,
        estimatedSizeRange = "~380–850 KB / foto"
    );

    companion object {
        fun fromKey(raw: String?): ImageQualityPreset {
            return entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: STANDAR
        }
    }
}

data class ImageQualitySettings(
    val defaultPreset: ImageQualityPreset = ImageQualityPreset.STANDAR,
    val autoCompressionEnabled: Boolean = true
)

data class EffectiveCompressionProfile(
    val maxDimensionPx: Int,
    val minAcceptableDimensionPx: Int,
    val targetQuality: Int,
    val minQualityFloor: Int,
    val targetMaxKb: Int,
    val forceDocumentLandscape: Boolean
)

/**
 * Pengelola preferensi kualitas foto & pencatatan statistik penghematan kompresi aktual.
 * Seluruh data disimpan permanen pada SharedPreferences `saf_soc_prefs`.
 */
object ImageQualityPreferencesManager {
    private const val PREFS_NAME = "saf_soc_prefs"
    private const val KEY_QUALITY_PRESET = "img_quality_preset"
    private const val KEY_AUTO_COMPRESSION = "img_auto_compression_enabled"
    private const val KEY_RAW_BYTES_TOTAL = "img_analytics_raw_bytes_total"
    private const val KEY_COMPRESSED_BYTES_TOTAL = "img_analytics_compressed_bytes_total"
    private const val KEY_PROCESSED_COUNT = "img_analytics_processed_count"

    fun loadSettings(context: Context): ImageQualitySettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val preset = ImageQualityPreset.fromKey(prefs.getString(KEY_QUALITY_PRESET, ImageQualityPreset.STANDAR.key))
        val autoComp = prefs.getBoolean(KEY_AUTO_COMPRESSION, true)
        return ImageQualitySettings(defaultPreset = preset, autoCompressionEnabled = autoComp)
    }

    fun saveSettings(context: Context, settings: ImageQualitySettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_QUALITY_PRESET, settings.defaultPreset.key)
            .putBoolean(KEY_AUTO_COMPRESSION, settings.autoCompressionEnabled)
            .apply()
    }

    fun recordCompressionMetrics(
        context: Context,
        rawSourceBytes: Long,
        compressedBytes: Long
    ) {
        if (rawSourceBytes <= 0L || compressedBytes <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val safeRaw = maxOf(rawSourceBytes, compressedBytes)
        val prevRaw = prefs.getLong(KEY_RAW_BYTES_TOTAL, 0L)
        val prevComp = prefs.getLong(KEY_COMPRESSED_BYTES_TOTAL, 0L)
        val prevCount = prefs.getInt(KEY_PROCESSED_COUNT, 0)
        prefs.edit()
            .putLong(KEY_RAW_BYTES_TOTAL, prevRaw + safeRaw)
            .putLong(KEY_COMPRESSED_BYTES_TOTAL, prevComp + compressedBytes)
            .putInt(KEY_PROCESSED_COUNT, prevCount + 1)
            .apply()
    }

    fun getRecordedSavingsBytes(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getLong(KEY_RAW_BYTES_TOTAL, 0L)
        val comp = prefs.getLong(KEY_COMPRESSED_BYTES_TOTAL, 0L)
        return (raw - comp).coerceAtLeast(0L)
    }

    fun getRecordedRawBytes(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_RAW_BYTES_TOTAL, 0L)
    }

    fun getRecordedCompressedBytes(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_COMPRESSED_BYTES_TOTAL, 0L)
    }
}

/**
 * Kategori foto resmi pada aplikasi SEWAin Aja Freezer SOC (Section 2: Smart Document Image Profile).
 * Mengatur batas resolusi maksimal, kualitas JPEG standar kategori, target ukuran fleksibel,
 * serta aturan orientasi spesifik per jenis objek.
 */
enum class PhotoCategory(
    val displayLabel: String,
    val maxDimensionPx: Int,
    val minAcceptableDimensionPx: Int,
    val targetMinKb: Int,
    val targetMaxKb: Int,
    val defaultQuality: Int,
    val minQualityFloor: Int,
    val forceDocumentLandscape: Boolean
) {
    /** FOTO KTP: max 2000 px, JPEG 88-92%, pertahankan ketajaman teks & prioritaskan akurasi OCR. */
    KTP(
        displayLabel = "Foto KTP (OCR)",
        maxDimensionPx = 2000,
        minAcceptableDimensionPx = 1400,
        targetMinKb = 300,
        targetMaxKb = 750,
        defaultQuality = 90,
        minQualityFloor = 88,
        forceDocumentLandscape = true
    ),

    /** FOTO FREEZER: max 1600 px, JPEG 82%, pertahankan orientasi alami (landscape/natural). */
    FREEZER(
        displayLabel = "Foto Freezer",
        maxDimensionPx = 1600,
        minAcceptableDimensionPx = 1152,
        targetMinKb = 220,
        targetMaxKb = 600,
        defaultQuality = 82,
        minQualityFloor = 80,
        forceDocumentLandscape = false
    ),

    /** FOTO PELANGGAN: max 1280 px, JPEG 80%, pertahankan orientasi portrait/landscape asli. */
    CUSTOMER(
        displayLabel = "Foto Pelanggan",
        maxDimensionPx = 1280,
        minAcceptableDimensionPx = 960,
        targetMinKb = 150,
        targetMaxKb = 400,
        defaultQuality = 80,
        minQualityFloor = 78,
        forceDocumentLandscape = false
    ),

    /** FOTO LOKASI: max 1280 px, JPEG 80%, pertahankan orientasi asli. */
    LOCATION(
        displayLabel = "Foto Lokasi",
        maxDimensionPx = 1280,
        minAcceptableDimensionPx = 960,
        targetMinKb = 150,
        targetMaxKb = 400,
        defaultQuality = 80,
        minQualityFloor = 78,
        forceDocumentLandscape = false
    ),

    /** BUKTI PEMBAYARAN: max 1600 px, JPEG 85%, pastikan angka nominal tetap terbaca jelas. */
    PAYMENT_PROOF(
        displayLabel = "Bukti Pembayaran",
        maxDimensionPx = 1600,
        minAcceptableDimensionPx = 1100,
        targetMinKb = 180,
        targetMaxKb = 550,
        defaultQuality = 85,
        minQualityFloor = 82,
        forceDocumentLandscape = false
    ),

    /** DOKUMENTASI UMUM: max 1280 px, JPEG 80%, pertahankan orientasi asli. */
    GENERAL_DOC(
        displayLabel = "Dokumentasi Umum",
        maxDimensionPx = 1280,
        minAcceptableDimensionPx = 960,
        targetMinKb = 150,
        targetMaxKb = 400,
        defaultQuality = 80,
        minQualityFloor = 78,
        forceDocumentLandscape = false
    );

    companion object {
        fun fromSlotOrType(slotOrType: String): PhotoCategory {
            val upper = slotOrType.uppercase(Locale.ROOT)
            return when {
                upper.contains("KTP") -> KTP
                upper.contains("FREEZER") || upper.contains("UNIT") || upper.contains("KONDISI") -> FREEZER
                upper.contains("BUKTI") || upper.contains("PAYMENT") || upper.contains("BAYAR") || upper.contains("TRANSFER") || upper.contains("QRIS") -> PAYMENT_PROOF
                upper.contains("LOKASI") || upper.contains("LOCATION") || upper.contains("PENEMPATAN") -> LOCATION
                upper.contains("PELANGGAN") || upper.contains("CUSTOMER") || upper.contains("PROFILE") || upper.contains("WAJAH") -> CUSTOMER
                else -> GENERAL_DOC
            }
        }
    }
}

enum class PreferredCompressionFormat {
    JPEG,
    WEBP_AUTO
}

/**
 * Metadata EXIF penting yang diekstrak terlebih dahulu sebelum metadata tambahan dibersihkan.
 */
data class ExtractedExifMetadata(
    val orientationTag: Int = ExifInterface.ORIENTATION_NORMAL,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val dateTimeOriginal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hadNonNormalOrientation: Boolean = false
)

data class ProcessedImageResult(
    val compressedFile: File,
    val thumbnailFile: File,
    val category: PhotoCategory,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
    val bytesSize: Long,
    val originalSourceBytes: Long,
    val appliedQuality: Int,
    val sha256Hash: String,
    val extractedMetadata: ExtractedExifMetadata,
    val wasOrientationNormalized: Boolean,
    val wasFrontCameraMirrorFixed: Boolean,
    val ktpQualityReport: KtpImageQualityReport? = null
)

data class CacheCleanupResult(
    val deletedFilesCount: Int,
    val freedBytes: Long
)

data class ImageStorageAnalytics(
    val totalPhotoCount: Int = 0,
    val appCacheBytes: Long = 0L,
    val appCacheFileCount: Int = 0,
    val thumbnailBytes: Long = 0L,
    val thumbnailFileCount: Int = 0,
    val offlineLocalPhotosBytes: Long = 0L,
    val offlineLocalPhotosCount: Int = 0,
    val pendingUploadPhotosCount: Int = 0,
    val pendingUploadPhotosBytes: Long = 0L,
    val totalLocalStorageBytes: Long = 0L,
    val cloudStorageBytes: Long = 0L,
    val uploadedCloudPhotosCount: Int = 0,
    val failedUploadPhotosCount: Int = 0,
    val estimatedCompressionSavingsBytes: Long = 0L,
    val totalTempCacheBytes: Long = 0L,
    val availableDeviceBytes: Long = 0L,
    val totalDeviceBytes: Long = 0L
)

/**
 * Mesin Pemrosesan Gambar Terpadu untuk Aplikasi SEWAin Aja Freezer SOC:
 * 1. Smart Image Compression dengan 3 pilihan kualitas (Hemat 70%/1280px, Standar 82%/1600px, HD 90%/2400px) + Profil Dokumen KTP OCR (2000px, 88-92%).
 * 2. Automatic Camera & EXIF Orientation Detection + All-8 Tag Normalization + Front Camera Mirror Undo + Zero Double Rotation.
 * 3. Automatic Aspect Ratio Correction tanpa memaksa semua foto menjadi landscape.
 * 4. Automatic WebP Thumbnail Generator (max 300 px) untuk seluruh daftar & galeri.
 * 5. Metadata Extraction (GPS & Timestamp disimpan terpisah) lalu pembersihan metadata berlebih.
 */
object SmartImageProcessor {

    private const val TAG = "SmartImageProcessor"
    const val THUMBNAIL_MAX_PX = 300
    const val THUMBNAIL_WEBP_QUALITY = 72

    fun supportsWebpLossy(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @Suppress("DEPRECATION")
    fun resolveCompressFormat(preferred: PreferredCompressionFormat): Pair<Bitmap.CompressFormat, String> {
        return when (preferred) {
            PreferredCompressionFormat.WEBP_AUTO -> {
                val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
                fmt to "image/webp"
            }
            PreferredCompressionFormat.JPEG -> Bitmap.CompressFormat.JPEG to "image/jpeg"
        }
    }

    /**
     * Menghitung profil kompresi efektif dengan menggabungkan kategori dokumentasi
     * dan preferensi kualitas pengguna (Hemat / Standar / HD).
     * Pengecualian KTP: selalu memakai profil OCR khusus (2000 px, 88-92%).
     */
    fun resolveEffectiveProfile(
        category: PhotoCategory,
        settings: ImageQualitySettings
    ): EffectiveCompressionProfile {
        if (category == PhotoCategory.KTP) {
            return EffectiveCompressionProfile(
                maxDimensionPx = PhotoCategory.KTP.maxDimensionPx,
                minAcceptableDimensionPx = PhotoCategory.KTP.minAcceptableDimensionPx,
                targetQuality = PhotoCategory.KTP.defaultQuality,
                minQualityFloor = PhotoCategory.KTP.minQualityFloor,
                targetMaxKb = PhotoCategory.KTP.targetMaxKb,
                forceDocumentLandscape = true
            )
        }
        if (!settings.autoCompressionEnabled) {
            return EffectiveCompressionProfile(
                maxDimensionPx = 2400,
                minAcceptableDimensionPx = 1920,
                targetQuality = 95,
                minQualityFloor = 92,
                targetMaxKb = 2048,
                forceDocumentLandscape = category.forceDocumentLandscape
            )
        }
        return when (settings.defaultPreset) {
            ImageQualityPreset.HEMAT -> {
                val floorQuality = if (category == PhotoCategory.PAYMENT_PROOF) 70 else ImageQualityPreset.HEMAT.minQualityFloor
                val maxQual = ImageQualityPreset.HEMAT.jpegQuality
                EffectiveCompressionProfile(
                    maxDimensionPx = ImageQualityPreset.HEMAT.maxDimensionPx,
                    minAcceptableDimensionPx = 960,
                    targetQuality = maxQual,
                    minQualityFloor = floorQuality,
                    targetMaxKb = 250,
                    forceDocumentLandscape = category.forceDocumentLandscape
                )
            }
            ImageQualityPreset.STANDAR -> EffectiveCompressionProfile(
                maxDimensionPx = category.maxDimensionPx,
                minAcceptableDimensionPx = category.minAcceptableDimensionPx,
                targetQuality = category.defaultQuality,
                minQualityFloor = category.minQualityFloor,
                targetMaxKb = category.targetMaxKb,
                forceDocumentLandscape = category.forceDocumentLandscape
            )
            ImageQualityPreset.HD -> EffectiveCompressionProfile(
                maxDimensionPx = ImageQualityPreset.HD.maxDimensionPx,
                minAcceptableDimensionPx = 1800,
                targetQuality = ImageQualityPreset.HD.jpegQuality,
                minQualityFloor = ImageQualityPreset.HD.minQualityFloor,
                targetMaxKb = 1100,
                forceDocumentLandscape = category.forceDocumentLandscape
            )
        }
    }

    /**
     * Membaca seluruh 8 tag EXIF orientasi serta informasi lokasi GPS & waktu pengambilan foto
     * sebelum gambar dinormalisasi dan metadata tidak perlu dihapus.
     */
    fun extractExifMetadataFromFile(file: File): ExtractedExifMetadata {
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            parseExifInterface(exif)
        }.getOrDefault(ExtractedExifMetadata())
    }

    fun extractExifMetadataFromUri(context: Context, uri: Uri): ExtractedExifMetadata {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                parseExifInterface(exif)
            } ?: ExtractedExifMetadata()
        }.getOrDefault(ExtractedExifMetadata())
    }

    fun parseExifInterface(exif: ExifInterface): ExtractedExifMetadata {
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val (deg, flipH, flipV) = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Triple(0, true, false)
            ExifInterface.ORIENTATION_ROTATE_180 -> Triple(180, false, false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> Triple(0, false, true)
            ExifInterface.ORIENTATION_TRANSPOSE -> Triple(90, true, false)
            ExifInterface.ORIENTATION_ROTATE_90 -> Triple(90, false, false)
            ExifInterface.ORIENTATION_TRANSVERSE -> Triple(270, true, false)
            ExifInterface.ORIENTATION_ROTATE_270 -> Triple(270, false, false)
            else -> Triple(0, false, false)
        }
        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?.takeIf { it.isNotBlank() }
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)?.takeIf { it.isNotBlank() }

        val latLong = FloatArray(2)
        val hasLatLong = runCatching {
            @Suppress("DEPRECATION")
            exif.getLatLong(latLong)
        }.getOrDefault(false)

        return ExtractedExifMetadata(
            orientationTag = orientation,
            rotationDegrees = deg,
            flipHorizontal = flipH,
            flipVertical = flipV,
            dateTimeOriginal = dateTime,
            latitude = if (hasLatLong) latLong[0].toDouble() else null,
            longitude = if (hasLatLong) latLong[1].toDouble() else null,
            hadNonNormalOrientation = orientation != ExifInterface.ORIENTATION_NORMAL &&
                orientation != ExifInterface.ORIENTATION_UNDEFINED
        )
    }

    /**
     * Memeriksa apakah file JPEG sudah berorientasi normal (`ORIENTATION_NORMAL` / undefined)
     * sehingga tidak akan pernah diputar dua kali (double-rotation prevention).
     */
    fun isOrientationNormalized(file: File): Boolean {
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            val tag = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            tag == ExifInterface.ORIENTATION_NORMAL || tag == ExifInterface.ORIENTATION_UNDEFINED
        }.getOrDefault(true)
    }

    /**
     * Membangun Matrix transformasi yang akurat untuk seluruh 8 tag EXIF orientasi
     * plus koreksi cermin (mirror undo) khusus kamera depan.
     */
    fun buildExifCorrectionMatrix(
        orientationTag: Int,
        isFrontCamera: Boolean = false,
        extraRotationDegrees: Int = 0
    ): Matrix {
        val matrix = Matrix()
        when (orientationTag) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> Unit
        }
        val normExtra = ((extraRotationDegrees % 360) + 360) % 360
        if (normExtra != 0) {
            matrix.postRotate(normExtra.toFloat())
        }
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f)
        }
        return matrix
    }

    /**
     * Menormalisasi orientasi bitmap berdasarkan tag EXIF, kamera depan/belakang,
     * dan aturan orientasi alami objek:
     * - KTP: mengikuti orientasi dokumen horizontal (landscape) tanpa distorsi.
     * - Freezer / Pelanggan / Lokasi / Bukti Bayar / Umum: menjaga orientasi & rasio aspek asli!
     */
    fun normalizeBitmapOrientation(
        source: Bitmap,
        orientationTag: Int = ExifInterface.ORIENTATION_NORMAL,
        isFrontCamera: Boolean = false,
        extraRotationDegrees: Int = 0,
        category: PhotoCategory = PhotoCategory.GENERAL_DOC
    ): Bitmap {
        val matrix = buildExifCorrectionMatrix(orientationTag, isFrontCamera, extraRotationDegrees)
        var working = if (!matrix.isIdentity) {
            try {
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            } catch (e: Throwable) {
                Log.w(TAG, "Gagal menerapkan matriks orientasi: ${e.message}")
                source
            }
        } else {
            source
        }

        // Khusus dokumen KTP: jika hasil orientasi masih vertikal (portrait tinggi > lebar * 1.15),
        // putar 90 derajat secara proporsional ke orientasi kartu landscape tanpa merusak rasio aspek.
        if (category.forceDocumentLandscape && working.height > (working.width * 1.15f).roundToInt()) {
            val ktpMatrix = Matrix().apply { postRotate(90f) }
            val rotatedKtp = runCatching {
                Bitmap.createBitmap(working, 0, 0, working.width, working.height, ktpMatrix, true)
            }.getOrNull()
            if (rotatedKtp != null && rotatedKtp !== working) {
                if (working !== source) working.recycle()
                working = rotatedKtp
            }
        }

        return working
    }

    /**
     * Mengubah ukuran bitmap secara proporsional (menjaga aspect ratio 100%, tidak pernah upscale paksa)
     * sehingga sisi terpanjang <= maxDimensionPx.
     */
    fun scaleDownPreservingAspect(source: Bitmap, maxDimensionPx: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDimensionPx && h <= maxDimensionPx) return source
        val scale = maxDimensionPx.toFloat() / maxOf(w, h).toFloat()
        val newW = (w * scale).roundToInt().coerceAtLeast(1)
        val newH = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    /**
     * Mengompresi bitmap secara fleksibel sesuai EffectiveCompressionProfile.
     * Kualitas & resolusi adalah batas maksimum (tanpa memaksa jika ukuran sudah hemat).
     */
    fun compressAdaptivePreservingSharpness(
        bitmap: Bitmap,
        profile: EffectiveCompressionProfile,
        preferredFormat: PreferredCompressionFormat = PreferredCompressionFormat.JPEG
    ): Pair<ByteArray, Int> {
        val (compressFormat, _) = resolveCompressFormat(preferredFormat)
        var workingBitmap = scaleDownPreservingAspect(bitmap, profile.maxDimensionPx)
        var quality = profile.targetQuality
        val maxBytes = profile.targetMaxKb * 1024

        val baos = ByteArrayOutputStream()
        workingBitmap.compress(compressFormat, quality, baos)

        while (baos.size() > maxBytes && quality - 2 >= profile.minQualityFloor) {
            quality -= 2
            baos.reset()
            workingBitmap.compress(compressFormat, quality, baos)
        }

        if (baos.size() > (maxBytes * 1.35).toInt() &&
            maxOf(workingBitmap.width, workingBitmap.height) > profile.minAcceptableDimensionPx
        ) {
            val slightlySmaller = scaleDownPreservingAspect(workingBitmap, profile.minAcceptableDimensionPx)
            if (slightlySmaller !== workingBitmap) {
                if (workingBitmap !== bitmap) workingBitmap.recycle()
                workingBitmap = slightlySmaller
                baos.reset()
                workingBitmap.compress(compressFormat, quality, baos)
            }
        }

        if (workingBitmap !== bitmap) {
            workingBitmap.recycle()
        }
        return baos.toByteArray() to quality
    }

    /**
     * Overload kompatibel untuk pemanggil yang langsung memberikan PhotoCategory.
     */
    fun compressAdaptivePreservingSharpness(
        bitmap: Bitmap,
        category: PhotoCategory,
        preferredFormat: PreferredCompressionFormat = PreferredCompressionFormat.JPEG,
        settings: ImageQualitySettings = ImageQualitySettings()
    ): Pair<ByteArray, Int> {
        return compressAdaptivePreservingSharpness(
            bitmap = bitmap,
            profile = resolveEffectiveProfile(category, settings),
            preferredFormat = preferredFormat
        )
    }

    /**
     * Memproses sumber foto (File / Uri / Bitmap / Path String) menjadi pasangan:
     * 1. File utama terkompresi & terorientasi tegak sesuai profil kategori + pilihan kualitas user
     * 2. Thumbnail WebP <= 300 px untuk tampilan daftar cepat & hemat data.
     */
    fun processImageSourceToFiles(
        context: Context,
        source: Any,
        category: PhotoCategory,
        outputMainFile: File,
        outputThumbFile: File,
        isFrontCamera: Boolean = false,
        extraRotationDegrees: Int = 0,
        preferredFormat: PreferredCompressionFormat = PreferredCompressionFormat.JPEG,
        qualitySettingsOverride: ImageQualitySettings? = null
    ): ProcessedImageResult? {
        return try {
            val activeSettings = qualitySettingsOverride ?: ImageQualityPreferencesManager.loadSettings(context)
            val effectiveProfile = resolveEffectiveProfile(category, activeSettings)

            var metadata = ExtractedExifMetadata()
            var rawSourceBytes = 0L
            val rawBitmap: Bitmap? = when (source) {
                is Bitmap -> source
                is File -> {
                    if (!source.exists() || source.length() <= 0L) return null
                    rawSourceBytes = source.length()
                    metadata = extractExifMetadataFromFile(source)
                    decodeSampledFile(source, effectiveProfile.maxDimensionPx)
                }
                is Uri -> {
                    if (source.scheme == "file") {
                        val f = File(source.path.orEmpty())
                        if (!f.exists() || f.length() <= 0L) return null
                        rawSourceBytes = f.length()
                        metadata = extractExifMetadataFromFile(f)
                        decodeSampledFile(f, effectiveProfile.maxDimensionPx)
                    } else {
                        rawSourceBytes = queryUriFileSize(context, source)
                        metadata = extractExifMetadataFromUri(context, source)
                        decodeSampledUri(context, source, effectiveProfile.maxDimensionPx)
                    }
                }
                is String -> {
                    val trimmed = source.trim()
                    if (trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
                        val uri = Uri.parse(trimmed)
                        if (trimmed.startsWith("file://")) {
                            val f = File(uri.path.orEmpty())
                            if (!f.exists() || f.length() <= 0L) return null
                            rawSourceBytes = f.length()
                            metadata = extractExifMetadataFromFile(f)
                            decodeSampledFile(f, effectiveProfile.maxDimensionPx)
                        } else {
                            rawSourceBytes = queryUriFileSize(context, uri)
                            metadata = extractExifMetadataFromUri(context, uri)
                            decodeSampledUri(context, uri, effectiveProfile.maxDimensionPx)
                        }
                    } else {
                        val f = File(trimmed)
                        if (!f.exists() || f.length() <= 0L) return null
                        rawSourceBytes = f.length()
                        metadata = extractExifMetadataFromFile(f)
                        decodeSampledFile(f, effectiveProfile.maxDimensionPx)
                    }
                }
                else -> null
            }

            if (rawBitmap == null) return null

            val orientedBitmap = normalizeBitmapOrientation(
                source = rawBitmap,
                orientationTag = metadata.orientationTag,
                isFrontCamera = isFrontCamera,
                extraRotationDegrees = extraRotationDegrees,
                category = category
            )

            // Jika sumber asli adalah in-memory Bitmap tanpa ukuran file awal, estimasi baseline 95% JPEG aktual
            if (rawSourceBytes <= 0L) {
                val baselineStream = ByteArrayOutputStream()
                orientedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, baselineStream)
                rawSourceBytes = baselineStream.size().toLong()
            }

            val scaledMain = scaleDownPreservingAspect(orientedBitmap, effectiveProfile.maxDimensionPx)
            val (compressedBytes, usedQuality) = compressAdaptivePreservingSharpness(
                bitmap = scaledMain,
                profile = effectiveProfile,
                preferredFormat = preferredFormat
            )

            outputMainFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileOutputStream(outputMainFile).use { fos ->
                fos.write(compressedBytes)
                fos.flush()
            }

            val (_, mimeType) = resolveCompressFormat(preferredFormat)
            if (mimeType == "image/jpeg") {
                runCatching {
                    val outExif = ExifInterface(outputMainFile.absolutePath)
                    outExif.setAttribute(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL.toString()
                    )
                    outExif.saveAttributes()
                }
            }

            // Section 4: Automatic Thumbnail Generator — ALWAYS WebP format, max 300 px
            val thumbBitmap = scaleDownPreservingAspect(scaledMain, THUMBNAIL_MAX_PX)
            val thumbBaos = ByteArrayOutputStream()
            val (webpThumbFormat, _) = resolveCompressFormat(PreferredCompressionFormat.WEBP_AUTO)
            thumbBitmap.compress(webpThumbFormat, THUMBNAIL_WEBP_QUALITY, thumbBaos)
            outputThumbFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileOutputStream(outputThumbFile).use { fos ->
                fos.write(thumbBaos.toByteArray())
                fos.flush()
            }

            // Evaluasi kualitas KTP pra-simpan apabila kategori == KTP
            val ktpQuality = if (category == PhotoCategory.KTP) {
                evaluateKtpQuickQuality(scaledMain)
            } else {
                null
            }

            val mainW = scaledMain.width
            val mainH = scaledMain.height
            val thumbW = thumbBitmap.width
            val thumbH = thumbBitmap.height
            val finalSizeBytes = outputMainFile.length()
            val sha256 = computeSha256(outputMainFile)

            // Catat statistik penghematan kompresi aktual ke ledger permanen
            ImageQualityPreferencesManager.recordCompressionMetrics(
                context = context,
                rawSourceBytes = rawSourceBytes,
                compressedBytes = finalSizeBytes
            )

            if (thumbBitmap !== scaledMain) thumbBitmap.recycle()
            if (scaledMain !== orientedBitmap) scaledMain.recycle()
            if (orientedBitmap !== rawBitmap) orientedBitmap.recycle()
            if (source !is Bitmap && rawBitmap !== source) rawBitmap.recycle()

            ProcessedImageResult(
                compressedFile = outputMainFile,
                thumbnailFile = outputThumbFile,
                category = category,
                mimeType = mimeType,
                width = mainW,
                height = mainH,
                thumbnailWidth = thumbW,
                thumbnailHeight = thumbH,
                bytesSize = finalSizeBytes,
                originalSourceBytes = rawSourceBytes,
                appliedQuality = usedQuality,
                sha256Hash = sha256,
                extractedMetadata = metadata,
                wasOrientationNormalized = metadata.hadNonNormalOrientation || extraRotationDegrees != 0 ||
                    (category.forceDocumentLandscape && rawBitmap.height > rawBitmap.width),
                wasFrontCameraMirrorFixed = isFrontCamera,
                ktpQualityReport = ktpQuality
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal memproses gambar: ${e.message}", e)
            null
        }
    }

    /**
     * Memproses file hasil tangkapan kamera langsung di tempat (in-place normalization & compression),
     * sehingga preview dan hasil simpan 100% konsisten orientasinya dan hemat ukuran.
     */
    fun normalizeAndCompressFileInPlace(
        context: Context,
        targetFile: File,
        category: PhotoCategory,
        isFrontCamera: Boolean = false,
        extraRotationDegrees: Int = 0
    ): ProcessedImageResult? {
        if (!targetFile.exists() || targetFile.length() <= 0L) return null
        val tempOut = File(context.cacheDir, "norm_tmp_${System.nanoTime()}.jpg")
        val thumbFile = resolveThumbnailFileFor(context, targetFile)
        val result = processImageSourceToFiles(
            context = context,
            source = targetFile,
            category = category,
            outputMainFile = tempOut,
            outputThumbFile = thumbFile,
            isFrontCamera = isFrontCamera,
            extraRotationDegrees = extraRotationDegrees,
            preferredFormat = PreferredCompressionFormat.JPEG
        ) ?: return null

        return try {
            tempOut.copyTo(targetFile, overwrite = true)
            tempOut.delete()
            result.copy(compressedFile = targetFile, bytesSize = targetFile.length())
        } catch (e: Exception) {
            tempOut.delete()
            null
        }
    }

    /**
     * Memastikan file thumbnail WebP (<= 300 px) tersedia untuk path file atau URI gambar apapun.
     */
    fun resolveThumbnailFileFor(context: Context, mainFile: File): File {
        val thumbDir = File(context.filesDir, "photo_thumbnails").apply {
            if (!exists()) mkdirs()
        }
        val key = sha256String(mainFile.absolutePath).take(16)
        return File(thumbDir, "thumb_${key}.webp")
    }

    fun getExistingThumbnailPathOrNull(context: Context, mainPathOrUri: String): String? {
        if (mainPathOrUri.isBlank()) return null
        if (mainPathOrUri.startsWith("http")) return null
        val cleanPath = if (mainPathOrUri.startsWith("file://")) {
            Uri.parse(mainPathOrUri).path.orEmpty()
        } else {
            mainPathOrUri
        }
        if (cleanPath.isBlank() || cleanPath.startsWith("content://")) return null
        val mainFile = File(cleanPath)

        // Prioritaskan sidecar WebP lalu sidecar JPG lama untuk kompatibilitas mundur
        val sidecarWebp = File(mainFile.parentFile, "${mainFile.nameWithoutExtension}_thumb.webp")
        if (sidecarWebp.exists() && sidecarWebp.length() > 0L) return sidecarWebp.absolutePath
        val sidecarJpg = File(mainFile.parentFile, "${mainFile.nameWithoutExtension}_thumb.jpg")
        if (sidecarJpg.exists() && sidecarJpg.length() > 0L) return sidecarJpg.absolutePath

        val mappedWebp = resolveThumbnailFileFor(context, mainFile)
        if (mappedWebp.exists() && mappedWebp.length() > 0L) return mappedWebp.absolutePath

        val key = sha256String(mainFile.absolutePath).take(16)
        val mappedLegacyJpg = File(File(context.filesDir, "photo_thumbnails"), "thumb_${key}.jpg")
        if (mappedLegacyJpg.exists() && mappedLegacyJpg.length() > 0L) return mappedLegacyJpg.absolutePath
        return null
    }

    /**
     * Evaluasi cepat kualitas KTP pra-simpan:
     * cek batas terpotong, blur, terlalu gelap, pantulan glare, & keterbacaan tulisan.
     */
    fun evaluateKtpQuickQuality(bitmap: Bitmap): KtpImageQualityReport {
        val detection = KtpImageProcessor.detectKtpCardBounds(bitmap)
        val corners = KtpImageProcessor.detectFourCornerPoints(
            bitmap = bitmap,
            bounds = detection.bounds,
            touchesBorder = detection.touchesBorder
        )
        return KtpImageProcessor.analyzeQualityMetrics(
            bitmap = bitmap,
            cardBounds = detection.bounds,
            cardAreaRatio = detection.areaRatio,
            cornerPoints = corners
        )
    }

    private fun queryUriFileSize(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize.coerceAtLeast(0L)
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun decodeSampledFile(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension, maxDimension)
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }

    private fun decodeSampledUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension, maxDimension)
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        }
    }

    fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        val targetMax = maxOf(reqWidth, reqHeight)
        if (height > targetMax * 1.5 || width > targetMax * 1.5) {
            val halfH = height / 2
            val halfW = width / 2
            while ((halfH / inSampleSize) >= targetMax && (halfW / inSampleSize) >= targetMax) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun computeSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read = input.read(buf)
                while (read != -1) {
                    digest.update(buf, 0, read)
                    read = input.read(buf)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun sha256String(value: String): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }.getOrDefault(value.hashCode().toString())
    }
}

/**
 * Pengelola Pembersihan Cache & Analitik Penyimpanan Gambar Aktual (Section 5 & Section 9).
 *
 * Ketentuan Ketat Pembersihan Cache:
 * - Menghapus cache tidak diperlukan, file temporary, & staging foto Cloud yang SUDAH terverifikasi terunggah.
 * - TIDAK PERNAH menghapus dokumentasi utama, foto yang belum diunggah, database pelanggan, atau transaksi.
 */
object SmartImageStorageOptimizer {

    private val TEMPORARY_CACHE_SUBDIRS = setOf(
        "ktp_temp",
        "cloud_uri_imports",
        "camera_tmp",
        "image_cache",
        "coil_cache"
    )

    private val TEMPORARY_PREFIXES = listOf(
        "captured_ktp_",
        "norm_tmp_",
        "restore_staging_",
        "share_tmp_"
    )

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "webp", "png")

    /**
     * Menghitung statistik penyimpanan aktual (bukan angka simulasi) berdasarkan file nyata di perangkat,
     * antrean CloudPhotoJobEntity, dan catatan ukuran foto MediaStore.
     */
    fun computeActualStorageAnalytics(
        context: Context,
        cloudPhotoJobs: List<CloudPhotoJobEntity> = emptyList(),
        mediaStoreRecordedPhotoCount: Int = 0,
        mediaStoreRecordedBytes: Long = 0L
    ): ImageStorageAnalytics {
        // 1. Cache sementara aplikasi (cacheDir subdirs + prefix temp)
        var appCacheBytes = 0L
        var appCacheFiles = 0
        val cacheRoot = context.cacheDir
        if (cacheRoot != null && cacheRoot.exists()) {
            TEMPORARY_CACHE_SUBDIRS.forEach { dirName ->
                val dir = File(cacheRoot, dirName)
                if (dir.exists()) {
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        appCacheFiles++
                        appCacheBytes += f.length()
                    }
                }
            }
            cacheRoot.listFiles()?.forEach { entry ->
                if (TEMPORARY_PREFIXES.any { entry.name.startsWith(it) }) {
                    if (entry.isDirectory) {
                        entry.walkTopDown().filter { it.isFile }.forEach { f ->
                            appCacheFiles++
                            appCacheBytes += f.length()
                        }
                    } else if (entry.isFile) {
                        appCacheFiles++
                        appCacheBytes += entry.length()
                    }
                }
            }
        }

        // 2. Thumbnail WebP/JPG lokal (~300px)
        var thumbBytes = 0L
        var thumbCount = 0
        val thumbDir = File(context.filesDir, "photo_thumbnails")
        if (thumbDir.exists()) {
            thumbDir.walkTopDown().filter { it.isFile }.forEach { f ->
                thumbCount++
                thumbBytes += f.length()
            }
        }

        // 3. Dokumentasi foto offline utama (SEWAIN_AJA_FREEZER, photos, ktp_documents, Pictures)
        var offlineDocBytes = 0L
        var offlineDocCount = 0
        val primaryDirs = listOfNotNull(
            File(context.filesDir, "SEWAIN_AJA_FREEZER/CUSTOMERS"),
            File(context.filesDir, "SEWAIN_AJA_FREEZER/TRANSACTIONS"),
            File(context.filesDir, "SEWAIN_AJA_FREEZER/FREEZERS"),
            File(context.filesDir, "photos"),
            File(context.filesDir, "ktp_documents"),
            runCatching {
                val pic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (pic != null) File(pic, "SEWAin Aja Freezer SOC") else null
            }.getOrNull()
        )
        primaryDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val ext = f.extension.lowercase(Locale.ROOT)
                    if (f.name.contains("_thumb.")) {
                        thumbCount++
                        thumbBytes += f.length()
                    } else if (ext in IMAGE_EXTENSIONS) {
                        offlineDocCount++
                        offlineDocBytes += f.length()
                    }
                }
            }
        }

        // Sertakan foto MediaStore Scoped Storage (Android 10+) yang tidak bisa diwalk langsung sebagai File
        if (mediaStoreRecordedBytes > 0L) {
            offlineDocBytes += mediaStoreRecordedBytes
        }
        val effectiveOfflineCount = maxOf(offlineDocCount, mediaStoreRecordedPhotoCount)

        // 4. Cloud staging cache (`filesDir/cloud_photo_cache`) & status unggahan Cloud
        val cloudCacheDir = File(context.filesDir, "cloud_photo_cache")
        val uploadedStagingPaths = cloudPhotoJobs
            .filter { it.status == "UPLOADED" && it.cloudDownloadUrl.isNotBlank() }
            .map { it.localFilePath }
            .toSet()

        var cleanableCloudStagingBytes = 0L
        var cleanableCloudStagingCount = 0
        var activeCloudStagingBytes = 0L
        if (cloudCacheDir.exists()) {
            cloudCacheDir.walkTopDown().filter { it.isFile }.forEach { f ->
                if (f.name.contains("_thumb.")) {
                    thumbCount++
                    thumbBytes += f.length()
                } else if (f.absolutePath in uploadedStagingPaths) {
                    cleanableCloudStagingCount++
                    cleanableCloudStagingBytes += f.length()
                } else {
                    activeCloudStagingBytes += f.length()
                }
            }
        }

        val pendingJobs = cloudPhotoJobs.filter { it.status == "QUEUED" || it.status == "UPLOADING" || it.status == "FAILED" }
        val uploadedJobs = cloudPhotoJobs.filter { it.status == "UPLOADED" }
        val failedCount = cloudPhotoJobs.count { it.status == "FAILED" }

        val pendingBytes = pendingJobs.sumOf { job ->
            val fileSize = File(job.localFilePath).takeIf { it.exists() }?.length() ?: 0L
            maxOf(job.bytesTotal, fileSize)
        }
        val cloudBytes = uploadedJobs.sumOf { it.bytesTotal.coerceAtLeast(0L) }

        val totalAppCacheBytes = appCacheBytes + cleanableCloudStagingBytes
        val totalAppCacheCount = appCacheFiles + cleanableCloudStagingCount
        val totalLocalBytes = offlineDocBytes + thumbBytes + activeCloudStagingBytes + totalAppCacheBytes

        val dbSize = runCatching {
            val dbFile = context.getDatabasePath("sewain_aja_freezer_soc.db")
            if (dbFile.exists()) dbFile.length() else 0L
        }.getOrDefault(0L)

        val (availBytes, totalDeviceBytes) = runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes to stat.totalBytes
        }.getOrDefault((2L * 1024 * 1024 * 1024) to (16L * 1024 * 1024 * 1024))

        val totalPhotoCount = maxOf(effectiveOfflineCount, uploadedJobs.size + pendingJobs.size)

        return ImageStorageAnalytics(
            totalPhotoCount = totalPhotoCount,
            appCacheBytes = totalAppCacheBytes,
            appCacheFileCount = totalAppCacheCount,
            thumbnailBytes = thumbBytes,
            thumbnailFileCount = thumbCount,
            offlineLocalPhotosBytes = offlineDocBytes,
            offlineLocalPhotosCount = effectiveOfflineCount,
            pendingUploadPhotosCount = pendingJobs.size,
            pendingUploadPhotosBytes = pendingBytes,
            totalLocalStorageBytes = totalLocalBytes + dbSize,
            cloudStorageBytes = cloudBytes,
            uploadedCloudPhotosCount = uploadedJobs.size,
            failedUploadPhotosCount = failedCount,
            estimatedCompressionSavingsBytes = ImageQualityPreferencesManager.getRecordedSavingsBytes(context),
            totalTempCacheBytes = totalAppCacheBytes,
            availableDeviceBytes = availBytes,
            totalDeviceBytes = totalDeviceBytes
        )
    }

    /**
     * Membersihkan hanya file cache temporary dan staging yang aman:
     * - Menghapus subdirektori cacheDir temp (`ktp_temp`, `cloud_uri_imports`, `camera_tmp`, `image_cache`, `coil_cache`)
     * - Menghapus file staging di `cloud_photo_cache` HANYA JIKA foto tsb sudah berstatus `UPLOADED` ke Cloud
     * - Menghapus thumbnail sementara milik staging yang sudah bersih/kadaluarsa
     * - DILARANG KERAS menghapus: dokumentasi utama, foto `QUEUED`/`FAILED` yang menunggu upload, DB SQLite, pelanggan, atau transaksi.
     */
    fun cleanSafeCachesPreservingImportantData(
        context: Context,
        cloudPhotoJobs: List<CloudPhotoJobEntity> = emptyList(),
        olderThanMillis: Long = 0L,
        nowMillis: Long = System.currentTimeMillis()
    ): CacheCleanupResult {
        var result = cleanupTemporaryImageCaches(
            context = context,
            olderThanMillis = olderThanMillis,
            nowMillis = nowMillis
        )

        var extraDeleted = 0
        var extraBytes = 0L

        val protectedPendingPaths = cloudPhotoJobs
            .filter { it.status != "UPLOADED" }
            .flatMap { listOf(it.localFilePath, it.thumbnailPath) }
            .filter { it.isNotBlank() }
            .toSet()

        val verifiedUploadedStagingPaths = cloudPhotoJobs
            .filter { it.status == "UPLOADED" && it.cloudDownloadUrl.isNotBlank() }
            .map { it.localFilePath }
            .filter { it.isNotBlank() }
            .toSet()

        val cloudCacheDir = File(context.filesDir, "cloud_photo_cache")
        if (cloudCacheDir.exists() && cloudCacheDir.isDirectory) {
            cloudCacheDir.walkBottomUp().forEach { entry ->
                if (entry != cloudCacheDir && entry.isFile) {
                    val path = entry.absolutePath
                    if (path !in protectedPendingPaths &&
                        (path in verifiedUploadedStagingPaths || entry.name.contains("_thumb."))
                    ) {
                        val size = entry.length()
                        if (runCatching { entry.delete() }.getOrDefault(false)) {
                            extraDeleted++
                            extraBytes += size
                        }
                    }
                }
            }
        }

        return CacheCleanupResult(
            deletedFilesCount = result.deletedFilesCount + extraDeleted,
            freedBytes = result.freedBytes + extraBytes
        )
    }

    fun cleanupTemporaryImageCaches(
        context: Context,
        olderThanMillis: Long = 24L * 60L * 60L * 1000L,
        nowMillis: Long = System.currentTimeMillis()
    ): CacheCleanupResult {
        var deletedCount = 0
        var freedBytes = 0L
        val cacheRoot = context.cacheDir ?: return CacheCleanupResult(0, 0L)

        // 1. Bersihkan subdirektori cache sementara yang diketahui aman
        TEMPORARY_CACHE_SUBDIRS.forEach { dirName ->
            val dir = File(cacheRoot, dirName)
            if (dir.exists() && dir.isDirectory) {
                dir.walkBottomUp().forEach { entry ->
                    if (entry != dir && entry.isFile) {
                        val age = nowMillis - entry.lastModified()
                        if (olderThanMillis <= 0L || age >= olderThanMillis) {
                            val size = entry.length()
                            if (runCatching { entry.delete() }.getOrDefault(false)) {
                                deletedCount++
                                freedBytes += size
                            }
                        }
                    }
                }
            }
        }

        // 2. Bersihkan file temp di root cacheDir yang sesuai prefix temp aplikasi
        cacheRoot.listFiles()?.forEach { entry ->
            val matchesTempPrefix = TEMPORARY_PREFIXES.any { entry.name.startsWith(it) }
            if (matchesTempPrefix) {
                val age = nowMillis - entry.lastModified()
                if (olderThanMillis <= 0L || age >= olderThanMillis) {
                    val size = if (entry.isDirectory) entry.walkTopDown().filter { it.isFile }.sumOf { it.length() } else entry.length()
                    val removed = if (entry.isDirectory) entry.deleteRecursively() else entry.delete()
                    if (removed) {
                        deletedCount++
                        freedBytes += size
                    }
                }
            }
        }

        return CacheCleanupResult(deletedFilesCount = deletedCount, freedBytes = freedBytes)
    }
}
