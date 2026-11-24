package com.example

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.backup.RestoreMode
import com.example.backup.StructuredBackupEngine
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.DriveLocalSyncQueueEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.repository.FreezerRepository
import com.example.data.repository.UnitReservationManager
import com.example.drive.GoogleDriveAuthManager
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.GoogleDriveRestClient
import com.example.drive.MirrorOutboxCodec
import com.example.ui.screens.TransactionsScreen
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionLifecycleAndMultiUnitAuditTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: FreezerDao
    private lateinit var repository: FreezerRepository
    private lateinit var mirrorEngine: GoogleDriveMirrorSyncEngine
    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeDrive: FakeDriveCloudServer
    private lateinit var dbName: String

    private class FakeDriveCloudServer {
        data class DriveObj(
            val id: String,
            var name: String,
            var mimeType: String,
            var parentId: String,
            var payloadText: String = "",
            var trashed: Boolean = false,
            var appId: String = "",
            var datasetId: String = "",
            var generationId: String = "",
            var recordUuid: String = "",
            var entityType: String = "",
            var revision: Long = 1L,
            var schemaVersion: Int = 24,
            var checksum: String = "",
            var syncTimestamp: Long = 0L
        )

        val objects = ConcurrentHashMap<String, DriveObj>()
        val idSeq = AtomicInteger(100)
        @Volatile
        var networkOffline: Boolean = false

        fun activeTransactionFiles(): List<DriveObj> =
            objects.values.filter { !it.trashed && it.entityType == MirrorOutboxCodec.CATEGORY_TRANSACTION }

        fun dispatcher(): Dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (networkOffline) {
                    return MockResponse().setResponseCode(503).setBody("""{"error":{"message":"Offline simulated"}}""")
                }
                val path = request.path.orEmpty()
                val method = request.method.orEmpty()
                return when {
                    method == "GET" && path.startsWith("/drive/v3/files?") -> handleList(path)
                    method == "GET" && path.contains("alt=media") -> handleDownload(path)
                    method == "GET" && path.startsWith("/drive/v3/files/") -> handleGetMeta(path)
                    method == "POST" && path.startsWith("/drive/v3/files") && !path.startsWith("/upload/") -> handleCreateFolder(request)
                    (method == "POST" || method == "PATCH") && path.startsWith("/upload/drive/v3/files") -> handleMultipartUpload(request, path, method == "PATCH")
                    method == "DELETE" && path.startsWith("/drive/v3/files/") -> handleDelete(path)
                    else -> MockResponse().setResponseCode(404).setBody("""{"error":{"message":"Unknown $method $path"}}""")
                }
            }
        }

        private fun handleList(path: String): MockResponse {
            val decoded = java.net.URLDecoder.decode(path, "UTF-8")
            val qParam = decoded.substringAfter("q=", "").substringBefore("&")
            val matched = objects.values.filter { obj ->
                if (qParam.contains("trashed = false") && obj.trashed) return@filter false
                val parentRegex = Regex("'([^']+)' in parents")
                val pm = parentRegex.find(qParam)
                if (pm != null && obj.parentId != pm.groupValues[1]) return@filter false
                val nameRegex = Regex("name = '([^']+)'")
                val nm = nameRegex.find(qParam)
                if (nm != null && obj.name != nm.groupValues[1]) return@filter false
                val mimeRegex = Regex("mimeType = '([^']+)'")
                val mm = mimeRegex.find(qParam)
                if (mm != null && obj.mimeType != mm.groupValues[1]) return@filter false
                val propRegex = Regex("appProperties has \\{ key='([^']+)' and value='([^']+)' \\}")
                for (m in propRegex.findAll(qParam)) {
                    val k = m.groupValues[1]
                    val v = m.groupValues[2]
                    val actual = when (k) {
                        "appId", "applicationId" -> obj.appId
                        "datasetId" -> obj.datasetId
                        "record_uuid", "recordUuid" -> obj.recordUuid
                        "entity_type", "recordCategory" -> obj.entityType
                        else -> ""
                    }
                    if (actual != v) return@filter false
                }
                true
            }
            val arr = JSONArray()
            matched.forEach { arr.put(toJson(it)) }
            return MockResponse().setResponseCode(200).setBody(JSONObject().put("files", arr).toString())
        }

        private fun handleDownload(path: String): MockResponse {
            val fileId = path.substringAfter("/drive/v3/files/").substringBefore("?")
            val obj = objects[fileId]
            return if (obj == null || obj.trashed) {
                MockResponse().setResponseCode(404).setBody("""{"error":{"message":"Not found"}}""")
            } else {
                MockResponse().setResponseCode(200).setBody(obj.payloadText)
            }
        }

        private fun handleGetMeta(path: String): MockResponse {
            val fileId = path.substringAfter("/drive/v3/files/").substringBefore("?")
            val obj = objects[fileId]
            return if (obj == null || obj.trashed) {
                MockResponse().setResponseCode(404).setBody("""{"error":{"message":"Not found"}}""")
            } else {
                MockResponse().setResponseCode(200).setBody(toJson(obj).toString())
            }
        }

        private fun handleCreateFolder(request: RecordedRequest): MockResponse {
            val body = JSONObject(request.body.readUtf8())
            val id = "drv_folder_${idSeq.incrementAndGet()}"
            val parents = body.optJSONArray("parents")
            val parentId = if (parents != null && parents.length() > 0) parents.optString(0, "root") else "root"
            val obj = DriveObj(
                id = id,
                name = body.optString("name"),
                mimeType = body.optString("mimeType", "application/vnd.google-apps.folder"),
                parentId = parentId
            )
            objects[id] = obj
            return MockResponse().setResponseCode(200).setBody(toJson(obj).toString())
        }

        private fun handleMultipartUpload(request: RecordedRequest, path: String, isPatch: Boolean): MockResponse {
            val raw = request.body.readUtf8()
            val firstJsonStart = raw.indexOf('{')
            val metaEndMarker = raw.indexOf("\r\n--", firstJsonStart).takeIf { it > 0 } ?: raw.length
            val metaJson = JSONObject(raw.substring(firstJsonStart, metaEndMarker).trim())
            val payloadSectionStart = raw.indexOf("\r\n\r\n", metaEndMarker)
            val payloadBody = if (payloadSectionStart > 0) {
                val afterHeaders = raw.substring(payloadSectionStart + 4)
                val endIdx = afterHeaders.lastIndexOf("\r\n--")
                if (endIdx >= 0) afterHeaders.substring(0, endIdx) else afterHeaders
            } else ""

            val appProps = metaJson.optJSONObject("appProperties")
            val name = metaJson.optString("name", "record.json")
            val mimeType = metaJson.optString("mimeType", "application/json")
            val parents = metaJson.optJSONArray("parents")
            val parentId = if (parents != null && parents.length() > 0) parents.optString(0, "root") else "root"

            val obj = if (isPatch) {
                val fileId = path.substringAfter("/upload/drive/v3/files/").substringBefore("?")
                val existing = objects[fileId] ?: return MockResponse().setResponseCode(404).setBody("""{"error":{"message":"Not found"}}""")
                existing.apply {
                    this.name = name.ifBlank { this.name }
                    this.payloadText = payloadBody
                }
            } else {
                val id = "drv_file_${idSeq.incrementAndGet()}"
                DriveObj(
                    id = id,
                    name = name,
                    mimeType = mimeType,
                    parentId = parentId,
                    payloadText = payloadBody
                ).also { objects[id] = it }
            }

            if (appProps != null) {
                obj.appId = appProps.optString("appId", appProps.optString("applicationId", obj.appId))
                obj.datasetId = appProps.optString("datasetId", obj.datasetId)
                obj.generationId = appProps.optString("generation_id", appProps.optString("datasetGeneration", obj.generationId))
                obj.recordUuid = appProps.optString("record_uuid", appProps.optString("recordUuid", obj.recordUuid))
                obj.entityType = appProps.optString("entity_type", appProps.optString("recordCategory", obj.entityType))
                obj.revision = appProps.optString("revision", "1").toLongOrNull() ?: obj.revision
                obj.schemaVersion = appProps.optString("schema_version", "24").toIntOrNull() ?: 24
                obj.checksum = appProps.optString("checksum", obj.checksum)
                obj.syncTimestamp = appProps.optString("sync_timestamp", "0").toLongOrNull() ?: obj.syncTimestamp
            }
            return MockResponse().setResponseCode(200).setBody(toJson(obj).toString())
        }

        private fun handleDelete(path: String): MockResponse {
            val fileId = path.substringAfter("/drive/v3/files/").substringBefore("?")
            val existing = objects[fileId]
            return if (existing == null || existing.trashed) {
                MockResponse().setResponseCode(404).setBody("""{"error":{"message":"Not found"}}""")
            } else {
                existing.trashed = true
                MockResponse().setResponseCode(204)
            }
        }

        private fun toJson(obj: DriveObj): JSONObject = JSONObject().apply {
            put("id", obj.id)
            put("name", obj.name)
            put("mimeType", obj.mimeType)
            put("size", obj.payloadText.toByteArray().size.toString())
            put("modifiedTime", "2026-11-19T12:00:00Z")
            put("trashed", obj.trashed)
            put("parents", JSONArray().put(obj.parentId))
            put(
                "appProperties",
                JSONObject().apply {
                    if (obj.appId.isNotBlank()) {
                        put("appId", obj.appId)
                        put("applicationId", obj.appId)
                    }
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
                    put("schema_version", obj.schemaVersion.toString())
                    if (obj.checksum.isNotBlank()) {
                        put("checksum", obj.checksum)
                        put("checksumSha256", obj.checksum)
                    }
                    if (obj.syncTimestamp > 0L) put("sync_timestamp", obj.syncTimestamp.toString())
                }
            )
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        UnitReservationManager.clearAll()
        dbName = "tx_lifecycle_audit_v24_${System.nanoTime()}.db"
        fakeDrive = FakeDriveCloudServer()
        mockWebServer = MockWebServer()
        mockWebServer.dispatcher = fakeDrive.dispatcher()
        mockWebServer.start()

        GoogleDriveAuthManager.markConnectedMetadata(
            context = context,
            email = "audit.sewainaja@gmail.com",
            displayName = "Admin Audit SAF",
            photoUrl = ""
        )
        openDbAndWireEngine()
    }

    private fun openDbAndWireEngine() {
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
        val apiBase = mockWebServer.url("/drive/v3").toString()
        val uploadBase = mockWebServer.url("/upload/drive/v3").toString()
        val restClient = GoogleDriveRestClient(apiBaseUrl = apiBase, uploadBaseUrl = uploadBase)
        repository = FreezerRepository(dao)
        mirrorEngine = GoogleDriveMirrorSyncEngine(
            context = context,
            dao = dao,
            driveClient = restClient,
            accessTokenOverride = "test_access_token_valid_123"
        ).apply {
            autoTriggerSyncOnMutation = false
        }
        repository.attachMirrorSyncEngine(mirrorEngine)
    }

    @After
    fun tearDown() {
        UnitReservationManager.clearAll()
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        if (::mockWebServer.isInitialized) {
            mockWebServer.shutdown()
        }
        context.deleteDatabase(dbName)
    }

    private fun makeCustomer(code: String, name: String, nik: String) = CustomerEntity(
        kodePelanggan = code,
        namaLengkap = name,
        nik = nik,
        jenisKelamin = "Laki-laki",
        nomorHp = "081234567890",
        alamatLengkap = "JL. SLAMET RIYADI NO. 10, SOLO",
        kabupatenKota = "Kota Surakarta",
        kecamatan = "Laweyan",
        tanggalRegistrasi = "2026-11-01"
    )

    private fun makeUnit(code: String, name: String, status: String, price: Long = 350_000L) = FreezerUnitEntity(
        kodeUnit = code,
        namaUnit = name,
        jenisUnit = "Chest Freezer",
        owner = "SOC",
        hargaPembelian = 4_500_000L,
        kapasitasLiter = 300,
        cicilanPerBulan = price,
        merek = "GEA",
        model = "AB-318-R",
        nomorSeri = "SN-$code",
        statusUnit = status,
        lokasiUnit = "Gudang Solo",
        tanggalPembelian = "2026-01-10",
        tanggalPengiriman = "2026-02-01"
    )

    private fun makeUnitItem(
        unit: FreezerUnitEntity,
        index: Int,
        program: String = "Sewa Reguler",
        totalHm: Int = 0,
        keHm: Int = 1,
        price: Long = 350_000L,
        transactionId: Long = 0L,
        customerId: Long = 0L
    ) = TransactionUnitItemEntity(
        transactionId = transactionId,
        customerId = customerId,
        unitId = unit.id,
        unitIndexNumber = index,
        kodeUnitSnapshot = unit.kodeUnit,
        namaUnitSnapshot = unit.namaUnit,
        jenisUnitSnapshot = unit.jenisUnit,
        merekSnapshot = unit.merek,
        kapasitasLiterSnapshot = unit.kapasitasLiter,
        jenisProgram = program,
        totalPeriodeHakMilik = totalHm,
        pembayaranKeHakMilik = keHm,
        statusProgramHakMilik = "Aktif",
        hargaSewa = price,
        durasiHari = 30,
        tanggalMulaiSewa = "2026-11-01",
        tanggalAkhirSewa = "2026-12-01",
        tanggalJatuhTempo = "2026-12-01",
        nominalPembayaran = price,
        statusPembayaran = "Lunas",
        statusPenyewaan = "Dalam Penyewaan"
    )

    private fun makeParentTx(
        custId: Long,
        custName: String,
        primaryUnit: FreezerUnitEntity,
        invoiceNo: String,
        hargaSewa: Long = 350_000L
    ) = RentalTransactionEntity(
        nomorInvoice = invoiceNo,
        tanggalPemesanan = "2026-11-01",
        customerId = custId,
        namaPelangganSnapshot = custName,
        unitId = primaryUnit.id,
        namaUnitSnapshot = primaryUnit.namaUnit,
        kodeUnitSnapshot = primaryUnit.kodeUnit,
        hargaSewa = hargaSewa,
        durasiSewaBulan = 1,
        durasiHari = 30,
        tanggalMulaiSewa = "2026-11-01",
        tanggalAkhirSewa = "2026-12-01",
        tanggalJatuhTempo = "2026-12-01",
        nominalPembayaran = hargaSewa,
        ongkir = 0L,
        deposit = 0L,
        diskon = 0L,
        totalTagihan = hargaSewa,
        statusPembayaran = "Lunas",
        statusPenyewaan = "Dalam Penyewaan",
        metodePembayaran = "Transfer BCA",
        namaPetugas = "Admin Audit SAF"
    )

    // =========================================================================
    // SCENARIOS 1, 2, 3, 4: MULTI-UNIT SEQUENTIAL SELECTION + DUPLICATE REJECTION
    // =========================================================================
    @Test
    fun scenarios1To4_sequentialMultiUnit1_2_3_andRejectDuplicateUnitInSameTransaction() = runBlocking {
        val custId = repository.saveCustomer(makeCustomer("CUST-01", "BUDI SANTOSO", "3372011111110001"))
        val u1Id = repository.saveUnit(makeUnit("SAF-001", "GEA 300L #1", "Tersedia", 350_000L))
        val u2Id = repository.saveUnit(makeUnit("SAF-002", "GEA 300L #2", "Tersedia", 400_000L))
        val u3Id = repository.saveUnit(makeUnit("SAF-003", "GEA 300L #3", "Tersedia", 450_000L))
        val u1 = repository.getUnitById(u1Id)!!
        val u2 = repository.getUnitById(u2Id)!!
        val u3 = repository.getUnitById(u3Id)!!

        // Scenario 1: Create transaction with Unit 1
        val tx1 = repository.saveMultiUnitTransaction(
            parentTransaction = makeParentTx(custId, "BUDI SANTOSO", u1, "INV/SAF/2026/0001", 350_000L),
            unitItems = listOf(makeUnitItem(u1, 1, "Sewa Reguler", 0, 1, 350_000L))
        )
        assertEquals("Disewa", repository.getUnitById(u1Id)!!.statusUnit)
        assertEquals(1, repository.getTransactionUnitItemsByTxOnce(tx1.id).size)

        // Scenario 2 & 3: Create multi-unit transaction with Unit 2 + Unit 3 sequentially
        // First reset u1 to Tersedia or create a fresh 3-unit transaction with u1, u2, u3
        val u4Id = repository.saveUnit(makeUnit("SAF-004", "GEA 300L #4", "Tersedia", 500_000L))
        val u4 = repository.getUnitById(u4Id)!!

        // Scenario 4: Reject duplicate unit (u2 selected twice in same transaction)
        val dupValidation = repository.validateMultiUnitTransaction(
            customerId = custId,
            transactionId = 0L,
            unitItems = listOf(makeUnitItem(u2, 1, customerId = custId), makeUnitItem(u2, 2, customerId = custId))
        )
        assertFalse("Duplicate unit in same transaction must be rejected", dupValidation.isValid)
        assertEquals("ERR_DUPLICATE_UNIT_IN_TX", dupValidation.errorCode)

        // Create 3-unit transaction (Unit 2 + Unit 3 + Unit 4) with mixed programs
        val txMulti = repository.saveMultiUnitTransaction(
            parentTransaction = makeParentTx(custId, "BUDI SANTOSO", u2, "INV/SAF/2026/0002", 1_350_000L),
            unitItems = listOf(
                makeUnitItem(u2, 1, "Sewa Reguler", 0, 1, 400_000L),
                makeUnitItem(u3, 2, "Sewa Hak Milik", 12, 1, 450_000L),
                makeUnitItem(u4, 3, "Sewa Reguler", 0, 1, 500_000L)
            )
        )
        val savedItems = repository.getTransactionUnitItemsByTxOnce(txMulti.id)
        assertEquals(3, savedItems.size)
        assertEquals(1_350_000L, txMulti.totalTagihan)
        assertEquals("Disewa", repository.getUnitById(u2Id)!!.statusUnit)
        assertEquals("Disewa", repository.getUnitById(u3Id)!!.statusUnit)
        assertEquals("Disewa", repository.getUnitById(u4Id)!!.statusUnit)
    }

    // =========================================================================
    // SCENARIOS 5, 6, 7: STRICT AVAILABILITY FILTER & LIVE VALIDATION
    // (REJECT RENTED, LOST/HILANG, REPAIR/PERBAIKAN, AND TIMED RESERVATIONS)
    // =========================================================================
    @Test
    fun scenarios5To7_strictUnitAvailabilityRejectsRentedLostRepairAndReservedUnits() = runBlocking {
        val custId = repository.saveCustomer(makeCustomer("CUST-02", "SITI AMINAH", "3372011111110002"))
        val idTersedia = repository.saveUnit(makeUnit("SAF-AVAIL", "Freezer Tersedia", "Tersedia"))
        val idDisewa = repository.saveUnit(makeUnit("SAF-RENT1", "Freezer Disewa", "Disewa"))
        val idSedangDisewa = repository.saveUnit(makeUnit("SAF-RENT2", "Freezer Sedang Disewa", "Sedang disewa"))
        val idMasihTersewa = repository.saveUnit(makeUnit("SAF-RENT3", "Freezer Masih Tersewa", "Masih Tersewa"))
        val idHilang = repository.saveUnit(makeUnit("SAF-LOST", "Freezer Hilang", "Hilang"))
        val idPerbaikan = repository.saveUnit(makeUnit("SAF-REP1", "Freezer Dalam Perbaikan", "Dalam Perbaikan"))
        val idMaintenance = repository.saveUnit(makeUnit("SAF-REP2", "Freezer Maintenance", "Maintenance"))
        val idRusak = repository.saveUnit(makeUnit("SAF-REP3", "Freezer Rusak", "Rusak"))

        // Strict query MUST return ONLY the 1 "Tersedia" unit
        val strictAvailable = repository.getStrictlyAvailableUnitsOnce()
        assertEquals(1, strictAvailable.size)
        assertEquals(idTersedia, strictAvailable.first().id)

        // Scenario 5: Reject rented units (Disewa, Sedang disewa, Masih Tersewa)
        for (rentedId in listOf(idDisewa, idSedangDisewa, idMasihTersewa)) {
            val u = repository.getUnitById(rentedId)!!
            val res = repository.validateMultiUnitTransaction(
                customerId = custId,
                transactionId = 0L,
                unitItems = listOf(makeUnitItem(u, 1, customerId = custId))
            )
            assertFalse("Unit status '${u.statusUnit}' must be rejected", res.isValid)
            assertEquals("ERR_UNIT_ALREADY_RENTED", res.errorCode)
        }

        // Scenario 6: Reject lost unit (Hilang)
        val uHilang = repository.getUnitById(idHilang)!!
        val resHilang = repository.validateMultiUnitTransaction(
            customerId = custId,
            transactionId = 0L,
            unitItems = listOf(makeUnitItem(uHilang, 1, customerId = custId))
        )
        assertFalse("Unit status 'Hilang' must be rejected", resHilang.isValid)
        assertEquals("ERR_UNIT_LOST", resHilang.errorCode)

        // Scenario 7: Reject repair/maintenance/damaged units
        for (repairId in listOf(idPerbaikan, idMaintenance, idRusak)) {
            val u = repository.getUnitById(repairId)!!
            val res = repository.validateMultiUnitTransaction(
                customerId = custId,
                transactionId = 0L,
                unitItems = listOf(makeUnitItem(u, 1, customerId = custId))
            )
            assertFalse("Unit status '${u.statusUnit}' must be rejected", res.isValid)
            assertEquals("ERR_UNIT_MAINTENANCE", res.errorCode)
        }

        // Temporary Timed Reservation blocks concurrent form session
        val uAvail = repository.getUnitById(idTersedia)!!
        assertTrue(UnitReservationManager.reserveUnit(sessionId = "SESSION_A", unitId = uAvail.id))
        val resConcurrent = repository.validateMultiUnitTransaction(
            customerId = custId,
            transactionId = 0L,
            unitItems = listOf(makeUnitItem(uAvail, 1, customerId = custId)),
            reservationSessionId = "SESSION_B"
        )
        assertFalse("Reserved unit must block another session", resConcurrent.isValid)
        assertEquals("ERR_UNIT_RESERVED", resConcurrent.errorCode)

        // Same session passes validation, and releasing reservation allows SESSION_B
        assertTrue(
            repository.validateMultiUnitTransaction(
                customerId = custId,
                transactionId = 0L,
                unitItems = listOf(makeUnitItem(uAvail, 1, customerId = custId)),
                reservationSessionId = "SESSION_A"
            ).isValid
        )
        UnitReservationManager.releaseSession("SESSION_A")
        assertTrue(
            repository.validateMultiUnitTransaction(
                customerId = custId,
                transactionId = 0L,
                unitItems = listOf(makeUnitItem(uAvail, 1, customerId = custId)),
                reservationSessionId = "SESSION_B"
            ).isValid
        )
    }

    // =========================================================================
    // SCENARIOS 8 & 9: EDIT TRANSACTION WITHOUT DUPLICATE + ADD NEW UNIT
    // PRESERVING EXISTING UNIT ITEMS, PAYMENT ALLOCATIONS & HAK MILIK HISTORY
    // =========================================================================
    @Test
    fun scenarios8And9_editTransactionAndAddUnitPreservesExistingUnitAndHakMilikHistoryWithoutDuplicate() = runBlocking {
        val custId = repository.saveCustomer(makeCustomer("CUST-03", "AGUS PRASETYO", "3372011111110003"))
        val u1Id = repository.saveUnit(makeUnit("SAF-HM-01", "GEA Hak Milik 1", "Tersedia", 500_000L))
        val u2Id = repository.saveUnit(makeUnit("SAF-REG-02", "RSA Reguler 2", "Tersedia", 300_000L))
        val u1 = repository.getUnitById(u1Id)!!
        val u2 = repository.getUnitById(u2Id)!!

        // Initial saved transaction with Unit 1 (Sewa Hak Milik 12x, pembayaran ke-1)
        val initialTx = repository.saveMultiUnitTransaction(
            parentTransaction = makeParentTx(custId, "AGUS PRASETYO", u1, "INV/SAF/2026/0099", 500_000L),
            unitItems = listOf(makeUnitItem(u1, 1, "Sewa Hak Milik", 12, 1, 500_000L))
        )
        val initialItems = repository.getTransactionUnitItemsByTxOnce(initialTx.id)
        assertEquals(1, initialItems.size)
        val originalUnit1ItemId = initialItems.first().id

        // Record a Hak Milik installment payment (allocation + increment pembayaranKeHakMilik -> 2)
        repository.recordUnitHakMilikPayment(
            transactionId = initialTx.id,
            unitItemId = originalUnit1ItemId,
            nominalPembayaran = 500_000L,
            metodePembayaran = "Transfer BCA",
            tanggalPembayaran = "2026-11-15",
            namaPengirim = "AGUS PRASETYO",
            catatanPembayaran = "Cicilan Hak Milik Ke-2",
            incrementPaymentSequence = true
        )
        val allocationsBeforeEdit = dao.getUnitPaymentAllocationsByTxOnce(initialTx.id)
        assertEquals(1, allocationsBeforeEdit.size)
        val unit1AfterInstallment = repository.getTransactionUnitItemsByTxOnce(initialTx.id).first()
        assertEquals(2, unit1AfterInstallment.pembayaranKeHakMilik)

        // Scenario 8 & 9: Edit transaction and ADD Unit 2 (Sewa Reguler) to the SAME parent transaction
        val editedTx = repository.saveMultiUnitTransaction(
            parentTransaction = repository.getTransactionById(initialTx.id)!!.copy(catatan = "EDITED + TAMBAH UNIT 2"),
            unitItems = listOf(
                unit1AfterInstallment,
                makeUnitItem(u2, 2, "Sewa Reguler", 0, 1, 300_000L, transactionId = initialTx.id, customerId = custId)
            )
        )

        // Must NOT create duplicate parent transaction
        assertEquals(initialTx.id, editedTx.id)
        assertEquals(initialTx.uuid, editedTx.uuid)
        assertEquals(1, repository.getAllTransactionsOnce().size)

        // Unit 1 ID, Hak Milik installment progress (ke-2/12), AND payment allocation history MUST be 100% preserved!
        val itemsAfterEdit = repository.getTransactionUnitItemsByTxOnce(editedTx.id)
        assertEquals(2, itemsAfterEdit.size)
        val preservedUnit1 = itemsAfterEdit.first { it.unitId == u1Id }
        assertEquals("Existing unit item primary key must be preserved (no delete/reinsert cascade!)", originalUnit1ItemId, preservedUnit1.id)
        assertEquals("Sewa Hak Milik", preservedUnit1.jenisProgram)
        assertEquals(2, preservedUnit1.pembayaranKeHakMilik)
        assertEquals(12, preservedUnit1.totalPeriodeHakMilik)

        val allocationsAfterEdit = dao.getUnitPaymentAllocationsByTxOnce(editedTx.id)
        assertEquals("Payment allocations for Unit 1 must survive Edit Transaction!", 1, allocationsAfterEdit.size)
        assertEquals(originalUnit1ItemId, allocationsAfterEdit.first().unitItemId)

        // Unit 2 must be attached cleanly and marked Disewa, with recalculated parent total = 500k + 300k = 800k
        val addedUnit2 = itemsAfterEdit.first { it.unitId == u2Id }
        assertEquals("Sewa Reguler", addedUnit2.jenisProgram)
        assertEquals(800_000L, editedTx.totalTagihan)
        assertEquals("Disewa", repository.getUnitById(u1Id)!!.statusUnit)
        assertEquals("Disewa", repository.getUnitById(u2Id)!!.statusUnit)
    }

    // =========================================================================
    // SCENARIOS 10, 11, 12, 13, 14:
    // SOFT-DELETE + SMART UNIT RESTORATION + OFFLINE QUEUE + ONLINE DRIVE SYNC
    // + APP REOPEN CONSISTENCY + BACKUP RESTORE & SYNC NEVER RESURRECTS!
    // =========================================================================
    @Test
    fun scenarios10To14_deleteTransactionSmartUnitRestoreOfflineSyncReopenAndNeverResurrect() = runBlocking {
        val custId = repository.saveCustomer(makeCustomer("CUST-04", "DEWI LESTARI", "3372011111110004"))
        val uFreeId = repository.saveUnit(makeUnit("SAF-DEL-FREE", "Freezer Normal", "Tersedia"))
        val uLostId = repository.saveUnit(makeUnit("SAF-DEL-LOST", "Freezer Jadi Hilang", "Tersedia"))
        val uRepairId = repository.saveUnit(makeUnit("SAF-DEL-REP", "Freezer Jadi Perbaikan", "Tersedia"))
        val uSharedId = repository.saveUnit(makeUnit("SAF-DEL-SHR", "Freezer Aktif Lain", "Tersedia"))

        val uFree = repository.getUnitById(uFreeId)!!
        val uLost = repository.getUnitById(uLostId)!!
        val uRepair = repository.getUnitById(uRepairId)!!
        val uShared = repository.getUnitById(uSharedId)!!

        // Create Transaction A holding 4 units
        val txA = repository.saveMultiUnitTransaction(
            parentTransaction = makeParentTx(custId, "DEWI LESTARI", uFree, "INV/SAF/2026/DEL-A", 1_400_000L),
            unitItems = listOf(
                makeUnitItem(uFree, 1),
                makeUnitItem(uLost, 2),
                makeUnitItem(uRepair, 3),
                makeUnitItem(uShared, 4)
            )
        )
        // Create another active Transaction B that references uShared (simulating overlap guard)
        val txB = repository.saveTransaction(
            makeParentTx(custId, "DEWI LESTARI", uShared, "INV/SAF/2026/KEEP-B", 350_000L)
        )
        // Simulate uLost becoming "Hilang" and uRepair becoming "Dalam Perbaikan" during rental
        repository.saveUnit(repository.getUnitById(uLostId)!!.copy(statusUnit = "Hilang"))
        repository.saveUnit(repository.getUnitById(uRepairId)!!.copy(statusUnit = "Dalam Perbaikan"))

        // Initial online sync to Google Drive so txA exists on cloud
        fakeDrive.networkOffline = false
        mirrorEngine.syncRepository.processSyncQueue()
        val cloudTxBeforeDelete = fakeDrive.activeTransactionFiles()
        assertTrue("txA should exist on Google Drive before delete", cloudTxBeforeDelete.any { it.recordUuid == txA.uuid })

        // Take a backup ZIP BEFORE deleting txA (for Scenario 14 anti-resurrection check!)
        val backupDir = File(context.cacheDir, "audit_backup_${System.nanoTime()}").apply { mkdirs() }
        val preDeleteBackupZip = StructuredBackupEngine.createStructuredBackupZip(
            context = context,
            repository = repository,
            encryptSensitivePayload = false,
            includePhotos = false,
            destinationDir = backupDir,
            customFileName = "pre_delete_txA.zip",
            saveToHistory = false
        ).getOrThrow().second

        // Scenario 10 & 11: Go OFFLINE and delete Transaction A
        fakeDrive.networkOffline = true
        val txABeforeDelete = repository.getTransactionById(txA.id)!!
        repository.deleteTransaction(txABeforeDelete, deletedBy = "Admin Audit SAF")

        // Verify Soft Delete metadata (is_deleted, deleted_at, deleted_by, UUID) & Tombstone
        val rawDeletedRow = dao.getTransactionByIdIncludingDeleted(txA.id)!!
        assertTrue(rawDeletedRow.isDeleted)
        assertTrue(rawDeletedRow.deletedAtMillis > 0L)
        assertEquals("Admin Audit SAF", rawDeletedRow.deletedBy)
        assertEquals(txA.uuid, rawDeletedRow.uuid)
        assertNull("Active transaction query must hide soft-deleted txA", repository.getTransactionById(txA.id))
        assertNotNull("Tombstone must be persisted", dao.getDriveDeletionTombstoneByUuid(txA.uuid))

        // Verify Smart Unit Status Restoration (Scenario 10)
        assertEquals("Free unit must revert to Tersedia", "Tersedia", repository.getUnitById(uFreeId)!!.statusUnit)
        assertEquals("Lost unit must remain Hilang", "Hilang", repository.getUnitById(uLostId)!!.statusUnit)
        assertEquals("Repair unit must remain Dalam Perbaikan", "Dalam Perbaikan", repository.getUnitById(uRepairId)!!.statusUnit)
        assertEquals("Unit in another active txB must remain Disewa", "Disewa", repository.getUnitById(uSharedId)!!.statusUnit)

        // Verify Customer & other transaction B remain intact
        assertNotNull(repository.getCustomerById(custId))
        assertNotNull(repository.getTransactionById(txB))

        // Verify Offline Delete is queued in outbox (`OP_DELETE`)
        val pendingDeleteQueue = repository.getAllDriveLocalSyncQueueOnce()
            .filter { it.recordUuid == txA.uuid && it.operationType == MirrorOutboxCodec.OP_DELETE }
        assertTrue("Offline delete must be queued in sync outbox", pendingDeleteQueue.isNotEmpty())

        // Attempt sync while offline -> stays queued safely
        mirrorEngine.syncRepository.processSyncQueue()
        assertTrue("Cloud still has txA while offline", fakeDrive.activeTransactionFiles().any { it.recordUuid == txA.uuid })

        // Scenario 12: Reconnect ONLINE -> automatic queue processing deletes txA from Google Drive
        fakeDrive.networkOffline = false
        mirrorEngine.syncRepository.processSyncQueue(ignoreBackoffTimers = true)
        assertFalse(
            "Deleted txA MUST be removed from Google Drive once online!",
            fakeDrive.activeTransactionFiles().any { it.recordUuid == txA.uuid }
        )

        // Scenario 13: Reopen database / simulate app restart -> data stays 100% consistent
        database.close()
        openDbAndWireEngine()
        assertNull("Deleted txA must stay deleted after reopen", repository.getTransactionById(txA.id))
        assertEquals("Tersedia", repository.getUnitById(uFreeId)!!.statusUnit)
        assertEquals("Hilang", repository.getUnitById(uLostId)!!.statusUnit)
        assertEquals("Dalam Perbaikan", repository.getUnitById(uRepairId)!!.statusUnit)
        assertNotNull(repository.getTransactionById(txB))

        // Scenario 14: Restore OLD backup (BOTH APPEND_MERGE & FULL_RESTORE) + Sync -> txA NEVER resurrects!
        val mergeRestore = StructuredBackupEngine.executeSafeRestore(
            context = context,
            repository = repository,
            sourceFileOrUri = preDeleteBackupZip,
            mode = RestoreMode.APPEND_MERGE
        )
        assertTrue(mergeRestore.success)
        assertNull("Deleted txA must NEVER resurrect on APPEND_MERGE restore", repository.getTransactionById(txA.id))

        val fullRestore = StructuredBackupEngine.executeSafeRestore(
            context = context,
            repository = repository,
            sourceFileOrUri = preDeleteBackupZip,
            mode = RestoreMode.FULL_RESTORE
        )
        assertTrue(fullRestore.success)
        assertNull("Deleted txA must NEVER resurrect on FULL_RESTORE restore", repository.getTransactionById(txA.id))

        // Sync after restore -> txA still absent locally and on Google Drive
        mirrorEngine.syncRepository.processSyncQueue()
        assertNull(repository.getTransactionById(txA.id))
        assertFalse(fakeDrive.activeTransactionFiles().any { it.recordUuid == txA.uuid })
    }

    // =========================================================================
    // NON-DESTRUCTIVE MIGRATION 23 -> 24 TEST
    // =========================================================================
    @Test
    fun migration23To24_addsDeletedAtAndDeletedByWithoutLosingExistingTransactions() {
        val migDbFile = File(context.cacheDir, "mig_23_24_test_${System.nanoTime()}.db")
        val v23Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(23) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS rental_transactions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                nomorInvoice TEXT NOT NULL,
                                uuid TEXT NOT NULL DEFAULT 'test-uuid-v23',
                                is_deleted INTEGER NOT NULL DEFAULT 0
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO rental_transactions (id, nomorInvoice, uuid, is_deleted) VALUES (7, 'INV/SAF/V23/0007', 'uuid-legacy-007', 0)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        v23Helper.writableDatabase.close()

        val v24Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(24) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        if (oldVersion == 23 && newVersion == 24) {
                            AppDatabase.MIGRATION_23_24.migrate(db)
                        }
                    }
                })
                .build()
        )
        val db24 = v24Helper.writableDatabase
        val cols = mutableSetOf<String>()
        db24.query("PRAGMA table_info('rental_transactions')").use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) cols.add(c.getString(idx))
        }
        assertTrue("deleted_at must exist after MIGRATION_23_24", "deleted_at" in cols)
        assertTrue("deleted_by must exist after MIGRATION_23_24", "deleted_by" in cols)

        db24.query("SELECT nomorInvoice, deleted_at, deleted_by FROM rental_transactions WHERE id = 7").use { c ->
            assertTrue("Existing transaction must survive migration", c.moveToFirst())
            assertEquals("INV/SAF/V23/0007", c.getString(0))
            assertEquals(0L, c.getLong(1))
            assertEquals("", c.getString(2))
        }
        db24.close()
        migDbFile.delete()
    }
}
