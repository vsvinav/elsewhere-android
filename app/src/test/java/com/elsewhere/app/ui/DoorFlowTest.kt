package com.elsewhere.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.DoorNav
import com.elsewhere.app.ui.nav.Origin
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.shell.RangeViewModel
import com.elsewhere.app.ui.shell.ShellViewModel
import com.elsewhere.app.ui.tabs.map.OriginTag
import com.elsewhere.app.ui.world.ElsewhereTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DoorFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val record = Fx.record(range = Fx.utc(2024, 0, 1)..Fx.utc(2024, 11, 31))
    private val source = Fakes.Source(record)
    private val store = RangeStore(source, CoroutineScope(Dispatchers.Main))
    private val doors = DoorNav(store, CoroutineScope(Dispatchers.Main))

    private fun show() {
        val range = RangeViewModel(store)
        rule.setContent {
            val maps = Fakes.Maps()
            val shell = remember { ShellViewModel(Fakes.Store(record), source, maps, Fakes.lookups(maps, CoroutineScope(Dispatchers.Main)), doors) }
            ElsewhereTheme {
                AppScaffold({}, {}, {}, shell, range, content = { t, _ ->
                    // Every bill prints one figure as a door; the map wears the stub it came in on.
                    Column {
                        com.elsewhere.app.ui.nav.Door(Door.Range(Fx.utc(2024, 2, 1), Fx.utc(2024, 2, 31), "${t.label} figure")) { Text("${t.label} figure") }
                        // Stands in for a bill's scroll: saved and restored with the entry, lost when the entry is.
                        var bumps by rememberSaveable { mutableIntStateOf(0) }
                        Text("${t.label} bumped $bumps", Modifier.clickable { bumps++ })
                        if (t == Tab.MAP) { val o = doors.origin.collectAsState().value; OriginTag(o, doors::back) }
                    }
                })
            }
        }
    }

    @Test fun `a door opens the map on its window, and the stub takes the reader back to the bill`() {
        show()
        rule.onNodeWithText("NUMBERS").performClick()
        rule.onNodeWithContentDescription("Open on the map: Numbers figure").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Map figure").assertIsDisplayed()
        assertEquals(Fx.utc(2024, 2, 1)..Fx.utc(2024, 2, 31), store.range.value)
        assertEquals(Origin("Numbers figure", Tab.NUMBERS), doors.origin.value)

        rule.onNodeWithContentDescription("Back to Numbers").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Numbers figure").assertIsDisplayed()
        assertNull(doors.origin.value)
    }

    @Test fun `system Back on the map goes back through the door`() {
        show()
        rule.onNodeWithContentDescription("Open on the map: Tour figure").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Map figure").assertIsDisplayed()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Tour figure").assertIsDisplayed()
        assertNull(doors.origin.value)
    }

    @Test fun `system Back leaves no extra entry behind, so a second Back lands on the Tour, not the map`() {
        show()
        rule.onNodeWithText("NUMBERS").performClick()
        rule.onNodeWithContentDescription("Open on the map: Numbers figure").performClick()
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Numbers figure").assertIsDisplayed()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Tour figure").assertIsDisplayed()
    }

    @Test fun `a way back that dies on the map forgets the bill it saved, so the next door saves the bill as it is`() {
        show()
        rule.onNodeWithText("NUMBERS").performClick()
        rule.onNodeWithText("Numbers bumped 0").performClick()
        rule.onNodeWithContentDescription("Open on the map: Numbers figure").performClick()
        rule.waitForIdle()
        doors.pinChanged("somewhere")
        rule.waitForIdle()
        assertNull(doors.origin.value)
        rule.onNodeWithText("NUMBERS").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Numbers bumped 0").performClick()
        rule.onNodeWithText("Numbers bumped 1").performClick()
        rule.onNodeWithContentDescription("Open on the map: Numbers figure").performClick()
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Numbers bumped 2").assertIsDisplayed()
    }

    @Test fun `the replay is still the map, so switching to it keeps the stub`() {
        show()
        rule.onNodeWithContentDescription("Open on the map: Tour figure").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("REPLAY").performClick()
        rule.waitForIdle()
        assertEquals(Origin("Tour figure", Tab.TOUR), doors.origin.value)
    }

    @Test fun `leaving the map by the strip drops the stub`() {
        show()
        rule.onNodeWithContentDescription("Open on the map: Tour figure").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("FACTS").performClick()
        rule.waitForIdle()
        assertNull(doors.origin.value)
    }
}
