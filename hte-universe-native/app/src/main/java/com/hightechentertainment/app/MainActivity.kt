package com.hightechentertainment.app

import android.os.Bundle
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
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

private val Obsidian = Color(0xFF08090B)
private val Panel = Color(0xFF121418)
private val Gold = Color(0xFFD6B15E)
private val Silver = Color(0xFFC4CAD2)
private val Muted = Color(0xFF8B929D)

@OptIn(markerClass = [UnstableApi::class])
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSupabaseIntent(intent)
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
        if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            runCatching { SupabaseClientProvider.client.handleDeeplinks(intent) }
        }
    }
}

@Composable
private fun HteUniverseApp() {
    var splashFinished by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, secondary = Silver, background = Obsidian,
        surface = Panel, onPrimary = Obsidian, onBackground = Color.White, onSurface = Color.White
    )) {
        Surface(Modifier.fillMaxSize(), color = Obsidian) {
            if (!splashFinished) UniverseSplash { splashFinished = true } else UniverseShell()
        }
    }
}

@Composable
private fun UniverseSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val startupEnabled = AppSettingsStore(context).startupSoundEnabled
        val player = if (startupEnabled) MediaPlayer.create(context, R.raw.hte_universe_intro) else null
        // The packaged stereo MP3 is 14.054s; pitch-preserving 0.937x playback aligns it to the 15s splash.
        runCatching { player?.setPlaybackParams(PlaybackParams().setSpeed(0.937f).setPitch(1.0f)) }
        player?.start()
        onDispose {
            try { player?.stop() } catch (_: IllegalStateException) { }
            player?.release()
        }
    }
    var elapsed by remember { mutableIntStateOf(0) }
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
    LaunchedEffect(Unit) {
        while (elapsed < 15) { delay(1000); elapsed++ }
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF17130B), Obsidian, Color(0xFF050608))))) {
        StarField(Modifier.fillMaxSize(), orbit)
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painter = painterResource(R.drawable.ic_hte_crown), contentDescription = "High-Tech Entertainment crown", modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(5.dp))
            Image(painter = painterResource(R.drawable.ht_logo), contentDescription = "High-Tech Entertainment official logo", contentScale = ContentScale.Fit, modifier = Modifier.size(142.dp))
            Spacer(Modifier.height(8.dp))
            Text("UNIVERSE", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 7.sp)
            Spacer(Modifier.height(44.dp))
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = orbit }) {
                    drawCircle(Gold.copy(alpha = .45f), style = Stroke(width = 1.3.dp.toPx()))
                    drawCircle(Silver.copy(alpha = .22f), radius = size.minDimension * .39f, style = Stroke(width = 1.dp.toPx()))
                    drawCircle(Gold, radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * .5f, 4.dp.toPx()))
                }
                Icon(Icons.Outlined.Headphones, "Spinning headphones", tint = Gold,
                    modifier = Modifier.size(112.dp).graphicsLayer { rotationZ = headsetSpin })
                Icon(Icons.Outlined.Videocam, "Spinning video camera", tint = Silver,
                    modifier = Modifier.align(Alignment.BottomEnd).size(58.dp).graphicsLayer { rotationZ = cameraSpin })
                Icon(Icons.Outlined.VideoLibrary, "Orbiting video library", tint = Gold.copy(alpha = .8f),
                    modifier = Modifier.align(Alignment.TopEnd).size(36.dp).graphicsLayer { rotationZ = -orbit })
                Icon(Icons.Outlined.AutoAwesome, "Orbiting star", tint = Gold,
                    modifier = Modifier.align(Alignment.TopStart).size(30.dp).graphicsLayer { rotationZ = orbit })
            }
            Spacer(Modifier.height(38.dp))
            Text(captions[(elapsed / 4).coerceIn(0, 3)], color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(9.dp))
            Text("THE PRIDE OF AFRICA", color = Gold, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(36.dp))
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
