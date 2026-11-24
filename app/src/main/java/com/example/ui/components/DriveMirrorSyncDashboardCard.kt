package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import com.example.data.local.DataConsistencyAuditReport
import com.example.data.local.DriveCloudQuarantineEntity
import com.example.data.local.DriveMirrorDashboardState
import com.example.data.local.SyncTechnicalLogEntity

/**
 * Bagian J, 10 & 11:
 * Dashboard Sinkronisasi Real-Time Google Drive Cloud Mirror, Audit Konsistensi,
 * Log Teknis Sinkronisasi & Dialog Reset Data 3 Opsi.
 */
@Composable
fun DriveMirrorSyncDashboardCard(
    state: DriveMirrorDashboardState,
    onSyncNowClick: () -> Unit,
    onRetryFailedClick: () -> Unit,
    onResetLocalAndCloudConfirm: (createFinalBackupFirst: Boolean) -> Unit,
    onResetLocalOnlyConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    consistencyAuditReport: DataConsistencyAuditReport? = null,
    isAuditingConsistency: Boolean = false,
    syncTechnicalLogs: List<SyncTechnicalLogEntity> = emptyList(),
    onRunConsistencyAuditClick: () -> Unit = {},
    onCleanOrphanAndResyncClick: () -> Unit = {},
    quarantineItems: List<DriveCloudQuarantineEntity> = emptyList(),
    onScanStaleCloudClick: () -> Unit = {},
    onPurgeVerifiedQuarantineClick: () -> Unit = {},
    onReconnectDriveClick: () -> Unit = {}
) {
    var showResetOptionsDialog by remember { mutableStateOf(false) }
    var showSecondConfirmLocalAndCloudDialog by remember { mutableStateOf(false) }
    var createFinalBackupBeforeReset by remember { mutableStateOf(false) }
    var showTechnicalLogs by remember { mutableStateOf(false) }

    val effectiveStatusKey = when {
        state.requiresReAuth || state.syncStatusCode == "WAITING_AUTH" || state.overallStatus == "FAILED_AUTH" -> "WAITING_AUTH"
        state.syncStatusCode == "WAITING_NETWORK" -> "WAITING_NETWORK"
        state.syncStatusCode == "PARTIAL_SUCCESS" -> "PARTIAL_SUCCESS"
        else -> state.overallStatus
    }

    val statusColor = when (effectiveStatusKey) {
        "SYNCED", "SUCCESS" -> Color(0xFF16A34A)
        "PENDING", "WAITING_NETWORK", "PARTIAL_SUCCESS" -> Color(0xFFD97706)
        "UPLOADING", "DOWNLOADING", "RUNNING" -> Color(0xFF2563EB)
        "CONFLICT" -> Color(0xFFB91C1C)
        "FAILED", "FAILED_AUTH", "WAITING_AUTH" -> Color(0xFFDC2626)
        "RESETTING" -> Color(0xFF7C3AED)
        else -> Color(0xFF475569)
    }

    val statusLabelId = when (effectiveStatusKey) {
        "SYNCED", "SUCCESS" -> "SUCCESS • TERSINKRON"
        "PARTIAL_SUCCESS" -> "PARTIAL_SUCCESS • SEBAGIAN"
        "PENDING" -> "PENDING • MENUNGGU SINKRON"
        "WAITING_NETWORK" -> "WAITING_NETWORK • OFFLINE"
        "WAITING_AUTH", "FAILED_AUTH" -> "WAITING_AUTH • LOGIN ULANG"
        "UPLOADING", "RUNNING" -> "RUNNING • MENGUNGGAH"
        "DOWNLOADING" -> "RUNNING • MENGUNDUH"
        "CONFLICT" -> "CONFLICT • VERSI BERBEDA"
        "FAILED" -> "FAILED • PERLU RETRY"
        "RESETTING" -> "RESETTING • MERESET DATA"
        else -> state.syncStatusCode.ifBlank { state.overallStatus }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_drive_mirror_sync_dashboard"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (effectiveStatusKey) {
                                "SYNCED", "SUCCESS" -> Icons.Default.CloudDone
                                "FAILED", "FAILED_AUTH", "WAITING_AUTH", "CONFLICT" -> Icons.Default.ErrorOutline
                                "PENDING", "WAITING_NETWORK", "PARTIAL_SUCCESS" -> Icons.Default.Schedule
                                else -> Icons.Default.CloudSync
                            },
                            contentDescription = "Status Cloud Mirror",
                            tint = statusColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Dashboard Sinkronisasi Cloud Mirror",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                state.requiresReAuth || state.syncStatusCode == "WAITING_AUTH" ->
                                    "Autentikasi Google Drive kedaluwarsa — login ulang diperlukan"
                                state.isDriveConnected ->
                                    "Terhubung: ${state.connectedEmail.ifBlank { "Akun Google Drive Aktif" }}"
                                else ->
                                    "Google Drive belum terhubung (Mode Antrean Lokal)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.requiresReAuth) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.14f),
                    modifier = Modifier.testTag("badge_mirror_sync_status")
                ) {
                    Text(
                        text = statusLabelId,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Progress Bar when UPLOADING / DOWNLOADING / RESETTING
            if (state.overallStatus in listOf("UPLOADING", "DOWNLOADING", "RESETTING")) {
                val progressVal = if (state.overallStatus == "DOWNLOADING") {
                    state.downloadProgressPercent
                } else {
                    state.uploadProgressPercent
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.08f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.activeOperationLabel.ifBlank { "Memproses sinkronisasi cloud mirror..." },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                        Text(
                            text = "${progressVal.coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (progressVal.coerceIn(0, 100)) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("progress_mirror_sync"),
                        color = statusColor
                    )
                }
            }

            // Metrics Grid: Total Data Lokal vs Cloud
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MirrorStatMetricBox(
                    label = "Total Data Lokal",
                    value = state.totalLocalDataCount.toString(),
                    accentColor = Color(0xFF0F766E),
                    testTag = "metric_total_local_data",
                    modifier = Modifier.weight(1f)
                )
                MirrorStatMetricBox(
                    label = "Total Data Cloud",
                    value = state.totalCloudDataCount.toString(),
                    accentColor = Color(0xFF2563EB),
                    testTag = "metric_total_cloud_data",
                    modifier = Modifier.weight(1f)
                )
            }

            // Metrics Grid: Synced & Photos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MirrorStatMetricBox(
                    label = "Data Synced",
                    value = state.totalSyncedDataCount.toString(),
                    accentColor = Color(0xFF0284C7),
                    testTag = "metric_total_synced_data",
                    modifier = Modifier.weight(1f)
                )
                MirrorStatMetricBox(
                    label = "Foto Tersinkron",
                    value = state.totalSyncedPhotosCount.toString(),
                    accentColor = Color(0xFF16A34A),
                    testTag = "metric_total_synced_photos",
                    modifier = Modifier.weight(1f)
                )
            }

            // Metrics Grid: Pending & Gagal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MirrorStatMetricBox(
                    label = "Data Pending",
                    value = state.pendingSyncCount.toString(),
                    accentColor = Color(0xFFD97706),
                    testTag = "metric_pending_sync_count",
                    modifier = Modifier.weight(1f)
                )
                MirrorStatMetricBox(
                    label = "Data Gagal",
                    value = state.failedSyncCount.toString(),
                    accentColor = Color(0xFFDC2626),
                    testTag = "metric_failed_sync_count",
                    modifier = Modifier.weight(1f)
                )
            }

            // Metrics Grid: Stale & Conflict
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MirrorStatMetricBox(
                    label = "Data Stale",
                    value = state.staleDataCount.toString(),
                    accentColor = Color(0xFF9333EA),
                    testTag = "metric_stale_data_count",
                    modifier = Modifier.weight(1f)
                )
                MirrorStatMetricBox(
                    label = "Data Conflict",
                    value = state.conflictDataCount.toString(),
                    accentColor = Color(0xFFB91C1C),
                    testTag = "metric_conflict_data_count",
                    modifier = Modifier.weight(1f)
                )
            }

            // Last Sync & Manifest Metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Sinkronisasi Terakhir: ${state.lastSyncTimeDisplay}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("text_last_mirror_sync_time")
                )
                Text(
                    text = "Generasi Dataset: ${state.datasetGeneration}" +
                        if (state.isResetLocked) " • RESET LOCK AKTIF" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isResetLocked) Color(0xFF7C3AED) else Color(0xFF0284C7),
                    modifier = Modifier.testTag("text_dataset_generation")
                )
                Text(
                    text = "Manifest Cloud: v${state.manifestState.syncVersion} • Status Dataset: ${state.manifestState.datasetStatus} • Pelanggan: ${state.manifestState.totalCustomers} • Unit: ${state.manifestState.totalUnits} • Transaksi: ${state.manifestState.totalTransactions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.lastErrorMessage.isNotBlank()) {
                    Text(
                        text = "Catatan: ${state.lastErrorMessage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (state.requiresReAuth || state.failedAuthCount > 0 || state.syncStatusCode == "WAITING_AUTH") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFDC2626).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("banner_drive_reauth_required"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Autentikasi Google Drive Diperlukan (HTTP 401)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB91C1C)
                    )
                    Text(
                        text = "Token akses OAuth 2.0 kedaluwarsa atau dicabut. Antrean (${state.failedSyncCount} gagal, ${state.pendingSyncCount} pending) tersimpan aman di SQLite dan akan otomatis dilanjutkan setelah login ulang.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7F1D1D)
                    )
                    Button(
                        onClick = onReconnectDriveClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_mirror_reconnect_drive"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Login Ulang Google Drive",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Login Ulang Google Drive & Pulihkan Antrean", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Action Buttons: Sinkronisasi Sekarang & Retry Gagal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSyncNowClick,
                    enabled = !state.isResetLocked,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_mirror_sync_now"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sinkronisasi Sekarang",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sinkronisasi Sekarang", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRetryFailedClick,
                    enabled = !state.isResetLocked,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_mirror_retry_failed"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry Gagal",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry Gagal (${state.failedSyncCount})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Audit Konsistensi Data (Bagian 10)
            OutlinedButton(
                onClick = onRunConsistencyAuditClick,
                enabled = !isAuditingConsistency && !state.isResetLocked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_audit_consistency"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FactCheck,
                    contentDescription = "Audit Konsistensi Lokal vs Cloud",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAuditingConsistency) "Mengaudit SQLite vs Drive..." else "Audit Konsistensi (Lokal vs Cloud)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (consistencyAuditReport != null) {
                val reportOk = consistencyAuditReport.isConsistent && consistencyAuditReport.errorMessage.isBlank()
                val auditAccent = if (reportOk) Color(0xFF16A34A) else Color(0xFFD97706)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, auditAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .background(auditAccent.copy(alpha = 0.08f))
                        .padding(10.dp)
                        .testTag("card_consistency_audit_report"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (reportOk) "AUDIT KONSISTEN" else "DITEMUKAN SELISIH / PERINGATAN",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = auditAccent
                        )
                        Text(
                            text = consistencyAuditReport.auditedAtDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Lokal SQLite: Pelanggan=${consistencyAuditReport.localCustomersCount}, Unit=${consistencyAuditReport.localUnitsCount}, Transaksi=${consistencyAuditReport.localTransactionsCount}, Bayar=${consistencyAuditReport.localPaymentsCount}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "Cloud Drive: Pelanggan=${consistencyAuditReport.cloudCustomersCount}, Unit=${consistencyAuditReport.cloudUnitsCount}, Transaksi=${consistencyAuditReport.cloudTransactionsCount}, Bayar=${consistencyAuditReport.cloudPaymentsCount}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "Generasi Lokal=${consistencyAuditReport.localDatasetGeneration} | Cloud=${consistencyAuditReport.cloudDatasetGeneration.ifBlank { "-" }} (${consistencyAuditReport.cloudManifestStatus.ifBlank { "-" }})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (consistencyAuditReport.errorMessage.isNotBlank()) {
                        Text(
                            text = "Info: ${consistencyAuditReport.errorMessage}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626)
                        )
                    }
                    if (consistencyAuditReport.missingInCloud.isNotEmpty()) {
                        Text(
                            text = "Belum di Cloud (${consistencyAuditReport.missingInCloud.size}): ${consistencyAuditReport.missingInCloud.take(5).joinToString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD97706)
                        )
                    }
                    if (consistencyAuditReport.duplicateCloudFiles.isNotEmpty()) {
                        Text(
                            text = "Duplikat Cloud (${consistencyAuditReport.duplicateCloudFiles.size}): ${consistencyAuditReport.duplicateCloudFiles.take(5).joinToString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626)
                        )
                    }
                    if (consistencyAuditReport.staleRecords.isNotEmpty()) {
                        Text(
                            text = "Orphan/Stale Cloud (${consistencyAuditReport.staleRecords.size}): ${consistencyAuditReport.staleRecords.take(5).joinToString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626)
                        )
                    }
                    if (consistencyAuditReport.quarantinedFilesCount > 0) {
                        Text(
                            text = "Dikunci di Karantina Cloud: ${consistencyAuditReport.quarantinedFilesCount} file (tidak pernah di-auto-import)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val needsOrphanCleanupOrResync = !reportOk ||
                        consistencyAuditReport.staleRecords.isNotEmpty() ||
                        consistencyAuditReport.duplicateCloudFiles.isNotEmpty() ||
                        consistencyAuditReport.quarantinedFilesCount > 0 ||
                        state.staleDataCount > 0
                    if (needsOrphanCleanupOrResync) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onCleanOrphanAndResyncClick,
                            enabled = !isAuditingConsistency && !state.isResetLocked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_clean_orphan_and_resync")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Bersihkan Orphan dan Sinkron Ulang",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAuditingConsistency) {
                                    "Membersihkan Orphan & Sinkron Ulang..."
                                } else {
                                    "Bersihkan Orphan & Sinkron Ulang"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Karantina File Stale Cloud & Pembersihan Aman (Bagian C)
            val activeQuarantine = quarantineItems.filter { it.status == "QUARANTINED" }
            val verifiedQuarantineCount = activeQuarantine.count { it.ownershipVerified }
            val displayedQuarantineCount = maxOf(state.quarantinedFilesCount, activeQuarantine.size)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (displayedQuarantineCount > 0) Color(0xFFD97706).copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .background(
                        if (displayedQuarantineCount > 0) Color(0xFFD97706).copy(alpha = 0.06f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                    .padding(12.dp)
                    .testTag("card_cloud_quarantine_panel"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Karantina File Stale Cloud",
                            tint = if (displayedQuarantineCount > 0) Color(0xFFD97706) else Color(0xFF0F766E),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Karantina Cloud (${displayedQuarantineCount})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (displayedQuarantineCount == 0) {
                                    "Tidak ada file stale/duplikat/orphan di karantina."
                                } else {
                                    "Terverifikasi App=$verifiedQuarantineCount • Kepemilikan Tidak Pasti=${activeQuarantine.size - verifiedQuarantineCount} (aman/tidak dihapus)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (activeQuarantine.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        activeQuarantine.take(5).forEach { q ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${q.fileName} [${q.classification}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = q.quarantineReason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (q.ownershipVerified) Color(0xFFDC2626).copy(alpha = 0.12f) else Color(0xFF475569).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (q.ownershipVerified) "Terverifikasi App" else "Tidak Pasti (Aman)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (q.ownershipVerified) Color(0xFFDC2626) else Color(0xFF475569),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onScanStaleCloudClick,
                        enabled = !isAuditingConsistency && !state.isResetLocked,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_scan_stale_cloud"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Scan Stale Cloud", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (verifiedQuarantineCount > 0) {
                        Button(
                            onClick = onPurgeVerifiedQuarantineClick,
                            enabled = !isAuditingConsistency && !state.isResetLocked,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_purge_verified_quarantine"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderDelete,
                                contentDescription = "Bersihkan Karantina Terverifikasi",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bersihkan ($verifiedQuarantineCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Expandable Technical Sync Logs (Bagian 11)
            OutlinedButton(
                onClick = { showTechnicalLogs = !showTechnicalLogs },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_toggle_technical_logs"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Log Teknis Sinkronisasi",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Log Teknis Sinkronisasi (${syncTechnicalLogs.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (showTechnicalLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (showTechnicalLogs) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(10.dp)
                        .testTag("panel_technical_sync_logs"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (syncTechnicalLogs.isEmpty()) {
                        Text(
                            text = "Belum ada catatan log teknis sinkronisasi.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        syncTechnicalLogs.take(10).forEach { log ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${log.operationType} [${log.datasetGenerationId}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (log.errorCode.isNotBlank()) Color(0xFFDC2626) else Color(0xFF0284C7)
                                    )
                                    Text(
                                        text = log.operationTimeDisplay,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (log.recordId.isNotBlank() || log.driveFileId.isNotBlank()) {
                                    Text(
                                        text = "Rec=${log.recordId.ifBlank { "-" }} • DriveID=${log.driveFileId.ifBlank { "-" }} • Retry=${log.retryCount}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                if (log.errorCode.isNotBlank() || log.errorMessage.isNotBlank()) {
                                    Text(
                                        text = listOf(log.errorCode, log.errorMessage).filter { it.isNotBlank() }.joinToString(": "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (log.errorCode.isNotBlank()) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Reset Data Sinkronisasi Button (Bagian E)
            OutlinedButton(
                onClick = { showResetOptionsDialog = true },
                enabled = !state.isResetLocked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_open_reset_data_modal"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFDC2626)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Reset Data Aplikasi & Cloud",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RESET DATA APLIKASI (Lokal & Cloud Mirror)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }

    // Step 1 Modal: 3 Pilihan Reset sesuai Bagian E
    if (showResetOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showResetOptionsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Peringatan Reset",
                    tint = Color(0xFFDC2626)
                )
            },
            title = {
                Text(
                    text = "Pilih Mode Reset Data Aplikasi",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pilih tindakan reset yang diinginkan:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            showResetOptionsDialog = false
                            showSecondConfirmLocalAndCloudDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_option_reset_local_and_cloud"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("1. RESET DATA LOKAL DAN CLOUD", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showResetOptionsDialog = false
                            onResetLocalOnlyConfirm()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_option_reset_local_only"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("2. RESET DATA LOKAL SAJA", fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = { showResetOptionsDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_option_reset_cancel")
                    ) {
                        Text("3. BATALKAN", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // Step 2 Modal: Konfirmasi Kedua untuk RESET DATA LOKAL DAN CLOUD
    if (showSecondConfirmLocalAndCloudDialog) {
        AlertDialog(
            onDismissRequest = { showSecondConfirmLocalAndCloudDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Konfirmasi Kedua Reset Lokal dan Cloud",
                    tint = Color(0xFFDC2626)
                )
            },
            title = {
                Text(
                    text = "Konfirmasi Kedua: Hapus Lokal & Cloud",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "PERINGATAN: Seluruh data pelanggan, unit freezer, transaksi, pembayaran, foto lokal, serta seluruh file mirror & dokumentasi aplikasi di Google Drive akan dihapus permanen. Data lama tidak akan direstore otomatis setelah reset.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        Checkbox(
                            checked = createFinalBackupBeforeReset,
                            onCheckedChange = { createFinalBackupBeforeReset = it },
                            modifier = Modifier.testTag("checkbox_pre_reset_backup")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Buat backup arsip terakhir sebelum reset",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSecondConfirmLocalAndCloudDialog = false
                        onResetLocalAndCloudConfirm(createFinalBackupBeforeReset)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("btn_confirm_reset_local_and_cloud_step2")
                ) {
                    Text("Ya, Reset Lokal & Cloud Sekarang", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSecondConfirmLocalAndCloudDialog = false },
                    modifier = Modifier.testTag("btn_cancel_reset_step2")
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun MirrorStatMetricBox(
    label: String,
    value: String,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
