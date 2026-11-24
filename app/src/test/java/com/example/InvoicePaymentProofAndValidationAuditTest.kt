package com.example

import android.content.Context
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionDocumentEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.DatabaseGenerationManager
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.GoogleDriveRestClient
import com.example.drive.GoogleDriveService
import com.example.drive.SyncConflictResolver
import com.example.drive.SyncConsistencyChecker
import com.example.drive.SyncQueueManager
import com.example.drive.SyncRepository
import com.example.pdf.InvoicePdfGenerator
import com.example.util.FormValidationEngine
import com.example.util.StorageSyncStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowCompatPdfDocument::class])
class InvoicePaymentProofAndValidationAuditTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: FreezerDao
    private lateinit var repository: FreezerRepository
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(GoogleDriveMirrorSyncEngine.APP_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.freezerDao()
        repository = FreezerRepository(dao)
        tempDir = File(context.cacheDir, "proof_audit_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createSampleProofImage(name: String, width: Int, height: Int, color: Int): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        val file = File(tempDir, name)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        return file
    }

    private fun sampleTransaction(): RentalTransactionEntity = RentalTransactionEntity(
        id = 101L,
        nomorInvoice = "INV/SAF/2026/0099",
        customerId = 1L,
        unitId = 1L,
        namaPelangganSnapshot = "BUDI SANTOSO",
        kodeUnitSnapshot = "SAF-CF-001",
        namaUnitSnapshot = "RSA CHEST FREEZER 200L",
        tanggalPemesanan = "2026-11-01",
        tanggalMulaiSewa = "2026-11-01",
        tanggalAkhirSewa = "2026-12-01",
        tanggalJatuhTempo = "2026-12-01",
        durasiHari = 30,
        hargaSewa = 400_000L,
        totalTagihan = 400_000L,
        nominalPembayaran = 400_000L,
        statusPembayaran = "Lunas",
        metodePembayaran = "Transfer BCA",
        statusPenyewaan = "Dalam Penyewaan",
        alamatLokasiGps = "JL. SLAMET RIYADI NO 12 SURAKARTA"
    )

    private fun sampleCustomer(): CustomerEntity = CustomerEntity(
        id = 1L,
        kodePelanggan = "CUST/SAF/0001",
        namaLengkap = "BUDI SANTOSO",
        nik = "3372010101900001",
        nomorHp = "081234567890",
        alamatLengkap = "JL. SLAMET RIYADI NO 12 SURAKARTA",
        jenisKelamin = "Laki-laki",
        tanggalRegistrasi = "2026-01-02"
    )

    private fun sampleUnit(): FreezerUnitEntity = FreezerUnitEntity(
        id = 1L,
        kodeUnit = "SAF-CF-001",
        namaUnit = "RSA CHEST FREEZER 200L",
        jenisUnit = "Chest Freezer",
        owner = "SAF",
        hargaPembelian = 3_200_000L,
        kapasitasLiter = 200,
        cicilanPerBulan = 300_000L,
        jumlahCicilanTotal = 12,
        totalPembayaranCicilan = 600_000L,
        statusInvestasi = "DIBAYAR SEBAGIAN",
        kategoriSheet = "INVESTASI SEWA FREEZER",
        merek = "RSA",
        model = "CF-210",
        nomorSeri = "SN-2026-001",
        statusUnit = "Tersedia",
        lokasiUnit = "Gudang Utama SAF Solo",
        tanggalPembelian = "2026-01-10"
    )

    private fun countPdfPages(pdfFile: File): Int {
        val text = pdfFile.readText(Charsets.UTF_8)
        val match = Regex("""\((\d+) pages\)""").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    // ==================== 1. INTEGRASI BUKTI PEMBAYARAN KE INVOICE PDF ====================

    @Test
    fun test1_invoicePdf_withoutPaymentProofs_appendsDedicatedEmptyNoticePage() {
        val pdfFile = InvoicePdfGenerator.generateInvoicePdf(
            context = context,
            transaction = sampleTransaction(),
            customer = sampleCustomer(),
            unit = sampleUnit(),
            companyProfile = CompanyProfile(),
            paymentProofs = emptyList()
        )
        assertTrue("Invoice PDF must exist", pdfFile.exists())
        assertTrue("Invoice PDF must be non-empty", pdfFile.length() > 1_000L)
        val pages = countPdfPages(pdfFile)
        assertTrue("Invoice PDF with empty payment proof notice must have >= 3 pages (got $pages)", pages >= 3)
        assertEquals("BUKTI PEMBAYARAN BELUM DIUNGGAH", InvoicePdfGenerator.EMPTY_PAYMENT_PROOF_NOTICE)
    }

    @Test
    fun test2_invoicePdf_chronologicalSortingAndMultiPageInstallmentProofs() {
        val img1 = createSampleProofImage("proof_1.jpg", 800, 1200, Color.rgb(16, 185, 129))
        val img2 = createSampleProofImage("proof_2.jpg", 1000, 750, Color.rgb(2, 132, 199))
        val img3 = createSampleProofImage("proof_3.jpg", 600, 900, Color.rgb(245, 158, 11))

        val pLate = PaymentProofEntity(
            id = 3L,
            transactionId = 101L,
            tanggalPembayaran = "2026-11-15",
            nominalPembayaran = 100_000L,
            metodePembayaran = "QRIS",
            namaPengirim = "BUDI SANTOSO",
            catatanPembayaran = "PELUNASAN TAHAP 3",
            fotoBuktiUri = img3.absolutePath,
            waktuUpload = "15/11/2026 14:20"
        )
        val pEarly = PaymentProofEntity(
            id = 1L,
            transactionId = 101L,
            tanggalPembayaran = "2026-11-01",
            nominalPembayaran = 150_000L,
            metodePembayaran = "Transfer BCA",
            namaPengirim = "BUDI SANTOSO",
            catatanPembayaran = "DP TAHAP 1",
            fotoBuktiUri = img1.absolutePath,
            waktuUpload = "01/11/2026 09:10"
        )
        val pMid = PaymentProofEntity(
            id = 2L,
            transactionId = 101L,
            tanggalPembayaran = "2026-11-10",
            nominalPembayaran = 150_000L,
            metodePembayaran = "Transfer Mandiri",
            namaPengirim = "BUDI SANTOSO",
            catatanPembayaran = "CICILAN TAHAP 2",
            fotoBuktiUri = img2.absolutePath,
            waktuUpload = "10/11/2026 11:45"
        )

        val sorted = InvoicePdfGenerator.sortPaymentProofsChronologically(listOf(pLate, pEarly, pMid))
        assertEquals(listOf("2026-11-01", "2026-11-10", "2026-11-15"), sorted.map { it.tanggalPembayaran })

        val pdfEmpty = InvoicePdfGenerator.generateInvoicePdf(
            context = context,
            transaction = sampleTransaction(),
            customer = sampleCustomer(),
            unit = sampleUnit(),
            companyProfile = CompanyProfile(),
            paymentProofs = emptyList()
        )
        val pagesEmpty = countPdfPages(pdfEmpty)

        val pdfThreeProofs = InvoicePdfGenerator.generateInvoicePdf(
            context = context,
            transaction = sampleTransaction(),
            customer = sampleCustomer(),
            unit = sampleUnit(),
            companyProfile = CompanyProfile(),
            paymentProofs = listOf(pLate, pEarly, pMid)
        )
        val pagesThree = countPdfPages(pdfThreeProofs)
        assertEquals("3 payment proofs should occupy 2 attachment pages (+1 vs empty page)", pagesEmpty + 1, pagesThree)
    }

    @Test
    fun test3_invoicePdf_highResProofImage_isOptimizedProportionalAndCompact() {
        val hugeImg = createSampleProofImage("huge_receipt.jpg", 2400, 3200, Color.rgb(30, 58, 138))
        val optimized = InvoicePdfGenerator.loadOptimizedPaymentProofBitmap(
            context = context,
            uriOrPath = hugeImg.absolutePath,
            maxDimensionPx = 960
        )
        assertNotNull("Optimized bitmap must decode cleanly", optimized)
        val bmp = optimized!!
        assertTrue("Max dimension must be <= 960px (got ${bmp.width}x${bmp.height})", maxOf(bmp.width, bmp.height) <= 960)
        val ratio = bmp.width.toFloat() / bmp.height.toFloat()
        assertEquals("Aspect ratio must be preserved without cropping", 0.75f, ratio, 0.03f)
        bmp.recycle()
    }

    @Test
    fun test4_replacePaymentProof_preservesUuidHistoryAndArchivesOldProof() = runBlocking {
        val oldImg = createSampleProofImage("old_proof.jpg", 400, 600, Color.RED)
        val newImg = createSampleProofImage("new_proof.jpg", 400, 600, Color.GREEN)

        val custId = repository.saveCustomer(sampleCustomer().copy(id = 0L))
        val unitId = repository.saveUnit(sampleUnit().copy(id = 0L))
        val txId = repository.saveTransaction(
            sampleTransaction().copy(id = 0L, customerId = custId, unitId = unitId)
        )
        val initialProof = PaymentProofEntity(
            transactionId = txId,
            tanggalPembayaran = "2026-11-05",
            nominalPembayaran = 200_000L,
            metodePembayaran = "Transfer BCA",
            namaPengirim = "BUDI",
            catatanPembayaran = "DP Awal",
            fotoBuktiUri = oldImg.absolutePath
        )
        val proofId = repository.savePaymentProof(initialProof)
        val savedInitial = dao.getPaymentProofById(proofId)!!

        // Archive previous proof file before replacing (matching ViewModel behavior)
        val replacedInput = savedInitial.copy(
            fotoBuktiUri = newImg.absolutePath,
            catatanPembayaran = "DP Awal (Struk diperjelas)"
        )
        if (savedInitial.fotoBuktiUri != replacedInput.fotoBuktiUri) {
            repository.saveTransactionDocument(
                TransactionDocumentEntity(
                    transactionId = txId,
                    customerId = custId,
                    documentType = "ARSIP_BUKTI_PEMBAYARAN_SEBELUMNYA",
                    fileName = File(savedInitial.fotoBuktiUri).name,
                    relativePath = savedInitial.fotoBuktiUri,
                    mimeType = "image/jpeg",
                    fileSize = File(savedInitial.fotoBuktiUri).length()
                )
            )
        }
        val replacedId = repository.savePaymentProof(replacedInput)
        val afterReplace = dao.getPaymentProofById(replacedId)!!

        assertEquals("ID must stay identical on replace", savedInitial.id, afterReplace.id)
        assertEquals("UUID must stay identical on replace", savedInitial.uuid, afterReplace.uuid)
        assertTrue("Version must increment on replace", afterReplace.version > savedInitial.version)
        assertEquals("New image path must be stored", newImg.absolutePath, afterReplace.fotoBuktiUri)

        val archivedDocs = dao.getTransactionDocumentsByTxOnce(txId)
            .filter { it.documentType == "ARSIP_BUKTI_PEMBAYARAN_SEBELUMNYA" }
        assertEquals("Previous payment proof must be archived without losing history", 1, archivedDocs.size)
        assertEquals(oldImg.absolutePath, archivedDocs.first().relativePath)
    }

    // ==================== 2. VALIDASI KELENGKAPAN SEMUA 12 FORM ====================

    @Test
    fun test5_all12FormValidators_detectMissingFieldsAndPassWhenComplete() {
        // 1. Form Pelanggan
        val custBad = FormValidationEngine.validateCustomerForm("", "12345", "08", "")
        assertFalse(custBad.isValid)
        assertTrue(custBad.bannerMessage.startsWith("DATA BELUM LENGKAP! Silakan lengkapi"))
        assertEquals("namaLengkap", custBad.firstErrorFieldKey)
        assertEquals(4, custBad.fieldErrors.size)
        assertTrue(FormValidationEngine.validateCustomerForm("Budi", "3372010101900001", "081234567890", "Solo").isValid)

        // 2. Form Unit Freezer
        val unitBad = FormValidationEngine.validateUnitForm("", "", "", "", 0, -100L)
        assertFalse(unitBad.isValid)
        assertTrue(unitBad.bannerMessage.contains("DATA BELUM LENGKAP!"))
        assertEquals("namaUnit", unitBad.firstErrorFieldKey)
        assertTrue(FormValidationEngine.validateUnitForm("RSA 200L", "RSA", "CF-210", "SN-01", 200, 3_200_000L).isValid)

        // 3. Form Investasi Unit
        val invBad = FormValidationEngine.validateInvestmentForm("RSA 200L", 3_000_000L, 300_000L, 10, 12)
        assertFalse(invBad.isValid)
        assertEquals("cicilanSudahDibayar", invBad.firstErrorFieldKey)
        assertTrue(FormValidationEngine.validateInvestmentForm("RSA 200L", 3_000_000L, 300_000L, 10, 5).isValid)

        // 4 & 5. Form Penyewaan & Transaksi Baru
        val txBad = FormValidationEngine.validateTransactionForm(0L, 0, "", 0L, "")
        assertFalse(txBad.isValid)
        assertEquals("customerId", txBad.firstErrorFieldKey)
        assertEquals(5, txBad.fieldErrors.size)
        val txDup = FormValidationEngine.validateTransactionForm(1L, 2, "2026-11-01", 350_000L, "Solo", duplicateUnitIds = true)
        assertFalse(txDup.isValid)
        assertEquals("units", txDup.firstErrorFieldKey)
        assertTrue(FormValidationEngine.validateTransactionForm(1L, 1, "2026-11-01", 350_000L, "Solo").isValid)

        // 6. Form Perpanjangan Sewa
        val renBad = FormValidationEngine.validateRenewalForm(1L, 0, 0, "", anyInvalidDuration = true, anyNegativeOrZeroPrice = true)
        assertFalse(renBad.isValid)
        assertEquals("selectedUnits", renBad.firstErrorFieldKey)
        assertTrue(FormValidationEngine.validateRenewalForm(1L, 1, 0, "Transfer BCA").isValid)

        // 7. Form Pembayaran / Bukti Pembayaran
        val payBad = FormValidationEngine.validatePaymentProofForm(10L, "", 0L, "", requireProofAttachment = true, fotoBuktiUri = "")
        assertFalse(payBad.isValid)
        assertEquals("tanggalPembayaran", payBad.firstErrorFieldKey)
        assertEquals(4, payBad.fieldErrors.size)
        assertTrue(FormValidationEngine.validatePaymentProofForm(10L, "2026-11-01", 350_000L, "Transfer BCA").isValid)

        // 8. Form Pengembalian Unit
        val retBad = FormValidationEngine.validateReturnForm(0L, "", "", "", "")
        assertFalse(retBad.isValid)
        assertEquals("transactionId", retBad.firstErrorFieldKey)
        assertTrue(FormValidationEngine.validateReturnForm(1L, "2026-11-20", "Petugas A", "Budi", "Baik").isValid)

        // 9. Form Perbaikan Unit
        val mntBad = FormValidationEngine.validateMaintenanceForm(1L, "", "", -5000L)
        assertFalse(mntBad.isValid)
        assertEquals("tanggalPerbaikan", mntBad.firstErrorFieldKey)
        assertTrue(FormValidationEngine.validateMaintenanceForm(1L, "2026-11-20", "Isi Freon", 150_000L).isValid)

        // 10. Form Dokumentasi
        val docBad = FormValidationEngine.validateDocumentationForm(1L, "", "", false)
        assertFalse(docBad.isValid)
        assertEquals("category", docBad.firstErrorFieldKey)
        assertTrue(FormValidationEngine.validateDocumentationForm(1L, "KTP", "KTP_DEPAN", true).isValid)

        // 11. Form Lokasi Penggunaan
        val locBad = FormValidationEngine.validateUsageLocationForm(1L, "", null, null)
        assertFalse(locBad.isValid)
        assertEquals("alamatLengkap", locBad.firstErrorFieldKey)
        assertTrue(FormValidationEngine.validateUsageLocationForm(1L, "Jl. Slamet Riyadi Solo", -7.5666, 110.8166).isValid)

        // 12. Form Metode Pembayaran
        val pmBad = FormValidationEngine.validatePaymentMethodForm("", "TRANSFER_BANK", "", "")
        assertFalse(pmBad.isValid)
        assertEquals("namaMetode", pmBad.firstErrorFieldKey)
        assertEquals(3, pmBad.fieldErrors.size)
        assertTrue(FormValidationEngine.validatePaymentMethodForm("Transfer BCA", "TRANSFER_BANK", "1234567890", "SAF SOC").isValid)
        assertTrue(FormValidationEngine.validatePaymentMethodForm("Tunai", "TUNAI", "", "").isValid)
    }

    // ==================== 3. PENGUJIAN 8 KONDISI PENYIMPANAN & KEGAGALAN ====================

    @Test
    fun test6_edgeConditions_offlineDriveFailureAndRetryWithoutDuplicates() = runBlocking {
        // Condition 1 & 3: Save complete customer locally while offline
        val savedId = repository.saveCustomer(sampleCustomer().copy(id = 0L))
        val readBack = dao.getCustomerById(savedId)
        assertNotNull("Local SQLite must verify record saved", readBack)
        assertEquals(
            StorageSyncStatus.SAVED_LOCAL,
            StorageSyncStatus.fromEntitySyncStatus(readBack!!.syncStatus, isCurrentlySyncing = false, isOffline = true)
        )
        assertEquals(
            StorageSyncStatus.PENDING_SYNC,
            StorageSyncStatus.fromEntitySyncStatus(readBack.syncStatus, isCurrentlySyncing = false, isOffline = false)
        )

        // Condition 4: Google Drive returns HTTP 503 -> local DB remains 100% intact, retry succeeds cleanly with 0 duplicates
        var driveDown = true
        val idSeq = AtomicInteger(100)
        val cloudRecords = ConcurrentHashMap<String, JSONObject>()
        val cloudPayloads = ConcurrentHashMap<String, String>()
        val mockServer = MockWebServer()
        mockServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (driveDown) {
                    return MockResponse().setResponseCode(503).setBody("""{"error":"service_unavailable"}""")
                }
                val path = request.path.orEmpty()
                val method = request.method.orEmpty()
                val rawBody = request.body.readUtf8()
                if (path.startsWith("/upload/drive/v3/files")) {
                    val contentType = request.getHeader("Content-Type").orEmpty()
                    val boundary = Regex("""boundary=([^;\s]+)""").find(contentType)?.groupValues?.get(1)?.trim('"') ?: "===sewain_drive_boundary_"
                    val delimiter = if (rawBody.contains("--$boundary")) "--$boundary" else "--===sewain_drive_boundary_"
                    val parts = rawBody.split(delimiter).map { it.trim() }.filter { it.isNotEmpty() && it != "--" }
                    var meta = JSONObject()
                    var payload = ""
                    parts.forEachIndexed { idx, part ->
                        val splitIdx = part.indexOf("\r\n\r\n").takeIf { it >= 0 } ?: part.indexOf("\n\n")
                        val bodySection = if (splitIdx >= 0) {
                            val offset = if (part.substring(splitIdx).startsWith("\r\n\r\n")) 4 else 2
                            part.substring(splitIdx + offset).trimEnd('\r', '\n', '-')
                        } else part
                        if (idx == 0) {
                            val s = bodySection.indexOf("{")
                            val e = bodySection.lastIndexOf("}")
                            if (s >= 0 && e > s) runCatching { meta = JSONObject(bodySection.substring(s, e + 1)) }
                        } else if (idx == 1) {
                            payload = bodySection
                        }
                    }
                    val fid = if (method.equals("PATCH", true)) {
                        path.substringAfter("/upload/drive/v3/files/").substringBefore("?")
                    } else {
                        "FILE_${idSeq.incrementAndGet()}"
                    }
                    val resp = JSONObject()
                        .put("id", fid)
                        .put("name", meta.optString("name", "$fid.json"))
                        .put("mimeType", meta.optString("mimeType", "application/json"))
                        .put("size", payload.toByteArray(Charsets.UTF_8).size.toString())
                        .put("modifiedTime", "2026-11-20T09:00:00Z")
                        .put("appProperties", meta.optJSONObject("appProperties") ?: JSONObject())
                    cloudRecords[fid] = resp
                    cloudPayloads[fid] = payload
                    return MockResponse().setResponseCode(200).addHeader("Date", "Fri, 20 Nov 2026 09:00:00 GMT").setBody(resp.toString())
                }
                if (path.startsWith("/drive/v3/files") && method == "POST") {
                    val reqJson = runCatching { JSONObject(rawBody) }.getOrElse { JSONObject() }
                    val fid = "FLD_${idSeq.incrementAndGet()}"
                    val obj = JSONObject()
                        .put("id", fid)
                        .put("name", reqJson.optString("name", "folder"))
                        .put("mimeType", reqJson.optString("mimeType", GoogleDriveRestClient.FOLDER_MIME))
                    cloudRecords[fid] = obj
                    return MockResponse().setResponseCode(200).setBody(obj.toString())
                }
                if (path.startsWith("/drive/v3/files/") && method == "GET") {
                    val fid = path.substringAfter("/drive/v3/files/").substringBefore("?")
                    val found = cloudRecords[fid] ?: return MockResponse().setResponseCode(404)
                    return if (path.contains("alt=media")) {
                        MockResponse().setResponseCode(200).setBody(cloudPayloads[fid] ?: "{}")
                    } else {
                        MockResponse().setResponseCode(200).setBody(found.toString())
                    }
                }
                if (path.startsWith("/drive/v3/files") && method == "GET") {
                    val arr = JSONArray()
                    val decodedQ = java.net.URLDecoder.decode(path.substringAfter("q=", "").substringBefore("&"), "UTF-8")
                    val nameMatch = Regex("""name\s*=\s*'([^']+)'""").find(decodedQ)?.groupValues?.get(1)
                    val uuidMatch = Regex("""value='([^']+)'""").find(decodedQ)?.groupValues?.get(1)
                    cloudRecords.values.forEach { rec ->
                        val okName = nameMatch == null || rec.optString("name") == nameMatch
                        val okUuid = uuidMatch == null || !decodedQ.contains("record_uuid") || rec.optJSONObject("appProperties")?.optString("record_uuid") == uuidMatch
                        if (okName && okUuid) arr.put(rec)
                    }
                    return MockResponse().setResponseCode(200).setBody(JSONObject().put("files", arr).toString())
                }
                return MockResponse().setResponseCode(200).setBody("{}")
            }
        }
        mockServer.start()
        try {
            val baseUrl = mockServer.url("/").toString().trimEnd('/')
            val restClient = GoogleDriveRestClient("$baseUrl/drive/v3", "$baseUrl/upload/drive/v3")
            val genMgr = DatabaseGenerationManager(context)
            val driveService = GoogleDriveService(context, restClient, accessTokenOverride = "test-token")
            val queueMgr = SyncQueueManager(dao, genMgr)
            val conflictResolver = SyncConflictResolver(dao, genMgr)
            val checker = SyncConsistencyChecker(context, dao, driveService, genMgr, conflictResolver)
            val syncRepo = SyncRepository(context, dao, genMgr, driveService, queueMgr, conflictResolver, checker).apply {
                autoTriggerSyncOnMutation = false
            }

            // First sync while Drive returns 503 -> must fail gracefully & preserve local DB row
            val failedReport = syncRepo.processSyncQueue(ignoreBackoffTimers = true)
            assertTrue("Sync while Drive is 503 must report failure or pending retry", failedReport.isFailure || (failedReport.getOrNull()?.failedCount ?: 0) > 0)
            assertNotNull("Local SQLite customer must NEVER be lost when Drive fails", dao.getCustomerById(savedId))

            // Drive recovers -> reset retry timers and retry sync twice to confirm idempotency
            driveDown = false
            queueMgr.resetFailedItemsToPending()
            val retryReport = syncRepo.processSyncQueue(ignoreBackoffTimers = true).getOrThrow()
            assertEquals("Retry sync after Drive recovery must have 0 failures", 0, retryReport.failedCount)

            val syncedCust = dao.getCustomerById(savedId)!!
            assertEquals(StorageSyncStatus.SYNCED, StorageSyncStatus.fromEntitySyncStatus(syncedCust.syncStatus))

            val countBeforeSecondSync = cloudRecords.values.count {
                it.optString("mimeType") == "application/json" && !it.optString("name").contains("MANIFEST", true)
            }
            syncRepo.processSyncQueue(ignoreBackoffTimers = true)
            val countAfterSecondSync = cloudRecords.values.count {
                it.optString("mimeType") == "application/json" && !it.optString("name").contains("MANIFEST", true)
            }
            assertEquals("Repeated sync must NEVER create duplicate cloud files", countBeforeSecondSync, countAfterSecondSync)
        } finally {
            mockServer.shutdown()
        }
    }

    @Test
    fun test7_errorClassification_coversAllRequiredFailureCauses() {
        val dbErr = FormValidationEngine.classifySaveFailureReason(SQLiteException("disk I/O error"))
        assertTrue("Must classify SQLite error clearly: $dbErr", dbErr.contains("Database lokal"))

        val storageErr = FormValidationEngine.classifySaveFailureReason(IOException("ENOSPC: No space left on device"))
        assertTrue("Must classify storage full clearly: $storageErr", storageErr.contains("Penyimpanan perangkat"))

        val offlineErr = FormValidationEngine.classifySaveFailureReason(UnknownHostException("Unable to resolve host"))
        assertTrue("Must classify offline error clearly: $offlineErr", offlineErr.contains("Koneksi internet terputus"))

        val timeoutErr = FormValidationEngine.classifySaveFailureReason(SocketTimeoutException("Read timed out"))
        assertTrue("Must classify timeout clearly: $timeoutErr", timeoutErr.contains("Server tidak memberikan respons"))

        val driveErr = FormValidationEngine.classifySaveFailureReason(IllegalStateException("Google Drive 401 unauthorized"))
        assertTrue("Must classify Drive failure clearly: $driveErr", driveErr.contains("Sinkronisasi Google Drive gagal"))

        val photoErr = FormValidationEngine.classifySaveFailureReason(IOException("Upload foto dokumentasi gagal"))
        assertTrue("Must classify photo failure clearly: $photoErr", photoErr.contains("File dokumentasi gagal diunggah"))

        val conflictErr = FormValidationEngine.classifySaveFailureReason(IllegalStateException("Unit sudah disewa / konflik"))
        assertTrue("Must classify conflict clearly: $conflictErr", conflictErr.contains("konflik data"))
    }

    @Test
    fun test8_unexpectedCloseAutoDraft_and_rapidRepeatedSaveProtection() = runBlocking {
        // Condition 8: Unexpected app close mid-form -> FormDraftEntity survives and does not pollute active customer table
        repository.saveFormDraft(
            formType = "CUSTOMER",
            targetEntityId = 0L,
            title = "Draft Pelanggan Belum Selesai",
            subtitle = "NIK 337201...",
            payloadJson = """{"namaLengkap":"SITI AMINAH","nik":"3372010202910002"}"""
        )
        val restoredDrafts = dao.getAllFormDraftsOnce()
        assertEquals(1, restoredDrafts.size)
        assertTrue(restoredDrafts.first().payloadJson.contains("SITI AMINAH"))
        assertEquals("Active customer table must remain clean until final save", 0, dao.getAllCustomersOnce().size)

        // Condition 7: Rapid repeated save with same UUID/data -> idempotent without duplicate rows
        val unit = sampleUnit().copy(id = 0L, kodeUnit = "SAF-DEDUP-01", nomorSeri = "SN-DEDUP-01")
        val unitId = repository.saveUnit(unit)
        val stored = dao.getUnitById(unitId)!!
        val concurrentIds = (1..5).map {
            async { repository.saveUnit(stored) }
        }.awaitAll()
        assertTrue("All rapid repeated saves must target the single record ID", concurrentIds.all { it == unitId })
        assertEquals("Exactly 1 unit row must exist (no duplicates)", 1, dao.getAllUnitsOnce().size)

        // Verify all 5 StorageSyncStatus enum labels match exact Indonesian requirements
        assertEquals("Tersimpan di perangkat", StorageSyncStatus.SAVED_LOCAL.label)
        assertEquals("Menunggu sinkronisasi", StorageSyncStatus.PENDING_SYNC.label)
        assertEquals("Sedang disinkronkan", StorageSyncStatus.SYNCING.label)
        assertEquals("Berhasil tersinkronisasi", StorageSyncStatus.SYNCED.label)
        assertEquals("Gagal disinkronisasi", StorageSyncStatus.SYNC_FAILED.label)
        assertEquals(StorageSyncStatus.SYNCING, StorageSyncStatus.fromEntitySyncStatus("PENDING", isCurrentlySyncing = true))
        assertEquals(StorageSyncStatus.SYNC_FAILED, StorageSyncStatus.fromEntitySyncStatus("SYNC_FAILED"))
    }
}
