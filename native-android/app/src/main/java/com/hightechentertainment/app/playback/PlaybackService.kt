package com.hightechentertainment.app.playback

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the single application playback engine.
 *
 * MediaSessionService keeps audio playback alive when the Activity is backgrounded
 * and exposes standard media controls to Android system surfaces and Bluetooth devices.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            // Media3 manages audio focus and transient focus changes.
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
