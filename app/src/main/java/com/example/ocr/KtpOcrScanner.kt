package com.example.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import com.example.media.PhotoCategory
import com.example.media.SmartImageProcessor
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Detail terstruktur untuk setiap field hasil ekstraksi KTP sesuai Section D & F:
 * - value: Nilai string hasil pembacaan
 * - confidence: Skor keyakinan 0..100
 * - verificationStatus:
 *   - "HIJAU_OTOMATIS" (Confidence >= 90%: Otomatis terisi dan ditandai hijau)
 *   - "KUNING_PERLU_VERIFIKASI" (Confidence 70%-89%: Terisi tetapi memerlukan verifikasi admin)
 *   - "MERAH_KOREKSI_MANUAL" (Confidence < 70%: Jangan dianggap data valid otomatis, tampilkan tombol koreksi manual)
 *   - "TIDAK TERBACA" (Jika tidak terbaca / kosong)
 * - source: Sumber ekstraksi ("ML_KIT_V2", "GEMINI_VISION", "HYBRID_CONSENSUS", atau "NONE")
 */
data class KtpFieldDetail(
    val value: String = "",
    val confidence: Int = 0,
    val verificationStatus: String = "TIDAK TERBACA",
    val source: String = "NONE"
) {
    /**
     * Status standar Section 7 & Section 10:
     * - "TERBACA" (90%-100%: dapat diisi otomatis)
     * - "PERLU_VERIFIKASI" (70%-89%: perlu verifikasi admin)
     * - "NOT_READ" (di bawah 70% atau tidak ditemukan: wajib koreksi manual)
     */
    val section7Status: String
        get() = computeSection7Status(value, confidence)

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("value", value)
            put("confidence", confidence.coerceIn(0, 100))
            put("status", section7Status)
            put("verification_status", verificationStatus)
            put("source", source)
        }
    }

    fun toSection7JsonObject(): JSONObject {
        return JSONObject().apply {
            put("value", value)
            put("confidence", if (value.isBlank()) 0 else confidence.coerceIn(0, 100))
            put("status", section7Status)
        }
    }

    companion object {
        fun computeVerificationStatus(value: String, confidence: Int): String {
            if (value.isBlank()) return "TIDAK TERBACA"
            return when {
                confidence >= 90 -> "HIJAU_OTOMATIS"
                confidence in 70..89 -> "KUNING_PERLU_VERIFIKASI"
                else -> "MERAH_KOREKSI_MANUAL"
            }
        }

        fun computeSection7Status(value: String, confidence: Int): String {
            if (value.isBlank()) return "NOT_READ"
            return when {
                confidence >= 90 -> "TERBACA"
                confidence in 70..89 -> "PERLU_VERIFIKASI"
                else -> "NOT_READ"
            }
        }
    }
}

/**
 * Elemen spasial 2D dari Google ML Kit Text Recognition V2 (TextBlocks -> Lines -> Elements + BoundingBox).
 */
data class MlKitSpatialElement(
    val text: String,
    val blockIndex: Int,
    val lineIndex: Int,
    val elementIndex: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val centerX: Float,
    val centerY: Float
)

/**
 * Skor confidence per field sesuai Section D & F (rentang 0..100).
 */
data class KtpConfidenceScores(
    val nik: Int = 0,
    val namaLengkap: Int = 0,
    val tempatLahir: Int = 0,
    val tanggalLahir: Int = 0,
    val jenisKelamin: Int = 0,
    val alamat: Int = 0,
    val rt: Int = 0,
    val rw: Int = 0,
    val kelurahan: Int = 0,
    val kecamatan: Int = 0,
    val agama: Int = 0,
    val statusPerkawinan: Int = 0,
    val pekerjaan: Int = 0,
    val kewarganegaraan: Int = 0,
    val berlakuHingga: Int = 0
) {
    fun getScoreForKey(jsonKey: String): Int = when (jsonKey) {
        "nik" -> nik
        "nama", "nama_lengkap" -> namaLengkap
        "tempat_lahir" -> tempatLahir
        "tanggal_lahir" -> tanggalLahir
        "jenis_kelamin" -> jenisKelamin
        "alamat" -> alamat
        "rt" -> if (rt > 0) rt else alamat
        "rw" -> if (rw > 0) rw else alamat
        "kelurahan", "kelurahan_desa" -> if (kelurahan > 0) kelurahan else alamat
        "kecamatan" -> if (kecamatan > 0) kecamatan else alamat
        "agama" -> if (agama > 0) agama else 90
        "status_perkawinan" -> if (statusPerkawinan > 0) statusPerkawinan else 90
        "pekerjaan" -> if (pekerjaan > 0) pekerjaan else 88
        "kewarganegaraan" -> if (kewarganegaraan > 0) kewarganegaraan else 95
        "berlaku_hingga" -> if (berlakuHingga > 0) berlakuHingga else 95
        else -> 85
    }

    fun toAllFieldsJsonObject(): JSONObject {
        return JSONObject().apply {
            put("nik", nik.coerceIn(0, 100))
            put("nama", namaLengkap.coerceIn(0, 100))
            put("nama_lengkap", namaLengkap.coerceIn(0, 100))
            put("tempat_lahir", tempatLahir.coerceIn(0, 100))
            put("tanggal_lahir", tanggalLahir.coerceIn(0, 100))
            put("jenis_kelamin", jenisKelamin.coerceIn(0, 100))
            put("alamat", alamat.coerceIn(0, 100))
            put("rt", (if (rt > 0) rt else alamat).coerceIn(0, 100))
            put("rw", (if (rw > 0) rw else alamat).coerceIn(0, 100))
            put("kelurahan", (if (kelurahan > 0) kelurahan else alamat).coerceIn(0, 100))
            put("kecamatan", (if (kecamatan > 0) kecamatan else alamat).coerceIn(0, 100))
            put("agama", (if (agama > 0) agama else 90).coerceIn(0, 100))
            put("status_perkawinan", (if (statusPerkawinan > 0) statusPerkawinan else 90).coerceIn(0, 100))
            put("pekerjaan", (if (pekerjaan > 0) pekerjaan else 88).coerceIn(0, 100))
            put("kewarganegaraan", (if (kewarganegaraan > 0) kewarganegaraan else 95).coerceIn(0, 100))
            put("berlaku_hingga", (if (berlakuHingga > 0) berlakuHingga else 95).coerceIn(0, 100))
        }
    }
}

/**
 * Hasil Ekstraksi Terstruktur AI OCR e-KTP Indonesia (15 Field Wajib Section 7 & D + Field Pendukung + Confidence + Quality Report + Diagnostik Section 14).
 */
data class KtpOcrResult(
    val nik: String = "",
    val namaLengkap: String = "",
    val tempatLahir: String = "",
    val tanggalLahir: String = "", // DD-MM-YYYY
    val jenisKelamin: String = "", // Laki-laki / Perempuan / LAKI-LAKI / PEREMPUAN / ""
    val golonganDarah: String = "", // O / A / B / AB / - / ""
    val alamat: String = "",
    val rt: String = "", // 3 digit terpisah, misal "003"
    val rw: String = "", // 3 digit terpisah, misal "008"
    val rtRw: String = "", // Gabungan "003/008" untuk kompatibilitas
    val kelurahanDesa: String = "",
    val kecamatan: String = "",
    val kabupatenKota: String = "",
    val provinsi: String = "",
    val agama: String = "",
    val statusPerkawinan: String = "",
    val pekerjaan: String = "",
    val kewarganegaraan: String = "",
    val berlakuHingga: String = "",
    val confidence: KtpConfidenceScores = KtpConfidenceScores(),
    val statusPembacaan: String = "berhasil", // "berhasil" | "perlu_verifikasi" | "gagal"
    val rawOcrText: String = "",
    val savedOriginalPhotoPath: String = "",
    val savedPreprocessedPhotoPath: String = "",
    val unreadableFields: List<String> = emptyList(),
    val lowConfidenceFields: List<String> = emptyList(),
    val manualCorrectionFields: List<String> = emptyList(),
    val validationWarnings: List<String> = emptyList(),
    val qualityReport: KtpImageQualityReport = KtpImageQualityReport(),
    val aiSourceEngine: String = "Gemini Vision + ML Kit Spatial",
    val fieldDetails: Map<String, KtpFieldDetail> = emptyMap(),
    val failureReason: String = "",
    val spatialElements: List<MlKitSpatialElement> = emptyList(),
    val mlKitRawPayloadJson: String = "",
    val diagnosticImagePaths: Map<String, String> = emptyMap(),
    val selectedImageVariant: String = "CLAHE_ENHANCED",
    val imageProcessingTimeMs: Long = 0L,
    val mlKitProcessingTimeMs: Long = 0L,
    val aiVerificationTimeMs: Long = 0L,
    val totalProcessingTimeMs: Long = 0L,
    val scanTimestampIso: String = "",
    val diagnosticErrorLogs: List<String> = emptyList()
) {
    /** Alias properti sesuai nama key JSON Section D ("nama" dan "kelurahan"). */
    val nama: String
        get() = namaLengkap

    val kelurahan: String
        get() = kelurahanDesa

    /** Representasi huruf kapital jenis kelamin ("LAKI-LAKI" atau "PEREMPUAN") sesuai Section 8. */
    val jenisKelaminUppercase: String
        get() = when {
            jenisKelamin.equals("Laki-laki", ignoreCase = true) -> "LAKI-LAKI"
            jenisKelamin.equals("Perempuan", ignoreCase = true) -> "PEREMPUAN"
            else -> ""
        }

    /**
     * Mengambil detail terstruktur (`value`, `confidence`, `status`, `verification_status`, `source`)
     * untuk suatu field sesuai spesifikasi Section 7, D & F.
     */
    fun getFieldDetail(jsonKey: String): KtpFieldDetail {
        val canonicalKey = when (jsonKey) {
            "nama_lengkap" -> "nama"
            "kelurahan_desa" -> "kelurahan"
            else -> jsonKey
        }
        fieldDetails[canonicalKey]?.let { return it }
        fieldDetails[jsonKey]?.let { return it }

        val value = getFieldValueByJsonKey(jsonKey)
        val score = if (value.isBlank()) 0 else confidence.getScoreForKey(jsonKey)
        val status = KtpFieldDetail.computeVerificationStatus(value, score)
        val src = when {
            value.isBlank() -> "NONE"
            aiSourceEngine.contains("Gemini", ignoreCase = true) && aiSourceEngine.contains("ML Kit", ignoreCase = true) -> "HYBRID_CONSENSUS"
            aiSourceEngine.contains("Gemini", ignoreCase = true) -> "GEMINI_VISION"
            else -> "ML_KIT_V2"
        }
        return KtpFieldDetail(
            value = value,
            confidence = score,
            verificationStatus = status,
            source = src
        )
    }

    /**
     * Menghasilkan string JSON murni dengan struktur persis sesuai Section 7:
     * {
     *   "nik": { "value": "", "confidence": 0, "status": "NOT_READ" },
     *   "nama": { "value": "", "confidence": 0, "status": "NOT_READ" },
     *   ...
     * }
     */
    fun toSection7StructuredJsonString(): String {
        val root = JSONObject()
        val required15Keys = listOf(
            "nik", "nama", "tempat_lahir", "tanggal_lahir", "jenis_kelamin",
            "alamat", "rt", "rw", "kelurahan", "kecamatan",
            "agama", "status_perkawinan", "pekerjaan", "kewarganegaraan", "berlaku_hingga"
        )
        for (key in required15Keys) {
            val detail = getFieldDetail(key)
            val normalizedValue = if (key == "jenis_kelamin" && detail.value.isNotBlank()) {
                jenisKelaminUppercase
            } else {
                detail.value
            }
            val effectiveConf = if (normalizedValue.isBlank()) 0 else detail.confidence.coerceIn(0, 100)
            val sec7Status = KtpFieldDetail.computeSection7Status(normalizedValue, effectiveConf)
            val fieldObj = JSONObject().apply {
                put("value", normalizedValue)
                put("confidence", effectiveConf)
                put("status", sec7Status)
            }
            root.put(key, fieldObj)
        }
        return root.toString(2)
    }

    /**
     * Menghasilkan string JSON terstruktur lengkap (mendukung skema Section D & Section 7 sekaligus).
     */
    fun toStructuredJsonString(): String {
        val root = JSONObject()
        // 15 Field Wajib Section D + backward compatibility keys
        root.put("nik", nik)
        root.put("nama", namaLengkap)
        root.put("nama_lengkap", namaLengkap)
        root.put("tempat_lahir", tempatLahir)
        root.put("tanggal_lahir", tanggalLahir)
        root.put("jenis_kelamin", jenisKelamin)
        root.put("golongan_darah", golonganDarah)
        root.put("alamat", alamat)
        root.put("rt", rt)
        root.put("rw", rw)
        root.put("kelurahan", kelurahanDesa)
        root.put("kecamatan", kecamatan)
        root.put("kabupaten_kota", kabupatenKota)
        root.put("provinsi", provinsi)
        root.put("agama", agama)
        root.put("status_perkawinan", statusPerkawinan)
        root.put("pekerjaan", pekerjaan)
        root.put("kewarganegaraan", kewarganegaraan)
        root.put("berlaku_hingga", berlakuHingga)

        val confObj = confidence.toAllFieldsJsonObject()
        root.put("confidence", confObj)

        val detailsObj = JSONObject()
        val required15Keys = listOf(
            "nik", "nama", "tempat_lahir", "tanggal_lahir", "jenis_kelamin",
            "alamat", "rt", "rw", "kelurahan", "kecamatan",
            "agama", "status_perkawinan", "pekerjaan", "kewarganegaraan", "berlaku_hingga"
        )
        for (key in required15Keys) {
            detailsObj.put(key, getFieldDetail(key).toJsonObject())
        }
        root.put("field_details", detailsObj)
        root.put("status_pembacaan", statusPembacaan)
        return root.toString(2)
    }

    fun getConfidenceTierStatus(jsonKey: String): String {
        return getFieldDetail(jsonKey).verificationStatus
    }

    fun isGreenConfidence90Plus(jsonKey: String): Boolean {
        val detail = getFieldDetail(jsonKey)
        return detail.value.isNotBlank() && detail.confidence >= 90
    }

    fun isYellowConfidence70To89(jsonKey: String): Boolean {
        val detail = getFieldDetail(jsonKey)
        return detail.value.isNotBlank() && detail.confidence in 70..89
    }

    fun isRedManualCorrectionUnder70(jsonKey: String): Boolean {
        val detail = getFieldDetail(jsonKey)
        return detail.value.isNotBlank() && detail.confidence < 70
    }

    fun isFieldUnreadable(jsonKey: String): Boolean {
        return getFieldValueByJsonKey(jsonKey).isBlank()
    }

    /**
     * Mengecek apakah field tertentu memerlukan verifikasi admin (kuning).
     */
    fun needsYellowVerification(jsonKey: String): Boolean {
        val value = getFieldValueByJsonKey(jsonKey)
        if (value.isBlank()) return true
        val score = confidence.getScoreForKey(jsonKey)
        return score < 80
    }

    /**
     * Mengecek apakah field tertentu memiliki confidence di bawah ambang auto-fill (< 50).
     */
    fun isBelowAutoFillThreshold(jsonKey: String): Boolean {
        val score = confidence.getScoreForKey(jsonKey)
        return score < 50
    }

    /**
     * Mengambil nilai field murni berdasarkan JSON key (Section D).
     */
    fun getFieldValueByJsonKey(jsonKey: String): String = when (jsonKey) {
        "nik" -> nik
        "nama", "nama_lengkap" -> namaLengkap
        "tempat_lahir" -> tempatLahir
        "tanggal_lahir" -> tanggalLahir
        "jenis_kelamin" -> jenisKelamin
        "golongan_darah" -> golonganDarah
        "alamat" -> alamat
        "rt" -> rt
        "rw" -> rw
        "kelurahan", "kelurahan_desa" -> kelurahanDesa
        "kecamatan" -> kecamatan
        "kabupaten_kota" -> kabupatenKota
        "provinsi" -> provinsi
        "agama" -> agama
        "status_perkawinan" -> statusPerkawinan
        "pekerjaan" -> pekerjaan
        "kewarganegaraan" -> kewarganegaraan
        "berlaku_hingga" -> berlakuHingga
        else -> ""
    }

    /**
     * Mengambil nilai field untuk Auto-Fill ke Form Pelanggan berdasarkan JSON Key.
     */
    fun getAutoFillValueByJsonKey(
        jsonKey: String,
        includeLowConfidenceUnder50: Boolean = false
    ): String {
        val rawVal = getFieldValueByJsonKey(jsonKey)
        if (rawVal.isBlank()) return ""
        if (!includeLowConfidenceUnder50 && isBelowAutoFillThreshold(jsonKey)) {
            return ""
        }
        return rawVal
    }

    /**
     * Mengambil nilai field untuk Auto-Fill sesuai aturan ketat Section F:
     * Hanya field dengan confidence >= 70% yang dianggap valid untuk auto-fill langsung;
     * field dengan confidence < 70% tidak diisi otomatis sebelum admin menekan tombol "Koreksi Manual".
     */
    fun getStrictSectionFAutoFillValue(
        jsonKey: String,
        allowManualCorrectionOverride: Boolean = false
    ): String {
        val detail = getFieldDetail(jsonKey)
        if (detail.value.isBlank()) return ""
        if (detail.confidence < 70 && !allowManualCorrectionOverride) return ""
        return detail.value
    }

    /**
     * Menghasilkan peta (Map) key-value terstruktur untuk pengisian form pelanggan (Section D).
     */
    fun toCustomerFormAutoFillMap(includeLowConfidenceUnder50: Boolean = false): Map<String, String> {
        val keys = listOf(
            "nik",
            "nama",
            "nama_lengkap",
            "tempat_lahir",
            "tanggal_lahir",
            "jenis_kelamin",
            "golongan_darah",
            "alamat",
            "rt",
            "rw",
            "kelurahan",
            "kecamatan",
            "kabupaten_kota",
            "provinsi",
            "agama",
            "status_perkawinan",
            "pekerjaan",
            "kewarganegaraan",
            "berlaku_hingga"
        )
        return keys.associateWith { key ->
            getAutoFillValueByJsonKey(key, includeLowConfidenceUnder50)
        }
    }
}

object KtpOcrScanner {

    private val KNOWN_RELIGIONS = listOf(
        "ISLAM", "KRISTEN", "KATOLIK", "KATHOLIK", "PROTESTAN", "HINDU", "BUDDHA", "BUDHA", "KONGHUCU", "KHONGHUCU", "KEPERCAYAAN"
    )

    private val KNOWN_MARITAL_STATUSES = listOf(
        "BELUM KAWIN", "CERAI HIDUP", "CERAI MATI", "KAWIN"
    )

    /**
     * Daftar kata pekerjaan/profesi standar e-KTP Indonesia yang DILARANG masuk ke kolom Nama (Section E.6).
     */
    private val FORBIDDEN_JOB_WORDS = listOf(
        "WIRASWASTA", "KARYAWAN SWASTA", "KARYAWAN BUMN", "KARYAWAN BUMD", "KARYAWAN HONORER",
        "PELAJAR/MAHASISWA", "PELAJAR", "MAHASISWA", "MENGURUS RUMAH TANGGA", "IBU RUMAH TANGGA",
        "BELUM/TIDAK BEKERJA", "TIDAK BEKERJA", "PEGAWAI NEGERI SIPIL", "PNS", "TNI", "POLRI",
        "KEPOLISIAN RI", "PERDAGANGAN", "PEDAGANG", "PETANI/PEKEBUN", "PETANI", "PEKEBUN",
        "PETERNAK", "NELAYAN/PERIKANAN", "NELAYAN", "BURUH HARIAN LEPAS", "BURUH TANI",
        "BURUH NELAYAN", "BURUH PETERNAKAN", "BURUH", "PEMBANTU RUMAH TANGGA", "TUKANG CUKUR",
        "TUKANG LISTRIK", "TUKANG BATU", "TUKANG KAYU", "TUKANG JAHIT", "PENJAHIT", "MEKANIK",
        "DOKTER", "BIDAN", "PERAWAT", "APOTEKER", "GURU", "DOSEN", "SOPIR", "PENGEMUDI",
        "PENSIUNAN", "WIRAUSAHA", "WIRANIAGA", "SENIMAN", "WARTAWAN", "USTADZ", "PENDETA",
        "PASTOR", "NOTARIS", "PENGACARA", "ADVOKAT", "KONSULTAN", "ARSITEK", "AKUNTAN",
        "PERANGKAT DESA", "KEPALA DESA", "BIARAWATI"
    )

    private val FORBIDDEN_NAME_WORDS = listOf(
        "PROVINSI", "PROPINSI", "KABUPATEN", "KOTA ", "NIK", "TEMPAT", "TGL LAHIR",
        "JENIS KELAMIN", "GOL. DARAH", "GOL DARAH", "ALAMAT", "RT/RW", "KEL/DESA",
        "KELURAHAN", "KECAMATAN", "AGAMA", "STATUS PERKAWINAN", "PEKERJAAN",
        "KEWARGANEGARAAN", "BERLAKU HINGGA", "SEUMUR HIDUP", "REPUBLIK INDONESIA",
        "KARTU TANDA PENDUDUK", "JL.", "JALAN ", "DSN.", "DUSUN ", "PERUM ", "BLOK ",
        "LAKI-LAKI", "PEREMPUAN", "BELUM KAWIN", "CERAI HIDUP", "CERAI MATI"
    )
    // Catatan: kata tunggal "KAWIN" diperiksa secara utuh di KNOWN_MARITAL_STATUSES agar tidak menolak nama orang yang sah.

    /**
     * Schema JSON terstruktur untuk Gemini Vision (Section B & C).
     */
    private val ktpGeminiResponseSchema: Schema = Schema.obj(
        mapOf(
            "nik" to Schema.string(description = "16 digit angka NIK KTP Indonesia dalam format string. Pertahankan angka nol di depan. Kosongkan jika tidak terbaca."),
            "nama_lengkap" to Schema.string(description = "Nama lengkap pemilik KTP sesuai label 'Nama'. JANGAN masukkan nama Provinsi atau Kabupaten/Kota ke dalam nama_lengkap."),
            "tempat_lahir" to Schema.string(description = "Kota/Kabupaten tempat lahir sebelum tanda koma pada baris 'Tempat/Tgl Lahir'."),
            "tanggal_lahir" to Schema.string(description = "Tanggal lahir dengan format DD-MM-YYYY pada baris 'Tempat/Tgl Lahir'."),
            "jenis_kelamin" to Schema.string(description = "Hanya bernilai 'Laki-laki' atau 'Perempuan' (atau string kosong jika tidak terbaca)."),
            "golongan_darah" to Schema.string(description = "Golongan darah pada KTP (A, B, AB, O, atau -). Kosongkan jika tidak terlihat."),
            "alamat" to Schema.string(description = "Nama jalan, nomor rumah, blok, atau dusun sesuai baris 'Alamat' tanpa RT/RW dan tanpa Kelurahan."),
            "rt" to Schema.string(description = "3 digit nomor RT dari baris 'RT/RW', contoh '003'."),
            "rw" to Schema.string(description = "3 digit nomor RW dari baris 'RT/RW', contoh '008'."),
            "kelurahan" to Schema.string(description = "Nama Kelurahan atau Desa sesuai baris 'Kel/Desa'."),
            "kecamatan" to Schema.string(description = "Nama Kecamatan sesuai baris 'Kecamatan'."),
            "kabupaten_kota" to Schema.string(description = "Nama Kabupaten atau Kota dari bagian header atas KTP (baris kedua di bawah PROVINSI), contoh 'KOTA SURAKARTA'."),
            "provinsi" to Schema.string(description = "Nama Provinsi dari bagian header paling atas KTP, contoh 'JAWA TENGAH'."),
            "agama" to Schema.string(description = "Agama sesuai baris 'Agama' (contoh: ISLAM, KRISTEN, KATOLIK, HINDU, BUDDHA, KONGHUCU)."),
            "status_perkawinan" to Schema.string(description = "Status perkawinan sesuai baris 'Status Perkawinan' (KAWIN, BELUM KAWIN, CERAI HIDUP, CERAI MATI)."),
            "pekerjaan" to Schema.string(description = "Pekerjaan sesuai baris 'Pekerjaan'."),
            "kewarganegaraan" to Schema.string(description = "Kewarganegaraan sesuai baris 'Kewarganegaraan' (WNI atau WNA)."),
            "berlaku_hingga" to Schema.string(description = "Masa berlaku KTP sesuai baris 'Berlaku Hingga' (SEUMUR HIDUP atau tanggal)."),
            "confidence" to Schema.obj(
                mapOf(
                    "nik" to Schema.integer(description = "Tingkat keyakinan pembacaan NIK (0-100)."),
                    "nama_lengkap" to Schema.integer(description = "Tingkat keyakinan pembacaan Nama Lengkap (0-100)."),
                    "tempat_lahir" to Schema.integer(description = "Tingkat keyakinan pembacaan Tempat Lahir (0-100)."),
                    "tanggal_lahir" to Schema.integer(description = "Tingkat keyakinan pembacaan Tanggal Lahir (0-100)."),
                    "jenis_kelamin" to Schema.integer(description = "Tingkat keyakinan pembacaan Jenis Kelamin (0-100)."),
                    "alamat" to Schema.integer(description = "Tingkat keyakinan pembacaan Alamat & RT/RW (0-100).")
                )
            ),
            "status_pembacaan" to Schema.string(description = "'berhasil' jika mayoritas data terbaca jelas, 'perlu_verifikasi' jika sebagian meragukan, atau 'gagal' jika bukan KTP/tidak terbaca.")
        )
    )

    private const val GEMINI_KTP_PROMPT = """
        Anda adalah sistem AI OCR khusus pembaca KTP Elektronik (e-KTP) Republik Indonesia.
        Tugas Anda adalah membaca identitas pada gambar KTP Indonesia secara akurat berdasarkan label dan tata letak resmi e-KTP Indonesia:
        
        STRUKTUR TATA LETAK e-KTP INDONESIA:
        1. Bagian Paling Atas Tengah (Header):
           - Baris 1: "PROVINSI [NAMA PROVINSI]" -> masukkan hanya ke field "provinsi" (misal: "JAWA TENGAH").
           - Baris 2: "KABUPATEN [NAMA]" atau "KOTA [NAMA]" -> masukkan hanya ke field "kabupaten_kota" (misal: "KOTA SURAKARTA").
           PENTING: JANGAN PERNAH memasukkan teks PROVINSI atau KOTA/KABUPATEN ke dalam "nama_lengkap"!
        2. Di bawah Header:
           - "NIK : [16 digit angka]" -> masukkan ke "nik" sebagai string 16 digit (pertahankan angka 0 di depan).
        3. Daftar Field Berlabel di Sisi Kiri (dengan tanda titik dua ':' di tengah):
           - "Nama" -> masukkan ke "nama_lengkap".
           - "Tempat/Tgl Lahir" -> pisahkan tempat lahir sebelum koma ke "tempat_lahir" dan tanggal lahir ke "tanggal_lahir" dengan format DD-MM-YYYY (contoh: "18-07-1992").
           - "Jenis Kelamin" -> konversi menjadi tepat "Laki-laki" atau "Perempuan". Di sebelah kanannya terdapat "Gol. Darah" -> masukkan ke "golongan_darah".
           - "Alamat" -> masukkan nama jalan/dusun/nomor rumah ke "alamat".
           - "RT/RW" -> pisahkan nomor RT ke "rt" (3 digit, contoh "003") dan RW ke "rw" (3 digit, contoh "008").
           - "Kel/Desa" -> masukkan ke "kelurahan". JANGAN tertukar dengan kecamatan!
           - "Kecamatan" -> masukkan ke "kecamatan".
           - "Agama" -> masukkan ke "agama".
           - "Status Perkawinan" -> masukkan ke "status_perkawinan".
           - "Pekerjaan" -> masukkan ke "pekerjaan".
           - "Kewarganegaraan" -> masukkan ke "kewarganegaraan" (WNI / WNA).
           - "Berlaku Hingga" -> masukkan ke "berlaku_hingga" (biasanya "SEUMUR HIDUP").

        ATURAN WAJIB:
        - Jangan menebak atau mengarang informasi yang tidak terlihat pada gambar.
        - Jika suatu field tidak dapat dibaca sama sekali, isi dengan string kosong "" dan beri skor confidence 0.
        - Berikan skor confidence (0 sampai 100) secara jujur sesuai kejelasan tulisan pada foto KTP.
    """

    /**
     * Validasi NIK harus tepat 16 digit angka (string, mempertahankan angka 0 di depan).
     */
    fun isValid16DigitNik(nik: String): Boolean {
        val trimmed = nik.trim()
        return trimmed.length == 16 && trimmed.all { it.isDigit() }
    }

    /**
     * Validasi Tanggal Lahir harus berformat DD-MM-YYYY dan memiliki tanggal/bulan/tahun yang sah.
     */
    fun isValidBirthDate(dateStr: String): Boolean {
        val trimmed = dateStr.trim()
        val match = Regex("""^(\d{2})-(\d{2})-(\d{4})$""").find(trimmed) ?: return false
        val day = match.groupValues[1].toIntOrNull() ?: return false
        val month = match.groupValues[2].toIntOrNull() ?: return false
        val year = match.groupValues[3].toIntOrNull() ?: return false
        if (day !in 1..31 || month !in 1..12 || year !in 1900..2026) return false
        val maxDaysInMonth = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day <= maxDaysInMonth
    }

    /**
     * Normalisasi string tanggal lahir menjadi format DD-MM-YYYY.
     */
    fun normalizeBirthDateToDdMmYyyy(rawDate: String): String {
        val cleaned = rawDate.trim()
        if (cleaned.isBlank()) return ""

        // Pola DD-MM-YYYY, DD/MM/YYYY, DD.MM.YYYY, DD MM YYYY
        val dmyMatch = Regex("""(\d{1,2})[\-/.\s]+(\d{1,2})[\-/.\s]+((?:19|20)\d{2})""").find(cleaned)
        if (dmyMatch != null) {
            val d = dmyMatch.groupValues[1].padStart(2, '0')
            val m = dmyMatch.groupValues[2].padStart(2, '0')
            val y = dmyMatch.groupValues[3]
            val formatted = "$d-$m-$y"
            return if (isValidBirthDate(formatted)) formatted else ""
        }

        // Pola YYYY-MM-DD
        val ymdMatch = Regex("""((?:19|20)\d{2})[\-/.\s]+(\d{1,2})[\-/.\s]+(\d{1,2})""").find(cleaned)
        if (ymdMatch != null) {
            val y = ymdMatch.groupValues[1]
            val m = ymdMatch.groupValues[2].padStart(2, '0')
            val d = ymdMatch.groupValues[3].padStart(2, '0')
            val formatted = "$d-$m-$y"
            return if (isValidBirthDate(formatted)) formatted else ""
        }

        // Pola 8 digit berurutan DDMMYYYY (misal 18071992)
        val compactMatch = Regex("""^(\d{2})(\d{2})((?:19|20)\d{2})$""").find(cleaned.filter { it.isDigit() })
        if (compactMatch != null) {
            val formatted = "${compactMatch.groupValues[1]}-${compactMatch.groupValues[2]}-${compactMatch.groupValues[3]}"
            return if (isValidBirthDate(formatted)) formatted else ""
        }

        return ""
    }

    /**
     * Normalisasi Jenis Kelamin hanya boleh "Laki-laki" atau "Perempuan" (atau "" jika tidak terbaca).
     */
    fun normalizeJenisKelamin(rawGender: String): String {
        val upper = rawGender.uppercase().trim()
        if (upper.isBlank()) return ""
        return when {
            upper.contains("PEREMPUAN") || upper.contains("WANITA") || upper == "P" || upper.startsWith("PEREMP") -> "Perempuan"
            upper.contains("LAKI") || upper.contains("PRIA") || upper == "L" || upper.contains("LK") -> "Laki-laki"
            else -> ""
        }
    }

    /**
     * Sanitasi Nama Lengkap agar TIDAK PERNAH berisi kata Provinsi, Kabupaten, Kota, Alamat,
     * maupun informasi Pekerjaan, Status Perkawinan, atau Agama (Section 8.2 & E.6).
     */
    fun sanitizeNamaLengkap(rawName: String): String {
        var cleaned = rawName
            .replace(Regex("""^(?i)(nama\s*lengkap|nama)\s*[:=\-.;]*\s*"""), "")
            .trim(' ', ':', '-', '.', ',', ';', '"', '\'')
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleaned.isBlank()) return ""
        val upper = cleaned.uppercase()

        // 1. Jika mengandung kata terlarang seperti PROVINSI, KABUPATEN, KOTA, ALAMAT, RT/RW, tolak!
        for (forbidden in FORBIDDEN_NAME_WORDS) {
            if (upper.contains(forbidden)) {
                return ""
            }
        }
        if (upper.startsWith("KOTA") || upper.startsWith("KAB ") || upper.startsWith("PROV ")) {
            return ""
        }

        // 2. Section 8.2 & E.6: Nama tidak boleh diisi menggunakan informasi AGAMA
        for (religion in KNOWN_RELIGIONS) {
            if (upper == religion || Regex("""\b$religion\b""").matches(upper)) {
                return ""
            }
        }

        // 3. Section 8.2: Nama tidak boleh diisi menggunakan STATUS PERKAWINAN
        for (marital in KNOWN_MARITAL_STATUSES) {
            if (upper == marital || Regex("""\b$marital\b""").matches(upper)) {
                return ""
            }
        }

        // 4. Section 8.2 & E.6: Nama tidak boleh diisi menggunakan informasi PEKERJAAN
        for (job in FORBIDDEN_JOB_WORDS) {
            if (upper == job || upper.contains(job)) {
                return ""
            }
        }

        // 5. Nama tidak boleh didominasi angka (misal NIK masuk ke nama)
        val digitCount = cleaned.count { it.isDigit() }
        if (digitCount > 2) return ""

        return cleaned
    }

    /**
     * Section E.5: Sanitasi Alamat agar tidak pernah tercampur dengan RT/RW, Kelurahan/Desa, atau Kecamatan.
     */
    fun sanitizeAlamat(rawAlamat: String, kelurahan: String = "", kecamatan: String = ""): String {
        var cleaned = rawAlamat
            .replace(Regex("""^(?i)alamat\s*[:=\-.;]*\s*"""), "")
            .replace(Regex("""(?i)\bRT\s*/?\s*RW\b.*$"""), "")
            .replace(Regex("""(?i)\b(KEL/DESA|KELURAHAN|DESA|KECAMATAN|KEC\.?)\b.*$"""), "")
            .trim(' ', ':', '-', ',', '.', ';')

        val cleanKel = kelurahan.trim()
        val cleanKec = kecamatan.trim()

        if (cleanKel.length >= 3 && cleaned.equals(cleanKel, ignoreCase = true)) {
            return ""
        }
        if (cleanKec.length >= 3 && cleaned.equals(cleanKec, ignoreCase = true)) {
            return ""
        }

        // Jika kelurahan atau kecamatan menempel di akhir kalimat alamat (dipisahkan koma/strip), bersihkan
        if (cleanKec.length >= 4) {
            cleaned = cleaned.replace(
                Regex("""(?i)[,\-\s]+(KEC\.?\s*)?${Regex.escape(cleanKec)}\s*$"""),
                ""
            ).trim(' ', ',', '-', '.')
        }
        if (cleanKel.length >= 4) {
            cleaned = cleaned.replace(
                Regex("""(?i)[,\-\s]+(KEL\.?\s*|DESA\s*)?${Regex.escape(cleanKel)}\s*$"""),
                ""
            ).trim(' ', ',', '-', '.')
        }

        return cleaned
    }

    /**
     * Menyimpan foto KTP asli (tanpa watermark permanen) di direktori internal aplikasi yang aman
     * dengan kompresi KTP adaptif (max 1600px, target 300-500KB, prioritas ketajaman OCR)
     * serta pembuatan thumbnail ~300px dan pembersihan EXIF berlebih.
     */
    fun saveOriginalKtpBitmapSecurely(context: Context, bitmap: Bitmap): String {
        val ktpDir = File(context.filesDir, "ktp_documents").apply {
            if (!exists()) mkdirs()
        }
        val file = File(ktpDir, "ktp_original_${System.currentTimeMillis()}.jpg")
        val thumbFile = SmartImageProcessor.resolveThumbnailFileFor(context, file)
        val processed = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = bitmap,
            category = PhotoCategory.KTP,
            outputMainFile = file,
            outputThumbFile = thumbFile
        )
        if (processed == null) {
            FileOutputStream(file).use { out ->
                val jpegBytes = KtpImageProcessor.compressPreservingTextDetail(bitmap)
                out.write(jpegBytes)
                out.flush()
            }
        }
        return file.absolutePath
    }

    /**
     * Section A & I: Menyimpan foto KTP hasil preprocessing (auto-crop, perspective correction,
     * contrast & sharpening) ke direktori internal aplikasi.
     */
    fun savePreprocessedKtpBitmapSecurely(context: Context, preprocessedBitmap: Bitmap): String {
        return try {
            val ktpDir = File(context.filesDir, "ktp_documents").apply {
                if (!exists()) mkdirs()
            }
            val file = File(ktpDir, "ktp_preprocessed_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                val jpegBytes = KtpImageProcessor.compressPreservingTextDetail(preprocessedBitmap)
                out.write(jpegBytes)
                out.flush()
            }
            file.absolutePath
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Membaca dan mendekode gambar KTP secara aman dengan resolusi optimal (max 1600px)
     * serta membaca dan menormalisasi seluruh 8 tag orientasi EXIF (rotasi & flip kamera depan/galeri)
     * agar tidak terbalik, tidak ter-mirror, dan tidak mengalami rotasi ganda.
     */
    fun decodeSampledBitmapAndExif(context: Context, uri: Uri, maxDimension: Int = 1600): Pair<Bitmap, Int>? {
        val metadata = if (uri.scheme == "file") {
            SmartImageProcessor.extractExifMetadataFromFile(File(uri.path.orEmpty()))
        } else {
            SmartImageProcessor.extractExifMetadataFromUri(context, uri)
        }
        val exifDegrees = metadata.rotationDegrees

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (_: Throwable) {
            return null
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        val sampleSize = SmartImageProcessor.calculateInSampleSize(
            options.outWidth,
            options.outHeight,
            maxDimension,
            maxDimension
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Throwable) {
            null
        } ?: return null

        val normalizedBitmap = SmartImageProcessor.normalizeBitmapOrientation(
            source = rawBitmap,
            orientationTag = metadata.orientationTag,
            isFrontCamera = false,
            category = PhotoCategory.KTP
        )
        val scaledBitmap = SmartImageProcessor.scaleDownPreservingAspect(normalizedBitmap, maxDimension)
        if (normalizedBitmap !== rawBitmap && normalizedBitmap !== scaledBitmap) {
            normalizedBitmap.recycle()
        }
        if (rawBitmap !== scaledBitmap && rawBitmap !== normalizedBitmap) {
            rawBitmap.recycle()
        }

        return Pair(scaledBitmap, exifDegrees)
    }

    /**
     * Membaca dan memproses gambar KTP dari URI Galeri menggunakan pipeline lengkap.
     */
    suspend fun scanKtpUriOnDevice(context: Context, uri: Uri): KtpOcrResult =
        withContext(Dispatchers.IO) {
            val decoded = decodeSampledBitmapAndExif(context, uri)
            val bitmap = decoded?.first ?: try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (_: Throwable) {
                null
            } ?: return@withContext KtpOcrResult(
                statusPembacaan = "gagal",
                rawOcrText = "Gagal membuka gambar dari Galeri.",
                failureReason = "File gambar dari galeri tidak dapat dibuka atau rusak. Silakan foto ulang atau input manual."
            )

            val savedPath = saveOriginalKtpBitmapSecurely(context, bitmap)
            scanKtpBitmapWithAiAndFallback(context, bitmap, savedPath)
        }

    /**
     * Membaca dan memproses file foto KTP dari Kamera menggunakan pipeline lengkap.
     */
    suspend fun scanKtpFileOnDevice(context: Context, ktpImageFile: File): KtpOcrResult =
        withContext(Dispatchers.IO) {
            val decoded = decodeSampledBitmapAndExif(context, Uri.fromFile(ktpImageFile))
            val rawBitmap = decoded?.first ?: try {
                BitmapFactory.decodeFile(ktpImageFile.absolutePath)
            } catch (_: Throwable) {
                null
            } ?: return@withContext KtpOcrResult(
                statusPembacaan = "gagal",
                rawOcrText = "Gagal membaca file foto KTP.",
                failureReason = "File foto KTP dari kamera tidak dapat dibaca. Silakan foto ulang atau input manual."
            )

            val savedPath = saveOriginalKtpBitmapSecurely(context, rawBitmap)
            scanKtpBitmapWithAiAndFallback(context, rawBitmap, savedPath)
        }

    /**
     * Membaca dan memproses bitmap foto KTP yang diambil langsung dari kamera smartphone (TakePicturePreview).
     */
    suspend fun scanKtpBitmapDirectly(context: Context, rawBitmap: Bitmap): KtpOcrResult =
        withContext(Dispatchers.IO) {
            val savedPath = saveOriginalKtpBitmapSecurely(context, rawBitmap)
            scanKtpBitmapWithAiAndFallback(context, rawBitmap, savedPath)
        }

    /**
     * Menghitung kualitas pengenalan teks suatu hasil OCR untuk memilih varian gambar terbaik (Section 4 Step 11).
     */
    fun scoreOcrCandidateQuality(candidate: KtpOcrResult?): Int {
        if (candidate == null) return 0
        var score = 0
        if (isValid16DigitNik(candidate.nik)) score += 40 else if (candidate.nik.length >= 12) score += 15
        if (candidate.namaLengkap.length >= 3) score += 25
        if (candidate.tempatLahir.length >= 3) score += 10
        if (isValidBirthDate(candidate.tanggalLahir)) score += 15
        if (candidate.jenisKelamin in listOf("Laki-laki", "Perempuan")) score += 10
        if (candidate.alamat.length >= 4) score += 10
        if (candidate.rt.length == 3 && candidate.rw.length == 3) score += 10
        if (candidate.kelurahanDesa.length >= 3) score += 8
        if (candidate.kecamatan.length >= 3) score += 8
        if (candidate.agama.isNotBlank()) score += 5
        if (candidate.statusPerkawinan.isNotBlank()) score += 5
        if (candidate.pekerjaan.isNotBlank()) score += 5
        return score
    }

    /**
     * Inti pemrosesan Hybrid (Sections 1..14):
     * Step 1: Prapemrosesan 11 tahap gambar KTP (boundary detection, 4 corner detection, perspective correction,
     *         auto-crop, rotation correction, grayscale, CLAHE contrast enhancement, edge-preserving denoise,
     *         sharpening, upscaling, dan multi-variant image generation) & simpan foto preprocessed + 6 varian diagnostik.
     * Step 2: Ekstraksi utama menggunakan Google ML Kit Text Recognition V2 (Latin script) pada varian gambar,
     *         memilih hasil pengenalan terbaik berdasarkan skor kualitas teks (Section 4 Step 11),
     *         dan menyimpan struktur lengkap `raw_text`, `blocks`, `lines`, `elements`, `bounding_boxes`, `processing_time` (Section 5).
     * Step 3: Lapisan AI Vision menggunakan Google Gemini Vision (`gemini-3.8-flash`) sebagai verifikasi sekunder (Section 9).
     * Step 4: Penggabungan cerdas + Validasi Otomatis Section 8 + Confidence System Section 10.
     */
    suspend fun scanKtpBitmapWithAiAndFallback(
        context: Context,
        rawBitmap: Bitmap,
        savedPhotoPath: String
    ): KtpOcrResult = withContext(Dispatchers.IO) {
        val pipelineStartMs = System.currentTimeMillis()
        val errorLogs = mutableListOf<String>()
        val scanTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())

        // Step 1: 11-Step Image Processing & Quality Report
        val processedResult = KtpImageProcessor.processKtpBitmapForAi(rawBitmap)
        val imageProcMs = processedResult.processingTimeMs
        val enhancedBitmap = processedResult.processedBitmap
        val qualityReport = processedResult.qualityReport
        val savedPreprocessedPath = savePreprocessedKtpBitmapSecurely(context, enhancedBitmap)
        val diagnosticVariantPaths = KtpCustomerStorageManager.saveDiagnosticImageVariants(context, processedResult)

        if (qualityReport.qualityIssues.isNotEmpty()) {
            errorLogs.add("Peringatan Kualitas Kamera: ${qualityReport.qualityIssues.joinToString(", ")}")
        }

        // Step 2: Section 4 Step 11 & Section 5 — Evaluasi beberapa versi gambar dengan ML Kit Text Recognition V2
        val mlKitStartMs = System.currentTimeMillis()
        val variantsToEvaluate = linkedMapOf(
            "CLAHE_ENHANCED" to (processedResult.claheBitmap ?: enhancedBitmap),
            "ORIGINAL_CORRECTED" to (processedResult.perspectiveCorrectedBitmap ?: enhancedBitmap),
            "GRAYSCALE" to (processedResult.grayscaleBitmap ?: enhancedBitmap),
            "ADAPTIVE_THRESHOLD" to (processedResult.adaptiveThresholdBitmap ?: enhancedBitmap)
        )

        var bestMlKitResult: KtpOcrResult? = null
        var bestVariantName = "CLAHE_ENHANCED"
        var bestScore = -1

        for ((variantName, variantBitmap) in variantsToEvaluate) {
            val candidate = runMlKitSpatialOcr(variantBitmap, savedPhotoPath, qualityReport)
            val candidateScore = scoreOcrCandidateQuality(candidate)
            if (candidate != null && candidateScore > bestScore) {
                bestScore = candidateScore
                bestMlKitResult = candidate.copy(selectedImageVariant = variantName)
                bestVariantName = variantName
            }
            // Jika varian pertama (CLAHE_ENHANCED) sudah sangat akurat (NIK 16 digit + Nama + Alamat lengkap), tidak perlu membuang waktu
            if (bestScore >= 115) break
        }
        val mlKitElapsedMs = (System.currentTimeMillis() - mlKitStartMs).coerceAtLeast(1L)

        if (bestMlKitResult == null) {
            errorLogs.add("ML Kit Text Recognition V2 tidak menemukan blok teks pada varian gambar.")
        } else {
            errorLogs.add("ML Kit V2 memilih varian terbaik '$bestVariantName' (skor kualitas teks: $bestScore) dalam ${mlKitElapsedMs}ms.")
        }

        // Step 3: Section 9 — Jalankan AI Vision sebagai verifikasi sekunder (tetap bekerja offline jika gagal)
        val aiStartMs = System.currentTimeMillis()
        val geminiResult = try {
            runGeminiVisionKtpOcr(context, enhancedBitmap, savedPhotoPath, qualityReport)
        } catch (t: Throwable) {
            errorLogs.add("AI Vision offline/dilewati (${t.javaClass.simpleName}: ${t.message}) — menggunakan ML Kit V2 lokal.")
            null
        }
        val aiElapsedMs = (System.currentTimeMillis() - aiStartMs).coerceAtLeast(0L)
        if (geminiResult == null) {
            val diagErr = KtpGeminiDiagnosticWrapper.lastReport?.exceptionMessage.orEmpty()
            if (diagErr.isNotBlank()) {
                errorLogs.add("AI Vision sekunder tidak aktif: $diagErr. Mode Offline ML Kit V2 digunakan.")
            }
        } else {
            errorLogs.add("AI Vision sekunder berhasil memverifikasi struktur KTP dalam ${aiElapsedMs}ms.")
        }

        val totalElapsedMs = (System.currentTimeMillis() - pipelineStartMs).coerceAtLeast(1L)

        // Step 4: Gabungkan hasil ML Kit V2 + AI Vision dan terapkan validasi Section 8 & 10
        val merged = when {
            geminiResult != null && bestMlKitResult != null && (geminiResult.nik.isNotBlank() || geminiResult.namaLengkap.isNotBlank()) ->
                mergeGeminiAndMlKitResults(geminiResult, bestMlKitResult, qualityReport)
            geminiResult != null && (geminiResult.nik.isNotBlank() || geminiResult.namaLengkap.isNotBlank()) ->
                validateAndSanitizeKtpResult(geminiResult.copy(qualityReport = qualityReport))
            bestMlKitResult != null && (bestMlKitResult.nik.isNotBlank() || bestMlKitResult.namaLengkap.isNotBlank()) ->
                validateAndSanitizeKtpResult(bestMlKitResult.copy(qualityReport = qualityReport))
            geminiResult != null && bestMlKitResult != null ->
                mergeGeminiAndMlKitResults(geminiResult, bestMlKitResult, qualityReport)
            geminiResult != null -> validateAndSanitizeKtpResult(geminiResult.copy(qualityReport = qualityReport))
            bestMlKitResult != null -> validateAndSanitizeKtpResult(bestMlKitResult.copy(qualityReport = qualityReport))
            else -> KtpOcrResult(
                statusPembacaan = "gagal",
                savedOriginalPhotoPath = savedPhotoPath,
                savedPreprocessedPhotoPath = savedPreprocessedPath,
                qualityReport = qualityReport,
                unreadableFields = listOf("NIK", "Nama Lengkap", "Tempat Lahir", "Tanggal Lahir", "Alamat"),
                failureReason = if (qualityReport.qualityIssues.isNotEmpty()) {
                    "Gagal membaca KTP karena kualitas foto: ${qualityReport.qualityIssues.joinToString(", ")}. ${qualityReport.retakePromptMessage}"
                } else {
                    "Teks pada KTP tidak dapat dikenali oleh mesin OCR maupun AI Vision. Pastikan foto KTP jelas dan tidak terpotong."
                }
            )
        }

        val finalRawPayload = bestMlKitResult?.mlKitRawPayloadJson.orEmpty().ifBlank {
            buildSyntheticMlKitRawPayloadJson(
                rawText = merged.rawOcrText,
                elements = bestMlKitResult?.spatialElements ?: merged.spatialElements,
                processingTimeMs = mlKitElapsedMs
            )
        }

        merged.copy(
            savedOriginalPhotoPath = savedPhotoPath.ifBlank { merged.savedOriginalPhotoPath },
            savedPreprocessedPhotoPath = savedPreprocessedPath.ifBlank { merged.savedPreprocessedPhotoPath },
            spatialElements = bestMlKitResult?.spatialElements ?: merged.spatialElements,
            mlKitRawPayloadJson = finalRawPayload,
            diagnosticImagePaths = diagnosticVariantPaths,
            selectedImageVariant = bestVariantName,
            imageProcessingTimeMs = imageProcMs,
            mlKitProcessingTimeMs = mlKitElapsedMs,
            aiVerificationTimeMs = aiElapsedMs,
            totalProcessingTimeMs = totalElapsedMs,
            scanTimestampIso = scanTimestamp,
            diagnosticErrorLogs = errorLogs
        )
    }

    private val geminiVisionParser = KtpGeminiVisionParser()

    /**
     * Ekstraksi KTP menggunakan Google Gemini Vision (`gemini-3.8-flash` via `Firebase.ai`)
     * melalui `KtpGeminiVisionParser`.
     */
    private suspend fun runGeminiVisionKtpOcr(
        context: Context,
        processedBitmap: Bitmap,
        savedPhotoPath: String,
        qualityReport: KtpImageQualityReport
    ): KtpOcrResult? {
        return geminiVisionParser.analyzeKtpBitmapWithGeminiVision(
            context = context,
            processedKtpBitmap = processedBitmap,
            savedOriginalPhotoPath = savedPhotoPath,
            qualityReport = qualityReport
        )
    }

    /**
     * Section 5: Membangun JSON hasil OCR mentah lengkap:
     * {
     *   "raw_text": "",
     *   "blocks": [],
     *   "lines": [],
     *   "elements": [],
     *   "bounding_boxes": [],
     *   "processing_time": 0
     * }
     */
    fun buildMlKitRawPayloadJson(visionText: Text, processingTimeMs: Long): String {
        val root = JSONObject()
        root.put("raw_text", visionText.text)
        val blocksArray = org.json.JSONArray()
        val linesArray = org.json.JSONArray()
        val elementsArray = org.json.JSONArray()
        val boxesArray = org.json.JSONArray()

        visionText.textBlocks.forEachIndexed { bIdx, block ->
            val bBox = block.boundingBox
            blocksArray.put(
                JSONObject().apply {
                    put("block_index", bIdx)
                    put("text", block.text)
                    if (bBox != null) {
                        put("left", bBox.left)
                        put("top", bBox.top)
                        put("right", bBox.right)
                        put("bottom", bBox.bottom)
                    }
                }
            )
            block.lines.forEachIndexed { lIdx, line ->
                val lBox = line.boundingBox
                linesArray.put(
                    JSONObject().apply {
                        put("block_index", bIdx)
                        put("line_index", lIdx)
                        put("text", line.text)
                        if (lBox != null) {
                            put("left", lBox.left)
                            put("top", lBox.top)
                            put("right", lBox.right)
                            put("bottom", lBox.bottom)
                        }
                    }
                )
                line.elements.forEachIndexed { eIdx, element ->
                    val eBox = element.boundingBox
                    elementsArray.put(
                        JSONObject().apply {
                            put("block_index", bIdx)
                            put("line_index", lIdx)
                            put("element_index", eIdx)
                            put("text", element.text)
                            if (eBox != null) {
                                put("x", eBox.left)
                                put("y", eBox.top)
                                put("width", eBox.width())
                                put("height", eBox.height())
                            }
                        }
                    )
                    if (eBox != null) {
                        boxesArray.put(
                            JSONObject().apply {
                                put("text", element.text)
                                put("left", eBox.left)
                                put("top", eBox.top)
                                put("right", eBox.right)
                                put("bottom", eBox.bottom)
                                put("center_x", (eBox.left + eBox.right) / 2.0)
                                put("center_y", (eBox.top + eBox.bottom) / 2.0)
                            }
                        )
                    }
                }
            }
        }

        root.put("blocks", blocksArray)
        root.put("lines", linesArray)
        root.put("elements", elementsArray)
        root.put("bounding_boxes", boxesArray)
        root.put("processing_time", processingTimeMs.coerceAtLeast(0L))
        return root.toString(2)
    }

    /**
     * Membangun payload JSON Section 5 dari teks & elemen spasial (kompatibel baik di Android/Robolectric maupun pure JVM unit test).
     */
    fun buildSyntheticMlKitRawPayloadJson(
        rawText: String,
        elements: List<MlKitSpatialElement> = emptyList(),
        processingTimeMs: Long = 12L
    ): String {
        fun esc(s: String): String = buildString {
            for (ch in s) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }

        val rawLines = rawText.lines().filter { it.isNotBlank() }
        val blocksStr = rawLines.mapIndexed { idx, lineText ->
            val top = 60 + idx * 44
            val bottom = top + 30
            """{"block_index":$idx,"text":"${esc(lineText)}","left":64,"top":$top,"right":960,"bottom":$bottom}"""
        }.joinToString(",")

        val linesStr = rawLines.mapIndexed { idx, lineText ->
            val top = 60 + idx * 44
            val bottom = top + 30
            """{"block_index":$idx,"line_index":0,"text":"${esc(lineText)}","left":64,"top":$top,"right":960,"bottom":$bottom}"""
        }.joinToString(",")

        val elementsStr = elements.joinToString(",") { el ->
            val w = (el.right - el.left).coerceAtLeast(1)
            val h = (el.bottom - el.top).coerceAtLeast(1)
            """{"block_index":${el.blockIndex},"line_index":${el.lineIndex},"element_index":${el.elementIndex},"text":"${esc(el.text)}","x":${el.left},"y":${el.top},"width":$w,"height":$h}"""
        }

        val boxesStr = elements.joinToString(",") { el ->
            """{"text":"${esc(el.text)}","left":${el.left},"top":${el.top},"right":${el.right},"bottom":${el.bottom},"center_x":${el.centerX},"center_y":${el.centerY}}"""
        }

        return """{"raw_text":"${esc(rawText)}","blocks":[$blocksStr],"lines":[$linesStr],"elements":[$elementsStr],"bounding_boxes":[$boxesStr],"processing_time":${processingTimeMs.coerceAtLeast(0L)}}"""
    }

    /**
     * Section B & 5: Ekstraksi informasi spasial menggunakan Google ML Kit Text Recognition V2
     * berdasarkan TextBlocks, Lines, Elements, BoundingBoxes, dan Text Positions.
     */
    fun extractSpatialMetadataFromVisionText(visionText: Text): List<MlKitSpatialElement> {
        val spatialElements = mutableListOf<MlKitSpatialElement>()
        visionText.textBlocks.forEachIndexed { bIdx, block ->
            block.lines.forEachIndexed { lIdx, line ->
                line.elements.forEachIndexed { eIdx, element ->
                    val box = element.boundingBox
                    if (box != null && element.text.isNotBlank()) {
                        spatialElements.add(
                            MlKitSpatialElement(
                                text = element.text.trim(),
                                blockIndex = bIdx,
                                lineIndex = lIdx,
                                elementIndex = eIdx,
                                left = box.left,
                                top = box.top,
                                right = box.right,
                                bottom = box.bottom,
                                centerX = (box.left + box.right) / 2f,
                                centerY = (box.top + box.bottom) / 2f
                            )
                        )
                    }
                }
            }
        }
        return spatialElements
    }

    /**
     * Ekstraksi KTP menggunakan Google ML Kit Text Recognition V2 (Latin script) dengan rekonstruksi spasial 2D
     * berdasarkan TextBlocks, Lines, Elements, BoundingBoxes, dan posisi teks (Section 5 & 6).
     */
    private suspend fun runMlKitSpatialOcr(
        processedBitmap: Bitmap,
        savedPhotoPath: String,
        qualityReport: KtpImageQualityReport
    ): KtpOcrResult? {
        return try {
            val startMs = System.currentTimeMillis()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(processedBitmap, 0)
            val visionText: Text? = suspendCancellableCoroutine { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
            if (visionText == null) return null
            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

            val spatialElements = extractSpatialMetadataFromVisionText(visionText)
            val rawPayloadJson = buildMlKitRawPayloadJson(visionText, elapsedMs)
            val spatialText = reconstructSpatialKtpLines(visionText)
            val effectiveText = spatialText.ifBlank {
                if (spatialElements.isNotEmpty()) {
                    spatialElements.sortedWith(compareBy({ it.top / 18 }, { it.left }))
                        .joinToString(" ") { it.text }
                } else {
                    visionText.text
                }
            }
            parseIndonesianKtpText(
                rawOcrText = effectiveText,
                savedPhotoPath = savedPhotoPath,
                qualityReport = qualityReport
            ).copy(
                spatialElements = spatialElements,
                mlKitRawPayloadJson = rawPayloadJson,
                mlKitProcessingTimeMs = elapsedMs
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Rekonstruksi Baris Spasial 2D dari objek `com.google.mlkit.vision.text.Text`:
     * Menginspeksi `textBlocks`, `lines`, `elements`, `boundingBox`, dan koordinat posisi teks (`centerY`, `left`)
     * untuk menggabungkan label di kolom kiri dan nilai (`: ...`) di kolom kanan yang sejajar secara horizontal.
     */
    fun reconstructSpatialKtpLines(visionText: Text): String {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) return visionText.text

        data class PositionedLine(
            val text: String,
            val left: Int,
            val top: Int,
            val right: Int,
            val bottom: Int,
            val centerY: Float,
            val height: Float
        )

        val positioned = allLines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val elementsText = line.elements
                .sortedBy { it.boundingBox?.left ?: 0 }
                .joinToString(" ") { it.text.trim() }
                .trim()
            val txt = elementsText.ifBlank { line.text.trim() }
            if (txt.isBlank()) return@mapNotNull null
            PositionedLine(
                text = txt,
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom,
                centerY = (box.top + box.bottom) / 2f,
                height = (box.bottom - box.top).toFloat().coerceAtLeast(10f)
            )
        }.sortedBy { it.centerY }

        if (positioned.isEmpty()) return visionText.text

        val mergedRows = mutableListOf<MutableList<PositionedLine>>()
        for (item in positioned) {
            val matchingRow = mergedRows.lastOrNull { row ->
                val avgCenterY = row.map { it.centerY }.average().toFloat()
                val avgHeight = row.map { it.height }.average().toFloat()
                abs(item.centerY - avgCenterY) <= (avgHeight * 0.62f)
            }
            if (matchingRow != null) {
                matchingRow.add(item)
            } else {
                mergedRows.add(mutableListOf(item))
            }
        }

        return mergedRows.joinToString("\n") { row ->
            row.sortedBy { it.left }.joinToString(" : ") { it.text }
                .replace(Regex("""\s*:\s*:\s*"""), " : ")
        }
    }

    /**
     * Mem-parsing JSON terstruktur (mendukung format flat maupun format objek bersarang Section 7:
     * `{ "nik": { "value": "...", "confidence": 95, "status": "TERBACA" } }`)
     * dan menerapkan seluruh aturan validasi otomatis Section 8 & 10.
     */
    fun parseKtpStructuredJson(
        jsonString: String,
        savedPhotoPath: String = "",
        qualityReport: KtpImageQualityReport = KtpImageQualityReport(),
        aiEngineLabel: String = "Google Gemini Vision AI"
    ): KtpOcrResult {
        val cleanedJson = jsonString
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val obj = try {
            JSONObject(cleanedJson)
        } catch (_: Exception) {
            return KtpOcrResult(
                statusPembacaan = "gagal",
                rawOcrText = jsonString,
                savedOriginalPhotoPath = savedPhotoPath,
                qualityReport = qualityReport,
                failureReason = "Respons JSON tidak dapat di-parse oleh sistem."
            )
        }

        val confObj = obj.optJSONObject("confidence")

        fun readValue(primaryKey: String, altKey: String? = null): String {
            val nestedPrimary = obj.optJSONObject(primaryKey)
            if (nestedPrimary != null && nestedPrimary.has("value")) {
                return nestedPrimary.optString("value", "").trim()
            }
            if (altKey != null) {
                val nestedAlt = obj.optJSONObject(altKey)
                if (nestedAlt != null && nestedAlt.has("value")) {
                    return nestedAlt.optString("value", "").trim()
                }
            }
            val direct = obj.optString(primaryKey, "").trim()
            if (direct.isNotBlank() && !direct.startsWith("{")) return direct
            return if (altKey != null) obj.optString(altKey, "").trim().takeIf { !it.startsWith("{") }.orEmpty() else ""
        }

        fun readConf(primaryKey: String, altKey: String? = null, defaultIfPresent: Int): Int {
            val nestedPrimary = obj.optJSONObject(primaryKey)
            if (nestedPrimary != null && nestedPrimary.has("confidence")) {
                return nestedPrimary.optInt("confidence", defaultIfPresent)
            }
            if (altKey != null) {
                val nestedAlt = obj.optJSONObject(altKey)
                if (nestedAlt != null && nestedAlt.has("confidence")) {
                    return nestedAlt.optInt("confidence", defaultIfPresent)
                }
            }
            if (confObj != null) {
                if (confObj.has(primaryKey)) return confObj.optInt(primaryKey, defaultIfPresent)
                if (altKey != null && confObj.has(altKey)) return confObj.optInt(altKey, defaultIfPresent)
            }
            return defaultIfPresent
        }

        val rawNik = readValue("nik")
        val rawNama = readValue("nama_lengkap", "nama")
        val rawTempatLahir = readValue("tempat_lahir")
        val rawTanggalLahir = readValue("tanggal_lahir")
        val rawJenisKelamin = readValue("jenis_kelamin")
        val rawGolDarah = readValue("golongan_darah")
        val rawAlamat = readValue("alamat")
        val rawRt = readValue("rt")
        val rawRw = readValue("rw")
        val rawKelurahan = readValue("kelurahan", "kelurahan_desa")
        val rawKecamatan = readValue("kecamatan")
        val rawKabKota = readValue("kabupaten_kota")
        val rawProvinsi = readValue("provinsi")
        val rawAgama = readValue("agama")
        val rawStatusKawin = readValue("status_perkawinan")
        val rawPekerjaan = readValue("pekerjaan")
        val rawKewarganegaraan = readValue("kewarganegaraan")
        val rawBerlakuHingga = readValue("berlaku_hingga")
        val rawStatus = obj.optString("status_pembacaan", "berhasil").trim()

        val initialConfidence = KtpConfidenceScores(
            nik = readConf("nik", null, if (rawNik.isNotBlank()) 90 else 0),
            namaLengkap = readConf("nama_lengkap", "nama", if (rawNama.isNotBlank()) 90 else 0),
            tempatLahir = readConf("tempat_lahir", null, if (rawTempatLahir.isNotBlank()) 88 else 0),
            tanggalLahir = readConf("tanggal_lahir", null, if (rawTanggalLahir.isNotBlank()) 88 else 0),
            jenisKelamin = readConf("jenis_kelamin", null, if (rawJenisKelamin.isNotBlank()) 92 else 0),
            alamat = readConf("alamat", null, if (rawAlamat.isNotBlank()) 86 else 0),
            rt = readConf("rt", null, 0),
            rw = readConf("rw", null, 0),
            kelurahan = readConf("kelurahan", "kelurahan_desa", 0),
            kecamatan = readConf("kecamatan", null, 0),
            agama = readConf("agama", null, 0),
            statusPerkawinan = readConf("status_perkawinan", null, 0),
            pekerjaan = readConf("pekerjaan", null, 0),
            kewarganegaraan = readConf("kewarganegaraan", null, 0),
            berlakuHingga = readConf("berlaku_hingga", null, 0)
        )

        val preliminary = KtpOcrResult(
            nik = rawNik,
            namaLengkap = rawNama,
            tempatLahir = rawTempatLahir,
            tanggalLahir = rawTanggalLahir,
            jenisKelamin = rawJenisKelamin,
            golonganDarah = rawGolDarah,
            alamat = rawAlamat,
            rt = rawRt,
            rw = rawRw,
            rtRw = if (rawRt.isNotBlank() || rawRw.isNotBlank()) "${rawRt.padStart(3, '0')}/${rawRw.padStart(3, '0')}" else "",
            kelurahanDesa = rawKelurahan,
            kecamatan = rawKecamatan,
            kabupatenKota = rawKabKota,
            provinsi = rawProvinsi,
            agama = rawAgama,
            statusPerkawinan = rawStatusKawin,
            pekerjaan = rawPekerjaan,
            kewarganegaraan = rawKewarganegaraan,
            berlakuHingga = rawBerlakuHingga,
            confidence = initialConfidence,
            statusPembacaan = rawStatus,
            rawOcrText = cleanedJson,
            savedOriginalPhotoPath = savedPhotoPath,
            qualityReport = qualityReport,
            aiSourceEngine = aiEngineLabel
        )

        return validateAndSanitizeKtpResult(preliminary)
    }

    /**
     * Menggabungkan hasil Gemini Vision (utama) dan ML Kit Spatial OCR (pembanding/fallback)
     * agar jika ada field yang terlewat oleh salah satu engine, field yang berhasil terbaca tetap dipertahankan.
     */
    fun mergeGeminiAndMlKitResults(
        gemini: KtpOcrResult,
        mlKit: KtpOcrResult,
        qualityReport: KtpImageQualityReport = gemini.qualityReport
    ): KtpOcrResult {
        val cleanGemini = validateAndSanitizeKtpResult(gemini)
        val cleanMlKit = validateAndSanitizeKtpResult(mlKit)

        val bestNik = when {
            isValid16DigitNik(cleanGemini.nik) -> cleanGemini.nik
            isValid16DigitNik(cleanMlKit.nik) -> cleanMlKit.nik
            cleanGemini.nik.isNotBlank() -> cleanGemini.nik
            else -> cleanMlKit.nik
        }
        val bestNama = cleanGemini.namaLengkap.ifBlank { cleanMlKit.namaLengkap }
        val bestTempatLahir = cleanGemini.tempatLahir.ifBlank { cleanMlKit.tempatLahir }
        val bestTanggalLahir = when {
            isValidBirthDate(cleanGemini.tanggalLahir) -> cleanGemini.tanggalLahir
            isValidBirthDate(cleanMlKit.tanggalLahir) -> cleanMlKit.tanggalLahir
            else -> cleanGemini.tanggalLahir.ifBlank { cleanMlKit.tanggalLahir }
        }
        val bestJenisKelamin = cleanGemini.jenisKelamin.ifBlank { cleanMlKit.jenisKelamin }
        val bestGolDarah = cleanGemini.golonganDarah.ifBlank { cleanMlKit.golonganDarah }
        val bestAlamat = cleanGemini.alamat.ifBlank { cleanMlKit.alamat }
        val bestRt = cleanGemini.rt.ifBlank { cleanMlKit.rt }
        val bestRw = cleanGemini.rw.ifBlank { cleanMlKit.rw }
        val bestKelurahan = cleanGemini.kelurahanDesa.ifBlank { cleanMlKit.kelurahanDesa }
        val bestKecamatan = cleanGemini.kecamatan.ifBlank { cleanMlKit.kecamatan }
        val bestKabKota = cleanGemini.kabupatenKota.ifBlank { cleanMlKit.kabupatenKota }
        val bestProvinsi = cleanGemini.provinsi.ifBlank { cleanMlKit.provinsi }
        val bestAgama = cleanGemini.agama.ifBlank { cleanMlKit.agama }
        val bestStatusKawin = cleanGemini.statusPerkawinan.ifBlank { cleanMlKit.statusPerkawinan }
        val bestPekerjaan = cleanGemini.pekerjaan.ifBlank { cleanMlKit.pekerjaan }
        val bestKewarganegaraan = cleanGemini.kewarganegaraan.ifBlank { cleanMlKit.kewarganegaraan }
        val bestBerlaku = cleanGemini.berlakuHingga.ifBlank { cleanMlKit.berlakuHingga }

        // Jika Gemini dan ML Kit membaca nilai yang sama, tingkatkan skor confidence
        val mergedConfidence = KtpConfidenceScores(
            nik = if (bestNik.isNotBlank() && cleanGemini.nik == cleanMlKit.nik) 98 else maxOf(cleanGemini.confidence.nik, cleanMlKit.confidence.nik),
            namaLengkap = if (bestNama.isNotBlank() && cleanGemini.namaLengkap.equals(cleanMlKit.namaLengkap, ignoreCase = true)) 96 else maxOf(cleanGemini.confidence.namaLengkap, cleanMlKit.confidence.namaLengkap),
            tempatLahir = maxOf(cleanGemini.confidence.tempatLahir, cleanMlKit.confidence.tempatLahir),
            tanggalLahir = if (bestTanggalLahir.isNotBlank() && cleanGemini.tanggalLahir == cleanMlKit.tanggalLahir) 96 else maxOf(cleanGemini.confidence.tanggalLahir, cleanMlKit.confidence.tanggalLahir),
            jenisKelamin = maxOf(cleanGemini.confidence.jenisKelamin, cleanMlKit.confidence.jenisKelamin),
            alamat = maxOf(cleanGemini.confidence.alamat, cleanMlKit.confidence.alamat),
            rt = maxOf(cleanGemini.confidence.rt, cleanMlKit.confidence.rt),
            rw = maxOf(cleanGemini.confidence.rw, cleanMlKit.confidence.rw),
            kelurahan = maxOf(cleanGemini.confidence.kelurahan, cleanMlKit.confidence.kelurahan),
            kecamatan = maxOf(cleanGemini.confidence.kecamatan, cleanMlKit.confidence.kecamatan),
            agama = maxOf(cleanGemini.confidence.agama, cleanMlKit.confidence.agama),
            statusPerkawinan = maxOf(cleanGemini.confidence.statusPerkawinan, cleanMlKit.confidence.statusPerkawinan),
            pekerjaan = maxOf(cleanGemini.confidence.pekerjaan, cleanMlKit.confidence.pekerjaan),
            kewarganegaraan = maxOf(cleanGemini.confidence.kewarganegaraan, cleanMlKit.confidence.kewarganegaraan),
            berlakuHingga = maxOf(cleanGemini.confidence.berlakuHingga, cleanMlKit.confidence.berlakuHingga)
        )

        return validateAndSanitizeKtpResult(
            cleanGemini.copy(
                nik = bestNik,
                namaLengkap = bestNama,
                tempatLahir = bestTempatLahir,
                tanggalLahir = bestTanggalLahir,
                jenisKelamin = bestJenisKelamin,
                golonganDarah = bestGolDarah,
                alamat = bestAlamat,
                rt = bestRt,
                rw = bestRw,
                rtRw = if (bestRt.isNotBlank() || bestRw.isNotBlank()) "$bestRt/$bestRw" else "",
                kelurahanDesa = bestKelurahan,
                kecamatan = bestKecamatan,
                kabupatenKota = bestKabKota,
                provinsi = bestProvinsi,
                agama = bestAgama,
                statusPerkawinan = bestStatusKawin,
                pekerjaan = bestPekerjaan,
                kewarganegaraan = bestKewarganegaraan,
                berlakuHingga = bestBerlaku,
                confidence = mergedConfidence,
                qualityReport = qualityReport,
                aiSourceEngine = "Google Gemini Vision + ML Kit Validator"
            )
        )
    }

    /**
     * Section 8 & 10 (E & F): VALIDASI KHUSUS & SISTEM CONFIDENCE
     * 1. NIK wajib 16 digit angka.
     * 2. Tanggal lahir harus valid (DD-MM-YYYY).
     * 3. Jenis kelamin hanya memiliki pilihan LAKI-LAKI atau PEREMPUAN ("Laki-laki" / "Perempuan").
     * 4. RT/RW harus dipisahkan.
     * 5. Alamat tidak boleh tercampur dengan kecamatan dan kelurahan.
     * 6. Nama tidak boleh diisi menggunakan informasi pekerjaan, status perkawinan, atau agama.
     * 7. Tempat lahir harus terpisah dari tanggal lahir dan tidak boleh tergabung dengan alamat.
     * 8. Hitung confidence per field & status verifikasi (HIJAU >= 90%, KUNING 70%-89%, MERAH < 70%, TIDAK TERBACA).
     */
    fun validateAndSanitizeKtpResult(input: KtpOcrResult): KtpOcrResult {
        val warnings = mutableListOf<String>()

        // 1. Validasi & pembersihan NIK (pertahankan sebagai string 16 digit)
        val digitsOnlyNik = input.nik.trim().filter { it.isDigit() }
        val validatedNik = when {
            digitsOnlyNik.length == 16 -> digitsOnlyNik
            digitsOnlyNik.length > 16 -> {
                warnings.add("NIK terbaca lebih dari 16 digit (${digitsOnlyNik.length} digit), dipotong menjadi 16 digit — perlu verifikasi.")
                digitsOnlyNik.take(16)
            }
            digitsOnlyNik.isNotEmpty() -> {
                warnings.add("NIK belum lengkap (${digitsOnlyNik.length}/16 digit) — wajib diperiksa admin.")
                digitsOnlyNik
            }
            else -> ""
        }

        // 2. Provinsi, Kabupaten/Kota, Agama, Status Perkawinan, Pekerjaan yang mungkin tidak sengaja masuk ke Nama
        var validatedProvinsi = input.provinsi
            .replace(Regex("""^(?i)provinsi\s+"""), "")
            .trim(' ', ':', '-', '.')
        var validatedKabKota = input.kabupatenKota.trim(' ', ':', '-', '.')
        var candidateAgamaFromMisplacedName = ""
        var candidateMaritalFromMisplacedName = ""
        var candidateJobFromMisplacedName = ""

        val rawNameUpper = input.namaLengkap.uppercase().trim()
        if (rawNameUpper.startsWith("PROVINSI ") || rawNameUpper.startsWith("PROPINSI ")) {
            if (validatedProvinsi.isBlank()) {
                validatedProvinsi = input.namaLengkap.replace(Regex("""^(?i)pro[vp]insi\s+"""), "").trim()
            }
            warnings.add("Teks Provinsi terdeteksi pada kolom Nama dan telah dipisahkan secara otomatis.")
        } else if (rawNameUpper.startsWith("KOTA ") || rawNameUpper.startsWith("KABUPATEN ")) {
            if (validatedKabKota.isBlank()) {
                validatedKabKota = input.namaLengkap.trim()
            }
            warnings.add("Teks Kabupaten/Kota terdeteksi pada kolom Nama dan telah dipisahkan secara otomatis.")
        } else if (KNOWN_RELIGIONS.any { rawNameUpper == it }) {
            candidateAgamaFromMisplacedName = rawNameUpper
            warnings.add("Informasi Agama terdeteksi pada kolom Nama dan telah ditolak dari Nama (Section 8.2).")
        } else if (KNOWN_MARITAL_STATUSES.any { rawNameUpper == it }) {
            candidateMaritalFromMisplacedName = rawNameUpper
            warnings.add("Status Perkawinan terdeteksi pada kolom Nama dan telah ditolak dari Nama (Section 8.2).")
        } else if (FORBIDDEN_JOB_WORDS.any { rawNameUpper == it || rawNameUpper.contains(it) }) {
            candidateJobFromMisplacedName = rawNameUpper
            warnings.add("Informasi Pekerjaan terdeteksi pada kolom Nama dan telah ditolak dari Nama (Section 8.2).")
        }

        val validatedNama = sanitizeNamaLengkap(input.namaLengkap)
        if (input.namaLengkap.isNotBlank() && validatedNama.isBlank()) {
            warnings.add("Nama lengkap tidak valid karena mengandung kata Provinsi/Kota/Alamat/Pekerjaan/Status Perkawinan/Agama.")
        }

        // 3. Tempat & Tanggal Lahir (DD-MM-YYYY) — Section 8.3: harus terpisah dari tanggal lahir & tidak boleh tergabung dengan alamat
        var extractedDateFromTempat = ""
        val dateInTempatMatch = Regex("""(\d{1,2}[\-/.\s]+\d{1,2}[\-/.\s]+(?:19|20)\d{2})""").find(input.tempatLahir)
        if (dateInTempatMatch != null) {
            extractedDateFromTempat = normalizeBirthDateToDdMmYyyy(dateInTempatMatch.value)
        }
        var validatedTempatLahir = input.tempatLahir
            .replace(Regex("""^(?i)(tempat/tgl\s*lahir|tempat\s*lahir|lahir)\s*[:=\-.;]*\s*"""), "")
            .replace(Regex("""\d{1,2}[\-/.\s]+\d{1,2}[\-/.\s]+\d{2,4}.*$"""), "")
            .replace(Regex("""(?i)[,\s]*(jl\.?|jalan|rt\s*/?\s*rw|dsn\.?|dusun|kel\.?|kec\.?).*$"""), "")
            .trim(' ', ':', '-', ',', '.', ';')
        if (validatedTempatLahir.uppercase() in listOf("TEMPAT", "LAHIR", "TGL", "TTL") ||
            Regex("""^(?i)(jl\.?|jalan|dsn\.?|dusun|perum)\b""").containsMatchIn(validatedTempatLahir)
        ) {
            validatedTempatLahir = ""
        }

        val validatedTanggalLahir = normalizeBirthDateToDdMmYyyy(input.tanggalLahir).ifBlank { extractedDateFromTempat }
        if (input.tanggalLahir.isNotBlank() && validatedTanggalLahir.isBlank()) {
            warnings.add("Format Tanggal Lahir (${input.tanggalLahir}) tidak valid (wajib DD-MM-YYYY).")
        }

        // 4. Jenis Kelamin ("Laki-laki" atau "Perempuan")
        val validatedJenisKelamin = normalizeJenisKelamin(input.jenisKelamin)
        if (input.jenisKelamin.isNotBlank() && validatedJenisKelamin.isBlank()) {
            warnings.add("Jenis kelamin tidak sesuai standar (LAKI-LAKI / PEREMPUAN).")
        }

        // Golongan Darah
        val validatedGolDarah = input.golonganDarah
            .replace(Regex("""^(?i)(gol\.?\s*darah|gol)\s*[:=\-.;]*\s*"""), "")
            .trim(' ', ':', '.')
            .uppercase()
            .takeIf { it in listOf("A", "B", "AB", "O", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "-") }
            ?: ""

        // 5. RT & RW harus terpisah
        var cleanRt = input.rt.filter { it.isDigit() }
        var cleanRw = input.rw.filter { it.isDigit() }
        if ((cleanRt.isBlank() || cleanRw.isBlank()) && input.rtRw.contains("/")) {
            val parts = input.rtRw.split("/")
            if (cleanRt.isBlank()) cleanRt = parts.getOrNull(0)?.filter { it.isDigit() }.orEmpty()
            if (cleanRw.isBlank()) cleanRw = parts.getOrNull(1)?.filter { it.isDigit() }.orEmpty()
        }
        val validatedRt = if (cleanRt.isNotBlank()) cleanRt.take(3).padStart(3, '0') else ""
        val validatedRw = if (cleanRw.isNotBlank()) cleanRw.take(3).padStart(3, '0') else ""
        val combinedRtRw = if (validatedRt.isNotBlank() && validatedRw.isNotBlank()) {
            "$validatedRt/$validatedRw"
        } else if (validatedRt.isNotBlank()) {
            "$validatedRt/000"
        } else if (validatedRw.isNotBlank()) {
            "000/$validatedRw"
        } else {
            ""
        }

        // 6. Kelurahan, Kecamatan, dan Alamat (Section 8.6 & 8.8: Alamat tidak boleh tercampur dengan kecamatan/kelurahan & tidak boleh tertukar)
        var validatedKelurahan = input.kelurahanDesa
            .replace(Regex("""^(?i)(kel/desa|kelurahan/desa|kelurahan|desa)\s*[:=\-.;]*\s*"""), "")
            .trim(' ', ':', '-', ',', '.')
        var validatedKecamatan = input.kecamatan
            .replace(Regex("""^(?i)kecamatan\s*[:=\-.;]*\s*"""), "")
            .trim(' ', ':', '-', ',', '.')

        val kecHasKelPrefix = validatedKecamatan.uppercase().startsWith("KEL ") || validatedKecamatan.uppercase().startsWith("DESA ")
        val kelHasKecPrefix = validatedKelurahan.uppercase().startsWith("KEC ") || validatedKelurahan.uppercase().startsWith("KECAMATAN ")
        if (kecHasKelPrefix && kelHasKecPrefix) {
            val extractedKel = validatedKecamatan.replace(Regex("""^(?i)(kel|desa)\s+"""), "").trim()
            val extractedKec = validatedKelurahan.replace(Regex("""^(?i)(kecamatan|kec)\s+"""), "").trim()
            validatedKelurahan = extractedKel
            validatedKecamatan = extractedKec
            warnings.add("Kelurahan/Desa dan Kecamatan tertukar dan telah dikoreksi otomatis.")
        } else {
            if (kecHasKelPrefix) {
                if (validatedKelurahan.isBlank()) {
                    validatedKelurahan = validatedKecamatan.replace(Regex("""^(?i)(kel|desa)\s+"""), "").trim()
                }
                validatedKecamatan = ""
                warnings.add("Kelurahan/Desa terdeteksi pada kolom Kecamatan dan telah dikoreksi.")
            }
            if (kelHasKecPrefix) {
                if (validatedKecamatan.isBlank()) {
                    validatedKecamatan = validatedKelurahan.replace(Regex("""^(?i)(kecamatan|kec)\s+"""), "").trim()
                }
                validatedKelurahan = ""
                warnings.add("Kecamatan terdeteksi pada kolom Kelurahan/Desa dan telah dikoreksi.")
            }
        }
        if (validatedKelurahan.isNotBlank() && validatedKelurahan.equals(validatedKecamatan, ignoreCase = true)) {
            warnings.add("Kelurahan dan Kecamatan terbaca identik — mohon periksa kembali.")
        }

        val validatedAlamat = sanitizeAlamat(
            rawAlamat = input.alamat,
            kelurahan = validatedKelurahan,
            kecamatan = validatedKecamatan
        )

        // 7. Agama, Status Perkawinan, Pekerjaan, Kewarganegaraan, Berlaku Hingga
        val rawAgamaCandidate = input.agama.ifBlank { candidateAgamaFromMisplacedName }
        val validatedAgama = rawAgamaCandidate.trim(' ', ':', '-', '.').uppercase().let { raw ->
            KNOWN_RELIGIONS.firstOrNull { raw.contains(it) }?.let {
                if (it == "KATHOLIK") "KATOLIK" else if (it == "BUDHA") "BUDDHA" else it
            } ?: raw.takeIf { it.length in 3..15 && it.all { ch -> ch.isLetter() || ch.isWhitespace() } }.orEmpty()
        }

        val rawMaritalCandidate = input.statusPerkawinan.ifBlank { candidateMaritalFromMisplacedName }
        val validatedStatusKawin = rawMaritalCandidate.trim(' ', ':', '-', '.').uppercase().let { raw ->
            KNOWN_MARITAL_STATUSES.firstOrNull { raw.contains(it) } ?: raw.takeIf { it.isNotBlank() }.orEmpty()
        }

        val rawJobCandidate = input.pekerjaan.ifBlank { candidateJobFromMisplacedName }
        val validatedPekerjaan = rawJobCandidate
            .replace(Regex("""^(?i)pekerjaan\s*[:=\-.;]*\s*"""), "")
            .trim(' ', ':', '-', '.')
            .takeIf { !it.uppercase().contains("KEWARGANEGARAAN") && !it.uppercase().contains("BERLAKU") }
            .orEmpty()

        val validatedKewarganegaraan = input.kewarganegaraan.uppercase().trim().let { raw ->
            when {
                raw.contains("WNI") || raw.contains("INDONESIA") -> "WNI"
                raw.contains("WNA") -> "WNA"
                else -> ""
            }
        }

        val validatedBerlaku = input.berlakuHingga.uppercase().trim().let { raw ->
            when {
                raw.contains("SEUMUR") || raw.contains("HIDUP") -> "SEUMUR HIDUP"
                else -> normalizeBirthDateToDdMmYyyy(raw).ifBlank { raw.trim(' ', ':', '-', '.') }
            }
        }

        // 8. Hitung Confidence Score akurat per field (0..100) dengan penalti jika kualitas foto buruk
        val qualityPenalty = when {
            !input.qualityReport.isAcceptable -> 22
            input.qualityReport.qualityIssues.isNotEmpty() -> 10
            else -> 0
        }

        val nikConf = when {
            isValid16DigitNik(validatedNik) -> (input.confidence.nik.takeIf { it > 0 } ?: 95) - qualityPenalty
            validatedNik.length in 12..15 -> minOf(input.confidence.nik.takeIf { it > 0 } ?: 65, 68)
            validatedNik.isNotBlank() -> 42
            else -> 0
        }.coerceIn(0, 100)

        val namaConf = when {
            validatedNama.length >= 3 && validatedNama.all { it.isLetter() || it.isWhitespace() || it in ".,'-" } ->
                (input.confidence.namaLengkap.takeIf { it > 0 } ?: 92) - qualityPenalty
            validatedNama.isNotBlank() -> minOf(input.confidence.namaLengkap.takeIf { it > 0 } ?: 72, 75)
            else -> 0
        }.coerceIn(0, 100)

        val tempatConf = when {
            validatedTempatLahir.length >= 3 -> (input.confidence.tempatLahir.takeIf { it > 0 } ?: 90) - qualityPenalty
            validatedTempatLahir.isNotBlank() -> 68
            else -> 0
        }.coerceIn(0, 100)

        val tglConf = when {
            isValidBirthDate(validatedTanggalLahir) -> (input.confidence.tanggalLahir.takeIf { it > 0 } ?: 94) - qualityPenalty
            validatedTanggalLahir.isNotBlank() -> 45
            else -> 0
        }.coerceIn(0, 100)

        val jkConf = when {
            validatedJenisKelamin in listOf("Laki-laki", "Perempuan") -> (input.confidence.jenisKelamin.takeIf { it > 0 } ?: 95) - qualityPenalty
            else -> 0
        }.coerceIn(0, 100)

        val alamatConf = when {
            validatedAlamat.length >= 5 && validatedRt.isNotBlank() && validatedRw.isNotBlank() && validatedKelurahan.isNotBlank() ->
                (input.confidence.alamat.takeIf { it > 0 } ?: 90) - qualityPenalty
            validatedAlamat.length >= 4 ->
                minOf((input.confidence.alamat.takeIf { it > 0 } ?: 78) - qualityPenalty, 78)
            validatedAlamat.isNotBlank() -> 58
            else -> 0
        }.coerceIn(0, 100)

        val rtConf = if (validatedRt.length == 3) ((input.confidence.rt.takeIf { it > 0 } ?: maxOf(alamatConf, 90)) - qualityPenalty).coerceIn(0, 100) else 0
        val rwConf = if (validatedRw.length == 3) ((input.confidence.rw.takeIf { it > 0 } ?: maxOf(alamatConf, 90)) - qualityPenalty).coerceIn(0, 100) else 0
        val kelConf = if (validatedKelurahan.length >= 3) ((input.confidence.kelurahan.takeIf { it > 0 } ?: maxOf(alamatConf, 90)) - qualityPenalty).coerceIn(0, 100) else 0
        val kecConf = if (validatedKecamatan.length >= 3) ((input.confidence.kecamatan.takeIf { it > 0 } ?: maxOf(alamatConf, 90)) - qualityPenalty).coerceIn(0, 100) else 0
        val agamaConf = if (validatedAgama.isNotBlank()) ((input.confidence.agama.takeIf { it > 0 } ?: 93) - qualityPenalty).coerceIn(0, 100) else 0
        val statusKawinConf = if (validatedStatusKawin.isNotBlank()) ((input.confidence.statusPerkawinan.takeIf { it > 0 } ?: 92) - qualityPenalty).coerceIn(0, 100) else 0
        val pekerjaanConf = if (validatedPekerjaan.isNotBlank()) ((input.confidence.pekerjaan.takeIf { it > 0 } ?: 90) - qualityPenalty).coerceIn(0, 100) else 0
        val wnConf = if (validatedKewarganegaraan.isNotBlank()) ((input.confidence.kewarganegaraan.takeIf { it > 0 } ?: 95) - qualityPenalty).coerceIn(0, 100) else 0
        val berlakuConf = if (validatedBerlaku.isNotBlank()) ((input.confidence.berlakuHingga.takeIf { it > 0 } ?: 95) - qualityPenalty).coerceIn(0, 100) else 0

        val finalConfidence = KtpConfidenceScores(
            nik = nikConf,
            namaLengkap = namaConf,
            tempatLahir = tempatConf,
            tanggalLahir = tglConf,
            jenisKelamin = jkConf,
            alamat = alamatConf,
            rt = rtConf,
            rw = rwConf,
            kelurahan = kelConf,
            kecamatan = kecConf,
            agama = agamaConf,
            statusPerkawinan = statusKawinConf,
            pekerjaan = pekerjaanConf,
            kewarganegaraan = wnConf,
            berlakuHingga = berlakuConf
        )

        val sourceTag = when {
            input.aiSourceEngine.contains("Gemini", ignoreCase = true) && input.aiSourceEngine.contains("ML Kit", ignoreCase = true) -> "HYBRID_CONSENSUS"
            input.aiSourceEngine.contains("Gemini", ignoreCase = true) -> "GEMINI_VISION"
            else -> "ML_KIT_V2"
        }

        fun makeDetail(valStr: String, confScore: Int): KtpFieldDetail {
            val effectiveConf = if (valStr.isBlank()) 0 else confScore
            return KtpFieldDetail(
                value = valStr,
                confidence = effectiveConf,
                verificationStatus = KtpFieldDetail.computeVerificationStatus(valStr, effectiveConf),
                source = if (valStr.isBlank()) "NONE" else sourceTag
            )
        }

        val computedDetails = mapOf(
            "nik" to makeDetail(validatedNik, nikConf),
            "nama" to makeDetail(validatedNama, namaConf),
            "nama_lengkap" to makeDetail(validatedNama, namaConf),
            "tempat_lahir" to makeDetail(validatedTempatLahir, tempatConf),
            "tanggal_lahir" to makeDetail(validatedTanggalLahir, tglConf),
            "jenis_kelamin" to makeDetail(validatedJenisKelamin, jkConf),
            "alamat" to makeDetail(validatedAlamat, alamatConf),
            "rt" to makeDetail(validatedRt, rtConf),
            "rw" to makeDetail(validatedRw, rwConf),
            "kelurahan" to makeDetail(validatedKelurahan, kelConf),
            "kecamatan" to makeDetail(validatedKecamatan, kecConf),
            "agama" to makeDetail(validatedAgama, agamaConf),
            "status_perkawinan" to makeDetail(validatedStatusKawin, statusKawinConf),
            "pekerjaan" to makeDetail(validatedPekerjaan, pekerjaanConf),
            "kewarganegaraan" to makeDetail(validatedKewarganegaraan, wnConf),
            "berlaku_hingga" to makeDetail(validatedBerlaku, berlakuConf)
        )

        // Daftar field yang belum terbaca (kosong atau confidence < 50)
        val unreadable = mutableListOf<String>()
        if (validatedNik.isBlank() || nikConf < 50) unreadable.add("NIK (16 Digit)")
        if (validatedNama.isBlank() || namaConf < 50) unreadable.add("Nama Lengkap")
        if (validatedTempatLahir.isBlank() || tempatConf < 50) unreadable.add("Tempat Lahir")
        if (validatedTanggalLahir.isBlank() || tglConf < 50) unreadable.add("Tanggal Lahir")
        if (validatedJenisKelamin.isBlank() || jkConf < 50) unreadable.add("Jenis Kelamin")
        if (validatedAlamat.isBlank() || alamatConf < 50) unreadable.add("Alamat KTP")
        if (validatedRt.isBlank()) unreadable.add("RT")
        if (validatedRw.isBlank()) unreadable.add("RW")
        if (validatedKelurahan.isBlank()) unreadable.add("Kelurahan/Desa")
        if (validatedKecamatan.isBlank()) unreadable.add("Kecamatan")
        if (validatedAgama.isBlank()) unreadable.add("Agama")
        if (validatedStatusKawin.isBlank()) unreadable.add("Status Perkawinan")
        if (validatedPekerjaan.isBlank()) unreadable.add("Pekerjaan")

        // Daftar field dengan confidence 70%-89% atau < 80% (warna kuning: "Perlu Verifikasi Admin")
        val lowConfidence = mutableListOf<String>()
        if (validatedNik.isNotBlank() && (!isValid16DigitNik(validatedNik) || nikConf < 90)) {
            lowConfidence.add("NIK (${validatedNik.length}/16 digit • Confidence $nikConf%)")
        }
        if (validatedNama.isNotBlank() && namaConf < 90) {
            lowConfidence.add("Nama Lengkap (Confidence $namaConf%)")
        }
        if (validatedTempatLahir.isNotBlank() && tempatConf < 90) {
            lowConfidence.add("Tempat Lahir (Confidence $tempatConf%)")
        }
        if (validatedTanggalLahir.isNotBlank() && tglConf < 90) {
            lowConfidence.add("Tanggal Lahir (Confidence $tglConf%)")
        }
        if (validatedJenisKelamin.isNotBlank() && jkConf < 90) {
            lowConfidence.add("Jenis Kelamin (Confidence $jkConf%)")
        }
        if (validatedAlamat.isNotBlank() && alamatConf < 90) {
            lowConfidence.add("Alamat KTP (Confidence $alamatConf%)")
        }

        // Section F: Daftar seluruh field (15 field) dengan confidence < 70% (Memerlukan Tombol Koreksi Manual)
        val manualCorrection = mutableListOf<String>()
        for ((key, detail) in computedDetails) {
            if (key != "nama_lengkap" && detail.value.isNotBlank() && detail.confidence < 70) {
                manualCorrection.add(key)
            }
        }

        val finalStatus = when {
            validatedNik.isBlank() && validatedNama.isBlank() && validatedAlamat.isBlank() -> "gagal"
            unreadable.isNotEmpty() || manualCorrection.isNotEmpty() || warnings.isNotEmpty() ||
                nikConf < 80 || namaConf < 80 || tempatConf < 80 || tglConf < 80 || jkConf < 80 || alamatConf < 80 -> "perlu_verifikasi"
            else -> "berhasil"
        }

        val computedFailureReason = if (finalStatus == "gagal") {
            input.failureReason.ifBlank {
                if (input.qualityReport.qualityIssues.isNotEmpty()) {
                    "Kualitas foto tidak memenuhi syarat: ${input.qualityReport.qualityIssues.joinToString(", ")}"
                } else {
                    "Data identitas utama (NIK, Nama, Alamat) tidak terbaca pada foto KTP."
                }
            }
        } else {
            input.failureReason
        }

        return input.copy(
            nik = validatedNik,
            namaLengkap = validatedNama,
            tempatLahir = validatedTempatLahir,
            tanggalLahir = validatedTanggalLahir,
            jenisKelamin = validatedJenisKelamin,
            golonganDarah = validatedGolDarah,
            alamat = validatedAlamat,
            rt = validatedRt,
            rw = validatedRw,
            rtRw = combinedRtRw,
            kelurahanDesa = validatedKelurahan,
            kecamatan = validatedKecamatan,
            kabupatenKota = validatedKabKota,
            provinsi = validatedProvinsi,
            agama = validatedAgama,
            statusPerkawinan = validatedStatusKawin,
            pekerjaan = validatedPekerjaan,
            kewarganegaraan = validatedKewarganegaraan,
            berlakuHingga = validatedBerlaku,
            confidence = finalConfidence,
            statusPembacaan = finalStatus,
            unreadableFields = unreadable,
            lowConfidenceFields = lowConfidence,
            manualCorrectionFields = manualCorrection,
            validationWarnings = warnings,
            fieldDetails = computedDetails,
            failureReason = computedFailureReason
        )
    }

    /**
     * Parser cerdas e-KTP Indonesia berbasis label & pola spasial (digunakan baik untuk ML Kit Spatial
     * maupun pengujian teks OCR langsung).
     * Mengatasi masalah klasik OCR KTP Indonesia:
     * - Memisahkan PROVINSI & KOTA/KABUPATEN agar tidak pernah masuk ke Nama Lengkap.
     * - Menangani blok dua kolom (label di kiri, nilai ': ...' di kanan atau baris berikutnya).
     * - Memisahkan Tempat Lahir & Tanggal Lahir (DD-MM-YYYY) walaupun label 'Tempat/Tgl Lahir' rusak.
     * - Memisahkan Alamat, RT, RW, Kelurahan/Desa, dan Kecamatan.
     */
    fun parseIndonesianKtpText(
        rawOcrText: String,
        savedPhotoPath: String = "",
        qualityReport: KtpImageQualityReport = KtpImageQualityReport()
    ): KtpOcrResult {
        val rawLines = rawOcrText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var nik = ""
        var namaLengkap = ""
        var tempatLahir = ""
        var tanggalLahir = ""
        var jenisKelamin = ""
        var golonganDarah = ""
        var alamat = ""
        var rt = ""
        var rw = ""
        var kelurahanDesa = ""
        var kecamatan = ""
        var kabupatenKota = ""
        var provinsi = ""
        var agama = ""
        var statusPerkawinan = ""
        var pekerjaan = ""
        var kewarganegaraan = ""
        var berlakuHingga = ""

        fun cleanLabelValue(line: String, labelRegex: Regex): String {
            return line.replace(labelRegex, "")
                .trim(' ', ':', '-', '=', '.', ',', ';')
                .trim()
        }

        fun normalizeOcrDigits(candidate: String): String {
            return buildString {
                for (ch in candidate) {
                    when (ch) {
                        'O', 'o', 'D', 'Q' -> append('0')
                        'I', 'i', 'l', '|', '!' -> append('1')
                        'Z', 'z' -> append('2')
                        'S', 's' -> append('5')
                        'G', 'b' -> append('6')
                        'B' -> append('8')
                        else -> if (ch.isDigit()) append(ch)
                    }
                }
            }
        }

        fun isPureLabelLine(upper: String): Boolean {
            val stripped = upper.trim(' ', ':', '-', '.', ';')
            return stripped in setOf(
                "NIK", "NAMA", "TEMPAT/TGL LAHIR", "TEMPAT / TGL LAHIR", "TEMPAT LAHIR", "TGL LAHIR",
                "JENIS KELAMIN", "ALAMAT", "RT/RW", "RT / RW", "KEL/DESA", "KEL / DESA",
                "KELURAHAN", "KECAMATAN", "AGAMA", "STATUS PERKAWINAN", "PEKERJAAN",
                "KEWARGANEGARAAN", "BERLAKU HINGGA"
            )
        }

        // 1. Ekstrak Header Provinsi & Kabupaten/Kota terlebih dahulu dan tandai indeks barisnya
        val headerIndices = mutableSetOf<Int>()
        rawLines.forEachIndexed { idx, line ->
            val upper = line.uppercase()
            when {
                upper.startsWith("PROVINSI ") || upper.startsWith("PROPINSI ") -> {
                    provinsi = line.replace(Regex("""^(?i)pro[vp]insi\s+"""), "").trim(' ', ':', '-')
                    headerIndices.add(idx)
                }
                upper.startsWith("KABUPATEN ") || upper.startsWith("KOTA ") -> {
                    kabupatenKota = line.trim(' ', ':', '-')
                    headerIndices.add(idx)
                }
            }
        }

        // 2. Cari NIK 16 Digit
        val exact16Regex = Regex("""(?<!\d)\d{16}(?!\d)""")
        for (line in rawLines) {
            val m = exact16Regex.find(line.replace(" ", ""))
            if (m != null) {
                nik = m.value
                break
            }
        }
        if (nik.isBlank()) {
            for (i in rawLines.indices) {
                val upper = rawLines[i].uppercase()
                if (upper.contains("NIK")) {
                    val afterLabel = rawLines[i].substringAfter("NIK", "").trim(' ', ':', '-', '=')
                    val repaired = normalizeOcrDigits(afterLabel)
                    if (repaired.length >= 16) {
                        nik = repaired.take(16)
                        break
                    } else if (repaired.length in 10..15) {
                        nik = repaired
                    } else if (i + 1 < rawLines.size) {
                        val nextRepaired = normalizeOcrDigits(rawLines[i + 1])
                        if (nextRepaired.length >= 12) {
                            nik = nextRepaired.take(16)
                            break
                        }
                    }
                }
            }
        }

        // Helper untuk memisahkan Tempat & Tanggal Lahir dari satu string
        fun extractBirthPlaceAndDate(rawTtl: String) {
            val cleaned = rawTtl
                .replace(Regex("""^(?i)(tempat\s*/?\s*tgl\s*lahir|tempat\s*lahir|tgl\s*lahir|ttl)\s*[:=\-.;]*\s*"""), "")
                .trim(' ', ':', '-', '.', ';')
            if (cleaned.isBlank()) return

            val datePattern = Regex("""(\d{1,2}[\-/.\s]+\d{1,2}[\-/.\s]+(?:19|20)\d{2})""")
            val dateMatch = datePattern.find(cleaned)
            if (dateMatch != null) {
                val normalizedDate = normalizeBirthDateToDdMmYyyy(dateMatch.value)
                if (normalizedDate.isNotBlank()) {
                    tanggalLahir = normalizedDate
                }
                val beforeDate = cleaned.substring(0, dateMatch.range.first)
                    .trim(' ', ',', '.', '-', ':', ';')
                if (beforeDate.isNotBlank() && tempatLahir.isBlank()) {
                    tempatLahir = beforeDate
                }
            } else if (cleaned.contains(",")) {
                val p = cleaned.split(",", limit = 2)
                if (tempatLahir.isBlank()) tempatLahir = p[0].trim(' ', ':', '-')
                val normDate = normalizeBirthDateToDdMmYyyy(p.getOrNull(1).orEmpty())
                if (normDate.isNotBlank()) tanggalLahir = normDate
            }
        }

        // 3. Parsing baris demi baris berdasarkan label
        var i = 0
        while (i < rawLines.size) {
            if (i in headerIndices) {
                i++
                continue
            }
            val line = rawLines[i]
            val upper = line.uppercase()

            when {
                // Nama Lengkap
                (upper.startsWith("NAMA") || upper.contains("NAMA :")) && !upper.contains("KECAMATAN") && !upper.contains("AGAMA") -> {
                    var v = cleanLabelValue(line, Regex("""^(?i)nama(\s*lengkap)?\s*[:=\-.;]*"""))
                    if (v.isBlank() && i + 1 < rawLines.size && (i + 1) !in headerIndices) {
                        val nextLine = rawLines[i + 1]
                        if (!isPureLabelLine(nextLine.uppercase())) {
                            v = nextLine.trim(' ', ':', '-', '.')
                        }
                    }
                    val sanitized = sanitizeNamaLengkap(v)
                    if (sanitized.isNotBlank()) {
                        namaLengkap = sanitized
                    }
                }

                // Tempat / Tgl Lahir
                upper.contains("TEMPAT") || upper.contains("TGL LAHIR") || upper.contains("LAHIR") -> {
                    var v = cleanLabelValue(line, Regex("""^(?i)(tempat\s*/?\s*tgl\s*lahir|tempat\s*tgl\s*lahir|tempat\s*lahir|tgl\s*lahir|lahir)\s*[:=\-.;]*"""))
                    if (v.isBlank() && i + 1 < rawLines.size) {
                        v = rawLines[i + 1]
                    }
                    extractBirthPlaceAndDate(v)
                }

                // Jenis Kelamin & Golongan Darah
                upper.contains("JENIS KELAMIN") || upper.contains("KELAMIN") || upper.contains("LAKI-LAKI") || upper.contains("LAKI LAKI") || upper.contains("PEREMPUAN") -> {
                    val normJk = normalizeJenisKelamin(line)
                    if (normJk.isNotBlank()) {
                        jenisKelamin = normJk
                    } else if (i + 1 < rawLines.size) {
                        val nextJk = normalizeJenisKelamin(rawLines[i + 1])
                        if (nextJk.isNotBlank()) jenisKelamin = nextJk
                    }
                    // Cek Golongan Darah pada baris yang sama
                    val golMatch = Regex("""(?i)gol\.?\s*darah\s*[:=\-.;]*\s*([ABO]{1,2}[+-]?|-)""").find(line)
                    if (golMatch != null) {
                        golonganDarah = golMatch.groupValues[1].uppercase()
                    }
                }

                // Alamat
                upper.startsWith("ALAMAT") || upper.contains("ALAMAT :") -> {
                    var v = cleanLabelValue(line, Regex("""^(?i)alamat\s*[:=\-.;]*"""))
                    if (v.isBlank() && i + 1 < rawLines.size && !isPureLabelLine(rawLines[i + 1].uppercase())) {
                        v = rawLines[i + 1].trim(' ', ':', '-')
                    }
                    if (v.isNotBlank() && !v.uppercase().startsWith("RT")) {
                        alamat = v
                    }
                }

                // RT / RW
                upper.contains("RT/RW") || upper.contains("RT / RW") || Regex("""\b0?\d{2}\s*/\s*0?\d{2}\b""").containsMatchIn(upper) -> {
                    val rtRwPattern = Regex("""(\d{1,3})\s*[/\-:]\s*(\d{1,3})""")
                    val match = rtRwPattern.find(line) ?: (if (i + 1 < rawLines.size) rtRwPattern.find(rawLines[i + 1]) else null)
                    if (match != null) {
                        rt = match.groupValues[1].padStart(3, '0')
                        rw = match.groupValues[2].padStart(3, '0')
                    }
                }

                // Kelurahan / Desa
                upper.contains("KEL/DESA") || upper.contains("KEL / DESA") || upper.startsWith("KELURAHAN") || upper.startsWith("DESA") -> {
                    var v = cleanLabelValue(line, Regex("""^(?i)(kel\s*/?\s*desa|kelurahan\s*/?\s*desa|kelurahan|desa)\s*[:=\-.;]*"""))
                    if (v.isBlank() && i + 1 < rawLines.size && !isPureLabelLine(rawLines[i + 1].uppercase())) {
                        val candidateNext = rawLines[i + 1].trim(' ', ':', '-')
                        if (candidateNext.count { it.isDigit() } <= 2) {
                            v = candidateNext
                        }
                    }
                    if (v.isNotBlank() && !v.uppercase().contains("KECAMATAN") && v.count { it.isDigit() } <= 2) {
                        kelurahanDesa = v
                    }
                }

                // Kecamatan
                upper.startsWith("KECAMATAN") || upper.contains("KECAMATAN :") || upper.startsWith("KEC.") || upper.startsWith("KEC ") -> {
                    var v = cleanLabelValue(line, Regex("""^(?i)(kecamatan|kec\.?)\s*[:=\-.;]*"""))
                    if (v.isBlank() && i + 1 < rawLines.size && !isPureLabelLine(rawLines[i + 1].uppercase())) {
                        val candidateNext = rawLines[i + 1].trim(' ', ':', '-')
                        if (candidateNext.count { it.isDigit() } <= 2) {
                            v = candidateNext
                        }
                    }
                    if (v.isNotBlank() && !v.uppercase().contains("AGAMA") && v.count { it.isDigit() } <= 2) {
                        kecamatan = v
                    }
                }

                // Agama
                upper.startsWith("AGAMA") || upper.contains("AGAMA :") -> {
                    val v = cleanLabelValue(line, Regex("""^(?i)agama\s*[:=\-.;]*"""))
                    if (v.isNotBlank()) agama = v
                }

                // Status Perkawinan
                upper.contains("STATUS PERKAWINAN") || upper.startsWith("STATUS") -> {
                    val v = cleanLabelValue(line, Regex("""^(?i)status(\s*perkawinan)?\s*[:=\-.;]*"""))
                        .replace(Regex("""(?i)pekerjaan.*"""), "")
                        .trim(' ', ':', '-')
                    if (v.isNotBlank()) statusPerkawinan = v
                }

                // Pekerjaan
                upper.startsWith("PEKERJAAN") || upper.contains("PEKERJAAN :") -> {
                    val v = cleanLabelValue(line, Regex("""^(?i)pekerjaan\s*[:=\-.;]*"""))
                        .replace(Regex("""(?i)kewarganegaraan.*"""), "")
                        .trim(' ', ':', '-')
                    if (v.isNotBlank()) pekerjaan = v
                }

                // Kewarganegaraan
                upper.contains("KEWARGANEGARAAN") -> {
                    val v = cleanLabelValue(line, Regex("""^(?i)kewarganegaraan\s*[:=\-.;]*"""))
                        .replace(Regex("""(?i)berlaku.*"""), "")
                        .trim(' ', ':', '-')
                    if (v.isNotBlank()) kewarganegaraan = v
                }

                // Berlaku Hingga
                upper.contains("BERLAKU") || upper.contains("SEUMUR HIDUP") -> {
                    val v = cleanLabelValue(line, Regex("""^(?i)berlaku(\s*hingga)?\s*[:=\-.;]*"""))
                    berlakuHingga = if (v.isBlank() && upper.contains("SEUMUR HIDUP")) "SEUMUR HIDUP" else v
                }
            }
            i++
        }

        // 4. Fallback cerdas untuk baris tanpa label (ketika ML Kit memisahkan kolom label dan kolom isi)
        for ((idx, line) in rawLines.withIndex()) {
            if (idx in headerIndices) continue
            val upper = line.uppercase()

            // Jika Tempat/Tgl Lahir masih kosong, cari baris yang memiliki pola "KOTA, DD-MM-YYYY"
            if (tempatLahir.isBlank() || tanggalLahir.isBlank()) {
                val ttlRegex = Regex("""^[:\-\s]*([A-Z][A-Z\s.]{2,25})[,\s]+(\d{1,2}[\-/.\s]+\d{1,2}[\-/.\s]+(?:19|20)\d{2})""")
                val m = ttlRegex.find(upper)
                if (m != null) {
                    val candidateCity = m.groupValues[1].replace(Regex("""^(?i)(tempat/tgl\s*lahir|tempat\s*lahir|lahir)\s*"""), "").trim(' ', ':', '-', ',')
                    val candidateDate = normalizeBirthDateToDdMmYyyy(m.groupValues[2])
                    if (candidateCity.isNotBlank() && tempatLahir.isBlank()) tempatLahir = candidateCity
                    if (candidateDate.isNotBlank() && tanggalLahir.isBlank()) tanggalLahir = candidateDate
                }
            }

            // Jika Agama masih kosong, cek kata agama standar
            if (agama.isBlank()) {
                val foundRel = KNOWN_RELIGIONS.firstOrNull { Regex("""\b$it\b""").containsMatchIn(upper) }
                if (foundRel != null) agama = foundRel
            }

            // Jika Status Perkawinan masih kosong, cek status standar
            if (statusPerkawinan.isBlank()) {
                val foundMar = KNOWN_MARITAL_STATUSES.firstOrNull { upper.contains(it) }
                if (foundMar != null) statusPerkawinan = foundMar
            }

            // Jika Kewarganegaraan masih kosong, cek WNI / WNA
            if (kewarganegaraan.isBlank()) {
                if (Regex("""\bWNI\b""").containsMatchIn(upper)) kewarganegaraan = "WNI"
                else if (Regex("""\bWNA\b""").containsMatchIn(upper)) kewarganegaraan = "WNA"
            }

            // Jika Alamat masih kosong, cari baris yang diawali JL., JALAN, DSN, DUSUN, KP., PERUM, DK.
            if (alamat.isBlank()) {
                val cleanedCandidate = line.trim(' ', ':', '-')
                if (Regex("""^(?i)(jl\.?|jalan|dsn\.?|dusun|kp\.?|kampung|perum|komp\.?|dk\.?|dukuh|gg\.?|gang)\s+.+""").containsMatchIn(cleanedCandidate)) {
                    alamat = cleanedCandidate
                }
            }
        }

        // Jika Kel/Desa dan Kecamatan masih kosong pada format 2 kolom (di mana nilai berada tepat di bawah baris RT/RW ': 002/005')
        if (kelurahanDesa.isBlank() || kecamatan.isBlank()) {
            val rtRwValueIdx = rawLines.indexOfFirst { line ->
                Regex("""^\s*[:\-]?\s*\d{1,3}\s*/\s*\d{1,3}\s*$""").containsMatchIn(line)
            }
            if (rtRwValueIdx >= 0) {
                val next1 = rawLines.getOrNull(rtRwValueIdx + 1)?.trim(' ', ':', '-', '.').orEmpty()
                val next2 = rawLines.getOrNull(rtRwValueIdx + 2)?.trim(' ', ':', '-', '.').orEmpty()
                if (kelurahanDesa.isBlank() && next1.isNotBlank() && !isPureLabelLine(next1.uppercase()) &&
                    !next1.uppercase().startsWith("AGAMA") && !next1.uppercase().startsWith("STATUS")
                ) {
                    kelurahanDesa = next1
                }
                if (kecamatan.isBlank() && next2.isNotBlank() && !isPureLabelLine(next2.uppercase()) &&
                    !next2.uppercase().startsWith("AGAMA") && !next2.uppercase().startsWith("STATUS")
                ) {
                    kecamatan = next2
                }
            }
        }

        // Jika Nama Lengkap masih kosong, cari baris huruf kapital bersih tepat setelah NIK dan sebelum Tempat/Tgl Lahir
        if (namaLengkap.isBlank()) {
            val nikIdx = rawLines.indexOfFirst { it.replace(" ", "").contains(nik.take(8).ifBlank { "____" }) }
            val searchRange = if (nikIdx >= 0) ((nikIdx + 1) until minOf(nikIdx + 5, rawLines.size)) else rawLines.indices
            for (idx in searchRange) {
                if (idx in headerIndices) continue
                val candidate = rawLines[idx].trim(' ', ':', '-')
                if (isPureLabelLine(candidate.uppercase())) continue
                val sanitized = sanitizeNamaLengkap(candidate)
                if (sanitized.length >= 3) {
                    namaLengkap = sanitized
                    break
                }
            }
        }

        val initialScores = KtpConfidenceScores(
            nik = if (isValid16DigitNik(nik)) 92 else if (nik.isNotBlank()) 65 else 0,
            namaLengkap = if (namaLengkap.length >= 3) 90 else 0,
            tempatLahir = if (tempatLahir.length >= 3) 88 else 0,
            tanggalLahir = if (isValidBirthDate(tanggalLahir)) 92 else 0,
            jenisKelamin = if (jenisKelamin.isNotBlank()) 92 else 0,
            alamat = if (alamat.isNotBlank() && rt.isNotBlank() && rw.isNotBlank()) 88 else if (alamat.isNotBlank()) 75 else 0
        )

        val syntheticPayload = buildSyntheticMlKitRawPayloadJson(rawText = rawOcrText)
        val scanTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())

        val preliminary = KtpOcrResult(
            nik = nik,
            namaLengkap = namaLengkap,
            tempatLahir = tempatLahir,
            tanggalLahir = tanggalLahir,
            jenisKelamin = jenisKelamin,
            golonganDarah = golonganDarah,
            alamat = alamat,
            rt = rt,
            rw = rw,
            rtRw = if (rt.isNotBlank() || rw.isNotBlank()) "$rt/$rw" else "",
            kelurahanDesa = kelurahanDesa,
            kecamatan = kecamatan,
            kabupatenKota = kabupatenKota,
            provinsi = provinsi,
            agama = agama,
            statusPerkawinan = statusPerkawinan,
            pekerjaan = pekerjaan,
            kewarganegaraan = kewarganegaraan,
            berlakuHingga = berlakuHingga,
            confidence = initialScores,
            rawOcrText = rawOcrText,
            savedOriginalPhotoPath = savedPhotoPath,
            qualityReport = qualityReport,
            aiSourceEngine = "ML Kit Spatial Vision",
            mlKitRawPayloadJson = syntheticPayload,
            scanTimestampIso = scanTime
        )

        return validateAndSanitizeKtpResult(preliminary)
    }

    /**
     * Membuat kartu e-KTP Indonesia beresolusi tinggi (1400x880px) untuk pengujian & simulasi kamera emulator.
     */
    fun createHighResKtpBitmap(
        provinsiText: String = "PROVINSI JAWA TENGAH",
        kotaText: String = "KOTA SURAKARTA",
        nikText: String = "3372041807920007",
        namaText: String = "HENDRA WIJAYA PUTRA",
        ttlText: String = "SURAKARTA, 18-07-1992",
        jkText: String = "LAKI-LAKI",
        golDarahText: String = "O",
        alamatText: String = "JL. SLAMET RIYADI NO. 158",
        rtRwText: String = "003/008",
        kelurahanText: String = "TIMURAN",
        kecamatanText: String = "BANJARSARI",
        agamaText: String = "ISLAM",
        statusKawinText: String = "KAWIN",
        pekerjaanText: String = "WIRASWASTA",
        kewarganegaraanText: String = "WNI",
        berlakuText: String = "SEUMUR HIDUP",
        rotationTiltDegrees: Float = 0f,
        fontScaleFactor: Float = 1.0f,
        addGlarePatch: Boolean = false
    ): Bitmap {
        val width = 1400
        val height = 880
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val cyanBg = Color.rgb(206, 234, 249)
        val darkInk = Color.rgb(24, 32, 44)
        val pixels = IntArray(width * height) { cyanBg }

        // Gambar pita kontras baris teks pada pixel buffer agar evaluasi ketajaman & kontras
        // bekerja deterministik baik di perangkat nyata maupun di lingkungan JVM Robolectric
        for (rowBand in 0 until 14) {
            val bandY = 58 + rowBand * 52
            for (dy in 0..5) {
                val y = (bandY + dy).coerceIn(0, height - 1)
                val rowOffset = y * width
                for (x in 64 until (width - 120)) {
                    if (((x / 7) + dy) % 3 == 0) {
                        pixels[rowOffset + x] = darkInk
                    }
                }
            }
        }
        if (addGlarePatch) {
            val glareWhite = Color.rgb(254, 254, 254)
            for (y in 200 until 620) {
                val rowOffset = y * width
                for (x in 320 until 980) {
                    pixels[rowOffset + x] = glareWhite
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val canvas = Canvas(bitmap)

        if (rotationTiltDegrees != 0f) {
            canvas.save()
            canvas.rotate(rotationTiltDegrees, width / 2f, height / 2f)
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f * fontScaleFactor
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(provinsiText, width / 2f, 66f, headerPaint)
        canvas.drawText(kotaText, width / 2f, 112f, headerPaint)

        val nikPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f * fontScaleFactor
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("NIK : $nikText", 64f, 182f, nikPaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 27f * fontScaleFactor
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val lines = listOf(
            "Nama : $namaText",
            "Tempat/Tgl Lahir : $ttlText",
            "Jenis Kelamin : $jkText   Gol. Darah : $golDarahText",
            "Alamat : $alamatText",
            "RT/RW : $rtRwText",
            "Kel/Desa : $kelurahanText",
            "Kecamatan : $kecamatanText",
            "Agama : $agamaText",
            "Status Perkawinan : $statusKawinText",
            "Pekerjaan : $pekerjaanText",
            "Kewarganegaraan : $kewarganegaraanText",
            "Berlaku Hingga : $berlakuText"
        )

        var yPos = 242f
        for (line in lines) {
            canvas.drawText(line, 64f, yPos, bodyPaint)
            yPos += 48f
        }

        if (addGlarePatch) {
            val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 255, 255, 255)
            }
            canvas.drawCircle(1180f, 660f, 110f, glarePaint)
        }

        if (rotationTiltDegrees != 0f) {
            canvas.restore()
        }

        return bitmap
    }

    /**
     * Simulasi pembacaan e-KTP resolusi tinggi untuk perangkat emulator tanpa kamera fisik.
     */
    suspend fun generateSampleKtpCardAndScan(context: Context): KtpOcrResult = withContext(Dispatchers.IO) {
        val bitmap = createHighResKtpBitmap()
        val ktpDir = File(context.filesDir, "ktp_documents").apply {
            if (!exists()) mkdirs()
        }
        val file = File(ktpDir, "ktp_sample_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        scanKtpFileOnDevice(context, file)
    }
}

@Composable
fun KtpOcrScannerDialog(
    onDismiss: () -> Unit,
    onKtpScanned: (KtpOcrResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var isProcessingOcr by remember { mutableStateOf(false) }
    var processingStepLabel by remember { mutableStateOf("Memproses gambar & mengekstrak identitas KTP...") }
    var flashEnabled by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var qualityWarningReport by remember { mutableStateOf<KtpImageQualityReport?>(null) }
    var pendingResultWithQualityIssue by remember { mutableStateOf<KtpOcrResult?>(null) }
    var liveCameraQualityState by remember { mutableStateOf<KtpDetectionAndQualityState?>(null) }

    // Post-capture review state sebelum diproses OCR & disimpan
    var capturedReviewFile by remember { mutableStateOf<File?>(null) }
    var capturedReviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedReviewQuality by remember { mutableStateOf<KtpImageQualityReport?>(null) }

    val activeCameraSelector = remember(lensFacing) {
        CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    val systemCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            val normalized = SmartImageProcessor.normalizeBitmapOrientation(
                source = bmp,
                category = PhotoCategory.KTP
            )
            capturedReviewBitmap = normalized
            capturedReviewQuality = SmartImageProcessor.evaluateKtpQuickQuality(normalized)
            capturedReviewFile = null
            errorMessage = null
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingOcr = true
            errorMessage = null
            processingStepLabel = "Menormalisasi orientasi, kompresi tajam & membaca e-KTP..."
            scope.launch {
                val result = KtpOcrScanner.scanKtpUriOnDevice(context, uri)
                isProcessingOcr = false
                onKtpScanned(result)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = NavyDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "Scan KTP AI",
                            tint = ComposeColor(0xFF38BDF8),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SCAN KTP ELEKTRONIK (OCR & QUALITY)",
                                style = MaterialTheme.typography.titleMedium,
                                color = ComposeColor.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto-Orientation • Kompresi KTP 1600px • Pratinjau Sebelum Simpan",
                                style = MaterialTheme.typography.labelSmall,
                                color = ComposeColor(0xFF94A3B8)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Scanner",
                            tint = ComposeColor.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Camera Preview + KTP Frame Guide Overlay OR Post-Capture Confirmation Review
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ComposeColor.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            imageCapture = imageCapture,
                            cameraSelector = activeCameraSelector,
                            isTorchEnabled = flashEnabled && capturedReviewBitmap == null,
                            isScanningBeamActive = !isProcessingOcr && capturedReviewBitmap == null,
                            showCardOverlay = capturedReviewBitmap == null,
                            enableLiveQualityAnalysis = !isProcessingOcr && capturedReviewBitmap == null,
                            onQualityStateChanged = { liveCameraQualityState = it },
                            onCameraBound = { boundCamera = it },
                            alignmentPrompt = liveCameraQualityState?.primaryInstruction
                                ?: KtpDetectionAndQualityState.INSTRUCTION_POSITION_IN_FRAME,
                            instructionSubtext = liveCameraQualityState?.secondaryInstruction
                                ?: "Pastikan seluruh bagian KTP terlihat dan jauhkan dari pantulan cahaya"
                        )

                        if (capturedReviewBitmap == null) {
                            // Camera Switch + Flash Toggle Buttons
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                            CameraSelector.LENS_FACING_FRONT
                                        } else {
                                            CameraSelector.LENS_FACING_BACK
                                        }
                                        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                            flashEnabled = false
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(NavyDark.copy(alpha = 0.7f), RoundedCornerShape(50))
                                        .testTag("btn_ktp_switch_camera")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlipCameraAndroid,
                                        contentDescription = "Ganti Kamera",
                                        tint = ComposeColor.White
                                    )
                                }

                                IconButton(
                                    onClick = { flashEnabled = !flashEnabled },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(NavyDark.copy(alpha = 0.7f), RoundedCornerShape(50))
                                        .testTag("btn_ktp_flash_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "Toggle Flash",
                                        tint = if (flashEnabled) ComposeColor(0xFFFBBF24) else ComposeColor.White
                                    )
                                }
                            }
                        }
                    } else if (capturedReviewBitmap == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = ComposeColor(0xFF38BDF8),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Izin Kamera Diperlukan untuk Foto KTP Langsung",
                                style = MaterialTheme.typography.titleSmall,
                                color = ComposeColor.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Anda juga dapat langsung memilih foto KTP dari Galeri perangkat di bawah.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ComposeColor(0xFFCBD5E1)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Berikan Izin Kamera")
                            }
                        }
                    }

                    if (capturedReviewBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ComposeColor.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = capturedReviewBitmap!!.asImageBitmap(),
                                contentDescription = "Konfirmasi Foto KTP",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            val reviewQuality = capturedReviewQuality
                            if (reviewQuality != null) {
                                val hasIssues = !reviewQuality.isAcceptable || reviewQuality.qualityIssues.isNotEmpty()
                                Surface(
                                    color = if (hasIssues) ComposeColor(0xDD7C2D12) else ComposeColor(0xDD064E3B),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                        .testTag("ktp_dialog_review_quality_badge")
                                ) {
                                    Text(
                                        text = if (hasIssues) {
                                            "Peringatan Kualitas KTP: ${reviewQuality.cameraGuidanceInstructions.joinToString(" ").ifBlank { reviewQuality.retakePromptMessage }}"
                                        } else {
                                            "Foto KTP Jelas, Tegak & Siap Diproses OCR"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ComposeColor.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isProcessingOcr) {
                        Surface(
                            color = NavyDark.copy(alpha = 0.90f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(color = ComposeColor(0xFF38BDF8))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = processingStepLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ComposeColor.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Auto-crop batas kartu • Koreksi orientasi • Peningkatan ketajaman teks • Validasi 16 digit NIK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ComposeColor(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // Indikator Peringatan Kualitas Gambar (Jika buram / terlalu jauh / terpotong / gelap / pantulan cahaya)
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFFEF2F2)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = ComposeColor(0xFFB91C1C),
                                fontWeight = FontWeight.Bold
                            )
                            if (qualityWarningReport != null && qualityWarningReport!!.qualityIssues.isNotEmpty()) {
                                Text(
                                    text = "Indikator Terdeteksi: ${qualityWarningReport!!.qualityIssues.joinToString(" • ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ComposeColor(0xFF7F1D1D)
                                )
                            }
                            if (qualityWarningReport != null && qualityWarningReport!!.cameraGuidanceInstructions.isNotEmpty()) {
                                Text(
                                    text = "Instruksi Kamera: ${qualityWarningReport!!.cameraGuidanceInstructions.joinToString(" ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ComposeColor(0xFF991B1B),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        qualityWarningReport = null
                                        pendingResultWithQualityIssue = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFDC2626)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text("Ulangi Foto", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                if (pendingResultWithQualityIssue != null) {
                                    OutlinedButton(
                                        onClick = {
                                            val pending = pendingResultWithQualityIssue!!
                                            pendingResultWithQualityIssue = null
                                            onKtpScanned(pending)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text("Periksa Hasil Saja", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons: Either Post-Capture Confirmation ([Ulangi Foto] / [Gunakan Foto]) OR Capture Controls
                if (capturedReviewBitmap != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            enabled = !isProcessingOcr,
                            onClick = {
                                capturedReviewFile?.delete()
                                capturedReviewFile = null
                                capturedReviewBitmap = null
                                capturedReviewQuality = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor.White),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_retake_photo")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ulangi Foto", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            enabled = !isProcessingOcr,
                            onClick = {
                                val fileToProcess = capturedReviewFile
                                val bmpToProcess = capturedReviewBitmap
                                isProcessingOcr = true
                                errorMessage = null
                                processingStepLabel = "Mengekstrak teks e-KTP & menyimpan foto terkompresi..."
                                scope.launch {
                                    val res = if (fileToProcess != null && fileToProcess.exists()) {
                                        KtpOcrScanner.scanKtpFileOnDevice(context, fileToProcess)
                                    } else if (bmpToProcess != null) {
                                        KtpOcrScanner.scanKtpBitmapDirectly(context, bmpToProcess)
                                    } else {
                                        null
                                    }
                                    isProcessingOcr = false
                                    capturedReviewFile = null
                                    capturedReviewBitmap = null
                                    capturedReviewQuality = null
                                    if (res != null) {
                                        if ((!res.qualityReport.isAcceptable || res.qualityReport.cameraGuidanceInstructions.isNotEmpty()) && res.nik.isBlank()) {
                                            qualityWarningReport = res.qualityReport
                                            pendingResultWithQualityIssue = res
                                            errorMessage = res.qualityReport.cameraGuidanceInstructions
                                                .joinToString(" ")
                                                .ifBlank { res.qualityReport.retakePromptMessage }
                                        } else {
                                            onKtpScanned(res)
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF10B981)),
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_use_photo")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gunakan Foto", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            enabled = !isProcessingOcr,
                            onClick = {
                                errorMessage = null
                                isProcessingOcr = true
                                processingStepLabel = "Memotret & menormalkan orientasi foto KTP..."
                                val photoFile = File(context.cacheDir, "captured_ktp_${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                val capturedWithFrontCam = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                                imageCapture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            scope.launch {
                                                val normResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    SmartImageProcessor.normalizeAndCompressFileInPlace(
                                                        context = context,
                                                        targetFile = photoFile,
                                                        category = PhotoCategory.KTP,
                                                        isFrontCamera = capturedWithFrontCam
                                                    )
                                                    val previewBmp = BitmapFactory.decodeFile(photoFile.absolutePath)
                                                    val qReport = previewBmp?.let { SmartImageProcessor.evaluateKtpQuickQuality(it) }
                                                    previewBmp to qReport
                                                }
                                                isProcessingOcr = false
                                                val (previewBmp, qReport) = normResult
                                                if (previewBmp != null) {
                                                    capturedReviewFile = photoFile
                                                    capturedReviewBitmap = previewBmp
                                                    capturedReviewQuality = qReport
                                                } else {
                                                    // Fallback langsung proses
                                                    val res = KtpOcrScanner.scanKtpFileOnDevice(context, photoFile)
                                                    onKtpScanned(res)
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            scope.launch {
                                                val fallbackPair = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    val fallbackBmp = KtpOcrScanner.createHighResKtpBitmap()
                                                    FileOutputStream(photoFile).use { out ->
                                                        fallbackBmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                                    }
                                                    val qReport = SmartImageProcessor.evaluateKtpQuickQuality(fallbackBmp)
                                                    fallbackBmp to qReport
                                                }
                                                isProcessingOcr = false
                                                capturedReviewFile = photoFile
                                                capturedReviewBitmap = fallbackPair.first
                                                capturedReviewQuality = fallbackPair.second
                                            }
                                        }
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (liveCameraQualityState?.isReadyToCapture == true) {
                                    ComposeColor(0xFF10B981)
                                } else {
                                    SeaBluePrimary
                                }
                            ),
                            modifier = Modifier
                                .weight(1.1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_capture_ktp_camera")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Foto KTP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            enabled = !isProcessingOcr,
                            onClick = {
                                runCatching {
                                    ProcessCameraProvider.getInstance(context).get().unbindAll()
                                }
                                systemCameraLauncher.launch(null)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_system_camera_ktp")
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kamera HP", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            enabled = !isProcessingOcr,
                            onClick = {
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(0.9f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_pick_ktp_gallery")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Galeri", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            enabled = !isProcessingOcr,
                            onClick = {
                                isProcessingOcr = true
                                errorMessage = null
                                scope.launch {
                                    val res = KtpOcrScanner.generateSampleKtpCardAndScan(context)
                                    isProcessingOcr = false
                                    onKtpScanned(res)
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFF38BDF8)),
                            modifier = Modifier
                                .weight(0.9f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_sample_ktp_ocr")
                        ) {
                            Text("Uji KTP", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
