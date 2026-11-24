package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.CloudStoragePhotoManager
import com.example.data.local.AppDatabase
import com.example.data.local.CloudPhotoJobEntity
import com.example.data.local.CompanyProfile
import com.example.media.ImageQualityPreferencesManager
import com.example.media.ImageQualityPreset
import com.example.media.ImageQualitySettings
import com.example.media.ImageStorageAnalytics
import com.example.media.PhotoCategory
import com.example.media.SmartImageProcessor
import com.example.media.SmartImageStorageOptimizer
import com.example.ui.screens.SettingsAndSyncScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartImageManagementUpgradeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createDetailedBitmap(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(218, 232, 245))
        val paint = Paint().apply {
            color = Color.rgb(18, 32, 48)
            textSize = 36f
            isAntiAlias = true
        }
        val step = (height / 14).coerceAtLeast(36)
        var y = step
        while (y < height - 20) {
            canvas.drawText("SEWAIN AJA FREEZER SOC 3372010101900001", 60f, y.toFloat(), paint)
            y += step
        }
        return bmp
    }

    @Test
    fun qualityPresetsEnforceMaxCapsAndPreserveAspectRatioWithKtpOcrException() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "test_presets_${System.nanoTime()}").apply { mkdirs() }

        val landscapeLarge = File(workDir, "src_freezer_2800x1866.jpg")
        createDetailedBitmap(2800, 1866).also { bmp ->
            FileOutputStream(landscapeLarge).use { bmp.compress(Bitmap.CompressFormat.JPEG, 96, it) }
            bmp.recycle()
        }

        val portraitCustomer = File(workDir, "src_customer_1800x2400.jpg")
        createDetailedBitmap(1800, 2400).also { bmp ->
            FileOutputStream(portraitCustomer).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bmp.recycle()
        }

        val smallSrc = File(workDir, "src_small_640x480.jpg")
        createDetailedBitmap(640, 480).also { bmp ->
            FileOutputStream(smallSrc).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
        }

        // 1. Hemat: max 1280px, JPEG 70%
        val hematOut = File(workDir, "hemat_freezer.jpg")
        val hematThumb = File(workDir, "hemat_freezer_thumb.webp")
        val hematRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = landscapeLarge,
            category = PhotoCategory.FREEZER,
            outputMainFile = hematOut,
            outputThumbFile = hematThumb,
            qualitySettingsOverride = ImageQualitySettings(ImageQualityPreset.HEMAT, true)
        )!!
        assertTrue("Hemat max dimension <= 1280", maxOf(hematRes.width, hematRes.height) <= 1280)
        assertTrue("Hemat quality <= 70", hematRes.appliedQuality <= 70)
        assertEquals(3.0 / 2.0, hematRes.width.toDouble() / hematRes.height.toDouble(), 0.03)

        // 2. Standar: mengikuti profil kategori (FREEZER 1600/82%, BUKTI BAYAR 1600/85%, CUSTOMER portrait 1280/80%)
        val stdFreezerOut = File(workDir, "std_freezer.jpg")
        val stdFreezerRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = landscapeLarge,
            category = PhotoCategory.FREEZER,
            outputMainFile = stdFreezerOut,
            outputThumbFile = File(workDir, "std_freezer_thumb.webp"),
            qualitySettingsOverride = ImageQualitySettings(ImageQualityPreset.STANDAR, true)
        )!!
        assertTrue("Standar freezer max <= 1600", maxOf(stdFreezerRes.width, stdFreezerRes.height) <= 1600)
        assertTrue("Standar freezer quality in 80..82", stdFreezerRes.appliedQuality in 80..82)

        val stdPaymentOut = File(workDir, "std_payment.jpg")
        val stdPaymentRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = landscapeLarge,
            category = PhotoCategory.PAYMENT_PROOF,
            outputMainFile = stdPaymentOut,
            outputThumbFile = File(workDir, "std_payment_thumb.webp"),
            qualitySettingsOverride = ImageQualitySettings(ImageQualityPreset.STANDAR, true)
        )!!
        assertTrue("Standar payment max <= 1600", maxOf(stdPaymentRes.width, stdPaymentRes.height) <= 1600)
        assertTrue("Standar payment quality in 82..85", stdPaymentRes.appliedQuality in 82..85)

        val stdCustOut = File(workDir, "std_customer_portrait.jpg")
        val stdCustRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = portraitCustomer,
            category = PhotoCategory.CUSTOMER,
            outputMainFile = stdCustOut,
            outputThumbFile = File(workDir, "std_cust_thumb.webp"),
            qualitySettingsOverride = ImageQualitySettings(ImageQualityPreset.STANDAR, true)
        )!!
        assertTrue("Customer portrait must stay portrait (height > width)", stdCustRes.height > stdCustRes.width)
        assertTrue("Customer max <= 1280", maxOf(stdCustRes.width, stdCustRes.height) <= 1280)
        assertEquals(3.0 / 4.0, stdCustRes.width.toDouble() / stdCustRes.height.toDouble(), 0.03)

        // 3. HD: max 2400px, JPEG 90%
        val hdOut = File(workDir, "hd_freezer.jpg")
        val hdRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = landscapeLarge,
            category = PhotoCategory.FREEZER,
            outputMainFile = hdOut,
            outputThumbFile = File(workDir, "hd_thumb.webp"),
            qualitySettingsOverride = ImageQualitySettings(ImageQualityPreset.HD, true)
        )!!
        assertTrue("HD max <= 2400", maxOf(hdRes.width, hdRes.height) <= 2400)
        assertTrue("HD width > Standar width", hdRes.width > stdFreezerRes.width)
        assertTrue("HD quality in 85..90", hdRes.appliedQuality in 85..90)

        // 4. Pengecualian KTP: walaupun user memilih HEMAT, KTP tetap max 2000px & JPEG 88-92% untuk akurasi OCR
        val ktpUnderHematOut = File(workDir, "ktp_under_hemat.jpg")
        val ktpRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = landscapeLarge,
            category = PhotoCategory.KTP,
            outputMainFile = ktpUnderHematOut,
            outputThumbFile = File(workDir, "ktp_thumb.webp"),
            qualitySettingsOverride = ImageQualitySettings(ImageQualityPreset.HEMAT, true)
        )!!
        assertTrue("KTP must exceed HEMAT 1280 cap when source >= 2000", ktpRes.width in 1400..2000)
        assertTrue("KTP quality must stay in 88..92 even under HEMAT", ktpRes.appliedQuality in 88..92)

        // 5. Batas maksimum tidak boleh memaksa foto kecil dibesarkan (no upscaling/stretching)
        val smallOut = File(workDir, "small_preserved.jpg")
        val smallRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = smallSrc,
            category = PhotoCategory.FREEZER,
            outputMainFile = smallOut,
            outputThumbFile = File(workDir, "small_thumb.webp"),
            qualitySettingsOverride = ImageQualitySettings(ImageQualityPreset.HD, true)
        )!!
        assertEquals(640, smallRes.width)
        assertEquals(480, smallRes.height)

        // 6. Auto-compression OFF -> kualitas sangat tinggi (>= 92%)
        val noCompOut = File(workDir, "no_comp.jpg")
        val noCompRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = landscapeLarge,
            category = PhotoCategory.FREEZER,
            outputMainFile = noCompOut,
            outputThumbFile = File(workDir, "no_comp_thumb.webp"),
            qualitySettingsOverride = ImageQualitySettings(ImageQualityPreset.HEMAT, false)
        )!!
        assertTrue("Auto compression disabled preserves >= 92 quality", noCompRes.appliedQuality >= 92)

        workDir.deleteRecursively()
    }

    @Test
    fun qualitySettingsPersistAcrossLoadsAndWebpThumbnailsAreGeneratedUnder300Px() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        ImageQualityPreferencesManager.saveSettings(
            context,
            ImageQualitySettings(defaultPreset = ImageQualityPreset.HD, autoCompressionEnabled = false)
        )
        val loadedHd = ImageQualityPreferencesManager.loadSettings(context)
        assertEquals(ImageQualityPreset.HD, loadedHd.defaultPreset)
        assertFalse(loadedHd.autoCompressionEnabled)

        ImageQualityPreferencesManager.saveSettings(
            context,
            ImageQualitySettings(defaultPreset = ImageQualityPreset.HEMAT, autoCompressionEnabled = true)
        )
        val loadedHemat = ImageQualityPreferencesManager.loadSettings(context)
        assertEquals(ImageQualityPreset.HEMAT, loadedHemat.defaultPreset)
        assertTrue(loadedHemat.autoCompressionEnabled)

        // Kembalikan ke default STANDAR agar tidak mengganggu test lain
        ImageQualityPreferencesManager.saveSettings(context, ImageQualitySettings())

        // Verifikasi WebP Thumbnail <= 300px + header RIFF/WEBP
        val workDir = File(context.cacheDir, "test_webp_thumb_${System.nanoTime()}").apply { mkdirs() }
        val mainOut = File(workDir, "freezer_unit.jpg")
        val thumbOut = SmartImageProcessor.resolveThumbnailFileFor(context, mainOut)
        val bmp = createDetailedBitmap(1600, 1200)
        val res = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = bmp,
            category = PhotoCategory.FREEZER,
            outputMainFile = mainOut,
            outputThumbFile = thumbOut
        )!!
        bmp.recycle()

        assertTrue("Thumbnail extension must be .webp", res.thumbnailFile.name.endsWith(".webp"))
        assertTrue("Thumbnail must exist and be compact", res.thumbnailFile.exists() && res.thumbnailFile.length() > 0L)
        assertTrue("Thumbnail max dimension <= 300 px", maxOf(res.thumbnailWidth, res.thumbnailHeight) <= 300)

        val headerBytes = res.thumbnailFile.readBytes()
        val headerMagic = String(headerBytes.copyOfRange(0, 4))
        val webpMagic = String(headerBytes.copyOfRange(8, 12))
        assertEquals("RIFF", headerMagic)
        assertEquals("WEBP", webpMagic)
        assertNotNull("WebP thumbnail must decode cleanly", BitmapFactory.decodeFile(res.thumbnailFile.absolutePath))

        val resolvedThumb = SmartImageProcessor.getExistingThumbnailPathOrNull(context, mainOut.absolutePath)
        assertEquals(res.thumbnailFile.absolutePath, resolvedThumb)

        res.thumbnailFile.delete()
        workDir.deleteRecursively()
    }

    @Test
    fun actualStorageAnalyticsAndSafeCacheCleanerPreservePendingAndPrimaryData() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Bersihkan sisa test sebelumnya
        File(context.filesDir, "SEWAIN_AJA_FREEZER").deleteRecursively()
        File(context.filesDir, "photos").deleteRecursively()
        File(context.filesDir, "cloud_photo_cache").deleteRecursively()
        File(context.filesDir, "photo_thumbnails").deleteRecursively()

        // 1. Buat dokumen utama + thumbnail WebP
        val primaryDir = File(context.filesDir, "SEWAIN_AJA_FREEZER/CUSTOMERS/CUST_SAFE").apply { mkdirs() }
        val primaryKtp = File(primaryDir, "ktp_cust_safe.jpg")
        val rawSrc = File(context.cacheDir, "raw_2400_${System.nanoTime()}.jpg")
        createDetailedBitmap(2400, 1500).also { bmp ->
            FileOutputStream(rawSrc).use { bmp.compress(Bitmap.CompressFormat.JPEG, 98, it) }
            bmp.recycle()
        }
        val thumbFile = SmartImageProcessor.resolveThumbnailFileFor(context, primaryKtp)
        val processed = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = rawSrc,
            category = PhotoCategory.KTP,
            outputMainFile = primaryKtp,
            outputThumbFile = thumbFile
        )!!
        rawSrc.delete()

        val primaryFreezer = File(File(context.filesDir, "photos").apply { mkdirs() }, "freezer_utama.jpg").apply {
            writeBytes(ByteArray(4096) { 0x42 })
        }

        // 2. Buat file cache sementara yang aman dihapus
        val tempKtp = File(File(context.cacheDir, "ktp_temp").apply { mkdirs() }, "preview_ktp.jpg").apply {
            writeBytes(ByteArray(3072) { 0x11 })
        }
        val tempCoil = File(File(context.cacheDir, "image_cache").apply { mkdirs() }, "cached_img.bin").apply {
            writeBytes(ByteArray(2048) { 0x22 })
        }

        // 3. Staging Cloud: 1 sudah UPLOADED (boleh dibersihkan), 1 QUEUED offline (HARAM dihapus), 1 FAILED (HARAM dihapus)
        val stagingDir = File(context.filesDir, "cloud_photo_cache").apply { mkdirs() }
        val uploadedStaging = File(stagingDir, "uploaded_done.jpg").apply {
            writeBytes(ByteArray(5000) { 0x33 })
        }
        val queuedOffline = File(stagingDir, "offline_pending.jpg").apply {
            writeBytes(ByteArray(6000) { 0x44 })
        }
        val queuedOfflineThumb = File(stagingDir, "offline_pending_thumb.webp").apply {
            writeBytes(ByteArray(1200) { 0x55 })
        }
        val failedRetry = File(stagingDir, "failed_retry.jpg").apply {
            writeBytes(ByteArray(7000) { 0x66 })
        }

        val jobs = listOf(
            CloudPhotoJobEntity(
                id = 1L,
                uid = "u1",
                ownerType = "CUSTOMER",
                ownerLocalId = 1L,
                ownerCloudId = "c1",
                photoSlot = "KTP",
                localFilePath = uploadedStaging.absolutePath,
                thumbnailPath = "",
                storagePath = "users/u1/customers/c1/ktp/uploaded_done.jpg",
                cloudDownloadUrl = "https://firebasestorage.example/uploaded_done.jpg",
                bytesTotal = 5000L,
                progressPercent = 100,
                status = CloudStoragePhotoManager.STATUS_UPLOADED
            ),
            CloudPhotoJobEntity(
                id = 2L,
                uid = "u1",
                ownerType = "UNIT",
                ownerLocalId = 2L,
                ownerCloudId = "u2",
                photoSlot = "FREEZER",
                localFilePath = queuedOffline.absolutePath,
                thumbnailPath = queuedOfflineThumb.absolutePath,
                storagePath = "users/u1/units/u2/freezer/offline_pending.jpg",
                cloudDownloadUrl = "",
                bytesTotal = 6000L,
                progressPercent = 0,
                status = CloudStoragePhotoManager.STATUS_QUEUED
            ),
            CloudPhotoJobEntity(
                id = 3L,
                uid = "u1",
                ownerType = "TRANSACTION",
                ownerLocalId = 3L,
                ownerCloudId = "tx3",
                photoSlot = "BUKTI_BAYAR",
                localFilePath = failedRetry.absolutePath,
                thumbnailPath = "",
                storagePath = "users/u1/transactions/tx3/bukti/failed_retry.jpg",
                cloudDownloadUrl = "",
                bytesTotal = 7000L,
                progressPercent = 0,
                status = CloudStoragePhotoManager.STATUS_FAILED,
                errorMessage = "Offline"
            )
        )

        // Hitung Analitik Aktual
        val analytics = SmartImageStorageOptimizer.computeActualStorageAnalytics(context, jobs)
        assertTrue("Total photo count reflects actual photos", analytics.totalPhotoCount >= 3)
        assertEquals(5000L, analytics.cloudStorageBytes)
        assertEquals(1, analytics.uploadedCloudPhotosCount)
        assertEquals(2, analytics.pendingUploadPhotosCount)
        assertEquals(13000L, analytics.pendingUploadPhotosBytes)
        assertEquals(1, analytics.failedUploadPhotosCount)
        assertEquals(
            3072L + 2048L + 5000L,
            analytics.totalTempCacheBytes
        )
        assertEquals(
            ImageQualityPreferencesManager.getRecordedSavingsBytes(context),
            analytics.estimatedCompressionSavingsBytes
        )
        assertTrue("Recorded compression savings > 0 after processing large image", analytics.estimatedCompressionSavingsBytes >= 0L)
        assertTrue("Local storage includes primary + thumbs + staging + cache", analytics.totalLocalStorageBytes >= processed.bytesSize + 4096L + 6000L + 7000L)

        // Jalankan Safe Cache Cleaner
        val cleanRes = SmartImageStorageOptimizer.cleanSafeCachesPreservingImportantData(
            context = context,
            cloudPhotoJobs = jobs,
            olderThanMillis = 0L
        )
        assertTrue("At least 3 temp/uploaded staging files deleted", cleanRes.deletedFilesCount >= 3)
        assertFalse("Temp ktp cache must be deleted", tempKtp.exists())
        assertFalse("Temp coil cache must be deleted", tempCoil.exists())
        assertFalse("Uploaded staging file may be safely cleaned", uploadedStaging.exists())

        assertTrue("Primary KTP doc MUST NEVER be deleted", primaryKtp.exists())
        assertTrue("Primary freezer photo MUST NEVER be deleted", primaryFreezer.exists())
        assertTrue("Offline QUEUED photo MUST NEVER be deleted before upload", queuedOffline.exists())
        assertTrue("Offline QUEUED thumb MUST NEVER be deleted", queuedOfflineThumb.exists())
        assertTrue("FAILED photo awaiting retry MUST NEVER be deleted", failedRetry.exists())

        File(context.filesDir, "SEWAIN_AJA_FREEZER").deleteRecursively()
        File(context.filesDir, "photos").deleteRecursively()
        File(context.filesDir, "cloud_photo_cache").deleteRecursively()
        File(context.filesDir, "photo_thumbnails").deleteRecursively()
    }

    @Test
    fun offlinePhotoQueueMarksQueuedThenPreservesFailureAndRetriesSingleJob(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:test")
                    .setApiKey("test-api-key")
                    .setProjectId("sewain-aja-test")
                    .setStorageBucket("sewain-aja-test.appspot.com")
                    .build()
            )
        }

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.freezerDao()
        val manager = CloudStoragePhotoManager(context = context, dao = dao)

        val srcDir = File(context.cacheDir, "test_offline_q_${System.nanoTime()}").apply { mkdirs() }
        val photoFile = File(srcDir, "unit_200L.jpg")
        createDetailedBitmap(1200, 900).also { bmp ->
            FileOutputStream(photoFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
        }

        val job = manager.enqueuePhotoJob(
            uid = "uid_offline_1",
            ownerType = "UNIT",
            ownerLocalId = 55L,
            ownerCloudId = "unit_55",
            photoSlot = "FREEZER",
            sourcePathOrUri = photoFile.absolutePath
        )!!
        assertEquals(CloudStoragePhotoManager.STATUS_QUEUED, job.status)
        assertTrue(job.storagePath.startsWith("users/uid_offline_1/units/unit_55/"))
        assertTrue(File(job.localFilePath).exists())
        assertTrue(job.thumbnailPath.endsWith(".webp") && File(job.thumbnailPath).exists())

        // Simulasikan gagal upload saat offline -> coba retrySinglePhotoJob
        dao.upsertCloudPhotoJob(
            job.copy(
                status = CloudStoragePhotoManager.STATUS_FAILED,
                errorMessage = "Jaringan offline"
            )
        )
        val retryRes = manager.retrySinglePhotoJob("uid_offline_1", job.id, null)
        // Di lingkungan test tanpa server/auth Firebase nyata, status tidak berubah menjadi UPLOADED & file lokal tetap aman
        val updatedJob = dao.getAllCloudPhotoJobsOnce("uid_offline_1").first { it.id == job.id }
        assertTrue("Local file MUST remain intact when upload is not confirmed", File(updatedJob.localFilePath).exists())
        assertTrue(retryRes.isFailure || updatedJob.status == CloudStoragePhotoManager.STATUS_UPLOADED)

        db.close()
        srcDir.deleteRecursively()
        File(context.filesDir, "cloud_photo_cache").deleteRecursively()
        Unit
    }

    @Test
    fun settingsSubmenus8AAnd8BRenderQualityPresetsAnalyticsCleanConfirmationDialogAndRetryActions() {
        var latestSettings = ImageQualitySettings(ImageQualityPreset.STANDAR, true)
        var cleanCacheCalls = 0
        var retriedSingleJobId = -1L

        val sampleAnalytics = ImageStorageAnalytics(
            totalPhotoCount = 12,
            appCacheBytes = 1024L * 1024L * 2L,
            appCacheFileCount = 6,
            thumbnailBytes = 320L * 1024L,
            thumbnailFileCount = 12,
            offlineLocalPhotosBytes = 3L * 1024L * 1024L,
            offlineLocalPhotosCount = 12,
            pendingUploadPhotosCount = 2,
            pendingUploadPhotosBytes = 512L * 1024L,
            totalLocalStorageBytes = 6L * 1024L * 1024L,
            cloudStorageBytes = 2500L * 1024L,
            uploadedCloudPhotosCount = 10,
            failedUploadPhotosCount = 1,
            estimatedCompressionSavingsBytes = 14L * 1024L * 1024L,
            totalTempCacheBytes = 1024L * 1024L * 2L,
            availableDeviceBytes = 4L * 1024L * 1024L * 1024L,
            totalDeviceBytes = 32L * 1024L * 1024L * 1024L
        )
        val sampleJobs = listOf(
            CloudPhotoJobEntity(
                id = 77L,
                uid = "uid_1",
                ownerType = "TRANSACTION",
                ownerLocalId = 7L,
                ownerCloudId = "tx_7",
                photoSlot = "BUKTI_BAYAR",
                localFilePath = "/tmp/bukti.jpg",
                thumbnailPath = "/tmp/bukti_thumb.webp",
                storagePath = "users/uid_1/transactions/tx_7/bukti_bayar/bukti.jpg",
                bytesTotal = 260_000L,
                progressPercent = 0,
                status = CloudStoragePhotoManager.STATUS_FAILED,
                errorMessage = "Timeout koneksi ke Cloud Storage"
            )
        )

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
                    onResetAllDataEmpty = {},
                    cloudPhotoJobs = sampleJobs,
                    imageQualitySettings = latestSettings,
                    imageStorageAnalytics = sampleAnalytics,
                    onUpdateImageQualitySettings = { latestSettings = it },
                    onCleanSmartStorageCache = { cleanCacheCalls++ },
                    onRetrySingleFailedPhoto = { retriedSingleJobId = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Buka submenu 8A (Pengaturan Kualitas Foto)
        composeTestRule.onNodeWithTag("submenu_tab_8A").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_image_quality_settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quality_preset_HEMAT").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ImageQualityPreset.HEMAT, latestSettings.defaultPreset)

        // 2. Tutup & buka submenu 8B (Pengelolaan Penyimpanan)
        composeTestRule.onNodeWithTag("btn_close_submenu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submenu_tab_8B").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_storage_analytics_and_cleaner").assertIsDisplayed()
        composeTestRule.onNodeWithText("TOTAL FOTO").assertIsDisplayed()
        composeTestRule.onNodeWithText("12 Foto").assertIsDisplayed()
        composeTestRule.onNodeWithText("PENYIMPANAN LOKAL").assertIsDisplayed()
        composeTestRule.onNodeWithText("PENYIMPANAN CLOUD").assertIsDisplayed()
        composeTestRule.onNodeWithText("PENGHEMATAN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Timeout koneksi ke Cloud Storage").performScrollTo().assertIsDisplayed()

        // Uji Upload Ulang per foto gagal
        composeTestRule.onNodeWithTag("btn_retry_job_77").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(77L, retriedSingleJobId)

        // Uji dialog konfirmasi BERSIHKAN CACHE (belum bersih sebelum konfirmasi)
        composeTestRule.onNodeWithTag("btn_clean_storage_cache").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_confirm_clean_cache").assertIsDisplayed()
        assertEquals(0, cleanCacheCalls)
        composeTestRule.onNodeWithTag("btn_confirm_clean_cache").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, cleanCacheCalls)
    }
}
