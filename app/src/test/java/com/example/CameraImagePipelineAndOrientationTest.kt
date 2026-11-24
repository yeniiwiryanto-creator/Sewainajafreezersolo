package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import androidx.camera.core.ImageCapture
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.CloudStoragePhotoManager
import com.example.cloud.GoogleAuthGateScreen
import com.example.data.local.AppDatabase
import com.example.media.PhotoCategory
import com.example.media.PreferredCompressionFormat
import com.example.media.SmartImageProcessor
import com.example.media.SmartImageStorageOptimizer
import com.example.ocr.KtpCameraViewfinder
import com.example.ocr.KtpImageQualityReport
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class CameraImagePipelineAndOrientationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createQuadrantLandscapeBitmap(width: Int = 480, height: Int = 240): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val hw = width / 2
        val hh = height / 2
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = when {
                    x < hw && y < hh -> Color.RED
                    x >= hw && y < hh -> Color.GREEN
                    x < hw && y >= hh -> Color.BLUE
                    else -> Color.YELLOW
                }
            }
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    /**
     * Membalikkan matriks EXIF sehingga bitmap uji yang tersimpan di file membutuhkan
     * koreksi EXIF persis seperti kamera nyata saat menyimpan pixel + tag ORIENTATION_*.
     */
    private fun createPreRotatedSourceForExifTag(upright: Bitmap, orientationTag: Int): Bitmap {
        val forward = SmartImageProcessor.buildExifCorrectionMatrix(orientationTag)
        val inverse = Matrix()
        val invertible = forward.invert(inverse)
        assertTrue("Orientation matrix must be invertible", invertible)
        return Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, inverse, false)
    }

    private fun isCloseColor(pixel: Int, target: Int, tolerance: Int = 65): Boolean {
        return kotlin.math.abs(Color.red(pixel) - Color.red(target)) <= tolerance &&
            kotlin.math.abs(Color.green(pixel) - Color.green(target)) <= tolerance &&
            kotlin.math.abs(Color.blue(pixel) - Color.blue(target)) <= tolerance
    }

    private fun assertQuadrantsUpright(bitmap: Bitmap, label: String) {
        val w = bitmap.width
        val h = bitmap.height
        assertTrue("$label should keep landscape aspect ($w x $h)", w > h)
        val tl = bitmap.getPixel(w / 4, h / 4)
        val tr = bitmap.getPixel((w * 3) / 4, h / 4)
        val bl = bitmap.getPixel(w / 4, (h * 3) / 4)
        val br = bitmap.getPixel((w * 3) / 4, (h * 3) / 4)
        val diag = "tl=(${Color.red(tl)},${Color.green(tl)},${Color.blue(tl)},a=${Color.alpha(tl)}) " +
            "tr=(${Color.red(tr)},${Color.green(tr)},${Color.blue(tr)}) " +
            "bl=(${Color.red(bl)},${Color.green(bl)},${Color.blue(bl)}) " +
            "br=(${Color.red(br)},${Color.green(br)},${Color.blue(br)})"
        assertTrue("$label top-left expected RED [$diag]", isCloseColor(tl, Color.RED))
        assertTrue("$label top-right expected GREEN [$diag]", isCloseColor(tr, Color.GREEN))
        assertTrue("$label bottom-left expected BLUE [$diag]", isCloseColor(bl, Color.BLUE))
        assertTrue("$label bottom-right expected YELLOW [$diag]", isCloseColor(br, Color.YELLOW))
    }

    @Test
    fun allEightExifOrientationsAndFrontCameraMirrorNormalizeWithoutDoubleRotation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "test_exif_norm_${System.nanoTime()}").apply { mkdirs() }
        val upright = createQuadrantLandscapeBitmap(width = 480, height = 240)

        val tags = listOf(
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE
        )

        tags.forEach { tag ->
            val rawBitmap = if (tag == ExifInterface.ORIENTATION_NORMAL) {
                upright
            } else {
                createPreRotatedSourceForExifTag(upright, tag)
            }
            val inputFile = File(workDir, "in_$tag.jpg")
            FileOutputStream(inputFile).use { fos ->
                rawBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            val exif = ExifInterface(inputFile.absolutePath)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, tag.toString())
            exif.saveAttributes()

            val mainOut = File(workDir, "out_$tag.jpg")
            val thumbOut = File(workDir, "thumb_$tag.jpg")
            val res = SmartImageProcessor.processImageSourceToFiles(
                context = context,
                source = inputFile,
                category = PhotoCategory.FREEZER,
                outputMainFile = mainOut,
                outputThumbFile = thumbOut
            )
            assertNotNull("Tag $tag result should not be null", res)
            assertTrue("Output file must be marked ORIENTATION_NORMAL", SmartImageProcessor.isOrientationNormalized(mainOut))
            val decoded = BitmapFactory.decodeFile(mainOut.absolutePath)
            assertNotNull(decoded)
            assertQuadrantsUpright(decoded!!, "EXIF tag=$tag")

            // Double-rotation prevention: proses ulang file hasil harus tetap tegak identik
            val mainOutSecond = File(workDir, "out_reprocess_$tag.jpg")
            val thumbOutSecond = File(workDir, "thumb_reprocess_$tag.jpg")
            val resSecond = SmartImageProcessor.processImageSourceToFiles(
                context = context,
                source = mainOut,
                category = PhotoCategory.FREEZER,
                outputMainFile = mainOutSecond,
                outputThumbFile = thumbOutSecond
            )
            assertNotNull(resSecond)
            assertFalse("Reprocessing normalized file should not report non-normal EXIF", resSecond!!.extractedMetadata.hadNonNormalOrientation)
            val decodedSecond = BitmapFactory.decodeFile(mainOutSecond.absolutePath)!!
            assertQuadrantsUpright(decodedSecond, "Reprocessed EXIF tag=$tag")
        }

        // Front camera mirror undo test
        val mirroredMatrix = Matrix().apply { postScale(-1f, 1f) }
        val mirrored = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, mirroredMatrix, false)
        val frontIn = File(workDir, "front_mirrored.jpg")
        FileOutputStream(frontIn).use { fos -> mirrored.compress(Bitmap.CompressFormat.JPEG, 95, fos) }
        val frontOut = File(workDir, "front_fixed.jpg")
        val frontThumb = File(workDir, "front_thumb.jpg")
        val frontRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = frontIn,
            category = PhotoCategory.CUSTOMER,
            outputMainFile = frontOut,
            outputThumbFile = frontThumb,
            isFrontCamera = true
        )
        assertNotNull(frontRes)
        assertTrue(frontRes!!.wasFrontCameraMirrorFixed)
        val frontDecoded = BitmapFactory.decodeFile(frontOut.absolutePath)!!
        assertQuadrantsUpright(frontDecoded, "Front camera un-mirrored")

        workDir.deleteRecursively()
    }

    @Test
    fun objectAppropriateOrientationPreservesNaturalAspectWithoutForcingLandscape() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "test_aspect_${System.nanoTime()}").apply { mkdirs() }
        val portraitSource = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
        }
        val portraitFile = File(workDir, "portrait_src.jpg")
        FileOutputStream(portraitFile).use { portraitSource.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        val nonKtpCategories = listOf(
            PhotoCategory.FREEZER,
            PhotoCategory.CUSTOMER,
            PhotoCategory.LOCATION,
            PhotoCategory.PAYMENT_PROOF,
            PhotoCategory.GENERAL_DOC
        )
        nonKtpCategories.forEach { category ->
            val out = File(workDir, "out_${category.name}.jpg")
            val thumb = File(workDir, "thumb_${category.name}.jpg")
            val res = SmartImageProcessor.processImageSourceToFiles(
                context = context,
                source = portraitFile,
                category = category,
                outputMainFile = out,
                outputThumbFile = thumb
            )!!
            assertTrue("${category.name} portrait must remain portrait (${res.width}x${res.height})", res.height > res.width)
            val ratio = res.height.toFloat() / res.width.toFloat()
            assertEquals("Aspect ratio must stay 1000/600 without stretching", 1000f / 600f, ratio, 0.05f)
        }

        // KTP portrait photo automatically normalized to landscape ID-1 orientation without stretching
        val ktpOut = File(workDir, "out_KTP.jpg")
        val ktpThumb = File(workDir, "thumb_KTP.jpg")
        val ktpRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = portraitFile,
            category = PhotoCategory.KTP,
            outputMainFile = ktpOut,
            outputThumbFile = ktpThumb
        )!!
        assertTrue("KTP should normalize to landscape (${ktpRes.width}x${ktpRes.height})", ktpRes.width > ktpRes.height)
        val ktpRatio = ktpRes.width.toFloat() / ktpRes.height.toFloat()
        assertEquals("KTP aspect ratio must stay proportional without distortion", 1000f / 600f, ktpRatio, 0.05f)

        workDir.deleteRecursively()
    }

    @Test
    fun categoryCompressionExtractsAndStripsMetadataGeneratesWebpAndThumbnails() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "test_compress_${System.nanoTime()}").apply { mkdirs() }

        // Buat gambar besar 2400x1800 berisi banyak detail garis/teks
        val largeBmp = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(largeBmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 3f
            textSize = 48f
        }
        for (i in 0 until 40) {
            canvas.drawLine(0f, i * 44f, 2400f, i * 44f, paint)
            canvas.drawText("NIK 337201010190000$i SURAKARTA", 80f, i * 44f + 36f, paint)
        }

        val largeFile = File(workDir, "large_with_gps.jpg")
        FileOutputStream(largeFile).use { largeBmp.compress(Bitmap.CompressFormat.JPEG, 96, it) }

        val srcExif = ExifInterface(largeFile.absolutePath)
        srcExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        srcExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:15 09:30:00")
        srcExif.setAttribute(ExifInterface.TAG_MAKE, "Samsung")
        srcExif.setAttribute(ExifInterface.TAG_MODEL, "Galaxy-Test")
        srcExif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "7/1,34/1,3180/100")
        srcExif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
        srcExif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "110/1,49/1,2748/100")
        srcExif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
        srcExif.saveAttributes()

        // 1. Uji setiap kategori batas resolusi & kualitas + strip EXIF
        PhotoCategory.entries.forEach { cat ->
            val out = File(workDir, "comp_${cat.name}.jpg")
            val thumb = File(workDir, "comp_thumb_${cat.name}.jpg")
            val res = SmartImageProcessor.processImageSourceToFiles(
                context = context,
                source = largeFile,
                category = cat,
                outputMainFile = out,
                outputThumbFile = thumb
            )!!

            assertTrue("${cat.name} max dim <= ${cat.maxDimensionPx}", maxOf(res.width, res.height) <= cat.maxDimensionPx)
            assertTrue("${cat.name} applied quality >= ${cat.minQualityFloor}", res.appliedQuality >= cat.minQualityFloor)
            assertTrue("${cat.name} thumbnail <= 300px", maxOf(res.thumbnailWidth, res.thumbnailHeight) <= 300)
            assertTrue("${cat.name} thumbnail file exists & non-empty", thumb.exists() && thumb.length() > 0L)

            // Verifikasi metadata penting diekstrak lebih dulu
            assertEquals("2026:11:15 09:30:00", res.extractedMetadata.dateTimeOriginal)
            assertNotNull(res.extractedMetadata.latitude)
            assertNotNull(res.extractedMetadata.longitude)
            assertEquals(-7.5755, res.extractedMetadata.latitude!!, 0.01)
            assertEquals(110.8243, res.extractedMetadata.longitude!!, 0.01)

            // Verifikasi metadata kamera/lokasi telah dibersihkan dari output file
            val outExif = ExifInterface(out.absolutePath)
            assertEquals(
                ExifInterface.ORIENTATION_NORMAL,
                outExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            )
            val outLatLong = FloatArray(2)
            @Suppress("DEPRECATION")
            assertFalse("Output JPEG GPS must be stripped", outExif.getLatLong(outLatLong))
            assertEquals(null, outExif.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals(null, outExif.getAttribute(ExifInterface.TAG_MODEL))
        }

        // 2. Uji format WebP (WEBP_AUTO)
        val webpOut = File(workDir, "comp_ktp.webp")
        val webpThumb = File(workDir, "comp_ktp_thumb.webp")
        val webpRes = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = largeFile,
            category = PhotoCategory.KTP,
            outputMainFile = webpOut,
            outputThumbFile = webpThumb,
            preferredFormat = PreferredCompressionFormat.WEBP_AUTO
        )!!
        assertEquals("image/webp", webpRes.mimeType)
        assertTrue(webpOut.exists() && webpOut.length() > 0L)
        assertNotNull("WebP output must be decodable", BitmapFactory.decodeFile(webpOut.absolutePath))

        workDir.deleteRecursively()
    }

    @Test
    fun ktpQuickQualityDetectsDarkAndBlurryWhilePreservingSharpKtpOcrReadability() {
        // 1. Kartu gelap sekali -> wajib terdeteksi isTooDark & !isAcceptable
        val darkBmp = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(8, 8, 8))
        }
        val darkReport = SmartImageProcessor.evaluateKtpQuickQuality(darkBmp)
        assertTrue("Very dark bitmap must be flagged isTooDark", darkReport.isTooDark)
        assertFalse(darkReport.isAcceptable)
        assertTrue(darkReport.cameraGuidanceInstructions.any { it.contains("gelap", ignoreCase = true) })

        // 2. Kartu polos / buram (zero detail gradient) -> wajib terdeteksi isBlurry / isUnreadable
        val blurryBmp = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(160, 175, 185))
        }
        val blurryReport = SmartImageProcessor.evaluateKtpQuickQuality(blurryBmp)
        assertTrue("Low-edge bitmap must flag blurry or unreadable", blurryReport.isBlurry || blurryReport.isUnreadable)

        // 3. Kartu kontras tajam berisi pola garis teks KTP -> wajib tajam & acceptable
        val w = 1280
        val h = 800
        val sharpKtp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val ktpPixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val insideCard = x in 80..(w - 80) && y in 60..(h - 60)
                val isTextStroke = insideCard &&
                    (y in 120..145 || y in 210..235 || y in 280..305 || y in 350..375) &&
                    ((x / 12) % 2 == 0) && x in 140..900
                ktpPixels[rowOffset + x] = when {
                    !insideCard -> Color.rgb(20, 25, 30)
                    isTextStroke -> Color.BLACK
                    else -> Color.rgb(190, 225, 240)
                }
            }
        }
        sharpKtp.setPixels(ktpPixels, 0, w, 0, 0, w, h)
        val sharpReport = SmartImageProcessor.evaluateKtpQuickQuality(sharpKtp)
        assertFalse("Clear KTP card should not be too dark", sharpReport.isTooDark)
        assertTrue("Clear KTP card should have high sharpnessScore", sharpReport.sharpnessScore > 10.0)
    }

    @Test
    fun tieredStorageSafeCacheCleanupPreservesPrimaryDocumentationPhotos() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Temp cache files (harus dibersihkan)
        val tempKtp = File(File(context.cacheDir, "ktp_temp").apply { mkdirs() }, "tmp_ktp.jpg").apply {
            writeText("temp_ktp_bytes")
        }
        val tempCloudImport = File(File(context.cacheDir, "cloud_uri_imports").apply { mkdirs() }, "import_1.jpg").apply {
            writeText("temp_import_bytes")
        }
        val tempImageCache = File(File(context.cacheDir, "image_cache").apply { mkdirs() }, "cached_1.bin").apply {
            writeText("temp_cache_bytes")
        }
        val tempRootPrefix = File(context.cacheDir, "captured_ktp_999.jpg").apply {
            writeText("temp_captured_bytes")
        }

        // Primary files (DILARANG KERAS terhapus)
        val primaryDoc = File(File(context.filesDir, "SEWAIN_AJA_FREEZER/customers/CUST001").apply { mkdirs() }, "ktp.jpg").apply {
            writeText("primary_ktp_important")
        }
        val primaryPhoto = File(File(context.filesDir, "photos").apply { mkdirs() }, "unit_01.jpg").apply {
            writeText("primary_unit_photo")
        }
        val primaryKtpDoc = File(File(context.filesDir, "ktp_documents").apply { mkdirs() }, "ktp_safe.jpg").apply {
            writeText("primary_ktp_doc")
        }

        val cleanup = SmartImageStorageOptimizer.cleanupTemporaryImageCaches(
            context = context,
            olderThanMillis = 0L
        )
        assertTrue("At least 4 temp files must be cleaned", cleanup.deletedFilesCount >= 4)
        assertTrue("Freed bytes should be > 0", cleanup.freedBytes > 0L)
        assertFalse(tempKtp.exists())
        assertFalse(tempCloudImport.exists())
        assertFalse(tempImageCache.exists())
        assertFalse(tempRootPrefix.exists())

        assertTrue("Primary SEWAIN_AJA_FREEZER doc MUST stay intact", primaryDoc.exists())
        assertTrue("Primary photos MUST stay intact", primaryPhoto.exists())
        assertTrue("Primary ktp_documents MUST stay intact", primaryKtpDoc.exists())

        primaryDoc.delete()
        primaryPhoto.delete()
        primaryKtpDoc.delete()
    }

    @Test
    fun cloudPhotoJobDeduplicatesIdenticalUploadsAndAvoidsBase64Storage(): Unit = runBlocking {
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

        val srcDir = File(context.cacheDir, "test_cloud_dedup_${System.nanoTime()}").apply { mkdirs() }
        val bmp = createQuadrantLandscapeBitmap(width = 800, height = 500)
        val photoFile = File(srcDir, "ktp_budi.jpg")
        FileOutputStream(photoFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }

        val firstJob = manager.enqueuePhotoJob(
            uid = "uid_test_1",
            ownerType = "CUSTOMER",
            ownerLocalId = 10L,
            ownerCloudId = "cust_10",
            photoSlot = "KTP",
            sourcePathOrUri = photoFile.absolutePath
        )
        assertNotNull(firstJob)
        assertTrue("Storage path must follow structured path", firstJob!!.storagePath.startsWith("users/uid_test_1/customers/cust_10/ktp/"))
        assertFalse("Must never store Base64 data URLs", firstJob.storagePath.contains("base64") || firstJob.localFilePath.contains("data:image"))
        assertTrue("Thumbnail path must be generated", firstJob.thumbnailPath.isNotBlank() && File(firstJob.thumbnailPath).exists())

        // Enqueue identik kedua kalinya -> wajib di-skip (tidak ada duplikasi job di Room)
        val secondJob = manager.enqueuePhotoJob(
            uid = "uid_test_1",
            ownerType = "CUSTOMER",
            ownerLocalId = 10L,
            ownerCloudId = "cust_10",
            photoSlot = "KTP",
            sourcePathOrUri = photoFile.absolutePath
        )
        assertNotNull(secondJob)
        assertEquals(firstJob.id, secondJob!!.id)
        assertEquals(1, dao.getAllCloudPhotoJobsOnce("uid_test_1").size)

        db.close()
        srcDir.deleteRecursively()
        Unit
    }

    @Test
    fun ktpCameraViewfinderDisplaysConfirmationReviewQualityBannerAndHandlesRetakeUseActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previewBmp = createQuadrantLandscapeBitmap(width = 640, height = 400)
        val qualityWarning = KtpImageQualityReport(
            isTooDark = true,
            isBlurry = false,
            cardAreaRatio = 0.72
        )
        var retaken = false
        var confirmed = false

        composeTestRule.setContent {
            MaterialTheme {
                KtpCameraViewfinder(
                    imageCapture = ImageCapture.Builder().build(),
                    capturedPreviewBitmap = previewBmp,
                    capturedQualityReport = qualityWarning,
                    onRetakeClick = { retaken = true },
                    onConfirmUsePhotoClick = { confirmed = true },
                    onCaptureClick = {},
                    onCloseClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("KONFIRMASI FOTO KTP").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ktp_review_quality_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_retake_photo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_use_photo").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_retake_photo").performClick()
        composeTestRule.onNodeWithTag("btn_use_photo").performClick()
        composeTestRule.waitForIdle()

        assertTrue("Retake callback must fire", retaken)
        assertTrue("Confirm use photo callback must fire", confirmed)
    }

    @Test
    fun googleAuthGateScreenRendersBrandLogoAndSignInButtonWithoutDrawableCrash() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                GoogleAuthGateScreen(
                    isLoading = false,
                    errorMessage = null,
                    onGoogleSignInClick = { clicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SEWAin Aja Freezer SOC").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Google sign-in button callback must fire", clicked)
    }
}
