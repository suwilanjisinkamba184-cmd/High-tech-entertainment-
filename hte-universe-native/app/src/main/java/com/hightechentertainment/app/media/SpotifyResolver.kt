package com.hightechentertainment.app.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Resolves Spotify's official short links without extracting or proxying Spotify media. */
internal suspend fun resolveSpotifyEmbedUrl(rawUrl: String): String? = withContext(Dispatchers.IO) {
    val cleaned = rawUrl.trim()
    val initial = runCatching { URI(cleaned) }.getOrNull() ?: return@withContext null
    val initialHost = initial.host?.lowercase()?.removePrefix("www.") ?: return@withContext null
    if (initial.scheme?.lowercase() != "https" || initial.userInfo != null || initial.port !in listOf(-1, 443)) {
        return@withContext null
    }
    if (initialHost == "open.spotify.com") return@withContext spotifyEmbedUrl(cleaned)
    if (initialHost != "spotify.link") return@withContext null

    var current = runCatching { URL(cleaned) }.getOrNull() ?: return@withContext null
    repeat(5) {
        val uri = runCatching { URI(current.toString()) }.getOrNull() ?: return@withContext null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return@withContext null
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) {
            return@withContext null
        }
        if (host == "open.spotify.com") return@withContext spotifyEmbedUrl(uri.toString())
        if (host != "spotify.link") return@withContext null

        val connection = (current.openConnection() as? HttpURLConnection)
            ?: return@withContext null
        val code: Int
        val location: String?
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "HTE-Universe-Android")
            code = connection.responseCode
            location = connection.getHeaderField("Location")
        } catch (_: Exception) {
            return@withContext null
        } finally {
            connection.disconnect()
        }
        if (code !in 300..399 || location.isNullOrBlank()) return@withContext null
        current = runCatching { URL(current, location) }.getOrNull() ?: return@withContext null
    }
    null
}
