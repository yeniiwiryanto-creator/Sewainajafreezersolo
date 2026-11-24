package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sheets.DuplicateImportPolicy
import com.example.sheets.SmartColumnMappingRegistry
import com.example.sheets.SmartImportExecutionReport
import com.example.sheets.SmartImportPreviewState
import com.example.sheets.SmartImportTarget
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.TextDescription
import com.example.ui.theme.TextInputPrimary

@Composable
fun SmartImportSubmenuPanel(
    preview: SmartImportPreviewState?,
    report: SmartImportExecutionReport?,
    isImporting: Boolean,
    onPickImportFileUri: (Uri) -> Unit,
    onChangeTargetAndSheet: (SmartImportTarget, String?) -> Unit,
    onUpdateColumnMapping: (Int, String?) -> Unit,
    onChangeDuplicatePolicy: (DuplicateImportPolicy) -> Unit,
    onExecuteImport: () -> Unit,
    onDismissPreview: () -> Unit,
    onExportErrorReportXlsx: (Uri) -> Unit,
    onExportErrorReportCsv: (Uri) -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickImportFileUri(uri)
        }
    }

    val exportErrorXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) onExportErrorReportXlsx(uri)
    }

    val exportErrorCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) onExportErrorReportCsv(uri)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("panel_smart_import")
    ) {
        Text(
            text = "4. Smart Import Multi-Format (.XLSX, .CSV, .JSON, .ZIP)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = NavyDark
        )
        Text(
            text = "Deteksi format, encoding & pemisah otomatis • Pemetaan kolom interaktif • Validasi duplikat & preview minimal 10 baris.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                filePickerLauncher.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "text/csv",
                        "text/comma-separated-values",
                        "text/plain",
                        "application/json",
                        "application/zip",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            },
            enabled = !isImporting,
            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_pick_smart_import_file")
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = "Pilih File Import", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pilih File Import (.XLSX / .CSV / .JSON / .ZIP)", fontWeight = FontWeight.Bold)
        }

        if (isImporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (preview != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0F9FF),
                border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preview.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Format: ${preview.detectedFormat.label} • Encoding: ${preview.detectedEncoding} • ${preview.totalRows} baris",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0369A1)
                            )
                        }
                        OutlinedButton(
                            onClick = onDismissPreview,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Tutup", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Sheet Selector if multiple sheets exist
                    if (preview.availableSheets.size > 1) {
                        Text("Pilih Sheet:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            preview.availableSheets.forEach { sheet ->
                                FilterChip(
                                    selected = sheet == preview.selectedSheet,
                                    onClick = { onChangeTargetAndSheet(preview.target, sheet) },
                                    label = { Text(sheet, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    // Target Entity Selector
                    Text("Target Data Tujuan:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        SmartImportTarget.entries.forEach { tgt ->
                            FilterChip(
                                selected = preview.target == tgt,
                                onClick = { onChangeTargetAndSheet(tgt, preview.selectedSheet) },
                                label = { Text(tgt.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("chip_import_target_${tgt.name}")
                            )
                        }
                    }

                    HorizontalDivider()

                    // Auto Column Mapping Editor
                    Text(
                        text = "Pemetaan Kolom Otomatis (${preview.headers.size} kolom):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    val targetFields = SmartColumnMappingRegistry.fieldsForTarget(preview.target)
                    preview.headers.forEachIndexed { colIdx, headerName ->
                        val mappedKey = preview.columnMapping[colIdx]
                        val mappedDef = targetFields.firstOrNull { it.key == mappedKey }
                        var expanded by remember(colIdx, preview.fileName, preview.target) { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = headerName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = NavyDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("btn_map_col_$colIdx")
                                ) {
                                    Text(
                                        text = mappedDef?.label ?: "Abaikan Kolom",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (mappedDef != null) SeaBluePrimary else TextDescription
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextInputPrimary, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    containerColor = PureWhite
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("— Abaikan Kolom —", color = TextDescription) },
                                        onClick = {
                                            expanded = false
                                            onUpdateColumnMapping(colIdx, null)
                                        }
                                    )
                                    targetFields.forEach { def ->
                                        DropdownMenuItem(
                                            text = { Text(def.label, color = TextInputPrimary) },
                                            onClick = {
                                                expanded = false
                                                onUpdateColumnMapping(colIdx, def.key)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (preview.missingRequiredFields.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, StatusOverdueRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusOverdueRed, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Kolom wajib belum dipetakan: ${preview.missingRequiredFields.joinToString(", ") { it.label.replace(" *", "") }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF991B1B),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Duplicate Policy Selector
                    Text("Kebijakan Duplikat Data:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        DuplicateImportPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = preview.duplicatePolicy == policy,
                                onClick = { onChangeDuplicatePolicy(policy) },
                                label = { Text(policy.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("chip_dup_${policy.name}")
                            )
                        }
                    }

                    // Preview Summary Pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        ImportSummaryPill("Total: ${preview.totalRows}", Color(0xFFE2E8F0), NavyDark)
                        ImportSummaryPill("Valid: ${preview.validCount}", Color(0xFFD1FAE5), Color(0xFF065F46))
                        ImportSummaryPill("Duplikat: ${preview.duplicateCount}", Color(0xFFFEF3C7), Color(0xFF92400E))
                        ImportSummaryPill("Diperbarui: ${preview.toUpdateCount}", Color(0xFFE0F2FE), Color(0xFF0369A1))
                        ImportSummaryPill("Invalid: ${preview.invalidCount}", Color(0xFFFEE2E2), Color(0xFF991B1B))
                    }

                    // Preview Table (at least 10 rows shown)
                    val sampleCount = preview.rowValidations.size.coerceAtMost(12)
                    Text(
                        text = "Preview Data ($sampleCount dari ${preview.totalRows} baris):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    preview.rowValidations.take(12).forEach { rowRes ->
                        val bgColor = when {
                            !rowRes.isValid -> Color(0xFFFEF2F2)
                            rowRes.isDuplicate -> Color(0xFFFFFBEB)
                            else -> Color.White
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = bgColor,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Baris #${rowRes.rowNumber} • ${rowRes.mappedValues.values.filter { it.isNotBlank() }.take(3).joinToString(" | ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = when {
                                            !rowRes.isValid -> "INVALID"
                                            rowRes.isDuplicate -> "DUPLIKAT"
                                            else -> "VALID"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = when {
                                            !rowRes.isValid -> StatusOverdueRed
                                            rowRes.isDuplicate -> Color(0xFFD97706)
                                            else -> StatusPaidGreen
                                        }
                                    )
                                }
                                if (rowRes.isDuplicate) {
                                    Text(
                                        text = "Cocok dengan: ${rowRes.duplicateMatchLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF92400E)
                                    )
                                }
                                if (rowRes.errorReasons.isNotEmpty()) {
                                    Text(
                                        text = rowRes.errorReasons.joinToString("; "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusOverdueRed
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onExecuteImport,
                        enabled = preview.canExecuteImport && !isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPaidGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_execute_smart_import")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Jalankan Import Sekarang", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (report != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (report.invalidCount == 0 && !report.wasInterrupted) Color(0xFFECFDF5) else Color(0xFFFFFBEB)
                ),
                border = BorderStroke(
                    1.dp,
                    if (report.invalidCount == 0 && !report.wasInterrupted) StatusPaidGreen.copy(alpha = 0.4f) else Color(0xFFF59E0B)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_smart_import_report")
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (report.invalidCount == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (report.invalidCount == 0) StatusPaidGreen else Color(0xFFD97706)
                        )
                        Column {
                            Text(
                                text = "Laporan Hasil Import (${report.targetLabel})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Text(report.message, style = MaterialTheme.typography.bodySmall, color = NavyDark)
                        }
                    }

                    if (report.errorRows.isNotEmpty()) {
                        Text(
                            text = "${report.errorRows.size} baris bermasalah dapat diunduh untuk diperbaiki:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = {
                                    exportErrorXlsxLauncher.launch("Laporan_Error_Import_${System.currentTimeMillis()}.xlsx")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_download_error_xlsx")
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Error Report .XLSX", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    exportErrorCsvLauncher.launch("Laporan_Error_Import_${System.currentTimeMillis()}.csv")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_download_error_csv")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Error Report .CSV", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportSummaryPill(text: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
