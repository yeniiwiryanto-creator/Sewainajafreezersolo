package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import com.example.data.local.CustomerEntity
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema

/**
 * Representasi state formulir registrasi pelanggan yang terpetakan langsung dari JSON key hasil
 * pembacaan AI OCR KTP Indonesia (Section C, D & E).
 */
data class CustomerRegistrationFormState(
    val nik: String = "",
    val namaLengkap: String = "",
    val tempatLahir: String = "",
    val tanggalLahir: String = "",
    val jenisKelamin: String = "",
    val golonganDarah: String = "",
    val alamatLengkap: String = "",
    val rt: String = "",
    val rw: String = "",
    val kelurahanDesa: String = "",
    val kecamatan: String = "",
    val kabupatenKota: String = "",
    val provinsi: String = "",
    val agama: String = "",
    val statusPerkawinan: String = "",
    val pekerjaan: String = "",
    val kewarganegaraan: String = "WNI",
    val berlakuHingga: String = "SEUMUR HIDUP",
    val fotoKtpDepanUri: String = "",
    val yellowVerificationFields: Set<String> = emptySet(),
    val blockedLowConfidenceFields: Set<String> = emptySet(),
    val unreadableFields: List<String> = emptyList(),
    val validationWarnings: List<String> = emptyList(),
    val statusPembacaan: String = "berhasil"
) {
    /**
     * Mengonversi state formulir yang telah terisi ke `CustomerEntity` untuk disimpan ke database Room
     * tanpa mengubah struktur tabel pelanggan yang sudah berjalan.
     */
    fun toCustomerEntity(
        existingCustomer: CustomerEntity? = null,
        nomorHp: String = existingCustomer?.nomorHp ?: "",
        tanggalRegistrasi: String = existingCustomer?.tanggalRegistrasi ?: "",
        catatan: String = existingCustomer?.catatan ?: ""
    ): CustomerEntity {
        val combinedRtRw = if (rt.isNotBlank() || rw.isNotBlank()) {
            "${rt.ifBlank { "000" }}/${rw.ifBlank { "000" }}"
        } else {
            existingCustomer?.rtRw ?: ""
        }
        return CustomerEntity(
            id = existingCustomer?.id ?: 0L,
            kodePelanggan = existingCustomer?.kodePelanggan ?: "",
            namaLengkap = namaLengkap.ifBlank { existingCustomer?.namaLengkap ?: "" },
            nik = nik.ifBlank { existingCustomer?.nik ?: "" },
            nomorHp = nomorHp,
            alamatLengkap = alamatLengkap.ifBlank { existingCustomer?.alamatLengkap ?: "" },
            jenisKelamin = jenisKelamin.ifBlank { existingCustomer?.jenisKelamin ?: "Laki-laki" },
            fotoKtpDepanUri = fotoKtpDepanUri.ifBlank { existingCustomer?.fotoKtpDepanUri ?: "" },
            fotoKtpBelakangUri = existingCustomer?.fotoKtpBelakangUri ?: "",
            catatan = catatan,
            tanggalRegistrasi = tanggalRegistrasi,
            tempatLahir = tempatLahir.ifBlank { existingCustomer?.tempatLahir ?: "" },
            tanggalLahir = tanggalLahir.ifBlank { existingCustomer?.tanggalLahir ?: "" },
            rtRw = combinedRtRw,
            rt = rt.ifBlank { existingCustomer?.rt ?: "" },
            rw = rw.ifBlank { existingCustomer?.rw ?: "" },
            kelurahanDesa = kelurahanDesa.ifBlank { existingCustomer?.kelurahanDesa ?: "" },
            kecamatan = kecamatan.ifBlank { existingCustomer?.kecamatan ?: "" },
            kabupatenKota = kabupatenKota.ifBlank { existingCustomer?.kabupatenKota ?: "Kota Surakarta" },
            agama = agama.ifBlank { existingCustomer?.agama ?: "" },
            statusPerkawinan = statusPerkawinan.ifBlank { existingCustomer?.statusPerkawinan ?: "" },
            pekerjaan = pekerjaan.ifBlank { existingCustomer?.pekerjaan ?: "" },
            kewarganegaraan = kewarganegaraan.ifBlank { existingCustomer?.kewarganegaraan ?: "WNI" },
            berlakuHingga = berlakuHingga.ifBlank { existingCustomer?.berlakuHingga ?: "SEUMUR HIDUP" },
            persetujuanDataKtpLokasi = existingCustomer?.persetujuanDataKtpLokasi ?: true,
            namaLokasiDefault = existingCustomer?.namaLokasiDefault ?: "",
            lokasiPenggunaanDefault = existingCustomer?.lokasiPenggunaanDefault ?: "",
            rtRwLokasiDefault = existingCustomer?.rtRwLokasiDefault ?: "",
            kelurahanLokasiDefault = existingCustomer?.kelurahanLokasiDefault ?: "",
            kecamatanLokasiDefault = existingCustomer?.kecamatanLokasiDefault ?: "",
            kabupatenKotaLokasiDefault = existingCustomer?.kabupatenKotaLokasiDefault ?: "Kota Surakarta",
            latitudeDefault = existingCustomer?.latitudeDefault,
            longitudeDefault = existingCustomer?.longitudeDefault,
            linkGoogleMapsDefault = existingCustomer?.linkGoogleMapsDefault ?: ""
        )
    }
}

/**
 * Kelas adapter yang menghubungkan `KtpGeminiParser` dengan `KtpOcrResult` dan `CustomerRegistrationFormState`.
 */
class KtpGeminiVisionParser {

    companion object {
        const val CONFIDENCE_YELLOW_THRESHOLD = KtpGeminiParser.CONFIDENCE_YELLOW_THRESHOLD
        const val CONFIDENCE_AUTOFILL_MIN_THRESHOLD = KtpGeminiParser.CONFIDENCE_AUTOFILL_MIN_THRESHOLD

        val requestedKtpResponseSchema: Schema = KtpGeminiParser.ktpJsonSchema
        const val INDONESIAN_KTP_SYSTEM_PROMPT = KtpGeminiParser.KTP_EXTRACTION_PROMPT
    }

    /**
     * Laporan diagnostik eksekusi Gemini Vision terakhir dari wrapper.
     */
    val lastDiagnosticReport: KtpGeminiDiagnosticReport?
        get() = KtpGeminiDiagnosticWrapper.lastReport

    /**
     * Memanggil `KtpGeminiParser.extractKtpData` untuk mengekstrak data e-KTP dari gambar yang telah diproses
     * dan mengembalikan `KtpOcrResult`.
     */
    suspend fun analyzeKtpBitmapWithGeminiVision(
        context: Context,
        processedKtpBitmap: Bitmap,
        savedOriginalPhotoPath: String = "",
        qualityReport: KtpImageQualityReport = KtpImageQualityReport()
    ): KtpOcrResult? {
        val ktpData = KtpGeminiParser.extractKtpData(
            context = context,
            ktpBitmap = processedKtpBitmap,
            preprocessImage = false
        )
        if (ktpData.statusPembacaan == "gagal" && ktpData.nik.isBlank() && ktpData.namaLengkap.isBlank()) {
            return null
        }
        return ktpData.toKtpOcrResult(
            savedOriginalPhotoPath = savedOriginalPhotoPath,
            qualityReport = qualityReport
        )
    }

    /**
     * Menangani objek `GenerateContentResponse` dari Gemini Vision API melalui `KtpGeminiParser`.
     */
    fun handleGeminiVisionResponse(
        response: GenerateContentResponse,
        savedPhotoPath: String = "",
        qualityReport: KtpImageQualityReport = KtpImageQualityReport()
    ): KtpOcrResult {
        val ktpData = KtpGeminiParser.parseGeminiResponse(response)
        if (ktpData.statusPembacaan == "gagal" && ktpData.nik.isBlank() && ktpData.namaLengkap.isBlank()) {
            return KtpOcrResult(
                statusPembacaan = "gagal",
                savedOriginalPhotoPath = savedPhotoPath,
                qualityReport = qualityReport,
                unreadableFields = listOf("NIK", "Nama Lengkap", "Tempat Lahir", "Tanggal Lahir", "Alamat")
            )
        }
        return ktpData.toKtpOcrResult(
            savedOriginalPhotoPath = savedPhotoPath,
            qualityReport = qualityReport
        )
    }

    /**
     * Mem-parsing JSON string dari Gemini Vision ke dalam struktur `KtpOcrResult` melalui `KtpGeminiParser`.
     */
    fun parseGeminiJsonResponse(
        rawJsonString: String,
        savedPhotoPath: String = "",
        qualityReport: KtpImageQualityReport = KtpImageQualityReport()
    ): KtpOcrResult {
        return KtpGeminiParser.parseJsonToKtpData(rawJsonString).toKtpOcrResult(
            savedOriginalPhotoPath = savedPhotoPath,
            qualityReport = qualityReport
        )
    }

    /**
     * Membangun JSON String yang patuh 100% pada skema wajib Section C dari hasil ekstraksi.
     */
    fun formatToRequestedJsonSchema(result: KtpOcrResult): String {
        return KtpGeminiParser.fromKtpOcrResult(result).toJsonString()
    }

    /**
     * Memetakan data hasil pembacaan AI (`KtpOcrResult`) ke dalam `CustomerRegistrationFormState`
     * menggunakan `KtpGeminiParser.mapKtpDataToCustomerForm`.
     */
    fun mapToCustomerRegistrationForm(
        ocrResult: KtpOcrResult,
        currentForm: CustomerRegistrationFormState = CustomerRegistrationFormState(),
        allowLowConfidenceUnder50: Boolean = false
    ): CustomerRegistrationFormState {
        val ktpData = KtpGeminiParser.fromKtpOcrResult(ocrResult)
        return KtpGeminiParser.mapKtpDataToCustomerForm(
            ktpData = ktpData,
            currentForm = currentForm,
            allowLowConfidenceUnder50 = allowLowConfidenceUnder50,
            savedPhotoPath = ocrResult.savedOriginalPhotoPath
        )
    }

    /**
     * Mem-parsing JSON string secara langsung dan memetakannya ke `CustomerRegistrationFormState`
     * menggunakan `KtpGeminiParser.parseAndMapToCustomerForm`.
     */
    fun parseAndMapJsonToCustomerForm(
        rawJsonString: String,
        currentForm: CustomerRegistrationFormState = CustomerRegistrationFormState(),
        allowLowConfidenceUnder50: Boolean = false,
        savedPhotoPath: String = ""
    ): CustomerRegistrationFormState {
        return KtpGeminiParser.parseAndMapToCustomerForm(
            jsonString = rawJsonString,
            currentForm = currentForm,
            allowLowConfidenceUnder50 = allowLowConfidenceUnder50,
            savedPhotoPath = savedPhotoPath
        )
    }
}
