package com.hightechentertainment.app

import android.net.Uri
import android.widget.Toast
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import io.github.jan.supabase.auth.auth
import com.hightechentertainment.app.data.AuthRepository
import com.hightechentertainment.app.data.EntertainmentCatalogRepository
import com.hightechentertainment.app.data.MemberRepository
import com.hightechentertainment.app.data.SupabaseClientProvider
import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.data.TrackRepository
import com.hightechentertainment.app.data.VideoDto
import com.hightechentertainment.app.media.HtePlaybackController
import com.hightechentertainment.app.media.OfflineDownloadManager
import com.hightechentertainment.app.media.OfflineDownloadRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var selectedTrackQueue by remember { mutableStateOf<List<TrackDto>?>(null) }
    var selectedVideo by remember { mutableStateOf<VideoDto?>(null) }
    var showVoiceAssistant by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val offlineManager = remember(context) { OfflineDownloadManager(context) }
    val settingsStore = remember(context) { AppSettingsStore(context) }
    var settingsRevision by remember { mutableStateOf(0) }
    val autoplayNext = remember(settingsRevision) { settingsStore.autoplayNext }
    var downloads by remember { mutableStateOf<List<OfflineDownloadRecord>>(emptyList()) }
    var favouriteTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
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
    LaunchedEffect(selectedTab, showVoiceAssistant) {
        if (selectedTab == UniverseTab.LIBRARY) downloads = withContext(Dispatchers.IO) { offlineManager.getDownloads() }
        if (configured) favouriteTrackIds = runCatching { withContext(Dispatchers.IO) { MemberRepository().getFavouriteTrackIds() } }.getOrDefault(emptySet())
    }

    fun toggleFavourite(track: TrackDto) {
        scope.launch {
            try {
                if (track.id in favouriteTrackIds) withContext(Dispatchers.IO) { MemberRepository().removeFavourite(track.id) }
                else withContext(Dispatchers.IO) { MemberRepository().addFavourite(track.id) }
                favouriteTrackIds = if (track.id in favouriteTrackIds) favouriteTrackIds - track.id else favouriteTrackIds + track.id
            } catch (error: Throwable) {
                Toast.makeText(context, error.message?.take(120) ?: "Sign in to save favourites.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startDownload(url: String?, title: String, mediaType: String) {
        scope.launch {
            try {
                if (url.isNullOrBlank()) error("This item has no download URL yet.")
                withContext(Dispatchers.IO) { offlineManager.enqueue(url, title, mediaType, allowMetered = settingsStore.networkPolicy != "WIFI_ONLY" && !settingsStore.dataSaver) }
                downloads = withContext(Dispatchers.IO) { offlineManager.getDownloads() }
                Toast.makeText(context, "Download started: $title", Toast.LENGTH_SHORT).show()
            } catch (error: Throwable) {
                Toast.makeText(context, error.message?.take(120) ?: "Download could not start.", Toast.LENGTH_LONG).show()
            }
        }
    }

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
            UniverseTopBar(onNotifications = { selectedTab = UniverseTab.ACCOUNT }, onSearch = { selectedTab = UniverseTab.DISCOVER }, onVoice = { showVoiceAssistant = true })
            when (selectedTab) {
                UniverseTab.HOME -> HomeScreen(
                    tracks = tracks, videos = videos, isLoading = isLoading,
                    onExplore = { selectedTab = UniverseTab.DISCOVER },
                    onVideos = { selectedTab = UniverseTab.VIDEOS },
                    onPlayTrack = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks else listOf(it) }, onPlayVideo = { selectedVideo = it },
                    onDownloadTrack = { startDownload(it.audioUrl, it.title, "audio") },
                    favouriteTrackIds = favouriteTrackIds, onToggleFavourite = ::toggleFavourite,
                    onDownloadVideo = { startDownload(it.videoUrl, it.title, "video") },
                    onRefresh = ::refreshCatalogue
                )
                UniverseTab.DISCOVER -> DiscoverScreen(
                    tracks = tracks, isLoading = isLoading, error = catalogueError,
                    onPlay = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks else listOf(it) }, onDownload = { startDownload(it.audioUrl, it.title, "audio") },
                    favouriteTrackIds = favouriteTrackIds, onToggleFavourite = ::toggleFavourite,
                    initialQuery = searchQuery, onQueryChanged = { searchQuery = it }, onRefresh = ::refreshCatalogue
                )
                UniverseTab.VIDEOS -> VideosScreen(
                    videos = videos, isLoading = isLoading, error = catalogueError,
                    onPlay = { selectedVideo = it }, onDownload = { startDownload(it.videoUrl, it.title, "video") }, onRefresh = ::refreshCatalogue
                )
                UniverseTab.LIBRARY -> LibraryScreen(
                    onSignIn = { selectedTab = UniverseTab.ACCOUNT },
                    downloads = downloads,
                    favouriteTracks = tracks.filter { it.id in favouriteTrackIds },
                    onPlayFavourite = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks.filter { track -> track.id in favouriteTrackIds } else listOf(it) },
                    onToggleFavourite = ::toggleFavourite,
                    onDownloadFavourite = { startDownload(it.audioUrl, it.title, "audio") },
                    onRemoveDownload = { record -> scope.launch { withContext(Dispatchers.IO) { offlineManager.remove(record.id) }; downloads = withContext(Dispatchers.IO) { offlineManager.getDownloads() } } },
                    onPlayDownload = { record ->
                        if (record.localUri.isNullOrBlank()) Toast.makeText(context, "This download is not ready yet.", Toast.LENGTH_SHORT).show()
                        else if (record.mediaType == "video") selectedVideo = VideoDto(record.id.toString(), record.title, videoUrl = record.localUri)
                        else {
                            val offlineTrack = TrackDto(record.id.toString(), record.title, artist = "Downloaded", audioUrl = record.localUri)
                            selectedTrack = offlineTrack
                            selectedTrackQueue = listOf(offlineTrack)
                        }
                    }
                )
                UniverseTab.ACCOUNT -> AccountScreen(configured = configured, onSettingsChanged = { settingsRevision++ })
            }
        }
    }

    selectedTrack?.let { AudioPlayerDialog(track = it, queue = selectedTrackQueue ?: if (autoplayNext) tracks else listOf(it), onDismiss = { selectedTrack = null; selectedTrackQueue = null }) }
    selectedVideo?.let { VideoPlayerDialog(video = it, onDismiss = { selectedVideo = null }) }
    if (showVoiceAssistant) {
        VoiceAssistantDialog(
            tracks = tracks,
            favouriteTracks = tracks.filter { it.id in favouriteTrackIds },
            onPlayFavourites = { favourites ->
                favourites.firstOrNull()?.let { first ->
                    selectedTrack = first
                    selectedTrackQueue = if (autoplayNext) favourites else listOf(first)
                }
            },
            onNavigate = { destination ->
                selectedTab = when (destination) {
                    "HOME" -> UniverseTab.HOME
                    "VIDEOS" -> UniverseTab.VIDEOS
                    "LIBRARY" -> UniverseTab.LIBRARY
                    "ACCOUNT" -> UniverseTab.ACCOUNT
                    else -> UniverseTab.DISCOVER
                }
            },
            onSearch = { searchQuery = it; selectedTab = UniverseTab.DISCOVER },
            onDismiss = { showVoiceAssistant = false }
        )
    }
}

@Composable
private fun UniverseTopBar(onNotifications: () -> Unit, onSearch: () -> Unit, onVoice: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ShellBackground).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("HTE UNIVERSE", color = ShellGold, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text("The Pride of Africa", color = ShellSilver, fontSize = 11.sp)
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search", tint = ShellSilver) }
        IconButton(onClick = onVoice) { Icon(Icons.Outlined.Mic, "Voice assistant", tint = ShellGold) }
        IconButton(onClick = onNotifications) { Icon(Icons.Outlined.NotificationsNone, "Notifications", tint = ShellSilver) }
    }
}

@Composable
private fun HomeScreen(
    tracks: List<TrackDto>, videos: List<VideoDto>, isLoading: Boolean,
    onExplore: () -> Unit, onVideos: () -> Unit,
    onPlayTrack: (TrackDto) -> Unit, onPlayVideo: (VideoDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit, onDownloadVideo: (VideoDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit, onRefresh: () -> Unit
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
            items(tracks.take(4), key = { it.id }) { track -> TrackRow(track = track, onPlay = { onPlayTrack(track) }, onDownload = { onDownloadTrack(track) }, isFavourite = track.id in favouriteTrackIds, onToggleFavourite = { onToggleFavourite(track) }) }
        }
        item { SectionHeading("LATEST VIDEOS", "See all", onVideos) }
        if (videos.isEmpty()) {
            item { Text("Published HTE videos will appear here.", color = ShellMuted, fontSize = 13.sp) }
        } else {
            items(videos.take(2), key = { it.id }) { video -> VideoRow(video = video, onPlay = { onPlayVideo(video) }, onDownload = { onDownloadVideo(video) }) }
        }
    }
}

@Composable
private fun DiscoverScreen(tracks: List<TrackDto>, isLoading: Boolean, error: String?, onPlay: (TrackDto) -> Unit, onDownload: (TrackDto) -> Unit, favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit, initialQuery: String, onQueryChanged: (String) -> Unit, onRefresh: () -> Unit) {
    var query by remember { mutableStateOf(initialQuery) }
    LaunchedEffect(initialQuery) { query = initialQuery }
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
        OutlinedTextField(value = query, onValueChange = { query = it; onQueryChanged(it) }, modifier = Modifier.fillMaxWidth(),
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
                items(filtered, key = { it.id }) { track -> TrackRow(track = track, onPlay = { onPlay(track) }, onDownload = { onDownload(track) }, isFavourite = track.id in favouriteTrackIds, onToggleFavourite = { onToggleFavourite(track) }) }
            }
        }
    }
}

@Composable
private fun VideosScreen(videos: List<VideoDto>, isLoading: Boolean, error: String?, onPlay: (VideoDto) -> Unit, onDownload: (VideoDto) -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Videos", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Watch HTE visuals, performances and stories.", color = ShellMuted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        if (error != null) Text(error, color = ShellGold, fontSize = 12.sp)
        if (videos.isEmpty()) {
            EmptyCatalogueState(if (isLoading) "Loading video catalogue…" else "No published videos yet", isLoading, onRefresh)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(videos, key = { it.id }) { video -> VideoRow(video = video, onPlay = { onPlay(video) }, onDownload = { onDownload(video) }) }
            }
        }
    }
}

@Composable
private fun TrackRow(track: TrackDto, onPlay: () -> Unit, onDownload: () -> Unit, isFavourite: Boolean, onToggleFavourite: () -> Unit) {
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
            IconButton(onClick = onToggleFavourite) { Icon(if (isFavourite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Toggle favourite", tint = if (isFavourite) ShellGold else ShellSilver) }
            IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, "Download ${track.title}", tint = ShellSilver) }
            IconButton(onClick = onPlay) { Icon(Icons.Outlined.PlayCircle, "Play ${track.title}", tint = ShellGold, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun VideoRow(video: VideoDto, onPlay: () -> Unit, onDownload: () -> Unit) {
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
            IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, "Download ${video.title}", tint = ShellSilver) }
            Icon(Icons.Outlined.PlayArrow, "Play video", tint = ShellGold)
        }
    }
}

@Composable
private fun LibraryScreen(
    onSignIn: () -> Unit,
    downloads: List<OfflineDownloadRecord>,
    favouriteTracks: List<TrackDto>,
    onPlayFavourite: (TrackDto) -> Unit,
    onToggleFavourite: (TrackDto) -> Unit,
    onDownloadFavourite: (TrackDto) -> Unit,
    onRemoveDownload: (OfflineDownloadRecord) -> Unit,
    onPlayDownload: (OfflineDownloadRecord) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Your Library", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Your music, saved your way.", color = ShellMuted, fontSize = 13.sp)
        }
        item { Text("FAVOURITES", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
        if (favouriteTracks.isEmpty()) {
            item { Text("Sign in and tap the heart on a track to save it here.", color = ShellMuted, fontSize = 12.sp) }
        } else {
            items(favouriteTracks, key = { it.id }) { track ->
                TrackRow(track = track, onPlay = { onPlayFavourite(track) }, onDownload = { onDownloadFavourite(track) }, isFavourite = true, onToggleFavourite = { onToggleFavourite(track) })
            }
        }
        item { LibraryTile(Icons.Outlined.QueueMusic, "Playlists", "Build and organise your listening") }
        item { LibraryTile(Icons.Outlined.History, "Listening history", "Return to what you played") }
        item { Text("OFFLINE DOWNLOADS", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
        if (downloads.isEmpty()) {
            item { Text("No offline downloads yet. Use the download icon beside a track or video to save it.", color = ShellMuted, fontSize = 12.sp) }
        } else {
            items(downloads, key = { it.id }) { record ->
                Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (record.mediaType == "video") Icons.Outlined.VideoLibrary else Icons.Outlined.MusicNote, null, tint = ShellGold)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${record.mediaType.uppercase()} · ${record.status}", color = ShellMuted, fontSize = 11.sp)
                        }
                        if (record.status == "Downloaded" && !record.localUri.isNullOrBlank()) {
                            IconButton(onClick = { onPlayDownload(record) }) { Icon(Icons.Outlined.PlayArrow, "Play offline", tint = ShellGold) }
                        }
                        IconButton(onClick = { onRemoveDownload(record) }) { Icon(Icons.Outlined.Delete, "Remove download", tint = ShellMuted) }
                    }
                }
            }
        }
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
private fun AccountScreen(configured: Boolean, onSettingsChanged: () -> Unit) {
    MemberCenter(configured = configured, onSettingsChanged = onSettingsChanged)
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
private fun AudioPlayerDialog(track: TrackDto, queue: List<TrackDto>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val validQueue = remember(queue, track.id, track.audioUrl) {
        if (track.audioUrl?.startsWith("http", ignoreCase = true) != true) emptyList() else queue.filter { candidate ->
            runCatching { Uri.parse(candidate.audioUrl.orEmpty()).scheme in listOf("https", "http") }.getOrDefault(false)
        }.map { candidate ->
            MediaItem.Builder()
                .setMediaId(candidate.id)
                .setUri(candidate.audioUrl!!)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(candidate.title).setArtist(candidate.artist).build())
                .build()
        }
    }
    val fallbackItem = remember(track.id) {
        MediaItem.Builder().setMediaId(track.id).setUri(track.audioUrl.orEmpty())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()).build()
    }

    DisposableEffect(context) {
        val future = HtePlaybackController.connect(context, { connected -> controller = connected }, { error ->
            playbackError = error.message?.take(160) ?: "The background player could not start."
        })
        onDispose {
            controller = null
            // Keep the app-scoped controller connected so background and voice controls remain available.
        }
    }

    LaunchedEffect(controller, track.id) {
        val player = controller ?: return@LaunchedEffect
        val items = if (validQueue.isNotEmpty()) validQueue else listOf(fallbackItem)
        val startIndex = items.indexOfFirst { it.mediaId == track.id }.coerceAtLeast(0)
        player.setMediaItems(items, startIndex, C.TIME_UNSET)
        player.prepare()
        player.play()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ShellBackground) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NOW PLAYING · BACKGROUND ENABLED", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close player", tint = ShellSilver) }
                }
                Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(track.artist, color = ShellMuted, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                if (controller != null) {
                    AndroidView(
                        factory = { PlayerView(it).apply { player = controller; useController = true } },
                        update = { it.player = controller },
                        modifier = Modifier.fillMaxWidth().height(190.dp)
                    )
                } else {
                    CircularProgressIndicator(color = ShellGold, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                playbackError?.let { Text(it, color = ShellGold, fontSize = 12.sp) }
                Text("Playback continues when you leave this screen. Use the Android media notification to control it.", color = ShellMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun VideoPlayerDialog(video: VideoDto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val validUrl = runCatching { Uri.parse(video.videoUrl).scheme in listOf("https", "http", "content", "file") }.getOrDefault(false)
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
