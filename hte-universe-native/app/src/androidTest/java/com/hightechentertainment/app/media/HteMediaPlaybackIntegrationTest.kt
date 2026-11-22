package com.hightechentertainment.app.media

import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.hightechentertainment.app.AppSettingsStore
import com.hightechentertainment.app.MainActivity
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin

@OptIn(UnstableApi::class)
class HteMediaPlaybackIntegrationTest {
    @Test
    fun mediaSessionPlaysLocalAudioAndContinuesWhenActivityStops() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val testAudio = File(targetContext.cacheDir, "hte-media-service-test.wav")
        val connection = MediaController.Builder(
            targetContext,
            SessionToken(targetContext, ComponentName(targetContext, HtePlaybackService::class.java))
        ).buildAsync()
        var controller: MediaController? = null

        try {
            writeTestWav(testAudio, durationSeconds = 4)
            controller = connection.get(10, TimeUnit.SECONDS)

            val started = CountDownLatch(1)
            val ended = CountDownLatch(1)
            val playbackError = AtomicReference<PlaybackException?>(null)
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) started.countDown()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) ended.countDown()
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError.set(error)
                    started.countDown()
                    ended.countDown()
                }
            }

            instrumentation.runOnMainSync {
                controller.addListener(listener)
                controller.setMediaItem(MediaItem.fromUri(Uri.fromFile(testAudio)))
                controller.prepare()
                controller.play()
            }

            assertTrue("Media3 service never entered playing state.", started.await(10, TimeUnit.SECONDS))
            assertNull("Media3 reported a playback error.", playbackError.get())

            // MainActivity and UniverseShell both honor the default-enabled
            // backgroundPlayback preference. Moving the activity to CREATED
            // simulates leaving the foreground without terminating the service.
            assertTrue("Background playback preference should default to enabled.", AppSettingsStore(targetContext).backgroundPlayback)
            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(900)

            val stillPlaying = AtomicBoolean(false)
            instrumentation.runOnMainSync { stillPlaying.set(controller.isPlaying) }
            assertTrue("Audio playback stopped when the activity entered the background.", stillPlaying.get())

            assertTrue("Local test audio did not reach STATE_ENDED.", ended.await(10, TimeUnit.SECONDS))
            assertNull("Media3 failed while playing the test audio.", playbackError.get())
        } finally {
            instrumentation.runOnMainSync {
                controller?.let {
                    runCatching { it.stop() }
                    runCatching { it.release() }
                }
            }
            runCatching { MediaController.releaseFuture(connection) }
            scenario.close()
            testAudio.delete()
        }
    }

    private fun writeTestWav(file: File, durationSeconds: Int) {
        val sampleRate = 16_000
        val sampleCount = sampleRate * durationSeconds
        val dataBytes = sampleCount * 2
        DataOutputStream(FileOutputStream(file)).use { output ->
            output.writeBytes("RIFF")
            output.writeLittleEndianInt(36 + dataBytes)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(1) // PCM
            output.writeLittleEndianShort(1) // mono
            output.writeLittleEndianInt(sampleRate)
            output.writeLittleEndianInt(sampleRate * 2)
            output.writeLittleEndianShort(2) // block alignment
            output.writeLittleEndianShort(16)
            output.writeBytes("data")
            output.writeLittleEndianInt(dataBytes)
            repeat(sampleCount) { index ->
                val sample = (sin(2.0 * PI * 440.0 * index / sampleRate) * 4_000).toInt()
                output.writeLittleEndianShort(sample)
            }
        }
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value and 0xff)
        writeByte((value ushr 8) and 0xff)
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        writeLittleEndianShort(value and 0xffff)
        writeLittleEndianShort((value ushr 16) and 0xffff)
    }
}
