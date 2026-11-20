package com.hightechentertainment.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlPolicyTest {
    @Test fun spotifyPagesAreExternalNotPlayableAudio() {
        assertTrue(isExternalStreamingSource("direct", "https://open.spotify.com/track/1234567890123456789012"))
        assertFalse(isPlayableInAppAudioUrl("https://open.spotify.com/track/1234567890123456789012", "direct"))
    }

    @Test fun spotifyLinksConvertToOfficialAppUris() {
        assertEquals(
            "spotify:track:1234567890123456789012",
            spotifyAppUri("https://open.spotify.com/track/1234567890123456789012?si=abc")
        )
        assertEquals(
            "spotify:playlist:1234567890123456789012",
            spotifyAppUri("spotify:playlist:1234567890123456789012")
        )
        assertNull(spotifyAppUri("https://example.com/not-spotify"))
        assertTrue(isSpotifySource("spotify", "https://open.spotify.com/track/1234567890123456789012"))
    }

    @Test fun youtubeLinksResolveTheirVideoIds() {
        val id = "YVbOceX0pHM"
        assertEquals(id, extractYouTubeVideoId("https://youtu.be/$id?si=share"))
        assertEquals(id, extractYouTubeVideoId("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, extractYouTubeVideoId("https://m.youtube.com/shorts/$id"))
        assertEquals(id, extractYouTubeVideoId("https://www.youtube-nocookie.com/embed/$id"))
        assertNull(extractYouTubeVideoId("https://example.com/$id"))
    }

    @Test fun youtubeAndGenericExternalPlatformsAreNotPlayedInsideExoplayer() {
        assertTrue(isExternalStreamingSource("youtube", "https://youtu.be/YVbOceX0pHM"))
        assertTrue(isExternalStreamingSource("external", "https://music.example.com/song"))
        assertFalse(isPlayableInAppAudioUrl("https://music.example.com/song", "external"))
        assertFalse(isPlayableVideoUrl("https://www.youtube-nocookie.com/embed/YVbOceX0pHM"))
    }

    @Test fun directHttpsAudioAndAppLocalFilesRemainPlayable() {
        assertTrue(isPlayableInAppAudioUrl("https://cdn.example/audio/song.mp3", "direct"))
        assertTrue(isPlayableInAppAudioUrl("file:///data/user/0/com.hightechentertainment.app/files/song.mp3"))
        assertTrue(isPlayableInAppAudioUrl("content://media/external/audio/media/42"))
    }

    @Test fun cleartextHttpIsNotPlayableAsInAppAudioOrVideo() {
        assertFalse(isPlayableInAppAudioUrl("http://cdn.example/audio/song.mp3"))
        assertFalse(isPlayableVideoUrl("http://cdn.example/video.mp4"))
    }

    @Test fun directHttpsVideoAndLocalVideoRemainPlayable() {
        assertTrue(isPlayableVideoUrl("https://project.supabase.co/storage/v1/object/public/hte-video/clip.mp4"))
        assertTrue(isPlayableVideoUrl("content://media/external/video/media/42"))
    }

    @Test fun secureDownloadsRequireHttpsAndAValidHost() {
        assertTrue(isSecureMediaDownloadUrl("https://cdn.example/audio/song.mp3?token=abc"))
        assertFalse(isSecureMediaDownloadUrl("http://cdn.example/audio/song.mp3"))
        assertFalse(isSecureMediaDownloadUrl("https:///audio/song.mp3"))
        assertFalse(isSecureMediaDownloadUrl("file:///storage/emulated/0/song.mp3"))
    }
}
