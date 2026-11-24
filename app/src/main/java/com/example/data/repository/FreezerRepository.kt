package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.AppPermissionEntity
import com.example.data.local.BackupAuditLogEntity
import com.example.data.local.CustomerDocumentEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerLocationEntity
import com.example.data.local.CustomerPhotoEntity
import com.example.data.local.CustomerTimelineEventEntity
import com.example.data.local.DEFAULT_PERJANJIAN_SEWA
import com.example.data.local.DriveCloudFileMapEntity
import com.example.data.local.DriveDeletionTombstoneEntity
import com.example.data.local.DriveLocalSyncQueueEntity
import com.example.data.local.DrivePhotoMetadataEntity
import com.example.data.local.FormDraftEntity
import com.example.data.local.FreezerDao
import com.example.data.local.FreezerReturnEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.FreezerUnitMaintenanceEntity
import com.example.data.local.FreezerUnitWithMaintenanceHistory
import com.example.data.local.LocalBackupEntity
import com.example.data.local.PaymentMethodEntity
import com.example.data.local.PaymentProofEntity
import com.example.data.local.RentalExtensionHistoryEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.SyncLogEntity
import com.example.data.local.SyncTechnicalLogEntity
import com.example.data.local.TransactionDocumentEntity
import com.example.data.local.TransactionOfficerAuditLogEntity
import com.example.data.local.TransactionSignatureEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitPaymentAllocationEntity
import com.example.data.local.UnitRentalPriceEntity
import com.example.drive.GoogleDriveMirrorSyncEngine
import com.example.drive.MirrorOutboxCodec
import com.example.util.DateCurrencyUtils
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

data class MultiUnitValidationResult(
    val isValid: Boolean,
    val errorCode: String = "",
    val errorMessage: String = ""
)

data class MultiUnitBillingSummary(
    val totalUnits: Int,
    val totalHargaSewaUnits: Long,
    val ongkir: Long,
    val deposit: Long,
    val diskon: Long,
    val totalTagihan: Long,
    val totalPembayaran: Long,
    val sisaTagihan: Long,
    val unitBreakdowns: List<TransactionUnitItemEntity>
)

data class ContractHierarchyNode(
    val transaction: RentalTransactionEntity,
    val contractNumberDisplay: String,
    val unitItems: List<TransactionUnitItemEntity>,
    val paymentProofs: List<PaymentProofEntity>,
    val unitAllocations: List<UnitPaymentAllocationEntity>
)

data class CustomerSmartSummary(
    val customer: CustomerEntity,
    val totalUnitAktif: Int,
    val totalKontrakAktif: Int,
    val totalKontrakKeseluruhan: Int,
    val totalTagihanBulanan: Long,
    val totalTunggakan: Long,
    val pembayaranBulanIni: Long,
    val statusPelangganLabel: String,
    val contracts: List<ContractHierarchyNode>
)

class FreezerRepository(private val dao: FreezerDao) {

    private var appContext: Context? = null

    constructor(context: Context) : this(AppDatabase.getInstance(context).freezerDao()) {
        appContext = context.applicationContext
    }

    @Volatile
    var mirrorSyncEngine: GoogleDriveMirrorSyncEngine? = null

    fun attachMirrorSyncEngine(engine: GoogleDriveMirrorSyncEngine?) {
        mirrorSyncEngine = engine
    }

    private fun currentDatasetId(): String {
        mirrorSyncEngine?.let { return it.getCurrentDatasetId() }
        val ctx = appContext ?: return "dataset_initial"
        val prefs = ctx.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("dataset_id", null)?.takeIf { it.isNotBlank() } ?: "dataset_initial"
    }

    private fun currentDatasetGenerationId(): String {
        mirrorSyncEngine?.let { return it.getCurrentDatasetGenerationId() }
        val ctx = appContext ?: return "GENERATION_001"
        val prefs = ctx.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)
        val seq = prefs.getInt("dataset_generation_seq", 1).coerceAtLeast(1)
        return String.format(Locale.US, "GENERATION_%03d", seq)
    }

    private fun currentServerTimeRef(): Long =
        mirrorSyncEngine?.driveClient?.currentServerReferenceTimeMillis() ?: 0L

    private fun prepareUnitForSave(
        incoming: FreezerUnitEntity,
        existing: FreezerUnitEntity?,
        now: Long = System.currentTimeMillis()
    ): FreezerUnitEntity {
        val resolvedUuid = MirrorOutboxCodec.ensureValidUuidV4(
            existing?.uuid?.ifBlank { incoming.uuid } ?: incoming.uuid
        )
        val resolvedCreatedAt = when {
            existing != null && existing.createdAtMillis > 0L -> existing.createdAtMillis
            incoming.createdAtMillis > 0L -> incoming.createdAtMillis
            else -> now
        }
        val nextVersion = if (existing != null) {
            maxOf(incoming.version, existing.version) + 1L
        } else {
            incoming.version.coerceAtLeast(1L)
        }
        return incoming.copy(
            uuid = resolvedUuid,
            version = nextVersion,
            createdAtMillis = resolvedCreatedAt,
            updatedAtMillis = now,
            isDeleted = false,
            driveId = incoming.driveId.ifBlank { existing?.driveId.orEmpty() }
        )
    }

    private fun prepareCustomerForSave(
        incoming: CustomerEntity,
        existing: CustomerEntity?,
        now: Long = System.currentTimeMillis()
    ): CustomerEntity {
        val resolvedUuid = MirrorOutboxCodec.ensureValidUuidV4(
            existing?.uuid?.ifBlank { incoming.uuid } ?: incoming.uuid
        )
        val resolvedCreatedAt = when {
            existing != null && existing.createdAtMillis > 0L -> existing.createdAtMillis
            incoming.createdAtMillis > 0L -> incoming.createdAtMillis
            else -> now
        }
        val nextVersion = if (existing != null) {
            maxOf(incoming.version, existing.version) + 1L
        } else {
            incoming.version.coerceAtLeast(1L)
        }
        return incoming.copy(
            uuid = resolvedUuid,
            version = nextVersion,
            createdAtMillis = resolvedCreatedAt,
            updatedAtMillis = now,
            isDeleted = false,
            driveId = incoming.driveId.ifBlank { existing?.driveId.orEmpty() }
        )
    }

    private fun prepareTransactionForSave(
        incoming: RentalTransactionEntity,
        existing: RentalTransactionEntity?,
        now: Long = System.currentTimeMillis(),
        allowOfficerCorrection: Boolean = false
    ): RentalTransactionEntity {
        val resolvedUuid = MirrorOutboxCodec.ensureValidUuidV4(
            existing?.uuid?.ifBlank { incoming.uuid } ?: incoming.uuid
        )
        val resolvedCreatedAt = when {
            existing != null && existing.createdAtMillis > 0L -> existing.createdAtMillis
            incoming.createdAtMillis > 0L -> incoming.createdAtMillis
            else -> now
        }
        val nextVersion = if (existing != null) {
            maxOf(incoming.version, existing.version) + 1L
        } else {
            incoming.version.coerceAtLeast(1L)
        }
        // Permanent Officer Identity & Signature Snapshot protection:
        // Once saved with an officer identity/signature, regular edits NEVER overwrite original officer data.
        val existingHasLockedOfficer = existing != null && (
            existing.officerSnapshotLocked ||
                existing.namaPetugas.isNotBlank() ||
                existing.tandaTanganPetugasUri.isNotBlank()
            )
        val shouldPreserveExistingOfficer = existingHasLockedOfficer && !allowOfficerCorrection
        val resolvedOfficerName = if (shouldPreserveExistingOfficer) {
            existing!!.namaPetugas.ifBlank { incoming.namaPetugas }
        } else {
            incoming.namaPetugas
        }
        val resolvedOfficerSig = if (shouldPreserveExistingOfficer) {
            existing!!.tandaTanganPetugasUri.ifBlank { incoming.tandaTanganPetugasUri }
        } else {
            incoming.tandaTanganPetugasUri
        }
        val resolvedOfficerAccountId = if (shouldPreserveExistingOfficer) {
            existing!!.officerAccountId.ifBlank { incoming.officerAccountId }
        } else {
            incoming.officerAccountId
        }
        val resolvedOfficerSignedAt = when {
            shouldPreserveExistingOfficer -> existing!!.officerSignedAt.ifBlank { incoming.officerSignedAt }
            incoming.officerSignedAt.isNotBlank() -> incoming.officerSignedAt
            resolvedOfficerName.isNotBlank() || resolvedOfficerSig.isNotBlank() -> DateCurrencyUtils.nowDateTimeDisplay()
            else -> ""
        }
        val resolvedOfficerLocked = shouldPreserveExistingOfficer ||
            incoming.officerSnapshotLocked ||
            resolvedOfficerName.isNotBlank() ||
            resolvedOfficerSig.isNotBlank()

        return incoming.copy(
            uuid = resolvedUuid,
            version = nextVersion,
            createdAtMillis = resolvedCreatedAt,
            updatedAtMillis = now,
            isDeleted = false,
            driveId = incoming.driveId.ifBlank { existing?.driveId.orEmpty() },
            namaPetugas = resolvedOfficerName,
            tandaTanganPetugasUri = resolvedOfficerSig,
            officerAccountId = resolvedOfficerAccountId,
            officerSnapshotLocked = resolvedOfficerLocked,
            officerSignedAt = resolvedOfficerSignedAt
        )
    }

    private fun preparePaymentProofForSave(
        incoming: PaymentProofEntity,
        existing: PaymentProofEntity?,
        now: Long = System.currentTimeMillis()
    ): PaymentProofEntity {
        val resolvedUuid = MirrorOutboxCodec.ensureValidUuidV4(
            existing?.uuid?.ifBlank { incoming.uuid } ?: incoming.uuid
        )
        val resolvedCreatedAt = when {
            existing != null && existing.createdAtMillis > 0L -> existing.createdAtMillis
            incoming.createdAtMillis > 0L -> incoming.createdAtMillis
            else -> now
        }
        val nextVersion = if (existing != null) {
            maxOf(incoming.version, existing.version) + 1L
        } else {
            incoming.version.coerceAtLeast(1L)
        }
        return incoming.copy(
            uuid = resolvedUuid,
            version = nextVersion,
            createdAtMillis = resolvedCreatedAt,
            updatedAtMillis = now,
            isDeleted = false,
            driveId = incoming.driveId.ifBlank { existing?.driveId.orEmpty() }
        )
    }

    val allUnits: Flow<List<FreezerUnitEntity>> = dao.getAllUnits()
    val allCustomers: Flow<List<CustomerEntity>> = dao.getAllCustomers()
    val allTransactions: Flow<List<RentalTransactionEntity>> = dao.getAllTransactions()
    val allPaymentProofs: Flow<List<PaymentProofEntity>> = dao.getAllPaymentProofs()
    val allPaymentMethods: Flow<List<PaymentMethodEntity>> = dao.getAllPaymentMethods()
    val activePaymentMethods: Flow<List<PaymentMethodEntity>> = dao.getActivePaymentMethods()
    val allExtensions: Flow<List<RentalExtensionHistoryEntity>> = dao.getAllExtensions()
    val allReturns: Flow<List<FreezerReturnEntity>> = dao.getAllReturns()
    val syncLogs: Flow<List<SyncLogEntity>> = dao.getSyncLogs()
    val allCustomerPhotos: Flow<List<CustomerPhotoEntity>> = dao.getAllCustomerPhotos()
    val allCustomerDocuments: Flow<List<CustomerDocumentEntity>> = dao.getAllCustomerDocuments()
    val allTransactionDocuments: Flow<List<TransactionDocumentEntity>> = dao.getAllTransactionDocuments()
    val allCustomerLocations: Flow<List<CustomerLocationEntity>> = dao.getAllCustomerLocations()
    val allLocalBackups: Flow<List<LocalBackupEntity>> = dao.getAllLocalBackups()
    val allAppPermissions: Flow<List<AppPermissionEntity>> = dao.getAllAppPermissions()
    val allTransactionUnitItems: Flow<List<TransactionUnitItemEntity>> = dao.getAllTransactionUnitItems()
    val allTransactionSignatures: Flow<List<TransactionSignatureEntity>> = dao.getAllTransactionSignatures()
    val allUnitPaymentAllocations: Flow<List<UnitPaymentAllocationEntity>> = dao.getAllUnitPaymentAllocations()
    val allCustomerTimelineEvents: Flow<List<CustomerTimelineEventEntity>> = dao.getAllCustomerTimelineEvents()
    val allBackupAuditLogs: Flow<List<BackupAuditLogEntity>> = dao.getAllBackupAuditLogs()
    val allDriveCloudFileMappings: Flow<List<DriveCloudFileMapEntity>> = dao.observeAllDriveCloudFileMappings()
    val allDriveLocalSyncQueue: Flow<List<DriveLocalSyncQueueEntity>> = dao.observeAllDriveLocalSyncQueue()
    val allDriveDeletionTombstones: Flow<List<DriveDeletionTombstoneEntity>> = dao.observeAllDriveDeletionTombstones()
    val allUnitsWithMaintenanceHistory: Flow<List<FreezerUnitWithMaintenanceHistory>> = dao.getAllUnitsWithMaintenanceHistory()
    val allFormDrafts: Flow<List<FormDraftEntity>> = dao.observeAllFormDrafts()
    val allUnitRentalPrices: Flow<List<UnitRentalPriceEntity>> = dao.observeAllUnitRentalPrices()
    val allTransactionOfficerAuditLogs: Flow<List<TransactionOfficerAuditLogEntity>> = dao.observeAllTransactionOfficerAuditLogs()

    fun getTransactionsForUnit(unitId: Long): Flow<List<RentalTransactionEntity>> =
        dao.getTransactionsForUnit(unitId)

    fun getTransactionsForCustomer(customerId: Long): Flow<List<RentalTransactionEntity>> =
        dao.getTransactionsForCustomer(customerId)

    fun getCustomerPhotos(customerId: Long): Flow<List<CustomerPhotoEntity>> =
        dao.getCustomerPhotos(customerId)

    fun getCustomerPhotosByCategory(customerId: Long, category: String): Flow<List<CustomerPhotoEntity>> =
        dao.getCustomerPhotosByCategory(customerId, category)

    suspend fun getCustomerPhotosOnce(customerId: Long): List<CustomerPhotoEntity> =
        dao.getCustomerPhotosOnce(customerId)

    suspend fun countCustomerPhotosInCategory(customerId: Long, category: String): Int =
        dao.countCustomerPhotosInCategory(customerId, category)

    suspend fun saveCustomerPhoto(photo: CustomerPhotoEntity): Long =
        dao.insertCustomerPhoto(photo)

    suspend fun deleteCustomerPhoto(photo: CustomerPhotoEntity) =
        dao.deleteCustomerPhoto(photo)

    // --- v11 SUPPORTING TABLES REPOSITORY METHODS ---
    suspend fun getAllCustomerDocumentsOnce(): List<CustomerDocumentEntity> =
        dao.getAllCustomerDocumentsOnce()

    suspend fun getCustomerDocumentsByCustomerOnce(customerId: Long): List<CustomerDocumentEntity> =
        dao.getCustomerDocumentsByCustomerOnce(customerId)

    suspend fun saveCustomerDocument(doc: CustomerDocumentEntity): Long =
        if (doc.id == 0L) dao.insertCustomerDocument(doc) else { dao.updateCustomerDocument(doc); doc.id }

    suspend fun deleteCustomerDocument(doc: CustomerDocumentEntity) =
        dao.deleteCustomerDocument(doc)

    suspend fun getAllTransactionDocumentsOnce(): List<TransactionDocumentEntity> =
        dao.getAllTransactionDocumentsOnce()

    suspend fun getTransactionDocumentsByTxOnce(transactionId: Long): List<TransactionDocumentEntity> =
        dao.getTransactionDocumentsByTxOnce(transactionId)

    suspend fun saveTransactionDocument(doc: TransactionDocumentEntity): Long =
        if (doc.id == 0L) dao.insertTransactionDocument(doc) else { dao.updateTransactionDocument(doc); doc.id }

    suspend fun deleteTransactionDocument(doc: TransactionDocumentEntity) =
        dao.deleteTransactionDocument(doc)

    suspend fun getAllCustomerLocationsOnce(): List<CustomerLocationEntity> =
        dao.getAllCustomerLocationsOnce()

    suspend fun getLatestCustomerLocation(customerId: Long): CustomerLocationEntity? =
        dao.getLatestCustomerLocation(customerId)

    suspend fun saveCustomerLocation(loc: CustomerLocationEntity): Long {
        val now = DateCurrencyUtils.nowDateTimeDisplay()
        val stamped = loc.copy(
            createdAt = loc.createdAt.ifBlank { now },
            updatedAt = now
        )
        return if (stamped.id == 0L) {
            dao.insertCustomerLocation(stamped)
        } else {
            dao.updateCustomerLocation(stamped)
            stamped.id
        }
    }

    suspend fun getAllLocalBackupsOnce(): List<LocalBackupEntity> =
        dao.getAllLocalBackupsOnce()

    suspend fun saveLocalBackup(backup: LocalBackupEntity): Long =
        if (backup.id == 0L) dao.insertLocalBackup(backup) else { dao.updateLocalBackup(backup); backup.id }

    suspend fun deleteLocalBackup(backup: LocalBackupEntity) =
        dao.deleteLocalBackup(backup)

    suspend fun getAllAppPermissionsOnce(): List<AppPermissionEntity> =
        dao.getAllAppPermissionsOnce()

    suspend fun upsertAppPermission(permissionName: String, permissionStatus: String): Long {
        val now = DateCurrencyUtils.nowDateTimeDisplay()
        val existing = dao.getAppPermissionByName(permissionName)
        return if (existing == null) {
            dao.insertAppPermission(
                AppPermissionEntity(
                    permissionName = permissionName,
                    permissionStatus = permissionStatus,
                    lastChecked = now,
                    updatedAt = now
                )
            )
        } else {
            dao.updateAppPermission(
                existing.copy(
                    permissionStatus = permissionStatus,
                    lastChecked = now,
                    updatedAt = now
                )
            )
            existing.id
        }
    }

    fun getPaymentProofsForTransaction(transactionId: Long): Flow<List<PaymentProofEntity>> =
        dao.getPaymentProofsForTransaction(transactionId)

    suspend fun getPaymentProofsForTransactionOnce(transactionId: Long): List<PaymentProofEntity> =
        dao.getPaymentProofsForTransactionOnce(transactionId)

    suspend fun getAllUnitsOnce(): List<FreezerUnitEntity> = dao.getAllUnitsOnce()
    suspend fun getStrictlyAvailableUnitsOnce(): List<FreezerUnitEntity> = dao.getStrictlyAvailableUnitsOnce()
    suspend fun getAllCustomersOnce(): List<CustomerEntity> = dao.getAllCustomersOnce()
    suspend fun getAllTransactionsOnce(): List<RentalTransactionEntity> = dao.getAllTransactionsOnce()
    suspend fun getAllPaymentMethodsOnce(): List<PaymentMethodEntity> = dao.getAllPaymentMethodsOnce()

    suspend fun getUnitById(id: Long): FreezerUnitEntity? = dao.getUnitById(id)
    suspend fun getCustomerById(id: Long): CustomerEntity? = dao.getCustomerById(id)
    suspend fun findCustomerByNik(nik: String): CustomerEntity? =
        if (nik.trim().isEmpty()) null else dao.findCustomerByNik(nik.trim())
    suspend fun getTransactionById(id: Long): RentalTransactionEntity? = dao.getTransactionById(id)

    // --- 1. FITUR BUKTI PEMBAYARAN ---
    suspend fun savePaymentProof(
        proof: PaymentProofEntity,
        recalculateTransactionPayment: Boolean = true
    ): Long {
        val existingProof = if (proof.id > 0L) {
            dao.getPaymentProofById(proof.id)
        } else null
        val isNew = existingProof == null
        val preparedRaw = preparePaymentProofForSave(
            incoming = proof.copy(
                waktuUpload = proof.waktuUpload.ifBlank { DateCurrencyUtils.nowDateTimeDisplay() }
            ),
            existing = existingProof
        )
        val changedFields = MirrorOutboxCodec.computePaymentProofChangedFieldsJson(existingProof, preparedRaw)
        val savedId = dao.savePaymentProofWithOutboxAtomic(
            proof = preparedRaw,
            isNew = isNew,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = changedFields,
            serverTimestampMillis = currentServerTimeRef()
        )
        if (recalculateTransactionPayment && preparedRaw.transactionId > 0L) {
            synchronizeTransactionPaymentFromProofs(preparedRaw.transactionId, preparedRaw.metodePembayaran)
        }
        val finalProof = dao.getPaymentProofById(savedId) ?: preparedRaw.copy(id = savedId)
        mirrorSyncEngine?.onPaymentOutboxCommitted(
            proof = finalProof,
            isNew = isNew,
            previousPhotoUri = existingProof?.fotoBuktiUri.orEmpty()
        )
        return savedId
    }

    suspend fun deletePaymentProof(
        proof: PaymentProofEntity,
        recalculateTransactionPayment: Boolean = true
    ) {
        val freshProof = dao.getPaymentProofByIdIncludingDeleted(proof.id) ?: proof
        dao.deletePaymentProofWithOutboxAtomic(
            proof = freshProof,
            datasetGenerationId = currentDatasetGenerationId(),
            serverTimestampMillis = currentServerTimeRef()
        )
        if (recalculateTransactionPayment && freshProof.transactionId > 0L) {
            synchronizeTransactionPaymentFromProofs(freshProof.transactionId, null)
        }
        mirrorSyncEngine?.onOutboxCommitted()
    }

    private suspend fun synchronizeTransactionPaymentFromProofs(
        transactionId: Long,
        latestMethod: String?
    ) {
        val tx = dao.getTransactionById(transactionId) ?: return
        val proofs = dao.getPaymentProofsForTransactionOnce(transactionId)
        val totalFromProofs = proofs.sumOf { it.nominalPembayaran }
        val newNominal = if (proofs.isNotEmpty()) totalFromProofs else tx.nominalPembayaran
        val daysToDue = DateCurrencyUtils.daysFromToday(tx.tanggalJatuhTempo)
        val newStatus = when {
            tx.totalTagihan > 0L && newNominal >= tx.totalTagihan -> "Lunas"
            daysToDue < 0L && newNominal < tx.totalTagihan -> "Terlambat"
            daysToDue == 0L && newNominal < tx.totalTagihan -> "Jatuh Tempo"
            newNominal > 0L && tx.statusPembayaran.equals("DP / Uang Muka", ignoreCase = true) -> "DP / Uang Muka"
            newNominal > 0L -> "Dibayar Sebagian"
            else -> "Belum Bayar"
        }
        val updatedMethod = if (!latestMethod.isNullOrBlank()) latestMethod else tx.metodePembayaran
        val updatedTx = prepareTransactionForSave(
            incoming = tx.copy(
                nominalPembayaran = newNominal,
                statusPembayaran = newStatus,
                metodePembayaran = updatedMethod
            ),
            existing = tx
        )
        val changedFields = MirrorOutboxCodec.computeTransactionChangedFieldsJson(tx, updatedTx)
        dao.saveTransactionWithOutboxAtomic(
            transaction = updatedTx,
            isNew = false,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = changedFields,
            serverTimestampMillis = currentServerTimeRef()
        )
        mirrorSyncEngine?.onTransactionOutboxCommitted(tx = updatedTx, isNew = false)
    }

    // --- 2. SETTING -> METODE PEMBAYARAN ---
    suspend fun savePaymentMethod(method: PaymentMethodEntity): Long {
        return if (method.id == 0L) {
            dao.insertPaymentMethod(method)
        } else {
            dao.updatePaymentMethod(method)
            method.id
        }
    }

    suspend fun deletePaymentMethod(method: PaymentMethodEntity) {
        dao.deletePaymentMethod(method)
    }

    suspend fun generateNextUnitCode(): String {
        val existingUnits = dao.getAllUnitsOnce()
        var idx = existingUnits.size + 1
        while (true) {
            val candidate = String.format("SAF-CF-%03d", idx)
            if (dao.getUnitByKodeIncludingDeleted(candidate) == null) return candidate
            idx++
        }
    }

    suspend fun saveUnit(unit: FreezerUnitEntity): Long {
        val existingUnit = if (unit.id > 0L) dao.getUnitById(unit.id) else null
        val isNew = existingUnit == null
        val withCode = if (unit.kodeUnit.isBlank()) {
            unit.copy(kodeUnit = generateNextUnitCode())
        } else unit
        val prepared = prepareUnitForSave(incoming = withCode, existing = existingUnit)
        val changedFields = MirrorOutboxCodec.computeUnitChangedFieldsJson(existingUnit, prepared)
        val savedId = dao.saveUnitWithOutboxAtomic(
            unit = prepared,
            isNew = isNew,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = changedFields,
            serverTimestampMillis = currentServerTimeRef()
        )
        val finalUnit = dao.getUnitById(savedId) ?: prepared.copy(id = savedId)
        mirrorSyncEngine?.onUnitOutboxCommitted(
            unit = finalUnit,
            isNew = isNew,
            previousPhotoUri = existingUnit?.fotoUnitUri.orEmpty()
        )
        return savedId
    }

    suspend fun deleteUnit(unit: FreezerUnitEntity) {
        val freshUnit = dao.getUnitByIdIncludingDeleted(unit.id) ?: unit
        val relatedDriveFileIds = dao.deleteUnitWithOutboxAtomic(
            unit = freshUnit,
            datasetGenerationId = currentDatasetGenerationId(),
            serverTimestampMillis = currentServerTimeRef()
        )
        mirrorSyncEngine?.onUnitDeletedOutboxCommitted(unit = freshUnit, relatedDriveFileIds = relatedDriveFileIds)
    }

    fun getUnitWithMaintenanceHistory(unitId: Long): Flow<FreezerUnitWithMaintenanceHistory?> =
        dao.getUnitWithMaintenanceHistoryById(unitId)

    fun getMaintenanceHistoryForUnit(unitId: Long): Flow<List<FreezerUnitMaintenanceEntity>> =
        dao.getMaintenanceHistoryForUnit(unitId)

    suspend fun getMaintenanceHistoryForUnitOnce(unitId: Long): List<FreezerUnitMaintenanceEntity> =
        dao.getMaintenanceHistoryForUnitOnce(unitId)

    suspend fun recordUnitMaintenance(
        unitId: Long,
        maintenanceDate: String,
        maintenanceType: String,
        description: String = "",
        technicianName: String = "",
        cost: Long = 0L,
        statusAfterMaintenance: String = "Tersedia"
    ): Long {
        val unit = dao.getUnitById(unitId) ?: return -1L
        val resolvedStatus = statusAfterMaintenance.ifBlank { unit.statusUnit }
        val recordId = dao.insertFreezerUnitMaintenance(
            FreezerUnitMaintenanceEntity(
                unitId = unitId,
                unitCode = unit.kodeUnit,
                maintenanceDate = maintenanceDate,
                maintenanceType = maintenanceType,
                description = description,
                technicianName = technicianName,
                cost = cost,
                statusAfterMaintenance = resolvedStatus
            )
        )
        val summaryLine = buildString {
            append(maintenanceDate)
            append(" - ")
            append(maintenanceType)
            if (description.isNotBlank()) {
                append(": ")
                append(description)
            }
            if (technicianName.isNotBlank()) {
                append(" (")
                append(technicianName)
                append(")")
            }
        }
        val updatedSummary = if (unit.maintenanceHistory.isBlank()) {
            summaryLine
        } else {
            summaryLine + "\n" + unit.maintenanceHistory
        }
        val updatedUnit = prepareUnitForSave(
            incoming = unit.copy(
                statusUnit = resolvedStatus,
                maintenanceHistory = updatedSummary
            ),
            existing = unit
        )
        val changedFields = MirrorOutboxCodec.computeUnitChangedFieldsJson(unit, updatedUnit)
        dao.saveUnitWithOutboxAtomic(
            unit = updatedUnit,
            isNew = false,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = changedFields,
            serverTimestampMillis = currentServerTimeRef()
        )
        val persistedUnit = dao.getUnitById(unitId) ?: updatedUnit
        mirrorSyncEngine?.onUnitOutboxCommitted(
            unit = persistedUnit,
            isNew = false,
            previousPhotoUri = unit.fotoUnitUri
        )
        return recordId
    }

    suspend fun generateUniqueCustomerCode(preferredId: Long = 0L): String {
        var counter = if (preferredId > 0L) preferredId.toInt() else (dao.getCustomerCount() + 1)
        while (true) {
            val candidate = String.format("CUST/SAF/%04d", counter)
            val existing = dao.getCustomerByKodeIncludingDeleted(candidate)
            if (existing == null || existing.id == preferredId) {
                return candidate
            }
            counter++
        }
    }

    /**
     * Saves or updates a customer in the central database.
     * - Automatically assigns a permanent Customer ID (CUST/SAF/000X) so all future transactions stay linked under 1 identity.
     * - Automatically synchronizes updated customer name/KTP/contact snapshots across existing transactions.
     */
    suspend fun saveCustomer(customer: CustomerEntity): Long {
        val existingBeforeSave = if (customer.id > 0L) dao.getCustomerById(customer.id) else null
        val isNewCustomer = existingBeforeSave == null
        // Keep combined rtRw and separated rt/rw synchronized
        val syncedRtRw = when {
            customer.rt.isNotBlank() || customer.rw.isNotBlank() ->
                "${customer.rt.ifBlank { "000" }}/${customer.rw.ifBlank { "000" }}"
            else -> customer.rtRw
        }
        val parts = syncedRtRw.split("/")
        val syncedRt = customer.rt.ifBlank { parts.getOrNull(0)?.trim().orEmpty() }
        val syncedRw = customer.rw.ifBlank { parts.getOrNull(1)?.trim().orEmpty() }
        val normalizedCustomer = prepareCustomerForSave(
            incoming = customer.copy(
                rtRw = syncedRtRw,
                rt = syncedRt,
                rw = syncedRw
            ),
            existing = existingBeforeSave
        )
        val changedFields = MirrorOutboxCodec.computeCustomerChangedFieldsJson(existingBeforeSave, normalizedCustomer)
        val savedId = dao.saveCustomerWithOutboxAtomic(
            customer = normalizedCustomer,
            isNew = isNewCustomer,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = changedFields,
            serverTimestampMillis = currentServerTimeRef()
        )
        val savedCustomer = dao.getCustomerById(savedId) ?: normalizedCustomer.copy(id = savedId)

        if (!isNewCustomer) {
            // Synchronize changes to customer snapshot fields in rental_transactions
            val allTx = dao.getAllTransactionsOnce().filter { it.customerId == savedCustomer.id }
            allTx.forEach { tx ->
                val updatedTx = prepareTransactionForSave(
                    incoming = tx.copy(
                        namaPelangganSnapshot = savedCustomer.namaLengkap,
                        fotoKtpDepanTransUri = tx.fotoKtpDepanTransUri.ifBlank { savedCustomer.fotoKtpDepanUri },
                        fotoKtpBelakangTransUri = tx.fotoKtpBelakangTransUri.ifBlank { savedCustomer.fotoKtpBelakangUri }
                    ),
                    existing = tx
                )
                val txChangedFields = MirrorOutboxCodec.computeTransactionChangedFieldsJson(tx, updatedTx)
                dao.saveTransactionWithOutboxAtomic(
                    transaction = updatedTx,
                    isNew = false,
                    datasetId = currentDatasetId(),
                    datasetGenerationId = currentDatasetGenerationId(),
                    changedFieldsJson = txChangedFields,
                    serverTimestampMillis = currentServerTimeRef()
                )
            }
        }
        // Sync customer_location table when customer has default coordinates
        val lat = savedCustomer.latitudeDefault
        val lon = savedCustomer.longitudeDefault
        if (savedId > 0L && lat != null && lon != null) {
            val now = DateCurrencyUtils.nowDateTimeDisplay()
            val existingLoc = dao.getLatestCustomerLocation(savedId)
            val addr = savedCustomer.lokasiPenggunaanDefault.ifBlank { savedCustomer.alamatLengkap }
            if (existingLoc == null || existingLoc.latitude != lat || existingLoc.longitude != lon || existingLoc.address != addr) {
                dao.insertCustomerLocation(
                    CustomerLocationEntity(
                        customerId = savedId,
                        latitude = lat,
                        longitude = lon,
                        address = addr,
                        locationSource = "CUSTOMER_DEFAULT",
                        accuracy = 5.0f,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
        if (savedId > 0L) {
            logCustomerTimelineEvent(
                customerId = savedId,
                eventType = "REGISTRASI",
                title = if (isNewCustomer) "Registrasi Pelanggan Baru" else "Pembaruan Profil Pelanggan",
                description = "${savedCustomer.namaLengkap} (${savedCustomer.nomorHp}) • Alamat: ${savedCustomer.alamatLengkap.take(60)}"
            )
            mirrorSyncEngine?.onCustomerOutboxCommitted(
                customer = savedCustomer,
                isNew = isNewCustomer,
                previousKtpUri = existingBeforeSave?.fotoKtpDepanUri.orEmpty(),
                previousCustomerPhotoUri = existingBeforeSave?.fotoPelangganUri.orEmpty()
            )
        }
        return savedId
    }

    suspend fun getTransactionCountForCustomer(customerId: Long): Int =
        dao.getTransactionCountForCustomer(customerId)

    suspend fun deleteCustomer(
        customer: CustomerEntity,
        allowCascadeDeleteTransactions: Boolean = false
    ): Boolean {
        val freshCustomer = dao.getCustomerByIdIncludingDeleted(customer.id) ?: customer
        val txCount = dao.getTransactionCountForCustomer(freshCustomer.id)
        if (txCount > 0 && !allowCascadeDeleteTransactions) {
            // Protect customer that already has historical transactions unless cascade is requested
            return false
        }
        val (slotKeys, relatedDriveFileIds) = dao.deleteCustomerWithOutboxAtomic(
            customer = freshCustomer,
            datasetGenerationId = currentDatasetGenerationId(),
            serverTimestampMillis = currentServerTimeRef()
        )
        mirrorSyncEngine?.onCustomerDeletedOutboxCommitted(
            customer = freshCustomer,
            relatedPhotoSlotKeys = slotKeys,
            relatedDriveFileIds = relatedDriveFileIds
        )
        return true
    }

    private fun parseInvoiceSequenceForYear(invoice: String, targetYear: Int): Int {
        val m = Regex("""INV/SAF/(\d{4})\d*/(\d+)""", RegexOption.IGNORE_CASE).find(invoice.trim())
            ?: return 0
        val y = m.groupValues[1].toIntOrNull() ?: return 0
        if (y != targetYear) return 0
        return m.groupValues[2].toIntOrNull() ?: 0
    }

    private fun readPersistedInvoiceSeqFloor(year: Int): Int {
        mirrorSyncEngine?.generationManager?.let {
            return it.getObservedMaxInvoiceSequenceForYear(year)
        }
        val ctx = appContext ?: return 0
        val prefs = ctx.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("${com.example.drive.DatabaseGenerationManager.KEY_INVOICE_SEQ_PREFIX}$year", 0).coerceAtLeast(0)
    }

    fun recordObservedInvoiceSequence(invoiceNumber: String) {
        val engineGen = mirrorSyncEngine?.generationManager
        if (engineGen != null) {
            engineGen.recordObservedInvoiceNumber(invoiceNumber)
            return
        }
        val ctx = appContext ?: return
        val m = Regex("""INV/SAF/(\d{4})\d*/(\d+)""", RegexOption.IGNORE_CASE).find(invoiceNumber.trim()) ?: return
        val y = m.groupValues[1].toIntOrNull() ?: return
        val seq = m.groupValues[2].toIntOrNull() ?: return
        if (seq <= 0) return
        val prefs = ctx.getSharedPreferences(GoogleDriveMirrorSyncEngine.PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${com.example.drive.DatabaseGenerationManager.KEY_INVOICE_SEQ_PREFIX}$y"
        if (seq > prefs.getInt(key, 0)) {
            prefs.edit().putInt(key, seq).commit()
        }
    }

    /**
     * Generates a strictly monotonic, collision-free invoice number `INV/SAF/YYYY/XXXX`
     * by combining:
     * 1. All local SQLite `rental_transactions` rows (INCLUDING soft-deleted rows)
     * 2. Pending/failed Drive outbox queue payloads (`drive_local_sync_queue`)
     * 3. Cloud metadata high-water sequence (from Drive mirror manifest / Cloud sync / audit)
     *
     * Never resets back to `0001` when a local transaction fails or rolls back.
     */
    suspend fun generateUniqueInvoiceNumber(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val prefix = "INV/SAF/$year/%"

        var maxSeq = 0

        // 1. All local transactions (active + soft-deleted)
        dao.getAllInvoiceNumbersMatchingPrefixIncludingDeleted(prefix).forEach { inv ->
            maxSeq = maxOf(maxSeq, parseInvoiceSequenceForYear(inv, year))
        }

        // 2. Drive sync queue payloads (protects against rolled-back / in-flight cloud records)
        runCatching {
            dao.getAllDriveLocalSyncQueueOnce()
                .filter { it.recordCategory == GoogleDriveMirrorSyncEngine.CATEGORY_TRANSACTION }
                .forEach { q ->
                    if (q.payloadJson.contains("INV/SAF/")) {
                        val m = Regex("""INV/SAF/\d{4}\d*/\d+""").find(q.payloadJson)
                        if (m != null) {
                            maxSeq = maxOf(maxSeq, parseInvoiceSequenceForYear(m.value, year))
                        }
                    }
                }
        }

        // 3. Persisted Cloud + Local monotonic high-water sequence
        maxSeq = maxOf(maxSeq, readPersistedInvoiceSeqFloor(year))

        // 4. Optional live cloud metadata check when connected & online (non-blocking best-effort)
        runCatching {
            val cloudMax = mirrorSyncEngine?.syncRepository?.fetchCloudMaxInvoiceSequenceForYear(year) ?: 0
            maxSeq = maxOf(maxSeq, cloudMax)
        }

        var counter = maxOf(maxSeq + 1, dao.getTransactionCount() + 1)
        while (true) {
            val candidate = String.format(Locale.US, "INV/SAF/%d/%04d", year, counter)
            val existing = dao.getTransactionByInvoiceIncludingDeleted(candidate)
            if (existing == null) {
                recordObservedInvoiceSequence(candidate)
                return candidate
            }
            counter++
        }
    }

    suspend fun getTransactionByUuid(uuid: String): RentalTransactionEntity? =
        if (uuid.isBlank()) null else dao.getTransactionByUuid(uuid)

    /**
     * Non-destructive, idempotent PDF path attachment + Drive fileId linking.
     * NEVER triggers Room OnConflictStrategy.REPLACE or changes transaction rowId/UUID.
     * If a Drive file was already uploaded on attempt 1 (same record_uuid), preserves & links its
     * existing driveFileId to SQLite without creating duplicate files on Google Drive.
     */
    suspend fun attachGeneratedPdfAndSyncIdempotently(
        transactionId: Long,
        transactionUuid: String,
        pdfPath: String
    ): RentalTransactionEntity {
        val now = System.currentTimeMillis()
        val committed = (if (transactionUuid.isNotBlank()) dao.getTransactionByUuid(transactionUuid) else null)
            ?: dao.getTransactionById(transactionId)
            ?: throw IllegalStateException("Transaksi tidak ditemukan di SQLite saat menautkan PDF (id=$transactionId, uuid=$transactionUuid).")

        if (pdfPath.isNotBlank() && committed.pdfInvoicePath != pdfPath) {
            dao.updateTransactionPdfPath(committed.id, pdfPath, now)
        }

        val key = "TRANSACTION:${committed.id}"
        val existingMap = dao.getDriveCloudFileMapByKey(key) ?: dao.getDriveCloudFileMapByUuid(committed.uuid)
        val existingDriveId = committed.driveId
            .ifBlank { existingMap?.driveFileId.orEmpty() }

        val freshAfterPdf = (dao.getTransactionByUuid(committed.uuid) ?: dao.getTransactionById(committed.id) ?: committed)
            .copy(
                pdfInvoicePath = pdfPath.ifBlank { committed.pdfInvoicePath },
                driveId = existingDriveId
            )

        if (existingDriveId.isNotBlank() && committed.driveId != existingDriveId) {
            val targetSyncStatus = if (existingMap?.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED) {
                MirrorOutboxCodec.STATUS_SYNCED
            } else {
                freshAfterPdf.syncStatus.ifBlank { MirrorOutboxCodec.STATUS_PENDING_UPDATE }
            }
            dao.updateTransactionSyncAndDriveId(freshAfterPdf.id, targetSyncStatus, existingDriveId)
        }

        // Update outbox payload idempotently with pdfInvoicePath while preserving driveFileId (zero duplicate Drive files)
        val payload = MirrorOutboxCodec.serializeTransactionJson(
            freshAfterPdf,
            currentDatasetId(),
            currentDatasetGenerationId(),
            currentServerTimeRef()
        )
        val newHash = MirrorOutboxCodec.computeSha256(payload)
        val latestQueue = dao.getLatestDriveSyncQueueForKey(key)
        val alreadySyncedSameHash = existingMap != null &&
            existingMap.driveFileId.isNotBlank() &&
            existingMap.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED &&
            existingMap.contentHash == newHash

        if (!alreadySyncedSameHash) {
            val plan = MirrorOutboxCodec.buildUpsertOutboxPlan(
                localRecordKey = key,
                recordCategory = MirrorOutboxCodec.CATEGORY_TRANSACTION,
                recordId = freshAfterPdf.id,
                recordUuid = freshAfterPdf.uuid,
                entityVersion = freshAfterPdf.version,
                updatedAtMillis = now,
                payloadJson = payload,
                isNew = existingDriveId.isBlank() && existingMap?.driveFileId.isNullOrBlank(),
                datasetGenerationId = currentDatasetGenerationId(),
                changedFieldsJson = """["pdfInvoicePath"]""",
                existingMap = existingMap,
                latestQueue = latestQueue,
                serverTimestampMillis = currentServerTimeRef(),
                explicitDriveFileId = existingDriveId
            )
            if (plan != null) {
                dao.upsertDriveCloudFileMap(plan.fileMap)
                dao.upsertDriveLocalSyncQueueItem(plan.queueItem)
            }
        }

        recordObservedInvoiceSequence(freshAfterPdf.nomorInvoice)
        return dao.getTransactionByUuid(freshAfterPdf.uuid)
            ?: dao.getTransactionById(freshAfterPdf.id)
            ?: freshAfterPdf
    }

    /**
     * Links an already-uploaded Google Drive fileId back to the local SQLite transaction record
     * during idempotent retry ("COBA LAGI") so Drive never creates duplicate JSON/PDF copies.
     */
    suspend fun linkExistingDriveFileIdToTransaction(
        transactionUuid: String,
        localId: Long = 0L
    ): String {
        val tx = (if (transactionUuid.isNotBlank()) dao.getTransactionByUuid(transactionUuid) else null)
            ?: (if (localId > 0L) dao.getTransactionById(localId) else null)
            ?: return ""
        val key = "TRANSACTION:${tx.id}"
        val existingMap = dao.getDriveCloudFileMapByKey(key) ?: dao.getDriveCloudFileMapByUuid(tx.uuid)
        val driveId = tx.driveId.ifBlank { existingMap?.driveFileId.orEmpty() }
        if (driveId.isNotBlank() && tx.driveId != driveId) {
            val syncSt = if (existingMap?.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_SYNCED) {
                MirrorOutboxCodec.STATUS_SYNCED
            } else {
                tx.syncStatus
            }
            dao.updateTransactionSyncAndDriveId(tx.id, syncSt, driveId)
        }
        return driveId
    }

    suspend fun saveTransaction(transaction: RentalTransactionEntity): Long {
        val existingTx = if (transaction.id > 0L) dao.getTransactionById(transaction.id) else null
        val isNewTx = existingTx == null
        val preparedTx = prepareTransactionForSave(incoming = transaction, existing = existingTx)
        val changedFields = MirrorOutboxCodec.computeTransactionChangedFieldsJson(existingTx, preparedTx)
        val savedId = dao.saveTransactionWithOutboxAtomic(
            transaction = preparedTx,
            isNew = isNewTx,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = changedFields,
            serverTimestampMillis = currentServerTimeRef()
        )
        // Also update the customer's default usage location & GPS if provided in this transaction
        val customer = dao.getCustomerById(preparedTx.customerId)
        if (customer != null) {
            val updatedKode = customer.kodePelanggan.ifBlank { generateUniqueCustomerCode(customer.id) }
            val updatedNamaLokasi = if (preparedTx.namaLokasiUsaha.isNotBlank()) preparedTx.namaLokasiUsaha else customer.namaLokasiDefault
            val updatedLokasi = if (preparedTx.alamatLokasiGps.isNotBlank()) preparedTx.alamatLokasiGps else customer.lokasiPenggunaanDefault
            val updatedRtRwLokasi = if (preparedTx.rtRwLokasi.isNotBlank()) preparedTx.rtRwLokasi else customer.rtRwLokasiDefault
            val updatedKelLokasi = if (preparedTx.kelurahanDesaLokasi.isNotBlank()) preparedTx.kelurahanDesaLokasi else customer.kelurahanLokasiDefault
            val updatedKecLokasi = if (preparedTx.kecamatanLokasi.isNotBlank()) preparedTx.kecamatanLokasi else customer.kecamatanLokasiDefault
            val updatedKabLokasi = if (preparedTx.kabupatenKotaLokasi.isNotBlank()) preparedTx.kabupatenKotaLokasi else customer.kabupatenKotaLokasiDefault
            val updatedLat = preparedTx.latitude ?: customer.latitudeDefault
            val updatedLon = preparedTx.longitude ?: customer.longitudeDefault
            val updatedLinkMaps = if (preparedTx.linkGoogleMaps.isNotBlank()) preparedTx.linkGoogleMaps else customer.linkGoogleMapsDefault
            val updatedKtpDepan = customer.fotoKtpDepanUri.ifBlank { preparedTx.fotoKtpDepanTransUri }
            if (updatedKode != customer.kodePelanggan ||
                updatedNamaLokasi != customer.namaLokasiDefault ||
                updatedLokasi != customer.lokasiPenggunaanDefault ||
                updatedRtRwLokasi != customer.rtRwLokasiDefault ||
                updatedKelLokasi != customer.kelurahanLokasiDefault ||
                updatedKecLokasi != customer.kecamatanLokasiDefault ||
                updatedKabLokasi != customer.kabupatenKotaLokasiDefault ||
                updatedLat != customer.latitudeDefault ||
                updatedLon != customer.longitudeDefault ||
                updatedLinkMaps != customer.linkGoogleMapsDefault ||
                updatedKtpDepan != customer.fotoKtpDepanUri
            ) {
                val updatedCust = prepareCustomerForSave(
                    incoming = customer.copy(
                        kodePelanggan = updatedKode,
                        namaLokasiDefault = updatedNamaLokasi,
                        lokasiPenggunaanDefault = updatedLokasi,
                        rtRwLokasiDefault = updatedRtRwLokasi,
                        kelurahanLokasiDefault = updatedKelLokasi,
                        kecamatanLokasiDefault = updatedKecLokasi,
                        kabupatenKotaLokasiDefault = updatedKabLokasi,
                        latitudeDefault = updatedLat,
                        longitudeDefault = updatedLon,
                        linkGoogleMapsDefault = updatedLinkMaps,
                        fotoKtpDepanUri = updatedKtpDepan
                    ),
                    existing = customer
                )
                val custChanged = MirrorOutboxCodec.computeCustomerChangedFieldsJson(customer, updatedCust)
                dao.saveCustomerWithOutboxAtomic(
                    customer = updatedCust,
                    isNew = false,
                    datasetId = currentDatasetId(),
                    datasetGenerationId = currentDatasetGenerationId(),
                    changedFieldsJson = custChanged,
                    serverTimestampMillis = currentServerTimeRef()
                )
                mirrorSyncEngine?.onCustomerOutboxCommitted(
                    customer = updatedCust,
                    isNew = false,
                    previousKtpUri = customer.fotoKtpDepanUri,
                    previousCustomerPhotoUri = customer.fotoPelangganUri
                )
            }
            // Also persist GPS coordinates in CUSTOMER_LOCATION table if transaction has coordinates
            if (preparedTx.latitude != null && preparedTx.longitude != null) {
                val now = DateCurrencyUtils.nowDateTimeDisplay()
                val existingLoc = dao.getLatestCustomerLocation(customer.id)
                val txAddress = preparedTx.alamatLokasiGps.ifBlank { customer.lokasiPenggunaanDefault.ifBlank { customer.alamatLengkap } }
                val acc = preparedTx.akurasiGpsMeter ?: 5.0f
                val src = if (acc > 0f && acc < 100f) "GPS_AUTO" else "MAP_PICKER"
                if (existingLoc == null || existingLoc.latitude != preparedTx.latitude || existingLoc.longitude != preparedTx.longitude) {
                    dao.insertCustomerLocation(
                        CustomerLocationEntity(
                            customerId = customer.id,
                            latitude = preparedTx.latitude,
                            longitude = preparedTx.longitude,
                            address = txAddress,
                            locationSource = src,
                            accuracy = acc,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }
        }
        // Automatically synchronize unit status based on active rentals (with smart protection rules)
        val unit = dao.getUnitById(preparedTx.unitId)
        if (unit != null) {
            if (preparedTx.statusPenyewaan == "Selesai" || preparedTx.statusPenyewaan == "Dibatalkan") {
                restoreUnitStatusIfEligible(unitId = unit.id, excludingTransactionId = savedId)
            } else if (preparedTx.statusPenyewaan == "Dalam Penyewaan" || preparedTx.statusPenyewaan == "Booking") {
                val updatedLokasi = if (preparedTx.alamatLokasiGps.isNotBlank()) {
                    preparedTx.alamatLokasiGps
                } else {
                    unit.lokasiUnit
                }
                if (unit.statusUnit != "Disewa" || updatedLokasi != unit.lokasiUnit) {
                    val updatedUnit = prepareUnitForSave(
                        incoming = unit.copy(statusUnit = "Disewa", lokasiUnit = updatedLokasi),
                        existing = unit
                    )
                    val unitChanged = MirrorOutboxCodec.computeUnitChangedFieldsJson(unit, updatedUnit)
                    dao.saveUnitWithOutboxAtomic(
                        unit = updatedUnit,
                        isNew = false,
                        datasetId = currentDatasetId(),
                        datasetGenerationId = currentDatasetGenerationId(),
                        changedFieldsJson = unitChanged,
                        serverTimestampMillis = currentServerTimeRef()
                    )
                    mirrorSyncEngine?.onUnitOutboxCommitted(
                        unit = updatedUnit,
                        isNew = false,
                        previousPhotoUri = unit.fotoUnitUri
                    )
                }
            }
        }
        val finalTx = dao.getTransactionById(savedId) ?: preparedTx.copy(id = savedId)
        mirrorSyncEngine?.onTransactionOutboxCommitted(
            tx = finalTx,
            isNew = isNewTx
        )
        return savedId
    }

    // --- MULTI-UNIT TRANSACTION, UNIT-BASED BILLING SCHEDULE & SIGNATURE INTEGRATION (v12) ---

    fun getTransactionUnitItemsByTx(transactionId: Long): Flow<List<TransactionUnitItemEntity>> =
        dao.getTransactionUnitItemsByTx(transactionId)

    suspend fun getTransactionUnitItemsByTxOnce(transactionId: Long): List<TransactionUnitItemEntity> =
        dao.getTransactionUnitItemsByTxOnce(transactionId)

    suspend fun getTransactionUnitItemsByCustomerOnce(customerId: Long): List<TransactionUnitItemEntity> =
        dao.getTransactionUnitItemsByCustomerOnce(customerId)

    suspend fun getAllTransactionUnitItemsOnce(): List<TransactionUnitItemEntity> =
        dao.getAllTransactionUnitItemsOnce()

    suspend fun getTransactionSignaturesByTxOnce(transactionId: Long): List<TransactionSignatureEntity> =
        dao.getTransactionSignaturesByTxOnce(transactionId)

    fun calculateMultiUnitBillingSummary(
        unitItems: List<TransactionUnitItemEntity>,
        ongkir: Long = 0L,
        deposit: Long = 0L,
        diskon: Long = 0L,
        overrideNominalPembayaran: Long? = null
    ): MultiUnitBillingSummary {
        val totalSewa = unitItems.sumOf { it.hargaSewa.coerceAtLeast(0L) }
        val totalTagihan = (totalSewa + ongkir + deposit - diskon).coerceAtLeast(0L)
        val sumUnitPayments = unitItems.sumOf { it.nominalPembayaran.coerceAtLeast(0L) }
        val totalBayar = overrideNominalPembayaran?.takeIf { it > 0L && sumUnitPayments == 0L } ?: sumUnitPayments
        val sisa = (totalTagihan - totalBayar).coerceAtLeast(0L)
        return MultiUnitBillingSummary(
            totalUnits = unitItems.size,
            totalHargaSewaUnits = totalSewa,
            ongkir = ongkir,
            deposit = deposit,
            diskon = diskon,
            totalTagihan = totalTagihan,
            totalPembayaran = totalBayar,
            sisaTagihan = sisa,
            unitBreakdowns = unitItems
        )
    }

    /**
     * Smart Unit Status Restoration Rule (Delete / Cancel / Remove Unit):
     * - If unit status is "Hilang" -> ALWAYS keep "Hilang".
     * - If unit status is repair/maintenance ("Dalam Perbaikan", "Perbaikan", "Maintenance", "Perawatan", "Rusak") -> ALWAYS keep maintenance status.
     * - If unit status is "Tidak Aktif" -> keep "Tidak Aktif".
     * - If unit STILL has another active (non-deleted, non-Selesai, non-Dibatalkan) transaction -> keep rented status.
     * - Otherwise -> restore unit status to "Tersedia" AND commit to SQLite + Google Drive Outbox!
     */
    suspend fun restoreUnitStatusIfEligible(
        unitId: Long,
        excludingTransactionId: Long = -1L
    ): Boolean {
        val unit = dao.getUnitById(unitId) ?: return false
        if (unit.isLostStatus() || unit.isMaintenanceOrDamagedStatus() || unit.isInactiveStatus()) {
            return false
        }
        val otherActiveParentTxs = dao.getActiveTransactionsForUnitOnce(unitId, excludingTransactionId)
        val otherActiveItems = dao.getActiveUnitItemsForUnitOnce(unitId, excludingTransactionId)
        if (otherActiveParentTxs.isNotEmpty() || otherActiveItems.isNotEmpty()) {
            return false
        }
        if (unit.isAvailableStatus()) {
            return false
        }
        val freedUnit = prepareUnitForSave(unit.copy(statusUnit = "Tersedia"), unit)
        val uCh = MirrorOutboxCodec.computeUnitChangedFieldsJson(unit, freedUnit)
        dao.saveUnitWithOutboxAtomic(
            unit = freedUnit,
            isNew = false,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = uCh,
            serverTimestampMillis = currentServerTimeRef()
        )
        mirrorSyncEngine?.onUnitOutboxCommitted(freedUnit, isNew = false, previousPhotoUri = unit.fotoUnitUri)
        return true
    }

    /**
     * Section 2, 3 & 5: Validasi Sistem Multi-Unit Berbasis Status Aktual Database SQLite
     * - Pelanggan wajib dipilih
     * - Minimal 1 unit dipilih
     * - Satu unit tidak dapat dipilih dua kali dalam transaksi yang sama
     * - Unit baru yang ditambahkan WAJIB berstatus aktual "Tersedia" di database SQLite
     *   (menolak tegas: Hilang, Disewa/Sedang disewa/Masih Tersewa, Perbaikan/Dalam Perbaikan/Maintenance/Perawatan/Rusak, Dipesan, Tidak Aktif)
     * - Menolak unit yang masih aktif di transaksi lain atau sedang direservasi sementara oleh sesi form lain
     * - Setiap unit wajib memiliki program sewa dan harga
     */
    suspend fun validateMultiUnitTransaction(
        customerId: Long,
        transactionId: Long,
        unitItems: List<TransactionUnitItemEntity>,
        reservationSessionId: String = ""
    ): MultiUnitValidationResult {
        if (customerId <= 0L) {
            return MultiUnitValidationResult(
                isValid = false,
                errorCode = "ERR_NO_CUSTOMER",
                errorMessage = "Data pelanggan belum dipilih."
            )
        }
        if (unitItems.isEmpty()) {
            return MultiUnitValidationResult(
                isValid = false,
                errorCode = "ERR_NO_UNITS",
                errorMessage = "Minimal 1 unit freezer harus dipilih dalam transaksi."
            )
        }
        val unitIds = unitItems.map { it.unitId }
        if (unitIds.any { it <= 0L }) {
            return MultiUnitValidationResult(
                isValid = false,
                errorCode = "ERR_INVALID_UNIT_ID",
                errorMessage = "Terdapat unit freezer yang belum dipilih."
            )
        }
        if (unitIds.distinct().size != unitIds.size) {
            return MultiUnitValidationResult(
                isValid = false,
                errorCode = "ERR_DUPLICATE_UNIT_IN_TX",
                errorMessage = "Satu unit freezer tidak dapat disewa dua kali dalam transaksi yang sama."
            )
        }
        for (item in unitItems) {
            if (item.jenisProgram.isBlank()) {
                return MultiUnitValidationResult(
                    isValid = false,
                    errorCode = "ERR_MISSING_PROGRAM",
                    errorMessage = "Setiap unit wajib memiliki program sewa (Sewa Reguler / Sewa Hak Milik)."
                )
            }
            if (item.hargaSewa <= 0L) {
                return MultiUnitValidationResult(
                    isValid = false,
                    errorCode = "ERR_INVALID_PRICE",
                    errorMessage = "Unit ${item.namaUnitSnapshot.ifBlank { "#${item.unitIndexNumber}" }} wajib memiliki harga sewa lebih dari Rp 0."
                )
            }
        }
        // Validate live DB status for every newly selected unit (units not already owned by this transaction)
        val existingTxUnits = if (transactionId > 0L) {
            dao.getTransactionUnitItemsByTxOnce(transactionId).map { it.unitId }.toSet()
        } else {
            emptySet()
        }
        val existingParentTx = if (transactionId > 0L) dao.getTransactionById(transactionId) else null

        for (item in unitItems) {
            val isOwnedByCurrentTx = item.unitId in existingTxUnits || existingParentTx?.unitId == item.unitId
            if (!isOwnedByCurrentTx) {
                val dbUnit = dao.getUnitById(item.unitId)
                if (dbUnit == null || dbUnit.isDeleted) {
                    return MultiUnitValidationResult(
                        isValid = false,
                        errorCode = "ERR_UNIT_NOT_FOUND",
                        errorMessage = "Unit ${item.namaUnitSnapshot.ifBlank { "#${item.unitId}" }} tidak ditemukan atau telah dihapus."
                    )
                }
                if (dbUnit.isLostStatus()) {
                    return MultiUnitValidationResult(
                        isValid = false,
                        errorCode = "ERR_UNIT_LOST",
                        errorMessage = "Unit ${dbUnit.namaUnit} (${dbUnit.kodeUnit}) berstatus HILANG dan tidak tersedia untuk disewakan."
                    )
                }
                if (dbUnit.isMaintenanceOrDamagedStatus()) {
                    return MultiUnitValidationResult(
                        isValid = false,
                        errorCode = "ERR_UNIT_MAINTENANCE",
                        errorMessage = "Unit ${dbUnit.namaUnit} (${dbUnit.kodeUnit}) berstatus ${dbUnit.statusUnit} (Perbaikan/Perawatan) dan tidak tersedia untuk disewakan."
                    )
                }
                val otherActiveParent = dao.getActiveTransactionsForUnitOnce(item.unitId, transactionId)
                val otherActiveItems = dao.getActiveUnitItemsForUnitOnce(item.unitId, transactionId)
                if (dbUnit.isRentedStatus() || otherActiveParent.isNotEmpty() || otherActiveItems.isNotEmpty()) {
                    return MultiUnitValidationResult(
                        isValid = false,
                        errorCode = "ERR_UNIT_ALREADY_RENTED",
                        errorMessage = "Unit ${dbUnit.namaUnit} (${dbUnit.kodeUnit}) sedang disewa dan tidak dapat dipilih kembali."
                    )
                }
                if (!dbUnit.isStrictlyAvailableForNewRental()) {
                    return MultiUnitValidationResult(
                        isValid = false,
                        errorCode = "ERR_UNIT_UNAVAILABLE",
                        errorMessage = "Unit ${dbUnit.namaUnit} (${dbUnit.kodeUnit}) berstatus '${dbUnit.statusUnit}' dan hanya unit berstatus 'Tersedia' yang dapat dipilih."
                    )
                }
                if (reservationSessionId.isNotBlank() &&
                    UnitReservationManager.isReservedByOtherSession(item.unitId, reservationSessionId)
                ) {
                    return MultiUnitValidationResult(
                        isValid = false,
                        errorCode = "ERR_UNIT_RESERVED",
                        errorMessage = "Unit ${dbUnit.namaUnit} (${dbUnit.kodeUnit}) sedang dipilih pada form transaksi lain. Pilih unit tersedia lainnya."
                    )
                }
            }
        }

        return MultiUnitValidationResult(isValid = true)
    }

    /**
     * Saves a Multi-Unit Transaction safely & non-destructively:
     * - Re-validates live DB status & timed reservation right before committing
     * - Calculates total billing & derived program summary across all units
     * - Saves parent RentalTransactionEntity (same ID & UUID on edit — NEVER creates duplicate parent!)
     * - Performs NON-DESTRUCTIVE UPSERT on TransactionUnitItemEntity (preserves existing item IDs,
     *   unit_payment_allocations, and independent Sewa Hak Milik history!)
     * - Updates all rented FreezerUnitEntity records to "Disewa" (and safely evaluates any removed/cancelled units)
     * - Syncs parent transaction & unit status changes to SQLite + Google Drive Outbox
     */
    suspend fun saveMultiUnitTransaction(
        parentTransaction: RentalTransactionEntity,
        unitItems: List<TransactionUnitItemEntity>,
        signatures: List<TransactionSignatureEntity> = emptyList(),
        reservationSessionId: String = ""
    ): RentalTransactionEntity {
        val now = DateCurrencyUtils.nowDateTimeDisplay()
        val existingParentBeforeSave = if (parentTransaction.id > 0L) dao.getTransactionById(parentTransaction.id) else null
        val previousItems = if (parentTransaction.id > 0L) {
            dao.getTransactionUnitItemsByTxOnce(parentTransaction.id)
        } else {
            emptyList()
        }

        val normalizedItems = if (unitItems.isNotEmpty()) {
            unitItems.mapIndexed { idx, item ->
                val dbUnit = dao.getUnitById(item.unitId)
                val start = item.tanggalMulaiSewa.ifBlank { parentTransaction.tanggalMulaiSewa.ifBlank { DateCurrencyUtils.todayIso() } }
                val durasi = if (item.durasiHari > 0) item.durasiHari else parentTransaction.durasiHari.coerceAtLeast(30)
                val end = item.tanggalAkhirSewa.ifBlank { DateCurrencyUtils.addDaysToIsoDate(start, durasi) }
                val due = item.tanggalJatuhTempo.ifBlank { end }
                val statusBayarUnit = when {
                    item.hargaSewa > 0L && item.nominalPembayaran >= item.hargaSewa -> "Lunas"
                    item.nominalPembayaran > 0L -> "Dibayar Sebagian"
                    else -> item.statusPembayaran.ifBlank { "Belum Bayar" }
                }
                item.copy(
                    customerId = parentTransaction.customerId,
                    unitIndexNumber = idx + 1,
                    kodeUnitSnapshot = item.kodeUnitSnapshot.ifBlank { dbUnit?.kodeUnit.orEmpty() },
                    namaUnitSnapshot = item.namaUnitSnapshot.ifBlank { dbUnit?.namaUnit.orEmpty() },
                    jenisUnitSnapshot = item.jenisUnitSnapshot.ifBlank { dbUnit?.jenisUnit.orEmpty() },
                    merekSnapshot = item.merekSnapshot.ifBlank { dbUnit?.merek.orEmpty() },
                    kapasitasLiterSnapshot = if (item.kapasitasLiterSnapshot > 0) item.kapasitasLiterSnapshot else (dbUnit?.kapasitasLiter ?: 0),
                    tanggalMulaiSewa = start,
                    durasiHari = durasi,
                    tanggalAkhirSewa = end,
                    tanggalJatuhTempo = due,
                    statusPembayaran = statusBayarUnit,
                    statusPenyewaan = item.statusPenyewaan.ifBlank { parentTransaction.statusPenyewaan.ifBlank { "Dalam Penyewaan" } },
                    createdAt = item.createdAt.ifBlank { now },
                    updatedAt = now
                )
            }
        } else {
            val dbUnit = dao.getUnitById(parentTransaction.unitId)
            listOf(
                TransactionUnitItemEntity(
                    transactionId = parentTransaction.id,
                    customerId = parentTransaction.customerId,
                    unitId = parentTransaction.unitId,
                    unitIndexNumber = 1,
                    kodeUnitSnapshot = parentTransaction.kodeUnitSnapshot.ifBlank { dbUnit?.kodeUnit.orEmpty() },
                    namaUnitSnapshot = parentTransaction.namaUnitSnapshot.ifBlank { dbUnit?.namaUnit.orEmpty() },
                    jenisUnitSnapshot = dbUnit?.jenisUnit.orEmpty(),
                    merekSnapshot = dbUnit?.merek.orEmpty(),
                    kapasitasLiterSnapshot = dbUnit?.kapasitasLiter ?: 0,
                    jenisProgram = parentTransaction.jenisProgram,
                    totalPeriodeHakMilik = parentTransaction.totalPeriodeHakMilik,
                    pembayaranKeHakMilik = parentTransaction.pembayaranKeHakMilik,
                    statusProgramHakMilik = parentTransaction.statusProgramHakMilik,
                    hargaSewa = parentTransaction.hargaSewa,
                    durasiHari = parentTransaction.durasiHari,
                    tanggalMulaiSewa = parentTransaction.tanggalMulaiSewa,
                    tanggalAkhirSewa = parentTransaction.tanggalAkhirSewa,
                    tanggalJatuhTempo = parentTransaction.tanggalJatuhTempo,
                    nominalPembayaran = parentTransaction.nominalPembayaran,
                    statusPembayaran = parentTransaction.statusPembayaran,
                    statusPenyewaan = parentTransaction.statusPenyewaan,
                    fotoKondisiDepanUri = parentTransaction.fotoUnitSebelumKirimUri,
                    fotoKondisiSampingUri = parentTransaction.fotoKondisiFisikTransUri,
                    fotoSerahTerimaPelangganUri = parentTransaction.fotoSerahTerimaUri,
                    fotoSerahTerimaLokasiUri = parentTransaction.fotoFreezerDiLokasiUri,
                    fotoBuktiPenerimaanUri = parentTransaction.fotoPelangganBersamaFreezerUri,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // Real-time DB validation right before committing (protects against race conditions & unavailable units)
        val validation = validateMultiUnitTransaction(
            customerId = parentTransaction.customerId,
            transactionId = parentTransaction.id,
            unitItems = normalizedItems,
            reservationSessionId = reservationSessionId
        )
        if (!validation.isValid) {
            throw IllegalArgumentException(validation.errorMessage)
        }

        val summary = calculateMultiUnitBillingSummary(
            unitItems = normalizedItems,
            ongkir = parentTransaction.ongkir,
            deposit = parentTransaction.deposit,
            diskon = parentTransaction.diskon,
            overrideNominalPembayaran = parentTransaction.nominalPembayaran
        )

        val firstUnit = normalizedItems.first()
        val combinedNamaUnit = if (normalizedItems.size == 1) {
            firstUnit.namaUnitSnapshot
        } else {
            "${normalizedItems.size} Unit (${normalizedItems.joinToString(", ") { it.namaUnitSnapshot }})"
        }
        val combinedKodeUnit = normalizedItems.joinToString(", ") { it.kodeUnitSnapshot }
        val distinctPrograms = normalizedItems.map { it.jenisProgram }.distinct()
        val firstHakMilikUnit = normalizedItems.firstOrNull { it.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true) }
        val combinedProgram = when {
            distinctPrograms.size == 1 -> distinctPrograms.first()
            firstHakMilikUnit != null -> "Sewa Hak Milik"
            else -> firstUnit.jenisProgram.ifBlank { "Sewa Reguler" }
        }
        val derivedTotalPeriodeHm = firstHakMilikUnit?.totalPeriodeHakMilik ?: 0
        val derivedPembayaranKeHm = firstHakMilikUnit?.pembayaranKeHakMilik ?: 1
        val derivedStatusProgramHm = firstHakMilikUnit?.statusProgramHakMilik ?: "Aktif"

        val aggStatusBayar = when {
            summary.totalTagihan > 0L && summary.totalPembayaran >= summary.totalTagihan -> "Lunas"
            summary.totalPembayaran > 0L -> "Dibayar Sebagian"
            else -> parentTransaction.statusPembayaran.ifBlank { "Belum Bayar" }
        }

        val penyewaSig = signatures.firstOrNull { it.signerRole == "PENYEWA" }
        val petugasSig = signatures.firstOrNull { it.signerRole == "PETUGAS_SAF" }

        val invoiceNo = parentTransaction.nomorInvoice.ifBlank { generateUniqueInvoiceNumber() }

        val updatedParent = parentTransaction.copy(
            nomorInvoice = invoiceNo,
            unitId = firstUnit.unitId,
            namaUnitSnapshot = combinedNamaUnit,
            kodeUnitSnapshot = combinedKodeUnit,
            jenisProgram = combinedProgram,
            totalPeriodeHakMilik = derivedTotalPeriodeHm,
            pembayaranKeHakMilik = derivedPembayaranKeHm,
            statusProgramHakMilik = derivedStatusProgramHm,
            hargaSewa = summary.totalHargaSewaUnits,
            totalTagihan = summary.totalTagihan,
            nominalPembayaran = summary.totalPembayaran,
            statusPembayaran = aggStatusBayar,
            durasiHari = firstUnit.durasiHari.coerceAtLeast(1),
            tanggalMulaiSewa = firstUnit.tanggalMulaiSewa.ifBlank { parentTransaction.tanggalMulaiSewa },
            tanggalAkhirSewa = firstUnit.tanggalAkhirSewa.ifBlank { parentTransaction.tanggalAkhirSewa },
            tanggalJatuhTempo = firstUnit.tanggalJatuhTempo.ifBlank { parentTransaction.tanggalJatuhTempo },
            fotoUnitSebelumKirimUri = parentTransaction.fotoUnitSebelumKirimUri.ifBlank { firstUnit.fotoKondisiDepanUri },
            fotoKondisiFisikTransUri = parentTransaction.fotoKondisiFisikTransUri.ifBlank { firstUnit.fotoKondisiSampingUri },
            fotoSerahTerimaUri = parentTransaction.fotoSerahTerimaUri.ifBlank { firstUnit.fotoSerahTerimaPelangganUri },
            fotoFreezerDiLokasiUri = parentTransaction.fotoFreezerDiLokasiUri.ifBlank { firstUnit.fotoSerahTerimaLokasiUri },
            fotoPelangganBersamaFreezerUri = parentTransaction.fotoPelangganBersamaFreezerUri.ifBlank { firstUnit.fotoBuktiPenerimaanUri },
            tandaTanganPelangganUri = penyewaSig?.filePath?.ifBlank { parentTransaction.tandaTanganPelangganUri } ?: parentTransaction.tandaTanganPelangganUri,
            tandaTanganPetugasUri = petugasSig?.filePath?.ifBlank { parentTransaction.tandaTanganPetugasUri } ?: parentTransaction.tandaTanganPetugasUri,
            namaPenerima = penyewaSig?.signerName?.ifBlank { parentTransaction.namaPenerima } ?: parentTransaction.namaPenerima,
            namaPetugas = petugasSig?.signerName?.ifBlank { parentTransaction.namaPetugas } ?: parentTransaction.namaPetugas
        )

        val existingTxForPrepare = if (updatedParent.id > 0L) {
            dao.getTransactionByIdIncludingDeleted(updatedParent.id)
        } else if (updatedParent.uuid.isNotBlank()) {
            dao.getTransactionByUuidIncludingDeleted(updatedParent.uuid)
        } else {
            null
        }
        val isNewParentTx = existingTxForPrepare == null
        val preparedParent = prepareTransactionForSave(incoming = updatedParent, existing = existingTxForPrepare)
        val parentChangedFields = MirrorOutboxCodec.computeTransactionChangedFieldsJson(existingTxForPrepare, preparedParent)

        // ATOMIC Room @Transaction commit for BOTH `rental_transactions` AND `transaction_unit_items` + Outbox.
        // Guarantees committed rowId > 0L and permanent UUID v4 in SQLite BEFORE PDF generation or Drive upload!
        val (finalParent, persistedUnitItems) = dao.saveMultiUnitTransactionAtomic(
            parentTransaction = preparedParent,
            normalizedItems = normalizedItems,
            previousItems = previousItems,
            isNew = isNewParentTx,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = parentChangedFields,
            nowDisplay = now,
            serverTimestampMillis = currentServerTimeRef()
        )
        val savedTxId = finalParent.id
        recordObservedInvoiceSequence(finalParent.nomorInvoice)

        // Synchronize customer's default usage location & GPS after atomic commit
        val customer = dao.getCustomerById(finalParent.customerId)
        if (customer != null) {
            val updatedKode = customer.kodePelanggan.ifBlank { generateUniqueCustomerCode(customer.id) }
            val updatedNamaLokasi = if (finalParent.namaLokasiUsaha.isNotBlank()) finalParent.namaLokasiUsaha else customer.namaLokasiDefault
            val updatedLokasi = if (finalParent.alamatLokasiGps.isNotBlank()) finalParent.alamatLokasiGps else customer.lokasiPenggunaanDefault
            val updatedRtRwLokasi = if (finalParent.rtRwLokasi.isNotBlank()) finalParent.rtRwLokasi else customer.rtRwLokasiDefault
            val updatedKelLokasi = if (finalParent.kelurahanDesaLokasi.isNotBlank()) finalParent.kelurahanDesaLokasi else customer.kelurahanLokasiDefault
            val updatedKecLokasi = if (finalParent.kecamatanLokasi.isNotBlank()) finalParent.kecamatanLokasi else customer.kecamatanLokasiDefault
            val updatedKabLokasi = if (finalParent.kabupatenKotaLokasi.isNotBlank()) finalParent.kabupatenKotaLokasi else customer.kabupatenKotaLokasiDefault
            val updatedLat = finalParent.latitude ?: customer.latitudeDefault
            val updatedLon = finalParent.longitude ?: customer.longitudeDefault
            val updatedLinkMaps = if (finalParent.linkGoogleMaps.isNotBlank()) finalParent.linkGoogleMaps else customer.linkGoogleMapsDefault
            val updatedKtpDepan = customer.fotoKtpDepanUri.ifBlank { finalParent.fotoKtpDepanTransUri }
            if (updatedKode != customer.kodePelanggan ||
                updatedNamaLokasi != customer.namaLokasiDefault ||
                updatedLokasi != customer.lokasiPenggunaanDefault ||
                updatedRtRwLokasi != customer.rtRwLokasiDefault ||
                updatedKelLokasi != customer.kelurahanLokasiDefault ||
                updatedKecLokasi != customer.kecamatanLokasiDefault ||
                updatedKabLokasi != customer.kabupatenKotaLokasiDefault ||
                updatedLat != customer.latitudeDefault ||
                updatedLon != customer.longitudeDefault ||
                updatedLinkMaps != customer.linkGoogleMapsDefault ||
                updatedKtpDepan != customer.fotoKtpDepanUri
            ) {
                val updatedCust = prepareCustomerForSave(
                    incoming = customer.copy(
                        kodePelanggan = updatedKode,
                        namaLokasiDefault = updatedNamaLokasi,
                        lokasiPenggunaanDefault = updatedLokasi,
                        rtRwLokasiDefault = updatedRtRwLokasi,
                        kelurahanLokasiDefault = updatedKelLokasi,
                        kecamatanLokasiDefault = updatedKecLokasi,
                        kabupatenKotaLokasiDefault = updatedKabLokasi,
                        latitudeDefault = updatedLat,
                        longitudeDefault = updatedLon,
                        linkGoogleMapsDefault = updatedLinkMaps,
                        fotoKtpDepanUri = updatedKtpDepan
                    ),
                    existing = customer
                )
                val custChanged = MirrorOutboxCodec.computeCustomerChangedFieldsJson(customer, updatedCust)
                dao.saveCustomerWithOutboxAtomic(
                    customer = updatedCust,
                    isNew = false,
                    datasetId = currentDatasetId(),
                    datasetGenerationId = currentDatasetGenerationId(),
                    changedFieldsJson = custChanged,
                    serverTimestampMillis = currentServerTimeRef()
                )
                mirrorSyncEngine?.onCustomerOutboxCommitted(
                    customer = updatedCust,
                    isNew = false,
                    previousKtpUri = customer.fotoKtpDepanUri,
                    previousCustomerPhotoUri = customer.fotoPelangganUri
                )
            }
        }

        // Notify mirror sync engine of committed transaction outbox
        mirrorSyncEngine?.onTransactionOutboxCommitted(
            tx = finalParent,
            isNew = isNewParentTx
        )

        // Apply smart unit restoration for any unit explicitly removed during edit
        val currentUnitIds = persistedUnitItems.map { it.unitId }.toSet()
        for (oldItem in previousItems) {
            if (oldItem.unitId !in currentUnitIds) {
                restoreUnitStatusIfEligible(unitId = oldItem.unitId, excludingTransactionId = savedTxId)
            }
        }

        // Update status of all units currently attached to the transaction (and sync to Drive Outbox)
        val isParentClosedOrCancelled = finalParent.statusPenyewaan == "Selesai" || finalParent.statusPenyewaan == "Dibatalkan"
        for (item in persistedUnitItems) {
            val u = dao.getUnitById(item.unitId)
            if (u != null) {
                if (isParentClosedOrCancelled || item.statusPenyewaan == "Selesai" || item.statusPenyewaan == "Dibatalkan") {
                    restoreUnitStatusIfEligible(unitId = u.id, excludingTransactionId = savedTxId)
                } else {
                    val targetLoc = finalParent.alamatLokasiGps.ifBlank { u.lokasiUnit }
                    if (u.statusUnit != "Disewa" || u.lokasiUnit != targetLoc) {
                        val updatedU = prepareUnitForSave(u.copy(statusUnit = "Disewa", lokasiUnit = targetLoc), u)
                        val ch = MirrorOutboxCodec.computeUnitChangedFieldsJson(u, updatedU)
                        dao.saveUnitWithOutboxAtomic(
                            unit = updatedU,
                            isNew = false,
                            datasetId = currentDatasetId(),
                            datasetGenerationId = currentDatasetGenerationId(),
                            changedFieldsJson = ch,
                            serverTimestampMillis = currentServerTimeRef()
                        )
                        mirrorSyncEngine?.onUnitOutboxCommitted(updatedU, isNew = false, previousPhotoUri = u.fotoUnitUri)
                    }
                }
            }
        }

        // Release temporary form reservations after DB commit succeeds
        if (reservationSessionId.isNotBlank()) {
            UnitReservationManager.releaseSession(reservationSessionId)
        }

        val isOfficerLockedOnExisting = existingParentBeforeSave != null && (
            existingParentBeforeSave.officerSnapshotLocked ||
                existingParentBeforeSave.namaPetugas.isNotBlank() ||
                existingParentBeforeSave.tandaTanganPetugasUri.isNotBlank()
            )

        // Save digital/uploaded signatures if provided (never overwrite locked PETUGAS_SAF snapshot on regular edits)
        for (sig in signatures) {
            if (sig.filePath.isNotBlank()) {
                if (sig.signerRole == "PETUGAS_SAF" && isOfficerLockedOnExisting) {
                    continue
                }
                dao.deleteTransactionSignatureByRole(savedTxId, sig.signerRole)
                dao.insertTransactionSignature(
                    sig.copy(
                        id = 0L,
                        transactionId = savedTxId,
                        customerId = finalParent.customerId,
                        signedDate = sig.signedDate.ifBlank { DateCurrencyUtils.todayIso() },
                        createdAt = sig.createdAt.ifBlank { now }
                    )
                )
            }
        }

        // Log timeline event automatically
        logCustomerTimelineEvent(
            customerId = finalParent.customerId,
            transactionId = savedTxId,
            unitId = firstUnit.unitId,
            eventType = if (parentTransaction.id == 0L) "SEWA_UNIT" else "TAMBAH_UNIT",
            title = if (parentTransaction.id == 0L) {
                "Kontrak Sewa Baru (${finalParent.nomorInvoice})"
            } else {
                "Pembaruan Kontrak (${finalParent.nomorInvoice})"
            },
            description = "${persistedUnitItems.size} Unit Freezer • Program: $combinedProgram • Total Tagihan: ${DateCurrencyUtils.formatRupiah(finalParent.totalTagihan)}",
            amount = finalParent.totalTagihan
        )

        return finalParent
    }

    /**
     * Adds a new unit to an existing transaction later (e.g., Pelanggan menyewa unit tambahan di kemudian hari)
     * without altering the start dates, due dates, programs, prices, or Hak Milik history of existing units!
     */
    suspend fun addUnitToExistingTransaction(
        transactionId: Long,
        newUnitItem: TransactionUnitItemEntity,
        reservationSessionId: String = ""
    ): TransactionUnitItemEntity {
        val parentTx = dao.getTransactionById(transactionId)
            ?: throw IllegalArgumentException("Transaksi tidak ditemukan.")
        val existingItems = dao.getTransactionUnitItemsByTxOnce(transactionId)

        if (existingItems.any { it.unitId == newUnitItem.unitId } || (existingItems.isEmpty() && parentTx.unitId == newUnitItem.unitId)) {
            throw IllegalArgumentException("Satu unit freezer tidak dapat disewa dua kali dalam transaksi yang sama.")
        }
        val dbUnit = dao.getUnitById(newUnitItem.unitId)
            ?: throw IllegalArgumentException("Unit freezer tidak ditemukan di database.")
        if (dbUnit.isLostStatus()) {
            throw IllegalArgumentException("Unit ${dbUnit.namaUnit} (${dbUnit.kodeUnit}) berstatus HILANG dan tidak tersedia.")
        }
        if (dbUnit.isMaintenanceOrDamagedStatus()) {
            throw IllegalArgumentException("Unit ${dbUnit.namaUnit} (${dbUnit.kodeUnit}) sedang dalam ${dbUnit.statusUnit} dan tidak tersedia.")
        }
        val otherActiveParent = dao.getActiveTransactionsForUnitOnce(newUnitItem.unitId, transactionId)
        val otherActiveItems = dao.getActiveUnitItemsForUnitOnce(newUnitItem.unitId, transactionId)
        if (dbUnit.isRentedStatus() || otherActiveParent.isNotEmpty() || otherActiveItems.isNotEmpty()) {
            throw IllegalArgumentException("Unit ${dbUnit.namaUnit} sedang disewa dan tidak dapat dipilih kembali.")
        }
        if (!dbUnit.isStrictlyAvailableForNewRental()) {
            throw IllegalArgumentException("Unit ${dbUnit.namaUnit} berstatus '${dbUnit.statusUnit}' dan tidak tersedia untuk disewakan.")
        }
        if (reservationSessionId.isNotBlank() &&
            UnitReservationManager.isReservedByOtherSession(newUnitItem.unitId, reservationSessionId)
        ) {
            throw IllegalArgumentException("Unit ${dbUnit.namaUnit} sedang dipilih sementara oleh form transaksi lain.")
        }

        val now = DateCurrencyUtils.nowDateTimeDisplay()
        // If legacy transaction had 0 unit items in transaction_unit_items, backfill its original primary unit item first!
        var baseItems = existingItems
        if (baseItems.isEmpty() && parentTx.unitId > 0L) {
            val origUnit = dao.getUnitById(parentTx.unitId)
            val backfilled = TransactionUnitItemEntity(
                transactionId = transactionId,
                customerId = parentTx.customerId,
                unitId = parentTx.unitId,
                unitIndexNumber = 1,
                kodeUnitSnapshot = parentTx.kodeUnitSnapshot.ifBlank { origUnit?.kodeUnit.orEmpty() },
                namaUnitSnapshot = parentTx.namaUnitSnapshot.ifBlank { origUnit?.namaUnit.orEmpty() },
                jenisUnitSnapshot = origUnit?.jenisUnit.orEmpty(),
                merekSnapshot = origUnit?.merek.orEmpty(),
                kapasitasLiterSnapshot = origUnit?.kapasitasLiter ?: 0,
                jenisProgram = parentTx.jenisProgram,
                totalPeriodeHakMilik = parentTx.totalPeriodeHakMilik,
                pembayaranKeHakMilik = parentTx.pembayaranKeHakMilik,
                statusProgramHakMilik = parentTx.statusProgramHakMilik,
                hargaSewa = parentTx.hargaSewa,
                durasiHari = parentTx.durasiHari,
                tanggalMulaiSewa = parentTx.tanggalMulaiSewa,
                tanggalAkhirSewa = parentTx.tanggalAkhirSewa,
                tanggalJatuhTempo = parentTx.tanggalJatuhTempo,
                nominalPembayaran = parentTx.nominalPembayaran,
                statusPembayaran = parentTx.statusPembayaran,
                statusPenyewaan = parentTx.statusPenyewaan,
                createdAt = now,
                updatedAt = now
            )
            dao.insertTransactionUnitItem(backfilled)
            baseItems = dao.getTransactionUnitItemsByTxOnce(transactionId)
        }

        val nextIndex = (baseItems.maxOfOrNull { it.unitIndexNumber } ?: baseItems.size) + 1
        val start = newUnitItem.tanggalMulaiSewa.ifBlank { DateCurrencyUtils.todayIso() }
        val durasi = if (newUnitItem.durasiHari > 0) newUnitItem.durasiHari else 30
        val end = newUnitItem.tanggalAkhirSewa.ifBlank { DateCurrencyUtils.addDaysToIsoDate(start, durasi) }
        val due = newUnitItem.tanggalJatuhTempo.ifBlank { end }

        val itemToInsert = newUnitItem.copy(
            id = 0L,
            transactionId = transactionId,
            customerId = parentTx.customerId,
            unitIndexNumber = nextIndex,
            kodeUnitSnapshot = newUnitItem.kodeUnitSnapshot.ifBlank { dbUnit.kodeUnit },
            namaUnitSnapshot = newUnitItem.namaUnitSnapshot.ifBlank { dbUnit.namaUnit },
            jenisUnitSnapshot = newUnitItem.jenisUnitSnapshot.ifBlank { dbUnit.jenisUnit },
            merekSnapshot = newUnitItem.merekSnapshot.ifBlank { dbUnit.merek },
            kapasitasLiterSnapshot = if (newUnitItem.kapasitasLiterSnapshot > 0) newUnitItem.kapasitasLiterSnapshot else dbUnit.kapasitasLiter,
            tanggalMulaiSewa = start,
            durasiHari = durasi,
            tanggalAkhirSewa = end,
            tanggalJatuhTempo = due,
            storageFolderPath = newUnitItem.storageFolderPath.ifBlank {
                "SEWAIN_AJA_FREEZER/TRANSACTIONS/TX_${transactionId}/UNITS/UNIT_${dbUnit.id}"
            },
            createdAt = now,
            updatedAt = now
        )
        val insertedId = dao.insertTransactionUnitItem(itemToInsert)
        val rentedUnit = prepareUnitForSave(
            dbUnit.copy(statusUnit = "Disewa", lokasiUnit = parentTx.alamatLokasiGps.ifBlank { dbUnit.lokasiUnit }),
            dbUnit
        )
        val unitCh = MirrorOutboxCodec.computeUnitChangedFieldsJson(dbUnit, rentedUnit)
        dao.saveUnitWithOutboxAtomic(
            unit = rentedUnit,
            isNew = false,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = unitCh,
            serverTimestampMillis = currentServerTimeRef()
        )
        mirrorSyncEngine?.onUnitOutboxCommitted(rentedUnit, isNew = false, previousPhotoUri = dbUnit.fotoUnitUri)
        if (reservationSessionId.isNotBlank()) {
            UnitReservationManager.releaseUnit(reservationSessionId, dbUnit.id)
        }

        recalculateParentTransactionFromUnitItems(transactionId)
        logCustomerTimelineEvent(
            customerId = parentTx.customerId,
            transactionId = transactionId,
            unitId = dbUnit.id,
            eventType = "TAMBAH_UNIT",
            title = "Tambah Unit ke Kontrak ${parentTx.nomorInvoice}",
            description = "${dbUnit.namaUnit} (${dbUnit.kodeUnit}) • ${itemToInsert.jenisProgram} • Jatuh tempo: ${itemToInsert.tanggalJatuhTempo}",
            amount = itemToInsert.hargaSewa
        )
        return itemToInsert.copy(id = insertedId)
    }

    /**
     * Updates a single unit item (e.g. changing due date, rental price, program, or photos of Unit #2)
     * without affecting any other unit in the transaction.
     */
    suspend fun updateSingleUnitInTransaction(
        updatedUnitItem: TransactionUnitItemEntity
    ): TransactionUnitItemEntity {
        val now = DateCurrencyUtils.nowDateTimeDisplay()
        val statusBayarUnit = when {
            updatedUnitItem.hargaSewa > 0L && updatedUnitItem.nominalPembayaran >= updatedUnitItem.hargaSewa -> "Lunas"
            updatedUnitItem.nominalPembayaran > 0L -> "Dibayar Sebagian"
            else -> updatedUnitItem.statusPembayaran.ifBlank { "Belum Bayar" }
        }
        val stamped = updatedUnitItem.copy(
            statusPembayaran = statusBayarUnit,
            updatedAt = now
        )
        dao.updateTransactionUnitItem(stamped)
        if (stamped.transactionId > 0L) {
            recalculateParentTransactionFromUnitItems(stamped.transactionId)
        }
        return stamped
    }

    suspend fun recalculateParentTransactionFromUnitItems(transactionId: Long) {
        val parentTx = dao.getTransactionById(transactionId) ?: return
        val allItems = dao.getTransactionUnitItemsByTxOnce(transactionId)
        if (allItems.isEmpty()) return

        val summary = calculateMultiUnitBillingSummary(
            unitItems = allItems,
            ongkir = parentTx.ongkir,
            deposit = parentTx.deposit,
            diskon = parentTx.diskon,
            overrideNominalPembayaran = null
        )
        val firstUnit = allItems.first()
        val combinedNamaUnit = if (allItems.size == 1) {
            firstUnit.namaUnitSnapshot
        } else {
            "${allItems.size} Unit (${allItems.joinToString(", ") { it.namaUnitSnapshot }})"
        }
        val combinedKodeUnit = allItems.joinToString(", ") { it.kodeUnitSnapshot }
        val distinctPrograms = allItems.map { it.jenisProgram }.distinct()
        val firstHakMilikUnit = allItems.firstOrNull { it.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true) }
        val combinedProgram = when {
            distinctPrograms.size == 1 -> distinctPrograms.first()
            firstHakMilikUnit != null -> "Sewa Hak Milik"
            else -> firstUnit.jenisProgram.ifBlank { parentTx.jenisProgram }
        }
        val aggStatusBayar = when {
            summary.totalTagihan > 0L && summary.totalPembayaran >= summary.totalTagihan -> "Lunas"
            summary.totalPembayaran > 0L -> "Dibayar Sebagian"
            else -> parentTx.statusPembayaran
        }
        val updatedParentTx = prepareTransactionForSave(
            incoming = parentTx.copy(
                namaUnitSnapshot = combinedNamaUnit,
                kodeUnitSnapshot = combinedKodeUnit,
                jenisProgram = combinedProgram,
                totalPeriodeHakMilik = firstHakMilikUnit?.totalPeriodeHakMilik ?: 0,
                pembayaranKeHakMilik = firstHakMilikUnit?.pembayaranKeHakMilik ?: 1,
                statusProgramHakMilik = firstHakMilikUnit?.statusProgramHakMilik ?: "Aktif",
                hargaSewa = summary.totalHargaSewaUnits,
                totalTagihan = summary.totalTagihan,
                nominalPembayaran = summary.totalPembayaran,
                statusPembayaran = aggStatusBayar
            ),
            existing = parentTx
        )
        val txCh = MirrorOutboxCodec.computeTransactionChangedFieldsJson(parentTx, updatedParentTx)
        dao.saveTransactionWithOutboxAtomic(
            transaction = updatedParentTx,
            isNew = false,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = txCh,
            serverTimestampMillis = currentServerTimeRef()
        )
        mirrorSyncEngine?.onTransactionOutboxCommitted(updatedParentTx, isNew = false)
    }

    suspend fun saveTransactionSignatureRecord(signature: TransactionSignatureEntity): Long {
        val now = DateCurrencyUtils.nowDateTimeDisplay()
        val tx = dao.getTransactionById(signature.transactionId)
        val officerLocked = tx != null && (
            tx.officerSnapshotLocked ||
                tx.namaPetugas.isNotBlank() ||
                tx.tandaTanganPetugasUri.isNotBlank()
            )
        if (signature.signerRole == "PETUGAS_SAF" && officerLocked) {
            // Permanent officer snapshot protection: refuse silent overwrite outside audited correction
            return 0L
        }
        dao.deleteTransactionSignatureByRole(signature.transactionId, signature.signerRole)
        val savedId = dao.insertTransactionSignature(
            signature.copy(
                id = 0L,
                signedDate = signature.signedDate.ifBlank { DateCurrencyUtils.todayIso() },
                createdAt = signature.createdAt.ifBlank { now }
            )
        )
        if (tx != null) {
            val updatedTx = if (signature.signerRole == "PENYEWA") {
                prepareTransactionForSave(
                    tx.copy(
                        tandaTanganPelangganUri = signature.filePath,
                        namaPenerima = signature.signerName.ifBlank { tx.namaPenerima }
                    ),
                    tx
                )
            } else if (signature.signerRole == "PETUGAS_SAF") {
                prepareTransactionForSave(
                    tx.copy(
                        tandaTanganPetugasUri = signature.filePath,
                        namaPetugas = signature.signerName.ifBlank { tx.namaPetugas },
                        officerSnapshotLocked = true,
                        officerSignedAt = tx.officerSignedAt.ifBlank { now }
                    ),
                    tx
                )
            } else null
            if (updatedTx != null) {
                val sigCh = MirrorOutboxCodec.computeTransactionChangedFieldsJson(tx, updatedTx)
                dao.saveTransactionWithOutboxAtomic(
                    transaction = updatedTx,
                    isNew = false,
                    datasetId = currentDatasetId(),
                    datasetGenerationId = currentDatasetGenerationId(),
                    changedFieldsJson = sigCh,
                    serverTimestampMillis = currentServerTimeRef()
                )
                mirrorSyncEngine?.onTransactionOutboxCommitted(updatedTx, isNew = false)
            }
        }
        return savedId
    }

    /**
     * Soft-deletes a transaction with Tombstone & Google Drive Outbox integration while preserving
     * customer records, unrelated transactions, and applying Smart Unit Status Restoration:
     * - Restores unit to "Tersedia" ONLY IF the unit has NO other active transaction AND is NOT "Hilang" / "Perbaikan".
     * - Syncs both the transaction deletion (OP_DELETE) and any unit status updates (OP_UPDATE) to Google Drive.
     */
    suspend fun deleteTransaction(
        transaction: RentalTransactionEntity,
        deletedBy: String = ""
    ) {
        val freshTx = dao.getTransactionByIdIncludingDeleted(transaction.id) ?: transaction
        val (unitItems, relatedDriveFileIds) = dao.deleteTransactionWithOutboxAtomic(
            transaction = freshTx,
            datasetGenerationId = currentDatasetGenerationId(),
            serverTimestampMillis = currentServerTimeRef(),
            deletedBy = deletedBy
        )
        val affectedUnitIds = buildSet {
            if (freshTx.unitId > 0L) add(freshTx.unitId)
            unitItems.forEach { if (it.unitId > 0L) add(it.unitId) }
        }
        for (uId in affectedUnitIds) {
            restoreUnitStatusIfEligible(unitId = uId, excludingTransactionId = freshTx.id)
        }
        mirrorSyncEngine?.onTransactionDeletedOutboxCommitted(freshTx, relatedDriveFileIds = relatedDriveFileIds)
    }

    /**
     * Cancels a transaction (statusPenyewaan = "Dibatalkan"), updates SQLite + Drive Outbox,
     * and applies Smart Unit Status Restoration across all attached units.
     */
    suspend fun cancelTransaction(
        transaction: RentalTransactionEntity,
        reason: String = ""
    ): RentalTransactionEntity? {
        val existingTx = dao.getTransactionById(transaction.id) ?: return null
        val unitItems = dao.getTransactionUnitItemsByTxOnce(existingTx.id)
        val now = DateCurrencyUtils.nowDateTimeDisplay()
        val updatedCatatan = if (reason.isNotBlank()) {
            listOf(existingTx.catatan, "Dibatalkan ($now): $reason").filter { it.isNotBlank() }.joinToString(" | ")
        } else {
            existingTx.catatan
        }
        val cancelledTx = prepareTransactionForSave(
            incoming = existingTx.copy(
                statusPenyewaan = "Dibatalkan",
                catatan = updatedCatatan
            ),
            existing = existingTx
        )
        val txCh = MirrorOutboxCodec.computeTransactionChangedFieldsJson(existingTx, cancelledTx)
        dao.saveTransactionWithOutboxAtomic(
            transaction = cancelledTx,
            isNew = false,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = txCh,
            serverTimestampMillis = currentServerTimeRef()
        )
        for (item in unitItems) {
            dao.updateTransactionUnitItem(item.copy(statusPenyewaan = "Dibatalkan", updatedAt = now))
        }
        val affectedUnitIds = buildSet {
            if (existingTx.unitId > 0L) add(existingTx.unitId)
            unitItems.forEach { if (it.unitId > 0L) add(it.unitId) }
        }
        for (uId in affectedUnitIds) {
            restoreUnitStatusIfEligible(unitId = uId, excludingTransactionId = existingTx.id)
        }
        val finalTx = dao.getTransactionById(existingTx.id) ?: cancelledTx
        mirrorSyncEngine?.onTransactionOutboxCommitted(finalTx, isNew = false)
        return finalTx
    }

    suspend fun generateUniqueReturnDocNumber(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        var counter = dao.getReturnCount() + 1
        while (true) {
            val candidate = String.format("RET/SAF/%d/%04d", year, counter)
            val existing = dao.getReturnByDocNumber(candidate)
            if (existing == null) {
                return candidate
            }
            counter++
        }
    }

    /**
     * Section 3 & 4: Process Freezer Return (Serah Terima Pengembalian Freezer)
     * - Automatically changes transaction status to SELESAI ("Selesai") without deleting old transaction history
     * - Separates transaction status from unit status:
     *   - If condition is "Baik / Normal" or "Baik dengan catatan" -> unit status becomes "Tersedia"
     *   - If condition is "Rusak ringan", "Rusak berat", or "Perlu pemeriksaan teknisi" -> unit status becomes "Dalam Perbaikan"
     * - Saves all return documentation photos, customer & officer digital signatures, and return date/time
     */
    suspend fun processFreezerReturn(returnEntity: FreezerReturnEntity): FreezerReturnEntity {
        val docNo = if (returnEntity.nomorDokumenPengembalian.isBlank()) {
            generateUniqueReturnDocNumber()
        } else {
            returnEntity.nomorDokumenPengembalian
        }

        val isDamagedOrNeedsTech = returnEntity.kondisiUnitSaatKembali.contains("Rusak", ignoreCase = true) ||
            returnEntity.kondisiUnitSaatKembali.contains("teknisi", ignoreCase = true) ||
            returnEntity.kondisiUnitSaatKembali.contains("Perbaikan", ignoreCase = true)

        val targetUnitStatus = if (isDamagedOrNeedsTech) "Dalam Perbaikan" else "Tersedia"

        val finalReturn = returnEntity.copy(
            nomorDokumenPengembalian = docNo,
            statusUnitSetelahKembali = targetUnitStatus
        )

        val returnId = if (finalReturn.id == 0L) {
            dao.insertReturn(finalReturn)
        } else {
            dao.updateReturn(finalReturn)
            finalReturn.id
        }

        // Update linked transaction status to Selesai (disconnecting active rental while preserving full history)
        val tx = dao.getTransactionById(finalReturn.transactionId)
        if (tx != null) {
            val updatedCatatan = buildString {
                if (tx.catatan.isNotBlank()) append(tx.catatan).append(" | ")
                append("Dikembalikan pada ${finalReturn.tanggalPengembalian} ${finalReturn.jamPengembalian} ($docNo - Kondisi: ${finalReturn.kondisiUnitSaatKembali})")
            }
            val updatedTx = prepareTransactionForSave(
                tx.copy(
                    statusPenyewaan = "Selesai",
                    catatan = updatedCatatan
                ),
                tx
            )
            val txCh = MirrorOutboxCodec.computeTransactionChangedFieldsJson(tx, updatedTx)
            dao.saveTransactionWithOutboxAtomic(
                transaction = updatedTx,
                isNew = false,
                datasetId = currentDatasetId(),
                datasetGenerationId = currentDatasetGenerationId(),
                changedFieldsJson = txCh,
                serverTimestampMillis = currentServerTimeRef()
            )
            mirrorSyncEngine?.onTransactionOutboxCommitted(updatedTx, isNew = false)
        }

        // Update linked freezer unit status (Tersedia vs Dalam Perbaikan) & linked TransactionUnitItemEntity
        val txUnitItems = dao.getTransactionUnitItemsByTxOnce(finalReturn.transactionId)
        txUnitItems.filter { it.unitId == finalReturn.unitId }.forEach { unitItem ->
            dao.updateTransactionUnitItem(
                unitItem.copy(
                    statusPenyewaan = "Selesai",
                    updatedAt = DateCurrencyUtils.nowDateTimeDisplay()
                )
            )
        }

        val unit = dao.getUnitById(finalReturn.unitId)
        if (unit != null) {
            val updatedKeterangan = if (finalReturn.catatanKondisiUnit.isNotBlank()) {
                "Pengembalian (${finalReturn.tanggalPengembalian}): ${finalReturn.kondisiUnitSaatKembali} - ${finalReturn.catatanKondisiUnit}"
            } else {
                unit.keterangan
            }
            val updatedUnit = prepareUnitForSave(
                unit.copy(
                    statusUnit = targetUnitStatus,
                    lokasiUnit = "Gudang Utama SEWAin Aja Freezer SOC, Solo",
                    kondisiUnit = finalReturn.kondisiUnitSaatKembali,
                    keterangan = updatedKeterangan
                ),
                unit
            )
            val uCh = MirrorOutboxCodec.computeUnitChangedFieldsJson(unit, updatedUnit)
            dao.saveUnitWithOutboxAtomic(
                unit = updatedUnit,
                isNew = false,
                datasetId = currentDatasetId(),
                datasetGenerationId = currentDatasetGenerationId(),
                changedFieldsJson = uCh,
                serverTimestampMillis = currentServerTimeRef()
            )
            mirrorSyncEngine?.onUnitOutboxCommitted(updatedUnit, isNew = false, previousPhotoUri = unit.fotoUnitUri)
        }

        logCustomerTimelineEvent(
            customerId = finalReturn.customerId,
            transactionId = finalReturn.transactionId,
            unitId = finalReturn.unitId,
            eventType = "PENGEMBALIAN",
            title = "Pengembalian Unit ${finalReturn.kodeUnitSnapshot}",
            description = "Dokumen: $docNo • Kondisi: ${finalReturn.kondisiUnitSaatKembali} • Status unit otomatis: $targetUnitStatus",
            amount = 0L
        )

        return finalReturn.copy(id = returnId)
    }

    suspend fun updateReturnRecord(returnEntity: FreezerReturnEntity) {
        dao.updateReturn(returnEntity)
    }

    /**
     * Section 6: Extend Rental in Days (Perpanjangan Sewa dalam Hari)
     * - Updates end date reference & marks old transaction linked
     * - Creates extension history record
     * - Creates a new transaction with a new invoice & billing without deleting previous transaction
     */
    suspend fun extendRentalByDays(
        oldTransaction: RentalTransactionEntity,
        additionalDays: Int,
        biayaSewaPerpanjangan: Long,
        ongkirTambahan: Long,
        nominalDibayarSekarang: Long,
        catatanPerpanjangan: String
    ): RentalTransactionEntity {
        val newInvoice = generateUniqueInvoiceNumber()
        val newStartDate = oldTransaction.tanggalAkhirSewa
        val safeDays = additionalDays.coerceAtLeast(1)
        val newEndDate = DateCurrencyUtils.addDaysToIsoDate(newStartDate, safeDays)
        val totalTagihanBaru = (biayaSewaPerpanjangan + ongkirTambahan).coerceAtLeast(0L)
        val statusBayarBaru = when {
            nominalDibayarSekarang >= totalTagihanBaru -> "Lunas"
            nominalDibayarSekarang > 0L -> "Dibayar Sebagian"
            else -> "Belum Bayar"
        }

        // Keep old transaction intact and append extension reference
        val updatedOldTx = prepareTransactionForSave(
            oldTransaction.copy(
                catatan = (oldTransaction.catatan + " | Diperpanjang $safeDays hari ke $newInvoice").trim(' ', '|')
            ),
            oldTransaction
        )
        val oldTxCh = MirrorOutboxCodec.computeTransactionChangedFieldsJson(oldTransaction, updatedOldTx)
        dao.saveTransactionWithOutboxAtomic(
            transaction = updatedOldTx,
            isNew = false,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = oldTxCh,
            serverTimestampMillis = currentServerTimeRef()
        )
        mirrorSyncEngine?.onTransactionOutboxCommitted(updatedOldTx, isNew = false)

        val approxMonths = ceil(safeDays / 30.0).toInt().coerceAtLeast(1)

        val isHakMilik = oldTransaction.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
        val totalPeriode = if (isHakMilik) oldTransaction.totalPeriodeHakMilik.coerceAtLeast(12) else 0
        val nextPembayaranKe = if (isHakMilik) {
            (oldTransaction.pembayaranKeHakMilik + 1).coerceAtMost(totalPeriode)
        } else {
            1
        }
        val nextStatusProgram = if (isHakMilik) {
            when {
                nextPembayaranKe >= totalPeriode && statusBayarBaru == "Lunas" -> "Selesai"
                statusBayarBaru == "Lunas" -> "Lunas"
                else -> "Aktif"
            }
        } else {
            "Aktif"
        }

        val newTransaction = prepareTransactionForSave(
            incoming = oldTransaction.copy(
                id = 0L,
                uuid = java.util.UUID.randomUUID().toString(),
                version = 1L,
                nomorInvoice = newInvoice,
                tanggalPemesanan = DateCurrencyUtils.todayIso(),
                hargaSewa = biayaSewaPerpanjangan,
                durasiSewaBulan = approxMonths,
                durasiHari = safeDays,
                tanggalMulaiSewa = newStartDate,
                tanggalAkhirSewa = newEndDate,
                tanggalJatuhTempo = newEndDate,
                nominalPembayaran = nominalDibayarSekarang,
                ongkir = ongkirTambahan,
                deposit = 0L,
                diskon = 0L,
                totalTagihan = totalTagihanBaru,
                statusPembayaran = statusBayarBaru,
                statusPenyewaan = if (isHakMilik && nextStatusProgram == "Selesai") "Selesai" else "Dalam Penyewaan",
                catatan = "Perpanjangan $safeDays Hari dari ${oldTransaction.nomorInvoice}. $catatanPerpanjangan".trim(),
                pdfInvoicePath = "",
                parentTransactionId = oldTransaction.id,
                jenisProgram = oldTransaction.jenisProgram,
                totalPeriodeHakMilik = totalPeriode,
                pembayaranKeHakMilik = nextPembayaranKe,
                statusProgramHakMilik = nextStatusProgram
            ),
            existing = null
        )

        val newTxCh = MirrorOutboxCodec.computeTransactionChangedFieldsJson(null, newTransaction)
        val newTransId = dao.saveTransactionWithOutboxAtomic(
            transaction = newTransaction,
            isNew = true,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = newTxCh,
            serverTimestampMillis = currentServerTimeRef()
        )
        val created = dao.getTransactionById(newTransId) ?: newTransaction.copy(id = newTransId)
        mirrorSyncEngine?.onTransactionOutboxCommitted(created, isNew = true)

        dao.insertExtension(
            RentalExtensionHistoryEntity(
                originalTransactionId = oldTransaction.id,
                newTransactionId = newTransId,
                nomorInvoiceLama = oldTransaction.nomorInvoice,
                nomorInvoiceBaru = newInvoice,
                tanggalPerpanjangan = DateCurrencyUtils.todayIso(),
                tanggalAkhirLama = oldTransaction.tanggalAkhirSewa,
                tanggalAkhirBaru = newEndDate,
                tambahanDurasiHari = safeDays,
                tagihanBaru = totalTagihanBaru,
                catatan = catatanPerpanjangan
            )
        )
        return created
    }

    /**
     * Resets database data (with option to either clear everything to empty or restore default Solo Raya dataset).
     */
    suspend fun resetAllDatabaseData(reseedDefaultSoloRayaData: Boolean) {
        dao.clearAllCoreAndSyncTablesForFullReset(
            keepTombstones = false,
            clearLocalBackupHistory = false
        )
        if (reseedDefaultSoloRayaData) {
            seedSoloRayaDataInternal()
        } else {
            ensureDefaultPaymentMethodsSeeded()
        }
        logSync(
            jenisOperasi = "RESET DATA",
            sheetName = if (reseedDefaultSoloRayaData) "RESET KE DATA AWAL SOLO RAYA" else "HAPUS SELURUH DATA (KOSONG)",
            berhasil = 1,
            gagal = 0,
            status = "SUKSES",
            detail = if (reseedDefaultSoloRayaData) "Seluruh data direset ke data awal operasional Solo Raya." else "Seluruh data transaksi, pelanggan, dan unit telah dikosongkan."
        )
    }

    suspend fun resetTransactionsOnly() {
        val existingProofs = dao.getAllPaymentProofsOnce()
        val existingTx = dao.getAllTransactionsOnce()
        existingProofs.forEach { p ->
            dao.deletePaymentProofWithOutboxAtomic(
                proof = p,
                datasetGenerationId = currentDatasetGenerationId(),
                serverTimestampMillis = currentServerTimeRef()
            )
        }
        dao.deleteAllReturns()
        dao.deleteAllExtensions()
        existingTx.forEach { t ->
            dao.deleteTransactionWithOutboxAtomic(
                transaction = t,
                datasetGenerationId = currentDatasetGenerationId(),
                serverTimestampMillis = currentServerTimeRef()
            )
        }
        // Set all rented units back to Tersedia
        val units = dao.getAllUnitsOnce()
        units.forEach { u ->
            if (u.statusUnit == "Disewa") {
                val updatedUnit = prepareUnitForSave(u.copy(statusUnit = "Tersedia"), u)
                val uCh = MirrorOutboxCodec.computeUnitChangedFieldsJson(u, updatedUnit)
                dao.saveUnitWithOutboxAtomic(
                    unit = updatedUnit,
                    isNew = false,
                    datasetId = currentDatasetId(),
                    datasetGenerationId = currentDatasetGenerationId(),
                    changedFieldsJson = uCh,
                    serverTimestampMillis = currentServerTimeRef()
                )
            }
        }
        mirrorSyncEngine?.onOutboxCommitted()
        logSync(
            jenisOperasi = "RESET TRANSAKSI",
            sheetName = "TRANSAKSI SEWA",
            berhasil = 1,
            gagal = 0,
            status = "SUKSES",
            detail = "Seluruh riwayat transaksi sewa dihapus, data unit & pelanggan tetap dipertahankan."
        )
    }

    suspend fun logSync(
        jenisOperasi: String,
        sheetName: String,
        berhasil: Int,
        gagal: Int,
        status: String,
        detail: String
    ) {
        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = DateCurrencyUtils.nowDateTimeDisplay(),
                jenisOperasi = jenisOperasi,
                sheetName = sheetName,
                jumlahBerhasil = berhasil,
                jumlahGagal = gagal,
                status = status,
                detailPesan = detail
            )
        )
    }

    suspend fun ensureDefaultPaymentMethodsSeeded() {
        if (dao.getPaymentMethodCount() > 0) return
        val defaults = listOf(
            PaymentMethodEntity(
                namaMetode = "Transfer BCA",
                kategoriMetode = "TRANSFER_BANK",
                namaBankAtauPenyedia = "Bank Central Asia (BCA)",
                nomorRekening = "0154889921",
                namaPemilikRekening = "SEWAIN AJA FREEZER SOC",
                keteranganPembayaran = "Harap unggah bukti transfer BCA setelah pembayaran",
                isAktif = true
            ),
            PaymentMethodEntity(
                namaMetode = "Transfer BRI",
                kategoriMetode = "TRANSFER_BANK",
                namaBankAtauPenyedia = "Bank Rakyat Indonesia (BRI)",
                nomorRekening = "009701003456561",
                namaPemilikRekening = "SEWAIN AJA FREEZER SOC",
                keteranganPembayaran = "Konfirmasi bukti transfer BRI ke Admin Solo Raya",
                isAktif = true
            ),
            PaymentMethodEntity(
                namaMetode = "Transfer Mandiri",
                kategoriMetode = "TRANSFER_BANK",
                namaBankAtauPenyedia = "Bank Mandiri",
                nomorRekening = "1380019283746",
                namaPemilikRekening = "SEWAIN AJA FREEZER SOC",
                keteranganPembayaran = "Rekening operasional Bank Mandiri Cab. Slamet Riyadi Solo",
                isAktif = true
            ),
            PaymentMethodEntity(
                namaMetode = "Transfer BNI",
                kategoriMetode = "TRANSFER_BANK",
                namaBankAtauPenyedia = "Bank Negara Indonesia (BNI)",
                nomorRekening = "0882345619",
                namaPemilikRekening = "SEWAIN AJA FREEZER SOC",
                keteranganPembayaran = "Transfer BNI operasional persewaan freezer",
                isAktif = true
            ),
            PaymentMethodEntity(
                namaMetode = "QRIS",
                kategoriMetode = "QRIS",
                namaBankAtauPenyedia = "QRIS Nasional (All E-Wallet & Mobile Banking)",
                nomorRekening = "NMID: ID2026091882991",
                namaPemilikRekening = "SEWAIN AJA FREEZER SOC",
                keteranganPembayaran = "Scan QRIS melalui Mobile Banking / GoPay / OVO / Dana / ShopeePay",
                isAktif = true
            ),
            PaymentMethodEntity(
                namaMetode = "Cash / Tunai",
                kategoriMetode = "TUNAI",
                namaBankAtauPenyedia = "Pembayaran Tunai di Lokasi / Kantor SAF",
                nomorRekening = "",
                namaPemilikRekening = "Petugas Serah Terima / Kasir SAF",
                keteranganPembayaran = "Dibayarkan tunai saat pengantaran unit atau di kantor Solo",
                isAktif = true
            )
        )
        defaults.forEach { dao.insertPaymentMethod(it) }
    }

    /**
     * Disabled per zero-remnant Hard Reset directive:
     * Never auto-seed sample units, dummy customers, or sample transactions on startup or after wipe.
     */
    suspend fun ensureInitialDataSeeded() {
        // Intentionally empty: app must start in a 100% clean, empty state with zero sample data.
        return
    }

    private suspend fun seedSoloRayaDataInternal() {
        ensureDefaultPaymentMethodsSeeded()
        val today = DateCurrencyUtils.todayIso()
        val tomorrow = DateCurrencyUtils.addDaysToIsoDate(today, 1)
        val in3Days = DateCurrencyUtils.addDaysToIsoDate(today, 3)
        val in7Days = DateCurrencyUtils.addDaysToIsoDate(today, 7)
        val overdue2Days = DateCurrencyUtils.addDaysToIsoDate(today, -2)
        val in37Days = DateCurrencyUtils.addDaysToIsoDate(today, 37)

        val units = listOf(
            FreezerUnitEntity(
                kodeUnit = "SAF-CF-001",
                namaUnit = "RSA CF-310 Chest Freezer 300L",
                jenisUnit = "Chest Freezer",
                owner = "Investasi Internal SAF",
                hargaPembelian = 3450000L,
                kapasitasLiter = 300,
                cicilanPerBulan = 350000L,
                jumlahCicilanTotal = 10,
                totalPembayaranCicilan = 3500000L,
                statusInvestasi = "LUNAS",
                kategoriSheet = "INVESTASI SEWA FREEZER",
                merek = "RSA",
                model = "CF-310",
                nomorSeri = "SN-RSA310-8821",
                statusUnit = "Disewa",
                lokasiUnit = "Jl. Slamet Riyadi No. 214, Laweyan, Surakarta",
                tanggalPembelian = "2025-06-15",
                keterangan = "Kondisi prima, karet pintu rapat, dingin -24C"
            ),
            FreezerUnitEntity(
                kodeUnit = "SAF-CF-002",
                namaUnit = "GEA AB-226R Chest Freezer 220L",
                jenisUnit = "Chest Freezer",
                owner = "Mitra Investor - Bpk. Hendra",
                hargaPembelian = 2950000L,
                kapasitasLiter = 220,
                cicilanPerBulan = 300000L,
                jumlahCicilanTotal = 10,
                totalPembayaranCicilan = 1800000L,
                statusInvestasi = "DIBAYAR SEBAGIAN",
                kategoriSheet = "INVESTASI SEWA FREEZER",
                merek = "GEA",
                model = "AB-226R",
                nomorSeri = "SN-GEA226-4419",
                statusUnit = "Disewa",
                lokasiUnit = "Jl. Adi Sucipto No. 88, Colomadu, Karanganyar",
                tanggalPembelian = "2025-09-10",
                keterangan = "Unit sewa UMKM Frozen Food Colomadu"
            ),
            FreezerUnitEntity(
                kodeUnit = "SAF-SC-003",
                namaUnit = "Polytron SCN-230 Showcase Cooler",
                jenisUnit = "Showcase",
                owner = "Investasi Internal SAF",
                hargaPembelian = 3650000L,
                kapasitasLiter = 230,
                cicilanPerBulan = 3650000L / 10,
                jumlahCicilanTotal = 10,
                totalPembayaranCicilan = 3650000L,
                statusInvestasi = "DONE",
                kategoriSheet = "INVESTASI FREEZER POINT FROZEN",
                merek = "Polytron",
                model = "SCN-230",
                nomorSeri = "SN-PLT230-9912",
                statusUnit = "Disewa",
                lokasiUnit = "Jl. Ir. Soekarno No. 45, Solo Baru, Sukoharjo",
                tanggalPembelian = "2025-07-01",
                keterangan = "Lampu LED showcase terang, rak lengkap 4 susun"
            ),
            FreezerUnitEntity(
                kodeUnit = "SAF-ASI-004",
                namaUnit = "Aqua AQF-120FR Freezer ASI 4 Laci",
                jenisUnit = "Freezer ASI",
                owner = "Mitra Investor - Ibu Yeni",
                hargaPembelian = 2350000L,
                kapasitasLiter = 120,
                cicilanPerBulan = 250000L,
                jumlahCicilanTotal = 10,
                totalPembayaranCicilan = 2000000L,
                statusInvestasi = "DIBAYAR SEBAGIAN",
                kategoriSheet = "INVESTASI SEWA FREEZER",
                merek = "Aqua",
                model = "AQF-120FR",
                nomorSeri = "SN-AQA120-3104",
                statusUnit = "Disewa",
                lokasiUnit = "Perum Gentan Citra Indah Blok B4, Baki, Sukoharjo",
                tanggalPembelian = "2026-01-20",
                keterangan = "Khusus penyimpanan ASI eksklusif, higienis"
            ),
            FreezerUnitEntity(
                kodeUnit = "SAF-CF-005",
                namaUnit = "Modena MD-0500W Chest Freezer 500L",
                jenisUnit = "Chest Freezer",
                owner = "Point Frozen Solo",
                hargaPembelian = 5800000L,
                kapasitasLiter = 500,
                cicilanPerBulan = 580000L,
                jumlahCicilanTotal = 10,
                totalPembayaranCicilan = 2900000L,
                statusInvestasi = "LIST",
                kategoriSheet = "INVESTASI FREEZER POINT FROZEN",
                merek = "Modena",
                model = "MD-0500W",
                nomorSeri = "SN-MDN500-7710",
                statusUnit = "Tersedia",
                lokasiUnit = "Gudang Utama SEWAin Aja Freezer SOC, Banjarsari, Solo",
                tanggalPembelian = "2026-02-12",
                keterangan = "Siap kirim, kapasitas besar 2 pintu angkat"
            ),
            FreezerUnitEntity(
                kodeUnit = "SAF-CF-006",
                namaUnit = "RSA CF-110 Chest Freezer 100L",
                jenisUnit = "Chest Freezer",
                owner = "Investasi Internal SAF",
                hargaPembelian = 2150000L,
                kapasitasLiter = 100,
                cicilanPerBulan = 215000L,
                jumlahCicilanTotal = 10,
                totalPembayaranCicilan = 2150000L,
                statusInvestasi = "LUNAS",
                kategoriSheet = "INVESTASI SEWA FREEZER",
                merek = "RSA",
                model = "CF-110",
                nomorSeri = "SN-RSA110-1198",
                statusUnit = "Maintenance",
                lokasiUnit = "Workshop Teknis SAF, Kartasura",
                tanggalPembelian = "2025-05-05",
                keterangan = "Pengecekan berkala thermostat dan pembersihan kondensor"
            )
        )

        val unitIds = units.map { dao.insertUnit(it) }

        val customers = listOf(
            CustomerEntity(
                kodePelanggan = "CUST/SAF/0001",
                namaLengkap = "Budi Santoso (Agen Point Frozen Laweyan)",
                nik = "3372011405880001",
                nomorHp = "081226345678",
                alamatLengkap = "Jl. Slamet Riyadi No. 214, Kec. Laweyan, Kota Surakarta",
                jenisKelamin = "Laki-laki",
                tempatLahir = "SURAKARTA",
                tanggalLahir = "14-05-1988",
                rtRw = "002/007",
                kelurahanDesa = "SONDAKAN",
                kecamatan = "LAWEYAN",
                agama = "ISLAM",
                statusPerkawinan = "KAWIN",
                pekerjaan = "WIRASWASTA",
                kewarganegaraan = "WNI",
                catatan = "Pelanggan langganan tepat waktu, menyewa lebih dari 1 unit saat event",
                tanggalRegistrasi = "2025-08-01",
                lokasiPenggunaanDefault = "Jl. Slamet Riyadi No. 214, Laweyan, Surakarta",
                latitudeDefault = -7.5695,
                longitudeDefault = 110.8021
            ),
            CustomerEntity(
                kodePelanggan = "CUST/SAF/0002",
                namaLengkap = "Siti Rahmawati (Dapur Frozen Colomadu)",
                nik = "3313125509910003",
                nomorHp = "081390876543",
                alamatLengkap = "Jl. Adi Sucipto No. 88, Malangjiwan, Colomadu, Karanganyar",
                jenisKelamin = "Perempuan",
                tempatLahir = "KARANGANYAR",
                tanggalLahir = "15-09-1991",
                rtRw = "004/003",
                kelurahanDesa = "MALANGJIWAN",
                kecamatan = "COLOMADU",
                agama = "ISLAM",
                statusPerkawinan = "KAWIN",
                pekerjaan = "PEDAGANG",
                kewarganegaraan = "WNI",
                catatan = "Toko frozen food depan jalan raya",
                tanggalRegistrasi = "2025-10-12",
                lokasiPenggunaanDefault = "Jl. Adi Sucipto No. 88, Colomadu, Karanganyar",
                latitudeDefault = -7.5378,
                longitudeDefault = 110.7615
            ),
            CustomerEntity(
                kodePelanggan = "CUST/SAF/0003",
                namaLengkap = "Andi Prasetyo (Minuman & Es Kristal Solo Baru)",
                nik = "3311092203900002",
                nomorHp = "085725112233",
                alamatLengkap = "Jl. Ir. Soekarno No. 45, Madegondo, Grogol, Solo Baru, Sukoharjo",
                jenisKelamin = "Laki-laki",
                tempatLahir = "SUKOHARJO",
                tanggalLahir = "22-03-1990",
                rtRw = "001/005",
                kelurahanDesa = "MADEGONDO",
                kecamatan = "GROGOL",
                agama = "ISLAM",
                statusPerkawinan = "BELUM KAWIN",
                pekerjaan = "WIRASWASTA",
                kewarganegaraan = "WNI",
                catatan = "Sewa showcase untuk display minuman dingin",
                tanggalRegistrasi = "2026-01-15",
                lokasiPenggunaanDefault = "Jl. Ir. Soekarno No. 45, Solo Baru, Sukoharjo",
                latitudeDefault = -7.6012,
                longitudeDefault = 110.8156
            ),
            CustomerEntity(
                kodePelanggan = "CUST/SAF/0004",
                namaLengkap = "dr. Nadia Kusuma",
                nik = "3311124811940004",
                nomorHp = "082134567890",
                alamatLengkap = "Perum Gentan Citra Indah Blok B4, Baki, Sukoharjo",
                jenisKelamin = "Perempuan",
                tempatLahir = "SURAKARTA",
                tanggalLahir = "08-11-1994",
                rtRw = "003/009",
                kelurahanDesa = "GENTAN",
                kecamatan = "BAKI",
                agama = "KRISTEN",
                statusPerkawinan = "KAWIN",
                pekerjaan = "DOKTER",
                kewarganegaraan = "WNI",
                catatan = "Penyewaan Freezer ASI 90 hari",
                tanggalRegistrasi = "2026-03-01",
                lokasiPenggunaanDefault = "Perum Gentan Citra Indah Blok B4, Baki, Sukoharjo",
                latitudeDefault = -7.5844,
                longitudeDefault = 110.7798
            )
        )

        val custIds = customers.map { dao.insertCustomer(it) }

        val transactions = listOf(
            RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0001",
                tanggalPemesanan = DateCurrencyUtils.addDaysToIsoDate(today, -30),
                customerId = custIds[0],
                namaPelangganSnapshot = customers[0].namaLengkap,
                unitId = unitIds[0],
                namaUnitSnapshot = units[0].namaUnit,
                kodeUnitSnapshot = units[0].kodeUnit,
                hargaSewa = 300000L,
                durasiSewaBulan = 1,
                durasiHari = 30,
                tanggalMulaiSewa = DateCurrencyUtils.addDaysToIsoDate(today, -30),
                tanggalAkhirSewa = today,
                tanggalJatuhTempo = today,
                nominalPembayaran = 350000L,
                ongkir = 50000L,
                deposit = 100000L,
                diskon = 0L,
                totalTagihan = 450000L,
                statusPembayaran = "Dibayar Sebagian",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA",
                catatan = "Jatuh tempo hari ini, konfirmasi perpanjangan WA",
                latitude = -7.5695,
                longitude = 110.8021,
                alamatLokasiGps = "Jl. Slamet Riyadi No. 214, Laweyan, Surakarta",
                tanggalLokasiGps = DateCurrencyUtils.addDaysToIsoDate(today, -30),
                jamLokasiGps = "10:15:00",
                namaLokasiUsaha = "Agen Point Frozen Laweyan",
                namaPenanggungJawab = "Budi Santoso",
                nomorWhatsappLokasi = "081226345678",
                provinsiLokasi = "Jawa Tengah",
                kabupatenKotaLokasi = "Kota Surakarta",
                kecamatanLokasi = "Laweyan",
                kelurahanDesaLokasi = "Sondakan",
                kodePosLokasi = "57147",
                linkGoogleMaps = "https://maps.google.com/?q=-7.569500,110.802100",
                catatanLokasi = "Ruko pintu biru sebelah apotek",
                akurasiGpsMeter = 4.2f,
                namaPenerima = "Budi Santoso",
                tanggalPenerimaan = DateCurrencyUtils.addDaysToIsoDate(today, -30),
                jamPenerimaan = "10:20:00",
                namaPetugas = "Rizky (Tim Kirim SAF)",
                keteranganKondisiFreezer = "Unit mulus, suhu -22C, keranjang 2 pcs lengkap",
                periodeBulanLabel = "PERIODE AKTIF"
            ),
            RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0002",
                tanggalPemesanan = DateCurrencyUtils.addDaysToIsoDate(today, -29),
                customerId = custIds[1],
                namaPelangganSnapshot = customers[1].namaLengkap,
                unitId = unitIds[1],
                namaUnitSnapshot = units[1].namaUnit,
                kodeUnitSnapshot = units[1].kodeUnit,
                hargaSewa = 250000L,
                durasiSewaBulan = 1,
                durasiHari = 30,
                tanggalMulaiSewa = DateCurrencyUtils.addDaysToIsoDate(today, -29),
                tanggalAkhirSewa = tomorrow,
                tanggalJatuhTempo = tomorrow,
                nominalPembayaran = 285000L,
                ongkir = 35000L,
                deposit = 0L,
                diskon = 0L,
                totalTagihan = 285000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "QRIS",
                catatan = "Jatuh tempo besok",
                latitude = -7.5378,
                longitude = 110.7615,
                alamatLokasiGps = "Jl. Adi Sucipto No. 88, Colomadu, Karanganyar",
                tanggalLokasiGps = DateCurrencyUtils.addDaysToIsoDate(today, -29),
                jamLokasiGps = "14:05:00",
                namaLokasiUsaha = "Dapur Frozen Colomadu",
                namaPenanggungJawab = "Siti Rahmawati",
                nomorWhatsappLokasi = "081390876543",
                provinsiLokasi = "Jawa Tengah",
                kabupatenKotaLokasi = "Kab. Karanganyar",
                kecamatanLokasi = "Colomadu",
                kelurahanDesaLokasi = "Malangjiwan",
                kodePosLokasi = "57177",
                linkGoogleMaps = "https://maps.google.com/?q=-7.537800,110.761500",
                catatanLokasi = "Depan halte bus Adi Sucipto",
                akurasiGpsMeter = 3.8f,
                namaPenerima = "Siti Rahmawati",
                tanggalPenerimaan = DateCurrencyUtils.addDaysToIsoDate(today, -29),
                jamPenerimaan = "14:10:00",
                namaPetugas = "Bagus (Tim Kirim SAF)",
                keteranganKondisiFreezer = "Berfungsi normal, kunci 1 buah",
                periodeBulanLabel = "PERIODE AKTIF",
                jenisProgram = "Sewa Hak Milik",
                totalPeriodeHakMilik = 12,
                pembayaranKeHakMilik = 6,
                statusProgramHakMilik = "Aktif"
            ),
            RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0003",
                tanggalPemesanan = DateCurrencyUtils.addDaysToIsoDate(today, -32),
                customerId = custIds[2],
                namaPelangganSnapshot = customers[2].namaLengkap,
                unitId = unitIds[2],
                namaUnitSnapshot = units[2].namaUnit,
                kodeUnitSnapshot = units[2].kodeUnit,
                hargaSewa = 350000L,
                durasiSewaBulan = 1,
                durasiHari = 30,
                tanggalMulaiSewa = DateCurrencyUtils.addDaysToIsoDate(today, -32),
                tanggalAkhirSewa = overdue2Days,
                tanggalJatuhTempo = overdue2Days,
                nominalPembayaran = 0L,
                ongkir = 40000L,
                deposit = 0L,
                diskon = 0L,
                totalTagihan = 390000L,
                statusPembayaran = "Terlambat",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer Mandiri",
                catatan = "Terlambat 2 hari, segera kirim ulang invoice via WhatsApp",
                latitude = -7.6012,
                longitude = 110.8156,
                alamatLokasiGps = "Jl. Ir. Soekarno No. 45, Solo Baru, Sukoharjo",
                tanggalLokasiGps = DateCurrencyUtils.addDaysToIsoDate(today, -32),
                jamLokasiGps = "11:30:00",
                namaLokasiUsaha = "Outlet Es Kristal & Minuman Solo Baru",
                namaPenanggungJawab = "Andi Prasetyo",
                nomorWhatsappLokasi = "085725112233",
                provinsiLokasi = "Jawa Tengah",
                kabupatenKotaLokasi = "Kab. Sukoharjo",
                kecamatanLokasi = "Grogol",
                kelurahanDesaLokasi = "Madegondo",
                kodePosLokasi = "57552",
                linkGoogleMaps = "https://maps.google.com/?q=-7.601200,110.815600",
                catatanLokasi = "Sebelah utara bundaran Pandawa Solo Baru",
                akurasiGpsMeter = 5.0f,
                namaPenerima = "Andi Prasetyo",
                tanggalPenerimaan = DateCurrencyUtils.addDaysToIsoDate(today, -32),
                jamPenerimaan = "11:35:00",
                namaPetugas = "Rizky (Tim Kirim SAF)",
                keteranganKondisiFreezer = "Showcase bersih, rak 4 susun",
                periodeBulanLabel = "PERIODE AKTIF"
            ),
            RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0004",
                tanggalPemesanan = DateCurrencyUtils.addDaysToIsoDate(today, -87),
                customerId = custIds[3],
                namaPelangganSnapshot = customers[3].namaLengkap,
                unitId = unitIds[3],
                namaUnitSnapshot = units[3].namaUnit,
                kodeUnitSnapshot = units[3].kodeUnit,
                hargaSewa = 600000L,
                durasiSewaBulan = 3,
                durasiHari = 90,
                tanggalMulaiSewa = DateCurrencyUtils.addDaysToIsoDate(today, -87),
                tanggalAkhirSewa = in3Days,
                tanggalJatuhTempo = in3Days,
                nominalPembayaran = 580000L,
                ongkir = 30000L,
                deposit = 0L,
                diskon = 50000L,
                totalTagihan = 580000L,
                statusPembayaran = "Lunas",
                statusPenyewaan = "Dalam Penyewaan",
                metodePembayaran = "Transfer BCA",
                catatan = "Paket 90 hari diskon Rp 50.000, jatuh tempo 3 hari lagi",
                latitude = -7.5844,
                longitude = 110.7798,
                alamatLokasiGps = "Perum Gentan Citra Indah Blok B4, Baki, Sukoharjo",
                tanggalLokasiGps = DateCurrencyUtils.addDaysToIsoDate(today, -87),
                jamLokasiGps = "09:40:00",
                namaLokasiUsaha = "Kediaman dr. Nadia Kusuma (Penyimpanan ASI)",
                namaPenanggungJawab = "dr. Nadia Kusuma",
                nomorWhatsappLokasi = "082134567890",
                provinsiLokasi = "Jawa Tengah",
                kabupatenKotaLokasi = "Kab. Sukoharjo",
                kecamatanLokasi = "Baki",
                kelurahanDesaLokasi = "Gentan",
                kodePosLokasi = "57556",
                linkGoogleMaps = "https://maps.google.com/?q=-7.584400,110.779800",
                catatanLokasi = "Rumah cat putih pagar hitam Blok B4",
                akurasiGpsMeter = 3.5f,
                namaPenerima = "dr. Nadia Kusuma",
                tanggalPenerimaan = DateCurrencyUtils.addDaysToIsoDate(today, -87),
                jamPenerimaan = "09:45:00",
                namaPetugas = "Bagus (Tim Kirim SAF)",
                keteranganKondisiFreezer = "Freezer ASI 4 laci utuh dan steril",
                periodeBulanLabel = "PERIODE AKTIF"
            ),
            RentalTransactionEntity(
                nomorInvoice = "INV/SAF/2026/0005",
                tanggalPemesanan = today,
                customerId = custIds[0],
                namaPelangganSnapshot = customers[0].namaLengkap,
                unitId = unitIds[4],
                namaUnitSnapshot = units[4].namaUnit,
                kodeUnitSnapshot = units[4].kodeUnit,
                hargaSewa = 450000L,
                durasiSewaBulan = 1,
                durasiHari = 30,
                tanggalMulaiSewa = in7Days,
                tanggalAkhirSewa = in37Days,
                tanggalJatuhTempo = in7Days,
                nominalPembayaran = 200000L,
                ongkir = 50000L,
                deposit = 100000L,
                diskon = 0L,
                totalTagihan = 600000L,
                statusPembayaran = "Belum Bayar",
                statusPenyewaan = "Booking",
                metodePembayaran = "Transfer BCA",
                catatan = "Pelanggan Budi Santoso menyewa unit kedua untuk event bazar Solo Raya (7 hari lagi)",
                latitude = -7.5695,
                longitude = 110.8021,
                alamatLokasiGps = "Jl. Slamet Riyadi No. 214, Laweyan, Surakarta",
                tanggalLokasiGps = today,
                jamLokasiGps = "15:00:00",
                namaLokasiUsaha = "Stand Bazar Kuliner Point Frozen Laweyan",
                namaPenanggungJawab = "Budi Santoso",
                nomorWhatsappLokasi = "081226345678",
                provinsiLokasi = "Jawa Tengah",
                kabupatenKotaLokasi = "Kota Surakarta",
                kecamatanLokasi = "Laweyan",
                kelurahanDesaLokasi = "Sondakan",
                kodePosLokasi = "57147",
                linkGoogleMaps = "https://maps.google.com/?q=-7.569500,110.802100",
                catatanLokasi = "Stand bazar kuliner gedung timur",
                akurasiGpsMeter = 4.0f,
                namaPenerima = "Budi Santoso",
                tanggalPenerimaan = today,
                jamPenerimaan = "15:05:00",
                namaPetugas = "Admin SAF Solo",
                keteranganKondisiFreezer = "Unit siap dikirim H-1 event",
                periodeBulanLabel = "BOOKING"
            )
        )

        transactions.forEach { dao.insertTransaction(it) }
    }

    // ==================== MASTER CUSTOMER, PARTIAL PAYMENT ALLOCATION & TIMELINE ENGINE (v13) ====================

    suspend fun logCustomerTimelineEvent(
        customerId: Long,
        transactionId: Long? = null,
        unitId: Long? = null,
        eventType: String,
        title: String,
        description: String = "",
        amount: Long = 0L
    ): Long {
        if (customerId <= 0L) return 0L
        val nowTs = DateCurrencyUtils.nowDateTimeDisplay()
        val nowIso = DateCurrencyUtils.todayIso()
        return dao.insertCustomerTimelineEvent(
            CustomerTimelineEventEntity(
                customerId = customerId,
                transactionId = transactionId,
                unitId = unitId,
                eventType = eventType,
                title = title,
                description = description,
                amount = amount,
                eventTimestamp = nowTs,
                eventDateIso = nowIso
            )
        )
    }

    /**
     * Section 4: Partial Payment & Payment Allocation Engine
     * Allows admin to input a partial payment (e.g. Rp400.000 out of Rp600.000) and explicitly
     * allocate amounts to specific rented units inside the contract without falsely marking
     * unallocated units as paid.
     */
    suspend fun allocatePartialPaymentToUnits(
        transactionId: Long,
        totalPaidAmount: Long,
        unitAllocationsMap: Map<Long, Long>, // unitItemId -> allocatedAmount
        paymentDate: String = DateCurrencyUtils.todayIso(),
        metodePembayaran: String = "Transfer BCA",
        namaPengirim: String = "",
        catatanPembayaran: String = "",
        fotoBuktiUri: String = ""
    ): PaymentProofEntity {
        if (totalPaidAmount <= 0L) {
            throw IllegalArgumentException("Nominal pembayaran harus lebih dari Rp 0.")
        }
        val tx = dao.getTransactionById(transactionId)
            ?: throw IllegalArgumentException("Kontrak transaksi tidak ditemukan.")

        var unitItems = dao.getTransactionUnitItemsByTxOnce(transactionId)
        if (unitItems.isEmpty()) {
            // Auto-materialize single unit item for legacy single-unit transactions
            val dbUnit = dao.getUnitById(tx.unitId)
            val createdItem = TransactionUnitItemEntity(
                transactionId = tx.id,
                customerId = tx.customerId,
                unitId = tx.unitId,
                unitIndexNumber = 1,
                kodeUnitSnapshot = tx.kodeUnitSnapshot.ifBlank { dbUnit?.kodeUnit.orEmpty() },
                namaUnitSnapshot = tx.namaUnitSnapshot.ifBlank { dbUnit?.namaUnit.orEmpty() },
                jenisUnitSnapshot = dbUnit?.jenisUnit.orEmpty(),
                merekSnapshot = dbUnit?.merek.orEmpty(),
                kapasitasLiterSnapshot = dbUnit?.kapasitasLiter ?: 0,
                jenisProgram = tx.jenisProgram,
                totalPeriodeHakMilik = tx.totalPeriodeHakMilik,
                pembayaranKeHakMilik = tx.pembayaranKeHakMilik,
                statusProgramHakMilik = tx.statusProgramHakMilik,
                hargaSewa = tx.hargaSewa,
                durasiHari = tx.durasiHari,
                tanggalMulaiSewa = tx.tanggalMulaiSewa,
                tanggalAkhirSewa = tx.tanggalAkhirSewa,
                tanggalJatuhTempo = tx.tanggalJatuhTempo,
                nominalPembayaran = tx.nominalPembayaran,
                statusPembayaran = tx.statusPembayaran,
                statusPenyewaan = tx.statusPenyewaan
            )
            val itemId = dao.insertTransactionUnitItem(createdItem)
            unitItems = listOf(createdItem.copy(id = itemId))
        }

        val itemById = unitItems.associateBy { it.id }
        val nonZeroAllocations = unitAllocationsMap.filterValues { it > 0L }
        val sumAllocated = nonZeroAllocations.values.sum()
        if (sumAllocated <= 0L) {
            throw IllegalArgumentException("Pilih minimal 1 unit dan masukkan nominal alokasi pembayaran.")
        }
        if (sumAllocated > totalPaidAmount) {
            throw IllegalArgumentException("Total alokasi unit (${DateCurrencyUtils.formatRupiah(sumAllocated)}) tidak boleh melebihi nominal dibayar (${DateCurrencyUtils.formatRupiah(totalPaidAmount)}).")
        }

        val now = DateCurrencyUtils.nowDateTimeDisplay()
        val jsonArr = JSONArray()
        nonZeroAllocations.forEach { (unitItemId, amount) ->
            val uItem = itemById[unitItemId]
            val obj = JSONObject().apply {
                put("unitItemId", unitItemId)
                put("unitId", uItem?.unitId ?: 0L)
                put("unitName", uItem?.namaUnitSnapshot ?: "Unit #$unitItemId")
                put("kodeUnit", uItem?.kodeUnitSnapshot ?: "")
                put("allocatedAmount", amount)
            }
            jsonArr.put(obj)
        }

        val allocSummaryText = nonZeroAllocations.entries.joinToString(", ") { (uid, amt) ->
            val name = itemById[uid]?.namaUnitSnapshot?.ifBlank { itemById[uid]?.kodeUnitSnapshot } ?: "Unit #$uid"
            "$name: ${DateCurrencyUtils.formatRupiah(amt)}"
        }

        val finalNotes = buildString {
            if (catatanPembayaran.isNotBlank()) append(catatanPembayaran).append(" • ")
            append("Alokasi: ").append(allocSummaryText)
        }

        val proof = preparePaymentProofForSave(
            incoming = PaymentProofEntity(
                transactionId = transactionId,
                tanggalPembayaran = paymentDate.ifBlank { DateCurrencyUtils.todayIso() },
                nominalPembayaran = sumAllocated,
                metodePembayaran = metodePembayaran,
                namaPengirim = namaPengirim.ifBlank { tx.namaPelangganSnapshot },
                catatanPembayaran = finalNotes,
                fotoBuktiUri = fotoBuktiUri,
                waktuUpload = now,
                unitItemId = if (nonZeroAllocations.size == 1) nonZeroAllocations.keys.first() else null,
                alokasiUnitJson = jsonArr.toString()
            ),
            existing = null
        )
        val proofChangedFields = MirrorOutboxCodec.computePaymentProofChangedFieldsJson(null, proof)
        val proofId = dao.savePaymentProofWithOutboxAtomic(
            proof = proof,
            isNew = true,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = proofChangedFields,
            serverTimestampMillis = currentServerTimeRef()
        )
        val finalProof = dao.getPaymentProofById(proofId) ?: proof.copy(id = proofId)

        val allocationRows = mutableListOf<UnitPaymentAllocationEntity>()
        nonZeroAllocations.forEach { (unitItemId, amount) ->
            val item = itemById[unitItemId] ?: return@forEach
            val newUnitPaid = (item.nominalPembayaran + amount).coerceAtLeast(0L)
            val newUnitStatus = when {
                item.hargaSewa > 0L && newUnitPaid >= item.hargaSewa -> "Lunas"
                newUnitPaid > 0L -> "Dibayar Sebagian"
                else -> "Belum Bayar"
            }
            dao.updateTransactionUnitItem(
                item.copy(
                    nominalPembayaran = newUnitPaid,
                    statusPembayaran = newUnitStatus,
                    updatedAt = now
                )
            )
            allocationRows.add(
                UnitPaymentAllocationEntity(
                    paymentProofId = proofId,
                    transactionId = transactionId,
                    unitItemId = item.id,
                    customerId = tx.customerId,
                    unitId = item.unitId,
                    namaUnitSnapshot = item.namaUnitSnapshot,
                    kodeUnitSnapshot = item.kodeUnitSnapshot,
                    allocatedAmount = amount,
                    paymentDate = paymentDate.ifBlank { DateCurrencyUtils.todayIso() },
                    periodeTagihan = item.periodeTagihan.ifBlank { paymentDate.take(7) },
                    metodePembayaran = metodePembayaran,
                    catatan = catatanPembayaran,
                    createdAt = now
                )
            )
        }
        if (allocationRows.isNotEmpty()) {
            dao.insertUnitPaymentAllocations(allocationRows)
        }

        // Sync parent transaction nominal & status from updated unit items WITHOUT marking unallocated units paid
        val updatedItems = dao.getTransactionUnitItemsByTxOnce(transactionId)
        val summary = calculateMultiUnitBillingSummary(
            unitItems = updatedItems,
            ongkir = tx.ongkir,
            deposit = tx.deposit,
            diskon = tx.diskon,
            overrideNominalPembayaran = null
        )
        val aggStatus = when {
            summary.totalTagihan > 0L && summary.totalPembayaran >= summary.totalTagihan -> "Lunas"
            summary.totalPembayaran > 0L -> "Dibayar Sebagian"
            else -> "Belum Bayar"
        }
        val updatedTx = prepareTransactionForSave(
            incoming = tx.copy(
                nominalPembayaran = summary.totalPembayaran,
                statusPembayaran = aggStatus,
                metodePembayaran = metodePembayaran
            ),
            existing = tx
        )
        val txChangedFields = MirrorOutboxCodec.computeTransactionChangedFieldsJson(tx, updatedTx)
        dao.saveTransactionWithOutboxAtomic(
            transaction = updatedTx,
            isNew = false,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = txChangedFields,
            serverTimestampMillis = currentServerTimeRef()
        )

        mirrorSyncEngine?.onPaymentOutboxCommitted(proof = finalProof, isNew = true)
        mirrorSyncEngine?.onTransactionOutboxCommitted(tx = updatedTx, isNew = false)

        logCustomerTimelineEvent(
            customerId = tx.customerId,
            transactionId = transactionId,
            eventType = "PEMBAYARAN",
            title = "Pembayaran Diterima (${tx.nomorInvoice})",
            description = "$metodePembayaran • $allocSummaryText",
            amount = sumAllocated
        )

        return finalProof
    }

    companion object {
        fun buildCustomerSmartSummary(
            customer: CustomerEntity,
            transactions: List<RentalTransactionEntity>,
            allUnitItems: List<TransactionUnitItemEntity>,
            allPaymentProofs: List<PaymentProofEntity>,
            allUnitAllocations: List<UnitPaymentAllocationEntity>,
            todayIso: String = DateCurrencyUtils.todayIso()
        ): CustomerSmartSummary {
            val custTx = transactions.filter { it.customerId == customer.id }
                .sortedBy { it.id }
            val activeTx = custTx.filter {
                (it.statusPenyewaan.equals("Dalam Penyewaan", true) ||
                    it.statusPenyewaan.equals("Booking", true))
            }

            val itemsByTx = allUnitItems.groupBy { it.transactionId }
            val proofsByTx = allPaymentProofs.groupBy { it.transactionId }
            val allocsByTx = allUnitAllocations.groupBy { it.transactionId }

            val contractNodes = custTx.mapIndexed { idx, tx ->
                val txItems = itemsByTx[tx.id].orEmpty().ifEmpty {
                    // Fallback synthetic unit item view for legacy single-unit tx
                    listOf(
                        TransactionUnitItemEntity(
                            id = -tx.id,
                            transactionId = tx.id,
                            customerId = tx.customerId,
                            unitId = tx.unitId,
                            unitIndexNumber = 1,
                            kodeUnitSnapshot = tx.kodeUnitSnapshot,
                            namaUnitSnapshot = tx.namaUnitSnapshot,
                            jenisProgram = tx.jenisProgram,
                            totalPeriodeHakMilik = tx.totalPeriodeHakMilik,
                            pembayaranKeHakMilik = tx.pembayaranKeHakMilik,
                            statusProgramHakMilik = tx.statusProgramHakMilik,
                            hargaSewa = tx.hargaSewa.takeIf { it > 0L } ?: tx.totalTagihan,
                            durasiHari = tx.durasiHari,
                            tanggalMulaiSewa = tx.tanggalMulaiSewa,
                            tanggalAkhirSewa = tx.tanggalAkhirSewa,
                            tanggalJatuhTempo = tx.tanggalJatuhTempo,
                            nominalPembayaran = tx.nominalPembayaran,
                            statusPembayaran = tx.statusPembayaran,
                            statusPenyewaan = tx.statusPenyewaan
                        )
                    )
                }
                ContractHierarchyNode(
                    transaction = tx,
                    contractNumberDisplay = "Kontrak #${(idx + 1).toString().padStart(3, '0')} • ${tx.nomorInvoice}",
                    unitItems = txItems,
                    paymentProofs = proofsByTx[tx.id].orEmpty(),
                    unitAllocations = allocsByTx[tx.id].orEmpty()
                )
            }

            val activeNodes = contractNodes.filter {
                it.transaction.statusPenyewaan.equals("Dalam Penyewaan", true) ||
                    it.transaction.statusPenyewaan.equals("Booking", true)
            }

            val activeUnitsCount = activeNodes.sumOf { node ->
                node.unitItems.count { !it.statusPenyewaan.equals("Selesai", true) && !it.statusPenyewaan.equals("Dibatalkan", true) }
            }

            val monthlyBilling = activeNodes.sumOf { node ->
                node.unitItems
                    .filter { !it.statusPenyewaan.equals("Selesai", true) && !it.statusPenyewaan.equals("Dibatalkan", true) }
                    .sumOf { it.hargaSewa }
            }

            val totalTunggakan = custTx
                .filter { !it.statusPenyewaan.equals("Dibatalkan", true) }
                .sumOf { (it.totalTagihan - it.nominalPembayaran).coerceAtLeast(0L) }

            val currentMonthPrefix = todayIso.take(7)
            val custProofs = custTx.flatMap { proofsByTx[it.id].orEmpty() }
            val paymentsThisMonth = if (custProofs.any { it.tanggalPembayaran.startsWith(currentMonthPrefix) }) {
                custProofs.filter { it.tanggalPembayaran.startsWith(currentMonthPrefix) }.sumOf { it.nominalPembayaran }
            } else {
                custTx.filter { it.tanggalPemesanan.startsWith(currentMonthPrefix) }.sumOf { it.nominalPembayaran }
            }

            val hasOverdue = activeNodes.any { node ->
                node.unitItems.any { item ->
                    item.computeSmartBillingStatus(todayIso) == "Terlambat"
                }
            }

            val derivedStatus = when {
                hasOverdue -> "Menunggak"
                activeTx.isNotEmpty() -> "Pelanggan Aktif"
                custTx.isNotEmpty() -> "Selesai / Nonaktif"
                else -> customer.statusPelanggan.ifBlank { "Baru Terdaftar" }
            }

            return CustomerSmartSummary(
                customer = customer,
                totalUnitAktif = activeUnitsCount,
                totalKontrakAktif = activeTx.size,
                totalKontrakKeseluruhan = custTx.size,
                totalTagihanBulanan = monthlyBilling,
                totalTunggakan = totalTunggakan,
                pembayaranBulanIni = paymentsThisMonth,
                statusPelangganLabel = derivedStatus,
                contracts = contractNodes.reversed() // newest first
            )
        }

        fun buildCombinedTimelineForCustomer(
            customer: CustomerEntity,
            transactions: List<RentalTransactionEntity>,
            unitItems: List<TransactionUnitItemEntity>,
            paymentProofs: List<PaymentProofEntity>,
            returns: List<FreezerReturnEntity>,
            photos: List<CustomerPhotoEntity>,
            storedEvents: List<CustomerTimelineEventEntity>
        ): List<CustomerTimelineEventEntity> {
            val custStored = storedEvents.filter { it.customerId == customer.id }
            val result = mutableListOf<CustomerTimelineEventEntity>()
            result.addAll(custStored)

            val custTx = transactions.filter { it.customerId == customer.id }
            val txIds = custTx.map { it.id }.toSet()

            // Backfill registration if missing in storedEvents
            if (custStored.none { it.eventType == "REGISTRASI" }) {
                result.add(
                    CustomerTimelineEventEntity(
                        id = -100L - customer.id,
                        customerId = customer.id,
                        eventType = "REGISTRASI",
                        title = "Pelanggan Terdaftar (${customer.kodePelanggan.ifBlank { "CUST/SAF/${customer.id.toString().padStart(4, '0')}" }})",
                        description = "${customer.namaLengkap} bergabung • ${customer.alamatLengkap.take(60)}",
                        eventTimestamp = "${customer.tanggalRegistrasi} 09:00:00",
                        eventDateIso = customer.tanggalRegistrasi
                    )
                )
            }

            // Backfill contracts / rentals
            custTx.forEach { tx ->
                if (custStored.none { it.transactionId == tx.id && it.eventType == "SEWA_UNIT" }) {
                    val unitCount = unitItems.count { it.transactionId == tx.id }.coerceAtLeast(1)
                    result.add(
                        CustomerTimelineEventEntity(
                            id = -2000L - tx.id,
                            customerId = customer.id,
                            transactionId = tx.id,
                            unitId = tx.unitId,
                            eventType = "SEWA_UNIT",
                            title = "Sewa Unit (${tx.nomorInvoice})",
                            description = "$unitCount Unit (${tx.namaUnitSnapshot}) • Program: ${tx.jenisProgram}",
                            amount = tx.totalTagihan,
                            eventTimestamp = "${tx.tanggalPemesanan} 10:00:00",
                            eventDateIso = tx.tanggalPemesanan
                        )
                    )
                }
            }

            // Backfill payment proofs not yet reflected in storedEvents
            paymentProofs.filter { it.transactionId in txIds }.forEach { proof ->
                if (custStored.none { it.transactionId == proof.transactionId && it.eventType == "PEMBAYARAN" && it.amount == proof.nominalPembayaran }) {
                    result.add(
                        CustomerTimelineEventEntity(
                            id = -3000L - proof.id,
                            customerId = customer.id,
                            transactionId = proof.transactionId,
                            eventType = "PEMBAYARAN",
                            title = "Pembayaran (${proof.metodePembayaran})",
                            description = proof.catatanPembayaran.ifBlank { "Bukti pembayaran diunggah" },
                            amount = proof.nominalPembayaran,
                            eventTimestamp = proof.waktuUpload.ifBlank { "${proof.tanggalPembayaran} 12:00:00" },
                            eventDateIso = proof.tanggalPembayaran
                        )
                    )
                }
            }

            // Backfill photos
            photos.filter { it.customerId == customer.id }.forEach { photo ->
                if (custStored.none { it.eventType == "DOKUMENTASI" && it.description.contains(photo.fileName) }) {
                    result.add(
                        CustomerTimelineEventEntity(
                            id = -4000L - photo.id,
                            customerId = customer.id,
                            transactionId = photo.transactionId,
                            eventType = "DOKUMENTASI",
                            title = "Upload Dokumentasi (${photo.namaDokumentasi})",
                            description = "Kategori: ${photo.kategori} • ${photo.fileName}",
                            eventTimestamp = photo.tanggalWaktuIso,
                            eventDateIso = photo.tanggalWaktuIso.take(10)
                        )
                    )
                }
            }

            // Backfill returns
            returns.filter { it.customerId == customer.id }.forEach { ret ->
                if (custStored.none { it.transactionId == ret.transactionId && it.eventType == "PENGEMBALIAN" }) {
                    result.add(
                        CustomerTimelineEventEntity(
                            id = -5000L - ret.id,
                            customerId = customer.id,
                            transactionId = ret.transactionId,
                            unitId = ret.unitId,
                            eventType = "PENGEMBALIAN",
                            title = "Pengembalian Unit (${ret.kodeUnitSnapshot})",
                            description = "Dokumen: ${ret.nomorDokumenPengembalian} • Kondisi: ${ret.kondisiUnitSaatKembali}",
                            eventTimestamp = "${ret.tanggalPengembalian} ${ret.jamPengembalian}",
                            eventDateIso = ret.tanggalPengembalian
                        )
                    )
                }
            }

            return result.sortedWith(
                compareByDescending<CustomerTimelineEventEntity> { it.eventDateIso }
                    .thenByDescending { it.eventTimestamp }
                    .thenByDescending { it.id }
            )
        }
    }

    // --- BACKUP, RESTORE & AUDIT LOG METHODS (v15) ---
    suspend fun getAllCustomerPhotosOnce(): List<CustomerPhotoEntity> = dao.getAllCustomerPhotosOnce()

    suspend fun getAllCustomerTimelineEventsOnce(): List<CustomerTimelineEventEntity> = dao.getAllCustomerTimelineEventsOnce()

    suspend fun getAllExtensionsOnce(): List<RentalExtensionHistoryEntity> = dao.getAllExtensionsOnce()

    suspend fun getAllPaymentProofsOnce(): List<PaymentProofEntity> = dao.getAllPaymentProofsOnce()

    suspend fun getAllUnitPaymentAllocationsOnce(): List<UnitPaymentAllocationEntity> = dao.getAllUnitPaymentAllocationsOnce()

    suspend fun getAllTransactionSignaturesOnce(): List<TransactionSignatureEntity> = dao.getAllTransactionSignaturesOnce()

    suspend fun getAllReturnsOnce(): List<FreezerReturnEntity> = dao.getAllReturnsOnce()

    suspend fun getLocalBackupByBackupId(backupId: String): LocalBackupEntity? =
        dao.getLocalBackupByBackupId(backupId)

    suspend fun getLocalBackupByDriveFileId(driveFileId: String): LocalBackupEntity? =
        dao.getLocalBackupByDriveFileId(driveFileId)

    suspend fun deleteLocalBackupById(id: Long) = dao.deleteLocalBackupById(id)

    suspend fun getAllBackupAuditLogsOnce(): List<BackupAuditLogEntity> = dao.getAllBackupAuditLogsOnce()

    suspend fun logBackupAuditEvent(
        actionType: String,
        status: String,
        fileName: String = "",
        checksum: String = "",
        recordCount: Int = 0,
        actorUid: String = "",
        actorEmail: String = "",
        detailMessage: String = ""
    ): Long {
        val nowMs = System.currentTimeMillis()
        return dao.insertBackupAuditLog(
            BackupAuditLogEntity(
                actionType = actionType,
                status = status,
                fileName = fileName,
                checksum = checksum,
                recordCount = recordCount,
                actorUid = actorUid,
                actorEmail = actorEmail,
                detailMessage = detailMessage,
                timestampDisplay = DateCurrencyUtils.nowDateTimeDisplay(),
                timestampMillis = nowMs
            )
        )
    }

    suspend fun replaceDatabaseForFullRestore(
        customers: List<CustomerEntity>,
        units: List<FreezerUnitEntity>,
        transactions: List<RentalTransactionEntity>,
        unitItems: List<TransactionUnitItemEntity>,
        paymentProofs: List<PaymentProofEntity>,
        allocations: List<UnitPaymentAllocationEntity>,
        signatures: List<TransactionSignatureEntity>,
        returns: List<FreezerReturnEntity>,
        extensions: List<RentalExtensionHistoryEntity>,
        paymentMethods: List<PaymentMethodEntity>,
        customerDocuments: List<CustomerDocumentEntity>,
        transactionDocuments: List<TransactionDocumentEntity>,
        customerLocations: List<CustomerLocationEntity>,
        customerPhotos: List<CustomerPhotoEntity>,
        timelineEvents: List<CustomerTimelineEventEntity>
    ) {
        val now = System.currentTimeMillis()
        val seenCustUuids = HashSet<String>()
        val normalizedCustomers = customers.map { c ->
            val u = c.uuid.takeIf { it.isNotBlank() && seenCustUuids.add(it) } ?: java.util.UUID.randomUUID().toString().also { seenCustUuids.add(it) }
            c.copy(
                uuid = u,
                version = c.version.coerceAtLeast(1L),
                createdAtMillis = c.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = c.updatedAtMillis.takeIf { it > 0L } ?: now
            )
        }
        val seenUnitUuids = HashSet<String>()
        val normalizedUnits = units.map { uEntity ->
            val u = uEntity.uuid.takeIf { it.isNotBlank() && seenUnitUuids.add(it) } ?: java.util.UUID.randomUUID().toString().also { seenUnitUuids.add(it) }
            uEntity.copy(
                uuid = u,
                version = uEntity.version.coerceAtLeast(1L),
                createdAtMillis = uEntity.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = uEntity.updatedAtMillis.takeIf { it > 0L } ?: now
            )
        }
        val seenTxUuids = HashSet<String>()
        val normalizedTx = transactions.map { t ->
            val u = t.uuid.takeIf { it.isNotBlank() && seenTxUuids.add(it) } ?: java.util.UUID.randomUUID().toString().also { seenTxUuids.add(it) }
            t.copy(
                uuid = u,
                version = t.version.coerceAtLeast(1L),
                createdAtMillis = t.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = t.updatedAtMillis.takeIf { it > 0L } ?: now
            )
        }
        val seenProofUuids = HashSet<String>()
        val normalizedProofs = paymentProofs.map { p ->
            val u = p.uuid.takeIf { it.isNotBlank() && seenProofUuids.add(it) } ?: java.util.UUID.randomUUID().toString().also { seenProofUuids.add(it) }
            p.copy(
                uuid = u,
                version = p.version.coerceAtLeast(1L),
                createdAtMillis = p.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = p.updatedAtMillis.takeIf { it > 0L } ?: now
            )
        }
        dao.replaceDatabaseForFullRestore(
            customers = normalizedCustomers,
            units = normalizedUnits,
            transactions = normalizedTx,
            unitItems = unitItems,
            paymentProofs = normalizedProofs,
            allocations = allocations,
            signatures = signatures,
            returns = returns,
            extensions = extensions,
            paymentMethods = paymentMethods,
            customerDocuments = customerDocuments,
            transactionDocuments = transactionDocuments,
            customerLocations = customerLocations,
            customerPhotos = customerPhotos,
            timelineEvents = timelineEvents
        )
    }

    // --- GOOGLE DRIVE DOCUMENTATION PHOTO METADATA (v16) ---
    val allDrivePhotoMetadata: Flow<List<DrivePhotoMetadataEntity>> = dao.observeAllDrivePhotoMetadata()

    suspend fun getAllDrivePhotoMetadataOnce(): List<DrivePhotoMetadataEntity> =
        dao.getAllDrivePhotoMetadataOnce()

    suspend fun getDrivePhotoMetadataById(id: Long): DrivePhotoMetadataEntity? =
        dao.getDrivePhotoMetadataById(id)

    suspend fun getDrivePhotoMetadataByCustomerOnce(customerId: Long): List<DrivePhotoMetadataEntity> =
        dao.getDrivePhotoMetadataByCustomerOnce(customerId)

    suspend fun getDrivePhotoMetadataByUnitOnce(unitId: Long): List<DrivePhotoMetadataEntity> =
        dao.getDrivePhotoMetadataByUnitOnce(unitId)

    suspend fun getDrivePhotoMetadataByTransactionOnce(transactionId: Long): List<DrivePhotoMetadataEntity> =
        dao.getDrivePhotoMetadataByTransactionOnce(transactionId)

    suspend fun findDrivePhotoByChecksumAndCategory(checksum: String, categoryCode: String): DrivePhotoMetadataEntity? =
        dao.findDrivePhotoByChecksumAndCategory(checksum, categoryCode)

    suspend fun getPendingOrFailedDrivePhotosOnce(): List<DrivePhotoMetadataEntity> =
        dao.getPendingOrFailedDrivePhotosOnce()

    suspend fun upsertDrivePhotoMetadata(entity: DrivePhotoMetadataEntity): Long =
        dao.upsertDrivePhotoMetadata(entity)

    suspend fun updateDrivePhotoSyncResult(
        id: Long,
        syncStatus: String,
        driveFileId: String,
        driveFolderId: String,
        accessUrlOrPath: String,
        tempOriginalPath: String,
        uploadDate: String,
        retryCount: Int,
        lastError: String
    ) = dao.updateDrivePhotoSyncResult(
        id = id,
        syncStatus = syncStatus,
        driveFileId = driveFileId,
        driveFolderId = driveFolderId,
        accessUrlOrPath = accessUrlOrPath,
        tempOriginalPath = tempOriginalPath,
        uploadDate = uploadDate,
        retryCount = retryCount,
        lastError = lastError
    )

    suspend fun deleteDrivePhotoMetadata(entity: DrivePhotoMetadataEntity) =
        dao.deleteDrivePhotoMetadata(entity)

    // --- GOOGLE DRIVE CLOUD MIRROR REPOSITORY QUERIES (v17) ---
    suspend fun getAllDriveCloudFileMappingsOnce(): List<DriveCloudFileMapEntity> =
        dao.getAllDriveCloudFileMappingsOnce()

    suspend fun getDriveCloudFileMapByKey(localRecordKey: String): DriveCloudFileMapEntity? =
        dao.getDriveCloudFileMapByKey(localRecordKey)

    suspend fun getAllDriveLocalSyncQueueOnce(): List<DriveLocalSyncQueueEntity> =
        dao.getAllDriveLocalSyncQueueOnce()

    suspend fun getRunnableDriveSyncQueueItemsOnce(): List<DriveLocalSyncQueueEntity> =
        dao.getRunnableDriveSyncQueueItemsOnce()

    suspend fun getAllDriveDeletionTombstonesOnce(): List<DriveDeletionTombstoneEntity> =
        dao.getAllDriveDeletionTombstonesOnce()

    fun observeSyncTechnicalLogs(): Flow<List<SyncTechnicalLogEntity>> =
        dao.observeRecentSyncTechnicalLogs()

    // =========================================================================
    // v23 ADDITIVE FEATURES: AUTO-DRAFT, PER-UNIT RENTAL PRICING,
    // PER-UNIT SEWA HAK MILIK PROGRESS, & AUDITED OFFICER SNAPSHOT CORRECTION
    // =========================================================================

    // --- 1. ISOLATED FORM DRAFTS (NEVER SYNCED TO GOOGLE DRIVE OR ACTIVE TABLES) ---

    suspend fun getAllFormDraftsOnce(): List<FormDraftEntity> =
        dao.getAllFormDraftsOnce()

    suspend fun getFormDraftByUuid(uuid: String): FormDraftEntity? =
        dao.getFormDraftByUuid(uuid)

    suspend fun getLatestFormDraft(formType: String, targetEntityId: Long = 0L): FormDraftEntity? =
        dao.getLatestFormDraftByTypeAndTarget(formType, targetEntityId)

    suspend fun saveFormDraft(
        formType: String,
        targetEntityId: Long = 0L,
        ownerAccount: String = "",
        title: String,
        subtitle: String = "",
        payloadJson: String,
        existingUuid: String = ""
    ): FormDraftEntity {
        val nowMs = System.currentTimeMillis()
        val nowDisplay = DateCurrencyUtils.nowDateTimeDisplay()
        val existing = when {
            existingUuid.isNotBlank() -> dao.getFormDraftByUuid(existingUuid)
            else -> dao.getLatestFormDraftByTypeAndTarget(formType, targetEntityId)
        }
        val resolvedUuid = MirrorOutboxCodec.ensureValidUuidV4(
            existing?.uuid?.ifBlank { existingUuid } ?: existingUuid
        )
        val entity = FormDraftEntity(
            id = existing?.id ?: 0L,
            uuid = resolvedUuid,
            formType = formType,
            targetEntityId = targetEntityId,
            ownerAccount = ownerAccount.ifBlank { existing?.ownerAccount.orEmpty() },
            title = title,
            subtitle = subtitle,
            payloadJson = payloadJson,
            version = (existing?.version ?: 0L) + 1L,
            createdAtMillis = existing?.createdAtMillis?.takeIf { it > 0L } ?: nowMs,
            updatedAtMillis = nowMs,
            updatedAtDisplay = nowDisplay
        )
        val savedId = dao.upsertFormDraft(entity)
        return entity.copy(id = if (entity.id > 0L) entity.id else savedId)
    }

    suspend fun deleteFormDraftByUuid(uuid: String) {
        if (uuid.isNotBlank()) dao.deleteFormDraftByUuid(uuid)
    }

    suspend fun deleteFormDraftsForForm(formType: String, targetEntityId: Long = 0L) {
        dao.deleteFormDraftsByTypeAndTarget(formType, targetEntityId)
    }

    // --- 2. PER-UNIT RENTAL PERIOD PRICING (3, 7, 14, 30, 60, 90 HARI) ---

    suspend fun getAllUnitRentalPricesOnce(): List<UnitRentalPriceEntity> =
        dao.getAllUnitRentalPricesOnce()

    suspend fun getUnitRentalPricesByUnitOnce(unitId: Long): List<UnitRentalPriceEntity> =
        dao.getUnitRentalPricesByUnitOnce(unitId)

    suspend fun resolveRentalPriceForUnitAndPeriod(unitId: Long, periodDays: Int): Long? =
        dao.getUnitRentalPriceForPeriod(unitId, periodDays)?.price?.takeIf { it > 0L }

    suspend fun saveUnitRentalPrices(unitId: Long, pricesByPeriod: Map<Int, Long>) {
        if (unitId <= 0L) return
        dao.replaceUnitRentalPricesAtomic(unitId, pricesByPeriod)
    }

    suspend fun saveUnitWithRentalPrices(
        unit: FreezerUnitEntity,
        pricesByPeriod: Map<Int, Long>
    ): Long {
        // Keep unit.cicilanPerBulan synchronized with 30-day monthly price if user didn't set a separate monthly figure
        val monthlyPrice = pricesByPeriod[30]?.takeIf { it > 0L }
            ?: pricesByPeriod.entries.filter { it.value > 0L }.minByOrNull { kotlin.math.abs(it.key - 30) }?.value
        val syncedUnit = if (unit.cicilanPerBulan <= 0L && monthlyPrice != null) {
            unit.copy(cicilanPerBulan = monthlyPrice)
        } else {
            unit
        }
        val savedId = saveUnit(syncedUnit)
        if (savedId > 0L) {
            dao.replaceUnitRentalPricesAtomic(savedId, pricesByPeriod)
        }
        return savedId
    }

    /**
     * Resolves the freshest rental price directly from Room SQLite (`dao.getUnitById(unitId)` + `unit_rental_prices`)
     * for any package: Harian (1 hari), Mingguan (7 hari), Bulanan (30 hari), custom period, or Program Hak Milik (e.g. 19 Kali).
     */
    suspend fun resolveLiveUnitPrice(
        unitId: Long,
        periodDays: Int,
        jenisProgram: String
    ): Pair<FreezerUnitEntity?, Long> {
        if (unitId <= 0L) return null to 0L
        val unit = dao.getUnitById(unitId) ?: return null to 0L
        val periodPrices = dao.getUnitRentalPricesByUnitOnce(unitId)
            .filter { it.price > 0L }
            .sortedBy { it.periodDays }

        val isHakMilik = jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
        val resolvedPrice = if (isHakMilik) {
            unit.cicilanPerBulan.takeIf { it > 0L }
                ?: periodPrices.firstOrNull { it.periodDays == 30 }?.price
                ?: periodPrices.firstOrNull()?.price
                ?: 0L
        } else {
            val exact = periodPrices.firstOrNull { it.periodDays == periodDays }?.price
            if (exact != null && exact > 0L) {
                exact
            } else {
                val monthlyRef = periodPrices.firstOrNull { it.periodDays == 30 }?.price
                    ?: unit.cicilanPerBulan.takeIf { it > 0L }
                when {
                    periodDays == 1 -> {
                        periodPrices.firstOrNull { it.periodDays == 3 }?.let { (it.price / 3L).coerceAtLeast(1000L) }
                            ?: monthlyRef?.let { (it / 30L).coerceAtLeast(1000L) }
                            ?: periodPrices.firstOrNull()?.price
                            ?: 0L
                    }
                    periodDays == 7 -> {
                        periodPrices.firstOrNull { it.periodDays == 14 }?.let { (it.price / 2L).coerceAtLeast(1000L) }
                            ?: monthlyRef?.let { (it / 4L).coerceAtLeast(1000L) }
                            ?: periodPrices.firstOrNull()?.price
                            ?: 0L
                    }
                    periodDays == 30 -> {
                        monthlyRef ?: periodPrices.firstOrNull()?.price ?: 0L
                    }
                    else -> {
                        monthlyRef ?: periodPrices.firstOrNull()?.price ?: 0L
                    }
                }
            }
        }
        return unit to resolvedPrice
    }

    /**
     * Immediately applies Cloud-to-Local unit price updates to local Room SQLite without being
     * blocked by Stale status or timestamp skew, clearing any stale/conflict queue markers on that unit
     * so Room Flows immediately emit fresh prices to FormTransaksiScreen.
     */
    suspend fun applyCloudUnitPricingOverride(
        remoteUnit: FreezerUnitEntity,
        remotePeriodPrices: Map<Int, Long> = emptyMap()
    ): FreezerUnitEntity {
        val existing = (if (remoteUnit.id > 0L) dao.getUnitByIdIncludingDeleted(remoteUnit.id) else null)
            ?: (if (remoteUnit.uuid.isNotBlank()) dao.getUnitByUuidIncludingDeleted(remoteUnit.uuid) else null)
            ?: (if (remoteUnit.kodeUnit.isNotBlank()) dao.getUnitByKodeIncludingDeleted(remoteUnit.kodeUnit) else null)
        val now = maxOf(System.currentTimeMillis(), remoteUnit.updatedAtMillis + 1L)
        val merged = if (existing != null) {
            existing.copy(
                hargaPembelian = if (remoteUnit.hargaPembelian > 0L) remoteUnit.hargaPembelian else existing.hargaPembelian,
                cicilanPerBulan = if (remoteUnit.cicilanPerBulan > 0L) remoteUnit.cicilanPerBulan else existing.cicilanPerBulan,
                version = maxOf(existing.version, remoteUnit.version),
                updatedAtMillis = now,
                syncStatus = MirrorOutboxCodec.STATUS_SYNCED,
                isDeleted = false
            )
        } else {
            remoteUnit.copy(
                uuid = MirrorOutboxCodec.ensureValidUuidV4(remoteUnit.uuid),
                updatedAtMillis = now,
                syncStatus = MirrorOutboxCodec.STATUS_SYNCED,
                isDeleted = false
            )
        }
        val savedId = if (existing != null) {
            dao.updateUnit(merged)
            merged.id
        } else {
            dao.insertUnit(merged)
        }
        if (savedId > 0L && remotePeriodPrices.isNotEmpty()) {
            dao.replaceUnitRentalPricesAtomic(savedId, remotePeriodPrices)
        }
        // Clear any stale/conflict markers on this unit so local cache reflects Cloud price immediately
        val unitKey = "UNIT:$savedId"
        dao.getAllDriveLocalSyncQueueOnce()
            .filter { q ->
                q.localRecordKey == unitKey && (
                    q.syncStatus.equals("STALE", ignoreCase = true) ||
                        q.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_STALE_OPERATION ||
                        q.syncStatus == GoogleDriveMirrorSyncEngine.STATUS_CONFLICT
                    )
            }
            .forEach { q ->
                dao.deleteDriveSyncQueueById(q.id)
            }
        return dao.getUnitById(savedId) ?: merged.copy(id = savedId)
    }

    // --- 3. INDEPENDENT PER-UNIT SEWA HAK MILIK PAYMENT PROGRESS ---

    /**
     * Records a payment strictly for ONE specific freezer unit (`unitItemId`) in a transaction:
     * - Creates a PaymentProofEntity linked to `transactionId` and `unitItemId` (and enqueues Google Drive sync)
     * - Creates a UnitPaymentAllocationEntity linked to `unitItemId` and `unitId`
     * - Updates ONLY that unit item's `pembayaranKeHakMilik`, `nominalPembayaran`, `statusPembayaran`,
     *   and `statusProgramHakMilik` WITHOUT altering any sibling units in the same transaction.
     * - Recalculates parent transaction aggregate totals.
     */
    suspend fun recordUnitHakMilikPayment(
        transactionId: Long,
        unitItemId: Long,
        nominalPembayaran: Long,
        metodePembayaran: String = "Transfer BCA",
        tanggalPembayaran: String = DateCurrencyUtils.todayIso(),
        namaPengirim: String = "",
        catatanPembayaran: String = "",
        fotoBuktiUri: String = "",
        incrementPaymentSequence: Boolean = true
    ): TransactionUnitItemEntity {
        val parentTx = dao.getTransactionById(transactionId)
            ?: throw IllegalArgumentException("Transaksi tidak ditemukan.")
        val targetItem = dao.getTransactionUnitItemById(unitItemId)
            ?: throw IllegalArgumentException("Item unit pada transaksi tidak ditemukan.")
        require(targetItem.transactionId == transactionId) {
            "Unit tidak termasuk dalam transaksi ini."
        }
        val nowDisplay = DateCurrencyUtils.nowDateTimeDisplay()
        val nextPaymentSequence = if (targetItem.jenisProgram == "Sewa Hak Milik" && incrementPaymentSequence) {
            val maxTarget = targetItem.totalPeriodeHakMilik.takeIf { it > 0 } ?: Int.MAX_VALUE
            (targetItem.pembayaranKeHakMilik + 1).coerceAtMost(maxTarget)
        } else {
            targetItem.pembayaranKeHakMilik
        }
        val nextProgramStatus = when {
            targetItem.jenisProgram != "Sewa Hak Milik" -> targetItem.statusProgramHakMilik
            targetItem.totalPeriodeHakMilik > 0 && nextPaymentSequence >= targetItem.totalPeriodeHakMilik -> "Lunas"
            else -> "Aktif"
        }
        val newUnitPaidTotal = (targetItem.nominalPembayaran + nominalPembayaran.coerceAtLeast(0L)).coerceAtLeast(0L)
        val newUnitPayStatus = when {
            targetItem.jenisProgram == "Sewa Hak Milik" && nextProgramStatus == "Lunas" -> "Lunas"
            targetItem.hargaSewa > 0L && newUnitPaidTotal >= targetItem.hargaSewa -> "Lunas"
            newUnitPaidTotal > 0L -> "Dibayar Sebagian"
            else -> targetItem.statusPembayaran
        }

        // 1. Persist PaymentProof linked to transaction & unitItemId via Transactional Outbox
        val autoNote = buildString {
            if (catatanPembayaran.isNotBlank()) {
                append(catatanPembayaran)
                append(" • ")
            }
            append("${targetItem.namaUnitSnapshot} (${targetItem.kodeUnitSnapshot})")
            if (targetItem.jenisProgram == "Sewa Hak Milik" && targetItem.totalPeriodeHakMilik > 0) {
                append(" • Cicilan Ke-$nextPaymentSequence dari ${targetItem.totalPeriodeHakMilik}")
            }
        }
        val proof = preparePaymentProofForSave(
            incoming = PaymentProofEntity(
                transactionId = transactionId,
                tanggalPembayaran = tanggalPembayaran,
                nominalPembayaran = nominalPembayaran.coerceAtLeast(0L),
                metodePembayaran = metodePembayaran,
                namaPengirim = namaPengirim.ifBlank { parentTx.namaPelangganSnapshot },
                catatanPembayaran = autoNote,
                fotoBuktiUri = fotoBuktiUri,
                waktuUpload = nowDisplay,
                unitItemId = unitItemId,
                alokasiUnitJson = JSONObject().apply {
                    put("unitItemId", unitItemId)
                    put("unitId", targetItem.unitId)
                    put("kodeUnit", targetItem.kodeUnitSnapshot)
                    put("pembayaranKeHakMilik", nextPaymentSequence)
                    put("totalPeriodeHakMilik", targetItem.totalPeriodeHakMilik)
                }.toString()
            ),
            existing = null
        )
        val proofChangedFields = MirrorOutboxCodec.computePaymentProofChangedFieldsJson(null, proof)
        val savedProofId = dao.savePaymentProofWithOutboxAtomic(
            proof = proof,
            isNew = true,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = proofChangedFields,
            serverTimestampMillis = currentServerTimeRef()
        )
        val savedProof = dao.getPaymentProofById(savedProofId) ?: proof.copy(id = savedProofId)
        mirrorSyncEngine?.onPaymentOutboxCommitted(proof = savedProof, isNew = true)

        // 2. Persist UnitPaymentAllocationEntity specifically tied to this unitItemId & unitId
        dao.insertUnitPaymentAllocation(
            UnitPaymentAllocationEntity(
                paymentProofId = savedProofId,
                transactionId = transactionId,
                unitItemId = unitItemId,
                customerId = parentTx.customerId,
                unitId = targetItem.unitId,
                namaUnitSnapshot = targetItem.namaUnitSnapshot,
                kodeUnitSnapshot = targetItem.kodeUnitSnapshot,
                allocatedAmount = nominalPembayaran.coerceAtLeast(0L),
                paymentDate = tanggalPembayaran,
                periodeTagihan = if (targetItem.jenisProgram == "Sewa Hak Milik" && targetItem.totalPeriodeHakMilik > 0) {
                    "Ke-$nextPaymentSequence/${targetItem.totalPeriodeHakMilik}"
                } else {
                    targetItem.periodeTagihan
                },
                metodePembayaran = metodePembayaran,
                catatan = autoNote,
                createdAt = nowDisplay
            )
        )

        // 3. Update ONLY this specific unit item (sibling units remain 100% untouched)
        val updatedItem = targetItem.copy(
            pembayaranKeHakMilik = nextPaymentSequence,
            statusProgramHakMilik = nextProgramStatus,
            nominalPembayaran = newUnitPaidTotal,
            statusPembayaran = newUnitPayStatus,
            updatedAt = nowDisplay
        )
        dao.updateTransactionUnitItem(updatedItem)

        // 4. Recalculate parent transaction aggregates
        recalculateParentTransactionFromUnitItems(transactionId)

        // 5. Log customer timeline event
        logCustomerTimelineEvent(
            customerId = parentTx.customerId,
            transactionId = transactionId,
            unitId = targetItem.unitId,
            eventType = "PEMBAYARAN",
            title = if (targetItem.jenisProgram == "Sewa Hak Milik") {
                "Cicilan Hak Milik Ke-$nextPaymentSequence/${targetItem.totalPeriodeHakMilik} (${targetItem.namaUnitSnapshot})"
            } else {
                "Pembayaran Unit ${targetItem.namaUnitSnapshot}"
            },
            description = "$metodePembayaran • ${DateCurrencyUtils.formatRupiah(nominalPembayaran)} • $autoNote",
            amount = nominalPembayaran
        )

        return updatedItem
    }

    // --- 4. AUDITED CORRECTION FOR PERMANENT LOCKED OFFICER SNAPSHOT ---

    suspend fun getOfficerAuditLogsByTransactionOnce(transactionId: Long): List<TransactionOfficerAuditLogEntity> =
        dao.getOfficerAuditLogsByTransactionOnce(transactionId)

    /**
     * Explicit audited correction flow for changing a locked officer name or signature on a saved transaction.
     * Requires a mandatory non-blank `correctionReason` and records a permanent audit trail row.
     */
    suspend fun correctTransactionOfficerWithAuditLog(
        transactionId: Long,
        newOfficerName: String,
        newSignatureUri: String,
        correctedBy: String,
        correctionReason: String
    ): RentalTransactionEntity {
        val reason = correctionReason.trim()
        require(reason.isNotBlank()) { "Alasan koreksi identitas petugas wajib diisi." }
        val existingTx = dao.getTransactionById(transactionId)
            ?: throw IllegalArgumentException("Transaksi tidak ditemukan.")
        val resolvedNewName = newOfficerName.trim().ifBlank { existingTx.namaPetugas }
        val resolvedNewSig = newSignatureUri.trim().ifBlank { existingTx.tandaTanganPetugasUri }
        val nowMs = System.currentTimeMillis()
        val nowDisplay = DateCurrencyUtils.nowDateTimeDisplay()

        // 1. Record permanent audit log entry
        dao.insertTransactionOfficerAuditLog(
            TransactionOfficerAuditLogEntity(
                transactionId = transactionId,
                previousOfficerName = existingTx.namaPetugas,
                newOfficerName = resolvedNewName,
                previousSignatureUri = existingTx.tandaTanganPetugasUri,
                newSignatureUri = resolvedNewSig,
                correctedBy = correctedBy.trim().ifBlank { "Admin" },
                correctionReason = reason,
                correctedAtMillis = nowMs,
                correctedAtDisplay = nowDisplay
            )
        )

        // 2. Update transaction snapshot explicitly with allowOfficerCorrection = true
        val correctedTx = prepareTransactionForSave(
            incoming = existingTx.copy(
                namaPetugas = resolvedNewName,
                tandaTanganPetugasUri = resolvedNewSig,
                officerSnapshotLocked = true,
                officerSignedAt = nowDisplay
            ),
            existing = existingTx,
            now = nowMs,
            allowOfficerCorrection = true
        )
        val changedFields = MirrorOutboxCodec.computeTransactionChangedFieldsJson(existingTx, correctedTx)
        dao.saveTransactionWithOutboxAtomic(
            transaction = correctedTx,
            isNew = false,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            changedFieldsJson = changedFields,
            serverTimestampMillis = currentServerTimeRef()
        )

        // 3. Update PETUGAS_SAF signature record if signature URI provided
        if (resolvedNewSig.isNotBlank()) {
            dao.deleteTransactionSignatureByRole(transactionId, "PETUGAS_SAF")
            dao.insertTransactionSignature(
                TransactionSignatureEntity(
                    transactionId = transactionId,
                    customerId = existingTx.customerId,
                    signerRole = "PETUGAS_SAF",
                    signerName = resolvedNewName,
                    signatureMethod = "DRAW",
                    filePath = resolvedNewSig,
                    hasTransparentBackground = true,
                    signedDate = DateCurrencyUtils.todayIso(),
                    createdAt = nowDisplay
                )
            )
        }

        val persisted = dao.getTransactionById(transactionId) ?: correctedTx
        mirrorSyncEngine?.onTransactionOutboxCommitted(persisted, isNew = false)
        return persisted
    }

    // =========================================================================
    // V25 LOCATION PHOTO SYNC STATUS HELPERS
    // =========================================================================

    suspend fun getCustomerPhotosByTransactionOnce(transactionId: Long): List<CustomerPhotoEntity> =
        dao.getCustomerPhotosByTransactionOnce(transactionId)

    suspend fun updateCustomerPhotoSyncStatus(
        photoId: Long,
        uploadStatus: String,
        driveFileId: String = ""
    ) {
        dao.updateCustomerPhotoSyncStatus(
            photoId = photoId,
            uploadStatus = uploadStatus,
            driveFileId = driveFileId,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun updateCustomerPhotoSyncStatusByPath(
        filePath: String,
        uploadStatus: String,
        driveFileId: String = ""
    ) {
        if (filePath.isBlank()) return
        dao.updateCustomerPhotoSyncStatusByPath(
            filePath = filePath,
            uploadStatus = uploadStatus,
            driveFileId = driveFileId,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    // =========================================================================
    // V25 DETAILED MULTI-UNIT PERPANJANGAN SEWA + TAMBAH UNIT BARU (ATOMIC)
    // =========================================================================

    suspend fun buildCustomerRenewalProfile(customerId: Long): CustomerRenewalProfile? {
        val customer = dao.getCustomerById(customerId) ?: return null
        val allUnits = dao.getAllUnitsOnce()
        val allUnitPrices = dao.getAllUnitRentalPricesOnce()
        val allTxs = dao.getAllTransactionsOnce()
        val allUnitItems = dao.getAllTransactionUnitItemsOnce()
        val allAllocations = dao.getAllUnitPaymentAllocationsOnce()
        val allExtensions = dao.getAllExtensionsOnce()
        return RentalRenewalCalculator.buildCustomerProfile(
            customer = customer,
            allUnits = allUnits,
            allUnitPrices = allUnitPrices,
            allTransactions = allTxs,
            allUnitItems = allUnitItems,
            allAllocations = allAllocations,
            allExtensions = allExtensions
        )
    }

    /**
     * Executes an atomic multi-unit renewal transaction (optionally combined with adding new available units).
     * Guarantees:
     * 1. Untouched active units of the customer remain 100% unmodified.
     * 2. Sewa Hak Milik units strictly continue from persisted history (`paid + 1` of `totalPeriode`).
     * 3. Newly added units MUST be strictly `"Tersedia"` in live SQLite and use exact master data prices.
     * 4. All DB updates + outbox entries execute inside a single Room `@Transaction`.
     */
    suspend fun executeDetailedRenewalTransaction(
        request: DetailedRenewalRequest
    ): DetailedRenewalResult {
        require(request.selectedRenewals.isNotEmpty() || request.additionalNewUnits.isNotEmpty()) {
            "Pilih minimal 1 unit untuk diperpanjang atau ditambahkan."
        }
        val customer = dao.getCustomerById(request.customerId)
            ?: throw IllegalArgumentException("Pelanggan tidak ditemukan di database.")

        val profile = buildCustomerRenewalProfile(request.customerId)
            ?: throw IllegalArgumentException("Gagal memuat profil penyewaan pelanggan.")
        val activeByUnitId = profile.activeUnits.associateBy { it.unitId }

        // Validate no duplicate unit IDs across renewals and new units
        val renewUnitIds = request.selectedRenewals.map { it.unitId }
        require(renewUnitIds.size == renewUnitIds.distinct().size) {
            "Unit yang diperpanjang tidak boleh duplikat."
        }
        val newUnitIds = request.additionalNewUnits.map { it.unitId }
        require(newUnitIds.size == newUnitIds.distinct().size) {
            "Unit tambahan baru tidak boleh duplikat."
        }
        require(renewUnitIds.intersect(newUnitIds.toSet()).isEmpty()) {
            "Unit yang sedang diperpanjang tidak boleh dipilih ulang sebagai unit baru."
        }

        // Strict live SQLite validation + master price enforcement for every additional new unit
        val allUnitPrices = dao.getAllUnitRentalPricesOnce()
        val nowMs = System.currentTimeMillis()
        val nowIso = DateCurrencyUtils.todayIso()
        val nowDisplay = DateCurrencyUtils.nowDateTimeDisplay()

        val validatedNewUnits = mutableListOf<Pair<NewUnitDuringRenewalSelection, FreezerUnitEntity>>()
        val updatedUnitsToRented = mutableListOf<Pair<FreezerUnitEntity, String>>()

        request.additionalNewUnits.forEach { newSel ->
            val liveUnit = dao.getUnitById(newSel.unitId)
                ?: throw IllegalStateException("Unit tambahan (ID ${newSel.unitId}) tidak ditemukan.")
            if (!liveUnit.isStrictlyAvailableForNewRental()) {
                throw IllegalStateException(
                    "Unit ${liveUnit.kodeUnit} (${liveUnit.namaUnit}) berstatus '${liveUnit.statusUnit}' dan tidak memenuhi syarat untuk disewakan."
                )
            }
            val activeTxs = dao.getActiveTransactionsForUnitOnce(liveUnit.id)
            val activeItems = dao.getActiveUnitItemsForUnitOnce(liveUnit.id)
            if (activeTxs.isNotEmpty() || activeItems.isNotEmpty()) {
                throw IllegalStateException(
                    "Unit ${liveUnit.kodeUnit} masih tercatat pada transaksi aktif lain."
                )
            }
            val masterPrice = RentalRenewalCalculator.resolveMasterPriceForUnit(
                unit = liveUnit,
                unitPrices = allUnitPrices,
                jenisProgram = newSel.jenisProgram,
                periodDays = newSel.periodDays
            )
            val safeNewStart = newSel.tanggalMulaiSewa.ifBlank { nowIso }
            val safeNewDays = newSel.periodDays.coerceAtLeast(1)
            val safeNewEnd = DateCurrencyUtils.addDaysToIsoDate(safeNewStart, safeNewDays)
            val totalHmNew = if (newSel.isHakMilik) {
                newSel.totalPeriodeHakMilik.takeIf { it > 0 } ?: liveUnit.jumlahCicilanTotal.takeIf { it > 0 } ?: 12
            } else 0
            val enforcedSelection = newSel.copy(
                kodeUnit = liveUnit.kodeUnit,
                namaUnit = liveUnit.namaUnit,
                merek = liveUnit.merek,
                kapasitasLiter = liveUnit.kapasitasLiter,
                nomorSeri = liveUnit.nomorSeri,
                hargaSewaMaster = masterPrice,
                totalPeriodeHakMilik = totalHmNew,
                tanggalMulaiSewa = safeNewStart,
                tanggalAkhirSewa = safeNewEnd
            )
            validatedNewUnits.add(enforcedSelection to liveUnit)

            val updatedUnit = prepareUnitForSave(
                incoming = liveUnit.copy(statusUnit = "Disewa"),
                existing = liveUnit,
                now = nowMs
            )
            val unitChangedJson = MirrorOutboxCodec.computeUnitChangedFieldsJson(liveUnit, updatedUnit)
            updatedUnitsToRented.add(updatedUnit to unitChangedJson)
        }

        // Normalize & verify every renewed unit against persisted history (especially Hak Milik continuity!)
        val normalizedRenewals = mutableListOf<UnitRenewalSelection>()
        val updatedSourceUnitItems = mutableListOf<TransactionUnitItemEntity>()
        val sourceTxIdsToAppendNote = mutableSetOf<Long>()
        val fulfilledHakMilikNames = mutableListOf<String>()

        request.selectedRenewals.forEach { sel ->
            val persistedInfo = activeByUnitId[sel.unitId]
                ?: throw IllegalStateException("Unit ${sel.kodeUnit} tidak tercatat sebagai unit aktif milik ${customer.namaLengkap}.")
            val safeDays = sel.periodDays.coerceAtLeast(1)
            val newStart = sel.tanggalMulaiBaru.ifBlank {
                persistedInfo.tanggalAkhirSewa.ifBlank { nowIso }
            }
            val newEnd = DateCurrencyUtils.addDaysToIsoDate(newStart, safeDays)

            val isHm = persistedInfo.isHakMilik
            val totalHm = if (isHm) persistedInfo.totalPeriodeHakMilik.coerceAtLeast(1) else 0
            val prevPaidHm = if (isHm) persistedInfo.sudahDibayarHakMilik else 0
            // CRITICAL: Always continue strictly from persisted history (`prevPaidHm + 1`), NEVER restart at 1!
            val nextPaidHm = if (isHm) {
                (prevPaidHm + 1).coerceAtMost(totalHm)
            } else 1

            val resolvedPrice = if (sel.hargaSewa > 0L) sel.hargaSewa else persistedInfo.resolveMasterPriceForPeriod(safeDays)
            val normalized = sel.copy(
                unitItemId = persistedInfo.unitItemId,
                sourceTransactionId = persistedInfo.sourceTransactionId,
                sourceInvoiceNumber = persistedInfo.sourceInvoiceNumber,
                kodeUnit = persistedInfo.kodeUnit,
                namaUnit = persistedInfo.namaUnit,
                nomorSeri = persistedInfo.nomorSeri,
                jenisProgram = persistedInfo.jenisProgram,
                periodDays = safeDays,
                tanggalMulaiBaru = newStart,
                tanggalAkhirBaru = newEnd,
                tanggalJatuhTempoBaru = sel.tanggalJatuhTempoBaru.ifBlank { newEnd },
                hargaSewa = resolvedPrice,
                totalPeriodeHakMilik = totalHm,
                pembayaranKeSebelumnya = prevPaidHm,
                pembayaranKeBaru = nextPaidHm
            )
            normalizedRenewals.add(normalized)
            sourceTxIdsToAppendNote.add(persistedInfo.sourceTransactionId)

            val isFulfilledNow = isHm && nextPaidHm >= totalHm && normalized.statusPembayaran == "Lunas"
            if (isFulfilledNow) {
                fulfilledHakMilikNames.add("${persistedInfo.kodeUnit} (${persistedInfo.namaUnit})")
            }

            // Update ONLY this specific source TransactionUnitItemEntity (leaving all other units untouched!)
            if (persistedInfo.unitItemId > 0L) {
                val existingItem = dao.getTransactionUnitItemById(persistedInfo.unitItemId)
                if (existingItem != null) {
                    val updatedItem = existingItem.copy(
                        tanggalAkhirSewa = newEnd,
                        tanggalJatuhTempo = normalized.tanggalJatuhTempoBaru,
                        pembayaranKeHakMilik = if (isHm) nextPaidHm else existingItem.pembayaranKeHakMilik,
                        totalPeriodeHakMilik = if (isHm) totalHm else existingItem.totalPeriodeHakMilik,
                        statusProgramHakMilik = when {
                            isFulfilledNow -> "Selesai"
                            isHm && normalized.statusPembayaran == "Lunas" -> "Lunas"
                            else -> existingItem.statusProgramHakMilik
                        },
                        statusPembayaran = normalized.statusPembayaran,
                        statusPenyewaan = if (isFulfilledNow) "Selesai" else "Dalam Penyewaan",
                        updatedAt = nowDisplay
                    )
                    updatedSourceUnitItems.add(updatedItem)
                }
            }
        }

        val summary = RentalRenewalCalculator.calculateIntegratedSummary(
            renewals = normalizedRenewals,
            newUnits = validatedNewUnits.map { it.first },
            ongkirTambahan = request.ongkirTambahan,
            globalDiscount = request.diskonTambahan
        )

        val newInvoice = generateUniqueInvoiceNumber()
        val primaryRenewal = normalizedRenewals.firstOrNull()
        val primaryNewUnit = validatedNewUnits.firstOrNull()?.first
        val primaryUnitId = primaryRenewal?.unitId ?: primaryNewUnit?.unitId ?: 0L
        val primarySourceTx = primaryRenewal?.let { dao.getTransactionById(it.sourceTransactionId) }
        val allUnitCodes = buildList {
            normalizedRenewals.forEach { add(it.kodeUnit) }
            validatedNewUnits.forEach { add(it.first.kodeUnit) }
        }
        val allUnitNames = buildList {
            normalizedRenewals.forEach { add(it.namaUnit) }
            validatedNewUnits.forEach { add(it.first.namaUnit) }
        }
        val combinedCodes = allUnitCodes.joinToString(", ")
        val combinedNames = allUnitNames.joinToString(", ")
        val firstStart = primaryRenewal?.tanggalMulaiBaru ?: primaryNewUnit?.tanggalMulaiSewa ?: nowIso
        val maxEnd = (normalizedRenewals.map { it.tanggalAkhirBaru } + validatedNewUnits.map { it.first.tanggalAkhirSewa })
            .filter { it.isNotBlank() }
            .maxOrNull()
            ?: DateCurrencyUtils.addDaysToIsoDate(firstStart, 30)
        val maxDays = (normalizedRenewals.map { it.periodDays } + validatedNewUnits.map { it.first.periodDays })
            .maxOrNull()
            ?.coerceAtLeast(1) ?: 30
        val approxMonths = ceil(maxDays / 30.0).toInt().coerceAtLeast(1)

        val primaryProgram = when {
            normalizedRenewals.size == 1 && validatedNewUnits.isEmpty() -> normalizedRenewals.first().jenisProgram
            normalizedRenewals.all { it.isHakMilik } && validatedNewUnits.all { it.first.isHakMilik } -> "Sewa Hak Milik"
            else -> primaryRenewal?.jenisProgram ?: primaryNewUnit?.jenisProgram ?: "Sewa Reguler"
        }
        val totalPeriodeHeader = primaryRenewal?.takeIf { it.isHakMilik }?.totalPeriodeHakMilik
            ?: primaryNewUnit?.takeIf { it.isHakMilik }?.totalPeriodeHakMilik
            ?: 0
        val pembayaranKeHeader = primaryRenewal?.takeIf { it.isHakMilik }?.pembayaranKeBaru ?: 1

        val catatanDetail = buildString {
            append("Perpanjangan ${normalizedRenewals.size} unit")
            if (validatedNewUnits.isNotEmpty()) {
                append(" + Tambah ${validatedNewUnits.size} unit baru")
            }
            if (request.catatanUmum.isNotBlank()) {
                append(". ${request.catatanUmum.trim()}")
            }
        }

        val newTx = prepareTransactionForSave(
            incoming = RentalTransactionEntity(
                id = 0L,
                uuid = MirrorOutboxCodec.generateUuidV4(),
                version = 1L,
                nomorInvoice = newInvoice,
                customerId = customer.id,
                unitId = primaryUnitId,
                namaPelangganSnapshot = customer.namaLengkap,
                namaUnitSnapshot = combinedNames,
                kodeUnitSnapshot = combinedCodes,
                tanggalPemesanan = nowIso,
                hargaSewa = summary.totalRenewalGross + summary.totalNewUnitsGross,
                durasiSewaBulan = approxMonths,
                durasiHari = maxDays,
                tanggalMulaiSewa = firstStart,
                tanggalAkhirSewa = maxEnd,
                tanggalJatuhTempo = maxEnd,
                metodePembayaran = request.metodePembayaran.ifBlank { "Transfer Bank" },
                nominalPembayaran = summary.totalPaid,
                ongkir = summary.ongkirTambahan,
                deposit = 0L,
                diskon = summary.totalItemDiscounts + summary.globalDiscount,
                totalTagihan = summary.grandTotal,
                statusPembayaran = summary.paymentStatus,
                statusPenyewaan = "Dalam Penyewaan",
                catatan = catatanDetail,
                alamatLokasiGps = primarySourceTx?.alamatLokasiGps ?: customer.alamatLengkap,
                latitude = primarySourceTx?.latitude ?: customer.latitudeDefault,
                longitude = primarySourceTx?.longitude ?: customer.longitudeDefault,
                linkGoogleMaps = primarySourceTx?.linkGoogleMaps ?: "",
                namaPetugas = request.namaPetugas,
                officerAccountId = request.officerAccountId,
                officerSnapshotLocked = request.namaPetugas.isNotBlank(),
                officerSignedAt = if (request.namaPetugas.isNotBlank()) nowDisplay else "",
                parentTransactionId = primaryRenewal?.sourceTransactionId,
                jenisProgram = primaryProgram,
                totalPeriodeHakMilik = totalPeriodeHeader,
                pembayaranKeHakMilik = pembayaranKeHeader,
                statusProgramHakMilik = if (fulfilledHakMilikNames.isNotEmpty() && normalizedRenewals.size == 1 && validatedNewUnits.isEmpty()) "Selesai" else "Aktif"
            ),
            existing = null,
            now = nowMs
        )
        val newTxChangedFields = MirrorOutboxCodec.computeTransactionChangedFieldsJson(null, newTx)

        // Build new TransactionUnitItemEntity list (renewed units + newly added units)
        val newUnitItems = mutableListOf<TransactionUnitItemEntity>()
        val extensionRecords = mutableListOf<RentalExtensionHistoryEntity>()
        val paymentAllocations = mutableListOf<UnitPaymentAllocationEntity>()
        var idxCounter = 1

        normalizedRenewals.forEach { ren ->
            val masterUnit = dao.getUnitById(ren.unitId)
            val isFulfilled = ren.isHakMilik && ren.pembayaranKeBaru >= ren.totalPeriodeHakMilik && ren.statusPembayaran == "Lunas"
            newUnitItems.add(
                TransactionUnitItemEntity(
                    id = 0L,
                    transactionId = 0L,
                    customerId = customer.id,
                    unitId = ren.unitId,
                    unitIndexNumber = idxCounter++,
                    kodeUnitSnapshot = ren.kodeUnit,
                    namaUnitSnapshot = ren.namaUnit,
                    jenisUnitSnapshot = masterUnit?.jenisUnit ?: "",
                    merekSnapshot = masterUnit?.merek ?: "",
                    kapasitasLiterSnapshot = masterUnit?.kapasitasLiter ?: 0,
                    jenisProgram = ren.jenisProgram,
                    totalPeriodeHakMilik = ren.totalPeriodeHakMilik,
                    pembayaranKeHakMilik = ren.pembayaranKeBaru,
                    statusProgramHakMilik = when {
                        isFulfilled -> "Selesai"
                        ren.isHakMilik && ren.statusPembayaran == "Lunas" -> "Lunas"
                        else -> "Aktif"
                    },
                    hargaSewa = ren.tagihanBersih,
                    durasiHari = ren.periodDays,
                    tanggalMulaiSewa = ren.tanggalMulaiBaru,
                    tanggalAkhirSewa = ren.tanggalAkhirBaru,
                    tanggalJatuhTempo = ren.tanggalJatuhTempoBaru,
                    nominalPembayaran = ren.nominalDibayar,
                    statusPembayaran = ren.statusPembayaran,
                    statusPenyewaan = if (isFulfilled) "Selesai" else "Dalam Penyewaan",
                    catatanUnit = ren.catatan.ifBlank {
                        if (ren.isHakMilik) "Perpanjangan Hak Milik Ke-${ren.pembayaranKeBaru}/${ren.totalPeriodeHakMilik}"
                        else "Perpanjangan Reguler ${ren.periodDays} Hari"
                    },
                    createdAt = nowDisplay,
                    updatedAt = nowDisplay,
                    periodeTagihan = "${ren.periodDays} Hari"
                )
            )
            extensionRecords.add(
                RentalExtensionHistoryEntity(
                    id = 0L,
                    originalTransactionId = ren.sourceTransactionId,
                    newTransactionId = 0L,
                    nomorInvoiceLama = ren.sourceInvoiceNumber,
                    nomorInvoiceBaru = newInvoice,
                    tanggalPerpanjangan = nowIso,
                    tanggalAkhirLama = activeByUnitId[ren.unitId]?.tanggalAkhirSewa.orEmpty(),
                    tanggalAkhirBaru = ren.tanggalAkhirBaru,
                    tambahanDurasiHari = ren.periodDays,
                    tagihanBaru = ren.tagihanBersih,
                    catatan = ren.catatan.ifBlank {
                        if (ren.isHakMilik) "Pembayaran Hak Milik ke-${ren.pembayaranKeBaru} dari ${ren.totalPeriodeHakMilik}"
                        else "Perpanjangan unit ${ren.kodeUnit} (${ren.periodDays} hari)"
                    },
                    uuid = MirrorOutboxCodec.generateUuidV4(),
                    customerId = customer.id,
                    unitId = ren.unitId,
                    unitItemId = ren.unitItemId,
                    jenisProgram = ren.jenisProgram,
                    pembayaranKeHakMilik = if (ren.isHakMilik) ren.pembayaranKeBaru else 0,
                    totalPeriodeHakMilik = ren.totalPeriodeHakMilik,
                    diskon = ren.diskon,
                    nominalDibayar = ren.nominalDibayar,
                    statusPembayaran = ren.statusPembayaran,
                    updatedAtMillis = nowMs
                )
            )
            if (ren.nominalDibayar > 0L) {
                paymentAllocations.add(
                    UnitPaymentAllocationEntity(
                        id = 0L,
                        paymentProofId = 0L,
                        transactionId = 0L,
                        unitItemId = 0L,
                        customerId = customer.id,
                        unitId = ren.unitId,
                        namaUnitSnapshot = ren.namaUnit,
                        kodeUnitSnapshot = ren.kodeUnit,
                        allocatedAmount = ren.nominalDibayar,
                        paymentDate = nowIso,
                        periodeTagihan = if (ren.isHakMilik) {
                            "Hak Milik Ke-${ren.pembayaranKeBaru}/${ren.totalPeriodeHakMilik}"
                        } else {
                            "${ren.periodDays} Hari"
                        },
                        metodePembayaran = request.metodePembayaran,
                        catatan = if (ren.isHakMilik) {
                            "Pembayaran Hak Milik ke-${ren.pembayaranKeBaru}/${ren.totalPeriodeHakMilik} ($newInvoice)"
                        } else {
                            "Perpanjangan Reguler ${ren.periodDays} hari ($newInvoice)"
                        },
                        createdAt = nowDisplay
                    )
                )
            }
        }

        validatedNewUnits.forEach { (newSel, masterUnit) ->
            newUnitItems.add(
                TransactionUnitItemEntity(
                    id = 0L,
                    transactionId = 0L,
                    customerId = customer.id,
                    unitId = newSel.unitId,
                    unitIndexNumber = idxCounter++,
                    kodeUnitSnapshot = masterUnit.kodeUnit,
                    namaUnitSnapshot = masterUnit.namaUnit,
                    jenisUnitSnapshot = masterUnit.jenisUnit,
                    merekSnapshot = masterUnit.merek,
                    kapasitasLiterSnapshot = masterUnit.kapasitasLiter,
                    jenisProgram = newSel.jenisProgram,
                    totalPeriodeHakMilik = newSel.totalPeriodeHakMilik,
                    pembayaranKeHakMilik = 1,
                    statusProgramHakMilik = "Aktif",
                    hargaSewa = newSel.tagihanBersih,
                    durasiHari = newSel.periodDays,
                    tanggalMulaiSewa = newSel.tanggalMulaiSewa,
                    tanggalAkhirSewa = newSel.tanggalAkhirSewa,
                    tanggalJatuhTempo = newSel.tanggalAkhirSewa,
                    nominalPembayaran = newSel.nominalDibayar,
                    statusPembayaran = newSel.statusPembayaran,
                    statusPenyewaan = "Dalam Penyewaan",
                    catatanUnit = newSel.catatan.ifBlank { "Unit Baru Tambahan Saat Perpanjangan ($newInvoice)" },
                    createdAt = nowDisplay,
                    updatedAt = nowDisplay,
                    periodeTagihan = "${newSel.periodDays} Hari"
                )
            )
            if (newSel.nominalDibayar > 0L) {
                paymentAllocations.add(
                    UnitPaymentAllocationEntity(
                        id = 0L,
                        paymentProofId = 0L,
                        transactionId = 0L,
                        unitItemId = 0L,
                        customerId = customer.id,
                        unitId = newSel.unitId,
                        namaUnitSnapshot = masterUnit.namaUnit,
                        kodeUnitSnapshot = masterUnit.kodeUnit,
                        allocatedAmount = newSel.nominalDibayar,
                        paymentDate = nowIso,
                        periodeTagihan = "${newSel.periodDays} Hari",
                        metodePembayaran = request.metodePembayaran,
                        catatan = "Unit tambahan baru saat perpanjangan ($newInvoice)",
                        createdAt = nowDisplay
                    )
                )
            }
        }

        // Append non-destructive extension reference note to source transactions without altering their amounts or other units
        val updatedSourceTxs = mutableListOf<Pair<RentalTransactionEntity, String>>()
        sourceTxIdsToAppendNote.forEach { srcTxId ->
            val srcTx = dao.getTransactionById(srcTxId) ?: return@forEach
            val renewedCodesForSrc = normalizedRenewals.filter { it.sourceTransactionId == srcTxId }
                .joinToString(", ") { it.kodeUnit }
            val updatedSrc = prepareTransactionForSave(
                incoming = srcTx.copy(
                    catatan = (srcTx.catatan + " | Perpanjangan [$renewedCodesForSrc] -> $newInvoice").trim(' ', '|')
                ),
                existing = srcTx,
                now = nowMs
            )
            val srcChanged = MirrorOutboxCodec.computeTransactionChangedFieldsJson(srcTx, updatedSrc)
            updatedSourceTxs.add(updatedSrc to srcChanged)
        }

        val timelineEvent = CustomerTimelineEventEntity(
            customerId = customer.id,
            transactionId = null,
            unitId = primaryUnitId.takeIf { it > 0L },
            eventType = "PERPANJANGAN",
            title = "Perpanjangan Sewa Terpadu ($newInvoice)",
            description = buildString {
                append("${normalizedRenewals.size} unit diperpanjang")
                if (validatedNewUnits.isNotEmpty()) append(", ${validatedNewUnits.size} unit baru ditambahkan")
                append(" • Total ${DateCurrencyUtils.formatRupiah(summary.grandTotal)} • Dibayar ${DateCurrencyUtils.formatRupiah(summary.totalPaid)}")
            },
            amount = summary.totalPaid,
            eventTimestamp = nowDisplay,
            eventDateIso = nowIso
        )

        val newTxId = dao.executeDetailedRenewalAtomic(
            newTransaction = newTx,
            newTxChangedFieldsJson = newTxChangedFields,
            newUnitItems = newUnitItems,
            updatedSourceUnitItems = updatedSourceUnitItems,
            updatedSourceTransactions = updatedSourceTxs,
            extensionRecords = extensionRecords,
            paymentAllocations = paymentAllocations,
            updatedNewUnitsToRented = updatedUnitsToRented,
            timelineEvent = timelineEvent,
            datasetId = currentDatasetId(),
            datasetGenerationId = currentDatasetGenerationId(),
            serverTimestampMillis = currentServerTimeRef()
        )

        val persistedNewTx = dao.getTransactionById(newTxId) ?: newTx.copy(id = newTxId)
        mirrorSyncEngine?.onTransactionOutboxCommitted(persistedNewTx, isNew = true)
        updatedSourceTxs.forEach { (srcTx, _) ->
            mirrorSyncEngine?.onTransactionOutboxCommitted(srcTx, isNew = false)
        }
        updatedUnitsToRented.forEach { (u, _) ->
            mirrorSyncEngine?.onUnitOutboxCommitted(u, isNew = false)
        }

        return DetailedRenewalResult(
            transaction = persistedNewTx,
            renewedUnitsCount = normalizedRenewals.size,
            addedNewUnitsCount = validatedNewUnits.size,
            fulfilledHakMilikUnitNames = fulfilledHakMilikNames
        )
    }

    /**
     * Post-Full-Restore Cloud Reconciliation:
     * 1. Records deletion tombstones & DELETE outbox jobs for any pre-restore record that was replaced
     *    so Google Drive removes obsolete pre-restore cloud files and NEVER resurrects them.
     * 2. Re-enqueues all restored active records under the newly rotated dataset generation so
     *    Google Drive mirrors the restored database 100% cleanly without duplicates.
     */
    suspend fun reconcileOutboxAfterFullRestore(
        preRestoreCustomers: List<CustomerEntity>,
        preRestoreUnits: List<FreezerUnitEntity>,
        preRestoreTransactions: List<RentalTransactionEntity>,
        preRestorePayments: List<PaymentProofEntity>,
        datasetId: String,
        datasetGenerationId: String
    ) {
        val nowMs = currentServerTimeRef()
        val existingMaps = dao.getAllDriveCloudFileMappingsOnce().associateBy { it.localRecordKey }
        val existingMapsByUuid = dao.getAllDriveCloudFileMappingsOnce()
            .filter { it.recordUuid.isNotBlank() }
            .associateBy { it.recordUuid }

        val restoredCustomers = dao.getAllCustomersOnce()
        val restoredUnits = dao.getAllUnitsOnce()
        val restoredTransactions = dao.getAllTransactionsOnce()
        val restoredPayments = dao.getAllPaymentProofsOnce()

        val restoredCustUuids = restoredCustomers.map { it.uuid }.filter { it.isNotBlank() }.toSet()
        val restoredUnitUuids = restoredUnits.map { it.uuid }.filter { it.isNotBlank() }.toSet()
        val restoredTxUuids = restoredTransactions.map { it.uuid }.filter { it.isNotBlank() }.toSet()
        val restoredPayUuids = restoredPayments.map { it.uuid }.filter { it.isNotBlank() }.toSet()

        // Clear stale pending queue items from pre-restore dataset
        dao.deleteAllDriveLocalSyncQueue()

        // Queue DELETE + tombstone for pre-restore records not present in restored backup
        suspend fun enqueueObsoleteDelete(
            category: String,
            id: Long,
            uuid: String,
            driveFileId: String
        ) {
            val key = "$category:$id"
            val existingMap = (if (uuid.isNotBlank()) existingMapsByUuid[uuid] else null) ?: existingMaps[key]
            val plan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                localRecordKey = key,
                recordCategory = category,
                recordId = id,
                recordUuid = uuid,
                explicitDriveFileId = driveFileId,
                datasetGenerationId = datasetGenerationId,
                existingMap = existingMap,
                latestQueue = null,
                serverTimestampMillis = nowMs
            )
            // Queue cloud DELETE job & mark cloud file map as pending delete so Google Drive
            // removes obsolete pre-restore files cleanly without auto-resurrection, while NOT
            // recording a permanent user-deletion tombstone that would block future explicit
            // APPEND_MERGE imports requested by the user.
            if (plan.updatedFileMap != null) {
                dao.upsertDriveCloudFileMap(plan.updatedFileMap)
            }
            if (plan.tombstone.driveFileIdAtDeletion.isNotBlank() || plan.tombstone.recordUuid.isNotBlank()) {
                dao.upsertDriveLocalSyncQueueItem(plan.queueItem)
            }
        }

        preRestoreCustomers.filter { it.uuid.isNotBlank() && it.uuid !in restoredCustUuids }.forEach {
            enqueueObsoleteDelete(MirrorOutboxCodec.CATEGORY_CUSTOMER, it.id, it.uuid, it.driveId)
        }
        preRestoreUnits.filter { it.uuid.isNotBlank() && it.uuid !in restoredUnitUuids }.forEach {
            enqueueObsoleteDelete(MirrorOutboxCodec.CATEGORY_UNIT, it.id, it.uuid, it.driveId)
        }
        preRestoreTransactions.filter { it.uuid.isNotBlank() && it.uuid !in restoredTxUuids }.forEach {
            enqueueObsoleteDelete(MirrorOutboxCodec.CATEGORY_TRANSACTION, it.id, it.uuid, it.driveId)
        }
        preRestorePayments.filter { it.uuid.isNotBlank() && it.uuid !in restoredPayUuids }.forEach {
            enqueueObsoleteDelete(MirrorOutboxCodec.CATEGORY_PAYMENT, it.id, it.uuid, it.driveId)
        }

        // Re-enqueue every restored active record under the new dataset generation
        restoredCustomers.forEach { c ->
            val fixed = c.copy(
                uuid = MirrorOutboxCodec.ensureValidUuidV4(c.uuid),
                updatedAtMillis = maxOf(c.updatedAtMillis, nowMs),
                syncStatus = MirrorOutboxCodec.STATUS_PENDING_UPDATE
            )
            val key = "${MirrorOutboxCodec.CATEGORY_CUSTOMER}:${fixed.id}"
            val existingMap = (existingMapsByUuid[fixed.uuid] ?: existingMaps[key])
                ?.copy(syncStatus = MirrorOutboxCodec.STATUS_PENDING)
            val payload = MirrorOutboxCodec.serializeCustomerJson(fixed, datasetId, datasetGenerationId, nowMs)
            val plan = MirrorOutboxCodec.buildUpsertOutboxPlan(
                localRecordKey = key,
                recordCategory = MirrorOutboxCodec.CATEGORY_CUSTOMER,
                recordId = fixed.id,
                recordUuid = fixed.uuid,
                entityVersion = fixed.version.coerceAtLeast(1L),
                updatedAtMillis = fixed.updatedAtMillis,
                payloadJson = payload,
                isNew = existingMap?.driveFileId.isNullOrBlank(),
                datasetGenerationId = datasetGenerationId,
                changedFieldsJson = """["full_restore"]""",
                existingMap = existingMap,
                latestQueue = null,
                serverTimestampMillis = nowMs,
                explicitDriveFileId = fixed.driveId
            )
            if (plan != null) {
                dao.upsertDriveCloudFileMap(plan.fileMap)
                dao.upsertDriveLocalSyncQueueItem(plan.queueItem)
            }
        }
        restoredUnits.forEach { u ->
            val fixed = u.copy(
                uuid = MirrorOutboxCodec.ensureValidUuidV4(u.uuid),
                updatedAtMillis = maxOf(u.updatedAtMillis, nowMs),
                syncStatus = MirrorOutboxCodec.STATUS_PENDING_UPDATE
            )
            val key = "${MirrorOutboxCodec.CATEGORY_UNIT}:${fixed.id}"
            val existingMap = (existingMapsByUuid[fixed.uuid] ?: existingMaps[key])
                ?.copy(syncStatus = MirrorOutboxCodec.STATUS_PENDING)
            val payload = MirrorOutboxCodec.serializeUnitJson(fixed, datasetId, datasetGenerationId, nowMs)
            val plan = MirrorOutboxCodec.buildUpsertOutboxPlan(
                localRecordKey = key,
                recordCategory = MirrorOutboxCodec.CATEGORY_UNIT,
                recordId = fixed.id,
                recordUuid = fixed.uuid,
                entityVersion = fixed.version.coerceAtLeast(1L),
                updatedAtMillis = fixed.updatedAtMillis,
                payloadJson = payload,
                isNew = existingMap?.driveFileId.isNullOrBlank(),
                datasetGenerationId = datasetGenerationId,
                changedFieldsJson = """["full_restore"]""",
                existingMap = existingMap,
                latestQueue = null,
                serverTimestampMillis = nowMs,
                explicitDriveFileId = fixed.driveId
            )
            if (plan != null) {
                dao.upsertDriveCloudFileMap(plan.fileMap)
                dao.upsertDriveLocalSyncQueueItem(plan.queueItem)
            }
        }
        restoredTransactions.forEach { t ->
            val fixed = t.copy(
                uuid = MirrorOutboxCodec.ensureValidUuidV4(t.uuid),
                updatedAtMillis = maxOf(t.updatedAtMillis, nowMs),
                syncStatus = MirrorOutboxCodec.STATUS_PENDING_UPDATE
            )
            val key = "${MirrorOutboxCodec.CATEGORY_TRANSACTION}:${fixed.id}"
            val existingMap = (existingMapsByUuid[fixed.uuid] ?: existingMaps[key])
                ?.copy(syncStatus = MirrorOutboxCodec.STATUS_PENDING)
            val payload = MirrorOutboxCodec.serializeTransactionJson(fixed, datasetId, datasetGenerationId, nowMs)
            val plan = MirrorOutboxCodec.buildUpsertOutboxPlan(
                localRecordKey = key,
                recordCategory = MirrorOutboxCodec.CATEGORY_TRANSACTION,
                recordId = fixed.id,
                recordUuid = fixed.uuid,
                entityVersion = fixed.version.coerceAtLeast(1L),
                updatedAtMillis = fixed.updatedAtMillis,
                payloadJson = payload,
                isNew = existingMap?.driveFileId.isNullOrBlank(),
                datasetGenerationId = datasetGenerationId,
                changedFieldsJson = """["full_restore"]""",
                existingMap = existingMap,
                latestQueue = null,
                serverTimestampMillis = nowMs,
                explicitDriveFileId = fixed.driveId
            )
            if (plan != null) {
                dao.upsertDriveCloudFileMap(plan.fileMap)
                dao.upsertDriveLocalSyncQueueItem(plan.queueItem)
            }
        }
        restoredPayments.forEach { p ->
            val fixed = p.copy(
                uuid = MirrorOutboxCodec.ensureValidUuidV4(p.uuid),
                updatedAtMillis = maxOf(p.updatedAtMillis, nowMs),
                syncStatus = MirrorOutboxCodec.STATUS_PENDING_UPDATE
            )
            val key = "${MirrorOutboxCodec.CATEGORY_PAYMENT}:${fixed.id}"
            val existingMap = (existingMapsByUuid[fixed.uuid] ?: existingMaps[key])
                ?.copy(syncStatus = MirrorOutboxCodec.STATUS_PENDING)
            val payload = MirrorOutboxCodec.serializePaymentJson(fixed, datasetId, datasetGenerationId, nowMs)
            val plan = MirrorOutboxCodec.buildUpsertOutboxPlan(
                localRecordKey = key,
                recordCategory = MirrorOutboxCodec.CATEGORY_PAYMENT,
                recordId = fixed.id,
                recordUuid = fixed.uuid,
                entityVersion = fixed.version.coerceAtLeast(1L),
                updatedAtMillis = fixed.updatedAtMillis,
                payloadJson = payload,
                isNew = existingMap?.driveFileId.isNullOrBlank(),
                datasetGenerationId = datasetGenerationId,
                changedFieldsJson = """["full_restore"]""",
                existingMap = existingMap,
                latestQueue = null,
                serverTimestampMillis = nowMs,
                explicitDriveFileId = fixed.driveId
            )
            if (plan != null) {
                dao.upsertDriveCloudFileMap(plan.fileMap)
                dao.upsertDriveLocalSyncQueueItem(plan.queueItem)
            }
        }
    }
}



