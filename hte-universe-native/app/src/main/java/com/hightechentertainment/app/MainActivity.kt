package com.hightechentertainment.app

import android.os.Bundle
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Obsidian = Color(0xFF08090B)
private val Panel = Color(0xFF121418)
private val Gold = Color(0xFFD6B15E)
private val Silver = Color(0xFFC4CAD2)
private val Muted = Color(0xFF8B929D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 9, 11)
        window.navigationBarColor = android.graphics.Color.rgb(8, 9, 11)
        setContent { HteUniverseApp() }
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
            if (!splashFinished) UniverseSplash { splashFinished = true } else UniverseHome()
        }
    }
}

@Composable
private fun UniverseSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val audioId = context.resources.getIdentifier("hte_universe_intro", "raw", context.packageName)
        val player = if (audioId != 0) MediaPlayer.create(context, audioId) else null
        player?.start()
        onDispose {
            try { player?.stop() } catch (_: IllegalStateException) { }
            player?.release()
        }
    }
    var elapsed by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "universe-motion")
    val orbit by transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing)), label = "orbit")
    val headsetTilt by transition.animateFloat(-13f, 13f,
        infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "headset")
    val cameraTilt by transition.animateFloat(12f, -12f,
        infiniteRepeatable(tween(2700), RepeatMode.Reverse), label = "camera")
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
            Image(painter = painterResource(R.drawable.ic_hte_crown), contentDescription = "HTE Universe crown", modifier = Modifier.size(width = 76.dp, height = 52.dp))
            Spacer(Modifier.height(8.dp))
            Text("HTE", color = Silver, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp)
            Text("UNIVERSE", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 7.sp)
            Text("HIGH-TECH ENTERTAINMENT", color = Silver, fontSize = 10.sp, letterSpacing = 2.4.sp)
            Spacer(Modifier.height(44.dp))
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = orbit }) {
                    drawCircle(Gold.copy(alpha = .45f), style = Stroke(width = 1.3.dp.toPx()))
                    drawCircle(Silver.copy(alpha = .22f), radius = size.minDimension * .39f, style = Stroke(width = 1.dp.toPx()))
                    drawCircle(Gold, radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * .5f, 4.dp.toPx()))
                }
                Icon(Icons.Outlined.Headphones, "Rotating headphones", tint = Gold,
                    modifier = Modifier.size(112.dp).graphicsLayer { rotationY = headsetTilt; rotationZ = -headsetTilt / 3 })
                Icon(Icons.Outlined.Videocam, "Orbiting video camera", tint = Silver,
                    modifier = Modifier.align(Alignment.BottomEnd).size(58.dp)
                        .graphicsLayer { rotationY = cameraTilt; rotationZ = cameraTilt })
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

@Composable
private fun UniverseHome() {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf("Home", "Discover", "Videos", "Library", "Account")
    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Explore, Icons.Outlined.VideoLibrary,
        Icons.Outlined.LibraryMusic, Icons.Outlined.Person)
    Scaffold(containerColor = Obsidian, bottomBar = {
        NavigationBar(containerColor = Panel, contentColor = Gold) {
            tabs.forEachIndexed { index, label ->
                NavigationBarItem(selected = selected == index, onClick = { selected = index },
                    icon = { Icon(icons[index], contentDescription = label) }, label = { Text(label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold,
                        unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Gold.copy(alpha = .12f)))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HTE UNIVERSE", color = Gold, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text("The Pride of Africa", color = Silver, fontSize = 11.sp)
                }
                IconButton(onClick = {}) { Icon(Icons.Outlined.NotificationsNone, "Notifications", tint = Silver) }
                IconButton(onClick = {}) { Icon(Icons.Outlined.Search, "Search", tint = Silver) }
            }
            Spacer(Modifier.height(28.dp))
            Text(tabs[selected], color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("WELCOME TO YOUR UNIVERSE", color = Gold, fontSize = 10.sp, letterSpacing = 1.8.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Music. Creativity. Connection.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Discover music, artists and visual entertainment from High-Tech Entertainment.",
                        color = Silver, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeaturePill(Icons.Outlined.Headphones, "Music")
                        FeaturePill(Icons.Outlined.Videocam, "Videos")
                        FeaturePill(Icons.Outlined.GraphicEq, "Studio")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("EXPLORE HTE", color = Silver, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            Text("Your entertainment universe is taking shape.", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FeaturePill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Gold.copy(alpha = .10f))
        .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = Gold, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Silver, fontSize = 11.sp)
    }
}
