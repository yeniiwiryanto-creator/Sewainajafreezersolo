package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.drive.MirrorOutboxCodec
import kotlinx.coroutines.flow.Flow

@Dao
interface FreezerDao {

    // --- FREEZER UNITS ---
    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 ORDER BY kodeUnit ASC")
    fun getAllUnits(): Flow<List<FreezerUnitEntity>>

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 ORDER BY kodeUnit ASC")
    suspend fun getAllUnitsOnce(): List<FreezerUnitEntity>

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 AND LOWER(TRIM(statusUnit)) IN ('tersedia', 'available') ORDER BY kodeUnit ASC")
    suspend fun getStrictlyAvailableUnitsOnce(): List<FreezerUnitEntity>

    @Query("SELECT * FROM freezer_units WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getUnitById(id: Long): FreezerUnitEntity?

    @Query("SELECT * FROM freezer_units WHERE id = :id LIMIT 1")
    suspend fun getUnitByIdIncludingDeleted(id: Long): FreezerUnitEntity?

    @Query("SELECT * FROM freezer_units WHERE kodeUnit = :kode AND is_deleted = 0 LIMIT 1")
    suspend fun getUnitByKode(kode: String): FreezerUnitEntity?

    @Query("SELECT * FROM freezer_units WHERE kodeUnit = :kode LIMIT 1")
    suspend fun getUnitByKodeIncludingDeleted(kode: String): FreezerUnitEntity?

    @Query("SELECT * FROM freezer_units WHERE uuid = :uuid AND is_deleted = 0 LIMIT 1")
    suspend fun getUnitByUuid(uuid: String): FreezerUnitEntity?

    @Query("SELECT * FROM freezer_units WHERE uuid = :uuid LIMIT 1")
    suspend fun getUnitByUuidIncludingDeleted(uuid: String): FreezerUnitEntity?

    @Query("SELECT * FROM freezer_units WHERE sync_status IN (:statuses) ORDER BY updated_at ASC, id ASC")
    suspend fun getUnitsBySyncStatuses(statuses: List<String>): List<FreezerUnitEntity>

    @Query("UPDATE freezer_units SET is_deleted = 1, sync_status = :syncStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDeleteUnit(id: Long, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "PENDING_DELETE")

    @Query("UPDATE freezer_units SET sync_status = :syncStatus, drive_id = :driveId WHERE id = :id")
    suspend fun updateUnitSyncAndDriveId(id: Long, syncStatus: String, driveId: String)

    @Query("DELETE FROM freezer_units WHERE id = :id")
    suspend fun hardDeleteUnitById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: FreezerUnitEntity): Long

    @Update
    suspend fun updateUnit(unit: FreezerUnitEntity)

    @Delete
    suspend fun deleteUnit(unit: FreezerUnitEntity)

    @Query("DELETE FROM freezer_units")
    suspend fun deleteAllUnits()

    // --- FREEZER UNIT MAINTENANCE HISTORY (v19) ---
    @Transaction
    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 ORDER BY kodeUnit ASC")
    fun getAllUnitsWithMaintenanceHistory(): Flow<List<FreezerUnitWithMaintenanceHistory>>

    @Transaction
    @Query("SELECT * FROM freezer_units WHERE id = :unitId AND is_deleted = 0 LIMIT 1")
    fun getUnitWithMaintenanceHistoryById(unitId: Long): Flow<FreezerUnitWithMaintenanceHistory?>

    @Query("SELECT * FROM freezer_unit_maintenance WHERE unit_id = :unitId ORDER BY maintenance_date DESC, created_at_millis DESC, id DESC")
    fun getMaintenanceHistoryForUnit(unitId: Long): Flow<List<FreezerUnitMaintenanceEntity>>

    @Query("SELECT * FROM freezer_unit_maintenance WHERE unit_id = :unitId ORDER BY maintenance_date DESC, created_at_millis DESC, id DESC")
    suspend fun getMaintenanceHistoryForUnitOnce(unitId: Long): List<FreezerUnitMaintenanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFreezerUnitMaintenance(record: FreezerUnitMaintenanceEntity): Long

    @Update
    suspend fun updateFreezerUnitMaintenance(record: FreezerUnitMaintenanceEntity)

    @Delete
    suspend fun deleteFreezerUnitMaintenance(record: FreezerUnitMaintenanceEntity)

    @Query("DELETE FROM freezer_unit_maintenance")
    suspend fun deleteAllFreezerUnitMaintenance()

    // --- CUSTOMERS ---
    @Query("SELECT * FROM customers WHERE is_deleted = 0 ORDER BY namaLengkap ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE is_deleted = 0 ORDER BY namaLengkap ASC")
    suspend fun getAllCustomersOnce(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerByIdIncludingDeleted(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE kodePelanggan = :kode AND is_deleted = 0 LIMIT 1")
    suspend fun getCustomerByKode(kode: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE kodePelanggan = :kode LIMIT 1")
    suspend fun getCustomerByKodeIncludingDeleted(kode: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE uuid = :uuid AND is_deleted = 0 LIMIT 1")
    suspend fun getCustomerByUuid(uuid: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE uuid = :uuid LIMIT 1")
    suspend fun getCustomerByUuidIncludingDeleted(uuid: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE nik = :nik AND is_deleted = 0 LIMIT 1")
    suspend fun findCustomerByNik(nik: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE nomorHp != '' AND nomorHp = :hp AND is_deleted = 0 LIMIT 1")
    suspend fun findCustomerByPhone(hp: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE is_deleted = 0 AND (nik = :nik OR (nomorHp != '' AND nomorHp = :hp) OR LOWER(namaLengkap) = LOWER(:nama)) LIMIT 1")
    suspend fun findDuplicateCustomer(nik: String, hp: String, nama: String): CustomerEntity?

    @Query("SELECT COUNT(*) FROM customers WHERE is_deleted = 0")
    suspend fun getCustomerCount(): Int

    @Query("SELECT COUNT(*) FROM rental_transactions WHERE customerId = :customerId AND is_deleted = 0")
    suspend fun getTransactionCountForCustomer(customerId: Long): Int

    @Query("SELECT * FROM customers WHERE sync_status IN (:statuses) ORDER BY updated_at ASC, id ASC")
    suspend fun getCustomersBySyncStatuses(statuses: List<String>): List<CustomerEntity>

    @Query("UPDATE customers SET is_deleted = 1, sync_status = :syncStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDeleteCustomer(id: Long, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "PENDING_DELETE")

    @Query("UPDATE customers SET sync_status = :syncStatus, drive_id = :driveId WHERE id = :id")
    suspend fun updateCustomerSyncAndDriveId(id: Long, syncStatus: String, driveId: String)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun hardDeleteCustomerById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    // --- RENTAL TRANSACTIONS ---
    @Query("SELECT * FROM rental_transactions WHERE is_deleted = 0 ORDER BY id DESC")
    fun getAllTransactions(): Flow<List<RentalTransactionEntity>>

    @Query("SELECT * FROM rental_transactions WHERE is_deleted = 0 ORDER BY id DESC")
    suspend fun getAllTransactionsOnce(): List<RentalTransactionEntity>

    @Query("SELECT * FROM rental_transactions WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getTransactionById(id: Long): RentalTransactionEntity?

    @Query("SELECT * FROM rental_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionByIdIncludingDeleted(id: Long): RentalTransactionEntity?

    @Query("SELECT * FROM rental_transactions WHERE nomorInvoice = :invoice AND is_deleted = 0 LIMIT 1")
    suspend fun getTransactionByInvoice(invoice: String): RentalTransactionEntity?

    @Query("SELECT * FROM rental_transactions WHERE nomorInvoice = :invoice LIMIT 1")
    suspend fun getTransactionByInvoiceIncludingDeleted(invoice: String): RentalTransactionEntity?

    @Query("SELECT * FROM rental_transactions WHERE uuid = :uuid AND is_deleted = 0 LIMIT 1")
    suspend fun getTransactionByUuid(uuid: String): RentalTransactionEntity?

    @Query("SELECT * FROM rental_transactions WHERE uuid = :uuid LIMIT 1")
    suspend fun getTransactionByUuidIncludingDeleted(uuid: String): RentalTransactionEntity?

    @Query("SELECT nomorInvoice FROM rental_transactions WHERE nomorInvoice LIKE :prefix")
    suspend fun getAllInvoiceNumbersMatchingPrefixIncludingDeleted(prefix: String): List<String>

    @Query("SELECT * FROM rental_transactions WHERE unitId = :unitId AND is_deleted = 0 ORDER BY tanggalMulaiSewa DESC")
    fun getTransactionsForUnit(unitId: Long): Flow<List<RentalTransactionEntity>>

    @Query("SELECT * FROM rental_transactions WHERE customerId = :customerId AND is_deleted = 0 ORDER BY tanggalMulaiSewa DESC")
    fun getTransactionsForCustomer(customerId: Long): Flow<List<RentalTransactionEntity>>

    @Query("SELECT COUNT(*) FROM rental_transactions WHERE is_deleted = 0")
    suspend fun getTransactionCount(): Int

    @Query("SELECT * FROM rental_transactions WHERE sync_status IN (:statuses) ORDER BY updated_at ASC, id ASC")
    suspend fun getTransactionsBySyncStatuses(statuses: List<String>): List<RentalTransactionEntity>

    @Query(
        """
        SELECT * FROM rental_transactions
        WHERE is_deleted = 0
          AND id != :excludeTxId
          AND unitId = :unitId
          AND LOWER(TRIM(statusPenyewaan)) NOT IN ('selesai', 'dibatalkan')
        ORDER BY id DESC
        """
    )
    suspend fun getActiveTransactionsForUnitOnce(unitId: Long, excludeTxId: Long = -1L): List<RentalTransactionEntity>

    @Query(
        """
        SELECT tui.* FROM transaction_unit_items tui
        INNER JOIN rental_transactions rt ON rt.id = tui.transaction_id
        WHERE rt.is_deleted = 0
          AND rt.id != :excludeTxId
          AND tui.unit_id = :unitId
          AND LOWER(TRIM(rt.statusPenyewaan)) NOT IN ('selesai', 'dibatalkan')
          AND LOWER(TRIM(tui.status_penyewaan)) NOT IN ('selesai', 'dibatalkan')
        ORDER BY tui.id DESC
        """
    )
    suspend fun getActiveUnitItemsForUnitOnce(unitId: Long, excludeTxId: Long = -1L): List<TransactionUnitItemEntity>

    @Query(
        """
        UPDATE rental_transactions
        SET is_deleted = 1,
            sync_status = :syncStatus,
            updated_at = :updatedAt,
            deleted_at = :deletedAt,
            deleted_by = :deletedBy
        WHERE id = :id
        """
    )
    suspend fun softDeleteTransaction(
        id: Long,
        updatedAt: Long = System.currentTimeMillis(),
        syncStatus: String = "PENDING_DELETE",
        deletedAt: Long = updatedAt,
        deletedBy: String = ""
    )

    @Query("UPDATE rental_transactions SET sync_status = :syncStatus, drive_id = :driveId WHERE id = :id")
    suspend fun updateTransactionSyncAndDriveId(id: Long, syncStatus: String, driveId: String)

    @Query("UPDATE rental_transactions SET pdfInvoicePath = :pdfPath, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTransactionPdfPath(id: Long, pdfPath: String, updatedAt: Long)

    @Query("DELETE FROM rental_transactions WHERE id = :id")
    suspend fun hardDeleteTransactionById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: RentalTransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: RentalTransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: RentalTransactionEntity)

    @Query("DELETE FROM rental_transactions")
    suspend fun deleteAllTransactions()

    // --- EXTENSION HISTORY ---
    @Query("SELECT * FROM extension_history ORDER BY id DESC")
    fun getAllExtensions(): Flow<List<RentalExtensionHistoryEntity>>

    @Query("SELECT * FROM extension_history ORDER BY id DESC")
    suspend fun getAllExtensionsOnce(): List<RentalExtensionHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(extension: RentalExtensionHistoryEntity): Long

    @Query("DELETE FROM extension_history")
    suspend fun deleteAllExtensions()

    // --- FREEZER RETURNS (SERAH TERIMA PENGEMBALIAN) ---
    @Query("SELECT * FROM freezer_returns ORDER BY id DESC")
    fun getAllReturns(): Flow<List<FreezerReturnEntity>>

    @Query("SELECT * FROM freezer_returns ORDER BY id DESC")
    suspend fun getAllReturnsOnce(): List<FreezerReturnEntity>

    @Query("SELECT * FROM freezer_returns WHERE id = :id LIMIT 1")
    suspend fun getReturnById(id: Long): FreezerReturnEntity?

    @Query("SELECT * FROM freezer_returns WHERE nomorDokumenPengembalian = :docNo LIMIT 1")
    suspend fun getReturnByDocNumber(docNo: String): FreezerReturnEntity?

    @Query("SELECT * FROM freezer_returns WHERE transactionId = :txId ORDER BY id DESC LIMIT 1")
    suspend fun getReturnForTransaction(txId: Long): FreezerReturnEntity?

    @Query("SELECT COUNT(*) FROM freezer_returns")
    suspend fun getReturnCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturn(freezerReturn: FreezerReturnEntity): Long

    @Update
    suspend fun updateReturn(freezerReturn: FreezerReturnEntity)

    @Delete
    suspend fun deleteReturn(freezerReturn: FreezerReturnEntity)

    @Query("DELETE FROM freezer_returns")
    suspend fun deleteAllReturns()

    // --- PAYMENT PROOFS (1. FITUR BUKTI PEMBAYARAN) ---
    @Query("SELECT * FROM payment_proofs WHERE is_deleted = 0 ORDER BY id DESC")
    fun getAllPaymentProofs(): Flow<List<PaymentProofEntity>>

    @Query("SELECT * FROM payment_proofs WHERE transactionId = :transactionId AND is_deleted = 0 ORDER BY id DESC")
    fun getPaymentProofsForTransaction(transactionId: Long): Flow<List<PaymentProofEntity>>

    @Query("SELECT * FROM payment_proofs WHERE transactionId = :transactionId AND is_deleted = 0 ORDER BY id DESC")
    suspend fun getPaymentProofsForTransactionOnce(transactionId: Long): List<PaymentProofEntity>

    @Query("SELECT * FROM payment_proofs WHERE sync_status IN (:statuses) ORDER BY updated_at ASC, id ASC")
    suspend fun getPaymentProofsBySyncStatuses(statuses: List<String>): List<PaymentProofEntity>

    @Query("UPDATE payment_proofs SET is_deleted = 1, sync_status = :syncStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDeletePaymentProof(id: Long, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "PENDING_DELETE")

    @Query("UPDATE payment_proofs SET sync_status = :syncStatus, drive_id = :driveId WHERE id = :id")
    suspend fun updatePaymentProofSyncAndDriveId(id: Long, syncStatus: String, driveId: String)

    @Query("DELETE FROM payment_proofs WHERE id = :id")
    suspend fun hardDeletePaymentProofById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentProof(proof: PaymentProofEntity): Long

    @Update
    suspend fun updatePaymentProof(proof: PaymentProofEntity)

    @Delete
    suspend fun deletePaymentProof(proof: PaymentProofEntity)

    @Query("DELETE FROM payment_proofs")
    suspend fun deleteAllPaymentProofs()

    // --- PAYMENT METHODS (2. SETTING -> METODE PEMBAYARAN) ---
    @Query("SELECT * FROM payment_methods ORDER BY id ASC")
    fun getAllPaymentMethods(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods ORDER BY id ASC")
    suspend fun getAllPaymentMethodsOnce(): List<PaymentMethodEntity>

    @Query("SELECT * FROM payment_methods WHERE isAktif = 1 ORDER BY id ASC")
    fun getActivePaymentMethods(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun getPaymentMethodCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethod(method: PaymentMethodEntity): Long

    @Update
    suspend fun updatePaymentMethod(method: PaymentMethodEntity)

    @Delete
    suspend fun deletePaymentMethod(method: PaymentMethodEntity)

    @Query("DELETE FROM payment_methods")
    suspend fun deleteAllPaymentMethods()

    // --- SYNC LOGS ---
    @Query("SELECT * FROM sync_logs ORDER BY id DESC LIMIT 50")
    fun getSyncLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLogEntity): Long

    @Query("DELETE FROM sync_logs")
    suspend fun deleteAllSyncLogs()

    // --- CUSTOMER PHOTOS (OFFLINE FOLDER PER PELANGGAN) ---
    @Query("SELECT * FROM customer_photos ORDER BY id DESC")
    fun getAllCustomerPhotos(): Flow<List<CustomerPhotoEntity>>

    @Query("SELECT * FROM customer_photos ORDER BY id DESC")
    suspend fun getAllCustomerPhotosOnce(): List<CustomerPhotoEntity>

    @Query("SELECT * FROM customer_photos WHERE customerId = :customerId ORDER BY id DESC")
    fun getCustomerPhotos(customerId: Long): Flow<List<CustomerPhotoEntity>>

    @Query("SELECT * FROM customer_photos WHERE customerId = :customerId ORDER BY id DESC")
    suspend fun getCustomerPhotosOnce(customerId: Long): List<CustomerPhotoEntity>

    @Query("SELECT * FROM customer_photos WHERE customerId = :customerId AND kategori = :kategori ORDER BY id DESC")
    fun getCustomerPhotosByCategory(customerId: Long, kategori: String): Flow<List<CustomerPhotoEntity>>

    @Query("SELECT COUNT(*) FROM customer_photos WHERE customerId = :customerId AND kategori = :kategori")
    suspend fun countCustomerPhotosInCategory(customerId: Long, kategori: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerPhoto(photo: CustomerPhotoEntity): Long

    @Delete
    suspend fun deleteCustomerPhoto(photo: CustomerPhotoEntity)

    @Query("DELETE FROM customer_photos WHERE customerId = :customerId")
    suspend fun deletePhotosByCustomer(customerId: Long)

    // --- CUSTOMER DOCUMENTS (v11: customer_documents) ---
    @Query("SELECT * FROM customer_documents ORDER BY id DESC")
    fun getAllCustomerDocuments(): Flow<List<CustomerDocumentEntity>>

    @Query("SELECT * FROM customer_documents ORDER BY id DESC")
    suspend fun getAllCustomerDocumentsOnce(): List<CustomerDocumentEntity>

    @Query("SELECT * FROM customer_documents WHERE customer_id = :customerId ORDER BY id DESC")
    fun getCustomerDocumentsByCustomer(customerId: Long): Flow<List<CustomerDocumentEntity>>

    @Query("SELECT * FROM customer_documents WHERE customer_id = :customerId ORDER BY id DESC")
    suspend fun getCustomerDocumentsByCustomerOnce(customerId: Long): List<CustomerDocumentEntity>

    @Query("SELECT * FROM customer_documents WHERE customer_id = :customerId AND document_type = :docType ORDER BY id DESC LIMIT 1")
    suspend fun getLatestCustomerDocumentByType(customerId: Long, docType: String): CustomerDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerDocument(doc: CustomerDocumentEntity): Long

    @Update
    suspend fun updateCustomerDocument(doc: CustomerDocumentEntity)

    @Delete
    suspend fun deleteCustomerDocument(doc: CustomerDocumentEntity)

    @Query("DELETE FROM customer_documents WHERE id = :id")
    suspend fun deleteCustomerDocumentById(id: Long)

    // --- TRANSACTION DOCUMENTS (v11: transaction_documents) ---
    @Query("SELECT * FROM transaction_documents ORDER BY id DESC")
    fun getAllTransactionDocuments(): Flow<List<TransactionDocumentEntity>>

    @Query("SELECT * FROM transaction_documents ORDER BY id DESC")
    suspend fun getAllTransactionDocumentsOnce(): List<TransactionDocumentEntity>

    @Query("SELECT * FROM transaction_documents WHERE transaction_id = :transactionId ORDER BY id DESC")
    fun getTransactionDocumentsByTx(transactionId: Long): Flow<List<TransactionDocumentEntity>>

    @Query("SELECT * FROM transaction_documents WHERE transaction_id = :transactionId ORDER BY id DESC")
    suspend fun getTransactionDocumentsByTxOnce(transactionId: Long): List<TransactionDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionDocument(doc: TransactionDocumentEntity): Long

    @Update
    suspend fun updateTransactionDocument(doc: TransactionDocumentEntity)

    @Delete
    suspend fun deleteTransactionDocument(doc: TransactionDocumentEntity)

    @Query("DELETE FROM transaction_documents WHERE id = :id")
    suspend fun deleteTransactionDocumentById(id: Long)

    // --- CUSTOMER LOCATION (v11: customer_location) ---
    @Query("SELECT * FROM customer_location ORDER BY id DESC")
    fun getAllCustomerLocations(): Flow<List<CustomerLocationEntity>>

    @Query("SELECT * FROM customer_location ORDER BY id DESC")
    suspend fun getAllCustomerLocationsOnce(): List<CustomerLocationEntity>

    @Query("SELECT * FROM customer_location WHERE customer_id = :customerId ORDER BY id DESC")
    fun getCustomerLocationsByCustomer(customerId: Long): Flow<List<CustomerLocationEntity>>

    @Query("SELECT * FROM customer_location WHERE customer_id = :customerId ORDER BY id DESC LIMIT 1")
    suspend fun getLatestCustomerLocation(customerId: Long): CustomerLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerLocation(loc: CustomerLocationEntity): Long

    @Update
    suspend fun updateCustomerLocation(loc: CustomerLocationEntity)

    @Delete
    suspend fun deleteCustomerLocation(loc: CustomerLocationEntity)

    // --- LOCAL BACKUP (v11: local_backup) ---
    @Query("SELECT * FROM local_backup ORDER BY id DESC")
    fun getAllLocalBackups(): Flow<List<LocalBackupEntity>>

    @Query("SELECT * FROM local_backup ORDER BY id DESC")
    suspend fun getAllLocalBackupsOnce(): List<LocalBackupEntity>

    @Query("SELECT * FROM local_backup WHERE backup_id = :backupId LIMIT 1")
    suspend fun getLocalBackupByBackupId(backupId: String): LocalBackupEntity?

    @Query("SELECT * FROM local_backup WHERE drive_file_id = :driveFileId LIMIT 1")
    suspend fun getLocalBackupByDriveFileId(driveFileId: String): LocalBackupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalBackup(backup: LocalBackupEntity): Long

    @Update
    suspend fun updateLocalBackup(backup: LocalBackupEntity)

    @Delete
    suspend fun deleteLocalBackup(backup: LocalBackupEntity)

    @Query("DELETE FROM local_backup WHERE id = :id")
    suspend fun deleteLocalBackupById(id: Long)

    // --- APP PERMISSION (v11: app_permission) ---
    @Query("SELECT * FROM app_permission ORDER BY id ASC")
    fun getAllAppPermissions(): Flow<List<AppPermissionEntity>>

    @Query("SELECT * FROM app_permission ORDER BY id ASC")
    suspend fun getAllAppPermissionsOnce(): List<AppPermissionEntity>

    @Query("SELECT * FROM app_permission WHERE permission_name = :permissionName LIMIT 1")
    suspend fun getAppPermissionByName(permissionName: String): AppPermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppPermission(permission: AppPermissionEntity): Long

    @Update
    suspend fun updateAppPermission(permission: AppPermissionEntity)

    // --- MULTI-UNIT TRANSACTION ITEMS (v12: transaction_unit_items) ---
    @Query("SELECT * FROM transaction_unit_items ORDER BY transaction_id DESC, unit_index_number ASC, id ASC")
    fun getAllTransactionUnitItems(): Flow<List<TransactionUnitItemEntity>>

    @Query("SELECT * FROM transaction_unit_items ORDER BY transaction_id DESC, unit_index_number ASC, id ASC")
    suspend fun getAllTransactionUnitItemsOnce(): List<TransactionUnitItemEntity>

    @Query("SELECT * FROM transaction_unit_items WHERE transaction_id = :transactionId ORDER BY unit_index_number ASC, id ASC")
    fun getTransactionUnitItemsByTx(transactionId: Long): Flow<List<TransactionUnitItemEntity>>

    @Query("SELECT * FROM transaction_unit_items WHERE transaction_id = :transactionId ORDER BY unit_index_number ASC, id ASC")
    suspend fun getTransactionUnitItemsByTxOnce(transactionId: Long): List<TransactionUnitItemEntity>

    @Query("SELECT * FROM transaction_unit_items WHERE customer_id = :customerId ORDER BY transaction_id DESC, unit_index_number ASC")
    fun getTransactionUnitItemsByCustomer(customerId: Long): Flow<List<TransactionUnitItemEntity>>

    @Query("SELECT * FROM transaction_unit_items WHERE customer_id = :customerId ORDER BY transaction_id DESC, unit_index_number ASC")
    suspend fun getTransactionUnitItemsByCustomerOnce(customerId: Long): List<TransactionUnitItemEntity>

    @Query("SELECT * FROM transaction_unit_items WHERE id = :id LIMIT 1")
    suspend fun getTransactionUnitItemById(id: Long): TransactionUnitItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionUnitItem(item: TransactionUnitItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionUnitItems(items: List<TransactionUnitItemEntity>): List<Long>

    @Update
    suspend fun updateTransactionUnitItem(item: TransactionUnitItemEntity)

    @Delete
    suspend fun deleteTransactionUnitItem(item: TransactionUnitItemEntity)

    @Query("DELETE FROM transaction_unit_items WHERE id = :id")
    suspend fun deleteTransactionUnitItemById(id: Long)

    @Query("DELETE FROM transaction_unit_items WHERE transaction_id = :transactionId")
    suspend fun deleteTransactionUnitItemsByTx(transactionId: Long)

    // --- TRANSACTION SIGNATURES (v12: transaction_signatures) ---
    @Query("SELECT * FROM transaction_signatures ORDER BY id DESC")
    fun getAllTransactionSignatures(): Flow<List<TransactionSignatureEntity>>

    @Query("SELECT * FROM transaction_signatures ORDER BY id DESC")
    suspend fun getAllTransactionSignaturesOnce(): List<TransactionSignatureEntity>

    @Query("SELECT * FROM transaction_signatures WHERE transaction_id = :transactionId ORDER BY id DESC")
    fun getTransactionSignaturesByTx(transactionId: Long): Flow<List<TransactionSignatureEntity>>

    @Query("SELECT * FROM transaction_signatures WHERE transaction_id = :transactionId ORDER BY id DESC")
    suspend fun getTransactionSignaturesByTxOnce(transactionId: Long): List<TransactionSignatureEntity>

    @Query("SELECT * FROM transaction_signatures WHERE transaction_id = :transactionId AND signer_role = :signerRole ORDER BY id DESC LIMIT 1")
    suspend fun getLatestSignatureForTxAndRole(transactionId: Long, signerRole: String): TransactionSignatureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionSignature(sig: TransactionSignatureEntity): Long

    @Update
    suspend fun updateTransactionSignature(sig: TransactionSignatureEntity)

    @Delete
    suspend fun deleteTransactionSignature(sig: TransactionSignatureEntity)

    @Query("DELETE FROM transaction_signatures WHERE transaction_id = :transactionId AND signer_role = :signerRole")
    suspend fun deleteTransactionSignatureByRole(transactionId: Long, signerRole: String)

    // --- UNIT PAYMENT ALLOCATIONS (v13: unit_payment_allocations) ---
    @Query("SELECT * FROM unit_payment_allocations ORDER BY id DESC")
    fun getAllUnitPaymentAllocations(): Flow<List<UnitPaymentAllocationEntity>>

    @Query("SELECT * FROM unit_payment_allocations ORDER BY id DESC")
    suspend fun getAllUnitPaymentAllocationsOnce(): List<UnitPaymentAllocationEntity>

    @Query("SELECT * FROM unit_payment_allocations WHERE transaction_id = :transactionId ORDER BY id DESC")
    suspend fun getUnitPaymentAllocationsByTxOnce(transactionId: Long): List<UnitPaymentAllocationEntity>

    @Query("SELECT * FROM unit_payment_allocations WHERE customer_id = :customerId ORDER BY id DESC")
    suspend fun getUnitPaymentAllocationsByCustomerOnce(customerId: Long): List<UnitPaymentAllocationEntity>

    @Query("SELECT * FROM unit_payment_allocations WHERE unit_item_id = :unitItemId ORDER BY id DESC")
    suspend fun getUnitPaymentAllocationsByUnitItemOnce(unitItemId: Long): List<UnitPaymentAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnitPaymentAllocation(allocation: UnitPaymentAllocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnitPaymentAllocations(allocations: List<UnitPaymentAllocationEntity>): List<Long>

    @Query("DELETE FROM unit_payment_allocations WHERE payment_proof_id = :paymentProofId")
    suspend fun deleteUnitPaymentAllocationsByProof(paymentProofId: Long)

    // --- CUSTOMER TIMELINE EVENTS (v13: customer_timeline_events) ---
    @Query("SELECT * FROM customer_timeline_events ORDER BY id DESC")
    fun getAllCustomerTimelineEvents(): Flow<List<CustomerTimelineEventEntity>>

    @Query("SELECT * FROM customer_timeline_events ORDER BY id DESC")
    suspend fun getAllCustomerTimelineEventsOnce(): List<CustomerTimelineEventEntity>

    @Query("SELECT * FROM customer_timeline_events WHERE customer_id = :customerId ORDER BY id DESC")
    suspend fun getCustomerTimelineEventsOnce(customerId: Long): List<CustomerTimelineEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerTimelineEvent(event: CustomerTimelineEventEntity): Long

    @Query("DELETE FROM customer_timeline_events WHERE customer_id = :customerId")
    suspend fun deleteCustomerTimelineEvents(customerId: Long)

    // --- CLOUD SYNC QUEUE (v14: cloud_sync_queue) ---
    @Query("SELECT * FROM cloud_sync_queue WHERE uid = :uid ORDER BY id ASC")
    fun observeCloudSyncQueue(uid: String): Flow<List<CloudSyncQueueEntity>>

    @Query("SELECT * FROM cloud_sync_queue WHERE uid = :uid AND status IN ('PENDING', 'FAILED', 'SYNCING') ORDER BY id ASC")
    suspend fun getPendingSyncQueueOnce(uid: String): List<CloudSyncQueueEntity>

    @Query("SELECT COUNT(*) FROM cloud_sync_queue WHERE uid = :uid AND status IN ('PENDING', 'FAILED', 'SYNCING')")
    fun observePendingSyncCount(uid: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCloudSyncQueueItem(item: CloudSyncQueueEntity): Long

    @Query("UPDATE cloud_sync_queue SET status = :status, lastError = :lastError, retryCount = :retryCount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCloudSyncQueueStatus(id: Long, status: String, lastError: String, retryCount: Int, updatedAt: String)

    @Query("SELECT * FROM cloud_sync_queue WHERE uid = :uid AND entityType = :entityType AND cloudDocId = :cloudDocId LIMIT 1")
    suspend fun getCloudSyncQueueByDocId(uid: String, entityType: String, cloudDocId: String): CloudSyncQueueEntity?

    @Query("UPDATE cloud_sync_queue SET status = 'PENDING', updatedAt = :updatedAt WHERE uid = :uid AND status = 'SYNCING'")
    suspend fun resetStuckSyncingQueueItems(uid: String, updatedAt: String): Int

    @Query("DELETE FROM cloud_sync_queue WHERE id = :id")
    suspend fun deleteCloudSyncQueueItemById(id: Long)

    @Query("DELETE FROM cloud_sync_queue WHERE uid = :uid AND status = 'SYNCED'")
    suspend fun purgeSyncedQueueItems(uid: String)

    // --- CLOUD PHOTO UPLOAD JOBS (v14: cloud_photo_jobs) ---
    @Query("SELECT * FROM cloud_photo_jobs WHERE uid = :uid ORDER BY id DESC")
    fun observeCloudPhotoJobs(uid: String): Flow<List<CloudPhotoJobEntity>>

    @Query("SELECT * FROM cloud_photo_jobs WHERE uid = :uid ORDER BY id ASC")
    suspend fun getAllCloudPhotoJobsOnce(uid: String): List<CloudPhotoJobEntity>

    @Query("SELECT * FROM cloud_photo_jobs WHERE uid = :uid AND status IN ('QUEUED', 'FAILED', 'UPLOADING') ORDER BY id ASC")
    suspend fun getPendingPhotoJobsOnce(uid: String): List<CloudPhotoJobEntity>

    @Query("SELECT * FROM cloud_photo_jobs WHERE uid = :uid AND ownerType = :ownerType AND ownerLocalId = :ownerLocalId ORDER BY id ASC")
    suspend fun getCloudPhotoJobsForOwnerOnce(uid: String, ownerType: String, ownerLocalId: Long): List<CloudPhotoJobEntity>

    @Query("SELECT * FROM cloud_photo_jobs WHERE uid = :uid AND ownerType = :ownerType AND ownerLocalId = :ownerLocalId AND status IN ('QUEUED', 'FAILED', 'UPLOADING') ORDER BY id ASC")
    suspend fun getPendingPhotoJobsForOwnerOnce(uid: String, ownerType: String, ownerLocalId: Long): List<CloudPhotoJobEntity>

    @Query("UPDATE cloud_photo_jobs SET status = 'QUEUED', updatedAt = :updatedAt WHERE uid = :uid AND status = 'UPLOADING'")
    suspend fun resetStuckUploadingPhotoJobs(uid: String, updatedAt: String): Int

    @Query("DELETE FROM cloud_photo_jobs WHERE uid = :uid AND ownerType = :ownerType AND ownerLocalId = :ownerLocalId")
    suspend fun deleteCloudPhotoJobsForOwner(uid: String, ownerType: String, ownerLocalId: Long)

    @Query("SELECT * FROM cloud_photo_jobs WHERE uid = :uid AND status = 'FAILED' ORDER BY id ASC")
    suspend fun getFailedPhotoJobsOnce(uid: String): List<CloudPhotoJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCloudPhotoJob(job: CloudPhotoJobEntity): Long

    @Query("UPDATE cloud_photo_jobs SET status = :status, progressPercent = :progressPercent, bytesUploaded = :bytesUploaded, errorMessage = :errorMessage, retryCount = :retryCount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCloudPhotoJobProgress(
        id: Long,
        status: String,
        progressPercent: Int,
        bytesUploaded: Long,
        errorMessage: String,
        retryCount: Int,
        updatedAt: String
    )

    @Query("UPDATE cloud_photo_jobs SET status = 'UPLOADED', progressPercent = 100, bytesUploaded = bytesTotal, cloudDownloadUrl = :downloadUrl, thumbnailDownloadUrl = :thumbUrl, errorMessage = '', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markCloudPhotoJobUploaded(id: Long, downloadUrl: String, thumbUrl: String, updatedAt: String)

    @Query("UPDATE freezer_units SET sync_status = :syncStatus WHERE id = :id")
    suspend fun updateUnitSyncStatusOnly(id: Long, syncStatus: String)

    @Query("UPDATE customers SET sync_status = :syncStatus WHERE id = :id")
    suspend fun updateCustomerSyncStatusOnly(id: Long, syncStatus: String)

    @Query("UPDATE rental_transactions SET sync_status = :syncStatus WHERE id = :id")
    suspend fun updateTransactionSyncStatusOnly(id: Long, syncStatus: String)

    @Query("UPDATE payment_proofs SET sync_status = :syncStatus WHERE id = :id")
    suspend fun updatePaymentProofSyncStatusOnly(id: Long, syncStatus: String)

    // --- CLOUD MIGRATION STATE (v14: cloud_migration_state) ---
    @Query("SELECT * FROM cloud_migration_state WHERE uid = :uid LIMIT 1")
    fun observeCloudMigrationState(uid: String): Flow<CloudMigrationStateEntity?>

    @Query("SELECT * FROM cloud_migration_state WHERE uid = :uid LIMIT 1")
    suspend fun getCloudMigrationStateOnce(uid: String): CloudMigrationStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCloudMigrationState(state: CloudMigrationStateEntity)

    // --- ADDITIONAL BULK QUERIES FOR CLOUD SYNC & MIGRATION ---
    @Query("SELECT * FROM payment_proofs WHERE is_deleted = 0 ORDER BY id ASC")
    suspend fun getAllPaymentProofsOnce(): List<PaymentProofEntity>

    @Query("SELECT * FROM payment_proofs WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getPaymentProofById(id: Long): PaymentProofEntity?

    @Query("SELECT * FROM payment_proofs WHERE id = :id LIMIT 1")
    suspend fun getPaymentProofByIdIncludingDeleted(id: Long): PaymentProofEntity?

    @Query("SELECT * FROM payment_proofs WHERE uuid = :uuid AND is_deleted = 0 LIMIT 1")
    suspend fun getPaymentProofByUuid(uuid: String): PaymentProofEntity?

    @Query("SELECT * FROM payment_proofs WHERE uuid = :uuid LIMIT 1")
    suspend fun getPaymentProofByUuidIncludingDeleted(uuid: String): PaymentProofEntity?

    @Query("SELECT COUNT(*) FROM freezer_units WHERE is_deleted = 0")
    suspend fun getUnitCount(): Int

    @Query("SELECT * FROM rental_transactions WHERE is_deleted = 0 ORDER BY id DESC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsPagedOnce(limit: Int, offset: Int): List<RentalTransactionEntity>

    @Query("SELECT * FROM customers WHERE is_deleted = 0 ORDER BY namaLengkap ASC LIMIT :limit OFFSET :offset")
    suspend fun getCustomersPagedOnce(limit: Int, offset: Int): List<CustomerEntity>

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 ORDER BY kodeUnit ASC LIMIT :limit OFFSET :offset")
    suspend fun getUnitsPagedOnce(limit: Int, offset: Int): List<FreezerUnitEntity>

    // --- BACKUP AUDIT LOGS (v15) ---
    @Query("SELECT * FROM backup_audit_logs ORDER BY timestamp_millis DESC, id DESC LIMIT 200")
    fun getAllBackupAuditLogs(): Flow<List<BackupAuditLogEntity>>

    @Query("SELECT * FROM backup_audit_logs ORDER BY timestamp_millis DESC, id DESC")
    suspend fun getAllBackupAuditLogsOnce(): List<BackupAuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupAuditLog(log: BackupAuditLogEntity): Long

    // --- ATOMIC BULK RESTORE HELPERS (v15) ---
    @Query("DELETE FROM customer_documents")
    suspend fun deleteAllCustomerDocuments()

    @Query("DELETE FROM transaction_documents")
    suspend fun deleteAllTransactionDocuments()

    @Query("DELETE FROM customer_location")
    suspend fun deleteAllCustomerLocations()

    @Query("DELETE FROM customer_photos")
    suspend fun deleteAllCustomerPhotos()

    @Query("DELETE FROM transaction_signatures")
    suspend fun deleteAllTransactionSignatures()

    @Query("DELETE FROM unit_payment_allocations")
    suspend fun deleteAllUnitPaymentAllocations()

    @Query("DELETE FROM transaction_unit_items")
    suspend fun deleteAllTransactionUnitItems()

    @Query("DELETE FROM customer_timeline_events")
    suspend fun deleteAllCustomerTimelineEvents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomersBulk(list: List<CustomerEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnitsBulk(list: List<FreezerUnitEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionsBulk(list: List<RentalTransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentProofsBulk(list: List<PaymentProofEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethodsBulk(list: List<PaymentMethodEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturnsBulk(list: List<FreezerReturnEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtensionsBulk(list: List<RentalExtensionHistoryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerDocumentsBulk(list: List<CustomerDocumentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionDocumentsBulk(list: List<TransactionDocumentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerLocationsBulk(list: List<CustomerLocationEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerPhotosBulk(list: List<CustomerPhotoEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionSignaturesBulk(list: List<TransactionSignatureEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerTimelineEventsBulk(list: List<CustomerTimelineEventEntity>): List<Long>

    @Transaction
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
        deleteAllUnitPaymentAllocations()
        deleteAllTransactionSignatures()
        deleteAllTransactionUnitItems()
        deleteAllReturns()
        deleteAllPaymentProofs()
        deleteAllExtensions()
        deleteAllTransactionDocuments()
        deleteAllCustomerTimelineEvents()
        deleteAllCustomerPhotos()
        deleteAllCustomerLocations()
        deleteAllCustomerDocuments()
        deleteAllTransactions()
        deleteAllUnits()
        deleteAllCustomers()
        deleteAllPaymentMethods()

        if (customers.isNotEmpty()) insertCustomersBulk(customers)
        if (units.isNotEmpty()) insertUnitsBulk(units)
        if (paymentMethods.isNotEmpty()) insertPaymentMethodsBulk(paymentMethods)
        if (transactions.isNotEmpty()) insertTransactionsBulk(transactions)
        if (unitItems.isNotEmpty()) insertTransactionUnitItems(unitItems)
        if (paymentProofs.isNotEmpty()) insertPaymentProofsBulk(paymentProofs)
        if (allocations.isNotEmpty()) insertUnitPaymentAllocations(allocations)
        if (signatures.isNotEmpty()) insertTransactionSignaturesBulk(signatures)
        if (returns.isNotEmpty()) insertReturnsBulk(returns)
        if (extensions.isNotEmpty()) insertExtensionsBulk(extensions)
        if (customerDocuments.isNotEmpty()) insertCustomerDocumentsBulk(customerDocuments)
        if (transactionDocuments.isNotEmpty()) insertTransactionDocumentsBulk(transactionDocuments)
        if (customerLocations.isNotEmpty()) insertCustomerLocationsBulk(customerLocations)
        if (customerPhotos.isNotEmpty()) insertCustomerPhotosBulk(customerPhotos)
        if (timelineEvents.isNotEmpty()) insertCustomerTimelineEventsBulk(timelineEvents)
    }

    // --- GOOGLE DRIVE DOCUMENTATION PHOTO METADATA (v16: drive_photo_metadata) ---
    @Query("SELECT * FROM drive_photo_metadata ORDER BY created_at_millis DESC, id DESC")
    fun observeAllDrivePhotoMetadata(): Flow<List<DrivePhotoMetadataEntity>>

    @Query("SELECT * FROM drive_photo_metadata ORDER BY created_at_millis DESC, id DESC")
    suspend fun getAllDrivePhotoMetadataOnce(): List<DrivePhotoMetadataEntity>

    @Query("SELECT * FROM drive_photo_metadata WHERE id = :id LIMIT 1")
    suspend fun getDrivePhotoMetadataById(id: Long): DrivePhotoMetadataEntity?

    @Query("SELECT * FROM drive_photo_metadata WHERE customer_id = :customerId ORDER BY created_at_millis DESC, id DESC")
    suspend fun getDrivePhotoMetadataByCustomerOnce(customerId: Long): List<DrivePhotoMetadataEntity>

    @Query("SELECT * FROM drive_photo_metadata WHERE unit_id = :unitId ORDER BY created_at_millis DESC, id DESC")
    suspend fun getDrivePhotoMetadataByUnitOnce(unitId: Long): List<DrivePhotoMetadataEntity>

    @Query("SELECT * FROM drive_photo_metadata WHERE transaction_id = :transactionId ORDER BY created_at_millis DESC, id DESC")
    suspend fun getDrivePhotoMetadataByTransactionOnce(transactionId: Long): List<DrivePhotoMetadataEntity>

    @Query("SELECT * FROM drive_photo_metadata WHERE source_checksum_sha256 = :checksum AND category_code = :categoryCode LIMIT 1")
    suspend fun findDrivePhotoByChecksumAndCategory(checksum: String, categoryCode: String): DrivePhotoMetadataEntity?

    @Query("SELECT * FROM drive_photo_metadata WHERE sync_status IN ('MENUNGGU UPLOAD', 'GAGAL', 'PERLU SINKRONISASI', 'SEDANG DIUPLOAD') ORDER BY created_at_millis ASC, id ASC")
    suspend fun getPendingOrFailedDrivePhotosOnce(): List<DrivePhotoMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrivePhotoMetadata(entity: DrivePhotoMetadataEntity): Long

    @Query("UPDATE drive_photo_metadata SET sync_status = :syncStatus, drive_file_id = :driveFileId, drive_folder_id = :driveFolderId, access_url_or_path = :accessUrlOrPath, temp_original_path = :tempOriginalPath, upload_date = :uploadDate, retry_count = :retryCount, last_error = :lastError WHERE id = :id")
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
    )

    @Delete
    suspend fun deleteDrivePhotoMetadata(entity: DrivePhotoMetadataEntity)

    @Query("DELETE FROM drive_photo_metadata WHERE id = :id")
    suspend fun deleteDrivePhotoMetadataById(id: Long)

    @Query("DELETE FROM drive_photo_metadata")
    suspend fun deleteAllDrivePhotoMetadata()

    // --- GOOGLE DRIVE CLOUD MIRROR FILE MAPPING (v17: drive_cloud_file_map) ---
    @Query("SELECT * FROM drive_cloud_file_map ORDER BY last_modified_millis DESC")
    fun observeAllDriveCloudFileMappings(): Flow<List<DriveCloudFileMapEntity>>

    @Query("SELECT * FROM drive_cloud_file_map ORDER BY last_modified_millis DESC")
    suspend fun getAllDriveCloudFileMappingsOnce(): List<DriveCloudFileMapEntity>

    @Query("SELECT * FROM drive_cloud_file_map WHERE local_record_key = :localRecordKey LIMIT 1")
    suspend fun getDriveCloudFileMapByKey(localRecordKey: String): DriveCloudFileMapEntity?

    @Query("SELECT * FROM drive_cloud_file_map WHERE record_uuid = :recordUuid AND record_uuid != '' LIMIT 1")
    suspend fun getDriveCloudFileMapByUuid(recordUuid: String): DriveCloudFileMapEntity?

    @Query("SELECT * FROM drive_cloud_file_map WHERE record_category = :recordCategory AND local_record_id = :localRecordId")
    suspend fun getDriveCloudFileMapsByCategoryAndId(recordCategory: String, localRecordId: Long): List<DriveCloudFileMapEntity>

    @Query("SELECT * FROM drive_cloud_file_map WHERE local_record_key LIKE :keyPrefix || '%'")
    suspend fun getDriveCloudFileMapsByPrefix(keyPrefix: String): List<DriveCloudFileMapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDriveCloudFileMap(entity: DriveCloudFileMapEntity)

    @Query("UPDATE drive_cloud_file_map SET sync_status = :syncStatus, last_modified_millis = :lastModifiedMillis, conflict_reason = :conflictReason WHERE local_record_key = :localRecordKey")
    suspend fun updateDriveCloudFileMapStatusAndReason(
        localRecordKey: String,
        syncStatus: String,
        lastModifiedMillis: Long,
        conflictReason: String = ""
    )

    @Query("UPDATE drive_cloud_file_map SET sync_status = :syncStatus, last_modified_millis = :lastModifiedMillis WHERE local_record_key = :localRecordKey")
    suspend fun updateDriveCloudFileMapStatus(localRecordKey: String, syncStatus: String, lastModifiedMillis: Long)

    @Query("DELETE FROM drive_cloud_file_map WHERE local_record_key = :localRecordKey")
    suspend fun deleteDriveCloudFileMapByKey(localRecordKey: String)

    @Query("DELETE FROM drive_cloud_file_map")
    suspend fun deleteAllDriveCloudFileMaps()

    // --- GOOGLE DRIVE LOCAL SYNC QUEUE (v17: drive_local_sync_queue) ---
    @Query("SELECT * FROM drive_local_sync_queue ORDER BY timestamp_millis ASC, id ASC")
    fun observeAllDriveLocalSyncQueue(): Flow<List<DriveLocalSyncQueueEntity>>

    @Query("SELECT * FROM drive_local_sync_queue ORDER BY timestamp_millis ASC, id ASC")
    suspend fun getAllDriveLocalSyncQueueOnce(): List<DriveLocalSyncQueueEntity>

    @Query("SELECT * FROM drive_local_sync_queue WHERE sync_status IN ('PENDING_SYNC', 'FAILED') ORDER BY timestamp_millis ASC, id ASC")
    suspend fun getRunnableDriveSyncQueueItemsOnce(): List<DriveLocalSyncQueueEntity>

    @Query("SELECT * FROM drive_local_sync_queue WHERE sync_status IN ('FAILED', 'FAILED_AUTH', 'UPLOADING') ORDER BY timestamp_millis ASC, id ASC")
    suspend fun getFailedAndAuthFailedDriveSyncQueueItemsOnce(): List<DriveLocalSyncQueueEntity>

    @Query("SELECT * FROM drive_local_sync_queue WHERE operation_id = :operationId LIMIT 1")
    suspend fun getDriveSyncQueueByOperationId(operationId: String): DriveLocalSyncQueueEntity?

    @Query("SELECT * FROM drive_local_sync_queue WHERE local_record_key = :localRecordKey ORDER BY version DESC, timestamp_millis DESC LIMIT 1")
    suspend fun getLatestDriveSyncQueueForKey(localRecordKey: String): DriveLocalSyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDriveLocalSyncQueueItem(entity: DriveLocalSyncQueueEntity): Long

    @Query("UPDATE drive_local_sync_queue SET sync_status = :syncStatus, retry_count = :retryCount, next_retry_at_millis = :nextRetryAtMillis, last_error = :lastError WHERE id = :id")
    suspend fun updateDriveSyncQueueStatus(
        id: Long,
        syncStatus: String,
        retryCount: Int,
        nextRetryAtMillis: Long,
        lastError: String
    )

    @Query("UPDATE drive_local_sync_queue SET sync_status = 'PENDING_SYNC', next_retry_at_millis = 0, last_error = '' WHERE sync_status IN ('FAILED', 'FAILED_AUTH', 'UPLOADING')")
    suspend fun resetFailedDriveSyncQueueToPending()

    @Query("UPDATE drive_local_sync_queue SET sync_status = 'PENDING_SYNC', next_retry_at_millis = 0, last_error = '' WHERE sync_status = 'FAILED_AUTH'")
    suspend fun resetFailedAuthDriveSyncQueueToPending()

    @Query("UPDATE drive_local_sync_queue SET sync_status = 'PENDING_SYNC', next_retry_at_millis = 0 WHERE sync_status = 'UPLOADING'")
    suspend fun resetStuckUploadingDriveSyncQueueToPending()

    @Query("DELETE FROM drive_local_sync_queue WHERE id = :id")
    suspend fun deleteDriveSyncQueueById(id: Long)

    @Query("DELETE FROM drive_local_sync_queue WHERE sync_status = 'SYNCED'")
    suspend fun deleteSyncedDriveSyncQueueItems()

    @Query("DELETE FROM drive_local_sync_queue")
    suspend fun deleteAllDriveLocalSyncQueue()

    // --- GOOGLE DRIVE DELETION TOMBSTONES (v17: drive_deletion_tombstones) ---
    @Query("SELECT * FROM drive_deletion_tombstones ORDER BY deleted_at_millis DESC")
    fun observeAllDriveDeletionTombstones(): Flow<List<DriveDeletionTombstoneEntity>>

    @Query("SELECT * FROM drive_deletion_tombstones ORDER BY deleted_at_millis DESC")
    suspend fun getAllDriveDeletionTombstonesOnce(): List<DriveDeletionTombstoneEntity>

    @Query("SELECT * FROM drive_deletion_tombstones WHERE local_record_key = :localRecordKey LIMIT 1")
    suspend fun getDriveDeletionTombstoneByKey(localRecordKey: String): DriveDeletionTombstoneEntity?

    @Query("SELECT * FROM drive_deletion_tombstones WHERE record_uuid = :recordUuid AND record_uuid != '' LIMIT 1")
    suspend fun getDriveDeletionTombstoneByUuid(recordUuid: String): DriveDeletionTombstoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDriveDeletionTombstone(entity: DriveDeletionTombstoneEntity)

    @Query("UPDATE freezer_units SET sync_status = :syncStatus WHERE id = :id")
    suspend fun updateUnitSyncStatus(id: Long, syncStatus: String)

    @Query("UPDATE customers SET sync_status = :syncStatus WHERE id = :id")
    suspend fun updateCustomerSyncStatus(id: Long, syncStatus: String)

    @Query("UPDATE rental_transactions SET sync_status = :syncStatus WHERE id = :id")
    suspend fun updateTransactionSyncStatus(id: Long, syncStatus: String)

    @Query("UPDATE payment_proofs SET sync_status = :syncStatus WHERE id = :id")
    suspend fun updatePaymentProofSyncStatus(id: Long, syncStatus: String)

    @Query("UPDATE drive_deletion_tombstones SET synced_to_cloud = :synced WHERE local_record_key = :localRecordKey")
    suspend fun markDriveDeletionTombstoneSynced(localRecordKey: String, synced: Boolean)

    @Query(
        """
        UPDATE drive_deletion_tombstones
        SET synced_to_cloud = :synced,
            uuid_verified_gone = :uuidVerifiedGone,
            manifest_updated_at_deletion = :manifestUpdatedAtDeletion
        WHERE local_record_key = :localRecordKey
        """
    )
    suspend fun markDriveDeletionTombstoneVerified(
        localRecordKey: String,
        synced: Boolean,
        uuidVerifiedGone: Boolean,
        manifestUpdatedAtDeletion: Boolean
    )

    @Query("DELETE FROM drive_deletion_tombstones WHERE local_record_key = :localRecordKey")
    suspend fun deleteDriveDeletionTombstoneByKey(localRecordKey: String)

    @Query("DELETE FROM drive_deletion_tombstones WHERE record_uuid = :recordUuid AND record_uuid != ''")
    suspend fun deleteDriveDeletionTombstoneByUuid(recordUuid: String)

    @Query("DELETE FROM drive_deletion_tombstones")
    suspend fun deleteAllDriveDeletionTombstones()

    @Query("DELETE FROM cloud_sync_queue")
    suspend fun deleteAllLegacyCloudSyncQueue()

    @Query("DELETE FROM cloud_photo_jobs")
    suspend fun deleteAllLegacyCloudPhotoJobs()

    @Query("DELETE FROM cloud_migration_state")
    suspend fun deleteAllLegacyCloudMigrationState()

    @Query("DELETE FROM local_backup")
    suspend fun deleteAllLocalBackups()

    // --- SYNC TECHNICAL LOGS (v18: sync_technical_logs) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncTechnicalLog(entity: SyncTechnicalLogEntity): Long

    @Query("SELECT * FROM sync_technical_logs ORDER BY operation_time_millis DESC, id DESC LIMIT 100")
    fun observeRecentSyncTechnicalLogs(): Flow<List<SyncTechnicalLogEntity>>

    @Query("SELECT * FROM sync_technical_logs ORDER BY operation_time_millis DESC, id DESC")
    suspend fun getAllSyncTechnicalLogsOnce(): List<SyncTechnicalLogEntity>

    @Query("DELETE FROM sync_technical_logs")
    suspend fun deleteAllSyncTechnicalLogs()

    // --- CLOUD QUARANTINE TABLE (v21: drive_cloud_quarantine) ---
    @Query("SELECT * FROM drive_cloud_quarantine ORDER BY quarantined_at_millis DESC, id DESC")
    fun observeAllDriveCloudQuarantine(): Flow<List<DriveCloudQuarantineEntity>>

    @Query("SELECT * FROM drive_cloud_quarantine ORDER BY quarantined_at_millis DESC, id DESC")
    suspend fun getAllDriveCloudQuarantineOnce(): List<DriveCloudQuarantineEntity>

    @Query("SELECT * FROM drive_cloud_quarantine WHERE status = 'QUARANTINED' ORDER BY quarantined_at_millis DESC, id DESC")
    suspend fun getActiveDriveCloudQuarantineOnce(): List<DriveCloudQuarantineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDriveCloudQuarantine(entity: DriveCloudQuarantineEntity): Long

    @Query("UPDATE drive_cloud_quarantine SET status = :status, resolved_at_millis = :resolvedAtMillis WHERE drive_file_id = :driveFileId")
    suspend fun updateDriveCloudQuarantineStatus(driveFileId: String, status: String, resolvedAtMillis: Long)

    @Query("DELETE FROM drive_cloud_quarantine WHERE drive_file_id = :driveFileId")
    suspend fun deleteDriveCloudQuarantineByFileId(driveFileId: String)

    @Query("DELETE FROM drive_cloud_quarantine")
    suspend fun deleteAllDriveCloudQuarantine()

    // --- TRANSACTIONAL OUTBOX ATOMIC OPERATIONS (v21/v22) ---

    @Transaction
    suspend fun saveUnitWithOutboxAtomic(
        unit: FreezerUnitEntity,
        isNew: Boolean,
        datasetId: String,
        datasetGenerationId: String,
        changedFieldsJson: String,
        serverTimestampMillis: Long = 0L
    ): Long {
        val existingRow = if (!isNew && unit.id > 0L) getUnitByIdIncludingDeleted(unit.id) else null
        val existingKey = if (unit.id > 0L) "UNIT:${unit.id}" else ""
        val existingMapPre = if (existingKey.isNotBlank()) getDriveCloudFileMapByKey(existingKey) else null
        val preservedDriveId = unit.driveId
            .ifBlank { existingRow?.driveId.orEmpty() }
            .ifBlank { existingMapPre?.driveFileId.orEmpty() }
        val stillUnsyncedCreate = !isNew &&
            preservedDriveId.isBlank() &&
            existingRow?.syncStatus == MirrorOutboxCodec.STATUS_PENDING_CREATE
        val nextStatus = if (isNew || stillUnsyncedCreate) {
            MirrorOutboxCodec.STATUS_PENDING_CREATE
        } else {
            MirrorOutboxCodec.STATUS_PENDING_UPDATE
        }
        val stamped = unit.copy(
            uuid = MirrorOutboxCodec.ensureValidUuidV4(unit.uuid),
            syncStatus = nextStatus,
            changedFieldsJson = changedFieldsJson,
            isDeleted = false,
            driveId = preservedDriveId
        )
        val savedId = if (stamped.id == 0L || isNew) {
            insertUnit(stamped)
        } else {
            updateUnit(stamped)
            stamped.id
        }
        val persisted = getUnitByIdIncludingDeleted(savedId) ?: stamped.copy(id = savedId)
        val key = "UNIT:$savedId"
        val existingMap = getDriveCloudFileMapByKey(key)
        val latestQueue = getLatestDriveSyncQueueForKey(key)
        val payload = MirrorOutboxCodec.serializeUnitJson(persisted, datasetId, datasetGenerationId, serverTimestampMillis)
        val plan = MirrorOutboxCodec.buildUpsertOutboxPlan(
            localRecordKey = key,
            recordCategory = MirrorOutboxCodec.CATEGORY_UNIT,
            recordId = savedId,
            recordUuid = persisted.uuid,
            entityVersion = persisted.version,
            updatedAtMillis = persisted.updatedAtMillis,
            payloadJson = payload,
            isNew = isNew,
            datasetGenerationId = datasetGenerationId,
            changedFieldsJson = changedFieldsJson,
            existingMap = existingMap,
            latestQueue = latestQueue,
            serverTimestampMillis = serverTimestampMillis,
            explicitDriveFileId = persisted.driveId
        )
        if (plan != null) {
            deleteDriveDeletionTombstoneByKey(plan.localRecordKey)
            if (plan.recordUuid.isNotBlank()) deleteDriveDeletionTombstoneByUuid(plan.recordUuid)
            upsertDriveCloudFileMap(plan.fileMap)
            upsertDriveLocalSyncQueueItem(plan.queueItem)
        }
        return savedId
    }

    @Transaction
    suspend fun deleteUnitWithOutboxAtomic(
        unit: FreezerUnitEntity,
        datasetGenerationId: String,
        serverTimestampMillis: Long = 0L
    ): List<String> {
        val freshUnit = getUnitByIdIncludingDeleted(unit.id) ?: unit
        val key = "UNIT:${freshUnit.id}"
        val existingMap = getDriveCloudFileMapByKey(key)
        val resolvedDriveId = freshUnit.driveId
            .ifBlank { unit.driveId }
            .ifBlank { existingMap?.driveFileId.orEmpty() }
        val nowMs = System.currentTimeMillis()

        val relatedPhotoMeta = getDrivePhotoMetadataByUnitOnce(freshUnit.id)
        val relatedDriveFileIds = relatedPhotoMeta.map { it.driveFileId }.filter { it.isNotBlank() }
        for (meta in relatedPhotoMeta) {
            deleteDrivePhotoMetadata(meta)
        }

        val childTxs = getAllTransactionsOnce().filter { it.unitId == freshUnit.id }
        val childProofs = childTxs.flatMap { getPaymentProofsForTransactionOnce(it.id) }

        // Soft-delete unit (DO NOT hard delete until Google Drive confirms deletion)
        softDeleteUnit(freshUnit.id, nowMs, MirrorOutboxCodec.STATUS_PENDING_DELETE)
        if (resolvedDriveId.isNotBlank() && freshUnit.driveId.isBlank()) {
            updateUnitSyncAndDriveId(freshUnit.id, MirrorOutboxCodec.STATUS_PENDING_DELETE, resolvedDriveId)
        }
        for (tx in childTxs) {
            softDeleteTransaction(tx.id, nowMs, MirrorOutboxCodec.STATUS_PENDING_DELETE)
        }
        for (proof in childProofs) {
            softDeletePaymentProof(proof.id, nowMs, MirrorOutboxCodec.STATUS_PENDING_DELETE)
        }

        val plan = MirrorOutboxCodec.buildDeleteOutboxPlan(
            localRecordKey = key,
            recordCategory = MirrorOutboxCodec.CATEGORY_UNIT,
            recordId = freshUnit.id,
            recordUuid = freshUnit.uuid,
            explicitDriveFileId = resolvedDriveId,
            datasetGenerationId = datasetGenerationId,
            existingMap = existingMap,
            latestQueue = getLatestDriveSyncQueueForKey(key),
            serverTimestampMillis = serverTimestampMillis
        )
        upsertDriveDeletionTombstone(plan.tombstone)
        plan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
        upsertDriveLocalSyncQueueItem(plan.queueItem)

        val photoKey = "PHOTO_FREEZER:UNIT:${freshUnit.id}"
        val photoMap = getDriveCloudFileMapByKey(photoKey)
        if (photoMap != null) {
            val photoPlan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                localRecordKey = photoKey,
                recordCategory = "PHOTO_FREEZER",
                recordId = freshUnit.id,
                recordUuid = photoMap.recordUuid,
                explicitDriveFileId = photoMap.driveFileId,
                datasetGenerationId = datasetGenerationId,
                existingMap = photoMap,
                latestQueue = getLatestDriveSyncQueueForKey(photoKey),
                serverTimestampMillis = serverTimestampMillis
            )
            upsertDriveDeletionTombstone(photoPlan.tombstone)
            photoPlan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
            upsertDriveLocalSyncQueueItem(photoPlan.queueItem)
        }

        for (tx in childTxs) {
            val txKey = "TRANSACTION:${tx.id}"
            val txMap = getDriveCloudFileMapByKey(txKey)
            val txDriveId = tx.driveId.ifBlank { txMap?.driveFileId.orEmpty() }
            val txPlan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                localRecordKey = txKey,
                recordCategory = MirrorOutboxCodec.CATEGORY_TRANSACTION,
                recordId = tx.id,
                recordUuid = tx.uuid,
                explicitDriveFileId = txDriveId,
                datasetGenerationId = datasetGenerationId,
                existingMap = txMap,
                latestQueue = getLatestDriveSyncQueueForKey(txKey),
                serverTimestampMillis = serverTimestampMillis
            )
            upsertDriveDeletionTombstone(txPlan.tombstone)
            txPlan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
            upsertDriveLocalSyncQueueItem(txPlan.queueItem)
        }
        for (proof in childProofs) {
            val pKey = "PAYMENT:${proof.id}"
            val pMap = getDriveCloudFileMapByKey(pKey)
            val pDriveId = proof.driveId.ifBlank { pMap?.driveFileId.orEmpty() }
            val pPlan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                localRecordKey = pKey,
                recordCategory = MirrorOutboxCodec.CATEGORY_PAYMENT,
                recordId = proof.id,
                recordUuid = proof.uuid,
                explicitDriveFileId = pDriveId,
                datasetGenerationId = datasetGenerationId,
                existingMap = pMap,
                latestQueue = getLatestDriveSyncQueueForKey(pKey),
                serverTimestampMillis = serverTimestampMillis
            )
            upsertDriveDeletionTombstone(pPlan.tombstone)
            pPlan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
            upsertDriveLocalSyncQueueItem(pPlan.queueItem)
        }
        return relatedDriveFileIds
    }

    @Transaction
    suspend fun saveCustomerWithOutboxAtomic(
        customer: CustomerEntity,
        isNew: Boolean,
        datasetId: String,
        datasetGenerationId: String,
        changedFieldsJson: String,
        serverTimestampMillis: Long = 0L
    ): Long {
        val existingRow = if (!isNew && customer.id > 0L) getCustomerByIdIncludingDeleted(customer.id) else null
        val existingKey = if (customer.id > 0L) "CUSTOMER:${customer.id}" else ""
        val existingMapPre = if (existingKey.isNotBlank()) getDriveCloudFileMapByKey(existingKey) else null
        val preservedDriveId = customer.driveId
            .ifBlank { existingRow?.driveId.orEmpty() }
            .ifBlank { existingMapPre?.driveFileId.orEmpty() }
        val stillUnsyncedCreate = !isNew &&
            preservedDriveId.isBlank() &&
            existingRow?.syncStatus == MirrorOutboxCodec.STATUS_PENDING_CREATE
        val nextStatus = if (isNew || stillUnsyncedCreate) {
            MirrorOutboxCodec.STATUS_PENDING_CREATE
        } else {
            MirrorOutboxCodec.STATUS_PENDING_UPDATE
        }
        val stamped = customer.copy(
            uuid = MirrorOutboxCodec.ensureValidUuidV4(customer.uuid),
            syncStatus = nextStatus,
            changedFieldsJson = changedFieldsJson,
            isDeleted = false,
            driveId = preservedDriveId
        )
        val savedId = if (stamped.id == 0L || isNew) {
            val insertedId = insertCustomer(stamped)
            if (stamped.kodePelanggan.isBlank()) {
                var counter = insertedId.toInt().coerceAtLeast(1)
                var codeCandidate: String
                while (true) {
                    codeCandidate = String.format(java.util.Locale.US, "CUST/SAF/%04d", counter)
                    val dup = getCustomerByKodeIncludingDeleted(codeCandidate)
                    if (dup == null || dup.id == insertedId) break
                    counter++
                }
                updateCustomer(stamped.copy(id = insertedId, kodePelanggan = codeCandidate))
            }
            insertedId
        } else {
            val resolvedCode = if (stamped.kodePelanggan.isBlank()) {
                var counter = stamped.id.toInt().coerceAtLeast(1)
                var codeCandidate: String
                while (true) {
                    codeCandidate = String.format(java.util.Locale.US, "CUST/SAF/%04d", counter)
                    val dup = getCustomerByKodeIncludingDeleted(codeCandidate)
                    if (dup == null || dup.id == stamped.id) break
                    counter++
                }
                codeCandidate
            } else stamped.kodePelanggan
            updateCustomer(stamped.copy(kodePelanggan = resolvedCode))
            stamped.id
        }
        val persisted = getCustomerByIdIncludingDeleted(savedId) ?: stamped.copy(id = savedId)
        val key = "CUSTOMER:$savedId"
        val payload = MirrorOutboxCodec.serializeCustomerJson(persisted, datasetId, datasetGenerationId, serverTimestampMillis)
        val plan = MirrorOutboxCodec.buildUpsertOutboxPlan(
            localRecordKey = key,
            recordCategory = MirrorOutboxCodec.CATEGORY_CUSTOMER,
            recordId = savedId,
            recordUuid = persisted.uuid,
            entityVersion = persisted.version,
            updatedAtMillis = persisted.updatedAtMillis,
            payloadJson = payload,
            isNew = isNew,
            datasetGenerationId = datasetGenerationId,
            changedFieldsJson = changedFieldsJson,
            existingMap = getDriveCloudFileMapByKey(key),
            latestQueue = getLatestDriveSyncQueueForKey(key),
            serverTimestampMillis = serverTimestampMillis,
            explicitDriveFileId = persisted.driveId
        )
        if (plan != null) {
            deleteDriveDeletionTombstoneByKey(plan.localRecordKey)
            if (plan.recordUuid.isNotBlank()) deleteDriveDeletionTombstoneByUuid(plan.recordUuid)
            upsertDriveCloudFileMap(plan.fileMap)
            upsertDriveLocalSyncQueueItem(plan.queueItem)
        }
        return savedId
    }

    @Transaction
    suspend fun deleteCustomerWithOutboxAtomic(
        customer: CustomerEntity,
        datasetGenerationId: String,
        serverTimestampMillis: Long = 0L
    ): Pair<List<String>, List<String>> {
        val freshCustomer = getCustomerByIdIncludingDeleted(customer.id) ?: customer
        val key = "CUSTOMER:${freshCustomer.id}"
        val existingMap = getDriveCloudFileMapByKey(key)
        val resolvedDriveId = freshCustomer.driveId
            .ifBlank { customer.driveId }
            .ifBlank { existingMap?.driveFileId.orEmpty() }
        val nowMs = System.currentTimeMillis()

        val relatedPhotoMeta = getDrivePhotoMetadataByCustomerOnce(freshCustomer.id)
        val relatedDriveFileIds = relatedPhotoMeta.map { it.driveFileId }.filter { it.isNotBlank() }
        val relatedSlotMaps = getDriveCloudFileMapsByCategoryAndId("PHOTO_KTP", freshCustomer.id) +
            getDriveCloudFileMapsByCategoryAndId("PHOTO_CUSTOMER", freshCustomer.id)
        for (meta in relatedPhotoMeta) {
            deleteDrivePhotoMetadata(meta)
        }
        val childTxs = getAllTransactionsOnce().filter { it.customerId == freshCustomer.id }
        val childProofs = childTxs.flatMap { getPaymentProofsForTransactionOnce(it.id) }

        // Soft-delete customer + child transactions + child proofs (NEVER hard delete before Drive confirmation)
        softDeleteCustomer(freshCustomer.id, nowMs, MirrorOutboxCodec.STATUS_PENDING_DELETE)
        if (resolvedDriveId.isNotBlank() && freshCustomer.driveId.isBlank()) {
            updateCustomerSyncAndDriveId(freshCustomer.id, MirrorOutboxCodec.STATUS_PENDING_DELETE, resolvedDriveId)
        }
        for (tx in childTxs) {
            softDeleteTransaction(tx.id, nowMs, MirrorOutboxCodec.STATUS_PENDING_DELETE)
        }
        for (proof in childProofs) {
            softDeletePaymentProof(proof.id, nowMs, MirrorOutboxCodec.STATUS_PENDING_DELETE)
        }

        val plan = MirrorOutboxCodec.buildDeleteOutboxPlan(
            localRecordKey = key,
            recordCategory = MirrorOutboxCodec.CATEGORY_CUSTOMER,
            recordId = freshCustomer.id,
            recordUuid = freshCustomer.uuid,
            explicitDriveFileId = resolvedDriveId,
            datasetGenerationId = datasetGenerationId,
            existingMap = existingMap,
            latestQueue = getLatestDriveSyncQueueForKey(key),
            serverTimestampMillis = serverTimestampMillis
        )
        upsertDriveDeletionTombstone(plan.tombstone)
        plan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
        upsertDriveLocalSyncQueueItem(plan.queueItem)

        val slotKeys = buildSet {
            add("PHOTO_KTP:CUSTOMER:${freshCustomer.id}")
            add("PHOTO_CUSTOMER:CUSTOMER:${freshCustomer.id}")
            relatedSlotMaps.forEach { add(it.localRecordKey) }
        }
        for (slotKey in slotKeys) {
            val cat = if (slotKey.startsWith("PHOTO_KTP")) "PHOTO_KTP" else "PHOTO_CUSTOMER"
            val slotMap = getDriveCloudFileMapByKey(slotKey)
            if (slotMap != null) {
                val pPlan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                    localRecordKey = slotKey,
                    recordCategory = cat,
                    recordId = freshCustomer.id,
                    recordUuid = slotMap.recordUuid,
                    explicitDriveFileId = slotMap.driveFileId,
                    datasetGenerationId = datasetGenerationId,
                    existingMap = slotMap,
                    latestQueue = getLatestDriveSyncQueueForKey(slotKey),
                    serverTimestampMillis = serverTimestampMillis
                )
                upsertDriveDeletionTombstone(pPlan.tombstone)
                pPlan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
                upsertDriveLocalSyncQueueItem(pPlan.queueItem)
            }
        }
        for (tx in childTxs) {
            val txKey = "TRANSACTION:${tx.id}"
            val txMap = getDriveCloudFileMapByKey(txKey)
            val txDriveId = tx.driveId.ifBlank { txMap?.driveFileId.orEmpty() }
            val txPlan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                localRecordKey = txKey,
                recordCategory = MirrorOutboxCodec.CATEGORY_TRANSACTION,
                recordId = tx.id,
                recordUuid = tx.uuid,
                explicitDriveFileId = txDriveId,
                datasetGenerationId = datasetGenerationId,
                existingMap = txMap,
                latestQueue = getLatestDriveSyncQueueForKey(txKey),
                serverTimestampMillis = serverTimestampMillis
            )
            upsertDriveDeletionTombstone(txPlan.tombstone)
            txPlan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
            upsertDriveLocalSyncQueueItem(txPlan.queueItem)
        }
        for (proof in childProofs) {
            val pKey = "PAYMENT:${proof.id}"
            val pMap = getDriveCloudFileMapByKey(pKey)
            val pDriveId = proof.driveId.ifBlank { pMap?.driveFileId.orEmpty() }
            val pPlan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                localRecordKey = pKey,
                recordCategory = MirrorOutboxCodec.CATEGORY_PAYMENT,
                recordId = proof.id,
                recordUuid = proof.uuid,
                explicitDriveFileId = pDriveId,
                datasetGenerationId = datasetGenerationId,
                existingMap = pMap,
                latestQueue = getLatestDriveSyncQueueForKey(pKey),
                serverTimestampMillis = serverTimestampMillis
            )
            upsertDriveDeletionTombstone(pPlan.tombstone)
            pPlan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
            upsertDriveLocalSyncQueueItem(pPlan.queueItem)
        }
        return relatedSlotMaps.map { it.localRecordKey } to relatedDriveFileIds
    }

    @Transaction
    suspend fun saveTransactionWithOutboxAtomic(
        transaction: RentalTransactionEntity,
        isNew: Boolean,
        datasetId: String,
        datasetGenerationId: String,
        changedFieldsJson: String,
        serverTimestampMillis: Long = 0L
    ): Long {
        val resolvedUuid = MirrorOutboxCodec.ensureValidUuidV4(transaction.uuid)
        val existingRow = when {
            !isNew && transaction.id > 0L -> getTransactionByIdIncludingDeleted(transaction.id)
            resolvedUuid.isNotBlank() -> getTransactionByUuidIncludingDeleted(resolvedUuid)
            else -> null
        }
        val effectiveId = existingRow?.id?.takeIf { it > 0L } ?: transaction.id
        val existingKey = if (effectiveId > 0L) "TRANSACTION:$effectiveId" else ""
        val existingMapPre = (if (existingKey.isNotBlank()) getDriveCloudFileMapByKey(existingKey) else null)
            ?: getDriveCloudFileMapByUuid(resolvedUuid)
        val preservedDriveId = transaction.driveId
            .ifBlank { existingRow?.driveId.orEmpty() }
            .ifBlank { existingMapPre?.driveFileId.orEmpty() }
        val isEffectiveCreate = existingRow == null && effectiveId == 0L
        val stillUnsyncedCreate = !isEffectiveCreate &&
            preservedDriveId.isBlank() &&
            existingRow?.syncStatus == MirrorOutboxCodec.STATUS_PENDING_CREATE
        val nextStatus = if (isEffectiveCreate || stillUnsyncedCreate) {
            MirrorOutboxCodec.STATUS_PENDING_CREATE
        } else {
            MirrorOutboxCodec.STATUS_PENDING_UPDATE
        }
        val stamped = transaction.copy(
            id = effectiveId,
            uuid = resolvedUuid,
            syncStatus = nextStatus,
            changedFieldsJson = changedFieldsJson,
            isDeleted = false,
            driveId = preservedDriveId
        )
        val savedId = if (isEffectiveCreate) {
            insertTransaction(stamped)
        } else {
            updateTransaction(stamped)
            stamped.id
        }
        val persisted = getTransactionByIdIncludingDeleted(savedId)
            ?: getTransactionByUuidIncludingDeleted(resolvedUuid)
            ?: stamped.copy(id = savedId)
        val committedId = persisted.id.takeIf { it > 0L } ?: savedId
        val key = "TRANSACTION:$committedId"
        val payload = MirrorOutboxCodec.serializeTransactionJson(persisted, datasetId, datasetGenerationId, serverTimestampMillis)
        val plan = MirrorOutboxCodec.buildUpsertOutboxPlan(
            localRecordKey = key,
            recordCategory = MirrorOutboxCodec.CATEGORY_TRANSACTION,
            recordId = committedId,
            recordUuid = persisted.uuid,
            entityVersion = persisted.version,
            updatedAtMillis = persisted.updatedAtMillis,
            payloadJson = payload,
            isNew = isEffectiveCreate,
            datasetGenerationId = datasetGenerationId,
            changedFieldsJson = changedFieldsJson,
            existingMap = getDriveCloudFileMapByKey(key) ?: getDriveCloudFileMapByUuid(persisted.uuid),
            latestQueue = getLatestDriveSyncQueueForKey(key),
            serverTimestampMillis = serverTimestampMillis,
            explicitDriveFileId = persisted.driveId
        )
        if (plan != null) {
            deleteDriveDeletionTombstoneByKey(plan.localRecordKey)
            if (plan.recordUuid.isNotBlank()) deleteDriveDeletionTombstoneByUuid(plan.recordUuid)
            upsertDriveCloudFileMap(plan.fileMap)
            upsertDriveLocalSyncQueueItem(plan.queueItem)
        }
        return committedId
    }

    /**
     * Atomic Room @Transaction commit for Parent Transaction + Child `transaction_unit_items` + Drive Outbox.
     * Guarantees valid committed `rowId > 0L` and permanent UUID v4 in SQLite BEFORE PDF generation or Google Drive upload.
     */
    @Transaction
    suspend fun saveMultiUnitTransactionAtomic(
        parentTransaction: RentalTransactionEntity,
        normalizedItems: List<TransactionUnitItemEntity>,
        previousItems: List<TransactionUnitItemEntity>,
        isNew: Boolean,
        datasetId: String,
        datasetGenerationId: String,
        changedFieldsJson: String,
        nowDisplay: String,
        serverTimestampMillis: Long = 0L
    ): Pair<RentalTransactionEntity, List<TransactionUnitItemEntity>> {
        val savedTxId = saveTransactionWithOutboxAtomic(
            transaction = parentTransaction,
            isNew = isNew,
            datasetId = datasetId,
            datasetGenerationId = datasetGenerationId,
            changedFieldsJson = changedFieldsJson,
            serverTimestampMillis = serverTimestampMillis
        )
        val committedParent = getTransactionById(savedTxId)
            ?: getTransactionByUuid(parentTransaction.uuid)
            ?: throw IllegalStateException("Commit atomik transaksi gagal: rowId=$savedTxId atau UUID tidak ditemukan di SQLite.")

        val prevById = previousItems.associateBy { it.id }
        val prevByUnitId = previousItems.associateBy { it.unitId }
        val retainedExistingIds = mutableSetOf<Long>()
        val persistedUnitItems = mutableListOf<TransactionUnitItemEntity>()

        normalizedItems.forEachIndexed { idx, item ->
            val matchedExisting = (if (item.id > 0L) prevById[item.id] else null) ?: prevByUnitId[item.unitId]
            val folderPath = item.storageFolderPath.ifBlank {
                matchedExisting?.storageFolderPath?.ifBlank {
                    "SEWAIN_AJA_FREEZER/TRANSACTIONS/TX_${committedParent.id}/UNITS/UNIT_${item.unitId}"
                } ?: "SEWAIN_AJA_FREEZER/TRANSACTIONS/TX_${committedParent.id}/UNITS/UNIT_${item.unitId}"
            }
            if (matchedExisting != null) {
                val updatedRow = item.copy(
                    id = matchedExisting.id,
                    transactionId = committedParent.id,
                    customerId = committedParent.customerId,
                    unitIndexNumber = idx + 1,
                    storageFolderPath = folderPath,
                    createdAt = matchedExisting.createdAt.ifBlank { item.createdAt.ifBlank { nowDisplay } },
                    updatedAt = nowDisplay
                )
                updateTransactionUnitItem(updatedRow)
                retainedExistingIds.add(matchedExisting.id)
                persistedUnitItems.add(updatedRow)
            } else {
                val newRow = item.copy(
                    id = 0L,
                    transactionId = committedParent.id,
                    customerId = committedParent.customerId,
                    unitIndexNumber = idx + 1,
                    storageFolderPath = folderPath,
                    createdAt = item.createdAt.ifBlank { nowDisplay },
                    updatedAt = nowDisplay
                )
                val insertedItemId = insertTransactionUnitItem(newRow)
                persistedUnitItems.add(newRow.copy(id = insertedItemId))
            }
        }

        for (oldItem in previousItems) {
            if (oldItem.id !in retainedExistingIds) {
                deleteTransactionUnitItemById(oldItem.id)
            }
        }

        return committedParent to persistedUnitItems
    }

    @Transaction
    suspend fun deleteTransactionWithOutboxAtomic(
        transaction: RentalTransactionEntity,
        datasetGenerationId: String,
        serverTimestampMillis: Long = 0L,
        deletedBy: String = ""
    ): Pair<List<TransactionUnitItemEntity>, List<String>> {
        val freshTx = getTransactionByIdIncludingDeleted(transaction.id) ?: transaction
        val key = "TRANSACTION:${freshTx.id}"
        val existingMap = getDriveCloudFileMapByKey(key)
        val resolvedDriveId = freshTx.driveId
            .ifBlank { transaction.driveId }
            .ifBlank { existingMap?.driveFileId.orEmpty() }
        val nowMs = System.currentTimeMillis()
        val resolvedDeletedBy = deletedBy.ifBlank { freshTx.deletedBy }.ifBlank { freshTx.namaPetugas }.ifBlank { "Admin" }

        val unitItems = getTransactionUnitItemsByTxOnce(freshTx.id)
        val childProofs = getPaymentProofsForTransactionOnce(freshTx.id)
        val relatedPhotoMeta = getDrivePhotoMetadataByTransactionOnce(freshTx.id)
        val relatedDriveFileIds = relatedPhotoMeta.map { it.driveFileId }.filter { it.isNotBlank() }
        for (meta in relatedPhotoMeta) {
            deleteDrivePhotoMetadata(meta)
        }

        // Soft-delete transaction + child payment proofs (NEVER hard delete before Drive confirmation)
        softDeleteTransaction(
            id = freshTx.id,
            updatedAt = nowMs,
            syncStatus = MirrorOutboxCodec.STATUS_PENDING_DELETE,
            deletedAt = nowMs,
            deletedBy = resolvedDeletedBy
        )
        if (resolvedDriveId.isNotBlank() && freshTx.driveId.isBlank()) {
            updateTransactionSyncAndDriveId(freshTx.id, MirrorOutboxCodec.STATUS_PENDING_DELETE, resolvedDriveId)
        }
        for (proof in childProofs) {
            softDeletePaymentProof(proof.id, nowMs, MirrorOutboxCodec.STATUS_PENDING_DELETE)
        }

        val plan = MirrorOutboxCodec.buildDeleteOutboxPlan(
            localRecordKey = key,
            recordCategory = MirrorOutboxCodec.CATEGORY_TRANSACTION,
            recordId = freshTx.id,
            recordUuid = freshTx.uuid,
            explicitDriveFileId = resolvedDriveId,
            datasetGenerationId = datasetGenerationId,
            existingMap = existingMap,
            latestQueue = getLatestDriveSyncQueueForKey(key),
            serverTimestampMillis = serverTimestampMillis
        )
        upsertDriveDeletionTombstone(plan.tombstone)
        plan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
        upsertDriveLocalSyncQueueItem(plan.queueItem)

        for (slotPair in listOf(
            "PHOTO_TRANSACTION:TX:${freshTx.id}:SERAH_TERIMA" to "PHOTO_TRANSACTION",
            "PHOTO_AGREEMENT:TX:${freshTx.id}:SIGNATURE" to "PHOTO_AGREEMENT"
        )) {
            val sMap = getDriveCloudFileMapByKey(slotPair.first)
            if (sMap != null) {
                val pPlan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                    localRecordKey = slotPair.first,
                    recordCategory = slotPair.second,
                    recordId = freshTx.id,
                    recordUuid = sMap.recordUuid,
                    explicitDriveFileId = sMap.driveFileId,
                    datasetGenerationId = datasetGenerationId,
                    existingMap = sMap,
                    latestQueue = getLatestDriveSyncQueueForKey(slotPair.first),
                    serverTimestampMillis = serverTimestampMillis
                )
                upsertDriveDeletionTombstone(pPlan.tombstone)
                pPlan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
                upsertDriveLocalSyncQueueItem(pPlan.queueItem)
            }
        }
        for (proof in childProofs) {
            val pKey = "PAYMENT:${proof.id}"
            val pMap = getDriveCloudFileMapByKey(pKey)
            val pDriveId = proof.driveId.ifBlank { pMap?.driveFileId.orEmpty() }
            val pPlan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                localRecordKey = pKey,
                recordCategory = MirrorOutboxCodec.CATEGORY_PAYMENT,
                recordId = proof.id,
                recordUuid = proof.uuid,
                explicitDriveFileId = pDriveId,
                datasetGenerationId = datasetGenerationId,
                existingMap = pMap,
                latestQueue = getLatestDriveSyncQueueForKey(pKey),
                serverTimestampMillis = serverTimestampMillis
            )
            upsertDriveDeletionTombstone(pPlan.tombstone)
            pPlan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
            upsertDriveLocalSyncQueueItem(pPlan.queueItem)
        }
        return unitItems to relatedDriveFileIds
    }

    @Transaction
    suspend fun savePaymentProofWithOutboxAtomic(
        proof: PaymentProofEntity,
        isNew: Boolean,
        datasetId: String,
        datasetGenerationId: String,
        changedFieldsJson: String,
        serverTimestampMillis: Long = 0L
    ): Long {
        val existingRow = if (!isNew && proof.id > 0L) getPaymentProofByIdIncludingDeleted(proof.id) else null
        val existingKey = if (proof.id > 0L) "PAYMENT:${proof.id}" else ""
        val existingMapPre = if (existingKey.isNotBlank()) getDriveCloudFileMapByKey(existingKey) else null
        val preservedDriveId = proof.driveId
            .ifBlank { existingRow?.driveId.orEmpty() }
            .ifBlank { existingMapPre?.driveFileId.orEmpty() }
        val stillUnsyncedCreate = !isNew &&
            preservedDriveId.isBlank() &&
            existingRow?.syncStatus == MirrorOutboxCodec.STATUS_PENDING_CREATE
        val nextStatus = if (isNew || stillUnsyncedCreate) {
            MirrorOutboxCodec.STATUS_PENDING_CREATE
        } else {
            MirrorOutboxCodec.STATUS_PENDING_UPDATE
        }
        val stamped = proof.copy(
            uuid = MirrorOutboxCodec.ensureValidUuidV4(proof.uuid),
            syncStatus = nextStatus,
            changedFieldsJson = changedFieldsJson,
            isDeleted = false,
            driveId = preservedDriveId
        )
        val savedId = if (stamped.id == 0L || isNew) {
            insertPaymentProof(stamped)
        } else {
            updatePaymentProof(stamped)
            stamped.id
        }
        val persisted = getPaymentProofByIdIncludingDeleted(savedId) ?: stamped.copy(id = savedId)
        val key = "PAYMENT:$savedId"
        val payload = MirrorOutboxCodec.serializePaymentJson(persisted, datasetId, datasetGenerationId, serverTimestampMillis)
        val plan = MirrorOutboxCodec.buildUpsertOutboxPlan(
            localRecordKey = key,
            recordCategory = MirrorOutboxCodec.CATEGORY_PAYMENT,
            recordId = savedId,
            recordUuid = persisted.uuid,
            entityVersion = persisted.version,
            updatedAtMillis = persisted.updatedAtMillis,
            payloadJson = payload,
            isNew = isNew,
            datasetGenerationId = datasetGenerationId,
            changedFieldsJson = changedFieldsJson,
            existingMap = getDriveCloudFileMapByKey(key),
            latestQueue = getLatestDriveSyncQueueForKey(key),
            serverTimestampMillis = serverTimestampMillis,
            explicitDriveFileId = persisted.driveId
        )
        if (plan != null) {
            deleteDriveDeletionTombstoneByKey(plan.localRecordKey)
            if (plan.recordUuid.isNotBlank()) deleteDriveDeletionTombstoneByUuid(plan.recordUuid)
            upsertDriveCloudFileMap(plan.fileMap)
            upsertDriveLocalSyncQueueItem(plan.queueItem)
        }
        return savedId
    }

    @Transaction
    suspend fun deletePaymentProofWithOutboxAtomic(
        proof: PaymentProofEntity,
        datasetGenerationId: String,
        serverTimestampMillis: Long = 0L
    ) {
        val freshProof = getPaymentProofByIdIncludingDeleted(proof.id) ?: proof
        val key = "PAYMENT:${freshProof.id}"
        val existingMap = getDriveCloudFileMapByKey(key)
        val resolvedDriveId = freshProof.driveId
            .ifBlank { proof.driveId }
            .ifBlank { existingMap?.driveFileId.orEmpty() }
        val nowMs = System.currentTimeMillis()

        // Soft-delete payment proof (NEVER hard delete before Drive confirmation)
        softDeletePaymentProof(freshProof.id, nowMs, MirrorOutboxCodec.STATUS_PENDING_DELETE)
        if (resolvedDriveId.isNotBlank() && freshProof.driveId.isBlank()) {
            updatePaymentProofSyncAndDriveId(freshProof.id, MirrorOutboxCodec.STATUS_PENDING_DELETE, resolvedDriveId)
        }

        val plan = MirrorOutboxCodec.buildDeleteOutboxPlan(
            localRecordKey = key,
            recordCategory = MirrorOutboxCodec.CATEGORY_PAYMENT,
            recordId = freshProof.id,
            recordUuid = freshProof.uuid,
            explicitDriveFileId = resolvedDriveId,
            datasetGenerationId = datasetGenerationId,
            existingMap = existingMap,
            latestQueue = getLatestDriveSyncQueueForKey(key),
            serverTimestampMillis = serverTimestampMillis
        )
        upsertDriveDeletionTombstone(plan.tombstone)
        plan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
        upsertDriveLocalSyncQueueItem(plan.queueItem)

        val photoKey = "PHOTO_PAYMENT:PAYMENT:${freshProof.id}"
        val photoMap = getDriveCloudFileMapByKey(photoKey)
        if (photoMap != null) {
            val photoPlan = MirrorOutboxCodec.buildDeleteOutboxPlan(
                localRecordKey = photoKey,
                recordCategory = "PHOTO_PAYMENT",
                recordId = freshProof.id,
                recordUuid = photoMap.recordUuid,
                explicitDriveFileId = photoMap.driveFileId,
                datasetGenerationId = datasetGenerationId,
                existingMap = photoMap,
                latestQueue = getLatestDriveSyncQueueForKey(photoKey),
                serverTimestampMillis = serverTimestampMillis
            )
            upsertDriveDeletionTombstone(photoPlan.tombstone)
            photoPlan.updatedFileMap?.let { upsertDriveCloudFileMap(it) }
            upsertDriveLocalSyncQueueItem(photoPlan.queueItem)
        }
    }

    @Transaction
    suspend fun clearAllCoreAndSyncTablesForFullReset(
        keepTombstones: Boolean = false,
        clearLocalBackupHistory: Boolean = false
    ) {
        deleteAllUnitPaymentAllocations()
        deleteAllTransactionSignatures()
        deleteAllTransactionOfficerAuditLogs()
        deleteAllTransactionUnitItems()
        deleteAllReturns()
        deleteAllPaymentProofs()
        deleteAllExtensions()
        deleteAllTransactionDocuments()
        deleteAllCustomerTimelineEvents()
        deleteAllCustomerPhotos()
        deleteAllCustomerLocations()
        deleteAllCustomerDocuments()
        deleteAllTransactions()
        deleteAllUnitRentalPrices()
        deleteAllFreezerUnitMaintenance()
        deleteAllUnits()
        deleteAllCustomers()
        deleteAllFormDrafts()
        deleteAllDrivePhotoMetadata()
        deleteAllDriveCloudFileMaps()
        deleteAllDriveLocalSyncQueue()
        deleteAllDriveCloudQuarantine()
        deleteAllLegacyCloudSyncQueue()
        deleteAllLegacyCloudPhotoJobs()
        deleteAllLegacyCloudMigrationState()
        if (clearLocalBackupHistory) {
            deleteAllLocalBackups()
        }
        if (!keepTombstones) {
            deleteAllDriveDeletionTombstones()
        }
    }

    // --- AUTO-DRAFT ISOLATED STORAGE (v23: form_drafts) ---
    @Query("SELECT * FROM form_drafts ORDER BY updated_at DESC")
    fun observeAllFormDrafts(): Flow<List<FormDraftEntity>>

    @Query("SELECT * FROM form_drafts ORDER BY updated_at DESC")
    suspend fun getAllFormDraftsOnce(): List<FormDraftEntity>

    @Query("SELECT * FROM form_drafts WHERE uuid = :uuid LIMIT 1")
    suspend fun getFormDraftByUuid(uuid: String): FormDraftEntity?

    @Query(
        """
        SELECT * FROM form_drafts
        WHERE form_type = :formType AND target_entity_id = :targetEntityId
        ORDER BY updated_at DESC LIMIT 1
        """
    )
    suspend fun getLatestFormDraftByTypeAndTarget(formType: String, targetEntityId: Long): FormDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFormDraft(draft: FormDraftEntity): Long

    @Query("DELETE FROM form_drafts WHERE uuid = :uuid")
    suspend fun deleteFormDraftByUuid(uuid: String)

    @Query("DELETE FROM form_drafts WHERE form_type = :formType AND target_entity_id = :targetEntityId")
    suspend fun deleteFormDraftsByTypeAndTarget(formType: String, targetEntityId: Long)

    @Query("DELETE FROM form_drafts")
    suspend fun deleteAllFormDrafts()

    // --- PER-UNIT RENTAL PERIOD PRICING (v23: unit_rental_prices) ---
    @Query("SELECT * FROM unit_rental_prices ORDER BY unit_id ASC, period_days ASC")
    fun observeAllUnitRentalPrices(): Flow<List<UnitRentalPriceEntity>>

    @Query("SELECT * FROM unit_rental_prices ORDER BY unit_id ASC, period_days ASC")
    suspend fun getAllUnitRentalPricesOnce(): List<UnitRentalPriceEntity>

    @Query("SELECT * FROM unit_rental_prices WHERE unit_id = :unitId ORDER BY period_days ASC")
    fun observeUnitRentalPricesByUnit(unitId: Long): Flow<List<UnitRentalPriceEntity>>

    @Query("SELECT * FROM unit_rental_prices WHERE unit_id = :unitId ORDER BY period_days ASC")
    suspend fun getUnitRentalPricesByUnitOnce(unitId: Long): List<UnitRentalPriceEntity>

    @Query("SELECT * FROM unit_rental_prices WHERE unit_id = :unitId AND period_days = :periodDays LIMIT 1")
    suspend fun getUnitRentalPriceForPeriod(unitId: Long, periodDays: Int): UnitRentalPriceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnitRentalPrice(entity: UnitRentalPriceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnitRentalPrices(entities: List<UnitRentalPriceEntity>)

    @Query("DELETE FROM unit_rental_prices WHERE unit_id = :unitId")
    suspend fun deleteUnitRentalPricesByUnit(unitId: Long)

    @Query("DELETE FROM unit_rental_prices")
    suspend fun deleteAllUnitRentalPrices()

    @Transaction
    suspend fun replaceUnitRentalPricesAtomic(unitId: Long, pricesByPeriod: Map<Int, Long>) {
        deleteUnitRentalPricesByUnit(unitId)
        val now = System.currentTimeMillis()
        val toInsert = pricesByPeriod
            .filter { (days, price) -> days > 0 && price > 0L }
            .map { (days, price) ->
                UnitRentalPriceEntity(
                    unitId = unitId,
                    periodDays = days,
                    price = price,
                    updatedAtMillis = now
                )
            }
        if (toInsert.isNotEmpty()) {
            upsertUnitRentalPrices(toInsert)
        }
    }

    // --- TRANSACTION OFFICER CORRECTION AUDIT LOGS (v23: transaction_officer_audit_logs) ---
    @Query("SELECT * FROM transaction_officer_audit_logs ORDER BY corrected_at_millis DESC, id DESC")
    fun observeAllTransactionOfficerAuditLogs(): Flow<List<TransactionOfficerAuditLogEntity>>

    @Query("SELECT * FROM transaction_officer_audit_logs WHERE transaction_id = :transactionId ORDER BY corrected_at_millis DESC, id DESC")
    fun observeOfficerAuditLogsByTransaction(transactionId: Long): Flow<List<TransactionOfficerAuditLogEntity>>

    @Query("SELECT * FROM transaction_officer_audit_logs WHERE transaction_id = :transactionId ORDER BY corrected_at_millis DESC, id DESC")
    suspend fun getOfficerAuditLogsByTransactionOnce(transactionId: Long): List<TransactionOfficerAuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionOfficerAuditLog(entity: TransactionOfficerAuditLogEntity): Long

    @Query("DELETE FROM transaction_officer_audit_logs")
    suspend fun deleteAllTransactionOfficerAuditLogs()

    // --- V25 LOCATION & FREEZER DOCUMENTATION SYNC TRACKING ---
    @Query("SELECT * FROM customer_photos WHERE transactionId = :transactionId ORDER BY id DESC")
    suspend fun getCustomerPhotosByTransactionOnce(transactionId: Long): List<CustomerPhotoEntity>

    @Query("SELECT * FROM customer_photos WHERE customerId = :customerId AND kategori = :category ORDER BY id DESC")
    suspend fun getCustomerPhotosByCustomerAndCategoryOnce(customerId: Long, category: String): List<CustomerPhotoEntity>

    @Query(
        """
        UPDATE customer_photos
        SET upload_status = :uploadStatus,
            drive_file_id = :driveFileId,
            updated_at_millis = :updatedAtMillis
        WHERE id = :photoId
        """
    )
    suspend fun updateCustomerPhotoSyncStatus(
        photoId: Long,
        uploadStatus: String,
        driveFileId: String = "",
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE customer_photos
        SET upload_status = :uploadStatus,
            drive_file_id = :driveFileId,
            updated_at_millis = :updatedAtMillis
        WHERE filePath = :filePath
        """
    )
    suspend fun updateCustomerPhotoSyncStatusByPath(
        filePath: String,
        uploadStatus: String,
        driveFileId: String = "",
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    // --- V25 DETAILED MULTI-UNIT RENEWAL QUERIES & ATOMIC EXECUTION ---
    @Query("SELECT * FROM extension_history WHERE customer_id = :customerId ORDER BY id DESC")
    suspend fun getExtensionHistoryByCustomerOnce(customerId: Long): List<RentalExtensionHistoryEntity>

    @Query("SELECT * FROM extension_history WHERE unit_id = :unitId ORDER BY id DESC")
    suspend fun getExtensionHistoryByUnitOnce(unitId: Long): List<RentalExtensionHistoryEntity>

    @Query("SELECT * FROM unit_payment_allocations WHERE unit_id = :unitId ORDER BY id DESC")
    suspend fun getUnitPaymentAllocationsByUnitOnce(unitId: Long): List<UnitPaymentAllocationEntity>

    @Transaction
    suspend fun executeDetailedRenewalAtomic(
        newTransaction: RentalTransactionEntity,
        newTxChangedFieldsJson: String,
        newUnitItems: List<TransactionUnitItemEntity>,
        updatedSourceUnitItems: List<TransactionUnitItemEntity>,
        updatedSourceTransactions: List<Pair<RentalTransactionEntity, String>>,
        extensionRecords: List<RentalExtensionHistoryEntity>,
        paymentAllocations: List<UnitPaymentAllocationEntity>,
        updatedNewUnitsToRented: List<Pair<FreezerUnitEntity, String>>,
        timelineEvent: CustomerTimelineEventEntity?,
        datasetId: String,
        datasetGenerationId: String,
        serverTimestampMillis: Long
    ): Long {
        val newTxId = saveTransactionWithOutboxAtomic(
            transaction = newTransaction,
            isNew = true,
            datasetId = datasetId,
            datasetGenerationId = datasetGenerationId,
            changedFieldsJson = newTxChangedFieldsJson,
            serverTimestampMillis = serverTimestampMillis
        )

        updatedSourceUnitItems.forEach { srcItem ->
            updateTransactionUnitItem(srcItem)
        }

        updatedSourceTransactions.forEach { (srcTx, changedJson) ->
            saveTransactionWithOutboxAtomic(
                transaction = srcTx,
                isNew = false,
                datasetId = datasetId,
                datasetGenerationId = datasetGenerationId,
                changedFieldsJson = changedJson,
                serverTimestampMillis = serverTimestampMillis
            )
        }

        val insertedItemIdByUnitId = mutableMapOf<Long, Long>()
        newUnitItems.forEach { item ->
            val insertedItemId = insertTransactionUnitItem(item.copy(id = 0L, transactionId = newTxId))
            insertedItemIdByUnitId[item.unitId] = insertedItemId
        }

        extensionRecords.forEach { ext ->
            insertExtension(ext.copy(id = 0L, newTransactionId = newTxId))
        }

        paymentAllocations.forEach { alloc ->
            val targetItemId = if (alloc.unitItemId > 0L) {
                alloc.unitItemId
            } else {
                insertedItemIdByUnitId[alloc.unitId] ?: 0L
            }
            if (targetItemId > 0L) {
                insertUnitPaymentAllocation(
                    alloc.copy(
                        id = 0L,
                        transactionId = if (alloc.transactionId > 0L) alloc.transactionId else newTxId,
                        unitItemId = targetItemId
                    )
                )
            }
        }

        updatedNewUnitsToRented.forEach { (rentedUnit, unitChangedJson) ->
            saveUnitWithOutboxAtomic(
                unit = rentedUnit,
                isNew = false,
                datasetId = datasetId,
                datasetGenerationId = datasetGenerationId,
                changedFieldsJson = unitChangedJson,
                serverTimestampMillis = serverTimestampMillis
            )
        }

        if (timelineEvent != null) {
            insertCustomerTimelineEvent(timelineEvent.copy(transactionId = newTxId))
        }

        return newTxId
    }
}





