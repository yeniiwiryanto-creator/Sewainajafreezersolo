package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerPhotoEntity
import com.example.data.local.DrivePhotoMetadataEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.location.GpsLocationResult
import com.example.location.LocationGpsHelper
import com.example.storage.CustomerPhotoStorageHelper
import com.example.ui.components.FreezerUnitThumbnail
import com.example.ui.components.GpsLocationMapCard
import com.example.ui.components.InputTextCaseMode
import com.example.ui.components.LocationDocPhotoDisplayItem
import com.example.ui.components.LocationDocSlots
import com.example.ui.components.LocationFreezerDocumentationCard
import com.example.ui.components.MultiMarkerGoogleMapView
import com.example.ui.components.SmartCaseOutlinedTextField
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.TextNavActive
import com.example.ui.theme.TextNavInactive
import com.example.ui.components.FormValidationAlertBanner
import com.example.util.DateCurrencyUtils
import com.example.util.FormValidationEngine
import com.example.util.FormValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Section 1 (Revised): Simplified "LOKASI PENGGUNAAN FREEZER" screen.
 * Only two primary sections:
 * A. TAGGING LOKASI GPS (Ambil Lokasi GPS, Latitude & Longitude otomatis, Peta Google Maps, Buka Google Maps, Perbarui Titik Lokasi)
 * B. ALAMAT PENGGUNAAN FREEZER (Diisi otomatis via Reverse Geocoding atau diedit manual, terpisah dari Alamat KTP Pelanggan)
 */
@Composable
fun UsageLocationScreen(
    transactions: List<RentalTransactionEntity>,
    customers: List<CustomerEntity>,
    units: List<FreezerUnitEntity>,
    onCaptureGpsLocation: ((GpsLocationResult) -> Unit) -> Unit,
    onUpdateTransactionLocation: (RentalTransactionEntity) -> Unit,
    customerPhotos: List<CustomerPhotoEntity> = emptyList(),
    drivePhotoMetadataList: List<DrivePhotoMetadataEntity> = emptyList(),
    isUploadingPhoto: Boolean = false,
    onSaveLocationPhoto: (CustomerEntity, RentalTransactionEntity, String, Any, Double?, Double?) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteCustomerPhoto: (CustomerPhotoEntity) -> Unit = {},
    onRetryLocationPhotoUpload: (CustomerPhotoEntity) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingTxLocation by remember { mutableStateOf<RentalTransactionEntity?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedMapTxId by remember { mutableStateOf<Long?>(null) }

    val transactionsWithGps = remember(transactions) {
        transactions.filter { it.latitude != null && it.longitude != null }
    }

    val filteredTransactions = remember(transactions, customers, searchQuery) {
        transactions.filter { tx ->
            val cust = customers.find { it.id == tx.customerId }
            searchQuery.isBlank() ||
                tx.namaPelangganSnapshot.contains(searchQuery, ignoreCase = true) ||
                tx.nomorInvoice.contains(searchQuery, ignoreCase = true) ||
                tx.kodeUnitSnapshot.contains(searchQuery, ignoreCase = true) ||
                tx.alamatLokasiGps.contains(searchQuery, ignoreCase = true) ||
                (cust?.nik?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("usage_location_screen")
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
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = LightBlueAccent,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LOKASI PENGGUNAAN FREEZER",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tagging Koordinat GPS & Alamat Penggunaan Freezer (Terpisah dari Alamat KTP)",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightBlueAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Row: Daftar Unit vs Peta Sebaran Solo Raya
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = PureWhite,
            contentColor = SeaBluePrimary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                selectedContentColor = TextNavActive,
                unselectedContentColor = TextNavInactive,
                modifier = Modifier.background(if (selectedTab == 0) SeaBluePrimary else PureWhite),
                text = { Text("📋 Daftar Unit (${filteredTransactions.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                selectedContentColor = TextNavActive,
                unselectedContentColor = TextNavInactive,
                modifier = Modifier.background(if (selectedTab == 1) SeaBluePrimary else PureWhite),
                text = { Text("🗺️ Peta Solo Raya (${transactionsWithGps.size})", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedTab == 0) {
            SmartCaseOutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                caseMode = InputTextCaseMode.UPPERCASE,
                placeholder = { Text("Cari Pelanggan, No. Transaksi, Kode Unit, atau Alamat...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari Lokasi") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_usage_location_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredTransactions, key = { it.id }) { tx ->
                    val customer = customers.find { it.id == tx.customerId }
                    val unit = units.find { it.id == tx.unitId }
                    UsageLocationItemCard(
                        tx = tx,
                        customer = customer,
                        unit = unit,
                        onEditLocation = { editingTxLocation = tx }
                    )
                }
            }
        } else {
            PetaSebaranSoloRayaView(
                transactionsWithGps = transactionsWithGps,
                customers = customers,
                units = units,
                selectedTxId = selectedMapTxId,
                onSelectTransaction = { selectedMapTxId = it },
                onEditLocation = { tx -> editingTxLocation = tx }
            )
        }
    }

    if (editingTxLocation != null) {
        val currentTx = editingTxLocation!!
        val matchedCustomer = customers.find { it.id == currentTx.customerId }
        val matchedUnit = units.find { it.id == currentTx.unitId }
        UsageLocationEditorDialog(
            tx = currentTx,
            customer = matchedCustomer,
            unit = matchedUnit,
            customerPhotos = customerPhotos,
            drivePhotoMetadataList = drivePhotoMetadataList,
            isUploadingPhoto = isUploadingPhoto,
            onSaveLocationPhoto = { docSlot, source, lat, lon ->
                val resolvedCustomer = matchedCustomer ?: CustomerEntity(
                    id = currentTx.customerId,
                    namaLengkap = currentTx.namaPelangganSnapshot,
                    nik = "",
                    nomorHp = currentTx.nomorWhatsappLokasi,
                    alamatLengkap = currentTx.alamatLokasiGps,
                    jenisKelamin = "Laki-laki",
                    tanggalRegistrasi = DateCurrencyUtils.todayIso()
                )
                onSaveLocationPhoto(resolvedCustomer, currentTx, docSlot, source, lat, lon)
            },
            onDeleteCustomerPhoto = onDeleteCustomerPhoto,
            onRetryLocationPhotoUpload = onRetryLocationPhotoUpload,
            onDismiss = { editingTxLocation = null },
            onCaptureGpsLocation = onCaptureGpsLocation,
            onSave = { updatedTx ->
                onUpdateTransactionLocation(updatedTx)
                editingTxLocation = null
            }
        )
    }
}

@Composable
private fun PetaSebaranSoloRayaView(
    transactionsWithGps: List<RentalTransactionEntity>,
    customers: List<CustomerEntity>,
    units: List<FreezerUnitEntity>,
    selectedTxId: Long?,
    onSelectTransaction: (Long) -> Unit,
    onEditLocation: (RentalTransactionEntity) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val activeTx = transactionsWithGps.find { it.id == selectedTxId }
        ?: transactionsWithGps.firstOrNull()
    val activeCust = customers.find { it.id == activeTx?.customerId }
    val activeUnit = units.find { it.id == activeTx?.unitId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Peta Sebaran Freezer Solo Raya",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Google Maps SDK: Surakarta, Solo Baru, Kartasura, Palur, Colomadu, Sukoharjo, Klaten, Boyolali, Sragen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = SeaBluePrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${transactionsWithGps.size} Unit",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SeaBluePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Multi Marker Google Map Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    MultiMarkerGoogleMapView(
                        transactionsWithGps = transactionsWithGps,
                        onSelectTransaction = onSelectTransaction,
                        selectedTxId = activeTx?.id,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusPaidGreen))
                        Text("Lunas", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusWarningYellow))
                        Text("DP / Belum Lunas", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusOverdueRed))
                        Text("Terlambat", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SeaBluePrimary))
                        Text("Dipilih", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 Tip: Ketuk salah satu pin pada peta Google Maps atau pilih unit dari daftar chip di bawah untuk melihat rincian & navigasi turn-by-turn.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (transactionsWithGps.isNotEmpty()) {
            // Horizontal unit selection chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                transactionsWithGps.forEach { tx ->
                    val isSelected = activeTx?.id == tx.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTransaction(tx.id) },
                        label = {
                            Text(
                                "${tx.kodeUnitSnapshot} • ${tx.namaPelangganSnapshot.take(12)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SeaBluePrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Detailed Selected Unit Card
            if (activeTx != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FreezerUnitThumbnail(
                                photoUriOrPath = activeTx.fotoUnitSebelumKirimUri.ifBlank { activeUnit?.fotoUnitUri ?: "" },
                                jenisUnit = activeUnit?.jenisUnit ?: activeTx.namaUnitSnapshot,
                                kapasitasLiter = activeUnit?.kapasitasLiter ?: 200,
                                size = 54.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeTx.namaPelangganSnapshot,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${activeTx.nomorInvoice} • ${activeTx.kodeUnitSnapshot} (${activeTx.namaUnitSnapshot})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SeaBluePrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                color = StatusPaidGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = activeTx.statusPembayaran.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusPaidGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Alamat Penggunaan Freezer:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = activeTx.alamatLokasiGps.ifBlank { "Lokasi Solo Raya (Koordinat Tertandai)" },
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = String.format(Locale.US, "Koordinat GPS: %.6f, %.6f", activeTx.latitude ?: 0.0, activeTx.longitude ?: 0.0),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SeaBluePrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Actions for Selected Unit
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (activeTx.latitude != null && activeTx.longitude != null) {
                                        LocationGpsHelper.openInGoogleMaps(
                                            context = context,
                                            lat = activeTx.latitude!!,
                                            lon = activeTx.longitude!!,
                                            label = "Freezer ${activeTx.kodeUnitSnapshot} - ${activeTx.namaPelangganSnapshot}"
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka Maps", style = MaterialTheme.typography.labelMedium)
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (activeTx.latitude != null && activeTx.longitude != null) {
                                        LocationGpsHelper.openTurnByTurnNavigation(
                                            context = context,
                                            lat = activeTx.latitude!!,
                                            lon = activeTx.longitude!!
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Navigasi", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clip = ClipData.newPlainText("Koordinat GPS", "${activeTx.latitude}, ${activeTx.longitude}")
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Koordinat disalin: ${activeTx.latitude}, ${activeTx.longitude}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salin Koordinat", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { onEditLocation(activeTx) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.EditLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Lokasi", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Belum Ada Unit dengan Koordinat GPS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Silakan buka tab 'Daftar Unit' lalu tekan tombol 'Perbarui Lokasi' untuk mengambil titik GPS freezer dan menampilkannya di peta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun UsageLocationItemCard(
    tx: RentalTransactionEntity,
    customer: CustomerEntity?,
    unit: FreezerUnitEntity?,
    onEditLocation: () -> Unit
) {
    val context = LocalContext.current
    val hasGps = tx.latitude != null && tx.longitude != null
    val mapsLink = if (hasGps) {
        LocationGpsHelper.formatGoogleMapsUrl(tx.latitude!!, tx.longitude!!)
    } else {
        tx.linkGoogleMaps.ifBlank {
            "https://maps.google.com/?q=${Uri.encode(tx.alamatLokasiGps.ifBlank { "Surakarta" })}"
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("usage_location_card_${tx.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    FreezerUnitThumbnail(
                        photoUriOrPath = tx.fotoUnitSebelumKirimUri.ifBlank { unit?.fotoUnitUri ?: "" },
                        jenisUnit = unit?.jenisUnit ?: tx.namaUnitSnapshot,
                        kapasitasLiter = unit?.kapasitasLiter ?: 200,
                        size = 50.dp
                    )
                    Column {
                        Text(
                            text = tx.namaPelangganSnapshot,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${tx.nomorInvoice} • ${tx.kodeUnitSnapshot} (${tx.namaUnitSnapshot})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeaBluePrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    color = if (hasGps) StatusPaidGreen.copy(alpha = 0.14f) else Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (hasGps) "GPS TERTANDAI" else "BELUM GPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasGps) StatusPaidGreen else Color(0xFFD97706),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Perbandingan Alamat KTP vs Alamat Penggunaan Freezer
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Alamat KTP Pelanggan: ${customer?.alamatLengkap ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Alamat Penggunaan Freezer: ${tx.alamatLokasiGps.ifBlank { "Belum diisi" }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (hasGps) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "Koordinat GPS: %.6f, %.6f",
                        tx.latitude,
                        tx.longitude
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = SeaBluePrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onEditLocation,
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_update_usage_location_${tx.id}")
                ) {
                    Icon(Icons.Default.EditLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Perbarui Lokasi", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsLink)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buka Google Maps", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (hasGps) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            LocationGpsHelper.openTurnByTurnNavigation(context, tx.latitude!!, tx.longitude!!)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Navigasi Maps", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            val clip = ClipData.newPlainText("Koordinat GPS", "${tx.latitude}, ${tx.longitude}")
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(clip)
                            Toast.makeText(context, "Koordinat disalin: ${tx.latitude}, ${tx.longitude}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salin Koordinat", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun UsageLocationEditorDialog(
    tx: RentalTransactionEntity,
    customer: CustomerEntity?,
    unit: FreezerUnitEntity?,
    customerPhotos: List<CustomerPhotoEntity> = emptyList(),
    drivePhotoMetadataList: List<DrivePhotoMetadataEntity> = emptyList(),
    isUploadingPhoto: Boolean = false,
    onSaveLocationPhoto: (String, Any, Double?, Double?) -> Unit = { _, _, _, _ -> },
    onDeleteCustomerPhoto: (CustomerPhotoEntity) -> Unit = {},
    onRetryLocationPhotoUpload: (CustomerPhotoEntity) -> Unit = {},
    onDismiss: () -> Unit,
    onCaptureGpsLocation: ((GpsLocationResult) -> Unit) -> Unit,
    onSave: (RentalTransactionEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Only two primary data blocks per Section 1 Revision:
    // A. Tagging Lokasi GPS (Latitude & Longitude)
    // B. Alamat Penggunaan Freezer (Separate from Alamat KTP)
    var latInput by remember { mutableStateOf(tx.latitude?.let { String.format(Locale.US, "%.6f", it) } ?: "") }
    var lonInput by remember { mutableStateOf(tx.longitude?.let { String.format(Locale.US, "%.6f", it) } ?: "") }
    var akurasiMeter by remember { mutableStateOf(tx.akurasiGpsMeter) }
    var alamatPenggunaan by remember { mutableStateOf(tx.alamatLokasiGps) }
    var tanggalLokasiGps by remember { mutableStateOf(tx.tanggalLokasiGps.ifBlank { DateCurrencyUtils.todayIso() }) }
    var jamLokasiGps by remember { mutableStateOf(tx.jamLokasiGps.ifBlank { DateCurrencyUtils.nowTimeDisplay() }) }
    var locationValidationResult by remember { mutableStateOf<FormValidationResult?>(null) }
    val locationScrollState = rememberScrollState()

    fun applyGpsResult(res: GpsLocationResult) {
        if (res.isSuccess && res.latitude != null && res.longitude != null) {
            latInput = String.format(Locale.US, "%.6f", res.latitude)
            lonInput = String.format(Locale.US, "%.6f", res.longitude)
            akurasiMeter = res.accuracyMeters
            if (res.addressText.isNotBlank()) {
                alamatPenggunaan = res.addressText
            }
            if (res.dateIso.isNotBlank()) tanggalLokasiGps = res.dateIso
            if (res.timeStr.isNotBlank()) jamLokasiGps = res.timeStr
        }
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onCaptureGpsLocation { applyGpsResult(it) }
        }
    }

    fun requestGpsNow() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            onCaptureGpsLocation { applyGpsResult(it) }
        } else {
            gpsPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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
                    .verticalScroll(locationScrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "LOKASI PENGGUNAAN FREEZER",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
                Text(
                    text = "Transaksi: ${tx.nomorInvoice} • Unit: ${tx.kodeUnitSnapshot} (${unit?.namaUnit ?: tx.namaUnitSnapshot})",
                    style = MaterialTheme.typography.bodySmall,
                    color = SeaBluePrimary,
                    fontWeight = FontWeight.SemiBold
                )
                FormValidationAlertBanner(
                    validationResult = locationValidationResult,
                    testTagName = "usage_location_validation_banner"
                )

                // Comparison Box showing KTP Address (read-only) vs Freezer Usage Address
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ALAMAT KTP PELANGGAN (Data Terpisah):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "${customer?.namaLengkap ?: tx.namaPelangganSnapshot} — ${customer?.alamatLengkap ?: "-"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // A. TAGGING LOKASI GPS
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "A. TAGGING LOKASI GPS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )

                        Button(
                            onClick = { requestGpsNow() },
                            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_ambil_lokasi_gps")
                        ) {
                            Icon(Icons.Default.GpsFixed, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📍 AMBIL LOKASI GPS", fontWeight = FontWeight.Bold)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SmartCaseOutlinedTextField(
                                value = latInput,
                                onValueChange = { latInput = it },
                                caseMode = InputTextCaseMode.PRESERVE,
                                label = { Text("Latitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            SmartCaseOutlinedTextField(
                                value = lonInput,
                                onValueChange = { lonInput = it },
                                caseMode = InputTextCaseMode.PRESERVE,
                                label = { Text("Longitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = { requestGpsNow() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Perbarui Titik Lokasi", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    val latD = latInput.toDoubleOrNull()
                                    val lonD = lonInput.toDoubleOrNull()
                                    val url = if (latD != null && lonD != null) {
                                        LocationGpsHelper.formatGoogleMapsUrl(latD, lonD)
                                    } else {
                                        "https://maps.google.com/?q=${Uri.encode(alamatPenggunaan.ifBlank { "Surakarta" })}"
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Buka Google Maps", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        val latDVal = latInput.toDoubleOrNull()
                        val lonDVal = lonInput.toDoubleOrNull()
                        if (latDVal != null && lonDVal != null) {
                            OutlinedButton(
                                onClick = {
                                    LocationGpsHelper.openTurnByTurnNavigation(context, latDVal, lonDVal)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Navigasi Turn-by-Turn Google Maps", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Interactive Map with Draggable Pin
                GpsLocationMapCard(
                    latitude = latInput.toDoubleOrNull(),
                    longitude = lonInput.toDoubleOrNull(),
                    addressText = alamatPenggunaan,
                    dateStr = tanggalLokasiGps,
                    timeStr = jamLokasiGps,
                    accuracyMeters = akurasiMeter,
                    onPinMoved = { newLat, newLon ->
                        latInput = String.format(Locale.US, "%.6f", newLat)
                        lonInput = String.format(Locale.US, "%.6f", newLon)
                        scope.launch {
                            val structured = withContext(Dispatchers.IO) {
                                LocationGpsHelper.reverseGeocodeStructured(context, newLat, newLon)
                            }
                            if (structured.fullAddress.isNotBlank()) {
                                alamatPenggunaan = structured.fullAddress
                            }
                        }
                    }
                )

                // B. ALAMAT PENGGUNAAN FREEZER
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "B. ALAMAT PENGGUNAAN FREEZER",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Alamat diisi otomatis berdasarkan lokasi GPS (reverse geocoding) dan dapat diedit secara manual oleh admin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SmartCaseOutlinedTextField(
                            value = alamatPenggunaan,
                            onValueChange = { alamatPenggunaan = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Alamat Penggunaan Freezer *") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_usage_full_address")
                        )
                    }
                }

                // C. DOKUMENTASI FREEZER DI LOKASI & TAMPAK DEPAN LOKASI
                val relevantLocationPhotos = remember(customerPhotos, tx.id, tx.customerId) {
                    customerPhotos.filter { photo ->
                        (photo.transactionId == tx.id ||
                            (photo.customerId == tx.customerId && photo.kategori == CustomerPhotoStorageHelper.KAT_FOTO_LOKASI))
                    }
                }
                val freezerLocItems = remember(relevantLocationPhotos, drivePhotoMetadataList) {
                    relevantLocationPhotos
                        .filter { it.namaDokumentasi.contains("Freezer", ignoreCase = true) || it.kategori == CustomerPhotoStorageHelper.KAT_FOTO_UNIT }
                        .map { photo ->
                            val driveMeta = drivePhotoMetadataList.firstOrNull {
                                it.transactionId == tx.id && it.customerId == tx.customerId && it.fileName.contains("UNIT", true)
                            }
                            LocationDocPhotoDisplayItem(
                                id = photo.id.toString(),
                                docSlot = LocationDocSlots.SLOT_FREEZER_DI_LOKASI,
                                localPath = photo.filePath,
                                capturedAtIso = photo.tanggalWaktuIso,
                                latitude = photo.latitude,
                                longitude = photo.longitude,
                                uploadStatus = photo.uploadStatus.ifBlank { driveMeta?.syncStatus ?: "PENDING" },
                                persistedEntity = photo
                            )
                        }
                }
                val frontLocItems = remember(relevantLocationPhotos, drivePhotoMetadataList) {
                    relevantLocationPhotos
                        .filter { it.namaDokumentasi.contains("Tampak Depan", ignoreCase = true) || (it.namaDokumentasi.contains("Lokasi", ignoreCase = true) && !it.namaDokumentasi.contains("Freezer", ignoreCase = true)) }
                        .map { photo ->
                            val driveMeta = drivePhotoMetadataList.firstOrNull {
                                it.transactionId == tx.id && it.customerId == tx.customerId
                            }
                            LocationDocPhotoDisplayItem(
                                id = photo.id.toString(),
                                docSlot = LocationDocSlots.SLOT_TAMPAK_DEPAN_LOKASI,
                                localPath = photo.filePath,
                                capturedAtIso = photo.tanggalWaktuIso,
                                latitude = photo.latitude,
                                longitude = photo.longitude,
                                uploadStatus = photo.uploadStatus.ifBlank { driveMeta?.syncStatus ?: "PENDING" },
                                persistedEntity = photo
                            )
                        }
                }

                LocationFreezerDocumentationCard(
                    freezerLocationPhotos = freezerLocItems,
                    frontLocationPhotos = frontLocItems,
                    drivePhotoMetadataList = drivePhotoMetadataList,
                    customerLabel = customer?.namaLengkap ?: tx.namaPelangganSnapshot,
                    unitLabel = unit?.namaUnit ?: tx.namaUnitSnapshot,
                    currentLatitude = latInput.toDoubleOrNull(),
                    currentLongitude = lonInput.toDoubleOrNull(),
                    isUploading = isUploadingPhoto,
                    onAddPhotoFromSource = { docSlot, src ->
                        onSaveLocationPhoto(
                            docSlot,
                            src,
                            latInput.toDoubleOrNull(),
                            lonInput.toDoubleOrNull()
                        )
                    },
                    onDeletePhoto = { item ->
                        item.persistedEntity?.let { onDeleteCustomerPhoto(it) }
                    },
                    onRetryUpload = { item ->
                        item.persistedEntity?.let { onRetryLocationPhotoUpload(it) }
                    }
                )

                // Action Buttons
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
                            val latD = latInput.toDoubleOrNull()
                            val lonD = lonInput.toDoubleOrNull()
                            val res = FormValidationEngine.validateUsageLocationForm(
                                customerId = tx.customerId.takeIf { it > 0L } ?: 1L,
                                alamatLengkap = alamatPenggunaan,
                                latitude = latD,
                                longitude = lonD
                            )
                            if (!res.isValid) {
                                locationValidationResult = res
                                scope.launch { locationScrollState.animateScrollTo(0) }
                                return@Button
                            }
                            locationValidationResult = null
                            val computedMapsLink = if (latD != null && lonD != null) {
                                LocationGpsHelper.formatGoogleMapsUrl(latD, lonD)
                            } else {
                                ""
                            }
                            val firstFreezerUri = freezerLocItems.firstOrNull()?.localPath ?: tx.fotoFreezerDiLokasiUri
                            val firstFrontUri = frontLocItems.firstOrNull()?.localPath ?: tx.fotoDepanLokasiUri
                            val updated = tx.copy(
                                alamatLokasiGps = alamatPenggunaan.trim(),
                                latitude = latD,
                                longitude = lonD,
                                akurasiGpsMeter = akurasiMeter,
                                linkGoogleMaps = computedMapsLink,
                                tanggalLokasiGps = tanggalLokasiGps,
                                jamLokasiGps = jamLokasiGps,
                                fotoFreezerDiLokasiUri = firstFreezerUri,
                                fotoDepanLokasiUri = firstFrontUri
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_usage_location")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan Lokasi GPS", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
