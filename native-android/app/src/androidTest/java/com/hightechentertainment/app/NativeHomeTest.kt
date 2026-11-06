package com.hightechentertainment.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class NativeHomeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsHighTechBranding() {
        composeRule.onNodeWithText("HIGH-TECH").assertIsDisplayed()
        composeRule.onNodeWithText("Explore music").assertIsDisplayed()
    }
}
