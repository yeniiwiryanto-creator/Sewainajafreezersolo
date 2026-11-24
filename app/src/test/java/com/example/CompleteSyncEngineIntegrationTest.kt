package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.backup.RestoreMode
import com.example.backup.StructuredBackupEngine
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.ConflictResolutionStrategy
import com.example.drive.DatabaseGenerationManager
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.GoogleDriveRestClient
import com.example.drive.GoogleDriveService
import com.example.drive.MirrorOutboxCodec
import com.example.drive.SyncConflictResolver
import com.example.drive.SyncConsistencyChecker
import com.example.drive.SyncQueueManager
import com.example.drive.SyncRepository
import com.example.drive.SyncWorker
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CompleteSyncEngineIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: FreezerDao
    private lateinit var repository: FreezerRepository
    private lateinit var generationManager: DatabaseGenerationManager
    private lateinit var driveService: GoogleDriveService
    private lateinit var queueManager: SyncQueueManager
    private lateinit var conflictResolver: SyncConflictResolver
    private lateinit var consistencyChecker: SyncConsistencyChecker
    private lateinit var syncRepository: SyncRepository
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeBackend: FullCloudDriveBackend

    private val dbName = "complete_sync_engine_integration.db"

    private class FullCloudDriveBackend {
        data class DriveObj(
            val id: String,
            var name: String,
            var mimeType: String,
            var parentId: String,
            var rawBody: ByteArray,
            var jsonPayloadText: String,
            var trashed: Boolean = false,
            var checksum: String = "",
            var appId: String = "",
            var applicationId: String = "",
            var datasetId: String = "",
            var generationId: String = "",
            var recordUuid: String = "",
            var entityType: String = "",
            var revision: Long = 1L,
            var schemaVersion: Int = 25,
            var syncTimestamp: Long = 0L
        )

        val storage = ConcurrentHashMap<String, DriveObj>()
        private val idSeq = AtomicInteger(200)

        fun activeOperationalFiles(): List<DriveObj> =
            storage.values.filter { obj ->
                !obj.trashed &&
                    obj.mimeType != GoogleDriveRestClient.FOLDER_MIME &&
                    obj.name != GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME &&
                    obj.name !in setOf("customers.json", "units.json", "transactions.json", "payments.json", "APP_SETTINGS.json") &&
                    !obj.name.endsWith(".zip", ignoreCase = true)
            }

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                val method = request.method ?: "GET"
                val contentType = request.getHeader("Content-Type").orEmpty()
                val rawBody = request.body.readByteArray()
                val bodyText = String(rawBody, Charsets.UTF_8)

                if (path.startsWith("/upload/drive/v3/files")) {
                    val isPatch = method.equals("PATCH", ignoreCase = true)
                    val (metaJson, payloadText, payloadBytes) = parseMultipartBody(contentType, bodyText, rawBody)
                    val name = metaJson.optString("name", "unnamed.dat")
                    val mimeType = metaJson.optString("mimeType", "application/json")
                    val parentsArr = metaJson.optJSONArray("parents")
                    val parentId = if (parentsArr != null && parentsArr.length() > 0) parentsArr.optString(0, "root") else "root"
                    val appProps = metaJson.optJSONObject("appProperties")

                    val checksum = appProps?.optString("checksum", appProps.optString("checksumSha256", "")).orEmpty()
                    val appIdProp = appProps?.optString("appId", "").orEmpty()
                    val applicationIdProp = appProps?.optString("applicationId", "").orEmpty()
                    val dsIdProp = appProps?.optString("datasetId", "").orEmpty()
                    val genId = appProps?.optString("generation_id", appProps.optString("datasetGeneration", "")).orEmpty()
                    val recUuid = appProps?.optString("record_uuid", appProps.optString("recordUuid", "")).orEmpty()
                    val entityType = appProps?.optString("entity_type", appProps.optString("recordCategory", "")).orEmpty()
                    val rev = (appProps?.optString("revision", appProps.optString("version", "1")) ?: "1").toLongOrNull() ?: 1L
                    val schemaVer = (appProps?.optString("schema_version", "25") ?: "25").toIntOrNull() ?: 25
                    val syncTs = (appProps?.optString("sync_timestamp", appProps.optString("updatedAtMillis", "0")) ?: "0").toLongOrNull() ?: 0L

                    return if (isPatch) {
                        val fileId = path.substringAfter("/upload/drive/v3/files/").substringBefore("?").trim()
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            MockResponse().setResponseCode(404).setBody("""{"error":{"message":"Not found"}}""")
                        } else {
                            existing.name = name.ifBlank { existing.name }
                            existing.mimeType = mimeType.ifBlank { existing.mimeType }
                            existing.rawBody = payloadBytes
                            existing.jsonPayloadText = payloadText
                            if (checksum.isNotBlank()) existing.checksum = checksum
                            if (appIdProp.isNotBlank()) existing.appId = appIdProp
                            if (applicationIdProp.isNotBlank()) existing.applicationId = applicationIdProp
                            if (dsIdProp.isNotBlank()) existing.datasetId = dsIdProp
                            if (genId.isNotBlank()) existing.generationId = genId
                            if (recUuid.isNotBlank()) existing.recordUuid = recUuid
                            if (entityType.isNotBlank()) existing.entityType = entityType
                            existing.revision = rev
                            existing.schemaVersion = schemaVer
                            existing.syncTimestamp = syncTs
                            MockResponse().setResponseCode(200)
                                .addHeader("Date", "Tue, 17 Nov 2026 09:00:00 GMT")
                                .setBody(toJson(existing))
                        }
                    } else {
                        val newId = "drv_full_${idSeq.incrementAndGet()}"
                        val obj = DriveObj(
                            id = newId,
                            name = name,
                            mimeType = mimeType,
                            parentId = parentId,
                            rawBody = payloadBytes,
                            jsonPayloadText = payloadText,
                            checksum = checksum,
                            appId = appIdProp,
                            applicationId = applicationIdProp,
                            datasetId = dsIdProp,
                            generationId = genId,
                            recordUuid = recUuid,
                            entityType = entityType,
                            revision = rev,
                            schemaVersion = schemaVer,
                            syncTimestamp = syncTs
                        )
                        storage[newId] = obj
                        MockResponse().setResponseCode(200)
                            .addHeader("Date", "Tue, 17 Nov 2026 09:00:00 GMT")
                            .setBody(toJson(obj))
                    }
                }

                if (path.startsWith("/drive/v3/files")) {
                    val subPath = path.removePrefix("/drive/v3/files")
                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("POST", ignoreCase = true)) {
                        val json = runCatching { JSONObject(bodyText) }.getOrNull() ?: JSONObject()
                        val newId = "drv_fld_${idSeq.incrementAndGet()}"
                        val obj = DriveObj(
                            id = newId,
                            name = json.optString("name", "Folder"),
                            mimeType = json.optString("mimeType", GoogleDriveRestClient.FOLDER_MIME),
                            parentId = json.optJSONArray("parents")?.optString(0, "root") ?: "root",
                            rawBody = ByteArray(0),
                            jsonPayloadText = ""
                        )
                        storage[newId] = obj
                        return MockResponse().setResponseCode(200)
                            .addHeader("Date", "Tue, 17 Nov 2026 09:00:00 GMT")
                            .setBody(toJson(obj))
                    }

                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("GET", ignoreCase = true)) {
                        val q = decodeQueryParam(path, "q")
                        val arr = JSONArray()
                        filterByQuery(q).forEach { arr.put(JSONObject(toJson(it))) }
                        return MockResponse().setResponseCode(200)
                            .addHeader("Date", "Tue, 17 Nov 2026 09:00:00 GMT")
                            .setBody(JSONObject().put("files", arr).toString())
                    }

                    val fileId = subPath.removePrefix("/").substringBefore("?").trim()
                    if (method.equals("DELETE", ignoreCase = true)) {
                        storage.remove(fileId)
                        return MockResponse().setResponseCode(204).addHeader("Date", "Tue, 17 Nov 2026 09:00:00 GMT")
                    }
                    if (method.equals("GET", ignoreCase = true)) {
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            return MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}""")
                        }
                        return if (path.contains("alt=media")) {
                            MockResponse().setResponseCode(200)
                                .addHeader("Date", "Tue, 17 Nov 2026 09:00:00 GMT")
                                .setBody(existing.jsonPayloadText)
                        } else {
                            MockResponse().setResponseCode(200)
                                .addHeader("Date", "Tue, 17 Nov 2026 09:00:00 GMT")
                                .setBody(toJson(existing))
                        }
                    }
                }
                return MockResponse().setResponseCode(404)
            }
        }

        private fun parseMultipartBody(
            contentTypeHeader: String,
            bodyText: String,
            rawBytes: ByteArray
        ): Triple<JSONObject, String, ByteArray> {
            val boundary = Regex("""boundary=([^;\s]+)""")
                .find(contentTypeHeader)?.groupValues?.get(1)?.trim('"')
                ?: "===sewain_drive_boundary_"
            val delimiter = if (bodyText.contains("--$boundary")) "--$boundary" else "--===sewain_drive_boundary_"
            val rawParts = bodyText.split(delimiter).map { it.trim() }.filter { it.isNotEmpty() && it != "--" }
            var metadataJson = JSONObject()
            var contentText = ""
            for ((idx, part) in rawParts.withIndex()) {
                val splitIdx = part.indexOf("\r\n\r\n").takeIf { it >= 0 } ?: part.indexOf("\n\n")
                val bodySection = if (splitIdx >= 0) {
                    val offset = if (part.substring(splitIdx).startsWith("\r\n\r\n")) 4 else 2
                    part.substring(splitIdx + offset).trimEnd('\r', '\n', '-')
                } else part
                if (idx == 0) {
                    val jsonStart = bodySection.indexOf("{")
                    val jsonEnd = bodySection.lastIndexOf("}")
                    if (jsonStart >= 0 && jsonEnd > jsonStart) {
                        runCatching { metadataJson = JSONObject(bodySection.substring(jsonStart, jsonEnd + 1)) }
                    }
                } else if (idx == 1) {
                    contentText = bodySection
                }
            }
            val payloadBytes = if (contentText.isNotEmpty()) {
                contentText.toByteArray(Charsets.UTF_8)
            } else {
                rawBytes.takeIf { it.isNotEmpty() } ?: ByteArray(64) { 1 }
            }
            return Triple(metadataJson, contentText, payloadBytes)
        }

        private fun filterByQuery(q: String): List<DriveObj> {
            val active = storage.values.filter { !it.trashed }
            if (q.isBlank()) return active
            val nameMatch = Regex("""name\s*=\s*'([^']+)'""").find(q)?.groupValues?.get(1)
            val parentMatch = Regex("""'([^']+)'\s+in\s+parents""").find(q)?.groupValues?.get(1)
            val appPropMatch = Regex("""appProperties\s+has\s*\{\s*key='([^']+)'\s+and\s+value='([^']+)'\s*\}""").find(q)
            val appPropKey = appPropMatch?.groupValues?.get(1)
            val appPropVal = appPropMatch?.groupValues?.get(2)
            val wantsFolderOnly = q.contains("mimeType = 'application/vnd.google-apps.folder'")
            val wantsNonFolderOnly = q.contains("mimeType != 'application/vnd.google-apps.folder'")
            return active.filter { obj ->
                val okName = nameMatch == null || obj.name == nameMatch
                val okParent = parentMatch == null || obj.parentId == parentMatch
                val okFolder = !wantsFolderOnly || obj.mimeType == GoogleDriveRestClient.FOLDER_MIME
                val okNonFolder = !wantsNonFolderOnly || obj.mimeType != GoogleDriveRestClient.FOLDER_MIME
                val okAppProp = when (appPropKey) {
                    null -> true
                    "appId" -> obj.appId == appPropVal
                    "applicationId" -> obj.applicationId == appPropVal
                    "record_uuid", "recordUuid" -> obj.recordUuid == appPropVal
                    "generation_id", "datasetGeneration" -> obj.generationId == appPropVal
                    "entity_type", "recordCategory" -> obj.entityType == appPropVal
                    else -> true
                }
                okName && okParent && okFolder && okNonFolder && okAppProp
            }
        }

        private fun decodeQueryParam(fullPath: String, key: String): String {
            val queryPart = fullPath.substringAfter("?", "")
            if (queryPart.isBlank()) return ""
            return queryPart.split("&")
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault("") }
                ?: ""
        }

        private fun toJson(obj: DriveObj): String {
            return JSONObject().apply {
                put("id", obj.id)
                put("name", obj.name)
                put("mimeType", obj.mimeType)
                put("size", obj.rawBody.size.toString())
                put("parents", JSONArray(listOf(obj.parentId)))
                put("createdTime", "2026-11-17T09:00:00.000Z")
                put("modifiedTime", "2026-11-17T09:00:00.000Z")
                put("trashed", obj.trashed)
                put("appProperties", JSONObject().apply {
                    put("checksum", obj.checksum)
                    put("checksumSha256", obj.checksum)
                    if (obj.appId.isNotBlank()) put("appId", obj.appId)
                    if (obj.applicationId.isNotBlank()) put("applicationId", obj.applicationId)
                    if (obj.datasetId.isNotBlank()) put("datasetId", obj.datasetId)
                    if (obj.generationId.isNotBlank()) {
                        put("generation_id", obj.generationId)
                        put("datasetGeneration", obj.generationId)
                    }
                    if (obj.recordUuid.isNotBlank()) {
                        put("record_uuid", obj.recordUuid)
                        put("recordUuid", obj.recordUuid)
                    }
                    if (obj.entityType.isNotBlank()) {
                        put("entity_type", obj.entityType)
                        put("recordCategory", obj.entityType)
                    }
                    put("revision", obj.revision.toString())
                    put("version", obj.revision.toString())
                    put("schema_version", obj.schemaVersion.toString())
                    put("sync_timestamp", obj.syncTimestamp.toString())
                    put("updatedAtMillis", obj.syncTimestamp.toString())
                })
            }.toString()
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.APP_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()

        mockWebServer = MockWebServer()
        fakeBackend = FullCloudDriveBackend()
        mockWebServer.dispatcher = fakeBackend.dispatcher
        mockWebServer.start()

        openAndWire()
    }

    private fun openAndWire() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24, AppDatabase.MIGRATION_24_25)
            .allowMainThreadQueries()
            .build()
        dao = database.freezerDao()
        repository = FreezerRepository(dao)

        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val restClient = GoogleDriveRestClient(
            apiBaseUrl = "$baseUrl/drive/v3",
            uploadBaseUrl = "$baseUrl/upload/drive/v3"
        )
        generationManager = DatabaseGenerationManager(context)
        driveService = GoogleDriveService(context, restClient, accessTokenOverride = "integration-token")
        queueManager = SyncQueueManager(dao, generationManager)
        conflictResolver = SyncConflictResolver(dao, generationManager)
        consistencyChecker = SyncConsistencyChecker(context, dao, driveService, generationManager, conflictResolver)
        syncRepository = SyncRepository(
            context = context,
            dao = dao,
            generationManager = generationManager,
            driveService = driveService,
            queueManager = queueManager,
            conflictResolver = conflictResolver,
            consistencyChecker = consistencyChecker
        ).apply {
            autoTriggerSyncOnMutation = false
        }

        val engine = GoogleDriveMirrorSyncEngine(
            context = context,
            dao = dao,
            driveClient = restClient,
            accessTokenOverride = "integration-token"
        ).apply {
            autoTriggerSyncOnMutation = false
        }
        repository.attachMirrorSyncEngine(engine)
        SyncWorker.testRepositoryFactory = { syncRepository }
    }

    @After
    fun tearDown() {
        SyncWorker.testRepositoryFactory = null
        database.close()
        context.deleteDatabase(dbName)
        mockWebServer.shutdown()
    }

    @Test
    fun fullEndToEndLifecycle_CreateUpdateDeleteRestartOfflineReconnectResetAndAudit(): Unit = runBlocking {
        // =====================================================================
        // 1. CREATE 10 RECORDS (4 Units, 3 Customers, 2 Transactions, 1 Payment)
        // =====================================================================
        val unitIds = (1..4).map { idx ->
            repository.saveUnit(
                FreezerUnitEntity(
                    kodeUnit = "FRZ-10$idx",
                    namaUnit = "Freezer Gea $idx",
                    jenisUnit = "Chest Freezer",
                    owner = "SOC",
                    hargaPembelian = 4_500_000L,
                    kapasitasLiter = 300 + idx * 10,
                    cicilanPerBulan = 500_000L,
                    merek = "Gea",
                    model = "AB-$idx",
                    nomorSeri = "SN-100$idx",
                    statusUnit = "Tersedia",
                    lokasiUnit = "Solo",
                    tanggalPembelian = "2026-01-01"
                )
            )
        }
        val customerIds = (1..3).map { idx ->
            repository.saveCustomer(
                CustomerEntity(
                    namaLengkap = "Pelanggan $idx",
                    nik = "337200000000000$idx",
                    nomorHp = "081200000$idx",
                    alamatLengkap = "Solo No $idx",
                    jenisKelamin = "Laki-laki",
                    tanggalRegistrasi = "2026-01-02"
                )
            )
        }
        val txIds = (1..2).map { idx ->
            repository.saveTransaction(
                RentalTransactionEntity(
                    nomorInvoice = "INV-INT-00$idx",
                    tanggalPemesanan = "2026-11-01",
                    customerId = customerIds[idx - 1],
                    namaPelangganSnapshot = "Pelanggan $idx",
                    unitId = unitIds[idx - 1],
                    namaUnitSnapshot = "Freezer Gea $idx",
                    kodeUnitSnapshot = "FRZ-10$idx",
                    hargaSewa = 500_000L,
                    durasiHari = 30,
                    tanggalMulaiSewa = "2026-11-01",
                    tanggalAkhirSewa = "2026-12-01",
                    tanggalJatuhTempo = "2026-12-01",
                    nominalPembayaran = 0L,
                    totalTagihan = 500_000L,
                    statusPembayaran = "Belum Bayar",
                    statusPenyewaan = "Dalam Penyewaan",
                    metodePembayaran = "Transfer BCA"
                )
            )
        }
        val paymentId = repository.savePaymentProof(
            PaymentProofEntity(
                transactionId = txIds[0],
                tanggalPembayaran = "2026-11-02",
                nominalPembayaran = 250_000L,
                metodePembayaran = "Transfer BCA"
            )
        )

        // Verify SyncWorker drains all 10 created records (and any synchronized parent tx updates coalesced cleanly)
        val workerCreateResult = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), workerCreateResult)
        assertEquals(10, fakeBackend.activeOperationalFiles().size)

        // Verify all required Google Drive appProperties & permanent UUID v4 format
        fakeBackend.activeOperationalFiles().forEach { obj ->
            assertTrue("UUID must be valid v4: ${obj.recordUuid}", MirrorOutboxCodec.isValidUuidV4(obj.recordUuid))
            assertTrue(obj.entityType.isNotBlank())
            assertEquals("GENERATION_001", obj.generationId)
            assertTrue(obj.revision >= 1L)
            assertEquals(25, obj.schemaVersion)
            assertTrue(obj.checksum.length == 64)
            assertTrue(obj.syncTimestamp > 0L)
        }

        // Capture pre-reset backup ZIP for Requirement F check later
        val gen1BackupDir = File(context.cacheDir, "test_gen1_bkp").apply { mkdirs() }
        val gen1BackupResult = StructuredBackupEngine.createStructuredBackupZip(
            context = context,
            repository = repository,
            encryptSensitivePayload = false,
            includePhotos = false,
            destinationDir = gen1BackupDir,
            customFileName = "gen1_pre_reset.zip",
            saveToHistory = false
        )
        assertTrue(gen1BackupResult.isSuccess)
        val gen1BackupZip = gen1BackupResult.getOrThrow().second

        // =====================================================================
        // 2. UPDATE 5 RECORDS & VERIFY UUID STABILITY + REVISION INCREMENT
        // =====================================================================
        val unit1Before = repository.getUnitById(unitIds[0])!!
        val unit2Before = repository.getUnitById(unitIds[1])!!
        val cust1Before = repository.getCustomerById(customerIds[0])!!
        val tx1Before = repository.getTransactionById(txIds[0])!!
        val payBefore = dao.getPaymentProofByIdIncludingDeleted(paymentId)!!

        repository.saveUnit(unit1Before.copy(kondisiUnit = "Terawat Premium"))
        repository.saveUnit(unit2Before.copy(cicilanPerBulan = 550_000L))
        repository.saveCustomer(cust1Before.copy(alamatLengkap = "Jl. Slamet Riyadi No 99"))
        repository.saveTransaction(tx1Before.copy(catatan = "Updated note"))
        repository.savePaymentProof(payBefore.copy(nominalPembayaran = 300_000L))

        val workerUpdateResult = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), workerUpdateResult)

        // Ensure UUID never changed and versions incremented cleanly with 0 duplicates
        assertEquals(unit1Before.uuid, repository.getUnitById(unitIds[0])!!.uuid)
        assertEquals(unit1Before.version + 1L, repository.getUnitById(unitIds[0])!!.version)
        assertEquals(cust1Before.uuid, repository.getCustomerById(customerIds[0])!!.uuid)
        assertEquals(10, fakeBackend.activeOperationalFiles().size)

        // =====================================================================
        // 3. DELETE 3 RECORDS (SOFT DELETE -> TOMBSTONE -> CLOUD DELETE -> PURGE)
        // =====================================================================
        val delUnit = repository.getUnitById(unitIds[3])!!
        val delCust = repository.getCustomerById(customerIds[2])!!
        val delTx = repository.getTransactionById(txIds[1])!!

        repository.deleteUnit(delUnit)
        repository.deleteCustomer(delCust)
        repository.deleteTransaction(delTx)

        // Soft-deleted immediately hidden from UI queries
        assertTrue(repository.getAllUnitsOnce().none { it.id == delUnit.id })
        assertTrue(repository.getAllCustomersOnce().none { it.id == delCust.id })
        assertTrue(repository.getAllTransactionsOnce().none { it.id == delTx.id })

        val workerDeleteResult = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), workerDeleteResult)

        // Deleted files removed from Google Drive + tombstones written
        assertEquals(7, fakeBackend.activeOperationalFiles().size)
        assertTrue(fakeBackend.activeOperationalFiles().none { it.recordUuid in listOf(delUnit.uuid, delCust.uuid, delTx.uuid) })

        // Verify deleted records cannot be resurrected from stale metadata
        assertFalse(
            syncRepository.canApplyRemoteRecordWithoutResurrection(
                localRecordKey = "UNIT:${delUnit.id}",
                remoteDatasetId = generationManager.getCurrentDatasetId(),
                remoteTimestampMillis = System.currentTimeMillis(),
                remoteVersion = delUnit.version,
                recordUuid = delUnit.uuid
            )
        )

        // =====================================================================
        // 4. SIMULATE OFFLINE MUTATION -> PROCESS RESTART -> RECONNECT -> SYNC
        // =====================================================================
        syncRepository.setSimulatedOfflineMode(true)
        val offlineUnitId = repository.saveUnit(
            FreezerUnitEntity(
                kodeUnit = "FRZ-OFFLINE",
                namaUnit = "Freezer Offline",
                jenisUnit = "Chest Freezer",
                owner = "SOC",
                hargaPembelian = 5_000_000L,
                kapasitasLiter = 400,
                cicilanPerBulan = 600_000L,
                merek = "RSA",
                model = "CF-400",
                nomorSeri = "SN-OFFLINE",
                statusUnit = "Tersedia",
                lokasiUnit = "Solo",
                tanggalPembelian = "2026-01-01"
            )
        )
        val offlineResult = syncRepository.processSyncQueue(ignoreBackoffTimers = true).getOrThrow()
        assertTrue(offlineResult.remainingPendingCount >= 1)

        // Close & reopen Room SQLite DB to simulate full app restart while offline
        database.close()
        openAndWire()
        syncRepository.setSimulatedOfflineMode(false)

        // Verify SyncWorker picks up persistent outbox after restart when online
        val postRestartResult = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), postRestartResult)
        assertEquals(8, fakeBackend.activeOperationalFiles().size)
        assertEquals(MirrorOutboxCodec.STATUS_SYNCED, repository.getUnitById(offlineUnitId)!!.syncStatus)

        // =====================================================================
        // 5. CONFLICT DETECTION & RESOLUTION VIA SyncConflictResolver
        // =====================================================================
        val conflictTargetUnit = repository.getUnitById(unitIds[0])!!
        val cloudObj = fakeBackend.activeOperationalFiles().first { it.recordUuid == conflictTargetUnit.uuid }
        cloudObj.revision = conflictTargetUnit.version + 5L // remote ahead
        repository.saveUnit(conflictTargetUnit.copy(kondisiUnit = "Editted Locally Offline"))

        val conflictBatch = syncRepository.processSyncQueue(ignoreBackoffTimers = true).getOrThrow()
        assertEquals(1, conflictBatch.failedCount)
        assertTrue(conflictResolver.getActiveConflictEntries().any { it.localRecordKey == "UNIT:${conflictTargetUnit.id}" })

        val resolved = syncRepository.resolveConflict(
            localRecordKey = "UNIT:${conflictTargetUnit.id}",
            strategy = ConflictResolutionStrategy.PREFER_LOCAL_FORCE_PUSH,
            syncNow = true
        ).getOrThrow()
        assertTrue(resolved)
        assertTrue(conflictResolver.getActiveConflictEntries().isEmpty())
        assertEquals(MirrorOutboxCodec.STATUS_SYNCED, repository.getUnitById(conflictTargetUnit.id)!!.syncStatus)

        // Pre-reset consistency audit == 100% clean
        val preResetAudit = syncRepository.runDataConsistencyAudit().getOrThrow()
        assertTrue("Expected 100% consistent before reset, got: $preResetAudit", preResetAudit.isConsistent)

        // =====================================================================
        // 6. FACTORY RESET (LOCAL & CLOUD) -> GENERATION BUMP + OLD DATA BLOCKED
        // =====================================================================
        val resetRes = syncRepository.executeResetLocalAndCloud().getOrThrow()
        assertEquals("GENERATION_002", resetRes.datasetGeneration)
        assertEquals(0, repository.getAllUnitsOnce().size)
        assertEquals(0, repository.getAllCustomersOnce().size)
        assertEquals(0, fakeBackend.activeOperationalFiles().size)

        // Old GENERATION_001 ZIP restore MUST be rejected after Factory Reset
        val blockedRestore = StructuredBackupEngine.executeSafeRestore(
            context = context,
            repository = repository,
            sourceFileOrUri = gen1BackupZip,
            mode = RestoreMode.FULL_RESTORE
        )
        assertFalse(blockedRestore.success)
        assertTrue(blockedRestore.message.contains("GENERATION_001"))

        // =====================================================================
        // 7. CREATE NEW GENERATION DATA & VERIFY 100% CONSISTENCY AUDIT
        // =====================================================================
        val postResetCustId = repository.saveCustomer(
            CustomerEntity(
                namaLengkap = "Pelanggan Generasi Baru",
                nik = "3372999999999999",
                nomorHp = "081999888777",
                alamatLengkap = "Yogyakarta",
                jenisKelamin = "Perempuan",
                tanggalRegistrasi = "2026-11-18"
            )
        )
        val postResetUnitId = repository.saveUnit(
            FreezerUnitEntity(
                kodeUnit = "FRZ-GEN2-01",
                namaUnit = "Freezer Sharp Gen2",
                jenisUnit = "Showcase",
                owner = "SOC",
                hargaPembelian = 4_800_000L,
                kapasitasLiter = 350,
                cicilanPerBulan = 525_000L,
                merek = "Sharp",
                model = "SH-350",
                nomorSeri = "SN-GEN2-01",
                statusUnit = "Tersedia",
                lokasiUnit = "Yogyakarta",
                tanggalPembelian = "2026-11-18"
            )
        )
        val finalWorkerRes = TestListenableWorkerBuilder<SyncWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), finalWorkerRes)

        val finalAudit = syncRepository.runDataConsistencyAudit().getOrThrow()
        assertEquals("GENERATION_002", finalAudit.localDatasetGeneration)
        assertEquals("GENERATION_002", finalAudit.cloudDatasetGeneration)
        assertEquals(1, finalAudit.localCustomersCount)
        assertEquals(1, finalAudit.localUnitsCount)
        assertEquals(0, finalAudit.missingCount)
        assertEquals(0, finalAudit.duplicateCount)
        assertEquals(0, finalAudit.staleCount)
        assertEquals(0, finalAudit.conflictCount)
        assertEquals(0, finalAudit.generationMismatchCount)
        assertEquals(0, finalAudit.checksumMismatchCount)
        assertEquals(0, finalAudit.revisionMismatchCount)
        assertEquals(0, finalAudit.quarantineCount)
        assertTrue("Final Consistency Audit must pass 100%, got: $finalAudit", finalAudit.isConsistent)
        assertNotNull(repository.getCustomerById(postResetCustId))
        assertNotNull(repository.getUnitById(postResetUnitId))

        gen1BackupDir.deleteRecursively()
    }
}
