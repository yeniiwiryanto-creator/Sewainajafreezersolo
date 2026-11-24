package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.util.FormValidationResult
import com.example.util.StorageSyncStatus

/**
 * High-contrast validation error banner rendered inside every form whenever required fields
 * are missing or invalid. Never clears user inputs and explicitly names each missing column.
 */
@Composable
fun FormValidationAlertBanner(
    validationResult: FormValidationResult?,
    modifier: Modifier = Modifier,
    testTagName: String = "form_validation_alert_banner"
) {
    if (validationResult == null || validationResult.isValid || validationResult.bannerMessage.isBlank()) {
        return
    }
    Surface(
        color = Color(0xFFFEF2F2),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, StatusOverdueRed),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagName)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Data Belum Lengkap",
                tint = StatusOverdueRed,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = validationResult.bannerMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF991B1B)
                )
                if (validationResult.fieldErrors.size > 1) {
                    validationResult.fieldErrors.values.take(4).forEach { detail ->
                        Text(
                            text = "• $detail",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB91C1C)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact 5-State Local & Google Drive Sync Status Badge:
 * 1. Tersimpan di perangkat
 * 2. Menunggu sinkronisasi
 * 3. Sedang disinkronkan
 * 4. Berhasil tersinkronisasi
 * 5. Gagal disinkronisasi
 */
@Composable
fun StorageSyncStatusBadge(
    status: StorageSyncStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, fgColor, icon) = when (status) {
        StorageSyncStatus.SAVED_LOCAL -> Triple(Color(0xFFE0F2FE), Color(0xFF0369A1), Icons.Default.PhoneAndroid)
        StorageSyncStatus.PENDING_SYNC -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), Icons.Default.CloudQueue)
        StorageSyncStatus.SYNCING -> Triple(Color(0xFFDBEAFE), SeaBluePrimary, Icons.Default.CloudSync)
        StorageSyncStatus.SYNCED -> Triple(Color(0xFFDCFCE7), StatusPaidGreen, Icons.Default.CloudDone)
        StorageSyncStatus.SYNC_FAILED -> Triple(Color(0xFFFEE2E2), StatusOverdueRed, Icons.Default.CloudOff)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.35f)),
        modifier = modifier.testTag("sync_status_badge_${status.name.lowercase()}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = status.label,
                tint = fgColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Inline field error helper text shown directly beneath invalid form fields.
 */
@Composable
fun FieldErrorSupportingText(errorMessage: String?) {
    if (!errorMessage.isNullOrBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = StatusOverdueRed,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = StatusOverdueRed,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
