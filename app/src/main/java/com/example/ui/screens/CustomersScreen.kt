package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.contacts.PhoneNumberNormalizer
import com.example.contacts.WhatsAppContactPickerTrailingButton
import com.example.contacts.rememberWhatsAppContactImporter
import com.example.ui.components.InputTextCaseMode
import com.example.ui.components.SmartCaseOutlinedTextField
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerPhotoEntity
import com.example.data.local.CustomerTimelineEventEntity
import com.example.data.local.FormDraftEntity
import com.example.data.local.FreezerReturnEntity
import com.example.ui.components.DraftAutoSaveStatusPill
import com.example.ui.components.FormDraftCodec
import com.example.ui.components.FormDraftTypes
import com.example.ui.components.ResumeDraftBanner
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.example.data.local.PaymentMethodEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitPaymentAllocationEntity
import com.example.data.repository.FreezerRepository
import com.example.location.LocationGpsHelper
import com.example.media.PhotoCategory
import com.example.media.SmartImageProcessor
import com.example.ocr.CustomerRegistrationFormState
import com.example.ocr.KtpCustomerStorageManager
import com.example.ocr.KtpGeminiDiagnosticReport
import com.example.ocr.KtpGeminiDiagnosticWrapper
import com.example.ocr.KtpGeminiParser
import com.example.ocr.KtpGeminiVisionParser
import com.example.ocr.KtpOcrDiagnosticDialog
import com.example.ocr.KtpOcrResult
import com.example.ocr.KtpOcrScanner
import com.example.ocr.KtpOcrScannerDialog
import com.example.storage.CustomerPhotoStorageHelper
import com.example.ui.components.CustomerAvatarThumbnail
import com.example.ui.components.CustomerTimelineSection
import com.example.ui.components.GpsLocationMapCard
import com.example.ui.components.MasterContractHierarchySection
import com.example.ui.components.PhotoDocumentationSlot
import com.example.ui.components.SmartCustomerDashboardCard
import com.example.ui.components.SmartPartialPaymentAllocationDialog
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.SkyBlueSurface
import com.example.ui.theme.StatusActiveBlue
import com.example.ui.theme.StatusActiveBlueBg
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPaidGreenBg
import com.example.ui.components.FieldErrorSupportingText
import com.example.ui.components.FormValidationAlertBanner
import com.example.ui.components.StorageSyncStatusBadge
import com.example.ui.theme.TextDateAccent
import com.example.util.FormValidationEngine
import com.example.util.FormValidationResult
import com.example.util.StorageSyncStatus
import com.example.ui.theme.TextDescription
import com.example.util.DateCurrencyUtils
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    transactions: List<RentalTransactionEntity>,
    customerPhotos: List<CustomerPhotoEntity> = emptyList(),
    paymentProofs: List<PaymentProofEntity> = emptyList(),
    transactionUnitItems: List<TransactionUnitItemEntity> = emptyList(),
    unitPaymentAllocations: List<UnitPaymentAllocationEntity> = emptyList(),
    customerTimelineEvents: List<CustomerTimelineEventEntity> = emptyList(),
    returnsHistory: List<FreezerReturnEntity> = emptyList(),
    paymentMethods: List<PaymentMethodEntity> = emptyList(),
    onSaveCustomer: (CustomerEntity) -> Unit,
    onDeleteCustomer: (CustomerEntity) -> Unit,
    onSaveCustomerPhoto: (CustomerEntity, String, String, Any, Long?, Double?, Double?, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onDeleteCustomerPhoto: (CustomerPhotoEntity) -> Unit = {},
    onOpenCustomerFolder: (CustomerEntity) -> Unit = {},
    onAddUnitForCustomer: (CustomerEntity) -> Unit = {},
    onPrintContractPdf: (RentalTransactionEntity) -> Unit = {},
    onAllocatePartialPayment: (Long, Long, Map<Long, Long>, String, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    formDrafts: List<FormDraftEntity> = emptyList(),
    pendingDraftToOpen: FormDraftEntity? = null,
    onPendingDraftConsumed: () -> Unit = {},
    onSaveDraft: (String, Long, String, String, String, String, (FormDraftEntity) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteDraft: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var activeCustomerDraftToApply by remember { mutableStateOf<FormDraftEntity?>(null) }
    var showFormDialog by remember { mutableStateOf(false) }
    var selectedCustomerForDetail by remember { mutableStateOf<CustomerEntity?>(null) }

    LaunchedEffect(pendingDraftToOpen) {
        if (pendingDraftToOpen != null && pendingDraftToOpen.formType == FormDraftTypes.CUSTOMER) {
            editingCustomer = if (pendingDraftToOpen.targetEntityId > 0L) {
                customers.firstOrNull { it.id == pendingDraftToOpen.targetEntityId }
            } else null
            activeCustomerDraftToApply = pendingDraftToOpen
            showFormDialog = true
            onPendingDraftConsumed()
        }
    }

    // Section 3B: Search customer by ID Pelanggan, Nama, Nomor HP, NIK, or Alamat
    val filteredCustomers = remember(customers, searchQuery) {
        customers.filter { c ->
            searchQuery.isBlank() ||
                c.kodePelanggan.contains(searchQuery, ignoreCase = true) ||
                c.namaLengkap.contains(searchQuery, ignoreCase = true) ||
                c.nomorHp.contains(searchQuery, ignoreCase = true) ||
                c.nik.contains(searchQuery, ignoreCase = true) ||
                c.alamatLengkap.contains(searchQuery, ignoreCase = true) ||
                c.lokasiPenggunaanDefault.contains(searchQuery, ignoreCase = true) ||
                c.kecamatan.contains(searchQuery, ignoreCase = true) ||
                c.kelurahanDesa.contains(searchQuery, ignoreCase = true) ||
                c.kabupatenKota.contains(searchQuery, ignoreCase = true)
        }
    }

    var visibleLimit by remember(searchQuery) { mutableStateOf(50) }
    val pagedCustomers = remember(filteredCustomers, visibleLimit) { filteredCustomers.take(visibleLimit) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCustomer = null
                    showFormDialog = true
                },
                containerColor = SeaBluePrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_customer")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Tambah Pelanggan")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Pelanggan", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("customers_screen")
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            SmartCaseOutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                caseMode = InputTextCaseMode.UPPERCASE,
                placeholder = { Text("Cari ID Pelanggan (CUST/SAF/...), Nama, WA, NIK, atau Lokasi...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari Pelanggan") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_customer_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pagedCustomers, key = { it.id }) { customer ->
                    val summary = remember(
                        customer,
                        transactions,
                        transactionUnitItems,
                        paymentProofs,
                        unitPaymentAllocations
                    ) {
                        FreezerRepository.buildCustomerSmartSummary(
                            customer = customer,
                            transactions = transactions,
                            allUnitItems = transactionUnitItems,
                            allPaymentProofs = paymentProofs,
                            allUnitAllocations = unitPaymentAllocations
                        )
                    }
                    CustomerCardItem(
                        customer = customer,
                        activeRentalsCount = summary.totalUnitAktif,
                        activeContractsCount = summary.totalKontrakAktif,
                        monthlyBillingTotal = summary.totalTagihanBulanan,
                        smartStatusLabel = summary.statusPelangganLabel,
                        totalRentalsCount = summary.totalKontrakKeseluruhan,
                        onClickDetail = { selectedCustomerForDetail = customer },
                        onClickEdit = {
                            editingCustomer = customer
                            showFormDialog = true
                        }
                    )
                }
                if (filteredCustomers.size > pagedCustomers.size) {
                    item(key = "load_more_customers") {
                        OutlinedButton(
                            onClick = { visibleLimit += 50 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_load_more_customers")
                        ) {
                            Text("Muat Lebih Banyak (${filteredCustomers.size - pagedCustomers.size} lagi)")
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        val targetCustId = editingCustomer?.id ?: 0L
        val matchingCustDraft = remember(formDrafts, targetCustId, activeCustomerDraftToApply) {
            activeCustomerDraftToApply ?: formDrafts.firstOrNull {
                it.formType == FormDraftTypes.CUSTOMER && it.targetEntityId == targetCustId
            }
        }
        CustomerFormDialog(
            initialCustomer = editingCustomer,
            existingCustomers = customers,
            availableDraft = matchingCustDraft,
            autoApplyDraft = activeCustomerDraftToApply != null,
            onDismiss = {
                activeCustomerDraftToApply = null
                showFormDialog = false
            },
            onViewExistingCustomerDetail = { existing ->
                activeCustomerDraftToApply = null
                showFormDialog = false
                selectedCustomerForDetail = existing
            },
            onSwitchToExistingCustomer = { existing ->
                activeCustomerDraftToApply = null
                editingCustomer = existing
            },
            onSave = { saved ->
                onSaveCustomer(saved)
                matchingCustDraft?.uuid?.let { onDeleteDraft(it) }
                activeCustomerDraftToApply = null
                showFormDialog = false
            },
            onDelete = { toDel ->
                onDeleteCustomer(toDel)
                activeCustomerDraftToApply = null
                showFormDialog = false
            },
            onAutoSaveDraft = onSaveDraft,
            onDiscardDraft = onDeleteDraft
        )
    }

    if (selectedCustomerForDetail != null) {
        val c = selectedCustomerForDetail!!
        val custRentals = transactions.filter { it.customerId == c.id }
        val custPhotos = customerPhotos.filter { it.customerId == c.id }
        CustomerDetailAndMultiRentalDialog(
            customer = c,
            rentals = custRentals,
            photos = custPhotos,
            allUnitItems = transactionUnitItems,
            allPaymentProofs = paymentProofs,
            allUnitAllocations = unitPaymentAllocations,
            storedTimelineEvents = customerTimelineEvents,
            returnsHistory = returnsHistory,
            paymentMethods = paymentMethods,
            onDismiss = { selectedCustomerForDetail = null },
            onEdit = {
                selectedCustomerForDetail = null
                editingCustomer = c
                showFormDialog = true
            },
            onSavePhoto = { cat, docName, source, txId, lat, lon, notes ->
                onSaveCustomerPhoto(c, cat, docName, source, txId, lat, lon, notes)
            },
            onDeletePhoto = onDeleteCustomerPhoto,
            onOpenFolder = { onOpenCustomerFolder(c) },
            onAddUnitForCustomer = {
                selectedCustomerForDetail = null
                onAddUnitForCustomer(c)
            },
            onPrintContractPdf = onPrintContractPdf,
            onAllocatePartialPayment = onAllocatePartialPayment
        )
    }
}

@Composable
private fun CustomerCardItem(
    customer: CustomerEntity,
    activeRentalsCount: Int,
    activeContractsCount: Int = 0,
    monthlyBillingTotal: Long = 0L,
    smartStatusLabel: String = "Terdaftar",
    totalRentalsCount: Int,
    onClickDetail: () -> Unit,
    onClickEdit: () -> Unit
) {
    val context = LocalContext.current
    val permanentCode = customer.kodePelanggan.ifBlank {
        "CUST/SAF/${customer.id.toString().padStart(4, '0')}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickDetail() }
            .testTag("customer_card_${customer.id}"),
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
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CustomerAvatarThumbnail(customer = customer, sizeDp = 46)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (activeRentalsCount > 0) StatusActiveBlueBg else StatusPaidGreenBg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (activeRentalsCount > 0) "$activeRentalsCount Unit • $activeContractsCount Kontrak" else smartStatusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (activeRentalsCount > 0) StatusActiveBlue else StatusPaidGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (monthlyBillingTotal > 0L) {
                                Surface(
                                    color = SkyBlueSurface,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${DateCurrencyUtils.formatRupiah(monthlyBillingTotal)}/bln",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NavyDark,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "$permanentCode • ${customer.jenisKelamin}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = onClickEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Pelanggan", tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = customer.namaLengkap,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Badge, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NIK: ${customer.nik}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = StatusPaidGreen, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "HP / WA: ${customer.nomorHp}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.nomorHp}"))
                        context.startActivity(dialIntent)
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "KTP: ${customer.alamatLengkap}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (customer.lokasiPenggunaanDefault.isNotBlank()) {
                Text(
                    text = "Lokasi Pakai: ${customer.namaLokasiDefault.takeIf { it.isNotBlank() }?.let { "$it - " } ?: ""}${customer.lokasiPenggunaanDefault}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeaBluePrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tgl Registrasi: ${DateCurrencyUtils.formatDisplayDate(customer.tanggalRegistrasi)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDateAccent,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Histori Transaksi: $totalRentalsCount Sewa",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeaBluePrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CustomerDetailAndMultiRentalDialog(
    customer: CustomerEntity,
    rentals: List<RentalTransactionEntity>,
    photos: List<CustomerPhotoEntity> = emptyList(),
    allUnitItems: List<TransactionUnitItemEntity> = emptyList(),
    allPaymentProofs: List<PaymentProofEntity> = emptyList(),
    allUnitAllocations: List<UnitPaymentAllocationEntity> = emptyList(),
    storedTimelineEvents: List<CustomerTimelineEventEntity> = emptyList(),
    returnsHistory: List<FreezerReturnEntity> = emptyList(),
    paymentMethods: List<PaymentMethodEntity> = emptyList(),
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSavePhoto: (kategori: String, docName: String, source: Any, txId: Long?, lat: Double?, lon: Double?, notes: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeletePhoto: (CustomerPhotoEntity) -> Unit = {},
    onOpenFolder: () -> Unit = {},
    onAddUnitForCustomer: () -> Unit = {},
    onPrintContractPdf: (RentalTransactionEntity) -> Unit = {},
    onAllocatePartialPayment: (Long, Long, Map<Long, Long>, String, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val permanentCode = customer.kodePelanggan.ifBlank {
        "CUST/SAF/${customer.id.toString().padStart(4, '0')}"
    }
    val separatedRt = customer.rt.ifBlank { customer.rtRw.substringBefore("/", "") }
    val separatedRw = customer.rw.ifBlank { customer.rtRw.substringAfter("/", "") }

    var activeSectionTab by remember { mutableStateOf("KONTRAK") } // KONTRAK, PEMBAYARAN, TIMELINE, PROFIL_FOTO
    var selectedCategoryTab by remember { mutableStateOf(CustomerPhotoStorageHelper.KAT_KTP) }
    var photoToPreviewFullscreen by remember { mutableStateOf<CustomerPhotoEntity?>(null) }
    var photoToDeleteConfirm by remember { mutableStateOf<CustomerPhotoEntity?>(null) }
    var showAddPhotoDialog by remember { mutableStateOf(false) }
    var allocatingTxForPartialPayment by remember { mutableStateOf<RentalTransactionEntity?>(null) }

    val smartSummary = remember(customer, rentals, allUnitItems, allPaymentProofs, allUnitAllocations) {
        FreezerRepository.buildCustomerSmartSummary(
            customer = customer,
            transactions = rentals,
            allUnitItems = allUnitItems,
            allPaymentProofs = allPaymentProofs,
            allUnitAllocations = allUnitAllocations
        )
    }

    val combinedTimeline = remember(
        customer,
        rentals,
        allUnitItems,
        allPaymentProofs,
        returnsHistory,
        photos,
        storedTimelineEvents
    ) {
        FreezerRepository.buildCombinedTimelineForCustomer(
            customer = customer,
            transactions = rentals,
            unitItems = allUnitItems,
            paymentProofs = allPaymentProofs,
            returns = returnsHistory,
            photos = photos,
            storedEvents = storedTimelineEvents
        )
    }

    val filteredPhotos = remember(photos, selectedCategoryTab) {
        photos.filter { it.kategori == selectedCategoryTab }
    }

    // Determine freezer usage coordinates (from customer default or latest transaction)
    val latestTxWithGps = rentals.firstOrNull { it.latitude != null && it.longitude != null }
    val displayLat = customer.latitudeDefault ?: latestTxWithGps?.latitude
    val displayLon = customer.longitudeDefault ?: latestTxWithGps?.longitude
    val displayUsageLocation = customer.lokasiPenggunaanDefault.ifBlank {
        latestTxWithGps?.alamatLokasiGps ?: customer.alamatLengkap
    }
    val displayLocationName = customer.namaLokasiDefault.ifBlank {
        latestTxWithGps?.namaLokasiUsaha ?: customer.namaLengkap
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
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MASTER CUSTOMER PROFILE • $permanentCode",
                            style = MaterialTheme.typography.labelMedium,
                            color = SeaBluePrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(customer.namaLengkap, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section 1 & Section 6: Smart Customer Dashboard Header + KPI Grid + 6 Quick Actions
                SmartCustomerDashboardCard(
                    summary = smartSummary,
                    onActionLihatKontrak = { activeSectionTab = "KONTRAK" },
                    onActionTambahUnit = onAddUnitForCustomer,
                    onActionRiwayatPembayaran = { activeSectionTab = "PEMBAYARAN" },
                    onActionDokumentasi = { activeSectionTab = "PROFIL_FOTO" },
                    onActionEditPelanggan = onEdit,
                    onActionCetakPdf = {
                        val targetTx = smartSummary.contracts.firstOrNull()?.transaction
                        if (targetTx != null) {
                            onPrintContractPdf(targetTx)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4 Section Navigation Tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        "KONTRAK" to "Kontrak (${smartSummary.contracts.size})",
                        "PEMBAYARAN" to "Tagihan & Bayar",
                        "TIMELINE" to "Timeline (${combinedTimeline.size})",
                        "PROFIL_FOTO" to "Profil, GPS & Foto"
                    ).forEach { (tabKey, tabLabel) ->
                        FilterChip(
                            selected = activeSectionTab == tabKey,
                            onClick = { activeSectionTab = tabKey },
                            modifier = Modifier.testTag("customer_detail_tab_$tabKey"),
                            label = {
                                Text(
                                    text = tabLabel,
                                    fontWeight = if (activeSectionTab == tabKey) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (activeSectionTab == "KONTRAK") {
                    MasterContractHierarchySection(
                        summary = smartSummary,
                        onAllocatePaymentForContract = { node -> allocatingTxForPartialPayment = node.transaction },
                        onPrintContractPdf = onPrintContractPdf,
                        onAddUnitContract = onAddUnitForCustomer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (activeSectionTab == "PEMBAYARAN") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "TAGIHAN PER UNIT & ALOKASI PEMBAYARAN SEBAGIAN",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        if (smartSummary.contracts.isEmpty()) {
                            Text("Belum ada kontrak atau tagihan untuk pelanggan ini.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            smartSummary.contracts.forEach { node ->
                                val tx = node.transaction
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(node.contractNumberDisplay, fontWeight = FontWeight.Bold, color = NavyDark, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    "Tagihan: ${DateCurrencyUtils.formatRupiah(tx.totalTagihan)} • Dibayar: ${DateCurrencyUtils.formatRupiah(tx.nominalPembayaran)} • Sisa: ${DateCurrencyUtils.formatRupiah((tx.totalTagihan - tx.nominalPembayaran).coerceAtLeast(0L))}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SeaBluePrimary
                                                )
                                            }
                                            val txSisaTagihan = (tx.totalTagihan - tx.nominalPembayaran).coerceAtLeast(0L)
                                            if (txSisaTagihan > 0L) {
                                                Button(
                                                    onClick = { allocatingTxForPartialPayment = tx },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.testTag("btn_allocate_tx_${tx.id}")
                                                ) {
                                                    Text("Alokasi Bayar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        node.unitItems.forEach { item ->
                                            val smartStatus = item.computeSmartBillingStatus()
                                            Text(
                                                text = "• ${item.namaUnitSnapshot}: ${DateCurrencyUtils.formatRupiah(item.nominalPembayaran)} / ${DateCurrencyUtils.formatRupiah(item.hargaSewa)} ($smartStatus • Jatuh Tempo ${DateCurrencyUtils.formatDisplayDate(item.tanggalJatuhTempo)})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NavyDark
                                            )
                                        }
                                        if (node.unitAllocations.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                            Text("Riwayat Alokasi Per Unit:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SeaBluePrimary)
                                            node.unitAllocations.forEach { alloc ->
                                                val unitLbl = alloc.namaUnitSnapshot.ifBlank { alloc.kodeUnitSnapshot }
                                                Text(
                                                    text = "  ✓ ${alloc.paymentDate}: $unitLbl -> ${DateCurrencyUtils.formatRupiah(alloc.allocatedAmount)} (${alloc.metodePembayaran})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StatusPaidGreen
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (activeSectionTab == "TIMELINE") {
                    CustomerTimelineSection(events = combinedTimeline)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (activeSectionTab == "PROFIL_FOTO") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ID Pelanggan Permanen: $permanentCode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SeaBluePrimary)
                        Text("NIK (16 Digit): ${customer.nik}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Nomor HP / WhatsApp: ${customer.nomorHp}", style = MaterialTheme.typography.bodyMedium)
                        Text("Tempat Lahir: ${customer.tempatLahir.ifBlank { "-" }} • Tanggal Lahir: ${customer.tanggalLahir.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Jenis Kelamin: ${customer.jenisKelamin}", style = MaterialTheme.typography.bodySmall)
                        Text("Alamat Sesuai KTP: ${customer.alamatLengkap}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "RT: ${separatedRt.ifBlank { "-" }} • RW: ${separatedRw.ifBlank { "-" }} • Kel/Desa: ${customer.kelurahanDesa.ifBlank { "-" }} • Kec: ${customer.kecamatan.ifBlank { "-" }} • Kab/Kota: ${customer.kabupatenKota.ifBlank { "-" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Agama: ${customer.agama.ifBlank { "-" }} • Status Perkawinan: ${customer.statusPerkawinan.ifBlank { "-" }} • Pekerjaan: ${customer.pekerjaan.ifBlank { "-" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Kewarganegaraan: ${customer.kewarganegaraan.ifBlank { "WNI" }} • Berlaku Hingga: ${customer.berlakuHingga.ifBlank { "SEUMUR HIDUP" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (customer.lokasiPenggunaanDefault.isNotBlank() || customer.namaLokasiDefault.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Nama Lokasi Freezer: ${customer.namaLokasiDefault.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = SeaBluePrimary
                            )
                            Text(
                                text = "Alamat Lokasi Penggunaan: ${customer.lokasiPenggunaanDefault.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SeaBluePrimary
                            )
                            Text(
                                text = "RT/RW Lokasi: ${customer.rtRwLokasiDefault.ifBlank { "-" }} • Kel/Desa: ${customer.kelurahanLokasiDefault.ifBlank { "-" }} • Kec: ${customer.kecamatanLokasiDefault.ifBlank { "-" }} • Kota/Kab: ${customer.kabupatenKotaLokasiDefault.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (displayLat != null && displayLon != null) {
                            Text("Koordinat GPS Lokasi Freezer: $displayLat, $displayLon", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Tanggal Registrasi: ${customer.tanggalRegistrasi}", style = MaterialTheme.typography.bodySmall)
                        if (customer.catatan.isNotBlank()) {
                            Text("Catatan: ${customer.catatan}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Section 3: Tampilan Lokasi Pelanggan (Peta Mini Lokasi Freezer + Pin Lokasi + [LIHAT PETA] + [NAVIGASI GOOGLE MAPS])
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "PETA MINI LOKASI FREEZER PELANGGAN",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
                Spacer(modifier = Modifier.height(6.dp))
                GpsLocationMapCard(
                    latitude = displayLat,
                    longitude = displayLon,
                    addressText = "$displayLocationName • $displayUsageLocation",
                    dateStr = customer.tanggalRegistrasi,
                    timeStr = "",
                    onPinMoved = null
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // SISTEM PENYIMPANAN FOTO OFFLINE PER PELANGGAN
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DOKUMENTASI FOTO PELANGGAN",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SeaBluePrimary
                                )
                                Text(
                                    text = "Folder: Pictures/SEWAin Aja Freezer SOC/${CustomerPhotoStorageHelper.getCustomerFolderName(customer)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            IconButton(
                                onClick = onOpenFolder,
                                modifier = Modifier.testTag("btn_open_customer_folder_gallery")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Buka di Galeri", tint = SeaBluePrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Category Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CustomerPhotoStorageHelper.ALL_CATEGORIES.forEach { cat ->
                                val isSelected = selectedCategoryTab == cat
                                val countInCat = photos.count { it.kategori == cat }
                                val shortLabel = when (cat) {
                                    CustomerPhotoStorageHelper.KAT_KTP -> "01. KTP"
                                    CustomerPhotoStorageHelper.KAT_SERAH_TERIMA -> "02. Serah Terima"
                                    CustomerPhotoStorageHelper.KAT_FOTO_UNIT -> "03. Unit"
                                    CustomerPhotoStorageHelper.KAT_FOTO_LOKASI -> "04. Lokasi"
                                    CustomerPhotoStorageHelper.KAT_PENGEMBALIAN -> "05. Kembali"
                                    else -> cat
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategoryTab = cat },
                                    label = {
                                        Text(
                                            text = "$shortLabel ($countInCat)",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action buttons: [+ Tambah Foto] & [📂 Buka Folder Galeri]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showAddPhotoDialog = true },
                                modifier = Modifier.weight(1f).testTag("btn_add_documentation_photo"),
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tambah Foto", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onOpenFolder,
                                modifier = Modifier.weight(1f).testTag("btn_open_folder_btn"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka di Galeri", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Photos list / grid in current category
                        if (filteredPhotos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Belum ada foto di folder '${CustomerPhotoStorageHelper.getCategoryDisplayName(selectedCategoryTab)}'",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Tekan 'Tambah Foto' untuk memotret atau memilih dari galeri.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextDescription,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                filteredPhotos.forEach { photoItem ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Thumbnail
                                            Box(
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.Black.copy(alpha = 0.05f))
                                                    .clickable { photoToPreviewFullscreen = photoItem },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val thumbPath = SmartImageProcessor.getExistingThumbnailPathOrNull(context, photoItem.filePath)
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(thumbPath?.let { File(it) } ?: photoItem.filePath)
                                                        .size(300, 300)
                                                        .scale(Scale.FILL)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = photoItem.namaDokumentasi,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 4.dp))
                                                        .padding(2.dp)
                                                ) {
                                                    Icon(Icons.Default.ZoomIn, contentDescription = "Lihat Foto", tint = Color.White, modifier = Modifier.size(12.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = photoItem.namaDokumentasi.ifBlank { photoItem.fileName },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NavyDark
                                                )
                                                Text(
                                                    text = photoItem.fileName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = SeaBluePrimary
                                                )
                                                Text(
                                                    text = "Waktu: ${photoItem.tanggalWaktuIso.replace("T", " ")}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (photoItem.catatan.isNotBlank()) {
                                                    Text(
                                                        text = "Catatan: ${photoItem.catatan}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { photoToDeleteConfirm = photoItem },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Hapus Foto", tint = StatusOverdueRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "HISTORI TRANSAKSI BERDASARKAN PELANGGAN INI (${rentals.size} Transaksi)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Seluruh riwayat sewa tercatat pada satu identitas pelanggan tetap ($permanentCode).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))
                if (rentals.isEmpty()) {
                    Text("Belum ada transaksi sewa untuk pelanggan ini.", style = MaterialTheme.typography.bodySmall)
                } else {
                    rentals.forEach { tx ->
                        val isHakMilik = tx.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
                        val sisaPeriode = (tx.totalPeriodeHakMilik - tx.pembayaranKeHakMilik).coerceAtLeast(0)
                        val programBadgeText = if (isHakMilik) {
                            "Sewa Hak Milik ${tx.totalPeriodeHakMilik}x (Ke-${tx.pembayaranKeHakMilik}/${tx.totalPeriodeHakMilik} • Sisa ${sisaPeriode}x • ${tx.statusProgramHakMilik})"
                        } else {
                            "Sewa Reguler"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(tx.nomorInvoice, style = MaterialTheme.typography.labelSmall, color = SeaBluePrimary, fontWeight = FontWeight.Bold)
                                    Text(tx.statusPenyewaan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                Text("${tx.namaUnitSnapshot} (${tx.kodeUnitSnapshot})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Program: $programBadgeText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isHakMilik) Color(0xFF0284C7) else NavyDark,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Periode: ${DateCurrencyUtils.formatDisplayDate(tx.tanggalMulaiSewa)} s/d ${DateCurrencyUtils.formatDisplayDate(tx.tanggalAkhirSewa)} (${tx.durasiHari} Hari)", style = MaterialTheme.typography.bodySmall)
                                if (tx.alamatLokasiGps.isNotBlank()) {
                                    Text("Lokasi Penggunaan: ${tx.alamatLokasiGps}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Tagihan: ${DateCurrencyUtils.formatRupiah(tx.totalTagihan)} • Status: ${tx.statusPembayaran}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                } // end activeSectionTab == "PROFIL_FOTO"
            }
        }
    }

    if (allocatingTxForPartialPayment != null) {
        val allocTx = allocatingTxForPartialPayment!!
        val itemsForTx = allUnitItems.filter { it.transactionId == allocTx.id }
        SmartPartialPaymentAllocationDialog(
            transaction = allocTx,
            unitItems = itemsForTx,
            paymentMethods = paymentMethods,
            onDismiss = { allocatingTxForPartialPayment = null },
            onConfirmAllocate = { totalPaid, allocations, paymentDate, method, sender, notes ->
                onAllocatePartialPayment(
                    allocTx.id,
                    totalPaid,
                    allocations,
                    paymentDate,
                    method,
                    sender,
                    notes
                )
                allocatingTxForPartialPayment = null
            }
        )
    }

    // Fullscreen Photo Viewer Dialog
    if (photoToPreviewFullscreen != null) {
        val p = photoToPreviewFullscreen!!
        Dialog(
            onDismissRequest = { photoToPreviewFullscreen = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = p.filePath,
                        contentDescription = p.namaDokumentasi,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Header Info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.namaDokumentasi, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(p.fileName, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                            Text(p.relativeFolder, color = LightBlueAccent, fontSize = 10.sp)
                        }
                        IconButton(onClick = { photoToPreviewFullscreen = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (photoToDeleteConfirm != null) {
        val p = photoToDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { photoToDeleteConfirm = null },
            title = { Text("Hapus Foto Dokumentasi?", fontWeight = FontWeight.Bold) },
            text = {
                Text("File '${p.fileName}' akan dihapus dari penyimpanan perangkat. Tindakan ini tidak dapat dibatalkan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePhoto(p)
                        photoToDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOverdueRed)
                ) {
                    Text("Hapus Foto")
                }
            },
            dismissButton = {
                TextButton(onClick = { photoToDeleteConfirm = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Add Photo Modal Dialog (Kamera / Galeri + Kategori + Nama Dokumentasi)
    if (showAddPhotoDialog) {
        AddCustomerDocumentationPhotoDialog(
            customer = customer,
            defaultCategory = selectedCategoryTab,
            onDismiss = { showAddPhotoDialog = false },
            onSave = { category, docName, source, txId, lat, lon, notes ->
                onSavePhoto(category, docName, source, txId, lat, lon, notes)
                showAddPhotoDialog = false
            }
        )
    }
}

@Composable
private fun AddCustomerDocumentationPhotoDialog(
    customer: CustomerEntity,
    defaultCategory: String,
    onDismiss: () -> Unit,
    onSave: (category: String, docName: String, source: Any, txId: Long?, lat: Double?, lon: Double?, notes: String) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(defaultCategory) }
    var documentationName by remember {
        mutableStateOf(
            when (defaultCategory) {
                CustomerPhotoStorageHelper.KAT_KTP -> "KTP_DEPAN"
                CustomerPhotoStorageHelper.KAT_SERAH_TERIMA -> "SERAH_TERIMA_UNIT"
                CustomerPhotoStorageHelper.KAT_FOTO_UNIT -> "KONDISI_FISIK"
                CustomerPhotoStorageHelper.KAT_FOTO_LOKASI -> "PENEMPATAN_FREEZER"
                CustomerPhotoStorageHelper.KAT_PENGEMBALIAN -> "KONDISI_PENGEMBALIAN"
                else -> "DOKUMENTASI"
            }
        )
    }
    var notes by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<Any?>(null) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var docValidationResult by remember { mutableStateOf<FormValidationResult?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedSource = uri
            try {
                val meta = SmartImageProcessor.extractExifMetadataFromUri(context, uri)
                val cat = when (selectedCategory) {
                    CustomerPhotoStorageHelper.KAT_KTP -> PhotoCategory.KTP
                    CustomerPhotoStorageHelper.KAT_FOTO_LOKASI -> PhotoCategory.LOCATION
                    else -> PhotoCategory.FREEZER
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { raw ->
                        previewBitmap = SmartImageProcessor.normalizeBitmapOrientation(
                            source = raw,
                            orientationTag = meta.orientationTag,
                            category = cat
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: android.graphics.Bitmap? ->
        if (bmp != null) {
            val cat = when (selectedCategory) {
                CustomerPhotoStorageHelper.KAT_KTP -> PhotoCategory.KTP
                CustomerPhotoStorageHelper.KAT_FOTO_LOKASI -> PhotoCategory.LOCATION
                else -> PhotoCategory.FREEZER
            }
            val normalized = SmartImageProcessor.normalizeBitmapOrientation(source = bmp, category = cat)
            selectedSource = normalized
            previewBitmap = normalized
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Tambah Foto Dokumentasi", fontWeight = FontWeight.Bold)
                Text(
                    text = "Folder: ${CustomerPhotoStorageHelper.getCustomerFolderName(customer)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeaBluePrimary,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FormValidationAlertBanner(
                    validationResult = docValidationResult,
                    testTagName = "doc_photo_validation_banner"
                )
                Text("1. Pilih Kategori Folder:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CustomerPhotoStorageHelper.ALL_CATEGORIES.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCategory = cat
                                    documentationName = when (cat) {
                                        CustomerPhotoStorageHelper.KAT_KTP -> "KTP_DEPAN"
                                        CustomerPhotoStorageHelper.KAT_SERAH_TERIMA -> "SERAH_TERIMA_UNIT"
                                        CustomerPhotoStorageHelper.KAT_FOTO_UNIT -> "KONDISI_FISIK"
                                        CustomerPhotoStorageHelper.KAT_FOTO_LOKASI -> "PENEMPATAN_FREEZER"
                                        CustomerPhotoStorageHelper.KAT_PENGEMBALIAN -> "KONDISI_PENGEMBALIAN"
                                        else -> "DOKUMENTASI"
                                    }
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = selectedCategory == cat,
                                onCheckedChange = {
                                    selectedCategory = cat
                                    documentationName = when (cat) {
                                        CustomerPhotoStorageHelper.KAT_KTP -> "KTP_DEPAN"
                                        CustomerPhotoStorageHelper.KAT_SERAH_TERIMA -> "SERAH_TERIMA_UNIT"
                                        CustomerPhotoStorageHelper.KAT_FOTO_UNIT -> "KONDISI_FISIK"
                                        CustomerPhotoStorageHelper.KAT_FOTO_LOKASI -> "PENEMPATAN_FREEZER"
                                        CustomerPhotoStorageHelper.KAT_PENGEMBALIAN -> "KONDISI_PENGEMBALIAN"
                                        else -> "DOKUMENTASI"
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(CustomerPhotoStorageHelper.getCategoryDisplayName(cat), fontSize = 12.sp)
                        }
                    }
                }

                SmartCaseOutlinedTextField(
                    value = documentationName,
                    onValueChange = { documentationName = it },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Nama Dokumentasi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SmartCaseOutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Catatan Tambahan (Opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("2. Ambil atau Pilih Foto:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kamera", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galeri", fontSize = 12.sp)
                    }
                }

                if (previewBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Preview Foto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = FormValidationEngine.validateDocumentationForm(
                        customerId = customer.id,
                        category = selectedCategory,
                        documentationName = documentationName,
                        hasPhotoSelected = selectedSource != null
                    )
                    if (!res.isValid) {
                        docValidationResult = res
                        return@Button
                    }
                    docValidationResult = null
                    onSave(
                        selectedCategory,
                        documentationName.trim(),
                        selectedSource!!,
                        null,
                        customer.latitudeDefault,
                        customer.longitudeDefault,
                        notes
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                modifier = Modifier.testTag("btn_save_doc_photo")
            ) {
                Text("Simpan Foto Offline")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun CustomerFormDialog(
    initialCustomer: CustomerEntity?,
    existingCustomers: List<CustomerEntity> = emptyList(),
    availableDraft: FormDraftEntity? = null,
    autoApplyDraft: Boolean = false,
    onDismiss: () -> Unit,
    onViewExistingCustomerDetail: ((CustomerEntity) -> Unit)? = null,
    onSwitchToExistingCustomer: ((CustomerEntity) -> Unit)? = null,
    onSave: (CustomerEntity) -> Unit,
    onDelete: (CustomerEntity) -> Unit,
    onAutoSaveDraft: (String, Long, String, String, String, String, (FormDraftEntity) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDiscardDraft: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val autoKodePelanggan = remember(initialCustomer, existingCustomers) {
        initialCustomer?.kodePelanggan?.ifBlank {
            "CUST/SAF/${(initialCustomer.id.takeIf { it > 0 } ?: (existingCustomers.size + 1).toLong()).toString().padStart(4, '0')}"
        } ?: "CUST/SAF/${(existingCustomers.size + 1).toString().padStart(4, '0')}"
    }
    var namaLengkap by remember(initialCustomer) { mutableStateOf(initialCustomer?.namaLengkap ?: "") }
    var nik by remember(initialCustomer) { mutableStateOf(initialCustomer?.nik ?: "") }
    var nomorHp by remember(initialCustomer) { mutableStateOf(initialCustomer?.nomorHp ?: "") }
    var tempatLahir by remember(initialCustomer) { mutableStateOf(initialCustomer?.tempatLahir ?: "") }
    var tanggalLahir by remember(initialCustomer) { mutableStateOf(initialCustomer?.tanggalLahir ?: "") }
    var jenisKelamin by remember(initialCustomer) { mutableStateOf(initialCustomer?.jenisKelamin ?: "Laki-laki") }
    var alamatLengkap by remember(initialCustomer) { mutableStateOf(initialCustomer?.alamatLengkap ?: "") }

    // Separated RT & RW fields (Feature 7)
    var rt by remember(initialCustomer) {
        mutableStateOf(
            initialCustomer?.rt?.ifBlank { initialCustomer.rtRw.substringBefore("/", "") } ?: ""
        )
    }
    var rw by remember(initialCustomer) {
        mutableStateOf(
            initialCustomer?.rw?.ifBlank { initialCustomer.rtRw.substringAfter("/", "") } ?: ""
        )
    }
    var kelurahanDesa by remember(initialCustomer) { mutableStateOf(initialCustomer?.kelurahanDesa ?: "") }
    var kecamatan by remember(initialCustomer) { mutableStateOf(initialCustomer?.kecamatan ?: "") }
    var kabupatenKota by remember(initialCustomer) { mutableStateOf(initialCustomer?.kabupatenKota ?: "") }
    var provinsi by remember(initialCustomer) { mutableStateOf("JAWA TENGAH") }
    var golonganDarah by remember(initialCustomer) { mutableStateOf("-") }
    var agama by remember(initialCustomer) { mutableStateOf(initialCustomer?.agama ?: "ISLAM") }
    var statusPerkawinan by remember(initialCustomer) { mutableStateOf(initialCustomer?.statusPerkawinan ?: "KAWIN") }
    var pekerjaan by remember(initialCustomer) { mutableStateOf(initialCustomer?.pekerjaan ?: "") }
    var kewarganegaraan by remember(initialCustomer) { mutableStateOf(initialCustomer?.kewarganegaraan ?: "WNI") }
    var berlakuHingga by remember(initialCustomer) { mutableStateOf(initialCustomer?.berlakuHingga?.ifBlank { "SEUMUR HIDUP" } ?: "SEUMUR HIDUP") }

    // Feature 3: Tagging Lokasi Penggunaan Freezer on Tambah Pelanggan
    var namaLokasiDefault by remember(initialCustomer) { mutableStateOf(initialCustomer?.namaLokasiDefault ?: "") }
    var lokasiPenggunaanDefault by remember(initialCustomer) { mutableStateOf(initialCustomer?.lokasiPenggunaanDefault ?: "") }
    var rtRwLokasiDefault by remember(initialCustomer) { mutableStateOf(initialCustomer?.rtRwLokasiDefault ?: "") }
    var kelurahanLokasiDefault by remember(initialCustomer) { mutableStateOf(initialCustomer?.kelurahanLokasiDefault ?: "") }
    var kecamatanLokasiDefault by remember(initialCustomer) { mutableStateOf(initialCustomer?.kecamatanLokasiDefault ?: "") }
    var kotaKabupatenLokasiDefault by remember(initialCustomer) { mutableStateOf(initialCustomer?.kabupatenKotaLokasiDefault ?: "Kota Surakarta") }
    var latDefaultText by remember(initialCustomer) { mutableStateOf(initialCustomer?.latitudeDefault?.toString() ?: "") }
    var lonDefaultText by remember(initialCustomer) { mutableStateOf(initialCustomer?.longitudeDefault?.toString() ?: "") }
    var linkGoogleMapsDefault by remember(initialCustomer) { mutableStateOf(initialCustomer?.linkGoogleMapsDefault ?: "") }

    var fotoKtpDepanUri by remember(initialCustomer) { mutableStateOf(initialCustomer?.fotoKtpDepanUri ?: "") }
    var fotoKtpBelakangUri by remember(initialCustomer) { mutableStateOf(initialCustomer?.fotoKtpBelakangUri ?: "") }
    var fotoPelangganUri by remember(initialCustomer) { mutableStateOf(initialCustomer?.fotoPelangganUri ?: "") }
    var catatan by remember(initialCustomer) { mutableStateOf(initialCustomer?.catatan ?: "") }
    var tanggalRegistrasi by remember(initialCustomer) { mutableStateOf(initialCustomer?.tanggalRegistrasi ?: DateCurrencyUtils.todayIso()) }
    var persetujuanDataKtp by remember(initialCustomer) { mutableStateOf(initialCustomer?.persetujuanDataKtpLokasi ?: true) }

    var showOcrScannerDialog by remember { mutableStateOf(false) }
    var showAiScanOptionDialog by remember { mutableStateOf(false) }
    var isScanningInProgress by remember { mutableStateOf(false) }
    var scanningStatusText by remember { mutableStateOf("") }
    var latestOcrResult by remember { mutableStateOf<KtpOcrResult?>(null) }
    var showStructuredJsonInspection by remember { mutableStateOf(false) }
    var ocrSuccessBanner by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var viewingDuplicateCustomerPopup by remember { mutableStateOf<CustomerEntity?>(null) }
    // Section G: Jangan menyimpan hasil OCR yang belum diverifikasi sebagai data final pelanggan
    var isOcrVerifiedByAdmin by remember(initialCustomer) {
        mutableStateOf(initialCustomer?.statusVerifikasiAdminOcr != "BELUM_DIVERIFIKASI")
    }
    var manuallyCorrectedFields by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showOcrDiagnosticDialog by remember { mutableStateOf(false) }

    val ktpGeminiParser = remember { KtpGeminiVisionParser() }

    // Section B: Auto-Draft state for Customer Form
    var currentCustDraftUuid by remember { mutableStateOf(if (autoApplyDraft) availableDraft?.uuid.orEmpty() else "") }
    var custDraftSavedAtDisplay by remember { mutableStateOf("") }
    var showCustResumeBanner by remember { mutableStateOf(availableDraft != null && !autoApplyDraft) }
    var custDraftInitialized by remember { mutableStateOf(false) }

    fun applyCustomerDraft(draft: FormDraftEntity) {
        val obj = FormDraftCodec.parseJsonObject(draft.payloadJson)
        if (obj.has("namaLengkap")) namaLengkap = obj.optString("namaLengkap", namaLengkap)
        if (obj.has("nik")) nik = obj.optString("nik", nik)
        if (obj.has("nomorHp")) nomorHp = obj.optString("nomorHp", nomorHp)
        if (obj.has("tempatLahir")) tempatLahir = obj.optString("tempatLahir", tempatLahir)
        if (obj.has("tanggalLahir")) tanggalLahir = obj.optString("tanggalLahir", tanggalLahir)
        if (obj.has("jenisKelamin")) jenisKelamin = obj.optString("jenisKelamin", jenisKelamin)
        if (obj.has("alamatLengkap")) alamatLengkap = obj.optString("alamatLengkap", alamatLengkap)
        if (obj.has("rt")) rt = obj.optString("rt", rt)
        if (obj.has("rw")) rw = obj.optString("rw", rw)
        if (obj.has("kelurahanDesa")) kelurahanDesa = obj.optString("kelurahanDesa", kelurahanDesa)
        if (obj.has("kecamatan")) kecamatan = obj.optString("kecamatan", kecamatan)
        if (obj.has("kabupatenKota")) kabupatenKota = obj.optString("kabupatenKota", kabupatenKota)
        if (obj.has("agama")) agama = obj.optString("agama", agama)
        if (obj.has("statusPerkawinan")) statusPerkawinan = obj.optString("statusPerkawinan", statusPerkawinan)
        if (obj.has("pekerjaan")) pekerjaan = obj.optString("pekerjaan", pekerjaan)
        if (obj.has("namaLokasiDefault")) namaLokasiDefault = obj.optString("namaLokasiDefault", namaLokasiDefault)
        if (obj.has("lokasiPenggunaanDefault")) lokasiPenggunaanDefault = obj.optString("lokasiPenggunaanDefault", lokasiPenggunaanDefault)
        if (obj.has("latDefaultText")) latDefaultText = obj.optString("latDefaultText", latDefaultText)
        if (obj.has("lonDefaultText")) lonDefaultText = obj.optString("lonDefaultText", lonDefaultText)
        if (obj.has("fotoKtpDepanUri")) fotoKtpDepanUri = obj.optString("fotoKtpDepanUri", fotoKtpDepanUri)
        if (obj.has("catatan")) catatan = obj.optString("catatan", catatan)
        currentCustDraftUuid = draft.uuid
        custDraftSavedAtDisplay = draft.updatedAtDisplay
        showCustResumeBanner = false
    }

    LaunchedEffect(availableDraft?.uuid, autoApplyDraft) {
        if (autoApplyDraft && availableDraft != null) {
            applyCustomerDraft(availableDraft)
        }
        custDraftInitialized = true
    }

    val customerDraftPayloadJson = remember(
        namaLengkap, nik, nomorHp, tempatLahir, tanggalLahir, jenisKelamin,
        alamatLengkap, rt, rw, kelurahanDesa, kecamatan, kabupatenKota,
        agama, statusPerkawinan, pekerjaan, namaLokasiDefault,
        lokasiPenggunaanDefault, latDefaultText, lonDefaultText, fotoKtpDepanUri, catatan
    ) {
        FormDraftCodec.encodeMap(
            mapOf(
                "namaLengkap" to namaLengkap,
                "nik" to nik,
                "nomorHp" to nomorHp,
                "tempatLahir" to tempatLahir,
                "tanggalLahir" to tanggalLahir,
                "jenisKelamin" to jenisKelamin,
                "alamatLengkap" to alamatLengkap,
                "rt" to rt,
                "rw" to rw,
                "kelurahanDesa" to kelurahanDesa,
                "kecamatan" to kecamatan,
                "kabupatenKota" to kabupatenKota,
                "agama" to agama,
                "statusPerkawinan" to statusPerkawinan,
                "pekerjaan" to pekerjaan,
                "namaLokasiDefault" to namaLokasiDefault,
                "lokasiPenggunaanDefault" to lokasiPenggunaanDefault,
                "latDefaultText" to latDefaultText,
                "lonDefaultText" to lonDefaultText,
                "fotoKtpDepanUri" to fotoKtpDepanUri,
                "catatan" to catatan
            )
        )
    }

    LaunchedEffect(customerDraftPayloadJson, custDraftInitialized) {
        if (!custDraftInitialized) return@LaunchedEffect
        val hasInput = namaLengkap.isNotBlank() || nik.isNotBlank() || nomorHp.isNotBlank() || alamatLengkap.isNotBlank() || fotoKtpDepanUri.isNotBlank()
        if (!hasInput) return@LaunchedEffect
        delay(650L)
        onAutoSaveDraft(
            FormDraftTypes.CUSTOMER,
            initialCustomer?.id ?: 0L,
            namaLengkap.ifBlank { "Draft Pelanggan Baru" },
            listOfNotNull(nik.takeIf { it.isNotBlank() }, nomorHp.takeIf { it.isNotBlank() }).joinToString(" • "),
            customerDraftPayloadJson,
            currentCustDraftUuid
        ) { savedDraft ->
            currentCustDraftUuid = savedDraft.uuid
            custDraftSavedAtDisplay = savedDraft.updatedAtDisplay
        }
    }

    /**
     * Section D, E, F & G: Memetakan hasil pembacaan ke formulir Tambah Pelanggan Baru:
     * - Confidence >= 90%: Otomatis terisi dan ditandai hijau.
     * - Confidence 70%-89%: Terisi tetapi memerlukan verifikasi admin.
     * - Confidence < 70%: Jangan dianggap data valid otomatis (kecuali admin menekan "Koreksi Manual").
     * - Jika tidak terbaca: Tampilkan status TIDAK TERBACA.
     * - Jangan menghapus foto KTP walaupun OCR gagal.
     */
    fun applyOcrResultToForm(
        ocrResult: KtpOcrResult,
        includeLowConfidenceUnder50: Boolean = false
    ) {
        latestOcrResult = ocrResult
        // Section H.2: Jangan menghapus foto KTP meskipun OCR gagal
        if (ocrResult.savedOriginalPhotoPath.isNotBlank()) {
            fotoKtpDepanUri = ocrResult.savedOriginalPhotoPath
        }

        // Section G: Jika status pembacaan perlu_verifikasi atau gagal, wajibkan verifikasi admin sebelum simpan final
        val allMainGreen = ocrResult.statusPembacaan == "berhasil" &&
            ocrResult.isGreenConfidence90Plus("nik") &&
            ocrResult.isGreenConfidence90Plus("nama") &&
            ocrResult.isGreenConfidence90Plus("tanggal_lahir") &&
            ocrResult.isGreenConfidence90Plus("jenis_kelamin") &&
            ocrResult.isGreenConfidence90Plus("alamat")
        isOcrVerifiedByAdmin = allMainGreen

        val currentFormState = CustomerRegistrationFormState(
            nik = nik,
            namaLengkap = namaLengkap,
            tempatLahir = tempatLahir,
            tanggalLahir = tanggalLahir,
            jenisKelamin = jenisKelamin,
            golonganDarah = golonganDarah,
            alamatLengkap = alamatLengkap,
            rt = rt,
            rw = rw,
            kelurahanDesa = kelurahanDesa,
            kecamatan = kecamatan,
            kabupatenKota = kabupatenKota,
            provinsi = provinsi,
            agama = agama,
            statusPerkawinan = statusPerkawinan,
            pekerjaan = pekerjaan,
            kewarganegaraan = kewarganegaraan,
            berlakuHingga = berlakuHingga,
            fotoKtpDepanUri = fotoKtpDepanUri
        )

        val ktpData = KtpGeminiParser.fromKtpOcrResult(ocrResult)
        val mappedForm = KtpGeminiParser.mapKtpDataToCustomerForm(
            ktpData = ktpData,
            currentForm = currentFormState,
            allowLowConfidenceUnder50 = includeLowConfidenceUnder50,
            savedPhotoPath = ocrResult.savedOriginalPhotoPath
        )

        fun pickStrict70OrManual(jsonKey: String, mappedVal: String, fallbackCurrent: String): String {
            if (mappedVal.isBlank()) return fallbackCurrent
            val conf = ocrResult.confidence.getScoreForKey(jsonKey)
            return if (conf >= 70 || includeLowConfidenceUnder50 || jsonKey in manuallyCorrectedFields) {
                mappedVal
            } else {
                fallbackCurrent
            }
        }

        nik = pickStrict70OrManual("nik", mappedForm.nik, nik)
        namaLengkap = pickStrict70OrManual("nama", mappedForm.namaLengkap, namaLengkap)
        tempatLahir = pickStrict70OrManual("tempat_lahir", mappedForm.tempatLahir, tempatLahir)
        tanggalLahir = pickStrict70OrManual("tanggal_lahir", mappedForm.tanggalLahir, tanggalLahir)
        jenisKelamin = pickStrict70OrManual("jenis_kelamin", mappedForm.jenisKelamin, jenisKelamin)
        golonganDarah = mappedForm.golonganDarah.ifBlank { golonganDarah }
        alamatLengkap = pickStrict70OrManual("alamat", mappedForm.alamatLengkap, alamatLengkap)
        rt = pickStrict70OrManual("rt", mappedForm.rt, rt)
        rw = pickStrict70OrManual("rw", mappedForm.rw, rw)
        kelurahanDesa = pickStrict70OrManual("kelurahan", mappedForm.kelurahanDesa, kelurahanDesa)
        kecamatan = pickStrict70OrManual("kecamatan", mappedForm.kecamatan, kecamatan)
        kabupatenKota = mappedForm.kabupatenKota.ifBlank { kabupatenKota }
        provinsi = mappedForm.provinsi.ifBlank { provinsi }
        agama = pickStrict70OrManual("agama", mappedForm.agama, agama)
        statusPerkawinan = pickStrict70OrManual("status_perkawinan", mappedForm.statusPerkawinan, statusPerkawinan)
        pekerjaan = pickStrict70OrManual("pekerjaan", mappedForm.pekerjaan, pekerjaan)
        kewarganegaraan = mappedForm.kewarganegaraan.ifBlank { kewarganegaraan }
        berlakuHingga = mappedForm.berlakuHingga.ifBlank { berlakuHingga }
        if (mappedForm.fotoKtpDepanUri.isNotBlank()) {
            fotoKtpDepanUri = mappedForm.fotoKtpDepanUri
        }

        ocrSuccessBanner = when (ocrResult.statusPembacaan) {
            "berhasil" -> "Foto berhasil diproses! Seluruh field KTP terbaca (Confidence >= 90% Hijau) dan telah dipetakan ke formulir pelanggan."
            "gagal" -> "Pembacaan otomatis gagal: ${ocrResult.failureReason.ifBlank { "Teks KTP tidak terbaca jelas." }} Foto KTP tetap tersimpan. Silakan pilih Foto Ulang atau Input Manual."
            else -> "Pembacaan selesai dengan status PERLU VERIFIKASI ADMIN. Mohon periksa field kuning (70-89%) atau koreksi manual (<70%), lalu centang verifikasi admin sebelum menyimpan."
        }
    }

    val directCameraOcrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { capturedBitmap: Bitmap? ->
        if (capturedBitmap != null) {
            isScanningInProgress = true
            scanningStatusText = "Memproses foto kamera & mengekstrak data e-KTP via ML Kit V2 + AI Vision..."
            scope.launch {
                val ocrRes = KtpOcrScanner.scanKtpBitmapDirectly(context, capturedBitmap)
                isScanningInProgress = false
                applyOcrResultToForm(ocrRes, includeLowConfidenceUnder50 = false)
            }
        }
    }

    val directGalleryOcrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isScanningInProgress = true
            scanningStatusText = "Memproses gambar KTP & mengekstrak JSON via ML Kit V2 + AI Vision..."
            scope.launch {
                val ocrRes = KtpOcrScanner.scanKtpUriOnDevice(context, uri)
                isScanningInProgress = false
                applyOcrResultToForm(ocrRes, includeLowConfidenceUnder50 = false)
            }
        }
    }

    var contactImportNotice by remember { mutableStateOf<String?>(null) }
    val launchWhatsAppContactPicker = rememberWhatsAppContactImporter(
        onContactImported = { normalizedWa, upperName ->
            nomorHp = normalizedWa
            if (namaLengkap.isBlank() && upperName.isNotBlank()) {
                namaLengkap = upperName
            }
            validationError = null
            contactImportNotice = "Kontak berhasil diimpor: $normalizedWa"
        },
        onStatusMessage = { msg ->
            contactImportNotice = msg
        }
    )

    // Duplicate NIK & Nomor WhatsApp detection (normalisasi +62 / 62 / 08)
    val isNik16Valid = nik.length == 16 && nik.all { it.isDigit() }
    val normalizedInputPhone = remember(nomorHp) {
        PhoneNumberNormalizer.normalizeWhatsAppNumber(nomorHp)
    }
    var confirmDuplicatePhoneOverride by remember(normalizedInputPhone) { mutableStateOf(false) }
    val duplicateNikCustomer = remember(nik, existingCustomers, initialCustomer) {
        if (nik.length == 16) {
            existingCustomers.find { it.nik == nik && it.id != (initialCustomer?.id ?: -1L) }
        } else {
            null
        }
    }
    val duplicatePhoneCustomer = remember(normalizedInputPhone, existingCustomers, initialCustomer) {
        if (normalizedInputPhone.length >= 8) {
            existingCustomers.find {
                it.id != (initialCustomer?.id ?: -1L) &&
                    PhoneNumberNormalizer.areEquivalentWhatsAppNumbers(it.nomorHp, normalizedInputPhone)
            }
        } else {
            null
        }
    }
    val duplicateCustomer = duplicateNikCustomer ?: if (confirmDuplicatePhoneOverride) null else duplicatePhoneCustomer

    var formValidationResult by remember { mutableStateOf<FormValidationResult?>(null) }
    val customerFormScrollState = rememberScrollState()

    fun triggerSaveCustomerAction() {
        val res = FormValidationEngine.validateCustomerForm(
            namaLengkap = namaLengkap,
            nik = nik,
            nomorHp = nomorHp,
            alamatLengkap = alamatLengkap,
            requireOcrVerification = latestOcrResult != null,
            isOcrVerifiedByAdmin = isOcrVerifiedByAdmin
        )
        if (!res.isValid) {
            formValidationResult = res
            validationError = res.bannerMessage
            scope.launch { customerFormScrollState.animateScrollTo(0) }
            return
        }
        formValidationResult = null
        validationError = null
        val finalNik = nik.filter { it.isDigit() }
        val combinedRtRw = if (rt.isNotBlank() || rw.isNotBlank()) {
            "${rt.ifBlank { "000" }}/${rw.ifBlank { "000" }}"
        } else {
            initialCustomer?.rtRw ?: ""
        }
        val finalLat = latDefaultText.toDoubleOrNull()
        val finalLon = lonDefaultText.toDoubleOrNull()
        val finalMapsLink = linkGoogleMapsDefault.ifBlank {
            if (finalLat != null && finalLon != null) {
                LocationGpsHelper.formatGoogleMapsUrl(finalLat, finalLon)
            } else ""
        }
        val ocr = latestOcrResult
        val rawOriginalPath = fotoKtpDepanUri.ifBlank { ocr?.savedOriginalPhotoPath.orEmpty() }
        val rawPreprocessedPath = ocr?.savedPreprocessedPhotoPath?.ifBlank { initialCustomer?.fotoKtpPreprocessedUri ?: "" } ?: (initialCustomer?.fotoKtpPreprocessedUri ?: "")

        // Section 12: Simpan ke struktur folder lokal /Customers/<CUST-ID>/ktp_original.jpg, ktp_processed.jpg, ocr_result.json
        val storedPaths = KtpCustomerStorageManager.saveCustomerKtpArtifacts(
            context = context,
            customerCode = autoKodePelanggan,
            originalPhotoPath = rawOriginalPath,
            preprocessedPhotoPath = rawPreprocessedPath,
            ocrResult = ocr
        )

        val finalOriginalUri = storedPaths.originalPhotoPath.ifBlank { rawOriginalPath }
        val finalProcessedUri = storedPaths.processedPhotoPath.ifBlank { rawPreprocessedPath }
        val finalScanTime = ocr?.scanTimestampIso?.ifBlank {
            initialCustomer?.waktuPemindaianOcr?.ifBlank {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            } ?: java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        } ?: (initialCustomer?.waktuPemindaianOcr ?: "")

        val entity = CustomerEntity(
            id = initialCustomer?.id ?: 0L,
            kodePelanggan = initialCustomer?.kodePelanggan?.ifBlank { autoKodePelanggan } ?: autoKodePelanggan,
            namaLengkap = namaLengkap.ifBlank { "Pelanggan SAF Baru" },
            nik = finalNik,
            nomorHp = nomorHp.ifBlank { "081200000000" },
            alamatLengkap = alamatLengkap.ifBlank { "Surakarta, Solo Raya" },
            jenisKelamin = jenisKelamin.ifBlank { "Laki-laki" },
            fotoKtpDepanUri = finalOriginalUri,
            fotoKtpBelakangUri = fotoKtpBelakangUri,
            catatan = catatan,
            tanggalRegistrasi = tanggalRegistrasi,
            tempatLahir = tempatLahir,
            tanggalLahir = tanggalLahir,
            rtRw = combinedRtRw,
            kelurahanDesa = kelurahanDesa,
            kecamatan = kecamatan,
            agama = agama,
            statusPerkawinan = statusPerkawinan,
            pekerjaan = pekerjaan,
            kewarganegaraan = kewarganegaraan.ifBlank { "WNI" },
            persetujuanDataKtpLokasi = persetujuanDataKtp,
            lokasiPenggunaanDefault = lokasiPenggunaanDefault,
            latitudeDefault = finalLat,
            longitudeDefault = finalLon,
            rt = rt,
            rw = rw,
            kabupatenKota = kabupatenKota,
            berlakuHingga = berlakuHingga.ifBlank { "SEUMUR HIDUP" },
            namaLokasiDefault = namaLokasiDefault,
            rtRwLokasiDefault = rtRwLokasiDefault,
            kelurahanLokasiDefault = kelurahanLokasiDefault,
            kecamatanLokasiDefault = kecamatanLokasiDefault,
            kabupatenKotaLokasiDefault = kotaKabupatenLokasiDefault,
            linkGoogleMapsDefault = finalMapsLink,
            fotoKtpPreprocessedUri = finalProcessedUri,
            rawOcrText = ocr?.rawOcrText?.ifBlank { initialCustomer?.rawOcrText ?: "" } ?: (initialCustomer?.rawOcrText ?: ""),
            parsedOcrJson = ocr?.toStructuredJsonString()?.ifBlank { initialCustomer?.parsedOcrJson ?: "" } ?: (initialCustomer?.parsedOcrJson ?: ""),
            ocrFieldConfidencesJson = ocr?.confidence?.toAllFieldsJsonObject()?.toString() ?: (initialCustomer?.ocrFieldConfidencesJson ?: ""),
            statusVerifikasiAdminOcr = if (isOcrVerifiedByAdmin) "TERVERIFIKASI_ADMIN" else "BELUM_DIVERIFIKASI",
            waktuPemindaianOcr = finalScanTime,
            fotoPelangganUri = fotoPelangganUri,
            statusPelanggan = initialCustomer?.statusPelanggan ?: "Aktif"
        )
        if (currentCustDraftUuid.isNotBlank()) {
            onDiscardDraft(currentCustDraftUuid)
        }
        onSave(entity)
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
                            text = if (initialCustomer == null) "Tambah Pelanggan Baru" else "Edit Data Pelanggan",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID Pelanggan Otomatis: $autoKodePelanggan",
                            style = MaterialTheme.typography.labelMedium,
                            color = SeaBluePrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (custDraftSavedAtDisplay.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            DraftAutoSaveStatusPill(savedAtDisplay = custDraftSavedAtDisplay)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                if (showCustResumeBanner && availableDraft != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ResumeDraftBanner(
                        draft = availableDraft,
                        onResume = { applyCustomerDraft(availableDraft) },
                        onDiscard = {
                            onDiscardDraft(availableDraft.uuid)
                            showCustResumeBanner = false
                        }
                    )
                }

                FormValidationAlertBanner(
                    validationResult = formValidationResult,
                    modifier = Modifier.padding(top = 8.dp),
                    testTagName = "customer_form_validation_banner"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(customerFormScrollState)
                ) {
                    // 1. FITUR AI SCAN KTP OTOMATIS (e-KTP INDONESIA)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyDark)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "AI SCAN KTP OTOMATIS (GEMINI VISION + SPATIAL OCR)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Auto-Crop Kartu • Koreksi Kemiringan • Ekstraksi JSON 18 Field e-KTP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF34D399)
                                    )
                                }
                            }

                            Text(
                                text = "Gunakan Google Gemini Vision untuk membaca NIK 16 digit, Nama Lengkap, Tempat/Tgl Lahir, Jenis Kelamin, Alamat, RT/RW, Kelurahan, Kecamatan, Kabupaten/Kota, dan Provinsi secara akurat tanpa tertukar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f)
                            )

                            // Main Prominent Button: "📷 Scan KTP dengan AI"
                            Button(
                                onClick = { showAiScanOptionDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_scan_ktp_with_ai")
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📷 Scan KTP dengan AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            }

                            // Three explicit quick options:
                            // 1. Ambil foto KTP langsung melalui kamera smartphone
                            // 2. Upload foto KTP dari galeri perangkat
                            // 3. Uji sampel e-KTP langsung
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilledTonalButton(
                                    onClick = { directCameraOcrLauncher.launch(null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_scan_ktp_kamera")
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("1. Kamera HP", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = {
                                        directGalleryOcrLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBAE6FD)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_scan_ktp_galeri")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("2. Galeri", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        isScanningInProgress = true
                                        scanningStatusText = "Memproses sampel e-KTP & auto-fill formulir..."
                                        scope.launch {
                                            val ocrRes = KtpOcrScanner.generateSampleKtpCardAndScan(context)
                                            isScanningInProgress = false
                                            applyOcrResultToForm(ocrRes, includeLowConfidenceUnder50 = true)
                                        }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF065F46),
                                        contentColor = Color(0xFFA7F3D0)
                                    ),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("btn_scan_ktp_sample")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("3. Uji Sampel", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Reading / Processing Animation
                    if (isScanningInProgress) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3456)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
                                Text(
                                    text = scanningStatusText.ifBlank { "Google Gemini Vision sedang membaca struktur e-KTP Indonesia..." },
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Auto-crop batas kartu • Koreksi perspektif • Peningkatan ketajaman tulisan • Validasi JSON",
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    // =========================================================================
                    // SECTION F: TAMPILAN PREVIEW OCR YANG SEDERHANA ("STATUS PEMBACAAN KTP")
                    // Jangan menampilkan teks mentah yang berantakan.
                    // Sediakan tombol: Scan Ulang KTP, Periksa Hasil AI, Isi Form Pelanggan, Simpan Pelanggan
                    // =========================================================================
                    if (latestOcrResult != null || fotoKtpDepanUri.isNotBlank()) {
                        val ocr = latestOcrResult
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ocr_verification_preview_card")
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SeaBluePrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "STATUS PEMBACAAN KTP",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = NavyDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (ocr != null) {
                                        Surface(
                                            color = if (ocr.statusPembacaan == "berhasil") StatusPaidGreenBg else Color(0xFFFEF3C7),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (ocr.statusPembacaan == "berhasil") "BERHASIL" else "PERLU VERIFIKASI",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (ocr.statusPembacaan == "berhasil") StatusPaidGreen else Color(0xFFB45309),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // Section 11.A & 11.B: Preview Foto KTP Asli & Foto KTP Hasil Preprocessing
                                val ktpBitmap = remember(fotoKtpDepanUri) {
                                    if (fotoKtpDepanUri.isNotBlank()) {
                                        try {
                                            if (fotoKtpDepanUri.startsWith("content://")) {
                                                context.contentResolver.openInputStream(Uri.parse(fotoKtpDepanUri))?.use {
                                                    BitmapFactory.decodeStream(it)
                                                }
                                            } else if (File(fotoKtpDepanUri).exists()) {
                                                BitmapFactory.decodeFile(fotoKtpDepanUri)
                                            } else {
                                                null
                                            }
                                        } catch (_: Exception) {
                                            null
                                        }
                                    } else null
                                }
                                val preprocessedUri = ocr?.savedPreprocessedPhotoPath.orEmpty()
                                val preprocessedBitmap = remember(preprocessedUri) {
                                    if (preprocessedUri.isNotBlank() && File(preprocessedUri).exists()) {
                                        try {
                                            BitmapFactory.decodeFile(preprocessedUri)
                                        } catch (_: Exception) {
                                            null
                                        }
                                    } else null
                                }

                                if (ktpBitmap != null || preprocessedBitmap != null) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (ktpBitmap != null) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(135.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.Black)
                                                    .testTag("preview_ktp_original_photo")
                                            ) {
                                                Image(
                                                    bitmap = ktpBitmap.asImageBitmap(),
                                                    contentDescription = "Foto KTP Asli",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Surface(
                                                    color = NavyDark.copy(alpha = 0.82f),
                                                    shape = RoundedCornerShape(bottomEnd = 8.dp)
                                                ) {
                                                    Text(
                                                        text = "A. Foto KTP Asli",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (preprocessedBitmap != null) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(135.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.Black)
                                                    .testTag("preview_ktp_preprocessed_photo")
                                            ) {
                                                Image(
                                                    bitmap = preprocessedBitmap.asImageBitmap(),
                                                    contentDescription = "Foto KTP Hasil Preprocessing",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Surface(
                                                    color = Color(0xFF065F46).copy(alpha = 0.88f),
                                                    shape = RoundedCornerShape(bottomEnd = 8.dp)
                                                ) {
                                                    Text(
                                                        text = "B. Hasil Preprocessing (${ocr?.selectedImageVariant ?: "CLAHE"})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (ocr != null) {
                                    // Indikator kualitas gambar jika ada masalah (buram, gelap, terlalu jauh, terpotong, silau)
                                    if (ocr.qualityReport.qualityIssues.isNotEmpty()) {
                                        Surface(
                                            color = Color(0xFFFEF2F2),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "⚠ Indikator Kualitas Gambar: ${ocr.qualityReport.qualityIssues.joinToString(", ")}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StatusOverdueRed,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = ocr.qualityReport.retakePromptMessage,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF7F1D1D)
                                                )
                                            }
                                        }
                                    }

                                    // Section H: Error Handling jika OCR gagal (jangan hapus foto, tampilkan alasan, opsi Foto Ulang & Input Manual)
                                    if (ocr.statusPembacaan == "gagal" || ocr.failureReason.isNotBlank()) {
                                        Surface(
                                            color = Color(0xFFFEF2F2),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ocr_failure_error_card")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "⚠ Pembacaan Otomatis Gagal (Foto KTP Tetap Tersimpan)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = StatusOverdueRed,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Alasan Kegagalan: ${ocr.failureReason.ifBlank { "Karakter pada KTP kurang jelas atau tertutup pantulan cahaya." }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF7F1D1D)
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Button(
                                                        onClick = { showAiScanOptionDialog = true },
                                                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .testTag("btn_ocr_error_foto_ulang")
                                                    ) {
                                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Foto Ulang", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            isOcrVerifiedByAdmin = true
                                                            ocrSuccessBanner = "Mode Input Manual diaktifkan. Foto KTP asli tetap tersimpan, silakan lengkapi kolom formulir di bawah."
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .testTag("btn_ocr_error_input_manual")
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Input Manual", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Ringkasan Sederhana Status Pembacaan Per Kolom Utama (Section F Confidence System)
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Hasil Ekstraksi & Confidence (${ocr.aiSourceEngine})",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = SeaBluePrimary,
                                                fontWeight = FontWeight.Bold
                                            )

                                            @Composable
                                            fun StatusRowItem(
                                                jsonKey: String,
                                                label: String,
                                                value: String,
                                                confidenceScore: Int,
                                                onManualCorrectionClick: () -> Unit
                                            ) {
                                                // Section F:
                                                // >= 90%: Otomatis terisi dan ditandai hijau
                                                // 70%-89%: Terisi tetapi memerlukan verifikasi admin
                                                // < 70%: Jangan dianggap data valid; tampilkan tombol koreksi manual
                                                // Kosong: Tampilkan status TIDAK TERBACA
                                                val isGreen90 = value.isNotBlank() && confidenceScore >= 90
                                                val isYellow70To89 = value.isNotBlank() && confidenceScore in 70..89
                                                val isBelow70 = value.isNotBlank() && confidenceScore < 70
                                                val statusText = when {
                                                    isGreen90 -> "HIJAU • OTOMATIS ($confidenceScore%)"
                                                    isYellow70To89 -> "PERLU VERIFIKASI ($confidenceScore%)"
                                                    isBelow70 -> "RAGU <70% ($confidenceScore%)"
                                                    else -> "TIDAK TERBACA"
                                                }
                                                val badgeBg = when {
                                                    isGreen90 -> StatusPaidGreenBg
                                                    isYellow70To89 -> Color(0xFFFEF3C7)
                                                    else -> Color(0xFFFEF2F2)
                                                }
                                                val badgeColor = when {
                                                    isGreen90 -> StatusPaidGreen
                                                    isYellow70To89 -> Color(0xFFB45309)
                                                    else -> StatusOverdueRed
                                                }

                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "$label: ${value.ifBlank { "TIDAK TERBACA" }}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = NavyDark,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Surface(
                                                            color = badgeBg,
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = statusText,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = badgeColor,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    if (isBelow70 || value.isBlank()) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(top = 2.dp),
                                                            horizontalArrangement = Arrangement.End
                                                        ) {
                                                            TextButton(
                                                                onClick = onManualCorrectionClick,
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                                modifier = Modifier
                                                                    .height(26.dp)
                                                                    .testTag("btn_koreksi_manual_$jsonKey")
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Edit,
                                                                    contentDescription = null,
                                                                    tint = StatusOverdueRed,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "Koreksi Manual",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = StatusOverdueRed,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            StatusRowItem("nik", "NIK", ocr.nik, ocr.confidence.nik) {
                                                manuallyCorrectedFields = manuallyCorrectedFields + "nik"
                                                if (nik.isBlank() && ocr.nik.isNotBlank()) nik = ocr.nik
                                                ocrSuccessBanner = "Silakan koreksi manual kolom 1. NIK KTP pada formulir di bawah."
                                            }
                                            StatusRowItem("nama", "Nama", ocr.namaLengkap, ocr.confidence.namaLengkap) {
                                                manuallyCorrectedFields = manuallyCorrectedFields + "nama"
                                                if (namaLengkap.isBlank() && ocr.namaLengkap.isNotBlank()) namaLengkap = ocr.namaLengkap
                                                ocrSuccessBanner = "Silakan koreksi manual kolom 2. Nama Lengkap pada formulir di bawah."
                                            }
                                            StatusRowItem(
                                                "tempat_tanggal_lahir",
                                                "Tempat/Tgl Lahir",
                                                "${ocr.tempatLahir.ifBlank { "-" }}, ${ocr.tanggalLahir.ifBlank { "-" }}",
                                                minOf(ocr.confidence.tempatLahir, ocr.confidence.tanggalLahir)
                                            ) {
                                                manuallyCorrectedFields = manuallyCorrectedFields + setOf("tempat_lahir", "tanggal_lahir")
                                                if (tempatLahir.isBlank() && ocr.tempatLahir.isNotBlank()) tempatLahir = ocr.tempatLahir
                                                if (tanggalLahir.isBlank() && ocr.tanggalLahir.isNotBlank()) tanggalLahir = ocr.tanggalLahir
                                                ocrSuccessBanner = "Silakan koreksi manual kolom Tempat & Tanggal Lahir pada formulir di bawah."
                                            }
                                            StatusRowItem("jenis_kelamin", "Jenis Kelamin", ocr.jenisKelamin, ocr.confidence.jenisKelamin) {
                                                manuallyCorrectedFields = manuallyCorrectedFields + "jenis_kelamin"
                                                if (ocr.jenisKelamin.isNotBlank()) jenisKelamin = ocr.jenisKelamin
                                                ocrSuccessBanner = "Silakan pilih/koreksi Jenis Kelamin pada formulir di bawah."
                                            }
                                            StatusRowItem(
                                                "alamat",
                                                "Alamat",
                                                "${ocr.alamat.ifBlank { "-" }} (RT ${ocr.rt.ifBlank { "-" }}/RW ${ocr.rw.ifBlank { "-" }}, Kel. ${ocr.kelurahanDesa.ifBlank { "-" }}, Kec. ${ocr.kecamatan.ifBlank { "-" }})",
                                                ocr.confidence.alamat
                                            ) {
                                                manuallyCorrectedFields = manuallyCorrectedFields + setOf("alamat", "rt", "rw", "kelurahan", "kecamatan")
                                                if (alamatLengkap.isBlank() && ocr.alamat.isNotBlank()) alamatLengkap = ocr.alamat
                                                if (rt.isBlank() && ocr.rt.isNotBlank()) rt = ocr.rt
                                                if (rw.isBlank() && ocr.rw.isNotBlank()) rw = ocr.rw
                                                if (kelurahanDesa.isBlank() && ocr.kelurahanDesa.isNotBlank()) kelurahanDesa = ocr.kelurahanDesa
                                                if (kecamatan.isBlank() && ocr.kecamatan.isNotBlank()) kecamatan = ocr.kecamatan
                                                ocrSuccessBanner = "Silakan koreksi manual kolom Alamat, RT/RW, Kelurahan, dan Kecamatan di bawah."
                                            }
                                        }
                                    }

                                    // Peringatan Validasi & Field Confidence 70%-89% (Warna Kuning)
                                    if (ocr.lowConfidenceFields.isNotEmpty() || ocr.validationWarnings.isNotEmpty()) {
                                        Surface(
                                            color = Color(0xFFFFFBEB),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                if (ocr.lowConfidenceFields.isNotEmpty()) {
                                                    Text(
                                                        text = "⚠ Field Confidence < 90% (Memerlukan Verifikasi Admin): ${ocr.lowConfidenceFields.joinToString(", ")}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFB45309),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                ocr.validationWarnings.forEach { warn ->
                                                    Text(
                                                        text = "• $warn",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF92400E)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Field Belum Terbaca / Confidence < 70%
                                    if (ocr.unreadableFields.isNotEmpty() || ocr.manualCorrectionFields.isNotEmpty()) {
                                        Surface(
                                            color = Color(0xFFFEF2F2),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "ℹ Kolom TIDAK TERBACA / Confidence < 70% (Gunakan Tombol Koreksi Manual): ${(ocr.unreadableFields + ocr.manualCorrectionFields).distinct().joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StatusOverdueRed,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }

                                    // Section G: Konfirmasi Verifikasi Admin (Wajib agar data OCR yang belum diverifikasi tidak disimpan sebagai data final)
                                    Surface(
                                        color = if (isOcrVerifiedByAdmin) StatusPaidGreenBg else Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                isOcrVerifiedByAdmin = !isOcrVerifiedByAdmin
                                                validationError = null
                                            }
                                            .testTag("card_admin_verify_ocr")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Checkbox(
                                                checked = isOcrVerifiedByAdmin,
                                                onCheckedChange = {
                                                    isOcrVerifiedByAdmin = it
                                                    validationError = null
                                                },
                                                modifier = Modifier.testTag("checkbox_admin_verify_ocr")
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (isOcrVerifiedByAdmin) "✓ Data Hasil OCR Telah Diverifikasi Admin" else "⚠ Wajib Verifikasi Admin Sebelum Simpan Final",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isOcrVerifiedByAdmin) StatusPaidGreen else Color(0xFFB45309)
                                                )
                                                Text(
                                                    text = "Pastikan seluruh informasi KTP pada formulir telah sesuai sebelum disimpan sebagai data pelanggan.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = NavyDark
                                                )
                                            }
                                        }
                                    }

                                    // Panel inspeksi JSON terstruktur & Diagnostik Gemini (ketika admin menekan "Periksa Hasil AI")
                                    if (showStructuredJsonInspection) {
                                        val diagReport = KtpGeminiDiagnosticWrapper.lastReport
                                        Surface(
                                            color = NavyDark,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = "HASIL EKSTRAKSI JSON TERSTRUKTUR (SECTION D):",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF38BDF8),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = ocr.toStructuredJsonString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontSize = 10.sp
                                                )

                                                if (diagReport != null) {
                                                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))
                                                    Text(
                                                        text = "LAPORAN DIAGNOSTIK GEMINI VISION API (${diagReport.status}):",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (diagReport.isSuccess) Color(0xFF34D399) else Color(0xFFF87171),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Waktu: ${diagReport.formattedTime} • Latensi: ${diagReport.durationMs}ms • Model: ${diagReport.modelName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 10.sp
                                                    )

                                                    if (!diagReport.rawResponseText.isNullOrBlank()) {
                                                        Text(
                                                            text = "Raw Response Gemini (Panjang: ${diagReport.rawResponseText.length}):",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFBAE6FD),
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            text = diagReport.rawResponseText,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFE2E8F0),
                                                            fontSize = 10.sp
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "Raw Response Gemini: <KOSONG / TIDAK ADA TEKS>",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFFCA5A5),
                                                            fontSize = 10.sp
                                                        )
                                                    }

                                                    if (diagReport.exceptionClass != null || diagReport.exceptionMessage != null) {
                                                        Text(
                                                            text = "Detail Kesalahan / Exception:",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFF87171),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "Kelas: ${diagReport.exceptionClass.orEmpty()}\nPesan: ${diagReport.exceptionMessage.orEmpty()}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFFECACA),
                                                            fontSize = 10.sp
                                                        )
                                                        if (!diagReport.stackTracePreview.isNullOrBlank()) {
                                                            Text(
                                                                text = "StackTrace:\n${diagReport.stackTracePreview}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color(0xFF94A3B8),
                                                                fontSize = 9.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (ocrSuccessBanner != null) {
                                    Text(
                                        text = ocrSuccessBanner!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF065F46),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // 4 Tombol Aksi Wajib sesuai Section F & G:
                                // 1. Scan Ulang KTP
                                // 2. Periksa Hasil AI
                                // 3. Isi Form Pelanggan
                                // 4. Simpan Pelanggan
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { showAiScanOptionDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_rescan_ktp_ocr")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scan Ulang KTP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            showStructuredJsonInspection = !showStructuredJsonInspection
                                            ocrSuccessBanner = "Silakan periksa hasil ekstraksi AI dan koreksi kolom berwarna kuning jika diperlukan."
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_verify_ktp_ocr")
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Periksa Hasil AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            if (ocr != null) {
                                                applyOcrResultToForm(ocr, includeLowConfidenceUnder50 = false)
                                                ocrSuccessBanner = "Seluruh kolom yang valid (Confidence >= 70%) telah diisi otomatis ke Form Pelanggan!"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_autofill_customer_form")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Isi Form Pelanggan", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        enabled = duplicateCustomer == null && persetujuanDataKtp && isOcrVerifiedByAdmin,
                                        onClick = { triggerSaveCustomerAction() },
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusPaidGreen),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_save_customer_from_ocr_preview")
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Simpan Pelanggan", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Section 14: Tombol Mode Diagnostik OCR (Developer) untuk menampilkan 12 indikator pipeline OCR
                                if (ocr != null) {
                                    OutlinedButton(
                                        onClick = { showOcrDiagnosticDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDark),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_open_ocr_diagnostic")
                                    ) {
                                        Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Mode Diagnostik OCR (6 Varian Gambar • Bounding Box • JSON Section 7 • ${ocr.totalProcessingTimeMs}ms)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Deteksi NIK / Nomor WhatsApp Ganda (Konfirmasi Pengguna Wajib)
                    val shownDuplicateCustomer = duplicateNikCustomer ?: duplicatePhoneCustomer
                    if (shownDuplicateCustomer != null) {
                        val dupReason = if (duplicateNikCustomer != null) "NIK $nik" else "Nomor WhatsApp $normalizedInputPhone"
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("duplicate_nik_warning_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusOverdueRed)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Deteksi Pelanggan Ganda: $dupReason Sudah Terdaftar!",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = StatusOverdueRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "$dupReason sudah pernah terdaftar atas nama '${shownDuplicateCustomer.namaLengkap}' (${shownDuplicateCustomer.kodePelanggan.ifBlank { "ID #${shownDuplicateCustomer.id}" }} • WA: ${shownDuplicateCustomer.nomorHp}). Pilih tindakan:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF7F1D1D)
                                )
                                if (duplicateNikCustomer == null && duplicatePhoneCustomer != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = confirmDuplicatePhoneOverride,
                                            onCheckedChange = { confirmDuplicatePhoneOverride = it },
                                            modifier = Modifier.testTag("checkbox_confirm_duplicate_wa")
                                        )
                                        Text(
                                            text = "Konfirmasi: Tetap simpan pelanggan baru dengan nomor WhatsApp ini",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF7F1D1D)
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (onViewExistingCustomerDetail != null) {
                                                onViewExistingCustomerDetail(shownDuplicateCustomer)
                                            } else {
                                                viewingDuplicateCustomerPopup = shownDuplicateCustomer
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_dup_lihat_pelanggan_lama")
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Lihat Pelanggan Lama", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = {
                                            if (onSwitchToExistingCustomer != null) {
                                                onSwitchToExistingCustomer(shownDuplicateCustomer)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_dup_gunakan_pelanggan_lama")
                                    ) {
                                        Text("Gunakan Data Lama", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOverdueRed),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_dup_batalkan_input_baru")
                                ) {
                                    Text("Batalkan Input Baru", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusOverdueRed,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "3. FORM EDIT & VERIFIKASI DATA KTP PELANGGAN",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )

                    val yellowVerifyBg = Color(0xFFFFFBEB)
                    val nikNeedsYellow = latestOcrResult?.needsYellowVerification("nik") == true
                    val namaNeedsYellow = latestOcrResult?.needsYellowVerification("nama_lengkap") == true
                    val tempatNeedsYellow = latestOcrResult?.needsYellowVerification("tempat_lahir") == true
                    val tglNeedsYellow = latestOcrResult?.needsYellowVerification("tanggal_lahir") == true
                    val alamatNeedsYellow = latestOcrResult?.needsYellowVerification("alamat") == true

                    // 1. NIK (16 Digit) + Validation Indicator
                    SmartCaseOutlinedTextField(
                        value = nik,
                        onValueChange = {
                            nik = it.filter { c -> c.isDigit() }.take(16)
                            validationError = null
                        },
                        caseMode = InputTextCaseMode.PRESERVE,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = {
                            Text(
                                when {
                                    isNik16Valid && !nikNeedsYellow -> "1. NIK KTP (16 Digit Valid ✓)"
                                    nikNeedsYellow -> "1. NIK KTP (⚠ Perlu Verifikasi Admin - ${nik.length}/16)"
                                    else -> "1. NIK KTP (Wajib 16 Digit Angka - Saat ini ${nik.length}/16)"
                                }
                            )
                        },
                        isError = nik.isNotEmpty() && !isNik16Valid,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (nikNeedsYellow) yellowVerifyBg else Color.Transparent, RoundedCornerShape(8.dp))
                            .testTag("input_customer_nik")
                    )

                    // 2. Nama Lengkap & Nomor WhatsApp (Auto CapsLock + Tombol Import Kontak)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = namaLengkap,
                            onValueChange = {
                                namaLengkap = it
                                validationError = null
                            },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = {
                                Text(
                                    if (namaNeedsYellow) "2. Nama Lengkap (⚠ Perlu Verifikasi)"
                                    else "2. Nama Lengkap (Sesuai KTP)"
                                )
                            },
                            isError = latestOcrResult != null && namaLengkap.isBlank(),
                            modifier = Modifier
                                .weight(1.1f)
                                .background(if (namaNeedsYellow) yellowVerifyBg else Color.Transparent, RoundedCornerShape(8.dp))
                                .testTag("input_customer_name")
                        )
                        SmartCaseOutlinedTextField(
                            value = nomorHp,
                            onValueChange = {
                                nomorHp = it
                                validationError = null
                            },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            label = { Text("Nomor WhatsApp") },
                            placeholder = { Text("08xxxxxxxxxx") },
                            trailingIcon = {
                                WhatsAppContactPickerTrailingButton(
                                    onClick = { launchWhatsAppContactPicker() },
                                    modifier = Modifier.testTag("btn_import_wa_contact_customer")
                                )
                            },
                            isError = duplicatePhoneCustomer != null && !confirmDuplicatePhoneOverride,
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_customer_phone")
                        )
                    }
                    if (!contactImportNotice.isNullOrBlank()) {
                        Text(
                            text = contactImportNotice ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary,
                            modifier = Modifier.testTag("text_contact_import_notice_customer")
                        )
                    }

                    // 3. Tempat Lahir (dipisahkan) & 4. Tanggal Lahir (dipisahkan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = tempatLahir,
                            onValueChange = { tempatLahir = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = {
                                Text(
                                    if (tempatNeedsYellow) "3. Tempat Lahir (⚠ Verifikasi)"
                                    else "3. Tempat Lahir (Dipisahkan)"
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (tempatNeedsYellow) yellowVerifyBg else Color.Transparent, RoundedCornerShape(8.dp))
                                .testTag("input_customer_birthplace")
                        )
                        SmartCaseOutlinedTextField(
                            value = tanggalLahir,
                            onValueChange = { tanggalLahir = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            label = {
                                Text(
                                    if (tglNeedsYellow) "4. Tgl Lahir (⚠ DD-MM-YYYY)"
                                    else "4. Tgl Lahir (DD-MM-YYYY)"
                                )
                            },
                            isError = tanggalLahir.isNotBlank() && !KtpOcrScanner.isValidBirthDate(tanggalLahir),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (tglNeedsYellow) yellowVerifyBg else Color.Transparent, RoundedCornerShape(8.dp))
                                .testTag("input_customer_birthdate")
                        )
                    }

                    // 5. Jenis Kelamin & Golongan Darah
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1.4f)) {
                            Text("5. Jenis Kelamin:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Laki-laki", "Perempuan").forEach { jk ->
                                    FilterChip(
                                        selected = jenisKelamin.equals(jk, ignoreCase = true),
                                        onClick = { jenisKelamin = jk },
                                        label = { Text(jk) }
                                    )
                                }
                            }
                        }
                        SmartCaseOutlinedTextField(
                            value = golonganDarah,
                            onValueChange = { golonganDarah = it.take(3) },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Gol. Darah") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.6f)
                                .testTag("input_customer_blood_type")
                        )
                    }

                    // 6. Alamat KTP
                    SmartCaseOutlinedTextField(
                        value = alamatLengkap,
                        onValueChange = { alamatLengkap = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = {
                            Text(
                                if (alamatNeedsYellow) "6. Alamat Sesuai KTP (⚠ Perlu Verifikasi)"
                                else "6. Alamat Sesuai KTP"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (alamatNeedsYellow) yellowVerifyBg else Color.Transparent, RoundedCornerShape(8.dp))
                            .testTag("input_customer_address")
                    )

                    // 7. RT (dipisahkan), 8. RW (dipisahkan), 9. Kelurahan/Desa
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = rt,
                            onValueChange = { rt = it.filter { ch -> ch.isDigit() }.take(3) },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("7. RT") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.6f)
                                .testTag("input_customer_rt")
                        )
                        SmartCaseOutlinedTextField(
                            value = rw,
                            onValueChange = { rw = it.filter { ch -> ch.isDigit() }.take(3) },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("8. RW") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.6f)
                                .testTag("input_customer_rw")
                        )
                        SmartCaseOutlinedTextField(
                            value = kelurahanDesa,
                            onValueChange = { kelurahanDesa = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("9. Kelurahan / Desa") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("input_customer_kelurahan")
                        )
                    }

                    // 10. Kecamatan, 11. Kabupaten/Kota, & 12. Provinsi
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = kecamatan,
                            onValueChange = { kecamatan = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("10. Kecamatan") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_customer_kecamatan")
                        )
                        SmartCaseOutlinedTextField(
                            value = kabupatenKota,
                            onValueChange = { kabupatenKota = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("11. Kabupaten / Kota") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_customer_kabupaten_kota")
                        )
                    }

                    SmartCaseOutlinedTextField(
                        value = provinsi,
                        onValueChange = { provinsi = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("12. Provinsi Sesuai KTP") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_customer_provinsi")
                    )

                    // 13. Agama & 14. Status Perkawinan
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = agama,
                            onValueChange = { agama = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("13. Agama") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_customer_agama")
                        )
                        SmartCaseOutlinedTextField(
                            value = statusPerkawinan,
                            onValueChange = { statusPerkawinan = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("14. Status Perkawinan") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_customer_marital")
                        )
                    }

                    // 15. Pekerjaan, Kewarganegaraan, & 16. Berlaku Hingga
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = pekerjaan,
                            onValueChange = { pekerjaan = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("15. Pekerjaan") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_customer_job")
                        )
                        SmartCaseOutlinedTextField(
                            value = berlakuHingga,
                            onValueChange = { berlakuHingga = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("16. Berlaku Hingga") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_customer_valid_until")
                        )
                        SmartCaseOutlinedTextField(
                            value = kewarganegaraan,
                            onValueChange = { kewarganegaraan = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("WN") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.55f)
                                .testTag("input_customer_citizenship")
                        )
                    }

                    // SECTION 3: FITUR ACTIVE GPS LOCATION TAGGING & LIVE LOCATION MAP
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        text = "ACTIVE GPS LOCATION TAGGING (LOKASI PENGGUNAAN FREEZER)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SeaBluePrimary
                    )
                    Text(
                        text = "Deteksi posisi GPS perangkat secara real-time, sesuaikan marker secara manual bila diperlukan, lalu klik SIMPAN TITIK LOKASI untuk mengunci koordinat lokasi pelanggan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    GpsLocationMapCard(
                        latitude = latDefaultText.toDoubleOrNull(),
                        longitude = lonDefaultText.toDoubleOrNull(),
                        addressText = lokasiPenggunaanDefault.ifBlank { alamatLengkap },
                        dateStr = tanggalRegistrasi,
                        timeStr = "",
                        onPinMoved = { pickedLat, pickedLon ->
                            latDefaultText = String.format(java.util.Locale.US, "%.6f", pickedLat)
                            lonDefaultText = String.format(java.util.Locale.US, "%.6f", pickedLon)
                            linkGoogleMapsDefault = LocationGpsHelper.formatGoogleMapsUrl(pickedLat, pickedLon)
                            scope.launch {
                                val detailed = LocationGpsHelper.reverseGeocodeStructured(context, pickedLat, pickedLon)
                                if (detailed.fullAddress.isNotBlank() && lokasiPenggunaanDefault.isBlank()) {
                                    lokasiPenggunaanDefault = detailed.fullAddress
                                }
                                if (detailed.kelurahanDesa.isNotBlank() && kelurahanLokasiDefault.isBlank()) {
                                    kelurahanLokasiDefault = detailed.kelurahanDesa
                                }
                                if (detailed.kecamatan.isNotBlank() && kecamatanLokasiDefault.isBlank()) {
                                    kecamatanLokasiDefault = detailed.kecamatan
                                }
                                if (detailed.kabupatenKota.isNotBlank() && kotaKabupatenLokasiDefault.isBlank()) {
                                    kotaKabupatenLokasiDefault = detailed.kabupatenKota
                                }
                            }
                        },
                        onAddressResolved = { fullAddr, kel, kec, kab ->
                            if (fullAddr.isNotBlank()) lokasiPenggunaanDefault = fullAddr
                            if (kel.isNotBlank()) kelurahanLokasiDefault = kel
                            if (kec.isNotBlank()) kecamatanLokasiDefault = kec
                            if (kab.isNotBlank()) kotaKabupatenLokasiDefault = kab
                        },
                        onSaveLocation = { savedLat, savedLon, savedAddr ->
                            latDefaultText = String.format(java.util.Locale.US, "%.6f", savedLat)
                            lonDefaultText = String.format(java.util.Locale.US, "%.6f", savedLon)
                            linkGoogleMapsDefault = LocationGpsHelper.formatGoogleMapsUrl(savedLat, savedLon)
                            if (savedAddr.isNotBlank() && lokasiPenggunaanDefault.isBlank()) {
                                lokasiPenggunaanDefault = savedAddr
                            }
                        }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = namaLokasiDefault,
                            onValueChange = { namaLokasiDefault = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Nama Lokasi (Outlet/Toko/Rumah)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = rtRwLokasiDefault,
                            onValueChange = { rtRwLokasiDefault = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("RT/RW Lokasi") },
                            singleLine = true,
                            modifier = Modifier.weight(0.65f)
                        )
                    }

                    SmartCaseOutlinedTextField(
                        value = lokasiPenggunaanDefault,
                        onValueChange = { lokasiPenggunaanDefault = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Alamat Lengkap Lokasi Penggunaan Freezer") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_customer_usage_location")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = kelurahanLokasiDefault,
                            onValueChange = { kelurahanLokasiDefault = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Kelurahan/Desa Lokasi") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = kecamatanLokasiDefault,
                            onValueChange = { kecamatanLokasiDefault = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Kecamatan Lokasi") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = kotaKabupatenLokasiDefault,
                            onValueChange = { kotaKabupatenLokasiDefault = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Kota/Kab Lokasi") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmartCaseOutlinedTextField(
                            value = latDefaultText,
                            onValueChange = {
                                latDefaultText = it
                                val la = it.toDoubleOrNull()
                                val lo = lonDefaultText.toDoubleOrNull()
                                if (la != null && lo != null) {
                                    linkGoogleMapsDefault = LocationGpsHelper.formatGoogleMapsUrl(la, lo)
                                }
                            },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = { Text("Latitude GPS") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SmartCaseOutlinedTextField(
                            value = lonDefaultText,
                            onValueChange = {
                                lonDefaultText = it
                                val la = latDefaultText.toDoubleOrNull()
                                val lo = it.toDoubleOrNull()
                                if (la != null && lo != null) {
                                    linkGoogleMapsDefault = LocationGpsHelper.formatGoogleMapsUrl(la, lo)
                                }
                            },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = { Text("Longitude GPS") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SmartCaseOutlinedTextField(
                        value = linkGoogleMapsDefault,
                        onValueChange = { linkGoogleMapsDefault = it },
                        caseMode = InputTextCaseMode.PRESERVE,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        label = { Text("Link Peta (OpenStreetMap / GPS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SmartCaseOutlinedTextField(
                        value = catatan,
                        onValueChange = { catatan = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text("Catatan Pelanggan") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Section 7: Customer Consent Checkbox
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Checkbox(
                                checked = persetujuanDataKtp,
                                onCheckedChange = { persetujuanDataKtp = it }
                            )
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = SeaBluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pelanggan menyetujui pengambilan & penyimpanan foto KTP asli serta lokasi penggunaan freezer untuk keperluan administrasi sewa.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Dokumentasi Foto KTP Pelanggan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Unggah 1 foto KTP bagian depan (Kamera / Galeri).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val custFolder = "cust_${nik.ifBlank { "new" }}"
                    PhotoDocumentationSlot(
                        label = "Foto KTP Bagian Depan",
                        photoKey = "ktp_depan",
                        transactionOrEntityFolder = custFolder,
                        currentUriOrPath = fotoKtpDepanUri,
                        isMandatory = true,
                        onPhotoSaved = { fotoKtpDepanUri = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (initialCustomer != null) {
                        OutlinedButton(
                            onClick = { onDelete(initialCustomer) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = StatusOverdueRed)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus", color = StatusOverdueRed)
                        }
                    }
                    Button(
                        enabled = duplicateCustomer == null && persetujuanDataKtp,
                        onClick = {
                            triggerSaveCustomerAction()
                        },
                        modifier = Modifier
                            .weight(2f)
                            .testTag("btn_save_customer")
                    ) {
                        Text("SIMPAN DATA PELANGGAN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (viewingDuplicateCustomerPopup != null) {
        val dup = viewingDuplicateCustomerPopup!!
        CustomerDetailAndMultiRentalDialog(
            customer = dup,
            rentals = emptyList(),
            onDismiss = { viewingDuplicateCustomerPopup = null },
            onEdit = {
                viewingDuplicateCustomerPopup = null
                onSwitchToExistingCustomer?.invoke(dup)
            }
        )
    }

    if (showOcrScannerDialog) {
        KtpOcrScannerDialog(
            onDismiss = { showOcrScannerDialog = false },
            onKtpScanned = { ocrResult ->
                showOcrScannerDialog = false
                applyOcrResultToForm(ocrResult)
            }
        )
    }

    if (showOcrDiagnosticDialog && latestOcrResult != null) {
        KtpOcrDiagnosticDialog(
            ocrResult = latestOcrResult!!,
            onDismiss = { showOcrDiagnosticDialog = false }
        )
    }

    if (showAiScanOptionDialog) {
        AlertDialog(
            onDismissRequest = { showAiScanOptionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = SeaBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📷 Scan KTP dengan AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pilih metode pengambilan atau pengujian dokumen e-KTP pelanggan:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = Color(0xFFF0F9FF),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAiScanOptionDialog = false
                                directCameraOcrLauncher.launch(null)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("1. Kamera HP (Foto Langsung)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Buka aplikasi kamera bawaan HP untuk menjepret KTP", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                            }
                        }
                    }

                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAiScanOptionDialog = false
                                showOcrScannerDialog = true
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("2. Scanner Kartu KTP (Panduan Bingkai)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Pemindai interaktif dengan bingkai kartu dan deteksi kualitas", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                            }
                        }
                    }

                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAiScanOptionDialog = false
                                directGalleryOcrLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("3. Upload Foto KTP dari Galeri", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Pilih file gambar KTP JPG/PNG dari galeri HP", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                            }
                        }
                    }

                    Surface(
                        color = Color(0xFFECFDF5),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAiScanOptionDialog = false
                                isScanningInProgress = true
                                scanningStatusText = "Menghasilkan sampel e-KTP demo & auto-fill formulir..."
                                scope.launch {
                                    val ocrRes = KtpOcrScanner.generateSampleKtpCardAndScan(context)
                                    isScanningInProgress = false
                                    applyOcrResultToForm(ocrRes, includeLowConfidenceUnder50 = true)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("4. Uji Sampel e-KTP Demo (Otomatis)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF065F46))
                                Text("Simulasi e-KTP lengkap untuk verifikasi cepat di emulator", style = MaterialTheme.typography.labelSmall, color = Color(0xFF047857))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAiScanOptionDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
