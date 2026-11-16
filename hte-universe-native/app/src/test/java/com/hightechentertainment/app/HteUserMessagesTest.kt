package com.hightechentertainment.app

import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HteUserMessagesTest {
    @Test
    fun genericFailureUsesFallbackWithoutLeakingTechnicalDetails() {
        val message = IllegalStateException("internal token=do-not-display").toHteUserMessage("Could not load this content. Please retry.")

        assertEquals("Could not load this content. Please retry.", message)
        assertFalse(message.contains("internal token"))
        assertFalse(message.contains("do-not-display"))
    }

    @Test
    fun nestedNetworkFailureUsesFriendlyConnectionGuidance() {
        val error = IllegalStateException("request failed: api.internal.local", UnknownHostException("api.internal.local"))

        val message = error.toHteUserMessage("Generic fallback")

        assertTrue(message.startsWith("No internet connection"))
        assertFalse(message.contains("api.internal.local"))
    }

    @Test
    fun securityFailureUsesFriendlyPermissionMessage() {
        val message = SecurityException("permission denied by internal policy").toHteUserMessage("Generic fallback")

        assertTrue(message.contains("do not have permission"))
        assertFalse(message.contains("internal policy"))
    }
}
