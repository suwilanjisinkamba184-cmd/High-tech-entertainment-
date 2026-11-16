package com.hightechentertainment.app

import android.os.Bundle
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import io.github.jan.supabase.auth.handleDeeplinks
import com.hightechentertainment.app.data.SupabaseClientProvider
import com.hightechentertainment.app.media.HtePlaybackController
import com.hightechentertainment.app.security.HteSecurityFirewall
import com.hightechentertainment.app.ui.design.HteTheme

private val Obsidian = Color(0xFF08090B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG && !HteSecurityFirewall.verifyAppIntegrity(this)) {
            Log.e("HTESecurity", "Production application signature verification failed.")
            Toast.makeText(this, "This HTE Universe installation could not be verified.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        SupabaseClientProvider.initialize(applicationContext)
        handleSupabaseIntent(intent)
        // Make edge-to-edge behavior explicit on Android 15+ and keep Compose responsible for safe insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent { HteUniverseApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSupabaseIntent(intent)
    }

    override fun onStop() {
        if (!AppSettingsStore(this).backgroundPlayback) HtePlaybackController.pause()
        super.onStop()
    }

    private fun handleSupabaseIntent(intent: Intent) {
        val callback = intent.data ?: return
        if (callback.scheme != "com.hightechentertainment.app" || callback.host != "auth-callback") return
        if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            runCatching { SupabaseClientProvider.client.handleDeeplinks(intent) }
                .onFailure { error ->
                    Log.e("HTEAuth", "Supabase app callback could not be processed", error)
                    Toast.makeText(this, "Sign-in could not be completed. Please try again.", Toast.LENGTH_LONG).show()
                }
        }
    }
}

@Composable
private fun HteUniverseApp() {
    var splashFinished by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsStore = remember(context) { AppSettingsStore(context) }
    var reduceMotion by remember(settingsStore) { mutableStateOf(settingsStore.reduceMotion) }
    DisposableEffect(settingsStore) {
        val stopObserving = settingsStore.observeReduceMotion { reduceMotion = it }
        onDispose { stopObserving() }
    }
    HteTheme(reduceMotion = reduceMotion) {
        Surface(Modifier.fillMaxSize(), color = Obsidian) {
            if (!splashFinished) UniverseSplash { splashFinished = true } else UniverseShell()
        }
    }
}

@Composable
private fun UniverseSplash(onFinished: () -> Unit) {
    HteSplashScreen(
        onSplashFinished = onFinished,
        logo = {
            Image(
                painter = painterResource(R.drawable.ht_logo),
                contentDescription = "High-Tech Entertainment official logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

