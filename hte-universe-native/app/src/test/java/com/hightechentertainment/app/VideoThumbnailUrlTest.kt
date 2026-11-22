package com.hightechentertainment.app

import com.hightechentertainment.app.data.VideoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoThumbnailUrlTest {
    @Test
    fun keepsValidHttpsThumbnail() {
        val video = VideoDto(
            id = "v1", title = "Test", videoUrl = "https://cdn.example.com/video.mp4",
            thumbnailUrl = "https://cdn.example.com/poster.jpg"
        )
        assertEquals("https://cdn.example.com/poster.jpg", resolveVideoThumbnailUrl(video))
    }

    @Test
    fun derivesPosterFromYoutubeIdWhenThumbnailIsMissing() {
        val video = VideoDto(
            id = "v2", title = "Test", videoUrl = "https://www.youtube.com/watch?v=abcdefghijk",
            youtubeId = "abcdefghijk"
        )
        assertEquals("https://img.youtube.com/vi/abcdefghijk/hqdefault.jpg", resolveVideoThumbnailUrl(video))
    }

    @Test
    fun rejectsWatchPageAsThumbnailAndUsesYoutubePoster() {
        val video = VideoDto(
            id = "v3", title = "Test", videoUrl = "https://youtu.be/abcdefghijk",
            thumbnailUrl = "https://www.youtube.com/watch?v=abcdefghijk"
        )
        assertEquals("https://img.youtube.com/vi/abcdefghijk/hqdefault.jpg", resolveVideoThumbnailUrl(video))
    }

    @Test
    fun doesNotUseInsecureOrMalformedThumbnail() {
        val video = VideoDto(
            id = "v4", title = "Test", videoUrl = "https://cdn.example.com/video.mp4",
            thumbnailUrl = "http://cdn.example.com/poster.jpg"
        )
        assertNull(resolveVideoThumbnailUrl(video))
    }

    @Test
    fun missingYoutubePosterUsesOrderedFallbackVariants() {
        val video = VideoDto(
            id = "v5", title = "Test", videoUrl = "https://www.youtube.com/watch?v=abcdefghijk",
            youtubeId = "abcdefghijk", thumbnailUrl = "https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg"
        )
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg",
                "https://img.youtube.com/vi/abcdefghijk/hqdefault.jpg",
                "https://img.youtube.com/vi/abcdefghijk/sddefault.jpg",
                "https://img.youtube.com/vi/abcdefghijk/mqdefault.jpg",
                "https://img.youtube.com/vi/abcdefghijk/default.jpg"
            ),
            resolveVideoThumbnailCandidates(video)
        )
    }

    @Test
    fun fallbackMovesToNextDistinctCandidate() {
        val video = VideoDto(
            id = "v6", title = "Test", videoUrl = "https://youtu.be/abcdefghijk",
            youtubeId = "abcdefghijk"
        )
        assertEquals(
            "https://img.youtube.com/vi/abcdefghijk/sddefault.jpg",
            youtubeThumbnailFallbackUrl(video, "https://img.youtube.com/vi/abcdefghijk/hqdefault.jpg")
        )
        assertNull(youtubeThumbnailFallbackUrl(video, "https://img.youtube.com/vi/abcdefghijk/default.jpg"))
    }

    @Test
    fun candidatesAreUniqueAndHttpsOnly() {
        val video = VideoDto(
            id = "v7", title = "Test", videoUrl = "https://www.youtube.com/watch?v=abcdefghijk",
            youtubeId = "abcdefghijk", thumbnailUrl = "https://img.youtube.com/vi/abcdefghijk/hqdefault.jpg"
        )
        val candidates = resolveVideoThumbnailCandidates(video)
        assertEquals(candidates.size, candidates.toSet().size)
        assertTrue(candidates.all { it.startsWith("https://") })
    }
}
