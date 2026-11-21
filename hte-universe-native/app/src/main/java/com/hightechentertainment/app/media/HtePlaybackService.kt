package com.hightechentertainment.app.media

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hightechentertainment.app.AppSettingsStore

/** Owns the audio player outside the Activity so playback can continue in the background. */
@OptIn(markerClass = [UnstableApi::class])
class HtePlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            // Keep the CPU awake during foreground music playback when the screen is off.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
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

    // Media3 grants playback commands to trusted controllers (System UI, Bluetooth and Auto)
    // while untrusted controllers receive read-only access. Keep this default policy so
    // external media keys continue working without granting arbitrary playback commands.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        HteAudioEffects.release()
        activeAudioSessionId = 0
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
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
