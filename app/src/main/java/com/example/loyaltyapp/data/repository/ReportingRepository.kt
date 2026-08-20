package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.result.AppResult
import com.example.loyaltyapp.data.remote.NetworkErrors
import com.example.loyaltyapp.data.remote.api.MobileApi
import com.example.loyaltyapp.data.remote.dto.DailySummaryRowDto
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online-only end-of-shift reads: `/mobile/daily-summary` (reconciliation rows) and
 * `/mobile/sales/mine` (server's record of the attendant's own sales, for reconciling against
 * local state independent of what the local DB thinks happened).
 */
@Singleton
class ReportingRepository @Inject constructor(
    private val mobileApi: MobileApi
) {
    suspend fun fetchDailySummary(date: String? = null): AppResult<List<DailySummaryRowDto>> = try {
        AppResult.Success(mobileApi.dailySummary(date).reconciliation)
    } catch (e: HttpException) {
        AppResult.Failure(NetworkErrors.messageFor(e, "Could not load today's summary."), e)
    } catch (e: IOException) {
        AppResult.Failure("Could not reach the office to load today's summary.", e)
    }

    suspend fun fetchSalesMineCount(date: String? = null): AppResult<Int> = try {
        AppResult.Success(mobileApi.salesMine(date).total)
    } catch (e: HttpException) {
        AppResult.Failure(NetworkErrors.messageFor(e, "Could not reconcile with the office."), e)
    } catch (e: IOException) {
        AppResult.Failure("Could not reach the office to reconcile.", e)
    }
}
