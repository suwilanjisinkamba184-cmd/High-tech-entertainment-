package com.hightechentertainment.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal object HteDiagnosticsNotifier {
    private const val CHANNEL_ID = "hte-device-diagnostics"

    private fun notify(context: Context, title: String, message: String, notificationId: Int) {
        // Android 13+ requires runtime notification permission. Diagnostic reports
        // are persisted independently, so a denied permission never loses the report.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.i("HTEDiagnostics", "Notification permission not granted; report remains saved.")
            return
        }
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "HTE device diagnostics", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val openDiagnostics = PendingIntent.getActivity(
                context, notificationId, Intent(context, HteDiagnosticsActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hte_crown)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(openDiagnostics)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }.onFailure {
            android.util.Log.w("HTEDiagnostics", "Could not post diagnostic notification; report remains saved.", it)
        }
    }

    fun show(context: Context, report: HteDeviceDiagnostics.Report) {
        val failed = report.checks.count { it.status == "FAIL" }
        notify(context, "HTE Universe needs attention",
            "The first-install self-check found $failed issue(s). Tap to review.", 4107)
    }

    fun showCrash(context: Context) {
        notify(context, "HTE Universe recovered after a crash",
            "A crash report was saved. Tap to review and send it to the founder.", 4108)
    }
}
