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
    private val connectivityObserver: ConnectivityObserver
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

    fun observeAll(): Flow<List<CustomerRegistrationEntity>> = registrationDao.observeAll()

    fun observePendingCount(): Flow<Int> = registrationDao.observePendingCount()
}
