package com.example.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerReturnEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PdfTemplateSettings
import com.example.data.local.RentalTransactionEntity
import com.example.util.DateCurrencyUtils
import java.io.File
import java.io.FileOutputStream

object ReturnPdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 width in PostScript points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in PostScript points (72 dpi)
    private const val MARGIN = 34f

    /**
     * Section 5: Generates a professional A4 PDF "BUKTI SERAH TERIMA PENGEMBALIAN FREEZER"
     * with Customize Template PDF support (background image/PDF, transparency, margins, logo position),
     * Payment Status Stamp on top-right of every page, Permanent Owner Signature on every page,
     * customer & unit identities, return condition, 4 un-cropped return documentation photos, and digital signatures.
     */
    fun generateReturnPdf(
        context: Context,
        returnEntity: FreezerReturnEntity,
        customer: CustomerEntity? = null,
        unit: FreezerUnitEntity? = null,
        companyProfile: CompanyProfile = CompanyProfile(),
        maskNikInPdf: Boolean = true,
        pdfTemplateSettings: PdfTemplateSettings = PdfTemplateSettings(),
        linkedTransaction: RentalTransactionEntity? = null
    ): File {
        val returnsDir = File(context.filesDir, "returns_pdf")
        if (!returnsDir.exists()) returnsDir.mkdirs()

        val safeFileName = returnEntity.nomorDokumenPengembalian.replace("/", "_") + ".pdf"
        val outFile = File(returnsDir, safeFileName)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val navyColor = Color.rgb(10, 37, 64)
        val seaBlue = Color.rgb(2, 132, 199)
        val lightBlueBg = Color.rgb(224, 242, 254)
        val slateGray = Color.rgb(100, 116, 139)
        val darkText = Color.rgb(15, 23, 42)
        val borderGray = Color.rgb(203, 213, 225)
        val paidGreen = Color.rgb(16, 185, 129)
        val repairOrange = Color.rgb(239, 68, 68)
        val warnYellow = Color.rgb(245, 158, 11)

        val baseNormalTypeface = PdfTemplateRenderer.resolveTypeface(pdfTemplateSettings.fontFamily, pdfTemplateSettings.fontWeight, forceBold = false)
        val baseBoldTypeface = PdfTemplateRenderer.resolveTypeface(pdfTemplateSettings.fontFamily, pdfTemplateSettings.fontWeight, forceBold = true)
        val bodyFontSize = pdfTemplateSettings.bodyFontSizePt.coerceIn(7.5f, 11.5f)
        val subFontSize = pdfTemplateSettings.subtitleFontSizePt.coerceIn(8.5f, 13f)

        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = subFontSize
            typeface = baseBoldTypeface
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slateGray
            textSize = bodyFontSize
            typeface = baseNormalTypeface
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = bodyFontSize
            typeface = baseBoldTypeface
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = borderGray
        }

        val stampLabel = PdfTemplateRenderer.resolvePaymentStampLabel(linkedTransaction)

        // Render Customize Template PDF background & top-right Payment Status Stamp
        PdfTemplateRenderer.drawPageBackground(
            context = context,
            canvas = canvas,
            template = pdfTemplateSettings,
            pageWidth = PAGE_WIDTH,
            pageHeight = PAGE_HEIGHT
        )
        PdfTemplateRenderer.drawPaymentStatusStampOnPage(
            canvas = canvas,
            stampLabel = stampLabel,
            template = pdfTemplateSettings,
            pageWidth = PAGE_WIDTH
        )

        // 1. HEADER BANNER (BUKTI SERAH TERIMA PENGEMBALIAN FREEZER)
        var y = PdfTemplateRenderer.drawHeaderBannerWithTemplate(
            context = context,
            canvas = canvas,
            companyProfile = companyProfile,
            template = pdfTemplateSettings,
            documentTitle = "DOKUMEN PENGEMBALIAN",
            documentNumberLabel = "No: ${returnEntity.nomorDokumenPengembalian}",
            documentSubLabel1 = "Ref Inv: ${returnEntity.nomorInvoiceSnapshot}",
            documentSubLabel2 = "Tgl: ${DateCurrencyUtils.formatDisplayDate(returnEntity.tanggalPengembalian)}",
            bannerSubtitle = "BUKTI SERAH TERIMA PENGEMBALIAN FREEZER",
            pageWidth = PAGE_WIDTH
        )

        // 2. IDENTITAS PELANGGAN (LEFT) & IDENTITAS UNIT FREEZER (RIGHT)
        val colWidth = (PAGE_WIDTH - (MARGIN * 2) - 12f) / 2f
        val leftX = MARGIN
        val rightX = MARGIN + colWidth + 12f
        val boxHeight = 112f

        fillPaint.color = Color.argb(242, 248, 250, 252)
        val custRect = RectF(leftX, y, leftX + colWidth, y + boxHeight)
        canvas.drawRoundRect(custRect, 6f, 6f, fillPaint)
        canvas.drawRoundRect(custRect, 6f, 6f, strokePaint)

        val kodeCust = customer?.kodePelanggan?.ifBlank { "CUST/SAF/${customer.id.toString().padStart(4, '0')}" }
            ?: "CUST/SAF/${returnEntity.customerId.toString().padStart(4, '0')}"
        canvas.drawText("IDENTITAS PELANGGAN ($kodeCust)", leftX + 8f, y + 16f, sectionTitlePaint)
        val nikRaw = customer?.nik ?: returnEntity.nikPelangganSnapshot.ifBlank { "-" }
        val nikDisplay = DateCurrencyUtils.maskNik(nikRaw, maskNikInPdf)
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, leftX + 8f, y + 32f, colWidth - 16f,
            "Nama Pelanggan",
            customer?.namaLengkap ?: returnEntity.namaPelangganSnapshot,
            labelPaint, valuePaint
        )
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, leftX + 8f, y + 47f, colWidth - 16f,
            "NIK Pelanggan",
            nikDisplay,
            labelPaint, valuePaint
        )
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, leftX + 8f, y + 62f, colWidth - 16f,
            "Nomor WhatsApp",
            customer?.nomorHp ?: returnEntity.nomorWaPelangganSnapshot.ifBlank { "-" },
            labelPaint, valuePaint
        )
        InvoicePdfGenerator.drawMultilineKeyValue(
            canvas, leftX + 8f, y + 77f, colWidth - 16f,
            "Alamat KTP",
            customer?.alamatLengkap ?: returnEntity.alamatKtpSnapshot.ifBlank { "-" },
            labelPaint, valuePaint
        )

        val unitRect = RectF(rightX, y, rightX + colWidth, y + boxHeight)
        canvas.drawRoundRect(unitRect, 6f, 6f, fillPaint)
        canvas.drawRoundRect(unitRect, 6f, 6f, strokePaint)

        canvas.drawText("IDENTITAS UNIT FREEZER", rightX + 8f, y + 16f, sectionTitlePaint)
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, rightX + 8f, y + 32f, colWidth - 16f,
            "Kode Unit",
            returnEntity.kodeUnitSnapshot,
            labelPaint, valuePaint
        )
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, rightX + 8f, y + 47f, colWidth - 16f,
            "Nama Unit",
            unit?.namaUnit ?: returnEntity.namaUnitSnapshot,
            labelPaint, valuePaint
        )
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, rightX + 8f, y + 62f, colWidth - 16f,
            "Merek & Tipe",
            returnEntity.merekTipeSnapshot.ifBlank { "${unit?.merek ?: "-"} ${unit?.model ?: ""}".trim() },
            labelPaint, valuePaint
        )
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, rightX + 8f, y + 77f, colWidth - 16f,
            "Tgl Mulai Sewa",
            DateCurrencyUtils.formatDisplayDate(returnEntity.tanggalMulaiSewaSnapshot),
            labelPaint, valuePaint
        )
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, rightX + 8f, y + 92f, colWidth - 16f,
            "Tgl Pengembalian",
            "${DateCurrencyUtils.formatDisplayDate(returnEntity.tanggalPengembalian)} (${returnEntity.jamPengembalian})",
            labelPaint, valuePaint
        )

        y += boxHeight + 10f

        // 3. RINCIAN PENGEMBALIAN & KONDISI UNIT SAAT DIKEMBALIKAN
        val condBoxHeight = 100f
        fillPaint.color = lightBlueBg
        val condHeaderRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 20f)
        canvas.drawRoundRect(condHeaderRect, 4f, 4f, fillPaint)
        canvas.drawText("DATA SERAH TERIMA PENGEMBALIAN & KONDISI UNIT", MARGIN + 8f, y + 14f, sectionTitlePaint)
        y += 23f

        fillPaint.color = Color.rgb(248, 250, 252)
        val condBodyRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + condBoxHeight)
        canvas.drawRoundRect(condBodyRect, 6f, 6f, fillPaint)
        canvas.drawRoundRect(condBodyRect, 6f, 6f, strokePaint)

        val fullInnerWidth = PAGE_WIDTH - (MARGIN * 2) - 16f
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, MARGIN + 8f, y + 15f, fullInnerWidth,
            "No. Transaksi Sewa",
            "${returnEntity.nomorInvoiceSnapshot} | Harga Sewa: ${DateCurrencyUtils.formatRupiah(returnEntity.hargaSewaSnapshot)}",
            labelPaint, valuePaint
        )
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, MARGIN + 8f, y + 30f, fullInnerWidth,
            "Alamat Pakai",
            returnEntity.alamatPenggunaanSnapshot.ifBlank { "-" },
            labelPaint, valuePaint
        )
        val gpsLastStr = if (returnEntity.latitudeTerakhir != null && returnEntity.longitudeTerakhir != null) {
            String.format("%.6f, %.6f", returnEntity.latitudeTerakhir, returnEntity.longitudeTerakhir)
        } else {
            "-"
        }
        InvoicePdfGenerator.drawKeyValueRow(
            canvas, MARGIN + 8f, y + 45f, fullInnerWidth,
            "GPS Terakhir",
            "$gpsLastStr | Petugas Penerima: ${returnEntity.namaPetugasPenerima}",
            labelPaint, valuePaint
        )
        InvoicePdfGenerator.drawMultilineKeyValue(
            canvas, MARGIN + 8f, y + 60f, fullInnerWidth,
            "Catatan Kondisi",
            returnEntity.catatanKondisiUnit.ifBlank { "Unit telah diterima kembali sesuai pemeriksaan." },
            labelPaint, valuePaint
        )

        // Condition & Unit Status Badges inside box bottom
        val isDamaged = returnEntity.statusUnitSetelahKembali.equals("Dalam Perbaikan", true)
        fillPaint.color = when {
            isDamaged -> repairOrange
            returnEntity.kondisiUnitSaatKembali.contains("catatan", true) -> warnYellow
            else -> paidGreen
        }
        val condBadgeRect = RectF(MARGIN + 8f, y + 78f, MARGIN + 255f, y + 96f)
        canvas.drawRoundRect(condBadgeRect, 4f, 4f, fillPaint)
        val whiteBoldPaint = Paint(valuePaint).apply { color = Color.WHITE; textSize = 8.4f }
        canvas.drawText(
            "KONDISI KEMBALI: ${returnEntity.kondisiUnitSaatKembali.uppercase()}",
            MARGIN + 14f,
            y + 90f,
            whiteBoldPaint
        )

        fillPaint.color = if (isDamaged) repairOrange else seaBlue
        val statusUnitBadgeRect = RectF(MARGIN + 265f, y + 78f, PAGE_WIDTH - MARGIN - 8f, y + 96f)
        canvas.drawRoundRect(statusUnitBadgeRect, 4f, 4f, fillPaint)
        canvas.drawText(
            "TRANSAKSI: SELESAI | STATUS UNIT: ${returnEntity.statusUnitSetelahKembali.uppercase()}",
            MARGIN + 273f,
            y + 90f,
            whiteBoldPaint
        )

        y += condBoxHeight + 10f

        // 4. FOTO DOKUMENTASI PENGEMBALIAN (2x2 Grid so photos are large and never cropped)
        canvas.drawText("FOTO DOKUMENTASI PENGEMBALIAN FREEZER", MARGIN, y + 11f, sectionTitlePaint)
        y += 15f

        val returnPhotos = listOf(
            "1. Kondisi Saat Dikembalikan" to returnEntity.fotoKondisiKembaliUri,
            "2. Bagian Luar Freezer" to returnEntity.fotoBagianLuarUri,
            "3. Bagian Dalam Freezer" to returnEntity.fotoBagianDalamUri,
            "4. Foto Tambahan Pengembalian" to returnEntity.fotoTambahanUri
        )

        val gridGap = 12f
        val cellWidth = (PAGE_WIDTH - (MARGIN * 2) - gridGap) / 2f
        val cellHeight = 120f

        for ((index, pair) in returnPhotos.withIndex()) {
            val col = index % 2
            val row = index / 2
            val cx = MARGIN + col * (cellWidth + gridGap)
            val cy = y + row * (cellHeight + 8f)
            InvoicePdfGenerator.drawPhotoBox(
                context = context,
                canvas = canvas,
                x = cx,
                y = cy,
                width = cellWidth,
                height = cellHeight,
                caption = pair.first,
                uriOrPath = pair.second
            )
        }

        y += (cellHeight * 2) + 18f

        // 5. TANDA TANGAN PELANGGAN & TANDA TANGAN PETUGAS
        val sigBoxHeight = 88f
        val sigColWidth = (PAGE_WIDTH - (MARGIN * 2) - 14f) / 2f

        val custSigRect = RectF(MARGIN, y, MARGIN + sigColWidth, y + sigBoxHeight)
        canvas.drawRoundRect(custSigRect, 6f, 6f, strokePaint)
        canvas.drawText("Tanda Tangan Pelanggan / Pengembali", MARGIN + 8f, y + 14f, sectionTitlePaint)
        InvoicePdfGenerator.drawSignatureBitmapInBox(
            context = context,
            canvas = canvas,
            uriOrPath = returnEntity.tandaTanganPelangganUri,
            rect = RectF(MARGIN + 14f, y + 18f, MARGIN + sigColWidth - 14f, y + 60f)
        )
        canvas.drawText(
            "Pelanggan: ${returnEntity.namaPelangganSnapshot}",
            MARGIN + 8f,
            y + 72f,
            valuePaint
        )
        canvas.drawText(
            "Waktu Kembali: ${DateCurrencyUtils.formatDisplayDate(returnEntity.tanggalPengembalian)} ${returnEntity.jamPengembalian}",
            MARGIN + 8f,
            y + 83f,
            labelPaint
        )

        val offX = MARGIN + sigColWidth + 14f
        val offSigRect = RectF(offX, y, offX + sigColWidth, y + sigBoxHeight)
        canvas.drawRoundRect(offSigRect, 6f, 6f, strokePaint)
        canvas.drawText("Tanda Tangan Petugas Penerima (${companyProfile.namaPerusahaan})", offX + 8f, y + 14f, sectionTitlePaint)
        InvoicePdfGenerator.drawSignatureBitmapInBox(
            context = context,
            canvas = canvas,
            uriOrPath = returnEntity.tandaTanganPetugasUri,
            rect = RectF(offX + 14f, y + 18f, offX + sigColWidth - 14f, y + 60f),
            fallbackOfficerName = returnEntity.namaPetugasPenerima.ifBlank { "Petugas SAF SOC" }
        )
        canvas.drawText(
            "Petugas Penerima: ${returnEntity.namaPetugasPenerima}",
            offX + 8f,
            y + 72f,
            valuePaint
        )
        canvas.drawText(
            "Status Unit: ${returnEntity.statusUnitSetelahKembali} (${returnEntity.kondisiUnitSaatKembali})",
            offX + 8f,
            y + 83f,
            labelPaint
        )

        y += sigBoxHeight + 6f

        // 6. FOOTER STATEMENT (above the Permanent Owner Signature strip)
        val footerReserve = pdfTemplateSettings.footerHeightPt.coerceIn(26f, 44f) + 9f
        fillPaint.color = lightBlueBg
        val footerRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, (PAGE_HEIGHT - footerReserve).coerceAtLeast(y + 20f))
        canvas.drawRoundRect(footerRect, 5f, 5f, fillPaint)
        val statementPaint = Paint(valuePaint).apply {
            color = navyColor
            textSize = 7.8f
        }
        val statement = "BUKTI SAH PENGEMBALIAN: \"Unit freezer di atas telah diserahkan kembali oleh pelanggan kepada pihak SEWAin Aja Freezer SOC dengan kondisi sebagaimana tercatat pada dokumen ini.\""
        InvoicePdfGenerator.drawWrappedText(
            canvas,
            statement,
            MARGIN + 8f,
            y + 11f,
            PAGE_WIDTH - (MARGIN * 2) - 16f,
            statementPaint,
            maxLines = 2
        )

        // Section 5: Draw Permanent Owner Signature on Return PDF page
        PdfTemplateRenderer.drawOwnerPermanentSignatureOnPage(
            context = context,
            canvas = canvas,
            companyProfile = companyProfile,
            template = pdfTemplateSettings,
            snapshotUri = returnEntity.tandaTanganOwnerSnapshotUri,
            snapshotSizePt = returnEntity.ukuranTandaTanganOwnerSnapshot,
            snapshotPosition = returnEntity.posisiTandaTanganOwnerSnapshot,
            pageNumber = 1,
            pageWidth = PAGE_WIDTH,
            pageHeight = PAGE_HEIGHT
        )

        pdfDocument.finishPage(page)

        FileOutputStream(outFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return outFile
    }

    fun shareReturnPdfViaWhatsApp(
        context: Context,
        pdfFile: File,
        returnEntity: FreezerReturnEntity
    ) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, pdfFile)
        val message = buildString {
            appendLine("*[BUKTI SERAH TERIMA PENGEMBALIAN FREEZER]*")
            appendLine("*SEWAin Aja Freezer SOC*")
            appendLine("No. Dokumen: *${returnEntity.nomorDokumenPengembalian}*")
            appendLine("Ref. Invoice: ${returnEntity.nomorInvoiceSnapshot}")
            appendLine("Pelanggan: ${returnEntity.namaPelangganSnapshot}")
            appendLine("Unit Freezer: ${returnEntity.namaUnitSnapshot} (${returnEntity.kodeUnitSnapshot})")
            appendLine("Tgl Pengembalian: ${DateCurrencyUtils.formatDisplayDate(returnEntity.tanggalPengembalian)} ${returnEntity.jamPengembalian}")
            appendLine("Kondisi Saat Kembali: *${returnEntity.kondisiUnitSaatKembali}*")
            appendLine("Status Transaksi: *SELESAI*")
            appendLine("Petugas Penerima: ${returnEntity.namaPetugasPenerima}")
            appendLine("\nTerima kasih telah menyewa unit freezer di SEWAin Aja Freezer SOC (Solo Raya).")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(sendIntent, "Bagikan Bukti Pengembalian PDF via WhatsApp").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
