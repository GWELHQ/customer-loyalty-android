package com.example.loyaltyapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.loyaltyapp.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stations: List<StationEntity>)

    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getById(id: String): StationEntity?

    @Query("SELECT * FROM stations")
    fun observeAll(): Flow<List<StationEntity>>
}
