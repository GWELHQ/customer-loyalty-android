package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.cashback.CashbackCalculator
import com.example.loyaltyapp.core.cashback.SaleCalculation
import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.data.local.dao.SaleDao
import com.example.loyaltyapp.data.local.entity.CustomerEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.local.entity.SmsStatus
import com.example.loyaltyapp.data.local.entity.SyncStatus
import com.example.loyaltyapp.data.remote.NetworkErrors
import com.example.loyaltyapp.data.remote.api.MobileApi
import com.example.loyaltyapp.data.remote.dto.SaleRequestDto
import com.example.loyaltyapp.data.remote.dto.SyncRequestDto
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NewSaleInput(
    val stationId: String,
    val stationName: String,
    val attendantId: String,
    val attendantName: String,
    val customer: CustomerEntity,
    val product: Product,
    val amountPaidKes: BigDecimal,
    val pricePerLitre: BigDecimal
)

/** Max rows the backend accepts per `/mobile/sync` call — larger local queues batch across multiple calls. */
private const val SYNC_BATCH_SIZE = 500

@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val mobileApi: MobileApi,
    private val connectivityObserver: ConnectivityObserver
) {

    fun calculationFor(input: NewSaleInput): SaleCalculation {
        val rate = input.customer.specialRateKesPerLitre ?: CashbackCalculator.DEFAULT_CASHBACK_RATE_PER_LITRE
        return CashbackCalculator.calculate(input.amountPaidKes, input.pricePerLitre, rate)
    }

    /**
     * Persists the sale locally first (source of truth), then makes a best-effort immediate
     * sync attempt if online via the single-sale endpoint. The record is never lost if that
     * attempt fails — it just stays PENDING/FAILED for the background worker's batched
     * `/mobile/sync` or a manual retry to pick up later.
     */
    suspend fun recordSale(input: NewSaleInput): SaleEntity {
        val calc = calculationFor(input)
        val wasOnlineAtCapture = connectivityObserver.currentlyOnline()
        val localId = UUID.randomUUID().toString()

        val entity = SaleEntity(
            localSaleId = localId,
            idempotencyKey = localId,
            serverSaleRef = null,
            stationId = input.stationId,
            stationName = input.stationName,
            attendantId = input.attendantId,
            attendantName = input.attendantName,
            customerId = input.customer.id,
            customerName = input.customer.fullName,
            customerPhoneE164 = input.customer.phoneNumber,
            product = input.product,
            amountPaidKes = input.amountPaidKes,
            pricePerLitreSnapshot = calc.pricePerLitre,
            cashbackRatePerLitreSnapshot = calc.cashbackRatePerLitre,
            isSpecialRateSnapshot = input.customer.specialRateKesPerLitre != null,
            litres = calc.litres,
            wholeLitres = calc.wholeLitres,
            cashbackKes = calc.cashback,
            capturedAtMillis = System.currentTimeMillis(),
            capturedOffline = !wasOnlineAtCapture,
            syncStatus = SyncStatus.PENDING,
            smsStatus = SmsStatus.PENDING
        )
        saleDao.insert(entity)

        if (wasOnlineAtCapture) {
            attemptSync(entity)
        }
        return saleDao.getById(localId) ?: entity
    }

    /** Submits one sale via the online-path `/mobile/sales` endpoint; safe to call repeatedly (idempotency-keyed). */
    suspend fun attemptSync(sale: SaleEntity): SaleEntity {
        if (!connectivityObserver.currentlyOnline()) return sale

        saleDao.update(sale.copy(syncStatus = SyncStatus.SYNCING))
        return try {
            val response = mobileApi.submitSale(sale.toRequestDto())
            val updated = sale.copy(
                syncStatus = SyncStatus.SYNCED,
                serverSaleRef = response.id,
                smsStatus = parseSmsStatus(response.smsStatus),
                lastSyncErrorMessage = null,
                syncAttempts = sale.syncAttempts + 1
            )
            saleDao.update(updated)
            updated
        } catch (e: HttpException) {
            val message = NetworkErrors.messageFor(
                e,
                fallback = "The office found a problem with this sale. See your supervisor."
            )
            val updated = sale.copy(
                syncStatus = SyncStatus.CONFLICT,
                lastSyncErrorMessage = message,
                syncAttempts = sale.syncAttempts + 1
            )
            saleDao.update(updated)
            updated
        } catch (e: Exception) {
            val updated = sale.copy(
                syncStatus = SyncStatus.FAILED,
                lastSyncErrorMessage = "Could not reach the office. It will try again automatically, or tap Sync now.",
                syncAttempts = sale.syncAttempts + 1
            )
            saleDao.update(updated)
            updated
        }
    }

    /**
     * Flushes every PENDING/FAILED sale via the batched `/mobile/sync` endpoint (≤500/call),
     * then updates each local row from its own per-item result — a 200 response can still
     * contain individually rejected rows.
     */
    suspend fun syncAllPending(): Int {
        if (!connectivityObserver.currentlyOnline()) return 0
        val pending = saleDao.getByStatus(SyncStatus.PENDING) + saleDao.getByStatus(SyncStatus.FAILED)
        if (pending.isEmpty()) return 0

        var succeeded = 0
        pending.chunked(SYNC_BATCH_SIZE).forEach { batch ->
            batch.forEach { saleDao.update(it.copy(syncStatus = SyncStatus.SYNCING)) }
            try {
                val response = mobileApi.sync(SyncRequestDto(sales = batch.map { it.toRequestDto() }))
                val byLocalId = batch.associateBy { it.localSaleId }
                val byIdempotencyKey = batch.associateBy { it.idempotencyKey }
                response.results.forEach { result ->
                    val original = result.clientLocalId?.let { byLocalId[it] } ?: byIdempotencyKey[result.idempotencyKey]
                    if (original != null) {
                        val updated = applySyncResult(original, result.result, result.saleId, result.errorReason)
                        saleDao.update(updated)
                        if (updated.syncStatus == SyncStatus.SYNCED || updated.syncStatus == SyncStatus.NEEDS_REVIEW) succeeded++
                    }
                }
            } catch (e: Exception) {
                batch.forEach {
                    saleDao.update(
                        it.copy(
                            syncStatus = SyncStatus.FAILED,
                            lastSyncErrorMessage = "Could not reach the office. It will try again automatically, or tap Sync now.",
                            syncAttempts = it.syncAttempts + 1
                        )
                    )
                }
            }
        }
        return succeeded
    }

    private fun applySyncResult(sale: SaleEntity, result: String, saleId: String?, errorReason: String?): SaleEntity =
        when (result) {
            "accepted", "already_processed" -> sale.copy(
                syncStatus = SyncStatus.SYNCED,
                serverSaleRef = saleId ?: sale.serverSaleRef,
                lastSyncErrorMessage = null,
                syncAttempts = sale.syncAttempts + 1
            )
            "needs_review" -> sale.copy(
                syncStatus = SyncStatus.NEEDS_REVIEW,
                serverSaleRef = saleId ?: sale.serverSaleRef,
                lastSyncErrorMessage = "The price changed while this sale was saved on your phone. It has been recorded — see your supervisor if unsure.",
                syncAttempts = sale.syncAttempts + 1
            )
            "rejected" -> sale.copy(
                syncStatus = SyncStatus.CONFLICT,
                lastSyncErrorMessage = errorReason ?: "The office found a problem with this sale. See your supervisor.",
                syncAttempts = sale.syncAttempts + 1
            )
            else -> sale.copy(
                syncStatus = SyncStatus.FAILED,
                lastSyncErrorMessage = "Unexpected sync result. It will try again automatically.",
                syncAttempts = sale.syncAttempts + 1
            )
        }

    private fun SaleEntity.toRequestDto(): SaleRequestDto = SaleRequestDto(
        customerPhone = customerPhoneE164,
        product = product.name,
        amountPaid = amountPaidKes.toDouble(),
        stationId = stationId,
        saleDate = Instant.ofEpochMilli(capturedAtMillis).toString(),
        idempotencyKey = idempotencyKey,
        clientLocalId = localSaleId,
        claimedPricePerLitre = pricePerLitreSnapshot.toDouble(),
        claimedCashbackEarned = cashbackKes.toDouble()
    )

    private fun parseSmsStatus(raw: String): SmsStatus =
        runCatching { SmsStatus.valueOf(raw.uppercase()) }.getOrDefault(SmsStatus.PENDING)

    fun observeTodayForAttendant(attendantId: String): Flow<List<SaleEntity>> {
        val startOfDay = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return saleDao.observeTodayForAttendant(attendantId, startOfDay)
    }

    fun observeQueue(): Flow<List<SaleEntity>> = saleDao.observeAll()

    fun observePendingCount(): Flow<Int> = saleDao.observePendingCount()
}
