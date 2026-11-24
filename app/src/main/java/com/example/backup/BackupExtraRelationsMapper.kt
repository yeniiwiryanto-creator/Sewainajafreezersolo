package com.example.backup

import com.example.data.local.CustomerDocumentEntity
import com.example.data.local.CustomerLocationEntity
import com.example.data.local.CustomerPhotoEntity
import com.example.data.local.CustomerTimelineEventEntity
import com.example.data.local.FreezerReturnEntity
import com.example.data.local.RentalExtensionHistoryEntity
import com.example.data.local.TransactionDocumentEntity
import com.example.data.local.TransactionSignatureEntity
import com.example.data.local.TransactionUnitItemEntity
import org.json.JSONArray
import org.json.JSONObject

object BackupExtraRelationsMapper {

    fun toJson(snapshot: BackupDatabaseSnapshot): JSONObject = JSONObject().apply {
        put("unitItems", JSONArray().apply {
            snapshot.unitItems.forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id)
                    put("transactionId", i.transactionId)
                    put("customerId", i.customerId)
                    put("unitId", i.unitId)
                    put("unitIndexNumber", i.unitIndexNumber)
                    put("kodeUnitSnapshot", i.kodeUnitSnapshot)
                    put("namaUnitSnapshot", i.namaUnitSnapshot)
                    put("jenisUnitSnapshot", i.jenisUnitSnapshot)
                    put("merekSnapshot", i.merekSnapshot)
                    put("kapasitasLiterSnapshot", i.kapasitasLiterSnapshot)
                    put("jenisProgram", i.jenisProgram)
                    put("totalPeriodeHakMilik", i.totalPeriodeHakMilik)
                    put("pembayaranKeHakMilik", i.pembayaranKeHakMilik)
                    put("statusProgramHakMilik", i.statusProgramHakMilik)
                    put("hargaSewa", i.hargaSewa)
                    put("durasiHari", i.durasiHari)
                    put("tanggalMulaiSewa", i.tanggalMulaiSewa)
                    put("tanggalAkhirSewa", i.tanggalAkhirSewa)
                    put("tanggalJatuhTempo", i.tanggalJatuhTempo)
                    put("nominalPembayaran", i.nominalPembayaran)
                    put("statusPembayaran", i.statusPembayaran)
                    put("statusPenyewaan", i.statusPenyewaan)
                    put("catatanUnit", i.catatanUnit)
                    put("fotoKondisiDepanUri", i.fotoKondisiDepanUri)
                    put("fotoKondisiSampingUri", i.fotoKondisiSampingUri)
                    put("fotoKondisiDalamUri", i.fotoKondisiDalamUri)
                    put("fotoKondisiTambahanUri", i.fotoKondisiTambahanUri)
                    put("fotoSerahTerimaPelangganUri", i.fotoSerahTerimaPelangganUri)
                    put("fotoSerahTerimaLokasiUri", i.fotoSerahTerimaLokasiUri)
                    put("fotoBuktiPenerimaanUri", i.fotoBuktiPenerimaanUri)
                    put("fotoSerahTerimaTambahanUri", i.fotoSerahTerimaTambahanUri)
                    put("storageFolderPath", i.storageFolderPath)
                    put("createdAt", i.createdAt)
                    put("updatedAt", i.updatedAt)
                    put("periodeTagihan", i.periodeTagihan)
                })
            }
        })
        put("signatures", JSONArray().apply {
            snapshot.signatures.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("transactionId", s.transactionId)
                    put("customerId", s.customerId)
                    put("signerRole", s.signerRole)
                    put("signerName", s.signerName)
                    put("signatureMethod", s.signatureMethod)
                    put("filePath", s.filePath)
                    put("relativePath", s.relativePath)
                    put("hasTransparentBackground", s.hasTransparentBackground)
                    put("signedDate", s.signedDate)
                    put("createdAt", s.createdAt)
                })
            }
        })
        put("returns", JSONArray().apply {
            snapshot.returns.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("nomorDokumenPengembalian", r.nomorDokumenPengembalian)
                    put("transactionId", r.transactionId)
                    put("nomorInvoiceSnapshot", r.nomorInvoiceSnapshot)
                    put("customerId", r.customerId)
                    put("namaPelangganSnapshot", r.namaPelangganSnapshot)
                    put("nikPelangganSnapshot", r.nikPelangganSnapshot)
                    put("nomorWaPelangganSnapshot", r.nomorWaPelangganSnapshot)
                    put("alamatKtpSnapshot", r.alamatKtpSnapshot)
                    put("unitId", r.unitId)
                    put("kodeUnitSnapshot", r.kodeUnitSnapshot)
                    put("namaUnitSnapshot", r.namaUnitSnapshot)
                    put("merekTipeSnapshot", r.merekTipeSnapshot)
                    put("tanggalMulaiSewaSnapshot", r.tanggalMulaiSewaSnapshot)
                    put("hargaSewaSnapshot", r.hargaSewaSnapshot)
                    put("tanggalJatuhTempoSnapshot", r.tanggalJatuhTempoSnapshot)
                    put("alamatPenggunaanSnapshot", r.alamatPenggunaanSnapshot)
                    put("latitudeTerakhir", r.latitudeTerakhir ?: JSONObject.NULL)
                    put("longitudeTerakhir", r.longitudeTerakhir ?: JSONObject.NULL)
                    put("tanggalPengembalian", r.tanggalPengembalian)
                    put("jamPengembalian", r.jamPengembalian)
                    put("namaPetugasPenerima", r.namaPetugasPenerima)
                    put("kondisiUnitSaatKembali", r.kondisiUnitSaatKembali)
                    put("catatanKondisiUnit", r.catatanKondisiUnit)
                    put("statusUnitSetelahKembali", r.statusUnitSetelahKembali)
                    put("fotoKondisiKembaliUri", r.fotoKondisiKembaliUri)
                    put("fotoBagianLuarUri", r.fotoBagianLuarUri)
                    put("fotoBagianDalamUri", r.fotoBagianDalamUri)
                    put("fotoTambahanUri", r.fotoTambahanUri)
                    put("tandaTanganPelangganUri", r.tandaTanganPelangganUri)
                    put("tandaTanganPetugasUri", r.tandaTanganPetugasUri)
                    put("pdfBuktiPengembalianPath", r.pdfBuktiPengembalianPath)
                })
            }
        })
        put("extensions", JSONArray().apply {
            snapshot.extensions.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("originalTransactionId", e.originalTransactionId)
                    put("newTransactionId", e.newTransactionId)
                    put("nomorInvoiceLama", e.nomorInvoiceLama)
                    put("nomorInvoiceBaru", e.nomorInvoiceBaru)
                    put("tanggalPerpanjangan", e.tanggalPerpanjangan)
                    put("tanggalAkhirLama", e.tanggalAkhirLama)
                    put("tanggalAkhirBaru", e.tanggalAkhirBaru)
                    put("tambahanDurasiHari", e.tambahanDurasiHari)
                    put("tagihanBaru", e.tagihanBaru)
                    put("catatan", e.catatan)
                    put("uuid", e.uuid)
                    put("customerId", e.customerId)
                    put("unitId", e.unitId)
                    put("unitItemId", e.unitItemId)
                    put("jenisProgram", e.jenisProgram)
                    put("pembayaranKeHakMilik", e.pembayaranKeHakMilik)
                    put("totalPeriodeHakMilik", e.totalPeriodeHakMilik)
                    put("diskon", e.diskon)
                    put("nominalDibayar", e.nominalDibayar)
                    put("statusPembayaran", e.statusPembayaran)
                    put("updatedAtMillis", e.updatedAtMillis)
                })
            }
        })
        put("customerDocuments", JSONArray().apply {
            snapshot.customerDocuments.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id); put("customerId", d.customerId); put("documentType", d.documentType)
                    put("fileName", d.fileName); put("relativePath", d.relativePath); put("mimeType", d.mimeType)
                    put("fileSize", d.fileSize); put("checksum", d.checksum); put("createdAt", d.createdAt); put("updatedAt", d.updatedAt)
                })
            }
        })
        put("transactionDocuments", JSONArray().apply {
            snapshot.transactionDocuments.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id); put("transactionId", d.transactionId); put("customerId", d.customerId)
                    put("documentType", d.documentType); put("fileName", d.fileName); put("relativePath", d.relativePath)
                    put("mimeType", d.mimeType); put("fileSize", d.fileSize); put("checksum", d.checksum); put("createdAt", d.createdAt)
                })
            }
        })
        put("customerLocations", JSONArray().apply {
            snapshot.customerLocations.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("customerId", l.customerId); put("latitude", l.latitude); put("longitude", l.longitude)
                    put("address", l.address); put("locationSource", l.locationSource); put("accuracy", l.accuracy.toDouble())
                    put("createdAt", l.createdAt); put("updatedAt", l.updatedAt)
                })
            }
        })
        put("customerPhotos", JSONArray().apply {
            snapshot.customerPhotos.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("customerId", p.customerId)
                    put("transactionId", p.transactionId ?: JSONObject.NULL)
                    put("kategori", p.kategori); put("namaDokumentasi", p.namaDokumentasi)
                    put("filePath", p.filePath); put("relativeFolder", p.relativeFolder); put("fileName", p.fileName)
                    put("tanggalWaktuIso", p.tanggalWaktuIso)
                    put("latitude", p.latitude ?: JSONObject.NULL); put("longitude", p.longitude ?: JSONObject.NULL)
                    put("catatan", p.catatan); put("sizeBytes", p.sizeBytes)
                    put("unitId", p.unitId ?: JSONObject.NULL)
                    put("uuid", p.uuid); put("uploadStatus", p.uploadStatus)
                    put("driveFileId", p.driveFileId); put("updatedAtMillis", p.updatedAtMillis)
                })
            }
        })
        put("timelineEvents", JSONArray().apply {
            snapshot.timelineEvents.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("customerId", t.customerId)
                    put("transactionId", t.transactionId ?: JSONObject.NULL)
                    put("unitId", t.unitId ?: JSONObject.NULL)
                    put("eventType", t.eventType); put("title", t.title); put("description", t.description)
                    put("amount", t.amount); put("eventTimestamp", t.eventTimestamp); put("eventDateIso", t.eventDateIso)
                })
            }
        })
    }

    fun unitItemsFromJson(root: JSONObject): List<TransactionUnitItemEntity> {
        val arr = root.optJSONArray("unitItems") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    TransactionUnitItemEntity(
                        id = o.optLong("id", 0L),
                        transactionId = o.optLong("transactionId", 0L),
                        customerId = o.optLong("customerId", 0L),
                        unitId = o.optLong("unitId", 0L),
                        unitIndexNumber = o.optInt("unitIndexNumber", 1),
                        kodeUnitSnapshot = o.optString("kodeUnitSnapshot", ""),
                        namaUnitSnapshot = o.optString("namaUnitSnapshot", ""),
                        jenisUnitSnapshot = o.optString("jenisUnitSnapshot", ""),
                        merekSnapshot = o.optString("merekSnapshot", ""),
                        kapasitasLiterSnapshot = o.optInt("kapasitasLiterSnapshot", 0),
                        jenisProgram = o.optString("jenisProgram", "Sewa Reguler"),
                        totalPeriodeHakMilik = o.optInt("totalPeriodeHakMilik", 0),
                        pembayaranKeHakMilik = o.optInt("pembayaranKeHakMilik", 1),
                        statusProgramHakMilik = o.optString("statusProgramHakMilik", "Aktif"),
                        hargaSewa = o.optLong("hargaSewa", 0L),
                        durasiHari = o.optInt("durasiHari", 30),
                        tanggalMulaiSewa = o.optString("tanggalMulaiSewa", ""),
                        tanggalAkhirSewa = o.optString("tanggalAkhirSewa", ""),
                        tanggalJatuhTempo = o.optString("tanggalJatuhTempo", ""),
                        nominalPembayaran = o.optLong("nominalPembayaran", 0L),
                        statusPembayaran = o.optString("statusPembayaran", "Belum Bayar"),
                        statusPenyewaan = o.optString("statusPenyewaan", "Dalam Penyewaan"),
                        catatanUnit = o.optString("catatanUnit", ""),
                        fotoKondisiDepanUri = o.optString("fotoKondisiDepanUri", ""),
                        fotoKondisiSampingUri = o.optString("fotoKondisiSampingUri", ""),
                        fotoKondisiDalamUri = o.optString("fotoKondisiDalamUri", ""),
                        fotoKondisiTambahanUri = o.optString("fotoKondisiTambahanUri", ""),
                        fotoSerahTerimaPelangganUri = o.optString("fotoSerahTerimaPelangganUri", ""),
                        fotoSerahTerimaLokasiUri = o.optString("fotoSerahTerimaLokasiUri", ""),
                        fotoBuktiPenerimaanUri = o.optString("fotoBuktiPenerimaanUri", ""),
                        fotoSerahTerimaTambahanUri = o.optString("fotoSerahTerimaTambahanUri", ""),
                        storageFolderPath = o.optString("storageFolderPath", ""),
                        createdAt = o.optString("createdAt", ""),
                        updatedAt = o.optString("updatedAt", ""),
                        periodeTagihan = o.optString("periodeTagihan", "")
                    )
                )
            }
        }
    }

    fun signaturesFromJson(root: JSONObject): List<TransactionSignatureEntity> {
        val arr = root.optJSONArray("signatures") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    TransactionSignatureEntity(
                        id = o.optLong("id", 0L),
                        transactionId = o.optLong("transactionId", 0L),
                        customerId = o.optLong("customerId", 0L),
                        signerRole = o.optString("signerRole", "PENYEWA"),
                        signerName = o.optString("signerName", ""),
                        signatureMethod = o.optString("signatureMethod", "DRAW"),
                        filePath = o.optString("filePath", ""),
                        relativePath = o.optString("relativePath", ""),
                        hasTransparentBackground = o.optBoolean("hasTransparentBackground", true),
                        signedDate = o.optString("signedDate", ""),
                        createdAt = o.optString("createdAt", "")
                    )
                )
            }
        }
    }

    fun returnsFromJson(root: JSONObject): List<FreezerReturnEntity> {
        val arr = root.optJSONArray("returns") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    FreezerReturnEntity(
                        id = o.optLong("id", 0L),
                        nomorDokumenPengembalian = o.optString("nomorDokumenPengembalian", ""),
                        transactionId = o.optLong("transactionId", 0L),
                        nomorInvoiceSnapshot = o.optString("nomorInvoiceSnapshot", ""),
                        customerId = o.optLong("customerId", 0L),
                        namaPelangganSnapshot = o.optString("namaPelangganSnapshot", ""),
                        nikPelangganSnapshot = o.optString("nikPelangganSnapshot", ""),
                        nomorWaPelangganSnapshot = o.optString("nomorWaPelangganSnapshot", ""),
                        alamatKtpSnapshot = o.optString("alamatKtpSnapshot", ""),
                        unitId = o.optLong("unitId", 0L),
                        kodeUnitSnapshot = o.optString("kodeUnitSnapshot", ""),
                        namaUnitSnapshot = o.optString("namaUnitSnapshot", ""),
                        merekTipeSnapshot = o.optString("merekTipeSnapshot", ""),
                        tanggalMulaiSewaSnapshot = o.optString("tanggalMulaiSewaSnapshot", ""),
                        hargaSewaSnapshot = o.optLong("hargaSewaSnapshot", 0L),
                        tanggalJatuhTempoSnapshot = o.optString("tanggalJatuhTempoSnapshot", ""),
                        alamatPenggunaanSnapshot = o.optString("alamatPenggunaanSnapshot", ""),
                        latitudeTerakhir = if (o.isNull("latitudeTerakhir")) null else o.optDouble("latitudeTerakhir"),
                        longitudeTerakhir = if (o.isNull("longitudeTerakhir")) null else o.optDouble("longitudeTerakhir"),
                        tanggalPengembalian = o.optString("tanggalPengembalian", ""),
                        jamPengembalian = o.optString("jamPengembalian", ""),
                        namaPetugasPenerima = o.optString("namaPetugasPenerima", ""),
                        kondisiUnitSaatKembali = o.optString("kondisiUnitSaatKembali", "Baik / Normal"),
                        catatanKondisiUnit = o.optString("catatanKondisiUnit", ""),
                        statusUnitSetelahKembali = o.optString("statusUnitSetelahKembali", "Tersedia"),
                        fotoKondisiKembaliUri = o.optString("fotoKondisiKembaliUri", ""),
                        fotoBagianLuarUri = o.optString("fotoBagianLuarUri", ""),
                        fotoBagianDalamUri = o.optString("fotoBagianDalamUri", ""),
                        fotoTambahanUri = o.optString("fotoTambahanUri", ""),
                        tandaTanganPelangganUri = o.optString("tandaTanganPelangganUri", ""),
                        tandaTanganPetugasUri = o.optString("tandaTanganPetugasUri", ""),
                        pdfBuktiPengembalianPath = o.optString("pdfBuktiPengembalianPath", "")
                    )
                )
            }
        }
    }

    fun extensionsFromJson(root: JSONObject): List<RentalExtensionHistoryEntity> {
        val arr = root.optJSONArray("extensions") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    RentalExtensionHistoryEntity(
                        id = o.optLong("id", 0L),
                        originalTransactionId = o.optLong("originalTransactionId", 0L),
                        newTransactionId = o.optLong("newTransactionId", 0L),
                        nomorInvoiceLama = o.optString("nomorInvoiceLama", ""),
                        nomorInvoiceBaru = o.optString("nomorInvoiceBaru", ""),
                        tanggalPerpanjangan = o.optString("tanggalPerpanjangan", ""),
                        tanggalAkhirLama = o.optString("tanggalAkhirLama", ""),
                        tanggalAkhirBaru = o.optString("tanggalAkhirBaru", ""),
                        tambahanDurasiHari = o.optInt("tambahanDurasiHari", 30),
                        tagihanBaru = o.optLong("tagihanBaru", 0L),
                        catatan = o.optString("catatan", ""),
                        uuid = o.optString("uuid", ""),
                        customerId = o.optLong("customerId", 0L),
                        unitId = o.optLong("unitId", 0L),
                        unitItemId = o.optLong("unitItemId", 0L),
                        jenisProgram = o.optString("jenisProgram", "Sewa Reguler"),
                        pembayaranKeHakMilik = o.optInt("pembayaranKeHakMilik", 0),
                        totalPeriodeHakMilik = o.optInt("totalPeriodeHakMilik", 0),
                        diskon = o.optLong("diskon", 0L),
                        nominalDibayar = o.optLong("nominalDibayar", 0L),
                        statusPembayaran = o.optString("statusPembayaran", "Lunas"),
                        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    fun customerDocumentsFromJson(root: JSONObject): List<CustomerDocumentEntity> {
        val arr = root.optJSONArray("customerDocuments") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CustomerDocumentEntity(
                        id = o.optLong("id", 0L),
                        customerId = o.optLong("customerId", 0L),
                        documentType = o.optString("documentType", ""),
                        fileName = o.optString("fileName", ""),
                        relativePath = o.optString("relativePath", ""),
                        mimeType = o.optString("mimeType", "image/jpeg"),
                        fileSize = o.optLong("fileSize", 0L),
                        checksum = o.optString("checksum", ""),
                        createdAt = o.optString("createdAt", ""),
                        updatedAt = o.optString("updatedAt", "")
                    )
                )
            }
        }
    }

    fun transactionDocumentsFromJson(root: JSONObject): List<TransactionDocumentEntity> {
        val arr = root.optJSONArray("transactionDocuments") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    TransactionDocumentEntity(
                        id = o.optLong("id", 0L),
                        transactionId = o.optLong("transactionId", 0L),
                        customerId = o.optLong("customerId", 0L),
                        documentType = o.optString("documentType", ""),
                        fileName = o.optString("fileName", ""),
                        relativePath = o.optString("relativePath", ""),
                        mimeType = o.optString("mimeType", "image/jpeg"),
                        fileSize = o.optLong("fileSize", 0L),
                        checksum = o.optString("checksum", ""),
                        createdAt = o.optString("createdAt", "")
                    )
                )
            }
        }
    }

    fun customerLocationsFromJson(root: JSONObject): List<CustomerLocationEntity> {
        val arr = root.optJSONArray("customerLocations") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CustomerLocationEntity(
                        id = o.optLong("id", 0L),
                        customerId = o.optLong("customerId", 0L),
                        latitude = o.optDouble("latitude", 0.0),
                        longitude = o.optDouble("longitude", 0.0),
                        address = o.optString("address", ""),
                        locationSource = o.optString("locationSource", "GPS_AUTO"),
                        accuracy = o.optDouble("accuracy", 0.0).toFloat(),
                        createdAt = o.optString("createdAt", ""),
                        updatedAt = o.optString("updatedAt", "")
                    )
                )
            }
        }
    }

    fun customerPhotosFromJson(root: JSONObject): List<CustomerPhotoEntity> {
        val arr = root.optJSONArray("customerPhotos") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CustomerPhotoEntity(
                        id = o.optLong("id", 0L),
                        customerId = o.optLong("customerId", 0L),
                        transactionId = if (o.isNull("transactionId")) null else o.optLong("transactionId"),
                        kategori = o.optString("kategori", ""),
                        namaDokumentasi = o.optString("namaDokumentasi", ""),
                        filePath = o.optString("filePath", ""),
                        relativeFolder = o.optString("relativeFolder", ""),
                        fileName = o.optString("fileName", ""),
                        tanggalWaktuIso = o.optString("tanggalWaktuIso", ""),
                        latitude = if (o.isNull("latitude")) null else o.optDouble("latitude"),
                        longitude = if (o.isNull("longitude")) null else o.optDouble("longitude"),
                        catatan = o.optString("catatan", ""),
                        sizeBytes = o.optLong("sizeBytes", 0L),
                        unitId = if (o.isNull("unitId")) null else o.optLong("unitId"),
                        uuid = o.optString("uuid", ""),
                        uploadStatus = o.optString("uploadStatus", "PENDING"),
                        driveFileId = o.optString("driveFileId", ""),
                        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    fun timelineEventsFromJson(root: JSONObject): List<CustomerTimelineEventEntity> {
        val arr = root.optJSONArray("timelineEvents") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CustomerTimelineEventEntity(
                        id = o.optLong("id", 0L),
                        customerId = o.optLong("customerId", 0L),
                        transactionId = if (o.isNull("transactionId")) null else o.optLong("transactionId"),
                        unitId = if (o.isNull("unitId")) null else o.optLong("unitId"),
                        eventType = o.optString("eventType", "SEWA_UNIT"),
                        title = o.optString("title", ""),
                        description = o.optString("description", ""),
                        amount = o.optLong("amount", 0L),
                        eventTimestamp = o.optString("eventTimestamp", ""),
                        eventDateIso = o.optString("eventDateIso", "")
                    )
                )
            }
        }
    }
}
