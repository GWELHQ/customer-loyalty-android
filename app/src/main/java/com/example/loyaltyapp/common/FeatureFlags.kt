package com.example.loyaltyapp.common

/**
 * Mirrors the backend's `apps/api/src/common/feature-flags.ts` (2026-08-28 handover note):
 * customer NFC scan, badge-tap login, and plate-check OCR are disabled server-side for now
 * (endpoints return 400) — code stays intact here too, just gated off, so flipping these back
 * to `true` is the only change needed when the backend re-enables them.
 */
object FeatureFlags {
    const val CUSTOMER_NFC_SCAN_ENABLED = false
    const val BADGE_LOGIN_ENABLED = false
    const val PLATE_CHECK_ENABLED = false
}
