package com.example.loyaltyapp.core.cashback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class CashbackCalculatorTest {

    @Test
    fun `10point34 litres earns 20 shillings at default rate`() {
        // amount chosen so amount / price = 10.34 L exactly
        val price = BigDecimal("100")
        val amount = BigDecimal("1034.00")
        val result = CashbackCalculator.calculate(amount, price)

        assertEquals(0, BigDecimal("10.34").compareTo(result.litres))
        assertEquals(0, BigDecimal("10").compareTo(result.wholeLitres))
        assertEquals(0, BigDecimal("20").compareTo(result.cashback))
    }

    @Test
    fun `20point67 litres earns 40 shillings at default rate`() {
        val price = BigDecimal("100")
        val amount = BigDecimal("2067.00")
        val result = CashbackCalculator.calculate(amount, price)

        assertEquals(0, BigDecimal("20.67").compareTo(result.litres))
        assertEquals(0, BigDecimal("20").compareTo(result.wholeLitres))
        assertEquals(0, BigDecimal("40").compareTo(result.cashback))
    }

    @Test
    fun `litres are floored not rounded at the whole-litre boundary`() {
        // 9.999... litres must floor to 9, not round to 10
        val price = BigDecimal("100")
        val amount = BigDecimal("999.99")
        val result = CashbackCalculator.calculate(amount, price)

        assertEquals(0, BigDecimal("9").compareTo(result.wholeLitres))
        assertEquals(0, BigDecimal("18").compareTo(result.cashback))
    }

    @Test
    fun `special cashback rate overrides default rate`() {
        val price = BigDecimal("100")
        val amount = BigDecimal("1000.00")
        val specialRate = BigDecimal("5")
        val result = CashbackCalculator.calculate(amount, price, specialRate)

        assertEquals(0, BigDecimal("10").compareTo(result.wholeLitres))
        assertEquals(0, BigDecimal("50").compareTo(result.cashback))
    }

    @Test
    fun `zero amount yields zero litres and zero cashback`() {
        val result = CashbackCalculator.calculate(BigDecimal.ZERO, BigDecimal("100"))
        assertEquals(0, BigDecimal.ZERO.compareTo(result.wholeLitres))
        assertEquals(0, BigDecimal.ZERO.compareTo(result.cashback))
    }

    @Test
    fun `negative amount is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CashbackCalculator.calculate(BigDecimal("-1"), BigDecimal("100"))
        }
    }

    @Test
    fun `non-positive price is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CashbackCalculator.calculate(BigDecimal("100"), BigDecimal.ZERO)
        }
    }

    @Test
    fun `less than one litre earns zero cashback`() {
        val result = CashbackCalculator.calculate(BigDecimal("50"), BigDecimal("100"))
        assertEquals(0, BigDecimal.ZERO.compareTo(result.wholeLitres))
        assertEquals(0, BigDecimal.ZERO.compareTo(result.cashback))
    }
}
