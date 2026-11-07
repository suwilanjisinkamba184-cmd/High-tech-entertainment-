package com.hightechentertainment.app.playback

import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionResult

/**
 * Owns the single application playback engine.
 *
 * Sponsored audio is a server-scheduled pre-roll. Skip, seek and playlist
 * replacement commands are rejected by the session itself while an ad is
 * current, including commands originating from Android system media controls.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        }
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                @Suppress("DEPRECATION")
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    return if (SponsoredPlaybackPolicy.rejectsCommand(
                            session.player.currentMediaItem?.mediaId,
                            playerCommand
                        )
                    ) {
                        SessionResult.RESULT_ERROR_PERMISSION_DENIED
                    } else {
                        SessionResult.RESULT_SUCCESS
                    }
                }
            })
            .build()
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

/** Shared, testable policy used by the MediaSession command boundary. */
internal object SponsoredPlaybackPolicy {
    private const val AD_MEDIA_ID_PREFIX = "hte-ad:"

    private val blockedCommands = setOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_CHANGE_MEDIA_ITEMS
    )

    fun rejectsCommand(mediaId: String?, playerCommand: Int): Boolean =
        mediaId?.startsWith(AD_MEDIA_ID_PREFIX) == true && playerCommand in blockedCommands
}
