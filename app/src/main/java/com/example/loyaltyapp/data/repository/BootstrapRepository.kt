package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.result.AppResult
import com.example.loyaltyapp.core.session.SessionManager
import com.example.loyaltyapp.data.local.dao.PriceDao
import com.example.loyaltyapp.data.local.dao.StationDao
import com.example.loyaltyapp.data.local.entity.PriceEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.StationEntity
import com.example.loyaltyapp.data.remote.NetworkErrors
import com.example.loyaltyapp.data.remote.api.MobileApi
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls `GET /mobile/bootstrap` once after login (and periodically on resume) to cache
 * station + current prices for offline use, per the handover's offline-first design.
 */
@Singleton
class BootstrapRepository @Inject constructor(
    private val mobileApi: MobileApi,
    private val stationDao: StationDao,
    private val priceDao: PriceDao,
    private val sessionManager: SessionManager
) {
    suspend fun refresh(): AppResult<Unit> {
        return try {
            val response = mobileApi.bootstrap()
            stationDao.upsertAll(
                listOf(
                    StationEntity(
                        id = response.station.id,
                        code = response.station.code,
                        name = response.station.name,
                        active = response.station.active
                    )
                )
            )
            val now = System.currentTimeMillis()
            priceDao.upsertAll(
                response.prices.values.map {
                    PriceEntity(
                        product = Product.valueOf(it.product),
                        pricePerLitre = java.math.BigDecimal(it.pricePerLitre.toString()),
                        effectiveFrom = runCatching { Instant.parse(it.effectiveFrom).toEpochMilli() }.getOrDefault(now),
                        fetchedAtMillis = now
                    )
                }
            )
            sessionManager.saveConfigVersion(response.configVersion)
            AppResult.Success(Unit)
        } catch (e: HttpException) {
            AppResult.Failure(NetworkErrors.messageFor(e, "Could not load station data."), e)
        } catch (e: IOException) {
            AppResult.Failure("Could not reach the office to load station data.", e)
        }
    }
}
