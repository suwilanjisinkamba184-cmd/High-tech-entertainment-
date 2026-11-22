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
    fun startupDisplaysApprovedBrandingAndOfficialLogo() {
        // Wait for the final animated branding element so assertions are not
        // racing the splash's staged subtitle/tagline reveal.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("HTE UNIVERSE")
                    .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(HTE_STARTUP_TAGLINE.uppercase())
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("HTE UNIVERSE").fetchSemanticsNode()
        composeRule.onNodeWithText(HTE_STARTUP_TAGLINE.uppercase()).fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("High-Tech Entertainment official logo").fetchSemanticsNode()
    }

    @Test
    fun homeVoiceEntryOpensCompactAssistant() {
        composeRule.waitUntil(timeoutMillis = 25_000) {
            composeRule.onAllNodesWithContentDescription("Open HTE AI Voice Assistant")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Open HTE AI Voice Assistant").performClick()
        composeRule.onNodeWithText("HTE AI Voice Assistant").fetchSemanticsNode()
    }

    @Test
    fun accountTabAndSettingsHubRemainReachable() {
        // NavigationBarItem merges its icon and label semantics. Assert and click
        // the visible label rather than assuming the nested icon description is
        // exposed as a standalone accessibility node on every Compose version.
        composeRule.waitUntil(timeoutMillis = 25_000) {
            composeRule.onAllNodesWithText("Account")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Account").performClick()

        // The control-centre card is below the fold in this LazyColumn. Wait for
        // the list itself, then scroll to the target item so LazyColumn composes
        // it before asserting its text. Waiting for off-screen text first will
        // always time out because that item has not been composed yet.
        composeRule.waitUntil(timeoutMillis = 12_000) {
            runCatching {
                composeRule.onNodeWithTag("member_center_list").fetchSemanticsNode()
            }.isSuccess
        }
        val memberCenter = composeRule.onNodeWithTag("member_center_list")
        memberCenter.performScrollToNode(hasText("HTE Control Centre"))
        composeRule.onNodeWithText("HTE Control Centre").fetchSemanticsNode()
        memberCenter.performScrollToNode(hasText("Open Settings"))
        composeRule.onNodeWithText("Open Settings").performClick()
        composeRule.onNodeWithText("Settings").fetchSemanticsNode()
        composeRule.onNodeWithText("Personalise your universe.").fetchSemanticsNode()
    }
}
