package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.local.CustomerPhotoEntity
import com.example.data.local.DrivePhotoMetadataEntity
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.TextDescription
import com.example.ui.theme.TextPageTitle
import com.example.util.DateCurrencyUtils
import java.io.File
import java.util.Locale

object LocationDocSlots {
    const val SLOT_FREEZER_DI_LOKASI = "FREEZER_DI_LOKASI"
    const val SLOT_TAMPAK_DEPAN_LOKASI = "TAMPAK_DEPAN_LOKASI"
    const val CATEGORY_FOTO_LOKASI = "04_Foto_Lokasi"
}

/**
 * Representasi satu item foto dokumentasi lokasi (baik yang sudah tersimpan di SQLite
 * maupun yang sedang di-stage di form transaksi baru sebelum tombol Simpan ditekan).
 */
data class LocationDocPhotoDisplayItem(
    val id: String,
    val docSlot: String, // FREEZER_DI_LOKASI | TAMPAK_DEPAN_LOKASI
    val localPath: String,
    val capturedAtIso: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val uploadStatus: String = "PENDING", // PENDING | UPLOADING | UPLOADED | FAILED
    val persistedEntity: CustomerPhotoEntity? = null
)

fun mapUploadStatusLabelAndColor(rawStatus: String): Triple<String, Color, Color> {
    return when (rawStatus.trim().uppercase(Locale.ROOT)) {
        "UPLOADED", "SYNCED", "BERHASIL" -> Triple("Berhasil", Color(0xFF065F46), Color(0xFFD1FAE5))
        "UPLOADING", "MENGUNGGAH" -> Triple("Mengunggah", Color(0xFF0369A1), Color(0xFFE0F2FE))
        "FAILED", "GAGAL" -> Triple("Gagal", Color(0xFF991B1B), Color(0xFFFEE2E2))
        else -> Triple("Menunggu", Color(0xFF92400E), Color(0xFFFEF3C7))
    }
}

/**
 * Kartu Upload Dokumentasi Freezer di Lokasi & Tampak Depan Lokasi.
 * Digunakan secara konsisten di:
 * 1. Form Penyewaan (`TransactionsScreen`)
 * 2. Form Lokasi Penggunaan Freezer (`UsageLocationScreen`)
 *
 * Mendukung kamera langsung, galeri (single & multi-foto via Photo Picker tanpa izin storage luas),
 * metadata waktu & koordinat GPS, penyimpanan lokal lebih dulu, dan status upload Google Drive real-time.
 */
@Composable
fun LocationFreezerDocumentationCard(
    freezerLocationPhotos: List<LocationDocPhotoDisplayItem>,
    frontLocationPhotos: List<LocationDocPhotoDisplayItem>,
    drivePhotoMetadataList: List<DrivePhotoMetadataEntity> = emptyList(),
    customerLabel: String = "",
    unitLabel: String = "",
    currentLatitude: Double? = null,
    currentLongitude: Double? = null,
    isUploading: Boolean = false,
    onAddPhotoFromSource: (docSlot: String, source: Any) -> Unit,
    onDeletePhoto: (LocationDocPhotoDisplayItem) -> Unit,
    onRetryUpload: ((LocationDocPhotoDisplayItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val driveStatusByPath = remember(drivePhotoMetadataList) {
        drivePhotoMetadataList.associateBy { it.localCompressedPath.ifBlank { it.tempOriginalPath } }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_location_freezer_documentation")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Dokumentasi Freezer di Lokasi",
                    tint = SeaBluePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DOKUMENTASI FREEZER & LOKASI PENGGUNAAN",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPageTitle
                    )
                    Text(
                        text = buildString {
                            append("Foto tersimpan lokal terlebih dahulu lalu otomatis disinkronkan ke Google Drive.")
                            if (customerLabel.isNotBlank()) append(" • $customerLabel")
                            if (unitLabel.isNotBlank()) append(" • $unitLabel")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDescription
                    )
                }
            }

            if (currentLatitude != null && currentLongitude != null) {
                Surface(
                    color = Color(0xFFE0F2FE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "GPS Aktif",
                            tint = SeaBluePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Metadata GPS aktif untuk foto: %.6f, %.6f",
                                currentLatitude,
                                currentLongitude
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = NavyDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // A. Dokumentasi Freezer di Lokasi
            LocationDocCategoryBlock(
                title = "A. Dokumentasi Freezer di Lokasi",
                subtitle = "Foto unit freezer yang sudah terpasang di lokasi pelanggan (mendukung lebih dari 1 foto).",
                icon = Icons.Default.PhotoCamera,
                docSlot = LocationDocSlots.SLOT_FREEZER_DI_LOKASI,
                photos = freezerLocationPhotos,
                driveStatusByPath = driveStatusByPath,
                isUploading = isUploading,
                cameraButtonTestTag = "btn_doc_freezer_lokasi_camera",
                galleryButtonTestTag = "btn_doc_freezer_lokasi_gallery",
                sectionTestTag = "section_doc_freezer_di_lokasi",
                onAddPhotoFromSource = { src ->
                    onAddPhotoFromSource(LocationDocSlots.SLOT_FREEZER_DI_LOKASI, src)
                },
                onDeletePhoto = onDeletePhoto,
                onRetryUpload = onRetryUpload
            )

            HorizontalDivider(color = Color(0xFFE2E8F0))

            // B. Dokumentasi Tampak Depan Lokasi
            LocationDocCategoryBlock(
                title = "B. Dokumentasi Tampak Depan Lokasi",
                subtitle = "Foto tampak depan tempat usaha / lokasi pelanggan (terhubung dengan pelanggan, transaksi & unit).",
                icon = Icons.Default.Storefront,
                docSlot = LocationDocSlots.SLOT_TAMPAK_DEPAN_LOKASI,
                photos = frontLocationPhotos,
                driveStatusByPath = driveStatusByPath,
                isUploading = isUploading,
                cameraButtonTestTag = "btn_doc_tampak_depan_camera",
                galleryButtonTestTag = "btn_doc_tampak_depan_gallery",
                sectionTestTag = "section_doc_tampak_depan_lokasi",
                onAddPhotoFromSource = { src ->
                    onAddPhotoFromSource(LocationDocSlots.SLOT_TAMPAK_DEPAN_LOKASI, src)
                },
                onDeletePhoto = onDeletePhoto,
                onRetryUpload = onRetryUpload
            )
        }
    }
}

@Composable
private fun LocationDocCategoryBlock(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    docSlot: String,
    photos: List<LocationDocPhotoDisplayItem>,
    driveStatusByPath: Map<String, DrivePhotoMetadataEntity>,
    isUploading: Boolean,
    cameraButtonTestTag: String,
    galleryButtonTestTag: String,
    sectionTestTag: String,
    onAddPhotoFromSource: (Any) -> Unit,
    onDeletePhoto: (LocationDocPhotoDisplayItem) -> Unit,
    onRetryUpload: ((LocationDocPhotoDisplayItem) -> Unit)?
) {
    val context = LocalContext.current
    var pendingCameraRequest by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            onAddPhotoFromSource(bitmap)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCameraRequest) {
            pendingCameraRequest = false
            cameraLauncher.launch(null)
        } else {
            pendingCameraRequest = false
        }
    }

    // Zero-permission Android Photo Picker supporting multiple photos at once
    val multiGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris: List<Uri> ->
        uris.forEach { uri -> onAddPhotoFromSource(uri) }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(sectionTestTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPageTitle
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDescription
                )
            }
            Surface(
                color = if (photos.isNotEmpty()) Color(0xFFD1FAE5) else Color(0xFFF1F5F9),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${photos.size} Foto",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (photos.isNotEmpty()) Color(0xFF065F46) else TextDescription,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Action buttons: Camera & Multi-Gallery
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        cameraLauncher.launch(null)
                    } else {
                        pendingCameraRequest = true
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = !isUploading,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag(cameraButtonTestTag)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Ambil Foto Kamera", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ambil Kamera", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    multiGalleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !isUploading,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag(galleryButtonTestTag)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Pilih Foto Galeri", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pilih Galeri", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }

        if (photos.isEmpty()) {
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Belum ada foto pada slot $title. Ambil langsung dari kamera atau pilih satu/beberapa foto dari galeri.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDescription,
                    modifier = Modifier.padding(10.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEachIndexed { idx, photo ->
                    val matchedDrive = driveStatusByPath[photo.localPath]
                    val resolvedStatus = matchedDrive?.syncStatus?.takeIf { it.isNotBlank() } ?: photo.uploadStatus
                    val (statusLabel, statusFg, statusBg) = mapUploadStatusLabelAndColor(resolvedStatus)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_${docSlot}_${idx + 1}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                val model: Any = remember(photo.localPath) {
                                    val f = File(photo.localPath)
                                    if (f.exists()) f else photo.localPath
                                }
                                AsyncImage(
                                    model = model,
                                    contentDescription = "$title #${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Foto #${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPageTitle
                                    )
                                    Surface(
                                        color = statusBg,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (statusLabel) {
                                                    "Berhasil" -> Icons.Default.CheckCircle
                                                    "Mengunggah" -> Icons.Default.CloudUpload
                                                    "Gagal" -> Icons.Default.ErrorOutline
                                                    else -> Icons.Default.CloudQueue
                                                },
                                                contentDescription = "Status Upload $statusLabel",
                                                tint = statusFg,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = statusLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = statusFg,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Waktu: ${photo.capturedAtIso.ifBlank { DateCurrencyUtils.nowDateTimeDisplay() }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDescription,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (photo.latitude != null && photo.longitude != null) {
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "GPS: %.5f, %.5f",
                                            photo.latitude,
                                            photo.longitude
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SeaBluePrimary,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (statusLabel == "Gagal" && onRetryUpload != null) {
                                IconButton(
                                    onClick = { onRetryUpload(photo) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Coba Unggah Ulang",
                                        tint = StatusWarningYellow
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeletePhoto(photo) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .testTag("btn_delete_${docSlot}_${idx + 1}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus Foto Dokumentasi",
                                    tint = StatusOverdueRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
