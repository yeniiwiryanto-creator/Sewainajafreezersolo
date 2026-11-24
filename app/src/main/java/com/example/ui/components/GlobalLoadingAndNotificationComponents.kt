package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.AppAppearanceConfig
import com.example.ui.theme.BackgroundPositionMode
import com.example.ui.theme.BackgroundScaleMode
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.TextDescription
import com.example.ui.theme.TextPageTitle
import com.example.viewmodel.AppLoadingState
import com.example.viewmodel.AppNotificationEvent
import com.example.viewmodel.OperationNotificationType
import java.io.File
import kotlin.math.roundToInt

/**
 * Indikator loading non-blocking yang terhubung langsung dengan status proses nyata di ViewModel
 * (simpan data, proses transaksi, sinkronisasi Google Drive, unggah foto).
 */
@Composable
fun GlobalOperationLoadingBanner(
    loadingState: AppLoadingState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = loadingState.isLoading && !loadingState.isBlocking,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = NavyDark,
            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("global_loading_banner")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF38BDF8),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = loadingState.message.ifBlank { "Sedang memproses..." },
                        style = MaterialTheme.typography.bodySmall,
                        color = PureWhite,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (loadingState.progress != null) {
                        Text(
                            text = "${(loadingState.progress.coerceIn(0f, 1f) * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (loadingState.progress != null) {
                    LinearProgressIndicator(
                        progress = { loadingState.progress.coerceIn(0f, 1f) },
                        color = Color(0xFF38BDF8),
                        trackColor = PureWhite.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        color = Color(0xFF38BDF8),
                        trackColor = PureWhite.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Notifikasi terpusat untuk status BERHASIL / GAGAL / INFO dengan tombol "Coba Lagi" bila gagal.
 */
@Composable
fun GlobalOperationNotificationBanner(
    event: AppNotificationEvent?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = event != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (event != null) {
            val (containerColor, icon) = when (event.type) {
                OperationNotificationType.SUCCESS -> StatusPaidGreen to Icons.Default.CheckCircle
                OperationNotificationType.ERROR -> StatusOverdueRed to Icons.Default.ErrorOutline
                OperationNotificationType.INFO -> SeaBluePrimary to Icons.Default.Info
            }
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_notification_banner_${event.type.name.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = event.type.name,
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                        if (event.detail.isNotBlank()) {
                            Text(
                                text = event.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite.copy(alpha = 0.92f)
                            )
                        }
                    }
                    if (event.type == OperationNotificationType.ERROR && event.onRetry != null) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                event.onRetry.invoke()
                            },
                            modifier = Modifier.testTag("btn_notification_retry")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Coba Lagi",
                                tint = PureWhite,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = event.retryLabel ?: "Coba Lagi",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Notifikasi",
                            tint = PureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overlay progress khusus proses kritis yang memerlukan penguncian sementara (seperti Restore Database).
 */
@Composable
fun BlockingOperationProgressOverlay(
    loadingState: AppLoadingState
) {
    if (!loadingState.isLoading || !loadingState.isBlocking) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .testTag("blocking_loading_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.35f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                CircularProgressIndicator(
                    color = SeaBluePrimary,
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = loadingState.message.ifBlank { "Memulihkan database, mohon tunggu..." },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPageTitle
                )
                val pct = loadingState.progress
                if (pct != null) {
                    LinearProgressIndicator(
                        progress = { pct.coerceIn(0f, 1f) },
                        color = SeaBluePrimary,
                        trackColor = Color(0xFFE2E8F0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                    Text(
                        text = "${(pct.coerceIn(0f, 1f) * 100f).roundToInt()}% selesai",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDescription,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    LinearProgressIndicator(
                        color = SeaBluePrimary,
                        trackColor = Color(0xFFE2E8F0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Layer Background Gambar Kustom dengan kontrol posisi, skala, transparansi, blur (API 31+),
 * serta scrim otomatis agar keterbacaan teks & kartu tetap tinggi.
 */
@Composable
fun AppCustomBackgroundLayer(
    appearance: AppAppearanceConfig,
    activeTabIndex: Int,
    isLoginScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shouldShow = appearance.shouldShowBackgroundOnTab(activeTabIndex, isLoginScreen)
    if (!shouldShow) return
    val imageFile = remember(appearance.backgroundImagePath) { File(appearance.backgroundImagePath) }
    if (!imageFile.exists()) return

    val contentScale = when (appearance.backgroundScaleMode) {
        BackgroundScaleMode.CROP -> ContentScale.Crop
        BackgroundScaleMode.FIT -> ContentScale.Fit
        BackgroundScaleMode.FILL_WIDTH -> ContentScale.FillWidth
    }
    val alignment = when (appearance.backgroundPositionMode) {
        BackgroundPositionMode.CENTER -> Alignment.Center
        BackgroundPositionMode.TOP -> Alignment.TopCenter
        BackgroundPositionMode.BOTTOM -> Alignment.BottomCenter
    }

    val blurMod = if (appearance.backgroundBlurDp > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(appearance.backgroundBlurDp.dp)
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = imageFile,
            contentDescription = null,
            contentScale = contentScale,
            alignment = alignment,
            modifier = Modifier
                .fillMaxSize()
                .then(blurMod)
                .alpha(appearance.backgroundOpacity.coerceIn(0.05f, 0.60f))
        )
        // Soft readability veil so dark text and cards remain clearly legible
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appearance.backgroundColor.copy(alpha = (1f - appearance.backgroundOpacity).coerceIn(0.40f, 0.92f)))
        )
    }
}
