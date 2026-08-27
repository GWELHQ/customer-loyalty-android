package com.example.loyaltyapp.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Enqueues [SyncWorker] runs: a steady periodic sweep, plus expedited one-shots on demand. */
@Singleton
class SyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)

        // A sync is one or two paginated GET /mobile/customers calls (see
        // CustomerRepository.syncCustomers — full pull once, `updatedSince` incremental after
        // that), so a 15-minute cadence is cheap and keeps admin-side edits (e.g. a customer's
        // plate number) from being stale on the phone for long.
        val customerRefreshRequest = PeriodicWorkRequestBuilder<CustomerRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            CUSTOMER_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            customerRefreshRequest
        )
    }

    /** Requested right after a sale is captured, when connectivity returns, or on manual "Sync now". */
    fun requestImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Fired once right after login so a fresh device isn't stuck with an empty/thin customer cache. */
    fun requestImmediateCustomerDirectorySync() {
        val request = OneTimeWorkRequestBuilder<CustomerRefreshWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(CUSTOMER_REFRESH_IMMEDIATE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "sync_pending_sales_periodic"
        const val IMMEDIATE_WORK_NAME = "sync_pending_sales_immediate"
        const val CUSTOMER_REFRESH_WORK_NAME = "customer_cache_refresh_periodic"
        const val CUSTOMER_REFRESH_IMMEDIATE_WORK_NAME = "customer_cache_refresh_immediate"
    }
}
