package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) untuk riwayat dan catatan transaksi penyewaan (`RentalTransactionEntity`) di Room Database.
 * Menyediakan kueri reaktif menggunakan `Flow<List<RentalTransactionEntity>>` dan operasi CRUD lokal.
 */
@Dao
interface RentalTransactionDao {

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

    @Query("SELECT * FROM rental_transactions WHERE unitId = :unitId AND is_deleted = 0 ORDER BY tanggalMulaiSewa DESC")
    fun getTransactionsForUnit(unitId: Long): Flow<List<RentalTransactionEntity>>

    @Query("SELECT * FROM rental_transactions WHERE customerId = :customerId AND is_deleted = 0 ORDER BY tanggalMulaiSewa DESC")
    fun getTransactionsForCustomer(customerId: Long): Flow<List<RentalTransactionEntity>>

    @Query("SELECT * FROM rental_transactions WHERE statusPembayaran = :status AND is_deleted = 0 ORDER BY tanggalJatuhTempo ASC")
    fun getTransactionsByPaymentStatus(status: String): Flow<List<RentalTransactionEntity>>

    @Query("SELECT * FROM rental_transactions WHERE statusPenyewaan = :status AND is_deleted = 0 ORDER BY id DESC")
    fun getTransactionsByRentalStatus(status: String): Flow<List<RentalTransactionEntity>>

    @Query("SELECT COUNT(*) FROM rental_transactions WHERE is_deleted = 0")
    suspend fun getTransactionCount(): Int

    @Query("SELECT COUNT(*) FROM rental_transactions WHERE customerId = :customerId AND is_deleted = 0")
    suspend fun getTransactionCountForCustomer(customerId: Long): Int

    @Query("SELECT COUNT(*) FROM rental_transactions WHERE unitId = :unitId AND is_deleted = 0")
    suspend fun getTransactionCountForUnit(unitId: Long): Int

    @Query("SELECT * FROM rental_transactions WHERE sync_status IN (:statuses) ORDER BY updated_at ASC, id ASC")
    suspend fun getTransactionsBySyncStatuses(statuses: List<String>): List<RentalTransactionEntity>

    @Query("UPDATE rental_transactions SET is_deleted = 1, sync_status = :syncStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDeleteTransaction(id: Long, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "PENDING_DELETE")

    @Query("UPDATE rental_transactions SET sync_status = :syncStatus, drive_id = :driveId WHERE id = :id")
    suspend fun updateTransactionSyncAndDriveId(id: Long, syncStatus: String, driveId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: RentalTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<RentalTransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: RentalTransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: RentalTransactionEntity)

    @Query("DELETE FROM rental_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM rental_transactions")
    suspend fun deleteAllTransactions()
}
