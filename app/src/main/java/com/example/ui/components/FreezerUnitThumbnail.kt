package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.example.cloud.CloudStoragePhotoManager
import com.example.media.SmartImageProcessor
import com.example.storage.LocalStorageAndBackupManager
import java.io.File

/**
 * Displays a crisp thumbnail of a Freezer Unit (~300px lazy-loaded).
 * - Prefers pre-generated ~300px thumbnail or subsamples via Coil so lists never load full-resolution images.
 * - Resolves local staged copies for `gdrive://` references and falls back cleanly to the custom Freezer vector
 *   illustration if a remote URL is missing (e.g., `Object does not exist at location` / HTTP 404).
 */
@Composable
fun FreezerUnitThumbnail(
    photoUriOrPath: String,
    jenisUnit: String,
    kapasitasLiter: Int = 200,
    size: Dp = 68.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val resolvedModel: Any? = remember(photoUriOrPath) {
        val raw = photoUriOrPath.trim()
        if (raw.isBlank()) {
            null
        } else if (raw.startsWith("gdrive://", ignoreCase = true)) {
            val rel = raw.removePrefix("gdrive://${CloudStoragePhotoManager.DRIVE_BACKUP_ROOT}/").trimStart('/')
            val staged = runCatching {
                val root = LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
                File(root, "DRIVE_STAGE/$rel").takeIf { it.exists() && it.length() > 0L }
            }.getOrNull()
            staged
        } else if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            raw
        } else {
            val thumbPath = SmartImageProcessor.getExistingThumbnailPathOrNull(context, raw)
            val candidatePath = thumbPath ?: raw
            val file = File(candidatePath)
            when {
                file.exists() && file.length() > 0L -> file
                raw.startsWith("content://", ignoreCase = true) || raw.startsWith("file://", ignoreCase = true) -> raw
                else -> null
            }
        }
    }
    var imageLoadFailed by remember(resolvedModel) { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0A2540), Color(0xFF0284C7))
                )
            )
            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), shape)
    ) {
        if (resolvedModel != null && !imageLoadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedModel)
                    .size(300, 300)
                    .scale(Scale.FILL)
                    .crossfade(true)
                    .build(),
                onError = { imageLoadFailed = true },
                contentDescription = "Thumbnail Unit $jenisUnit",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                val w = this.size.width
                val h = this.size.height

                when {
                    jenisUnit.contains("Showcase", ignoreCase = true) -> {
                        // Tall glass-door Showcase Cooler illustration
                        val boxW = w * 0.62f
                        val boxH = h * 0.88f
                        val left = (w - boxW) / 2f
                        val top = (h - boxH) / 2f
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color(0xFFE0F2FE),
                            topLeft = Offset(left + 3.dp.toPx(), top + 6.dp.toPx()),
                            size = Size(boxW - 6.dp.toPx(), boxH - 12.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                        // 3 shelves
                        for (i in 1..3) {
                            val sy = top + (boxH * i / 4f)
                            drawLine(
                                color = Color(0xFF0284C7),
                                start = Offset(left + 4.dp.toPx(), sy),
                                end = Offset(left + boxW - 4.dp.toPx(), sy),
                                strokeWidth = 2f
                            )
                        }
                    }

                    jenisUnit.contains("ASI", ignoreCase = true) -> {
                        // Upright 4-drawer Freezer ASI illustration
                        val boxW = w * 0.68f
                        val boxH = h * 0.82f
                        val left = (w - boxW) / 2f
                        val top = (h - boxH) / 2f
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                        // Drawer lines
                        for (i in 1..3) {
                            val dy = top + (boxH * i / 4f)
                            drawLine(
                                color = Color(0xFF38BDF8),
                                start = Offset(left + 3.dp.toPx(), dy),
                                end = Offset(left + boxW - 3.dp.toPx(), dy),
                                strokeWidth = 2f
                            )
                        }
                    }

                    else -> {
                        // Horizontal Chest Freezer illustration
                        val boxW = w * 0.88f
                        val boxH = h * 0.62f
                        val left = (w - boxW) / 2f
                        val top = (h - boxH) / 2f
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                        // Lid divider
                        drawLine(
                            color = Color(0xFF0284C7),
                            start = Offset(left, top + boxH * 0.28f),
                            end = Offset(left + boxW, top + boxH * 0.28f),
                            strokeWidth = 2.5f
                        )
                        // Green LED indicator
                        drawCircle(
                            color = Color(0xFF10B981),
                            radius = 2.5.dp.toPx(),
                            center = Offset(left + boxW * 0.82f, top + boxH * 0.78f)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AcUnit,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "${kapasitasLiter}L",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF0A2540),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
