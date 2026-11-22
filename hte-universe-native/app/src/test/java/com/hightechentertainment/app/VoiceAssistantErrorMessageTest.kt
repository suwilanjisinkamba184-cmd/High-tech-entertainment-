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
        assertTrue(message.contains("agent permissions"))
        assertTrue(message.contains("server-side API credentials"))
    }

    @Test fun expiredSessionAsksUserToSignInAgain() {
        assertTrue(voiceFailureMessage("401 session expired").contains("Sign in again"))
    }

    @Test fun providerPermissionFailuresExplainTheCorrectOwnerAction() {
        val message = voiceFailureMessage("403 forbidden")
        assertTrue(message.contains("agent permissions"))
        assertTrue(message.contains("server-side API credentials"))
    }
}
