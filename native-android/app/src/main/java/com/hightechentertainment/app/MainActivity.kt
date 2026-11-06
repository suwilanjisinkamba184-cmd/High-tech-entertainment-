package com.hightechentertainment.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HteBlack = Color(0xFF080808)
private val HteSurface = Color(0xFF151515)
private val HteGold = Color(0xFFD7A83E)
private val HteMuted = Color(0xFFAAA39A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HteTheme { HighTechApp() } }
    }
}

@Composable
private fun HteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HteGold, onPrimary = HteBlack,
            background = HteBlack, onBackground = Color.White,
            surface = HteSurface, onSurface = Color.White,
            secondary = Color(0xFFC5C5C5)
        ),
        content = content
    )
}

private data class AppDestination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val destinations = listOf(
    AppDestination("Home", Icons.Rounded.Home),
    AppDestination("Discover", Icons.Rounded.Search),
    AppDestination("Videos", Icons.Rounded.VideoLibrary),
    AppDestination("Library", Icons.Rounded.LibraryMusic),
    AppDestination("Favourites", Icons.Rounded.FavoriteBorder)
)

@Composable
private fun HighTechApp() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = HteBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = HteSurface) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).background(
                            Brush.linearGradient(listOf(Color(0xFFFFE6A1), HteGold, Color(0xFF80500B))),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) { Text("HT", color = HteBlack, fontWeight = FontWeight.Black) }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("HIGH-TECH", color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text("ENTERTAINMENT", color = HteGold, fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("3.0", color = HteMuted, fontSize = 12.sp)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(
                        when (selectedTab) {
                            1 -> "Discover"
                            2 -> "Music videos"
                            3 -> "Your library"
                            4 -> "Favourites"
                            else -> "Music. Beats. Production. Beyond."
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Quality Sound. Global Vibes.", color = HteMuted)
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = HteSurface)
                ) {
                    Column(
                        modifier = Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFF342713), Color(0xFF19130B), HteSurface))
                        ).padding(22.dp)
                    ) {
                        Text("HIGH-TECH ENTERTAINMENT", color = HteGold, fontSize = 11.sp, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Your sound.\\nYour world.", color = Color.White, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(10.dp))
                        Text("Discover music, releases and videos from the High-Tech family.", color = HteMuted)
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { selectedTab = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = HteGold, contentColor = HteBlack)
                        ) { Text("Explore music", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(
                        if (selectedTab == 0) "Made for your next discovery" else destinations[selectedTab].label,
                        color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(10.dp))
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = HteSurface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp).background(Color(0xFF2A2112), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = HteGold) }
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text("Your music is getting ready", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Connect your account to load the catalogue.",
                                    color = HteMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Add to favourites", tint = HteGold)
                        }
                    }
                }
            }
            item {
                Text(
                    "NATIVE ANDROID FOUNDATION",
                    color = HteMuted, fontSize = 10.sp, letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }
    }
}
