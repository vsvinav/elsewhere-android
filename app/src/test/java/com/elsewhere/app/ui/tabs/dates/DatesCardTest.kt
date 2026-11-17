package com.elsewhere.app.ui.tabs.dates

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.data.Activity
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
class DatesCardTest {
    @get:Rule val rule = createComposeRule()
    private val base = SampleRecord.record
    private val ny = base.trips.maxBy { it.start }
    private val confirmed = ArrayList<Pair<Int, String>>()

    private fun leg(id: Int, p: Double, recorded: Boolean): Activity =
        Fx.act(ny.start + 5 * Fx.H, ny.start + 6 * Fx.H, "IN_PASSENGER_VEHICLE", 15_000.0).copy(id = id, p = p, recorded = recorded)

    private fun show(vararg legs: Activity) {
        val ui = datesUi(base.copy(activities = base.activities + legs), base, SampleRecord.profile)
        val t = ui.trips.first { it.trip.start == ny.start }
        rule.setContent {
            ElsewhereTheme {
                CompositionLocalProvider(LocalMotion provides false) {
                    TripPass(t, ui.units, open = true, detail = tripDetail(ui.data, ui.strip, t.trip), onConfirm = { id, m -> confirmed.add(id to m) }) {}
                }
            }
        }
    }

    @Test fun aGuessedLegAsksHowYouGotThereAndTakesTheAnswer() {
        show(leg(41, 0.6, recorded = true))
        rule.onNodeWithText("How did you get there?").assertExists()
        for (c in listOf("Car", "Bus", "Train", "Metro", "Other")) rule.onNodeWithContentDescription(c).assertExists()
        rule.onNodeWithContentDescription("Train").performClick()
        assertEquals(listOf(41 to "IN_TRAIN"), confirmed)
        rule.onAllNodesWithText("How did you get there?").assertCountEquals(0)
    }

    @Test fun aLegFromAnExportIsNotAskedAbout() {
        show(leg(42, 0.6, recorded = false), leg(43, 0.9, recorded = true))
        rule.onAllNodesWithText("How did you get there?").assertCountEquals(0)
    }
}
