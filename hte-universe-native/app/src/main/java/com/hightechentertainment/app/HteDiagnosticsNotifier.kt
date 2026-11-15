package com.hightechentertainment.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

internal object HteDiagnosticsNotifier {
    private const val CHANNEL_ID = "hte-device-diagnostics"

    fun show(context: Context, report: HteDeviceDiagnostics.Report) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "HTE device diagnostics", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val openDiagnostics = PendingIntent.getActivity(
                context, 4107, Intent(context, HteDiagnosticsActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val failed = report.checks.count { it.status == "FAIL" }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_hte_crown)
                .setContentTitle("HTE Universe needs attention")
                .setContentText("The first-install self-check found an issue. Tap to review.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "The first-install self-check found $failed issue(s). Review the diagnostic report and contact the HTE founder."
                ))
                .setContentIntent(openDiagnostics)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(4107, notification)
        }.onFailure {
            android.util.Log.w("HTEDiagnostics", "Could not post diagnostic notification; report remains saved.", it)
        }
    }
}
