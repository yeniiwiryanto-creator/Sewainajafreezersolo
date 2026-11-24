package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        FreezerUnitEntity::class,
        CustomerEntity::class,
        RentalTransactionEntity::class,
        PaymentProofEntity::class,
        PaymentMethodEntity::class,
        RentalExtensionHistoryEntity::class,
        FreezerReturnEntity::class,
        SyncLogEntity::class,
        CustomerPhotoEntity::class,
        CustomerDocumentEntity::class,
        TransactionDocumentEntity::class,
        CustomerLocationEntity::class,
        LocalBackupEntity::class,
        AppPermissionEntity::class,
        TransactionUnitItemEntity::class,
        TransactionSignatureEntity::class,
        UnitPaymentAllocationEntity::class,
        CustomerTimelineEventEntity::class,
        CloudSyncQueueEntity::class,
        CloudPhotoJobEntity::class,
        CloudMigrationStateEntity::class,
        BackupAuditLogEntity::class,
        DrivePhotoMetadataEntity::class,
        DriveCloudFileMapEntity::class,
        DriveLocalSyncQueueEntity::class,
        DriveDeletionTombstoneEntity::class,
        SyncTechnicalLogEntity::class,
        FreezerUnitMaintenanceEntity::class,
        DriveCloudQuarantineEntity::class,
        FormDraftEntity::class,
        UnitRentalPriceEntity::class,
        TransactionOfficerAuditLogEntity::class
    ],
    version = 25,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun freezerDao(): FreezerDao
    abstract fun customerDao(): CustomerDao
    abstract fun freezerUnitDao(): FreezerUnitDao
    abstract fun rentalTransactionDao(): RentalTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Non-destructive migration for CustomerEntity KTP OCR fields
                db.execSQL("ALTER TABLE customers ADD COLUMN tempatLahir TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN tanggalLahir TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN rtRw TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN kelurahanDesa TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN kecamatan TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN agama TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN statusPerkawinan TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN pekerjaan TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN kewarganegaraan TEXT NOT NULL DEFAULT 'WNI'")
                db.execSQL("ALTER TABLE customers ADD COLUMN persetujuanDataKtpLokasi INTEGER NOT NULL DEFAULT 1")

                // Non-destructive migration for RentalTransactionEntity Usage Location & Photo Documentation metadata
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN fotoFreezerDiLokasiUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN waktuFotoKtp TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN ketFotoKtp TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN waktuFotoUnitSebelum TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN ketFotoUnitSebelum TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN waktuFotoSerahTerima TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN ketFotoSerahTerima TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN waktuFotoFreezerDiLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN ketFotoFreezerDiLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN waktuFotoDepanLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN ketFotoDepanLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN namaLokasiUsaha TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN namaPenanggungJawab TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN nomorWhatsappLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN provinsiLokasi TEXT NOT NULL DEFAULT 'Jawa Tengah'")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN kabupatenKotaLokasi TEXT NOT NULL DEFAULT 'Kota Surakarta'")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN kecamatanLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN kelurahanDesaLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN kodePosLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN akurasiGpsMeter REAL")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN linkGoogleMaps TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN catatanLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN persetujuanPelangganTrans INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN isiPerjanjianSewaSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `freezer_returns` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nomorDokumenPengembalian` TEXT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `nomorInvoiceSnapshot` TEXT NOT NULL,
                        `customerId` INTEGER NOT NULL,
                        `namaPelangganSnapshot` TEXT NOT NULL,
                        `nikPelangganSnapshot` TEXT NOT NULL DEFAULT '',
                        `nomorWaPelangganSnapshot` TEXT NOT NULL DEFAULT '',
                        `alamatKtpSnapshot` TEXT NOT NULL DEFAULT '',
                        `unitId` INTEGER NOT NULL,
                        `kodeUnitSnapshot` TEXT NOT NULL,
                        `namaUnitSnapshot` TEXT NOT NULL,
                        `merekTipeSnapshot` TEXT NOT NULL DEFAULT '',
                        `tanggalMulaiSewaSnapshot` TEXT NOT NULL DEFAULT '',
                        `hargaSewaSnapshot` INTEGER NOT NULL DEFAULT 0,
                        `tanggalJatuhTempoSnapshot` TEXT NOT NULL DEFAULT '',
                        `alamatPenggunaanSnapshot` TEXT NOT NULL DEFAULT '',
                        `latitudeTerakhir` REAL,
                        `longitudeTerakhir` REAL,
                        `tanggalPengembalian` TEXT NOT NULL,
                        `jamPengembalian` TEXT NOT NULL,
                        `namaPetugasPenerima` TEXT NOT NULL,
                        `kondisiUnitSaatKembali` TEXT NOT NULL,
                        `catatanKondisiUnit` TEXT NOT NULL DEFAULT '',
                        `statusUnitSetelahKembali` TEXT NOT NULL DEFAULT 'Tersedia',
                        `fotoKondisiKembaliUri` TEXT NOT NULL DEFAULT '',
                        `fotoBagianLuarUri` TEXT NOT NULL DEFAULT '',
                        `fotoBagianDalamUri` TEXT NOT NULL DEFAULT '',
                        `fotoTambahanUri` TEXT NOT NULL DEFAULT '',
                        `tandaTanganPelangganUri` TEXT NOT NULL DEFAULT '',
                        `tandaTanganPetugasUri` TEXT NOT NULL DEFAULT '',
                        `pdfBuktiPengembalianPath` TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(`transactionId`) REFERENCES `rental_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`unitId`) REFERENCES `freezer_units`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_freezer_returns_nomorDokumenPengembalian` ON `freezer_returns` (`nomorDokumenPengembalian`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_freezer_returns_transactionId` ON `freezer_returns` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_freezer_returns_customerId` ON `freezer_returns` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_freezer_returns_unitId` ON `freezer_returns` (`unitId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN kodePelanggan TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN lokasiPenggunaanDefault TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN latitudeDefault REAL")
                db.execSQL("ALTER TABLE customers ADD COLUMN longitudeDefault REAL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_kodePelanggan` ON `customers` (`kodePelanggan`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN jenisProgram TEXT NOT NULL DEFAULT 'Sewa Reguler'")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN totalPeriodeHakMilik INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN pembayaranKeHakMilik INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN statusProgramHakMilik TEXT NOT NULL DEFAULT 'Aktif'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add separated OCR & default usage location columns to customers
                db.execSQL("ALTER TABLE customers ADD COLUMN rt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN rw TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN kabupatenKota TEXT NOT NULL DEFAULT 'Kota Surakarta'")
                db.execSQL("ALTER TABLE customers ADD COLUMN berlakuHingga TEXT NOT NULL DEFAULT 'SEUMUR HIDUP'")
                db.execSQL("ALTER TABLE customers ADD COLUMN namaLokasiDefault TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN rtRwLokasiDefault TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN kelurahanLokasiDefault TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN kecamatanLokasiDefault TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN kabupatenKotaLokasiDefault TEXT NOT NULL DEFAULT 'Kota Surakarta'")
                db.execSQL("ALTER TABLE customers ADD COLUMN linkGoogleMapsDefault TEXT NOT NULL DEFAULT ''")

                // 2. Add location RT/RW & Owner signature snapshot columns to rental_transactions
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN rtRwLokasi TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN tandaTanganOwnerSnapshotUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN ukuranTandaTanganOwnerSnapshot REAL NOT NULL DEFAULT 42.0")
                db.execSQL("ALTER TABLE rental_transactions ADD COLUMN posisiTandaTanganOwnerSnapshot TEXT NOT NULL DEFAULT 'RIGHT'")

                // 3. Add Owner signature snapshot columns to freezer_returns
                db.execSQL("ALTER TABLE freezer_returns ADD COLUMN tandaTanganOwnerSnapshotUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE freezer_returns ADD COLUMN ukuranTandaTanganOwnerSnapshot REAL NOT NULL DEFAULT 42.0")
                db.execSQL("ALTER TABLE freezer_returns ADD COLUMN posisiTandaTanganOwnerSnapshot TEXT NOT NULL DEFAULT 'RIGHT'")

                // 4. Create payment_proofs table (1. FITUR BUKTI PEMBAYARAN)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_proofs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `tanggalPembayaran` TEXT NOT NULL,
                        `nominalPembayaran` INTEGER NOT NULL,
                        `metodePembayaran` TEXT NOT NULL,
                        `namaPengirim` TEXT NOT NULL DEFAULT '',
                        `catatanPembayaran` TEXT NOT NULL DEFAULT '',
                        `fotoBuktiUri` TEXT NOT NULL DEFAULT '',
                        `waktuUpload` TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(`transactionId`) REFERENCES `rental_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_proofs_transactionId` ON `payment_proofs` (`transactionId`)")

                // 5. Create payment_methods table (2. SETTING -> METODE PEMBAYARAN)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_methods` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `namaMetode` TEXT NOT NULL,
                        `kategoriMetode` TEXT NOT NULL DEFAULT 'TRANSFER_BANK',
                        `namaBankAtauPenyedia` TEXT NOT NULL DEFAULT '',
                        `nomorRekening` TEXT NOT NULL DEFAULT '',
                        `namaPemilikRekening` TEXT NOT NULL DEFAULT '',
                        `fotoQrisUri` TEXT NOT NULL DEFAULT '',
                        `logoBankUri` TEXT NOT NULL DEFAULT '',
                        `keteranganPembayaran` TEXT NOT NULL DEFAULT '',
                        `isAktif` INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_methods_namaMetode` ON `payment_methods` (`namaMetode`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SISTEM PENYIMPANAN FOTO OFFLINE PER PELANGGAN DI ANDROID
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_photos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customerId` INTEGER NOT NULL,
                        `transactionId` INTEGER,
                        `kategori` TEXT NOT NULL,
                        `namaDokumentasi` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `relativeFolder` TEXT NOT NULL DEFAULT '',
                        `fileName` TEXT NOT NULL DEFAULT '',
                        `tanggalWaktuIso` TEXT NOT NULL DEFAULT '',
                        `latitude` REAL,
                        `longitude` REAL,
                        `catatan` TEXT NOT NULL DEFAULT '',
                        `sizeBytes` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_photos_customerId` ON `customer_photos` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_photos_transactionId` ON `customer_photos` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_photos_kategori` ON `customer_photos` (`kategori`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Section I: Penyimpanan Foto Hasil Preprocessing, Raw OCR, Hasil Parsing, Confidence, & Verifikasi Admin
                db.execSQL("ALTER TABLE customers ADD COLUMN fotoKtpPreprocessedUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN rawOcrText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN parsedOcrJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN ocrFieldConfidencesJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN statusVerifikasiAdminOcr TEXT NOT NULL DEFAULT 'TERVERIFIKASI_ADMIN'")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN waktuPemindaianOcr TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_documents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customer_id` INTEGER NOT NULL,
                        `document_type` TEXT NOT NULL,
                        `file_name` TEXT NOT NULL,
                        `relative_path` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `file_size` INTEGER NOT NULL,
                        `checksum` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_documents_customer_id` ON `customer_documents` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_documents_document_type` ON `customer_documents` (`document_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_documents_checksum` ON `customer_documents` (`checksum`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_documents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transaction_id` INTEGER NOT NULL,
                        `customer_id` INTEGER NOT NULL,
                        `document_type` TEXT NOT NULL,
                        `file_name` TEXT NOT NULL,
                        `relative_path` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `file_size` INTEGER NOT NULL,
                        `checksum` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_documents_transaction_id` ON `transaction_documents` (`transaction_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_documents_customer_id` ON `transaction_documents` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_documents_document_type` ON `transaction_documents` (`document_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_documents_checksum` ON `transaction_documents` (`checksum`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_location` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customer_id` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `address` TEXT NOT NULL,
                        `location_source` TEXT NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_location_customer_id` ON `customer_location` (`customer_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_backup` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `backup_name` TEXT NOT NULL,
                        `backup_path` TEXT NOT NULL,
                        `backup_size` INTEGER NOT NULL,
                        `backup_type` TEXT NOT NULL,
                        `backup_status` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_backup_backup_type` ON `local_backup` (`backup_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_backup_created_at` ON `local_backup` (`created_at`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_permission` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `permission_name` TEXT NOT NULL,
                        `permission_status` TEXT NOT NULL,
                        `last_checked` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_app_permission_permission_name` ON `app_permission` (`permission_name`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                        `jenis_unit_snapshot` TEXT NOT NULL,
                        `merek_snapshot` TEXT NOT NULL,
                        `kapasitas_liter_snapshot` INTEGER NOT NULL,
                        `jenis_program` TEXT NOT NULL,
                        `total_periode_hak_milik` INTEGER NOT NULL,
                        `pembayaran_ke_hak_milik` INTEGER NOT NULL,
                        `status_program_hak_milik` TEXT NOT NULL,
                        `harga_sewa` INTEGER NOT NULL,
                        `durasi_hari` INTEGER NOT NULL,
                        `tanggal_mulai_sewa` TEXT NOT NULL,
                        `tanggal_akhir_sewa` TEXT NOT NULL,
                        `tanggal_jatuh_tempo` TEXT NOT NULL,
                        `nominal_pembayaran` INTEGER NOT NULL,
                        `status_pembayaran` TEXT NOT NULL,
                        `status_penyewaan` TEXT NOT NULL,
                        `catatan_unit` TEXT NOT NULL,
                        `foto_kondisi_depan_uri` TEXT NOT NULL,
                        `foto_kondisi_samping_uri` TEXT NOT NULL,
                        `foto_kondisi_dalam_uri` TEXT NOT NULL,
                        `foto_kondisi_tambahan_uri` TEXT NOT NULL,
                        `foto_serah_terima_pelanggan_uri` TEXT NOT NULL,
                        `foto_serah_terima_lokasi_uri` TEXT NOT NULL,
                        `foto_bukti_penerimaan_uri` TEXT NOT NULL,
                        `foto_serah_terima_tambahan_uri` TEXT NOT NULL,
                        `storage_folder_path` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        FOREIGN KEY(`transaction_id`) REFERENCES `rental_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`unit_id`) REFERENCES `freezer_units`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_unit_items_transaction_id` ON `transaction_unit_items` (`transaction_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_unit_items_customer_id` ON `transaction_unit_items` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_unit_items_unit_id` ON `transaction_unit_items` (`unit_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_unit_items_tanggal_jatuh_tempo` ON `transaction_unit_items` (`tanggal_jatuh_tempo`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_signatures` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transaction_id` INTEGER NOT NULL,
                        `customer_id` INTEGER NOT NULL,
                        `signer_role` TEXT NOT NULL,
                        `signer_name` TEXT NOT NULL,
                        `signature_method` TEXT NOT NULL,
                        `file_path` TEXT NOT NULL,
                        `relative_path` TEXT NOT NULL,
                        `has_transparent_background` INTEGER NOT NULL,
                        `signed_date` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        FOREIGN KEY(`transaction_id`) REFERENCES `rental_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_signatures_transaction_id` ON `transaction_signatures` (`transaction_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_signatures_customer_id` ON `transaction_signatures` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_signatures_signer_role` ON `transaction_signatures` (`signer_role`)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Non-destructive column additions on existing tables
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `fotoPelangganUri` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `statusPelanggan` TEXT NOT NULL DEFAULT 'Pelanggan Aktif'")

                db.execSQL("ALTER TABLE `freezer_units` ADD COLUMN `kondisiUnit` TEXT NOT NULL DEFAULT 'Baik & Siap Pakai'")
                db.execSQL("ALTER TABLE `freezer_units` ADD COLUMN `tanggalPengiriman` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `freezer_units` ADD COLUMN `lokasiGpsPemasangan` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `freezer_units` ADD COLUMN `latitudePemasangan` REAL")
                db.execSQL("ALTER TABLE `freezer_units` ADD COLUMN `longitudePemasangan` REAL")

                db.execSQL("ALTER TABLE `transaction_unit_items` ADD COLUMN `periode_tagihan` TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE `payment_proofs` ADD COLUMN `unitItemId` INTEGER")
                db.execSQL("ALTER TABLE `payment_proofs` ADD COLUMN `alokasiUnitJson` TEXT NOT NULL DEFAULT ''")

                // New table: unit_payment_allocations
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `unit_payment_allocations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `payment_proof_id` INTEGER NOT NULL,
                        `transaction_id` INTEGER NOT NULL,
                        `unit_item_id` INTEGER NOT NULL,
                        `customer_id` INTEGER NOT NULL,
                        `unit_id` INTEGER NOT NULL,
                        `nama_unit_snapshot` TEXT NOT NULL,
                        `kode_unit_snapshot` TEXT NOT NULL,
                        `allocated_amount` INTEGER NOT NULL,
                        `payment_date` TEXT NOT NULL,
                        `periode_tagihan` TEXT NOT NULL,
                        `metode_pembayaran` TEXT NOT NULL,
                        `catatan` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        FOREIGN KEY(`transaction_id`) REFERENCES `rental_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`unit_item_id`) REFERENCES `transaction_unit_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_unit_payment_allocations_payment_proof_id` ON `unit_payment_allocations` (`payment_proof_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_unit_payment_allocations_transaction_id` ON `unit_payment_allocations` (`transaction_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_unit_payment_allocations_unit_item_id` ON `unit_payment_allocations` (`unit_item_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_unit_payment_allocations_customer_id` ON `unit_payment_allocations` (`customer_id`)")

                // New table: customer_timeline_events
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_timeline_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customer_id` INTEGER NOT NULL,
                        `transaction_id` INTEGER,
                        `unit_id` INTEGER,
                        `event_type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `event_timestamp` TEXT NOT NULL,
                        `event_date_iso` TEXT NOT NULL,
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_timeline_events_customer_id` ON `customer_timeline_events` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_timeline_events_transaction_id` ON `customer_timeline_events` (`transaction_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_timeline_events_event_type` ON `customer_timeline_events` (`event_type`)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Performance indices on existing tables for large-scale datasets
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_freezer_units_statusUnit` ON `freezer_units` (`statusUnit`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rental_transactions_statusPembayaran` ON `rental_transactions` (`statusPembayaran`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rental_transactions_statusPenyewaan` ON `rental_transactions` (`statusPenyewaan`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rental_transactions_tanggalJatuhTempo` ON `rental_transactions` (`tanggalJatuhTempo`)")

                // Offline-first Cloud Sync Queue
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_sync_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uid` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityLocalId` INTEGER NOT NULL,
                        `cloudDocId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastError` TEXT NOT NULL,
                        `clientUpdatedAtMillis` INTEGER NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_sync_queue_status` ON `cloud_sync_queue` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_sync_queue_entityType` ON `cloud_sync_queue` (`entityType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_sync_queue_cloudDocId` ON `cloud_sync_queue` (`cloudDocId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cloud_sync_queue_uid_entityType_cloudDocId` ON `cloud_sync_queue` (`uid`, `entityType`, `cloudDocId`)")

                // Cloud Storage Photo Upload Jobs
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_photo_jobs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uid` TEXT NOT NULL,
                        `ownerType` TEXT NOT NULL,
                        `ownerLocalId` INTEGER NOT NULL,
                        `ownerCloudId` TEXT NOT NULL,
                        `photoSlot` TEXT NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `thumbnailPath` TEXT NOT NULL,
                        `storagePath` TEXT NOT NULL,
                        `cloudDownloadUrl` TEXT NOT NULL,
                        `thumbnailDownloadUrl` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `bytesTotal` INTEGER NOT NULL,
                        `bytesUploaded` INTEGER NOT NULL,
                        `progressPercent` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `errorMessage` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_photo_jobs_status` ON `cloud_photo_jobs` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_photo_jobs_ownerType_ownerLocalId` ON `cloud_photo_jobs` (`ownerType`, `ownerLocalId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cloud_photo_jobs_uid_storagePath` ON `cloud_photo_jobs` (`uid`, `storagePath`)")

                // Resumable SQLite -> Cloud Migration State
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_migration_state` (
                        `uid` TEXT NOT NULL PRIMARY KEY,
                        `currentStep` INTEGER NOT NULL,
                        `stepLabel` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `processedItems` INTEGER NOT NULL,
                        `totalItems` INTEGER NOT NULL,
                        `localCustomersCount` INTEGER NOT NULL,
                        `cloudCustomersCount` INTEGER NOT NULL,
                        `localUnitsCount` INTEGER NOT NULL,
                        `cloudUnitsCount` INTEGER NOT NULL,
                        `localTxCount` INTEGER NOT NULL,
                        `cloudTxCount` INTEGER NOT NULL,
                        `localPhotosCount` INTEGER NOT NULL,
                        `cloudPhotosCount` INTEGER NOT NULL,
                        `lastError` TEXT NOT NULL,
                        `startedAt` TEXT NOT NULL,
                        `completedAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `backup_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `location_type` TEXT NOT NULL DEFAULT 'LOKAL'")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `drive_file_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `verification_status` TEXT NOT NULL DEFAULT 'TERVERIFIKASI'")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `checksum_sha256` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `is_encrypted` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `customer_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `unit_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `transaction_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `payment_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `local_backup` ADD COLUMN `photo_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_backup_backup_id` ON `local_backup` (`backup_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `backup_audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `action_type` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `file_name` TEXT NOT NULL,
                        `checksum` TEXT NOT NULL,
                        `record_count` INTEGER NOT NULL,
                        `actor_uid` TEXT NOT NULL,
                        `actor_email` TEXT NOT NULL,
                        `detail_message` TEXT NOT NULL,
                        `timestamp_display` TEXT NOT NULL,
                        `timestamp_millis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_audit_logs_action_type` ON `backup_audit_logs` (`action_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_audit_logs_timestamp_millis` ON `backup_audit_logs` (`timestamp_millis`)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_photo_metadata` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customer_id` INTEGER,
                        `unit_id` INTEGER,
                        `transaction_id` INTEGER,
                        `file_name` TEXT NOT NULL,
                        `category_code` TEXT NOT NULL,
                        `drive_root_section` TEXT NOT NULL,
                        `drive_year_folder` TEXT NOT NULL,
                        `drive_category_folder` TEXT NOT NULL,
                        `drive_file_id` TEXT NOT NULL,
                        `drive_folder_id` TEXT NOT NULL,
                        `original_size_bytes` INTEGER NOT NULL,
                        `compressed_size_bytes` INTEGER NOT NULL,
                        `upload_date` TEXT NOT NULL,
                        `sync_status` TEXT NOT NULL,
                        `access_url_or_path` TEXT NOT NULL,
                        `local_compressed_path` TEXT NOT NULL,
                        `thumbnail_cache_path` TEXT NOT NULL,
                        `temp_original_path` TEXT NOT NULL,
                        `source_checksum_sha256` TEXT NOT NULL,
                        `retry_count` INTEGER NOT NULL,
                        `last_error` TEXT NOT NULL,
                        `created_at_millis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_metadata_customer_id` ON `drive_photo_metadata` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_metadata_unit_id` ON `drive_photo_metadata` (`unit_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_metadata_transaction_id` ON `drive_photo_metadata` (`transaction_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_metadata_category_code` ON `drive_photo_metadata` (`category_code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_metadata_sync_status` ON `drive_photo_metadata` (`sync_status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_metadata_source_checksum_sha256` ON `drive_photo_metadata` (`source_checksum_sha256`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_cloud_file_map` (
                        `local_record_key` TEXT NOT NULL PRIMARY KEY,
                        `record_category` TEXT NOT NULL,
                        `local_record_id` INTEGER NOT NULL,
                        `drive_file_id` TEXT NOT NULL,
                        `drive_folder_id` TEXT NOT NULL,
                        `file_name` TEXT NOT NULL,
                        `content_hash` TEXT NOT NULL,
                        `version` INTEGER NOT NULL,
                        `last_modified_millis` INTEGER NOT NULL,
                        `sync_status` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_cloud_file_map_record_category_local_record_id` ON `drive_cloud_file_map` (`record_category`, `local_record_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_cloud_file_map_drive_file_id` ON `drive_cloud_file_map` (`drive_file_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_cloud_file_map_sync_status` ON `drive_cloud_file_map` (`sync_status`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_local_sync_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operation_id` TEXT NOT NULL,
                        `record_category` TEXT NOT NULL,
                        `record_id` INTEGER NOT NULL,
                        `local_record_key` TEXT NOT NULL,
                        `operation_type` TEXT NOT NULL,
                        `version` INTEGER NOT NULL,
                        `timestamp_millis` INTEGER NOT NULL,
                        `payload_json` TEXT NOT NULL,
                        `photo_category` TEXT NOT NULL,
                        `new_photo_path` TEXT NOT NULL,
                        `previous_drive_file_id` TEXT NOT NULL,
                        `content_hash` TEXT NOT NULL,
                        `sync_status` TEXT NOT NULL,
                        `retry_count` INTEGER NOT NULL,
                        `next_retry_at_millis` INTEGER NOT NULL,
                        `last_error` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_local_sync_queue_operation_id` ON `drive_local_sync_queue` (`operation_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_local_sync_queue_local_record_key` ON `drive_local_sync_queue` (`local_record_key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_local_sync_queue_sync_status` ON `drive_local_sync_queue` (`sync_status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_local_sync_queue_timestamp_millis` ON `drive_local_sync_queue` (`timestamp_millis`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_deletion_tombstones` (
                        `local_record_key` TEXT NOT NULL PRIMARY KEY,
                        `record_category` TEXT NOT NULL,
                        `record_id` INTEGER NOT NULL,
                        `deleted_version` INTEGER NOT NULL,
                        `deleted_at_millis` INTEGER NOT NULL,
                        `drive_file_id_at_deletion` TEXT NOT NULL,
                        `synced_to_cloud` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_deletion_tombstones_record_category_record_id` ON `drive_deletion_tombstones` (`record_category`, `record_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_deletion_tombstones_synced_to_cloud` ON `drive_deletion_tombstones` (`synced_to_cloud`)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `drive_cloud_file_map` ADD COLUMN `dataset_generation_id` TEXT NOT NULL DEFAULT 'GENERATION_001'")
                db.execSQL("ALTER TABLE `drive_local_sync_queue` ADD COLUMN `dataset_generation_id` TEXT NOT NULL DEFAULT 'GENERATION_001'")
                db.execSQL("ALTER TABLE `drive_deletion_tombstones` ADD COLUMN `dataset_generation_id` TEXT NOT NULL DEFAULT 'GENERATION_001'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_technical_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operation_time_millis` INTEGER NOT NULL,
                        `operation_time_display` TEXT NOT NULL,
                        `operation_type` TEXT NOT NULL,
                        `record_id` TEXT NOT NULL,
                        `drive_file_id` TEXT NOT NULL,
                        `dataset_generation_id` TEXT NOT NULL,
                        `local_status` TEXT NOT NULL,
                        `cloud_status` TEXT NOT NULL,
                        `error_code` TEXT NOT NULL,
                        `error_message` TEXT NOT NULL,
                        `retry_count` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_technical_logs_operation_time_millis` ON `sync_technical_logs` (`operation_time_millis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_technical_logs_operation_type` ON `sync_technical_logs` (`operation_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_technical_logs_dataset_generation_id` ON `sync_technical_logs` (`dataset_generation_id`)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `freezer_units` ADD COLUMN `maintenanceHistory` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `freezer_unit_maintenance` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `unit_id` INTEGER NOT NULL,
                        `unit_code` TEXT NOT NULL,
                        `maintenance_date` TEXT NOT NULL,
                        `maintenance_type` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `technician_name` TEXT NOT NULL,
                        `cost` INTEGER NOT NULL,
                        `status_after_maintenance` TEXT NOT NULL,
                        `created_at_millis` INTEGER NOT NULL,
                        FOREIGN KEY(`unit_id`) REFERENCES `freezer_units`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_freezer_unit_maintenance_unit_id` ON `freezer_unit_maintenance` (`unit_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_freezer_unit_maintenance_unit_code` ON `freezer_unit_maintenance` (`unit_code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_freezer_unit_maintenance_maintenance_date` ON `freezer_unit_maintenance` (`maintenance_date`)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val sqlUuidV4Expr =
                    "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
                        "substr(lower(hex(randomblob(2))),2) || '-' || " +
                        "substr('89ab', abs(random()) % 4 + 1, 1) || " +
                        "substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))))"
                val coreTables = listOf("freezer_units", "customers", "rental_transactions", "payment_proofs")
                for (table in coreTables) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE `$table` SET `uuid` = $sqlUuidV4Expr WHERE `uuid` = ''")
                    db.execSQL("UPDATE `$table` SET `created_at` = (CAST(strftime('%s','now') AS INTEGER) * 1000) WHERE `created_at` = 0")
                    db.execSQL("UPDATE `$table` SET `updated_at` = `created_at` WHERE `updated_at` = 0")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uuid` ON `$table` (`uuid`)")
                }

                db.execSQL("ALTER TABLE `drive_cloud_file_map` ADD COLUMN `record_uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `drive_cloud_file_map` ADD COLUMN `server_timestamp_millis` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `drive_cloud_file_map` ADD COLUMN `conflict_reason` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_cloud_file_map_record_uuid` ON `drive_cloud_file_map` (`record_uuid`)")

                db.execSQL("ALTER TABLE `drive_local_sync_queue` ADD COLUMN `record_uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `drive_local_sync_queue` ADD COLUMN `base_version` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `drive_local_sync_queue` ADD COLUMN `server_timestamp_millis` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_local_sync_queue_record_uuid` ON `drive_local_sync_queue` (`record_uuid`)")

                db.execSQL("ALTER TABLE `drive_deletion_tombstones` ADD COLUMN `record_uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_deletion_tombstones_record_uuid` ON `drive_deletion_tombstones` (`record_uuid`)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val coreTables = listOf("freezer_units", "customers", "rental_transactions", "payment_proofs")
                for (table in coreTables) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `sync_status` TEXT NOT NULL DEFAULT 'PENDING'")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `changed_fields_json` TEXT NOT NULL DEFAULT ''")
                }

                db.execSQL("ALTER TABLE `drive_local_sync_queue` ADD COLUMN `changed_fields_json` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `drive_deletion_tombstones` ADD COLUMN `uuid_verified_gone` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `drive_deletion_tombstones` ADD COLUMN `manifest_updated_at_deletion` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_cloud_quarantine` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `drive_file_id` TEXT NOT NULL,
                        `drive_parent_folder_id` TEXT NOT NULL DEFAULT '',
                        `file_name` TEXT NOT NULL,
                        `classification` TEXT NOT NULL,
                        `local_record_key` TEXT NOT NULL DEFAULT '',
                        `record_uuid` TEXT NOT NULL DEFAULT '',
                        `dataset_generation_id` TEXT NOT NULL DEFAULT '',
                        `ownership_verified` INTEGER NOT NULL DEFAULT 0,
                        `quarantine_reason` TEXT NOT NULL DEFAULT '',
                        `status` TEXT NOT NULL DEFAULT 'QUARANTINED',
                        `quarantined_at_millis` INTEGER NOT NULL DEFAULT 0,
                        `resolved_at_millis` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_cloud_quarantine_drive_file_id` ON `drive_cloud_quarantine` (`drive_file_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_cloud_quarantine_classification` ON `drive_cloud_quarantine` (`classification`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_cloud_quarantine_status` ON `drive_cloud_quarantine` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_cloud_quarantine_record_uuid` ON `drive_cloud_quarantine` (`record_uuid`)")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val mapCols = buildSet {
                    db.query("PRAGMA table_info(`drive_cloud_file_map`)").use { cursor ->
                        val nameIdx = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (nameIdx >= 0) add(cursor.getString(nameIdx))
                        }
                    }
                }
                val hasExtendedMapCols = "record_category" in mapCols &&
                    "local_record_id" in mapCols &&
                    "record_uuid" in mapCols

                val coreTables = listOf(
                    "freezer_units" to "UNIT",
                    "customers" to "CUSTOMER",
                    "rental_transactions" to "TRANSACTION",
                    "payment_proofs" to "PAYMENT"
                )
                for ((table, category) in coreTables) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `is_deleted` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `drive_id` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_is_deleted` ON `$table` (`is_deleted`)")
                    db.execSQL("UPDATE `$table` SET `sync_status` = 'PENDING_CREATE' WHERE `sync_status` = 'PENDING' OR `sync_status` = ''")
                    if (hasExtendedMapCols) {
                        db.execSQL(
                            """
                            UPDATE `$table` SET `drive_id` = COALESCE(
                                (SELECT `drive_file_id` FROM `drive_cloud_file_map`
                                 WHERE `record_category` = '$category'
                                   AND (`local_record_id` = `$table`.`id` OR (`record_uuid` != '' AND `record_uuid` = `$table`.`uuid`))
                                 LIMIT 1),
                                ''
                            ) WHERE `drive_id` = ''
                            """.trimIndent()
                        )
                    } else if ("local_record_key" in mapCols && "drive_file_id" in mapCols) {
                        db.execSQL(
                            """
                            UPDATE `$table` SET `drive_id` = COALESCE(
                                (SELECT `drive_file_id` FROM `drive_cloud_file_map`
                                 WHERE `local_record_key` = ('$category:' || `$table`.`id`)
                                 LIMIT 1),
                                ''
                            ) WHERE `drive_id` = ''
                            """.trimIndent()
                        )
                    }
                }
            }
        }

        /**
         * v23 Additive Non-Destructive Migration:
         * 1. Creates isolated `form_drafts` table for Auto-Draft across all forms.
         * 2. Creates `unit_rental_prices` table for per-unit rental period pricing (3, 7, 14, 30, 60, 90 hari).
         * 3. Creates `transaction_officer_audit_logs` table for audited corrections of locked officer snapshots.
         * 4. Adds permanent officer snapshot metadata columns to `rental_transactions` without modifying existing rows.
         */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Isolated Auto-Draft table (never synced to active tables or Drive sync queue)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `form_drafts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `form_type` TEXT NOT NULL,
                        `target_entity_id` INTEGER NOT NULL DEFAULT 0,
                        `owner_account` TEXT NOT NULL DEFAULT '',
                        `title` TEXT NOT NULL DEFAULT '',
                        `subtitle` TEXT NOT NULL DEFAULT '',
                        `payload_json` TEXT NOT NULL DEFAULT '{}',
                        `version` INTEGER NOT NULL DEFAULT 1,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at_display` TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_form_drafts_uuid` ON `form_drafts` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_form_drafts_form_type_target_entity_id` ON `form_drafts` (`form_type`, `target_entity_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_form_drafts_owner_account` ON `form_drafts` (`owner_account`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_form_drafts_updated_at` ON `form_drafts` (`updated_at`)")

                // 2. Per-unit rental period price table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `unit_rental_prices` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `unit_id` INTEGER NOT NULL,
                        `period_days` INTEGER NOT NULL,
                        `price` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`unit_id`) REFERENCES `freezer_units`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_unit_rental_prices_unit_id` ON `unit_rental_prices` (`unit_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_unit_rental_prices_unit_id_period_days` ON `unit_rental_prices` (`unit_id`, `period_days`)")

                // 3. Transaction Officer Correction Audit Log table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_officer_audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transaction_id` INTEGER NOT NULL,
                        `previous_officer_name` TEXT NOT NULL DEFAULT '',
                        `new_officer_name` TEXT NOT NULL DEFAULT '',
                        `previous_signature_uri` TEXT NOT NULL DEFAULT '',
                        `new_signature_uri` TEXT NOT NULL DEFAULT '',
                        `corrected_by` TEXT NOT NULL DEFAULT '',
                        `correction_reason` TEXT NOT NULL DEFAULT '',
                        `corrected_at_millis` INTEGER NOT NULL DEFAULT 0,
                        `corrected_at_display` TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(`transaction_id`) REFERENCES `rental_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_officer_audit_logs_transaction_id` ON `transaction_officer_audit_logs` (`transaction_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_officer_audit_logs_corrected_at_millis` ON `transaction_officer_audit_logs` (`corrected_at_millis`)")

                // 4. Additive Permanent Officer Snapshot columns on rental_transactions
                val existingTxCols = mutableSetOf<String>()
                db.query("PRAGMA table_info(`rental_transactions`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0) existingTxCols.add(cursor.getString(nameIdx))
                    }
                }
                if ("officer_account_id" !in existingTxCols) {
                    db.execSQL("ALTER TABLE `rental_transactions` ADD COLUMN `officer_account_id` TEXT NOT NULL DEFAULT ''")
                }
                if ("officer_snapshot_locked" !in existingTxCols) {
                    db.execSQL("ALTER TABLE `rental_transactions` ADD COLUMN `officer_snapshot_locked` INTEGER NOT NULL DEFAULT 0")
                }
                if ("officer_signed_at" !in existingTxCols) {
                    db.execSQL("ALTER TABLE `rental_transactions` ADD COLUMN `officer_signed_at` TEXT NOT NULL DEFAULT ''")
                }
                // Lock existing transactions that already have an officer name or signature to protect historical records
                db.execSQL(
                    """
                    UPDATE `rental_transactions`
                    SET `officer_snapshot_locked` = 1
                    WHERE (`namaPetugas` != '' OR `tandaTanganPetugasUri` != '') AND `officer_snapshot_locked` = 0
                    """.trimIndent()
                )
            }
        }

        /**
         * v24 Additive Non-Destructive Migration:
         * Adds soft-delete tombstone audit columns (`deleted_at`, `deleted_by`) to `rental_transactions`
         * while preserving 100% of existing transaction, customer, unit, payment, and sync records.
         */
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingTxCols = mutableSetOf<String>()
                db.query("PRAGMA table_info(`rental_transactions`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0) existingTxCols.add(cursor.getString(nameIdx))
                    }
                }
                if ("deleted_at" !in existingTxCols) {
                    db.execSQL("ALTER TABLE `rental_transactions` ADD COLUMN `deleted_at` INTEGER NOT NULL DEFAULT 0")
                }
                if ("deleted_by" !in existingTxCols) {
                    db.execSQL("ALTER TABLE `rental_transactions` ADD COLUMN `deleted_by` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /**
         * v25 Additive Non-Destructive Migration:
         * - Adds unit_id, uuid, upload_status, drive_file_id, updated_at_millis to `customer_photos`
         *   for Freezer-in-Location & Front-of-Location documentation sync tracking.
         * - Adds per-unit renewal tracking columns (uuid, customer_id, unit_id, unit_item_id,
         *   jenis_program, pembayaran_ke_hak_milik, total_periode_hak_milik, diskon, nominal_dibayar,
         *   status_pembayaran, updated_at_millis) to `extension_history`.
         */
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun existingColumns(tableName: String): Set<String> {
                    val cols = mutableSetOf<String>()
                    db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                        val nameIdx = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (nameIdx >= 0) cols.add(cursor.getString(nameIdx))
                        }
                    }
                    return cols
                }

                val photoCols = existingColumns("customer_photos")
                if ("unit_id" !in photoCols) {
                    db.execSQL("ALTER TABLE `customer_photos` ADD COLUMN `unit_id` INTEGER")
                }
                if ("uuid" !in photoCols) {
                    db.execSQL("ALTER TABLE `customer_photos` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                }
                if ("upload_status" !in photoCols) {
                    db.execSQL("ALTER TABLE `customer_photos` ADD COLUMN `upload_status` TEXT NOT NULL DEFAULT 'PENDING'")
                }
                if ("drive_file_id" !in photoCols) {
                    db.execSQL("ALTER TABLE `customer_photos` ADD COLUMN `drive_file_id` TEXT NOT NULL DEFAULT ''")
                }
                if ("updated_at_millis" !in photoCols) {
                    db.execSQL("ALTER TABLE `customer_photos` ADD COLUMN `updated_at_millis` INTEGER NOT NULL DEFAULT 0")
                }

                val extCols = existingColumns("extension_history")
                if ("uuid" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                }
                if ("customer_id" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `customer_id` INTEGER NOT NULL DEFAULT 0")
                }
                if ("unit_id" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `unit_id` INTEGER NOT NULL DEFAULT 0")
                }
                if ("unit_item_id" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `unit_item_id` INTEGER NOT NULL DEFAULT 0")
                }
                if ("jenis_program" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `jenis_program` TEXT NOT NULL DEFAULT 'Sewa Reguler'")
                }
                if ("pembayaran_ke_hak_milik" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `pembayaran_ke_hak_milik` INTEGER NOT NULL DEFAULT 0")
                }
                if ("total_periode_hak_milik" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `total_periode_hak_milik` INTEGER NOT NULL DEFAULT 0")
                }
                if ("diskon" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `diskon` INTEGER NOT NULL DEFAULT 0")
                }
                if ("nominal_dibayar" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `nominal_dibayar` INTEGER NOT NULL DEFAULT 0")
                }
                if ("status_pembayaran" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `status_pembayaran` TEXT NOT NULL DEFAULT 'Lunas'")
                }
                if ("updated_at_millis" !in extCols) {
                    db.execSQL("ALTER TABLE `extension_history` ADD COLUMN `updated_at_millis` INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private fun backupDatabaseFileBeforeMigration(context: Context) {
            try {
                val dbFile = context.getDatabasePath("sewain_aja_freezer_soc.db")
                if (dbFile.exists() && dbFile.length() > 0L) {
                    val backupDir = File(context.filesDir, "db_backups")
                    if (!backupDir.exists()) backupDir.mkdirs()
                    val backupFile = File(backupDir, "sewain_aja_freezer_soc_pre_migration_backup.db")
                    dbFile.copyTo(backupFile, overwrite = true)
                }
            } catch (_: Exception) {
                // Non-fatal backup helper
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                backupDatabaseFileBeforeMigration(context.applicationContext)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sewain_aja_freezer_soc.db"
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24,
                        MIGRATION_24_25
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
