package com.example.loyaltyapp.core.cashback

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Result of a cashback calculation. All fields are exact snapshot values that should be
 * persisted verbatim on a sale record so historical calculations never change even if the
 * live price or rate changes later.
 */
data class SaleCalculation(
    val litres: BigDecimal,
    val wholeLitres: BigDecimal,
    val cashback: BigDecimal,
    val pricePerLitre: BigDecimal,
    val cashbackRatePerLitre: BigDecimal
)

/**
 * The cashback formula is non-negotiable:
 *
 *   litres = amountPaid / activePricePerLitre
 *   wholeLitres = floor(litres)
 *   cashback = wholeLitres * cashbackRate
 *
 * Example: 10.34 L earns KES 20 (at KES 2/L). Example: 20.67 L earns KES 40.
 */
object CashbackCalculator {

    val DEFAULT_CASHBACK_RATE_PER_LITRE: BigDecimal = BigDecimal("2")

    /** Litre precision used for display/intermediate math before flooring to a whole litre. */
    private const val LITRE_SCALE = 4

    fun calculate(
        amountPaidKes: BigDecimal,
        pricePerLitre: BigDecimal,
        cashbackRatePerLitre: BigDecimal = DEFAULT_CASHBACK_RATE_PER_LITRE
    ): SaleCalculation {
        require(pricePerLitre > BigDecimal.ZERO) { "pricePerLitre must be positive" }
        require(amountPaidKes >= BigDecimal.ZERO) { "amountPaidKes cannot be negative" }

        val litres = amountPaidKes.divide(pricePerLitre, LITRE_SCALE, RoundingMode.HALF_UP)
        val wholeLitres = litres.setScale(0, RoundingMode.FLOOR)
        val cashback = wholeLitres.multiply(cashbackRatePerLitre)

        return SaleCalculation(
            litres = litres,
            wholeLitres = wholeLitres,
            cashback = cashback,
            pricePerLitre = pricePerLitre,
            cashbackRatePerLitre = cashbackRatePerLitre
        )
    }
}
