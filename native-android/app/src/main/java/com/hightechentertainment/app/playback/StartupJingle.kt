package com.hightechentertainment.app.playback

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.hightechentertainment.app.R

/**
 * Branded 15-second startup cue. It is deliberately separate from Media3 so it
 * never appears in the listener queue, media session, or sponsored-audio flow.
 */
object StartupJingle {
    private const val SPLASH_DURATION_MS = 15_000L
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val stopTask = Runnable { stopForSplash() }

    @Synchronized
    fun playForSplash(context: Context): Boolean {
        stopForSplash()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Do not interrupt music the listener was already playing in another app.
        if (audioManager.isMusicActive) return false

        val next = runCatching {
            MediaPlayer.create(context.applicationContext, R.raw.high_tech_startup)
        }.getOrNull() ?: return false

        return runCatching {
            next.setVolume(0.62f, 0.62f)
            // If the bundled cue is a little shorter than the visual sequence,
            // loop it so sound and animation still share the same 15s boundary.
            next.isLooping = next.duration in 1 until SPLASH_DURATION_MS.toInt()
            next.setOnErrorListener { mediaPlayer, _, _ ->
                synchronized(this) {
                    if (player === mediaPlayer) player = null
                }
                mediaPlayer.release()
                true
            }
            player = next
            next.start()
            handler.postDelayed(stopTask, SPLASH_DURATION_MS)
            true
        }.getOrElse {
            runCatching { next.release() }
            player = null
            false
        }
    }

    @Synchronized
    fun stopForSplash() {
        handler.removeCallbacks(stopTask)
        val active = player
        player = null
        if (active != null) {
            runCatching {
                if (active.isPlaying) active.stop()
            }
            runCatching { active.release() }
        }
    }

    /** Kept for the legacy Compose host while the Flutter shell is introduced. */
    fun playOnce(context: Context) {
        playForSplash(context)
    }
}
