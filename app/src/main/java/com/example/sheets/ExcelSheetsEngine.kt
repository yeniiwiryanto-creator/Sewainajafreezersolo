package com.example.sheets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.repository.FreezerRepository
import com.example.util.DateCurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SheetImportPreview(
    val fileName: String,
    val availableSheets: List<String>,
    val selectedSheet: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val detectedDuplicates: Int,
    val invalidDatesCount: Int,
    val invalidNominalsCount: Int,
    val invalidPhonesCount: Int,
    val mismatchedColumnsWarning: List<String>,
    val rowErrors: List<String>
)

data class ImportExecutionReport(
    val sheetName: String,
    val successCount: Int,
    val failedCount: Int,
    val duplicateSkippedOrMerged: Int,
    val errors: List<String>
)

object ExcelSheetsEngine {

    private val httpClient = OkHttpClient()

    val SUPPORTED_TEMPLATE_SHEETS = listOf(
        "INVESTASI SEWA FREEZER",
        "INVESTASI FREEZER POINT FROZEN",
        "DATA PELANGGAN"
    )

    /**
     * Parses an XLSX file from Uri or File using standard Android ZipInputStream + XmlPullParser.
     * Returns a map of SheetName -> List of Rows (each Row is List of String cell values).
     */
    suspend fun readXlsxWorkbook(context: Context, uri: Uri): Map<String, List<List<String>>> =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseXlsxStream(inputStream)
            } ?: emptyMap()
        }

    fun parseXlsxStream(inputStream: InputStream): Map<String, List<List<String>>> {
        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    zipEntries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // 1. Parse xl/sharedStrings.xml
        val sharedStrings = mutableListOf<String>()
        zipEntries["xl/sharedStrings.xml"]?.let { bytes ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(String(bytes, Charsets.UTF_8)))
            var event = parser.eventType
            var currentText = StringBuilder()
            var insideSi = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "si") {
                            insideSi = true
                            currentText = StringBuilder()
                        } else if (insideSi && parser.name == "t") {
                            currentText.append(parser.nextText())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "si" && insideSi) {
                            sharedStrings.add(currentText.toString())
                            insideSi = false
                        }
                    }
                }
                event = parser.next()
            }
        }

        // 2. Parse xl/workbook.xml to get ordered sheet names
        val sheetNames = mutableListOf<Pair<String, String>>() // sheetName to sheetIndex
        zipEntries["xl/workbook.xml"]?.let { bytes ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(String(bytes, Charsets.UTF_8)))
            var event = parser.eventType
            var idx = 1
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet$idx"
                    sheetNames.add(name to "xl/worksheets/sheet$idx.xml")
                    idx++
                }
                event = parser.next()
            }
        }

        if (sheetNames.isEmpty()) {
            // Fallback scan for any worksheet files
            zipEntries.keys.filter { it.startsWith("xl/worksheets/sheet") }.sorted().forEachIndexed { idx, path ->
                val defaultName = SUPPORTED_TEMPLATE_SHEETS.getOrElse(idx) { "Sheet${idx + 1}" }
                sheetNames.add(defaultName to path)
            }
        }

        val result = linkedMapOf<String, List<List<String>>>()
        for ((sheetName, sheetPath) in sheetNames) {
            val sheetBytes = zipEntries[sheetPath] ?: continue
            val rows = parseWorksheetXml(String(sheetBytes, Charsets.UTF_8), sharedStrings)
            result[sheetName] = rows
        }
        return result
    }

    private fun parseWorksheetXml(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType

        var currentRow = mutableMapOf<Int, String>()
        var maxColInRow = -1
        var currentColIndex = 0
        var currentCellType: String? = null
        var lastVal = ""

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRow = mutableMapOf()
                            maxColInRow = -1
                        }
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r") ?: ""
                            currentColIndex = columnRefToIndex(ref)
                            currentCellType = parser.getAttributeValue(null, "t")
                            lastVal = ""
                        }
                        "v", "t" -> {
                            lastVal = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            val resolved = if (currentCellType == "s") {
                                val sIdx = lastVal.toIntOrNull() ?: -1
                                sharedStrings.getOrElse(sIdx) { lastVal }
                            } else {
                                lastVal
                            }
                            currentRow[currentColIndex] = resolved.trim()
                            if (currentColIndex > maxColInRow) maxColInRow = currentColIndex
                        }
                        "row" -> {
                            if (maxColInRow >= 0) {
                                val rowList = (0..maxColInRow).map { currentRow[it] ?: "" }
                                if (rowList.any { it.isNotBlank() }) {
                                    rows.add(rowList)
                                }
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun columnRefToIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return 0
        var index = 0
        for (ch in letters) {
            index = index * 26 + (ch - 'A' + 1)
        }
        return index - 1
    }

    /**
     * Parses CSV from Uri or raw CSV text
     */
    suspend fun readCsvRows(context: Context, uri: Uri): List<List<String>> =
        withContext(Dispatchers.IO) {
            val rows = mutableListOf<List<String>>()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).forEachLine { line ->
                    if (line.isNotBlank()) {
                        rows.add(parseCsvLine(line))
                    }
                }
            }
            rows
        }

    fun parseCsvText(csvContent: String): List<List<String>> {
        return csvContent.lines()
            .filter { it.isNotBlank() }
            .map { parseCsvLine(it) }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                (ch == ',' || ch == ';') && !inQuotes -> {
                    result.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        result.add(cur.toString().trim())
        return result
    }

    /**
     * Downloads public or shared Google Sheets CSV by Spreadsheet URL or ID and GID/SheetName
     */
    suspend fun fetchGoogleSheetCsv(sheetUrlOrId: String, sheetName: String): List<List<String>> =
        withContext(Dispatchers.IO) {
            val spreadsheetId = extractSpreadsheetId(sheetUrlOrId)
            val exportUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&sheet=${Uri.encode(sheetName)}"
            val request = Request.Builder().url(exportUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Gagal mengakses Google Sheets (HTTP ${response.code}). Pastikan link Google Sheets memiliki akses 'Anyone with the link' atau gunakan Apps Script Webhook.")
                }
                val body = response.body?.string() ?: ""
                parseCsvText(body)
            }
        }

    /**
     * Pushes full database synchronization payload to a Google Sheets Apps Script Web App URL
     */
    suspend fun pushToGoogleSheetsWebhook(
        webhookUrl: String,
        units: List<FreezerUnitEntity>,
        customers: List<CustomerEntity>,
        transactions: List<RentalTransactionEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank()) return@withContext false
        val jsonPayload = buildString {
            append("{")
            append("\"source\":\"SEWAin Aja Freezer SOC\",")
            append("\"timestamp\":\"${DateCurrencyUtils.nowDateTimeDisplay()}\",")
            append("\"unitsCount\":${units.size},")
            append("\"customersCount\":${customers.size},")
            append("\"transactionsCount\":${transactions.size}")
            append("}")
        }
        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(webhookUrl).post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            resp.isSuccessful
        }
    }

    private fun extractSpreadsheetId(input: String): String {
        val trimmed = input.trim()
        val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
        val match = regex.find(trimmed)
        return match?.groupValues?.get(1) ?: trimmed
    }

    /**
     * Validates rows before import and produces a comprehensive SheetImportPreview
     * (Section 9: Preview data, Deteksi duplikat, Validasi tanggal, Validasi nominal, Validasi nomor HP, Deteksi kolom tidak cocok)
     */
    suspend fun analyzeSheetPreview(
        fileName: String,
        availableSheets: List<String>,
        selectedSheet: String,
        allRows: List<List<String>>,
        repository: FreezerRepository
    ): SheetImportPreview {
        if (allRows.isEmpty()) {
            return SheetImportPreview(
                fileName = fileName,
                availableSheets = availableSheets,
                selectedSheet = selectedSheet,
                headers = emptyList(),
                rows = emptyList(),
                detectedDuplicates = 0,
                invalidDatesCount = 0,
                invalidNominalsCount = 0,
                invalidPhonesCount = 0,
                mismatchedColumnsWarning = listOf("Sheet kosong atau tidak memiliki baris data."),
                rowErrors = emptyList()
            )
        }

        // Find header row (first row containing keywords like UNIT, NAMA, OWNER, NIK)
        val headerIdx = allRows.indexOfFirst { row ->
            val joined = row.joinToString(" ").uppercase()
            joined.contains("UNIT") || joined.contains("NAMA") || joined.contains("OWNER") || joined.contains("NIK")
        }.coerceAtLeast(0)

        val headers = allRows[headerIdx].map { it.uppercase().trim() }
        val dataRows = allRows.drop(headerIdx + 1).filter { row -> row.any { it.isNotBlank() } }

        val warnings = mutableListOf<String>()
        val rowErrors = mutableListOf<String>()
        var duplicates = 0
        var invalidDates = 0
        var invalidNominals = 0
        var invalidPhones = 0

        val existingUnits = repository.getAllUnitsOnce()
        val existingCustomers = repository.getAllCustomersOnce()

        val upperSheet = selectedSheet.uppercase()
        if (upperSheet.contains("PELANGGAN")) {
            val expectedCols = listOf("NAMA", "NIK", "ALAMAT", "JENIS KELAMIN", "NO HP")
            for (col in expectedCols) {
                if (headers.none { it.contains(col) || (col == "NO HP" && it.contains("HP")) }) {
                    warnings.add("Kolom '$col' tidak ditemukan secara eksplisit pada sheet DATA PELANGGAN (sistem pemetaan otomatis diaktifkan).")
                }
            }
            val idxNama = headers.indexOfFirst { it.contains("NAMA") }.takeIf { it >= 0 } ?: 0
            val idxNik = headers.indexOfFirst { it.contains("NIK") }.takeIf { it >= 0 } ?: 1
            val idxHp = headers.indexOfFirst { it.contains("HP") || it.contains("TELP") }.takeIf { it >= 0 } ?: 4

            dataRows.forEachIndexed { rIdx, row ->
                val nama = row.getOrNull(idxNama)?.trim() ?: ""
                val nik = row.getOrNull(idxNik)?.trim() ?: ""
                val hp = row.getOrNull(idxHp)?.trim() ?: ""

                if (existingCustomers.any {
                        (nik.isNotBlank() && it.nik == nik) ||
                            (hp.isNotBlank() && it.nomorHp == hp) ||
                            (nama.isNotBlank() && it.namaLengkap.equals(nama, ignoreCase = true))
                    }
                ) {
                    duplicates++
                }
                if (hp.isNotBlank() && !DateCurrencyUtils.isValidPhone(hp)) {
                    invalidPhones++
                    rowErrors.add("Baris #${rIdx + 2}: Nomor HP '$hp' tidak sesuai format standar Indonesia (08xx / +62).")
                }
            }
        } else {
            // Sheet 1 (INVESTASI SEWA FREEZER) or Sheet 2 (INVESTASI FREEZER POINT FROZEN)
            val expectedMain = listOf("UNIT", "OWNER", "HARGA UNIT", "SIZE", "CICILAN")
            for (col in expectedMain) {
                if (headers.none { it.contains(col) || (col == "HARGA UNIT" && it.contains("HARGA")) }) {
                    warnings.add("Kolom '$col' menggunakan variasi format lama (pemetaan otomatis aktif).")
                }
            }

            val idxUnit = headers.indexOfFirst { it.contains("UNIT") }.takeIf { it >= 0 } ?: 0
            val idxHarga = headers.indexOfFirst { it.contains("HARGA") }.takeIf { it >= 0 } ?: 2
            val idxCicilan = headers.indexOfFirst { it.contains("CICILAN") }.takeIf { it >= 0 } ?: 4

            dataRows.forEachIndexed { rIdx, row ->
                val unitLabel = row.getOrNull(idxUnit)?.trim() ?: ""
                val hargaStr = row.getOrNull(idxHarga)?.trim() ?: ""
                val cicilanStr = row.getOrNull(idxCicilan)?.trim() ?: ""

                if (unitLabel.isNotBlank() && existingUnits.any {
                        it.kodeUnit.equals(unitLabel, ignoreCase = true) ||
                            it.namaUnit.equals(unitLabel, ignoreCase = true)
                    }
                ) {
                    duplicates++
                }

                if (hargaStr.isNotBlank() && DateCurrencyUtils.parseNominal(hargaStr) == null) {
                    invalidNominals++
                    rowErrors.add("Baris #${rIdx + 2}: Harga Unit '$hargaStr' bukan angka nominal valid.")
                }
                if (cicilanStr.isNotBlank() && DateCurrencyUtils.parseNominal(cicilanStr) == null) {
                    invalidNominals++
                    rowErrors.add("Baris #${rIdx + 2}: Cicilan '$cicilanStr' bukan angka nominal valid.")
                }

                // Check repeating monthly rental columns (TGL SEWA, TGL AKHIR SEWA, NOMINAL)
                headers.forEachIndexed { colIdx, colName ->
                    val cellVal = row.getOrNull(colIdx)?.trim() ?: ""
                    if (cellVal.isNotBlank() && cellVal != "-") {
                        if (colName.contains("TGL") && DateCurrencyUtils.parseFlexibleDateToIso(cellVal) == null) {
                            invalidDates++
                            rowErrors.add("Baris #${rIdx + 2} Kolom $colName: Tanggal '$cellVal' tidak valid.")
                        }
                        if (colName.contains("NOMINAL") && DateCurrencyUtils.parseNominal(cellVal) == null) {
                            invalidNominals++
                            rowErrors.add("Baris #${rIdx + 2} Kolom $colName: Nominal '$cellVal' tidak valid.")
                        }
                    }
                }
            }
        }

        return SheetImportPreview(
            fileName = fileName,
            availableSheets = availableSheets,
            selectedSheet = selectedSheet,
            headers = headers,
            rows = dataRows,
            detectedDuplicates = duplicates,
            invalidDatesCount = invalidDates,
            invalidNominalsCount = invalidNominals,
            invalidPhonesCount = invalidPhones,
            mismatchedColumnsWarning = warnings,
            rowErrors = rowErrors
        )
    }

    /**
     * Executes the import into Room database without deleting any existing records.
     * Supports automatic mapping of repeating monthly rental columns and legacy status variations!
     */
    suspend fun executeImport(
        preview: SheetImportPreview,
        repository: FreezerRepository
    ): ImportExecutionReport {
        var success = 0
        var failed = 0
        var duplicatesMerged = 0
        val errors = mutableListOf<String>()

        val headers = preview.headers
        val upperSheet = preview.selectedSheet.uppercase()

        if (upperSheet.contains("PELANGGAN")) {
            val idxNama = headers.indexOfFirst { it.contains("NAMA") }.takeIf { it >= 0 } ?: 0
            val idxNik = headers.indexOfFirst { it.contains("NIK") }.takeIf { it >= 0 } ?: 1
            val idxAlamat = headers.indexOfFirst { it.contains("ALAMAT") }.takeIf { it >= 0 } ?: 2
            val idxJk = headers.indexOfFirst { it.contains("KELAMIN") || it.contains("JK") }.takeIf { it >= 0 } ?: 3
            val idxHp = headers.indexOfFirst { it.contains("HP") || it.contains("TELP") }.takeIf { it >= 0 } ?: 4

            val existingCustomers = repository.getAllCustomersOnce().toMutableList()

            preview.rows.forEachIndexed { idx, row ->
                try {
                    val nama = row.getOrNull(idxNama)?.trim() ?: ""
                    if (nama.isBlank()) {
                        failed++
                        errors.add("Baris #${idx + 2}: Nama pelanggan kosong.")
                        return@forEachIndexed
                    }
                    val nik = row.getOrNull(idxNik)?.trim()?.ifBlank { "337200000000${1000 + idx}" } ?: ""
                    val alamat = row.getOrNull(idxAlamat)?.trim()?.ifBlank { "Solo Raya" } ?: "Solo Raya"
                    val jkRaw = row.getOrNull(idxJk)?.trim()?.uppercase() ?: "L"
                    val jk = if (jkRaw.startsWith("P") || jkRaw.contains("PEREMPUAN") || jkRaw.contains("WANITA")) "Perempuan" else "Laki-laki"
                    val hp = row.getOrNull(idxHp)?.trim() ?: ""

                    val duplicate = existingCustomers.find {
                        (nik.isNotBlank() && it.nik == nik) ||
                            (hp.isNotBlank() && it.nomorHp == hp) ||
                            it.namaLengkap.equals(nama, ignoreCase = true)
                    }

                    if (duplicate != null) {
                        repository.saveCustomer(
                            duplicate.copy(
                                alamatLengkap = alamat.ifBlank { duplicate.alamatLengkap },
                                nomorHp = hp.ifBlank { duplicate.nomorHp }
                            )
                        )
                        duplicatesMerged++
                        success++
                    } else {
                        val newId = repository.saveCustomer(
                            CustomerEntity(
                                namaLengkap = nama,
                                nik = nik,
                                nomorHp = hp,
                                alamatLengkap = alamat,
                                jenisKelamin = jk,
                                tanggalRegistrasi = DateCurrencyUtils.todayIso()
                            )
                        )
                        existingCustomers.add(
                            CustomerEntity(
                                id = newId,
                                namaLengkap = nama,
                                nik = nik,
                                nomorHp = hp,
                                alamatLengkap = alamat,
                                jenisKelamin = jk,
                                tanggalRegistrasi = DateCurrencyUtils.todayIso()
                            )
                        )
                        success++
                    }
                } catch (e: Exception) {
                    failed++
                    errors.add("Baris #${idx + 2}: ${e.localizedMessage}")
                }
            }
        } else {
            // Sheet 1: INVESTASI SEWA FREEZER or Sheet 2: INVESTASI FREEZER POINT FROZEN
            val isPointFrozen = upperSheet.contains("POINT FROZEN")
            val sheetCategory = if (isPointFrozen) "INVESTASI FREEZER POINT FROZEN" else "INVESTASI SEWA FREEZER"

            val idxUnit = headers.indexOfFirst { it.contains("UNIT") && !it.contains("HARGA") }.takeIf { it >= 0 } ?: 0
            val idxOwner = headers.indexOfFirst { it.contains("OWNER") || it.contains("PEMILIK") }.takeIf { it >= 0 } ?: 1
            val idxHarga = headers.indexOfFirst { it.contains("HARGA") }.takeIf { it >= 0 } ?: 2
            val idxSize = headers.indexOfFirst { it.contains("SIZE") || it.contains("KAPASITAS") || it.contains("LITER") }.takeIf { it >= 0 } ?: 3
            val idxCicilan = headers.indexOfFirst { it.contains("CICILAN") }.takeIf { it >= 0 } ?: 4
            val idxStatusInv = headers.indexOfFirst { it.contains("STATUS INVESTASI") || it == "STATUS" }.takeIf { it >= 0 } ?: -1

            // Detect repeating monthly rental blocks starting after column 4:
            // NAMA, ALAMAT, TGL SEWA, TGL AKHIR SEWA, NOMINAL, STATUS SEWA, SUDAH BAYAR, KWITANSI
            val namaIndices = headers.mapIndexedNotNull { i, h ->
                if (i >= 5 && (h == "NAMA" || h.startsWith("NAMA PELANGGAN") || h.contains("PENYEWA"))) i else null
            }

            val existingUnits = repository.getAllUnitsOnce().toMutableList()
            val existingCustomers = repository.getAllCustomersOnce().toMutableList()

            preview.rows.forEachIndexed { rIdx, row ->
                try {
                    val rawUnitName = row.getOrNull(idxUnit)?.trim() ?: ""
                    if (rawUnitName.isBlank()) {
                        failed++
                        errors.add("Baris #${rIdx + 2}: Kolom UNIT kosong.")
                        return@forEachIndexed
                    }

                    val owner = row.getOrNull(idxOwner)?.trim()?.ifBlank { "Investasi SAF" } ?: "Investasi SAF"
                    val harga = DateCurrencyUtils.parseNominal(row.getOrNull(idxHarga) ?: "0") ?: 3000000L
                    val sizeDigits = (row.getOrNull(idxSize) ?: "200").filter { it.isDigit() }.toIntOrNull() ?: 200
                    val cicilan = DateCurrencyUtils.parseNominal(row.getOrNull(idxCicilan) ?: "0") ?: 0L
                    val rawInvStatus = if (idxStatusInv >= 0) row.getOrNull(idxStatusInv)?.uppercase()?.trim() ?: "" else ""
                    val mappedInvStatus = mapInvestmentStatus(rawInvStatus)

                    val duplicateUnit = existingUnits.find {
                        it.kodeUnit.equals(rawUnitName, ignoreCase = true) ||
                            it.namaUnit.equals(rawUnitName, ignoreCase = true)
                    }

                    val unitId = if (duplicateUnit != null) {
                        val updated = duplicateUnit.copy(
                            owner = owner,
                            hargaPembelian = if (harga > 0) harga else duplicateUnit.hargaPembelian,
                            kapasitasLiter = if (sizeDigits > 0) sizeDigits else duplicateUnit.kapasitasLiter,
                            cicilanPerBulan = if (cicilan > 0) cicilan else duplicateUnit.cicilanPerBulan,
                            statusInvestasi = mappedInvStatus,
                            kategoriSheet = sheetCategory
                        )
                        repository.saveUnit(updated)
                        duplicatesMerged++
                        duplicateUnit.id
                    } else {
                        val generatedKode = if (rawUnitName.length <= 12 && rawUnitName.any { it.isDigit() }) {
                            rawUnitName.uppercase()
                        } else {
                            String.format("SAF-IMP-%03d", existingUnits.size + 1)
                        }
                        val jenis = when {
                            rawUnitName.contains("SHOWCASE", ignoreCase = true) -> "Showcase"
                            rawUnitName.contains("ASI", ignoreCase = true) -> "Freezer ASI"
                            else -> "Chest Freezer"
                        }
                        val newUnit = FreezerUnitEntity(
                            kodeUnit = generatedKode,
                            namaUnit = rawUnitName,
                            jenisUnit = jenis,
                            owner = owner,
                            hargaPembelian = harga,
                            kapasitasLiter = sizeDigits,
                            cicilanPerBulan = cicilan,
                            statusInvestasi = mappedInvStatus,
                            kategoriSheet = sheetCategory,
                            merek = rawUnitName.split(" ").firstOrNull() ?: "RSA",
                            model = "Series-$sizeDigits",
                            nomorSeri = "SN-$generatedKode",
                            statusUnit = if (mappedInvStatus == "HILANG") "Hilang" else "Tersedia",
                            lokasiUnit = "Solo Raya",
                            tanggalPembelian = DateCurrencyUtils.todayIso()
                        )
                        val id = repository.saveUnit(newUnit)
                        existingUnits.add(newUnit.copy(id = id))
                        id
                    }

                    // Process repeating monthly rental periods if present in row
                    for ((periodNum, namaColIdx) in namaIndices.withIndex()) {
                        val penyewaNama = row.getOrNull(namaColIdx)?.trim() ?: ""
                        if (penyewaNama.isBlank() || penyewaNama == "-") continue

                        val alamat = row.getOrNull(namaColIdx + 1)?.trim()?.ifBlank { "Solo Raya" } ?: "Solo Raya"
                        val tglSewaRaw = row.getOrNull(namaColIdx + 2)?.trim() ?: ""
                        val tglAkhirRaw = row.getOrNull(namaColIdx + 3)?.trim() ?: ""
                        val nominalRaw = row.getOrNull(namaColIdx + 4)?.trim() ?: ""
                        val statusSewaRaw = row.getOrNull(namaColIdx + 5)?.trim() ?: ""
                        val sudahBayarRaw = row.getOrNull(namaColIdx + 6)?.trim() ?: ""
                        val kwitansiRaw = row.getOrNull(namaColIdx + 7)?.trim() ?: ""

                        val tglMulai = DateCurrencyUtils.parseFlexibleDateToIso(tglSewaRaw) ?: DateCurrencyUtils.todayIso()
                        val tglAkhir = DateCurrencyUtils.parseFlexibleDateToIso(tglAkhirRaw)
                            ?: DateCurrencyUtils.addMonthsToIsoDate(tglMulai, 1)
                        val nominal = DateCurrencyUtils.parseNominal(nominalRaw) ?: 250000L
                        val sudahBayar = DateCurrencyUtils.parseNominal(sudahBayarRaw)
                            ?: if (statusSewaRaw.contains("LUNAS", true) || statusSewaRaw.contains("DONE", true)) nominal else 0L

                        // Ensure customer exists
                        var cust = existingCustomers.find { it.namaLengkap.equals(penyewaNama, ignoreCase = true) }
                        if (cust == null) {
                            val newCust = CustomerEntity(
                                namaLengkap = penyewaNama,
                                nik = "33720099${1000 + existingCustomers.size}",
                                nomorHp = "08120000${1000 + existingCustomers.size}",
                                alamatLengkap = alamat,
                                jenisKelamin = "Laki-laki",
                                tanggalRegistrasi = tglMulai
                            )
                            val cId = repository.saveCustomer(newCust)
                            cust = newCust.copy(id = cId)
                            existingCustomers.add(cust)
                        }

                        val invoiceNo = if (kwitansiRaw.isNotBlank() && kwitansiRaw != "-") {
                            kwitansiRaw
                        } else {
                            repository.generateUniqueInvoiceNumber()
                        }

                        val mappedPayStatus = mapLegacyPaymentStatus(statusSewaRaw, sudahBayarRaw, nominal, sudahBayar)
                        val mappedRentStatus = mapLegacyRentalStatus(statusSewaRaw)

                        repository.saveTransaction(
                            RentalTransactionEntity(
                                nomorInvoice = invoiceNo,
                                tanggalPemesanan = tglMulai,
                                customerId = cust.id,
                                namaPelangganSnapshot = cust.namaLengkap,
                                unitId = unitId,
                                namaUnitSnapshot = rawUnitName,
                                kodeUnitSnapshot = rawUnitName.take(12),
                                hargaSewa = nominal,
                                durasiSewaBulan = 1,
                                durasiHari = 30,
                                tanggalMulaiSewa = tglMulai,
                                tanggalAkhirSewa = tglAkhir,
                                tanggalJatuhTempo = tglAkhir,
                                nominalPembayaran = sudahBayar,
                                totalTagihan = nominal,
                                statusPembayaran = mappedPayStatus,
                                statusPenyewaan = mappedRentStatus,
                                metodePembayaran = "Transfer",
                                alamatLokasiGps = alamat,
                                periodeBulanLabel = "PERIODE #${periodNum + 1}",
                                catatan = "Diimpor dari $upperSheet"
                            )
                        )
                    }

                    success++
                } catch (e: Exception) {
                    failed++
                    errors.add("Baris #${rIdx + 2}: ${e.localizedMessage}")
                }
            }
        }

        repository.logSync(
            jenisOperasi = "IMPORT (${preview.fileName})",
            sheetName = preview.selectedSheet,
            berhasil = success,
            gagal = failed,
            status = if (failed == 0) "SUKSES" else "SEBAGIAN",
            detail = "Berhasil: $success, Duplikat digabung: $duplicatesMerged, Gagal: $failed"
        )

        return ImportExecutionReport(
            sheetName = preview.selectedSheet,
            successCount = success,
            failedCount = failed,
            duplicateSkippedOrMerged = duplicatesMerged,
            errors = errors
        )
    }

    private fun mapInvestmentStatus(raw: String): String {
        val u = raw.uppercase()
        return when {
            u.contains("HILANG") -> "HILANG"
            u.contains("LUNAS") -> "LUNAS"
            u.contains("DONE") || u.contains("SELESAI") -> "DONE"
            u.contains("SEBAGIAN") || u.contains("CICIL") || u.contains("DP") -> "DIBAYAR SEBAGIAN"
            else -> "LIST"
        }
    }

    private fun mapLegacyPaymentStatus(statusRaw: String, sudahBayarRaw: String, nominal: Long, sudahBayar: Long): String {
        val combined = "$statusRaw $sudahBayarRaw".uppercase()
        return when {
            combined.contains("LUNAS") || combined.contains("DONE") || combined.contains("SUDAH") || (nominal > 0 && sudahBayar >= nominal) -> "Lunas"
            combined.contains("TELAT") || combined.contains("TERLAMBAT") || combined.contains("OVERDUE") -> "Terlambat"
            combined.contains("DP") || combined.contains("SEBAGIAN") || sudahBayar in 1 until nominal -> "Dibayar Sebagian"
            else -> "Belum Bayar"
        }
    }

    private fun mapLegacyRentalStatus(statusRaw: String): String {
        val u = statusRaw.uppercase()
        return when {
            u.contains("BATAL") || u.contains("CANCEL") -> "Dibatalkan"
            u.contains("SELESAI") || u.contains("KEMBALI") || u.contains("PULL") -> "Selesai"
            u.contains("BOOK") || u.contains("KEEP") -> "Booking"
            else -> "Dalam Penyewaan"
        }
    }

    /**
     * Generates a valid multi-sheet `.xlsx` file compatible with TIMELINE SEWA IN AJA FREEZER.xlsx
     * containing:
     * - Sheet 1: INVESTASI SEWA FREEZER
     * - Sheet 2: INVESTASI FREEZER POINT FROZEN
     * - Sheet 3: DATA PELANGGAN
     * - Sheet 4: TRANSAKSI & JADWAL JATUH TEMPO
     */
    suspend fun exportFullWorkbookXlsx(
        context: Context,
        units: List<FreezerUnitEntity>,
        customers: List<CustomerEntity>,
        transactions: List<RentalTransactionEntity>,
        filePrefix: String = "TIMELINE_SEWA_IN_AJA_FREEZER"
    ): File = withContext(Dispatchers.IO) {
        val exportsDir = File(context.filesDir, "exports")
        if (!exportsDir.exists()) exportsDir.mkdirs()
        val outFile = File(exportsDir, "${filePrefix}_${DateCurrencyUtils.todayIso()}.xlsx")

        val sheet1Rows = mutableListOf<List<String>>()
        sheet1Rows.add(
            listOf(
                "UNIT", "OWNER", "HARGA UNIT", "SIZE", "CICILAN",
                "NAMA", "ALAMAT", "TGL SEWA", "TGL AKHIR SEWA", "NOMINAL", "STATUS SEWA", "SUDAH BAYAR", "KWITANSI"
            )
        )
        units.filter { it.kategoriSheet != "INVESTASI FREEZER POINT FROZEN" }.forEach { u ->
            val latestTx = transactions.firstOrNull { it.unitId == u.id }
            sheet1Rows.add(
                listOf(
                    "${u.kodeUnit} - ${u.namaUnit}",
                    u.owner,
                    u.hargaPembelian.toString(),
                    "${u.kapasitasLiter}L",
                    u.cicilanPerBulan.toString(),
                    latestTx?.namaPelangganSnapshot ?: "-",
                    latestTx?.alamatLokasiGps?.ifBlank { u.lokasiUnit } ?: u.lokasiUnit,
                    latestTx?.tanggalMulaiSewa ?: "-",
                    latestTx?.tanggalAkhirSewa ?: "-",
                    (latestTx?.totalTagihan ?: 0L).toString(),
                    latestTx?.statusPenyewaan ?: u.statusUnit,
                    (latestTx?.nominalPembayaran ?: 0L).toString(),
                    latestTx?.nomorInvoice ?: "-"
                )
            )
        }

        val sheet2Rows = mutableListOf<List<String>>()
        sheet2Rows.add(listOf("UNIT", "OWNER", "HARGA UNIT", "SIZE", "CICILAN", "TOTAL DIBAYAR", "STATUS INVESTASI"))
        units.forEach { u ->
            sheet2Rows.add(
                listOf(
                    "${u.kodeUnit} - ${u.namaUnit}",
                    u.owner,
                    u.hargaPembelian.toString(),
                    "${u.kapasitasLiter}L",
                    u.cicilanPerBulan.toString(),
                    u.totalPembayaranCicilan.toString(),
                    u.statusInvestasi
                )
            )
        }

        val sheet3Rows = mutableListOf<List<String>>()
        sheet3Rows.add(listOf("NAMA", "NIK", "ALAMAT", "JENIS KELAMIN", "NO HP", "TGL REGISTRASI"))
        customers.forEach { c ->
            sheet3Rows.add(
                listOf(c.namaLengkap, c.nik, c.alamatLengkap, c.jenisKelamin, c.nomorHp, c.tanggalRegistrasi)
            )
        }

        val sheet4Rows = mutableListOf<List<String>>()
        sheet4Rows.add(
            listOf(
                "NO INVOICE", "TGL PESAN", "NAMA PELANGGAN", "UNIT FREEZER", "DURASI (HARI)",
                "TGL MULAI", "TGL AKHIR", "JATUH TEMPO", "BIAYA SEWA", "ONGKIR",
                "TOTAL TAGIHAN", "DIBAYAR", "PIUTANG", "STATUS BAYAR", "STATUS SEWA",
                "KOORDINAT GPS", "ALAMAT LOKASI"
            )
        )
        transactions.forEach { t ->
            val piutang = (t.totalTagihan - t.nominalPembayaran).coerceAtLeast(0L)
            val gps = if (t.latitude != null && t.longitude != null) "${t.latitude},${t.longitude}" else "-"
            sheet4Rows.add(
                listOf(
                    t.nomorInvoice, t.tanggalPemesanan, t.namaPelangganSnapshot, t.namaUnitSnapshot,
                    "${t.durasiHari} Hari", t.tanggalMulaiSewa, t.tanggalAkhirSewa, t.tanggalJatuhTempo,
                    t.hargaSewa.toString(), t.ongkir.toString(), t.totalTagihan.toString(),
                    t.nominalPembayaran.toString(), piutang.toString(),
                    t.statusPembayaran, t.statusPenyewaan, gps, t.alamatLokasiGps
                )
            )
        }

        writeMultiSheetXlsx(
            outFile,
            listOf(
                "INVESTASI SEWA FREEZER" to sheet1Rows,
                "INVESTASI FREEZER POINT FROZEN" to sheet2Rows,
                "DATA PELANGGAN" to sheet3Rows,
                "TRANSAKSI DAN JATUH TEMPO" to sheet4Rows
            )
        )
        outFile
    }

    /**
     * Exports a specific category (Pelanggan, Unit, Transaksi, Pendapatan, Jadwal Jatuh Tempo) to CSV
     */
    suspend fun exportCategoryCsv(
        context: Context,
        categoryName: String,
        rows: List<List<String>>
    ): File = withContext(Dispatchers.IO) {
        val exportsDir = File(context.filesDir, "exports")
        if (!exportsDir.exists()) exportsDir.mkdirs()
        val safeName = categoryName.replace(" ", "_").uppercase()
        val outFile = File(exportsDir, "SAF_${safeName}_${DateCurrencyUtils.todayIso()}.csv")
        outFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            for (row in rows) {
                val escaped = row.joinToString(",") { cell ->
                    "\"${cell.replace("\"", "\"\"")}\""
                }
                writer.write(escaped)
                writer.newLine()
            }
        }
        outFile
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, "Bagikan / Simpan File Export").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun writeMultiSheetXlsx(outFile: File, sheets: List<Pair<String, List<List<String>>>>) {
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            val sheetOverrides = sheets.indices.joinToString("\n") { idx ->
                """<Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  $sheetOverrides
</Types>"""
            zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            zos.write(rootRels.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            val wbRels = sheets.indices.joinToString("\n") { idx ->
                """<Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>"""
            }
            val wbRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  $wbRels
</Relationships>"""
            zos.write(wbRelsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. xl/workbook.xml
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            val sheetEntries = sheets.mapIndexed { idx, (name, _) ->
                """<sheet name="${escapeXml(name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>"""
            }.joinToString("\n")
            val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    $sheetEntries
  </sheets>
</workbook>"""
            zos.write(workbookXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. xl/worksheets/sheet{N}.xml
            sheets.forEachIndexed { idx, (_, rows) ->
                zos.putNextEntry(ZipEntry("xl/worksheets/sheet${idx + 1}.xml"))
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                rows.forEachIndexed { rIdx, row ->
                    val rowNum = rIdx + 1
                    sb.append("""<row r="$rowNum">""")
                    row.forEachIndexed { cIdx, cellVal ->
                        val colLetter = indexToColumnLetter(cIdx)
                        val ref = "$colLetter$rowNum"
                        sb.append("""<c r="$ref" t="inlineStr"><is><t>${escapeXml(cellVal)}</t></is></c>""")
                    }
                    sb.append("""</row>""")
                }
                sb.append("""</sheetData></worksheet>""")
                zos.write(sb.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun indexToColumnLetter(colIndex: Int): String {
        var n = colIndex + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
