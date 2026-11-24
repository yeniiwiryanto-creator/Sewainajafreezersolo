package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.CloudFirestoreRepository
import com.example.cloud.CloudFirestoreWipeReport
import com.example.cloud.CloudMediaStorageBackend
import com.example.cloud.CloudStoragePhotoManager
import com.example.cloud.CloudSyncAndMigrationManager
import com.example.cloud.StorageWipeResult
import com.example.data.local.AppDatabase
import com.example.data.local.CloudPhotoJobEntity
import com.example.data.local.CloudSyncQueueEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerPhotoEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.repository.FreezerRepository
import com.example.reset.HardResetWipeManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardResetWipeVerificationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: FreezerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FreezerRepository(db.freezerDao())
        // Ensure clean sentinel state before each test
        context.getSharedPreferences(HardResetWipeManager.SENTINEL_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private class FakeWipeableFirestoreRepository(
        initialDocsByCollection: Map<String, MutableList<String>>
    ) : CloudFirestoreRepository() {
        val docsByCollection: MutableMap<String, MutableList<String>> =
            initialDocsByCollection.mapValues { it.value.toMutableList() }.toMutableMap()
        var rootProfileExists: Boolean = true
        var wipeCallCount: Int = 0

        override suspend fun wipeAllUserCollectionsAndVerifyEmpty(
            uid: String,
            batchSize: Int
        ): CloudFirestoreWipeReport {
            wipeCallCount++
            var deleted = 0
            val wiped = mutableListOf<String>()
            for (col in ALL_USER_SUBCOLLECTIONS) {
                val list = docsByCollection[col]
                if (list != null) {
                    deleted += list.size
                    list.clear()
                }
                wiped += col
            }
            if (rootProfileExists) {
                rootProfileExists = false
                deleted++
            }
            val remaining = docsByCollection.values.sumOf { it.size } + if (rootProfileExists) 1 else 0
            return CloudFirestoreWipeReport(
                deletedDocumentCount = deleted,
                remainingDocumentCount = remaining,
                wipedCollections = wiped
            )
        }
    }

    private class FakeRecursiveStorageBackend(
        initialPaths: List<String>,
        private val missingGhostPaths: Set<String>
    ) : CloudMediaStorageBackend {
        val storedPaths = initialPaths.toMutableList()
        var wipeCallCount: Int = 0

        override suspend fun uploadAndGetDownloadUrl(
            storagePath: String,
            localFile: File,
            mimeType: String
        ): Result<String> = Result.success("https://fake.storage/$storagePath")

        override suspend fun resolveDownloadUrl(storagePath: String): Result<String> =
            Result.success("https://fake.storage/$storagePath")

        override suspend fun deleteObject(storagePath: String): Result<Unit> {
            storedPaths.remove(storagePath)
            return Result.success(Unit)
        }

        override suspend fun listAllAndDeleteRecursively(rootPrefix: String): Result<StorageWipeResult> {
            wipeCallCount++
            val matching = storedPaths.filter { it.startsWith(rootPrefix) }.toList()
            var deleted = 0
            var skippedNotFound = 0

            // Simulate iterating every listed object including a broken/missing ghost reference
            // throwing "Object does not exist at location" (ERROR_OBJECT_NOT_FOUND) without aborting loop.
            for (path in matching) {
                try {
                    if (path in missingGhostPaths) {
                        throw IllegalStateException("Object does not exist at location (404): $path")
                    }
                    storedPaths.remove(path)
                    deleted++
                } catch (e: Exception) {
                    if (CloudStoragePhotoManager.isObjectNotFoundOrUnconfigured(e)) {
                        storedPaths.remove(path)
                        skippedNotFound++
                    } else {
                        throw e
                    }
                }
            }
            val remaining = storedPaths.count { it.startsWith(rootPrefix) }
            return Result.success(
                StorageWipeResult(
                    deletedObjectCount = deleted,
                    skippedNotFoundCount = skippedNotFound,
                    remainingObjectCount = remaining
                )
            )
        }
    }

    @Test
    fun zeroRemnantHardReset_wipesLocalDbSequencesFilesPrefsFirestoreAndStorage_andPreventsAutoReseed() = runBlocking {
        val uid = "uid_sewainajafreezersolo"
        val dao = db.freezerDao()

        // 1. Seed rich local SQLite tables (advancing autoincrement counters)
        val unit1Id = dao.insertUnit(
            FreezerUnitEntity(
                kodeUnit = "SAF-001",
                namaUnit = "Freezer Lama 1",
                jenisUnit = "Chest Freezer",
                owner = "Internal",
                hargaPembelian = 3000000L,
                kapasitasLiter = 200,
                cicilanPerBulan = 300000L,
                merek = "RSA",
                model = "CF-210",
                nomorSeri = "SN-001",
                statusUnit = "Tersedia",
                lokasiUnit = "Solo",
                tanggalPembelian = "2025-01-01"
            )
        )
        val unit2Id = dao.insertUnit(
            FreezerUnitEntity(
                kodeUnit = "SAF-002",
                namaUnit = "Freezer Lama 2",
                jenisUnit = "Chest Freezer",
                owner = "Internal",
                hargaPembelian = 3500000L,
                kapasitasLiter = 300,
                cicilanPerBulan = 350000L,
                merek = "GEA",
                model = "AB-318",
                nomorSeri = "SN-002",
                statusUnit = "Disewa",
                lokasiUnit = "Surakarta",
                tanggalPembelian = "2025-02-01"
            )
        )
        val custId = dao.insertCustomer(
            CustomerEntity(
                namaLengkap = "Pelanggan Lama",
                nik = "3372010101900001",
                nomorHp = "08123456789",
                alamatLengkap = "Jl. Slamet Riyadi No. 1, Solo",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = "2026-01-01"
            )
        )
        val txId = dao.insertTransaction(
            RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0009",
                tanggalPemesanan = "2026-02-01",
                customerId = custId,
                namaPelangganSnapshot = "Pelanggan Lama",
                unitId = unit2Id,
                namaUnitSnapshot = "Freezer Lama 2",
                kodeUnitSnapshot = "SAF-002",
                hargaSewa = 300000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-02-01",
                tanggalAkhirSewa = "2026-03-03",
                tanggalJatuhTempo = "2026-03-03",
                totalTagihan = 300000L,
                nominalPembayaran = 150000L,
                statusPembayaran = "Dibayar Sebagian",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "QRIS"
            )
        )
        dao.insertPaymentProof(
            PaymentProofEntity(
                transactionId = txId,
                tanggalPembayaran = "2026-02-01",
                nominalPembayaran = 150000L,
                metodePembayaran = "QRIS"
            )
        )
        dao.insertCustomerPhoto(
            CustomerPhotoEntity(
                customerId = custId,
                kategori = "KTP",
                namaDokumentasi = "KTP Depan",
                filePath = "/tmp/ktp.jpg"
            )
        )
        dao.upsertCloudSyncQueueItem(
            CloudSyncQueueEntity(
                uid = uid,
                entityType = "UNIT",
                entityLocalId = unit1Id,
                cloudDocId = "unit_1"
            )
        )
        dao.upsertCloudPhotoJob(
            CloudPhotoJobEntity(
                uid = uid,
                ownerType = "UNIT",
                ownerLocalId = unit1Id,
                ownerCloudId = "unit_1",
                photoSlot = "FOTO_UNIT",
                localFilePath = "/tmp/unit_1.jpg",
                storagePath = "users/$uid/units/unit_1/photos/unit_1.jpg"
            )
        )
        assertTrue("Autoincrement should have advanced before reset", unit2Id >= 2L)

        // 2. Seed local files including unit_*_foto_unit_*.jpg, cache, external files & datastore
        val unitPhotoFile = File(context.filesDir, "photos/unit_2_foto_unit_17300001.jpg").apply {
            parentFile?.mkdirs()
            writeText("fake-jpeg-bytes")
        }
        val cacheThumbFile = File(context.cacheDir, "thumbnails/unit_2_thumb.webp").apply {
            parentFile?.mkdirs()
            writeText("fake-thumb-bytes")
        }
        val externalInvoiceFile = File(context.getExternalFilesDir(null), "SEWAIN_AJA_FREEZER/invoices/INV_009.pdf").apply {
            parentFile?.mkdirs()
            writeText("fake-pdf-bytes")
        }
        val datastoreFile = File(context.filesDir, "datastore/sync_state.preferences_pb").apply {
            parentFile?.mkdirs()
            writeText("fake-ds")
        }
        assertTrue(unitPhotoFile.exists())
        assertTrue(cacheThumbFile.exists())
        assertTrue(externalInvoiceFile.exists())
        assertTrue(datastoreFile.exists())

        // 3. Seed SharedPreferences keys (sync tokens, timestamps, quarantine flags)
        val mainPrefs = context.getSharedPreferences("saf_soc_prefs", Context.MODE_PRIVATE)
        mainPrefs.edit()
            .putString("company_name", "Old Company")
            .putLong("last_sync_timestamp", 1731632400000L)
            .putBoolean("quarantine_active", true)
            .commit()
        val mirrorPrefs = context.getSharedPreferences("sewain_drive_cloud_mirror_prefs", Context.MODE_PRIVATE)
        mirrorPrefs.edit()
            .putString("oauth_token", "stale-token-123")
            .putInt("dataset_generation", 5)
            .commit()

        // 4. Seed Fake Cloud Firestore (all 13 subcollections) + Fake Firebase Storage (including a 404 ghost ref)
        val initialFirestoreDocs = CloudFirestoreRepository.ALL_USER_SUBCOLLECTIONS.associateWith { col ->
            mutableListOf("${col}_doc_1", "${col}_doc_2")
        }
        val fakeFirestore = FakeWipeableFirestoreRepository(initialFirestoreDocs)
        val ghostPath = "users/$uid/units/unit_99/photos/unit_99_foto_unit_broken.jpg"
        val fakeStorageBackend = FakeRecursiveStorageBackend(
            initialPaths = listOf(
                "users/$uid/units/unit_1/photos/unit_1_foto_unit_111.jpg",
                ghostPath,
                "users/$uid/customers/cust_1/ktp/ktp_depan.jpg",
                "users/$uid/transactions/tx_1/payment_proofs/proof_1.jpg"
            ),
            missingGhostPaths = setOf(ghostPath)
        )
        val photoManager = CloudStoragePhotoManager(context, dao, mediaBackend = fakeStorageBackend)
        val syncManager = CloudSyncAndMigrationManager(context, dao, fakeFirestore, photoManager) { true }

        val hardResetManager = HardResetWipeManager(
            context = context,
            appDatabase = db,
            cloudFirestoreRepo = fakeFirestore,
            cloudPhotoManager = photoManager,
            cloudSyncManager = syncManager
        )

        assertTrue("Startup hard reset should be required before first wipe", hardResetManager.isStartupHardResetRequired(uid))

        // 5. Execute Zero-Remnant Hard Reset
        val report = hardResetManager.executeZeroRemnantHardReset(
            authenticatedUid = uid,
            forceManualRun = false
        )

        // Verify full zero-remnant report
        assertTrue("Hard reset must be fully verified zero-remnant", report.isFullyVerifiedZeroRemnant)
        assertEquals(0, report.localRemainingTableRows)
        assertEquals(0, report.localRemainingFilesCount)
        assertTrue("Expected at least 4 deleted local files", report.localDeletedFilesCount >= 4)
        assertEquals(0, report.firestoreRemainingDocsCount)
        assertEquals(27, report.firestoreDeletedDocsCount) // 13 * 2 + 1 root profile doc
        assertEquals(3, report.storageDeletedObjectsCount)
        assertEquals(1, report.storageSkippedNotFoundCount)
        assertEquals(0, report.storageRemainingObjectsCount)
        assertFalse("Sync manager must be resumed after verified reset", syncManager.isPausedForHardReset())

        // Verify local files completely gone
        assertFalse(unitPhotoFile.exists())
        assertFalse(cacheThumbFile.exists())
        assertFalse(externalInvoiceFile.exists())
        assertFalse(datastoreFile.exists())

        // Verify all app SharedPreferences cleared
        assertTrue(context.getSharedPreferences("saf_soc_prefs", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(context.getSharedPreferences("sewain_drive_cloud_mirror_prefs", Context.MODE_PRIVATE).all.isEmpty())

        // Verify Room tables empty
        assertTrue(dao.getAllUnitsOnce().isEmpty())
        assertTrue(dao.getAllCustomersOnce().isEmpty())
        assertTrue(dao.getAllTransactionsOnce().isEmpty())
        assertTrue(dao.getAllPaymentProofsOnce().isEmpty())

        // Verify ensureInitialDataSeeded() NEVER auto-reseeds dummy data
        repository.ensureInitialDataSeeded()
        assertTrue("No sample units should be seeded", dao.getAllUnitsOnce().isEmpty())
        assertTrue("No sample customers should be seeded", dao.getAllCustomersOnce().isEmpty())
        assertTrue("No sample transactions should be seeded", dao.getAllTransactionsOnce().isEmpty())

        // Verify SQLite AUTOINCREMENT primary keys were reset back to 0 (first fresh insert gets id == 1)
        val freshUnitId = dao.insertUnit(
            FreezerUnitEntity(
                kodeUnit = "NEW-001",
                namaUnit = "Unit Baru Pasca Reset",
                jenisUnit = "Chest Freezer",
                owner = "Internal",
                hargaPembelian = 3000000L,
                kapasitasLiter = 200,
                cicilanPerBulan = 300000L,
                merek = "RSA",
                model = "CF-210",
                nomorSeri = "SN-NEW-001",
                statusUnit = "Tersedia",
                lokasiUnit = "Solo",
                tanggalPembelian = "2026-11-21"
            )
        )
        assertEquals("Autoincrement PK must restart at 1 after hard reset", 1L, freshUnitId)

        // 6. Verify one-shot sentinel prevents wiping newly entered user data on subsequent app launch!
        assertFalse("Startup hard reset must NOT be required on subsequent launch", hardResetManager.isStartupHardResetRequired(uid))
        val secondRunReport = hardResetManager.executeZeroRemnantHardReset(
            authenticatedUid = uid,
            forceManualRun = false
        )
        assertFalse("Second startup launch must skip wipe", secondRunReport.localWipeExecuted)
        assertFalse("Second startup launch must skip cloud wipe", secondRunReport.cloudWipeExecuted)
        assertEquals("Firestore wipe must only have run once", 1, fakeFirestore.wipeCallCount)
        assertEquals("Storage wipe must only have run once", 1, fakeStorageBackend.wipeCallCount)
        assertEquals("Newly entered user unit must remain intact", 1, dao.getAllUnitsOnce().size)
        assertEquals("NEW-001", dao.getAllUnitsOnce().first().kodeUnit)
    }
}
