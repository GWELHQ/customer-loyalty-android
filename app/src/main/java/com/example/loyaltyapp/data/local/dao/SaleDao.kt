package com.example.loyaltyapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    /** IGNORE (not REPLACE) on the unique idempotencyKey index — retries can never duplicate a sale. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sales: List<SaleEntity>)

    @Update
    suspend fun update(sale: SaleEntity)

    @Query("SELECT * FROM sales WHERE localSaleId = :localSaleId")
    suspend fun getById(localSaleId: String): SaleEntity?

    @Query("SELECT * FROM sales WHERE localSaleId = :localSaleId")
    fun observeById(localSaleId: String): Flow<SaleEntity?>

    @Query("SELECT * FROM sales WHERE idempotencyKey = :idempotencyKey")
    suspend fun getByIdempotencyKey(idempotencyKey: String): SaleEntity?

    @Query("SELECT * FROM sales WHERE attendantId = :attendantId AND capturedAtMillis >= :startOfDayMillis ORDER BY capturedAtMillis DESC")
    fun observeTodayForAttendant(attendantId: String, startOfDayMillis: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE syncStatus IN (:statuses) ORDER BY capturedAtMillis DESC")
    fun observeByStatuses(statuses: List<SyncStatus>): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales ORDER BY capturedAtMillis DESC")
    fun observeAll(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE syncStatus = :status ORDER BY capturedAtMillis ASC")
    suspend fun getByStatus(status: SyncStatus): List<SaleEntity>

    @Query("SELECT COUNT(*) FROM sales WHERE syncStatus IN ('PENDING', 'SYNCING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    /** Used by AttendantCredentialGarbageCollector to decide whether an attendant's retained refresh token is still needed. */
    @Query("SELECT COUNT(*) FROM sales WHERE attendantId = :attendantId AND syncStatus IN ('PENDING', 'FAILED')")
    suspend fun countPendingOrFailedForAttendant(attendantId: String): Int
}
