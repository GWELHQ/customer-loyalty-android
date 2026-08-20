package com.example.loyaltyapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val active: Boolean = true
)
