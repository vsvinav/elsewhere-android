package com.elsewhere.app.ui.record

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.elsewhere.app.data.Recording
import com.elsewhere.app.record.RecordStats
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RecordScreenTest {
    @get:Rule val rule = createComposeRule()
    private val now = 1_800_000_000L
    private fun rows(vararg states: PermState) = Perm.entries.zip(states).map { (p, s) -> PermRow(p, s) }

    @Test fun `with every permission granted the details are folded and the ticket reads recording`() {
        val ui = RecordUi(rows(PermState.GRANTED, PermState.GRANTED, PermState.GRANTED, PermState.GRANTED), true, Recording(true, now - 86400, 0L), RecordStats(1204, 7, now - 180), true, now)
        rule.setContent { ElsewhereTheme { RecordScreen(ui, RecordActions()) } }
        rule.onNodeWithText("1,204 fixes · last 3 min ago").assertIsDisplayed()
        rule.onNodeWithText("STOP RECORDING").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("MOTION").assertDoesNotExist()
        rule.onNodeWithTag(DETAILS_TAG).performScrollTo().performClick()
        rule.onNodeWithText("MOTION").performScrollTo().assertIsDisplayed()
    }

    @Test fun `a missing permission opens the details on its own`() {
        val ui = RecordUi(rows(PermState.GRANTED, PermState.GRANTED, PermState.ASK, PermState.GRANTED), true, Recording(), RecordStats(0, 0, null), true, now)
        rule.setContent { ElsewhereTheme { RecordScreen(ui, RecordActions()) } }
        rule.onNodeWithText("0 fixes · no fixes yet").assertIsDisplayed()
        rule.onNodeWithText("START RECORDING").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("MOTION").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("ALLOW").performScrollTo().assertIsDisplayed()
    }
}
