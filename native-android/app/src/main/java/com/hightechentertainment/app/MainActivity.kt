package com.hightechentertainment.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Repeat
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
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.hightechentertainment.app.playback.PlaybackService
import com.hightechentertainment.app.playback.StartupJingle
import com.hightechentertainment.app.playback.OfflineMusicDownloads
import com.hightechentertainment.app.data.NativeTrack
import com.hightechentertainment.app.data.AuthResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val HteBlack = Color(0xFF080808)
private val HteSurface = Color(0xFF151515)
private val HteGold = Color(0xFFD7A83E)
private val HteMuted = Color(0xFFAAA39A)

class MainActivity : ComponentActivity() {
    private val hteApplication: HteApplication
        get() = application as HteApplication
    private val catalogRepository
        get() = hteApplication.catalogRepository
    private val authRepository
        get() = hteApplication.authRepository
    private val sessionManager
        get() = hteApplication.sessionManager
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val offlineDownloads by lazy { OfflineMusicDownloads(applicationContext) }
    private var playbackPlayer by mutableStateOf<Player?>(null)
    private var playbackIsReady by mutableStateOf(false)
    private var playbackIsActive by mutableStateOf(false)
    private var sponsoredAudioPlaying by mutableStateOf(false)
    private var shuffleEnabled by mutableStateOf(false)
    private var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var knownTracks: List<NativeTrack> = emptyList()
    private var currentTrackState by mutableStateOf<NativeTrack?>(null)
    private var playbackPositionMs by mutableLongStateOf(0L)
    private var playbackDurationMs by mutableLongStateOf(0L)
    private var pendingAuthMessage by mutableStateOf<String?>(null)
    private var oauthInProgress by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectToPlaybackService()
        if (savedInstanceState == null) StartupJingle.playOnce(applicationContext)
        setContent {
            HteTheme {
                val signedInSession by sessionManager.session.collectAsState()
                val authStatus by sessionManager.status.collectAsState()
                var authBusy by remember { mutableStateOf(false) }
                var authMessage by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) { sessionManager.restore() }
                if (signedInSession == null) {
                    NativeAuthScreen(
                        busy = authBusy || oauthInProgress || authStatus == "restoring",
                        message = authMessage ?: pendingAuthMessage,
                        onSignIn = { email, password ->
                            authBusy = true
                            authMessage = null
                            lifecycleScope.launch {
                                when (val result = authRepository.signInWithPassword(email, password)) {
                                    is AuthResult.Success -> sessionManager.accept(result.session)
                                    is AuthResult.Failure -> authMessage = result.message
                                    is AuthResult.Notice -> authMessage = result.message
                                }
                                authBusy = false
                            }
                        },
                        onSignUp = { email, password ->
                            authBusy = true
                            authMessage = null
                            lifecycleScope.launch {
                                when (val result = authRepository.signUp(email, password)) {
                                    is AuthResult.Success -> sessionManager.accept(result.session)
                                    is AuthResult.Failure -> authMessage = result.message
                                    is AuthResult.Notice -> authMessage = result.message
                                }
                                authBusy = false
                            }
                        },
                        onReset = { email ->
                            authBusy = true
                            authMessage = null
                            lifecycleScope.launch {
                                when (val result = authRepository.requestPasswordReset(email)) {
                                    is AuthResult.Success -> authMessage = "Password reset requested."
                                    is AuthResult.Failure -> authMessage = result.message
                                    is AuthResult.Notice -> authMessage = result.message
                                }
                                authBusy = false
                            }
                        },
                        onGoogleSignIn = { startGoogleOAuth() }
                    )
                } else {
                val activeSession = requireNotNull(signedInSession)
                var tracks by remember { mutableStateOf<List<NativeTrack>>(emptyList()) }
                var favouriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
                var founderAccess by remember { mutableStateOf(false) }
                var loading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                val currentTrack = currentTrackState
                val playing = playbackIsActive
                LaunchedEffect(playing, currentTrack?.id) {
                    while (playing) {
                        val player = playbackPlayer
                        playbackPositionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
                        playbackDurationMs = player?.duration?.takeIf { it > 0L } ?: 0L
                        delay(500L)
                    }
                }

                fun loadCatalog() {
                    loading = true
                    error = null
                    lifecycleScope.launch {
                        val result = catalogRepository.publishedTracks()
                        tracks = result.items
                        knownTracks = result.items
                        currentTrackState = playbackPlayer?.currentMediaItem?.mediaId?.let { id ->
                            result.items.firstOrNull { it.id == id }
                        } ?: currentTrackState
                        error = result.error
                        loading = false
                    }
                }
                LaunchedEffect(activeSession.userId) {
                    loadCatalog()
                    hteApplication.userLibraryRepository.favouriteTrackIds()
                        .onSuccess { favouriteIds = it.toSet() }
                    founderAccess = hteApplication.featureRepository.hasFounderAccess().getOrDefault(false)
                }

                HighTechApp(
                    favouriteIds = favouriteIds,
                    featureRepository = hteApplication.featureRepository,
                    userLibraryRepository = hteApplication.userLibraryRepository,
                    sponsoredAudioRepository = hteApplication.sponsoredAudioRepository,
                    sponsoredAudioPlaying = sponsoredAudioPlaying,
                    isFounderAccount = founderAccess,
                    userId = activeSession.userId,
                    onDownloadTrack = { track ->
                        val id = offlineDownloads.enqueue(track)
                        error = if (id == null) "This track has no secure downloadable audio file." else "Download queued. Android will notify you when it is ready offline."
                    },
                    onToggleFavourite = { track ->
                        lifecycleScope.launch {
                            val isSaved = track.id in favouriteIds
                            val result = if (isSaved) {
                                hteApplication.userLibraryRepository.removeFavourite(track.id)
                            } else {
                                hteApplication.userLibraryRepository.addFavourite(track.id)
                            }
                            if (result.successful) {
                                favouriteIds = if (isSaved) favouriteIds - track.id else favouriteIds + track.id
                            } else {
                                error = result.message ?: "Could not update favourites."
                            }
                        }
                    },
                    tracks = tracks,
                    loading = loading,
                    error = error,
                    currentTrack = currentTrack,
                    playing = playing,
                    onRefresh = { loadCatalog() },
                    onPlay = { track ->
                        val activePlayer = playbackPlayer
                        val playableTracks = tracks.filter { !it.audioUrl.isNullOrBlank() }
                        val startIndex = playableTracks.indexOfFirst { it.id == track.id }
                        when {
                            !playbackIsReady || activePlayer == null ->
                                error = "The audio player is still connecting. Please try again."
                            startIndex < 0 ->
                                error = "This track does not have a playable audio file yet."
                            else -> lifecycleScope.launch {
                                val sponsoredAd = hteApplication.sponsoredAudioRepository.nextActiveAd().getOrNull()
                                val musicQueue = if (sponsoredAd != null) playableTracks.drop(startIndex) else playableTracks
                                val queue = buildList {
                                    sponsoredAd?.let { ad ->
                                        add(MediaItem.Builder().setMediaId("hte-ad:${ad.id}").setUri(ad.audioUrl).build())
                                    }
                                    addAll(musicQueue.map { item ->
                                        val uri = offlineDownloads.localUri(item.id)?.toString() ?: item.audioUrl!!
                                        MediaItem.Builder().setMediaId(item.id).setUri(uri).build()
                                    })
                                }
                                activePlayer.setMediaItems(queue, if (sponsoredAd != null) 0 else startIndex, C.TIME_UNSET)
                                activePlayer.shuffleModeEnabled = if (sponsoredAd == null) shuffleEnabled else false
                                activePlayer.repeatMode = if (sponsoredAd == null) repeatMode else Player.REPEAT_MODE_OFF
                                currentTrackState = if (sponsoredAd == null) track else null
                                sponsoredAudioPlaying = sponsoredAd != null
                                activePlayer.prepare()
                                activePlayer.play()
                            }
                        }
                    },
                    onPause = { playbackPlayer?.pause() },
                    onResume = { playbackPlayer?.play() },
                    onNext = { playbackPlayer?.seekToNextMediaItem() },
                    onPrevious = { playbackPlayer?.seekToPreviousMediaItem() },
                    onToggleShuffle = {
                        shuffleEnabled = !shuffleEnabled
                        playbackPlayer?.shuffleModeEnabled = shuffleEnabled
                    },
                    onToggleRepeat = {
                        repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                        playbackPlayer?.repeatMode = repeatMode
                    },
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    positionMs = playbackPositionMs,
                    durationMs = playbackDurationMs,
                    onSeek = { position -> playbackPlayer?.seekTo(position) },
                    onSignOut = {
                        lifecycleScope.launch { sessionManager.signOut() }
                    }
                )
                }
            }
        }
        handleOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun startGoogleOAuth() {
        val redirectUri = "com.hightechentertainment.app://login-callback"
        val authorizationUrl = authRepository.googleOAuthAuthorizationUrl(redirectUri)
        if (authorizationUrl == null) {
            pendingAuthMessage = "Google sign-in could not be started. Please try again."
            return
        }
        pendingAuthMessage = null
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)))
        }.onFailure {
            pendingAuthMessage = "No browser is available to complete Google sign-in."
        }
    }

    private fun handleOAuthCallback(callbackIntent: Intent?) {
        val callback = callbackIntent?.data ?: return
        if (callback.scheme != "com.hightechentertainment.app" || callback.host != "login-callback") return
        val providerError = callback.getQueryParameter("error_description")
            ?: callback.getQueryParameter("error")
        if (!providerError.isNullOrBlank()) {
            pendingAuthMessage = "Google sign-in was not completed. Please try again."
            return
        }
        val code = callback.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            pendingAuthMessage = "Google sign-in returned an invalid callback. Please try again."
            return
        }
        if (oauthInProgress) return
        oauthInProgress = true
        pendingAuthMessage = null
        lifecycleScope.launch {
            try {
                when (val result = authRepository.exchangeGoogleOAuthCode(code)) {
                    is AuthResult.Success -> sessionManager.accept(result.session)
                    is AuthResult.Failure -> pendingAuthMessage = result.message
                    is AuthResult.Notice -> pendingAuthMessage = result.message
                }
            } finally {
                oauthInProgress = false
            }
        }
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }
                .onSuccess { controller ->
                    playbackPlayer = controller
                    playbackIsReady = true
                    playbackIsActive = controller.isPlaying
                    shuffleEnabled = controller.shuffleModeEnabled
                    repeatMode = controller.repeatMode
                    val activeMediaId = controller.currentMediaItem?.mediaId
                    sponsoredAudioPlaying = activeMediaId?.startsWith("hte-ad:") == true
                    currentTrackState = if (sponsoredAudioPlaying) null else activeMediaId?.let { id ->
                        knownTracks.firstOrNull { it.id == id }
                    }
                    controller.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            playbackIsActive = isPlaying
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            val mediaId = mediaItem?.mediaId
                            if (mediaId?.startsWith("hte-ad:") == true) {
                                sponsoredAudioPlaying = true
                                currentTrackState = null
                                val adId = mediaId.removePrefix("hte-ad:")
                                lifecycleScope.launch { hteApplication.sponsoredAudioRepository.recordImpression(adId) }
                            } else {
                                sponsoredAudioPlaying = false
                                controller.shuffleModeEnabled = shuffleEnabled
                                controller.repeatMode = repeatMode
                                currentTrackState = mediaId?.let { id -> knownTracks.firstOrNull { it.id == id } }
                                mediaId?.let { trackId ->
                                    lifecycleScope.launch { hteApplication.userLibraryRepository.recordListening(trackId) }
                                }
                            }
                        }
                    })
                }
                .onFailure {
                    playbackIsReady = false
                    playbackIsActive = false
                }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        playbackPlayer = null
        playbackIsReady = false
        super.onDestroy()
    }
}

@Composable
private fun HteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HteGold,
            onPrimary = HteBlack,
            background = HteBlack,
            onBackground = Color.White,
            surface = HteSurface,
            onSurface = Color.White,
            secondary = Color(0xFFC5C5C5)
        ),
        content = content
    )
}

private data class AppDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val destinations = listOf(
    AppDestination("Home", Icons.Rounded.Home),
    AppDestination("Discover", Icons.Rounded.Search),
    AppDestination("Videos", Icons.Rounded.VideoLibrary),
    AppDestination("Library", Icons.Rounded.LibraryMusic),
    AppDestination("Favourites", Icons.Rounded.FavoriteBorder)
)

@Composable
private fun HighTechApp(
    tracks: List<NativeTrack>,
    favouriteIds: Set<String>,
    featureRepository: com.hightechentertainment.app.data.FeatureRepository,
    userLibraryRepository: com.hightechentertainment.app.data.UserLibraryRepository,
    sponsoredAudioRepository: com.hightechentertainment.app.data.SponsoredAudioRepository,
    sponsoredAudioPlaying: Boolean,
    isFounderAccount: Boolean,
    userId: String?,
    onToggleFavourite: (NativeTrack) -> Unit,
    onDownloadTrack: (NativeTrack) -> Unit,
    loading: Boolean,
    error: String?,
    currentTrack: NativeTrack?,
    playing: Boolean,
    onRefresh: () -> Unit,
    onPlay: (NativeTrack) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val visibleTracks = tracks.filter {
        it.title.contains(search, true) || it.artist.contains(search, true) ||
            (it.genre?.contains(search, true) == true)
    }

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
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        TextButton(onClick = onSignOut) { Text("SIGN OUT", color = HteGold, fontSize = 10.sp) }
                        IconButton(onClick = onRefresh, enabled = !loading) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh catalogue", tint = HteGold)
                        }
                        Box {
                            TextButton(onClick = { moreExpanded = true }) { Text("MORE", color = HteGold) }
                            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                                buildList {
                                    add("Bookings" to 5)
                                    add("Profile" to 6)
                                    add("App updates" to 8)
                                    if (isFounderAccount) add("Founder Studio" to 7)
                                }.forEach { (label, index) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        selectedTab = index
                                        moreExpanded = false
                                    })
                                }
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text(
                            when (selectedTab) {
                                1 -> "Discover music"
                                2 -> "Music videos"
                                3 -> "Your library"
                                4 -> "Favourites"
                                5 -> "Your bookings"
                                6 -> "Your profile"
                                7 -> "Founder Studio"
                                8 -> "App updates"
                                else -> "Music. Beats. Production. Beyond."
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Quality Sound. Global Vibes.", color = HteMuted)
                    }
                }
                if (selectedTab == 0 || selectedTab == 1) {
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
                                Text("Your sound.\nYour world.", color = Color.White, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(10.dp))
                                Text("Discover published music from the High-Tech family.", color = HteMuted)
                            }
                        }
                    }
                }
                if (selectedTab == 0 || selectedTab == 1) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Explore the catalogue", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.weight(1f))
                            Text("${tracks.size} tracks", color = HteMuted, fontSize = 12.sp)
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                                .background(HteSurface, RoundedCornerShape(14.dp)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = HteMuted)
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = search,
                                onValueChange = { search = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                                decorationBox = { inner ->
                                    if (search.isEmpty()) Text("Search tracks, artists or genres", color = HteMuted)
                                    inner()
                                }
                            )
                        }
                    }
                    when {
                        loading -> item {
                            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = HteGold)
                            }
                        }
                        error != null && tracks.isEmpty() -> item {
                            StatusCard(error, "Retry", onRefresh)
                        }
                        visibleTracks.isEmpty() -> item {
                            StatusCard(
                                if (tracks.isEmpty()) "No published tracks are available yet." else "No tracks match your search.",
                                "Refresh",
                                onRefresh
                            )
                        }
                        else -> items(visibleTracks, key = { it.id }) { track ->
                            TrackRow(
                                track = track,
                                isCurrent = currentTrack?.id == track.id,
                                playing = playing && currentTrack?.id == track.id,
                                isFavorite = track.id in favouriteIds,
                                onFavourite = { onToggleFavourite(track) },
                                onDownload = { onDownloadTrack(track) },
                                onClick = { onPlay(track) }
                            )
                        }
                    }
                } else if (selectedTab == 4) {
                    val savedTracks = tracks.filter { it.id in favouriteIds }
                    if (savedTracks.isEmpty()) {
                        item { StatusCard("Your favourites will appear here when you save a track.", "Refresh", onRefresh) }
                    } else {
                        items(savedTracks, key = { "favourite-${it.id}" }) { track ->
                            TrackRow(
                                track = track,
                                isCurrent = currentTrack?.id == track.id,
                                playing = playing && currentTrack?.id == track.id,
                                isFavorite = true,
                                onFavourite = { onToggleFavourite(track) },
                                onDownload = { onDownloadTrack(track) },
                                onClick = { onPlay(track) }
                            )
                        }
                    }
                } else {
                    item {
                        Box(Modifier.fillParentMaxSize()) {
                            AdditionalFeatureScreen(
                                destination = when (selectedTab) {
                                    2 -> "Videos"
                                    3 -> "Library"
                                    5 -> "Bookings"
                                    6 -> "Profile"
                                    7 -> "Founder Studio"
                                    8 -> "App Updates"
                                    else -> "Library"
                                },
                                tracks = tracks,
                                features = featureRepository,
                                library = userLibraryRepository,
                                sponsoredAudio = sponsoredAudioRepository,
                                isFounder = isFounderAccount,
                                userId = userId,
                                onPlayTrack = onPlay
                            )
                        }
                    }
                }
                if (error != null && tracks.isNotEmpty()) {
                    item { Text(error, color = HteMuted, modifier = Modifier.padding(horizontal = 20.dp)) }
                }
            }

            if (currentTrack != null || sponsoredAudioPlaying) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF201A10)).padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    var seekPreview by remember(currentTrack?.id, sponsoredAudioPlaying) { mutableStateOf<Float?>(null) }
                    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = seekPreview ?: progress,
                        onValueChange = { seekPreview = it },
                        onValueChangeFinished = {
                            seekPreview?.let { if (durationMs > 0L) onSeek((it * durationMs).toLong()) }
                            seekPreview = null
                        },
                        enabled = durationMs > 0L && !sponsoredAudioPlaying
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (sponsoredAudioPlaying) "Sponsored audio" else currentTrack?.title.orEmpty(), color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (sponsoredAudioPlaying) "Advertisement" else currentTrack?.artist.orEmpty(), color = HteMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!sponsoredAudioPlaying) IconButton(onClick = onPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track", tint = HteMuted)
                    }
                    IconButton(onClick = { if (playing) onPause() else onResume() }) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = HteGold
                        )
                    }
                    if (!sponsoredAudioPlaying) {
                        IconButton(onClick = onNext) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next track", tint = HteMuted)
                        }
                        IconButton(onClick = onToggleShuffle) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Toggle shuffle", tint = if (shuffleEnabled) HteGold else HteMuted)
                        }
                        IconButton(onClick = onToggleRepeat) {
                            Icon(Icons.Rounded.Repeat, contentDescription = "Cycle repeat mode", tint = if (repeatMode == Player.REPEAT_MODE_OFF) HteMuted else HteGold)
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: NativeTrack,
    isCurrent: Boolean,
    playing: Boolean,
    isFavorite: Boolean,
    onFavourite: () -> Unit,
    onDownload: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCurrent) Color(0xFF2A2112) else HteSurface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(52.dp).background(Color(0xFF2A2112), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = HteGold
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = HteMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                track.genre?.let { Text(it, color = HteGold, fontSize = 10.sp) }
            }
            IconButton(onClick = onFavourite) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                    tint = if (isFavorite) HteGold else HteMuted
                )
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, contentDescription = "Download for offline listening", tint = HteMuted)
            }
            Text("PLAY", color = HteGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusCard(message: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = HteSurface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(message, color = HteMuted)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(action, color = HteGold) }
        }
    }
}
