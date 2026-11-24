package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

object FreezerUnitStatus {
    const val AVAILABLE = "Tersedia"
    const val RENTED = "Disewa"
    const val MAINTENANCE = "Maintenance"
    const val ENGLISH_AVAILABLE = "Available"
    const val ENGLISH_RENTED = "Rented"
}

@Entity(
    tableName = "freezer_units",
    indices = [
        Index(value = ["kodeUnit"], unique = true),
        Index(value = ["statusUnit"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["is_deleted"])
    ]
)
data class FreezerUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kodeUnit: String,
    val namaUnit: String,
    val jenisUnit: String, // Chest Freezer, Showcase, Freezer ASI, Freezer lainnya
    val owner: String,
    val hargaPembelian: Long,
    val kapasitasLiter: Int,
    val cicilanPerBulan: Long,
    val jumlahCicilanTotal: Int = 12,
    val totalPembayaranCicilan: Long = 0L,
    val statusInvestasi: String = "LIST", // LIST, DIBAYAR SEBAGIAN, DONE, LUNAS, HILANG
    val kategoriSheet: String = "INVESTASI SEWA FREEZER", // INVESTASI SEWA FREEZER or INVESTASI FREEZER POINT FROZEN
    val merek: String,
    val model: String,
    val nomorSeri: String,
    val fotoUnitUri: String = "",
    val fotoKondisiFisikUri: String = "",
    val fotoBagianDalamUri: String = "",
    val fotoMerekSeriUri: String = "",
    val statusUnit: String, // Tersedia / Available, Dipesan, Dalam pengiriman, Sedang disewa / Disewa / Rented, Perawatan, Rusak, Dikembalikan, Dalam Perbaikan, Maintenance, Tidak Aktif, Hilang
    val lokasiUnit: String,
    val tanggalPembelian: String,
    val keterangan: String = "",
    // Smart Unit Management fields (v13)
    val kondisiUnit: String = "Baik & Siap Pakai",
    val tanggalPengiriman: String = "",
    val lokasiGpsPemasangan: String = "",
    val latitudePemasangan: Double? = null,
    val longitudePemasangan: Double? = null,
    // Freezer Unit Registry Maintenance History summary (v19)
    val maintenanceHistory: String = "",
    // Single-Source-of-Truth Sync & Versioning Metadata (v20/v21/v22)
    @ColumnInfo(name = "uuid") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "version") val version: Long = 1L,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING_CREATE",
    @ColumnInfo(name = "changed_fields_json") val changedFieldsJson: String = "",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "drive_id") val driveId: String = ""
) {
    @get:Ignore
    val revision: Long
        get() = version
    fun isRentedStatus(): Boolean =
        statusUnit.equals("Disewa", ignoreCase = true) ||
            statusUnit.equals("Sedang disewa", ignoreCase = true) ||
            statusUnit.equals("Masih Tersewa", ignoreCase = true) ||
            statusUnit.equals("Dalam pengiriman", ignoreCase = true) ||
            statusUnit.equals("Dipesan", ignoreCase = true) ||
            statusUnit.equals("Rented", ignoreCase = true)

    fun isLostStatus(): Boolean =
        statusUnit.equals("Hilang", ignoreCase = true) ||
            statusUnit.equals("Lost", ignoreCase = true)

    fun isMaintenanceOrDamagedStatus(): Boolean =
        statusUnit.equals("Dalam Perbaikan", ignoreCase = true) ||
            statusUnit.equals("Maintenance", ignoreCase = true) ||
            statusUnit.equals("Perawatan", ignoreCase = true) ||
            statusUnit.equals("Rusak", ignoreCase = true) ||
            statusUnit.equals("Perbaikan", ignoreCase = true)

    fun isInactiveStatus(): Boolean =
        statusUnit.equals("Tidak Aktif", ignoreCase = true) ||
            statusUnit.equals("Nonaktif", ignoreCase = true)

    fun isAvailableStatus(): Boolean =
        statusUnit.equals("Tersedia", ignoreCase = true) ||
            statusUnit.equals("Available", ignoreCase = true)

    /**
     * Strict DB-level availability rule for new transactions & adding units to existing transactions:
     * ONLY non-deleted units with status "Tersedia" (or legacy "Available") are selectable.
     * Strictly excludes Hilang, Disewa/Sedang disewa/Masih Tersewa, Perbaikan/Dalam Perbaikan/Maintenance/Perawatan/Rusak,
     * Dipesan, Dalam pengiriman, and Tidak Aktif.
     */
    fun isStrictlyAvailableForNewRental(): Boolean =
        !isDeleted && isAvailableStatus()
}

@Entity(
    tableName = "freezer_unit_maintenance",
    foreignKeys = [
        ForeignKey(
            entity = FreezerUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["unit_id"]),
        Index(value = ["unit_code"]),
        Index(value = ["maintenance_date"])
    ]
)
data class FreezerUnitMaintenanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "unit_id") val unitId: Long,
    @ColumnInfo(name = "unit_code") val unitCode: String = "",
    @ColumnInfo(name = "maintenance_date") val maintenanceDate: String,
    @ColumnInfo(name = "maintenance_type") val maintenanceType: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "technician_name") val technicianName: String = "",
    @ColumnInfo(name = "cost") val cost: Long = 0L,
    @ColumnInfo(name = "status_after_maintenance") val statusAfterMaintenance: String = "Tersedia",
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long = System.currentTimeMillis()
)

data class FreezerUnitWithMaintenanceHistory(
    @Embedded val unit: FreezerUnitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "unit_id"
    )
    val maintenanceRecords: List<FreezerUnitMaintenanceEntity> = emptyList()
)

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["nik"]),
        Index(value = ["nomorHp"]),
        Index(value = ["kodePelanggan"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["is_deleted"])
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kodePelanggan: String = "", // Permanent Customer ID e.g. CUST/SAF/0001
    val namaLengkap: String,
    val nik: String,
    val nomorHp: String,
    val alamatLengkap: String,
    val jenisKelamin: String, // Laki-laki, Perempuan
    val fotoKtpDepanUri: String = "",
    val fotoKtpBelakangUri: String = "",
    val catatan: String = "",
    val tanggalRegistrasi: String,
    // Complete Indonesian KTP OCR Fields (Section 1 & Section 7)
    val tempatLahir: String = "",
    val tanggalLahir: String = "",
    val rtRw: String = "",
    val rt: String = "", // Separated RT
    val rw: String = "", // Separated RW
    val kelurahanDesa: String = "",
    val kecamatan: String = "",
    val kabupatenKota: String = "Kota Surakarta",
    val agama: String = "",
    val statusPerkawinan: String = "",
    val pekerjaan: String = "",
    val kewarganegaraan: String = "WNI",
    val berlakuHingga: String = "SEUMUR HIDUP",
    val persetujuanDataKtpLokasi: Boolean = true,
    // Default Usage Location & GPS Coordinates integrated with Customer, New Transaction & Google Maps
    val namaLokasiDefault: String = "",
    val lokasiPenggunaanDefault: String = "",
    val rtRwLokasiDefault: String = "",
    val kelurahanLokasiDefault: String = "",
    val kecamatanLokasiDefault: String = "",
    val kabupatenKotaLokasiDefault: String = "Kota Surakarta",
    val latitudeDefault: Double? = null,
    val longitudeDefault: Double? = null,
    val linkGoogleMapsDefault: String = "",
    // Section I & Section 12: Penyimpanan Hasil OCR & AI Vision KTP Terhubung dengan ID Pelanggan
    val fotoKtpPreprocessedUri: String = "",
    val rawOcrText: String = "",
    val parsedOcrJson: String = "",
    val ocrFieldConfidencesJson: String = "",
    val statusVerifikasiAdminOcr: String = "TERVERIFIKASI_ADMIN",
    val waktuPemindaianOcr: String = "",
    // Master Customer fields (v13)
    val fotoPelangganUri: String = "",
    val statusPelanggan: String = "Pelanggan Aktif",
    // Single-Source-of-Truth Sync & Versioning Metadata (v20/v21/v22)
    @ColumnInfo(name = "uuid") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "version") val version: Long = 1L,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING_CREATE",
    @ColumnInfo(name = "changed_fields_json") val changedFieldsJson: String = "",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "drive_id") val driveId: String = ""
) {
    @get:Ignore
    val revision: Long
        get() = version
}

@Entity(
    tableName = "rental_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FreezerUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["nomorInvoice"], unique = true),
        Index(value = ["customerId"]),
        Index(value = ["unitId"]),
        Index(value = ["statusPembayaran"]),
        Index(value = ["statusPenyewaan"]),
        Index(value = ["tanggalJatuhTempo"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["is_deleted"])
    ]
)
data class RentalTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nomorInvoice: String, // e.g., INV/SAF/2026/0001
    val tanggalPemesanan: String, // yyyy-MM-dd
    val customerId: Long,
    val namaPelangganSnapshot: String,
    val unitId: Long,
    val namaUnitSnapshot: String,
    val kodeUnitSnapshot: String,
    val hargaSewa: Long, // Total harga sewa untuk durasi hari yang dipilih
    val durasiSewaBulan: Int = 1, // Disimpan untuk kompatibilitas laporan bulanan
    val durasiHari: Int, // Durasi sewa dalam Hari
    val tanggalMulaiSewa: String, // yyyy-MM-dd
    val tanggalAkhirSewa: String, // yyyy-MM-dd
    val tanggalJatuhTempo: String, // yyyy-MM-dd
    val nominalPembayaran: Long,
    val ongkir: Long = 0L, // Biaya Ongkos Kirim (Ongkir)
    val deposit: Long = 0L,
    val diskon: Long = 0L,
    val totalTagihan: Long,
    val statusPembayaran: String, // Belum Bayar, Dibayar Sebagian, DP / Uang Muka, Lunas, Jatuh Tempo, Terlambat
    val statusPenyewaan: String, // Booking, Dalam Penyewaan, Selesai, Dibatalkan
    val metodePembayaran: String, // Transfer BCA, Transfer Mandiri, QRIS, Tunai
    val catatan: String = "",
    // Photo Documentation per Transaction (No permanent watermark on original file)
    val fotoKtpDepanTransUri: String = "",
    val fotoKtpBelakangTransUri: String = "",
    val fotoUnitSebelumKirimUri: String = "",
    val fotoKondisiFisikTransUri: String = "",
    val fotoBagianDalamTransUri: String = "",
    val fotoMerekSeriTransUri: String = "",
    val fotoSerahTerimaUri: String = "",
    val fotoKondisiSerahTerimaUri: String = "",
    val fotoPelangganBersamaFreezerUri: String = "",
    val fotoFreezerDiLokasiUri: String = "", // Foto freezer setelah berada di lokasi penggunaan
    val fotoDepanLokasiUri: String = "", // Foto tampak depan tempat usaha atau lokasi penggunaan
    // Photo Timestamps & Captions (Section 3: Tanggal & waktu pengambilan + Keterangan foto)
    val waktuFotoKtp: String = "",
    val ketFotoKtp: String = "",
    val waktuFotoUnitSebelum: String = "",
    val ketFotoUnitSebelum: String = "",
    val waktuFotoSerahTerima: String = "",
    val ketFotoSerahTerima: String = "",
    val waktuFotoFreezerDiLokasi: String = "",
    val ketFotoFreezerDiLokasi: String = "",
    val waktuFotoDepanLokasi: String = "",
    val ketFotoDepanLokasi: String = "",
    // GPS Location Tagging & Detailed Usage Location (Section 2 & 3: LOKASI PENGGUNAAN FREEZER - separate from KTP address)
    val namaLokasiUsaha: String = "",
    val namaPenanggungJawab: String = "",
    val nomorWhatsappLokasi: String = "",
    val alamatLokasiGps: String = "", // Alamat lengkap penggunaan freezer
    val rtRwLokasi: String = "",
    val provinsiLokasi: String = "Jawa Tengah",
    val kabupatenKotaLokasi: String = "Kota Surakarta",
    val kecamatanLokasi: String = "",
    val kelurahanDesaLokasi: String = "",
    val kodePosLokasi: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val akurasiGpsMeter: Float? = null,
    val linkGoogleMaps: String = "",
    val catatanLokasi: String = "",
    val tanggalLokasiGps: String = "",
    val jamLokasiGps: String = "",
    // Section 5: Digital Signature & Handover
    val tandaTanganPelangganUri: String = "",
    val tandaTanganPetugasUri: String = "",
    val namaPenerima: String = "",
    val tanggalPenerimaan: String = "",
    val jamPenerimaan: String = "",
    val namaPetugas: String = "",
    val keteranganKondisiFreezer: String = "",
    val persetujuanPelangganTrans: Boolean = true,
    // Section 7: Invoice PDF Path & Rental Agreement Snapshot (Perjanjian Sewa)
    val pdfInvoicePath: String = "",
    val isiPerjanjianSewaSnapshot: String = "",
    // Section 6: Extension parent reference
    val parentTransactionId: Long? = null,
    val periodeBulanLabel: String = "", // e.g. "OKT 2026" for timeline compatibility
    // Add-On Update Section A: Pilihan Program Penyewaan (Sewa Reguler vs Sewa Hak Milik)
    val jenisProgram: String = "Sewa Reguler", // "Sewa Reguler" or "Sewa Hak Milik"
    val totalPeriodeHakMilik: Int = 0, // e.g. 12, 15, 18, 19, 20, 24 (0 if Sewa Reguler)
    val pembayaranKeHakMilik: Int = 1, // Current payment sequence e.g. 1..totalPeriodeHakMilik
    val statusProgramHakMilik: String = "Aktif", // "Aktif", "Lunas", "Selesai"
    // Permanent Owner Signature Snapshot (preserves the Owner signature version when document was issued)
    val tandaTanganOwnerSnapshotUri: String = "",
    val ukuranTandaTanganOwnerSnapshot: Float = 42f,
    val posisiTandaTanganOwnerSnapshot: String = "RIGHT",
    // Single-Source-of-Truth Sync & Versioning Metadata (v20/v21/v22)
    @ColumnInfo(name = "uuid") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "version") val version: Long = 1L,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING_CREATE",
    @ColumnInfo(name = "changed_fields_json") val changedFieldsJson: String = "",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "drive_id") val driveId: String = "",
    // Permanent Officer Identity & Signature Snapshot Metadata (v23 — additive)
    @ColumnInfo(name = "officer_account_id") val officerAccountId: String = "",
    @ColumnInfo(name = "officer_snapshot_locked") val officerSnapshotLocked: Boolean = false,
    @ColumnInfo(name = "officer_signed_at") val officerSignedAt: String = "",
    // Soft-Delete / Tombstone Audit Metadata (v24 — additive, non-destructive)
    @ColumnInfo(name = "deleted_at") val deletedAtMillis: Long = 0L,
    @ColumnInfo(name = "deleted_by") val deletedBy: String = ""
) {
    @get:Ignore
    val revision: Long
        get() = version
}

/**
 * 1. FITUR BUKTI PEMBAYARAN (PaymentProofEntity)
 * Supports multiple payment proofs per transaction (staged/partial payments) without overwriting previous proofs.
 */
@Entity(
    tableName = "payment_proofs",
    foreignKeys = [
        ForeignKey(
            entity = RentalTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["is_deleted"])
    ]
)
data class PaymentProofEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val tanggalPembayaran: String, // yyyy-MM-dd
    val nominalPembayaran: Long,
    val metodePembayaran: String, // Transfer BCA, QRIS, Cash / Tunai, etc.
    val namaPengirim: String = "",
    val catatanPembayaran: String = "",
    val fotoBuktiUri: String = "", // Saved in filesDir/photos/payment_proofs/
    val waktuUpload: String = "",
    // Partial payment allocation support (v13)
    val unitItemId: Long? = null,
    val alokasiUnitJson: String = "",
    // Single-Source-of-Truth Sync & Versioning Metadata (v20/v21/v22)
    @ColumnInfo(name = "uuid") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "version") val version: Long = 1L,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING_CREATE",
    @ColumnInfo(name = "changed_fields_json") val changedFieldsJson: String = "",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "drive_id") val driveId: String = ""
) {
    @get:Ignore
    val revision: Long
        get() = version
}

/**
 * 2. SETTING -> METODE PEMBAYARAN (PaymentMethodEntity)
 * Configurable payment methods (Transfer Bank, QRIS, Cash/Tunai, Lainnya).
 * Active methods automatically appear in Transaksi Baru with QRIS image or Bank account details.
 */
@Entity(
    tableName = "payment_methods",
    indices = [Index(value = ["namaMetode"])]
)
data class PaymentMethodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val namaMetode: String, // e.g. Transfer BCA, Transfer BRI, Transfer Mandiri, Transfer BNI, QRIS, Cash / Tunai
    val kategoriMetode: String = "TRANSFER_BANK", // TRANSFER_BANK, QRIS, TUNAI, LAINNYA
    val namaBankAtauPenyedia: String = "",
    val nomorRekening: String = "",
    val namaPemilikRekening: String = "",
    val fotoQrisUri: String = "",
    val logoBankUri: String = "",
    val keteranganPembayaran: String = "",
    val isAktif: Boolean = true
)

/**
 * Menu Serah Terima Pengembalian Freezer (Section 3, 4, 5, 6)
 * Relational entity connected to rental_transactions, customers, and freezer_units by ID.
 */
@Entity(
    tableName = "freezer_returns",
    foreignKeys = [
        ForeignKey(
            entity = RentalTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FreezerUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["nomorDokumenPengembalian"], unique = true),
        Index(value = ["transactionId"]),
        Index(value = ["customerId"]),
        Index(value = ["unitId"])
    ]
)
data class FreezerReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nomorDokumenPengembalian: String, // e.g., RET/SAF/2026/0001
    val transactionId: Long,
    val nomorInvoiceSnapshot: String,
    val customerId: Long,
    val namaPelangganSnapshot: String,
    val nikPelangganSnapshot: String = "",
    val nomorWaPelangganSnapshot: String = "",
    val alamatKtpSnapshot: String = "",
    val unitId: Long,
    val kodeUnitSnapshot: String,
    val namaUnitSnapshot: String,
    val merekTipeSnapshot: String = "",
    val tanggalMulaiSewaSnapshot: String = "",
    val hargaSewaSnapshot: Long = 0L,
    val tanggalJatuhTempoSnapshot: String = "",
    val alamatPenggunaanSnapshot: String = "",
    val latitudeTerakhir: Double? = null,
    val longitudeTerakhir: Double? = null,
    // C. Tanggal & Waktu Pengembalian
    val tanggalPengembalian: String, // yyyy-MM-dd
    val jamPengembalian: String, // HH:mm:ss
    val namaPetugasPenerima: String,
    // D. Kondisi Unit Saat Dikembalikan
    val kondisiUnitSaatKembali: String, // Baik / Normal, Baik dengan catatan, Rusak ringan, Rusak berat, Perlu pemeriksaan teknisi
    val catatanKondisiUnit: String = "",
    val statusUnitSetelahKembali: String = "Tersedia", // Tersedia atau Dalam Perbaikan
    // A. Foto Dokumentasi Pengembalian (4 slot)
    val fotoKondisiKembaliUri: String = "",
    val fotoBagianLuarUri: String = "",
    val fotoBagianDalamUri: String = "",
    val fotoTambahanUri: String = "",
    // B. Tanda Tangan Digital Pengembalian
    val tandaTanganPelangganUri: String = "",
    val tandaTanganPetugasUri: String = "",
    // Section 5: Generated Return Handover PDF Path
    val pdfBuktiPengembalianPath: String = "",
    // Owner signature snapshot on return PDF
    val tandaTanganOwnerSnapshotUri: String = "",
    val ukuranTandaTanganOwnerSnapshot: Float = 42f,
    val posisiTandaTanganOwnerSnapshot: String = "RIGHT"
)

@Entity(tableName = "extension_history")
data class RentalExtensionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalTransactionId: Long,
    val newTransactionId: Long,
    val nomorInvoiceLama: String,
    val nomorInvoiceBaru: String,
    val tanggalPerpanjangan: String,
    val tanggalAkhirLama: String,
    val tanggalAkhirBaru: String,
    val tambahanDurasiHari: Int,
    val tagihanBaru: Long,
    val catatan: String = "",
    @ColumnInfo(name = "uuid") val uuid: String = "",
    @ColumnInfo(name = "customer_id") val customerId: Long = 0L,
    @ColumnInfo(name = "unit_id") val unitId: Long = 0L,
    @ColumnInfo(name = "unit_item_id") val unitItemId: Long = 0L,
    @ColumnInfo(name = "jenis_program") val jenisProgram: String = "Sewa Reguler",
    @ColumnInfo(name = "pembayaran_ke_hak_milik") val pembayaranKeHakMilik: Int = 0,
    @ColumnInfo(name = "total_periode_hak_milik") val totalPeriodeHakMilik: Int = 0,
    @ColumnInfo(name = "diskon") val diskon: Long = 0L,
    @ColumnInfo(name = "nominal_dibayar") val nominalDibayar: Long = 0L,
    @ColumnInfo(name = "status_pembayaran") val statusPembayaran: String = "Lunas",
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: String,
    val jenisOperasi: String, // IMPORT XLSX, IMPORT CSV, EXPORT XLSX, GOOGLE SHEETS SYNC, BACKUP, RESET DATA
    val sheetName: String,
    val jumlahBerhasil: Int,
    val jumlahGagal: Int,
    val status: String, // SUKSES, SEBAGIAN, GAGAL
    val detailPesan: String
)

data class CompanyProfile(
    val namaPerusahaan: String = "SEWAin Aja Freezer SOC",
    val alamat: String = "Jl. Slamet Riyadi No. 214, Laweyan, Kota Surakarta (Solo Raya)",
    val nomorHp: String = "0812-2600-8899",
    val email: String = "admin@sewainajafreezersoc.com",
    val instagram: String = "@sewainajafreezer.soc",
    val facebook: String = "SEWAin Aja Freezer Solo Raya",
    val profilePictureUri: String = "",
    val identitasUsaha: String = "NIB / SIUP: 9120300451829 • Persewaan Cold Storage & Freezer Solo Raya",
    val informasiTambahan: String = "Layanan Antar-Jemput & Teknisi Siaga 24 Jam Wilayah Solo Raya",
    // Section 5: Tanda Tangan Permanen Owner pada PDF (Setting -> Profile Perusahaan -> Tanda Tangan Owner)
    val ownerSignatureUri: String = "",
    val ownerSignatureSizePt: Float = 42f, // 26f .. 72f
    val ownerSignaturePosition: String = "RIGHT", // LEFT, CENTER, RIGHT
    val ownerName: String = "Owner SEWAin Aja Freezer SOC"
)

/**
 * Section 6: Setting -> Customize Template PDF configuration (Advanced Font, Header, Logo, Page & Live Preview)
 * Applied automatically to:
 * 1. Invoice transaksi
 * 2. Surat perjanjian sewa
 * 3. Form serah terima unit
 * 4. Form pengembalian freezer
 */
data class PdfTemplateSettings(
    val backgroundUri: String = "", // Local file path in filesDir/pdf_templates/ (JPG, PNG, or PDF)
    val backgroundType: String = "DEFAULT", // DEFAULT, IMAGE, PDF
    val backgroundFileName: String = "Template Default SEWAin Aja Freezer SOC",
    val backgroundOpacity: Float = 0.16f, // 0.05f .. 0.55f so text, tables, photos & signatures remain readable
    // D. Pengaturan Halaman
    val paperSize: String = "A4", // A4 (595 x 842 pt)
    val marginTopPt: Float = 34f,
    val marginBottomPt: Float = 34f,
    val marginHorizontalPt: Float = 34f,
    val marginLeftPt: Float = 34f,
    val marginRightPt: Float = 34f,
    val footerHeightPt: Float = 34f,
    val paragraphSpacingPt: Float = 3.5f,
    val contentOffsetYPt: Float = 0f,
    // C. Pengaturan Logo
    val customLogoUri: String = "",
    val logoPosition: String = "LEFT", // LEFT, CENTER, RIGHT
    val logoSizePt: Float = 44f, // Adjustable logo size in pt (28f .. 64f)
    // A. Pengaturan Font
    val fontFamily: String = "SANS_SERIF", // SANS_SERIF, SERIF, MONOSPACE
    val fontWeight: String = "REGULAR", // REGULAR, MEDIUM, BOLD
    val titleFontSizePt: Float = 13.5f,
    val subtitleFontSizePt: Float = 10.0f,
    val bodyFontSizePt: Float = 8.8f,
    val captionFontSizePt: Float = 7.8f,
    // B. Pengaturan Header
    val headerHeightPt: Float = 80f, // 64f .. 130f
    val headerWidthPct: Float = 100f, // 85f .. 100f
    val companyNameFontSizePt: Float = 13.5f,
    val companyAddressFontSizePt: Float = 8.0f,
    val companyPhoneFontSizePt: Float = 8.0f,
    val headerElementSpacingPt: Float = 13.0f
)

/**
 * SISTEM PENYIMPANAN FOTO OFFLINE PER PELANGGAN DI ANDROID
 * Tabel metadata dokumentasi foto pelanggan yang terhubung dengan folder fisik di:
 * Pictures/SEWAin Aja Freezer SOC/<FolderPelanggan>/<SubfolderKategori>/
 */
@Entity(
    tableName = "customer_photos",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["transactionId"]),
        Index(value = ["kategori"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CustomerPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val transactionId: Long? = null,
    val kategori: String, // 01_KTP, 02_Serah_Terima, 03_Foto_Unit, 04_Foto_Lokasi, 05_Pengembalian
    val namaDokumentasi: String, // e.g. KTP_DEPAN, FREEZER_DI_LOKASI, TAMPAK_DEPAN_LOKASI
    val filePath: String, // MediaStore Uri content:// or file://
    val relativeFolder: String = "", // e.g. Pictures/SEWAin Aja Freezer SOC/Budi Santoso_CUST001/04_Foto_Lokasi
    val fileName: String = "", // e.g. 20261111_CUST001_FREEZER_DI_LOKASI_001.jpg
    val tanggalWaktuIso: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val catatan: String = "",
    val sizeBytes: Long = 0L,
    @ColumnInfo(name = "unit_id") val unitId: Long? = null,
    @ColumnInfo(name = "uuid") val uuid: String = "",
    @ColumnInfo(name = "upload_status") val uploadStatus: String = "PENDING", // PENDING | UPLOADING | UPLOADED | FAILED
    @ColumnInfo(name = "drive_file_id") val driveFileId: String = "",
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * TABEL 1 (Supporting Table v11): CUSTOMER_DOCUMENTS
 * Menyimpan metadata dokumen pelanggan (KTP depan, KTP belakang, dokumentasi pelanggan)
 * dengan relative_path terstruktur di dalam SEWAIN_AJA_FREEZER/CUSTOMERS/<CUSTOMER_ID>/
 */
@Entity(
    tableName = "customer_documents",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["document_type"]),
        Index(value = ["checksum"])
    ]
)
data class CustomerDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "customer_id")
    val customerId: Long,
    @ColumnInfo(name = "document_type")
    val documentType: String, // KTP_DEPAN | KTP_BELAKANG | DOKUMEN_PELANGGAN | FOTO_PELANGGAN
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String, // e.g., SEWAIN_AJA_FREEZER/CUSTOMERS/1/KTP/KTP_DEPAN_1_1700000000.jpg
    @ColumnInfo(name = "mime_type")
    val mimeType: String = "image/jpeg",
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,
    @ColumnInfo(name = "checksum")
    val checksum: String = "", // SHA-256
    @ColumnInfo(name = "created_at")
    val createdAt: String = "",
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = ""
)

/**
 * TABEL 2 (Supporting Table v11): TRANSACTION_DOCUMENTS
 * Menyimpan metadata dokumen transaksi & freezer (bukti pembayaran, dokumen perjanjian,
 * dokumentasi serah terima, foto unit freezer, kondisi freezer, dokumentasi pengiriman)
 */
@Entity(
    tableName = "transaction_documents",
    indices = [
        Index(value = ["transaction_id"]),
        Index(value = ["customer_id"]),
        Index(value = ["document_type"]),
        Index(value = ["checksum"])
    ]
)
data class TransactionDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,
    @ColumnInfo(name = "customer_id")
    val customerId: Long,
    @ColumnInfo(name = "document_type")
    val documentType: String, // BUKTI_PEMBAYARAN | DOKUMEN_PERJANJIAN | DOKUMENTASI_TRANSAKSI | DOKUMENTASI_FREEZER
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String, // e.g., SEWAIN_AJA_FREEZER/TRANSACTIONS/1/PAYMENT/...
    @ColumnInfo(name = "mime_type")
    val mimeType: String = "image/jpeg",
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,
    @ColumnInfo(name = "checksum")
    val checksum: String = "", // SHA-256
    @ColumnInfo(name = "created_at")
    val createdAt: String = ""
)

/**
 * TABEL 3 (Supporting Table v11): CUSTOMER_LOCATION
 * Menyimpan titik koordinat GPS, alamat lokasi, sumber lokasi (GPS_AUTO / MANUAL_INPUT / MAP_PICKER), dan akurasi.
 */
@Entity(
    tableName = "customer_location",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["customer_id"])
    ]
)
data class CustomerLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "customer_id")
    val customerId: Long,
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    @ColumnInfo(name = "address")
    val address: String = "",
    @ColumnInfo(name = "location_source")
    val locationSource: String = "GPS_AUTO", // GPS_AUTO | MANUAL_INPUT | MAP_PICKER
    @ColumnInfo(name = "accuracy")
    val accuracy: Float = 0f,
    @ColumnInfo(name = "created_at")
    val createdAt: String = "",
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = ""
)

/**
 * TABEL 4 (Supporting Table v11): LOCAL_BACKUP
 * Menyimpan riwayat backup database, dokumen, atau full backup (.zip) beserta status dan ukuran file.
 */
@Entity(
    tableName = "local_backup",
    indices = [
        Index(value = ["backup_type"]),
        Index(value = ["created_at"]),
        Index(value = ["backup_id"])
    ]
)
data class LocalBackupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "backup_name")
    val backupName: String,
    @ColumnInfo(name = "backup_path")
    val backupPath: String, // relative path inside SEWAIN_AJA_FREEZER/BACKUP/...
    @ColumnInfo(name = "backup_size")
    val backupSize: Long = 0L,
    @ColumnInfo(name = "backup_type")
    val backupType: String = "FULL_BACKUP", // DATABASE | CUSTOMER_PHOTOS | KTP_PHOTOS | PAYMENT_PROOFS | AGREEMENTS | FREEZER_DOCS | FULL_BACKUP
    @ColumnInfo(name = "backup_status")
    val backupStatus: String = "SUCCESS", // SUCCESS | FAILED | VERIFIED | RESTORED | QUEUED_OFFLINE
    @ColumnInfo(name = "created_at")
    val createdAt: String = "",
    @ColumnInfo(name = "backup_id")
    val backupId: String = "",
    @ColumnInfo(name = "location_type")
    val locationType: String = "LOKAL", // LOKAL | GOOGLE_DRIVE | LOKAL_DAN_DRIVE
    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String = "",
    @ColumnInfo(name = "verification_status")
    val verificationStatus: String = "TERVERIFIKASI", // TERVERIFIKASI | GAGAL | MENUNGGU
    @ColumnInfo(name = "checksum_sha256")
    val checksumSha256: String = "",
    @ColumnInfo(name = "is_encrypted")
    val isEncrypted: Boolean = true,
    @ColumnInfo(name = "customer_count")
    val customerCount: Int = 0,
    @ColumnInfo(name = "unit_count")
    val unitCount: Int = 0,
    @ColumnInfo(name = "transaction_count")
    val transactionCount: Int = 0,
    @ColumnInfo(name = "payment_count")
    val paymentCount: Int = 0,
    @ColumnInfo(name = "photo_count")
    val photoCount: Int = 0
)

/**
 * TABEL 5 (Supporting Table v11): APP_PERMISSION
 * Menyimpan status izin perangkat (CAMERA, GALLERY, LOCATION, LOCAL_STORAGE) secara persisten di SQLite.
 */
@Entity(
    tableName = "app_permission",
    indices = [
        Index(value = ["permission_name"], unique = true)
    ]
)
data class AppPermissionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "permission_name")
    val permissionName: String, // CAMERA | GALLERY | LOCATION | LOCAL_STORAGE
    @ColumnInfo(name = "permission_status")
    val permissionStatus: String, // AKTIF | PERLU_PERSETUJUAN | TIDAK_AKTIF
    @ColumnInfo(name = "last_checked")
    val lastChecked: String = "",
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = ""
)

/**
 * TABEL MULTI-UNIT TRANSAKSI (v12): TRANSACTION_UNIT_ITEMS
 * Menyimpan setiap unit freezer dalam satu transaksi pelanggan dengan:
 * - Tipe & kapasitas freezer masing-masing
 * - Program sewa masing-masing (Sewa Reguler / Sewa Hak Milik)
 * - Harga sewa & nominal pembayaran masing-masing
 * - Tanggal mulai sewa & Tanggal jatuh tempo masing-masing (Unit-Based Billing Schedule)
 * - Dokumentasi foto terpisah per unit (4 Foto Kondisi Unit + 4 Foto Serah Terima)
 */
@Entity(
    tableName = "transaction_unit_items",
    foreignKeys = [
        ForeignKey(
            entity = RentalTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FreezerUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transaction_id"]),
        Index(value = ["customer_id"]),
        Index(value = ["unit_id"]),
        Index(value = ["tanggal_jatuh_tempo"])
    ]
)
data class TransactionUnitItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L, // Unit Transaction ID
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,
    @ColumnInfo(name = "customer_id")
    val customerId: Long,
    @ColumnInfo(name = "unit_id")
    val unitId: Long, // Freezer ID
    @ColumnInfo(name = "unit_index_number")
    val unitIndexNumber: Int = 1,
    @ColumnInfo(name = "kode_unit_snapshot")
    val kodeUnitSnapshot: String = "",
    @ColumnInfo(name = "nama_unit_snapshot")
    val namaUnitSnapshot: String = "",
    @ColumnInfo(name = "jenis_unit_snapshot")
    val jenisUnitSnapshot: String = "",
    @ColumnInfo(name = "merek_snapshot")
    val merekSnapshot: String = "",
    @ColumnInfo(name = "kapasitas_liter_snapshot")
    val kapasitasLiterSnapshot: Int = 0,
    @ColumnInfo(name = "jenis_program")
    val jenisProgram: String = "Sewa Reguler", // "Sewa Reguler" | "Sewa Hak Milik"
    @ColumnInfo(name = "total_periode_hak_milik")
    val totalPeriodeHakMilik: Int = 0,
    @ColumnInfo(name = "pembayaran_ke_hak_milik")
    val pembayaranKeHakMilik: Int = 1,
    @ColumnInfo(name = "status_program_hak_milik")
    val statusProgramHakMilik: String = "Aktif",
    @ColumnInfo(name = "harga_sewa")
    val hargaSewa: Long = 0L,
    @ColumnInfo(name = "durasi_hari")
    val durasiHari: Int = 30,
    @ColumnInfo(name = "tanggal_mulai_sewa")
    val tanggalMulaiSewa: String = "",
    @ColumnInfo(name = "tanggal_akhir_sewa")
    val tanggalAkhirSewa: String = "",
    @ColumnInfo(name = "tanggal_jatuh_tempo")
    val tanggalJatuhTempo: String = "", // Payment Schedule per unit (Unit-Based Billing Schedule)
    @ColumnInfo(name = "nominal_pembayaran")
    val nominalPembayaran: Long = 0L,
    @ColumnInfo(name = "status_pembayaran")
    val statusPembayaran: String = "Belum Bayar", // Lunas | Dibayar Sebagian | DP / Uang Muka | Belum Bayar | Jatuh Tempo | Terlambat
    @ColumnInfo(name = "status_penyewaan")
    val statusPenyewaan: String = "Dalam Penyewaan", // Dalam Penyewaan | Booking | Selesai | Dibatalkan
    @ColumnInfo(name = "catatan_unit")
    val catatanUnit: String = "",

    // A. Foto Kondisi Unit (4 slots per unit)
    @ColumnInfo(name = "foto_kondisi_depan_uri")
    val fotoKondisiDepanUri: String = "",
    @ColumnInfo(name = "foto_kondisi_samping_uri")
    val fotoKondisiSampingUri: String = "",
    @ColumnInfo(name = "foto_kondisi_dalam_uri")
    val fotoKondisiDalamUri: String = "",
    @ColumnInfo(name = "foto_kondisi_tambahan_uri")
    val fotoKondisiTambahanUri: String = "",

    // B. Foto Serah Terima (4 slots per unit)
    @ColumnInfo(name = "foto_serah_terima_pelanggan_uri")
    val fotoSerahTerimaPelangganUri: String = "",
    @ColumnInfo(name = "foto_serah_terima_lokasi_uri")
    val fotoSerahTerimaLokasiUri: String = "",
    @ColumnInfo(name = "foto_bukti_penerimaan_uri")
    val fotoBuktiPenerimaanUri: String = "",
    @ColumnInfo(name = "foto_serah_terima_tambahan_uri")
    val fotoSerahTerimaTambahanUri: String = "",

    @ColumnInfo(name = "storage_folder_path")
    val storageFolderPath: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: String = "",
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = "",
    @ColumnInfo(name = "periode_tagihan")
    val periodeTagihan: String = ""
) {
    val sisaTagihanUnit: Long
        get() = (hargaSewa - nominalPembayaran).coerceAtLeast(0L)

    fun computeSmartBillingStatus(todayIso: String = com.example.util.DateCurrencyUtils.todayIso()): String {
        if (hargaSewa > 0L && nominalPembayaran >= hargaSewa) return "Sudah dibayar"
        val daysToDue = if (tanggalJatuhTempo.isNotBlank()) {
            com.example.util.DateCurrencyUtils.daysBetweenIsoDates(todayIso, tanggalJatuhTempo)
        } else 999L
        return when {
            nominalPembayaran > 0L -> "Pembayaran sebagian"
            daysToDue == 0L -> "Jatuh tempo hari ini"
            daysToDue < 0L -> "Terlambat"
            else -> "Belum jatuh tempo"
        }
    }

    val kondisiPhotosList: List<String>
        get() = listOf(
            fotoKondisiDepanUri,
            fotoKondisiSampingUri,
            fotoKondisiDalamUri,
            fotoKondisiTambahanUri
        ).filter { it.isNotBlank() }

    val serahTerimaPhotosList: List<String>
        get() = listOf(
            fotoSerahTerimaPelangganUri,
            fotoSerahTerimaLokasiUri,
            fotoBuktiPenerimaanUri,
            fotoSerahTerimaTambahanUri
        ).filter { it.isNotBlank() }

    val kondisiPhotosUploadedCount: Int
        get() = kondisiPhotosList.size

    val serahTerimaPhotosUploadedCount: Int
        get() = serahTerimaPhotosList.size

    val isDocumentationComplete: Boolean
        get() = fotoKondisiDepanUri.isNotBlank() && fotoSerahTerimaPelangganUri.isNotBlank()

    val statusKelengkapanFoto: String
        get() = if (isDocumentationComplete) "Lengkap" else "Belum Lengkap"

    val hakMilikRemainingCount: Int
        get() = if (jenisProgram == "Sewa Hak Milik" && totalPeriodeHakMilik > 0) {
            (totalPeriodeHakMilik - pembayaranKeHakMilik).coerceAtLeast(0)
        } else 0

    val hakMilikProgressFraction: Float
        get() = if (jenisProgram == "Sewa Hak Milik" && totalPeriodeHakMilik > 0) {
            (pembayaranKeHakMilik.toFloat() / totalPeriodeHakMilik.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val hakMilikProgressLabel: String
        get() = if (jenisProgram == "Sewa Hak Milik" && totalPeriodeHakMilik > 0) {
            "Pembayaran ke-$pembayaranKeHakMilik dari $totalPeriodeHakMilik"
        } else ""
}

/**
 * TABEL TANDA TANGAN TRANSAKSI (v12): TRANSACTION_SIGNATURES
 * Menyimpan tanda tangan digital langsung (DRAW) maupun upload gambar tanda tangan (UPLOAD_IMAGE)
 * dengan background transparan yang terhubung ke Customer ID dan Transaction ID.
 */
@Entity(
    tableName = "transaction_signatures",
    foreignKeys = [
        ForeignKey(
            entity = RentalTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transaction_id"]),
        Index(value = ["customer_id"]),
        Index(value = ["signer_role"])
    ]
)
data class TransactionSignatureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L, // Signature ID
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,
    @ColumnInfo(name = "customer_id")
    val customerId: Long,
    @ColumnInfo(name = "signer_role")
    val signerRole: String = "PENYEWA", // PENYEWA | PETUGAS_SAF
    @ColumnInfo(name = "signer_name")
    val signerName: String = "",
    @ColumnInfo(name = "signature_method")
    val signatureMethod: String = "DRAW", // DRAW | UPLOAD_IMAGE
    @ColumnInfo(name = "file_path")
    val filePath: String = "",
    @ColumnInfo(name = "relative_path")
    val relativePath: String = "",
    @ColumnInfo(name = "has_transparent_background")
    val hasTransparentBackground: Boolean = true,
    @ColumnInfo(name = "signed_date")
    val signedDate: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: String = ""
)

/**
 * TABEL ALOKASI PEMBAYARAN PER UNIT (v13): UNIT_PAYMENT_ALLOCATIONS
 * Menghubungkan setiap pembayaran (penuh maupun sebagian / partial payment) secara spesifik ke unit freezer tertentu
 * dalam hierarki CUSTOMER -> CONTRACT -> RENTAL UNIT -> PAYMENT SCHEDULE -> PAYMENT HISTORY.
 */
@Entity(
    tableName = "unit_payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = RentalTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionUnitItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_item_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["payment_proof_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["unit_item_id"]),
        Index(value = ["customer_id"])
    ]
)
data class UnitPaymentAllocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "payment_proof_id")
    val paymentProofId: Long,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,
    @ColumnInfo(name = "unit_item_id")
    val unitItemId: Long,
    @ColumnInfo(name = "customer_id")
    val customerId: Long,
    @ColumnInfo(name = "unit_id")
    val unitId: Long,
    @ColumnInfo(name = "nama_unit_snapshot")
    val namaUnitSnapshot: String = "",
    @ColumnInfo(name = "kode_unit_snapshot")
    val kodeUnitSnapshot: String = "",
    @ColumnInfo(name = "allocated_amount")
    val allocatedAmount: Long = 0L,
    @ColumnInfo(name = "payment_date")
    val paymentDate: String = "",
    @ColumnInfo(name = "periode_tagihan")
    val periodeTagihan: String = "",
    @ColumnInfo(name = "metode_pembayaran")
    val metodePembayaran: String = "",
    @ColumnInfo(name = "catatan")
    val catatan: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: String = ""
)

/**
 * TABEL TIMELINE AKTIVITAS PELANGGAN (v13): CUSTOMER_TIMELINE_EVENTS
 * Mencatat secara otomatis kronologi aktivitas pelanggan (Registrasi, Sewa unit, Tambah unit,
 * Pembayaran penuh/sebagian, Upload dokumentasi, Pengembalian unit, Kontrak selesai).
 */
@Entity(
    tableName = "customer_timeline_events",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["event_type"])
    ]
)
data class CustomerTimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "customer_id")
    val customerId: Long,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,
    @ColumnInfo(name = "unit_id")
    val unitId: Long? = null,
    @ColumnInfo(name = "event_type")
    val eventType: String, // REGISTRASI | SEWA_UNIT | TAMBAH_UNIT | PEMBAYARAN | DOKUMENTASI | PENGEMBALIAN | KONTRAK_SELESAI
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String = "",
    @ColumnInfo(name = "amount")
    val amount: Long = 0L,
    @ColumnInfo(name = "event_timestamp")
    val eventTimestamp: String = "",
    @ColumnInfo(name = "event_date_iso")
    val eventDateIso: String = ""
)

data class SmartNotificationSettings(
    val enableDueSoonReminder: Boolean = true,
    val dueReminderDaysBefore: Int = 3,
    val enableOverdueReminder: Boolean = true,
    val enableUnitAddedNotification: Boolean = true,
    val enableContractCompletedNotification: Boolean = true,
    val enableIncompletePhotoReminder: Boolean = true,
    val enableUnreturnedUnitReminder: Boolean = true
)

val DEFAULT_SEWA_HAK_MILIK_PERIODS = listOf(12, 15, 18, 19, 20, 24)

const val DEFAULT_PERJANJIAN_SEWA = """1. Penyewa bertanggung jawab atas unit freezer selama masa penyewaan berlangsung.
2. Penyewa wajib menjaga kebersihan dan kondisi unit freezer.
3. Penyewa tidak diperkenankan memindahkan unit tanpa persetujuan pihak penyedia.
4. Kerusakan akibat kelalaian pengguna menjadi tanggung jawab penyewa.
5. Pembayaran sewa dilakukan sesuai periode dan tanggal jatuh tempo yang telah disepakati.
6. Pengembalian unit dilakukan dalam kondisi sesuai kesepakatan awal.
7. Ketentuan lainnya mengikuti kesepakatan antara kedua belah pihak."""

/**
 * TABEL CLOUD SYNC QUEUE (v14): CLOUD_SYNC_QUEUE
 * Durable offline-first mutation queue for automatic cloud sync & retry when connectivity returns.
 */
@Entity(
    tableName = "cloud_sync_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["entityType"]),
        Index(value = ["cloudDocId"]),
        Index(value = ["uid", "entityType", "cloudDocId"], unique = true)
    ]
)
data class CloudSyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String = "",
    val entityType: String,
    val entityLocalId: Long = 0L,
    val cloudDocId: String,
    val operation: String = "UPSERT",
    val payloadJson: String = "",
    val status: String = "PENDING",
    val retryCount: Int = 0,
    val lastError: String = "",
    val clientUpdatedAtMillis: Long = 0L,
    val createdAt: String = "",
    val updatedAt: String = ""
)

/**
 * TABEL CLOUD PHOTO JOBS (v14): CLOUD_PHOTO_JOBS
 * Tracks compressed full-resolution and thumbnail uploads to Firebase Cloud Storage with progress and retry.
 */
@Entity(
    tableName = "cloud_photo_jobs",
    indices = [
        Index(value = ["status"]),
        Index(value = ["ownerType", "ownerLocalId"]),
        Index(value = ["uid", "storagePath"], unique = true)
    ]
)
data class CloudPhotoJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String = "",
    val ownerType: String,
    val ownerLocalId: Long = 0L,
    val ownerCloudId: String = "",
    val photoSlot: String,
    val localFilePath: String,
    val thumbnailPath: String = "",
    val storagePath: String,
    val cloudDownloadUrl: String = "",
    val thumbnailDownloadUrl: String = "",
    val mimeType: String = "image/jpeg",
    val bytesTotal: Long = 0L,
    val bytesUploaded: Long = 0L,
    val progressPercent: Int = 0,
    val status: String = "QUEUED",
    val retryCount: Int = 0,
    val errorMessage: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

/**
 * TABEL CLOUD MIGRATION STATE (v14): CLOUD_MIGRATION_STATE
 * Persists resumable 10-step SQLite -> Cloud migration checkpoints and verified counts per Google UID.
 */
@Entity(tableName = "cloud_migration_state")
data class CloudMigrationStateEntity(
    @PrimaryKey val uid: String,
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
    val startedAt: String = "",
    val completedAt: String = "",
    val updatedAt: String = ""
)

/**
 * TABEL AUDIT LOG BACKUP, RESTORE & IMPORT/EXPORT (v15): BACKUP_AUDIT_LOGS
 */
@Entity(
    tableName = "backup_audit_logs",
    indices = [
        Index(value = ["action_type"]),
        Index(value = ["timestamp_millis"])
    ]
)
data class BackupAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "action_type")
    val actionType: String, // BACKUP_LOCAL | BACKUP_DRIVE | UPLOAD_DRIVE | DOWNLOAD_DRIVE | RESTORE_FULL | RESTORE_APPEND | IMPORT_DATA | EXPORT_DATA | DELETE_BACKUP | AUTH_DRIVE | SCHEDULE_BACKUP
    @ColumnInfo(name = "status")
    val status: String, // SUKSES | GAGAL | QUEUED | ROLLBACK
    @ColumnInfo(name = "file_name")
    val fileName: String = "",
    @ColumnInfo(name = "checksum")
    val checksum: String = "",
    @ColumnInfo(name = "record_count")
    val recordCount: Int = 0,
    @ColumnInfo(name = "actor_uid")
    val actorUid: String = "",
    @ColumnInfo(name = "actor_email")
    val actorEmail: String = "",
    @ColumnInfo(name = "detail_message")
    val detailMessage: String = "",
    @ColumnInfo(name = "timestamp_display")
    val timestampDisplay: String = "",
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long = 0L
)

data class BackupScheduleConfig(
    val frequency: String = "DAILY", // DAILY | WEEKLY | MONTHLY | MANUAL
    val scheduledHour: Int = 1,
    val scheduledMinute: Int = 0,
    val requireWifiOnly: Boolean = false,
    val requireBatteryNotLow: Boolean = true,
    val maxVersionsToKeep: Int = 30,
    val encryptSensitivePayload: Boolean = true,
    val includePhotos: Boolean = true,
    val autoUploadToDrive: Boolean = true
)

/**
 * TABEL METADATA DOKUMENTASI FOTO GOOGLE DRIVE (v16): DRIVE_PHOTO_METADATA
 * Menyimpan metadata setiap foto yang diunggah dari form Unit Investasi Freezer,
 * Pelanggan (KTP & Dokumentasi), Transaksi Sewa, Bukti Pembayaran, dan Pengembalian Unit.
 * Menggunakan relasi ID pelanggan (customer_id) dan ID unit (unit_id) tanpa mengubah
 * struktur tabel utama customers, freezer_units, dan rental_transactions.
 */
@Entity(
    tableName = "drive_photo_metadata",
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["unit_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["category_code"]),
        Index(value = ["sync_status"]),
        Index(value = ["source_checksum_sha256"])
    ]
)
data class DrivePhotoMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "customer_id")
    val customerId: Long? = null,
    @ColumnInfo(name = "unit_id")
    val unitId: Long? = null,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "category_code")
    val categoryCode: String, // FOTO_UNIT | NOMOR_SERI | KONDISI_UNIT | KTP | DOKUMENTASI_PELANGGAN | BUKTI_PEMBAYARAN | SERAH_TERIMA | DOKUMENTASI_LAINNYA
    @ColumnInfo(name = "drive_root_section")
    val driveRootSection: String = "", // 01. UNIT INVESTASI UNIT | 02. DOKUMENTASI SEWA | 03. BACKUP DOKUMENTASI
    @ColumnInfo(name = "drive_year_folder")
    val driveYearFolder: String = "2026",
    @ColumnInfo(name = "drive_category_folder")
    val driveCategoryFolder: String = "",
    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String = "",
    @ColumnInfo(name = "drive_folder_id")
    val driveFolderId: String = "",
    @ColumnInfo(name = "original_size_bytes")
    val originalSizeBytes: Long = 0L,
    @ColumnInfo(name = "compressed_size_bytes")
    val compressedSizeBytes: Long = 0L,
    @ColumnInfo(name = "upload_date")
    val uploadDate: String = "",
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "MENUNGGU UPLOAD", // MENUNGGU UPLOAD | SEDANG DIUPLOAD | BERHASIL | GAGAL | PERLU SINKRONISASI
    @ColumnInfo(name = "access_url_or_path")
    val accessUrlOrPath: String = "",
    @ColumnInfo(name = "local_compressed_path")
    val localCompressedPath: String = "",
    @ColumnInfo(name = "thumbnail_cache_path")
    val thumbnailCachePath: String = "",
    @ColumnInfo(name = "temp_original_path")
    val tempOriginalPath: String = "",
    @ColumnInfo(name = "source_checksum_sha256")
    val sourceChecksumSha256: String = "",
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String = "",
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class DrivePhotoCompressionConfig(
    val standardQualityPercent: Int = 80, // 75-85%
    val ktpAndDocumentQualityPercent: Int = 88, // 85-90%
    val maxResolutionPx: Int = 1920, // 1920x1920
    val targetMinSizeKb: Int = 200,
    val targetMaxSizeKb: Int = 500,
    val maskNikInFileName: Boolean = true, // Default masked e.g. 3372XXXXXXXXXXXX
    val autoDeleteTempAfterUpload: Boolean = true,
    val autoSyncOnCaptureOrSelect: Boolean = true
)

data class DriveStorageSummaryStats(
    val totalPhotosCount: Int = 0,
    val totalOriginalBytes: Long = 0L,
    val totalCompressedBytes: Long = 0L,
    val savedBytesAfterCompression: Long = 0L,
    val savedPercent: Int = 0,
    val uploadedSuccessCount: Int = 0,
    val waitingUploadCount: Int = 0,
    val uploadingNowCount: Int = 0,
    val failedUploadCount: Int = 0,
    val needsSyncCount: Int = 0
)

/**
 * TABEL CLOUD FILE MAPPING (v17): DRIVE_CLOUD_FILE_MAP
 * Menyimpan pemetaan 1-ke-1 antara data/foto lokal dan Google Drive File ID agar perubahan
 * selalu menggunakan files.update (PATCH) pada file yang sama tanpa duplikasi.
 */
@Entity(
    tableName = "drive_cloud_file_map",
    indices = [
        Index(value = ["record_category", "local_record_id"]),
        Index(value = ["drive_file_id"]),
        Index(value = ["sync_status"]),
        Index(value = ["record_uuid"])
    ]
)
data class DriveCloudFileMapEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_record_key")
    val localRecordKey: String, // Contoh: CUSTOMER:1, UNIT:2, TRANSACTION:3, PAYMENT:4, SETTINGS:COMPANY, PHOTO:KTP:CUSTOMER:1
    @ColumnInfo(name = "record_category")
    val recordCategory: String, // CUSTOMER | UNIT | TRANSACTION | PAYMENT | SETTINGS | PHOTO_KTP | PHOTO_CUSTOMER | PHOTO_FREEZER | PHOTO_PAYMENT | PHOTO_TRANSACTION | PHOTO_AGREEMENT | CLOUD_MANIFEST
    @ColumnInfo(name = "local_record_id")
    val localRecordId: Long = 0L,
    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String = "",
    @ColumnInfo(name = "drive_folder_id")
    val driveFolderId: String = "",
    @ColumnInfo(name = "file_name")
    val fileName: String = "",
    @ColumnInfo(name = "content_hash")
    val contentHash: String = "",
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "last_modified_millis")
    val lastModifiedMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING", // SYNCED | PENDING | UPLOADING | DOWNLOADING | FAILED | DELETE_PENDING | RESETTING | CONFLICT | STALE
    @ColumnInfo(name = "dataset_generation_id")
    val datasetGenerationId: String = "GENERATION_001",
    @ColumnInfo(name = "record_uuid")
    val recordUuid: String = "",
    @ColumnInfo(name = "server_timestamp_millis")
    val serverTimestampMillis: Long = 0L,
    @ColumnInfo(name = "conflict_reason")
    val conflictReason: String = ""
)

/**
 * TABEL LOCAL SYNC QUEUE (v17/v18/v20): DRIVE_LOCAL_SYNC_QUEUE
 * Mencatat setiap perubahan (CREATE, UPDATE, DELETE, REPLACE_PHOTO, RESET_ALL) dengan
 * Operation ID unik, UUID record, Version, Base Version, Server Timestamp, Dataset Generation,
 * dan dukungan idempotency serta exponential backoff.
 */
@Entity(
    tableName = "drive_local_sync_queue",
    indices = [
        Index(value = ["operation_id"], unique = true),
        Index(value = ["local_record_key"]),
        Index(value = ["sync_status"]),
        Index(value = ["timestamp_millis"]),
        Index(value = ["record_uuid"])
    ]
)
data class DriveLocalSyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "record_category")
    val recordCategory: String, // CUSTOMER | UNIT | TRANSACTION | PAYMENT | SETTINGS | PHOTO | SYSTEM
    @ColumnInfo(name = "record_id")
    val recordId: Long = 0L,
    @ColumnInfo(name = "local_record_key")
    val localRecordKey: String,
    @ColumnInfo(name = "operation_type")
    val operationType: String, // CREATE | UPDATE | DELETE | REPLACE_PHOTO | RESET_ALL
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "payload_json")
    val payloadJson: String = "",
    @ColumnInfo(name = "photo_category")
    val photoCategory: String = "",
    @ColumnInfo(name = "new_photo_path")
    val newPhotoPath: String = "",
    @ColumnInfo(name = "previous_drive_file_id")
    val previousDriveFileId: String = "",
    @ColumnInfo(name = "content_hash")
    val contentHash: String = "",
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING_SYNC", // PENDING_SYNC | UPLOADING | SYNCED | FAILED | RESETTING | STALE_OPERATION | CONFLICT
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "next_retry_at_millis")
    val nextRetryAtMillis: Long = 0L,
    @ColumnInfo(name = "last_error")
    val lastError: String = "",
    @ColumnInfo(name = "dataset_generation_id")
    val datasetGenerationId: String = "GENERATION_001",
    @ColumnInfo(name = "record_uuid")
    val recordUuid: String = "",
    @ColumnInfo(name = "base_version")
    val baseVersion: Long = 0L,
    @ColumnInfo(name = "server_timestamp_millis")
    val serverTimestampMillis: Long = 0L,
    @ColumnInfo(name = "changed_fields_json")
    val changedFieldsJson: String = ""
)

/**
 * TABEL DELETION TOMBSTONE (v17/v18/v20): DRIVE_DELETION_TOMBSTONES
 * Mencegah "Old Data Resurrection": mencatat setiap data/foto yang sudah dihapus beserta UUID dan versinya
 * agar tidak pernah dikembalikan secara tidak sengaja dari backup atau cloud lama.
 */
@Entity(
    tableName = "drive_deletion_tombstones",
    indices = [
        Index(value = ["record_category", "record_id"]),
        Index(value = ["synced_to_cloud"]),
        Index(value = ["record_uuid"])
    ]
)
data class DriveDeletionTombstoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_record_key")
    val localRecordKey: String,
    @ColumnInfo(name = "record_category")
    val recordCategory: String,
    @ColumnInfo(name = "record_id")
    val recordId: Long = 0L,
    @ColumnInfo(name = "deleted_version")
    val deletedVersion: Long = 1L,
    @ColumnInfo(name = "deleted_at_millis")
    val deletedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "drive_file_id_at_deletion")
    val driveFileIdAtDeletion: String = "",
    @ColumnInfo(name = "synced_to_cloud")
    val syncedToCloud: Boolean = false,
    @ColumnInfo(name = "dataset_generation_id")
    val datasetGenerationId: String = "GENERATION_001",
    @ColumnInfo(name = "record_uuid")
    val recordUuid: String = "",
    @ColumnInfo(name = "uuid_verified_gone")
    val uuidVerifiedGone: Boolean = false,
    @ColumnInfo(name = "manifest_updated_at_deletion")
    val manifestUpdatedAtDeletion: Boolean = false
)

/**
 * TABEL KARANTINA CLOUD STALE FILES (v21): DRIVE_CLOUD_QUARANTINE
 * Menandai file Google Drive lama, duplikat, tanpa referensi DB aktif, atau berada di tombstone
 * ke dalam status QUARANTINE tanpa pernah mengimpor ulang ke SQLite atau menghapus file yang belum terverifikasi.
 */
@Entity(
    tableName = "drive_cloud_quarantine",
    indices = [
        Index(value = ["drive_file_id"], unique = true),
        Index(value = ["classification"]),
        Index(value = ["status"]),
        Index(value = ["record_uuid"])
    ]
)
data class DriveCloudQuarantineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String,
    @ColumnInfo(name = "drive_parent_folder_id")
    val driveParentFolderId: String = "",
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "classification")
    val classification: String, // OLD_CUSTOMER_FILE | OLD_UNIT_FILE | DUPLICATE_FILE | NO_DB_REFERENCE | PREVIOUS_GENERATION | DELETED_IN_LOCAL_TOMBSTONE
    @ColumnInfo(name = "local_record_key")
    val localRecordKey: String = "",
    @ColumnInfo(name = "record_uuid")
    val recordUuid: String = "",
    @ColumnInfo(name = "dataset_generation_id")
    val datasetGenerationId: String = "",
    @ColumnInfo(name = "ownership_verified")
    val ownershipVerified: Boolean = false,
    @ColumnInfo(name = "quarantine_reason")
    val quarantineReason: String = "",
    @ColumnInfo(name = "status")
    val status: String = "QUARANTINED", // QUARANTINED | PURGED | IGNORED_UNCERTAIN
    @ColumnInfo(name = "quarantined_at_millis")
    val quarantinedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "resolved_at_millis")
    val resolvedAtMillis: Long = 0L
)

/**
 * TABEL LOG TEKNIS SINKRONISASI & RESET (v18): SYNC_TECHNICAL_LOGS
 * Bagian 11: Mencatat log teknis terstruktur (waktu operasi, tipe, recordId, driveFileId,
 * datasetGenerationId, localStatus, cloudStatus, errorCode, errorMessage, retryCount).
 */
@Entity(
    tableName = "sync_technical_logs",
    indices = [
        Index(value = ["operation_time_millis"]),
        Index(value = ["operation_type"]),
        Index(value = ["dataset_generation_id"])
    ]
)
data class SyncTechnicalLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "operation_time_millis")
    val operationTimeMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "operation_time_display")
    val operationTimeDisplay: String = "",
    @ColumnInfo(name = "operation_type")
    val operationType: String = "",
    @ColumnInfo(name = "record_id")
    val recordId: String = "",
    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String = "",
    @ColumnInfo(name = "dataset_generation_id")
    val datasetGenerationId: String = "GENERATION_001",
    @ColumnInfo(name = "local_status")
    val localStatus: String = "",
    @ColumnInfo(name = "cloud_status")
    val cloudStatus: String = "",
    @ColumnInfo(name = "error_code")
    val errorCode: String = "",
    @ColumnInfo(name = "error_message")
    val errorMessage: String = "",
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0
)

/**
 * Laporan Audit Konsistensi Data (Bagian 10): LOCAL DATABASE VS GOOGLE DRIVE.
 * Tidak pernah menggabungkan atau merestore perbedaan secara otomatis.
 */
data class DataConsistencyAuditReport(
    val localCustomersCount: Int = 0,
    val localUnitsCount: Int = 0,
    val localTransactionsCount: Int = 0,
    val localPaymentsCount: Int = 0,
    val localPhotosCount: Int = 0,
    val cloudCustomersCount: Int = 0,
    val cloudUnitsCount: Int = 0,
    val cloudTransactionsCount: Int = 0,
    val cloudPaymentsCount: Int = 0,
    val cloudPhotosCount: Int = 0,
    val missingInCloud: List<String> = emptyList(),
    val duplicateCloudFiles: List<String> = emptyList(),
    val staleRecords: List<String> = emptyList(),
    val uuidMismatches: List<String> = emptyList(),
    val versionMismatches: List<String> = emptyList(),
    val conflictRecords: List<String> = emptyList(),
    val tombstoneViolations: List<String> = emptyList(),
    val pendingOperationsCount: Int = 0,
    val failedOperationsCount: Int = 0,
    val localDatasetGeneration: String = "GENERATION_001",
    val cloudDatasetGeneration: String = "",
    val cloudManifestStatus: String = "",
    val serverTimeMillis: Long = 0L,
    val quarantinedFilesCount: Int = 0,
    val quarantinedItemsSummary: List<String> = emptyList(),
    val isConsistent: Boolean = true,
    val auditedAtDisplay: String = "",
    val errorMessage: String = "",
    val duplicateUuids: List<String> = emptyList(),
    val checksumMismatches: List<String> = emptyList(),
    val generationMismatches: List<String> = emptyList()
) {
    val missingCount: Int get() = missingInCloud.size
    val duplicateCount: Int get() = (duplicateCloudFiles + duplicateUuids).distinct().size
    val staleCount: Int get() = staleRecords.size
    val conflictCount: Int get() = conflictRecords.size
    val generationMismatchCount: Int get() = generationMismatches.size
    val revisionMismatchCount: Int get() = versionMismatches.size
    val checksumMismatchCount: Int get() = checksumMismatches.size
    val quarantineCount: Int get() = quarantinedFilesCount
}

data class DriveManifestRecordEntry(
    val localRecordKey: String,
    val recordCategory: String,
    val recordId: Long,
    val recordUuid: String,
    val revision: Long,
    val driveFileId: String = "",
    val contentHash: String = "",
    val updatedAtMillis: Long = 0L,
    val fileName: String = ""
)

data class DriveManifestTombstoneEntry(
    val localRecordKey: String,
    val recordCategory: String,
    val recordId: Long,
    val recordUuid: String,
    val deletedVersion: Long,
    val deletedAtMillis: Long,
    val uuidVerifiedGone: Boolean = false
)

/**
 * Model Cloud Manifest (`manifest.json`) sesuai Bagian 4, 5, 6 & C.
 * Menjadi satu-satunya sumber referensi dataset aktif di Google Drive.
 */
data class DriveCloudManifestState(
    val applicationId: String = "com.aistudio.sewainajafreezer.soc",
    val userId: String = "owner_default",
    val datasetId: String = "dataset_initial",
    val datasetGeneration: String = "GENERATION_001",
    val databaseVersion: Int = 25,
    val lastSyncTimestamp: Long = 0L,
    val lastResetTimestamp: Long = 0L,
    val resetCompleted: Boolean = false,
    val totalCustomers: Int = 0,
    val totalUnits: Int = 0,
    val totalTransactions: Int = 0,
    val totalPayments: Int = 0,
    val totalPhotos: Int = 0,
    val syncVersion: Long = 1L,
    val datasetStatus: String = "ACTIVE", // ACTIVE | EMPTY_AFTER_RESET
    val activeRecords: List<DriveManifestRecordEntry> = emptyList(),
    val tombstones: List<DriveManifestTombstoneEntry> = emptyList()
)

/**
 * Model Status Dashboard Sinkronisasi sesuai Bagian 3, 6 & 11.
 * Status mendukung: SYNCED, PENDING, UPLOADING, DOWNLOADING, FAILED, RESETTING, CONFLICT
 */
data class DriveMirrorDashboardState(
    val isDriveConnected: Boolean = false,
    val connectedEmail: String = "",
    val overallStatus: String = "SYNCED", // SYNCED | PENDING | UPLOADING | DOWNLOADING | FAILED | FAILED_AUTH | WAITING_AUTH | WAITING_NETWORK | PARTIAL_SUCCESS | RESETTING | CONFLICT
    val syncStatusCode: String = "SUCCESS", // SUCCESS | PARTIAL_SUCCESS | FAILED | RUNNING | WAITING_AUTH | WAITING_NETWORK
    val totalLocalDataCount: Int = 0,
    val totalCloudDataCount: Int = 0,
    val totalSyncedDataCount: Int = 0,
    val totalSyncedPhotosCount: Int = 0,
    val pendingSyncCount: Int = 0,
    val failedSyncCount: Int = 0,
    val failedAuthCount: Int = 0,
    val staleDataCount: Int = 0,
    val conflictDataCount: Int = 0,
    val quarantinedFilesCount: Int = 0,
    val requiresReAuth: Boolean = false,
    val lastSyncTimeDisplay: String = "Belum pernah disinkronkan",
    val lastSyncTimestampMillis: Long = 0L,
    val lastServerTimeMillis: Long = 0L,
    val serverTimeDisplay: String = "",
    val uploadProgressPercent: Int = 0,
    val downloadProgressPercent: Int = 0,
    val activeOperationLabel: String = "",
    val lastErrorMessage: String = "",
    val datasetGeneration: String = "GENERATION_001",
    val isResetLocked: Boolean = false,
    val manifestState: DriveCloudManifestState = DriveCloudManifestState()
)

/**
 * TABEL DRAFT OTOMATIS (v23): FORM_DRAFTS
 * Menyimpan draft pengisian form secara terpisah dari tabel transaksi/unit/pelanggan aktif.
 * Draft TIDAK PERNAH masuk ke antrean Google Drive Sync sebagai data final sebelum disimpan.
 */
@Entity(
    tableName = "form_drafts",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["form_type", "target_entity_id"]),
        Index(value = ["owner_account"]),
        Index(value = ["updated_at"])
    ]
)
data class FormDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "form_type") val formType: String, // UNIT | CUSTOMER | TRANSACTION | RETURN
    @ColumnInfo(name = "target_entity_id") val targetEntityId: Long = 0L, // 0 = form baru, >0 = edit record
    @ColumnInfo(name = "owner_account") val ownerAccount: String = "",
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "subtitle") val subtitle: String = "",
    @ColumnInfo(name = "payload_json") val payloadJson: String = "{}",
    @ColumnInfo(name = "version") val version: Long = 1L,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at_display") val updatedAtDisplay: String = ""
)

/**
 * TABEL DAFTAR HARGA SEWA PER PERIODE PER UNIT (v23): UNIT_RENTAL_PRICES
 * Setiap unit freezer dapat memiliki harga sewa terpisah per periode (3, 7, 14, 30, 60, 90 hari).
 * Periode yang tidak diisi tidak disimpan / bernilai 0 dan tidak tampil pada pilihan transaksi.
 */
@Entity(
    tableName = "unit_rental_prices",
    foreignKeys = [
        ForeignKey(
            entity = FreezerUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["unit_id"]),
        Index(value = ["unit_id", "period_days"], unique = true)
    ]
)
data class UnitRentalPriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "unit_id") val unitId: Long,
    @ColumnInfo(name = "period_days") val periodDays: Int, // 3, 7, 14, 30, 60, 90
    @ColumnInfo(name = "price") val price: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        val SUPPORTED_PERIOD_DAYS = listOf(3, 7, 14, 30, 60, 90)
    }
}

/**
 * TABEL AUDIT LOG KOREKSI IDENTITAS PETUGAS TRANSAKSI (v23): TRANSACTION_OFFICER_AUDIT_LOGS
 * Mencatat setiap perubahan identitas atau tanda tangan petugas pada transaksi yang sudah tersimpan.
 */
@Entity(
    tableName = "transaction_officer_audit_logs",
    foreignKeys = [
        ForeignKey(
            entity = RentalTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transaction_id"]),
        Index(value = ["corrected_at_millis"])
    ]
)
data class TransactionOfficerAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "previous_officer_name") val previousOfficerName: String = "",
    @ColumnInfo(name = "new_officer_name") val newOfficerName: String = "",
    @ColumnInfo(name = "previous_signature_uri") val previousSignatureUri: String = "",
    @ColumnInfo(name = "new_signature_uri") val newSignatureUri: String = "",
    @ColumnInfo(name = "corrected_by") val correctedBy: String = "",
    @ColumnInfo(name = "correction_reason") val correctionReason: String = "",
    @ColumnInfo(name = "corrected_at_millis") val correctedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "corrected_at_display") val correctedAtDisplay: String = ""
)



