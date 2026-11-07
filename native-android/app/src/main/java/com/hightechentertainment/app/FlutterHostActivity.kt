package com.hightechentertainment.app

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.hightechentertainment.app.data.AuthResult
import com.hightechentertainment.app.data.HteApplication
import com.hightechentertainment.app.playback.StartupJingle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { hteApplication.sessionManager.restore() }
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
}
