package com.hightechentertainment.app

import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val HTEBlack = Color(0xFF030806)
private val HTEEmeraldDeep = Color(0xFF061A12)
private val HTEGreenGlow = Color(0xFF00FF88)
private val HTEGoldLight = Color(0xFFF5D77F)
private val HTEGold = Color(0xFFD4AF37)
private val HTEGoldDark = Color(0xFF8C6D1F)
private val HTESilver = Color(0xFFD0D4DC)

private data class HteStar(
    val x: Float,
    val y: Float,
    val radius: Float,
    val phase: Float
)

@Composable
fun HteSplashScreen(
    onSplashFinished: () -> Unit,
    logo: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var elapsed by remember { mutableIntStateOf(0) }
    val latestElapsed by rememberUpdatedState(elapsed)

    val hAlpha = remember { Animatable(0f) }
    val hScale = remember { Animatable(1.6f) }
    val tAlpha = remember { Animatable(0f) }
    val tScale = remember { Animatable(1.6f) }
    val eAlpha = remember { Animatable(0f) }
    val eScale = remember { Animatable(1.6f) }
    val ringProgress = remember { Animatable(0f) }
    val innerLogoScale = remember { Animatable(0.75f) }
    val innerLogoAlpha = remember { Animatable(0f) }
    val flashGlow = remember { Animatable(0f) }

    var showSubtitle by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var showFooter by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }

    // One player only. Respect the user's startup-sound preference and pause/resume with lifecycle.
    DisposableEffect(context, lifecycleOwner) {
        val enabled = runCatching { AppSettingsStore(context).startupSoundEnabled }.getOrDefault(true)
        val player = if (enabled) runCatching {
            MediaPlayer.create(context, R.raw.hte_universe_intro)?.apply {
                setPlaybackParams(PlaybackParams().setSpeed(0.937f).setPitch(1.0f))
                isLooping = false
            }
        }.getOrNull() else null

        fun resumeIfNeeded() {
            if (latestElapsed < 15 && player != null && player.isPlaying.not()) {
                runCatching { player.start() }
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) resumeIfNeeded()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (runCatching { player?.isPlaying == true }.getOrDefault(false)) runCatching { player?.pause() }
                Lifecycle.Event.ON_START -> resumeIfNeeded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { player?.stop() }
            runCatching { player?.release() }
        }
    }

    // Foreground time only: returning from another app does not shorten the 15-second intro.
    LaunchedEffect(lifecycleOwner) {
        while (elapsed < 15) {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                delay(1000L)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    elapsed = (elapsed + 1).coerceAtMost(15)
                }
            } else {
                delay(250L)
            }
        }
    }
    LaunchedEffect(elapsed) {
        if (elapsed >= 15) onSplashFinished()
    }

    // Choreography runs independently of the 15-second timer.
    LaunchedEffect(Unit) {
        launch { hAlpha.animateTo(1f, tween(350)); hScale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 320f)) }
        delay(280L)
        launch { tAlpha.animateTo(1f, tween(350)); tScale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 320f)) }
        delay(280L)
        launch { eAlpha.animateTo(1f, tween(350)); eScale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 320f)) }
        delay(450L)
        ringProgress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        launch { flashGlow.animateTo(1f, tween(250)); flashGlow.animateTo(0.25f, tween(900)) }
        launch { innerLogoAlpha.animateTo(1f, tween(700)) }
        launch { innerLogoScale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 200f)) }
        delay(350L)
        showSubtitle = true
        delay(200L)
        showTagline = true
        delay(200L)
        showFooter = true
        delay(200L)
        showProgress = true
    }

    val stars = remember {
        val rng = Random(42)
        List(75) { HteStar(rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * 2.2f + 0.6f, rng.nextFloat() * 360f) }
    }
    val infinite = rememberInfiniteTransition(label = "cosmic-ambient")
    val ambientAngle by infinite.animateFloat(0f, 360f,
        infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart), label = "orbital-rotation")
    val twinklePhase by infinite.animateFloat(0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "star-twinkle")

    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(HTEEmeraldDeep, HTEBlack), radius = 1200f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val backdropCenter = Offset(size.width / 2f, size.height * 0.43f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(HTEGreenGlow.copy(alpha = 0.18f + flashGlow.value * 0.22f), Color(0xFF0A3A24).copy(alpha = 0.25f), Color.Transparent),
                    center = backdropCenter, radius = size.minDimension * 0.7f
                ), radius = size.minDimension * 0.7f, center = backdropCenter
            )
            stars.forEach { star ->
                val alpha = 0.25f + 0.75f * ((sin(twinklePhase + star.phase) + 1f) / 2f)
                drawCircle(Color.White.copy(alpha = alpha), star.radius, Offset(size.width * star.x, size.height * star.y))
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    // The ring has its own draw scope; define its center here (not in the backdrop Canvas).
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val strokeWidth = 10.dp.toPx()
                    val outerRadius = (size.minDimension - strokeWidth) / 2f
                    val innerRadius = outerRadius - 12.dp.toPx()
                    val sweep = 360f * ringProgress.value
                    if (sweep > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(HTEGoldDark, HTEGoldLight, HTEGold, HTEGoldLight, HTEGoldDark)),
                            startAngle = -90f, sweepAngle = sweep, useCenter = false,
                            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                            size = Size(size.width - strokeWidth, size.height - strokeWidth),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        if (ringProgress.value < 0.99f) {
                            val rad = Math.toRadians((-90f + sweep).toDouble())
                            drawCircle(HTEGoldLight, 7.dp.toPx(), Offset(center.x + outerRadius * cos(rad).toFloat(), center.y + outerRadius * sin(rad).toFloat()))
                        }
                    }
                    if (ringProgress.value > 0.6f) {
                        val a = ((ringProgress.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        drawCircle(HTEGold.copy(alpha = 0.35f * a), innerRadius, style = Stroke(1.5.dp.toPx()))
                        for (i in 0 until 6) {
                            val angle = Math.toRadians((ambientAngle + i * 60f).toDouble())
                            drawCircle(HTEGoldLight.copy(alpha = 0.85f * a), 2.5.dp.toPx(), Offset(center.x + outerRadius * cos(angle).toFloat(), center.y + outerRadius * sin(angle).toFloat()))
                        }
                    }
                }
                Box(Modifier.size(170.dp).graphicsLayer { alpha = innerLogoAlpha.value; scaleX = innerLogoScale.value; scaleY = innerLogoScale.value }, contentAlignment = Alignment.Center) { logo() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = (1f - innerLogoAlpha.value * 0.85f).coerceIn(0f, 1f) }
                ) {
                    MonogramLetter("H", hAlpha.value, hScale.value)
                    MonogramLetter("T", tAlpha.value, tScale.value)
                    MonogramLetter("E", eAlpha.value, eScale.value)
                }
            }
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(showSubtitle, enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 2 }) {
                Text("HTE UNIVERSE", style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, HTEGoldLight, HTEGold)), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp))
            }
            Spacer(Modifier.height(8.dp))
            AnimatedVisibility(showTagline, enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 2 }) {
                Text("MUSIC. TECHNOLOGY. BEYOND.", color = HTESilver, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.5.sp)
            }
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(showFooter, enter = fadeIn(tween(800))) {
                Text("HIGH-TECH ENTERTAINMENT", color = HTEGold.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(showProgress, enter = fadeIn(tween(600))) {
                LinearProgressIndicator(
                    progress = { (elapsed / 15f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(0.65f).height(3.dp),
                    color = HTEGold, trackColor = HTESilver.copy(alpha = 0.18f)
                )
            }
        }
    }
}

@Composable
private fun MonogramLetter(letter: String, alpha: Float, scale: Float) {
    Text(
        letter,
        modifier = Modifier.graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale },
        style = TextStyle(
            brush = Brush.verticalGradient(listOf(Color.White, HTESilver, Color(0xFF8E96A4))),
            fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp
        )
    )
}
