package com.elsewhere.app.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.elsewhere.app.Fx
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class YearWheelUiTest {
    @get:Rule val rule = createComposeRule()

    private val record = Fx.utc(2019, 2, 4)..(Fx.utc(2025, 10, 15) + 86399)
    private fun span(y: Int) = yearSpan(record, y)
    private val seen = ArrayList<LongRange>()

    private fun show(initial: LongRange) = rule.setContent {
        var sel by androidx.compose.runtime.remember { mutableStateOf(initial) }
        ElsewhereTheme { YearWheel(record, sel, onRange = { seen += it; sel = it }) }
    }

    private fun state() = rule.onNodeWithContentDescription("Year").fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    @Test fun `prints the selected year big with its neighbours either side, and ALL for the whole record`() {
        show(span(2024))
        rule.onNodeWithContentDescription("Year 2024").assertIsDisplayed()
        rule.onNodeWithTag("wheel-prev").assertTextEquals("2023")
        rule.onNodeWithTag("wheel-next").assertTextEquals("2025")
        rule.onNodeWithTag("tick-2024").assertIsSelected()
        assertEquals("2024", state())
        val info = rule.onNodeWithContentDescription("Year").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(2024f, info.current)
        assertEquals(2019f..2025f, info.range)

        rule.onNodeWithTag("wheel-big").performClick()
        rule.onNodeWithContentDescription("All years").assertTextEquals("A", "L", "L", "A", "L", "L")
        rule.onNodeWithTag("tick-All").assertIsSelected()
        rule.onNodeWithTag("wheel-prev").assertTextEquals("2025")
        assertEquals("All years", state())
        assertEquals(listOf(record), seen)
    }

    @Test fun `setProgress and taps on ghosts and ticks jump to a year`() {
        show(record)
        rule.onNodeWithContentDescription("Year").performSemanticsAction(SemanticsActions.SetProgress) { it(2025f) }
        assertEquals(span(2025), seen.last())
        rule.onNodeWithTag("wheel-prev").performClick()
        assertEquals(span(2024), seen.last())
        rule.onNodeWithTag("tick-2020").performClick()
        assertEquals(span(2020), seen.last())
        rule.onNodeWithTag("tick-2020").assertIsSelected()
        rule.onNodeWithTag("tick-All").performClick()
        assertEquals(record, seen.last())
    }

    @Test fun `a 250 dp drag scrubs two years live and clamps at the record`() {
        show(span(2022))
        val px = with(rule.density) { 250.dp.toPx() }
        rule.onNodeWithContentDescription("Year").performTouchInput { swipeLeft(centerX + px / 2, centerX - px / 2, durationMillis = 400) }
        assertEquals(listOf(span(2023), span(2024)), seen)
        assertEquals("2024", state())
        seen.clear()
        // Two full-width pulls back ask for six years; the record only has five to give.
        repeat(2) { rule.onNodeWithContentDescription("Year").performTouchInput { swipeRight(left + 10f, right - 10f, durationMillis = 800) } }
        assertEquals(span(2019), seen.last())
        assertEquals("2019", state())
    }
}
