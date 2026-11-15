package com.hightechentertainment.app

import android.app.Application

class HteUniverseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HteCrashReporter.install(this)
        HteDeviceDiagnostics.firstInstallCheck(this)
        if (HteCrashReporter.pendingReport(this) != null) {
            HteDiagnosticsNotifier.showCrash(this)
        }
    }
}
