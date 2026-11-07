package com.hightechentertainment.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.OpenableColumns
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
import io.elevenlabs.ClientTool
import io.elevenlabs.ClientToolResult
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import com.hightechentertainment.app.playback.StartupJingle
import com.hightechentertainment.app.playback.PlaybackService
import com.hightechentertainment.app.playback.OfflineMusicDownloads
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flutter owns presentation. This bridge reuses the existing process-scoped
 * Supabase repositories; it does not create a second backend or auth store.
 */
class FlutterHostActivity : FlutterActivity() {
    companion object {
        private const val REQUEST_MEDIA_PICKER = 4812
        private const val REQUEST_RECORD_AUDIO = 4813
    }

    private val startupChannelName = "com.hightechentertainment.app/startup"
    private val appChannelName = "com.hightechentertainment.app/data"
    private val hteApplication: HteApplication
        get() = application as HteApplication
    private val authRepository
        get() = hteApplication.authRepository
    private val sessionManager
        get() = hteApplication.sessionManager
    private var oauthInProgress = false
    private var pendingMediaPickerResult: MethodChannel.Result? = null
    private var pendingMediaPickerKind: String? = null
    private var appMethodChannel: MethodChannel? = null
    private var pendingVoicePermissionResult: MethodChannel.Result? = null
    private var voiceSession: ConversationSession? = null
    private var voiceMuted = false
    private var playbackFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val offlineDownloads by lazy { OfflineMusicDownloads(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { sessionManager.restore() }
        connectToPlaybackService()
        handleOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun startGoogleOAuth(): Boolean {
        val redirectUri = "com.hightechentertainment.app://login-callback"
        val authorizationUrl = authRepository.googleOAuthAuthorizationUrl(redirectUri) ?: return false
        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)))
            true
        }.getOrDefault(false)
    }

    private fun handleOAuthCallback(callbackIntent: Intent?) {
        val callback = callbackIntent?.data ?: return
        if (callback.scheme != "com.hightechentertainment.app" || callback.host != "login-callback") return
        val providerError = callback.getQueryParameter("error_description")
            ?: callback.getQueryParameter("error")
        if (!providerError.isNullOrBlank()) return
        val code = callback.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return
        if (oauthInProgress) return
        oauthInProgress = true
        lifecycleScope.launch {
            try {
                when (val auth = authRepository.exchangeGoogleOAuthCode(code)) {
                    is AuthResult.Success -> sessionManager.accept(auth.session)
                    is AuthResult.Failure -> android.util.Log.w("HTE-OAuth", auth.message)
                    is AuthResult.Notice -> android.util.Log.i("HTE-OAuth", auth.message)
                }
            } finally {
                oauthInProgress = false
            }
        }
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
                    else -> result.notImplemented()
                }
            }

        val appChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, appChannelName)
        appMethodChannel = appChannel
        appChannel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "pickAndUploadMedia" -> launchMediaPicker(
                        call.argument<String>("kind").orEmpty(), result
                    )
                    "startGoogleOAuth" -> {
                        val started = startGoogleOAuth()
                        result.success(mapOf(
                            "success" to started,
                            "message" to if (started) null else "Google sign-in could not be started. Check the OAuth configuration and try again."
                        ))
                    }
                    "openAccountDeletion" -> {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://high-techmedia.netlify.app/account-deletion.html"
                        )))
                        result.success(true)
                    }
                    "requestVoicePermission" -> requestVoicePermission(result)
                    "startVoiceSession" -> startVoiceSession(result)
                    "endVoiceSession" -> endVoiceSession(result)
                    "setVoiceMuted" -> setVoiceMuted(call.argument<Boolean>("muted") ?: false, result)
                    "getSession" -> result.success(sessionMap())
                    "getPlaybackState" -> result.success(playbackState())
                    "playVideo" -> {
                        val videoUrl = call.argument<String>("videoUrl").orEmpty()
                        if (!videoUrl.startsWith("https://") || android.net.Uri.parse(videoUrl).host.isNullOrBlank()) {
                            result.success(mapOf("success" to false, "message" to "Only secure video URLs are supported."))
                        } else {
                            startActivity(Intent(this, NativeVideoActivity::class.java).apply {
                                putExtra(NativeVideoActivity.EXTRA_VIDEO_ID, call.argument<String>("videoId").orEmpty())
                                putExtra(NativeVideoActivity.EXTRA_VIDEO_URL, videoUrl)
                                putExtra(NativeVideoActivity.EXTRA_VIDEO_TITLE, call.argument<String>("title").orEmpty())
                            })
                            result.success(mapOf("success" to true))
                        }
                    }
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
                    "requestPasswordReset" -> lifecycleScope.launch {
                        when (val auth = authRepository.requestPasswordReset(call.argument<String>("email").orEmpty())) {
                            is AuthResult.Success -> result.success(mapOf("success" to true, "message" to "Password reset requested."))
                            is AuthResult.Notice -> result.success(mapOf("success" to true, "message" to auth.message))
                            is AuthResult.Failure -> result.success(mapOf("success" to false, "message" to auth.message))
                        }
                    }
                    "updatePassword" -> lifecycleScope.launch {
                        hteApplication.featureRepository.updatePassword(
                            call.argument<String>("password").orEmpty()
                        ).onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
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
                    "getProfile" -> lifecycleScope.launch {
                        hteApplication.featureRepository.getProfile()
                            .onSuccess { profile ->
                                result.success(mapOf(
                                    "displayName" to profile.displayName,
                                    "bio" to profile.bio,
                                    "phone" to profile.phone,
                                    "avatarUrl" to profile.avatarUrl
                                ))
                            }
                            .onFailure { result.error("PROFILE_ERROR", it.message, null) }
                    }
                    "saveProfile" -> lifecycleScope.launch {
                        val profile = com.hightechentertainment.app.data.NativeProfile(
                            displayName = call.argument<String>("displayName").orEmpty(),
                            bio = call.argument<String>("bio").orEmpty(),
                            phone = call.argument<String>("phone").orEmpty(),
                            avatarUrl = call.argument<String>("avatarUrl").orEmpty()
                        )
                        hteApplication.featureRepository.saveProfile(profile)
                            .onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "submitBooking" -> lifecycleScope.launch {
                        val operation = hteApplication.userLibraryRepository.submitBooking(
                            call.argument<String>("name").orEmpty(),
                            call.argument<String>("email").orEmpty(),
                            call.argument<String>("bookingType").orEmpty(),
                            call.argument<String>("requestedDate"),
                            call.argument<String>("message").orEmpty()
                        )
                        result.success(mapOf("success" to operation.successful, "message" to operation.message))
                    }
                    "getMyBookings" -> lifecycleScope.launch {
                        hteApplication.featureRepository.myBookings()
                            .onSuccess { bookings ->
                                result.success(bookings.map { booking ->
                                    mapOf("id" to booking.id, "name" to booking.name, "email" to booking.email,
                                        "type" to booking.type, "requestedDate" to booking.requestedDate,
                                        "message" to booking.message, "status" to booking.status)
                                })
                            }
                            .onFailure { result.error("BOOKINGS_ERROR", it.message, null) }
                    }
                    "getFounderData" -> lifecycleScope.launch {
                        val access = hteApplication.featureRepository.hasFounderAccess().getOrDefault(false)
                        if (!access) {
                            result.success(mapOf("authorized" to false))
                        } else {
                            val overview = hteApplication.featureRepository.founderOverview().getOrNull()
                            val drafts = hteApplication.featureRepository.founderDrafts().getOrNull().orEmpty()
                            val bookings = hteApplication.featureRepository.founderBookings().getOrNull().orEmpty()
                            val campaigns = hteApplication.sponsoredAudioRepository.campaigns().getOrNull().orEmpty()
                            result.success(mapOf(
                                "authorized" to true,
                                "overview" to overview?.let {
                                    mapOf("tracks" to it.tracks, "artists" to it.artists, "releases" to it.releases,
                                        "videos" to it.videos, "bookings" to it.bookings)
                                },
                                "drafts" to drafts.map { mapOf("table" to it.table, "id" to it.id, "title" to it.title) },
                                "bookings" to bookings.map { mapOf("id" to it.id, "name" to it.name, "email" to it.email,
                                    "type" to it.type, "requestedDate" to it.requestedDate,
                                    "message" to it.message, "status" to it.status) },
                                "campaigns" to campaigns.map { mapOf("id" to it.id, "title" to it.title,
                                    "audioUrl" to it.audioUrl, "isPublished" to it.isPublished,
                                    "startsAt" to it.startsAt, "endsAt" to it.endsAt, "priority" to it.priority) }
                            ))
                        }
                    }
                    "updateBookingStatus" -> lifecycleScope.launch {
                        hteApplication.featureRepository.updateBookingStatus(
                            call.argument<String>("id").orEmpty(),
                            call.argument<String>("status").orEmpty()
                        ).onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "setDraftPublished" -> lifecycleScope.launch {
                        val table = call.argument<String>("table").orEmpty()
                        if (table !in setOf("tracks", "artists", "releases", "videos")) {
                            result.success(mapOf("success" to false, "message" to "Unsupported content type."))
                        } else {
                            hteApplication.featureRepository.setDraftPublished(
                                table, call.argument<String>("id").orEmpty(),
                                call.argument<Boolean>("published") ?: false
                            ).onSuccess { result.success(mapOf("success" to true)) }
                                .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                        }
                    }
                    "uploadAudio" -> lifecycleScope.launch {
                        val authorized = hteApplication.featureRepository.hasFounderAccess().getOrDefault(false)
                        if (!authorized) {
                            result.success(mapOf("success" to false, "message" to "Founder Studio access is required."))
                        } else {
                            val bytes = call.argument<ByteArray>("bytes") ?: ByteArray(0)
                            hteApplication.featureRepository.uploadAudio(
                                bytes,
                                call.argument<String>("contentType").orEmpty(),
                                call.argument<String>("extension").orEmpty()
                            ).onSuccess { url -> result.success(mapOf("success" to true, "url" to url)) }
                                .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                        }
                    }
                    "uploadVideo" -> lifecycleScope.launch {
                        val authorized = hteApplication.featureRepository.hasFounderAccess().getOrDefault(false)
                        if (!authorized) {
                            result.success(mapOf("success" to false, "message" to "Founder Studio access is required."))
                        } else {
                            val uri = android.net.Uri.parse(call.argument<String>("uri").orEmpty())
                            hteApplication.featureRepository.uploadVideo(
                                applicationContext,
                                uri,
                                call.argument<String>("contentType").orEmpty(),
                                call.argument<String>("extension").orEmpty()
                            ).onSuccess { url -> result.success(mapOf("success" to true, "url" to url)) }
                                .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                        }
                    }
                    "uploadAvatar" -> lifecycleScope.launch {
                        val bytes = call.argument<ByteArray>("bytes") ?: ByteArray(0)
                        hteApplication.featureRepository.uploadAvatar(
                            bytes,
                            call.argument<String>("contentType").orEmpty()
                        ).onSuccess { url -> result.success(mapOf("success" to true, "url" to url)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "createTrack" -> lifecycleScope.launch {
                        hteApplication.featureRepository.createTrack(
                            call.argument<String>("title").orEmpty(),
                            call.argument<String>("artist").orEmpty(),
                            call.argument<String>("audioUrl").orEmpty(),
                            call.argument<String>("genre").orEmpty()
                        ).onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "createArtist" -> lifecycleScope.launch {
                        hteApplication.featureRepository.createArtist(
                            call.argument<String>("name").orEmpty(),
                            call.argument<String>("bio").orEmpty()
                        ).onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "createRelease" -> lifecycleScope.launch {
                        hteApplication.featureRepository.createRelease(
                            call.argument<String>("title").orEmpty(),
                            call.argument<String>("description").orEmpty()
                        ).onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "createVideo" -> lifecycleScope.launch {
                        hteApplication.featureRepository.createVideo(
                            call.argument<String>("title").orEmpty(),
                            call.argument<String>("videoUrl").orEmpty(),
                            call.argument<String>("description").orEmpty(),
                            call.argument<String>("artist").orEmpty()
                        ).onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "publishVideo" -> lifecycleScope.launch {
                        hteApplication.featureRepository.publishVideo(
                            call.argument<String>("id").orEmpty(),
                            call.argument<Boolean>("published") ?: false
                        ).onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "createCampaign" -> lifecycleScope.launch {
                        hteApplication.sponsoredAudioRepository.createCampaign(
                            call.argument<String>("title").orEmpty(),
                            call.argument<String>("audioUrl").orEmpty(),
                            call.argument<String>("startsAt"),
                            call.argument<String>("endsAt"),
                            call.argument<Int>("priority") ?: 0
                        ).onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "setCampaignPublished" -> lifecycleScope.launch {
                        hteApplication.sponsoredAudioRepository.setCampaignPublished(
                            call.argument<String>("id").orEmpty(),
                            call.argument<Boolean>("published") ?: false
                        ).onSuccess { result.success(mapOf("success" to true)) }
                            .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
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
                    "getPlaylists" -> lifecycleScope.launch {
                        hteApplication.userLibraryRepository.playlists()
                            .onSuccess { rows ->
                                result.success(rows.map { mapOf("id" to it.id, "name" to it.name, "description" to it.description) })
                            }
                            .onFailure { result.error("PLAYLISTS_ERROR", it.message, null) }
                    }
                    "createPlaylist" -> lifecycleScope.launch {
                        hteApplication.userLibraryRepository.createPlaylist(
                            call.argument<String>("name").orEmpty(),
                            call.argument<String>("description").orEmpty()
                        ).onSuccess { playlist ->
                            result.success(mapOf("success" to true, "playlist" to mapOf(
                                "id" to playlist.id, "name" to playlist.name, "description" to playlist.description
                            )))
                        }.onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
                    }
                    "getPlaylistTracks" -> lifecycleScope.launch {
                        hteApplication.userLibraryRepository.playlistTrackIds(
                            call.argument<String>("playlistId").orEmpty()
                        ).onSuccess { result.success(it) }
                            .onFailure { result.error("PLAYLIST_TRACKS_ERROR", it.message, null) }
                    }
                    "addTrackToPlaylist" -> lifecycleScope.launch {
                        val operation = hteApplication.userLibraryRepository.addTrackToPlaylist(
                            call.argument<String>("playlistId").orEmpty(),
                            call.argument<String>("trackId").orEmpty()
                        )
                        result.success(mapOf("success" to operation.successful, "message" to operation.message))
                    }
                    "removeTrackFromPlaylist" -> lifecycleScope.launch {
                        val operation = hteApplication.userLibraryRepository.removeTrackFromPlaylist(
                            call.argument<String>("playlistId").orEmpty(),
                            call.argument<String>("trackId").orEmpty()
                        )
                        result.success(mapOf("success" to operation.successful, "message" to operation.message))
                    }
                    "deletePlaylist" -> lifecycleScope.launch {
                        val operation = hteApplication.userLibraryRepository.removePlaylist(
                            call.argument<String>("playlistId").orEmpty()
                        )
                        result.success(mapOf("success" to operation.successful, "message" to operation.message))
                    }
                    else -> result.notImplemented()
                }
            }
    }

    @Deprecated("Android document picker compatibility callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PICKER) {
            completeMediaPicker(resultCode, data?.data)
        }
    }

    private fun launchMediaPicker(kind: String, result: MethodChannel.Result) {
        if (kind !in setOf("audio", "video", "image")) {
            result.success(mapOf("success" to false, "message" to "Unsupported media type."))
            return
        }
        if (pendingMediaPickerResult != null) {
            result.success(mapOf("success" to false, "message" to "A media picker is already open."))
            return
        }
        pendingMediaPickerKind = kind
        pendingMediaPickerResult = result
        val mimeType = when (kind) {
            "audio" -> "audio/*"
            "video" -> "video/*"
            else -> "image/*"
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivityForResult(intent, REQUEST_MEDIA_PICKER) }.onFailure { error ->
            pendingMediaPickerKind = null
            pendingMediaPickerResult = null
            result.success(mapOf("success" to false, "message" to (error.message ?: "Could not open media picker.")))
        }
    }

    private fun completeMediaPicker(resultCode: Int, uri: Uri?) {
        val result = pendingMediaPickerResult ?: return
        val kind = pendingMediaPickerKind
        pendingMediaPickerResult = null
        pendingMediaPickerKind = null
        if (resultCode != RESULT_OK || uri == null) {
            result.success(mapOf("success" to false, "cancelled" to true))
            return
        }
        lifecycleScope.launch {
            try {
                val mimeType = contentResolver.getType(uri).orEmpty()
                val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }.orEmpty()
                val extension = displayName.substringAfterLast('.', "").lowercase().ifBlank {
                    when (mimeType) {
                        "audio/mpeg" -> "mp3"
                        "audio/mp4" -> "m4a"
                        "audio/aac" -> "aac"
                        "audio/wav", "audio/x-wav" -> "wav"
                        "audio/ogg" -> "ogg"
                        "video/mp4" -> "mp4"
                        "video/webm" -> "webm"
                        "video/quicktime" -> "mov"
                        "video/x-m4v" -> "m4v"
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/jpeg" -> "jpg"
                        else -> ""
                    }
                }
                val upload = when (kind) {
                    "audio" -> {
                        if (!hteApplication.featureRepository.hasFounderAccess().getOrDefault(false)) {
                            result.success(mapOf("success" to false, "message" to "Founder Studio access is required."))
                            return@launch
                        }
                        hteApplication.featureRepository.uploadAudio(applicationContext, uri, mimeType, extension)
                    }
                    "video" -> {
                        if (!hteApplication.featureRepository.hasFounderAccess().getOrDefault(false)) {
                            result.success(mapOf("success" to false, "message" to "Founder Studio access is required."))
                            return@launch
                        }
                        hteApplication.featureRepository.uploadVideo(applicationContext, uri, mimeType, extension)
                    }
                    "image" -> hteApplication.featureRepository.uploadAvatar(applicationContext, uri, mimeType, extension)
                    else -> {
                        result.success(mapOf("success" to false, "message" to "Unsupported media type."))
                        return@launch
                    }
                }
                upload.onSuccess { url ->
                    result.success(mapOf("success" to true, "url" to url))
                }.onFailure { error ->
                    result.success(mapOf("success" to false, "message" to (error.message ?: "Upload failed.")))
                }
            } catch (error: Exception) {
                result.success(mapOf("success" to false, "message" to (error.message ?: "Upload failed.")))
            }
        }
    }

    private fun requestVoicePermission(result: MethodChannel.Result) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            result.success(mapOf("granted" to true))
            return
        }
        if (pendingVoicePermissionResult != null) {
            result.success(mapOf("granted" to false, "message" to "A microphone permission request is already active."))
            return
        }
        pendingVoicePermissionResult = result
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
    }

    @Deprecated("Android microphone permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            pendingVoicePermissionResult?.success(mapOf("granted" to granted))
            pendingVoicePermissionResult = null
        }
    }

    private fun startVoiceSession(result: MethodChannel.Result) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            result.success(mapOf("success" to false, "permissionRequired" to true))
            return
        }
        if (voiceSession != null) {
            result.success(mapOf("success" to true, "alreadyConnected" to true))
            return
        }
        lifecycleScope.launch {
            try {
                sendVoiceEvent("status", "Securing your private voice session…")
                val activeSession = sessionManager.session.value
                    ?: throw IllegalStateException("Sign in to use the Voice Companion.")
                val token = hteApplication.voiceSessionTokenRepository.fetchToken()
                val catalogue = hteApplication.catalogRepository.publishedTracks().items
                val favourites = hteApplication.userLibraryRepository.favouriteTrackIds().getOrDefault(emptyList()).toSet()
                val founderAccess = hteApplication.featureRepository.hasFounderAccess().getOrDefault(false)
                val tools = mapOf(
                    "control_playback" to object : ClientTool {
                        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                            val action = parameters["action"] as? String
                                ?: return ClientToolResult.failure("A playback action was not supplied.")
                            if (action == "play_favourites" && favourites.isEmpty()) {
                                return ClientToolResult.failure("There are no saved favourites to play yet.")
                            }
                            withContext(Dispatchers.Main.immediate) {
                                when (action) {
                                    "pause" -> mediaController?.pause()
                                    "resume" -> mediaController?.play()
                                    "next" -> mediaController?.seekToNextMediaItem()
                                    "previous" -> mediaController?.seekToPreviousMediaItem()
                                    "stop" -> mediaController?.stop()
                                    "play_favourites" -> lifecycleScope.launch {
                                        val selected = catalogue.filter { it.id in favourites && !it.audioUrl.isNullOrBlank() }
                                        val ad = hteApplication.sponsoredAudioRepository.nextActiveAd().getOrNull()
                                        val queue = buildList {
                                            ad?.let { add(MediaItem.Builder().setMediaId("hte-ad:${it.id}").setUri(it.audioUrl).build()) }
                                            selected.forEach { track ->
                                                add(MediaItem.Builder().setMediaId(track.id).setUri(
                                                    offlineDownloads.localUri(track.id)?.toString() ?: track.audioUrl!!
                                                ).build())
                                            }
                                        }
                                        mediaController?.setMediaItems(queue)
                                        mediaController?.prepare()
                                        mediaController?.play()
                                    }
                                    else -> return@withContext
                                }
                            }
                            return ClientToolResult.success("The app received the $action playback command.")
                        }
                    },
                    "navigate_app" to object : ClientTool {
                        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                            val destination = parameters["destination"] as? String
                                ?: return ClientToolResult.failure("A destination was not supplied.")
                            if (destination.equals("Founder Studio", true) && !founderAccess) {
                                return ClientToolResult.failure("This account does not have Founder Studio access.")
                            }
                            withContext(Dispatchers.Main.immediate) {
                                appMethodChannel?.invokeMethod("voiceNavigate", mapOf("destination" to destination))
                            }
                            return ClientToolResult.success("Requested navigation to $destination.")
                        }
                    },
                    "search_catalogue" to object : ClientTool {
                        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                            val query = parameters["query"] as? String
                                ?: return ClientToolResult.failure("A search term was not supplied.")
                            val matches = catalogue.filter {
                                it.title.contains(query, true) || it.artist.contains(query, true) ||
                                    (it.genre?.contains(query, true) == true)
                            }.take(5)
                            if (matches.isEmpty()) return ClientToolResult.failure("No published tracks matched that search.")
                            withContext(Dispatchers.Main.immediate) {
                                appMethodChannel?.invokeMethod("voiceSearch", mapOf("query" to query))
                            }
                            val summary = matches.joinToString("; ") { "${it.title} by ${it.artist}" }
                            return ClientToolResult.success("Found these published tracks: $summary")
                        }
                    }
                )
                val config = ConversationConfig(
                    conversationToken = token,
                    userId = activeSession.userId,
                    onStatusChange = { status ->
                        runOnUiThread {
                            sendVoiceEvent("status", status.toString())
                            if (status.toString().contains("DISCONNECTED", true) ||
                                status.toString().contains("ERROR", true)
                            ) {
                                mediaController?.volume = 1f
                            }
                        }
                    },
                    onModeChange = { mode ->
                        runOnUiThread {
                            val speaking = mode.toString().contains("SPEAKING", true)
                            mediaController?.volume = if (speaking) 0.25f else 1f
                            sendVoiceEvent("mode", if (speaking) "Speaking" else "Listening")
                        }
                    },
                    onUserTranscriptEvent = { text, _ -> runOnUiThread { sendVoiceEvent("userTranscript", text) } },
                    onAgentResponseEvent = { text, _ -> runOnUiThread { sendVoiceEvent("agentTranscript", text) } },
                    clientTools = tools
                )
                voiceSession = ConversationClient.startSession(config, this@FlutterHostActivity)
                sendVoiceEvent("status", "Connecting to High-Tech Voice Companion…")
                result.success(mapOf("success" to true))
            } catch (error: Exception) {
                mediaController?.volume = 1f
                sendVoiceEvent("status", error.message ?: "Could not start the online voice session.")
                result.success(mapOf("success" to false, "message" to (error.message ?: "Voice session failed.")))
            }
        }
    }

    private fun endVoiceSession(result: MethodChannel.Result) {
        val active = voiceSession
        voiceSession = null
        voiceMuted = false
        mediaController?.volume = 1f
        lifecycleScope.launch {
            runCatching { active?.endSession() }
            sendVoiceEvent("status", "Disconnected")
            result.success(mapOf("success" to true))
        }
    }

    private fun setVoiceMuted(muted: Boolean, result: MethodChannel.Result) {
        val active = voiceSession
        if (active == null) {
            result.success(mapOf("success" to false, "message" to "Start a voice session first."))
            return
        }
        lifecycleScope.launch {
            runCatching { active.setMicMuted(muted) }
                .onSuccess {
                    voiceMuted = muted
                    result.success(mapOf("success" to true, "muted" to muted))
                }
                .onFailure { result.success(mapOf("success" to false, "message" to it.message)) }
        }
    }

    private fun sendVoiceEvent(event: String, value: String) {
        appMethodChannel?.invokeMethod("voiceEvent", mapOf("event" to event, "value" to value))
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
        voiceSession?.let { active ->
            lifecycleScope.launch { runCatching { active.endSession() } }
        }
        voiceSession = null
        mediaController?.volume = 1f
        playbackFuture?.let { MediaController.releaseFuture(it) }
        playbackFuture = null
        mediaController = null
        super.onDestroy()
    }
}
