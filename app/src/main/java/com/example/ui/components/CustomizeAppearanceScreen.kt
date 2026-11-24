package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.ui.theme.AppAppearanceConfig
import com.example.ui.theme.AppAppearancePreferences
import com.example.ui.theme.AppColorThemePreset
import com.example.ui.theme.AppFontChoice
import com.example.ui.theme.BackgroundPositionMode
import com.example.ui.theme.BackgroundScaleMode
import com.example.ui.theme.BackgroundTargetScope
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.TextDescription
import com.example.ui.theme.TextFormLabel
import com.example.ui.theme.TextPageTitle
import com.example.ui.theme.appFilterChipColors
import com.example.ui.theme.appOutlinedTextFieldColors
import com.example.ui.theme.appPrimaryButtonColors
import java.io.File
import kotlin.math.roundToInt

/**
 * PENGATURAN TAMPILAN / CUSTOMIZE APP:
 * A. Custom Color Theme (11 preset + Color Picker untuk 8 elemen warna dengan penjaga kontras)
 * B. Custom Font (6 font lokal + upload .TTF/.OTF tervalidasi & aman)
 * C. Custom Background (Kamera/Galeri, posisi, ukuran, transparansi, blur, cakupan halaman)
 * D. [ PREVIEW ] • [ SIMPAN TAMPILAN ] • [ RESET DEFAULT ]
 */
@Composable
fun CustomizeAppearanceSettingsCard(
    savedConfig: AppAppearanceConfig,
    onPreviewConfig: (AppAppearanceConfig?) -> Unit,
    onSaveConfig: (AppAppearanceConfig) -> Unit,
    onResetDefault: () -> Unit,
    onFeedbackMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var draft by remember(savedConfig) { mutableStateOf(savedConfig) }
    var isPreviewActive by remember { mutableStateOf(false) }
    var activeColorPickerSlot by remember { mutableStateOf<Pair<String, String>?>(null) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var displayName = "custom_font.ttf"
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) displayName = c.getString(0) ?: displayName
                }
            }
            val res = AppAppearancePreferences.importAndValidateCustomFont(context, uri, displayName)
            res.onSuccess { (path, fileName) ->
                draft = draft.copy(
                    fontChoiceId = AppFontChoice.CUSTOM_FILE.id,
                    customFontPath = path,
                    customFontDisplayName = fileName
                )
                onFeedbackMessage("Font '$fileName' berhasil divalidasi. Tekan PREVIEW atau SIMPAN TAMPILAN.")
            }.onFailure { err ->
                onFeedbackMessage(err.localizedMessage ?: "File font tidak valid.")
            }
        }
    }

    val bgGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            AppAppearancePreferences.saveCustomBackgroundImage(context, uri)
                .onSuccess { path ->
                    draft = draft.copy(
                        backgroundImagePath = path,
                        backgroundScopeId = if (draft.backgroundScope == BackgroundTargetScope.NONE) {
                            BackgroundTargetScope.ALL_SCREENS.id
                        } else draft.backgroundScopeId
                    )
                    onFeedbackMessage("Gambar background dipilih dari galeri.")
                }
                .onFailure { err ->
                    onFeedbackMessage("Gagal memuat gambar background: ${err.localizedMessage}")
                }
        }
    }

    val bgCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            AppAppearancePreferences.saveCustomBackgroundImage(context, bitmap)
                .onSuccess { path ->
                    draft = draft.copy(
                        backgroundImagePath = path,
                        backgroundScopeId = if (draft.backgroundScope == BackgroundTargetScope.NONE) {
                            BackgroundTargetScope.ALL_SCREENS.id
                        } else draft.backgroundScopeId
                    )
                    onFeedbackMessage("Gambar background berhasil diambil dari kamera.")
                }
                .onFailure { err ->
                    onFeedbackMessage("Gagal menyimpan foto background: ${err.localizedMessage}")
                }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) bgCameraLauncher.launch(null)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customize_appearance_screen")
    ) {
        // Header Banner
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = draft.navBarColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = draft.accentColor, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PENGATURAN TAMPILAN / CUSTOMIZE APP",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    Text(
                        text = "Tersimpan lokal di perangkat tanpa mengubah data transaksi dan tidak otomatis diunggah ke cloud.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }

        // ==================== A. CUSTOM COLOR THEME ====================
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ColorLens, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "A. CUSTOM COLOR THEME",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPageTitle
                    )
                }

                // 11 Presets horizontal strip
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    AppColorThemePreset.entries.forEach { preset ->
                        val isSelected = draft.themePresetId.equals(preset.id, ignoreCase = true)
                        val swatchColor = AppAppearanceConfig.parseHexColorOrDefault(preset.primaryHex, SeaBluePrimary)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) swatchColor.copy(alpha = 0.14f) else Color(0xFFF8FAFC),
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) swatchColor else Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .clickable {
                                    draft = if (preset == AppColorThemePreset.CUSTOM) {
                                        draft.copy(themePresetId = preset.id)
                                    } else {
                                        draft.applyPreset(preset)
                                    }
                                    if (isPreviewActive) onPreviewConfig(draft)
                                }
                                .testTag("theme_preset_${preset.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(1.dp, Color(0xFF94A3B8), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = PureWhite, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = TextPageTitle
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))
                Text(
                    text = "Kustomisasi 8 Elemen Warna (Ketuk untuk membuka Color Picker):",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDescription
                )

                val colorSlots = listOf(
                    Triple("primary", "Primary Color", draft.primaryColorHex),
                    Triple("secondary", "Secondary Color", draft.secondaryColorHex),
                    Triple("background", "Background Color", draft.backgroundColorHex),
                    Triple("card", "Card Color", draft.cardColorHex),
                    Triple("text", "Text Color", draft.textColorHex),
                    Triple("navbar", "Navigation Bar", draft.navBarColorHex),
                    Triple("button", "Button Color", draft.buttonColorHex),
                    Triple("accent", "Accent Color", draft.accentColorHex)
                )

                colorSlots.chunked(2).forEach { rowPair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        rowPair.forEach { (key, label, hex) ->
                            val swatch = AppAppearanceConfig.parseHexColorOrDefault(hex, SeaBluePrimary)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeColorPickerSlot = key to hex }
                                    .testTag("color_slot_$key")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                            .border(1.dp, Color(0xFF64748B), CircleShape)
                                    )
                                    Column {
                                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextPageTitle)
                                        Text(hex.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextDescription)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== B. CUSTOM FONT ====================
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
                    Icon(Icons.Default.FontDownload, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "B. CUSTOM FONT (.TTF / .OTF)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPageTitle
                    )
                }

                AppFontChoice.entries.filter { it != AppFontChoice.CUSTOM_FILE }.forEach { fontChoice ->
                    val selected = draft.fontChoice == fontChoice
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFFE0F2FE) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (selected) SeaBluePrimary else Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = draft.copy(fontChoiceId = fontChoice.id)
                                if (isPreviewActive) onPreviewConfig(draft)
                            }
                            .testTag("font_choice_${fontChoice.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = fontChoice.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = TextPageTitle
                            )
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = "Aktif", tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (draft.customFontPath.isNotBlank()) {
                    val isCustomSelected = draft.fontChoice == AppFontChoice.CUSTOM_FILE
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCustomSelected) Color(0xFFD1FAE5) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, StatusPaidGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = draft.copy(fontChoiceId = AppFontChoice.CUSTOM_FILE.id)
                                if (isPreviewActive) onPreviewConfig(draft)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Font Upload: ${draft.customFontDisplayName.ifBlank { "Custom Font" }}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46)
                                )
                                Text("Tervalidasi & tersimpan lokal", style = MaterialTheme.typography.labelSmall, color = TextDescription)
                            }
                            if (isCustomSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Aktif", tint = StatusPaidGreen)
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { fontPickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_upload_custom_font")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Font Sendiri (.TTF / .OTF)", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==================== C. CUSTOM BACKGROUND ====================
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "C. CUSTOM BACKGROUND APLIKASI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPageTitle
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (granted) bgCameraLauncher.launch(null) else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_bg_camera")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kamera", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            bgGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_bg_gallery")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galeri", fontWeight = FontWeight.Bold)
                    }
                }

                if (draft.backgroundImagePath.isNotBlank() && File(draft.backgroundImagePath).exists()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = File(draft.backgroundImagePath),
                            contentDescription = "Preview Background",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                draft = draft.copy(
                                    backgroundImagePath = "",
                                    backgroundScopeId = BackgroundTargetScope.NONE.id
                                )
                                if (isPreviewActive) onPreviewConfig(draft)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus Background", tint = StatusOverdueRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus", style = MaterialTheme.typography.labelSmall, color = StatusOverdueRed)
                        }
                    }
                }

                Text("Penerapan Background:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextFormLabel)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    BackgroundTargetScope.entries.forEach { scope ->
                        FilterChip(
                            selected = draft.backgroundScope == scope,
                            onClick = {
                                draft = draft.copy(backgroundScopeId = scope.id)
                                if (isPreviewActive) onPreviewConfig(draft)
                            },
                            label = { Text(scope.label, style = MaterialTheme.typography.labelSmall) },
                            colors = appFilterChipColors(),
                            modifier = Modifier.testTag("bg_scope_${scope.id}")
                        )
                    }
                }

                if (draft.backgroundScope == BackgroundTargetScope.SPECIFIC_SCREENS) {
                    val tabNames = listOf(0 to "Dashboard", 1 to "Data Unit", 2 to "Pelanggan", 3 to "Transaksi", 4 to "Laporan", 5 to "Pengaturan")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        tabNames.forEach { (idx, name) ->
                            val active = idx in draft.backgroundSpecificTabs
                            FilterChip(
                                selected = active,
                                onClick = {
                                    val updatedSet = if (active) draft.backgroundSpecificTabs - idx else draft.backgroundSpecificTabs + idx
                                    draft = draft.copy(backgroundSpecificTabs = updatedSet)
                                    if (isPreviewActive) onPreviewConfig(draft)
                                },
                                label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                                colors = appFilterChipColors()
                            )
                        }
                    }
                }

                // Scale & Position
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    BackgroundScaleMode.entries.forEach { mode ->
                        FilterChip(
                            selected = draft.backgroundScaleMode == mode,
                            onClick = { draft = draft.copy(backgroundScaleModeId = mode.id) },
                            label = { Text("Ukuran: ${mode.label}", style = MaterialTheme.typography.labelSmall) },
                            colors = appFilterChipColors()
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    BackgroundPositionMode.entries.forEach { pos ->
                        FilterChip(
                            selected = draft.backgroundPositionMode == pos,
                            onClick = { draft = draft.copy(backgroundPositionId = pos.id) },
                            label = { Text("Posisi: ${pos.label}", style = MaterialTheme.typography.labelSmall) },
                            colors = appFilterChipColors()
                        )
                    }
                }

                // Opacity & Blur Sliders
                Column {
                    Text(
                        text = "Transparansi Gambar: ${(draft.backgroundOpacity * 100f).roundToInt()}% (Dijaga agar teks tetap terbaca)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFormLabel
                    )
                    Slider(
                        value = draft.backgroundOpacity,
                        onValueChange = {
                            draft = draft.copy(backgroundOpacity = it.coerceIn(0.05f, 0.60f))
                            if (isPreviewActive) onPreviewConfig(draft)
                        },
                        valueRange = 0.05f..0.60f
                    )
                }

                Column {
                    Text(
                        text = "Tingkat Blur Background: ${draft.backgroundBlurDp.roundToInt()} dp",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFormLabel
                    )
                    Slider(
                        value = draft.backgroundBlurDp,
                        onValueChange = {
                            draft = draft.copy(backgroundBlurDp = it.coerceIn(0f, 20f))
                            if (isPreviewActive) onPreviewConfig(draft)
                        },
                        valueRange = 0f..20f
                    )
                }
            }
        }

        // ==================== D. PREVIEW / SIMPAN / RESET DEFAULT ====================
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = {
                    isPreviewActive = !isPreviewActive
                    onPreviewConfig(if (isPreviewActive) draft else null)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_customize_preview")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isPreviewActive) "TUTUP PREVIEW" else "PREVIEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    isPreviewActive = false
                    onPreviewConfig(null)
                    onSaveConfig(draft)
                },
                colors = appPrimaryButtonColors(draft.buttonColor),
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag("btn_customize_save")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("SIMPAN TAMPILAN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = {
                isPreviewActive = false
                onPreviewConfig(null)
                draft = AppAppearanceConfig.DEFAULT
                onResetDefault()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_customize_reset")
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = StatusOverdueRed, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("RESET DEFAULT", color = StatusOverdueRed, fontWeight = FontWeight.Bold)
        }
    }

    // Interactive RGB + Hex Color Picker Dialog
    activeColorPickerSlot?.let { (slotKey, initialHex) ->
        SimpleRgbHexColorPickerDialog(
            title = "Pilih Warna ($slotKey)",
            initialHex = initialHex,
            onDismiss = { activeColorPickerSlot = null },
            onColorSelected = { chosenHex ->
                draft = when (slotKey) {
                    "primary" -> draft.copy(themePresetId = AppColorThemePreset.CUSTOM.id, primaryColorHex = chosenHex)
                    "secondary" -> draft.copy(themePresetId = AppColorThemePreset.CUSTOM.id, secondaryColorHex = chosenHex)
                    "background" -> draft.copy(themePresetId = AppColorThemePreset.CUSTOM.id, backgroundColorHex = chosenHex)
                    "card" -> draft.copy(themePresetId = AppColorThemePreset.CUSTOM.id, cardColorHex = chosenHex)
                    "text" -> draft.copy(themePresetId = AppColorThemePreset.CUSTOM.id, textColorHex = chosenHex)
                    "navbar" -> draft.copy(themePresetId = AppColorThemePreset.CUSTOM.id, navBarColorHex = chosenHex)
                    "button" -> draft.copy(themePresetId = AppColorThemePreset.CUSTOM.id, buttonColorHex = chosenHex)
                    "accent" -> draft.copy(themePresetId = AppColorThemePreset.CUSTOM.id, accentColorHex = chosenHex)
                    else -> draft
                }
                if (isPreviewActive) onPreviewConfig(draft)
                activeColorPickerSlot = null
            }
        )
    }
}

@Composable
private fun SimpleRgbHexColorPickerDialog(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val startColor = AppAppearanceConfig.parseHexColorOrDefault(initialHex, SeaBluePrimary)
    val startArgb = startColor.toArgb()
    var red by remember { mutableFloatStateOf(((startArgb shr 16) and 0xFF).toFloat()) }
    var green by remember { mutableFloatStateOf(((startArgb shr 8) and 0xFF).toFloat()) }
    var blue by remember { mutableFloatStateOf((startArgb and 0xFF).toFloat()) }
    val currentColor = Color(red.roundToInt(), green.roundToInt(), blue.roundToInt())
    val currentHex = AppAppearanceConfig.colorToHex(currentColor)
    var hexInput by remember(currentHex) { mutableStateOf(currentHex) }

    val quickSwatches = listOf(
        "#0284C7", "#0A2540", "#0EA5E9", "#1D4ED8", "#059669",
        "#7C3AED", "#DC2626", "#EA580C", "#18181B", "#FFFFFF",
        "#F8FAFC", "#172B4D"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextPageTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(currentColor)
                        .border(1.dp, Color(0xFF94A3B8), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentHex,
                        color = AppAppearanceConfig.ensureReadableTextOn(PureWhite, currentColor),
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    quickSwatches.forEach { sw ->
                        val c = AppAppearanceConfig.parseHexColorOrDefault(sw, SeaBluePrimary)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(1.dp, Color(0xFF64748B), CircleShape)
                                .clickable {
                                    val argb = c.toArgb()
                                    red = ((argb shr 16) and 0xFF).toFloat()
                                    green = ((argb shr 8) and 0xFF).toFloat()
                                    blue = (argb and 0xFF).toFloat()
                                }
                        )
                    }
                }

                Column {
                    Text("Red: ${red.roundToInt()}", style = MaterialTheme.typography.labelSmall, color = TextFormLabel)
                    Slider(value = red, onValueChange = { red = it }, valueRange = 0f..255f)
                    Text("Green: ${green.roundToInt()}", style = MaterialTheme.typography.labelSmall, color = TextFormLabel)
                    Slider(value = green, onValueChange = { green = it }, valueRange = 0f..255f)
                    Text("Blue: ${blue.roundToInt()}", style = MaterialTheme.typography.labelSmall, color = TextFormLabel)
                    Slider(value = blue, onValueChange = { blue = it }, valueRange = 0f..255f)
                }

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        hexInput = input
                        if (input.length == 7 && input.startsWith("#")) {
                            runCatching {
                                val parsed = android.graphics.Color.parseColor(input)
                                red = ((parsed shr 16) and 0xFF).toFloat()
                                green = ((parsed shr 8) and 0xFF).toFloat()
                                blue = (parsed and 0xFF).toFloat()
                            }
                        }
                    },
                    label = { Text("Kode Hex (#RRGGBB)") },
                    singleLine = true,
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(currentHex) },
                colors = appPrimaryButtonColors(SeaBluePrimary)
            ) {
                Text("Terapkan Warna", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
