package com.hightechentertainment.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrackDownloadRoutingTest {
    @Test fun routesAudioTrackToTemporarySupabaseDeliveryPage() {
        assertEquals(
            "https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/download/?track=track-123",
            temporaryAudioDownloadPage("track-123")
        )
    }

    @Test fun safelyEncodesTrackIdentifiers() {
        assertEquals(
            "https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/download/?track=id%2Fwith%20spaces%3F",
            temporaryAudioDownloadPage("id/with spaces?")
        )
    }

    @Test fun rejectsBlankTrackIdentifiers() {
        assertThrows(IllegalArgumentException::class.java) {
            temporaryAudioDownloadPage("  ")
        }
    }
}
