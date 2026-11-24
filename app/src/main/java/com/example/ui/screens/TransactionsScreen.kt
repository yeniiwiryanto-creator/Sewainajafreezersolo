package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.contacts.PhoneNumberNormalizer
import com.example.contacts.WhatsAppContactPickerTrailingButton
import com.example.contacts.rememberWhatsAppContactImporter
import com.example.ui.components.InputTextCaseMode
import com.example.ui.components.SmartCaseOutlinedTextField
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerPhotoEntity
import com.example.data.local.DEFAULT_SEWA_HAK_MILIK_PERIODS
import com.example.data.local.DrivePhotoMetadataEntity
import com.example.data.local.FormDraftEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentMethodEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalExtensionHistoryEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionOfficerAuditLogEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitPaymentAllocationEntity
import com.example.data.local.UnitRentalPriceEntity
import com.example.data.repository.DetailedRenewalRequest
import com.example.data.repository.DetailedRenewalResult
import com.example.data.repository.UnitReservationManager
import com.example.location.GpsLocationResult
import com.example.location.LocationGpsHelper
import com.example.pdf.InvoicePdfGenerator
import com.example.storage.CustomerPhotoStorageHelper
import com.example.ui.components.CameraXCaptureDialog
import com.example.ui.components.DetailedRenewalTransactionDialog
import com.example.ui.components.DigitalSignaturePad
import com.example.ui.components.DraftAutoSaveStatusPill
import com.example.ui.components.FormDraftCodec
import com.example.ui.components.FormDraftTypes
import com.example.ui.components.FreezerUnitThumbnail
import com.example.ui.components.GpsLocationMapCard
import com.example.ui.components.LocationDocPhotoDisplayItem
import com.example.ui.components.LocationDocSlots
import com.example.ui.components.LocationFreezerDocumentationCard
import com.example.ui.components.MultiUnitPhotoDocumentationSection
import com.example.ui.components.MultiUnitRentalSection
import com.example.ui.components.PhotoDocumentationSlot
import com.example.ui.components.ResumeDraftBanner
import com.example.ui.components.SmartPartialPaymentAllocationDialog
import com.example.ui.theme.IceWhite
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
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
import com.example.ui.theme.TextDateAccent
import com.example.ui.components.FieldErrorSupportingText
import com.example.ui.components.FormValidationAlertBanner
import com.example.ui.components.StorageSyncStatusBadge
import com.example.ui.theme.appFilterChipColors
import com.example.ui.theme.appOutlinedTextFieldColors
import com.example.util.DateCurrencyUtils
import com.example.util.FormValidationEngine
import com.example.util.FormValidationResult
import com.example.util.StorageSyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.ceil

val STATUS_PEMBAYARAN_OPTIONS = listOf(
    "Belum Bayar",
    "Dibayar Sebagian",
    "Lunas",
    "Terlambat"
)

val STATUS_PENYEWAAN_OPTIONS = listOf(
    "Booking",
    "Dalam Penyewaan",
    "Selesai",
    "Dibatalkan"
)

val METODE_PEMBAYARAN_OPTIONS = listOf(
    "Transfer BCA",
    "Transfer BRI",
    "Transfer Mandiri",
    "Transfer BNI",
    "QRIS",
    "Cash / Tunai"
)

val JENIS_PROGRAM_OPTIONS = listOf(
    "Sewa Reguler",
    "Sewa Hak Milik"
)

val STATUS_PROGRAM_HAK_MILIK_OPTIONS = listOf(
    "Aktif",
    "Lunas",
    "Selesai"
)

@Composable
fun TransactionsScreen(
    transactions: List<RentalTransactionEntity>,
    customers: List<CustomerEntity>,
    units: List<FreezerUnitEntity>,
    paymentMethods: List<PaymentMethodEntity> = emptyList(),
    paymentProofs: List<PaymentProofEntity> = emptyList(),
    transactionUnitItems: List<TransactionUnitItemEntity> = emptyList(),
    unitPaymentAllocations: List<UnitPaymentAllocationEntity> = emptyList(),
    sewaHakMilikPeriods: List<Int> = DEFAULT_SEWA_HAK_MILIK_PERIODS,
    externallySelectedTx: RentalTransactionEntity?,
    preselectedCustomerForNewTx: CustomerEntity? = null,
    onClearExternalTx: () -> Unit,
    onClearPreselectedCustomer: () -> Unit = {},
    onSaveTransaction: (RentalTransactionEntity, (RentalTransactionEntity) -> Unit) -> Unit,
    onSaveMultiUnitTransaction: (RentalTransactionEntity, List<TransactionUnitItemEntity>, (RentalTransactionEntity) -> Unit) -> Unit = { tx, _, cb ->
        onSaveTransaction(tx, cb)
    },
    onResolveFreshUnitPrice: (suspend (Long, Int, String) -> Long)? = null,
    onSavePaymentProof: (PaymentProofEntity) -> Unit = {},
    onDeletePaymentProof: (PaymentProofEntity) -> Unit = {},
    onAllocatePartialPayment: (Long, Long, Map<Long, Long>, String, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onExtendRentalByDays: (RentalTransactionEntity, Int, Long, Long, Long, String) -> Unit,
    onGenerateInvoicePdf: (RentalTransactionEntity, (File) -> Unit) -> Unit,
    onCaptureGpsLocation: ((GpsLocationResult) -> Unit) -> Unit,
    onPostMessage: (String) -> Unit,
    onSaveCustomerInline: (CustomerEntity, (CustomerEntity) -> Unit) -> Unit = { _, _ -> },
    onDeleteTransaction: (RentalTransactionEntity) -> Unit = {},
    unitRentalPrices: List<UnitRentalPriceEntity> = emptyList(),
    loggedInOfficerName: String = "",
    loggedInOfficerAccountId: String = "",
    transactionOfficerAuditLogs: List<TransactionOfficerAuditLogEntity> = emptyList(),
    formDrafts: List<FormDraftEntity> = emptyList(),
    pendingDraftToOpen: FormDraftEntity? = null,
    onPendingDraftConsumed: () -> Unit = {},
    onSaveDraft: (String, Long, String, String, String, String, (FormDraftEntity) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteDraft: (String) -> Unit = {},
    onRecordUnitHakMilikPayment: (Long, Long, Long, String, String, String, String, String, Boolean) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onCorrectOfficerWithAuditLog: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    customerPhotos: List<CustomerPhotoEntity> = emptyList(),
    drivePhotoMetadataList: List<DrivePhotoMetadataEntity> = emptyList(),
    extensions: List<RentalExtensionHistoryEntity> = emptyList(),
    isProcessingOperation: Boolean = false,
    onSaveLocationPhoto: (CustomerEntity, Long?, Long?, String, Any, Double?, Double?) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteCustomerPhoto: (CustomerPhotoEntity) -> Unit = {},
    onRetryLocationPhotoUpload: (CustomerPhotoEntity) -> Unit = {},
    onSubmitDetailedRenewal: (DetailedRenewalRequest, (DetailedRenewalResult) -> Unit) -> Unit = { _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("Semua") }

    var activeTransactionDetail by remember { mutableStateOf<RentalTransactionEntity?>(null) }
    var editingTransaction by remember { mutableStateOf<RentalTransactionEntity?>(null) }
    var stagedPreselectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var showTransactionForm by remember { mutableStateOf(false) }
    var activePendingDraft by remember { mutableStateOf<FormDraftEntity?>(null) }
    var extendingTransaction by remember { mutableStateOf<RentalTransactionEntity?>(null) }
    var showDetailedRenewalModal by remember { mutableStateOf(false) }
    var pendingDeleteTransaction by remember { mutableStateOf<RentalTransactionEntity?>(null) }
    var pdfViewerFile by remember { mutableStateOf<File?>(null) }

    val unitRentalPricesMap = remember(unitRentalPrices) {
        unitRentalPrices.groupBy { it.unitId }
    }
    val transactionDrafts = remember(formDrafts) {
        formDrafts.filter { it.formType == FormDraftTypes.TRANSACTION }
    }

    LaunchedEffect(externallySelectedTx) {
        if (externallySelectedTx != null) {
            activeTransactionDetail = externallySelectedTx
            onClearExternalTx()
        }
    }

    LaunchedEffect(preselectedCustomerForNewTx) {
        if (preselectedCustomerForNewTx != null) {
            stagedPreselectedCustomer = preselectedCustomerForNewTx
            editingTransaction = null
            activePendingDraft = null
            showTransactionForm = true
            onClearPreselectedCustomer()
        }
    }

    LaunchedEffect(pendingDraftToOpen) {
        if (pendingDraftToOpen != null && pendingDraftToOpen.formType == FormDraftTypes.TRANSACTION) {
            editingTransaction = if (pendingDraftToOpen.targetEntityId > 0L) {
                transactions.find { it.id == pendingDraftToOpen.targetEntityId }
            } else null
            activePendingDraft = pendingDraftToOpen
            showTransactionForm = true
            onPendingDraftConsumed()
        }
    }

    val filteredTx = remember(transactions, searchQuery, selectedStatusFilter) {
        transactions.filter { tx ->
            val matchQuery = searchQuery.isBlank() ||
                tx.nomorInvoice.contains(searchQuery, ignoreCase = true) ||
                tx.namaPelangganSnapshot.contains(searchQuery, ignoreCase = true) ||
                tx.namaUnitSnapshot.contains(searchQuery, ignoreCase = true) ||
                tx.kodeUnitSnapshot.contains(searchQuery, ignoreCase = true) ||
                tx.jenisProgram.contains(searchQuery, ignoreCase = true)
            val matchFilter = selectedStatusFilter == "Semua" ||
                tx.jenisProgram.equals(selectedStatusFilter, ignoreCase = true) ||
                tx.statusPembayaran.equals(selectedStatusFilter, ignoreCase = true) ||
                tx.statusPenyewaan.equals(selectedStatusFilter, ignoreCase = true)
            matchQuery && matchFilter
        }
    }

    var visibleLimit by remember(searchQuery, selectedStatusFilter) { mutableStateOf(50) }
    val pagedTx = remember(filteredTx, visibleLimit) { filteredTx.take(visibleLimit) }

    val sewaRegulerTxList = remember(transactions) {
        transactions.filter { !it.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true) && it.statusPenyewaan != "Dibatalkan" }
    }
    val sewaHakMilikTxList = remember(transactions) {
        transactions.filter { it.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true) && it.statusPenyewaan != "Dibatalkan" }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTransaction = null
                    showTransactionForm = true
                },
                containerColor = SeaBluePrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_transaction")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Buat Transaksi Sewa")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Transaksi Baru", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("transactions_screen")
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            SmartCaseOutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                caseMode = InputTextCaseMode.UPPERCASE,
                placeholder = { Text("Cari No. Invoice, Pelanggan, atau Unit Freezer...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari Transaksi") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_transaction_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                (listOf("Semua", "Sewa Reguler", "Sewa Hak Milik") + STATUS_PEMBAYARAN_OPTIONS + STATUS_PENYEWAAN_OPTIONS).forEach { filterLabel ->
                    FilterChip(
                        selected = selectedStatusFilter == filterLabel,
                        onClick = { selectedStatusFilter = filterLabel },
                        label = { Text(filterLabel, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // Separated Summary Strip: Sewa Reguler vs Sewa Hak Milik (Never mixed in calculation)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Surface(
                    color = StatusActiveBlueBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = "SEWA REGULER (${sewaRegulerTxList.size} Transaksi)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusActiveBlue
                        )
                        Text(
                            text = "Total: ${DateCurrencyUtils.formatRupiah(sewaRegulerTxList.sumOf { it.totalTagihan })}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NavyDark
                        )
                    }
                }
                Surface(
                    color = Color(0xFFEDE9FE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = "SEWA HAK MILIK (${sewaHakMilikTxList.size} Transaksi)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9)
                        )
                        Text(
                            text = "Total: ${DateCurrencyUtils.formatRupiah(sewaHakMilikTxList.sumOf { it.totalTagihan })}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NavyDark
                        )
                    }
                }
            }

            // Quick Action: Transaksi Perpanjangan Sewa Detail (Multi-Unit + Hak Milik + Tambah Unit Baru)
            FilledTonalButton(
                onClick = {
                    extendingTransaction = null
                    showDetailedRenewalModal = true
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFE0F2FE),
                    contentColor = NavyDark
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(vertical = 2.dp)
                    .testTag("btn_open_detailed_renewal")
            ) {
                Icon(Icons.Default.Autorenew, contentDescription = null, tint = SeaBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Perpanjangan Sewa & Tambah Unit Pelanggan", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pagedTx, key = { it.id }) { tx ->
                    val matchedUnit = units.find { it.id == tx.unitId }
                    val txProofsCount = paymentProofs.count { it.transactionId == tx.id }
                    val txUnitItems = transactionUnitItems.filter { it.transactionId == tx.id }
                    TransactionCardItem(
                        tx = tx,
                        matchedUnit = matchedUnit,
                        txUnitItems = txUnitItems,
                        paymentProofsCount = txProofsCount,
                        onClickDetail = { activeTransactionDetail = tx },
                        onClickExtend = { extendingTransaction = tx },
                        onClickEdit = {
                            editingTransaction = tx
                            showTransactionForm = true
                        },
                        onClickDelete = {
                            pendingDeleteTransaction = tx
                        }
                    )
                }
                if (filteredTx.size > pagedTx.size) {
                    item(key = "load_more_transactions") {
                        OutlinedButton(
                            onClick = { visibleLimit += 50 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_load_more_transactions")
                        ) {
                            Text("Muat Lebih Banyak (${filteredTx.size - pagedTx.size} lagi)")
                        }
                    }
                }
            }
        }
    }

    if (showTransactionForm) {
        val existingTxProofs = remember(paymentProofs, editingTransaction) {
            if (editingTransaction != null) {
                paymentProofs.filter { it.transactionId == editingTransaction!!.id }
            } else emptyList()
        }
        val existingTxUnitItems = remember(transactionUnitItems, editingTransaction) {
            if (editingTransaction != null) {
                transactionUnitItems.filter { it.transactionId == editingTransaction!!.id }
            } else emptyList()
        }
        TransactionAndHandoverFormDialog(
            initialTransaction = editingTransaction,
            initialUnitItems = existingTxUnitItems,
            preselectedCustomer = stagedPreselectedCustomer,
            customers = customers,
            allTransactions = transactions,
            units = units,
            unitRentalPricesMap = unitRentalPricesMap,
            loggedInOfficerName = loggedInOfficerName,
            loggedInOfficerAccountId = loggedInOfficerAccountId,
            existingDrafts = transactionDrafts,
            initialDraftToApply = activePendingDraft,
            paymentMethods = paymentMethods,
            existingPaymentProofs = existingTxProofs,
            sewaHakMilikPeriods = sewaHakMilikPeriods,
            customerPhotos = customerPhotos,
            drivePhotoMetadataList = drivePhotoMetadataList,
            isUploadingPhoto = isProcessingOperation,
            onSaveLocationPhoto = onSaveLocationPhoto,
            onDeleteCustomerPhoto = onDeleteCustomerPhoto,
            onRetryLocationPhotoUpload = onRetryLocationPhotoUpload,
            onDismiss = {
                stagedPreselectedCustomer = null
                activePendingDraft = null
                showTransactionForm = false
            },
            onCaptureGpsLocation = onCaptureGpsLocation,
            onSaveCustomerInline = onSaveCustomerInline,
            onSavePaymentProof = onSavePaymentProof,
            onDeletePaymentProof = onDeletePaymentProof,
            onSaveDraft = onSaveDraft,
            onDeleteDraft = onDeleteDraft,
            onCorrectOfficerWithAuditLog = onCorrectOfficerWithAuditLog,
            onResolveFreshUnitPrice = onResolveFreshUnitPrice,
            onPostMessage = onPostMessage,
            onSave = { txToSave, unitItemsToSave, pendingProof, stagedLocationPhotos ->
                onSaveMultiUnitTransaction(txToSave, unitItemsToSave) { savedTx ->
                    if (pendingProof != null && pendingProof.fotoBuktiUri.isNotBlank()) {
                        onSavePaymentProof(
                            pendingProof.copy(transactionId = savedTx.id)
                        )
                    }
                    if (stagedLocationPhotos.isNotEmpty()) {
                        val resolvedCust = customers.find { it.id == savedTx.customerId }
                            ?: CustomerEntity(
                                id = savedTx.customerId,
                                namaLengkap = savedTx.namaPelangganSnapshot,
                                nik = "",
                                nomorHp = savedTx.nomorWhatsappLokasi,
                                alamatLengkap = savedTx.alamatLokasiGps,
                                jenisKelamin = "Laki-laki",
                                tanggalRegistrasi = DateCurrencyUtils.todayIso()
                            )
                        stagedLocationPhotos.forEach { staged ->
                            onSaveLocationPhoto(
                                resolvedCust,
                                savedTx.id,
                                savedTx.unitId.takeIf { it > 0L },
                                staged.slotCode,
                                staged.source,
                                staged.latitude,
                                staged.longitude
                            )
                        }
                    }
                    stagedPreselectedCustomer = null
                    activePendingDraft = null
                    showTransactionForm = false
                    activeTransactionDetail = savedTx
                }
            }
        )
    }

    if (activeTransactionDetail != null) {
        val currentTx = transactions.find { it.id == activeTransactionDetail!!.id } ?: activeTransactionDetail!!
        val cust = customers.find { it.id == currentTx.customerId }
        val unit = units.find { it.id == currentTx.unitId }
        val custHistory = transactions.filter { it.customerId == currentTx.customerId }
        val txProofs = paymentProofs.filter { it.transactionId == currentTx.id }
        val txUnitItems = transactionUnitItems.filter { it.transactionId == currentTx.id }
        val txAllocations = unitPaymentAllocations.filter { it.transactionId == currentTx.id }
        val txAuditLogs = transactionOfficerAuditLogs.filter { it.transactionId == currentTx.id }
        TransactionInvoiceAndHandoverDetailDialog(
            tx = currentTx,
            matchedCustomer = cust,
            customerHistoryTransactions = custHistory,
            matchedUnit = unit,
            txUnitItems = txUnitItems,
            txUnitAllocations = txAllocations,
            officerAuditLogs = txAuditLogs,
            paymentMethods = paymentMethods,
            transactionPaymentProofs = txProofs,
            customerPhone = cust?.nomorHp ?: "",
            onDismiss = { activeTransactionDetail = null },
            onEditHandoverOrTx = {
                activeTransactionDetail = null
                editingTransaction = currentTx
                activePendingDraft = null
                showTransactionForm = true
            },
            onExtendRental = {
                activeTransactionDetail = null
                extendingTransaction = currentTx
            },
            onSavePaymentProof = onSavePaymentProof,
            onDeletePaymentProof = onDeletePaymentProof,
            onAllocatePartialPayment = onAllocatePartialPayment,
            onRecordUnitHakMilikPayment = onRecordUnitHakMilikPayment,
            onCorrectOfficerWithAuditLog = onCorrectOfficerWithAuditLog,
            onDeleteTransaction = {
                onDeleteTransaction(currentTx)
                activeTransactionDetail = null
            },
            onGeneratePdf = { onReady ->
                onGenerateInvoicePdf(currentTx, onReady)
            },
            onOpenPdfViewer = { file ->
                pdfViewerFile = file
            },
            onPostMessage = onPostMessage
        )
    }

    if (showDetailedRenewalModal || extendingTransaction != null) {
        val txToExtend = extendingTransaction
        DetailedRenewalTransactionDialog(
            initialCustomerId = txToExtend?.customerId,
            initialUnitId = txToExtend?.unitId,
            customers = customers,
            units = units,
            unitRentalPrices = unitRentalPrices,
            transactions = transactions,
            transactionUnitItems = transactionUnitItems,
            unitPaymentAllocations = unitPaymentAllocations,
            extensions = extensions,
            paymentMethods = paymentMethods,
            loggedInOfficerName = loggedInOfficerName,
            loggedInOfficerAccountId = loggedInOfficerAccountId,
            isProcessing = isProcessingOperation,
            onDismiss = {
                extendingTransaction = null
                showDetailedRenewalModal = false
            },
            onSubmitDetailedRenewal = { request, onDialogComplete ->
                onSubmitDetailedRenewal(request) { result ->
                    onDialogComplete()
                    extendingTransaction = null
                    showDetailedRenewalModal = false
                    activeTransactionDetail = result.transaction
                }
            }
        )
    }

    if (pdfViewerFile != null) {
        InAppPdfViewerDialog(
            pdfFile = pdfViewerFile!!,
            onDismiss = { pdfViewerFile = null }
        )
    }

    if (pendingDeleteTransaction != null) {
        val txToDelete = pendingDeleteTransaction!!
        val matchedCust = customers.find { it.id == txToDelete.customerId }
        val custCode = matchedCust?.kodePelanggan?.ifBlank {
            "CUST/SAF/${txToDelete.customerId.toString().padStart(4, '0')}"
        } ?: "CUST/SAF/${txToDelete.customerId.toString().padStart(4, '0')}"
        val txUnitCount = transactionUnitItems.count { it.transactionId == txToDelete.id }.coerceAtLeast(1)
        DeleteTransactionConfirmDialog(
            tx = txToDelete,
            unitCount = txUnitCount,
            customerCode = custCode,
            onDismiss = { pendingDeleteTransaction = null },
            onConfirm = {
                pendingDeleteTransaction = null
                if (activeTransactionDetail?.id == txToDelete.id) {
                    activeTransactionDetail = null
                }
                onDeleteTransaction(txToDelete)
            }
        )
    }
}

@Composable
private fun TransactionCardItem(
    tx: RentalTransactionEntity,
    matchedUnit: FreezerUnitEntity?,
    txUnitItems: List<TransactionUnitItemEntity> = emptyList(),
    paymentProofsCount: Int = 0,
    onClickDetail: () -> Unit,
    onClickExtend: () -> Unit,
    onClickEdit: () -> Unit,
    onClickDelete: () -> Unit = {}
) {
    val daysDiff = DateCurrencyUtils.daysFromToday(tx.tanggalJatuhTempo)

    val (payBg, payFg) = when {
        tx.statusPembayaran == "Terlambat" || (daysDiff < 0 && tx.statusPembayaran != "Lunas") ->
            StatusOverdueRedBg to StatusOverdueRed
        tx.statusPembayaran == "Lunas" ->
            StatusPaidGreenBg to StatusPaidGreen
        else ->
            StatusWarningYellowBg to StatusWarningYellow
    }

    val (rentBg, rentFg) = when (tx.statusPenyewaan) {
        "Dalam Penyewaan" -> if (daysDiff in 0..7) StatusWarningYellowBg to StatusWarningYellow else StatusActiveBlueBg to StatusActiveBlue
        "Selesai" -> StatusPaidGreenBg to StatusPaidGreen
        "Dibatalkan" -> StatusOverdueRedBg to StatusOverdueRed
        else -> StatusWarningYellowBg to StatusWarningYellow
    }

    val sisaTagihan = (tx.totalTagihan - tx.nominalPembayaran).coerceAtLeast(0L)
    val thumbPhoto = tx.fotoUnitSebelumKirimUri.ifBlank { matchedUnit?.fotoUnitUri ?: "" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickDetail() }
            .testTag("transaction_card_${tx.id}"),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = tx.nomorInvoice,
                        style = MaterialTheme.typography.labelLarge,
                        color = SeaBluePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = payBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = tx.statusPembayaran.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = payFg,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(color = rentBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = tx.statusPenyewaan.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = rentFg,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClickEdit,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_edit_tx_card_${tx.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Transaksi",
                            tint = SeaBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onClickDelete,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_delete_tx_card_${tx.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Transaksi",
                            tint = StatusOverdueRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Unit Thumbnail + Transaction Info Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FreezerUnitThumbnail(
                    photoUriOrPath = thumbPhoto,
                    jenisUnit = matchedUnit?.jenisUnit ?: tx.namaUnitSnapshot,
                    kapasitasLiter = matchedUnit?.kapasitasLiter ?: 200,
                    size = 66.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.namaPelangganSnapshot,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Unit: ${tx.namaUnitSnapshot} (${tx.kodeUnitSnapshot})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val isHakMilik = tx.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
                    val totalProg = tx.totalPeriodeHakMilik.coerceAtLeast(1)
                    val currentSeq = tx.pembayaranKeHakMilik.coerceIn(1, totalProg)
                    val sisaKali = (totalProg - currentSeq).coerceAtLeast(0)
                    Surface(
                        color = if (isHakMilik) Color(0xFFEDE9FE) else Color(0xFFE0F2FE),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isHakMilik) {
                                "PROGRAM: SEWA HAK MILIK ${tx.totalPeriodeHakMilik}x • Pembayaran Ke-$currentSeq/$totalProg • Sisa ${sisaKali}x • Status: ${tx.statusProgramHakMilik}"
                            } else {
                                "PROGRAM: SEWA REGULER"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isHakMilik) Color(0xFF5B21B6) else SeaBluePrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isHakMilik) {
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { (currentSeq.toFloat() / totalProg.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (currentSeq >= totalProg && tx.statusPembayaran == "Lunas") StatusPaidGreen else Color(0xFF7C3AED),
                            trackColor = Color(0xFFDDD6FE)
                        )
                    }
                    Text(
                        text = "Durasi: ${tx.durasiHari} Hari • Mulai: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalMulaiSewa)} s/d ${DateCurrencyUtils.formatDisplayDate(tx.tanggalJatuhTempo)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDateAccent,
                        fontWeight = FontWeight.Medium
                    )
                    if (txUnitItems.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "MULTI-UNIT (${txUnitItems.size} UNIT FREEZER):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NavyDark
                                )
                                txUnitItems.forEachIndexed { idx, item ->
                                    Text(
                                        text = "${idx + 1}. ${item.namaUnitSnapshot} (${item.jenisProgram}) • ${DateCurrencyUtils.formatRupiah(item.hargaSewa)} • JT: ${DateCurrencyUtils.formatDisplayDate(item.tanggalJatuhTempo)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NavyDark
                                    )
                                }
                            }
                        }
                    }
                    if (paymentProofsCount > 0) {
                        Text(
                            text = "✓ Bukti Pembayaran Tersimpan ($paymentProofsCount Bukti • ${tx.metodePembayaran})",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusPaidGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Total: ${DateCurrencyUtils.formatRupiah(tx.totalTagihan)} (Termasuk Ongkir ${DateCurrencyUtils.formatRupiah(tx.ongkir)})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dibayar: ${DateCurrencyUtils.formatRupiah(tx.nominalPembayaran)} | Sisa: ${DateCurrencyUtils.formatRupiah(sisaTagihan)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sisaTagihan > 0) StatusOverdueRed else StatusPaidGreen
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onClickExtend,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = "Perpanjang", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Perpanjang", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = onClickDetail,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_open_invoice_${tx.id}")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Invoice PDF", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Invoice", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionInvoiceAndHandoverDetailDialog(
    tx: RentalTransactionEntity,
    matchedCustomer: CustomerEntity? = null,
    customerHistoryTransactions: List<RentalTransactionEntity> = emptyList(),
    matchedUnit: FreezerUnitEntity?,
    txUnitItems: List<TransactionUnitItemEntity> = emptyList(),
    txUnitAllocations: List<UnitPaymentAllocationEntity> = emptyList(),
    officerAuditLogs: List<TransactionOfficerAuditLogEntity> = emptyList(),
    paymentMethods: List<PaymentMethodEntity> = emptyList(),
    transactionPaymentProofs: List<PaymentProofEntity> = emptyList(),
    customerPhone: String,
    onDismiss: () -> Unit,
    onEditHandoverOrTx: () -> Unit,
    onExtendRental: () -> Unit,
    onSavePaymentProof: (PaymentProofEntity) -> Unit = {},
    onDeletePaymentProof: (PaymentProofEntity) -> Unit = {},
    onAllocatePartialPayment: (Long, Long, Map<Long, Long>, String, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onRecordUnitHakMilikPayment: (Long, Long, Long, String, String, String, String, String, Boolean) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onCorrectOfficerWithAuditLog: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    onDeleteTransaction: () -> Unit = {},
    onGeneratePdf: ((File) -> Unit) -> Unit,
    onOpenPdfViewer: (File) -> Unit,
    onPostMessage: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val thumbPhoto = tx.fotoUnitSebelumKirimUri.ifBlank { matchedUnit?.fotoUnitUri ?: "" }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddPaymentProofModal by remember { mutableStateOf(false) }
    var showPartialAllocationModal by remember { mutableStateOf(false) }
    var unitItemForHakMilikPayment by remember { mutableStateOf<TransactionUnitItemEntity?>(null) }
    var showOfficerCorrectionDialog by remember { mutableStateOf(false) }
    var editingProofEntity by remember { mutableStateOf<PaymentProofEntity?>(null) }
    var previewProofImageUri by remember { mutableStateOf<String?>(null) }

    val permanentCustId = matchedCustomer?.kodePelanggan?.ifBlank {
        "CUST/SAF/${matchedCustomer.id.toString().padStart(4, '0')}"
    } ?: "CUST/SAF/${tx.customerId.toString().padStart(4, '0')}"

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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        FreezerUnitThumbnail(
                            photoUriOrPath = thumbPhoto,
                            jenisUnit = matchedUnit?.jenisUnit ?: tx.namaUnitSnapshot,
                            kapasitasLiter = matchedUnit?.kapasitasLiter ?: 200,
                            size = 56.dp
                        )
                        Column {
                            Text(
                                text = "${tx.nomorInvoice} • $permanentCustId",
                                style = MaterialTheme.typography.labelLarge,
                                color = SeaBluePrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = tx.namaPelangganSnapshot,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${tx.namaUnitSnapshot} (${tx.kodeUnitSnapshot})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = onEditHandoverOrTx) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Transaksi & Serah Terima")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus Transaksi", tint = StatusOverdueRed)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                // 6 MANDATORY INVOICE PDF ACTION BUTTONS (Section 7)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyDark)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MANAJEMEN INVOICE PDF OTOMATIS (UKURAN A4)",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No. Invoice Unik: ${tx.nomorInvoice} • Cap Status Pembayaran & TTD Permanen Owner Otomatis di Setiap Halaman",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF38BDF8)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    onGeneratePdf { file ->
                                        onPostMessage("Invoice PDF berhasil dibuat: ${file.name}")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_generate_invoice_pdf")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("GENERATE INVOICE PDF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onGeneratePdf { file ->
                                        onOpenPdfViewer(file)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_view_invoice_pdf")
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LIHAT INVOICE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    onGeneratePdf { file ->
                                        onPostMessage("File PDF tersimpan di penyimpanan internal: ${file.absolutePath}")
                                        val authority = "${context.packageName}.fileprovider"
                                        val uri = FileProvider.getUriForFile(context, authority, file)
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            context.startActivity(viewIntent)
                                        } catch (_: Exception) {
                                            onOpenPdfViewer(file)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_download_pdf")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DOWNLOAD PDF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    onGeneratePdf { file ->
                                        InvoicePdfGenerator.shareInvoiceViaWhatsApp(
                                            context = context,
                                            pdfFile = file,
                                            transaction = tx,
                                            customerPhone = customerPhone,
                                            isResend = false
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_share_whatsapp")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("BAGIKAN VIA WHATSAPP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onGeneratePdf { file ->
                                        InvoicePdfGenerator.printInvoicePdf(
                                            context = context,
                                            pdfFile = file,
                                            jobName = "Invoice_${tx.nomorInvoice}"
                                        )
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_print_invoice")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CETAK INVOICE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    onGeneratePdf { file ->
                                        InvoicePdfGenerator.shareInvoiceViaWhatsApp(
                                            context = context,
                                            pdfFile = file,
                                            transaction = tx,
                                            customerPhone = customerPhone,
                                            isResend = true
                                        )
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_resend_invoice")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("KIRIM ULANG INVOICE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Transaction Summary Info
                val isHakMilikDetail = tx.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
                val totalProgDetail = tx.totalPeriodeHakMilik.coerceAtLeast(1)
                val currentSeqDetail = tx.pembayaranKeHakMilik.coerceIn(1, totalProgDetail)
                val sisaPeriodeDetail = (totalProgDetail - currentSeqDetail).coerceAtLeast(0)
                val isHakMilikComplete = isHakMilikDetail && currentSeqDetail >= totalProgDetail &&
                    (tx.statusPembayaran == "Lunas" || tx.statusProgramHakMilik.equals("Selesai", ignoreCase = true))

                if (isHakMilikComplete) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StatusPaidGreenBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "NOTIFIKASI PROGRAM SEWA HAK MILIK TERPENUHI!",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = StatusPaidGreen
                            )
                            Text(
                                text = "Seluruh kewajiban pembayaran (${tx.pembayaranKeHakMilik}/${tx.totalPeriodeHakMilik} Kali) telah terpenuhi. Unit freezer '${tx.namaUnitSnapshot} (${tx.kodeUnitSnapshot})' telah memenuhi ketentuan Program Sewa Hak Milik untuk pelanggan ${tx.namaPelangganSnapshot}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NavyDark
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Rincian Program, Biaya Sewa, Ongkir & Pembayaran", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("ID Pelanggan Tetap: $permanentCustId (${tx.namaPelangganSnapshot})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = SeaBluePrimary)
                        Text(
                            text = if (isHakMilikDetail) {
                                "Jenis Program: SEWA HAK MILIK (${tx.totalPeriodeHakMilik} Kali)"
                            } else {
                                "Jenis Program: SEWA REGULER"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isHakMilikDetail) Color(0xFF5B21B6) else SeaBluePrimary
                        )
                        if (isHakMilikDetail) {
                            Text(
                                text = "Progres Program: Pembayaran Ke-$currentSeqDetail dari $totalProgDetail Kali (${((currentSeqDetail * 100f) / totalProgDetail).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sisa Pembayaran Program: $sisaPeriodeDetail Kali periode lagi • Status Program: ${tx.statusProgramHakMilik}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (sisaPeriodeDetail == 0) StatusPaidGreen else Color(0xFF6D28D9)
                            )
                            LinearProgressIndicator(
                                progress = { (currentSeqDetail.toFloat() / totalProgDetail.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (isHakMilikComplete) StatusPaidGreen else Color(0xFF7C3AED),
                                trackColor = Color(0xFFDDD6FE)
                            )
                        }
                        Text("Unit Freezer: ${tx.namaUnitSnapshot} (${tx.kodeUnitSnapshot})", style = MaterialTheme.typography.bodySmall)
                        Text("Tanggal Pemesanan: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalPemesanan)}", style = MaterialTheme.typography.bodySmall)
                        Text("Durasi Sewa: ${tx.durasiHari} Hari", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Periode: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalMulaiSewa)} s/d ${DateCurrencyUtils.formatDisplayDate(tx.tanggalAkhirSewa)}", style = MaterialTheme.typography.bodySmall)
                        Text("Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalJatuhTempo)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Biaya Sewa (${tx.durasiHari} Hari): ${DateCurrencyUtils.formatRupiah(tx.hargaSewa)}", style = MaterialTheme.typography.bodySmall)
                        Text("Biaya Ongkos Kirim (Ongkir): ${DateCurrencyUtils.formatRupiah(tx.ongkir)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Deposit: ${DateCurrencyUtils.formatRupiah(tx.deposit)} | Diskon: ${DateCurrencyUtils.formatRupiah(tx.diskon)}", style = MaterialTheme.typography.bodySmall)
                        Text("Total Tagihan: ${DateCurrencyUtils.formatRupiah(tx.totalTagihan)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SeaBluePrimary)
                        Text("Nominal Pembayaran: ${DateCurrencyUtils.formatRupiah(tx.nominalPembayaran)} (${tx.metodePembayaran})", style = MaterialTheme.typography.bodySmall)
                        val sisa = (tx.totalTagihan - tx.nominalPembayaran).coerceAtLeast(0L)
                        Text("Sisa Pembayaran: ${DateCurrencyUtils.formatRupiah(sisa)} • Status: ${tx.statusPembayaran}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Serah Terima: Penerima '${tx.namaPenerima.ifBlank { tx.namaPelangganSnapshot }}' pada ${tx.tanggalPenerimaan} ${tx.jamPenerimaan}", style = MaterialTheme.typography.bodySmall)
                        Text("Petugas SAF: ${tx.namaPetugas.ifBlank { "Tim Operasional SAF" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Kondisi Freezer: ${tx.keteranganKondisiFreezer.ifBlank { "Baik & Lengkap" }}", style = MaterialTheme.typography.bodySmall)
                        if (tx.alamatLokasiGps.isNotBlank() || tx.namaLokasiUsaha.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("Nama Lokasi Usaha: ${tx.namaLokasiUsaha.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("Alamat Penggunaan Freezer: ${tx.alamatLokasiGps}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("RT/RW: ${tx.rtRwLokasi.ifBlank { "-" }} • Kel/Desa: ${tx.kelurahanDesaLokasi.ifBlank { "-" }} • Kec: ${tx.kecamatanLokasi.ifBlank { "-" }} • Kota/Kab: ${tx.kabupatenKotaLokasi.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                            if (tx.linkGoogleMaps.isNotBlank()) {
                                Text("Link Maps: ${tx.linkGoogleMaps}", style = MaterialTheme.typography.labelSmall, color = SeaBluePrimary)
                            }
                        }
                    }
                }

                if (txUnitItems.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_multi_unit_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "RINCIAN MULTI-UNIT & SMART BILLING (${txUnitItems.size} UNIT)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { showPartialAllocationModal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("btn_open_partial_alloc_dialog")
                                ) {
                                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Alokasi Bayar Unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            txUnitItems.forEachIndexed { idx, item ->
                                val smartStatus = item.computeSmartBillingStatus()
                                val itemAllocs = txUnitAllocations.filter { it.unitItemId == item.id }
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Unit #${idx + 1}: ${item.namaUnitSnapshot} (${item.kodeUnitSnapshot}) • ${item.kapasitasLiterSnapshot}L",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = NavyDark,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = when (smartStatus) {
                                                    "Sudah dibayar" -> StatusPaidGreenBg
                                                    "Terlambat" -> StatusOverdueRedBg
                                                    "Jatuh tempo hari ini", "Pembayaran sebagian" -> StatusWarningYellowBg
                                                    else -> StatusActiveBlueBg
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = smartStatus,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (smartStatus) {
                                                        "Sudah dibayar" -> StatusPaidGreen
                                                        "Terlambat" -> StatusOverdueRed
                                                        "Jatuh tempo hari ini", "Pembayaran sebagian" -> StatusWarningYellow
                                                        else -> StatusActiveBlue
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        val isItemHakMilik = item.jenisProgram.contains("Hak Milik", true)
                                        val progStr = if (isItemHakMilik) {
                                            "Sewa Hak Milik • ${item.hakMilikProgressLabel}"
                                        } else "Sewa Reguler (${item.durasiHari} Hari)"
                                        Text(
                                            text = "Program: $progStr • Harga: ${DateCurrencyUtils.formatRupiah(item.hargaSewa)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isItemHakMilik) Color(0xFF5B21B6) else SeaBluePrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (isItemHakMilik) {
                                            LinearProgressIndicator(
                                                progress = { item.hakMilikProgressFraction },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                                    .height(7.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = if (item.hakMilikRemainingCount == 0) StatusPaidGreen else Color(0xFF7C3AED),
                                                trackColor = Color(0xFFDDD6FE)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Sisa ${item.hakMilikRemainingCount}x cicilan • Status: ${item.statusProgramHakMilik}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (item.hakMilikRemainingCount == 0) StatusPaidGreen else Color(0xFF6D28D9),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                FilledTonalButton(
                                                    onClick = { unitItemForHakMilikPayment = item },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.testTag("btn_pay_unit_hm_${item.id}")
                                                ) {
                                                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("+ Catat Cicilan Unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Mulai: ${DateCurrencyUtils.formatDisplayDate(item.tanggalMulaiSewa)} • Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(item.tanggalJatuhTempo)}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            text = "Status Bayar: ${item.statusPembayaran} (Dibayar ${DateCurrencyUtils.formatRupiah(item.nominalPembayaran)} • Sisa ${DateCurrencyUtils.formatRupiah(item.sisaTagihanUnit)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (item.sisaTagihanUnit > 0L) StatusOverdueRed else StatusPaidGreen,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (itemAllocs.isNotEmpty()) {
                                            Text(
                                                text = "Riwayat Alokasi: ${itemAllocs.joinToString { "${it.paymentDate} (${DateCurrencyUtils.formatRupiah(it.allocatedAmount)})" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StatusPaidGreen
                                            )
                                        }
                                        Text(
                                            text = "Dokumentasi Unit (2 Slot): Tampak Depan ${if (item.fotoKondisiDepanUri.isNotBlank()) "✓" else "—"} • Serah Terima ${if (item.fotoSerahTerimaPelangganUri.isNotBlank()) "✓" else "—"} • ${item.statusKelengkapanFoto}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (item.isDocumentationComplete) StatusPaidGreen else StatusWarningYellow,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Permanent Officer Identity & Audited Correction Card (Section G)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_permanent_officer_snapshot"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "IDENTITAS & TANDA TANGAN PETUGAS (PERMANEN)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            }
                            if (tx.officerSnapshotLocked) {
                                Surface(color = StatusPaidGreenBg, shape = RoundedCornerShape(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = StatusPaidGreen, modifier = Modifier.size(11.dp))
                                        Text("Terkunci", style = MaterialTheme.typography.labelSmall, color = StatusPaidGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Petugas: ${tx.namaPetugas.ifBlank { "Tim Operasional SAF" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        if (tx.officerSignedAt.isNotBlank()) {
                            Text(
                                text = "Waktu Pengesahan Snapshot: ${tx.officerSignedAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (tx.tandaTanganPetugasUri.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = File(tx.tandaTanganPetugasUri).takeIf { it.exists() } ?: tx.tandaTanganPetugasUri,
                                    contentDescription = "Tanda Tangan Permanen Petugas",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Text(
                            text = "Nama & tanda tangan petugas terkunci sebagai snapshot permanen dan tidak berubah saat transaksi dibuka/diedit oleh petugas lain.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showOfficerCorrectionDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_detail_correct_officer")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Koreksi Identitas Petugas (Wajib Audit Log)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        if (officerAuditLogs.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                text = "Riwayat Audit Koreksi Petugas (${officerAuditLogs.size}):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            officerAuditLogs.take(4).forEach { log ->
                                Text(
                                    text = "• ${log.correctedAtDisplay}: '${log.previousOfficerName}' ➔ '${log.newOfficerName}' oleh ${log.correctedBy.ifBlank { "Admin" }} — Alasan: ${log.correctionReason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ==================== SECTION 1: FITUR BUKTI PEMBAYARAN & RIWAYAT PEMBAYARAN BERTAHAP ====================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_proof_detail_section"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = StatusPaidGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "BUKTI PEMBAYARAN & RIWAYAT BERTAHAP (${transactionPaymentProofs.size} Bukti)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                    Text(
                                        text = "Setiap bukti pembayaran terhubung otomatis ke Invoice PDF (halaman akhir) & histori pembayaran tetap terjaga.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF065F46)
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    editingProofEntity = null
                                    showAddPaymentProofModal = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusPaidGreen),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_add_payment_proof_detail")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah Bukti", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Status Transaksi:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StorageSyncStatusBadge(
                                status = StorageSyncStatus.fromEntitySyncStatus(tx.syncStatus)
                            )
                        }

                        if (transactionPaymentProofs.isEmpty()) {
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("empty_payment_proof_notice")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = InvoicePdfGenerator.EMPTY_PAYMENT_PROOF_NOTICE,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(
                                        text = "Tekan [Tambah Bukti] untuk melampirkan foto struk transfer, screenshot mobile banking, bukti QRIS, atau dokumen pembayaran lainnya agar otomatis tampil di halaman akhir Invoice PDF.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF78350F)
                                    )
                                }
                            }
                        } else {
                            val sortedProofs = remember(transactionPaymentProofs) {
                                InvoicePdfGenerator.sortPaymentProofsChronologically(transactionPaymentProofs)
                            }
                            sortedProofs.forEachIndexed { idx, proof ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        if (proof.fotoBuktiUri.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.Black.copy(alpha = 0.08f))
                                                    .clickable { previewProofImageUri = proof.fotoBuktiUri },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (proof.fotoBuktiUri.endsWith(".pdf", ignoreCase = true)) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(
                                                            Icons.Default.PictureAsPdf,
                                                            contentDescription = "Dokumen PDF Bukti Pembayaran",
                                                            tint = StatusOverdueRed,
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                        Text("PDF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    AsyncImage(
                                                        model = File(proof.fotoBuktiUri).takeIf { it.exists() } ?: proof.fotoBuktiUri,
                                                        contentDescription = "Foto Bukti Pembayaran",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "Tahap #${idx + 1} • ${DateCurrencyUtils.formatRupiah(proof.nominalPembayaran)}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = StatusPaidGreen
                                            )
                                            Text(
                                                text = "Metode: ${proof.metodePembayaran} • Pengirim: ${proof.namaPengirim.ifBlank { tx.namaPelangganSnapshot }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Tgl Bayar: ${proof.tanggalPembayaran} • Upload: ${proof.waktuUpload}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (proof.catatanPembayaran.isNotBlank()) {
                                                Text(
                                                    text = "Keterangan: ${proof.catatanPembayaran}",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            StorageSyncStatusBadge(
                                                status = StorageSyncStatus.fromEntitySyncStatus(proof.syncStatus)
                                            )
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            IconButton(
                                                onClick = { previewProofImageUri = proof.fotoBuktiUri },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("btn_view_payment_proof_${proof.id}")
                                            ) {
                                                Icon(Icons.Default.Visibility, contentDescription = "Lihat Bukti Pembayaran", tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    editingProofEntity = proof
                                                    showAddPaymentProofModal = true
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("btn_replace_payment_proof_${proof.id}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Ganti Bukti Pembayaran", tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { onDeletePaymentProof(proof) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("btn_delete_payment_proof_${proof.id}")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Hapus Bukti Pembayaran", tint = StatusOverdueRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Histori Transaksi Berdasarkan Pelanggan Ini
                if (customerHistoryTransactions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SeaBluePrimary.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Histori Transaksi Pelanggan $permanentCustId (${customerHistoryTransactions.size} Transaksi)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = SeaBluePrimary
                                )
                            }
                            customerHistoryTransactions.take(5).forEach { hTx ->
                                Text(
                                    text = "• ${hTx.nomorInvoice} — ${hTx.namaUnitSnapshot} (${hTx.durasiHari} Hari) • ${DateCurrencyUtils.formatRupiah(hTx.totalTagihan)} [${hTx.statusPenyewaan}]",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // GPS Map Viewer with Accuracy Indicator & Mode A/B
                GpsLocationMapCard(
                    latitude = tx.latitude,
                    longitude = tx.longitude,
                    addressText = tx.alamatLokasiGps,
                    dateStr = tx.tanggalLokasiGps,
                    timeStr = tx.jamLokasiGps,
                    accuracyMeters = tx.akurasiGpsMeter
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onExtendRental,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Perpanjang (Hari)")
                    }
                    Button(
                        onClick = onEditHandoverOrTx,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit / Lokasi & Foto")
                    }
                }
            }
        }
    }

    if (showPartialAllocationModal) {
        SmartPartialPaymentAllocationDialog(
            transaction = tx,
            unitItems = txUnitItems,
            paymentMethods = paymentMethods,
            onDismiss = { showPartialAllocationModal = false },
            onConfirmAllocate = { totalPaid, allocations, paymentDate, method, sender, notes ->
                onAllocatePartialPayment(
                    tx.id,
                    totalPaid,
                    allocations,
                    paymentDate,
                    method,
                    sender,
                    notes
                )
                showPartialAllocationModal = false
                onPostMessage("Alokasi pembayaran per unit untuk ${tx.nomorInvoice} tersimpan!")
            }
        )
    }

    if (showAddPaymentProofModal) {
        PaymentProofUploadDialog(
            transactionId = tx.id,
            invoiceNumber = tx.nomorInvoice,
            defaultAmount = (tx.totalTagihan - tx.nominalPembayaran).takeIf { it > 0L } ?: tx.totalTagihan,
            defaultMethod = tx.metodePembayaran,
            defaultSender = tx.namaPelangganSnapshot,
            paymentMethods = paymentMethods,
            initialProof = editingProofEntity,
            onDismiss = {
                showAddPaymentProofModal = false
                editingProofEntity = null
            },
            onSave = { proof ->
                onSavePaymentProof(proof)
                showAddPaymentProofModal = false
                editingProofEntity = null
                onPostMessage("Bukti pembayaran ${tx.nomorInvoice} berhasil disimpan!")
            }
        )
    }

    if (previewProofImageUri != null) {
        Dialog(
            onDismissRequest = { previewProofImageUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = NavyDark
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Preview Bukti Pembayaran (${tx.nomorInvoice})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { previewProofImageUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = File(previewProofImageUri!!).takeIf { it.exists() } ?: previewProofImageUri!!,
                            contentDescription = "Bukti Pembayaran",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (unitItemForHakMilikPayment != null) {
        val targetItem = unitItemForHakMilikPayment!!
        UnitHakMilikInstallmentDialog(
            transaction = tx,
            unitItem = targetItem,
            paymentMethods = paymentMethods,
            onDismiss = { unitItemForHakMilikPayment = null },
            onConfirm = { nominal, method, payDate, sender, notes, incrementStep ->
                onRecordUnitHakMilikPayment(
                    tx.id,
                    targetItem.id,
                    nominal,
                    method,
                    payDate,
                    sender,
                    notes,
                    "",
                    incrementStep
                )
                unitItemForHakMilikPayment = null
            }
        )
    }

    if (showOfficerCorrectionDialog) {
        OfficerCorrectionAuditDialog(
            transactionId = tx.id,
            invoiceNumber = tx.nomorInvoice,
            currentOfficerName = tx.namaPetugas,
            currentSignatureUri = tx.tandaTanganPetugasUri,
            onDismiss = { showOfficerCorrectionDialog = false },
            onConfirm = { newName, newSigUri, reason ->
                onCorrectOfficerWithAuditLog(tx.id, newName, newSigUri, reason)
                showOfficerCorrectionDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        DeleteTransactionConfirmDialog(
            tx = tx,
            unitCount = txUnitItems.size.coerceAtLeast(1),
            customerCode = permanentCustId,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDeleteTransaction()
            }
        )
    }
}

@Composable
private fun DeleteTransactionConfirmDialog(
    tx: RentalTransactionEntity,
    unitCount: Int,
    customerCode: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Konfirmasi Hapus Transaksi",
                fontWeight = FontWeight.Bold,
                color = NavyDark
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_tx_confirm_summary")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Nomor Transaksi: ${tx.nomorInvoice}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Nama Pelanggan: ${tx.namaPelangganSnapshot} ($customerCode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF263445)
                        )
                        Text(
                            text = "Jumlah Unit: ${unitCount.coerceAtLeast(1)} Unit Freezer",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF263445)
                        )
                        Text(
                            text = "Total Transaksi: ${DateCurrencyUtils.formatRupiah(tx.totalTagihan)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusOverdueRed
                        )
                    }
                }
                Text(
                    text = "Transaksi akan dihapus menggunakan Soft Delete / Tombstone dan otomatis disinkronkan ke Google Drive (atau masuk antrean jika offline). Data pelanggan tetap aman, dan status unit yang tidak dipakai transaksi aktif lain akan dikembalikan ke Tersedia (status Hilang/Perbaikan tidak diubah).",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF475569)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusOverdueRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("btn_confirm_delete_tx")
            ) {
                Text("Hapus Transaksi", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_delete_tx")
            ) {
                Text("Batal", color = NavyDark)
            }
        }
    )
}

@Composable
private fun UnitHakMilikInstallmentDialog(
    transaction: RentalTransactionEntity,
    unitItem: TransactionUnitItemEntity,
    paymentMethods: List<PaymentMethodEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String, String, String, Boolean) -> Unit
) {
    val activeMethods = remember(paymentMethods) {
        paymentMethods.filter { it.isAktif }.map { it.namaMetode }.ifEmpty { METODE_PEMBAYARAN_OPTIONS }
    }
    var nominalText by remember(unitItem.id) { mutableStateOf(unitItem.hargaSewa.coerceAtLeast(100000L).toString()) }
    var metode by remember { mutableStateOf(activeMethods.firstOrNull() ?: "Transfer BCA") }
    var payDate by remember { mutableStateOf(DateCurrencyUtils.todayIso()) }
    var sender by remember { mutableStateOf(transaction.namaPelangganSnapshot) }
    var notes by remember(unitItem.id) {
        val nextStep = (unitItem.pembayaranKeHakMilik + 1).coerceAtMost(unitItem.totalPeriodeHakMilik.coerceAtLeast(1))
        mutableStateOf("Cicilan Sewa Hak Milik Ke-$nextStep/${unitItem.totalPeriodeHakMilik} (${unitItem.namaUnitSnapshot})")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Catat Cicilan Unit: ${unitItem.namaUnitSnapshot}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Surface(color = Color(0xFFF3E8FF), shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Progres Saat Ini: ${unitItem.hakMilikProgressLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5B21B6)
                        )
                        Text(
                            text = "Pembayaran ini hanya memperbarui unit '${unitItem.namaUnitSnapshot}' (${unitItem.kodeUnitSnapshot}) tanpa memengaruhi unit lain dalam transaksi ini.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B21A8)
                        )
                    }
                }
                OutlinedTextField(
                    value = nominalText,
                    onValueChange = { nominalText = it.filter { c -> c.isDigit() } },
                    label = { Text("Nominal Cicilan Unit (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_unit_hm_nominal")
                )
                OutlinedTextField(
                    value = payDate,
                    onValueChange = { payDate = it },
                    label = { Text("Tanggal Pembayaran (YYYY-MM-DD)") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Metode Pembayaran:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    activeMethods.forEach { m ->
                        FilterChip(
                            selected = metode == m,
                            onClick = { metode = m },
                            label = { Text(m, style = MaterialTheme.typography.labelSmall) },
                            colors = appFilterChipColors()
                        )
                    }
                }
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Nama Pengirim") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Cicilan") },
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = nominalText.toLongOrNull()?.coerceAtLeast(1L) ?: unitItem.hargaSewa.coerceAtLeast(1L)
                    onConfirm(amount, metode, payDate, sender, notes, true)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                modifier = Modifier.testTag("btn_confirm_unit_hm_payment")
            ) {
                Text("Simpan Cicilan Unit", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun OfficerCorrectionAuditDialog(
    transactionId: Long,
    invoiceNumber: String,
    currentOfficerName: String,
    currentSignatureUri: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var newOfficerName by remember { mutableStateOf(currentOfficerName) }
    var newSignatureUri by remember { mutableStateOf(currentSignatureUri) }
    var reason by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Koreksi Identitas Petugas (Audit Log)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Transaksi $invoiceNumber memiliki snapshot petugas permanen. Setiap koreksi wajib menyertakan alasan jelas dan dicatat permanen ke Audit Log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SmartCaseOutlinedTextField(
                    value = newOfficerName,
                    onValueChange = {
                        newOfficerName = it
                        validationError = null
                    },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Nama Petugas Baru") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_officer_correction_name")
                )
                DigitalSignaturePad(
                    title = "Tanda Tangan Petugas Baru (Opsional jika ganti)",
                    signerName = newOfficerName,
                    transactionFolder = "tx_${transactionId}_correction",
                    filePrefix = "petugas_koreksi",
                    existingSignaturePath = newSignatureUri,
                    onSignatureSaved = { newSignatureUri = it }
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        validationError = null
                    },
                    label = { Text("Alasan Koreksi (Wajib Diisi)") },
                    placeholder = { Text("Contoh: Kesalahan input nama petugas saat serah terima") },
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_officer_correction_reason")
                )
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusOverdueRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newOfficerName.isBlank()) {
                        validationError = "Nama petugas tidak boleh kosong."
                        return@Button
                    }
                    if (reason.isBlank()) {
                        validationError = "Alasan koreksi wajib diisi untuk Audit Log."
                        return@Button
                    }
                    onConfirm(newOfficerName.trim(), newSignatureUri, reason.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                modifier = Modifier.testTag("btn_confirm_officer_correction")
            ) {
                Text("Simpan Koreksi & Catat Audit", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * Dialog to Upload / Edit a Payment Proof (Gallery JPG/JPEG/PNG or CameraX capture with preview before saving).
 */
@Composable
fun PaymentProofUploadDialog(
    transactionId: Long,
    invoiceNumber: String,
    defaultAmount: Long,
    defaultMethod: String,
    defaultSender: String,
    paymentMethods: List<PaymentMethodEntity> = emptyList(),
    initialProof: PaymentProofEntity? = null,
    onDismiss: () -> Unit,
    onSave: (PaymentProofEntity) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var tanggalPembayaran by remember(initialProof) { mutableStateOf(initialProof?.tanggalPembayaran ?: DateCurrencyUtils.todayIso()) }
    var nominalText by remember(initialProof) { mutableStateOf((initialProof?.nominalPembayaran ?: defaultAmount).toString()) }
    var metodePembayaran by remember(initialProof) { mutableStateOf(initialProof?.metodePembayaran ?: defaultMethod) }
    var namaPengirim by remember(initialProof) { mutableStateOf(initialProof?.namaPengirim ?: defaultSender) }
    var catatanPembayaran by remember(initialProof) { mutableStateOf(initialProof?.catatanPembayaran ?: "") }
    var fotoBuktiUri by remember(initialProof) { mutableStateOf(initialProof?.fotoBuktiUri ?: "") }
    var waktuUpload by remember(initialProof) { mutableStateOf(initialProof?.waktuUpload ?: DateCurrencyUtils.nowDateTimeDisplay()) }
    var showCameraCapture by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<FormValidationResult?>(null) }

    val activeMethodLabels = remember(paymentMethods) {
        val active = paymentMethods.filter { it.isAktif }.map { it.namaMetode }
        if (active.isEmpty()) METODE_PEMBAYARAN_OPTIONS else active
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showCameraCapture = true
    }

    fun copySelectedProofUri(uri: android.net.Uri) {
        try {
            val dir = File(context.filesDir, "photos/payment_proofs")
            if (!FormValidationEngine.isDeviceStorageWritable(dir)) {
                Toast.makeText(context, "DATA GAGAL DISIMPAN! Penyimpanan perangkat tidak tersedia.", Toast.LENGTH_LONG).show()
                return
            }
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: "image/jpeg"
            val ext = when {
                mime.contains("pdf") -> "pdf"
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("jpeg") -> "jpeg"
                else -> "jpg"
            }
            val destFile = File(dir, "BUKTI_${transactionId}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            if (!destFile.exists() || destFile.length() <= 0L) {
                Toast.makeText(context, "DATA GAGAL DISIMPAN! File bukti pembayaran gagal dibaca.", Toast.LENGTH_LONG).show()
                return
            }
            fotoBuktiUri = destFile.absolutePath
            waktuUpload = DateCurrencyUtils.nowDateTimeDisplay()
            validationResult = null
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "DATA GAGAL DISIMPAN! ${FormValidationEngine.classifySaveFailureReason(e)}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) copySelectedProofUri(uri)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) copySelectedProofUri(uri)
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
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (initialProof == null) "Tambah Bukti Pembayaran" else "Ganti Bukti Pembayaran",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (initialProof == null) {
                                "Invoice: $invoiceNumber • Struk transfer, M-Banking, QRIS, atau PDF"
                            } else {
                                "Invoice: $invoiceNumber • Bukti lama diarsipkan otomatis, histori pembayaran tetap tersimpan"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                FormValidationAlertBanner(
                    validationResult = validationResult,
                    testTagName = "payment_proof_validation_banner"
                )

                // Image / Document Preview Before Saving + Replace/Delete Buttons
                if (fotoBuktiUri.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Preview Bukti Pembayaran (Akan Terlampir di Invoice PDF):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusPaidGreen
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (fotoBuktiUri.endsWith(".pdf", ignoreCase = true)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            Icons.Default.PictureAsPdf,
                                            contentDescription = "Preview Dokumen PDF Bukti Pembayaran",
                                            tint = Color.White,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = File(fotoBuktiUri).name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                } else {
                                    AsyncImage(
                                        model = File(fotoBuktiUri).takeIf { it.exists() } ?: fotoBuktiUri,
                                        contentDescription = "Preview Bukti Pembayaran",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Waktu Upload: $waktuUpload",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { fotoBuktiUri = "" },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = StatusOverdueRed, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hapus Lampiran", color = StatusOverdueRed, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) showCameraCapture = true else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_camera_payment_proof")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (fotoBuktiUri.isBlank()) "Foto Struk" else "Ganti Kamera", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = {
                            galleryPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_gallery_payment_proof")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (fotoBuktiUri.isBlank()) "Galeri / QRIS" else "Ganti Galeri", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedButton(
                    onClick = {
                        documentPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_doc_payment_proof")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = SeaBluePrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (fotoBuktiUri.isBlank()) "Pilih Dokumen Pembayaran Lainnya (Gambar / PDF)" else "Ganti dengan Dokumen / PDF Lain",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmartCaseOutlinedTextField(
                        value = tanggalPembayaran,
                        onValueChange = {
                            tanggalPembayaran = it
                            if (validationResult != null) validationResult = null
                        },
                        caseMode = InputTextCaseMode.PRESERVE,
                        label = { Text("Tanggal Pembayaran *") },
                        isError = validationResult?.fieldErrors?.containsKey("tanggalPembayaran") == true,
                        supportingText = { FieldErrorSupportingText(validationResult?.fieldErrors?.get("tanggalPembayaran")) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_payment_proof_date")
                    )
                    SmartCaseOutlinedTextField(
                        value = nominalText,
                        onValueChange = {
                            nominalText = it.filter { ch -> ch.isDigit() }
                            if (validationResult != null) validationResult = null
                        },
                        caseMode = InputTextCaseMode.PRESERVE,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Nominal Pembayaran (Rp) *") },
                        isError = validationResult?.fieldErrors?.containsKey("nominalPembayaran") == true,
                        supportingText = { FieldErrorSupportingText(validationResult?.fieldErrors?.get("nominalPembayaran")) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_payment_proof_amount")
                    )
                }

                Text("Metode Pembayaran *:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    activeMethodLabels.forEach { m ->
                        FilterChip(
                            selected = metodePembayaran == m,
                            onClick = {
                                metodePembayaran = m
                                if (validationResult != null) validationResult = null
                            },
                            label = { Text(m) }
                        )
                    }
                }

                SmartCaseOutlinedTextField(
                    value = namaPengirim,
                    onValueChange = { namaPengirim = it },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Nama Pengirim / Penyetor") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_payment_proof_sender")
                )

                SmartCaseOutlinedTextField(
                    value = catatanPembayaran,
                    onValueChange = { catatanPembayaran = it },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Keterangan Pembayaran (Contoh: Pelunasan / Cicilan Tahap 1)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_payment_proof_notes")
                )

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
                            val amount = nominalText.toLongOrNull() ?: 0L
                            val result = FormValidationEngine.validatePaymentProofForm(
                                transactionId = transactionId,
                                tanggalPembayaran = tanggalPembayaran,
                                nominalPembayaran = amount,
                                metodePembayaran = metodePembayaran,
                                requireProofAttachment = false,
                                fotoBuktiUri = fotoBuktiUri
                            )
                            if (!result.isValid) {
                                validationResult = result
                                scope.launch { scrollState.animateScrollTo(0) }
                                return@Button
                            }
                            validationResult = null
                            val updatedEntity = if (initialProof != null) {
                                initialProof.copy(
                                    transactionId = transactionId,
                                    tanggalPembayaran = tanggalPembayaran.trim(),
                                    nominalPembayaran = amount,
                                    metodePembayaran = metodePembayaran.trim(),
                                    namaPengirim = namaPengirim.ifBlank { defaultSender },
                                    catatanPembayaran = catatanPembayaran,
                                    fotoBuktiUri = fotoBuktiUri,
                                    waktuUpload = waktuUpload.ifBlank { DateCurrencyUtils.nowDateTimeDisplay() }
                                )
                            } else {
                                PaymentProofEntity(
                                    transactionId = transactionId,
                                    tanggalPembayaran = tanggalPembayaran.trim(),
                                    nominalPembayaran = amount,
                                    metodePembayaran = metodePembayaran.trim(),
                                    namaPengirim = namaPengirim.ifBlank { defaultSender },
                                    catatanPembayaran = catatanPembayaran,
                                    fotoBuktiUri = fotoBuktiUri,
                                    waktuUpload = waktuUpload.ifBlank { DateCurrencyUtils.nowDateTimeDisplay() }
                                )
                            }
                            onSave(updatedEntity)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPaidGreen),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("btn_save_payment_proof")
                    ) {
                        Text(
                            if (initialProof == null) "SIMPAN BUKTI PEMBAYARAN" else "SIMPAN PENGGANTIAN BUKTI",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showCameraCapture) {
        CameraXCaptureDialog(
            title = "Foto Bukti Pembayaran ($invoiceNumber)",
            folderName = "payment_proofs",
            filePrefix = "BUKTI_${transactionId}",
            onDismiss = { showCameraCapture = false },
            onPhotoCaptured = { file ->
                fotoBuktiUri = file.absolutePath
                waktuUpload = DateCurrencyUtils.nowDateTimeDisplay()
                showCameraCapture = false
            }
        )
    }
}

/**
 * Reusable Payment Method Info Card for Transaksi Baru:
 * - Jika memilih QRIS: tampilkan gambar QRIS.
 * - Jika memilih Transfer Bank: tampilkan nama bank, nomor rekening, dan nama pemilik rekening.
 * - Jika memilih Cash / Tunai: tidak perlu menampilkan nomor rekening.
 */
@Composable
fun SelectedPaymentMethodDetailCard(
    selectedMethodName: String,
    paymentMethods: List<PaymentMethodEntity>
) {
    val context = LocalContext.current
    val matched = remember(selectedMethodName, paymentMethods) {
        paymentMethods.find { it.namaMetode.equals(selectedMethodName, ignoreCase = true) }
    }
    val category = matched?.kategoriMetode ?: when {
        selectedMethodName.contains("QRIS", ignoreCase = true) -> "QRIS"
        selectedMethodName.contains("Tunai", ignoreCase = true) || selectedMethodName.contains("Cash", ignoreCase = true) -> "TUNAI"
        else -> "TRANSFER_BANK"
    }

    when (category) {
        "QRIS" -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_method_qris_card")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = SeaBluePrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PEMBAYARAN QRIS — ${matched?.namaPemilikRekening?.ifBlank { "SEWAin Aja Freezer SOC" } ?: "SEWAin Aja Freezer SOC"}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                    }
                    if (!matched?.fotoQrisUri.isNullOrBlank() && File(matched!!.fotoQrisUri).exists()) {
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = File(matched.fotoQrisUri),
                                contentDescription = "Gambar QRIS",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(160.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.QrCode2,
                                    contentDescription = "QRIS",
                                    tint = NavyDark,
                                    modifier = Modifier.size(96.dp)
                                )
                                Text(
                                    text = "QRIS All Payment SAF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SeaBluePrimary
                                )
                            }
                        }
                    }
                    Text(
                        text = matched?.keteranganPembayaran?.ifBlank { "Scan kode QRIS di atas melalui aplikasi Mobile Banking / E-Wallet Anda." }
                            ?: "Scan kode QRIS di atas melalui aplikasi Mobile Banking / E-Wallet Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        "TUNAI" -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = StatusPaidGreenBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_method_tunai_card")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = StatusPaidGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Pembayaran Cash / Tunai",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                        Text(
                            text = matched?.keteranganPembayaran?.ifBlank { "Pembayaran tunai diserahkan langsung kepada petugas resmi SEWAin Aja Freezer SOC saat serah terima." }
                                ?: "Pembayaran tunai diserahkan langsung kepada petugas resmi SEWAin Aja Freezer SOC saat serah terima.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            }
        }

        else -> {
            // TRANSFER_BANK
            val bankName = matched?.namaBankAtauPenyedia?.ifBlank { selectedMethodName } ?: selectedMethodName
            val accNumber = matched?.nomorRekening?.ifBlank { "Hubungi Admin SAF" } ?: "Hubungi Admin SAF"
            val accHolder = matched?.namaPemilikRekening?.ifBlank { "SEWAin Aja Freezer SOC" } ?: "SEWAin Aja Freezer SOC"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_method_bank_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = SeaBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "INFORMASI REKENING TRANSFER BANK",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Nomor Rekening SAF", accNumber))
                                Toast.makeText(context, "Nomor rekening disalin: $accNumber", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salin Rekening", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = "Nama Bank: $bankName",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Nomor Rekening: $accNumber",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = SeaBluePrimary
                    )
                    Text(
                        text = "Atas Nama: $accHolder",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!matched?.keteranganPembayaran.isNullOrBlank()) {
                        Text(
                            text = matched!!.keteranganPembayaran,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified Form Dialog for Creating/Editing Rental Transaction (Section 4):
 * 1. DATA PELANGGAN (Pilih Pelanggan Terdaftar ATAU + Pelanggan Baru langsung tanpa meninggalkan halaman transaksi)
 * 2. DATA PENYEWAAN & METODE PEMBAYARAN + UPLOAD BUKTI PEMBAYARAN
 * 3. DATA UNIT FREEZER
 * 4. LOKASI PENGGUNAAN (Tagging Manual & Integrasi Leaflet.js / OpenStreetMap)
 * 5. SERAH TERIMA
 */
private data class StagedLocationDocPhoto(
    val id: Long,
    val slotCode: String,
    val source: Any,
    val previewModel: Any,
    val capturedAt: String,
    val latitude: Double?,
    val longitude: Double?
)

@Composable
private fun TransactionAndHandoverFormDialog(
    initialTransaction: RentalTransactionEntity?,
    initialUnitItems: List<TransactionUnitItemEntity> = emptyList(),
    preselectedCustomer: CustomerEntity? = null,
    customers: List<CustomerEntity>,
    allTransactions: List<RentalTransactionEntity> = emptyList(),
    units: List<FreezerUnitEntity>,
    unitRentalPricesMap: Map<Long, List<UnitRentalPriceEntity>> = emptyMap(),
    loggedInOfficerName: String = "",
    loggedInOfficerAccountId: String = "",
    existingDrafts: List<FormDraftEntity> = emptyList(),
    initialDraftToApply: FormDraftEntity? = null,
    paymentMethods: List<PaymentMethodEntity> = emptyList(),
    existingPaymentProofs: List<PaymentProofEntity> = emptyList(),
    sewaHakMilikPeriods: List<Int> = DEFAULT_SEWA_HAK_MILIK_PERIODS,
    customerPhotos: List<CustomerPhotoEntity> = emptyList(),
    drivePhotoMetadataList: List<DrivePhotoMetadataEntity> = emptyList(),
    isUploadingPhoto: Boolean = false,
    onSaveLocationPhoto: (CustomerEntity, Long?, Long?, String, Any, Double?, Double?) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteCustomerPhoto: (CustomerPhotoEntity) -> Unit = {},
    onRetryLocationPhotoUpload: (CustomerPhotoEntity) -> Unit = {},
    onDismiss: () -> Unit,
    onCaptureGpsLocation: ((GpsLocationResult) -> Unit) -> Unit,
    onSaveCustomerInline: (CustomerEntity, (CustomerEntity) -> Unit) -> Unit = { _, _ -> },
    onSavePaymentProof: (PaymentProofEntity) -> Unit = {},
    onDeletePaymentProof: (PaymentProofEntity) -> Unit = {},
    onSaveDraft: (String, Long, String, String, String, String, (FormDraftEntity) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteDraft: (String) -> Unit = {},
    onCorrectOfficerWithAuditLog: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    onResolveFreshUnitPrice: (suspend (Long, Int, String) -> Long)? = null,
    onPostMessage: (String) -> Unit = {},
    onSave: (RentalTransactionEntity, List<TransactionUnitItemEntity>, PaymentProofEntity?, List<StagedLocationDocPhoto>) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val formScrollState = rememberScrollState()
    var currentGuideStep by remember { mutableStateOf(1) } // 1..8 Guided Transaction Steps

    var selectedCustomer by remember {
        mutableStateOf(
            preselectedCustomer
                ?: customers.find { it.id == initialTransaction?.customerId }
                ?: customers.firstOrNull()
        )
    }
    var customerSearchFilter by remember { mutableStateOf("") }
    var showInlineCustomerModal by remember { mutableStateOf(false) }
    var inlineEditingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    val formSessionId = remember(initialTransaction?.id) {
        "TX_FORM_${initialTransaction?.id ?: 0L}_${UUID.randomUUID()}"
    }
    val ownedUnitIdsOnOpen = remember(initialTransaction?.id, initialUnitItems) {
        buildSet {
            if (initialTransaction != null && initialTransaction.unitId > 0L) {
                add(initialTransaction.unitId)
            }
            initialUnitItems.forEach { if (it.unitId > 0L) add(it.unitId) }
        }
    }
    DisposableEffect(formSessionId) {
        onDispose {
            UnitReservationManager.releaseSession(formSessionId)
        }
    }
    val reservedByOtherSessionUnitIds = remember(units, formSessionId) {
        UnitReservationManager.getReservedUnitIdsByOtherSessions(formSessionId)
    }

    var selectedUnit by remember {
        mutableStateOf(
            if (initialTransaction != null) {
                units.find { it.id == initialTransaction.unitId }
                    ?: units.find { u -> initialUnitItems.any { it.unitId == u.id } }
            } else {
                units.firstOrNull { u ->
                    u.isStrictlyAvailableForNewRental() && u.id !in reservedByOtherSessionUnitIds
                }
            }
        )
    }

    fun resolveDefaultUnitPeriodAndPrice(unit: FreezerUnitEntity?, preferredDays: Int? = null): Pair<Int, Long> {
        val configured = unit?.let { u ->
            unitRentalPricesMap[u.id].orEmpty().filter { it.price > 0L }.sortedBy { it.periodDays }
        }.orEmpty()
        if (configured.isNotEmpty()) {
            val matched = preferredDays?.let { d -> configured.find { it.periodDays == d } }
                ?: configured.find { it.periodDays == 30 }
                ?: configured.first()
            return matched.periodDays to matched.price
        }
        val fallbackDays = preferredDays ?: 30
        val fallbackPrice = unit?.cicilanPerBulan?.takeIf { it > 0L } ?: 300000L
        return fallbackDays to fallbackPrice
    }

    // Program Penyewaan: Sewa Reguler vs Sewa Hak Milik (disinkronkan dari unit items)
    val availableHakMilikPeriods = remember(sewaHakMilikPeriods) {
        if (sewaHakMilikPeriods.isEmpty()) DEFAULT_SEWA_HAK_MILIK_PERIODS else sewaHakMilikPeriods.sorted()
    }
    var jenisProgram by remember {
        mutableStateOf(initialTransaction?.jenisProgram?.ifBlank { "Sewa Reguler" } ?: "Sewa Reguler")
    }
    var totalPeriodeHakMilik by remember {
        mutableStateOf(
            initialTransaction?.totalPeriodeHakMilik?.takeIf { it > 0 }
                ?: (availableHakMilikPeriods.firstOrNull() ?: 12)
        )
    }
    var pembayaranKeHakMilik by remember {
        mutableStateOf((initialTransaction?.pembayaranKeHakMilik?.coerceAtLeast(1) ?: 1).toString())
    }
    var statusProgramHakMilik by remember {
        mutableStateOf(initialTransaction?.statusProgramHakMilik?.ifBlank { "Aktif" } ?: "Aktif")
    }

    val initialResolvedUnitPricing = remember(selectedUnit?.id, unitRentalPricesMap) {
        resolveDefaultUnitPeriodAndPrice(selectedUnit, initialTransaction?.durasiHari)
    }
    var tanggalPemesanan by remember { mutableStateOf(initialTransaction?.tanggalPemesanan ?: DateCurrencyUtils.todayIso()) }
    var hargaSewa by remember {
        mutableStateOf(
            initialTransaction?.hargaSewa?.toString()
                ?: if (selectedUnit != null) initialResolvedUnitPricing.second.toString() else "0"
        )
    }
    var durasiHari by remember {
        mutableStateOf(initialTransaction?.durasiHari?.toString() ?: initialResolvedUnitPricing.first.toString())
    }
    var tanggalMulai by remember { mutableStateOf(initialTransaction?.tanggalMulaiSewa ?: DateCurrencyUtils.todayIso()) }

    val computedDays = durasiHari.toIntOrNull()?.coerceAtLeast(1) ?: 30
    var tanggalAkhir by remember(tanggalMulai, computedDays) {
        mutableStateOf(
            initialTransaction?.tanggalAkhirSewa ?: DateCurrencyUtils.addDaysToIsoDate(tanggalMulai, computedDays)
        )
    }
    var tanggalJatuhTempo by remember(tanggalAkhir) {
        mutableStateOf(initialTransaction?.tanggalJatuhTempo ?: tanggalAkhir)
    }

    var multiUnitItems by remember {
        mutableStateOf(
            when {
                initialUnitItems.isNotEmpty() -> initialUnitItems
                selectedUnit != null -> {
                    val u = selectedUnit!!
                    val initialPrice = initialTransaction?.hargaSewa ?: initialResolvedUnitPricing.second
                    val initialDur = initialTransaction?.durasiHari ?: initialResolvedUnitPricing.first
                    val startDate = initialTransaction?.tanggalMulaiSewa ?: DateCurrencyUtils.todayIso()
                    val endDate = initialTransaction?.tanggalAkhirSewa ?: DateCurrencyUtils.addDaysToIsoDate(startDate, initialDur)
                    val dueDate = initialTransaction?.tanggalJatuhTempo ?: endDate
                    listOf(
                        TransactionUnitItemEntity(
                            transactionId = initialTransaction?.id ?: 0L,
                            customerId = selectedCustomer?.id ?: 0L,
                            unitId = u.id,
                            unitIndexNumber = 1,
                            kodeUnitSnapshot = u.kodeUnit.ifBlank { initialTransaction?.kodeUnitSnapshot.orEmpty() },
                            namaUnitSnapshot = u.namaUnit.ifBlank { initialTransaction?.namaUnitSnapshot.orEmpty() },
                            jenisUnitSnapshot = u.jenisUnit,
                            merekSnapshot = u.merek,
                            kapasitasLiterSnapshot = u.kapasitasLiter,
                            jenisProgram = initialTransaction?.jenisProgram?.ifBlank { "Sewa Reguler" } ?: "Sewa Reguler",
                            totalPeriodeHakMilik = initialTransaction?.totalPeriodeHakMilik ?: 0,
                            pembayaranKeHakMilik = initialTransaction?.pembayaranKeHakMilik ?: 1,
                            statusProgramHakMilik = initialTransaction?.statusProgramHakMilik?.ifBlank { "Aktif" } ?: "Aktif",
                            hargaSewa = initialPrice,
                            durasiHari = initialDur,
                            tanggalMulaiSewa = startDate,
                            tanggalAkhirSewa = endDate,
                            tanggalJatuhTempo = dueDate,
                            nominalPembayaran = initialTransaction?.nominalPembayaran ?: initialPrice,
                            statusPembayaran = initialTransaction?.statusPembayaran ?: "Lunas",
                            statusPenyewaan = initialTransaction?.statusPenyewaan ?: "Dalam Penyewaan",
                            fotoKondisiDepanUri = initialTransaction?.fotoUnitSebelumKirimUri.orEmpty(),
                            fotoKondisiSampingUri = initialTransaction?.fotoKondisiFisikTransUri.orEmpty(),
                            fotoKondisiDalamUri = initialTransaction?.fotoBagianDalamTransUri.orEmpty(),
                            fotoSerahTerimaPelangganUri = initialTransaction?.fotoSerahTerimaUri.orEmpty(),
                            fotoSerahTerimaLokasiUri = initialTransaction?.fotoFreezerDiLokasiUri.orEmpty(),
                            fotoBuktiPenerimaanUri = initialTransaction?.fotoPelangganBersamaFreezerUri.orEmpty()
                        )
                    )
                }
                else -> emptyList()
            }
        )
    }

    // Reservasi sementara otomatis untuk unit baru yang dipilih dalam form aktif (dilepas otomatis saat form ditutup/dibatalkan)
    LaunchedEffect(multiUnitItems, formSessionId) {
        val newUnitIdsToReserve = multiUnitItems
            .map { it.unitId }
            .filter { it > 0L && it !in ownedUnitIdsOnOpen }
        UnitReservationManager.syncSessionReservations(formSessionId, newUnitIdsToReserve)
    }

    var ongkir by remember { mutableStateOf(initialTransaction?.ongkir?.toString() ?: "35000") }
    var deposit by remember { mutableStateOf(initialTransaction?.deposit?.toString() ?: "0") }
    var diskon by remember { mutableStateOf(initialTransaction?.diskon?.toString() ?: "0") }

    val effectiveUnitRentTotal = remember(multiUnitItems, hargaSewa) {
        if (multiUnitItems.isNotEmpty()) {
            multiUnitItems.sumOf { it.hargaSewa.coerceAtLeast(0L) }
        } else {
            hargaSewa.toLongOrNull() ?: 0L
        }
    }
    val calculatedTotalTagihan = remember(effectiveUnitRentTotal, ongkir, deposit, diskon) {
        val ok = ongkir.toLongOrNull() ?: 0L
        val dep = deposit.toLongOrNull() ?: 0L
        val disc = diskon.toLongOrNull() ?: 0L
        (effectiveUnitRentTotal + ok + dep - disc).coerceAtLeast(0L)
    }

    var nominalPembayaran by remember(calculatedTotalTagihan) {
        mutableStateOf(initialTransaction?.nominalPembayaran?.toString() ?: calculatedTotalTagihan.toString())
    }

    // Sinkronisasi harga sewa reaktif: setiap kali harga unit diubah di Data Unit atau disinkronkan dari Cloud -> Lokal,
    // form transaksi otomatis memperbarui harga item unit & menghitung ulang Subtotal/Total di Langkah 3 (Biaya & JT).
    val lastSyncedDbPriceByUnitId = remember { mutableMapOf<Long, Long>() }
    LaunchedEffect(units, unitRentalPricesMap) {
        if (initialTransaction != null || multiUnitItems.isEmpty()) return@LaunchedEffect
        var anyPriceChanged = false
        val refreshedItems = multiUnitItems.map { item ->
            val liveUnit = units.find { it.id == item.unitId } ?: return@map item
            val unitPrices = unitRentalPricesMap[liveUnit.id].orEmpty()
            val isHm = item.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
            val freshPrice = com.example.ui.components.resolveReactiveUnitRentalPrice(
                unit = liveUnit,
                configuredPrices = unitPrices,
                periodDays = item.durasiHari.coerceAtLeast(1),
                isHakMilik = isHm
            )
            val prevDbPrice = lastSyncedDbPriceByUnitId[liveUnit.id]
            lastSyncedDbPriceByUnitId[liveUnit.id] = freshPrice
            val userDidNotManuallyOverride = prevDbPrice == null || item.hargaSewa == prevDbPrice
            if (freshPrice > 0L && item.hargaSewa != freshPrice && userDidNotManuallyOverride) {
                anyPriceChanged = true
                val shouldSyncNominal = item.nominalPembayaran <= 0L || item.nominalPembayaran == item.hargaSewa
                val updatedNominal = if (shouldSyncNominal) freshPrice else item.nominalPembayaran
                item.copy(
                    hargaSewa = freshPrice,
                    nominalPembayaran = updatedNominal,
                    statusPembayaran = if (updatedNominal >= freshPrice) "Lunas" else if (updatedNominal > 0L) "Dibayar Sebagian" else "Belum Bayar"
                )
            } else {
                item
            }
        }
        if (anyPriceChanged) {
            multiUnitItems = refreshedItems
            val newTotalRent = refreshedItems.sumOf { it.hargaSewa.coerceAtLeast(0L) }
            hargaSewa = newTotalRent.toString()
            val ok = ongkir.toLongOrNull() ?: 0L
            val dep = deposit.toLongOrNull() ?: 0L
            val disc = diskon.toLongOrNull() ?: 0L
            nominalPembayaran = (newTotalRent + ok + dep - disc).coerceAtLeast(0L).toString()
        }
    }
    var statusPembayaran by remember { mutableStateOf(initialTransaction?.statusPembayaran ?: "Lunas") }
    var statusPenyewaan by remember { mutableStateOf(initialTransaction?.statusPenyewaan ?: "Dalam Penyewaan") }

    val activeMethods = remember(paymentMethods) {
        val active = paymentMethods.filter { it.isAktif }
        active
    }
    val methodOptionNames = remember(activeMethods) {
        if (activeMethods.isEmpty()) METODE_PEMBAYARAN_OPTIONS else activeMethods.map { it.namaMetode }
    }
    var metodePembayaran by remember {
        mutableStateOf(initialTransaction?.metodePembayaran ?: (methodOptionNames.firstOrNull() ?: "Transfer BCA"))
    }
    var catatan by remember { mutableStateOf(initialTransaction?.catatan ?: "") }

    // Inline Payment Proof fields for New Transaction
    var fotoBuktiPembayaranUri by remember { mutableStateOf(existingPaymentProofs.firstOrNull()?.fotoBuktiUri ?: "") }
    var namaPengirimBukti by remember(selectedCustomer) { mutableStateOf(selectedCustomer?.namaLengkap ?: "") }
    var catatanBuktiPembayaran by remember { mutableStateOf("Pembayaran awal sewa freezer") }
    var waktuUploadBukti by remember { mutableStateOf(DateCurrencyUtils.nowDateTimeDisplay()) }
    var showProofCameraDialog by remember { mutableStateOf(false) }
    var showAddStagedProofDialog by remember { mutableStateOf(false) }

    val proofGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val dir = File(context.filesDir, "photos/payment_proofs")
            if (!dir.exists()) dir.mkdirs()
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("jpeg") -> "jpeg"
                else -> "jpg"
            }
            val destFile = File(dir, "BUKTI_NEW_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            fotoBuktiPembayaranUri = destFile.absolutePath
            waktuUploadBukti = DateCurrencyUtils.nowDateTimeDisplay()
        }
    }

    val proofCameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showProofCameraDialog = true
    }

    val txFolder = remember {
        "tx_${initialTransaction?.id ?: System.currentTimeMillis()}"
    }
    val previewInvoiceLabel = initialTransaction?.nomorInvoice?.ifBlank { "INV/SAF/AUTO" } ?: "INV/SAF/AUTO"

    // Photo URIs + Timestamps + Captions (Section 3: Tanpa watermark permanen pada foto asli)
    var fotoKtpDepanTransUri by remember { mutableStateOf(initialTransaction?.fotoKtpDepanTransUri ?: selectedCustomer?.fotoKtpDepanUri ?: "") }
    var fotoKtpBelakangTransUri by remember { mutableStateOf(initialTransaction?.fotoKtpBelakangTransUri ?: selectedCustomer?.fotoKtpBelakangUri ?: "") }
    var waktuFotoKtp by remember { mutableStateOf(initialTransaction?.waktuFotoKtp ?: DateCurrencyUtils.nowDateTimeDisplay()) }
    var ketFotoKtp by remember { mutableStateOf(initialTransaction?.ketFotoKtp ?: "Foto KTP asli pelanggan terverifikasi") }

    var fotoUnitSebelumKirimUri by remember { mutableStateOf(initialTransaction?.fotoUnitSebelumKirimUri ?: selectedUnit?.fotoUnitUri ?: "") }
    var waktuFotoUnitSebelum by remember { mutableStateOf(initialTransaction?.waktuFotoUnitSebelum ?: DateCurrencyUtils.nowDateTimeDisplay()) }
    var ketFotoUnitSebelum by remember { mutableStateOf(initialTransaction?.ketFotoUnitSebelum ?: "Unit siap dikirim dalam kondisi dingin normal") }

    var fotoKondisiFisikTransUri by remember { mutableStateOf(initialTransaction?.fotoKondisiFisikTransUri ?: selectedUnit?.fotoKondisiFisikUri ?: "") }
    var fotoBagianDalamTransUri by remember { mutableStateOf(initialTransaction?.fotoBagianDalamTransUri ?: selectedUnit?.fotoBagianDalamUri ?: "") }
    var fotoMerekSeriTransUri by remember { mutableStateOf(initialTransaction?.fotoMerekSeriTransUri ?: selectedUnit?.fotoMerekSeriUri ?: "") }

    var fotoSerahTerimaUri by remember { mutableStateOf(initialTransaction?.fotoSerahTerimaUri ?: "") }
    var waktuFotoSerahTerima by remember { mutableStateOf(initialTransaction?.waktuFotoSerahTerima ?: "") }
    var ketFotoSerahTerima by remember { mutableStateOf(initialTransaction?.ketFotoSerahTerima ?: "Dokumentasi serah terima unit bersama pelanggan") }

    var fotoKondisiSerahTerimaUri by remember { mutableStateOf(initialTransaction?.fotoKondisiSerahTerimaUri ?: "") }
    var fotoPelangganBersamaFreezerUri by remember { mutableStateOf(initialTransaction?.fotoPelangganBersamaFreezerUri ?: "") }

    var fotoFreezerDiLokasiUri by remember { mutableStateOf(initialTransaction?.fotoFreezerDiLokasiUri ?: "") }
    var waktuFotoFreezerDiLokasi by remember { mutableStateOf(initialTransaction?.waktuFotoFreezerDiLokasi ?: "") }
    var ketFotoFreezerDiLokasi by remember { mutableStateOf(initialTransaction?.ketFotoFreezerDiLokasi ?: "Posisi freezer setelah terpasang di lokasi usaha") }

    var fotoDepanLokasiUri by remember { mutableStateOf(initialTransaction?.fotoDepanLokasiUri ?: "") }
    var waktuFotoDepanLokasi by remember { mutableStateOf(initialTransaction?.waktuFotoDepanLokasi ?: "") }
    var ketFotoDepanLokasi by remember { mutableStateOf(initialTransaction?.ketFotoDepanLokasi ?: "Tampak depan toko/tempat usaha penggunaan freezer") }
    var stagedLocationPhotos by remember { mutableStateOf<List<StagedLocationDocPhoto>>(emptyList()) }

    // Section 3 & 4: Usage Location Fields (Separate from KTP Address!)
    var namaLokasiUsaha by remember {
        mutableStateOf(
            initialTransaction?.namaLokasiUsaha?.ifBlank {
                selectedCustomer?.namaLokasiDefault?.ifBlank { "Outlet Usaha ${selectedCustomer?.namaLengkap ?: ""}" }
                    ?: "Outlet Usaha ${selectedCustomer?.namaLengkap ?: ""}"
            } ?: "Outlet Usaha ${selectedCustomer?.namaLengkap ?: ""}"
        )
    }
    var namaPenanggungJawab by remember {
        mutableStateOf(initialTransaction?.namaPenanggungJawab ?: selectedCustomer?.namaLengkap ?: "")
    }
    var nomorWhatsappLokasi by remember {
        mutableStateOf(initialTransaction?.nomorWhatsappLokasi ?: selectedCustomer?.nomorHp ?: "")
    }
    var alamatLokasiGps by remember {
        mutableStateOf(initialTransaction?.alamatLokasiGps ?: selectedCustomer?.lokasiPenggunaanDefault ?: "")
    }
    var rtRwLokasi by remember {
        mutableStateOf(initialTransaction?.rtRwLokasi ?: selectedCustomer?.rtRwLokasiDefault ?: "")
    }
    var provinsiLokasi by remember {
        mutableStateOf(initialTransaction?.provinsiLokasi ?: "Jawa Tengah")
    }
    var kabupatenKotaLokasi by remember {
        mutableStateOf(initialTransaction?.kabupatenKotaLokasi ?: selectedCustomer?.kabupatenKotaLokasiDefault?.ifBlank { "Kota Surakarta" } ?: "Kota Surakarta")
    }
    var kecamatanLokasi by remember {
        mutableStateOf(initialTransaction?.kecamatanLokasi ?: selectedCustomer?.kecamatanLokasiDefault?.ifBlank { selectedCustomer?.kecamatan ?: "" } ?: "")
    }
    var kelurahanDesaLokasi by remember {
        mutableStateOf(initialTransaction?.kelurahanDesaLokasi ?: selectedCustomer?.kelurahanLokasiDefault?.ifBlank { selectedCustomer?.kelurahanDesa ?: "" } ?: "")
    }
    var kodePosLokasi by remember {
        mutableStateOf(initialTransaction?.kodePosLokasi ?: "57141")
    }
    var latitude by remember { mutableStateOf<Double?>(initialTransaction?.latitude ?: selectedCustomer?.latitudeDefault) }
    var longitude by remember { mutableStateOf<Double?>(initialTransaction?.longitude ?: selectedCustomer?.longitudeDefault) }
    var akurasiGpsMeter by remember { mutableStateOf<Float?>(initialTransaction?.akurasiGpsMeter) }
    var linkGoogleMaps by remember {
        mutableStateOf(
            initialTransaction?.linkGoogleMaps ?: if (latitude != null && longitude != null) {
                "https://maps.google.com/?q=$latitude,$longitude"
            } else ""
        )
    }
    var catatanLokasi by remember { mutableStateOf(initialTransaction?.catatanLokasi ?: "") }
    var tanggalLokasiGps by remember { mutableStateOf(initialTransaction?.tanggalLokasiGps ?: "") }
    var jamLokasiGps by remember { mutableStateOf(initialTransaction?.jamLokasiGps ?: "") }
    var gpsErrorDialogMessage by remember { mutableStateOf<String?>(null) }

    val isOfficerSnapshotLocked = remember(initialTransaction) {
        initialTransaction != null && (
            initialTransaction.officerSnapshotLocked ||
                initialTransaction.namaPetugas.isNotBlank() ||
                initialTransaction.tandaTanganPetugasUri.isNotBlank()
            )
    }
    val defaultNewOfficerName = remember(loggedInOfficerName) {
        loggedInOfficerName.ifBlank { "Petugas Operasional SAF" }
    }
    var tandaTanganPelangganUri by remember { mutableStateOf(initialTransaction?.tandaTanganPelangganUri ?: "") }
    var tandaTanganPetugasUri by remember { mutableStateOf(initialTransaction?.tandaTanganPetugasUri ?: "") }
    var namaPenerima by remember { mutableStateOf(initialTransaction?.namaPenerima ?: selectedCustomer?.namaLengkap ?: "") }
    var tanggalPenerimaan by remember { mutableStateOf(initialTransaction?.tanggalPenerimaan ?: DateCurrencyUtils.todayIso()) }
    var jamPenerimaan by remember { mutableStateOf(initialTransaction?.jamPenerimaan ?: DateCurrencyUtils.nowTime()) }
    var namaPetugas by remember {
        mutableStateOf(initialTransaction?.namaPetugas?.ifBlank { defaultNewOfficerName } ?: defaultNewOfficerName)
    }
    var officerAccountId by remember {
        mutableStateOf(initialTransaction?.officerAccountId?.ifBlank { loggedInOfficerAccountId } ?: loggedInOfficerAccountId)
    }
    var officerSignedAt by remember {
        mutableStateOf(initialTransaction?.officerSignedAt?.ifBlank { DateCurrencyUtils.nowDateTimeDisplay() } ?: DateCurrencyUtils.nowDateTimeDisplay())
    }
    var showFormOfficerCorrectionDialog by remember { mutableStateOf(false) }
    var keteranganKondisiFreezer by remember { mutableStateOf(initialTransaction?.keteranganKondisiFreezer ?: "Unit berfungsi normal, bersih, dingin optimal") }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }

    // ==================== AUTO-DRAFT FORM TRANSAKSI (TERISOLASI LOKAL) ====================
    val targetTxDraftId = initialTransaction?.id ?: 0L
    val matchingSavedDraft = remember(existingDrafts, targetTxDraftId) {
        existingDrafts.firstOrNull { it.formType == FormDraftTypes.TRANSACTION && it.targetEntityId == targetTxDraftId }
    }
    var activeDraftUuid by remember { mutableStateOf(initialDraftToApply?.uuid ?: "") }
    var draftSavedAtDisplay by remember { mutableStateOf("") }
    var showResumeDraftBanner by remember(matchingSavedDraft?.uuid, initialDraftToApply?.uuid) {
        mutableStateOf(matchingSavedDraft != null && initialDraftToApply == null)
    }
    var draftHasUserEdits by remember { mutableStateOf(false) }

    fun applyTransactionDraft(draft: FormDraftEntity) {
        val obj = FormDraftCodec.parseJsonObject(draft.payloadJson)
        val cid = obj.optLong("customerId", 0L)
        if (cid > 0L) customers.find { it.id == cid }?.let { selectedCustomer = it }
        val uid = obj.optLong("unitId", 0L)
        if (uid > 0L) units.find { it.id == uid }?.let { selectedUnit = it }
        obj.optString("jenisProgram").takeIf { it.isNotBlank() }?.let { jenisProgram = it }
        obj.optString("durasiHari").takeIf { it.isNotBlank() }?.let { durasiHari = it }
        obj.optString("hargaSewa").takeIf { it.isNotBlank() }?.let { hargaSewa = it }
        obj.optString("ongkir").takeIf { it.isNotBlank() }?.let { ongkir = it }
        obj.optString("deposit").takeIf { it.isNotBlank() }?.let { deposit = it }
        obj.optString("diskon").takeIf { it.isNotBlank() }?.let { diskon = it }
        obj.optString("nominalPembayaran").takeIf { it.isNotBlank() }?.let { nominalPembayaran = it }
        obj.optString("metodePembayaran").takeIf { it.isNotBlank() }?.let { metodePembayaran = it }
        obj.optString("catatan").let { if (it.isNotBlank()) catatan = it }
        obj.optString("namaLokasiUsaha").takeIf { it.isNotBlank() }?.let { namaLokasiUsaha = it }
        obj.optString("alamatLokasiGps").takeIf { it.isNotBlank() }?.let { alamatLokasiGps = it }
        obj.optString("namaPenerima").takeIf { it.isNotBlank() }?.let { namaPenerima = it }
        obj.optString("keteranganKondisiFreezer").takeIf { it.isNotBlank() }?.let { keteranganKondisiFreezer = it }
        if (!isOfficerSnapshotLocked) {
            obj.optString("namaPetugas").takeIf { it.isNotBlank() }?.let { namaPetugas = it }
        }
        activeDraftUuid = draft.uuid
        draftSavedAtDisplay = draft.updatedAtDisplay
        showResumeDraftBanner = false
    }

    LaunchedEffect(initialDraftToApply?.uuid) {
        if (initialDraftToApply != null) {
            applyTransactionDraft(initialDraftToApply)
        }
    }

    LaunchedEffect(
        selectedCustomer?.id,
        selectedUnit?.id,
        jenisProgram,
        durasiHari,
        hargaSewa,
        ongkir,
        deposit,
        diskon,
        nominalPembayaran,
        metodePembayaran,
        catatan,
        namaLokasiUsaha,
        alamatLokasiGps,
        namaPenerima,
        keteranganKondisiFreezer,
        multiUnitItems.size
    ) {
        if (!draftHasUserEdits) {
            draftHasUserEdits = true
            return@LaunchedEffect
        }
        delay(800L)
        val payload = FormDraftCodec.encodeMap(
            mapOf(
                "customerId" to (selectedCustomer?.id ?: 0L),
                "unitId" to (selectedUnit?.id ?: 0L),
                "jenisProgram" to jenisProgram,
                "durasiHari" to durasiHari,
                "hargaSewa" to hargaSewa,
                "ongkir" to ongkir,
                "deposit" to deposit,
                "diskon" to diskon,
                "nominalPembayaran" to nominalPembayaran,
                "metodePembayaran" to metodePembayaran,
                "catatan" to catatan,
                "namaLokasiUsaha" to namaLokasiUsaha,
                "alamatLokasiGps" to alamatLokasiGps,
                "namaPenerima" to namaPenerima,
                "keteranganKondisiFreezer" to keteranganKondisiFreezer,
                "namaPetugas" to namaPetugas,
                "unitCount" to multiUnitItems.size
            )
        )
        val draftTitle = "Transaksi: ${selectedCustomer?.namaLengkap ?: namaPenerima.ifBlank { "Pelanggan Baru" }}"
        val draftSub = "${multiUnitItems.size} Unit • $jenisProgram • ${DateCurrencyUtils.formatRupiah(calculatedTotalTagihan)}"
        onSaveDraft(
            FormDraftTypes.TRANSACTION,
            targetTxDraftId,
            draftTitle,
            draftSub,
            payload,
            activeDraftUuid
        ) { saved ->
            activeDraftUuid = saved.uuid
            draftSavedAtDisplay = saved.updatedAtDisplay
        }
    }

    var txContactImportNotice by remember { mutableStateOf<String?>(null) }
    val launchTxContactPicker = rememberWhatsAppContactImporter(
        onContactImported = { normalizedWa, upperName ->
            nomorWhatsappLokasi = normalizedWa
            if (namaPenanggungJawab.isBlank() && upperName.isNotBlank()) {
                namaPenanggungJawab = upperName
            }
            if (namaPenerima.isBlank() && upperName.isNotBlank()) {
                namaPenerima = upperName
            }
            if (selectedCustomer == null && upperName.isNotBlank()) {
                val matchedExisting = customers.find {
                    PhoneNumberNormalizer.areEquivalentWhatsAppNumbers(it.nomorHp, normalizedWa)
                }
                if (matchedExisting != null) {
                    selectedCustomer = matchedExisting
                }
            }
            txContactImportNotice = "Kontak WhatsApp diimpor: $normalizedWa"
        },
        onStatusMessage = { msg -> txContactImportNotice = msg }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onCaptureGpsLocation { res ->
                if (res.isSuccess) {
                    latitude = res.latitude
                    longitude = res.longitude
                    akurasiGpsMeter = res.accuracyMeters
                    alamatLokasiGps = res.addressText
                    if (res.provinsi.isNotBlank()) provinsiLokasi = res.provinsi
                    if (res.kabupatenKota.isNotBlank()) kabupatenKotaLokasi = res.kabupatenKota
                    if (res.kecamatan.isNotBlank()) kecamatanLokasi = res.kecamatan
                    if (res.kelurahanDesa.isNotBlank()) kelurahanDesaLokasi = res.kelurahanDesa
                    if (res.kodePos.isNotBlank()) kodePosLokasi = res.kodePos
                    linkGoogleMaps = res.googleMapsLink
                    tanggalLokasiGps = res.dateIso
                    jamLokasiGps = res.timeStr
                } else {
                    gpsErrorDialogMessage = res.errorMessage
                }
            }
        } else {
            gpsErrorDialogMessage = "Izin lokasi GPS ditolak. Anda tetap dapat memasukkan alamat dan menggeser pin pada peta secara manual."
        }
    }

    fun buildTransactionEntity(): RentalTransactionEntity {
        val cust = selectedCustomer
        val primaryUnitItem = multiUnitItems.firstOrNull()
        val unit = primaryUnitItem?.let { item -> units.find { it.id == item.unitId } } ?: selectedUnit
        val approxMonths = ceil(computedDays / 30.0).toInt().coerceAtLeast(1)
        val finalMapsLink = linkGoogleMaps.ifBlank {
            if (latitude != null && longitude != null) "https://maps.google.com/?q=$latitude,$longitude" else ""
        }
        val primaryHakMilikItem = multiUnitItems.firstOrNull {
            it.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
        }
        val isHakMilik = primaryHakMilikItem != null || jenisProgram.contains("Hak Milik", ignoreCase = true)
        val safeTotalPeriode = if (isHakMilik) {
            (primaryHakMilikItem?.totalPeriodeHakMilik?.takeIf { it > 0 } ?: totalPeriodeHakMilik).coerceAtLeast(1)
        } else 0
        val safePembayaranKe = if (isHakMilik) {
            (primaryHakMilikItem?.pembayaranKeHakMilik?.takeIf { it > 0 } ?: (pembayaranKeHakMilik.toIntOrNull() ?: 1))
                .coerceIn(1, safeTotalPeriode)
        } else 1
        val computedStatusProgram = if (isHakMilik) {
            val baseStatus = primaryHakMilikItem?.statusProgramHakMilik?.ifBlank { statusProgramHakMilik } ?: statusProgramHakMilik
            if (safePembayaranKe >= safeTotalPeriode && statusPembayaran.equals("Lunas", ignoreCase = true)) {
                if (baseStatus == "Selesai") "Selesai" else "Lunas"
            } else {
                baseStatus
            }
        } else "Aktif"

        return RentalTransactionEntity(
            id = initialTransaction?.id ?: 0L,
            nomorInvoice = initialTransaction?.nomorInvoice ?: "",
            tanggalPemesanan = tanggalPemesanan,
            customerId = cust?.id ?: (initialTransaction?.customerId ?: 0L),
            namaPelangganSnapshot = cust?.namaLengkap ?: namaPenerima.ifBlank { "Pelanggan SAF" },
            unitId = primaryUnitItem?.unitId ?: (unit?.id ?: (initialTransaction?.unitId ?: 0L)),
            namaUnitSnapshot = primaryUnitItem?.namaUnitSnapshot?.ifBlank { unit?.namaUnit ?: "Unit Freezer SAF" }
                ?: (unit?.namaUnit ?: "Unit Freezer SAF"),
            kodeUnitSnapshot = primaryUnitItem?.kodeUnitSnapshot?.ifBlank { unit?.kodeUnit ?: "SAF-001" }
                ?: (unit?.kodeUnit ?: "SAF-001"),
            jenisProgram = if (isHakMilik) "Sewa Hak Milik" else "Sewa Reguler",
            totalPeriodeHakMilik = safeTotalPeriode,
            pembayaranKeHakMilik = safePembayaranKe,
            statusProgramHakMilik = computedStatusProgram,
            hargaSewa = effectiveUnitRentTotal,
            durasiSewaBulan = approxMonths,
            durasiHari = primaryUnitItem?.durasiHari?.coerceAtLeast(1) ?: computedDays,
            tanggalMulaiSewa = primaryUnitItem?.tanggalMulaiSewa?.ifBlank { tanggalMulai } ?: tanggalMulai,
            tanggalAkhirSewa = primaryUnitItem?.tanggalAkhirSewa?.ifBlank { tanggalAkhir } ?: tanggalAkhir,
            tanggalJatuhTempo = primaryUnitItem?.tanggalJatuhTempo?.ifBlank { tanggalJatuhTempo } ?: tanggalJatuhTempo,
            nominalPembayaran = nominalPembayaran.toLongOrNull() ?: 0L,
            ongkir = ongkir.toLongOrNull() ?: 0L,
            deposit = deposit.toLongOrNull() ?: 0L,
            diskon = diskon.toLongOrNull() ?: 0L,
            totalTagihan = calculatedTotalTagihan,
            statusPembayaran = statusPembayaran,
            statusPenyewaan = statusPenyewaan,
            metodePembayaran = metodePembayaran,
            catatan = catatan,
            fotoKtpDepanTransUri = fotoKtpDepanTransUri,
            fotoKtpBelakangTransUri = fotoKtpBelakangTransUri,
            fotoUnitSebelumKirimUri = fotoUnitSebelumKirimUri,
            fotoKondisiFisikTransUri = fotoKondisiFisikTransUri,
            fotoBagianDalamTransUri = fotoBagianDalamTransUri,
            fotoMerekSeriTransUri = fotoMerekSeriTransUri,
            fotoSerahTerimaUri = fotoSerahTerimaUri,
            fotoKondisiSerahTerimaUri = fotoKondisiSerahTerimaUri,
            fotoPelangganBersamaFreezerUri = fotoPelangganBersamaFreezerUri,
            fotoFreezerDiLokasiUri = fotoFreezerDiLokasiUri,
            fotoDepanLokasiUri = fotoDepanLokasiUri,
            waktuFotoKtp = waktuFotoKtp,
            ketFotoKtp = ketFotoKtp,
            waktuFotoUnitSebelum = waktuFotoUnitSebelum,
            ketFotoUnitSebelum = ketFotoUnitSebelum,
            waktuFotoSerahTerima = waktuFotoSerahTerima,
            ketFotoSerahTerima = ketFotoSerahTerima,
            waktuFotoFreezerDiLokasi = waktuFotoFreezerDiLokasi,
            ketFotoFreezerDiLokasi = ketFotoFreezerDiLokasi,
            waktuFotoDepanLokasi = waktuFotoDepanLokasi,
            ketFotoDepanLokasi = ketFotoDepanLokasi,
            namaLokasiUsaha = namaLokasiUsaha,
            namaPenanggungJawab = namaPenanggungJawab.ifBlank { cust?.namaLengkap ?: "" },
            nomorWhatsappLokasi = nomorWhatsappLokasi.ifBlank { cust?.nomorHp ?: "" },
            alamatLokasiGps = alamatLokasiGps,
            rtRwLokasi = rtRwLokasi,
            provinsiLokasi = provinsiLokasi,
            kabupatenKotaLokasi = kabupatenKotaLokasi,
            kecamatanLokasi = kecamatanLokasi,
            kelurahanDesaLokasi = kelurahanDesaLokasi,
            kodePosLokasi = kodePosLokasi,
            latitude = latitude,
            longitude = longitude,
            akurasiGpsMeter = akurasiGpsMeter,
            linkGoogleMaps = finalMapsLink,
            catatanLokasi = catatanLokasi,
            tanggalLokasiGps = tanggalLokasiGps,
            jamLokasiGps = jamLokasiGps,
            tandaTanganPelangganUri = tandaTanganPelangganUri,
            tandaTanganPetugasUri = if (isOfficerSnapshotLocked) {
                initialTransaction?.tandaTanganPetugasUri ?: tandaTanganPetugasUri
            } else {
                tandaTanganPetugasUri
            },
            namaPenerima = namaPenerima.ifBlank { cust?.namaLengkap ?: "" },
            tanggalPenerimaan = tanggalPenerimaan,
            jamPenerimaan = jamPenerimaan,
            namaPetugas = if (isOfficerSnapshotLocked) {
                initialTransaction?.namaPetugas ?: namaPetugas
            } else {
                namaPetugas.ifBlank { defaultNewOfficerName }
            },
            officerAccountId = if (isOfficerSnapshotLocked) {
                initialTransaction?.officerAccountId ?: officerAccountId
            } else {
                officerAccountId.ifBlank { loggedInOfficerAccountId }
            },
            officerSnapshotLocked = true,
            officerSignedAt = if (isOfficerSnapshotLocked) {
                initialTransaction?.officerSignedAt?.ifBlank { officerSignedAt } ?: officerSignedAt
            } else {
                officerSignedAt.ifBlank { DateCurrencyUtils.nowDateTimeDisplay() }
            },
            keteranganKondisiFreezer = keteranganKondisiFreezer,
            pdfInvoicePath = initialTransaction?.pdfInvoicePath ?: "",
            parentTransactionId = initialTransaction?.parentTransactionId,
            tandaTanganOwnerSnapshotUri = initialTransaction?.tandaTanganOwnerSnapshotUri ?: "",
            ukuranTandaTanganOwnerSnapshot = initialTransaction?.ukuranTandaTanganOwnerSnapshot ?: 42f,
            posisiTandaTanganOwnerSnapshot = initialTransaction?.posisiTandaTanganOwnerSnapshot ?: "RIGHT"
        )
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
                            text = if (initialTransaction == null) "Form Transaksi & Kontrak Terpadu" else "Edit Transaksi ${initialTransaction.nomorInvoice}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Panduan Langkah $currentGuideStep/8 • SEWAin Aja Freezer SOC",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (draftSavedAtDisplay.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            DraftAutoSaveStatusPill(savedAtDisplay = draftSavedAtDisplay)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                if (showResumeDraftBanner && matchingSavedDraft != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ResumeDraftBanner(
                        draft = matchingSavedDraft,
                        onResume = { applyTransactionDraft(matchingSavedDraft) },
                        onDiscard = {
                            onDeleteDraft(matchingSavedDraft.uuid)
                            showResumeDraftBanner = false
                        }
                    )
                }

                // 8-Step Guided Transaction Flow Bar (Section 9)
                val guidedSteps = remember {
                    listOf(
                        1 to "Pelanggan",
                        2 to "Unit & Program",
                        3 to "Biaya & JT",
                        4 to "Pembayaran",
                        5 to "Dokumentasi",
                        6 to "TTD",
                        7 to "Review",
                        8 to "Simpan PDF"
                    )
                }
                fun jumpToGuideStep(step: Int) {
                    currentGuideStep = step.coerceIn(1, 8)
                    scope.launch {
                        val maxScroll = formScrollState.maxValue.coerceAtLeast(1)
                        val fraction = when (currentGuideStep) {
                            1 -> 0.0f
                            2 -> 0.16f
                            3 -> 0.34f
                            4 -> 0.46f
                            5 -> 0.64f
                            6 -> 0.84f
                            7 -> 0.94f
                            else -> 1.0f
                        }
                        formScrollState.animateScrollTo((maxScroll * fraction).toInt())
                    }
                }
                var txFormValidationResult by remember { mutableStateOf<FormValidationResult?>(null) }
                fun validateTransactionBeforeConfirm(): Boolean {
                    val duplicateUnitIds = multiUnitItems.groupingBy { it.unitId }.eachCount().any { it.value > 1 }
                    val validUnitCount = multiUnitItems.count { it.unitId > 0L }
                    val effectiveCustomerId = selectedCustomer?.id ?: if (namaPenerima.isNotBlank()) 1L else 0L
                    val res = FormValidationEngine.validateTransactionForm(
                        customerId = effectiveCustomerId,
                        selectedUnitCount = validUnitCount,
                        tanggalMulaiSewa = tanggalMulai,
                        totalTagihan = calculatedTotalTagihan,
                        alamatLokasiGps = alamatLokasiGps.ifBlank { selectedCustomer?.alamatLengkap ?: "" },
                        duplicateUnitIds = duplicateUnitIds
                    )
                    if (!res.isValid) {
                        txFormValidationResult = res
                        onPostMessage(res.bannerMessage)
                        when (res.firstErrorFieldKey) {
                            "customerId" -> jumpToGuideStep(1)
                            "units" -> jumpToGuideStep(2)
                            "tanggalMulaiSewa", "totalTagihan" -> jumpToGuideStep(3)
                            "alamatLokasiGps" -> jumpToGuideStep(4)
                            else -> jumpToGuideStep(1)
                        }
                        return false
                    }
                    val invalidNewUnit = multiUnitItems.firstOrNull { item ->
                        item.unitId !in ownedUnitIdsOnOpen &&
                            (units.find { it.id == item.unitId }?.isStrictlyAvailableForNewRental() != true)
                    }
                    if (invalidNewUnit != null) {
                        val unitErr = FormValidationResult.invalid(
                            linkedMapOf("units" to ("Unit Freezer Tersedia" to "Unit '${invalidNewUnit.namaUnitSnapshot}' tidak berstatus Tersedia dan tidak dapat disewakan."))
                        )
                        txFormValidationResult = unitErr
                        onPostMessage(unitErr.bannerMessage)
                        jumpToGuideStep(2)
                        return false
                    }
                    txFormValidationResult = null
                    return true
                }
                FormValidationAlertBanner(
                    validationResult = txFormValidationResult,
                    modifier = Modifier.padding(bottom = 6.dp),
                    testTagName = "tx_form_validation_banner"
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = IceWhite),
                    border = BorderStroke(1.dp, SkyBlueSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("guided_tx_stepper_card")
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            guidedSteps.forEach { (stepNum, stepLabel) ->
                                val isActive = currentGuideStep == stepNum
                                val isDone = currentGuideStep > stepNum
                                FilterChip(
                                    selected = isActive,
                                    onClick = { jumpToGuideStep(stepNum) },
                                    modifier = Modifier.testTag("tx_step_chip_$stepNum"),
                                    label = {
                                        Text(
                                            text = if (isDone) "✓ $stepNum. $stepLabel" else "$stepNum. $stepLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { jumpToGuideStep(currentGuideStep - 1) },
                                enabled = currentGuideStep > 1,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_tx_step_prev")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sebelumnya", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                text = "Langkah $currentGuideStep/8: ${guidedSteps.firstOrNull { it.first == currentGuideStep }?.second ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Button(
                                onClick = {
                                    if (currentGuideStep < 8) {
                                        jumpToGuideStep(currentGuideStep + 1)
                                    } else if (validateTransactionBeforeConfirm()) {
                                        showSaveConfirmDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_tx_step_next")
                            ) {
                                Text(if (currentGuideStep < 8) "Lanjut" else "Simpan & PDF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(formScrollState)
                ) {
                    // ==================== BAGIAN 1: DATA PELANGGAN (TERINTEGRASI DATABASE UTAMA) ====================
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "1. DATA PELANGGAN (KTP & KONTAK):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SeaBluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                inlineEditingCustomer = null
                                showInlineCustomerModal = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_inline_new_customer")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Pelanggan Baru", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    SmartCaseOutlinedTextField(
                        value = customerSearchFilter,
                        onValueChange = { customerSearchFilter = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        placeholder = { Text("Cari Pelanggan Terdaftar (Nama, ID CUST/SAF/..., WA, atau NIK)...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val matchingCustomers = remember(customers, customerSearchFilter) {
                        customers.filter { c ->
                            customerSearchFilter.isBlank() ||
                                c.namaLengkap.contains(customerSearchFilter, ignoreCase = true) ||
                                c.kodePelanggan.contains(customerSearchFilter, ignoreCase = true) ||
                                c.nomorHp.contains(customerSearchFilter, ignoreCase = true) ||
                                c.nik.contains(customerSearchFilter, ignoreCase = true)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        matchingCustomers.forEach { c ->
                            val custCode = c.kodePelanggan.ifBlank { "CUST/SAF/${c.id.toString().padStart(4, '0')}" }
                            FilterChip(
                                selected = selectedCustomer?.id == c.id,
                                onClick = {
                                    selectedCustomer = c
                                    namaPenerima = c.namaLengkap
                                    namaPenanggungJawab = c.namaLengkap
                                    nomorWhatsappLokasi = c.nomorHp
                                    namaPengirimBukti = c.namaLengkap
                                    if (c.namaLokasiDefault.isNotBlank()) {
                                        namaLokasiUsaha = c.namaLokasiDefault
                                    } else if (namaLokasiUsaha.isBlank() || namaLokasiUsaha.startsWith("Outlet Usaha")) {
                                        namaLokasiUsaha = "Outlet Usaha ${c.namaLengkap}"
                                    }
                                    if (c.lokasiPenggunaanDefault.isNotBlank() && alamatLokasiGps.isBlank()) {
                                        alamatLokasiGps = c.lokasiPenggunaanDefault
                                    }
                                    if (c.rtRwLokasiDefault.isNotBlank() && rtRwLokasi.isBlank()) {
                                        rtRwLokasi = c.rtRwLokasiDefault
                                    }
                                    if (c.kelurahanLokasiDefault.isNotBlank() && kelurahanDesaLokasi.isBlank()) {
                                        kelurahanDesaLokasi = c.kelurahanLokasiDefault
                                    }
                                    if (c.kecamatanLokasiDefault.isNotBlank() && kecamatanLokasi.isBlank()) {
                                        kecamatanLokasi = c.kecamatanLokasiDefault
                                    }
                                    if (c.kabupatenKotaLokasiDefault.isNotBlank() && kabupatenKotaLokasi.isBlank()) {
                                        kabupatenKotaLokasi = c.kabupatenKotaLokasiDefault
                                    }
                                    if (c.latitudeDefault != null && c.longitudeDefault != null && latitude == null) {
                                        latitude = c.latitudeDefault
                                        longitude = c.longitudeDefault
                                        linkGoogleMaps = "https://maps.google.com/?q=${c.latitudeDefault},${c.longitudeDefault}"
                                    }
                                    if (fotoKtpDepanTransUri.isBlank()) fotoKtpDepanTransUri = c.fotoKtpDepanUri
                                    if (fotoKtpBelakangTransUri.isBlank()) fotoKtpBelakangTransUri = c.fotoKtpBelakangUri
                                },
                                label = { Text("${c.namaLengkap} ($custCode)") }
                            )
                        }
                    }

                    // Summary Card of Selected Customer's KTP Data & Rental History
                    selectedCustomer?.let { cust ->
                        val custCode = cust.kodePelanggan.ifBlank { "CUST/SAF/${cust.id.toString().padStart(4, '0')}" }
                        val custPreviousTx = remember(allTransactions, cust.id) {
                            allTransactions.filter { it.customerId == cust.id }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "ID Pelanggan Tetap: $custCode",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SeaBluePrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            inlineEditingCustomer = cust
                                            showInlineCustomerModal = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("btn_inline_edit_customer")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit Data Pelanggan", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text("Nama Lengkap: ${cust.namaLengkap}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("Nomor KTP (NIK): ${cust.nik}", style = MaterialTheme.typography.bodySmall)
                                Text("Nomor WhatsApp: ${cust.nomorHp}", style = MaterialTheme.typography.bodySmall)
                                Text("Alamat Sesuai KTP: ${cust.alamatLengkap}", style = MaterialTheme.typography.bodySmall)
                                if (cust.lokasiPenggunaanDefault.isNotBlank()) {
                                    Text("Lokasi Penggunaan Tersimpan: ${cust.lokasiPenggunaanDefault}", style = MaterialTheme.typography.bodySmall, color = SeaBluePrimary)
                                }
                                if (custPreviousTx.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Text(
                                        text = "Histori Transaksi Pelanggan Ini (${custPreviousTx.size} Sewa Tercatat):",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SeaBluePrimary
                                    )
                                    custPreviousTx.take(3).forEach { hTx ->
                                        val progBadge = if (hTx.jenisProgram.contains("Hak Milik", ignoreCase = true)) {
                                            "Sewa Hak Milik ${hTx.totalPeriodeHakMilik}x (Ke-${hTx.pembayaranKeHakMilik}/${hTx.totalPeriodeHakMilik})"
                                        } else {
                                            "Sewa Reguler"
                                        }
                                        Text(
                                            text = "• ${hTx.nomorInvoice} [$progBadge]: ${hTx.namaUnitSnapshot} (${hTx.durasiHari} Hari) - ${hTx.statusPenyewaan}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Kolom Nomor WhatsApp Transaksi / PIC Lokasi dengan tombol Import Kontak [👤]
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = namaPenanggungJawab,
                            onValueChange = { namaPenanggungJawab = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Nama Penanggung Jawab") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("input_tx_pic_name")
                        )
                        SmartCaseOutlinedTextField(
                            value = nomorWhatsappLokasi,
                            onValueChange = { nomorWhatsappLokasi = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            label = { Text("Nomor WhatsApp") },
                            placeholder = { Text("08xxxxxxxxxx") },
                            trailingIcon = {
                                WhatsAppContactPickerTrailingButton(
                                    onClick = { launchTxContactPicker() },
                                    modifier = Modifier.testTag("btn_import_wa_contact_tx")
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_tx_whatsapp")
                        )
                    }
                    if (!txContactImportNotice.isNullOrBlank()) {
                        Text(
                            text = txContactImportNotice ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary,
                            modifier = Modifier.testTag("text_contact_import_notice_tx")
                        )
                    }

                    // ==================== BAGIAN 2: DATA UNIT FREEZER & PROGRAM PER UNIT ====================
                    HorizontalDivider()
                    Text(
                        text = "2. DATA UNIT FREEZER YANG DISEWAKAN:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )

                    SmartCaseOutlinedTextField(
                        value = keteranganKondisiFreezer,
                        onValueChange = { keteranganKondisiFreezer = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Kondisi Unit Freezer Saat Diserahkan") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Unified Multi-Unit & Program Selector (100% single source of truth untuk program & unit berstatus Tersedia)
                    MultiUnitRentalSection(
                        unitItems = multiUnitItems,
                        allUnits = units,
                        unitRentalPricesMap = unitRentalPricesMap,
                        sewaHakMilikPeriods = availableHakMilikPeriods,
                        ongkir = ongkir.toLongOrNull() ?: 0L,
                        deposit = deposit.toLongOrNull() ?: 0L,
                        diskon = diskon.toLongOrNull() ?: 0L,
                        isEditMode = initialTransaction != null,
                        ownedUnitIds = ownedUnitIdsOnOpen,
                        reservedByOtherSessionUnitIds = reservedByOtherSessionUnitIds,
                        onResolveFreshUnitPrice = onResolveFreshUnitPrice,
                        onUnitItemsChanged = { updatedList ->
                            multiUnitItems = updatedList
                            val totalRent = updatedList.sumOf { it.hargaSewa.coerceAtLeast(0L) }
                            val totalPaidUnits = updatedList.sumOf { it.nominalPembayaran.coerceAtLeast(0L) }
                            hargaSewa = totalRent.toString()
                            val ok = ongkir.toLongOrNull() ?: 0L
                            val dep = deposit.toLongOrNull() ?: 0L
                            val disc = diskon.toLongOrNull() ?: 0L
                            val newTotalBill = (totalRent + ok + dep - disc).coerceAtLeast(0L)
                            nominalPembayaran = if (totalPaidUnits > 0L) (totalPaidUnits + ok + dep - disc).coerceAtLeast(0L).toString() else newTotalBill.toString()
                            updatedList.firstOrNull()?.let { firstItem ->
                                units.find { it.id == firstItem.unitId }?.let { matched ->
                                    selectedUnit = matched
                                    if (fotoUnitSebelumKirimUri.isBlank() && matched.fotoUnitUri.isNotBlank()) {
                                        fotoUnitSebelumKirimUri = matched.fotoUnitUri
                                    }
                                }
                                jenisProgram = firstItem.jenisProgram
                                durasiHari = firstItem.durasiHari.coerceAtLeast(1).toString()
                                if (firstItem.totalPeriodeHakMilik > 0) totalPeriodeHakMilik = firstItem.totalPeriodeHakMilik
                                if (firstItem.pembayaranKeHakMilik > 0) pembayaranKeHakMilik = firstItem.pembayaranKeHakMilik.toString()
                                if (firstItem.statusProgramHakMilik.isNotBlank()) statusProgramHakMilik = firstItem.statusProgramHakMilik
                                if (firstItem.tanggalMulaiSewa.isNotBlank()) tanggalMulai = firstItem.tanggalMulaiSewa
                                if (firstItem.tanggalAkhirSewa.isNotBlank()) tanggalAkhir = firstItem.tanggalAkhirSewa
                                if (firstItem.tanggalJatuhTempo.isNotBlank()) tanggalJatuhTempo = firstItem.tanggalJatuhTempo
                            }
                        },
                        onValidationMessage = { msg -> onPostMessage(msg) }
                    )

                    // ==================== BAGIAN 3: BIAYA & JATUH TEMPO ====================
                    HorizontalDivider()
                    Text(
                        text = "3. BIAYA & JATUH TEMPO:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark,
                        modifier = Modifier.testTag("section_biaya_jatuh_tempo")
                    )
                    Text(
                        text = "Nomor Transaksi / Invoice: ${initialTransaction?.nomorInvoice ?: "Otomatis saat disimpan (INV/SAF/...)"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SeaBluePrimary,
                        fontWeight = FontWeight.Bold
                    )

                    // Rincian Biaya, Jatuh Tempo & Jadwal Cicilan per Unit (Sumber tunggal dari Daftar Unit di atas)
                    if (multiUnitItems.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_biaya_jatuh_tempo_per_unit")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Rincian Biaya & Jatuh Tempo per Unit (${multiUnitItems.size} Unit)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                multiUnitItems.forEachIndexed { idx, item ->
                                    val isItemHakMilik = item.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
                                    val itemSisa = (item.hargaSewa - item.nominalPembayaran).coerceAtLeast(0L)
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isItemHakMilik) Color(0xFFD8B4FE) else Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${idx + 1}. ${item.namaUnitSnapshot.ifBlank { "Unit #${idx + 1}" }} (${item.kodeUnitSnapshot.ifBlank { "-" }})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NavyDark,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    color = if (isItemHakMilik) Color(0xFFEDE9FE) else Color(0xFFE0F2FE),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (isItemHakMilik) "Sewa Hak Milik" else "Sewa Reguler",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isItemHakMilik) Color(0xFF5B21B6) else NavyDark,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Harga Sewa: ${DateCurrencyUtils.formatRupiah(item.hargaSewa)} • Dibayar: ${DateCurrencyUtils.formatRupiah(item.nominalPembayaran)} • Sisa: ${DateCurrencyUtils.formatRupiah(itemSisa)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF263445),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Mulai: ${item.tanggalMulaiSewa.ifBlank { "-" }} • Jatuh Tempo: ${item.tanggalJatuhTempo.ifBlank { "-" }} (${item.durasiHari} Hari)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextDateAccent,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (isItemHakMilik) {
                                                val totalTarget = item.totalPeriodeHakMilik.coerceAtLeast(1)
                                                val currentKe = item.pembayaranKeHakMilik.coerceIn(1, totalTarget)
                                                val sisaCicilan = (totalTarget - currentKe).coerceAtLeast(0)
                                                Text(
                                                    text = "Jadwal Cicilan Hak Milik: Pembayaran Ke-$currentKe/$totalTarget • Sisa ${sisaCicilan}x • Status: ${item.statusProgramHakMilik}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF5B21B6),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = ongkir,
                            onValueChange = { ongkir = it.filter { c -> c.isDigit() } },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Biaya Pengiriman / Ongkir (Rp)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_ongkir")
                        )
                        SmartCaseOutlinedTextField(
                            value = deposit,
                            onValueChange = { deposit = it.filter { c -> c.isDigit() } },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Deposit (Rp)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = diskon,
                            onValueChange = { diskon = it.filter { c -> c.isDigit() } },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Diskon (Rp)") },
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = tanggalMulai,
                            onValueChange = { newStart ->
                                tanggalMulai = newStart
                                val d = durasiHari.toIntOrNull()?.coerceAtLeast(1) ?: 30
                                val calcEnd = DateCurrencyUtils.addDaysToIsoDate(newStart, d)
                                tanggalAkhir = calcEnd
                                tanggalJatuhTempo = calcEnd
                                if (multiUnitItems.size == 1 && newStart.length == 10) {
                                    multiUnitItems = listOf(
                                        multiUnitItems.first().copy(
                                            tanggalMulaiSewa = newStart,
                                            tanggalAkhirSewa = calcEnd,
                                            tanggalJatuhTempo = calcEnd
                                        )
                                    )
                                }
                            },
                            caseMode = InputTextCaseMode.PRESERVE,
                            label = { Text("Tgl Mulai Utama (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SmartCaseOutlinedTextField(
                        value = tanggalJatuhTempo,
                        onValueChange = { newDue ->
                            tanggalJatuhTempo = newDue
                            tanggalAkhir = newDue
                            if (multiUnitItems.size == 1) {
                                multiUnitItems = listOf(
                                    multiUnitItems.first().copy(
                                        tanggalAkhirSewa = newDue,
                                        tanggalJatuhTempo = newDue
                                    )
                                )
                            }
                        },
                        caseMode = InputTextCaseMode.PRESERVE,
                        label = { Text("Tanggal Jatuh Tempo Utama (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    val parsedNominalPaid = nominalPembayaran.toLongOrNull() ?: 0L
                    val computedSisaPembayaran = (calculatedTotalTagihan - parsedNominalPaid).coerceAtLeast(0L)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SeaBluePrimary.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Total Harga Sewa (${multiUnitItems.size} Unit):", style = MaterialTheme.typography.bodySmall, color = Color(0xFF263445))
                                Text(DateCurrencyUtils.formatRupiah(effectiveUnitRentTotal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = NavyDark)
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Biaya Pengiriman / Ongkir:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF263445))
                                Text(DateCurrencyUtils.formatRupiah(ongkir.toLongOrNull() ?: 0L), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = NavyDark)
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Deposit (+) / Diskon (-):", style = MaterialTheme.typography.bodySmall, color = Color(0xFF263445))
                                Text(
                                    "+${DateCurrencyUtils.formatRupiah(deposit.toLongOrNull() ?: 0L)} / -${DateCurrencyUtils.formatRupiah(diskon.toLongOrNull() ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NavyDark
                                )
                            }
                            HorizontalDivider()
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("TOTAL BIAYA / TAGIHAN:", fontWeight = FontWeight.Bold, color = NavyDark)
                                Text(
                                    text = DateCurrencyUtils.formatRupiah(calculatedTotalTagihan),
                                    fontWeight = FontWeight.Bold,
                                    color = SeaBluePrimary
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sisa Pembayaran:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF263445))
                                Text(
                                    text = DateCurrencyUtils.formatRupiah(computedSisaPembayaran),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (computedSisaPembayaran > 0L) StatusOverdueRed else StatusPaidGreen
                                )
                            }
                        }
                    }

                    SmartCaseOutlinedTextField(
                        value = nominalPembayaran,
                        onValueChange = { nominalPembayaran = it.filter { c -> c.isDigit() } },
                        caseMode = InputTextCaseMode.PRESERVE,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Nominal Pembayaran Diterima (Rp)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Status Pembayaran:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        STATUS_PEMBAYARAN_OPTIONS.forEach { st ->
                            FilterChip(
                                selected = statusPembayaran == st,
                                onClick = { statusPembayaran = st },
                                label = { Text(st) }
                            )
                        }
                    }

                    Text("Status Penyewaan:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        STATUS_PENYEWAAN_OPTIONS.forEach { st ->
                            FilterChip(
                                selected = statusPenyewaan == st,
                                onClick = { statusPenyewaan = st },
                                label = { Text(st) }
                            )
                        }
                    }

                    // ==================== SECTION 2: INTEGRASI METODE PEMBAYARAN ====================
                    Text(
                        text = "Metode Pembayaran (Terintegrasi Setting → Metode Pembayaran):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        methodOptionNames.forEach { mp ->
                            FilterChip(
                                selected = metodePembayaran == mp,
                                onClick = { metodePembayaran = mp },
                                label = { Text(mp) },
                                modifier = Modifier.testTag("chip_metode_${mp.replace(" ", "_").lowercase()}")
                            )
                        }
                    }

                    // Dynamic Payment Method Details (QRIS image / Bank Account / Cash)
                    SelectedPaymentMethodDetailCard(
                        selectedMethodName = metodePembayaran,
                        paymentMethods = paymentMethods
                    )

                    // ==================== SECTION 1: UPLOAD BUKTI PEMBAYARAN PADA MENU TRANSAKSI ====================
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_payment_proof_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = StatusPaidGreen)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "UPLOAD BUKTI PEMBAYARAN (JPG, JPEG, PNG)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = NavyDark
                                        )
                                        Text(
                                            text = "Preview sebelum disimpan • Bisa diganti/dihapus • Mendukung pembayaran bertahap",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF065F46)
                                        )
                                    }
                                }
                                if (initialTransaction != null) {
                                    OutlinedButton(
                                        onClick = { showAddStagedProofDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Tahap Baru", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            if (fotoBuktiPembayaranUri.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = File(fotoBuktiPembayaranUri).takeIf { it.exists() } ?: fotoBuktiPembayaranUri,
                                        contentDescription = "Preview Bukti Pembayaran",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Waktu Upload: $waktuUploadBukti",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF065F46)
                                    )
                                    OutlinedButton(
                                        onClick = { fotoBuktiPembayaranUri = "" },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = StatusOverdueRed, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hapus Foto Bukti", color = StatusOverdueRed, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                        if (hasCam) showProofCameraDialog = true else proofCameraPermLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_form_camera_bukti_bayar")
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (fotoBuktiPembayaranUri.isBlank()) "Kamera Bukti" else "Ganti Kamera", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        proofGalleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_form_gallery_bukti_bayar")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (fotoBuktiPembayaranUri.isBlank()) "Upload Galeri" else "Ganti Galeri", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmartCaseOutlinedTextField(
                                    value = namaPengirimBukti,
                                    onValueChange = { namaPengirimBukti = it },
                                    caseMode = InputTextCaseMode.UPPERCASE,
                                    label = { Text("Nama Pengirim Bukti") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                SmartCaseOutlinedTextField(
                                    value = catatanBuktiPembayaran,
                                    onValueChange = { catatanBuktiPembayaran = it },
                                    caseMode = InputTextCaseMode.UPPERCASE,
                                    label = { Text("Catatan Pembayaran") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (existingPaymentProofs.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = "Riwayat Bukti Pembayaran Bertahap Tersimpan (${existingPaymentProofs.size} Bukti):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                existingPaymentProofs.forEachIndexed { idx, p ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "• Tahap #${existingPaymentProofs.size - idx}: ${DateCurrencyUtils.formatRupiah(p.nominalPembayaran)} (${p.metodePembayaran} - ${p.tanggalPembayaran})",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        IconButton(
                                            onClick = { onDeletePaymentProof(p) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = StatusOverdueRed, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==================== BAGIAN 4: LOKASI PENGGUNAAN FREEZER (TERPISAH DARI KTP) ====================
                    HorizontalDivider()
                    Text("4. LOKASI PENGGUNAAN FREEZER & TAGGING GOOGLE MAPS:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SeaBluePrimary)
                    Text(
                        text = "A. TAGGING MANUAL (GESER PETA/PIN WALAUPUN GPS MATI) & INTEGRASI GOOGLE MAPS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )

                    Button(
                        onClick = {
                            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (fineGranted || coarseGranted) {
                                onCaptureGpsLocation { res ->
                                    if (res.isSuccess) {
                                        latitude = res.latitude
                                        longitude = res.longitude
                                        akurasiGpsMeter = res.accuracyMeters
                                        alamatLokasiGps = res.addressText
                                        if (res.kabupatenKota.isNotBlank()) kabupatenKotaLokasi = res.kabupatenKota
                                        if (res.kecamatan.isNotBlank()) kecamatanLokasi = res.kecamatan
                                        if (res.kelurahanDesa.isNotBlank()) kelurahanDesaLokasi = res.kelurahanDesa
                                        linkGoogleMaps = res.googleMapsLink
                                        tanggalLokasiGps = res.dateIso
                                        jamLokasiGps = res.timeStr
                                    } else {
                                        gpsErrorDialogMessage = res.errorMessage
                                    }
                                }
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_ambil_lokasi_sekarang")
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📍 AMBIL LOKASI GPS SAAT INI", fontWeight = FontWeight.Bold)
                    }

                    GpsLocationMapCard(
                        latitude = latitude,
                        longitude = longitude,
                        addressText = alamatLokasiGps,
                        dateStr = tanggalLokasiGps,
                        timeStr = jamLokasiGps,
                        accuracyMeters = akurasiGpsMeter,
                        onPinMoved = { newLat, newLon ->
                            latitude = newLat
                            longitude = newLon
                            akurasiGpsMeter = 2.0f
                            linkGoogleMaps = LocationGpsHelper.formatGoogleMapsUrl(newLat, newLon)
                            tanggalLokasiGps = DateCurrencyUtils.todayIso()
                            jamLokasiGps = DateCurrencyUtils.nowTime()
                            scope.launch {
                                val detailed = LocationGpsHelper.reverseGeocodeStructured(context, newLat, newLon)
                                if (detailed.fullAddress.isNotBlank() && alamatLokasiGps.isBlank()) {
                                    alamatLokasiGps = detailed.fullAddress
                                }
                                if (detailed.kelurahanDesa.isNotBlank()) kelurahanDesaLokasi = detailed.kelurahanDesa
                                if (detailed.kecamatan.isNotBlank()) kecamatanLokasi = detailed.kecamatan
                                if (detailed.kabupatenKota.isNotBlank()) kabupatenKotaLokasi = detailed.kabupatenKota
                            }
                        },
                        onAddressResolved = { resolvedAddr, kel, kec, kab ->
                            if (resolvedAddr.isNotBlank()) alamatLokasiGps = resolvedAddr
                            if (kel.isNotBlank()) kelurahanDesaLokasi = kel
                            if (kec.isNotBlank()) kecamatanLokasi = kec
                            if (kab.isNotBlank()) kabupatenKotaLokasi = kab
                        },
                        onSaveLocation = { savedLat, savedLon, savedAddr ->
                            latitude = savedLat
                            longitude = savedLon
                            linkGoogleMaps = LocationGpsHelper.formatGoogleMapsUrl(savedLat, savedLon)
                            tanggalLokasiGps = DateCurrencyUtils.todayIso()
                            jamLokasiGps = DateCurrencyUtils.nowTime()
                            if (savedAddr.isNotBlank() && alamatLokasiGps.isBlank()) {
                                alamatLokasiGps = savedAddr
                            }
                        }
                    )

                    Text(
                        text = "B. DATA LENGKAP LOKASI PENGGUNAAN FREEZER (Terpisah dari Alamat KTP)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = namaLokasiUsaha,
                            onValueChange = { namaLokasiUsaha = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Nama Lokasi (Outlet / Toko / Rumah)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                        SmartCaseOutlinedTextField(
                            value = rtRwLokasi,
                            onValueChange = { rtRwLokasi = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("RT / RW Lokasi") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    SmartCaseOutlinedTextField(
                        value = alamatLokasiGps,
                        onValueChange = { alamatLokasiGps = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Alamat Lengkap Penggunaan Freezer (Otomatis dari GPS / Edit Manual)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = kelurahanDesaLokasi,
                            onValueChange = { kelurahanDesaLokasi = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Kelurahan / Desa") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = kecamatanLokasi,
                            onValueChange = { kecamatanLokasi = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Kecamatan") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = kabupatenKotaLokasi,
                            onValueChange = { kabupatenKotaLokasi = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Kota / Kabupaten") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SmartCaseOutlinedTextField(
                        value = linkGoogleMaps,
                        onValueChange = { linkGoogleMaps = it },
                        caseMode = InputTextCaseMode.PRESERVE,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        label = { Text("Link Peta (OpenStreetMap / GPS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ==================== BAGIAN 5: DOKUMENTASI FOTO TRANSAKSI (RINGKAS 2 SLOT PER UNIT) ====================
                    HorizontalDivider()
                    MultiUnitPhotoDocumentationSection(
                        transactionFolder = txFolder,
                        unitItems = multiUnitItems,
                        onUnitItemsChanged = { updatedUnitsWithPhotos ->
                            multiUnitItems = updatedUnitsWithPhotos
                            updatedUnitsWithPhotos.firstOrNull()?.let { first ->
                                if (first.fotoKondisiDepanUri.isNotBlank()) fotoUnitSebelumKirimUri = first.fotoKondisiDepanUri
                                if (first.fotoSerahTerimaPelangganUri.isNotBlank()) fotoSerahTerimaUri = first.fotoSerahTerimaPelangganUri
                            }
                        }
                    )

                    // ==================== BAGIAN 5B: DOKUMENTASI FREEZER DI LOKASI & TAMPAK DEPAN LOKASI ====================
                    val txIdForPhotos = initialTransaction?.id?.takeIf { it > 0L }
                    val persistedLocPhotos = remember(customerPhotos, txIdForPhotos, selectedCustomer?.id) {
                        if (txIdForPhotos != null) {
                            customerPhotos.filter { it.transactionId == txIdForPhotos }
                        } else emptyList()
                    }
                    val freezerLocItems = remember(persistedLocPhotos, stagedLocationPhotos, drivePhotoMetadataList) {
                        val persisted = persistedLocPhotos
                            .filter { it.namaDokumentasi.contains("Freezer", true) || it.kategori == CustomerPhotoStorageHelper.KAT_FOTO_UNIT }
                            .map { p ->
                                LocationDocPhotoDisplayItem(
                                    id = p.id.toString(),
                                    docSlot = LocationDocSlots.SLOT_FREEZER_DI_LOKASI,
                                    localPath = p.filePath,
                                    capturedAtIso = p.tanggalWaktuIso,
                                    latitude = p.latitude,
                                    longitude = p.longitude,
                                    uploadStatus = p.uploadStatus.ifBlank { "PENDING" },
                                    persistedEntity = p
                                )
                            }
                        val staged = stagedLocationPhotos
                            .filter { it.slotCode == LocationDocSlots.SLOT_FREEZER_DI_LOKASI }
                            .map { s ->
                                LocationDocPhotoDisplayItem(
                                    id = s.id.toString(),
                                    docSlot = s.slotCode,
                                    localPath = s.previewModel.toString(),
                                    capturedAtIso = s.capturedAt,
                                    latitude = s.latitude,
                                    longitude = s.longitude,
                                    uploadStatus = "PENDING",
                                    persistedEntity = null
                                )
                            }
                        persisted + staged
                    }
                    val frontLocItems = remember(persistedLocPhotos, stagedLocationPhotos, drivePhotoMetadataList) {
                        val persisted = persistedLocPhotos
                            .filter { it.namaDokumentasi.contains("Tampak Depan", true) || (it.namaDokumentasi.contains("Lokasi", true) && !it.namaDokumentasi.contains("Freezer", true)) }
                            .map { p ->
                                LocationDocPhotoDisplayItem(
                                    id = p.id.toString(),
                                    docSlot = LocationDocSlots.SLOT_TAMPAK_DEPAN_LOKASI,
                                    localPath = p.filePath,
                                    capturedAtIso = p.tanggalWaktuIso,
                                    latitude = p.latitude,
                                    longitude = p.longitude,
                                    uploadStatus = p.uploadStatus.ifBlank { "PENDING" },
                                    persistedEntity = p
                                )
                            }
                        val staged = stagedLocationPhotos
                            .filter { it.slotCode == LocationDocSlots.SLOT_TAMPAK_DEPAN_LOKASI }
                            .map { s ->
                                LocationDocPhotoDisplayItem(
                                    id = s.id.toString(),
                                    docSlot = s.slotCode,
                                    localPath = s.previewModel.toString(),
                                    capturedAtIso = s.capturedAt,
                                    latitude = s.latitude,
                                    longitude = s.longitude,
                                    uploadStatus = "PENDING",
                                    persistedEntity = null
                                )
                            }
                        persisted + staged
                    }

                    LocationFreezerDocumentationCard(
                        freezerLocationPhotos = freezerLocItems,
                        frontLocationPhotos = frontLocItems,
                        drivePhotoMetadataList = drivePhotoMetadataList,
                        customerLabel = selectedCustomer?.namaLengkap.orEmpty(),
                        unitLabel = multiUnitItems.firstOrNull()?.namaUnitSnapshot ?: selectedUnit?.namaUnit.orEmpty(),
                        currentLatitude = latitude,
                        currentLongitude = longitude,
                        isUploading = isUploadingPhoto,
                        onAddPhotoFromSource = { docSlot, src ->
                            val nowStr = DateCurrencyUtils.nowDateTimeDisplay()
                            if (docSlot == LocationDocSlots.SLOT_FREEZER_DI_LOKASI) {
                                if (fotoFreezerDiLokasiUri.isBlank()) {
                                    fotoFreezerDiLokasiUri = src.toString()
                                    waktuFotoFreezerDiLokasi = nowStr
                                }
                            } else {
                                if (fotoDepanLokasiUri.isBlank()) {
                                    fotoDepanLokasiUri = src.toString()
                                    waktuFotoDepanLokasi = nowStr
                                }
                            }
                            val cust = selectedCustomer
                            val primaryUnitId = multiUnitItems.firstOrNull()?.unitId ?: selectedUnit?.id
                            if (txIdForPhotos != null && cust != null) {
                                onSaveLocationPhoto(
                                    cust,
                                    txIdForPhotos,
                                    primaryUnitId,
                                    docSlot,
                                    src,
                                    latitude,
                                    longitude
                                )
                            } else {
                                stagedLocationPhotos = stagedLocationPhotos + StagedLocationDocPhoto(
                                    id = -System.nanoTime(),
                                    slotCode = docSlot,
                                    source = src,
                                    previewModel = src,
                                    capturedAt = nowStr,
                                    latitude = latitude,
                                    longitude = longitude
                                )
                            }
                        },
                        onDeletePhoto = { item ->
                            if (item.persistedEntity != null) {
                                onDeleteCustomerPhoto(item.persistedEntity)
                            } else {
                                stagedLocationPhotos = stagedLocationPhotos.filterNot { it.id.toString() == item.id }
                            }
                        },
                        onRetryUpload = { item ->
                            item.persistedEntity?.let { onRetryLocationPhotoUpload(it) }
                        }
                    )

                    // ==================== BAGIAN 6: SERAH TERIMA & TANDA TANGAN DIGITAL ====================
                    HorizontalDivider()
                    Text("6. SERAH TERIMA & TANDA TANGAN DIGITAL PELANGGAN & PETUGAS:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SeaBluePrimary)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = namaPenerima,
                            onValueChange = { namaPenerima = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Nama Penerima / Pelanggan") },
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = namaPetugas,
                            onValueChange = { if (!isOfficerSnapshotLocked) namaPetugas = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            enabled = !isOfficerSnapshotLocked,
                            label = { Text(if (isOfficerSnapshotLocked) "Petugas SAF (Terkunci)" else "Nama Petugas SAF (Auto Login)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_nama_petugas_form")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = tanggalPenerimaan,
                            onValueChange = { tanggalPenerimaan = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            label = { Text("Tanggal Serah Terima") },
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = jamPenerimaan,
                            onValueChange = { jamPenerimaan = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            label = { Text("Waktu / Jam Serah Terima") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    DigitalSignaturePad(
                        title = "Tanda Tangan Digital Pelanggan (Layar HP)",
                        signerName = namaPenerima,
                        transactionFolder = txFolder,
                        filePrefix = "pelanggan",
                        existingSignaturePath = tandaTanganPelangganUri,
                        onSignatureSaved = { tandaTanganPelangganUri = it }
                    )

                    if (isOfficerSnapshotLocked) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_form_officer_locked"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = StatusPaidGreen, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Tanda Tangan Petugas Terkunci Permanen",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                }
                                Text(
                                    text = "Petugas: $namaPetugas • Snapshot: ${officerSignedAt.ifBlank { "-" }}. Tidak akan berubah saat transaksi dibuka/diedit petugas lain.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (tandaTanganPetugasUri.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(74.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = File(tandaTanganPetugasUri).takeIf { it.exists() } ?: tandaTanganPetugasUri,
                                            contentDescription = "Tanda Tangan Petugas Terkunci",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                if (initialTransaction != null && initialTransaction.id > 0L) {
                                    OutlinedButton(
                                        onClick = { showFormOfficerCorrectionDialog = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_form_correct_officer")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Koreksi Identitas Petugas (Audit Log)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        DigitalSignaturePad(
                            title = "Tanda Tangan Digital Petugas SAF (Terkunci Permanen Setelah Simpan)",
                            signerName = namaPetugas,
                            transactionFolder = txFolder,
                            filePrefix = "petugas",
                            existingSignaturePath = tandaTanganPetugasUri,
                            onSignatureSaved = {
                                tandaTanganPetugasUri = it
                                officerSignedAt = DateCurrencyUtils.nowDateTimeDisplay()
                            }
                        )
                    }

                    SmartCaseOutlinedTextField(
                        value = catatan,
                        onValueChange = { catatan = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Catatan Tambahan Transaksi") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (validateTransactionBeforeConfirm()) {
                            showSaveConfirmDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_simpan_serah_terima")
                ) {
                    Text("SIMPAN SERAH TERIMA & BUAT INVOICE PDF (${multiUnitItems.size} UNIT)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showSaveConfirmDialog) {
        val totalUnitsCount = multiUnitItems.size.coerceAtLeast(1)
        val totalPaidNum = nominalPembayaran.toLongOrNull() ?: 0L
        val remainingBill = (calculatedTotalTagihan - totalPaidNum).coerceAtLeast(0L)
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            title = { Text("Konfirmasi Simpan Transaksi ($totalUnitsCount Unit)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pelanggan: ${selectedCustomer?.namaLengkap ?: namaPenerima}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("Jumlah Unit: $totalUnitsCount Unit Freezer", style = MaterialTheme.typography.bodySmall)
                    Text("Total Tagihan: ${DateCurrencyUtils.formatRupiah(calculatedTotalTagihan)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SeaBluePrimary)
                    Text("Nominal Pembayaran: ${DateCurrencyUtils.formatRupiah(totalPaidNum)}", style = MaterialTheme.typography.bodySmall)
                    Text("Sisa Tagihan: ${DateCurrencyUtils.formatRupiah(remainingBill)}", style = MaterialTheme.typography.bodySmall, color = if (remainingBill > 0L) StatusOverdueRed else StatusPaidGreen, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pastikan data unit, jadwal jatuh tempo per unit, dokumentasi foto, dan tanda tangan sudah benar. Simpan transaksi & generate Invoice PDF?", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveConfirmDialog = false
                        val builtTx = buildTransactionEntity()
                        val synchronizedUnits = multiUnitItems.mapIndexed { idx, item ->
                            val matchedMasterUnit = units.find { it.id == item.unitId }
                            val isSingle = multiUnitItems.size == 1
                            item.copy(
                                transactionId = builtTx.id,
                                customerId = builtTx.customerId,
                                unitIndexNumber = idx + 1,
                                kodeUnitSnapshot = item.kodeUnitSnapshot.ifBlank { matchedMasterUnit?.kodeUnit ?: builtTx.kodeUnitSnapshot },
                                namaUnitSnapshot = item.namaUnitSnapshot.ifBlank { matchedMasterUnit?.namaUnit ?: builtTx.namaUnitSnapshot },
                                jenisUnitSnapshot = item.jenisUnitSnapshot.ifBlank { matchedMasterUnit?.jenisUnit.orEmpty() },
                                merekSnapshot = item.merekSnapshot.ifBlank { matchedMasterUnit?.merek.orEmpty() },
                                kapasitasLiterSnapshot = if (item.kapasitasLiterSnapshot > 0) item.kapasitasLiterSnapshot else (matchedMasterUnit?.kapasitasLiter ?: 0),
                                statusPenyewaan = builtTx.statusPenyewaan,
                                statusPembayaran = if (isSingle) builtTx.statusPembayaran else item.statusPembayaran,
                                fotoKondisiDepanUri = if (isSingle) item.fotoKondisiDepanUri.ifBlank { builtTx.fotoUnitSebelumKirimUri } else item.fotoKondisiDepanUri,
                                fotoKondisiSampingUri = if (isSingle) item.fotoKondisiSampingUri.ifBlank { builtTx.fotoKondisiFisikTransUri } else item.fotoKondisiSampingUri,
                                fotoKondisiDalamUri = if (isSingle) item.fotoKondisiDalamUri.ifBlank { builtTx.fotoBagianDalamTransUri } else item.fotoKondisiDalamUri,
                                fotoSerahTerimaPelangganUri = if (isSingle) item.fotoSerahTerimaPelangganUri.ifBlank { builtTx.fotoSerahTerimaUri } else item.fotoSerahTerimaPelangganUri,
                                fotoSerahTerimaLokasiUri = if (isSingle) item.fotoSerahTerimaLokasiUri.ifBlank { builtTx.fotoFreezerDiLokasiUri } else item.fotoSerahTerimaLokasiUri,
                                fotoBuktiPenerimaanUri = if (isSingle) item.fotoBuktiPenerimaanUri.ifBlank { builtTx.fotoPelangganBersamaFreezerUri } else item.fotoBuktiPenerimaanUri
                            )
                        }
                        val initialProofIfNew = if (fotoBuktiPembayaranUri.isNotBlank() && existingPaymentProofs.none { it.fotoBuktiUri == fotoBuktiPembayaranUri }) {
                            PaymentProofEntity(
                                transactionId = builtTx.id,
                                tanggalPembayaran = tanggalPemesanan,
                                nominalPembayaran = nominalPembayaran.toLongOrNull() ?: calculatedTotalTagihan,
                                metodePembayaran = metodePembayaran,
                                namaPengirim = namaPengirimBukti.ifBlank { builtTx.namaPelangganSnapshot },
                                catatanPembayaran = catatanBuktiPembayaran,
                                fotoBuktiUri = fotoBuktiPembayaranUri,
                                waktuUpload = waktuUploadBukti
                            )
                        } else null
                        if (activeDraftUuid.isNotBlank()) {
                            onDeleteDraft(activeDraftUuid)
                        }
                        UnitReservationManager.releaseSession(formSessionId)
                        onSave(builtTx, synchronizedUnits, initialProofIfNew, stagedLocationPhotos)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                    modifier = Modifier.testTag("btn_confirm_save_tx")
                ) {
                    Text("Ya, Simpan & Buat PDF", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSaveConfirmDialog = false }) {
                    Text("Periksa Lagi")
                }
            }
        )
    }

    if (showFormOfficerCorrectionDialog && initialTransaction != null && initialTransaction.id > 0L) {
        OfficerCorrectionAuditDialog(
            transactionId = initialTransaction.id,
            invoiceNumber = initialTransaction.nomorInvoice,
            currentOfficerName = namaPetugas,
            currentSignatureUri = tandaTanganPetugasUri,
            onDismiss = { showFormOfficerCorrectionDialog = false },
            onConfirm = { newName, newSigUri, reason ->
                onCorrectOfficerWithAuditLog(initialTransaction.id, newName, newSigUri, reason)
                namaPetugas = newName
                tandaTanganPetugasUri = newSigUri
                officerSignedAt = DateCurrencyUtils.nowDateTimeDisplay()
                showFormOfficerCorrectionDialog = false
            }
        )
    }

    if (showProofCameraDialog) {
        CameraXCaptureDialog(
            title = "Foto Bukti Pembayaran",
            folderName = "payment_proofs",
            filePrefix = "BUKTI_FORM",
            onDismiss = { showProofCameraDialog = false },
            onPhotoCaptured = { file ->
                fotoBuktiPembayaranUri = file.absolutePath
                waktuUploadBukti = DateCurrencyUtils.nowDateTimeDisplay()
                showProofCameraDialog = false
            }
        )
    }

    if (showAddStagedProofDialog && initialTransaction != null) {
        PaymentProofUploadDialog(
            transactionId = initialTransaction.id,
            invoiceNumber = initialTransaction.nomorInvoice,
            defaultAmount = (calculatedTotalTagihan - (nominalPembayaran.toLongOrNull() ?: 0L)).coerceAtLeast(100000L),
            defaultMethod = metodePembayaran,
            defaultSender = selectedCustomer?.namaLengkap ?: "",
            paymentMethods = paymentMethods,
            onDismiss = { showAddStagedProofDialog = false },
            onSave = { proof ->
                onSavePaymentProof(proof)
                showAddStagedProofDialog = false
            }
        )
    }

    if (gpsErrorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { gpsErrorDialogMessage = null },
            title = { Text("Pemberitahuan GPS Smartphone") },
            text = { Text(gpsErrorDialogMessage!!) },
            confirmButton = {
                Button(onClick = { gpsErrorDialogMessage = null }) {
                    Text("Mengerti")
                }
            }
        )
    }

    if (showInlineCustomerModal) {
        CustomerFormDialog(
            initialCustomer = inlineEditingCustomer,
            existingCustomers = customers,
            onDismiss = { showInlineCustomerModal = false },
            onSwitchToExistingCustomer = { existingCust ->
                selectedCustomer = existingCust
                namaPenerima = existingCust.namaLengkap
                namaPenanggungJawab = existingCust.namaLengkap
                nomorWhatsappLokasi = existingCust.nomorHp
                namaPengirimBukti = existingCust.namaLengkap
                if (existingCust.lokasiPenggunaanDefault.isNotBlank() && alamatLokasiGps.isBlank()) {
                    alamatLokasiGps = existingCust.lokasiPenggunaanDefault
                }
                if (existingCust.latitudeDefault != null && existingCust.longitudeDefault != null && latitude == null) {
                    latitude = existingCust.latitudeDefault
                    longitude = existingCust.longitudeDefault
                    linkGoogleMaps = "https://maps.google.com/?q=${existingCust.latitudeDefault},${existingCust.longitudeDefault}"
                }
                if (existingCust.fotoKtpDepanUri.isNotBlank()) fotoKtpDepanTransUri = existingCust.fotoKtpDepanUri
                if (existingCust.fotoKtpBelakangUri.isNotBlank()) fotoKtpBelakangTransUri = existingCust.fotoKtpBelakangUri
                showInlineCustomerModal = false
            },
            onSave = { custToSave ->
                onSaveCustomerInline(custToSave) { savedCust ->
                    selectedCustomer = savedCust
                    namaPenerima = savedCust.namaLengkap
                    namaPenanggungJawab = savedCust.namaLengkap
                    nomorWhatsappLokasi = savedCust.nomorHp
                    namaPengirimBukti = savedCust.namaLengkap
                    if (savedCust.namaLokasiDefault.isNotBlank()) {
                        namaLokasiUsaha = savedCust.namaLokasiDefault
                    } else if (namaLokasiUsaha.isBlank() || namaLokasiUsaha.startsWith("Outlet Usaha")) {
                        namaLokasiUsaha = "Outlet Usaha ${savedCust.namaLengkap}"
                    }
                    if (savedCust.lokasiPenggunaanDefault.isNotBlank()) {
                        alamatLokasiGps = savedCust.lokasiPenggunaanDefault
                    }
                    if (savedCust.rtRwLokasiDefault.isNotBlank()) rtRwLokasi = savedCust.rtRwLokasiDefault
                    if (savedCust.kelurahanLokasiDefault.isNotBlank()) kelurahanDesaLokasi = savedCust.kelurahanLokasiDefault
                    if (savedCust.kecamatanLokasiDefault.isNotBlank()) kecamatanLokasi = savedCust.kecamatanLokasiDefault
                    if (savedCust.kabupatenKotaLokasiDefault.isNotBlank()) kabupatenKotaLokasi = savedCust.kabupatenKotaLokasiDefault
                    if (savedCust.latitudeDefault != null && savedCust.longitudeDefault != null) {
                        latitude = savedCust.latitudeDefault
                        longitude = savedCust.longitudeDefault
                        linkGoogleMaps = "https://maps.google.com/?q=${savedCust.latitudeDefault},${savedCust.longitudeDefault}"
                    }
                    if (savedCust.fotoKtpDepanUri.isNotBlank()) fotoKtpDepanTransUri = savedCust.fotoKtpDepanUri
                    if (savedCust.fotoKtpBelakangUri.isNotBlank()) fotoKtpBelakangTransUri = savedCust.fotoKtpBelakangUri
                    showInlineCustomerModal = false
                }
            },
            onDelete = {
                showInlineCustomerModal = false
            }
        )
    }
}

/**
 * Extend Rental Dialog in DAYS (Perpanjangan Sewa dalam Hari + Ongkir Tambahan opsional)
 */
@Composable
private fun ExtendRentalByDaysDialog(
    transaction: RentalTransactionEntity,
    onDismiss: () -> Unit,
    onConfirmExtend: (Int, Long, Long, Long, String) -> Unit
) {
    var tambahanHari by remember { mutableStateOf("30") }
    var biayaSewaPerpanjangan by remember { mutableStateOf(transaction.hargaSewa.toString()) }
    var ongkirTambahan by remember { mutableStateOf("0") }
    var validationResult by remember { mutableStateOf<FormValidationResult?>(null) }

    val parsedDays = tambahanHari.toIntOrNull() ?: 0
    val d = parsedDays.coerceAtLeast(1)
    val parsedHarga = biayaSewaPerpanjangan.toLongOrNull() ?: 0L
    val ok = ongkirTambahan.toLongOrNull() ?: 0L
    val totalTagihanBaru = parsedHarga + ok
    var dibayarSekarang by remember(totalTagihanBaru) { mutableStateOf(totalTagihanBaru.toString()) }
    var catatan by remember { mutableStateOf("Perpanjangan sewa $d hari") }

    val tglAkhirBaru = DateCurrencyUtils.addDaysToIsoDate(transaction.tanggalAkhirSewa, d)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perpanjangan Sewa Freezer (Hari)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                FormValidationAlertBanner(
                    validationResult = validationResult,
                    testTagName = "extend_rental_validation_banner"
                )
                Text(
                    text = "Transaksi Lama (${transaction.nomorInvoice}) tidak akan dihapus. Sistem akan membuat riwayat perpanjangan & invoice tagihan baru.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Pelanggan: ${transaction.namaPelangganSnapshot}", style = MaterialTheme.typography.labelLarge)
                Text("Unit: ${transaction.namaUnitSnapshot}", style = MaterialTheme.typography.bodySmall)
                Text("Akhir Sewa Lama: ${DateCurrencyUtils.formatDisplayDate(transaction.tanggalAkhirSewa)}", style = MaterialTheme.typography.bodySmall, color = TextDateAccent)
                Text("Akhir Sewa Baru (+$d Hari): ${DateCurrencyUtils.formatDisplayDate(tglAkhirBaru)}", style = MaterialTheme.typography.bodySmall, color = TextDateAccent, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = tambahanHari,
                    onValueChange = {
                        tambahanHari = it.filter { c -> c.isDigit() }
                        if (validationResult != null) validationResult = null
                    },
                    label = { Text("Tambahan Durasi (Hari) *") },
                    isError = validationResult?.fieldErrors?.containsKey("durasi") == true,
                    supportingText = { FieldErrorSupportingText(validationResult?.fieldErrors?.get("durasi")) },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = biayaSewaPerpanjangan,
                    onValueChange = {
                        biayaSewaPerpanjangan = it.filter { c -> c.isDigit() }
                        if (validationResult != null) validationResult = null
                    },
                    label = { Text("Biaya Sewa Perpanjangan (Rp) *") },
                    isError = validationResult?.fieldErrors?.containsKey("hargaSewa") == true,
                    supportingText = { FieldErrorSupportingText(validationResult?.fieldErrors?.get("hargaSewa")) },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = ongkirTambahan,
                    onValueChange = { ongkirTambahan = it.filter { c -> c.isDigit() } },
                    label = { Text("Ongkir Tambahan (Rp)") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors()
                )
                Text(
                    text = "Tagihan Baru: ${DateCurrencyUtils.formatRupiah(totalTagihanBaru)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SeaBluePrimary
                )
                OutlinedTextField(
                    value = dibayarSekarang,
                    onValueChange = { dibayarSekarang = it.filter { c -> c.isDigit() } },
                    label = { Text("Nominal Dibayar Sekarang (Rp)") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors()
                )
                SmartCaseOutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Catatan Perpanjangan") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = FormValidationEngine.validateRenewalForm(
                        customerId = transaction.customerId.takeIf { it > 0L } ?: 1L,
                        renewedUnitCount = 1,
                        addedNewUnitCount = 0,
                        metodePembayaran = transaction.metodePembayaran.ifBlank { "Transfer Bank" },
                        anyInvalidDuration = parsedDays <= 0,
                        anyNegativeOrZeroPrice = parsedHarga <= 0L
                    )
                    if (!res.isValid) {
                        validationResult = res
                        return@Button
                    }
                    validationResult = null
                    onConfirmExtend(
                        parsedDays,
                        parsedHarga,
                        ok,
                        dibayarSekarang.toLongOrNull() ?: 0L,
                        catatan
                    )
                },
                modifier = Modifier.testTag("btn_confirm_extend_rental")
            ) {
                Text("Simpan Perpanjangan")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun InAppPdfViewerDialog(
    pdfFile: File,
    onDismiss: () -> Unit
) {
    val pageBitmaps = remember { mutableStateListOf<Bitmap>() }

    LaunchedEffect(pdfFile) {
        withContext(Dispatchers.IO) {
            try {
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
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0A2540)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Pratinjau Dokumen PDF Resmi (A4)",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pdfFile.name} • ${pageBitmaps.size} Halaman",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup PDF", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(pageBitmaps) { bmp ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Halaman Invoice PDF",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}
