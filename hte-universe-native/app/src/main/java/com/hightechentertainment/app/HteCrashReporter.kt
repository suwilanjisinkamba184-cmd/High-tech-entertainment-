package com.hightechentertainment.app

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/** Stores a privacy-minimised fatal-crash summary for review on the next launch. */
internal object HteCrashReporter {
    private const val PREFS = "hte_device_diagnostics"
    private const val KEY_PENDING_CRASH = "pending_crash_report"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val frames = error.stackTrace.take(24).joinToString("\n") {
                    "  at ${it.className}.${it.methodName}(${it.fileName ?: "?"}:${it.lineNumber})"
                }
                val report = buildString {
                    appendLine("HTE UNIVERSE FATAL CRASH")
                    appendLine("App: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception type: ${error.javaClass.name}")
                    appendLine("Stack frames (top 24):")
                    appendLine(frames)
                    appendLine()
                    appendLine("Privacy: exception message and app data are intentionally omitted to avoid leaking credentials or personal content.")
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_PENDING_CRASH, report)
                    .apply()
            }.onFailure { Log.e("HTECrashReporter", "Could not persist crash summary", it) }
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun pendingReport(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PENDING_CRASH, null)
}
