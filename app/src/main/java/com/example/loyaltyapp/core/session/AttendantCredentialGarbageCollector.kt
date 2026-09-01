package com.example.loyaltyapp.core.session

import com.example.loyaltyapp.data.repository.CustomerRegistrationRepository
import com.example.loyaltyapp.data.repository.SaleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drops a retained refresh token (see [AttendantCredentialStore]) once that attendant has zero
 * PENDING/FAILED sales *and* zero unresolved registrations (PENDING/SYNCING/FAILED/SUBMITTED —
 * everything short of APPROVED) left — checked jointly, in one place, rather than inline in
 * [SaleRepository]/[CustomerRegistrationRepository] themselves, because both repositories can
 * independently observe "nothing of mine left" for the same attendant within the same
 * [com.example.loyaltyapp.sync.SyncWorker] tick; removing the credential after just one of them
 * finishes would strand the other's still-pending work. A SUBMITTED registration in particular
 * still needs this attendant's token later — `reconcileApprovals()` keeps polling with it until a
 * supervisor approves or rejects the request — so it counts as unresolved, not "done", here.
 */
@Singleton
class AttendantCredentialGarbageCollector @Inject constructor(
    private val credentialStore: AttendantCredentialStore,
    private val sessionManager: SessionManager,
    private val saleRepository: SaleRepository,
    private val customerRegistrationRepository: CustomerRegistrationRepository
) {
    suspend fun pruneFullySynced() {
        val currentAttendantId = sessionManager.currentSession()?.attendantId
        credentialStore.all().forEach { credential ->
            if (credential.attendantId == currentAttendantId) return@forEach
            val pendingSales = saleRepository.countPendingOrFailedForAttendant(credential.attendantId)
            val unresolvedRegistrations = customerRegistrationRepository.countUnresolvedForAttendant(credential.attendantId)
            if (pendingSales == 0 && unresolvedRegistrations == 0) {
                credentialStore.remove(credential.attendantId)
            }
        }
    }
}
