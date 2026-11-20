package com.hightechentertainment.app.media

import java.net.URI

private fun parseMediaUri(value: String?): URI? =
    runCatching { URI(value?.trim().orEmpty()).takeIf { it.scheme != null } }.getOrNull()

private val spotifyContentTypes = setOf("track", "album", "playlist", "artist", "show", "episode")

/** Identifies Spotify links without treating their web pages as playable audio files. */
internal fun isSpotifySource(sourcePlatform: String?, url: String?): Boolean {
    val platform = sourcePlatform.orEmpty().trim().lowercase().replace('-', '_').replace(' ', '_')
    if (platform == "spotify") return true
    val host = parseMediaUri(url)?.host?.lowercase().orEmpty()
    return host == "spotify.com" || host.endsWith(".spotify.com") ||
        host == "spotify.link" || host.endsWith(".spotify.link")
}

/**
 * Converts canonical Spotify links into the official Spotify app URI.
 * The Android app remote/deep-link route delegates playback to Spotify; HTE never extracts or
 * proxies Spotify audio. Returns null when the link cannot safely be converted.
 */
internal fun spotifyAppUri(url: String?): String? {
    val raw = url?.trim().orEmpty()
    if (raw.matches(Regex("spotify:(track|album|playlist|artist|show|episode):[A-Za-z0-9]+"))) return raw
    val uri = parseMediaUri(raw) ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
    if (host != "open.spotify.com") return null
    val segments = uri.path.split('/').filter(String::isNotBlank)
    val typeIndex = segments.indexOfFirst { it in spotifyContentTypes }
    if (typeIndex < 0) return null
    val type = segments.getOrNull(typeIndex) ?: return null
    val id = segments.getOrNull(typeIndex + 1)?.takeIf { it.matches(Regex("[A-Za-z0-9]{10,32}")) }
        ?: return null
    return "spotify:$type:$id"
}

/** Extracts a canonical YouTube video ID from watch, short, shorts, live, and embed links. */
internal fun extractYouTubeVideoId(raw: String?): String? {
    val uri = parseMediaUri(raw) ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
    val segments = uri.path.split('/').filter(String::isNotBlank)
    val id = when (host) {
        "youtu.be" -> segments.firstOrNull()
        "youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com" -> {
            val queryId = uri.rawQuery.orEmpty().split('&')
                .firstOrNull { it.startsWith("v=") }
                ?.substringAfter("v=")
            queryId ?: if (segments.firstOrNull() in setOf("embed", "shorts", "live")) segments.getOrNull(1) else null
        }
        else -> null
    }
    return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
}

/** True for catalogue entries that must be opened in their source app/browser, not ExoPlayer. */
internal fun isExternalStreamingSource(sourcePlatform: String?, url: String?): Boolean {
    val platform = sourcePlatform.orEmpty().trim().lowercase().replace('-', '_').replace(' ', '_')
    if (platform in setOf("spotify", "youtube", "external", "soundcloud", "audiomack", "boomplay")) return true

    val host = parseMediaUri(url)?.host?.lowercase().orEmpty()
    return host == "spotify.com" || host.endsWith(".spotify.com") ||
        host == "spotify.link" || host.endsWith(".spotify.link") ||
        host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
}

/** Accept only actual in-app media URIs; external streaming pages and cleartext HTTP are excluded. */
internal fun isPlayableInAppAudioUrl(url: String?, sourcePlatform: String? = null): Boolean {
    if (isExternalStreamingSource(sourcePlatform, url)) return false
    val uri = parseMediaUri(url) ?: return false
    return when (uri.scheme.lowercase()) {
        "https" -> !uri.host.isNullOrBlank()
        "file", "content" -> true
        else -> false
    }
}

/** Direct video files and HTTPS streaming manifests are supported; web pages are not. */
internal fun isPlayableVideoUrl(url: String?): Boolean {
    if (isExternalStreamingSource(null, url)) return false
    val uri = parseMediaUri(url) ?: return false
    return when (uri.scheme.lowercase()) {
        "https" -> !uri.host.isNullOrBlank()
        "file", "content" -> true
        else -> false
    }
}

/** Public downloads must use a secure HTTPS URL with a valid host. */
internal fun isSecureMediaDownloadUrl(url: String?): Boolean {
    val uri = parseMediaUri(url) ?: return false
    return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
        uri.userInfo == null
}
