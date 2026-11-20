package com.hightechentertainment.app

import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantErrorMessageTest {
    @Test fun networkFailuresGiveConnectionGuidance() {
        assertTrue(voiceFailureMessage("network timeout").contains("internet connection"))
    }

    @Test fun missingElevenLabsPermissionExplainsOwnerConfiguration() {
        val message = voiceFailureMessage("missing convai_write permission")
        assertTrue(message.contains("project owner"))
        assertTrue(message.contains("credentials and permissions"))
    }

    @Test fun expiredSessionAsksUserToSignInAgain() {
        assertTrue(voiceFailureMessage("401 session expired").contains("Sign in again"))
    }
}
