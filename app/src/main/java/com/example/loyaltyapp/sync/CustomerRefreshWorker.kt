package com.example.loyaltyapp.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.loyaltyapp.data.repository.CustomerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically rebuilds the complete local customer cache by sweeping the full national-prefix
 * space (see [CustomerRepository.refreshFullDirectory] — there's no bulk "list customers"
 * endpoint available to attendants).
 */
@HiltWorker
class CustomerRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val customerRepository: CustomerRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            customerRepository.refreshFullDirectory()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
