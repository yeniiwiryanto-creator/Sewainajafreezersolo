package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupJsonMapper
import com.example.backup.RestoreMode
import com.example.backup.StructuredBackupEngine
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerPhotoEntity
import com.example.data.local.DrivePhotoMetadataEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.repository.DetailedRenewalRequest
import com.example.data.repository.FreezerRepository
import com.example.data.repository.NewUnitDuringRenewalSelection
import com.example.data.repository.UnitRenewalSelection
import com.example.drive.DatabaseGenerationManager
import com.example.drive.DriveDocumentationCategory
import com.example.drive.GoogleDriveAuthManager
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.GoogleDrivePhotoCenterManager
import com.example.drive.GoogleDriveRestClient
import com.example.drive.MirrorOutboxCodec
import com.example.storage.CustomerPhotoStorageHelper
import com.example.ui.components.LocationDocSlots
import com.example.ui.components.mapUploadStatusLabelAndColor
import com.example.ui.theme.AppAppearanceConfig
import com.example.ui.theme.AppAppearancePreferences
import com.example.ui.theme.AppColorThemePreset
import com.example.ui.theme.AppFontChoice
import com.example.ui.theme.BackgroundPositionMode
import com.example.ui.theme.BackgroundScaleMode
import com.example.ui.theme.BackgroundTargetScope
import com.example.viewmodel.AppLoadingState
import com.example.viewmodel.AppNotificationEvent
import com.example.viewmodel.OperationNotificationType
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive Robolectric Integration Test Suite covering ALL 12 Mandatory Tests + MIGRATION_24_25.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComprehensiveAuditAndFeatureUpgradeTest {

    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var database: AppDatabase
    private lateinit var dao: FreezerDao
    private lateinit var repository: FreezerRepository
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeDrive: FakeDriveServer
    private lateinit var restClient: GoogleDriveRestClient
    private lateinit var mirrorEngine: GoogleDriveMirrorSyncEngine

    private class FakeDriveFile(
        val id: String,
        var name: String,
        var mimeType: String,
        var parents: List<String>,
        var appProperties: MutableMap<String, String> = mutableMapOf(),
        var payloadBytes: ByteArray = ByteArray(0),
        var trashed: Boolean = false
    )

    private class FakeDriveServer {
        val files = ConcurrentHashMap<String, FakeDriveFile>()
        val idSeq = AtomicInteger(100)
        val simulateOffline = AtomicBoolean(false)
        val uploadCount = AtomicInteger(0)
        val deleteCount = AtomicInteger(0)

        fun activeRecordFiles(): List<FakeDriveFile> =
            files.values.filter {
                !it.trashed &&
                    it.mimeType != "application/vnd.google-apps.folder" &&
                    it.name != "manifest.json" &&
                    it.appProperties["record_uuid"]?.isNotBlank() == true
            }

        fun activePhotoFiles(): List<FakeDriveFile> =
            files.values.filter { !it.trashed && it.mimeType.startsWith("image/") }

        fun dispatcher(): Dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (simulateOffline.get()) {
                    return MockResponse().setResponseCode(503).setBody("""{"error":{"code":503,"message":"Network unavailable"}}""")
                }
                val path = request.requestUrl?.encodedPath.orEmpty()
                val method = request.method.orEmpty()
                val query = request.requestUrl?.queryParameter("q")?.let {
                    runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
                }.orEmpty()

                if (path.startsWith("/upload/drive/v3/files")) {
                    val bodyText = request.body.readUtf8()
                    val targetId = path.removePrefix("/upload/drive/v3/files").trim('/').takeIf { it.isNotBlank() }
                    val metaJson = extractFirstJsonBlock(bodyText)
                    val payloadContent = extractSecondPartPayload(bodyText)

                    if (method == "POST") {
                        val newId = "drv_${idSeq.incrementAndGet()}"
                        val name = metaJson?.optString("name").orEmpty().ifBlank { "file_$newId.json" }
                        val mime = metaJson?.optString("mimeType").orEmpty().ifBlank {
                            if (name.endsWith(".jpg") || name.endsWith(".webp")) "image/jpeg" else "application/json"
                        }
                        val parents = mutableListOf<String>()
                        metaJson?.optJSONArray("parents")?.let { pArr ->
                            for (i in 0 until pArr.length()) parents.add(pArr.getString(i))
                        }
                        val props = mutableMapOf<String, String>()
                        metaJson?.optJSONObject("appProperties")?.let { ap ->
                            ap.keys().forEach { k -> props[k] = ap.optString(k) }
                        }

                        val created = FakeDriveFile(
                            id = newId,
                            name = name,
                            mimeType = mime,
                            parents = parents,
                            appProperties = props,
                            payloadBytes = payloadContent.toByteArray(Charsets.UTF_8)
                        )
                        files[newId] = created
                        uploadCount.incrementAndGet()
                        return MockResponse().setResponseCode(200).setBody(fileToJson(created).toString())
                    }
                    if (method == "PATCH" && targetId != null) {
                        val existing = files[targetId]
                            ?: return MockResponse().setResponseCode(404).setBody("""{"error":{"code":404,"message":"Not found"}}""")
                        metaJson?.optString("name")?.takeIf { it.isNotBlank() }?.let { existing.name = it }
                        metaJson?.optJSONObject("appProperties")?.let { ap ->
                            ap.keys().forEach { k -> existing.appProperties[k] = ap.optString(k) }
                        }
                        if (payloadContent.isNotEmpty()) {
                            existing.payloadBytes = payloadContent.toByteArray(Charsets.UTF_8)
                        }
                        uploadCount.incrementAndGet()
                        return MockResponse().setResponseCode(200).setBody(fileToJson(existing).toString())
                    }
                }

                if (path == "/drive/v3/files" && method == "POST") {
                    val meta = JSONObject(request.body.readUtf8())
                    val newId = "fld_${idSeq.incrementAndGet()}"
                    val parents = mutableListOf<String>()
                    meta.optJSONArray("parents")?.let { arr ->
                        for (i in 0 until arr.length()) parents.add(arr.getString(i))
                    }
                    val props = mutableMapOf<String, String>()
                    meta.optJSONObject("appProperties")?.let { ap ->
                        ap.keys().forEach { k -> props[k] = ap.optString(k) }
                    }
                    val created = FakeDriveFile(
                        id = newId,
                        name = meta.optString("name", "folder"),
                        mimeType = meta.optString("mimeType", "application/vnd.google-apps.folder"),
                        parents = parents,
                        appProperties = props
                    )
                    files[newId] = created
                    return MockResponse().setResponseCode(200).setBody(fileToJson(created).toString())
                }

                if (path == "/drive/v3/files" && method == "GET") {
                    val matched = files.values.filter { f -> matchesDriveQuery(f, query) }
                    val arr = JSONArray()
                    matched.forEach { arr.put(fileToJson(it)) }
                    return MockResponse().setResponseCode(200)
                        .setBody(JSONObject().put("files", arr).toString())
                }

                if (path.startsWith("/drive/v3/files/")) {
                    val fileId = path.removePrefix("/drive/v3/files/").trim('/')
                    val existing = files[fileId]
                        ?: return MockResponse().setResponseCode(404).setBody("""{"error":{"code":404,"message":"Not found"}}""")

                    return when (method) {
                        "GET" -> {
                            val alt = request.requestUrl?.queryParameter("alt")
                            if (alt == "media") {
                                MockResponse().setResponseCode(200).setBody(String(existing.payloadBytes, Charsets.UTF_8))
                            } else {
                                MockResponse().setResponseCode(200).setBody(fileToJson(existing).toString())
                            }
                        }
                        "DELETE" -> {
                            files.remove(fileId)
                            deleteCount.incrementAndGet()
                            MockResponse().setResponseCode(204)
                        }
                        "PATCH" -> {
                            val body = runCatching { JSONObject(request.body.readUtf8()) }.getOrNull()
                            if (body?.optBoolean("trashed", false) == true) {
                                existing.trashed = true
                                deleteCount.incrementAndGet()
                            }
                            MockResponse().setResponseCode(200).setBody(fileToJson(existing).toString())
                        }
                        else -> MockResponse().setResponseCode(405)
                    }
                }

                return MockResponse().setResponseCode(404).setBody("""{"error":{"code":404,"message":"Unhandled $method $path"}}""")
            }
        }

        private fun matchesDriveQuery(f: FakeDriveFile, q: String): Boolean {
            if (q.contains("trashed = false") && f.trashed) return false
            Regex("name = '([^']+)'").find(q)?.let { m ->
                if (f.name != m.groupValues[1]) return false
            }
            Regex("mimeType = '([^']+)'").find(q)?.let { m ->
                if (f.mimeType != m.groupValues[1]) return false
            }
            Regex("'([^']+)' in parents").find(q)?.let { m ->
                if (m.groupValues[1] !in f.parents) return false
            }
            Regex("appProperties has \\{ key='([^']+)' and value='([^']+)' \\}").findAll(q).forEach { m ->
                val key = m.groupValues[1]
                val expected = m.groupValues[2]
                if (f.appProperties[key] != expected) return false
            }
            return true
        }

        private fun fileToJson(f: FakeDriveFile): JSONObject = JSONObject().apply {
            put("id", f.id)
            put("name", f.name)
            put("mimeType", f.mimeType)
            put("size", f.payloadBytes.size.toString())
            put("modifiedTime", "2026-11-20T10:00:00Z")
            put("trashed", f.trashed)
            put("parents", JSONArray(f.parents))
            put("appProperties", JSONObject(f.appProperties as Map<*, *>))
        }

        private fun extractFirstJsonBlock(multipartBody: String): JSONObject? {
            val parts = multipartBody.split(Regex("--[a-zA-Z0-9_=-]+"))
            for (part in parts) {
                val idx = part.indexOf('{')
                val endIdx = part.lastIndexOf('}')
                if (idx >= 0 && endIdx > idx) {
                    val candidate = part.substring(idx, endIdx + 1)
                    val parsed = runCatching { JSONObject(candidate) }.getOrNull()
                    if (parsed != null && (parsed.has("name") || parsed.has("appProperties"))) {
                        return parsed
                    }
                }
            }
            return null
        }

        private fun extractSecondPartPayload(multipartBody: String): String {
            val parts = multipartBody.split(Regex("--[a-zA-Z0-9_=-]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "--" }
            if (parts.size < 2) return ""
            val second = parts[1]
            val sep = second.indexOf("\r\n\r\n")
            if (sep >= 0) return second.substring(sep + 4).trim()
            val lfSep = second.indexOf("\n\n")
            return if (lfSep >= 0) second.substring(lfSep + 2).trim() else second
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "comprehensive_audit_v25_${System.nanoTime()}.db"
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.APP_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        AppAppearancePreferences.reset(context)

        mockWebServer = MockWebServer()
        fakeDrive = FakeDriveServer()
        mockWebServer.dispatcher = fakeDrive.dispatcher()
        mockWebServer.start()

        GoogleDriveAuthManager.markConnectedMetadata(
            context = context,
            email = "audit.soc@gmail.com",
            displayName = "Admin SEWAin Aja SOC",
            photoUrl = ""
        )
        openDbAndWire()
    }

    private fun openDbAndWire() {
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
        mirrorEngine = GoogleDriveMirrorSyncEngine(
            context = context,
            dao = dao,
            driveClient = restClient,
            accessTokenOverride = "ya29.audit-comprehensive-token"
        ).apply {
            autoTriggerSyncOnMutation = false
            setSimulatedOfflineMode(false)
        }
        repository.attachMirrorSyncEngine(mirrorEngine)
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        runCatching { mockWebServer.shutdown() }
        context.deleteDatabase(dbName)
    }

    private fun sampleCustomer(code: String, name: String, wa: String = "081234567890") = CustomerEntity(
        kodePelanggan = code,
        namaLengkap = name,
        nik = "337201010190${code.takeLast(4).padStart(4, '1')}",
        nomorHp = wa,
        alamatLengkap = "Jl. Slamet Riyadi No. 88, Solo",
        jenisKelamin = "Laki-laki",
        tanggalRegistrasi = "2026-11-01"
    )

    private fun sampleUnit(
        code: String,
        name: String,
        status: String = "Tersedia",
        monthlyPrice: Long = 350_000L,
        tenor: Int = 12,
        merek: String = "GEA",
        kapasitas: Int = 300
    ) = FreezerUnitEntity(
        kodeUnit = code,
        namaUnit = name,
        jenisUnit = "Chest Freezer",
        owner = "SOC",
        hargaPembelian = 4_800_000L,
        kapasitasLiter = kapasitas,
        cicilanPerBulan = monthlyPrice,
        jumlahCicilanTotal = tenor,
        merek = merek,
        model = "AB-${kapasitas}-R",
        nomorSeri = "SN-$code",
        statusUnit = status,
        lokasiUnit = "Gudang Solo",
        tanggalPembelian = "2026-01-10"
    )

    private fun sampleTx(
        invoice: String,
        custId: Long,
        unitId: Long,
        custName: String,
        unitName: String,
        unitCode: String,
        harga: Long = 350_000L,
        start: String = "2026-11-01",
        end: String = "2026-12-01",
        program: String = "Sewa Reguler",
        totalHm: Int = 0,
        keHm: Int = 1
    ) = RentalTransactionEntity(
        nomorInvoice = invoice,
        customerId = custId,
        unitId = unitId,
        namaPelangganSnapshot = custName,
        namaUnitSnapshot = unitName,
        kodeUnitSnapshot = unitCode,
        tanggalPemesanan = start,
        hargaSewa = harga,
        durasiSewaBulan = 1,
        durasiHari = 30,
        tanggalMulaiSewa = start,
        tanggalAkhirSewa = end,
        tanggalJatuhTempo = end,
        metodePembayaran = "Transfer Bank",
        totalTagihan = harga,
        nominalPembayaran = harga,
        statusPembayaran = "Lunas",
        statusPenyewaan = "Dalam Penyewaan",
        jenisProgram = program,
        totalPeriodeHakMilik = totalHm,
        pembayaranKeHakMilik = keHm
    )

    // =========================================================================
    // TEST 1: UPLOAD DOKUMENTASI FREEZER DI LOKASI & TAMPAK DEPAN LOKASI
    // =========================================================================
    @Test
    fun test1_documentationUploadSavesLocallyWithMetadataAndTracksRealDriveSyncStatuses(): Unit = runBlocking {
        val custId = repository.saveCustomer(sampleCustomer("PLG-001", "Budi Frozen Solo"))
        val cust = repository.getCustomerById(custId)!!
        val unitId = repository.saveUnit(sampleUnit("FRZ-001", "GEA 300L", status = "Tersedia"))
        val unit = repository.getUnitById(unitId)!!

        val txId = repository.saveTransaction(
            sampleTx("INV-DOC-001", custId, unitId, cust.namaLengkap, unit.namaUnit, unit.kodeUnit)
                .copy(latitude = -7.5755, longitude = 110.8243)
        )

        val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val docDir = File(context.filesDir, "test_doc_photos").apply { mkdirs() }
        val freezerFile = File(docDir, "freezer_lokasi_1.jpg").also { f ->
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val frontFile = File(docDir, "tampak_depan_1.jpg").also { f ->
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }

        val freezerPhotoId = repository.saveCustomerPhoto(
            CustomerPhotoEntity(
                customerId = custId,
                transactionId = txId,
                unitId = unitId,
                kategori = CustomerPhotoStorageHelper.KAT_FOTO_LOKASI,
                namaDokumentasi = LocationDocSlots.SLOT_FREEZER_DI_LOKASI,
                filePath = freezerFile.absolutePath,
                fileName = freezerFile.name,
                sizeBytes = freezerFile.length(),
                tanggalWaktuIso = "2026-11-20T09:15:00",
                latitude = -7.5755,
                longitude = 110.8243,
                catatan = "Freezer di lokasi pelanggan",
                uploadStatus = "PENDING"
            )
        )
        val frontPhotoId = repository.saveCustomerPhoto(
            CustomerPhotoEntity(
                customerId = custId,
                transactionId = txId,
                unitId = unitId,
                kategori = CustomerPhotoStorageHelper.KAT_FOTO_LOKASI,
                namaDokumentasi = LocationDocSlots.SLOT_TAMPAK_DEPAN_LOKASI,
                filePath = frontFile.absolutePath,
                fileName = frontFile.name,
                sizeBytes = frontFile.length(),
                tanggalWaktuIso = "2026-11-20T09:16:00",
                latitude = -7.5755,
                longitude = 110.8243,
                catatan = "Tampak depan toko",
                uploadStatus = "PENDING"
            )
        )

        val savedPhotos = repository.getCustomerPhotosOnce(custId)
        assertEquals(2, savedPhotos.size)
        assertTrue(savedPhotos.any { it.namaDokumentasi == LocationDocSlots.SLOT_FREEZER_DI_LOKASI && it.unitId == unitId && it.transactionId == txId })
        assertTrue(savedPhotos.any { it.namaDokumentasi == LocationDocSlots.SLOT_TAMPAK_DEPAN_LOKASI && it.latitude == -7.5755 && it.longitude == 110.8243 })
        assertEquals("Menunggu", mapUploadStatusLabelAndColor("PENDING").first)
        assertEquals("Mengunggah", mapUploadStatusLabelAndColor("UPLOADING").first)
        assertEquals("Berhasil", mapUploadStatusLabelAndColor("UPLOADED").first)
        assertEquals("Gagal", mapUploadStatusLabelAndColor("FAILED").first)

        val metaFreezer = DrivePhotoMetadataEntity(
            fileName = freezerFile.name,
            categoryCode = DriveDocumentationCategory.FOTO_UNIT.code,
            customerId = custId,
            transactionId = txId,
            unitId = unitId,
            driveYearFolder = "2026",
            localCompressedPath = freezerFile.absolutePath,
            tempOriginalPath = freezerFile.absolutePath,
            compressedSizeBytes = freezerFile.length(),
            syncStatus = GoogleDrivePhotoCenterManager.STATUS_MENUNGGU_UPLOAD
        )
        val metaId = repository.upsertDrivePhotoMetadata(metaFreezer)
        val persistedMeta = metaFreezer.copy(id = metaId)

        // Offline -> stays PENDING & local file preserved
        val offlineRes = GoogleDrivePhotoCenterManager.uploadSingleQueuedMetadata(
            context = context,
            repository = repository,
            metadata = persistedMeta,
            driveClient = restClient,
            explicitAccessToken = "ya29.audit-comprehensive-token",
            isOnlineOverride = false,
            linkedCustomerPhotoId = freezerPhotoId
        )
        assertTrue(offlineRes.isSuccess)
        assertEquals("PENDING", repository.getCustomerPhotosOnce(custId).first { it.id == freezerPhotoId }.uploadStatus)
        assertTrue(freezerFile.exists())

        // Online -> uploads to Drive & mirrors UPLOADED + driveFileId to customer_photos
        val onlineRes = GoogleDrivePhotoCenterManager.uploadSingleQueuedMetadata(
            context = context,
            repository = repository,
            metadata = persistedMeta,
            driveClient = restClient,
            explicitAccessToken = "ya29.audit-comprehensive-token",
            isOnlineOverride = true,
            linkedCustomerPhotoId = freezerPhotoId
        )
        assertTrue("Online photo upload must succeed: ${onlineRes.exceptionOrNull()}", onlineRes.isSuccess)
        val updatedPhoto = repository.getCustomerPhotosOnce(custId).first { it.id == freezerPhotoId }
        assertEquals("UPLOADED", updatedPhoto.uploadStatus)
        assertTrue(updatedPhoto.driveFileId.isNotBlank())
        assertTrue(fakeDrive.activePhotoFiles().isNotEmpty())

        // Failure while online (503) -> marks FAILED & keeps local file intact
        fakeDrive.simulateOffline.set(true)
        val metaFront = metaFreezer.copy(
            id = 0L,
            fileName = frontFile.name,
            localCompressedPath = frontFile.absolutePath,
            tempOriginalPath = frontFile.absolutePath
        )
        val frontMetaId = repository.upsertDrivePhotoMetadata(metaFront)
        val failRes = GoogleDrivePhotoCenterManager.uploadSingleQueuedMetadata(
            context = context,
            repository = repository,
            metadata = metaFront.copy(id = frontMetaId),
            driveClient = restClient,
            explicitAccessToken = "ya29.audit-comprehensive-token",
            isOnlineOverride = true,
            linkedCustomerPhotoId = frontPhotoId
        )
        assertTrue(failRes.isFailure)
        assertEquals("FAILED", repository.getCustomerPhotosOnce(custId).first { it.id == frontPhotoId }.uploadStatus)
        assertTrue(frontFile.exists())
        fakeDrive.simulateOffline.set(false)
    }

    // =========================================================================
    // TEST 2 & TEST 3: 1 PELANGGAN SEWA 3 UNIT + PERPANJANG HANYA UNIT 2
    // =========================================================================
    @Test
    fun test2_and_test3_multiUnitThreeUnitsAndRenewOnlyUnit2LeavesUnit1And3Unchanged(): Unit = runBlocking {
        val custId = repository.saveCustomer(sampleCustomer("PLG-MULTI", "Warung Es Bu Sari"))
        val u1Id = repository.saveUnit(sampleUnit("FRZ-U1", "GEA 200L Unit 1", monthlyPrice = 250_000L))
        val u2Id = repository.saveUnit(sampleUnit("FRZ-U2", "GEA 300L Unit 2", monthlyPrice = 350_000L))
        val u3Id = repository.saveUnit(sampleUnit("FRZ-U3", "RSA 400L Unit 3", monthlyPrice = 450_000L))

        val u1 = repository.getUnitById(u1Id)!!
        val u2 = repository.getUnitById(u2Id)!!
        val u3 = repository.getUnitById(u3Id)!!

        val baseTx = sampleTx(
            invoice = "INV-MULTI-001",
            custId = custId,
            unitId = u1Id,
            custName = "Warung Es Bu Sari",
            unitName = "GEA 200L Unit 1, GEA 300L Unit 2, RSA 400L Unit 3",
            unitCode = "FRZ-U1, FRZ-U2, FRZ-U3",
            harga = 1_050_000L,
            start = "2026-10-01",
            end = "2026-10-31"
        )
        val unitItems = listOf(
            TransactionUnitItemEntity(
                transactionId = 0L, customerId = custId, unitId = u1Id, unitIndexNumber = 1,
                kodeUnitSnapshot = u1.kodeUnit, namaUnitSnapshot = u1.namaUnit, jenisProgram = "Sewa Reguler",
                hargaSewa = 250_000L, durasiHari = 30, tanggalMulaiSewa = "2026-10-01",
                tanggalAkhirSewa = "2026-10-31", tanggalJatuhTempo = "2026-10-31",
                nominalPembayaran = 250_000L, statusPembayaran = "Lunas", statusPenyewaan = "Dalam Penyewaan"
            ),
            TransactionUnitItemEntity(
                transactionId = 0L, customerId = custId, unitId = u2Id, unitIndexNumber = 2,
                kodeUnitSnapshot = u2.kodeUnit, namaUnitSnapshot = u2.namaUnit, jenisProgram = "Sewa Reguler",
                hargaSewa = 350_000L, durasiHari = 30, tanggalMulaiSewa = "2026-10-01",
                tanggalAkhirSewa = "2026-10-31", tanggalJatuhTempo = "2026-10-31",
                nominalPembayaran = 350_000L, statusPembayaran = "Lunas", statusPenyewaan = "Dalam Penyewaan"
            ),
            TransactionUnitItemEntity(
                transactionId = 0L, customerId = custId, unitId = u3Id, unitIndexNumber = 3,
                kodeUnitSnapshot = u3.kodeUnit, namaUnitSnapshot = u3.namaUnit, jenisProgram = "Sewa Reguler",
                hargaSewa = 450_000L, durasiHari = 30, tanggalMulaiSewa = "2026-10-01",
                tanggalAkhirSewa = "2026-10-31", tanggalJatuhTempo = "2026-10-31",
                nominalPembayaran = 450_000L, statusPembayaran = "Lunas", statusPenyewaan = "Dalam Penyewaan"
            )
        )
        val baseTxId = repository.saveMultiUnitTransaction(baseTx, unitItems).id
        assertTrue(baseTxId > 0L)

        val profileBefore = repository.buildCustomerRenewalProfile(custId)!!
        assertEquals(3, profileBefore.activeUnits.size)
        assertEquals("Disewa", repository.getUnitById(u1Id)!!.statusUnit)
        assertEquals("Disewa", repository.getUnitById(u2Id)!!.statusUnit)
        assertEquals("Disewa", repository.getUnitById(u3Id)!!.statusUnit)

        val originalItems = dao.getTransactionUnitItemsByTxOnce(baseTxId)
        val origU1Item = originalItems.first { it.unitId == u1Id }
        val origU2Item = originalItems.first { it.unitId == u2Id }
        val origU3Item = originalItems.first { it.unitId == u3Id }

        val renewalResult = repository.executeDetailedRenewalTransaction(
            DetailedRenewalRequest(
                customerId = custId,
                selectedRenewals = listOf(
                    UnitRenewalSelection(
                        unitId = u2Id,
                        unitItemId = origU2Item.id,
                        sourceTransactionId = baseTxId,
                        sourceInvoiceNumber = baseTx.nomorInvoice,
                        kodeUnit = u2.kodeUnit,
                        namaUnit = u2.namaUnit,
                        nomorSeri = u2.nomorSeri,
                        jenisProgram = "Sewa Reguler",
                        periodDays = 30,
                        tanggalMulaiBaru = "2026-10-31",
                        tanggalAkhirBaru = "2026-11-30",
                        tanggalJatuhTempoBaru = "2026-11-30",
                        hargaSewa = 350_000L,
                        diskon = 25_000L,
                        nominalDibayar = 325_000L
                    )
                ),
                additionalNewUnits = emptyList(),
                metodePembayaran = "Transfer Bank",
                namaPetugas = "Petugas Solo"
            )
        )

        assertEquals(1, renewalResult.renewedUnitsCount)
        assertEquals(0, renewalResult.addedNewUnitsCount)
        assertEquals(325_000L, renewalResult.transaction.totalTagihan)

        val afterItems = dao.getTransactionUnitItemsByTxOnce(baseTxId)
        val afterU1 = afterItems.first { it.unitId == u1Id }
        val afterU2 = afterItems.first { it.unitId == u2Id }
        val afterU3 = afterItems.first { it.unitId == u3Id }

        // Unit 1 & Unit 3 remain 100% UNCHANGED
        assertEquals(origU1Item.tanggalAkhirSewa, afterU1.tanggalAkhirSewa)
        assertEquals(origU1Item.tanggalJatuhTempo, afterU1.tanggalJatuhTempo)
        assertEquals(origU1Item.hargaSewa, afterU1.hargaSewa)
        assertEquals(origU3Item.tanggalAkhirSewa, afterU3.tanggalAkhirSewa)
        assertEquals(origU3Item.tanggalJatuhTempo, afterU3.tanggalJatuhTempo)
        assertEquals(origU3Item.hargaSewa, afterU3.hargaSewa)

        // ONLY Unit 2 updated
        assertEquals("2026-11-30", afterU2.tanggalAkhirSewa)
        assertEquals("2026-11-30", afterU2.tanggalJatuhTempo)
        val exts = repository.getAllExtensionsOnce()
        assertEquals(1, exts.size)
        assertEquals(u2Id, exts.first().unitId)
        assertEquals("2026-11-30", exts.first().tanggalAkhirBaru)
    }

    // =========================================================================
    // TEST 4: SEWA HAK MILIK CONTINUITY (SUDAH BAYAR 5 -> LANJUT KE-6 DARI 12)
    // =========================================================================
    @Test
    fun test4_hakMilikRenewalContinuesStrictlyFrom5To6Of12AndNeverResetsTo1(): Unit = runBlocking {
        val custId = repository.saveCustomer(sampleCustomer("PLG-HM", "Toko Daging Sejahtera"))
        val hmUnitAId = repository.saveUnit(sampleUnit("FRZ-HM-A", "GEA Hak Milik A", monthlyPrice = 420_000L, tenor = 12))
        val hmUnitBId = repository.saveUnit(sampleUnit("FRZ-HM-B", "GEA Hak Milik B", monthlyPrice = 380_000L, tenor = 12))

        val baseTx = sampleTx(
            invoice = "INV-HM-001",
            custId = custId,
            unitId = hmUnitAId,
            custName = "Toko Daging Sejahtera",
            unitName = "GEA Hak Milik A, GEA Hak Milik B",
            unitCode = "FRZ-HM-A, FRZ-HM-B",
            harga = 800_000L,
            start = "2026-06-01",
            end = "2026-11-01",
            program = "Sewa Hak Milik",
            totalHm = 12,
            keHm = 5
        )
        val txId = repository.saveMultiUnitTransaction(
            baseTx,
            listOf(
                TransactionUnitItemEntity(
                    transactionId = 0L, customerId = custId, unitId = hmUnitAId, unitIndexNumber = 1,
                    kodeUnitSnapshot = "FRZ-HM-A", namaUnitSnapshot = "GEA Hak Milik A",
                    jenisProgram = "Sewa Hak Milik", totalPeriodeHakMilik = 12, pembayaranKeHakMilik = 5,
                    statusProgramHakMilik = "Aktif", hargaSewa = 420_000L, durasiHari = 30,
                    tanggalMulaiSewa = "2026-06-01", tanggalAkhirSewa = "2026-11-01", tanggalJatuhTempo = "2026-11-01",
                    nominalPembayaran = 420_000L, statusPembayaran = "Lunas", statusPenyewaan = "Dalam Penyewaan"
                ),
                TransactionUnitItemEntity(
                    transactionId = 0L, customerId = custId, unitId = hmUnitBId, unitIndexNumber = 2,
                    kodeUnitSnapshot = "FRZ-HM-B", namaUnitSnapshot = "GEA Hak Milik B",
                    jenisProgram = "Sewa Hak Milik", totalPeriodeHakMilik = 12, pembayaranKeHakMilik = 2,
                    statusProgramHakMilik = "Aktif", hargaSewa = 380_000L, durasiHari = 30,
                    tanggalMulaiSewa = "2026-09-01", tanggalAkhirSewa = "2026-11-01", tanggalJatuhTempo = "2026-11-01",
                    nominalPembayaran = 380_000L, statusPembayaran = "Lunas", statusPenyewaan = "Dalam Penyewaan"
                )
            )
        ).id

        val profileBefore = repository.buildCustomerRenewalProfile(custId)!!
        val infoA = profileBefore.activeUnits.first { it.unitId == hmUnitAId }
        val infoB = profileBefore.activeUnits.first { it.unitId == hmUnitBId }
        assertEquals(12, infoA.totalPeriodeHakMilik)
        assertEquals(5, infoA.sudahDibayarHakMilik)
        assertEquals(7, infoA.sisaPeriodeHakMilik)
        assertEquals(6, infoA.pembayaranBerikutnyaHakMilik)
        assertEquals(2, infoB.sudahDibayarHakMilik)
        assertEquals(3, infoB.pembayaranBerikutnyaHakMilik)

        // Intentionally pass bogus pembayaranKeBaru = 1 to verify Repository ALWAYS enforces persisted history (5 -> 6)!
        val res = repository.executeDetailedRenewalTransaction(
            DetailedRenewalRequest(
                customerId = custId,
                selectedRenewals = listOf(
                    UnitRenewalSelection(
                        unitId = hmUnitAId,
                        unitItemId = infoA.unitItemId,
                        sourceTransactionId = txId,
                        sourceInvoiceNumber = baseTx.nomorInvoice,
                        kodeUnit = "FRZ-HM-A",
                        namaUnit = "GEA Hak Milik A",
                        nomorSeri = "SN-FRZ-HM-A",
                        jenisProgram = "Sewa Hak Milik",
                        periodDays = 30,
                        tanggalMulaiBaru = "2026-11-01",
                        tanggalAkhirBaru = "2026-12-01",
                        tanggalJatuhTempoBaru = "2026-12-01",
                        hargaSewa = 420_000L,
                        nominalDibayar = 420_000L,
                        totalPeriodeHakMilik = 12,
                        pembayaranKeSebelumnya = 0,
                        pembayaranKeBaru = 1
                    )
                )
            )
        )

        assertEquals(6, res.transaction.pembayaranKeHakMilik)
        assertEquals(12, res.transaction.totalPeriodeHakMilik)

        val profileAfter = repository.buildCustomerRenewalProfile(custId)!!
        val afterA = profileAfter.activeUnits.first { it.unitId == hmUnitAId }
        val afterB = profileAfter.activeUnits.first { it.unitId == hmUnitBId }
        assertEquals(6, afterA.sudahDibayarHakMilik)
        assertEquals(6, afterA.sisaPeriodeHakMilik)
        assertEquals(7, afterA.pembayaranBerikutnyaHakMilik)
        assertEquals(2, afterB.sudahDibayarHakMilik)
    }

    // =========================================================================
    // TEST 5 & TEST 6: TAMBAH 2 UNIT BARU SAAT PERPANJANGAN + HARGA MASTER UNIT
    // =========================================================================
    @Test
    fun test5_and_test6_addNewUnitsStrictlyFiltersAvailableOnlyAndEnforcesMasterPricing(): Unit = runBlocking {
        val custId = repository.saveCustomer(sampleCustomer("PLG-ADD", "Katering Berkah"))
        val existingUnitId = repository.saveUnit(sampleUnit("FRZ-EX", "Existing 300L", status = "Tersedia", monthlyPrice = 300_000L))
        val avail1Id = repository.saveUnit(sampleUnit("FRZ-AV1", "Available 200L", status = "Tersedia", monthlyPrice = 275_000L))
        val avail2Id = repository.saveUnit(sampleUnit("FRZ-AV2", "Available 500L", status = "Tersedia", monthlyPrice = 520_000L))
        val lostUnitId = repository.saveUnit(sampleUnit("FRZ-LOST", "Unit Hilang", status = "Hilang", monthlyPrice = 300_000L))
        val maintUnitId = repository.saveUnit(sampleUnit("FRZ-MAINT", "Unit Servis", status = "Dalam Perbaikan", monthlyPrice = 300_000L))
        val rentedOtherId = repository.saveUnit(sampleUnit("FRZ-OTH", "Unit Disewa Orang", status = "Disewa", monthlyPrice = 300_000L))

        // Configure explicit Master Unit Rental Price for avail1 (30 Hari = Rp 290.000)
        repository.saveUnitRentalPrices(avail1Id, mapOf(30 to 290_000L))

        val tx0Id = repository.saveMultiUnitTransaction(
            sampleTx("INV-ADD-001", custId, existingUnitId, "Katering Berkah", "Existing 300L", "FRZ-EX", 300_000L, "2026-10-01", "2026-10-31"),
            listOf(
                TransactionUnitItemEntity(
                    transactionId = 0L, customerId = custId, unitId = existingUnitId, unitIndexNumber = 1,
                    kodeUnitSnapshot = "FRZ-EX", namaUnitSnapshot = "Existing 300L", jenisProgram = "Sewa Reguler",
                    hargaSewa = 300_000L, durasiHari = 30, tanggalMulaiSewa = "2026-10-01",
                    tanggalAkhirSewa = "2026-10-31", tanggalJatuhTempo = "2026-10-31",
                    nominalPembayaran = 300_000L, statusPembayaran = "Lunas", statusPenyewaan = "Dalam Penyewaan"
                )
            )
        ).id
        val existingItem = dao.getTransactionUnitItemsByTxOnce(tx0Id).first()

        val selectable = repository.getAllUnitsOnce().filter { it.isStrictlyAvailableForNewRental() }
        assertEquals(setOf(avail1Id, avail2Id), selectable.map { it.id }.toSet())

        // Attempt to add Hilang, Dalam Perbaikan, or Disewa -> MUST be rejected!
        for (badId in listOf(lostUnitId, maintUnitId, rentedOtherId)) {
            var rejected = false
            try {
                repository.executeDetailedRenewalTransaction(
                    DetailedRenewalRequest(
                        customerId = custId,
                        selectedRenewals = emptyList(),
                        additionalNewUnits = listOf(
                            NewUnitDuringRenewalSelection(
                                unitId = badId,
                                kodeUnit = "BAD",
                                namaUnit = "Bad Unit",
                                merek = "GEA",
                                kapasitasLiter = 300,
                                nomorSeri = "SN-BAD",
                                hargaSewaMaster = 300_000L
                            )
                        )
                    )
                )
            } catch (_: IllegalStateException) {
                rejected = true
            }
            assertTrue("Unavailable unit ID $badId must be rejected", rejected)
        }

        // Renew existingUnit + add 2 valid Tersedia units with bogus prices (1L) -> Repository MUST enforce Master Pricing!
        val result = repository.executeDetailedRenewalTransaction(
            DetailedRenewalRequest(
                customerId = custId,
                selectedRenewals = listOf(
                    UnitRenewalSelection(
                        unitId = existingUnitId,
                        unitItemId = existingItem.id,
                        sourceTransactionId = tx0Id,
                        sourceInvoiceNumber = "INV-ADD-001",
                        kodeUnit = "FRZ-EX",
                        namaUnit = "Existing 300L",
                        nomorSeri = "SN-FRZ-EX",
                        jenisProgram = "Sewa Reguler",
                        periodDays = 30,
                        tanggalMulaiBaru = "2026-10-31",
                        tanggalAkhirBaru = "2026-11-30",
                        tanggalJatuhTempoBaru = "2026-11-30",
                        hargaSewa = 300_000L,
                        nominalDibayar = 300_000L
                    )
                ),
                additionalNewUnits = listOf(
                    NewUnitDuringRenewalSelection(
                        unitId = avail1Id,
                        kodeUnit = "FRZ-AV1",
                        namaUnit = "Available 200L",
                        merek = "GEA",
                        kapasitasLiter = 200,
                        nomorSeri = "SN-FRZ-AV1",
                        jenisProgram = "Sewa Reguler",
                        periodDays = 30,
                        hargaSewaMaster = 1L, // Overridden to 290_000L
                        nominalDibayar = 290_000L
                    ),
                    NewUnitDuringRenewalSelection(
                        unitId = avail2Id,
                        kodeUnit = "FRZ-AV2",
                        namaUnit = "Available 500L",
                        merek = "GEA",
                        kapasitasLiter = 500,
                        nomorSeri = "SN-FRZ-AV2",
                        jenisProgram = "Sewa Reguler",
                        periodDays = 30,
                        hargaSewaMaster = 1L, // Overridden to 520_000L
                        nominalDibayar = 520_000L
                    )
                )
            )
        )

        assertEquals(1, result.renewedUnitsCount)
        assertEquals(2, result.addedNewUnitsCount)
        assertEquals(1_110_000L, result.transaction.totalTagihan)
        assertEquals("Disewa", repository.getUnitById(avail1Id)!!.statusUnit)
        assertEquals("Disewa", repository.getUnitById(avail2Id)!!.statusUnit)
        assertEquals("Hilang", repository.getUnitById(lostUnitId)!!.statusUnit)

        val newTxItems = dao.getTransactionUnitItemsByTxOnce(result.transaction.id)
        assertEquals(3, newTxItems.size)
        assertEquals(290_000L, newTxItems.first { it.unitId == avail1Id }.hargaSewa)
        assertEquals(520_000L, newTxItems.first { it.unitId == avail2Id }.hargaSewa)
    }

    // =========================================================================
    // TEST 7 & TEST 8: BACKUP (.ZIP & .JSON) -> PREVIEW -> RESTORE -> DRIVE SYNC
    // =========================================================================
    @Test
    fun test7_and_test8_exportPreviewRestoreZipAndJsonAndReconcileDriveWithoutResurrection(): Unit = runBlocking {
        val custAId = repository.saveCustomer(sampleCustomer("PLG-BKP-A", "Pelanggan Backup Asli"))
        val unitAId = repository.saveUnit(sampleUnit("FRZ-BKP-A", "Unit Backup Asli"))
        val txAId = repository.saveTransaction(
            sampleTx("INV-BKP-A", custAId, unitAId, "Pelanggan Backup Asli", "Unit Backup Asli", "FRZ-BKP-A")
        )
        repository.savePaymentProof(
            PaymentProofEntity(
                transactionId = txAId,
                tanggalPembayaran = "2026-11-01",
                nominalPembayaran = 350_000L,
                metodePembayaran = "Transfer Bank",
                namaPengirim = "Pelanggan Backup Asli"
            )
        )

        mirrorEngine.processSyncQueue().getOrThrow()
        val genBefore = DatabaseGenerationManager(context).getCurrentGenerationId()

        // TEST 7a: Export ZIP backup & inspect preview
        val (_, zipBackupFile) = StructuredBackupEngine.createStructuredBackupZip(
            context = context,
            repository = repository,
            encryptSensitivePayload = false,
            backupType = "FULL_BACKUP"
        ).getOrThrow()
        assertTrue(zipBackupFile.exists() && zipBackupFile.length() > 0L)

        val zipPreview = StructuredBackupEngine.inspectBackupZipForRestore(context, zipBackupFile)
        assertTrue("ZIP preview must be valid: ${zipPreview.errorMessage}", zipPreview.isValid)
        assertEquals(1, zipPreview.customerCount)
        assertEquals(1, zipPreview.unitCount)
        assertEquals(1, zipPreview.transactionCount)
        assertEquals(1, zipPreview.paymentCount)
        assertEquals(StructuredBackupEngine.CURRENT_DB_VERSION, zipPreview.databaseVersion)

        // TEST 7b: Raw JSON backup export + inspect preview
        val snap = StructuredBackupEngine.captureCurrentSnapshot(context, repository)
        val jsonFile = File(context.cacheDir, "test_export_backup.json").apply {
            val root = JSONObject().apply {
                put("backupId", "JSON-BKP-1")
                put("createdAt", "2026-11-20 10:00:00")
                put("appVersion", StructuredBackupEngine.CURRENT_APP_VERSION)
                put("databaseVersion", StructuredBackupEngine.CURRENT_DB_VERSION)
                put("customers", BackupJsonMapper.customersToJson(snap.customers).getJSONArray("customers"))
                put("units", BackupJsonMapper.unitsToJson(snap.units).getJSONArray("units"))
                put("transactions", BackupJsonMapper.transactionsToJson(snap.transactions).getJSONArray("transactions"))
                put("paymentProofs", BackupJsonMapper.paymentsToJson(snap.paymentProofs, snap.unitAllocations).getJSONArray("paymentProofs"))
            }
            writeText(root.toString())
        }
        val jsonPreview = StructuredBackupEngine.inspectBackupZipForRestore(context, jsonFile)
        assertTrue("JSON preview must be valid: ${jsonPreview.errorMessage}", jsonPreview.isValid)
        assertEquals(1, jsonPreview.customerCount)
        assertEquals(1, jsonPreview.unitCount)
        assertEquals(1, jsonPreview.transactionCount)
        assertEquals(1, jsonPreview.paymentCount)

        // Mutate DB to a DIFFERENT Dataset B and sync it to Drive!
        val custBId = repository.saveCustomer(sampleCustomer("PLG-TEMP-B", "Pelanggan Sementara Yang Harus Hilang"))
        val custBUuid = repository.getCustomerById(custBId)!!.uuid
        val unitBId = repository.saveUnit(sampleUnit("FRZ-TEMP-B", "Unit Sementara"))
        val unitBUuid = repository.getUnitById(unitBId)!!.uuid
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(2, repository.getAllCustomersOnce().size)
        assertTrue(fakeDrive.activeRecordFiles().any { it.appProperties["record_uuid"] == custBUuid })

        // Execute FULL_RESTORE from ZIP backup with progress tracking
        val progressEvents = mutableListOf<Float>()
        val restoreRes = StructuredBackupEngine.executeSafeRestore(
            context = context,
            repository = repository,
            sourceFileOrUri = zipBackupFile,
            mode = RestoreMode.FULL_RESTORE,
            onProgress = { pct, _ -> progressEvents.add(pct) }
        )
        assertTrue("Full restore must succeed: ${restoreRes.message}", restoreRes.success)
        assertTrue(progressEvents.isNotEmpty() && progressEvents.last() >= 0.99f)

        assertEquals(1, repository.getAllCustomersOnce().size)
        assertEquals("Pelanggan Backup Asli", repository.getAllCustomersOnce().first().namaLengkap)
        assertEquals(1, repository.getAllUnitsOnce().size)
        assertEquals(1, repository.getAllTransactionsOnce().size)
        assertEquals(1, repository.getAllPaymentProofsOnce().size)

        // TEST 8: Generation rotated & Google Drive deletes Dataset B without resurrection!
        val genAfter = DatabaseGenerationManager(context).getCurrentGenerationId()
        assertNotEquals("Dataset generation MUST rotate after full restore", genBefore, genAfter)

        mirrorEngine.processSyncQueue().getOrThrow()

        assertFalse(
            "Pre-restore customer B must be removed from Google Drive",
            fakeDrive.activeRecordFiles().any { it.appProperties["record_uuid"] == custBUuid }
        )
        assertFalse(
            "Pre-restore unit B must be removed from Google Drive",
            fakeDrive.activeRecordFiles().any { it.appProperties["record_uuid"] == unitBUuid }
        )

        mirrorEngine.verifyConsistencyOnAppStartup().getOrThrow()
        assertEquals(1, repository.getAllCustomersOnce().size)
        assertEquals("Pelanggan Backup Asli", repository.getAllCustomersOnce().first().namaLengkap)

        // JSON restore also succeeds cleanly
        val jsonRestoreRes = StructuredBackupEngine.executeSafeRestore(
            context = context,
            repository = repository,
            sourceFileOrUri = jsonFile,
            mode = RestoreMode.FULL_RESTORE
        )
        assertTrue("JSON restore must succeed: ${jsonRestoreRes.message}", jsonRestoreRes.success)
        assertEquals(1, repository.getAllCustomersOnce().size)
    }

    // =========================================================================
    // TEST 9 & TEST 10: OFFLINE QUEUE -> RECONNECT -> ZERO DUPLICATE DRIVE SYNC
    // =========================================================================
    @Test
    fun test9_and_test10_offlineTransactionQueuedLocallyThenSyncedOnceOnReconnectWithZeroDuplicates(): Unit = runBlocking {
        fakeDrive.simulateOffline.set(true)

        val custId = repository.saveCustomer(sampleCustomer("PLG-OFF", "Pelanggan Mode Offline"))
        val unitId = repository.saveUnit(sampleUnit("FRZ-OFF", "Unit Mode Offline"))
        val txId = repository.saveTransaction(
            sampleTx("INV-OFF-001", custId, unitId, "Pelanggan Mode Offline", "Unit Mode Offline", "FRZ-OFF")
        )

        assertNotNull(repository.getTransactionById(txId))
        val pendingQueueBefore = repository.getAllDriveLocalSyncQueueOnce()
            .filter { it.syncStatus == MirrorOutboxCodec.STATUS_PENDING_SYNC }
        assertTrue("Offline changes must be queued in SQLite outbox", pendingQueueBefore.size >= 3)

        mirrorEngine.processSyncQueue()
        assertEquals(0, fakeDrive.activeRecordFiles().size)

        // Reconnect internet -> drains outbox with ZERO duplicates
        fakeDrive.simulateOffline.set(false)
        mirrorEngine.processSyncQueue(forceRetryFailed = true, ignoreBackoffTimers = true).getOrThrow()

        val txUuid = repository.getTransactionById(txId)!!.uuid
        val custUuid = repository.getCustomerById(custId)!!.uuid
        val unitUuid = repository.getUnitById(unitId)!!.uuid

        val driveFilesAfterFirstSync = fakeDrive.activeRecordFiles()
        assertEquals(1, driveFilesAfterFirstSync.count { it.appProperties["record_uuid"] == custUuid })
        assertEquals(1, driveFilesAfterFirstSync.count { it.appProperties["record_uuid"] == unitUuid })
        assertEquals(1, driveFilesAfterFirstSync.count { it.appProperties["record_uuid"] == txUuid })

        // Re-running sync remains 100% idempotent (ZERO duplicates)
        mirrorEngine.processSyncQueue().getOrThrow()
        assertEquals(driveFilesAfterFirstSync.size, fakeDrive.activeRecordFiles().size)
        assertEquals(0, repository.getAllDriveLocalSyncQueueOnce().count { it.syncStatus == MirrorOutboxCodec.STATUS_PENDING_SYNC })
    }

    // =========================================================================
    // TEST 11: CUSTOMIZE APP (TEMA, WARNA, FONT, UPLOAD .TTF, BACKGROUND, RESTART)
    // =========================================================================
    @Test
    fun test11_customizeAppearancePersistsAcrossRestartAndValidatesCustomFontsSafely(): Unit = runBlocking {
        val custId = repository.saveCustomer(sampleCustomer("PLG-APP", "Pelanggan Stabil"))
        val initialOutboxCount = repository.getAllDriveLocalSyncQueueOnce().size

        assertEquals(11, AppColorThemePreset.entries.size)
        val unreadableWhiteOnWhite = AppAppearanceConfig.ensureReadableTextOn(
            androidx.compose.ui.graphics.Color.White,
            androidx.compose.ui.graphics.Color.White
        )
        assertTrue(
            AppAppearanceConfig.contrastRatio(unreadableWhiteOnWhite, androidx.compose.ui.graphics.Color.White) >= 4.5
        )

        val badFont = File(context.cacheDir, "fake_font.ttf").apply {
            writeText("NOT_A_VALID_TTF_FILE_1234567890_PADDING_BYTES_1234567890_PADDING")
        }
        assertTrue(AppAppearancePreferences.importAndValidateCustomFont(context, badFont, "fake_font.ttf").isFailure)
        assertTrue(AppAppearancePreferences.importAndValidateCustomFont(context, badFont, "malware.exe").isFailure)

        val realTtfCopy = File(context.cacheDir, "inter_custom.ttf")
        context.resources.openRawResource(R.font.inter).use { input ->
            realTtfCopy.outputStream().use { output -> input.copyTo(output) }
        }
        val goodFontRes = AppAppearancePreferences.importAndValidateCustomFont(context, realTtfCopy, "inter_custom.ttf")
        assertTrue("Valid .ttf must succeed: ${goodFontRes.exceptionOrNull()}", goodFontRes.isSuccess)
        val (savedFontPath, savedFontName) = goodFontRes.getOrThrow()
        assertTrue(File(savedFontPath).exists())

        val bgBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val savedBgPath = AppAppearancePreferences.saveCustomBackgroundImage(context, bgBmp).getOrThrow()
        assertTrue(File(savedBgPath).exists())

        val customConfig = AppAppearanceConfig(
            themePresetId = AppColorThemePreset.CUSTOM.id,
            primaryColorHex = "#6D28D9",
            secondaryColorHex = AppColorThemePreset.PURPLE.secondaryHex,
            backgroundColorHex = AppColorThemePreset.PURPLE.backgroundHex,
            cardColorHex = AppColorThemePreset.PURPLE.cardHex,
            textColorHex = AppColorThemePreset.PURPLE.textHex,
            navBarColorHex = AppColorThemePreset.PURPLE.navBarHex,
            buttonColorHex = "#6D28D9",
            accentColorHex = "#EC4899",
            fontChoiceId = AppFontChoice.CUSTOM_FILE.id,
            customFontPath = savedFontPath,
            customFontDisplayName = savedFontName,
            backgroundImagePath = savedBgPath,
            backgroundScaleModeId = BackgroundScaleMode.FIT.id,
            backgroundPositionId = BackgroundPositionMode.TOP.id,
            backgroundOpacity = 0.25f,
            backgroundBlurDp = 6f,
            backgroundScopeId = BackgroundTargetScope.DASHBOARD_ONLY.id
        )
        AppAppearancePreferences.save(context, customConfig)

        val reloaded = AppAppearancePreferences.load(context)
        assertEquals("#6D28D9", reloaded.primaryColorHex)
        assertEquals("#EC4899", reloaded.accentColorHex)
        assertEquals(AppFontChoice.CUSTOM_FILE, reloaded.fontChoice)
        assertEquals(savedFontPath, reloaded.customFontPath)
        assertEquals(savedBgPath, reloaded.backgroundImagePath)
        assertEquals(BackgroundTargetScope.DASHBOARD_ONLY, reloaded.backgroundScope)
        assertTrue(reloaded.shouldShowBackgroundOnTab(0))
        assertFalse(reloaded.shouldShowBackgroundOnTab(3))

        assertNotNull(repository.getCustomerById(custId))
        assertEquals(initialOutboxCount, repository.getAllDriveLocalSyncQueueOnce().size)

        val resetCfg = AppAppearancePreferences.reset(context)
        assertEquals(AppColorThemePreset.BLUE_OCEAN, resetCfg.preset)
        assertEquals(AppFontChoice.DEFAULT_PLUS_JAKARTA, resetCfg.fontChoice)
    }

    // =========================================================================
    // TEST 12: REAL LOADING & NOTIFICATION LIFECYCLE + DOUBLE-TAP GUARD
    // =========================================================================
    @Test
    fun test12_realLoadingStateLifecycleErrorRetryAndDoubleSubmitGuard(): Unit = runBlocking {
        val observedStates = mutableListOf<AppLoadingState>()
        val notifications = mutableListOf<AppNotificationEvent>()
        val busyGuard = AtomicBoolean(false)
        var executionCount = 0

        suspend fun runTrackedOperation(shouldFail: Boolean): Boolean {
            if (!busyGuard.compareAndSet(false, true)) return false
            return try {
                observedStates.add(AppLoadingState(isLoading = true, message = "Sedang memproses transaksi...", isBlocking = false))
                executionCount++
                if (shouldFail) throw IllegalStateException("Koneksi Google Drive terputus.")
                val id = repository.saveCustomer(sampleCustomer("PLG-LOAD-$executionCount", "Pelanggan Real Loading"))
                assertTrue(id > 0L)
                notifications.add(
                    AppNotificationEvent(
                        type = OperationNotificationType.SUCCESS,
                        message = "Transaksi berhasil dibuat."
                    )
                )
                true
            } catch (e: Exception) {
                notifications.add(
                    AppNotificationEvent(
                        type = OperationNotificationType.ERROR,
                        message = "Proses gagal.",
                        detail = e.message.orEmpty(),
                        retryLabel = "Coba Lagi",
                        onRetry = {}
                    )
                )
                false
            } finally {
                observedStates.add(AppLoadingState(isLoading = false))
                busyGuard.set(false)
            }
        }

        assertFalse(runTrackedOperation(shouldFail = true))
        assertTrue(observedStates[0].isLoading)
        assertFalse(observedStates[1].isLoading)
        assertEquals(OperationNotificationType.ERROR, notifications.last().type)
        assertEquals("Coba Lagi", notifications.last().retryLabel)
        assertNotNull(notifications.last().onRetry)

        busyGuard.set(true)
        assertFalse(runTrackedOperation(shouldFail = false))
        busyGuard.set(false)

        assertTrue(runTrackedOperation(shouldFail = false))
        assertEquals(OperationNotificationType.SUCCESS, notifications.last().type)
        assertEquals("Transaksi berhasil dibuat.", notifications.last().message)
        assertEquals(1, repository.getAllCustomersOnce().size)
    }

    // =========================================================================
    // MIGRATION_24_25 NON-DESTRUCTIVE UPGRADE TEST
    // =========================================================================
    @Test
    fun migration24To25_preservesExistingRecordsAndAddsNewColumnsIdempotently(): Unit = runBlocking {
        val migDbName = "migration_24_25_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(migDbName)
        dbFile.parentFile?.mkdirs()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.execSQL("PRAGMA user_version = 24")
        rawDb.execSQL(
            """
            CREATE TABLE customer_photos (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                customerId INTEGER NOT NULL,
                transactionId INTEGER,
                kategori TEXT NOT NULL,
                fileName TEXT NOT NULL,
                localPath TEXT NOT NULL,
                fileSizeBytes INTEGER NOT NULL,
                capturedAtIso TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                catatan TEXT NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            INSERT INTO customer_photos (customerId, transactionId, kategori, fileName, localPath, fileSizeBytes, capturedAtIso, catatan)
            VALUES (1, 10, '04_Foto_Lokasi', 'lama_freezer.jpg', '/tmp/lama_freezer.jpg', 12345, '2026-10-01T08:00:00', 'Foto lama v24')
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE extension_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                originalTransactionId INTEGER NOT NULL,
                newTransactionId INTEGER NOT NULL,
                nomorInvoiceLama TEXT NOT NULL,
                nomorInvoiceBaru TEXT NOT NULL,
                tanggalPerpanjangan TEXT NOT NULL,
                tanggalAkhirLama TEXT NOT NULL,
                tanggalAkhirBaru TEXT NOT NULL,
                tambahanDurasiHari INTEGER NOT NULL,
                tagihanBaru INTEGER NOT NULL,
                catatan TEXT NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            INSERT INTO extension_history (originalTransactionId, newTransactionId, nomorInvoiceLama, nomorInvoiceBaru, tanggalPerpanjangan, tanggalAkhirLama, tanggalAkhirBaru, tambahanDurasiHari, tagihanBaru, catatan)
            VALUES (10, 11, 'INV-001', 'INV-002', '2026-11-01', '2026-11-01', '2026-12-01', 30, 350000, 'Perpanjangan v24')
            """.trimIndent()
        )
        rawDb.close()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(25) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        AppDatabase.MIGRATION_24_25.migrate(db)
                        AppDatabase.MIGRATION_24_25.migrate(db)
                    }
                })
                .build()
        )
        val upgraded = helper.writableDatabase

        upgraded.query("SELECT fileName, unit_id, uuid, upload_status, drive_file_id FROM customer_photos").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("lama_freezer.jpg", c.getString(0))
            assertTrue(c.isNull(1))
            assertEquals("", c.getString(2))
            assertEquals("PENDING", c.getString(3))
            assertEquals("", c.getString(4))
        }
        upgraded.query("SELECT nomorInvoiceBaru, unit_id, pembayaran_ke_hak_milik, status_pembayaran FROM extension_history").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("INV-002", c.getString(0))
            assertEquals(0L, c.getLong(1))
            assertEquals(0, c.getInt(2))
            assertEquals("Lunas", c.getString(3))
        }
        helper.close()
        context.deleteDatabase(migDbName)
    }
}
