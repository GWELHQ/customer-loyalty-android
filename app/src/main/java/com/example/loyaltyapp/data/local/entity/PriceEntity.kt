package com.example.loyaltyapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * Cached currently-active price-per-litre for a product (the server always sends "the current
 * price", not a history — one row per product, overwritten on every bootstrap/refresh). The
 * attendant app never sets or edits prices; the server also always recalculates authoritatively
 * at submit/sync time, so this is preview data only.
 */
@Entity(tableName = "prices")
data class PriceEntity(
    @PrimaryKey val product: Product,
    val pricePerLitre: BigDecimal,
    val effectiveFrom: Long,
    val fetchedAtMillis: Long
)
