package com.hightechentertainment.app.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsoredPlaybackPolicyTest {
    @Test
    fun blocksSkipSeekAndQueueReplacementDuringSponsoredAudio() {
        val blocked = listOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS
        )
        blocked.forEach { command ->
            assertTrue(
                "Expected command $command to be blocked during sponsored audio",
                SponsoredPlaybackPolicy.rejectsCommand("hte-ad:campaign-123", command)
            )
        }
    }

    @Test
    fun allowsPlaybackControlsDuringSponsoredAudio() {
        assertFalse(
            SponsoredPlaybackPolicy.rejectsCommand(
                "hte-ad:campaign-123", Player.COMMAND_PLAY_PAUSE
            )
        )
    }

    @Test
    fun doesNotRestrictNormalMusicOrEmptyQueue() {
        assertFalse(
            SponsoredPlaybackPolicy.rejectsCommand(
                "track-123", Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
            )
        )
        assertFalse(
            SponsoredPlaybackPolicy.rejectsCommand(
                null, Player.COMMAND_CHANGE_MEDIA_ITEMS
            )
        )
    }
}
