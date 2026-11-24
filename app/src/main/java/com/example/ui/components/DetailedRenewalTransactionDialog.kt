package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentMethodEntity
import com.example.data.local.RentalExtensionHistoryEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitPaymentAllocationEntity
import com.example.data.local.UnitRentalPriceEntity
import com.example.data.repository.CustomerActiveUnitRenewalInfo
import com.example.data.repository.DetailedRenewalRequest
import com.example.data.repository.NewUnitDuringRenewalSelection
import com.example.data.repository.RentalRenewalCalculator
import com.example.data.repository.UnitRenewalSelection
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.TextDateAccent
import com.example.ui.theme.TextDescription
import com.example.ui.theme.TextFormLabel
import com.example.ui.theme.TextPageTitle
import com.example.ui.theme.appCheckboxColors
import com.example.ui.theme.appFilterChipColors
import com.example.ui.theme.appOutlinedTextFieldColors
import com.example.ui.theme.appPrimaryButtonColors
import androidx.compose.runtime.rememberCoroutineScope
import com.example.util.DateCurrencyUtils
import com.example.util.FormValidationEngine
import com.example.util.FormValidationResult
import kotlinx.coroutines.launch

/**
 * Form Lengkap Transaksi Perpanjangan Sewa Terpadu:
 * A. Pilih Pelanggan (menampilkan Nama, WA, Alamat, Histori Penyewaan, Histori Perpanjangan, Unit Aktif)
 * B. Pilih Unit Spesifik yang Diperpanjang (bisa 1 atau lebih unit sekaligus)
 * C. Perpanjangan Program Reguler (harga master unit, periode, jatuh tempo otomatis, diskon, status bayar)
 * D. Perpanjangan Program Sewa Hak Milik (membaca histori pembayaran sebelumnya, otomatis ke-(X+1))
 * E. [+ TAMBAH UNIT SEWA BARU] (hanya unit Tersedia, multi-select, harga dari master data unit)
 * F. Kalkulasi Transaksi Terintegrasi & Simpan Atomik ke SQLite + Google Drive Sync
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedRenewalTransactionDialog(
    initialCustomerId: Long? = null,
    initialUnitId: Long? = null,
    customers: List<CustomerEntity>,
    units: List<FreezerUnitEntity>,
    unitRentalPrices: List<UnitRentalPriceEntity>,
    transactions: List<RentalTransactionEntity>,
    transactionUnitItems: List<TransactionUnitItemEntity>,
    unitPaymentAllocations: List<UnitPaymentAllocationEntity>,
    extensions: List<RentalExtensionHistoryEntity>,
    paymentMethods: List<PaymentMethodEntity>,
    loggedInOfficerName: String,
    loggedInOfficerAccountId: String,
    isProcessing: Boolean = false,
    onDismiss: () -> Unit,
    onSubmitDetailedRenewal: (DetailedRenewalRequest, onComplete: () -> Unit) -> Unit
) {
    // Build profiles for all customers who have active units
    val profilesByCustomerId = remember(
        customers,
        units,
        unitRentalPrices,
        transactions,
        transactionUnitItems,
        unitPaymentAllocations,
        extensions
    ) {
        customers.associate { cust ->
            cust.id to RentalRenewalCalculator.buildCustomerProfile(
                customer = cust,
                allUnits = units,
                allUnitPrices = unitRentalPrices,
                allTransactions = transactions,
                allUnitItems = transactionUnitItems,
                allAllocations = unitPaymentAllocations,
                allExtensions = extensions
            )
        }
    }

    val customersWithActiveUnits = remember(customers, profilesByCustomerId) {
        customers.filter { (profilesByCustomerId[it.id]?.activeUnits?.size ?: 0) > 0 }
    }
    val selectableCustomers = remember(customersWithActiveUnits, customers) {
        if (customersWithActiveUnits.isNotEmpty()) customersWithActiveUnits else customers
    }

    var selectedCustomerId by remember {
        mutableStateOf(
            initialCustomerId?.takeIf { id -> selectableCustomers.any { it.id == id } }
                ?: selectableCustomers.firstOrNull()?.id
                ?: 0L
        )
    }
    var customerDropdownExpanded by remember { mutableStateOf(false) }

    val activeProfile = profilesByCustomerId[selectedCustomerId]
    val renewalSelectionsMap = remember { mutableStateMapOf<Long, UnitRenewalSelection>() }
    var additionalNewUnits by remember { mutableStateOf<List<NewUnitDuringRenewalSelection>>(emptyList()) }
    var showAddNewUnitPicker by remember { mutableStateOf(false) }

    var ongkirInput by remember { mutableStateOf("0") }
    var globalDiscountInput by remember { mutableStateOf("0") }
    var metodePembayaran by remember {
        mutableStateOf(paymentMethods.firstOrNull { it.isAktif }?.namaMetode ?: "Transfer Bank")
    }
    var catatanUmum by remember { mutableStateOf("") }
    var validationResult by remember { mutableStateOf<FormValidationResult?>(null) }
    val bodyScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun buildDefaultSelection(info: CustomerActiveUnitRenewalInfo): UnitRenewalSelection {
        val defaultDays = 30
        val newStart = info.tanggalAkhirSewa.ifBlank { DateCurrencyUtils.todayIso() }
        val newEnd = DateCurrencyUtils.addDaysToIsoDate(newStart, defaultDays)
        val masterPrice = info.resolveMasterPriceForPeriod(defaultDays)
        return UnitRenewalSelection(
            unitId = info.unitId,
            unitItemId = info.unitItemId,
            sourceTransactionId = info.sourceTransactionId,
            sourceInvoiceNumber = info.sourceInvoiceNumber,
            kodeUnit = info.kodeUnit,
            namaUnit = info.namaUnit,
            nomorSeri = info.nomorSeri,
            jenisProgram = info.jenisProgram,
            periodDays = defaultDays,
            tanggalMulaiBaru = newStart,
            tanggalAkhirBaru = newEnd,
            tanggalJatuhTempoBaru = newEnd,
            hargaSewa = masterPrice,
            diskon = 0L,
            nominalDibayar = masterPrice,
            totalPeriodeHakMilik = info.totalPeriodeHakMilik,
            pembayaranKeSebelumnya = info.sudahDibayarHakMilik,
            pembayaranKeBaru = info.pembayaranBerikutnyaHakMilik,
            catatan = if (info.isHakMilik) {
                "Pembayaran Hak Milik ke-${info.pembayaranBerikutnyaHakMilik} dari ${info.totalPeriodeHakMilik}"
            } else {
                "Perpanjangan Sewa Reguler $defaultDays Hari"
            }
        )
    }

    // Reset selections when switching customer (and pre-select initialUnitId if opened from a specific tx)
    LaunchedEffect(selectedCustomerId, activeProfile?.activeUnits?.size) {
        renewalSelectionsMap.clear()
        additionalNewUnits = emptyList()
        validationResult = null
        val unitsForCust = activeProfile?.activeUnits.orEmpty()
        if (initialCustomerId != null && selectedCustomerId == initialCustomerId && initialUnitId != null) {
            unitsForCust.firstOrNull { it.unitId == initialUnitId }?.let { match ->
                renewalSelectionsMap[match.unitId] = buildDefaultSelection(match)
            }
        }
    }

    // Strictly available units for [+ TAMBAH UNIT SEWA BARU]
    val strictlyAvailableNewUnits = remember(units, additionalNewUnits, activeProfile) {
        val activeOwnedIds = activeProfile?.activeUnits?.map { it.unitId }?.toSet().orEmpty()
        val pickedNewIds = additionalNewUnits.map { it.unitId }.toSet()
        units.filter { u ->
            u.isStrictlyAvailableForNewRental() &&
                u.id !in activeOwnedIds &&
                u.id !in pickedNewIds
        }
    }

    val selectedRenewalsList = renewalSelectionsMap.values.toList()
    val ongkirVal = ongkirInput.toLongOrNull() ?: 0L
    val globalDiscountVal = globalDiscountInput.toLongOrNull() ?: 0L
    val integratedSummary = remember(selectedRenewalsList, additionalNewUnits, ongkirVal, globalDiscountVal) {
        RentalRenewalCalculator.calculateIntegratedSummary(
            renewals = selectedRenewalsList,
            newUnits = additionalNewUnits,
            ongkirTambahan = ongkirVal,
            globalDiscount = globalDiscountVal
        )
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .testTag("dialog_detailed_renewal"),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF8FAFC)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = NavyDark, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, tint = Color(0xFF38BDF8))
                            Column {
                                Text(
                                    text = "TRANSAKSI PERPANJANGAN SEWA",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Pilih unit spesifik (Reguler / Hak Milik) + opsional tambah unit sewa baru",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                        IconButton(onClick = { if (!isProcessing) onDismiss() }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = PureWhite)
                        }
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(bodyScrollState)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FormValidationAlertBanner(
                        validationResult = validationResult,
                        testTagName = "detailed_renewal_validation_banner"
                    )
                    // ==================== A. PILIH PELANGGAN ====================
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "A. PILIH PELANGGAN",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPageTitle
                                )
                            }

                            ExposedDropdownMenuBox(
                                expanded = customerDropdownExpanded,
                                onExpandedChange = { customerDropdownExpanded = !customerDropdownExpanded }
                            ) {
                                val currentCust = activeProfile?.customer
                                OutlinedTextField(
                                    value = currentCust?.let {
                                        "${it.namaLengkap} (${activeProfile.activeUnits.size} Unit Aktif)"
                                    } ?: "Pilih Pelanggan",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Pelanggan") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerDropdownExpanded) },
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .testTag("dropdown_renewal_customer")
                                )
                                ExposedDropdownMenu(
                                    expanded = customerDropdownExpanded,
                                    onDismissRequest = { customerDropdownExpanded = false }
                                ) {
                                    selectableCustomers.forEach { cust ->
                                        val count = profilesByCustomerId[cust.id]?.activeUnits?.size ?: 0
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(cust.namaLengkap, fontWeight = FontWeight.Bold, color = TextPageTitle)
                                                     Text(
                                                         "WA: ${cust.nomorHp.ifBlank { "-" }} • $count unit aktif disewa",
                                                         style = MaterialTheme.typography.labelSmall,
                                                         color = TextDescription
                                                     )
                                                 }
                                             },
                                             onClick = {
                                                 selectedCustomerId = cust.id
                                                 customerDropdownExpanded = false
                                             }
                                         )
                                     }
                                 }
                             }

                             if (activeProfile != null) {
                                 Surface(
                                     color = Color(0xFFF0F9FF),
                                     shape = RoundedCornerShape(10.dp),
                                     border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.25f)),
                                     modifier = Modifier.fillMaxWidth()
                                 ) {
                                     Column(
                                         modifier = Modifier.padding(10.dp),
                                         verticalArrangement = Arrangement.spacedBy(3.dp)
                                     ) {
                                         Text(
                                             text = activeProfile.customer.namaLengkap,
                                             style = MaterialTheme.typography.titleSmall,
                                             fontWeight = FontWeight.Bold,
                                             color = TextPageTitle
                                         )
                                         Text(
                                             text = "No. WhatsApp: ${activeProfile.customer.nomorHp.ifBlank { "-" }}",
                                             style = MaterialTheme.typography.bodySmall,
                                             color = TextFormLabel
                                         )
                                        Text(
                                            text = "Alamat: ${activeProfile.customer.alamatLengkap.ifBlank { "-" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextDescription
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFBAE6FD))
                                        Text(
                                            text = "Histori Penyewaan: ${activeProfile.rentalHistory.size} transaksi • Unit Aktif Disewa: ${activeProfile.activeUnits.size} unit • Histori Perpanjangan: ${activeProfile.extensionHistory.size}x",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SeaBluePrimary
                                        )
                                        if (activeProfile.extensionHistory.isNotEmpty()) {
                                            val latestExt = activeProfile.extensionHistory.first()
                                            Text(
                                                text = "Perpanjangan Terakhir: ${DateCurrencyUtils.formatDisplayDate(latestExt.tanggalPerpanjangan)} (${latestExt.nomorInvoiceBaru}) • ${latestExt.catatan}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextDescription
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==================== B. PILIH UNIT YANG DIPERPANJANG ====================
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Kitchen, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "B. PILIH UNIT YANG DIPERPANJANG",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPageTitle
                                    )
                                }
                                Surface(
                                    color = SeaBluePrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${selectedRenewalsList.size}/${activeProfile?.activeUnits?.size ?: 0} Dipilih",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SeaBluePrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            val activeUnits = activeProfile?.activeUnits.orEmpty()
                            if (activeUnits.isEmpty()) {
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Pelanggan ini tidak memiliki unit sewa aktif saat ini.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF92400E),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
                                activeUnits.forEachIndexed { idx, unitInfo ->
                                    val isChecked = renewalSelectionsMap.containsKey(unitInfo.unitId)
                                    val currentSel = renewalSelectionsMap[unitInfo.unitId]

                                    ActiveUnitRenewalCard(
                                        index = idx + 1,
                                        unitInfo = unitInfo,
                                        isSelected = isChecked,
                                        selection = currentSel,
                                        onToggleSelect = { checked ->
                                            if (checked) {
                                                renewalSelectionsMap[unitInfo.unitId] = buildDefaultSelection(unitInfo)
                                            } else {
                                                renewalSelectionsMap.remove(unitInfo.unitId)
                                            }
                                            validationResult = null
                                        },
                                        onUpdateSelection = { updated ->
                                            renewalSelectionsMap[unitInfo.unitId] = updated
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ==================== E. TAMBAH UNIT SEWA BARU SAAT PERPANJANGAN ====================
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "E. TAMBAH UNIT SEWA BARU (OPSIONAL)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPageTitle
                                    )
                                    Text(
                                        text = "Hanya menampilkan unit berstatus TERSEDIA (${strictlyAvailableNewUnits.size} unit siap disewa). Harga otomatis dari master data unit.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextDescription
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { showAddNewUnitPicker = true },
                                enabled = strictlyAvailableNewUnits.isNotEmpty() && !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_renewal_add_new_unit")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Tambah Unit Sewa Baru")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (strictlyAvailableNewUnits.isNotEmpty()) {
                                        "+ TAMBAH UNIT SEWA BARU (${strictlyAvailableNewUnits.size} Tersedia)"
                                    } else {
                                        "Tidak Ada Unit Tersedia Untuk Ditambahkan"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            additionalNewUnits.forEachIndexed { idx, newUnitSel ->
                                val masterUnit = units.firstOrNull { it.id == newUnitSel.unitId }
                                NewUnitDuringRenewalCard(
                                    index = idx + 1,
                                    selection = newUnitSel,
                                    onUpdate = { updated ->
                                        val enforcedMasterPrice = if (masterUnit != null) {
                                            RentalRenewalCalculator.resolveMasterPriceForUnit(
                                                unit = masterUnit,
                                                unitPrices = unitRentalPrices,
                                                jenisProgram = updated.jenisProgram,
                                                periodDays = updated.periodDays
                                            )
                                        } else updated.hargaSewaMaster
                                        val recalculatedEnd = DateCurrencyUtils.addDaysToIsoDate(
                                            updated.tanggalMulaiSewa.ifBlank { DateCurrencyUtils.todayIso() },
                                            updated.periodDays.coerceAtLeast(1)
                                        )
                                        val finalItem = updated.copy(
                                            hargaSewaMaster = enforcedMasterPrice,
                                            tanggalAkhirSewa = recalculatedEnd,
                                            nominalDibayar = if (updated.jenisProgram != newUnitSel.jenisProgram || updated.periodDays != newUnitSel.periodDays) {
                                                (enforcedMasterPrice - updated.diskon).coerceAtLeast(0L)
                                            } else updated.nominalDibayar
                                        )
                                        additionalNewUnits = additionalNewUnits.toMutableList().apply { set(idx, finalItem) }
                                    },
                                    onRemove = {
                                        additionalNewUnits = additionalNewUnits.toMutableList().apply { removeAt(idx) }
                                    }
                                )
                            }
                        }
                    }

                    // ==================== F. KALKULASI TRANSAKSI TERINTEGRASI ====================
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                        border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_renewal_integrated_summary")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "F. KALKULASI TRANSAKSI TERINTEGRASI",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPageTitle
                                )
                            }

                            selectedRenewalsList.forEachIndexed { idx, r ->
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Perpanjangan #${idx + 1}: ${r.namaUnit} (${r.kodeUnit})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPageTitle
                                        )
                                        Text(
                                            text = if (r.isHakMilik) {
                                                "Program Hak Milik • Pembayaran ke-${r.pembayaranKeBaru} dari ${r.totalPeriodeHakMilik}"
                                            } else {
                                                "Program Reguler • ${r.periodDays} Hari (${DateCurrencyUtils.formatDisplayDate(r.tanggalMulaiBaru)} s/d ${DateCurrencyUtils.formatDisplayDate(r.tanggalAkhirBaru)})"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextDescription
                                        )
                                    }
                                    Text(
                                        text = DateCurrencyUtils.formatRupiah(r.tagihanBersih),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SeaBluePrimary
                                    )
                                }
                            }

                            additionalNewUnits.forEachIndexed { idx, n ->
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Unit Baru #${idx + 1}: ${n.namaUnit} (${n.kodeUnit})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusPaidGreen
                                        )
                                        Text(
                                            text = "${n.jenisProgram} • ${n.periodDays} Hari • Master Price",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextDescription
                                        )
                                    }
                                    Text(
                                        text = DateCurrencyUtils.formatRupiah(n.tagihanBersih),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusPaidGreen
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFBAE6FD))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = ongkirInput,
                                    onValueChange = { ongkirInput = it.filter { c -> c.isDigit() } },
                                    label = { Text("Ongkir Tambahan (Rp)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = globalDiscountInput,
                                    onValueChange = { globalDiscountInput = it.filter { c -> c.isDigit() } },
                                    label = { Text("Diskon Tambahan (Rp)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = appOutlinedTextFieldColors(),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            SmartCaseOutlinedTextField(
                                value = catatanUmum,
                                onValueChange = { catatanUmum = it },
                                caseMode = InputTextCaseMode.UPPERCASE,
                                label = { Text("Catatan Transaksi Perpanjangan") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(color = Color(0xFFBAE6FD))

                            SummaryRowItem("Total Perpanjangan (${selectedRenewalsList.size} unit)", DateCurrencyUtils.formatRupiah(integratedSummary.totalRenewalGross))
                            if (additionalNewUnits.isNotEmpty()) {
                                SummaryRowItem("Total Unit Baru (${additionalNewUnits.size} unit)", DateCurrencyUtils.formatRupiah(integratedSummary.totalNewUnitsGross))
                            }
                            if (integratedSummary.totalItemDiscounts + integratedSummary.globalDiscount > 0L) {
                                SummaryRowItem(
                                    "Total Diskon",
                                    "- ${DateCurrencyUtils.formatRupiah(integratedSummary.totalItemDiscounts + integratedSummary.globalDiscount)}",
                                    valueColor = StatusOverdueRed
                                )
                            }
                            if (integratedSummary.ongkirTambahan > 0L) {
                                SummaryRowItem("Ongkir Tambahan", DateCurrencyUtils.formatRupiah(integratedSummary.ongkirTambahan))
                            }
                            SummaryRowItem(
                                "TOTAL AKHIR TRANSAKSI",
                                DateCurrencyUtils.formatRupiah(integratedSummary.grandTotal),
                                isBold = true,
                                valueColor = NavyDark
                            )
                            SummaryRowItem(
                                "Total Dibayar",
                                DateCurrencyUtils.formatRupiah(integratedSummary.totalPaid),
                                valueColor = StatusPaidGreen
                            )
                            SummaryRowItem(
                                "Sisa Tagihan (${integratedSummary.paymentStatus})",
                                DateCurrencyUtils.formatRupiah(integratedSummary.remainingBalance),
                                isBold = true,
                                valueColor = if (integratedSummary.remainingBalance > 0L) StatusOverdueRed else StatusPaidGreen
                            )
                        }
                    }

                }

                // Bottom Action Bar
                Surface(
                    color = PureWhite,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (!isProcessing) onDismiss() },
                            enabled = !isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val res = FormValidationEngine.validateRenewalForm(
                                    customerId = selectedCustomerId,
                                    renewedUnitCount = selectedRenewalsList.size,
                                    addedNewUnitCount = additionalNewUnits.size,
                                    metodePembayaran = metodePembayaran,
                                    anyInvalidDuration = selectedRenewalsList.any { it.periodDays <= 0 } || additionalNewUnits.any { it.periodDays <= 0 },
                                    anyNegativeOrZeroPrice = selectedRenewalsList.any { it.hargaSewa <= 0L } || additionalNewUnits.any { it.hargaSewaMaster <= 0L }
                                )
                                if (!res.isValid) {
                                    validationResult = res
                                    scope.launch { bodyScrollState.animateScrollTo(0) }
                                    return@Button
                                }
                                validationResult = null
                                val req = DetailedRenewalRequest(
                                    customerId = selectedCustomerId,
                                    selectedRenewals = selectedRenewalsList,
                                    additionalNewUnits = additionalNewUnits,
                                    ongkirTambahan = ongkirVal,
                                    diskonTambahan = globalDiscountVal,
                                    metodePembayaran = metodePembayaran,
                                    namaPetugas = loggedInOfficerName,
                                    officerAccountId = loggedInOfficerAccountId,
                                    catatanUmum = catatanUmum
                                )
                                onSubmitDetailedRenewal(req) {
                                    onDismiss()
                                }
                            },
                            enabled = !isProcessing,
                            colors = appPrimaryButtonColors(SeaBluePrimary),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                                .testTag("btn_submit_detailed_renewal")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = PureWhite,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Memproses...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan Perpanjangan", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Strict Available Unit Picker Dialog for [+ TAMBAH UNIT SEWA BARU]
    if (showAddNewUnitPicker) {
        AvailableUnitsMultiPickerDialog(
            availableUnits = strictlyAvailableNewUnits,
            unitRentalPrices = unitRentalPrices,
            onDismiss = { showAddNewUnitPicker = false },
            onConfirmAddUnits = { chosenUnits ->
                val today = DateCurrencyUtils.todayIso()
                val mapped = chosenUnits.map { u ->
                    val masterMonthly = RentalRenewalCalculator.resolveMasterPriceForUnit(
                        unit = u,
                        unitPrices = unitRentalPrices,
                        jenisProgram = "Sewa Reguler",
                        periodDays = 30
                    )
                    NewUnitDuringRenewalSelection(
                        unitId = u.id,
                        kodeUnit = u.kodeUnit,
                        namaUnit = u.namaUnit,
                        merek = u.merek,
                        kapasitasLiter = u.kapasitasLiter,
                        nomorSeri = u.nomorSeri,
                        jenisProgram = "Sewa Reguler",
                        periodDays = 30,
                        totalPeriodeHakMilik = u.jumlahCicilanTotal.takeIf { it > 0 } ?: 12,
                        hargaSewaMaster = masterMonthly,
                        diskon = 0L,
                        nominalDibayar = masterMonthly,
                        tanggalMulaiSewa = today,
                        tanggalAkhirSewa = DateCurrencyUtils.addDaysToIsoDate(today, 30)
                    )
                }
                additionalNewUnits = additionalNewUnits + mapped
                showAddNewUnitPicker = false
            }
        )
    }
}

@Composable
private fun ActiveUnitRenewalCard(
    index: Int,
    unitInfo: CustomerActiveUnitRenewalInfo,
    isSelected: Boolean,
    selection: UnitRenewalSelection?,
    onToggleSelect: (Boolean) -> Unit,
    onUpdateSelection: (UnitRenewalSelection) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFFF0F9FF) else Color(0xFFF8FAFC),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) SeaBluePrimary else Color(0xFFCBD5E1)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("renewal_unit_card_${unitInfo.unitId}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSelect(!isSelected) }
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onToggleSelect,
                    colors = appCheckboxColors(),
                    modifier = Modifier.testTag("chk_renewal_unit_${unitInfo.unitId}")
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$index. ${unitInfo.namaUnit} (${unitInfo.kodeUnit})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPageTitle
                    )
                    Text(
                        text = "No. Seri: ${unitInfo.nomorSeri.ifBlank { "-" }} • Program: ${unitInfo.jenisProgram}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unitInfo.isHakMilik) Color(0xFF7C3AED) else SeaBluePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Mulai: ${DateCurrencyUtils.formatDisplayDate(unitInfo.tanggalMulaiSewa)} • Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(unitInfo.tanggalJatuhTempo)} • Status: ${unitInfo.statusPembayaran}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDateAccent
                    )
                    if (unitInfo.isHakMilik) {
                        Text(
                            text = "Hak Milik: Sudah dibayar ${unitInfo.sudahDibayarHakMilik} dari ${unitInfo.totalPeriodeHakMilik} kali • Sisa: ${unitInfo.sisaPeriodeHakMilik} kali",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9)
                        )
                    }
                }
            }

            if (isSelected && selection != null) {
                HorizontalDivider(color = Color(0xFFBAE6FD))

                if (unitInfo.isHakMilik) {
                    // D. PERPANJANGAN PROGRAM SEWA HAK MILIK
                    Surface(
                        color = Color(0xFFF5F3FF),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(16.dp))
                                Text(
                                    text = "PROGRAM SEWA HAK MILIK (OTOMATIS LANJUT HISTORI)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5B21B6)
                                )
                            }
                            Text(
                                text = "Total Kewajiban: ${unitInfo.totalPeriodeHakMilik} kali • Sudah Dibayar: ${unitInfo.sudahDibayarHakMilik} kali • Sisa: ${unitInfo.sisaPeriodeHakMilik} kali",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPageTitle
                            )
                            Text(
                                text = "Pembayaran Berikutnya: Pembayaran ke-${selection.pembayaranKeBaru} dari ${selection.totalPeriodeHakMilik}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6D28D9),
                                modifier = Modifier.testTag("txt_hm_next_payment_${unitInfo.unitId}")
                            )
                            if (unitInfo.historiTanggalPembayaran.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = TextDescription, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = "Histori tanggal bayar: ${unitInfo.historiTanggalPembayaran.joinToString(", ") { DateCurrencyUtils.formatDisplayDate(it) }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextDescription
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // C. PERPANJANGAN PROGRAM REGULER - Period chips
                    Text(
                        text = "Periode Perpanjangan Reguler (Harga Master Unit):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextFormLabel
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf(7, 14, 30, 60, 90).forEach { days ->
                            FilterChip(
                                selected = selection.periodDays == days,
                                onClick = {
                                    val newEnd = DateCurrencyUtils.addDaysToIsoDate(selection.tanggalMulaiBaru, days)
                                    val newPrice = unitInfo.resolveMasterPriceForPeriod(days)
                                    onUpdateSelection(
                                        selection.copy(
                                            periodDays = days,
                                            tanggalAkhirBaru = newEnd,
                                            tanggalJatuhTempoBaru = newEnd,
                                            hargaSewa = newPrice,
                                            nominalDibayar = (newPrice - selection.diskon).coerceAtLeast(0L)
                                        )
                                    )
                                },
                                label = { Text("$days Hari", style = MaterialTheme.typography.labelSmall) },
                                colors = appFilterChipColors()
                            )
                        }
                    }
                }

                // Auto-calculated period display
                Text(
                    text = "Periode Baru: ${DateCurrencyUtils.formatDisplayDate(selection.tanggalMulaiBaru)} s/d ${DateCurrencyUtils.formatDisplayDate(selection.tanggalAkhirBaru)} (Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(selection.tanggalJatuhTempoBaru)})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextDateAccent
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selection.hargaSewa.toString(),
                        onValueChange = { v ->
                            val parsed = v.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            onUpdateSelection(selection.copy(hargaSewa = parsed))
                        },
                        label = { Text(if (unitInfo.isHakMilik) "Nominal Cicilan (Rp)" else "Harga Sewa (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = selection.diskon.toString(),
                        onValueChange = { v ->
                            val disc = v.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            onUpdateSelection(selection.copy(diskon = disc))
                        },
                        label = { Text("Diskon (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selection.nominalDibayar.toString(),
                        onValueChange = { v ->
                            val paid = v.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            onUpdateSelection(selection.copy(nominalDibayar = paid))
                        },
                        label = { Text("Nominal Dibayar (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = if (selection.statusPembayaran == "Lunas") Color(0xFFD1FAE5) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                text = selection.statusPembayaran,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selection.statusPembayaran == "Lunas") Color(0xFF065F46) else Color(0xFF92400E)
                            )
                            Text(
                                text = DateCurrencyUtils.formatRupiah(selection.tagihanBersih),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPageTitle,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewUnitDuringRenewalCard(
    index: Int,
    selection: NewUnitDuringRenewalSelection,
    onUpdate: (NewUnitDuringRenewalSelection) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFECFDF5),
        border = BorderStroke(1.dp, StatusPaidGreen.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("renewal_new_unit_card_${selection.unitId}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Unit Baru #$index: ${selection.namaUnit} (${selection.kodeUnit})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPageTitle
                    )
                    Text(
                        text = "${selection.merek} • ${selection.kapasitasLiter}L • No. Seri: ${selection.nomorSeri.ifBlank { "-" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDescription
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus Unit Tambahan", tint = StatusOverdueRed)
                }
            }

            // Program choice: Sewa Reguler vs Sewa Hak Milik (uses master unit data)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Sewa Reguler", "Sewa Hak Milik").forEach { prog ->
                    FilterChip(
                        selected = selection.jenisProgram.equals(prog, ignoreCase = true),
                        onClick = { onUpdate(selection.copy(jenisProgram = prog)) },
                        label = { Text(prog, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        colors = appFilterChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!selection.isHakMilik) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf(7, 14, 30, 60, 90).forEach { days ->
                        FilterChip(
                            selected = selection.periodDays == days,
                            onClick = { onUpdate(selection.copy(periodDays = days)) },
                            label = { Text("$days Hari", style = MaterialTheme.typography.labelSmall) },
                            colors = appFilterChipColors()
                        )
                    }
                }
            }

            Text(
                text = "Harga Master Unit (${selection.jenisProgram}): ${DateCurrencyUtils.formatRupiah(selection.hargaSewaMaster)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = StatusPaidGreen
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selection.diskon.toString(),
                    onValueChange = { v ->
                        val disc = v.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        onUpdate(selection.copy(diskon = disc))
                    },
                    label = { Text("Diskon (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = selection.nominalDibayar.toString(),
                    onValueChange = { v ->
                        val paid = v.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        onUpdate(selection.copy(nominalDibayar = paid))
                    },
                    label = { Text("Dibayar (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AvailableUnitsMultiPickerDialog(
    availableUnits: List<FreezerUnitEntity>,
    unitRentalPrices: List<UnitRentalPriceEntity>,
    onDismiss: () -> Unit,
    onConfirmAddUnits: (List<FreezerUnitEntity>) -> Unit
) {
    val selectedIds = remember { mutableStateMapOf<Long, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pilih Unit Tersedia (${availableUnits.size})",
                fontWeight = FontWeight.Bold,
                color = TextPageTitle
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Hanya unit berstatus TERSEDIA yang ditampilkan (unit Disewa, Hilang, dan Perbaikan otomatis disembunyikan).",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDescription
                )
                availableUnits.forEach { u ->
                    val checked = selectedIds[u.id] == true
                    val masterPrice = RentalRenewalCalculator.resolveMasterPriceForUnit(
                        unit = u,
                        unitPrices = unitRentalPrices,
                        jenisProgram = "Sewa Reguler",
                        periodDays = 30
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (checked) Color(0xFFE0F2FE) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (checked) SeaBluePrimary else Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedIds[u.id] = !checked }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selectedIds[u.id] = it },
                                colors = appCheckboxColors()
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${u.namaUnit} (${u.kodeUnit})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPageTitle
                                )
                                Text(
                                    text = "${u.merek} • ${u.kapasitasLiter}L • Seri: ${u.nomorSeri.ifBlank { "-" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDescription
                                )
                                Text(
                                    text = "Master Reguler 30hr: ${DateCurrencyUtils.formatRupiah(masterPrice)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SeaBluePrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val chosen = availableUnits.filter { selectedIds[it.id] == true }
            Button(
                onClick = { onConfirmAddUnits(chosen) },
                enabled = chosen.isNotEmpty(),
                colors = appPrimaryButtonColors(SeaBluePrimary)
            ) {
                Text("Tambahkan (${chosen.size} Unit)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun SummaryRowItem(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = TextPageTitle
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = TextFormLabel
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}
