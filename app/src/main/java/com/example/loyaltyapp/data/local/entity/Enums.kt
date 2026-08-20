package com.example.loyaltyapp.data.local.entity

/** Fuel product sold. */
enum class Product { PMS, AGO }

/**
 * Sync lifecycle of a locally captured sale.
 * PENDING: saved locally, waiting to be sent.
 * SYNCING: a sync attempt is currently in flight.
 * SYNCED: the backend accepted the sale cleanly (`accepted` or `already_processed`).
 * NEEDS_REVIEW: the backend recorded the sale but the client's cached price/cashback preview
 *               disagreed with its authoritative calculation — not a failure, just flagged.
 * FAILED: a sync attempt failed with a retryable error (e.g. no network, timeout).
 * CONFLICT: the backend rejected the sale (`rejected`) — needs supervisor attention, retrying
 *           an unchanged payload will reject again.
 */
enum class SyncStatus { PENDING, SYNCING, SYNCED, NEEDS_REVIEW, FAILED, CONFLICT }

/** Delivery status of the customer-facing cashback SMS, tracked independently of sale sync. */
enum class SmsStatus { PENDING, SENT, FAILED, NOT_APPLICABLE }

/** Sync lifecycle of a locally captured new-customer registration request. */
enum class RegistrationSyncStatus { PENDING, SYNCING, SUBMITTED, FAILED }
