package com.example.loyaltyapp.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.loyaltyapp.R
import com.example.loyaltyapp.data.local.entity.CustomerRegistrationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "registration_approvals"

/** Local notification shown when a pending new-customer registration is approved by a supervisor. */
@Singleton
class RegistrationApprovalNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Registration approvals",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies you when a new-customer request you submitted is approved."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notifyApproved(registration: CustomerRegistrationEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = openAppIntent?.let {
            android.app.PendingIntent.getActivity(
                context,
                registration.localId.hashCode(),
                it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Registration approved")
            .setContentText("${registration.customerFullName}'s sale has been approved and is now in Today's sales.")
            .setAutoCancel(true)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()

        notificationManager.notify(registration.localId.hashCode(), notification)
    }
}
