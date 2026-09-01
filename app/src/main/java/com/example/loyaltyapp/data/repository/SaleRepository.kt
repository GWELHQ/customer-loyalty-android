package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.cashback.CashbackCalculator
import com.example.loyaltyapp.core.cashback.SaleCalculation
import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.core.phone.PhoneNumber
import com.example.loyaltyapp.core.session.AttendantSyncAuthenticator
import com.example.loyaltyapp.core.session.TokenResult
import com.example.loyaltyapp.core.sms.AfricasTalkingSmsSender
import com.example.loyaltyapp.core.sms.SmsSendResult
import com.example.loyaltyapp.data.local.dao.SaleDao
import com.example.loyaltyapp.data.local.entity.CustomerEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.SaleEntity
import com.example.loyaltyapp.data.local.entity.SmsStatus
import com.example.loyaltyapp.data.local.entity.SyncStatus
import com.example.loyaltyapp.data.remote.NetworkErrors
import com.example.loyaltyapp.data.remote.api.MobileApi
import com.example.loyaltyapp.data.remote.dto.SaleRequestDto
import com.example.loyaltyapp.data.remote.dto.SaleResponseDto
import com.example.loyaltyapp.data.remote.dto.SmsStatusReportDto
import com.example.loyaltyapp.data.remote.dto.SyncRequestDto
import com.example.loyaltyapp.data.remote.dto.SyncResponseDto
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
    val pricePerLitre: BigDecimal,
    val plateCheckId: String? = null
)

/** Max rows the backend accepts per `/mobile/sync` call — larger local queues batch across multiple calls. */
private const val SYNC_BATCH_SIZE = 500

@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val mobileApi: MobileApi,
    private val connectivityObserver: ConnectivityObserver,
    private val smsSender: AfricasTalkingSmsSender,
    private val customerRepository: CustomerRepository,
    private val attendantSyncAuthenticator: AttendantSyncAuthenticator
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
            smsStatus = SmsStatus.PENDING,
            plateCheckId = input.plateCheckId
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
            // Only ever attempt once per sale — `sale.smsStatus` is still PENDING the first time
            // this succeeds; a later manual retry of an already-SMSed sale (SyncQueueViewModel)
            // must not text the customer twice.
            val smsStatus = if (sale.smsStatus == SmsStatus.PENDING) {
                sendSaleSmsAndReport(
                    saleId = response.id,
                    phone = response.customerPhoneAtSale,
                    cashbackEarned = response.snapshot.cashbackEarned,
                    monthToDateCashback = response.monthToDateCashback
                ) ?: parseSmsStatus(response.smsStatus)
            } else {
                sale.smsStatus
            }
            val updated = sale.copy(
                syncStatus = SyncStatus.SYNCED,
                serverSaleRef = response.id,
                smsStatus = smsStatus,
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
     * grouped by [SaleEntity.attendantId] and synced **once per attendant, sequentially** — the
     * backend has no per-item attendant field, so a call always attributes its whole batch to
     * whichever token made it (handover doc §5.6). The attendant currently signed into the UI
     * uses their live token via the implicit-auth [MobileApi.sync]; any other attendant (queued
     * offline, then auto-logged-out) gets a token via [AttendantSyncAuthenticator], silently
     * refreshed from their retained refresh token, and syncs via the explicit-header
     * [MobileApi.syncAs] so the interceptor's current-session token is never substituted in by
     * mistake. Each local row is updated from its own per-item result — a 200 response can still
     * contain individually rejected rows. Retained refresh tokens are pruned separately, once
     * both this and registration retries have run for the tick (see
     * [com.example.loyaltyapp.core.session.AttendantCredentialGarbageCollector]) — not here.
     */
    suspend fun syncAllPending(): Int {
        if (!connectivityObserver.currentlyOnline()) return 0
        val pending = saleDao.getByStatus(SyncStatus.PENDING) + saleDao.getByStatus(SyncStatus.FAILED)
        if (pending.isEmpty()) return 0

        var succeeded = 0
        pending.groupBy { it.attendantId }.forEach { (attendantId, attendantSales) ->
            when (val tokenResult = attendantSyncAuthenticator.accessTokenFor(attendantId)) {
                TokenResult.Unavailable, TokenResult.SessionDead -> Unit
                is TokenResult.Available -> {
                    attendantSales.chunked(SYNC_BATCH_SIZE).forEach { batch ->
                        succeeded += syncBatch(batch) {
                            if (tokenResult.isForegroundSession) {
                                mobileApi.sync(SyncRequestDto(sales = batch.map { it.toRequestDto() }))
                            } else {
                                mobileApi.syncAs(
                                    bearer = "Bearer ${tokenResult.accessToken}",
                                    request = SyncRequestDto(sales = batch.map { it.toRequestDto() })
                                )
                            }
                        }
                    }
                }
            }
        }
        return succeeded
    }

    private suspend fun syncBatch(batch: List<SaleEntity>, call: suspend () -> SyncResponseDto): Int {
        batch.forEach { saleDao.update(it.copy(syncStatus = SyncStatus.SYNCING)) }
        var succeeded = 0
        try {
            val response = call()
            val byLocalId = batch.associateBy { it.localSaleId }
            val byIdempotencyKey = batch.associateBy { it.idempotencyKey }
            response.results.forEach { result ->
                val original = result.clientLocalId?.let { byLocalId[it] } ?: byIdempotencyKey[result.idempotencyKey]
                if (original != null) {
                    var updated = applySyncResult(original, result.result, result.saleId, result.errorReason)
                    if (updated.syncStatus == SyncStatus.SYNCED || updated.syncStatus == SyncStatus.NEEDS_REVIEW) {
                        succeeded++
                        // customerPhone/cashbackEarned/monthToDateCashback are only present on
                        // "accepted"/"needs_review" results that actually created a sale —
                        // "already_processed" (and the guard below) keep this a one-shot send.
                        val saleId = result.saleId
                        if (original.smsStatus == SmsStatus.PENDING && saleId != null) {
                            val smsStatus = sendSaleSmsAndReport(
                                saleId = saleId,
                                phone = result.customerPhone,
                                cashbackEarned = result.cashbackEarned,
                                monthToDateCashback = result.monthToDateCashback
                            )
                            if (smsStatus != null) updated = updated.copy(smsStatus = smsStatus)
                        }
                    }
                    saleDao.update(updated)
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
        return succeeded
    }

    /** Used by AttendantCredentialGarbageCollector to decide whether an attendant's retained refresh token is still needed. */
    suspend fun countPendingOrFailedForAttendant(attendantId: String): Int =
        saleDao.countPendingOrFailedForAttendant(attendantId)

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
        claimedCashbackEarned = cashbackKes.toDouble(),
        plateCheckId = plateCheckId
    )

    private fun parseSmsStatus(raw: String): SmsStatus =
        runCatching { SmsStatus.valueOf(raw.uppercase()) }.getOrDefault(SmsStatus.PENDING)

    /**
     * Sends the cashback-confirmation SMS directly (see [AfricasTalkingSmsSender]) and reports
     * the outcome back to the API for the web admin app's visibility — both fire-and-forget:
     * SMS failure never affects the sale, which is already durably recorded server-side by the
     * time this runs, and a failed status report is simply dropped (staff can retry the SMS from
     * the web app independently). Returns null (meaning "don't touch smsStatus locally") when
     * [phone]/[cashbackEarned]/[monthToDateCashback] aren't all present — that's the server's
     * signal that no sale was actually created here, or that this call doesn't carry the fields
     * (e.g. an older backend), so there is nothing to send for.
     */
    private suspend fun sendSaleSmsAndReport(
        saleId: String,
        phone: String?,
        cashbackEarned: Double?,
        monthToDateCashback: Double?
    ): SmsStatus? {
        if (phone == null || cashbackEarned == null || monthToDateCashback == null) return null
        // Nothing to tell the customer about — and nothing to retry later, unlike the null-return
        // cases above, so this is marked NOT_APPLICABLE rather than left PENDING.
        if (cashbackEarned <= 0.0) return SmsStatus.NOT_APPLICABLE

        val message = smsSender.buildMessage(cashbackEarned, monthToDateCashback)
        val result = smsSender.send(phone, message)
        val report = when (result) {
            is SmsSendResult.Success -> SmsStatusReportDto(success = true, providerResponse = result.providerMessageId)
            is SmsSendResult.Failure -> SmsStatusReportDto(success = false, errorReason = result.reason)
        }
        runCatching { mobileApi.reportSmsStatus(saleId, report) }
        return if (result is SmsSendResult.Success) SmsStatus.SENT else SmsStatus.FAILED
    }

    fun observeTodayForAttendant(attendantId: String): Flow<List<SaleEntity>> {
        val startOfDay = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return saleDao.observeTodayForAttendant(attendantId, startOfDay)
    }

    fun observeQueue(): Flow<List<SaleEntity>> = saleDao.observeAll()

    fun observeById(localSaleId: String): Flow<SaleEntity?> = saleDao.observeById(localSaleId)

    fun observePendingCount(): Flow<Int> = saleDao.observePendingCount()

    /**
     * Adopts a sale the server created on our behalf (e.g. a customer-registration request that
     * a supervisor approved) into the local queue, already SYNCED, so it shows up in "Today's
     * sales" the same as one recorded directly on this phone. IGNORE on the unique
     * idempotencyKey index means this is safe to call repeatedly for the same sale.
     */
    suspend fun adoptServerSale(response: SaleResponseDto, customerName: String) {
        saleDao.insert(response.toEntity(customerName))
    }

    /**
     * Pulls today's sales for the signed-in attendant down from the server and adopts any this
     * device doesn't already have locally — the missing half of sync. Sales already flow local
     * device -> server (`attemptSync`/`syncAllPending`); without this, a second device signed
     * into the same attendant account would never see sales recorded on the first one, since each
     * device's Room DB is otherwise only ever written to by that device's own capture/sync calls.
     * Matches on [SaleEntity.idempotencyKey] (globally unique, server-echoed) so this is safe to
     * call repeatedly and never re-adopts a sale this device already has — including one it
     * created itself. Best-effort: silently does nothing if offline or the call fails, since this
     * only ever fills in something already durably recorded server-side.
     */
    suspend fun pullTodayFromServer(): Int {
        if (!connectivityObserver.currentlyOnline()) return 0
        return try {
            val response = mobileApi.salesMine()
            var pulled = 0
            // Same customer often appears on several of the day's sales — cache resolved names
            // within this one pull so a repeat phone number doesn't cost a second network lookup.
            val resolvedNames = mutableMapOf<String, String>()
            response.items.forEach { item ->
                if (saleDao.getByIdempotencyKey(item.idempotencyKey) == null) {
                    val customerName = resolvedNames.getOrPut(item.customerPhoneAtSale) {
                        resolveCustomerName(item.customerPhoneAtSale)
                    }
                    saleDao.insert(item.toEntity(customerName))
                    pulled++
                }
            }
            pulled
        } catch (_: Exception) {
            0
        }
    }

    /** Local cache first, then one authoritative remote lookup — never crashes; falls back to the raw phone number. */
    private suspend fun resolveCustomerName(phoneE164: String): String {
        customerRepository.findByPhone(phoneE164)?.let { return it.fullName }
        val national = PhoneNumber.nationalDigits(phoneE164)
        val remote = customerRepository.searchRemoteExact(national)
        return remote?.firstOrNull { it.phoneNumber == phoneE164 }?.fullName ?: phoneE164
    }

    private fun SaleResponseDto.toEntity(customerName: String): SaleEntity = SaleEntity(
        localSaleId = id,
        idempotencyKey = idempotencyKey,
        serverSaleRef = id,
        stationId = stationId,
        stationName = stationNameAtSale,
        attendantId = attendantId,
        attendantName = attendantNameAtSale,
        customerId = customerId,
        customerName = customerName,
        customerPhoneE164 = customerPhoneAtSale,
        product = Product.valueOf(product),
        amountPaidKes = BigDecimal.valueOf(amountPaid),
        pricePerLitreSnapshot = BigDecimal.valueOf(snapshot.pricePerLitre),
        cashbackRatePerLitreSnapshot = BigDecimal.valueOf(snapshot.cashbackRatePerLitre),
        isSpecialRateSnapshot = specialRateIdAtSale != null,
        litres = BigDecimal.valueOf(snapshot.litres),
        wholeLitres = BigDecimal.valueOf(snapshot.wholeLitres.toDouble()),
        cashbackKes = BigDecimal.valueOf(snapshot.cashbackEarned),
        capturedAtMillis = runCatching { Instant.parse(saleDate).toEpochMilli() }.getOrDefault(System.currentTimeMillis()),
        capturedOffline = false,
        syncStatus = SyncStatus.SYNCED,
        smsStatus = parseSmsStatus(smsStatus)
    )
}
