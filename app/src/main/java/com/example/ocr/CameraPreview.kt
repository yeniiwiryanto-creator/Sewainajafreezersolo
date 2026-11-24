package com.example.ocr

import android.graphics.Bitmap
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Rasio standar dimensi fisik kartu ID-1 / e-KTP Indonesia (85.60 mm x 53.98 mm = ~1.586 : 1).
 */
const val KTP_CARD_ASPECT_RATIO = 1.586f

/**
 * Composable `CameraPreview` yang menggunakan CameraX (`Preview`, `ImageCapture`, dan `ImageAnalysis`)
 * serta modul OpenCV `KtpCameraQualityAnalyzer` untuk menyediakan:
 * 1. Viewfinder real-time CameraX (`PreviewView`) terintegrasi dengan `LifecycleOwner`.
 * 2. Live KTP Detection (deteksi keberadaan kartu, 4 titik sudut, kelengkapan frame, dan kemiringan).
 * 3. Camera Quality Validation real-time (Blur Laplacian, Brightness, Contrast, Glare/Pantulan, Focus, Resolusi).
 * 4. Tampilan instruksi pengguna dinamis berdasarkan status kualitas gambar sebelum foto diambil.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture? = null,
    isTorchEnabled: Boolean = false,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    cardGuideAspectRatio: Float = KTP_CARD_ASPECT_RATIO,
    showCardOverlay: Boolean = true,
    enableLiveQualityAnalysis: Boolean = true,
    externalQualityState: KtpDetectionAndQualityState? = null,
    alignmentPrompt: String = KtpDetectionAndQualityState.INSTRUCTION_POSITION_IN_FRAME,
    instructionSubtext: String = "Pastikan seluruh bagian KTP terlihat dan jauhkan dari pantulan cahaya",
    isScanningBeamActive: Boolean = true,
    onQualityStateChanged: ((KtpDetectionAndQualityState) -> Unit)? = null,
    onCameraBound: ((Camera) -> Unit)? = null,
    onCameraError: ((Throwable) -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var previewUseCaseRef by remember { mutableStateOf<Preview?>(null) }
    var imageAnalysisRef by remember { mutableStateOf<ImageAnalysis?>(null) }
    var focusTapPosition by remember { mutableStateOf<Offset?>(null) }
    var liveQualityState by remember { mutableStateOf<KtpDetectionAndQualityState?>(null) }
    var activeSurfaceRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val currentOnCameraBound by rememberUpdatedState(onCameraBound)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val currentOnQualityStateChanged by rememberUpdatedState(onQualityStateChanged)
    val currentEnableLiveQualityAnalysis by rememberUpdatedState(enableLiveQualityAnalysis)

    val activeQualityState = externalQualityState ?: liveQualityState

    // Sinkronisasi orientasi perangkat (Portrait 0°, Landscape 90°/270°, Terbalik 180°)
    // ke ImageCapture.targetRotation & ImageAnalysis.targetRotation agar preview & hasil foto selalu tegak.
    DisposableEffect(context, imageCapture, imageAnalysisRef) {
        val listener = runCatching {
            object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    val rotation = when (orientation) {
                        in 45..134 -> Surface.ROTATION_270
                        in 135..224 -> Surface.ROTATION_180
                        in 225..314 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }
                    if (rotation != activeSurfaceRotation) {
                        activeSurfaceRotation = rotation
                        imageCapture?.targetRotation = rotation
                        imageAnalysisRef?.targetRotation = rotation
                    }
                }
            }.also { it.enable() }
        }.getOrNull()

        onDispose {
            runCatching { listener?.disable() }
        }
    }

    // Perbarui status senter / torch saat nilai parameter berubah
    LaunchedEffect(boundCamera, isTorchEnabled) {
        try {
            boundCamera?.cameraControl?.enableTorch(isTorchEnabled)
        } catch (e: Throwable) {
            Log.w("CameraPreview", "Gagal memperbarui status torch: ${e.message}")
        }
    }

    // Bind CameraX hanya saat previewView, cameraSelector, atau imageCapture berubah.
    // Jangan unbind/rebind saat enableLiveQualityAnalysis berubah di tengah proses takePicture().
    LaunchedEffect(previewViewRef, cameraSelector, imageCapture) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                previewUseCaseRef = preview

                imageCapture?.targetRotation = activeSurfaceRotation

                val delegateAnalyzer = KtpCameraQualityAnalyzer(minAnalysisIntervalMs = 180L) { state ->
                    mainExecutor.execute {
                        liveQualityState = state
                        currentOnQualityStateChanged?.invoke(state)
                    }
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(activeSurfaceRotation)
                    .build()
                    .also { analysisUseCase ->
                        analysisUseCase.setAnalyzer(analysisExecutor) { imageProxy ->
                            if (!currentEnableLiveQualityAnalysis) {
                                imageProxy.close()
                            } else {
                                delegateAnalyzer.analyze(imageProxy)
                            }
                        }
                    }
                imageAnalysisRef = imageAnalysis

                cameraProvider.unbindAll()

                val camera = if (imageCapture != null) {
                    try {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture,
                            imageAnalysis
                        )
                    } catch (bindEx: Throwable) {
                        Log.w("CameraPreview", "Fallback bind tanpa ImageAnalysis: ${bindEx.message}")
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    }
                } else {
                    try {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (bindEx: Throwable) {
                        Log.w("CameraPreview", "Fallback bind hanya Preview: ${bindEx.message}")
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    }
                }

                boundCamera = camera
                currentOnCameraBound?.invoke(camera)
                runCatching { camera.cameraControl.enableTorch(isTorchEnabled) }
            } catch (t: Throwable) {
                Log.e("CameraPreview", "Gagal menginisialisasi kamera CameraX: ${t.message}", t)
                currentOnCameraError?.invoke(t)
            }
        }, mainExecutor)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(boundCamera, previewViewRef) {
                detectTapGestures { tapOffset ->
                    val view = previewViewRef ?: return@detectTapGestures
                    val camera = boundCamera ?: return@detectTapGestures
                    try {
                        val meteringPointFactory = view.meteringPointFactory
                        val point = meteringPointFactory.createPoint(tapOffset.x, tapOffset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        camera.cameraControl.startFocusAndMetering(action)
                        focusTapPosition = tapOffset
                    } catch (t: Throwable) {
                        Log.d("CameraPreview", "Tap to focus error: ${t.message}")
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. CameraX AndroidView Viewfinder + Real-Time ImageAnalysis
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also {
                    previewViewRef = it
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Real-time KTP Alignment & Quality Validation Overlay
        if (showCardOverlay) {
            KtpViewfinderOverlay(
                modifier = Modifier.fillMaxSize(),
                cardAspectRatio = cardGuideAspectRatio,
                isScanningBeamActive = isScanningBeamActive,
                focusPoint = focusTapPosition,
                qualityState = activeQualityState,
                alignmentPrompt = activeQualityState?.primaryInstruction ?: alignmentPrompt,
                instructionSubtext = activeQualityState?.secondaryInstruction ?: instructionSubtext,
                onFocusDismissed = { focusTapPosition = null }
            )
        }

        // 3. Slot Konten Tambahan di atas Viewfinder
        overlayContent?.invoke(this)
    }

    // Bersihkan unbind & executor saat composable keluar dari composition
    DisposableEffect(Unit) {
        onDispose {
            try {
                boundCamera?.cameraControl?.enableTorch(false)
            } catch (_: Throwable) {
            }
            try {
                imageAnalysisRef?.clearAnalyzer()
            } catch (_: Throwable) {
            }
            try {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    runCatching {
                        val provider = providerFuture.get()
                        val useCases = listOfNotNull(previewUseCaseRef, imageCapture, imageAnalysisRef)
                        if (useCases.isNotEmpty()) {
                            provider.unbind(*useCases.toTypedArray())
                        }
                    }
                }, mainExecutor)
            } catch (_: Throwable) {
            }
            try {
                analysisExecutor.shutdown()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Komponen overlay grafis viewfinder KTP dengan umpan balik Live KTP Detection & Camera Quality Validation:
 * - Scrim gelap di sekeliling kartu.
 * - Garis putus-putus pembatas e-KTP & 4 sudut siku-siku (hijau saat kualitas siap, kuning/merah saat ada kendala).
 * - Poligon 4 sudut kartu real-time hasil deteksi OpenCV (`normalizedCorners`).
 * - Panduan posisi foto wajah KTP di sebelah kanan.
 * - Animasi garis sinar pemindaian (scan beam) vertikal.
 * - Indikator fokus sentuh (tap-to-focus ring).
 * - Banner instruksi dinamis ("Posisikan KTP di dalam bingkai.", "Jauhkan dari pantulan cahaya.",
 *   "Pastikan seluruh bagian KTP terlihat.", "Jangan mengambil foto dalam kondisi buram.")
 * - Baris indikator metrik kualitas kamera (Sudut 4/4, Fokus/Blur, Pantulan/Glare, Cahaya, Kemiringan).
 */
@Composable
fun KtpViewfinderOverlay(
    modifier: Modifier = Modifier,
    cardAspectRatio: Float = KTP_CARD_ASPECT_RATIO,
    isScanningBeamActive: Boolean = true,
    focusPoint: Offset? = null,
    qualityState: KtpDetectionAndQualityState? = null,
    alignmentPrompt: String = KtpDetectionAndQualityState.INSTRUCTION_POSITION_IN_FRAME,
    instructionSubtext: String = "Pastikan seluruh sudut kartu terlihat dan tidak silau",
    onFocusDismissed: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "KtpScannerBeamTransition")
    val beamProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BeamProgress"
    )

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            kotlinx.coroutines.delay(1500)
            onFocusDismissed()
        }
    }

    val isReady = qualityState?.isReadyToCapture == true
    val hasIssues = qualityState != null && !qualityState.isReadyToCapture && qualityState.isCardDetected

    val guideColor = when {
        isReady -> Color(0xFF10B981) // Emerald Green when all 4 corners + quality checks pass
        hasIssues -> Color(0xFFF59E0B) // Amber when card detected but blur/glare/cut-off exists
        else -> Color(0xFF38BDF8) // Cyan default guide
    }
    val bracketColor = when {
        isReady -> Color(0xFF34D399)
        hasIssues -> Color(0xFFFBBF24)
        else -> Color(0xFF0284C7)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val cardWidth = canvasWidth * 0.88f
            val cardHeight = cardWidth / cardAspectRatio
            val left = (canvasWidth - cardWidth) / 2f
            val top = (canvasHeight - cardHeight) / 2f
            val right = left + cardWidth
            val bottom = top + cardHeight

            val scrimColor = Color(0x99000000)
            val photoBoxColor = guideColor.copy(alpha = 0.45f)

            // 1. Scrim gelap di 4 sisi luar kartu KTP
            drawRect(color = scrimColor, topLeft = Offset.Zero, size = Size(canvasWidth, top))
            drawRect(color = scrimColor, topLeft = Offset(0f, bottom), size = Size(canvasWidth, canvasHeight - bottom))
            drawRect(color = scrimColor, topLeft = Offset(0f, top), size = Size(left, cardHeight))
            drawRect(color = scrimColor, topLeft = Offset(right, top), size = Size(canvasWidth - right, cardHeight))

            // 2. Garis batas kartu putus-putus
            drawRoundRect(
                color = guideColor.copy(alpha = 0.88f),
                topLeft = Offset(left, top),
                size = Size(cardWidth, cardHeight),
                cornerRadius = CornerRadius(20f, 20f),
                style = Stroke(
                    width = 3.5f,
                    pathEffect = if (isReady) null else PathEffect.dashPathEffect(floatArrayOf(24f, 14f), 0f)
                )
            )

            // 3. Empat Sudut Siku-Siku Penuntun (Corner Brackets)
            val cornerLength = cardWidth * 0.12f
            val cornerStroke = 7.5f

            // Top-Left Corner
            drawPath(
                path = Path().apply {
                    moveTo(left, top + cornerLength)
                    lineTo(left, top + 10f)
                    quadraticBezierTo(left, top, left + 10f, top)
                    lineTo(left + cornerLength, top)
                },
                color = bracketColor,
                style = Stroke(width = cornerStroke)
            )

            // Top-Right Corner
            drawPath(
                path = Path().apply {
                    moveTo(right - cornerLength, top)
                    lineTo(right - 10f, top)
                    quadraticBezierTo(right, top, right, top + 10f)
                    lineTo(right, top + cornerLength)
                },
                color = bracketColor,
                style = Stroke(width = cornerStroke)
            )

            // Bottom-Left Corner
            drawPath(
                path = Path().apply {
                    moveTo(left, bottom - cornerLength)
                    lineTo(left, bottom - 10f)
                    quadraticBezierTo(left, bottom, left + 10f, bottom)
                    lineTo(left + cornerLength, bottom)
                },
                color = bracketColor,
                style = Stroke(width = cornerStroke)
            )

            // Bottom-Right Corner
            drawPath(
                path = Path().apply {
                    moveTo(right - cornerLength, bottom)
                    lineTo(right - 10f, bottom)
                    quadraticBezierTo(right, bottom, right, bottom - 10f)
                    lineTo(right, bottom - cornerLength)
                },
                color = bracketColor,
                style = Stroke(width = cornerStroke)
            )

            // 3b. Visualisasi Poligon 4 Titik Sudut KTP Real-Time (OpenCV Quadrilateral Detection)
            val normCorners = qualityState?.normalizedCorners.orEmpty()
            if (normCorners.size == 4 && qualityState?.isCardDetected == true) {
                val polyPts = normCorners.map { pt ->
                    Offset(
                        x = left + (pt.x.coerceIn(0f, 1f) * cardWidth),
                        y = top + (pt.y.coerceIn(0f, 1f) * cardHeight)
                    )
                }
                val quadPath = Path().apply {
                    moveTo(polyPts[0].x, polyPts[0].y)
                    lineTo(polyPts[1].x, polyPts[1].y)
                    lineTo(polyPts[2].x, polyPts[2].y)
                    lineTo(polyPts[3].x, polyPts[3].y)
                    close()
                }
                drawPath(
                    path = quadPath,
                    color = guideColor.copy(alpha = 0.55f),
                    style = Stroke(width = 2.5f)
                )
                polyPts.forEach { cornerOffset ->
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = cornerOffset
                    )
                    drawCircle(
                        color = bracketColor,
                        radius = 5.dp.toPx(),
                        center = cornerOffset
                    )
                }
            }

            // 4. Bingkai Penanda Foto KTP di sisi kanan kartu
            val photoBoxLeft = left + cardWidth * 0.70f
            val photoBoxTop = top + cardHeight * 0.22f
            val photoBoxWidth = cardWidth * 0.24f
            val photoBoxHeight = cardHeight * 0.58f

            drawRoundRect(
                color = photoBoxColor,
                topLeft = Offset(photoBoxLeft, photoBoxTop),
                size = Size(photoBoxWidth, photoBoxHeight),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )

            // 5. Animasi Garis Sinar Pemindaian (Scan Beam)
            if (isScanningBeamActive) {
                val beamY = top + (cardHeight * beamProgress)
                drawLine(
                    color = Color(0xFF34D399).copy(alpha = 0.90f),
                    start = Offset(left + 10f, beamY),
                    end = Offset(right - 10f, beamY),
                    strokeWidth = 4f
                )
                drawLine(
                    color = guideColor.copy(alpha = 0.35f),
                    start = Offset(left + 15f, beamY),
                    end = Offset(right - 15f, beamY),
                    strokeWidth = 12f
                )
            }

            // 6. Indikator Tap-to-Focus
            if (focusPoint != null) {
                drawCircle(
                    color = Color(0xFFFACC15),
                    radius = 48f,
                    center = focusPoint,
                    style = Stroke(width = 3.5f)
                )
                drawCircle(
                    color = Color(0xFFFACC15),
                    radius = 6f,
                    center = focusPoint
                )
            }
        }

        // Banner Instruksi Kualitas Kamera Dinamis (Section 3.B)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = when {
                    isReady -> Color(0xDD065F46)
                    hasIssues -> Color(0xDD7C2D12)
                    else -> Color(0xDD0F172A)
                },
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = guideColor.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .testTag("ktp_camera_instruction_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isReady -> Icons.Default.CheckCircle
                            hasIssues -> Icons.Default.Warning
                            else -> Icons.Default.Badge
                        },
                        contentDescription = null,
                        tint = when {
                            isReady -> Color(0xFF34D399)
                            hasIssues -> Color(0xFFFBBF24)
                            else -> Color(0xFF38BDF8)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alignmentPrompt,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (instructionSubtext.isNotBlank()) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = instructionSubtext,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Live Camera Quality Validation Badges (4 Corners, Focus/Blur, Glare, Brightness, Tilt)
            val badges = qualityState?.qualityBadges.orEmpty()
            if (badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .testTag("ktp_camera_quality_badges_row")
                ) {
                    badges.forEach { badge ->
                        Surface(
                            color = if (badge.isPassed) {
                                Color(0xCC064E3B)
                            } else {
                                Color(0xCC7F1D1D)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = if (badge.isPassed) Color(0xFF34D399) else Color(0xFFF87171),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (badge.isPassed) Color(0xFF34D399) else Color(0xFFF87171))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${badge.label}: ${badge.valueText}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Viewfinder pemindai e-KTP mandiri lengkap dengan kontrol kamera, tombol senter,
 * deteksi 4 sudut real-time, validasi kualitas kamera, pemicu foto (capture), pemilihan galeri, dan tombol uji sampel.
 */
@Composable
fun KtpCameraViewfinder(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture,
    isProcessing: Boolean = false,
    processingStatusText: String = "",
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    capturedPreviewBitmap: Bitmap? = null,
    capturedQualityReport: KtpImageQualityReport? = null,
    onSwitchCameraClick: (() -> Unit)? = null,
    onRetakeClick: (() -> Unit)? = null,
    onConfirmUsePhotoClick: (() -> Unit)? = null,
    onCaptureClick: () -> Unit,
    onCloseClick: () -> Unit,
    onGalleryClick: (() -> Unit)? = null,
    onSystemCameraClick: (() -> Unit)? = null,
    onSampleTestClick: (() -> Unit)? = null
) {
    var flashEnabled by remember { mutableStateOf(false) }
    var currentQualityState by remember { mutableStateOf<KtpDetectionAndQualityState?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (capturedPreviewBitmap != null) {
            // Mode Konfirmasi Hasil Foto Sebelum Disimpan / Diproses OCR
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A2540))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "KONFIRMASI FOTO KTP",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pastikan posisi KTP tegak, tidak terpotong, dan seluruh tulisan NIK terbaca tajam",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFBAE6FD)
                        )
                    }
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_close_viewfinder")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = capturedPreviewBitmap.asImageBitmap(),
                        contentDescription = "Pratinjau Foto KTP",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (capturedQualityReport != null) {
                    val hasIssues = !capturedQualityReport.isAcceptable ||
                        capturedQualityReport.qualityIssues.isNotEmpty()
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasIssues) Color(0xFF451A03) else Color(0xFF064E3B)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ktp_review_quality_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (hasIssues) {
                                    "Perlu Perhatian Kualitas KTP — Disarankan Ulangi Foto"
                                } else {
                                    "Kualitas Foto KTP Jelas & Siap OCR"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasIssues) Color(0xFFFDE68A) else Color(0xFF6EE7B7),
                                fontWeight = FontWeight.Bold
                            )
                            if (hasIssues) {
                                Text(
                                    text = capturedQualityReport.cameraGuidanceInstructions
                                        .joinToString(" ")
                                        .ifBlank { capturedQualityReport.retakePromptMessage },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFEF3C7)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRetakeClick?.invoke() ?: onCloseClick() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_retake_photo")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ulangi Foto", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onConfirmUsePhotoClick?.invoke() ?: onCaptureClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_use_photo")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gunakan Foto", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // 1. Real-time CameraX Viewfinder + Live KTP Detection & Quality Validation
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                imageCapture = imageCapture,
                cameraSelector = cameraSelector,
                isTorchEnabled = flashEnabled,
                isScanningBeamActive = !isProcessing,
                showCardOverlay = true,
                enableLiveQualityAnalysis = !isProcessing,
                onQualityStateChanged = { currentQualityState = it }
            )

            // 2. Header Bar: Judul, Balik Kamera, Senter, & Tombol Tutup
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = Color(0xCC0284C7),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SCANNER e-KTP",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Deteksi 4 Sudut • Validasi Blur, Cahaya & Glare",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onSwitchCameraClick != null) {
                        IconButton(
                            onClick = onSwitchCameraClick,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0x88000000), CircleShape)
                                .testTag("btn_switch_camera")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Ganti Kamera Depan/Belakang",
                                tint = Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = { flashEnabled = !flashEnabled },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0x88000000), CircleShape)
                            .testTag("btn_toggle_flash")
                    ) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash Senter",
                            tint = if (flashEnabled) Color(0xFFFACC15) else Color.White
                        )
                    }

                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0x88000000), CircleShape)
                            .testTag("btn_close_viewfinder")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.White
                        )
                    }
                }
            }

            // 3. Indikator Loading saat Pemrosesan Berlangsung
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = processingStatusText.ifBlank { "Membaca & mengekstrak data KTP..." },
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. Bottom Control Bar: Shutter, Galeri, Kamera Sistem, & Uji KTP
            Surface(
                color = Color(0xDD0F172A),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentQualityState != null) {
                        Text(
                            text = currentQualityState!!.primaryInstruction,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentQualityState!!.isReadyToCapture) Color(0xFF34D399) else Color(0xFFFDE68A),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tombol Jepret Utama (Shutter Button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (onGalleryClick != null) {
                            IconButton(
                                onClick = onGalleryClick,
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0x4438BDF8), CircleShape)
                                    .testTag("btn_viewfinder_gallery")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Pilih dari Galeri",
                                    tint = Color(0xFF38BDF8)
                                )
                            }
                        }

                        Button(
                            onClick = onCaptureClick,
                            enabled = !isProcessing,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentQualityState?.isReadyToCapture == true) {
                                    Color(0xFF10B981)
                                } else {
                                    Color(0xFF0284C7)
                                },
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(70.dp)
                                .testTag("btn_viewfinder_capture")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Jepret KTP",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        if (onSystemCameraClick != null) {
                            IconButton(
                                onClick = onSystemCameraClick,
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0x4438BDF8), CircleShape)
                                    .testTag("btn_viewfinder_system_camera")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Buka Kamera HP",
                                    tint = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onSystemCameraClick != null) {
                            FilledTonalButton(
                                onClick = onSystemCameraClick,
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Kamera HP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (onSampleTestClick != null) {
                            OutlinedButton(
                                onClick = onSampleTestClick,
                                enabled = !isProcessing,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Uji Sampel KTP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
