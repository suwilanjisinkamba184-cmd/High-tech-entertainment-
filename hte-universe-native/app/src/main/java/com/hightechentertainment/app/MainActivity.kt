package com.hightechentertainment.app

import android.os.Bundle
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import io.github.jan.supabase.auth.handleDeeplinks
import com.hightechentertainment.app.data.SupabaseClientProvider
import com.hightechentertainment.app.media.HtePlaybackController
import com.hightechentertainment.app.security.HteSecurityFirewall
import com.hightechentertainment.app.ui.design.HteTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

private val Obsidian = Color(0xFF08090B)
private val Panel = Color(0xFF121418)
private val Gold = Color(0xFFD6B15E)
private val Silver = Color(0xFFC4CAD2)
private val Muted = Color(0xFF8B929D)

internal const val HTE_STARTUP_TAGLINE = "Music. Beats. Production. Beyond."

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

@Composable
private fun StarField(modifier: Modifier, rotation: Float) {
    Canvas(modifier) {
        val points = listOf(.08f to .14f, .82f to .18f, .18f to .73f, .91f to .66f,
            .72f to .88f, .34f to .25f, .56f to .09f, .12f to .48f, .88f to .42f, .46f to .92f)
        points.forEachIndexed { index, point ->
            val pulse = if ((index + rotation.toInt() / 36) % 2 == 0) 1f else .45f
            drawCircle(Gold.copy(alpha = .28f * pulse), radius = (1.4f + index % 3).dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * point.first, size.height * point.second))
        }
    }
}
