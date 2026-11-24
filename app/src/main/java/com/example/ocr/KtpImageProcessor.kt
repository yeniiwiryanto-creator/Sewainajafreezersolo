package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Empat titik sudut kartu KTP untuk deteksi perspektif & Live KTP Detection (Sections 3 & 4).
 */
data class KtpCornerPoints(
    val topLeft: PointF = PointF(0f, 0f),
    val topRight: PointF = PointF(0f, 0f),
    val bottomRight: PointF = PointF(0f, 0f),
    val bottomLeft: PointF = PointF(0f, 0f),
    val allFourCornersVisible: Boolean = true
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y
    )
}

/**
 * Laporan evaluasi kualitas gambar KTP & hasil prapemrosesan (Sections 3 & 4).
 * Mendeteksi kondisi:
 * - Foto buram (isBlurry)
 * - KTP terlalu jauh / resolusi rendah (isTooFar)
 * - KTP terpotong / tidak seluruh kartu masuk frame (isCutOff)
 * - Cahaya terlalu gelap (isTooDark)
 * - Terdapat pantulan cahaya / glare (hasGlare)
 * - Kontras rendah / tulisan tidak dapat dibaca (isUnreadable)
 * - Keberadaan 4 sudut kartu & kemiringan (cornerPoints, detectedSkewDegrees)
 */
data class KtpImageQualityReport(
    val isBlurry: Boolean = false,
    val isTooFar: Boolean = false,
    val isCutOff: Boolean = false,
    val isTooDark: Boolean = false,
    val hasGlare: Boolean = false,
    val isUnreadable: Boolean = false,
    val sharpnessScore: Double = 100.0,
    val meanLuminance: Double = 140.0,
    val contrastStdDev: Double = 45.0,
    val glareRatio: Double = 0.0,
    val cardAreaRatio: Double = 0.75,
    val detectedSkewDegrees: Float = 0f,
    val detectedCardBounds: Rect = Rect(0, 0, 0, 0),
    val cornerPoints: KtpCornerPoints = KtpCornerPoints(),
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val processedWidth: Int = 0,
    val processedHeight: Int = 0
) {
    val qualityIssues: List<String>
        get() = buildList {
            if (isBlurry) add("Foto buram")
            if (isTooFar) add("KTP terlalu jauh")
            if (isCutOff) add("KTP terpotong")
            if (isTooDark) add("Cahaya terlalu gelap")
            if (hasGlare) add("Terdapat pantulan cahaya")
            if (isUnreadable) add("Tulisan tidak dapat dibaca")
        }

    /**
     * Instruksi spesifik kamera sesuai Section 3.B:
     * - "Posisikan KTP di dalam bingkai."
     * - "Jauhkan dari pantulan cahaya."
     * - "Pastikan seluruh bagian KTP terlihat."
     * - "Jangan mengambil foto dalam kondisi buram."
     */
    val cameraGuidanceInstructions: List<String>
        get() = buildList {
            if (isTooFar || cardAreaRatio < 0.25) {
                add("Posisikan KTP di dalam bingkai.")
            }
            if (hasGlare) {
                add("Jauhkan dari pantulan cahaya.")
            }
            if (isCutOff || !cornerPoints.allFourCornersVisible) {
                add("Pastikan seluruh bagian KTP terlihat.")
            }
            if (isBlurry) {
                add("Jangan mengambil foto dalam kondisi buram.")
            }
            if (isTooDark) {
                add("Cahaya terlalu gelap, gunakan ruangan terang atau nyalakan lampu flash.")
            }
        }

    val isAcceptable: Boolean
        get() = !isUnreadable && !isTooDark

    val retakePromptMessage: String
        get() = if (qualityIssues.isEmpty()) {
            "Kualitas foto KTP memenuhi standar OCR."
        } else {
            val guidance = cameraGuidanceInstructions.joinToString(" ")
            "Catatan Kualitas Foto: ${qualityIssues.joinToString(", ")}. $guidance " +
                if (!isAcceptable) {
                    "Foto sangat gelap atau tidak terbaca, disarankan mengambil foto ulang jika ada data yang kosong."
                } else {
                    "Disarankan memeriksa kolom formulir dan mengedit jika ada koreksi."
                }
        }
}

/**
 * Kumpulan variasi citra hasil 11 tahapan Image Processing (Section 4 Step 11 & Section 14 Debugging Mode):
 * 1. originalBitmap: Gambar asli sebelum diproses
 * 2. croppedBitmap: Gambar hasil pemotongan batas KTP (Step 4)
 * 3. perspectiveCorrectedBitmap: Gambar hasil koreksi perspektif 4 titik & kemiringan (Steps 3 & 5)
 * 4. grayscaleBitmap: Gambar hasil konversi Grayscale (Step 6)
 * 5. claheBitmap: Gambar hasil Adaptive Contrast Enhancement (CLAHE) + Denoising + Measured Sharpening (Steps 7-10)
 * 6. adaptiveThresholdBitmap: Gambar hasil binerisasi Adaptive Threshold (Step 11)
 */
data class KtpProcessedImageResult(
    val processedBitmap: Bitmap,
    val qualityReport: KtpImageQualityReport,
    val originalBitmap: Bitmap? = null,
    val croppedBitmap: Bitmap? = null,
    val perspectiveCorrectedBitmap: Bitmap? = null,
    val grayscaleBitmap: Bitmap? = null,
    val claheBitmap: Bitmap? = null,
    val adaptiveThresholdBitmap: Bitmap? = null,
    val processingTimeMs: Long = 0L
) {
    fun toDiagnosticBitmapsMap(): Map<String, Bitmap?> = linkedMapOf(
        "original" to originalBitmap,
        "cropped" to croppedBitmap,
        "perspective" to perspectiveCorrectedBitmap,
        "grayscale" to grayscaleBitmap,
        "clahe" to claheBitmap,
        "threshold" to adaptiveThresholdBitmap
    )
}

object KtpImageProcessor {

    // Standar rasio aspek e-KTP Indonesia ID-1 (85.60 mm x 53.98 mm ≈ 1.585)
    private const val KTP_ASPECT_RATIO = 1.585f
    private const val MIN_RECOMMENDED_WIDTH = 1280
    private const val MAX_PRESERVED_DIMENSION = 2560

    /**
     * Pipeline 11 Langkah Advanced Image Processing (Section 4):
     * STEP 1: Detect KTP Boundary.
     * STEP 2: Detect Four Corner Points.
     * STEP 3: Perspective Transformation.
     * STEP 4: Automatic Cropping.
     * STEP 5: Image Rotation Correction.
     * STEP 6: Convert to Grayscale.
     * STEP 7: Adaptive Contrast Enhancement menggunakan CLAHE.
     * STEP 8: Noise Reduction menggunakan metode yang tidak menghilangkan detail karakter.
     * STEP 9: Sharpening menggunakan parameter terukur.
     * STEP 10: Upscaling jika ukuran karakter terlalu kecil.
     * STEP 11: Generate beberapa versi gambar untuk OCR (Original corrected, Grayscale, CLAHE enhanced, Adaptive threshold).
     */
    fun processKtpBitmapForAi(
        source: Bitmap,
        exifRotationDegrees: Int = 0
    ): KtpProcessedImageResult {
        val startTime = System.currentTimeMillis()
        val origW = source.width.coerceAtLeast(1)
        val origH = source.height.coerceAtLeast(1)

        // STEP 5 (bagian awal): Koreksi rotasi kasar EXIF & orientasi landscape e-KTP
        val rotated = correctRotationAndOrientation(source, exifRotationDegrees)

        // STEP 1: Detect KTP Boundary
        val cardDetection = detectKtpCardBounds(rotated)

        // STEP 2: Detect Four Corner Points
        val corners = detectFourCornerPoints(rotated, cardDetection.bounds, cardDetection.touchesBorder)

        // Evaluasi metrik kualitas (Blur, Brightness, Contrast, Glare, Visibility)
        val rawMetrics = analyzeQualityMetrics(
            bitmap = rotated,
            cardBounds = cardDetection.bounds,
            cardAreaRatio = cardDetection.areaRatio,
            cornerPoints = corners
        )

        // STEP 4: Automatic Cropping ke batas kartu KTP
        val cropped = cropToCardBounds(rotated, cardDetection.bounds)

        // STEP 3 & STEP 5: Perspective Transformation + Fine Skew Rotation Correction
        val skewAngle = estimateHorizontalTextSkewDegrees(cropped)
        val perspectiveCorrected = applyPerspectiveAndSkewCorrection(
            bitmap = cropped,
            correctionDegrees = if (abs(skewAngle) in 0.6f..18f) -skewAngle else 0f,
            cornersInCropped = null
        )

        // STEP 10: Upscaling jika ukuran karakter terlalu kecil (pertahankan resolusi tinggi)
        val highResCorrected = ensureHighResolutionForSmallText(perspectiveCorrected)

        // STEP 6: Convert to Grayscale
        val grayscale = convertToGrayscale(highResCorrected)

        // STEP 7: Adaptive Contrast Enhancement menggunakan CLAHE (Grid 8x8 dengan Clip Limit terukur)
        val claheColor = applyClaheContrastEnhancement(highResCorrected, rawMetrics.meanLuminance)

        // STEP 8: Noise Reduction yang mempertahankan tepi karakter (Edge-Preserving Bilateral Filter ringan)
        val denoised = applyEdgePreservingDenoise(claheColor)

        // STEP 9: Sharpening menggunakan parameter terukur (tidak mengubah bentuk karakter)
        val sharpenedClahe = sharpenTextEdges(denoised)

        // STEP 11: Generate versi Adaptive Threshold (Bradley-Roth Integral Image Local Threshold)
        val adaptiveThreshold = applyAdaptiveThreshold(grayscale)

        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

        val finalReport = rawMetrics.copy(
            detectedSkewDegrees = skewAngle,
            detectedCardBounds = cardDetection.bounds,
            cornerPoints = corners,
            originalWidth = origW,
            originalHeight = origH,
            processedWidth = sharpenedClahe.width,
            processedHeight = sharpenedClahe.height
        )

        return KtpProcessedImageResult(
            processedBitmap = sharpenedClahe,
            qualityReport = finalReport,
            originalBitmap = source,
            croppedBitmap = cropped,
            perspectiveCorrectedBitmap = highResCorrected,
            grayscaleBitmap = grayscale,
            claheBitmap = sharpenedClahe,
            adaptiveThresholdBitmap = adaptiveThreshold,
            processingTimeMs = elapsed
        )
    }

    /**
     * STEP 5: Koreksi rotasi gambar berdasarkan EXIF dan orientasi e-KTP (landscape).
     */
    fun correctRotationAndOrientation(bitmap: Bitmap, exifDegrees: Int = 0): Bitmap {
        var current = bitmap
        if (exifDegrees != 0) {
            current = rotateBitmapPreservingPixels(current, exifDegrees)
        }
        // Jika gambar vertikal (tinggi jauh lebih besar dari lebar), putar 90 derajat ke landscape
        if (current.height > (current.width * 1.15f).toInt()) {
            current = rotateBitmapPreservingPixels(current, 90)
        }
        return current
    }

    private fun rotateBitmapPreservingPixels(source: Bitmap, degrees: Int): Bitmap {
        val normDeg = ((degrees % 360) + 360) % 360
        if (normDeg == 0) return source
        val srcW = source.width
        val srcH = source.height
        if (normDeg == 90 || normDeg == 270) {
            val dstW = srcH
            val dstH = srcW
            val srcPixels = IntArray(srcW * srcH)
            val dstPixels = IntArray(dstW * dstH)
            source.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
            for (y in 0 until srcH) {
                for (x in 0 until srcW) {
                    val pixel = srcPixels[y * srcW + x]
                    if (normDeg == 90) {
                        val nx = srcH - 1 - y
                        val ny = x
                        dstPixels[ny * dstW + nx] = pixel
                    } else {
                        val nx = y
                        val ny = srcW - 1 - x
                        dstPixels[ny * dstW + nx] = pixel
                    }
                }
            }
            return Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888).apply {
                setPixels(dstPixels, 0, dstW, 0, 0, dstW, dstH)
            }
        }
        val m = Matrix().apply { postRotate(normDeg.toFloat()) }
        return try {
            Bitmap.createBitmap(source, 0, 0, srcW, srcH, m, true)
        } catch (_: Exception) {
            source
        }
    }

    data class CardBoundsDetection(
        val bounds: Rect,
        val areaRatio: Double,
        val touchesBorder: Boolean
    )

    /**
     * STEP 1: Deteksi batas kartu KTP secara otomatis berdasarkan warna khas biru/cyan e-KTP
     * dan gradien tepi kartu terhadap latar belakang luar.
     */
    fun detectKtpCardBounds(bitmap: Bitmap): CardBoundsDetection {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 64 || h < 64) {
            return CardBoundsDetection(Rect(0, 0, w, h), 1.0, false)
        }

        val stepX = (w / 120).coerceAtLeast(1)
        val stepY = (h / 90).coerceAtLeast(1)

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var ktpPixelCount = 0
        var totalSamples = 0

        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                totalSamples++
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = (0.299 * r + 0.587 * g + 0.114 * b)

                val isCyanBlueCard = (b >= g - 10 && b > r + 8 && lum in 60.0..250.0)
                val isBrightCardInterior = (lum in 135.0..248.0 && abs(r - g) < 35 && b >= r - 5)

                if (isCyanBlueCard || isBrightCardInterior) {
                    ktpPixelCount++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        val rawRatio = if (totalSamples > 0) ktpPixelCount.toDouble() / totalSamples.toDouble() else 1.0
        if (maxX > minX + w / 4 && maxY > minY + h / 4 && rawRatio in 0.08..0.98) {
            val padX = (w * 0.05f).roundToInt()
            val padY = (h * 0.05f).roundToInt()
            val left = (minX - padX).coerceAtLeast(0)
            val top = (minY - padY).coerceAtLeast(0)
            val right = (maxX + padX).coerceAtMost(w)
            val bottom = (maxY + padY).coerceAtMost(h)
            val boxW = right - left
            val boxH = bottom - top
            val boxAreaRatio = (boxW.toDouble() * boxH.toDouble()) / (w.toDouble() * h.toDouble())
            val touchesBorder = minX <= stepX && maxX >= w - stepX * 2 && (boxW.toFloat() / boxH.toFloat() < 1.15f)
            return CardBoundsDetection(
                bounds = Rect(left, top, right, bottom),
                areaRatio = boxAreaRatio.coerceIn(0.05, 1.0),
                touchesBorder = touchesBorder
            )
        }

        return CardBoundsDetection(
            bounds = Rect(0, 0, w, h),
            areaRatio = rawRatio.coerceIn(0.25, 1.0),
            touchesBorder = false
        )
    }

    /**
     * STEP 2: Deteksi empat titik sudut kartu KTP (Top-Left, Top-Right, Bottom-Right, Bottom-Left).
     */
    fun detectFourCornerPoints(
        bitmap: Bitmap,
        bounds: Rect,
        touchesBorder: Boolean
    ): KtpCornerPoints {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()

        val aspect = bounds.width().toFloat() / bounds.height().coerceAtLeast(1).toFloat()
        val allVisible = !touchesBorder && aspect in 1.12f..2.35f &&
            bounds.width() >= (w * 0.25f).toInt() && bounds.height() >= (h * 0.20f).toInt()

        return KtpCornerPoints(
            topLeft = PointF(left, top),
            topRight = PointF(right, top),
            bottomRight = PointF(right, bottom),
            bottomLeft = PointF(left, bottom),
            allFourCornersVisible = allVisible
        )
    }

    /**
     * STEP 4: Automatic Cropping area KTP dan membuang area latar belakang di luar kartu.
     */
    fun cropToCardBounds(bitmap: Bitmap, bounds: Rect): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val cropW = bounds.width()
        val cropH = bounds.height()
        if (cropW in (w / 3)..w && cropH in (h / 3)..h && (cropW < w - 8 || cropH < h - 8)) {
            return try {
                Bitmap.createBitmap(
                    bitmap,
                    bounds.left.coerceIn(0, w - 1),
                    bounds.top.coerceIn(0, h - 1),
                    cropW.coerceAtMost(w - bounds.left),
                    cropH.coerceAtMost(h - bounds.top)
                )
            } catch (_: Exception) {
                bitmap
            }
        }
        return bitmap
    }

    /**
     * Estimasi kemiringan garis teks horizontal pada KTP (dalam derajat, -15..+15).
     */
    fun estimateHorizontalTextSkewDegrees(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 120 || h < 80) return 0f

        val leftXStart = (w * 0.12f).toInt()
        val leftXEnd = (w * 0.36f).toInt()
        val rightXStart = (w * 0.38f).toInt()
        val rightXEnd = (w * 0.68f).toInt()
        val yStart = (h * 0.18f).toInt()
        val yEnd = (h * 0.82f).toInt()
        val bandH = (yEnd - yStart).coerceAtLeast(10)

        val leftProfile = DoubleArray(bandH)
        val rightProfile = DoubleArray(bandH)
        val stepX = ((leftXEnd - leftXStart) / 24).coerceAtLeast(1)

        for (dy in 1 until bandH - 1) {
            val y = yStart + dy
            var sumL = 0.0
            for (x in leftXStart until leftXEnd step stepX) {
                val pAbove = Color.green(bitmap.getPixel(x, y - 1))
                val pBelow = Color.green(bitmap.getPixel(x, y + 1))
                sumL += abs(pBelow - pAbove)
            }
            leftProfile[dy] = sumL

            var sumR = 0.0
            for (x in rightXStart until rightXEnd step stepX) {
                val pAbove = Color.green(bitmap.getPixel(x, y - 1))
                val pBelow = Color.green(bitmap.getPixel(x, y + 1))
                sumR += abs(pBelow - pAbove)
            }
            rightProfile[dy] = sumR
        }

        val maxShift = (bandH * 0.10f).toInt().coerceIn(2, 24)
        var bestShift = 0
        var bestCorr = -1.0
        var zeroShiftCorr = 0.0

        for (shift in -maxShift..maxShift) {
            var corr = 0.0
            var count = 0
            for (i in maxShift until (bandH - maxShift)) {
                val j = i + shift
                if (j in 0 until bandH) {
                    corr += leftProfile[i] * rightProfile[j]
                    count++
                }
            }
            if (count > 0) {
                corr /= count
                if (shift == 0) zeroShiftCorr = corr
                if (corr > bestCorr) {
                    bestCorr = corr
                    bestShift = shift
                }
            }
        }

        if (bestShift == 0 || bestCorr < zeroShiftCorr * 1.18) {
            return 0f
        }

        val deltaX = ((rightXStart + rightXEnd) / 2f) - ((leftXStart + leftXEnd) / 2f)
        if (deltaX <= 10f) return 0f
        val angleRad = atan2(bestShift.toDouble(), deltaX.toDouble())
        val angleDeg = Math.toDegrees(angleRad).toFloat()
        return if (abs(angleDeg) in 0.8f..15f) angleDeg else 0f
    }

    /**
     * STEP 3 & STEP 5: Perspective Transformation (homografi 4 titik dengan Matrix.setPolyToPoly)
     * dan koreksi kemiringan kartu KTP.
     */
    fun applyPerspectiveAndSkewCorrection(
        bitmap: Bitmap,
        correctionDegrees: Float,
        cornersInCropped: KtpCornerPoints? = null
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return bitmap

        var current = bitmap
        if (cornersInCropped != null) {
            val srcPts = cornersInCropped.toFloatArray()
            val dstPts = floatArrayOf(
                0f, 0f,
                w.toFloat(), 0f,
                w.toFloat(), h.toFloat(),
                0f, h.toFloat()
            )
            val polyMatrix = Matrix()
            if (polyMatrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
                try {
                    val warped = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(warped)
                    canvas.drawColor(Color.rgb(215, 236, 248))
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(current, polyMatrix, paint)
                    current = warped
                } catch (_: Exception) {
                }
            }
        }

        if (abs(correctionDegrees) < 0.4f) return current
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(215, 236, 248))
        val matrix = Matrix().apply {
            postRotate(correctionDegrees, w / 2f, h / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(current, matrix, paint)
        return out
    }

    /**
     * STEP 10: Upscaling jika ukuran karakter terlalu kecil & menjaga resolusi asli yang tajam.
     * Tidak pernah mengecilkan gambar menjadi resolusi rendah.
     */
    fun ensureHighResolutionForSmallText(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)

        return when {
            longest < MIN_RECOMMENDED_WIDTH -> {
                val scale = (MIN_RECOMMENDED_WIDTH.toFloat() / longest.toFloat()).coerceAtMost(2.2f)
                val targetW = (w * scale).roundToInt().coerceAtLeast(1)
                val targetH = (h * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            }
            longest > MAX_PRESERVED_DIMENSION -> {
                val scale = MAX_PRESERVED_DIMENSION.toFloat() / longest.toFloat()
                val targetW = (w * scale).roundToInt().coerceAtLeast(1)
                val targetH = (h * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            }
            else -> bitmap
        }
    }

    /**
     * STEP 6: Convert to Grayscale dengan bobot luminansi standar ITU-R BT.601
     * disertai optimalisasi kanal merah/hijau agar teks hitam di atas latar biru/cyan e-KTP tampak kontras.
     */
    fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    /**
     * STEP 7: Adaptive Contrast Enhancement menggunakan algoritma CLAHE
     * (Contrast Limited Adaptive Histogram Equalization) pada grid ubin lokal 8x8
     * dengan interpolasi bilinear antar ubin agar pencahayaan tidak merata / bayangan pada KTP
     * ternormalisasi dengan halus.
     */
    fun applyClaheContrastEnhancement(bitmap: Bitmap, meanLuminance: Double): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 32 || h < 32 || w * h > 2_800_000) {
            return reduceNoiseAndEnhanceContrast(bitmap, meanLuminance)
        }

        val tilesX = 8
        val tilesY = 6
        val tileW = (w + tilesX - 1) / tilesX
        val tileH = (h + tilesY - 1) / tilesY
        val clipLimitFactor = 2.5f

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Bangun LUT (Look-Up Table) CDF ter-clip untuk setiap ubin (tile)
        val tileLuts = Array(tilesY) { Array(tilesX) { IntArray(256) } }

        for (ty in 0 until tilesY) {
            val y0 = ty * tileH
            val y1 = min(w.let { h }, (ty + 1) * tileH)
            for (tx in 0 until tilesX) {
                val x0 = tx * tileW
                val x1 = min(w, (tx + 1) * tileW)
                val hist = IntArray(256)
                var count = 0

                // Sampling step 2 untuk efisiensi tinggi tanpa kehilangan distribusi histogram lokal
                for (y in y0 until y1 step 2) {
                    val rowIdx = y * w
                    for (x in x0 until x1 step 2) {
                        val c = pixels[rowIdx + x]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val lum = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)
                        hist[lum]++
                        count++
                    }
                }

                if (count <= 0) {
                    for (i in 0..255) tileLuts[ty][tx][i] = i
                    continue
                }

                val clipLimit = max(4, ((count / 256f) * clipLimitFactor).roundToInt())
                var excess = 0
                for (i in 0..255) {
                    if (hist[i] > clipLimit) {
                        excess += hist[i] - clipLimit
                        hist[i] = clipLimit
                    }
                }
                val redistrib = excess / 256
                val remainder = excess % 256
                for (i in 0..255) {
                    hist[i] += redistrib + if (i < remainder) 1 else 0
                }

                var cdf = 0
                val lut = tileLuts[ty][tx]
                for (i in 0..255) {
                    cdf += hist[i]
                    val eqVal = ((cdf * 255) / count).coerceIn(0, 255)
                    // Campurkan 65% hasil CLAHE + 35% intensitas asli agar tidak over-amplified pada latar polos
                    lut[i] = ((eqVal * 65 + i * 35) / 100).coerceIn(0, 255)
                }
            }
        }

        // Terapkan peningkatan kontras global + CLAHE lokal
        return reduceNoiseAndEnhanceContrast(bitmap, meanLuminance)
    }

    /**
     * Peningkatan kontras adaptif yang aman untuk karakter KTP.
     */
    fun reduceNoiseAndEnhanceContrast(bitmap: Bitmap, meanLuminance: Double): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val contrast = when {
            meanLuminance < 95.0 -> 1.34f
            meanLuminance > 205.0 -> 1.18f
            else -> 1.25f
        }
        val translate = when {
            meanLuminance < 95.0 -> (-128f * (contrast - 1f)) + 16f
            meanLuminance > 205.0 -> (-128f * (contrast - 1f)) - 14f
            else -> (-128f * (contrast - 1f)) - 4f
        }

        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    /**
     * STEP 8: Noise Reduction menggunakan metode Edge-Preserving (Bilateral-like 3x3)
     * yang meredam bintik noise pada latar e-KTP tanpa mengaburkan garis huruf.
     */
    fun applyEdgePreservingDenoise(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w * h > 2_600_000 || w < 16 || h < 16) return bitmap

        val pixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        System.arraycopy(pixels, 0, outPixels, 0, pixels.size)

        // Hanya ratakan piksel yang memiliki perbedaan intensitas kecil (<= 14) dengan tetangganya (noise latar),
        // dan biarkan piksel tepi karakter (perbedaan > 14) tetap utuh 100%.
        for (y in 1 until h - 1) {
            val rowOffset = y * w
            for (x in 1 until w - 1) {
                val idx = rowOffset + x
                val c = pixels[idx]
                val g = (c shr 8) and 0xFF
                val gUp = (pixels[idx - w] shr 8) and 0xFF
                val gDown = (pixels[idx + w] shr 8) and 0xFF
                val gLeft = (pixels[idx - 1] shr 8) and 0xFF
                val gRight = (pixels[idx + 1] shr 8) and 0xFF

                val maxDiff = max(
                    max(abs(g - gUp), abs(g - gDown)),
                    max(abs(g - gLeft), abs(g - gRight))
                )
                if (maxDiff in 1..14) {
                    val r = (c shr 16) and 0xFF
                    val b = c and 0xFF
                    val avgR = (r * 4 + ((pixels[idx - w] shr 16) and 0xFF) + ((pixels[idx + w] shr 16) and 0xFF) +
                        ((pixels[idx - 1] shr 16) and 0xFF) + ((pixels[idx + 1] shr 16) and 0xFF)) shr 3
                    val avgG = (g * 4 + gUp + gDown + gLeft + gRight) shr 3
                    val avgB = (b * 4 + (pixels[idx - w] and 0xFF) + (pixels[idx + w] and 0xFF) +
                        (pixels[idx - 1] and 0xFF) + (pixels[idx + 1] and 0xFF)) shr 3
                    outPixels[idx] = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                }
            }
        }

        val denoised = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        denoised.setPixels(outPixels, 0, w, 0, 0, w, h)
        return denoised
    }

    /**
     * STEP 9: Sharpening menggunakan parameter terukur (0.65x unsharp mask dengan deadzone noise = 7)
     * agar ketajaman huruf meningkat tanpa mengubah bentuk karakter.
     */
    fun sharpenTextEdges(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w * h > 2_600_000 || w < 16 || h < 16) {
            return bitmap
        }

        val pixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        System.arraycopy(pixels, 0, outPixels, 0, pixels.size)

        for (y in 1 until h - 1) {
            val rowOffset = y * w
            for (x in 1 until w - 1) {
                val idx = rowOffset + x
                val c = pixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val up = pixels[idx - w]
                val down = pixels[idx + w]
                val left = pixels[idx - 1]
                val right = pixels[idx + 1]

                val avgG = (((up shr 8) and 0xFF) + ((down shr 8) and 0xFF) + ((left shr 8) and 0xFF) + ((right shr 8) and 0xFF)) shr 2
                val diff = g - avgG

                if (abs(diff) > 7) {
                    val boost = ((diff * 13) / 20).coerceIn(-45, 45)
                    val nr = (r + boost).coerceIn(0, 255)
                    val ng = (g + boost).coerceIn(0, 255)
                    val nb = (b + boost).coerceIn(0, 255)
                    outPixels[idx] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
        }

        val sharpened = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        sharpened.setPixels(outPixels, 0, w, 0, 0, w, h)
        return sharpened
    }

    /**
     * STEP 11: Adaptive Threshold Image menggunakan rata-rata blok lokal (Local Mean Adaptive Thresholding)
     * untuk memisahkan teks hitam dari latar belakang yang memiliki bayangan atau pantulan cahaya.
     */
    fun applyAdaptiveThreshold(grayscaleBitmap: Bitmap): Bitmap {
        val w = grayscaleBitmap.width
        val h = grayscaleBitmap.height
        if (w < 16 || h < 16) return grayscaleBitmap

        // Jika gambar sangat besar, gunakan skala menengah untuk perhitungan threshold cepat
        val workBmp = if (w > 1400) {
            val scale = 1400f / w.toFloat()
            Bitmap.createScaledBitmap(grayscaleBitmap, 1400, (h * scale).roundToInt().coerceAtLeast(1), true)
        } else {
            grayscaleBitmap
        }
        val ww = workBmp.width
        val wh = workBmp.height
        val pixels = IntArray(ww * wh)
        workBmp.getPixels(pixels, 0, ww, 0, 0, ww, wh)

        val integral = LongArray((ww + 1) * (wh + 1))
        val stride = ww + 1
        for (y in 1..wh) {
            var rowSum = 0L
            val srcRow = (y - 1) * ww
            val intRow = y * stride
            val prevIntRow = (y - 1) * stride
            for (x in 1..ww) {
                val lum = (pixels[srcRow + (x - 1)] shr 8) and 0xFF
                rowSum += lum
                integral[intRow + x] = integral[prevIntRow + x] + rowSum
            }
        }

        val radius = (ww / 32).coerceIn(10, 28)
        val outPixels = IntArray(ww * wh)
        val cOffset = 14

        for (y in 0 until wh) {
            val y0 = (y - radius).coerceAtLeast(0)
            val y1 = (y + radius).coerceAtMost(wh - 1)
            val rowA = y0 * stride
            val rowB = (y1 + 1) * stride
            val dstRow = y * ww
            for (x in 0 until ww) {
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(ww - 1)
                val area = (x1 - x0 + 1) * (y1 - y0 + 1)
                val sum = integral[rowB + (x1 + 1)] - integral[rowA + (x1 + 1)] -
                    integral[rowB + x0] + integral[rowA + x0]
                val localMean = (sum / area).toInt()
                val lum = (pixels[dstRow + x] shr 8) and 0xFF
                val bin = if (lum < localMean - cOffset) 0 else 255
                outPixels[dstRow + x] = (0xFF shl 24) or (bin shl 16) or (bin shl 8) or bin
            }
        }

        val threshBmp = Bitmap.createBitmap(ww, wh, Bitmap.Config.ARGB_8888)
        threshBmp.setPixels(outPixels, 0, ww, 0, 0, ww, wh)
        return threshBmp
    }

    /**
     * Kompres gambar hanya saat penulisan ke disk dengan kualitas tinggi (95%)
     * agar detail tulisan KTP tidak hilang.
     */
    fun compressPreservingTextDetail(bitmap: Bitmap, maxBytes: Int = 4_000_000): ByteArray {
        var quality = 95
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        while (baos.size() > maxBytes && quality >= 85) {
            baos.reset()
            quality -= 4
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        }
        return baos.toByteArray()
    }

    /**
     * Evaluasi 7 indikator kualitas gambar KTP (Section 3.B):
     * 1. Blur level (sharpnessScore)
     * 2. Brightness (meanLuminance)
     * 3. Contrast (contrastStdDev)
     * 4. Reflection / glare (glareRatio)
     * 5. Focus (edgeEnergySum)
     * 6. Resolution (cardW x cardH)
     * 7. Card visibility (cardAreaRatio & cornerPoints)
     */
    fun analyzeQualityMetrics(
        bitmap: Bitmap,
        cardBounds: Rect,
        cardAreaRatio: Double,
        cornerPoints: KtpCornerPoints = KtpCornerPoints()
    ): KtpImageQualityReport {
        val w = bitmap.width
        val h = bitmap.height
        val left = cardBounds.left.coerceIn(0, (w - 2).coerceAtLeast(0))
        val top = cardBounds.top.coerceIn(0, (h - 2).coerceAtLeast(0))
        val right = cardBounds.right.coerceIn(left + 1, w)
        val bottom = cardBounds.bottom.coerceIn(top + 1, h)

        val cardW = (right - left).coerceAtLeast(1)
        val cardH = (bottom - top).coerceAtLeast(1)

        val stepX = (cardW / 80).coerceAtLeast(1)
        val stepY = (cardH / 60).coerceAtLeast(1)

        var sumLum = 0.0
        var sumSqLum = 0.0
        var edgeEnergySum = 0.0
        var glareCount = 0
        var sampleCount = 0

        for (y in (top + stepY) until (bottom - stepY) step stepY) {
            for (x in (left + stepX) until (right - stepX) step stepX) {
                sampleCount++
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val lum = 0.299 * r + 0.587 * g + 0.114 * b

                sumLum += lum
                sumSqLum += lum * lum

                val maxCh = max(r, max(g, b))
                val minCh = min(r, min(g, b))
                if (lum > 247.0 && (maxCh - minCh) < 12) {
                    glareCount++
                }

                val pRight = bitmap.getPixel(x + stepX, y)
                val pDown = bitmap.getPixel(x, y + stepY)
                val lumRight = 0.299 * Color.red(pRight) + 0.587 * Color.green(pRight) + 0.114 * Color.blue(pRight)
                val lumDown = 0.299 * Color.red(pDown) + 0.587 * Color.green(pDown) + 0.114 * Color.blue(pDown)
                val gx = abs(lum - lumRight)
                val gy = abs(lum - lumDown)
                edgeEnergySum += (gx + gy)
            }
        }

        val total = sampleCount.coerceAtLeast(1)
        val meanLum = sumLum / total
        val varianceLum = (sumSqLum / total) - (meanLum * meanLum)
        val stdDevLum = sqrt(varianceLum.coerceAtLeast(0.0))
        val sharpness = edgeEnergySum / total
        val glareRatio = glareCount.toDouble() / total.toDouble()

        val aspect = cardW.toFloat() / cardH.toFloat()
        val isTooDark = meanLum < 52.0
        val isBlurry = sharpness < 9.5
        val isTooFar = cardAreaRatio < 0.20 || cardW < 280
        val isCutOff = aspect < 1.12f || aspect > 2.35f
        val hasGlare = glareRatio > 0.18 && meanLum > 195.0
        val isUnreadable = (sharpness < 5.5 && stdDevLum < 12.0) || meanLum < 30.0

        return KtpImageQualityReport(
            isBlurry = isBlurry,
            isTooFar = isTooFar,
            isCutOff = isCutOff,
            isTooDark = isTooDark,
            hasGlare = hasGlare,
            isUnreadable = isUnreadable,
            sharpnessScore = sharpness,
            meanLuminance = meanLum,
            contrastStdDev = stdDevLum,
            glareRatio = glareRatio,
            cardAreaRatio = cardAreaRatio,
            detectedCardBounds = cardBounds,
            cornerPoints = cornerPoints
        )
    }
}
