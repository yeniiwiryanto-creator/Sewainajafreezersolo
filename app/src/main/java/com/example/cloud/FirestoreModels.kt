package com.example.cloud

import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerReturnEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionSignatureEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitPaymentAllocationEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val lastActiveDevice: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "displayName" to displayName.ifBlank { "Pengguna Google" },
        "email" to email,
        "photoUrl" to photoUrl,
        "lastActiveDevice" to lastActiveDevice,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudCustomer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val kodePelanggan: String = "",
    val namaLengkap: String = "",
    val nik: String = "",
    val nomorHp: String = "",
    val alamatLengkap: String = "",
    val jenisKelamin: String = "",
    val tempatLahir: String = "",
    val tanggalLahir: String = "",
    val rtRw: String = "",
    val kelurahanDesa: String = "",
    val kecamatan: String = "",
    val kabupatenKota: String = "Kota Surakarta",
    val agama: String = "",
    val statusPerkawinan: String = "",
    val pekerjaan: String = "",
    val kewarganegaraan: String = "WNI",
    val berlakuHingga: String = "SEUMUR HIDUP",
    val namaLokasiDefault: String = "",
    val lokasiPenggunaanDefault: String = "",
    val latitudeDefault: Double? = null,
    val longitudeDefault: Double? = null,
    val linkGoogleMapsDefault: String = "",
    val statusPelanggan: String = "Pelanggan Aktif",
    val statusVerifikasiAdminOcr: String = "TERVERIFIKASI_ADMIN",
    val tanggalRegistrasi: String = "",
    val catatan: String = "",
    val fotoKtpDepanUrl: String = "",
    val fotoKtpBelakangUrl: String = "",
    val fotoPelangganUrl: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "localId" to localId,
        "kodePelanggan" to kodePelanggan,
        "namaLengkap" to namaLengkap,
        "nik" to nik,
        "nomorHp" to nomorHp,
        "alamatLengkap" to alamatLengkap,
        "jenisKelamin" to jenisKelamin,
        "tempatLahir" to tempatLahir,
        "tanggalLahir" to tanggalLahir,
        "rtRw" to rtRw,
        "kelurahanDesa" to kelurahanDesa,
        "kecamatan" to kecamatan,
        "kabupatenKota" to kabupatenKota,
        "agama" to agama,
        "statusPerkawinan" to statusPerkawinan,
        "pekerjaan" to pekerjaan,
        "kewarganegaraan" to kewarganegaraan,
        "berlakuHingga" to berlakuHingga,
        "namaLokasiDefault" to namaLokasiDefault,
        "lokasiPenggunaanDefault" to lokasiPenggunaanDefault,
        "latitudeDefault" to latitudeDefault,
        "longitudeDefault" to longitudeDefault,
        "linkGoogleMapsDefault" to linkGoogleMapsDefault,
        "statusPelanggan" to statusPelanggan,
        "statusVerifikasiAdminOcr" to statusVerifikasiAdminOcr,
        "tanggalRegistrasi" to tanggalRegistrasi,
        "catatan" to catatan,
        "fotoKtpDepanUrl" to fotoKtpDepanUrl,
        "fotoKtpBelakangUrl" to fotoKtpBelakangUrl,
        "fotoPelangganUrl" to fotoPelangganUrl,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudFreezerUnit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val kodeUnit: String = "",
    val namaUnit: String = "",
    val jenisUnit: String = "",
    val owner: String = "",
    val hargaPembelian: Long = 0L,
    val kapasitasLiter: Int = 0,
    val cicilanPerBulan: Long = 0L,
    val jumlahCicilanTotal: Int = 12,
    val totalPembayaranCicilan: Long = 0L,
    val statusInvestasi: String = "LIST",
    val kategoriSheet: String = "INVESTASI SEWA FREEZER",
    val merek: String = "",
    val model: String = "",
    val nomorSeri: String = "",
    val statusUnit: String = "Tersedia",
    val lokasiUnit: String = "",
    val tanggalPembelian: String = "",
    val keterangan: String = "",
    val kondisiUnit: String = "Baik & Siap Pakai",
    val tanggalPengiriman: String = "",
    val lokasiGpsPemasangan: String = "",
    val latitudePemasangan: Double? = null,
    val longitudePemasangan: Double? = null,
    val fotoUnitUrl: String = "",
    val fotoKondisiFisikUrl: String = "",
    val fotoBagianDalamUrl: String = "",
    val fotoMerekSeriUrl: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "localId" to localId,
        "kodeUnit" to kodeUnit,
        "namaUnit" to namaUnit,
        "jenisUnit" to jenisUnit,
        "owner" to owner,
        "hargaPembelian" to hargaPembelian,
        "kapasitasLiter" to kapasitasLiter.toLong(),
        "cicilanPerBulan" to cicilanPerBulan,
        "jumlahCicilanTotal" to jumlahCicilanTotal.toLong(),
        "totalPembayaranCicilan" to totalPembayaranCicilan,
        "statusInvestasi" to statusInvestasi,
        "kategoriSheet" to kategoriSheet,
        "merek" to merek,
        "model" to model,
        "nomorSeri" to nomorSeri,
        "statusUnit" to statusUnit,
        "lokasiUnit" to lokasiUnit,
        "tanggalPembelian" to tanggalPembelian,
        "keterangan" to keterangan,
        "kondisiUnit" to kondisiUnit,
        "tanggalPengiriman" to tanggalPengiriman,
        "lokasiGpsPemasangan" to lokasiGpsPemasangan,
        "latitudePemasangan" to latitudePemasangan,
        "longitudePemasangan" to longitudePemasangan,
        "fotoUnitUrl" to fotoUnitUrl,
        "fotoKondisiFisikUrl" to fotoKondisiFisikUrl,
        "fotoBagianDalamUrl" to fotoBagianDalamUrl,
        "fotoMerekSeriUrl" to fotoMerekSeriUrl,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudRentalTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val nomorInvoice: String = "",
    val tanggalPemesanan: String = "",
    val customerId: Long = 0L,
    val customerCloudId: String = "",
    val namaPelangganSnapshot: String = "",
    val unitId: Long = 0L,
    val unitCloudId: String = "",
    val namaUnitSnapshot: String = "",
    val kodeUnitSnapshot: String = "",
    val hargaSewa: Long = 0L,
    val durasiSewaBulan: Int = 1,
    val durasiHari: Int = 30,
    val tanggalMulaiSewa: String = "",
    val tanggalAkhirSewa: String = "",
    val tanggalJatuhTempo: String = "",
    val nominalPembayaran: Long = 0L,
    val ongkir: Long = 0L,
    val deposit: Long = 0L,
    val diskon: Long = 0L,
    val totalTagihan: Long = 0L,
    val statusPembayaran: String = "Belum Bayar",
    val statusPenyewaan: String = "Dalam Penyewaan",
    val metodePembayaran: String = "",
    val catatan: String = "",
    val namaLokasiUsaha: String = "",
    val alamatLokasiGps: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val akurasiGpsMeter: Double? = null,
    val linkGoogleMaps: String = "",
    val jenisProgram: String = "Sewa Reguler",
    val totalPeriodeHakMilik: Int = 0,
    val pembayaranKeHakMilik: Int = 1,
    val statusProgramHakMilik: String = "Aktif",
    val isiPerjanjianSewaSnapshot: String = "",
    val pdfInvoicePath: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "localId" to localId,
        "nomorInvoice" to nomorInvoice,
        "tanggalPemesanan" to tanggalPemesanan,
        "customerId" to customerId,
        "customerCloudId" to customerCloudId,
        "namaPelangganSnapshot" to namaPelangganSnapshot,
        "unitId" to unitId,
        "unitCloudId" to unitCloudId,
        "namaUnitSnapshot" to namaUnitSnapshot,
        "kodeUnitSnapshot" to kodeUnitSnapshot,
        "hargaSewa" to hargaSewa,
        "durasiSewaBulan" to durasiSewaBulan.toLong(),
        "durasiHari" to durasiHari.toLong(),
        "tanggalMulaiSewa" to tanggalMulaiSewa,
        "tanggalAkhirSewa" to tanggalAkhirSewa,
        "tanggalJatuhTempo" to tanggalJatuhTempo,
        "nominalPembayaran" to nominalPembayaran,
        "ongkir" to ongkir,
        "deposit" to deposit,
        "diskon" to diskon,
        "totalTagihan" to totalTagihan,
        "statusPembayaran" to statusPembayaran,
        "statusPenyewaan" to statusPenyewaan,
        "metodePembayaran" to metodePembayaran,
        "catatan" to catatan,
        "namaLokasiUsaha" to namaLokasiUsaha,
        "alamatLokasiGps" to alamatLokasiGps,
        "latitude" to latitude,
        "longitude" to longitude,
        "akurasiGpsMeter" to akurasiGpsMeter,
        "linkGoogleMaps" to linkGoogleMaps,
        "jenisProgram" to jenisProgram,
        "totalPeriodeHakMilik" to totalPeriodeHakMilik.toLong(),
        "pembayaranKeHakMilik" to pembayaranKeHakMilik.toLong(),
        "statusProgramHakMilik" to statusProgramHakMilik,
        "isiPerjanjianSewaSnapshot" to isiPerjanjianSewaSnapshot,
        "pdfInvoicePath" to pdfInvoicePath,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudTransactionUnitItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val transactionId: Long = 0L,
    val transactionCloudId: String = "",
    val customerId: Long = 0L,
    val unitId: Long = 0L,
    val unitIndexNumber: Int = 1,
    val kodeUnitSnapshot: String = "",
    val namaUnitSnapshot: String = "",
    val jenisUnitSnapshot: String = "",
    val merekSnapshot: String = "",
    val kapasitasLiterSnapshot: Int = 0,
    val jenisProgram: String = "Sewa Reguler",
    val totalPeriodeHakMilik: Int = 0,
    val pembayaranKeHakMilik: Int = 1,
    val statusProgramHakMilik: String = "Aktif",
    val hargaSewa: Long = 0L,
    val durasiHari: Int = 30,
    val tanggalMulaiSewa: String = "",
    val tanggalAkhirSewa: String = "",
    val tanggalJatuhTempo: String = "",
    val nominalPembayaran: Long = 0L,
    val statusPembayaran: String = "Belum Bayar",
    val statusPenyewaan: String = "Dalam Penyewaan",
    val catatanUnit: String = "",
    val periodeTagihan: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "localId" to localId,
        "transactionId" to transactionId,
        "transactionCloudId" to transactionCloudId,
        "customerId" to customerId,
        "unitId" to unitId,
        "unitIndexNumber" to unitIndexNumber.toLong(),
        "kodeUnitSnapshot" to kodeUnitSnapshot,
        "namaUnitSnapshot" to namaUnitSnapshot,
        "jenisUnitSnapshot" to jenisUnitSnapshot,
        "merekSnapshot" to merekSnapshot,
        "kapasitasLiterSnapshot" to kapasitasLiterSnapshot.toLong(),
        "jenisProgram" to jenisProgram,
        "totalPeriodeHakMilik" to totalPeriodeHakMilik.toLong(),
        "pembayaranKeHakMilik" to pembayaranKeHakMilik.toLong(),
        "statusProgramHakMilik" to statusProgramHakMilik,
        "hargaSewa" to hargaSewa,
        "durasiHari" to durasiHari.toLong(),
        "tanggalMulaiSewa" to tanggalMulaiSewa,
        "tanggalAkhirSewa" to tanggalAkhirSewa,
        "tanggalJatuhTempo" to tanggalJatuhTempo,
        "nominalPembayaran" to nominalPembayaran,
        "statusPembayaran" to statusPembayaran,
        "statusPenyewaan" to statusPenyewaan,
        "catatanUnit" to catatanUnit,
        "periodeTagihan" to periodeTagihan,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudPaymentProof(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val transactionId: Long = 0L,
    val transactionCloudId: String = "",
    val tanggalPembayaran: String = "",
    val nominalPembayaran: Long = 0L,
    val metodePembayaran: String = "Tunai",
    val namaPengirim: String = "",
    val catatanPembayaran: String = "",
    val fotoBuktiUrl: String = "",
    val waktuUpload: String = "",
    val unitItemId: Long? = null,
    val alokasiUnitJson: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "localId" to localId,
        "transactionId" to transactionId,
        "transactionCloudId" to transactionCloudId,
        "tanggalPembayaran" to tanggalPembayaran,
        "nominalPembayaran" to nominalPembayaran,
        "metodePembayaran" to metodePembayaran.ifBlank { "Tunai" },
        "namaPengirim" to namaPengirim,
        "catatanPembayaran" to catatanPembayaran,
        "fotoBuktiUrl" to fotoBuktiUrl,
        "waktuUpload" to waktuUpload,
        "unitItemId" to unitItemId,
        "alokasiUnitJson" to alokasiUnitJson,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudUnitPaymentAllocation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val paymentProofId: Long = 0L,
    val transactionId: Long = 0L,
    val unitItemId: Long = 0L,
    val customerId: Long = 0L,
    val unitId: Long = 0L,
    val namaUnitSnapshot: String = "",
    val kodeUnitSnapshot: String = "",
    val allocatedAmount: Long = 0L,
    val paymentDate: String = "",
    val periodeTagihan: String = "",
    val metodePembayaran: String = "",
    val catatan: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "localId" to localId,
        "paymentProofId" to paymentProofId,
        "transactionId" to transactionId,
        "unitItemId" to unitItemId,
        "customerId" to customerId,
        "unitId" to unitId,
        "namaUnitSnapshot" to namaUnitSnapshot,
        "kodeUnitSnapshot" to kodeUnitSnapshot,
        "allocatedAmount" to allocatedAmount,
        "paymentDate" to paymentDate,
        "periodeTagihan" to periodeTagihan,
        "metodePembayaran" to metodePembayaran,
        "catatan" to catatan,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudTransactionSignature(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val transactionId: Long = 0L,
    val customerId: Long = 0L,
    val signerRole: String = "PENYEWA",
    val signerName: String = "",
    val signatureMethod: String = "DRAW",
    val fileUrl: String = "",
    val relativePath: String = "",
    val hasTransparentBackground: Boolean = true,
    val signedDate: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "localId" to localId,
        "transactionId" to transactionId,
        "customerId" to customerId,
        "signerRole" to signerRole.ifBlank { "PENYEWA" },
        "signerName" to signerName,
        "signatureMethod" to signatureMethod,
        "fileUrl" to fileUrl,
        "relativePath" to relativePath,
        "hasTransparentBackground" to hasTransparentBackground,
        "signedDate" to signedDate,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudFreezerReturn(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val nomorDokumenPengembalian: String = "",
    val transactionId: Long = 0L,
    val nomorInvoiceSnapshot: String = "",
    val customerId: Long = 0L,
    val namaPelangganSnapshot: String = "",
    val unitId: Long = 0L,
    val kodeUnitSnapshot: String = "",
    val namaUnitSnapshot: String = "",
    val tanggalPengembalian: String = "",
    val jamPengembalian: String = "",
    val namaPetugasPenerima: String = "",
    val kondisiUnitSaatKembali: String = "",
    val catatanKondisiUnit: String = "",
    val statusUnitSetelahKembali: String = "Tersedia",
    val pdfBuktiPengembalianPath: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "localId" to localId,
        "nomorDokumenPengembalian" to nomorDokumenPengembalian,
        "transactionId" to transactionId,
        "nomorInvoiceSnapshot" to nomorInvoiceSnapshot,
        "customerId" to customerId,
        "namaPelangganSnapshot" to namaPelangganSnapshot,
        "unitId" to unitId,
        "kodeUnitSnapshot" to kodeUnitSnapshot,
        "namaUnitSnapshot" to namaUnitSnapshot,
        "tanggalPengembalian" to tanggalPengembalian,
        "jamPengembalian" to jamPengembalian,
        "namaPetugasPenerima" to namaPetugasPenerima,
        "kondisiUnitSaatKembali" to kondisiUnitSaatKembali,
        "catatanKondisiUnit" to catatanKondisiUnit,
        "statusUnitSetelahKembali" to statusUnitSetelahKembali,
        "pdfBuktiPengembalianPath" to pdfBuktiPengembalianPath,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudPhotoMetadata(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ownerType: String = "",
    val ownerLocalId: Long = 0L,
    val ownerCloudId: String = "",
    val photoSlot: String = "",
    val localFilePath: String = "",
    val storagePath: String = "",
    val downloadUrl: String = "",
    val thumbnailUrl: String = "",
    val mimeType: String = "image/jpeg",
    val bytesTotal: Long = 0L,
    val uploadStatus: String = "QUEUED",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "ownerType" to ownerType,
        "ownerLocalId" to ownerLocalId,
        "ownerCloudId" to ownerCloudId,
        "photoSlot" to photoSlot,
        "localFilePath" to localFilePath,
        "storagePath" to storagePath,
        "downloadUrl" to downloadUrl,
        "thumbnailUrl" to thumbnailUrl,
        "mimeType" to mimeType,
        "bytesTotal" to bytesTotal,
        "uploadStatus" to uploadStatus,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudCompanySettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val namaPerusahaan: String = "SEWAin Aja Freezer SOC",
    val alamat: String = "",
    val nomorHp: String = "",
    val email: String = "",
    val instagram: String = "",
    val facebook: String = "",
    val identitasUsaha: String = "",
    val informasiTambahan: String = "",
    val ownerName: String = "",
    val ownerSignatureUrl: String = "",
    val perjanjianSewaText: String = "",
    val paymentMethodsJson: String = "",
    val smartNotifJson: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val version: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "namaPerusahaan" to namaPerusahaan.ifBlank { "SEWAin Aja Freezer SOC" },
        "alamat" to alamat,
        "nomorHp" to nomorHp,
        "email" to email,
        "instagram" to instagram,
        "facebook" to facebook,
        "identitasUsaha" to identitasUsaha,
        "informasiTambahan" to informasiTambahan,
        "ownerName" to ownerName,
        "ownerSignatureUrl" to ownerSignatureUrl,
        "perjanjianSewaText" to perjanjianSewaText,
        "paymentMethodsJson" to paymentMethodsJson,
        "smartNotifJson" to smartNotifJson,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "version" to version,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class CloudMigrationState(
    @DocumentId val id: String = "",
    val userId: String = "",
    val currentStep: Int = 0,
    val stepLabel: String = "",
    val status: String = "NOT_STARTED",
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val localCustomersCount: Int = 0,
    val cloudCustomersCount: Int = 0,
    val localUnitsCount: Int = 0,
    val cloudUnitsCount: Int = 0,
    val localTxCount: Int = 0,
    val cloudTxCount: Int = 0,
    val localPhotosCount: Int = 0,
    val cloudPhotosCount: Int = 0,
    val lastError: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(existingCreatedAt: Timestamp? = null): Map<String, Any> = mapOf<String, Any?>(
        "userId" to userId,
        "currentStep" to currentStep.toLong(),
        "stepLabel" to stepLabel,
        "status" to status,
        "processedItems" to processedItems.toLong(),
        "totalItems" to totalItems.toLong(),
        "localCustomersCount" to localCustomersCount.toLong(),
        "cloudCustomersCount" to cloudCustomersCount.toLong(),
        "localUnitsCount" to localUnitsCount.toLong(),
        "cloudUnitsCount" to cloudUnitsCount.toLong(),
        "localTxCount" to localTxCount.toLong(),
        "cloudTxCount" to cloudTxCount.toLong(),
        "localPhotosCount" to localPhotosCount.toLong(),
        "cloudPhotosCount" to cloudPhotosCount.toLong(),
        "lastError" to lastError,
        "clientUpdatedAtMillis" to clientUpdatedAtMillis,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

// --- Deterministic Cloud IDs & Mapper Helpers ---

object CloudIdMapper {
    fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return cleaned.take(120).ifBlank { "doc_unknown" }
    }

    fun customerDocId(localId: Long): String = "cust_$localId"
    fun unitDocId(localId: Long): String = "unit_$localId"
    fun transactionDocId(localId: Long): String = "tx_$localId"
    fun transactionUnitItemDocId(localId: Long): String = "txitem_$localId"
    fun paymentProofDocId(localId: Long): String = "proof_$localId"
    fun allocationDocId(localId: Long): String = "alloc_$localId"
    fun signatureDocId(localId: Long): String = "sig_$localId"
    fun returnDocId(localId: Long): String = "ret_$localId"
    fun photoMetaDocId(ownerType: String, ownerLocalId: Long, photoSlot: String): String =
        sanitizeDocId("photo_${ownerType.lowercase()}_${ownerLocalId}_${photoSlot.lowercase()}")
}

fun CustomerEntity.toCloudCustomer(
    userId: String,
    timestampMillis: Long = System.currentTimeMillis(),
    version: Long = 1L,
    fotoKtpDepanUrl: String = "",
    fotoKtpBelakangUrl: String = "",
    fotoPelangganUrl: String = ""
): CloudCustomer = CloudCustomer(
    id = CloudIdMapper.customerDocId(id),
    userId = userId,
    localId = id,
    kodePelanggan = kodePelanggan,
    namaLengkap = namaLengkap.ifBlank { "Pelanggan $id" },
    nik = nik,
    nomorHp = nomorHp,
    alamatLengkap = alamatLengkap,
    jenisKelamin = jenisKelamin,
    tempatLahir = tempatLahir,
    tanggalLahir = tanggalLahir,
    rtRw = rtRw,
    kelurahanDesa = kelurahanDesa,
    kecamatan = kecamatan,
    kabupatenKota = kabupatenKota,
    agama = agama,
    statusPerkawinan = statusPerkawinan,
    pekerjaan = pekerjaan,
    kewarganegaraan = kewarganegaraan,
    berlakuHingga = berlakuHingga,
    namaLokasiDefault = namaLokasiDefault,
    lokasiPenggunaanDefault = lokasiPenggunaanDefault,
    latitudeDefault = latitudeDefault,
    longitudeDefault = longitudeDefault,
    linkGoogleMapsDefault = linkGoogleMapsDefault,
    statusPelanggan = statusPelanggan,
    statusVerifikasiAdminOcr = statusVerifikasiAdminOcr,
    tanggalRegistrasi = tanggalRegistrasi,
    catatan = catatan,
    fotoKtpDepanUrl = fotoKtpDepanUrl,
    fotoKtpBelakangUrl = fotoKtpBelakangUrl,
    fotoPelangganUrl = fotoPelangganUrl,
    clientUpdatedAtMillis = timestampMillis,
    version = version
)

fun CloudCustomer.effectiveUpdatedAtMillis(): Long =
    maxOf(updatedAt?.toDate()?.time ?: 0L, clientUpdatedAtMillis)

fun CloudFreezerUnit.effectiveUpdatedAtMillis(): Long =
    maxOf(updatedAt?.toDate()?.time ?: 0L, clientUpdatedAtMillis)

fun CloudRentalTransaction.effectiveUpdatedAtMillis(): Long =
    maxOf(updatedAt?.toDate()?.time ?: 0L, clientUpdatedAtMillis)

fun CloudPaymentProof.effectiveUpdatedAtMillis(): Long =
    maxOf(updatedAt?.toDate()?.time ?: 0L, clientUpdatedAtMillis)

private fun mergeLocalOrRemoteMediaUri(existingLocalUri: String?, remoteUrl: String): String {
    val local = existingLocalUri.orEmpty().trim()
    val remote = remoteUrl.trim()
    if (local.isBlank()) return remote
    // Preserve valid device-local file/content URI; allow remote https:// URL to refresh older remote references
    val localIsRemoteRef = local.startsWith("http://", ignoreCase = true) ||
        local.startsWith("https://", ignoreCase = true) ||
        local.startsWith("gdrive://", ignoreCase = true)
    return if (localIsRemoteRef && remote.startsWith("https://", ignoreCase = true)) {
        remote
    } else {
        local
    }
}

fun CloudCustomer.toLocalEntity(existing: CustomerEntity? = null): CustomerEntity {
    val resolvedUpdatedAt = effectiveUpdatedAtMillis().takeIf { it > 0L }
        ?: existing?.updatedAtMillis
        ?: System.currentTimeMillis()
    val resolvedCreatedAt = existing?.createdAtMillis?.takeIf { it > 0L }
        ?: createdAt?.toDate()?.time
        ?: resolvedUpdatedAt
    return CustomerEntity(
        id = if (localId > 0L) localId else existing?.id ?: 0L,
        kodePelanggan = kodePelanggan.ifBlank { existing?.kodePelanggan.orEmpty() },
        namaLengkap = namaLengkap,
        nik = nik,
        nomorHp = nomorHp,
        alamatLengkap = alamatLengkap,
        jenisKelamin = jenisKelamin.ifBlank { existing?.jenisKelamin.orEmpty() },
        fotoKtpDepanUri = mergeLocalOrRemoteMediaUri(existing?.fotoKtpDepanUri, fotoKtpDepanUrl),
        fotoKtpBelakangUri = mergeLocalOrRemoteMediaUri(existing?.fotoKtpBelakangUri, fotoKtpBelakangUrl),
        catatan = catatan,
        tanggalRegistrasi = tanggalRegistrasi.ifBlank { existing?.tanggalRegistrasi.orEmpty() },
        tempatLahir = tempatLahir,
        tanggalLahir = tanggalLahir,
        rtRw = rtRw,
        rt = existing?.rt.orEmpty(),
        rw = existing?.rw.orEmpty(),
        kelurahanDesa = kelurahanDesa,
        kecamatan = kecamatan,
        kabupatenKota = kabupatenKota,
        agama = agama,
        statusPerkawinan = statusPerkawinan,
        pekerjaan = pekerjaan,
        kewarganegaraan = kewarganegaraan,
        berlakuHingga = berlakuHingga,
        persetujuanDataKtpLokasi = existing?.persetujuanDataKtpLokasi ?: true,
        namaLokasiDefault = namaLokasiDefault,
        lokasiPenggunaanDefault = lokasiPenggunaanDefault,
        rtRwLokasiDefault = existing?.rtRwLokasiDefault.orEmpty(),
        kelurahanLokasiDefault = existing?.kelurahanLokasiDefault.orEmpty(),
        kecamatanLokasiDefault = existing?.kecamatanLokasiDefault.orEmpty(),
        kabupatenKotaLokasiDefault = existing?.kabupatenKotaLokasiDefault ?: kabupatenKota,
        latitudeDefault = latitudeDefault,
        longitudeDefault = longitudeDefault,
        linkGoogleMapsDefault = linkGoogleMapsDefault,
        fotoKtpPreprocessedUri = existing?.fotoKtpPreprocessedUri.orEmpty(),
        rawOcrText = existing?.rawOcrText.orEmpty(),
        parsedOcrJson = existing?.parsedOcrJson.orEmpty(),
        ocrFieldConfidencesJson = existing?.ocrFieldConfidencesJson.orEmpty(),
        statusVerifikasiAdminOcr = statusVerifikasiAdminOcr,
        waktuPemindaianOcr = existing?.waktuPemindaianOcr.orEmpty(),
        fotoPelangganUri = mergeLocalOrRemoteMediaUri(existing?.fotoPelangganUri, fotoPelangganUrl),
        statusPelanggan = statusPelanggan,
        uuid = existing?.uuid?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
        version = maxOf(existing?.version ?: 1L, version.coerceAtLeast(1L)),
        createdAtMillis = resolvedCreatedAt,
        updatedAtMillis = resolvedUpdatedAt,
        syncStatus = "SYNCED",
        changedFieldsJson = existing?.changedFieldsJson.orEmpty(),
        isDeleted = false,
        driveId = existing?.driveId.orEmpty()
    )
}

fun FreezerUnitEntity.toCloudUnit(
    userId: String,
    timestampMillis: Long = System.currentTimeMillis(),
    version: Long = 1L,
    fotoUnitUrl: String = "",
    fotoKondisiFisikUrl: String = "",
    fotoBagianDalamUrl: String = "",
    fotoMerekSeriUrl: String = ""
): CloudFreezerUnit = CloudFreezerUnit(
    id = CloudIdMapper.unitDocId(id),
    userId = userId,
    localId = id,
    kodeUnit = kodeUnit,
    namaUnit = namaUnit,
    jenisUnit = jenisUnit,
    owner = owner,
    hargaPembelian = hargaPembelian,
    kapasitasLiter = kapasitasLiter,
    cicilanPerBulan = cicilanPerBulan,
    jumlahCicilanTotal = jumlahCicilanTotal,
    totalPembayaranCicilan = totalPembayaranCicilan,
    statusInvestasi = statusInvestasi,
    kategoriSheet = kategoriSheet,
    merek = merek,
    model = model,
    nomorSeri = nomorSeri,
    statusUnit = statusUnit,
    lokasiUnit = lokasiUnit,
    tanggalPembelian = tanggalPembelian,
    keterangan = keterangan,
    kondisiUnit = kondisiUnit,
    tanggalPengiriman = tanggalPengiriman,
    lokasiGpsPemasangan = lokasiGpsPemasangan,
    latitudePemasangan = latitudePemasangan,
    longitudePemasangan = longitudePemasangan,
    fotoUnitUrl = fotoUnitUrl,
    fotoKondisiFisikUrl = fotoKondisiFisikUrl,
    fotoBagianDalamUrl = fotoBagianDalamUrl,
    fotoMerekSeriUrl = fotoMerekSeriUrl,
    clientUpdatedAtMillis = timestampMillis,
    version = version
)

fun CloudFreezerUnit.toLocalEntity(existing: FreezerUnitEntity? = null): FreezerUnitEntity {
    val resolvedUpdatedAt = effectiveUpdatedAtMillis().takeIf { it > 0L }
        ?: existing?.updatedAtMillis
        ?: System.currentTimeMillis()
    val resolvedCreatedAt = existing?.createdAtMillis?.takeIf { it > 0L }
        ?: createdAt?.toDate()?.time
        ?: resolvedUpdatedAt
    return FreezerUnitEntity(
        id = if (localId > 0L) localId else existing?.id ?: 0L,
        kodeUnit = kodeUnit,
        namaUnit = namaUnit,
        jenisUnit = jenisUnit,
        owner = owner,
        hargaPembelian = hargaPembelian,
        kapasitasLiter = kapasitasLiter,
        cicilanPerBulan = cicilanPerBulan,
        jumlahCicilanTotal = jumlahCicilanTotal,
        totalPembayaranCicilan = totalPembayaranCicilan,
        statusInvestasi = statusInvestasi,
        kategoriSheet = kategoriSheet,
        merek = merek,
        model = model,
        nomorSeri = nomorSeri,
        fotoUnitUri = mergeLocalOrRemoteMediaUri(existing?.fotoUnitUri, fotoUnitUrl),
        fotoKondisiFisikUri = mergeLocalOrRemoteMediaUri(existing?.fotoKondisiFisikUri, fotoKondisiFisikUrl),
        fotoBagianDalamUri = mergeLocalOrRemoteMediaUri(existing?.fotoBagianDalamUri, fotoBagianDalamUrl),
        fotoMerekSeriUri = mergeLocalOrRemoteMediaUri(existing?.fotoMerekSeriUri, fotoMerekSeriUrl),
        statusUnit = statusUnit,
        lokasiUnit = lokasiUnit,
        tanggalPembelian = tanggalPembelian,
        keterangan = keterangan,
        kondisiUnit = kondisiUnit,
        tanggalPengiriman = tanggalPengiriman,
        lokasiGpsPemasangan = lokasiGpsPemasangan,
        latitudePemasangan = latitudePemasangan,
        longitudePemasangan = longitudePemasangan,
        maintenanceHistory = existing?.maintenanceHistory.orEmpty(),
        uuid = existing?.uuid?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
        version = maxOf(existing?.version ?: 1L, version.coerceAtLeast(1L)),
        createdAtMillis = resolvedCreatedAt,
        updatedAtMillis = resolvedUpdatedAt,
        syncStatus = "SYNCED",
        changedFieldsJson = existing?.changedFieldsJson.orEmpty(),
        isDeleted = false,
        driveId = existing?.driveId.orEmpty()
    )
}

fun RentalTransactionEntity.toCloudTransaction(
    userId: String,
    timestampMillis: Long = System.currentTimeMillis(),
    version: Long = 1L
): CloudRentalTransaction = CloudRentalTransaction(
    id = CloudIdMapper.transactionDocId(id),
    userId = userId,
    localId = id,
    nomorInvoice = nomorInvoice,
    tanggalPemesanan = tanggalPemesanan,
    customerId = customerId,
    customerCloudId = CloudIdMapper.customerDocId(customerId),
    namaPelangganSnapshot = namaPelangganSnapshot,
    unitId = unitId,
    unitCloudId = CloudIdMapper.unitDocId(unitId),
    namaUnitSnapshot = namaUnitSnapshot,
    kodeUnitSnapshot = kodeUnitSnapshot,
    hargaSewa = hargaSewa,
    durasiSewaBulan = durasiSewaBulan,
    durasiHari = durasiHari,
    tanggalMulaiSewa = tanggalMulaiSewa,
    tanggalAkhirSewa = tanggalAkhirSewa,
    tanggalJatuhTempo = tanggalJatuhTempo,
    nominalPembayaran = nominalPembayaran,
    ongkir = ongkir,
    deposit = deposit,
    diskon = diskon,
    totalTagihan = totalTagihan,
    statusPembayaran = statusPembayaran,
    statusPenyewaan = statusPenyewaan,
    metodePembayaran = metodePembayaran,
    catatan = catatan,
    namaLokasiUsaha = namaLokasiUsaha,
    alamatLokasiGps = alamatLokasiGps,
    latitude = latitude,
    longitude = longitude,
    akurasiGpsMeter = akurasiGpsMeter?.toDouble(),
    linkGoogleMaps = linkGoogleMaps,
    jenisProgram = jenisProgram,
    totalPeriodeHakMilik = totalPeriodeHakMilik,
    pembayaranKeHakMilik = pembayaranKeHakMilik,
    statusProgramHakMilik = statusProgramHakMilik,
    isiPerjanjianSewaSnapshot = isiPerjanjianSewaSnapshot,
    pdfInvoicePath = pdfInvoicePath,
    clientUpdatedAtMillis = timestampMillis,
    version = version
)

fun TransactionUnitItemEntity.toCloudUnitItem(
    userId: String,
    timestampMillis: Long = System.currentTimeMillis(),
    version: Long = 1L
): CloudTransactionUnitItem = CloudTransactionUnitItem(
    id = CloudIdMapper.transactionUnitItemDocId(id),
    userId = userId,
    localId = id,
    transactionId = transactionId,
    transactionCloudId = CloudIdMapper.transactionDocId(transactionId),
    customerId = customerId,
    unitId = unitId,
    unitIndexNumber = unitIndexNumber,
    kodeUnitSnapshot = kodeUnitSnapshot,
    namaUnitSnapshot = namaUnitSnapshot,
    jenisUnitSnapshot = jenisUnitSnapshot,
    merekSnapshot = merekSnapshot,
    kapasitasLiterSnapshot = kapasitasLiterSnapshot,
    jenisProgram = jenisProgram,
    totalPeriodeHakMilik = totalPeriodeHakMilik,
    pembayaranKeHakMilik = pembayaranKeHakMilik,
    statusProgramHakMilik = statusProgramHakMilik,
    hargaSewa = hargaSewa,
    durasiHari = durasiHari,
    tanggalMulaiSewa = tanggalMulaiSewa,
    tanggalAkhirSewa = tanggalAkhirSewa,
    tanggalJatuhTempo = tanggalJatuhTempo,
    nominalPembayaran = nominalPembayaran,
    statusPembayaran = statusPembayaran,
    statusPenyewaan = statusPenyewaan,
    catatanUnit = catatanUnit,
    periodeTagihan = periodeTagihan,
    clientUpdatedAtMillis = timestampMillis,
    version = version
)

fun PaymentProofEntity.toCloudPaymentProof(
    userId: String,
    timestampMillis: Long = System.currentTimeMillis(),
    version: Long = 1L,
    fotoBuktiUrl: String = ""
): CloudPaymentProof = CloudPaymentProof(
    id = CloudIdMapper.paymentProofDocId(id),
    userId = userId,
    localId = id,
    transactionId = transactionId,
    transactionCloudId = CloudIdMapper.transactionDocId(transactionId),
    tanggalPembayaran = tanggalPembayaran,
    nominalPembayaran = nominalPembayaran,
    metodePembayaran = metodePembayaran.ifBlank { "Tunai" },
    namaPengirim = namaPengirim,
    catatanPembayaran = catatanPembayaran,
    fotoBuktiUrl = fotoBuktiUrl,
    waktuUpload = waktuUpload,
    unitItemId = unitItemId,
    alokasiUnitJson = alokasiUnitJson,
    clientUpdatedAtMillis = timestampMillis,
    version = version
)

fun UnitPaymentAllocationEntity.toCloudAllocation(
    userId: String,
    timestampMillis: Long = System.currentTimeMillis(),
    version: Long = 1L
): CloudUnitPaymentAllocation = CloudUnitPaymentAllocation(
    id = CloudIdMapper.allocationDocId(id),
    userId = userId,
    localId = id,
    paymentProofId = paymentProofId,
    transactionId = transactionId,
    unitItemId = unitItemId,
    customerId = customerId,
    unitId = unitId,
    namaUnitSnapshot = namaUnitSnapshot,
    kodeUnitSnapshot = kodeUnitSnapshot,
    allocatedAmount = allocatedAmount,
    paymentDate = paymentDate,
    periodeTagihan = periodeTagihan,
    metodePembayaran = metodePembayaran,
    catatan = catatan,
    clientUpdatedAtMillis = timestampMillis,
    version = version
)

fun TransactionSignatureEntity.toCloudSignature(
    userId: String,
    timestampMillis: Long = System.currentTimeMillis(),
    version: Long = 1L,
    fileUrl: String = ""
): CloudTransactionSignature = CloudTransactionSignature(
    id = CloudIdMapper.signatureDocId(id),
    userId = userId,
    localId = id,
    transactionId = transactionId,
    customerId = customerId,
    signerRole = signerRole.ifBlank { "PENYEWA" },
    signerName = signerName,
    signatureMethod = signatureMethod,
    fileUrl = fileUrl,
    relativePath = relativePath,
    hasTransparentBackground = hasTransparentBackground,
    signedDate = signedDate,
    clientUpdatedAtMillis = timestampMillis,
    version = version
)

fun FreezerReturnEntity.toCloudReturn(
    userId: String,
    timestampMillis: Long = System.currentTimeMillis(),
    version: Long = 1L
): CloudFreezerReturn = CloudFreezerReturn(
    id = CloudIdMapper.returnDocId(id),
    userId = userId,
    localId = id,
    nomorDokumenPengembalian = nomorDokumenPengembalian,
    transactionId = transactionId,
    nomorInvoiceSnapshot = nomorInvoiceSnapshot,
    customerId = customerId,
    namaPelangganSnapshot = namaPelangganSnapshot,
    unitId = unitId,
    kodeUnitSnapshot = kodeUnitSnapshot,
    namaUnitSnapshot = namaUnitSnapshot,
    tanggalPengembalian = tanggalPengembalian,
    jamPengembalian = jamPengembalian,
    namaPetugasPenerima = namaPetugasPenerima,
    kondisiUnitSaatKembali = kondisiUnitSaatKembali,
    catatanKondisiUnit = catatanKondisiUnit,
    statusUnitSetelahKembali = statusUnitSetelahKembali,
    pdfBuktiPengembalianPath = pdfBuktiPengembalianPath,
    clientUpdatedAtMillis = timestampMillis,
    version = version
)

fun CompanyProfile.toCloudSettings(
    userId: String,
    perjanjianSewaText: String,
    paymentMethodsJson: String,
    smartNotifJson: String,
    ownerSignatureUrl: String = "",
    timestampMillis: Long = System.currentTimeMillis(),
    version: Long = 1L
): CloudCompanySettings = CloudCompanySettings(
    id = "primary",
    userId = userId,
    namaPerusahaan = namaPerusahaan.ifBlank { "SEWAin Aja Freezer SOC" },
    alamat = alamat,
    nomorHp = nomorHp,
    email = email,
    instagram = instagram,
    facebook = facebook,
    identitasUsaha = identitasUsaha,
    informasiTambahan = informasiTambahan,
    ownerName = ownerName,
    ownerSignatureUrl = ownerSignatureUrl,
    perjanjianSewaText = perjanjianSewaText,
    paymentMethodsJson = paymentMethodsJson,
    smartNotifJson = smartNotifJson,
    clientUpdatedAtMillis = timestampMillis,
    version = version
)

fun DocumentSnapshot.toCloudCustomerEstimated(): CloudCustomer? {
    if (!exists()) return null
    val data = getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return CloudCustomer(
        id = id,
        userId = (data["userId"] as? String).orEmpty(),
        localId = (data["localId"] as? Number)?.toLong() ?: 0L,
        kodePelanggan = (data["kodePelanggan"] as? String).orEmpty(),
        namaLengkap = (data["namaLengkap"] as? String).orEmpty(),
        nik = (data["nik"] as? String).orEmpty(),
        nomorHp = (data["nomorHp"] as? String).orEmpty(),
        alamatLengkap = (data["alamatLengkap"] as? String).orEmpty(),
        jenisKelamin = (data["jenisKelamin"] as? String).orEmpty(),
        tempatLahir = (data["tempatLahir"] as? String).orEmpty(),
        tanggalLahir = (data["tanggalLahir"] as? String).orEmpty(),
        rtRw = (data["rtRw"] as? String).orEmpty(),
        kelurahanDesa = (data["kelurahanDesa"] as? String).orEmpty(),
        kecamatan = (data["kecamatan"] as? String).orEmpty(),
        kabupatenKota = (data["kabupatenKota"] as? String).orEmpty(),
        agama = (data["agama"] as? String).orEmpty(),
        statusPerkawinan = (data["statusPerkawinan"] as? String).orEmpty(),
        pekerjaan = (data["pekerjaan"] as? String).orEmpty(),
        kewarganegaraan = (data["kewarganegaraan"] as? String).orEmpty(),
        berlakuHingga = (data["berlakuHingga"] as? String).orEmpty(),
        namaLokasiDefault = (data["namaLokasiDefault"] as? String).orEmpty(),
        lokasiPenggunaanDefault = (data["lokasiPenggunaanDefault"] as? String).orEmpty(),
        latitudeDefault = (data["latitudeDefault"] as? Number)?.toDouble(),
        longitudeDefault = (data["longitudeDefault"] as? Number)?.toDouble(),
        linkGoogleMapsDefault = (data["linkGoogleMapsDefault"] as? String).orEmpty(),
        statusPelanggan = (data["statusPelanggan"] as? String) ?: "Pelanggan Aktif",
        statusVerifikasiAdminOcr = (data["statusVerifikasiAdminOcr"] as? String) ?: "TERVERIFIKASI_ADMIN",
        tanggalRegistrasi = (data["tanggalRegistrasi"] as? String).orEmpty(),
        catatan = (data["catatan"] as? String).orEmpty(),
        fotoKtpDepanUrl = (data["fotoKtpDepanUrl"] as? String).orEmpty(),
        fotoKtpBelakangUrl = (data["fotoKtpBelakangUrl"] as? String).orEmpty(),
        fotoPelangganUrl = (data["fotoPelangganUrl"] as? String).orEmpty(),
        clientUpdatedAtMillis = (data["clientUpdatedAtMillis"] as? Number)?.toLong() ?: 0L,
        version = (data["version"] as? Number)?.toLong() ?: 1L,
        createdAt = data["createdAt"] as? Timestamp,
        updatedAt = data["updatedAt"] as? Timestamp
    )
}

fun DocumentSnapshot.toCloudUnitEstimated(): CloudFreezerUnit? {
    if (!exists()) return null
    val data = getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return CloudFreezerUnit(
        id = id,
        userId = (data["userId"] as? String).orEmpty(),
        localId = (data["localId"] as? Number)?.toLong() ?: 0L,
        kodeUnit = (data["kodeUnit"] as? String).orEmpty(),
        namaUnit = (data["namaUnit"] as? String).orEmpty(),
        jenisUnit = (data["jenisUnit"] as? String).orEmpty(),
        owner = (data["owner"] as? String).orEmpty(),
        hargaPembelian = (data["hargaPembelian"] as? Number)?.toLong() ?: 0L,
        kapasitasLiter = (data["kapasitasLiter"] as? Number)?.toInt() ?: 0,
        cicilanPerBulan = (data["cicilanPerBulan"] as? Number)?.toLong() ?: 0L,
        jumlahCicilanTotal = (data["jumlahCicilanTotal"] as? Number)?.toInt() ?: 12,
        totalPembayaranCicilan = (data["totalPembayaranCicilan"] as? Number)?.toLong() ?: 0L,
        statusInvestasi = (data["statusInvestasi"] as? String) ?: "LIST",
        kategoriSheet = (data["kategoriSheet"] as? String) ?: "INVESTASI SEWA FREEZER",
        merek = (data["merek"] as? String).orEmpty(),
        model = (data["model"] as? String).orEmpty(),
        nomorSeri = (data["nomorSeri"] as? String).orEmpty(),
        statusUnit = (data["statusUnit"] as? String) ?: "Tersedia",
        lokasiUnit = (data["lokasiUnit"] as? String).orEmpty(),
        tanggalPembelian = (data["tanggalPembelian"] as? String).orEmpty(),
        keterangan = (data["keterangan"] as? String).orEmpty(),
        kondisiUnit = (data["kondisiUnit"] as? String) ?: "Baik & Siap Pakai",
        tanggalPengiriman = (data["tanggalPengiriman"] as? String).orEmpty(),
        lokasiGpsPemasangan = (data["lokasiGpsPemasangan"] as? String).orEmpty(),
        latitudePemasangan = (data["latitudePemasangan"] as? Number)?.toDouble(),
        longitudePemasangan = (data["longitudePemasangan"] as? Number)?.toDouble(),
        fotoUnitUrl = (data["fotoUnitUrl"] as? String).orEmpty(),
        fotoKondisiFisikUrl = (data["fotoKondisiFisikUrl"] as? String).orEmpty(),
        fotoBagianDalamUrl = (data["fotoBagianDalamUrl"] as? String).orEmpty(),
        fotoMerekSeriUrl = (data["fotoMerekSeriUrl"] as? String).orEmpty(),
        clientUpdatedAtMillis = (data["clientUpdatedAtMillis"] as? Number)?.toLong() ?: 0L,
        version = (data["version"] as? Number)?.toLong() ?: 1L,
        createdAt = data["createdAt"] as? Timestamp,
        updatedAt = data["updatedAt"] as? Timestamp
    )
}

fun DocumentSnapshot.toCloudTransactionEstimated(): CloudRentalTransaction? {
    if (!exists()) return null
    val data = getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return CloudRentalTransaction(
        id = id,
        userId = (data["userId"] as? String).orEmpty(),
        localId = (data["localId"] as? Number)?.toLong() ?: 0L,
        nomorInvoice = (data["nomorInvoice"] as? String).orEmpty(),
        tanggalPemesanan = (data["tanggalPemesanan"] as? String).orEmpty(),
        customerId = (data["customerId"] as? Number)?.toLong() ?: 0L,
        customerCloudId = (data["customerCloudId"] as? String).orEmpty(),
        namaPelangganSnapshot = (data["namaPelangganSnapshot"] as? String).orEmpty(),
        unitId = (data["unitId"] as? Number)?.toLong() ?: 0L,
        unitCloudId = (data["unitCloudId"] as? String).orEmpty(),
        namaUnitSnapshot = (data["namaUnitSnapshot"] as? String).orEmpty(),
        kodeUnitSnapshot = (data["kodeUnitSnapshot"] as? String).orEmpty(),
        hargaSewa = (data["hargaSewa"] as? Number)?.toLong() ?: 0L,
        durasiSewaBulan = (data["durasiSewaBulan"] as? Number)?.toInt() ?: 1,
        durasiHari = (data["durasiHari"] as? Number)?.toInt() ?: 30,
        tanggalMulaiSewa = (data["tanggalMulaiSewa"] as? String).orEmpty(),
        tanggalAkhirSewa = (data["tanggalAkhirSewa"] as? String).orEmpty(),
        tanggalJatuhTempo = (data["tanggalJatuhTempo"] as? String).orEmpty(),
        nominalPembayaran = (data["nominalPembayaran"] as? Number)?.toLong() ?: 0L,
        ongkir = (data["ongkir"] as? Number)?.toLong() ?: 0L,
        deposit = (data["deposit"] as? Number)?.toLong() ?: 0L,
        diskon = (data["diskon"] as? Number)?.toLong() ?: 0L,
        totalTagihan = (data["totalTagihan"] as? Number)?.toLong() ?: 0L,
        statusPembayaran = (data["statusPembayaran"] as? String) ?: "Belum Bayar",
        statusPenyewaan = (data["statusPenyewaan"] as? String) ?: "Dalam Penyewaan",
        metodePembayaran = (data["metodePembayaran"] as? String).orEmpty(),
        catatan = (data["catatan"] as? String).orEmpty(),
        namaLokasiUsaha = (data["namaLokasiUsaha"] as? String).orEmpty(),
        alamatLokasiGps = (data["alamatLokasiGps"] as? String).orEmpty(),
        latitude = (data["latitude"] as? Number)?.toDouble(),
        longitude = (data["longitude"] as? Number)?.toDouble(),
        akurasiGpsMeter = (data["akurasiGpsMeter"] as? Number)?.toDouble(),
        linkGoogleMaps = (data["linkGoogleMaps"] as? String).orEmpty(),
        jenisProgram = (data["jenisProgram"] as? String) ?: "Sewa Reguler",
        totalPeriodeHakMilik = (data["totalPeriodeHakMilik"] as? Number)?.toInt() ?: 0,
        pembayaranKeHakMilik = (data["pembayaranKeHakMilik"] as? Number)?.toInt() ?: 1,
        statusProgramHakMilik = (data["statusProgramHakMilik"] as? String) ?: "Aktif",
        isiPerjanjianSewaSnapshot = (data["isiPerjanjianSewaSnapshot"] as? String).orEmpty(),
        pdfInvoicePath = (data["pdfInvoicePath"] as? String).orEmpty(),
        clientUpdatedAtMillis = (data["clientUpdatedAtMillis"] as? Number)?.toLong() ?: 0L,
        version = (data["version"] as? Number)?.toLong() ?: 1L,
        createdAt = data["createdAt"] as? Timestamp,
        updatedAt = data["updatedAt"] as? Timestamp
    )
}

fun DocumentSnapshot.toCloudPaymentProofEstimated(): CloudPaymentProof? {
    if (!exists()) return null
    val data = getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return CloudPaymentProof(
        id = id,
        userId = (data["userId"] as? String).orEmpty(),
        localId = (data["localId"] as? Number)?.toLong() ?: 0L,
        transactionId = (data["transactionId"] as? Number)?.toLong() ?: 0L,
        transactionCloudId = (data["transactionCloudId"] as? String).orEmpty(),
        tanggalPembayaran = (data["tanggalPembayaran"] as? String).orEmpty(),
        nominalPembayaran = (data["nominalPembayaran"] as? Number)?.toLong() ?: 0L,
        metodePembayaran = (data["metodePembayaran"] as? String) ?: "Tunai",
        namaPengirim = (data["namaPengirim"] as? String).orEmpty(),
        catatanPembayaran = (data["catatanPembayaran"] as? String).orEmpty(),
        fotoBuktiUrl = (data["fotoBuktiUrl"] as? String).orEmpty(),
        waktuUpload = (data["waktuUpload"] as? String).orEmpty(),
        unitItemId = (data["unitItemId"] as? Number)?.toLong(),
        alokasiUnitJson = (data["alokasiUnitJson"] as? String).orEmpty(),
        clientUpdatedAtMillis = (data["clientUpdatedAtMillis"] as? Number)?.toLong() ?: 0L,
        version = (data["version"] as? Number)?.toLong() ?: 1L,
        createdAt = data["createdAt"] as? Timestamp,
        updatedAt = data["updatedAt"] as? Timestamp
    )
}

fun CloudRentalTransaction.toLocalEntity(
    existing: RentalTransactionEntity? = null,
    resolvedCustomerId: Long = existing?.customerId ?: customerId,
    resolvedUnitId: Long = existing?.unitId ?: unitId
): RentalTransactionEntity {
    val resolvedUpdatedAt = effectiveUpdatedAtMillis().takeIf { it > 0L }
        ?: existing?.updatedAtMillis
        ?: System.currentTimeMillis()
    val resolvedCreatedAt = existing?.createdAtMillis?.takeIf { it > 0L }
        ?: createdAt?.toDate()?.time
        ?: resolvedUpdatedAt
    return (existing ?: RentalTransactionEntity(
        id = if (localId > 0L) localId else 0L,
        nomorInvoice = nomorInvoice,
        tanggalPemesanan = tanggalPemesanan,
        customerId = resolvedCustomerId,
        namaPelangganSnapshot = namaPelangganSnapshot,
        unitId = resolvedUnitId,
        namaUnitSnapshot = namaUnitSnapshot,
        kodeUnitSnapshot = kodeUnitSnapshot,
        hargaSewa = hargaSewa,
        durasiSewaBulan = durasiSewaBulan,
        durasiHari = durasiHari,
        tanggalMulaiSewa = tanggalMulaiSewa,
        tanggalAkhirSewa = tanggalAkhirSewa,
        tanggalJatuhTempo = tanggalJatuhTempo,
        nominalPembayaran = nominalPembayaran,
        ongkir = ongkir,
        deposit = deposit,
        diskon = diskon,
        totalTagihan = totalTagihan,
        statusPembayaran = statusPembayaran,
        statusPenyewaan = statusPenyewaan,
        metodePembayaran = metodePembayaran
    )).copy(
        id = if (localId > 0L) localId else existing?.id ?: 0L,
        nomorInvoice = nomorInvoice.ifBlank { existing?.nomorInvoice.orEmpty() },
        tanggalPemesanan = tanggalPemesanan.ifBlank { existing?.tanggalPemesanan.orEmpty() },
        customerId = resolvedCustomerId,
        namaPelangganSnapshot = namaPelangganSnapshot.ifBlank { existing?.namaPelangganSnapshot.orEmpty() },
        unitId = resolvedUnitId,
        namaUnitSnapshot = namaUnitSnapshot.ifBlank { existing?.namaUnitSnapshot.orEmpty() },
        kodeUnitSnapshot = kodeUnitSnapshot.ifBlank { existing?.kodeUnitSnapshot.orEmpty() },
        hargaSewa = hargaSewa,
        durasiSewaBulan = durasiSewaBulan,
        durasiHari = durasiHari,
        tanggalMulaiSewa = tanggalMulaiSewa,
        tanggalAkhirSewa = tanggalAkhirSewa,
        tanggalJatuhTempo = tanggalJatuhTempo,
        nominalPembayaran = nominalPembayaran,
        ongkir = ongkir,
        deposit = deposit,
        diskon = diskon,
        totalTagihan = totalTagihan,
        statusPembayaran = statusPembayaran,
        statusPenyewaan = statusPenyewaan,
        metodePembayaran = metodePembayaran,
        catatan = catatan,
        namaLokasiUsaha = namaLokasiUsaha,
        alamatLokasiGps = alamatLokasiGps,
        latitude = latitude,
        longitude = longitude,
        akurasiGpsMeter = akurasiGpsMeter?.toFloat() ?: existing?.akurasiGpsMeter,
        linkGoogleMaps = linkGoogleMaps,
        jenisProgram = jenisProgram,
        totalPeriodeHakMilik = totalPeriodeHakMilik,
        pembayaranKeHakMilik = pembayaranKeHakMilik,
        statusProgramHakMilik = statusProgramHakMilik,
        isiPerjanjianSewaSnapshot = isiPerjanjianSewaSnapshot.ifBlank { existing?.isiPerjanjianSewaSnapshot.orEmpty() },
        pdfInvoicePath = existing?.pdfInvoicePath.orEmpty().ifBlank { pdfInvoicePath },
        uuid = existing?.uuid?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
        version = maxOf(existing?.version ?: 1L, version.coerceAtLeast(1L)),
        createdAtMillis = resolvedCreatedAt,
        updatedAtMillis = resolvedUpdatedAt,
        syncStatus = "SYNCED",
        isDeleted = false,
        driveId = existing?.driveId.orEmpty()
    )
}

fun CloudPaymentProof.toLocalEntity(
    existing: PaymentProofEntity? = null,
    resolvedTransactionId: Long = existing?.transactionId ?: transactionId
): PaymentProofEntity {
    val resolvedUpdatedAt = effectiveUpdatedAtMillis().takeIf { it > 0L }
        ?: existing?.updatedAtMillis
        ?: System.currentTimeMillis()
    val resolvedCreatedAt = existing?.createdAtMillis?.takeIf { it > 0L }
        ?: createdAt?.toDate()?.time
        ?: resolvedUpdatedAt
    return PaymentProofEntity(
        id = if (localId > 0L) localId else existing?.id ?: 0L,
        transactionId = resolvedTransactionId,
        tanggalPembayaran = tanggalPembayaran,
        nominalPembayaran = nominalPembayaran,
        metodePembayaran = metodePembayaran.ifBlank { "Tunai" },
        namaPengirim = namaPengirim,
        catatanPembayaran = catatanPembayaran,
        fotoBuktiUri = mergeLocalOrRemoteMediaUri(existing?.fotoBuktiUri, fotoBuktiUrl),
        waktuUpload = waktuUpload.ifBlank { existing?.waktuUpload.orEmpty() },
        unitItemId = unitItemId ?: existing?.unitItemId,
        alokasiUnitJson = alokasiUnitJson.ifBlank { existing?.alokasiUnitJson.orEmpty() },
        uuid = existing?.uuid?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
        version = maxOf(existing?.version ?: 1L, version.coerceAtLeast(1L)),
        createdAtMillis = resolvedCreatedAt,
        updatedAtMillis = resolvedUpdatedAt,
        syncStatus = "SYNCED",
        changedFieldsJson = existing?.changedFieldsJson.orEmpty(),
        isDeleted = false,
        driveId = existing?.driveId.orEmpty()
    )
}

enum class CloudChangeType {
    ADDED,
    MODIFIED,
    REMOVED
}

data class CloudDocumentChange<T>(
    val type: CloudChangeType,
    val docId: String,
    val data: T?,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false
)

