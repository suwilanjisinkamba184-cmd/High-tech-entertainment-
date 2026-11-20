package com.hightechentertainment.app.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadManagerTest {
    @Test fun acceptsSecureHttpsMediaUrls() {
        assertTrue(isSecureMediaDownloadUrl("https://cdn.example.com/audio/track.mp3"))
        assertTrue(isSecureMediaDownloadUrl("HTTPS://cdn.example.com/video/clip.mp4"))
    }
    @Test fun rejectsInsecureOrMalformedMediaUrls() {
        assertFalse(isSecureMediaDownloadUrl("http://cdn.example.com/audio/track.mp3"))
        assertFalse(isSecureMediaDownloadUrl("file:///sdcard/track.mp3"))
        assertFalse(isSecureMediaDownloadUrl("javascript:alert(1)"))
        assertFalse(isSecureMediaDownloadUrl("https://user:password@cdn.example.com/track.mp3"))
        assertFalse(isSecureMediaDownloadUrl("not a URL"))
    }
}
