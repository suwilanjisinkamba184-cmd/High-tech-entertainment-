package com.hightechentertainment.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HteSplashSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startupDisplaysOfficialLogoAndApprovedBranding() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("High-Tech Entertainment official logo")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("High-Tech Entertainment official logo").assertExists()
        composeRule.onNodeWithText("HTE UNIVERSE").assertExists()
        composeRule.onNodeWithText(HTE_STARTUP_TAGLINE.uppercase()).assertExists()
    }
}
