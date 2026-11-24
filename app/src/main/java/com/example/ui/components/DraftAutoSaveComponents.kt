package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FormDraftEntity
import org.json.JSONObject

object FormDraftTypes {
    const val UNIT = "UNIT"
    const val CUSTOMER = "CUSTOMER"
    const val TRANSACTION = "TRANSACTION"
    const val RETURN = "RETURN"

    fun displayLabel(formType: String): String = when (formType) {
        UNIT -> "Form Unit Freezer"
        CUSTOMER -> "Form Pelanggan"
        TRANSACTION -> "Form Transaksi Sewa"
        RETURN -> "Form Pengembalian Unit"
        else -> "Draft Form"
    }

    fun badgeColor(formType: String): Color = when (formType) {
        UNIT -> Color(0xFF0284C7)
        CUSTOMER -> Color(0xFF0D9488)
        TRANSACTION -> Color(0xFF7C3AED)
        RETURN -> Color(0xFFD97706)
        else -> Color(0xFF475569)
    }
}

object FormDraftCodec {
    fun parseJsonObject(json: String): JSONObject {
        return runCatching {
            if (json.isBlank()) JSONObject() else JSONObject(json)
        }.getOrElse { JSONObject() }
    }

    fun encodeMap(entries: Map<String, Any?>): String {
        val obj = JSONObject()
        entries.forEach { (k, v) ->
            when (v) {
                null -> obj.put(k, "")
                is Boolean, is Int, is Long, is Double, is String -> obj.put(k, v)
                else -> obj.put(k, v.toString())
            }
        }
        return obj.toString()
    }
}

@Composable
fun DraftAutoSaveStatusPill(
    savedAtDisplay: String,
    modifier: Modifier = Modifier
) {
    if (savedAtDisplay.isBlank()) return
    Surface(
        modifier = modifier.testTag("pill_draft_autosaved"),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFECFDF5),
        border = BorderStroke(1.dp, Color(0xFFA7F3D0))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = "Status draft otomatis",
                tint = Color(0xFF059669),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "Draft tersimpan lokal • $savedAtDisplay",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF065F46),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ResumeDraftBanner(
    draft: FormDraftEntity,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("banner_resume_draft_${draft.formType}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = BorderStroke(1.dp, Color(0xFFFCD34D))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Lanjutkan draft tersimpan",
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Draft Belum Selesai Ditemukan",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        text = buildString {
                            append(draft.title.ifBlank { FormDraftTypes.displayLabel(draft.formType) })
                            if (draft.updatedAtDisplay.isNotBlank()) {
                                append(" • Disimpan ")
                                append(draft.updatedAtDisplay)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF78350F),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (draft.subtitle.isNotBlank()) {
                Text(
                    text = draft.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF92400E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_resume_draft"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Lanjutkan Draft", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.testTag("btn_discard_draft"),
                    border = BorderStroke(1.dp, Color(0xFFD97706)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus draft",
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Hapus", color = Color(0xFFB45309), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun FormDraftsManagerDialog(
    drafts: List<FormDraftEntity>,
    onResumeDraft: (FormDraftEntity) -> Unit,
    onDeleteDraft: (FormDraftEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = "Daftar Draft",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Daftar Draft Tersimpan (${drafts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Draft tersimpan aman di lokal & tidak masuk data aktif sebelum disimpan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            if (drafts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Belum ada draft yang tersimpan",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Saat Anda mulai mengisi form Unit, Pelanggan, Transaksi, atau Pengembalian dan belum menyelesaikannya, draft akan otomatis tersimpan di sini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(drafts, key = { it.uuid }) { draft ->
                        val badgeColor = FormDraftTypes.badgeColor(draft.formType)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("draft_item_${draft.uuid}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = FormDraftTypes.displayLabel(draft.formType),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudOff,
                                            contentDescription = "Khusus lokal",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "v${draft.version} • Lokal",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = draft.title.ifBlank { FormDraftTypes.displayLabel(draft.formType) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (draft.subtitle.isNotBlank()) {
                                    Text(
                                        text = draft.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = buildString {
                                        append("Diperbarui: ${draft.updatedAtDisplay.ifBlank { "-" }}")
                                        if (draft.ownerAccount.isNotBlank()) {
                                            append(" • Akun: ${draft.ownerAccount}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { onResumeDraft(draft) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_open_draft_${draft.uuid}"),
                                        shape = RoundedCornerShape(9.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Lanjutkan", fontSize = 12.sp)
                                    }
                                    IconButton(
                                        onClick = { onDeleteDraft(draft) },
                                        modifier = Modifier.testTag("btn_delete_draft_${draft.uuid}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus draft",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_drafts_dialog")
            ) {
                Text("Tutup")
            }
        }
    )
}
