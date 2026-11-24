package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloud.CloudStoragePhotoManager
import com.example.data.local.CloudPhotoJobEntity
import com.example.media.ImageQualityPreset
import com.example.media.ImageQualitySettings
import com.example.media.ImageStorageAnalytics
import com.example.media.PhotoCategory
import com.example.storage.LocalStorageAndBackupManager
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.appRadioButtonColors

/**
 * 8. PENGATURAN KUALITAS FOTO:
 * Pilihan Hemat (70% / 1280px), Standar (82% / 1600px Default), HD (90% / 2400px),
 * keterangan setiap pilihan, estimasi ukuran, info ruang penyimpanan, dan toggle kompresi otomatis.
 */
@Composable
fun ImageQualitySettingsCard(
    settings: ImageQualitySettings,
    analytics: ImageStorageAnalytics,
    onUpdateSettings: (ImageQualitySettings) -> Unit,
    onRefreshAnalytics: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_image_quality_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.HighQuality, contentDescription = null, tint = SeaBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Pengaturan Kualitas Foto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyDark
                        )
                        Text(
                            text = "Kompresi otomatis kamera & galeri sesuai kebutuhan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshAnalytics,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Perbarui info penyimpanan")
                }
            }

            // Pengaturan Kompresi Otomatis
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Compress, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Kompresi Otomatis",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Text(
                                text = if (settings.autoCompressionEnabled) {
                                    "Aktif — kamera & galeri otomatis dioptimalkan sebelum simpan/upload"
                                } else {
                                    "Nonaktif — menyimpan resolusi tinggi (KTP tetap dioptimalkan OCR)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.autoCompressionEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateSettings(settings.copy(autoCompressionEnabled = enabled))
                        },
                        modifier = Modifier.testTag("switch_auto_compression")
                    )
                }
            }

            // Pilihan Kualitas Default: Hemat / Standar / HD
            Text(
                text = "PILIHAN KUALITAS DEFAULT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = SeaBluePrimary
            )

            ImageQualityPreset.entries.forEachIndexed { idx, preset ->
                val isSelected = settings.defaultPreset == preset
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) SeaBluePrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) SeaBluePrimary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUpdateSettings(settings.copy(defaultPreset = preset))
                        }
                        .testTag("quality_preset_${preset.key}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            colors = appRadioButtonColors(),
                            onClick = {
                                onUpdateSettings(settings.copy(defaultPreset = preset))
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${preset.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) SeaBluePrimary else NavyDark
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) SeaBluePrimary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = preset.estimatedSizeRange,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = preset.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Profil Khusus Dokumen
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0F9FF),
                border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Profil Otomatis Berdasarkan Kategori Dokumen",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Text(
                        text = "• ${PhotoCategory.KTP.displayLabel}: Maks ${PhotoCategory.KTP.maxDimensionPx} px • JPEG 88–92% (OCR tetap tajam)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• ${PhotoCategory.FREEZER.displayLabel}: Maks ${PhotoCategory.FREEZER.maxDimensionPx} px • JPEG ${PhotoCategory.FREEZER.defaultQuality}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• ${PhotoCategory.PAYMENT_PROOF.displayLabel}: Maks ${PhotoCategory.PAYMENT_PROOF.maxDimensionPx} px • JPEG ${PhotoCategory.PAYMENT_PROOF.defaultQuality}% (Nominal jelas)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Pelanggan, Lokasi & Umum: Maks 1280 px • JPEG 80% + Thumbnail WebP ≤ 300 px",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Informasi Ruang Penyimpanan Singkat
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Informasi Ruang Penyimpanan Aktual",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Text(
                        text = "Terpakai Lokal: ${LocalStorageAndBackupManager.formatFileSize(analytics.totalLocalStorageBytes)} • Tersedia Perangkat: ${LocalStorageAndBackupManager.formatFileSize(analytics.availableDeviceBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Penghematan Kompresi: ${LocalStorageAndBackupManager.formatFileSize(analytics.estimatedCompressionSavingsBytes)} (${analytics.totalPhotoCount} dokumentasi)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusPaidGreen
                    )
                }
            }
        }
    }
}

/**
 * 5. PENGELOLAAN PENYIMPANAN & 9. IMAGE STORAGE ANALYTICS + CLOUD PHOTO STATUS
 */
@Composable
fun StorageManagementAndAnalyticsCard(
    analytics: ImageStorageAnalytics,
    cloudSyncStatus: String,
    cloudPhotoJobs: List<CloudPhotoJobEntity>,
    onRefreshAnalytics: () -> Unit,
    onConfirmCleanCache: () -> Unit,
    onRetryAllFailedPhotos: () -> Unit,
    onRetrySinglePhoto: (Long) -> Unit
) {
    var showCleanConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_storage_analytics_and_cleaner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = SeaBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Pengelolaan Penyimpanan & Analitik",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyDark
                        )
                        Text(
                            text = "Status Sinkronisasi: $cloudSyncStatus",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshAnalytics,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Hitung ulang statistik penyimpanan")
                }
            }

            // Section 9: Dashboard Penyimpanan Aktual
            Text(
                text = "DASHBOARD ANALITIK PENYIMPANAN (AKTUAL)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = SeaBluePrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsMetricTile(
                    label = "TOTAL FOTO",
                    value = "${analytics.totalPhotoCount} Foto",
                    icon = Icons.Default.PhotoLibrary,
                    tint = SeaBluePrimary,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMetricTile(
                    label = "PENYIMPANAN LOKAL",
                    value = LocalStorageAndBackupManager.formatFileSize(analytics.totalLocalStorageBytes),
                    icon = Icons.Default.Storage,
                    tint = NavyDark,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsMetricTile(
                    label = "PENYIMPANAN CLOUD",
                    value = LocalStorageAndBackupManager.formatFileSize(analytics.cloudStorageBytes),
                    icon = Icons.Default.CloudDone,
                    tint = StatusPaidGreen,
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMetricTile(
                    label = "PENGHEMATAN",
                    value = LocalStorageAndBackupManager.formatFileSize(analytics.estimatedCompressionSavingsBytes),
                    icon = Icons.Default.Savings,
                    tint = Color(0xFF0D9488),
                    modifier = Modifier.weight(1f)
                )
            }

            AnalyticsMetricTile(
                label = "TOTAL CACHE SEMENTARA",
                value = "${LocalStorageAndBackupManager.formatFileSize(analytics.totalTempCacheBytes)} (${analytics.appCacheFileCount} file sementara)",
                icon = Icons.Default.CleaningServices,
                tint = Color(0xFFD97706),
                modifier = Modifier.fillMaxWidth()
            )

            // Section 5: Rincian Penyimpanan Lengkap
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Rincian Kapasitas Aplikasi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    StorageBreakdownRow("• Cache aplikasi", "${LocalStorageAndBackupManager.formatFileSize(analytics.appCacheBytes)} (${analytics.appCacheFileCount} file)")
                    StorageBreakdownRow("• Thumbnail (WebP ≤300px)", "${LocalStorageAndBackupManager.formatFileSize(analytics.thumbnailBytes)} (${analytics.thumbnailFileCount} file)")
                    StorageBreakdownRow("• Foto offline utama", "${LocalStorageAndBackupManager.formatFileSize(analytics.offlineLocalPhotosBytes)} (${analytics.offlineLocalPhotosCount} foto)")
                    StorageBreakdownRow("• Menunggu upload", "${analytics.pendingUploadPhotosCount} foto (${LocalStorageAndBackupManager.formatFileSize(analytics.pendingUploadPhotosBytes)})")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    StorageBreakdownRow("Total penyimpanan lokal", LocalStorageAndBackupManager.formatFileSize(analytics.totalLocalStorageBytes), bold = true)
                    StorageBreakdownRow("Total penyimpanan Cloud", "${LocalStorageAndBackupManager.formatFileSize(analytics.cloudStorageBytes)} (${analytics.uploadedCloudPhotosCount} foto)", bold = true)
                }
            }

            // Tombol BERSIHKAN CACHE (dengan konfirmasi wajib)
            Button(
                onClick = { showCleanConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_clean_storage_cache")
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("BERSIHKAN CACHE", fontWeight = FontWeight.Bold)
            }

            // Antrean Upload Cloud & Offline Image Management
            if (cloudPhotoJobs.isNotEmpty()) {
                HorizontalDivider()
                val failedCount = cloudPhotoJobs.count { it.status == CloudStoragePhotoManager.STATUS_FAILED }
                val pendingCount = cloudPhotoJobs.count {
                    it.status == CloudStoragePhotoManager.STATUS_QUEUED ||
                        it.status == CloudStoragePhotoManager.STATUS_UPLOADING
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Antrean Upload Cloud (${cloudPhotoJobs.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Text(
                        text = "Antrean: $pendingCount • Gagal: $failedCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failedCount > 0) StatusOverdueRed else SeaBluePrimary
                    )
                }

                if (failedCount > 0) {
                    OutlinedButton(
                        onClick = onRetryAllFailedPhotos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_retry_all_failed_photos")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("UPLOAD ULANG ($failedCount Gagal)", fontWeight = FontWeight.Bold)
                    }
                }

                cloudPhotoJobs.take(8).forEach { job ->
                    CloudPhotoJobStatusRow(
                        job = job,
                        onRetryClick = { onRetrySinglePhoto(job.id) }
                    )
                }
            }
        }
    }

    if (showCleanConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCleanConfirmDialog = false },
            modifier = Modifier.testTag("dialog_confirm_clean_cache"),
            title = {
                Text(
                    text = "Konfirmasi Bersihkan Cache",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Tindakan ini akan membersihkan cache sementara (${LocalStorageAndBackupManager.formatFileSize(analytics.totalTempCacheBytes)}), thumbnail sementara, dan file temporary hasil unggahan yang sudah tersimpan di Cloud.\n\n" +
                        "DIJAMIN AMAN: Dokumentasi utama, foto yang belum diunggah (${analytics.pendingUploadPhotosCount} foto), database pelanggan, dan data transaksi TIDAK akan dihapus."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanConfirmDialog = false
                        onConfirmCleanCache()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                    modifier = Modifier.testTag("btn_confirm_clean_cache")
                ) {
                    Text("Bersihkan Sekarang", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCleanConfirmDialog = false },
                    modifier = Modifier.testTag("btn_cancel_clean_cache")
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun AnalyticsMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = tint
                )
            }
        }
    }
}

@Composable
private fun StorageBreakdownRow(
    label: String,
    value: String,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) NavyDark else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = NavyDark
        )
    }
}

@Composable
fun CloudPhotoJobStatusRow(
    job: CloudPhotoJobEntity,
    onRetryClick: () -> Unit
) {
    val (statusLabel, statusColor, statusIcon) = when (job.status) {
        CloudStoragePhotoManager.STATUS_UPLOADED -> Triple("BERHASIL", StatusPaidGreen, Icons.Default.CheckCircle)
        CloudStoragePhotoManager.STATUS_UPLOADING -> Triple("MENGUNGGAH ${job.progressPercent}%", SeaBluePrimary, Icons.Default.CloudUpload)
        CloudStoragePhotoManager.STATUS_FAILED -> Triple("GAGAL", StatusOverdueRed, Icons.Default.ErrorOutline)
        else -> Triple("MENUNGGU UPLOAD", Color(0xFFD97706), Icons.Default.CloudQueue)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(statusIcon, contentDescription = statusLabel, tint = statusColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "${job.ownerType} #${job.ownerLocalId} • ${job.photoSlot}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "${LocalStorageAndBackupManager.formatFileSize(job.bytesTotal)} • ${job.storagePath.substringAfterLast('/')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (job.status == CloudStoragePhotoManager.STATUS_UPLOADING || job.status == CloudStoragePhotoManager.STATUS_QUEUED) {
                LinearProgressIndicator(
                    progress = { (job.progressPercent.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (job.status == CloudStoragePhotoManager.STATUS_FAILED) {
                Text(
                    text = job.errorMessage.ifBlank { "Upload gagal karena gangguan jaringan." },
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusOverdueRed
                )
                OutlinedButton(
                    onClick = onRetryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_retry_job_${job.id}")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Ulang", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
