package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.GoogleDriveAndReportExporter
import com.example.data.local.AppDatabase
import com.example.data.local.CloudPhotoJobEntity
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowCompatPdfDocument::class])
class CloudUpgradeAndScaleTest {

    @Test
    fun migration13To14PreservesExistingDataAndCreatesCloudTablesAndIndices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_13_14_test_${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(13) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `freezer_units` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `kodeUnit` TEXT NOT NULL,
                        `namaUnit` TEXT NOT NULL,
                        `statusUnit` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `kodePelanggan` TEXT NOT NULL,
                        `namaLengkap` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rental_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nomorInvoice` TEXT NOT NULL,
                        `statusPembayaran` TEXT NOT NULL,
                        `statusPenyewaan` TEXT NOT NULL,
                        `tanggalJatuhTempo` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO `freezer_units` (`kodeUnit`, `namaUnit`, `statusUnit`) VALUES ('FRZ/SAF/0001', 'GEA 100L', 'Tersedia')")
                db.execSQL("INSERT INTO `customers` (`kodePelanggan`, `namaLengkap`) VALUES ('CUST/SAF/0001', 'Budi Santoso')")
                db.execSQL("INSERT INTO `rental_transactions` (`nomorInvoice`, `statusPembayaran`, `statusPenyewaan`, `tanggalJatuhTempo`) VALUES ('INV/SAF/0001', 'Belum Bayar', 'Dalam Penyewaan', '2026-12-01')")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_13_14.migrate(db)

        db.query("SELECT COUNT(*) FROM customers WHERE namaLengkap = 'Budi Santoso'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM freezer_units WHERE kodeUnit = 'FRZ/SAF/0001'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM rental_transactions WHERE nomorInvoice = 'INV/SAF/0001'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        val createdTables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) createdTables.add(cursor.getString(0))
        }
        assertTrue(createdTables.contains("cloud_sync_queue"))
        assertTrue(createdTables.contains("cloud_photo_jobs"))
        assertTrue(createdTables.contains("cloud_migration_state"))

        val createdIndices = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='index'").use { cursor ->
            while (cursor.moveToNext()) createdIndices.add(cursor.getString(0))
        }
        assertTrue(createdIndices.contains("index_freezer_units_statusUnit"))
        assertTrue(createdIndices.contains("index_rental_transactions_statusPembayaran"))
        assertTrue(createdIndices.contains("index_rental_transactions_statusPenyewaan"))
        assertTrue(createdIndices.contains("index_rental_transactions_tanggalJatuhTempo"))

        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun largeScaleDatasetInsertionQueryAndExcelPdfExportsSucceed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.freezerDao()

        (1..100).forEach { idx ->
            val kode = "FRZ/SCALE/${idx.toString().padStart(4, '0')}"
            dao.insertUnit(
                FreezerUnitEntity(
                    kodeUnit = kode,
                    namaUnit = "Freezer Scale $idx",
                    jenisUnit = "Chest Freezer",
                    owner = "SEWAin Aja",
                    hargaPembelian = 3500000L,
                    kapasitasLiter = 200,
                    cicilanPerBulan = 300000L,
                    merek = "GEA",
                    model = "AB-226R",
                    nomorSeri = "SN-$idx",
                    statusUnit = if (idx % 2 == 0) "Tersedia" else "Disewa",
                    lokasiUnit = "Solo",
                    tanggalPembelian = "2026-01-01"
                )
            )
        }

        (1..500).forEach { idx ->
            dao.insertCustomer(
                CustomerEntity(
                    kodePelanggan = "CUST/SCALE/${idx.toString().padStart(4, '0')}",
                    namaLengkap = "Pelanggan Skala Besar $idx",
                    nik = "3372010101${idx.toString().padStart(6, '0')}",
                    nomorHp = "62813000${idx.toString().padStart(5, '0')}",
                    alamatLengkap = "Jl. Slamet Riyadi No. $idx, Surakarta",
                    jenisKelamin = "Laki-laki",
                    tanggalRegistrasi = "2026-11-01"
                )
            )
        }

        (1..1000).forEach { idx ->
            val custIndex = ((idx - 1) % 500) + 1
            val unitIndex = ((idx - 1) % 100) + 1
            dao.insertTransaction(
                RentalTransactionEntity(
                    nomorInvoice = "INV/SCALE/${idx.toString().padStart(5, '0')}",
                    tanggalPemesanan = "2026-11-01",
                    customerId = custIndex.toLong(),
                    namaPelangganSnapshot = "Pelanggan Skala Besar $custIndex",
                    unitId = unitIndex.toLong(),
                    namaUnitSnapshot = "Freezer Scale $unitIndex",
                    kodeUnitSnapshot = "FRZ/SCALE/${unitIndex.toString().padStart(4, '0')}",
                    hargaSewa = 200000L,
                    durasiHari = 30,
                    tanggalMulaiSewa = "2026-11-01",
                    tanggalAkhirSewa = "2026-12-01",
                    tanggalJatuhTempo = "2026-12-01",
                    nominalPembayaran = 100000L,
                    totalTagihan = 200000L,
                    statusPembayaran = "Cicilan",
                    statusPenyewaan = "Dalam Penyewaan",
                    metodePembayaran = "Transfer BCA",
                    jenisProgram = "Sewa Reguler"
                )
            )
        }

        (1..200).forEach { idx ->
            dao.upsertCloudPhotoJob(
                CloudPhotoJobEntity(
                    uid = "scale_uid",
                    ownerType = "CUSTOMER",
                    ownerLocalId = idx.toLong(),
                    ownerCloudId = "cust_$idx",
                    photoSlot = "KTP",
                    localFilePath = "/tmp/ktp_$idx.jpg",
                    storagePath = "users/scale_uid/customers/cust_$idx/ktp/ktp_$idx.jpg"
                )
            )
        }

        val allUnits = dao.getAllUnitsOnce()
        val allCustomers = dao.getAllCustomersOnce()
        val allTxs = dao.getAllTransactionsOnce()
        val allPhotoJobs = dao.getAllCloudPhotoJobsOnce("scale_uid")

        assertEquals(100, allUnits.size)
        assertEquals(500, allCustomers.size)
        assertEquals(1000, allTxs.size)
        assertEquals(200, allPhotoJobs.size)

        val xlsxOut = ByteArrayOutputStream()
        val xlsxRes = GoogleDriveAndReportExporter.exportCustomersToXlsxStream(allCustomers, xlsxOut)
        assertTrue(xlsxRes.isSuccess)
        assertEquals(500, xlsxRes.getOrNull())
        val xlsxBytes = xlsxOut.toByteArray()
        assertTrue(xlsxBytes.size > 1000)
        assertEquals('P'.code.toByte(), xlsxBytes[0])
        assertEquals('K'.code.toByte(), xlsxBytes[1])

        val pdfOut = ByteArrayOutputStream()
        val pdfRes = GoogleDriveAndReportExporter.exportTransactionsToPdfStream(
            transactions = allTxs.take(120),
            companyProfile = CompanyProfile(namaPerusahaan = "SEWAin Aja Freezer SOC"),
            outputStream = pdfOut
        )
        assertTrue(pdfRes.isSuccess)
        assertEquals(120, pdfRes.getOrNull())
        val pdfBytes = pdfOut.toByteArray()
        assertTrue(pdfBytes.size > 1000)
        val header = String(pdfBytes.copyOfRange(0, 4), Charsets.US_ASCII)
        assertEquals("%PDF", header)

        db.close()
    }
}
