package com.example.loyaltyapp.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formatting helpers for KES amounts and litre quantities, using tabular-numeral-friendly output. */
object Money {

    private fun kesFormatter(): DecimalFormat {
        val symbols = DecimalFormatSymbols(Locale.US)
        return DecimalFormat("#,##0", symbols)
    }

    private fun litreFormatter(): DecimalFormat {
        val symbols = DecimalFormatSymbols(Locale.US)
        return DecimalFormat("#,##0.00", symbols)
    }

    /** Formats a whole-shilling amount, e.g. "KES 2,450". */
    fun formatKes(amount: BigDecimal): String {
        val whole = amount.setScale(0, RoundingMode.HALF_UP)
        return "KES ${kesFormatter().format(whole)}"
    }

    fun formatKesPlain(amount: BigDecimal): String {
        val whole = amount.setScale(0, RoundingMode.HALF_UP)
        return kesFormatter().format(whole)
    }

    /** Formats a litre quantity, e.g. "20.67 L". */
    fun formatLitres(litres: BigDecimal): String {
        return "${litreFormatter().format(litres.setScale(2, RoundingMode.HALF_UP))} L"
    }

    /** Formats a price per litre, e.g. "KES 213.69/L". */
    fun formatPricePerLitre(price: BigDecimal): String {
        return "KES ${litreFormatter().format(price.setScale(2, RoundingMode.HALF_UP))}/L"
    }
}
