package com.elsewhere.app.ui

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.highlights.HighlightWorker
import com.elsewhere.app.highlights.PendingHighlight
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.tabs.facts.FactsTab
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
    @get:Rule val rule = createComposeRule()
    private val whole = Fx.utc(2024, 0, 1)..Fx.utc(2024, 11, 31)
    private val record = Fx.record(range = whole)

    @After fun clear() { PendingHighlight.date.value = null }

    private fun scaffold(range: RangeViewModel, content: @Composable (Tab, (Tab) -> Unit) -> Unit) {
        rule.setContent {
            val maps = Fakes.Maps()
            val shell = remember { ShellViewModel(Fakes.Store(record), Fakes.Source(record), maps, Fakes.lookups(maps, CoroutineScope(Dispatchers.Main))) }
            ElsewhereTheme { AppScaffold({}, {}, {}, shell, range, content = content) }
        }
    }

    @Test fun `opening Facts twice leaves the shared range untouched`() {
        val scope = CoroutineScope(Dispatchers.Main)
        val store = RangeStore(Fakes.Source(record), scope)
        val range = RangeViewModel(store)
        val maps = Fakes.Maps()
        val facts = FactsViewModel(store, Fakes.Store(record), Fakes.lookups(maps, scope))
        scaffold(range) { t, go -> if (t == Tab.FACTS) FactsTab(onOpen = { r, _, _ -> range.setRange(r); go(Tab.MAP) }, vm = facts) else Text("page ${t.route}") }
        rule.waitForIdle()
        val narrowed = Fx.utc(2024, 5, 1)..Fx.utc(2024, 5, 30)
        range.setRange(narrowed)
        rule.onNodeWithText("FACTS").performClick(); rule.waitForIdle()
        rule.onNodeWithText("TOUR").performClick(); rule.waitForIdle()
        rule.onNodeWithText("FACTS").performClick(); rule.waitForIdle()
        assertEquals(narrowed, range.range.value)
    }

    @Test fun `a pending highlight jumps the shell to Facts and the tab takes the date`() {
        val range = RangeViewModel(RangeStore(Fakes.Source(record), CoroutineScope(Dispatchers.Main)))
        scaffold(range) { t, _ -> Text("page ${t.route}") }
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
