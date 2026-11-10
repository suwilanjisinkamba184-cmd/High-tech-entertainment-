package com.hightechentertainment.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.hightechentertainment.app.playback.StartupAudio
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HTE_BLACK = Color(0xFF080808)
private val HTE_PANEL = Color(0xFF151515)
private val HTE_GOLD = Color(0xFFD7A83E)
private val HTE_SILVER = Color(0xFFC7CCD0)

class MainActivity : ComponentActivity() {
    private val startupAudio by lazy { StartupAudio(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { HteHome() } }
        if (savedInstanceState == null) window.decorView.post { lifecycleScope.launch { delay(250); startupAudio.play() } }
    }
    override fun onDestroy() { startupAudio.release(); super.onDestroy() }
}

@Composable
private fun HteHome() {
    val tabs = listOf("Home", "Discover", "Videos", "Library", "Account")
    var selected by remember { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize(), color = HTE_BLACK) {
        Scaffold(containerColor = HTE_BLACK, bottomBar = {
            NavigationBar(containerColor = HTE_PANEL) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(selected == index, { selected = index }, icon = { Text(tab.take(1), color = if (selected == index) HTE_GOLD else HTE_SILVER) }, label = { Text(tab) })
                }
            }
        }) { inset ->
            Column(Modifier.fillMaxSize().padding(inset).padding(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("HIGH-TECH", color = HTE_SILVER, fontSize = 13.sp, letterSpacing = 4.sp)
                        Text("ENTERTAINMENT", color = HTE_GOLD, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    }
                    Text("HTE", color = HTE_GOLD, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.fillMaxWidth().background(HTE_PANEL, RoundedCornerShape(24.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("MUSIC. BEATS. PRODUCTION. BEYOND.", color = HTE_GOLD, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Text(listOf("Your sound. Your world.", "Discover new sounds.", "Watch the latest visuals.", "Your music library.", "Your HTE account.")[selected], color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("A clean native experience is being built for High-Tech Entertainment.", color = HTE_SILVER, fontSize = 14.sp, lineHeight = 21.sp)
                }
                Text("FOUNDATION BUILD 4.0", color = HTE_SILVER.copy(alpha = .55f), fontSize = 10.sp, letterSpacing = 2.sp)
            }
        }
    }
}
