package com.example.loyaltyapp.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.loyaltyapp.core.notify.RegistrationApprovalNotifier
import com.example.loyaltyapp.data.repository.CustomerRegistrationRepository
import com.example.loyaltyapp.data.repository.SaleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Pushes locally queued sales (batched via `/mobile/sync`) and pending customer-registration
 * requests (retried individually — no bulk endpoint) to the backend — each grouped by attendant
 * and synced through that attendant's own token, whether or not they're still signed into the UI
 * (see `AttendantSyncAuthenticator`) — checks whether any still-pending registration has since
 * been approved by a supervisor (see `CustomerRegistrationRepository.reconcileApprovals`),
 * notifying the attendant for each one, and pulls down today's sales from the server so one
 * recorded on a different device signed into the same attendant account shows up here too (see
 * `SaleRepository.pullTodayFromServer`). Runs periodically in the background and is also
 * triggered immediately whenever connectivity returns or the attendant taps "Sync now". Safe to
 * run concurrently or repeatedly — every submission is idempotency-keyed.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val saleRepository: SaleRepository,
    private val customerRegistrationRepository: CustomerRegistrationRepository,
    private val registrationApprovalNotifier: RegistrationApprovalNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            saleRepository.syncAllPending()
            customerRegistrationRepository.retryAllPending()
            customerRegistrationRepository.reconcileApprovals()
                .forEach { registrationApprovalNotifier.notifyApproved(it) }
            saleRepository.pullTodayFromServer()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
