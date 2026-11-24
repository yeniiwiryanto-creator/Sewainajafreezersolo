package com.example.sheets

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.backup.BackupJsonMapper
import com.example.backup.StructuredBackupEngine
import com.example.util.SecurityCryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

enum class ImportFileFormat(val label: String) {
    XLSX("Excel (.xlsx)"),
    CSV("CSV (.csv)"),
    JSON("JSON (.json)"),
    ZIP("Arsip Backup (.zip)"),
    UNKNOWN("Tidak Dikenali")
}

data class ParsedImportSheet(
    val sheetName: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val suggestedTarget: String = ""
)

data class ParsedImportFileResult(
    val isSuccess: Boolean,
    val errorMessage: String = "",
    val fileName: String = "",
    val detectedFormat: ImportFileFormat = ImportFileFormat.UNKNOWN,
    val detectedDelimiter: Char = ',',
    val detectedEncoding: String = "UTF-8",
    val sheets: List<ParsedImportSheet> = emptyList(),
    val isVerifiedBackupZip: Boolean = false
)

object SmartImportFormatParser {

    const val MAX_IMPORT_FILE_BYTES = 100L * 1024L * 1024L
    const val MAX_ZIP_ENTRIES = 8_000
    const val MAX_SINGLE_UNCOMPRESSED_BYTES = 50L * 1024L * 1024L

    val ALLOWED_EXTENSIONS = setOf("xlsx", "csv", "txt", "json", "zip")

    suspend fun parseUriOrFile(
        context: Context,
        source: Any,
        explicitFileName: String? = null
    ): ParsedImportFileResult = withContext(Dispatchers.IO) {
        try {
            val fileName = explicitFileName ?: when (source) {
                is File -> source.name
                is Uri -> resolveUriFileName(context, source) ?: "import_file"
                else -> "import_file"
            }

            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val bytes = when (source) {
                is File -> {
                    if (!source.exists() || source.length() <= 0L) {
                        return@withContext ParsedImportFileResult(false, "File import tidak ditemukan atau kosong.", fileName)
                    }
                    if (source.length() > MAX_IMPORT_FILE_BYTES) {
                        return@withContext ParsedImportFileResult(false, "Ukuran file melebihi batas maksimal 100 MB.", fileName)
                    }
                    source.readBytes()
                }
                is Uri -> {
                    val baos = ByteArrayOutputStream()
                    context.contentResolver.openInputStream(source)?.use { input ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        var read = input.read(buffer)
                        while (read > 0) {
                            total += read
                            if (total > MAX_IMPORT_FILE_BYTES) {
                                return@withContext ParsedImportFileResult(false, "Ukuran file melebihi batas maksimal 100 MB.", fileName)
                            }
                            baos.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                    } ?: return@withContext ParsedImportFileResult(false, "Tidak dapat membuka file dari penyimpanan.", fileName)
                    baos.toByteArray()
                }
                is ByteArray -> source
                else -> return@withContext ParsedImportFileResult(false, "Tipe sumber import tidak didukung.", fileName)
            }

            if (bytes.isEmpty()) {
                return@withContext ParsedImportFileResult(false, "Isi file kosong dan tidak dapat diimpor.", fileName)
            }

            val format = detectFormat(fileName, ext, bytes)
            if (format == ImportFileFormat.UNKNOWN) {
                return@withContext ParsedImportFileResult(
                    false,
                    "Format file '$fileName' tidak didukung. Gunakan .xlsx, .csv, .json, atau .zip.",
                    fileName
                )
            }

            when (format) {
                ImportFileFormat.XLSX -> parseXlsxBytes(fileName, bytes)
                ImportFileFormat.CSV -> parseCsvBytes(fileName, bytes)
                ImportFileFormat.JSON -> parseJsonBytes(fileName, bytes)
                ImportFileFormat.ZIP -> parseZipBytes(context, fileName, bytes)
                ImportFileFormat.UNKNOWN -> ParsedImportFileResult(false, "Format tidak didukung", fileName)
            }
        } catch (e: Exception) {
            ParsedImportFileResult(false, "Gagal membaca file import: ${e.localizedMessage ?: "File korup"}")
        }
    }

    fun detectFormat(fileName: String, ext: String, bytes: ByteArray): ImportFileFormat {
        val isZipHeader = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (ext == "xlsx" && isZipHeader) return ImportFileFormat.XLSX
        if (ext == "zip" && isZipHeader) return ImportFileFormat.ZIP
        if (ext == "csv" || ext == "txt") return ImportFileFormat.CSV
        if (ext == "json") return ImportFileFormat.JSON

        // Inspect magic signature when extension is missing or ambiguous
        if (isZipHeader) {
            var hasWorkbookXml = false
            runCatching {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "xl/workbook.xml") {
                            hasWorkbookXml = true
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            return if (hasWorkbookXml) ImportFileFormat.XLSX else ImportFileFormat.ZIP
        }
        val trimmed = String(bytes.take(256).toByteArray(), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return ImportFileFormat.JSON
        if (trimmed.isNotEmpty() && (ext.isBlank() || ext in ALLOWED_EXTENSIONS)) return ImportFileFormat.CSV
        return ImportFileFormat.UNKNOWN
    }

    private fun parseXlsxBytes(fileName: String, bytes: ByteArray): ParsedImportFileResult {
        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zis ->
            var count = 0
            var totalUncompressed = 0L
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                count++
                if (count > MAX_ZIP_ENTRIES) {
                    return ParsedImportFileResult(false, "File XLSX melebihi batas entry aman.", fileName, ImportFileFormat.XLSX)
                }
                if (!entry.isDirectory) {
                    val safeName = entry.name.replace('\\', '/').trimStart('/')
                    if (safeName.contains("..")) {
                        return ParsedImportFileResult(false, "File XLSX mengandung path traversal tidak aman.", fileName, ImportFileFormat.XLSX)
                    }
                    val entryBytes = zis.readBytes()
                    totalUncompressed += entryBytes.size
                    if (entryBytes.size > MAX_SINGLE_UNCOMPRESSED_BYTES || totalUncompressed > StructuredBackupEngine.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        return ParsedImportFileResult(false, "Ukuran konten XLSX melebihi batas aman.", fileName, ImportFileFormat.XLSX)
                    }
                    zipEntries[safeName] = entryBytes
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (!zipEntries.containsKey("xl/workbook.xml")) {
            return ParsedImportFileResult(false, "File bukan workbook Excel (.xlsx) yang valid.", fileName, ImportFileFormat.XLSX)
        }

        val sharedStrings = parseSharedStringsXml(zipEntries["xl/sharedStrings.xml"])
        val dateStyleIndices = parseDateStyleIndices(zipEntries["xl/styles.xml"])
        val sheetTargets = parseWorkbookSheets(zipEntries)

        val parsedSheets = mutableListOf<ParsedImportSheet>()
        for ((sheetName, path) in sheetTargets) {
            val sheetBytes = zipEntries[path] ?: continue
            val matrix = parseWorksheetXmlWithDates(
                xml = String(sheetBytes, Charsets.UTF_8),
                sharedStrings = sharedStrings,
                dateStyleIndices = dateStyleIndices
            ).filter { row -> row.any { it.isNotBlank() } }

            if (matrix.isEmpty()) continue
            val headers = matrix.first().mapIndexed { idx, h -> h.trim().ifBlank { "Kolom_${idx + 1}" } }
            val rows = matrix.drop(1).map { r ->
                val padded = MutableList(headers.size) { idx -> r.getOrElse(idx) { "" }.trim() }
                padded
            }
            parsedSheets.add(
                ParsedImportSheet(
                    sheetName = sheetName,
                    headers = headers,
                    rows = rows,
                    suggestedTarget = guessTargetFromNameOrHeaders(sheetName, headers)
                )
            )
        }

        if (parsedSheets.isEmpty()) {
            return ParsedImportFileResult(false, "Workbook Excel kosong atau tidak memiliki baris data.", fileName, ImportFileFormat.XLSX)
        }

        return ParsedImportFileResult(
            isSuccess = true,
            fileName = fileName,
            detectedFormat = ImportFileFormat.XLSX,
            sheets = parsedSheets
        )
    }

    private fun parseSharedStringsXml(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val list = mutableListOf<String>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(String(bytes, Charsets.UTF_8)))
        var event = parser.eventType
        var insideSi = false
        var current = StringBuilder()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        insideSi = true
                        current = StringBuilder()
                    } else if (insideSi && parser.name == "t") {
                        current.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si" && insideSi) {
                        list.add(current.toString())
                        insideSi = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseDateStyleIndices(stylesBytes: ByteArray?): Set<Int> {
        if (stylesBytes == null) return emptySet()
        val customDateFmtIds = mutableSetOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47)
        val dateCellXfIndices = mutableSetOf<Int>()
        runCatching {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(String(stylesBytes, Charsets.UTF_8)))
            var event = parser.eventType
            var insideCellXfs = false
            var xfIndex = 0
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "numFmt" -> {
                                val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                                val code = parser.getAttributeValue(null, "formatCode")?.lowercase(Locale.ROOT).orEmpty()
                                if (id != null && (code.contains("yy") || (code.contains("mm") && code.contains("dd")))) {
                                    customDateFmtIds.add(id)
                                }
                            }
                            "cellXfs" -> {
                                insideCellXfs = true
                                xfIndex = 0
                            }
                            "xf" -> if (insideCellXfs) {
                                val fmtId = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                                if (fmtId in customDateFmtIds) {
                                    dateCellXfIndices.add(xfIndex)
                                }
                                xfIndex++
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "cellXfs") insideCellXfs = false
                }
                event = parser.next()
            }
        }
        return dateCellXfIndices
    }

    private fun parseWorkbookSheets(zipEntries: Map<String, ByteArray>): List<Pair<String, String>> {
        val relTargets = mutableMapOf<String, String>()
        zipEntries["xl/_rels/workbook.xml.rels"]?.let { relBytes ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(String(relBytes, Charsets.UTF_8)))
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id").orEmpty()
                    val target = parser.getAttributeValue(null, "Target").orEmpty().trimStart('/')
                    if (id.isNotBlank() && target.isNotBlank()) {
                        relTargets[id] = if (target.startsWith("xl/")) target else "xl/$target"
                    }
                }
                ev = parser.next()
            }
        }

        val list = mutableListOf<Pair<String, String>>()
        zipEntries["xl/workbook.xml"]?.let { wbBytes ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(String(wbBytes, Charsets.UTF_8)))
            var ev = parser.eventType
            var idx = 1
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet$idx"
                    var rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    if (rId.isNullOrBlank()) {
                        rId = parser.getAttributeValue(null, "r:id")
                    }
                    val targetPath = relTargets[rId] ?: "xl/worksheets/sheet$idx.xml"
                    list.add(name to targetPath)
                    idx++
                }
                ev = parser.next()
            }
        }
        return list
    }

    private fun parseWorksheetXmlWithDates(
        xml: String,
        sharedStrings: List<String>,
        dateStyleIndices: Set<Int>
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var currentRow = mutableListOf<String>()
        var cellType = ""
        var cellStyleIdx = -1
        var cellRef = ""
        var currentValue = ""
        var insideRow = false
        var insideInlineText = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            insideRow = true
                            currentRow = mutableListOf()
                        }
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t").orEmpty()
                            cellStyleIdx = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                            cellRef = parser.getAttributeValue(null, "r").orEmpty()
                            currentValue = ""
                        }
                        "v" -> if (insideRow) {
                            val raw = parser.nextText()
                            currentValue = when (cellType) {
                                "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                                "b" -> if (raw == "1") "true" else "false"
                                else -> formatNumericOrDateCell(raw, cellStyleIdx in dateStyleIndices)
                            }
                        }
                        "t" -> if (insideRow) {
                            insideInlineText = true
                            currentValue += parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "c" -> if (insideRow) {
                            val colIdx = columnIndexFromCellRef(cellRef, currentRow.size)
                            while (currentRow.size < colIdx) currentRow.add("")
                            currentRow.add(currentValue.trim())
                        }
                        "row" -> if (insideRow) {
                            rows.add(currentRow)
                            insideRow = false
                        }
                        "t" -> insideInlineText = false
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun columnIndexFromCellRef(ref: String, fallbackIdx: Int): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
        if (letters.isEmpty()) return fallbackIdx
        var col = 0
        for (ch in letters) {
            col = col * 26 + (ch - 'A' + 1)
        }
        return (col - 1).coerceAtLeast(0)
    }

    fun formatNumericOrDateCell(raw: String, isDateStyled: Boolean): String {
        val trimmed = raw.trim()
        val num = trimmed.toDoubleOrNull() ?: return trimmed
        if (isDateStyled && num >= 20000.0 && num <= 75000.0) {
            return excelSerialToIsoDate(num.toInt())
        }
        // Preserve integer representation cleanly (avoid "12345.0" or scientific notation for long phone/NIK)
        val longVal = num.toLong()
        return if (num == longVal.toDouble()) longVal.toString() else trimmed
    }

    fun excelSerialToIsoDate(serialDays: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            // Excel epoch adjustment for 1900 leap-year bug
            set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
            add(Calendar.DAY_OF_YEAR, serialDays)
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(cal.time)
    }

    fun parseCsvBytes(fileName: String, bytes: ByteArray): ParsedImportFileResult {
        val (charset, offset) = detectEncodingAndBomOffset(bytes)
        val text = String(bytes, offset, bytes.size - offset, charset)
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) {
            return ParsedImportFileResult(false, "File CSV kosong.", fileName, ImportFileFormat.CSV)
        }
        val delimiter = detectCsvDelimiter(lines.take(10))
        val matrix = lines.map { splitCsvLine(it, delimiter) }.filter { row -> row.any { it.isNotBlank() } }
        val headers = matrix.first().mapIndexed { idx, h -> h.trim().ifBlank { "Kolom_${idx + 1}" } }
        val rows = matrix.drop(1).map { r ->
            MutableList(headers.size) { idx -> r.getOrElse(idx) { "" }.trim() }
        }
        return ParsedImportFileResult(
            isSuccess = true,
            fileName = fileName,
            detectedFormat = ImportFileFormat.CSV,
            detectedDelimiter = delimiter,
            detectedEncoding = charset.name(),
            sheets = listOf(
                ParsedImportSheet(
                    sheetName = "CSV (${if (delimiter == '\t') "TAB" else delimiter})",
                    headers = headers,
                    rows = rows,
                    suggestedTarget = guessTargetFromNameOrHeaders(fileName, headers)
                )
            )
        )
    }

    private fun detectEncodingAndBomOffset(bytes: ByteArray): Pair<Charset, Int> {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8 to 3
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE to 2
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE to 2
        }
        // Check if valid UTF-8 vs fallback ISO-8859-1
        val utf8Decoder = Charsets.UTF_8.newDecoder()
        val isUtf8 = runCatching {
            utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes))
        }.isSuccess
        return (if (isUtf8) Charsets.UTF_8 else Charsets.ISO_8859_1) to 0
    }

    fun detectCsvDelimiter(sampleLines: List<String>): Char {
        val candidates = listOf(',', ';', '\t', '|')
        val firstLine = sampleLines.firstOrNull().orEmpty()
        return candidates.maxByOrNull { delim ->
            val counts = sampleLines.map { splitCsvLine(it, delim).size }
            val cols = counts.firstOrNull() ?: 1
            if (cols <= 1) 0 else cols * 100 - counts.sumOf { kotlin.math.abs(it - cols) }
        }?.takeIf { firstLine.contains(it) } ?: ','
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val cols = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    cur.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == delimiter && !inQuotes) {
                cols.add(cur.toString().trim())
                cur.clear()
            } else {
                cur.append(ch)
            }
            i++
        }
        cols.add(cur.toString().trim())
        return cols
    }

    fun parseJsonBytes(fileName: String, bytes: ByteArray): ParsedImportFileResult {
        val text = String(bytes, Charsets.UTF_8).trimStart('\uFEFF').trim()
        val sheets = mutableListOf<ParsedImportSheet>()

        if (text.startsWith("[")) {
            val arr = JSONArray(text)
            arrayToSheet("Data JSON", arr)?.let { sheets.add(it) }
        } else if (text.startsWith("{")) {
            val root = JSONObject(text)
            val knownKeys = listOf("customers", "pelanggan", "units", "freezer_units", "transactions", "transaksi", "payments", "paymentProofs", "data", "rows")
            var matched = false
            knownKeys.forEach { key ->
                val arr = root.optJSONArray(key)
                if (arr != null && arr.length() > 0) {
                    arrayToSheet(key, arr)?.let { sheets.add(it) }
                    matched = true
                }
            }
            if (!matched) {
                // Convert single flat object to 1-row sheet
                val keys = root.keys().asSequence().toList()
                if (keys.isNotEmpty()) {
                    sheets.add(
                        ParsedImportSheet(
                            sheetName = "JSON Object",
                            headers = keys,
                            rows = listOf(keys.map { root.opt(it)?.toString().orEmpty() }),
                            suggestedTarget = guessTargetFromNameOrHeaders(fileName, keys)
                        )
                    )
                }
            }
        } else {
            return ParsedImportFileResult(false, "Format JSON tidak valid (harus array atau object).", fileName, ImportFileFormat.JSON)
        }

        if (sheets.isEmpty()) {
            return ParsedImportFileResult(false, "File JSON tidak berisi data record yang dapat diimpor.", fileName, ImportFileFormat.JSON)
        }
        return ParsedImportFileResult(
            isSuccess = true,
            fileName = fileName,
            detectedFormat = ImportFileFormat.JSON,
            sheets = sheets
        )
    }

    private fun arrayToSheet(name: String, arr: JSONArray): ParsedImportSheet? {
        if (arr.length() == 0) return null
        val headers = linkedSetOf<String>()
        val objects = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            objects.add(obj)
            obj.keys().forEach { k -> headers.add(k) }
        }
        if (headers.isEmpty()) return null
        val headerList = headers.toList()
        val rows = objects.map { obj ->
            headerList.map { h ->
                val v = obj.opt(h)
                if (v == null || v == JSONObject.NULL) "" else v.toString()
            }
        }
        return ParsedImportSheet(
            sheetName = name,
            headers = headerList,
            rows = rows,
            suggestedTarget = guessTargetFromNameOrHeaders(name, headerList)
        )
    }

    private suspend fun parseZipBytes(context: Context, fileName: String, bytes: ByteArray): ParsedImportFileResult {
        val tempFile = File(context.cacheDir, "zip_import_${System.currentTimeMillis()}.zip")
        try {
            tempFile.writeBytes(bytes)
            val preview = StructuredBackupEngine.inspectBackupZipForRestore(context, tempFile)
            if (!preview.isValid) {
                return ParsedImportFileResult(false, preview.errorMessage, fileName, ImportFileFormat.ZIP)
            }
            val sheets = mutableListOf<ParsedImportSheet>()
            val stage = File(context.cacheDir, "zip_import_extract_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                ZipInputStream(BufferedInputStream(FileInputStream(tempFile))).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile = SecurityCryptoUtils.sanitizeZipEntryPath(entry.name, stage)
                                ?: return ParsedImportFileResult(false, "Path Zip Slip terdeteksi: ${entry.name}", fileName, ImportFileFormat.ZIP)
                            outFile.parentFile?.mkdirs()
                            outFile.writeBytes(zis.readBytes())
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                val dbFolder = File(stage, "${StructuredBackupEngine.ROOT_ZIP_FOLDER}/database")
                if (dbFolder.exists()) {
                    listOf(
                        "customers.json" to "customers",
                        "units.json" to "units",
                        "transactions.json" to "transactions",
                        "payments.json" to "paymentProofs"
                    ).forEach { (fName, arrayKey) ->
                        val f = File(dbFolder, fName)
                        if (f.exists()) {
                            val decoded = BackupJsonMapper.decodePayload(f.readText(Charsets.UTF_8))
                            val arr = decoded.optJSONArray(arrayKey)
                            if (arr != null && arr.length() > 0) {
                                arrayToSheet(fName.removeSuffix(".json"), arr)?.let { sheets.add(it) }
                            }
                        }
                    }
                }
            } finally {
                runCatching { stage.deleteRecursively() }
            }
            return ParsedImportFileResult(
                isSuccess = sheets.isNotEmpty() || preview.isValid,
                errorMessage = if (sheets.isEmpty()) "Arsip ZIP terverifikasi, namun tidak ada tabel JSON terstruktur." else "",
                fileName = fileName,
                detectedFormat = ImportFileFormat.ZIP,
                sheets = sheets,
                isVerifiedBackupZip = true
            )
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun guessTargetFromNameOrHeaders(name: String, headers: List<String>): String {
        val joined = (listOf(name) + headers).joinToString(" ").lowercase(Locale.ROOT)
        return when {
            joined.contains("payment") || joined.contains("pembayaran") || joined.contains("bukti") -> "PEMBAYARAN"
            joined.contains("invoice") || joined.contains("transaksi") || joined.contains("sewa") -> "TRANSAKSI"
            joined.contains("kodeunit") || joined.contains("kode_unit") || joined.contains("unit") || joined.contains("freezer") || joined.contains("seri") -> "UNIT_FREEZER"
            else -> "PELANGGAN"
        }
    }

    private fun resolveUriFileName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }
}
