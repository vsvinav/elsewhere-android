package com.elsewhere.app.ui.tabs.numbers

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.DayType
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
class NumbersDoorsTest {
    @get:Rule val rule = createComposeRule()
    private val ui = numbersUi(SampleRecord.record, SampleRecord.profile)

    private fun show(opened: MutableList<Door>) {
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalOpenDoor provides { opened.add(it) }, LocalMotion provides false) { NumbersScreen(ui) } } }
    }

    @Test fun `stub fields, big figures, chart columns and city cells open their doors`() {
        val opened = ArrayList<Door>()
        show(opened)
        val d = ui.doors
        for (door in listOf(d.bestMonth!!, d.which!!, d.monthColumns[3], d.hoursAway, d.dayTypes.getValue(DayType.HOME), d.daysAway, d.legs, d.newPlaces, d.exploreColumns[0], d.cityCount, d.cities[0].city, d.cities[0].visits, d.cities[0].last)) rule.openDoor(door.label)
        assertEquals(listOf(d.bestMonth, d.which, d.monthColumns[3], d.hoursAway, d.dayTypes.getValue(DayType.HOME), d.daysAway, d.legs, d.newPlaces, d.exploreColumns[0], d.cityCount, d.cities[0].city, d.cities[0].visits, d.cities[0].last), opened)
    }

    @Test fun `peak hour and busiest weekday are figures, not doors`() {
        show(ArrayList())
        rule.onNodeWithText("Peak hour", ignoreCase = true, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithContentDescription("Open on the map: Peak hour", substring = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Open on the map: Busiest day", substring = true).assertCountEquals(0)
    }
}
