package com.hightechentertainment.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLinkClassifierTest {
    @Test fun acceptsCanonicalAndShortVideoLinks() {
        val id = "YVbOceX0pHM"
        val expected = "https://www.youtube.com/watch?v=$id"
        listOf(
            "https://youtu.be/$id?si=share",
            "https://www.youtube.com/watch?v=$id",
            "youtube.com/shorts/$id",
            "https://m.youtube.com/embed/$id",
            "https://www.youtube.com/live/$id"
        ).forEach { input ->
            val result = YouTubeLinkClassifier.classify(input)
            assertTrue("Expected a single video for $input", result is YouTubeSubmissionRoute.SingleVideo)
            assertEquals(expected, (result as YouTubeSubmissionRoute.SingleVideo).canonicalWatchUrl)
        }
    }

    @Test fun routesPlaylistsAndChannelsAwayFromSingleVideoSubmission() {
        assertTrue(YouTubeLinkClassifier.classify("https://www.youtube.com/playlist?list=PL123") is YouTubeSubmissionRoute.Playlist)
        assertTrue(YouTubeLinkClassifier.classify("https://www.youtube.com/watch?list=PL123") is YouTubeSubmissionRoute.Playlist)
        assertTrue(YouTubeLinkClassifier.classify("https://www.youtube.com/@artist") is YouTubeSubmissionRoute.Channel)
        assertTrue(YouTubeLinkClassifier.classify("https://www.youtube.com/channel/UC123") is YouTubeSubmissionRoute.Channel)
    }

    @Test fun rejectsInvalidIdsAndUntrustedHosts() {
        assertTrue(YouTubeLinkClassifier.classify("https://youtu.be/not-a-vide") is YouTubeSubmissionRoute.Invalid)
        assertTrue(YouTubeLinkClassifier.classify("https://youtube.com.evil.example/watch?v=YVbOceX0pHM") is YouTubeSubmissionRoute.Invalid)
        assertTrue(YouTubeLinkClassifier.classify("http://youtube.com/watch?v=YVbOceX0pHM") is YouTubeSubmissionRoute.Invalid)
        assertTrue(YouTubeLinkClassifier.classify("not a URL") is YouTubeSubmissionRoute.Invalid)
    }
}
