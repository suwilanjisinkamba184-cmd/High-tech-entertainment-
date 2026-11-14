package com.hightechentertainment.app

import android.net.Uri
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                catalogueError = "We couldn't load the catalogue. Check your connection and try again."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(configured) { refreshCatalogue() }
    LaunchedEffect(selectedTab) {
        if (selectedTab == UniverseTab.LIBRARY) {
            downloads = withContext(Dispatchers.IO) { offlineManager.getDownloads() }
        }
    }

    LaunchedEffect(configured, selectedTab) {
        if (configured && selectedTab == UniverseTab.LIBRARY) {
            favouriteTrackIds = runCatching {
                withContext(Dispatchers.IO) { MemberRepository().getFavouriteTrackIds() }
            }.getOrDefault(favouriteTrackIds)
        }
    }

    fun toggleFavourite(track: TrackDto) {
        scope.launch {
            try {
                if (track.id in favouriteTrackIds) withContext(Dispatchers.IO) { MemberRepository().removeFavourite(track.id) }
                else withContext(Dispatchers.IO) { MemberRepository().addFavourite(track.id) }
                favouriteTrackIds = if (track.id in favouriteTrackIds) favouriteTrackIds - track.id else favouriteTrackIds + track.id
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Toast.makeText(context, error.message?.take(120) ?: "Sign in to save favourites.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startDownload(url: String?, title: String, mediaType: String) {
        scope.launch {
            try {
                if (url.isNullOrBlank()) error("This item has no download URL yet.")
                withContext(Dispatchers.IO) { offlineManager.enqueue(url, title, mediaType, allowMetered = allowsMeteredMediaDownloads(settingsStore.networkPolicy, settingsStore.dataSaver)) }
                downloads = withContext(Dispatchers.IO) { offlineManager.getDownloads() }
                Toast.makeText(context, "Download started: $title", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
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
                    onVoice = { showVoiceAssistant = true },
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
    onExplore: () -> Unit, onVideos: () -> Unit, onVoice: () -> Unit,
    onPlayTrack: (TrackDto) -> Unit, onPlayVideo: (VideoDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit, onDownloadVideo: (VideoDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit, onRefresh: () -> Unit
) {
    Universe2026Home(
        tracks = tracks, videos = videos, isLoading = isLoading,
        onExplore = onExplore, onVideos = onVideos, onVoice = onVoice,
        onPlayTrack = onPlayTrack, onPlayVideo = onPlayVideo,
        onDownloadTrack = onDownloadTrack, onDownloadVideo = onDownloadVideo,
        favouriteTrackIds = favouriteTrackIds, onToggleFavourite = onToggleFavourite,
        onRefresh = onRefresh
    )
}

@Composable
private fun DiscoverScreen(
    tracks: List<TrackDto>, isLoading: Boolean, error: String?,
    onPlay: (TrackDto) -> Unit, onDownload: (TrackDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit,
    initialQuery: String, onQueryChanged: (String) -> Unit, onRefresh: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var selectedGenre by remember { mutableStateOf("All") }
    LaunchedEffect(initialQuery) { query = initialQuery }
    val genres = listOf("All") + tracks.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.distinct().sorted()
    val filtered = tracks.filter { track ->
        val q = query.trim()
        (q.isBlank() || track.title.contains(q, true) || track.artist.contains(q, true) || track.genre.orEmpty().contains(q, true)) &&
            (selectedGenre == "All" || track.genre.equals(selectedGenre, true))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ShellBackground),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("SIGNAL / DISCOVERY", color = ShellGold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("Find your frequency.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Explore the HTE sound across artists and genres.", color = ShellMuted, fontSize = 12.sp)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("THE HTE CATALOGUE", color = ShellGold, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = query, onValueChange = { query = it; onQueryChanged(it) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Search songs, artists or genres") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = ShellGold) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ShellGold, unfocusedBorderColor = Color.White.copy(alpha = .12f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = ShellGold
                        )
                    )
                    Text("FILTER BY GENRE", color = ShellMuted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(genres, key = { it }) { genre ->
                            FilterChip(
                                selected = selectedGenre == genre,
                                onClick = { selectedGenre = genre },
                                label = { Text(genre, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ShellGold.copy(alpha = .18f),
                                    selectedLabelColor = ShellGold,
                                    labelColor = ShellSilver
                                )
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CURATED FOR YOU", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${filtered.size} matching tracks", color = ShellMuted, fontSize = 11.sp)
                }
                TextButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(Icons.Outlined.Refresh, null, tint = ShellGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("Refresh", color = ShellGold, fontSize = 11.sp)
                }
            }
        }
        if (error != null) item { Text(error, color = ShellGold, fontSize = 12.sp) }
        if (filtered.isEmpty()) item {
            EmptyCatalogueState(if (isLoading) "Loading the HTE catalogue…" else "No tracks match this search yet.", isLoading, onRefresh)
        } else items(filtered, key = { it.id }) { track ->
            TrackRow(track, { onPlay(track) }, { onDownload(track) }, track.id in favouriteTrackIds, { onToggleFavourite(track) })
        }
    }
}

@Composable
private fun VideosScreen(videos: List<VideoDto>, isLoading: Boolean, error: String?, onPlay: (VideoDto) -> Unit, onDownload: (VideoDto) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ShellBackground),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("HTE VISUAL NETWORK", color = ShellGold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("Watch the culture.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Music videos, performances and stories from the HTE universe.", color = ShellMuted, fontSize = 12.sp)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).background(ShellGold.copy(alpha = .13f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PlayCircle, null, tint = ShellGold, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CINEMATIC PLAYBACK", color = ShellGold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                        Text("Fullscreen-ready video", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Adaptive streaming with retry controls", color = ShellMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LATEST RELEASES", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${videos.size} published videos", color = ShellMuted, fontSize = 11.sp)
                }
                TextButton(onClick = onRefresh, enabled = !isLoading) { Icon(Icons.Outlined.Refresh, null, tint = ShellGold); Text("Retry", color = ShellGold) }
            }
        }
        if (error != null) item { Text(error, color = ShellGold, fontSize = 12.sp) }
        if (videos.isEmpty()) item { EmptyCatalogueState(if (isLoading) "Loading videos…" else "No published videos yet.", isLoading, onRefresh) }
        else items(videos, key = { it.id }) { video -> VideoRow(video, { onPlay(video) }, { onDownload(video) }) }
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
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                    tint = if (isFavourite) ShellGold else ShellMuted
                )
            }
            IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, "Download ${track.title}", tint = ShellSilver) }
            IconButton(onClick = onPlay) { Icon(Icons.Outlined.PlayCircle, "Play ${track.title}", tint = ShellGold, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun VideoRow(video: VideoDto, onPlay: () -> Unit, onDownload: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(164.dp).background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF392D1D), Color(0xFF171B22), Color(0xFF0D1117))),
                RoundedCornerShape(13.dp)
            ), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.VideoLibrary, null, tint = ShellGold.copy(alpha = .72f), modifier = Modifier.size(52.dp))
                Box(Modifier.align(Alignment.Center).size(48.dp).background(Color.Black.copy(alpha = .52f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, "Play ${video.title}", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text("HTE VISUALS", color = ShellSilver, fontSize = 8.sp, letterSpacing = 1.6.sp, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(video.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(video.artist ?: "High-Tech Entertainment", color = ShellMuted, fontSize = 11.sp)
                }
                IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, "Download ${video.title}", tint = ShellSilver) }
                IconButton(onClick = onPlay) { Icon(Icons.Outlined.PlayCircle, "Play ${video.title}", tint = ShellGold, modifier = Modifier.size(28.dp)) }
            }
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
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("YOUR PERSONAL COLLECTION", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                Text("Your Library.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Saved music and downloads, ready when you are.", color = ShellMuted, fontSize = 12.sp)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LibraryMetric("${favouriteTracks.size}", "FAVOURITES", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(38.dp).background(Color.White.copy(alpha = .1f)))
                    LibraryMetric("${downloads.count { it.status == "Downloaded" }}", "OFFLINE", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(38.dp).background(Color.White.copy(alpha = .1f)))
                    LibraryMetric("${downloads.size}", "IN QUEUE", Modifier.weight(1f))
                }
            }
        }
        item { Text("FAVOURITES", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold) }
        if (favouriteTracks.isEmpty()) item {
            LibraryEmptyCard("No favourites saved", "Tap the heart on any published track to keep it in your collection.", onSignIn, "Open account")
        } else items(favouriteTracks, key = { it.id }) { track ->
            TrackRow(track, { onPlayFavourite(track) }, { onDownloadFavourite(track) }, true, { onToggleFavourite(track) })
        }
        item { Text("OFFLINE DOWNLOADS", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold) }
        if (downloads.isEmpty()) item {
            LibraryEmptyCard("Nothing downloaded yet", "Use the download action on a track or video to save it for offline playback.", onSignIn, "Manage account")
        } else items(downloads, key = { it.id }) { record ->
            Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(ShellGold.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(if (record.mediaType == "video") Icons.Outlined.VideoLibrary else Icons.Outlined.MusicNote, null, tint = ShellGold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(record.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${record.mediaType.uppercase()} · ${record.status}", color = ShellMuted, fontSize = 10.sp)
                    }
                    if (record.status == "Downloaded" && !record.localUri.isNullOrBlank()) IconButton(onClick = { onPlayDownload(record) }) { Icon(Icons.Outlined.PlayArrow, "Play offline", tint = ShellGold) }
                    IconButton(onClick = { onRemoveDownload(record) }) { Icon(Icons.Outlined.Delete, "Remove download", tint = ShellMuted) }
                }
            }
        }
    }
}

@Composable
private fun LibraryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ShellMuted, fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun LibraryEmptyCard(title: String, description: String, onAction: () -> Unit, action: String) {
    Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(description, color = ShellMuted, fontSize = 11.sp)
            TextButton(onClick = onAction) { Text(action, color = ShellGold) }
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
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    val validQueue = remember(queue, track.id, track.audioUrl) {
        queue.mapNotNull { candidate ->
            val url = candidate.audioUrl.orEmpty()
            if (!url.isPlayableUri()) return@mapNotNull null
            MediaItem.Builder().setMediaId(candidate.id).setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(candidate.title).setArtist(candidate.artist).build()).build()
        }
    }
    DisposableEffect(context) {
        var effectActive = true
        val connectionFuture = HtePlaybackController.connect(
            context,
            { mediaController -> if (effectActive) controller = mediaController },
            { error ->
                if (effectActive) {
                    playbackError = error.message?.take(160)
                        ?: "The background audio service could not start."
                }
            }
        )
        onDispose {
            effectActive = false
            controller = null
            HtePlaybackController.release(connectionFuture)
        }
    }
    LaunchedEffect(controller, track.id, track.audioUrl, validQueue) {
        val player = controller ?: return@LaunchedEffect
        val url = track.audioUrl.orEmpty()
        if (!url.isPlayableUri()) {
            playbackError = "This track does not have a valid audio stream URL yet."
            return@LaunchedEffect
        }
        val items = if (validQueue.isNotEmpty()) validQueue else listOf(
            MediaItem.Builder().setMediaId(track.id).setUri(track.audioUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()).build()
        )
        val startIndex = items.indexOfFirst { it.mediaId == track.id }.coerceAtLeast(0)
        runCatching { player.setMediaItems(items, startIndex, C.TIME_UNSET); player.prepare(); player.play() }
            .onFailure { playbackError = it.message?.take(160) ?: "Unable to start this track." }
    }
    LaunchedEffect(controller) {
        while (controller != null) {
            val player = controller
            position = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            isPlaying = player?.isPlaying == true
            isBuffering = player?.playbackState == Player.STATE_BUFFERING
            delay(400)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ShellBackground) {
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("HTE AUDIO / NOW PLAYING", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
                        Text("Premium listening session", color = ShellMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.KeyboardArrowDown, "Minimise player", tint = ShellSilver, modifier = Modifier.size(28.dp)) }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(236.dp).background(
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF58452B), Color(0xFF1A2028), Color(0xFF101216))),
                        RoundedCornerShape(28.dp)
                    ), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Headphones, null, tint = ShellGold.copy(alpha = .88f), modifier = Modifier.size(104.dp))
                        Text("HIGH-TECH ENTERTAINMENT", color = ShellSilver.copy(alpha = .75f), fontSize = 8.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp))
                    }
                    Text(track.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = ShellSilver, fontSize = 13.sp)
                    if (isBuffering) LinearProgressIndicator(Modifier.fillMaxWidth(.72f), color = ShellGold)
                    Slider(
                        value = position.coerceAtMost(duration.coerceAtLeast(1L)).toFloat(),
                        onValueChange = { controller?.seekTo(it.toLong()) },
                        valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                        enabled = controller != null && duration > 0,
                        colors = SliderDefaults.colors(thumbColor = ShellGold, activeTrackColor = ShellGold, inactiveTrackColor = ShellMuted.copy(alpha = .35f))
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatMediaTime(position), color = ShellMuted, fontSize = 10.sp)
                        Text(formatMediaTime(duration), color = ShellMuted, fontSize = 10.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = controller != null) { Icon(Icons.Outlined.SkipPrevious, "Previous track", tint = ShellSilver, modifier = Modifier.size(34.dp)) }
                        Spacer(Modifier.width(18.dp))
                        FilledIconButton(
                            onClick = { if (isPlaying) controller?.pause() else controller?.play() },
                            enabled = controller != null,
                            modifier = Modifier.size(68.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = ShellGold, contentColor = ShellBackground)
                        ) { Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (isPlaying) "Pause" else "Play", modifier = Modifier.size(34.dp)) }
                        Spacer(Modifier.width(18.dp))
                        IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = controller != null) { Icon(Icons.Outlined.SkipNext, "Next track", tint = ShellSilver, modifier = Modifier.size(34.dp)) }
                    }
                    Text("QUEUE · ${validQueue.size.coerceAtLeast(1)} TRACKS", color = ShellMuted, fontSize = 9.sp, letterSpacing = 1.4.sp)
                    playbackError?.let { Text(it, color = Color(0xFFFFB3A8), fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
                Text("Playback stays active in the background. Use the Android media notification to control it outside HTE Universe.", color = ShellMuted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

private fun String.isPlayableUri(): Boolean {
    val uri = runCatching { Uri.parse(trim()) }.getOrNull() ?: return false
    return when (uri.scheme?.lowercase()) {
        "https", "http" -> !uri.host.isNullOrBlank()
        "content", "file" -> !uri.path.isNullOrBlank()
        else -> false
    }
}

private fun formatMediaTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@UnstableApi
@Composable
private fun VideoPlayerDialog(video: VideoDto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose { if (previousOrientation != null) activity?.requestedOrientation = previousOrientation }
    }
    val validUrl = remember(video.videoUrl) {
        runCatching {
            val uri = Uri.parse(video.videoUrl)
            uri.scheme in listOf("https", "http", "content", "file") && !video.videoUrl.isBlank()
        }.getOrDefault(false)
    }
    val player = remember(video.id, video.videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            if (validUrl) {
                setMediaItem(MediaItem.fromUri(video.videoUrl))
                prepare()
                playWhenReady = true
            }
        }
    }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Video playback failed. Check the connection or retry the stream."
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) playbackError = null
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(video.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(video.artist ?: "High-Tech Entertainment", color = ShellMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close video", tint = Color.White) }
                }
                Spacer(Modifier.height(8.dp))
                if (validUrl) {
                    AndroidView(
                        factory = { viewContext -> PlayerView(viewContext).apply {
                            this.player = player
                            useController = true
                            controllerAutoShow = true
                            controllerHideOnTouch = true
                            controllerShowTimeoutMs = 3500
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            keepScreenOn = true
                        } },
                        update = { view -> view.player = player },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("This video does not have a valid playback URL.", color = ShellGold)
                    }
                }
                if (isBuffering) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ShellGold)
                playbackError?.let { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, color = ShellSilver, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { playbackError = null; player.prepare(); player.playWhenReady = true }) { Text("Retry", color = ShellGold) }
                        }
                    }
                }
            }
        }
    }
}


private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
