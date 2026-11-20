package com.hightechentertainment.app.media

import java.net.URI
import java.net.URLDecoder

sealed interface YouTubeSubmissionRoute {
    data class SingleVideo(val videoId: String, val canonicalWatchUrl: String) : YouTubeSubmissionRoute
    data class Playlist(val playlistId: String) : YouTubeSubmissionRoute
    data class Channel(val handleOrId: String) : YouTubeSubmissionRoute
    data class Invalid(val userMessage: String) : YouTubeSubmissionRoute
}

/** Classifies YouTube URLs before the single-video submission Edge Function is called. */
object YouTubeLinkClassifier {
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")
    private val supportedHosts = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

    fun classify(rawInput: String): YouTubeSubmissionRoute {
        val input = rawInput.trim()
        if (input.isEmpty()) return YouTubeSubmissionRoute.Invalid("Please paste a YouTube video link.")
        val normalized = if (input.startsWith("http://", true) || input.startsWith("https://", true)) input else "https://$input"
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: return YouTubeSubmissionRoute.Invalid("The YouTube link format is invalid.")
        val host = uri.host?.lowercase()?.removePrefix("www.")
            ?: return YouTubeSubmissionRoute.Invalid("The YouTube link is missing a valid host.")
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) {
            return YouTubeSubmissionRoute.Invalid("Use a secure HTTPS YouTube link.")
        }
        if (host !in supportedHosts) return YouTubeSubmissionRoute.Invalid("Only YouTube links are supported here.")

        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val query = parseQuery(uri.rawQuery)
        val first = segments.firstOrNull().orEmpty()

        if (host == "youtu.be") {
            val id = segments.firstOrNull().orEmpty()
            return singleVideoOrInvalid(id)
        }

        if (first == "watch") {
            val id = query["v"].orEmpty()
            if (id.isNotBlank()) return singleVideoOrInvalid(id)
            val listId = query["list"].orEmpty()
            if (listId.isNotBlank()) return YouTubeSubmissionRoute.Playlist(listId)
            return YouTubeSubmissionRoute.Invalid("This YouTube watch link is missing its video ID.")
        }

        if (first in setOf("shorts", "embed", "live")) {
            return singleVideoOrInvalid(segments.getOrNull(1).orEmpty())
        }

        if (first == "playlist" || query["list"].orEmpty().isNotBlank()) {
            return YouTubeSubmissionRoute.Playlist(query["list"].orEmpty())
        }

        if (first.startsWith("@") || first in setOf("channel", "c", "user")) {
            val identifier = if (first.startsWith("@")) first else segments.getOrNull(1).orEmpty()
            return if (identifier.isNotBlank()) YouTubeSubmissionRoute.Channel(identifier)
            else YouTubeSubmissionRoute.Invalid("The YouTube channel link is missing its channel identifier.")
        }

        return YouTubeSubmissionRoute.Invalid("Only individual public YouTube videos can be submitted here.")
    }

    private fun singleVideoOrInvalid(id: String): YouTubeSubmissionRoute =
        if (videoIdPattern.matches(id)) {
            YouTubeSubmissionRoute.SingleVideo(id, "https://www.youtube.com/watch?v=$id")
        } else {
            YouTubeSubmissionRoute.Invalid("The YouTube video ID is invalid or missing.")
        }

    private fun parseQuery(rawQuery: String?): Map<String, String> = rawQuery.orEmpty()
        .split('&')
        .filter(String::isNotBlank)
        .mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) null else runCatching {
                URLDecoder.decode(pair.substring(0, index), "UTF-8") to
                    URLDecoder.decode(pair.substring(index + 1), "UTF-8")
            }.getOrNull()
        }.toMap()
}
