package com.elsewhere.app.ui.record

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.highlights.DEFAULT_MINUTE
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HighlightsBillTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `ticking the box turns the daily note on at eight, unticking turns it off`() {
        val set = ArrayList<Int?>()
        val at = mutableStateOf<Int?>(null)
        rule.setContent { ElsewhereTheme { HighlightsBill(at.value, { set.add(it); at.value = it }) } }
        rule.onNodeWithText("Send a daily highlight").performClick()
        assertEquals(listOf<Int?>(DEFAULT_MINUTE), set)
        rule.onNodeWithText("08:00").assertIsDisplayed()
        rule.onNodeWithText("Send a daily highlight").performClick()
        assertEquals(listOf(DEFAULT_MINUTE, null), set)
    }
    @Test fun `the hour opens a time picker whose Set button saves the new minute`() {
        var at: Int? = 9 * 60 + 30
        rule.setContent { ElsewhereTheme { HighlightsBill(at, { at = it }) } }
        rule.onNodeWithContentDescription("Change the hour").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("HIGHLIGHT AT").assertIsDisplayed()
        rule.onNodeWithText("SET").performClick()
        rule.waitForIdle()
        assertEquals(9 * 60 + 30, at)
    }
}
