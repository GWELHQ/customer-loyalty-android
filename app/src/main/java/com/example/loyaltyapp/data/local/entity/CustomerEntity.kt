package com.example.loyaltyapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * Customers are centralized across all stations and identified primarily by normalized phone
 * number. [specialRateKesPerLitre] is chairman-approved and read-only in this app — nothing in
 * the attendant UI may write to it. [totalCashbackEarned] is fetched/cached (needed for the
 * "special rate" preview) but deliberately never rendered in attendant-facing screens.
 */
@Entity(
    tableName = "customers",
    indices = [Index(value = ["phoneNumber"], unique = true)]
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val fullName: String,
    val homeStationId: String?,
    val specialRateKesPerLitre: BigDecimal?,
    val specialRateEffectiveFrom: Long?,
    val specialRateEffectiveTo: Long?,
    val totalCashbackEarned: BigDecimal,
    val updatedAtMillis: Long
)
