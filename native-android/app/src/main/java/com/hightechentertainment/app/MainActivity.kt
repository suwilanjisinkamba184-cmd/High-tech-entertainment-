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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.material3.MiniController
import androidx.media3.ui.compose.material3.NextButton
import androidx.media3.ui.compose.material3.PlayPauseButton
import androidx.media3.ui.compose.material3.Player as Media3ComposePlayer
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.material3.PreviousButton
import androidx.media3.ui.compose.material3.RepeatButton
import androidx.media3.ui.compose.material3.ShuffleButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.hightechentertainment.app.playback.PlaybackService
import com.hightechentertainment.app.playback.StartupJingle
import com.hightechentertainment.app.playback.OfflineMusicDownloads
import com.hightechentertainment.app.data.NativeTrack
import com.hightechentertainment.app.data.NativeArtist
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

    private fun setVoiceDucking(active: Boolean) {
        // Keep music audible under the assistant, then restore full level on listening/disconnect.
        playbackPlayer?.volume = if (active) 0.25f else 1f
    }

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
                    voiceTokenRepository = hteApplication.voiceSessionTokenRepository,
                    userLibraryRepository = hteApplication.userLibraryRepository,
                    sponsoredAudioRepository = hteApplication.sponsoredAudioRepository,
                    downloads = offlineDownloads,
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
                    player = playbackPlayer,
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
                                        add(MediaItem.Builder().setMediaId("hte-ad:${ad.id}").setUri(ad.audioUrl).setMediaMetadata(MediaMetadata.Builder().setTitle("Sponsored audio").setArtist("High-Tech Entertainment").setAlbumTitle("High-Tech Entertainment").build()).build())
                                    }
                                    addAll(musicQueue.map { item ->
                                        val uri = offlineDownloads.localUri(item.id)?.toString() ?: item.audioUrl!!
                                        MediaItem.Builder().setMediaId(item.id).setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).setAlbumTitle("High-Tech Entertainment").build()).build()
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
                    onStop = { playbackPlayer?.stop() },
                    onPlayFavourites = {
                        val activePlayer = playbackPlayer
                        val favouriteQueue = tracks.filter { it.id in favouriteIds && !it.audioUrl.isNullOrBlank() }
                        when {
                            !playbackIsReady || activePlayer == null ->
                                error = "The audio player is still connecting. Please try again."
                            favouriteQueue.isEmpty() ->
                                error = "You do not have any downloadable favourites to play yet."
                            else -> lifecycleScope.launch {
                                val sponsoredAd = hteApplication.sponsoredAudioRepository.nextActiveAd().getOrNull()
                                val queue = buildList {
                                    sponsoredAd?.let { ad ->
                                        add(MediaItem.Builder().setMediaId("hte-ad:${ad.id}").setUri(ad.audioUrl).setMediaMetadata(MediaMetadata.Builder().setTitle("Sponsored audio").setArtist("High-Tech Entertainment").setAlbumTitle("High-Tech Entertainment").build()).build())
                                    }
                                    addAll(favouriteQueue.map { item ->
                                        val uri = offlineDownloads.localUri(item.id)?.toString() ?: item.audioUrl!!
                                        MediaItem.Builder().setMediaId(item.id).setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).setAlbumTitle("High-Tech Entertainment").build()).build()
                                    })
                                }
                                activePlayer.setMediaItems(queue, if (sponsoredAd != null) 0 else 0, C.TIME_UNSET)
                                activePlayer.shuffleModeEnabled = false
                                activePlayer.repeatMode = repeatMode
                                currentTrackState = if (sponsoredAd == null) favouriteQueue.first() else null
                                sponsoredAudioPlaying = sponsoredAd != null
                                activePlayer.prepare()
                                activePlayer.play()
                            }
                        }
                    },
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
                    onVoiceAudioActive = { active -> setVoiceDucking(active) },
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
    AppDestination("Favourites", Icons.Rounded.FavoriteBorder),
    AppDestination("Downloads", Icons.Rounded.Download)
)

@Composable
private fun HighTechApp(
    tracks: List<NativeTrack>,
    favouriteIds: Set<String>,
    featureRepository: com.hightechentertainment.app.data.FeatureRepository,
    voiceTokenRepository: com.hightechentertainment.app.data.VoiceSessionTokenRepository,
    userLibraryRepository: com.hightechentertainment.app.data.UserLibraryRepository,
    sponsoredAudioRepository: com.hightechentertainment.app.data.SponsoredAudioRepository,
    downloads: OfflineMusicDownloads,
    sponsoredAudioPlaying: Boolean,
    isFounderAccount: Boolean,
    userId: String?,
    onToggleFavourite: (NativeTrack) -> Unit,
    onDownloadTrack: (NativeTrack) -> Unit,
    loading: Boolean,
    error: String?,
    currentTrack: NativeTrack?,
    player: Player?,
    playing: Boolean,
    onRefresh: () -> Unit,
    onPlay: (NativeTrack) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
    onPlayFavourites: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onVoiceAudioActive: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var artists by remember { mutableStateOf<List<NativeArtist>>(emptyList()) }
    var artistsLoading by remember { mutableStateOf(false) }
    var artistsError by remember { mutableStateOf<String?>(null) }
    var artistRefreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedTab, artistRefreshKey) {
        if (selectedTab == 11) {
            artistsLoading = true
            artistsError = null
            featureRepository.publishedArtists()
                .onSuccess { artists = it }
                .onFailure { artistsError = it.message ?: "Could not load artists." }
            artistsLoading = false
        }
    }
    val context = LocalContext.current
    val visibleTracks = tracks.filter {
        it.title.contains(search, true) || it.artist.contains(search, true) ||
            (it.genre?.contains(search, true) == true)
    }
    fun shareTrack(track: NativeTrack) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Listen to ${track.title} by ${track.artist} on High-Tech Entertainment.\nhttps://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/"
            )
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share track"))
    }

    Scaffold(
        containerColor = HteBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101114), contentColor = HteGold, tonalElevation = 12.dp) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label, maxLines = 1, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HteBlack, selectedTextColor = HteGold, indicatorColor = HteGold,
                            unselectedIconColor = HteMuted, unselectedTextColor = HteMuted
                        )
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp).background(
                                Brush.linearGradient(listOf(Color(0xFFFFE9B0), HteGold, Color(0xFF795018))),
                                RoundedCornerShape(15.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) { Text("HT", color = HteBlack, fontWeight = FontWeight.Black, fontSize = 17.sp) }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("HIGH-TECH", color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp)
                            Text("ENTERTAINMENT", color = HteGold, fontSize = 9.sp, letterSpacing = 2.4.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(color = Color(0xFF201B12), shape = RoundedCornerShape(50)) {
                            Text("3.1", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = HteGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onRefresh, enabled = !loading) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh catalogue", tint = HteGold)
                        }
                        Box {
                            TextButton(onClick = { moreExpanded = true }) { Text("MENU", color = HteGold, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                                buildList {
                                    add("Artists" to 11)
                                    add("Downloads" to 5)
                                    add("Bookings" to 6)
                                    add("Profile & account" to 7)
                                    add("App updates" to 9)
                                    add("Voice Companion" to 10)
                                    if (isFounderAccount) add("Founder Studio" to 8)
                                    add("Sign out" to -1)
                                }.forEach { (label, index) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        moreExpanded = false
                                        if (index == -1) onSignOut() else selectedTab = index
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
                                5 -> "Offline downloads"
                                6 -> "Your bookings"
                                7 -> "Your profile"
                                8 -> "Founder Studio"
                                9 -> "App updates"
                                10 -> "Voice Companion"
                                11 -> "Artists"
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
                                    Brush.linearGradient(listOf(Color(0xFF49351A), Color(0xFF211A10), Color(0xFF151515)))
                                ).padding(24.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = Color(0x33E4B653), shape = RoundedCornerShape(50)) {
                                        Text("THE HIGH-TECH EXPERIENCE", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            color = HteGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = HteGold)
                                }
                                Spacer(Modifier.height(18.dp))
                                Text("Music without\nboundaries.", color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(10.dp))
                                Text("Discover the latest sounds, build your library and experience the High-Tech family.",
                                    color = Color(0xFFD0C7B8), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(20.dp))
                                Text("MUSIC. BEATS. PRODUCTION. BEYOND.", color = HteGold, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
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
                                onShare = { shareTrack(track) },
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
                                onShare = { shareTrack(track) },
                                onClick = { onPlay(track) }
                            )
                        }
                    }
                } else if (selectedTab == 11) {
                    when {
                        artistsLoading && artists.isEmpty() -> item {
                            Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = HteGold)
                            }
                        }
                        artistsError != null && artists.isEmpty() -> item {
                            StatusCard(artistsError ?: "Could not load artists.", "Retry") { artistRefreshKey++ }
                        }
                        artists.isEmpty() -> item {
                            StatusCard("No published artist profiles are available yet.", "Refresh") { artistRefreshKey++ }
                        }
                        else -> items(artists, key = { "artist-${it.id}" }) { artist ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = HteSurface)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(58.dp).background(
                                            Brush.linearGradient(listOf(Color(0xFF49351A), Color(0xFF201A10))),
                                            CircleShape
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(artist.name.take(1).uppercase(), color = HteGold,
                                            fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                        Text(artist.name, color = Color.White, fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            artist.bio.ifBlank { "High-Tech Entertainment artist" },
                                            color = HteMuted, fontSize = 13.sp, maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Box(Modifier.fillParentMaxSize()) {
                            if (selectedTab == 10) {
                                VoiceCompanionScreen(
                                    onPause = onPause,
                                    onResume = onResume,
                                    onNext = onNext,
                                    onPrevious = onPrevious,
                                    onStop = onStop,
                                    onNavigate = { destination ->
                                        selectedTab = when (destination) {
                                            "Home" -> 0
                                            "Discover" -> 1
                                            "Videos" -> 2
                                            "Library" -> 3
                                            "Favourites" -> 4
                                            "Downloads" -> 5
                                            "Bookings" -> 6
                                            "Profile" -> 7
                                            "App Updates" -> 9
                                            "Voice Companion" -> 10
                                            "Artists" -> 11
                                            "Founder Studio" -> if (isFounderAccount) 8 else selectedTab
                                            else -> selectedTab
                                        }
                                    },
                                    onSearch = { query ->
                                        search = query
                                        selectedTab = 1
                                    },
                                    onPlayFavourites = onPlayFavourites,
                                    onVoiceAudioActive = onVoiceAudioActive,
                                    isFounder = isFounderAccount,
                                    tokenRepository = voiceTokenRepository,
                                    userId = userId.orEmpty(),
                                    tracks = tracks,
                                    favouriteIds = favouriteIds
                                )
                            } else AdditionalFeatureScreen(
                                destination = when (selectedTab) {
                                    2 -> "Videos"
                                    3 -> "Library"
                                    5 -> "Downloads"
                                    6 -> "Bookings"
                                    7 -> "Profile"
                                    8 -> "Founder Studio"
                                    9 -> "App Updates"
                                    else -> "Library"
                                },
                                tracks = tracks,
                                features = featureRepository,
                                library = userLibraryRepository,
                                sponsoredAudio = sponsoredAudioRepository,
                                downloads = downloads,
                                isFounder = isFounderAccount,
                                userId = userId,
                                onPlayTrack = onPlay,
                                audioWasPlaying = playing,
                                onPauseAudio = onPause,
                                onResumeAudio = onResume
                            )
                        }
                    }
                }
                if (error != null && tracks.isNotEmpty()) {
                    item { Text(error, color = HteMuted, modifier = Modifier.padding(horizontal = 20.dp)) }
                }
            }

            if ((currentTrack != null || sponsoredAudioPlaying) && player != null) {
                MiniController(
                    player = player,
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF201A10))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    defaultArtwork = painterResource(R.drawable.hte_launcher_foreground),
                    onClick = { showNowPlaying = true },
                    playerControls = { activePlayer ->
                        PreviousButton(activePlayer)
                        PlayPauseButton(activePlayer)
                        NextButton(activePlayer)
                        ShuffleButton(activePlayer)
                        RepeatButton(activePlayer)
                    }
                )
            }
            if (showNowPlaying && player != null) {
                AudioNowPlayingDialog(
                    player = player,
                    title = if (sponsoredAudioPlaying) "Sponsored audio" else currentTrack?.title.orEmpty(),
                    artist = if (sponsoredAudioPlaying) "High-Tech Entertainment" else currentTrack?.artist.orEmpty(),
                    onClose = { showNowPlaying = false }
                )
            }
        }
    }
}


@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
private fun AudioNowPlayingDialog(
    player: Player,
    title: String,
    artist: String,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("NOW PLAYING", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("CLOSE", color = Color(0xFFD4AF37)) }
            }
            Media3ComposePlayer(
                player = player,
                modifier = Modifier.fillMaxWidth().weight(1f),
                showControls = true,
                artwork = {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(Color(0xFF49351A), Color(0xFF151515), Color.Black))
                        ),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text("HTE", color = Color(0xFFD4AF37), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                    }
                },
                topControls = { PlayerDefaults.TopControls(player) },
                centerControls = { PlayerDefaults.CenterControls(player) },
                bottomControls = { PlayerDefaults.BottomControls(player) }
            )
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(artist, color = Color(0xFFB8B0A3), style = MaterialTheme.typography.bodyMedium)
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
    onShare: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, HteGold.copy(alpha = .55f)) else null,
        colors = CardDefaults.cardColors(containerColor = if (isCurrent) Color(0xFF211B10) else HteSurface)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF4B371A), Color(0xFF201A11))), RoundedCornerShape(15.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause ${track.title}" else "Play ${track.title}",
                    tint = HteGold, modifier = Modifier.size(28.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = HteMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                track.genre?.let { Text(it.uppercase(), color = HteGold, fontSize = 9.sp, letterSpacing = .8.sp, fontWeight = FontWeight.Bold) }
            }
            IconButton(onClick = onFavourite) {
                Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                    tint = if (isFavorite) HteGold else HteMuted)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = "Share track", tint = HteMuted)
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, contentDescription = "Download for offline listening", tint = HteMuted)
            }
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
