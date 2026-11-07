package com.hightechentertainment.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

/** Full-screen video surface backed by the app's one shared Media3 session. */
class NativeVideoActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
            ?.takeIf { it.startsWith("https://") && android.net.Uri.parse(it).host != null }
        if (videoUrl == null) {
            finish()
            return
        }

        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setShowNextButton(false)
            setShowPreviousButton(false)
        }
        setContentView(playerView)

        val token = SessionToken(this, ComponentName(this, com.hightechentertainment.app.playback.PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                playerView.player = mediaController
                val mediaId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
                mediaController.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId("hte-video:" + mediaId)
                        .setUri(videoUrl)
                        .build()
                )
                mediaController.prepare()
                mediaController.play()
            }.onFailure {
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controller?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        playerView.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO_ID = "hte.video.id"
        const val EXTRA_VIDEO_URL = "hte.video.url"
        const val EXTRA_VIDEO_TITLE = "hte.video.title"
    }
}
