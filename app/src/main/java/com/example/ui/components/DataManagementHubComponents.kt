package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.backup.RestoreMode
import com.example.backup.RestorePreviewSummary
import com.example.data.local.BackupAuditLogEntity
import com.example.data.local.BackupScheduleConfig
import com.example.data.local.LocalBackupEntity
import com.example.drive.DriveBackupFileItem
import com.example.drive.DriveConnectionStatus
import com.example.sheets.DuplicateImportPolicy
import com.example.sheets.ExcelTemplateAndReportExporter.TemplateType
import com.example.sheets.SmartImportExecutionReport
import com.example.sheets.SmartImportPreviewState
import com.example.sheets.SmartImportTarget
import com.example.storage.LocalStorageAndBackupManager
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen

enum class DataManagementSubmenu(
    val numberLabel: String,
    val title: String,
    val icon: ImageVector
) {
    BACKUP_DRIVE("1", "Backup Google Drive", Icons.Default.CloudUpload),
    BACKUP_HISTORY("2", "Riwayat Backup", Icons.Default.History),
    RESTORE_DATA("3", "Restore Data", Icons.Default.Restore),
    IMPORT_DATA("4", "Import Data", Icons.Default.FileUpload),
    EXPORT_DATA("5", "Export Data", Icons.Default.Download),
    DOWNLOAD_TEMPLATE("6", "Download Template", Icons.Default.TableView),
    AUTO_BACKUP_SETTINGS("7", "Pengaturan Backup Otomatis", Icons.Default.Schedule)
}

@Composable
fun DataManagementHubCard(
    driveStatus: DriveConnectionStatus,
    driveBackups: List<DriveBackupFileItem>,
    localBackups: List<LocalBackupEntity>,
    backupScheduleConfig: BackupScheduleConfig,
    auditLogs: List<BackupAuditLogEntity>,
    restorePreview: RestorePreviewSummary?,
    lastRestoreMessage: String?,
    isBusy: Boolean,
    smartImportPreview: SmartImportPreviewState?,
    smartImportReport: SmartImportExecutionReport?,
    isImporting: Boolean,
    onConnectOrSwitchDrive: (String?) -> Unit,
    onDisconnectDrive: () -> Unit,
    onRunDriveBackupNow: () -> Unit,
    onRefreshDriveBackups: () -> Unit,
    onDownloadDriveBackupToUri: (DriveBackupFileItem, Uri) -> Unit,
    onDeleteDriveBackup: (DriveBackupFileItem) -> Unit,
    onDeleteLocalBackup: (LocalBackupEntity) -> Unit,
    onBackupToSafUri: (Uri, String) -> Unit,
    onInspectDriveBackupForRestore: (DriveBackupFileItem) -> Unit,
    onInspectLocalBackupForRestore: (LocalBackupEntity) -> Unit,
    onInspectExternalZipForRestore: (Uri) -> Unit,
    onExecuteRestore: (RestoreMode) -> Unit,
    onDismissRestorePreview: () -> Unit,
    onSaveScheduleConfig: (BackupScheduleConfig) -> Unit,
    onPickImportFileUri: (Uri) -> Unit,
    onChangeImportTargetAndSheet: (SmartImportTarget, String?) -> Unit,
    onUpdateImportColumnMapping: (Int, String?) -> Unit,
    onChangeImportDuplicatePolicy: (DuplicateImportPolicy) -> Unit,
    onExecuteSmartImport: () -> Unit,
    onDismissSmartImportPreview: () -> Unit,
    onExportErrorReportXlsx: (Uri) -> Unit,
    onExportErrorReportCsv: (Uri) -> Unit,
    onExportFullXlsx: () -> Unit,
    onExportCategoryCsv: (String) -> Unit,
    onExportCustomersXlsxUri: (Uri) -> Unit,
    onExportTransactionsPdfUri: (Uri) -> Unit,
    onExportTemplateToUri: (TemplateType, Uri) -> Unit
) {
    var activeSubmenu by remember { mutableStateOf(DataManagementSubmenu.BACKUP_DRIVE) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var selectedRestoreMode by remember { mutableStateOf(RestoreMode.FULL_RESTORE) }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }
    var pendingDownloadDriveItem by remember { mutableStateOf<DriveBackupFileItem?>(null) }
    var pendingTemplateType by remember { mutableStateOf(TemplateType.PELANGGAN) }

    val restoreZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) onInspectExternalZipForRestore(uri)
    }

    val saveSafBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) onBackupToSafUri(uri, "FULL_BACKUP")
    }

    val saveDriveDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val targetItem = pendingDownloadDriveItem
        pendingDownloadDriveItem = null
        if (uri != null && targetItem != null) onDownloadDriveBackupToUri(targetItem, uri)
    }

    val saveTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) onExportTemplateToUri(pendingTemplateType, uri)
    }

    val exportCustXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? -> if (uri != null) onExportCustomersXlsxUri(uri) }

    val exportTxPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? -> if (uri != null) onExportTransactionsPdfUri(uri) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_backup_restore_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = SeaBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "7. PENGATURAN DATA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyDark
                    )
                    Text(
                        text = "Google Drive Backup, Restore Rollback-Safe, Smart Import & Template",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // 7 Clean Submenu Navigation Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                DataManagementSubmenu.entries.forEach { sub ->
                    val selected = activeSubmenu == sub
                    FilterChip(
                        selected = selected,
                        onClick = { activeSubmenu = sub },
                        leadingIcon = {
                            Icon(sub.icon, contentDescription = sub.title, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(
                                text = "${sub.numberLabel}. ${sub.title}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("data_submenu_${sub.numberLabel}")
                    )
                }
            }

            if (!lastRestoreMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE0F2FE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lastRestoreMessage,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyDark,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            HorizontalDivider()

            when (activeSubmenu) {
                DataManagementSubmenu.BACKUP_DRIVE -> {
                    DriveConnectionAndBackupSection(
                        driveStatus = driveStatus,
                        isBusy = isBusy,
                        onConnectDrive = { onConnectOrSwitchDrive(null) },
                        onDisconnectClick = { showDisconnectDialog = true },
                        onBackupDriveNow = onRunDriveBackupNow,
                        onBackupSafNow = {
                            saveSafBackupLauncher.launch("SEWAIN_BACKUP_${System.currentTimeMillis()}.zip")
                        }
                    )
                }

                DataManagementSubmenu.BACKUP_HISTORY -> {
                    BackupHistorySection(
                        driveBackups = driveBackups,
                        localBackups = localBackups,
                        onRefreshDrive = onRefreshDriveBackups,
                        onDownloadDriveItem = { item ->
                            pendingDownloadDriveItem = item
                            saveDriveDownloadLauncher.launch(item.name)
                        },
                        onRestoreDriveItem = { item ->
                            onInspectDriveBackupForRestore(item)
                            activeSubmenu = DataManagementSubmenu.RESTORE_DATA
                        },
                        onDeleteDriveItem = onDeleteDriveBackup,
                        onRestoreLocalItem = { item ->
                            onInspectLocalBackupForRestore(item)
                            activeSubmenu = DataManagementSubmenu.RESTORE_DATA
                        },
                        onDeleteLocalItem = onDeleteLocalBackup
                    )
                }

                DataManagementSubmenu.RESTORE_DATA -> {
                    RestoreDataSection(
                        restorePreview = restorePreview,
                        selectedMode = selectedRestoreMode,
                        isBusy = isBusy,
                        onPickZipFromPhone = {
                            restoreZipLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/json",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        onSelectMode = { selectedRestoreMode = it },
                        onConfirmRestoreClick = { showConfirmRestoreDialog = true },
                        onDismissPreview = onDismissRestorePreview
                    )
                }

                DataManagementSubmenu.IMPORT_DATA -> {
                    SmartImportSubmenuPanel(
                        preview = smartImportPreview,
                        report = smartImportReport,
                        isImporting = isImporting,
                        onPickImportFileUri = onPickImportFileUri,
                        onChangeTargetAndSheet = onChangeImportTargetAndSheet,
                        onUpdateColumnMapping = onUpdateImportColumnMapping,
                        onChangeDuplicatePolicy = onChangeImportDuplicatePolicy,
                        onExecuteImport = onExecuteSmartImport,
                        onDismissPreview = onDismissSmartImportPreview,
                        onExportErrorReportXlsx = onExportErrorReportXlsx,
                        onExportErrorReportCsv = onExportErrorReportCsv
                    )
                }

                DataManagementSubmenu.EXPORT_DATA -> {
                    ExportDataSection(
                        onExportFullZip = {
                            saveSafBackupLauncher.launch("SEWAIN_EXPORT_FULL_${System.currentTimeMillis()}.zip")
                        },
                        onExportFullXlsx = onExportFullXlsx,
                        onExportCustXlsx = {
                            exportCustXlsxLauncher.launch("Data_Pelanggan_SAF_${System.currentTimeMillis()}.xlsx")
                        },
                        onExportTxPdf = {
                            exportTxPdfLauncher.launch("Laporan_Transaksi_SAF_${System.currentTimeMillis()}.pdf")
                        },
                        onExportCategoryCsv = onExportCategoryCsv
                    )
                }

                DataManagementSubmenu.DOWNLOAD_TEMPLATE -> {
                    DownloadTemplateSection(
                        onDownloadTemplate = { tplType ->
                            pendingTemplateType = tplType
                            saveTemplateLauncher.launch(tplType.fileName)
                        }
                    )
                }

                DataManagementSubmenu.AUTO_BACKUP_SETTINGS -> {
                    AutoBackupScheduleAndAuditSection(
                        config = backupScheduleConfig,
                        auditLogs = auditLogs,
                        onSaveConfig = onSaveScheduleConfig
                    )
                }
            }
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Putuskan Koneksi Google Drive?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Izin akses Google Drive untuk backup otomatis akan dicabut. File backup yang sudah tersimpan di Drive tetap aman.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        onDisconnectDrive()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOverdueRed)
                ) { Text("Putuskan Koneksi") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text("Batal") }
            }
        )
    }

    if (showConfirmRestoreDialog && restorePreview != null) {
        AlertDialog(
            onDismissRequest = { showConfirmRestoreDialog = false },
            title = { Text("Konfirmasi Restore Data", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Mode: ${selectedRestoreMode.label}\n" +
                        "Pelanggan: ${restorePreview.customerCount}, Unit: ${restorePreview.unitCount}, " +
                        "Transaksi: ${restorePreview.transactionCount}, Foto: ${restorePreview.photoCount}.\n\n" +
                        "Aplikasi akan membuat snapshot pengaman otomatis sebelum memproses restore sehingga data bisa di-rollback jika terjadi kesalahan."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmRestoreDialog = false
                        onExecuteRestore(selectedRestoreMode)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusPaidGreen)
                ) { Text("Jalankan Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRestoreDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun DriveConnectionAndBackupSection(
    driveStatus: DriveConnectionStatus,
    isBusy: Boolean,
    onConnectDrive: () -> Unit,
    onDisconnectClick: () -> Unit,
    onBackupDriveNow: () -> Unit,
    onBackupSafNow: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("panel_drive_backup")) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (driveStatus.isConnected) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
            border = BorderStroke(
                1.dp,
                if (driveStatus.isConnected) StatusPaidGreen.copy(alpha = 0.4f) else Color(0xFFCBD5E1)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (driveStatus.isConnected) StatusPaidGreen.copy(alpha = 0.15f) else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    if (driveStatus.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = driveStatus.photoUrl,
                            contentDescription = "Foto Akun Google Drive",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (driveStatus.isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = "Status Koneksi Drive",
                            tint = if (driveStatus.isConnected) StatusPaidGreen else Color(0xFF64748B)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (driveStatus.isConnected) "Terhubung ke Google Drive" else "Google Drive Belum Terhubung",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Text(
                        text = if (driveStatus.isConnected) {
                            "${driveStatus.displayName} (${driveStatus.accountEmail.ifBlank { "Akun Google" }})"
                        } else {
                            "Gunakan OAuth 2.0 scope minimal (drive.appdata & drive.file) yang aman."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569)
                    )
                    if (driveStatus.lastDriveSyncAt.isNotBlank()) {
                        Text(
                            text = "Sinkronisasi terakhir: ${driveStatus.lastDriveSyncAt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF059669)
                        )
                    }
                }
            }
        }

        if (driveStatus.hasPendingOfflineUpload) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Antrian offline aktif: backup terbaru sudah tersimpan aman secara lokal & akan diupload otomatis saat online.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF92400E),
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = onConnectDrive,
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_connect_google_drive")
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (driveStatus.isConnected) "Ganti Akun Drive" else "Hubungkan Drive", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            if (driveStatus.isConnected) {
                OutlinedButton(
                    onClick = onDisconnectClick,
                    enabled = !isBusy,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_disconnect_google_drive")
                ) {
                    Text("Putuskan", color = StatusOverdueRed, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Button(
            onClick = onBackupDriveNow,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_backup_now")
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Backup Terenkripsi ke Google Drive Sekarang", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onBackupSafNow,
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simpan Arsip Backup (.ZIP) ke Penyimpanan HP")
        }
    }
}

@Composable
private fun BackupHistorySection(
    driveBackups: List<DriveBackupFileItem>,
    localBackups: List<LocalBackupEntity>,
    onRefreshDrive: () -> Unit,
    onDownloadDriveItem: (DriveBackupFileItem) -> Unit,
    onRestoreDriveItem: (DriveBackupFileItem) -> Unit,
    onDeleteDriveItem: (DriveBackupFileItem) -> Unit,
    onRestoreLocalItem: (LocalBackupEntity) -> Unit,
    onDeleteLocalItem: (LocalBackupEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("panel_backup_history")) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "2. Riwayat Backup (Maks. 30 Versi)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NavyDark
            )
            OutlinedButton(
                onClick = onRefreshDrive,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_refresh_drive_backups")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang Drive", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Refresh Drive", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (driveBackups.isNotEmpty()) {
            Text("Backup di Google Drive (${driveBackups.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SeaBluePrimary)
            driveBackups.take(30).forEach { item ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF0F9FF),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NavyDark)
                        Text(
                            text = "${LocalStorageAndBackupManager.formatFileSize(item.sizeBytes)} • Pelanggan: ${item.customerCount}, Unit: ${item.unitCount}, Tx: ${item.transactionCount}, Foto: ${item.photoCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF475569)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = { onDownloadDriveItem(item) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(onClick = { onRestoreDriveItem(item) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { onDeleteDriveItem(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus Backup Drive", tint = StatusOverdueRed)
                            }
                        }
                    }
                }
            }
        }

        Text("Backup Lokal Tersimpan (${localBackups.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NavyDark)
        if (localBackups.isEmpty()) {
            Text("Belum ada riwayat backup lokal.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            localBackups.take(30).forEach { bkp ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bkp.backupName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${bkp.createdAt} • ${LocalStorageAndBackupManager.formatFileSize(bkp.backupSize)} • ${bkp.locationType} • ${bkp.verificationStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569)
                            )
                        }
                        Row {
                            IconButton(onClick = { onRestoreLocalItem(bkp) }) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore Backup Lokal", tint = SeaBluePrimary)
                            }
                            IconButton(onClick = { onDeleteLocalItem(bkp) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus Backup Lokal", tint = StatusOverdueRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreDataSection(
    restorePreview: RestorePreviewSummary?,
    selectedMode: RestoreMode,
    isBusy: Boolean,
    onPickZipFromPhone: () -> Unit,
    onSelectMode: (RestoreMode) -> Unit,
    onConfirmRestoreClick: () -> Unit,
    onDismissPreview: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("panel_restore_data")) {
        Text(
            text = "3. Import Backup & Restore Database (.ZIP / .JSON / .DB)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = NavyDark
        )
        Text(
            text = "Mendukung file backup resmi aplikasi (.ZIP, .JSON, maupun .DB/.SQLITE) dengan validasi struktur, snapshot pengaman otomatis, auto-rollback, dan sinkronisasi ulang Google Drive.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF475569)
        )
        Button(
            onClick = onPickZipFromPhone,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_pick_restore_zip")
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("IMPORT BACKUP (.ZIP / .JSON / .DB)", fontWeight = FontWeight.Bold)
        }

        if (restorePreview != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (restorePreview.isValid) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, if (restorePreview.isValid) StatusPaidGreen.copy(alpha = 0.5f) else StatusOverdueRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_restore_preview")
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = if (restorePreview.isValid) StatusPaidGreen else StatusOverdueRed)
                            Text(
                                text = if (restorePreview.isValid) "Preview Backup Terverifikasi" else "Validasi Backup Gagal",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        }
                        TextButton(onClick = onDismissPreview) { Text("Tutup") }
                    }
                    if (!restorePreview.isValid) {
                        Text(restorePreview.errorMessage, style = MaterialTheme.typography.bodySmall, color = StatusOverdueRed)
                    } else {
                        Text(
                            text = "File: ${restorePreview.fileName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Tanggal Backup: ${restorePreview.createdAt.ifBlank { "-" }} • Versi Backup: DB v${restorePreview.databaseVersion} (App ${restorePreview.appVersion.ifBlank { "-" }})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF087EA4),
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("• Jumlah Pelanggan: ${restorePreview.customerCount}", style = MaterialTheme.typography.bodySmall, color = NavyDark)
                                Text("• Jumlah Unit Freezer: ${restorePreview.unitCount}", style = MaterialTheme.typography.bodySmall, color = NavyDark)
                                Text("• Jumlah Transaksi: ${restorePreview.transactionCount}", style = MaterialTheme.typography.bodySmall, color = NavyDark)
                                Text("• Jumlah Pembayaran: ${restorePreview.paymentCount}", style = MaterialTheme.typography.bodySmall, color = NavyDark)
                                Text("• Jumlah Dokumentasi: ${restorePreview.photoCount}", style = MaterialTheme.typography.bodySmall, color = NavyDark)
                            }
                        }
                        Text(
                            text = "Integritas File: Lolos (${restorePreview.verifiedEntriesCount} entri terverifikasi)",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusPaidGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            RestoreMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = selectedMode == mode,
                                    onClick = { onSelectMode(mode) },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onDismissPreview,
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_cancel_restore_import")
                            ) {
                                Text("BATALKAN IMPORT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onConfirmRestoreClick,
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusPaidGreen),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("btn_execute_restore")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedMode == RestoreMode.FULL_RESTORE) "RESTORE DATA SEPENUHNYA" else "EKSEKUSI RESTORE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportDataSection(
    onExportFullZip: () -> Unit,
    onExportFullXlsx: () -> Unit,
    onExportCustXlsx: () -> Unit,
    onExportTxPdf: () -> Unit,
    onExportCategoryCsv: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("panel_export_data")) {
        Text("5. Export Data Multi-Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NavyDark)
        Button(onClick = onExportFullZip, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Full Encrypted Backup (.ZIP)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onExportFullXlsx, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Full .XLSX", style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(onClick = onExportCustXlsx, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pelanggan .XLSX", style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onExportTxPdf, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Laporan PDF", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { onExportCategoryCsv("TRANSAKSI") }, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Transaksi .CSV", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DownloadTemplateSection(
    onDownloadTemplate: (TemplateType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("panel_download_template")) {
        Text("6. Download Template Excel Import (.XLSX)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NavyDark)
        Text("Unduh template resmi siap pakai dengan header standar & contoh pengisian yang benar.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
        TemplateType.entries.forEach { tpl ->
            FilledTonalButton(
                onClick = { onDownloadTemplate(tpl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_download_tpl_${tpl.name}")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(tpl.fileName, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AutoBackupScheduleAndAuditSection(
    config: BackupScheduleConfig,
    auditLogs: List<BackupAuditLogEntity>,
    onSaveConfig: (BackupScheduleConfig) -> Unit
) {
    var draft by remember(config) { mutableStateOf(config) }
    val freqs = listOf(
        "DAILY" to "Setiap Hari (01:00)",
        "WEEKLY" to "Setiap Minggu",
        "MONTHLY" to "Setiap Bulan",
        "MANUAL" to "Backup Manual"
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("panel_auto_backup_settings")) {
        Text("7. Jadwal Backup Otomatis (WorkManager)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NavyDark)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            freqs.forEach { (code, label) ->
                FilterChip(
                    selected = draft.frequency == code,
                    onClick = { draft = draft.copy(frequency = code) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Hanya saat terhubung Wi-Fi", style = MaterialTheme.typography.bodySmall)
            Switch(checked = draft.requireWifiOnly, onCheckedChange = { draft = draft.copy(requireWifiOnly = it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Hanya saat baterai mencukupi", style = MaterialTheme.typography.bodySmall)
            Switch(checked = draft.requireBatteryNotLow, onCheckedChange = { draft = draft.copy(requireBatteryNotLow = it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Enkripsi AES-256-GCM pada arsip", style = MaterialTheme.typography.bodySmall)
            Switch(checked = draft.encryptSensitivePayload, onCheckedChange = { draft = draft.copy(encryptSensitivePayload = it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Sertakan foto & bukti pembayaran", style = MaterialTheme.typography.bodySmall)
            Switch(checked = draft.includePhotos, onCheckedChange = { draft = draft.copy(includePhotos = it) })
        }
        Button(
            onClick = { onSaveConfig(draft) },
            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_save_backup_schedule")
        ) {
            Text("Simpan Jadwal Backup Otomatis", fontWeight = FontWeight.Bold)
        }

        if (auditLogs.isNotEmpty()) {
            HorizontalDivider()
            Text("Riwayat Audit Aktivitas Data (${auditLogs.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            auditLogs.take(10).forEach { log ->
                Text(
                    text = "• [${log.actionType}] ${log.status} — ${log.detailMessage} (${log.timestampDisplay})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF475569)
                )
            }
        }
    }
}
