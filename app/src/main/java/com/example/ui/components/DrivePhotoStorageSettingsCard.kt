package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.DrivePhotoCompressionConfig
import com.example.data.local.DrivePhotoMetadataEntity
import com.example.data.local.DriveStorageSummaryStats
import com.example.drive.DriveConnectionStatus
import com.example.drive.DriveFolderTreeSummary
import com.example.drive.DriveUploadProgressState
import com.example.drive.GoogleDrivePhotoCenterManager
import com.example.storage.LocalStorageAndBackupManager
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import java.io.File
import kotlin.math.roundToInt

@Composable
fun DrivePhotoStorageSettingsCard(
    driveConnectionStatus: DriveConnectionStatus,
    drivePhotoItems: List<DrivePhotoMetadataEntity>,
    driveStorageStats: DriveStorageSummaryStats,
    compressionConfig: DrivePhotoCompressionConfig,
    folderTreeSummary: DriveFolderTreeSummary,
    liveUploadProgress: DriveUploadProgressState,
    isSyncBusy: Boolean,
    openedPhotoFile: File? = null,
    openedPhotoMetadata: DrivePhotoMetadataEntity? = null,
    onConnectOrSwitchDrive: (String?) -> Unit,
    onSyncNow: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onSaveCompressionConfig: (DrivePhotoCompressionConfig) -> Unit,
    onEnsureDriveFolderHierarchy: () -> Unit,
    onRetrySinglePhotoUpload: (DrivePhotoMetadataEntity) -> Unit,
    onOpenPhotoFromDriveOrLocal: (DrivePhotoMetadataEntity) -> Unit,
    onDismissOpenedPhotoDialog: () -> Unit
) {
    var activeSectionIndex by remember { mutableIntStateOf(0) } // 0 = Semua / Ringkasan, 1..7 = A..G

    val sectionTabs = listOf(
        0 to "Semua Bagian",
        1 to "A. Koneksi Google Drive",
        2 to "B. Status Sinkronisasi",
        3 to "C. Pengaturan Kompresi Foto",
        4 to "D. Struktur Folder Google Drive",
        5 to "E. Riwayat Upload",
        6 to "F. Sinkronisasi Manual",
        7 to "G. Informasi Kapasitas Penyimpanan"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_pengaturan_penyimpanan_drive"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SeaBluePrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Pengaturan Penyimpanan Google Drive",
                        tint = SeaBluePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PENGATURAN PENYIMPANAN (GOOGLE DRIVE FOTO & DOKUMENTASI)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyDark
                    )
                    Text(
                        text = "Pusat penyimpanan otomatis seluruh foto Unit Investasi Freezer, KTP Pelanggan, Bukti Pembayaran & Serah Terima.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Filter chip bar for A..G sections
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                sectionTabs.forEach { (idx, label) ->
                    val selected = activeSectionIndex == idx
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) SeaBluePrimary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) SeaBluePrimary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .clickable { activeSectionIndex = idx }
                            .testTag("drive_storage_tab_$idx")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else NavyDark,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // A. KONEKSI GOOGLE DRIVE
            // =========================================================================
            if (activeSectionIndex == 0 || activeSectionIndex == 1) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (driveConnectionStatus.isConnected) Color(0xFFECFDF5) else Color(0xFFFFFBEB),
                    border = BorderStroke(
                        1.dp,
                        if (driveConnectionStatus.isConnected) StatusPaidGreen.copy(alpha = 0.45f)
                        else Color(0xFFF59E0B).copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("section_drive_connection")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (driveConnectionStatus.isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (driveConnectionStatus.isConnected) StatusPaidGreen else Color(0xFFD97706)
                                )
                                Text(
                                    text = "A. KONEKSI GOOGLE DRIVE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (driveConnectionStatus.isConnected) StatusPaidGreen else Color(0xFFD97706)
                            ) {
                                Text(
                                    text = if (driveConnectionStatus.isConnected) "TERHUBUNG" else "BELUM TERHUBUNG",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = SeaBluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Email Akun Google: ${
                                    driveConnectionStatus.accountEmail.ifBlank { "Belum ada akun Google Drive terhubung" }
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = NavyDark
                            )
                        }

                        val syncTimeText = driveConnectionStatus.lastDriveSyncAt
                        if (syncTimeText.isNotBlank()) {
                            Text(
                                text = "Sinkronisasi Terakhir: $syncTimeText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { onConnectOrSwitchDrive(driveConnectionStatus.accountEmail.takeIf { it.isNotBlank() }) },
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_drive_hubungkan")
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (driveConnectionStatus.isConnected) "Ganti Akun" else "Hubungkan",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FilledTonalButton(
                                onClick = onSyncNow,
                                enabled = !isSyncBusy,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_drive_sinkronisasi_sekarang")
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sinkronisasi Sekarang",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (driveConnectionStatus.isConnected) {
                            OutlinedButton(
                                onClick = onDisconnectDrive,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOverdueRed),
                                border = BorderStroke(1.dp, StatusOverdueRed.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_drive_putuskan_koneksi")
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Putuskan Koneksi",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // B. STATUS SINKRONISASI
            // =========================================================================
            if (activeSectionIndex == 0 || activeSectionIndex == 2) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("section_drive_sync_status")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = SeaBluePrimary)
                            Text(
                                text = "B. STATUS SINKRONISASI DOKUMENTASI",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        }

                        if (liveUploadProgress.isUploading || isSyncBusy) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = liveUploadProgress.statusText.ifBlank { "Sedang menyinkronkan dokumentasi ke Google Drive..." },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SeaBluePrimary
                                )
                                LinearProgressIndicator(
                                    progress = { (liveUploadProgress.progressPercent / 100f).coerceIn(0.05f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SyncStatBadge(
                                label = "BERHASIL",
                                count = driveStorageStats.uploadedSuccessCount,
                                color = StatusPaidGreen,
                                modifier = Modifier.weight(1f)
                            )
                            SyncStatBadge(
                                label = "MENUNGGU UPLOAD",
                                count = driveStorageStats.waitingUploadCount + driveStorageStats.needsSyncCount,
                                color = Color(0xFFD97706),
                                modifier = Modifier.weight(1f)
                            )
                            SyncStatBadge(
                                label = "GAGAL",
                                count = driveStorageStats.failedUploadCount,
                                color = StatusOverdueRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // C. PENGATURAN KOMPRESI FOTO & PRIVASI NIK
            // =========================================================================
            if (activeSectionIndex == 0 || activeSectionIndex == 3) {
                var stdQuality by remember(compressionConfig) {
                    mutableFloatStateOf(compressionConfig.standardQualityPercent.toFloat())
                }
                var ktpQuality by remember(compressionConfig) {
                    mutableFloatStateOf(compressionConfig.ktpAndDocumentQualityPercent.toFloat())
                }
                var maskNikToggle by remember(compressionConfig) {
                    mutableStateOf(compressionConfig.maskNikInFileName)
                }
                var autoCleanTempToggle by remember(compressionConfig) {
                    mutableStateOf(compressionConfig.autoDeleteTempAfterUpload)
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("section_drive_compression_settings")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.HighQuality, contentDescription = null, tint = SeaBluePrimary)
                            Text(
                                text = "C. PENGATURAN KOMPRESI FOTO & PENAMAAN OTOMATIS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        }

                        Text(
                            text = "Format JPEG • Resolusi Maksimal ${compressionConfig.maxResolutionPx}x${compressionConfig.maxResolutionPx} px • Target 200 KB–500 KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column {
                            Text(
                                text = "Kualitas Foto Umum (Unit, Pelanggan, Lokasi): ${stdQuality.roundToInt()}% (Standar 75–85%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = stdQuality,
                                onValueChange = { stdQuality = it },
                                valueRange = 75f..85f,
                                onValueChangeFinished = {
                                    onSaveCompressionConfig(
                                        compressionConfig.copy(
                                            standardQualityPercent = stdQuality.roundToInt(),
                                            ktpAndDocumentQualityPercent = ktpQuality.roundToInt(),
                                            maskNikInFileName = maskNikToggle,
                                            autoDeleteTempAfterUpload = autoCleanTempToggle
                                        )
                                    )
                                }
                            )
                        }

                        Column {
                            Text(
                                text = "Kualitas Foto KTP, Nomor Seri & Bukti Pembayaran: ${ktpQuality.roundToInt()}% (Ketajaman Tinggi 85–90%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = ktpQuality,
                                onValueChange = { ktpQuality = it },
                                valueRange = 85f..90f,
                                onValueChangeFinished = {
                                    onSaveCompressionConfig(
                                        compressionConfig.copy(
                                            standardQualityPercent = stdQuality.roundToInt(),
                                            ktpAndDocumentQualityPercent = ktpQuality.roundToInt(),
                                            maskNikInFileName = maskNikToggle,
                                            autoDeleteTempAfterUpload = autoCleanTempToggle
                                        )
                                    )
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Masking NIK pada Nama File Google Drive",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Contoh: BUDI_SANTOSO_3372XXXXXXXXXXXX_KTP_20261116.jpg (NIK lengkap tetap tersimpan di database)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = maskNikToggle,
                                onCheckedChange = { checked ->
                                    maskNikToggle = checked
                                    onSaveCompressionConfig(
                                        compressionConfig.copy(
                                            standardQualityPercent = stdQuality.roundToInt(),
                                            ktpAndDocumentQualityPercent = ktpQuality.roundToInt(),
                                            maskNikInFileName = checked,
                                            autoDeleteTempAfterUpload = autoCleanTempToggle
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("switch_mask_nik_drive_filename")
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hapus File Asli Sementara Setelah Upload Berhasil",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Hemat memori HP setelah Google Drive File ID tersimpan (tidak menghapus galeri asli pengguna)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoCleanTempToggle,
                                onCheckedChange = { checked ->
                                    autoCleanTempToggle = checked
                                    onSaveCompressionConfig(
                                        compressionConfig.copy(
                                            standardQualityPercent = stdQuality.roundToInt(),
                                            ktpAndDocumentQualityPercent = ktpQuality.roundToInt(),
                                            maskNikInFileName = maskNikToggle,
                                            autoDeleteTempAfterUpload = checked
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("switch_auto_delete_temp_after_upload")
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // D. STRUKTUR FOLDER GOOGLE DRIVE
            // =========================================================================
            if (activeSectionIndex == 0 || activeSectionIndex == 4) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0F9FF),
                    border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("section_drive_folder_structure")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = SeaBluePrimary)
                                Text(
                                    text = "D. STRUKTUR FOLDER GOOGLE DRIVE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            }
                            if (folderTreeSummary.isVerifiedOnDrive) {
                                Text(
                                    text = "Terverifikasi",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusPaidGreen
                                )
                            }
                        }

                        val treePreview = remember {
                            """
                            SEWAin Aja Freezer SOC
                            ├── 01. UNIT INVESTASI UNIT
                            │   ├── 2026
                            │   │   ├── Foto Unit
                            │   │   ├── Foto Nomor Seri
                            │   │   └── Dokumentasi Kondisi Unit
                            │   └── 2027
                            ├── 02. DOKUMENTASI SEWA
                            │   ├── 2026
                            │   │   ├── Foto KTP Pelanggan
                            │   │   ├── Foto Dokumentasi Pelanggan
                            │   │   ├── Foto Bukti Pembayaran
                            │   │   ├── Foto Serah Terima Unit
                            │   │   └── Foto Dokumentasi Lainnya
                            │   └── 2027
                            └── 03. BACKUP DOKUMENTASI
                            """.trimIndent()
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = treePreview,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = NavyDark,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        FilledTonalButton(
                            onClick = onEnsureDriveFolderHierarchy,
                            enabled = !isSyncBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_verify_create_drive_folders")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Buat / Verifikasi Struktur Folder di Google Drive",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // F. SINKRONISASI MANUAL & G. INFORMASI KAPASITAS PENYIMPANAN
            // =========================================================================
            if (activeSectionIndex == 0 || activeSectionIndex == 6 || activeSectionIndex == 7) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("section_drive_storage_capacity")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = SeaBluePrimary)
                            Text(
                                text = "F & G. SINKRONISASI MANUAL & INFORMASI KAPASITAS PENYIMPANAN",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        }

                        InfoCapacityRow("Total Foto Tersimpan", "${driveStorageStats.totalPhotosCount} Foto")
                        InfoCapacityRow(
                            "Total Ukuran Dokumentasi (Setelah Kompresi)",
                            LocalStorageAndBackupManager.formatFileSize(driveStorageStats.totalCompressedBytes)
                        )
                        InfoCapacityRow(
                            "Ukuran Asli Sebelum Kompresi",
                            LocalStorageAndBackupManager.formatFileSize(driveStorageStats.totalOriginalBytes)
                        )
                        InfoCapacityRow(
                            "Penghematan Ukuran Setelah Kompresi",
                            "${LocalStorageAndBackupManager.formatFileSize(driveStorageStats.savedBytesAfterCompression)} (${driveStorageStats.savedPercent}%)"
                        )
                        InfoCapacityRow("Total File Berhasil Diupload", "${driveStorageStats.uploadedSuccessCount} File")
                        InfoCapacityRow(
                            "Total File Menunggu Upload",
                            "${driveStorageStats.waitingUploadCount + driveStorageStats.needsSyncCount} File"
                        )
                        InfoCapacityRow("Total File Gagal", "${driveStorageStats.failedUploadCount} File")

                        Button(
                            onClick = onSyncNow,
                            enabled = !isSyncBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_manual_sync_all_photos")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sinkronisasi Manual Seluruh Dokumentasi ke Google Drive",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // E. RIWAYAT UPLOAD DOKUMENTASI FOTO
            // =========================================================================
            if (activeSectionIndex == 0 || activeSectionIndex == 5) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("section_drive_upload_history")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = SeaBluePrimary)
                                Text(
                                    text = "E. RIWAYAT UPLOAD DOKUMENTASI (${drivePhotoItems.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            }
                        }

                        if (drivePhotoItems.isEmpty()) {
                            Text(
                                text = "Belum ada riwayat upload dokumentasi foto. Foto yang diambil dari kamera atau diunggah dari galeri pada form Unit, Pelanggan, atau Transaksi akan otomatis masuk ke sini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            drivePhotoItems.take(25).forEach { item ->
                                DrivePhotoHistoryItemRow(
                                    item = item,
                                    onOpenPhoto = { onOpenPhotoFromDriveOrLocal(item) },
                                    onRetryUpload = { onRetrySinglePhotoUpload(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog to view a photo retrieved locally or downloaded on-demand from Google Drive
    if (openedPhotoFile != null && openedPhotoMetadata != null) {
        AlertDialog(
            onDismissRequest = onDismissOpenedPhotoDialog,
            title = {
                Text(
                    text = openedPhotoMetadata.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = openedPhotoFile,
                        contentDescription = openedPhotoMetadata.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        text = "Folder Drive: ${openedPhotoMetadata.driveRootSection}/${openedPhotoMetadata.driveYearFolder}/${openedPhotoMetadata.driveCategoryFolder}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (openedPhotoMetadata.driveFileId.isNotBlank()) {
                        Text(
                            text = "Google Drive File ID: ${openedPhotoMetadata.driveFileId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusPaidGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissOpenedPhotoDialog) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun SyncStatBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = NavyDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InfoCapacityRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = NavyDark
        )
    }
}

@Composable
private fun DrivePhotoHistoryItemRow(
    item: DrivePhotoMetadataEntity,
    onOpenPhoto: () -> Unit,
    onRetryUpload: () -> Unit
) {
    val statusColor = when (item.syncStatus) {
        GoogleDrivePhotoCenterManager.STATUS_BERHASIL -> StatusPaidGreen
        GoogleDrivePhotoCenterManager.STATUS_GAGAL -> StatusOverdueRed
        else -> Color(0xFFD97706)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = item.syncStatus,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "${item.driveRootSection} / ${item.driveYearFolder} / ${item.driveCategoryFolder}",
                style = MaterialTheme.typography.labelSmall,
                color = SeaBluePrimary
            )

            Text(
                text = "Asli: ${LocalStorageAndBackupManager.formatFileSize(item.originalSizeBytes)} → Kompresi: ${LocalStorageAndBackupManager.formatFileSize(item.compressedSizeBytes)} • ${item.uploadDate}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.driveFileId.isNotBlank()) {
                Text(
                    text = "Drive File ID: ${item.driveFileId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusPaidGreen
                )
            }

            if (item.lastError.isNotBlank() && item.syncStatus != GoogleDrivePhotoCenterManager.STATUS_BERHASIL) {
                Text(
                    text = item.lastError,
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusOverdueRed
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onOpenPhoto,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buka Foto", style = MaterialTheme.typography.labelSmall)
                }

                if (item.syncStatus != GoogleDrivePhotoCenterManager.STATUS_BERHASIL) {
                    FilledTonalButton(
                        onClick = onRetryUpload,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload Ulang", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
