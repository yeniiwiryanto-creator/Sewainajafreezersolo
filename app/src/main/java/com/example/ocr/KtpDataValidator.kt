package com.example.ocr

/**
 * Kode error deskriptif untuk validasi atribut e-KTP Indonesia.
 */
enum class KtpErrorCode(
    val code: String,
    val field: String,
    val message: String
) {
    ERR_NIK_EMPTY(
        code = "ERR_NIK_EMPTY",
        field = "nik",
        message = "NIK tidak boleh kosong."
    ),
    ERR_NIK_LENGTH(
        code = "ERR_NIK_LENGTH",
        field = "nik",
        message = "NIK harus terdiri dari tepat 16 digit angka (panjang saat ini tidak sesuai)."
    ),
    ERR_NIK_NON_DIGIT(
        code = "ERR_NIK_NON_DIGIT",
        field = "nik",
        message = "NIK hanya boleh berisi karakter angka (0-9)."
    ),
    ERR_TANGGAL_LAHIR_EMPTY(
        code = "ERR_TANGGAL_LAHIR_EMPTY",
        field = "tanggal_lahir",
        message = "Tanggal lahir tidak boleh kosong."
    ),
    ERR_TANGGAL_LAHIR_FORMAT(
        code = "ERR_TANGGAL_LAHIR_FORMAT",
        field = "tanggal_lahir",
        message = "Format tanggal lahir harus DD-MM-YYYY (contoh: 17-08-1945)."
    ),
    ERR_TANGGAL_LAHIR_INVALID_DATE(
        code = "ERR_TANGGAL_LAHIR_INVALID_DATE",
        field = "tanggal_lahir",
        message = "Tanggal lahir bukan tanggal kalender yang valid (hari, bulan, atau tahun tidak sesuai)."
    ),
    ERR_JENIS_KELAMIN_EMPTY(
        code = "ERR_JENIS_KELAMIN_EMPTY",
        field = "jenis_kelamin",
        message = "Jenis kelamin tidak boleh kosong."
    ),
    ERR_JENIS_KELAMIN_INVALID(
        code = "ERR_JENIS_KELAMIN_INVALID",
        field = "jenis_kelamin",
        message = "Jenis kelamin harus memuat atau bernilai 'Laki-laki' atau 'Perempuan'."
    )
}

/**
 * Laporan hasil validasi data KTP yang memuat status keabsahan per kolom,
 * daftar kode error deskriptif, dan pesan kesalahan terstruktur.
 */
data class KtpDataValidationResult(
    val isValid: Boolean,
    val isNikValid: Boolean,
    val isTanggalLahirValid: Boolean,
    val isJenisKelaminValid: Boolean,
    val errorCodes: List<KtpErrorCode> = emptyList(),
    val errorMessages: List<String> = emptyList(),
    val errorsByField: Map<String, List<KtpErrorCode>> = emptyMap()
) {
    val hasErrors: Boolean get() = !isValid

    fun hasError(field: String): Boolean = errorsByField[field]?.isNotEmpty() == true

    fun getFirstErrorCode(field: String): KtpErrorCode? = errorsByField[field]?.firstOrNull()

    fun getFirstErrorMessage(field: String): String? = errorsByField[field]?.firstOrNull()?.message

    fun toLegacyKtpValidationResult(): KtpValidationResult {
        val flags = mutableSetOf<KtpFieldErrorFlag>()
        val messages = linkedMapOf<String, String>()

        if (!isNikValid) {
            flags.add(KtpFieldErrorFlag.INVALID_NIK)
            messages["nik"] = getFirstErrorMessage("nik") ?: KtpFieldErrorFlag.INVALID_NIK.message
        }
        if (!isTanggalLahirValid) {
            flags.add(KtpFieldErrorFlag.INVALID_TANGGAL_LAHIR)
            messages["tanggal_lahir"] = getFirstErrorMessage("tanggal_lahir") ?: KtpFieldErrorFlag.INVALID_TANGGAL_LAHIR.message
        }
        if (!isJenisKelaminValid) {
            flags.add(KtpFieldErrorFlag.INVALID_JENIS_KELAMIN)
            messages["jenis_kelamin"] = getFirstErrorMessage("jenis_kelamin") ?: KtpFieldErrorFlag.INVALID_JENIS_KELAMIN.message
        }

        return KtpValidationResult(
            isNikValid = isNikValid,
            isTanggalLahirValid = isTanggalLahirValid,
            isJenisKelaminValid = isJenisKelaminValid,
            invalidNikError = !isNikValid,
            invalidTanggalLahirError = !isTanggalLahirValid,
            invalidJenisKelaminError = !isJenisKelaminValid,
            errorFlags = flags,
            fieldErrors = mapOf(
                "nik" to !isNikValid,
                "tanggal_lahir" to !isTanggalLahirValid,
                "jenis_kelamin" to !isJenisKelaminValid
            ),
            errorMessages = messages
        )
    }
}

/**
 * Helper class `KtpDataValidator` untuk memvalidasi atribut e-KTP Indonesia:
 * 1. Panjang dan karakter NIK (wajib tepat 16 digit angka, mempertahankan angka 0 di depan).
 * 2. Format tanggal lahir (wajib format DD-MM-YYYY dan tanggal kalender valid).
 * 3. Nilai jenis kelamin (wajib memuat atau bernilai 'Laki-laki' atau 'Perempuan').
 *
 * Mengembalikan kode error deskriptif (`KtpErrorCode`) untuk setiap kesalahan yang ditemukan.
 */
class KtpDataValidator {

    companion object {
        const val NIK_REQUIRED_LENGTH = 16
        private val NIK_REGEX = Regex("""^\d{16}$""")
        private val DD_MM_YYYY_REGEX = Regex("""^(\d{2})-(\d{2})-(\d{4})$""")

        /**
         * Memvalidasi objek `KtpData` lengkap.
         */
        fun validate(ktpData: KtpData): KtpDataValidationResult {
            return validate(
                nik = ktpData.nik,
                tanggalLahir = ktpData.tanggalLahir,
                jenisKelamin = ktpData.jenisKelamin
            )
        }

        /**
         * Memvalidasi tiga field inti KTP: NIK, tanggal lahir, dan jenis kelamin.
         */
        fun validate(
            nik: String,
            tanggalLahir: String,
            jenisKelamin: String
        ): KtpDataValidationResult {
            val nikErrors = validateNik(nik)
            val tglErrors = validateTanggalLahir(tanggalLahir)
            val jkErrors = validateJenisKelamin(jenisKelamin)

            val allErrors = mutableListOf<KtpErrorCode>().apply {
                addAll(nikErrors)
                addAll(tglErrors)
                addAll(jkErrors)
            }

            val isNikValid = nikErrors.isEmpty()
            val isTanggalLahirValid = tglErrors.isEmpty()
            val isJenisKelaminValid = jkErrors.isEmpty()
            val isValid = isNikValid && isTanggalLahirValid && isJenisKelaminValid

            val errorsByField = linkedMapOf<String, List<KtpErrorCode>>()
            if (nikErrors.isNotEmpty()) errorsByField["nik"] = nikErrors
            if (tglErrors.isNotEmpty()) errorsByField["tanggal_lahir"] = tglErrors
            if (jkErrors.isNotEmpty()) errorsByField["jenis_kelamin"] = jkErrors

            return KtpDataValidationResult(
                isValid = isValid,
                isNikValid = isNikValid,
                isTanggalLahirValid = isTanggalLahirValid,
                isJenisKelaminValid = isJenisKelaminValid,
                errorCodes = allErrors,
                errorMessages = allErrors.map { it.message },
                errorsByField = errorsByField
            )
        }

        /**
         * Memeriksa `nik`:
         * - Tidak boleh kosong.
         * - Harus tepat 16 karakter.
         * - Harus hanya berisi angka (0-9).
         */
        fun validateNik(nik: String): List<KtpErrorCode> {
            val trimmed = nik.trim()
            val errors = mutableListOf<KtpErrorCode>()

            if (trimmed.isEmpty()) {
                errors.add(KtpErrorCode.ERR_NIK_EMPTY)
                return errors
            }

            val isOnlyDigits = trimmed.all { it.isDigit() }
            if (!isOnlyDigits) {
                errors.add(KtpErrorCode.ERR_NIK_NON_DIGIT)
            }

            if (trimmed.length != NIK_REQUIRED_LENGTH) {
                errors.add(KtpErrorCode.ERR_NIK_LENGTH)
            }

            return errors
        }

        /**
         * Memeriksa apakah NIK valid (tepat 16 digit angka).
         */
        fun isValidNik(nik: String): Boolean {
            return validateNik(nik).isEmpty()
        }

        /**
         * Memeriksa format dan validitas kalender `tanggal_lahir`:
         * - Tidak boleh kosong.
         * - Harus sesuai format `DD-MM-YYYY`.
         * - Hari, bulan, dan tahun harus merupakan tanggal kalender yang valid (termasuk tahun kabisat).
         */
        fun validateTanggalLahir(tanggalLahir: String): List<KtpErrorCode> {
            val trimmed = tanggalLahir.trim()
            val errors = mutableListOf<KtpErrorCode>()

            if (trimmed.isEmpty()) {
                errors.add(KtpErrorCode.ERR_TANGGAL_LAHIR_EMPTY)
                return errors
            }

            val match = DD_MM_YYYY_REGEX.matchEntire(trimmed)
            if (match == null) {
                errors.add(KtpErrorCode.ERR_TANGGAL_LAHIR_FORMAT)
                return errors
            }

            val day = match.groupValues[1].toIntOrNull() ?: 0
            val month = match.groupValues[2].toIntOrNull() ?: 0
            val year = match.groupValues[3].toIntOrNull() ?: 0

            if (month !in 1..12 || year !in 1900..2100) {
                errors.add(KtpErrorCode.ERR_TANGGAL_LAHIR_INVALID_DATE)
                return errors
            }

            val maxDay = when (month) {
                2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }

            if (day !in 1..maxDay) {
                errors.add(KtpErrorCode.ERR_TANGGAL_LAHIR_INVALID_DATE)
            }

            return errors
        }

        /**
         * Memeriksa apakah format tanggal lahir valid.
         */
        fun isValidTanggalLahir(tanggalLahir: String): Boolean {
            return validateTanggalLahir(tanggalLahir).isEmpty()
        }

        /**
         * Memeriksa apakah `jenis_kelamin` memuat atau bernilai 'Laki-laki' atau 'Perempuan'.
         */
        fun validateJenisKelamin(jenisKelamin: String): List<KtpErrorCode> {
            val trimmed = jenisKelamin.trim()
            val errors = mutableListOf<KtpErrorCode>()

            if (trimmed.isEmpty()) {
                errors.add(KtpErrorCode.ERR_JENIS_KELAMIN_EMPTY)
                return errors
            }

            val containsLakiLaki = trimmed.contains("Laki-laki", ignoreCase = true) ||
                trimmed.equals("Laki", ignoreCase = true)
            val containsPerempuan = trimmed.contains("Perempuan", ignoreCase = true)

            if (!containsLakiLaki && !containsPerempuan) {
                errors.add(KtpErrorCode.ERR_JENIS_KELAMIN_INVALID)
            }

            return errors
        }

        /**
         * Memeriksa apakah jenis kelamin valid ('Laki-laki' atau 'Perempuan').
         */
        fun isValidJenisKelamin(jenisKelamin: String): Boolean {
            return validateJenisKelamin(jenisKelamin).isEmpty()
        }

        /**
         * Menormalkan string jenis kelamin menjadi 'Laki-laki' atau 'Perempuan' jika valid.
         */
        fun normalizeJenisKelamin(jenisKelamin: String): String {
            val trimmed = jenisKelamin.trim()
            return when {
                trimmed.contains("Perempuan", ignoreCase = true) -> "Perempuan"
                trimmed.contains("Laki-laki", ignoreCase = true) || trimmed.equals("Laki", ignoreCase = true) -> "Laki-laki"
                else -> trimmed
            }
        }
    }

    // Instance methods for callers instantiating KtpDataValidator()
    fun validate(ktpData: KtpData): KtpDataValidationResult = Companion.validate(ktpData)

    fun validate(nik: String, tanggalLahir: String, jenisKelamin: String): KtpDataValidationResult =
        Companion.validate(nik, tanggalLahir, jenisKelamin)

    fun validateNik(nik: String): List<KtpErrorCode> = Companion.validateNik(nik)

    fun validateTanggalLahir(tanggalLahir: String): List<KtpErrorCode> = Companion.validateTanggalLahir(tanggalLahir)

    fun validateJenisKelamin(jenisKelamin: String): List<KtpErrorCode> = Companion.validateJenisKelamin(jenisKelamin)

    fun isValidNik(nik: String): Boolean = Companion.isValidNik(nik)

    fun isValidTanggalLahir(tanggalLahir: String): Boolean = Companion.isValidTanggalLahir(tanggalLahir)

    fun isValidJenisKelamin(jenisKelamin: String): Boolean = Companion.isValidJenisKelamin(jenisKelamin)

    fun normalizeJenisKelamin(jenisKelamin: String): String = Companion.normalizeJenisKelamin(jenisKelamin)
}
