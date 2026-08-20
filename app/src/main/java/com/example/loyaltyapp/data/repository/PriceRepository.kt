package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.data.local.dao.PriceDao
import com.example.loyaltyapp.data.local.entity.PriceEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.remote.api.MobileApi
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceRepository @Inject constructor(
    private val priceDao: PriceDao,
    private val mobileApi: MobileApi,
    private val connectivityObserver: ConnectivityObserver
) {
    fun observePrices(): Flow<List<PriceEntity>> = priceDao.observeAll()

    suspend fun getCachedPrice(product: Product): PriceEntity? = priceDao.getPrice(product)

    /** True when there is no usable cached price and the app cannot reach the server. */
    suspend fun isBlockedForSale(): Boolean {
        val hasCache = priceDao.getPrice(Product.PMS) != null || priceDao.getPrice(Product.AGO) != null
        return !hasCache && !connectivityObserver.currentlyOnline()
    }

    /** Standalone refresh of just prices (bootstrap also caches these; use this mid-shift). */
    suspend fun refreshFromRemote() {
        if (!connectivityObserver.currentlyOnline()) return
        try {
            val remote = mobileApi.currentPrices()
            if (remote.isNotEmpty()) {
                val now = System.currentTimeMillis()
                priceDao.upsertAll(
                    remote.values.map {
                        PriceEntity(
                            product = Product.valueOf(it.product),
                            pricePerLitre = java.math.BigDecimal(it.pricePerLitre.toString()),
                            effectiveFrom = runCatching { Instant.parse(it.effectiveFrom).toEpochMilli() }.getOrDefault(now),
                            fetchedAtMillis = now
                        )
                    }
                )
            }
        } catch (_: Exception) {
            // Keep whatever is cached; the UI surfaces staleness via fetchedAtMillis.
        }
    }
}
