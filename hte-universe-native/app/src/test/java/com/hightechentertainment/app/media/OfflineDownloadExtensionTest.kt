package com.hightechentertainment.app.media

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineDownloadExtensionTest {
    @Test fun mimeTypeOverridesMisleadingUrlSuffix() {
        assertEquals("m4a", resolveMediaFileExtension("https://cdn.example/song.mp3?token=x", "audio", "audio/mp4"))
    }
    @Test fun urlSuffixIsUsedWhenMimeTypeIsUnavailable() {
        assertEquals("flac", resolveMediaFileExtension("https://cdn.example/audio/live.flac?signature=abc", "audio"))
    }
    @Test fun videoMimeTypesAreRecognized() {
        assertEquals("webm", resolveMediaFileExtension("https://cdn.example/clip", "video", "video/webm"))
    }
    @Test fun unknownFormatsUseSafeMediaDefaults() {
        assertEquals("mp3", resolveMediaFileExtension("https://cdn.example/stream", "audio"))
        assertEquals("mp4", resolveMediaFileExtension("https://cdn.example/stream", "video"))
    }
}
