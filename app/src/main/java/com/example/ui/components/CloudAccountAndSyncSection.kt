package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cloud.CloudStoragePhotoManager
import com.example.cloud.CloudSyncAndMigrationManager
import com.example.cloud.GoogleAccountInfo
import com.example.data.local.CloudMigrationStateEntity
import com.example.data.local.CloudPhotoJobEntity
import com.example.ui.theme.AppColorTokens
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusWarningYellow

@Composable
fun CloudAccountAndSyncSection(
    googleAccountInfo: GoogleAccountInfo?,
    cloudSyncStatus: String,
    cloudLastSyncTimestamp: String,
    cloudSyncError: String?,
    cloudMigrationState: CloudMigrationStateEntity?,
    cloudPhotoJobs: List<CloudPhotoJobEntity>,
    cloudPendingSyncCount: Int,
    onManualCloudSync: () -> Unit,
    onRetryFailedPhotos: () -> Unit,
    onRetrySinglePhoto: (Long) -> Unit = {},
    onResumeCloudMigration: () -> Unit,
    onSwitchGoogleAccount: () -> Unit,
    onSignOutGoogleAccount: () -> Unit
) {
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. KARTU AKUN GOOGLE AKTIF
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_google_account"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavyDark)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val photoUrl = googleAccountInfo?.photoUrl.orEmpty()
                        if (photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Foto Profil Google",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Akun Google",
                                tint = Color(0xFFBAE6FD),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = googleAccountInfo?.displayName?.ifBlank { "Akun Google Aktif" }
                                ?: "Belum Login Akun Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = googleAccountInfo?.email?.ifBlank { "-" } ?: "Silakan masuk untuk sinkronisasi cloud",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBAE6FD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!googleAccountInfo?.uid.isNullOrBlank()) {
                            Text(
                                text = "UID: ${googleAccountInfo?.uid}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColorTokens.NavInactiveOnDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onSwitchGoogleAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_switch_google_account")
                    ) {
                        Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ganti Akun", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showLogoutConfirmDialog = true },
                        enabled = googleAccountInfo != null,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.65f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_logout_google_account")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Logout Akun", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. STATUS SINKRONISASI CLOUD FIRESTORE & ANTREAN OFFLINE
        val (statusBg, statusFg, statusIcon) = when (cloudSyncStatus) {
            CloudSyncAndMigrationManager.STATUS_SYNCED -> Triple(
                Color(0xFFECFDF5),
                StatusPaidGreen,
                Icons.Default.CloudDone
            )
            CloudSyncAndMigrationManager.STATUS_SYNCING -> Triple(
                Color(0xFFE0F2FE),
                SeaBluePrimary,
                Icons.Default.CloudSync
            )
            CloudSyncAndMigrationManager.STATUS_WAITING_NETWORK -> Triple(
                Color(0xFFFEF3C7),
                Color(0xFFB45309),
                Icons.Default.CloudOff
            )
            else -> Triple(
                Color(0xFFFEE2E2),
                StatusOverdueRed,
                Icons.Default.ErrorOutline
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_cloud_sync_status"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sinkronisasi Cloud Firestore",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusBg,
                        modifier = Modifier.testTag("badge_cloud_sync_status")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(statusIcon, contentDescription = null, tint = statusFg, modifier = Modifier.size(14.dp))
                            Text(
                                text = cloudSyncStatus,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusFg
                            )
                        }
                    }
                }

                if (cloudSyncStatus == CloudSyncAndMigrationManager.STATUS_SYNCING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Text(
                    text = "Antrean perubahan offline menunggu sync: $cloudPendingSyncCount item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cloudLastSyncTimestamp.isNotBlank()) {
                    Text(
                        text = "Sinkronisasi Cloud Terakhir: $cloudLastSyncTimestamp",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF475569)
                    )
                }
                if (!cloudSyncError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Catatan Sinkronisasi: $cloudSyncError",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusOverdueRed,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Button(
                    onClick = onManualCloudSync,
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_cloud_sync_now")
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sinkronkan Cloud Sekarang", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                // Ringkasan Unggah Foto Cloud Storage
                val uploadedPhotos = cloudPhotoJobs.count { it.status == CloudStoragePhotoManager.STATUS_UPLOADED }
                val failedPhotos = cloudPhotoJobs.count { it.status == CloudStoragePhotoManager.STATUS_FAILED }
                val activePhotos = cloudPhotoJobs.filter {
                    it.status == CloudStoragePhotoManager.STATUS_QUEUED ||
                        it.status == CloudStoragePhotoManager.STATUS_UPLOADING
                }

                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Cloud Storage Foto (Terunggah: $uploadedPhotos • Antrean: ${activePhotos.size} • Gagal: $failedPhotos)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyDark
                    )
                }

                if (activePhotos.isNotEmpty()) {
                    val avgProgress = activePhotos.map { it.progressPercent.coerceIn(0, 100) }.average().toFloat()
                    Text(
                        text = "Progres Upload: ${avgProgress.toInt()}% (${activePhotos.size} foto dalam proses/antrean)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SeaBluePrimary
                    )
                    LinearProgressIndicator(
                        progress = { avgProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_cloud_photo_upload")
                    )
                }

                if (failedPhotos > 0) {
                    OutlinedButton(
                        onClick = onRetryFailedPhotos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_retry_failed_photos")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unggah Ulang $failedPhotos Foto Gagal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                // Detail status unggahan foto terbaru/aktif/gagal (persentase, MENUNGGU UPLOAD/BERHASIL/GAGAL, & Upload Ulang)
                val highlightedJobs = (cloudPhotoJobs.filter { it.status != CloudStoragePhotoManager.STATUS_UPLOADED } +
                    cloudPhotoJobs.filter { it.status == CloudStoragePhotoManager.STATUS_UPLOADED }).take(5)
                highlightedJobs.forEach { job ->
                    CloudPhotoJobStatusRow(
                        job = job,
                        onRetryClick = { onRetrySinglePhoto(job.id) }
                    )
                }
            }
        }

        // 3. KARTU MIGRASI DATA SQLITE -> CLOUD (10 LANGKAH RESUMABLE)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_cloud_migration"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val currentStep = cloudMigrationState?.currentStep ?: 0
                val migrationStatus = cloudMigrationState?.status ?: "NOT_STARTED"
                val progressFraction = (currentStep.coerceIn(0, 10) / 10f)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (migrationStatus == "COMPLETED") Icons.Default.Verified else Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = if (migrationStatus == "COMPLETED") StatusPaidGreen else SeaBluePrimary
                        )
                        Text(
                            text = "Migrasi SQLite ke Cloud",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (migrationStatus == "COMPLETED") Color(0xFFECFDF5) else Color(0xFFE0F2FE)
                    ) {
                        Text(
                            text = migrationStatus,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (migrationStatus == "COMPLETED") StatusPaidGreen else SeaBluePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("progress_cloud_migration")
                )

                Text(
                    text = CloudSyncAndMigrationManager.stepDescription(currentStep),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NavyDark
                )

                if (cloudMigrationState != null) {
                    Text(
                        text = "Verifikasi Jumlah Data — Pelanggan: ${cloudMigrationState.localCustomersCount} lokal / ${cloudMigrationState.cloudCustomersCount} cloud • Unit: ${cloudMigrationState.localUnitsCount} lokal / ${cloudMigrationState.cloudUnitsCount} cloud • Transaksi: ${cloudMigrationState.localTxCount} lokal / ${cloudMigrationState.cloudTxCount} cloud",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF475569)
                    )
                }

                Text(
                    text = "Data lokal SQLite tetap dipertahankan aman dan tidak pernah dihapus sebelum seluruh migrasi terverifikasi selesai.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!cloudMigrationState?.lastError.isNullOrBlank()) {
                    Text(
                        text = "Terakhir terhenti: ${cloudMigrationState?.lastError}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusWarningYellow
                    )
                }

                Button(
                    onClick = onResumeCloudMigration,
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_start_resume_migration")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (migrationStatus) {
                            "COMPLETED" -> "Verifikasi & Sinkronisasi Ulang"
                            "PAUSED", "FAILED", "IN_PROGRESS" -> "Lanjutkan Migrasi (Step $currentStep/10)"
                            else -> "Mulai Migrasi ke Cloud"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = StatusOverdueRed) },
            title = { Text("Konfirmasi Logout Akun Google", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Anda akan keluar dari akun ${googleAccountInfo?.email ?: "Google saat ini"}. Data lokal tetap tersimpan, namun sinkronisasi Cloud dijeda sampai Anda masuk kembali. Lanjutkan logout?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onSignOutGoogleAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOverdueRed),
                    modifier = Modifier.testTag("btn_confirm_logout_google")
                ) {
                    Text("Ya, Logout")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
