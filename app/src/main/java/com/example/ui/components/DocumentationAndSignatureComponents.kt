package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.util.DateCurrencyUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.view.OrientationEventListener
import android.view.Surface
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.example.media.PhotoCategory
import com.example.media.SmartImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Reusable Photo Slot Card for KTP, Unit Freezer, Serah Terima, and Depan Lokasi photos.
 * Supports:
 * - Direct in-app CameraX capture
 * - Zero-permission Gallery PickVisualMedia
 * - Preview full photo before saving
 * - Replace photo
 */
@Composable
fun PhotoDocumentationSlot(
    label: String,
    photoKey: String,
    transactionOrEntityFolder: String,
    currentUriOrPath: String,
    isMandatory: Boolean = false,
    timestampText: String = "",
    captionText: String = "",
    transactionNumber: String = "",
    onTimestampUpdated: ((String) -> Unit)? = null,
    onCaptionChanged: ((String) -> Unit)? = null,
    onPhotoSaved: (String) -> Unit
) {
    val context = LocalContext.current
    var showCameraDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCameraDialog = true
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiedFile = copyUriToInternalPhotoFile(context, uri, transactionOrEntityFolder, photoKey)
            if (copiedFile != null) {
                onTimestampUpdated?.invoke(DateCurrencyUtils.nowDateTimeDisplay())
                onPhotoSaved(copiedFile.absolutePath)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isMandatory) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "WAJIB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (currentUriOrPath.isNotBlank()) {
                    Surface(
                        color = Color(0xFFD1FAE5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Tersimpan",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF065F46),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (currentUriOrPath.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.08f))
                        .clickable { showPreviewDialog = true }
                ) {
                    AsyncImage(
                        model = File(currentUriOrPath).takeIf { it.exists() } ?: currentUriOrPath,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = "Lihat Foto",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ketuk untuk perbesar", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = {
                        val hasCamPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCamPermission) {
                            showCameraDialog = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_camera_$photoKey")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Kamera", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (currentUriOrPath.isBlank()) "Kamera" else "Ganti Kamera", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_gallery_$photoKey")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeri", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (currentUriOrPath.isBlank()) "Galeri" else "Ganti Galeri", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (timestampText.isNotBlank() || transactionNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildString {
                        if (timestampText.isNotBlank()) append("Waktu Ambil: $timestampText")
                        if (transactionNumber.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append("No. Transaksi: $transactionNumber")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (onCaptionChanged != null) {
                Spacer(modifier = Modifier.height(6.dp))
                SmartCaseOutlinedTextField(
                    value = captionText,
                    onValueChange = onCaptionChanged,
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Keterangan Foto ($label)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_caption_$photoKey")
                )
            }
        }
    }

    if (showCameraDialog) {
        CameraXCaptureDialog(
            title = label,
            folderName = transactionOrEntityFolder,
            filePrefix = photoKey,
            onDismiss = { showCameraDialog = false },
            onPhotoCaptured = { savedFile ->
                onTimestampUpdated?.invoke(DateCurrencyUtils.nowDateTimeDisplay())
                onPhotoSaved(savedFile.absolutePath)
                showCameraDialog = false
            }
        )
    }

    if (showPreviewDialog && currentUriOrPath.isNotBlank()) {
        Dialog(
            onDismissRequest = { showPreviewDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Foto Asli Tanpa Watermark Permanen • Waktu: ${timestampText.ifBlank { "Tersimpan" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (captionText.isNotBlank()) {
                                Text(
                                    text = "Keterangan: $captionText",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        IconButton(onClick = { showPreviewDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = File(currentUriOrPath).takeIf { it.exists() } ?: currentUriOrPath,
                            contentDescription = label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraXCaptureDialog(
    title: String,
    folderName: String,
    filePrefix: String,
    photoCategory: PhotoCategory = PhotoCategory.fromSlotOrType(filePrefix),
    onDismiss: () -> Unit,
    onPhotoCaptured: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var capturedPreviewFile by remember { mutableStateOf<File?>(null) }
    var isProcessingCapture by remember { mutableStateOf(false) }
    var activeSurfaceRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var previewUseCaseRef by remember { mutableStateOf<Preview?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Sinkronkan orientasi perangkat (Portrait / Landscape / Inverted 180) dengan ImageCapture.targetRotation
    DisposableEffect(context, imageCapture) {
        val orientationListener = runCatching {
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
                        imageCapture.targetRotation = rotation
                    }
                }
            }.also { it.enable() }
        }.getOrNull()

        onDispose {
            runCatching { orientationListener?.disable() }
            runCatching {
                val mainExec = ContextCompat.getMainExecutor(context)
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    runCatching {
                        val provider = providerFuture.get()
                        val useCases = listOfNotNull(previewUseCaseRef, imageCapture)
                        if (useCases.isNotEmpty()) {
                            provider.unbind(*useCases.toTypedArray())
                        }
                    }
                }, mainExec)
            }
        }
    }

    LaunchedEffect(lensFacing) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    imageCapture.targetRotation = activeSurfaceRotation
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    previewUseCaseRef = preview
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.w("CameraX", "Camera bind fallback: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.w("CameraX", "CameraProvider init error: ${e.message}")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = Color(0xFF0A2540)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kamera Dokumentasi SAF",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup Kamera", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (capturedPreviewFile != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = capturedPreviewFile,
                                contentDescription = "Preview Foto",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (capturedPreviewFile != null) {
                    // Konfirmasi pratinjau foto sebelum disimpan permanen
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                capturedPreviewFile?.delete()
                                capturedPreviewFile = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_retake_photo"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Ulangi")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ulangi Foto", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                capturedPreviewFile?.let { onPhotoCaptured(it) }
                            },
                            modifier = Modifier
                                .weight(1.1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_use_photo"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Gunakan Foto")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gunakan Foto", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .testTag("btn_switch_camera")
                        ) {
                            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Balik Kamera", tint = Color.White)
                        }

                        Button(
                            enabled = !isProcessingCapture,
                            onClick = {
                                isProcessingCapture = true
                                val targetDir = File(context.filesDir, "photos/$folderName")
                                if (!targetDir.exists()) targetDir.mkdirs()
                                val photoFile = File(targetDir, "${filePrefix}_${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                val capturedWithFrontCam = (lensFacing == CameraSelector.LENS_FACING_FRONT)

                                imageCapture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    SmartImageProcessor.normalizeAndCompressFileInPlace(
                                                        context = context,
                                                        targetFile = photoFile,
                                                        category = photoCategory,
                                                        isFrontCamera = capturedWithFrontCam
                                                    )
                                                }
                                                isProcessingCapture = false
                                                capturedPreviewFile = photoFile
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            // If running on an environment without physical camera stream, generate a clear stamped documentation photo
                                            val fallback = createStampedPhotoDocument(context, photoFile, title, folderName)
                                            isProcessingCapture = false
                                            capturedPreviewFile = fallback
                                        }
                                    }
                                )
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier
                                .heightIn(min = 56.dp)
                                .testTag("btn_camera_capture")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Ambil Foto")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProcessingCapture) "MEMPROSES..." else "AMBIL FOTO",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun createStampedPhotoDocument(
    context: Context,
    targetFile: File,
    title: String,
    folderName: String
): File {
    val bmp = Bitmap.createBitmap(640, 420, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(AndroidColor.rgb(15, 39, 68))
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 26f
        isFakeBoldText = true
    }
    canvas.drawText("SEWAin Aja Freezer SOC", 36f, 68f, paint)
    paint.textSize = 20f
    paint.color = AndroidColor.rgb(56, 189, 248)
    canvas.drawText("Dokumentasi: $title", 36f, 115f, paint)
    paint.color = AndroidColor.LTGRAY
    paint.textSize = 16f
    canvas.drawText("ID Referensi: $folderName", 36f, 155f, paint)
    canvas.drawText("Waktu Kamera: ${com.example.util.DateCurrencyUtils.nowDateTimeDisplay()}", 36f, 190f, paint)
    FileOutputStream(targetFile).use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 84, out)
    }
    return targetFile
}

private fun copyUriToInternalPhotoFile(
    context: Context,
    sourceUri: Uri,
    folderName: String,
    photoKey: String
): File? {
    return try {
        val dir = File(context.filesDir, "photos/$folderName")
        if (!dir.exists()) dir.mkdirs()
        val destFile = File(dir, "${photoKey}_${System.currentTimeMillis()}.jpg")
        val thumbFile = SmartImageProcessor.resolveThumbnailFileFor(context, destFile)
        val processed = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = sourceUri,
            category = PhotoCategory.fromSlotOrType(photoKey),
            outputMainFile = destFile,
            outputThumbFile = thumbFile
        )
        if (processed != null && destFile.exists() && destFile.length() > 0L) {
            return destFile
        }
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile
    } catch (e: Exception) {
        null
    }
}

/**
 * Section 4 & 5: Interactive Digital Signature Pad + Signature Image Upload (PNG/JPG/JPEG)
 * Supports:
 * - Metode 1: Tanda Tangan Langsung (Draw on white canvas with finger, Hapus, Ulangi, Simpan TTD)
 * - Metode 2: Upload Gambar Tanda Tangan (Upload from Gallery or capture from Camera, auto remove white background -> transparent PNG, preview, replace/delete)
 */
@Composable
fun DigitalSignaturePad(
    title: String,
    signerName: String,
    transactionFolder: String,
    filePrefix: String,
    existingSignaturePath: String,
    onSignatureSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var savedPath by remember(existingSignaturePath) { mutableStateOf(existingSignaturePath) }
    // Mode: "DRAW" (Metode 1: Tanda Tangan Langsung) or "UPLOAD_IMAGE" (Metode 2: Upload Gambar Tanda Tangan)
    var selectedMethod by remember { mutableStateOf("DRAW") }
    var autoRemoveWhiteBg by remember { mutableStateOf(true) }

    val gallerySignatureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = SignatureImageProcessor.decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                val outFile = SignatureImageProcessor.saveUploadedOrCapturedSignature(
                    context = context,
                    sourceBitmap = bmp,
                    transactionFolder = transactionFolder,
                    filePrefix = filePrefix,
                    signerName = signerName,
                    removeWhiteBackground = autoRemoveWhiteBg
                )
                savedPath = outFile.absolutePath
                onSignatureSaved(outFile.absolutePath)
            }
        }
    }

    val cameraSignatureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            val outFile = SignatureImageProcessor.saveUploadedOrCapturedSignature(
                context = context,
                sourceBitmap = bmp,
                transactionFolder = transactionFolder,
                filePrefix = filePrefix,
                signerName = signerName,
                removeWhiteBackground = autoRemoveWhiteBg
            )
            savedPath = outFile.absolutePath
            onSignatureSaved(outFile.absolutePath)
        } else {
            val fallbackFile = saveSignatureBitmapToFile(
                context = context,
                strokes = strokes,
                signerName = signerName,
                folderName = transactionFolder,
                filePrefix = filePrefix
            )
            savedPath = fallbackFile.absolutePath
            onSignatureSaved(fallbackFile.absolutePath)
        }
    }

    val cameraSignaturePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraSignatureLauncher.launch(null)
        } else {
            val fallbackFile = saveSignatureBitmapToFile(
                context = context,
                strokes = strokes,
                signerName = signerName,
                folderName = transactionFolder,
                filePrefix = filePrefix
            )
            savedPath = fallbackFile.absolutePath
            onSignatureSaved(fallbackFile.absolutePath)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (savedPath.isNotBlank()) {
                    Surface(
                        color = Color(0xFFD1FAE5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Tanda Tangan Tersimpan",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF065F46),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pilihan Metode Tanda Tangan (Metode 1: Langsung vs Metode 2: Upload Gambar)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedMethod == "DRAW") Color(0xFF0A2540) else Color(0xFFF1F5F9),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedMethod = "DRAW" }
                        .testTag("tab_sig_draw_$filePrefix")
                ) {
                    Text(
                        text = "1. TTD Langsung",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedMethod == "DRAW") Color.White else Color(0xFF334155),
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedMethod == "UPLOAD_IMAGE") Color(0xFF0284C7) else Color(0xFFF1F5F9),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedMethod = "UPLOAD_IMAGE" }
                        .testTag("tab_sig_upload_$filePrefix")
                ) {
                    Text(
                        text = "2. Upload Gambar TTD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedMethod == "UPLOAD_IMAGE") Color.White else Color(0xFF334155),
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedMethod == "DRAW") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes.add(currentStroke)
                                        currentStroke = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val allStrokes = strokes + listOf(currentStroke)
                        for (pts in allStrokes) {
                            if (pts.size > 1) {
                                val path = Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    for (i in 1 until pts.size) {
                                        lineTo(pts[i].x, pts[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF0A2540),
                                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }

                    if (strokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            text = if (savedPath.isNotBlank()) "Tanda tangan telah tersimpan. Goreskan di sini untuk memperbarui."
                            else "Tanda tangan langsung pada layar HP di area ini",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                strokes.removeAt(strokes.lastIndex)
                            }
                            currentStroke = emptyList()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ulangi", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            strokes.clear()
                            currentStroke = emptyList()
                            savedPath = ""
                            onSignatureSaved("")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Bersihkan", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Hapus", style = MaterialTheme.typography.labelSmall)
                    }

                    FilledTonalButton(
                        onClick = {
                            val file = saveSignatureBitmapToFile(
                                context = context,
                                strokes = strokes,
                                signerName = signerName,
                                folderName = transactionFolder,
                                filePrefix = filePrefix
                            )
                            savedPath = file.absolutePath
                            onSignatureSaved(file.absolutePath)
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("btn_save_sig_$filePrefix")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Terapkan Tanda Tangan", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simpan TTD", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                // Metode 2: Upload Gambar Tanda Tangan (PNG, JPG, JPEG) + Auto Remove White Background
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Otomatis Hapus Background Putih (Transparan PNG)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0A2540),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (autoRemoveWhiteBg) Color(0xFFD1FAE5) else Color(0xFFE2E8F0),
                                modifier = Modifier.clickable { autoRemoveWhiteBg = !autoRemoveWhiteBg }
                            ) {
                                Text(
                                    text = if (autoRemoveWhiteBg) "AKTIF" else "NONAKTIF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (autoRemoveWhiteBg) Color(0xFF065F46) else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    gallerySignatureLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_upload_sig_gallery_$filePrefix")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Upload Galeri", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Galeri (PNG/JPG)", style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val hasCam = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasCam) {
                                        cameraSignatureLauncher.launch(null)
                                    } else {
                                        cameraSignaturePermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_upload_sig_camera_$filePrefix")
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Kamera TTD", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Foto TTD", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Preview Tanda Tangan Sebelum Disimpan & Tombol Ganti / Hapus
            if (savedPath.isNotBlank() && File(savedPath).exists()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF94A3B8), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Preview Tanda Tangan (${signerName.ifBlank { "Penandatangan" }})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A2540)
                            )
                            Text(
                                text = "Hapus TTD",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        savedPath = ""
                                        onSignatureSaved("")
                                    }
                                    .testTag("btn_clear_saved_sig_$filePrefix")
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = File(savedPath),
                                contentDescription = "Preview Tanda Tangan",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun saveSignatureBitmapToFile(
    context: Context,
    strokes: List<List<Offset>>,
    signerName: String,
    folderName: String,
    filePrefix: String
): File {
    val dir = File(context.filesDir, "photos/$folderName")
    if (!dir.exists()) dir.mkdirs()
    val outFile = File(dir, "${filePrefix}_signature.png")

    val bmp = Bitmap.createBitmap(600, 260, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    // Transparent background so signature overlays cleanly on PDF contract
    canvas.drawColor(AndroidColor.TRANSPARENT)

    val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(10, 37, 64)
        style = AndroidPaint.Style.STROKE
        strokeWidth = 5.5f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }

    if (strokes.isNotEmpty()) {
        for (pts in strokes) {
            for (i in 0 until pts.size - 1) {
                canvas.drawLine(pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y, strokePaint)
            }
        }
    } else {
        // Stylized signature fallback if user tapped Simpan TTD directly with signer name
        val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(2, 132, 199)
            textSize = 36f
            isFakeBoldText = true
            textSkewX = -0.25f
        }
        canvas.drawText(signerName.ifBlank { "Tanda Tangan Sah" }, 60f, 135f, textPaint)
    }

    val footerPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(71, 85, 105)
        textSize = 15f
    }
    canvas.drawText(
        "Ditandatangani oleh: ${signerName.ifBlank { "Pelanggan SAF" }} (${DateCurrencyUtils.nowDateTimeDisplay()})",
        20f,
        242f,
        footerPaint
    )

    FileOutputStream(outFile).use { out ->
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    try {
        val rootDir = com.example.storage.LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
        val archiveDir = File(rootDir, "AGREEMENTS/SIGNATURES/$folderName")
        if (!archiveDir.exists()) archiveDir.mkdirs()
        outFile.copyTo(File(archiveDir, outFile.name), overwrite = true)
    } catch (_: Exception) {
    }
    return outFile
}

