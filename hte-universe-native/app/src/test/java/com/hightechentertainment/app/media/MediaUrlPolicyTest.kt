package com.hightechentertainment.app.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlPolicyTest {
    @Test fun spotifyPagesAreExternalNotPlayableAudio() {
        assertTrue(isExternalStreamingSource("direct", "https://open.spotify.com/track/example"))
        assertFalse(isPlayableInAppAudioUrl("https://open.spotify.com/track/example", "direct"))
    }

    @Test fun youtubeAndGenericExternalPlatformsAreNotPlayedInsideExoplayer() {
        assertTrue(isExternalStreamingSource("youtube", "https://youtu.be/example"))
        assertTrue(isExternalStreamingSource("external", "https://music.example.com/song"))
        assertFalse(isPlayableInAppAudioUrl("https://music.example.com/song", "external"))
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
