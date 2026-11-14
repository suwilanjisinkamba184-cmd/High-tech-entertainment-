package com.hightechentertainment.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HteSecurityFirewallTest {
    @Test
    fun trustedFingerprintIsAFullSha256Digest() {
        assertTrue(HteSecurityFirewall.TRUSTED_CERT_SHA256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun certificateFingerprintUsesLowercaseSha256Hex() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HteSecurityFirewall.sha256Fingerprint("abc".toByteArray())
        )
    }

    @Test
    fun fingerprintComparisonIsCaseInsensitiveAndRejectsMismatch() {
        assertTrue(HteSecurityFirewall.isTrustedFingerprint(HteSecurityFirewall.TRUSTED_CERT_SHA256.uppercase()))
        assertFalse(HteSecurityFirewall.isTrustedFingerprint("0".repeat(64)))
    }
}
