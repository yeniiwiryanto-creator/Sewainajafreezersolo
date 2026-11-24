package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.example.data.local.DEFAULT_SEWA_HAK_MILIK_PERIODS
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitRentalPriceEntity
import com.example.media.PhotoCategory
import com.example.media.SmartImageProcessor
import com.example.storage.LocalStorageAndBackupManager
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.TextInputPrimary
import com.example.ui.theme.appFilterChipColors
import com.example.ui.theme.appOutlinedTextFieldColors
import com.example.util.DateCurrencyUtils
import java.io.File
import java.io.FileOutputStream

/**
 * Pemroses Gambar Tanda Tangan (Section 4):
 * - Mendukung format PNG, JPG, JPEG dari Galeri maupun Kamera
 * - Menghapus background putih secara otomatis menjadi transparan (ARGB_8888 PNG)
 * - Auto-crop area tanda tangan dengan rasio proporsional (tidak gepeng/distorsi)
 * - Menyimpan secara permanen di folder transaksi SEWAIN_AJA_FREEZER/TRANSACTIONS/<TX>/SIGNATURES/
 */
object SignatureImageProcessor {

    fun removeWhiteBackgroundAndCrop(
        source: Bitmap,
        removeWhiteBackground: Boolean = true,
        autoCropBounds: Boolean = true,
        whiteThreshold: Int = 222
    ): Bitmap {
        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        val argbBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        argbBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var hasInkPixel = false

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                val pixel = pixels[idx]
                val a = (pixel ushr 24) and 0xFF
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                if (a < 25) {
                    pixels[idx] = AndroidColor.TRANSPARENT
                    continue
                }

                val luminance = (r * 299 + g * 587 + b * 114) / 1000
                val isNearWhite = (r >= whiteThreshold && g >= whiteThreshold && b >= whiteThreshold) || luminance >= whiteThreshold

                if (removeWhiteBackground && isNearWhite) {
                    pixels[idx] = AndroidColor.TRANSPARENT
                } else {
                    // Preserve ink stroke and deepen contrast slightly for crisp PDF output
                    val darkenedR = (r * 0.75f).toInt().coerceIn(0, 255)
                    val darkenedG = (g * 0.75f).toInt().coerceIn(0, 255)
                    val darkenedB = (b * 0.82f).toInt().coerceIn(0, 255)
                    val smoothAlpha = if (removeWhiteBackground && luminance > (whiteThreshold - 30)) {
                        (((whiteThreshold - luminance).toFloat() / 30f) * 255f).toInt().coerceIn(60, 255)
                    } else {
                        a
                    }
                    pixels[idx] = (smoothAlpha shl 24) or (darkenedR shl 16) or (darkenedG shl 8) or darkenedB
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    hasInkPixel = true
                }
            }
        }

        argbBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        if (!autoCropBounds || !hasInkPixel || maxX <= minX || maxY <= minY) {
            return scaleProportionally(argbBitmap, maxWidth = 700, maxHeight = 300)
        }

        val padX = ((maxX - minX) * 0.08f).toInt().coerceAtLeast(12)
        val padY = ((maxY - minY) * 0.12f).toInt().coerceAtLeast(12)
        val cropLeft = (minX - padX).coerceAtLeast(0)
        val cropTop = (minY - padY).coerceAtLeast(0)
        val cropRight = (maxX + padX).coerceAtMost(width)
        val cropBottom = (maxY + padY).coerceAtMost(height)
        val cropW = (cropRight - cropLeft).coerceAtLeast(1)
        val cropH = (cropBottom - cropTop).coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(argbBitmap, cropLeft, cropTop, cropW, cropH)
        return scaleProportionally(cropped, maxWidth = 700, maxHeight = 300)
    }

    fun scaleProportionally(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        if (w <= maxWidth && h <= maxHeight) return source
        val ratio = minOf(maxWidth.toFloat() / w.toFloat(), maxHeight.toFloat() / h.toFloat())
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    fun saveUploadedOrCapturedSignature(
        context: Context,
        sourceBitmap: Bitmap,
        transactionFolder: String,
        filePrefix: String,
        signerName: String,
        removeWhiteBackground: Boolean = true
    ): File {
        val processed = removeWhiteBackgroundAndCrop(
            source = sourceBitmap,
            removeWhiteBackground = removeWhiteBackground,
            autoCropBounds = true
        )
        // Save in structured SEWAIN_AJA_FREEZER/AGREEMENTS/SIGNATURES and transaction folder
        val rootDir = LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
        val txSigDir = File(rootDir, "AGREEMENTS/SIGNATURES/$transactionFolder")
        if (!txSigDir.exists()) txSigDir.mkdirs()

        val legacyDir = File(context.filesDir, "photos/$transactionFolder")
        if (!legacyDir.exists()) legacyDir.mkdirs()

        val fileName = "${filePrefix}_uploaded_signature.png"
        val primaryFile = File(legacyDir, fileName)
        val archiveFile = File(txSigDir, fileName)

        FileOutputStream(primaryFile).use { out ->
            processed.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        try {
            primaryFile.copyTo(archiveFile, overwrite = true)
        } catch (_: Exception) {
        }
        return primaryFile
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Helper penyimpanan foto dokumentasi spesifik per unit:
 * Menyimpan foto ke SEWAIN_AJA_FREEZER/FREEZER_DOCUMENTATION/<TX_FOLDER>/UNIT_<UNIT_ID>/
 */
object PerUnitPhotoStorageHelper {

    fun saveUnitPhotoFromBitmap(
        context: Context,
        bitmap: Bitmap,
        transactionFolder: String,
        unitId: Long,
        unitIndex: Int,
        slotKey: String
    ): File {
        val rootDir = LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
        val unitDir = File(rootDir, "FREEZER_DOCUMENTATION/$transactionFolder/UNIT_${unitIndex}_ID_${unitId}")
        if (!unitDir.exists()) unitDir.mkdirs()

        val outFile = File(unitDir, "${slotKey}_${System.currentTimeMillis()}.jpg")
        val thumbFile = SmartImageProcessor.resolveThumbnailFileFor(context, outFile)
        val category = PhotoCategory.fromSlotOrType(slotKey).let {
            if (it == PhotoCategory.GENERAL_DOC) PhotoCategory.FREEZER else it
        }
        val processed = SmartImageProcessor.processImageSourceToFiles(
            context = context,
            source = bitmap,
            category = category,
            outputMainFile = outFile,
            outputThumbFile = thumbFile
        )
        if (processed == null) {
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out)
            }
        }
        return outFile
    }

    fun saveUnitPhotoFromUri(
        context: Context,
        uri: Uri,
        transactionFolder: String,
        unitId: Long,
        unitIndex: Int,
        slotKey: String
    ): File? {
        return try {
            val rootDir = LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
            val unitDir = File(rootDir, "FREEZER_DOCUMENTATION/$transactionFolder/UNIT_${unitIndex}_ID_${unitId}")
            if (!unitDir.exists()) unitDir.mkdirs()

            val outFile = File(unitDir, "${slotKey}_${System.currentTimeMillis()}.jpg")
            val thumbFile = SmartImageProcessor.resolveThumbnailFileFor(context, outFile)
            val category = PhotoCategory.fromSlotOrType(slotKey).let {
                if (it == PhotoCategory.GENERAL_DOC) PhotoCategory.FREEZER else it
            }
            val processed = SmartImageProcessor.processImageSourceToFiles(
                context = context,
                source = uri,
                category = category,
                outputMainFile = outFile,
                outputThumbFile = thumbFile
            )
            if (processed != null && outFile.exists() && outFile.length() > 0L) {
                return outFile
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (outFile.exists() && outFile.length() > 0L) outFile else null
        } catch (_: Exception) {
            null
        }
    }

    fun generateSimulatedUnitCameraPhoto(
        context: Context,
        transactionFolder: String,
        unitId: Long,
        unitIndex: Int,
        unitName: String,
        slotKey: String,
        slotTitle: String
    ): File {
        val rootDir = LocalStorageAndBackupManager.ensureStandardDirectoryStructure(context)
        val unitDir = File(rootDir, "FREEZER_DOCUMENTATION/$transactionFolder/UNIT_${unitIndex}_ID_${unitId}")
        if (!unitDir.exists()) unitDir.mkdirs()

        val outFile = File(unitDir, "${slotKey}_${System.currentTimeMillis()}.png")
        val bmp = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        canvas.drawColor(AndroidColor.rgb(10, 37, 64))

        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 24f
            isFakeBoldText = true
        }
        canvas.drawText("SEWAin Aja Freezer SOC - Dokumentasi Unit #$unitIndex", 28f, 60f, paint)
        paint.color = AndroidColor.rgb(56, 189, 248)
        paint.textSize = 20f
        canvas.drawText("Unit: $unitName", 28f, 105f, paint)
        canvas.drawText("Kategori: $slotTitle", 28f, 145f, paint)
        paint.color = AndroidColor.LTGRAY
        paint.textSize = 16f
        canvas.drawText("Folder: UNIT_${unitIndex}_ID_${unitId}", 28f, 190f, paint)
        canvas.drawText("Waktu: ${DateCurrencyUtils.nowDateTimeDisplay()}", 28f, 225f, paint)

        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return outFile
    }
}

/**
 * SECTION 1 & SECTION 3 (B & C):
 * Card-Based Multi-Unit Rental Editor + Unit-Based Billing Schedule + Automatic Billing Summary
 */
/**
 * Helper reaktif untuk menghitung harga sewa terkini berdasarkan data unit di Room (`FreezerUnitEntity` + `unit_rental_prices`)
 * untuk paket Harian (1 hari), Mingguan (7 hari), Bulanan (30 hari), periode khusus, maupun Program Hak Milik.
 */
internal fun resolveReactiveUnitRentalPrice(
    unit: FreezerUnitEntity?,
    configuredPrices: List<UnitRentalPriceEntity>,
    periodDays: Int,
    isHakMilik: Boolean
): Long {
    val validPrices = configuredPrices.filter { it.price > 0L }.sortedBy { it.periodDays }
    if (isHakMilik) {
        return unit?.cicilanPerBulan?.takeIf { it > 0L }
            ?: validPrices.firstOrNull { it.periodDays == 30 }?.price
            ?: validPrices.firstOrNull()?.price
            ?: 300000L
    }
    validPrices.firstOrNull { it.periodDays == periodDays }?.let { return it.price }
    val monthlyBase = validPrices.firstOrNull { it.periodDays == 30 }?.price
        ?: unit?.cicilanPerBulan?.takeIf { it > 0L }
        ?: validPrices.firstOrNull()?.let { entry ->
            if (entry.periodDays > 0) (entry.price * 30L) / entry.periodDays else entry.price
        }
        ?: 0L
    if (monthlyBase > 0L) {
        return when (periodDays) {
            1 -> ((monthlyBase + 29L) / 30L).coerceAtLeast(1000L)
            7 -> ((monthlyBase * 7L + 29L) / 30L).coerceAtLeast(1000L)
            30 -> monthlyBase
            else -> ((monthlyBase * periodDays.coerceAtLeast(1).toLong() + 29L) / 30L).coerceAtLeast(1000L)
        }
    }
    return 300000L
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MultiUnitRentalSection(
    unitItems: List<TransactionUnitItemEntity>,
    allUnits: List<FreezerUnitEntity>,
    unitRentalPricesMap: Map<Long, List<UnitRentalPriceEntity>> = emptyMap(),
    sewaHakMilikPeriods: List<Int> = DEFAULT_SEWA_HAK_MILIK_PERIODS,
    ongkir: Long,
    deposit: Long,
    diskon: Long,
    isEditMode: Boolean = false,
    ownedUnitIds: Set<Long> = emptySet(),
    reservedByOtherSessionUnitIds: Set<Long> = emptySet(),
    onResolveFreshUnitPrice: (suspend (Long, Int, String) -> Long)? = null,
    onUnitItemsChanged: (List<TransactionUnitItemEntity>) -> Unit,
    onValidationMessage: (String) -> Unit = {}
) {
    val sectionScope = rememberCoroutineScope()
    val selectedUnitIds = remember(unitItems) { unitItems.map { it.unitId }.toSet() }
    val remainingAvailableUnits = remember(allUnits, selectedUnitIds, reservedByOtherSessionUnitIds, ownedUnitIds, isEditMode) {
        allUnits.filter { u ->
            u.id !in selectedUnitIds && (
                (u.isStrictlyAvailableForNewRental() && u.id !in reservedByOtherSessionUnitIds) ||
                    (isEditMode && u.id in ownedUnitIds && !u.isDeleted)
                )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header Card: Daftar Unit yang Disewa
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.22f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
                                imageVector = Icons.Default.AcUnit,
                                contentDescription = "Daftar Unit",
                                tint = SeaBluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Daftar Unit yang Disewa (${unitItems.size} Unit)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Text(
                                text = "Hanya unit berstatus Tersedia yang dapat dipilih • Program & jatuh tempo independen per unit",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (remainingAvailableUnits.isNotEmpty()) Color(0xFFE0F2FE) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Sisa Tersedia: ${remainingAvailableUnits.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingAvailableUnits.isNotEmpty()) NavyDark else Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (unitItems.isEmpty()) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_available_units_warning")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Tidak ada unit tersedia",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (remainingAvailableUnits.isEmpty()) {
                                    "Tidak ada unit freezer berstatus Tersedia saat ini. Pastikan status unit di menu Unit adalah Tersedia (bukan Disewa, Hilang, atau Perbaikan)."
                                } else {
                                    "Belum ada unit dipilih. Tekan tombol TAMBAH UNIT di bawah untuk memilih unit berstatus Tersedia."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF991B1B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                unitItems.forEachIndexed { index, item ->
                    key("unit_slot_${index}_${item.id}_${item.unitId}") {
                        MultiUnitItemCard(
                            index = index,
                            item = item,
                            totalUnitsCount = unitItems.size,
                            allUnits = allUnits,
                            unitRentalPricesMap = unitRentalPricesMap,
                            alreadySelectedUnitIds = selectedUnitIds - item.unitId,
                            isEditMode = isEditMode,
                            ownedUnitIds = ownedUnitIds,
                            reservedByOtherSessionUnitIds = reservedByOtherSessionUnitIds,
                            sewaHakMilikPeriods = sewaHakMilikPeriods,
                            onResolveFreshUnitPrice = onResolveFreshUnitPrice,
                            onUpdateItem = { updated ->
                                val newList = unitItems.toMutableList().apply {
                                    set(index, updated.copy(unitIndexNumber = index + 1))
                                }
                                onUnitItemsChanged(newList)
                            },
                            onRemoveItem = {
                                if (unitItems.size <= 1) {
                                    onValidationMessage("Minimal harus terdapat 1 unit dalam transaksi.")
                                } else {
                                    val newList = unitItems.toMutableList().apply {
                                        removeAt(index)
                                    }.mapIndexed { idx, u -> u.copy(unitIndexNumber = idx + 1) }
                                    onUnitItemsChanged(newList)
                                }
                            },
                            onValidationMessage = onValidationMessage
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Tombol + TAMBAH UNIT FREEZER (Hanya memilih dari unit berstatus Tersedia yang belum dipilih)
                Button(
                    onClick = {
                        val candidateUnit = remainingAvailableUnits.firstOrNull()

                        if (candidateUnit == null) {
                            onValidationMessage("Tidak ada lagi unit freezer berstatus Tersedia yang dapat ditambahkan ke transaksi ini.")
                        } else {
                            val today = DateCurrencyUtils.todayIso()
                            val configuredPrices = unitRentalPricesMap[candidateUnit.id].orEmpty()
                                .filter { it.price > 0L }
                                .sortedBy { it.periodDays }
                            val chosenPeriodPrice = configuredPrices.firstOrNull { it.periodDays == 30 }
                                ?: configuredPrices.firstOrNull()
                            val defaultDuration = chosenPeriodPrice?.periodDays ?: 30
                            val defaultEnd = DateCurrencyUtils.addDaysToIsoDate(today, defaultDuration)
                            val defaultPrice = resolveReactiveUnitRentalPrice(
                                unit = candidateUnit,
                                configuredPrices = configuredPrices,
                                periodDays = defaultDuration,
                                isHakMilik = false
                            )
                            val newUnitItem = TransactionUnitItemEntity(
                                transactionId = unitItems.firstOrNull()?.transactionId ?: 0L,
                                customerId = unitItems.firstOrNull()?.customerId ?: 0L,
                                unitId = candidateUnit.id,
                                unitIndexNumber = unitItems.size + 1,
                                kodeUnitSnapshot = candidateUnit.kodeUnit,
                                namaUnitSnapshot = candidateUnit.namaUnit,
                                jenisUnitSnapshot = candidateUnit.jenisUnit,
                                merekSnapshot = candidateUnit.merek,
                                kapasitasLiterSnapshot = candidateUnit.kapasitasLiter,
                                jenisProgram = "Sewa Reguler",
                                totalPeriodeHakMilik = 0,
                                pembayaranKeHakMilik = 1,
                                statusProgramHakMilik = "Aktif",
                                hargaSewa = defaultPrice,
                                durasiHari = defaultDuration,
                                tanggalMulaiSewa = today,
                                tanggalAkhirSewa = defaultEnd,
                                tanggalJatuhTempo = defaultEnd,
                                nominalPembayaran = defaultPrice,
                                statusPembayaran = if (defaultPrice > 0L) "Lunas" else "Belum Bayar",
                                statusPenyewaan = "Dalam Penyewaan"
                            )
                            val appended = unitItems + newUnitItem
                            onUnitItemsChanged(appended)
                            if (onResolveFreshUnitPrice != null) {
                                sectionScope.launch {
                                    val liveDbPrice = runCatching {
                                        onResolveFreshUnitPrice(candidateUnit.id, defaultDuration, "Sewa Reguler")
                                    }.getOrDefault(defaultPrice)
                                    if (liveDbPrice > 0L && liveDbPrice != defaultPrice) {
                                        onUnitItemsChanged(
                                            appended.map { slot ->
                                                if (slot.unitId == candidateUnit.id) {
                                                    slot.copy(
                                                        hargaSewa = liveDbPrice,
                                                        nominalPembayaran = liveDbPrice,
                                                        statusPembayaran = "Lunas"
                                                    )
                                                } else slot
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    enabled = remainingAvailableUnits.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SeaBluePrimary,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFCBD5E1),
                        disabledContentColor = Color(0xFF475569)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_add_unit_to_transaction")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Unit Freezer", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            remainingAvailableUnits.isEmpty() -> "SEMUA UNIT TERSEDIA SUDAH DIPILIH"
                            isEditMode -> "+ TAMBAH UNIT KE TRANSAKSI (${remainingAvailableUnits.size} Tersedia)"
                            else -> "+ TAMBAH UNIT FREEZER (${remainingAvailableUnits.size} Tersedia)"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Section C: Ringkasan Pembayaran Multi-Unit
        MultiUnitBillingSummaryCard(
            unitItems = unitItems,
            ongkir = ongkir,
            deposit = deposit,
            diskon = diskon
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MultiUnitItemCard(
    index: Int,
    item: TransactionUnitItemEntity,
    totalUnitsCount: Int,
    allUnits: List<FreezerUnitEntity>,
    unitRentalPricesMap: Map<Long, List<UnitRentalPriceEntity>>,
    alreadySelectedUnitIds: Set<Long>,
    isEditMode: Boolean,
    ownedUnitIds: Set<Long>,
    reservedByOtherSessionUnitIds: Set<Long>,
    sewaHakMilikPeriods: List<Int>,
    onResolveFreshUnitPrice: (suspend (Long, Int, String) -> Long)? = null,
    onUpdateItem: (TransactionUnitItemEntity) -> Unit,
    onRemoveItem: () -> Unit,
    onValidationMessage: (String) -> Unit
) {
    val cardScope = rememberCoroutineScope()
    var isEditingExpanded by remember { mutableStateOf(true) }
    var unitDropdownExpanded by remember { mutableStateOf(false) }

    val selectableUnits = remember(
        allUnits,
        alreadySelectedUnitIds,
        item.unitId,
        isEditMode,
        ownedUnitIds,
        reservedByOtherSessionUnitIds
    ) {
        allUnits.filter { u ->
            u.id == item.unitId || (
                u.id !in alreadySelectedUnitIds && (
                    (u.isStrictlyAvailableForNewRental() && u.id !in reservedByOtherSessionUnitIds) ||
                        (isEditMode && u.id in ownedUnitIds && !u.isDeleted)
                    )
                )
        }
    }

    val isHakMilik = item.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_unit_item_${index + 1}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHakMilik) Color(0xFFF5F3FF) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isHakMilik) Color(0xFF8B5CF6).copy(alpha = 0.45f) else Color(0xFFCBD5E1)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top bar of unit card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (isHakMilik) Color(0xFF6D28D9) else NavyDark,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "UNIT ${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.namaUnitSnapshot.ifBlank { "Pilih Unit Freezer" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.kodeUnitSnapshot.ifBlank { "-" }} • ${if (item.kapasitasLiterSnapshot > 0) "${item.kapasitasLiterSnapshot} Liter" else item.jenisUnitSnapshot.ifBlank { "Freezer" }} • ${
                                if (isHakMilik) "Hak Milik Ke-${item.pembayaranKeHakMilik}/${item.totalPeriodeHakMilik.coerceAtLeast(1)} (${item.statusProgramHakMilik})" else "Sewa Reguler (${item.durasiHari} Hari)"
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isHakMilik) Color(0xFF5B21B6) else SeaBluePrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { isEditingExpanded = !isEditingExpanded },
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_edit_unit_${index + 1}"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditingExpanded) Icons.Default.ExpandLess else Icons.Default.Edit,
                            contentDescription = "Edit Unit",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEditingExpanded) "Tutup" else "Edit Unit", style = MaterialTheme.typography.labelSmall)
                    }

                    if (totalUnitsCount > 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onRemoveItem,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("btn_remove_unit_${index + 1}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus Unit",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Summary Pill Row (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Harga Sewa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = DateCurrencyUtils.formatRupiah(item.hargaSewa),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                }
                Column {
                    Text("Mulai Sewa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = item.tanggalMulaiSewa.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Jatuh Tempo Unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = item.tanggalJatuhTempo.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = SeaBluePrimary
                    )
                }
            }

            AnimatedVisibility(visible = isEditingExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Pilih Unit Freezer Dropdown
                    ExposedDropdownMenuBox(
                        expanded = unitDropdownExpanded,
                        onExpandedChange = { unitDropdownExpanded = !unitDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = if (item.namaUnitSnapshot.isNotBlank()) {
                                "${item.kodeUnitSnapshot} - ${item.namaUnitSnapshot} (${item.kapasitasLiterSnapshot}L)"
                            } else {
                                "Pilih Unit Freezer"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipe & Kapasitas Freezer (Unit ${index + 1})") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitDropdownExpanded) },
                            colors = appOutlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("dropdown_unit_selector_${index + 1}")
                        )
                        ExposedDropdownMenu(
                            expanded = unitDropdownExpanded,
                            onDismissRequest = { unitDropdownExpanded = false },
                            containerColor = PureWhite
                        ) {
                            selectableUnits.forEach { u ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${u.kodeUnit} • ${u.namaUnit} (${u.kapasitasLiter}L - ${u.statusUnit})",
                                            color = TextInputPrimary
                                        )
                                    },
                                    modifier = Modifier.testTag("dropdown_unit_option_${index + 1}_${u.id}"),
                                    onClick = {
                                        if (u.id in alreadySelectedUnitIds) {
                                            onValidationMessage("Unit ${u.namaUnit} sudah dipilih pada kartu unit lain dalam transaksi ini.")
                                        } else {
                                            val unitPrices = unitRentalPricesMap[u.id].orEmpty()
                                                .filter { it.price > 0L }
                                                .sortedBy { it.periodDays }
                                            val matchedPeriod = unitPrices.firstOrNull { it.periodDays == item.durasiHari }
                                                ?: unitPrices.firstOrNull { it.periodDays == 30 }
                                                ?: unitPrices.firstOrNull()
                                            val start = item.tanggalMulaiSewa.ifBlank { DateCurrencyUtils.todayIso() }
                                            val nextDuration = if (!isHakMilik) {
                                                matchedPeriod?.periodDays ?: item.durasiHari.coerceAtLeast(1)
                                            } else {
                                                item.durasiHari.coerceAtLeast(30)
                                            }
                                            val nextEnd = DateCurrencyUtils.addDaysToIsoDate(start, nextDuration.coerceAtLeast(1))
                                            // Selalu ambil harga sewa terbaru dari unit `u` yang baru dipilih (tidak boleh membawa harga unit lama)
                                            val nextPrice = resolveReactiveUnitRentalPrice(
                                                unit = u,
                                                configuredPrices = unitPrices,
                                                periodDays = nextDuration,
                                                isHakMilik = isHakMilik
                                            )
                                            val shouldSyncNominal = item.nominalPembayaran <= 0L || item.nominalPembayaran == item.hargaSewa
                                            val nextNominal = if (shouldSyncNominal) nextPrice else item.nominalPembayaran
                                            val nextPayStatus = when {
                                                nextNominal >= nextPrice && nextPrice > 0L -> "Lunas"
                                                nextNominal > 0L -> "Dibayar Sebagian"
                                                else -> "Belum Bayar"
                                            }
                                            val updatedItem = item.copy(
                                                unitId = u.id,
                                                kodeUnitSnapshot = u.kodeUnit,
                                                namaUnitSnapshot = u.namaUnit,
                                                jenisUnitSnapshot = u.jenisUnit,
                                                merekSnapshot = u.merek,
                                                kapasitasLiterSnapshot = u.kapasitasLiter,
                                                durasiHari = nextDuration,
                                                tanggalAkhirSewa = nextEnd,
                                                tanggalJatuhTempo = nextEnd,
                                                hargaSewa = nextPrice,
                                                nominalPembayaran = nextNominal,
                                                statusPembayaran = nextPayStatus
                                            )
                                            onUpdateItem(updatedItem)
                                            if (onResolveFreshUnitPrice != null) {
                                                cardScope.launch {
                                                    val liveDbPrice = runCatching {
                                                        onResolveFreshUnitPrice(u.id, nextDuration, updatedItem.jenisProgram)
                                                    }.getOrDefault(nextPrice)
                                                    if (liveDbPrice > 0L && liveDbPrice != updatedItem.hargaSewa) {
                                                        val syncedNom = if (shouldSyncNominal) liveDbPrice else updatedItem.nominalPembayaran
                                                        onUpdateItem(
                                                            updatedItem.copy(
                                                                hargaSewa = liveDbPrice,
                                                                nominalPembayaran = syncedNom,
                                                                statusPembayaran = if (syncedNom >= liveDbPrice) "Lunas" else if (syncedNom > 0L) "Dibayar Sebagian" else "Belum Bayar"
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        unitDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Program Penyewaan per Unit: Sewa Reguler vs Sewa Hak Milik
                    Text(
                        text = "Program Penyewaan Unit ${index + 1}:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = !isHakMilik,
                            onClick = {
                                val selectedUnitEntity = allUnits.find { it.id == item.unitId }
                                val unitPrices = unitRentalPricesMap[item.unitId].orEmpty()
                                    .filter { it.price > 0L }
                                    .sortedBy { it.periodDays }
                                val matched = unitPrices.firstOrNull { it.periodDays == item.durasiHari }
                                    ?: unitPrices.firstOrNull { it.periodDays == 30 }
                                    ?: unitPrices.firstOrNull()
                                val start = item.tanggalMulaiSewa.ifBlank { DateCurrencyUtils.todayIso() }
                                val nextDays = matched?.periodDays ?: item.durasiHari.coerceAtLeast(30)
                                val nextEnd = DateCurrencyUtils.addDaysToIsoDate(start, nextDays)
                                val freshRegularPrice = resolveReactiveUnitRentalPrice(
                                    unit = selectedUnitEntity,
                                    configuredPrices = unitPrices,
                                    periodDays = nextDays,
                                    isHakMilik = false
                                )
                                val shouldSyncNominal = item.nominalPembayaran <= 0L || item.nominalPembayaran == item.hargaSewa
                                val nextNominal = if (shouldSyncNominal) freshRegularPrice else item.nominalPembayaran
                                val updated = item.copy(
                                    jenisProgram = "Sewa Reguler",
                                    totalPeriodeHakMilik = 0,
                                    pembayaranKeHakMilik = 1,
                                    statusProgramHakMilik = "Aktif",
                                    durasiHari = nextDays,
                                    tanggalAkhirSewa = nextEnd,
                                    tanggalJatuhTempo = nextEnd,
                                    hargaSewa = freshRegularPrice,
                                    nominalPembayaran = nextNominal,
                                    statusPembayaran = if (nextNominal >= freshRegularPrice && freshRegularPrice > 0L) "Lunas" else if (nextNominal > 0L) "Dibayar Sebagian" else "Belum Bayar"
                                )
                                onUpdateItem(updated)
                                if (onResolveFreshUnitPrice != null && item.unitId > 0L) {
                                    cardScope.launch {
                                        val liveDbPrice = runCatching {
                                            onResolveFreshUnitPrice(item.unitId, nextDays, "Sewa Reguler")
                                        }.getOrDefault(freshRegularPrice)
                                        if (liveDbPrice > 0L && liveDbPrice != updated.hargaSewa) {
                                            val syncedNom = if (shouldSyncNominal) liveDbPrice else updated.nominalPembayaran
                                            onUpdateItem(
                                                updated.copy(
                                                    hargaSewa = liveDbPrice,
                                                    nominalPembayaran = syncedNom,
                                                    statusPembayaran = if (syncedNom >= liveDbPrice) "Lunas" else if (syncedNom > 0L) "Dibayar Sebagian" else "Belum Bayar"
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            label = { Text("Sewa Reguler") },
                            colors = appFilterChipColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chip_unit_${index + 1}_reguler")
                        )
                        FilterChip(
                            selected = isHakMilik,
                            onClick = {
                                val selectedUnitEntity = allUnits.find { it.id == item.unitId }
                                val unitPrices = unitRentalPricesMap[item.unitId].orEmpty()
                                    .filter { it.price > 0L }
                                    .sortedBy { it.periodDays }
                                val defaultHmTarget = when {
                                    item.totalPeriodeHakMilik > 0 -> item.totalPeriodeHakMilik
                                    19 in sewaHakMilikPeriods -> 19
                                    sewaHakMilikPeriods.isNotEmpty() -> sewaHakMilikPeriods.maxOrNull() ?: 12
                                    else -> 12
                                }
                                val freshHmPrice = resolveReactiveUnitRentalPrice(
                                    unit = selectedUnitEntity,
                                    configuredPrices = unitPrices,
                                    periodDays = 30,
                                    isHakMilik = true
                                )
                                val start = item.tanggalMulaiSewa.ifBlank { DateCurrencyUtils.todayIso() }
                                val nextEnd = DateCurrencyUtils.addDaysToIsoDate(start, 30)
                                val shouldSyncNominal = item.nominalPembayaran <= 0L || item.nominalPembayaran == item.hargaSewa
                                val nextNominal = if (shouldSyncNominal) freshHmPrice else item.nominalPembayaran
                                val updated = item.copy(
                                    jenisProgram = "Sewa Hak Milik",
                                    totalPeriodeHakMilik = defaultHmTarget,
                                    pembayaranKeHakMilik = item.pembayaranKeHakMilik.coerceAtLeast(1),
                                    statusProgramHakMilik = "Aktif",
                                    durasiHari = 30,
                                    tanggalAkhirSewa = nextEnd,
                                    tanggalJatuhTempo = nextEnd,
                                    hargaSewa = freshHmPrice,
                                    nominalPembayaran = nextNominal,
                                    statusPembayaran = if (nextNominal >= freshHmPrice && freshHmPrice > 0L) "Lunas" else if (nextNominal > 0L) "Dibayar Sebagian" else "Belum Bayar"
                                )
                                onUpdateItem(updated)
                                if (onResolveFreshUnitPrice != null && item.unitId > 0L) {
                                    cardScope.launch {
                                        val liveDbPrice = runCatching {
                                            onResolveFreshUnitPrice(item.unitId, 30, "Sewa Hak Milik")
                                        }.getOrDefault(freshHmPrice)
                                        if (liveDbPrice > 0L && liveDbPrice != updated.hargaSewa) {
                                            val syncedNom = if (shouldSyncNominal) liveDbPrice else updated.nominalPembayaran
                                            onUpdateItem(
                                                updated.copy(
                                                    hargaSewa = liveDbPrice,
                                                    nominalPembayaran = syncedNom,
                                                    statusPembayaran = if (syncedNom >= liveDbPrice) "Lunas" else if (syncedNom > 0L) "Dibayar Sebagian" else "Belum Bayar"
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            label = { Text("Sewa Hak Milik") },
                            colors = appFilterChipColors(selectedContainerColor = Color(0xFF6A1B9A)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chip_unit_${index + 1}_hak_milik")
                        )
                    }

                    // Section D.3: Periode Harga Sewa Khusus Unit Ini (Sewa Reguler: Harian, Mingguan, Bulanan & Custom)
                    if (!isHakMilik) {
                        val currentUnitEntity = remember(allUnits, item.unitId) {
                            allUnits.find { it.id == item.unitId }
                        }
                        val configuredUnitPrices = remember(unitRentalPricesMap, item.unitId) {
                            unitRentalPricesMap[item.unitId].orEmpty()
                                .filter { it.price > 0L }
                                .sortedBy { it.periodDays }
                        }
                        val quickPeriodOptions = remember(currentUnitEntity, configuredUnitPrices) {
                            val byDays = linkedMapOf<Int, Pair<String, Long>>()
                            // Tambahkan paket standar Harian (1d), Mingguan (7d), Bulanan (30d) + seluruh periode kustom di Data Unit
                            listOf(1 to "Harian (1 Hari)", 7 to "Mingguan (7 Hari)", 30 to "Bulanan (30 Hari)").forEach { (days, label) ->
                                val exact = configuredUnitPrices.firstOrNull { it.periodDays == days }
                                val price = exact?.price ?: resolveReactiveUnitRentalPrice(
                                    unit = currentUnitEntity,
                                    configuredPrices = configuredUnitPrices,
                                    periodDays = days,
                                    isHakMilik = false
                                )
                                if (price > 0L) {
                                    byDays[days] = label to price
                                }
                            }
                            configuredUnitPrices.forEach { entry ->
                                if (entry.periodDays !in byDays) {
                                    byDays[entry.periodDays] = "${entry.periodDays} Hari" to entry.price
                                }
                            }
                            byDays.entries.sortedBy { it.key }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (quickPeriodOptions.isNotEmpty()) {
                            Text(
                                text = "Pilihan Paket & Periode Sewa Unit ${item.kodeUnitSnapshot.ifBlank { "#${index + 1}" }} (Otomatis Ambil Harga Data Unit):",
                                style = MaterialTheme.typography.labelSmall,
                                color = SeaBluePrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                quickPeriodOptions.forEach { (days, labelAndPrice) ->
                                    val (periodLabel, resolvedPrice) = labelAndPrice
                                    val isSelectedPeriod = item.durasiHari == days && item.hargaSewa == resolvedPrice
                                    FilterChip(
                                        selected = isSelectedPeriod,
                                        onClick = {
                                            val start = item.tanggalMulaiSewa.ifBlank { DateCurrencyUtils.todayIso() }
                                            val end = DateCurrencyUtils.addDaysToIsoDate(start, days)
                                            val shouldSyncNominal = item.nominalPembayaran <= 0L || item.nominalPembayaran == item.hargaSewa
                                            val nextNominal = if (shouldSyncNominal) resolvedPrice else item.nominalPembayaran
                                            val updatedPayStatus = when {
                                                nextNominal >= resolvedPrice -> "Lunas"
                                                nextNominal > 0L -> "Dibayar Sebagian"
                                                else -> "Belum Bayar"
                                            }
                                            val updated = item.copy(
                                                durasiHari = days,
                                                tanggalMulaiSewa = start,
                                                tanggalAkhirSewa = end,
                                                tanggalJatuhTempo = end,
                                                hargaSewa = resolvedPrice,
                                                nominalPembayaran = nextNominal,
                                                statusPembayaran = updatedPayStatus
                                            )
                                            onUpdateItem(updated)
                                            if (onResolveFreshUnitPrice != null && item.unitId > 0L) {
                                                cardScope.launch {
                                                    val liveDbPrice = runCatching {
                                                        onResolveFreshUnitPrice(item.unitId, days, "Sewa Reguler")
                                                    }.getOrDefault(resolvedPrice)
                                                    if (liveDbPrice > 0L && liveDbPrice != updated.hargaSewa) {
                                                        val syncedNom = if (shouldSyncNominal) liveDbPrice else updated.nominalPembayaran
                                                        onUpdateItem(
                                                            updated.copy(
                                                                hargaSewa = liveDbPrice,
                                                                nominalPembayaran = syncedNom,
                                                                statusPembayaran = if (syncedNom >= liveDbPrice) "Lunas" else if (syncedNom > 0L) "Dibayar Sebagian" else "Belum Bayar"
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        label = {
                                            Text(
                                                "$periodLabel • ${DateCurrencyUtils.formatRupiah(resolvedPrice)}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = appFilterChipColors(),
                                        modifier = Modifier.testTag("chip_unit_${index + 1}_period_${days}d")
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Tarif periode khusus unit ini belum diatur di master Unit. Masukkan harga sewa secara manual di bawah.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Section C: Progres Sewa Hak Milik Independen PER UNIT FREEZER
                    if (isHakMilik) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("panel_hak_milik_unit_${index + 1}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val targetPeriode = item.totalPeriodeHakMilik.coerceAtLeast(1)
                                val currentKe = item.pembayaranKeHakMilik.coerceIn(0, targetPeriode)
                                val sisaKe = (targetPeriode - currentKe).coerceAtLeast(0)
                                val progressFraction = (currentKe.toFloat() / targetPeriode.toFloat()).coerceIn(0f, 1f)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Progres Hak Milik Unit ${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF5B21B6),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ke-$currentKe dari $targetPeriode • Sisa ${sisaKe}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF6D28D9),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (sisaKe == 0) StatusPaidGreen else Color(0xFF7C3AED),
                                    trackColor = Color(0xFFEDE9FE)
                                )

                                Text(
                                    text = "Target Total Cicilan Unit Ini (Kali):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF5B21B6),
                                    fontWeight = FontWeight.SemiBold
                                )
                                val allPeriodPresets = remember(sewaHakMilikPeriods) {
                                    (listOf(8, 10) + sewaHakMilikPeriods).distinct().sorted()
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    allPeriodPresets.forEach { p ->
                                        FilterChip(
                                            selected = item.totalPeriodeHakMilik == p,
                                            onClick = {
                                                val selectedUnitEntity = allUnits.find { it.id == item.unitId }
                                                val unitPrices = unitRentalPricesMap[item.unitId].orEmpty()
                                                    .filter { it.price > 0L }
                                                    .sortedBy { it.periodDays }
                                                val freshHmPrice = resolveReactiveUnitRentalPrice(
                                                    unit = selectedUnitEntity,
                                                    configuredPrices = unitPrices,
                                                    periodDays = 30,
                                                    isHakMilik = true
                                                )
                                                val clampedKe = item.pembayaranKeHakMilik.coerceIn(1, p)
                                                val nextProgStatus = if (clampedKe >= p) "Lunas" else "Aktif"
                                                val shouldSyncNominal = item.nominalPembayaran <= 0L || item.nominalPembayaran == item.hargaSewa
                                                val nextNominal = if (shouldSyncNominal) freshHmPrice else item.nominalPembayaran
                                                val updated = item.copy(
                                                    totalPeriodeHakMilik = p,
                                                    pembayaranKeHakMilik = clampedKe,
                                                    statusProgramHakMilik = nextProgStatus,
                                                    hargaSewa = freshHmPrice,
                                                    nominalPembayaran = nextNominal,
                                                    statusPembayaran = if (nextNominal >= freshHmPrice && freshHmPrice > 0L) "Lunas" else if (nextNominal > 0L) "Dibayar Sebagian" else "Belum Bayar"
                                                )
                                                onUpdateItem(updated)
                                                if (onResolveFreshUnitPrice != null && item.unitId > 0L) {
                                                    cardScope.launch {
                                                        val liveDbPrice = runCatching {
                                                            onResolveFreshUnitPrice(item.unitId, 30, "Sewa Hak Milik")
                                                        }.getOrDefault(freshHmPrice)
                                                        if (liveDbPrice > 0L && liveDbPrice != updated.hargaSewa) {
                                                            val syncedNom = if (shouldSyncNominal) liveDbPrice else updated.nominalPembayaran
                                                            onUpdateItem(
                                                                updated.copy(
                                                                    hargaSewa = liveDbPrice,
                                                                    nominalPembayaran = syncedNom,
                                                                    statusPembayaran = if (syncedNom >= liveDbPrice) "Lunas" else if (syncedNom > 0L) "Dibayar Sebagian" else "Belum Bayar"
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            label = { Text("${p}x") },
                                            colors = appFilterChipColors(selectedContainerColor = Color(0xFF6A1B9A)),
                                            modifier = Modifier.testTag("chip_unit_${index + 1}_hm_${p}x")
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = if (item.pembayaranKeHakMilik == 0) "" else item.pembayaranKeHakMilik.toString(),
                                        onValueChange = { raw ->
                                            val parsed = raw.filter { it.isDigit() }.toIntOrNull() ?: 0
                                            val clamped = parsed.coerceIn(0, targetPeriode)
                                            val nextProgStatus = if (clamped >= targetPeriode && targetPeriode > 0) "Lunas" else "Aktif"
                                            onUpdateItem(
                                                item.copy(
                                                    pembayaranKeHakMilik = clamped,
                                                    statusProgramHakMilik = nextProgStatus
                                                )
                                            )
                                        },
                                        label = { Text("Pembayaran Ke-") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = appOutlinedTextFieldColors(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_unit_hm_ke_${index + 1}")
                                    )
                                    OutlinedTextField(
                                        value = if (item.totalPeriodeHakMilik == 0) "" else item.totalPeriodeHakMilik.toString(),
                                        onValueChange = { raw ->
                                            val parsed = raw.filter { it.isDigit() }.toIntOrNull() ?: 0
                                            val clampedKe = if (parsed > 0) item.pembayaranKeHakMilik.coerceAtMost(parsed) else item.pembayaranKeHakMilik
                                            val nextProgStatus = if (parsed > 0 && clampedKe >= parsed) "Lunas" else "Aktif"
                                            onUpdateItem(
                                                item.copy(
                                                    totalPeriodeHakMilik = parsed,
                                                    pembayaranKeHakMilik = clampedKe,
                                                    statusProgramHakMilik = nextProgStatus
                                                )
                                            )
                                        },
                                        label = { Text("Dari Total (x)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = appOutlinedTextFieldColors(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_unit_hm_total_${index + 1}")
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val dec = (item.pembayaranKeHakMilik - 1).coerceAtLeast(1)
                                            onUpdateItem(
                                                item.copy(
                                                    pembayaranKeHakMilik = dec,
                                                    statusProgramHakMilik = if (dec >= targetPeriode) "Lunas" else "Aktif"
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("-1 Cicilan", style = MaterialTheme.typography.labelSmall)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            val inc = (item.pembayaranKeHakMilik + 1).coerceAtMost(targetPeriode)
                                            onUpdateItem(
                                                item.copy(
                                                    pembayaranKeHakMilik = inc,
                                                    statusProgramHakMilik = if (inc >= targetPeriode) "Lunas" else "Aktif"
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_inc_unit_hm_${index + 1}")
                                    ) {
                                        Text("+1 Cicilan", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Harga Sewa & Nominal Pembayaran Unit
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = if (item.hargaSewa == 0L) "" else item.hargaSewa.toString(),
                            onValueChange = { v ->
                                val parsed = v.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                val updatedStatus = when {
                                    parsed > 0L && item.nominalPembayaran >= parsed -> "Lunas"
                                    item.nominalPembayaran > 0L -> "Dibayar Sebagian"
                                    else -> "Belum Bayar"
                                }
                                onUpdateItem(
                                    item.copy(
                                        hargaSewa = parsed,
                                        statusPembayaran = updatedStatus
                                    )
                                )
                            },
                            label = { Text("Harga Sewa Unit (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = appOutlinedTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_unit_price_${index + 1}")
                        )

                        OutlinedTextField(
                            value = if (item.nominalPembayaran == 0L) "" else item.nominalPembayaran.toString(),
                            onValueChange = { v ->
                                val parsed = v.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                val updatedStatus = when {
                                    item.hargaSewa > 0L && parsed >= item.hargaSewa -> "Lunas"
                                    parsed > 0L -> "Dibayar Sebagian"
                                    else -> "Belum Bayar"
                                }
                                onUpdateItem(
                                    item.copy(
                                        nominalPembayaran = parsed,
                                        statusPembayaran = updatedStatus
                                    )
                                )
                            },
                            label = { Text("Dibayar Unit (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = appOutlinedTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_unit_paid_${index + 1}")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tanggal Mulai Sewa & Tanggal Jatuh Tempo Masing-Masing Unit (Unit-Based Billing Schedule)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = item.tanggalMulaiSewa,
                            onValueChange = { newStart ->
                                val end = if (newStart.length == 10) {
                                    DateCurrencyUtils.addDaysToIsoDate(newStart, item.durasiHari.coerceAtLeast(1))
                                } else {
                                    item.tanggalAkhirSewa
                                }
                                onUpdateItem(
                                    item.copy(
                                        tanggalMulaiSewa = newStart,
                                        tanggalAkhirSewa = end,
                                        tanggalJatuhTempo = if (item.tanggalJatuhTempo.isBlank() || item.tanggalJatuhTempo == item.tanggalAkhirSewa) end else item.tanggalJatuhTempo
                                    )
                                )
                            },
                            label = { Text("Mulai Sewa (YYYY-MM-DD)") },
                            singleLine = true,
                            colors = appOutlinedTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_unit_start_date_${index + 1}")
                        )

                        OutlinedTextField(
                            value = item.tanggalJatuhTempo,
                            onValueChange = { newDue ->
                                onUpdateItem(item.copy(tanggalJatuhTempo = newDue))
                            },
                            label = { Text("Jatuh Tempo Unit") },
                            placeholder = { Text("Misal: 2026-11-10 / Tanggal 10") },
                            singleLine = true,
                            colors = appOutlinedTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_unit_due_date_${index + 1}")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Due Date Presets (Tanggal 5, Tanggal 10, Tanggal 15, Tanggal 20, Tanggal 25, +30 Hari)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Jadwal Jatuh Tempo",
                            tint = SeaBluePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Jadwal Cepat:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf("Tanggal 5", "Tanggal 10", "Tanggal 20", "+30 Hari").forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SeaBluePrimary.copy(alpha = 0.1f),
                                modifier = Modifier.clickable {
                                    val resolvedDue = if (preset == "+30 Hari") {
                                        DateCurrencyUtils.addDaysToIsoDate(
                                            item.tanggalMulaiSewa.ifBlank { DateCurrencyUtils.todayIso() },
                                            30
                                        )
                                    } else {
                                        preset
                                    }
                                    onUpdateItem(item.copy(tanggalJatuhTempo = resolvedDue))
                                }
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SeaBluePrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * SECTION 3.C: Ringkasan Pembayaran Multi-Unit
 * Menampilkan total unit disewa, rincian tagihan setiap unit, total tagihan, total pembayaran awal, dan sisa tagihan.
 */
@Composable
fun MultiUnitBillingSummaryCard(
    unitItems: List<TransactionUnitItemEntity>,
    ongkir: Long,
    deposit: Long,
    diskon: Long
) {
    val totalUnitPrice = unitItems.sumOf { it.hargaSewa.coerceAtLeast(0L) }
    val totalTagihan = (totalUnitPrice + ongkir + deposit - diskon).coerceAtLeast(0L)
    val totalBayarUnits = unitItems.sumOf { it.nominalPembayaran.coerceAtLeast(0L) }
    val sisaTagihan = (totalTagihan - totalBayarUnits).coerceAtLeast(0L)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("multi_unit_billing_summary_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Ringkasan Pembayaran",
                        tint = LightBlueAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "C. Ringkasan Pembayaran (${unitItems.size} Unit)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Surface(
                    color = LightBlueAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Total Unit: ${unitItems.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LightBlueAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tabel Rincian per Unit
            unitItems.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${idx + 1}. ${item.namaUnitSnapshot.ifBlank { "Unit #${idx + 1}" }} (${
                                if (item.jenisProgram.equals("Sewa Hak Milik", true)) {
                                    "Hak Milik Ke-${item.pembayaranKeHakMilik}/${item.totalPeriodeHakMilik.coerceAtLeast(1)}"
                                } else {
                                    "Reguler ${item.durasiHari} Hari"
                                }
                            })",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Mulai: ${item.tanggalMulaiSewa.ifBlank { "-" }} • Jatuh Tempo: ${item.tanggalJatuhTempo.ifBlank { "-" }} • ${item.statusPembayaran}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = DateCurrencyUtils.formatRupiah(item.hargaSewa),
                        style = MaterialTheme.typography.bodySmall,
                        color = LightBlueAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.18f)
            )

            SummaryLine("Total Biaya Sewa (${unitItems.size} Unit)", DateCurrencyUtils.formatRupiah(totalUnitPrice))
            if (ongkir > 0L) SummaryLine("Biaya Ongkir / Antar-Jemput", "+ ${DateCurrencyUtils.formatRupiah(ongkir)}")
            if (deposit > 0L) SummaryLine("Deposit Jaminan", "+ ${DateCurrencyUtils.formatRupiah(deposit)}")
            if (diskon > 0L) SummaryLine("Potongan Diskon", "- ${DateCurrencyUtils.formatRupiah(diskon)}")

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TOTAL TAGIHAN SELURUH UNIT",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = LightBlueAccent
                )
                Text(
                    text = DateCurrencyUtils.formatRupiah(totalTagihan),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = LightBlueAccent
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SummaryLine("Total Pembayaran Unit", DateCurrencyUtils.formatRupiah(totalBayarUnits))
            SummaryLine(
                label = "Sisa Tagihan",
                value = DateCurrencyUtils.formatRupiah(sisaTagihan),
                highlight = sisaTagihan > 0L
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Color(0xFFFCD34D) else Color.White
        )
    }
}

/**
 * SECTION 2 & SECTION 3.D:
 * Dokumentasi Foto Berdasarkan Jumlah Unit (1 kartu dokumentasi terstruktur untuk setiap unit yang disewa).
 * Setiap unit memiliki:
 * - A. Foto Kondisi Unit (4 slot: Tampak Depan, Tampak Samping, Bagian Dalam, Kondisi Fisik Tambahan)
 * - B. Foto Serah Terima (4 slot: Saat Diserahkan ke Pelanggan, Unit di Lokasi Pelanggan, Bukti Penerimaan, Dokumentasi Tambahan)
 * - Indikator: Foto kondisi unit: X/4 | Foto serah terima: Y/4 | Status: Lengkap / Belum Lengkap
 */
@Composable
fun MultiUnitPhotoDocumentationSection(
    transactionFolder: String,
    unitItems: List<TransactionUnitItemEntity>,
    onUnitItemsChanged: (List<TransactionUnitItemEntity>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.22f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "D. Dokumentasi Foto Berdasarkan Jumlah Unit (${unitItems.size} Unit)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
                Text(
                    text = "Setiap unit yang disewa memiliki folder dan blok dokumentasi foto terpisah (Kondisi Unit & Serah Terima).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                unitItems.forEachIndexed { index, item ->
                    PerUnitDocumentationCard(
                        unitIndex = index + 1,
                        transactionFolder = transactionFolder,
                        item = item,
                        onUpdateItem = { updated ->
                            val newList = unitItems.toMutableList().apply {
                                set(index, updated)
                            }
                            onUnitItemsChanged(newList)
                        }
                    )
                    if (index < unitItems.lastIndex) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PerUnitDocumentationCard(
    unitIndex: Int,
    transactionFolder: String,
    item: TransactionUnitItemEntity,
    onUpdateItem: (TransactionUnitItemEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(unitIndex == 1) }
    val isComplete = item.isDocumentationComplete

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("doc_card_unit_$unitIndex"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(
            1.dp,
            if (isComplete) StatusPaidGreen.copy(alpha = 0.5f) else Color(0xFFCBD5E1)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dokumentasi Unit $unitIndex - ${item.namaUnitSnapshot.ifBlank { "Freezer #$unitIndex" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val uploadedTwoSlotCount = (if (item.fotoKondisiDepanUri.isNotBlank()) 1 else 0) +
                        (if (item.fotoSerahTerimaPelangganUri.isNotBlank()) 1 else 0)
                    Text(
                        text = "Dokumentasi Wajib Unit: $uploadedTwoSlotCount/2 (Kondisi Depan & Serah Terima)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeaBluePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isComplete) Color(0xFFD1FAE5) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isComplete) Icons.Default.Verified else Icons.Default.Warning,
                                contentDescription = item.statusKelengkapanFoto,
                                tint = if (isComplete) Color(0xFF065F46) else Color(0xFF92400E),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.statusKelengkapanFoto,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isComplete) Color(0xFF065F46) else Color(0xFF92400E)
                            )
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Buka Dokumentasi Unit"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Section F: Disederhanakan menjadi tepat 2 slot dokumentasi per unit:
                    // 1. Foto Kondisi Unit (Tampak Depan)
                    // 2. Foto Serah Terima Unit
                    // Foto lama/legacy pada database tetap dipertahankan non-destruktif saat edit.
                    Text(
                        text = "Dokumentasi Foto Unit $unitIndex (2 Foto Utama)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        UnitPhotoSlotItem(
                            modifier = Modifier.weight(1f),
                            title = "1. Foto Kondisi Unit (Tampak Depan)",
                            slotKey = "kondisi_depan",
                            currentUri = item.fotoKondisiDepanUri,
                            transactionFolder = transactionFolder,
                            unitId = item.unitId,
                            unitIndex = unitIndex,
                            unitName = item.namaUnitSnapshot,
                            onPhotoUpdated = { onUpdateItem(item.copy(fotoKondisiDepanUri = it)) }
                        )
                        UnitPhotoSlotItem(
                            modifier = Modifier.weight(1f),
                            title = "2. Foto Serah Terima Unit",
                            slotKey = "serah_terima_unit",
                            currentUri = item.fotoSerahTerimaPelangganUri,
                            transactionFolder = transactionFolder,
                            unitId = item.unitId,
                            unitIndex = unitIndex,
                            unitName = item.namaUnitSnapshot,
                            onPhotoUpdated = { onUpdateItem(item.copy(fotoSerahTerimaPelangganUri = it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitPhotoSlotItem(
    modifier: Modifier = Modifier,
    title: String,
    slotKey: String,
    currentUri: String,
    transactionFolder: String,
    unitId: Long,
    unitIndex: Int,
    unitName: String,
    onPhotoUpdated: (String) -> Unit
) {
    val context = LocalContext.current
    val hasPhoto = currentUri.isNotBlank() && File(currentUri).exists()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val saved = PerUnitPhotoStorageHelper.saveUnitPhotoFromBitmap(
                context = context,
                bitmap = bitmap,
                transactionFolder = transactionFolder,
                unitId = unitId,
                unitIndex = unitIndex,
                slotKey = slotKey
            )
            onPhotoUpdated(saved.absolutePath)
        } else {
            val fallback = PerUnitPhotoStorageHelper.generateSimulatedUnitCameraPhoto(
                context = context,
                transactionFolder = transactionFolder,
                unitId = unitId,
                unitIndex = unitIndex,
                unitName = unitName,
                slotKey = slotKey,
                slotTitle = title
            )
            onPhotoUpdated(fallback.absolutePath)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            val fallback = PerUnitPhotoStorageHelper.generateSimulatedUnitCameraPhoto(
                context = context,
                transactionFolder = transactionFolder,
                unitId = unitId,
                unitIndex = unitIndex,
                unitName = unitName,
                slotKey = slotKey,
                slotTitle = title
            )
            onPhotoUpdated(fallback.absolutePath)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = PerUnitPhotoStorageHelper.saveUnitPhotoFromUri(
                context = context,
                uri = uri,
                transactionFolder = transactionFolder,
                unitId = unitId,
                unitIndex = unitIndex,
                slotKey = slotKey
            )
            if (saved != null) {
                onPhotoUpdated(saved.absolutePath)
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (hasPhoto) StatusPaidGreen.copy(alpha = 0.6f) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasPhoto) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Tersimpan",
                        tint = StatusPaidGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (hasPhoto) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                ) {
                    val thumbPath = SmartImageProcessor.getExistingThumbnailPathOrNull(context, currentUri)
                    val displayModel = thumbPath?.let { File(it) } ?: File(currentUri)
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(displayModel)
                            .size(300, 300)
                            .scale(Scale.FILL)
                            .crossfade(true)
                            .build(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { onPhotoUpdated("") },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(26.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Foto",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = {
                        val hasCamPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCamPerm) {
                            cameraLauncher.launch(null)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .testTag("btn_unit_${unitIndex}_cam_$slotKey"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Kamera", modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Kamera", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .testTag("btn_unit_${unitIndex}_gal_$slotKey"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeri", modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Galeri", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
