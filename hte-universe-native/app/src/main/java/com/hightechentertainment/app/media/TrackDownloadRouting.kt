package com.hightechentertainment.app.media

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val HTE_DOWNLOAD_PAGE =
    "https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/download/"

/**
 * Builds the public HTE hand-off for a catalogue audio track.
 *
 * Native clients deliberately do not enqueue a permanent audio URL directly:
 * the website delivery flow validates rights, creates a private temporary
 * Supabase copy, and returns a short-lived download link.
 */
internal fun temporaryAudioDownloadPage(trackId: String): String {
    require(trackId.isNotBlank()) { "A track ID is required for temporary audio delivery." }
    val encodedTrackId = URLEncoder.encode(trackId, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
    return "$HTE_DOWNLOAD_PAGE?track=$encodedTrackId"
}
