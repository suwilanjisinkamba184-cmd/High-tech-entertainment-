package com.hightechentertainment.app

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.hightechentertainment.app.playback.StartupJingle

/**
 * Flutter is the presentation host. Android services and the existing native
 * app remain in this application and are exposed through explicit channels.
 */
class FlutterHostActivity : FlutterActivity() {
    private val startupChannelName = "com.hightechentertainment.app/startup"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, startupChannelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "playStartup" -> result.success(
                        StartupJingle.playForSplash(applicationContext)
                    )
                    "stopStartup" -> {
                        StartupJingle.stopForSplash()
                        result.success(true)
                    }
                    "openNativeApp" -> {
                        startActivity(Intent(this, MainActivity::class.java))
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun onStop() {
        StartupJingle.stopForSplash()
        super.onStop()
    }
}
