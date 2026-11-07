package com.hightechentertainment.app.playback

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import com.hightechentertainment.app.R

/**
 * One-shot branded startup sound. It is a separate MediaPlayer instance and
 * never enters the listener's Media3 music queue or media notification.
 * It does not request audio focus and will not start over active device audio.
 */
object StartupJingle {
    fun playOnce(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.isMusicActive) return

        val player = runCatching {
            MediaPlayer.create(context.applicationContext, R.raw.high_tech_startup)
        }.getOrNull() ?: return

        player.setVolume(0.62f, 0.62f)
        player.setOnCompletionListener { it.release() }
        player.setOnErrorListener { mediaPlayer, _, _ ->
            mediaPlayer.release()
            true
        }
        runCatching { player.start() }.onFailure { player.release() }
    }
}
