package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.ui.components.InputTextCaseMode
import com.example.ui.components.MonthlyRevenueBarChart
import com.example.ui.components.MultiMarkerGoogleMapView
import com.example.ui.components.SmartCaseOutlinedTextField
import com.example.ui.components.UnitUtilizationDonutCard
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusActiveBlue
import com.example.ui.theme.StatusActiveBlueBg
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusOverdueRedBg
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPaidGreenBg
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.StatusWarningYellowBg
import com.example.ui.theme.PureWhite
import com.example.ui.theme.appFilterChipColors
import com.example.util.DateCurrencyUtils
import com.example.viewmodel.DashboardMetrics

@Composable
fun DashboardScreen(
    metrics: DashboardMetrics,
    syncStatus: String,
    transactions: List<RentalTransactionEntity> = emptyList(),
    customers: List<CustomerEntity> = emptyList(),
    units: List<FreezerUnitEntity> = emptyList(),
    onNavigateToUnits: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToUsageLocations: () -> Unit = {},
    onNavigateToReturns: () -> Unit = {},
    onOpenTransactionDetail: (RentalTransactionEntity) -> Unit,
    onTriggerNotificationTest: () -> Unit
) {
    val context = LocalContext.current
    var selectedScheduleTab by remember { mutableIntStateOf(0) }
    var globalSearchQuery by remember { mutableStateOf("") }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onTriggerNotificationTest()
    }

    val customerMap = remember(customers) { customers.associateBy { it.id } }

    // Global Search results across Nama Pelanggan, NIK, Nomor Transaksi, Kode Unit, Lokasi Penggunaan Freezer
    val globalSearchResults = remember(globalSearchQuery, transactions, customerMap) {
        val q = globalSearchQuery.trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            transactions.filter { tx ->
                val cust = customerMap[tx.customerId]
                tx.namaPelangganSnapshot.contains(q, ignoreCase = true) ||
                    (cust?.nik?.contains(q, ignoreCase = true) == true) ||
                    tx.nomorInvoice.contains(q, ignoreCase = true) ||
                    tx.kodeUnitSnapshot.contains(q, ignoreCase = true) ||
                    tx.namaUnitSnapshot.contains(q, ignoreCase = true) ||
                    tx.namaLokasiUsaha.contains(q, ignoreCase = true) ||
                    tx.alamatLokasiGps.contains(q, ignoreCase = true) ||
                    tx.kecamatanLokasi.contains(q, ignoreCase = true) ||
                    tx.kabupatenKotaLokasi.contains(q, ignoreCase = true)
            }
        }
    }

    val activeTransactionsCount = remember(transactions, metrics) {
        if (metrics.transaksiAktifCount > 0) {
            metrics.transaksiAktifCount
        } else {
            transactions.count { it.statusPenyewaan == "Dalam Penyewaan" || it.statusPenyewaan == "Booking" }
        }
    }
    val completedTransactionsCount = remember(transactions, metrics) {
        if (metrics.transaksiSelesaiCount > 0) {
            metrics.transaksiSelesaiCount
        } else {
            transactions.count { it.statusPenyewaan.equals("Selesai", ignoreCase = true) }
        }
    }
    val dueTransactionsCount = remember(metrics) {
        metrics.jatuhTempoHariIni.size + metrics.jatuhTempoBesok.size + metrics.jatuhTempo3Hari.size
    }
    val overdueCustomersCount = remember(metrics) {
        (metrics.tagihanTerlambat.map { it.customerId } + metrics.tagihanBelumDibayar.map { it.customerId }).distinct().size
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen")
    ) {
        // 1. HERO BRANDING BANNER (SEWAin Aja Freezer SOC)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NavyDark, Color(0xFF0369A1), SeaBluePrimary)
                        )
                    )
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = 120.dp.toPx(),
                        center = Offset(size.width * 0.9f, size.height * 0.2f)
                    )
                    drawCircle(
                        color = LightBlueAccent.copy(alpha = 0.1f),
                        radius = 80.dp.toPx(),
                        center = Offset(size.width * 0.75f, size.height * 0.85f)
                    )
                }

                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AcUnit,
                                        contentDescription = "Logo SEWAin Aja Freezer SOC",
                                        tint = LightBlueAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "SEWAin Aja Freezer SOC",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "FREEZER RENTAL MANAGEMENT - SOLO RAYA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LightBlueAccent
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onTriggerNotificationTest()
                                }
                            },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .testTag("btn_test_due_notification")
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = "Kirim Pengingat Jatuh Tempo",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Action Buttons inside Hero
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onNavigateToTransactions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LightBlueAccent,
                                contentColor = NavyDark
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("hero_btn_new_transaction")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sewa Baru", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onNavigateToReturns,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.22f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_freezer_returns")
                        ) {
                            Icon(Icons.Default.AssignmentReturn, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pengembalian", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onNavigateToUsageLocations,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_usage_locations")
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lokasi GPS", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Status Database: $syncStatus",
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite
                    )
                }
            }
        }

        // 2. PENCARIAN GLOBAL DASHBOARD ADMIN (Section 6: Nama Pelanggan, NIK, No Transaksi, Kode Unit, Lokasi Penggunaan)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SmartCaseOutlinedTextField(
                        value = globalSearchQuery,
                        onValueChange = { globalSearchQuery = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Cari Nama, NIK, No Transaksi, Kode Unit, atau Lokasi Penggunaan...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari Global") },
                        trailingIcon = {
                            if (globalSearchQuery.isNotBlank()) {
                                IconButton(onClick = { globalSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus Pencarian")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_dashboard_global")
                    )

                    if (globalSearchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Hasil Pencarian (${globalSearchResults.size} transaksi ditemukan):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SeaBluePrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (globalSearchResults.isEmpty()) {
                            Text(
                                text = "Tidak ada data yang cocok dengan kata kunci \"$globalSearchQuery\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                globalSearchResults.take(5).forEach { tx ->
                                    ScheduleTransactionItemRow(
                                        tx = tx,
                                        onClick = { onOpenTransactionDetail(tx) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. PENDAPATAN & KEUANGAN SUMMARY CARDS (Section 6 & 8)
        item {
            Text(
                text = "RINGKASAN PENDAPATAN & TAGIHAN",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FinancialMetricCard(
                        title = "Pendapatan Hari Ini",
                        amount = DateCurrencyUtils.formatRupiah(metrics.pendapatanHariIni),
                        subtitle = "Transaksi masuk hari ini",
                        accentColor = StatusPaidGreen,
                        icon = Icons.Default.TrendingUp,
                        modifier = Modifier.weight(1f)
                    )
                    FinancialMetricCard(
                        title = "Pendapatan Bulan Ini",
                        amount = DateCurrencyUtils.formatRupiah(metrics.pendapatanBulanIni),
                        subtitle = "Akumulasi bulan berjalan",
                        accentColor = SeaBluePrimary,
                        icon = Icons.Default.AccountBalanceWallet,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FinancialMetricCard(
                        title = "Total Pendapatan",
                        amount = DateCurrencyUtils.formatRupiah(metrics.pendapatanTahunIni),
                        subtitle = "Aktif: $activeTransactionsCount | Selesai: $completedTransactionsCount",
                        accentColor = NavyDark,
                        icon = Icons.Default.ReceiptLong,
                        modifier = Modifier.weight(1f)
                    )
                    FinancialMetricCard(
                        title = "Pelanggan Menunggak",
                        amount = "$overdueCustomersCount Pelanggan",
                        subtitle = "Piutang: ${DateCurrencyUtils.formatRupiah(metrics.totalPiutang)}",
                        accentColor = StatusOverdueRed,
                        icon = Icons.Default.Warning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. TOTAL UNIT, PELANGGAN, TRANSAKSI AKTIF, SELESAI & PENGEMBALIAN SUMMARY (Section 7)
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Total Unit Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToUnits() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Kitchen, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TOTAL UNIT FREEZER", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${metrics.totalUnits} Freezer",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Total Unit Tersedia: ${metrics.unitTersedia}", style = MaterialTheme.typography.bodySmall, color = StatusPaidGreen)
                        Text("• Total Unit Tersewa: ${metrics.unitDisewa}", style = MaterialTheme.typography.bodySmall, color = SeaBluePrimary)
                        Text("• Total Unit Perbaikan: ${metrics.unitMaintenance}", style = MaterialTheme.typography.bodySmall, color = StatusWarningYellow)
                        Text("• Non-Aktif/Hilang: ${metrics.unitTidakAktif}", style = MaterialTheme.typography.bodySmall, color = StatusOverdueRed)
                    }
                }

                // Data Pelanggan, Transaksi & Pengembalian Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToCustomers() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TRANSAKSI & RETUR", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${metrics.totalPelanggan} Mitra",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Total Transaksi Aktif: $activeTransactionsCount", style = MaterialTheme.typography.bodySmall, color = SeaBluePrimary)
                        Text("• Total Transaksi Selesai: $completedTransactionsCount", style = MaterialTheme.typography.bodySmall, color = StatusPaidGreen)
                        Text("• Pengembalian Bulan Ini: ${metrics.pengembalianBulanIniCount}", style = MaterialTheme.typography.bodySmall, color = NavyDark, fontWeight = FontWeight.SemiBold)
                        Text("• Jatuh Tempo: $dueTransactionsCount", style = MaterialTheme.typography.bodySmall, color = StatusWarningYellow)
                    }
                }
            }
        }

        // 4B. DAFTAR PENGEMBALIAN TERBARU & AKSES MENU PENGEMBALIAN FREEZER (Section 3 & 7)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = SeaBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "SERAH TERIMA & PENGEMBALIAN TERBARU",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Total pengembalian bulan ini: ${metrics.pengembalianBulanIniCount} unit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onNavigateToReturns,
                            modifier = Modifier.testTag("btn_dashboard_open_returns")
                        ) {
                            Icon(Icons.Default.AssignmentReturn, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Menu Pengembalian", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (metrics.daftarPengembalianTerbaru.isEmpty()) {
                        Text(
                            text = "Belum ada riwayat serah terima pengembalian freezer. Tekan 'Menu Pengembalian' untuk mencatat pengembalian unit & cetak Bukti Serah Terima PDF.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            metrics.daftarPengembalianTerbaru.forEach { ret ->
                                val isGood = ret.kondisiUnitSaatKembali.equals("Baik / Normal", ignoreCase = true) ||
                                    ret.kondisiUnitSaatKembali.equals("Baik dengan catatan", ignoreCase = true)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToReturns() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${ret.nomorDokumenPengembalian} • ${ret.namaPelangganSnapshot}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${ret.kodeUnitSnapshot} (${ret.namaUnitSnapshot}) • Inv: ${ret.nomorInvoiceSnapshot}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Kembali: ${DateCurrencyUtils.formatDisplayDate(ret.tanggalPengembalian)} ${ret.jamPengembalian} • Kondisi: ${ret.kondisiUnitSaatKembali}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isGood) StatusPaidGreen else StatusWarningYellow,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Surface(
                                            color = if (isGood) StatusPaidGreenBg else StatusWarningYellowBg,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = ret.statusUnitSetelahKembali.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isGood) StatusPaidGreen else StatusWarningYellow,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. DAFTAR LOKASI PENGGUNAAN FREEZER (Section 2 & 6)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = SeaBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "DAFTAR LOKASI PENGGUNAAN FREEZER",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Monitoring titik GPS lokasi usaha tempat unit digunakan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onNavigateToUsageLocations,
                            modifier = Modifier.testTag("btn_dashboard_manage_locations")
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kelola GPS", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val usageLocations = transactions.filter {
                        it.statusPenyewaan == "Dalam Penyewaan" || it.statusPenyewaan == "Booking" || it.alamatLokasiGps.isNotBlank()
                    }
                    val usageLocationsWithGps = remember(usageLocations) {
                        usageLocations.filter { it.latitude != null && it.longitude != null }
                    }

                    if (usageLocationsWithGps.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            MultiMarkerGoogleMapView(
                                transactionsWithGps = usageLocationsWithGps,
                                onSelectTransaction = { txId ->
                                    val found = transactions.find { it.id == txId }
                                    if (found != null) {
                                        onOpenTransactionDetail(found)
                                    } else {
                                        onNavigateToUsageLocations()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (usageLocations.isEmpty()) {
                        Text(
                            text = "Belum ada lokasi penggunaan freezer yang terdaftar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            usageLocations.take(4).forEach { tx ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToUsageLocations() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = SeaBluePrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = tx.namaPelangganSnapshot,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${tx.kodeUnitSnapshot} • ${tx.alamatLokasiGps.ifBlank { "Alamat penggunaan belum diisi" }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (tx.latitude != null && tx.longitude != null) {
                                                    Text(
                                                        text = String.format("GPS: %.5f, %.5f", tx.latitude, tx.longitude),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = StatusPaidGreen,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                val lat = tx.latitude ?: -7.566620
                                                val lon = tx.longitude ?: 110.816650
                                                com.example.location.LocationGpsHelper.openInMapOrBrowser(
                                                    context = context,
                                                    lat = lat,
                                                    lon = lon,
                                                    label = tx.alamatLokasiGps.ifBlank { "Lokasi Freezer SAF" }
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Default.Map, contentDescription = "Buka OpenStreetMap", tint = SeaBluePrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. SISTEM JADWAL & PENGINGAT JATUH TEMPO DENGAN INDIKATOR WARNA
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = SeaBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "SISTEM JADWAL & JATUH TEMPO",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Hijau = Lunas | Kuning = Mendekati | Merah = Terlambat | Biru = Disewa",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val tabs = listOf(
                        "Hari Ini (${metrics.jatuhTempoHariIni.size})",
                        "Besok (${metrics.jatuhTempoBesok.size})",
                        "3 Hari (${metrics.jatuhTempo3Hari.size})",
                        "7 Hari (${metrics.jatuhTempo7Hari.size})",
                        "Terlambat (${metrics.tagihanTerlambat.size})",
                        "Belum Bayar (${metrics.tagihanBelumDibayar.size})",
                        "Lunas (${metrics.transaksiLunas.size})"
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        tabs.forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedScheduleTab == idx,
                                onClick = { selectedScheduleTab = idx },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                colors = appFilterChipColors()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val displayedList = when (selectedScheduleTab) {
                        0 -> metrics.jatuhTempoHariIni
                        1 -> metrics.jatuhTempoBesok
                        2 -> metrics.jatuhTempo3Hari
                        3 -> metrics.jatuhTempo7Hari
                        4 -> metrics.tagihanTerlambat
                        5 -> metrics.tagihanBelumDibayar
                        else -> metrics.transaksiLunas
                    }

                    if (displayedList.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EventBusy, contentDescription = null, tint = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tidak ada tagihan pada kategori jadwal ini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            displayedList.forEach { tx ->
                                ScheduleTransactionItemRow(
                                    tx = tx,
                                    onClick = { onOpenTransactionDetail(tx) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. CHARTS: PENDAPATAN BULANAN & TINGKAT PENGGUNAAN UNIT
        item {
            MonthlyRevenueBarChart(monthlyData = metrics.monthlyRevenueChart)
        }

        item {
            UnitUtilizationDonutCard(
                totalUnits = metrics.totalUnits,
                tersedia = metrics.unitTersedia,
                disewa = metrics.unitDisewa,
                maintenance = metrics.unitMaintenance,
                tidakAktifAtauHilang = metrics.unitTidakAktif
            )
        }
    }
}

@Composable
private fun FinancialMetricCard(
    title: String,
    amount: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ScheduleTransactionItemRow(
    tx: RentalTransactionEntity,
    onClick: () -> Unit
) {
    val daysDiff = DateCurrencyUtils.daysFromToday(tx.tanggalJatuhTempo)

    // Color Indicator per Section 6:
    // Hijau = Lunas | Kuning = Mendekati jatuh tempo | Merah = Terlambat | Biru = Dalam penyewaan
    val (badgeBg, badgeTextColor, badgeLabel) = when {
        tx.statusPembayaran == "Terlambat" || (daysDiff < 0 && tx.statusPembayaran != "Lunas") ->
            Triple(StatusOverdueRedBg, StatusOverdueRed, "TERLAMBAT (${kotlin.math.abs(daysDiff)} hr)")
        daysDiff in 0..7 && tx.statusPenyewaan == "Dalam Penyewaan" ->
            Triple(StatusWarningYellowBg, StatusWarningYellow, if (daysDiff == 0L) "JATUH TEMPO HARI INI" else "H-$daysDiff JATUH TEMPO")
        tx.statusPembayaran == "Lunas" ->
            Triple(StatusPaidGreenBg, StatusPaidGreen, "LUNAS")
        else ->
            Triple(StatusActiveBlueBg, StatusActiveBlue, tx.statusPenyewaan.uppercase())
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            com.example.ui.components.FreezerUnitThumbnail(
                photoUriOrPath = tx.fotoUnitSebelumKirimUri,
                jenisUnit = tx.namaUnitSnapshot,
                kapasitasLiter = 200,
                size = 52.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tx.nomorInvoice,
                        style = MaterialTheme.typography.labelSmall,
                        color = SeaBluePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeTextColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tx.namaPelangganSnapshot,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${tx.namaUnitSnapshot} (${tx.durasiHari} Hari) • Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalJatuhTempo)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tx.namaLokasiUsaha.isNotBlank() || tx.alamatLokasiGps.isNotBlank()) {
                    Text(
                        text = "📍 ${tx.namaLokasiUsaha.ifBlank { tx.alamatLokasiGps }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeaBluePrimary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DateCurrencyUtils.formatRupiah(tx.totalTagihan),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = tx.statusPembayaran,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tx.statusPembayaran == "Lunas") StatusPaidGreen else StatusWarningYellow
                )
            }
        }
    }
}
