package com.hightechentertainment.app

import android.app.Application

class HteUniverseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HteDeviceDiagnostics.firstInstallCheck(this)
    }
}
