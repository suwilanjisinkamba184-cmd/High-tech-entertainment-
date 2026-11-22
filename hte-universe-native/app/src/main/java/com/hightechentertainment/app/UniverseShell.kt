package com.hightechentertainment.app

import android.net.Uri
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.view.View
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.hightechentertainment.app.player.Media3PlayerController
import com.hightechentertainment.app.player.PlayerHost
import com.hightechentertainment.app.player.PlayerUiState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.channel
import com.hightechentertainment.app.data.AuthRepository
import com.hightechentertainment.app.data.ArtistDto
import com.hightechentertainment.app.data.ReleaseDto
import com.hightechentertainment.app.data.AudienceAnalyticsRepository
import com.hightechentertainment.app.data.EntertainmentCatalogRepository
import com.hightechentertainment.app.data.MemberRepository
import com.hightechentertainment.app.data.PlaylistDto
import com.hightechentertainment.app.data.SupabaseClientProvider
import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.data.TrackRepository
import com.hightechentertainment.app.data.VideoDto
import com.hightechentertainment.app.media.HtePlaybackController
import com.hightechentertainment.app.media.extractYouTubeVideoId
import com.hightechentertainment.app.media.isExternalStreamingSource
import com.hightechentertainment.app.media.isSpotifySource
import com.hightechentertainment.app.media.spotifyAppUri
import com.hightechentertainment.app.media.isPlayableInAppAudioUrl
import com.hightechentertainment.app.media.isPlayableVideoUrl
import com.hightechentertainment.app.media.OfflineDownloadManager
import com.hightechentertainment.app.media.resolveDownloadedLocalUri
import com.hightechentertainment.app.media.OfflineDownloadRecord
import com.hightechentertainment.app.media.temporaryAudioDownloadPage
import com.hightechentertainment.app.ui.design.HteIcons
import com.hightechentertainment.app.ui.design.HteGlowingIconBadge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.TimeoutCancellationException

private val ShellGold = com.hightechentertainment.app.ui.design.HteColors.Gold
private val ShellSilver = com.hightechentertainment.app.ui.design.HteColors.Silver
private val ShellMuted = com.hightechentertainment.app.ui.design.HteColors.Muted
private val ShellPanel = com.hightechentertainment.app.ui.design.HteColors.Panel
private val ShellBackground = com.hightechentertainment.app.ui.design.HteColors.Obsidian

private data class CatalogueSnapshot(
    val tracks: List<TrackDto>,
    val artists: List<ArtistDto>,
    val releases: List<ReleaseDto>,
    val videos: List<VideoDto>
)

private enum class UniverseTab(val label: String) {
    HOME("Home"), DISCOVER("Discover"), VIDEOS("Videos"), LIBRARY("Library"), ACCOUNT("Account")
}

@Composable
fun UniverseShell() {
    var selectedTab by remember { mutableStateOf(UniverseTab.HOME) }
    var tracks by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var artists by remember { mutableStateOf<List<ArtistDto>>(emptyList()) }
    var releases by remember { mutableStateOf<List<ReleaseDto>>(emptyList()) }
    var videos by remember { mutableStateOf<List<VideoDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var catalogueError by remember { mutableStateOf<String?>(null) }
    var selectedTrack by remember { mutableStateOf<TrackDto?>(null) }
    var selectedTrackQueue by remember { mutableStateOf<List<TrackDto>?>(null) }
    var selectedVideo by remember { mutableStateOf<VideoDto?>(null) }
    var selectedSpotifyUrl by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistDto?>(null) }
    var showVoiceAssistant by remember { mutableStateOf(false) }
    var showSettingsHub by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var videoSearchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioSystemBars = remember(activity) { activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) } }
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
        val externalPlatform = isExternalStreamingSource(selected.sourcePlatform, sourceUrl)
        if (externalPlatform) {
            val url = sourceUrl?.takeIf(String::isNotBlank)
            try {
                if (url == null) error("This external track has no source link.")
                if (isSpotifySource(selected.sourcePlatform, url)) {
                    // Use Spotify's official embedded player inside HTE. Spotify controls
                    // entitlement and playback; HTE never extracts or proxies audio.
                    selectedSpotifyUrl = url
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                scope.launch {
            runCatching { withContext(Dispatchers.IO) { MemberRepository().recordListening(selected.id) } }
            runCatching { withContext(Dispatchers.IO) { audienceAnalytics.record("track_play", selected.id) } }
        }
            } catch (error: ActivityNotFoundException) {
                val message = if (isSpotifySource(selected.sourcePlatform, url)) {
                    "Spotify is not installed. Install the Spotify app to play this track."
                } else {
                    "This music app is not installed. Check the link and try again."
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
            val candidateSource = candidate.sourceUrl?.takeIf(String::isNotBlank) ?: candidate.audioUrl
            if (isExternalStreamingSource(candidate.sourcePlatform, candidateSource)) return@mapNotNull null
            val audioUrl = candidate.audioUrl?.takeIf { isPlayableInAppAudioUrl(it, candidate.sourcePlatform) }
                ?: return@mapNotNull null
            val uri = audioUrl.toUri()
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
    var recentlyPlayedTrackIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<PlaylistDto>>(emptyList()) }
    var playlistTracks by remember { mutableStateOf<Map<String, List<TrackDto>>>(emptyMap()) }
    var homeProfileName by remember { mutableStateOf<String?>(null) }
    var homeProfileAvatar by remember { mutableStateOf<String?>(null) }
    val configured = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    // Startup, ON_RESUME and periodic refreshes must not race shared UI state.
    var catalogueRefreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun refreshCatalogue() {
        if (catalogueRefreshJob?.isActive == true) return
        catalogueRefreshJob = scope.launch {
            if (!configured) {
                catalogueError = "The catalogue is not connected in this build. A secure Supabase URL and publishable key are required."
                return@launch
            }
            isLoading = true
            catalogueError = null
            try {
                val snapshot = withTimeout(20_000) {
                    coroutineScope {
                        val catalogue = EntertainmentCatalogRepository()
                        val trackRequest = async { TrackRepository().getPublishedTracks() }
                        val artistRequest = async { catalogue.getVisibleArtists() }
                        val releaseRequest = async { catalogue.getPublishedReleases() }
                        val videoRequest = async { catalogue.getPublishedVideos() }
                        CatalogueSnapshot(
                            tracks = trackRequest.await(),
                            artists = artistRequest.await(),
                            releases = releaseRequest.await(),
                            videos = videoRequest.await()
                        )
                    }
                }
                tracks = snapshot.tracks
                artists = snapshot.artists
                releases = snapshot.releases
                videos = snapshot.videos
            } catch (_: TimeoutCancellationException) {
                catalogueError = "The catalogue is taking too long to respond. Check your connection and retry."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                catalogueError = "We couldn't load the catalogue. Check your connection and try again."
            } finally {
                isLoading = false
                catalogueRefreshJob = null
            }
        }
    }

    LaunchedEffect(configured) {
        refreshCatalogue()
        if (configured) runCatching { withContext(Dispatchers.IO) { audienceAnalytics.record("page_view") } }
    }

    // Subscribe to all four public catalogue tables. The minute-based refresh below
    // remains a recovery path if the device loses its Realtime websocket.
    LaunchedEffect(configured) {
        if (!configured) return@LaunchedEffect
        val client = SupabaseClientProvider.client
        val channel = client.channel("hte-universe-public-catalogue")
        val changeFlows = listOf("tracks", "artists", "releases", "videos").map { tableName ->
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = tableName
            }
        }
        try {
            coroutineScope {
                changeFlows.forEach { flow ->
                    launch { flow.collect { refreshCatalogue() } }
                }
                channel.subscribe(blockUntilSubscribed = true)
                awaitCancellation()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Existing lifecycle and periodic refreshes provide a safe fallback.
        } finally {
            runCatching { channel.unsubscribe() }
        }
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

    LaunchedEffect(selectedTab) {
        if (selectedTab == UniverseTab.LIBRARY) {
            while (true) {
                delay(2_000)
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) continue
                val latest = withContext(Dispatchers.IO) { offlineManager.getDownloads() }
                downloads = latest
                if (latest.none { it.status == "Queued" || it.status == "Downloading" || it.status == "Paused" }) break
            }
        }
    }

    LaunchedEffect(configured, selectedTab) {
        if (configured && (selectedTab == UniverseTab.LIBRARY || selectedTab == UniverseTab.HOME)) {
            try {
                val repository = MemberRepository()
                favouriteTrackIds = withContext(Dispatchers.IO) { repository.getFavouriteTrackIds() }
                if (selectedTab == UniverseTab.LIBRARY) {
                    recentlyPlayedTrackIds = withContext(Dispatchers.IO) { repository.getMyListeningTrackIds() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                favouriteTrackIds = emptySet()
                if (selectedTab == UniverseTab.LIBRARY) {
                    recentlyPlayedTrackIds = emptyList()
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
            .filter { candidate ->
                val source = candidate.sourceUrl?.takeIf(String::isNotBlank) ?: candidate.audioUrl
                !isExternalStreamingSource(candidate.sourcePlatform, source) &&
                    isPlayableInAppAudioUrl(candidate.audioUrl, candidate.sourcePlatform)
            }
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

    fun startDownload(
        url: String?,
        title: String,
        mediaType: String,
        trackId: String? = null,
        sourcePlatform: String = "direct",
        externalSourceUrl: String? = null,
        downloadAllowed: Boolean = true,
        rightsStatus: String = "review_required"
    ) {
        scope.launch {
            try {
                if (mediaType == "audio" && trackId != null) {
                    if (!downloadAllowed || rightsStatus !in setOf("owned_or_licensed", "legacy_existing")) {
                        Toast.makeText(context, "Download rights have not been confirmed for this track.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    if (isExternalStreamingSource(sourcePlatform, externalSourceUrl)) {
                        Toast.makeText(context, "This track links to an external streaming page. Spotify and YouTube audio cannot be downloaded through HTE.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    // All catalogue audio must pass through the server-side temporary
                    // Supabase delivery flow. Never enqueue the permanent branded URL
                    // directly from Android, even when one is already available.
                    val downloadPage = temporaryAudioDownloadPage(trackId)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadPage)))
                    // Preserve the existing download analytics event after handing
                    // the user to the secure website delivery flow.
                    try {
                        withContext(Dispatchers.IO) { audienceAnalytics.record("track_download", trackId) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        // Analytics must never prevent or misreport a successful hand-off.
                    }
                    Toast.makeText(context, "Opening secure HTE temporary download for $title…", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (url.isNullOrBlank()) error(
                    if (mediaType == "audio") "The HTE-branded download is not ready yet."
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
                    "The download could not start. Check your connection and available storage, then try again.",
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
        // The expanded phone player is a true immersive surface. System bars remain
        // available through the Android swipe gesture and are restored on every exit.
        val immersiveAudio = playerExpanded && playerHasTrack && !isWideLayout && selectedVideo == null
        LaunchedEffect(immersiveAudio, audioSystemBars) {
            if (immersiveAudio) {
                audioSystemBars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                audioSystemBars?.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                audioSystemBars?.show(WindowInsetsCompat.Type.systemBars())
                audioSystemBars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
        DisposableEffect(audioSystemBars) {
            onDispose {
                audioSystemBars?.show(WindowInsetsCompat.Type.systemBars())
                audioSystemBars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
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
            UniverseTopBar(
                searchQuery = searchQuery,
                onQueryChange = { query ->
                    searchQuery = query
                    selectedTab = UniverseTab.DISCOVER
                }
            )
            when (selectedTab) {
                UniverseTab.HOME -> HomeScreen(
                    tracks = tracks, artists = artists, releases = releases, videos = videos, isLoading = isLoading,
                    onExplore = { selectedTab = UniverseTab.DISCOVER },
                    onLibrary = { selectedTab = UniverseTab.LIBRARY },
                    onVideos = { selectedTab = UniverseTab.VIDEOS },
                    onVoice = { showVoiceAssistant = true },
                    onProfile = { selectedTab = UniverseTab.ACCOUNT },
                    onSettings = { showSettingsHub = true },
                    onArtistClick = { selectedArtist = it },
                    playlists = playlists, playlistTracks = playlistTracks,
                    profileName = homeProfileName, profileAvatarUrl = homeProfileAvatar,
                    onPlayTrack = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks else listOf(it); playerExpanded = true },
                    onPlayPlaylist = ::playPlaylist, onPlayVideo = { selectedVideo = it },
                    onDownloadTrack = { track -> startDownload(if (track.downloadAllowed && track.downloadBrandingStatus == "ready") track.brandedAudioUrl else null, track.title, "audio", track.id, track.sourcePlatform, track.sourceUrl ?: track.audioUrl, track.downloadAllowed, track.rightsStatus) },
                    favouriteTrackIds = favouriteTrackIds, onToggleFavourite = ::toggleFavourite,
                    onDownloadVideo = { startDownload(it.downloadUrl, it.title, "video") },
                    onRefresh = ::refreshCatalogue
                )
                UniverseTab.DISCOVER -> DiscoverScreen(
                    tracks = tracks, artists = artists, isLoading = isLoading, error = catalogueError,
                    onArtistClick = { selectedArtist = it },
                    onPlay = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks else listOf(it); playerExpanded = true }, onDownload = { track -> startDownload(if (track.downloadAllowed && track.downloadBrandingStatus == "ready") track.brandedAudioUrl else null, track.title, "audio", track.id, track.sourcePlatform, track.sourceUrl ?: track.audioUrl, track.downloadAllowed, track.rightsStatus) },
                    favouriteTrackIds = favouriteTrackIds, onToggleFavourite = ::toggleFavourite,
                    initialQuery = searchQuery, onQueryChanged = { searchQuery = it }, onRefresh = ::refreshCatalogue
                )
                UniverseTab.VIDEOS -> VideosScreen(
                    videos = videos, searchQuery = videoSearchQuery,
                    onSearchQueryChange = { videoSearchQuery = it },
                    isLoading = isLoading, error = catalogueError,
                    onPlay = { selectedVideo = it }, onDownload = { startDownload(it.downloadUrl, it.title, "video") }, onRefresh = ::refreshCatalogue
                )
                UniverseTab.LIBRARY -> LibraryScreen(
                    onSignIn = { selectedTab = UniverseTab.ACCOUNT },
                    downloads = downloads,
                    favouriteTracks = tracks.filter { it.id in favouriteTrackIds },
                    recentlyPlayedTracks = recentlyPlayedTrackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } },
                    playlists = playlists,
                    playlistTracks = playlistTracks,
                    onPlayPlaylist = ::playPlaylist,
                    onPlayRecent = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks else listOf(it); playerExpanded = true },
                    onPlayFavourite = { selectedTrack = it; selectedTrackQueue = if (autoplayNext) tracks.filter { track -> track.id in favouriteTrackIds } else listOf(it); playerExpanded = true },
                    onToggleFavourite = ::toggleFavourite,
                    onDownloadFavourite = { track -> startDownload(if (track.downloadAllowed && track.downloadBrandingStatus == "ready") track.brandedAudioUrl else null, track.title, "audio", track.id, track.sourcePlatform, track.sourceUrl ?: track.audioUrl, track.downloadAllowed, track.rightsStatus) },
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
                    onRetryDownload = { record ->
                        val source = record.sourceUrl
                        if (source.isNullOrBlank()) {
                            Toast.makeText(context, "This older download has no saved retry link. Remove it and download the item again.", Toast.LENGTH_LONG).show()
                        } else {
                            startDownload(source, record.title, record.mediaType)
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
                UniverseTab.ACCOUNT -> AccountScreen(
                    configured = configured,
                    onSettingsChanged = { settingsRevision++ },
                    onVoiceAssistant = { showVoiceAssistant = true },
                    onVideoSubmitted = { refreshCatalogue() }
                )
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
                    immersive = false,
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
                    // The player is layered above Scaffold content, so use the
                    // measured bottom-bar + system-bar inset instead of a fixed offset.
                    modifier = Modifier.align(Alignment.BottomCenter),
                    collapsedBottomInset = insets.calculateBottomPadding(),
                    immersive = true,
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

    if (showSettingsHub) {
        Dialog(onDismissRequest = { showSettingsHub = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF08090B)) {
                SettingsHub(
                    onClose = { showSettingsHub = false },
                    settingsStore = settingsStore,
                    downloadManager = offlineManager,
                    onSettingsChanged = { settingsRevision++ },
                    onVoiceAssistant = {
                        showSettingsHub = false
                        showVoiceAssistant = true
                    }
                )
            }
        }
    }
    selectedArtist?.let { artist ->
        ArtistProfileDialog(
            artist = artist,
            tracks = tracks.filter { it.artist.equals(artist.name, ignoreCase = true) },
            favouriteTrackIds = favouriteTrackIds,
            onPlayTrack = { track ->
                selectedTrack = track
                selectedTrackQueue = if (autoplayNext) tracks.filter { it.artist.equals(artist.name, true) } else listOf(track)
                playerExpanded = true
                selectedArtist = null
            },
            onDownloadTrack = { track -> startDownload(if (track.downloadAllowed && track.downloadBrandingStatus == "ready") track.brandedAudioUrl else null, track.title, "audio", track.id, track.sourcePlatform, track.sourceUrl ?: track.audioUrl, track.downloadAllowed, track.rightsStatus) },
            onToggleFavourite = ::toggleFavourite,
            onDismiss = { selectedArtist = null }
        )
    }
    selectedVideo?.let { VideoPlayerDialog(video = it, quality = videoQuality, onDismiss = { selectedVideo = null }) }
    selectedSpotifyUrl?.let { url -> SpotifyEmbedDialog(url = url, onDismiss = { selectedSpotifyUrl = null }) }
    if (showVoiceAssistant) {
        VoiceAssistantDialog(
            tracks = tracks,
            favouriteTracks = tracks.filter { it.id in favouriteTrackIds },
            videos = videos,
            onPlayFavourites = { favourites ->
                favourites.firstOrNull()?.let { first ->
                    selectedTrack = first
                    selectedTrackQueue = if (autoplayNext) favourites else listOf(first)
                    playerExpanded = true
                }
            },
            onPlayTrack = { track ->
                selectedTrack = track
                selectedTrackQueue = if (autoplayNext) tracks else listOf(track)
                playerExpanded = true
            },
            onDownloadTrack = { track -> startDownload(if (track.downloadAllowed && track.downloadBrandingStatus == "ready") track.brandedAudioUrl else null, track.title, "audio", track.id, track.sourcePlatform, track.sourceUrl ?: track.audioUrl, track.downloadAllowed, track.rightsStatus) },
            onPlayVideo = { video -> selectedTab = UniverseTab.VIDEOS; selectedVideo = video },
            onDownloadVideo = { video -> startDownload(video.downloadUrl, video.title, "video") },
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
            onSearchVideos = { videoSearchQuery = it },
            onDismiss = { showVoiceAssistant = false }
        )
    }
}

@Composable
private fun UniverseTopBar(searchQuery: String, onQueryChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ShellBackground)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            singleLine = true,
            placeholder = { Text("Search music, artists or videos", color = ShellMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = ShellGold) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = ShellSilver)
                    }
                }
            },
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ShellGold,
                unfocusedBorderColor = Color.White.copy(alpha = .12f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = ShellGold
            )
        )
    }
}

@Composable
private fun HomeScreen(
    tracks: List<TrackDto>, artists: List<ArtistDto>, releases: List<ReleaseDto>, videos: List<VideoDto>, playlists: List<PlaylistDto>,
    playlistTracks: Map<String, List<TrackDto>>, profileName: String?, profileAvatarUrl: String?, isLoading: Boolean,
    onExplore: () -> Unit, onLibrary: () -> Unit, onVideos: () -> Unit, onVoice: () -> Unit, onProfile: () -> Unit, onSettings: () -> Unit, onArtistClick: (ArtistDto) -> Unit,
    onPlayTrack: (TrackDto) -> Unit, onPlayPlaylist: (PlaylistDto) -> Unit, onPlayVideo: (VideoDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit, onDownloadVideo: (VideoDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit, onRefresh: () -> Unit
) {
    MusicHomeScreen(
        tracks = tracks, artists = artists, releases = releases, videos = videos, playlists = playlists, playlistTracks = playlistTracks,
        profileName = profileName, profileAvatarUrl = profileAvatarUrl, isLoading = isLoading,
        onExplore = onExplore, onLibrary = onLibrary, onVideos = onVideos, onVoice = onVoice, onProfile = onProfile, onSettings = onSettings, onArtistClick = onArtistClick,
        onPlayTrack = onPlayTrack, onPlayPlaylist = onPlayPlaylist, onPlayVideo = onPlayVideo,
        onDownloadTrack = onDownloadTrack, onDownloadVideo = onDownloadVideo,
        favouriteTrackIds = favouriteTrackIds, onToggleFavourite = onToggleFavourite,
        onRefresh = onRefresh
    )
}

@Composable
private fun DiscoverScreen(
    tracks: List<TrackDto>, artists: List<ArtistDto>, isLoading: Boolean, error: String?,
    onArtistClick: (ArtistDto) -> Unit, onPlay: (TrackDto) -> Unit, onDownload: (TrackDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit,
    initialQuery: String, onQueryChanged: (String) -> Unit, onRefresh: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var selectedGenre by remember { mutableStateOf("All") }
    LaunchedEffect(initialQuery) { query = initialQuery }
    val genres = listOf("All") + tracks.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.distinct().sorted()
    val matchingArtists = artists.filter { artist ->
        query.isBlank() || artist.name.contains(query.trim(), true) || artist.bio.orEmpty().contains(query.trim(), true)
    }
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
        if (matchingArtists.isNotEmpty()) {
            item { Text("ARTISTS", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold) }
            items(matchingArtists, key = { "artist-${it.id}" }) { artist -> ArtistResultRow(artist) { onArtistClick(artist) } }
        }
        if (filtered.isEmpty() && matchingArtists.isEmpty()) item {
            EmptyCatalogueState(if (isLoading) "Loading the HTE catalogue…" else "No tracks or artists match this search yet.", isLoading, onRefresh)
        } else items(filtered, key = { "track-${it.id}" }) { track ->
            TrackRow(track, { onPlay(track) }, { onDownload(track) }, track.id in favouriteTrackIds, { onToggleFavourite(track) })
        }
    }
}

@Composable
private fun ArtistResultRow(artist: ArtistDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ShellPanel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(ShellGold.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                if (!artist.imageUrl.isNullOrBlank()) AsyncImage(
                    model = artist.imageUrl, contentDescription = "${artist.name} artist image",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                ) else Icon(Icons.Outlined.Person, null, tint = ShellGold, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(artist.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist.bio?.takeIf(String::isNotBlank) ?: "High-Tech Entertainment artist", color = ShellMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ChevronRight, "View artist", tint = ShellGold)
        }
    }
}

@Composable
private fun ArtistProfileDialog(
    artist: ArtistDto,
    tracks: List<TrackDto>,
    favouriteTrackIds: Set<String>,
    onPlayTrack: (TrackDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit,
    onToggleFavourite: (TrackDto) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ShellBackground) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ARTIST PROFILE", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close artist profile", tint = ShellSilver) }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp)).background(ShellPanel), contentAlignment = Alignment.Center) {
                            if (!artist.imageUrl.isNullOrBlank()) AsyncImage(
                                model = artist.imageUrl, contentDescription = "${artist.name} artist artwork",
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            ) else Icon(Icons.Outlined.Person, null, tint = ShellGold, modifier = Modifier.size(70.dp))
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(artist.name, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            Text(artist.bio?.takeIf(String::isNotBlank) ?: "Artist · High-Tech Entertainment", color = ShellSilver, fontSize = 12.sp)
                        }
                    }
                    item { Text("MUSIC BY ${artist.name.uppercase()}", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold) }
                    if (tracks.isEmpty()) item {
                        Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(16.dp)) {
                            Text("This artist profile is available. Music will appear here when tracks are added to the catalogue.", color = ShellSilver, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                    } else items(tracks, key = { "artist-track-${it.id}" }) { track ->
                        TrackRow(track, { onPlayTrack(track) }, { onDownloadTrack(track) }, track.id in favouriteTrackIds, { onToggleFavourite(track) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VideosScreen(
    videos: List<VideoDto>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onPlay: (VideoDto) -> Unit,
    onDownload: (VideoDto) -> Unit,
    onRefresh: () -> Unit
) {
    val filteredVideos = videos.filter { video ->
        searchQuery.isBlank() ||
            video.title.contains(searchQuery.trim(), ignoreCase = true) ||
            video.artist.orEmpty().contains(searchQuery.trim(), ignoreCase = true) ||
            video.description.orEmpty().contains(searchQuery.trim(), ignoreCase = true)
    }
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search videos or artists") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = ShellGold) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear video search")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ShellGold,
                    unfocusedBorderColor = ShellMuted.copy(alpha = .45f),
                    focusedLabelColor = ShellGold,
                    cursorColor = ShellGold
                )
            )
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
                    Text("${filteredVideos.size} matching videos", color = ShellMuted, fontSize = 11.sp)
                }
                TextButton(onClick = onRefresh, enabled = !isLoading) { Icon(Icons.Outlined.Refresh, null, tint = ShellGold); Text("Retry", color = ShellGold) }
            }
        }
        if (error != null) item { Text(error, color = ShellGold, fontSize = 12.sp) }
        if (filteredVideos.isEmpty()) {
            item {
                EmptyCatalogueState(
                    if (isLoading) "Loading videos…" else if (searchQuery.isBlank()) "No published videos yet." else "No videos match “$searchQuery”.",
                    isLoading,
                    onRefresh
                )
            }
        } else items(filteredVideos, key = { it.id }) { video ->
            VideoRow(video, { onPlay(video) }, if (!video.downloadUrl.isNullOrBlank()) ({ onDownload(video) }) else null)
        }
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
            if (track.downloadAllowed && track.rightsStatus in setOf("owned_or_licensed", "legacy_existing") && !isExternalStreamingSource(track.sourcePlatform, track.sourceUrl ?: track.audioUrl)) {
                IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Download, "Download ${track.title}", tint = ShellSilver, modifier = Modifier.size(19.dp))
                }
            }
            IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.PlayCircle, "Play ${track.title}", tint = ShellGold, modifier = Modifier.size(25.dp))
            }
        }
    }
}

private fun isYouTubeWatchUrl(value: String): Boolean {
    // java.net.URI is deterministic in local JVM tests; android.net.Uri is a mocked Android stub there.
    val host = runCatching { java.net.URI(value).host.orEmpty().lowercase() }.getOrDefault("")
    return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" || host.endsWith(".youtu.be")
}

/** Returns a safe HTTPS poster URL, deriving YouTube artwork when catalogue thumbnail metadata is absent or invalid. */
internal fun resolveVideoThumbnailCandidates(video: VideoDto): List<String> {
    val supplied = video.thumbnailUrl?.trim().orEmpty()
    val suppliedUri = runCatching { java.net.URI(supplied) }.getOrNull()
    val validSupplied = supplied.takeIf {
        suppliedUri != null &&
            suppliedUri.scheme.equals("https", ignoreCase = true) &&
            !suppliedUri.host.isNullOrBlank() &&
            !isYouTubeWatchUrl(it)
    }

    val youtubeId = video.youtubeId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        ?: extractYouTubeVideoId(video.videoUrl)
    val suppliedPath = validSupplied?.let { runCatching { java.net.URI(it).path }.getOrNull() }
    val youtubeCandidates = youtubeId?.let { id ->
        // hqdefault is the most consistently available non-placeholder poster.
        // Try lower-resolution variants before maxres, which YouTube often
        // serves as a tiny placeholder when the high-resolution image is absent.
        listOf("hqdefault", "sddefault", "mqdefault", "default", "maxresdefault")
            .map { quality -> "https://img.youtube.com/vi/$id/$quality.jpg" }
            .filterNot { candidate ->
                suppliedPath != null && runCatching { java.net.URI(candidate).path == suppliedPath }.getOrDefault(false)
            }
    }.orEmpty()

    return (listOfNotNull(validSupplied) + youtubeCandidates).distinct()
}

internal fun resolveVideoThumbnailUrl(video: VideoDto): String? =
    resolveVideoThumbnailCandidates(video).firstOrNull()

internal fun youtubeThumbnailFallbackUrl(video: VideoDto, failedUrl: String): String? {
    val candidates = resolveVideoThumbnailCandidates(video)
    val failedIndex = candidates.indexOf(failedUrl)
    return if (failedIndex >= 0) candidates.getOrNull(failedIndex + 1) else candidates.firstOrNull()
}

@Composable
internal fun VideoPosterImage(video: VideoDto, description: String, modifier: Modifier = Modifier) {
    val key = remember(video.id, video.thumbnailUrl, video.videoUrl, video.youtubeId) {
        video.id + video.thumbnailUrl.orEmpty() + video.videoUrl + video.youtubeId.orEmpty()
    }
    val candidates = remember(key) { resolveVideoThumbnailCandidates(video) }
    var candidateIndex by remember(key) { mutableIntStateOf(0) }
    var imageFailed by remember(key) { mutableStateOf(false) }
    val posterUrl = candidates.getOrNull(candidateIndex)

    Box(modifier, contentAlignment = Alignment.Center) {
        if (posterUrl != null && !imageFailed) {
            AsyncImage(
                model = posterUrl,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    if (candidateIndex < candidates.lastIndex) {
                        candidateIndex += 1
                    } else {
                        imageFailed = true
                    }
                }
            )
        } else {
            Icon(
                Icons.Outlined.VideoLibrary,
                null,
                tint = ShellGold.copy(alpha = .72f),
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

@Composable
private fun VideoRow(video: VideoDto, onPlay: () -> Unit, onDownload: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(13.dp)).background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF392D1D), Color(0xFF171B22), Color(0xFF0D1117)))
            ), contentAlignment = Alignment.Center) {
                VideoPosterImage(video, "Thumbnail for ${video.title}", Modifier.fillMaxSize())
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
                if (onDownload != null) IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, "Download original ${video.title}", tint = ShellSilver) }
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
    recentlyPlayedTracks: List<TrackDto>,
    playlists: List<PlaylistDto>,
    playlistTracks: Map<String, List<TrackDto>>,
    onPlayPlaylist: (PlaylistDto) -> Unit,
    onPlayRecent: (TrackDto) -> Unit,
    onPlayFavourite: (TrackDto) -> Unit,
    onToggleFavourite: (TrackDto) -> Unit,
    onDownloadFavourite: (TrackDto) -> Unit,
    onRemoveDownload: (OfflineDownloadRecord) -> Unit,
    onRetryDownload: (OfflineDownloadRecord) -> Unit,
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
        item { Text("YOUR PLAYLISTS", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold) }
        if (playlists.isEmpty()) item {
            LibraryEmptyCard("No playlists yet", "Create a playlist from your HTE member account. Your playlists stay synced with the website.", onSignIn, "Open account")
        } else items(playlists, key = { "playlist-${it.id}" }) { playlist ->
            Card(colors = CardDefaults.cardColors(containerColor = ShellPanel), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(ShellGold.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.QueueMusic, null, tint = ShellGold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(playlist.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlistTracks[playlist.id].orEmpty().size} available tracks", color = ShellMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { onPlayPlaylist(playlist) }) {
                        Icon(Icons.Outlined.PlayCircle, "Play ${playlist.name}", tint = ShellGold)
                    }
                }
            }
        }
        item { Text("RECENTLY PLAYED", color = ShellGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold) }
        if (recentlyPlayedTracks.isEmpty()) item {
            LibraryEmptyCard("No listening history yet", "Play a published track while signed in and it will appear here and on the HTE website account page.", onSignIn, "Open account")
        } else items(recentlyPlayedTracks, key = { "recent-${it.id}" }) { track ->
            TrackRow(track, { onPlayRecent(track) }, { onDownloadFavourite(track) }, favouriteTracks.any { it.id == track.id }, { onToggleFavourite(track) })
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
                        val progress = record.progressPercent
                        Text(
                            "${record.mediaType.uppercase()} · ${record.status}" +
                                if (record.status == "Downloading" && progress != null) " · $progress%" else "",
                            color = ShellMuted,
                            fontSize = 10.sp
                        )
                        if (record.status == "Downloading" && progress != null) {
                            LinearProgressIndicator(
                                progress = progress / 100f,
                                modifier = Modifier.fillMaxWidth(),
                                color = ShellGold,
                                trackColor = ShellMuted.copy(alpha = .22f)
                            )
                        }
                    }
                    if (record.status == "Downloaded" && !record.localUri.isNullOrBlank()) IconButton(onClick = { onPlayDownload(record) }) { Icon(Icons.Outlined.PlayArrow, "Play offline", tint = ShellGold) }
                    if (record.status == "Failed" && !record.sourceUrl.isNullOrBlank()) IconButton(onClick = { onRetryDownload(record) }) { Icon(Icons.Outlined.Refresh, "Retry download", tint = ShellGold) }
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
private fun AccountScreen(
    configured: Boolean,
    onSettingsChanged: () -> Unit,
    onVoiceAssistant: () -> Unit,
    onVideoSubmitted: () -> Unit
) {
    MemberCenter(
        configured = configured,
        onSettingsChanged = onSettingsChanged,
        onVoiceAssistant = onVoiceAssistant,
        onVideoSubmitted = onVideoSubmitted
    )
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


@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoPlayerDialog(video: VideoDto, quality: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val previousOrientation = remember(activity) { activity?.requestedOrientation }
    var immersiveVideo by remember(video.id) { mutableStateOf(false) }

    val offlineUri = remember(video.id, video.downloadUrl, video.videoUrl) {
        val records = OfflineDownloadManager(context).getDownloads()
        listOfNotNull(video.downloadUrl, video.videoUrl)
            .distinct()
            .firstNotNullOfOrNull { source -> resolveDownloadedLocalUri(records, source) }
            ?.let(Uri::parse)
    }
    val youtubeId = remember(video.videoUrl, video.youtubeId) { video.youtubeId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: extractYouTubeVideoId(video.videoUrl) }
    if (youtubeId != null && offlineUri == null) {
        YouTubeVideoDialog(video, youtubeId, onDismiss)
        return
    }

    val playbackUri = offlineUri ?: Uri.parse(video.videoUrl.trim())
    val validUrl = remember(video.videoUrl, offlineUri) { offlineUri != null || isPlayableVideoUrl(video.videoUrl) }
    // Register the Media3 listener before preparing the stream so the initial buffering,
    // first-frame and decoder errors cannot be missed on fast connections.
    val player = remember(video.id, video.videoUrl, offlineUri) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    // Video is Activity-scoped, unlike background music: pause it when the app leaves foreground.
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && player.isPlaying) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
    var firstFrameRendered by remember(video.id) { mutableStateOf(false) }
    var videoPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }
    val isSeekingNow by rememberUpdatedState(isSeeking)

    fun toggleVideoPlayback() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playerError != null) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            player.play()
        }
    }

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
                firstFrameRendered = false
            }
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
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
    LaunchedEffect(player, validUrl, video.videoUrl) {
        if (validUrl) {
            player.setMediaItem(MediaItem.fromUri(playbackUri))
            player.prepare()
            player.playWhenReady = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Compose Dialog owns a separate Window. Fullscreen must control that window,
        // not the Activity window behind it, otherwise status/navigation bars can remain
        // visible over the video or the dialog can be measured inside the old safe area.
        val dialogView = LocalView.current
        val dialogWindow = remember(dialogView) {
            (dialogView.parent as? DialogWindowProvider)?.window
        }
        val window = dialogWindow ?: activity?.window
        val systemBars = remember(window) { window?.let { WindowInsetsControllerCompat(it, it.decorView) } }

        DisposableEffect(dialogWindow, systemBars) {
            dialogWindow?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            onDispose {
                systemBars?.show(WindowInsetsCompat.Type.systemBars())
                systemBars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                dialogWindow?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
                if (previousOrientation != null) {
                    activity?.requestedOrientation = previousOrientation
                }
            }
        }

        val toggleImmersive: () -> Unit = {
            val enteringImmersive = !immersiveVideo
            immersiveVideo = enteringImmersive
            if (enteringImmersive) {
                systemBars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                systemBars?.hide(WindowInsetsCompat.Type.systemBars())
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                systemBars?.show(WindowInsetsCompat.Type.systemBars())
                systemBars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        BackHandler(enabled = immersiveVideo) { toggleImmersive() }

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
                                Text(
                                    if (offlineUri != null) "Downloaded · ${video.artist ?: "High-Tech Entertainment"}"
                                    else video.artist ?: "High-Tech Entertainment",
                                    color = if (offlineUri != null) ShellGold else ShellMuted,
                                    fontSize = 10.sp
                                )
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
                        val videoCanvasModifier = if (immersiveVideo || landscape) {
                            Modifier.fillMaxWidth().weight(1f)
                        } else {
                            Modifier.fillMaxWidth().height(minOf(availableWidth * (9f / 16f), availableHeight * .46f))
                        }
                        Box(videoCanvasModifier.background(Color(0xFF050506)), contentAlignment = Alignment.Center) {
                            AndroidView(
                                factory = { viewContext ->
                                    (LayoutInflater.from(viewContext)
                                        .inflate(R.layout.hte_video_player, FrameLayout(viewContext), false) as PlayerView).apply {
                                        this.player = player
                                        keepScreenOn = true
                                    }
                                },
                                update = { view -> if (view.player !== player) view.player = player },
                                onRelease = { view ->
                                    view.player = null
                                    view.keepScreenOn = false
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (!videoPlaying || isBuffering) {
                                // Keep the video surface visually intentional before the first frame,
                                // while paused, and during rebuffering instead of exposing an empty black canvas.
                                if (!firstFrameRendered) {
                                    VideoPosterImage(video, "Preview image for ${video.title}", Modifier.fillMaxSize())
                                }
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = .10f), Color.Black.copy(alpha = .48f))
                                        )
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.size(66.dp).clip(CircleShape)
                                            .background(ShellGold.copy(alpha = .94f))
                                            .border(1.dp, Color.White.copy(alpha = .5f), CircleShape)
                                            .clickable(enabled = !isBuffering, onClick = ::toggleVideoPlayback),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isBuffering) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 2.5.dp)
                                        else Icon(Icons.Outlined.PlayArrow, "Play video", tint = Color.Black, modifier = Modifier.size(38.dp))
                                    }
                                }
                            }
                        }
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
                                    onClick = ::toggleVideoPlayback,
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
                                    firstFrameRendered = false
                                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
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

@Composable
private fun YouTubeVideoDialog(video: VideoDto, youtubeId: String, onDismiss: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var embeddedWebView by remember(video.id) { mutableStateOf<WebView?>(null) }
    var webViewLoading by remember(video.id) { mutableStateOf(true) }
    var webViewError by remember(video.id) { mutableStateOf(false) }
    var webViewErrorCode by remember(video.id) { mutableIntStateOf(0) }
    val videoContext = LocalContext.current
    var webViewGeneration by remember(video.id) { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner, embeddedWebView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> embeddedWebView?.onPause()
                Lifecycle.Event.ON_RESUME -> embeddedWebView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF050506)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(video.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(video.artist ?: "High-Tech Entertainment", color = ShellMuted, fontSize = 10.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close video", tint = Color.White) }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                key(webViewGeneration) {
                    AndroidView(
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            embeddedWebView = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadsImagesAutomatically = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                setSupportMultipleWindows(false)
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    webViewLoading = true
                                    webViewError = false
                                    webViewErrorCode = 0
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    webViewLoading = false
                                }
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        webViewLoading = false
                                        webViewError = true
                                    }
                                }
                                override fun onRenderProcessGone(
                                    view: WebView,
                                    detail: android.webkit.RenderProcessGoneDetail
                                ): Boolean {
                                    embeddedWebView = null
                                    runCatching { view.destroy() }
                                    webViewLoading = false
                                    webViewError = true
                                    return true
                                }
                            }
                            setBackgroundColor(android.graphics.Color.BLACK)
                            val targetWebView = this
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onPlayerReady() { targetWebView.post { webViewLoading = false } }

                                @JavascriptInterface
                                fun onPlayerError(code: Int) {
                                    targetWebView.post {
                                        webViewLoading = false
                                        webViewError = true
                                        webViewErrorCode = code
                                    }
                                }
                            }, "HTEVideoBridge")
                            // YouTube requires an HTTP Referer (or equivalent app identity) for
                            // embedded players in app WebViews. Loading a wrapper with a stable HTTPS
                            // base URL supplies that identity; a direct loadUrl(embedUrl) can trigger
                            // YouTube error 153 and leave a black/empty player surface.
                            // Use the real, publicly reachable HTE origin as WebView identity.
                            // A made-up package-name host is not a valid HTTP referrer for YouTube.
                            val appOrigin = "https://suwilanjisinkamba184-cmd.github.io"
                            val appBaseUrl = "$appOrigin/High-tech-entertainment-/"
                            val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"><style>html,body,#player{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}</style></head><body><div id="player" title="HTE video"></div><script>var player;function onYouTubeIframeAPIReady(){player=new YT.Player('player',{videoId:'$youtubeId',playerVars:{autoplay:1,playsinline:1,rel:0,controls:1,origin:'$appOrigin'},events:{onReady:function(){HTEVideoBridge.onPlayerReady();},onError:function(e){HTEVideoBridge.onPlayerError(e.data);}}});}var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';document.head.appendChild(tag);</script></body></html>"""
                            loadDataWithBaseURL(appBaseUrl, html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = {
                        embeddedWebView = null
                        runCatching { it.stopLoading() }
                        runCatching { it.onPause() }
                        runCatching { it.loadUrl("about:blank") }
                        runCatching { it.removeAllViews() }
                        runCatching { it.destroy() }
                    }
                    )
                }
                if (webViewLoading && !webViewError) {
                    CircularProgressIndicator(color = ShellGold, strokeWidth = 2.5.dp)
                }
                if (webViewError) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (webViewErrorCode > 0) "YouTube player error $webViewErrorCode. Open this video in YouTube."
                            else "This YouTube video could not be loaded. Open it in YouTube.",
                            color = ShellSilver, fontSize = 12.sp
                        )
                        Button(
                            onClick = {
                                webViewError = false
                                webViewErrorCode = 0
                                webViewLoading = true
                                webViewGeneration += 1
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ShellGold, contentColor = Color.Black)
                        ) { Text("Retry video", fontWeight = FontWeight.Bold) }
                        OutlinedButton(onClick = { openYouTubeVideo(videoContext, youtubeId) }) {
                            Text("Open in YouTube", color = ShellGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text("Streaming from YouTube • Downloads are available only for HTE original files.", color = ShellMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun openYouTubeVideo(context: Context, youtubeId: String) {
    val url = "https://www.youtube.com/watch?v=$youtubeId"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser or YouTube app is available to open this video.", Toast.LENGTH_LONG).show()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}



@Composable
fun SpotifyEmbedDialog(url: String, onDismiss: () -> Unit) {
    val directEmbedUrl = remember(url) { com.hightechentertainment.app.media.spotifyEmbedUrl(url) }
    var embedUrl by remember(url) { mutableStateOf(directEmbedUrl) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostContext = LocalContext.current
    var loading by remember(url) { mutableStateOf(directEmbedUrl == null) }
    var failed by remember(url) { mutableStateOf(false) }
    var generation by remember(url) { mutableIntStateOf(0) }
    var resolveAttempt by remember(url) { mutableIntStateOf(0) }
    LaunchedEffect(url, directEmbedUrl, resolveAttempt) {
        if (directEmbedUrl != null) {
            embedUrl = directEmbedUrl
            failed = false
            loading = false
        } else {
            loading = true
            failed = false
            val resolved = com.hightechentertainment.app.media.resolveSpotifyEmbedUrl(url)
            embedUrl = resolved
            failed = resolved == null
            loading = false
        }
    }
    var webView by remember(url) { mutableStateOf<WebView?>(null) }
    // Snapshot delegated Compose state before entering AndroidView factory lambdas.
    val embedUrlForRender = embedUrl
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF08090B)).padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("SPOTIFY PLAYER", color = ShellGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("Listen inside HTE Universe", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close Spotify player", tint = Color.White) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp).background(Color(0xFF111318), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                if (loading && embedUrlForRender == null) {
                    CircularProgressIndicator(color = ShellGold, strokeWidth = 2.5.dp)
                } else if (embedUrlForRender != null && !failed) {
                    key(generation) {
                        AndroidView(
                            factory = { ctx -> WebView(ctx).apply {
                                webView = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = true
                                settings.loadsImagesAutomatically = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, page: String?, favicon: android.graphics.Bitmap?) { loading = true; failed = false }
                                    override fun onPageFinished(view: WebView?, page: String?) { loading = false }
                                    override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                        if (request?.isForMainFrame == true) { loading = false; failed = true }
                                    }
                                }
                                setBackgroundColor(android.graphics.Color.rgb(17,19,24))
                                val playerHeight = if (embedUrlForRender.contains("/track/")) "152px" else "352px"
                                val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"><style>html,body{margin:0;padding:0;width:100%;height:100%;background:#111318;display:flex;align-items:center;justify-content:center}iframe{display:block;width:100%;min-width:280px;height:$playerHeight;border:0;border-radius:12px}</style></head><body><iframe title="Spotify player" src="$embedUrlForRender" allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture" allowfullscreen></iframe></body></html>"""
                                loadDataWithBaseURL("https://open.spotify.com", html, "text/html", "UTF-8", null)
                            } },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { view -> webView = null; runCatching { view.stopLoading() }; runCatching { view.loadUrl("about:blank") }; runCatching { view.destroy() } }
                        )
                    }
                    if (loading) CircularProgressIndicator(color = ShellGold, strokeWidth = 2.5.dp)
                } else {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Spotify's embedded player could not be loaded for this link.", color = ShellSilver, fontSize = 12.sp)
                        TextButton(onClick = { failed = false; loading = true; generation += 1; resolveAttempt += 1 }) { Text("Retry", color = ShellGold) }
                        TextButton(onClick = { runCatching { val appUri = com.hightechentertainment.app.media.spotifyAppUri(url) ?: url; hostContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUri))) } }) { Text("Open Spotify app", color = ShellSilver) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = {
                    runCatching {
                        val appUri = com.hightechentertainment.app.media.spotifyAppUri(url) ?: url
                        hostContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUri)))
                    }.onFailure {
                        Toast.makeText(hostContext, "Spotify could not be opened. Please check the link and try again.", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = ShellGold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in Spotify app", color = ShellGold, fontSize = 11.sp)
                }
            }
            Text("Official Spotify embed. Playback availability and full-track access are controlled by Spotify and your account.", color = ShellMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun formatVideoTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
