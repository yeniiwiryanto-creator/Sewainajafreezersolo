package com.example.cloud

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.local.CompanyProfile
import com.example.data.local.CustomerEntity
import com.example.data.local.RentalTransactionEntity
import com.example.util.DateCurrencyUtils
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object GoogleDriveAndReportExporter {

    fun copyLocalFileToSafUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri
    ): Result<Long> {
        return try {
            require(sourceFile.exists() && sourceFile.length() > 0L) {
                "File sumber tidak ditemukan: ${sourceFile.name}"
            }
            var copied = 0L
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    copied = input.copyTo(out)
                }
                out.flush()
            } ?: error("Tidak dapat membuka tujuan penyimpanan Google Drive / perangkat.")
            Result.success(copied)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a genuine OOXML .xlsx workbook stream with customer master data (compatible with Excel & Google Sheets).
     */
    fun exportCustomersToXlsxStream(
        customers: List<CustomerEntity>,
        outputStream: OutputStream
    ): Result<Int> {
        return try {
            val headers = listOf(
                "ID Pelanggan",
                "Nama Lengkap",
                "NIK",
                "No. WhatsApp",
                "Alamat KTP",
                "Lokasi Penggunaan Default",
                "Latitude",
                "Longitude",
                "Status Pelanggan",
                "Tanggal Registrasi"
            )
            val rows = buildList {
                add(headers)
                customers.forEach { c ->
                    add(
                        listOf(
                            c.kodePelanggan.ifBlank { "CUST-${c.id}" },
                            c.namaLengkap,
                            c.nik,
                            c.nomorHp,
                            c.alamatLengkap,
                            c.lokasiPenggunaanDefault.ifBlank { c.namaLokasiDefault },
                            c.latitudeDefault?.toString().orEmpty(),
                            c.longitudeDefault?.toString().orEmpty(),
                            c.statusPelanggan,
                            c.tanggalRegistrasi
                        )
                    )
                }
            }

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
                writeZipTextEntry(
                    zos,
                    "[Content_Types].xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
                )
                writeZipTextEntry(
                    zos,
                    "_rels/.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
                )
                writeZipTextEntry(
                    zos,
                    "xl/workbook.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Data Pelanggan" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
                )
                writeZipTextEntry(
                    zos,
                    "xl/_rels/workbook.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
                )

                val sheetXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                    rows.forEachIndexed { rowIdx, cols ->
                        val rowNum = rowIdx + 1
                        append("""<row r="$rowNum">""")
                        cols.forEachIndexed { colIdx, value ->
                            val colLetter = ('A' + colIdx)
                            val escaped = escapeXml(value)
                            append("""<c r="$colLetter$rowNum" t="inlineStr"><is><t>$escaped</t></is></c>""")
                        }
                        append("</row>")
                    }
                    append("</sheetData></worksheet>")
                }
                writeZipTextEntry(zos, "xl/worksheets/sheet1.xml", sheetXml)
            }
            Result.success(customers.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a clean paginated A4 PDF ledger report for all rental transactions.
     */
    fun exportTransactionsToPdfStream(
        transactions: List<RentalTransactionEntity>,
        companyProfile: CompanyProfile,
        outputStream: OutputStream
    ): Result<Int> {
        val document = PdfDocument()
        return try {
            val pageWidth = 595
            val pageHeight = 842
            val titlePaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val subPaint = Paint().apply {
                color = Color.parseColor("#475569")
                textSize = 9.5f
            }
            val headerBgPaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
            }
            val rowPaint = Paint().apply {
                color = Color.parseColor("#1E293B")
                textSize = 8.5f
            }
            val boldRowPaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            val rowsPerPage = 32
            val chunks = if (transactions.isEmpty()) listOf(emptyList()) else transactions.chunked(rowsPerPage)

            chunks.forEachIndexed { pageIndex, pageItems ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                var y = 42f
                canvas.drawText("${companyProfile.namaPerusahaan} — Laporan Transaksi Sewa", 32f, y, titlePaint)
                y += 16f
                canvas.drawText(
                    "Dicetak: ${DateCurrencyUtils.nowIsoDateTime()}  •  Total Transaksi: ${transactions.size}  •  Hal ${pageIndex + 1}/${chunks.size}",
                    32f,
                    y,
                    subPaint
                )
                y += 18f

                canvas.drawRect(32f, y - 12f, pageWidth - 32f, y + 6f, headerBgPaint)
                canvas.drawText("Invoice", 36f, y, boldRowPaint)
                canvas.drawText("Pelanggan", 136f, y, boldRowPaint)
                canvas.drawText("Unit", 252f, y, boldRowPaint)
                canvas.drawText("Tagihan", 358f, y, boldRowPaint)
                canvas.drawText("Terbayar", 432f, y, boldRowPaint)
                canvas.drawText("Status", 502f, y, boldRowPaint)
                y += 18f

                if (pageItems.isEmpty()) {
                    canvas.drawText("Belum ada data transaksi tercatat.", 36f, y, rowPaint)
                } else {
                    pageItems.forEach { tx ->
                        canvas.drawText(tx.nomorInvoice.take(18), 36f, y, rowPaint)
                        canvas.drawText(tx.namaPelangganSnapshot.take(20), 136f, y, rowPaint)
                        canvas.drawText(tx.kodeUnitSnapshot.ifBlank { tx.namaUnitSnapshot }.take(16), 252f, y, rowPaint)
                        canvas.drawText(DateCurrencyUtils.formatRupiah(tx.totalTagihan), 358f, y, rowPaint)
                        canvas.drawText(DateCurrencyUtils.formatRupiah(tx.nominalPembayaran), 432f, y, rowPaint)
                        canvas.drawText(tx.statusPembayaran.take(14), 502f, y, rowPaint)
                        y += 19f
                    }
                }
                document.finishPage(page)
            }

            document.writeTo(outputStream)
            outputStream.flush()
            Result.success(transactions.size)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { document.close() }
        }
    }

    private fun writeZipTextEntry(zos: ZipOutputStream, entryName: String, content: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch.code >= 0x20) append(ch)
            }
        }
    }
}
