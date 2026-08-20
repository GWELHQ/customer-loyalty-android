package com.example.loyaltyapp.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.loyaltyapp.data.repository.CustomerRegistrationRepository
import com.example.loyaltyapp.data.repository.SaleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Pushes locally queued sales (batched via `/mobile/sync`) and pending customer-registration
 * requests (retried individually — no bulk endpoint) to the backend. Runs periodically in the
 * background and is also triggered immediately whenever connectivity returns or the attendant
 * taps "Sync now". Safe to run concurrently or repeatedly — every submission is idempotency-keyed.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val saleRepository: SaleRepository,
    private val customerRegistrationRepository: CustomerRegistrationRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            saleRepository.syncAllPending()
            customerRegistrationRepository.retryAllPending()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
