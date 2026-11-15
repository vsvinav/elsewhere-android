package com.elsewhere.app.ui.tabs.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.ui.Tab
import com.elsewhere.app.ui.nav.Origin
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class OriginTagTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `the stub prints the figure it came from and a tap goes back`() {
        var backs = 0
        rule.setContent { ElsewhereTheme { OriginTag(Origin("42 countries", Tab.TOUR), onBack = { backs++ }) } }
        rule.onNodeWithText("FROM · 42 COUNTRIES").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back to Tour").performClick()
        assertEquals(1, backs)
    }

    @Test fun `no origin, no stub`() {
        rule.setContent { ElsewhereTheme { OriginTag(null, onBack = {}) } }
        rule.onNodeWithText("FROM", substring = true).assertDoesNotExist()
    }
}
