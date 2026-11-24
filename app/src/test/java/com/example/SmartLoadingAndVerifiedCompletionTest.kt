package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
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
import com.example.process.GlobalProcessManager
import com.example.process.GlobalProcessState
import com.example.process.GlobalProcessStatus
import com.example.process.ProcessExecutionOutcome
import com.example.process.ProcessStageBadge
import com.example.process.ProcessVerificationEngine
import com.example.process.ProcessVerificationReport
import com.example.process.VerificationItemStatus
import com.example.ui.components.InterruptedProcessRecoveryBanner
import com.example.ui.components.SmartLoadingProcessDialog
import com.example.ui.theme.SEWAinAjaFreezerTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", shadows = [ShadowCompatPdfDocument::class])
class SmartLoadingAndVerifiedCompletionTest {

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
    private lateinit var processManager: GlobalProcessManager
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeBackend: ControllableDriveBackend

    private val isNetworkOnline = AtomicBoolean(true)

    private class ControllableDriveBackend {
        data class DriveObj(
            val id: String,
            var name: String,
            var mimeType: String,
            var parentId: String,
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
        @Volatile var forceErrorCode: Int? = null
        val uploadAttemptCount = AtomicInteger(0)

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val errCode = forceErrorCode
                if (errCode != null) {
                    return MockResponse()
                        .setResponseCode(errCode)
                        .setBody("""{"error":{"code":$errCode,"message":"Simulated Drive error $errCode"}}""")
                }

                val path = request.path ?: ""
                val method = request.method ?: "GET"
                val contentType = request.getHeader("Content-Type").orEmpty()
                val rawBody = request.body.readByteArray()
                val bodyText = String(rawBody, Charsets.UTF_8)

                if (path.startsWith("/upload/drive/v3/files")) {
                    uploadAttemptCount.incrementAndGet()
                    val isPatch = method.equals("PATCH", ignoreCase = true)
                    val (metaJson, payloadText) = parseMultipartBody(contentType, bodyText)
                    val name = metaJson.optString("name", "unnamed.json")
                    val mimeType = metaJson.optString("mimeType", "application/json")
                    val parentsArr = metaJson.optJSONArray("parents")
                    val parentId = if (parentsArr != null && parentsArr.length() > 0) parentsArr.optString(0, "root") else "root"
                    val appProps = metaJson.optJSONObject("appProperties")
                    val checksum = appProps?.optString("checksum", appProps.optString("checksumSha256", "")).orEmpty()

                    val fileId = if (isPatch) {
                        path.substringAfter("/upload/drive/v3/files/").substringBefore("?").trim()
                    } else {
                        "drv_smart_${idSeq.incrementAndGet()}"
                    }
                    val existing = storage[fileId]
                    val obj = DriveObj(
                        id = fileId,
                        name = name.ifBlank { existing?.name ?: "unnamed.json" },
                        mimeType = mimeType.ifBlank { existing?.mimeType ?: "application/json" },
                        parentId = if (isPatch && existing != null) existing.parentId else parentId,
                        jsonPayloadText = payloadText,
                        trashed = false,
                        checksum = checksum.ifBlank { existing?.checksum.orEmpty() },
                        appId = appProps?.optString("appId", existing?.appId.orEmpty()).orEmpty(),
                        applicationId = appProps?.optString("applicationId", existing?.applicationId.orEmpty()).orEmpty(),
                        datasetId = appProps?.optString("datasetId", existing?.datasetId.orEmpty()).orEmpty(),
                        generationId = appProps?.optString("generation_id", appProps.optString("datasetGeneration", existing?.generationId.orEmpty())).orEmpty(),
                        recordUuid = appProps?.optString("record_uuid", appProps.optString("recordUuid", existing?.recordUuid.orEmpty())).orEmpty(),
                        entityType = appProps?.optString("entity_type", appProps.optString("entityType", existing?.entityType.orEmpty())).orEmpty(),
                        revision = appProps?.optString("revision", "1")?.toLongOrNull() ?: (existing?.revision ?: 1L),
                        schemaVersion = appProps?.optString("schema_version", "25")?.toIntOrNull() ?: 25,
                        syncTimestamp = appProps?.optString("sync_timestamp", "0")?.toLongOrNull() ?: System.currentTimeMillis()
                    )
                    storage[fileId] = obj
                    return MockResponse().setResponseCode(200)
                        .addHeader("Date", "Tue, 17 Nov 2026 09:00:00 GMT")
                        .setBody(toJson(obj))
                }

                if (path.startsWith("/drive/v3/files")) {
                    val subPath = path.removePrefix("/drive/v3/files")
                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("POST", ignoreCase = true)) {
                        val reqJson = JSONObject(bodyText)
                        val fileId = "drv_folder_${idSeq.incrementAndGet()}"
                        val parentsArr = reqJson.optJSONArray("parents")
                        val parentId = if (parentsArr != null && parentsArr.length() > 0) parentsArr.optString(0, "root") else "root"
                        val appProps = reqJson.optJSONObject("appProperties")
                        val obj = DriveObj(
                            id = fileId,
                            name = reqJson.optString("name", "Folder"),
                            mimeType = reqJson.optString("mimeType", GoogleDriveRestClient.FOLDER_MIME),
                            parentId = parentId,
                            jsonPayloadText = "",
                            appId = appProps?.optString("appId", "").orEmpty(),
                            applicationId = appProps?.optString("applicationId", "").orEmpty(),
                            datasetId = appProps?.optString("datasetId", "").orEmpty(),
                            generationId = appProps?.optString("generation_id", appProps.optString("datasetGeneration", "")).orEmpty(),
                            entityType = appProps?.optString("entity_type", "").orEmpty()
                        )
                        storage[fileId] = obj
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
                        return MockResponse().setResponseCode(204)
                            .addHeader("Date", "Tue, 17 Nov 2026 09:00:00 GMT")
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

        private fun parseMultipartBody(contentTypeHeader: String, bodyText: String): Pair<JSONObject, String> {
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
                    val s = bodySection.indexOf("{")
                    val e = bodySection.lastIndexOf("}")
                    if (s >= 0 && e > s) runCatching { metadataJson = JSONObject(bodySection.substring(s, e + 1)) }
                } else if (idx == 1) {
                    contentText = bodySection
                }
            }
            return metadataJson to contentText
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
                    "entity_type", "entityType" -> obj.entityType == appPropVal
                    else -> true
                }
                okName && okParent && okFolder && okNonFolder && okAppProp
            }
        }

        private fun decodeQueryParam(fullPath: String, key: String): String {
            val qs = fullPath.substringAfter("?", "")
            if (qs.isBlank()) return ""
            return qs.split("&")
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("$key=")
                ?.let { URLDecoder.decode(it, "UTF-8") }
                .orEmpty()
        }

        private fun toJson(obj: DriveObj): String = JSONObject().apply {
            put("id", obj.id)
            put("name", obj.name)
            put("mimeType", obj.mimeType)
            put("createdTime", "2026-11-17T09:00:00.000Z")
            put("modifiedTime", "2026-11-17T09:00:00.000Z")
            put("size", obj.jsonPayloadText.toByteArray(Charsets.UTF_8).size.toString())
            put("trashed", obj.trashed)
            put("parents", JSONArray(listOf(obj.parentId)))
            put("appProperties", JSONObject().apply {
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
                if (obj.checksum.isNotBlank()) {
                    put("checksum", obj.checksum)
                    put("checksumSha256", obj.checksum)
                }
                put("revision", obj.revision.toString())
                put("version", obj.revision.toString())
                put("schema_version", obj.schemaVersion.toString())
                put("sync_timestamp", obj.syncTimestamp.toString())
                put("updatedAtMillis", obj.syncTimestamp.toString())
            })
        }.toString()
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sewain_global_process_manager_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.APP_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.freezerDao()
        repository = FreezerRepository(dao)

        mockWebServer = MockWebServer()
        fakeBackend = ControllableDriveBackend()
        mockWebServer.dispatcher = fakeBackend.dispatcher
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val restClient = GoogleDriveRestClient(
            apiBaseUrl = "$baseUrl/drive/v3",
            uploadBaseUrl = "$baseUrl/upload/drive/v3"
        )
        generationManager = DatabaseGenerationManager(context)
        driveService = GoogleDriveService(context, restClient, accessTokenOverride = "test-valid-oauth2-token-sewain")
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
            accessTokenOverride = "test-valid-oauth2-token-sewain"
        ).apply {
            autoTriggerSyncOnMutation = false
        }
        repository.attachMirrorSyncEngine(engine)
        SyncWorker.testRepositoryFactory = { syncRepository }

        processManager = GlobalProcessManager(
            context = context,
            dao = dao,
            defaultAutoDismissMillis = 0L
        )
    }

    @After
    fun tearDown() {
        SyncWorker.testRepositoryFactory = null
        runCatching { database.close() }
        runCatching { mockWebServer.shutdown() }
    }

    private suspend fun runImmediateDriveSync(): String {
        if (!isNetworkOnline.get()) {
            return "Koneksi internet sedang terputus. Data tetap aman di perangkat dan akan disinkronkan saat online."
        }
        val batchRes = syncRepository.processSyncQueue(forceRetryFailed = false, ignoreBackoffTimers = true)
        return batchRes.fold(
            onSuccess = { batch ->
                when {
                    batch.requiresReAuth -> "Autentikasi Google Drive kedaluwarsa (HTTP 401)."
                    batch.totalFailedInDb + batch.totalFailedAuthInDb > 0 ->
                        batch.errorMessage.ifBlank { "Upload Google Drive gagal." }
                    else -> ""
                }
            },
            onFailure = { err -> err.localizedMessage ?: "Gagal terhubung ke Google Drive." }
        )
    }

    private fun sampleCustomer(nama: String, nik: String, alamat: String = "Surakarta"): CustomerEntity =
        CustomerEntity(
            namaLengkap = nama,
            nik = nik,
            nomorHp = "081234567890",
            alamatLengkap = alamat,
            jenisKelamin = "Laki-laki",
            tanggalRegistrasi = "2026-11-17"
        )

    private fun sampleUnit(kode: String, nama: String): FreezerUnitEntity =
        FreezerUnitEntity(
            kodeUnit = kode,
            namaUnit = nama,
            jenisUnit = "Chest Freezer",
            owner = "SEWAin Aja Solo",
            hargaPembelian = 4_500_000L,
            kapasitasLiter = 318,
            cicilanPerBulan = 275_000L,
            merek = "GEA",
            model = "AB-318-R",
            nomorSeri = "SN-$kode-001",
            tanggalPembelian = "2026-01-10",
            statusUnit = "Tersedia",
            lokasiUnit = "Gudang Solo"
        )

    private fun createTempJpegFile(name: String): File {
        val file = File(context.cacheDir, name)
        val bmp = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.rgb(12, 96, 170))
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        bmp.recycle()
        return file
    }

    // =========================================================================
    // A. ALL 12 REQUIRED WORKFLOWS — REAL DB + REAL DRIVE SYNC + REAL VERIFICATION
    // =========================================================================

    @Test
    fun workflow01_02_03_addEditAndDeleteCustomer_strictVerifiedCompletion() = runBlocking {
        // 1. TAMBAH PELANGGAN
        val customer = sampleCustomer("Budi Santoso Solo", "3372011204880001", "Jl. Slamet Riyadi No. 10, Surakarta")
        val addState = processManager.executeTrackedProcess(
            processName = "Menyimpan Data Pelanggan...",
            idempotencyKey = "ADD_CUST_${customer.nik}",
            totalSteps = 4,
            initialMessage = "Memvalidasi data pelanggan..."
        ) { handle ->
            handle.updateProgress(1, 20, "Menyimpan data pelanggan ke database lokal...", ProcessStageBadge.PROCESSING)
            val savedId = repository.saveCustomer(customer)
            val persisted = dao.getCustomerById(savedId)!!
            handle.markStageCompleted("LOCAL_DB")
            handle.updateProgress(2, 55, "Data pelanggan tersimpan di perangkat...", ProcessStageBadge.LOCAL_SAVED)
            handle.updateProgress(3, 78, "Menyinkronkan data ke Google Drive...", ProcessStageBadge.SYNCING)
            val driveErr = runImmediateDriveSync()
            handle.enterVerification(step = 4, percentage = 92, message = "Memverifikasi konsistensi data lokal & cloud...")
            val localCheck = processManager.verificationEngine.verifyLocalEntitySaved(
                entityType = ProcessVerificationEngine.ENTITY_CUSTOMER,
                localId = savedId,
                expectedUuid = persisted.uuid
            )
            val (driveCheck, syncCheck) = processManager.verificationEngine.verifyRecordCloudAndSyncState(
                localRecordKey = "CUSTOMER:$savedId",
                expectedUuid = persisted.uuid,
                driveSyncError = driveErr
            )
            ProcessExecutionOutcome(
                result = persisted,
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = localCheck.status,
                    localDatabaseDetail = localCheck.detail,
                    documentationStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
                    googleDriveStatus = driveCheck.first,
                    googleDriveDetail = driveCheck.second,
                    synchronizationStatus = syncCheck.first,
                    synchronizationDetail = syncCheck.second,
                    requireCloudForFullCompletion = true
                )
            )
        }
        assertNotNull(addState)
        assertEquals(GlobalProcessStatus.COMPLETED, addState!!.processStatus)
        assertEquals(100, addState.progressPercentage)
        assertEquals(ProcessStageBadge.COMPLETED, addState.stageBadge)
        assertTrue(addState.verificationReport!!.allRequiredPassed)

        val savedCustomer = dao.getAllCustomersOnce().first()
        assertTrue(savedCustomer.uuid.isNotBlank())

        // 2. EDIT PELANGGAN
        val edited = savedCustomer.copy(alamatLengkap = "Jl. Dr. Rajiman No. 88, Laweyan, Solo")
        val editState = processManager.executeTrackedProcess(
            processName = "Memperbarui Data Pelanggan...",
            idempotencyKey = "EDIT_CUST_${edited.id}",
            totalSteps = 4
        ) { handle ->
            handle.updateProgress(1, 25, "Menyimpan perubahan ke SQLite...", ProcessStageBadge.PROCESSING)
            repository.saveCustomer(edited)
            handle.updateProgress(2, 65, "Menyinkronkan pembaruan ke Google Drive...", ProcessStageBadge.SYNCING)
            val driveErr = runImmediateDriveSync()
            handle.enterVerification(step = 4, percentage = 92)
            val localCheck = processManager.verificationEngine.verifyLocalEntitySaved(
                entityType = ProcessVerificationEngine.ENTITY_CUSTOMER,
                localId = edited.id,
                expectedUuid = savedCustomer.uuid,
                extraIntegrityCheck = {
                    val fresh = dao.getCustomerById(edited.id)
                    if (fresh?.alamatLengkap != edited.alamatLengkap) "Alamat terbaru belum tersimpan." else null
                }
            )
            val (driveCheck, syncCheck) = processManager.verificationEngine.verifyRecordCloudAndSyncState(
                localRecordKey = "CUSTOMER:${edited.id}",
                expectedUuid = savedCustomer.uuid,
                driveSyncError = driveErr
            )
            ProcessExecutionOutcome(
                result = edited,
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = localCheck.status,
                    localDatabaseDetail = localCheck.detail,
                    googleDriveStatus = driveCheck.first,
                    googleDriveDetail = driveCheck.second,
                    synchronizationStatus = syncCheck.first,
                    synchronizationDetail = syncCheck.second
                )
            )
        }
        assertEquals(GlobalProcessStatus.COMPLETED, editState!!.processStatus)
        assertEquals(100, editState.progressPercentage)
        assertEquals("Jl. Dr. Rajiman No. 88, Laweyan, Solo", dao.getCustomerById(edited.id)?.alamatLengkap)

        // 3. HAPUS PELANGGAN (dengan validasi & tombstone Google Drive)
        val deleteState = processManager.executeTrackedProcess(
            processName = "Menghapus Data Pelanggan...",
            idempotencyKey = "DEL_CUST_${edited.id}",
            totalSteps = 4
        ) { handle ->
            handle.updateProgress(1, 30, "Menghapus data pelanggan dari SQLite...", ProcessStageBadge.PROCESSING)
            repository.deleteCustomer(edited)
            handle.updateProgress(2, 70, "Menghapus file pelanggan di Google Drive...", ProcessStageBadge.SYNCING)
            val driveErr = runImmediateDriveSync()
            handle.enterVerification(step = 4, percentage = 92)
            val localDel = processManager.verificationEngine.verifyLocalEntityDeleted(
                entityType = ProcessVerificationEngine.ENTITY_CUSTOMER,
                localId = edited.id
            )
            val (driveCheck, syncCheck) = processManager.verificationEngine.verifyRecordCloudAndSyncState(
                localRecordKey = "CUSTOMER:${edited.id}",
                expectedUuid = savedCustomer.uuid,
                isDeleteOperation = true,
                driveSyncError = driveErr
            )
            ProcessExecutionOutcome(
                result = Unit,
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = localDel.status,
                    localDatabaseDetail = localDel.detail,
                    googleDriveStatus = driveCheck.first,
                    googleDriveDetail = driveCheck.second,
                    synchronizationStatus = syncCheck.first,
                    synchronizationDetail = syncCheck.second
                )
            )
        }
        assertEquals(GlobalProcessStatus.COMPLETED, deleteState!!.processStatus)
        assertEquals(100, deleteState.progressPercentage)
        assertNull(dao.getCustomerById(edited.id))
    }

    @Test
    fun workflow04_05_06_08_09_unitTransactionRenewalPdfAndPaymentProof_fullPipeline() = runBlocking {
        // 4. TAMBAH & EDIT UNIT FREEZER
        val unitId = repository.saveUnit(sampleUnit("SAF-300-A1", "GEA AB-318-R"))
        val unit = dao.getUnitById(unitId)!!
        val custId = repository.saveCustomer(sampleCustomer("Siti Rahayu", "3372025501900002", "Sukoharjo"))
        val customer = dao.getCustomerById(custId)!!

        val editUnitState = processManager.executeTrackedProcess(
            processName = "Memperbarui Unit Freezer...",
            idempotencyKey = "EDIT_UNIT_$unitId",
            totalSteps = 4
        ) { handle ->
            handle.updateProgress(1, 25, "Menyimpan perubahan unit...", ProcessStageBadge.PROCESSING)
            repository.saveUnit(unit.copy(lokasiUnit = "Cabang Kartasura"))
            handle.updateProgress(2, 70, "Menyinkronkan unit ke Google Drive...", ProcessStageBadge.SYNCING)
            val err = runImmediateDriveSync()
            handle.enterVerification(step = 4, percentage = 92)
            val local = processManager.verificationEngine.verifyLocalEntitySaved(ProcessVerificationEngine.ENTITY_UNIT, unitId, unit.uuid)
            val (drv, syn) = processManager.verificationEngine.verifyRecordCloudAndSyncState("UNIT:$unitId", unit.uuid, driveSyncError = err)
            ProcessExecutionOutcome(
                result = unitId,
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = local.status,
                    localDatabaseDetail = local.detail,
                    googleDriveStatus = drv.first,
                    googleDriveDetail = drv.second,
                    synchronizationStatus = syn.first,
                    synchronizationDetail = syn.second
                )
            )
        }
        assertEquals(GlobalProcessStatus.COMPLETED, editUnitState!!.processStatus)
        assertEquals(100, editUnitState.progressPercentage)

        // 5. MEMBUAT TRANSAKSI SEWA (8 tahap realistis + relasi + Generate Invoice PDF + Google Drive)
        val observedSteps = mutableListOf<Int>()
        val txEntity = RentalTransactionEntity(
            nomorInvoice = "INV-SAF-2026-0001",
            tanggalPemesanan = "2026-11-17",
            customerId = custId,
            namaPelangganSnapshot = customer.namaLengkap,
            unitId = unitId,
            namaUnitSnapshot = unit.namaUnit,
            kodeUnitSnapshot = unit.kodeUnit,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-17",
            tanggalAkhirSewa = "2026-12-17",
            tanggalJatuhTempo = "2026-12-17",
            hargaSewa = 275_000L,
            ongkir = 25_000L,
            totalTagihan = 300_000L,
            nominalPembayaran = 300_000L,
            metodePembayaran = "Transfer BCA",
            statusPembayaran = "Lunas",
            statusPenyewaan = "Dalam Penyewaan"
        )
        var generatedPdfFile: File? = null
        var createdTxId = 0L

        val txState = processManager.executeTrackedProcess(
            processName = "Menyimpan Transaksi Sewa...",
            idempotencyKey = "CREATE_TX_${txEntity.nomorInvoice}",
            totalSteps = 8
        ) { handle ->
            handle.updateProgress(1, 10, "Memvalidasi data transaksi...", ProcessStageBadge.PROCESSING)
            observedSteps.add(processManager.processState.value!!.progressPercentage)

            handle.updateProgress(2, 22, "Menyimpan data pelanggan & unit...", ProcessStageBadge.PROCESSING)
            observedSteps.add(processManager.processState.value!!.progressPercentage)

            handle.updateProgress(3, 36, "Menyimpan transaksi ke database lokal...", ProcessStageBadge.LOCAL_SAVED)
            createdTxId = repository.saveTransaction(txEntity)
            val savedTx = dao.getTransactionById(createdTxId)!!
            handle.markStageCompleted("LOCAL_DB")
            observedSteps.add(processManager.processState.value!!.progressPercentage)

            handle.updateProgress(4, 50, "Memperbarui status unit freezer...", ProcessStageBadge.LOCAL_SAVED)
            observedSteps.add(processManager.processState.value!!.progressPercentage)

            handle.updateProgress(5, 68, "Membuat berkas Invoice PDF...", ProcessStageBadge.PROCESSING)
            generatedPdfFile = InvoicePdfGenerator.generateInvoicePdf(
                context = context,
                transaction = savedTx,
                customer = customer,
                unit = dao.getUnitById(unitId),
                companyProfile = CompanyProfile(namaPerusahaan = "SEWAin Aja Freezer SOC"),
                defaultPerjanjianSewa = "Perjanjian sewa resmi."
            )
            handle.markStageCompleted("DOCS")
            observedSteps.add(processManager.processState.value!!.progressPercentage)

            handle.updateProgress(6, 84, "Menyinkronkan transaksi ke Google Drive...", ProcessStageBadge.UPLOADING)
            val driveErr = runImmediateDriveSync()
            observedSteps.add(processManager.processState.value!!.progressPercentage)

            handle.enterVerification(step = 7, percentage = 92, message = "Memverifikasi transaksi, PDF & Google Drive...")
            observedSteps.add(processManager.processState.value!!.progressPercentage)

            val localCheck = processManager.verificationEngine.verifyLocalEntitySaved(
                ProcessVerificationEngine.ENTITY_TRANSACTION, createdTxId, savedTx.uuid
            )
            val pdfCheck = processManager.verificationEngine.verifyPdfDocument(generatedPdfFile)
            val (drvCheck, synCheck) = processManager.verificationEngine.verifyRecordCloudAndSyncState(
                "TRANSACTION:$createdTxId", savedTx.uuid, driveSyncError = driveErr
            )
            ProcessExecutionOutcome(
                result = savedTx,
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = localCheck.status,
                    localDatabaseDetail = localCheck.detail,
                    documentationStatus = pdfCheck.status,
                    documentationDetail = pdfCheck.detail,
                    googleDriveStatus = drvCheck.first,
                    googleDriveDetail = drvCheck.second,
                    synchronizationStatus = synCheck.first,
                    synchronizationDetail = synCheck.second
                )
            )
        }
        assertEquals(listOf(10, 22, 36, 50, 68, 84, 92), observedSteps)
        assertEquals(GlobalProcessStatus.COMPLETED, txState!!.processStatus)
        assertEquals(100, txState.progressPercentage)
        assertNotNull(generatedPdfFile)
        assertTrue(generatedPdfFile!!.exists() && generatedPdfFile!!.length() > 64L)

        // 8. VERIFIKASI PDF KORUP/KOSONG TIDAK BOLEH 100%
        val corruptPdf = File(context.cacheDir, "corrupt_invoice.pdf").apply { writeText("NOT_A_PDF") }
        val corruptCheck = processManager.verificationEngine.verifyPdfDocument(corruptPdf)
        assertEquals(VerificationItemStatus.FAILED, corruptCheck.status)

        // 6. PERPANJANGAN SEWA
        val existingTx = dao.getTransactionById(createdTxId)!!
        val renewState = processManager.executeTrackedProcess(
            processName = "Memperpanjang Masa Sewa...",
            idempotencyKey = "RENEW_TX_$createdTxId",
            totalSteps = 4
        ) { handle ->
            handle.updateProgress(1, 30, "Memperbarui tanggal jatuh tempo sewa...", ProcessStageBadge.PROCESSING)
            val renewed = existingTx.copy(durasiHari = 60, tanggalAkhirSewa = "2027-01-16", tanggalJatuhTempo = "2027-01-16")
            repository.saveTransaction(renewed)
            handle.updateProgress(2, 72, "Menyinkronkan perpanjangan ke Google Drive...", ProcessStageBadge.SYNCING)
            val err = runImmediateDriveSync()
            handle.enterVerification(step = 4, percentage = 92)
            val local = processManager.verificationEngine.verifyLocalEntitySaved(
                ProcessVerificationEngine.ENTITY_TRANSACTION, createdTxId, existingTx.uuid
            ) {
                if (dao.getTransactionById(createdTxId)?.durasiHari != 60) "Durasi perpanjangan belum tersimpan." else null
            }
            val (drv, syn) = processManager.verificationEngine.verifyRecordCloudAndSyncState("TRANSACTION:$createdTxId", existingTx.uuid, driveSyncError = err)
            ProcessExecutionOutcome(
                result = Unit,
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = local.status,
                    localDatabaseDetail = local.detail,
                    googleDriveStatus = drv.first,
                    googleDriveDetail = drv.second,
                    synchronizationStatus = syn.first,
                    synchronizationDetail = syn.second
                )
            )
        }
        assertEquals(GlobalProcessStatus.COMPLETED, renewState!!.processStatus)
        assertEquals(100, renewState.progressPercentage)

        // 7 & 9. UPLOAD DOKUMENTASI + BUKTI PEMBAYARAN
        val proofImg = createTempJpegFile("bukti_transfer_001.jpg")
        val payState = processManager.executeTrackedProcess(
            processName = "Menyimpan Bukti Pembayaran...",
            idempotencyKey = "SAVE_PAY_PROOF_$createdTxId",
            totalSteps = 5
        ) { handle ->
            handle.updateProgress(1, 25, "Menyimpan bukti pembayaran & memperbarui transaksi...", ProcessStageBadge.PROCESSING)
            val proofId = repository.savePaymentProof(
                PaymentProofEntity(
                    transactionId = createdTxId,
                    tanggalPembayaran = "2026-11-17",
                    nominalPembayaran = 300_000L,
                    metodePembayaran = "Transfer BCA",
                    namaPengirim = customer.namaLengkap,
                    catatanPembayaran = "Pelunasan bulan ke-1",
                    fotoBuktiUri = proofImg.absolutePath
                )
            )
            val persistedProof = dao.getPaymentProofById(proofId)!!
            handle.updateProgress(3, 72, "Menyinkronkan bukti pembayaran ke Google Drive...", ProcessStageBadge.UPLOADING)
            val err = runImmediateDriveSync()
            handle.enterVerification(step = 5, percentage = 92)
            val local = processManager.verificationEngine.verifyLocalEntitySaved(
                ProcessVerificationEngine.ENTITY_PAYMENT, proofId, persistedProof.uuid
            )
            val (docStatus, docDetail) = processManager.verificationEngine.verifyDocumentationFiles(
                localFilePaths = listOf(proofImg.absolutePath),
                requireCloudUploaded = false
            )
            val (drv, syn) = processManager.verificationEngine.verifyRecordCloudAndSyncState(
                "PAYMENT:$proofId", persistedProof.uuid, driveSyncError = err
            )
            ProcessExecutionOutcome(
                result = persistedProof,
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = local.status,
                    localDatabaseDetail = local.detail,
                    documentationStatus = docStatus,
                    documentationDetail = docDetail,
                    googleDriveStatus = drv.first,
                    googleDriveDetail = drv.second,
                    synchronizationStatus = syn.first,
                    synchronizationDetail = syn.second
                )
            )
        }
        assertEquals(GlobalProcessStatus.COMPLETED, payState!!.processStatus)
        assertEquals(100, payState.progressPercentage)
        assertEquals(VerificationItemStatus.SUCCESS, payState.verificationReport!!.documentationStatus)
    }

    @Test
    fun workflow10_11_fullGoogleDriveSyncAndFactoryReset_strictVerifiedCompletion() = runBlocking {
        repository.saveCustomer(sampleCustomer("Pelanggan Reset Test", "3372091111990009", "Karanganyar"))

        // 10. FULL GOOGLE DRIVE SYNC
        val syncState = processManager.executeTrackedProcess(
            processName = "Sinkronisasi Google Drive...",
            idempotencyKey = "FULL_SYNC_TEST",
            totalSteps = 4
        ) { handle ->
            handle.updateProgress(1, 30, "Mengunggah perubahan data ke Google Drive...", ProcessStageBadge.UPLOADING)
            val batch = syncRepository.processSyncQueue(forceRetryFailed = false, ignoreBackoffTimers = true).getOrThrow()
            handle.enterVerification(step = 4, percentage = 92)
            val report = processManager.verificationEngine.verifyFullSyncBatch(batch)
            ProcessExecutionOutcome(result = batch, verificationReport = report)
        }
        assertEquals(GlobalProcessStatus.COMPLETED, syncState!!.processStatus)
        assertEquals(100, syncState.progressPercentage)
        assertTrue(syncState.verificationReport!!.allRequiredPassed)

        // 11. FACTORY RESET LOKAL & CLOUD
        val resetState = processManager.executeTrackedProcess(
            processName = "Factory Reset Lokal & Cloud...",
            idempotencyKey = "FACTORY_RESET_TEST",
            totalSteps = 4
        ) { handle ->
            handle.updateProgress(1, 35, "Menghapus data lokal & file Google Drive...", ProcessStageBadge.UPLOADING)
            val resetRes = syncRepository.executeResetLocalAndCloud().getOrThrow()
            handle.enterVerification(step = 4, percentage = 92)
            val report = processManager.verificationEngine.verifyFactoryResetCompleted(resetRes, includeCloud = true)
            ProcessExecutionOutcome(result = resetRes, verificationReport = report)
        }
        assertEquals(GlobalProcessStatus.COMPLETED, resetState!!.processStatus)
        assertEquals(100, resetState.progressPercentage)
        assertEquals(0, dao.getAllCustomersOnce().size)
        assertEquals(0, dao.getAllUnitsOnce().size)
        assertEquals(0, dao.getAllTransactionsOnce().size)
    }

    // =========================================================================
    // B. ALL 7 REQUIRED FAULT, EDGE-CASE & STRESS SIMULATIONS
    // =========================================================================

    @Test
    fun fault01_slowInternet_progressStaysBelow100AndDialogCannotBeDismissedMidFlight() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val startedUpload = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.Default) {
            processManager.executeTrackedProcess(
                processName = "Menyimpan Transaksi...",
                idempotencyKey = "SLOW_INTERNET_OP",
                totalSteps = 4
            ) { handle ->
                handle.updateProgress(1, 25, "Menyimpan ke database lokal...", ProcessStageBadge.LOCAL_SAVED)
                handle.updateProgress(2, 62, "Mengunggah ke Google Drive (jaringan lambat)...", ProcessStageBadge.UPLOADING)
                startedUpload.complete(Unit)
                gate.await()
                handle.enterVerification(step = 4, percentage = 94)
                ProcessExecutionOutcome(
                    result = Unit,
                    verificationReport = ProcessVerificationReport(
                        localDatabaseStatus = VerificationItemStatus.SUCCESS,
                        googleDriveStatus = VerificationItemStatus.SUCCESS,
                        synchronizationStatus = VerificationItemStatus.SUCCESS
                    )
                )
            }
        }

        startedUpload.await()
        delay(250L)
        val inFlight = processManager.processState.value!!
        assertTrue(inFlight.isActive)
        assertEquals(62, inFlight.progressPercentage)
        assertEquals(ProcessStageBadge.UPLOADING, inFlight.stageBadge)
        assertTrue("Progress must stay strictly below 100% while uploading", inFlight.progressPercentage < 100)

        processManager.dismissDialog()
        assertTrue("Dialog must remain visible while backend work is active", processManager.processState.value!!.isVisible)

        gate.complete(Unit)
        job.join()
        val done = processManager.processState.value!!
        assertEquals(GlobalProcessStatus.COMPLETED, done.processStatus)
        assertEquals(100, done.progressPercentage)
    }

    @Test
    fun fault02_offlineInternet_marksPartialLocalSaved_never100PercentOrSelesai() = runBlocking {
        isNetworkOnline.set(false)
        val cust = sampleCustomer("Pelanggan Offline Solo", "3372051234567890", "Wonogiri")

        val state = processManager.executeTrackedProcess(
            processName = "Menyimpan Pelanggan...",
            idempotencyKey = "OFFLINE_SAVE_CUST",
            totalSteps = 4,
            retryAction = {}
        ) { handle ->
            handle.updateProgress(1, 35, "Menyimpan ke SQLite...", ProcessStageBadge.LOCAL_SAVED)
            val id = repository.saveCustomer(cust)
            val persisted = dao.getCustomerById(id)!!
            handle.markStageCompleted("LOCAL_DB")
            handle.updateProgress(2, 70, "Mencoba sinkronisasi ke Google Drive...", ProcessStageBadge.SYNCING)
            val driveErr = runImmediateDriveSync()
            handle.enterVerification(step = 4, percentage = 92)
            val local = processManager.verificationEngine.verifyLocalEntitySaved(ProcessVerificationEngine.ENTITY_CUSTOMER, id, persisted.uuid)
            val (drv, syn) = processManager.verificationEngine.verifyRecordCloudAndSyncState("CUSTOMER:$id", persisted.uuid, driveSyncError = driveErr)
            ProcessExecutionOutcome(
                result = persisted,
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = local.status,
                    localDatabaseDetail = local.detail,
                    googleDriveStatus = drv.first,
                    googleDriveDetail = drv.second,
                    synchronizationStatus = syn.first,
                    synchronizationDetail = syn.second,
                    requireCloudForFullCompletion = true
                ),
                partialSummaryMessage = driveErr
            )
        }

        assertNotNull(state)
        assertEquals(GlobalProcessStatus.PARTIAL_SUCCESS, state!!.processStatus)
        assertEquals(ProcessStageBadge.PARTIAL_LOCAL, state.stageBadge)
        assertTrue("Offline partial save must NEVER show 100%", state.progressPercentage in 50..85)
        assertTrue(state.canRetry)
        assertEquals(0L, state.autoDismissDelayMillis)
        assertEquals(1, dao.getAllCustomersOnce().size)
    }

    @Test
    fun fault03_googleDriveFailure500ThenIdempotentRetry_skipsLocalStageWithZeroDuplicates() = runBlocking {
        fakeBackend.forceErrorCode = 500
        val idempKey = "RETRY_DRIVE_IDEMPOTENT_CUST"
        val localInsertExecutions = AtomicInteger(0)

        suspend fun runSaveWithCheckpoint(): GlobalProcessState? {
            return processManager.executeTrackedProcess(
                processName = "Menyimpan Pelanggan...",
                idempotencyKey = idempKey,
                totalSteps = 4,
                retryAction = {}
            ) { handle ->
                val savedId: Long = if (!handle.isStageCompleted("LOCAL_DB")) {
                    handle.updateProgress(1, 30, "Menyimpan ke SQLite...", ProcessStageBadge.PROCESSING)
                    localInsertExecutions.incrementAndGet()
                    val id = repository.saveCustomer(sampleCustomer("Pelanggan Retry Safe", "3372089988776655", "Boyolali"))
                    handle.putArtifact("savedCustomerId", id)
                    handle.markStageCompleted("LOCAL_DB")
                    id
                } else {
                    handle.getArtifact<Long>("savedCustomerId")!!
                }
                val persisted = dao.getCustomerById(savedId)!!
                handle.updateProgress(2, 70, "Mengunggah ke Google Drive...", ProcessStageBadge.UPLOADING)
                val driveErr = runImmediateDriveSync()
                handle.enterVerification(step = 4, percentage = 92)
                val local = processManager.verificationEngine.verifyLocalEntitySaved(ProcessVerificationEngine.ENTITY_CUSTOMER, savedId, persisted.uuid)
                val (drv, syn) = processManager.verificationEngine.verifyRecordCloudAndSyncState("CUSTOMER:$savedId", persisted.uuid, driveSyncError = driveErr)
                ProcessExecutionOutcome(
                    result = persisted,
                    verificationReport = ProcessVerificationReport(
                        localDatabaseStatus = local.status,
                        localDatabaseDetail = local.detail,
                        googleDriveStatus = drv.first,
                        googleDriveDetail = drv.second,
                        synchronizationStatus = syn.first,
                        synchronizationDetail = syn.second,
                        requireCloudForFullCompletion = true
                    ),
                    partialSummaryMessage = driveErr
                )
            }
        }

        // Attempt 1: Google Drive returns HTTP 500 -> PARTIAL_SUCCESS, LOCAL_DB checkpoint recorded
        val attempt1 = runSaveWithCheckpoint()!!
        assertEquals(GlobalProcessStatus.PARTIAL_SUCCESS, attempt1.processStatus)
        assertTrue(attempt1.progressPercentage <= 85)
        assertTrue(attempt1.completedStageIds.contains("LOCAL_DB"))
        assertEquals(1, localInsertExecutions.get())
        assertEquals(1, dao.getAllCustomersOnce().size)

        // Attempt 2: Google Drive recovers -> Retry skips LOCAL_DB (0 duplicate rows) and completes 100%
        fakeBackend.forceErrorCode = null
        syncRepository.recoverFailedSyncOperations()
        val attempt2 = runSaveWithCheckpoint()!!
        assertEquals(GlobalProcessStatus.COMPLETED, attempt2.processStatus)
        assertEquals(100, attempt2.progressPercentage)
        assertEquals("LOCAL_DB must be skipped on retry — zero duplicate inserts!", 1, localInsertExecutions.get())
        assertEquals("SQLite must still contain exactly 1 customer row", 1, dao.getAllCustomersOnce().size)
    }

    @Test
    fun fault04_missingOrFailedDocumentationPhoto_blocksVerifiedCompletion() = runBlocking {
        val missingPhotoPath = File(context.cacheDir, "does_not_exist_${UUID.randomUUID()}.jpg").absolutePath
        val state = processManager.executeTrackedProcess(
            processName = "Mengunggah Foto KTP...",
            idempotencyKey = "MISSING_PHOTO_TEST",
            totalSteps = 3,
            retryAction = {}
        ) { handle ->
            handle.updateProgress(1, 50, "Menyimpan berkas foto...", ProcessStageBadge.PROCESSING)
            handle.enterVerification(step = 3, percentage = 92)
            val (docStatus, docDetail) = processManager.verificationEngine.verifyDocumentationFiles(
                localFilePaths = listOf(missingPhotoPath),
                requireCloudUploaded = false
            )
            ProcessExecutionOutcome(
                result = Unit,
                verificationReport = ProcessVerificationReport(
                    localDatabaseStatus = VerificationItemStatus.SUCCESS,
                    documentationStatus = docStatus,
                    documentationDetail = docDetail,
                    googleDriveStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
                    synchronizationStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
                    requireCloudForFullCompletion = false
                )
            )
        }
        assertNotNull(state)
        assertEquals(GlobalProcessStatus.FAILED, state!!.processStatus)
        assertTrue(state.progressPercentage < 100)
        assertEquals(VerificationItemStatus.FAILED, state.verificationReport!!.documentationStatus)
    }

    @Test
    fun fault05_databaseFailure_stopsImmediatelyBeforeDriveAndShowsFailedState() = runBlocking {
        val uploadsBefore = fakeBackend.uploadAttemptCount.get()
        val state = processManager.executeTrackedProcess<Unit>(
            processName = "Menyimpan Unit Freezer...",
            idempotencyKey = "DB_FAILURE_TEST",
            totalSteps = 4,
            retryAction = {}
        ) { handle ->
            handle.updateProgress(1, 20, "Menyimpan ke database SQLite...", ProcessStageBadge.PROCESSING)
            throw IllegalStateException("SQLite disk full: gagal menulis tabel freezer_units")
        }

        assertNotNull(state)
        assertEquals(GlobalProcessStatus.FAILED, state!!.processStatus)
        assertEquals(ProcessStageBadge.FAILED, state.stageBadge)
        assertTrue("Progress must stop below 100% on DB error", state.progressPercentage < 100)
        assertTrue(state.errorMessage.contains("SQLite disk full"))
        assertEquals("Google Drive must NOT be called after DB failure", uploadsBefore, fakeBackend.uploadAttemptCount.get())
    }

    @Test
    fun fault06_appClosedMidProcess_detectedOnNextLaunchNeverTreatedAsComplete() {
        val prefs = context.getSharedPreferences("sewain_global_process_manager_prefs", Context.MODE_PRIVATE)
        val inFlightJson = JSONObject().apply {
            put("processId", "proc-killed-123")
            put("idempotencyKey", "CREATE_TX_KILLED")
            put("processName", "Menyimpan Transaksi Sewa...")
            put("currentStep", 4)
            put("totalSteps", 8)
            put("progressPercentage", 52)
            put("currentActivityMessage", "Mengunggah dokumentasi ke Google Drive...")
            put("startedAt", System.currentTimeMillis() - 5000L)
        }
        prefs.edit().putString("active_in_flight_process_json", inFlightJson.toString()).commit()

        val restartedManager = GlobalProcessManager(context, dao, defaultAutoDismissMillis = 0L)
        val notice = restartedManager.interruptedProcessNotice.value
        assertNotNull("Restart must detect interrupted mid-flight process", notice)
        assertEquals(GlobalProcessStatus.FAILED, notice!!.processStatus)
        assertEquals(52, notice.progressPercentage)
        assertTrue(notice.errorMessage.contains("Menyimpan Transaksi Sewa"))
        assertTrue(prefs.getString("active_in_flight_process_json", "").isNullOrEmpty())
    }

    @Test
    fun fault07_rapidDuplicateButtonTaps_blockedByIdempotencyKeyWithSingleDbWrite() = runBlocking {
        val executions = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()

        val jobs = (1..5).map { idx ->
            async(Dispatchers.Default) {
                if (idx > 1) started.await()
                processManager.executeTrackedProcess(
                    processName = "Menyimpan Pelanggan...",
                    idempotencyKey = "RAPID_TAP_SAME_CUSTOMER",
                    totalSteps = 3
                ) { handle ->
                    executions.incrementAndGet()
                    handle.updateProgress(1, 35, "Menyimpan ke SQLite...", ProcessStageBadge.PROCESSING)
                    val id = repository.saveCustomer(sampleCustomer("Anti Double Submit Customer", "3372090001112223", "Klaten"))
                    started.complete(Unit)
                    gate.await()
                    val persisted = dao.getCustomerById(id)!!
                    handle.enterVerification(step = 3, percentage = 92)
                    val local = processManager.verificationEngine.verifyLocalEntitySaved(ProcessVerificationEngine.ENTITY_CUSTOMER, id, persisted.uuid)
                    ProcessExecutionOutcome(
                        result = persisted,
                        verificationReport = ProcessVerificationReport(
                            localDatabaseStatus = local.status,
                            localDatabaseDetail = local.detail,
                            googleDriveStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
                            synchronizationStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
                            requireCloudForFullCompletion = false
                        )
                    )
                }
            }
        }

        started.await()
        delay(80L)
        gate.complete(Unit)
        val results = jobs.awaitAll()

        val completedCount = results.count { it?.processStatus == GlobalProcessStatus.COMPLETED }
        val ignoredCount = results.count { it == null }
        assertEquals("Exactly 1 execution must succeed", 1, completedCount)
        assertEquals("All 4 duplicate rapid taps must be blocked", 4, ignoredCount)
        assertEquals("Backend block must run strictly once", 1, executions.get())
        assertEquals("SQLite must have strictly 1 row (0 duplicates)", 1, dao.getAllCustomersOnce().size)
    }

    // =========================================================================
    // C. COMPOSE UI TESTS FOR SMART LOADING DIALOG & RECOVERY BANNER
    // =========================================================================

    @Test
    fun uiTest_smartLoadingDialog_rendersProcessingPartialCompletedFailedAndRetryButton() {
        var retryClicks = 0
        val currentState = androidx.compose.runtime.mutableStateOf<GlobalProcessState?>(
            GlobalProcessState(
                processId = "ui-1",
                idempotencyKey = "UI_TEST",
                processName = "Menyimpan Transaksi Sewa...",
                currentStep = 4,
                totalSteps = 8,
                progressPercentage = 50,
                currentActivityMessage = "Mengunggah dokumentasi ke Google Drive...",
                processStatus = GlobalProcessStatus.PROCESSING,
                stageBadge = ProcessStageBadge.UPLOADING,
                isVisible = true
            )
        )

        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                SmartLoadingProcessDialog(
                    state = currentState.value,
                    onRetry = { retryClicks++ },
                    onDismiss = { currentState.value = currentState.value?.copy(isVisible = false) }
                )
            }
        }

        // 1. Active UPLOADING state (50%, 50/100)
        composeRule.onNodeWithTag("smart_loading_process_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_loading_status_badge").assertTextContains("UPLOADING")
        composeRule.onNodeWithTag("smart_loading_percentage_text").assertTextContains("50%")
        composeRule.onNodeWithTag("smart_loading_fraction_text").assertTextContains("50/100")

        // 2. PARTIAL_SUCCESS state (⚠ TERSIMPAN LOKAL, 75%, Retry button clickable)
        currentState.value = currentState.value!!.copy(
            progressPercentage = 75,
            processStatus = GlobalProcessStatus.PARTIAL_SUCCESS,
            stageBadge = ProcessStageBadge.PARTIAL_LOCAL,
            failedStageName = "Sinkronisasi Google Drive",
            errorMessage = "Koneksi internet terputus. Data tetap aman di perangkat.",
            canRetry = true,
            verificationReport = ProcessVerificationReport(
                localDatabaseStatus = VerificationItemStatus.SUCCESS,
                localDatabaseDetail = "Tersimpan & terverifikasi di SQLite",
                documentationStatus = VerificationItemStatus.SUCCESS,
                documentationDetail = "1 berkas PDF valid",
                googleDriveStatus = VerificationItemStatus.PARTIAL_OFFLINE,
                googleDriveDetail = "Menunggu koneksi internet",
                synchronizationStatus = VerificationItemStatus.PARTIAL_OFFLINE,
                synchronizationDetail = "Masuk antrean coba ulang"
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_loading_title").assertTextContains("DATA TERSIMPAN LOKAL", substring = true)
        composeRule.onNodeWithTag("smart_loading_status_badge").assertTextContains("TERSIMPAN LOKAL", substring = true)
        composeRule.onNodeWithTag("smart_loading_percentage_text").assertTextContains("75%")
        composeRule.onNodeWithText("Database lokal").assertIsDisplayed()
        composeRule.onNodeWithText("Google Drive").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_loading_btn_retry").assertIsDisplayed().performClick()
        assertEquals(1, retryClicks)

        // 3. COMPLETED state (✓ SELESAI!, 100%, 100/100)
        currentState.value = currentState.value!!.copy(
            progressPercentage = 100,
            processStatus = GlobalProcessStatus.COMPLETED,
            stageBadge = ProcessStageBadge.COMPLETED,
            canRetry = false,
            errorMessage = "",
            verificationReport = ProcessVerificationReport(
                localDatabaseStatus = VerificationItemStatus.SUCCESS,
                documentationStatus = VerificationItemStatus.SUCCESS,
                googleDriveStatus = VerificationItemStatus.SUCCESS,
                synchronizationStatus = VerificationItemStatus.SUCCESS
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_loading_title").assertTextContains("SELESAI", substring = true)
        composeRule.onNodeWithTag("smart_loading_percentage_text").assertTextContains("100%")
        composeRule.onNodeWithTag("smart_loading_fraction_text").assertTextContains("100/100")
    }

    @Test
    fun uiTest_interruptedProcessRecoveryBanner_displaysAndTriggersSyncAction() {
        var syncClicked = false
        val interrupted = GlobalProcessState(
            processId = "int-1",
            idempotencyKey = "INT_KEY",
            processName = "Menyimpan Transaksi Sewa...",
            progressPercentage = 60,
            processStatus = GlobalProcessStatus.FAILED,
            stageBadge = ProcessStageBadge.FAILED,
            errorMessage = "Aplikasi sempat ditutup saat proses berlangsung (60%)."
        )
        composeRule.setContent {
            SEWAinAjaFreezerTheme {
                InterruptedProcessRecoveryBanner(
                    interrupted = interrupted,
                    onSyncNow = { syncClicked = true },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("interrupted_process_recovery_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_recover_interrupted_sync").assertIsDisplayed().performClick()
        assertTrue(syncClicked)
    }
}
