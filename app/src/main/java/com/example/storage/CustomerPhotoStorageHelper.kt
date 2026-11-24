package com.example.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerPhotoEntity
import com.example.drive.MirrorOutboxCodec
import com.example.media.PhotoCategory
import com.example.media.SmartImageProcessor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper untuk SISTEM PENYIMPANAN FOTO OFFLINE PER PELANGGAN DI ANDROID.
 *
 * Lokasi penyimpanan di perangkat Android:
 * Pictures/SEWAin Aja Freezer SOC/
 *
 * Struktur folder per pelanggan:
 * Pictures/
 * └── SEWAin Aja Freezer SOC/
 *     ├── Nama Pelanggan_CUST001/
 *     │   ├── 01_KTP/
 *     │   ├── 02_Serah_Terima/
 *     │   ├── 03_Foto_Unit/
 *     │   ├── 04_Foto_Lokasi/
 *     │   └── 05_Pengembalian/
 *
 * Format penamaan file terstandar:
 * YYYYMMDD_IDPelanggan_KATEGORI_NamaDokumentasi_NNN.jpg
 */
object CustomerPhotoStorageHelper {

    private const val TAG = "CustomerPhotoStorage"

    const val APP_ROOT_FOLDER = "SEWAin Aja Freezer SOC"

    // 5 Kategori folder resmi
    const val KAT_KTP = "01_KTP"
    const val KAT_SERAH_TERIMA = "02_Serah_Terima"
    const val KAT_FOTO_UNIT = "03_Foto_Unit"
    const val KAT_FOTO_LOKASI = "04_Foto_Lokasi"
    const val KAT_PENGEMBALIAN = "05_Pengembalian"

    val ALL_CATEGORIES = listOf(
        KAT_KTP,
        KAT_SERAH_TERIMA,
        KAT_FOTO_UNIT,
        KAT_FOTO_LOKASI,
        KAT_PENGEMBALIAN
    )

    fun getCategoryDisplayName(cat: String): String = when (cat) {
        KAT_KTP -> "01. Foto KTP"
        KAT_SERAH_TERIMA -> "02. Serah Terima"
        KAT_FOTO_UNIT -> "03. Foto Unit Freezer"
        KAT_FOTO_LOKASI -> "04. Lokasi Penempatan"
        KAT_PENGEMBALIAN -> "05. Pengembalian Unit"
        else -> cat
    }

    private fun mapCategoryToPhotoCategory(category: String): PhotoCategory = when (category) {
        KAT_KTP -> PhotoCategory.KTP
        KAT_SERAH_TERIMA, KAT_FOTO_UNIT, KAT_PENGEMBALIAN -> PhotoCategory.FREEZER
        KAT_FOTO_LOKASI -> PhotoCategory.LOCATION
        else -> PhotoCategory.fromSlotOrType(category)
    }

    /**
     * Nama folder pelanggan aman:
     * Menjaga privasi (tanpa NIK lengkap), menggunakan ID Pelanggan unik untuk mencegah benturan nama yang sama.
     * Contoh: "Budi Santoso_CUST001"
     */
    fun getCustomerFolderName(customer: CustomerEntity): String {
        val cleanName = customer.namaLengkap
            .replace(Regex("[^a-zA-Z0-9 ]"), "")
            .trim()
            .replace("\\s+".toRegex(), " ")
            .ifBlank { "Pelanggan" }

        val custCode = if (customer.kodePelanggan.isNotBlank()) {
            customer.kodePelanggan.replace("/", "-")
        } else {
            "CUST${customer.id.toString().padStart(3, '0')}"
        }
        return "${cleanName}_$custCode"
    }

    /**
     * Relative path di folder Pictures, misalnya:
     * "Pictures/SEWAin Aja Freezer SOC/Budi Santoso_CUST001/01_KTP/"
     */
    fun getRelativePathForCategory(customer: CustomerEntity, category: String): String {
        val custFolder = getCustomerFolderName(customer)
        return "Pictures/$APP_ROOT_FOLDER/$custFolder/$category"
    }

    /**
     * Short category tag for filename
     */
    private fun getCategoryShortTag(category: String): String = when (category) {
        KAT_KTP -> "KTP"
        KAT_SERAH_TERIMA -> "SERAH_TERIMA"
        KAT_FOTO_UNIT -> "FOTO_UNIT"
        KAT_FOTO_LOKASI -> "FOTO_LOKASI"
        KAT_PENGEMBALIAN -> "KEMBALI"
        else -> category.replace(Regex("[^a-zA-Z0-9]"), "").uppercase(Locale.ROOT)
    }

    /**
     * Generate standard filename:
     * YYYYMMDD_IDPelanggan_KATEGORI_NamaDokumentasi_NNN.jpg
     */
    fun generateStandardFileName(
        customer: CustomerEntity,
        category: String,
        documentationName: String,
        sequence: Int = 1
    ): String {
        val dateCompact = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val custCode = if (customer.kodePelanggan.isNotBlank()) {
            customer.kodePelanggan.replace("/", "").replace("-", "")
        } else {
            "CUST${customer.id.toString().padStart(3, '0')}"
        }
        val catTag = getCategoryShortTag(category)
        val docTag = documentationName
            .replace(Regex("[^a-zA-Z0-9]"), "_")
            .trim('_')
            .ifBlank { "DOC" }
            .uppercase(Locale.ROOT)
        val seqStr = sequence.coerceAtLeast(1).toString().padStart(3, '0')

        return "${dateCompact}_${custCode}_${catTag}_${docTag}_$seqStr.jpg"
    }

    /**
     * Simpan foto ke MediaStore / Pictures shared storage per folder pelanggan.
     * Bekerja secara offline 100% dan terindeks langsung di Galeri Android.
     * Secara otomatis menormalisasi orientasi EXIF, mengompresi sesuai kategori,
     * mengekstrak metadata GPS/waktu sebelum EXIF tambahan dibersihkan, dan membuat thumbnail 300px.
     */
    fun savePhotoToCustomerCategory(
        context: Context,
        customer: CustomerEntity,
        category: String,
        documentationName: String,
        sourceUriOrBitmap: Any,
        transactionId: Long? = null,
        unitId: Long? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        catatan: String = "",
        existingSequence: Int = 1
    ): CustomerPhotoEntity? {
        val tempDir = File(context.cacheDir, "camera_tmp").apply { if (!exists()) mkdirs() }
        val tempMainFile = File(tempDir, "cust_proc_${System.nanoTime()}.jpg")
        val tempThumbFile = File(tempDir, "cust_proc_${System.nanoTime()}_thumb.webp")
        try {
            val fileName = generateStandardFileName(customer, category, documentationName, existingSequence)
            val relativeSubDir = getRelativePathForCategory(customer, category) // e.g. Pictures/SEWAin Aja.../01_KTP
            val photoCategory = mapCategoryToPhotoCategory(category)

            val processed = SmartImageProcessor.processImageSourceToFiles(
                context = context,
                source = sourceUriOrBitmap,
                category = photoCategory,
                outputMainFile = tempMainFile,
                outputThumbFile = tempThumbFile
            )

            val resolvedSource: Any = processed?.compressedFile ?: sourceUriOrBitmap
            val extractedMeta = processed?.extractedMetadata
            val finalLat = latitude ?: extractedMeta?.latitude
            val finalLon = longitude ?: extractedMeta?.longitude
            val timestampIso = extractedMeta?.dateTimeOriginal?.replace(":", "-")?.takeIf { it.length >= 10 }
                ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

            var savedUriString = ""
            var sizeBytes = 0L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+) MediaStore scoped storage into Pictures/...
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "$relativeSubDir/")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        writeSourceToStream(context, resolvedSource, outputStream)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)

                    savedUriString = uri.toString()
                    // Query size
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            sizeBytes = pfd.statSize
                        }
                    } catch (_: Exception) {}
                }
            }

            // Fallback for Android 9 or if MediaStore insert failed / unit test environment
            if (savedUriString.isBlank()) {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    ?: File(context.filesDir, "Pictures")
                val targetDir = File(picturesDir, "$APP_ROOT_FOLDER/${getCustomerFolderName(customer)}/$category")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val targetFile = File(targetDir, fileName)
                FileOutputStream(targetFile).use { fos ->
                    writeSourceToStream(context, resolvedSource, fos)
                }
                if (processed != null && tempThumbFile.exists()) {
                    val thumbDest = SmartImageProcessor.resolveThumbnailFileFor(context, targetFile)
                    runCatching { tempThumbFile.copyTo(thumbDest, overwrite = true) }
                }
                savedUriString = Uri.fromFile(targetFile).toString()
                sizeBytes = targetFile.length()

                // Notify media scanner so gallery recognizes file
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )
                } catch (_: Exception) {}
            }

            return CustomerPhotoEntity(
                customerId = customer.id,
                transactionId = transactionId,
                unitId = unitId,
                uuid = MirrorOutboxCodec.generateUuidV4(),
                kategori = category,
                namaDokumentasi = documentationName,
                filePath = savedUriString,
                relativeFolder = relativeSubDir,
                fileName = fileName,
                tanggalWaktuIso = timestampIso,
                latitude = finalLat,
                longitude = finalLon,
                catatan = catatan,
                sizeBytes = sizeBytes,
                uploadStatus = "PENDING",
                driveFileId = "",
                updatedAtMillis = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan foto offline per pelanggan: ${e.message}", e)
            return null
        } finally {
            runCatching { if (tempMainFile.exists()) tempMainFile.delete() }
            runCatching { if (tempThumbFile.exists()) tempThumbFile.delete() }
        }
    }

    private fun writeSourceToStream(context: Context, source: Any, outputStream: java.io.OutputStream) {
        when (source) {
            is Bitmap -> {
                source.compress(Bitmap.CompressFormat.JPEG, 84, outputStream)
            }
            is Uri -> {
                context.contentResolver.openInputStream(source)?.use { input ->
                    input.copyTo(outputStream)
                }
            }
            is File -> {
                source.inputStream().use { input ->
                    input.copyTo(outputStream)
                }
            }
            is String -> {
                if (source.startsWith("content://") || source.startsWith("file://")) {
                    context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                        input.copyTo(outputStream)
                    }
                } else {
                    File(source).inputStream().use { input ->
                        input.copyTo(outputStream)
                    }
                }
            }
            else -> {
                throw IllegalArgumentException("Format sumber foto tidak didukung: ${source.javaClass.name}")
            }
        }
    }

    /**
     * Buka folder dokumentasi pelanggan di File Manager / Galeri Android.
     */
    fun openCustomerFolderInGallery(context: Context, customer: CustomerEntity) {
        try {
            val relativeSubDir = "$APP_ROOT_FOLDER/${getCustomerFolderName(customer)}"
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val customerFolder = File(picturesDir, relativeSubDir)

            // Pastikan direktori ada
            if (!customerFolder.exists()) {
                customerFolder.mkdirs()
                ALL_CATEGORIES.forEach { cat ->
                    File(customerFolder, cat).mkdirs()
                }
            }

            // Buka aplikasi Galeri / File Manager intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(customerFolder.absolutePath), "resource/folder")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Alternatif fallback buka galeri foto images
                val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(galleryIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Folder pelanggan: Pictures/$APP_ROOT_FOLDER/${getCustomerFolderName(customer)}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Hapus file foto dari disk / MediaStore jika diperlukan.
     */
    fun deletePhysicalPhoto(context: Context, photo: CustomerPhotoEntity): Boolean {
        return try {
            val uri = Uri.parse(photo.filePath)
            if (photo.filePath.startsWith("content://")) {
                val deleted = context.contentResolver.delete(uri, null, null)
                deleted > 0
            } else if (photo.filePath.startsWith("file://")) {
                val file = File(uri.path ?: "")
                if (file.exists()) file.delete() else true
            } else {
                val file = File(photo.filePath)
                if (file.exists()) file.delete() else true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghapus file fisik foto: ${e.message}")
            false
        }
    }
}
