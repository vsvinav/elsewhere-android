package com.elsewhere.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.ui.shell.RangeViewModel
import com.elsewhere.app.ui.shell.ShellViewModel
import com.elsewhere.app.ui.shell.epoch
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DatePickFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `tapping a day in the From sheet moves the range`() {
        val record = Fx.record(range = Fx.utc(2024, 0, 1)..Fx.utc(2024, 11, 31))
        val range = RangeViewModel(Fakes.Source(record))
        rule.setContent {
            val shell = remember { ShellViewModel(Fakes.Store(record), Fakes.Maps()) }
            ElsewhereTheme { AppScaffold({}, {}, shell, range) }
        }
        rule.onNodeWithText("1 JAN 2024").performClick()
        rule.onNode(isSelectable() and hasText("15")).performClick()
        rule.waitForIdle()
        assertEquals(LocalDate.of(2024, 1, 15).epoch(), range.range.value.first)
        assertEquals(Fx.utc(2024, 11, 31), range.range.value.last)
    }
}
