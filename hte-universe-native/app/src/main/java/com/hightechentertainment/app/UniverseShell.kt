package com.hightechentertainment.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.jan.supabase.auth.auth
import com.hightechentertainment.app.data.AuthRepository
import com.hightechentertainment.app.data.EntertainmentCatalogRepository
import com.hightechentertainment.app.data.SupabaseClientProvider
import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.data.TrackRepository
import com.hightechentertainment.app.data.VideoDto
import kotlinx.coroutines.launch

private val ShellGold = Color(0xFFD6B15E)
private val ShellSilver = Color(0xFFC4CAD2)
private val ShellMuted = Color(0xFF8B929D)
private val ShellPanel = Color(0xFF121418)
private val ShellBackground = Color(0xFF08090B)

private enum class UniverseTab(val label: String) {
    HOME("Home"), DISCOVER("Discover"), VIDEOS("Videos"), LIBRARY("Library"), ACCOUNT("Account")
}

@Composable
fun UniverseShell() {
    var selectedTab by remember { mutableStateOf(UniverseTab.HOME) }
    var tracks by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var videos by remember { mutableStateOf<List<VideoDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var catalogueError by remember { mutableStateOf<String?>(null) }
    var selectedTrack by remember { mutableStateOf<TrackDto?>(null) }
    var selectedVideo by remember { mutableStateOf<VideoDto?>(null) }
    val scope = rememberCoroutineScope()
    val configured = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    fun refreshCatalogue() {
        scope.launch {
            if (!configured) {
                catalogueError = "The catalogue is not connected in this build. A secure Supabase URL and publishable key are required."
                return@launch
            }
            isLoading = true
            catalogueError = null
            try {
                tracks = TrackRepository().getPublishedTracks()
                videos = EntertainmentCatalogRepository().getPublishedVideos()
            } catch (_: Throwable) {
                catalogueError = "We couldn't load the catalogue. Check your connection and try again."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(configured) { refreshCatalogue() }

    Scaffold(
        containerColor = ShellBackground,
        bottomBar = {
            NavigationBar(containerColor = ShellPanel, contentColor = ShellGold) {
                UniverseTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        UniverseTab.HOME -> Icons.Outlined.Home
                        UniverseTab.DISCOVER -> Icons.Outlined.Explore
                        UniverseTab.VIDEOS -> Icons.Outlined.VideoLibrary
                        UniverseTab.LIBRARY -> Icons.Outlined.LibraryMusic
                        UniverseTab.ACCOUNT -> Icons.Outlined.Person
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ShellGold, selectedTextColor = ShellGold,
                            unselectedIconColor = ShellMuted, unselectedTextColor = ShellMuted,
                            indicatorColor = ShellGold.copy(alpha = .12f)
                        )
                    )
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            UniverseTopBar(onNotifications = { selectedTab = UniverseTab.ACCOUNT }, onSearch = { selectedTab = UniverseTab.DISCOVER })
            when (selectedTab) {
                UniverseTab.HOME -> HomeScreen(
                    tracks = tracks, videos = videos, isLoading = isLoading,
                    onExplore = { selectedTab = UniverseTab.DISCOVER },
                    onVideos = { selectedTab = UniverseTab.VIDEOS },
                    onPlayTrack = { selectedTrack = it }, onPlayVideo = { selectedVideo = it },
                    onRefresh = ::refreshCatalogue
                )
                UniverseTab.DISCOVER -> DiscoverScreen(
                    tracks = tracks, isLoading = isLoading, error = catalogueError,
                    onPlay = { selectedTrack = it }, onRefresh = ::refreshCatalogue
                )
                UniverseTab.VIDEOS -> VideosScreen(
                    videos = videos, isLoading = isLoading, error = catalogueError,
                    onPlay = { selectedVideo = it }, onRefresh = ::refreshCatalogue
                )
                UniverseTab.LIBRARY -> LibraryScreen(onSignIn = { selectedTab = UniverseTab.ACCOUNT })
                UniverseTab.ACCOUNT -> AccountScreen(configured = configured)
            }
        }
    }

    selectedTrack?.let { AudioPlayerDialog(track = it, onDismiss = { selectedTrack = null }) }
    selectedVideo?.let { VideoPlayerDialog(video = it, onDismiss = { selectedVideo = null }) }
}

@Composable
private fun UniverseTopBar(onNotifications: () -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ShellBackground).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("HTE UNIVERSE", color = ShellGold, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text("The Pride of Africa", color = ShellSilver, fontSize = 11.sp)
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search", tint = ShellSilver) }
        IconButton(onClick = onNotifications) { Icon(Icons.Outlined.NotificationsNone, "Notifications", tint = ShellSilver) }
    }
}

@Composable
private fun HomeScreen(
    tracks: List<TrackDto>, videos: List<VideoDto>, isLoading: Boolean,
    onExplore: () -> Unit, onVideos: () -> Unit,
    onPlayTrack: (TrackDto) -> Unit, onPlayVideo: (VideoDto) -> Unit, onRefresh: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("YOUR UNIVERSE, YOUR SOUND", color = ShellMuted, fontSize = 10.sp, letterSpacing = 2.sp)
            Text("Beyond the ordinary.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("HIGH-TECH ENTERTAINMENT", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Music. Beats. Production. Beyond.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Discover releases, artists and visual entertainment in one connected space.", color = ShellSilver, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onExplore, colors = ButtonDefaults.buttonColors(containerColor = ShellGold, contentColor = ShellBackground)) { Text("Explore music") }
                        OutlinedButton(onClick = onVideos, colors = ButtonDefaults.outlinedButtonColors(contentColor = ShellSilver)) { Text("Watch videos") }
                    }
                }
            }
        }
        item { SectionHeading("FEATURED MUSIC", "See all", onExplore) }
        if (tracks.isEmpty()) {
            item { EmptyCatalogueState("No published tracks yet", isLoading, onRefresh) }
        } else {
            items(tracks.take(4), key = { it.id }) { track -> TrackRow(track = track, onPlay = { onPlayTrack(track) }) }
        }
        item { SectionHeading("LATEST VIDEOS", "See all", onVideos) }
        if (videos.isEmpty()) {
            item { Text("Published HTE videos will appear here.", color = ShellMuted, fontSize = 13.sp) }
        } else {
            items(videos.take(2), key = { it.id }) { video -> VideoRow(video = video, onPlay = { onPlayVideo(video) }) }
        }
    }
}

@Composable
private fun DiscoverScreen(tracks: List<TrackDto>, isLoading: Boolean, error: String?, onPlay: (TrackDto) -> Unit, onRefresh: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("All") }
    val genres = listOf("All") + tracks.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.distinct().sorted()
    val filtered = tracks.filter { track ->
        val matchesQuery = query.isBlank() || track.title.contains(query, true) || track.artist.contains(query, true)
        val matchesGenre = selectedGenre == "All" || track.genre.equals(selectedGenre, true)
        matchesQuery && matchesGenre
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Discover", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Find your next favourite sound.", color = ShellMuted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search tracks or artists") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            genres.take(6).forEach { genre ->
                FilterChip(selected = selectedGenre == genre, onClick = { selectedGenre = genre }, label = { Text(genre) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (error != null) Text(error, color = ShellGold, fontSize = 12.sp)
        if (filtered.isEmpty()) {
            EmptyCatalogueState(if (isLoading) "Loading music catalogue…" else "No matching music found", isLoading, onRefresh)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(filtered, key = { it.id }) { track -> TrackRow(track = track, onPlay = { onPlay(track) }) }
            }
        }
    }
}

@Composable
private fun VideosScreen(videos: List<VideoDto>, isLoading: Boolean, error: String?, onPlay: (VideoDto) -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Videos", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Watch HTE visuals, performances and stories.", color = ShellMuted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        if (error != null) Text(error, color = ShellGold, fontSize = 12.sp)
        if (videos.isEmpty()) {
            EmptyCatalogueState(if (isLoading) "Loading video catalogue…" else "No published videos yet", isLoading, onRefresh)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(videos, key = { it.id }) { video -> VideoRow(video = video, onPlay = { onPlay(video) }) }
            }
        }
    }
}

@Composable
private fun TrackRow(track: TrackDto, onPlay: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(ShellGold.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MusicNote, null, tint = ShellGold, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = ShellMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                track.genre?.let { Text(it, color = ShellGold, fontSize = 10.sp) }
            }
            IconButton(onClick = onPlay) { Icon(Icons.Outlined.PlayCircle, "Play ${track.title}", tint = ShellGold, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun VideoRow(video: VideoDto, onPlay: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(62.dp, 52.dp).background(ShellGold.copy(alpha = .12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PlayCircle, null, tint = ShellGold, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.artist ?: "High-Tech Entertainment", color = ShellMuted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.PlayArrow, "Play video", tint = ShellGold)
        }
    }
}

@Composable
private fun LibraryScreen(onSignIn: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Your Library", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Your music, saved your way.", color = ShellMuted, fontSize = 13.sp)
        }
        item { LibraryTile(Icons.Outlined.FavoriteBorder, "Favourites", "Your saved tracks and artists") }
        item { LibraryTile(Icons.Outlined.Download, "Offline downloads", "Music and videos available without data") }
        item { LibraryTile(Icons.Outlined.QueueMusic, "Playlists", "Build and organise your listening") }
        item { LibraryTile(Icons.Outlined.History, "Listening history", "Return to what you played") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("Sign in to sync your library", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Favourites, playlists and listening history are stored in your account.", color = ShellMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onSignIn) { Text("Open account") }
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ShellGold, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = ShellMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AccountScreen(configured: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    var isSignedIn by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val auth = remember(configured) { if (configured) runCatching { SupabaseClientProvider.client.auth }.getOrNull() else null }

    LaunchedEffect(auth) {
        if (auth != null) {
            isSignedIn = auth.currentUserOrNull() != null
            auth.sessionStatus.collect { isSignedIn = auth.currentUserOrNull() != null }
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Account", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Your HTE Universe membership.", color = ShellMuted, fontSize = 13.sp)
        }
        if (!configured || auth == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Icon(Icons.Outlined.Lock, null, tint = ShellGold, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Secure account connection required", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("This build needs the Supabase project URL and publishable key supplied through protected build configuration. No secret service key belongs in the APK.", color = ShellMuted, fontSize = 12.sp)
                    }
                }
            }
        } else if (isSignedIn) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Icon(Icons.Outlined.AccountCircle, null, tint = ShellGold, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("You're signed in", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(auth.currentUserOrNull()?.email ?: "HTE member", color = ShellSilver, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(onClick = {
                            scope.launch { runCatching { AuthRepository().signOut() }.onSuccess { statusMessage = "You have signed out." }.onFailure { statusMessage = "Sign-out failed. Try again." } }
                        }) { Icon(Icons.Outlined.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign out") }
                    }
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (createAccount) "Create your HTE account" else "Welcome back", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true)
                        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
                        Button(onClick = {
                            scope.launch {
                                statusMessage = null
                                runCatching {
                                    if (createAccount) AuthRepository().signUp(email, password) else AuthRepository().signIn(email, password)
                                }.onSuccess {
                                    isSignedIn = auth.currentUserOrNull() != null
                                    statusMessage = if (createAccount && !isSignedIn) "Account created. Check your email if confirmation is required." else "Account request completed."
                                }.onFailure { statusMessage = it.message?.take(180) ?: "Authentication failed. Please try again." }
                            }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ShellGold, contentColor = ShellBackground)) {
                            Text(if (createAccount) "Create account" else "Sign in")
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching { AuthRepository().signInWithGoogle() }
                                    .onSuccess { statusMessage = "Continue Google sign-in in your browser, then return to HTE Universe." }
                                    .onFailure { statusMessage = it.message?.take(180) ?: "Google sign-in could not start." }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
                        TextButton(onClick = { createAccount = !createAccount; statusMessage = null }) {
                            Text(if (createAccount) "Already have an account? Sign in" else "New to HTE? Create an account", color = ShellGold)
                        }
                    }
                }
            }
        }
        if (statusMessage != null) item { Text(statusMessage!!, color = ShellGold, fontSize = 12.sp) }
        item {
            Text("Settings, bookings, profile details and Founder Studio access will appear here as their account services are connected.", color = ShellMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyCatalogueState(message: String, isLoading: Boolean, onRefresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isLoading) CircularProgressIndicator(color = ShellGold, modifier = Modifier.size(24.dp))
            else Icon(Icons.Outlined.MusicOff, null, tint = ShellMuted, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(message, color = ShellSilver, fontSize = 12.sp)
            if (!isLoading) TextButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Retry") }
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ShellSilver, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action, color = ShellGold, fontSize = 11.sp) }
    }
}

@Composable
private fun AudioPlayerDialog(track: TrackDto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val validUrl = runCatching { Uri.parse(track.audioUrl.orEmpty()).scheme in listOf("https", "http") }.getOrDefault(false)
    val player = remember(track.id) {
        ExoPlayer.Builder(context).build().apply {
            if (validUrl) { setMediaItem(MediaItem.fromUri(track.audioUrl!!)); prepare(); playWhenReady = true }
        }
    }
    var playbackError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playbackError = "Playback failed. Check the audio URL or network connection." }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ShellBackground) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NOW PLAYING", color = ShellGold, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close player", tint = ShellSilver) }
                }
                Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(track.artist, color = ShellMuted, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                if (validUrl) AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxWidth().height(180.dp))
                else Text("This track does not have a valid audio URL yet.", color = ShellGold)
                playbackError?.let { Text(it, color = ShellGold, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun VideoPlayerDialog(video: VideoDto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val validUrl = runCatching { Uri.parse(video.videoUrl).scheme in listOf("https", "http") }.getOrDefault(false)
    val player = remember(video.id) {
        ExoPlayer.Builder(context).build().apply {
            if (validUrl) { setMediaItem(MediaItem.fromUri(video.videoUrl)); prepare(); playWhenReady = true }
        }
    }
    var playbackError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playbackError = "Video could not play. Check your connection and try again." }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(video.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(video.artist ?: "High-Tech Entertainment", color = ShellMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close video", tint = Color.White) }
                }
                if (validUrl) AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxWidth().weight(1f))
                else Text("This video does not have a valid playback URL.", color = ShellGold)
                playbackError?.let { Text(it, color = ShellGold, fontSize = 12.sp) }
            }
        }
    }
}
