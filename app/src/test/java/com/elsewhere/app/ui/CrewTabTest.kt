package com.elsewhere.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.crew.CrewPresence
import com.elsewhere.app.sonar.PendingCrew
import com.elsewhere.app.sonar.SonarHit
import com.elsewhere.app.sonar.SonarSheets
import com.elsewhere.app.ui.crew.CrewFixtures
import com.elsewhere.app.ui.crew.SonarSheetHost
import com.elsewhere.app.ui.crew.SonarSheetViewModel
import com.elsewhere.app.ui.nav.DoorNav
import com.elsewhere.app.ui.shell.LIVE_DOT
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.shell.RangeViewModel
import com.elsewhere.app.ui.shell.ShellViewModel
import com.elsewhere.app.ui.shell.TicketStrip
import com.elsewhere.app.ui.world.ElsewhereTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class CrewTabTest {
    @get:Rule val rule = createComposeRule()
    @After fun reset() { PendingCrew.code.value = null; PendingCrew.map.value = false }

    private fun shell(live: MutableStateFlow<Boolean>): ShellViewModel {
        val record = Fx.record(range = Fx.utc(2024, 0, 1)..Fx.utc(2024, 11, 31))
        val maps = Fakes.Maps()
        val scope = CoroutineScope(Dispatchers.Main)
        val presence = object : CrewPresence { override val live = live }
        return ShellViewModel(Fakes.Store(record), Fakes.Source(record), maps, Fakes.lookups(maps, scope), DoorNav(RangeStore(Fakes.Source(record), scope), scope), presence = presence)
    }

    @Test fun `Crew is the ninth stub and opens the Crew screen`() {
        val live = MutableStateFlow(false)
        rule.setContent {
            val shell = remember { shell(live) }
            val range = remember { RangeViewModel(RangeStore(Fakes.Source(Fx.record()), CoroutineScope(Dispatchers.Main))) }
            ElsewhereTheme { AppScaffold({}, {}, {}, shell, range, content = { t, _, _ -> Placeholder(t) }) }
        }
        rule.onAllNodesWithContentDescription(LIVE_DOT).assertCountEquals(0)
        rule.onAllNodesWithText("CREW").assertCountEquals(1)
        rule.onNodeWithText("CREW").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNode(hasText("CREW") and isSelected()).assertExists()
        rule.onAllNodesWithText("CREW").assertCountEquals(3)
    }

    @Test fun `a pair link lands on the Crew tab`() {
        val live = MutableStateFlow(false)
        rule.setContent {
            val shell = remember { shell(live) }
            val range = remember { RangeViewModel(RangeStore(Fakes.Source(Fx.record()), CoroutineScope(Dispatchers.Main))) }
            ElsewhereTheme { AppScaffold({}, {}, {}, shell, range, content = { t, _, _ -> Placeholder(t) }) }
        }
        rule.onNode(hasText("TOUR") and isSelected()).assertExists()
        PendingCrew.code.value = "ABCD-EFGH"
        rule.waitForIdle()
        rule.onNode(hasText("CREW") and isSelected()).assertExists()
    }

    @Test fun `the Crew stub wears a red dot while a friend is live`() {
        val live = MutableStateFlow(true)
        rule.setContent {
            val shell = remember { shell(live) }
            val range = remember { RangeViewModel(RangeStore(Fakes.Source(Fx.record()), CoroutineScope(Dispatchers.Main))) }
            ElsewhereTheme { AppScaffold({}, {}, {}, shell, range, content = { t, _, _ -> Placeholder(t) }) }
        }
        rule.onNodeWithContentDescription(LIVE_DOT).assertExists()
        live.value = false
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(LIVE_DOT).assertCountEquals(0)
    }

    @Test fun `a Sonar hit raises its sheet over whichever tab is open, and it stays while the app moves to the map`() {
        val live = MutableStateFlow(false)
        val sheets = object : SonarSheets {
            override val sheet = MutableStateFlow<SonarHit?>(null)
            var dismissed = 0
            override fun dismissSheet() { dismissed++; sheet.value = null }
            override fun sheetSnapped() = Unit
        }
        rule.setContent {
            val shell = remember { shell(live) }
            val range = remember { RangeViewModel(RangeStore(Fakes.Source(Fx.record()), CoroutineScope(Dispatchers.Main))) }
            val sheetVm = remember { SonarSheetViewModel(sheets, flowOf(null)) }
            ElsewhereTheme { AppScaffold({}, {}, {}, shell, range, content = { t, _, _ -> Placeholder(t) }, sheet = { SonarSheetHost(sheetVm, snap = { it() }) }) }
        }
        rule.onNodeWithText("CREW").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Marta is within a mile", ignoreCase = true).assertCountEquals(0)
        sheets.sheet.value = SonarHit(CrewFixtures.marta, "h1", CrewFixtures.NOW)
        rule.waitForIdle()
        rule.onNodeWithText("Marta is within a mile", ignoreCase = true).assertIsDisplayed()
        PendingCrew.map.value = true
        rule.waitForIdle()
        rule.onNode(hasText("MAP") and isSelected()).assertExists()
        rule.onNodeWithText("Marta is within a mile", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Dismiss", ignoreCase = true).performClick()
        rule.waitForIdle()
        assertEquals(1, sheets.dismissed)
        rule.onAllNodesWithText("Marta is within a mile", ignoreCase = true).assertCountEquals(0)
    }

    @Test fun `the strip draws a dot only on the stubs asked for`() {
        rule.setContent { ElsewhereTheme { TicketStrip(selected = Tab.TOUR, onSelect = {}, onBrand = {}, dots = setOf(Tab.CREW)) } }
        rule.onAllNodesWithContentDescription(LIVE_DOT).assertCountEquals(1)
    }
}
