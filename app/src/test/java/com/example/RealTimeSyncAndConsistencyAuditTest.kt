package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.backup.RestoreMode
import com.example.backup.StructuredBackupEngine
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.DriveMirrorDashboardState
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.DriveMirrorSyncWorker
import com.example.drive.GoogleDriveAuthManager
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
import org.junit.Assert.assertNotEquals
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
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * COMPREHENSIVE AUTOMATED TEST SUITE:
 * - TEST A: CREATE DATA & PERSISTENCE (Room SQLite + Sync Queue + Drive Sync + Close/Reopen DB)
 * - TEST B: EDIT DATA (Immutable UUID & created_at, Monotonic Version, In-Place PATCH, Conflict Protection)
 * - TEST C: DELETE DATA (Local Delete + Tombstone with recordUuid + Drive Delete 404 + Zero Resurrection)
 * - TEST D: OFFLINE TO ONLINE RECOVERY + WORKMANAGER (2 Create + 1 Edit + 1 Delete -> WorkManager Sync)
 * - TEST E: FACTORY RESET TOTAL (Local & Cloud = 0, Manifest EMPTY_AFTER_RESET, Preserves Email & ZIPs, Restart Safe)
 * - TEST F: DATA CONSISTENCY AUDIT + DASHBOARD INDICATORS + MIGRATION_19_20 NON-DESTRUCTIVE TEST
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealTimeSyncAndConsistencyAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FreezerRepository
    private lateinit var mirrorEngine: GoogleDriveMirrorSyncEngine
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeBackend: AuditDriveBackend
    private val dbFileName = "audit_sync_test.db"

    class AuditDriveBackend {
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
            var datasetId: String = "",
            var datasetGeneration: String = "",
            var recordUuid: String = "",
            var remoteVersion: Long = 1L,
            var updatedAtMillis: Long = System.currentTimeMillis(),
            var trashed: Boolean = false,
            var patchCount: Int = 0
        )

        @Volatile
        var corruptReadAfterWriteForNamePrefix: String? = null

        @Volatile
        var failDeleteForIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

        private val idSeq = AtomicInteger(9000)
        val storage = ConcurrentHashMap<String, DriveObj>()
        val postCount = AtomicInteger(0)
        val patchCount = AtomicInteger(0)
        val deleteCount = AtomicInteger(0)

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

        fun activeOperationalFiles(): List<DriveObj> =
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
                    val dsIdProp = appProps?.optString("datasetId", "").orEmpty()
                    val gen = appProps?.optString("generation_id", appProps.optString("datasetGeneration", "")).orEmpty()
                    val recUuid = appProps?.optString("record_uuid", appProps.optString("recordUuid", "")).orEmpty()
                    val ver = (appProps?.optString("revision", appProps.optString("version", "1")) ?: "1").toLongOrNull() ?: 1L
                    val updMs = (appProps?.optString("sync_timestamp", appProps.optString("updatedAtMillis", "0")) ?: "0").toLongOrNull() ?: System.currentTimeMillis()

                    return if (isPatch) {
                        val fileId = path.substringAfter("/upload/drive/v3/files/").substringBefore("?").trim()
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            MockResponse().setResponseCode(404).addHeader("Date", SERVER_DATE_HEADER)
                                .setBody("""{"error":{"message":"Not found"}}""")
                        } else {
                            existing.name = name.ifBlank { existing.name }
                            existing.mimeType = mimeType.ifBlank { existing.mimeType }
                            existing.rawBody = payloadBytes
                            existing.jsonPayloadText = payloadText
                            if (checksum.isNotBlank()) existing.checksumSha256 = checksum
                            if (appIdProp.isNotBlank()) existing.appId = appIdProp
                            if (applicationIdProp.isNotBlank()) existing.applicationId = applicationIdProp
                            if (dsIdProp.isNotBlank()) existing.datasetId = dsIdProp
                            if (gen.isNotBlank()) existing.datasetGeneration = gen
                            if (recUuid.isNotBlank()) existing.recordUuid = recUuid
                            existing.remoteVersion = ver
                            existing.updatedAtMillis = updMs
                            existing.patchCount += 1
                            patchCount.incrementAndGet()
                            MockResponse().setResponseCode(200).addHeader("Date", SERVER_DATE_HEADER)
                                .setBody(toJson(existing))
                        }
                    } else {
                        val newId = "drv_audit_${idSeq.incrementAndGet()}"
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
                            datasetId = dsIdProp,
                            datasetGeneration = gen,
                            recordUuid = recUuid,
                            remoteVersion = ver,
                            updatedAtMillis = updMs
                        )
                        storage[newId] = obj
                        postCount.incrementAndGet()
                        MockResponse().setResponseCode(200).addHeader("Date", SERVER_DATE_HEADER)
                            .setBody(toJson(obj))
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
                        return MockResponse().setResponseCode(200).addHeader("Date", SERVER_DATE_HEADER)
                            .setBody(toJson(obj))
                    }

                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("GET", ignoreCase = true)) {
                        val decodedQuery = decodeQueryParam(path, "q")
                        val matches = filterByQuery(decodedQuery)
                        val arr = JSONArray()
                        matches.forEach { arr.put(JSONObject(toJson(it))) }
                        return MockResponse().setResponseCode(200).addHeader("Date", SERVER_DATE_HEADER)
                            .setBody(JSONObject().put("files", arr).toString())
                    }

                    val fileId = subPath.removePrefix("/").substringBefore("?").trim()
                    if (method.equals("DELETE", ignoreCase = true)) {
                        if (fileId in failDeleteForIds) {
                            return MockResponse().setResponseCode(500).addHeader("Date", SERVER_DATE_HEADER)
                                .setBody("""{"error":{"message":"Simulated HTTP 500 Delete Failure"}}""")
                        }
                        storage.remove(fileId)
                        deleteCount.incrementAndGet()
                        return MockResponse().setResponseCode(204).addHeader("Date", SERVER_DATE_HEADER)
                    }
                    if (method.equals("GET", ignoreCase = true)) {
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            return MockResponse().setResponseCode(404).addHeader("Date", SERVER_DATE_HEADER)
                                .setBody("""{"error":"not_found"}""")
                        }
                        return if (path.contains("alt=media")) {
                            val tamperPrefix = corruptReadAfterWriteForNamePrefix
                            val responseText = if (!tamperPrefix.isNullOrBlank() && existing.name.startsWith(tamperPrefix)) {
                                """{"tampered":true,"uuid":"corrupted"}"""
                            } else {
                                existing.jsonPayloadText
                            }
                            MockResponse().setResponseCode(200).addHeader("Date", SERVER_DATE_HEADER)
                                .setBody(responseText)
                        } else {
                            MockResponse().setResponseCode(200).addHeader("Date", SERVER_DATE_HEADER)
                                .setBody(toJson(existing))
                        }
                    }
                }
                return MockResponse().setResponseCode(404).addHeader("Date", SERVER_DATE_HEADER)
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
                put("createdTime", "2026-11-17T07:00:00.000Z")
                put("modifiedTime", "2026-11-17T07:00:00.000Z")
                put("trashed", obj.trashed)
                put("appProperties", JSONObject().apply {
                    put("checksumSha256", obj.checksumSha256)
                    put("checksum", obj.checksumSha256)
                    if (obj.appId.isNotBlank()) put("appId", obj.appId)
                    if (obj.applicationId.isNotBlank()) put("applicationId", obj.applicationId)
                    if (obj.datasetId.isNotBlank()) put("datasetId", obj.datasetId)
                    if (obj.datasetGeneration.isNotBlank()) {
                        put("datasetGeneration", obj.datasetGeneration)
                        put("generation_id", obj.datasetGeneration)
                    }
                    if (obj.recordUuid.isNotBlank()) {
                        put("recordUuid", obj.recordUuid)
                        put("record_uuid", obj.recordUuid)
                    }
                    put("version", obj.remoteVersion.toString())
                    put("revision", obj.remoteVersion.toString())
                    put("updatedAtMillis", obj.updatedAtMillis.toString())
                    put("sync_timestamp", obj.updatedAtMillis.toString())
                })
            }.toString()
        }

        companion object {
            const val SERVER_DATE_HEADER = "Tue, 17 Nov 2026 07:00:00 GMT"
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbFileName)
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.APP_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()

        mockWebServer = MockWebServer()
        fakeBackend = AuditDriveBackend()
        mockWebServer.dispatcher = fakeBackend.dispatcher
        mockWebServer.start()

        openPersistentDatabaseAndWiring()
    }

    private fun openPersistentDatabaseAndWiring() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbFileName)
            .addMigrations(
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25
            )
            .allowMainThreadQueries()
            .build()
        val apiBase = mockWebServer.url("/drive/v3").toString()
        val uploadBase = mockWebServer.url("/upload/drive/v3").toString()
        val restClient = GoogleDriveRestClient(apiBaseUrl = apiBase, uploadBaseUrl = uploadBase)
        mirrorEngine = GoogleDriveMirrorSyncEngine(
            context = context,
            dao = database.freezerDao(),
            driveClient = restClient,
            accessTokenOverride = "ya29.audit-test-token"
        ).apply {
            autoTriggerSyncOnMutation = false
            setSimulatedOfflineMode(false)
        }
        repository = FreezerRepository(database.freezerDao())
        repository.attachMirrorSyncEngine(mirrorEngine)
    }

    @After
    fun tearDown() {
        mirrorEngine.autoTriggerSyncOnMutation = false
        DriveMirrorSyncWorker.testEngineFactory = null
        runCatching { database.close() }
        context.deleteDatabase(dbFileName)
        runCatching { mockWebServer.shutdown() }
    }

    private fun sampleUnit(
        code: String = "SAF-TST-001",
        name: String = "Freezer Box 200L",
        serial: String = "SN-200-001"
    ) = FreezerUnitEntity(
        kodeUnit = code,
        namaUnit = name,
        jenisUnit = "Chest Freezer",
        owner = "SEWAin Aja",
        hargaPembelian = 2_800_000L,
        kapasitasLiter = 200,
        cicilanPerBulan = 250_000L,
        merek = "RSA",
        model = "CF-210",
        nomorSeri = serial,
        statusUnit = "Tersedia",
        lokasiUnit = "Gudang Solo",
        tanggalPembelian = "2026-01-10"
    )

    private fun sampleCustomer(
        name: String = "Budi Santoso",
        nik: String = "3372011122330001"
    ) = CustomerEntity(
        namaLengkap = name,
        nik = nik,
        nomorHp = "081234567890",
        alamatLengkap = "Jl. Slamet Riyadi No. 10, Solo",
        jenisKelamin = "Laki-laki",
        tanggalRegistrasi = "2026-02-01"
    )

    @Test
    fun testA_createUnit_persistsInSqliteQueuedSyncedAndSurvivesReopen() {
        runBlocking {
        val savedId = repository.saveUnit(sampleUnit())
        val localUnit = repository.getUnitById(savedId)!!

        assertTrue(localUnit.uuid.isNotBlank())
        assertEquals(1L, localUnit.version)
        assertTrue(localUnit.createdAtMillis > 0L)
        assertTrue(localUnit.updatedAtMillis >= localUnit.createdAtMillis)

        val pendingQueue = repository.getRunnableDriveSyncQueueItemsOnce()
        assertEquals(1, pendingQueue.size)
        assertEquals(localUnit.uuid, pendingQueue.first().recordUuid)
        assertEquals(GoogleDriveMirrorSyncEngine.OP_CREATE, pendingQueue.first().operationType)

        val syncResult = mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(1, syncResult.syncedCount)
        assertEquals(0, repository.getRunnableDriveSyncQueueItemsOnce().size)
        assertEquals(1, fakeBackend.activeUnitFiles().size)

        val cloudFile = fakeBackend.activeUnitFiles().first()
        assertEquals(localUnit.uuid, cloudFile.recordUuid)
        assertEquals(1L, cloudFile.remoteVersion)
        val cloudJson = JSONObject(cloudFile.jsonPayloadText)
        assertEquals(localUnit.uuid, cloudJson.getString("uuid"))
        assertEquals(1L, cloudJson.getLong("version"))
        assertTrue(cloudJson.getLong("server_sync_time_ms") > 0L)
        assertTrue(mirrorEngine.driveClient.lastServerTimeMillis > 0L)

        // Close and reopen SQLite DB to verify persistent storage
        database.close()
        openPersistentDatabaseAndWiring()
        val reopenedUnit = repository.getUnitById(savedId)!!
        assertEquals(localUnit.uuid, reopenedUnit.uuid)
        assertEquals(1L, reopenedUnit.version)
        assertEquals("Freezer Box 200L", reopenedUnit.namaUnit)
        }
    }

    @Test
    fun testB_editUnit_preservesUuidIncrementsVersionPatchesInPlaceAndRejectsStaleConflict() {
        runBlocking {
        val id = repository.saveUnit(sampleUnit())
        mirrorEngine.processSyncQueue().getOrThrow()

        val v1 = repository.getUnitById(id)!!
        val originalDriveId = fakeBackend.activeUnitFiles().single().id

        // Edit unit -> UUID and createdAtMillis MUST NOT change, version must become 2
        val editedInput = v1.copy(
            namaUnit = "Freezer Box 200L Upgraded",
            lokasiUnit = "Outlet Sukoharjo",
            statusUnit = "Disewa"
        )
        repository.saveUnit(editedInput)
        val v2 = repository.getUnitById(id)!!
        assertEquals(v1.uuid, v2.uuid)
        assertEquals(v1.createdAtMillis, v2.createdAtMillis)
        assertEquals(2L, v2.version)
        assertTrue(v2.updatedAtMillis >= v1.updatedAtMillis)

        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(1, fakeBackend.activeUnitFiles().size)
        assertTrue(fakeBackend.patchCount.get() >= 1)
        val patchedCloud = fakeBackend.activeUnitFiles().single()
        assertEquals(originalDriveId, patchedCloud.id)
        assertEquals(2L, patchedCloud.remoteVersion)
        assertEquals("Freezer Box 200L Upgraded", JSONObject(patchedCloud.jsonPayloadText).getString("namaUnit"))

        // Simulate Cloud having newer version v5 while local attempts edit v3 -> Conflict Protection!
        patchedCloud.remoteVersion = 5L
        repository.saveUnit(v2.copy(namaUnit = "Stale Edit Attempt"))
        val conflictBatch = mirrorEngine.processSyncQueue().getOrThrow()
        assertTrue(conflictBatch.failedCount >= 1)
        assertEquals(5L, fakeBackend.storage[originalDriveId]!!.remoteVersion)
        assertEquals("Freezer Box 200L Upgraded", JSONObject(fakeBackend.storage[originalDriveId]!!.jsonPayloadText).getString("namaUnit"))
        assertEquals(GoogleDriveMirrorSyncEngine.STATUS_CONFLICT, mirrorEngine.dashboardState.value.overallStatus)
        assertEquals(1, mirrorEngine.dashboardState.value.conflictDataCount)
        }
    }

    @Test
    fun testC_deleteUnit_removesFromCloudWritesTombstoneAndPreventsBackupResurrection() {
        runBlocking {
        val id1 = repository.saveUnit(sampleUnit("SAF-DEL-001", "Unit Akan Dihapus", "SN-DEL-1"))
        val id2 = repository.saveUnit(sampleUnit("SAF-KEEP-002", "Unit Tetap Ada", "SN-KEEP-2"))
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(2, fakeBackend.activeUnitFiles().size)

        // Create a ZIP backup BEFORE deleting Unit 1 to test anti-resurrection later
        val (_, preDeleteBackup) = StructuredBackupEngine.createStructuredBackupZip(
            context = context,
            repository = repository,
            encryptSensitivePayload = false,
            saveToHistory = false
        ).getOrThrow()

        val unit1 = repository.getUnitById(id1)!!
        val unit2 = repository.getUnitById(id2)!!
        repository.deleteUnit(unit1)

        assertNull(repository.getUnitById(id1))
        val softDeletedUnit1 = database.freezerDao().getUnitByIdIncludingDeleted(id1)
        assertNotNull("Soft-deleted row must remain in SQLite until Drive deletion succeeds", softDeletedUnit1)
        assertTrue(softDeletedUnit1!!.isDeleted)
        assertEquals(GoogleDriveMirrorSyncEngine.STATUS_PENDING_DELETE, softDeletedUnit1.syncStatus)

        val tombstone = repository.getAllDriveDeletionTombstonesOnce().firstOrNull { it.localRecordKey == "UNIT:$id1" }
        assertNotNull(tombstone)
        assertEquals(unit1.uuid, tombstone!!.recordUuid)

        mirrorEngine.processSyncQueue().getOrThrow()
        assertNull("Hard delete must run after Drive confirms deletion", database.freezerDao().getUnitByIdIncludingDeleted(id1))
        assertEquals(1, fakeBackend.activeUnitFiles().size)
        assertEquals("${unit2.uuid}.json", fakeBackend.activeUnitFiles().single().name)
        assertFalse(
            mirrorEngine.canApplyRemoteRecordWithoutResurrection(
                localRecordKey = "UNIT:$id1",
                remoteDatasetId = mirrorEngine.getCurrentDatasetId(),
                remoteTimestampMillis = unit1.updatedAtMillis,
                remoteVersion = unit1.version,
                recordUuid = unit1.uuid
            )
        )

        // Attempt APPEND_MERGE restore from pre-delete backup -> deleted Unit 1 MUST NOT resurrect!
        val restoreResult = StructuredBackupEngine.executeSafeRestore(
            context = context,
            repository = repository,
            sourceFileOrUri = preDeleteBackup,
            mode = RestoreMode.APPEND_MERGE
        )
        assertTrue(restoreResult.success)
        val remainingUnits = repository.getAllUnitsOnce()
        assertEquals(1, remainingUnits.size)
        assertEquals("SAF-KEEP-002", remainingUnits.single().kodeUnit)
        }
    }

    @Test
    fun testD_offlineQueueAccumulatesAndWorkManagerDrainsUponReconnect() {
        runBlocking {
        // Pre-seed 2 units online first so we can edit & delete offline
        val editId = repository.saveUnit(sampleUnit("SAF-OFF-EDIT", "Offline Edit Target", "SN-OFF-E"))
        val delId = repository.saveUnit(sampleUnit("SAF-OFF-DEL", "Offline Delete Target", "SN-OFF-D"))
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(2, fakeBackend.activeUnitFiles().size)

        // Go OFFLINE
        mirrorEngine.setSimulatedOfflineMode(true)
        val createId1 = repository.saveUnit(sampleUnit("SAF-OFF-C1", "Offline Create 1", "SN-OFF-C1"))
        val createId2 = repository.saveUnit(sampleUnit("SAF-OFF-C2", "Offline Create 2", "SN-OFF-C2"))
        val toEdit = repository.getUnitById(editId)!!
        repository.saveUnit(toEdit.copy(namaUnit = "Offline Edited Successfully"))
        val toDelete = repository.getUnitById(delId)!!
        repository.deleteUnit(toDelete)

        // While offline, local DB reflects final state (3 units), Cloud still untouched (2 units), queue pending
        assertEquals(3, repository.getAllUnitsOnce().size)
        assertTrue(mirrorEngine.dashboardState.value.pendingSyncCount >= 4)

        // Reconnect online and execute DriveMirrorSyncWorker via WorkManager test harness
        mirrorEngine.setSimulatedOfflineMode(false)
        DriveMirrorSyncWorker.testEngineFactory = { mirrorEngine }
        val worker = TestListenableWorkerBuilder<DriveMirrorSyncWorker>(context).build()
        val workerResult = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), workerResult)

        // All offline creates, edit, and delete synced cleanly
        assertEquals(0, repository.getRunnableDriveSyncQueueItemsOnce().size)
        assertEquals(0, mirrorEngine.dashboardState.value.pendingSyncCount)
        assertEquals(0, mirrorEngine.dashboardState.value.failedSyncCount)
        assertEquals(3, fakeBackend.activeUnitFiles().size)
        assertNotNull(repository.getUnitById(createId1))
        assertNotNull(repository.getUnitById(createId2))
        assertNull(fakeBackend.activeUnitFiles().firstOrNull { it.name == "${toDelete.uuid}.json" })
        val cloudEdited = fakeBackend.activeUnitFiles().first { it.name == "${toEdit.uuid}.json" }
        assertEquals("Offline Edited Successfully", JSONObject(cloudEdited.jsonPayloadText).getString("namaUnit"))
        }
    }

    @Test
    fun testE_factoryResetLocalAndCloud_clearsEverythingPreservesEmailAndBackupsAndSurvivesRestart() {
        runBlocking {
        GoogleDriveAuthManager.markConnectedMetadata(context, "owner.sewainaja@gmail.com", "Owner SAF", "")
        // Seed non-app personal file & historical backup archive on Drive
        val personalFile = AuditDriveBackend.DriveObj(
            id = "drv_personal_keep",
            name = "personal_photo.jpg",
            mimeType = "image/jpeg",
            parentId = "root",
            rawBody = byteArrayOf(1, 2, 3),
            jsonPayloadText = "",
            checksumSha256 = ""
        )
        val zipBackup = AuditDriveBackend.DriveObj(
            id = "drv_zip_keep",
            name = "backup_2026_11.zip",
            mimeType = "application/zip",
            parentId = "root",
            rawBody = byteArrayOf(5, 6, 7),
            jsonPayloadText = "",
            checksumSha256 = ""
        )
        fakeBackend.storage[personalFile.id] = personalFile
        fakeBackend.storage[zipBackup.id] = zipBackup

        val custId = repository.saveCustomer(sampleCustomer())
        val unitId = repository.saveUnit(sampleUnit())
        repository.saveTransaction(
            RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/9001",
                tanggalPemesanan = "2026-11-01",
                customerId = custId,
                namaPelangganSnapshot = "Budi Santoso",
                unitId = unitId,
                namaUnitSnapshot = "Freezer Box 200L",
                kodeUnitSnapshot = "SAF-TST-001",
                hargaSewa = 350_000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-01",
                tanggalAkhirSewa = "2026-12-01",
                tanggalJatuhTempo = "2026-12-01",
                nominalPembayaran = 350_000L,
                totalTagihan = 350_000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA"
            )
        )
        mirrorEngine.processSyncQueue().getOrThrow()
        assertTrue(fakeBackend.activeOperationalFiles().isNotEmpty())

        val resetRes = mirrorEngine.executeResetLocalAndCloud().getOrThrow()
        assertEquals("LOCAL_AND_CLOUD", resetRes.resetMode)
        assertEquals(0, repository.getAllCustomersOnce().size)
        assertEquals(0, repository.getAllUnitsOnce().size)
        assertEquals(0, repository.getAllTransactionsOnce().size)
        assertEquals(0, repository.getAllDriveLocalSyncQueueOnce().size)
        assertEquals(0, fakeBackend.activeOperationalFiles().size)

        // Personal & historical ZIP preserved; Google Drive email config preserved
        assertTrue(fakeBackend.storage.containsKey("drv_personal_keep"))
        assertTrue(fakeBackend.storage.containsKey("drv_zip_keep"))
        assertEquals("owner.sewainaja@gmail.com", GoogleDriveAuthManager.getConnectionStatus(context).accountEmail)

        val manifestJson = JSONObject(fakeBackend.activeManifestFile()!!.jsonPayloadText)
        assertEquals("EMPTY_AFTER_RESET", manifestJson.getString("datasetStatus"))
        assertEquals(25, manifestJson.getInt("databaseVersion"))

        // Simulate app restart
        database.close()
        openPersistentDatabaseAndWiring()
        val afterRestart = mirrorEngine.verifyConsistencyOnAppStartup().getOrThrow()
        assertEquals(0, afterRestart.totalLocalDataCount)
        assertEquals(0, afterRestart.totalCloudDataCount)
        assertEquals(GoogleDriveMirrorSyncEngine.STATUS_SYNCED, afterRestart.overallStatus)
        }
    }

    @Test
    fun testF_consistencyAuditDashboardIndicatorsAndMigration19To20() {
        runBlocking {
        // 1. Consistency Audit & Dashboard State
        repository.saveCustomer(sampleCustomer("Pelanggan Audit", "3372010000000099"))
        repository.saveUnit(sampleUnit("SAF-AUD-01", "Unit Audit", "SN-AUD-01"))
        mirrorEngine.processSyncQueue().getOrThrow()

        val audit = mirrorEngine.runDataConsistencyAudit().getOrThrow()
        assertTrue(audit.isConsistent)
        assertTrue(audit.uuidMismatches.isEmpty())
        assertTrue(audit.versionMismatches.isEmpty())
        assertTrue(audit.conflictRecords.isEmpty())
        assertTrue(audit.tombstoneViolations.isEmpty())
        assertTrue(audit.serverTimeMillis > 0L)

        val dashState = mirrorEngine.dashboardState.value
        assertEquals(2, dashState.totalLocalDataCount)
        assertEquals(2, dashState.totalCloudDataCount)
        assertEquals(2, dashState.totalSyncedDataCount)
        assertEquals(0, dashState.pendingSyncCount)
        assertEquals(0, dashState.staleDataCount)
        assertEquals(0, dashState.conflictDataCount)
        assertTrue(dashState.lastSyncTimestampMillis > 0L)

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DriveMirrorSyncDashboardCard(
                        state = dashState,
                        onSyncNowClick = {},
                        onRetryFailedClick = {},
                        onResetLocalAndCloudConfirm = {},
                        onResetLocalOnlyConfirm = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag("metric_total_local_data").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_total_cloud_data").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_stale_data_count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_conflict_data_count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Total Data Lokal").assertIsDisplayed()
        composeRule.onNodeWithText("Total Data Cloud").assertIsDisplayed()
        composeRule.onNodeWithText("Data Stale").assertIsDisplayed()
        composeRule.onNodeWithText("Data Conflict").assertIsDisplayed()

        // 2. Non-Destructive MIGRATION_19_20 Verification
        val migDbName = "test_migration_19_20.db"
        context.deleteDatabase(migDbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `freezer_units` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kodeUnit` TEXT NOT NULL, `namaUnit` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `namaLengkap` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `rental_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nomorInvoice` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `payment_proofs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `drive_cloud_file_map` (`local_record_key` TEXT PRIMARY KEY NOT NULL, `record_category` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `drive_local_sync_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `operation_id` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `drive_deletion_tombstones` (`local_record_key` TEXT PRIMARY KEY NOT NULL, `record_category` TEXT NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val v19Db = helper.writableDatabase
        v19Db.execSQL("INSERT INTO `freezer_units` (`kodeUnit`, `namaUnit`) VALUES ('SAF-MIG-01', 'Freezer Legacy 1')")
        v19Db.execSQL("INSERT INTO `freezer_units` (`kodeUnit`, `namaUnit`) VALUES ('SAF-MIG-02', 'Freezer Legacy 2')")
        v19Db.execSQL("INSERT INTO `customers` (`namaLengkap`) VALUES ('Pelanggan Legacy')")
        v19Db.execSQL("INSERT INTO `rental_transactions` (`nomorInvoice`) VALUES ('INV/MIG/001')")
        v19Db.execSQL("INSERT INTO `payment_proofs` (`transactionId`) VALUES (1)")

        AppDatabase.MIGRATION_19_20.migrate(v19Db)

        val unitUuids = mutableSetOf<String>()
        v19Db.query("SELECT `kodeUnit`, `namaUnit`, `uuid`, `version`, `created_at`, `updated_at` FROM `freezer_units` ORDER BY id ASC").use { c ->
            assertEquals(2, c.count)
            while (c.moveToNext()) {
                val uuid = c.getString(2)
                assertTrue(uuid.isNotBlank())
                assertTrue(unitUuids.add(uuid))
                assertEquals(1L, c.getLong(3))
                assertTrue(c.getLong(4) > 0L)
                assertEquals(c.getLong(4), c.getLong(5))
            }
        }
        v19Db.query("SELECT `namaLengkap`, `uuid`, `version` FROM `customers`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Pelanggan Legacy", c.getString(0))
            assertTrue(c.getString(1).isNotBlank())
            assertEquals(1L, c.getLong(2))
        }
        v19Db.close()
        helper.close()
        context.deleteDatabase(migDbName)
        }
    }

    @Test
    fun testG_migration20To21_nonDestructiveUpgradePreservesRowsAndAddsOutboxQuarantineColumns() {
        val migDbName = "test_migration_20_21.db"
        context.deleteDatabase(migDbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `freezer_units` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kodeUnit` TEXT NOT NULL, `namaUnit` TEXT NOT NULL, `uuid` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `namaLengkap` TEXT NOT NULL, `uuid` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `rental_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nomorInvoice` TEXT NOT NULL, `uuid` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `payment_proofs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `uuid` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `drive_local_sync_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `operation_id` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `drive_deletion_tombstones` (`local_record_key` TEXT PRIMARY KEY NOT NULL, `record_category` TEXT NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val v20Db = helper.writableDatabase
        v20Db.execSQL("INSERT INTO `freezer_units` (`kodeUnit`, `namaUnit`, `uuid`, `version`, `created_at`, `updated_at`) VALUES ('SAF-20', 'Freezer v20', 'u-v20-uuid', 3, 1000, 2000)")
        v20Db.execSQL("INSERT INTO `customers` (`namaLengkap`, `uuid`, `version`, `created_at`, `updated_at`) VALUES ('Pelanggan v20', 'c-v20-uuid', 2, 1000, 1500)")
        v20Db.execSQL("INSERT INTO `drive_deletion_tombstones` (`local_record_key`, `record_category`) VALUES ('UNIT:99', 'UNIT')")

        AppDatabase.MIGRATION_20_21.migrate(v20Db)

        v20Db.query("SELECT `kodeUnit`, `uuid`, `sync_status`, `changed_fields_json` FROM `freezer_units`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("SAF-20", c.getString(0))
            assertEquals("u-v20-uuid", c.getString(1))
            assertEquals("PENDING", c.getString(2))
            assertEquals("", c.getString(3))
        }
        v20Db.query("SELECT `uuid_verified_gone`, `manifest_updated_at_deletion` FROM `drive_deletion_tombstones` WHERE `local_record_key` = 'UNIT:99'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
        }
        v20Db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='drive_cloud_quarantine'").use { c ->
            assertTrue(c.moveToFirst())
        }
        v20Db.close()
        helper.close()
        context.deleteDatabase(migDbName)
    }

    @Test
    fun testH_transactionalOutboxAll12CrudOps_atomicWithoutEngineAndTracksChangedFields() = runBlocking {
        // Verify atomic @Transaction outbox works even when mirrorSyncEngine is detached
        repository.attachMirrorSyncEngine(null)

        // 1-4 CREATE: Pelanggan, Unit, Transaksi, Pembayaran
        val custId = repository.saveCustomer(sampleCustomer("Pelanggan Outbox", "3372018899001122"))
        val unitId = repository.saveUnit(sampleUnit("SAF-OBX-1", "Unit Outbox 1", "SN-OBX-1"))
        val txId = repository.saveTransaction(
            RentalTransactionEntity(
                nomorInvoice = "INV/OBX/2026/001",
                tanggalPemesanan = "2026-11-10",
                customerId = custId,
                namaPelangganSnapshot = "Pelanggan Outbox",
                unitId = unitId,
                namaUnitSnapshot = "Unit Outbox 1",
                kodeUnitSnapshot = "SAF-OBX-1",
                hargaSewa = 300_000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-10",
                tanggalAkhirSewa = "2026-12-10",
                tanggalJatuhTempo = "2026-12-10",
                nominalPembayaran = 150_000L,
                totalTagihan = 300_000L,
                statusPembayaran = "Belum Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Tunai"
            )
        )
        val payId = repository.savePaymentProof(
            PaymentProofEntity(
                transactionId = txId,
                tanggalPembayaran = "2026-11-10",
                nominalPembayaran = 150_000L,
                metodePembayaran = "Tunai",
                catatanPembayaran = "DP Awal"
            )
        )

        val preSyncQueue = repository.getAllDriveLocalSyncQueueOnce()
        assertTrue(preSyncQueue.any { it.localRecordKey == "CUSTOMER:$custId" && it.operationType == "CREATE" })
        assertTrue(preSyncQueue.any { it.localRecordKey == "UNIT:$unitId" && it.operationType == "CREATE" })
        assertTrue(preSyncQueue.any { it.localRecordKey == "TRANSACTION:$txId" && it.operationType == "CREATE" })
        assertTrue(preSyncQueue.any { it.localRecordKey == "PAYMENT:$payId" && it.operationType == "CREATE" })
        assertEquals("PENDING_CREATE", repository.getCustomerById(custId)!!.syncStatus)
        assertEquals("PENDING_CREATE", repository.getUnitById(unitId)!!.syncStatus)

        // Attach engine & sync CREATEs
        repository.attachMirrorSyncEngine(mirrorEngine)
        mirrorEngine.processSyncQueue().getOrThrow()

        assertEquals("SYNCED", repository.getCustomerById(custId)!!.syncStatus)
        assertEquals("SYNCED", repository.getUnitById(unitId)!!.syncStatus)
        assertEquals("SYNCED", repository.getTransactionById(txId)!!.syncStatus)
        assertEquals("SYNCED", database.freezerDao().getPaymentProofById(payId)!!.syncStatus)

        // Verify ACTIVE_DATA/manifest.json has full activeRecords list
        val manifestObj = JSONObject(fakeBackend.activeManifestFile()!!.jsonPayloadText)
        val activeRecordsArr = manifestObj.getJSONArray("activeRecords")
        assertEquals(4, activeRecordsArr.length())

        // 5-8 EDIT: Pelanggan, Unit, Transaksi, Pembayaran (verify changedFieldsJson & version increment)
        val c1 = repository.getCustomerById(custId)!!
        repository.saveCustomer(c1.copy(nomorHp = "089988776655", catatan = "Prioritas"))
        val c2 = repository.getCustomerById(custId)!!
        assertEquals("PENDING_UPDATE", c2.syncStatus)
        assertTrue(c2.changedFieldsJson.contains("nomorHp"))
        assertTrue(c2.changedFieldsJson.contains("catatan"))

        val u1 = repository.getUnitById(unitId)!!
        repository.saveUnit(u1.copy(lokasiUnit = "Cabang Boyolali"))
        assertTrue(repository.getUnitById(unitId)!!.changedFieldsJson.contains("lokasiUnit"))

        val tx1 = repository.getTransactionById(txId)!!
        repository.saveTransaction(tx1.copy(nominalPembayaran = 300_000L, statusPembayaran = "Lunas"))
        assertTrue(repository.getTransactionById(txId)!!.changedFieldsJson.contains("statusPembayaran"))

        val p1 = database.freezerDao().getPaymentProofById(payId)!!
        repository.savePaymentProof(p1.copy(nominalPembayaran = 200_000L, catatanPembayaran = "Revisi Nominal"))
        val p2 = database.freezerDao().getPaymentProofById(payId)!!
        assertEquals(2L, p2.version)
        assertTrue(p2.changedFieldsJson.contains("nominalPembayaran"))

        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals("SYNCED", repository.getCustomerById(custId)!!.syncStatus)
        assertEquals("SYNCED", repository.getUnitById(unitId)!!.syncStatus)
        assertEquals("SYNCED", repository.getTransactionById(txId)!!.syncStatus)
        assertEquals("SYNCED", database.freezerDao().getPaymentProofById(payId)!!.syncStatus)

        // 9-12 DELETE: Pembayaran, Transaksi, Unit, Pelanggan -> 7-step Delete Pipeline
        val custUuid = c2.uuid
        val unitUuid = repository.getUnitById(unitId)!!.uuid
        val txUuid = repository.getTransactionById(txId)!!.uuid
        val payUuid = p2.uuid

        repository.deletePaymentProof(p2)
        repository.deleteTransaction(repository.getTransactionById(txId)!!)
        repository.deleteUnit(repository.getUnitById(unitId)!!)
        repository.deleteCustomer(repository.getCustomerById(custId)!!)

        mirrorEngine.processSyncQueue().getOrThrow()

        val remainingRecordFiles = fakeBackend.activeOperationalFiles().filter {
            it.name in listOf("${custUuid}.json", "${unitUuid}.json", "${txUuid}.json", "${payUuid}.json") ||
                it.name.startsWith("CUSTOMER_") || it.name.startsWith("UNIT_") ||
                it.name.startsWith("TRANSACTION_") || it.name.startsWith("PAYMENT_")
        }
        assertEquals(0, remainingRecordFiles.size)
        listOf("customers.json", "units.json", "transactions.json", "payments.json").forEach { snapName ->
            val snapObj = fakeBackend.activeOperationalFiles().firstOrNull { it.name == snapName }
            if (snapObj != null) {
                assertEquals(0, JSONArray(snapObj.jsonPayloadText).length())
            }
        }
        val tombstones = repository.getAllDriveDeletionTombstonesOnce()
        listOf(payUuid, txUuid, unitUuid, custUuid).forEach { expectedUuid ->
            val t = tombstones.firstOrNull { it.recordUuid == expectedUuid }
            assertNotNull("Tombstone missing for $expectedUuid", t)
            assertTrue("syncedToCloud false for $expectedUuid", t!!.syncedToCloud)
            assertTrue("uuidVerifiedGone false for $expectedUuid", t.uuidVerifiedGone)
            assertTrue("manifestUpdatedAtDeletion false for $expectedUuid", t.manifestUpdatedAtDeletion)
        }
        val finalManifest = JSONObject(fakeBackend.activeManifestFile()!!.jsonPayloadText)
        assertEquals(0, finalManifest.getJSONArray("activeRecords").length())
        assertEquals(4, finalManifest.getJSONArray("tombstones").length())
    }

    @Test
    fun testI_readAfterWriteRejectsTamper_andStaleCloudQuarantineNeverAutoImportsOrDeletesUncertainFiles() = runBlocking {
        // 1. Read-after-write verification rejects mismatched JSON content
        val badUnitId = repository.saveUnit(sampleUnit("SAF-RAW-1", "Tamper Unit", "SN-RAW-1"))
        val badUnitUuid = repository.getUnitById(badUnitId)!!.uuid
        fakeBackend.corruptReadAfterWriteForNamePrefix = "${badUnitUuid}.json"
        val failBatch = mirrorEngine.processSyncQueue().getOrThrow()
        assertTrue(failBatch.failedCount >= 1)
        assertEquals(GoogleDriveMirrorSyncEngine.STATUS_FAILED, repository.getUnitById(badUnitId)!!.syncStatus)

        // Restore honest Drive response and sync
        fakeBackend.corruptReadAfterWriteForNamePrefix = null
        val okBatch = mirrorEngine.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true).getOrThrow()
        assertEquals(1, okBatch.syncedCount)
        assertEquals(GoogleDriveMirrorSyncEngine.STATUS_SYNCED, repository.getUnitById(badUnitId)!!.syncStatus)

        // 2. Seed stale cloud files: (a) owned old customer file from previous gen, (b) uncertain foreign file without appProperties
        val activeDataId = fakeBackend.storage.values.first {
            !it.trashed && it.mimeType == GoogleDriveRestClient.FOLDER_MIME && it.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_ACTIVE_DATA
        }.id
        val customersFolderId = fakeBackend.storage.values.firstOrNull {
            !it.trashed && it.mimeType == GoogleDriveRestClient.FOLDER_MIME &&
                it.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_CUSTOMERS &&
                it.parentId == activeDataId
        }?.id ?: "drv_fld_customers_seed".also { fldId ->
            fakeBackend.storage[fldId] = AuditDriveBackend.DriveObj(
                id = fldId,
                name = GoogleDriveMirrorSyncEngine.SUBFOLDER_CUSTOMERS,
                mimeType = GoogleDriveRestClient.FOLDER_MIME,
                parentId = activeDataId,
                rawBody = ByteArray(0),
                jsonPayloadText = "",
                checksumSha256 = ""
            )
        }

        fakeBackend.storage["drv_stale_owned"] = AuditDriveBackend.DriveObj(
            id = "drv_stale_owned",
            name = "CUSTOMER_888.json",
            mimeType = "application/json",
            parentId = customersFolderId,
            rawBody = """{"id":888,"namaLengkap":"Old App Customer"}""".toByteArray(),
            jsonPayloadText = """{"id":888,"namaLengkap":"Old App Customer"}""",
            checksumSha256 = "",
            applicationId = GoogleDriveMirrorSyncEngine.APPLICATION_ID,
            datasetGeneration = "GENERATION_OLD_000",
            recordUuid = "old-uuid-888"
        )
        fakeBackend.storage["drv_uncertain_foreign"] = AuditDriveBackend.DriveObj(
            id = "drv_uncertain_foreign",
            name = "CUSTOMER_999.json",
            mimeType = "application/json",
            parentId = customersFolderId,
            rawBody = """{"id":999,"namaLengkap":"Foreign Customer"}""".toByteArray(),
            jsonPayloadText = """{"id":999,"namaLengkap":"Foreign Customer"}""",
            checksumSha256 = ""
        )

        val quarantineList = mirrorEngine.scanAndQuarantineStaleCloudFiles().getOrThrow()
        // Never auto-imports into SQLite
        assertEquals(0, repository.getAllCustomersOnce().size)
        assertEquals(2, quarantineList.size)

        val ownedItem = quarantineList.first { it.driveFileId == "drv_stale_owned" }
        val uncertainItem = quarantineList.first { it.driveFileId == "drv_uncertain_foreign" }
        assertTrue(ownedItem.ownershipVerified)
        assertEquals("PREVIOUS_GENERATION", ownedItem.classification)
        assertFalse(uncertainItem.ownershipVerified)
        assertEquals("OLD_CUSTOMER_FILE", uncertainItem.classification)

        // Purge verified quarantine -> deletes ONLY drv_stale_owned, keeps drv_uncertain_foreign
        val purgedCount = mirrorEngine.purgeVerifiedQuarantinedFiles().getOrThrow()
        assertEquals(1, purgedCount)
        assertFalse(fakeBackend.storage.containsKey("drv_stale_owned"))
        assertTrue(fakeBackend.storage.containsKey("drv_uncertain_foreign"))
        assertEquals(1, repository.getAllUnitsOnce().size)
    }

    @Test
    fun testJ_migration21To22_nonDestructiveAddsSoftDeleteDriveIdAndBackfillsFromMap() {
        val migDbName = "test_migration_21_22.db"
        context.deleteDatabase(migDbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `freezer_units` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kodeUnit` TEXT NOT NULL, `namaUnit` TEXT NOT NULL, `uuid` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `changed_fields_json` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `namaLengkap` TEXT NOT NULL, `uuid` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `changed_fields_json` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `rental_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nomorInvoice` TEXT NOT NULL, `uuid` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `changed_fields_json` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `payment_proofs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `uuid` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `changed_fields_json` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `drive_cloud_file_map` (`local_record_key` TEXT PRIMARY KEY NOT NULL, `drive_file_id` TEXT NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val v21Db = helper.writableDatabase
        v21Db.execSQL("INSERT INTO `freezer_units` (`id`, `kodeUnit`, `namaUnit`, `uuid`, `version`, `created_at`, `updated_at`, `sync_status`, `changed_fields_json`) VALUES (10, 'SAF-21', 'Freezer v21', 'uuid-v21-u10', 2, 1000, 2000, 'PENDING', '')")
        v21Db.execSQL("INSERT INTO `customers` (`id`, `namaLengkap`, `uuid`, `version`, `created_at`, `updated_at`, `sync_status`, `changed_fields_json`) VALUES (20, 'Pelanggan v21', 'uuid-v21-c20', 1, 1000, 1500, 'SYNCED', '')")
        v21Db.execSQL("INSERT INTO `drive_cloud_file_map` (`local_record_key`, `drive_file_id`) VALUES ('CUSTOMER:20', 'drv_existing_c20')")

        AppDatabase.MIGRATION_21_22.migrate(v21Db)

        v21Db.query("SELECT `sync_status`, `is_deleted`, `drive_id` FROM `freezer_units` WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("PENDING_CREATE", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertEquals("", c.getString(2))
        }
        v21Db.query("SELECT `sync_status`, `is_deleted`, `drive_id` FROM `customers` WHERE id = 20").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("SYNCED", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertEquals("drv_existing_c20", c.getString(2))
        }
        v21Db.close()
        helper.close()
        context.deleteDatabase(migDbName)
    }

    @Test
    fun testK_crossGenerationActiveRecordWithAppId_neverFalseQuarantinedOrFlaggedGenMismatch() = runBlocking {
        val unitId = repository.saveUnit(sampleUnit("SAF-APPID-1", "Freezer AppId", "SN-APPID-1"))
        mirrorEngine.processSyncQueue().getOrThrow()

        val activeUnit = repository.getUnitById(unitId)!!
        val cloudFile = fakeBackend.activeUnitFiles().single()
        assertEquals(com.example.drive.MirrorOutboxCodec.APP_ID_VALUE, cloudFile.appId)
        assertEquals("${activeUnit.uuid}.json", cloudFile.name)

        // Simulate file uploaded under previous GENERATION_001 while active SQLite record uses GENERATION_002
        cloudFile.datasetGeneration = "GENERATION_001"
        cloudFile.jsonPayloadText = JSONObject(cloudFile.jsonPayloadText).put("datasetGeneration", "GENERATION_001").toString()
        val prefs = context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("dataset_generation_seq", 2).commit()

        val audit = mirrorEngine.runDataConsistencyAudit().getOrThrow()
        assertTrue("Active file with matching UUID and appId must NOT be quarantined as GEN_MISMATCH: $audit", audit.quarantinedFilesCount == 0)
        assertEquals(0, database.freezerDao().getActiveDriveCloudQuarantineOnce().size)
    }

    @Test
    fun testL_softDeleteHttp500PreservesTombstoneRowAndRetryingWith204PermanentlyDeletes() = runBlocking {
        val unitId = repository.saveUnit(sampleUnit("SAF-SDEL-1", "Freezer Soft Delete", "SN-SDEL-1"))
        mirrorEngine.processSyncQueue().getOrThrow()
        val driveFileId = fakeBackend.activeUnitFiles().single().id

        // Inject HTTP 500 on Drive DELETE
        fakeBackend.failDeleteForIds.add(driveFileId)
        val unit = repository.getUnitById(unitId)!!
        repository.deleteUnit(unit)

        // Active queries hide soft-deleted row immediately
        assertNull(repository.getUnitById(unitId))
        assertEquals(0, repository.getAllUnitsOnce().size)

        val failBatch = mirrorEngine.processSyncQueue().getOrThrow()
        assertTrue(failBatch.failedCount >= 1)

        // Soft-deleted row MUST still exist in SQLite so retry knows its Drive ID & UUID
        val stillPending = database.freezerDao().getUnitByIdIncludingDeleted(unitId)
        assertNotNull(stillPending)
        assertTrue(stillPending!!.isDeleted)
        assertEquals(GoogleDriveMirrorSyncEngine.STATUS_PENDING_DELETE, stillPending.syncStatus)
        assertEquals(driveFileId, stillPending.driveId)

        // Recover HTTP 204 and retry -> Drive file deleted + SQLite row permanently hard-deleted
        fakeBackend.failDeleteForIds.remove(driveFileId)
        val okBatch = mirrorEngine.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true).getOrThrow()
        assertEquals(1, okBatch.syncedCount)
        assertNull(database.freezerDao().getUnitByIdIncludingDeleted(unitId))
        assertEquals(0, fakeBackend.activeUnitFiles().size)
    }
}
