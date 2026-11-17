package com.hightechentertainment.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeReleaseVersionTest {
    @Test
    fun newerBuildWithinSameMarketingVersionIsAnUpdate() {
        assertTrue(isNativeReleaseNewer("native-v4.1.2-build48", "4.1.2", 47L))
    }

    @Test
    fun installedCurrentBuildIsNotOfferedAgain() {
        assertFalse(isNativeReleaseNewer("native-v4.1.2-build47", "4.1.2", 47L))
    }

    @Test
    fun olderBuildWithinSameMarketingVersionIsNotAnUpdate() {
        assertFalse(isNativeReleaseNewer("native-v4.1.2-build46", "4.1.2", 47L))
    }

    @Test
    fun newerMarketingVersionIsAnUpdateEvenWithLowerBuildNumber() {
        assertTrue(isNativeReleaseNewer("native-v4.1.3-build1", "4.1.2", 47L))
    }

    @Test
    fun releaseTagOrderingIncludesBuildNumber() {
        assertTrue(compareNativeReleaseTags("native-v4.1.2-build48", "native-v4.1.2-build47") > 0)
        assertEquals(0, compareNativeReleaseTags("native-v4.1.2-build47", "native-v4.1.2-build47"))
    }
}
