package com.example.loyaltyapp.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.local.entity.SmsStatus
import com.example.loyaltyapp.data.local.entity.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class SaleDaoTest {

    private lateinit var db: LoyaltyDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LoyaltyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sale(idempotencyKey: String) = SaleEntity(
        localSaleId = idempotencyKey,
        idempotencyKey = idempotencyKey,
        serverSaleRef = null,
        stationId = "st-1",
        stationName = "Kisumu 1",
        attendantId = "att-1",
        attendantName = "Achieng Oketch",
        customerId = "cus-1",
        customerName = "Grace Atieno",
        customerPhoneE164 = "+254722481903",
        product = Product.PMS,
        amountPaidKes = BigDecimal("1000"),
        pricePerLitreSnapshot = BigDecimal("100"),
        cashbackRatePerLitreSnapshot = BigDecimal("2"),
        isSpecialRateSnapshot = false,
        litres = BigDecimal("10"),
        wholeLitres = BigDecimal("10"),
        cashbackKes = BigDecimal("20"),
        capturedAtMillis = System.currentTimeMillis(),
        capturedOffline = false,
        syncStatus = SyncStatus.PENDING,
        smsStatus = SmsStatus.PENDING
    )

    @Test
    fun insertingTheSameIdempotencyKeyTwiceIsIgnored() = runBlocking {
        val saleDao = db.saleDao()
        val first = sale("dup-key")
        val secondAttempt = sale("dup-key").copy(localSaleId = "different-local-id")

        saleDao.insert(first)
        saleDao.insert(secondAttempt) // retry after e.g. process death — must not create a duplicate row

        val all = saleDao.getByStatus(SyncStatus.PENDING)
        assertEquals(1, all.size)
        assertEquals(first.localSaleId, all.first().localSaleId)
    }

    @Test
    fun eachDistinctSaleIsStoredSeparately() = runBlocking {
        val saleDao = db.saleDao()
        saleDao.insert(sale("key-a"))
        saleDao.insert(sale("key-b"))

        assertEquals(2, saleDao.getByStatus(SyncStatus.PENDING).size)
    }
}
