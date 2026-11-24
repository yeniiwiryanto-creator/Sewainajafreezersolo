package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.FreezerUnitDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.FreezerUnitMaintenanceEntity
import com.example.data.local.FreezerUnitStatus
import com.example.data.repository.FreezerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FreezerUnitRegistryDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var unitDao: FreezerUnitDao
    private lateinit var repository: FreezerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        unitDao = database.freezerUnitDao()
        repository = FreezerRepository(database.freezerDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleUnit(
        kodeUnit: String,
        merek: String,
        model: String,
        statusUnit: String,
        maintenanceHistory: String = ""
    ) = FreezerUnitEntity(
        kodeUnit = kodeUnit,
        namaUnit = "$merek $model",
        jenisUnit = "Chest Freezer",
        owner = "Owner",
        hargaPembelian = 4_500_000L,
        kapasitasLiter = 210,
        cicilanPerBulan = 450_000L,
        merek = merek,
        model = model,
        nomorSeri = "SN-$kodeUnit",
        statusUnit = statusUnit,
        lokasiUnit = "Gudang Solo",
        tanggalPembelian = "2026-01-10",
        maintenanceHistory = maintenanceHistory
    )

    @Test
    fun unitRegistry_filtersByAvailableRentedBrandModelAndSearch() = runBlocking {
        unitDao.insertUnit(sampleUnit("SAF-CF-001", "GEA", "AB-226-R", FreezerUnitStatus.AVAILABLE))
        unitDao.insertUnit(sampleUnit("SAF-CF-002", "RSA", "CF-210", FreezerUnitStatus.ENGLISH_AVAILABLE))
        unitDao.insertUnit(sampleUnit("SAF-CF-003", "GEA", "AB-318-R", FreezerUnitStatus.RENTED))
        unitDao.insertUnit(sampleUnit("SAF-CF-004", "Modena", "MD-30W", FreezerUnitStatus.ENGLISH_RENTED))
        unitDao.insertUnit(sampleUnit("SAF-CF-005", "RSA", "CF-310", FreezerUnitStatus.MAINTENANCE))

        val available = unitDao.getAvailableUnits().first()
        assertEquals(2, available.size)
        assertTrue(available.all { it.isAvailableStatus() })
        assertEquals(listOf("SAF-CF-001", "SAF-CF-002"), available.map { it.kodeUnit })

        val rented = unitDao.getRentedUnits().first()
        assertEquals(2, rented.size)
        assertTrue(rented.all { it.isRentedStatus() })
        assertEquals(listOf("SAF-CF-003", "SAF-CF-004"), rented.map { it.kodeUnit })

        val maintenance = unitDao.getMaintenanceUnits().first()
        assertEquals(1, maintenance.size)
        assertEquals("SAF-CF-005", maintenance.first().kodeUnit)

        val geaUnits = unitDao.getUnitsByBrand("gea").first()
        assertEquals(2, geaUnits.size)

        val modelMatches = unitDao.getUnitsByModel("cf-210").first()
        assertEquals(1, modelMatches.size)
        assertEquals("SAF-CF-002", modelMatches.first().kodeUnit)

        val searchMatches = unitDao.searchUnits("MD-30W").first()
        assertEquals(1, searchMatches.size)
        assertEquals("Modena", searchMatches.first().merek)
    }

    @Test
    fun maintenanceHistory_recordsViaDaoAndRepository_relationAndCascadeDelete() = runBlocking {
        val unitId = unitDao.insertUnit(
            sampleUnit("SAF-CF-010", "GEA", "AB-226-R", FreezerUnitStatus.MAINTENANCE)
        )

        val firstRecordId = unitDao.recordMaintenanceAndUpdateUnit(
            unitId = unitId,
            maintenanceDate = "2026-03-01",
            maintenanceType = "Pembersihan Kondensor",
            description = "Bersihkan debu kumparan dan cek thermostat",
            technicianName = "Budi",
            cost = 150_000L,
            statusAfterMaintenance = FreezerUnitStatus.MAINTENANCE
        )
        assertTrue(firstRecordId > 0L)

        val secondRecordId = repository.recordUnitMaintenance(
            unitId = unitId,
            maintenanceDate = "2026-03-10",
            maintenanceType = "Isi Freon & Kalibrasi",
            description = "Tambah freon R600a dan tes -20C",
            technicianName = "Andi",
            cost = 275_000L,
            statusAfterMaintenance = FreezerUnitStatus.AVAILABLE
        )
        assertTrue(secondRecordId > 0L)

        val withHistory = unitDao.getUnitWithMaintenanceHistoryById(unitId).first()
        assertNotNull(withHistory)
        assertEquals(FreezerUnitStatus.AVAILABLE, withHistory!!.unit.statusUnit)
        assertTrue(withHistory.unit.maintenanceHistory.contains("Isi Freon & Kalibrasi"))
        assertTrue(withHistory.unit.maintenanceHistory.contains("Pembersihan Kondensor"))
        assertEquals(2, withHistory.maintenanceRecords.size)

        val orderedRecords = unitDao.getMaintenanceHistoryForUnit(unitId).first()
        assertEquals("2026-03-10", orderedRecords[0].maintenanceDate)
        assertEquals("2026-03-01", orderedRecords[1].maintenanceDate)

        // Verify FOREIGN KEY CASCADE removes maintenance records when unit is deleted
        unitDao.deleteUnitById(unitId)
        assertNull(unitDao.getUnitById(unitId))
        val remainingRecords = unitDao.getMaintenanceHistoryForUnitOnce(unitId)
        assertTrue(remainingRecords.isEmpty())
    }

    @Test
    fun migration18To19_preservesExistingUnitsAndCreatesMaintenanceTable() {
        val dbName = "test_migration_18_19.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
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
                                `jumlahCicilanTotal` INTEGER NOT NULL,
                                `totalPembayaranCicilan` INTEGER NOT NULL,
                                `statusInvestasi` TEXT NOT NULL,
                                `kategoriSheet` TEXT NOT NULL,
                                `merek` TEXT NOT NULL,
                                `model` TEXT NOT NULL,
                                `nomorSeri` TEXT NOT NULL,
                                `fotoUnitUri` TEXT NOT NULL,
                                `fotoKondisiFisikUri` TEXT NOT NULL,
                                `fotoBagianDalamUri` TEXT NOT NULL,
                                `fotoMerekSeriUri` TEXT NOT NULL,
                                `statusUnit` TEXT NOT NULL,
                                `lokasiUnit` TEXT NOT NULL,
                                `tanggalPembelian` TEXT NOT NULL,
                                `keterangan` TEXT NOT NULL,
                                `kondisiUnit` TEXT NOT NULL,
                                `tanggalPengiriman` TEXT NOT NULL,
                                `lokasiGpsPemasangan` TEXT NOT NULL,
                                `latitudePemasangan` REAL,
                                `longitudePemasangan` REAL
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_freezer_units_kodeUnit` ON `freezer_units` (`kodeUnit`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_freezer_units_statusUnit` ON `freezer_units` (`statusUnit`)")
                        db.execSQL(
                            """
                            INSERT INTO `freezer_units` (
                                kodeUnit, namaUnit, jenisUnit, owner, hargaPembelian, kapasitasLiter,
                                cicilanPerBulan, jumlahCicilanTotal, totalPembayaranCicilan,
                                statusInvestasi, kategoriSheet, merek, model, nomorSeri,
                                fotoUnitUri, fotoKondisiFisikUri, fotoBagianDalamUri, fotoMerekSeriUri,
                                statusUnit, lokasiUnit, tanggalPembelian, keterangan,
                                kondisiUnit, tanggalPengiriman, lokasiGpsPemasangan,
                                latitudePemasangan, longitudePemasangan
                            ) VALUES (
                                'SAF-MIG-001', 'RSA CF-210', 'Chest Freezer', 'Owner', 4200000, 210,
                                400000, 12, 0, 'LIST', 'INVESTASI SEWA FREEZER', 'RSA', 'CF-210', 'SN-MIG-001',
                                '', '', '', '', 'Tersedia', 'Gudang', '2026-01-01', '',
                                'Baik & Siap Pakai', '', '', NULL, NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val sqliteDb = helper.writableDatabase
        AppDatabase.MIGRATION_18_19.migrate(sqliteDb)

        sqliteDb.query("SELECT kodeUnit, merek, model, statusUnit, maintenanceHistory FROM freezer_units WHERE kodeUnit = 'SAF-MIG-001'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("SAF-MIG-001", cursor.getString(0))
            assertEquals("RSA", cursor.getString(1))
            assertEquals("CF-210", cursor.getString(2))
            assertEquals("Tersedia", cursor.getString(3))
            assertEquals("", cursor.getString(4))
        }

        sqliteDb.execSQL(
            """
            INSERT INTO `freezer_unit_maintenance` (
                unit_id, unit_code, maintenance_date, maintenance_type, description,
                technician_name, cost, status_after_maintenance, created_at_millis
            ) VALUES (1, 'SAF-MIG-001', '2026-04-01', 'Service Rutin', 'Cek kompresor', 'Deni', 100000, 'Tersedia', 1775000000000)
            """.trimIndent()
        )
        sqliteDb.query("SELECT COUNT(*) FROM freezer_unit_maintenance WHERE unit_id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        sqliteDb.close()
        helper.close()
        context.deleteDatabase(dbName)
    }
}
