package com.example.loyaltyapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import com.example.loyaltyapp.data.local.entity.RegistrationSyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerRegistrationDao {
    /** IGNORE on the unique idempotencyKey index — retries can never duplicate a request. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(registration: CustomerRegistrationEntity): Long

    @Update
    suspend fun update(registration: CustomerRegistrationEntity)

    @Query("SELECT * FROM customer_registrations WHERE localId = :localId")
    suspend fun getById(localId: String): CustomerRegistrationEntity?

    @Query("SELECT * FROM customer_registrations WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY capturedAtMillis ASC")
    suspend fun getPending(): List<CustomerRegistrationEntity>

    @Query("SELECT * FROM customer_registrations WHERE syncStatus = 'SUBMITTED' ORDER BY capturedAtMillis ASC")
    suspend fun getSubmitted(): List<CustomerRegistrationEntity>

    @Query("SELECT * FROM customer_registrations ORDER BY capturedAtMillis DESC")
    fun observeAll(): Flow<List<CustomerRegistrationEntity>>

    @Query("SELECT COUNT(*) FROM customer_registrations WHERE syncStatus IN ('PENDING', 'SYNCING', 'FAILED')")
    fun observePendingCount(): Flow<Int>
}
