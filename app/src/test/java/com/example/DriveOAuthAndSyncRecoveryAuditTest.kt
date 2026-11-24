package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.DriveCloudFileMapEntity
import com.example.data.local.DriveCloudManifestState
import com.example.data.local.DriveLocalSyncQueueEntity
import com.example.data.local.DriveMirrorDashboardState
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.DatabaseGenerationManager
import com.example.drive.DriveOAuthTransport
import com.example.drive.DriveTokenResult
import com.example.drive.GoogleDriveAuthManager
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.GoogleDriveRestClient
import com.example.drive.GoogleDriveService
import com.example.drive.MirrorOutboxCodec
import com.example.drive.SyncConflictResolver
import com.example.drive.SyncConsistencyChecker
import com.example.drive.SyncQueueManager
import com.example.drive.SyncRepository
import com.example.drive.SyncWorker
import com.example.ui.components.DriveMirrorSyncDashboardCard
import com.example.ui.theme.SEWAinAjaFreezerTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Comprehensive Direct Automated Test Suite covering:
 * A. OAuth 2.0 Authentication (valid token, proactive expired refresh, HTTP 401 single retry on
 *    DELETE PHOTO_TRANSACTION_ID_9.jpg, concurrent 401 single-flight Mutex refresh, revoked auth ->
 *    FAILED_AUTH / WAITING_AUTH without false green notification or infinite retry, re-login recovery).
 * B. Full Google Drive REST v3 File Operations (UPLOAD, DOWNLOAD, READ METADATA by fileId,
 *    UPDATE appProperties by fileId, LIST files, DELETE by real fileId + 404 verification).
 * C. SQLite CRUD (Add, Edit, Delete Unit, Customer, Transaction) with UUID v4 & Drive consistency.
 * D. Recovery & Resilience (25 failed operations in GENERATION_002 with Manifest Cloud v158 without
 *    duplicates/data loss/generation rotation, offline upload/delete -> online resume, force-close
 *    stuck UPLOADING recovery, Dashboard WAITING_AUTH UI banner).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveOAuthAndSyncRecoveryAuditTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: FreezerDao
    private lateinit var repository: FreezerRepository
    private lateinit var generationManager: DatabaseGenerationManager
    private lateinit var restClient: GoogleDriveRestClient
    private lateinit var driveService: GoogleDriveService
    private lateinit var queueManager: SyncQueueManager
    private lateinit var conflictResolver: SyncConflictResolver
    private lateinit var consistencyChecker: SyncConsistencyChecker
    private lateinit var syncRepository: SyncRepository
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeCloud: OAuthEnforcingDriveServer
    private lateinit var fakeOAuth: FakeDriveOAuthTransport

    private val dbName = "drive_oauth_sync_recovery_audit.db"
    private val fakeClockMs = AtomicLong(1_760_000_000_000L)

    private fun sha256Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun makeUnit(
        kode: String,
        merek: String = "Gea",
        kapasitasLiter: Int = 300,
        fotoUnitUri: String = "",
        version: Long = 1L
    ) = FreezerUnitEntity(
        kodeUnit = kode,
        namaUnit = "Freezer $kode",
        jenisUnit = "Chest Freezer",
        owner = "SOC",
        hargaPembelian = 4_500_000L,
        kapasitasLiter = kapasitasLiter,
        cicilanPerBulan = 500_000L,
        merek = merek,
        model = "AB-300",
        nomorSeri = "SN-$kode",
        statusUnit = "Tersedia",
        lokasiUnit = "Solo",
        tanggalPembelian = "2026-01-01",
        fotoUnitUri = fotoUnitUri,
        version = version
    )

    private fun makeCustomer(
        nama: String,
        nik: String,
        hp: String,
        alamat: String = "Jl. Slamet Riyadi Solo"
    ) = CustomerEntity(
        namaLengkap = nama,
        nik = nik,
        nomorHp = hp,
        alamatLengkap = alamat,
        jenisKelamin = "Laki-laki",
        tanggalRegistrasi = "2026-01-02"
    )

    private fun makeTransaction(
        invoice: String,
        customerId: Long,
        namaPelanggan: String,
        unitId: Long,
        kodeUnit: String,
        statusPenyewaan: String = "Dalam Penyewaan",
        statusPembayaran: String = "Lunas"
    ) = RentalTransactionEntity(
        nomorInvoice = invoice,
        tanggalPemesanan = "2026-11-01",
        customerId = customerId,
        namaPelangganSnapshot = namaPelanggan,
        unitId = unitId,
        namaUnitSnapshot = "Freezer $kodeUnit",
        kodeUnitSnapshot = kodeUnit,
        hargaSewa = 350_000L,
        durasiHari = 30,
        tanggalMulaiSewa = "2026-11-01",
        tanggalAkhirSewa = "2026-12-01",
        tanggalJatuhTempo = "2026-12-01",
        nominalPembayaran = 350_000L,
        totalTagihan = 350_000L,
        statusPembayaran = statusPembayaran,
        statusPenyewaan = statusPenyewaan,
        metodePembayaran = "Transfer BCA"
    )

    private class FakeDriveOAuthTransport(
        private val clockMs: AtomicLong
    ) : DriveOAuthTransport {
        @Volatile
        var nextResultProvider: (Int) -> DriveTokenResult = { seq ->
            DriveTokenResult.Valid(
                accessToken = "ya29.fresh_oauth_token_$seq",
                expiresAtMs = clockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS
            )
        }

        val acquireCallCount = AtomicInteger(0)
        val clearedTokens = CopyOnWriteArrayList<String>()

        override suspend fun acquireAccessTokenSilently(
            context: Context,
            accountEmail: String?
        ): DriveTokenResult {
            val seq = acquireCallCount.incrementAndGet()
            return nextResultProvider(seq)
        }

        override suspend fun clearCachedPlatformToken(
            context: Context,
            staleAccessToken: String
        ) {
            clearedTokens.add(staleAccessToken)
        }
    }

    private class OAuthEnforcingDriveServer {
        data class DriveObject(
            val id: String,
            var name: String,
            var mimeType: String,
            var parentId: String,
            var binaryPayload: ByteArray,
            var textPayload: String,
            var trashed: Boolean = false,
            var description: String = "",
            val appProperties: ConcurrentHashMap<String, String> = ConcurrentHashMap()
        )

        val acceptedTokens = ConcurrentHashMap.newKeySet<String>()
        val storage = ConcurrentHashMap<String, DriveObject>()
        val observedAuthHeaders = CopyOnWriteArrayList<String>()
        val unauthorizedResponsesIssued = AtomicInteger(0)
        val uploadPostCount = AtomicInteger(0)
        val deleteRequestCount = AtomicInteger(0)
        private val idSeq = AtomicInteger(1000)

        fun seedFolder(name: String, parentId: String = "root"): String {
            val existing = storage.values.firstOrNull {
                !it.trashed && it.mimeType == GoogleDriveRestClient.FOLDER_MIME &&
                    it.name == name && it.parentId == parentId
            }
            if (existing != null) return existing.id
            val id = "fld_${idSeq.incrementAndGet()}"
            storage[id] = DriveObject(
                id = id,
                name = name,
                mimeType = GoogleDriveRestClient.FOLDER_MIME,
                parentId = parentId,
                binaryPayload = ByteArray(0),
                textPayload = ""
            )
            return id
        }

        fun seedFile(
            name: String,
            mimeType: String,
            parentId: String,
            payloadBytes: ByteArray,
            appProperties: Map<String, String> = emptyMap(),
            explicitId: String? = null
        ): DriveObject {
            val id = explicitId ?: "drv_file_${idSeq.incrementAndGet()}"
            val obj = DriveObject(
                id = id,
                name = name,
                mimeType = mimeType,
                parentId = parentId,
                binaryPayload = payloadBytes,
                textPayload = runCatching { String(payloadBytes, Charsets.UTF_8) }.getOrDefault(""),
                appProperties = ConcurrentHashMap(appProperties)
            )
            storage[id] = obj
            return obj
        }

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val authHeader = request.getHeader("Authorization").orEmpty()
                observedAuthHeaders.add(authHeader)
                val token = authHeader.removePrefix("Bearer ").trim()
                if (!authHeader.startsWith("Bearer ") || token.isBlank() || token !in acceptedTokens) {
                    unauthorizedResponsesIssued.incrementAndGet()
                    return MockResponse()
                        .setResponseCode(401)
                        .addHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "error": {
                                "code": 401,
                                "message": "Request had invalid authentication credentials. Expected OAuth 2 access token, login cookie or other valid authentication credential.",
                                "status": "UNAUTHENTICATED",
                                "errors": [{ "reason": "authError", "message": "Invalid Credentials" }]
                              }
                            }
                            """.trimIndent()
                        )
                }

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
                    val appPropsJson = metaJson.optJSONObject("appProperties")

                    return if (isPatch) {
                        val fileId = path.substringAfter("/upload/drive/v3/files/").substringBefore("?").trim()
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            MockResponse().setResponseCode(404).setBody("""{"error":{"code":404,"message":"File not found: $fileId"}}""")
                        } else {
                            if (name.isNotBlank()) existing.name = name
                            if (mimeType.isNotBlank()) existing.mimeType = mimeType
                            existing.binaryPayload = payloadBytes
                            existing.textPayload = payloadText
                            mergeAppProperties(existing.appProperties, appPropsJson)
                            MockResponse().setResponseCode(200)
                                .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                                .setBody(toJson(existing))
                        }
                    } else {
                        uploadPostCount.incrementAndGet()
                        val newId = "drv_obj_${idSeq.incrementAndGet()}"
                        val props = ConcurrentHashMap<String, String>()
                        mergeAppProperties(props, appPropsJson)
                        val obj = DriveObject(
                            id = newId,
                            name = name,
                            mimeType = mimeType,
                            parentId = parentId,
                            binaryPayload = payloadBytes,
                            textPayload = payloadText,
                            appProperties = props
                        )
                        storage[newId] = obj
                        MockResponse().setResponseCode(200)
                            .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                            .setBody(toJson(obj))
                    }
                }

                if (path.startsWith("/drive/v3/files")) {
                    val subPath = path.removePrefix("/drive/v3/files")
                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("POST", ignoreCase = true)) {
                        val json = runCatching { JSONObject(bodyText) }.getOrNull() ?: JSONObject()
                        val newId = "drv_fld_${idSeq.incrementAndGet()}"
                        val props = ConcurrentHashMap<String, String>()
                        mergeAppProperties(props, json.optJSONObject("appProperties"))
                        val obj = DriveObject(
                            id = newId,
                            name = json.optString("name", "Folder"),
                            mimeType = json.optString("mimeType", GoogleDriveRestClient.FOLDER_MIME),
                            parentId = json.optJSONArray("parents")?.optString(0, "root") ?: "root",
                            binaryPayload = ByteArray(0),
                            textPayload = "",
                            appProperties = props
                        )
                        storage[newId] = obj
                        return MockResponse().setResponseCode(200)
                            .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                            .setBody(toJson(obj))
                    }

                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("GET", ignoreCase = true)) {
                        val q = decodeQueryParam(path, "q")
                        val arr = JSONArray()
                        filterByQuery(q).forEach { arr.put(JSONObject(toJson(it))) }
                        return MockResponse().setResponseCode(200)
                            .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                            .setBody(JSONObject().put("files", arr).toString())
                    }

                    val fileId = subPath.removePrefix("/").substringBefore("?").trim()
                    if (method.equals("DELETE", ignoreCase = true)) {
                        deleteRequestCount.incrementAndGet()
                        val removed = storage.remove(fileId)
                        return if (removed == null) {
                            MockResponse().setResponseCode(404)
                                .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                                .setBody("""{"error":{"code":404,"message":"File not found: $fileId"}}""")
                        } else {
                            MockResponse().setResponseCode(204)
                                .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                        }
                    }

                    if (method.equals("PATCH", ignoreCase = true)) {
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            return MockResponse().setResponseCode(404)
                                .setBody("""{"error":{"code":404,"message":"File not found: $fileId"}}""")
                        }
                        val patchJson = runCatching { JSONObject(bodyText) }.getOrNull() ?: JSONObject()
                        patchJson.optString("name", "").takeIf { it.isNotBlank() }?.let { existing.name = it }
                        patchJson.optString("description", "").takeIf { it.isNotBlank() }?.let { existing.description = it }
                        mergeAppProperties(existing.appProperties, patchJson.optJSONObject("appProperties"))
                        return MockResponse().setResponseCode(200)
                            .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                            .setBody(toJson(existing))
                    }

                    if (method.equals("GET", ignoreCase = true)) {
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            return MockResponse().setResponseCode(404)
                                .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                                .setBody("""{"error":{"code":404,"message":"File not found: $fileId"}}""")
                        }
                        return if (path.contains("alt=media")) {
                            MockResponse().setResponseCode(200)
                                .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                                .setBody(Buffer().write(existing.binaryPayload))
                        } else {
                            MockResponse().setResponseCode(200)
                                .addHeader("Date", "Fri, 20 Nov 2026 10:00:00 GMT")
                                .setBody(toJson(existing))
                        }
                    }
                }

                return MockResponse().setResponseCode(404)
            }
        }

        private fun mergeAppProperties(target: ConcurrentHashMap<String, String>, json: JSONObject?) {
            if (json == null) return
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = json.optString(k, "")
                if (v.isNotBlank()) target[k] = v
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
            val delimiterBytes = "--$boundary".toByteArray(Charsets.ISO_8859_1)
            val delimiter = "--$boundary"

            var metadataJson = JSONObject()
            var contentText = ""
            var binaryPart = ByteArray(0)

            val rawParts = bodyText.split(delimiter).map { it.trim() }.filter { it.isNotEmpty() && it != "--" }
            for ((idx, part) in rawParts.withIndex()) {
                val splitIdx = part.indexOf("\r\n\r\n").takeIf { it >= 0 } ?: part.indexOf("\n\n")
                val bodySection = if (splitIdx >= 0) {
                    val offset = if (part.substring(splitIdx).startsWith("\r\n\r\n")) 4 else 2
                    part.substring(splitIdx + offset).trimEnd('\r', '\n')
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

            val firstBoundary = indexOfBytes(rawBytes, delimiterBytes, 0)
            val secondBoundary = if (firstBoundary >= 0) indexOfBytes(rawBytes, delimiterBytes, firstBoundary + delimiterBytes.size) else -1
            val thirdBoundary = if (secondBoundary >= 0) indexOfBytes(rawBytes, delimiterBytes, secondBoundary + delimiterBytes.size) else -1
            if (secondBoundary >= 0 && thirdBoundary > secondBoundary) {
                val sep = "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
                val headerEnd = indexOfBytes(rawBytes, sep, secondBoundary)
                if (headerEnd in (secondBoundary until thirdBoundary)) {
                    val payloadStart = headerEnd + sep.size
                    var payloadEnd = thirdBoundary
                    if (payloadEnd >= 2 && rawBytes[payloadEnd - 2] == '\r'.code.toByte() && rawBytes[payloadEnd - 1] == '\n'.code.toByte()) {
                        payloadEnd -= 2
                    }
                    if (payloadEnd >= payloadStart) {
                        binaryPart = rawBytes.copyOfRange(payloadStart, payloadEnd)
                    }
                }
            }

            if (binaryPart.isEmpty()) {
                binaryPart = contentText.toByteArray(Charsets.UTF_8)
            }
            return Triple(metadataJson, contentText, binaryPart)
        }

        private fun indexOfBytes(haystack: ByteArray, needle: ByteArray, fromIndex: Int): Int {
            if (needle.isEmpty() || fromIndex < 0) return -1
            outer@ for (i in fromIndex..(haystack.size - needle.size)) {
                for (j in needle.indices) {
                    if (haystack[i + j] != needle[j]) continue@outer
                }
                return i
            }
            return -1
        }

        private fun filterByQuery(q: String): List<DriveObject> {
            val active = storage.values.filter { !it.trashed }
            if (q.isBlank()) return active
            val nameMatch = Regex("""name\s*=\s*'([^']+)'""").find(q)?.groupValues?.get(1)
            val parentMatch = Regex("""'([^']+)'\s+in\s+parents""").find(q)?.groupValues?.get(1)
            val appPropMatches = Regex("""appProperties\s+has\s*\{\s*key='([^']+)'\s+and\s+value='([^']+)'\s*\}""").findAll(q).toList()
            val wantsFolderOnly = q.contains("mimeType = 'application/vnd.google-apps.folder'")
            val wantsNonFolderOnly = q.contains("mimeType != 'application/vnd.google-apps.folder'")
            return active.filter { obj ->
                val okName = nameMatch == null || obj.name == nameMatch
                val okParent = parentMatch == null || obj.parentId == parentMatch
                val okFolder = !wantsFolderOnly || obj.mimeType == GoogleDriveRestClient.FOLDER_MIME
                val okNonFolder = !wantsNonFolderOnly || obj.mimeType != GoogleDriveRestClient.FOLDER_MIME
                val okAppProps = appPropMatches.all { m ->
                    val k = m.groupValues[1]
                    val v = m.groupValues[2]
                    obj.appProperties[k] == v ||
                        (k == "record_uuid" && obj.appProperties["recordUuid"] == v) ||
                        (k == "recordUuid" && obj.appProperties["record_uuid"] == v)
                }
                okName && okParent && okFolder && okNonFolder && okAppProps
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

        private fun toJson(obj: DriveObject): String {
            return JSONObject().apply {
                put("id", obj.id)
                put("name", obj.name)
                put("mimeType", obj.mimeType)
                put("size", obj.binaryPayload.size.toString())
                put("parents", JSONArray(listOf(obj.parentId)))
                put("createdTime", "2026-11-20T10:00:00.000Z")
                put("modifiedTime", "2026-11-20T10:00:00.000Z")
                put("trashed", obj.trashed)
                if (obj.description.isNotBlank()) put("description", obj.description)
                put("appProperties", JSONObject().apply {
                    obj.appProperties.forEach { (k, v) -> put(k, v) }
                })
            }.toString()
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        GoogleDriveAuthManager.resetForTesting(context)
        context.deleteDatabase(dbName)
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.APP_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("saf_soc_drive_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        fakeClockMs.set(1_760_000_000_000L)
        GoogleDriveAuthManager.clockMillisProvider = { fakeClockMs.get() }
        fakeOAuth = FakeDriveOAuthTransport(fakeClockMs)
        GoogleDriveAuthManager.oauthTransport = fakeOAuth

        GoogleDriveAuthManager.markConnectedMetadata(
            context = context,
            email = "owner.solo@sewainaja.id",
            displayName = "SEWAin Aja Freezer Solo",
            photoUrl = ""
        )

        mockWebServer = MockWebServer()
        fakeCloud = OAuthEnforcingDriveServer()
        mockWebServer.dispatcher = fakeCloud.dispatcher
        mockWebServer.start()

        openAndWire()
    }

    private fun openAndWire() {
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
        restClient = GoogleDriveRestClient(
            apiBaseUrl = "$baseUrl/drive/v3",
            uploadBaseUrl = "$baseUrl/upload/drive/v3"
        )
        generationManager = DatabaseGenerationManager(context)
        driveService = GoogleDriveService(context, restClient, accessTokenOverride = null)
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
            accessTokenOverride = null
        ).apply {
            autoTriggerSyncOnMutation = false
        }
        repository.attachMirrorSyncEngine(engine)
        SyncWorker.testRepositoryFactory = { syncRepository }
    }

    @After
    fun tearDown() {
        SyncWorker.testRepositoryFactory = null
        GoogleDriveAuthManager.resetForTesting(context)
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        context.deleteDatabase(dbName)
        if (::mockWebServer.isInitialized) {
            mockWebServer.shutdown()
        }
    }

    // =========================================================================
    // SUITE A: OAUTH 2.0 AUTHENTICATION TESTS
    // =========================================================================

    @Test
    fun testA1_validAccessTokenSucceedsWithoutRedundantRefresh(): Unit = runBlocking {
        val validToken = "ya29.valid_active_token_001"
        fakeCloud.acceptedTokens.add(validToken)
        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = validToken,
            expiresAtMs = fakeClockMs.get() + 40L * 60L * 1000L,
            accountEmail = "owner.solo@sewainaja.id"
        )

        val tokenResult = driveService.resolveValidAccessTokenResult()
        assertTrue(tokenResult is DriveTokenResult.Valid)
        assertEquals(validToken, (tokenResult as DriveTokenResult.Valid).accessToken)
        assertEquals("Valid cached token must not trigger redundant silent OAuth refresh", 0, fakeOAuth.acquireCallCount.get())

        val rootFolderRes = driveService.ensureRootAppFolderId(tokenResult.accessToken)
        assertTrue("Ensure root folder must succeed with valid OAuth token", rootFolderRes.isSuccess)
        assertEquals(0, fakeCloud.unauthorizedResponsesIssued.get())
    }

    @Test
    fun testA2_expiredTokenProactivelyRefreshedBeforeAnyDriveHttpRequest(): Unit = runBlocking {
        val expiredToken = "ya29.expired_stale_token_old"
        val freshToken = "ya29.fresh_proactive_token_new"
        fakeCloud.acceptedTokens.add(freshToken)
        fakeOAuth.nextResultProvider = {
            DriveTokenResult.Valid(freshToken, fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS)
        }

        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = expiredToken,
            expiresAtMs = fakeClockMs.get() + 2L * 60L * 1000L,
            accountEmail = "owner.solo@sewainaja.id"
        )

        val resolved = driveService.resolveValidAccessTokenResult()
        assertTrue("Expired token must proactively refresh to Valid", resolved is DriveTokenResult.Valid)
        assertEquals(freshToken, (resolved as DriveTokenResult.Valid).accessToken)
        assertTrue("Stale token must be evicted from Play Services cache", expiredToken in fakeOAuth.clearedTokens)
        assertEquals(1, fakeOAuth.acquireCallCount.get())

        val rootRes = driveService.ensureRootAppFolderId(resolved.accessToken)
        assertTrue(rootRes.isSuccess)
        assertEquals("Expired token must NEVER be sent to Google Drive API", 0, fakeCloud.unauthorizedResponsesIssued.get())
        assertTrue(fakeCloud.observedAuthHeaders.none { it.contains(expiredToken) })
    }

    @Test
    fun testA3_http401OnDeletePhotoTransactionId9TriggersClearRefreshAndSingleRetry(): Unit = runBlocking {
        val staleToken = "ya29.revoked_server_side_token"
        val freshToken = "ya29.fresh_after_401_token"

        fakeCloud.acceptedTokens.add(freshToken)
        fakeOAuth.nextResultProvider = {
            DriveTokenResult.Valid(freshToken, fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS)
        }

        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = staleToken,
            expiresAtMs = fakeClockMs.get() + 30L * 60L * 1000L,
            accountEmail = "owner.solo@sewainaja.id"
        )

        val rootId = fakeCloud.seedFolder(GoogleDriveRestClient.BACKUP_FOLDER_NAME)
        val docId = fakeCloud.seedFolder(GoogleDriveMirrorSyncEngine.SUBFOLDER_DOCUMENTATION, rootId)
        val txDocId = fakeCloud.seedFolder(GoogleDriveMirrorSyncEngine.SUBFOLDER_TRANSACTIONS, docId)
        val photoUuid = UUID.randomUUID().toString()
        val seededPhoto = fakeCloud.seedFile(
            name = "PHOTO_TRANSACTION_ID_9.jpg",
            mimeType = "image/jpeg",
            parentId = txDocId,
            payloadBytes = ByteArray(1024) { (it % 127).toByte() },
            appProperties = mapOf(
                "appId" to MirrorOutboxCodec.APP_ID_VALUE,
                "record_uuid" to photoUuid,
                "entity_type" to GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                "generation_id" to "GENERATION_002"
            ),
            explicitId = "drv_photo_tx_9_exact_id"
        )

        val slotKey = "PHOTO_TRANSACTION:TX:9:SERAH_TERIMA"
        dao.upsertDriveCloudFileMap(
            DriveCloudFileMapEntity(
                localRecordKey = slotKey,
                recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                localRecordId = 9L,
                driveFileId = seededPhoto.id,
                driveFolderId = txDocId,
                fileName = "PHOTO_TRANSACTION_ID_9.jpg",
                contentHash = sha256Bytes(seededPhoto.binaryPayload),
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_DELETE_PENDING,
                datasetGenerationId = generationManager.getCurrentGenerationId(),
                recordUuid = photoUuid
            )
        )
        queueManager.enqueueDeleteOperation(
            localRecordKey = slotKey,
            recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
            recordId = 9L,
            recordUuid = photoUuid,
            explicitDriveFileId = seededPhoto.id
        )

        val batch = syncRepository.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true).getOrThrow()

        assertEquals(
            "Batch must succeed after automatic 401 refresh + single retry",
            GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS,
            batch.overallSyncStatus
        )
        assertEquals(0, batch.totalFailedInDb)
        assertEquals(0, batch.totalFailedAuthInDb)
        assertEquals(0, batch.totalPendingInDb)
        assertTrue("HTTP 401 must have occurred on first request with staleToken", fakeCloud.unauthorizedResponsesIssued.get() >= 1)
        assertTrue("Stale token must be cleared via GoogleAuthUtil.clearToken", staleToken in fakeOAuth.clearedTokens)
        assertEquals("Refresh must execute exactly ONCE", 1, fakeOAuth.acquireCallCount.get())
        assertNull("PHOTO_TRANSACTION_ID_9.jpg must be verified deleted from Google Drive", fakeCloud.storage[seededPhoto.id])
        assertTrue("Local queue must be completely empty after verified delete", dao.getAllDriveLocalSyncQueueOnce().isEmpty())
    }

    @Test
    fun testA4_concurrent401RequestsExecuteSingleFlightRefreshExactlyOnce(): Unit = runBlocking {
        val staleToken = "ya29.concurrent_stale_token"
        val freshToken = "ya29.concurrent_refreshed_token"
        fakeCloud.acceptedTokens.add(freshToken)

        fakeOAuth.nextResultProvider = { seq ->
            Thread.sleep(40L)
            DriveTokenResult.Valid(
                accessToken = if (seq == 1) freshToken else "ya29.unexpected_second_refresh_$seq",
                expiresAtMs = fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS
            )
        }

        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = staleToken,
            expiresAtMs = fakeClockMs.get() + 30L * 60L * 1000L,
            accountEmail = "owner.solo@sewainaja.id"
        )

        val rootId = fakeCloud.seedFolder(GoogleDriveRestClient.BACKUP_FOLDER_NAME)
        val fileIds = (1..8).map { idx ->
            fakeCloud.seedFile(
                name = "CONCURRENT_$idx.json",
                mimeType = "application/json",
                parentId = rootId,
                payloadBytes = """{"idx":$idx}""".toByteArray(),
                appProperties = mapOf("appId" to MirrorOutboxCodec.APP_ID_VALUE)
            ).id
        }

        val results = coroutineScope {
            fileIds.map { fid ->
                async {
                    driveService.getFileMetadataById(staleToken, fid)
                }
            }.awaitAll()
        }

        assertTrue("All 8 concurrent Drive requests must succeed after single-flight refresh", results.all { it.isSuccess && it.getOrNull() != null })
        assertEquals("Single-flight Mutex must call OAuth refresh EXACTLY ONCE across 8 concurrent 401s", 1, fakeOAuth.acquireCallCount.get())
    }

    @Test
    fun testA5_revokedOAuthMarksFailedAuthStopsCascadeAndSyncWorkerDoesNotInfiniteRetry(): Unit = runBlocking {
        val revokedToken = "ya29.permanently_revoked_token"
        fakeOAuth.nextResultProvider = {
            DriveTokenResult.RequiresReAuth("Akses Google Drive dicabut oleh pengguna. Silakan login ulang.")
        }
        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = revokedToken,
            expiresAtMs = fakeClockMs.get() + 30L * 60L * 1000L,
            accountEmail = "owner.solo@sewainaja.id"
        )

        for (i in 1..5) {
            val unitId = dao.insertUnit(makeUnit("FRZ-AUTH-$i"))
            syncRepository.enqueueUnitChange(dao.getUnitById(unitId)!!, isNew = true)
        }

        val batch = syncRepository.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true).getOrThrow()

        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH, batch.overallSyncStatus)
        assertTrue("requiresReAuth must be true", batch.requiresReAuth)
        assertEquals("Only item #1 should transition to FAILED_AUTH (loop must stop immediately)", 1, batch.totalFailedAuthInDb)
        assertEquals("Remaining 4 items stay safely preserved as PENDING_SYNC in SQLite", 4, batch.totalPendingInDb)
        assertNotEquals("NEVER emit SUCCESS when auth failed", GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, batch.overallSyncStatus)

        val dash = syncRepository.dashboardState.value
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH, dash.syncStatusCode)
        assertTrue(dash.requiresReAuth)
        assertEquals(1, dash.failedAuthCount)

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val workerResult = worker.doWork()
        assertEquals(
            "SyncWorker must NOT retry infinitely when OAuth requires re-login",
            ListenableWorker.Result.failure(),
            workerResult
        )
    }

    @Test
    fun testA6_reLoginAutomaticallyRecoversFailedAuthQueueToSuccess(): Unit = runBlocking {
        val revokedToken = "ya29.revoked_phase1"
        fakeOAuth.nextResultProvider = {
            DriveTokenResult.RequiresReAuth("Token dicabut")
        }
        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = revokedToken,
            expiresAtMs = fakeClockMs.get() + 30L * 60L * 1000L,
            accountEmail = "owner.solo@sewainaja.id"
        )

        val unitId = dao.insertUnit(makeUnit("FRZ-RELOGIN-1"))
        syncRepository.enqueueUnitChange(dao.getUnitById(unitId)!!, isNew = true)

        val failedBatch = syncRepository.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true).getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH, failedBatch.overallSyncStatus)
        assertTrue(failedBatch.requiresReAuth)

        val reLoginToken = "ya29.fresh_after_relogin_ok"
        fakeCloud.acceptedTokens.add(reLoginToken)
        fakeOAuth.nextResultProvider = {
            DriveTokenResult.Valid(reLoginToken, fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS)
        }
        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = reLoginToken,
            expiresAtMs = fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS,
            accountEmail = "owner.solo@sewainaja.id"
        )

        val recoveredBatch = syncRepository.onAuthRecoveredAndResumeQueue().getOrThrow()

        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, recoveredBatch.overallSyncStatus)
        assertFalse(recoveredBatch.requiresReAuth)
        assertEquals(0, recoveredBatch.totalFailedInDb)
        assertEquals(0, recoveredBatch.totalFailedAuthInDb)
        assertEquals(0, recoveredBatch.totalPendingInDb)
        assertEquals(0, dao.getAllDriveLocalSyncQueueOnce().size)
        assertFalse(syncRepository.dashboardState.value.requiresReAuth)
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, syncRepository.dashboardState.value.syncStatusCode)
    }

    // =========================================================================
    // SUITE B: ALL GOOGLE DRIVE FILE OPERATIONS (BY REAL fileId)
    // =========================================================================

    @Test
    fun testB1_fullDriveFileOperationsUploadDownloadMetadataAppPropertiesListDeleteByRealFileId(): Unit = runBlocking {
        val validToken = "ya29.valid_file_ops_token"
        fakeCloud.acceptedTokens.add(validToken)
        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = validToken,
            expiresAtMs = fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS,
            accountEmail = "owner.solo@sewainaja.id"
        )

        val folderId = driveService.ensureCategoryMirrorFolderId(
            validToken,
            GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION
        ).getOrThrow()

        val photoBytes = ByteArray(2048) { ((it * 31 + 7) and 0xFF).toByte() }
        val photoSha256 = sha256Bytes(photoBytes)
        val localPhoto = File(context.cacheDir, "test_photo_tx_99.jpg").apply { writeBytes(photoBytes) }
        val recordUuid = UUID.randomUUID().toString()

        // 1. UPLOAD photo + Read-after-Write SHA-256 verification
        val uploadRes = driveService.uploadOrReplacePhotoWithVerification(
            accessToken = validToken,
            folderId = folderId,
            existingDriveFileId = null,
            localFile = localPhoto,
            targetFileName = "PHOTO_TRANSACTION_ID_99.jpg",
            contentHash = photoSha256,
            description = "Audit test photo transaction 99",
            appProperties = mapOf(
                "appId" to MirrorOutboxCodec.APP_ID_VALUE,
                "record_uuid" to recordUuid,
                "entity_type" to GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                "generation_id" to "GENERATION_002",
                "checksum" to photoSha256,
                "checksumSha256" to photoSha256
            )
        ).getOrThrow()
        assertTrue("Uploaded file must have a real Google Drive fileId", uploadRes.id.isNotBlank())

        val siblingObj = fakeCloud.seedFile(
            name = "PHOTO_TRANSACTION_ID_100.jpg",
            mimeType = "image/jpeg",
            parentId = folderId,
            payloadBytes = ByteArray(256) { 9 },
            appProperties = mapOf("appId" to MirrorOutboxCodec.APP_ID_VALUE)
        )

        // 2. DOWNLOAD photo by exact fileId & verify byte-for-byte SHA-256 parity
        val downloadedFile = File(context.cacheDir, "downloaded_tx_99.jpg")
        driveService.downloadFileToLocal(validToken, uploadRes.id, downloadedFile).getOrThrow()
        assertArrayEquals("Downloaded photo bytes must match uploaded bytes exactly", photoBytes, downloadedFile.readBytes())
        assertEquals(photoSha256, sha256Bytes(downloadedFile.readBytes()))

        // 3. READ METADATA by exact fileId
        val meta = driveService.getFileMetadataById(validToken, uploadRes.id).getOrThrow()
        assertNotNull("Metadata must exist for uploaded fileId", meta)
        assertEquals(uploadRes.id, meta!!.id)
        assertEquals(recordUuid, meta.rawAppProperties["record_uuid"])

        // 4. UPDATE appProperties by exact fileId
        val updatedMeta = driveService.updateFileAppProperties(
            accessToken = validToken,
            fileId = uploadRes.id,
            appProperties = mapOf("revision" to "2", "audit_tag" to "verified_ok")
        ).getOrThrow()
        assertEquals("2", updatedMeta.rawAppProperties["revision"])
        assertEquals("verified_ok", updatedMeta.rawAppProperties["audit_tag"])

        // 5. LIST files by recordUuid
        val listedByUuid = driveService.findFilesByRecordUuid(validToken, recordUuid).getOrThrow()
        assertEquals(1, listedByUuid.size)
        assertEquals(uploadRes.id, listedByUuid.first().id)

        // 6. DELETE photo by exact fileId & verify 404 absence
        val deleteRes = driveService.deleteFileStrictAndVerify(validToken, uploadRes.id, "PHOTO_TRANSACTION_ID_99.jpg")
        assertTrue("Strict delete + 404 verify must succeed", deleteRes.isSuccess)
        val metaAfterDelete = driveService.getFileMetadataById(validToken, uploadRes.id).getOrThrow()
        assertNull("Deleted fileId must return null (HTTP 404)", metaAfterDelete)
        assertNotNull("Unrelated sibling file must remain completely untouched", fakeCloud.storage[siblingObj.id])

        localPhoto.delete()
        downloadedFile.delete()
    }

    // =========================================================================
    // SUITE C: DATABASE CRUD (UNIT, CUSTOMER, TRANSACTION) + CLOUD CONSISTENCY
    // =========================================================================

    @Test
    fun testC1_crudUnitCustomerTransactionSyncEndToEndWithUuidV4AndZeroResurrection(): Unit = runBlocking {
        val validToken = "ya29.crud_valid_token"
        fakeCloud.acceptedTokens.add(validToken)
        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = validToken,
            expiresAtMs = fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS,
            accountEmail = "owner.solo@sewainaja.id"
        )

        // ADD Unit, Customer, Transaction
        val unitId = dao.insertUnit(makeUnit("FRZ-CRUD-01"))
        val unit = dao.getUnitById(unitId)!!
        val originalUnitUuid = unit.uuid
        assertTrue("Unit must have valid UUID v4", MirrorOutboxCodec.isValidUuidV4(originalUnitUuid))
        syncRepository.enqueueUnitChange(unit, isNew = true)

        val custId = dao.insertCustomer(makeCustomer("Budi Santoso", "3372010101010001", "081234567890"))
        val customer = dao.getCustomerById(custId)!!
        syncRepository.enqueueCustomerChange(customer, isNew = true)

        val txId = dao.insertTransaction(
            makeTransaction("INV-CRUD-01", custId, customer.namaLengkap, unitId, unit.kodeUnit)
        )
        val tx = dao.getTransactionById(txId)!!
        syncRepository.enqueueTransactionChange(tx, isNew = true)

        val addBatch = syncRepository.processSyncQueue(ignoreBackoffTimers = true).getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, addBatch.overallSyncStatus)
        assertEquals(0, addBatch.totalFailedInDb)
        assertEquals(0, addBatch.totalPendingInDb)

        val unitMapAfterAdd = dao.getDriveCloudFileMapByKey("UNIT:$unitId")
        assertNotNull(unitMapAfterAdd)
        val driveFileIdBeforeEdit = unitMapAfterAdd!!.driveFileId

        // EDIT Unit, Customer, Transaction
        val editedUnit = unit.copy(kondisiUnit = "Prima Terawat", version = unit.version + 1, updatedAtMillis = fakeClockMs.get() + 5000L)
        dao.updateUnit(editedUnit)
        syncRepository.enqueueUnitChange(editedUnit, isNew = false)

        val editedCustomer = customer.copy(alamatLengkap = "Jl. Adi Sucipto Solo", version = customer.version + 1, updatedAtMillis = fakeClockMs.get() + 5000L)
        dao.updateCustomer(editedCustomer)
        syncRepository.enqueueCustomerChange(editedCustomer, isNew = false)

        val editedTx = tx.copy(catatan = "Perpanjangan 1 bulan", version = tx.version + 1, updatedAtMillis = fakeClockMs.get() + 5000L)
        dao.updateTransaction(editedTx)
        syncRepository.enqueueTransactionChange(editedTx, isNew = false)

        val editBatch = syncRepository.processSyncQueue(ignoreBackoffTimers = true).getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, editBatch.overallSyncStatus)
        val unitMapAfterEdit = dao.getDriveCloudFileMapByKey("UNIT:$unitId")!!
        assertEquals("In-place edit must preserve exact Drive fileId without duplicates", driveFileIdBeforeEdit, unitMapAfterEdit.driveFileId)
        assertEquals("UUID v4 must never change on edit/sync", originalUnitUuid, dao.getUnitById(unitId)!!.uuid)

        // DELETE Transaction -> Customer -> Unit (soft-delete tombstone + verified cloud deletion)
        dao.softDeleteTransaction(txId)
        syncRepository.enqueueTransactionDeletion(editedTx)
        dao.softDeleteCustomer(custId)
        syncRepository.enqueueCustomerDeletion(editedCustomer)
        dao.softDeleteUnit(unitId)
        syncRepository.enqueueUnitDeletion(editedUnit)

        val deleteBatch = syncRepository.processSyncQueue(ignoreBackoffTimers = true).getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, deleteBatch.overallSyncStatus)
        assertEquals(0, dao.getAllDriveLocalSyncQueueOnce().size)
        assertNull("Deleted unit cloud file must be removed", fakeCloud.storage[driveFileIdBeforeEdit])
        assertTrue("Active local units must be empty after delete", dao.getAllUnitsOnce().isEmpty())
        assertTrue("Active local customers must be empty after delete", dao.getAllCustomersOnce().isEmpty())
        assertTrue("Active local transactions must be empty after delete", dao.getAllTransactionsOnce().isEmpty())

        val audit = syncRepository.runDataConsistencyAudit().getOrThrow()
        assertTrue("Local & Cloud must be 100% consistent after full CRUD lifecycle", audit.isConsistent)
    }

    // =========================================================================
    // SUITE D: RECOVERY OF 25 FAILED OPS IN GENERATION_002 / MANIFEST v158 + RESILIENCE
    // =========================================================================

    @Test
    fun testD1_recover25FailedOperationsInGeneration002WithManifestV158ZeroDuplicatesZeroDataLoss(): Unit = runBlocking {
        // 1. Reproduce EXACT production metadata: GENERATION_002, Manifest Cloud v158, ACTIVE
        val mirrorPrefs = context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)
        val productionDatasetId = "dataset_prod_soc_gen002"
        mirrorPrefs.edit()
            .putInt(DatabaseGenerationManager.KEY_GENERATION_SEQ, 2) // GENERATION_002
            .putString(DatabaseGenerationManager.KEY_DATASET_ID, productionDatasetId)
            .putLong(DatabaseGenerationManager.KEY_SYNC_VERSION, 158L) // Manifest Cloud v158
            .putString(DatabaseGenerationManager.KEY_DATASET_STATUS, "ACTIVE")
            .putString(DatabaseGenerationManager.KEY_OVERALL_STATUS, GoogleDriveMirrorSyncEngine.STATUS_FAILED)
            .commit()

        assertEquals("GENERATION_002", generationManager.getCurrentGenerationId())
        assertEquals(158L, generationManager.getSyncVersion())

        val validToken = "ya29.recovery_gen002_v158_token"
        fakeCloud.acceptedTokens.add(validToken)
        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = validToken,
            expiresAtMs = fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS,
            accountEmail = "owner.solo@sewainaja.id"
        )

        val rootId = fakeCloud.seedFolder(GoogleDriveRestClient.BACKUP_FOLDER_NAME)
        val activeDataId = fakeCloud.seedFolder(GoogleDriveMirrorSyncEngine.SUBFOLDER_ACTIVE_DATA, rootId)
        val docId = fakeCloud.seedFolder(GoogleDriveMirrorSyncEngine.SUBFOLDER_DOCUMENTATION, rootId)
        val unitsFolderId = fakeCloud.seedFolder(GoogleDriveMirrorSyncEngine.SUBFOLDER_UNITS, activeDataId)
        val customersFolderId = fakeCloud.seedFolder(GoogleDriveMirrorSyncEngine.SUBFOLDER_CUSTOMERS, activeDataId)
        val txFolderId = fakeCloud.seedFolder(GoogleDriveMirrorSyncEngine.SUBFOLDER_TRANSACTIONS, activeDataId)
        val txDocFolderId = fakeCloud.seedFolder(GoogleDriveMirrorSyncEngine.SUBFOLDER_TRANSACTIONS, docId)

        val manifestSeed = JSONObject().apply {
            put("applicationId", GoogleDriveMirrorSyncEngine.APPLICATION_ID)
            put("datasetId", productionDatasetId)
            put("datasetGeneration", "GENERATION_002")
            put("datasetStatus", "ACTIVE")
            put("syncVersion", 158L)
            put("databaseVersion", MirrorOutboxCodec.CURRENT_SCHEMA_VERSION)
        }.toString()
        val manifestObj = fakeCloud.seedFile(
            name = GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME,
            mimeType = "application/json",
            parentId = rootId,
            payloadBytes = manifestSeed.toByteArray(),
            appProperties = mapOf(
                "appId" to MirrorOutboxCodec.APP_ID_VALUE,
                "generation_id" to "GENERATION_002"
            )
        )

        val unrelatedCloudFile = fakeCloud.seedFile(
            name = "LAPORAN_PRIBADI_OWNER.pdf",
            mimeType = "application/pdf",
            parentId = rootId,
            payloadBytes = "keep-me-safe".toByteArray(),
            appProperties = emptyMap(),
            explicitId = "drv_unrelated_owner_doc"
        )

        // Seed existing historical records in SQLite (must remain 100% preserved!)
        val preservedCustomerId = dao.insertCustomer(
            makeCustomer("Pelanggan Historis Solo", "3372019999990001", "081111222333", "Laweyan, Solo")
        )
        val preservedUnitId = dao.insertUnit(makeUnit("FRZ-HIST-001", merek = "RSA", kapasitasLiter = 210))
        val preservedTxId = dao.insertTransaction(
            makeTransaction("INV-HIST-001", preservedCustomerId, "Pelanggan Historis Solo", preservedUnitId, "FRZ-HIST-001", statusPenyewaan = "Selesai")
        )
        val preservedTx = dao.getTransactionById(preservedTxId)!!
        val preservedCustomer = dao.getCustomerById(preservedCustomerId)!!
        val preservedUnit = dao.getUnitById(preservedUnitId)!!

        // Seed matching cloud files & mappings for those 3 already-synced historical records
        for (entry in listOf(
            listOf("UNIT:$preservedUnitId", GoogleDriveMirrorSyncEngine.CATEGORY_UNIT, preservedUnitId, preservedUnit.uuid, unitsFolderId,
                MirrorOutboxCodec.serializeUnitJson(preservedUnit, productionDatasetId, "GENERATION_002", fakeClockMs.get())),
            listOf("CUSTOMER:$preservedCustomerId", GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER, preservedCustomerId, preservedCustomer.uuid, customersFolderId,
                MirrorOutboxCodec.serializeCustomerJson(preservedCustomer, productionDatasetId, "GENERATION_002", fakeClockMs.get())),
            listOf("TRANSACTION:$preservedTxId", GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION, preservedTxId, preservedTx.uuid, txFolderId,
                MirrorOutboxCodec.serializeTransactionJson(preservedTx, productionDatasetId, "GENERATION_002", fakeClockMs.get()))
        )) {
            val key = entry[0] as String
            val cat = entry[1] as String
            val recId = entry[2] as Long
            val uuid = entry[3] as String
            val folder = entry[4] as String
            val payloadStr = entry[5] as String
            val sha = MirrorOutboxCodec.computeSha256(payloadStr)
            val fileObj = fakeCloud.seedFile(
                name = "$uuid.json",
                mimeType = "application/json",
                parentId = folder,
                payloadBytes = payloadStr.toByteArray(),
                appProperties = mapOf(
                    "appId" to MirrorOutboxCodec.APP_ID_VALUE,
                    "record_uuid" to uuid,
                    "entity_type" to cat,
                    "generation_id" to "GENERATION_002",
                    "checksum" to sha,
                    "checksumSha256" to sha
                )
            )
            dao.upsertDriveCloudFileMap(
                DriveCloudFileMapEntity(
                    localRecordKey = key,
                    recordCategory = cat,
                    localRecordId = recId,
                    driveFileId = fileObj.id,
                    driveFolderId = folder,
                    fileName = fileObj.name,
                    contentHash = sha,
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_SYNCED,
                    datasetGenerationId = "GENERATION_002",
                    recordUuid = uuid
                )
            )
            when (cat) {
                GoogleDriveMirrorSyncEngine.CATEGORY_UNIT ->
                    dao.updateUnit(preservedUnit.copy(syncStatus = MirrorOutboxCodec.STATUS_SYNCED, driveId = fileObj.id))
                GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER ->
                    dao.updateCustomer(preservedCustomer.copy(syncStatus = MirrorOutboxCodec.STATUS_SYNCED, driveId = fileObj.id))
                GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION ->
                    dao.updateTransaction(preservedTx.copy(syncStatus = MirrorOutboxCodec.STATUS_SYNCED, driveId = fileObj.id))
            }
        }

        // 2. Seed EXACTLY 25 FAILED operations in drive_local_sync_queue
        val alreadyOnCloudFileIds = mutableListOf<String>()
        val deletedTargetFileIds = mutableListOf<String>()

        // Ops 1..5: CREATE Unit already present on Cloud with identical SHA-256 (zero duplicate!)
        for (i in 1..5) {
            val uId = dao.insertUnit(makeUnit("FRZ-REC-EXIST-$i"))
            val u = dao.getUnitById(uId)!!
            val payload = MirrorOutboxCodec.serializeUnitJson(u, productionDatasetId, "GENERATION_002", fakeClockMs.get())
            val sha = MirrorOutboxCodec.computeSha256(payload)
            val existingRemote = fakeCloud.seedFile(
                name = "${u.uuid}.json",
                mimeType = "application/json",
                parentId = unitsFolderId,
                payloadBytes = payload.toByteArray(),
                appProperties = mapOf(
                    "appId" to MirrorOutboxCodec.APP_ID_VALUE,
                    "record_uuid" to u.uuid,
                    "entity_type" to GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                    "generation_id" to "GENERATION_002",
                    "checksum" to sha,
                    "checksumSha256" to sha
                )
            )
            alreadyOnCloudFileIds.add(existingRemote.id)
            dao.upsertDriveLocalSyncQueueItem(
                DriveLocalSyncQueueEntity(
                    localRecordKey = "UNIT:$uId",
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                    recordId = uId,
                    operationType = GoogleDriveMirrorSyncEngine.OP_CREATE,
                    payloadJson = payload,
                    contentHash = sha,
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    retryCount = 3,
                    lastError = "HTTP 401 UNAUTHENTICATED Invalid Credentials",
                    datasetGenerationId = "GENERATION_002",
                    recordUuid = u.uuid,
                    operationId = UUID.randomUUID().toString()
                )
            )
        }

        // Ops 6..10: CREATE new Customers not yet on Cloud
        for (i in 1..5) {
            val cId = dao.insertCustomer(makeCustomer("Pelanggan Recovery $i", "337201888888000$i", "0819000000$i", "Surakarta #$i"))
            val c = dao.getCustomerById(cId)!!
            val payload = MirrorOutboxCodec.serializeCustomerJson(c, productionDatasetId, "GENERATION_002", fakeClockMs.get())
            dao.upsertDriveLocalSyncQueueItem(
                DriveLocalSyncQueueEntity(
                    localRecordKey = "CUSTOMER:$cId",
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_CUSTOMER,
                    recordId = cId,
                    operationType = GoogleDriveMirrorSyncEngine.OP_CREATE,
                    payloadJson = payload,
                    contentHash = MirrorOutboxCodec.computeSha256(payload),
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    retryCount = 2,
                    lastError = "HTTP 401 UNAUTHENTICATED Invalid Credentials",
                    datasetGenerationId = "GENERATION_002",
                    recordUuid = c.uuid,
                    operationId = UUID.randomUUID().toString()
                )
            )
        }

        // Ops 11..15: UPDATE Units already mapped on Cloud (PATCH in place)
        for (i in 1..5) {
            val uId = dao.insertUnit(makeUnit("FRZ-REC-UPD-$i", merek = "RSA", version = 2L))
            val u = dao.getUnitById(uId)!!
            val oldRemote = fakeCloud.seedFile(
                name = "${u.uuid}.json",
                mimeType = "application/json",
                parentId = unitsFolderId,
                payloadBytes = """{"old":true}""".toByteArray(),
                appProperties = mapOf(
                    "appId" to MirrorOutboxCodec.APP_ID_VALUE,
                    "record_uuid" to u.uuid,
                    "entity_type" to GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                    "generation_id" to "GENERATION_002"
                )
            )
            dao.upsertDriveCloudFileMap(
                DriveCloudFileMapEntity(
                    localRecordKey = "UNIT:$uId",
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                    localRecordId = uId,
                    driveFileId = oldRemote.id,
                    driveFolderId = unitsFolderId,
                    fileName = oldRemote.name,
                    contentHash = "old_hash",
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    datasetGenerationId = "GENERATION_002",
                    recordUuid = u.uuid
                )
            )
            val payload = MirrorOutboxCodec.serializeUnitJson(u, productionDatasetId, "GENERATION_002", fakeClockMs.get())
            dao.upsertDriveLocalSyncQueueItem(
                DriveLocalSyncQueueEntity(
                    localRecordKey = "UNIT:$uId",
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                    recordId = uId,
                    operationType = GoogleDriveMirrorSyncEngine.OP_UPDATE,
                    payloadJson = payload,
                    contentHash = MirrorOutboxCodec.computeSha256(payload),
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    retryCount = 4,
                    lastError = "HTTP 401 UNAUTHENTICATED Invalid Credentials",
                    datasetGenerationId = "GENERATION_002",
                    recordUuid = u.uuid,
                    version = 2L,
                    operationId = UUID.randomUUID().toString()
                )
            )
        }

        // Ops 16..20: DELETE photos still live on Cloud (including exact PHOTO_TRANSACTION_ID_9.jpg)
        for (i in 1..5) {
            val txNum = 8L + i // 9, 10, 11, 12, 13
            val fileName = "PHOTO_TRANSACTION_ID_${txNum}.jpg"
            val photoUuid = UUID.randomUUID().toString()
            val remotePhoto = fakeCloud.seedFile(
                name = fileName,
                mimeType = "image/jpeg",
                parentId = txDocFolderId,
                payloadBytes = ByteArray(512) { i.toByte() },
                appProperties = mapOf(
                    "appId" to MirrorOutboxCodec.APP_ID_VALUE,
                    "record_uuid" to photoUuid,
                    "entity_type" to GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                    "generation_id" to "GENERATION_002"
                )
            )
            deletedTargetFileIds.add(remotePhoto.id)
            val slotKey = "PHOTO_TRANSACTION:TX:$txNum:SERAH_TERIMA"
            dao.upsertDriveCloudFileMap(
                DriveCloudFileMapEntity(
                    localRecordKey = slotKey,
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                    localRecordId = txNum,
                    driveFileId = remotePhoto.id,
                    driveFolderId = txDocFolderId,
                    fileName = fileName,
                    contentHash = sha256Bytes(remotePhoto.binaryPayload),
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    datasetGenerationId = "GENERATION_002",
                    recordUuid = photoUuid
                )
            )
            dao.upsertDriveLocalSyncQueueItem(
                DriveLocalSyncQueueEntity(
                    localRecordKey = slotKey,
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                    recordId = txNum,
                    operationType = GoogleDriveMirrorSyncEngine.OP_DELETE,
                    payloadJson = JSONObject().put("driveFileId", remotePhoto.id).toString(),
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    retryCount = 5,
                    lastError = "HTTP 401 UNAUTHENTICATED Invalid Credentials Expected OAuth 2 access token ($fileName)",
                    datasetGenerationId = "GENERATION_002",
                    recordUuid = photoUuid,
                    operationId = UUID.randomUUID().toString()
                )
            )
        }

        // Ops 21..25: DELETE items already absent on Cloud (404 idempotent completion)
        for (i in 1..5) {
            val ghostTxNum = 50L + i
            val ghostUuid = UUID.randomUUID().toString()
            val slotKey = "PHOTO_TRANSACTION:TX:$ghostTxNum:SERAH_TERIMA"
            dao.upsertDriveLocalSyncQueueItem(
                DriveLocalSyncQueueEntity(
                    localRecordKey = slotKey,
                    recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_PHOTO_TRANSACTION,
                    recordId = ghostTxNum,
                    operationType = GoogleDriveMirrorSyncEngine.OP_DELETE,
                    payloadJson = JSONObject().put("driveFileId", "drv_already_gone_$ghostTxNum").toString(),
                    syncStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED,
                    retryCount = 3,
                    lastError = "HTTP 401 UNAUTHENTICATED",
                    datasetGenerationId = "GENERATION_002",
                    recordUuid = ghostUuid,
                    operationId = UUID.randomUUID().toString()
                )
            )
        }

        // Verify exact pre-recovery dashboard snapshot: Data Pending = 0, Data Gagal = 25, GENERATION_002, v158
        val preDashboard = syncRepository.refreshDashboardStateSnapshot()
        assertEquals(0, preDashboard.pendingSyncCount)
        assertEquals(25, preDashboard.failedSyncCount)
        assertEquals("GENERATION_002", preDashboard.datasetGeneration)
        assertEquals(158L, preDashboard.manifestState.syncVersion)
        assertEquals("ACTIVE", preDashboard.manifestState.datasetStatus)
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_FAILED, preDashboard.syncStatusCode)

        val postsBeforeRecovery = fakeCloud.uploadPostCount.get()

        // 3. EXECUTE RECOVERY OF ALL 25 FAILED OPERATIONS
        val recovery = syncRepository.recoverFailedSyncOperations().getOrThrow()

        val remainingAfter = dao.getAllDriveLocalSyncQueueOnce()
        assertEquals(
            "Recovery must finish with SUCCESS (failed=${recovery.totalFailedInDb}, auth=${recovery.totalFailedAuthInDb}, pending=${recovery.totalPendingInDb}, conflict=${recovery.totalConflictInDb}, err=${recovery.errorMessage}, remaining=${remainingAfter.map { "${it.localRecordKey}:${it.operationType}:${it.syncStatus}:${it.lastError}" }})",
            GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS,
            recovery.overallSyncStatus
        )
        assertEquals(0, recovery.totalFailedInDb)
        assertEquals(0, recovery.totalFailedAuthInDb)
        assertEquals(0, recovery.totalPendingInDb)
        assertEquals(0, recovery.totalConflictInDb)
        assertEquals(0, dao.getAllDriveLocalSyncQueueOnce().size)

        // Zero duplicates for the 5 files already present on cloud
        assertTrue(
            "Files already matching SHA-256 on cloud must reconcile without duplicate upload",
            recovery.alreadyResolvedOnCloudCount >= 5
        )
        for (existingId in alreadyOnCloudFileIds) {
            val obj = fakeCloud.storage[existingId]!!
            val sameUuidFiles = fakeCloud.storage.values.filter { !it.trashed && it.appProperties["record_uuid"] == obj.appProperties["record_uuid"] }
            assertEquals("Zero duplicate files allowed on Google Drive for UUID ${obj.appProperties["record_uuid"]}", 1, sameUuidFiles.size)
        }
        assertTrue(fakeCloud.uploadPostCount.get() - postsBeforeRecovery >= 5)

        // All 5 live DELETE targets (including PHOTO_TRANSACTION_ID_9.jpg) verified absent
        for (deletedId in deletedTargetFileIds) {
            assertNull("Deleted photo $deletedId must be absent from Google Drive", fakeCloud.storage[deletedId])
        }

        // Unrelated owner file untouched
        assertNotNull("Unrelated cloud file must NEVER be deleted", fakeCloud.storage[unrelatedCloudFile.id])

        // GENERATION_002 NOT rotated & Manifest Cloud incremented cleanly from v158 -> v159+
        assertEquals("GENERATION_002 must NEVER be changed/rotated during recovery", "GENERATION_002", generationManager.getCurrentGenerationId())
        assertTrue("Manifest Cloud version must advance cleanly beyond v158", generationManager.getSyncVersion() >= 159L)
        assertEquals("ACTIVE", generationManager.getDatasetStatus())
        assertNotNull("Cloud manifest.json must remain intact", fakeCloud.storage[manifestObj.id])

        // Historical transaction data in SQLite 100% preserved
        assertNotNull("Historical transaction must remain intact", dao.getTransactionById(preservedTxId))
        assertEquals(1, dao.getAllTransactionsOnce().size)
        assertEquals(11, dao.getAllUnitsOnce().size)
        assertEquals(6, dao.getAllCustomersOnce().size)

        val postDashboard = syncRepository.dashboardState.value
        assertEquals(0, postDashboard.pendingSyncCount)
        assertEquals(0, postDashboard.failedSyncCount)
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, postDashboard.syncStatusCode)
        assertEquals("GENERATION_002", postDashboard.datasetGeneration)

        val auditReport = syncRepository.runDataConsistencyAudit().getOrThrow()
        assertTrue("Local SQLite and Google Drive must be 100% consistent after recovering 25 ops", auditReport.isConsistent)
    }

    @Test
    fun testD2_offlineDuringUploadAndDeleteThenOnlineResumesWithoutDuplicates(): Unit = runBlocking {
        val validToken = "ya29.offline_resume_token"
        fakeCloud.acceptedTokens.add(validToken)
        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = validToken,
            expiresAtMs = fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS,
            accountEmail = "owner.solo@sewainaja.id"
        )

        syncRepository.setSimulatedOfflineMode(true)
        val photoFile = File(context.cacheDir, "offline_unit_photo.jpg").apply { writeBytes(ByteArray(512) { 42 }) }
        val unitId = dao.insertUnit(makeUnit("FRZ-OFFLINE-1", merek = "Aqua", fotoUnitUri = photoFile.absolutePath))
        val unit = dao.getUnitById(unitId)!!
        syncRepository.enqueueUnitChange(unit, isNew = true)

        val offlineBatch = syncRepository.processSyncQueue().getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_NETWORK, offlineBatch.overallSyncStatus)
        assertTrue(offlineBatch.totalPendingInDb >= 2)

        syncRepository.setSimulatedOfflineMode(false)
        val onlineBatch = syncRepository.processSyncQueue(ignoreBackoffTimers = true).getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, onlineBatch.overallSyncStatus)
        assertEquals(0, dao.getAllDriveLocalSyncQueueOnce().size)
        val unitMap = dao.getDriveCloudFileMapByKey("UNIT:$unitId")!!
        val photoMap = dao.getDriveCloudFileMapByKey("PHOTO_FREEZER:UNIT:$unitId")!!
        assertNotNull(fakeCloud.storage[unitMap.driveFileId])
        assertNotNull(fakeCloud.storage[photoMap.driveFileId])

        syncRepository.setSimulatedOfflineMode(true)
        dao.softDeleteUnit(unitId)
        syncRepository.enqueueUnitDeletion(unit)
        val offlineDeleteBatch = syncRepository.processSyncQueue().getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_NETWORK, offlineDeleteBatch.overallSyncStatus)

        syncRepository.setSimulatedOfflineMode(false)
        val onlineDeleteBatch = syncRepository.processSyncQueue(ignoreBackoffTimers = true).getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, onlineDeleteBatch.overallSyncStatus)
        assertNull(fakeCloud.storage[unitMap.driveFileId])
        assertNull(fakeCloud.storage[photoMap.driveFileId])
        assertEquals(0, dao.getAllDriveLocalSyncQueueOnce().size)

        photoFile.delete()
    }

    @Test
    fun testD3_forceCloseMidSyncStuckUploadingRowsResumeCleanlyOnReopen(): Unit = runBlocking {
        val validToken = "ya29.crash_resume_token"
        fakeCloud.acceptedTokens.add(validToken)
        GoogleDriveAuthManager.cacheEncryptedSessionToken(
            context = context,
            accessToken = validToken,
            expiresAtMs = fakeClockMs.get() + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS,
            accountEmail = "owner.solo@sewainaja.id"
        )

        val unitId = dao.insertUnit(makeUnit("FRZ-CRASH-1", merek = "Sharp", kapasitasLiter = 250))
        val unit = dao.getUnitById(unitId)!!
        val payload = MirrorOutboxCodec.serializeUnitJson(
            unit,
            generationManager.getCurrentDatasetId(),
            generationManager.getCurrentGenerationId(),
            fakeClockMs.get()
        )

        dao.upsertDriveLocalSyncQueueItem(
            DriveLocalSyncQueueEntity(
                localRecordKey = "UNIT:$unitId",
                recordCategory = GoogleDriveMirrorSyncEngine.CATEGORY_UNIT,
                recordId = unitId,
                operationType = GoogleDriveMirrorSyncEngine.OP_CREATE,
                payloadJson = payload,
                contentHash = MirrorOutboxCodec.computeSha256(payload),
                syncStatus = GoogleDriveMirrorSyncEngine.STATUS_UPLOADING,
                datasetGenerationId = generationManager.getCurrentGenerationId(),
                recordUuid = unit.uuid,
                operationId = UUID.randomUUID().toString()
            )
        )

        val restartedSyncRepo = SyncRepository(
            context = context,
            dao = dao,
            generationManager = generationManager,
            driveService = driveService
        ).apply { autoTriggerSyncOnMutation = false }

        val batch = restartedSyncRepo.processSyncQueue(ignoreBackoffTimers = true).getOrThrow()

        assertEquals(GoogleDriveMirrorSyncEngine.SYNC_STATUS_SUCCESS, batch.overallSyncStatus)
        assertEquals(0, batch.totalFailedInDb)
        assertEquals(0, batch.totalPendingInDb)
        assertEquals(0, dao.getAllDriveLocalSyncQueueOnce().size)
        val map = dao.getDriveCloudFileMapByKey("UNIT:$unitId")
        assertNotNull("Stranded UPLOADING row must finish syncing on app restart", map)
        assertNotNull(fakeCloud.storage[map!!.driveFileId])
    }

    @Test
    fun testD4_dashboardUiShowsWaitingAuthReLoginBannerAndNeverShowsFakeSuccess() {
        var reconnectClicked = false
        val state = DriveMirrorDashboardState(
            isDriveConnected = false,
            connectedEmail = "owner.solo@sewainaja.id",
            overallStatus = GoogleDriveMirrorSyncEngine.STATUS_FAILED_AUTH,
            syncStatusCode = GoogleDriveMirrorSyncEngine.SYNC_STATUS_WAITING_AUTH,
            requiresReAuth = true,
            pendingSyncCount = 0,
            failedSyncCount = 25,
            failedAuthCount = 1,
            datasetGeneration = "GENERATION_002",
            lastErrorMessage = "HTTP 401 UNAUTHENTICATED Invalid Credentials Expected OAuth 2 access token",
            manifestState = DriveCloudManifestState(
                datasetGeneration = "GENERATION_002",
                syncVersion = 158L,
                datasetStatus = "ACTIVE"
            )
        )

        composeTestRule.setContent {
            SEWAinAjaFreezerTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DriveMirrorSyncDashboardCard(
                        state = state,
                        onSyncNowClick = {},
                        onRetryFailedClick = {},
                        onResetLocalAndCloudConfirm = {},
                        onResetLocalOnlyConfirm = {},
                        onReconnectDriveClick = { reconnectClicked = true }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("badge_mirror_sync_status").assertIsDisplayed()
        composeTestRule.onNodeWithText("WAITING_AUTH • LOGIN ULANG").assertIsDisplayed()
        composeTestRule.onNodeWithTag("banner_drive_reauth_required").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_mirror_reconnect_drive").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTrue("Clicking re-login banner button must invoke onReconnectDriveClick", reconnectClicked)
    }
}
