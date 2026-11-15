package com.elsewhere.app.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.highlights.HighlightWorker
import com.elsewhere.app.highlights.PendingHighlight
import com.elsewhere.app.ui.nav.DoorNav
import com.elsewhere.app.ui.nav.Origin
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.tabs.facts.FactsTab
import com.elsewhere.app.ui.tabs.highlights.OpenDoor
import com.elsewhere.app.ui.tabs.map.OriginTag
import com.elsewhere.app.ui.tabs.facts.FactsViewModel
import com.elsewhere.app.ui.shell.RangeViewModel
import com.elsewhere.app.ui.shell.ShellViewModel
import com.elsewhere.app.ui.world.ElsewhereTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The highlight card's door and the notification tap, through the scaffold that owns the range. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HighlightDoorFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val whole = Fx.utc(2024, 0, 1)..Fx.utc(2024, 11, 31)
    private val record = Fx.record(range = whole)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val store = RangeStore(Fakes.Source(record), scope)
    private val doors = DoorNav(store, scope)

    @After fun clear() { PendingHighlight.date.value = null }

    private fun scaffold(range: RangeViewModel, content: @Composable (Tab, (Tab) -> Unit, OpenDoor) -> Unit) {
        rule.setContent {
            val maps = Fakes.Maps()
            val shell = remember { ShellViewModel(Fakes.Store(record), Fakes.Source(record), maps, Fakes.lookups(maps, scope), doors) }
            ElsewhereTheme { AppScaffold({}, {}, {}, shell, range, content = content) }
        }
    }

    @Test fun `a highlight is a door onto its day, the map wears its stub, and Back returns to Facts`() {
        val range = RangeViewModel(store)
        val day = Fx.utc(2024, 10, 14)..Fx.utc(2024, 10, 14) + 86_399
        scaffold(range) { t, _, onOpen ->
            Text("page ${t.route}", Modifier.clickable { onOpen(day, day.first + 3600, listOf("cafe")) })
            if (t == Tab.MAP) OriginTag(doors.origin.collectAsState().value, doors::back)
        }
        rule.onNodeWithText("FACTS").performClick(); rule.waitForIdle()
        rule.onNodeWithText("page facts").performClick(); rule.waitForIdle()
        rule.onNodeWithText("page map").assertIsDisplayed()
        rule.onNodeWithText("FROM · ON THIS DAY").assertIsDisplayed()
        assertEquals(Origin("On this day", Tab.FACTS), doors.origin.value)
        assertEquals(day, store.range.value)
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("page facts").assertIsDisplayed()
        assertNull(doors.origin.value)
    }

    @Test fun `opening Facts twice leaves the shared range untouched`() {
        val range = RangeViewModel(store)
        val maps = Fakes.Maps()
        val facts = FactsViewModel(store, Fakes.Store(record), Fakes.lookups(maps, scope))
        scaffold(range) { t, _, onOpen -> if (t == Tab.FACTS) FactsTab(onOpen = onOpen, vm = facts) else Text("page ${t.route}") }
        rule.waitForIdle()
        val narrowed = Fx.utc(2024, 5, 1)..Fx.utc(2024, 5, 30)
        range.setRange(narrowed)
        rule.onNodeWithText("FACTS").performClick(); rule.waitForIdle()
        rule.onNodeWithText("TOUR").performClick(); rule.waitForIdle()
        rule.onNodeWithText("FACTS").performClick(); rule.waitForIdle()
        assertEquals(narrowed, range.range.value)
    }

    @Test fun `a pending highlight jumps the shell to Facts and the tab takes the date`() {
        val range = RangeViewModel(store)
        scaffold(range) { t, _, _ -> Text("page ${t.route}") }
        rule.onNodeWithText("page tour").assertIsDisplayed()
        PendingHighlight.date.value = "2024-11-14"
        rule.waitForIdle()
        rule.onNodeWithText("page facts").assertIsDisplayed()
    }

    @Test fun `a notification tap's intent publishes its day, in onCreate or onNewIntent alike`() {
        PendingHighlight.take(Intent().putExtra(HighlightWorker.EXTRA_OPEN, "2025-11-14"))
        assertEquals("2025-11-14", PendingHighlight.date.value)
        PendingHighlight.date.value = null
        PendingHighlight.take(Intent()); PendingHighlight.take(null)
        assertNull(PendingHighlight.date.value)
    }
}
