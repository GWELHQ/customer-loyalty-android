package com.example.loyaltyapp.core.phone

/**
 * Normalizes Kenyan mobile numbers to a canonical E.164-ish form: +254 7XX XXX XXX
 * (also accepts 01... Safaricom/Airtel ranges since 2020, kept lenient here).
 *
 * Accepts input like "0722481903", "722481903", "+254722481903", "254722481903",
 * with arbitrary spacing.
 */
object PhoneNumber {

    data class NormalizedPhone(
        val e164: String,       // +254722481903
        val displayGrouped: String, // +254 722 481 903
        val nationalSignificant: String // 722481903 (9 digits, no leading 0/254)
    )

    /** Strips everything but digits. */
    fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Strips a leading "0" or "254" so `0722…`, `254722…`, and bare `722…` (also the `01…`/
     * `1…` range) all collapse to the same 9-digit national-significant-number form used for
     * local cache matching, progress counting, and display — a search box has no way to know in
     * advance which form the attendant is about to type, so every partial-entry lookup needs to
     * normalize as it goes, not just on a completed number (see [normalize]).
     */
    fun nationalDigits(raw: String): String {
        val digits = digitsOnly(raw)
        return when {
            digits.startsWith("254") -> digits.removePrefix("254")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
    }

    /**
     * Attempts to normalize [raw] into a canonical Kenyan MSISDN.
     * Returns null if the input cannot be resolved to a valid 9-digit national number
     * starting with a mobile prefix (7 or 1).
     */
    fun normalize(raw: String): NormalizedPhone? {
        var digits = digitsOnly(raw)

        digits = when {
            digits.startsWith("254") -> digits.removePrefix("254")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }

        if (digits.length != 9) return null
        if (digits[0] != '7' && digits[0] != '1') return null

        val e164 = "+254$digits"
        val grouped = "+254 ${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6, 9)}"
        return NormalizedPhone(e164 = e164, displayGrouped = grouped, nationalSignificant = digits)
    }

    /** True if [raw] normalizes to a valid Kenyan mobile number. */
    fun isValid(raw: String): Boolean = normalize(raw) != null

    /**
     * Formats partial (in-progress) digit entry as groups, for live display while typing.
     * Echoes exactly what was typed — including a leading 0, if any — so the attendant always
     * sees their own keystrokes back rather than a silently-shortened number (a 9-digit display
     * for 10 digits typed would read as "my first 0 didn't register," inviting a duplicate 0).
     */
    fun formatPartial(digits: String): String {
        val cleaned = digitsOnly(digits)
        val hasLeadingZero = cleaned.startsWith("0")
        val maxLen = if (hasLeadingZero) 10 else 9
        val d = cleaned.take(maxLen)
        val firstGroupSize = if (hasLeadingZero) 4 else 3
        val parts = buildList {
            if (d.isNotEmpty()) add(d.take(firstGroupSize))
            if (d.length > firstGroupSize) add(d.substring(firstGroupSize, minOf(firstGroupSize + 3, d.length)))
            if (d.length > firstGroupSize + 3) add(d.substring(firstGroupSize + 3, minOf(firstGroupSize + 6, d.length)))
        }
        return parts.joinToString(" ")
    }
}
