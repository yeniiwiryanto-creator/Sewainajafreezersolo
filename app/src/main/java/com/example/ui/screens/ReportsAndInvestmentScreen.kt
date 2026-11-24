package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalExtensionHistoryEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.ui.components.MonthlyRevenueBarChart
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
import com.example.ui.theme.appOutlinedTextFieldColors
import com.example.ui.components.FieldErrorSupportingText
import com.example.ui.components.FormValidationAlertBanner
import com.example.util.DateCurrencyUtils
import com.example.util.FormValidationEngine
import com.example.util.FormValidationResult
import com.example.viewmodel.DashboardMetrics
import java.util.Locale

@Composable
fun ReportsAndInvestmentScreen(
    units: List<FreezerUnitEntity>,
    transactions: List<RentalTransactionEntity>,
    extensions: List<RentalExtensionHistoryEntity>,
    metrics: DashboardMetrics,
    onUpdateUnitInvestment: (FreezerUnitEntity) -> Unit,
    onExportFullXlsx: () -> Unit,
    onExportCategoryCsv: (String) -> Unit,
    transactionUnitItems: List<TransactionUnitItemEntity> = emptyList()
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Investasi & ROI, 1 = Laporan Keuangan & Export, 2 = Riwayat Perpanjangan, 3 = Progres Hak Milik Per Unit
    var selectedSheetFilter by remember { mutableStateOf("Semua") }
    var unitForInstallmentUpdate by remember { mutableStateOf<FreezerUnitEntity?>(null) }
    val hakMilikUnitItems = remember(transactionUnitItems) {
        transactionUnitItems.filter { it.jenisProgram.contains("Hak Milik", ignoreCase = true) }
    }

    val totalModalInvestasi = remember(units) { units.sumOf { it.hargaPembelian } }
    val totalCicilanTerbayar = remember(units) { units.sumOf { it.totalPembayaranCicilan } }
    val totalSisaKewajiban = remember(units) {
        units.sumOf { (it.hargaPembelian - it.totalPembayaranCicilan).coerceAtLeast(0L) }
    }
    val totalPendapatanSepanjangWaktu = remember(transactions) {
        transactions.filter { it.statusPenyewaan != "Dibatalkan" }.sumOf { it.nominalPembayaran }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("reports_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Sub-navigation Tabs
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("Manajemen Investasi & ROI Unit", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = appFilterChipColors()
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("Laporan Pendapatan & Export", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = appFilterChipColors()
            )
            FilterChip(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                label = { Text("Riwayat Perpanjangan (${extensions.size})", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = appFilterChipColors()
            )
            FilterChip(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                label = { Text("Progres Hak Milik (${hakMilikUnitItems.size})", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = appFilterChipColors(),
                modifier = Modifier.testTag("tab_reports_hak_milik_units")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                val filteredUnits = units.filter {
                    selectedSheetFilter == "Semua" || it.kategoriSheet == selectedSheetFilter || it.statusInvestasi == selectedSheetFilter
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Portfolio Summary Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = NavyDark)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "PORTFOLIO INVESTASI FREEZER SOLO RAYA (SECTION 10)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text("Total Nilai Pembelian Unit", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                        Text(DateCurrencyUtils.formatRupiah(totalModalInvestasi), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Total Pendapatan Sewa All-Time", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                        Text(DateCurrencyUtils.formatRupiah(totalPendapatanSepanjangWaktu), style = MaterialTheme.typography.titleMedium, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cicilan Terbayar: ${DateCurrencyUtils.formatRupiah(totalCicilanTerbayar)}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                    Text("Sisa Kewajiban: ${DateCurrencyUtils.formatRupiah(totalSisaKewajiban)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Filter by Sheet / Investment Status (INVESTASI SEWA FREEZER / INVESTASI FREEZER POINT FROZEN / LIST / DIBAYAR SEBAGIAN / DONE / LUNAS / HILANG)
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                "Semua",
                                "INVESTASI SEWA FREEZER",
                                "INVESTASI FREEZER POINT FROZEN",
                                "LIST",
                                "DIBAYAR SEBAGIAN",
                                "DONE",
                                "LUNAS",
                                "HILANG"
                            ).forEach { chip ->
                                FilterChip(
                                    selected = selectedSheetFilter == chip,
                                    onClick = { selectedSheetFilter = chip },
                                    label = { Text(chip, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    items(filteredUnits, key = { it.id }) { unit ->
                        val unitTx = transactions.filter { it.unitId == unit.id && it.statusPenyewaan != "Dibatalkan" }
                        val allTimeUnitRevenue = unitTx.sumOf { it.nominalPembayaran }
                        val avgMonthlyRate = unitTx.firstOrNull()?.hargaSewa ?: 280000L

                        UnitInvestmentRoiCard(
                            unit = unit,
                            allTimeRevenue = allTimeUnitRevenue,
                            avgMonthlyRent = avgMonthlyRate,
                            onPayInstallmentClick = { unitForInstallmentUpdate = unit }
                        )
                    }
                }
            }

            1 -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        MonthlyRevenueBarChart(monthlyData = metrics.monthlyRevenueChart)
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Rekapitulasi Keuangan & Piutang Bisnis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("• Pendapatan Hari Ini: ${DateCurrencyUtils.formatRupiah(metrics.pendapatanHariIni)}", style = MaterialTheme.typography.bodyMedium)
                                Text("• Pendapatan Bulan Ini: ${DateCurrencyUtils.formatRupiah(metrics.pendapatanBulanIni)}", style = MaterialTheme.typography.bodyMedium)
                                Text("• Pendapatan Tahun Ini: ${DateCurrencyUtils.formatRupiah(metrics.pendapatanTahunIni)}", style = MaterialTheme.typography.bodyMedium)
                                HorizontalDivider()
                                Text("• Total Tagihan Terbit: ${DateCurrencyUtils.formatRupiah(metrics.totalTagihan)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("• Total Pembayaran Masuk: ${DateCurrencyUtils.formatRupiah(metrics.totalPembayaran)}", style = MaterialTheme.typography.bodyMedium, color = StatusPaidGreen, fontWeight = FontWeight.Bold)
                                Text("• Total Piutang Belum Dibayar: ${DateCurrencyUtils.formatRupiah(metrics.totalPiutang)}", style = MaterialTheme.typography.bodyMedium, color = StatusOverdueRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Section 9: Export Center
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Pusat Export Laporan (XLSX & CSV)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Kompatibel penuh dengan struktur TIMELINE SEWA IN AJA FREEZER.xlsx (Sheet 1: INVESTASI SEWA FREEZER, Sheet 2: INVESTASI FREEZER POINT FROZEN, Sheet 3: DATA PELANGGAN).",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Button(
                                    onClick = onExportFullXlsx,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_export_full_xlsx")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("EXPORT WORKBOOK LENGKAP (.XLSX)", fontWeight = FontWeight.Bold)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    FilledTonalButton(
                                        onClick = { onExportCategoryCsv("PELANGGAN") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CSV Pelanggan", style = MaterialTheme.typography.labelMedium)
                                    }
                                    FilledTonalButton(
                                        onClick = { onExportCategoryCsv("UNIT") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CSV Data Unit", style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    FilledTonalButton(
                                        onClick = { onExportCategoryCsv("TRANSAKSI") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CSV Transaksi", style = MaterialTheme.typography.labelMedium)
                                    }
                                    FilledTonalButton(
                                        onClick = { onExportCategoryCsv("PENDAPATAN") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CSV Pendapatan", style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onExportCategoryCsv("JADWAL_JATUH_TEMPO") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export Jadwal Jatuh Tempo (.CSV)")
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (extensions.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Belum ada riwayat perpanjangan sewa. Gunakan tombol 'Perpanjang' pada menu Transaksi untuk memperpanjang masa sewa tanpa menghapus transaksi lama.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(extensions, key = { it.id }) { ext ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "${ext.nomorInvoiceLama} ➔ ${ext.nomorInvoiceBaru}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SeaBluePrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tanggal Perpanjangan: ${DateCurrencyUtils.formatDisplayDate(ext.tanggalPerpanjangan)} (+${ext.tambahanDurasiHari} Hari)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Akhir Sewa Lama: ${ext.tanggalAkhirLama} ➔ Akhir Baru: ${ext.tanggalAkhirBaru}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Tagihan Baru: ${DateCurrencyUtils.formatRupiah(ext.tagihanBaru)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (ext.catatan.isNotBlank()) {
                                        Text("Catatan: ${ext.catatan}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                val txById = remember(transactions) { transactions.associateBy { it.id } }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("list_hak_milik_unit_progress")
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF4C1D95))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "PROGRES SEWA HAK MILIK PER UNIT FREEZER",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFFDDD6FE),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${hakMilikUnitItems.size} Unit Freezer tercatat pada program Sewa Hak Milik. Setiap unit memiliki progres cicilan independen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (hakMilikUnitItems.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Belum ada unit freezer dengan program Sewa Hak Milik. Buat transaksi Sewa Hak Milik untuk memantau progres per unit di sini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(hakMilikUnitItems, key = { it.id }) { item ->
                            val parentTx = txById[item.transactionId]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_report_hm_unit_${item.id}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${item.namaUnitSnapshot} (${item.kodeUnitSnapshot})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = NavyDark
                                            )
                                            Text(
                                                text = "Pelanggan: ${parentTx?.namaPelangganSnapshot ?: "-"} • ${parentTx?.nomorInvoice ?: "TX #${item.transactionId}"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SeaBluePrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Surface(
                                            color = if (item.hakMilikRemainingCount == 0) StatusPaidGreenBg else Color(0xFFEDE9FE),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = item.statusProgramHakMilik,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.hakMilikRemainingCount == 0) StatusPaidGreen else Color(0xFF5B21B6),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = item.hakMilikProgressLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF5B21B6)
                                        )
                                        Text(
                                            text = "Sisa ${item.hakMilikRemainingCount}x cicilan",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.hakMilikRemainingCount == 0) StatusPaidGreen else StatusWarningYellow
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = { item.hakMilikProgressFraction },
                                        color = if (item.hakMilikRemainingCount == 0) StatusPaidGreen else Color(0xFF7C3AED),
                                        trackColor = Color(0xFFDDD6FE),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )

                                    Text(
                                        text = "Tarif Cicilan: ${DateCurrencyUtils.formatRupiah(item.hargaSewa)} • Terbayar: ${DateCurrencyUtils.formatRupiah(item.nominalPembayaran)} • Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(item.tanggalJatuhTempo)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (unitForInstallmentUpdate != null) {
        val u = unitForInstallmentUpdate!!
        UpdateInstallmentDialog(
            unit = u,
            onDismiss = { unitForInstallmentUpdate = null },
            onSave = { updated ->
                onUpdateUnitInvestment(updated)
                unitForInstallmentUpdate = null
            }
        )
    }
}

/**
 * Section 10: Detailed Investment & Automatic ROI / Break-Even Card per Freezer Unit
 */
@Composable
private fun UnitInvestmentRoiCard(
    unit: FreezerUnitEntity,
    allTimeRevenue: Long,
    avgMonthlyRent: Long,
    onPayInstallmentClick: () -> Unit
) {
    val sisaKewajiban = (unit.hargaPembelian - unit.totalPembayaranCicilan).coerceAtLeast(0L)
    val roiPct = if (unit.hargaPembelian > 0) {
        ((allTimeRevenue.toDouble() / unit.hargaPembelian.toDouble()) * 100.0).toFloat()
    } else 100f

    val estimatedBreakEvenMonths = DateCurrencyUtils.calculateRoiMonths(unit.hargaPembelian, avgMonthlyRent)
    val remainingRoiRevenueNeeded = (unit.hargaPembelian - allTimeRevenue).coerceAtLeast(0L)
    val remainingMonthsToBreakEven = DateCurrencyUtils.calculateRoiMonths(remainingRoiRevenueNeeded, avgMonthlyRent)

    val (invBg, invFg) = when (unit.statusInvestasi) {
        "LUNAS", "DONE" -> StatusPaidGreenBg to StatusPaidGreen
        "HILANG" -> StatusOverdueRedBg to StatusOverdueRed
        else -> StatusWarningYellowBg to StatusWarningYellow
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${unit.kodeUnit} • ${unit.kategoriSheet}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeaBluePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = unit.namaUnit,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(color = invBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = unit.statusInvestasi,
                        style = MaterialTheme.typography.labelSmall,
                        color = invFg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Nama Pemilik (Owner): ${unit.owner}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Harga Pembelian", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DateCurrencyUtils.formatRupiah(unit.hargaPembelian), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Cicilan (${unit.jumlahCicilanTotal}x)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${DateCurrencyUtils.formatRupiah(unit.cicilanPerBulan)}/bln", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Sisa Kewajiban", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = DateCurrencyUtils.formatRupiah(sisaKewajiban),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sisaKewajiban > 0) StatusOverdueRed else StatusPaidGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Total Pembayaran Cicilan: ${DateCurrencyUtils.formatRupiah(unit.totalPembayaranCicilan)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Total Pendapatan Sewa: ${DateCurrencyUtils.formatRupiah(allTimeRevenue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusPaidGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            // Automatic ROI Progress Bar
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = String.format(Locale.US, "Progres Balik Modal (ROI): %.1f%%", roiPct),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (remainingRoiRevenueNeeded <= 0L) "SUDAH BALIK MODAL (BEP)"
                    else String.format(Locale.US, "Est. BEP: %.1f bln lagi (Total %.1f bln)", remainingMonthsToBreakEven, estimatedBreakEvenMonths),
                    style = MaterialTheme.typography.labelSmall,
                    color = SeaBluePrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { (roiPct / 100f).coerceIn(0f, 1f) },
                color = if (roiPct >= 100f) StatusPaidGreen else SeaBluePrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = onPayInstallmentClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Catat Pembayaran Cicilan / Status", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun UpdateInstallmentDialog(
    unit: FreezerUnitEntity,
    onDismiss: () -> Unit,
    onSave: (FreezerUnitEntity) -> Unit
) {
    var totalCicilanDibayar by remember {
        mutableStateOf((unit.totalPembayaranCicilan + unit.cicilanPerBulan).coerceAtMost(unit.hargaPembelian).toString())
    }
    var statusInvestasi by remember { mutableStateOf(unit.statusInvestasi) }
    var validationResult by remember { mutableStateOf<FormValidationResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Cicilan & Status Investasi", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormValidationAlertBanner(
                    validationResult = validationResult,
                    testTagName = "investment_validation_banner"
                )
                Text("${unit.kodeUnit} - ${unit.namaUnit}", style = MaterialTheme.typography.titleSmall)
                Text("Harga Beli: ${DateCurrencyUtils.formatRupiah(unit.hargaPembelian)} | Cicilan/bln: ${DateCurrencyUtils.formatRupiah(unit.cicilanPerBulan)}", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = totalCicilanDibayar,
                    onValueChange = {
                        totalCicilanDibayar = it.filter { c -> c.isDigit() }
                        if (validationResult != null) validationResult = null
                    },
                    label = { Text("Total Akumulasi Pembayaran Cicilan (Rp) *") },
                    isError = validationResult?.fieldErrors?.isNotEmpty() == true,
                    supportingText = { FieldErrorSupportingText(validationResult?.fieldErrors?.values?.firstOrNull()) },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors()
                )

                Text("Status Investasi:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    STATUS_INVESTASI_OPTIONS.forEach { st ->
                        FilterChip(
                            selected = statusInvestasi == st,
                            onClick = { statusInvestasi = st },
                            label = { Text(st, style = MaterialTheme.typography.labelSmall) },
                            colors = appFilterChipColors()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPaid = totalCicilanDibayar.toLongOrNull()
                    if (totalCicilanDibayar.isBlank() || parsedPaid == null || parsedPaid < 0L) {
                        validationResult = FormValidationResult.invalid(
                            linkedMapOf("totalCicilanDibayar" to ("Total Pembayaran Cicilan" to "Masukkan nominal pembayaran cicilan yang valid."))
                        )
                        return@Button
                    }
                    val paidInstallmentsCount = if (unit.cicilanPerBulan > 0L) (parsedPaid / unit.cicilanPerBulan).toInt() else 0
                    val res = FormValidationEngine.validateInvestmentForm(
                        unitName = unit.namaUnit,
                        hargaPembelian = unit.hargaPembelian,
                        cicilanPerBulan = unit.cicilanPerBulan,
                        jumlahCicilanTotal = unit.jumlahCicilanTotal,
                        cicilanSudahDibayar = paidInstallmentsCount.coerceAtMost(unit.jumlahCicilanTotal.coerceAtLeast(paidInstallmentsCount))
                    )
                    if (!res.isValid) {
                        validationResult = res
                        return@Button
                    }
                    validationResult = null
                    val autoStatus = if (parsedPaid >= unit.hargaPembelian && statusInvestasi == "DIBAYAR SEBAGIAN") "LUNAS" else statusInvestasi
                    onSave(
                        unit.copy(
                            totalPembayaranCicilan = parsedPaid,
                            statusInvestasi = autoStatus
                        )
                    )
                },
                modifier = Modifier.testTag("btn_save_installment_update")
            ) {
                Text("Simpan Perubahan")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
