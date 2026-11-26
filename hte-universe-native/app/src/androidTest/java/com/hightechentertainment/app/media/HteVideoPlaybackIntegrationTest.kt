package com.hightechentertainment.app.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.hightechentertainment.app.MainActivity
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(UnstableApi::class)
class HteVideoPlaybackIntegrationTest {
    @Test
    fun exoplayerRendersFirstFrameFromLocalMp4() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val testVideo = File(targetContext.cacheDir, "hte-video-render-test.mp4")
        instrumentation.context.assets.open("hte_test_video.mp4").use { input ->
            testVideo.outputStream().use { output -> input.copyTo(output) }
        }

        val player = ExoPlayer.Builder(targetContext).build()
        val playerView = AtomicReference<PlayerView?>(null)
        val firstFrame = CountDownLatch(1)
        val playbackError = AtomicReference<PlaybackException?>(null)
        val hasVideoSize = AtomicBoolean(false)
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame.countDown()
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) hasVideoSize.set(true)
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError.set(error)
                firstFrame.countDown()
            }
        })

        try {
            scenario.onActivity { activity ->
                val view = PlayerView(activity)
                view.player = player
                playerView.set(view)
                activity.setContentView(view)
            }
            instrumentation.runOnMainSync {
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(testVideo)))
                player.prepare()
                player.play()
            }

            assertTrue("Media3 did not render the test video's first frame.", firstFrame.await(10, TimeUnit.SECONDS))
            assertNull("Media3 reported a video decoder/playback error.", playbackError.get())
            assertTrue("Media3 did not report a valid video size.", hasVideoSize.get())
        } finally {
            instrumentation.runOnMainSync {
                playerView.get()?.player = null
                player.release()
            }
            scenario.close()
            testVideo.delete()
        }
    }
}
