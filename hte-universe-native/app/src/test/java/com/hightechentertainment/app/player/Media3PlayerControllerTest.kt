package com.hightechentertainment.app.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3PlayerControllerTest {
    @Test fun endedPlaybackMustRestartFromBeginning() { assertTrue(shouldRestartEndedPlayback(Player.STATE_ENDED)) }
    @Test fun activeOrIdlePlaybackMustNotBeTreatedAsEnded() {
        assertFalse(shouldRestartEndedPlayback(Player.STATE_READY))
        assertFalse(shouldRestartEndedPlayback(Player.STATE_IDLE))
        assertFalse(shouldRestartEndedPlayback(Player.STATE_BUFFERING))
    }
}
