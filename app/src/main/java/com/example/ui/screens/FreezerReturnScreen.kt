package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.CustomerEntity
import com.example.data.local.FormDraftEntity
import com.example.data.local.FreezerReturnEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.pdf.InvoicePdfGenerator
import com.example.pdf.ReturnPdfGenerator
import com.example.ui.components.DigitalSignaturePad
import com.example.ui.components.DraftAutoSaveStatusPill
import com.example.ui.components.FormDraftCodec
import com.example.ui.components.FormDraftTypes
import com.example.ui.components.FreezerUnitThumbnail
import com.example.ui.components.InputTextCaseMode
import com.example.ui.components.PhotoDocumentationSlot
import com.example.ui.components.ResumeDraftBanner
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.components.FormValidationAlertBanner
import com.example.ui.components.SmartCaseOutlinedTextField
import com.example.util.FormValidationEngine
import com.example.util.FormValidationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusOverdueRedBg
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPaidGreenBg
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.StatusWarningYellowBg
import com.example.ui.theme.TextDateAccent
import com.example.ui.theme.appFilterChipColors
import com.example.util.DateCurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

val KONDISI_UNIT_KEMBALI_OPTIONS = listOf(
    "Baik / Normal",
    "Baik dengan catatan",
    "Rusak ringan",
    "Rusak berat",
    "Perlu pemeriksaan teknisi"
)

/**
 * Section 3, 4, 5 & 7: Menu Serah Terima Pengembalian Freezer & Riwayat Pengembalian.
 * Directly integrated with customers, rental_transactions, and freezer_units by ID.
 */
@Composable
fun FreezerReturnScreen(
    transactions: List<RentalTransactionEntity>,
    customers: List<CustomerEntity>,
    units: List<FreezerUnitEntity>,
    returnsHistory: List<FreezerReturnEntity>,
    loggedInOfficerName: String = "",
    formDrafts: List<FormDraftEntity> = emptyList(),
    pendingDraftToOpen: FormDraftEntity? = null,
    onPendingDraftConsumed: () -> Unit = {},
    onSaveDraft: (String, Long, String, String, String, String, (FormDraftEntity) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteDraft: (String) -> Unit = {},
    onSubmitReturn: (FreezerReturnEntity, (FreezerReturnEntity, File) -> Unit) -> Unit,
    onGenerateReturnPdf: (FreezerReturnEntity, (File) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Form Pengembalian (Sewa Aktif), 1 = Riwayat Pengembalian
    var searchQuery by remember { mutableStateOf("") }

    var activeTxForReturn by remember { mutableStateOf<RentalTransactionEntity?>(null) }
    var activeReturnDraftToApply by remember { mutableStateOf<FormDraftEntity?>(null) }
    var viewingReturnPdfState by remember { mutableStateOf<Pair<FreezerReturnEntity, File>?>(null) }

    LaunchedEffect(pendingDraftToOpen) {
        if (pendingDraftToOpen != null && pendingDraftToOpen.formType == FormDraftTypes.RETURN) {
            val targetTx = transactions.firstOrNull { it.id == pendingDraftToOpen.targetEntityId }
            if (targetTx != null) {
                activeTxForReturn = targetTx
                activeReturnDraftToApply = pendingDraftToOpen
            }
            onPendingDraftConsumed()
        }
    }

    val customerMap = remember(customers) { customers.associateBy { it.id } }
    val unitMap = remember(units) { units.associateBy { it.id } }

    // Active rentals eligible for return
    val activeRentals = remember(transactions, customerMap, unitMap, searchQuery) {
        transactions.filter { tx ->
            val isActive = tx.statusPenyewaan == "Dalam Penyewaan" || tx.statusPenyewaan == "Booking"
            val cust = customerMap[tx.customerId]
            val matchQuery = searchQuery.isBlank() ||
                tx.namaPelangganSnapshot.contains(searchQuery, ignoreCase = true) ||
                tx.nomorInvoice.contains(searchQuery, ignoreCase = true) ||
                tx.kodeUnitSnapshot.contains(searchQuery, ignoreCase = true) ||
                tx.namaUnitSnapshot.contains(searchQuery, ignoreCase = true) ||
                (cust?.nik?.contains(searchQuery, ignoreCase = true) == true)
            isActive && matchQuery
        }
    }

    // Return history searchable by Nama Pelanggan, Nomor Transaksi, Kode Unit, Tanggal Pengembalian
    val filteredReturnHistory = remember(returnsHistory, searchQuery) {
        returnsHistory.filter { ret ->
            searchQuery.isBlank() ||
                ret.namaPelangganSnapshot.contains(searchQuery, ignoreCase = true) ||
                ret.nomorInvoiceSnapshot.contains(searchQuery, ignoreCase = true) ||
                ret.nomorDokumenPengembalian.contains(searchQuery, ignoreCase = true) ||
                ret.kodeUnitSnapshot.contains(searchQuery, ignoreCase = true) ||
                ret.tanggalPengembalian.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("freezer_return_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Header Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Default.AssignmentReturn,
                    contentDescription = null,
                    tint = LightBlueAccent,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SERAH TERIMA PENGEMBALIAN FREEZER",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dokumentasi Pengembalian, Tanda Tangan Digital, Status Unit & Bukti PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightBlueAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Submenu Switcher: Form Pengembalian vs Riwayat Pengembalian
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                label = {
                    Text(
                        "Form Pengembalian (${activeRentals.size} Sewa Aktif)",
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.AssignmentReturn, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = appFilterChipColors(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_form_pengembalian")
            )

            FilterChip(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                label = {
                    Text(
                        "Riwayat Pengembalian (${returnsHistory.size})",
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = appFilterChipColors(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_riwayat_pengembalian")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SmartCaseOutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            caseMode = InputTextCaseMode.UPPERCASE,
            placeholder = {
                Text(
                    if (selectedSubTab == 0) {
                        "Cari Nama Pelanggan, Nomor Transaksi, atau Kode Unit..."
                    } else {
                        "Cari Nama Pelanggan, No Transaksi, Kode Unit, atau Tgl Pengembalian..."
                    }
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_freezer_return_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedSubTab == 0) {
            if (activeRentals.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Tidak ada transaksi aktif yang menunggu pengembalian sesuai pencarian.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(activeRentals, key = { it.id }) { tx ->
                        val cust = customerMap[tx.customerId]
                        val unit = unitMap[tx.unitId]
                        ActiveRentalReturnCard(
                            tx = tx,
                            customer = cust,
                            unit = unit,
                            onProcessReturn = { activeTxForReturn = tx }
                        )
                    }
                }
            }
        } else {
            if (filteredReturnHistory.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Belum ada riwayat pengembalian freezer yang tercatat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredReturnHistory, key = { it.id }) { ret ->
                        ReturnHistoryCard(
                            returnEntity = ret,
                            onViewPdf = {
                                onGenerateReturnPdf(ret) { file ->
                                    viewingReturnPdfState = ret to file
                                }
                            },
                            onShareWhatsApp = {
                                onGenerateReturnPdf(ret) { file ->
                                    ReturnPdfGenerator.shareReturnPdfViaWhatsApp(context, file, ret)
                                }
                            },
                            onPrintPdf = {
                                onGenerateReturnPdf(ret) { file ->
                                    InvoicePdfGenerator.printInvoicePdf(
                                        context,
                                        file,
                                        "Bukti_Pengembalian_${ret.nomorDokumenPengembalian}"
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog Form Serah Terima Pengembalian Freezer
    if (activeTxForReturn != null) {
        val tx = activeTxForReturn!!
        val cust = customerMap[tx.customerId]
        val unit = unitMap[tx.unitId]
        val matchingReturnDraft = remember(formDrafts, tx.id, activeReturnDraftToApply) {
            activeReturnDraftToApply ?: formDrafts.firstOrNull {
                it.formType == FormDraftTypes.RETURN && it.targetEntityId == tx.id
            }
        }
        FreezerReturnFormDialog(
            tx = tx,
            customer = cust,
            unit = unit,
            loggedInOfficerName = loggedInOfficerName,
            availableDraft = matchingReturnDraft,
            autoApplyDraft = activeReturnDraftToApply != null,
            onDismiss = {
                activeReturnDraftToApply = null
                activeTxForReturn = null
            },
            onAutoSaveDraft = onSaveDraft,
            onDiscardDraft = onDeleteDraft,
            onSaveReturn = { draftReturn, draftUuid ->
                onSubmitReturn(draftReturn) { savedReturn, pdfFile ->
                    if (draftUuid.isNotBlank()) onDeleteDraft(draftUuid)
                    activeReturnDraftToApply = null
                    activeTxForReturn = null
                    selectedSubTab = 1
                    viewingReturnPdfState = savedReturn to pdfFile
                }
            }
        )
    }

    // In-App PDF Viewer Dialog for Bukti Serah Terima Pengembalian Freezer
    if (viewingReturnPdfState != null) {
        val (retEntity, pdfFile) = viewingReturnPdfState!!
        ReturnPdfPreviewDialog(
            returnEntity = retEntity,
            pdfFile = pdfFile,
            onDismiss = { viewingReturnPdfState = null },
            onShareWhatsApp = {
                ReturnPdfGenerator.shareReturnPdfViaWhatsApp(context, pdfFile, retEntity)
            },
            onPrintPdf = {
                InvoicePdfGenerator.printInvoicePdf(
                    context,
                    pdfFile,
                    "Bukti_Pengembalian_${retEntity.nomorDokumenPengembalian}"
                )
            }
        )
    }
}

@Composable
private fun ActiveRentalReturnCard(
    tx: RentalTransactionEntity,
    customer: CustomerEntity?,
    unit: FreezerUnitEntity?,
    onProcessReturn: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_return_card_${tx.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FreezerUnitThumbnail(
                    photoUriOrPath = tx.fotoUnitSebelumKirimUri.ifBlank { unit?.fotoUnitUri ?: "" },
                    jenisUnit = unit?.jenisUnit ?: tx.namaUnitSnapshot,
                    kapasitasLiter = unit?.kapasitasLiter ?: 200,
                    size = 54.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.nomorInvoice,
                        style = MaterialTheme.typography.labelMedium,
                        color = SeaBluePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = customer?.namaLengkap ?: tx.namaPelangganSnapshot,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Unit: ${tx.kodeUnitSnapshot} • ${unit?.merek ?: ""} ${unit?.model ?: tx.namaUnitSnapshot}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Automatically displayed borrower & unit info per Section 3
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("• NIK Pelanggan: ${customer?.nik ?: "-"} | WA: ${customer?.nomorHp ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text("• Alamat KTP: ${customer?.alamatLengkap ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text("• Mulai Sewa: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalMulaiSewa)} | Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalJatuhTempo)}", style = MaterialTheme.typography.bodySmall)
                Text("• Harga Sewa: ${DateCurrencyUtils.formatRupiah(tx.hargaSewa)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("• Alamat Penggunaan Freezer: ${tx.alamatLokasiGps.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                val gpsStr = if (tx.latitude != null && tx.longitude != null) {
                    String.format(Locale.US, "%.6f, %.6f", tx.latitude, tx.longitude)
                } else {
                    "Belum ditag"
                }
                Text("• Lokasi GPS Terakhir: $gpsStr", style = MaterialTheme.typography.bodySmall, color = SeaBluePrimary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onProcessReturn,
                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_process_return_${tx.id}")
            ) {
                Icon(Icons.Default.AssignmentReturn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("PROSES SERAH TERIMA PENGEMBALIAN", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReturnHistoryCard(
    returnEntity: FreezerReturnEntity,
    onViewPdf: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onPrintPdf: () -> Unit
) {
    val isRepair = returnEntity.statusUnitSetelahKembali.equals("Dalam Perbaikan", true)
    val badgeBg = if (isRepair) StatusOverdueRedBg else StatusPaidGreenBg
    val badgeColor = if (isRepair) StatusOverdueRed else StatusPaidGreen

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("return_history_card_${returnEntity.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${returnEntity.nomorDokumenPengembalian} • Ref: ${returnEntity.nomorInvoiceSnapshot}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeaBluePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = returnEntity.namaPelangganSnapshot,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${returnEntity.kodeUnitSnapshot} — ${returnEntity.namaUnitSnapshot}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "UNIT: ${returnEntity.statusUnitSetelahKembali.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tanggal Pengembalian: ${DateCurrencyUtils.formatDisplayDate(returnEntity.tanggalPengembalian)} (${returnEntity.jamPengembalian})",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Kondisi Unit Saat Dikembalikan: ${returnEntity.kondisiUnitSaatKembali}",
                style = MaterialTheme.typography.bodySmall,
                color = badgeColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Petugas Penerima: ${returnEntity.namaPetugasPenerima}",
                style = MaterialTheme.typography.bodySmall
            )
            if (returnEntity.catatanKondisiUnit.isNotBlank()) {
                Text(
                    text = "Catatan: ${returnEntity.catatanKondisiUnit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onViewPdf,
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("btn_view_return_pdf_${returnEntity.id}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bukti PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onShareWhatsApp,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onPrintPdf,
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cetak", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun FreezerReturnFormDialog(
    tx: RentalTransactionEntity,
    customer: CustomerEntity?,
    unit: FreezerUnitEntity?,
    loggedInOfficerName: String = "",
    availableDraft: FormDraftEntity? = null,
    autoApplyDraft: Boolean = false,
    onDismiss: () -> Unit,
    onAutoSaveDraft: (String, Long, String, String, String, String, (FormDraftEntity) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDiscardDraft: (String) -> Unit = {},
    onSaveReturn: (FreezerReturnEntity, String) -> Unit
) {
    val returnFolder = remember(tx.id) { "returns/RET_TX_${tx.id}" }

    // C. Tanggal dan Waktu
    var tanggalPengembalian by remember { mutableStateOf(DateCurrencyUtils.todayIso()) }
    var jamPengembalian by remember { mutableStateOf(DateCurrencyUtils.nowTimeDisplay()) }
    var namaPetugasPenerima by remember {
        mutableStateOf(
            loggedInOfficerName.ifBlank { tx.namaPetugas.ifBlank { "Petugas Operasional SAF SOC" } }
        )
    }

    // D. Kondisi Unit Saat Dikembalikan
    var kondisiUnit by remember { mutableStateOf(KONDISI_UNIT_KEMBALI_OPTIONS.first()) }
    var catatanKondisi by remember { mutableStateOf("") }

    // A. Foto Pengembalian (4 slot)
    var fotoKondisiKembaliUri by remember { mutableStateOf("") }
    var fotoBagianLuarUri by remember { mutableStateOf("") }
    var fotoBagianDalamUri by remember { mutableStateOf("") }
    var fotoTambahanUri by remember { mutableStateOf("") }

    // B. Tanda Tangan Digital
    var tandaTanganPelangganUri by remember { mutableStateOf("") }
    var tandaTanganPetugasUri by remember { mutableStateOf("") }

    var currentReturnDraftUuid by remember { mutableStateOf(if (autoApplyDraft) availableDraft?.uuid.orEmpty() else "") }
    var returnDraftSavedAt by remember { mutableStateOf("") }
    var showReturnResumeBanner by remember { mutableStateOf(availableDraft != null && !autoApplyDraft) }
    var returnDraftReady by remember { mutableStateOf(false) }

    fun applyReturnDraft(draft: FormDraftEntity) {
        val obj = FormDraftCodec.parseJsonObject(draft.payloadJson)
        if (obj.has("tanggalPengembalian")) tanggalPengembalian = obj.optString("tanggalPengembalian", tanggalPengembalian)
        if (obj.has("jamPengembalian")) jamPengembalian = obj.optString("jamPengembalian", jamPengembalian)
        if (obj.has("namaPetugasPenerima")) namaPetugasPenerima = obj.optString("namaPetugasPenerima", namaPetugasPenerima)
        if (obj.has("kondisiUnit")) kondisiUnit = obj.optString("kondisiUnit", kondisiUnit)
        if (obj.has("catatanKondisi")) catatanKondisi = obj.optString("catatanKondisi", catatanKondisi)
        if (obj.has("fotoKondisiKembaliUri")) fotoKondisiKembaliUri = obj.optString("fotoKondisiKembaliUri", fotoKondisiKembaliUri)
        if (obj.has("fotoBagianLuarUri")) fotoBagianLuarUri = obj.optString("fotoBagianLuarUri", fotoBagianLuarUri)
        if (obj.has("fotoBagianDalamUri")) fotoBagianDalamUri = obj.optString("fotoBagianDalamUri", fotoBagianDalamUri)
        if (obj.has("fotoTambahanUri")) fotoTambahanUri = obj.optString("fotoTambahanUri", fotoTambahanUri)
        if (obj.has("tandaTanganPelangganUri")) tandaTanganPelangganUri = obj.optString("tandaTanganPelangganUri", tandaTanganPelangganUri)
        if (obj.has("tandaTanganPetugasUri")) tandaTanganPetugasUri = obj.optString("tandaTanganPetugasUri", tandaTanganPetugasUri)
        currentReturnDraftUuid = draft.uuid
        returnDraftSavedAt = draft.updatedAtDisplay
        showReturnResumeBanner = false
    }

    LaunchedEffect(availableDraft?.uuid, autoApplyDraft) {
        if (autoApplyDraft && availableDraft != null) {
            applyReturnDraft(availableDraft)
        }
        returnDraftReady = true
    }

    val returnDraftPayload = remember(
        tanggalPengembalian, jamPengembalian, namaPetugasPenerima, kondisiUnit,
        catatanKondisi, fotoKondisiKembaliUri, fotoBagianLuarUri, fotoBagianDalamUri,
        fotoTambahanUri, tandaTanganPelangganUri, tandaTanganPetugasUri
    ) {
        FormDraftCodec.encodeMap(
            mapOf(
                "tanggalPengembalian" to tanggalPengembalian,
                "jamPengembalian" to jamPengembalian,
                "namaPetugasPenerima" to namaPetugasPenerima,
                "kondisiUnit" to kondisiUnit,
                "catatanKondisi" to catatanKondisi,
                "fotoKondisiKembaliUri" to fotoKondisiKembaliUri,
                "fotoBagianLuarUri" to fotoBagianLuarUri,
                "fotoBagianDalamUri" to fotoBagianDalamUri,
                "fotoTambahanUri" to fotoTambahanUri,
                "tandaTanganPelangganUri" to tandaTanganPelangganUri,
                "tandaTanganPetugasUri" to tandaTanganPetugasUri
            )
        )
    }

    LaunchedEffect(returnDraftPayload, returnDraftReady) {
        if (!returnDraftReady) return@LaunchedEffect
        val hasInput = catatanKondisi.isNotBlank() ||
            fotoKondisiKembaliUri.isNotBlank() ||
            tandaTanganPelangganUri.isNotBlank() ||
            tandaTanganPetugasUri.isNotBlank() ||
            kondisiUnit != KONDISI_UNIT_KEMBALI_OPTIONS.first()
        if (!hasInput) return@LaunchedEffect
        delay(650L)
        onAutoSaveDraft(
            FormDraftTypes.RETURN,
            tx.id,
            "Pengembalian ${tx.namaPelangganSnapshot}",
            "${tx.nomorInvoice} • ${tx.kodeUnitSnapshot}",
            returnDraftPayload,
            currentReturnDraftUuid
        ) { saved ->
            currentReturnDraftUuid = saved.uuid
            returnDraftSavedAt = saved.updatedAtDisplay
        }
    }

    val isDamagedCondition = kondisiUnit.contains("Rusak", ignoreCase = true) ||
        kondisiUnit.contains("teknisi", ignoreCase = true)
    val resultingUnitStatus = if (isDamagedCondition) "Dalam Perbaikan" else "Tersedia"
    var returnValidationResult by remember { mutableStateOf<FormValidationResult?>(null) }
    val returnScrollState = rememberScrollState()
    val returnScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FORM SERAH TERIMA PENGEMBALIAN FREEZER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "No. Transaksi: ${tx.nomorInvoice} • Unit: ${tx.kodeUnitSnapshot}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (returnDraftSavedAt.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            DraftAutoSaveStatusPill(savedAtDisplay = returnDraftSavedAt)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                if (showReturnResumeBanner && availableDraft != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ResumeDraftBanner(
                        draft = availableDraft,
                        onResume = { applyReturnDraft(availableDraft) },
                        onDiscard = {
                            onDiscardDraft(availableDraft.uuid)
                            showReturnResumeBanner = false
                        }
                    )
                }

                FormValidationAlertBanner(
                    validationResult = returnValidationResult,
                    modifier = Modifier.padding(top = 8.dp),
                    testTagName = "return_form_validation_banner"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(returnScrollState)
                ) {
                    // Automatic Borrower & Freezer Information Card (Section 3)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "DATA PENYEWA & UNIT FREEZER (OTOMATIS):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Text("• Nomor Transaksi: ${tx.nomorInvoice}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("• Nama Pelanggan: ${customer?.namaLengkap ?: tx.namaPelangganSnapshot}", style = MaterialTheme.typography.bodySmall)
                            Text("• NIK Pelanggan: ${customer?.nik ?: "-"}", style = MaterialTheme.typography.bodySmall)
                            Text("• Nomor WhatsApp: ${customer?.nomorHp ?: "-"}", style = MaterialTheme.typography.bodySmall)
                            Text("• Alamat KTP: ${customer?.alamatLengkap ?: "-"}", style = MaterialTheme.typography.bodySmall)
                            Text("• Kode Unit Freezer: ${tx.kodeUnitSnapshot}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("• Merek & Tipe Freezer: ${unit?.merek ?: "-"} / ${unit?.jenisUnit ?: ""} (${unit?.model ?: tx.namaUnitSnapshot})", style = MaterialTheme.typography.bodySmall)
                            Text("• Tanggal Mulai Sewa: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalMulaiSewa)}", style = MaterialTheme.typography.bodySmall)
                            Text("• Harga Sewa: ${DateCurrencyUtils.formatRupiah(tx.hargaSewa)}", style = MaterialTheme.typography.bodySmall)
                            Text("• Jadwal Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalJatuhTempo)}", style = MaterialTheme.typography.bodySmall)
                            Text("• Alamat Penggunaan Freezer: ${tx.alamatLokasiGps.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                            val gpsLast = if (tx.latitude != null && tx.longitude != null) {
                                String.format(Locale.US, "%.6f, %.6f", tx.latitude, tx.longitude)
                            } else {
                                "Belum ditag"
                            }
                            Text("• Lokasi GPS Terakhir: $gpsLast", style = MaterialTheme.typography.bodySmall, color = SeaBluePrimary)
                        }
                    }

                    // D. KONDISI UNIT SAAT DIKEMBALIKAN & STATUS OTOMATIS (Section 3D & 4)
                    HorizontalDivider()
                    Text(
                        text = "D. KONDISI UNIT SAAT DIKEMBALIKAN",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SeaBluePrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        KONDISI_UNIT_KEMBALI_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = kondisiUnit == option,
                                onClick = { kondisiUnit = option },
                                label = { Text(option) }
                            )
                        }
                    }

                    // Automatic Status Separation Banner
                    Surface(
                        color = if (isDamagedCondition) StatusOverdueRedBg else StatusPaidGreenBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isDamagedCondition) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isDamagedCondition) StatusOverdueRed else StatusPaidGreen
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "STATUS OTOMATIS SETELAH PENGEMBALIAN:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDamagedCondition) StatusOverdueRed else StatusPaidGreen
                                )
                                Text(
                                    text = "• Status Transaksi: SELESAI\n• Status Unit Freezer (${tx.kodeUnitSnapshot}): ${resultingUnitStatus.uppercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    SmartCaseOutlinedTextField(
                        value = catatanKondisi,
                        onValueChange = { catatanKondisi = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Catatan Kondisi Unit Freezer Saat Dikembalikan") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_return_condition_note")
                    )

                    // C. TANGGAL DAN WAKTU PENGEMBALIAN
                    HorizontalDivider()
                    Text(
                        text = "C. TANGGAL, WAKTU & PETUGAS PENERIMA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SeaBluePrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = tanggalPengembalian,
                            onValueChange = { tanggalPengembalian = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            label = { Text("Tanggal Pengembalian") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = jamPengembalian,
                            onValueChange = { jamPengembalian = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            label = { Text("Jam Pengembalian") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SmartCaseOutlinedTextField(
                        value = namaPetugasPenerima,
                        onValueChange = { namaPetugasPenerima = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Nama Petugas Penerima *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_return_officer_name")
                    )

                    // A. FOTO PENGEMBALIAN (4 Slot)
                    HorizontalDivider()
                    Text(
                        text = "A. FOTO DOKUMENTASI PENGEMBALIAN FREEZER",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SeaBluePrimary
                    )

                    PhotoDocumentationSlot(
                        label = "1. Foto Kondisi Freezer Saat Dikembalikan",
                        photoKey = "return_kondisi_utama",
                        transactionOrEntityFolder = returnFolder,
                        currentUriOrPath = fotoKondisiKembaliUri,
                        isMandatory = true,
                        onPhotoSaved = { fotoKondisiKembaliUri = it }
                    )
                    PhotoDocumentationSlot(
                        label = "2. Foto Bagian Luar Freezer",
                        photoKey = "return_bagian_luar",
                        transactionOrEntityFolder = returnFolder,
                        currentUriOrPath = fotoBagianLuarUri,
                        isMandatory = true,
                        onPhotoSaved = { fotoBagianLuarUri = it }
                    )
                    PhotoDocumentationSlot(
                        label = "3. Foto Bagian Dalam Freezer",
                        photoKey = "return_bagian_dalam",
                        transactionOrEntityFolder = returnFolder,
                        currentUriOrPath = fotoBagianDalamUri,
                        isMandatory = true,
                        onPhotoSaved = { fotoBagianDalamUri = it }
                    )
                    PhotoDocumentationSlot(
                        label = "4. Foto Tambahan (Jika Diperlukan)",
                        photoKey = "return_tambahan",
                        transactionOrEntityFolder = returnFolder,
                        currentUriOrPath = fotoTambahanUri,
                        isMandatory = false,
                        onPhotoSaved = { fotoTambahanUri = it }
                    )

                    // B. TANDA TANGAN DIGITAL (Pelanggan & Petugas dengan tombol Hapus & Ulangi)
                    HorizontalDivider()
                    Text(
                        text = "B. TANDA TANGAN DIGITAL SERAH TERIMA PENGEMBALIAN",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SeaBluePrimary
                    )

                    DigitalSignaturePad(
                        title = "Tanda Tangan Digital Pelanggan",
                        signerName = customer?.namaLengkap ?: tx.namaPelangganSnapshot,
                        transactionFolder = returnFolder,
                        filePrefix = "return_pelanggan",
                        existingSignaturePath = tandaTanganPelangganUri,
                        onSignatureSaved = { tandaTanganPelangganUri = it }
                    )

                    DigitalSignaturePad(
                        title = "Tanda Tangan Digital Petugas Penerima",
                        signerName = namaPetugasPenerima,
                        transactionFolder = returnFolder,
                        filePrefix = "return_petugas",
                        existingSignaturePath = tandaTanganPetugasUri,
                        onSignatureSaved = { tandaTanganPetugasUri = it }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }

                    Button(
                        onClick = {
                            val res = FormValidationEngine.validateReturnForm(
                                transactionId = tx.id,
                                tanggalPengembalian = tanggalPengembalian,
                                namaPenerimaPetugas = namaPetugasPenerima,
                                namaPenyerahPelanggan = customer?.namaLengkap ?: tx.namaPelangganSnapshot,
                                kondisiUnit = kondisiUnit
                            )
                            if (!res.isValid) {
                                returnValidationResult = res
                                returnScope.launch { returnScrollState.animateScrollTo(0) }
                                return@Button
                            }
                            returnValidationResult = null
                            val returnEntity = FreezerReturnEntity(
                                nomorDokumenPengembalian = "", // Generated automatically in repository
                                transactionId = tx.id,
                                nomorInvoiceSnapshot = tx.nomorInvoice,
                                customerId = tx.customerId,
                                namaPelangganSnapshot = customer?.namaLengkap ?: tx.namaPelangganSnapshot,
                                nikPelangganSnapshot = customer?.nik ?: "",
                                nomorWaPelangganSnapshot = customer?.nomorHp ?: "",
                                alamatKtpSnapshot = customer?.alamatLengkap ?: "",
                                unitId = tx.unitId,
                                kodeUnitSnapshot = tx.kodeUnitSnapshot,
                                namaUnitSnapshot = unit?.namaUnit ?: tx.namaUnitSnapshot,
                                merekTipeSnapshot = "${unit?.merek ?: ""} ${unit?.model ?: ""}".trim(),
                                tanggalMulaiSewaSnapshot = tx.tanggalMulaiSewa,
                                hargaSewaSnapshot = tx.hargaSewa,
                                tanggalJatuhTempoSnapshot = tx.tanggalJatuhTempo,
                                alamatPenggunaanSnapshot = tx.alamatLokasiGps,
                                latitudeTerakhir = tx.latitude,
                                longitudeTerakhir = tx.longitude,
                                tanggalPengembalian = tanggalPengembalian.trim(),
                                jamPengembalian = jamPengembalian.trim().ifBlank { DateCurrencyUtils.nowTimeDisplay() },
                                namaPetugasPenerima = namaPetugasPenerima.trim(),
                                kondisiUnitSaatKembali = kondisiUnit,
                                catatanKondisiUnit = catatanKondisi.trim(),
                                statusUnitSetelahKembali = resultingUnitStatus,
                                fotoKondisiKembaliUri = fotoKondisiKembaliUri,
                                fotoBagianLuarUri = fotoBagianLuarUri,
                                fotoBagianDalamUri = fotoBagianDalamUri,
                                fotoTambahanUri = fotoTambahanUri,
                                tandaTanganPelangganUri = tandaTanganPelangganUri,
                                tandaTanganPetugasUri = tandaTanganPetugasUri
                            )
                            onSaveReturn(returnEntity, currentReturnDraftUuid)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPaidGreen),
                        modifier = Modifier
                            .weight(2f)
                            .testTag("btn_submit_freezer_return")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SIMPAN PENGEMBALIAN & BUAT PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReturnPdfPreviewDialog(
    returnEntity: FreezerReturnEntity,
    pdfFile: File,
    onDismiss: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onPrintPdf: () -> Unit
) {
    val pageBitmaps = remember { mutableStateListOf<Bitmap>() }

    LaunchedEffect(pdfFile) {
        withContext(Dispatchers.IO) {
            try {
                if (pdfFile.exists()) {
                    val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val rendered = mutableListOf<Bitmap>()
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        rendered.add(bmp)
                    }
                    renderer.close()
                    pfd.close()
                    pageBitmaps.clear()
                    pageBitmaps.addAll(rendered)
                }
            } catch (_: Exception) {
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BUKTI SERAH TERIMA PENGEMBALIAN FREEZER",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "${returnEntity.nomorDokumenPengembalian} • Status Unit: ${returnEntity.statusUnitSetelahKembali}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    items(pageBitmaps) { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Halaman Bukti Pengembalian PDF",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(6.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onShareWhatsApp,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPaidGreen),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kirim WhatsApp", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = onPrintPdf,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cetak A4")
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("Tutup")
                    }
                }
            }
        }
    }
}
