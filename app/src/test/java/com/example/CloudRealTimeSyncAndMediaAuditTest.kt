package com.example

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.cloud.CloudChangeType
import com.example.cloud.CloudCustomer
import com.example.cloud.CloudDocumentChange
import com.example.cloud.CloudFirestoreRepository
import com.example.cloud.CloudFreezerUnit
import com.example.cloud.CloudIdMapper
import com.example.cloud.CloudMediaStorageBackend
import com.example.cloud.CloudPaymentProof
import com.example.cloud.CloudPhotoMetadata
import com.example.cloud.CloudRentalTransaction
import com.example.cloud.CloudStoragePhotoManager
import com.example.cloud.CloudSyncAndMigrationManager
import com.example.cloud.CloudTransactionUnitItem
import com.example.cloud.MediaResolveResult
import com.example.data.local.AppDatabase
import com.example.data.local.CloudPhotoJobEntity
import com.example.data.local.CloudSyncQueueEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.DriveCloudQuarantineEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.drive.DatabaseGenerationManager
import com.example.drive.GoogleDriveRestClient
import com.example.drive.GoogleDriveService
import com.example.drive.SyncConflictResolver
import com.example.drive.SyncConsistencyChecker
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CloudRealTimeSyncAndMediaAuditTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: FreezerDao
    private val testUid = "uid_sync_audit_001"

    private class FakeCloudFirestoreRepo : CloudFirestoreRepository() {
        val customers = ConcurrentHashMap<String, CloudCustomer>()
        val units = ConcurrentHashMap<String, CloudFreezerUnit>()
        val transactions = ConcurrentHashMap<String, CloudRentalTransaction>()
        val unitItems = ConcurrentHashMap<String, CloudTransactionUnitItem>()
        val paymentProofs = ConcurrentHashMap<String, CloudPaymentProof>()
        val photoMeta = ConcurrentHashMap<String, CloudPhotoMetadata>()
        val deletedPaths: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val eventTimeline: MutableList<String> = Collections.synchronizedList(mutableListOf())
        @Volatile var failNextUpsert: Boolean = false

        override suspend fun upsertCustomer(customer: CloudCustomer): String {
            if (failNextUpsert) {
                failNextUpsert = false
                throw RuntimeException("Simulated transient Firestore network error")
            }
            val docId = customer.id.ifBlank { CloudIdMapper.customerDocId(customer.localId) }
            customers[docId] = customer.copy(id = docId)
            eventTimeline.add("FIRESTORE_UPSERT_CUSTOMER:$docId")
            return docId
        }

        override suspend fun upsertFreezerUnit(unit: CloudFreezerUnit): String {
            if (failNextUpsert) {
                failNextUpsert = false
                throw RuntimeException("Simulated transient Firestore network error")
            }
            val docId = unit.id.ifBlank { CloudIdMapper.unitDocId(unit.localId) }
            units[docId] = unit.copy(id = docId)
            eventTimeline.add("FIRESTORE_UPSERT_UNIT:$docId:foto=${unit.fotoUnitUrl}")
            return docId
        }

        override suspend fun upsertRentalTransaction(tx: CloudRentalTransaction): String {
            val docId = tx.id.ifBlank { CloudIdMapper.transactionDocId(tx.localId) }
            transactions[docId] = tx.copy(id = docId)
            eventTimeline.add("FIRESTORE_UPSERT_TX:$docId")
            return docId
        }

        override suspend fun upsertTransactionUnitItem(item: CloudTransactionUnitItem): String {
            val docId = item.id.ifBlank { CloudIdMapper.transactionUnitItemDocId(item.localId) }
            unitItems[docId] = item.copy(id = docId)
            return docId
        }

        override suspend fun upsertPaymentProof(proof: CloudPaymentProof): String {
            val docId = proof.id.ifBlank { CloudIdMapper.paymentProofDocId(proof.localId) }
            paymentProofs[docId] = proof.copy(id = docId)
            eventTimeline.add("FIRESTORE_UPSERT_PAYMENT:$docId:foto=${proof.fotoBuktiUrl}")
            return docId
        }

        override suspend fun upsertPhotoMetadata(meta: CloudPhotoMetadata): String {
            val docId = meta.id.ifBlank {
                CloudIdMapper.photoMetaDocId(meta.ownerType, meta.ownerLocalId, meta.photoSlot)
            }
            photoMeta[docId] = meta.copy(id = docId)
            eventTimeline.add("FIRESTORE_UPSERT_PHOTO_META:$docId")
            return docId
        }

        override suspend fun deleteDocument(collectionName: String, docId: String) {
            deletedPaths.add("$collectionName/$docId")
            eventTimeline.add("FIRESTORE_DELETE:$collectionName/$docId")
            when (collectionName) {
                "customers" -> customers.remove(docId)
                "freezer_units" -> units.remove(docId)
                "transactions" -> transactions.remove(docId)
                "transaction_unit_items" -> unitItems.remove(docId)
                "payment_proofs" -> paymentProofs.remove(docId)
                "photo_metadata" -> photoMeta.remove(docId)
            }
        }

        override suspend fun deletePhotoMetadata(docId: String) {
            deleteDocument("photo_metadata", docId)
        }
    }

    private class FakeMediaStorageBackend(
        private val timeline: MutableList<String>,
        var simulateObjectNotFoundOnUpload: Boolean = false,
        var simulateObjectNotFoundOnResolve: Boolean = false,
        var simulateObjectNotFoundOnDelete: Boolean = false
    ) : CloudMediaStorageBackend {
        val uploadedUrls = ConcurrentHashMap<String, String>()
        val deletedPaths: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun uploadAndGetDownloadUrl(
            storagePath: String,
            localFile: File,
            mimeType: String
        ): Result<String> {
            delay(15)
            if (simulateObjectNotFoundOnUpload) {
                return Result.failure(RuntimeException("Object does not exist at location."))
            }
            val url = "https://firebasestorage.googleapis.com/v0/b/sewain-soc/o/${storagePath.replace("/", "%2F")}?alt=media&token=ok"
            uploadedUrls[storagePath] = url
            timeline.add("STORAGE_UPLOAD_DONE:$storagePath")
            return Result.success(url)
        }

        override suspend fun resolveDownloadUrl(storagePath: String): Result<String> {
            if (simulateObjectNotFoundOnResolve) {
                return Result.failure(RuntimeException("Object does not exist at location."))
            }
            val url = uploadedUrls[storagePath]
                ?: return Result.failure(RuntimeException("Object does not exist at location."))
            return Result.success(url)
        }

        override suspend fun deleteObject(storagePath: String): Result<Unit> {
            deletedPaths.add(storagePath)
            timeline.add("STORAGE_DELETE:$storagePath")
            if (simulateObjectNotFoundOnDelete) {
                return Result.failure(RuntimeException("Object does not exist at location."))
            }
            uploadedUrls.remove(storagePath)
            return Result.success(Unit)
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val wmConfig = Configuration.Builder()
            .setMinimumLoggingLevel(Log.ERROR)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, wmConfig)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.freezerDao()
        context.getSharedPreferences("sewain_drive_cloud_mirror_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("saf_soc_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createTempJpeg(name: String): File {
        val file = File(context.cacheDir, name)
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        return file
    }

    private fun sampleUnit(
        uuid: String = UUID.randomUUID().toString(),
        driveId: String = "",
        kodeUnit: String = "FRZ-001",
        namaUnit: String = "Freezer RSA 300L",
        statusUnit: String = "Tersedia",
        fotoUnitUri: String = "",
        syncStatus: String = CloudSyncAndMigrationManager.SYNC_STATE_PENDING_SYNC,
        updatedAtMillis: Long = System.currentTimeMillis(),
        version: Long = 1L
    ): FreezerUnitEntity = FreezerUnitEntity(
        uuid = uuid,
        driveId = driveId,
        kodeUnit = kodeUnit,
        namaUnit = namaUnit,
        jenisUnit = "Chest Freezer",
        owner = "SEWAin Aja",
        hargaPembelian = 3_500_000L,
        kapasitasLiter = 300,
        cicilanPerBulan = 350_000L,
        merek = "RSA",
        model = "CF-310",
        nomorSeri = "SN-${kodeUnit}",
        statusUnit = statusUnit,
        lokasiUnit = "Gudang Solo",
        tanggalPembelian = "2025-01-10",
        fotoUnitUri = fotoUnitUri,
        syncStatus = syncStatus,
        updatedAtMillis = updatedAtMillis,
        version = version
    )

    private fun sampleCustomer(
        uuid: String = UUID.randomUUID().toString(),
        namaLengkap: String = "Budi Santoso",
        nomorHp: String = "08123456789"
    ): CustomerEntity = CustomerEntity(
        uuid = uuid,
        namaLengkap = namaLengkap,
        nik = "3372011001900001",
        nomorHp = nomorHp,
        alamatLengkap = "Jl. Slamet Riyadi No. 10, Surakarta",
        jenisKelamin = "Laki-laki",
        tanggalRegistrasi = "2025-01-15"
    )

    @Test
    fun test1_mediaFirstOrdering_uploadsPhotoAndObtainsDownloadUrlBeforeFirestoreDocWrite() = runBlocking {
        val fakeRepo = FakeCloudFirestoreRepo()
        val fakeStorage = FakeMediaStorageBackend(fakeRepo.eventTimeline)
        val photoMgr = CloudStoragePhotoManager(context, dao, mediaBackend = fakeStorage)
        val syncMgr = CloudSyncAndMigrationManager(
            context = context,
            dao = dao,
            cloudRepo = fakeRepo,
            photoManager = photoMgr,
            networkAvailabilityOverride = { true }
        )

        val localPhoto = createTempJpeg("unit_test_1.jpg")
        val unitId = dao.insertUnit(
            sampleUnit(
                kodeUnit = "FRZ-001",
                namaUnit = "Freezer RSA 300L",
                fotoUnitUri = localPhoto.absolutePath
            )
        )
        val unit = dao.getUnitById(unitId)!!

        // Non-blocking enqueue (autoDrain = false) keeps UI responsive, then explicit drain runs sync
        syncMgr.enqueueUnit(testUid, unit, autoDrain = false)
        val ok = syncMgr.drainPendingQueue(testUid, ignoreBackoff = true)

        assertTrue("Drain should succeed cleanly", ok)
        val cloudUnit = fakeRepo.units[CloudIdMapper.unitDocId(unitId)]
        assertNotNull("Unit must exist in Firestore", cloudUnit)
        assertTrue(
            "Firestore unit fotoUnitUrl must contain HTTPS downloadUrl from Storage BEFORE doc upsert",
            cloudUnit!!.fotoUnitUrl.startsWith("https://firebasestorage.googleapis.com/")
        )

        val storageIdx = fakeRepo.eventTimeline.indexOfFirst { it.startsWith("STORAGE_UPLOAD_DONE:") }
        val firestoreIdx = fakeRepo.eventTimeline.indexOfFirst { it.startsWith("FIRESTORE_UPSERT_UNIT:") }
        assertTrue(
            "Storage upload must occur before Firestore unit write (timeline=${fakeRepo.eventTimeline})",
            storageIdx in 0 until firestoreIdx
        )

        val updatedLocal = dao.getUnitById(unitId)!!
        assertEquals(CloudSyncAndMigrationManager.SYNC_STATE_SYNCED, updatedLocal.syncStatus)
        assertEquals(0, dao.getPendingSyncQueueOnce(testUid).size)
    }

    @Test
    fun test2_crudAndDeleteOutbox_propagatesDeleteToFirestoreAndCleansMediaWithoutStalling() = runBlocking {
        val fakeRepo = FakeCloudFirestoreRepo()
        val fakeStorage = FakeMediaStorageBackend(
            timeline = fakeRepo.eventTimeline,
            simulateObjectNotFoundOnDelete = true // Even if Storage throws "Object does not exist at location", delete must succeed!
        )
        val photoMgr = CloudStoragePhotoManager(context, dao, mediaBackend = fakeStorage)
        val syncMgr = CloudSyncAndMigrationManager(
            context = context,
            dao = dao,
            cloudRepo = fakeRepo,
            photoManager = photoMgr,
            networkAvailabilityOverride = { true }
        )

        val localPhoto = createTempJpeg("unit_delete_1.jpg")
        val unitId = dao.insertUnit(
            sampleUnit(
                kodeUnit = "FRZ-DEL-1",
                namaUnit = "Unit Akan Dihapus",
                fotoUnitUri = localPhoto.absolutePath
            )
        )
        val unit = dao.getUnitById(unitId)!!
        syncMgr.enqueueUnit(testUid, unit, autoDrain = false)
        assertTrue(syncMgr.drainPendingQueue(testUid, ignoreBackoff = true))
        val docId = CloudIdMapper.unitDocId(unitId)
        assertNotNull(fakeRepo.units[docId])

        // Perform soft-delete locally + enqueue Firestore OP_DELETE
        dao.softDeleteUnit(unitId, System.currentTimeMillis(), CloudSyncAndMigrationManager.SYNC_STATE_PENDING_DELETE)
        syncMgr.enqueueDeleteUnit(testUid, unit, autoDrain = false)

        val deleteDrained = syncMgr.drainPendingQueue(testUid, ignoreBackoff = true)
        assertTrue("Delete queue drain must succeed without stalling even when Storage returns Object-Not-Found", deleteDrained)
        assertNull("Firestore document must be deleted", fakeRepo.units[docId])
        assertTrue("Delete path recorded in Firestore", fakeRepo.deletedPaths.contains("freezer_units/$docId"))
        assertTrue("Storage delete attempted", fakeStorage.deletedPaths.isNotEmpty())
        assertTrue("Local cloud_photo_jobs cleaned up", dao.getCloudPhotoJobsForOwnerOnce(testUid, "UNIT", unitId).isEmpty())
        assertEquals(0, dao.getPendingSyncQueueOnce(testUid).size)
    }

    @Test
    fun test3_objectNotFoundAndMissingLocalFile_fallbacksPreventSyncStall() = runBlocking {
        val fakeRepo = FakeCloudFirestoreRepo()
        val fakeStorage = FakeMediaStorageBackend(
            timeline = fakeRepo.eventTimeline,
            simulateObjectNotFoundOnUpload = true,
            simulateObjectNotFoundOnResolve = true
        )
        val photoMgr = CloudStoragePhotoManager(context, dao, mediaBackend = fakeStorage)
        val syncMgr = CloudSyncAndMigrationManager(
            context = context,
            dao = dao,
            cloudRepo = fakeRepo,
            photoManager = photoMgr,
            networkAvailabilityOverride = { true }
        )

        // Case A: Upload encounters "Object does not exist at location" -> falls back cleanly to DRIVE_STAGE URL
        val validPhoto = createTempJpeg("unit_fallback_1.jpg")
        val unit1Id = dao.insertUnit(
            sampleUnit(
                kodeUnit = "FRZ-FB-1",
                namaUnit = "Unit Fallback Storage",
                fotoUnitUri = validPhoto.absolutePath
            )
        )
        syncMgr.enqueueUnit(testUid, dao.getUnitById(unit1Id)!!, autoDrain = false)
        assertTrue("Drain must not stall on Object-Not-Found from Storage", syncMgr.drainPendingQueue(testUid, ignoreBackoff = true))
        val fbDoc = fakeRepo.units[CloudIdMapper.unitDocId(unit1Id)]
        assertNotNull(fbDoc)
        assertTrue("Fallback URL uses staged gdrive:// path", fbDoc!!.fotoUnitUrl.startsWith("gdrive://"))

        // Case B: Missing local source file -> marks single job MISSING_LOCAL and still syncs unit doc cleanly
        val unit2Id = dao.insertUnit(
            sampleUnit(
                kodeUnit = "FRZ-MISS-2",
                namaUnit = "Unit Foto Hilang",
                fotoUnitUri = "/nonexistent/path/missing_photo_999.jpg"
            )
        )
        syncMgr.enqueueUnit(testUid, dao.getUnitById(unit2Id)!!, autoDrain = false)
        assertTrue("Drain must continue even when local source photo is missing", syncMgr.drainPendingQueue(testUid, ignoreBackoff = true))
        assertNotNull("Unit 2 must still sync to Firestore", fakeRepo.units[CloudIdMapper.unitDocId(unit2Id)])
        val unit2Jobs = dao.getCloudPhotoJobsForOwnerOnce(testUid, "UNIT", unit2Id)
        assertEquals(1, unit2Jobs.size)
        assertEquals(CloudStoragePhotoManager.STATUS_MISSING_LOCAL, unit2Jobs.first().status)

        // Case C: resolveAccessibleMediaUrl handles "Object does not exist at location" with local fallback & MissingMedia
        val resolvedWithLocal = photoMgr.resolveAccessibleMediaUrl(
            storagePath = "users/$testUid/units/unit_99/photos/missing.jpg",
            existingUrl = "",
            localFallbackPath = validPhoto.absolutePath
        )
        assertTrue("Should fall back to local file when remote object does not exist", resolvedWithLocal is MediaResolveResult.LocalFallback)

        val resolvedMissingAll = photoMgr.resolveAccessibleMediaUrl(
            storagePath = "users/$testUid/units/unit_99/photos/missing.jpg",
            existingUrl = "",
            localFallbackPath = "/does/not/exist.jpg"
        )
        assertTrue("Should return MissingMedia without throwing when both cloud & local are absent", resolvedMissingAll is MediaResolveResult.MissingMedia)
    }

    @Test
    fun test4_incrementalTwoWaySync_antiEcho_metadataPreservation_andTombstoneProtection() = runBlocking {
        val fakeRepo = FakeCloudFirestoreRepo()
        val fakeStorage = FakeMediaStorageBackend(fakeRepo.eventTimeline)
        val photoMgr = CloudStoragePhotoManager(context, dao, mediaBackend = fakeStorage)
        val syncMgr = CloudSyncAndMigrationManager(
            context = context,
            dao = dao,
            cloudRepo = fakeRepo,
            photoManager = photoMgr,
            networkAvailabilityOverride = { true }
        )

        val fixedUuid = "11111111-2222-4333-8444-555555555555"
        val unitId = dao.insertUnit(
            sampleUnit(
                uuid = fixedUuid,
                driveId = "drive_file_xyz",
                kodeUnit = "FRZ-SYNC-1",
                namaUnit = "Freezer Lama",
                statusUnit = "Tersedia",
                syncStatus = CloudSyncAndMigrationManager.SYNC_STATE_SYNCED,
                updatedAtMillis = 1000L,
                version = 1L
            )
        )
        val docId = CloudIdMapper.unitDocId(unitId)

        // 1. Latency-compensated local write (hasPendingWrites = true) must be IGNORED (anti-echo)
        syncMgr.applyRemoteUnitChanges(
            uid = testUid,
            changes = listOf(
                CloudDocumentChange(
                    type = CloudChangeType.MODIFIED,
                    docId = docId,
                    data = CloudFreezerUnit(
                        id = docId,
                        userId = testUid,
                        localId = unitId,
                        kodeUnit = "FRZ-SYNC-1",
                        namaUnit = "Should Be Ignored Echo",
                        clientUpdatedAtMillis = 9999L,
                        version = 2L
                    ),
                    hasPendingWrites = true
                )
            )
        )
        assertEquals("Freezer Lama", dao.getUnitById(unitId)!!.namaUnit)

        // 2. Real remote MODIFIED with newer timestamp merges cleanly AND preserves original UUID, driveId, and SYNCED status
        syncMgr.applyRemoteUnitChanges(
            uid = testUid,
            changes = listOf(
                CloudDocumentChange(
                    type = CloudChangeType.MODIFIED,
                    docId = docId,
                    data = CloudFreezerUnit(
                        id = docId,
                        userId = testUid,
                        localId = unitId,
                        kodeUnit = "FRZ-SYNC-1",
                        namaUnit = "Freezer Diperbarui Cloud",
                        statusUnit = "Disewa",
                        clientUpdatedAtMillis = 5000L,
                        version = 2L
                    ),
                    hasPendingWrites = false
                )
            )
        )
        val mergedUnit = dao.getUnitById(unitId)!!
        assertEquals("Freezer Diperbarui Cloud", mergedUnit.namaUnit)
        assertEquals("Disewa", mergedUnit.statusUnit)
        assertEquals("UUID must NEVER change during remote sync", fixedUuid, mergedUnit.uuid)
        assertEquals("DriveId must be preserved during remote sync", "drive_file_xyz", mergedUnit.driveId)
        assertEquals("Remote-synced row must remain SYNCED (no infinite push loop)", CloudSyncAndMigrationManager.SYNC_STATE_SYNCED, mergedUnit.syncStatus)

        // 3. Stale remote update (older timestamp) must NOT overwrite newer local data (LWW)
        syncMgr.applyRemoteUnitChanges(
            uid = testUid,
            changes = listOf(
                CloudDocumentChange(
                    type = CloudChangeType.MODIFIED,
                    docId = docId,
                    data = CloudFreezerUnit(
                        id = docId,
                        userId = testUid,
                        localId = unitId,
                        kodeUnit = "FRZ-SYNC-1",
                        namaUnit = "Stale Old Name",
                        clientUpdatedAtMillis = 2000L,
                        version = 1L
                    ),
                    hasPendingWrites = false
                )
            )
        )
        assertEquals("Freezer Diperbarui Cloud", dao.getUnitById(unitId)!!.namaUnit)

        // 4. Local soft-deleted unit must NEVER resurrect when a stale remote ADDED/MODIFIED arrives
        dao.softDeleteUnit(unitId, updatedAt = 8000L, syncStatus = CloudSyncAndMigrationManager.SYNC_STATE_PENDING_DELETE)
        syncMgr.applyRemoteUnitChanges(
            uid = testUid,
            changes = listOf(
                CloudDocumentChange(
                    type = CloudChangeType.ADDED,
                    docId = docId,
                    data = CloudFreezerUnit(
                        id = docId,
                        userId = testUid,
                        localId = unitId,
                        kodeUnit = "FRZ-SYNC-1",
                        namaUnit = "Zombie Resurrection Attempt",
                        clientUpdatedAtMillis = 9000L,
                        version = 3L
                    ),
                    hasPendingWrites = false
                )
            )
        )
        val afterZombieAttempt = dao.getUnitByIdIncludingDeleted(unitId)!!
        assertTrue("Locally deleted unit must NEVER resurrect", afterZombieAttempt.isDeleted)

        // 5. Remote REMOVED event soft-deletes an active local unit when no newer local pending upsert exists
        val unit2Id = dao.insertUnit(
            sampleUnit(
                kodeUnit = "FRZ-REM-2",
                namaUnit = "Unit Dihapus Dari Device Lain",
                syncStatus = CloudSyncAndMigrationManager.SYNC_STATE_SYNCED,
                updatedAtMillis = 1000L
            )
        )
        val doc2Id = CloudIdMapper.unitDocId(unit2Id)
        syncMgr.applyRemoteUnitChanges(
            uid = testUid,
            changes = listOf(
                CloudDocumentChange(
                    type = CloudChangeType.REMOVED,
                    docId = doc2Id,
                    data = null,
                    hasPendingWrites = false
                )
            )
        )
        assertNull("Unit 2 should no longer appear in active queries", dao.getUnitById(unit2Id))
        assertTrue("Unit 2 should be soft-deleted locally", dao.getUnitByIdIncludingDeleted(unit2Id)!!.isDeleted)
    }

    @Test
    fun test5_twoWayTransactionsAndPaymentProofs_andConcurrentPushPullMutexSafety() = runBlocking {
        val fakeRepo = FakeCloudFirestoreRepo()
        val fakeStorage = FakeMediaStorageBackend(fakeRepo.eventTimeline)
        val photoMgr = CloudStoragePhotoManager(context, dao, mediaBackend = fakeStorage)
        val syncMgr = CloudSyncAndMigrationManager(
            context = context,
            dao = dao,
            cloudRepo = fakeRepo,
            photoManager = photoMgr,
            networkAvailabilityOverride = { true }
        )

        val custId = dao.insertCustomer(sampleCustomer(namaLengkap = "Budi Santoso", nomorHp = "08123456789"))
        val unitId = dao.insertUnit(sampleUnit(kodeUnit = "FRZ-TX-1", namaUnit = "Freezer 200L"))

        // Remote ADDED for Transaction & PaymentProof from another device
        syncMgr.applyRemoteTransactionChanges(
            uid = testUid,
            changes = listOf(
                CloudDocumentChange(
                    type = CloudChangeType.ADDED,
                    docId = "tx_77",
                    data = CloudRentalTransaction(
                        id = "tx_77",
                        userId = testUid,
                        localId = 77L,
                        nomorInvoice = "INV-REMOTE-77",
                        customerId = custId,
                        namaPelangganSnapshot = "Budi Santoso",
                        unitId = unitId,
                        namaUnitSnapshot = "Freezer 200L",
                        kodeUnitSnapshot = "FRZ-TX-1",
                        totalTagihan = 450_000L,
                        statusPembayaran = "Lunas",
                        clientUpdatedAtMillis = 5000L,
                        version = 1L
                    )
                )
            )
        )
        val syncedTx = dao.getTransactionById(77L)
        assertNotNull("Remote transaction should be inserted locally", syncedTx)
        assertEquals("INV-REMOTE-77", syncedTx!!.nomorInvoice)
        assertEquals(CloudSyncAndMigrationManager.SYNC_STATE_SYNCED, syncedTx.syncStatus)

        syncMgr.applyRemotePaymentProofChanges(
            uid = testUid,
            changes = listOf(
                CloudDocumentChange(
                    type = CloudChangeType.ADDED,
                    docId = "pay_88",
                    data = CloudPaymentProof(
                        id = "pay_88",
                        userId = testUid,
                        localId = 88L,
                        transactionId = 77L,
                        nominalPembayaran = 450_000L,
                        metodePembayaran = "QRIS",
                        fotoBuktiUrl = "https://firebasestorage.googleapis.com/v0/b/sewain-soc/o/pay_88.jpg?alt=media",
                        clientUpdatedAtMillis = 5500L,
                        version = 1L
                    )
                )
            )
        )
        val syncedPay = dao.getPaymentProofById(88L)
        assertNotNull("Remote payment proof should be inserted locally", syncedPay)
        assertEquals(450_000L, syncedPay!!.nominalPembayaran)
        assertEquals(CloudSyncAndMigrationManager.SYNC_STATE_SYNCED, syncedPay.syncStatus)

        // Concurrent Push + Pull stress test under shared syncMutex (must complete with zero crash/corruption)
        val customer = dao.getCustomerById(custId)!!
        syncMgr.enqueueCustomer(testUid, customer, autoDrain = false)
        val jobs = listOf(
            async { syncMgr.drainPendingQueue(testUid, ignoreBackoff = true) },
            async {
                syncMgr.applyRemoteCustomerChanges(
                    testUid,
                    listOf(
                        CloudDocumentChange(
                            type = CloudChangeType.MODIFIED,
                            docId = CloudIdMapper.customerDocId(custId),
                            data = CloudCustomer(
                                id = CloudIdMapper.customerDocId(custId),
                                userId = testUid,
                                localId = custId,
                                namaLengkap = "Budi Santoso Remote",
                                nomorHp = "08123456789",
                                clientUpdatedAtMillis = System.currentTimeMillis() + 60_000L,
                                version = 2L
                            )
                        )
                    )
                )
            }
        )
        jobs.awaitAll()
        assertNotNull(dao.getCustomerById(custId))
    }

    @Test
    fun test6_crashRecovery_exponentialBackoff_andQuarantineAutoRecovery() = runBlocking {
        val fakeRepo = FakeCloudFirestoreRepo()
        val fakeStorage = FakeMediaStorageBackend(fakeRepo.eventTimeline)
        val photoMgr = CloudStoragePhotoManager(context, dao, mediaBackend = fakeStorage)
        var online = false
        val syncMgr = CloudSyncAndMigrationManager(
            context = context,
            dao = dao,
            cloudRepo = fakeRepo,
            photoManager = photoMgr,
            networkAvailabilityOverride = { online }
        )

        // Verify exponential backoff progression
        assertEquals(0L, CloudSyncAndMigrationManager.computeExponentialBackoffMillis(0))
        assertEquals(2_000L, CloudSyncAndMigrationManager.computeExponentialBackoffMillis(1))
        assertEquals(4_000L, CloudSyncAndMigrationManager.computeExponentialBackoffMillis(2))
        assertEquals(8_000L, CloudSyncAndMigrationManager.computeExponentialBackoffMillis(3))
        assertEquals(16_000L, CloudSyncAndMigrationManager.computeExponentialBackoffMillis(4))
        assertEquals(60_000L, CloudSyncAndMigrationManager.computeExponentialBackoffMillis(8))

        // Simulate crash-stranded SYNCING queue item + UPLOADING photo job
        val validPhoto = createTempJpeg("crash_recovery_photo.jpg")
        val unitUuid = UUID.randomUUID().toString()
        val unitId = dao.insertUnit(
            sampleUnit(
                uuid = unitUuid,
                kodeUnit = "FRZ-CRASH-1",
                namaUnit = "Unit Crash Recovery",
                fotoUnitUri = validPhoto.absolutePath
            )
        )
        val docId = CloudIdMapper.unitDocId(unitId)
        dao.upsertCloudSyncQueueItem(
            CloudSyncQueueEntity(
                uid = testUid,
                entityType = CloudSyncAndMigrationManager.TYPE_UNIT,
                entityLocalId = unitId,
                cloudDocId = docId,
                operation = CloudSyncAndMigrationManager.OP_UPSERT,
                status = CloudSyncAndMigrationManager.SYNC_STATE_SYNCING,
                clientUpdatedAtMillis = System.currentTimeMillis()
            )
        )
        dao.upsertCloudPhotoJob(
            CloudPhotoJobEntity(
                uid = testUid,
                ownerType = "UNIT",
                ownerLocalId = unitId,
                ownerCloudId = docId,
                photoSlot = "FOTO_UNIT",
                localFilePath = validPhoto.absolutePath,
                storagePath = "users/$testUid/units/$docId/photos/crash_test.jpg",
                status = CloudStoragePhotoManager.STATUS_UPLOADING
            )
        )

        // While offline, drain returns false and sets waiting network status (items stay safely queued)
        assertFalse(syncMgr.drainPendingQueue(testUid, ignoreBackoff = true))
        assertEquals(CloudSyncAndMigrationManager.STATUS_WAITING_NETWORK, syncMgr.syncStatus.value)

        // Come back online -> transient failure first -> retry with backoff -> success!
        online = true
        fakeRepo.failNextUpsert = true
        assertFalse(syncMgr.drainPendingQueue(testUid, ignoreBackoff = true))
        assertEquals(CloudSyncAndMigrationManager.STATUS_FAILED, syncMgr.syncStatus.value)
        val failedQueue = dao.getPendingSyncQueueOnce(testUid)
        assertEquals(1, failedQueue.size)
        assertEquals(CloudSyncAndMigrationManager.SYNC_STATE_FAILED, failedQueue.first().status)
        assertEquals(1, failedQueue.first().retryCount)

        // Immediate retry (ignoreBackoff = true simulates reconnect/manual sync) succeeds cleanly
        assertTrue(syncMgr.drainPendingQueue(testUid, ignoreBackoff = true))
        assertEquals(CloudSyncAndMigrationManager.STATUS_SYNCED, syncMgr.syncStatus.value)
        assertNotNull(fakeRepo.units[docId])
        assertEquals(0, dao.getPendingSyncQueueOnce(testUid).size)

        // Quarantine Self-Healing Recovery:
        // Active UUID in valid generation -> AUTO_RECOVERED
        // Invalidated post-reset generation -> stays QUARANTINED
        val genMgr = DatabaseGenerationManager(context)
        val rotation = genMgr.rotateGenerationOnFactoryReset(isCloudAlsoReset = false)
        val revokedGen = rotation.previousGenerationId
        val currentGen = rotation.newGenerationId
        val driveClient = GoogleDriveRestClient()
        val driveService = GoogleDriveService(context, driveClient)
        val checker = SyncConsistencyChecker(context, dao, driveService, genMgr, SyncConflictResolver(dao, genMgr))

        dao.upsertDriveCloudQuarantine(
            DriveCloudQuarantineEntity(
                driveFileId = "q_active_1",
                fileName = "UNIT_$unitUuid.json",
                classification = "OLD_UNIT_FILE",
                recordUuid = unitUuid,
                datasetGenerationId = currentGen,
                ownershipVerified = true,
                status = "QUARANTINED"
            )
        )
        dao.upsertDriveCloudQuarantine(
            DriveCloudQuarantineEntity(
                driveFileId = "q_revoked_2",
                fileName = "UNIT_revoked.json",
                classification = "PREVIOUS_GENERATION",
                recordUuid = unitUuid,
                datasetGenerationId = revokedGen,
                ownershipVerified = true,
                status = "QUARANTINED"
            )
        )

        val recoveredCount = checker.autoRecoverEligibleQuarantineItems().getOrThrow()
        assertEquals("Only the active valid-generation item should auto-recover", 1, recoveredCount)
        val remainingActive = dao.getActiveDriveCloudQuarantineOnce()
        assertEquals(1, remainingActive.size)
        assertEquals("q_revoked_2", remainingActive.first().driveFileId)
    }
}
