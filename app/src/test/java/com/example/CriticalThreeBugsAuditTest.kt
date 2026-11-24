package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.DataConsistencyAuditReport
import com.example.data.local.DriveLocalSyncQueueEntity
import com.example.data.local.DriveMirrorDashboardState
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitRentalPriceEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.DatabaseGenerationManager
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.GoogleDriveRestClient
import com.example.drive.GoogleDriveService
import com.example.drive.SyncConflictResolver
import com.example.drive.SyncConsistencyChecker
import com.example.drive.SyncQueueManager
import com.example.drive.SyncRepository
import com.example.drive.SyncWorker
import com.example.pdf.InvoicePdfGenerator
import com.example.process.ProcessVerificationEngine
import com.example.process.VerificationItemStatus
import com.example.ui.components.DriveMirrorSyncDashboardCard
import com.example.ui.components.MultiUnitRentalSection
import com.example.ui.theme.SEWAinAjaFreezerTheme
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URLDecoder
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", shadows = [ShadowCompatPdfDocument::class])
class CriticalThreeBugsAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

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
    private lateinit var mirrorEngine: GoogleDriveMirrorSyncEngine
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeBackend: AuditDriveBackend

    private val dbName = "critical_three_bugs_audit.db"

    private fun makeCustomer(nama: String, idx: Int = 1) = CustomerEntity(
        namaLengkap = nama,
        nik = "337200000000000$idx",
        nomorHp = "08123456789$idx",
        alamatLengkap = "Jl. Slamet Riyadi No $idx, Solo",
        jenisKelamin = "Laki-laki",
        tanggalRegistrasi = "2026-11-01"
    )

    private fun makeUnit(
        id: Long = 0L,
        kode: String,
        nama: String,
        kapasitas: Int = 300,
        cicilanPerBulan: Long = 350000L,
        statusUnit: String = "Tersedia",
        updatedAtMillis: Long = System.currentTimeMillis()
    ) = FreezerUnitEntity(
        id = id,
        kodeUnit = kode,
        namaUnit = nama,
        jenisUnit = "Chest Freezer",
        owner = "SOC",
        hargaPembelian = 4_500_000L,
        kapasitasLiter = kapasitas,
        cicilanPerBulan = cicilanPerBulan,
        merek = "Gea",
        model = "AB-$kapasitas",
        nomorSeri = "SN-$kode",
        statusUnit = statusUnit,
        lokasiUnit = "Solo",
        tanggalPembelian = "2026-01-01",
        updatedAtMillis = updatedAtMillis
    )

    private fun makeTx(
        nomorInvoice: String,
        customerId: Long,
        namaPelanggan: String,
        unitId: Long,
        namaUnit: String,
        kodeUnit: String,
        hargaSewa: Long = 350000L,
        ongkir: Long = 35000L
    ) = RentalTransactionEntity(
        nomorInvoice = nomorInvoice,
        tanggalPemesanan = "2026-11-21",
        customerId = customerId,
        namaPelangganSnapshot = namaPelanggan,
        unitId = unitId,
        namaUnitSnapshot = namaUnit,
        kodeUnitSnapshot = kodeUnit,
        hargaSewa = hargaSewa,
        ongkir = ongkir,
        durasiHari = 30,
        tanggalMulaiSewa = "2026-11-21",
        tanggalAkhirSewa = "2026-12-21",
        tanggalJatuhTempo = "2026-12-21",
        nominalPembayaran = hargaSewa + ongkir,
        totalTagihan = hargaSewa + ongkir,
        statusPembayaran = "Lunas",
        statusPenyewaan = "Dalam Penyewaan",
        metodePembayaran = "Transfer BCA"
    )

    private class AuditDriveBackend {
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
        private val idSeq = AtomicInteger(500)

        fun activeTransactionJsonFilesForUuid(uuid: String): List<DriveObj> =
            storage.values.filter {
                !it.trashed &&
                    it.mimeType != GoogleDriveRestClient.FOLDER_MIME &&
                    it.recordUuid == uuid &&
                    it.entityType.equals("TRANSACTION", ignoreCase = true)
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
                    val appId = appProps?.optString("appId", "").orEmpty()
                    val applicationId = appProps?.optString("applicationId", "").orEmpty()
                    val datasetId = appProps?.optString("datasetId", "").orEmpty()
                    val generationId = appProps?.optString("generation_id", appProps.optString("datasetGeneration", "")).orEmpty()
                    val recordUuid = appProps?.optString("record_uuid", appProps.optString("recordUuid", "")).orEmpty()
                    val entityType = appProps?.optString("entity_type", appProps.optString("recordCategory", "")).orEmpty()
                    val revision = appProps?.optString("revision", "1")?.toLongOrNull() ?: 1L
                    val schemaVersion = appProps?.optString("schema_version", "25")?.toIntOrNull() ?: 25
                    val syncTimestamp = appProps?.optString("sync_timestamp", "0")?.toLongOrNull() ?: System.currentTimeMillis()

                    if (isPatch) {
                        val fileId = path.substringAfter("/upload/drive/v3/files/").substringBefore("?")
                        val existing = storage[fileId]
                        if (existing != null) {
                            existing.name = name
                            existing.mimeType = mimeType
                            existing.rawBody = payloadBytes
                            existing.jsonPayloadText = payloadText
                            existing.checksum = checksum
                            existing.appId = appId
                            existing.applicationId = applicationId
                            existing.datasetId = datasetId
                            existing.generationId = generationId
                            existing.recordUuid = recordUuid
                            existing.entityType = entityType
                            existing.revision = revision
                            existing.schemaVersion = schemaVersion
                            existing.syncTimestamp = syncTimestamp
                            existing.trashed = false
                            return MockResponse().setResponseCode(200).setBody(toJson(existing))
                        }
                        return MockResponse().setResponseCode(404).setBody("""{"error":{"code":404}}""")
                    } else {
                        val newId = "drv_audit_${idSeq.incrementAndGet()}"
                        val obj = DriveObj(
                            id = newId,
                            name = name,
                            mimeType = mimeType,
                            parentId = parentId,
                            rawBody = payloadBytes,
                            jsonPayloadText = payloadText,
                            checksum = checksum,
                            appId = appId,
                            applicationId = applicationId,
                            datasetId = datasetId,
                            generationId = generationId,
                            recordUuid = recordUuid,
                            entityType = entityType,
                            revision = revision,
                            schemaVersion = schemaVersion,
                            syncTimestamp = syncTimestamp
                        )
                        storage[newId] = obj
                        return MockResponse().setResponseCode(200).setBody(toJson(obj))
                    }
                }

                if (path.startsWith("/drive/v3/files") && method == "POST") {
                    val meta = JSONObject(bodyText)
                    val newId = "drv_folder_${idSeq.incrementAndGet()}"
                    val parentsArr = meta.optJSONArray("parents")
                    val parentId = if (parentsArr != null && parentsArr.length() > 0) parentsArr.optString(0, "root") else "root"
                    val obj = DriveObj(
                        id = newId,
                        name = meta.optString("name", "folder"),
                        mimeType = meta.optString("mimeType", GoogleDriveRestClient.FOLDER_MIME),
                        parentId = parentId,
                        rawBody = ByteArray(0),
                        jsonPayloadText = ""
                    )
                    storage[newId] = obj
                    return MockResponse().setResponseCode(200).setBody(toJson(obj))
                }

                if (path.startsWith("/drive/v3/files/") && method == "DELETE") {
                    val fileId = path.substringAfter("/drive/v3/files/").substringBefore("?")
                    val removed = storage.remove(fileId)
                    return if (removed != null) {
                        MockResponse().setResponseCode(204)
                    } else {
                        MockResponse().setResponseCode(404).setBody("""{"error":{"code":404}}""")
                    }
                }

                if (path.startsWith("/drive/v3/files/") && method == "GET") {
                    val fileId = path.substringAfter("/drive/v3/files/").substringBefore("?")
                    val obj = storage[fileId] ?: return MockResponse().setResponseCode(404)
                    return if (path.contains("alt=media")) {
                        MockResponse().setResponseCode(200).setBody(obj.jsonPayloadText)
                    } else {
                        MockResponse().setResponseCode(200).setBody(toJson(obj))
                    }
                }

                if (path.startsWith("/drive/v3/files") && method == "GET") {
                    val q = decodeQueryParam(path, "q")
                    val matches = filterByQuery(q)
                    val arr = JSONArray()
                    matches.forEach { arr.put(JSONObject(toJson(it))) }
                    return MockResponse().setResponseCode(200).setBody(
                        JSONObject().put("files", arr).toString()
                    )
                }

                return MockResponse().setResponseCode(404)
            }
        }

        private fun parseMultipartBody(
            contentType: String,
            bodyText: String,
            rawBody: ByteArray
        ): Triple<JSONObject, String, ByteArray> {
            val boundary = contentType.substringAfter("boundary=", "").trim().trim('"')
            if (boundary.isBlank()) return Triple(JSONObject(), bodyText, rawBody)
            val parts = bodyText.split("--$boundary").filter { it.isNotBlank() && !it.startsWith("--") }
            val metaPart = parts.getOrNull(0)?.substringAfter("\r\n\r\n")?.trim().orEmpty()
            val mediaPart = parts.getOrNull(1)?.substringAfter("\r\n\r\n")?.trimEnd('\r', '\n').orEmpty()
            val metaJson = runCatching { JSONObject(metaPart) }.getOrElse { JSONObject() }
            return Triple(metaJson, mediaPart, mediaPart.toByteArray(Charsets.UTF_8))
        }

        private fun filterByQuery(q: String): List<DriveObj> {
            val nameRegex = Regex("name\\s*=\\s*'([^']+)'")
            val parentRegex = Regex("'([^']+)'\\s+in\\s+parents")
            val appPropRegex = Regex("appProperties\\s+has\\s+\\{\\s*key='([^']+)'\\s+and\\s+value='([^']+)'\\s*\\}")

            val expectedName = nameRegex.find(q)?.groupValues?.get(1)
            val expectedParent = parentRegex.find(q)?.groupValues?.get(1)
            val appPropMatch = appPropRegex.find(q)
            val appPropKey = appPropMatch?.groupValues?.get(1)
            val appPropVal = appPropMatch?.groupValues?.get(2)
            val requireFolder = q.contains("mimeType = '${GoogleDriveRestClient.FOLDER_MIME}'")
            val requireNotFolder = q.contains("mimeType != '${GoogleDriveRestClient.FOLDER_MIME}'")

            return storage.values.filter { obj ->
                if (obj.trashed) return@filter false
                val okName = expectedName == null || obj.name == expectedName
                val okParent = expectedParent == null || obj.parentId == expectedParent
                val okFolder = !requireFolder || obj.mimeType == GoogleDriveRestClient.FOLDER_MIME
                val okNonFolder = !requireNotFolder || obj.mimeType != GoogleDriveRestClient.FOLDER_MIME
                val okAppProp = if (appPropKey == null || appPropVal == null) true else when (appPropKey) {
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
                put("createdTime", "2026-11-21T10:00:00.000Z")
                put("modifiedTime", "2026-11-21T10:00:00.000Z")
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
        fakeBackend = AuditDriveBackend()
        mockWebServer.dispatcher = fakeBackend.dispatcher
        mockWebServer.start()

        database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25
            )
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
        driveService = GoogleDriveService(context, restClient, accessTokenOverride = "audit-test-token")
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
        mirrorEngine = GoogleDriveMirrorSyncEngine(
            context = context,
            dao = dao,
            driveClient = restClient,
            accessTokenOverride = "audit-test-token"
        ).apply {
            autoTriggerSyncOnMutation = false
        }
        repository.attachMirrorSyncEngine(mirrorEngine)
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
    fun bug1_atomicMultiUnitInsert_uuidVerification_and_idempotentDriveRetry() = runBlocking {
        val custId = repository.saveCustomer(makeCustomer("Budi Santoso", 1))
        val unit1Id = repository.saveUnit(makeUnit(kode = "FRZ-101", nama = "Freezer 300L", kapasitas = 300, cicilanPerBulan = 350000L))
        val unit2Id = repository.saveUnit(makeUnit(kode = "FRZ-102", nama = "Freezer 400L", kapasitas = 400, cicilanPerBulan = 420000L))

        val invoiceNo = repository.generateUniqueInvoiceNumber()
        val parentTx = makeTx(
            nomorInvoice = invoiceNo,
            customerId = custId,
            namaPelanggan = "Budi Santoso",
            unitId = unit1Id,
            namaUnit = "Freezer 300L + Freezer 400L",
            kodeUnit = "FRZ-101, FRZ-102",
            hargaSewa = 770000L,
            ongkir = 35000L
        )
        val unitItems = listOf(
            TransactionUnitItemEntity(
                transactionId = 0L,
                customerId = custId,
                unitId = unit1Id,
                unitIndexNumber = 1,
                kodeUnitSnapshot = "FRZ-101",
                namaUnitSnapshot = "Freezer 300L",
                hargaSewa = 350000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-21",
                tanggalAkhirSewa = "2026-12-21",
                tanggalJatuhTempo = "2026-12-21",
                nominalPembayaran = 350000L,
                statusPembayaran = "Lunas"
            ),
            TransactionUnitItemEntity(
                transactionId = 0L,
                customerId = custId,
                unitId = unit2Id,
                unitIndexNumber = 2,
                kodeUnitSnapshot = "FRZ-102",
                namaUnitSnapshot = "Freezer 400L",
                hargaSewa = 420000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-21",
                tanggalAkhirSewa = "2026-12-21",
                tanggalJatuhTempo = "2026-12-21",
                nominalPembayaran = 420000L,
                statusPembayaran = "Lunas"
            )
        )

        // 1. Atomic @Transaction commit BEFORE PDF generation and Drive upload
        val committedTx = repository.saveMultiUnitTransaction(parentTx, unitItems)
        assertTrue("Committed rowId must be > 0", committedTx.id > 0L)
        assertTrue("Committed uuid must be non-blank", committedTx.uuid.isNotBlank())
        val committedItems = repository.getTransactionUnitItemsByTxOnce(committedTx.id)
        assertEquals(2, committedItems.size)

        // 2. Generate real PDF and attach non-destructively (no REPLACE rowId shift!)
        val pdfFile = InvoicePdfGenerator.generateInvoicePdf(
            context = context,
            transaction = committedTx,
            customer = repository.getCustomerById(custId),
            unit = repository.getUnitById(unit1Id),
            companyProfile = CompanyProfile(),
            unitItems = committedItems
        )
        val afterPdfTx = repository.attachGeneratedPdfAndSyncIdempotently(
            transactionId = committedTx.id,
            transactionUuid = committedTx.uuid,
            pdfPath = pdfFile.absolutePath
        )
        assertNotNull(afterPdfTx)
        assertEquals("Row ID must remain identical after PDF attach", committedTx.id, afterPdfTx!!.id)
        assertEquals("UUID must remain identical after PDF attach", committedTx.uuid, afterPdfTx.uuid)
        assertEquals(pdfFile.absolutePath, afterPdfTx.pdfInvoicePath)

        // 3. Direct UUID verification via ProcessVerificationEngine (never fails with false 90% rollback!)
        val verifier = ProcessVerificationEngine(dao)
        val localVerify = verifier.verifyLocalEntitySaved(
            entityType = ProcessVerificationEngine.ENTITY_TRANSACTION,
            localId = committedTx.id,
            expectedUuid = committedTx.uuid,
            extraIntegrityCheck = {
                if (repository.getTransactionUnitItemsByTxOnce(committedTx.id).size == 2) null
                else "Expected 2 committed unit items"
            }
        )
        assertEquals(VerificationItemStatus.SUCCESS, localVerify.status)
        assertTrue(File(afterPdfTx.pdfInvoicePath).exists())

        // 4. First Drive upload
        syncRepository.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true)
        val driveFilesAfterAttempt1 = fakeBackend.activeTransactionJsonFilesForUuid(committedTx.uuid)
        assertEquals("Attempt 1 should upload exactly 1 transaction JSON to Drive", 1, driveFilesAfterAttempt1.size)
        val originalDriveFileId = driveFilesAfterAttempt1.first().id

        // 5. Simulate retry ("COBA LAGI") where local mapping link is re-checked and sync runs again:
        // MUST NOT create duplicate Drive JSON file; must reuse & link existing driveFileId!
        val linkedDriveId = repository.linkExistingDriveFileIdToTransaction(committedTx.uuid, committedTx.id)
        assertEquals(originalDriveFileId, linkedDriveId)
        repository.attachGeneratedPdfAndSyncIdempotently(committedTx.id, committedTx.uuid, pdfFile.absolutePath)
        syncRepository.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true)

        val driveFilesAfterRetry = fakeBackend.activeTransactionJsonFilesForUuid(committedTx.uuid)
        assertEquals("Retry must be idempotent with ZERO duplicate files on Drive", 1, driveFilesAfterRetry.size)
        assertEquals(originalDriveFileId, driveFilesAfterRetry.first().id)

        val cloudMap = dao.getDriveCloudFileMapByUuid(committedTx.uuid)
        assertNotNull(cloudMap)
        assertEquals(originalDriveFileId, cloudMap!!.driveFileId)
    }

    @Test
    fun bug2_liveUnitPriceResolution_and_cloudOverrideIgnoresStaleTimestamp() = runBlocking {
        val unitId = repository.saveUnitWithRentalPrices(
            unit = makeUnit(
                kode = "FRZ-201",
                nama = "Freezer Sliding 500L",
                kapasitas = 500,
                cicilanPerBulan = 380000L,
                updatedAtMillis = System.currentTimeMillis() + 60_000L // simulate local clock skew ahead of cloud
            ),
            pricesByPeriod = mapOf(1 to 25000L, 7 to 120000L, 30 to 350000L)
        )
        val initialUnit = repository.getUnitById(unitId)!!

        // Verify live Room resolution for Harian, Mingguan, Bulanan, and Program Hak Milik
        assertEquals(25000L, repository.resolveLiveUnitPrice(unitId, 1, "Sewa Reguler").second)
        assertEquals(120000L, repository.resolveLiveUnitPrice(unitId, 7, "Sewa Reguler").second)
        assertEquals(350000L, repository.resolveLiveUnitPrice(unitId, 30, "Sewa Reguler").second)
        assertEquals(380000L, repository.resolveLiveUnitPrice(unitId, 30, "Sewa Hak Milik").second)

        // Inject a STALE queue row for this unit
        dao.upsertDriveLocalSyncQueueItem(
            DriveLocalSyncQueueEntity(
                operationId = "OP_STALE_UNIT_$unitId",
                recordCategory = "UNIT",
                recordId = unitId,
                localRecordKey = "UNIT:$unitId",
                operationType = "UPDATE",
                payloadJson = "{}",
                syncStatus = "STALE"
            )
        )

        // Simulate Cloud-to-Local sync bringing updated price even with older cloud timestamp & local STALE marker
        val cloudUnit = initialUnit.copy(
            cicilanPerBulan = 460000L,
            updatedAtMillis = System.currentTimeMillis() - 120_000L
        )
        val applied = repository.applyCloudUnitPricingOverride(
            remoteUnit = cloudUnit,
            remotePeriodPrices = mapOf(1 to 30000L, 7 to 150000L, 30 to 430000L)
        )
        assertEquals(460000L, applied.cicilanPerBulan)
        assertEquals(460000L, dao.getUnitById(unitId)?.cicilanPerBulan)
        assertEquals(30000L, repository.resolveLiveUnitPrice(unitId, 1, "Sewa Reguler").second)
        assertEquals(150000L, repository.resolveLiveUnitPrice(unitId, 7, "Sewa Reguler").second)
        assertEquals(430000L, repository.resolveLiveUnitPrice(unitId, 30, "Sewa Reguler").second)
        assertEquals(460000L, repository.resolveLiveUnitPrice(unitId, 30, "Sewa Hak Milik").second)

        val remainingStale = dao.getAllDriveLocalSyncQueueOnce().count {
            it.localRecordKey == "UNIT:$unitId" && (it.syncStatus == "STALE" || it.syncStatus == "STALE_OPERATION")
        }
        assertEquals("STALE marker for unit must be cleared on cloud override", 0, remainingStale)
    }

    @Test
    fun bug2_composeUi_switchingUnitAndPackageImmediatelyUpdatesPrice() {
        val unitA = makeUnit(
            id = 10L,
            kode = "FRZ-A",
            nama = "Freezer A 200L",
            kapasitas = 200,
            cicilanPerBulan = 320000L
        )
        val unitB = makeUnit(
            id = 20L,
            kode = "FRZ-B",
            nama = "Freezer B 600L",
            kapasitas = 600,
            cicilanPerBulan = 550000L
        )
        val pricesMap = mapOf(
            10L to listOf(
                UnitRentalPriceEntity(unitId = 10L, periodDays = 1, price = 15000L),
                UnitRentalPriceEntity(unitId = 10L, periodDays = 7, price = 85000L),
                UnitRentalPriceEntity(unitId = 10L, periodDays = 30, price = 300000L)
            ),
            20L to listOf(
                UnitRentalPriceEntity(unitId = 20L, periodDays = 1, price = 30000L),
                UnitRentalPriceEntity(unitId = 20L, periodDays = 7, price = 175000L),
                UnitRentalPriceEntity(unitId = 20L, periodDays = 30, price = 520000L)
            )
        )

        var currentItems by mutableStateOf(
            listOf(
                TransactionUnitItemEntity(
                    transactionId = 0L,
                    customerId = 1L,
                    unitId = 10L,
                    unitIndexNumber = 1,
                    kodeUnitSnapshot = "FRZ-A",
                    namaUnitSnapshot = "Freezer A 200L",
                    kapasitasLiterSnapshot = 200,
                    jenisProgram = "Sewa Reguler",
                    hargaSewa = 300000L,
                    durasiHari = 30,
                    tanggalMulaiSewa = "2026-11-21",
                    tanggalAkhirSewa = "2026-12-21",
                    tanggalJatuhTempo = "2026-12-21",
                    nominalPembayaran = 300000L
                )
            )
        )

        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                MultiUnitRentalSection(
                    unitItems = currentItems,
                    allUnits = listOf(unitA, unitB),
                    unitRentalPricesMap = pricesMap,
                    sewaHakMilikPeriods = listOf(12, 15, 19),
                    ongkir = 35000L,
                    deposit = 0L,
                    diskon = 0L,
                    onUnitItemsChanged = { updated -> currentItems = updated }
                )
            }
        }

        // Switch to Harian (1 Hari)
        composeRule.onNodeWithTag("chip_unit_1_period_1d").performClick()
        composeRule.waitForIdle()
        assertEquals(15000L, currentItems.first().hargaSewa)
        assertEquals(1, currentItems.first().durasiHari)

        // Switch to Mingguan (7 Hari)
        composeRule.onNodeWithTag("chip_unit_1_period_7d").performClick()
        composeRule.waitForIdle()
        assertEquals(85000L, currentItems.first().hargaSewa)
        assertEquals(7, currentItems.first().durasiHari)

        // Switch to Sewa Hak Milik (must pull Unit A's 320.000 & default to 19x)
        composeRule.onNodeWithTag("chip_unit_1_hak_milik").performClick()
        composeRule.waitForIdle()
        assertEquals("Sewa Hak Milik", currentItems.first().jenisProgram)
        assertEquals(320000L, currentItems.first().hargaSewa)
        assertEquals(19, currentItems.first().totalPeriodeHakMilik)

        // Switch selected unit to Unit B while in Sewa Hak Milik: MUST immediately update to 550.000 (not stay at 320.000!)
        composeRule.onNodeWithTag("dropdown_unit_selector_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dropdown_unit_option_1_20").performClick()
        composeRule.waitForIdle()
        assertEquals(20L, currentItems.first().unitId)
        assertEquals(550000L, currentItems.first().hargaSewa)
    }

    @Test
    fun bug3_cleanOrphanCloudAndResync_deletesOrphansAndDuplicates_resetsStaleToZero() = runBlocking {
        // Seed valid local data & sync cleanly
        val custId = repository.saveCustomer(makeCustomer("Siti Aminah", 2))
        val unitId = repository.saveUnit(makeUnit(kode = "FRZ-301", nama = "Freezer 350L", kapasitas = 350, cicilanPerBulan = 350000L))
        val txId = repository.saveTransaction(
            makeTx(
                nomorInvoice = repository.generateUniqueInvoiceNumber(),
                customerId = custId,
                namaPelanggan = "Siti Aminah",
                unitId = unitId,
                namaUnit = "Freezer 350L",
                kodeUnit = "FRZ-301",
                hargaSewa = 350000L
            )
        )
        assertTrue(txId > 0L)
        syncRepository.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true)

        val gen = generationManager.getCurrentGenerationId()
        val appId = GoogleDriveMirrorSyncEngine.APPLICATION_ID
        val datasetId = generationManager.getCurrentDatasetId()
        val anyUploadedParent = fakeBackend.storage.values.first {
            it.recordUuid.isNotBlank() && it.mimeType == "application/json"
        }.parentId

        // Inject 2 ORPHAN_CLOUD JSON files (UUIDs not present in local SQLite)
        val orphanUuid1 = UUID.randomUUID().toString()
        val orphanUuid2 = UUID.randomUUID().toString()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        fakeBackend.storage["drv_orphan_1"] = AuditDriveBackend.DriveObj(
            id = "drv_orphan_1",
            name = "TRANSACTION_999.json",
            mimeType = "application/json",
            parentId = anyUploadedParent,
            rawBody = """{"nomorInvoice":"INV/SAF/$currentYear/0014"}""".toByteArray(),
            jsonPayloadText = """{"nomorInvoice":"INV/SAF/$currentYear/0014"}""",
            appId = appId,
            applicationId = appId,
            datasetId = datasetId,
            generationId = gen,
            recordUuid = orphanUuid1,
            entityType = "TRANSACTION",
            syncTimestamp = System.currentTimeMillis()
        )
        fakeBackend.storage["drv_orphan_2"] = AuditDriveBackend.DriveObj(
            id = "drv_orphan_2",
            name = "CUSTOMER_999.json",
            mimeType = "application/json",
            parentId = anyUploadedParent,
            rawBody = """{"namaLengkap":"Ghost"}""".toByteArray(),
            jsonPayloadText = """{"namaLengkap":"Ghost"}""",
            appId = appId,
            applicationId = appId,
            datasetId = datasetId,
            generationId = gen,
            recordUuid = orphanUuid2,
            entityType = "CUSTOMER",
            syncTimestamp = System.currentTimeMillis()
        )

        // Inject 1 duplicate cloud file for existing unit
        val existingUnitFile = fakeBackend.storage.values.first { it.entityType == "UNIT" && !it.trashed }
        fakeBackend.storage["drv_dup_unit"] = existingUnitFile.copy(
            id = "drv_dup_unit",
            syncTimestamp = existingUnitFile.syncTimestamp - 5000L
        )

        // Inject 1 STALE_OPERATION queue row
        dao.upsertDriveLocalSyncQueueItem(
            DriveLocalSyncQueueEntity(
                operationId = "OP_STALE_ORPHAN_999",
                recordCategory = "TRANSACTION",
                recordId = 999L,
                localRecordKey = "TRANSACTION:999",
                operationType = "CREATE",
                payloadJson = "{}",
                syncStatus = "STALE_OPERATION"
            )
        )

        // Pre-cleanup audit MUST detect orphans and duplicates
        val preAudit = syncRepository.runDataConsistencyAudit().getOrThrow()
        assertFalse("Pre-cleanup audit must report inconsistency", preAudit.isConsistent)
        assertTrue("Pre-cleanup must detect ORPHAN_CLOUD stale records", preAudit.staleRecords.any { it.contains("ORPHAN_CLOUD") })
        assertTrue("Pre-cleanup must detect duplicate cloud files", preAudit.duplicateCloudFiles.isNotEmpty())

        // Execute one-click "Bersihkan Orphan & Sinkron Ulang"
        val cleanupReport = syncRepository.cleanOrphanCloudAndResync().getOrThrow()
        assertTrue("Orphans must be deleted", cleanupReport.deletedOrphanCloudFiles >= 2)
        assertTrue("Duplicate cloud files must be deleted", cleanupReport.deletedDuplicateCloudFiles >= 1)
        assertTrue("Stale queue rows must be cleared", cleanupReport.clearedStaleQueueRows >= 1)

        val postAudit = cleanupReport.postCleanupAudit
        assertNotNull(postAudit)
        assertTrue("Post-cleanup audit must be consistent", postAudit!!.isConsistent)
        assertEquals("Duplikat must reset to 0", 0, postAudit.duplicateCloudFiles.size)
        assertEquals("Stale/Orphan must reset to 0", 0, postAudit.staleRecords.size)
        assertFalse("Orphan 1 must be removed from Drive", fakeBackend.storage.containsKey("drv_orphan_1"))
        assertFalse("Orphan 2 must be removed from Drive", fakeBackend.storage.containsKey("drv_orphan_2"))
    }

    @Test
    fun bug3_monotonicInvoiceGenerator_neverResetsTo0001_afterFailedSaveOrCloudMetadata() = runBlocking {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val custId = repository.saveCustomer(makeCustomer("Andi Wijaya", 3))
        val unitId = repository.saveUnit(makeUnit(kode = "FRZ-401", nama = "Freezer 200L", kapasitas = 200))

        // Seed a local transaction INV/SAF/YYYY/0003 and soft-delete it via repository
        val txEntity = makeTx(
            nomorInvoice = "INV/SAF/$currentYear/0003",
            customerId = custId,
            namaPelanggan = "Andi Wijaya",
            unitId = unitId,
            namaUnit = "Freezer 200L",
            kodeUnit = "FRZ-401"
        )
        val txId = repository.saveTransaction(txEntity)
        val savedTx = repository.getTransactionById(txId)!!
        repository.deleteTransaction(savedTx)

        // Also seed a Cloud Drive transaction file with INV/SAF/YYYY/0007
        syncRepository.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true)
        val txFolder = fakeBackend.storage.values.firstOrNull {
            it.mimeType == GoogleDriveRestClient.FOLDER_MIME && it.name == "TRANSACTIONS"
        }?.id ?: "root"
        fakeBackend.storage["drv_cloud_inv_7"] = AuditDriveBackend.DriveObj(
            id = "drv_cloud_inv_7",
            name = "TRANSACTION_77.json",
            mimeType = "application/json",
            parentId = txFolder,
            rawBody = """{"nomorInvoice":"INV/SAF/$currentYear/0007","id":77}""".toByteArray(),
            jsonPayloadText = """{"nomorInvoice":"INV/SAF/$currentYear/0007","id":77}""",
            appId = GoogleDriveMirrorSyncEngine.APPLICATION_ID,
            applicationId = GoogleDriveMirrorSyncEngine.APPLICATION_ID,
            datasetId = generationManager.getCurrentDatasetId(),
            generationId = generationManager.getCurrentGenerationId(),
            recordUuid = UUID.randomUUID().toString(),
            entityType = "TRANSACTION",
            syncTimestamp = System.currentTimeMillis()
        )

        // Generate invoice -> must consider local + soft-deleted + cloud max (7) -> 0008!
        val inv1 = repository.generateUniqueInvoiceNumber()
        assertEquals("INV/SAF/$currentYear/0008", inv1)

        // Even if saving inv1 fails / rolls back before hitting SQLite, next call MUST NOT reset to 0001 or reuse 0008 -> 0009!
        val inv2 = repository.generateUniqueInvoiceNumber()
        assertEquals("INV/SAF/$currentYear/0009", inv2)
    }

    @Test
    fun bug3_composeUi_cleanOrphanAndResyncButtonDisplayedAndClickable() {
        val clicked = AtomicBoolean(false)
        val auditWithOrphans = DataConsistencyAuditReport(
            auditedAtDisplay = "21/11/2026 11:00",
            localCustomersCount = 1,
            cloudCustomersCount = 1,
            localUnitsCount = 1,
            cloudUnitsCount = 1,
            localTransactionsCount = 0,
            cloudTransactionsCount = 2,
            localPaymentsCount = 0,
            cloudPaymentsCount = 0,
            duplicateCloudFiles = listOf("UNIT_1.json (x2)"),
            staleRecords = listOf("ORPHAN_CLOUD:TRANSACTION_99.json"),
            isConsistent = false
        )

        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DriveMirrorSyncDashboardCard(
                        state = DriveMirrorDashboardState(
                            isDriveConnected = true,
                            connectedEmail = "admin@sewainaja.id",
                            staleDataCount = 1
                        ),
                        onSyncNowClick = {},
                        onRetryFailedClick = {},
                        onResetLocalAndCloudConfirm = {},
                        onResetLocalOnlyConfirm = {},
                        consistencyAuditReport = auditWithOrphans,
                        onCleanOrphanAndResyncClick = { clicked.set(true) }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("btn_clean_orphan_and_resync").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Bersihkan Orphan & Sinkron Ulang").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_clean_orphan_and_resync").performClick()
        composeRule.waitForIdle()
        assertTrue("Clicking Bersihkan Orphan & Sinkron Ulang must invoke callback", clicked.get())
    }
}
