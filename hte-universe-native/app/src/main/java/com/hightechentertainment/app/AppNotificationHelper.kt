package com.hightechentertainment.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat

object AppNotificationHelper {
    private const val CHANNEL_ID = "hte_account_updates"

    fun show(context: Context, title: String, message: String, notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()) {
        if (!AppSettingsStore(context).notificationsEnabled) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "HTE account updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Booking and account updates from HTE Universe"
                }
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hte_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
            .onFailure { if (it !is SecurityException) throw it }
    }
}
