package com.example.loyaltyapp.ui.sale

import com.example.loyaltyapp.data.local.entity.CustomerEntity
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import com.example.loyaltyapp.data.local.entity.PriceEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.SaleEntity

enum class SaleScreen { LOOKUP, CREATE, BLOCKED, ENTRY, REVIEW, SUCCESS }

data class SaleUiState(
    val screen: SaleScreen = SaleScreen.LOOKUP,
    val stationId: String = "",
    val stationName: String = "",
    val attendantId: String = "",
    val attendantName: String = "",

    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,

    val queryDigits: String = "",
    val matches: List<CustomerEntity> = emptyList(),
    val searchedEnough: Boolean = false,
    /** True once a full 9-digit number has been checked against the live server and found nothing. */
    val confirmedNotFound: Boolean = false,

    val newCustomerName: String = "",
    val createError: String? = null,
    /** True while the current CREATE/ENTRY/REVIEW/SUCCESS flow is a pending-approval registration, not a sale. */
    val isNewCustomerRegistration: Boolean = false,

    val customer: CustomerEntity? = null,
    val product: Product? = null,
    val amountDigits: String = "",

    val prices: Map<Product, PriceEntity> = emptyMap(),
    val blockedNoPrice: Boolean = false,

    val isSubmitting: Boolean = false,
    val lastSale: SaleEntity? = null,
    val lastRegistration: CustomerRegistrationEntity? = null,
    val lastSaleWasOffline: Boolean = false
) {
    /**
     * [queryDigits] as typed may or may not carry a leading 0 (07…/01… vs 7…/1…) — this strips
     * it to the 9-digit national-significant-number form for *matching* (local cache fragment
     * search, exact remote lookup, "is this number complete" checks). Never use this for
     * display: showing the attendant fewer digits than they actually typed reads as "my first 0
     * didn't register," inviting a duplicate keystroke — the UI always echoes [queryDigits] as-is.
     */
    val nationalQueryDigits: String
        get() = com.example.loyaltyapp.core.phone.PhoneNumber.nationalDigits(queryDigits)

    /** 10 once a leading 0 has been typed (07…/01…), else 9 (7…/1…) — the on-screen keypad cap. */
    val maxQueryDigits: Int
        get() = if (queryDigits.startsWith("0")) 10 else 9

    val amountPaid: java.math.BigDecimal
        get() = if (amountDigits.isEmpty()) java.math.BigDecimal.ZERO else java.math.BigDecimal(amountDigits)

    val selectedPrice: PriceEntity?
        get() = product?.let { prices[it] }

    val calculation: com.example.loyaltyapp.core.cashback.SaleCalculation?
        get() {
            val price = selectedPrice ?: return null
            if (amountPaid <= java.math.BigDecimal.ZERO) return null
            val rate = customer?.specialRateKesPerLitre
                ?: com.example.loyaltyapp.core.cashback.CashbackCalculator.DEFAULT_CASHBACK_RATE_PER_LITRE
            return com.example.loyaltyapp.core.cashback.CashbackCalculator.calculate(amountPaid, price.pricePerLitre, rate)
        }

    val readyForReview: Boolean
        get() = (customer != null || isNewCustomerRegistration) && product != null &&
            amountPaid > java.math.BigDecimal.ZERO && !blockedNoPrice

    val entryBlockReason: String?
        get() = when {
            product == null -> "Choose Petrol or Diesel to continue."
            amountPaid <= java.math.BigDecimal.ZERO -> "Enter the amount the customer paid."
            else -> null
        }
}
