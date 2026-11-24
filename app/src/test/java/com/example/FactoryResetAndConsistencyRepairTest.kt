package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.DataConsistencyAuditReport
import com.example.data.local.DriveLocalSyncQueueEntity
import com.example.data.local.DriveMirrorDashboardState
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.SyncTechnicalLogEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.GoogleDriveRestClient
import com.example.ui.components.DriveMirrorSyncDashboardCard
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * BAGIAN 9 & BAGIAN 12: SIMULASI WAJIB + VERIFIKASI KONSISTENSI DATA, FACTORY RESET & SINKRONISASI.
 * Menguji:
 * - Simulasi wajib BAGIAN 9: 100 pelanggan + 50 unit -> sync -> reset lokal & cloud ->
 *   tambah 1 pelanggan + 1 unit baru -> simulasi restart -> verifikasi STRICT 1 pelanggan & 1 unit.
 * - Offline mutation -> online FIFO sync drain.
 * - Edit data via PATCH tanpa duplikasi.
 * - Replace photo (1 foto aktif di local & cloud).
 * - Hapus unit & pelanggan -> terhapus dari Drive & tercatat tombstone.
 * - Error injection HTTP 500 saat Delete Cloud -> gagal terang-terangan dengan File ID/Nama & log teknis.
 * - Penolakan STALE_OPERATION saat generasi berubah (GENERATION_001 vs GENERATION_002).
 * - Audit konsistensi SQLite vs Google Drive (`runDataConsistencyAudit`) tanpa auto-merge.
 * - Keamanan non-app files & HISTORICAL_BACKUP ZIP archives tetap aman saat Factory Reset.
 * - UI Dashboard: datasetGeneration badge, Audit Konsistensi report & Log Teknis expandable panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FactoryResetAndConsistencyRepairTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FreezerRepository
    private lateinit var mirrorEngine: GoogleDriveMirrorSyncEngine
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeBackend: RepairTestFakeDriveBackend

    class RepairTestFakeDriveBackend {
        data class DriveObj(
            val id: String,
            var name: String,
            var mimeType: String,
            var parentId: String,
            var rawBody: ByteArray,
            var jsonPayloadText: String,
            var checksumSha256: String,
            var appId: String = "",
            var applicationId: String = "",
            var recordUuid: String = "",
            var datasetGeneration: String = "",
            var trashed: Boolean = false,
            var patchCount: Int = 0
        )

        private val idSeq = AtomicInteger(5000)
        val storage = ConcurrentHashMap<String, DriveObj>()
        val failDeleteForIds = ConcurrentHashMap.newKeySet<String>()
        val postUploadCount = AtomicInteger(0)
        val patchCount = AtomicInteger(0)

        fun seedExternalObject(
            id: String,
            name: String,
            mimeType: String,
            parentId: String,
            payload: String = "",
            appId: String = ""
        ): DriveObj {
            val obj = DriveObj(
                id = id,
                name = name,
                mimeType = mimeType,
                parentId = parentId,
                rawBody = payload.toByteArray(Charsets.UTF_8),
                jsonPayloadText = payload,
                checksumSha256 = "",
                appId = appId,
                datasetGeneration = "GENERATION_001"
            )
            storage[id] = obj
            return obj
        }

        fun activeCustomerFiles(): List<DriveObj> {
            val custFolderIds = storage.values.filter {
                !it.trashed && it.mimeType == GoogleDriveRestClient.FOLDER_MIME &&
                    it.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_CUSTOMERS
            }.map { it.id }.toSet()
            return storage.values.filter {
                !it.trashed && it.mimeType != GoogleDriveRestClient.FOLDER_MIME &&
                    it.name.endsWith(".json") && it.name != "customers.json" &&
                    (it.parentId in custFolderIds || it.name.startsWith("CUSTOMER_"))
            }
        }

        fun activeUnitFiles(): List<DriveObj> {
            val unitFolderIds = storage.values.filter {
                !it.trashed && it.mimeType == GoogleDriveRestClient.FOLDER_MIME &&
                    it.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_UNITS
            }.map { it.id }.toSet()
            return storage.values.filter {
                !it.trashed && it.mimeType != GoogleDriveRestClient.FOLDER_MIME &&
                    it.name.endsWith(".json") && it.name != "units.json" &&
                    (it.parentId in unitFolderIds || it.name.startsWith("UNIT_"))
            }
        }

        fun activeKtpPhotosForCustomer(customerId: Long): List<DriveObj> =
            storage.values.filter { !it.trashed && it.name == "PHOTO_KTP_ID_${customerId}.jpg" }

        fun activeMirrorDataFiles(): List<DriveObj> =
            storage.values.filter { obj ->
                !obj.trashed &&
                    obj.mimeType != GoogleDriveRestClient.FOLDER_MIME &&
                    obj.name != GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME &&
                    !obj.name.endsWith(".zip", ignoreCase = true) &&
                    !obj.name.startsWith("personal_")
            }

        fun activeManifestFile(): DriveObj? =
            storage.values.firstOrNull { !it.trashed && it.name == GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME }

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
                    val mimeType = metaJson.optString("mimeType", "application/octet-stream")
                    val parentsArr = metaJson.optJSONArray("parents")
                    val parentId = if (parentsArr != null && parentsArr.length() > 0) parentsArr.optString(0, "root") else "root"
                    val appProps = metaJson.optJSONObject("appProperties")
                    val checksum = appProps?.optString("checksum", appProps.optString("checksumSha256", "")).orEmpty()
                    val appIdProp = appProps?.optString("appId", "").orEmpty()
                    val applicationIdProp = appProps?.optString("applicationId", "").orEmpty()
                    val recUuid = appProps?.optString("record_uuid", appProps.optString("recordUuid", "")).orEmpty()
                    val gen = appProps?.optString("generation_id", appProps.optString("datasetGeneration", "")).orEmpty()

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
                            if (checksum.isNotBlank()) existing.checksumSha256 = checksum
                            if (appIdProp.isNotBlank()) existing.appId = appIdProp
                            if (applicationIdProp.isNotBlank()) existing.applicationId = applicationIdProp
                            if (recUuid.isNotBlank()) existing.recordUuid = recUuid
                            if (gen.isNotBlank()) existing.datasetGeneration = gen
                            existing.patchCount += 1
                            patchCount.incrementAndGet()
                            MockResponse().setResponseCode(200).setBody(toJson(existing))
                        }
                    } else {
                        val newId = "drv_obj_${idSeq.incrementAndGet()}"
                        val obj = DriveObj(
                            id = newId,
                            name = name,
                            mimeType = mimeType,
                            parentId = parentId,
                            rawBody = payloadBytes,
                            jsonPayloadText = payloadText,
                            checksumSha256 = checksum,
                            appId = appIdProp,
                            applicationId = applicationIdProp,
                            recordUuid = recUuid,
                            datasetGeneration = gen
                        )
                        storage[newId] = obj
                        postUploadCount.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody(toJson(obj))
                    }
                }

                if (path.startsWith("/drive/v3/files")) {
                    val subPath = path.removePrefix("/drive/v3/files")
                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("POST", ignoreCase = true)) {
                        val json = runCatching { JSONObject(bodyText) }.getOrNull() ?: JSONObject()
                        val folderName = json.optString("name", "Folder")
                        val mime = json.optString("mimeType", GoogleDriveRestClient.FOLDER_MIME)
                        val parents = json.optJSONArray("parents")
                        val parentId = if (parents != null && parents.length() > 0) parents.optString(0, "root") else "root"
                        val newId = "drv_fld_${idSeq.incrementAndGet()}"
                        val obj = DriveObj(
                            id = newId,
                            name = folderName,
                            mimeType = mime,
                            parentId = parentId,
                            rawBody = ByteArray(0),
                            jsonPayloadText = "",
                            checksumSha256 = ""
                        )
                        storage[newId] = obj
                        return MockResponse().setResponseCode(200).setBody(toJson(obj))
                    }

                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("GET", ignoreCase = true)) {
                        val decodedQuery = decodeQueryParam(path, "q")
                        val matches = filterByQuery(decodedQuery)
                        val arr = JSONArray()
                        matches.forEach { arr.put(JSONObject(toJson(it))) }
                        return MockResponse().setResponseCode(200).setBody(JSONObject().put("files", arr).toString())
                    }

                    val fileId = subPath.removePrefix("/").substringBefore("?").trim()
                    if (method.equals("DELETE", ignoreCase = true)) {
                        if (fileId in failDeleteForIds) {
                            return MockResponse().setResponseCode(500).setBody("""{"error":{"message":"Simulated Drive HTTP 500 Delete Failure"}}""")
                        }
                        storage.remove(fileId)
                        return MockResponse().setResponseCode(204)
                    }
                    if (method.equals("GET", ignoreCase = true)) {
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            return MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}""")
                        }
                        return if (path.contains("alt=media")) {
                            MockResponse().setResponseCode(200).setBody(existing.jsonPayloadText)
                        } else {
                            MockResponse().setResponseCode(200).setBody(toJson(existing))
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
                    "recordUuid", "record_uuid" -> obj.recordUuid == appPropVal
                    "datasetGeneration", "generation_id" -> obj.datasetGeneration == appPropVal
                    else -> true
                }
                okName && okParent && okFolder && okNonFolder && okAppProp
            }
        }

        private fun decodeQueryParam(fullPath: String, key: String): String {
            val queryPart = fullPath.substringAfter("?", "")
            if (queryPart.isBlank()) return ""
            for (pair in queryPart.split("&")) {
                val kv = pair.split("=", limit = 2)
                if (kv.size == 2 && kv[0] == key) return URLDecoder.decode(kv[1], "UTF-8")
            }
            return ""
        }

        private fun toJson(obj: DriveObj): String {
            val sizeBytes = obj.rawBody.size.coerceAtLeast(64)
            return JSONObject().apply {
                put("id", obj.id)
                put("name", obj.name)
                put("size", sizeBytes.toString())
                put("parents", JSONArray(listOf(obj.parentId)))
                put("createdTime", "2026-11-16T10:00:00Z")
                put("modifiedTime", "2026-11-16T10:00:00Z")
                put("mimeType", obj.mimeType)
                put("sha256Checksum", obj.checksumSha256)
                put(
                    "appProperties",
                    JSONObject().apply {
                        put("checksumSha256", obj.checksumSha256)
                        put("checksum", obj.checksumSha256)
                        if (obj.appId.isNotBlank()) put("appId", obj.appId)
                        if (obj.applicationId.isNotBlank()) put("applicationId", obj.applicationId)
                        if (obj.recordUuid.isNotBlank()) {
                            put("recordUuid", obj.recordUuid)
                            put("record_uuid", obj.recordUuid)
                        }
                        if (obj.datasetGeneration.isNotBlank()) {
                            put("datasetGeneration", obj.datasetGeneration)
                            put("generation_id", obj.datasetGeneration)
                        }
                    }
                )
            }.toString()
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.APP_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fakeBackend = RepairTestFakeDriveBackend()
        mockWebServer = MockWebServer()
        mockWebServer.dispatcher = fakeBackend.dispatcher
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val driveClient = GoogleDriveRestClient(
            apiBaseUrl = "$baseUrl/drive/v3",
            uploadBaseUrl = "$baseUrl/upload/drive/v3"
        )
        mirrorEngine = GoogleDriveMirrorSyncEngine(
            context = context,
            dao = database.freezerDao(),
            driveClient = driveClient,
            accessTokenOverride = "mock_oauth_drive_token_v18"
        ).apply {
            autoTriggerSyncOnMutation = false
            simulatedOfflineOverride = false
        }

        repository = FreezerRepository(database.freezerDao()).apply {
            attachMirrorSyncEngine(mirrorEngine)
        }
    }

    @After
    fun tearDown() {
        database.close()
        mockWebServer.shutdown()
    }

    private fun makeCustomer(
        code: String,
        name: String,
        nik: String,
        phone: String,
        fotoKtpUri: String = ""
    ) = CustomerEntity(
        kodePelanggan = code,
        namaLengkap = name,
        nik = nik,
        jenisKelamin = "Laki-laki",
        nomorHp = phone,
        alamatLengkap = "Jl. Slamet Riyadi Surakarta",
        tanggalRegistrasi = "2026-11-16",
        fotoKtpDepanUri = fotoKtpUri
    )

    private fun makeUnit(code: String, name: String, serial: String) = FreezerUnitEntity(
        kodeUnit = code,
        namaUnit = name,
        jenisUnit = "Chest Freezer",
        owner = "Owner Test",
        hargaPembelian = 4_500_000L,
        kapasitasLiter = 300,
        cicilanPerBulan = 450_000L,
        merek = "RSA",
        model = "CF-310",
        nomorSeri = serial,
        statusUnit = "Tersedia",
        lokasiUnit = "Gudang Solo",
        tanggalPembelian = "2026-11-16"
    )

    private fun createPhotoFile(name: String, seed: String): File {
        val dir = File(context.filesDir, "SEWAinAjaFreezer/test_photos").apply { mkdirs() }
        return File(dir, name).apply {
            writeBytes("JPEG_TEST_$seed".toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * BAGIAN 9 — SIMULASI WAJIB:
     * 1. Masukkan 100 pelanggan + 50 unit -> sinkronkan ke Drive
     * 2. Lakukan Factory Reset (RESET LOKAL DAN CLOUD)
     * 3. Tambah 1 pelanggan (CUSTOMER_TEST_001) + 1 unit (UNIT_TEST_001)
     * 4. Tutup & buka kembali aplikasi (new engine instance + check startup guards)
     * Hasil wajib: Hanya ada 1 pelanggan & 1 unit, data lama (100+50 & Solo Raya) tidak muncul kembali!
     */
    @Test
    fun bagian9_mandatorySimulation_100Customers50Units_reset_addOneCustomerOneUnit_restartStrictly1And1() = runBlocking {
        // 1. Seed 100 customers & 50 units
        val oldCustomerIds = mutableListOf<Long>()
        for (i in 1..100) {
            val id = repository.saveCustomer(
                makeCustomer(
                    code = "CUST-SIM-$i",
                    name = "Pelanggan Simulasi $i",
                    nik = "337201000000${1000 + i}",
                    phone = "08120000${1000 + i}"
                )
            )
            oldCustomerIds.add(id)
        }
        val oldUnitIds = mutableListOf<Long>()
        for (i in 1..50) {
            val id = repository.saveUnit(
                makeUnit(
                    code = "UNIT-SIM-$i",
                    name = "Freezer Simulasi $i",
                    serial = "SN-SIM-$i"
                )
            )
            oldUnitIds.add(id)
        }

        val initialSync = mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(150, initialSync.syncedCount)
        assertEquals(100, fakeBackend.activeCustomerFiles().size)
        assertEquals(50, fakeBackend.activeUnitFiles().size)

        val oldDatasetId = mirrorEngine.getCurrentDatasetId()
        val preResetTs = System.currentTimeMillis()
        Thread.sleep(10)

        // 2. Execute Factory Reset (LOCAL_AND_CLOUD)
        val resetRes = mirrorEngine.executeResetLocalAndCloud().getOrThrow()
        assertEquals("LOCAL_AND_CLOUD", resetRes.resetMode)
        assertEquals("GENERATION_002", resetRes.datasetGeneration)
        assertEquals(0, database.freezerDao().getAllCustomersOnce().size)
        assertEquals(0, database.freezerDao().getAllUnitsOnce().size)
        assertEquals(0, fakeBackend.activeCustomerFiles().size)
        assertEquals(0, fakeBackend.activeUnitFiles().size)
        val resetManifest = JSONObject(fakeBackend.activeManifestFile()!!.jsonPayloadText)
        assertEquals("EMPTY_AFTER_RESET", resetManifest.getString("datasetStatus"))
        assertEquals("GENERATION_002", resetManifest.getString("datasetGeneration"))

        // 3. Add strictly 1 new customer & 1 new unit
        repository.saveCustomer(
            makeCustomer(
                code = "CUSTOMER_TEST_001",
                name = "CUSTOMER_TEST_001",
                nik = "3372090000000001",
                phone = "089900000001"
            )
        )
        repository.saveUnit(
            makeUnit(
                code = "UNIT_TEST_001",
                name = "UNIT_TEST_001",
                serial = "SN-TEST-001"
            )
        )
        mirrorEngine.processSyncQueue().getOrThrow()

        // 4. Simulate app restart (new engine instance + check startup guards)
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val restartedEngine = GoogleDriveMirrorSyncEngine(
            context = context,
            dao = database.freezerDao(),
            driveClient = GoogleDriveRestClient("$baseUrl/drive/v3", "$baseUrl/upload/drive/v3"),
            accessTokenOverride = "mock_oauth_drive_token_v18"
        ).apply {
            autoTriggerSyncOnMutation = false
            simulatedOfflineOverride = false
        }

        // Verify startup seed guard & anti-resurrection guard block Solo Raya & pre-reset records
        val appPrefs = context.getSharedPreferences(GoogleDriveMirrorSyncEngine.APP_PREFS_NAME, Context.MODE_PRIVATE)
        assertTrue(appPrefs.getBoolean("intentionally_cleared_db", false))
        assertTrue(appPrefs.getBoolean("initial_seed_already_ran", false))
        assertFalse(
            restartedEngine.canApplyRemoteRecordWithoutResurrection(
                localRecordKey = "CUSTOMER:${oldCustomerIds.first()}",
                remoteDatasetId = oldDatasetId,
                remoteTimestampMillis = preResetTs,
                remoteVersion = 1L
            )
        )
        assertFalse(
            restartedEngine.canApplyRemoteRecordWithoutResurrection(
                localRecordKey = "UNIT:${oldUnitIds.first()}",
                remoteDatasetId = oldDatasetId,
                remoteTimestampMillis = preResetTs,
                remoteVersion = 1L
            )
        )

        val startupState = restartedEngine.verifyConsistencyOnAppStartup().getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.STATUS_SYNCED, startupState.overallStatus)
        assertEquals("GENERATION_002", startupState.datasetGeneration)

        val localCustomers = database.freezerDao().getAllCustomersOnce()
        val localUnits = database.freezerDao().getAllUnitsOnce()
        assertEquals(1, localCustomers.size)
        assertEquals("CUSTOMER_TEST_001", localCustomers.single().kodePelanggan)
        assertEquals(1, localUnits.size)
        assertEquals("UNIT_TEST_001", localUnits.single().kodeUnit)

        assertEquals(1, fakeBackend.activeCustomerFiles().size)
        assertEquals(1, fakeBackend.activeUnitFiles().size)
        val audit = restartedEngine.runDataConsistencyAudit().getOrThrow()
        assertTrue("Audit after reset+add+restart must be 100% consistent", audit.isConsistent)
        assertEquals(1, audit.localCustomersCount)
        assertEquals(1, audit.cloudCustomersCount)
        assertEquals(1, audit.localUnitsCount)
        assertEquals(1, audit.cloudUnitsCount)
    }

    /**
     * BAGIAN 12 — Acceptance Tests:
     * Delete unit removes UNIT_<id>.json from Drive and creates tombstone;
     * HTTP 500 delete failure during Reset fails loudly with file ID + name and logs error in sync_technical_logs;
     * STALE_OPERATION rejects old generation queue entries;
     * Non-app files & HISTORICAL_BACKUP ZIPs stay untouched on Reset.
     */
    @Test
    fun bagian12_resetFailureVisibility_staleGenerationRejection_andBackupZipPreservation() = runBlocking {
        // Seed unrelated non-app file & HISTORICAL_BACKUP archive on Google Drive
        val personalFolder = fakeBackend.seedExternalObject(
            id = "personal_folder_01",
            name = "My_Personal_Photos",
            mimeType = GoogleDriveRestClient.FOLDER_MIME,
            parentId = "root"
        )
        val personalFile = fakeBackend.seedExternalObject(
            id = "personal_file_01",
            name = "personal_family.jpg",
            mimeType = "image/jpeg",
            parentId = personalFolder.id,
            payload = "family_pic"
        )

        val rootFolderRes = fakeBackend.seedExternalObject(
            id = "root_app_folder",
            name = GoogleDriveRestClient.BACKUP_FOLDER_NAME,
            mimeType = GoogleDriveRestClient.FOLDER_MIME,
            parentId = "root"
        )
        val histBackupFolder = fakeBackend.seedExternalObject(
            id = "hist_backup_folder",
            name = GoogleDriveMirrorSyncEngine.SUBFOLDER_HISTORICAL_BACKUP,
            mimeType = GoogleDriveRestClient.FOLDER_MIME,
            parentId = rootFolderRes.id
        )
        val backupZip = fakeBackend.seedExternalObject(
            id = "hist_zip_01",
            name = "SEWAIN_BACKUP_2026_11_16.zip",
            mimeType = "application/zip",
            parentId = histBackupFolder.id,
            payload = "zip_bytes"
        )

        // Add unit & customer, sync to Drive
        val unitId = repository.saveUnit(makeUnit("UNIT-DEL-01", "Freezer Uji Hapus", "SN-DEL-01"))
        val custId = repository.saveCustomer(makeCustomer("CUST-DEL-01", "Pelanggan Uji", "3372011111111111", "081111222333"))
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(1, fakeBackend.activeUnitFiles().size)
        assertEquals(1, fakeBackend.activeCustomerFiles().size)

        // Delete unit -> Drive deletes UNIT_<id>.json & tombstone synced
        val unit = database.freezerDao().getUnitById(unitId)!!
        repository.deleteUnit(unit)
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(0, database.freezerDao().getAllUnitsOnce().size)
        assertEquals(0, fakeBackend.activeUnitFiles().size)
        assertTrue(database.freezerDao().getDriveDeletionTombstoneByKey("UNIT:$unitId")!!.syncedToCloud)

        // Test HTTP 500 Delete Failure during Reset -> must FAIL loudly with exact file ID/name & log error
        val custDriveFile = fakeBackend.activeCustomerFiles().single()
        fakeBackend.failDeleteForIds.add(custDriveFile.id)

        val failedReset = mirrorEngine.executeResetLocalAndCloud()
        assertTrue("HTTP 500 delete failure must fail loudly, not silently succeed", failedReset.isFailure)
        val errMsg = failedReset.exceptionOrNull()?.message.orEmpty()
        assertTrue("Error message must identify failing file ID", errMsg.contains(custDriveFile.id))
        assertTrue("Error message must identify failing file name", errMsg.contains(custDriveFile.name))
        assertEquals(GoogleDriveMirrorSyncEngine.STATUS_FAILED, mirrorEngine.dashboardState.value.overallStatus)

        val techLogsAfterFail = database.freezerDao().getAllSyncTechnicalLogsOnce()
        assertTrue(
            "Technical logs must record reset failure",
            techLogsAfterFail.any { it.operationType == "RESET_LOCAL_AND_CLOUD" && it.errorCode.isNotBlank() }
        )

        // Now clear simulated HTTP 500 error & execute reset cleanly -> verify non-app & HISTORICAL_BACKUP ZIP survive
        fakeBackend.failDeleteForIds.remove(custDriveFile.id)
        val okReset = mirrorEngine.executeResetLocalAndCloud().getOrThrow()
        assertEquals("GENERATION_002", okReset.datasetGeneration)
        assertNotNull("Non-app file must never be deleted by Factory Reset", fakeBackend.storage[personalFile.id])
        assertNotNull("HISTORICAL_BACKUP ZIP must be preserved across Factory Reset", fakeBackend.storage[backupZip.id])

        // Test STALE_OPERATION rejection when a leftover queue item carries old GENERATION_001
        val staleCustomerId = custId + 99L
        database.freezerDao().upsertDriveLocalSyncQueueItem(
            DriveLocalSyncQueueEntity(
                operationId = "OP-STALE-GEN1",
                recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
                recordId = staleCustomerId,
                localRecordKey = "CUSTOMER:$staleCustomerId",
                operationType = GoogleDriveMirrorSyncEngine.OP_CREATE,
                version = 1L,
                timestampMillis = System.currentTimeMillis(),
                payloadJson = "{}",
                contentHash = "stale_hash",
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_PENDING_SYNC,
                datasetGenerationId = "GENERATION_001"
            )
        )
        val batchWithStale = mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(1, batchWithStale.staleSkippedCount)
        assertEquals(0, fakeBackend.activeCustomerFiles().size)
        val staleLogs = database.freezerDao().getAllSyncTechnicalLogsOnce()
        assertTrue(
            "STALE_OPERATION must be recorded in sync_technical_logs",
            staleLogs.any { it.errorCode == GoogleDriveMirrorSyncEngine.STATUS_STALE_OPERATION }
        )
    }

    @Test
    fun bagian10_12_consistencyAuditDetectsDriftAndPhotoReplacementWithoutAutoMerge() = runBlocking {
        val oldPhoto = createPhotoFile("ktp_v1.jpg", "KTP_V1_BYTES")
        val newPhoto = createPhotoFile("ktp_v2.jpg", "KTP_V2_BYTES")

        val cid = repository.saveCustomer(
            makeCustomer("CUST-AUD-1", "Sari Audit", "3372055555550001", "081234500001", oldPhoto.absolutePath)
        )
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(1, fakeBackend.activeKtpPhotosForCustomer(cid).size)

        // Replace photo -> strictly 1 verified photo locally & on Drive
        val saved = database.freezerDao().getCustomerById(cid)!!
        repository.saveCustomer(saved.copy(fotoKtpDepanUri = newPhoto.absolutePath))
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(1, fakeBackend.activeKtpPhotosForCustomer(cid).size)
        assertFalse(oldPhoto.exists())
        assertTrue(newPhoto.exists())

        // Inject an orphan stale file into Drive's ACTIVE_DATA folder -> Audit must flag drift WITHOUT auto-merging
        val activeDataFolderId = fakeBackend.storage.values.first {
            it.mimeType == GoogleDriveRestClient.FOLDER_MIME && it.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_ACTIVE_DATA
        }.id
        fakeBackend.seedExternalObject(
            id = "orphan_cust_999",
            name = "CUSTOMER_999.json",
            mimeType = "application/json",
            parentId = activeDataFolderId,
            payload = """{"id":999,"namaLengkap":"Orphan Lama"}"""
        )

        val auditWithDrift = mirrorEngine.runDataConsistencyAudit().getOrThrow()
        assertFalse("Audit must report inconsistency when orphan cloud file exists", auditWithDrift.isConsistent)
        assertTrue(auditWithDrift.staleRecords.any { it.contains("CUSTOMER_999.json") })
        // Crucially: SQLite local must NOT auto-merge CUSTOMER_999!
        assertEquals(1, database.freezerDao().getAllCustomersOnce().size)
        assertEquals(cid, database.freezerDao().getAllCustomersOnce().single().id)
    }

    @Test
    fun dashboardUi_rendersGenerationConsistencyAuditReportAndTechnicalLogs() {
        var auditClicked = false
        val report = DataConsistencyAuditReport(
            localCustomersCount = 1,
            localUnitsCount = 1,
            cloudCustomersCount = 1,
            cloudUnitsCount = 1,
            localDatasetGeneration = "GENERATION_002",
            cloudDatasetGeneration = "GENERATION_002",
            cloudManifestStatus = "ACTIVE",
            isConsistent = true,
            auditedAtDisplay = "16 Nov 2026, 12:00:00"
        )
        val techLog = SyncTechnicalLogEntity(
            id = 1L,
            operationTimeDisplay = "16 Nov 2026, 12:00:00",
            operationType = "RESET_LOCAL_AND_CLOUD",
            recordId = "dataset_reset_002",
            driveFileId = "drv_manifest_02",
            datasetGenerationId = "GENERATION_002",
            localStatus = "EMPTY_VERIFIED",
            cloudStatus = "EMPTY_VERIFIED",
            errorMessage = "Dihapus: lokal=150, cloud=151"
        )

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DriveMirrorSyncDashboardCard(
                        state = DriveMirrorDashboardState(
                            isDriveConnected = true,
                            connectedEmail = "owner@sewainajafreezer.com",
                            overallStatus = "SYNCED",
                            datasetGeneration = "GENERATION_002"
                        ),
                        onSyncNowClick = {},
                        onRetryFailedClick = {},
                        onResetLocalAndCloudConfirm = {},
                        onResetLocalOnlyConfirm = {},
                        consistencyAuditReport = report,
                        syncTechnicalLogs = listOf(techLog),
                        onRunConsistencyAuditClick = { auditClicked = true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("text_dataset_generation").assertExists()
        composeRule.onNodeWithTag("card_consistency_audit_report").assertExists()
        composeRule.onNodeWithTag("btn_audit_consistency").performScrollTo().performClick()
        assertTrue(auditClicked)

        composeRule.onNodeWithTag("btn_toggle_technical_logs").performScrollTo().performClick()
        composeRule.onNodeWithTag("panel_technical_sync_logs").assertExists()
        composeRule.onNodeWithText("RESET_LOCAL_AND_CLOUD [GENERATION_002]").assertExists()
    }

    @Test
    fun bagian13_resetLocalAndCloudSweepsStrayAppIdFilesWhilePreservingZipsAndPersonalFiles() = runBlocking {
        val rootFolderRes = fakeBackend.seedExternalObject(
            id = "root_app_folder_sweep",
            name = GoogleDriveRestClient.BACKUP_FOLDER_NAME,
            mimeType = GoogleDriveRestClient.FOLDER_MIME,
            parentId = "root"
        )
        val histFolder = fakeBackend.seedExternalObject(
            id = "hist_folder_sweep",
            name = GoogleDriveMirrorSyncEngine.SUBFOLDER_HISTORICAL_BACKUP,
            mimeType = GoogleDriveRestClient.FOLDER_MIME,
            parentId = rootFolderRes.id
        )
        val zipBackup = fakeBackend.seedExternalObject(
            id = "zip_keep_sweep",
            name = "SEWAIN_BACKUP_SAFE.zip",
            mimeType = "application/zip",
            parentId = histFolder.id,
            payload = "zip_data",
            appId = com.example.drive.MirrorOutboxCodec.APP_ID_VALUE
        )
        val personalFile = fakeBackend.seedExternalObject(
            id = "personal_keep_sweep",
            name = "personal_notes.txt",
            mimeType = "text/plain",
            parentId = "root",
            payload = "personal"
        )
        val strayAppFile = fakeBackend.seedExternalObject(
            id = "stray_app_uuid_json",
            name = "11111111-2222-4333-8444-555555555555.json",
            mimeType = "application/json",
            parentId = "root",
            payload = """{"uuid":"11111111-2222-4333-8444-555555555555"}""",
            appId = com.example.drive.MirrorOutboxCodec.APP_ID_VALUE
        )

        repository.saveUnit(makeUnit("SAF-SWP-1", "Freezer Sweep", "SN-SWP-1"))
        mirrorEngine.processSyncQueue().getOrThrow()

        val resetRes = mirrorEngine.executeResetLocalAndCloud().getOrThrow()
        assertEquals(0, database.freezerDao().getAllUnitsOnce().size)
        assertTrue(resetRes.deletedCloudFilesCount >= 2)
        assertFalse("Stray appId-tagged record outside ACTIVE_DATA must be swept on reset", fakeBackend.storage.containsKey(strayAppFile.id))
        assertNotNull("ZIP backup must be preserved even if appId is stamped", fakeBackend.storage[zipBackup.id])
        assertNotNull("Non-app personal file must be preserved", fakeBackend.storage[personalFile.id])
    }
}
