package com.example.loyaltyapp.data.repository

import com.example.loyaltyapp.core.connectivity.ConnectivityObserver
import com.example.loyaltyapp.data.local.dao.CustomerRegistrationDao
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import com.example.loyaltyapp.data.local.entity.Product
import com.example.loyaltyapp.data.local.entity.RegistrationSyncStatus
import com.example.loyaltyapp.data.remote.api.MobileApi
import com.example.loyaltyapp.data.remote.dto.CustomerRegistrationRequestDto
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NewCustomerRegistrationInput(
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
    private val saleRepository: SaleRepository
) {
    suspend fun submit(input: NewCustomerRegistrationInput): CustomerRegistrationEntity {
        val wasOnlineAtCapture = connectivityObserver.currentlyOnline()
        val localId = UUID.randomUUID().toString()
        val entity = CustomerRegistrationEntity(
            localId = localId,
            idempotencyKey = localId,
            serverRequestId = null,
            customerFullName = input.fullName,
            customerPhoneNumber = input.phoneNumber,
            product = input.product,
            amountPaid = input.amountPaid,
            capturedAtMillis = System.currentTimeMillis(),
            capturedOffline = !wasOnlineAtCapture,
            syncStatus = RegistrationSyncStatus.PENDING
        )
        registrationDao.insert(entity)

        return if (wasOnlineAtCapture) attemptSync(entity) else entity
    }

    suspend fun attemptSync(registration: CustomerRegistrationEntity): CustomerRegistrationEntity {
        if (!connectivityObserver.currentlyOnline()) return registration

        registrationDao.update(registration.copy(syncStatus = RegistrationSyncStatus.SYNCING))
        return try {
            val response = mobileApi.registerCustomer(
                CustomerRegistrationRequestDto(
                    customerFullName = registration.customerFullName,
                    customerPhoneNumber = registration.customerPhoneNumber,
                    product = registration.product.name,
                    amountPaid = registration.amountPaid.toDouble(),
                    saleDate = Instant.ofEpochMilli(registration.capturedAtMillis).toString(),
                    idempotencyKey = registration.idempotencyKey
                )
            )
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

    /** No bulk endpoint for registrations — retried one at a time. */
    suspend fun retryAllPending() {
        if (!connectivityObserver.currentlyOnline()) return
        registrationDao.getPending().forEach { attemptSync(it) }
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
     * (scoped per-day, so it's queried once per distinct day among still-SUBMITTED requests),
     * adopts it into the local sales queue if found, and flips the registration to APPROVED.
     * Returns the registrations that were newly found approved this call, for the caller to
     * notify the attendant about.
     */
    suspend fun reconcileApprovals(): List<CustomerRegistrationEntity> {
        if (!connectivityObserver.currentlyOnline()) return emptyList()
        val submitted = registrationDao.getSubmitted()
        if (submitted.isEmpty()) return emptyList()

        val dates = submitted.map { it.capturedAtMillis.toServerDate() }.toSet()
        val salesByIdempotencyKey = HashMap<String, com.example.loyaltyapp.data.remote.dto.SaleResponseDto>()
        dates.forEach { date ->
            try {
                mobileApi.salesMine(date).items.forEach { salesByIdempotencyKey[it.idempotencyKey] = it }
            } catch (_: Exception) {
                // Best-effort — this day's lookup failed, try again on the next reconcile.
            }
        }

        val newlyApproved = mutableListOf<CustomerRegistrationEntity>()
        submitted.forEach { registration ->
            val sale = salesByIdempotencyKey[registration.idempotencyKey] ?: return@forEach
            saleRepository.adoptServerSale(sale, registration.customerFullName)
            val approved = registration.copy(syncStatus = RegistrationSyncStatus.APPROVED)
            registrationDao.update(approved)
            newlyApproved.add(approved)
        }
        return newlyApproved
    }

    private fun Long.toServerDate(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_LOCAL_DATE)
}
