package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.process.GlobalProcessState
import com.example.process.GlobalProcessStatus
import com.example.process.ProcessVerificationReport
import com.example.process.VerificationItemStatus
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.TextDescription
import com.example.ui.theme.TextFormLabel
import com.example.ui.theme.TextPageTitle

/**
 * Global Smart Loading & Verified Completion Modal Dialog.
 *
 * - Darkened scrim backdrop with rounded white modal card.
 * - Real-work progress bar (determinate 0-100% + 0/100..100/100 counter, or indeterminate).
 * - Live status badge: PROCESSING, LOCAL SAVED, UPLOADING, SYNCING, VERIFYING, ✓ SELESAI, ⚠ TERSIMPAN LOKAL, ✕ GAGAL.
 * - Cannot be dismissed while backend work or verification is still running.
 * - Displays explicit 4-layer verification checklist on completion, partial local save, or failure.
 */
@Composable
fun SmartLoadingProcessDialog(
    state: GlobalProcessState?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit = {}
) {
    if (state == null || !state.isVisible) return

    val canDismissDialog = state.isTerminal

    val accentColor by animateColorAsState(
        targetValue = when (state.processStatus) {
            GlobalProcessStatus.COMPLETED -> StatusPaidGreen
            GlobalProcessStatus.PARTIAL_SUCCESS -> StatusWarningYellow
            GlobalProcessStatus.FAILED -> StatusOverdueRed
            GlobalProcessStatus.CANCELLED -> TextDescription
            GlobalProcessStatus.VERIFYING -> Color(0xFF087EA4)
            else -> SeaBluePrimary
        },
        animationSpec = tween(220),
        label = "process_accent_color"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = (state.progressPercentage.coerceIn(0, 100)) / 100f,
        animationSpec = tween(180),
        label = "process_bar_fraction"
    )

    Dialog(
        onDismissRequest = { if (canDismissDialog) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = canDismissDialog,
            dismissOnClickOutside = canDismissDialog,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.28f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag("smart_loading_process_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row: Title + Live Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val headerIcon = when (state.processStatus) {
                                GlobalProcessStatus.COMPLETED -> Icons.Default.CheckCircle
                                GlobalProcessStatus.PARTIAL_SUCCESS -> Icons.Default.WarningAmber
                                GlobalProcessStatus.FAILED -> Icons.Default.ErrorOutline
                                GlobalProcessStatus.VERIFYING -> Icons.Default.VerifiedUser
                                else -> Icons.Default.CloudDone
                            }
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = state.stageBadge.displayLabel,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = when (state.processStatus) {
                                GlobalProcessStatus.COMPLETED -> "✓ SELESAI!"
                                GlobalProcessStatus.PARTIAL_SUCCESS -> "⚠ DATA TERSIMPAN LOKAL"
                                GlobalProcessStatus.FAILED -> "✕ PROSES GAGAL"
                                GlobalProcessStatus.CANCELLED -> "PROSES DIBATALKAN"
                                else -> state.processName.ifBlank { "Memproses Data..." }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (state.processStatus) {
                                GlobalProcessStatus.COMPLETED -> StatusPaidGreen
                                GlobalProcessStatus.PARTIAL_SUCCESS -> Color(0xFFB45309)
                                GlobalProcessStatus.FAILED -> StatusOverdueRed
                                else -> TextPageTitle
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("smart_loading_title")
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .semantics(mergeDescendants = true) {}
                            .testTag("smart_loading_status_badge")
                    ) {
                        Text(
                            text = state.stageBadge.displayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state.processStatus == GlobalProcessStatus.PARTIAL_SUCCESS) {
                                Color(0xFF92400E)
                            } else {
                                accentColor
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Process Subtitle when in terminal state so user knows which activity finished
                if (state.isTerminal && state.processName.isNotBlank()) {
                    Text(
                        text = state.processName.removeSuffix("..."),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextFormLabel
                    )
                }

                // Horizontal Progress Bar (Real work only — determinate or indeterminate)
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isIndeterminate && state.isActive) {
                        LinearProgressIndicator(
                            color = accentColor,
                            trackColor = Color(0xFFE2E8F0),
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .testTag("smart_loading_progress_indeterminate")
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            color = accentColor,
                            trackColor = Color(0xFFE2E8F0),
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .testTag("smart_loading_progress_bar")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.isIndeterminate && state.isActive) {
                                "Sedang bekerja…"
                            } else {
                                "${state.progressPercentage.coerceIn(0, 100)}%"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPageTitle,
                            modifier = Modifier.testTag("smart_loading_percentage_text")
                        )
                        Text(
                            text = if (state.isIndeterminate && state.isActive) {
                                "Tahap ${state.currentStep}/${state.totalSteps}"
                            } else {
                                state.stepFractionText
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF087EA4),
                            modifier = Modifier.testTag("smart_loading_fraction_text")
                        )
                    }
                }

                // Active Activity Description or Outcome Explanation
                Text(
                    text = state.currentActivityMessage.ifBlank { "Menjalankan proses..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDescription,
                    modifier = Modifier.testTag("smart_loading_activity_message")
                )

                // Specific Error Cause Box on FAILED or PARTIAL_SUCCESS
                if ((state.processStatus == GlobalProcessStatus.FAILED || state.processStatus == GlobalProcessStatus.PARTIAL_SUCCESS) &&
                    state.errorMessage.isNotBlank()
                ) {
                    val boxBg = if (state.processStatus == GlobalProcessStatus.FAILED) {
                        Color(0xFFFEF2F2)
                    } else {
                        Color(0xFFFFFBEB)
                    }
                    val boxBorder = if (state.processStatus == GlobalProcessStatus.FAILED) {
                        StatusOverdueRed.copy(alpha = 0.35f)
                    } else {
                        StatusWarningYellow.copy(alpha = 0.45f)
                    }
                    Surface(
                        color = boxBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, boxBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_loading_error_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (state.failedStageName.isNotBlank()) {
                                Text(
                                    text = "Tahap terkendala: ${state.failedStageName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.processStatus == GlobalProcessStatus.FAILED) {
                                        StatusOverdueRed
                                    } else {
                                        Color(0xFF92400E)
                                    }
                                )
                            }
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPageTitle
                            )
                        }
                    }
                }

                // Verification Summary Matrix (Database Lokal, Dokumentasi, Google Drive, Sinkronisasi)
                val report = state.verificationReport
                if (report != null && state.isTerminal) {
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    VerificationSummarySection(report = report)
                }

                // Action Buttons (Only on terminal states, or optional Cancel when allowed)
                if (state.isTerminal) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.canRetry &&
                            (state.processStatus == GlobalProcessStatus.FAILED ||
                                state.processStatus == GlobalProcessStatus.PARTIAL_SUCCESS ||
                                state.processStatus == GlobalProcessStatus.CANCELLED)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("smart_loading_btn_close")
                            ) {
                                Text(
                                    text = "Tutup",
                                    color = TextPageTitle,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = onRetry,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.processStatus == GlobalProcessStatus.PARTIAL_SUCCESS) {
                                        Color(0xFFD97706)
                                    } else {
                                        SeaBluePrimary
                                    },
                                    contentColor = PureWhite
                                ),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .heightIn(min = 48.dp)
                                    .testTag("smart_loading_btn_retry")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Coba Lagi",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "COBA LAGI",
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.processStatus == GlobalProcessStatus.COMPLETED) {
                                        StatusPaidGreen
                                    } else {
                                        SeaBluePrimary
                                    },
                                    contentColor = PureWhite
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("smart_loading_btn_done")
                            ) {
                                Text(
                                    text = if (state.processStatus == GlobalProcessStatus.COMPLETED) "Selesai" else "Tutup",
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (state.canCancel) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("smart_loading_btn_cancel")
                    ) {
                        Text(
                            text = "Batalkan Proses",
                            color = TextPageTitle,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationSummarySection(report: ProcessVerificationReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_loading_verification_summary"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Hasil Verifikasi Proses:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TextPageTitle
        )
        VerificationRow(
            label = "Database lokal",
            status = report.localDatabaseStatus,
            detail = report.localDatabaseDetail
        )
        VerificationRow(
            label = "Dokumentasi",
            status = report.documentationStatus,
            detail = report.documentationDetail
        )
        VerificationRow(
            label = "Google Drive",
            status = report.googleDriveStatus,
            detail = report.googleDriveDetail
        )
        VerificationRow(
            label = "Sinkronisasi",
            status = report.synchronizationStatus,
            detail = report.synchronizationDetail
        )
    }
}

@Composable
private fun VerificationRow(
    label: String,
    status: VerificationItemStatus,
    detail: String
) {
    val (badgeColor, icon) = when (status) {
        VerificationItemStatus.SUCCESS -> StatusPaidGreen to Icons.Default.CheckCircle
        VerificationItemStatus.SKIPPED_NOT_REQUIRED -> TextDescription to Icons.Default.CheckCircle
        VerificationItemStatus.PARTIAL_OFFLINE -> Color(0xFFD97706) to Icons.Default.CloudOff
        VerificationItemStatus.FAILED -> StatusOverdueRed to Icons.Default.ErrorOutline
        VerificationItemStatus.PENDING -> SeaBluePrimary to Icons.Default.CloudDone
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$label ${status.displayLabel}",
                    tint = badgeColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = TextFormLabel
                )
            }
            Text(
                text = status.displayLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = TextDescription,
                modifier = Modifier.padding(start = 22.dp)
            )
        }
    }
}

/**
 * Recovery banner shown on startup if the app was force-closed mid-process earlier.
 */
@Composable
fun InterruptedProcessRecoveryBanner(
    interrupted: GlobalProcessState?,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    if (interrupted == null) return
    Surface(
        color = Color(0xFFFFFBEB),
        border = BorderStroke(1.dp, StatusWarningYellow.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("interrupted_process_recovery_banner")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Proses Terhenti",
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Proses Sebelumnya Terhenti (${interrupted.progressPercentage}%)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPageTitle
                )
            }
            Text(
                text = interrupted.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextDescription
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onSyncNow()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary, contentColor = PureWhite),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_recover_interrupted_sync")
                ) {
                    Text("Sinkronkan Sekarang", color = PureWhite, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_dismiss_interrupted_notice")
                ) {
                    Text("Tutup", color = TextPageTitle, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
