package com.hightechentertainment.app

import android.net.Uri
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.hightechentertainment.app.player.Media3PlayerController
import com.hightechentertainment.app.player.PlayerHost
import com.hightechentertainment.app.player.PlayerUiState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
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
import com.hightechentertainment.app.data.AudienceAnalyticsRepository
import com.hightechentertainment.app.data.EntertainmentCatalogRepository
import com.hightechentertainment.app.data.MemberRepository
import com.hightechentertainment.app.data.PlaylistDto
import com.hightechentertainment.app.data.SupabaseClientProvider
import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.data.TrackRepository
import com.hightechentertainment.app.data.VideoDto
import com.hightechentertainment.app.media.HtePlaybackController
import com.hightechentertainment.app.media.OfflineDownloadManager
import com.hightechentertainment.app.media.OfflineDownloadRecord
import com.hightechentertainment.app.ui.design.HteIcons
import com.hightechentertainment.app.ui.design.HteGlowingIconBadge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val ShellGold = Color(0xFFE5C158)
private val ShellSilver = Color(0xFFC4CAD2)
private val ShellMuted = Color(0xFF8B929D)
private val ShellPanel = Color(0xFF17161E)
private val ShellBackground = Color(0xFF0B0A0F)

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsStore = remember(context) { AppSettingsStore(context) }
    val audienceAnalytics = remember(context) { AudienceAnalyticsRepository(context) }
    var settingsRevision by remember { mutableStateOf(0) }
    val backgroundPlayback = remember(settingsRevision) { settingsStore.backgroundPlayback }
    val streamingQuality = remember(settingsRevision) { settingsStore.streamingQuality }
    val videoQuality = remember(settingsRevision) { settingsStore.videoQuality }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var playerExpanded by remember { mutableStateOf(false) }
    val playerUiController = remember(mediaController) { mediaController?.let { Media3PlayerController(it, scope) } }
    val playerUiState = playerUiController?.state?.collectAsState(initial = PlayerUiState())?.value ?: PlayerUiState()
    val playerHasTrack = playerUiState.track != null
    DisposableEffect(context) {
        var effectActive = true
        val connectionFuture = HtePlaybackController.connect(
            context,
            onConnected = { controller -> if (effectActive) mediaController = controller },
            onError = { error ->
                if (effectActive) Toast.makeText(
                    context,
                    "The audio player couldn't connect. Check your connection and try again.",
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
        onDispose {
            effectActive = false
            mediaController = null
            HtePlaybackController.release(connectionFuture)
        }
    }
    DisposableEffect(playerUiController) {
        onDispose { playerUiController?.release() }
    }
    DisposableEffect(lifecycleOwner, mediaController, backgroundPlayback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !backgroundPlayback) mediaController?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(mediaController, streamingQuality) {
        val player = mediaController ?: return@LaunchedEffect
        val maxAudioBitrate = when (streamingQuality) {
            "DATA SAVER" -> 128_000
            "NORMAL" -> 160_000
            "HIGH" -> 320_000
            else -> Int.MAX_VALUE
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxAudioBitrate(maxAudioBitrate)
            .build()
    }
    LaunchedEffect(mediaController, selectedTrack?.id, selectedTrackQueue) {
        val selected = selectedTrack ?: return@LaunchedEffect
        val sourceUrl = selected.sourceUrl?.takeIf(String::isNotBlank) ?: selected.audioUrl
        val sourceHost = sourceUrl?.let { runCatching { Uri.parse(it).host.orEmpty().lowercase() }.getOrDefault("") }.orEmpty()
        val externalPlatform = selected.sourcePlatform.lowercase() in setOf("youtube", "spotify") ||
            sourceHost == "youtu.be" || (sourceHost == "youtube.com" || sourceHost.endsWith(".youtube.com")) || (sourceHost == "spotify.com" || sourceHost.endsWith(".spotify.com"))
        if (externalPlatform) {
            try {
                val url = sourceUrl?.takeIf(String::isNotBlank) ?: error("This external track has no source link.")
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                scope.launch { runCatching { withContext(Dispatchers.IO) { audienceAnalytics.record("track_play", selected.id) } } }
            } catch (_: Throwable) {
                Toast.makeText(context, "This external music link could not be opened. Check the link and try again.", Toast.LENGTH_LONG).show()
            } finally {
                selectedTrack = null
                selectedTrackQueue = null
            }
            return@LaunchedEffect
        }
        val player = mediaController ?: return@LaunchedEffect
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
            selectedTrack = null
            selectedTrackQueue = null
            return@LaunchedEffect
        }
        val startIndex = items.indexOfFirst { it.mediaId == selected.id }
        if (startIndex < 0) {
            Toast.makeText(context, "The selected track has no playable audio URL.", Toast.LENGTH_LONG).show()
            selectedTrack = null
            selectedTrackQueue = null
            return@LaunchedEffect
        }
        player.setMediaItems(items, startIndex, C.TIME_UNSET)
        player.prepare()
        player.play()
        scope.launch { runCatching { withContext(Dispatchers.IO) { audienceAnalytics.record("track_play", selected.id) } } }
        playerExpanded = true
        selectedTrack = null
        selectedTrackQueue = null
    }
    val offlineManager = remember(context) { OfflineDownloadManager(context) }
    val autoplayNext = remember(settingsRevision) { settingsStore.autoplayNext }
    var downloads by remember { mutableStateOf<List<OfflineDownloadRecord>>(emptyList()) }
    var favouriteTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var playlists by remember { mutableStateOf<List<PlaylistDto>>(emptyList()) }
    var playlistTracks by remember { mutableStateOf<Map<String, List<TrackDto>>>(emptyMap()) }
    var homeProfileName by remember { mutableStateOf<String?>(null) }
    var homeProfileAvatar by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(configured) {
        refreshCatalogue()
        if (configured) runCatching { withContext(Dispatchers.IO) { audienceAnalytics.record("page_view") } }
    }
    DisposableEffect(lifecycleOwner, configured) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && configured) refreshCatalogue()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(configured) {
        if (configured) {
            while (true) {
                delay(60_000)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) refreshCatalogue()
            }
        }
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == UniverseTab.LIBRARY) {
            try {
                downloads = withContext(Dispatchers.IO) { offlineManager.getDownloads() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                Toast.makeText(context, "Your library could not be loaded. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(configured, selectedTab) {
        if (configured && (selectedTab == UniverseTab.LIBRARY || selectedTab == UniverseTab.HOME)) {
            try {
                favouriteTrackIds = withContext(Dispatchers.IO) { MemberRepository().getFavouriteTrackIds() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                favouriteTrackIds = emptySet()
                if (selectedTab == UniverseTab.LIBRARY) {
                    Toast.makeText(context, "Your favourites could not be refreshed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(configured, selectedTab, tracks) {
        if (configured && selectedTab == UniverseTab.HOME) {
            try {
                val repository = MemberRepository()
                val result = withContext(Dispatchers.IO) {
                    val profile = repository.getMyProfile()
                    val savedPlaylists = repository.getMyPlaylists()
                    val trackMap = savedPlaylists.associate { playlist ->
                        playlist.id to repository.getPlaylistTrackIds(playlist.id)
                            .mapNotNull { trackId -> tracks.firstOrNull { it.id == trackId } }
                    }
                    Triple(profile, savedPlaylists, trackMap)
                }
                homeProfileName = result.first?.displayName?.takeIf { it.isNotBlank() }
                homeProfileAvatar = result.first?.avatarUrl?.takeIf { it.isNotBlank() }
                playlists = result.second
                playlistTracks = result.third
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Signed-out visitors and members without playlists see no private playlist rail.
                homeProfileName = null
                homeProfileAvatar = null
                playlists = emptyList()
                playlistTracks = emptyMap()
            }
        }
    }

    fun playPlaylist(playlist: PlaylistDto) {
        val queue = playlistTracks[playlist.id].orEmpty()
            .filter { !it.audioUrl.isNullOrBlank() }
        if (queue.isEmpty()) {
            Toast.makeText(context, "This playlist has no playable published tracks yet.", Toast.LENGTH_SHORT).show()
            return
        }
        selectedTrack = queue.first()
        selectedTrackQueue = queue
        playerExpanded = true
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
                Toast.makeText(context, "We couldn't update your favourites. Please sign in and try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startDownload(url: String?, title: String, mediaType: String, trackId: String? = null) {
        scope.launch {
            try {
                if (mediaType == "audio" && trackId != null) {
                    val downloadPage = "https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/download/?track=${Uri.encode(trackId)}"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadPage)))
                    Toast.makeText(context, "Opening HTE branded download for $title…", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (url.isNullOrBlank()) error(
                    if (mediaType == "audio") "The HTE-branded download is not ready yet, or this external source is not downloadable."
                    else "This item has no download URL yet."
                )
                withContext(Dispatchers.IO) { offlineManager.enqueue(url, title, mediaType, allowMetered = allowsMeteredMediaDownloads(settingsStore.networkPolicy, settingsStore.dataSaver)) }
                downloads = withContext(Dispatchers.IO) { offlineManager.getDownloads() }
                if (mediaType == "audio" && trackId != null) {
                    withContext(Dispatchers.IO) { audienceAnalytics.record("track_download", trackId) }
                }
                Toast.makeText(context, "Download started: $title", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Toast.makeText(
                    context,
                    error.message?.takeIf { it.isNotBlank() } ?: "The download could not start. Check your connection and available storage, then try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // Use a navigation rail on wide/landscape windows and bottom navigation on phones.
        val isWideLayout = maxWidth >= 720.dp
        // Give the listening theater a proportional width on compact landscape and tablet windows.
        val playerPanelWidth = minOf(maxWidth * 0.48f, 400.dp)
        Scaffold(
        containerColor = ShellBackground,
        bottomBar = {
            if (!isWideLayout) {
                NavigationBar(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(1.dp, ShellGold.copy(alpha = .18f), RoundedCornerShape(28.dp)),
                    containerColor = ShellPanel.copy(alpha = .98f),
                    contentColor = ShellGold
                ) {
                    UniverseTab.entries.forEach { tab ->
                        val icon = HteIcons.navigation(tab.label)
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { HteGlowingIconBadge(icon = icon, selected = selectedTab == tab, contentDescription = tab.label) },
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
        }
    ) { insets ->
        Row(Modifier.fillMaxSize().padding(insets)) {
            if (isWideLayout) {
                NavigationRail(containerColor = ShellPanel, contentColor = ShellGold) {
                    UniverseTab.entries.forEach { tab ->
                        val icon = HteIcons.navigation(tab.label)
                        NavigationRailItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { HteGlowingIconBadge(icon = icon, selected = selectedTab == tab, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 9.sp) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ShellGold, selectedTextColor = ShellGold,
                                unselectedIconColor = ShellMuted, unselectedTextColor = ShellMuted,
                                indicatorColor = ShellGold.copy(alpha = .12f)
                            )
                        )
                    }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .padding(end = if (isWideLayout && playerHasTrack) playerPanelWidth + 8.dp else 0.dp)
            ) {
            if (selectedTab != UniverseTab.HOME) {
                UniverseTopBar(onNotifications = { selectedTab = UniverseTab.ACCOUNT }, onSearch = { selectedTab = UniverseTab.DISCOVER }, onVoice = { showVoiceAssistant = true })
            }
            when (selectedTab) {
                UniverseTab.HOME -> HomeScreen(
                    tracks = tracks, videos = videos, isLoading = isLoading,
                    onExplore = { selectedTab = UniverseTab.DISCOVER },
                    onLibrary = { selectedTab = UniverseTab.LIBRARY },
                    onVideos = { selectedTab = UniverseTab.VIDEOS },
                    onVoice = { showVoiceAssistant = true },
                    onProfile = { selectedTab = UniverseTab.ACCOUNT },
                    playlists = playlists, playlistTracks = playlistTracks,
                    profileName = homeProfileName, profileAvatarUrl = homeProfileAvatar,
                    onPlayTrack = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks else listOf(it); playerExpanded = true },
                    onPlayPlaylist = ::playPlaylist, onPlayVideo = { selectedVideo = it },
                    onDownloadTrack = { startDownload(if (it.downloadAllowed && it.downloadBrandingStatus == "ready") it.brandedAudioUrl else null, it.title, "audio", it.id) },
                    favouriteTrackIds = favouriteTrackIds, onToggleFavourite = ::toggleFavourite,
                    onDownloadVideo = { startDownload(it.videoUrl, it.title, "video") },
                    onRefresh = ::refreshCatalogue
                )
                UniverseTab.DISCOVER -> DiscoverScreen(
                    tracks = tracks, isLoading = isLoading, error = catalogueError,
                    onPlay = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks else listOf(it); playerExpanded = true }, onDownload = { startDownload(if (it.downloadAllowed && it.downloadBrandingStatus == "ready") it.brandedAudioUrl else null, it.title, "audio", it.id) },
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
                    onDownloadFavourite = { startDownload(if (it.downloadAllowed && it.downloadBrandingStatus == "ready") it.brandedAudioUrl else null, it.title, "audio", it.id) },
                    onRemoveDownload = { record ->
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { offlineManager.remove(record.id) }
                                downloads = withContext(Dispatchers.IO) { offlineManager.getDownloads() }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                Toast.makeText(context, "This download could not be removed. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
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
            if (isWideLayout && playerHasTrack) {
                // Wide layouts reserve a dedicated right-hand listening theater.
                PlayerHost(
                    controller = uiController,
                    expanded = playerExpanded,
                    onExpandedChange = { playerExpanded = it },
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .width(playerPanelWidth).fillMaxHeight()
                        .padding(start = 8.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                    collapsedBottomInset = 0.dp,
                    artwork = { url, artworkModifier ->
                        if (!url.isNullOrBlank()) {
                            AsyncImage(model = url, contentDescription = "Track artwork", contentScale = ContentScale.Crop, modifier = artworkModifier)
                        } else {
                            Box(
                                artworkModifier.background(
                                    Brush.radialGradient(listOf(Color(0xFF332716), Color(0xFF15120D), ShellBackground)),
                                    RoundedCornerShape(26.dp)
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier.size(184.dp).border(1.dp, ShellGold.copy(alpha = .62f), CircleShape)
                                        .background(Brush.sweepGradient(listOf(Color(0xFF302516), Color(0xFF090A0C), Color(0xFF302516))), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.size(142.dp).border(1.dp, ShellSilver.copy(alpha = .25f), CircleShape)
                                        .background(Color(0xFF101114), CircleShape), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(88.dp).border(2.dp, ShellGold, CircleShape)
                                            .background(Brush.radialGradient(listOf(Color(0xFF5B4725), Color(0xFF11100D))), CircleShape),
                                            contentAlignment = Alignment.Center) {
                                            Text("HTE", color = ShellGold, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                        }
                                    }
                                }
                                Text("HIGH-TECH ORIGINAL", color = ShellGold.copy(alpha = .8f), fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
                            }
                        }
                    },
                )
            } else {
                PlayerHost(
                    controller = uiController,
                    expanded = playerExpanded,
                    onExpandedChange = { playerExpanded = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    collapsedBottomInset = 96.dp,
                    artwork = { url, artworkModifier ->
                        if (!url.isNullOrBlank()) {
                            AsyncImage(model = url, contentDescription = "Track artwork", contentScale = ContentScale.Crop, modifier = artworkModifier)
                        } else {
                            Box(
                                artworkModifier.background(
                                    Brush.radialGradient(listOf(Color(0xFF332716), Color(0xFF15120D), ShellBackground)),
                                    RoundedCornerShape(26.dp)
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier.size(184.dp).border(1.dp, ShellGold.copy(alpha = .62f), CircleShape)
                                        .background(Brush.sweepGradient(listOf(Color(0xFF302516), Color(0xFF090A0C), Color(0xFF302516))), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.size(142.dp).border(1.dp, ShellSilver.copy(alpha = .25f), CircleShape)
                                        .background(Color(0xFF101114), CircleShape), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(88.dp).border(2.dp, ShellGold, CircleShape)
                                            .background(Brush.radialGradient(listOf(Color(0xFF5B4725), Color(0xFF11100D))), CircleShape),
                                            contentAlignment = Alignment.Center) {
                                            Text("HTE", color = ShellGold, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                        }
                                    }
                                }
                                Text("HIGH-TECH ORIGINAL", color = ShellGold.copy(alpha = .8f), fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
                            }
                        }
                    },
                )
            }
        }
        }
    }

    }

    selectedVideo?.let { VideoPlayerDialog(video = it, quality = videoQuality, onDismiss = { selectedVideo = null }) }
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
        Modifier.fillMaxWidth().background(ShellBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            Modifier.size(39.dp).background(
                Brush.linearGradient(listOf(Color(0xFF5A4522), Color(0xFF17130D))),
                RoundedCornerShape(13.dp)
            ).border(1.dp, ShellGold.copy(alpha = .42f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("HT", color = ShellGold, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = -.5.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("HIGH-TECH", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.ExtraBold)
            Text("ENTERTAINMENT  /  UNIVERSE", color = ShellSilver, fontSize = 8.sp, letterSpacing = .8.sp)
        }
        Surface(
            onClick = onSearch,
            shape = RoundedCornerShape(12.dp),
            color = ShellPanel,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .07f))
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Search, "Search", tint = ShellSilver, modifier = Modifier.size(19.dp))
            }
        }
        Surface(
            onClick = onVoice,
            shape = RoundedCornerShape(12.dp),
            color = ShellGold.copy(alpha = .14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ShellGold.copy(alpha = .35f))
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.GraphicEq, "Voice assistant", tint = ShellGold, modifier = Modifier.size(20.dp))
            }
        }
        Surface(
            onClick = onNotifications,
            shape = RoundedCornerShape(12.dp),
            color = ShellPanel,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .07f))
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.NotificationsNone, "Notifications", tint = ShellSilver, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen(
    tracks: List<TrackDto>, videos: List<VideoDto>, playlists: List<PlaylistDto>,
    playlistTracks: Map<String, List<TrackDto>>, profileName: String?, profileAvatarUrl: String?, isLoading: Boolean,
    onExplore: () -> Unit, onLibrary: () -> Unit, onVideos: () -> Unit, onVoice: () -> Unit, onProfile: () -> Unit,
    onPlayTrack: (TrackDto) -> Unit, onPlayPlaylist: (PlaylistDto) -> Unit, onPlayVideo: (VideoDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit, onDownloadVideo: (VideoDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit, onRefresh: () -> Unit
) {
    Universe2026Home(
        tracks = tracks, videos = videos, playlists = playlists, playlistTracks = playlistTracks,
        profileName = profileName, profileAvatarUrl = profileAvatarUrl, isLoading = isLoading,
        onExplore = onExplore, onLibrary = onLibrary, onVideos = onVideos, onVoice = onVoice, onProfile = onProfile,
        onPlayTrack = onPlayTrack, onPlayPlaylist = onPlayPlaylist, onPlayVideo = onPlayVideo,
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
    Card(
        colors = CardDefaults.cardColors(containerColor = ShellPanel),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(58.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF49391F), ShellPanel)),
                    RoundedCornerShape(12.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                if (!track.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = "Artwork for ${track.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(Icons.Outlined.Album, null, tint = ShellGold, modifier = Modifier.size(27.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = ShellMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                track.genre?.let {
                    Text(it.uppercase(), color = ShellGold, fontSize = 8.sp, letterSpacing = .8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            IconButton(onClick = onToggleFavourite, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                    tint = if (isFavourite) ShellGold else ShellMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Download, "Download ${track.title}", tint = ShellSilver, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.PlayCircle, "Play ${track.title}", tint = ShellGold, modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun VideoRow(video: VideoDto, onPlay: () -> Unit, onDownload: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(13.dp)).background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF392D1D), Color(0xFF171B22), Color(0xFF0D1117)))
            ), contentAlignment = Alignment.Center) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = "Thumbnail for ${video.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Outlined.VideoLibrary, null, tint = ShellGold.copy(alpha = .72f), modifier = Modifier.size(52.dp))
                }
                Box(Modifier.align(Alignment.Center).size(48.dp).background(Color.Black.copy(alpha = .62f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, "Play ${video.title}", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text("HTE VISUALS", color = Color.White, fontSize = 8.sp, letterSpacing = 1.6.sp, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
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
        val selectedIndex = validQueue.indexOfFirst { it.mediaId == track.id }
        val items = if (selectedIndex >= 0) validQueue else validQueue + MediaItem.Builder()
            .setMediaId(track.id).setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build())
            .build()
        val startIndex = if (selectedIndex >= 0) selectedIndex else items.lastIndex
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

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoPlayerDialog(video: VideoDto, quality: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val window = remember(activity) { activity?.window }
    val systemBars = remember(window) { window?.let { WindowInsetsControllerCompat(it, it.decorView) } }
    val previousOrientation = remember(activity) { activity?.requestedOrientation }
    var immersiveVideo by remember(video.id) { mutableStateOf(false) }

    val toggleImmersive = {
        val enteringImmersive = !immersiveVideo
        immersiveVideo = enteringImmersive
        if (enteringImmersive) {
            systemBars?.hide(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            systemBars?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    BackHandler(enabled = immersiveVideo) { toggleImmersive() }
    DisposableEffect(activity, systemBars) {
        onDispose {
            systemBars?.show(WindowInsetsCompat.Type.systemBars())
            if (previousOrientation != null) activity?.requestedOrientation = previousOrientation
        }
    }

    val validUrl = remember(video.videoUrl) {
        runCatching {
            val value = video.videoUrl.trim()
            val uri = Uri.parse(value)
            !uri.isOpaque && uri.scheme?.lowercase() in setOf("https", "http", "content", "file") && value.isNotBlank()
        }.getOrDefault(false)
    }
    val player = remember(video.id, video.videoUrl) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build().apply {
                if (validUrl) {
                    setMediaItem(MediaItem.fromUri(video.videoUrl.trim()))
                    prepare()
                    playWhenReady = true
                }
            }
    }
    LaunchedEffect(player, quality) {
        val maxVideoSize = when (quality) {
            "LOW" -> 640 to 360
            "MEDIUM" -> 854 to 480
            "HIGH" -> 1920 to 1080
            else -> Int.MAX_VALUE to Int.MAX_VALUE
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(maxVideoSize.first, maxVideoSize.second)
            .build()
    }

    var playbackError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var videoPlaying by remember { mutableStateOf(false) }
    var videoPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }
    val isSeekingNow by rememberUpdatedState(isSeeking)

    LaunchedEffect(player) {
        while (true) {
            videoPlaying = player.isPlaying
            if (!isSeekingNow) videoPosition = player.currentPosition.coerceAtLeast(0L)
            videoDuration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
            delay(400)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Video playback failed. Check the connection or retry the stream."
                videoPlaying = false
                isBuffering = false
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) playbackError = null
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                // Capture BoxWithConstraints values before entering ColumnScope to avoid receiver ambiguity.
                val availableWidth = maxWidth
                val availableHeight = maxHeight
                val landscape = availableWidth > availableHeight
                val safePadding = if (immersiveVideo) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                Column(
                    Modifier.fillMaxSize()
                        .then(safePadding)
                        .padding(horizontal = if (immersiveVideo) 0.dp else 12.dp,
                            vertical = if (immersiveVideo) 0.dp else 12.dp)
                ) {
                    if (!immersiveVideo) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(video.title, color = Color.White, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(video.artist ?: "High-Tech Entertainment", color = ShellMuted, fontSize = 10.sp)
                            }
                            IconButton(onClick = toggleImmersive) {
                                Icon(Icons.Outlined.Fullscreen, "Enter fullscreen", tint = ShellGold)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Outlined.Close, "Close video", tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (validUrl) {
                        AndroidView(
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    this.player = player
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    keepScreenOn = true
                                }
                            },
                            update = { view ->
                                if (view.player !== player) view.player = player
                            },
                            onRelease = { view ->
                                view.player = null
                                view.keepScreenOn = false
                            },
                            modifier = if (immersiveVideo || landscape) {
                                Modifier.fillMaxWidth().weight(1f)
                            } else {
                                Modifier.fillMaxWidth().height(minOf(availableWidth * (9f / 16f), availableHeight * .46f))
                            }
                        )
                        if (!immersiveVideo && !landscape) Spacer(Modifier.weight(1f))
                    } else {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("This video does not have a valid playback URL.", color = ShellGold)
                        }
                    }

                    if (validUrl) {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(ShellPanel, RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Slider(
                                value = if (isSeeking) seekFraction else if (videoDuration > 0L)
                                    (videoPosition.toFloat() / videoDuration).coerceIn(0f, 1f) else 0f,
                                onValueChange = { fraction -> isSeeking = true; seekFraction = fraction },
                                onValueChangeFinished = {
                                    if (videoDuration > 0L) player.seekTo((seekFraction * videoDuration).toLong())
                                    isSeeking = false
                                },
                                enabled = videoDuration > 0L,
                                colors = SliderDefaults.colors(thumbColor = ShellGold, activeTrackColor = ShellGold)
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatVideoTime(videoPosition), color = ShellMuted, fontSize = 10.sp)
                                TextButton(
                                    onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) { Text("−10s", color = ShellSilver) }
                                Button(
                                    onClick = { if (player.isPlaying) player.pause() else player.play() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ShellGold, contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text(if (videoPlaying) "Pause" else "Play", fontWeight = FontWeight.Bold) }
                                TextButton(
                                    onClick = {
                                        if (videoDuration > 0L) player.seekTo((player.currentPosition + 10_000L).coerceAtMost(videoDuration))
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) { Text("+10s", color = ShellSilver) }
                                IconButton(onClick = toggleImmersive, modifier = Modifier.size(40.dp)) {
                                    Icon(
                                        if (immersiveVideo) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                        if (immersiveVideo) "Exit fullscreen" else "Enter fullscreen",
                                        tint = ShellGold
                                    )
                                }
                                Text(formatVideoTime(videoDuration), color = ShellMuted, fontSize = 10.sp)
                            }
                        }
                    }
                    if (isBuffering) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ShellGold)
                    playbackError?.let { message ->
                        Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, color = ShellSilver, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    playbackError = null
                                    player.prepare()
                                    player.playWhenReady = true
                                }) { Text("Retry", color = ShellGold) }
                            }
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

private fun formatVideoTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
