package com.hightechentertainment.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.hightechentertainment.app.security.HteSecurityFirewall
import java.io.File

/** Safe on-device checks. Recovery only recreates app-owned directories; it never deletes user data. */
object HteDeviceDiagnostics {
    private const val PREFS = "hte_device_diagnostics"
    private const val KEY_REPORT = "latest_report"
    private const val FOUNDER_EMAIL = "suwilanjisinkamba184@gmail.com"

    data class Check(val name: String, val status: String, val detail: String)
    data class Report(val checks: List<Check>, val repairs: List<String>) {
        val hasFailures: Boolean get() = checks.any { it.status == "FAIL" }
        val issueCount: Int get() = checks.count { it.status != "PASS" }

        fun asText(): String = buildString {
            appendLine("HTE UNIVERSE DEVICE DIAGNOSTIC")
            appendLine("App: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Time: ${System.currentTimeMillis()}")
            appendLine()
            checks.forEach { appendLine("[${it.status}] ${it.name}: ${it.detail}") }
            appendLine()
            appendLine("Safe recovery actions:")
            if (repairs.isEmpty()) appendLine("None required.")
            else repairs.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Privacy: diagnostics only; no passwords, access tokens, media, messages or account contents are included.")
        }
    }

    fun run(context: Context, attemptSafeRepair: Boolean = true): Report {
        val app = context.applicationContext
        val repairs = mutableListOf<String>()
        if (attemptSafeRepair) {
            listOf(app.filesDir, app.cacheDir).forEach { directory ->
                if (!directory.exists() && directory.mkdirs()) repairs += "Recreated app-owned directory: ${directory.name}"
            }
        }

        val checks = mutableListOf<Check>()
        fun add(name: String, status: String, detail: String) {
            checks += Check(name, status, detail.take(220))
        }

        add("Application ID",
            if (app.packageName == HteSecurityFirewall.EXPECTED_PACKAGE_NAME) "PASS" else "FAIL",
            app.packageName)

        val integrityOk = BuildConfig.DEBUG || HteSecurityFirewall.verifyAppIntegrity(app)
        add("Production signing integrity", if (integrityOk) "PASS" else "FAIL",
            if (BuildConfig.DEBUG) "Debug build; production certificate check skipped."
            else if (integrityOk) "Trusted production signing certificate verified."
            else "Installed APK certificate does not match the trusted production certificate.")

        val backendOk = BuildConfig.SUPABASE_URL.startsWith("https://") &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
        add("Backend configuration", if (backendOk) "PASS" else "FAIL",
            if (backendOk) "Supabase HTTPS endpoint and publishable key are present."
            else "Supabase URL or publishable key is missing.")

        val internetPermission = app.packageManager.checkPermission(
            Manifest.permission.INTERNET, app.packageName
        ) == PackageManager.PERMISSION_GRANTED
        add("Internet permission", if (internetPermission) "PASS" else "FAIL",
            if (internetPermission) "Declared and granted." else "INTERNET permission is missing.")

        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        add("Network availability", if (online) "PASS" else "WARN",
            if (online) "An internet-capable network is connected."
            else "No active internet-capable network; offline features remain available.")

        val storageOk = runCatching {
            val probe = File(app.cacheDir, "hte-diagnostic-${System.nanoTime()}.tmp")
            probe.writeText("ok")
            probe.delete()
        }.getOrDefault(false)
        add("Private storage", if (storageOk) "PASS" else "FAIL",
            if (storageOk) "App-private cache is writable." else "Cannot write to app-private cache.")

        val audioResourceOk = runCatching {
            app.resources.openRawResource(R.raw.hte_universe_intro).use { it.read() >= 0 }
        }.getOrDefault(false)
        add("Startup audio resource", if (audioResourceOk) "PASS" else "FAIL",
            if (audioResourceOk) "Packaged startup audio can be opened." else "Startup audio resource is missing or unreadable.")

        val videoDecoder = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals("video/avc", true) }
            }
        }.getOrDefault(false)
        add("H.264 video decoder", if (videoDecoder) "PASS" else "WARN",
            if (videoDecoder) "Device advertises an H.264 decoder."
            else "No H.264 decoder reported; some video formats may not play.")

        val audioDecoder = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any {
                    it.equals("audio/mpeg", true) || it.equals("audio/mp4a-latm", true)
                }
            }
        }.getOrDefault(false)
        add("Audio decoder capability", if (audioDecoder) "PASS" else "WARN",
            if (audioDecoder) "Device advertises an MP3 or AAC decoder."
            else "Standard MP3/AAC decoder was not reported by the device.")

        val notifications = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        add("Diagnostic notifications", if (notifications) "PASS" else "WARN",
            if (notifications) "Notification permission is available."
            else "Notifications are disabled; review diagnostics manually.")

        val report = Report(checks, repairs)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_REPORT, report.asText())
            .putLong("last_run", System.currentTimeMillis())
            .apply()
        Log.i("HTEDiagnostics", "Self-check complete: ${report.issueCount} warning/failure(s).")
        return report
    }

    fun savedReport(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REPORT, "No device diagnostic has been recorded yet.") ?: "No diagnostic report."

    fun firstInstallCheck(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_install_check_done", false)) return
        Thread({
            runCatching {
                val report = run(app, true)
                prefs.edit().putBoolean("first_install_check_done", true).apply()
                if (report.hasFailures) HteDiagnosticsNotifier.show(app, report)
            }.onFailure { Log.e("HTEDiagnostics", "First-install diagnostic failed", it) }
        }, "hte-first-install-diagnostics").start()
    }

    fun emailIntent(context: Context, report: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FOUNDER_EMAIL")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "HTE Universe diagnostic / repair request")
            putExtra(Intent.EXTRA_TEXT, report)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
