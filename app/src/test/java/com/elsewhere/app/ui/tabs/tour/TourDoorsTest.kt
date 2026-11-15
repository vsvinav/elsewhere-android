package com.elsewhere.app.ui.tabs.tour

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.LocalOpenDoor
import com.elsewhere.app.ui.nav.openDoor
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Assert.assertEquals
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

    @Test fun `the hero's figures, stops and foot are doors`() {
        val opened = ArrayList<Door>()
        show(opened) { TourHero(ui, onGo = {}) }
        rule.openDoor("Lisbon · 7 nights")
        rule.openDoor("${ui.doors.distance.label}")
        rule.openDoor("${ui.doors.days.label}")
        rule.openDoor("${ui.doors.countries.label}")
        assertEquals(listOf(ui.doors.stops[0], ui.doors.distance, ui.doors.days, ui.doors.countries), opened)
    }

    @Test fun `the headline act, its stay and the longest trip are doors`() {
        val opened = ArrayList<Door>()
        show(opened) { HeadlineBill(ui, onGo = {}) }
        rule.openDoor("${ui.doors.act!!.label}")
        rule.openDoor("${ui.doors.actNights!!.label}")
        rule.openDoor("Longest trip · 7 nights")
        rule.openDoor("${ui.doors.nightsHome.label}")
        assertEquals(listOf(ui.doors.act, ui.doors.actNights, ui.doors.longestTrip, ui.doors.nightsHome), opened)
    }

    @Test fun `every cell of the records table and every stamp is a door`() {
        val opened = ArrayList<Door>()
        show(opened) { RecordsBill(ui) }
        rule.openDoor("${ui.doors.years[0][0].label}")
        rule.openDoor("${ui.doors.years[0][3].label}")
        rule.openDoor("${ui.doors.act!!.label}")
        rule.openDoor("${ui.doors.longestDay!!.label}")
        assertEquals(listOf(ui.doors.years[0][0], ui.doors.years[0][3], ui.doors.act, ui.doors.longestDay), opened)
    }

    @Test fun `setlist, residency and backstage figures are doors`() {
        val opened = ArrayList<Door>()
        show(opened) { Column(Modifier.verticalScroll(rememberScrollState())) { SetlistBill(ui); ResidencyBill(ui); BackstageBill(ui) } }
        rule.openDoor("${ui.doors.distanceOnRecord.label}")
        rule.openDoor("${ui.doors.laps.label}")
        rule.openDoor("${ui.doors.flights.label}")
        rule.openDoor("${ui.doors.thirdPlaces[0].label}")
        rule.openDoor("${ui.doors.record.label}")
        assertEquals(listOf(ui.doors.distanceOnRecord, ui.doors.laps, ui.doors.flights, ui.doors.thirdPlaces[0], ui.doors.record), opened)
    }
}
