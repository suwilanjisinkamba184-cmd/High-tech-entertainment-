package com.hightechentertainment.app.media

import android.media.audiofx.Equalizer

/** Owns the equalizer attached to HTE's active Media3 audio session. */
object HteAudioEffects {
    @Volatile private var equalizer: Equalizer? = null

    @Synchronized
    fun apply(sessionId: Int, enabled: Boolean, bands: List<Int>): Boolean {
        if (sessionId <= 0) return false
        return runCatching {
            equalizer?.release()
            equalizer = null
            if (!enabled) return true
            val effect = Equalizer(0, sessionId)
            effect.enabled = true
            val range = effect.bandLevelRange
            val count = minOf(effect.numberOfBands.toInt(), bands.size)
            for (index in 0 until count) {
                val value = bands[index].coerceIn(-12, 12)
                val mapped = (range[0] + (range[1] - range[0]) * ((value + 12) / 24f)).toInt().toShort()
                effect.setBandLevel(index.toShort(), mapped)
            }
            equalizer = effect
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }
}
