package com.hightechentertainment.app.media

import java.net.URI

private fun parseMediaUri(value: String?): URI? =
    runCatching { URI(value?.trim().orEmpty()).takeIf { it.scheme != null } }.getOrNull()

/** True for catalogue entries that must be opened in their source app/browser, not ExoPlayer. */
internal fun isExternalStreamingSource(sourcePlatform: String?, url: String?): Boolean {
    val platform = sourcePlatform.orEmpty().trim().lowercase().replace('-', '_').replace(' ', '_')
    if (platform in setOf("spotify", "youtube", "external", "soundcloud", "audiomack", "boomplay")) return true

    val host = parseMediaUri(url)?.host?.lowercase().orEmpty()
    return host == "spotify.com" || host.endsWith(".spotify.com") ||
        host == "spotify.link" || host.endsWith(".spotify.link") ||
        host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
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

