package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import java.io.File
import java.io.FileOutputStream

/**
 * Hasil penyimpanan berkas KTP pelanggan pada struktur folder standar Section 12:
 * /Customers/<CustomerCode>/
 *    ktp_original.jpg
 *    ktp_processed.jpg
 *    ocr_result.json
 */
data class CustomerKtpFolderResult(
    val customerFolder: String,
    val originalPhotoPath: String,
    val processedPhotoPath: String,
    val ocrResultJsonPath: String
)

object KtpCustomerStorageManager {

    /**
     * Menyimpan Foto KTP Asli, Foto Hasil Preprocessing, dan Structured OCR JSON
     * ke dalam folder lokal `/Customers/<kodePelanggan>/` sesuai spesifikasi Section 12.
     */
    fun saveCustomerKtpArtifacts(
        context: Context,
        kodePelanggan: String,
        originalPhotoPathOrUri: String,
        processedPhotoPathOrUri: String,
        ocrStructuredJson: String
    ): CustomerKtpFolderResult {
        val safeCode = kodePelanggan.trim()
            .ifBlank { "CUST-SAF-0001" }
            .replace(Regex("""[^A-Za-z0-9_\-]"""), "-")

        val customersRoot = File(context.filesDir, "Customers").apply {
            if (!exists()) mkdirs()
        }
        val customerDir = File(customersRoot, safeCode).apply {
            if (!exists()) mkdirs()
        }

        val targetOrigFile = File(customerDir, "ktp_original.jpg")
        val targetProcFile = File(customerDir, "ktp_processed.jpg")
        val targetJsonFile = File(customerDir, "ocr_result.json")

        // 1. Salin atau buat ktp_original.jpg
        copyOrWriteImageFile(originalPhotoPathOrUri, targetOrigFile)

        // 2. Salin atau buat ktp_processed.jpg (fallback ke original bila processed belum ada)
        val effectiveProcSource = processedPhotoPathOrUri.ifBlank { originalPhotoPathOrUri }
        copyOrWriteImageFile(effectiveProcSource, targetProcFile)

        // 3. Tulis ocr_result.json
        try {
            val jsonContent = ocrStructuredJson.ifBlank { "{}" }
            targetJsonFile.writeText(jsonContent, Charsets.UTF_8)
        } catch (_: Throwable) {
        }

        return CustomerKtpFolderResult(
            customerFolder = customerDir.absolutePath,
            originalPhotoPath = if (targetOrigFile.exists() && targetOrigFile.length() > 0L) {
                targetOrigFile.absolutePath
            } else {
                originalPhotoPathOrUri
            },
            processedPhotoPath = if (targetProcFile.exists() && targetProcFile.length() > 0L) {
                targetProcFile.absolutePath
            } else {
                processedPhotoPathOrUri
            },
            ocrResultJsonPath = targetJsonFile.absolutePath
        )
    }

    /**
     * Overload praktis yang menerima `KtpOcrResult?` dan menyimpan JSON lengkap (Section 7 + Section 5 Raw Payload + Metadata Pelanggan)
     * ke `/Customers/<customerCode>/ocr_result.json`.
     */
    fun saveCustomerKtpArtifacts(
        context: Context,
        customerCode: String,
        originalPhotoPath: String,
        preprocessedPhotoPath: String,
        ocrResult: KtpOcrResult? = null
    ): CustomerKtpFolderResult {
        val fullJsonString = if (ocrResult != null) {
            val root = org.json.JSONObject()
            root.put("customer_code", customerCode.ifBlank { "CUST-SAF-0001" })
            root.put("scan_timestamp", ocrResult.scanTimestampIso)
            root.put("status_pembacaan", ocrResult.statusPembacaan)
            root.put("selected_image_variant", ocrResult.selectedImageVariant)
            root.put("total_processing_time_ms", ocrResult.totalProcessingTimeMs)
            root.put(
                "section_7_structured_data",
                try {
                    org.json.JSONObject(ocrResult.toSection7StructuredJsonString())
                } catch (_: Throwable) {
                    org.json.JSONObject()
                }
            )
            root.put(
                "structured_data",
                try {
                    org.json.JSONObject(ocrResult.toStructuredJsonString())
                } catch (_: Throwable) {
                    org.json.JSONObject()
                }
            )
            root.put(
                "ml_kit_raw_payload",
                try {
                    if (ocrResult.mlKitRawPayloadJson.isNotBlank()) {
                        org.json.JSONObject(ocrResult.mlKitRawPayloadJson)
                    } else {
                        org.json.JSONObject()
                    }
                } catch (_: Throwable) {
                    org.json.JSONObject()
                }
            )
            root.toString(2)
        } else {
            org.json.JSONObject().apply {
                put("customer_code", customerCode.ifBlank { "CUST-SAF-0001" })
            }.toString(2)
        }
        return saveCustomerKtpArtifacts(
            context = context,
            kodePelanggan = customerCode,
            originalPhotoPathOrUri = originalPhotoPath,
            processedPhotoPathOrUri = preprocessedPhotoPath,
            ocrStructuredJson = fullJsonString
        )
    }

    private fun copyOrWriteImageFile(sourcePath: String, destFile: File) {
        if (sourcePath.isBlank()) return
        try {
            val srcFile = File(sourcePath)
            if (srcFile.exists() && srcFile.isFile && srcFile.absolutePath != destFile.absolutePath) {
                srcFile.copyTo(destFile, overwrite = true)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Menyimpan seluruh variasi gambar hasil 11-Step Image Processing untuk keperluan
     * Developer OCR Diagnostic Mode (Section 14).
     */
    fun saveDiagnosticImageVariants(
        context: Context,
        processedResult: KtpProcessedImageResult
    ): Map<String, String> {
        val diagDir = File(context.filesDir, "ktp_diagnostics").apply {
            if (!exists()) mkdirs()
        }
        val ts = System.currentTimeMillis()
        val paths = mutableMapOf<String, String>()

        fun saveBmp(bmp: Bitmap?, suffix: String): String {
            if (bmp == null) return ""
            return try {
                val f = File(diagDir, "diag_${ts}_$suffix.jpg")
                FileOutputStream(f).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.flush()
                }
                f.absolutePath
            } catch (_: Throwable) {
                ""
            }
        }

        paths["original"] = saveBmp(processedResult.originalBitmap ?: processedResult.processedBitmap, "original")
        paths["cropped"] = saveBmp(processedResult.croppedBitmap ?: processedResult.processedBitmap, "cropped")
        paths["perspective"] = saveBmp(processedResult.perspectiveCorrectedBitmap ?: processedResult.processedBitmap, "perspective")
        paths["grayscale"] = saveBmp(processedResult.grayscaleBitmap ?: processedResult.processedBitmap, "grayscale")
        paths["clahe"] = saveBmp(processedResult.claheBitmap ?: processedResult.processedBitmap, "clahe")
        paths["threshold"] = saveBmp(processedResult.adaptiveThresholdBitmap ?: processedResult.processedBitmap, "threshold")
        return paths
    }
}

/**
 * Halaman / Dialog Diagnostik OCR Khusus Developer (Section 14 DEBUGGING MODE).
 * Menampilkan seluruh 12 indikator diagnostik:
 * 1. Original image
 * 2. Cropped image
 * 3. Perspective corrected image
 * 4. Grayscale image
 * 5. CLAHE image
 * 6. Threshold image
 * 7. Raw OCR output (JSON dengan blocks, lines, elements, bounding_boxes, processing_time)
 * 8. Bounding box visualization (Overlay koordinat X,Y di atas gambar KTP)
 * 9. Parsed JSON (Struktur Section 7 & D)
 * 10. Confidence setiap field
 * 11. Total processing time
 * 12. Error log & Audit Pipeline
 */
@Composable
fun KtpOcrDiagnosticDialog(
    ocrResult: KtpOcrResult,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        "1-6. Variasi Citra (6 Versi)",
        "7-8. Bounding Box & Raw OCR",
        "9-10. Parsed JSON & Confidence",
        "11-12. Waktu Proses & Error Log"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .testTag("dialog_ktp_ocr_diagnostic"),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Mode Diagnostik OCR",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "DEVELOPER OCR DIAGNOSTIC MODE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Engine: ${ocrResult.aiSourceEngine} • Total Waktu: ${ocrResult.totalProcessingTimeMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Diagnostik",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White,
                    edgePadding = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    tabTitles.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Items 1 to 6: Original, Cropped, Perspective Corrected, Grayscale, CLAHE, Threshold
                            DiagnosticImageCard(
                                stepTitle = "1. Original Image (Foto Asli Sebelum Diproses)",
                                subtitle = "Resolusi asli: ${ocrResult.qualityReport.originalWidth}x${ocrResult.qualityReport.originalHeight} px",
                                imagePath = ocrResult.savedOriginalPhotoPath
                            )
                            DiagnosticImageCard(
                                stepTitle = "2. Cropped Image (Auto-Crop Batas Kartu KTP)",
                                subtitle = "Area rasio kartu: ${(ocrResult.qualityReport.cardAreaRatio * 100).toInt()}%",
                                imagePath = ocrResult.diagnosticImagePaths["cropped"]
                                    ?.ifBlank { ocrResult.savedPreprocessedPhotoPath }
                                    ?: ocrResult.savedPreprocessedPhotoPath
                            )
                            DiagnosticImageCard(
                                stepTitle = "3. Perspective Corrected Image (Koreksi 4 Sudut & Rotasi)",
                                subtitle = "Koreksi kemiringan: ${"%.2f".format(ocrResult.qualityReport.detectedSkewDegrees)}°",
                                imagePath = ocrResult.diagnosticImagePaths["perspective"]
                                    ?.ifBlank { ocrResult.savedPreprocessedPhotoPath }
                                    ?: ocrResult.savedPreprocessedPhotoPath
                            )
                            DiagnosticImageCard(
                                stepTitle = "4. Grayscale Image (Konversi Luminansi ITU-R BT.601)",
                                subtitle = "Rata-rata luminansi: ${"%.1f".format(ocrResult.qualityReport.meanLuminance)}",
                                imagePath = ocrResult.diagnosticImagePaths["grayscale"]
                                    ?.ifBlank { ocrResult.savedPreprocessedPhotoPath }
                                    ?: ocrResult.savedPreprocessedPhotoPath
                            )
                            DiagnosticImageCard(
                                stepTitle = "5. CLAHE Enhanced Image (Adaptive Contrast + Denoised + Sharpened)",
                                subtitle = "Resolusi optimal OCR: ${ocrResult.qualityReport.processedWidth}x${ocrResult.qualityReport.processedHeight} px • Varian Terpilih: ${ocrResult.selectedImageVariant}",
                                imagePath = ocrResult.savedPreprocessedPhotoPath
                            )
                            DiagnosticImageCard(
                                stepTitle = "6. Adaptive Threshold Image (Local Integral Thresholding)",
                                subtitle = "Digunakan untuk memisahkan teks pada kondisi bayangan/silau",
                                imagePath = ocrResult.diagnosticImagePaths["threshold"]
                                    ?.ifBlank { ocrResult.savedPreprocessedPhotoPath }
                                    ?: ocrResult.savedPreprocessedPhotoPath
                            )
                        }

                        1 -> {
                            // Item 8: Bounding Box Visualization
                            DiagnosticBoundingBoxCard(ocrResult = ocrResult)

                            // Item 7: Raw OCR Output (with blocks, lines, elements, bounding_boxes, processing_time)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "7. Raw OCR Output & Spatial Payload (Google ML Kit V2 Latin)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Jumlah Elemen Spasial Terdeteksi: ${ocrResult.spatialElements.size} elemen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Surface(
                                        color = Color(0xFF0B1120),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = ocrResult.mlKitRawPayloadJson.ifBlank { ocrResult.rawOcrText.ifBlank { "(Kosong)" } },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFFE2E8F0),
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Item 10: Confidence Setiap Field
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "10. Skor Confidence & Status Validasi per Field (15 Field KTP)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                    val required15 = listOf(
                                        "nik" to "NIK",
                                        "nama" to "Nama",
                                        "tempat_lahir" to "Tempat Lahir",
                                        "tanggal_lahir" to "Tanggal Lahir",
                                        "jenis_kelamin" to "Jenis Kelamin",
                                        "alamat" to "Alamat",
                                        "rt" to "RT",
                                        "rw" to "RW",
                                        "kelurahan" to "Kel/Desa",
                                        "kecamatan" to "Kecamatan",
                                        "agama" to "Agama",
                                        "status_perkawinan" to "Status Kawin",
                                        "pekerjaan" to "Pekerjaan",
                                        "kewarganegaraan" to "Kewarganegaraan",
                                        "berlaku_hingga" to "Berlaku Hingga"
                                    )
                                    for ((key, label) in required15) {
                                        val detail = ocrResult.getFieldDetail(key)
                                        val barColor = when {
                                            detail.value.isBlank() || detail.confidence < 70 -> Color(0xFFEF4444)
                                            detail.confidence >= 90 -> Color(0xFF22C55E)
                                            else -> Color(0xFFF59E0B)
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "$label: ${detail.value.ifBlank { "[NOT_READ]" }}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${detail.confidence}% (${detail.section7Status})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = barColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { (detail.confidence / 100f).coerceIn(0f, 1f) },
                                                color = barColor,
                                                trackColor = Color(0xFF334155),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                            )
                                        }
                                    }
                                }
                            }

                            // Item 9: Parsed JSON (Section 7 Structure)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "9. Parsed Structured JSON (Section 7 Format)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = Color(0xFF0B1120),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = ocrResult.toSection7StructuredJsonString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFF86EFAC),
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Item 11: Total Processing Time & Pipeline Breakdown
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "11. Total Processing Time & Metrik Kualitas Gambar",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "• Total Waktu Pemrosesan Pipeline: ${ocrResult.totalProcessingTimeMs} ms\n" +
                                            "• Waktu Image Preprocessing (11-Step): ${ocrResult.imageProcessingTimeMs} ms\n" +
                                            "• Waktu ML Kit Text Recognition V2: ${ocrResult.mlKitProcessingTimeMs} ms\n" +
                                            "• Waktu AI Vision Secondary Verification: ${ocrResult.aiVerificationTimeMs} ms\n" +
                                            "• Varian Preprocessing Terpilih: ${ocrResult.selectedImageVariant}\n" +
                                            "• Ketajaman (Sharpness Score): ${"%.2f".format(ocrResult.qualityReport.sharpnessScore)}\n" +
                                            "• Pencahayaan (Mean Luminance): ${"%.2f".format(ocrResult.qualityReport.meanLuminance)}\n" +
                                            "• Kontras (StdDev): ${"%.2f".format(ocrResult.qualityReport.contrastStdDev)}\n" +
                                            "• Glare Ratio: ${"%.3f".format(ocrResult.qualityReport.glareRatio)}\n" +
                                            "• Waktu Pemindaian (ISO): ${ocrResult.scanTimestampIso}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }

                            // Item 12: Error Log & Audit Trail
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "12. Error Log, Peringatan Validasi & Jejak Audit Pipeline",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFFF87171),
                                        fontWeight = FontWeight.Bold
                                    )
                                    val combinedLogs = buildString {
                                        if (ocrResult.failureReason.isNotBlank()) {
                                            appendLine("[FAILURE REASON]: ${ocrResult.failureReason}")
                                        }
                                        if (ocrResult.validationWarnings.isNotEmpty()) {
                                            appendLine("[VALIDATION WARNINGS]:")
                                            ocrResult.validationWarnings.forEach { appendLine(" - $it") }
                                        }
                                        if (ocrResult.diagnosticErrorLogs.isNotEmpty()) {
                                            appendLine("[PIPELINE & ERROR LOGS]:")
                                            ocrResult.diagnosticErrorLogs.forEach { appendLine(" • $it") }
                                        }
                                        if (isEmpty()) {
                                            append("Tidak ada error fatal. Seluruh tahapan pipeline berjalan normal.")
                                        }
                                    }
                                    Surface(
                                        color = Color(0xFF0B1120),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = combinedLogs,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFFFCA5A5),
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticImageCard(
    stepTitle: String,
    subtitle: String,
    imagePath: String
) {
    val bmp = remember(imagePath) {
        if (imagePath.isNotBlank() && File(imagePath).exists()) {
            try {
                BitmapFactory.decodeFile(imagePath)
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stepTitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stepTitle,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0B1120)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (imagePath.isBlank()) "Gambar belum tersedia" else "Path: $imagePath",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticBoundingBoxCard(ocrResult: KtpOcrResult) {
    val imagePath = ocrResult.savedPreprocessedPhotoPath.ifBlank { ocrResult.savedOriginalPhotoPath }
    val bmp = remember(imagePath) {
        if (imagePath.isNotBlank() && File(imagePath).exists()) {
            try {
                BitmapFactory.decodeFile(imagePath)
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "8. Bounding Box Visualization (Koordinat X,Y TextBlock & TextElement)",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Kotak Hijau = Label Anchor KTP • Kotak Biru Muda = Nilai Teks KTP (${ocrResult.spatialElements.size} kotak)",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.585f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B1120))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Visualisasi Bounding Box KTP",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val imgW = (bmp?.width ?: ocrResult.qualityReport.processedWidth).coerceAtLeast(1000).toFloat()
                    val imgH = (bmp?.height ?: ocrResult.qualityReport.processedHeight).coerceAtLeast(630).toFloat()
                    val scaleX = size.width / imgW
                    val scaleY = size.height / imgH

                    for (el in ocrResult.spatialElements) {
                        val isAnchor = el.text.uppercase() in setOf(
                            "NIK", "NAMA", "TEMPAT/TGL", "LAHIR", "JENIS", "KELAMIN",
                            "ALAMAT", "RT/RW", "KEL/DESA", "KECAMATAN", "AGAMA",
                            "STATUS", "PERKAWINAN", "PEKERJAAN", "KEWARGANEGARAAN", "BERLAKU"
                        )
                        val boxColor = if (isAnchor) Color(0xFF22C55E) else Color(0xFF38BDF8)
                        drawRect(
                            color = boxColor,
                            topLeft = Offset(el.left * scaleX, el.top * scaleY),
                            size = Size(
                                ((el.right - el.left).coerceAtLeast(4)) * scaleX,
                                ((el.bottom - el.top).coerceAtLeast(4)) * scaleY
                            ),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }
            }
        }
    }
}
