package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.DrivePhotoCompressionConfig
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.DriveConnectionStatus
import com.example.drive.DriveDocumentationCategory
import com.example.drive.DriveFolderTreeSummary
import com.example.drive.DriveUploadProgressState
import com.example.drive.GoogleDriveAuthManager
import com.example.drive.GoogleDrivePhotoCenterManager
import com.example.drive.GoogleDriveRestClient
import com.example.ui.components.DrivePhotoStorageSettingsCard
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class GoogleDrivePhotoCenterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: FreezerRepository
    private lateinit var fakeDriveClient: GoogleDriveRestClient

    // Simulated Google Drive state (in-memory folders & files)
    private data class SimulatedDriveFolder(
        val id: String,
        val name: String,
        val parentId: String?
    )

    private data class SimulatedDriveFile(
        val id: String,
        val name: String,
        val parentFolderId: String,
        val bytes: ByteArray,
        val checksumSha256: String
    )

    private val simulatedFolders = ConcurrentHashMap<String, SimulatedDriveFolder>()
    private val simulatedFiles = ConcurrentHashMap<String, SimulatedDriveFile>()
    private var idSequence = 100

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FreezerRepository(db.freezerDao())

        simulatedFolders.clear()
        simulatedFiles.clear()
        GoogleDrivePhotoCenterManager.clearFolderCache(context)

        GoogleDriveAuthManager.markConnectedMetadata(
            context = context,
            email = "owner.sewainaja@gmail.com",
            displayName = "Owner SEWAin Aja Freezer SOC",
            photoUrl = ""
        )

        val mockOkHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val method = req.method

                // 1. Folder lookup or file listing: GET /drive/v3/files?q=...
                if (method == "GET" && url.contains("/drive/v3/files") && !url.contains("alt=media")) {
                    val query = req.url.queryParameter("q").orEmpty()
                    val isFolderQuery = query.contains("application/vnd.google-apps.folder")
                    val filesJson = JSONArray()
                    if (isFolderQuery) {
                        val nameMatch = Regex("name\\s*=\\s*'([^']+)'").find(query)?.groupValues?.get(1)
                        val parentMatch = Regex("'([^']+)'\\s+in\\s+parents").find(query)?.groupValues?.get(1)
                        simulatedFolders.values.filter { folder ->
                            (nameMatch == null || folder.name == nameMatch) &&
                                (parentMatch == null || folder.parentId == parentMatch) &&
                                (parentMatch != null || folder.parentId == null)
                        }.forEach { folder ->
                            filesJson.put(
                                JSONObject().apply {
                                    put("id", folder.id)
                                    put("name", folder.name)
                                }
                            )
                        }
                    }
                    val root = JSONObject().put("files", filesJson)
                    return@addInterceptor Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(root.toString().toResponseBody("application/json".toMediaType()))
                        .build()
                }

                // 2. Multipart file upload: POST /upload/drive/v3/files?uploadType=multipart...
                if (method == "POST" && url.contains("uploadType=multipart")) {
                    val multipart = req.body as? MultipartBody
                    var name = "PHOTO.jpg"
                    var parentId = ""
                    var sha = ""
                    var uploadedBytes = ByteArray(128) { 0x55 }

                    if (multipart != null && multipart.parts.size >= 2) {
                        val metaBuf = okio.Buffer()
                        multipart.part(0).body.writeTo(metaBuf)
                        val metaObj = JSONObject(metaBuf.readUtf8())
                        name = metaObj.optString("name", "PHOTO.jpg")
                        parentId = metaObj.optJSONArray("parents")?.optString(0).orEmpty()
                        sha = metaObj.optJSONObject("appProperties")?.optString("checksumSha256").orEmpty()

                        val fileBuf = okio.Buffer()
                        multipart.part(1).body.writeTo(fileBuf)
                        uploadedBytes = fileBuf.readByteArray()
                    }

                    val fileId = "drive_photo_${++idSequence}"
                    simulatedFiles[fileId] = SimulatedDriveFile(
                        id = fileId,
                        name = name,
                        parentFolderId = parentId,
                        bytes = uploadedBytes,
                        checksumSha256 = sha
                    )
                    val respJson = JSONObject().apply {
                        put("id", fileId)
                        put("name", name)
                        put("size", uploadedBytes.size.toString())
                        put("createdTime", "2026-11-16T08:30:00Z")
                        put("appProperties", JSONObject().put("checksumSha256", sha))
                    }
                    return@addInterceptor Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(respJson.toString().toResponseBody("application/json".toMediaType()))
                        .build()
                }

                // 3. Folder creation: POST /drive/v3/files?fields=id,name
                if (method == "POST" && url.contains("/drive/v3/files")) {
                    val buf = okio.Buffer()
                    req.body?.writeTo(buf)
                    val bodyObj = JSONObject(buf.readUtf8())
                    val name = bodyObj.optString("name", "FOLDER")
                    val parentId = bodyObj.optJSONArray("parents")?.optString(0)
                    val newId = "folder_${++idSequence}_${name.replace(Regex("[^A-Za-z0-9]"), "_")}"
                    simulatedFolders[newId] = SimulatedDriveFolder(
                        id = newId,
                        name = name,
                        parentId = parentId
                    )
                    val respJson = JSONObject().apply {
                        put("id", newId)
                        put("name", name)
                    }
                    return@addInterceptor Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(respJson.toString().toResponseBody("application/json".toMediaType()))
                        .build()
                }

                // 4. Download file bytes: GET /drive/v3/files/{id}?alt=media
                if (method == "GET" && url.contains("alt=media")) {
                    val fileId = req.url.pathSegments.lastOrNull().orEmpty()
                    val stored = simulatedFiles[fileId]
                    return@addInterceptor if (stored != null) {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(stored.bytes.toResponseBody("image/jpeg".toMediaType()))
                            .build()
                    } else {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("Not found".toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        fakeDriveClient = GoogleDriveRestClient(client = mockOkHttp)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createSampleJpegFile(fileName: String, width: Int = 2400, height: Int = 1800, seedColor: Int = Color.BLUE): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(seedColor)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
        }
        canvas.drawText("SEWAin Aja Freezer SOC - $fileName", 80f, 200f, paint)
        val outFile = File(context.cacheDir, fileName)
        FileOutputStream(outFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        bmp.recycle()
        return outFile
    }

    @Test
    fun testA_uploadUnitFreezerPhotosCreatesExactFolderHierarchyAndAutoNamesFiles() = runBlocking {
        // 1. Provision full folder structure twice and verify NO duplicate folders are created on Google Drive
        val firstTree = GoogleDrivePhotoCenterManager.ensureFullDriveDocumentationHierarchy(
            context = context,
            driveClient = fakeDriveClient,
            explicitAccessToken = "ya29.test_drive_photo_center_token",
            yearsToProvision = listOf("2026", "2027")
        ).getOrThrow()

        val folderCountAfterFirstRun = simulatedFolders.size
        assertTrue("Folder tree must be verified on Drive", firstTree.isVerifiedOnDrive)

        // Clear memory cache to force querying Drive API again and ensure no duplicate folders are created
        GoogleDrivePhotoCenterManager.clearFolderCache()
        val secondTree = GoogleDrivePhotoCenterManager.ensureFullDriveDocumentationHierarchy(
            context = context,
            driveClient = fakeDriveClient,
            explicitAccessToken = "ya29.test_drive_photo_center_token",
            yearsToProvision = listOf("2026", "2027")
        ).getOrThrow()

        assertEquals(
            "Re-running folder provisioning must reuse existing folders without creating duplicates",
            folderCountAfterFirstRun,
            simulatedFolders.size
        )
        assertEquals(firstTree.rootFolderId, secondTree.rootFolderId)

        // 2. Save a Freezer Unit with Foto Unit, Foto Nomor Seri, and Foto Kondisi Unit
        val fotoUnitFile = createSampleJpegFile("raw_unit_midea.jpg", 2400, 1800, Color.rgb(10, 80, 160))
        val fotoSeriFile = createSampleJpegFile("raw_sn_midea.jpg", 2200, 1600, Color.rgb(20, 100, 140))
        val fotoKondisiFile = createSampleJpegFile("raw_kondisi_midea.jpg", 2000, 1500, Color.rgb(30, 120, 90))

        val rawUnit = FreezerUnitEntity(
            kodeUnit = "SAF-MIDEA-01",
            namaUnit = "MIDEA HS390CK",
            jenisUnit = "Chest Freezer",
            owner = "SEWAin Aja",
            hargaPembelian = 4200000L,
            kapasitasLiter = 390,
            cicilanPerBulan = 350000L,
            merek = "MIDEA",
            model = "HS390CK",
            nomorSeri = "SN123456",
            statusUnit = "Tersedia",
            lokasiUnit = "Gudang Solo",
            tanggalPembelian = "2026-11-16",
            fotoUnitUri = fotoUnitFile.absolutePath,
            fotoMerekSeriUri = fotoSeriFile.absolutePath,
            fotoKondisiFisikUri = fotoKondisiFile.absolutePath
        )
        val unitId = repository.saveUnit(rawUnit)
        val unit = rawUnit.copy(id = unitId)

        val uploadedUnitPhotos = GoogleDrivePhotoCenterManager.enqueueAndSyncUnitPhotos(
            context = context,
            repository = repository,
            unit = unit,
            driveClient = fakeDriveClient,
            explicitAccessToken = "ya29.test_drive_photo_center_token",
            isOnlineOverride = true
        )

        assertEquals(3, uploadedUnitPhotos.size)

        val byCategory = uploadedUnitPhotos.associateBy { it.categoryCode }
        val fotoUnitMeta = byCategory.getValue("FOTO_UNIT")
        val nomorSeriMeta = byCategory.getValue("NOMOR_SERI")
        val kondisiMeta = byCategory.getValue("KONDISI_UNIT")

        assertEquals("MIDEA_HS390CK_SN123456_FOTO_UNIT_20261116.jpg", fotoUnitMeta.fileName)
        assertEquals("MIDEA_HS390CK_SN123456_NOMOR_SERI_20261116.jpg", nomorSeriMeta.fileName)
        assertEquals("MIDEA_HS390CK_SN123456_KONDISI_UNIT_20261116.jpg", kondisiMeta.fileName)

        assertEquals("01. UNIT INVESTASI UNIT", fotoUnitMeta.driveRootSection)
        assertEquals("2026", fotoUnitMeta.driveYearFolder)
        assertEquals("Foto Unit", fotoUnitMeta.driveCategoryFolder)
        assertEquals("Foto Nomor Seri", nomorSeriMeta.driveCategoryFolder)
        assertEquals("Dokumentasi Kondisi Unit", kondisiMeta.driveCategoryFolder)

        uploadedUnitPhotos.forEach { meta ->
            assertEquals(GoogleDrivePhotoCenterManager.STATUS_BERHASIL, meta.syncStatus)
            assertTrue("Drive File ID must be saved in SQLite", meta.driveFileId.isNotBlank())
            assertEquals(unitId, meta.unitId)
            assertTrue(
                "Temporary original file must be cleaned up after successful Drive upload",
                meta.tempOriginalPath.isBlank()
            )
        }
    }

    @Test
    fun testB_and_C_uploadCustomerKtpWithNikMaskingAndUploadPaymentProof() = runBlocking {
        val ktpFile = createSampleJpegFile("raw_ktp_budi.jpg", 2200, 1400, Color.rgb(60, 40, 120))
        val rawCustomer = CustomerEntity(
            namaLengkap = "Budi Santoso",
            nik = "3372011611900001",
            nomorHp = "081234567890",
            alamatLengkap = "Jl. Slamet Riyadi No. 10, Surakarta",
            jenisKelamin = "Laki-laki",
            tanggalRegistrasi = "2026-11-16",
            fotoKtpDepanUri = ktpFile.absolutePath
        )
        val customerId = repository.saveCustomer(rawCustomer)
        val customer = rawCustomer.copy(id = customerId)

        val unitId = repository.saveUnit(
            FreezerUnitEntity(
                kodeUnit = "SAF-MIDEA-01",
                namaUnit = "MIDEA HS390CK",
                jenisUnit = "Chest Freezer",
                owner = "SEWAin Aja",
                hargaPembelian = 4200000L,
                kapasitasLiter = 390,
                cicilanPerBulan = 350000L,
                merek = "MIDEA",
                model = "HS390CK",
                nomorSeri = "SN123456",
                statusUnit = "Disewa",
                lokasiUnit = "Laweyan",
                tanggalPembelian = "2026-11-16"
            )
        )

        // Upload KTP Pelanggan (Test B)
        val customerMetas = GoogleDrivePhotoCenterManager.enqueueAndSyncCustomerPhotos(
            context = context,
            repository = repository,
            customer = customer,
            driveClient = fakeDriveClient,
            explicitAccessToken = "ya29.test_drive_photo_center_token",
            isOnlineOverride = true
        )
        assertEquals(1, customerMetas.size)
        val ktpMeta = customerMetas.first()

        // Verify automatic file naming with NIK masking (`3372XXXXXXXXXXXX`) while full NIK stays intact in SQLite
        assertEquals("BUDI_SANTOSO_3372XXXXXXXXXXXX_KTP_20261116.jpg", ktpMeta.fileName)
        assertEquals("02. DOKUMENTASI SEWA", ktpMeta.driveRootSection)
        assertEquals("2026", ktpMeta.driveYearFolder)
        assertEquals("Foto KTP Pelanggan", ktpMeta.driveCategoryFolder)
        assertEquals(GoogleDrivePhotoCenterManager.STATUS_BERHASIL, ktpMeta.syncStatus)
        assertEquals(customerId, ktpMeta.customerId)
        assertEquals("3372011611900001", repository.getCustomerById(customerId)?.nik)

        // Create Transaction + Upload Bukti Pembayaran (Test C)
        val rawTx = RentalTransactionEntity(
            nomorInvoice = "INV/SAF/2026/0077",
            tanggalPemesanan = "2026-11-16",
            customerId = customerId,
            namaPelangganSnapshot = customer.namaLengkap,
            unitId = unitId,
            namaUnitSnapshot = "MIDEA HS390CK",
            kodeUnitSnapshot = "SAF-MIDEA-01",
            hargaSewa = 350000L,
            durasiSewaBulan = 1,
            durasiHari = 30,
            tanggalMulaiSewa = "2026-11-16",
            tanggalAkhirSewa = "2026-12-16",
            tanggalJatuhTempo = "2026-12-16",
            nominalPembayaran = 350000L,
            totalTagihan = 350000L,
            statusPembayaran = "Lunas",
            statusPenyewaan = "Dalam Penyewaan",
            metodePembayaran = "Transfer BCA"
        )
        val txId = repository.saveTransaction(rawTx)

        val paymentProofFile = createSampleJpegFile("raw_bukti_transfer.jpg", 1600, 1200, Color.rgb(15, 118, 110))
        val rawProof = PaymentProofEntity(
            transactionId = txId,
            tanggalPembayaran = "2026-11-16",
            nominalPembayaran = 350000L,
            metodePembayaran = "Transfer BCA",
            fotoBuktiUri = paymentProofFile.absolutePath,
            catatanPembayaran = "Lunas periode 1"
        )
        val proofId = repository.savePaymentProof(rawProof)
        val proof = rawProof.copy(id = proofId)

        val proofMeta = GoogleDrivePhotoCenterManager.enqueueAndSyncPaymentProofPhoto(
            context = context,
            repository = repository,
            proof = proof,
            customer = customer,
            driveClient = fakeDriveClient,
            explicitAccessToken = "ya29.test_drive_photo_center_token",
            isOnlineOverride = true
        )
        assertNotNull(proofMeta)
        assertEquals("BUDI_SANTOSO_3372XXXXXXXXXXXX_BUKTI_PEMBAYARAN_20261116.jpg", proofMeta!!.fileName)
        assertEquals("02. DOKUMENTASI SEWA", proofMeta.driveRootSection)
        assertEquals("Foto Bukti Pembayaran", proofMeta.driveCategoryFolder)
        assertEquals(customerId, proofMeta.customerId)
        assertEquals(txId, proofMeta.transactionId)
        assertEquals(GoogleDrivePhotoCenterManager.STATUS_BERHASIL, proofMeta.syncStatus)
    }

    @Test
    fun testD_E_and_F_offlineQueueWorkManagerBackgroundSyncTempCleanupAndOnDemandDownload() = runBlocking {
        val rawCustomer = CustomerEntity(
            namaLengkap = "Siti Rahmawati",
            nik = "3372052002920003",
            nomorHp = "081399887766",
            alamatLengkap = "Jl. Adi Sucipto No. 45, Solo",
            jenisKelamin = "Perempuan",
            tanggalRegistrasi = "2026-11-16"
        )
        val customerId = repository.saveCustomer(rawCustomer)
        val customer = rawCustomer.copy(id = customerId)

        val serahTerimaPhoto = createSampleJpegFile("raw_serah_terima_siti.jpg", 2000, 1500, Color.rgb(180, 83, 9))

        // Test D (Part 1): Enqueue while OFFLINE -> status is MENUNGGU UPLOAD and temporary original file is preserved
        val offlineMeta = GoogleDrivePhotoCenterManager.enqueueDocumentationPhoto(
            context = context,
            repository = repository,
            source = serahTerimaPhoto,
            category = DriveDocumentationCategory.SERAH_TERIMA,
            customer = customer,
            dateIsoOrCompact = "2026-11-16",
            driveClient = fakeDriveClient,
            explicitAccessToken = "ya29.test_drive_photo_center_token",
            isOnlineOverride = false,
            uploadImmediately = true
        ).getOrThrow()

        assertEquals(GoogleDrivePhotoCenterManager.STATUS_MENUNGGU_UPLOAD, offlineMeta.syncStatus)
        assertEquals("SITI_RAHMAWATI_3372XXXXXXXXXXXX_SERAH_TERIMA_20261116.jpg", offlineMeta.fileName)
        assertTrue("Drive File ID must be empty while waiting offline", offlineMeta.driveFileId.isBlank())
        assertTrue(
            "Temporary original file must NOT be deleted before Drive upload succeeds",
            offlineMeta.tempOriginalPath.isNotBlank() && File(offlineMeta.tempOriginalPath).exists()
        )
        val savedTempOriginalFile = File(offlineMeta.tempOriginalPath)

        // Test E: Verify WorkManager unique background sync work was scheduled
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(GoogleDrivePhotoCenterManager.UNIQUE_DRIVE_PHOTO_SYNC_WORK)
            .get()
        assertTrue("Background DrivePhotoSyncWorker must be enqueued in WorkManager", workInfos.isNotEmpty())
        assertTrue(
            workInfos.any {
                it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.SUCCEEDED
            }
        )

        // Test D (Part 2): Internet connection comes back -> drainPendingDrivePhotoQueue uploads automatically
        val batchOutcome = GoogleDrivePhotoCenterManager.drainPendingDrivePhotoQueue(
            context = context,
            repository = repository,
            driveClient = fakeDriveClient,
            explicitAccessToken = "ya29.test_drive_photo_center_token",
            isOnlineOverride = true
        )
        assertEquals(1, batchOutcome.totalProcessed)
        assertEquals(1, batchOutcome.succeededCount)
        assertEquals(0, batchOutcome.failedCount)

        val syncedMeta = repository.getDrivePhotoMetadataByCustomerOnce(customerId).first()
        assertEquals(GoogleDrivePhotoCenterManager.STATUS_BERHASIL, syncedMeta.syncStatus)
        assertTrue("Drive File ID must be populated after sync", syncedMeta.driveFileId.isNotBlank())
        assertFalse(
            "Temporary original file must be deleted automatically after successful upload",
            savedTempOriginalFile.exists()
        )

        // Test F: Delete local compressed file & thumbnail to simulate viewing an old photo whose local copy was cleaned
        val localCompressedFile = File(syncedMeta.localCompressedPath)
        val localThumbFile = File(syncedMeta.thumbnailCachePath)
        if (localCompressedFile.exists()) localCompressedFile.delete()
        if (localThumbFile.exists()) localThumbFile.delete()
        assertFalse("Local compressed file should be deleted before on-demand test", localCompressedFile.exists())

        val redownloadedFile = GoogleDrivePhotoCenterManager.openOrDownloadPhotoFromDrive(
            context = context,
            repository = repository,
            metadata = syncedMeta,
            driveClient = fakeDriveClient,
            explicitAccessToken = "ya29.test_drive_photo_center_token"
        ).getOrThrow()

        assertTrue("Photo must be downloaded on-demand from Google Drive and exist locally", redownloadedFile.exists())
        assertTrue("Downloaded photo must have non-zero size", redownloadedFile.length() > 0L)
    }

    @Test
    fun testUI_pengaturanPenyimpananCardRendersSectionsAndButtons() {
        var syncClicked = false
        var verifyFoldersClicked = false

        val stats = GoogleDrivePhotoCenterManager.computeStorageSummaryStats(emptyList())
        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.verticalScroll(
                        androidx.compose.foundation.rememberScrollState()
                    )
                ) {
                    DrivePhotoStorageSettingsCard(
                        driveConnectionStatus = DriveConnectionStatus(
                            isConnected = true,
                            accountEmail = "owner.sewainaja@gmail.com",
                            displayName = "Owner SEWAin Aja Freezer SOC",
                            lastDriveSyncAt = "16 Nov 2026 09:00"
                        ),
                        drivePhotoItems = emptyList(),
                        driveStorageStats = stats,
                        compressionConfig = DrivePhotoCompressionConfig(),
                        folderTreeSummary = DriveFolderTreeSummary(
                            rootFolderId = "folder_root_1",
                            isVerifiedOnDrive = true,
                            lastVerifiedAt = "16 Nov 2026 09:00"
                        ),
                        liveUploadProgress = DriveUploadProgressState(),
                        isSyncBusy = false,
                        openedPhotoFile = null,
                        openedPhotoMetadata = null,
                        onConnectOrSwitchDrive = {},
                        onSyncNow = { syncClicked = true },
                        onDisconnectDrive = {},
                        onSaveCompressionConfig = {},
                        onEnsureDriveFolderHierarchy = { verifyFoldersClicked = true },
                        onRetrySinglePhotoUpload = {},
                        onOpenPhotoFromDriveOrLocal = {},
                        onDismissOpenedPhotoDialog = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("card_pengaturan_penyimpanan_drive").assertIsDisplayed()
        composeTestRule.onNodeWithText("PENGATURAN PENYIMPANAN", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("A. Koneksi Google Drive", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("owner.sewainaja@gmail.com", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_drive_sinkronisasi_sekarang").performClick()
        assertTrue("Clicking Sinkronisasi Sekarang must trigger callback", syncClicked)

        composeTestRule.onNodeWithTag("btn_verify_create_drive_folders")
            .performScrollTo()
            .performClick()
        assertTrue("Clicking Buat / Verifikasi Struktur Folder must trigger callback", verifyFoldersClicked)
    }
}
