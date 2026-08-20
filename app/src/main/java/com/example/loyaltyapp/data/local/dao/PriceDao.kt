package com.example.loyaltyapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.loyaltyapp.data.local.entity.PriceEntity
import com.example.loyaltyapp.data.local.entity.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(prices: List<PriceEntity>)

    @Query("SELECT * FROM prices WHERE product = :product LIMIT 1")
    suspend fun getPrice(product: Product): PriceEntity?

    @Query("SELECT * FROM prices")
    fun observeAll(): Flow<List<PriceEntity>>
}
