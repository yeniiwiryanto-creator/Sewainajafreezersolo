package com.example.backup

import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerDocumentEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerLocationEntity
import com.example.data.local.CustomerPhotoEntity
import com.example.data.local.CustomerTimelineEventEntity
import com.example.data.local.FreezerReturnEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentMethodEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.PdfTemplateSettings
import com.example.data.local.RentalExtensionHistoryEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.SmartNotificationSettings
import com.example.data.local.TransactionDocumentEntity
import com.example.data.local.TransactionSignatureEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitPaymentAllocationEntity
import com.example.util.SecurityCryptoUtils
import org.json.JSONArray
import org.json.JSONObject

data class BackupDatabaseSnapshot(
    val customers: List<CustomerEntity>,
    val units: List<FreezerUnitEntity>,
    val transactions: List<RentalTransactionEntity>,
    val unitItems: List<TransactionUnitItemEntity>,
    val paymentProofs: List<PaymentProofEntity>,
    val unitAllocations: List<UnitPaymentAllocationEntity>,
    val signatures: List<TransactionSignatureEntity>,
    val returns: List<FreezerReturnEntity>,
    val extensions: List<RentalExtensionHistoryEntity>,
    val paymentMethods: List<PaymentMethodEntity>,
    val customerDocuments: List<CustomerDocumentEntity>,
    val transactionDocuments: List<TransactionDocumentEntity>,
    val customerLocations: List<CustomerLocationEntity>,
    val customerPhotos: List<CustomerPhotoEntity>,
    val timelineEvents: List<CustomerTimelineEventEntity>,
    val companyProfile: CompanyProfile = CompanyProfile(),
    val perjanjianSewaText: String = "",
    val pdfTemplateSettings: PdfTemplateSettings = PdfTemplateSettings(),
    val smartNotificationSettings: SmartNotificationSettings = SmartNotificationSettings(),
    val googleSheetUrl: String = "",
    val webhookUrl: String = "",
    val autoSyncEnabled: Boolean = false,
    val maskNikInPdf: Boolean = true,
    val imageQualityTier: String = "STANDAR"
)

/**
 * Full-fidelity JSON + AES-256-GCM envelope mapper for all SEWAin Aja Freezer SOC database tables.
 */
object BackupJsonMapper {

    fun encodePayload(json: JSONObject, encrypt: Boolean): String {
        val raw = json.toString(2)
        return if (encrypt) SecurityCryptoUtils.encryptEnvelopeJson(raw) else raw
    }

    fun decodePayload(text: String): JSONObject {
        val plain = SecurityCryptoUtils.decryptEnvelopeJson(text)
        return JSONObject(plain)
    }

    private fun optNullableDouble(obj: JSONObject, key: String): Double? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.optDouble(key)

    private fun optNullableLong(obj: JSONObject, key: String): Long? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.optLong(key)

    private fun optNullableFloat(obj: JSONObject, key: String): Float? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.optDouble(key).toFloat()

    fun customersToJson(list: List<CustomerEntity>): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("kodePelanggan", c.kodePelanggan)
                put("namaLengkap", c.namaLengkap)
                put("nik", c.nik)
                put("nomorHp", c.nomorHp)
                put("alamatLengkap", c.alamatLengkap)
                put("jenisKelamin", c.jenisKelamin)
                put("fotoKtpDepanUri", c.fotoKtpDepanUri)
                put("fotoKtpBelakangUri", c.fotoKtpBelakangUri)
                put("catatan", c.catatan)
                put("tanggalRegistrasi", c.tanggalRegistrasi)
                put("tempatLahir", c.tempatLahir)
                put("tanggalLahir", c.tanggalLahir)
                put("rtRw", c.rtRw)
                put("rt", c.rt)
                put("rw", c.rw)
                put("kelurahanDesa", c.kelurahanDesa)
                put("kecamatan", c.kecamatan)
                put("kabupatenKota", c.kabupatenKota)
                put("agama", c.agama)
                put("statusPerkawinan", c.statusPerkawinan)
                put("pekerjaan", c.pekerjaan)
                put("kewarganegaraan", c.kewarganegaraan)
                put("berlakuHingga", c.berlakuHingga)
                put("persetujuanDataKtpLokasi", c.persetujuanDataKtpLokasi)
                put("namaLokasiDefault", c.namaLokasiDefault)
                put("lokasiPenggunaanDefault", c.lokasiPenggunaanDefault)
                put("rtRwLokasiDefault", c.rtRwLokasiDefault)
                put("kelurahanLokasiDefault", c.kelurahanLokasiDefault)
                put("kecamatanLokasiDefault", c.kecamatanLokasiDefault)
                put("kabupatenKotaLokasiDefault", c.kabupatenKotaLokasiDefault)
                put("latitudeDefault", c.latitudeDefault ?: JSONObject.NULL)
                put("longitudeDefault", c.longitudeDefault ?: JSONObject.NULL)
                put("linkGoogleMapsDefault", c.linkGoogleMapsDefault)
                put("fotoKtpPreprocessedUri", c.fotoKtpPreprocessedUri)
                put("rawOcrText", c.rawOcrText)
                put("parsedOcrJson", c.parsedOcrJson)
                put("ocrFieldConfidencesJson", c.ocrFieldConfidencesJson)
                put("statusVerifikasiAdminOcr", c.statusVerifikasiAdminOcr)
                put("waktuPemindaianOcr", c.waktuPemindaianOcr)
                put("fotoPelangganUri", c.fotoPelangganUri)
                put("statusPelanggan", c.statusPelanggan)
                put("uuid", c.uuid)
                put("version", c.version)
                put("createdAtMillis", c.createdAtMillis)
                put("updatedAtMillis", c.updatedAtMillis)
                put("syncStatus", c.syncStatus)
                put("changedFieldsJson", c.changedFieldsJson)
                put("isDeleted", c.isDeleted)
                put("driveId", c.driveId)
            })
        }
        put("customers", arr)
    }

    fun customersFromJson(root: JSONObject): List<CustomerEntity> {
        val arr = root.optJSONArray("customers") ?: JSONArray()
        val now = System.currentTimeMillis()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val parsedCreated = o.optLong("createdAtMillis", 0L).takeIf { it > 0L } ?: now
                val parsedUpdated = o.optLong("updatedAtMillis", 0L).takeIf { it > 0L } ?: parsedCreated
                add(
                    CustomerEntity(
                        id = o.optLong("id", 0L),
                        kodePelanggan = o.optString("kodePelanggan", ""),
                        namaLengkap = o.optString("namaLengkap", ""),
                        nik = o.optString("nik", ""),
                        nomorHp = o.optString("nomorHp", ""),
                        alamatLengkap = o.optString("alamatLengkap", ""),
                        jenisKelamin = o.optString("jenisKelamin", "Laki-laki"),
                        fotoKtpDepanUri = o.optString("fotoKtpDepanUri", ""),
                        fotoKtpBelakangUri = o.optString("fotoKtpBelakangUri", ""),
                        catatan = o.optString("catatan", ""),
                        tanggalRegistrasi = o.optString("tanggalRegistrasi", ""),
                        tempatLahir = o.optString("tempatLahir", ""),
                        tanggalLahir = o.optString("tanggalLahir", ""),
                        rtRw = o.optString("rtRw", ""),
                        rt = o.optString("rt", ""),
                        rw = o.optString("rw", ""),
                        kelurahanDesa = o.optString("kelurahanDesa", ""),
                        kecamatan = o.optString("kecamatan", ""),
                        kabupatenKota = o.optString("kabupatenKota", "Kota Surakarta"),
                        agama = o.optString("agama", ""),
                        statusPerkawinan = o.optString("statusPerkawinan", ""),
                        pekerjaan = o.optString("pekerjaan", ""),
                        kewarganegaraan = o.optString("kewarganegaraan", "WNI"),
                        berlakuHingga = o.optString("berlakuHingga", "SEUMUR HIDUP"),
                        persetujuanDataKtpLokasi = o.optBoolean("persetujuanDataKtpLokasi", true),
                        namaLokasiDefault = o.optString("namaLokasiDefault", ""),
                        lokasiPenggunaanDefault = o.optString("lokasiPenggunaanDefault", ""),
                        rtRwLokasiDefault = o.optString("rtRwLokasiDefault", ""),
                        kelurahanLokasiDefault = o.optString("kelurahanLokasiDefault", ""),
                        kecamatanLokasiDefault = o.optString("kecamatanLokasiDefault", ""),
                        kabupatenKotaLokasiDefault = o.optString("kabupatenKotaLokasiDefault", "Kota Surakarta"),
                        latitudeDefault = optNullableDouble(o, "latitudeDefault"),
                        longitudeDefault = optNullableDouble(o, "longitudeDefault"),
                        linkGoogleMapsDefault = o.optString("linkGoogleMapsDefault", ""),
                        fotoKtpPreprocessedUri = o.optString("fotoKtpPreprocessedUri", ""),
                        rawOcrText = o.optString("rawOcrText", ""),
                        parsedOcrJson = o.optString("parsedOcrJson", ""),
                        ocrFieldConfidencesJson = o.optString("ocrFieldConfidencesJson", ""),
                        statusVerifikasiAdminOcr = o.optString("statusVerifikasiAdminOcr", "TERVERIFIKASI_ADMIN"),
                        waktuPemindaianOcr = o.optString("waktuPemindaianOcr", ""),
                        fotoPelangganUri = o.optString("fotoPelangganUri", ""),
                        statusPelanggan = o.optString("statusPelanggan", "Pelanggan Aktif"),
                        uuid = o.optString("uuid", "").ifBlank { java.util.UUID.randomUUID().toString() },
                        version = o.optLong("version", 1L).coerceAtLeast(1L),
                        createdAtMillis = parsedCreated,
                        updatedAtMillis = parsedUpdated,
                        syncStatus = "PENDING_CREATE",
                        changedFieldsJson = o.optString("changedFieldsJson", ""),
                        isDeleted = false,
                        driveId = ""
                    )
                )
            }
        }
    }

    fun unitsToJson(list: List<FreezerUnitEntity>): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        list.forEach { u ->
            arr.put(JSONObject().apply {
                put("id", u.id)
                put("kodeUnit", u.kodeUnit)
                put("namaUnit", u.namaUnit)
                put("jenisUnit", u.jenisUnit)
                put("owner", u.owner)
                put("hargaPembelian", u.hargaPembelian)
                put("kapasitasLiter", u.kapasitasLiter)
                put("cicilanPerBulan", u.cicilanPerBulan)
                put("jumlahCicilanTotal", u.jumlahCicilanTotal)
                put("totalPembayaranCicilan", u.totalPembayaranCicilan)
                put("statusInvestasi", u.statusInvestasi)
                put("kategoriSheet", u.kategoriSheet)
                put("merek", u.merek)
                put("model", u.model)
                put("nomorSeri", u.nomorSeri)
                put("fotoUnitUri", u.fotoUnitUri)
                put("fotoKondisiFisikUri", u.fotoKondisiFisikUri)
                put("fotoBagianDalamUri", u.fotoBagianDalamUri)
                put("fotoMerekSeriUri", u.fotoMerekSeriUri)
                put("statusUnit", u.statusUnit)
                put("lokasiUnit", u.lokasiUnit)
                put("tanggalPembelian", u.tanggalPembelian)
                put("keterangan", u.keterangan)
                put("kondisiUnit", u.kondisiUnit)
                put("tanggalPengiriman", u.tanggalPengiriman)
                put("lokasiGpsPemasangan", u.lokasiGpsPemasangan)
                put("latitudePemasangan", u.latitudePemasangan ?: JSONObject.NULL)
                put("longitudePemasangan", u.longitudePemasangan ?: JSONObject.NULL)
                put("maintenanceHistory", u.maintenanceHistory)
                put("uuid", u.uuid)
                put("version", u.version)
                put("createdAtMillis", u.createdAtMillis)
                put("updatedAtMillis", u.updatedAtMillis)
                put("syncStatus", u.syncStatus)
                put("changedFieldsJson", u.changedFieldsJson)
                put("isDeleted", u.isDeleted)
                put("driveId", u.driveId)
            })
        }
        put("units", arr)
    }

    fun unitsFromJson(root: JSONObject): List<FreezerUnitEntity> {
        val arr = root.optJSONArray("units") ?: JSONArray()
        val now = System.currentTimeMillis()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val parsedCreated = o.optLong("createdAtMillis", 0L).takeIf { it > 0L } ?: now
                val parsedUpdated = o.optLong("updatedAtMillis", 0L).takeIf { it > 0L } ?: parsedCreated
                add(
                    FreezerUnitEntity(
                        id = o.optLong("id", 0L),
                        kodeUnit = o.optString("kodeUnit", ""),
                        namaUnit = o.optString("namaUnit", ""),
                        jenisUnit = o.optString("jenisUnit", "Chest Freezer"),
                        owner = o.optString("owner", "SEWAin Aja"),
                        hargaPembelian = o.optLong("hargaPembelian", 0L),
                        kapasitasLiter = o.optInt("kapasitasLiter", 100),
                        cicilanPerBulan = o.optLong("cicilanPerBulan", 0L),
                        jumlahCicilanTotal = o.optInt("jumlahCicilanTotal", 12),
                        totalPembayaranCicilan = o.optLong("totalPembayaranCicilan", 0L),
                        statusInvestasi = o.optString("statusInvestasi", "LIST"),
                        kategoriSheet = o.optString("kategoriSheet", "INVESTASI SEWA FREEZER"),
                        merek = o.optString("merek", ""),
                        model = o.optString("model", ""),
                        nomorSeri = o.optString("nomorSeri", ""),
                        fotoUnitUri = o.optString("fotoUnitUri", ""),
                        fotoKondisiFisikUri = o.optString("fotoKondisiFisikUri", ""),
                        fotoBagianDalamUri = o.optString("fotoBagianDalamUri", ""),
                        fotoMerekSeriUri = o.optString("fotoMerekSeriUri", ""),
                        statusUnit = o.optString("statusUnit", "Tersedia"),
                        lokasiUnit = o.optString("lokasiUnit", ""),
                        tanggalPembelian = o.optString("tanggalPembelian", ""),
                        keterangan = o.optString("keterangan", ""),
                        kondisiUnit = o.optString("kondisiUnit", "Baik & Siap Pakai"),
                        tanggalPengiriman = o.optString("tanggalPengiriman", ""),
                        lokasiGpsPemasangan = o.optString("lokasiGpsPemasangan", ""),
                        latitudePemasangan = optNullableDouble(o, "latitudePemasangan"),
                        longitudePemasangan = optNullableDouble(o, "longitudePemasangan"),
                        maintenanceHistory = o.optString("maintenanceHistory", ""),
                        uuid = o.optString("uuid", "").ifBlank { java.util.UUID.randomUUID().toString() },
                        version = o.optLong("version", 1L).coerceAtLeast(1L),
                        createdAtMillis = parsedCreated,
                        updatedAtMillis = parsedUpdated,
                        syncStatus = "PENDING_CREATE",
                        changedFieldsJson = o.optString("changedFieldsJson", ""),
                        isDeleted = false,
                        driveId = ""
                    )
                )
            }
        }
    }

    fun transactionsToJson(list: List<RentalTransactionEntity>): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("nomorInvoice", t.nomorInvoice)
                put("tanggalPemesanan", t.tanggalPemesanan)
                put("customerId", t.customerId)
                put("namaPelangganSnapshot", t.namaPelangganSnapshot)
                put("unitId", t.unitId)
                put("namaUnitSnapshot", t.namaUnitSnapshot)
                put("kodeUnitSnapshot", t.kodeUnitSnapshot)
                put("hargaSewa", t.hargaSewa)
                put("durasiSewaBulan", t.durasiSewaBulan)
                put("durasiHari", t.durasiHari)
                put("tanggalMulaiSewa", t.tanggalMulaiSewa)
                put("tanggalAkhirSewa", t.tanggalAkhirSewa)
                put("tanggalJatuhTempo", t.tanggalJatuhTempo)
                put("nominalPembayaran", t.nominalPembayaran)
                put("ongkir", t.ongkir)
                put("deposit", t.deposit)
                put("diskon", t.diskon)
                put("totalTagihan", t.totalTagihan)
                put("statusPembayaran", t.statusPembayaran)
                put("statusPenyewaan", t.statusPenyewaan)
                put("metodePembayaran", t.metodePembayaran)
                put("catatan", t.catatan)
                put("fotoKtpDepanTransUri", t.fotoKtpDepanTransUri)
                put("fotoKtpBelakangTransUri", t.fotoKtpBelakangTransUri)
                put("fotoUnitSebelumKirimUri", t.fotoUnitSebelumKirimUri)
                put("fotoKondisiFisikTransUri", t.fotoKondisiFisikTransUri)
                put("fotoBagianDalamTransUri", t.fotoBagianDalamTransUri)
                put("fotoMerekSeriTransUri", t.fotoMerekSeriTransUri)
                put("fotoSerahTerimaUri", t.fotoSerahTerimaUri)
                put("fotoKondisiSerahTerimaUri", t.fotoKondisiSerahTerimaUri)
                put("fotoPelangganBersamaFreezerUri", t.fotoPelangganBersamaFreezerUri)
                put("fotoFreezerDiLokasiUri", t.fotoFreezerDiLokasiUri)
                put("fotoDepanLokasiUri", t.fotoDepanLokasiUri)
                put("namaLokasiUsaha", t.namaLokasiUsaha)
                put("namaPenanggungJawab", t.namaPenanggungJawab)
                put("nomorWhatsappLokasi", t.nomorWhatsappLokasi)
                put("alamatLokasiGps", t.alamatLokasiGps)
                put("rtRwLokasi", t.rtRwLokasi)
                put("provinsiLokasi", t.provinsiLokasi)
                put("kabupatenKotaLokasi", t.kabupatenKotaLokasi)
                put("kecamatanLokasi", t.kecamatanLokasi)
                put("kelurahanDesaLokasi", t.kelurahanDesaLokasi)
                put("kodePosLokasi", t.kodePosLokasi)
                put("latitude", t.latitude ?: JSONObject.NULL)
                put("longitude", t.longitude ?: JSONObject.NULL)
                put("akurasiGpsMeter", t.akurasiGpsMeter?.toDouble() ?: JSONObject.NULL)
                put("linkGoogleMaps", t.linkGoogleMaps)
                put("catatanLokasi", t.catatanLokasi)
                put("tandaTanganPelangganUri", t.tandaTanganPelangganUri)
                put("tandaTanganPetugasUri", t.tandaTanganPetugasUri)
                put("namaPenerima", t.namaPenerima)
                put("tanggalPenerimaan", t.tanggalPenerimaan)
                put("jamPenerimaan", t.jamPenerimaan)
                put("namaPetugas", t.namaPetugas)
                put("keteranganKondisiFreezer", t.keteranganKondisiFreezer)
                put("persetujuanPelangganTrans", t.persetujuanPelangganTrans)
                put("pdfInvoicePath", t.pdfInvoicePath)
                put("isiPerjanjianSewaSnapshot", t.isiPerjanjianSewaSnapshot)
                put("parentTransactionId", t.parentTransactionId ?: JSONObject.NULL)
                put("periodeBulanLabel", t.periodeBulanLabel)
                put("jenisProgram", t.jenisProgram)
                put("totalPeriodeHakMilik", t.totalPeriodeHakMilik)
                put("pembayaranKeHakMilik", t.pembayaranKeHakMilik)
                put("statusProgramHakMilik", t.statusProgramHakMilik)
                put("uuid", t.uuid)
                put("version", t.version)
                put("createdAtMillis", t.createdAtMillis)
                put("updatedAtMillis", t.updatedAtMillis)
                put("syncStatus", t.syncStatus)
                put("changedFieldsJson", t.changedFieldsJson)
                put("isDeleted", t.isDeleted)
                put("driveId", t.driveId)
            })
        }
        put("transactions", arr)
    }

    fun transactionsFromJson(root: JSONObject): List<RentalTransactionEntity> {
        val arr = root.optJSONArray("transactions") ?: JSONArray()
        val now = System.currentTimeMillis()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val parsedCreated = o.optLong("createdAtMillis", 0L).takeIf { it > 0L } ?: now
                val parsedUpdated = o.optLong("updatedAtMillis", 0L).takeIf { it > 0L } ?: parsedCreated
                add(
                    RentalTransactionEntity(
                        id = o.optLong("id", 0L),
                        nomorInvoice = o.optString("nomorInvoice", ""),
                        tanggalPemesanan = o.optString("tanggalPemesanan", ""),
                        customerId = o.optLong("customerId", 0L),
                        namaPelangganSnapshot = o.optString("namaPelangganSnapshot", ""),
                        unitId = o.optLong("unitId", 0L),
                        namaUnitSnapshot = o.optString("namaUnitSnapshot", ""),
                        kodeUnitSnapshot = o.optString("kodeUnitSnapshot", ""),
                        hargaSewa = o.optLong("hargaSewa", 0L),
                        durasiSewaBulan = o.optInt("durasiSewaBulan", 1),
                        durasiHari = o.optInt("durasiHari", 30),
                        tanggalMulaiSewa = o.optString("tanggalMulaiSewa", ""),
                        tanggalAkhirSewa = o.optString("tanggalAkhirSewa", ""),
                        tanggalJatuhTempo = o.optString("tanggalJatuhTempo", ""),
                        nominalPembayaran = o.optLong("nominalPembayaran", 0L),
                        ongkir = o.optLong("ongkir", 0L),
                        deposit = o.optLong("deposit", 0L),
                        diskon = o.optLong("diskon", 0L),
                        totalTagihan = o.optLong("totalTagihan", 0L),
                        statusPembayaran = o.optString("statusPembayaran", "Belum Bayar"),
                        statusPenyewaan = o.optString("statusPenyewaan", "Dalam Penyewaan"),
                        metodePembayaran = o.optString("metodePembayaran", "Transfer BCA"),
                        catatan = o.optString("catatan", ""),
                        fotoKtpDepanTransUri = o.optString("fotoKtpDepanTransUri", ""),
                        fotoKtpBelakangTransUri = o.optString("fotoKtpBelakangTransUri", ""),
                        fotoUnitSebelumKirimUri = o.optString("fotoUnitSebelumKirimUri", ""),
                        fotoKondisiFisikTransUri = o.optString("fotoKondisiFisikTransUri", ""),
                        fotoBagianDalamTransUri = o.optString("fotoBagianDalamTransUri", ""),
                        fotoMerekSeriTransUri = o.optString("fotoMerekSeriTransUri", ""),
                        fotoSerahTerimaUri = o.optString("fotoSerahTerimaUri", ""),
                        fotoKondisiSerahTerimaUri = o.optString("fotoKondisiSerahTerimaUri", ""),
                        fotoPelangganBersamaFreezerUri = o.optString("fotoPelangganBersamaFreezerUri", ""),
                        fotoFreezerDiLokasiUri = o.optString("fotoFreezerDiLokasiUri", ""),
                        fotoDepanLokasiUri = o.optString("fotoDepanLokasiUri", ""),
                        namaLokasiUsaha = o.optString("namaLokasiUsaha", ""),
                        namaPenanggungJawab = o.optString("namaPenanggungJawab", ""),
                        nomorWhatsappLokasi = o.optString("nomorWhatsappLokasi", ""),
                        alamatLokasiGps = o.optString("alamatLokasiGps", ""),
                        rtRwLokasi = o.optString("rtRwLokasi", ""),
                        provinsiLokasi = o.optString("provinsiLokasi", "Jawa Tengah"),
                        kabupatenKotaLokasi = o.optString("kabupatenKotaLokasi", "Kota Surakarta"),
                        kecamatanLokasi = o.optString("kecamatanLokasi", ""),
                        kelurahanDesaLokasi = o.optString("kelurahanDesaLokasi", ""),
                        kodePosLokasi = o.optString("kodePosLokasi", ""),
                        latitude = optNullableDouble(o, "latitude"),
                        longitude = optNullableDouble(o, "longitude"),
                        akurasiGpsMeter = optNullableFloat(o, "akurasiGpsMeter"),
                        linkGoogleMaps = o.optString("linkGoogleMaps", ""),
                        catatanLokasi = o.optString("catatanLokasi", ""),
                        tandaTanganPelangganUri = o.optString("tandaTanganPelangganUri", ""),
                        tandaTanganPetugasUri = o.optString("tandaTanganPetugasUri", ""),
                        namaPenerima = o.optString("namaPenerima", ""),
                        tanggalPenerimaan = o.optString("tanggalPenerimaan", ""),
                        jamPenerimaan = o.optString("jamPenerimaan", ""),
                        namaPetugas = o.optString("namaPetugas", ""),
                        keteranganKondisiFreezer = o.optString("keteranganKondisiFreezer", ""),
                        persetujuanPelangganTrans = o.optBoolean("persetujuanPelangganTrans", true),
                        pdfInvoicePath = o.optString("pdfInvoicePath", ""),
                        isiPerjanjianSewaSnapshot = o.optString("isiPerjanjianSewaSnapshot", ""),
                        parentTransactionId = optNullableLong(o, "parentTransactionId"),
                        periodeBulanLabel = o.optString("periodeBulanLabel", ""),
                        jenisProgram = o.optString("jenisProgram", "Sewa Reguler"),
                        totalPeriodeHakMilik = o.optInt("totalPeriodeHakMilik", 0),
                        pembayaranKeHakMilik = o.optInt("pembayaranKeHakMilik", 1),
                        statusProgramHakMilik = o.optString("statusProgramHakMilik", "Aktif"),
                        uuid = o.optString("uuid", "").ifBlank { java.util.UUID.randomUUID().toString() },
                        version = o.optLong("version", 1L).coerceAtLeast(1L),
                        createdAtMillis = parsedCreated,
                        updatedAtMillis = parsedUpdated,
                        syncStatus = "PENDING_CREATE",
                        changedFieldsJson = o.optString("changedFieldsJson", ""),
                        isDeleted = false,
                        driveId = ""
                    )
                )
            }
        }
    }

    fun paymentsToJson(
        proofs: List<PaymentProofEntity>,
        allocations: List<UnitPaymentAllocationEntity>
    ): JSONObject = JSONObject().apply {
        put("paymentProofs", JSONArray().apply {
            proofs.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("transactionId", p.transactionId)
                    put("tanggalPembayaran", p.tanggalPembayaran)
                    put("nominalPembayaran", p.nominalPembayaran)
                    put("metodePembayaran", p.metodePembayaran)
                    put("namaPengirim", p.namaPengirim)
                    put("catatanPembayaran", p.catatanPembayaran)
                    put("fotoBuktiUri", p.fotoBuktiUri)
                    put("waktuUpload", p.waktuUpload)
                    put("unitItemId", p.unitItemId ?: JSONObject.NULL)
                    put("alokasiUnitJson", p.alokasiUnitJson)
                    put("uuid", p.uuid)
                    put("version", p.version)
                    put("createdAtMillis", p.createdAtMillis)
                    put("updatedAtMillis", p.updatedAtMillis)
                    put("syncStatus", p.syncStatus)
                    put("changedFieldsJson", p.changedFieldsJson)
                    put("isDeleted", p.isDeleted)
                    put("driveId", p.driveId)
                })
            }
        })
        put("unitPaymentAllocations", JSONArray().apply {
            allocations.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("paymentProofId", a.paymentProofId)
                    put("transactionId", a.transactionId)
                    put("unitItemId", a.unitItemId)
                    put("customerId", a.customerId)
                    put("unitId", a.unitId)
                    put("namaUnitSnapshot", a.namaUnitSnapshot)
                    put("kodeUnitSnapshot", a.kodeUnitSnapshot)
                    put("allocatedAmount", a.allocatedAmount)
                    put("paymentDate", a.paymentDate)
                    put("periodeTagihan", a.periodeTagihan)
                    put("metodePembayaran", a.metodePembayaran)
                    put("catatan", a.catatan)
                    put("createdAt", a.createdAt)
                })
            }
        })
    }

    fun paymentProofsFromJson(root: JSONObject): List<PaymentProofEntity> {
        val arr = root.optJSONArray("paymentProofs") ?: JSONArray()
        val now = System.currentTimeMillis()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val parsedCreated = o.optLong("createdAtMillis", 0L).takeIf { it > 0L } ?: now
                val parsedUpdated = o.optLong("updatedAtMillis", 0L).takeIf { it > 0L } ?: parsedCreated
                add(
                    PaymentProofEntity(
                        id = o.optLong("id", 0L),
                        transactionId = o.optLong("transactionId", 0L),
                        tanggalPembayaran = o.optString("tanggalPembayaran", ""),
                        nominalPembayaran = o.optLong("nominalPembayaran", 0L),
                        metodePembayaran = o.optString("metodePembayaran", "Transfer BCA"),
                        namaPengirim = o.optString("namaPengirim", ""),
                        catatanPembayaran = o.optString("catatanPembayaran", ""),
                        fotoBuktiUri = o.optString("fotoBuktiUri", ""),
                        waktuUpload = o.optString("waktuUpload", ""),
                        unitItemId = optNullableLong(o, "unitItemId"),
                        alokasiUnitJson = o.optString("alokasiUnitJson", ""),
                        uuid = o.optString("uuid", "").ifBlank { java.util.UUID.randomUUID().toString() },
                        version = o.optLong("version", 1L).coerceAtLeast(1L),
                        createdAtMillis = parsedCreated,
                        updatedAtMillis = parsedUpdated,
                        syncStatus = "PENDING_CREATE",
                        changedFieldsJson = o.optString("changedFieldsJson", ""),
                        isDeleted = false,
                        driveId = ""
                    )
                )
            }
        }
    }

    fun allocationsFromJson(root: JSONObject): List<UnitPaymentAllocationEntity> {
        val arr = root.optJSONArray("unitPaymentAllocations") ?: JSONArray()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    UnitPaymentAllocationEntity(
                        id = o.optLong("id", 0L),
                        paymentProofId = o.optLong("paymentProofId", 0L),
                        transactionId = o.optLong("transactionId", 0L),
                        unitItemId = o.optLong("unitItemId", 0L),
                        customerId = o.optLong("customerId", 0L),
                        unitId = o.optLong("unitId", 0L),
                        namaUnitSnapshot = o.optString("namaUnitSnapshot", ""),
                        kodeUnitSnapshot = o.optString("kodeUnitSnapshot", ""),
                        allocatedAmount = o.optLong("allocatedAmount", 0L),
                        paymentDate = o.optString("paymentDate", ""),
                        periodeTagihan = o.optString("periodeTagihan", ""),
                        metodePembayaran = o.optString("metodePembayaran", ""),
                        catatan = o.optString("catatan", ""),
                        createdAt = o.optString("createdAt", "")
                    )
                )
            }
        }
    }

    fun settingsToJson(
        companyProfile: CompanyProfile,
        paymentMethods: List<PaymentMethodEntity>,
        perjanjianSewaText: String,
        imageQualityTier: String,
        googleSheetUrl: String,
        webhookUrl: String,
        autoSyncEnabled: Boolean,
        maskNikInPdf: Boolean
    ): JSONObject = JSONObject().apply {
        put("companyProfile", JSONObject().apply {
            put("namaPerusahaan", companyProfile.namaPerusahaan)
            put("alamat", companyProfile.alamat)
            put("nomorHp", companyProfile.nomorHp)
            put("email", companyProfile.email)
            put("instagram", companyProfile.instagram)
            put("facebook", companyProfile.facebook)
            put("profilePictureUri", companyProfile.profilePictureUri)
            put("identitasUsaha", companyProfile.identitasUsaha)
            put("informasiTambahan", companyProfile.informasiTambahan)
            put("ownerSignatureUri", companyProfile.ownerSignatureUri)
            put("ownerSignatureSizePt", companyProfile.ownerSignatureSizePt.toDouble())
            put("ownerSignaturePosition", companyProfile.ownerSignaturePosition)
            put("ownerName", companyProfile.ownerName)
        })
        put("paymentMethods", JSONArray().apply {
            paymentMethods.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("namaMetode", m.namaMetode)
                    put("kategoriMetode", m.kategoriMetode)
                    put("namaBankAtauPenyedia", m.namaBankAtauPenyedia)
                    put("nomorRekening", m.nomorRekening)
                    put("namaPemilikRekening", m.namaPemilikRekening)
                    put("fotoQrisUri", m.fotoQrisUri)
                    put("logoBankUri", m.logoBankUri)
                    put("keteranganPembayaran", m.keteranganPembayaran)
                    put("isAktif", m.isAktif)
                })
            }
        })
        put("perjanjianSewaText", perjanjianSewaText)
        put("imageQualityTier", imageQualityTier)
        put("googleSheetUrl", googleSheetUrl)
        put("webhookUrl", webhookUrl)
        put("autoSyncEnabled", autoSyncEnabled)
        put("maskNikInPdf", maskNikInPdf)
    }

    fun paymentMethodsFromJson(root: JSONObject): List<PaymentMethodEntity> {
        val arr = root.optJSONArray("paymentMethods") ?: JSONArray()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    PaymentMethodEntity(
                        id = o.optLong("id", 0L),
                        namaMetode = o.optString("namaMetode", ""),
                        kategoriMetode = o.optString("kategoriMetode", "TRANSFER_BANK"),
                        namaBankAtauPenyedia = o.optString("namaBankAtauPenyedia", ""),
                        nomorRekening = o.optString("nomorRekening", ""),
                        namaPemilikRekening = o.optString("namaPemilikRekening", ""),
                        fotoQrisUri = o.optString("fotoQrisUri", ""),
                        logoBankUri = o.optString("logoBankUri", ""),
                        keteranganPembayaran = o.optString("keteranganPembayaran", ""),
                        isAktif = o.optBoolean("isAktif", true)
                    )
                )
            }
        }
    }

    fun companyProfileFromJson(root: JSONObject): CompanyProfile {
        val cp = root.optJSONObject("companyProfile") ?: return CompanyProfile()
        return CompanyProfile(
            namaPerusahaan = cp.optString("namaPerusahaan", "SEWAin Aja Freezer SOC"),
            alamat = cp.optString("alamat", ""),
            nomorHp = cp.optString("nomorHp", ""),
            email = cp.optString("email", ""),
            instagram = cp.optString("instagram", ""),
            facebook = cp.optString("facebook", ""),
            profilePictureUri = cp.optString("profilePictureUri", ""),
            identitasUsaha = cp.optString("identitasUsaha", ""),
            informasiTambahan = cp.optString("informasiTambahan", ""),
            ownerSignatureUri = cp.optString("ownerSignatureUri", ""),
            ownerSignatureSizePt = cp.optDouble("ownerSignatureSizePt", 42.0).toFloat(),
            ownerSignaturePosition = cp.optString("ownerSignaturePosition", "RIGHT"),
            ownerName = cp.optString("ownerName", "Owner SEWAin Aja Freezer SOC")
        )
    }
}
