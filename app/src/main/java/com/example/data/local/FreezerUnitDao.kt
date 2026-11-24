package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) untuk unit inventaris freezer (`FreezerUnitEntity`)
 * dan riwayat perawatannya (`FreezerUnitMaintenanceEntity`) di Room Database.
 * Menyediakan kueri reaktif menggunakan `Flow` dan operasi CRUD transaksional.
 */
@Dao
interface FreezerUnitDao {

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 ORDER BY kodeUnit ASC")
    fun getAllUnits(): Flow<List<FreezerUnitEntity>>

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 ORDER BY kodeUnit ASC")
    suspend fun getAllUnitsOnce(): List<FreezerUnitEntity>

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

    @Query("SELECT * FROM freezer_units WHERE statusUnit = :status AND is_deleted = 0 ORDER BY kodeUnit ASC")
    fun getUnitsByStatus(status: String): Flow<List<FreezerUnitEntity>>

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 AND LOWER(statusUnit) IN ('tersedia', 'available') ORDER BY kodeUnit ASC")
    fun getAvailableUnits(): Flow<List<FreezerUnitEntity>>

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 AND LOWER(statusUnit) IN ('disewa', 'sedang disewa', 'dalam pengiriman', 'dipesan', 'rented') ORDER BY kodeUnit ASC")
    fun getRentedUnits(): Flow<List<FreezerUnitEntity>>

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 AND LOWER(statusUnit) IN ('maintenance', 'dalam perbaikan', 'perawatan', 'rusak', 'perbaikan') ORDER BY kodeUnit ASC")
    fun getMaintenanceUnits(): Flow<List<FreezerUnitEntity>>

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 AND LOWER(merek) = LOWER(:brand) ORDER BY kodeUnit ASC")
    fun getUnitsByBrand(brand: String): Flow<List<FreezerUnitEntity>>

    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 AND LOWER(model) = LOWER(:model) ORDER BY kodeUnit ASC")
    fun getUnitsByModel(model: String): Flow<List<FreezerUnitEntity>>

    @Query(
        """
        SELECT * FROM freezer_units
        WHERE is_deleted = 0 AND (
            kodeUnit LIKE '%' || :query || '%'
            OR namaUnit LIKE '%' || :query || '%'
            OR merek LIKE '%' || :query || '%'
            OR model LIKE '%' || :query || '%'
            OR nomorSeri LIKE '%' || :query || '%'
        )
        ORDER BY kodeUnit ASC
        """
    )
    fun searchUnits(query: String): Flow<List<FreezerUnitEntity>>

    @Query("SELECT * FROM freezer_units WHERE jenisUnit = :jenis AND is_deleted = 0 ORDER BY kodeUnit ASC")
    fun getUnitsByJenis(jenis: String): Flow<List<FreezerUnitEntity>>

    @Query("SELECT COUNT(*) FROM freezer_units WHERE is_deleted = 0")
    suspend fun getUnitCount(): Int

    @Query("SELECT COUNT(*) FROM freezer_units WHERE statusUnit = :status AND is_deleted = 0")
    suspend fun getUnitCountByStatus(status: String): Int

    @Query("SELECT * FROM freezer_units WHERE sync_status IN (:statuses) ORDER BY updated_at ASC, id ASC")
    suspend fun getUnitsBySyncStatuses(statuses: List<String>): List<FreezerUnitEntity>

    @Query("UPDATE freezer_units SET is_deleted = 1, sync_status = :syncStatus, updated_at = :updatedAt WHERE id = :unitId")
    suspend fun softDeleteUnit(unitId: Long, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "PENDING_DELETE")

    @Query("UPDATE freezer_units SET sync_status = :syncStatus, drive_id = :driveId WHERE id = :unitId")
    suspend fun updateUnitSyncAndDriveId(unitId: Long, syncStatus: String, driveId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: FreezerUnitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<FreezerUnitEntity>): List<Long>

    @Update
    suspend fun updateUnit(unit: FreezerUnitEntity)

    @Query("UPDATE freezer_units SET statusUnit = :status, version = version + 1, updated_at = :updatedAt WHERE id = :unitId")
    suspend fun updateUnitStatus(unitId: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE freezer_units SET maintenanceHistory = :maintenanceHistory, version = version + 1, updated_at = :updatedAt WHERE id = :unitId")
    suspend fun updateMaintenanceHistorySummary(unitId: Long, maintenanceHistory: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteUnit(unit: FreezerUnitEntity)

    @Query("DELETE FROM freezer_units WHERE id = :id")
    suspend fun deleteUnitById(id: Long)

    @Query("DELETE FROM freezer_units")
    suspend fun deleteAllUnits()

    // --- UNIT WITH MAINTENANCE HISTORY (@Relation) ---
    @Transaction
    @Query("SELECT * FROM freezer_units WHERE is_deleted = 0 ORDER BY kodeUnit ASC")
    fun getAllUnitsWithMaintenanceHistory(): Flow<List<FreezerUnitWithMaintenanceHistory>>

    @Transaction
    @Query("SELECT * FROM freezer_units WHERE id = :unitId AND is_deleted = 0 LIMIT 1")
    fun getUnitWithMaintenanceHistoryById(unitId: Long): Flow<FreezerUnitWithMaintenanceHistory?>

    @Transaction
    @Query("SELECT * FROM freezer_units WHERE id = :unitId AND is_deleted = 0 LIMIT 1")
    suspend fun getUnitWithMaintenanceHistoryOnce(unitId: Long): FreezerUnitWithMaintenanceHistory?

    // --- MAINTENANCE HISTORY RECORDS ---
    @Query("SELECT * FROM freezer_unit_maintenance WHERE unit_id = :unitId ORDER BY maintenance_date DESC, created_at_millis DESC, id DESC")
    fun getMaintenanceHistoryForUnit(unitId: Long): Flow<List<FreezerUnitMaintenanceEntity>>

    @Query("SELECT * FROM freezer_unit_maintenance WHERE unit_id = :unitId ORDER BY maintenance_date DESC, created_at_millis DESC, id DESC")
    suspend fun getMaintenanceHistoryForUnitOnce(unitId: Long): List<FreezerUnitMaintenanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceRecord(record: FreezerUnitMaintenanceEntity): Long

    @Update
    suspend fun updateMaintenanceRecord(record: FreezerUnitMaintenanceEntity)

    @Delete
    suspend fun deleteMaintenanceRecord(record: FreezerUnitMaintenanceEntity)

    @Query("DELETE FROM freezer_unit_maintenance WHERE unit_id = :unitId")
    suspend fun deleteMaintenanceHistoryForUnit(unitId: Long)

    @Query("DELETE FROM freezer_unit_maintenance")
    suspend fun deleteAllMaintenanceRecords()

    @Transaction
    suspend fun recordMaintenanceAndUpdateUnit(
        unitId: Long,
        maintenanceDate: String,
        maintenanceType: String,
        description: String = "",
        technicianName: String = "",
        cost: Long = 0L,
        statusAfterMaintenance: String = ""
    ): Long {
        val unit = getUnitById(unitId) ?: return -1L
        val record = FreezerUnitMaintenanceEntity(
            unitId = unitId,
            unitCode = unit.kodeUnit,
            maintenanceDate = maintenanceDate,
            maintenanceType = maintenanceType,
            description = description,
            technicianName = technicianName,
            cost = cost,
            statusAfterMaintenance = statusAfterMaintenance.ifBlank { unit.statusUnit }
        )
        val recordId = insertMaintenanceRecord(record)
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
        val nextStatus = statusAfterMaintenance.ifBlank { unit.statusUnit }
        val nowMs = System.currentTimeMillis()
        updateUnit(
            unit.copy(
                statusUnit = nextStatus,
                maintenanceHistory = updatedSummary,
                version = unit.version + 1L,
                updatedAtMillis = nowMs
            )
        )
        return recordId
    }
}
