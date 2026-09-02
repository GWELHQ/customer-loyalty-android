package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.core.session.AttendantSyncAuthenticator
import com.example.loyaltyapp.core.session.TokenResult
import com.example.loyaltyapp.data.local.dao.CustomerRegistrationDao
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.RegistrationSyncStatus
import com.example.loyaltyapp.data.remote.api.MobileApi
import com.example.loyaltyapp.data.remote.dto.CustomerRegistrationRequestDto
import com.example.loyaltyapp.data.remote.dto.SaleResponseDto
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NewCustomerRegistrationInput(
    val attendantId: String,
    val fullName: String,
    val phoneNumber: String,
    val product: Product,
    val amountPaid: BigDecimal
)

/**
 * A new-customer "registration" is a pending-approval request, never a confirmed sale — it is
 * queueable offline like a sale (unlike a plain customer-creation call, which doesn't exist in
 * this API), but there is no bulk endpoint for it, so retries happen one row at a time.
 */
@Singleton
class CustomerRegistrationRepository @Inject constructor(
    private val registrationDao: CustomerRegistrationDao,
    private val mobileApi: MobileApi,
    private val connectivityObserver: ConnectivityObserver,
    private val saleRepository: SaleRepository,
    private val attendantSyncAuthenticator: AttendantSyncAuthenticator
) {
    suspend fun submit(input: NewCustomerRegistrationInput): CustomerRegistrationEntity {
        val wasOnlineAtCapture = connectivityObserver.currentlyOnline()
        val localId = UUID.randomUUID().toString()
        val entity = CustomerRegistrationEntity(
            localId = localId,
            idempotencyKey = localId,
            serverRequestId = null,
            attendantId = input.attendantId,
            customerFullName = input.fullName,
            customerPhoneNumber = input.phoneNumber,
            product = input.product,
            amountPaid = input.amountPaid,
            capturedAtMillis = System.currentTimeMillis(),
            capturedOffline = !wasOnlineAtCapture,
            syncStatus = RegistrationSyncStatus.PENDING
        )
        registrationDao.insert(entity)

        // The submitting attendant is, by construction, always the one currently signed into the
        // UI — no explicit bearer needed here, same as before this attendant-aware rework.
        return if (wasOnlineAtCapture) attemptSyncInternal(entity, explicitBearer = null) else entity
    }

    /** Retries a single registration as whoever is currently signed into the UI (implicit auth) — used by the manual "Sync now" / immediate-retry paths, which only ever act on the foreground attendant's own queue. */
    suspend fun attemptSync(registration: CustomerRegistrationEntity): CustomerRegistrationEntity =
        attemptSyncInternal(registration, explicitBearer = null)

    private suspend fun attemptSyncInternal(registration: CustomerRegistrationEntity, explicitBearer: String?): CustomerRegistrationEntity {
        if (!connectivityObserver.currentlyOnline()) return registration

        registrationDao.update(registration.copy(syncStatus = RegistrationSyncStatus.SYNCING))
        return try {
            val requestDto = CustomerRegistrationRequestDto(
                customerFullName = registration.customerFullName,
                customerPhoneNumber = registration.customerPhoneNumber,
                product = registration.product.name,
                amountPaid = registration.amountPaid.toDouble(),
                saleDate = Instant.ofEpochMilli(registration.capturedAtMillis).toString(),
                idempotencyKey = registration.idempotencyKey
            )
            val response = if (explicitBearer != null) {
                mobileApi.registerCustomerAs(explicitBearer, requestDto)
            } else {
                mobileApi.registerCustomer(requestDto)
            }
            val updated = registration.copy(
                syncStatus = RegistrationSyncStatus.SUBMITTED,
                serverRequestId = response.id,
                lastSyncErrorMessage = null,
                syncAttempts = registration.syncAttempts + 1
            )
            registrationDao.update(updated)
            updated
        } catch (e: retrofit2.HttpException) {
            // A 409/422 here almost always means the office already has this phone number as a
            // customer — the local "not found" check that routed the attendant here can be stale
            // (see [com.example.loyaltyapp.ui.sale.SaleFlowViewModel.checkFullNumberIfNeeded]).
            // Surface the server's own message rather than a generic offline one, since retrying
            // this exact request will never succeed.
            val updated = registration.copy(
                syncStatus = RegistrationSyncStatus.FAILED,
                lastSyncErrorMessage = com.example.loyaltyapp.data.remote.NetworkErrors.messageFor(
                    e,
                    fallback = "The office found a problem with this registration. See your supervisor."
                ),
                syncAttempts = registration.syncAttempts + 1
            )
            registrationDao.update(updated)
            updated
        } catch (e: Exception) {
            val updated = registration.copy(
                syncStatus = RegistrationSyncStatus.FAILED,
                lastSyncErrorMessage = "Could not reach the office. It will try again automatically, or tap Sync now.",
                syncAttempts = registration.syncAttempts + 1
            )
            registrationDao.update(updated)
            updated
        }
    }

    /**
     * No bulk endpoint for registrations — retried one at a time, grouped by [CustomerRegistrationEntity.attendantId]
     * and resolved through [AttendantSyncAuthenticator] exactly like [SaleRepository.syncAllPending] (handover doc
     * §5.6 — never touch a different attendant's queue with the wrong token). Rows with a blank `attendantId` are
     * legacy, queued before this column existed (see LoyaltyDatabase MIGRATION_4_5); they fall back to the old
     * implicit-session behavior since there's no real attendant to look up.
     */
    suspend fun retryAllPending() {
        if (!connectivityObserver.currentlyOnline()) return
        registrationDao.getPending().groupBy { it.attendantId }.forEach { (attendantId, registrations) ->
            if (attendantId.isBlank()) {
                registrations.forEach { attemptSyncInternal(it, explicitBearer = null) }
                return@forEach
            }
            when (val tokenResult = attendantSyncAuthenticator.accessTokenFor(attendantId)) {
                TokenResult.Unavailable, TokenResult.SessionDead -> Unit
                is TokenResult.Available -> {
                    val bearer = if (tokenResult.isForegroundSession) null else "Bearer ${tokenResult.accessToken}"
                    registrations.forEach { attemptSyncInternal(it, explicitBearer = bearer) }
                }
            }
        }
    }

    suspend fun getById(localId: String): CustomerRegistrationEntity? = registrationDao.getById(localId)

    fun observeById(localId: String): Flow<CustomerRegistrationEntity?> = registrationDao.observeById(localId)

    fun observeAll(): Flow<List<CustomerRegistrationEntity>> = registrationDao.observeAll()

    fun observeTodayUnapproved(startOfDayMillis: Long): Flow<List<CustomerRegistrationEntity>> =
        registrationDao.observeTodayUnapproved(startOfDayMillis)

    fun observePendingCount(): Flow<Int> = registrationDao.observePendingCount()

    /**
     * There's no `GET` endpoint for a registration's own status, so approval is detected
     * indirectly: once a supervisor approves a request, the server creates a real sale sharing
     * the registration's own `idempotencyKey`. This looks that sale up via `/mobile/sales/mine`
     * — grouped by [CustomerRegistrationEntity.attendantId] and resolved through
     * [AttendantSyncAuthenticator], same reasoning as [retryAllPending]: `/mobile/sales/mine` is
     * scoped server-side to whichever token calls it, so a submitted registration can only ever
     * be found under its *own* attendant's token, not whoever else happens to be logged in right
     * now. Queried once per distinct day among a given attendant's still-SUBMITTED requests.
     * Adopts the sale into the local sales queue if found, and flips the registration to
     * APPROVED. Returns the registrations that were newly found approved this call, for the
     * caller to notify the attendant about.
     */
    suspend fun reconcileApprovals(): List<CustomerRegistrationEntity> {
        if (!connectivityObserver.currentlyOnline()) return emptyList()
        val submitted = registrationDao.getSubmitted()
        if (submitted.isEmpty()) return emptyList()

        val newlyApproved = mutableListOf<CustomerRegistrationEntity>()
        submitted.groupBy { it.attendantId }.forEach { (attendantId, registrationsForAttendant) ->
            val salesByIdempotencyKey = fetchSalesMine(attendantId, registrationsForAttendant) ?: return@forEach
            registrationsForAttendant.forEach { registration ->
                val sale = salesByIdempotencyKey[registration.idempotencyKey] ?: return@forEach
                saleRepository.adoptServerSale(sale, registration.customerFullName)
                val approved = registration.copy(syncStatus = RegistrationSyncStatus.APPROVED)
                registrationDao.update(approved)
                newlyApproved.add(approved)
            }
        }
        return newlyApproved
    }

    /** Null return means "couldn't resolve a token for this attendant right now" — caller skips this group entirely and retries on the next reconcile, same as [retryAllPending]. */
    private suspend fun fetchSalesMine(
        attendantId: String,
        registrations: List<CustomerRegistrationEntity>
    ): Map<String, SaleResponseDto>? {
        val bearer: String? = if (attendantId.isBlank()) {
            null // legacy rows queued before this column existed — fall back to the old implicit/foreground behavior
        } else {
            when (val tokenResult = attendantSyncAuthenticator.accessTokenFor(attendantId)) {
                TokenResult.Unavailable, TokenResult.SessionDead -> return null
                is TokenResult.Available -> if (tokenResult.isForegroundSession) null else "Bearer ${tokenResult.accessToken}"
            }
        }

        val dates = registrations.map { it.capturedAtMillis.toServerDate() }.toSet()
        val salesByIdempotencyKey = HashMap<String, SaleResponseDto>()
        dates.forEach { date ->
            try {
                val items = if (bearer != null) mobileApi.salesMineAs(bearer, date).items else mobileApi.salesMine(date).items
                items.forEach { salesByIdempotencyKey[it.idempotencyKey] = it }
            } catch (_: Exception) {
                // Best-effort — this day's lookup failed, try again on the next reconcile.
            }
        }
        return salesByIdempotencyKey
    }

    private fun Long.toServerDate(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_LOCAL_DATE)
}
