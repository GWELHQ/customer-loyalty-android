package com.example.loyaltyapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * A locally-queued "new customer + first sale" request. Submitting this does **not** create a
 * real customer or sale — it's a pending-approval request a Station Supervisor/RTSM/Admin must
 * approve from the web app. [syncStatus] reaching [RegistrationSyncStatus.SUBMITTED] means the
 * server accepted the *request*, not that a sale exists yet — the UI must keep saying "pending
 * approval" through that transition, never "recorded"/"synced".
 *
 * There is no bulk endpoint for registrations (unlike sales' `/mobile/sync`), so the sync path
 * retries these one at a time.
 */
@Entity(
    tableName = "customer_registrations",
    indices = [Index(value = ["idempotencyKey"], unique = true)]
)
data class CustomerRegistrationEntity(
    @PrimaryKey val localId: String,
    val idempotencyKey: String,
    val serverRequestId: String?,

    /** Blank for rows queued before this column existed (see LoyaltyDatabase MIGRATION_4_5) — retryAllPending() falls back to the old implicit-session behavior for those. */
    val attendantId: String,

    val customerFullName: String,
    val customerPhoneNumber: String,
    val product: Product,
    val amountPaid: BigDecimal,

    val capturedAtMillis: Long,
    val capturedOffline: Boolean,

    val syncStatus: RegistrationSyncStatus,
    val syncAttempts: Int = 0,
    val lastSyncErrorMessage: String? = null
)
