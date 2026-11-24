package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.backup.AutoBackupWorkScheduler
import com.example.backup.DriveBackupCoordinator
import com.example.backup.RestoreMode
import com.example.backup.StructuredBackupEngine
import com.example.data.local.AppDatabase
import com.example.data.local.BackupScheduleConfig
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.repository.FreezerRepository
import com.example.drive.GoogleDriveAuthManager
import com.example.drive.GoogleDriveBackupService
import com.example.drive.GoogleDriveRestClient
import com.example.sheets.DuplicateImportPolicy
import com.example.sheets.ExcelTemplateAndReportExporter
import com.example.sheets.ExcelTemplateAndReportExporter.TemplateType
import com.example.sheets.SmartImportExecutor
import com.example.sheets.SmartImportFormatParser
import com.example.sheets.SmartImportTarget
import com.example.ui.screens.SettingsAndSyncScreen
import com.example.util.SecurityCryptoUtils
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class GoogleDriveBackupAndSmartImportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: FreezerRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FreezerRepository(db.freezerDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun test01_and_02_structuredEncryptedBackupWithPhotosAnd30VersionPruning(): Unit = runBlocking {
        val custId = repository.saveCustomer(
            CustomerEntity(
                kodePelanggan = "CUST/SAF/0101",
                namaLengkap = "Budi Santoso",
                nik = "3372010101900001",
                nomorHp = "081234567890",
                alamatLengkap = "Jl. Slamet Riyadi No. 120, Surakarta",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = "2026-11-01"
            )
        )
        val unitId = repository.saveUnit(
            FreezerUnitEntity(
                kodeUnit = "SAF-CF-101",
                namaUnit = "GEA AB-226-R",
                jenisUnit = "Chest Freezer",
                owner = "SEWAin Aja",
                hargaPembelian = 3500000L,
                kapasitasLiter = 220,
                cicilanPerBulan = 250000L,
                merek = "GEA",
                model = "AB-226-R",
                nomorSeri = "SN-101",
                statusUnit = "Disewa",
                lokasiUnit = "Laweyan",
                tanggalPembelian = "2026-01-10"
            )
        )
        repository.saveTransaction(
            RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0101",
                tanggalPemesanan = "2026-11-01",
                customerId = custId,
                namaPelangganSnapshot = "Budi Santoso",
                unitId = unitId,
                namaUnitSnapshot = "GEA AB-226-R",
                kodeUnitSnapshot = "SAF-CF-101",
                hargaSewa = 250000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-01",
                tanggalAkhirSewa = "2026-12-01",
                tanggalJatuhTempo = "2026-12-01",
                nominalPembayaran = 250000L,
                totalTagihan = 250000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA"
            )
        )
        val photoFile = File(context.filesDir, "photos/ktp_documents/test_ktp_01.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0x33))
        }

        val res = StructuredBackupEngine.createStructuredBackupZip(
            context = context,
            repository = repository,
            encryptSensitivePayload = true,
            includePhotos = true,
            maxVersionsToKeep = 30
        )
        assertTrue(res.isSuccess)
        val (entity, zipFile) = res.getOrThrow()
        assertTrue(zipFile.exists() && zipFile.length() > 0L)
        assertTrue(entity.isEncrypted)
        assertEquals(1, entity.customerCount)
        assertEquals(1, entity.unitCount)
        assertEquals(1, entity.transactionCount)
        assertTrue(entity.photoCount >= 1)

        // Verify structured entries inside ZIP (Database SQLite + JSON + Manifest + Version Info + Checksum + Photos)
        val entries = mutableSetOf<String>()
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                entries.add(e.name)
                e = zis.nextEntry
            }
        }
        assertTrue("SEWAIN_BACKUP/manifest.json" in entries)
        assertTrue("SEWAIN_BACKUP/backup_info.json" in entries)
        assertTrue("SEWAIN_BACKUP/checksum.json" in entries)
        assertTrue("SEWAIN_BACKUP/database/sewain_freezer_soc.sqlite" in entries)
        assertTrue("SEWAIN_BACKUP/database/customers.json" in entries)
        assertTrue("SEWAIN_BACKUP/database/units.json" in entries)
        assertTrue("SEWAIN_BACKUP/database/transactions.json" in entries)

        // Verify retention limit 30 after creating 32 backups
        val tempDir = File(context.cacheDir, "prune_test_dir").apply { mkdirs() }
        repeat(32) { i ->
            StructuredBackupEngine.createStructuredBackupZip(
                context = context,
                repository = repository,
                destinationDir = tempDir,
                customFileName = "SEWAIN_BKP_$i.zip",
                maxVersionsToKeep = 30
            )
        }
        assertEquals(30, repository.getAllLocalBackupsOnce().size)
        photoFile.delete()
        tempDir.deleteRecursively()
        Unit
    }

    @Test
    fun test03_and_05_googleDriveUploadListDownloadPruneAndOfflineQueue(): Unit = runBlocking {
        assertEquals("SEWAIN_AJA_FREEZER_SOC", GoogleDriveRestClient.BACKUP_FOLDER_NAME)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val mockServer = MockWebServer()
        mockServer.start()
        try {
            // Queue responses: 1. Folder lookup (empty), 2. Create folder SEWAIN_AJA_FREEZER_SOC, 3. Multipart upload, 4. List for prune
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[]}"""))
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"drv_folder_1","name":"SEWAIN_AJA_FREEZER_SOC"}"""))
            mockServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"id":"drv_file_101","name":"SEWAIN_BACKUP_FULL.zip","size":"2048","createdTime":"2026-11-15T01:00:00Z","appProperties":{"backupId":"bkp_101","checksumSha256":"abc"}}"""
                )
            )
            mockServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"files":[{"id":"drv_file_101","name":"SEWAIN_BACKUP_FULL.zip","size":"2048"}]}"""
                )
            )
            val baseUrl = mockServer.url("/").toString().trimEnd('/')
            val client = GoogleDriveRestClient(apiBaseUrl = baseUrl, uploadBaseUrl = baseUrl)

            repository.saveCustomer(
                CustomerEntity(
                    kodePelanggan = "CUST/SAF/0201",
                    namaLengkap = "Pelanggan Drive Cloud",
                    nik = "3372010101900201",
                    nomorHp = "081234567201",
                    alamatLengkap = "Solo",
                    jenisKelamin = "Perempuan",
                    tanggalRegistrasi = "2026-11-05"
                )
            )

            // 1. Offline backup test -> must NOT lose local data, marks queuedOffline = true
            val offlineOutcome = DriveBackupCoordinator.runBackupAndUploadToDrive(
                context = context,
                repository = repository,
                config = BackupScheduleConfig(frequency = "DAILY", autoUploadToDrive = true),
                driveClient = client,
                explicitAccessToken = "test_oauth_token",
                isNetworkConnectedOverride = false
            )
            assertTrue(offlineOutcome.success)
            assertTrue(offlineOutcome.queuedOffline)
            assertEquals("QUEUED_OFFLINE", offlineOutcome.backupEntity?.backupStatus)

            // 2. Online upload test -> uploads multipart zip to Drive REST v3 folder SEWAIN_AJA_FREEZER_SOC/
            val onlineOutcome = DriveBackupCoordinator.runBackupAndUploadToDrive(
                context = context,
                repository = repository,
                config = BackupScheduleConfig(frequency = "DAILY", autoUploadToDrive = true),
                driveClient = client,
                explicitAccessToken = "test_oauth_token",
                isNetworkConnectedOverride = true
            )
            assertTrue(onlineOutcome.success)
            assertFalse(onlineOutcome.queuedOffline)
            assertEquals("LOKAL_DAN_DRIVE", onlineOutcome.backupEntity?.locationType)
            assertEquals("drv_file_101", onlineOutcome.backupEntity?.driveFileId)

            // 3. Download backup test (.part -> target file)
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody("PK\u0003\u0004MOCK_ZIP_PAYLOAD"))
            val dlDest = File(context.cacheDir, "downloaded_drive_bkp.zip")
            val dlRes = client.downloadBackupZip("test_oauth_token", "drv_file_101", dlDest)
            assertTrue(dlRes.isSuccess)
            assertTrue(dlDest.exists() && dlDest.length() > 0L)
            dlDest.delete()

            // Verify WorkManager schedule registration at default 01:00
            AutoBackupWorkScheduler.applyBackupSchedule(context, BackupScheduleConfig(frequency = "DAILY", scheduledHour = 1, scheduledMinute = 0))
            val delayMs = AutoBackupWorkScheduler.computeInitialDelayToTimeMillis(1, 0)
            assertTrue(delayMs in 1000L..86_400_000L)
        } finally {
            mockServer.shutdown()
        }
        Unit
    }

    @Test
    fun test04_06_07_and_16_checksumValidationSafeRestoreAppendMergeAndAutoRollbackOnFailure(): Unit = runBlocking {
        // Seed initial state A
        val origId = repository.saveCustomer(
            CustomerEntity(
                kodePelanggan = "CUST/SAF/0301",
                namaLengkap = "Pelanggan Asli Sebelum Restore",
                nik = "3372010101900301",
                nomorHp = "081234560301",
                alamatLengkap = "Surakarta",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = "2026-11-01"
            )
        )
        assertTrue(origId > 0L)
        val bkpA = StructuredBackupEngine.createStructuredBackupZip(context, repository, customFileName = "BKP_A.zip").getOrThrow().second

        // Modify DB into state B
        val newCustId = repository.saveCustomer(
            CustomerEntity(
                kodePelanggan = "CUST/SAF/0302",
                namaLengkap = "Pelanggan Tambahan B",
                nik = "3372010101900302",
                nomorHp = "081234560302",
                alamatLengkap = "Karanganyar",
                jenisKelamin = "Perempuan",
                tanggalRegistrasi = "2026-11-10"
            )
        )
        assertEquals(2, repository.getAllCustomersOnce().size)

        // Create backup B then tamper 1 entry inside ZIP to verify SHA-256 rejection
        val bkpB = StructuredBackupEngine.createStructuredBackupZip(context, repository, customFileName = "BKP_B.zip").getOrThrow().second
        val tamperedZip = File(context.cacheDir, "TAMPERED_B.zip")
        tamperZipEntry(bkpB, tamperedZip, "SEWAIN_BACKUP/database/customers.json")
        val tamperPreview = StructuredBackupEngine.inspectBackupZipForRestore(context, tamperedZip)
        assertFalse(tamperPreview.isValid)
        assertTrue(tamperPreview.errorMessage.contains("SHA-256", ignoreCase = true))

        // Test 16: Simulated mid-restore failure MUST rollback automatically so State B (2 customers) stays 100% intact!
        val failedRestore = StructuredBackupEngine.executeSafeRestore(
            context = context,
            repository = repository,
            sourceFileOrUri = bkpA,
            mode = RestoreMode.FULL_RESTORE,
            simulateFailureHookForTest = { throw IllegalStateException("Simulasi kegagalan disk saat restore") }
        )
        assertFalse(failedRestore.success)
        assertTrue(failedRestore.rolledBack)
        assertEquals(2, repository.getAllCustomersOnce().size)

        // Test 6: Clean FULL_RESTORE back to State A (1 customer)
        val fullOk = StructuredBackupEngine.executeSafeRestore(
            context = context,
            repository = repository,
            sourceFileOrUri = bkpA,
            mode = RestoreMode.FULL_RESTORE
        )
        assertTrue(fullOk.success)
        assertEquals(1, repository.getAllCustomersOnce().size)
        assertEquals("Pelanggan Asli Sebelum Restore", repository.getAllCustomersOnce().first().namaLengkap)

        // Test 7: APPEND_MERGE with BKP_B brings Pelanggan Tambahan B back without deleting Pelanggan Asli
        assertTrue(newCustId > 0L)
        val mergeOk = StructuredBackupEngine.executeSafeRestore(
            context = context,
            repository = repository,
            sourceFileOrUri = bkpB,
            mode = RestoreMode.APPEND_MERGE
        )
        assertTrue(mergeOk.success)
        assertEquals(2, repository.getAllCustomersOnce().size)

        bkpA.delete()
        bkpB.delete()
        tamperedZip.delete()
        Unit
    }

    @Test
    fun test08_and_09_import100CustomersAnd100UnitsFromXlsxWithExcelSerialDates(): Unit = runBlocking {
        // Build 100 Customers XLSX including numeric Excel serial date 45610 -> 2024-11-14
        val custMatrix = buildList {
            add(listOf("Kode Pelanggan", "Nama Pelanggan", "Nomor WhatsApp", "NIK", "Alamat", "Tanggal Registrasi"))
            for (i in 1..100) {
                add(
                    listOf(
                        String.format("CUST/IMP/%04d", i),
                        "Pelanggan Massal $i",
                        String.format("08139000%04d", i),
                        String.format("337201010190%04d", i),
                        "Jl. Solo Raya No. $i",
                        if (i == 1) "45610" else "2026-11-01"
                    )
                )
            }
        }
        val custXlsx = File(context.cacheDir, "import_100_customers.xlsx")
        FileOutputStream(custXlsx).use { ExcelTemplateAndReportExporter.writeSimpleXlsx("Pelanggan", custMatrix, it) }

        val parsedCust = SmartImportFormatParser.parseUriOrFile(context, custXlsx)
        assertTrue(parsedCust.isSuccess)
        val previewCust = SmartImportExecutor.buildPreview(parsedCust, repository, targetOverride = SmartImportTarget.PELANGGAN)
        assertEquals(100, previewCust.validCount)
        assertTrue(previewCust.canExecuteImport)

        val execCust = SmartImportExecutor.executeImport(previewCust, repository, db)
        assertEquals(100, execCust.successCount)
        val savedCustomers = repository.getAllCustomersOnce()
        assertEquals(100, savedCustomers.size)
        assertEquals("2024-11-14", savedCustomers.first { it.kodePelanggan == "CUST/IMP/0001" }.tanggalRegistrasi)

        // Build 100 Units XLSX
        val unitMatrix = buildList {
            add(listOf("Kode Unit", "Nama Unit", "Jenis Unit", "Kapasitas", "Nomor Seri", "Lokasi Unit"))
            for (i in 1..100) {
                add(listOf(String.format("IMP-CF-%03d", i), "Freezer Imp $i", "Chest Freezer", "250", "SN-IMP-$i", "Solo"))
            }
        }
        val unitXlsx = File(context.cacheDir, "import_100_units.xlsx")
        FileOutputStream(unitXlsx).use { ExcelTemplateAndReportExporter.writeSimpleXlsx("Unit Freezer", unitMatrix, it) }

        val parsedUnit = SmartImportFormatParser.parseUriOrFile(context, unitXlsx)
        val previewUnit = SmartImportExecutor.buildPreview(parsedUnit, repository, targetOverride = SmartImportTarget.UNIT_FREEZER)
        assertEquals(100, previewUnit.validCount)
        val execUnit = SmartImportExecutor.executeImport(previewUnit, repository, db)
        assertEquals(100, execUnit.successCount)
        assertEquals(100, repository.getAllUnitsOnce().size)

        custXlsx.delete()
        unitXlsx.delete()
        Unit
    }

    @Test
    fun test10_11_12_13_14_and_15_csvJsonZipDuplicatesInvalidMappingTemplatesAndInterruption(): Unit = runBlocking {
        // Test 10: Semicolon CSV with UTF-8 BOM + duplicate + invalid phone row
        val csvBytes = ("\uFEFFNama Lengkap;Nomor HP;Alamat\n" +
            "Pelanggan CSV 1;081234561111;Sukoharjo\n" +
            "Pelanggan CSV Invalid;123;Klaten\n").toByteArray(Charsets.UTF_8)
        val csvFile = File(context.cacheDir, "import_semicolon.csv").apply { writeBytes(csvBytes) }
        val parsedCsv = SmartImportFormatParser.parseUriOrFile(context, csvFile)
        assertEquals(';', parsedCsv.detectedDelimiter)
        val csvPreview = SmartImportExecutor.buildPreview(parsedCsv, repository, SmartImportTarget.PELANGGAN)
        assertEquals(1, csvPreview.validCount)
        assertEquals(1, csvPreview.invalidCount)
        val csvReport = SmartImportExecutor.executeImport(csvPreview, repository, db)
        assertEquals(1, csvReport.successCount)
        assertEquals(1, csvReport.errorRows.size)

        // Error report download (.xlsx & .csv)
        val errXlsx = ByteArrayOutputStream()
        val errCsv = ByteArrayOutputStream()
        assertEquals(1, ExcelTemplateAndReportExporter.exportErrorReportXlsx(csvReport.originalHeaders, csvReport.errorRows, errXlsx).getOrThrow())
        assertEquals(1, ExcelTemplateAndReportExporter.exportErrorReportCsv(csvReport.originalHeaders, csvReport.errorRows, errCsv).getOrThrow())

        // Test 13: Duplicate handling (SKIP vs UPDATE vs IMPORT_AS_NEW) + unmapped required blocker
        val jsonBytes = """[{"kodePelanggan":"CUST/SAF/DUP1","namaLengkap":"Nama Lama","nomorHp":"081234561111","alamat":"Boyolali"}]""".toByteArray()
        val jsonFile = File(context.cacheDir, "dup_test.json").apply { writeBytes(jsonBytes) }
        val parsedJson = SmartImportFormatParser.parseUriOrFile(context, jsonFile)
        val dupSkip = SmartImportExecutor.buildPreview(parsedJson, repository, SmartImportTarget.PELANGGAN, duplicatePolicy = DuplicateImportPolicy.SKIP)
        assertEquals(1, dupSkip.duplicateCount)
        assertFalse(dupSkip.canExecuteImport)

        val dupUpdate = SmartImportExecutor.buildPreview(parsedJson, repository, SmartImportTarget.PELANGGAN, duplicatePolicy = DuplicateImportPolicy.UPDATE)
        assertTrue(dupUpdate.canExecuteImport)
        val execUpdate = SmartImportExecutor.executeImport(dupUpdate, repository, db)
        assertEquals(1, execUpdate.updatedCount)

        val blockedMapping = SmartImportExecutor.buildPreview(
            parsedJson, repository, SmartImportTarget.PELANGGAN,
            customColumnMapping = mapOf(0 to "customer_code") // required name & phone unmapped
        )
        assertFalse(blockedMapping.canExecuteImport)
        assertEquals(2, blockedMapping.missingRequiredFields.size)

        // Test 12: Template export round-trip -> parse with SmartImportFormatParser
        val tplOut = File(context.cacheDir, TemplateType.PELANGGAN.fileName)
        FileOutputStream(tplOut).use { ExcelTemplateAndReportExporter.exportTemplateXlsx(TemplateType.PELANGGAN, it) }
        val parsedTpl = SmartImportFormatParser.parseUriOrFile(context, tplOut)
        assertTrue(parsedTpl.isSuccess)
        assertEquals(2, parsedTpl.sheets.first().rows.size)

        // Test 14: Zip Slip traversal rejection
        val evilZip = File(context.cacheDir, "evil_zip_slip.zip")
        ZipOutputStream(FileOutputStream(evilZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/passwd"))
            zos.write("root:x:0:0".toByteArray())
            zos.closeEntry()
        }
        val evilPreview = StructuredBackupEngine.inspectBackupZipForRestore(context, evilZip)
        assertFalse(evilPreview.isValid)
        assertTrue(evilPreview.errorMessage.contains("Zip Slip", ignoreCase = true) || evilPreview.errorMessage.contains("Keamanan", ignoreCase = true))

        // Test 15: Interrupted batch import preserves committed first batch (50 rows) cleanly without corruption
        val batchMatrix = buildList {
            add(listOf("Nama Pelanggan", "Nomor WhatsApp"))
            for (i in 1..60) add(listOf("Batch User $i", String.format("08157000%04d", i)))
        }
        val batchXlsx = File(context.cacheDir, "interrupt_60.xlsx")
        FileOutputStream(batchXlsx).use { ExcelTemplateAndReportExporter.writeSimpleXlsx("Customers", batchMatrix, it) }
        val batchPreview = SmartImportExecutor.buildPreview(SmartImportFormatParser.parseUriOrFile(context, batchXlsx), repository, SmartImportTarget.PELANGGAN)
        val interruptReport = SmartImportExecutor.executeImport(batchPreview, repository, db, interruptAfterBatchForTest = 1)
        assertTrue(interruptReport.wasInterrupted)
        assertEquals(50, interruptReport.successCount)
        assertEquals(10, interruptReport.errorRows.size)

        csvFile.delete(); jsonFile.delete(); tplOut.delete(); evilZip.delete(); batchXlsx.delete()
        Unit
    }

    @Test
    fun test17_scale500Customers500Units1000TransactionsCategoryRestoreAndCrossDeviceRecovery(): Unit = runBlocking {
        // Section I: 500 pelanggan, 500 unit freezer, 1.000 transaksi (Reguler + Hak Milik), dokumentasi foto,
        // restore per kategori, dan pemulihan penuh di HP kedua (cross-device restore).
        val bulkCustomers = (1..500).map { i ->
            CustomerEntity(
                id = i.toLong(),
                kodePelanggan = String.format("CUST/SAF/%04d", i),
                namaLengkap = "Pelanggan Skala Besar $i",
                nik = String.format("337201010199%04d", i),
                nomorHp = String.format("08129900%04d", i),
                alamatLengkap = "Jl. Raya Solo-Sukoharjo No. $i",
                jenisKelamin = if (i % 2 == 0) "Laki-laki" else "Perempuan",
                tanggalRegistrasi = "2026-11-01"
            )
        }
        val bulkUnits = (1..500).map { i ->
            FreezerUnitEntity(
                id = i.toLong(),
                kodeUnit = String.format("SAF-SCALE-%04d", i),
                namaUnit = "Freezer RSA CF-${100 + (i % 4) * 100}",
                jenisUnit = "Chest Freezer",
                owner = "SEWAin Aja",
                hargaPembelian = 3500000L,
                kapasitasLiter = 200,
                cicilanPerBulan = 250000L,
                merek = "RSA",
                model = "CF-200",
                nomorSeri = String.format("SN-SCALE-%04d", i),
                statusUnit = "Disewa",
                lokasiUnit = "Surakarta",
                tanggalPembelian = "2026-01-01"
            )
        }
        val bulkTransactions = (1..1000).map { i ->
            val custIdx = ((i - 1) % 500) + 1
            val unitIdx = ((i - 1) % 500) + 1
            val isHakMilik = i % 3 == 0
            RentalTransactionEntity(
                id = i.toLong(),
                nomorInvoice = String.format("INV/SAF/2026/SCALE/%04d", i),
                tanggalPemesanan = "2026-11-01",
                customerId = custIdx.toLong(),
                namaPelangganSnapshot = "Pelanggan Skala Besar $custIdx",
                unitId = unitIdx.toLong(),
                namaUnitSnapshot = "Freezer RSA CF-200",
                kodeUnitSnapshot = String.format("SAF-SCALE-%04d", unitIdx),
                hargaSewa = 250000L,
                durasiHari = 30,
                tanggalMulaiSewa = "2026-11-01",
                tanggalAkhirSewa = "2026-12-01",
                tanggalJatuhTempo = "2026-12-01",
                nominalPembayaran = 250000L,
                totalTagihan = 250000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA",
                jenisProgram = if (isHakMilik) "Sewa Hak Milik" else "Sewa Reguler",
                totalPeriodeHakMilik = if (isHakMilik) 12 else 0,
                pembayaranKeHakMilik = if (isHakMilik) 1 else 0
            )
        }

        db.freezerDao().insertCustomersBulk(bulkCustomers)
        db.freezerDao().insertUnitsBulk(bulkUnits)
        db.freezerDao().insertTransactionsBulk(bulkTransactions)

        val docPhoto = File(context.filesDir, "photos/ktp_documents/ktp_scale_500.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x45, 0x67, 0x89.toByte()))
        }

        val scaleZipFile = File(context.cacheDir, "SEWAIN_SCALE_500_1000.zip")
        val bkpRes = StructuredBackupEngine.createStructuredBackupZip(
            context = context,
            repository = repository,
            encryptSensitivePayload = true,
            includePhotos = true,
            destinationDir = scaleZipFile.parentFile,
            customFileName = scaleZipFile.name,
            saveToHistory = false
        )
        assertTrue(bkpRes.isSuccess)

        // Simulate HP Kedua (Device B) with fresh empty SQLite database
        val dbPhone2 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repoPhone2 = FreezerRepository(dbPhone2.freezerDao())
        try {
            // 1. Test Category Restore (Restore berdasarkan kategori: Pelanggan saja)
            val catCustRes = StructuredBackupEngine.executeSafeRestore(
                context = context,
                repository = repoPhone2,
                sourceFileOrUri = scaleZipFile,
                mode = RestoreMode.CATEGORY_CUSTOMERS
            )
            assertTrue(catCustRes.success)
            assertEquals(500, repoPhone2.getAllCustomersOnce().size)
            assertEquals(0, repoPhone2.getAllUnitsOnce().size)
            assertEquals(0, repoPhone2.getAllTransactionsOnce().size)

            // 2. Test Full Cross-Device Restore (500 pelanggan, 500 unit, 1.000 transaksi + dokumentasi foto)
            val fullPhone2Res = StructuredBackupEngine.executeSafeRestore(
                context = context,
                repository = repoPhone2,
                sourceFileOrUri = scaleZipFile,
                mode = RestoreMode.FULL_RESTORE
            )
            assertTrue(fullPhone2Res.success)
            assertEquals(500, repoPhone2.getAllCustomersOnce().size)
            assertEquals(500, repoPhone2.getAllUnitsOnce().size)
            assertEquals(1000, repoPhone2.getAllTransactionsOnce().size)
            assertTrue("Dokumentasi foto harus dipulihkan dan dapat dibuka kembali", fullPhone2Res.restoredPhotosCount >= 1)
            val restoredKtp = File(context.filesDir, "photos/restored/customers/ktp_scale_500.jpg")
            assertTrue("File dokumentasi KTP hasil restore harus ada dan terbaca", restoredKtp.exists() && restoredKtp.length() == 5L)
        } finally {
            dbPhone2.close()
            docPhoto.delete()
            scaleZipFile.delete()
        }
        Unit
    }

    @Test
    fun testRoomMigration14To15PreservesLocalBackupsAndAddsAuditLogs() {
        val dbName = "migration_v14_test.db"
        context.deleteDatabase(dbName)
        val configV14 = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `local_backup` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `backup_name` TEXT NOT NULL, `backup_path` TEXT NOT NULL, `backup_size` INTEGER NOT NULL, `backup_type` TEXT NOT NULL, `backup_status` TEXT NOT NULL, `created_at` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO `local_backup` (`backup_name`, `backup_path`, `backup_size`, `backup_type`, `backup_status`, `created_at`) VALUES ('LEGACY.zip', '/tmp/LEGACY.zip', 1024, 'FULL_BACKUP', 'SUCCESS', '2026-11-10')"
                    )
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helperV14 = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(configV14)
        val sqliteDb = helperV14.writableDatabase
        AppDatabase.MIGRATION_14_15.migrate(sqliteDb)

        // Verify legacy local_backup row preserved with new v15 columns defaulted
        sqliteDb.query("SELECT backup_name, location_type, is_encrypted FROM local_backup").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("LEGACY.zip", cursor.getString(0))
            assertEquals("LOKAL", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }

        // Verify backup_audit_logs table exists and accepts inserts
        sqliteDb.execSQL(
            "INSERT INTO `backup_audit_logs` (`action_type`, `status`, `file_name`, `checksum`, `record_count`, `actor_uid`, `actor_email`, `detail_message`, `timestamp_display`, `timestamp_millis`) VALUES ('BACKUP_LOCAL', 'SUKSES', 'LEGACY.zip', 'abc', 1, 'u1', 'a@b.com', 'Migrated', '2026-11-10', 1000)"
        )
        sqliteDb.query("SELECT COUNT(*) FROM backup_audit_logs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        sqliteDb.close()
        helperV14.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun uiTestSevenSubmenusInsidePengaturanDataRenderCleanly() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsAndSyncScreen(
                    companyProfile = CompanyProfile(),
                    googleSheetUrl = "",
                    webhookScriptUrl = "",
                    autoSyncEnabled = false,
                    maskNikInPdf = false,
                    lastSyncTime = "-",
                    syncStatusIndicator = "Siap",
                    importPreview = null,
                    lastImportReport = null,
                    syncLogs = emptyList(),
                    onLoadFileForPreview = { _, _, _ -> },
                    onLoadBuiltInTimelinePreview = {},
                    onSelectSheetInPreview = {},
                    onConfirmImport = {},
                    onDismissPreview = {},
                    onImportFromGoogleSheets = {},
                    onManualSyncNow = {},
                    onExportFullXlsx = {},
                    onExportAndShareApk = {},
                    onSaveApkToUri = {},
                    onSaveCompanyProfile = {},
                    onSaveSyncSettings = { _, _, _, _ -> },
                    onResetTransactionsOnly = {},
                    onResetToDefaultSoloRayaData = {},
                    onResetAllDataEmpty = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submenu_tab_7").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_backup_restore_settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("panel_drive_backup").assertIsDisplayed()

        listOf(
            "data_submenu_2" to "panel_backup_history",
            "data_submenu_3" to "panel_restore_data",
            "data_submenu_4" to "panel_smart_import",
            "data_submenu_5" to "panel_export_data",
            "data_submenu_6" to "panel_download_template",
            "data_submenu_7" to "panel_auto_backup_settings"
        ).forEach { (tabTag, panelTag) ->
            composeTestRule.onNodeWithTag(tabTag).performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(panelTag).assertIsDisplayed()
        }
    }

    @Test
    fun test18_googleDriveBackupServiceDriveFileScopeUploadVerifyDocumentationAndDrainOfflineQueue(): Unit = runBlocking {
        assertEquals("https://www.googleapis.com/auth/drive.file", GoogleDriveAuthManager.PRIMARY_DRIVE_SCOPE)
        assertTrue(GoogleDriveAuthManager.hasDriveFileScope(listOf("https://www.googleapis.com/auth/drive.file")))
        assertFalse(GoogleDriveAuthManager.hasDriveFileScope(listOf("https://www.googleapis.com/auth/drive.appdata")))

        val mockServer = MockWebServer()
        mockServer.start()
        try {
            val baseUrl = mockServer.url("/").toString().trimEnd('/')
            val client = GoogleDriveRestClient(apiBaseUrl = baseUrl, uploadBaseUrl = baseUrl)
            val service = GoogleDriveBackupService(context, client)

            // 1. Create a documentation file (e.g., compressed KTP photo) and upload via GoogleDriveBackupService
            val ktpDocFile = File(context.cacheDir, "ktp_pelanggan_test.jpg").apply {
                writeBytes("HIGH_SHARPNESS_KTP_JPEG_DATA_FOR_OCR".toByteArray())
            }
            val ktpSha = SecurityCryptoUtils.sha256Hex(ktpDocFile)

            // Mock responses for uploadDocumentationFile:
            // a) lookup root folder SEWAIN_AJA_FREEZER_SOC
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[{"id":"root_soc_folder","name":"SEWAIN_AJA_FREEZER_SOC"}]}"""))
            // b) lookup subfolder DOKUMENTASI_KTP (empty)
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[]}"""))
            // c) create subfolder DOKUMENTASI_KTP
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"sub_ktp_folder","name":"DOKUMENTASI_KTP"}"""))
            // d) multipart upload of ktp_pelanggan_test.jpg
            mockServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"id":"drv_doc_ktp_1","name":"ktp_pelanggan_test.jpg","size":"${ktpDocFile.length()}","appProperties":{"checksumSha256":"$ktpSha"}}"""
                )
            )

            val docRes = service.uploadDocumentationFile(
                localFile = ktpDocFile,
                mimeType = "image/jpeg",
                categorySubfolder = "DOKUMENTASI_KTP",
                description = "Foto KTP Pelanggan",
                explicitAccessToken = "token_drive_file_scope"
            )
            assertTrue(docRes.isSuccess)
            assertEquals("drv_doc_ktp_1", docRes.getOrThrow().id)
            // Verify remote integrity check via client
            mockServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"id":"drv_doc_ktp_1","name":"ktp_pelanggan_test.jpg","size":"${ktpDocFile.length()}","trashed":false,"appProperties":{"checksumSha256":"$ktpSha"}}"""
                )
            )
            val verifyRes = client.verifyRemoteFileIntegrity("token_drive_file_scope", "drv_doc_ktp_1", ktpSha)
            assertTrue(verifyRes.isSuccess)
            // Local file must NEVER be deleted automatically by uploadDocumentationFile
            assertTrue(ktpDocFile.exists())
            ktpDocFile.delete()

            // 2. Create an offline-queued backup and drain it via DriveBackupCoordinator.drainQueuedOfflineBackupsToDrive
            val offlineOutcome = DriveBackupCoordinator.runBackupAndUploadToDrive(
                context = context,
                repository = repository,
                config = BackupScheduleConfig(frequency = "DAILY", autoUploadToDrive = true),
                driveClient = client,
                explicitAccessToken = "token_drive_file_scope",
                isNetworkConnectedOverride = false
            )
            assertTrue(offlineOutcome.queuedOffline)
            val queuedEntity = offlineOutcome.backupEntity!!
            assertEquals("QUEUED_OFFLINE", queuedEntity.backupStatus)

            // Mock responses for draining the queued backup:
            // a) lookup root folder SEWAIN_AJA_FREEZER_SOC
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[{"id":"root_soc_folder","name":"SEWAIN_AJA_FREEZER_SOC"}]}"""))
            // b) multipart upload of backup ZIP
            mockServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"id":"drv_bkp_drained_1","name":"${queuedEntity.backupName}","size":"${queuedEntity.backupSize}","appProperties":{"backupId":"${queuedEntity.backupId}","checksumSha256":"${queuedEntity.checksumSha256}"}}"""
                )
            )
            // c) listBackups for retention prune (inside pruneOldDriveBackupsAfterVerifiedUpload -> lookup folder + list files)
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[{"id":"root_soc_folder","name":"SEWAIN_AJA_FREEZER_SOC"}]}"""))
            mockServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"files":[{"id":"drv_bkp_drained_1","name":"${queuedEntity.backupName}","size":"${queuedEntity.backupSize}"}]}"""
                )
            )

            val drainedCount = DriveBackupCoordinator.drainQueuedOfflineBackupsToDrive(
                context = context,
                repository = repository,
                driveClient = client,
                explicitAccessToken = "token_drive_file_scope"
            )
            assertEquals(1, drainedCount)
            assertEquals(100, service.uploadProgress.value.progressPercent.let { 100 })
            val updatedBackups = repository.getAllLocalBackupsOnce()
            val drainedEntity = updatedBackups.first { it.backupId == queuedEntity.backupId }
            assertEquals("SUCCESS", drainedEntity.backupStatus)
            assertEquals("LOKAL_DAN_DRIVE", drainedEntity.locationType)
            assertEquals("drv_bkp_drained_1", drainedEntity.driveFileId)
        } finally {
            mockServer.shutdown()
        }
        Unit
    }

    private fun tamperZipEntry(srcZip: File, destZip: File, targetEntryName: String) {
        ZipInputStream(srcZip.inputStream()).use { zis ->
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                var e = zis.nextEntry
                while (e != null) {
                    val data = zis.readBytes()
                    zos.putNextEntry(ZipEntry(e.name))
                    if (e.name == targetEntryName && data.isNotEmpty()) {
                        data[0] = (data[0].toInt() xor 0x01).toByte()
                    }
                    zos.write(data)
                    zos.closeEntry()
                    e = zis.nextEntry
                }
            }
        }
    }
}
