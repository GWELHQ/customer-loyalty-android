package com.example.loyaltyapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * A single recorded sale. Price/rate/litre values are snapshots captured at submit time and
 * must never be recomputed from live prices later — history must stay stable even if prices
 * or cashback rates change afterward.
 *
 * [localSaleId] doubles as the idempotency key sent to the backend, so retried sync attempts
 * (including WorkManager retries after process death) cannot create duplicate sales.
 */
@Entity(
    tableName = "sales",
    indices = [Index(value = ["idempotencyKey"], unique = true)]
)
data class SaleEntity(
    @PrimaryKey val localSaleId: String,
    val idempotencyKey: String,
    val serverSaleRef: String?,

    val stationId: String,
    val stationName: String,
    val attendantId: String,
    val attendantName: String,

    val customerId: String,
    val customerName: String,
    val customerPhoneE164: String,

    val product: Product,
    val amountPaidKes: BigDecimal,
    val pricePerLitreSnapshot: BigDecimal,
    val cashbackRatePerLitreSnapshot: BigDecimal,
    val isSpecialRateSnapshot: Boolean,
    val litres: BigDecimal,
    val wholeLitres: BigDecimal,
    val cashbackKes: BigDecimal,

    val capturedAtMillis: Long,
    val capturedOffline: Boolean,

    val syncStatus: SyncStatus,
    val syncAttempts: Int = 0,
    val lastSyncErrorMessage: String? = null,

    val smsStatus: SmsStatus,

    // Carried through to the sale request so a queued/retried sale still submits the plate check
    // it was captured with. Optional — null when no photo was taken. The backend re-validates it
    // (same customer, <60min old) and silently ignores it if stale by the time this syncs.
    val plateCheckId: String? = null
)
