package com.elsewhere.app.ui.tabs.numbers

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.DayType
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.LocalOpenDoor
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

    @Test fun `stub fields, big figures and city rows open their doors`() {
        val opened = ArrayList<Door>()
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalOpenDoor provides { opened.add(it) }, LocalMotion provides false) { NumbersScreen(ui) } } }
        // "Best month" and "Which" print the same door twice; the first of any pair will do.
        fun tap(d: Door) = rule.onAllNodesWithContentDescription("Open on the map: ${d.label}", useUnmergedTree = true)[0].performScrollTo().performClick()
        tap(ui.doors.bestMonth!!)
        tap(ui.doors.hoursAway)
        tap(ui.doors.dayTypes.getValue(DayType.HOME))
        tap(ui.doors.daysAway)
        tap(ui.doors.legs)
        tap(ui.doors.newPlaces)
        tap(ui.doors.cities[0])
        assertEquals(listOf(ui.doors.bestMonth, ui.doors.hoursAway, ui.doors.dayTypes.getValue(DayType.HOME), ui.doors.daysAway, ui.doors.legs, ui.doors.newPlaces, ui.doors.cities[0]), opened)
    }
}
