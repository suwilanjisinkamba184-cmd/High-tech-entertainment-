package com.hightechentertainment.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAssistantVoicesTest {
    @Test
    fun preservesSupportedVoicePreset() {
        assertEquals(VoiceAssistantVoices.DEFAULT_ID, VoiceAssistantVoices.resolveId(VoiceAssistantVoices.DEFAULT_ID))
    }

    @Test
    fun repairsStaleStoredVoiceIdToCurrentDefault() {
        assertEquals(VoiceAssistantVoices.DEFAULT_ID, VoiceAssistantVoices.resolveId("deleted_or_old_voice_id"))
    }
}
