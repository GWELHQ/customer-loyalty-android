package com.example.loyaltyapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.loyaltyapp.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(customers: List<CustomerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findByPhone(phoneNumber: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phoneNumber LIKE '%' || :digits || '%' ORDER BY updatedAtMillis DESC LIMIT 20")
    fun searchByPhoneFragment(digits: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY updatedAtMillis DESC LIMIT :limit")
    fun recent(limit: Int = 3): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: String): CustomerEntity?

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int
}
