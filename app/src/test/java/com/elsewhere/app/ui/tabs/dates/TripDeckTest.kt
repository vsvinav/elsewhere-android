package com.elsewhere.app.ui.tabs.dates

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.elsewhere.app.SampleRecord
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
class TripDeckTest {
    @get:Rule val rule = createComposeRule()
    // The bed strip is left off so the deck sits at the top of the screen, where a finger can reach it.
    private val ui = datesUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile).copy(nights = null)
    private val opened = ArrayList<Door>()

    private fun show() = rule.setContent {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false, LocalOpenDoor provides { opened.add(it) }) { DatesScreen(ui) } }
    }

    @Test fun dealsOneCardPerTripNewestFirstAndTheArrowsMoveTheSelection() {
        show()
        rule.onNodeWithText("1 OF 3").assertExists()
        rule.onNodeWithContentDescription("Trip card: New York").assertIsSelected()
        rule.onNodeWithContentDescription("Next trip").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("2 OF 3").assertExists()
        rule.onNodeWithContentDescription("Trip card: Tokyo").assertIsSelected()
        rule.onNodeWithContentDescription("Next trip").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("3 OF 3").assertExists()
        rule.onNodeWithContentDescription("Trip card: Lisbon").assertIsSelected()
        rule.onNodeWithContentDescription("Previous trip").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("2 OF 3").assertExists()
    }

    @Test fun aCardTicksTheStatsWhereItBeatsTheOlderCardBesideIt() {
        show()
        // Tokyo beats Lisbon on distance and farthest point; New York, the newest, beats Tokyo on nothing; Lisbon, the oldest, has nothing to beat.
        val wins = compare(ui.cards[1], ui.cards[2]).let { listOf(it.nights, it.km, it.flights, it.farthest).count { s -> s == 1 } }
        assertEquals(2, wins)
        assertEquals(0, compare(ui.cards[0], ui.cards[1]).let { listOf(it.nights, it.km, it.flights, it.farthest).count { s -> s == 1 } })
        rule.onNodeWithContentDescription("Next trip").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onAllNodesWithContentDescription("Beats the trip before").assertCountEquals(wins)
    }

    @Test fun searchNarrowsTheDeckAndThePassesAndSaysSoWhenNothingMatches() {
        show()
        rule.onNodeWithContentDescription("Search trips").performTextInput("lisbon")
        rule.onNodeWithText("1 OF 1").assertExists()
        rule.onAllNodesWithContentDescription("Open trip").assertCountEquals(1)
        rule.onNodeWithContentDescription("Search trips").performTextInput(" 2023")
        rule.onNodeWithText("No trips match").assertExists()
        rule.onAllNodesWithContentDescription("Open trip").assertCountEquals(0)
    }

    @Test fun tappingACardOpensItsPassBelow() {
        show()
        rule.onAllNodesWithContentDescription("Close trip").assertCountEquals(0)
        rule.onNodeWithContentDescription("Open trip New York").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onAllNodesWithContentDescription("Close trip").assertCountEquals(1)
        rule.onAllNodesWithContentDescription("Open trip").assertCountEquals(2)
    }

    @Test fun mapWalksThroughTheTripDoor() {
        show()
        val ny = ui.cards[0]
        rule.onNodeWithContentDescription("Open on the map: New York · 2 nights").performClick()
        assertEquals(listOf<Door>(Door.Trip(ny.tripId, ny.departed, ny.returned, "New York · 2 nights")), opened)
    }
}
