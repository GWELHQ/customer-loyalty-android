package com.example.loyaltyapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.example.loyaltyapp.core.session.SessionManager
import com.example.loyaltyapp.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LoyaltyApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Fires every time the app comes to the foreground - a cold start, or just resuming from
        // the background - not only on a fresh PIN login. That's what makes an admin-side edit
        // (a customer's plate number, special rate, ...) show up on next app open instead of
        // waiting out the periodic sync interval; cheap enough to run unconditionally since
        // CustomerRepository.syncCustomers is an incremental updatedSince pull after the first.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (sessionManager.currentSession() != null && !sessionManager.isExpired()) {
                    syncScheduler.requestImmediateCustomerDirectorySync()
                }
            }
        })
    }
}
