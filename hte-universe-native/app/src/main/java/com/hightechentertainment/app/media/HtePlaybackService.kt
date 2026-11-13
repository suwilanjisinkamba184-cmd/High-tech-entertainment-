package com.hightechentertainment.app.media

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hightechentertainment.app.AppSettingsStore

/** Owns the audio player outside the Activity so playback can continue in the background. */
@OptIn(UnstableApi::class)
class HtePlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioPlayer = ExoPlayer.Builder(this).build()
        activeAudioSessionId = audioPlayer.audioSessionId
        applySavedEqualizer(activeAudioSessionId)
        audioPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                activeAudioSessionId = audioSessionId
                applySavedEqualizer(audioSessionId)
            }
        })
        player = audioPlayer
        mediaSession = MediaSession.Builder(this, audioPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        HteAudioEffects.release()
        activeAudioSessionId = 0
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun applySavedEqualizer(sessionId: Int) {
        val settings = AppSettingsStore(this)
        if (settings.equalizerEnabled) HteAudioEffects.apply(sessionId, true, settings.equalizerBands())
    }

    companion object {
        @Volatile var activeAudioSessionId: Int = 0
            private set
    }
}
