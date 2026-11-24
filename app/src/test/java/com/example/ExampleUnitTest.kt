package com.example

import com.example.data.local.DEFAULT_PERJANJIAN_SEWA
import com.example.ocr.KtpOcrScanner
import com.example.util.SecurityCryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun ktpOcrParser_extracts13FieldsAndValidates16DigitNik() {
        val sampleOcrRawText = """
            PROVINSI JAWA TENGAH
            KOTA SURAKARTA
            NIK : 3372041807920007
            Nama : HENDRA WIJAYA PUTRA
            Tempat/Tgl Lahir : SURAKARTA, 18-07-1992
            Jenis Kelamin : LAKI-LAKI Gol. Darah : O
            Alamat : JL. SLAMET RIYADI NO. 158
            RT/RW : 003/008
            Kel/Desa : TIMURAN
            Kecamatan : BANJARSARI
            Agama : ISLAM
            Status Perkawinan: KAWIN
            Pekerjaan : WIRASWASTA
            Kewarganegaraan: WNI
            Berlaku Hingga : SEUMUR HIDUP
        """.trimIndent()

        val result = KtpOcrScanner.parseIndonesianKtpText(
            rawOcrText = sampleOcrRawText,
            savedPhotoPath = "/data/user/0/com.aistudio.sewainajafreezer.soc/files/ktp_test.jpg"
        )

        assertEquals("3372041807920007", result.nik)
        assertTrue(KtpOcrScanner.isValid16DigitNik(result.nik))
        assertFalse(KtpOcrScanner.isValid16DigitNik("33720418"))
        assertEquals("HENDRA WIJAYA PUTRA", result.namaLengkap)
        assertEquals("SURAKARTA", result.tempatLahir)
        assertEquals("18-07-1992", result.tanggalLahir)
        assertEquals("Laki-laki", result.jenisKelamin)
        assertEquals("003/008", result.rtRw)
        assertEquals("TIMURAN", result.kelurahanDesa)
        assertEquals("BANJARSARI", result.kecamatan)
        assertEquals("ISLAM", result.agama)
        assertEquals("KAWIN", result.statusPerkawinan)
        assertEquals("WIRASWASTA", result.pekerjaan)
        assertEquals("WNI", result.kewarganegaraan)
    }

    @Test
    fun securityCrypto_encryptsAndDecryptsSensitiveData() {
        val originalNik = "3372011405880001"
        val encrypted = SecurityCryptoUtils.encryptSensitiveText(originalNik)
        assertNotEquals(originalNik, encrypted)
        val decrypted = SecurityCryptoUtils.decryptSensitiveText(encrypted)
        assertEquals(originalNik, decrypted)

        val pinHash = SecurityCryptoUtils.hashAdminPin("123456")
        assertTrue(SecurityCryptoUtils.verifyAdminPin("123456", pinHash))
        assertFalse(SecurityCryptoUtils.verifyAdminPin("000000", pinHash))
    }

    @Test
    fun perjanjianSewa_hasExpectedDefaultPoints() {
        assertTrue(DEFAULT_PERJANJIAN_SEWA.contains("Penyewa bertanggung jawab atas unit freezer"))
        assertTrue(DEFAULT_PERJANJIAN_SEWA.contains("Penyewa wajib menjaga kebersihan"))
        assertTrue(DEFAULT_PERJANJIAN_SEWA.lines().size >= 7)
    }

    @Test
    fun customerEntityAndPdfTemplateSettings_preservePermanentIdAndA4Settings() {
        val customer = com.example.data.local.CustomerEntity(
            id = 5L,
            kodePelanggan = "CUST/SAF/0005",
            namaLengkap = "Siti Aminah",
            nik = "3372015505900001",
            nomorHp = "081234567890",
            alamatLengkap = "Jl. Slamet Riyadi No. 12, Surakarta",
            jenisKelamin = "Perempuan",
            tanggalRegistrasi = "2026-11-10",
            lokasiPenggunaanDefault = "Ruko Solo Baru Blok A2",
            latitudeDefault = -7.5991,
            longitudeDefault = 110.8162
        )
        assertEquals("CUST/SAF/0005", customer.kodePelanggan)
        assertEquals("Ruko Solo Baru Blok A2", customer.lokasiPenggunaanDefault)

        val defaultTemplate = com.example.data.local.PdfTemplateSettings()
        assertEquals("A4", defaultTemplate.paperSize)
        assertEquals("LEFT", defaultTemplate.logoPosition)
        assertEquals(0.16f, defaultTemplate.backgroundOpacity, 0.001f)
        assertEquals(44f, defaultTemplate.logoSizePt, 0.001f)
    }

    @Test
    fun sewaHakMilikProgram_supportsAllRequiredPeriodsAndSeparatesFromSewaReguler() {
        val periods = com.example.data.local.DEFAULT_SEWA_HAK_MILIK_PERIODS
        assertEquals(listOf(12, 15, 18, 19, 20, 24), periods)

        val regulerTx = com.example.data.local.RentalTransactionEntity(
            nomorInvoice = "INV/SAF/20261110/0001",
            tanggalPemesanan = "2026-11-10",
            customerId = 1L,
            namaPelangganSnapshot = "Budi Santoso",
            unitId = 1L,
            namaUnitSnapshot = "RSA CF-310",
            kodeUnitSnapshot = "SAF-001",
            jenisProgram = "Sewa Reguler",
            totalPeriodeHakMilik = 0,
            pembayaranKeHakMilik = 1,
            statusProgramHakMilik = "Aktif",
            hargaSewa = 350_000L,
            durasiSewaBulan = 1,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-10",
            tanggalAkhirSewa = "2026-12-10",
            tanggalJatuhTempo = "2026-12-10",
            nominalPembayaran = 350_000L,
            totalTagihan = 350_000L,
            statusPembayaran = "Lunas",
            statusPenyewaan = "Dalam Penyewaan",
            metodePembayaran = "Transfer BCA"
        )

        val hakMilikTx = regulerTx.copy(
            nomorInvoice = "INV/SAF/20261110/0002",
            jenisProgram = "Sewa Hak Milik",
            totalPeriodeHakMilik = 18,
            pembayaranKeHakMilik = 18,
            statusProgramHakMilik = "Lunas"
        )

        assertEquals("Sewa Reguler", regulerTx.jenisProgram)
        assertEquals(0, regulerTx.totalPeriodeHakMilik)
        assertEquals("Sewa Hak Milik", hakMilikTx.jenisProgram)
        assertEquals(18, hakMilikTx.totalPeriodeHakMilik)
        assertEquals(18, hakMilikTx.pembayaranKeHakMilik)
        assertEquals(0, hakMilikTx.totalPeriodeHakMilik - hakMilikTx.pembayaranKeHakMilik)
        assertEquals("Lunas", hakMilikTx.statusProgramHakMilik)
    }

    @Test
    fun ktpOcrParser_separatesAll15FieldsAndFlagsUnreadableFieldsWithoutFakingData() {
        val fullKtpText = """
            PROVINSI JAWA TENGAH
            KOTA SURAKARTA
            NIK : 3372041807920007
            Nama : HENDRA WIJAYA PUTRA
            Tempat/Tgl Lahir : SURAKARTA, 18-07-1992
            Jenis Kelamin : LAKI-LAKI Gol. Darah : O
            Alamat : JL. SLAMET RIYADI NO. 158
            RT/RW : 003/008
            Kel/Desa : TIMURAN
            Kecamatan : BANJARSARI
            Agama : ISLAM
            Status Perkawinan: KAWIN
            Pekerjaan : WIRASWASTA
            Kewarganegaraan: WNI
            Berlaku Hingga : SEUMUR HIDUP
        """.trimIndent()

        val fullResult = KtpOcrScanner.parseIndonesianKtpText(fullKtpText, "/tmp/ktp.jpg")
        assertEquals("SURAKARTA", fullResult.tempatLahir)
        assertEquals("18-07-1992", fullResult.tanggalLahir)
        assertEquals("003", fullResult.rt)
        assertEquals("008", fullResult.rw)
        assertEquals("KOTA SURAKARTA", fullResult.kabupatenKota)
        assertEquals("SEUMUR HIDUP", fullResult.berlakuHingga)
        assertTrue(fullResult.unreadableFields.isEmpty())

        val partialKtpText = """
            PROVINSI JAWA TENGAH
            NIK : 3372041807920007
            Nama : HENDRA WIJAYA PUTRA
        """.trimIndent()
        val partialResult = KtpOcrScanner.parseIndonesianKtpText(partialKtpText, "/tmp/ktp2.jpg")
        assertEquals("", partialResult.tempatLahir)
        assertEquals("", partialResult.tanggalLahir)
        assertEquals("", partialResult.rt)
        assertEquals("", partialResult.rw)
        assertTrue(partialResult.unreadableFields.contains("Tempat Lahir"))
        assertTrue(partialResult.unreadableFields.contains("Tanggal Lahir"))
    }

    @Test
    fun pdfPaymentStatusStamp_resolvesDynamicStampFromTransactionStatus() {
        val baseTx = com.example.data.local.RentalTransactionEntity(
            nomorInvoice = "INV/SAF/20261110/0010",
            tanggalPemesanan = "2026-11-10",
            customerId = 1L,
            namaPelangganSnapshot = "Ahmad",
            unitId = 1L,
            namaUnitSnapshot = "RSA CF-210",
            kodeUnitSnapshot = "SAF-002",
            hargaSewa = 300_000L,
            durasiSewaBulan = 1,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-10",
            tanggalAkhirSewa = "2026-12-10",
            tanggalJatuhTempo = "2026-12-10",
            nominalPembayaran = 300_000L,
            totalTagihan = 300_000L,
            statusPembayaran = "Lunas",
            statusPenyewaan = "Dalam Penyewaan",
            metodePembayaran = "Transfer BCA"
        )

        val lunasStamp = com.example.pdf.PdfTemplateRenderer.resolvePaymentStampLabel(baseTx)
        assertEquals("LUNAS", lunasStamp)

        val dpTx = baseTx.copy(statusPembayaran = "DP / Uang Muka", nominalPembayaran = 100_000L)
        val dpStamp = com.example.pdf.PdfTemplateRenderer.resolvePaymentStampLabel(dpTx)
        assertEquals("DP / UANG MUKA", dpStamp)

        val belumLunasTx = baseTx.copy(
            statusPembayaran = "Belum Lunas",
            nominalPembayaran = 0L,
            tanggalJatuhTempo = "2099-12-31"
        )
        val belumLunasStamp = com.example.pdf.PdfTemplateRenderer.resolvePaymentStampLabel(belumLunasTx)
        assertEquals("BELUM LUNAS", belumLunasStamp)

        val terlambatTx = baseTx.copy(
            statusPembayaran = "Belum Lunas",
            nominalPembayaran = 0L,
            tanggalJatuhTempo = "2020-01-01"
        )
        val terlambatStamp = com.example.pdf.PdfTemplateRenderer.resolvePaymentStampLabel(terlambatTx)
        assertEquals("TERLAMBAT", terlambatStamp)
    }
}



