package com.hightechentertainment.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineDownloadResolutionTest {
    @Test
    fun resolvesOnlyCompletedDownloadForExactSourceUrl() {
        val records = listOf(
            OfflineDownloadRecord(
                id = 1L, title = "Song", mediaType = "audio", status = "Downloaded",
                localUri = "content://downloads/my_downloads/1", sourceUrl = "https://cdn.example.com/song.mp3"
            ),
            OfflineDownloadRecord(
                id = 2L, title = "Video", mediaType = "video", status = "Downloading",
                localUri = null, sourceUrl = "https://cdn.example.com/video.mp4"
            ),
            OfflineDownloadRecord(
                id = 3L, title = "Other", mediaType = "video", status = "Downloaded",
                localUri = "content://downloads/my_downloads/3", sourceUrl = "https://cdn.example.com/other.mp4"
            )
        )

        assertEquals(
            "content://downloads/my_downloads/1",
            resolveDownloadedLocalUri(records, "https://cdn.example.com/song.mp3")
        )
        assertNull(resolveDownloadedLocalUri(records, "https://cdn.example.com/video.mp4"))
        assertNull(resolveDownloadedLocalUri(records, "https://cdn.example.com/missing.mp4"))
    }
}
