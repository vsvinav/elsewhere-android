package com.elsewhere.app.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elsewhere.app.Fx
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun show(initial: LongRange, pitch: Dp? = null) = rule.setContent {
        var sel by androidx.compose.runtime.remember { mutableStateOf(initial) }
        ElsewhereTheme { YearWheel(record, sel, onRange = { seen += it; sel = it }, pitch = pitch) }
    }

    private fun big() = rule.onNodeWithContentDescription("Year")
    private fun row() = rule.onNodeWithTag("wheel-row")
    private fun state() = big().fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    @Test fun `prints the selected year big with its neighbours either side, and ALL for the whole record`() {
        show(span(2024))
        big().assertIsDisplayed().assertTextEquals("2", "0", "2", "4", "2", "0", "2", "4")
        rule.onNodeWithTag("wheel-prev").assertTextEquals("2023")
        rule.onNodeWithTag("wheel-next").assertTextEquals("2025")
        rule.onNodeWithTag("tick-2024").assertIsSelected()
        assertEquals("2024", state())
        val info = big().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(2024f, info.current)
        assertEquals(2019f..2025f, info.range)

        big().performClick()
        big().assertTextEquals("A", "L", "L", "A", "L", "L")
        rule.onNodeWithTag("tick-ALL").assertIsSelected()
        rule.onNodeWithTag("wheel-prev").assertTextEquals("2025")
        assertEquals("All years", state())
        assertEquals(listOf(record), seen)
    }

    @Test fun `a custom window prints its start year, tagged, with no tick pressed`() {
        show((record.last - 90 * DAY)..record.last)
        big().assertTextEquals("2", "0", "2", "5", "CUSTOM")
        assertEquals("Custom window", state())
        rule.onNodeWithTag("tick-2025").assertIsNotSelected()
        rule.onNodeWithTag("tick-ALL").assertIsNotSelected()
        big().performClick()
        assertEquals(listOf(record), seen)
    }

    @Test fun `setProgress and taps on ghosts and ticks jump to a year`() {
        show(record)
        big().performSemanticsAction(SemanticsActions.SetProgress) { it(2025f) }
        assertEquals(span(2025), seen.last())
        rule.onNodeWithTag("wheel-prev").performClick()
        assertEquals(span(2024), seen.last())
        rule.onNodeWithTag("tick-2020").performClick()
        assertEquals(span(2020), seen.last())
        rule.onNodeWithTag("tick-2020").assertIsSelected()
        rule.onNodeWithTag("tick-ALL").performClick()
        assertEquals(record, seen.last())
    }

    @Test fun `a 250 dp drag scrubs two years live and clamps at the record`() {
        show(span(2022), pitch = 120.dp)
        val px = with(rule.density) { 250.dp.toPx() }
        row().performTouchInput { swipeLeft(centerX + px / 2, centerX - px / 2, durationMillis = 400) }
        assertEquals(listOf(span(2023), span(2024)), seen)
        assertEquals("2024", state())
        seen.clear()
        // Two full-width pulls back ask for six years; the record only has five to give.
        repeat(2) { row().performTouchInput { swipeRight(left + 10f, right - 10f, durationMillis = 800) } }
        assertEquals(span(2019), seen.last())
        assertEquals("2019", state())
    }

    @Test fun `the row follows the finger at the real pitch with no jump at a crossing`() {
        show(span(2022), pitch = 240.dp)
        val at = big().getBoundsInRoot().left
        row().performTouchInput { down(center); moveBy(Offset(-with(rule.density) { 250.dp.toPx() }, 0f)) }
        rule.waitForIdle()
        assertEquals(listOf(span(2023)), seen)
        // 250 dp of finger, one 240 dp crossing: the row sits 10 dp left, not 110 dp right.
        val drift = big().getBoundsInRoot().left - at
        assertTrue("drift was $drift", drift > (-12).dp && drift < (-8).dp)
        row().performTouchInput { up() }
    }

    @Test fun `a press under the touch slop on the big year is not a drag`() {
        show(record)
        big().performTouchInput { down(center); moveBy(Offset(3f, 0f)); up() }
        rule.waitForIdle()
        assertEquals(emptyList<LongRange>(), seen)
    }

    @Test fun `a drag from ALL starts at the last year`() {
        show(record, pitch = 120.dp)
        val px = with(rule.density) { 130.dp.toPx() }
        row().performTouchInput { swipeRight(centerX - px / 2, centerX + px / 2, durationMillis = 300) }
        assertEquals(listOf(span(2024)), seen)
    }
}
