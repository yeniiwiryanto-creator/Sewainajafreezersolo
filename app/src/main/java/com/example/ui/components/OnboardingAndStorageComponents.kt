package com.example.ui.components

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.contacts.ContactAccessManager
import com.example.contacts.ContactPermissionState
import com.example.data.local.LocalBackupEntity
import com.example.storage.DeviceStorageSummary
import com.example.storage.LocalStorageAndBackupManager
import com.example.storage.ManagedDocumentCategory
import com.example.storage.PermissionCategoryStatus
import com.example.storage.PermissionIndicatorLevel
import com.example.storage.StorageIntegrityReport
import com.example.storage.UnifiedManagedDocumentItem
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusWarningYellow
import java.io.File

/**
 * Helper warna untuk indikator status izin:
 * HIJAU = AKTIF
 * KUNING = PERLU PERSETUJUAN
 * MERAH = TIDAK AKTIF
 */
fun permissionIndicatorColors(level: PermissionIndicatorLevel): Pair<Color, Color> {
    return when (level) {
        PermissionIndicatorLevel.HIJAU -> Color(0xFF059669) to Color(0xFFD1FAE5)
        PermissionIndicatorLevel.KUNING -> Color(0xFFD97706) to Color(0xFFFEF3C7)
        PermissionIndicatorLevel.MERAH -> Color(0xFFDC2626) to Color(0xFFFEE2E2)
    }
}

fun permissionCategoryIcon(code: String): ImageVector {
    return when (code.uppercase()) {
        "CAMERA" -> Icons.Default.CameraAlt
        "GALLERY" -> Icons.Default.PhotoLibrary
        "LOCATION" -> Icons.Default.LocationOn
        else -> Icons.Default.Storage
    }
}

/**
 * B. FIRST INSTALLATION ONBOARDING DIALOG
 * Ditampilkan ketika aplikasi pertama kali dibuka setelah instalasi.
 * Mematuhi prinsip izin bertahap: menjelaskan manfaat 4 kategori izin tanpa memaksa semua sekaligus.
 */
@Composable
fun FirstInstallOnboardingDialog(
    permissionStatuses: List<PermissionCategoryStatus>,
    onApproveAndContinue: () -> Unit,
    onSetupLater: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            LocalStorageAndBackupManager.markPermissionDeniedByUser(context, "CAMERA")
        }
        onRefreshPermissions()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val anyGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!anyGranted) {
            LocalStorageAndBackupManager.markPermissionDeniedByUser(context, "LOCATION")
        }
        onRefreshPermissions()
    }

    Dialog(
        onDismissRequest = onSetupLater,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("first_install_onboarding_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Biru Profesional
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NavyDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SeaBluePrimary.copy(alpha = 0.25f))
                                    .border(1.5.dp, LightBlueAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "Selamat Datang",
                                    tint = LightBlueAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "SELAMAT DATANG",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.testTag("onboarding_title")
                                )
                                Text(
                                    text = "SEWAIN AJA FREEZER SOC",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = LightBlueAccent,
                                    modifier = Modifier.testTag("onboarding_subtitle")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Untuk memberikan pengalaman penggunaan aplikasi yang optimal, kami membutuhkan izin akses beberapa fitur perangkat untuk mendukung pengelolaan pelanggan, dokumentasi, transaksi, dan lokasi pengiriman.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE0F2FE)
                        )
                    }
                }

                // Daftar 4 Kategori Izin Perangkat
                permissionStatuses.forEach { cat ->
                    val (fgColor, bgColor) = permissionIndicatorColors(cat.indicator)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_perm_card_${cat.code}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(SeaBluePrimary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = permissionCategoryIcon(cat.code),
                                            contentDescription = cat.title,
                                            tint = SeaBluePrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = cat.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = bgColor,
                                    border = BorderStroke(1.dp, fgColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = cat.indicator.badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = fgColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = cat.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF334155)
                            )

                            // Tombol aktivasi individual bertahap (opsional)
                            if (cat.code == "CAMERA" && cat.indicator != PermissionIndicatorLevel.HIJAU) {
                                OutlinedButton(
                                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Izinkan Kamera", style = MaterialTheme.typography.labelSmall)
                                }
                            } else if (cat.code == "LOCATION" && cat.indicator != PermissionIndicatorLevel.HIJAU) {
                                OutlinedButton(
                                    onClick = {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Izinkan Lokasi GPS", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE0F2FE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Catatan Privasi: Izin diminta secara bertahap saat fitur digunakan. Seluruh foto KTP, dokumen, dan database disimpan secara lokal & aman di dalam perangkat Anda.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NavyDark,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Tombol Aksi Utama & Alternatif
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onSetupLater,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_onboarding_setup_later")
                    ) {
                        Text("ATUR NANTI", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onApproveAndContinue,
                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("btn_onboarding_approve_continue")
                    ) {
                        Text("SETUJUI & LANJUTKAN", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/**
 * F. UNIFIED CAMERA & GALLERY SERVICE CARD
 * Komponen terpadu yang menyediakan:
 * 1. AMBIL FOTO (Kamera)
 * 2. PILIH DARI GALERI (Android Photo Picker)
 * Dilengkapi preview gambar, ambil ulang, validasi format/ukuran, dan salin permanen ke SEWAIN_AJA_FREEZER/.
 */
@Composable
fun UnifiedCameraGalleryServiceCard(
    title: String,
    subtitle: String,
    currentImagePath: String = "",
    onCaptureFromCameraBitmap: (Bitmap) -> Unit,
    onPickFromGalleryUri: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var validationError by remember { mutableStateOf<String?>(null) }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            validationError = null
            onCaptureFromCameraBitmap(bitmap)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            validationError = null
            cameraPreviewLauncher.launch(null)
        } else {
            LocalStorageAndBackupManager.markPermissionDeniedByUser(context, "CAMERA")
            validationError = "Izin kamera ditolak. Anda tetap dapat memilih foto dari Galeri atau membuka Pengaturan Perangkat."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            validationError = null
            onPickFromGalleryUri(uri)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NavyDark
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF334155)
            )

            if (currentImagePath.isNotBlank()) {
                val fileExists = remember(currentImagePath) {
                    currentImagePath.startsWith("content://") ||
                        currentImagePath.startsWith("file://") ||
                        File(currentImagePath).exists()
                }
                if (fileExists) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.06f))
                    ) {
                        AsyncImage(
                            model = if (currentImagePath.startsWith("/")) File(currentImagePath) else currentImagePath,
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "FILE TIDAK DITEMUKAN",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusOverdueRed,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            if (validationError != null) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = validationError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF92400E)
                        )
                        OutlinedButton(
                            onClick = { LocalStorageAndBackupManager.openDeviceAppSettings(context) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Buka Pengaturan Perangkat", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_unified_ambil_foto")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentImagePath.isBlank()) "AMBIL FOTO" else "AMBIL ULANG",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_unified_pilih_galeri")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PILIH DARI GALERI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * K.5. SUBMENU PENGATURAN -> IZIN AKSES PERANGKAT
 */
@Composable
fun DevicePermissionsSettingsCard(
    permissionStatuses: List<PermissionCategoryStatus>,
    onboardingDecision: String,
    onRefreshPermissions: () -> Unit,
    onShowOnboardingAgain: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var contactsStatusInfo by remember {
        mutableStateOf(ContactAccessManager.evaluatePermissionState(context))
    }
    var showContactsRequestPromptDialog by remember { mutableStateOf(false) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ContactAccessManager.markPermissionGranted(context)
        } else {
            ContactAccessManager.markPermissionDenied(context)
        }
        contactsStatusInfo = ContactAccessManager.evaluatePermissionState(context)
        onRefreshPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                contactsStatusInfo = ContactAccessManager.evaluatePermissionState(context)
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) LocalStorageAndBackupManager.markPermissionDeniedByUser(context, "CAMERA")
        onRefreshPermissions()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!ok) LocalStorageAndBackupManager.markPermissionDeniedByUser(context, "LOCATION")
        onRefreshPermissions()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_device_permissions_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = SeaBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "5. Izin Akses Perangkat (Android 10–16)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyDark
                        )
                        Text(
                            text = "Status Onboarding: $onboardingDecision • Tersimpan di tabel APP_PERMISSION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = {
                        contactsStatusInfo = ContactAccessManager.evaluatePermissionState(context)
                        onRefreshPermissions()
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Perbarui Status Izin")
                }
            }

            permissionStatuses.forEach { cat ->
                val (fgColor, bgColor) = permissionIndicatorColors(cat.indicator)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = permissionCategoryIcon(cat.code),
                                    contentDescription = null,
                                    tint = SeaBluePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = cat.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = bgColor,
                                border = BorderStroke(1.dp, fgColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = cat.indicator.badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = fgColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = cat.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "Pemeriksaan terakhir: ${cat.lastChecked}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cat.code == "CAMERA" && cat.indicator != PermissionIndicatorLevel.HIJAU) {
                                Button(
                                    onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Aktifkan Kamera", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (cat.code == "LOCATION" && cat.indicator != PermissionIndicatorLevel.HIJAU) {
                                Button(
                                    onClick = {
                                        locationLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Aktifkan Lokasi GPS", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { LocalStorageAndBackupManager.openDeviceLocationSettings(context) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Pengaturan GPS", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // ==================== 5. AKSES KONTAK TELEPON ANDROID ====================
            val (contactsFgColor, contactsBgColor) = when (contactsStatusInfo.state) {
                ContactPermissionState.DIIZINKAN -> Color(0xFF059669) to Color(0xFFD1FAE5)
                ContactPermissionState.BELUM_DIIZINKAN -> Color(0xFFD97706) to Color(0xFFFEF3C7)
                ContactPermissionState.IZIN_DITOLAK -> Color(0xFFDC2626) to Color(0xFFFEE2E2)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0F9FF),
                border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_contacts_permission_setting")
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = "Akses Kontak Telepon",
                                tint = SeaBluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "5. Akses Kontak Telepon",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = contactsBgColor,
                                    border = BorderStroke(1.dp, contactsFgColor.copy(alpha = 0.4f)),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = contactsStatusInfo.state.displayText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = contactsFgColor,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("badge_contacts_permission_status")
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = contactsStatusInfo.state == ContactPermissionState.DIIZINKAN,
                            onCheckedChange = { shouldEnable ->
                                if (shouldEnable) {
                                    if (contactsStatusInfo.osPermissionGranted) {
                                        ContactAccessManager.setAppContactsToggleEnabled(context, true)
                                        contactsStatusInfo = ContactAccessManager.evaluatePermissionState(context)
                                    } else {
                                        showContactsRequestPromptDialog = true
                                    }
                                } else {
                                    ContactAccessManager.setAppContactsToggleEnabled(context, false)
                                    contactsStatusInfo = ContactAccessManager.evaluatePermissionState(context)
                                }
                            },
                            modifier = Modifier.testTag("switch_contacts_permission")
                        )
                    }

                    Text(
                        text = contactsStatusInfo.detailMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF334155)
                    )

                    if (contactsStatusInfo.state != ContactPermissionState.DIIZINKAN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showContactsRequestPromptDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("btn_request_contacts_permission")
                            ) {
                                Text("Aktifkan Akses Kontak", style = MaterialTheme.typography.labelSmall)
                            }
                            if (contactsStatusInfo.state == ContactPermissionState.IZIN_DITOLAK) {
                                OutlinedButton(
                                    onClick = { LocalStorageAndBackupManager.openDeviceAppSettings(context) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Pengaturan Android", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            if (showContactsRequestPromptDialog) {
                AlertDialog(
                    onDismissRequest = { showContactsRequestPromptDialog = false },
                    title = { Text("Izinkan Akses Kontak Telepon?") },
                    text = {
                        Text(
                            "Aplikasi membutuhkan izin akses kontak hanya untuk memudahkan import nomor WhatsApp dan nama pelanggan ke dalam form. " +
                                "Apabila tidak diizinkan, aplikasi tetap dapat digunakan sepenuhnya dengan mengetik nomor WhatsApp secara manual."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showContactsRequestPromptDialog = false
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                            modifier = Modifier.testTag("btn_confirm_contacts_permission_dialog")
                        ) {
                            Text("Izinkan Sekarang")
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (contactsStatusInfo.state == ContactPermissionState.IZIN_DITOLAK) {
                                TextButton(
                                    onClick = {
                                        showContactsRequestPromptDialog = false
                                        LocalStorageAndBackupManager.openDeviceAppSettings(context)
                                    }
                                ) {
                                    Text("Buka Pengaturan")
                                }
                            }
                            TextButton(
                                onClick = { showContactsRequestPromptDialog = false }
                            ) {
                                Text("Batal / Input Manual")
                            }
                        }
                    }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { LocalStorageAndBackupManager.openDeviceAppSettings(context) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_open_device_app_settings")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pengaturan Perangkat", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onShowOnboardingAgain,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_reopen_onboarding")
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tampilkan Onboarding", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * H & K.6. SUBMENU PENGATURAN -> DOCUMENT MANAGEMENT
 * Menampilkan 6 kategori dokumen, fitur Lihat/Unduh/Bagikan/Hapus dengan konfirmasi,
 * serta tombol CHECK STORAGE INTEGRITY.
 */
@Composable
fun DocumentManagementSettingsCard(
    documents: List<UnifiedManagedDocumentItem>,
    integrityReport: StorageIntegrityReport?,
    onRunIntegrityCheck: () -> Unit,
    onUploadNewDocumentBitmap: (ManagedDocumentCategory, Bitmap) -> Unit,
    onUploadNewDocumentUri: (ManagedDocumentCategory, Uri) -> Unit,
    onShareDocument: (UnifiedManagedDocumentItem) -> Unit,
    onDeleteDocumentConfirmed: (UnifiedManagedDocumentItem) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<ManagedDocumentCategory?>(null) }
    var uploadCategoryTarget by remember { mutableStateOf(ManagedDocumentCategory.DOKUMEN_PELANGGAN) }
    var previewingDoc by remember { mutableStateOf<UnifiedManagedDocumentItem?>(null) }
    var deletingDoc by remember { mutableStateOf<UnifiedManagedDocumentItem?>(null) }

    val filteredDocs = remember(documents, selectedCategory) {
        if (selectedCategory == null) documents else documents.filter { it.category == selectedCategory }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_document_management_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = SeaBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "6. Document Management (${documents.size} Dokumen)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyDark
                        )
                        Text(
                            text = "Folder Terstandar: SEWAIN_AJA_FREEZER/ • Checksum SHA-256 Aktif",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tombol CHECK STORAGE INTEGRITY
            Button(
                onClick = onRunIntegrityCheck,
                colors = ButtonDefaults.buttonColors(containerColor = NavyDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_check_storage_integrity")
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = LightBlueAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CHECK STORAGE INTEGRITY",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            // Tampilkan Laporan Integritas jika sudah dijalankan
            if (integrityReport != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (integrityReport.isHealthy) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                    border = BorderStroke(
                        1.dp,
                        if (integrityReport.isHealthy) StatusPaidGreen else StatusOverdueRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("storage_integrity_report_box")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "HASIL CHECK STORAGE INTEGRITY (${integrityReport.checkedAt})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (integrityReport.isHealthy) Color(0xFF065F46) else StatusOverdueRed
                        )
                        Text(
                            text = "• Total File di Penyimpanan: ${integrityReport.totalFilesInStorage} file (${LocalStorageAndBackupManager.formatFileSize(integrityReport.totalSizeBytes)})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Total Record Dokumen SQLite: ${integrityReport.totalDatabaseRecords}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• File Valid (SHA-256 Cocok): ${integrityReport.validFilesCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF065F46),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• File Hilang (FILE TIDAK DITEMUKAN): ${integrityReport.missingFilesCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (integrityReport.missingFilesCount > 0) StatusOverdueRed else Color(0xFF334155)
                        )
                        Text(
                            text = "• File Duplikat (Checksum Identik): ${integrityReport.duplicateFilesCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• File Tanpa Record Database (Orphan): ${integrityReport.orphanFilesCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        integrityReport.missingFileDetails.take(3).forEach { msg ->
                            Text("⚠ $msg", style = MaterialTheme.typography.labelSmall, color = StatusOverdueRed)
                        }
                    }
                }
            }

            // Filter 6 Kategori Dokumen
            Text(
                text = "6 Kategori Dokumen Resmi:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NavyDark
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("Semua (${documents.size})", style = MaterialTheme.typography.labelSmall) }
                )
                ManagedDocumentCategory.entries.forEach { cat ->
                    val count = documents.count { it.category == cat }
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat) null else cat
                            uploadCategoryTarget = cat
                        },
                        label = { Text("${cat.displayName} ($count)", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Kartu Upload Cepat Kamera & Galeri Terpadu ke Kategori Terpilih
            UnifiedCameraGalleryServiceCard(
                title = "Tambah ke ${uploadCategoryTarget.displayName}",
                subtitle = uploadCategoryTarget.description,
                onCaptureFromCameraBitmap = { bmp ->
                    onUploadNewDocumentBitmap(uploadCategoryTarget, bmp)
                },
                onPickFromGalleryUri = { uri ->
                    onUploadNewDocumentUri(uploadCategoryTarget, uri)
                }
            )

            // Daftar Dokumen Terkelola
            if (filteredDocs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Belum ada dokumen tercatat pada kategori ini. Gunakan tombol AMBIL FOTO atau PILIH DARI GALERI di atas untuk menyimpan dokumen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredDocs.take(15).forEach { doc ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (doc.fileExists) Color(0xFFE2E8F0) else StatusOverdueRed
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = doc.fileName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = NavyDark
                                        )
                                        Text(
                                            text = "${doc.category.displayName} • ${doc.ownerLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SeaBluePrimary
                                        )
                                        Text(
                                            text = "Path: ${doc.relativePath}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (doc.fileExists) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                                    ) {
                                        Text(
                                            text = if (doc.fileExists) {
                                                LocalStorageAndBackupManager.formatFileSize(doc.fileSize)
                                            } else {
                                                "FILE TIDAK DITEMUKAN"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (doc.fileExists) Color(0xFF065F46) else StatusOverdueRed,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { previewingDoc = doc },
                                        enabled = doc.fileExists,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Lihat", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = { onShareDocument(doc) },
                                        enabled = doc.fileExists,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Bagikan", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = { deletingDoc = doc },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = StatusOverdueRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hapus", style = MaterialTheme.typography.labelSmall, color = StatusOverdueRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Preview Dokumen
    previewingDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { previewingDoc = null },
            title = {
                Text(doc.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (doc.fileExists && doc.mimeType.startsWith("image")) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.06f))
                        ) {
                            AsyncImage(
                                model = File(doc.absolutePath),
                                contentDescription = doc.fileName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Text("Kategori: ${doc.category.displayName}", style = MaterialTheme.typography.bodySmall)
                    Text("Pemilik: ${doc.ownerLabel}", style = MaterialTheme.typography.bodySmall)
                    Text("Relative Path: ${doc.relativePath}", style = MaterialTheme.typography.bodySmall)
                    Text("Ukuran: ${LocalStorageAndBackupManager.formatFileSize(doc.fileSize)}", style = MaterialTheme.typography.bodySmall)
                    Text("SHA-256: ${doc.checksum.take(24)}...", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                Button(onClick = { previewingDoc = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Dialog Konfirmasi Hapus Dokumen
    deletingDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { deletingDoc = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusOverdueRed) },
            title = { Text("Konfirmasi Hapus Dokumen", fontWeight = FontWeight.Bold) },
            text = {
                Text("Apakah Anda yakin ingin menghapus dokumen '${doc.fileName}' (${doc.ownerLabel})? Tindakan ini memerlukan konfirmasi pengguna.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDocumentConfirmed(doc)
                        deletingDoc = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOverdueRed)
                ) {
                    Text("Ya, Hapus Dokumen")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingDoc = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

/**
 * I & K.7. SUBMENU PENGATURAN -> BACKUP & RESTORE MANAGEMENT
 */
@Composable
fun BackupAndRestoreSettingsCard(
    backupHistory: List<LocalBackupEntity>,
    backupSchedule: String,
    lastRestoreMessage: String?,
    onCreateBackup: (String) -> Unit,
    onRestoreFromBackupEntity: (LocalBackupEntity) -> Unit,
    onRestoreFromExternalZipUri: (Uri) -> Unit,
    onUpdateBackupSchedule: (String) -> Unit,
    onExportCustomersXlsxUri: (Uri) -> Unit = {},
    onExportTransactionsPdfUri: (Uri) -> Unit = {},
    onBackupToGoogleDriveUri: (Uri, String) -> Unit = { _, _ -> },
    onShareBackupToDrive: (String) -> Unit = {}
) {
    var selectedBackupScope by remember { mutableStateOf("FULL_BACKUP") }
    var pendingRestoreEntity by remember { mutableStateOf<LocalBackupEntity?>(null) }
    var pendingExternalRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val restoreZipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingExternalRestoreUri = uri
        }
    }

    val exportCustomersExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri: Uri? ->
        if (uri != null) {
            onExportCustomersXlsxUri(uri)
        }
    }

    val exportTransactionsPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportTransactionsPdfUri(uri)
        }
    }

    val backupGoogleDriveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            onBackupToGoogleDriveUri(uri, selectedBackupScope)
        }
    }

    val scopes = listOf(
        "FULL_BACKUP" to "Seluruh Data Aplikasi (.zip)",
        "DATABASE" to "Database SQLite",
        "CUSTOMER_PHOTOS" to "Foto Pelanggan",
        "KTP_PHOTOS" to "Foto KTP",
        "PAYMENT_PROOFS" to "Bukti Pembayaran",
        "AGREEMENTS" to "Dokumen Perjanjian",
        "FREEZER_DOCS" to "Dokumentasi Freezer"
    )
    val latestBackup = backupHistory.firstOrNull()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_backup_restore_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = SeaBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "7. Backup & Restore Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyDark
                    )
                    Text(
                        text = "Arsip .ZIP dengan manifest.json (Versi DB v14 + Verifikasi Checksum SHA-256)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Ringkasan Waktu & Status Backup Terakhir
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (latestBackup != null) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                border = BorderStroke(
                    1.dp,
                    if (latestBackup != null) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFCBD5E1)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("badge_last_backup_status")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = if (latestBackup != null) Icons.Default.CheckCircle else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (latestBackup != null) Color(0xFF059669) else Color(0xFF64748B)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (latestBackup != null) {
                                "Status Backup Terakhir: Berhasil (${latestBackup.backupType})"
                            } else {
                                "Status Backup Terakhir: Belum Pernah Backup"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = if (latestBackup != null) {
                                "Waktu: ${latestBackup.createdAt} • Ukuran: ${LocalStorageAndBackupManager.formatFileSize(latestBackup.backupSize)}"
                            } else {
                                "Buat backup lokal atau simpan ke Google Drive agar data tetap aman."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            if (!lastRestoreMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE0F2FE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lastRestoreMessage,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyDark,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Text(
                text = "Pilih Cakupan Backup:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NavyDark
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                scopes.forEach { (code, label) ->
                    FilterChip(
                        selected = selectedBackupScope == code,
                        onClick = { selectedBackupScope = code },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Tombol Aksi Backup & Restore Utama
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onCreateBackup(selectedBackupScope) },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_backup_now")
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BACKUP SEKARANG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                }

                OutlinedButton(
                    onClick = {
                        restoreZipPickerLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_restore_data")
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESTORE DATA", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onCreateBackup("DATABASE") },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_export_database")
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BACKUP DATABASE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onCreateBackup("DOCUMENTS") },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_export_all_documents")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BACKUP DOKUMENTASI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            // Export Excel Pelanggan, PDF Transaksi & Google Drive Backup
            HorizontalDivider()
            Text(
                text = "EXPORT LAPORAN & BACKUP KE GOOGLE DRIVE:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NavyDark
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { exportCustomersExcelLauncher.launch("Data_Pelanggan_SAF.xlsx") },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_export_customers_xlsx")
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EXPORT PELANGGAN (.XLSX)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { exportTransactionsPdfLauncher.launch("Laporan_Transaksi_SAF.pdf") },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_export_transactions_pdf")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EXPORT TRANSAKSI (.PDF)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { backupGoogleDriveLauncher.launch("Backup_SEWAinAjaFreezer.zip") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_backup_google_drive")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SIMPAN KE DRIVE (.ZIP)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onShareBackupToDrive(selectedBackupScope) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_share_backup_drive")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("KIRIM KE DRIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            // Jadwal Backup
            HorizontalDivider()
            Text(
                text = "JADWAL BACKUP OTOMATIS:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NavyDark
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "HARIAN_OTOMATIS" to "Harian Otomatis",
                    "MINGGUAN" to "Mingguan",
                    "SAAT_TRANSAKSI_BARU" to "Setiap Transaksi Baru",
                    "MANUAL" to "Manual Saja"
                ).forEach { (schedCode, schedLabel) ->
                    FilterChip(
                        selected = backupSchedule == schedCode,
                        onClick = { onUpdateBackupSchedule(schedCode) },
                        label = { Text(schedLabel, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Riwayat Backup (dari tabel LOCAL_BACKUP)
            HorizontalDivider()
            Text(
                text = "RIWAYAT BACKUP (${backupHistory.size} Arsip Tersimpan):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NavyDark
            )
            if (backupHistory.isEmpty()) {
                Text(
                    text = "Belum ada riwayat backup lokal. Tekan tombol BACKUP SEKARANG untuk membuat arsip backup pertama Anda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    backupHistory.take(10).forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.backupName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                    Text(
                                        text = "${item.backupType} • ${LocalStorageAndBackupManager.formatFileSize(item.backupSize)} • ${item.createdAt}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        text = "Path: ${item.backupPath}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pendingRestoreEntity = item },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Restore", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Konfirmasi Sebelum Restore dari Arsip Lokal
    pendingRestoreEntity?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRestoreEntity = null },
            icon = { Icon(Icons.Default.Restore, contentDescription = null, tint = SeaBluePrimary) },
            title = { Text("Konfirmasi Restore Data", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Sistem akan mengekstrak '${target.backupName}' ke folder sementara dan memvalidasi checksum SHA-256 seluruh file sebelum memulihkan data.\n\nJika ada file yang rusak, restore otomatis dibatalkan agar data lama tetap aman. Lanjutkan restore sekarang?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreFromBackupEntity(target)
                        pendingRestoreEntity = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary)
                ) {
                    Text("Ya, Validasi & Restore")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestoreEntity = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Konfirmasi Sebelum Restore dari File Eksternal / Google Drive
    pendingExternalRestoreUri?.let { externalUri ->
        AlertDialog(
            onDismissRequest = { pendingExternalRestoreUri = null },
            icon = { Icon(Icons.Default.Restore, contentDescription = null, tint = SeaBluePrimary) },
            title = { Text("Konfirmasi Restore Arsip Eksternal", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "File backup eksternal akan diekstrak ke folder sementara dan divalidasi checksum SHA-256-nya sebelum menggantikan database & dokumen aktif. Pastikan arsip berasal dari SEWAin Aja Freezer SOC.\n\nLanjutkan restore sekarang?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreFromExternalZipUri(externalUri)
                        pendingExternalRestoreUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                    modifier = Modifier.testTag("btn_confirm_external_restore")
                ) {
                    Text("Ya, Validasi & Restore")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingExternalRestoreUri = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

/**
 * K.8 & K.9. SUBMENU PENGATURAN -> INFORMASI PENYIMPANAN & TENTANG APLIKASI
 */
@Composable
fun StorageInfoAndAboutSettingsCard(
    storageSummary: DeviceStorageSummary,
    appVersionName: String,
    appBuildNumber: String,
    onRefreshStorageInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_storage_info_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = SeaBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "8. Informasi Penyimpanan & 9. Tentang Aplikasi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyDark
                        )
                        Text(
                            text = "Arsitektur Penyimpanan Lokal Offline-First (Internal Storage)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRefreshStorageInfo) {
                    Icon(Icons.Default.Refresh, contentDescription = "Segarkan Info Penyimpanan")
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0F9FF),
                border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Root Folder: SEWAIN_AJA_FREEZER/ (${storageSummary.totalFilesCount} File)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Text(
                        text = "• DATABASE/: ${LocalStorageAndBackupManager.formatFileSize(storageSummary.databaseSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• CUSTOMERS/ (KTP, Documents, Photos): ${LocalStorageAndBackupManager.formatFileSize(storageSummary.customerDocsSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• TRANSACTIONS/ (Payment, Agreement, Documentation): ${LocalStorageAndBackupManager.formatFileSize(storageSummary.transactionDocsSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• FREEZERS/ (Photos, Condition): ${LocalStorageAndBackupManager.formatFileSize(storageSummary.freezerDocsSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• BACKUP/ (Database, Documents, Full Backup .zip): ${LocalStorageAndBackupManager.formatFileSize(storageSummary.backupFilesSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Total Penggunaan Aplikasi: ${LocalStorageAndBackupManager.formatFileSize(storageSummary.totalAppStorageBytes)} • Tersedia di Perangkat: ${LocalStorageAndBackupManager.formatFileSize(storageSummary.availableDeviceBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SeaBluePrimary
                    )
                }
            }

            // 9. Tentang Aplikasi
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NavyDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "9. TENTANG APLIKASI — SEWAIN AJA FREEZER SOC",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = LightBlueAccent
                    )
                    Text(
                        text = "Versi: $appVersionName • $appBuildNumber",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = "Sistem Manajemen Persewaan Freezer, OCR e-KTP Indonesia, Active GPS Tagging, Penyimpanan Dokumen Lokal Terenkripsi, dan Backup/Restore Terintegrasi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBAE6FD)
                    )
                }
            }
        }
    }
}
