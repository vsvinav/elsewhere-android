package com.elsewhere.app.ui.world

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.elsewhere.app.share.LocalPrinting

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaveMenuTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `save opens the three formats and text when the card has one, and text goes to the share sheet`() {
        rule.setContent { ElsewhereTheme { Bill(share = Share("nights") { "⬛🟥" }) { Text("body") } } }
        rule.onNodeWithText("STORY 9:16").assertDoesNotExist()
        rule.onNodeWithContentDescription("Save this card").performClick()
        rule.onNodeWithText("FREE").assertIsDisplayed()
        rule.onNodeWithText("STORY 9:16").assertIsDisplayed()
        rule.onNodeWithText("POST 4:5").assertIsDisplayed()
        rule.onNodeWithText("TEXT").performClick()
        val send = shadowOf(rule.activity).nextStartedActivity.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("⬛🟥", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun `a card that cannot print says so for a moment instead of crashing`() {
        // The press fails on the sheet, not in composition: a draw that throws, as a full disk or a missing app would.
        rule.setContent {
            ElsewhereTheme {
                Bill(share = Share("broken")) {
                    val printing = LocalPrinting.current
                    Text("body", Modifier.drawBehind { if (printing) error("no ink") })
                }
            }
        }
        rule.onNodeWithContentDescription("Save this card").performClick()
        rule.onNodeWithText("FREE").performClick()
        rule.onNodeWithText("COULDN'T SAVE").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(3000)
        rule.onNodeWithText("SAVE ▾").assertIsDisplayed()
    }

    @Test fun `a pass without a text version does not offer one`() {
        rule.setContent { ElsewhereTheme { Pass(share = Share("cities")) { Text("body") } } }
        rule.onNodeWithContentDescription("Save this card").performClick()
        rule.onNodeWithText("FREE").assertIsDisplayed()
        rule.onNodeWithText("TEXT").assertDoesNotExist()
    }
}
