package com.hightechentertainment.app.media

import com.hightechentertainment.app.normalizeEqualizerBands
import org.junit.Assert.assertEquals
import org.junit.Test

class EqualizerCurveTest {
    @Test fun migratesLegacyFiveBandCurveToTenBands() {
        val migrated = normalizeEqualizerBands(listOf(-10, -5, 0, 5, 10))
        assertEquals(10, migrated.size)
        assertEquals(-10, migrated.first())
        assertEquals(10, migrated.last())
        assertEquals(0, migrated[4])
    }

    @Test fun clampsPersistedGainsToSupportedRange() {
        assertEquals(List(10) { 12 }, normalizeEqualizerBands(List(10) { 40 }))
    }

    @Test fun interpolationUsesLogFrequencyCurveEndpoints() {
        val curve = listOf(-12, -9, -6, -3, 0, 3, 6, 9, 10, 12)
        assertEquals(-12f, interpolateEqualizerGain(31f, curve), 0.001f)
        assertEquals(12f, interpolateEqualizerGain(16_000f, curve), 0.001f)
    }
}
