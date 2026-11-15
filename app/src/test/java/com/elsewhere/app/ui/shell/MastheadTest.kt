package com.elsewhere.app.ui.shell

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.data.Counters
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MastheadTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `the menu offers Settings in place of the key and units rows`() {
        var settings = 0
        rule.setContent {
            ElsewhereTheme { Masthead(name = "Ana", counters = Counters(), onHome = {}, onChangeAnswers = {}, onSettings = { settings++ }, onStartOver = {}) }
        }
        rule.onNodeWithContentDescription("Menu").performClick()
        rule.onNodeWithText("CHANGE ANSWERS").assertIsDisplayed()
        rule.onNodeWithText("START OVER").assertIsDisplayed()
        rule.onAllNodesWithText("MAPS KEY").assertCountEquals(0)
        rule.onAllNodesWithText("UNITS: KM").assertCountEquals(0)
        rule.onNodeWithText("SETTINGS").performClick()
        assertEquals(1, settings)
    }
}
