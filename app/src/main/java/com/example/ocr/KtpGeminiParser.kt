package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Flag error spesifik untuk setiap atribut utama pada model `KtpData`.
 */
enum class KtpFieldErrorFlag(val jsonKey: String, val message: String) {
    INVALID_NIK("nik", "NIK harus terdiri dari tepat 16 digit angka (0–9)."),
    INVALID_TANGGAL_LAHIR("tanggal_lahir", "Tanggal lahir harus sesuai format DD-MM-YYYY yang valid."),
    INVALID_JENIS_KELAMIN("jenis_kelamin", "Jenis kelamin harus bernilai 'Laki-laki' atau 'Perempuan'.")
}

/**
 * Hasil validasi model `KtpData` yang memuat flag boolean spesifik, himpunan `KtpFieldErrorFlag`,
 * peta status error per key JSON, serta pesan kesalahan untuk setiap field yang tidak valid.
 */
data class KtpValidationResult(
    val isNikValid: Boolean,
    val isTanggalLahirValid: Boolean,
    val isJenisKelaminValid: Boolean,
    val invalidNikError: Boolean = !isNikValid,
    val invalidTanggalLahirError: Boolean = !isTanggalLahirValid,
    val invalidJenisKelaminError: Boolean = !isJenisKelaminValid,
    val errorFlags: Set<KtpFieldErrorFlag> = emptySet(),
    val fieldErrors: Map<String, Boolean> = mapOf(
        "nik" to !isNikValid,
        "tanggal_lahir" to !isTanggalLahirValid,
        "jenis_kelamin" to !isJenisKelaminValid
    ),
    val errorMessages: Map<String, String> = emptyMap()
) {
    val isValid: Boolean
        get() = isNikValid && isTanggalLahirValid && isJenisKelaminValid && errorFlags.isEmpty()

    fun hasError(jsonKey: String): Boolean = fieldErrors[jsonKey] == true
}

/**
 * Fungsi helper tingkat atas (top-level) untuk memvalidasi model `KtpData`:
 * 1. Memastikan `nik` terdiri dari tepat 16 digit angka (`^\d{16}$`).
 * 2. Memastikan `tanggalLahir` sesuai dengan format `DD-MM-YYYY` dan merupakan tanggal kalender yang sah.
 * 3. Memastikan `jenisKelamin` bernilai tepat `'Laki-laki'` atau `'Perempuan'`.
 * Mengembalikan `KtpValidationResult` dengan flag error spesifik untuk setiap field yang tidak valid.
 */
fun validateKtpData(ktpData: KtpData): KtpValidationResult {
    return KtpGeminiParser.validateKtpData(ktpData)
}

/**
 * Skor keyakinan (confidence 0–100) untuk setiap field utama e-KTP Indonesia.
 * - Confidence < 80: Tandai warna kuning ("Perlu Verifikasi") agar diperiksa admin.
 * - Confidence < 50: Jangan isi otomatis ke form pelanggan tanpa verifikasi admin.
 */
data class KtpConfidence(
    val nik: Int = 0,
    val namaLengkap: Int = 0,
    val tempatLahir: Int = 0,
    val tanggalLahir: Int = 0,
    val jenisKelamin: Int = 0,
    val alamat: Int = 0
) {
    fun scoreForKey(jsonKey: String): Int = when (jsonKey) {
        "nik" -> nik
        "nama", "nama_lengkap" -> namaLengkap
        "tempat_lahir" -> tempatLahir
        "tanggal_lahir" -> tanggalLahir
        "jenis_kelamin" -> jenisKelamin
        "alamat", "rt", "rw", "kelurahan", "kecamatan" -> alamat
        else -> 85
    }
}

/**
 * Objek hasil pembacaan terstruktur KTP Elektronik (e-KTP) Indonesia sesuai skema JSON wajib (Section C & D).
 */
data class KtpData(
    val nik: String = "",
    val namaLengkap: String = "",
    val tempatLahir: String = "",
    val tanggalLahir: String = "",
    val jenisKelamin: String = "",
    val golonganDarah: String = "",
    val alamat: String = "",
    val rt: String = "",
    val rw: String = "",
    val kelurahan: String = "",
    val kecamatan: String = "",
    val kabupatenKota: String = "",
    val provinsi: String = "",
    val agama: String = "",
    val statusPerkawinan: String = "",
    val pekerjaan: String = "",
    val kewarganegaraan: String = "",
    val berlakuHingga: String = "",
    val confidence: KtpConfidence = KtpConfidence(),
    val statusPembacaan: String = "berhasil"
) {
    /** Alias sesuai nama key JSON Section D ("nama"). */
    val nama: String
        get() = namaLengkap

    /** Gabungan Tempat, Tanggal Lahir (misal: "SURAKARTA, 19-08-1994"). */
    val ttl: String
        get() = listOf(tempatLahir.trim(), tanggalLahir.trim())
            .filter { it.isNotBlank() }
            .joinToString(", ")

    /** Gabungan Alamat KTP Lengkap (Alamat, RT/RW, Kel/Desa, Kecamatan, Kota/Kabupaten, Provinsi). */
    val alamatLengkap: String
        get() {
            val rtRwPart = if (rt.isNotBlank() || rw.isNotBlank()) {
                "RT ${rt.ifBlank { "000" }}/RW ${rw.ifBlank { "000" }}"
            } else ""
            val kelPart = if (kelurahan.isNotBlank()) "Kel. $kelurahan" else ""
            val kecPart = if (kecamatan.isNotBlank()) "Kec. $kecamatan" else ""
            return listOf(alamat.trim(), rtRwPart, kelPart, kecPart, kabupatenKota.trim(), provinsi.trim())
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }

    /** Format 6 baris "Siap Salin Tanpa Label" untuk ditempel ke kontrak sewa. */
    val teksSiapSalinTanpaLabel: String
        get() = listOf(
            nik.trim(),
            namaLengkap.trim(),
            ttl,
            jenisKelamin.trim(),
            pekerjaan.trim(),
            alamatLengkap
        ).filter { it.isNotBlank() }.joinToString("\n")

    /**
     * Menghasilkan JSON ringkas sesuai spesifikasi "Auto-Fill Form & Format Siap Salin Tanpa Label":
     * {
     *   "nik": "...",
     *   "nama": "...",
     *   "ttl": "...",
     *   "jenis_kelamin": "...",
     *   "pekerjaan": "...",
     *   "alamat_lengkap": "...",
     *   "teks_siap_salin_tanpa_label": "..."
     * }
     */
    fun toReadyToCopySummaryJson(): String {
        val obj = JSONObject()
        obj.put("nik", nik)
        obj.put("nama", namaLengkap)
        obj.put("ttl", ttl)
        obj.put("jenis_kelamin", jenisKelamin.uppercase())
        obj.put("pekerjaan", pekerjaan)
        obj.put("alamat_lengkap", alamatLengkap)
        obj.put("teks_siap_salin_tanpa_label", teksSiapSalinTanpaLabel)
        return obj.toString(2)
    }

    /**
     * Memvalidasi instance `KtpData` ini (`nik` 16 digit, `tanggalLahir` DD-MM-YYYY,
     * dan `jenisKelamin` 'Laki-laki' atau 'Perempuan').
     */
    fun validate(): KtpValidationResult = KtpGeminiParser.validateKtpData(this)

    /**
     * Mengonversi `KtpData` ke string JSON sesuai format wajib pada prompt (Section C & D).
     */
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("nik", nik)
        root.put("nama", namaLengkap)
        root.put("nama_lengkap", namaLengkap)
        root.put("ttl", ttl)
        root.put("tempat_lahir", tempatLahir)
        root.put("tanggal_lahir", tanggalLahir)
        root.put("jenis_kelamin", jenisKelamin)
        root.put("golongan_darah", golonganDarah)
        root.put("alamat", alamat)
        root.put("alamat_lengkap", alamatLengkap)
        root.put("rt", rt)
        root.put("rw", rw)
        root.put("kelurahan", kelurahan)
        root.put("kecamatan", kecamatan)
        root.put("kabupaten_kota", kabupatenKota)
        root.put("provinsi", provinsi)
        root.put("agama", agama)
        root.put("status_perkawinan", statusPerkawinan)
        root.put("pekerjaan", pekerjaan)
        root.put("kewarganegaraan", kewarganegaraan)
        root.put("berlaku_hingga", berlakuHingga)
        root.put("teks_siap_salin_tanpa_label", teksSiapSalinTanpaLabel)

        val confObj = JSONObject()
        confObj.put("nik", confidence.nik.coerceIn(0, 100))
        confObj.put("nama", confidence.namaLengkap.coerceIn(0, 100))
        confObj.put("nama_lengkap", confidence.namaLengkap.coerceIn(0, 100))
        confObj.put("tempat_lahir", confidence.tempatLahir.coerceIn(0, 100))
        confObj.put("tanggal_lahir", confidence.tanggalLahir.coerceIn(0, 100))
        confObj.put("jenis_kelamin", confidence.jenisKelamin.coerceIn(0, 100))
        confObj.put("alamat", confidence.alamat.coerceIn(0, 100))
        root.put("confidence", confObj)
        root.put("status_pembacaan", statusPembacaan)
        return root.toString(2)
    }

    /**
     * Mengecek apakah field tertentu perlu ditandai kuning (confidence < 80).
     */
    fun needsVerification(jsonKey: String): Boolean {
        return confidence.scoreForKey(jsonKey) < KtpGeminiParser.CONFIDENCE_YELLOW_THRESHOLD
    }

    /**
     * Mengecek apakah field tertentu aman untuk diisi otomatis (confidence >= 50).
     */
    fun shouldAutoFill(jsonKey: String): Boolean {
        return confidence.scoreForKey(jsonKey) >= KtpGeminiParser.CONFIDENCE_AUTOFILL_MIN_THRESHOLD
    }

    /**
     * Memetakan field ke Map berdasarkan nama key JSON untuk pengisian formulir pelanggan (Section D & E).
     */
    fun toCustomerFormMap(includeLowConfidenceUnder50: Boolean = false): Map<String, String> {
        fun pick(jsonKey: String, value: String): String {
            if (value.isBlank()) return ""
            if (!includeLowConfidenceUnder50 && !shouldAutoFill(jsonKey)) return ""
            return value
        }
        return mapOf(
            "nik" to pick("nik", nik),
            "nama" to pick("nama_lengkap", namaLengkap),
            "nama_lengkap" to pick("nama_lengkap", namaLengkap),
            "ttl" to ttl,
            "tempat_lahir" to pick("tempat_lahir", tempatLahir),
            "tanggal_lahir" to pick("tanggal_lahir", tanggalLahir),
            "jenis_kelamin" to pick("jenis_kelamin", jenisKelamin),
            "golongan_darah" to pick("golongan_darah", golonganDarah),
            "alamat" to pick("alamat", alamat),
            "alamat_lengkap" to alamatLengkap,
            "rt" to pick("rt", rt),
            "rw" to pick("rw", rw),
            "kelurahan" to pick("kelurahan", kelurahan),
            "kecamatan" to pick("kecamatan", kecamatan),
            "kabupaten_kota" to pick("kabupaten_kota", kabupatenKota),
            "provinsi" to pick("provinsi", provinsi),
            "agama" to pick("agama", agama),
            "status_perkawinan" to pick("status_perkawinan", statusPerkawinan),
            "pekerjaan" to pick("pekerjaan", pekerjaan),
            "kewarganegaraan" to pick("kewarganegaraan", kewarganegaraan),
            "berlaku_hingga" to pick("berlaku_hingga", berlakuHingga),
            "teks_siap_salin_tanpa_label" to teksSiapSalinTanpaLabel
        )
    }

    /**
     * Memetakan `KtpData` langsung ke `CustomerRegistrationFormState` untuk pengisian otomatis
     * formulir pelanggan sesuai key JSON dan aturan confidence (Section D & E).
     */
    fun toCustomerRegistrationFormState(
        currentForm: CustomerRegistrationFormState = CustomerRegistrationFormState(),
        allowLowConfidenceUnder50: Boolean = false,
        savedPhotoPath: String = ""
    ): CustomerRegistrationFormState {
        return KtpGeminiParser.mapKtpDataToCustomerForm(
            ktpData = this,
            currentForm = currentForm,
            allowLowConfidenceUnder50 = allowLowConfidenceUnder50,
            savedPhotoPath = savedPhotoPath
        )
    }

    /**
     * Mengonversi `KtpData` ke `KtpOcrResult` yang digunakan oleh komponen UI aplikasi.
     */
    fun toKtpOcrResult(
        savedOriginalPhotoPath: String = "",
        qualityReport: KtpImageQualityReport = KtpImageQualityReport()
    ): KtpOcrResult {
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
            rtRw = if (rt.isNotBlank() || rw.isNotBlank()) "${rt.padStart(3, '0')}/${rw.padStart(3, '0')}" else "",
            kelurahanDesa = kelurahan,
            kecamatan = kecamatan,
            kabupatenKota = kabupatenKota,
            provinsi = provinsi,
            agama = agama,
            statusPerkawinan = statusPerkawinan,
            pekerjaan = pekerjaan,
            kewarganegaraan = kewarganegaraan,
            berlakuHingga = berlakuHingga,
            confidence = KtpConfidenceScores(
                nik = confidence.nik,
                namaLengkap = confidence.namaLengkap,
                tempatLahir = confidence.tempatLahir,
                tanggalLahir = confidence.tanggalLahir,
                jenisKelamin = confidence.jenisKelamin,
                alamat = confidence.alamat
            ),
            statusPembacaan = statusPembacaan,
            savedOriginalPhotoPath = savedOriginalPhotoPath,
            qualityReport = qualityReport,
            aiSourceEngine = "Google Gemini Vision AI"
        )
        return KtpOcrScanner.validateAndSanitizeKtpResult(preliminary)
    }
}

/**
 * Singleton `KtpGeminiParser` yang berinteraksi dengan Google Generative AI SDK melalui Firebase AI Logic
 * (`Firebase.ai` dengan model `gemini-3.8-flash`), mengirimkan gambar KTP yang telah diproses,
 * mem-parsing respons ke dalam struktur JSON wajib (`KtpData`), memvalidasi field utama (`validateKtpData`),
 * serta menyediakan pemetaan otomatis ke kolom formulir pelanggan (`CustomerRegistrationFormState`).
 */
object KtpGeminiParser {

    const val MODEL_NAME = "gemini-3.8-flash"
    const val TIMEOUT_MS = 60_000L
    const val CONFIDENCE_YELLOW_THRESHOLD = 80
    const val CONFIDENCE_AUTOFILL_MIN_THRESHOLD = 50

    private val NIK_16_DIGITS_REGEX = Regex("""^\d{16}$""")
    private val DD_MM_YYYY_REGEX = Regex("""^(\d{2})-(\d{2})-(\d{4})$""")
    private val VALID_JENIS_KELAMIN_SET = setOf("Laki-laki", "Perempuan")

    /**
     * Memvalidasi model `KtpData` menggunakan `KtpDataValidator`:
     * - `nik` harus tepat 16 digit angka (`^\d{16}$`).
     * - `tanggalLahir` harus berformat `DD-MM-YYYY` dengan tanggal, bulan, dan tahun kalender yang valid.
     * - `jenisKelamin` harus memuat atau bernilai `'Laki-laki'` atau `'Perempuan'`.
     * Mengembalikan `KtpValidationResult` yang berisi flag error spesifik untuk setiap field yang tidak valid.
     */
    fun validateKtpData(ktpData: KtpData): KtpValidationResult {
        return KtpDataValidator.validate(ktpData).toLegacyKtpValidationResult()
    }

    /**
     * Memvalidasi model `KtpData` dengan hasil `KtpDataValidationResult` yang menyertakan kode error deskriptif.
     */
    fun validateKtpDataWithCodes(ktpData: KtpData): KtpDataValidationResult {
        return KtpDataValidator.validate(ktpData)
    }

    /**
     * Memeriksa apakah NIK terdiri dari tepat 16 digit angka (mempertahankan angka 0 di depan).
     */
    fun isValidNik(nik: String): Boolean {
        return KtpDataValidator.isValidNik(nik)
    }

    /**
     * Memeriksa apakah tanggal lahir sesuai dengan format `DD-MM-YYYY` dan merupakan tanggal kalender yang sah.
     */
    fun isValidTanggalLahir(tanggalLahir: String): Boolean {
        return KtpDataValidator.isValidTanggalLahir(tanggalLahir)
    }

    /**
     * Memeriksa apakah jenis kelamin memuat atau bernilai `'Laki-laki'` atau `'Perempuan'`.
     */
    fun isValidJenisKelamin(jenisKelamin: String): Boolean {
        return KtpDataValidator.isValidJenisKelamin(jenisKelamin)
    }

    /**
     * Schema JSON terstruktur sesuai spesifikasi Section C pada prompt.
     */
    val ktpJsonSchema: Schema = Schema.obj(
        properties = mapOf(
            "nik" to Schema.string(description = "16 digit NIK e-KTP Indonesia dalam bentuk string (pertahankan angka 0 di depan). Kosongkan jika tidak terbaca."),
            "nama_lengkap" to Schema.string(description = "Nama lengkap pemilik KTP sesuai label 'Nama'. Jangan pernah memasukkan nama Provinsi atau Kota ke sini."),
            "tempat_lahir" to Schema.string(description = "Tempat lahir sebelum tanda koma pada baris 'Tempat/Tgl Lahir'."),
            "tanggal_lahir" to Schema.string(description = "Tanggal lahir dengan format DD-MM-YYYY pada baris 'Tempat/Tgl Lahir'."),
            "jenis_kelamin" to Schema.string(description = "Hanya bernilai 'Laki-laki' atau 'Perempuan' (atau string kosong jika tidak terbaca)."),
            "golongan_darah" to Schema.string(description = "Golongan darah pada KTP (A, B, AB, O, atau -)."),
            "alamat" to Schema.string(description = "Alamat jalan, blok, nomor rumah, atau dusun sesuai baris 'Alamat'."),
            "rt" to Schema.string(description = "Nomor RT 3 digit terpisah dari baris 'RT/RW', contoh '003'."),
            "rw" to Schema.string(description = "Nomor RW 3 digit terpisah dari baris 'RT/RW', contoh '008'."),
            "kelurahan" to Schema.string(description = "Nama Kelurahan atau Desa sesuai baris 'Kel/Desa'."),
            "kecamatan" to Schema.string(description = "Nama Kecamatan sesuai baris 'Kecamatan'."),
            "kabupaten_kota" to Schema.string(description = "Nama Kabupaten atau Kota dari header atas KTP."),
            "provinsi" to Schema.string(description = "Nama Provinsi dari header paling atas KTP."),
            "agama" to Schema.string(description = "Agama sesuai baris 'Agama'."),
            "status_perkawinan" to Schema.string(description = "Status perkawinan sesuai baris 'Status Perkawinan'."),
            "pekerjaan" to Schema.string(description = "Pekerjaan sesuai baris 'Pekerjaan'."),
            "kewarganegaraan" to Schema.string(description = "Kewarganegaraan sesuai baris 'Kewarganegaraan' (WNI atau WNA)."),
            "berlaku_hingga" to Schema.string(description = "Masa berlaku KTP sesuai baris 'Berlaku Hingga' (SEUMUR HIDUP atau tanggal)."),
            "confidence" to Schema.obj(
                properties = mapOf(
                    "nik" to Schema.integer(description = "Tingkat keyakinan pembacaan NIK (0-100)."),
                    "nama_lengkap" to Schema.integer(description = "Tingkat keyakinan pembacaan Nama Lengkap (0-100)."),
                    "tempat_lahir" to Schema.integer(description = "Tingkat keyakinan pembacaan Tempat Lahir (0-100)."),
                    "tanggal_lahir" to Schema.integer(description = "Tingkat keyakinan pembacaan Tanggal Lahir (0-100)."),
                    "jenis_kelamin" to Schema.integer(description = "Tingkat keyakinan pembacaan Jenis Kelamin (0-100)."),
                    "alamat" to Schema.integer(description = "Tingkat keyakinan pembacaan Alamat (0-100).")
                )
            ),
            "status_pembacaan" to Schema.string(description = "'berhasil', 'perlu_verifikasi', atau 'gagal'.")
        )
    )

    const val KTP_EXTRACTION_PROMPT = """
        Anda adalah sistem AI OCR khusus pembaca KTP Elektronik (e-KTP) Republik Indonesia.
        Ekstrak informasi identitas pada gambar KTP ini berdasarkan label dan posisi resminya ke dalam struktur JSON yang diminta:
        - "provinsi": Baris paling atas pada header KTP (contoh: "JAWA TENGAH").
        - "kabupaten_kota": Baris kedua di bawah provinsi pada header KTP (contoh: "KOTA SURAKARTA").
          PENTING: Jangan pernah memasukkan teks PROVINSI atau KOTA/KABUPATEN ke dalam "nama_lengkap"!
        - "nik": String 16 digit angka (pertahankan angka nol di depan).
        - "nama_lengkap": Nama lengkap pemilik KTP sesuai baris berlabel "Nama".
        - "tempat_lahir" & "tanggal_lahir": Pisahkan tempat lahir dan tanggal lahir (format DD-MM-YYYY) dari baris "Tempat/Tgl Lahir".
        - "jenis_kelamin": Hanya boleh bernilai "Laki-laki" atau "Perempuan".
        - "golongan_darah": Golongan darah dari label "Gol. Darah" (jika tersedia).
        - "alamat": Nama jalan/dusun/nomor rumah dari baris "Alamat".
        - "rt" & "rw": Pisahkan nomor RT dan RW (masing-masing 3 digit, contoh "003" dan "008") dari baris "RT/RW".
        - "kelurahan": Nama Kelurahan/Desa dari baris "Kel/Desa" (jangan tertukar dengan Kecamatan).
        - "kecamatan": Nama Kecamatan dari baris "Kecamatan".
        - "agama", "status_perkawinan", "pekerjaan", "kewarganegaraan", "berlaku_hingga": Sesuai label masing-masing.
        - Jangan menebak data yang tidak terlihat. Jika suatu bagian tidak terbaca, kembalikan string kosong "" dan nilai confidence 0.
    """

    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ktpJsonSchema
                temperature = 0.0f
            }
        )
    }

    /**
     * Laporan diagnostik terakhir dari panggilan Gemini Vision API atau proses parsing JSON.
     */
    val lastDiagnosticReport: KtpGeminiDiagnosticReport?
        get() = KtpGeminiDiagnosticWrapper.lastReport

    /**
     * Mengirim gambar KTP (setelah melalui prapemrosesan auto-crop, koreksi rotasi/kemiringan,
     * peningkatan kontras & ketajaman) ke Gemini Vision dan mengembalikan objek `KtpData` tervalidasi.
     * Menggunakan `KtpGeminiDiagnosticWrapper` untuk mencatat raw response dan exception secara mendalam.
     */
    suspend fun extractKtpData(
        context: Context? = null,
        ktpBitmap: Bitmap,
        preprocessImage: Boolean = true
    ): KtpData {
        val diagnosticResult = KtpGeminiDiagnosticWrapper.executeWithDiagnostics(
            context = context,
            ktpBitmap = ktpBitmap,
            preprocessImage = preprocessImage
        )
        return diagnosticResult.ktpData
    }

    /**
     * Versi diagnostik dari `extractKtpData` yang mengembalikan `KtpGeminiDiagnosticResult`
     * (berisi `KtpData`, `KtpGeminiDiagnosticReport`, dan `Throwable` jika gagal).
     */
    suspend fun extractKtpDataWithDiagnostics(
        context: Context? = null,
        ktpBitmap: Bitmap,
        preprocessImage: Boolean = true
    ): KtpGeminiDiagnosticResult {
        return KtpGeminiDiagnosticWrapper.executeWithDiagnostics(
            context = context,
            ktpBitmap = ktpBitmap,
            preprocessImage = preprocessImage
        )
    }

    /**
     * Mengirim gambar KTP ke Gemini Vision dan langsung memetakan hasilnya ke `CustomerRegistrationFormState`.
     */
    suspend fun scanAndMapKtpToCustomerForm(
        context: Context? = null,
        ktpBitmap: Bitmap,
        currentForm: CustomerRegistrationFormState = CustomerRegistrationFormState(),
        allowLowConfidenceUnder50: Boolean = false,
        savedPhotoPath: String = "",
        preprocessImage: Boolean = true
    ): CustomerRegistrationFormState {
        val ktpData = extractKtpData(
            context = context,
            ktpBitmap = ktpBitmap,
            preprocessImage = preprocessImage
        )
        return mapKtpDataToCustomerForm(
            ktpData = ktpData,
            currentForm = currentForm,
            allowLowConfidenceUnder50 = allowLowConfidenceUnder50,
            savedPhotoPath = savedPhotoPath
        )
    }

    /**
     * Mem-parsing objek `GenerateContentResponse` dari Gemini Vision menjadi `KtpData`
     * dengan penangkapan data diagnostik lengkap (raw response text, candidate, finish reason).
     */
    fun parseGeminiResponse(response: GenerateContentResponse): KtpData {
        val (ktpData, report) = KtpGeminiDiagnosticWrapper.parseResponseWithDiagnostics(response)
        KtpGeminiDiagnosticWrapper.recordReport(report)
        return ktpData
    }

    /**
     * Mem-parsing string JSON hasil respons Gemini Vision menjadi objek `KtpData`
     * dan menerapkan seluruh aturan validasi otomatis e-KTP Indonesia (Section C & E).
     * Merekam setiap error parsing JSON ke dalam `KtpGeminiDiagnosticWrapper`.
     */
    fun parseJsonToKtpData(jsonString: String): KtpData {
        val (ktpData, report) = KtpGeminiDiagnosticWrapper.parseJsonWithDiagnostics(jsonString)
        KtpGeminiDiagnosticWrapper.recordReport(report)
        return ktpData
    }

    /**
     * Memetakan `KtpData` secara otomatis ke dalam `CustomerRegistrationFormState` berdasarkan
     * nama key JSON (Section D & E), serta memvalidasi `nik`, `tanggal_lahir`, dan `jenis_kelamin`
     * menggunakan `validateKtpData`.
     */
    fun mapKtpDataToCustomerForm(
        ktpData: KtpData,
        currentForm: CustomerRegistrationFormState = CustomerRegistrationFormState(),
        allowLowConfidenceUnder50: Boolean = false,
        savedPhotoPath: String = ""
    ): CustomerRegistrationFormState {
        val validation = validateKtpData(ktpData)
        val validatedOcrResult = ktpData.toKtpOcrResult(savedOriginalPhotoPath = savedPhotoPath)
        val jsonObj = JSONObject(validatedOcrResult.toStructuredJsonString())
        val confObj = jsonObj.optJSONObject("confidence")

        val yellowSet = mutableSetOf<String>()
        val blockedSet = mutableSetOf<String>()

        // Tandai field yang memiliki error flag pada hasil validasi ke dalam yellowSet
        validation.errorFlags.forEach { flag ->
            yellowSet.add(flag.jsonKey)
        }

        fun resolveFieldByJsonKey(
            jsonKey: String,
            confidenceKey: String?,
            fallbackCurrent: String
        ): String {
            val extracted = jsonObj.optString(jsonKey, "").trim()
            if (extracted.isBlank()) {
                return fallbackCurrent
            }

            val score = if (confidenceKey != null && confObj != null) {
                confObj.optInt(confidenceKey, 85)
            } else {
                validatedOcrResult.confidence.getScoreForKey(jsonKey)
            }

            if (score < CONFIDENCE_YELLOW_THRESHOLD) {
                yellowSet.add(jsonKey)
            }
            if (score < CONFIDENCE_AUTOFILL_MIN_THRESHOLD && !allowLowConfidenceUnder50) {
                blockedSet.add(jsonKey)
                return fallbackCurrent
            }
            return extracted
        }

        val mappedNik = resolveFieldByJsonKey("nik", "nik", currentForm.nik).take(16)
        if (mappedNik.isNotBlank() && !isValidNik(mappedNik)) {
            yellowSet.add("nik")
        }

        val mappedNama = resolveFieldByJsonKey("nama_lengkap", "nama_lengkap", currentForm.namaLengkap)
        val mappedTempatLahir = resolveFieldByJsonKey("tempat_lahir", "tempat_lahir", currentForm.tempatLahir)
        val mappedTanggalLahir = resolveFieldByJsonKey("tanggal_lahir", "tanggal_lahir", currentForm.tanggalLahir)
        val mappedJenisKelamin = resolveFieldByJsonKey("jenis_kelamin", "jenis_kelamin", currentForm.jenisKelamin)
        val mappedGolDarah = resolveFieldByJsonKey("golongan_darah", null, currentForm.golonganDarah)
        val mappedAlamat = resolveFieldByJsonKey("alamat", "alamat", currentForm.alamatLengkap)
        val mappedRt = resolveFieldByJsonKey("rt", "alamat", currentForm.rt)
        val mappedRw = resolveFieldByJsonKey("rw", "alamat", currentForm.rw)
        val mappedKelurahan = resolveFieldByJsonKey("kelurahan", "alamat", currentForm.kelurahanDesa)
        val mappedKecamatan = resolveFieldByJsonKey("kecamatan", "alamat", currentForm.kecamatan)
        val mappedKabKota = resolveFieldByJsonKey("kabupaten_kota", null, currentForm.kabupatenKota)
        val mappedProvinsi = resolveFieldByJsonKey("provinsi", null, currentForm.provinsi)
        val mappedAgama = resolveFieldByJsonKey("agama", null, currentForm.agama)
        val mappedStatusKawin = resolveFieldByJsonKey("status_perkawinan", null, currentForm.statusPerkawinan)
        val mappedPekerjaan = resolveFieldByJsonKey("pekerjaan", null, currentForm.pekerjaan)
        val mappedKewarganegaraan = resolveFieldByJsonKey("kewarganegaraan", null, currentForm.kewarganegaraan)
        val mappedBerlakuHingga = resolveFieldByJsonKey("berlaku_hingga", null, currentForm.berlakuHingga)

        val combinedWarnings = (validatedOcrResult.validationWarnings + validation.errorMessages.values).distinct()

        return currentForm.copy(
            nik = mappedNik,
            namaLengkap = mappedNama,
            tempatLahir = mappedTempatLahir,
            tanggalLahir = mappedTanggalLahir,
            jenisKelamin = mappedJenisKelamin,
            golonganDarah = mappedGolDarah,
            alamatLengkap = mappedAlamat,
            rt = mappedRt,
            rw = mappedRw,
            kelurahanDesa = mappedKelurahan,
            kecamatan = mappedKecamatan,
            kabupatenKota = mappedKabKota,
            provinsi = mappedProvinsi,
            agama = mappedAgama,
            statusPerkawinan = mappedStatusKawin,
            pekerjaan = mappedPekerjaan,
            kewarganegaraan = mappedKewarganegaraan.ifBlank { "WNI" },
            berlakuHingga = mappedBerlakuHingga.ifBlank { "SEUMUR HIDUP" },
            fotoKtpDepanUri = savedPhotoPath.ifBlank { currentForm.fotoKtpDepanUri },
            yellowVerificationFields = yellowSet,
            blockedLowConfidenceFields = blockedSet,
            unreadableFields = validatedOcrResult.unreadableFields,
            validationWarnings = combinedWarnings,
            statusPembacaan = validatedOcrResult.statusPembacaan
        )
    }

    /**
     * Mem-parsing JSON string hasil Gemini Vision dan langsung memetakannya ke `CustomerRegistrationFormState`.
     */
    fun parseAndMapToCustomerForm(
        jsonString: String,
        currentForm: CustomerRegistrationFormState = CustomerRegistrationFormState(),
        allowLowConfidenceUnder50: Boolean = false,
        savedPhotoPath: String = ""
    ): CustomerRegistrationFormState {
        val ktpData = parseJsonToKtpData(jsonString)
        return mapKtpDataToCustomerForm(
            ktpData = ktpData,
            currentForm = currentForm,
            allowLowConfidenceUnder50 = allowLowConfidenceUnder50,
            savedPhotoPath = savedPhotoPath
        )
    }

    /**
     * Mengonversi `KtpOcrResult` yang telah tervalidasi menjadi `KtpData`.
     */
    fun fromKtpOcrResult(result: KtpOcrResult): KtpData {
        val clean = KtpOcrScanner.validateAndSanitizeKtpResult(result)
        return KtpData(
            nik = clean.nik,
            namaLengkap = clean.namaLengkap,
            tempatLahir = clean.tempatLahir,
            tanggalLahir = clean.tanggalLahir,
            jenisKelamin = clean.jenisKelamin,
            golonganDarah = clean.golonganDarah,
            alamat = clean.alamat,
            rt = clean.rt,
            rw = clean.rw,
            kelurahan = clean.kelurahanDesa,
            kecamatan = clean.kecamatan,
            kabupatenKota = clean.kabupatenKota,
            provinsi = clean.provinsi,
            agama = clean.agama,
            statusPerkawinan = clean.statusPerkawinan,
            pekerjaan = clean.pekerjaan,
            kewarganegaraan = clean.kewarganegaraan,
            berlakuHingga = clean.berlakuHingga,
            confidence = KtpConfidence(
                nik = clean.confidence.nik,
                namaLengkap = clean.confidence.namaLengkap,
                tempatLahir = clean.confidence.tempatLahir,
                tanggalLahir = clean.confidence.tanggalLahir,
                jenisKelamin = clean.confidence.jenisKelamin,
                alamat = clean.confidence.alamat
            ),
            statusPembacaan = clean.statusPembacaan
        )
    }
}
