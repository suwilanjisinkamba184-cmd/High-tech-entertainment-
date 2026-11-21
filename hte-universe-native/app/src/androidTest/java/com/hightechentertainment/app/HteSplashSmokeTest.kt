package com.hightechentertainment.app

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
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
        // Wait for the final animated branding element so assertions are not
        // racing the splash's staged subtitle/tagline reveal.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("High-Tech Entertainment official logo")
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("HTE UNIVERSE")
                    .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(HTE_STARTUP_TAGLINE.uppercase())
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("High-Tech Entertainment official logo").assertExists()
        composeRule.onNodeWithText("HTE UNIVERSE").assertExists()
        composeRule.onNodeWithText(HTE_STARTUP_TAGLINE.uppercase()).assertExists()
    }
    @Test
    fun homeVoiceEntryOpensCompactAssistant() {
        composeRule.waitUntil(timeoutMillis = 25_000) {
            composeRule.onAllNodesWithContentDescription("Open HTE AI Voice Assistant")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Open HTE AI Voice Assistant").performClick()
        composeRule.onNodeWithText("HTE AI Voice Assistant").assertExists()
    }

    @Test
    fun accountTabAndSettingsHubRemainReachable() {
        composeRule.waitUntil(timeoutMillis = 25_000) {
            composeRule.onAllNodesWithContentDescription("Account")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Account").performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("Welcome back")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Welcome back").assertExists()
        composeRule.onNodeWithText("Sign in").assertExists()
        composeRule.onNodeWithTag("member_center_list")
            .performScrollToNode(hasText("Open Settings"))
        composeRule.onNodeWithText("Open Settings").performClick()
        composeRule.onNodeWithText("Settings").assertExists()
        composeRule.onNodeWithText("Personalise your universe.").assertExists()
    }

}
