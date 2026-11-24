package com.example.sheets

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.repository.FreezerRepository
import com.example.util.DateCurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class ImportRowValidationResult(
    val rowNumber: Int,
    val rawValues: List<String>,
    val mappedValues: Map<String, String>,
    val isValid: Boolean,
    val isDuplicate: Boolean = false,
    val duplicateMatchLabel: String = "",
    val errorReasons: List<String> = emptyList()
)

data class SmartImportPreviewState(
    val fileName: String,
    val detectedFormat: ImportFileFormat,
    val detectedEncoding: String = "UTF-8",
    val detectedDelimiter: Char = ',',
    val target: SmartImportTarget,
    val availableSheets: List<String>,
    val selectedSheet: String,
    val headers: List<String>,
    val columnMapping: Map<Int, String?>,
    val missingRequiredFields: List<TargetFieldDef>,
    val duplicatePolicy: DuplicateImportPolicy = DuplicateImportPolicy.SKIP,
    val totalRows: Int,
    val validCount: Int,
    val invalidCount: Int,
    val duplicateCount: Int,
    val toUpdateCount: Int,
    val rowValidations: List<ImportRowValidationResult>,
    val canExecuteImport: Boolean
)

data class SmartImportExecutionReport(
    val fileName: String,
    val formatLabel: String,
    val targetLabel: String,
    val totalRows: Int,
    val successCount: Int,
    val updatedCount: Int,
    val duplicateSkippedCount: Int,
    val invalidCount: Int,
    val originalHeaders: List<String> = emptyList(),
    val errorRows: List<ImportRowValidationResult> = emptyList(),
    val wasInterrupted: Boolean = false,
    val message: String
)

object SmartImportExecutor {

    const val BATCH_SIZE = 50

    suspend fun buildPreview(
        parsedFile: ParsedImportFileResult,
        repository: FreezerRepository,
        targetOverride: SmartImportTarget? = null,
        selectedSheetOverride: String? = null,
        customColumnMapping: Map<Int, String?>? = null,
        duplicatePolicy: DuplicateImportPolicy = DuplicateImportPolicy.SKIP
    ): SmartImportPreviewState = withContext(Dispatchers.IO) {
        val activeSheet = parsedFile.sheets.firstOrNull {
            selectedSheetOverride != null && it.sheetName == selectedSheetOverride
        } ?: parsedFile.sheets.first()

        val target = targetOverride ?: runCatching {
            SmartImportTarget.valueOf(activeSheet.suggestedTarget)
        }.getOrDefault(SmartImportTarget.PELANGGAN)

        val mapping = customColumnMapping ?: SmartColumnMappingRegistry.recommendMapping(activeSheet.headers, target)
        val missingRequired = SmartColumnMappingRegistry.findMissingRequiredFields(mapping, target)

        val existingCustomers = repository.getAllCustomersOnce()
        val existingUnits = repository.getAllUnitsOnce()
        val existingTx = repository.getAllTransactionsOnce()

        val validations = validateRows(
            target = target,
            rows = activeSheet.rows,
            columnMapping = mapping,
            existingCustomers = existingCustomers,
            existingUnits = existingUnits,
            existingTx = existingTx
        )

        val validNonDup = validations.count { it.isValid && !it.isDuplicate }
        val invalid = validations.count { !it.isValid }
        val dup = validations.count { it.isValid && it.isDuplicate }
        val toUpdate = if (duplicatePolicy == DuplicateImportPolicy.UPDATE) dup else 0

        val actionableCount = when (duplicatePolicy) {
            DuplicateImportPolicy.SKIP -> validNonDup
            DuplicateImportPolicy.UPDATE, DuplicateImportPolicy.IMPORT_AS_NEW -> validNonDup + dup
        }

        SmartImportPreviewState(
            fileName = parsedFile.fileName,
            detectedFormat = parsedFile.detectedFormat,
            detectedEncoding = parsedFile.detectedEncoding,
            detectedDelimiter = parsedFile.detectedDelimiter,
            target = target,
            availableSheets = parsedFile.sheets.map { it.sheetName },
            selectedSheet = activeSheet.sheetName,
            headers = activeSheet.headers,
            columnMapping = mapping,
            missingRequiredFields = missingRequired,
            duplicatePolicy = duplicatePolicy,
            totalRows = activeSheet.rows.size,
            validCount = validNonDup,
            invalidCount = invalid,
            duplicateCount = dup,
            toUpdateCount = toUpdate,
            rowValidations = validations,
            canExecuteImport = missingRequired.isEmpty() && actionableCount > 0
        )
    }

    private fun validateRows(
        target: SmartImportTarget,
        rows: List<List<String>>,
        columnMapping: Map<Int, String?>,
        existingCustomers: List<CustomerEntity>,
        existingUnits: List<FreezerUnitEntity>,
        existingTx: List<RentalTransactionEntity>
    ): List<ImportRowValidationResult> {
        val seenCustCodes = existingCustomers.mapNotNull { it.kodePelanggan.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT) }.toMutableSet()
        val seenCustNiks = existingCustomers.mapNotNull { it.nik.takeIf(String::isNotBlank) }.toMutableSet()
        val seenCustPhones = existingCustomers.mapNotNull { normalizePhoneDigits(it.nomorHp).takeIf(String::isNotBlank) }.toMutableSet()

        val seenUnitCodes = existingUnits.map { it.kodeUnit.uppercase(Locale.ROOT) }.toMutableSet()
        val seenSerials = existingUnits.mapNotNull { it.nomorSeri.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT) }.toMutableSet()

        val seenInvoices = existingTx.map { it.nomorInvoice.uppercase(Locale.ROOT) }.toMutableSet()

        return rows.mapIndexed { idx, rawRow ->
            val rowNumber = idx + 2 // Excel 1-based row index including header
            val mapped = linkedMapOf<String, String>()
            columnMapping.forEach { (colIdx, fieldKey) ->
                if (!fieldKey.isNullOrBlank()) {
                    mapped[fieldKey] = rawRow.getOrElse(colIdx) { "" }.trim()
                }
            }

            val errors = mutableListOf<String>()
            var isDup = false
            var dupLabel = ""

            when (target) {
                SmartImportTarget.PELANGGAN -> {
                    val name = mapped["customer_name"].orEmpty()
                    val rawPhone = mapped["phone_number"].orEmpty()
                    val code = mapped["customer_code"].orEmpty()
                    val nik = mapped["nik"].orEmpty()
                    val joinDateRaw = mapped["join_date"].orEmpty()

                    if (name.isBlank()) errors.add("Nama pelanggan wajib diisi")
                    val normalizedPhone = normalizePhoneForValidation(rawPhone)
                    if (!DateCurrencyUtils.isValidPhone(normalizedPhone)) {
                        errors.add("Nomor HP/WhatsApp tidak valid: '$rawPhone'")
                    }
                    if (joinDateRaw.isNotBlank() && DateCurrencyUtils.parseFlexibleDateToIso(joinDateRaw) == null) {
                        errors.add("Format tanggal registrasi tidak dikenali: '$joinDateRaw'")
                    }
                    if (nik.isNotBlank() && !nik.all { it.isDigit() }) {
                        errors.add("NIK harus berupa angka")
                    }

                    if (errors.isEmpty()) {
                        val phoneKey = normalizePhoneDigits(normalizedPhone)
                        val codeKey = code.uppercase(Locale.ROOT)
                        when {
                            codeKey.isNotBlank() && codeKey in seenCustCodes -> {
                                isDup = true
                                dupLabel = "ID Pelanggan '$code'"
                            }
                            nik.isNotBlank() && nik in seenCustNiks -> {
                                isDup = true
                                dupLabel = "NIK '$nik'"
                            }
                            phoneKey.isNotBlank() && phoneKey in seenCustPhones -> {
                                isDup = true
                                dupLabel = "No. WA '$rawPhone'"
                            }
                        }
                        if (!isDup) {
                            if (codeKey.isNotBlank()) seenCustCodes.add(codeKey)
                            if (nik.isNotBlank()) seenCustNiks.add(nik)
                            if (phoneKey.isNotBlank()) seenCustPhones.add(phoneKey)
                        }
                    }
                }

                SmartImportTarget.UNIT_FREEZER -> {
                    val code = mapped["unit_code"].orEmpty()
                    val name = mapped["unit_name"].orEmpty()
                    val serial = mapped["serial_number"].orEmpty()
                    val capRaw = mapped["capacity_liter"].orEmpty()
                    val priceRaw = mapped["purchase_price"].orEmpty()
                    val rentRaw = mapped["monthly_installment"].orEmpty()
                    val dateRaw = mapped["purchase_date"].orEmpty()

                    if (code.isBlank()) errors.add("Kode unit wajib diisi")
                    if (name.isBlank()) errors.add("Nama / tipe freezer wajib diisi")
                    if (capRaw.isNotBlank()) {
                        val capDigits = capRaw.replace("L", "", ignoreCase = true).replace("liter", "", ignoreCase = true).trim()
                        val capVal = capDigits.toIntOrNull()
                        if (capVal == null || capVal < 0) errors.add("Kapasitas liter tidak valid: '$capRaw'")
                    }
                    if (priceRaw.isNotBlank() && DateCurrencyUtils.parseNominal(priceRaw) == null) {
                        errors.add("Harga beli tidak valid: '$priceRaw'")
                    }
                    if (rentRaw.isNotBlank() && DateCurrencyUtils.parseNominal(rentRaw) == null) {
                        errors.add("Nominal cicilan/sewa tidak valid: '$rentRaw'")
                    }
                    if (dateRaw.isNotBlank() && DateCurrencyUtils.parseFlexibleDateToIso(dateRaw) == null) {
                        errors.add("Tanggal beli tidak valid: '$dateRaw'")
                    }

                    if (errors.isEmpty()) {
                        val codeKey = code.uppercase(Locale.ROOT)
                        val serKey = serial.uppercase(Locale.ROOT)
                        when {
                            codeKey in seenUnitCodes -> {
                                isDup = true
                                dupLabel = "Kode Unit '$code'"
                            }
                            serKey.isNotBlank() && serKey in seenSerials -> {
                                isDup = true
                                dupLabel = "No. Seri '$serial'"
                            }
                        }
                        if (!isDup) {
                            seenUnitCodes.add(codeKey)
                            if (serKey.isNotBlank()) seenSerials.add(serKey)
                        }
                    }
                }

                SmartImportTarget.TRANSAKSI -> {
                    val invoice = mapped["invoice_no"].orEmpty()
                    val custName = mapped["customer_name"].orEmpty()
                    val unitCode = mapped["unit_code"].orEmpty()
                    val startRaw = mapped["start_date"].orEmpty()
                    val dueRaw = mapped["due_date"].orEmpty()
                    val rentRaw = mapped["rent_amount"].orEmpty()
                    val paidRaw = mapped["paid_amount"].orEmpty()

                    if (invoice.isBlank()) errors.add("Nomor invoice wajib diisi")
                    if (custName.isBlank()) errors.add("Nama pelanggan wajib diisi")
                    if (unitCode.isBlank()) {
                        errors.add("Kode unit wajib diisi")
                    } else if (existingUnits.none { it.kodeUnit.equals(unitCode, ignoreCase = true) }) {
                        errors.add("Unit '$unitCode' belum terdaftar di Data Unit")
                    }
                    if (startRaw.isNotBlank() && DateCurrencyUtils.parseFlexibleDateToIso(startRaw) == null) {
                        errors.add("Tanggal mulai sewa tidak valid: '$startRaw'")
                    }
                    if (dueRaw.isNotBlank() && DateCurrencyUtils.parseFlexibleDateToIso(dueRaw) == null) {
                        errors.add("Tanggal jatuh tempo tidak valid: '$dueRaw'")
                    }
                    if (rentRaw.isNotBlank() && (DateCurrencyUtils.parseNominal(rentRaw) ?: -1L) < 0L) {
                        errors.add("Nominal tagihan tidak valid: '$rentRaw'")
                    }
                    if (paidRaw.isNotBlank() && (DateCurrencyUtils.parseNominal(paidRaw) ?: -1L) < 0L) {
                        errors.add("Nominal pembayaran tidak valid: '$paidRaw'")
                    }

                    if (errors.isEmpty()) {
                        val invKey = invoice.uppercase(Locale.ROOT)
                        if (invKey in seenInvoices) {
                            isDup = true
                            dupLabel = "Invoice '$invoice'"
                        } else {
                            seenInvoices.add(invKey)
                        }
                    }
                }

                SmartImportTarget.PEMBAYARAN -> {
                    val invoice = mapped["invoice_no"].orEmpty()
                    val amountRaw = mapped["payment_amount"].orEmpty()
                    val payDateRaw = mapped["payment_date"].orEmpty()

                    if (invoice.isBlank()) {
                        errors.add("Nomor invoice wajib diisi")
                    } else if (existingTx.none { it.nomorInvoice.equals(invoice, ignoreCase = true) }) {
                        errors.add("Transaksi dengan invoice '$invoice' tidak ditemukan")
                    }
                    val amount = DateCurrencyUtils.parseNominal(amountRaw)
                    if (amount == null || amount <= 0L) {
                        errors.add("Nominal pembayaran harus lebih dari 0: '$amountRaw'")
                    }
                    if (payDateRaw.isNotBlank() && DateCurrencyUtils.parseFlexibleDateToIso(payDateRaw) == null) {
                        errors.add("Tanggal pembayaran tidak valid: '$payDateRaw'")
                    }
                }
            }

            ImportRowValidationResult(
                rowNumber = rowNumber,
                rawValues = rawRow,
                mappedValues = mapped,
                isValid = errors.isEmpty(),
                isDuplicate = isDup,
                duplicateMatchLabel = dupLabel,
                errorReasons = errors
            )
        }
    }

    suspend fun executeImport(
        preview: SmartImportPreviewState,
        repository: FreezerRepository,
        db: AppDatabase? = null,
        interruptAfterBatchForTest: Int? = null
    ): SmartImportExecutionReport = withContext(Dispatchers.IO) {
        if (preview.missingRequiredFields.isNotEmpty()) {
            val missingLabels = preview.missingRequiredFields.joinToString(", ") { it.label.replace(" *", "") }
            return@withContext SmartImportExecutionReport(
                fileName = preview.fileName,
                formatLabel = preview.detectedFormat.label,
                targetLabel = preview.target.label,
                totalRows = preview.totalRows,
                successCount = 0,
                updatedCount = 0,
                duplicateSkippedCount = 0,
                invalidCount = preview.invalidCount,
                originalHeaders = preview.headers,
                errorRows = preview.rowValidations.filter { !it.isValid },
                message = "Pemetaan kolom wajib belum lengkap: $missingLabels"
            )
        }

        var insertedCount = 0
        var updatedCount = 0
        var skippedDuplicates = 0
        val errorRows = preview.rowValidations.filter { !it.isValid }.toMutableList()
        var interrupted = false

        val validRows = preview.rowValidations.filter { it.isValid }
        val batches = validRows.chunked(BATCH_SIZE)

        try {
            batches.forEachIndexed { batchIdx, batch ->
                if (interruptAfterBatchForTest != null && batchIdx >= interruptAfterBatchForTest) {
                    throw InterruptedException("Proses import dihentikan sementara pada batch ${batchIdx + 1}")
                }
                val commitBlock: suspend () -> Unit = {
                    batch.forEach { row ->
                        if (row.isDuplicate && preview.duplicatePolicy == DuplicateImportPolicy.SKIP) {
                            skippedDuplicates++
                        } else {
                            when (preview.target) {
                                SmartImportTarget.PELANGGAN -> {
                                    val didUpdate = importCustomerRow(row, preview.duplicatePolicy, repository)
                                    if (didUpdate) updatedCount++ else insertedCount++
                                }
                                SmartImportTarget.UNIT_FREEZER -> {
                                    val didUpdate = importUnitRow(row, preview.duplicatePolicy, repository)
                                    if (didUpdate) updatedCount++ else insertedCount++
                                }
                                SmartImportTarget.TRANSAKSI -> {
                                    val didUpdate = importTransactionRow(row, preview.duplicatePolicy, repository)
                                    if (didUpdate) updatedCount++ else insertedCount++
                                }
                                SmartImportTarget.PEMBAYARAN -> {
                                    importPaymentRow(row, repository)
                                    insertedCount++
                                }
                            }
                        }
                    }
                }
                if (db != null) {
                    db.withTransaction { commitBlock() }
                } else {
                    commitBlock()
                }
            }
        } catch (e: Exception) {
            interrupted = true
            val committedRows = (batches.take(interruptAfterBatchForTest ?: batches.size)).flatten().size
            validRows.drop(committedRows).forEach { remaining ->
                errorRows.add(
                    remaining.copy(
                        isValid = false,
                        errorReasons = listOf("Belum terproses karena import terhenti: ${e.localizedMessage ?: "Dibatalkan"}")
                    )
                )
            }
        }

        val totalSucceeded = insertedCount + updatedCount
        repository.logBackupAuditEvent(
            actionType = "IMPORT_DATA",
            status = if (interrupted) "SEBAGIAN" else "SUKSES",
            fileName = preview.fileName,
            recordCount = totalSucceeded,
            detailMessage = "Smart Import (${preview.detectedFormat.label} -> ${preview.target.label}): +$insertedCount baru, $updatedCount diperbarui, $skippedDuplicates duplikat dilewati, ${errorRows.size} invalid."
        )

        val msg = buildString {
            if (interrupted) {
                append("Import terhenti di tengah proses: ")
            } else {
                append("Smart Import selesai: ")
            }
            append("$insertedCount baru, $updatedCount diperbarui, $skippedDuplicates dilewati, ${errorRows.size} error.")
        }

        SmartImportExecutionReport(
            fileName = preview.fileName,
            formatLabel = preview.detectedFormat.label,
            targetLabel = preview.target.label,
            totalRows = preview.totalRows,
            successCount = insertedCount,
            updatedCount = updatedCount,
            duplicateSkippedCount = skippedDuplicates,
            invalidCount = errorRows.size,
            originalHeaders = preview.headers,
            errorRows = errorRows,
            wasInterrupted = interrupted,
            message = msg
        )
    }

    private suspend fun importCustomerRow(
        row: ImportRowValidationResult,
        policy: DuplicateImportPolicy,
        repository: FreezerRepository
    ): Boolean {
        val m = row.mappedValues
        val code = m["customer_code"].orEmpty()
        val name = m["customer_name"].orEmpty()
        val phone = normalizePhoneForValidation(m["phone_number"].orEmpty())
        val nik = m["nik"].orEmpty()
        val address = m["address"].orEmpty().ifBlank { "-" }
        val usageLoc = m["usage_location"].orEmpty()
        val joinDate = m["join_date"].orEmpty().takeIf(String::isNotBlank)
            ?.let { DateCurrencyUtils.parseFlexibleDateToIso(it) }
            ?: DateCurrencyUtils.todayIso()
        val status = m["status"].orEmpty().ifBlank { "Pelanggan Aktif" }

        val existing = repository.getAllCustomersOnce().firstOrNull {
            (code.isNotBlank() && it.kodePelanggan.equals(code, true)) ||
                (nik.isNotBlank() && it.nik == nik) ||
                normalizePhoneDigits(it.nomorHp) == normalizePhoneDigits(phone)
        }

        if (existing != null && policy == DuplicateImportPolicy.UPDATE) {
            repository.saveCustomer(
                existing.copy(
                    namaLengkap = name.ifBlank { existing.namaLengkap },
                    nomorHp = phone.ifBlank { existing.nomorHp },
                    nik = nik.ifBlank { existing.nik },
                    alamatLengkap = if (address != "-") address else existing.alamatLengkap,
                    lokasiPenggunaanDefault = usageLoc.ifBlank { existing.lokasiPenggunaanDefault },
                    tanggalRegistrasi = joinDate,
                    statusPelanggan = status
                )
            )
            return true
        }

        val finalCode = if (existing != null && policy == DuplicateImportPolicy.IMPORT_AS_NEW) {
            repository.generateUniqueCustomerCode()
        } else {
            code
        }

        repository.saveCustomer(
            CustomerEntity(
                kodePelanggan = finalCode,
                namaLengkap = name,
                nik = if (existing != null && policy == DuplicateImportPolicy.IMPORT_AS_NEW) "" else nik,
                nomorHp = phone,
                alamatLengkap = address,
                jenisKelamin = "Laki-laki",
                lokasiPenggunaanDefault = usageLoc,
                tanggalRegistrasi = joinDate,
                statusPelanggan = status
            )
        )
        return false
    }

    private suspend fun importUnitRow(
        row: ImportRowValidationResult,
        policy: DuplicateImportPolicy,
        repository: FreezerRepository
    ): Boolean {
        val m = row.mappedValues
        val code = m["unit_code"].orEmpty()
        val name = m["unit_name"].orEmpty()
        val type = m["unit_type"].orEmpty().ifBlank { "Chest Freezer" }
        val brand = m["brand"].orEmpty().ifBlank { "GEA" }
        val model = m["model"].orEmpty()
        val serial = m["serial_number"].orEmpty()
        val capacity = m["capacity_liter"].orEmpty()
            .replace("L", "", ignoreCase = true)
            .replace("liter", "", ignoreCase = true)
            .trim()
            .toIntOrNull() ?: 200
        val status = m["unit_status"].orEmpty().ifBlank { "Tersedia" }
        val condition = m["condition"].orEmpty().ifBlank { "Baik & Siap Pakai" }
        val location = m["location"].orEmpty().ifBlank { "Gudang Solo" }
        val price = DateCurrencyUtils.parseNominal(m["purchase_price"].orEmpty()) ?: 0L
        val installment = DateCurrencyUtils.parseNominal(m["monthly_installment"].orEmpty()) ?: 200000L
        val purchaseDate = m["purchase_date"].orEmpty().takeIf(String::isNotBlank)
            ?.let { DateCurrencyUtils.parseFlexibleDateToIso(it) }
            ?: DateCurrencyUtils.todayIso()

        val existing = repository.getAllUnitsOnce().firstOrNull {
            it.kodeUnit.equals(code, true) ||
                (serial.isNotBlank() && it.nomorSeri.equals(serial, true))
        }

        if (existing != null && policy == DuplicateImportPolicy.UPDATE) {
            repository.saveUnit(
                existing.copy(
                    namaUnit = name.ifBlank { existing.namaUnit },
                    jenisUnit = type,
                    merek = brand,
                    model = model.ifBlank { existing.model },
                    nomorSeri = serial.ifBlank { existing.nomorSeri },
                    kapasitasLiter = capacity,
                    statusUnit = status,
                    kondisiUnit = condition,
                    lokasiUnit = location,
                    hargaPembelian = if (price > 0L) price else existing.hargaPembelian,
                    cicilanPerBulan = if (installment > 0L) installment else existing.cicilanPerBulan,
                    tanggalPembelian = purchaseDate
                )
            )
            return true
        }

        val finalCode = if (existing != null && policy == DuplicateImportPolicy.IMPORT_AS_NEW) {
            repository.generateNextUnitCode()
        } else {
            code
        }

        repository.saveUnit(
            FreezerUnitEntity(
                kodeUnit = finalCode,
                namaUnit = name,
                jenisUnit = type,
                owner = "SEWAin Aja",
                hargaPembelian = price,
                kapasitasLiter = capacity,
                cicilanPerBulan = installment,
                merek = brand,
                model = model,
                nomorSeri = if (existing != null && policy == DuplicateImportPolicy.IMPORT_AS_NEW) "$serial-DUP-${System.currentTimeMillis() % 1000}" else serial,
                statusUnit = status,
                lokasiUnit = location,
                tanggalPembelian = purchaseDate,
                kondisiUnit = condition
            )
        )
        return false
    }

    private suspend fun importTransactionRow(
        row: ImportRowValidationResult,
        policy: DuplicateImportPolicy,
        repository: FreezerRepository
    ): Boolean {
        val m = row.mappedValues
        val invoiceRaw = m["invoice_no"].orEmpty()
        val custName = m["customer_name"].orEmpty()
        val custPhone = normalizePhoneForValidation(m["customer_phone"].orEmpty())
        val unitCode = m["unit_code"].orEmpty()
        val program = m["program"].orEmpty().ifBlank { "Sewa Reguler" }
        val startIso = m["start_date"].orEmpty().takeIf(String::isNotBlank)
            ?.let { DateCurrencyUtils.parseFlexibleDateToIso(it) }
            ?: DateCurrencyUtils.todayIso()
        val durasi = m["duration_days"].orEmpty().toIntOrNull()?.coerceAtLeast(1) ?: 30
        val dueIso = m["due_date"].orEmpty().takeIf(String::isNotBlank)
            ?.let { DateCurrencyUtils.parseFlexibleDateToIso(it) }
            ?: DateCurrencyUtils.addDaysToIsoDate(startIso, durasi)
        val rentAmount = DateCurrencyUtils.parseNominal(m["rent_amount"].orEmpty()) ?: 200000L
        val paidAmount = DateCurrencyUtils.parseNominal(m["paid_amount"].orEmpty()) ?: 0L
        val method = m["payment_method"].orEmpty().ifBlank { "Transfer BCA" }
        val payStatus = m["payment_status"].orEmpty().ifBlank {
            if (paidAmount >= rentAmount && rentAmount > 0L) "Lunas" else if (paidAmount > 0L) "Dibayar Sebagian" else "Belum Bayar"
        }
        val rentStatus = m["rental_status"].orEmpty().ifBlank { "Dalam Penyewaan" }

        val unit = repository.getAllUnitsOnce().first { it.kodeUnit.equals(unitCode, true) }
        val allCustomers = repository.getAllCustomersOnce()
        val matchedCustomer = allCustomers.firstOrNull {
            it.namaLengkap.equals(custName, true) ||
                (custPhone.isNotBlank() && normalizePhoneDigits(it.nomorHp) == normalizePhoneDigits(custPhone))
        }
        val customerId = matchedCustomer?.id ?: repository.saveCustomer(
            CustomerEntity(
                namaLengkap = custName,
                nik = "",
                nomorHp = custPhone.ifBlank { "08000000000" },
                alamatLengkap = "-",
                jenisKelamin = "Laki-laki",
                tanggalRegistrasi = startIso
            )
        )

        val existingTx = repository.getAllTransactionsOnce().firstOrNull { it.nomorInvoice.equals(invoiceRaw, true) }
        if (existingTx != null && policy == DuplicateImportPolicy.UPDATE) {
            repository.saveTransaction(
                existingTx.copy(
                    hargaSewa = rentAmount,
                    totalTagihan = rentAmount,
                    nominalPembayaran = paidAmount,
                    tanggalMulaiSewa = startIso,
                    tanggalAkhirSewa = dueIso,
                    tanggalJatuhTempo = dueIso,
                    statusPembayaran = payStatus,
                    statusPenyewaan = rentStatus,
                    metodePembayaran = method,
                    jenisProgram = program
                )
            )
            return true
        }

        val finalInvoice = if (existingTx != null && policy == DuplicateImportPolicy.IMPORT_AS_NEW) {
            repository.generateUniqueInvoiceNumber()
        } else {
            invoiceRaw
        }

        repository.saveTransaction(
            RentalTransactionEntity(
                nomorInvoice = finalInvoice,
                tanggalPemesanan = startIso,
                customerId = customerId,
                namaPelangganSnapshot = custName,
                unitId = unit.id,
                namaUnitSnapshot = unit.namaUnit,
                kodeUnitSnapshot = unit.kodeUnit,
                hargaSewa = rentAmount,
                durasiHari = durasi,
                tanggalMulaiSewa = startIso,
                tanggalAkhirSewa = dueIso,
                tanggalJatuhTempo = dueIso,
                nominalPembayaran = paidAmount,
                totalTagihan = rentAmount,
                statusPembayaran = payStatus,
                statusPenyewaan = rentStatus,
                metodePembayaran = method,
                jenisProgram = program
            )
        )
        return false
    }

    private suspend fun importPaymentRow(
        row: ImportRowValidationResult,
        repository: FreezerRepository
    ) {
        val m = row.mappedValues
        val invoice = m["invoice_no"].orEmpty()
        val amount = DateCurrencyUtils.parseNominal(m["payment_amount"].orEmpty()) ?: 0L
        val payDate = m["payment_date"].orEmpty().takeIf(String::isNotBlank)
            ?.let { DateCurrencyUtils.parseFlexibleDateToIso(it) }
            ?: DateCurrencyUtils.todayIso()
        val method = m["payment_method"].orEmpty().ifBlank { "Transfer BCA" }
        val sender = m["sender_name"].orEmpty()
        val note = m["note"].orEmpty().ifBlank { "Diimpor via Smart Import" }

        val tx = repository.getAllTransactionsOnce().first { it.nomorInvoice.equals(invoice, true) }
        repository.savePaymentProof(
            PaymentProofEntity(
                transactionId = tx.id,
                tanggalPembayaran = payDate,
                nominalPembayaran = amount,
                metodePembayaran = method,
                namaPengirim = sender,
                catatanPembayaran = note
            ),
            recalculateTransactionPayment = true
        )
    }

    private fun normalizePhoneForValidation(raw: String): String {
        val stripped = raw.replace(" ", "").replace("-", "").replace(".", "").trim()
        return if (stripped.startsWith("8") && stripped.length in 8..13 && stripped.all { it.isDigit() }) {
            "0$stripped"
        } else {
            stripped
        }
    }

    private fun normalizePhoneDigits(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("62") && digits.length > 4 -> "0" + digits.drop(2)
            digits.startsWith("8") && digits.length >= 8 -> "0$digits"
            else -> digits
        }
    }
}
