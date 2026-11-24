package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.cloud.CloudCustomer
import com.example.cloud.CloudFirestoreRepository
import com.example.cloud.CloudFreezerUnit
import com.example.cloud.CloudStoragePhotoManager
import com.example.cloud.CloudSyncAndMigrationManager
import com.example.data.local.AppDatabase
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.SmartNotificationSettings
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudSyncMigrationRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var context: Context
    private lateinit var roomDb: AppDatabase
    private lateinit var dao: FreezerDao

    @Before
    fun setUpLocalDb() {
        context = ApplicationProvider.getApplicationContext()
        roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = roomDb.freezerDao()
    }

    @After
    fun tearDownLocalDb() {
        roomDb.close()
    }

    private fun makeCustomer(
        kode: String,
        nama: String,
        hp: String,
        nik: String = "3372010101900001"
    ) = CustomerEntity(
        kodePelanggan = kode,
        namaLengkap = nama,
        nik = nik,
        nomorHp = hp,
        alamatLengkap = "Jl. Slamet Riyadi No. 10, Surakarta",
        jenisKelamin = "Laki-laki",
        tanggalRegistrasi = "2026-11-01"
    )

    private fun makeUnit(
        kode: String,
        nama: String,
        status: String = "Tersedia",
        kapasitas: Int = 200
    ) = FreezerUnitEntity(
        kodeUnit = kode,
        namaUnit = nama,
        jenisUnit = "Chest Freezer",
        owner = "SEWAin Aja",
        hargaPembelian = 3500000L,
        kapasitasLiter = kapasitas,
        cicilanPerBulan = 300000L,
        merek = "GEA",
        model = "AB-226R",
        nomorSeri = "SN-$kode",
        statusUnit = status,
        lokasiUnit = "Gudang Solo",
        tanggalPembelian = "2026-01-15"
    )

    private fun makeTx(
        invoice: String,
        customerId: Long,
        namaPelanggan: String,
        unitId: Long,
        namaUnit: String,
        kodeUnit: String
    ) = RentalTransactionEntity(
        nomorInvoice = invoice,
        tanggalPemesanan = "2026-11-01",
        customerId = customerId,
        namaPelangganSnapshot = namaPelanggan,
        unitId = unitId,
        namaUnitSnapshot = namaUnit,
        kodeUnitSnapshot = kodeUnit,
        hargaSewa = 200000L,
        durasiHari = 30,
        tanggalMulaiSewa = "2026-11-01",
        tanggalAkhirSewa = "2026-12-01",
        tanggalJatuhTempo = "2026-12-01",
        nominalPembayaran = 200000L,
        totalTagihan = 200000L,
        statusPembayaran = "Lunas",
        statusPenyewaan = "Dalam Penyewaan",
        metodePembayaran = "Transfer BCA",
        jenisProgram = "Sewa Reguler"
    )

    @Test
    fun offlineQueueWaitsForInternetThenDrainsIdempotentlyOnReconnect() = runBlocking {
        val uid = signInTestUser("sync_offline_test@test.com")
        var simulatedOnline = false
        val cloudRepo = CloudFirestoreRepository(firestore, auth)
        val photoMgr = CloudStoragePhotoManager(
            context = context,
            dao = dao
        )
        val syncManager = CloudSyncAndMigrationManager(
            context = context,
            dao = dao,
            cloudRepo = cloudRepo,
            photoManager = photoMgr,
            networkAvailabilityOverride = { simulatedOnline }
        )

        val custId = dao.insertCustomer(makeCustomer("CUST/SAF/8001", "Offline Test Pelanggan", "6281234560001"))
        val cust = dao.getCustomerById(custId)!!

        val unitId = dao.insertUnit(makeUnit("FRZ/SAF/8001", "Freezer Offline 100L", status = "Tersedia", kapasitas = 100))
        val unit = dao.getUnitById(unitId)!!

        syncManager.enqueueCustomer(uid, cust, autoDrain = true)
        syncManager.enqueueUnit(uid, unit, autoDrain = true)

        assertEquals(CloudSyncAndMigrationManager.STATUS_WAITING_NETWORK, syncManager.syncStatus.value)
        assertEquals(2, dao.getPendingSyncQueueOnce(uid).size)

        simulatedOnline = true
        val drained = withTimeout(15000L) { syncManager.drainPendingQueue(uid) }
        assertTrue(drained)
        assertEquals(CloudSyncAndMigrationManager.STATUS_SYNCED, syncManager.syncStatus.value)
        assertEquals(0, dao.getPendingSyncQueueOnce(uid).size)

        val cloudCust = withTimeout(10000L) { cloudRepo.getCustomer("cust_$custId") }
        assertNotNull(cloudCust)
        assertEquals("Offline Test Pelanggan", cloudCust?.namaLengkap)
    }

    @Test
    fun resumableTenStepMigrationPausesAtStep5ResumesToCompletedAndPreservesSqlite() = runBlocking {
        val uid = signInTestUser("migration_resume_test@test.com")
        val firebaseUser = auth.currentUser!!
        val cloudRepo = CloudFirestoreRepository(firestore, auth)
        val photoMgr = CloudStoragePhotoManager(
            context = context,
            dao = dao
        )
        val syncManager = CloudSyncAndMigrationManager(
            context = context,
            dao = dao,
            cloudRepo = cloudRepo,
            photoManager = photoMgr,
            networkAvailabilityOverride = { true }
        )

        val custId = dao.insertCustomer(makeCustomer("CUST/SAF/9001", "Pelanggan Migrasi Resumable", "6281234569001"))
        val unitId = dao.insertUnit(makeUnit("FRZ/SAF/9001", "RSA CF-210", status = "Disewa", kapasitas = 210))
        dao.insertTransaction(
            makeTx(
                invoice = "INV/SAF/2026/9001",
                customerId = custId,
                namaPelanggan = "Pelanggan Migrasi Resumable",
                unitId = unitId,
                namaUnit = "RSA CF-210",
                kodeUnit = "FRZ/SAF/9001"
            )
        )

        val paused = withTimeout(15000L) {
            syncManager.runOrResumeMigration(
                user = firebaseUser,
                companyProfile = CompanyProfile(),
                perjanjianText = "Perjanjian Sewa",
                paymentMethods = emptyList(),
                smartNotif = SmartNotificationSettings(),
                stopAfterStepForTest = 5
            )
        }
        assertEquals("PAUSED", paused.status)
        assertEquals(6, paused.currentStep)

        val completed = withTimeout(20000L) {
            syncManager.runOrResumeMigration(
                user = firebaseUser,
                companyProfile = CompanyProfile(),
                perjanjianText = "Perjanjian Sewa",
                paymentMethods = emptyList(),
                smartNotif = SmartNotificationSettings(),
                stopAfterStepForTest = null
            )
        }
        assertEquals("COMPLETED", completed.status)
        assertEquals(10, completed.currentStep)
        assertEquals(1, completed.localCustomersCount)
        assertTrue(completed.cloudCustomersCount >= 1)
        assertEquals(1, completed.localUnitsCount)
        assertTrue(completed.cloudUnitsCount >= 1)
        assertEquals(1, completed.localTxCount)
        assertTrue(completed.cloudTxCount >= 1)

        // Ensure SQLite records were never deleted
        assertEquals(1, dao.getAllCustomersOnce().size)
        assertEquals(1, dao.getAllUnitsOnce().size)
        assertEquals(1, dao.getAllTransactionsOnce().size)
    }

    @Test
    fun multiDeviceTimestampConflictResolutionFavorsNewestTimestamp() = runBlocking {
        val uid = signInTestUser("conflict_resolution_test@test.com")
        val cloudRepo = CloudFirestoreRepository(firestore, auth)
        val photoMgr = CloudStoragePhotoManager(
            context = context,
            dao = dao
        )
        val syncManager = CloudSyncAndMigrationManager(
            context = context,
            dao = dao,
            cloudRepo = cloudRepo,
            photoManager = photoMgr,
            networkAvailabilityOverride = { false }
        )

        val c1Id = dao.insertCustomer(makeCustomer("CUST/SAF/7001", "Lokal Lebih Baru", "628111111111"))
        val c1 = dao.getCustomerById(c1Id)!!
        syncManager.enqueueCustomer(uid, c1, autoDrain = false)

        val u1Id = dao.insertUnit(makeUnit("FRZ/SAF/7001", "Unit Awal", status = "Tersedia", kapasitas = 150))

        syncManager.mergeRemoteCustomersIntoLocal(
            uid,
            listOf(
                CloudCustomer(
                    id = "cust_$c1Id",
                    userId = uid,
                    localId = c1Id,
                    kodePelanggan = "CUST/SAF/7001",
                    namaLengkap = "Remote Stale Name",
                    nomorHp = "628111111111",
                    clientUpdatedAtMillis = 1000L
                )
            )
        )
        assertEquals("Lokal Lebih Baru", dao.getCustomerById(c1Id)?.namaLengkap)

        syncManager.mergeRemoteUnitsIntoLocal(
            uid,
            listOf(
                CloudFreezerUnit(
                    id = "unit_$u1Id",
                    userId = uid,
                    localId = u1Id,
                    kodeUnit = "FRZ/SAF/7001",
                    namaUnit = "Unit Updated From Device B",
                    kapasitasLiter = 150,
                    statusUnit = "Disewa",
                    clientUpdatedAtMillis = System.currentTimeMillis() + 60_000L
                )
            )
        )
        val updatedUnit = dao.getUnitById(u1Id)
        assertEquals("Unit Updated From Device B", updatedUnit?.namaUnit)
        assertEquals("Disewa", updatedUnit?.statusUnit)
    }
}
