package com.hightechentertainment.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupCopyTest {
    @Test
    fun splashUsesTheOfficialHighTechEntertainmentSlogan() {
        assertEquals("Music. Beats. Production. Beyond.", HTE_STARTUP_TAGLINE)
    }
}
