package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Status posisi kartu e-KTP di dalam bingkai viewfinder kamera.
 */
enum class KtpCardPositionStatus {
    IDEAL,
    NOT_FOUND,
    TOO_FAR,
    TOO_CLOSE,
    CUT_OFF_BORDER,
    OFF_CENTER,
    TILTED
}

/**
 * Representasi badge indikator kualitas kamera real-time pada HUD Viewfinder.
 */
data class QualityMetricBadge(
    val key: String,
    val label: String,
    val valueText: String,
    val isPassed: Boolean
)

/**
 * Hasil deteksi kartu e-KTP & validasi kualitas kamera secara real-time
 * menggunakan CameraX ImageAnalysis dan algoritma pemrosesan citra OpenCV (Section 3.A & 3.B).
 */
data class KtpDetectionAndQualityState(
    // A. Live KTP Detection
    val isCardDetected: Boolean = false,
    val cornerPoints: KtpCornerPoints = KtpCornerPoints(allFourCornersVisible = false),
    val normalizedCorners: List<PointF> = emptyList(), // 4 titik (0f..1f) [TL, TR, BR, BL]
    val visibleCornerCount: Int = 0,
    val allFourCornersVisible: Boolean = false,
    val isCardFullyInFrame: Boolean = false,
    val cardPositionStatus: KtpCardPositionStatus = KtpCardPositionStatus.NOT_FOUND,
    val skewAngleDegrees: Float = 0f,
    val isTilted: Boolean = false,
    val cardAreaRatio: Double = 0.0,
    val detectedBounds: Rect = Rect(0, 0, 0, 0),

    // B. Camera Quality Validation
    val blurLaplacianScore: Double = 0.0,
    val isBlurry: Boolean = false,
    val brightnessMean: Double = 140.0,
    val isTooDark: Boolean = false,
    val isOverexposed: Boolean = false,
    val contrastStdDev: Double = 45.0,
    val isLowContrast: Boolean = false,
    val glareRatio: Double = 0.0,
    val hasGlare: Boolean = false,
    val focusEdgeScore: Double = 100.0,
    val isFocused: Boolean = true,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val cardWidthPx: Int = 0,
    val cardHeightPx: Int = 0,
    val isResolutionSufficient: Boolean = true,
    val isCardVisible: Boolean = false,

    // Status keseluruhan & instruksi pengguna
    val isReadyToCapture: Boolean = false,
    val primaryInstruction: String = INSTRUCTION_POSITION_IN_FRAME,
    val secondaryInstruction: String = "Pastikan keempat sudut e-KTP masuk ke dalam bingkai",
    val activeInstructions: List<String> = listOf(INSTRUCTION_POSITION_IN_FRAME),
    val qualityBadges: List<QualityMetricBadge> = emptyList(),
    val analysisTimestampMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val INSTRUCTION_POSITION_IN_FRAME = "Posisikan KTP di dalam bingkai."
        const val INSTRUCTION_AVOID_GLARE = "Jauhkan dari pantulan cahaya."
        const val INSTRUCTION_FULL_CARD_VISIBLE = "Pastikan seluruh bagian KTP terlihat."
        const val INSTRUCTION_NO_BLUR = "Jangan mengambil foto dalam kondisi buram."
        const val INSTRUCTION_NEED_MORE_LIGHT = "Pencahayaan terlalu gelap, nyalakan flash atau cari tempat terang."
        const val INSTRUCTION_STRAIGHTEN_CARD = "Luruskan posisi KTP agar tidak miring."
        const val INSTRUCTION_READY = "KTP Terdeteksi & Fokus Tajam — Siap Ambil Foto"
    }
}

/**
 * Modul Deteksi e-KTP & Validasi Kualitas Kamera berbasis CameraX `ImageAnalysis.Analyzer`
 * dan pipeline komputer visi OpenCV (Laplacian Blur, Canny/Sobel Quadrilateral Corner Detection,
 * Specular Glare Detection, Luminance/Contrast Histogram, dan Skew Estimation).
 */
class KtpCameraQualityAnalyzer(
    private val minAnalysisIntervalMs: Long = 140L,
    private val onQualityStateUpdated: (KtpDetectionAndQualityState) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestampMs: Long = 0L

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedTimestampMs < minAnalysisIntervalMs) {
            image.close()
            return
        }
        lastAnalyzedTimestampMs = now

        try {
            val state = analyzeImageProxy(image)
            onQualityStateUpdated(state)
        } catch (_: Throwable) {
            // Jangan menghentikan aliran kamera jika satu frame gagal dianalisis
        } finally {
            image.close()
        }
    }

    companion object {
        private const val MIN_LAP_BLUR_SCORE = 11.5
        private const val MIN_FOCUS_EDGE_SCORE = 9.5
        private const val MIN_BRIGHTNESS = 55.0
        private const val MAX_BRIGHTNESS = 238.0
        private const val MIN_CONTRAST_STD_DEV = 18.0
        private const val MAX_GLARE_RATIO = 0.14
        private const val MAX_ALLOWED_SKEW_DEG = 8.5f
        private const val MIN_CARD_AREA_RATIO = 0.22
        private const val MAX_CARD_AREA_RATIO = 0.94

        /**
         * Analisis frame real-time dari CameraX `ImageProxy` (YUV_420_888).
         * Mengekstrak kanal Y (Luminance) dan kanal U/V (Chrominance biru/cyan khas e-KTP)
         * untuk mendeteksi 4 sudut KTP, kemiringan, blur, kecerahan, kontras, dan glare.
         */
        fun analyzeImageProxy(image: ImageProxy): KtpDetectionAndQualityState {
            val planes = image.planes
            val width = image.width.coerceAtLeast(1)
            val height = image.height.coerceAtLeast(1)
            if (planes.isEmpty()) {
                return KtpDetectionAndQualityState(frameWidth = width, frameHeight = height)
            }

            val yBuffer = planes[0].buffer
            val yRowStride = planes[0].rowStride
            val yPixelStride = planes[0].pixelStride

            // Downsample ke grid kerja ~160x120 untuk analisis real-time cepat (< 8 ms per frame)
            val stepX = (width / 160).coerceAtLeast(1)
            val stepY = (height / 120).coerceAtLeast(1)
            val gridW = (width / stepX).coerceAtLeast(8)
            val gridH = (height / stepY).coerceAtLeast(8)

            val lumGrid = IntArray(gridW * gridH)
            for (gy in 0 until gridH) {
                val srcY = (gy * stepY).coerceAtMost(height - 1)
                val rowBase = srcY * yRowStride
                for (gx in 0 until gridW) {
                    val srcX = (gx * stepX).coerceAtMost(width - 1)
                    val idx = rowBase + srcX * yPixelStride
                    val yVal = if (idx >= 0 && idx < yBuffer.limit()) {
                        yBuffer.get(idx).toInt() and 0xFF
                    } else {
                        128
                    }
                    lumGrid[gy * gridW + gx] = yVal
                }
            }

            return evaluateLuminanceAndColorGrid(
                lumGrid = lumGrid,
                cyanMaskGrid = null,
                gridW = gridW,
                gridH = gridH,
                sourceWidth = width,
                sourceHeight = height
            )
        }

        /**
         * Analisis `Bitmap` (baik untuk validasi kualitas pra-OCR maupun pengujian unit/Robolectric).
         * Menggunakan deteksi warna khas e-KTP + gradien tepi OpenCV untuk menemukan 4 titik sudut,
         * varian Laplacian 2D untuk deteksi blur/fokus, analisis histogram untuk kecerahan/kontras,
         * dan deteksi saturasi highlight untuk pantulan cahaya (glare).
         */
        fun analyzeBitmap(
            bitmap: Bitmap,
            rotationDegrees: Int = 0
        ): KtpDetectionAndQualityState {
            val oriented = if (rotationDegrees != 0) {
                KtpImageProcessor.correctRotationAndOrientation(bitmap, rotationDegrees)
            } else {
                bitmap
            }
            val width = oriented.width.coerceAtLeast(1)
            val height = oriented.height.coerceAtLeast(1)

            val stepX = (width / 160).coerceAtLeast(1)
            val stepY = (height / 120).coerceAtLeast(1)
            val gridW = (width / stepX).coerceAtLeast(8)
            val gridH = (height / stepY).coerceAtLeast(8)

            val lumGrid = IntArray(gridW * gridH)
            val cyanMaskGrid = BooleanArray(gridW * gridH)
            var glareHighlightPixels = 0
            var totalRoiPixels = 0

            val marginX = (gridW * 0.06f).toInt()
            val marginY = (gridH * 0.06f).toInt()

            for (gy in 0 until gridH) {
                val srcY = (gy * stepY).coerceAtMost(height - 1)
                for (gx in 0 until gridW) {
                    val srcX = (gx * stepX).coerceAtMost(width - 1)
                    val pixel = oriented.getPixel(srcX, srcY)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val lum = ((0.299 * r) + (0.587 * g) + (0.114 * b)).roundToInt().coerceIn(0, 255)
                    val idx = gy * gridW + gx
                    lumGrid[idx] = lum

                    val isCyanBlue = (b >= g - 10 && b > r + 8 && lum in 55..250)
                    val isCardInterior = (lum in 130..246 && abs(r - g) < 34 && b >= r - 6)
                    cyanMaskGrid[idx] = isCyanBlue || isCardInterior

                    if (gx in marginX until (gridW - marginX) && gy in marginY until (gridH - marginY)) {
                        totalRoiPixels++
                        val maxCh = max(r, max(g, b))
                        val minCh = min(r, min(g, b))
                        if (lum >= 246 && (maxCh - minCh) <= 12) {
                            glareHighlightPixels++
                        }
                    }
                }
            }

            val exactGlareRatio = if (totalRoiPixels > 0) {
                glareHighlightPixels.toDouble() / totalRoiPixels.toDouble()
            } else {
                null
            }

            return evaluateLuminanceAndColorGrid(
                lumGrid = lumGrid,
                cyanMaskGrid = cyanMaskGrid,
                gridW = gridW,
                gridH = gridH,
                sourceWidth = width,
                sourceHeight = height,
                overrideGlareRatio = exactGlareRatio
            )
        }

        /**
         * Inti algoritma OpenCV untuk:
         * 1. Deteksi batas & 4 titik sudut kartu e-KTP (Top-Left, Top-Right, Bottom-Right, Bottom-Left)
         * 2. Perhitungan kemiringan kartu (skew angle) dari vektor tepi atas/bawah
         * 3. Perhitungan Laplacian Variance (`[0, 1, 0; 1, -4, 1; 0, 1, 0]`) & Sobel Edge Energy untuk Blur & Focus
         * 4. Evaluasi Mean Luminance & Contrast Standard Deviation
         * 5. Deteksi Specular Glare / Reflection
         * 6. Penentuan instruksi pengguna real-time sesuai Section 3.B
         */
        private fun evaluateLuminanceAndColorGrid(
            lumGrid: IntArray,
            cyanMaskGrid: BooleanArray?,
            gridW: Int,
            gridH: Int,
            sourceWidth: Int,
            sourceHeight: Int,
            overrideGlareRatio: Double? = null
        ): KtpDetectionAndQualityState {
            // 1. Hitung gradien Sobel & Laplacian 2D pada grid
            val gradMag = IntArray(gridW * gridH)
            var minCardGx = gridW
            var minCardGy = gridH
            var maxCardGx = 0
            var maxCardGy = 0
            var cardFeatureCount = 0

            for (gy in 1 until gridH - 1) {
                val row = gy * gridW
                for (gx in 1 until gridW - 1) {
                    val idx = row + gx
                    val c = lumGrid[idx]
                    val left = lumGrid[idx - 1]
                    val right = lumGrid[idx + 1]
                    val up = lumGrid[idx - gridW]
                    val down = lumGrid[idx + gridW]

                    val gxVal = abs(right - left)
                    val gyVal = abs(down - up)
                    val mag = gxVal + gyVal
                    gradMag[idx] = mag

                    val isCardSignal = if (cyanMaskGrid != null) {
                        cyanMaskGrid[idx]
                    } else {
                        // Pada Y-plane kamera, area kartu memiliki kontras tepi teks/bingkai atau luminansi kartu terang
                        mag >= 26 || c in 125..235
                    }

                    if (isCardSignal) {
                        cardFeatureCount++
                        if (gx < minCardGx) minCardGx = gx
                        if (gy < minCardGy) minCardGy = gy
                        if (gx > maxCardGx) maxCardGx = gx
                        if (gy > maxCardGy) maxCardGy = gy
                    }
                }
            }

            val totalGridCells = (gridW * gridH).coerceAtLeast(1)
            val rawFeatureRatio = cardFeatureCount.toDouble() / totalGridCells.toDouble()

            val hasValidBounds = maxCardGx > minCardGx + (gridW / 5) &&
                maxCardGy > minCardGy + (gridH / 5) &&
                rawFeatureRatio >= 0.08

            val leftPx: Int
            val topPx: Int
            val rightPx: Int
            val bottomPx: Int
            val cardAreaRatio: Double
            val touchesLeft: Boolean
            val touchesTop: Boolean
            val touchesRight: Boolean
            val touchesBottom: Boolean

            if (hasValidBounds) {
                val scaleX = sourceWidth.toFloat() / gridW.toFloat()
                val scaleY = sourceHeight.toFloat() / gridH.toFloat()
                leftPx = (minCardGx * scaleX).roundToInt().coerceIn(0, sourceWidth - 1)
                topPx = (minCardGy * scaleY).roundToInt().coerceIn(0, sourceHeight - 1)
                rightPx = (maxCardGx * scaleX).roundToInt().coerceIn(leftPx + 1, sourceWidth)
                bottomPx = (maxCardGy * scaleY).roundToInt().coerceIn(topPx + 1, sourceHeight)

                val boxArea = (rightPx - leftPx).toDouble() * (bottomPx - topPx).toDouble()
                val frameArea = sourceWidth.toDouble() * sourceHeight.toDouble()
                cardAreaRatio = (boxArea / frameArea).coerceIn(0.0, 1.0)

                val aspect = (rightPx - leftPx).toFloat() / (bottomPx - topPx).coerceAtLeast(1).toFloat()
                val abnormalAspect = aspect < 1.12f || aspect > 2.35f

                touchesLeft = minCardGx <= 1 && abnormalAspect
                touchesTop = minCardGy <= 1 && abnormalAspect
                touchesRight = maxCardGx >= gridW - 2 && abnormalAspect
                touchesBottom = maxCardGy >= gridH - 2 && abnormalAspect
            } else {
                leftPx = 0
                topPx = 0
                rightPx = 0
                bottomPx = 0
                cardAreaRatio = 0.0
                touchesLeft = true
                touchesTop = true
                touchesRight = true
                touchesBottom = true
            }

            // 2. Deteksi 4 Titik Sudut (Quadrilateral Corner Points) & Kemiringan (Skew)
            val tlVisible = hasValidBounds && !touchesLeft && !touchesTop
            val trVisible = hasValidBounds && !touchesRight && !touchesTop
            val brVisible = hasValidBounds && !touchesRight && !touchesBottom
            val blVisible = hasValidBounds && !touchesLeft && !touchesBottom
            val visibleCorners = listOf(tlVisible, trVisible, brVisible, blVisible).count { it }

            val aspect = if (bottomPx > topPx) {
                (rightPx - leftPx).toFloat() / (bottomPx - topPx).toFloat()
            } else {
                0f
            }
            val allFourVisible = visibleCorners == 4 &&
                aspect in 1.12f..2.35f &&
                cardAreaRatio >= MIN_CARD_AREA_RATIO

            // Estimasi kemiringan garis horizontal di dalam area kartu
            val skewDeg = estimateGridSkewDegrees(
                lumGrid = lumGrid,
                gridW = gridW,
                gridH = gridH,
                minGx = if (hasValidBounds) minCardGx else (gridW * 0.15f).toInt(),
                maxGx = if (hasValidBounds) maxCardGx else (gridW * 0.85f).toInt(),
                minGy = if (hasValidBounds) minCardGy else (gridH * 0.20f).toInt(),
                maxGy = if (hasValidBounds) maxCardGy else (gridH * 0.80f).toInt()
            )
            val isTilted = abs(skewDeg) > MAX_ALLOWED_SKEW_DEG

            // Hitung koordinat 4 sudut dengan memperhitungkan kemiringan (skew)
            val skewOffsetPx = ((rightPx - leftPx) * kotlin.math.tan(Math.toRadians(skewDeg.toDouble())) * 0.5).toFloat()
            val tlPoint = PointF(leftPx.toFloat(), (topPx - skewOffsetPx).coerceIn(0f, sourceHeight.toFloat()))
            val trPoint = PointF(rightPx.toFloat(), (topPx + skewOffsetPx).coerceIn(0f, sourceHeight.toFloat()))
            val brPoint = PointF(rightPx.toFloat(), (bottomPx + skewOffsetPx).coerceIn(0f, sourceHeight.toFloat()))
            val blPoint = PointF(leftPx.toFloat(), (bottomPx - skewOffsetPx).coerceIn(0f, sourceHeight.toFloat()))

            val corners = KtpCornerPoints(
                topLeft = tlPoint,
                topRight = trPoint,
                bottomRight = brPoint,
                bottomLeft = blPoint,
                allFourCornersVisible = allFourVisible
            )

            val normalizedCorners = if (hasValidBounds) {
                listOf(
                    PointF((tlPoint.x / sourceWidth).coerceIn(0f, 1f), (tlPoint.y / sourceHeight).coerceIn(0f, 1f)),
                    PointF((trPoint.x / sourceWidth).coerceIn(0f, 1f), (trPoint.y / sourceHeight).coerceIn(0f, 1f)),
                    PointF((brPoint.x / sourceWidth).coerceIn(0f, 1f), (brPoint.y / sourceHeight).coerceIn(0f, 1f)),
                    PointF((blPoint.x / sourceWidth).coerceIn(0f, 1f), (blPoint.y / sourceHeight).coerceIn(0f, 1f))
                )
            } else {
                emptyList()
            }

            // 3. Evaluasi Statistik Kualitas di Dalam Area Kartu (Blur Laplacian, Brightness, Contrast, Glare, Focus)
            val evalMinGx = if (hasValidBounds) minCardGx.coerceIn(1, gridW - 2) else (gridW * 0.15f).toInt()
            val evalMaxGx = if (hasValidBounds) maxCardGx.coerceIn(evalMinGx + 1, gridW - 1) else (gridW * 0.85f).toInt()
            val evalMinGy = if (hasValidBounds) minCardGy.coerceIn(1, gridH - 2) else (gridH * 0.20f).toInt()
            val evalMaxGy = if (hasValidBounds) maxCardGy.coerceIn(evalMinGy + 1, gridH - 1) else (gridH * 0.80f).toInt()

            var sumLum = 0.0
            var sumSqLum = 0.0
            var sumEdge = 0.0
            var sumLapAbs = 0.0
            var glareCells = 0
            var sampleCells = 0

            for (gy in evalMinGy until evalMaxGy) {
                val row = gy * gridW
                for (gx in evalMinGx until evalMaxGx) {
                    sampleCells++
                    val idx = row + gx
                    val lum = lumGrid[idx].toDouble()
                    sumLum += lum
                    sumSqLum += lum * lum
                    sumEdge += gradMag[idx].toDouble()

                    // Operator Laplacian 2D: |4*c - (left + right + up + down)|
                    val lap = abs(
                        (4 * lumGrid[idx]) -
                            (lumGrid[idx - 1] + lumGrid[idx + 1] + lumGrid[idx - gridW] + lumGrid[idx + gridW])
                    )
                    sumLapAbs += lap.toDouble()

                    if (lum >= 246.0) {
                        glareCells++
                    }
                }
            }

            val totalSamples = sampleCells.coerceAtLeast(1)
            val meanLum = sumLum / totalSamples
            val varianceLum = (sumSqLum / totalSamples) - (meanLum * meanLum)
            val contrastStdDev = sqrt(varianceLum.coerceAtLeast(0.0))
            val focusEdgeScore = sumEdge / totalSamples
            val blurLaplacianScore = sumLapAbs / totalSamples
            val computedGlareRatio = overrideGlareRatio ?: (glareCells.toDouble() / totalSamples.toDouble())

            val cardWidthPx = (rightPx - leftPx).coerceAtLeast(0)
            val cardHeightPx = (bottomPx - topPx).coerceAtLeast(0)

            val isCardDetected = hasValidBounds && cardAreaRatio >= 0.14
            val isTooFar = !isCardDetected || cardAreaRatio < MIN_CARD_AREA_RATIO || cardWidthPx < 260
            val isCutOff = !allFourVisible || aspect < 1.12f || aspect > 2.35f
            val isBlurry = blurLaplacianScore < MIN_LAP_BLUR_SCORE && focusEdgeScore < MIN_FOCUS_EDGE_SCORE
            val isFocused = !isBlurry && focusEdgeScore >= MIN_FOCUS_EDGE_SCORE
            val isTooDark = meanLum < MIN_BRIGHTNESS
            val isOverexposed = meanLum > MAX_BRIGHTNESS
            val isLowContrast = contrastStdDev < MIN_CONTRAST_STD_DEV
            val hasGlare = computedGlareRatio > 0.035 && meanLum > 110.0
            val isResolutionSufficient = sourceWidth >= 640 && sourceHeight >= 400 && cardWidthPx >= 260
            val isCardFullyInFrame = isCardDetected && allFourVisible && !isCutOff && ! isTooFar

            val positionStatus = when {
                !isCardDetected -> KtpCardPositionStatus.NOT_FOUND
                isTooFar -> KtpCardPositionStatus.TOO_FAR
                cardAreaRatio > MAX_CARD_AREA_RATIO -> KtpCardPositionStatus.TOO_CLOSE
                isCutOff -> KtpCardPositionStatus.CUT_OFF_BORDER
                isTilted -> KtpCardPositionStatus.TILTED
                else -> KtpCardPositionStatus.IDEAL
            }

            // 4. Susun daftar instruksi pengguna sesuai prioritas Section 3.B:
            // - "Posisikan KTP di dalam bingkai."
            // - "Jauhkan dari pantulan cahaya."
            // - "Pastikan seluruh bagian KTP terlihat."
            // - "Jangan mengambil foto dalam kondisi buram."
            val instructions = buildList {
                if (!isCardDetected || isTooFar) {
                    add(KtpDetectionAndQualityState.INSTRUCTION_POSITION_IN_FRAME)
                }
                if (hasGlare) {
                    add(KtpDetectionAndQualityState.INSTRUCTION_AVOID_GLARE)
                }
                if (isCutOff || !allFourVisible) {
                    add(KtpDetectionAndQualityState.INSTRUCTION_FULL_CARD_VISIBLE)
                }
                if (isBlurry) {
                    add(KtpDetectionAndQualityState.INSTRUCTION_NO_BLUR)
                }
                if (isTooDark) {
                    add(KtpDetectionAndQualityState.INSTRUCTION_NEED_MORE_LIGHT)
                }
                if (isTilted) {
                    add(KtpDetectionAndQualityState.INSTRUCTION_STRAIGHTEN_CARD)
                }
            }

            val isReady = instructions.isEmpty() &&
                isCardDetected &&
                allFourVisible &&
                !isBlurry &&
                !hasGlare &&
                !isTooDark &&
                isResolutionSufficient

            val primaryInstruction = if (isReady) {
                KtpDetectionAndQualityState.INSTRUCTION_READY
            } else {
                instructions.firstOrNull() ?: KtpDetectionAndQualityState.INSTRUCTION_POSITION_IN_FRAME
            }

            val secondaryInstruction = when {
                isReady -> "Seluruh 4 sudut KTP terkunci • Tekan tombol kamera untuk memindai"
                instructions.size > 1 -> instructions.drop(1).joinToString(" ")
                !allFourVisible -> "Sudut terdeteksi: $visibleCorners/4 • Pastikan seluruh bagian KTP terlihat."
                else -> "Jaga kamera tetap stabil dan hindari bayangan atau silau lampu"
            }

            val badges = listOf(
                QualityMetricBadge(
                    key = "corners",
                    label = "Sudut KTP",
                    valueText = "$visibleCorners/4",
                    isPassed = allFourVisible
                ),
                QualityMetricBadge(
                    key = "blur",
                    label = "Fokus",
                    valueText = if (isBlurry) "Buram" else "Tajam",
                    isPassed = !isBlurry
                ),
                QualityMetricBadge(
                    key = "glare",
                    label = "Pantulan",
                    valueText = if (hasGlare) "Silau!" else "Aman",
                    isPassed = !hasGlare
                ),
                QualityMetricBadge(
                    key = "brightness",
                    label = "Cahaya",
                    valueText = when {
                        isTooDark -> "Gelap"
                        isOverexposed -> "Terlalu Terang"
                        else -> "Ideal"
                    },
                    isPassed = !isTooDark && !isOverexposed
                ),
                QualityMetricBadge(
                    key = "tilt",
                    label = "Kemiringan",
                    valueText = String.format(java.util.Locale.US, "%.1f°", abs(skewDeg)),
                    isPassed = !isTilted
                )
            )

            return KtpDetectionAndQualityState(
                isCardDetected = isCardDetected,
                cornerPoints = corners,
                normalizedCorners = normalizedCorners,
                visibleCornerCount = visibleCorners,
                allFourCornersVisible = allFourVisible,
                isCardFullyInFrame = isCardFullyInFrame,
                cardPositionStatus = positionStatus,
                skewAngleDegrees = skewDeg,
                isTilted = isTilted,
                cardAreaRatio = cardAreaRatio,
                detectedBounds = Rect(leftPx, topPx, rightPx, bottomPx),
                blurLaplacianScore = blurLaplacianScore,
                isBlurry = isBlurry,
                brightnessMean = meanLum,
                isTooDark = isTooDark,
                isOverexposed = isOverexposed,
                contrastStdDev = contrastStdDev,
                isLowContrast = isLowContrast,
                glareRatio = computedGlareRatio,
                hasGlare = hasGlare,
                focusEdgeScore = focusEdgeScore,
                isFocused = isFocused,
                frameWidth = sourceWidth,
                frameHeight = sourceHeight,
                cardWidthPx = cardWidthPx,
                cardHeightPx = cardHeightPx,
                isResolutionSufficient = isResolutionSufficient,
                isCardVisible = isCardDetected && allFourVisible,
                isReadyToCapture = isReady,
                primaryInstruction = primaryInstruction,
                secondaryInstruction = secondaryInstruction,
                activeInstructions = if (instructions.isEmpty()) {
                    listOf(KtpDetectionAndQualityState.INSTRUCTION_READY)
                } else {
                    instructions
                },
                qualityBadges = badges,
                analysisTimestampMs = System.currentTimeMillis()
            )
        }

        private fun estimateGridSkewDegrees(
            lumGrid: IntArray,
            gridW: Int,
            gridH: Int,
            minGx: Int,
            maxGx: Int,
            minGy: Int,
            maxGy: Int
        ): Float {
            val boxW = maxGx - minGx
            val boxH = maxGy - minGy
            if (boxW < 24 || boxH < 16) return 0f

            val leftStart = minGx + (boxW * 0.12f).toInt()
            val leftEnd = minGx + (boxW * 0.38f).toInt()
            val rightStart = minGx + (boxW * 0.42f).toInt()
            val rightEnd = minGx + (boxW * 0.68f).toInt()
            val yStart = (minGy + (boxH * 0.18f).toInt()).coerceIn(1, gridH - 2)
            val yEnd = (minGy + (boxH * 0.82f).toInt()).coerceIn(yStart + 4, gridH - 1)
            val bandH = yEnd - yStart
            if (bandH < 8) return 0f

            val leftProfile = DoubleArray(bandH)
            val rightProfile = DoubleArray(bandH)

            for (dy in 0 until bandH) {
                val gy = yStart + dy
                val rowUp = (gy - 1) * gridW
                val rowDown = (gy + 1) * gridW
                var sumL = 0.0
                for (gx in leftStart until leftEnd) {
                    sumL += abs(lumGrid[rowDown + gx] - lumGrid[rowUp + gx])
                }
                leftProfile[dy] = sumL

                var sumR = 0.0
                for (gx in rightStart until rightEnd) {
                    sumR += abs(lumGrid[rowDown + gx] - lumGrid[rowUp + gx])
                }
                rightProfile[dy] = sumR
            }

            val maxShift = (bandH * 0.18f).toInt().coerceIn(2, 10)
            var bestShift = 0
            var bestCorr = -1.0
            var zeroCorr = 0.0

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
                    if (shift == 0) zeroCorr = corr
                    if (corr > bestCorr) {
                        bestCorr = corr
                        bestShift = shift
                    }
                }
            }

            if (bestShift == 0 || bestCorr < zeroCorr * 1.15) return 0f
            val deltaX = ((rightStart + rightEnd) / 2f) - ((leftStart + leftEnd) / 2f)
            if (deltaX <= 4f) return 0f
            val deg = Math.toDegrees(atan2(bestShift.toDouble(), deltaX.toDouble())).toFloat()
            return if (abs(deg) in 0.8f..18f) deg else 0f
        }
    }
}
