package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.local.CompanyProfile
import com.example.data.local.PdfTemplateSettings
import java.io.File

object PdfTemplateRenderer {

    const val A4_WIDTH = 595
    const val A4_HEIGHT = 842

    private fun rgbColor(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun argbColor(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private val primaryNavy = rgbColor(10, 37, 64)
    private val oceanBlue = rgbColor(2, 132, 199)
    private val accentCyan = rgbColor(14, 165, 233)

    data class PaymentStampVisual(
        val label: String,
        val primaryColor: Int,
        val borderColor: Int,
        val fillColor: Int
    )

    /**
     * Section 4: Resolves the automatic Payment Status Stamp from the transaction database state:
     * - LUNAS -> Green
     * - TERLAMBAT -> Dark Red
     * - JATUH TEMPO -> Orange
     * - DP / UANG MUKA -> Blue
     * - BELUM LUNAS -> Red
     */
    fun resolvePaymentStampLabel(transaction: com.example.data.local.RentalTransactionEntity?): String {
        if (transaction == null) return "LUNAS"
        val statusRaw = transaction.statusPembayaran.trim()
        val daysToDue = com.example.util.DateCurrencyUtils.daysFromToday(transaction.tanggalJatuhTempo)
        val isFullyPaid = statusRaw.equals("Lunas", ignoreCase = true) ||
            (transaction.totalTagihan > 0L && transaction.nominalPembayaran >= transaction.totalTagihan)

        return when {
            isFullyPaid -> "LUNAS"
            statusRaw.equals("Terlambat", ignoreCase = true) ||
                (daysToDue < 0L && transaction.nominalPembayaran < transaction.totalTagihan) -> "TERLAMBAT"
            statusRaw.equals("Jatuh Tempo", ignoreCase = true) ||
                (daysToDue == 0L && transaction.nominalPembayaran < transaction.totalTagihan) -> "JATUH TEMPO"
            statusRaw.contains("DP", ignoreCase = true) ||
                statusRaw.contains("Uang Muka", ignoreCase = true) ||
                statusRaw.contains("Sebagian", ignoreCase = true) ||
                (transaction.nominalPembayaran > 0L && transaction.nominalPembayaran < transaction.totalTagihan) -> "DP / UANG MUKA"
            else -> "BELUM LUNAS"
        }
    }

    fun getPaymentStampVisual(stampLabel: String): PaymentStampVisual {
        return when (stampLabel.uppercase()) {
            "LUNAS" -> PaymentStampVisual(
                label = "LUNAS",
                primaryColor = rgbColor(16, 185, 129),
                borderColor = rgbColor(5, 150, 105),
                fillColor = argbColor(235, 236, 253, 245)
            )
            "DP / UANG MUKA" -> PaymentStampVisual(
                label = "DP / UANG MUKA",
                primaryColor = rgbColor(2, 132, 199),
                borderColor = rgbColor(3, 105, 161),
                fillColor = argbColor(235, 240, 249, 255)
            )
            "JATUH TEMPO" -> PaymentStampVisual(
                label = "JATUH TEMPO",
                primaryColor = rgbColor(234, 88, 12),
                borderColor = rgbColor(194, 65, 12),
                fillColor = argbColor(235, 255, 247, 237)
            )
            "TERLAMBAT" -> PaymentStampVisual(
                label = "TERLAMBAT",
                primaryColor = rgbColor(153, 27, 27),
                borderColor = rgbColor(127, 29, 29),
                fillColor = argbColor(235, 254, 242, 242)
            )
            else -> PaymentStampVisual(
                label = "BELUM LUNAS",
                primaryColor = rgbColor(220, 38, 38),
                borderColor = rgbColor(185, 28, 28),
                fillColor = argbColor(235, 254, 242, 242)
            )
        }
    }

    /**
     * Section 4: Draws the official Payment Status Stamp in the top-right corner of EVERY PDF page.
     */
    fun drawPaymentStatusStampOnPage(
        canvas: Canvas,
        stampLabel: String,
        template: PdfTemplateSettings,
        pageWidth: Int = A4_WIDTH
    ) {
        val visual = getPaymentStampVisual(stampLabel)
        val rightMargin = template.marginRightPt.coerceIn(18f, 56f)
        val stampWidth = 114f
        val stampHeight = 20f
        val rightX = pageWidth - rightMargin
        val leftX = rightX - stampWidth
        val topY = 10f
        val stampRect = RectF(leftX, topY, rightX, topY + stampHeight)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = visual.fillColor
        }
        val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = visual.borderColor
        }
        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
            color = visual.primaryColor
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = visual.primaryColor
            textSize = 8.4f
            textAlign = Paint.Align.CENTER
            typeface = resolveTypeface(template.fontFamily, "BOLD", forceBold = true)
        }

        canvas.drawRoundRect(stampRect, 4f, 4f, fillPaint)
        canvas.drawRoundRect(stampRect, 4f, 4f, outerBorderPaint)
        val insetRect = RectF(leftX + 2f, topY + 2f, rightX - 2f, topY + stampHeight - 2f)
        canvas.drawRoundRect(insetRect, 3f, 3f, innerBorderPaint)
        canvas.drawText("CAP: ${visual.label}", stampRect.centerX(), stampRect.centerY() + 3.0f, textPaint)
    }

    /**
     * Section 5: Draws the Permanent Owner Signature + "Verified by SEWAin Aja Freezer SOC"
     * at the bottom verification area of EVERY page of the PDF without obscuring document content.
     * Uses the historical snapshot if present on old documents, or the latest Owner signature for new documents.
     */
    fun drawOwnerPermanentSignatureOnPage(
        context: Context,
        canvas: Canvas,
        companyProfile: CompanyProfile,
        template: PdfTemplateSettings,
        snapshotUri: String = "",
        snapshotSizePt: Float = 0f,
        snapshotPosition: String = "",
        pageNumber: Int = 1,
        pageWidth: Int = A4_WIDTH,
        pageHeight: Int = A4_HEIGHT
    ) {
        val sigUri = snapshotUri.ifBlank { companyProfile.ownerSignatureUri }
        val sigSize = (if (snapshotSizePt > 0f) snapshotSizePt else companyProfile.ownerSignatureSizePt).coerceIn(24f, 56f)
        val sigPos = snapshotPosition.ifBlank { companyProfile.ownerSignaturePosition }.uppercase()

        val marginLeft = template.marginLeftPt.coerceIn(18f, 56f)
        val marginRight = template.marginRightPt.coerceIn(18f, 56f)
        val footerStripHeight = template.footerHeightPt.coerceIn(26f, 44f)
        val stripBottom = pageHeight - 7f
        val stripTop = stripBottom - footerStripHeight

        val boxWidth = 178f
        val boxLeft = when (sigPos) {
            "LEFT" -> marginLeft
            "CENTER" -> (pageWidth - boxWidth) / 2f
            else -> pageWidth - marginRight - boxWidth
        }
        val boxRect = RectF(boxLeft, stripTop, boxLeft + boxWidth, stripBottom)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(238, 248, 250, 252)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = Color.rgb(186, 230, 253)
        }
        canvas.drawRoundRect(boxRect, 4f, 4f, bgPaint)
        canvas.drawRoundRect(boxRect, 4f, 4f, strokePaint)

        // Draw signature image or stylized permanent Owner signature on the left of the badge
        val sigImgWidth = (sigSize * 1.35f).coerceIn(36f, 68f)
        val sigImgRect = RectF(
            boxRect.left + 4f,
            boxRect.top + 2f,
            boxRect.left + 4f + sigImgWidth,
            boxRect.bottom - 2f
        )
        val sigBmp = loadImageBitmap(context, sigUri, 200, 120)
        if (sigBmp != null) {
            canvas.drawBitmap(sigBmp, null, sigImgRect, null)
        } else {
            val scriptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = oceanBlue
                textSize = 7.8f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("TTD Owner", sigImgRect.centerX(), sigImgRect.centerY() + 2.5f, scriptPaint)
        }

        val verifiedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryNavy
            textSize = 7.0f
            typeface = resolveTypeface(template.fontFamily, "BOLD", forceBold = true)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 6.3f
            typeface = resolveTypeface(template.fontFamily, template.fontWeight, forceBold = false)
        }
        val textX = sigImgRect.right + 5f
        canvas.drawText(
            "Verified by SEWAin Aja Freezer SOC",
            textX,
            boxRect.top + (footerStripHeight * 0.45f),
            verifiedPaint
        )
        val ownerLabel = "${companyProfile.ownerName.take(22)} • Hal. $pageNumber"
        canvas.drawText(
            ownerLabel,
            textX,
            boxRect.top + (footerStripHeight * 0.80f),
            subPaint
        )
    }

    fun resolveTypeface(fontFamily: String, fontWeight: String, forceBold: Boolean = false): Typeface {
        val base = when (fontFamily.uppercase()) {
            "SERIF" -> Typeface.SERIF
            "MONOSPACE" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        val isBold = forceBold || fontWeight.equals("BOLD", ignoreCase = true) || fontWeight.equals("MEDIUM", ignoreCase = true)
        return Typeface.create(base, if (isBold) Typeface.BOLD else Typeface.NORMAL)
    }

    /**
     * Renders the configured background (default geometric letterhead OR user-uploaded JPG/PNG/PDF)
     * with adjustable transparency, plus a white readability veil so text, tables,
     * KTP photos, unit photos, and signatures are never obscured by the background.
     */
    fun drawPageBackground(
        context: Context,
        canvas: Canvas,
        template: PdfTemplateSettings,
        pageWidth: Int = A4_WIDTH,
        pageHeight: Int = A4_HEIGHT
    ) {
        val whiteFill = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), whiteFill)

        var customDrawn = false
        if (template.backgroundType != "DEFAULT" && template.backgroundUri.isNotBlank()) {
            val bgBitmap = loadBackgroundBitmap(
                context = context,
                uriOrPath = template.backgroundUri,
                isPdf = template.backgroundType.equals("PDF", ignoreCase = true),
                targetW = pageWidth * 2,
                targetH = pageHeight * 2
            )
            if (bgBitmap != null) {
                val alphaInt = (template.backgroundOpacity.coerceIn(0.05f, 1.0f) * 255).toInt()
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = alphaInt
                }
                canvas.drawBitmap(
                    bgBitmap,
                    null,
                    RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat()),
                    bgPaint
                )

                // Draw a subtle semi-opaque white readability layer inside the content area
                // whenever opacity is above 0.28f so text, tables, photos, and signatures stay crisp.
                if (template.backgroundOpacity > 0.28f) {
                    val veilAlpha = ((template.backgroundOpacity - 0.20f).coerceIn(0f, 0.72f) * 255).toInt()
                    val veilPaint = Paint().apply {
                        color = Color.argb(veilAlpha, 255, 255, 255)
                        style = Paint.Style.FILL
                    }
                    val left = (template.marginLeftPt - 6f).coerceAtLeast(12f)
                    val top = (template.marginTopPt - 6f).coerceAtLeast(12f)
                    val right = (pageWidth - template.marginRightPt + 6f).coerceAtMost(pageWidth - 12f)
                    val bottom = (pageHeight - template.marginBottomPt + 6f).coerceAtMost(pageHeight - 12f)
                    canvas.drawRoundRect(RectF(left, top, right, bottom), 6f, 6f, veilPaint)
                }
                customDrawn = true
            }
        }

        if (!customDrawn) {
            // Default corporate A4 template decorative accents (Sea Blue & Dark Navy)
            val topBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryNavy
                style = Paint.Style.FILL
            }
            val accentStripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentCyan
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(218, 228, 240)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 5f, topBarPaint)
            canvas.drawRect(0f, 5f, pageWidth.toFloat(), 8f, accentStripePaint)
            canvas.drawRect(0f, (pageHeight - 5).toFloat(), pageWidth.toFloat(), pageHeight.toFloat(), topBarPaint)
            canvas.drawRoundRect(
                RectF(12f, 12f, (pageWidth - 12).toFloat(), (pageHeight - 12).toFloat()),
                6f,
                6f,
                borderPaint
            )
        }
    }

    /**
     * Draws the company header banner and positions the logo according to `template.logoPosition`
     * ("LEFT", "CENTER", or "RIGHT"), returning the Y coordinate immediately below the header.
     */
    fun drawHeaderBannerWithTemplate(
        context: Context,
        canvas: Canvas,
        companyProfile: CompanyProfile,
        template: PdfTemplateSettings,
        documentTitle: String,
        documentNumberLabel: String,
        documentSubLabel1: String,
        documentSubLabel2: String = "",
        bannerSubtitle: String = "",
        pageWidth: Int = A4_WIDTH
    ): Float {
        val marginLeft = template.marginLeftPt.coerceIn(20f, 56f)
        val marginRight = template.marginRightPt.coerceIn(20f, 56f)
        val topY = (template.marginTopPt + template.contentOffsetYPt).coerceIn(28f, 96f)
        val availableWidth = (pageWidth - marginLeft - marginRight) * (template.headerWidthPct.coerceIn(85f, 100f) / 100f)
        val leftEdge = marginLeft + ((pageWidth - marginLeft - marginRight - availableWidth) / 2f)
        val rightEdge = leftEdge + availableWidth

        val logoUriToUse = template.customLogoUri.ifBlank { companyProfile.profilePictureUri }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryNavy
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = template.companyNameFontSizePt.coerceIn(10f, 18f)
            typeface = resolveTypeface(template.fontFamily, "BOLD", forceBold = true)
        }
        val addrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(186, 230, 253)
            textSize = template.companyAddressFontSizePt.coerceIn(6.5f, 12f)
            typeface = resolveTypeface(template.fontFamily, template.fontWeight, forceBold = false)
        }
        val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(186, 230, 253)
            textSize = template.companyPhoneFontSizePt.coerceIn(6.5f, 12f)
            typeface = resolveTypeface(template.fontFamily, template.fontWeight, forceBold = false)
        }

        val configuredLogoSize = template.logoSizePt.coerceIn(28f, 64f)
        val minBannerH = template.headerHeightPt.coerceIn(64f, 130f)
        val bannerHeight = if (template.logoPosition.equals("CENTER", ignoreCase = true)) {
            (configuredLogoSize + 56f).coerceAtLeast(minBannerH).coerceAtLeast(94f)
        } else {
            (configuredLogoSize + 28f).coerceAtLeast(minBannerH)
        }
        val headerRect = RectF(leftEdge, topY, rightEdge, topY + bannerHeight)
        canvas.drawRoundRect(headerRect, 8f, 8f, fillPaint)

        val elemGap = template.headerElementSpacingPt.coerceIn(10f, 18f)

        val identitySubtitle = buildString {
            if (companyProfile.identitasUsaha.isNotBlank()) {
                append(companyProfile.identitasUsaha)
            } else {
                append("IG: ${companyProfile.instagram} | FB: ${companyProfile.facebook}")
            }
            if (companyProfile.informasiTambahan.isNotBlank()) {
                append(" • ").append(companyProfile.informasiTambahan)
            }
        }

        when (template.logoPosition.uppercase()) {
            "CENTER" -> {
                val logoSize = configuredLogoSize.coerceAtMost(54f)
                val centerX = (leftEdge + rightEdge) / 2f
                InvoicePdfGenerator.drawCompanyLogoOrProfilePic(
                    context = context,
                    canvas = canvas,
                    profilePicUri = logoUriToUse,
                    x = centerX - (logoSize / 2f),
                    y = topY + 6f,
                    size = logoSize
                )
                val centerTitlePaint = Paint(titlePaint).apply {
                    textAlign = Paint.Align.CENTER
                }
                val centerSubPaint = Paint(addrPaint).apply {
                    textAlign = Paint.Align.CENTER
                }
                val textBaseY = topY + logoSize + 18f
                canvas.drawText(companyProfile.namaPerusahaan, centerX, textBaseY, centerTitlePaint)
                canvas.drawText(
                    "${companyProfile.alamat} | WA: ${companyProfile.nomorHp} | ${companyProfile.email}",
                    centerX,
                    textBaseY + elemGap - 1f,
                    centerSubPaint
                )
                canvas.drawText(
                    InvoicePdfGenerator.clipTextToWidth(identitySubtitle, centerSubPaint, (rightEdge - leftEdge - 24f)),
                    centerX,
                    textBaseY + (elemGap * 1.85f),
                    centerSubPaint
                )
                canvas.drawText(
                    "$documentTitle • $documentNumberLabel • $documentSubLabel1",
                    centerX,
                    textBaseY + (elemGap * 2.75f),
                    Paint(centerTitlePaint).apply {
                        textSize = template.subtitleFontSizePt.coerceIn(8f, 12f)
                        color = Color.rgb(125, 211, 252)
                    }
                )
            }

            "RIGHT" -> {
                val leftTitlePaint = Paint(titlePaint).apply {
                    textAlign = Paint.Align.LEFT
                    textSize = template.titleFontSizePt.coerceIn(10f, 16f)
                }
                val leftSubPaint = Paint(addrPaint).apply {
                    textAlign = Paint.Align.LEFT
                }
                val leftX = leftEdge + 12f
                canvas.drawText(documentTitle, leftX, topY + 22f, leftTitlePaint)
                canvas.drawText(documentNumberLabel, leftX, topY + 22f + elemGap, leftSubPaint)
                canvas.drawText(documentSubLabel1, leftX, topY + 22f + (elemGap * 2f), leftSubPaint)
                if (documentSubLabel2.isNotBlank()) {
                    canvas.drawText(documentSubLabel2, leftX, topY + 22f + (elemGap * 3f), leftSubPaint)
                }

                val logoSize = configuredLogoSize
                InvoicePdfGenerator.drawCompanyLogoOrProfilePic(
                    context = context,
                    canvas = canvas,
                    profilePicUri = logoUriToUse,
                    x = rightEdge - logoSize - 10f,
                    y = topY + ((bannerHeight - logoSize) / 2f),
                    size = logoSize
                )

                val rightTextX = rightEdge - logoSize - 18f
                val rTitlePaint = Paint(titlePaint).apply { textAlign = Paint.Align.RIGHT }
                val rAddrPaint = Paint(addrPaint).apply { textAlign = Paint.Align.RIGHT }
                val rPhonePaint = Paint(phonePaint).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText(companyProfile.namaPerusahaan, rightTextX, topY + 21f, rTitlePaint)
                canvas.drawText(
                    InvoicePdfGenerator.clipTextToWidth(companyProfile.alamat, rAddrPaint, 240f),
                    rightTextX,
                    topY + 21f + elemGap,
                    rAddrPaint
                )
                canvas.drawText(
                    "HP/WA: ${companyProfile.nomorHp} | Email: ${companyProfile.email}",
                    rightTextX,
                    topY + 21f + (elemGap * 2f),
                    rPhonePaint
                )
                canvas.drawText(
                    InvoicePdfGenerator.clipTextToWidth(
                        if (bannerSubtitle.isNotBlank()) bannerSubtitle else identitySubtitle,
                        rAddrPaint,
                        250f
                    ),
                    rightTextX,
                    topY + 21f + (elemGap * 3f),
                    rAddrPaint
                )
            }

            else -> {
                val logoSize = configuredLogoSize
                InvoicePdfGenerator.drawCompanyLogoOrProfilePic(
                    context = context,
                    canvas = canvas,
                    profilePicUri = logoUriToUse,
                    x = leftEdge + 10f,
                    y = topY + ((bannerHeight - logoSize) / 2f),
                    size = logoSize
                )

                val textStartX = leftEdge + logoSize + 20f
                canvas.drawText(companyProfile.namaPerusahaan, textStartX, topY + 21f, titlePaint)
                canvas.drawText(
                    InvoicePdfGenerator.clipTextToWidth(companyProfile.alamat, addrPaint, 260f),
                    textStartX,
                    topY + 21f + elemGap,
                    addrPaint
                )
                canvas.drawText(
                    "HP/WA: ${companyProfile.nomorHp} | Email: ${companyProfile.email}",
                    textStartX,
                    topY + 21f + (elemGap * 2f),
                    phonePaint
                )
                if (bannerSubtitle.isNotBlank()) {
                    canvas.drawText(
                        bannerSubtitle,
                        textStartX,
                        topY + 21f + (elemGap * 3f),
                        Paint(titlePaint).apply {
                            textSize = template.subtitleFontSizePt.coerceIn(8f, 12f)
                            color = Color.rgb(125, 211, 252)
                        }
                    )
                } else {
                    canvas.drawText(
                        InvoicePdfGenerator.clipTextToWidth(identitySubtitle, addrPaint, 260f),
                        textStartX,
                        topY + 21f + (elemGap * 3f),
                        addrPaint
                    )
                }

                val rightTextX = rightEdge - 12f
                val rightTitlePaint = Paint(titlePaint).apply {
                    textAlign = Paint.Align.RIGHT
                    textSize = template.titleFontSizePt.coerceIn(10f, 15f)
                }
                val rightSubPaint = Paint(addrPaint).apply {
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(documentTitle, rightTextX, topY + 23f, rightTitlePaint)
                canvas.drawText(documentNumberLabel, rightTextX, topY + 23f + elemGap, rightSubPaint)
                canvas.drawText(documentSubLabel1, rightTextX, topY + 23f + (elemGap * 2f), rightSubPaint)
                if (documentSubLabel2.isNotBlank()) {
                    canvas.drawText(documentSubLabel2, rightTextX, topY + 23f + (elemGap * 3f), rightSubPaint)
                }
            }
        }

        return topY + bannerHeight + 8f
    }

    /**
     * Generates a live A4 preview Bitmap for `Setting -> Customize Template PDF`
     * so the admin can inspect the background, transparency, margins, logo alignment,
     * Payment Status Stamp, Permanent Owner Signature, sample table, photos, and signatures before saving.
     */
    fun renderTemplatePreviewBitmap(
        context: Context,
        companyProfile: CompanyProfile,
        template: PdfTemplateSettings
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPageBackground(context, canvas, template, A4_WIDTH, A4_HEIGHT)
        drawPaymentStatusStampOnPage(canvas, "LUNAS", template, A4_WIDTH)

        var y = drawHeaderBannerWithTemplate(
            context = context,
            canvas = canvas,
            companyProfile = companyProfile,
            template = template,
            documentTitle = "PREVIEW TEMPLATE PDF",
            documentNumberLabel = "No: INV/SAF/20261110/0001",
            documentSubLabel1 = "Ukuran Kertas: ${template.paperSize} (595x842 pt)",
            documentSubLabel2 = "Font: ${template.fontFamily} (${template.fontWeight})",
            bannerSubtitle = "INVOICE • PERJANJIAN SEWA • SERAH TERIMA • PENGEMBALIAN",
            pageWidth = A4_WIDTH
        )

        val marginLeft = template.marginLeftPt.coerceIn(20f, 56f)
        val marginRight = template.marginRightPt.coerceIn(20f, 56f)
        val rightEdge = A4_WIDTH - marginRight
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(235, 248, 250, 252)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(203, 213, 225)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryNavy
            textSize = template.subtitleFontSizePt.coerceIn(8.5f, 13f)
            typeface = resolveTypeface(template.fontFamily, "BOLD", forceBold = true)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = template.bodyFontSizePt.coerceIn(7.5f, 11.5f)
            typeface = resolveTypeface(template.fontFamily, template.fontWeight, forceBold = false)
        }

        // Sample Data Pelanggan & Unit Box
        val boxRect = RectF(marginLeft, y, rightEdge, y + 86f)
        canvas.drawRoundRect(boxRect, 6f, 6f, cardFill)
        canvas.drawRoundRect(boxRect, 6f, 6f, strokePaint)
        canvas.drawText("1. CONTOH DATA PELANGGAN & UNIT FREEZER (KETERBACAAN TEKS 100%)", marginLeft + 10f, y + 16f, titlePaint)
        canvas.drawText("ID Pelanggan: CUST/SAF/0001   |   Nama: Budi Santoso (Kuliner Solo)", marginLeft + 10f, y + 34f, bodyPaint)
        canvas.drawText("Unit Freezer: RSA CF-310 Chest Freezer 300L (FRZ-SOC-001)", marginLeft + 10f, y + 50f, bodyPaint)
        canvas.drawText("Lokasi Pakai: Jl. Slamet Riyadi No. 88, Surakarta (-7.5694, 110.8167)", marginLeft + 10f, y + 66f, bodyPaint)
        y += 96f

        // Sample Rincian Biaya & Perjanjian Sewa Box
        val tableRect = RectF(marginLeft, y, rightEdge, y + 110f)
        canvas.drawRoundRect(tableRect, 6f, 6f, cardFill)
        canvas.drawRoundRect(tableRect, 6f, 6f, strokePaint)
        canvas.drawText("2. CONTOH TABEL BIAYA SEWA & SURAT PERJANJIAN SEWA", marginLeft + 10f, y + 16f, titlePaint)
        canvas.drawText("Biaya Sewa (30 Hari): Rp 450.000   |   Ongkir: Rp 50.000   |   Total: Rp 500.000 (LUNAS)", marginLeft + 10f, y + 34f, bodyPaint)
        canvas.drawText("Pasal 1: Penyewa bertanggung jawab atas unit freezer selama masa penyewaan berlangsung.", marginLeft + 10f, y + 54f, bodyPaint)
        canvas.drawText("Pasal 2: Penyewa wajib menjaga kebersihan dan kondisi fisik unit freezer.", marginLeft + 10f, y + 70f, bodyPaint)
        canvas.drawText("Pasal 3: Cap Status Pembayaran & TTD Owner otomatis tampil di setiap halaman.", marginLeft + 10f, y + 86f, bodyPaint)
        y += 120f

        // Sample Photo & Signature Box
        val sigRect = RectF(marginLeft, y, rightEdge, (y + 90f).coerceAtMost(A4_HEIGHT - template.marginBottomPt - template.footerHeightPt))
        canvas.drawRoundRect(sigRect, 6f, 6f, cardFill)
        canvas.drawRoundRect(sigRect, 6f, 6f, strokePaint)
        canvas.drawText("3. AREA FOTO KTP, FOTO UNIT & TANDA TANGAN DIGITAL", marginLeft + 10f, y + 16f, titlePaint)
        canvas.drawText("Margin Atas: ${template.marginTopPt.toInt()}pt | Bawah: ${template.marginBottomPt.toInt()}pt | Kiri: ${template.marginLeftPt.toInt()}pt | Kanan: ${template.marginRightPt.toInt()}pt", marginLeft + 10f, y + 36f, bodyPaint)
        canvas.drawText("[Foto KTP & Unit Tetap Tajam]                 [Tanda Tangan Penyewa]                 [Tanda Tangan Petugas SAF]", marginLeft + 10f, y + 64f, bodyPaint)

        drawOwnerPermanentSignatureOnPage(
            context = context,
            canvas = canvas,
            companyProfile = companyProfile,
            template = template,
            pageNumber = 1,
            pageWidth = A4_WIDTH,
            pageHeight = A4_HEIGHT
        )

        return bitmap
    }

    fun loadBackgroundBitmap(
        context: Context,
        uriOrPath: String,
        isPdf: Boolean,
        targetW: Int,
        targetH: Int
    ): Bitmap? {
        if (uriOrPath.isBlank()) return null
        val looksLikePdf = isPdf || uriOrPath.lowercase().endsWith(".pdf")
        if (looksLikePdf) {
            return renderFirstPdfPageToBitmap(context, uriOrPath, targetW, targetH)
        }
        return loadImageBitmap(context, uriOrPath, targetW, targetH)
    }

    private fun renderFirstPdfPageToBitmap(
        context: Context,
        uriOrPath: String,
        targetW: Int,
        targetH: Int
    ): Bitmap? {
        return runCatching {
            val pfd: ParcelFileDescriptor? = when {
                uriOrPath.startsWith("content://") ->
                    context.contentResolver.openFileDescriptor(Uri.parse(uriOrPath), "r")
                uriOrPath.startsWith("file://") -> {
                    val f = File(Uri.parse(uriOrPath).path ?: return null)
                    if (f.exists()) ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) else null
                }
                else -> {
                    val f = File(uriOrPath)
                    if (f.exists()) ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) else null
                }
            }
            pfd?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(
                                targetW.coerceAtLeast(A4_WIDTH),
                                targetH.coerceAtLeast(A4_HEIGHT),
                                Bitmap.Config.ARGB_8888
                            )
                            val whiteCanvas = Canvas(bitmap)
                            whiteCanvas.drawColor(Color.WHITE)
                            page.render(
                                bitmap,
                                Rect(0, 0, bitmap.width, bitmap.height),
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                            )
                            bitmap
                        }
                    } else null
                }
            }
        }.getOrNull()
    }

    private fun loadImageBitmap(
        context: Context,
        uriOrPath: String,
        maxW: Int,
        maxH: Int
    ): Bitmap? {
        return runCatching {
            val bytes = when {
                uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://") -> {
                    context.contentResolver.openInputStream(Uri.parse(uriOrPath))?.use { it.readBytes() }
                }
                else -> {
                    val f = File(uriOrPath)
                    if (f.exists()) f.readBytes() else null
                }
            } ?: return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxW * 2 || bounds.outHeight / sampleSize > maxH * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        }.getOrNull()
    }
}
