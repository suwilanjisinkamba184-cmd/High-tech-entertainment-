package com.hightechentertainment.app.media

import android.media.audiofx.Equalizer
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/** Standard 10-band user curve. Device Equalizer implementations may expose a different band count. */
internal val HTE_EQUALIZER_FREQUENCIES_HZ = listOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)

/** Interpolates the saved 10-band curve in logarithmic frequency space. */
internal fun interpolateEqualizerGain(frequencyHz: Float, gains: List<Int>): Float {
    if (gains.size != HTE_EQUALIZER_FREQUENCIES_HZ.size) return 0f
    val frequency = frequencyHz.coerceIn(HTE_EQUALIZER_FREQUENCIES_HZ.first(), HTE_EQUALIZER_FREQUENCIES_HZ.last())
    val position = (ln(frequency / HTE_EQUALIZER_FREQUENCIES_HZ.first()) /
        ln(HTE_EQUALIZER_FREQUENCIES_HZ.last() / HTE_EQUALIZER_FREQUENCIES_HZ.first()) * (gains.lastIndex)).coerceIn(0f, gains.lastIndex.toFloat())
    val lower = floor(position).toInt()
    val upper = (lower + 1).coerceAtMost(gains.lastIndex)
    val fraction = position - lower
    return gains[lower] + (gains[upper] - gains[lower]) * fraction
}

/** Applies HTE's 10-band curve to the actual hardware-supported bands for this audio session. */
object HteAudioEffects {
    @Volatile private var equalizer: Equalizer? = null
    @Volatile private var activeSessionId: Int = 0

    @Synchronized
    private fun effectFor(sessionId: Int): Equalizer {
        check(sessionId > 0) { "A valid audio session is required." }
        if (equalizer == null || activeSessionId != sessionId) {
            releaseLocked()
            equalizer = Equalizer(0, sessionId)
            activeSessionId = sessionId
        }
        return requireNotNull(equalizer)
    }

    @Synchronized
    fun supportedBandCount(sessionId: Int): Int = runCatching {
        effectFor(sessionId).numberOfBands.toInt()
    }.getOrDefault(0)

    @Synchronized
    fun apply(sessionId: Int, enabled: Boolean, bands: List<Int>): Boolean {
        if (sessionId <= 0 || bands.size != HTE_EQUALIZER_FREQUENCIES_HZ.size) return false
        return runCatching {
            val effect = effectFor(sessionId)
            val range = effect.bandLevelRange
            for (index in 0 until effect.numberOfBands.toInt()) {
                val centerHz = effect.getCenterFreq(index.toShort()).coerceAtLeast(1) / 1_000f
                val requestedMilliBel = (interpolateEqualizerGain(centerHz, bands) * 100f).roundToInt()
                val level = requestedMilliBel.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                effect.setBandLevel(index.toShort(), level)
            }
            effect.enabled = enabled
            true
        }.getOrElse {
            releaseLocked()
            false
        }
    }

    @Synchronized
    fun release() = releaseLocked()

    private fun releaseLocked() {
        runCatching { equalizer?.enabled = false }
        runCatching { equalizer?.release() }
        equalizer = null
        activeSessionId = 0
    }
}
