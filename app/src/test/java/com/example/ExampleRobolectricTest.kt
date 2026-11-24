package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.ocr.KtpConfidenceScores
import com.example.ocr.KtpImageProcessor
import com.example.ocr.KtpOcrResult
import com.example.ocr.KtpOcrScanner
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SEWAin Aja Freezer SOC", appName)
    }

    @Test
    fun `section C and D - structured JSON format and JSON key auto-fill mapping work accurately`() {
        val sampleJson = """
            {
              "nik": "0372041807920007",
              "nama_lengkap": "HENDRA WIJAYA PUTRA",
              "tempat_lahir": "SURAKARTA",
              "tanggal_lahir": "18-07-1992",
              "jenis_kelamin": "Laki-laki",
              "golongan_darah": "O",
              "alamat": "JL. SLAMET RIYADI NO. 158",
              "rt": "003",
              "rw": "008",
              "kelurahan": "TIMURAN",
              "kecamatan": "BANJARSARI",
              "kabupaten_kota": "KOTA SURAKARTA",
              "provinsi": "JAWA TENGAH",
              "agama": "ISLAM",
              "status_perkawinan": "KAWIN",
              "pekerjaan": "WIRASWASTA",
              "kewarganegaraan": "WNI",
              "berlaku_hingga": "SEUMUR HIDUP",
              "confidence": {
                "nik": 96,
                "nama_lengkap": 94,
                "tempat_lahir": 92,
                "tanggal_lahir": 95,
                "jenis_kelamin": 98,
                "alamat": 91
              },
              "status_pembacaan": "berhasil"
            }
        """.trimIndent()

        val parsed = KtpOcrScanner.parseKtpStructuredJson(sampleJson, "/tmp/ktp_camera.jpg")

        // Pastikan angka nol di depan NIK tetap utuh sebagai string 16 digit
        assertEquals("0372041807920007", parsed.nik)
        assertEquals(16, parsed.nik.length)
        assertTrue(KtpOcrScanner.isValid16DigitNik(parsed.nik))

        // Pastikan seluruh 18 field terisi sesuai key JSON
        val formMap = parsed.toCustomerFormAutoFillMap(includeLowConfidenceUnder50 = false)
        assertEquals("0372041807920007", formMap["nik"])
        assertEquals("HENDRA WIJAYA PUTRA", formMap["nama_lengkap"])
        assertEquals("SURAKARTA", formMap["tempat_lahir"])
        assertEquals("18-07-1992", formMap["tanggal_lahir"])
        assertEquals("Laki-laki", formMap["jenis_kelamin"])
        assertEquals("O", formMap["golongan_darah"])
        assertEquals("JL. SLAMET RIYADI NO. 158", formMap["alamat"])
        assertEquals("003", formMap["rt"])
        assertEquals("008", formMap["rw"])
        assertEquals("TIMURAN", formMap["kelurahan"])
        assertEquals("BANJARSARI", formMap["kecamatan"])
        assertEquals("KOTA SURAKARTA", formMap["kabupaten_kota"])
        assertEquals("JAWA TENGAH", formMap["provinsi"])
        assertEquals("ISLAM", formMap["agama"])
        assertEquals("KAWIN", formMap["status_perkawinan"])
        assertEquals("WIRASWASTA", formMap["pekerjaan"])
        assertEquals("WNI", formMap["kewarganegaraan"])
        assertEquals("SEUMUR HIDUP", formMap["berlaku_hingga"])

        // Pastikan serialisasi kembali ke JSON menghasilkan struktur Section C yang lengkap
        val exportedJson = JSONObject(parsed.toStructuredJsonString())
        assertEquals("0372041807920007", exportedJson.getString("nik"))
        assertEquals("JAWA TENGAH", exportedJson.getString("provinsi"))
        assertEquals("O", exportedJson.getString("golongan_darah"))
        assertEquals("berhasil", exportedJson.getString("status_pembacaan"))
        assertTrue(exportedJson.getJSONObject("confidence").getInt("nik") >= 80)
    }

    @Test
    fun `section E - validation prevents province in name, swaps, and enforces confidence thresholds 80 and 50`() {
        // Simulasikan bug klasik: AI/OCR salah memasukkan "PROVINSI JAWA TENGAH" ke nama_lengkap
        // dan memberikan confidence < 80 pada tempat_lahir (65) serta confidence < 50 pada alamat (35)
        val buggyResult = KtpOcrResult(
            nik = "3372041807920007",
            namaLengkap = "PROVINSI JAWA TENGAH",
            tempatLahir = "SURAKARTA",
            tanggalLahir = "18/07/1992",
            jenisKelamin = "PRIA",
            alamat = "JL. MERDEKA 10",
            rt = "4",
            rw = "9",
            kelurahanDesa = "LAWEYAN",
            kecamatan = "KEL. PAJANG",
            confidence = KtpConfidenceScores(
                nik = 95,
                namaLengkap = 70,
                tempatLahir = 65, // < 80 -> Kuning (Perlu Verifikasi), tapi >= 50 -> tetap di-autofill
                tanggalLahir = 90,
                jenisKelamin = 92,
                alamat = 35 // < 50 -> JANGAN di-autofill otomatis tanpa verifikasi!
            )
        )

        val validated = KtpOcrScanner.validateAndSanitizeKtpResult(buggyResult)

        // 1. "PROVINSI JAWA TENGAH" harus dipindahkan ke provinsi dan dihapus dari namaLengkap!
        assertEquals("", validated.namaLengkap)
        assertEquals("JAWA TENGAH", validated.provinsi)

        // 2. Tanggal lahir dinormalisasi ke DD-MM-YYYY
        assertEquals("18-07-1992", validated.tanggalLahir)
        assertTrue(KtpOcrScanner.isValidBirthDate(validated.tanggalLahir))

        // 3. Jenis kelamin dinormalisasi ke "Laki-laki"
        assertEquals("Laki-laki", validated.jenisKelamin)

        // 4. RT dan RW terpisah 3 digit
        assertEquals("004", validated.rt)
        assertEquals("009", validated.rw)

        // 5. Confidence < 80 (tempat_lahir = 65) ditandai kuning (needsYellowVerification == true)
        assertTrue(validated.needsYellowVerification("tempat_lahir"))
        assertFalse(validated.isBelowAutoFillThreshold("tempat_lahir"))

        // 6. Confidence < 50 (alamat = 35) TIDAK boleh diisi otomatis tanpa verifikasi
        assertTrue(validated.isBelowAutoFillThreshold("alamat"))
        val autoFillMap = validated.toCustomerFormAutoFillMap(includeLowConfidenceUnder50 = false)
        assertEquals("SURAKARTA", autoFillMap["tempat_lahir"]) // 65 >= 50 -> terisi + kuning
        assertEquals("", autoFillMap["alamat"]) // 35 < 50 -> dicegah agar tidak auto-fill otomatis!
    }

    @Test
    fun `section B and G - disordered two-column OCR text never swaps province into customer name`() {
        // Simulasikan hasil OCR mentah ketika label di kolom kiri dan nilai di kolom kanan terpisah
        val disorderedRawOcr = """
            PROVINSI JAWA TENGAH
            KOTA SURAKARTA
            NIK
            Nama
            Tempat/Tgl Lahir
            Jenis Kelamin
            Alamat
            RT/RW
            Kel/Desa
            Kecamatan
            : 3372011405880002
            : SITI NURHALIZA PUTRI
            : SURAKARTA, 14-05-1988
            : PEREMPUAN Gol. Darah : B
            : JL. SLAMET RIYADI NO. 88
            : 002/005
            : KAMPUNG BARU
            : PASAR KLIWON
            Agama : ISLAM
            Status Perkawinan : KAWIN
            Pekerjaan : WIRASWASTA
            Kewarganegaraan : WNI
            Berlaku Hingga : SEUMUR HIDUP
        """.trimIndent()

        val result = KtpOcrScanner.parseIndonesianKtpText(disorderedRawOcr)

        assertEquals("3372011405880002", result.nik)
        assertEquals("JAWA TENGAH", result.provinsi)
        assertEquals("KOTA SURAKARTA", result.kabupatenKota)
        assertEquals("SITI NURHALIZA PUTRI", result.namaLengkap)
        assertEquals("SURAKARTA", result.tempatLahir)
        assertEquals("14-05-1988", result.tanggalLahir)
        assertEquals("Perempuan", result.jenisKelamin)
        assertEquals("B", result.golonganDarah)
        assertEquals("JL. SLAMET RIYADI NO. 88", result.alamat)
        assertEquals("002", result.rt)
        assertEquals("005", result.rw)
        assertEquals("KAMPUNG BARU", result.kelurahanDesa)
        assertEquals("PASAR KLIWON", result.kecamatan)
    }

    @Test
    fun `section H - test all 7 mandatory KTP photo conditions and image processing pipeline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Foto KTP dari kamera (disimpan ke file kamera & diproses)
        val cameraBitmap = KtpOcrScanner.createHighResKtpBitmap()
        val cameraFile = File(context.cacheDir, "test_camera_ktp.jpg")
        FileOutputStream(cameraFile).use { out ->
            cameraBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        val cameraProcessed = KtpImageProcessor.processKtpBitmapForAi(cameraBitmap)
        assertTrue(cameraProcessed.qualityReport.isAcceptable)
        assertTrue(cameraProcessed.processedBitmap.width >= 1280)

        // 2. Foto KTP dari galeri (portrait dirotasi otomatis ke landscape)
        val portraitGalleryBitmap = KtpImageProcessor.correctRotationAndOrientation(cameraBitmap, 90)
        val galleryProcessed = KtpImageProcessor.processKtpBitmapForAi(portraitGalleryBitmap)
        assertTrue(galleryProcessed.processedBitmap.width > galleryProcessed.processedBitmap.height)
        assertTrue(galleryProcessed.qualityReport.isAcceptable)

        // 3. KTP dengan cahaya normal
        val normalLightProcessed = KtpImageProcessor.processKtpBitmapForAi(cameraBitmap)
        assertFalse(normalLightProcessed.qualityReport.isTooDark)
        assertFalse(normalLightProcessed.qualityReport.hasGlare)

        // 4. KTP sedikit miring (Perspective / Skew Correction)
        val tiltedBitmap = KtpOcrScanner.createHighResKtpBitmap(rotationTiltDegrees = 5.5f)
        val tiltedProcessed = KtpImageProcessor.processKtpBitmapForAi(tiltedBitmap)
        assertNotNull(tiltedProcessed.processedBitmap)
        assertTrue(tiltedProcessed.processedBitmap.width >= 1280)

        // 5. KTP dengan font kecil (resolusi ditingkatkan agar font kecil tetap terbaca, tidak di-downscale ke resolusi rendah)
        val smallCardBitmap = Bitmap.createScaledBitmap(
            KtpOcrScanner.createHighResKtpBitmap(fontScaleFactor = 0.85f),
            800,
            504,
            true
        )
        val smallFontProcessed = KtpImageProcessor.processKtpBitmapForAi(smallCardBitmap)
        assertTrue("Gambar font kecil harus di-upscale ke minimal 1280px", smallFontProcessed.processedBitmap.width >= 1280)

        // 6. KTP dengan pantulan cahaya / terlalu gelap (Quality Warning)
        val darkBitmap = Bitmap.createBitmap(1000, 630, Bitmap.Config.ARGB_8888).apply {
            val darkPixels = IntArray(1000 * 630) { Color.rgb(18, 20, 24) }
            setPixels(darkPixels, 0, 1000, 0, 0, 1000, 630)
        }
        val darkProcessed = KtpImageProcessor.processKtpBitmapForAi(darkBitmap)
        assertTrue(darkProcessed.qualityReport.isTooDark)
        assertFalse(darkProcessed.qualityReport.isAcceptable)
        assertTrue(darkProcessed.qualityReport.retakePromptMessage.contains("Cahaya terlalu gelap"))

        // 7. KTP resolusi tinggi (dipertahankan tajam & tidak dikecilkan ke resolusi rendah)
        val highResSource = Bitmap.createScaledBitmap(cameraBitmap, 2200, 1388, true)
        val highResProcessed = KtpImageProcessor.processKtpBitmapForAi(highResSource)
        assertEquals(2200, highResProcessed.processedBitmap.width)
        assertEquals(1388, highResProcessed.processedBitmap.height)
    }

    @Test
    fun `KtpGeminiVisionParser parses Gemini response JSON and maps to CustomerRegistrationFormState`() {
        val parser = com.example.ocr.KtpGeminiVisionParser()
        val geminiResponseJson = """
            {
              "nik": "3372051908950003",
              "nama_lengkap": "BAGUS PRASETYO",
              "tempat_lahir": "SURAKARTA",
              "tanggal_lahir": "19-08-1995",
              "jenis_kelamin": "Laki-laki",
              "golongan_darah": "AB",
              "alamat": "JL. VETERAN NO. 45",
              "rt": "002",
              "rw": "006",
              "kelurahan": "GAJAHAN",
              "kecamatan": "PASAR KLIWON",
              "kabupaten_kota": "KOTA SURAKARTA",
              "provinsi": "JAWA TENGAH",
              "agama": "ISLAM",
              "status_perkawinan": "BELUM KAWIN",
              "pekerjaan": "KARYAWAN SWASTA",
              "kewarganegaraan": "WNI",
              "berlaku_hingga": "SEUMUR HIDUP",
              "confidence": {
                "nik": 97,
                "nama_lengkap": 74,
                "tempat_lahir": 91,
                "tanggal_lahir": 93,
                "jenis_kelamin": 95,
                "alamat": 42
              },
              "status_pembacaan": "perlu_verifikasi"
            }
        """.trimIndent()

        val formState = parser.parseAndMapJsonToCustomerForm(
            rawJsonString = geminiResponseJson,
            allowLowConfidenceUnder50 = false,
            savedPhotoPath = "/tmp/ktp_bagus.jpg"
        )

        assertEquals("3372051908950003", formState.nik)
        assertEquals("BAGUS PRASETYO", formState.namaLengkap)
        assertEquals("SURAKARTA", formState.tempatLahir)
        assertEquals("19-08-1995", formState.tanggalLahir)
        assertEquals("Laki-laki", formState.jenisKelamin)
        assertEquals("AB", formState.golonganDarah)
        assertEquals("KOTA SURAKARTA", formState.kabupatenKota)
        assertEquals("JAWA TENGAH", formState.provinsi)
        assertEquals("ISLAM", formState.agama)
        assertEquals("BELUM KAWIN", formState.statusPerkawinan)
        assertEquals("KARYAWAN SWASTA", formState.pekerjaan)
        assertEquals("WNI", formState.kewarganegaraan)
        assertEquals("SEUMUR HIDUP", formState.berlakuHingga)

        // nama_lengkap memiliki confidence 74 (< 80) -> ditandai kuning (Perlu Verifikasi), tapi tetap terisi
        assertTrue(formState.yellowVerificationFields.contains("nama_lengkap"))

        // alamat memiliki confidence 42 (< 50) -> diblokir agar tidak terisi otomatis tanpa verifikasi admin
        assertTrue(formState.blockedLowConfidenceFields.contains("alamat"))
        assertEquals("", formState.alamatLengkap)

        // Konversi ke CustomerEntity untuk penyimpanan database
        val customerEntity = formState.toCustomerEntity(
            nomorHp = "081298765432",
            tanggalRegistrasi = "2026-11-11"
        )
        assertEquals("3372051908950003", customerEntity.nik)
        assertEquals("BAGUS PRASETYO", customerEntity.namaLengkap)
        assertEquals("SURAKARTA", customerEntity.tempatLahir)
        assertEquals("19-08-1995", customerEntity.tanggalLahir)
        assertEquals("Laki-laki", customerEntity.jenisKelamin)
    }

    @Test
    fun `KtpGeminiParser parses JSON into KtpData and enforces schema and confidence rules`() {
        val rawJson = """
            ```json
            {
              "nik": "0372011203900008",
              "nama_lengkap": "DEWI RATNASARI",
              "tempat_lahir": "SURAKARTA",
              "tanggal_lahir": "12-03-1990",
              "jenis_kelamin": "Perempuan",
              "golongan_darah": "A",
              "alamat": "JL. ADI SUCIPTO NO. 102",
              "rt": "001",
              "rw": "004",
              "kelurahan": "MANAHAN",
              "kecamatan": "BANJARSARI",
              "kabupaten_kota": "KOTA SURAKARTA",
              "provinsi": "JAWA TENGAH",
              "agama": "ISLAM",
              "status_perkawinan": "KAWIN",
              "pekerjaan": "WIRASWASTA",
              "kewarganegaraan": "WNI",
              "berlaku_hingga": "SEUMUR HIDUP",
              "confidence": {
                "nik": 98,
                "nama_lengkap": 95,
                "tempat_lahir": 72,
                "tanggal_lahir": 94,
                "jenis_kelamin": 96,
                "alamat": 45
              },
              "status_pembacaan": "berhasil"
            }
            ```
        """.trimIndent()

        val ktpData = com.example.ocr.KtpGeminiParser.parseJsonToKtpData(rawJson)

        assertEquals("0372011203900008", ktpData.nik)
        assertEquals("DEWI RATNASARI", ktpData.namaLengkap)
        assertEquals("SURAKARTA", ktpData.tempatLahir)
        assertEquals("12-03-1990", ktpData.tanggalLahir)
        assertEquals("Perempuan", ktpData.jenisKelamin)
        assertEquals("A", ktpData.golonganDarah)
        assertEquals("JL. ADI SUCIPTO NO. 102", ktpData.alamat)
        assertEquals("001", ktpData.rt)
        assertEquals("004", ktpData.rw)
        assertEquals("MANAHAN", ktpData.kelurahan)
        assertEquals("BANJARSARI", ktpData.kecamatan)
        assertEquals("KOTA SURAKARTA", ktpData.kabupatenKota)
        assertEquals("JAWA TENGAH", ktpData.provinsi)
        assertEquals("ISLAM", ktpData.agama)
        assertEquals("KAWIN", ktpData.statusPerkawinan)
        assertEquals("WIRASWASTA", ktpData.pekerjaan)
        assertEquals("WNI", ktpData.kewarganegaraan)
        assertEquals("SEUMUR HIDUP", ktpData.berlakuHingga)
        assertEquals("perlu_verifikasi", ktpData.statusPembacaan)

        // tempat_lahir (72) < 80 -> needsVerification == true, shouldAutoFill == true
        assertTrue(ktpData.needsVerification("tempat_lahir"))
        assertTrue(ktpData.shouldAutoFill("tempat_lahir"))

        // alamat (45) < 50 -> shouldAutoFill == false
        assertFalse(ktpData.shouldAutoFill("alamat"))

        val autoFillMap = ktpData.toCustomerFormMap(includeLowConfidenceUnder50 = false)
        assertEquals("0372011203900008", autoFillMap["nik"])
        assertEquals("SURAKARTA", autoFillMap["tempat_lahir"])
        assertEquals("", autoFillMap["alamat"])

        val exportedJson = JSONObject(ktpData.toJsonString())
        assertEquals("0372011203900008", exportedJson.getString("nik"))
        assertEquals("DEWI RATNASARI", exportedJson.getString("nama_lengkap"))
        assertEquals(98, exportedJson.getJSONObject("confidence").getInt("nik"))

        val mappedForm = com.example.ocr.KtpGeminiParser.parseAndMapToCustomerForm(
            jsonString = rawJson,
            allowLowConfidenceUnder50 = false,
            savedPhotoPath = "/tmp/ktp_dewi.jpg"
        )
        assertEquals("0372011203900008", mappedForm.nik)
        assertEquals("DEWI RATNASARI", mappedForm.namaLengkap)
        assertEquals("SURAKARTA", mappedForm.tempatLahir)
        assertEquals("12-03-1990", mappedForm.tanggalLahir)
        assertEquals("Perempuan", mappedForm.jenisKelamin)
        assertTrue(mappedForm.yellowVerificationFields.contains("tempat_lahir"))
        assertTrue(mappedForm.blockedLowConfidenceFields.contains("alamat"))
        assertEquals("", mappedForm.alamatLengkap)
    }

    @Test
    fun `validateKtpData verifies 16-digit nik, DD-MM-YYYY tanggal_lahir, and Laki-laki or Perempuan jenis_kelamin with specific error flags`() {
        val validKtp = com.example.ocr.KtpData(
            nik = "0372011405920001",
            namaLengkap = "ANDI PRATAMA",
            tempatLahir = "SURAKARTA",
            tanggalLahir = "14-05-1992",
            jenisKelamin = "Laki-laki"
        )
        val validResult = com.example.ocr.validateKtpData(validKtp)
        assertTrue(validResult.isValid)
        assertTrue(validResult.isNikValid)
        assertTrue(validResult.isTanggalLahirValid)
        assertTrue(validResult.isJenisKelaminValid)
        assertFalse(validResult.invalidNikError)
        assertFalse(validResult.invalidTanggalLahirError)
        assertFalse(validResult.invalidJenisKelaminError)
        assertTrue(validResult.errorFlags.isEmpty())

        // Invalid NIK (15 digits), invalid tanggal_lahir (YYYY-MM-DD and invalid day 31-02-1992), invalid jenis_kelamin ("Pria")
        val invalidKtp = com.example.ocr.KtpData(
            nik = "337201140592001",
            namaLengkap = "ANDI PRATAMA",
            tempatLahir = "SURAKARTA",
            tanggalLahir = "1992-05-14",
            jenisKelamin = "Pria"
        )
        val invalidResult = invalidKtp.validate()
        assertFalse(invalidResult.isValid)
        assertFalse(invalidResult.isNikValid)
        assertFalse(invalidResult.isTanggalLahirValid)
        assertFalse(invalidResult.isJenisKelaminValid)
        assertTrue(invalidResult.invalidNikError)
        assertTrue(invalidResult.invalidTanggalLahirError)
        assertTrue(invalidResult.invalidJenisKelaminError)
        assertTrue(invalidResult.errorFlags.contains(com.example.ocr.KtpFieldErrorFlag.INVALID_NIK))
        assertTrue(invalidResult.errorFlags.contains(com.example.ocr.KtpFieldErrorFlag.INVALID_TANGGAL_LAHIR))
        assertTrue(invalidResult.errorFlags.contains(com.example.ocr.KtpFieldErrorFlag.INVALID_JENIS_KELAMIN))
        assertTrue(invalidResult.hasError("nik"))
        assertTrue(invalidResult.hasError("tanggal_lahir"))
        assertTrue(invalidResult.hasError("jenis_kelamin"))
    }

    @Test
    fun `KtpGeminiDiagnosticWrapper captures raw response and logs parsing exceptions accurately`() {
        val validJson = """
            {
              "nik": "3372051208940001",
              "nama_lengkap": "EKO PRASETYO",
              "tempat_lahir": "SURAKARTA",
              "tanggal_lahir": "12-08-1994",
              "jenis_kelamin": "Laki-laki",
              "alamat": "JL. SLAMET RIYADI NO. 120",
              "rt": "003",
              "rw": "005",
              "kelurahan": "SRIWEDARI",
              "kecamatan": "LAWEYAN",
              "kabupaten_kota": "KOTA SURAKARTA",
              "provinsi": "JAWA TENGAH",
              "agama": "ISLAM",
              "status_perkawinan": "KAWIN",
              "pekerjaan": "KARYAWAN SWASTA",
              "kewarganegaraan": "WNI",
              "berlaku_hingga": "SEUMUR HIDUP",
              "confidence": {
                "nik": 96,
                "nama_lengkap": 94,
                "tempat_lahir": 92,
                "tanggal_lahir": 92,
                "jenis_kelamin": 95,
                "alamat": 90
              },
              "status_pembacaan": "berhasil"
            }
        """.trimIndent()

        // 1. Uji pemrosesan respons sukses
        val (data, successReport) = com.example.ocr.KtpGeminiDiagnosticWrapper.parseJsonWithDiagnostics(validJson)
        assertTrue(successReport.isSuccess)
        assertEquals("SUCCESS", successReport.status)
        assertEquals(validJson, successReport.rawResponseText)
        assertEquals("3372051208940001", data.nik)
        assertEquals("EKO PRASETYO", data.namaLengkap)
        assertTrue(successReport.toFormattedLog().contains("[KtpGeminiDiagnostics REPORT]"))

        // 2. Uji penangkapan respons yang rusak / malformed JSON (menangkap exception)
        val malformedJson = "{ nik: 3372051208940001, nama_lengkap: [UNCLOSED_JSON"
        val (failData, failReport) = com.example.ocr.KtpGeminiDiagnosticWrapper.parseJsonWithDiagnostics(malformedJson)
        assertFalse(failReport.isSuccess)
        assertEquals("JSON_PARSE_ERROR", failReport.status)
        assertEquals("JSON_PARSING", failReport.errorStage)
        assertEquals(malformedJson, failReport.rawResponseText)
        assertNotNull(failReport.exceptionClass)
        assertNotNull(failReport.exceptionMessage)
        assertEquals("gagal", failData.statusPembacaan)

        // 3. Pastikan wrapper menyimpan laporan terakhir dan riwayat
        com.example.ocr.KtpGeminiDiagnosticWrapper.recordReport(failReport)
        val lastReport = com.example.ocr.KtpGeminiDiagnosticWrapper.lastReport
        assertNotNull(lastReport)
        assertEquals("JSON_PARSE_ERROR", lastReport?.status)
        assertTrue(com.example.ocr.KtpGeminiDiagnosticWrapper.getRecentReports().isNotEmpty())
    }

    @Test
    fun `KtpDataValidator checks nik length, tanggal_lahir DD-MM-YYYY format, and jenis_kelamin contains Laki-laki or Perempuan with descriptive error codes`() {
        val validator = com.example.ocr.KtpDataValidator()

        // 1. Valid e-KTP data
        val validResult = com.example.ocr.KtpDataValidator.validate(
            nik = "3372051208940001",
            tanggalLahir = "17-08-1945",
            jenisKelamin = "Laki-laki"
        )
        assertTrue(validResult.isValid)
        assertTrue(validResult.isNikValid)
        assertTrue(validResult.isTanggalLahirValid)
        assertTrue(validResult.isJenisKelaminValid)
        assertTrue(validResult.errorCodes.isEmpty())

        // 2. NIK Length & Digit validations returning descriptive error codes
        val shortNikErrors = com.example.ocr.KtpDataValidator.validateNik("337201")
        assertTrue(shortNikErrors.contains(com.example.ocr.KtpErrorCode.ERR_NIK_LENGTH))

        val longNikErrors = com.example.ocr.KtpDataValidator.validateNik("337205120894000199")
        assertTrue(longNikErrors.contains(com.example.ocr.KtpErrorCode.ERR_NIK_LENGTH))

        val emptyNikErrors = com.example.ocr.KtpDataValidator.validateNik("")
        assertTrue(emptyNikErrors.contains(com.example.ocr.KtpErrorCode.ERR_NIK_EMPTY))

        val nonDigitNikErrors = com.example.ocr.KtpDataValidator.validateNik("337205120894000A")
        assertTrue(nonDigitNikErrors.contains(com.example.ocr.KtpErrorCode.ERR_NIK_NON_DIGIT))

        // 3. Tanggal Lahir DD-MM-YYYY date format and calendar checks
        val wrongFormatErrors = com.example.ocr.KtpDataValidator.validateTanggalLahir("1990-12-31")
        assertTrue(wrongFormatErrors.contains(com.example.ocr.KtpErrorCode.ERR_TANGGAL_LAHIR_FORMAT))

        val invalidDateErrors = com.example.ocr.KtpDataValidator.validateTanggalLahir("31-02-1995")
        assertTrue(invalidDateErrors.contains(com.example.ocr.KtpErrorCode.ERR_TANGGAL_LAHIR_INVALID_DATE))

        val leapYearValid = com.example.ocr.KtpDataValidator.isValidTanggalLahir("29-02-2024")
        assertTrue(leapYearValid)

        val nonLeapYearInvalid = com.example.ocr.KtpDataValidator.validateTanggalLahir("29-02-2023")
        assertTrue(nonLeapYearInvalid.contains(com.example.ocr.KtpErrorCode.ERR_TANGGAL_LAHIR_INVALID_DATE))

        val emptyTanggalErrors = com.example.ocr.KtpDataValidator.validateTanggalLahir("")
        assertTrue(emptyTanggalErrors.contains(com.example.ocr.KtpErrorCode.ERR_TANGGAL_LAHIR_EMPTY))

        // 4. Jenis Kelamin contains 'Laki-laki' or 'Perempuan'
        assertTrue(com.example.ocr.KtpDataValidator.isValidJenisKelamin("Laki-laki"))
        assertTrue(com.example.ocr.KtpDataValidator.isValidJenisKelamin("Perempuan"))
        assertTrue(com.example.ocr.KtpDataValidator.isValidJenisKelamin("JENIS KELAMIN: LAKI-LAKI"))
        assertTrue(com.example.ocr.KtpDataValidator.isValidJenisKelamin("STATUS: PEREMPUAN"))

        val invalidGenderErrors = com.example.ocr.KtpDataValidator.validateJenisKelamin("Pria")
        assertTrue(invalidGenderErrors.contains(com.example.ocr.KtpErrorCode.ERR_JENIS_KELAMIN_INVALID))

        val emptyGenderErrors = com.example.ocr.KtpDataValidator.validateJenisKelamin("")
        assertTrue(emptyGenderErrors.contains(com.example.ocr.KtpErrorCode.ERR_JENIS_KELAMIN_EMPTY))

        // 5. Test instance helper methods and error code mapping
        val invalidAllKtp = com.example.ocr.KtpData(
            nik = "123",
            tanggalLahir = "99-99-9999",
            jenisKelamin = "Lainnya"
        )
        val result = validator.validate(invalidAllKtp)
        assertFalse(result.isValid)
        assertTrue(result.hasError("nik"))
        assertTrue(result.hasError("tanggal_lahir"))
        assertTrue(result.hasError("jenis_kelamin"))
        assertEquals(com.example.ocr.KtpErrorCode.ERR_NIK_LENGTH, result.getFirstErrorCode("nik"))
        assertEquals(com.example.ocr.KtpErrorCode.ERR_TANGGAL_LAHIR_INVALID_DATE, result.getFirstErrorCode("tanggal_lahir"))
        assertEquals(com.example.ocr.KtpErrorCode.ERR_JENIS_KELAMIN_INVALID, result.getFirstErrorCode("jenis_kelamin"))
        assertTrue(result.errorMessages.isNotEmpty())
    }

    @Test
    fun `KtpCameraViewfinder and CameraPreview define accurate ID-1 aspect ratio and instructions`() {
        assertEquals(1.586f, com.example.ocr.KTP_CARD_ASPECT_RATIO, 0.01f)
    }

    @Test
    fun `Room Database entities and DAOs store customer profiles, freezer inventory units, and rental transactions locally`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val customerDao = db.customerDao()
            val freezerUnitDao = db.freezerUnitDao()
            val rentalTransactionDao = db.rentalTransactionDao()

            // 1. Store Customer Profile Locally
            val customer = com.example.data.local.CustomerEntity(
                id = 1L,
                kodePelanggan = "CUST/SAF/0001",
                namaLengkap = "Budi Santoso",
                nik = "3372010101900001",
                nomorHp = "081234567890",
                alamatLengkap = "Jl. Slamet Riyadi No. 12, Surakarta",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = "2026-11-12"
            )
            val insertedCustId = customerDao.insertCustomer(customer)
            assertEquals(1L, insertedCustId)

            val retrievedCustomer = customerDao.getCustomerById(1L)
            assertNotNull(retrievedCustomer)
            assertEquals("Budi Santoso", retrievedCustomer?.namaLengkap)
            assertEquals("3372010101900001", retrievedCustomer?.nik)
            assertEquals(1, customerDao.getCustomerCount())

            // 2. Store Freezer Inventory Unit Locally
            val unit = com.example.data.local.FreezerUnitEntity(
                id = 1L,
                kodeUnit = "FZ-SOC-001",
                namaUnit = "Chest Freezer 200L Starlight",
                jenisUnit = "Chest Freezer",
                owner = "SAF",
                hargaPembelian = 3500000L,
                kapasitasLiter = 200,
                cicilanPerBulan = 300000L,
                merek = "Starlight",
                model = "CF-200",
                nomorSeri = "SN-2026-CF200-01",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang Pusat Surakarta",
                tanggalPembelian = "2026-01-15"
            )
            val insertedUnitId = freezerUnitDao.insertUnit(unit)
            assertEquals(1L, insertedUnitId)

            val retrievedUnit = freezerUnitDao.getUnitByKode("FZ-SOC-001")
            assertNotNull(retrievedUnit)
            assertEquals("Chest Freezer 200L Starlight", retrievedUnit?.namaUnit)
            assertEquals("Tersedia", retrievedUnit?.statusUnit)
            assertEquals(1, freezerUnitDao.getUnitCount())

            // 3. Store Rental Transaction Record Locally
            val transaction = com.example.data.local.RentalTransactionEntity(
                id = 1L,
                nomorInvoice = "INV/SAF/2026/0001",
                tanggalPemesanan = "2026-11-12",
                customerId = 1L,
                namaPelangganSnapshot = "Budi Santoso",
                unitId = 1L,
                namaUnitSnapshot = "Chest Freezer 200L Starlight",
                kodeUnitSnapshot = "FZ-SOC-001",
                hargaSewa = 250000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-12",
                tanggalAkhirSewa = "2026-12-12",
                tanggalJatuhTempo = "2026-12-12",
                nominalPembayaran = 250000L,
                totalTagihan = 250000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA"
            )
            val insertedTxId = rentalTransactionDao.insertTransaction(transaction)
            assertEquals(1L, insertedTxId)

            val retrievedTx = rentalTransactionDao.getTransactionByInvoice("INV/SAF/2026/0001")
            assertNotNull(retrievedTx)
            assertEquals("INV/SAF/2026/0001", retrievedTx?.nomorInvoice)
            assertEquals("Lunas", retrievedTx?.statusPembayaran)
            assertEquals(1, rentalTransactionDao.getTransactionCount())
            assertEquals(1, rentalTransactionDao.getTransactionCountForCustomer(1L))
        } finally {
            db.close()
        }
    }

    @Test
    fun ktpOcrScanner_validatesSectionE_preventsJobOrReligionInNama_andSeparatesAlamatFromKelurahanKecamatan() {
        // Section E.5 & E.6:
        // 5. Alamat tidak boleh tercampur dengan kecamatan dan kelurahan.
        // 6. Nama tidak boleh diisi menggunakan informasi pekerjaan atau agama.
        val misalignedResult = KtpOcrResult(
            nik = "3372051408920003",
            namaLengkap = "KARYAWAN SWASTA", // Wrongly placed job in nama!
            tempatLahir = "SURAKARTA",
            tanggalLahir = "14-08-1992",
            jenisKelamin = "LAKI-LAKI",
            golonganDarah = "O",
            alamat = "JL. SLAMET RIYADI NO 120 RT/RW 002/005 KEL/DESA PENUMPING KECAMATAN LAWEYAN",
            rt = "2",
            rw = "5",
            kelurahanDesa = "PENUMPING",
            kecamatan = "LAWEYAN",
            kabupatenKota = "KOTA SURAKARTA",
            provinsi = "JAWA TENGAH",
            agama = "ISLAM",
            statusPerkawinan = "KAWIN",
            pekerjaan = "", // Missing job that got put into nama
            kewarganegaraan = "WNI",
            berlakuHingga = "SEUMUR HIDUP",
            confidence = KtpConfidenceScores(
                nik = 96,
                namaLengkap = 88,
                tempatLahir = 92,
                tanggalLahir = 94,
                alamat = 91
            ),
            rawOcrText = "NIK : 3372051408920003\nNama : HENDRA WIJAYA\nPekerjaan : KARYAWAN SWASTA"
        )

        val sanitized = KtpOcrScanner.validateAndSanitizeKtpResult(misalignedResult)
        // Nama must be cleared of job title and never contain "KARYAWAN SWASTA"
        assertTrue("Nama must not contain job title", sanitized.nama.isEmpty())
        assertEquals("KARYAWAN SWASTA", sanitized.pekerjaan)
        // RT/RW padded to 3 digits
        assertEquals("002", sanitized.rt)
        assertEquals("005", sanitized.rw)
        // Alamat must not contain RT/RW, KEL/DESA PENUMPING, or KECAMATAN LAWEYAN
        assertEquals("JL. SLAMET RIYADI NO 120", sanitized.alamat)
        assertFalse(sanitized.alamat.contains("PENUMPING"))
        assertFalse(sanitized.alamat.contains("LAWEYAN"))

        // Also test when religion is mistakenly placed in nama
        val religionInNama = misalignedResult.copy(
            namaLengkap = "ISLAM",
            agama = ""
        )
        val sanitizedReligion = KtpOcrScanner.validateAndSanitizeKtpResult(religionInNama)
        assertTrue("Nama must not contain religion", sanitizedReligion.nama.isEmpty())
        assertEquals("ISLAM", sanitizedReligion.agama)
    }

    @Test
    fun ktpOcrScanner_enforcesSectionD_structuredJsonAndFieldDetails_andSectionF_confidenceTiers() {
        val rawResult = KtpOcrResult(
            nik = "3372012504880002",
            namaLengkap = "DEWI KARTIKA SARI",
            tempatLahir = "SUKOHARJO",
            tanggalLahir = "25-04-1988",
            jenisKelamin = "PEREMPUAN",
            golonganDarah = "A",
            alamat = "JL. MERPATI INDAH NO. 45",
            rt = "004",
            rw = "009",
            kelurahanDesa = "MANAHAN",
            kecamatan = "BANJARSARI",
            kabupatenKota = "KOTA SURAKARTA",
            provinsi = "JAWA TENGAH",
            agama = "ISLAM",
            statusPerkawinan = "KAWIN",
            pekerjaan = "WIRASWASTA",
            kewarganegaraan = "WNI",
            berlakuHingga = "SEUMUR HIDUP",
            confidence = KtpConfidenceScores(
                nik = 98,            // >= 90% -> HIJAU_OTOMATIS
                namaLengkap = 95,    // >= 90% -> HIJAU_OTOMATIS
                tempatLahir = 82,    // 70-89% -> KUNING_PERLU_VERIFIKASI
                tanggalLahir = 92,   // >= 90% -> HIJAU_OTOMATIS
                jenisKelamin = 95,
                alamat = 62,         // < 70%  -> MERAH_KOREKSI_MANUAL
                rt = 91,
                rw = 91,
                kelurahan = 85,      // 70-89% -> KUNING_PERLU_VERIFIKASI
                kecamatan = 90,      // >= 90% -> HIJAU_OTOMATIS
                agama = 94,
                statusPerkawinan = 88,
                pekerjaan = 55,      // < 70%  -> MERAH_KOREKSI_MANUAL
                kewarganegaraan = 96,
                berlakuHingga = 95
            ),
            rawOcrText = "PROVINSI JAWA TENGAH\nKOTA SURAKARTA\nNIK : 3372012504880002",
            aiSourceEngine = "Gemini Vision + ML Kit Spatial"
        )

        val validated = KtpOcrScanner.validateAndSanitizeKtpResult(rawResult)

        // Section F checks
        assertTrue(validated.isGreenConfidence90Plus("nik"))
        assertTrue(validated.isGreenConfidence90Plus("nama"))
        assertTrue(validated.isYellowConfidence70To89("tempat_lahir"))
        assertTrue(validated.isYellowConfidence70To89("kelurahan"))
        assertTrue(validated.isRedManualCorrectionUnder70("alamat"))
        assertTrue(validated.isRedManualCorrectionUnder70("pekerjaan"))
        assertTrue(validated.manualCorrectionFields.contains("alamat"))
        assertTrue(validated.manualCorrectionFields.contains("pekerjaan"))

        // Auto-fill under Section F only returns value if >= 70% (unless manual correction override is true)
        assertEquals("3372012504880002", validated.getStrictSectionFAutoFillValue("nik"))
        assertEquals("SUKOHARJO", validated.getStrictSectionFAutoFillValue("tempat_lahir"))
        assertEquals("", validated.getStrictSectionFAutoFillValue("alamat"))
        assertEquals("JL. MERPATI INDAH NO. 45", validated.getStrictSectionFAutoFillValue("alamat", allowManualCorrectionOverride = true))

        // Section D structured JSON check
        val jsonStr = validated.toStructuredJsonString()
        val json = JSONObject(jsonStr)
        val required15Keys = listOf(
            "nik", "nama", "tempat_lahir", "tanggal_lahir", "jenis_kelamin",
            "alamat", "rt", "rw", "kelurahan", "kecamatan", "agama",
            "status_perkawinan", "pekerjaan", "kewarganegaraan", "berlaku_hingga"
        )
        for (key in required15Keys) {
            assertTrue("JSON must contain required key '$key'", json.has(key))
        }
        assertEquals("DEWI KARTIKA SARI", json.getString("nama"))
        assertEquals("Perempuan", json.getString("jenis_kelamin"))
        assertEquals("MANAHAN", json.getString("kelurahan"))

        // Verify field_details has value, confidence, verification_status, source for every field
        assertTrue(json.has("field_details"))
        val fieldDetailsObj = json.getJSONObject("field_details")
        for (key in required15Keys) {
            assertTrue("field_details must include '$key'", fieldDetailsObj.has(key))
            val detail = fieldDetailsObj.getJSONObject(key)
            assertTrue(detail.has("value"))
            assertTrue(detail.has("confidence"))
            assertTrue(detail.has("verification_status"))
            assertTrue(detail.has("source"))
        }
        assertEquals("HIJAU_OTOMATIS", fieldDetailsObj.getJSONObject("nik").getString("verification_status"))
        assertEquals("KUNING_PERLU_VERIFIKASI", fieldDetailsObj.getJSONObject("tempat_lahir").getString("verification_status"))
        assertEquals("MERAH_KOREKSI_MANUAL", fieldDetailsObj.getJSONObject("alamat").getString("verification_status"))
    }

    @Test
    fun ktpOcrScanner_evaluates30DistinctIndonesianKtpSamples_withPerFieldAccuracyAndRoomPersistence() = kotlinx.coroutines.runBlocking {
        data class KtpSampleGroundTruth(
            val sampleIndex: Int,
            val provinsi: String,
            val kabupatenKota: String,
            val nik: String,
            val nama: String,
            val tempatLahir: String,
            val tanggalLahir: String,
            val jenisKelamin: String, // LAKI-LAKI or PEREMPUAN
            val alamat: String,
            val rt: String,
            val rw: String,
            val kelurahan: String,
            val kecamatan: String,
            val agama: String,
            val statusPerkawinan: String,
            val pekerjaan: String,
            val kewarganegaraan: String = "WNI",
            val berlakuHingga: String = "SEUMUR HIDUP"
        )

        val samples30 = listOf(
            KtpSampleGroundTruth(1, "JAWA TENGAH", "KOTA SURAKARTA", "3372011203850001", "BUDI SANTOSO", "SURAKARTA", "12-03-1985", "LAKI-LAKI", "JL. SLAMET RIYADI NO. 88", "001", "004", "PENUMPING", "LAWEYAN", "ISLAM", "KAWIN", "WIRASWASTA"),
            KtpSampleGroundTruth(2, "JAWA TENGAH", "KABUPATEN SUKOHARJO", "3311095407900002", "SITI AMINAH", "SUKOHARJO", "14-07-1990", "PEREMPUAN", "PERUM GADING PERMAI BLOK B-12", "003", "007", "MAKAMHAJI", "KARTASURA", "ISLAM", "KAWIN", "MENGURUS RUMAH TANGGA"),
            KtpSampleGroundTruth(3, "JAWA TENGAH", "KABUPATEN KARANGANYAR", "3313062109880003", "AGUS PRASETYO", "KARANGANYAR", "21-09-1988", "LAKI-LAKI", "JL. LAWU RAYA NO. 15", "002", "006", "BEJEN", "KARANGANYAR", "ISLAM", "KAWIN", "KARYAWAN SWASTA"),
            KtpSampleGroundTruth(4, "JAWA TENGAH", "KABUPATEN BOYOLALI", "3309056211930004", "RATNA KUSUMA DEWI", "BOYOLALI", "22-11-1993", "PEREMPUAN", "DUKUH KEBONSO NO. 19", "005", "002", "PULISEN", "BOYOLALI", "ISLAM", "BELUM KAWIN", "PEGAWAI NEGERI SIPIL"),
            KtpSampleGroundTruth(5, "JAWA TENGAH", "KABUPATEN KLATEN", "3310020801820005", "JOKO SUSILO", "KLATEN", "08-01-1982", "LAKI-LAKI", "JL. PEMUDA SELATAN NO. 31", "004", "008", "TONGGALAN", "KLATEN TENGAH", "KRISTEN", "KAWIN", "PEDAGANG"),
            KtpSampleGroundTruth(6, "JAWA TENGAH", "KABUPATEN SRAGEN", "3314104505950006", "RINA WULANDARI", "SRAGEN", "05-05-1995", "PEREMPUAN", "KAMPUNG TEGALSARI NO. 7", "001", "003", "SRAGEN KULON", "SRAGEN", "ISLAM", "BELUM KAWIN", "KARYAWAN BUMN"),
            KtpSampleGroundTruth(7, "JAWA TENGAH", "KABUPATEN WONOGIRI", "3312111904800007", "BAMBANG SUPRIYADI", "WONOGIRI", "19-04-1980", "LAKI-LAKI", "JL. AHMAD YANI NO. 104", "002", "005", "GIRIPURWO", "WONOGIRI", "KATOLIK", "KAWIN", "GURU"),
            KtpSampleGroundTruth(8, "DAERAH ISTIMEWA YOGYAKARTA", "KOTA YOGYAKARTA", "3471035808910008", "THERESIA NUGRAHENI", "YOGYAKARTA", "18-08-1991", "PEREMPUAN", "JL. KALIURANG KM 5 GG. MEKAR NO. 9", "006", "002", "COKRODININGRATAN", "JETIS", "KATOLIK", "KAWIN", "DOSEN"),
            KtpSampleGroundTruth(9, "DAERAH ISTIMEWA YOGYAKARTA", "KABUPATEN SLEMAN", "3404071112870009", "ANTONIUS WIBOWO", "SLEMAN", "11-12-1987", "LAKI-LAKI", "JL. RINGROAD UTARA NO. 52", "007", "014", "CATURTUNGGAL", "DEPOK", "KRISTEN", "KAWIN", "ARSITEK"),
            KtpSampleGroundTruth(10, "DAERAH ISTIMEWA YOGYAKARTA", "KABUPATEN BANTUL", "3402156702960010", "NURUL HIDAYAH", "BANTUL", "27-02-1996", "PEREMPUAN", "JL. PARANGTRITIS KM 8 NO. 11", "003", "009", "TIMBULHARJO", "SEWON", "ISLAM", "BELUM KAWIN", "APOTEKER"),
            KtpSampleGroundTruth(11, "JAWA TIMUR", "KOTA SURABAYA", "3578041506840011", "HENDRO PRAMONO", "SURABAYA", "15-06-1984", "LAKI-LAKI", "JL. DARMO PERMAI TIMUR NO. 24", "005", "004", "SONOKWIJENAN", "SUKOMANUNGGAL", "ISLAM", "KAWIN", "WIRASWASTA"),
            KtpSampleGroundTruth(12, "JAWA TIMUR", "KOTA MALANG", "3573024910920012", "DIAN NOVITASARI", "MALANG", "09-10-1992", "PEREMPUAN", "JL. SOEKARNO HATTA INDAH BLOK C-4", "002", "011", "MOJOLANGU", "LOWOKWARU", "ISLAM", "KAWIN", "DOKTER"),
            KtpSampleGroundTruth(13, "JAWA TIMUR", "KABUPATEN SIDOARJO", "3515082301890013", "WAHYU HIDAYAT", "SIDOARJO", "23-01-1989", "LAKI-LAKI", "PERUM PONDOK JATI BLOK AK-8", "008", "003", "JATI", "SIDOARJO", "ISLAM", "KAWIN", "KARYAWAN SWASTA"),
            KtpSampleGroundTruth(14, "JAWA TIMUR", "KOTA MADIUN", "3577016403940014", "MEILANI PUTRI UTAMI", "MADIUN", "24-03-1994", "PEREMPUAN", "JL. PAHLAWAN NO. 63", "001", "002", "MADIUN LOR", "MANGUHARJO", "KRISTEN", "BELUM KAWIN", "AKUNTAN"),
            KtpSampleGroundTruth(15, "JAWA BARAT", "KOTA BANDUNG", "3273091708860015", "ASEP SUNANDAR", "BANDUNG", "17-08-1986", "LAKI-LAKI", "JL. BUAH BATU NO. 142", "004", "007", "CIJAGRA", "LENGKONG", "ISLAM", "KAWIN", "WIRASWASTA"),
            KtpSampleGroundTruth(16, "JAWA BARAT", "KOTA BOGOR", "3271045109910016", "NENG SITI ROHMAH", "BOGOR", "11-09-1991", "PEREMPUAN", "JL. PAJAJARAN INDAH V NO. 18", "003", "006", "BARANANGSIANG", "BOGOR TIMUR", "ISLAM", "KAWIN", "BIDAN"),
            KtpSampleGroundTruth(17, "JAWA BARAT", "KOTA BEKASI", "3275032805830017", "DEDY KURNIAWAN", "BEKASI", "28-05-1983", "LAKI-LAKI", "PERUM GALAXY HARAPAN BLOK D-9", "009", "012", "JAKA SETIA", "BEKASI SELATAN", "ISLAM", "KAWIN", "KARYAWAN SWASTA"),
            KtpSampleGroundTruth(18, "JAWA BARAT", "KOTA DEPOK", "3276026012970018", "NADIA SAFITRI", "DEPOK", "20-12-1997", "PEREMPUAN", "JL. MARGONDA RAYA GG. KOBER NO. 5", "002", "004", "PONDOK CINA", "BEJI", "ISLAM", "BELUM KAWIN", "PELAJAR/MAHASISWA"),
            KtpSampleGroundTruth(19, "DKI JAKARTA", "KOTA ADM. JAKARTA SELATAN", "3174010402850019", "REZA PAHLEVI", "JAKARTA", "04-02-1985", "LAKI-LAKI", "JL. TEBET TIMUR DALAM II NO. 16", "006", "005", "TEBET TIMUR", "TEBET", "ISLAM", "KAWIN", "KONSULTAN"),
            KtpSampleGroundTruth(20, "DKI JAKARTA", "KOTA ADM. JAKARTA BARAT", "3173065307900020", "VERONICA TANUWIJAYA", "JAKARTA", "13-07-1990", "PEREMPUAN", "TAMAN PALEM LESTARI BLOK A5 NO. 21", "010", "013", "CENGKARENG BARAT", "CENGKARENG", "BUDDHA", "KAWIN", "WIRASWASTA"),
            KtpSampleGroundTruth(21, "BANTEN", "KOTA TANGERANG SELATAN", "3674041911880021", "KEVIN LIMANTORO", "TANGERANG", "19-11-1988", "LAKI-LAKI", "BSD SEKTOR XIV BLOK H2 NO. 10", "004", "008", "RAWA MEKAR JAYA", "SERPONG", "KONGHUCU", "KAWIN", "WIRASWASTA"),
            KtpSampleGroundTruth(22, "BALI", "KOTA DENPASAR", "5171022604870022", "I MADE WIRAWAN", "DENPASAR", "26-04-1987", "LAKI-LAKI", "JL. GATOT SUBROTO TENGAH NO. 77", "001", "001", "DAUH PURI KAJA", "DENPASAR UTARA", "HINDU", "KAWIN", "SENIMAN"),
            KtpSampleGroundTruth(23, "BALI", "KABUPATEN BADUNG", "5103056908940023", "NI LUH PUTU AYU LESTARI", "BADUNG", "29-08-1994", "PEREMPUAN", "BR. KWANJI DALUNG NO. 14", "002", "003", "DALUNG", "KUTA UTARA", "HINDU", "BELUM KAWIN", "KARYAWAN SWASTA"),
            KtpSampleGroundTruth(24, "SUMATERA UTARA", "KOTA MEDAN", "1271051003810024", "TUMPAL SIMANJUNTAK", "MEDAN", "10-03-1981", "LAKI-LAKI", "JL. SISINGAMANGARAJA NO. 210", "005", "009", "TELADAN BARAT", "MEDAN KOTA", "KRISTEN", "KAWIN", "PENGACARA"),
            KtpSampleGroundTruth(25, "SUMATERA BARAT", "KOTA PADANG", "1371045501920025", "RAHMI YULIA FITRI", "PADANG", "15-01-1992", "PEREMPUAN", "JL. KHATIB SULAIMAN NO. 48", "003", "005", "ULAK KARANG SELATAN", "PADANG UTARA", "ISLAM", "KAWIN", "PERAWAT"),
            KtpSampleGroundTruth(26, "SUMATERA SELATAN", "KOTA PALEMBANG", "1671070309860026", "MUHAMMAD RIZKY PRATAMA", "PALEMBANG", "03-09-1986", "LAKI-LAKI", "JL. DEMANG LEBAR DAUN NO. 95", "007", "002", "LOROK PAKJO", "ILIR BARAT I", "ISLAM", "KAWIN", "KARYAWAN BUMD"),
            KtpSampleGroundTruth(27, "KALIMANTAN TIMUR", "KOTA BALIKPAPAN", "6471034706930027", "FITRIA HANDAYANI", "BALIKPAPAN", "07-06-1993", "PEREMPUAN", "JL. MT HARYONO DALAM NO. 33", "012", "004", "DAMAI", "BALIKPAPAN SELATAN", "ISLAM", "CERAI HIDUP", "WIRASWASTA"),
            KtpSampleGroundTruth(28, "SULAWESI SELATAN", "KOTA MAKASSAR", "7371092210840028", "ANDI MAPPANYUKKI", "MAKASSAR", "22-10-1984", "LAKI-LAKI", "JL. AP PETTARANI KOMP. IDI BLOK G-5", "004", "006", "MASALE", "PANAKKUKANG", "ISLAM", "KAWIN", "WIRASWASTA"),
            KtpSampleGroundTruth(29, "SULAWESI UTARA", "KOTA MANADO", "7171065904950029", "GRACE LUMEMPOUW", "MANADO", "19-04-1995", "PEREMPUAN", "JL. SAM RATULANGI NO. 118", "002", "003", "TITIWOUNGAN UTARA", "SARIO", "KRISTEN", "BELUM KAWIN", "JURNALIS"),
            KtpSampleGroundTruth(30, "NUSA TENGGARA BARAT", "KOTA MATARAM", "5271013012890030", "LALU HERMANSYAH", "MATARAM", "30-12-1989", "LAKI-LAKI", "JL. PEJANGGIK NO. 59", "005", "007", "MATARAM BARAT", "SELAPARANG", "ISLAM", "CERAI MATI", "PETANI/PEKEBUN")
        )

        assertEquals(30, samples30.size)

        val fieldCorrectCounts = mutableMapOf(
            "nik" to 0,
            "nama" to 0,
            "tempat_lahir" to 0,
            "tanggal_lahir" to 0,
            "jenis_kelamin" to 0,
            "alamat" to 0,
            "rt" to 0,
            "rw" to 0,
            "kelurahan" to 0,
            "kecamatan" to 0,
            "agama" to 0,
            "status_perkawinan" to 0,
            "pekerjaan" to 0,
            "kewarganegaraan" to 0,
            "berlaku_hingga" to 0
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val customerDao = db.customerDao()

            for (sample in samples30) {
                // 1. Construct realistic OCR text from KTP layout and verify ML Kit Spatial Parser
                val rawOcrText = """
                    PROVINSI ${sample.provinsi}
                    ${sample.kabupatenKota}
                    NIK : ${sample.nik}
                    Nama : ${sample.nama}
                    Tempat/Tgl Lahir : ${sample.tempatLahir}, ${sample.tanggalLahir}
                    Jenis Kelamin : ${sample.jenisKelamin}   Gol. Darah : O
                    Alamat : ${sample.alamat}
                        RT/RW : ${sample.rt}/${sample.rw}
                        Kel/Desa : ${sample.kelurahan}
                        Kecamatan : ${sample.kecamatan}
                    Agama : ${sample.agama}
                    Status Perkawinan : ${sample.statusPerkawinan}
                    Pekerjaan : ${sample.pekerjaan}
                    Kewarganegaraan : ${sample.kewarganegaraan}
                    Berlaku Hingga : ${sample.berlakuHingga}
                """.trimIndent()

                val mlKitParsed = KtpOcrScanner.parseIndonesianKtpText(rawOcrText)
                assertEquals("Sample #${sample.sampleIndex} ML Kit NIK", sample.nik, mlKitParsed.nik)
                assertEquals("Sample #${sample.sampleIndex} ML Kit Nama", sample.nama, mlKitParsed.nama)

                // 2. Test AI Vision JSON parsing + Validation pipeline
                val aiVisionJson = """
                    {
                      "nik": "${sample.nik}",
                      "nama": "${sample.nama}",
                      "tempat_lahir": "${sample.tempatLahir}",
                      "tanggal_lahir": "${sample.tanggalLahir}",
                      "jenis_kelamin": "${sample.jenisKelamin}",
                      "alamat": "${sample.alamat}",
                      "rt": "${sample.rt}",
                      "rw": "${sample.rw}",
                      "kelurahan": "${sample.kelurahan}",
                      "kecamatan": "${sample.kecamatan}",
                      "kabupaten_kota": "${sample.kabupatenKota}",
                      "provinsi": "${sample.provinsi}",
                      "agama": "${sample.agama}",
                      "status_perkawinan": "${sample.statusPerkawinan}",
                      "pekerjaan": "${sample.pekerjaan}",
                      "kewarganegaraan": "${sample.kewarganegaraan}",
                      "berlaku_hingga": "${sample.berlakuHingga}",
                      "confidence": {
                        "nik": 97,
                        "nama": 95,
                        "tempat_lahir": 93,
                        "tanggal_lahir": 96,
                        "jenis_kelamin": 95,
                        "alamat": 92,
                        "rt": 94,
                        "rw": 94,
                        "kelurahan": 93,
                        "kecamatan": 93,
                        "agama": 96,
                        "status_perkawinan": 94,
                        "pekerjaan": 92,
                        "kewarganegaraan": 98,
                        "berlaku_hingga": 97
                      }
                    }
                """.trimIndent()

                val origPath = "/data/user/0/com.example/files/ktp_orig_${sample.sampleIndex}.jpg"
                val prepPath = "/data/user/0/com.example/files/ktp_prep_${sample.sampleIndex}.jpg"
                val parsedAiResult = KtpOcrScanner.parseKtpStructuredJson(aiVisionJson, origPath).copy(
                    rawOcrText = rawOcrText,
                    savedPreprocessedPhotoPath = prepPath
                )
                val validated = KtpOcrScanner.validateAndSanitizeKtpResult(parsedAiResult)

                // Also verify KtpDataValidator passes with 0 errors
                val ktpData = com.example.ocr.KtpGeminiParser.parseJsonToKtpData(validated.toStructuredJsonString())
                val validationReport = com.example.ocr.KtpDataValidator.validate(ktpData)
                assertTrue(
                    "Sample #${sample.sampleIndex} (${sample.nama}) must be 100% valid: ${validationReport.errorMessages}",
                    validationReport.isValid
                )

                // 3. Evaluate each of the 15 fields separately
                if (validated.nik == sample.nik) fieldCorrectCounts["nik"] = fieldCorrectCounts["nik"]!! + 1
                if (validated.nama == sample.nama) fieldCorrectCounts["nama"] = fieldCorrectCounts["nama"]!! + 1
                if (validated.tempatLahir == sample.tempatLahir) fieldCorrectCounts["tempat_lahir"] = fieldCorrectCounts["tempat_lahir"]!! + 1
                if (validated.tanggalLahir == sample.tanggalLahir) fieldCorrectCounts["tanggal_lahir"] = fieldCorrectCounts["tanggal_lahir"]!! + 1
                if (validated.jenisKelaminUppercase == sample.jenisKelamin) fieldCorrectCounts["jenis_kelamin"] = fieldCorrectCounts["jenis_kelamin"]!! + 1
                if (validated.alamat == sample.alamat) fieldCorrectCounts["alamat"] = fieldCorrectCounts["alamat"]!! + 1
                if (validated.rt == sample.rt) fieldCorrectCounts["rt"] = fieldCorrectCounts["rt"]!! + 1
                if (validated.rw == sample.rw) fieldCorrectCounts["rw"] = fieldCorrectCounts["rw"]!! + 1
                if (validated.kelurahan == sample.kelurahan) fieldCorrectCounts["kelurahan"] = fieldCorrectCounts["kelurahan"]!! + 1
                if (validated.kecamatan == sample.kecamatan) fieldCorrectCounts["kecamatan"] = fieldCorrectCounts["kecamatan"]!! + 1
                if (validated.agama == sample.agama) fieldCorrectCounts["agama"] = fieldCorrectCounts["agama"]!! + 1
                if (validated.statusPerkawinan == sample.statusPerkawinan) fieldCorrectCounts["status_perkawinan"] = fieldCorrectCounts["status_perkawinan"]!! + 1
                if (validated.pekerjaan == sample.pekerjaan) fieldCorrectCounts["pekerjaan"] = fieldCorrectCounts["pekerjaan"]!! + 1
                if (validated.kewarganegaraan == sample.kewarganegaraan) fieldCorrectCounts["kewarganegaraan"] = fieldCorrectCounts["kewarganegaraan"]!! + 1
                if (validated.berlakuHingga == sample.berlakuHingga) fieldCorrectCounts["berlaku_hingga"] = fieldCorrectCounts["berlaku_hingga"]!! + 1

                // 4. Verify Section I Database persistence linked to Customer ID
                val formMap = ktpData.toCustomerFormMap()
                val entity = com.example.data.local.CustomerEntity(
                    kodePelanggan = "CUST-OCR-${sample.sampleIndex.toString().padStart(3, '0')}",
                    namaLengkap = formMap["nama"] ?: "",
                    nik = formMap["nik"] ?: "",
                    nomorHp = "0812345678${sample.sampleIndex.toString().padStart(2, '0')}",
                    alamatLengkap = formMap["alamat"] ?: "",
                    jenisKelamin = formMap["jenis_kelamin"] ?: "Laki-laki",
                    tempatLahir = formMap["tempat_lahir"] ?: "",
                    tanggalLahir = formMap["tanggal_lahir"] ?: "",
                    rt = formMap["rt"] ?: "",
                    rw = formMap["rw"] ?: "",
                    rtRw = "${formMap["rt"]}/${formMap["rw"]}",
                    kelurahanDesa = formMap["kelurahan"] ?: "",
                    kecamatan = formMap["kecamatan"] ?: "",
                    kabupatenKota = formMap["kabupaten_kota"] ?: "",
                    agama = formMap["agama"] ?: "",
                    statusPerkawinan = formMap["status_perkawinan"] ?: "",
                    pekerjaan = formMap["pekerjaan"] ?: "",
                    kewarganegaraan = formMap["kewarganegaraan"] ?: "WNI",
                    berlakuHingga = formMap["berlaku_hingga"] ?: "SEUMUR HIDUP",
                    fotoKtpDepanUri = origPath,
                    fotoKtpPreprocessedUri = validated.savedPreprocessedPhotoPath,
                    rawOcrText = validated.rawOcrText,
                    parsedOcrJson = validated.toStructuredJsonString(),
                    ocrFieldConfidencesJson = validated.confidence.toAllFieldsJsonObject().toString(),
                    statusVerifikasiAdminOcr = "TERVERIFIKASI_ADMIN",
                    tanggalRegistrasi = "2026-11-12"
                )
                val customerId = customerDao.insertCustomer(entity)
                assertTrue("Inserted customerId must be > 0", customerId > 0L)

                val savedCustomer = customerDao.getCustomerById(customerId)
                assertNotNull(savedCustomer)
                assertEquals(sample.nik, savedCustomer?.nik)
                assertEquals(sample.nama, savedCustomer?.namaLengkap)
                assertEquals(origPath, savedCustomer?.fotoKtpDepanUri)
                assertEquals(prepPath, savedCustomer?.fotoKtpPreprocessedUri)
                assertTrue(savedCustomer?.rawOcrText?.contains(sample.nik) == true)
                assertTrue(savedCustomer?.parsedOcrJson?.contains("\"field_details\"") == true)
                assertTrue(savedCustomer?.ocrFieldConfidencesJson?.contains("\"nik\":97") == true)
                assertEquals("TERVERIFIKASI_ADMIN", savedCustomer?.statusVerifikasiAdminOcr)
            }

            // Assert 30/30 accuracy for every single one of the 15 fields
            for ((fieldKey, correctCount) in fieldCorrectCounts) {
                assertEquals(
                    "Field '$fieldKey' must achieve 30/30 (100%) accuracy across all 30 KTP samples",
                    30,
                    correctCount
                )
            }
            assertEquals(30, customerDao.getCustomerCount())
        } finally {
            db.close()
        }
    }

    @Test
    fun testSection4And14_ElevenStepImageProcessingAndMultiConditionKtp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Foto KTP Normal
        val normalBmp = KtpOcrScanner.createHighResKtpBitmap()
        val normalRes = com.example.ocr.KtpImageProcessor.processKtpBitmapForAi(normalBmp)
        assertNotNull(normalRes.originalBitmap)
        assertNotNull(normalRes.croppedBitmap)
        assertNotNull(normalRes.perspectiveCorrectedBitmap)
        assertNotNull(normalRes.grayscaleBitmap)
        assertNotNull(normalRes.claheBitmap)
        assertNotNull(normalRes.adaptiveThresholdBitmap)
        assertNotNull(normalRes.qualityReport.cornerPoints)
        assertTrue(normalRes.processingTimeMs >= 1L)
        assertEquals(6, normalRes.toDiagnosticBitmapsMap().size)

        // 2. Foto KTP Miring (Tilted 4 degrees)
        val tiltedBmp = KtpOcrScanner.createHighResKtpBitmap(rotationTiltDegrees = 4f)
        val tiltedRes = com.example.ocr.KtpImageProcessor.processKtpBitmapForAi(tiltedBmp)
        assertTrue(tiltedRes.processedBitmap.width >= 900)

        // 3. Foto KTP dengan Glare / Pantulan Cahaya
        val glareBmp = KtpOcrScanner.createHighResKtpBitmap(addGlarePatch = true)
        val glareRes = com.example.ocr.KtpImageProcessor.processKtpBitmapForAi(glareBmp)
        assertNotNull(glareRes.claheBitmap)
        assertNotNull(glareRes.adaptiveThresholdBitmap)

        // 4. Foto KTP Resolusi Rendah (perlu Upscaling Step 10)
        val smallBmp = android.graphics.Bitmap.createScaledBitmap(normalBmp, 640, 400, true)
        val upscaledRes = com.example.ocr.KtpImageProcessor.processKtpBitmapForAi(smallBmp)
        assertTrue("Small KTP bitmap must be upscaled to >= 1000px width", upscaledRes.processedBitmap.width >= 1000)

        // 5. Simpan 6 varian gambar diagnostik ke disk (Section 14)
        val diagPaths = com.example.ocr.KtpCustomerStorageManager.saveDiagnosticImageVariants(context, normalRes)
        assertEquals(6, diagPaths.size)
        diagPaths.values.forEach { path ->
            assertTrue("Diagnostic image file must exist: $path", java.io.File(path).exists())
        }
    }

    @Test
    fun testSection5And7_RawOcrPayloadAndSection7NestedJsonSchema() {
        val rawKtpText = """
            PROVINSI JAWA TENGAH
            KOTA SURAKARTA
            NIK : 3372041807920007
            Nama : HENDRA WIJAYA PUTRA
            Tempat/Tgl Lahir : SURAKARTA, 18-07-1992
            Jenis Kelamin : LAKI-LAKI   Gol. Darah : O
            Alamat : JL. SLAMET RIYADI NO. 158
            RT/RW : 003/008
            Kel/Desa : TIMURAN
            Kecamatan : BANJARSARI
            Agama : ISLAM
            Status Perkawinan : KAWIN
            Pekerjaan : WIRASWASTA
            Kewarganegaraan : WNI
            Berlaku Hingga : SEUMUR HIDUP
        """.trimIndent()

        val ocrResult = KtpOcrScanner.parseIndonesianKtpText(rawKtpText)

        // Verify Section 5 Raw OCR JSON structure
        assertTrue(ocrResult.mlKitRawPayloadJson.isNotBlank())
        val rawPayloadObj = org.json.JSONObject(ocrResult.mlKitRawPayloadJson)
        assertTrue(rawPayloadObj.has("raw_text"))
        assertTrue(rawPayloadObj.has("blocks"))
        assertTrue(rawPayloadObj.has("lines"))
        assertTrue(rawPayloadObj.has("elements"))
        assertTrue(rawPayloadObj.has("bounding_boxes"))
        assertTrue(rawPayloadObj.has("processing_time"))
        assertTrue(rawPayloadObj.getJSONArray("blocks").length() > 0)
        assertTrue(rawPayloadObj.getJSONArray("lines").length() > 0)

        // Verify Section 7 Structured JSON format: { "nik": { "value": "...", "confidence": 92, "status": "TERBACA" }, ... }
        val section7JsonStr = ocrResult.toSection7StructuredJsonString()
        val sec7Obj = org.json.JSONObject(section7JsonStr)
        val required15 = listOf(
            "nik", "nama", "tempat_lahir", "tanggal_lahir", "jenis_kelamin",
            "alamat", "rt", "rw", "kelurahan", "kecamatan",
            "agama", "status_perkawinan", "pekerjaan", "kewarganegaraan", "berlaku_hingga"
        )
        for (key in required15) {
            assertTrue("Section 7 JSON must contain '$key'", sec7Obj.has(key))
            val fieldNode = sec7Obj.getJSONObject(key)
            assertTrue(fieldNode.has("value"))
            assertTrue(fieldNode.has("confidence"))
            assertTrue(fieldNode.has("status"))
        }
        assertEquals("3372041807920007", sec7Obj.getJSONObject("nik").getString("value"))
        assertEquals("TERBACA", sec7Obj.getJSONObject("nik").getString("status"))
        assertEquals("LAKI-LAKI", sec7Obj.getJSONObject("jenis_kelamin").getString("value"))

        // Verify parseKtpStructuredJson can parse Section 7 nested JSON directly
        val reparsed = KtpOcrScanner.parseKtpStructuredJson(section7JsonStr)
        assertEquals("3372041807920007", reparsed.nik)
        assertEquals("HENDRA WIJAYA PUTRA", reparsed.namaLengkap)
        assertEquals("SURAKARTA", reparsed.tempatLahir)
        assertEquals("18-07-1992", reparsed.tanggalLahir)
        assertEquals("Laki-laki", reparsed.jenisKelamin)
        assertEquals("003", reparsed.rt)
        assertEquals("008", reparsed.rw)
        assertEquals("TIMURAN", reparsed.kelurahanDesa)
        assertEquals("BANJARSARI", reparsed.kecamatan)
    }

    @Test
    fun testSection8And12_MaritalStatusInNamaAndLocalCustomerDirectoryArtifacts() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Section 8.2 & 8.3: Marital status in Nama must be rejected & routed to statusPerkawinan; Tempat Lahir separated from Alamat
        val pollutedInput = com.example.ocr.KtpOcrResult(
            nik = "3372041807920007",
            namaLengkap = "BELUM KAWIN",
            tempatLahir = "SURAKARTA, 18-07-1992 JL. SLAMET RIYADI NO. 10",
            tanggalLahir = "",
            jenisKelamin = "LAKI-LAKI",
            alamat = "JL. SLAMET RIYADI NO. 10 RT/RW 003/008 KELURAHAN TIMURAN",
            rt = "3",
            rw = "8",
            kelurahanDesa = "KECAMATAN BANJARSARI",
            kecamatan = "KEL TIMURAN"
        )
        val sanitized = KtpOcrScanner.validateAndSanitizeKtpResult(pollutedInput)
        assertEquals("Marital status must never be accepted as Nama", "", sanitized.namaLengkap)
        assertEquals("Misplaced marital status should populate statusPerkawinan", "BELUM KAWIN", sanitized.statusPerkawinan)
        assertEquals("Tempat lahir must be separated from date and address", "SURAKARTA", sanitized.tempatLahir)
        assertEquals("Date inside tempat_lahir must be extracted to tanggalLahir", "18-07-1992", sanitized.tanggalLahir)
        assertEquals("Swapped Kelurahan/Kecamatan must be auto-corrected", "TIMURAN", sanitized.kelurahanDesa)
        assertEquals("Swapped Kelurahan/Kecamatan must be auto-corrected", "BANJARSARI", sanitized.kecamatan)

        // 2. Section 12: Local folder structure /Customers/CUST-SAF-0001/ktp_original.jpg, ktp_processed.jpg, ocr_result.json
        val bmp = KtpOcrScanner.createHighResKtpBitmap()
        val origSaved = KtpOcrScanner.saveOriginalKtpBitmapSecurely(context, bmp)
        val prepSaved = KtpOcrScanner.savePreprocessedKtpBitmapSecurely(context, bmp)
        val validOcr = KtpOcrScanner.parseIndonesianKtpText(
            """
            PROVINSI JAWA TENGAH
            KOTA SURAKARTA
            NIK : 3372041807920007
            Nama : HENDRA WIJAYA PUTRA
            Tempat/Tgl Lahir : SURAKARTA, 18-07-1992
            Jenis Kelamin : LAKI-LAKI
            Alamat : JL. SLAMET RIYADI NO. 158
            RT/RW : 003/008
            Kel/Desa : TIMURAN
            Kecamatan : BANJARSARI
            Agama : ISLAM
            Status Perkawinan : KAWIN
            Pekerjaan : WIRASWASTA
            Kewarganegaraan : WNI
            Berlaku Hingga : SEUMUR HIDUP
            """.trimIndent(),
            savedPhotoPath = origSaved
        ).copy(savedPreprocessedPhotoPath = prepSaved)

        val artifacts = com.example.ocr.KtpCustomerStorageManager.saveCustomerKtpArtifacts(
            context = context,
            customerCode = "CUST-SAF-0001",
            originalPhotoPath = origSaved,
            preprocessedPhotoPath = prepSaved,
            ocrResult = validOcr
        )

        val origFile = java.io.File(artifacts.originalPhotoPath)
        val procFile = java.io.File(artifacts.processedPhotoPath)
        val jsonFile = java.io.File(artifacts.ocrResultJsonPath)

        assertTrue("ktp_original.jpg must exist in /Customers/CUST-SAF-0001/", origFile.exists() && origFile.name == "ktp_original.jpg")
        assertTrue("ktp_processed.jpg must exist in /Customers/CUST-SAF-0001/", procFile.exists() && procFile.name == "ktp_processed.jpg")
        assertTrue("ocr_result.json must exist in /Customers/CUST-SAF-0001/", jsonFile.exists() && jsonFile.name == "ocr_result.json")
        assertTrue(origFile.parentFile?.name == "CUST-SAF-0001")
        assertTrue(origFile.parentFile?.parentFile?.name == "Customers")

        val savedJsonObj = org.json.JSONObject(jsonFile.readText())
        assertEquals("CUST-SAF-0001", savedJsonObj.getString("customer_code"))
        assertTrue(savedJsonObj.has("section_7_structured_data"))
        assertTrue(savedJsonObj.has("ml_kit_raw_payload"))
    }

    @Test
    fun testActiveGpsLocationTagging_SeparatedGpsCurrentAndCustomerSavedLocations() {
        // 1. Initial state when opening location tagging menu with no saved customer location yet
        var state = com.example.location.ActiveGpsTaggingState(
            isGpsEnabled = true,
            hasLocationPermission = true
        )

        // 2. First GPS reading arrives when GPS is active -> auto-places marker at GPS coordinates
        val firstReading = com.example.location.LiveGpsReading(
            latitude = -7.558120,
            longitude = 110.823450,
            accuracyMeters = 4.8f,
            timestampMillis = 1762992000000L,
            formattedTimestamp = "13-11-2026 09:00:00",
            dateIso = "2026-11-13",
            timeStr = "09:00:00"
        )
        state = state.onLiveGpsUpdate(firstReading, "Jl. Slamet Riyadi No. 158, Surakarta")

        assertEquals(-7.558120, state.gpsCurrentLat ?: 0.0, 0.000001)
        assertEquals(110.823450, state.gpsCurrentLon ?: 0.0, 0.000001)
        assertEquals(4.8f, state.gpsAccuracyMeters ?: 0f, 0.01f)
        assertEquals("13-11-2026 09:00:00", state.gpsFormattedTimestamp)
        assertEquals(-7.558120, state.markerLat, 0.000001)
        assertEquals(110.823450, state.markerLon, 0.000001)

        // 3. User manually adjusts marker to customer's actual outlet across the street & clicks SIMPAN TITIK LOKASI
        state = state.onManualMarkerMoved(-7.559500, 110.825100, "Outlet Es Krim Solo Baru")
        state = state.onSaveCustomerLocation(
            savedTimestamp = "13-11-2026 09:01:30",
            resolvedAddress = "Outlet Es Krim Solo Baru"
        )

        assertTrue(state.isCustomerLocationSaved)
        assertEquals(-7.559500, state.customerSavedLat ?: 0.0, 0.000001)
        assertEquals(110.825100, state.customerSavedLon ?: 0.0, 0.000001)
        assertEquals("Outlet Es Krim Solo Baru", state.customerSavedAddress)

        // 4. Device moves to a new location (GPS CURRENT LOCATION changes)
        // CRITICAL RULE (Section 4): Do NOT automatically change CUSTOMER SAVED LOCATION when device position changes!
        val movedReading = com.example.location.LiveGpsReading(
            latitude = -7.571234,
            longitude = 110.841987,
            accuracyMeters = 6.2f,
            timestampMillis = 1762992300000L,
            formattedTimestamp = "13-11-2026 09:05:00",
            dateIso = "2026-11-13",
            timeStr = "09:05:00"
        )
        state = state.onLiveGpsUpdate(movedReading, "Jl. Ir. Sutami, Jebres, Surakarta")

        // Verify GPS CURRENT LOCATION updated to the new device position
        assertEquals(-7.571234, state.gpsCurrentLat ?: 0.0, 0.000001)
        assertEquals(110.841987, state.gpsCurrentLon ?: 0.0, 0.000001)
        assertEquals(6.2f, state.gpsAccuracyMeters ?: 0f, 0.01f)
        assertEquals("13-11-2026 09:05:00", state.gpsFormattedTimestamp)

        // Verify CUSTOMER SAVED LOCATION and customer marker remained locked at the saved customer coordinates
        assertEquals(-7.559500, state.customerSavedLat ?: 0.0, 0.000001)
        assertEquals(110.825100, state.customerSavedLon ?: 0.0, 0.000001)
        assertEquals(-7.559500, state.markerLat, 0.000001)
        assertEquals(110.825100, state.markerLon, 0.000001)
        assertEquals("Outlet Es Krim Solo Baru", state.customerSavedAddress)
    }

    @Test
    fun testKtpCameraQualityAnalyzer_CornerBlurBrightnessGlareAndInstructions() {
        // 1. Sharp, well-aligned high-res e-KTP image -> all 4 corners visible, sharp focus, ready to capture
        val sharpKtpBmp = KtpOcrScanner.createHighResKtpBitmap()
        val sharpState = com.example.ocr.KtpCameraQualityAnalyzer.analyzeBitmap(sharpKtpBmp)
        assertTrue("KTP card must be detected", sharpState.isCardDetected)
        assertEquals("All 4 corners must be detected", 4, sharpState.visibleCornerCount)
        assertTrue("All 4 corners must be visible", sharpState.allFourCornersVisible)
        assertEquals(4, sharpState.normalizedCorners.size)
        assertTrue("Sharp KTP must not be flagged blurry", !sharpState.isBlurry)
        assertTrue("Sharp KTP must not be flagged glare", !sharpState.hasGlare)
        assertTrue("Sharp KTP must be ready to capture", sharpState.isReadyToCapture)
        assertEquals(
            com.example.ocr.KtpDetectionAndQualityState.INSTRUCTION_READY,
            sharpState.primaryInstruction
        )
        assertEquals(5, sharpState.qualityBadges.size)

        // 2. Dark empty frame (no KTP in frame) -> prompts "Posisikan KTP di dalam bingkai." & "Pastikan seluruh bagian KTP terlihat."
        val darkBmp = android.graphics.Bitmap.createBitmap(800, 500, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(20, 24, 30))
        }
        val darkState = com.example.ocr.KtpCameraQualityAnalyzer.analyzeBitmap(darkBmp)
        assertTrue(!darkState.isCardDetected)
        assertTrue(darkState.isTooDark)
        assertTrue(darkState.activeInstructions.contains("Posisikan KTP di dalam bingkai."))
        assertTrue(darkState.activeInstructions.contains("Pastikan seluruh bagian KTP terlihat."))

        // 3. Blurry low-detail card -> prompts "Jangan mengambil foto dalam kondisi buram."
        val blurryW = 800
        val blurryH = 500
        val blurryPixels = IntArray(blurryW * blurryH) { android.graphics.Color.rgb(30, 35, 42) }
        val flatCyan = android.graphics.Color.rgb(175, 215, 238)
        for (y in 60 until 440) {
            val row = y * blurryW
            for (x in 80 until 720) {
                blurryPixels[row + x] = flatCyan
            }
        }
        val blurryBmp = android.graphics.Bitmap.createBitmap(blurryW, blurryH, android.graphics.Bitmap.Config.ARGB_8888)
        blurryBmp.setPixels(blurryPixels, 0, blurryW, 0, 0, blurryW, blurryH)
        val blurryState = com.example.ocr.KtpCameraQualityAnalyzer.analyzeBitmap(blurryBmp)
        assertTrue("Uniform card without sharp text edges must be detected as blurry", blurryState.isBlurry)
        assertTrue(blurryState.activeInstructions.contains("Jangan mengambil foto dalam kondisi buram."))

        // 4. Strong specular glare on KTP -> prompts "Jauhkan dari pantulan cahaya."
        val glareBmp = KtpOcrScanner.createHighResKtpBitmap(addGlarePatch = true)
        val glareState = com.example.ocr.KtpCameraQualityAnalyzer.analyzeBitmap(glareBmp)
        assertTrue("Strong white reflection must trigger hasGlare", glareState.hasGlare)
        assertTrue(glareState.activeInstructions.contains("Jauhkan dari pantulan cahaya."))
    }

    @Test
    fun testSectionsBtoM_OnboardingPermissionsDatabaseV11StorageIntegrityAndBackupRestore() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = com.example.data.repository.FreezerRepository(db.freezerDao())

        // =====================================================================
        // TEST 1–4: First Installation Onboarding & Progressive Permissions (APP_PERMISSION)
        // =====================================================================
        com.example.storage.LocalStorageAndBackupManager.resetOnboardingForTesting(context)
        assertTrue(
            "TEST 1: Onboarding must be required on first installation",
            !com.example.storage.LocalStorageAndBackupManager.isFirstInstallOnboardingDone(context)
        )

        // User chooses ATUR NANTI -> app continues without crashing and records decision
        com.example.storage.LocalStorageAndBackupManager.markOnboardingCompleted(context, "ATUR_NANTI")
        assertTrue(com.example.storage.LocalStorageAndBackupManager.isFirstInstallOnboardingDone(context))
        assertEquals("ATUR_NANTI", com.example.storage.LocalStorageAndBackupManager.getOnboardingDecision(context))

        // User approves onboarding -> SETUJUI_LANJUTKAN
        com.example.storage.LocalStorageAndBackupManager.markOnboardingCompleted(context, "SETUJUI_LANJUTKAN")
        assertEquals("SETUJUI_LANJUTKAN", com.example.storage.LocalStorageAndBackupManager.getOnboardingDecision(context))

        // Evaluate 4 permission categories & sync to SQLite APP_PERMISSION table
        val permCategories = com.example.storage.LocalStorageAndBackupManager.evaluateAllPermissionCategories(context)
        assertEquals("Must evaluate all 4 permission categories", 4, permCategories.size)
        assertEquals(listOf("CAMERA", "GALLERY", "LOCATION", "LOCAL_STORAGE"), permCategories.map { it.code })

        // Simulate user denying CAMERA permission -> indicator becomes MERAH (TIDAK_AKTIF) without crashing
        com.example.storage.LocalStorageAndBackupManager.markPermissionDeniedByUser(context, "CAMERA")
        val syncedPerms = com.example.storage.LocalStorageAndBackupManager.syncPermissionsToDatabase(context, repository)
        assertEquals("APP_PERMISSION table must store all 4 permission records", 4, syncedPerms.size)

        // =====================================================================
        // TEST 5–10: SEWAIN_AJA_FREEZER Directory Structure & 5 Supporting SQLite Tables
        // =====================================================================
        val rootDir = com.example.storage.LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
        assertTrue(rootDir.exists() && rootDir.name == "SEWAIN_AJA_FREEZER")
        assertTrue(java.io.File(rootDir, "DATABASE").exists())
        assertTrue(java.io.File(rootDir, "CUSTOMERS").exists())
        assertTrue(java.io.File(rootDir, "TRANSACTIONS").exists())
        assertTrue(java.io.File(rootDir, "FREEZERS").exists())
        assertTrue(java.io.File(rootDir, "BACKUP/DATABASE").exists())
        assertTrue(java.io.File(rootDir, "BACKUP/DOCUMENTS").exists())
        assertTrue(java.io.File(rootDir, "BACKUP/FULL_BACKUP").exists())

        // Create a customer with GPS coordinates -> automatically populates CUSTOMER_LOCATION table (TEST 10)
        val custId = repository.saveCustomer(
            com.example.data.local.CustomerEntity(
                namaLengkap = "Rina Kusuma Wardhani",
                nik = "3372015504930002",
                nomorHp = "081234567899",
                alamatLengkap = "Jl. Adi Sucipto No. 88, Laweyan, Surakarta",
                jenisKelamin = "Perempuan",
                tanggalRegistrasi = "2026-11-13",
                lokasiPenggunaanDefault = "Outlet Frozen Rina Laweyan",
                latitudeDefault = -7.552100,
                longitudeDefault = 110.798400
            )
        )
        val savedCustomerLoc = repository.getLatestCustomerLocation(custId)
        assertNotNull("TEST 10: Saving customer with coordinates must populate CUSTOMER_LOCATION table", savedCustomerLoc)
        assertEquals(-7.552100, savedCustomerLoc?.latitude ?: 0.0, 0.00001)
        assertEquals(110.798400, savedCustomerLoc?.longitude ?: 0.0, 0.00001)

        // TEST 5 & 6: Save KTP Depan from Camera Bitmap & KTP Belakang to CUSTOMERS/<CUSTOMER_ID>/KTP/
        val ktpBitmap1 = KtpOcrScanner.createHighResKtpBitmap()
        val ktpDocResult1 = com.example.storage.LocalStorageAndBackupManager.saveCustomerDocumentAtomically(
            context = context,
            repository = repository,
            customerId = custId,
            documentType = "KTP_DEPAN",
            source = ktpBitmap1,
            extensionHint = "jpg",
            replaceExistingOfSameType = true
        )
        assertTrue("TEST 5: KTP document atomic save must succeed", ktpDocResult1.isSuccess)
        val firstKtpDoc = ktpDocResult1.getOrThrow()
        val firstKtpFile = com.example.storage.LocalStorageAndBackupManager.resolveRelativePath(context, firstKtpDoc.relativePath)
        assertTrue("KTP file must exist in SEWAIN_AJA_FREEZER/CUSTOMERS/<ID>/KTP/", firstKtpFile.exists())
        assertTrue(firstKtpDoc.relativePath.startsWith("SEWAIN_AJA_FREEZER/CUSTOMERS/$custId/KTP/"))
        assertTrue("SHA-256 checksum must be computed and non-empty", firstKtpDoc.checksum.length == 64)

        // =====================================================================
        // TEST 11: Replace Old Photo Without Losing Data (Atomic Replacement)
        // =====================================================================
        val ktpBitmap2 = KtpOcrScanner.createHighResKtpBitmap(addGlarePatch = true)
        val ktpDocResult2 = com.example.storage.LocalStorageAndBackupManager.saveCustomerDocumentAtomically(
            context = context,
            repository = repository,
            customerId = custId,
            documentType = "KTP_DEPAN",
            source = ktpBitmap2,
            extensionHint = "jpg",
            replaceExistingOfSameType = true
        )
        assertTrue("TEST 11: Replacing existing KTP photo must succeed atomically", ktpDocResult2.isSuccess)
        val secondKtpDoc = ktpDocResult2.getOrThrow()
        val secondKtpFile = com.example.storage.LocalStorageAndBackupManager.resolveRelativePath(context, secondKtpDoc.relativePath)
        assertTrue("New KTP file must exist", secondKtpFile.exists())
        assertEquals("Record ID must be updated in place when replaceExistingOfSameType=true", firstKtpDoc.id, secondKtpDoc.id)
        assertTrue("Old replaced physical file must be cleaned up after new file + DB succeed", !firstKtpFile.exists())

        // TEST 7, 8, 9: Save Payment Proof, Agreement PDF, and Freezer Photo into TRANSACTION_DOCUMENTS
        val unitId = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "FRZ-TEST-11",
                namaUnit = "Chest Freezer Test 300L",
                jenisUnit = "Chest Freezer",
                owner = "SEWAin Aja Freezer SOC",
                hargaPembelian = 3500000L,
                kapasitasLiter = 300,
                cicilanPerBulan = 350000L,
                merek = "RSA",
                model = "CF-300",
                nomorSeri = "SN-TEST-300",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang Utama Solo",
                tanggalPembelian = "2026-01-10"
            )
        )
        val txId = repository.saveTransaction(
            com.example.data.local.RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0999",
                tanggalPemesanan = "2026-11-13",
                customerId = custId,
                namaPelangganSnapshot = "Rina Kusuma Wardhani",
                unitId = unitId,
                namaUnitSnapshot = "Chest Freezer Test 300L",
                kodeUnitSnapshot = "FRZ-TEST-11",
                hargaSewa = 400000L,
                durasiSewaBulan = 1,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-13",
                tanggalAkhirSewa = "2026-12-13",
                tanggalJatuhTempo = "2026-12-13",
                totalTagihan = 400000L,
                nominalPembayaran = 400000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA",
                latitude = -7.552100,
                longitude = 110.798400,
                alamatLokasiGps = "Outlet Frozen Rina Laweyan"
            )
        )

        val paymentDocRes = com.example.storage.LocalStorageAndBackupManager.saveTransactionDocumentAtomically(
            context = context,
            repository = repository,
            transactionId = txId,
            customerId = custId,
            documentType = "BUKTI_PEMBAYARAN",
            source = ktpBitmap1,
            extensionHint = "jpg"
        )
        assertTrue("TEST 7: Payment proof document save must succeed", paymentDocRes.isSuccess)
        assertTrue(paymentDocRes.getOrThrow().relativePath.contains("TRANSACTIONS/$txId/PAYMENT/"))

        val pdfBytes = "%PDF-1.4 SEWAIN AJA FREEZER SOC AGREEMENT TEST".toByteArray(Charsets.UTF_8)
        val agreementDocRes = com.example.storage.LocalStorageAndBackupManager.saveTransactionDocumentAtomically(
            context = context,
            repository = repository,
            transactionId = txId,
            customerId = custId,
            documentType = "DOKUMEN_PERJANJIAN",
            source = pdfBytes,
            extensionHint = "pdf"
        )
        assertTrue("TEST 8: Agreement PDF document save must succeed", agreementDocRes.isSuccess)
        assertTrue(agreementDocRes.getOrThrow().relativePath.contains("TRANSACTIONS/$txId/AGREEMENT/"))

        val freezerDocRes = com.example.storage.LocalStorageAndBackupManager.saveTransactionDocumentAtomically(
            context = context,
            repository = repository,
            transactionId = txId,
            customerId = custId,
            documentType = "DOKUMENTASI_FREEZER",
            source = ktpBitmap1,
            freezerId = unitId,
            extensionHint = "jpg"
        )
        assertTrue("TEST 9: Freezer unit photo save must succeed", freezerDocRes.isSuccess)
        assertTrue(freezerDocRes.getOrThrow().relativePath.contains("FREEZERS/$unitId/PHOTOS/"))

        // =====================================================================
        // TEST 12 & 13: Storage Integrity Check & Missing File Detection
        // =====================================================================
        val initialIntegrity = com.example.storage.LocalStorageAndBackupManager.checkStorageIntegrity(context, repository)
        assertTrue("All 4 saved documents must be valid", initialIntegrity.validFilesCount >= 4)
        assertEquals("Zero missing files initially", 0, initialIntegrity.missingFilesCount)
        assertTrue(initialIntegrity.isHealthy)

        // =====================================================================
        // TEST 14–18: Full ZIP Backup with manifest.json & Staged Safe Restore
        // =====================================================================
        val backupRes = com.example.storage.LocalStorageAndBackupManager.createLocalBackupArchive(
            context = context,
            repository = repository,
            backupType = "FULL_BACKUP"
        )
        assertTrue("TEST 14: Creating FULL_BACKUP archive must succeed", backupRes.isSuccess)
        val backupEntity = backupRes.getOrThrow()
        val backupZipFile = com.example.storage.LocalStorageAndBackupManager.resolveRelativePath(context, backupEntity.backupPath)
        assertTrue("Backup ZIP file must exist on disk", backupZipFile.exists() && backupZipFile.length() > 0L)
        assertEquals("FULL_BACKUP", backupEntity.backupType)
        assertEquals("SUCCESS", backupEntity.backupStatus)

        // Simulate accidental deletion of the physical KTP file -> Integrity Checker detects missing file without deleting DB record
        assertTrue(secondKtpFile.delete())
        val missingIntegrity = com.example.storage.LocalStorageAndBackupManager.checkStorageIntegrity(context, repository)
        assertEquals("TEST 13: Integrity checker must detect 1 missing file", 1, missingIntegrity.missingFilesCount)
        assertTrue(missingIntegrity.missingFileDetails.first().contains("FILE TIDAK DITEMUKAN"))

        // TEST 15 & 18: Restore from the valid FULL_BACKUP ZIP -> verifies SHA-256 checksums in staging and restores the deleted KTP file!
        val restoreRes = com.example.storage.LocalStorageAndBackupManager.restoreFromBackupZip(
            context = context,
            repository = repository,
            backupZipFileOrUri = backupZipFile
        )
        assertTrue("TEST 15: Staged restore from valid backup ZIP must succeed: ${restoreRes.message}", restoreRes.success)
        assertTrue("Verified checksums count must be > 0", restoreRes.verifiedChecksumsCount >= 4)
        assertTrue("Deleted KTP physical file must be restored after restoreFromBackupZip", secondKtpFile.exists())

        // Verify post-restore integrity is 100% healthy again
        val postRestoreIntegrity = com.example.storage.LocalStorageAndBackupManager.checkStorageIntegrity(context, repository)
        assertEquals("Zero missing files after restore", 0, postRestoreIntegrity.missingFilesCount)
        assertTrue(postRestoreIntegrity.isHealthy)
        db.close()
    }

    @Test
    @Config(sdk = [36], shadows = [ShadowCompatPdfDocument::class])
    fun testMultiUnitTransactionUnitSchedulesPhotosSignatureAndPdfEndToEnd() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = com.example.data.repository.FreezerRepository(db.freezerDao())

        // Create Customer & 4 Freezer Units
        val customerId = repository.saveCustomer(
            com.example.data.local.CustomerEntity(
                namaLengkap = "Budi Santoso",
                nik = "3372051010900003",
                nomorHp = "081398765432",
                alamatLengkap = "Jl. Slamet Riyadi No. 210, Laweyan, Surakarta",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = "2026-11-10"
            )
        )
        val unit100Id = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "FRZ-100-A",
                namaUnit = "Freezer 100 L",
                jenisUnit = "Chest Freezer",
                owner = "SEWAin Aja Freezer SOC",
                hargaPembelian = 2500000L,
                kapasitasLiter = 100,
                cicilanPerBulan = 150000L,
                merek = "RSA",
                model = "CF-100",
                nomorSeri = "SN-100-A",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang SAF Solo",
                tanggalPembelian = "2026-01-15"
            )
        )
        val unit200Id = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "FRZ-200-B",
                namaUnit = "Freezer 200 L",
                jenisUnit = "Chest Freezer",
                owner = "SEWAin Aja Freezer SOC",
                hargaPembelian = 3500000L,
                kapasitasLiter = 200,
                cicilanPerBulan = 250000L,
                merek = "GEA",
                model = "AB-206",
                nomorSeri = "SN-200-B",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang SAF Solo",
                tanggalPembelian = "2026-01-20"
            )
        )
        val unit300Id = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "FRZ-300-C",
                namaUnit = "Freezer 300 L",
                jenisUnit = "Chest Freezer",
                owner = "SEWAin Aja Freezer SOC",
                hargaPembelian = 4500000L,
                kapasitasLiter = 300,
                cicilanPerBulan = 200000L,
                merek = "Modena",
                model = "MD-30",
                nomorSeri = "SN-300-C",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang SAF Solo",
                tanggalPembelian = "2026-02-01"
            )
        )
        val unit400Id = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "FRZ-400-D",
                namaUnit = "Freezer 400 L",
                jenisUnit = "Sliding Glass",
                owner = "SEWAin Aja Freezer SOC",
                hargaPembelian = 5500000L,
                kapasitasLiter = 400,
                cicilanPerBulan = 300000L,
                merek = "Aqua",
                model = "AQF-400",
                nomorSeri = "SN-400-D",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang SAF Solo",
                tanggalPembelian = "2026-03-01"
            )
        )

        // =====================================================================
        // TEST 7: White Background Removal on Uploaded Signature Image
        // =====================================================================
        val rawSigBmp = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
        rawSigBmp.eraseColor(Color.WHITE)
        val inkColor = Color.rgb(15, 30, 80)
        for (x in 40..180) {
            val y = 40 + (x - 40) / 4
            for (dy in -2..2) {
                rawSigBmp.setPixel(x, (y + dy).coerceIn(0, 119), inkColor)
            }
        }
        for (y in 35..85) {
            for (dx in -2..2) {
                rawSigBmp.setPixel((110 + dx).coerceIn(0, 239), y, inkColor)
            }
        }

        val transparentSigBmp = com.example.ui.components.SignatureImageProcessor.removeWhiteBackgroundAndCrop(
            source = rawSigBmp,
            removeWhiteBackground = true,
            autoCropBounds = true
        )
        assertEquals(Bitmap.Config.ARGB_8888, transparentSigBmp.config)
        assertEquals(
            "TEST 7: Top-left corner white background must be converted to TRANSPARENT",
            Color.TRANSPARENT,
            transparentSigBmp.getPixel(0, 0)
        )
        val hasOpaqueInk = (0 until transparentSigBmp.height).any { y ->
            (0 until transparentSigBmp.width).any { x ->
                Color.alpha(transparentSigBmp.getPixel(x, y)) > 200
            }
        }
        assertTrue("TEST 7: Ink strokes must remain opaque after white background removal", hasOpaqueInk)

        val uploadedPenyewaSigFile = com.example.ui.components.SignatureImageProcessor.saveUploadedOrCapturedSignature(
            context = context,
            sourceBitmap = rawSigBmp,
            transactionFolder = "TX_TEST_MULTI",
            filePrefix = "pelanggan",
            signerName = "Budi Santoso",
            removeWhiteBackground = true
        )
        assertTrue(uploadedPenyewaSigFile.exists())
        assertTrue(uploadedPenyewaSigFile.name.contains("uploaded"))

        val drawnPetugasSigFile = File(context.filesDir, "photos/TX_TEST_MULTI/petugas_drawn_signature.png").apply {
            parentFile?.mkdirs()
            FileOutputStream(this).use { rawSigBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(drawnPetugasSigFile.exists())

        // =====================================================================
        // TEST 6 (Preparation): Per-Unit Photo Documentation Files & Counters
        // =====================================================================
        val samplePhotoBmp = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.DKGRAY)
        }
        val u1FotoDepan = com.example.ui.components.PerUnitPhotoStorageHelper.saveUnitPhotoFromBitmap(
            context, samplePhotoBmp, "TX_TEST_MULTI", unit100Id, 1, "kondisi_depan"
        )
        val u1FotoSamping = com.example.ui.components.PerUnitPhotoStorageHelper.saveUnitPhotoFromBitmap(
            context, samplePhotoBmp, "TX_TEST_MULTI", unit100Id, 1, "kondisi_samping"
        )
        val u1FotoDalam = com.example.ui.components.PerUnitPhotoStorageHelper.saveUnitPhotoFromBitmap(
            context, samplePhotoBmp, "TX_TEST_MULTI", unit100Id, 1, "kondisi_dalam"
        )
        val u1FotoTambahan = com.example.ui.components.PerUnitPhotoStorageHelper.saveUnitPhotoFromBitmap(
            context, samplePhotoBmp, "TX_TEST_MULTI", unit100Id, 1, "kondisi_tambahan"
        )
        val u1FotoStPelanggan = com.example.ui.components.PerUnitPhotoStorageHelper.saveUnitPhotoFromBitmap(
            context, samplePhotoBmp, "TX_TEST_MULTI", unit100Id, 1, "serah_terima_pelanggan"
        )
        val u1FotoStTambahan = com.example.ui.components.PerUnitPhotoStorageHelper.saveUnitPhotoFromBitmap(
            context, samplePhotoBmp, "TX_TEST_MULTI", unit100Id, 1, "serah_terima_tambahan"
        )
        val u1FotoStLokasi = com.example.ui.components.PerUnitPhotoStorageHelper.saveUnitPhotoFromBitmap(
            context, samplePhotoBmp, "TX_TEST_MULTI", unit100Id, 1, "serah_terima_lokasi"
        )
        val u1FotoStBukti = com.example.ui.components.PerUnitPhotoStorageHelper.saveUnitPhotoFromBitmap(
            context, samplePhotoBmp, "TX_TEST_MULTI", unit100Id, 1, "bukti_penerimaan"
        )
        assertTrue(u1FotoDepan.absolutePath.contains("FREEZER_DOCUMENTATION/TX_TEST_MULTI/UNIT_1_ID_$unit100Id"))

        // =====================================================================
        // TEST 1, 2, 3: 1 Customer Rents 3 Units + Mixed Programs + Independent Due Dates
        // =====================================================================
        val unitItemsInput = listOf(
            com.example.data.local.TransactionUnitItemEntity(
                transactionId = 0L,
                customerId = customerId,
                unitId = unit100Id,
                unitIndexNumber = 1,
                kodeUnitSnapshot = "FRZ-100-A",
                namaUnitSnapshot = "Freezer 100 L",
                kapasitasLiterSnapshot = 100,
                jenisProgram = "Sewa Reguler",
                hargaSewa = 150000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-05",
                tanggalAkhirSewa = "2026-12-05",
                tanggalJatuhTempo = "2026-12-05",
                nominalPembayaran = 150000L,
                fotoKondisiDepanUri = u1FotoDepan.absolutePath,
                fotoKondisiSampingUri = u1FotoSamping.absolutePath,
                fotoKondisiDalamUri = u1FotoDalam.absolutePath,
                fotoKondisiTambahanUri = u1FotoTambahan.absolutePath,
                fotoSerahTerimaPelangganUri = u1FotoStPelanggan.absolutePath,
                fotoSerahTerimaLokasiUri = u1FotoStLokasi.absolutePath,
                fotoBuktiPenerimaanUri = u1FotoStBukti.absolutePath,
                fotoSerahTerimaTambahanUri = u1FotoStTambahan.absolutePath
            ),
            com.example.data.local.TransactionUnitItemEntity(
                transactionId = 0L,
                customerId = customerId,
                unitId = unit200Id,
                unitIndexNumber = 2,
                kodeUnitSnapshot = "FRZ-200-B",
                namaUnitSnapshot = "Freezer 200 L",
                kapasitasLiterSnapshot = 200,
                jenisProgram = "Sewa Hak Milik",
                totalPeriodeHakMilik = 19,
                pembayaranKeHakMilik = 1,
                statusProgramHakMilik = "Aktif",
                hargaSewa = 250000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-10",
                tanggalAkhirSewa = "2026-12-10",
                tanggalJatuhTempo = "2026-12-10",
                nominalPembayaran = 250000L
            ),
            com.example.data.local.TransactionUnitItemEntity(
                transactionId = 0L,
                customerId = customerId,
                unitId = unit300Id,
                unitIndexNumber = 3,
                kodeUnitSnapshot = "FRZ-300-C",
                namaUnitSnapshot = "Freezer 300 L",
                kapasitasLiterSnapshot = 300,
                jenisProgram = "Sewa Reguler",
                hargaSewa = 200000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-20",
                tanggalAkhirSewa = "2026-12-20",
                tanggalJatuhTempo = "2026-12-20",
                nominalPembayaran = 100000L
            )
        )

        val parentTx = com.example.data.local.RentalTransactionEntity(
            nomorInvoice = "",
            tanggalPemesanan = "2026-11-05",
            customerId = customerId,
            namaPelangganSnapshot = "Budi Santoso",
            unitId = unit100Id,
            namaUnitSnapshot = "Freezer 100 L",
            kodeUnitSnapshot = "FRZ-100-A",
            hargaSewa = 600000L,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-05",
            tanggalAkhirSewa = "2026-12-05",
            tanggalJatuhTempo = "2026-12-05",
            nominalPembayaran = 500000L,
            ongkir = 0L,
            deposit = 0L,
            diskon = 0L,
            totalTagihan = 600000L,
            statusPembayaran = "Dibayar Sebagian",
            statusPenyewaan = "Dalam Penyewaan",
            metodePembayaran = "Transfer BCA",
            namaPenerima = "Budi Santoso",
            namaPetugas = "Rizky SAF",
            tandaTanganPelangganUri = uploadedPenyewaSigFile.absolutePath,
            tandaTanganPetugasUri = drawnPetugasSigFile.absolutePath
        )

        val savedTx = repository.saveMultiUnitTransaction(
            parentTransaction = parentTx,
            unitItems = unitItemsInput,
            signatures = listOf(
                com.example.data.local.TransactionSignatureEntity(
                    transactionId = 0L,
                    customerId = customerId,
                    signerRole = "PENYEWA",
                    signerName = "Budi Santoso",
                    signatureMethod = "UPLOAD_IMAGE",
                    filePath = uploadedPenyewaSigFile.absolutePath
                ),
                com.example.data.local.TransactionSignatureEntity(
                    transactionId = 0L,
                    customerId = customerId,
                    signerRole = "PETUGAS_SAF",
                    signerName = "Rizky SAF",
                    signatureMethod = "DRAW",
                    filePath = drawnPetugasSigFile.absolutePath
                )
            )
        )

        // Assert TEST 1: Total across all 3 units = 150k + 250k + 200k = 600k, total paid = 500k
        assertEquals("TEST 1: Total tagihan across 3 units must be Rp 600.000", 600000L, savedTx.totalTagihan)
        assertEquals("TEST 1: Total paid must be Rp 500.000", 500000L, savedTx.nominalPembayaran)
        assertEquals("Dibayar Sebagian", savedTx.statusPembayaran)

        val savedItems = repository.getTransactionUnitItemsByTxOnce(savedTx.id)
        assertEquals("TEST 1: All 3 unit items must be persisted", 3, savedItems.size)
        // Assert TEST 2: Mixed programs preserved
        assertEquals("Sewa Reguler", savedItems[0].jenisProgram)
        assertEquals("Sewa Hak Milik", savedItems[1].jenisProgram)
        assertEquals(19, savedItems[1].totalPeriodeHakMilik)
        assertEquals("Sewa Reguler", savedItems[2].jenisProgram)
        // Assert TEST 3: Independent due dates preserved
        assertEquals("2026-12-05", savedItems[0].tanggalJatuhTempo)
        assertEquals("2026-12-10", savedItems[1].tanggalJatuhTempo)
        assertEquals("2026-12-20", savedItems[2].tanggalJatuhTempo)
        // Assert TEST 6: Per-unit photo counts & completeness status
        assertEquals(4, savedItems[0].kondisiPhotosUploadedCount)
        assertEquals(4, savedItems[0].serahTerimaPhotosUploadedCount)
        assertEquals("Lengkap", savedItems[0].statusKelengkapanFoto)
        assertEquals(0, savedItems[1].kondisiPhotosUploadedCount)
        assertEquals("Belum Lengkap", savedItems[1].statusKelengkapanFoto)

        // All 3 rented units must now have statusUnit = "Disewa"
        assertEquals("Disewa", repository.getUnitById(unit100Id)?.statusUnit)
        assertEquals("Disewa", repository.getUnitById(unit200Id)?.statusUnit)
        assertEquals("Disewa", repository.getUnitById(unit300Id)?.statusUnit)
        assertEquals("Tersedia", repository.getUnitById(unit400Id)?.statusUnit)

        // =====================================================================
        // TEST 4: Add New Unit Later Without Altering Old Units' Schedules
        // =====================================================================
        val addedUnit4 = repository.addUnitToExistingTransaction(
            transactionId = savedTx.id,
            newUnitItem = com.example.data.local.TransactionUnitItemEntity(
                transactionId = savedTx.id,
                customerId = customerId,
                unitId = unit400Id,
                jenisProgram = "Sewa Reguler",
                hargaSewa = 300000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-25",
                tanggalAkhirSewa = "2026-12-25",
                tanggalJatuhTempo = "2026-12-25",
                nominalPembayaran = 300000L
            )
        )
        assertEquals(4, addedUnit4.unitIndexNumber)
        val afterAddItems = repository.getTransactionUnitItemsByTxOnce(savedTx.id)
        assertEquals(4, afterAddItems.size)
        assertEquals("2026-12-05", afterAddItems[0].tanggalJatuhTempo)
        assertEquals("2026-12-10", afterAddItems[1].tanggalJatuhTempo)
        assertEquals("2026-12-20", afterAddItems[2].tanggalJatuhTempo)
        assertEquals("2026-12-25", afterAddItems[3].tanggalJatuhTempo)
        assertEquals(900000L, db.freezerDao().getTransactionById(savedTx.id)?.totalTagihan)

        // =====================================================================
        // TEST 5: Edit Unit #2 Price & Due Date Without Affecting Other Units
        // =====================================================================
        val unit2ToEdit = afterAddItems[1]
        repository.updateSingleUnitInTransaction(
            unit2ToEdit.copy(
                hargaSewa = 275000L,
                tanggalJatuhTempo = "2026-12-15"
            )
        )
        val afterEditItems = repository.getTransactionUnitItemsByTxOnce(savedTx.id)
        assertEquals(275000L, afterEditItems[1].hargaSewa)
        assertEquals("2026-12-15", afterEditItems[1].tanggalJatuhTempo)
        // Units #1, #3, #4 untouched
        assertEquals(150000L, afterEditItems[0].hargaSewa)
        assertEquals("2026-12-05", afterEditItems[0].tanggalJatuhTempo)
        assertEquals(200000L, afterEditItems[2].hargaSewa)
        assertEquals("2026-12-20", afterEditItems[2].tanggalJatuhTempo)
        assertEquals(300000L, afterEditItems[3].hargaSewa)
        assertEquals(925000L, db.freezerDao().getTransactionById(savedTx.id)?.totalTagihan)

        // =====================================================================
        // Validation Checks (Section 5): Duplicate unit in 1 tx & already-rented unit
        // =====================================================================
        val dupValidation = repository.validateMultiUnitTransaction(
            customerId = customerId,
            transactionId = 0L,
            unitItems = listOf(
                com.example.data.local.TransactionUnitItemEntity(transactionId = 0L, customerId = customerId, unitId = unit400Id, jenisProgram = "Sewa Reguler", hargaSewa = 100000L),
                com.example.data.local.TransactionUnitItemEntity(transactionId = 0L, customerId = customerId, unitId = unit400Id, jenisProgram = "Sewa Reguler", hargaSewa = 100000L)
            )
        )
        assertFalse("Duplicate unit in same tx must be rejected", dupValidation.isValid)
        assertEquals("ERR_DUPLICATE_UNIT_IN_TX", dupValidation.errorCode)

        val rentedValidation = repository.validateMultiUnitTransaction(
            customerId = customerId,
            transactionId = 0L,
            unitItems = listOf(
                com.example.data.local.TransactionUnitItemEntity(transactionId = 0L, customerId = customerId, unitId = unit100Id, jenisProgram = "Sewa Reguler", hargaSewa = 150000L)
            )
        )
        assertFalse("Already rented unit must be rejected in new transaction", rentedValidation.isValid)
        assertEquals("ERR_UNIT_ALREADY_RENTED", rentedValidation.errorCode)

        // =====================================================================
        // TEST 8 & 9: PDF Generation & Persistence Integrity
        // =====================================================================
        val refreshedParentTx = db.freezerDao().getTransactionById(savedTx.id)!!
        val custObj = repository.getCustomerById(customerId)
        val primaryUnitObj = repository.getUnitById(unit100Id)
        val savedSigs = repository.getTransactionSignaturesByTxOnce(savedTx.id)
        assertEquals("TEST 9: Signatures must be saved in transaction_signatures", 2, savedSigs.size)
        assertEquals("UPLOAD_IMAGE", savedSigs.first { it.signerRole == "PENYEWA" }.signatureMethod)
        assertEquals("DRAW", savedSigs.first { it.signerRole == "PETUGAS_SAF" }.signatureMethod)

        val pdfFile = com.example.pdf.InvoicePdfGenerator.generateInvoicePdf(
            context = context,
            transaction = refreshedParentTx,
            customer = custObj,
            unit = primaryUnitObj,
            companyProfile = com.example.data.local.CompanyProfile(),
            maskNikInPdf = false,
            defaultPerjanjianSewa = "Syarat dan ketentuan sewa berlaku.",
            unitItems = afterEditItems
        )
        assertTrue("TEST 8: Multi-unit PDF contract must exist and be non-empty", pdfFile.exists() && pdfFile.length() > 1000L)
        val pdfHeader = pdfFile.readText(Charsets.UTF_8)
        assertTrue("TEST 8: PDF file must start with %PDF-1.4 header", pdfHeader.startsWith("%PDF-1.4"))
        db.close()
    }

    @Test
    fun `test migration 12 to 13 preserves data and adds smart rental tables and columns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration_12_13_test.db")
        if (dbFile.exists()) dbFile.delete()

        val callback = object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(12) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `kodePelanggan` TEXT NOT NULL DEFAULT '',
                        `namaLengkap` TEXT NOT NULL,
                        `nik` TEXT NOT NULL,
                        `nomorHp` TEXT NOT NULL,
                        `alamatLengkap` TEXT NOT NULL,
                        `jenisKelamin` TEXT NOT NULL,
                        `tanggalRegistrasi` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `freezer_units` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `kodeUnit` TEXT NOT NULL,
                        `namaUnit` TEXT NOT NULL,
                        `jenisUnit` TEXT NOT NULL,
                        `owner` TEXT NOT NULL,
                        `hargaPembelian` INTEGER NOT NULL,
                        `kapasitasLiter` INTEGER NOT NULL,
                        `cicilanPerBulan` INTEGER NOT NULL,
                        `merek` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `nomorSeri` TEXT NOT NULL,
                        `statusUnit` TEXT NOT NULL,
                        `lokasiUnit` TEXT NOT NULL,
                        `tanggalPembelian` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rental_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nomorInvoice` TEXT NOT NULL,
                        `tanggalPemesanan` TEXT NOT NULL,
                        `customerId` INTEGER NOT NULL,
                        `namaPelangganSnapshot` TEXT NOT NULL,
                        `unitId` INTEGER NOT NULL,
                        `namaUnitSnapshot` TEXT NOT NULL,
                        `kodeUnitSnapshot` TEXT NOT NULL,
                        `hargaSewa` INTEGER NOT NULL,
                        `durasiHari` INTEGER NOT NULL,
                        `tanggalMulaiSewa` TEXT NOT NULL,
                        `tanggalAkhirSewa` TEXT NOT NULL,
                        `tanggalJatuhTempo` TEXT NOT NULL,
                        `nominalPembayaran` INTEGER NOT NULL,
                        `totalTagihan` INTEGER NOT NULL,
                        `statusPembayaran` TEXT NOT NULL,
                        `statusPenyewaan` TEXT NOT NULL,
                        `metodePembayaran` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_unit_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transaction_id` INTEGER NOT NULL,
                        `customer_id` INTEGER NOT NULL,
                        `unit_id` INTEGER NOT NULL,
                        `unit_index_number` INTEGER NOT NULL,
                        `kode_unit_snapshot` TEXT NOT NULL,
                        `nama_unit_snapshot` TEXT NOT NULL,
                        `harga_sewa` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_proofs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `tanggalPembayaran` TEXT NOT NULL,
                        `nominalPembayaran` INTEGER NOT NULL,
                        `metodePembayaran` TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                // Seed v12 rows that must survive migration intact
                db.execSQL(
                    "INSERT INTO `customers` (`id`, `kodePelanggan`, `namaLengkap`, `nik`, `nomorHp`, `alamatLengkap`, `jenisKelamin`, `tanggalRegistrasi`) VALUES (1, 'CUST/SAF/0001', 'Budi Santoso', '3372011405880001', '081226345678', 'Jl. Slamet Riyadi Solo', 'Laki-laki', '2026-10-01')"
                )
                db.execSQL(
                    "INSERT INTO `freezer_units` (`id`, `kodeUnit`, `namaUnit`, `jenisUnit`, `owner`, `hargaPembelian`, `kapasitasLiter`, `cicilanPerBulan`, `merek`, `model`, `nomorSeri`, `statusUnit`, `lokasiUnit`, `tanggalPembelian`) VALUES (1, 'SAF-CF-001', 'RSA 200L', 'Chest Freezer', 'SAF', 3000000, 200, 300000, 'RSA', 'CF-200', 'SN-001', 'Disewa', 'Solo', '2026-01-01')"
                )
                db.execSQL(
                    "INSERT INTO `rental_transactions` (`id`, `nomorInvoice`, `tanggalPemesanan`, `customerId`, `namaPelangganSnapshot`, `unitId`, `namaUnitSnapshot`, `kodeUnitSnapshot`, `hargaSewa`, `durasiHari`, `tanggalMulaiSewa`, `tanggalAkhirSewa`, `tanggalJatuhTempo`, `nominalPembayaran`, `totalTagihan`, `statusPembayaran`, `statusPenyewaan`, `metodePembayaran`) VALUES (1, 'INV/SAF/2026/0001', '2026-10-01', 1, 'Budi Santoso', 1, 'RSA 200L', 'SAF-CF-001', 200000, 30, '2026-10-01', '2026-10-31', '2026-10-31', 200000, 200000, 'Lunas', 'Dalam Penyewaan', 'Transfer BCA')"
                )
                db.execSQL(
                    "INSERT INTO `transaction_unit_items` (`id`, `transaction_id`, `customer_id`, `unit_id`, `unit_index_number`, `kode_unit_snapshot`, `nama_unit_snapshot`, `harga_sewa`) VALUES (1, 1, 1, 1, 1, 'SAF-CF-001', 'RSA 200L', 200000)"
                )
                db.execSQL(
                    "INSERT INTO `payment_proofs` (`id`, `transactionId`, `tanggalPembayaran`, `nominalPembayaran`, `metodePembayaran`) VALUES (1, 1, '2026-10-01', 200000, 'Transfer BCA')"
                )
            }

            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // Handled directly via MIGRATION_12_13 call below
            }
        }

        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(callback)
                .build()
        )
        val sqliteDb = helper.writableDatabase
        com.example.data.local.AppDatabase.MIGRATION_12_13.migrate(sqliteDb)

        // Verify v12 customer data survived and new columns exist
        sqliteDb.query("SELECT namaLengkap, fotoPelangganUri, statusPelanggan FROM customers WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Budi Santoso", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals("Pelanggan Aktif", c.getString(2))
        }

        // Verify v12 unit data survived and Smart Unit Management columns exist
        sqliteDb.query("SELECT kodeUnit, kondisiUnit, tanggalPengiriman, lokasiGpsPemasangan FROM freezer_units WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("SAF-CF-001", c.getString(0))
            assertEquals("Baik & Siap Pakai", c.getString(1))
            assertEquals("", c.getString(2))
            assertEquals("", c.getString(3))
        }

        // Verify unit_payment_allocations & customer_timeline_events tables were created
        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('unit_payment_allocations', 'customer_timeline_events')").use { c ->
            val createdTables = mutableSetOf<String>()
            while (c.moveToNext()) createdTables.add(c.getString(0))
            assertTrue(createdTables.contains("unit_payment_allocations"))
            assertTrue(createdTables.contains("customer_timeline_events"))
        }

        helper.close()
        dbFile.delete()
    }

    @Test
    @Config(sdk = [36], shadows = [ShadowCompatPdfDocument::class])
    fun `test master customer summary hierarchy and partial payment allocation per unit`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val dao = db.freezerDao()
        val repository = com.example.data.repository.FreezerRepository(dao)

        // 1. Single Master Customer with permanent ID & photo URI
        val customerId = repository.saveCustomer(
            com.example.data.local.CustomerEntity(
                namaLengkap = "Budi Santoso",
                nik = "3372011405880001",
                nomorHp = "081226345678",
                alamatLengkap = "Jl. Slamet Riyadi No. 214, Surakarta",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = "2026-11-01",
                fotoPelangganUri = "/tmp/budi_profile.jpg"
            )
        )
        val savedCustomer = repository.getCustomerById(customerId)!!
        assertTrue(savedCustomer.kodePelanggan.startsWith("CUST/SAF/"))
        assertEquals("/tmp/budi_profile.jpg", savedCustomer.fotoPelangganUri)

        // Create 3 Freezer Units
        val unit1Id = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "SAF-CF-101",
                namaUnit = "Freezer 100 L",
                jenisUnit = "Chest Freezer",
                owner = "SAF",
                hargaPembelian = 2200000L,
                kapasitasLiter = 100,
                cicilanPerBulan = 150000L,
                merek = "RSA",
                model = "CF-110",
                nomorSeri = "SN-101",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang SAF Solo",
                tanggalPembelian = "2026-01-01"
            )
        )
        val unit2Id = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "SAF-CF-102",
                namaUnit = "Freezer 200 L",
                jenisUnit = "Chest Freezer",
                owner = "SAF",
                hargaPembelian = 3200000L,
                kapasitasLiter = 200,
                cicilanPerBulan = 250000L,
                merek = "GEA",
                model = "AB-226",
                nomorSeri = "SN-102",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang SAF Solo",
                tanggalPembelian = "2026-01-02"
            )
        )
        val unit3Id = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "SAF-CF-103",
                namaUnit = "Freezer 300 L",
                jenisUnit = "Chest Freezer",
                owner = "SAF",
                hargaPembelian = 4200000L,
                kapasitasLiter = 300,
                cicilanPerBulan = 200000L,
                merek = "Modena",
                model = "MD-300",
                nomorSeri = "SN-103",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang SAF Solo",
                tanggalPembelian = "2026-01-03"
            )
        )

        // Contract #1 (2 units: 150k + 250k) and Contract #2 (1 unit: 200k) -> 3 Active Units, 2 Active Contracts, Rp600k/bulan
        val futureDue = com.example.util.DateCurrencyUtils.addDaysToIsoDate(com.example.util.DateCurrencyUtils.todayIso(), 10)
        val todayIso = com.example.util.DateCurrencyUtils.todayIso()

        val contract1 = repository.saveMultiUnitTransaction(
            parentTransaction = com.example.data.local.RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0101",
                tanggalPemesanan = todayIso,
                customerId = customerId,
                namaPelangganSnapshot = "Budi Santoso",
                unitId = unit1Id,
                namaUnitSnapshot = "Freezer 100 L",
                kodeUnitSnapshot = "SAF-CF-101",
                hargaSewa = 400000L,
                durasiHari = 30,
                tanggalMulaiSewa = todayIso,
                tanggalAkhirSewa = futureDue,
                tanggalJatuhTempo = futureDue,
                nominalPembayaran = 0L,
                totalTagihan = 400000L,
                statusPembayaran = "Belum Bayar",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA"
            ),
            unitItems = listOf(
                com.example.data.local.TransactionUnitItemEntity(
                    transactionId = 0L,
                    customerId = customerId,
                    unitId = unit1Id,
                    kodeUnitSnapshot = "SAF-CF-101",
                    namaUnitSnapshot = "Freezer 100 L",
                    jenisProgram = "Sewa Reguler",
                    hargaSewa = 150000L,
                    durasiHari = 30,
                    tanggalMulaiSewa = todayIso,
                    tanggalAkhirSewa = futureDue,
                    tanggalJatuhTempo = futureDue,
                    nominalPembayaran = 0L
                ),
                com.example.data.local.TransactionUnitItemEntity(
                    transactionId = 0L,
                    customerId = customerId,
                    unitId = unit2Id,
                    kodeUnitSnapshot = "SAF-CF-102",
                    namaUnitSnapshot = "Freezer 200 L",
                    jenisProgram = "Sewa Hak Milik",
                    totalPeriodeHakMilik = 19,
                    hargaSewa = 250000L,
                    durasiHari = 30,
                    tanggalMulaiSewa = todayIso,
                    tanggalAkhirSewa = futureDue,
                    tanggalJatuhTempo = futureDue,
                    nominalPembayaran = 0L
                )
            )
        )

        val contract2 = repository.saveMultiUnitTransaction(
            parentTransaction = com.example.data.local.RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0102",
                tanggalPemesanan = todayIso,
                customerId = customerId,
                namaPelangganSnapshot = "Budi Santoso",
                unitId = unit3Id,
                namaUnitSnapshot = "Freezer 300 L",
                kodeUnitSnapshot = "SAF-CF-103",
                hargaSewa = 200000L,
                durasiHari = 30,
                tanggalMulaiSewa = todayIso,
                tanggalAkhirSewa = futureDue,
                tanggalJatuhTempo = futureDue,
                nominalPembayaran = 0L,
                totalTagihan = 200000L,
                statusPembayaran = "Belum Bayar",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "QRIS"
            ),
            unitItems = listOf(
                com.example.data.local.TransactionUnitItemEntity(
                    transactionId = 0L,
                    customerId = customerId,
                    unitId = unit3Id,
                    kodeUnitSnapshot = "SAF-CF-103",
                    namaUnitSnapshot = "Freezer 300 L",
                    jenisProgram = "Sewa Reguler",
                    hargaSewa = 200000L,
                    durasiHari = 30,
                    tanggalMulaiSewa = todayIso,
                    tanggalAkhirSewa = futureDue,
                    tanggalJatuhTempo = futureDue,
                    nominalPembayaran = 0L
                )
            )
        )

        // Verify Master Customer Summary before payment
        val summaryBefore = com.example.data.repository.FreezerRepository.buildCustomerSmartSummary(
            customer = savedCustomer,
            transactions = dao.getAllTransactionsOnce(),
            allUnitItems = dao.getAllTransactionUnitItemsOnce(),
            allPaymentProofs = dao.getAllPaymentProofs().first(),
            allUnitAllocations = dao.getAllUnitPaymentAllocationsOnce(),
            todayIso = todayIso
        )
        assertEquals(3, summaryBefore.totalUnitAktif)
        assertEquals(2, summaryBefore.totalKontrakAktif)
        assertEquals(600000L, summaryBefore.totalTagihanBulanan)
        assertEquals(600000L, summaryBefore.totalTunggakan)
        assertEquals(2, summaryBefore.contracts.size)

        // 2. Partial Payment & Manual Allocation:
        // Pay Rp 250.000 on Contract #1 -> allocate Rp 150.000 (full) to Unit 1, and Rp 100.000 (partial) to Unit 2.
        // Contract #2 Unit 3 remains completely unpaid!
        val c1Items = dao.getTransactionUnitItemsByTxOnce(contract1.id)
        val item1 = c1Items[0]
        val item2 = c1Items[1]

        val proof = repository.allocatePartialPaymentToUnits(
            transactionId = contract1.id,
            totalPaidAmount = 250000L,
            unitAllocationsMap = mapOf(
                item1.id to 150000L,
                item2.id to 100000L
            ),
            paymentDate = todayIso,
            metodePembayaran = "Transfer BCA",
            namaPengirim = "Budi Santoso",
            catatanPembayaran = "Bayar penuh Unit 1 + cicil sebagian Unit 2"
        )
        assertEquals(250000L, proof.nominalPembayaran)

        val updatedC1Items = dao.getTransactionUnitItemsByTxOnce(contract1.id)
        val updatedItem1 = updatedC1Items.first { it.id == item1.id }
        val updatedItem2 = updatedC1Items.first { it.id == item2.id }
        val updatedC2Item3 = dao.getTransactionUnitItemsByTxOnce(contract2.id).first()

        // Unit 1 is fully paid
        assertEquals(150000L, updatedItem1.nominalPembayaran)
        assertEquals("Lunas", updatedItem1.statusPembayaran)
        assertEquals("Sudah dibayar", updatedItem1.computeSmartBillingStatus(todayIso))

        // Unit 2 is partially paid (NOT marked paid!)
        assertEquals(100000L, updatedItem2.nominalPembayaran)
        assertEquals("Dibayar Sebagian", updatedItem2.statusPembayaran)
        assertEquals("Pembayaran sebagian", updatedItem2.computeSmartBillingStatus(todayIso))
        assertEquals(150000L, updatedItem2.sisaTagihanUnit)

        // Unit 3 in Contract #2 is completely untouched/unpaid
        assertEquals(0L, updatedC2Item3.nominalPembayaran)
        assertEquals("Belum jatuh tempo", updatedC2Item3.computeSmartBillingStatus(todayIso))

        // Parent contract #1 reflects 250k paid / Dibayar Sebagian
        val refreshedC1 = dao.getTransactionById(contract1.id)!!
        assertEquals(250000L, refreshedC1.nominalPembayaran)
        assertEquals("Dibayar Sebagian", refreshedC1.statusPembayaran)

        // Verify allocation rows & timeline events persisted
        val allocs = dao.getUnitPaymentAllocationsByCustomerOnce(customerId)
        assertEquals(2, allocs.size)

        val timeline = com.example.data.repository.FreezerRepository.buildCombinedTimelineForCustomer(
            customer = savedCustomer,
            transactions = dao.getAllTransactionsOnce(),
            unitItems = dao.getAllTransactionUnitItemsOnce(),
            paymentProofs = dao.getAllPaymentProofs().first(),
            returns = dao.getAllReturnsOnce(),
            photos = dao.getCustomerPhotosOnce(customerId),
            storedEvents = dao.getCustomerTimelineEventsOnce(customerId)
        )
        assertTrue(timeline.any { it.eventType == "REGISTRASI" })
        assertTrue(timeline.any { it.eventType == "SEWA_UNIT" })
        assertTrue(timeline.any { it.eventType == "PEMBAYARAN" && it.amount == 250000L })

        // Verify multi-unit contract PDF generation succeeds with contract title
        val pdfFile = com.example.pdf.InvoicePdfGenerator.generateInvoicePdf(
            context = context,
            transaction = refreshedC1,
            customer = savedCustomer,
            unit = repository.getUnitById(unit1Id),
            companyProfile = com.example.data.local.CompanyProfile(),
            unitItems = updatedC1Items
        )
        assertTrue(pdfFile.exists() && pdfFile.length() > 1000L)
        db.close()
    }

    @Test
    fun `test freezer return restores unit availability preserves history and logs timeline`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val dao = db.freezerDao()
        val repository = com.example.data.repository.FreezerRepository(dao)

        val customerId = repository.saveCustomer(
            com.example.data.local.CustomerEntity(
                namaLengkap = "Siti Rahmawati",
                nik = "3313125509910003",
                nomorHp = "081390876543",
                alamatLengkap = "Colomadu, Karanganyar",
                jenisKelamin = "Perempuan",
                tanggalRegistrasi = "2026-10-10"
            )
        )
        val unitGoodId = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "SAF-CF-201",
                namaUnit = "GEA 220L Good",
                jenisUnit = "Chest Freezer",
                owner = "SAF",
                hargaPembelian = 2900000L,
                kapasitasLiter = 220,
                cicilanPerBulan = 250000L,
                merek = "GEA",
                model = "AB-226",
                nomorSeri = "SN-201",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang SAF Solo",
                tanggalPembelian = "2026-01-10"
            )
        )
        val unitDamagedId = repository.saveUnit(
            com.example.data.local.FreezerUnitEntity(
                kodeUnit = "SAF-CF-202",
                namaUnit = "RSA 300L Damaged",
                jenisUnit = "Chest Freezer",
                owner = "SAF",
                hargaPembelian = 3500000L,
                kapasitasLiter = 300,
                cicilanPerBulan = 300000L,
                merek = "RSA",
                model = "CF-310",
                nomorSeri = "SN-202",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang SAF Solo",
                tanggalPembelian = "2026-01-11"
            )
        )

        val txGood = repository.saveMultiUnitTransaction(
            parentTransaction = com.example.data.local.RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0201",
                tanggalPemesanan = "2026-11-01",
                customerId = customerId,
                namaPelangganSnapshot = "Siti Rahmawati",
                unitId = unitGoodId,
                namaUnitSnapshot = "GEA 220L Good",
                kodeUnitSnapshot = "SAF-CF-201",
                hargaSewa = 250000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-01",
                tanggalAkhirSewa = "2026-12-01",
                tanggalJatuhTempo = "2026-12-01",
                nominalPembayaran = 250000L,
                totalTagihan = 250000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Cash / Tunai"
            ),
            unitItems = listOf(
                com.example.data.local.TransactionUnitItemEntity(
                    transactionId = 0L,
                    customerId = customerId,
                    unitId = unitGoodId,
                    hargaSewa = 250000L,
                    nominalPembayaran = 250000L
                )
            )
        )

        val txDamaged = repository.saveMultiUnitTransaction(
            parentTransaction = com.example.data.local.RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0202",
                tanggalPemesanan = "2026-11-02",
                customerId = customerId,
                namaPelangganSnapshot = "Siti Rahmawati",
                unitId = unitDamagedId,
                namaUnitSnapshot = "RSA 300L Damaged",
                kodeUnitSnapshot = "SAF-CF-202",
                hargaSewa = 300000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-02",
                tanggalAkhirSewa = "2026-12-02",
                tanggalJatuhTempo = "2026-12-02",
                nominalPembayaran = 300000L,
                totalTagihan = 300000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Cash / Tunai"
            ),
            unitItems = listOf(
                com.example.data.local.TransactionUnitItemEntity(
                    transactionId = 0L,
                    customerId = customerId,
                    unitId = unitDamagedId,
                    hargaSewa = 300000L,
                    nominalPembayaran = 300000L
                )
            )
        )

        // Return unitGood in "Baik / Normal" condition -> becomes Tersedia
        val retGood = repository.processFreezerReturn(
            com.example.data.local.FreezerReturnEntity(
                nomorDokumenPengembalian = "",
                transactionId = txGood.id,
                nomorInvoiceSnapshot = txGood.nomorInvoice,
                customerId = customerId,
                namaPelangganSnapshot = "Siti Rahmawati",
                unitId = unitGoodId,
                kodeUnitSnapshot = "SAF-CF-201",
                namaUnitSnapshot = "GEA 220L Good",
                tanggalPengembalian = "2026-11-28",
                jamPengembalian = "14:00:00",
                namaPetugasPenerima = "Petugas Solo",
                kondisiUnitSaatKembali = "Baik / Normal"
            )
        )
        assertEquals("Tersedia", retGood.statusUnitSetelahKembali)
        assertEquals("Tersedia", repository.getUnitById(unitGoodId)?.statusUnit)
        assertEquals("Selesai", dao.getTransactionById(txGood.id)?.statusPenyewaan)
        assertEquals("Selesai", dao.getTransactionUnitItemsByTxOnce(txGood.id).first().statusPenyewaan)

        // Return unitDamaged in "Rusak ringan" condition -> becomes Dalam Perbaikan
        val retDamaged = repository.processFreezerReturn(
            com.example.data.local.FreezerReturnEntity(
                nomorDokumenPengembalian = "",
                transactionId = txDamaged.id,
                nomorInvoiceSnapshot = txDamaged.nomorInvoice,
                customerId = customerId,
                namaPelangganSnapshot = "Siti Rahmawati",
                unitId = unitDamagedId,
                kodeUnitSnapshot = "SAF-CF-202",
                namaUnitSnapshot = "RSA 300L Damaged",
                tanggalPengembalian = "2026-11-29",
                jamPengembalian = "15:30:00",
                namaPetugasPenerima = "Petugas Solo",
                kondisiUnitSaatKembali = "Rusak ringan",
                catatanKondisiUnit = "Engsel pintu kanan kendur"
            )
        )
        assertEquals("Dalam Perbaikan", retDamaged.statusUnitSetelahKembali)
        val updatedDamagedUnit = repository.getUnitById(unitDamagedId)!!
        assertEquals("Dalam Perbaikan", updatedDamagedUnit.statusUnit)
        assertTrue(updatedDamagedUnit.isMaintenanceOrDamagedStatus())
        assertEquals("Selesai", dao.getTransactionById(txDamaged.id)?.statusPenyewaan)

        // History preserved & timeline logged
        assertEquals(2, dao.getAllTransactionsOnce().size)
        assertEquals(2, dao.getAllReturnsOnce().size)
        val events = dao.getCustomerTimelineEventsOnce(customerId)
        assertEquals(2, events.count { it.eventType == "PENGEMBALIAN" })
        db.close()
    }

    @Test
    fun `test smart billing status states and smart notification preferences`() {
        val today = "2026-11-15"
        val baseItem = com.example.data.local.TransactionUnitItemEntity(
            transactionId = 1L,
            customerId = 1L,
            unitId = 1L,
            hargaSewa = 200000L
        )
        assertEquals(
            "Sudah dibayar",
            baseItem.copy(nominalPembayaran = 200000L, tanggalJatuhTempo = "2026-11-10").computeSmartBillingStatus(today)
        )
        assertEquals(
            "Pembayaran sebagian",
            baseItem.copy(nominalPembayaran = 100000L, tanggalJatuhTempo = "2026-11-20").computeSmartBillingStatus(today)
        )
        assertEquals(
            "Jatuh tempo hari ini",
            baseItem.copy(nominalPembayaran = 0L, tanggalJatuhTempo = "2026-11-15").computeSmartBillingStatus(today)
        )
        assertEquals(
            "Terlambat",
            baseItem.copy(nominalPembayaran = 0L, tanggalJatuhTempo = "2026-11-12").computeSmartBillingStatus(today)
        )
        assertEquals(
            "Belum jatuh tempo",
            baseItem.copy(nominalPembayaran = 0L, tanggalJatuhTempo = "2026-11-25").computeSmartBillingStatus(today)
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("saf_soc_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("notif_due_soon", true)
            .putInt("notif_due_days_before", 5)
            .putBoolean("notif_overdue", false)
            .putBoolean("notif_unit_added", true)
            .putBoolean("notif_contract_completed", true)
            .putBoolean("notif_incomplete_photo", false)
            .putBoolean("notif_unreturned_unit", true)
            .commit()

        val loaded = com.example.notification.DueDateReminderReceiver.loadSmartNotificationSettings(prefs)
        assertTrue(loaded.enableDueSoonReminder)
        assertEquals(5, loaded.dueReminderDaysBefore)
        assertFalse(loaded.enableOverdueReminder)
        assertFalse(loaded.enableIncompletePhotoReminder)
        assertTrue(loaded.enableUnreturnedUnitReminder)
    }

    @Test
    fun testContactsPermission_StateTransitionsAndAndroidSettingsRevocation() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(context)
        com.example.contacts.ContactAccessManager.resetForTesting(context)

        // 1. Default status when app never requested contact permission -> "Belum diizinkan"
        shadowApp.denyPermissions(android.Manifest.permission.READ_CONTACTS)
        val initial = com.example.contacts.ContactAccessManager.evaluatePermissionState(context)
        assertEquals(
            com.example.contacts.ContactPermissionState.BELUM_DIIZINKAN,
            initial.state
        )
        assertEquals("Belum diizinkan", initial.state.displayText)
        assertFalse(initial.osPermissionGranted)

        // 2. User denies permission dialog -> "Izin ditolak"
        com.example.contacts.ContactAccessManager.markPermissionDenied(context)
        val afterDeny = com.example.contacts.ContactAccessManager.evaluatePermissionState(context)
        assertEquals(
            com.example.contacts.ContactPermissionState.IZIN_DITOLAK,
            afterDeny.state
        )
        assertEquals("Izin ditolak", afterDeny.state.displayText)

        // 3. User grants READ_CONTACTS permission -> "Diizinkan"
        shadowApp.grantPermissions(android.Manifest.permission.READ_CONTACTS)
        com.example.contacts.ContactAccessManager.markPermissionGranted(context)
        val afterGrant = com.example.contacts.ContactAccessManager.evaluatePermissionState(context)
        assertEquals(
            com.example.contacts.ContactPermissionState.DIIZINKAN,
            afterGrant.state
        )
        assertEquals("Diizinkan", afterGrant.state.displayText)
        assertTrue(afterGrant.osPermissionGranted)
        assertTrue(afterGrant.appToggleEnabled)

        // 4. User toggles OFF inside App Settings -> "Belum diizinkan"
        com.example.contacts.ContactAccessManager.setAppContactsToggleEnabled(context, false)
        val afterToggleOff = com.example.contacts.ContactAccessManager.evaluatePermissionState(context)
        assertEquals(
            com.example.contacts.ContactPermissionState.BELUM_DIIZINKAN,
            afterToggleOff.state
        )
        assertFalse(afterToggleOff.appToggleEnabled)

        // Turn toggle ON again -> "Diizinkan"
        com.example.contacts.ContactAccessManager.setAppContactsToggleEnabled(context, true)
        assertEquals(
            com.example.contacts.ContactPermissionState.DIIZINKAN,
            com.example.contacts.ContactAccessManager.evaluatePermissionState(context).state
        )

        // 5. Permission revoked from Android System Settings -> detected automatically on ON_RESUME evaluation
        shadowApp.denyPermissions(android.Manifest.permission.READ_CONTACTS)
        val afterRevoke = com.example.contacts.ContactAccessManager.evaluatePermissionState(context)
        assertEquals(
            com.example.contacts.ContactPermissionState.IZIN_DITOLAK,
            afterRevoke.state
        )
        assertEquals("Izin ditolak", afterRevoke.state.displayText)
        assertTrue(
            "Revocation from Android settings must be detected automatically",
            afterRevoke.wasRevokedFromSystemSettings
        )
        assertFalse(afterRevoke.appToggleEnabled)
    }

    @Test
    fun testWhatsAppContactImport_NormalizationMultiNumberPickerAndDuplicatePrevention() = kotlinx.coroutines.runBlocking {
        // 1. Phone normalization across +62, 62, 0062, 08, 8xx, dashes/spaces
        assertEquals("081234567890", com.example.contacts.PhoneNumberNormalizer.normalizeWhatsAppNumber("+62 812-3456-7890"))
        assertEquals("081234567890", com.example.contacts.PhoneNumberNormalizer.normalizeWhatsAppNumber("6281234567890"))
        assertEquals("081234567890", com.example.contacts.PhoneNumberNormalizer.normalizeWhatsAppNumber("0062 812 3456 7890"))
        assertEquals("081234567890", com.example.contacts.PhoneNumberNormalizer.normalizeWhatsAppNumber("0812-3456-7890"))
        assertEquals("081234567890", com.example.contacts.PhoneNumberNormalizer.normalizeWhatsAppNumber("81234567890"))

        // 2. Duplicate prevention across prefixes
        assertTrue(
            com.example.contacts.PhoneNumberNormalizer.areEquivalentWhatsAppNumbers(
                "+62 812-3456-7890",
                "081234567890"
            )
        )
        assertTrue(
            com.example.contacts.PhoneNumberNormalizer.areEquivalentWhatsAppNumbers(
                "6281234567890",
                "0812-3456-7890"
            )
        )
        assertFalse(
            com.example.contacts.PhoneNumberNormalizer.areEquivalentWhatsAppNumbers(
                "081234567890",
                "081299998888"
            )
        )
        assertEquals(
            "BUDI SANTOSO",
            com.example.contacts.PhoneNumberNormalizer.formatImportedCustomerName("  budi   santoso ")
        )

        // 3. System Contact Picker Intent uses ACTION_PICK with Phone.CONTENT_TYPE
        val pickerIntent = com.example.contacts.ContactAccessManager.buildSystemPhoneContactPickerIntent()
        assertEquals(android.content.Intent.ACTION_PICK, pickerIntent.action)
        assertEquals(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE, pickerIntent.type)

        // 4. Multi-number contact extraction via ContentProvider (Robolectric shadow)
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(context)
        shadowApp.grantPermissions(android.Manifest.permission.READ_CONTACTS)

        val pickedUri = android.net.Uri.parse("content://com.android.contacts/data/42")
        val mockProvider = object : android.content.ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun query(
                uri: android.net.Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): android.database.Cursor {
                val cols = arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.TYPE,
                    android.provider.ContactsContract.CommonDataKinds.Phone.LABEL,
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                )
                val cursor = android.database.MatrixCursor(cols)
                if (uri == pickedUri) {
                    cursor.addRow(
                        arrayOf<Any?>(
                            "Budi Santoso",
                            "+62 812-3456-7890",
                            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                            null,
                            77L
                        )
                    )
                } else {
                    cursor.addRow(
                        arrayOf<Any?>(
                            "Budi Santoso",
                            "+62 812-3456-7890",
                            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                            null,
                            77L
                        )
                    )
                    cursor.addRow(
                        arrayOf<Any?>(
                            "Budi Santoso",
                            "0857-2500-9900",
                            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
                            null,
                            77L
                        )
                    )
                }
                return cursor
            }
            override fun getType(uri: android.net.Uri): String? = null
            override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
            override fun delete(uri: android.net.Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        org.robolectric.Shadows.shadowOf(context.contentResolver).let {
            org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
                android.provider.ContactsContract.AUTHORITY,
                mockProvider
            )
        }

        val extracted = com.example.contacts.ContactAccessManager.extractContactFromPickerUri(context, pickedUri)
        assertNotNull(extracted)
        assertEquals("BUDI SANTOSO", extracted?.displayNameUpper)
        assertEquals(2, extracted?.phoneNumbers?.size)
        assertEquals("081234567890", extracted?.phoneNumbers?.get(0)?.normalizedNumber)
        assertEquals("085725009900", extracted?.phoneNumbers?.get(1)?.normalizedNumber)
    }

    @Test
    fun testSmartCaseInputs_AutoCapsLockCursorPreservationAndExclusions() {
        // 1. UPPERCASE conversion for customer names, addresses, notes
        assertEquals(
            "BUDI SANTOSO",
            com.example.ui.components.transformTextCase("budi santoso", com.example.ui.components.InputTextCaseMode.UPPERCASE)
        )
        assertEquals(
            "JL. SLAMET RIYADI NO. 12, SURAKARTA",
            com.example.ui.components.transformTextCase(
                "Jl. Slamet Riyadi No. 12, Surakarta",
                com.example.ui.components.InputTextCaseMode.UPPERCASE
            )
        )

        // 2. LOWERCASE for Email
        assertEquals(
            "admin@sewainajafreezer.id",
            com.example.ui.components.transformTextCase(
                "Admin@SewainAjaFreezer.ID",
                com.example.ui.components.InputTextCaseMode.LOWERCASE
            )
        )

        // 3. PRESERVE for Phone/WhatsApp, URLs, passwords/tokens
        val sheetUrl = "https://docs.google.com/spreadsheets/d/1AbCdEfG_XyZ/edit"
        assertEquals(
            sheetUrl,
            com.example.ui.components.transformTextCase(sheetUrl, com.example.ui.components.InputTextCaseMode.PRESERVE)
        )
        assertEquals(
            "+6281234567890",
            com.example.ui.components.transformTextCase("+6281234567890", com.example.ui.components.InputTextCaseMode.PRESERVE)
        )

        // 4. Cursor-safe editing in the MIDDLE of text does NOT jump cursor to the end
        val midEditInput = androidx.compose.ui.text.input.TextFieldValue(
            text = "BUDi SANTOSO",
            selection = androidx.compose.ui.text.TextRange(4, 4),
            composition = androidx.compose.ui.text.TextRange(3, 4)
        )
        val transformedTfv = com.example.ui.components.applyCaseToTextFieldValue(
            midEditInput,
            com.example.ui.components.InputTextCaseMode.UPPERCASE
        )
        assertEquals("BUDI SANTOSO", transformedTfv.text)
        assertEquals(
            "Cursor must remain at position 4 after middle insertion",
            androidx.compose.ui.text.TextRange(4, 4),
            transformedTfv.selection
        )
        assertEquals(
            androidx.compose.ui.text.TextRange(3, 4),
            transformedTfv.composition
        )
    }
}

@org.robolectric.annotation.Implements(android.graphics.pdf.PdfDocument::class)
class ShadowCompatPdfDocument {
    private val renderedPages = mutableListOf<android.graphics.pdf.PdfDocument.PageInfo>()

    @org.robolectric.annotation.Implementation
    protected fun startPage(pageInfo: android.graphics.pdf.PdfDocument.PageInfo): android.graphics.pdf.PdfDocument.Page {
        renderedPages.add(pageInfo)
        val bmp = Bitmap.createBitmap(
            pageInfo.pageWidth.coerceAtLeast(1),
            pageInfo.pageHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        return try {
            val ctor = android.graphics.pdf.PdfDocument.Page::class.java.getDeclaredConstructor(
                Canvas::class.java,
                android.graphics.pdf.PdfDocument.PageInfo::class.java
            )
            ctor.isAccessible = true
            ctor.newInstance(canvas, pageInfo)
        } catch (_: Exception) {
            val page = org.robolectric.shadow.api.Shadow.newInstanceOf(android.graphics.pdf.PdfDocument.Page::class.java)
            org.robolectric.util.ReflectionHelpers.setField(page, "mCanvas", canvas)
            org.robolectric.util.ReflectionHelpers.setField(page, "mPageInfo", pageInfo)
            page
        }
    }

    @org.robolectric.annotation.Implementation
    protected fun finishPage(page: android.graphics.pdf.PdfDocument.Page) {
        // No-op in Robolectric shadow
    }

    @org.robolectric.annotation.Implementation
    protected fun getPages(): List<android.graphics.pdf.PdfDocument.PageInfo> = renderedPages.toList()

    @org.robolectric.annotation.Implementation
    protected fun writeTo(out: java.io.OutputStream) {
        val header = "%PDF-1.4\n% SEWAin Aja Freezer SOC Multi-Unit Contract (${renderedPages.size} pages)\n".toByteArray(Charsets.UTF_8)
        val body = ByteArray(1200) { ' '.code.toByte() }
        out.write(header)
        out.write(body)
        out.write("\n%%EOF".toByteArray(Charsets.UTF_8))
    }

    @org.robolectric.annotation.Implementation
    protected fun close() {
        // No-op in Robolectric shadow
    }
}
