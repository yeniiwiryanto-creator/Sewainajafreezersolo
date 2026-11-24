package com.example.sheets

import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class SmartImportTarget(val label: String, val requiredFields: List<String>) {
    PELANGGAN("Pelanggan", listOf("customer_name", "phone_number")),
    UNIT_FREEZER("Unit Freezer", listOf("unit_code", "unit_name")),
    TRANSAKSI("Transaksi Sewa", listOf("invoice_no", "customer_name", "unit_code")),
    PEMBAYARAN("Riwayat Pembayaran", listOf("invoice_no", "payment_amount"))
}

enum class DuplicateImportPolicy(val label: String, val description: String) {
    SKIP("Lewati Duplikat", "Data yang sudah ada tidak diubah dan dilewati"),
    UPDATE("Perbarui Data", "Perbarui data lama sesuai nilai baru di file import"),
    IMPORT_AS_NEW("Impor Sebagai Baru", "Buat entri baru dengan kode/invoice unik (jika diizinkan)")
}

data class TargetFieldDef(
    val key: String,
    val label: String,
    val required: Boolean,
    val synonyms: List<String>
)

object SmartColumnMappingRegistry {

    fun fieldsForTarget(target: SmartImportTarget): List<TargetFieldDef> = when (target) {
        SmartImportTarget.PELANGGAN -> listOf(
            TargetFieldDef("customer_code", "ID / Kode Pelanggan", false, listOf("kodepelanggan", "kode_pelanggan", "idpelanggan", "customer_id", "cust_code", "id")),
            TargetFieldDef("customer_name", "Nama Pelanggan *", true, listOf("namapelanggan", "nama_pelanggan", "namalengkap", "nama", "customer", "customer_name", "pelanggan")),
            TargetFieldDef("phone_number", "Nomor HP / WhatsApp *", true, listOf("nomorhp", "nomor_hp", "nohp", "whatsapp", "nowa", "no_wa", "telepon", "phone", "phone_number", "hp")),
            TargetFieldDef("nik", "NIK (16 Digit)", false, listOf("nik", "noktp", "no_ktp", "ktp")),
            TargetFieldDef("address", "Alamat Lengkap", false, listOf("alamat", "alamatlengkap", "alamat_ktp", "address", "lokasi", "domisili")),
            TargetFieldDef("usage_location", "Lokasi Penggunaan", false, listOf("lokasipenggunaan", "namalokasipenggunaan", "usage_location", "tempat_usaha", "toko")),
            TargetFieldDef("join_date", "Tanggal Registrasi", false, listOf("tanggalregistrasi", "tanggal_gabung", "tgl_daftar", "join_date", "created_at")),
            TargetFieldDef("status", "Status Pelanggan", false, listOf("statuspelanggan", "status"))
        )
        SmartImportTarget.UNIT_FREEZER -> listOf(
            TargetFieldDef("unit_code", "Kode Unit *", true, listOf("kodeunit", "kode_unit", "unit_code", "kode", "unitid", "kode_freezer")),
            TargetFieldDef("unit_name", "Nama / Tipe Freezer *", true, listOf("namaunit", "nama_unit", "unit_name", "namafreezer", "tipefreezer", "nama")),
            TargetFieldDef("unit_type", "Jenis Freezer", false, listOf("jenisunit", "jenis_unit", "jenis", "kategori", "type", "unit_type")),
            TargetFieldDef("brand", "Merek", false, listOf("merek", "brand", "merk")),
            TargetFieldDef("model", "Model", false, listOf("model", "seri_model")),
            TargetFieldDef("serial_number", "Nomor Seri", false, listOf("nomorseri", "nomor_seri", "noseri", "serial", "serial_number", "sn")),
            TargetFieldDef("capacity_liter", "Kapasitas (Liter)", false, listOf("kapasitas", "kapasitasliter", "kapasitas_liter", "liter", "capacity", "volume")),
            TargetFieldDef("unit_status", "Status Unit", false, listOf("statusunit", "status_unit", "status")),
            TargetFieldDef("condition", "Kondisi Unit", false, listOf("kondisi", "kondisiunit", "kondisi_unit", "condition")),
            TargetFieldDef("location", "Lokasi Unit", false, listOf("lokasi", "lokasiunit", "lokasi_unit", "gudang", "location")),
            TargetFieldDef("purchase_price", "Harga Pembelian", false, listOf("hargapembelian", "harga_beli", "harga", "price")),
            TargetFieldDef("monthly_installment", "Cicilan / Sewa Bulanan", false, listOf("cicilan", "cicilanperbulan", "tarif", "tarifsewa", "nominal_sewa")),
            TargetFieldDef("purchase_date", "Tanggal Pembelian", false, listOf("tanggalpembelian", "tgl_beli", "purchase_date"))
        )
        SmartImportTarget.TRANSAKSI -> listOf(
            TargetFieldDef("invoice_no", "Nomor Invoice *", true, listOf("nomorinvoice", "nomor_invoice", "noinvoice", "invoice", "invoice_no", "notransaksi", "kontrak")),
            TargetFieldDef("customer_name", "Nama Pelanggan *", true, listOf("namapelanggan", "namapelanggansnapshot", "nama_pelanggan", "customer_name", "customer", "pelanggan")),
            TargetFieldDef("customer_phone", "No. WhatsApp Pelanggan", false, listOf("nomorhp", "nowa", "whatsapp", "phone")),
            TargetFieldDef("unit_code", "Kode Unit *", true, listOf("kodeunit", "kodeunitsnapshot", "kode_unit", "unit_code", "unit")),
            TargetFieldDef("program", "Program Sewa", false, listOf("jenisprogram", "program", "programsewa")),
            TargetFieldDef("start_date", "Tanggal Mulai", false, listOf("tanggalmulaisewa", "tanggalmulai", "tgl_mulai", "start_date", "tanggalpemesanan")),
            TargetFieldDef("due_date", "Tanggal Jatuh Tempo", false, listOf("tanggaljatuhtempo", "jatuhtempo", "due_date", "tanggalakhirsewa")),
            TargetFieldDef("duration_days", "Durasi (Hari)", false, listOf("durasihari", "durasi", "duration_days", "lama_sewa")),
            TargetFieldDef("rent_amount", "Nominal Tagihan", false, listOf("totaltagihan", "hargasewa", "tagihan", "nominal_sewa", "rent_amount", "nominal")),
            TargetFieldDef("paid_amount", "Sudah Dibayar", false, listOf("nominalpembayaran", "dibayar", "paid_amount", "terbayar")),
            TargetFieldDef("payment_method", "Metode Pembayaran", false, listOf("metodepembayaran", "metode", "payment_method")),
            TargetFieldDef("payment_status", "Status Pembayaran", false, listOf("statuspembayaran", "payment_status")),
            TargetFieldDef("rental_status", "Status Penyewaan", false, listOf("statuspenyewaan", "rental_status", "status"))
        )
        SmartImportTarget.PEMBAYARAN -> listOf(
            TargetFieldDef("invoice_no", "Nomor Invoice *", true, listOf("nomorinvoice", "noinvoice", "invoice", "invoice_no", "transactionid")),
            TargetFieldDef("payment_amount", "Nominal Pembayaran *", true, listOf("nominalpembayaran", "nominal", "jumlah", "amount", "payment_amount")),
            TargetFieldDef("payment_date", "Tanggal Pembayaran", false, listOf("tanggalpembayaran", "tanggal", "tgl_bayar", "payment_date")),
            TargetFieldDef("payment_method", "Metode Pembayaran", false, listOf("metodepembayaran", "metode", "payment_method")),
            TargetFieldDef("sender_name", "Nama Pengirim", false, listOf("namapengirim", "pengirim", "sender_name")),
            TargetFieldDef("note", "Catatan", false, listOf("catatanpembayaran", "catatan", "keterangan", "note"))
        )
    }

    fun normalizeHeader(raw: String): String =
        raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    /**
     * Automatically maps source columns to target fields by exact normalized & synonym similarity.
     * Returns Map<SourceColumnIndex, TargetFieldKey?>.
     */
    fun recommendMapping(headers: List<String>, target: SmartImportTarget): Map<Int, String?> {
        val defs = fieldsForTarget(target)
        val usedFields = mutableSetOf<String>()
        val mapping = linkedMapOf<Int, String?>()

        headers.forEachIndexed { colIdx, rawHeader ->
            val norm = normalizeHeader(rawHeader)
            val exactMatch = defs.firstOrNull { def ->
                def.key !in usedFields &&
                    (normalizeHeader(def.key) == norm || def.synonyms.any { normalizeHeader(it) == norm })
            }
            val partialMatch = exactMatch ?: defs.firstOrNull { def ->
                def.key !in usedFields &&
                    norm.length >= 3 &&
                    def.synonyms.any { syn ->
                        val s = normalizeHeader(syn)
                        (norm.contains(s) && s.length >= 3) || (s.contains(norm) && norm.length >= 4)
                    }
            }
            if (partialMatch != null) {
                usedFields.add(partialMatch.key)
                mapping[colIdx] = partialMatch.key
            } else {
                mapping[colIdx] = null
            }
        }
        return mapping
    }

    fun findMissingRequiredFields(
        mapping: Map<Int, String?>,
        target: SmartImportTarget
    ): List<TargetFieldDef> {
        val mappedKeys = mapping.values.filterNotNull().toSet()
        return fieldsForTarget(target).filter { it.required && it.key !in mappedKeys }
    }
}

object ExcelTemplateAndReportExporter {

    enum class TemplateType(val fileName: String, val label: String, val sheetTitle: String) {
        PELANGGAN("Template_Pelanggan.xlsx", "Template Pelanggan (.xlsx)", "Data Pelanggan"),
        UNIT("Template_Unit.xlsx", "Template Unit (.xlsx)", "Data Unit Freezer"),
        TRANSAKSI("Template_Transaksi.xlsx", "Template Transaksi (.xlsx)", "Data Transaksi"),
        PEMBAYARAN("Template_Pembayaran.xlsx", "Template Pembayaran (.xlsx)", "Data Pembayaran")
    }

    fun exportTemplateXlsx(type: TemplateType, outputStream: OutputStream): Result<Int> {
        val rows = when (type) {
            TemplateType.PELANGGAN -> listOf(
                listOf("ID Pelanggan", "Nama Pelanggan", "Nomor HP", "NIK", "Alamat Lengkap", "Lokasi Penggunaan", "Tanggal Registrasi", "Status Pelanggan"),
                listOf("CUST/SAF/0101", "Budi Santoso", "081234567890", "3372010101900001", "Jl. Slamet Riyadi No. 120, Laweyan, Surakarta", "Toko Es Segar Solo", "2026-11-01", "Pelanggan Aktif"),
                listOf("CUST/SAF/0102", "Siti Aminah", "082198765432", "3372025505920002", "Jl. Adi Sucipto No. 45, Banjarsari, Surakarta", "Warung Frozen Food", "2026-11-02", "Pelanggan Aktif")
            )
            TemplateType.UNIT -> listOf(
                listOf("Kode Unit", "Nama Unit", "Jenis Freezer", "Merek", "Model", "Nomor Seri", "Kapasitas Liter", "Status Unit", "Kondisi Unit", "Lokasi Unit", "Harga Pembelian", "Cicilan Bulanan", "Tanggal Pembelian"),
                listOf("SAF-CF-101", "Chest Freezer GEA 200L", "Chest Freezer", "GEA", "AB-226-R", "SN-GEA-2026101", "200", "Tersedia", "Baik & Siap Pakai", "Gudang Utama Solo", "3600000", "250000", "2026-10-15"),
                listOf("SAF-SC-102", "Showcase RSA 280L", "Showcase", "RSA", "Agate-280", "SN-RSA-2026102", "280", "Tersedia", "Baik & Siap Pakai", "Gudang Utama Solo", "4200000", "300000", "2026-10-20")
            )
            TemplateType.TRANSAKSI -> listOf(
                listOf("Nomor Invoice", "Nama Pelanggan", "No. WhatsApp", "Kode Unit", "Program Sewa", "Tanggal Mulai", "Tanggal Jatuh Tempo", "Durasi Hari", "Total Tagihan", "Nominal Pembayaran", "Metode Pembayaran", "Status Pembayaran", "Status Penyewaan"),
                listOf("INV/SAF/2026/0501", "Budi Santoso", "081234567890", "SAF-CF-101", "Sewa Reguler", "2026-11-01", "2026-12-01", "30", "250000", "250000", "Transfer BCA", "Lunas", "Dalam Penyewaan")
            )
            TemplateType.PEMBAYARAN -> listOf(
                listOf("Nomor Invoice", "Nominal Pembayaran", "Tanggal Pembayaran", "Metode Pembayaran", "Nama Pengirim", "Catatan"),
                listOf("INV/SAF/2026/0501", "250000", "2026-11-01", "Transfer BCA", "Budi Santoso", "Pembayaran sewa bulan November 2026")
            )
        }
        return writeSimpleXlsx(type.sheetTitle, rows, outputStream).map { rows.size - 1 }
    }

    fun exportErrorReportXlsx(
        originalHeaders: List<String>,
        errorRows: List<ImportRowValidationResult>,
        outputStream: OutputStream
    ): Result<Int> {
        val headers = listOf("BARIS_ASAL", "ALASAN_KESALAHAN") + originalHeaders
        val matrix = buildList {
            add(headers)
            errorRows.forEach { err ->
                add(listOf(err.rowNumber.toString(), err.errorReasons.joinToString("; ")) + err.rawValues)
            }
        }
        return writeSimpleXlsx("Laporan Error Import", matrix, outputStream).map { errorRows.size }
    }

    fun exportErrorReportCsv(
        originalHeaders: List<String>,
        errorRows: List<ImportRowValidationResult>,
        outputStream: OutputStream
    ): Result<Int> {
        return try {
            val headers = listOf("BARIS_ASAL", "ALASAN_KESALAHAN") + originalHeaders
            fun esc(v: String): String {
                val needQuotes = v.contains(',') || v.contains('"') || v.contains('\n')
                val clean = v.replace("\"", "\"\"")
                return if (needQuotes) "\"$clean\"" else clean
            }
            val sb = StringBuilder()
            sb.append('\uFEFF') // UTF-8 BOM for Excel friendliness
            sb.append(headers.joinToString(",") { esc(it) }).append("\r\n")
            errorRows.forEach { err ->
                val cols = listOf(err.rowNumber.toString(), err.errorReasons.joinToString("; ")) + err.rawValues
                sb.append(cols.joinToString(",") { esc(it) }).append("\r\n")
            }
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            Result.success(errorRows.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeSimpleXlsx(
        sheetTitle: String,
        rows: List<List<String>>,
        outputStream: OutputStream
    ): Result<Int> {
        return try {
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
                fun putEntry(name: String, text: String) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(text.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                putEntry(
                    "[Content_Types].xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
                )
                putEntry(
                    "_rels/.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
                )
                putEntry(
                    "xl/workbook.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${escapeXml(sheetTitle.take(31))}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
                )
                putEntry(
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
                            val colLetters = colIndexToLetters(colIdx)
                            append("""<c r="$colLetters$rowNum" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>""")
                        }
                        append("</row>")
                    }
                    append("</sheetData></worksheet>")
                }
                putEntry("xl/worksheets/sheet1.xml", sheetXml)
            }
            Result.success(rows.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun colIndexToLetters(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.toString()
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
