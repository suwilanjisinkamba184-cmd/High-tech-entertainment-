package com.hightechentertainment.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AppNotificationHelper {
    private const val CHANNEL_ID = "hte_account_updates"

    fun show(context: Context, title: String, message: String, notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "HTE account updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Booking and account updates from HTE Universe"
            }
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hte_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // Android 13+ requires runtime notification permission. Respect the app-level
        // notification switch and guard against permission revocation between check and use.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        try {
            notifications.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // The permission may be revoked between the check and notify call.
        }
    }
}
