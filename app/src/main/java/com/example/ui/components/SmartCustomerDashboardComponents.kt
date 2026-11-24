package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerTimelineEventEntity
import com.example.data.local.PaymentMethodEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitPaymentAllocationEntity
import com.example.data.repository.ContractHierarchyNode
import com.example.data.repository.CustomerSmartSummary
import com.example.media.SmartImageProcessor
import com.example.ui.theme.IceWhite
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBlueDark
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.SkyBlueSurface
import com.example.ui.theme.StatusActiveBlue
import com.example.ui.theme.StatusActiveBlueBg
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusOverdueRedBg
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPaidGreenBg
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.StatusWarningYellowBg
import com.example.ui.theme.appFilterChipColors
import com.example.ui.theme.appOutlinedTextFieldColors
import com.example.util.DateCurrencyUtils
import java.io.File

@Composable
fun CustomerAvatarThumbnail(
    customer: CustomerEntity,
    sizeDp: Int = 56
) {
    val context = LocalContext.current
    val photoModel = remember(customer.fotoPelangganUri, customer.fotoKtpDepanUri) {
        val raw = customer.fotoPelangganUri.ifBlank { customer.fotoKtpDepanUri }
        val resolved = SmartImageProcessor.getExistingThumbnailPathOrNull(context, raw) ?: raw
        when {
            resolved.isBlank() -> null
            resolved.startsWith("/") -> File(resolved).takeIf { it.exists() }
            else -> resolved
        }
    }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(SkyBlueSurface)
            .border(2.dp, LightBlueAccent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photoModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoModel)
                    .size(300, 300)
                    .scale(Scale.FILL)
                    .crossfade(true)
                    .build(),
                contentDescription = "Foto ${customer.namaLengkap}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initials = customer.namaLengkap
                .trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "P" }
            Text(
                text = initials,
                color = NavyDark,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (sizeDp / 3).sp
            )
        }
    }
}

/**
 * Section 1 & Section 6: Master Customer Smart Dashboard Header + KPI Grid + 6 Quick Actions.
 */
@Composable
fun SmartCustomerDashboardCard(
    summary: CustomerSmartSummary,
    onActionLihatKontrak: () -> Unit,
    onActionTambahUnit: () -> Unit,
    onActionRiwayatPembayaran: () -> Unit,
    onActionDokumentasi: () -> Unit,
    onActionEditPelanggan: () -> Unit,
    onActionCetakPdf: () -> Unit
) {
    val c = summary.customer
    val permanentCode = c.kodePelanggan.ifBlank { "CUST/SAF/${c.id.toString().padStart(4, '0')}" }
    val statusBadgeColors = when (summary.statusPelangganLabel) {
        "Pelanggan Aktif" -> StatusPaidGreenBg to StatusPaidGreen
        "Menunggak" -> StatusOverdueRedBg to StatusOverdueRed
        "Selesai / Nonaktif" -> StatusActiveBlueBg to StatusActiveBlue
        else -> StatusWarningYellowBg to StatusWarningYellow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_customer_dashboard_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(NavyDark, SeaBlueDark, SeaBluePrimary)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Identity Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CustomerAvatarThumbnail(customer = c, sizeDp = 64)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = permanentCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = statusBadgeColors.first,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = summary.statusPelangganLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusBadgeColors.second,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = c.namaLengkap.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "WA: ${c.nomorHp} • Bergabung: ${DateCurrencyUtils.formatDisplayDate(c.tanggalRegistrasi)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SkyBlueSurface
                    )
                }
            }

            // Hero Bullet Summary matching prompt spec
            Surface(
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "• ${summary.totalUnitAktif} Unit Freezer Aktif  • ${summary.totalKontrakAktif} Kontrak Aktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Tagihan Bulanan: ${DateCurrencyUtils.formatRupiah(summary.totalTagihanBulanan)}  • Status: ${summary.statusPelangganLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightBlueAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 6 KPI Grid (3 columns x 2 rows)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CustomerKpiTile("Unit Aktif", "${summary.totalUnitAktif}", Modifier.weight(1f))
                CustomerKpiTile("Kontrak", "${summary.totalKontrakAktif}/${summary.totalKontrakKeseluruhan}", Modifier.weight(1f))
                CustomerKpiTile("Tagihan/Bln", DateCurrencyUtils.formatRupiah(summary.totalTagihanBulanan), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CustomerKpiTile(
                    label = "Tunggakan",
                    value = DateCurrencyUtils.formatRupiah(summary.totalTunggakan),
                    modifier = Modifier.weight(1f),
                    highlightWarning = summary.totalTunggakan > 0L
                )
                CustomerKpiTile("Bayar Bln Ini", DateCurrencyUtils.formatRupiah(summary.pembayaranBulanIni), Modifier.weight(1f))
                CustomerKpiTile("Status", summary.statusPelangganLabel, Modifier.weight(1f))
            }

            // 6 Quick Action Buttons (Section 6)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionPill("Lihat Kontrak", Icons.Default.Assignment, onActionLihatKontrak, Modifier.weight(1f), "qa_lihat_kontrak")
                    QuickActionPill("Tambah Unit", Icons.Default.AddCircleOutline, onActionTambahUnit, Modifier.weight(1f), "qa_tambah_unit")
                    QuickActionPill("Pembayaran", Icons.Default.Payments, onActionRiwayatPembayaran, Modifier.weight(1f), "qa_riwayat_bayar")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionPill("Dokumentasi", Icons.Default.PhotoLibrary, onActionDokumentasi, Modifier.weight(1f), "qa_dokumentasi")
                    QuickActionPill("Edit Profil", Icons.Default.Edit, onActionEditPelanggan, Modifier.weight(1f), "qa_edit_pelanggan")
                    QuickActionPill("Cetak PDF", Icons.Default.PictureAsPdf, onActionCetakPdf, Modifier.weight(1f), "qa_cetak_pdf")
                }
            }
        }
    }
}

@Composable
private fun CustomerKpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlightWarning: Boolean = false
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SeaBlueDark,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = if (highlightWarning) StatusOverdueRed else NavyDark,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        color = Color.White.copy(alpha = 0.94f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = SeaBluePrimary, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NavyDark,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Section 2: Hierarchical Master Kontrak Pelanggan
 * CUSTOMER -> CONTRACT -> RENTAL UNIT -> PAYMENT SCHEDULE -> PAYMENT HISTORY
 */
@Composable
fun MasterContractHierarchySection(
    summary: CustomerSmartSummary,
    onAllocatePaymentForContract: (ContractHierarchyNode) -> Unit,
    onPrintContractPdf: (RentalTransactionEntity) -> Unit,
    onAddUnitContract: () -> Unit
) {
    val todayIso = remember { DateCurrencyUtils.todayIso() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MASTER KONTRAK & HIERARKI UNIT (${summary.contracts.size} Kontrak)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
                Text(
                    text = "Pelanggan -> Kontrak -> Unit Freezer -> Jadwal & Riwayat Pembayaran",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeaBlueDark
                )
            }
            FilledTonalButton(
                onClick = onAddUnitContract,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_master_add_unit_contract")
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tambah Unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }

        if (summary.contracts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = IceWhite),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Belum ada kontrak sewa untuk pelanggan ini.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            summary.contracts.forEach { node ->
                var expanded by remember(node.transaction.id) { mutableStateOf(true) }
                val tx = node.transaction
                val isCompleted = tx.statusPenyewaan.equals("Selesai", true) || tx.statusPenyewaan.equals("Dibatalkan", true)
                val remainingContract = (tx.totalTagihan - tx.nominalPembayaran).coerceAtLeast(0L)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("master_contract_card_${tx.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (isCompleted) Color(0xFFCBD5E1) else SeaBluePrimary.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = node.contractNumberDisplay,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NavyDark
                                )
                                Text(
                                    text = "${node.unitItems.size} Unit • Tgl: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalPemesanan)} • Status: ${tx.statusPenyewaan}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SeaBlueDark
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (remainingContract == 0L) StatusPaidGreenBg else StatusWarningYellowBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (remainingContract == 0L) "Lunas" else "Sisa ${DateCurrencyUtils.formatRupiah(remainingContract)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (remainingContract == 0L) StatusPaidGreen else StatusWarningYellow,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(30.dp)) {
                                    Icon(
                                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand Kontrak"
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HorizontalDivider(color = SkyBlueSurface)

                                // Rental Units inside Contract
                                node.unitItems.forEachIndexed { idx, item ->
                                    val smartStatus = item.computeSmartBillingStatus(todayIso)
                                    val (stBg, stFg) = when (smartStatus) {
                                        "Sudah dibayar" -> StatusPaidGreenBg to StatusPaidGreen
                                        "Pembayaran sebagian" -> StatusActiveBlueBg to StatusActiveBlue
                                        "Jatuh tempo hari ini" -> StatusWarningYellowBg to StatusWarningYellow
                                        "Terlambat" -> StatusOverdueRedBg to StatusOverdueRed
                                        else -> Color(0xFFF1F5F9) to NavyDark
                                    }
                                    val unitAllocs = node.unitAllocations.filter { it.unitItemId == item.id }

                                    Surface(
                                        color = IceWhite,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, SkyBlueSurface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Unit #${idx + 1}: ${item.namaUnitSnapshot} (${item.kodeUnitSnapshot})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NavyDark,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(color = stBg, shape = RoundedCornerShape(6.dp)) {
                                                    Text(
                                                        text = smartStatus,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = stFg,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Program: ${item.jenisProgram}${if (item.jenisProgram.contains("Hak Milik", true)) " (${item.pembayaranKeHakMilik}/${item.totalPeriodeHakMilik}x)" else ""} • Sewa: ${DateCurrencyUtils.formatRupiah(item.hargaSewa)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SeaBlueDark,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Jadwal: Mulai ${DateCurrencyUtils.formatDisplayDate(item.tanggalMulaiSewa)} • Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(item.tanggalJatuhTempo)}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                text = "Dibayar: ${DateCurrencyUtils.formatRupiah(item.nominalPembayaran)} • Sisa: ${DateCurrencyUtils.formatRupiah(item.sisaTagihanUnit)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.sisaTagihanUnit > 0L) StatusOverdueRed else StatusPaidGreen
                                            )
                                            if (unitAllocs.isNotEmpty()) {
                                                Text(
                                                    text = "Riwayat Alokasi (${unitAllocs.size}x): " + unitAllocs.take(3).joinToString(" | ") {
                                                        "${it.paymentDate}: ${DateCurrencyUtils.formatRupiah(it.allocatedAmount)}"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StatusPaidGreen,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Contract Footer Actions
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (remainingContract > 0L) {
                                        Button(
                                            onClick = { onAllocatePaymentForContract(node) },
                                            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("btn_allocate_payment_${tx.id}")
                                        ) {
                                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Bayar / Alokasi Unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onPrintContractPdf(tx) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_print_contract_pdf_${tx.id}")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cetak Kontrak PDF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section 5: Customer Timeline & Activity History
 */
@Composable
fun CustomerTimelineSection(
    events: List<CustomerTimelineEventEntity>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "TIMELINE & RIWAYAT AKTIVITAS (${events.size} Aktivitas)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = NavyDark
        )
        if (events.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = IceWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Belum ada catatan timeline untuk pelanggan ini.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            events.forEach { ev ->
                val (badgeColor, icon) = when (ev.eventType) {
                    "REGISTRASI" -> SeaBluePrimary to Icons.Default.PersonAdd
                    "SEWA_UNIT" -> NavyDark to Icons.Default.AcUnit
                    "TAMBAH_UNIT" -> Color(0xFF0284C7) to Icons.Default.AddCircleOutline
                    "PEMBAYARAN" -> StatusPaidGreen to Icons.Default.Payments
                    "DOKUMENTASI" -> Color(0xFF0D9488) to Icons.Default.PhotoLibrary
                    "PENGEMBALIAN" -> StatusWarningYellow to Icons.Default.AssignmentReturn
                    else -> SeaBlueDark to Icons.Default.Timeline
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.14f))
                            .border(1.5.dp, badgeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = ev.eventType, tint = badgeColor, modifier = Modifier.size(17.dp))
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, SkyBlueSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ev.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    modifier = Modifier.weight(1f)
                                )
                                if (ev.amount > 0L) {
                                    Text(
                                        text = DateCurrencyUtils.formatRupiah(ev.amount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            if (ev.description.isNotBlank()) {
                                Text(
                                    text = ev.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = ev.eventTimestamp.ifBlank { ev.eventDateIso },
                                style = MaterialTheme.typography.labelSmall,
                                color = SeaBluePrimary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section 4: Partial Payment & Per-Unit Allocation Dialog
 * Example: Customer pays Rp400.000 of Rp600.000 (Unit A 150k + Unit B 250k + Unit C 200k).
 * Admin can allocate Rp150.000 to Unit A and Rp250.000 to Unit B while leaving Unit C unpaid!
 */
@Composable
fun SmartPartialPaymentAllocationDialog(
    transaction: RentalTransactionEntity,
    unitItems: List<TransactionUnitItemEntity>,
    paymentMethods: List<PaymentMethodEntity>,
    onDismiss: () -> Unit,
    onConfirmAllocate: (
        totalPaid: Long,
        allocations: Map<Long, Long>,
        paymentDate: String,
        metode: String,
        sender: String,
        notes: String
    ) -> Unit
) {
    val activeItems = remember(unitItems, transaction) {
        if (unitItems.isNotEmpty()) {
            unitItems
        } else {
            listOf(
                TransactionUnitItemEntity(
                    id = -transaction.id,
                    transactionId = transaction.id,
                    customerId = transaction.customerId,
                    unitId = transaction.unitId,
                    unitIndexNumber = 1,
                    kodeUnitSnapshot = transaction.kodeUnitSnapshot,
                    namaUnitSnapshot = transaction.namaUnitSnapshot,
                    hargaSewa = transaction.totalTagihan,
                    nominalPembayaran = transaction.nominalPembayaran,
                    tanggalJatuhTempo = transaction.tanggalJatuhTempo
                )
            )
        }
    }

    val defaultDueTotal = remember(activeItems) {
        activeItems.sumOf { it.sisaTagihanUnit }.coerceAtLeast(0L)
    }

    var totalAmountText by remember { mutableStateOf(defaultDueTotal.toString()) }
    var paymentDate by remember { mutableStateOf(DateCurrencyUtils.todayIso()) }
    val methodNames = remember(paymentMethods) {
        paymentMethods.filter { it.isAktif }.map { it.namaMetode }
            .ifEmpty { listOf("Transfer BCA", "Transfer BRI", "Transfer Mandiri", "QRIS", "Cash / Tunai") }
    }
    var selectedMethod by remember { mutableStateOf(transaction.metodePembayaran.ifBlank { methodNames.first() }) }
    var senderName by remember { mutableStateOf(transaction.namaPelangganSnapshot) }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val allocMap = remember {
        mutableStateMapOf<Long, String>().apply {
            activeItems.forEach { item ->
                put(item.id, item.sisaTagihanUnit.toString())
            }
        }
    }

    val totalBudget = totalAmountText.toLongOrNull() ?: 0L
    val totalAllocated = allocMap.values.sumOf { it.toLongOrNull() ?: 0L }
    val unallocatedBudget = totalBudget - totalAllocated

    fun autoDistributeFifo() {
        var remaining = totalAmountText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        activeItems.forEach { item ->
            val need = item.sisaTagihanUnit.coerceAtLeast(0L)
            val give = minOf(remaining, need)
            allocMap[item.id] = give.toString()
            remaining -= give
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ALOKASI PEMBAYARAN PER UNIT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyDark
                        )
                        Text(
                            text = "Kontrak ${transaction.nomorInvoice} • ${transaction.namaPelangganSnapshot}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                OutlinedTextField(
                    value = totalAmountText,
                    onValueChange = {
                        totalAmountText = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                    },
                    label = { Text("Total Nominal Dibayar Pelanggan (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_partial_payment_total")
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tentukan Alokasi ke Unit Tertentu:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    TextButton(
                        onClick = { autoDistributeFifo() },
                        modifier = Modifier.testTag("btn_auto_distribute_fifo")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagi Otomatis")
                    }
                }

                activeItems.forEachIndexed { idx, item ->
                    val currentVal = allocMap[item.id] ?: "0"
                    Card(
                        colors = CardDefaults.cardColors(containerColor = IceWhite),
                        border = BorderStroke(1.dp, SkyBlueSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Unit #${idx + 1}: ${item.namaUnitSnapshot} (${item.kodeUnitSnapshot})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Sisa: ${DateCurrencyUtils.formatRupiah(item.sisaTagihanUnit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusOverdueRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = currentVal,
                                    onValueChange = {
                                        allocMap[item.id] = it.filter { ch -> ch.isDigit() }
                                        errorMsg = null
                                    },
                                    label = { Text("Alokasi Rp") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_alloc_unit_${item.id}")
                                )
                                OutlinedButton(
                                    onClick = { allocMap[item.id] = item.sisaTagihanUnit.toString() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Lunasi Unit", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = { allocMap[item.id] = "0" },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text("Rp 0", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Allocation validation summary card
                Surface(
                    color = if (unallocatedBudget < 0L) StatusOverdueRedBg else StatusActiveBlueBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Total Alokasi: ${DateCurrencyUtils.formatRupiah(totalAllocated)} / ${DateCurrencyUtils.formatRupiah(totalBudget)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (unallocatedBudget < 0L) StatusOverdueRed else NavyDark
                        )
                        Text(
                            text = if (unallocatedBudget < 0L) {
                                "Total alokasi melebihi nominal dibayar!"
                            } else {
                                "Unit dengan alokasi Rp 0 tetap berstatus belum dibayar (tidak otomatis lunas)."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unallocatedBudget < 0L) StatusOverdueRed else SeaBlueDark
                        )
                    }
                }

                Text("Metode Pembayaran:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    methodNames.forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method, fontSize = 12.sp) },
                            colors = appFilterChipColors()
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = paymentDate,
                        onValueChange = { paymentDate = it },
                        label = { Text("Tanggal Bayar (YYYY-MM-DD)") },
                        singleLine = true,
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    SmartCaseOutlinedTextField(
                        value = senderName,
                        onValueChange = { senderName = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Nama Pengirim") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                SmartCaseOutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Catatan Pembayaran (Opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, color = StatusOverdueRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            val parsedTotal = totalAmountText.toLongOrNull() ?: 0L
                            val parsedMap = allocMap.mapValues { (_, v) -> v.toLongOrNull() ?: 0L }
                            val sumAlloc = parsedMap.values.sum()
                            when {
                                parsedTotal <= 0L -> errorMsg = "Nominal pembayaran harus > Rp 0."
                                sumAlloc <= 0L -> errorMsg = "Alokasikan minimal Rp 1 ke salah satu unit."
                                sumAlloc > parsedTotal -> errorMsg = "Total alokasi melebihi nominal dibayar."
                                else -> onConfirmAllocate(
                                    parsedTotal,
                                    parsedMap,
                                    paymentDate,
                                    selectedMethod,
                                    senderName,
                                    notes
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_confirm_allocate_payment")
                    ) {
                        Text("Simpan Alokasi")
                    }
                }
            }
        }
    }
}
