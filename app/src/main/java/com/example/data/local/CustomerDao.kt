package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) untuk profil pelanggan (`CustomerEntity`) di Room Database.
 * Menyediakan kueri reaktif menggunakan `Flow<List<CustomerEntity>>` serta operasi
 * penambahan, pembaruan, dan penghapusan asynchronous (`suspend`).
 */
@Dao
interface CustomerDao {

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>): List<Long>

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: Long)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()
}
