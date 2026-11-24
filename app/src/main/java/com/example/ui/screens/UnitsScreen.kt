package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.local.FormDraftEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.UnitRentalPriceEntity
import com.example.ui.components.DraftAutoSaveStatusPill
import com.example.ui.components.FormDraftCodec
import com.example.ui.components.FormDraftTypes
import com.example.ui.components.InputTextCaseMode
import com.example.ui.components.PhotoDocumentationSlot
import com.example.ui.components.ResumeDraftBanner
import com.example.ui.components.SmartCaseOutlinedTextField
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusActiveBlue
import com.example.ui.theme.StatusActiveBlueBg
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusOverdueRedBg
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPaidGreenBg
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.StatusWarningYellowBg
import com.example.ui.theme.TextDateAccent
import com.example.ui.theme.TextFormLabel
import com.example.ui.theme.TextSectionTitle
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.components.FieldErrorSupportingText
import com.example.ui.components.FormValidationAlertBanner
import com.example.ui.components.StorageSyncStatusBadge
import com.example.ui.theme.appFilterChipColors
import com.example.ui.theme.appOutlinedTextFieldColors
import com.example.util.DateCurrencyUtils
import com.example.util.FormValidationEngine
import com.example.util.FormValidationResult
import com.example.util.StorageSyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val JENIS_UNIT_OPTIONS = listOf(
    "Chest Freezer",
    "Showcase",
    "Freezer ASI",
    "Freezer lainnya"
)

val STATUS_UNIT_OPTIONS = listOf(
    "Tersedia",
    "Dipesan",
    "Dalam pengiriman",
    "Sedang disewa",
    "Disewa",
    "Perawatan",
    "Rusak",
    "Dikembalikan",
    "Dalam Perbaikan",
    "Maintenance",
    "Tidak Aktif",
    "Hilang"
)

val KONDISI_UNIT_OPTIONS = listOf(
    "Baik",
    "Normal",
    "Perlu Servis",
    "Rusak Ringan",
    "Rusak Berat"
)

val STATUS_INVESTASI_OPTIONS = listOf(
    "LIST",
    "DIBAYAR SEBAGIAN",
    "DONE",
    "LUNAS",
    "HILANG"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsScreen(
    units: List<FreezerUnitEntity>,
    transactions: List<RentalTransactionEntity>,
    unitRentalPrices: List<UnitRentalPriceEntity> = emptyList(),
    formDrafts: List<FormDraftEntity> = emptyList(),
    pendingDraftToOpen: FormDraftEntity? = null,
    onPendingDraftConsumed: () -> Unit = {},
    onSaveUnit: (FreezerUnitEntity) -> Unit,
    onSaveUnitWithPrices: ((FreezerUnitEntity, Map<Int, Long>) -> Unit)? = null,
    onDeleteUnit: (FreezerUnitEntity) -> Unit,
    onSaveDraft: (String, Long, String, String, String, String, (FormDraftEntity) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteDraft: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("Semua") }
    var selectedJenisFilter by remember { mutableStateOf("Semua") }

    var editingUnit by remember { mutableStateOf<FreezerUnitEntity?>(null) }
    var activeUnitDraftToApply by remember { mutableStateOf<FormDraftEntity?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingDraftToOpen) {
        if (pendingDraftToOpen != null && pendingDraftToOpen.formType == FormDraftTypes.UNIT) {
            editingUnit = if (pendingDraftToOpen.targetEntityId > 0L) {
                units.firstOrNull { it.id == pendingDraftToOpen.targetEntityId }
            } else null
            activeUnitDraftToApply = pendingDraftToOpen
            showAddEditDialog = true
            onPendingDraftConsumed()
        }
    }
    var selectedUnitForHistory by remember { mutableStateOf<FreezerUnitEntity?>(null) }

    val filteredUnits = remember(units, searchQuery, selectedStatusFilter, selectedJenisFilter) {
        units.filter { unit ->
            val matchQuery = searchQuery.isBlank() ||
                unit.namaUnit.contains(searchQuery, ignoreCase = true) ||
                unit.kodeUnit.contains(searchQuery, ignoreCase = true) ||
                unit.merek.contains(searchQuery, ignoreCase = true) ||
                unit.nomorSeri.contains(searchQuery, ignoreCase = true) ||
                unit.owner.contains(searchQuery, ignoreCase = true)
            val matchStatus = selectedStatusFilter == "Semua" || unit.statusUnit.equals(selectedStatusFilter, ignoreCase = true)
            val matchJenis = selectedJenisFilter == "Semua" || unit.jenisUnit.equals(selectedJenisFilter, ignoreCase = true)
            matchQuery && matchStatus && matchJenis
        }
    }

    var visibleLimit by remember(searchQuery, selectedStatusFilter, selectedJenisFilter) { mutableStateOf(50) }
    val pagedUnits = remember(filteredUnits, visibleLimit) { filteredUnits.take(visibleLimit) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingUnit = null
                    showAddEditDialog = true
                },
                containerColor = SeaBluePrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_unit")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Unit Freezer")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Unit", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("units_screen")
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            SmartCaseOutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                caseMode = InputTextCaseMode.UPPERCASE,
                placeholder = { Text("Cari Kode Unit, Nama, Merek, No. Seri, Owner...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari Unit") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_unit_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter by Status Unit
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                (listOf("Semua") + STATUS_UNIT_OPTIONS).forEach { st ->
                    FilterChip(
                        selected = selectedStatusFilter == st,
                        onClick = { selectedStatusFilter = st },
                        label = { Text(st, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // Filter by Jenis Unit
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                (listOf("Semua") + JENIS_UNIT_OPTIONS).forEach { jn ->
                    FilterChip(
                        selected = selectedJenisFilter == jn,
                        onClick = { selectedJenisFilter = jn },
                        label = { Text(jn, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pagedUnits, key = { it.id }) { unit ->
                    val unitHistory = transactions.filter { it.unitId == unit.id }
                    UnitCardItem(
                        unit = unit,
                        rentalCount = unitHistory.size,
                        totalRevenueFromUnit = unitHistory.sumOf { it.nominalPembayaran },
                        onClickDetail = { selectedUnitForHistory = unit },
                        onClickEdit = {
                            editingUnit = unit
                            showAddEditDialog = true
                        }
                    )
                }
                if (filteredUnits.size > pagedUnits.size) {
                    item(key = "load_more_units") {
                        OutlinedButton(
                            onClick = { visibleLimit += 50 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_load_more_units")
                        ) {
                            Text("Muat Lebih Banyak (${filteredUnits.size - pagedUnits.size} lagi)")
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        val targetId = editingUnit?.id ?: 0L
        val existingPricesForUnit = remember(unitRentalPrices, targetId) {
            if (targetId > 0L) {
                unitRentalPrices.filter { it.unitId == targetId }.associate { it.periodDays to it.price }
            } else emptyMap()
        }
        val matchingDraft = remember(formDrafts, targetId, activeUnitDraftToApply) {
            activeUnitDraftToApply ?: formDrafts.firstOrNull {
                it.formType == FormDraftTypes.UNIT && it.targetEntityId == targetId
            }
        }
        UnitFormDialog(
            initialUnit = editingUnit,
            initialPeriodPrices = existingPricesForUnit,
            availableDraft = matchingDraft,
            autoApplyDraft = activeUnitDraftToApply != null,
            nextIndex = units.size + 1,
            onDismiss = {
                activeUnitDraftToApply = null
                showAddEditDialog = false
            },
            onSave = { saved, periodPricesMap, draftUuid ->
                if (onSaveUnitWithPrices != null) {
                    onSaveUnitWithPrices(saved, periodPricesMap)
                } else {
                    onSaveUnit(saved)
                }
                if (draftUuid.isNotBlank()) onDeleteDraft(draftUuid)
                activeUnitDraftToApply = null
                showAddEditDialog = false
            },
            onDelete = { toDel ->
                onDeleteUnit(toDel)
                activeUnitDraftToApply = null
                showAddEditDialog = false
            },
            onAutoSaveDraft = onSaveDraft,
            onDiscardDraft = onDeleteDraft
        )
    }

    if (selectedUnitForHistory != null) {
        val u = selectedUnitForHistory!!
        val unitRentals = transactions.filter { it.unitId == u.id }
        UnitHistoryAndDetailDialog(
            unit = u,
            rentalHistory = unitRentals,
            onDismiss = { selectedUnitForHistory = null },
            onEdit = {
                selectedUnitForHistory = null
                editingUnit = u
                showAddEditDialog = true
            }
        )
    }
}

@Composable
private fun UnitCardItem(
    unit: FreezerUnitEntity,
    rentalCount: Int,
    totalRevenueFromUnit: Long,
    onClickDetail: () -> Unit,
    onClickEdit: () -> Unit
) {
    val (bgBadge, fgBadge) = when {
        unit.statusUnit.equals("Tersedia", true) || unit.statusUnit.equals("Dikembalikan", true) -> StatusPaidGreenBg to StatusPaidGreen
        unit.isRentedStatus() || unit.statusUnit.equals("Dipesan", true) || unit.statusUnit.equals("Dalam pengiriman", true) -> StatusActiveBlueBg to StatusActiveBlue
        unit.isMaintenanceOrDamagedStatus() -> StatusWarningYellowBg to StatusWarningYellow
        else -> StatusOverdueRedBg to StatusOverdueRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickDetail() }
            .testTag("unit_card_${unit.kodeUnit}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = SeaBluePrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = unit.kodeUnit,
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = bgBadge,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = unit.statusUnit.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = fgBadge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(onClick = onClickEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Unit", tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                com.example.ui.components.FreezerUnitThumbnail(
                    photoUriOrPath = unit.fotoUnitUri,
                    jenisUnit = unit.jenisUnit,
                    kapasitasLiter = unit.kapasitasLiter,
                    size = 72.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = unit.namaUnit,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${unit.jenisUnit} • ${unit.merek} ${unit.model} • ${unit.kapasitasLiter} Liter • Kondisi: ${unit.kondisiUnit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unit.lokasiGpsPemasangan.ifBlank { unit.lokasiUnit },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (unit.tanggalPengiriman.isNotBlank()) {
                        Text(
                            text = "Tgl Kirim: ${DateCurrencyUtils.formatDisplayDate(unit.tanggalPengiriman)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDateAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Owner: ${unit.owner} (${unit.statusInvestasi})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Harga Beli: ${DateCurrencyUtils.formatRupiah(unit.hargaPembelian)} | Cicilan: ${DateCurrencyUtils.formatRupiah(unit.cicilanPerBulan)}/bln",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$rentalCount Riwayat",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitHistoryAndDetailDialog(
    unit: FreezerUnitEntity,
    rentalHistory: List<RentalTransactionEntity>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val totalPendapatanUnit = rentalHistory.sumOf { it.nominalPembayaran }
    val sisaKewajiban = (unit.hargaPembelian - unit.totalPembayaranCicilan).coerceAtLeast(0L)
    val roiProgress = if (unit.hargaPembelian > 0) {
        ((totalPendapatanUnit.toDouble() / unit.hargaPembelian.toDouble()) * 100.0).toInt()
    } else 100

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(unit.kodeUnit, style = MaterialTheme.typography.labelMedium, color = SeaBluePrimary)
                        Text(unit.namaUnit, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        StorageSyncStatusBadge(status = StorageSyncStatus.fromEntitySyncStatus(unit.syncStatus))
                    }
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Unit")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Spesifikasi & Investasi Unit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Jenis Unit: ${unit.jenisUnit} | Kapasitas: ${unit.kapasitasLiter} Liter", style = MaterialTheme.typography.bodySmall)
                        Text("Merek / Model: ${unit.merek} ${unit.model} | No. Seri: ${unit.nomorSeri}", style = MaterialTheme.typography.bodySmall)
                        Text("Owner / Investor: ${unit.owner} (${unit.kategoriSheet})", style = MaterialTheme.typography.bodySmall)
                        Text("Harga Pembelian: ${DateCurrencyUtils.formatRupiah(unit.hargaPembelian)} (Tgl: ${unit.tanggalPembelian})", style = MaterialTheme.typography.bodySmall)
                        Text("Cicilan: ${DateCurrencyUtils.formatRupiah(unit.cicilanPerBulan)} x ${unit.jumlahCicilanTotal} bln | Dibayar: ${DateCurrencyUtils.formatRupiah(unit.totalPembayaranCicilan)}", style = MaterialTheme.typography.bodySmall)
                        Text("Sisa Kewajiban Cicilan: ${DateCurrencyUtils.formatRupiah(sisaKewajiban)} | Status: ${unit.statusInvestasi}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Total Pendapatan Sewa Sepanjang Waktu: ${DateCurrencyUtils.formatRupiah(totalPendapatanUnit)} (Capai $roiProgress% ROI)", style = MaterialTheme.typography.bodySmall, color = StatusPaidGreen, fontWeight = FontWeight.Bold)
                        Text("Lokasi Saat Ini: ${unit.lokasiUnit}", style = MaterialTheme.typography.bodySmall)
                        Text("Kondisi Unit: ${unit.kondisiUnit} • Status: ${unit.statusUnit}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        if (unit.tanggalPengiriman.isNotBlank()) {
                            Text("Tanggal Pengiriman: ${DateCurrencyUtils.formatDisplayDate(unit.tanggalPengiriman)}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (unit.lokasiGpsPemasangan.isNotBlank()) {
                            Text("Lokasi GPS Pemasangan: ${unit.lokasiGpsPemasangan}", style = MaterialTheme.typography.bodySmall, color = SeaBluePrimary)
                        }
                        if (unit.keterangan.isNotBlank()) {
                            Text("Keterangan: ${unit.keterangan}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "RIWAYAT PENYEWAAN UNIT INI (${rentalHistory.size} Transaksi)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (rentalHistory.isEmpty()) {
                    Text(
                        text = "Belum ada riwayat transaksi penyewaan untuk unit ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    rentalHistory.forEach { tx ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(tx.nomorInvoice, style = MaterialTheme.typography.labelSmall, color = SeaBluePrimary, fontWeight = FontWeight.Bold)
                                    Text(tx.statusPenyewaan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                Text(tx.namaPelangganSnapshot, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Periode: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalMulaiSewa)} s/d ${DateCurrencyUtils.formatDisplayDate(tx.tanggalAkhirSewa)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Tagihan: ${DateCurrencyUtils.formatRupiah(tx.totalTagihan)} | Dibayar: ${DateCurrencyUtils.formatRupiah(tx.nominalPembayaran)} (${tx.statusPembayaran})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (tx.alamatLokasiGps.isNotBlank()) {
                                    Text("Lokasi: ${tx.alamatLokasiGps}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitFormDialog(
    initialUnit: FreezerUnitEntity?,
    initialPeriodPrices: Map<Int, Long> = emptyMap(),
    availableDraft: FormDraftEntity? = null,
    autoApplyDraft: Boolean = false,
    nextIndex: Int,
    onDismiss: () -> Unit,
    onSave: (FreezerUnitEntity, Map<Int, Long>, String) -> Unit,
    onDelete: (FreezerUnitEntity) -> Unit,
    onAutoSaveDraft: (String, Long, String, String, String, String, (FormDraftEntity) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDiscardDraft: (String) -> Unit = {}
) {
    var kodeUnit by remember { mutableStateOf(initialUnit?.kodeUnit ?: String.format("SAF-CF-%03d", nextIndex)) }
    var namaUnit by remember { mutableStateOf(initialUnit?.namaUnit ?: "") }
    var jenisUnit by remember { mutableStateOf(initialUnit?.jenisUnit ?: "Chest Freezer") }
    var owner by remember { mutableStateOf(initialUnit?.owner ?: "Investasi Internal SAF") }
    var hargaPembelian by remember { mutableStateOf(initialUnit?.hargaPembelian?.toString() ?: "3200000") }
    var kapasitasLiter by remember { mutableStateOf(initialUnit?.kapasitasLiter?.toString() ?: "200") }
    // Preserve existing legacy installment columns in DB (hidden from Unit form UI per Section D.4)
    val preservedCicilanPerBulan = remember(initialUnit) { initialUnit?.cicilanPerBulan ?: 0L }
    val preservedJumlahCicilanTotal = remember(initialUnit) { initialUnit?.jumlahCicilanTotal ?: 12 }
    val preservedTotalPembayaranCicilan = remember(initialUnit) { initialUnit?.totalPembayaranCicilan ?: 0L }
    var statusInvestasi by remember { mutableStateOf(initialUnit?.statusInvestasi ?: "LIST") }
    var kategoriSheet by remember { mutableStateOf(initialUnit?.kategoriSheet ?: "INVESTASI SEWA FREEZER") }
    var merek by remember { mutableStateOf(initialUnit?.merek ?: "RSA") }
    var model by remember { mutableStateOf(initialUnit?.model ?: "CF-210") }
    var nomorSeri by remember { mutableStateOf(initialUnit?.nomorSeri ?: "SN-SAF-${1000 + nextIndex}") }
    var statusUnit by remember { mutableStateOf(initialUnit?.statusUnit ?: "Tersedia") }
    var kondisiUnit by remember { mutableStateOf(initialUnit?.kondisiUnit ?: "Baik") }
    var lokasiUnit by remember { mutableStateOf(initialUnit?.lokasiUnit ?: "Gudang Utama SAF Solo") }
    var tanggalPengiriman by remember { mutableStateOf(initialUnit?.tanggalPengiriman ?: "") }
    var lokasiGpsPemasangan by remember { mutableStateOf(initialUnit?.lokasiGpsPemasangan ?: "") }
    var tanggalPembelian by remember { mutableStateOf(initialUnit?.tanggalPembelian ?: DateCurrencyUtils.todayIso()) }
    var keterangan by remember { mutableStateOf(initialUnit?.keterangan ?: "") }

    // Section D.2: DAFTAR HARGA SEWA FREEZER per periode (opsional: 3, 7, 14, 30, 60, 90 hari)
    var price3Days by remember { mutableStateOf(initialPeriodPrices[3]?.takeIf { it > 0L }?.toString() ?: "") }
    var price7Days by remember { mutableStateOf(initialPeriodPrices[7]?.takeIf { it > 0L }?.toString() ?: "") }
    var price14Days by remember { mutableStateOf(initialPeriodPrices[14]?.takeIf { it > 0L }?.toString() ?: "") }
    var price30Days by remember { mutableStateOf(initialPeriodPrices[30]?.takeIf { it > 0L }?.toString() ?: "") }
    var price60Days by remember { mutableStateOf(initialPeriodPrices[60]?.takeIf { it > 0L }?.toString() ?: "") }
    var price90Days by remember { mutableStateOf(initialPeriodPrices[90]?.takeIf { it > 0L }?.toString() ?: "") }

    // Section D.1: Simplified Unit Photo Documentation — ONLY 1 Slot: Foto Kondisi Fisik Freezer
    // Legacy photos (fotoBagianDalamUri, fotoMerekSeriUri) are preserved non-destructively on edit.
    var fotoKondisiFisikUri by remember {
        mutableStateOf(
            initialUnit?.fotoKondisiFisikUri?.ifBlank { initialUnit.fotoUnitUri } ?: ""
        )
    }
    val preservedFotoBagianDalamUri = remember(initialUnit) { initialUnit?.fotoBagianDalamUri ?: "" }
    val preservedFotoMerekSeriUri = remember(initialUnit) { initialUnit?.fotoMerekSeriUri ?: "" }

    // Section B: Auto-Draft state
    var currentDraftUuid by remember { mutableStateOf(if (autoApplyDraft) availableDraft?.uuid.orEmpty() else "") }
    var draftSavedAtDisplay by remember { mutableStateOf("") }
    var showResumeBanner by remember { mutableStateOf(availableDraft != null && !autoApplyDraft) }
    var draftInitialized by remember { mutableStateOf(false) }
    var unitValidationResult by remember { mutableStateOf<FormValidationResult?>(null) }
    val unitFormScrollState = rememberScrollState()
    val unitScope = rememberCoroutineScope()

    fun applyDraftPayload(draft: FormDraftEntity) {
        val obj = FormDraftCodec.parseJsonObject(draft.payloadJson)
        if (obj.has("kodeUnit")) kodeUnit = obj.optString("kodeUnit", kodeUnit)
        if (obj.has("namaUnit")) namaUnit = obj.optString("namaUnit", namaUnit)
        if (obj.has("jenisUnit")) jenisUnit = obj.optString("jenisUnit", jenisUnit)
        if (obj.has("owner")) owner = obj.optString("owner", owner)
        if (obj.has("hargaPembelian")) hargaPembelian = obj.optString("hargaPembelian", hargaPembelian)
        if (obj.has("kapasitasLiter")) kapasitasLiter = obj.optString("kapasitasLiter", kapasitasLiter)
        if (obj.has("statusInvestasi")) statusInvestasi = obj.optString("statusInvestasi", statusInvestasi)
        if (obj.has("kategoriSheet")) kategoriSheet = obj.optString("kategoriSheet", kategoriSheet)
        if (obj.has("merek")) merek = obj.optString("merek", merek)
        if (obj.has("model")) model = obj.optString("model", model)
        if (obj.has("nomorSeri")) nomorSeri = obj.optString("nomorSeri", nomorSeri)
        if (obj.has("statusUnit")) statusUnit = obj.optString("statusUnit", statusUnit)
        if (obj.has("kondisiUnit")) kondisiUnit = obj.optString("kondisiUnit", kondisiUnit)
        if (obj.has("lokasiUnit")) lokasiUnit = obj.optString("lokasiUnit", lokasiUnit)
        if (obj.has("tanggalPengiriman")) tanggalPengiriman = obj.optString("tanggalPengiriman", tanggalPengiriman)
        if (obj.has("lokasiGpsPemasangan")) lokasiGpsPemasangan = obj.optString("lokasiGpsPemasangan", lokasiGpsPemasangan)
        if (obj.has("tanggalPembelian")) tanggalPembelian = obj.optString("tanggalPembelian", tanggalPembelian)
        if (obj.has("keterangan")) keterangan = obj.optString("keterangan", keterangan)
        if (obj.has("fotoKondisiFisikUri")) fotoKondisiFisikUri = obj.optString("fotoKondisiFisikUri", fotoKondisiFisikUri)
        if (obj.has("price3Days")) price3Days = obj.optString("price3Days", price3Days)
        if (obj.has("price7Days")) price7Days = obj.optString("price7Days", price7Days)
        if (obj.has("price14Days")) price14Days = obj.optString("price14Days", price14Days)
        if (obj.has("price30Days")) price30Days = obj.optString("price30Days", price30Days)
        if (obj.has("price60Days")) price60Days = obj.optString("price60Days", price60Days)
        if (obj.has("price90Days")) price90Days = obj.optString("price90Days", price90Days)
        currentDraftUuid = draft.uuid
        draftSavedAtDisplay = draft.updatedAtDisplay
        showResumeBanner = false
    }

    LaunchedEffect(availableDraft?.uuid, autoApplyDraft) {
        if (autoApplyDraft && availableDraft != null) {
            applyDraftPayload(availableDraft)
        }
        draftInitialized = true
    }

    // Debounced auto-save draft whenever any unit input changes
    val draftSnapshotJson = remember(
        kodeUnit, namaUnit, jenisUnit, owner, hargaPembelian, kapasitasLiter,
        statusInvestasi, kategoriSheet, merek, model, nomorSeri, statusUnit,
        kondisiUnit, lokasiUnit, tanggalPengiriman, lokasiGpsPemasangan,
        tanggalPembelian, keterangan, fotoKondisiFisikUri,
        price3Days, price7Days, price14Days, price30Days, price60Days, price90Days
    ) {
        FormDraftCodec.encodeMap(
            mapOf(
                "kodeUnit" to kodeUnit,
                "namaUnit" to namaUnit,
                "jenisUnit" to jenisUnit,
                "owner" to owner,
                "hargaPembelian" to hargaPembelian,
                "kapasitasLiter" to kapasitasLiter,
                "statusInvestasi" to statusInvestasi,
                "kategoriSheet" to kategoriSheet,
                "merek" to merek,
                "model" to model,
                "nomorSeri" to nomorSeri,
                "statusUnit" to statusUnit,
                "kondisiUnit" to kondisiUnit,
                "lokasiUnit" to lokasiUnit,
                "tanggalPengiriman" to tanggalPengiriman,
                "lokasiGpsPemasangan" to lokasiGpsPemasangan,
                "tanggalPembelian" to tanggalPembelian,
                "keterangan" to keterangan,
                "fotoKondisiFisikUri" to fotoKondisiFisikUri,
                "price3Days" to price3Days,
                "price7Days" to price7Days,
                "price14Days" to price14Days,
                "price30Days" to price30Days,
                "price60Days" to price60Days,
                "price90Days" to price90Days
            )
        )
    }

    LaunchedEffect(draftSnapshotJson, draftInitialized) {
        if (!draftInitialized) return@LaunchedEffect
        val hasMeaningfulInput = namaUnit.isNotBlank() ||
            keterangan.isNotBlank() ||
            fotoKondisiFisikUri.isNotBlank() ||
            listOf(price3Days, price7Days, price14Days, price30Days, price60Days, price90Days).any { it.isNotBlank() }
        if (!hasMeaningfulInput) return@LaunchedEffect
        delay(650L)
        onAutoSaveDraft(
            FormDraftTypes.UNIT,
            initialUnit?.id ?: 0L,
            namaUnit.ifBlank { kodeUnit.ifBlank { "Draft Unit Freezer" } },
            "$jenisUnit • $merek $model • ${kapasitasLiter}L",
            draftSnapshotJson,
            currentDraftUuid
        ) { savedDraft ->
            currentDraftUuid = savedDraft.uuid
            draftSavedAtDisplay = savedDraft.updatedAtDisplay
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
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
                            text = if (initialUnit == null) "Tambah Unit Freezer Baru" else "Edit Data Unit Freezer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (draftSavedAtDisplay.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            DraftAutoSaveStatusPill(savedAtDisplay = draftSavedAtDisplay)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                if (showResumeBanner && availableDraft != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ResumeDraftBanner(
                        draft = availableDraft,
                        onResume = { applyDraftPayload(availableDraft) },
                        onDiscard = {
                            onDiscardDraft(availableDraft.uuid)
                            showResumeBanner = false
                        }
                    )
                }

                FormValidationAlertBanner(
                    validationResult = unitValidationResult,
                    modifier = Modifier.padding(top = 8.dp),
                    testTagName = "unit_form_validation_banner"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(unitFormScrollState)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmartCaseOutlinedTextField(
                            value = kodeUnit,
                            onValueChange = { kodeUnit = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Kode Unit") },
                            modifier = Modifier.weight(1.1f)
                        )
                        OutlinedButton(
                            onClick = { kodeUnit = String.format("SAF-CF-%03d", nextIndex) },
                            modifier = Modifier.testTag("btn_auto_unit_code")
                        ) {
                            Text("Auto Kode", style = MaterialTheme.typography.labelSmall)
                        }
                        SmartCaseOutlinedTextField(
                            value = kapasitasLiter,
                            onValueChange = {
                                kapasitasLiter = it.filter { c -> c.isDigit() }
                                if (unitValidationResult != null) unitValidationResult = null
                            },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Liter *") },
                            isError = unitValidationResult?.fieldErrors?.containsKey("kapasitasLiter") == true,
                            supportingText = { FieldErrorSupportingText(unitValidationResult?.fieldErrors?.get("kapasitasLiter")) },
                            modifier = Modifier.weight(0.75f)
                        )
                    }

                    SmartCaseOutlinedTextField(
                        value = namaUnit,
                        onValueChange = {
                            namaUnit = it
                            if (unitValidationResult != null) unitValidationResult = null
                        },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Nama Unit Freezer *") },
                        isError = unitValidationResult?.fieldErrors?.containsKey("namaUnit") == true,
                        supportingText = { FieldErrorSupportingText(unitValidationResult?.fieldErrors?.get("namaUnit")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_unit_name")
                    )

                    Text("Jenis Unit Freezer:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        JENIS_UNIT_OPTIONS.forEach { jn ->
                            FilterChip(
                                selected = jenisUnit == jn,
                                onClick = { jenisUnit = jn },
                                label = { Text(jn) }
                            )
                        }
                    }

                    Text("Status Operasional Unit:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        STATUS_UNIT_OPTIONS.forEach { st ->
                            FilterChip(
                                selected = statusUnit == st,
                                onClick = { statusUnit = st },
                                label = { Text(st) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = merek,
                            onValueChange = {
                                merek = it
                                if (unitValidationResult != null) unitValidationResult = null
                            },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Merek *") },
                            isError = unitValidationResult?.fieldErrors?.containsKey("merek") == true,
                            supportingText = { FieldErrorSupportingText(unitValidationResult?.fieldErrors?.get("merek")) },
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = model,
                            onValueChange = {
                                model = it
                                if (unitValidationResult != null) unitValidationResult = null
                            },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Model / Tipe *") },
                            isError = unitValidationResult?.fieldErrors?.containsKey("model") == true,
                            supportingText = { FieldErrorSupportingText(unitValidationResult?.fieldErrors?.get("model")) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = nomorSeri,
                            onValueChange = {
                                nomorSeri = it
                                if (unitValidationResult != null) unitValidationResult = null
                            },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Nomor Seri *") },
                            isError = unitValidationResult?.fieldErrors?.containsKey("nomorSeri") == true,
                            supportingText = { FieldErrorSupportingText(unitValidationResult?.fieldErrors?.get("nomorSeri")) },
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = tanggalPembelian,
                            onValueChange = { tanggalPembelian = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            label = { Text("Tgl Beli (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Kondisi Unit Freezer:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        KONDISI_UNIT_OPTIONS.forEach { cond ->
                            FilterChip(
                                selected = kondisiUnit == cond,
                                onClick = { kondisiUnit = cond },
                                label = { Text(cond) }
                            )
                        }
                    }

                    SmartCaseOutlinedTextField(
                        value = lokasiUnit,
                        onValueChange = { lokasiUnit = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Lokasi Unit Saat Ini") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = tanggalPengiriman,
                            onValueChange = { tanggalPengiriman = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            label = { Text("Tgl Kirim (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = lokasiGpsPemasangan,
                            onValueChange = { lokasiGpsPemasangan = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Lokasi GPS Pemasangan") },
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Data Kepemilikan & Investasi Freezer (Section 9 & 10)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    SmartCaseOutlinedTextField(
                        value = owner,
                        onValueChange = { owner = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Owner / Nama Pemilik Investasi") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("INVESTASI SEWA FREEZER", "INVESTASI FREEZER POINT FROZEN").forEach { kat ->
                            FilterChip(
                                selected = kategoriSheet == kat,
                                onClick = { kategoriSheet = kat },
                                label = { Text(kat, style = MaterialTheme.typography.labelSmall) },
                                colors = appFilterChipColors()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = hargaPembelian,
                        onValueChange = { hargaPembelian = it.filter { c -> c.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Harga Pembelian Unit (Rp)") },
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Status Investasi (Point Frozen / Mitra):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextFormLabel)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        STATUS_INVESTASI_OPTIONS.forEach { stInv ->
                            FilterChip(
                                selected = statusInvestasi == stInv,
                                onClick = { statusInvestasi = stInv },
                                label = { Text(stInv) },
                                colors = appFilterChipColors()
                            )
                        }
                    }

                    // Section D.2: DAFTAR HARGA SEWA FREEZER (Opsional per periode per unit)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("section_daftar_harga_sewa_freezer")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "DAFTAR HARGA SEWA FREEZER",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextSectionTitle
                            )
                            Text(
                                text = "Atur harga sewa per periode khusus unit ini (opsional). Periode yang dikosongkan tidak akan muncul sebagai pilihan pada transaksi Sewa Reguler.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = price3Days,
                                    onValueChange = { price3Days = it.filter { c -> c.isDigit() } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("Sewa 3 Hari (Rp)") },
                                    placeholder = { Text("Opsional") },
                                    singleLine = true,
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_unit_price_3d")
                                )
                                OutlinedTextField(
                                    value = price7Days,
                                    onValueChange = { price7Days = it.filter { c -> c.isDigit() } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("Sewa 7 Hari (Rp)") },
                                    placeholder = { Text("Opsional") },
                                    singleLine = true,
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_unit_price_7d")
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = price14Days,
                                    onValueChange = { price14Days = it.filter { c -> c.isDigit() } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("Sewa 14 Hari (Rp)") },
                                    placeholder = { Text("Opsional") },
                                    singleLine = true,
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_unit_price_14d")
                                )
                                OutlinedTextField(
                                    value = price30Days,
                                    onValueChange = { price30Days = it.filter { c -> c.isDigit() } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("Sewa 30 Hari (Rp)") },
                                    placeholder = { Text("Opsional") },
                                    singleLine = true,
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_unit_price_30d")
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = price60Days,
                                    onValueChange = { price60Days = it.filter { c -> c.isDigit() } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("Sewa 60 Hari (Rp)") },
                                    placeholder = { Text("Opsional") },
                                    singleLine = true,
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_unit_price_60d")
                                )
                                OutlinedTextField(
                                    value = price90Days,
                                    onValueChange = { price90Days = it.filter { c -> c.isDigit() } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("Sewa 90 Hari (Rp)") },
                                    placeholder = { Text("Opsional") },
                                    singleLine = true,
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_unit_price_90d")
                                )
                            }
                        }
                    }

                    SmartCaseOutlinedTextField(
                        value = keterangan,
                        onValueChange = { keterangan = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Keterangan Kondisi & Catatan Unit") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Dokumentasi Foto Unit Freezer (Section 4B)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Unggah 1 foto kondisi fisik unit freezer (Kamera / Galeri).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val folderKey = "unit_${kodeUnit.replace(" ", "_")}"
                    PhotoDocumentationSlot(
                        label = "Foto Kondisi Fisik Freezer",
                        photoKey = "unit_fisik",
                        transactionOrEntityFolder = folderKey,
                        currentUriOrPath = fotoKondisiFisikUri,
                        onPhotoSaved = { fotoKondisiFisikUri = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (initialUnit != null) {
                        OutlinedButton(
                            onClick = { onDelete(initialUnit) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = StatusOverdueRed)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus", color = StatusOverdueRed)
                        }
                    }
                    Button(
                        onClick = {
                            val parsedCap = kapasitasLiter.toIntOrNull() ?: 0
                            val parsedHarga = hargaPembelian.toLongOrNull() ?: 0L
                            val res = FormValidationEngine.validateUnitForm(
                                namaUnit = namaUnit,
                                merek = merek,
                                model = model,
                                nomorSeri = nomorSeri,
                                kapasitasLiter = parsedCap,
                                hargaPembelian = parsedHarga
                            )
                            if (!res.isValid) {
                                unitValidationResult = res
                                unitScope.launch { unitFormScrollState.animateScrollTo(0) }
                                return@Button
                            }
                            unitValidationResult = null
                            val entity = FreezerUnitEntity(
                                id = initialUnit?.id ?: 0L,
                                kodeUnit = kodeUnit.ifBlank { "SAF-CF-NEW" },
                                namaUnit = namaUnit.trim(),
                                jenisUnit = jenisUnit,
                                owner = owner,
                                hargaPembelian = parsedHarga,
                                kapasitasLiter = parsedCap,
                                cicilanPerBulan = preservedCicilanPerBulan,
                                jumlahCicilanTotal = preservedJumlahCicilanTotal,
                                totalPembayaranCicilan = preservedTotalPembayaranCicilan,
                                statusInvestasi = statusInvestasi,
                                kategoriSheet = kategoriSheet,
                                merek = merek.trim(),
                                model = model.trim(),
                                nomorSeri = nomorSeri.trim(),
                                fotoUnitUri = fotoKondisiFisikUri.ifBlank { initialUnit?.fotoUnitUri.orEmpty() },
                                fotoKondisiFisikUri = fotoKondisiFisikUri,
                                fotoBagianDalamUri = preservedFotoBagianDalamUri,
                                fotoMerekSeriUri = preservedFotoMerekSeriUri,
                                statusUnit = statusUnit,
                                lokasiUnit = lokasiUnit,
                                tanggalPembelian = tanggalPembelian,
                                keterangan = keterangan,
                                kondisiUnit = kondisiUnit,
                                tanggalPengiriman = tanggalPengiriman,
                                lokasiGpsPemasangan = lokasiGpsPemasangan
                            )
                            val pricesMap = buildMap<Int, Long> {
                                price3Days.toLongOrNull()?.takeIf { it > 0L }?.let { put(3, it) }
                                price7Days.toLongOrNull()?.takeIf { it > 0L }?.let { put(7, it) }
                                price14Days.toLongOrNull()?.takeIf { it > 0L }?.let { put(14, it) }
                                price30Days.toLongOrNull()?.takeIf { it > 0L }?.let { put(30, it) }
                                price60Days.toLongOrNull()?.takeIf { it > 0L }?.let { put(60, it) }
                                price90Days.toLongOrNull()?.takeIf { it > 0L }?.let { put(90, it) }
                            }
                            onSave(entity, pricesMap, currentDraftUuid)
                        },
                        modifier = Modifier
                            .weight(2f)
                            .testTag("btn_save_unit")
                    ) {
                        Text("SIMPAN DATA UNIT", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
