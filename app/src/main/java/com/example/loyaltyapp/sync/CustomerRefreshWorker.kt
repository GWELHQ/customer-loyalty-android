package com.example.loyaltyapp.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.loyaltyapp.data.repository.CustomerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically syncs the local customer cache against `GET /mobile/customers` (see
 * [CustomerRepository.syncCustomers]) — a full pull the first time, an incremental
 * `updatedSince` pull on every run after that.
 */
@HiltWorker
class CustomerRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val customerRepository: CustomerRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            customerRepository.syncCustomers()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
