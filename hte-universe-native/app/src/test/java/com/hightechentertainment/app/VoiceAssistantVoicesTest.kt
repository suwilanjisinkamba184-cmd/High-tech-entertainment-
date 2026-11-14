package com.hightechentertainment.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantVoicesTest {
    @Test
    fun defaultVoiceIsAvailable() {
        assertNotNull(VoiceAssistantVoices.find(VoiceAssistantVoices.DEFAULT_ID))
        assertEquals(VoiceAssistantVoices.DEFAULT_ID, VoiceAssistantVoices.default.id)
    }

    @Test
    fun voiceChoicesHaveUniqueIdsAndThreeCuratedOptions() {
        assertEquals(3, VoiceAssistantVoices.all.size)
        assertEquals(3, VoiceAssistantVoices.all.map { it.id }.distinct().size)
        assertTrue(VoiceAssistantVoices.all.all { it.label.isNotBlank() && it.description.isNotBlank() })
    }
}
