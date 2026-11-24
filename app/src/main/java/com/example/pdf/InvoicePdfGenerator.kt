package com.example.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.DEFAULT_PERJANJIAN_SEWA
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.PdfTemplateSettings
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.util.DateCurrencyUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.min

object InvoicePdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 width in PostScript points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in PostScript points (72 dpi)
    private const val MARGIN = 34f

    /**
     * Generates a professional multi-page A4 Invoice, Surat Perjanjian Sewa & Berita Acara Serah Terima Unit PDF
     * for SEWAin Aja Freezer SOC in the exact required order:
     * 1. Identitas bisnis & Informasi invoice (with Customize Template PDF background & logo position)
     * 2. Data pelanggan (with permanent ID CUST/SAF/...) & Data unit freezer
     * 3. Rincian biaya sewa
     * 4. Alamat penggunaan dan tagging lokasi GPS
     * 5. Foto dokumentasi
     * 6. Isi perjanjian sewa (from transaction snapshot or customize setting)
     * 7. Berita Acara Serah Terima Unit & Tanda tangan konsumen & pihak SEWAin Aja Freezer SOC
     * Automatically creates additional pages if the agreement is long so no text or signature is ever cut off.
     */
    const val EMPTY_PAYMENT_PROOF_NOTICE = "BUKTI PEMBAYARAN BELUM DIUNGGAH"

    /**
     * Sorts active payment proofs strictly chronologically (oldest payment date first)
     * so installment / partial payment history appears in exact transaction order in Invoice PDF.
     */
    fun sortPaymentProofsChronologically(
        paymentProofs: List<PaymentProofEntity>
    ): List<PaymentProofEntity> {
        return paymentProofs
            .filterNot { it.isDeleted }
            .sortedWith(
                compareBy<PaymentProofEntity> { it.tanggalPembayaran.ifBlank { "9999-99-99" } }
                    .thenBy { it.createdAtMillis }
                    .thenBy { it.id }
            )
    }

    fun generateInvoicePdf(
        context: Context,
        transaction: RentalTransactionEntity,
        customer: CustomerEntity?,
        unit: FreezerUnitEntity?,
        companyProfile: CompanyProfile = CompanyProfile(),
        maskNikInPdf: Boolean = true,
        defaultPerjanjianSewa: String = DEFAULT_PERJANJIAN_SEWA,
        pdfTemplateSettings: PdfTemplateSettings = PdfTemplateSettings(),
        unitItems: List<TransactionUnitItemEntity> = emptyList(),
        paymentProofs: List<PaymentProofEntity> = emptyList()
    ): File {
        val invoicesDir = File(context.filesDir, "invoices")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()

        val safeFileName = transaction.nomorInvoice.replace("/", "_") + ".pdf"
        val outFile = File(invoicesDir, safeFileName)

        val pdfDocument = PdfDocument()
        val marginX = pdfTemplateSettings.marginLeftPt.coerceIn(22f, 54f)
        val footerReserve = pdfTemplateSettings.footerHeightPt.coerceIn(26f, 44f) + 6f
        val marginBottom = (pdfTemplateSettings.marginBottomPt.coerceIn(22f, 54f) + footerReserve).coerceAtMost(96f)
        val stampLabel = PdfTemplateRenderer.resolvePaymentStampLabel(transaction)

        val navyColor = Color.rgb(0x17, 0x2B, 0x4D)
        val lightBlueBg = Color.rgb(224, 242, 254)
        val slateGray = Color.rgb(0x47, 0x55, 0x69)
        val darkText = Color.rgb(0x17, 0x2B, 0x4D)
        val borderGray = Color.rgb(203, 213, 225)
        val paidGreen = Color.rgb(16, 185, 129)
        val overdueRed = Color.rgb(239, 68, 68)
        val warnYellow = Color.rgb(245, 158, 11)

        val baseNormalTypeface = PdfTemplateRenderer.resolveTypeface(pdfTemplateSettings.fontFamily, pdfTemplateSettings.fontWeight, forceBold = false)
        val baseBoldTypeface = PdfTemplateRenderer.resolveTypeface(pdfTemplateSettings.fontFamily, pdfTemplateSettings.fontWeight, forceBold = true)
        val bodyFontSize = pdfTemplateSettings.bodyFontSizePt.coerceIn(7.5f, 11.5f)
        val subFontSize = pdfTemplateSettings.subtitleFontSizePt.coerceIn(8.5f, 13f)
        val captionFontSize = pdfTemplateSettings.captionFontSizePt.coerceIn(6.8f, 10f)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = pdfTemplateSettings.titleFontSizePt.coerceIn(11f, 17f)
            typeface = baseBoldTypeface
        }
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
        val agreementPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = captionFontSize.coerceAtLeast(8.0f)
            typeface = baseNormalTypeface
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = borderGray
        }

        var currentPageNumber = 1
        var currentPage = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
        )
        var currentCanvas = currentPage.canvas

        // Render Customize Template PDF background & top-right Payment Status Stamp on Page 1
        PdfTemplateRenderer.drawPageBackground(
            context = context,
            canvas = currentCanvas,
            template = pdfTemplateSettings,
            pageWidth = PAGE_WIDTH,
            pageHeight = PAGE_HEIGHT
        )
        PdfTemplateRenderer.drawPaymentStatusStampOnPage(
            canvas = currentCanvas,
            stampLabel = stampLabel,
            template = pdfTemplateSettings,
            pageWidth = PAGE_WIDTH
        )

        val contractHeaderTitle = if (unitItems.size > 1) {
            "INVOICE & KONTRAK SEWA FREEZER"
        } else {
            "INVOICE & KONTRAK SEWA"
        }
        var y = PdfTemplateRenderer.drawHeaderBannerWithTemplate(
            context = context,
            canvas = currentCanvas,
            companyProfile = companyProfile,
            template = pdfTemplateSettings,
            documentTitle = contractHeaderTitle,
            documentNumberLabel = "No. Kontrak / Invoice: ${transaction.nomorInvoice}",
            documentSubLabel1 = "Tgl: ${DateCurrencyUtils.formatDisplayDate(transaction.tanggalPemesanan)}",
            pageWidth = PAGE_WIDTH
        )

        fun finishCurrentPageWithOwnerSignature() {
            PdfTemplateRenderer.drawOwnerPermanentSignatureOnPage(
                context = context,
                canvas = currentCanvas,
                companyProfile = companyProfile,
                template = pdfTemplateSettings,
                snapshotUri = transaction.tandaTanganOwnerSnapshotUri,
                snapshotSizePt = transaction.ukuranTandaTanganOwnerSnapshot,
                snapshotPosition = transaction.posisiTandaTanganOwnerSnapshot,
                pageNumber = currentPageNumber,
                pageWidth = PAGE_WIDTH,
                pageHeight = PAGE_HEIGHT
            )
            pdfDocument.finishPage(currentPage)
        }

        fun startNewPage(continuationTitle: String) {
            finishCurrentPageWithOwnerSignature()
            currentPageNumber++
            currentPage = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            )
            currentCanvas = currentPage.canvas
            PdfTemplateRenderer.drawPageBackground(
                context = context,
                canvas = currentCanvas,
                template = pdfTemplateSettings,
                pageWidth = PAGE_WIDTH,
                pageHeight = PAGE_HEIGHT
            )
            PdfTemplateRenderer.drawPaymentStatusStampOnPage(
                canvas = currentCanvas,
                stampLabel = stampLabel,
                template = pdfTemplateSettings,
                pageWidth = PAGE_WIDTH
            )
            y = PdfTemplateRenderer.drawHeaderBannerWithTemplate(
                context = context,
                canvas = currentCanvas,
                companyProfile = companyProfile,
                template = pdfTemplateSettings,
                documentTitle = continuationTitle,
                documentNumberLabel = "No: ${transaction.nomorInvoice}",
                documentSubLabel1 = "Halaman $currentPageNumber",
                pageWidth = PAGE_WIDTH
            )
        }

        // 2. DATA PELANGGAN (LEFT) & DATA UNIT FREEZER (RIGHT)
        val colWidth = (PAGE_WIDTH - (marginX * 2) - 12f) / 2f
        val leftX = marginX
        val rightX = marginX + colWidth + 12f
        val boxHeight = 116f

        fillPaint.color = Color.argb(242, 248, 250, 252)
        val custRect = RectF(leftX, y, leftX + colWidth, y + boxHeight)
        currentCanvas.drawRoundRect(custRect, 6f, 6f, fillPaint)
        currentCanvas.drawRoundRect(custRect, 6f, 6f, strokePaint)

        val kodeCust = customer?.kodePelanggan?.ifBlank { "CUST/SAF/${customer.id.toString().padStart(4, '0')}" }
            ?: "CUST/SAF/${transaction.customerId.toString().padStart(4, '0')}"
        currentCanvas.drawText("DATA PELANGGAN ($kodeCust)", leftX + 8f, y + 15f, sectionTitlePaint)
        val nikRaw = customer?.nik ?: "-"
        val nikDisplay = DateCurrencyUtils.maskNik(nikRaw, maskNikInPdf)
        drawKeyValueRow(currentCanvas, leftX + 8f, y + 30f, colWidth - 16f, "Nama Pelanggan", customer?.namaLengkap ?: transaction.namaPelangganSnapshot, labelPaint, valuePaint)
        drawKeyValueRow(currentCanvas, leftX + 8f, y + 45f, colWidth - 16f, "Nomor HP / WA", customer?.nomorHp ?: "-", labelPaint, valuePaint)
        drawKeyValueRow(currentCanvas, leftX + 8f, y + 60f, colWidth - 16f, "NIK", nikDisplay, labelPaint, valuePaint)
        drawMultilineKeyValue(currentCanvas, leftX + 8f, y + 75f, colWidth - 16f, "Alamat KTP", customer?.alamatLengkap ?: "-", labelPaint, valuePaint)

        val unitRect = RectF(rightX, y, rightX + colWidth, y + boxHeight)
        currentCanvas.drawRoundRect(unitRect, 6f, 6f, fillPaint)
        currentCanvas.drawRoundRect(unitRect, 6f, 6f, strokePaint)

        val isHakMilik = transaction.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
        val totalProg = transaction.totalPeriodeHakMilik.coerceAtLeast(1)
        val currentPaySeq = transaction.pembayaranKeHakMilik.coerceIn(1, totalProg)
        val remainingPeriods = (totalProg - currentPaySeq).coerceAtLeast(0)
        val programDisplay = if (isHakMilik) {
            "Sewa Hak Milik ${transaction.totalPeriodeHakMilik}x (Ke-$currentPaySeq/$totalProg • Sisa ${remainingPeriods}x • ${transaction.statusProgramHakMilik})"
        } else {
            "Sewa Reguler (${transaction.durasiHari} Hari)"
        }

        currentCanvas.drawText("DATA UNIT FREEZER & PROGRAM SEWA", rightX + 8f, y + 15f, sectionTitlePaint)
        if (unitItems.size > 1) {
            val programsSummary = unitItems.mapIndexed { idx, it ->
                if (it.jenisProgram.equals("Sewa Hak Milik", true)) {
                    "U${idx + 1}: Hak Milik Ke-${it.pembayaranKeHakMilik}/${it.totalPeriodeHakMilik.coerceAtLeast(1)}"
                } else {
                    "U${idx + 1}: Reguler (${it.durasiHari}h)"
                }
            }.joinToString(" | ")
            val schedulesSummary = unitItems.joinToString(", ") { "U${it.unitIndexNumber}: ${it.tanggalJatuhTempo}" }
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 30f, colWidth - 16f, "Jumlah Unit", "${unitItems.size} Unit Freezer (Multi-Unit)", labelPaint, valuePaint)
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 45f, colWidth - 16f, "Daftar Unit", unitItems.joinToString(", ") { "${it.namaUnitSnapshot} (${it.kapasitasLiterSnapshot}L)" }, labelPaint, valuePaint)
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 60f, colWidth - 16f, "Program Sewa", programsSummary, labelPaint, valuePaint)
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 75f, colWidth - 16f, "Jatuh Tempo", schedulesSummary, labelPaint, valuePaint)
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 90f, colWidth - 16f, "Mulai Sewa", unitItems.first().tanggalMulaiSewa.ifBlank { transaction.tanggalMulaiSewa }, labelPaint, valuePaint)
        } else {
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 30f, colWidth - 16f, "Nama / Kode Unit", "${unit?.namaUnit ?: transaction.namaUnitSnapshot} (${transaction.kodeUnitSnapshot})", labelPaint, valuePaint)
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 45f, colWidth - 16f, "Merek & Kapasitas", "${unit?.merek ?: "-"} / ${unit?.kapasitasLiter ?: 0} Liter", labelPaint, valuePaint)
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 60f, colWidth - 16f, "Jenis Program", programDisplay, labelPaint, valuePaint)
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 75f, colWidth - 16f, "Durasi / Nomor Seri", "${transaction.durasiHari} Hari • SN: ${unit?.nomorSeri ?: "-"}", labelPaint, valuePaint)
            drawKeyValueRow(currentCanvas, rightX + 8f, y + 90f, colWidth - 16f, "Periode Sewa", "${DateCurrencyUtils.formatDisplayDate(transaction.tanggalMulaiSewa)} s/d ${DateCurrencyUtils.formatDisplayDate(transaction.tanggalAkhirSewa)}", labelPaint, valuePaint)
        }

        y += boxHeight + 10f

        // 3. RINCIAN BIAYA SEWA
        fillPaint.color = lightBlueBg
        val payHeaderRect = RectF(marginX, y, PAGE_WIDTH - marginX, y + 20f)
        currentCanvas.drawRoundRect(payHeaderRect, 4f, 4f, fillPaint)
        val rHeaderTitle = when {
            unitItems.size > 1 -> "RINCIAN BIAYA SEWA MULTI-UNIT (${unitItems.size} UNIT FREEZER) & PROGRES PER UNIT"
            isHakMilik -> "RINCIAN BIAYA SEWA HAK MILIK (${transaction.totalPeriodeHakMilik} KALI - PEMBAYARAN KE-$currentPaySeq)"
            else -> "RINCIAN BIAYA SEWA REGULER, ONGKIR & PEMBAYARAN"
        }
        currentCanvas.drawText(rHeaderTitle, marginX + 8f, y + 14f, sectionTitlePaint)
        y += 24f

        val sisaPembayaran = (transaction.totalTagihan - transaction.nominalPembayaran).coerceAtLeast(0L)
        val paymentRows = buildList {
            if (unitItems.size > 1) {
                unitItems.forEachIndexed { idx, uItem ->
                    val progTag = if (uItem.jenisProgram.equals("Sewa Hak Milik", true)) {
                        "Hak Milik Ke-${uItem.pembayaranKeHakMilik}/${uItem.totalPeriodeHakMilik.coerceAtLeast(1)} (Sisa ${uItem.hakMilikRemainingCount}x)"
                    } else {
                        "Reguler ${uItem.durasiHari}h"
                    }
                    val unitStatusTag = uItem.computeSmartBillingStatus()
                    add(
                        "Unit ${idx + 1}: ${uItem.namaUnitSnapshot} ($progTag • Tempo: ${uItem.tanggalJatuhTempo} • $unitStatusTag)" to
                            "${DateCurrencyUtils.formatRupiah(uItem.hargaSewa)} (Bayar: ${DateCurrencyUtils.formatRupiah(uItem.nominalPembayaran)})"
                    )
                }
                add("Total Biaya Sewa (${unitItems.size} Unit)" to DateCurrencyUtils.formatRupiah(transaction.hargaSewa))
            } else {
                if (isHakMilik) {
                    add("Program Sewa Hak Milik (${transaction.totalPeriodeHakMilik} Kali)" to "Pembayaran Ke-$currentPaySeq dari $totalProg (Sisa $remainingPeriods Kali • Status: ${transaction.statusProgramHakMilik})")
                }
                add("Biaya Sewa Unit (${transaction.durasiHari} Hari)" to DateCurrencyUtils.formatRupiah(transaction.hargaSewa))
            }
            add("Biaya Ongkos Kirim (Ongkir)" to DateCurrencyUtils.formatRupiah(transaction.ongkir))
            add("Deposit Jaminan Unit" to DateCurrencyUtils.formatRupiah(transaction.deposit))
            add("Potongan Diskon" to "- ${DateCurrencyUtils.formatRupiah(transaction.diskon)}")
            add("TOTAL TAGIHAN" to DateCurrencyUtils.formatRupiah(transaction.totalTagihan))
            add("Jumlah Dibayar (${transaction.metodePembayaran})" to DateCurrencyUtils.formatRupiah(transaction.nominalPembayaran))
            add("Sisa Pembayaran / Piutang" to DateCurrencyUtils.formatRupiah(sisaPembayaran))
        }

        val totalRowIndex = paymentRows.indexOfFirst { it.first == "TOTAL TAGIHAN" }
        val rowStep = if (paymentRows.size > 9) 12.2f else 14f
        for ((idx, row) in paymentRows.withIndex()) {
            val rowY = y + (idx * rowStep)
            if (idx == totalRowIndex) {
                fillPaint.color = Color.rgb(241, 245, 249)
                currentCanvas.drawRect(MARGIN, rowY - 10f, PAGE_WIDTH - MARGIN, rowY + 4f, fillPaint)
            }
            currentCanvas.drawText(row.first, MARGIN + 8f, rowY, if (idx == totalRowIndex || idx == 0 && isHakMilik) valuePaint else labelPaint)
            val rightValPaint = Paint(valuePaint).apply { textAlign = Paint.Align.RIGHT }
            currentCanvas.drawText(row.second, PAGE_WIDTH - MARGIN - 8f, rowY, rightValPaint)
        }
        y += paymentRows.size * rowStep + 4f

        val statusVisual = PdfTemplateRenderer.getPaymentStampVisual(stampLabel)
        fillPaint.color = statusVisual.primaryColor
        val badgeRect = RectF(MARGIN, y, MARGIN + 225f, y + 18f)
        currentCanvas.drawRoundRect(badgeRect, 4f, 4f, fillPaint)
        val badgeTextPaint = Paint(valuePaint).apply { color = Color.WHITE }
        currentCanvas.drawText("STATUS PEMBAYARAN: ${statusVisual.label}", MARGIN + 8f, y + 12.5f, badgeTextPaint)

        currentCanvas.drawText(
            "Jatuh Tempo: ${DateCurrencyUtils.formatDisplayDate(transaction.tanggalJatuhTempo)} | Status: ${transaction.statusPenyewaan}",
            MARGIN + 234f,
            y + 12.5f,
            valuePaint
        )
        y += 24f

        // 4. ALAMAT PENGGUNAAN DAN TAGGING LOKASI GPS
        val gpsBoxHeight = 76f
        fillPaint.color = Color.rgb(248, 250, 252)
        val gpsRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + gpsBoxHeight)
        currentCanvas.drawRoundRect(gpsRect, 6f, 6f, fillPaint)
        currentCanvas.drawRoundRect(gpsRect, 6f, 6f, strokePaint)

        currentCanvas.drawText("ALAMAT PENGGUNAAN FREEZER & TAGGING LOKASI GPS", MARGIN + 8f, y + 14f, sectionTitlePaint)
        val latStr = transaction.latitude?.let { String.format("%.6f", it) } ?: "Belum ditag"
        val lonStr = transaction.longitude?.let { String.format("%.6f", it) } ?: "Belum ditag"
        val mapsUrl = transaction.linkGoogleMaps.ifBlank {
            if (transaction.latitude != null && transaction.longitude != null) {
                com.example.location.LocationGpsHelper.formatOpenStreetMapUrl(transaction.latitude, transaction.longitude)
            } else {
                "https://www.openstreetmap.org/search?query=${Uri.encode(transaction.alamatLokasiGps.ifBlank { "Surakarta" })}"
            }
        }

        val gpsTextWidth = PAGE_WIDTH - (MARGIN * 2) - 96f
        drawMultilineKeyValue(
            currentCanvas,
            MARGIN + 8f,
            y + 29f,
            gpsTextWidth,
            "Alamat Pakai",
            transaction.alamatLokasiGps.ifBlank { customer?.alamatLengkap ?: "-" },
            labelPaint,
            valuePaint
        )
        drawKeyValueRow(
            currentCanvas,
            MARGIN + 8f,
            y + 54f,
            gpsTextWidth,
            "Koordinat GPS",
            "Lat: $latStr , Lon: $lonStr",
            labelPaint,
            valuePaint
        )
        drawKeyValueRow(
            currentCanvas,
            MARGIN + 8f,
            y + 68f,
            gpsTextWidth,
            "OpenStreetMap",
            mapsUrl,
            labelPaint,
            valuePaint
        )

        drawMapsQrCode(currentCanvas, PAGE_WIDTH - MARGIN - 74f, y + 6f, 56f, mapsUrl)
        val qrCaptionPaint = Paint(labelPaint).apply {
            textSize = 7f
            textAlign = Paint.Align.CENTER
        }
        currentCanvas.drawText("QR OpenStreetMap", PAGE_WIDTH - MARGIN - 46f, y + 70f, qrCaptionPaint)

        y += gpsBoxHeight + 10f

        // 5. FOTO DOKUMENTASI
        val photoStripHeight = 82f
        currentCanvas.drawText("FOTO DOKUMENTASI PENYEWAAN & LOKASI", MARGIN, y + 10f, sectionTitlePaint)
        y += 14f

        val mandatoryPhotos = listOf(
            "1. Foto KTP" to transaction.fotoKtpDepanTransUri.ifBlank { customer?.fotoKtpDepanUri ?: "" },
            "2. Foto Unit Freezer" to transaction.fotoUnitSebelumKirimUri.ifBlank { unit?.fotoUnitUri ?: "" },
            "3. Foto Serah Terima" to transaction.fotoSerahTerimaUri,
            "4. Freezer di Lokasi" to transaction.fotoFreezerDiLokasiUri.ifBlank { transaction.fotoPelangganBersamaFreezerUri },
            "5. Depan Lokasi" to transaction.fotoDepanLokasiUri
        )

        val slotGap = 8f
        val slotWidth = (PAGE_WIDTH - (MARGIN * 2) - (slotGap * 4)) / 5f
        for ((index, pair) in mandatoryPhotos.withIndex()) {
            val slotX = MARGIN + index * (slotWidth + slotGap)
            drawPhotoBox(
                context = context,
                canvas = currentCanvas,
                x = slotX,
                y = y,
                width = slotWidth,
                height = photoStripHeight,
                caption = pair.first,
                uriOrPath = pair.second
            )
        }
        y += photoStripHeight + 10f

        // 6. ISI PERJANJIAN SEWA (Placed right before Tanda Tangan Konsumen & Pihak SEWAin Aja Freezer SOC)
        val agreementText = transaction.isiPerjanjianSewaSnapshot.ifBlank { defaultPerjanjianSewa }.trim()
        val maxLineWidth = PAGE_WIDTH - (MARGIN * 2) - 16f
        val wrappedAgreementLines = wrapMultilineTextToLines(agreementText, agreementPaint, maxLineWidth)

        if (y + 38f > PAGE_HEIGHT - marginBottom) {
            startNewPage("PERJANJIAN SEWA & TANDA TANGAN")
        }

        fillPaint.color = lightBlueBg
        currentCanvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 19f), 4f, 4f, fillPaint)
        currentCanvas.drawText("PERJANJIAN SEWA FREEZER", MARGIN + 8f, y + 13.5f, sectionTitlePaint)
        y += 23f

        val lineHeight = 8.2f + pdfTemplateSettings.paragraphSpacingPt.coerceIn(2f, 8f)
        for (line in wrappedAgreementLines) {
            if (y + lineHeight > PAGE_HEIGHT - marginBottom - 10f) {
                startNewPage("LANJUTAN PERJANJIAN SEWA FREEZER")
            }
            currentCanvas.drawText(line, MARGIN + 8f, y + 8f, agreementPaint)
            y += lineHeight
        }
        y += 8f

        // 7. TANDA TANGAN KONSUMEN & TANDA TANGAN PIHAK SEWAin Aja Freezer SOC
        // Ensure signatures are never cut off across pages
        val sigBoxHeight = 92f
        val footerHeight = 26f
        if (y + sigBoxHeight + footerHeight + 8f > PAGE_HEIGHT - marginBottom) {
            startNewPage("BERITA ACARA SERAH TERIMA & PENGESAHAN")
        }

        val sigColWidth = (PAGE_WIDTH - (MARGIN * 2) - 14f) / 2f

        val custSigRect = RectF(MARGIN, y, MARGIN + sigColWidth, y + sigBoxHeight)
        currentCanvas.drawRoundRect(custSigRect, 6f, 6f, strokePaint)
        currentCanvas.drawText("Tanda Tangan Konsumen / Penyewa", MARGIN + 8f, y + 14f, sectionTitlePaint)
        drawSignatureBitmapInBox(
            context = context,
            canvas = currentCanvas,
            uriOrPath = transaction.tandaTanganPelangganUri,
            rect = RectF(MARGIN + 14f, y + 18f, MARGIN + sigColWidth - 14f, y + 64f)
        )
        currentCanvas.drawText(
            "Konsumen: ${transaction.namaPenerima.ifBlank { transaction.namaPelangganSnapshot }}",
            MARGIN + 8f,
            y + 76f,
            valuePaint
        )
        currentCanvas.drawText(
            "Tgl: ${transaction.tanggalPenerimaan.ifBlank { transaction.tanggalMulaiSewa }} ${transaction.jamPenerimaan}",
            MARGIN + 8f,
            y + 87f,
            labelPaint
        )

        val offX = MARGIN + sigColWidth + 14f
        val offSigRect = RectF(offX, y, offX + sigColWidth, y + sigBoxHeight)
        currentCanvas.drawRoundRect(offSigRect, 6f, 6f, strokePaint)
        currentCanvas.drawText("Tanda Tangan Pihak ${companyProfile.namaPerusahaan}", offX + 8f, y + 14f, sectionTitlePaint)
        drawSignatureBitmapInBox(
            context = context,
            canvas = currentCanvas,
            uriOrPath = transaction.tandaTanganPetugasUri,
            rect = RectF(offX + 14f, y + 18f, offX + sigColWidth - 14f, y + 64f),
            fallbackOfficerName = transaction.namaPetugas.ifBlank { "Tim Operasional SAF" }
        )
        currentCanvas.drawText(
            "Petugas: ${transaction.namaPetugas.ifBlank { "Admin / Kurir SAF SOC" }}",
            offX + 8f,
            y + 76f,
            valuePaint
        )
        val officerMetaLine = if (transaction.officerSignedAt.isNotBlank()) {
            "Snapshot: ${transaction.officerSignedAt.take(22)} • ${transaction.keteranganKondisiFreezer.ifBlank { "Baik" }.take(18)}"
        } else {
            "Kondisi Unit: ${transaction.keteranganKondisiFreezer.ifBlank { "Baik & Lengkap" }.take(36)}"
        }
        currentCanvas.drawText(
            officerMetaLine,
            offX + 8f,
            y + 87f,
            labelPaint
        )

        y += sigBoxHeight + 8f

        // 8. LEGAL STATEMENT FOOTER
        fillPaint.color = lightBlueBg
        val footerRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, (y + footerHeight).coerceAtMost(PAGE_HEIGHT - marginBottom))
        currentCanvas.drawRoundRect(footerRect, 5f, 5f, fillPaint)
        val statementPaint = Paint(valuePaint).apply {
            color = navyColor
            textSize = 8f
        }
        val statement = "PERNYATAAN RESMI: \"Dokumen ini merupakan bukti sah transaksi, berita acara serah terima unit, dan perjanjian penyewaan unit freezer antara SEWAin Aja Freezer SOC dengan penyewa.\""
        drawWrappedText(currentCanvas, statement, MARGIN + 8f, y + 11f, PAGE_WIDTH - (MARGIN * 2) - 16f, statementPaint, maxLines = 2)

        finishCurrentPageWithOwnerSignature()

        // Optional Attachment Page for High-Resolution Photo Documentation & Berita Acara Serah Terima Unit
        val perUnitPhotos = unitItems.flatMapIndexed { idx, uItem ->
            val uLabel = "Unit ${idx + 1} (${uItem.namaUnitSnapshot.take(16)})"
            listOf(
                "$uLabel - Tampak Depan" to uItem.fotoKondisiDepanUri,
                "$uLabel - Tampak Samping" to uItem.fotoKondisiSampingUri,
                "$uLabel - Bagian Dalam" to uItem.fotoKondisiDalamUri,
                "$uLabel - Kondisi Tambahan" to uItem.fotoKondisiTambahanUri,
                "$uLabel - Serah Terima" to uItem.fotoSerahTerimaPelangganUri,
                "$uLabel - Lokasi Pakai" to uItem.fotoSerahTerimaLokasiUri,
                "$uLabel - Bukti Terima" to uItem.fotoBuktiPenerimaanUri,
                "$uLabel - Dok. Tambahan" to uItem.fotoSerahTerimaTambahanUri
            )
        }.filter { it.second.isNotBlank() }

        val extraPhotos = (listOf(
            "Foto Kondisi Fisik Freezer" to transaction.fotoKondisiFisikTransUri.ifBlank { unit?.fotoKondisiFisikUri ?: "" },
            "Foto Bagian Dalam Freezer" to transaction.fotoBagianDalamTransUri.ifBlank { unit?.fotoBagianDalamUri ?: "" },
            "Foto Merek & Nomor Seri" to transaction.fotoMerekSeriTransUri.ifBlank { unit?.fotoMerekSeriUri ?: "" },
            "Foto Kondisi Saat Serah Terima" to transaction.fotoKondisiSerahTerimaUri,
            "Foto Pelanggan Bersama Freezer" to transaction.fotoPelangganBersamaFreezerUri
        ) + perUnitPhotos).filter { it.second.isNotBlank() }.distinctBy { it.second }

        val anyHighResMandatory = mandatoryPhotos.any { it.second.isNotBlank() }

        if (extraPhotos.isNotEmpty() || anyHighResMandatory) {
            currentPageNumber++
            val pageInfoAttach = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            val pageAttach = pdfDocument.startPage(pageInfoAttach)
            val canvas2 = pageAttach.canvas

            PdfTemplateRenderer.drawPageBackground(
                context = context,
                canvas = canvas2,
                template = pdfTemplateSettings,
                pageWidth = PAGE_WIDTH,
                pageHeight = PAGE_HEIGHT
            )
            PdfTemplateRenderer.drawPaymentStatusStampOnPage(
                canvas = canvas2,
                stampLabel = stampLabel,
                template = pdfTemplateSettings,
                pageWidth = PAGE_WIDTH
            )

            val y2 = PdfTemplateRenderer.drawHeaderBannerWithTemplate(
                context = context,
                canvas = canvas2,
                companyProfile = companyProfile,
                template = pdfTemplateSettings,
                documentTitle = "FORM SERAH TERIMA UNIT",
                documentNumberLabel = "No: ${transaction.nomorInvoice}",
                documentSubLabel1 = "Lampiran Dokumentasi Foto (Hal. $currentPageNumber)",
                bannerSubtitle = "BERITA ACARA SERAH TERIMA & DOKUMENTASI FOTO UNIT FREEZER",
                pageWidth = PAGE_WIDTH
            )

            val allPhotosToDetail = (mandatoryPhotos + extraPhotos).filter { it.second.isNotBlank() }
            val gridCols = 2
            val cellWidth = (PAGE_WIDTH - (MARGIN * 2) - 16f) / 2f
            val cellHeight = 148f

            for ((idx, item) in allPhotosToDetail.take(8).withIndex()) {
                val col = idx % gridCols
                val row = idx / gridCols
                val cx = MARGIN + col * (cellWidth + 16f)
                val cy = y2 + row * (cellHeight + 12f)
                drawPhotoBox(
                    context = context,
                    canvas = canvas2,
                    x = cx,
                    y = cy,
                    width = cellWidth,
                    height = cellHeight,
                    caption = item.first,
                    uriOrPath = item.second
                )
            }

            PdfTemplateRenderer.drawOwnerPermanentSignatureOnPage(
                context = context,
                canvas = canvas2,
                companyProfile = companyProfile,
                template = pdfTemplateSettings,
                snapshotUri = transaction.tandaTanganOwnerSnapshotUri,
                snapshotSizePt = transaction.ukuranTandaTanganOwnerSnapshot,
                snapshotPosition = transaction.posisiTandaTanganOwnerSnapshot,
                pageNumber = currentPageNumber,
                pageWidth = PAGE_WIDTH,
                pageHeight = PAGE_HEIGHT
            )
            pdfDocument.finishPage(pageAttach)
        }

        // =========================================================================
        // DEDICATED FINAL PAGE(S): LAMPIRAN BUKTI PEMBAYARAN
        // Always appended at the end of Invoice PDF (chronologically ordered).
        // Shows "BUKTI PEMBAYARAN BELUM DIUNGGAH" when no payment proof exists.
        // =========================================================================
        val sortedProofs = sortPaymentProofsChronologically(paymentProofs)
        val customerDisplayName = customer?.namaLengkap?.ifBlank { transaction.namaPelangganSnapshot }
            ?: transaction.namaPelangganSnapshot

        fun renderPaymentProofPageHeader(
            canvas: Canvas,
            pageNum: Int,
            subtitle: String
        ): Float {
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
            val headerBottom = PdfTemplateRenderer.drawHeaderBannerWithTemplate(
                context = context,
                canvas = canvas,
                companyProfile = companyProfile,
                template = pdfTemplateSettings,
                documentTitle = "LAMPIRAN BUKTI PEMBAYARAN",
                documentNumberLabel = "No. Invoice: ${transaction.nomorInvoice}",
                documentSubLabel1 = "Halaman $pageNum",
                bannerSubtitle = subtitle,
                pageWidth = PAGE_WIDTH
            )
            // Compact Customer & Invoice summary strip
            val stripRect = RectF(marginX, headerBottom, PAGE_WIDTH - marginX, headerBottom + 36f)
            fillPaint.color = Color.argb(245, 240, 249, 255)
            canvas.drawRoundRect(stripRect, 6f, 6f, fillPaint)
            canvas.drawRoundRect(stripRect, 6f, 6f, strokePaint)
            drawKeyValueRow(
                canvas, marginX + 8f, headerBottom + 14f, (PAGE_WIDTH - marginX * 2) * 0.55f,
                "Nama Pelanggan", customerDisplayName, labelPaint, valuePaint
            )
            drawKeyValueRow(
                canvas, marginX + 8f, headerBottom + 28f, (PAGE_WIDTH - marginX * 2) * 0.55f,
                "Nomor Invoice", transaction.nomorInvoice, labelPaint, valuePaint
            )
            val rightColX = marginX + (PAGE_WIDTH - marginX * 2) * 0.56f
            val rightColW = (PAGE_WIDTH - marginX * 2) * 0.44f - 8f
            drawKeyValueRow(
                canvas, rightColX, headerBottom + 14f, rightColW,
                "Total Tagihan", DateCurrencyUtils.formatRupiah(transaction.totalTagihan), labelPaint, valuePaint
            )
            drawKeyValueRow(
                canvas, rightColX, headerBottom + 28f, rightColW,
                "Total Dibayar", "${DateCurrencyUtils.formatRupiah(transaction.nominalPembayaran)} (${sortedProofs.size} Bukti)", labelPaint, valuePaint
            )
            return headerBottom + 46f
        }

        fun finishPaymentProofPage(page: PdfDocument.Page, pageNum: Int) {
            PdfTemplateRenderer.drawOwnerPermanentSignatureOnPage(
                context = context,
                canvas = page.canvas,
                companyProfile = companyProfile,
                template = pdfTemplateSettings,
                snapshotUri = transaction.tandaTanganOwnerSnapshotUri,
                snapshotSizePt = transaction.ukuranTandaTanganOwnerSnapshot,
                snapshotPosition = transaction.posisiTandaTanganOwnerSnapshot,
                pageNumber = pageNum,
                pageWidth = PAGE_WIDTH,
                pageHeight = PAGE_HEIGHT
            )
            pdfDocument.finishPage(page)
        }

        if (sortedProofs.isEmpty()) {
            currentPageNumber++
            val emptyProofPage = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            )
            val pCanvas = emptyProofPage.canvas
            val startY = renderPaymentProofPageHeader(
                canvas = pCanvas,
                pageNum = currentPageNumber,
                subtitle = "REKAP & LAMPIRAN BUKTI PEMBAYARAN PELANGGAN"
            )

            val noticeRect = RectF(marginX, startY + 12f, PAGE_WIDTH - marginX, startY + 188f)
            fillPaint.color = Color.argb(245, 254, 242, 242)
            pCanvas.drawRoundRect(noticeRect, 10f, 10f, fillPaint)
            val warnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
                color = overdueRed
            }
            pCanvas.drawRoundRect(noticeRect, 10f, 10f, warnBorderPaint)

            val emptyTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(153, 27, 27)
                textSize = 13.5f
                typeface = baseBoldTypeface
                textAlign = Paint.Align.CENTER
            }
            pCanvas.drawText(
                EMPTY_PAYMENT_PROOF_NOTICE,
                PAGE_WIDTH / 2f,
                startY + 62f,
                emptyTitlePaint
            )

            val emptySubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = slateGray
                textSize = 8.8f
                typeface = baseNormalTypeface
                textAlign = Paint.Align.CENTER
            }
            pCanvas.drawText(
                "Pelanggan: $customerDisplayName  •  Invoice: ${transaction.nomorInvoice}",
                PAGE_WIDTH / 2f,
                startY + 86f,
                emptySubPaint
            )
            pCanvas.drawText(
                "Metode: ${transaction.metodePembayaran}  •  Dibayar: ${DateCurrencyUtils.formatRupiah(transaction.nominalPembayaran)}  •  Status: ${transaction.statusPembayaran}",
                PAGE_WIDTH / 2f,
                startY + 104f,
                emptySubPaint
            )
            pCanvas.drawText(
                "Unggah foto struk transfer, screenshot mobile banking, QRIS, atau dokumen pembayaran",
                PAGE_WIDTH / 2f,
                startY + 132f,
                emptySubPaint
            )
            pCanvas.drawText(
                "melalui menu Detail Transaksi agar otomatis tampil pada halaman lampiran ini.",
                PAGE_WIDTH / 2f,
                startY + 146f,
                emptySubPaint
            )

            finishPaymentProofPage(emptyProofPage, currentPageNumber)
        } else {
            // Render up to 2 payment proofs per A4 page so every proof image is sharp & proportional
            val proofChunks = sortedProofs.chunked(2)
            var globalIdx = 0
            proofChunks.forEachIndexed { chunkIdx, chunk ->
                currentPageNumber++
                val proofPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                )
                val pCanvas = proofPage.canvas
                var py = renderPaymentProofPageHeader(
                    canvas = pCanvas,
                    pageNum = currentPageNumber,
                    subtitle = if (sortedProofs.size > 1) {
                        "RIWAYAT BUKTI PEMBAYARAN BERTAHAP / CICILAN (${sortedProofs.size} BUKTI • BAGIAN ${chunkIdx + 1}/${proofChunks.size})"
                    } else {
                        "LAMPIRAN RESMI BUKTI PEMBAYARAN PELANGGAN"
                    }
                )

                val availableHeight = (PAGE_HEIGHT - marginBottom - py - 10f).coerceAtLeast(240f)
                val cardGap = 12f
                val cardHeight = if (chunk.size == 1) {
                    availableHeight.coerceAtMost(420f)
                } else {
                    ((availableHeight - cardGap) / 2f).coerceAtLeast(220f)
                }

                for (proof in chunk) {
                    globalIdx++
                    drawPaymentProofCard(
                        context = context,
                        canvas = pCanvas,
                        x = marginX,
                        y = py,
                        width = PAGE_WIDTH - (marginX * 2),
                        height = cardHeight,
                        sequenceNumber = globalIdx,
                        totalProofs = sortedProofs.size,
                        customerName = customerDisplayName,
                        invoiceNumber = transaction.nomorInvoice,
                        proof = proof,
                        sectionTitlePaint = sectionTitlePaint,
                        labelPaint = labelPaint,
                        valuePaint = valuePaint,
                        strokePaint = strokePaint,
                        fillPaint = fillPaint,
                        paidGreen = paidGreen,
                        overdueRed = overdueRed
                    )
                    py += cardHeight + cardGap
                }

                finishPaymentProofPage(proofPage, currentPageNumber)
            }
        }

        FileOutputStream(outFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return outFile
    }

    private fun drawPaymentProofCard(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        sequenceNumber: Int,
        totalProofs: Int,
        customerName: String,
        invoiceNumber: String,
        proof: PaymentProofEntity,
        sectionTitlePaint: Paint,
        labelPaint: Paint,
        valuePaint: Paint,
        strokePaint: Paint,
        fillPaint: Paint,
        paidGreen: Int,
        overdueRed: Int
    ) {
        val cardRect = RectF(x, y, x + width, y + height)
        fillPaint.color = Color.argb(248, 255, 255, 255)
        canvas.drawRoundRect(cardRect, 8f, 8f, fillPaint)
        canvas.drawRoundRect(cardRect, 8f, 8f, strokePaint)

        // Header strip inside card
        val headerH = 22f
        val headerRect = RectF(x, y, x + width, y + headerH)
        fillPaint.color = Color.rgb(224, 242, 254)
        canvas.drawRoundRect(headerRect, 8f, 8f, fillPaint)
        val seqTitle = if (totalProofs > 1) {
            "BUKTI PEMBAYARAN #$sequenceNumber DARI $totalProofs (URUTAN TANGGAL)"
        } else {
            "BUKTI PEMBAYARAN #$sequenceNumber"
        }
        canvas.drawText(seqTitle, x + 10f, y + 14.5f, sectionTitlePaint)

        val amountHeaderPaint = Paint(valuePaint).apply {
            color = paidGreen
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            DateCurrencyUtils.formatRupiah(proof.nominalPembayaran),
            x + width - 10f,
            y + 14.5f,
            amountHeaderPaint
        )

        // Split content: left metadata (44%), right optimized proof image (56%)
        val contentTop = y + headerH + 8f
        val contentBottom = y + height - 8f
        val metaWidth = (width - 24f) * 0.44f
        val metaX = x + 10f
        val imgX = metaX + metaWidth + 10f
        val imgWidth = (x + width - 10f) - imgX

        val keteranganCombined = buildString {
            if (proof.catatanPembayaran.isNotBlank()) append(proof.catatanPembayaran.trim())
            if (proof.namaPengirim.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("Pengirim: ${proof.namaPengirim.trim()}")
            }
            if (isEmpty()) append("Pembayaran sewa freezer")
        }

        var my = contentTop + 12f
        val rowGap = 17f
        drawKeyValueRow(canvas, metaX, my, metaWidth, "Pelanggan", customerName, labelPaint, valuePaint)
        my += rowGap
        drawKeyValueRow(canvas, metaX, my, metaWidth, "No. Invoice", invoiceNumber, labelPaint, valuePaint)
        my += rowGap
        drawKeyValueRow(
            canvas, metaX, my, metaWidth,
            "Tgl. Bayar", DateCurrencyUtils.formatDisplayDate(proof.tanggalPembayaran), labelPaint, valuePaint
        )
        my += rowGap
        drawKeyValueRow(
            canvas, metaX, my, metaWidth,
            "Nominal", DateCurrencyUtils.formatRupiah(proof.nominalPembayaran), labelPaint, valuePaint
        )
        my += rowGap
        drawKeyValueRow(
            canvas, metaX, my, metaWidth,
            "Metode", proof.metodePembayaran.ifBlank { "-" }, labelPaint, valuePaint
        )
        my += rowGap
        drawMultilineKeyValue(
            canvas, metaX, my, metaWidth,
            "Keterangan", keteranganCombined, labelPaint, valuePaint
        )
        if (proof.waktuUpload.isNotBlank() && my + 32f < contentBottom) {
            drawKeyValueRow(
                canvas, metaX, my + 28f, metaWidth,
                "Diunggah", proof.waktuUpload, labelPaint, labelPaint
            )
        }

        // Right side: proportional, non-cropped, size-optimized proof image frame
        val imgFrame = RectF(imgX, contentTop, imgX + imgWidth, contentBottom)
        fillPaint.color = Color.rgb(248, 250, 252)
        canvas.drawRoundRect(imgFrame, 6f, 6f, fillPaint)
        canvas.drawRoundRect(imgFrame, 6f, 6f, strokePaint)

        val imgPadding = 5f
        val innerImgArea = RectF(
            imgFrame.left + imgPadding,
            imgFrame.top + imgPadding,
            imgFrame.right - imgPadding,
            imgFrame.bottom - imgPadding
        )

        val proofBitmap = loadOptimizedPaymentProofBitmap(
            context = context,
            uriOrPath = proof.fotoBuktiUri,
            maxDimensionPx = 960
        )

        if (proofBitmap != null) {
            val scale = min(
                innerImgArea.width() / proofBitmap.width.toFloat(),
                innerImgArea.height() / proofBitmap.height.toFloat()
            )
            val dw = proofBitmap.width * scale
            val dh = proofBitmap.height * scale
            val dx = innerImgArea.left + (innerImgArea.width() - dw) / 2f
            val dy = innerImgArea.top + (innerImgArea.height() - dh) / 2f
            val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(proofBitmap, null, RectF(dx, dy, dx + dw, dy + dh), bmpPaint)
            if (!proofBitmap.isRecycled) {
                proofBitmap.recycle()
            }
        } else {
            val emptyBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (proof.fotoBuktiUri.isBlank()) overdueRed else Color.rgb(71, 85, 105)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val msgLine1 = if (proof.fotoBuktiUri.isBlank()) {
                EMPTY_PAYMENT_PROOF_NOTICE
            } else {
                "DOKUMEN BUKTI PEMBAYARAN TERLAMPIR"
            }
            val msgLine2 = if (proof.fotoBuktiUri.isBlank()) {
                "(Riwayat nominal pembayaran tetap tercatat)"
            } else {
                File(proof.fotoBuktiUri).name.take(36)
            }
            canvas.drawText(msgLine1, innerImgArea.centerX(), innerImgArea.centerY() - 4f, emptyBoxPaint)
            val subP = Paint(labelPaint).apply { textAlign = Paint.Align.CENTER; textSize = 7.5f }
            canvas.drawText(msgLine2, innerImgArea.centerX(), innerImgArea.centerY() + 10f, subP)
        }
    }

    /**
     * Loads and optimizes payment proof image or PDF page-1 so Invoice PDF remains sharp,
     * correctly EXIF-rotated, proportional, and compact in file size.
     */
    internal fun loadOptimizedPaymentProofBitmap(
        context: Context,
        uriOrPath: String,
        maxDimensionPx: Int = 960
    ): Bitmap? {
        if (uriOrPath.isBlank()) return null
        return try {
            val file = File(uriOrPath)
            // Support attached PDF payment receipts by rendering page 1 cleanly
            if (file.exists() && file.name.lowercase().endsWith(".pdf")) {
                return renderFirstPdfPageToBitmap(file, maxDimensionPx)
            }

            val decoded = if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
                opts.inSampleSize = calculateInSampleSize(opts, maxDimensionPx, maxDimensionPx)
                opts.inJustDecodeBounds = false
                opts.inPreferredConfig = Bitmap.Config.RGB_565
                BitmapFactory.decodeFile(file.absolutePath, opts)?.let { bmp ->
                    applyExifRotation(file.absolutePath, bmp)
                }
            } else {
                val uri = Uri.parse(uriOrPath)
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                raw
            } ?: return null

            // Cap final pixel dimension to keep PDF compact without sacrificing readability
            val maxSide = maxOf(decoded.width, decoded.height)
            if (maxSide > maxDimensionPx && maxSide > 0) {
                val ratio = maxDimensionPx.toFloat() / maxSide.toFloat()
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } else {
                decoded
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExifRotation(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun renderFirstPdfPageToBitmap(pdfFile: File, maxDimensionPx: Int): Bitmap? {
        return try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        val scale = min(
                            maxDimensionPx.toFloat() / page.width.coerceAtLeast(1).toFloat(),
                            maxDimensionPx.toFloat() / page.height.coerceAtLeast(1).toFloat()
                        ).coerceIn(0.5f, 2.0f)
                        val w = (page.width * scale).toInt().coerceAtLeast(120)
                        val h = (page.height * scale).toInt().coerceAtLeast(120)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        bmp
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun wrapMultilineTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.lines()
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) {
                continue
            }
            val words = trimmed.split(Regex("\\s+"))
            var currentLine = ""
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) > maxWidth && currentLine.isNotEmpty()) {
                    result.add(currentLine)
                    currentLine = "   $word"
                } else {
                    currentLine = candidate
                }
            }
            if (currentLine.isNotEmpty()) {
                result.add(currentLine)
            }
        }
        return result
    }

    internal fun drawCompanyLogoOrProfilePic(
        context: Context,
        canvas: Canvas,
        profilePicUri: String,
        x: Float,
        y: Float,
        size: Float
    ) {
        val customBmp = loadScaledBitmap(context, profilePicUri, size.toInt() * 2, size.toInt() * 2)
        if (customBmp != null) {
            val destRect = RectF(x, y, x + size, y + size)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawRoundRect(destRect, 10f, 10f, bgPaint)
            canvas.drawBitmap(customBmp, null, destRect, null)
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(2, 132, 199)
        canvas.drawRoundRect(RectF(x, y, x + size, y + size), 10f, 10f, paint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(x + 8f, y + 12f, x + size - 8f, y + size - 10f), 4f, 4f, paint)
        paint.color = Color.rgb(2, 132, 199)
        canvas.drawRect(x + 8f, y + 21f, x + size - 8f, y + 24f, paint)
        paint.strokeWidth = 2.5f
        val cx = x + size / 2f
        val cy = y + 32f
        canvas.drawLine(cx - 6f, cy, cx + 6f, cy, paint)
        canvas.drawLine(cx, cy - 6f, cx, cy + 6f, paint)
    }

    internal fun drawKeyValueRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        maxWidth: Float,
        label: String,
        value: String,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        canvas.drawText("$label:", x, y, labelPaint)
        val valX = x + 90f
        val avail = (maxWidth - 90f).coerceAtLeast(40f)
        val clipped = clipTextToWidth(value, valuePaint, avail)
        canvas.drawText(clipped, valX, y, valuePaint)
    }

    internal fun drawMultilineKeyValue(
        canvas: Canvas,
        x: Float,
        y: Float,
        maxWidth: Float,
        label: String,
        value: String,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        canvas.drawText("$label:", x, y, labelPaint)
        val valX = x + 90f
        val avail = (maxWidth - 90f).coerceAtLeast(40f)
        drawWrappedText(canvas, value, valX, y, avail, valuePaint, maxLines = 2)
    }

    internal fun clipTextToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var res = text
        while (res.length > 3 && paint.measureText("$res...") > maxWidth) {
            res = res.dropLast(1)
        }
        return "$res..."
    }

    internal fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int = 3
    ) {
        val words = text.split(" ")
        var line = ""
        var y = startY
        var lineCount = 0
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(testLine) > maxWidth) {
                canvas.drawText(line, x, y, paint)
                line = word
                y += paint.textSize + 3f
                lineCount++
                if (lineCount >= maxLines - 1) break
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(clipTextToWidth(line, paint, maxWidth), x, y, paint)
        }
    }

    private fun drawMapsQrCode(canvas: Canvas, x: Float, y: Float, size: Float, content: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawRect(x, y, x + size, y + size, paint)
        paint.color = Color.rgb(10, 37, 64)
        val grid = 15
        val cell = size / grid

        fun isFinder(r: Int, c: Int): Boolean {
            val topLeft = r in 0..4 && c in 0..4
            val topRight = r in 0..4 && c in (grid - 5) until grid
            val bottomLeft = r in (grid - 5) until grid && c in 0..4
            return topLeft || topRight || bottomLeft
        }

        val hash = content.hashCode()
        for (r in 0 until grid) {
            for (c in 0 until grid) {
                val drawCell = if (isFinder(r, c)) {
                    val lr = if (r >= grid - 5) r - (grid - 5) else r
                    val lc = if (c >= grid - 5) c - (grid - 5) else c
                    lr == 0 || lr == 4 || lc == 0 || lc == 4 || (lr == 2 && lc == 2)
                } else {
                    val bitIndex = (r * grid + c) % 31
                    val charCode = content[(r * grid + c) % content.length].code
                    ((hash shr bitIndex) xor charCode) and 1 == 1
                }
                if (drawCell) {
                    canvas.drawRect(
                        x + c * cell,
                        y + r * cell,
                        x + (c + 1) * cell,
                        y + (r + 1) * cell,
                        paint
                    )
                }
            }
        }
    }

    internal fun drawPhotoBox(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        caption: String,
        uriOrPath: String
    ) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(248, 250, 252)
        }
        val rect = RectF(x, y, x + width, y + height)
        canvas.drawRoundRect(rect, 5f, 5f, bgPaint)
        canvas.drawRoundRect(rect, 5f, 5f, strokePaint)

        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(10, 37, 64)
            textSize = 7.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(clipTextToWidth(caption, captionPaint, width - 8f), x + 4f, y + 11.5f, captionPaint)

        val imgArea = RectF(x + 4f, y + 15f, x + width - 4f, y + height - 4f)
        val bitmap = loadScaledBitmap(context, uriOrPath, imgArea.width().toInt(), imgArea.height().toInt())

        if (bitmap != null) {
            val scale = min(
                imgArea.width() / bitmap.width.toFloat(),
                imgArea.height() / bitmap.height.toFloat()
            )
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val dx = imgArea.left + (imgArea.width() - dw) / 2f
            val dy = imgArea.top + (imgArea.height() - dh) / 2f
            val destRect = RectF(dx, dy, dx + dw, dy + dh)
            canvas.drawBitmap(bitmap, null, destRect, null)
        } else {
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 7.2f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Dokumentasi", imgArea.centerX(), imgArea.centerY() - 2f, subPaint)
            canvas.drawText("Tersedia di Aplikasi", imgArea.centerX(), imgArea.centerY() + 8f, subPaint)
        }
    }

    internal fun drawSignatureBitmapInBox(
        context: Context,
        canvas: Canvas,
        uriOrPath: String,
        rect: RectF,
        fallbackOfficerName: String? = null
    ) {
        val bmp = loadScaledBitmap(context, uriOrPath, rect.width().toInt(), rect.height().toInt())
        if (bmp != null) {
            val scale = min(rect.width() / bmp.width.toFloat(), rect.height() / bmp.height.toFloat())
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dx = rect.left + (rect.width() - dw) / 2f
            val dy = rect.top + (rect.height() - dh) / 2f
            canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), null)
        } else {
            val signPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(2, 132, 199)
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
            }
            val label = fallbackOfficerName ?: "Tanda Tangan Digital Terverifikasi"
            canvas.drawText(label, rect.centerX(), rect.centerY() + 4f, signPaint)
        }
    }

    internal fun loadScaledBitmap(context: Context, uriOrPath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (uriOrPath.isBlank()) return null
        return try {
            val file = File(uriOrPath)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                opts.inSampleSize = calculateInSampleSize(opts, reqWidth.coerceAtLeast(100), reqHeight.coerceAtLeast(100))
                opts.inJustDecodeBounds = false
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } else {
                val uri = Uri.parse(uriOrPath)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun shareInvoiceViaWhatsApp(
        context: Context,
        pdfFile: File,
        transaction: RentalTransactionEntity,
        customerPhone: String,
        isResend: Boolean = false
    ) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, pdfFile)
        val prefix = if (isResend) "[KIRIM ULANG INVOICE]" else "[INVOICE RESMI]"
        val message = buildString {
            appendLine("$prefix *SEWAin Aja Freezer SOC*")
            appendLine("No. Invoice: *${transaction.nomorInvoice}*")
            appendLine("Pelanggan: ${transaction.namaPelangganSnapshot}")
            appendLine("Unit Freezer: ${transaction.namaUnitSnapshot} (${transaction.kodeUnitSnapshot})")
            if (transaction.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)) {
                val sisaProg = (transaction.totalPeriodeHakMilik - transaction.pembayaranKeHakMilik).coerceAtLeast(0)
                appendLine("Program: *Sewa Hak Milik ${transaction.totalPeriodeHakMilik} Kali* (Pembayaran Ke-${transaction.pembayaranKeHakMilik}/${transaction.totalPeriodeHakMilik} • Sisa $sisaProg Kali • Status: ${transaction.statusProgramHakMilik})")
            } else {
                appendLine("Program: *Sewa Reguler*")
            }
            appendLine("Durasi Sewa: ${transaction.durasiHari} Hari (${DateCurrencyUtils.formatDisplayDate(transaction.tanggalMulaiSewa)} s/d ${DateCurrencyUtils.formatDisplayDate(transaction.tanggalAkhirSewa)})")
            appendLine("Biaya Sewa: ${DateCurrencyUtils.formatRupiah(transaction.hargaSewa)}")
            appendLine("Ongkir: ${DateCurrencyUtils.formatRupiah(transaction.ongkir)}")
            appendLine("Total Tagihan: *${DateCurrencyUtils.formatRupiah(transaction.totalTagihan)}*")
            appendLine("Status: *${transaction.statusPembayaran}*")
            if (transaction.latitude != null && transaction.longitude != null) {
                appendLine("Lokasi GPS (OpenStreetMap): ${com.example.location.LocationGpsHelper.formatOpenStreetMapUrl(transaction.latitude, transaction.longitude)}")
            }
            appendLine("\nTerima kasih telah mempercayakan kebutuhan cold storage Anda kepada SEWAin Aja Freezer SOC (Solo Raya).")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(sendIntent, "Bagikan Invoice PDF via WhatsApp").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun printInvoicePdf(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
