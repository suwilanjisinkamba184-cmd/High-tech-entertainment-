package com.hightechentertainment.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.hightechentertainment.app.data.AuthResult
import com.hightechentertainment.app.playback.StartupJingle
import com.hightechentertainment.app.playback.PlaybackService
import com.hightechentertainment.app.playback.OfflineMusicDownloads
import kotlinx.coroutines.launch

/**
 * Flutter owns presentation. This bridge reuses the existing process-scoped
 * Supabase repositories; it does not create a second backend or auth store.
 */
class FlutterHostActivity : FlutterActivity() {
    private val startupChannelName = "com.hightechentertainment.app/startup"
    private val appChannelName = "com.hightechentertainment.app/data"
    private val hteApplication: HteApplication
        get() = application as HteApplication
    private var playbackFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val offlineDownloads by lazy { OfflineMusicDownloads(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { hteApplication.sessionManager.restore() }
        connectToPlaybackService()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, startupChannelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "playStartup" -> result.success(
                        StartupJingle.playForSplash(applicationContext)
                    )
                    "stopStartup" -> {
                        StartupJingle.stopForSplash()
                        result.success(true)
                    }
                    "openNativeApp" -> {
                        startActivity(Intent(this, MainActivity::class.java))
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, appChannelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getSession" -> result.success(sessionMap())
                    "getPlaybackState" -> result.success(playbackState())
                    "playTrack" -> playTrack(call.argument<String>("trackId").orEmpty(), result)
                    "pausePlayback" -> {
                        mediaController?.pause()
                        result.success(playbackState())
                    }
                    "resumePlayback" -> {
                        mediaController?.play()
                        result.success(playbackState())
                    }
                    "nextTrack" -> {
                        mediaController?.seekToNextMediaItem()
                        result.success(playbackState())
                    }
                    "previousTrack" -> {
                        mediaController?.seekToPreviousMediaItem()
                        result.success(playbackState())
                    }
                    "downloadTrack" -> lifecycleScope.launch {
                        val id = call.argument<String>("trackId").orEmpty()
                        val track = hteApplication.catalogRepository.publishedTracks().items.firstOrNull { it.id == id }
                        val downloadId = track?.let { offlineDownloads.enqueue(it) }
                        result.success(mapOf("success" to (downloadId != null), "downloadId" to downloadId))
                    }
                    "signIn" -> authenticate(call.argument<String>("email"), call.argument<String>("password"), result, false)
                    "signUp" -> authenticate(call.argument<String>("email"), call.argument<String>("password"), result, true)
                    "signOut" -> lifecycleScope.launch {
                        hteApplication.sessionManager.signOut()
                        result.success(mapOf("success" to true, "session" to null))
                    }
                    "getTracks" -> lifecycleScope.launch {
                        runCatching { hteApplication.catalogRepository.publishedTracks() }
                            .onSuccess { catalog ->
                                result.success(mapOf(
                                    "items" to catalog.items.map { track ->
                                        mapOf(
                                            "id" to track.id,
                                            "title" to track.title,
                                            "artist" to track.artist,
                                            "audioUrl" to track.audioUrl,
                                            "coverUrl" to track.coverUrl,
                                            "genre" to track.genre,
                                            "durationSeconds" to track.durationSeconds
                                        )
                                    },
                                    "error" to catalog.error
                                ))
                            }
                            .onFailure { result.error("CATALOGUE_ERROR", it.message, null) }
                    }
                    "getArtists" -> lifecycleScope.launch {
                        hteApplication.featureRepository.publishedArtists()
                            .onSuccess { artists ->
                                result.success(artists.map { artist ->
                                    mapOf("id" to artist.id, "name" to artist.name, "slug" to artist.slug,
                                        "bio" to artist.bio, "imageUrl" to artist.imageUrl)
                                })
                            }
                            .onFailure { result.error("ARTISTS_ERROR", it.message, null) }
                    }
                    "getVideos" -> lifecycleScope.launch {
                        hteApplication.featureRepository.publishedVideos()
                            .onSuccess { videos ->
                                result.success(videos.map { video ->
                                    mapOf("id" to video.id, "title" to video.title,
                                        "description" to video.description, "videoUrl" to video.videoUrl,
                                        "thumbnailUrl" to video.thumbnailUrl, "artist" to video.artist)
                                })
                            }
                            .onFailure { result.error("VIDEOS_ERROR", it.message, null) }
                    }
                    "getFavourites" -> lifecycleScope.launch {
                        hteApplication.userLibraryRepository.favouriteTrackIds()
                            .onSuccess { result.success(it) }
                            .onFailure { result.error("FAVOURITES_ERROR", it.message, null) }
                    }
                    "setFavourite" -> lifecycleScope.launch {
                        val trackId = call.argument<String>("trackId").orEmpty()
                        val saved = call.argument<Boolean>("saved") ?: false
                        val operation = if (saved) {
                            hteApplication.userLibraryRepository.addFavourite(trackId)
                        } else {
                            hteApplication.userLibraryRepository.removeFavourite(trackId)
                        }
                        result.success(mapOf("success" to operation.successful, "message" to operation.message))
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        playbackFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { controller ->
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val mediaId = mediaItem?.mediaId ?: return
                        if (mediaId.startsWith("hte-ad:")) {
                            val adId = mediaId.removePrefix("hte-ad:")
                            lifecycleScope.launch {
                                hteApplication.sponsoredAudioRepository.recordImpression(adId)
                            }
                        } else {
                            lifecycleScope.launch {
                                hteApplication.userLibraryRepository.recordListening(mediaId)
                            }
                        }
                    }
                })
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun playTrack(trackId: String, result: MethodChannel.Result) {
        lifecycleScope.launch {
            val controller = mediaController
            if (controller == null) {
                result.success(mapOf("success" to false, "message" to "The music player is still connecting."))
                return@launch
            }
            val tracksResult = hteApplication.catalogRepository.publishedTracks()
            val playableTracks = tracksResult.items.filter { !it.audioUrl.isNullOrBlank() }
            val startIndex = playableTracks.indexOfFirst { it.id == trackId }
            if (startIndex < 0) {
                result.success(mapOf("success" to false, "message" to "This track has no published audio file."))
                return@launch
            }
            val sponsoredAd = hteApplication.sponsoredAudioRepository.nextActiveAd().getOrNull()
            val queue = buildList {
                sponsoredAd?.let { ad ->
                    add(MediaItem.Builder().setMediaId("hte-ad:${ad.id}").setUri(ad.audioUrl).build())
                }
                addAll(playableTracks.drop(startIndex).map { track ->
                    val uri = offlineDownloads.localUri(track.id)?.toString() ?: track.audioUrl!!
                    MediaItem.Builder().setMediaId(track.id).setUri(uri).build()
                })
            }
            controller.setMediaItems(queue, 0, C.TIME_UNSET)
            controller.prepare()
            controller.play()
            result.success(mapOf("success" to true, "playback" to playbackState()))
        }
    }

    private fun playbackState(): Map<String, Any?> {
        val player = mediaController
        return mapOf(
            "ready" to (player != null),
            "playing" to (player?.isPlaying == true),
            "mediaId" to player?.currentMediaItem?.mediaId,
            "positionMs" to (player?.currentPosition ?: 0L),
            "durationMs" to (player?.duration?.takeIf { it > 0L } ?: 0L)
        )
    }

    private fun authenticate(
        email: String?,
        password: String?,
        result: MethodChannel.Result,
        createAccount: Boolean
    ) {
        lifecycleScope.launch {
            val authResult = if (createAccount) {
                hteApplication.authRepository.signUp(email.orEmpty(), password.orEmpty())
            } else {
                hteApplication.authRepository.signInWithPassword(email.orEmpty(), password.orEmpty())
            }
            when (authResult) {
                is AuthResult.Success -> {
                    hteApplication.sessionManager.accept(authResult.session)
                    result.success(mapOf("success" to true, "session" to sessionMap()))
                }
                is AuthResult.Failure -> result.success(
                    mapOf("success" to false, "message" to authResult.message)
                )
                is AuthResult.Notice -> result.success(
                    mapOf("success" to true, "message" to authResult.message, "session" to sessionMap())
                )
            }
        }
    }

    private fun sessionMap(): Map<String, Any?> {
        val session = hteApplication.sessionManager.session.value
        return mapOf(
            "authenticated" to (session != null),
            "userId" to session?.userId,
            "email" to session?.email,
            "status" to hteApplication.sessionManager.status.value
        )
    }

    override fun onStop() {
        StartupJingle.stopForSplash()
        super.onStop()
    }

    override fun onDestroy() {
        playbackFuture?.let { MediaController.releaseFuture(it) }
        playbackFuture = null
        mediaController = null
        super.onDestroy()
    }
}
