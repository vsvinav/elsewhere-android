package com.elsewhere.app.ui.tabs.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.LocalOpenDoor
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TourDoorsTest {
    @get:Rule val rule = createComposeRule()
    private val ui = tourUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile, null)

    private fun show(opened: MutableList<Door>, content: @Composable () -> Unit) {
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalOpenDoor provides { opened.add(it) }, LocalMotion provides false) { content() } } }
    }

    @Test fun `the hero's stops, lede and foot are doors`() {
        val opened = ArrayList<Door>()
        show(opened) { TourHero(ui, onGo = {}) }
        rule.onNodeWithContentDescription("Open on the map: Lisbon · 7 nights").performClick()
        rule.onNodeWithContentDescription("Open on the map: ${ui.doors.distance.label}").performClick()
        rule.onNodeWithContentDescription("Open on the map: ${ui.doors.places.label}").performClick()
        assertEquals(listOf(ui.doors.stops[0], ui.doors.distance, ui.doors.places), opened)
    }

    @Test fun `the headline act, its stay and the longest trip are doors`() {
        val opened = ArrayList<Door>()
        show(opened) { HeadlineBill(ui, onGo = {}) }
        rule.onNodeWithContentDescription("Open on the map: Tokyo").performClick()
        rule.onNodeWithContentDescription("Open on the map: Longest trip · 7 nights").performClick()
        rule.onNodeWithContentDescription("Open on the map: ${ui.doors.nightsHome.label}").performClick()
        assertEquals(listOf(ui.doors.headline, ui.doors.longestTrip, ui.doors.nightsHome), opened)
    }

    @Test fun `every row of the records table and every stamp is a door`() {
        val opened = ArrayList<Door>()
        show(opened) { RecordsBill(ui) }
        rule.onNodeWithContentDescription("Open on the map: 2024").performClick()
        rule.onNodeWithContentDescription("Open on the map: ${ui.doors.farthest!!.label}").performClick()
        rule.onNodeWithContentDescription("Open on the map: ${ui.doors.longestDay!!.label}").performClick()
        assertEquals(listOf(ui.doors.years[0], ui.doors.farthest, ui.doors.longestDay), opened)
    }

    @Test fun `setlist, residency and backstage figures are doors`() {
        val opened = ArrayList<Door>()
        show(opened) { SetlistBill(ui); ResidencyBill(ui); BackstageBill(ui) }
        rule.onAllNodesWithContentDescription("Open on the map: ${ui.doors.distance.label}")[0].performClick()
        rule.onNodeWithContentDescription("Open on the map: ${ui.doors.thirdPlaces[0].label}").performClick()
        rule.onNodeWithContentDescription("Open on the map: ${ui.doors.record.label}").performClick()
        assertTrue(opened.size == 3 && opened[1] == ui.doors.thirdPlaces[0] && opened[2] == ui.doors.record)
    }
}
