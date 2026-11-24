package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model data yang merangkum seluruh hasil diagnosis dari panggilan Google Gemini Vision API
 * dan proses parsing JSON e-KTP. Menyimpan raw response dari Gemini, waktu eksekusi,
 * detail exception, serta ringkasan parsing untuk memudahkan penelusuran masalah (troubleshooting).
 */
data class KtpGeminiDiagnosticReport(
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp)),
    val durationMs: Long = 0L,
    val modelName: String = KtpGeminiParser.MODEL_NAME,
    val isSuccess: Boolean = false,
    val status: String = "PENDING", // "SUCCESS", "EMPTY_RESPONSE", "GEMINI_API_ERROR", "TIMEOUT", "JSON_PARSE_ERROR", "VALIDATION_ERROR"
    val errorStage: String? = null, // "INITIALIZATION", "API_REQUEST", "RESPONSE_EXTRACTION", "JSON_PARSING", "SCHEMA_MAPPING"
    val rawResponseText: String? = null,
    val candidateCount: Int = 0,
    val finishReason: String? = null,
    val parsedJsonCleaned: String? = null,
    val parsedKtpData: KtpData? = null,
    val validationErrors: List<String> = emptyList(),
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
    val stackTracePreview: String? = null,
    val imageDetails: String? = null
) {
    /**
     * Menghasilkan representasi log multi-baris yang rapi untuk dicetak ke Logcat Android.
     */
    fun toFormattedLog(): String {
        return buildString {
            appendLine("=================== [KtpGeminiDiagnostics REPORT] ===================")
            appendLine("Timestamp       : $formattedTime ($timestamp)")
            appendLine("Duration        : ${durationMs}ms")
            appendLine("Model           : $modelName")
            appendLine("Status          : $status (Success: $isSuccess)")
            if (errorStage != null) {
                appendLine("Error Stage     : $errorStage")
            }
            if (imageDetails != null) {
                appendLine("Image Details   : $imageDetails")
            }
            if (finishReason != null) {
                appendLine("Finish Reason   : $finishReason (Candidates: $candidateCount)")
            }
            if (!rawResponseText.isNullOrBlank()) {
                appendLine("--- RAW GEMINI RESPONSE TEXT (Length: ${rawResponseText.length}) ---")
                appendLine(rawResponseText)
                appendLine("---------------------------------------------------------------------")
            } else {
                appendLine("Raw Response    : <EMPTY / NULL>")
            }
            if (!parsedJsonCleaned.isNullOrBlank() && parsedJsonCleaned != rawResponseText) {
                appendLine("--- CLEANED JSON STRING ---")
                appendLine(parsedJsonCleaned)
                appendLine("---------------------------")
            }
            if (parsedKtpData != null) {
                appendLine("Parsed NIK      : '${parsedKtpData.nik}' (Len: ${parsedKtpData.nik.length})")
                appendLine("Parsed Nama     : '${parsedKtpData.namaLengkap}'")
                appendLine("Parsed Lahir    : '${parsedKtpData.tempatLahir}', '${parsedKtpData.tanggalLahir}'")
                appendLine("Parsed Kelamin  : '${parsedKtpData.jenisKelamin}'")
                appendLine("Parsed Alamat   : '${parsedKtpData.alamat}' (RT: ${parsedKtpData.rt}, RW: ${parsedKtpData.rw}, Kel: ${parsedKtpData.kelurahan}, Kec: ${parsedKtpData.kecamatan}, Kota: ${parsedKtpData.kabupatenKota}, Prov: ${parsedKtpData.provinsi})")
                appendLine("Confidence      : NIK=${parsedKtpData.confidence.nik}, Nama=${parsedKtpData.confidence.namaLengkap}, Alamat=${parsedKtpData.confidence.alamat}")
            }
            if (validationErrors.isNotEmpty()) {
                appendLine("Validation Errs : ${validationErrors.joinToString("; ")}")
            }
            if (exceptionClass != null || exceptionMessage != null) {
                appendLine("--- EXCEPTION DETAILS ---")
                appendLine("Class   : $exceptionClass")
                appendLine("Message : $exceptionMessage")
                if (!stackTracePreview.isNullOrBlank()) {
                    appendLine("StackTrace Preview:")
                    appendLine(stackTracePreview)
                }
                appendLine("-------------------------")
            }
            appendLine("=====================================================================")
        }
    }

    /**
     * Ringkasan singkat satu baris untuk tampilan UI badge atau notifikasi.
     */
    fun toSummaryString(): String {
        return if (isSuccess) {
            "✓ Gemini Vision Sukses (${durationMs}ms, NIK: ${parsedKtpData?.nik.orEmpty()})"
        } else {
            "✗ Gemini Vision Gagal [$status]: ${exceptionMessage ?: errorStage ?: "Respons Kosong"}"
        }
    }

    /**
     * Serialisasi ke format JSON terstruktur untuk kemudahan inspeksi teknis.
     */
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("timestamp", timestamp)
        root.put("formattedTime", formattedTime)
        root.put("durationMs", durationMs)
        root.put("modelName", modelName)
        root.put("isSuccess", isSuccess)
        root.put("status", status)
        root.put("errorStage", errorStage ?: JSONObject.NULL)
        root.put("rawResponseText", rawResponseText ?: JSONObject.NULL)
        root.put("candidateCount", candidateCount)
        root.put("finishReason", finishReason ?: JSONObject.NULL)
        root.put("parsedJsonCleaned", parsedJsonCleaned ?: JSONObject.NULL)
        root.put("exceptionClass", exceptionClass ?: JSONObject.NULL)
        root.put("exceptionMessage", exceptionMessage ?: JSONObject.NULL)
        root.put("imageDetails", imageDetails ?: JSONObject.NULL)
        if (parsedKtpData != null) {
            root.put("parsedNik", parsedKtpData.nik)
            root.put("parsedNama", parsedKtpData.namaLengkap)
            root.put("parsedStatus", parsedKtpData.statusPembacaan)
        }
        return root.toString(2)
    }
}

/**
 * Tipe hasil eksekusi berdiagnostik (Sealed Class).
 */
sealed class KtpGeminiDiagnosticResult {
    abstract val report: KtpGeminiDiagnosticReport
    abstract val ktpData: KtpData

    data class Success(
        val data: KtpData,
        override val report: KtpGeminiDiagnosticReport
    ) : KtpGeminiDiagnosticResult() {
        override val ktpData: KtpData get() = data
    }

    data class Failure(
        val fallbackData: KtpData,
        override val report: KtpGeminiDiagnosticReport,
        val throwable: Throwable?
    ) : KtpGeminiDiagnosticResult() {
        override val ktpData: KtpData get() = fallbackData
    }
}

/**
 * Diagnostic Logging Wrapper untuk `KtpGeminiParser`.
 *
 * Mengelilingi panggilan Google Gemini Vision API dan langkah parsing JSON dengan pelacakan
 * metrik lengkap: menangkap teks respons mentah (`response.text`), kandidat dan finish reason,
 * waktu latensi, jenis & pesan exception jika terjadi error, serta status validasi field e-KTP.
 *
 * Semua diagnosis dicatat ke Logcat Android menggunakan tag `KtpGeminiDiagnostics`
 * dan disimpan pada memori (`lastReport` dan `recentReports`) sehingga dapat langsung
 * diinspeksi oleh admin atau pengembang via UI.
 */
object KtpGeminiDiagnosticWrapper {

    const val TAG = "KtpGeminiDiagnostics"
    private const val MAX_RECENT_REPORTS = 10

    private val lock = Any()
    private val _recentReports = mutableListOf<KtpGeminiDiagnosticReport>()

    @Volatile
    var lastReport: KtpGeminiDiagnosticReport? = null
        private set

    /**
     * Mengambil riwayat laporan diagnostik terbaru.
     */
    fun getRecentReports(): List<KtpGeminiDiagnosticReport> {
        synchronized(lock) {
            return _recentReports.toList()
        }
    }

    /**
     * Menghapus riwayat diagnostik.
     */
    fun clearReports() {
        synchronized(lock) {
            _recentReports.clear()
            lastReport = null
        }
        Log.d(TAG, "Diagnostic reports cleared.")
    }

    /**
     * Menyimpan dan mencatat laporan diagnostik ke Logcat Android.
     */
    fun recordReport(report: KtpGeminiDiagnosticReport) {
        synchronized(lock) {
            lastReport = report
            _recentReports.add(0, report)
            if (_recentReports.size > MAX_RECENT_REPORTS) {
                _recentReports.removeAt(_recentReports.size - 1)
            }
        }

        val formatted = report.toFormattedLog()
        if (report.isSuccess) {
            Log.i(TAG, formatted)
        } else {
            Log.e(TAG, formatted)
        }
    }

    /**
     * Eksekusi wrapper utama untuk memanggil Google Gemini Vision API secara aman dengan logging diagnostik lengkap.
     * Menangkap seluruh raw response, waktu eksekusi, serta exception jika gagal.
     */
    suspend fun executeWithDiagnostics(
        context: Context? = null,
        ktpBitmap: Bitmap,
        preprocessImage: Boolean = true
    ): KtpGeminiDiagnosticResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val imageInfo = "${ktpBitmap.width}x${ktpBitmap.height} (${(ktpBitmap.byteCount / 1024)} KB, config=${ktpBitmap.config})"
        Log.d(TAG, "Starting Gemini Vision extraction for image: $imageInfo (preprocess=$preprocessImage)")

        // Step 1: Inisialisasi Firebase App jika belum
        if (context != null && FirebaseApp.getApps(context).isEmpty()) {
            try {
                FirebaseApp.initializeApp(context)
                Log.d(TAG, "FirebaseApp initialized successfully for context.")
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseApp.initializeApp warning: ${e.message}")
            }
        }

        // Step 2: Prapemrosesan Gambar
        val bitmapForAi = try {
            if (preprocessImage) {
                KtpImageProcessor.processKtpBitmapForAi(ktpBitmap).processedBitmap
            } else {
                ktpBitmap
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val (exClass, exMsg, stackPreview) = formatThrowable(e)
            val report = KtpGeminiDiagnosticReport(
                timestamp = startTime,
                durationMs = duration,
                modelName = KtpGeminiParser.MODEL_NAME,
                isSuccess = false,
                status = "IMAGE_PREPROCESSING_ERROR",
                errorStage = "IMAGE_PREPARATION",
                exceptionClass = exClass,
                exceptionMessage = exMsg,
                stackTracePreview = stackPreview,
                imageDetails = imageInfo
            )
            recordReport(report)
            return@withContext KtpGeminiDiagnosticResult.Failure(
                fallbackData = KtpData(statusPembacaan = "gagal"),
                report = report,
                throwable = e
            )
        }

        // Step 3: Panggilan ke Gemini Vision via Firebase AI Logic
        val response: GenerateContentResponse
        try {
            val generativeModel = Firebase.ai.generativeModel(
                modelName = KtpGeminiParser.MODEL_NAME,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = KtpGeminiParser.ktpJsonSchema
                    temperature = 0.0f
                }
            )

            response = withTimeout(KtpGeminiParser.TIMEOUT_MS) {
                generativeModel.generateContent(
                    content {
                        image(bitmapForAi)
                        text(KtpGeminiParser.KTP_EXTRACTION_PROMPT)
                    }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val duration = System.currentTimeMillis() - startTime
            val (exClass, exMsg, stackPreview) = formatThrowable(e)
            val isTimeout = e is kotlinx.coroutines.TimeoutCancellationException
            val status = if (isTimeout) "TIMEOUT" else "GEMINI_API_ERROR"

            val report = KtpGeminiDiagnosticReport(
                timestamp = startTime,
                durationMs = duration,
                modelName = KtpGeminiParser.MODEL_NAME,
                isSuccess = false,
                status = status,
                errorStage = "API_REQUEST",
                exceptionClass = exClass,
                exceptionMessage = exMsg,
                stackTracePreview = stackPreview,
                imageDetails = imageInfo
            )
            recordReport(report)
            return@withContext KtpGeminiDiagnosticResult.Failure(
                fallbackData = KtpData(statusPembacaan = "gagal"),
                report = report,
                throwable = e
            )
        }

        // Step 4: Menangkap Raw Response & Mem-parsing JSON
        val (parsedData, report) = parseResponseWithDiagnostics(
            response = response,
            startTime = startTime,
            imageDetails = imageInfo
        )

        recordReport(report)

        return@withContext if (report.isSuccess) {
            KtpGeminiDiagnosticResult.Success(data = parsedData, report = report)
        } else {
            KtpGeminiDiagnosticResult.Failure(
                fallbackData = parsedData,
                report = report,
                throwable = null
            )
        }
    }

    /**
     * Mem-parsing objek `GenerateContentResponse` dari Gemini Vision dengan penangkapan data diagnostik penuh.
     */
    fun parseResponseWithDiagnostics(
        response: GenerateContentResponse,
        startTime: Long = System.currentTimeMillis(),
        imageDetails: String? = null
    ): Pair<KtpData, KtpGeminiDiagnosticReport> {
        val duration = System.currentTimeMillis() - startTime
        val rawText = response.text?.trim().orEmpty()
        val candidateCount = response.candidates.size
        val firstCandidate = response.candidates.firstOrNull()
        val finishReason = firstCandidate?.finishReason?.name

        if (rawText.isBlank()) {
            val emptyReport = KtpGeminiDiagnosticReport(
                timestamp = startTime,
                durationMs = duration,
                modelName = KtpGeminiParser.MODEL_NAME,
                isSuccess = false,
                status = "EMPTY_RESPONSE",
                errorStage = "RESPONSE_EXTRACTION",
                rawResponseText = rawText,
                candidateCount = candidateCount,
                finishReason = finishReason,
                exceptionMessage = "Respons dari Gemini Vision kosong atau bernilai null (FinishReason: $finishReason)",
                imageDetails = imageDetails
            )
            return Pair(KtpData(statusPembacaan = "gagal"), emptyReport)
        }

        return parseJsonWithDiagnostics(
            rawJsonString = rawText,
            startTime = startTime,
            candidateCount = candidateCount,
            finishReason = finishReason,
            imageDetails = imageDetails
        )
    }

    /**
     * Mem-parsing string JSON dari Gemini Vision ke `KtpData` dengan pencatatan kesalahan JSON dan validasi field.
     */
    fun parseJsonWithDiagnostics(
        rawJsonString: String,
        startTime: Long = System.currentTimeMillis(),
        candidateCount: Int = 1,
        finishReason: String? = "STOP",
        imageDetails: String? = null
    ): Pair<KtpData, KtpGeminiDiagnosticReport> {
        val duration = System.currentTimeMillis() - startTime
        val cleanedJson = rawJsonString
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Validasi JSON Syntax
        val jsonObject: JSONObject
        try {
            jsonObject = JSONObject(cleanedJson)
        } catch (e: Exception) {
            val (exClass, exMsg, stackPreview) = formatThrowable(e)
            val parseErrorReport = KtpGeminiDiagnosticReport(
                timestamp = startTime,
                durationMs = duration,
                modelName = KtpGeminiParser.MODEL_NAME,
                isSuccess = false,
                status = "JSON_PARSE_ERROR",
                errorStage = "JSON_PARSING",
                rawResponseText = rawJsonString,
                parsedJsonCleaned = cleanedJson,
                candidateCount = candidateCount,
                finishReason = finishReason,
                exceptionClass = exClass,
                exceptionMessage = "Gagal mem-parsing teks Gemini ke format JSON: $exMsg",
                stackTracePreview = stackPreview,
                imageDetails = imageDetails
            )
            return Pair(KtpData(statusPembacaan = "gagal"), parseErrorReport)
        }

        // Mapping ke KtpData & Validasi Field e-KTP
        return try {
            val validatedResult = KtpOcrScanner.parseKtpStructuredJson(
                jsonString = cleanedJson,
                aiEngineLabel = "Google Gemini Vision AI"
            )
            val ktpData = KtpGeminiParser.fromKtpOcrResult(validatedResult)
            val validation = KtpGeminiParser.validateKtpData(ktpData)

            val valErrors = mutableListOf<String>()
            if (!validation.isNikValid) valErrors.add("NIK tidak valid (${ktpData.nik.length} digit): '${ktpData.nik}'")
            if (!validation.isTanggalLahirValid) valErrors.add("Tanggal Lahir tidak valid: '${ktpData.tanggalLahir}'")
            if (!validation.isJenisKelaminValid) valErrors.add("Jenis Kelamin tidak valid: '${ktpData.jenisKelamin}'")

            val isFullySuccessful = ktpData.statusPembacaan == "berhasil" &&
                ktpData.nik.isNotBlank() &&
                validation.isNikValid

            val status = when {
                isFullySuccessful -> "SUCCESS"
                ktpData.nik.isNotBlank() -> "PARTIAL_SUCCESS_WITH_WARNINGS"
                else -> "VALIDATION_ERROR"
            }

            val successReport = KtpGeminiDiagnosticReport(
                timestamp = startTime,
                durationMs = duration,
                modelName = KtpGeminiParser.MODEL_NAME,
                isSuccess = isFullySuccessful,
                status = status,
                errorStage = if (isFullySuccessful) null else "SCHEMA_MAPPING",
                rawResponseText = rawJsonString,
                parsedJsonCleaned = cleanedJson,
                parsedKtpData = ktpData,
                candidateCount = candidateCount,
                finishReason = finishReason,
                validationErrors = valErrors,
                imageDetails = imageDetails
            )

            Pair(ktpData, successReport)
        } catch (e: Exception) {
            val (exClass, exMsg, stackPreview) = formatThrowable(e)
            val mappingErrorReport = KtpGeminiDiagnosticReport(
                timestamp = startTime,
                durationMs = duration,
                modelName = KtpGeminiParser.MODEL_NAME,
                isSuccess = false,
                status = "SCHEMA_MAPPING_ERROR",
                errorStage = "SCHEMA_MAPPING",
                rawResponseText = rawJsonString,
                parsedJsonCleaned = cleanedJson,
                candidateCount = candidateCount,
                finishReason = finishReason,
                exceptionClass = exClass,
                exceptionMessage = "Kesalahan saat memetakan JSON ke KtpData: $exMsg",
                stackTracePreview = stackPreview,
                imageDetails = imageDetails
            )
            Pair(KtpData(statusPembacaan = "gagal"), mappingErrorReport)
        }
    }

    /**
     * Memformat objek Throwable menjadi informasi nama kelas, pesan, dan potongan ringkas stack trace.
     */
    private fun formatThrowable(t: Throwable): Triple<String, String, String> {
        val className = t.javaClass.name
        val message = t.message ?: t.localizedMessage ?: "Unknown error"
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val fullStack = sw.toString()
        val previewLines = fullStack.lines().take(12).joinToString("\n")
        return Triple(className, message, previewLines)
    }
}
