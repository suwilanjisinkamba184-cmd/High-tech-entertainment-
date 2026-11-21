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

    @Test fun embedded403InsideIdentifierDoesNotTriggerPermissionGuidance() {
        val message = voiceFailureMessage("Unexpected server error; request_id=abc403def")
        assertTrue(message.contains("temporarily unavailable"))
    }

    @Test fun embedded401InsideIdentifierDoesNotTriggerSessionGuidance() {
        val message = voiceFailureMessage("Unexpected server error; request_id=abc401def")
        assertTrue(message.contains("temporarily unavailable"))
    }

    @Test fun provider403WinsOverWebSocketConnectionWording() {
        val message = voiceFailureMessage("403 WebSocket connection failed")
        assertTrue(message.contains("agent permissions"))
        assertTrue(message.contains("server-side API credentials"))
        assertTrue(!message.contains("internet connection"))
    }

    @Test fun providerPermissionFailuresExplainTheCorrectOwnerAction() {
        val message = voiceFailureMessage("403 forbidden")
        assertTrue(message.contains("agent permissions"))
        assertTrue(message.contains("server-side API credentials"))
    }
}
