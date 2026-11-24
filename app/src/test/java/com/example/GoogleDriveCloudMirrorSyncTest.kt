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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.DriveMirrorDashboardState
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
 * BAGIAN K. PENGUJIAN WAJIB (TESTS 1 - 9) + UI DASHBOARD & 3-OPTION RESET DIALOG
 * Memverifikasi seluruh skenario Real-Time Google Drive Cloud Mirror Synchronization:
 * - TEST 1: Tambah 5 pelanggan (Local = 5, Cloud = 5, 0 duplikasi)
 * - TEST 2: Edit 1 pelanggan (ubah nama & nomor WhatsApp) via PATCH files.update tanpa membuat file baru
 * - TEST 3: Ganti foto KTP pelanggan (hanya 1 foto aktif di local dan cloud)
 * - TEST 4: Hapus 1 pelanggan (Local = 4, Cloud = 4, foto & data terkait ikut terhapus)
 * - TEST 5: Matikan internet, tambah & edit data, lalu nyalakan internet kembali (FIFO queue = 0 tertinggal)
 * - TEST 6: Pilih RESET DATA LOKAL DAN CLOUD (Local kosong, Cloud kosong, data lama tidak muncul kembali)
 * - TEST 7: Tambah 1 pelanggan baru setelah reset (Hanya 1 pelanggan di local dan cloud)
 * - TEST 8: Tutup aplikasi dan buka kembali (Data konsisten, tidak ada resurreksi data lama)
 * - TEST 9: Lakukan sinkronisasi berulang kali (Idempoten, 0 duplikasi file)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDriveCloudMirrorSyncTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FreezerRepository
    private lateinit var mirrorEngine: GoogleDriveMirrorSyncEngine
    private lateinit var mockWebServer: MockWebServer
    private lateinit var statefulDriveBackend: StatefulFakeDriveBackend

    class StatefulFakeDriveBackend {
        data class CloudDriveObject(
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
            var patchUpdateCount: Int = 0
        )

        private val idSequence = AtomicInteger(1000)
        val storage = ConcurrentHashMap<String, CloudDriveObject>()
        val postUploadCount = AtomicInteger(0)
        val patchUpdateCount = AtomicInteger(0)
        val deleteCallCount = AtomicInteger(0)

        fun activeCustomerFiles(): List<CloudDriveObject> {
            val custFolderIds = storage.values.filter {
                !it.trashed && it.mimeType == "application/vnd.google-apps.folder" &&
                    it.name == GoogleDriveMirrorSyncEngine.SUBFOLDER_CUSTOMERS
            }.map { it.id }.toSet()
            return storage.values.filter {
                !it.trashed && it.mimeType != "application/vnd.google-apps.folder" &&
                    it.name.endsWith(".json") && it.name != "customers.json" &&
                    (it.parentId in custFolderIds || it.name.startsWith("CUSTOMER_"))
            }
        }

        fun activeKtpPhotoFilesForCustomer(customerId: Long): List<CloudDriveObject> =
            storage.values.filter {
                !it.trashed && it.name == "PHOTO_KTP_ID_${customerId}.jpg"
            }

        fun activeCustomerAndPhotoDataFiles(): List<CloudDriveObject> =
            storage.values.filter {
                !it.trashed &&
                    it.mimeType != "application/vnd.google-apps.folder" &&
                    it.name != GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME
            }

        fun activeManifestFile(): CloudDriveObject? =
            storage.values.firstOrNull {
                !it.trashed && it.name == GoogleDriveMirrorSyncEngine.CLOUD_MANIFEST_FILE_NAME
            }

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                val method = request.method ?: "GET"
                val contentType = request.getHeader("Content-Type").orEmpty()
                val rawBodyBytes = request.body.readByteArray()
                val bodyText = String(rawBodyBytes, Charsets.UTF_8)

                // 1. Multipart upload or in-place update: /upload/drive/v3/files
                if (path.startsWith("/upload/drive/v3/files")) {
                    val isPatch = method.equals("PATCH", ignoreCase = true)
                    val parsedParts = parseMultipartBody(contentType, bodyText, rawBodyBytes)
                    val metaJson = parsedParts.first
                    val payloadText = parsedParts.second
                    val payloadBytes = parsedParts.third

                    val name = metaJson.optString("name", "unnamed.dat")
                    val mimeType = metaJson.optString("mimeType", "application/octet-stream")
                    val parentsArr = metaJson.optJSONArray("parents")
                    val parentId = if (parentsArr != null && parentsArr.length() > 0) parentsArr.optString(0, "root") else "root"
                    val appProps = metaJson.optJSONObject("appProperties")
                    val checksum = appProps?.optString("checksum", appProps.optString("checksumSha256", "")).orEmpty()
                    val appIdProp = appProps?.optString("appId", "").orEmpty()
                    val applicationIdProp = appProps?.optString("applicationId", "").orEmpty()
                    val recUuid = appProps?.optString("record_uuid", appProps.optString("recordUuid", "")).orEmpty()
                    val dsGen = appProps?.optString("generation_id", appProps.optString("datasetGeneration", "")).orEmpty()

                    return if (isPatch) {
                        val fileId = path.substringAfter("/upload/drive/v3/files/")
                            .substringBefore("?")
                            .trim()
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            MockResponse().setResponseCode(404).setBody("""{"error":{"message":"File not found"}}""")
                        } else {
                            existing.name = name.ifBlank { existing.name }
                            existing.mimeType = mimeType.ifBlank { existing.mimeType }
                            existing.rawBody = payloadBytes
                            existing.jsonPayloadText = payloadText
                            if (checksum.isNotBlank()) existing.checksumSha256 = checksum
                            if (appIdProp.isNotBlank()) existing.appId = appIdProp
                            if (applicationIdProp.isNotBlank()) existing.applicationId = applicationIdProp
                            if (recUuid.isNotBlank()) existing.recordUuid = recUuid
                            if (dsGen.isNotBlank()) existing.datasetGeneration = dsGen
                            existing.patchUpdateCount += 1
                            patchUpdateCount.incrementAndGet()
                            MockResponse()
                                .setResponseCode(200)
                                .setBody(buildDriveFileJsonResponse(existing))
                        }
                    } else {
                        val newId = "drive_file_${idSequence.incrementAndGet()}"
                        val obj = CloudDriveObject(
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
                            datasetGeneration = dsGen
                        )
                        storage[newId] = obj
                        postUploadCount.incrementAndGet()
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(buildDriveFileJsonResponse(obj))
                    }
                }

                // 2. Metadata / folder endpoints: /drive/v3/files
                if (path.startsWith("/drive/v3/files")) {
                    val subPath = path.removePrefix("/drive/v3/files")
                    // Create folder via POST /drive/v3/files
                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("POST", ignoreCase = true)) {
                        val json = runCatching { JSONObject(bodyText) }.getOrNull() ?: JSONObject()
                        val folderName = json.optString("name", "Folder")
                        val mime = json.optString("mimeType", "application/vnd.google-apps.folder")
                        val parents = json.optJSONArray("parents")
                        val parentId = if (parents != null && parents.length() > 0) parents.optString(0, "root") else "root"
                        val newId = "drive_folder_${idSequence.incrementAndGet()}"
                        val folderObj = CloudDriveObject(
                            id = newId,
                            name = folderName,
                            mimeType = mime,
                            parentId = parentId,
                            rawBody = ByteArray(0),
                            jsonPayloadText = "",
                            checksumSha256 = ""
                        )
                        storage[newId] = folderObj
                        return MockResponse()
                            .setResponseCode(200)
                            .setBody(buildDriveFileJsonResponse(folderObj))
                    }

                    // Query files / folders via GET /drive/v3/files?q=...
                    if ((subPath.isEmpty() || subPath.startsWith("?")) && method.equals("GET", ignoreCase = true)) {
                        val decodedQuery = decodeQueryParam(path, "q")
                        val matches = filterByDriveQuery(decodedQuery)
                        val filesArray = JSONArray()
                        matches.forEach { item ->
                            filesArray.put(JSONObject(buildDriveFileJsonResponse(item)))
                        }
                        return MockResponse()
                            .setResponseCode(200)
                            .setBody(JSONObject().put("files", filesArray).toString())
                    }

                    // Operations on a specific file ID: /drive/v3/files/{fileId}
                    val fileId = subPath.removePrefix("/").substringBefore("?").trim()
                    if (method.equals("DELETE", ignoreCase = true)) {
                        storage.remove(fileId)
                        deleteCallCount.incrementAndGet()
                        return MockResponse().setResponseCode(204)
                    }
                    if (method.equals("GET", ignoreCase = true)) {
                        val existing = storage[fileId]
                        if (existing == null || existing.trashed) {
                            return MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}""")
                        }
                        return if (path.contains("alt=media")) {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody(existing.jsonPayloadText)
                        } else {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody(buildDriveFileJsonResponse(existing))
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
                .find(contentTypeHeader)
                ?.groupValues
                ?.get(1)
                ?.trim('"')
                ?: "===sewain_drive_boundary_"

            val delimiter = if (bodyText.contains("--$boundary")) "--$boundary" else "--===sewain_drive_boundary_"
            val rawParts = bodyText.split(delimiter)
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "--" }

            var metadataJson = JSONObject()
            var contentText = ""

            for ((idx, part) in rawParts.withIndex()) {
                val splitIdx = part.indexOf("\r\n\r\n").takeIf { it >= 0 } ?: part.indexOf("\n\n")
                val bodySection = if (splitIdx >= 0) {
                    val offset = if (part.substring(splitIdx).startsWith("\r\n\r\n")) 4 else 2
                    part.substring(splitIdx + offset).trimEnd('\r', '\n', '-')
                } else {
                    part
                }
                if (idx == 0) {
                    val jsonStart = bodySection.indexOf("{")
                    val jsonEnd = bodySection.lastIndexOf("}")
                    if (jsonStart >= 0 && jsonEnd > jsonStart) {
                        runCatching {
                            metadataJson = JSONObject(bodySection.substring(jsonStart, jsonEnd + 1))
                        }
                    }
                } else if (idx == 1) {
                    contentText = bodySection
                }
            }

            val payloadBytes = if (contentText.isNotEmpty()) {
                contentText.toByteArray(Charsets.UTF_8)
            } else {
                rawBytes.takeIf { it.isNotEmpty() } ?: ByteArray(128) { 1 }
            }
            return Triple(metadataJson, contentText, payloadBytes)
        }

        private fun filterByDriveQuery(q: String): List<CloudDriveObject> {
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
                val okFolder = !wantsFolderOnly || obj.mimeType == "application/vnd.google-apps.folder"
                val okNonFolder = !wantsNonFolderOnly || obj.mimeType != "application/vnd.google-apps.folder"
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
                if (kv.size == 2 && kv[0] == key) {
                    return URLDecoder.decode(kv[1], "UTF-8")
                }
            }
            return ""
        }

        private fun buildDriveFileJsonResponse(obj: CloudDriveObject): String {
            val sizeBytes = obj.rawBody.size.coerceAtLeast(64)
            return JSONObject().apply {
                put("id", obj.id)
                put("name", obj.name)
                put("size", sizeBytes.toString())
                put("parents", JSONArray(listOf(obj.parentId)))
                put("createdTime", "2026-11-16T10:00:00Z")
                put("modifiedTime", "2026-11-16T10:00:00Z")
                put("mimeType", obj.mimeType)
                put("description", "Mirror Sync Object")
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
            .edit()
            .clear()
            .commit()

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        statefulDriveBackend = StatefulFakeDriveBackend()
        mockWebServer = MockWebServer()
        mockWebServer.dispatcher = statefulDriveBackend.dispatcher
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
            accessTokenOverride = "mock_oauth_drive_token_v17"
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

    private fun buildTestCustomer(
        kodePelanggan: String,
        namaLengkap: String,
        nik: String,
        nomorHp: String,
        alamatLengkap: String,
        fotoKtpDepanUri: String = ""
    ): CustomerEntity {
        return CustomerEntity(
            kodePelanggan = kodePelanggan,
            namaLengkap = namaLengkap,
            nik = nik,
            jenisKelamin = "Laki-laki",
            nomorHp = nomorHp,
            alamatLengkap = alamatLengkap,
            tanggalRegistrasi = "2026-11-16",
            fotoKtpDepanUri = fotoKtpDepanUri
        )
    }

    private fun createTempPhotoFile(fileName: String, contentSeed: String): File {
        val dir = File(context.filesDir, "SEWAinAjaFreezer/test_photos").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(("JPEG_HEADER_$contentSeed").toByteArray(Charsets.UTF_8))
        return file
    }

    /**
     * TEST 1 — Tambah 5 pelanggan.
     * Verifikasi: Local = 5, Cloud = 5, tidak ada duplikasi.
     */
    @Test
    fun test1_addFiveCustomers_localAndCloudMatchFiveWithoutDuplicates() = runBlocking {
        for (i in 1..5) {
            repository.saveCustomer(
                buildTestCustomer(
                    kodePelanggan = "CUST-00$i",
                    namaLengkap = "Pelanggan Solo $i",
                    nik = "337201000000000$i",
                    nomorHp = "08123456780$i",
                    alamatLengkap = "Jl. Slamet Riyadi No. $i, Surakarta"
                )
            )
        }

        val syncRes = mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(5, syncRes.syncedCount)
        assertEquals(0, syncRes.failedCount)

        val localCustomers = database.freezerDao().getAllCustomersOnce()
        val cloudCustomers = statefulDriveBackend.activeCustomerFiles()

        assertEquals(5, localCustomers.size)
        assertEquals(5, cloudCustomers.size)
        assertEquals(5, cloudCustomers.map { it.name }.distinct().size)
    }

    /**
     * TEST 2 — Edit 1 pelanggan (ubah nama dan nomor WhatsApp).
     * Verifikasi: Cloud berubah sesuai data baru, menggunakan `files.update` (PATCH), dan tidak membuat file baru.
     */
    @Test
    fun test2_editCustomerNameAndWhatsapp_cloudFileUpdatedInPlaceViaPatchWithoutNewDuplicateFile() = runBlocking {
        val id = repository.saveCustomer(
            buildTestCustomer(
                kodePelanggan = "CUST-EDIT-01",
                namaLengkap = "Budi Santoso Lama",
                nik = "3372019999990001",
                nomorHp = "081111111111",
                alamatLengkap = "Laweyan, Solo"
            )
        )
        mirrorEngine.processSyncQueue().getOrThrow()

        val initialCloudFile = statefulDriveBackend.activeCustomerFiles().single()
        val initialFileId = initialCloudFile.id
        val postUploadsBeforeEdit = statefulDriveBackend.postUploadCount.get()

        // Edit customer name & WhatsApp number
        val savedCustomer = database.freezerDao().getCustomerById(id)!!
        repository.saveCustomer(
            savedCustomer.copy(
                namaLengkap = "Budi Santoso Baru",
                nomorHp = "089999999999"
            )
        )
        mirrorEngine.processSyncQueue().getOrThrow()

        val cloudCustomerFilesAfterEdit = statefulDriveBackend.activeCustomerFiles()
        assertEquals(1, cloudCustomerFilesAfterEdit.size)
        val updatedCloudFile = cloudCustomerFilesAfterEdit.single()
        assertEquals(initialFileId, updatedCloudFile.id)
        assertTrue("Expected PATCH files.update to be invoked on the existing file", updatedCloudFile.patchUpdateCount >= 1)

        // Verify the JSON content in Google Drive reflects the updated name & WhatsApp number
        val updatedJson = JSONObject(updatedCloudFile.jsonPayloadText)
        assertEquals("Budi Santoso Baru", updatedJson.getString("namaLengkap"))
        assertEquals("089999999999", updatedJson.getString("nomorWhatsapp"))
        assertEquals("089999999999", updatedJson.getString("nomorHp"))

        // Verify no new customer file was POST-uploaded
        assertEquals(postUploadsBeforeEdit, statefulDriveBackend.postUploadCount.get())
    }

    /**
     * TEST 3 — Ganti foto KTP pelanggan.
     * Verifikasi: Foto baru tersimpan, hanya ada 1 foto aktif di local dan cloud.
     */
    @Test
    fun test3_replaceCustomerKtpPhoto_onlyOneActivePhotoInLocalAndCloud() = runBlocking {
        val ktpPhotoOld = createTempPhotoFile("ktp_lama.jpg", "OLD_KTP_IMAGE_CONTENT_V1")
        val ktpPhotoNew = createTempPhotoFile("ktp_baru.jpg", "NEW_KTP_IMAGE_CONTENT_V2")

        val customerId = repository.saveCustomer(
            buildTestCustomer(
                kodePelanggan = "CUST-KTP-01",
                namaLengkap = "Siti Aminah",
                nik = "3372028888880001",
                nomorHp = "081222333444",
                alamatLengkap = "Banjarsari, Solo",
                fotoKtpDepanUri = ktpPhotoOld.absolutePath
            )
        )
        mirrorEngine.processSyncQueue().getOrThrow()

        assertEquals(1, statefulDriveBackend.activeKtpPhotoFilesForCustomer(customerId).size)
        val initialPhotoDriveId = statefulDriveBackend.activeKtpPhotoFilesForCustomer(customerId).single().id

        // Replace KTP photo with new file
        val existingCustomer = database.freezerDao().getCustomerById(customerId)!!
        repository.saveCustomer(
            existingCustomer.copy(
                fotoKtpDepanUri = ktpPhotoNew.absolutePath
            )
        )
        mirrorEngine.processSyncQueue().getOrThrow()

        // Verify only 1 active KTP photo exists in Cloud and it was updated in-place
        val cloudKtpFiles = statefulDriveBackend.activeKtpPhotoFilesForCustomer(customerId)
        assertEquals(1, cloudKtpFiles.size)
        assertEquals(initialPhotoDriveId, cloudKtpFiles.single().id)
        assertEquals(mirrorEngine.computeFileSha256(ktpPhotoNew), cloudKtpFiles.single().checksumSha256)

        // Verify old local file was cleaned up and new local file exists
        assertFalse("Old replaced KTP photo file must be deleted from local storage", ktpPhotoOld.exists())
        assertTrue("New active KTP photo file must exist in local storage", ktpPhotoNew.exists())

        // Verify only 1 DrivePhotoMetadataEntity row for KTP exists for this customer
        val ktpMetaRows = database.freezerDao().getDrivePhotoMetadataByCustomerOnce(customerId)
            .filter { it.categoryCode == "KTP" }
        assertEquals(1, ktpMetaRows.size)
    }

    /**
     * TEST 4 — Hapus 1 pelanggan.
     * Verifikasi: Local = 4, Cloud = 4, foto dan data terkait ikut terhapus.
     */
    @Test
    fun test4_deleteOneCustomer_cloudDeletesCustomerAndRelatedPhotos_fourRemain() = runBlocking {
        val ktpFileForCust1 = createTempPhotoFile("ktp_cust_1.jpg", "CUST_1_KTP_BYTES")
        val customerIds = mutableListOf<Long>()

        for (i in 1..5) {
            val id = repository.saveCustomer(
                buildTestCustomer(
                    kodePelanggan = "CUST-DEL-0$i",
                    namaLengkap = "Pelanggan Uji Hapus $i",
                    nik = "337203777777000$i",
                    nomorHp = "08133344455$i",
                    alamatLengkap = "Jebres, Solo $i",
                    fotoKtpDepanUri = if (i == 1) ktpFileForCust1.absolutePath else ""
                )
            )
            customerIds.add(id)
        }
        mirrorEngine.processSyncQueue().getOrThrow()

        assertEquals(5, database.freezerDao().getAllCustomersOnce().size)
        assertEquals(5, statefulDriveBackend.activeCustomerFiles().size)
        assertEquals(1, statefulDriveBackend.activeKtpPhotoFilesForCustomer(customerIds[0]).size)

        // Delete customer #1
        val customerToDelete = database.freezerDao().getCustomerById(customerIds[0])!!
        repository.deleteCustomer(customerToDelete)
        mirrorEngine.processSyncQueue().getOrThrow()

        // Verify Local = 4, Cloud = 4, and customer #1's KTP photo in Cloud is also deleted
        assertEquals(4, database.freezerDao().getAllCustomersOnce().size)
        assertEquals(4, statefulDriveBackend.activeCustomerFiles().size)
        assertEquals(0, statefulDriveBackend.activeKtpPhotoFilesForCustomer(customerIds[0]).size)

        // Verify tombstone is recorded and marked synced
        val tombstone = database.freezerDao().getDriveDeletionTombstoneByKey("CUSTOMER:${customerIds[0]}")
        assertNotNull(tombstone)
        assertTrue(tombstone!!.syncedToCloud)
    }

    /**
     * TEST 5 — Matikan internet, tambah dan edit data, lalu nyalakan internet kembali.
     * Verifikasi: Semua perubahan tersinkron otomatis secara FIFO, tidak ada operasi yang tertinggal.
     */
    @Test
    fun test5_offlineAddAndEditThenReconnect_fifoQueueSyncsAllPendingToZero() = runBlocking {
        // Turn internet OFF
        mirrorEngine.setSimulatedOfflineMode(true)

        val c1Id = repository.saveCustomer(
            buildTestCustomer(
                kodePelanggan = "CUST-OFF-1",
                namaLengkap = "Andi Offline Awal",
                nik = "3372046666660001",
                nomorHp = "081555666771",
                alamatLengkap = "Pasar Kliwon, Solo"
            )
        )
        val c2Id = repository.saveCustomer(
            buildTestCustomer(
                kodePelanggan = "CUST-OFF-2",
                namaLengkap = "Rina Offline",
                nik = "3372046666660002",
                nomorHp = "081555666772",
                alamatLengkap = "Serengan, Solo"
            )
        )

        // Edit customer 1 while still offline
        val c1Saved = database.freezerDao().getCustomerById(c1Id)!!
        repository.saveCustomer(
            c1Saved.copy(
                namaLengkap = "Andi Offline Diedit",
                nomorHp = "081555999999"
            )
        )

        // Attempt sync while offline -> handles offline gracefully and keeps items as PENDING_SYNC
        val offlineAttempt = mirrorEngine.processSyncQueue()
        assertTrue(
            "Expected offline attempt to report failure or Offline status with pending items",
            offlineAttempt.isFailure || (offlineAttempt.getOrNull()?.errorMessage == "Offline" && (offlineAttempt.getOrNull()?.remainingPendingCount ?: 0) > 0)
        )
        val pendingWhileOffline = database.freezerDao().getRunnableDriveSyncQueueItemsOnce()
        assertTrue("Expected pending items queued while offline", pendingWhileOffline.isNotEmpty())
        assertEquals(0, statefulDriveBackend.activeCustomerFiles().size)

        // Reconnect internet -> process FIFO queue
        mirrorEngine.setSimulatedOfflineMode(false)
        val onlineSyncResult = mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(0, onlineSyncResult.failedCount)
        assertEquals(0, onlineSyncResult.remainingPendingCount)

        // Verify 0 pending items remain in queue
        val remainingQueue = database.freezerDao().getRunnableDriveSyncQueueItemsOnce()
        assertEquals(0, remainingQueue.size)

        // Verify both customers are in Google Drive and customer 1 has the edited name & WhatsApp
        val cloudCustomers = statefulDriveBackend.activeCustomerFiles()
        assertEquals(2, cloudCustomers.size)
        val c1Uuid = database.freezerDao().getCustomerById(c1Id)!!.uuid
        val c1Cloud = cloudCustomers.first { it.name == "${c1Uuid}.json" || it.recordUuid == c1Uuid }
        val c1Json = JSONObject(c1Cloud.jsonPayloadText)
        assertEquals("Andi Offline Diedit", c1Json.getString("namaLengkap"))
        assertEquals("081555999999", c1Json.getString("nomorWhatsapp"))
        assertTrue(c2Id > 0L)
    }

    /**
     * TEST 6 — Pilih RESET DATA LOKAL DAN CLOUD.
     * Verifikasi: Local kosong, Cloud kosong, data lama tidak muncul kembali.
     */
    @Test
    fun test6_resetLocalAndCloud_allLocalAndCloudDataClearedAndNoOldDataResurrection() = runBlocking {
        val oldIds = mutableListOf<Long>()
        for (i in 1..3) {
            val id = repository.saveCustomer(
                buildTestCustomer(
                    kodePelanggan = "OLD-00$i",
                    namaLengkap = "Pelanggan Lama $i",
                    nik = "337205111111000$i",
                    nomorHp = "08166677788$i",
                    alamatLengkap = "Solo Lama $i"
                )
            )
            oldIds.add(id)
        }
        val oldDatasetId = mirrorEngine.getCurrentDatasetId()
        val preResetTimestamp = System.currentTimeMillis()
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(3, statefulDriveBackend.activeCustomerFiles().size)

        Thread.sleep(10)
        val resetResult = mirrorEngine.executeResetLocalAndCloud().getOrThrow()
        assertEquals("LOCAL_AND_CLOUD", resetResult.resetMode)

        // Verify Local is completely empty
        assertEquals(0, database.freezerDao().getAllCustomersOnce().size)
        assertEquals(0, database.freezerDao().getAllUnitsOnce().size)
        assertEquals(0, database.freezerDao().getAllTransactionsOnce().size)
        assertEquals(0, database.freezerDao().getRunnableDriveSyncQueueItemsOnce().size)

        // Verify Cloud has 0 customer/unit/transaction/photo files, and a fresh empty cloud_manifest.json
        assertEquals(0, statefulDriveBackend.activeCustomerAndPhotoDataFiles().size)
        val emptyManifest = statefulDriveBackend.activeManifestFile()
        assertNotNull("Fresh empty cloud_manifest.json must be created after reset", emptyManifest)
        val emptyManifestJson = JSONObject(emptyManifest!!.jsonPayloadText)
        assertEquals("EMPTY_AFTER_RESET", emptyManifestJson.getString("datasetStatus"))
        assertEquals(0, emptyManifestJson.getInt("totalCustomers"))

        // Verify anti-resurrection guard rejects any pre-reset data
        val canResurrectOldData = mirrorEngine.canApplyRemoteRecordWithoutResurrection(
            localRecordKey = "CUSTOMER:${oldIds[0]}",
            remoteDatasetId = oldDatasetId,
            remoteTimestampMillis = preResetTimestamp,
            remoteVersion = 1L
        )
        assertFalse("Pre-reset data must never be allowed to resurrect", canResurrectOldData)
    }

    /**
     * TEST 7 — Tambah 1 pelanggan baru setelah reset.
     * Verifikasi: Hanya ada 1 pelanggan di local dan cloud.
     */
    @Test
    fun test7_addNewCustomerAfterReset_onlyOneCustomerInLocalAndCloud() = runBlocking {
        for (i in 1..4) {
            repository.saveCustomer(
                buildTestCustomer(
                    kodePelanggan = "PRE-RESET-0$i",
                    namaLengkap = "Pelanggan Pra Reset $i",
                    nik = "337205222222000$i",
                    nomorHp = "08166600000$i",
                    alamatLengkap = "Solo Pra Reset $i"
                )
            )
        }
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(4, statefulDriveBackend.activeCustomerFiles().size)

        // Reset Local & Cloud
        mirrorEngine.executeResetLocalAndCloud().getOrThrow()
        assertEquals(0, database.freezerDao().getAllCustomersOnce().size)
        assertEquals(0, statefulDriveBackend.activeCustomerFiles().size)

        // Add 1 new customer after reset
        repository.saveCustomer(
            buildTestCustomer(
                kodePelanggan = "NEW-AFTER-RESET-01",
                namaLengkap = "Pelanggan Baru Pasca Reset",
                nik = "3372059999990001",
                nomorHp = "081777888999",
                alamatLengkap = "Jl. Adi Sucipto No. 1, Solo"
            )
        )
        mirrorEngine.processSyncQueue().getOrThrow()

        assertEquals(1, database.freezerDao().getAllCustomersOnce().size)
        assertEquals(1, statefulDriveBackend.activeCustomerFiles().size)
        val activeManifestAfterAdd = JSONObject(statefulDriveBackend.activeManifestFile()!!.jsonPayloadText)
        assertEquals("ACTIVE", activeManifestAfterAdd.getString("datasetStatus"))
        assertEquals(1, activeManifestAfterAdd.getInt("totalCustomers"))
    }

    /**
     * TEST 8 — Tutup aplikasi dan buka kembali.
     * Verifikasi: Data tetap konsisten, data lama yang sudah dihapus/direset tidak muncul kembali.
     */
    @Test
    fun test8_appRestartConsistency_sqliteAndCloudRemainConsistentWithoutResurrection() = runBlocking {
        val c1Id = repository.saveCustomer(
            buildTestCustomer(
                kodePelanggan = "RESTART-OLD-01",
                namaLengkap = "Pelanggan Dihapus Sebelum Restart",
                nik = "3372053333330001",
                nomorHp = "081444555661",
                alamatLengkap = "Solo"
            )
        )
        mirrorEngine.processSyncQueue().getOrThrow()

        // Reset Local & Cloud, then add 1 active customer
        Thread.sleep(10)
        mirrorEngine.executeResetLocalAndCloud().getOrThrow()
        val activeId = repository.saveCustomer(
            buildTestCustomer(
                kodePelanggan = "RESTART-ACTIVE-01",
                namaLengkap = "Pelanggan Aktif Saat Restart",
                nik = "3372053333330002",
                nomorHp = "081444555662",
                alamatLengkap = "Solo Baru"
            )
        )
        mirrorEngine.processSyncQueue().getOrThrow()

        // Simulate closing and reopening the app with a brand-new engine instance
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val restartedDriveClient = GoogleDriveRestClient(
            apiBaseUrl = "$baseUrl/drive/v3",
            uploadBaseUrl = "$baseUrl/upload/drive/v3"
        )
        val restartedEngine = GoogleDriveMirrorSyncEngine(
            context = context,
            dao = database.freezerDao(),
            driveClient = restartedDriveClient,
            accessTokenOverride = "mock_oauth_drive_token_v17"
        ).apply {
            autoTriggerSyncOnMutation = false
            simulatedOfflineOverride = false
        }

        val startupState = restartedEngine.verifyConsistencyOnAppStartup().getOrThrow()
        assertEquals(GoogleDriveMirrorSyncEngine.STATUS_SYNCED, startupState.overallStatus)
        assertEquals(1, database.freezerDao().getAllCustomersOnce().size)
        assertEquals(activeId, database.freezerDao().getAllCustomersOnce().single().id)
        assertEquals(1, statefulDriveBackend.activeCustomerFiles().size)
        assertTrue(c1Id != activeId || database.freezerDao().getAllCustomersOnce().single().namaLengkap == "Pelanggan Aktif Saat Restart")
    }

    /**
     * TEST 9 — Lakukan sinkronisasi berulang kali.
     * Verifikasi: Tidak ada duplikasi file.
     */
    @Test
    fun test9_repeatedSyncIdempotency_noDuplicateFilesCreated() = runBlocking {
        val id1 = repository.saveCustomer(
            buildTestCustomer(
                kodePelanggan = "CUST-IDEM-1",
                namaLengkap = "Dewi Lestari",
                nik = "3372061234560001",
                nomorHp = "081888999001",
                alamatLengkap = "Kartasura, Sukoharjo"
            )
        )
        val id2 = repository.saveCustomer(
            buildTestCustomer(
                kodePelanggan = "CUST-IDEM-2",
                namaLengkap = "Hendra Wijaya",
                nik = "3372061234560002",
                nomorHp = "081888999002",
                alamatLengkap = "Colomadu, Karanganyar"
            )
        )

        // First sync
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(2, statefulDriveBackend.activeCustomerFiles().size)
        val postCountAfterFirstSync = statefulDriveBackend.postUploadCount.get()

        // Re-save identical entities and run processSyncQueue 5 times in a row
        val c1 = database.freezerDao().getCustomerById(id1)!!
        val c2 = database.freezerDao().getCustomerById(id2)!!
        repeat(5) {
            repository.saveCustomer(c1)
            repository.saveCustomer(c2)
            mirrorEngine.processSyncQueue().getOrThrow()
        }

        // Verify still strictly 2 customer files in Google Drive and 0 new POST uploads
        assertEquals(2, statefulDriveBackend.activeCustomerFiles().size)
        assertEquals(postCountAfterFirstSync, statefulDriveBackend.postUploadCount.get())
    }

    /**
     * TEST 10 — Verifikasi UI Dashboard Sinkronisasi (Bagian J) & Dialog 3 Opsi Reset (Bagian E).
     */
    @Test
    fun test10_dashboardUI_rendersSyncMetricsAndThreeResetOptionsWithSecondConfirmation() {
        var syncNowClicked = false
        var localAndCloudResetConfirmedWithBackup: Boolean? = null
        var localOnlyResetConfirmed = false

        composeRule.setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    DriveMirrorSyncDashboardCard(
                        state = DriveMirrorDashboardState(
                            isDriveConnected = true,
                            connectedEmail = "owner@sewainajafreezer.com",
                            overallStatus = "SYNCED",
                            totalSyncedDataCount = 12,
                            totalSyncedPhotosCount = 8,
                            pendingSyncCount = 0,
                            failedSyncCount = 0,
                            lastSyncTimeDisplay = "16 Nov 2026, 10:30:00"
                        ),
                        onSyncNowClick = { syncNowClicked = true },
                        onRetryFailedClick = {},
                        onResetLocalAndCloudConfirm = { createBackup ->
                            localAndCloudResetConfirmedWithBackup = createBackup
                        },
                        onResetLocalOnlyConfirm = {
                            localOnlyResetConfirmed = true
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("card_drive_mirror_sync_dashboard").assertExists()
        composeRule.onNodeWithTag("btn_mirror_sync_now").performScrollTo().performClick()
        assertTrue(syncNowClicked)

        // Open Reset Data Aplikasi dialog (Bagian E: 3 Pilihan)
        composeRule.onNodeWithTag("btn_open_reset_data_modal").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_option_reset_local_and_cloud").assertExists()
        composeRule.onNodeWithTag("btn_option_reset_local_only").assertExists()
        composeRule.onNodeWithTag("btn_option_reset_cancel").assertExists()

        // Choose Opsi 1: RESET DATA LOKAL DAN CLOUD -> opens 2nd confirmation dialog
        composeRule.onNodeWithTag("btn_option_reset_local_and_cloud").performClick()
        composeRule.onNodeWithTag("btn_confirm_reset_local_and_cloud_step2").assertExists().performClick()

        assertEquals(false, localAndCloudResetConfirmedWithBackup)
        assertFalse(localOnlyResetConfirmed)
    }
}
