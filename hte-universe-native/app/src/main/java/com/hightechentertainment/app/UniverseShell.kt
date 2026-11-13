package com.hightechentertainment.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
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
import androidx.core.net.toUri
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
import com.hightechentertainment.app.player.Media3PlayerController
import com.hightechentertainment.app.player.PlayerHost
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
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var playerExpanded by remember { mutableStateOf(false) }
    val playerUiController = remember(mediaController) { mediaController?.let { Media3PlayerController(it, scope) } }
    DisposableEffect(context) {
        HtePlaybackController.connect(
            context,
            onConnected = { mediaController = it },
            onError = { error -> Toast.makeText(context, error.message?.take(120) ?: "The background player could not start.", Toast.LENGTH_LONG).show() },
        )
        onDispose { }
    }
    DisposableEffect(playerUiController) {
        onDispose { playerUiController?.release() }
    }
    LaunchedEffect(mediaController, selectedTrack?.id, selectedTrackQueue) {
        val player = mediaController ?: return@LaunchedEffect
        val selected = selectedTrack ?: return@LaunchedEffect
        val candidates = (selectedTrackQueue ?: listOf(selected)).let { queue ->
            if (queue.any { it.id == selected.id }) queue else listOf(selected) + queue
        }
        val items = candidates.mapNotNull { candidate ->
            val uri = candidate.audioUrl?.takeIf(String::isNotBlank)?.toUri() ?: return@mapNotNull null
            if (uri.scheme !in listOf("http", "https", "file", "content")) return@mapNotNull null
            MediaItem.Builder()
                .setMediaId(candidate.id)
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(candidate.title).setArtist(candidate.artist).setArtworkUri(candidate.coverUrl?.toUri()).build())
                .build()
        }
        if (items.isEmpty()) {
            Toast.makeText(context, "This track does not have a valid audio URL.", Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        val startIndex = items.indexOfFirst { it.mediaId == selected.id }.coerceAtLeast(0)
        player.setMediaItems(items, startIndex, C.TIME_UNSET)
        player.prepare()
        player.play()
        playerExpanded = true
    }
    val offlineManager = remember(context) { OfflineDownloadManager(context) }
    val settingsStore = remember(context) { AppSettingsStore(context) }
    var settingsRevision by remember { mutableIntStateOf(0) }
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

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = ShellBackground,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0D0F12), contentColor = ShellGold, tonalElevation = 0.dp) {
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
                        onPlayTrack = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks else listOf(it); playerExpanded = true }, onPlayVideo = { selectedVideo = it },
                        onDownloadTrack = { startDownload(it.audioUrl, it.title, "audio") },
                        favouriteTrackIds = favouriteTrackIds, onToggleFavourite = ::toggleFavourite,
                        onDownloadVideo = { startDownload(it.videoUrl, it.title, "video") },
                        onRefresh = ::refreshCatalogue
                    )
                    UniverseTab.DISCOVER -> DiscoverScreen(
                        tracks = tracks, isLoading = isLoading, error = catalogueError,
                        onPlay = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks else listOf(it); playerExpanded = true }, onDownload = { startDownload(it.audioUrl, it.title, "audio") },
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
                        onPlayFavourite = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks.filter { track -> track.id in favouriteTrackIds } else listOf(it); playerExpanded = true },
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
                                playerExpanded = true
                            }
                        }
                    )
                    UniverseTab.ACCOUNT -> AccountScreen(configured = configured, onSettingsChanged = { settingsRevision++ })
                }
            }
        }
        playerUiController?.let { uiController ->
            PlayerHost(
                controller = uiController,
                expanded = playerExpanded,
                onExpandedChange = { playerExpanded = it },
                modifier = Modifier.align(Alignment.BottomCenter),
                collapsedBottomInset = 80.dp,
                artwork = { url, artworkModifier ->
                    if (!url.isNullOrBlank()) {
                        AsyncImage(model = url, contentDescription = "Track artwork", contentScale = ContentScale.Crop, modifier = artworkModifier)
                    } else {
                        Box(artworkModifier.background(Brush.linearGradient(listOf(Color(0xFF493719), ShellPanel))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Album, null, tint = ShellGold, modifier = Modifier.size(30.dp))
                        }
                    }
                },
            )
        }
    }

    selectedVideo?.let { VideoPlayerDialog(video = it, onDismiss = { selectedVideo = null }) }
    if (showVoiceAssistant) {
        VoiceAssistantDialog(
            tracks = tracks,
            favouriteTracks = tracks.filter { it.id in favouriteTrackIds },
            onPlayFavourites = { favourites ->
                favourites.firstOrNull()?.let { first ->
                    selectedTrack = first
                    selectedTrackQueue = if (autoplayNext) favourites else listOf(first)
                    playerExpanded = true
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
        Image(
            painter = painterResource(R.drawable.ht_logo),
            contentDescription = "High-Tech Entertainment",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(42.dp).padding(end = 8.dp)
        )
        Column(Modifier.weight(1f)) {
            Text("HTE UNIVERSE", color = ShellGold, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text("THE PRIDE OF AFRICA", color = ShellSilver, fontSize = 9.sp, letterSpacing = 1.2.sp)
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
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("WELCOME TO YOUR UNIVERSE", color = ShellGold, fontSize = 9.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.Bold)
                Text("Beyond the ordinary.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Music, visuals and culture — all in one place.", color = ShellMuted, fontSize = 12.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ShellPanel)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("MUSIC LIBRARY", color = ShellMuted, fontSize = 8.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                        Text(tracks.size.toString().padStart(2, '0'), color = ShellGold, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("published tracks", color = ShellMuted, fontSize = 9.sp)
                    }
                }
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ShellPanel)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("VISUAL WORLD", color = ShellMuted, fontSize = 8.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                        Text(videos.size.toString().padStart(2, '0'), color = ShellGold, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("published videos", color = ShellMuted, fontSize = 9.sp)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = ShellPanel)) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF352713), Color(0xFF17130D), Color(0xFF101216)))).padding(20.dp)) {
                    Image(painter = painterResource(R.drawable.ic_hte_crown), contentDescription = null,
                        modifier = Modifier.align(Alignment.TopEnd).size(112.dp).alpha(.20f))
                    Column(Modifier.fillMaxWidth(.88f)) {
                        Text("HIGH-TECH ENTERTAINMENT", color = ShellGold, fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(9.dp))
                        Text("Music. Beats. Production. Beyond.", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Discover new sounds, watch exclusive visuals and experience the HTE universe.", color = ShellSilver, fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(17.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Button(onClick = onExplore, colors = ButtonDefaults.buttonColors(containerColor = ShellGold, contentColor = ShellBackground), shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Outlined.Explore, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Explore music", fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = onVideos, border = androidx.compose.foundation.BorderStroke(1.dp, ShellGold.copy(alpha = .5f)), shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Outlined.PlayCircle, null, tint = ShellGold, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Watch", color = ShellSilver, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Afrobeat", "Dancehall", "Drill", "Gospel").forEach { genre ->
                    Surface(shape = RoundedCornerShape(50), color = ShellPanel, border = androidx.compose.foundation.BorderStroke(1.dp, ShellGold.copy(alpha = .20f))) {
                        Text(genre, color = ShellSilver, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
        item { SectionHeading("FEATURED MUSIC", "See all", onExplore) }
        if (tracks.isEmpty()) {
            item { EmptyCatalogueState("No published tracks yet", isLoading, onRefresh) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    items(tracks.take(10), key = { it.id }) { track ->
                        MusicFeatureCard(track, track.id in favouriteTrackIds, { onPlayTrack(track) }, { onToggleFavourite(track) }, { onDownloadTrack(track) })
                    }
                }
            }
        }
        item { SectionHeading("LATEST VIDEOS", "See all", onVideos) }
        if (videos.isEmpty()) {
            item { Text("Published HTE videos will appear here.", color = ShellMuted, fontSize = 12.sp) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    items(videos.take(8), key = { it.id }) { video -> VideoFeatureCard(video, { onPlayVideo(video) }, { onDownloadVideo(video) }) }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(R.drawable.ic_hte_crown), contentDescription = null, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("THE HTE EXPERIENCE", color = ShellGold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                        Text("A universe built around sound and creativity.", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Outlined.AutoAwesome, null, tint = ShellGold)
                }
            }
        }
    }
}

@Composable
private fun MusicFeatureCard(track: TrackDto, favourite: Boolean, onPlay: () -> Unit, onFavourite: () -> Unit, onDownload: () -> Unit) {
    Card(modifier = Modifier.width(158.dp), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = ShellPanel)) {
        Column {
            Box(Modifier.fillMaxWidth().height(142.dp).background(Brush.linearGradient(listOf(Color(0xFF59401D), Color(0xFF17130D))))) {
                if (!track.coverUrl.isNullOrBlank()) AsyncImage(model = track.coverUrl, contentDescription = "${track.title} cover artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Album, null, tint = ShellGold.copy(alpha = .85f), modifier = Modifier.size(58.dp)) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .58f)))))
                IconButton(onClick = onFavourite, modifier = Modifier.align(Alignment.TopEnd)) { Icon(if (favourite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Save ${track.title}", tint = if (favourite) ShellGold else Color.White) }
                Surface(onClick = onPlay, shape = RoundedCornerShape(50), color = ShellGold, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    Icon(Icons.Outlined.PlayArrow, "Play ${track.title}", tint = ShellBackground, modifier = Modifier.padding(7.dp).size(19.dp))
                }
                IconButton(onClick = onDownload, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Outlined.Download, "Download ${track.title}", tint = Color.White) }
            }
            Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = ShellMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                track.genre?.let { Text(it.uppercase(), color = ShellGold, fontSize = 8.sp, letterSpacing = 1.sp) }
            }
        }
    }
}

@Composable
private fun VideoFeatureCard(video: VideoDto, onPlay: () -> Unit, onDownload: () -> Unit) {
    Card(modifier = Modifier.width(218.dp), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = ShellPanel)) {
        Column {
            Box(Modifier.fillMaxWidth().height(126.dp).background(Brush.linearGradient(listOf(Color(0xFF44331A), Color(0xFF15171B))))) {
                if (!video.thumbnailUrl.isNullOrBlank()) AsyncImage(model = video.thumbnailUrl, contentDescription = "${video.title} thumbnail", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Movie, null, tint = ShellGold, modifier = Modifier.size(48.dp)) }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f)))
                Surface(onClick = onPlay, shape = RoundedCornerShape(50), color = ShellGold.copy(alpha = .96f), modifier = Modifier.align(Alignment.Center)) {
                    Icon(Icons.Outlined.PlayArrow, "Watch ${video.title}", tint = ShellBackground, modifier = Modifier.padding(9.dp).size(22.dp))
                }
                IconButton(onClick = onDownload, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Outlined.Download, "Download ${video.title}", tint = Color.White) }
            }
            Column(Modifier.padding(11.dp)) {
                Text(video.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(video.artist ?: "High-Tech Entertainment", color = ShellMuted, fontSize = 10.sp)
            }
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(genres.take(12)) { genre ->
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
        Text("THE VISUAL UNIVERSE", color = ShellGold, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text("Videos", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Music videos, performances and stories from HTE.", color = ShellMuted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ShellPanel)) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF302315), Color(0xFF15171B)))).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WATCH. FEEL. REPLAY.", color = ShellGold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("The sound has a visual side.", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${videos.size} videos in the HTE catalogue", color = ShellSilver, fontSize = 10.sp)
                }
                Icon(Icons.Outlined.Movie, null, tint = ShellGold, modifier = Modifier.size(38.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
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
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(13.dp)).background(ShellGold.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                if (!track.coverUrl.isNullOrBlank()) AsyncImage(model = track.coverUrl, contentDescription = "${track.title} cover artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Outlined.MusicNote, null, tint = ShellGold, modifier = Modifier.size(25.dp))
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
            Box(Modifier.size(76.dp, 62.dp).clip(RoundedCornerShape(11.dp)).background(ShellGold.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                if (!video.thumbnailUrl.isNullOrBlank()) AsyncImage(model = video.thumbnailUrl, contentDescription = "${video.title} thumbnail", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Outlined.PlayCircle, null, tint = ShellGold, modifier = Modifier.size(30.dp))
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
            Text("YOUR PERSONAL SPACE", color = ShellGold, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text("Your Library", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
            Text("Your music, saved your way.", color = ShellMuted, fontSize = 12.sp)
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ShellPanel)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SAVED COLLECTION", color = ShellGold, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                        Text("${favouriteTracks.size} favourites  ·  ${downloads.size} downloads", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Your collection stays with your HTE account and device.", color = ShellMuted, fontSize = 10.sp)
                    }
                    Icon(Icons.Outlined.LibraryMusic, null, tint = ShellGold, modifier = Modifier.size(34.dp))
                }
            }
        }
        item { Text("FAVOURITES", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
        if (favouriteTracks.isEmpty()) {
            item { Text("Sign in and tap the heart on a track to save it here.", color = ShellMuted, fontSize = 12.sp) }
        } else {
            items(favouriteTracks, key = { it.id }) { track ->
                TrackRow(track = track, onPlay = { onPlayFavourite(track) }, onDownload = { onDownloadFavourite(track) }, isFavourite = true, onToggleFavourite = { onToggleFavourite(track) })
            }
        }
        item { LibraryTile(Icons.AutoMirrored.Outlined.QueueMusic, "Playlists", "Build and organise your listening") }
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
    val playerScope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val validQueue = remember(queue, track.id, track.audioUrl) {
        if (track.audioUrl?.startsWith("http", ignoreCase = true) != true) emptyList() else queue.filter { candidate ->
            runCatching { candidate.audioUrl.orEmpty().toUri().scheme in listOf("https", "http") }.getOrDefault(false)
        }.map { candidate ->
            MediaItem.Builder()
                .setMediaId(candidate.id)
                .setUri(candidate.audioUrl!!)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(candidate.title).setArtist(candidate.artist).setArtworkUri(candidate.coverUrl?.toUri()).build())
                .build()
        }
    }
    val fallbackItem = remember(track.id) {
        MediaItem.Builder().setMediaId(track.id).setUri(track.audioUrl.orEmpty())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setArtworkUri(track.coverUrl?.toUri()).build()).build()
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

    val uiController = remember(controller) { controller?.let { Media3PlayerController(it, playerScope) } }
    DisposableEffect(uiController) {
        onDispose { uiController?.release() }
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
            Box(Modifier.fillMaxSize()) {
                if (uiController != null) {
                    PlayerHost(
                        controller = uiController,
                        expanded = true,
                        onExpandedChange = { expanded -> if (!expanded) onDismiss() },
                        modifier = Modifier.fillMaxSize(),
                        artwork = { url, artworkModifier ->
                            if (!url.isNullOrBlank()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "${track.title} cover artwork",
                                    contentScale = ContentScale.Crop,
                                    modifier = artworkModifier,
                                )
                            } else {
                                Box(
                                    artworkModifier.background(Brush.linearGradient(listOf(Color(0xFF493719), ShellPanel))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.Album, null, tint = ShellGold, modifier = Modifier.size(84.dp))
                                }
                            }
                        },
                    )
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ShellGold)
                        Spacer(Modifier.height(16.dp))
                        Text("Connecting to the HTE playback service…", color = ShellSilver)
                        playbackError?.let { Text(it, color = ShellGold, fontSize = 12.sp) }
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
                playbackError?.let { error ->
                    Text(error, color = ShellGold, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerDialog(video: VideoDto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val validUrl = runCatching { video.videoUrl.toUri().scheme in listOf("https", "http", "content", "file") }.getOrDefault(false)
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
