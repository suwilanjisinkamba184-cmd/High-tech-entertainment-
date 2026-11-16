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
    val reduceMotion = remember(context) { AppSettingsStore(context).reduceMotion }
    HteTheme(reduceMotion = reduceMotion) {
        Surface(Modifier.fillMaxSize(), color = Obsidian) {
            if (!splashFinished) UniverseSplash { splashFinished = true } else UniverseShell()
        }
    }
}

@Composable
private fun UniverseSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var elapsed by remember { mutableIntStateOf(0) }
    DisposableEffect(context, lifecycleOwner) {
        val startupEnabled = AppSettingsStore(context).startupSoundEnabled
        val player = if (startupEnabled) MediaPlayer.create(context, R.raw.hte_universe_intro) else null
        // The packaged stereo MP3 is 14.054s; pitch-preserving 0.937x playback aligns it to the 15s splash.
        runCatching { player?.setPlaybackParams(PlaybackParams().setSpeed(0.937f).setPitch(1.0f)) }
        player?.start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (player?.isPlaying == true) player.pause()
                Lifecycle.Event.ON_START -> if (elapsed < 15 && player?.isPlaying == false) runCatching { player.start() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { player?.stop() } catch (_: IllegalStateException) { }
            player?.release()
        }
    }
    val reduceMotion = remember(context) { AppSettingsStore(context).reduceMotion }
    val transition = rememberInfiniteTransition(label = "universe-motion")
    val animatedOrbit by transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing)), label = "orbit")
    val animatedHeadsetSpin by transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "headset-spin")
    val animatedCameraSpin by transition.animateFloat(360f, 0f,
        infiniteRepeatable(tween(6100, easing = LinearEasing)), label = "camera-spin")
    val orbit = if (reduceMotion) 0f else animatedOrbit
    val headsetSpin = if (reduceMotion) 0f else animatedHeadsetSpin
    val cameraSpin = if (reduceMotion) 0f else animatedCameraSpin
    val captions = listOf("Beyond the sound…", "Beyond the rhythm…", "Beyond boundaries…", "This is our universe.")
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (elapsed < 15) {
                delay(1000)
                elapsed++
            }
        }
    }
    LaunchedEffect(elapsed) {
        if (elapsed >= 15) onFinished()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF17130B), Obsidian, Color(0xFF050608))))) {
        val compactHeight = maxHeight < 680.dp
        val veryCompactHeight = maxHeight < 520.dp
        val crownBoxSize = if (veryCompactHeight) 54.dp else if (compactHeight) 62.dp else 72.dp
        val logoSize = if (veryCompactHeight) 86.dp else if (compactHeight) 100.dp else 118.dp
        val orbitSize = if (veryCompactHeight) 116.dp else if (compactHeight) 136.dp else 160.dp
        val headlineGap = if (compactHeight) 10.dp else 22.dp
        val sectionGap = if (compactHeight) 10.dp else 18.dp
        StarField(Modifier.fillMaxSize(), orbit)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = if (compactHeight) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(crownBoxSize).background(Brush.radialGradient(listOf(Gold.copy(alpha = .25f), Color.Transparent)), CircleShape), contentAlignment = Alignment.Center) {
                Image(painter = painterResource(R.drawable.ic_hte_crown), contentDescription = "High-Tech Entertainment crown", modifier = Modifier.size(crownBoxSize - 12.dp).graphicsLayer { rotationZ = if (reduceMotion) 0f else animatedCameraSpin * .035f })
            }
            Spacer(Modifier.height(2.dp))
            Image(painter = painterResource(R.drawable.ht_logo), contentDescription = "High-Tech Entertainment official logo", contentScale = ContentScale.Fit, modifier = Modifier.size(logoSize))
            Spacer(Modifier.height(8.dp))
            Text("UNIVERSE", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 7.sp)
            Spacer(Modifier.height(headlineGap))
            Box(Modifier.size(orbitSize).background(Brush.radialGradient(listOf(Gold.copy(alpha = .13f), Color.Transparent)), CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = orbit }) {
                    drawCircle(Gold.copy(alpha = .45f), style = Stroke(width = 1.3.dp.toPx()))
                    drawCircle(Silver.copy(alpha = .22f), radius = size.minDimension * .39f, style = Stroke(width = 1.dp.toPx()))
                    drawCircle(Gold, radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * .5f, 4.dp.toPx()))
                }
                Icon(Icons.Outlined.Headphones, "Spinning headphones", tint = Gold,
                    modifier = Modifier.size(orbitSize * .66f).graphicsLayer { rotationZ = headsetSpin; scaleX = 1.04f; scaleY = 1.04f })
                Icon(Icons.Outlined.Videocam, "Spinning video camera", tint = Silver,
                    modifier = Modifier.align(Alignment.BottomEnd).size(orbitSize * .26f).graphicsLayer { rotationZ = cameraSpin })
                Icon(Icons.Outlined.VideoLibrary, "Orbiting video library", tint = Gold.copy(alpha = .8f),
                    modifier = Modifier.align(Alignment.TopEnd).size(orbitSize * .175f).graphicsLayer { rotationZ = -orbit })
                Icon(Icons.Outlined.AutoAwesome, "Orbiting star", tint = Gold,
                    modifier = Modifier.align(Alignment.TopStart).size(orbitSize * .19f).graphicsLayer { rotationZ = orbit })
            }
            Spacer(Modifier.height(sectionGap))
            Text("A NEW SOUND. A NEW UNIVERSE.", color = Gold.copy(alpha = .82f), fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(captions[(elapsed / 4).coerceIn(0, 3)], color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, fontSize = if (compactHeight) 18.sp else 20.sp)
            Spacer(Modifier.height(9.dp))
            Text(HTE_STARTUP_TAGLINE, color = Gold, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(progress = { elapsed / 15f },
                modifier = Modifier.fillMaxWidth(.72f).height(3.dp).clip(CircleShape),
                color = Gold, trackColor = Silver.copy(alpha = .18f))
            Spacer(Modifier.height(10.dp))
            Text("PREPARING YOUR UNIVERSE  •  ${((elapsed / 15f) * 100).toInt()}%", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
        }
    }
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
