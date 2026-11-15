package com.elsewhere.app.ui.tabs.facts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.elsewhere.app.OnThisDay
import com.elsewhere.app.analytics.Kind
import com.elsewhere.app.analytics.todaysFact
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

/** Twin of web/src/tabs/Facts/HubDeck.test.tsx: same fixture, same cards, same copy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HubDeckTest {
    @get:Rule val rule = createComposeRule()
    private val data = OnThisDay.data()
    private val ui = factsUi(data, data, null, today = "2025-11-15")
    private val kinds = ArrayList<Kind>()
    private val opened = ArrayList<Pair<LongRange, List<String>>>()
    private var passport = 0

    private fun deck(page: Int = 0) = rule.setContent {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { HubDeck(ui, ui.hub!!, onOpen = { r, _, ids -> opened.add(r to ids) }, onOpenKind = { kinds.add(it) }, onOpenPassport = { passport++ }, page = page) } }
    }

    @Test fun aRecordUnderAYearOldShowsItsFarthestDayWithoutClaimingItHappenedOnThisDay() {
        val short = data.copy(meta = data.meta.copy(range = OnThisDay.at(2023, 11, 11, 8)..OnThisDay.at(2024, 5, 1)))
        val shortUi = factsUi(short, short, null, today = "2024-04-10")
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { HubDeck(shortUi, shortUi.hub!!, onOpen = { _, _, _ -> }, onOpenKind = {}, onOpenPassport = {}, page = 1) } } }
        rule.onNodeWithText("landed in Paris", substring = true).assertExists()
        rule.onNodeWithText("YOUR RECORD STARTS 11 NOVEMBER", substring = true).assertExists()
        rule.onAllNodesWithText("ON THIS DAY, EARLIER THIS YEAR", substring = true).assertCountEquals(0)
    }

    @Test fun dealsSixCardsInOrderWithTheFirstInFront() {
        deck()
        rule.onNodeWithContentDescription("Card: Today").assertIsSelected()
        rule.onNodeWithText("1 OF 6").assertExists()
        rule.onNodeWithContentDescription("Card 1 of 6").assertIsSelected()
        rule.onNodeWithContentDescription("Next card").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("2 OF 6").assertExists()
        rule.onNodeWithContentDescription("Card: On this day").assertIsSelected()
        rule.onNodeWithContentDescription("Card 6 of 6").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithContentDescription("Card: Rhythm").assertIsSelected()
        rule.onNodeWithContentDescription("Previous card").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithContentDescription("Card: Compare").assertIsSelected()
    }

    @Test fun todaysFactIsTheDaysPickAndOpenAsksForItsKind() {
        deck()
        val pick = todaysFact(ui.all, "2025-11-15")!!
        rule.onNodeWithText(pick.headline.uppercase()).assertIsDisplayed()
        rule.onNodeWithText(pick.detail).assertExists()
        rule.onNodeWithContentDescription("Open ${KIND_LABEL.getValue(pick.kind).lowercase()} facts").performClick()
        assertEquals(listOf(pick.kind), kinds)
    }

    @Test fun onThisDayNamesTheCityAndOpensItsDay() {
        deck(page = 1)
        rule.onNodeWithText("PARIS").assertIsDisplayed()
        rule.onNodeWithText("15 Nov 2022 — you landed in Paris.").assertExists()
        rule.onNodeWithContentDescription("Open on the map").performClick()
        assertEquals(1, opened.size)
        assertTrue(opened[0].first.first >= OnThisDay.at(2022, 11, 14) && opened[0].first.last <= OnThisDay.at(2022, 11, 18))
    }

    @Test fun streaksPrintTheRunsAndOpenTheRecords() {
        deck(page = 2)
        rule.onNodeWithText("Longest away streak".uppercase()).assertExists()
        rule.onNodeWithText("7").assertExists()
        rule.onNodeWithText("Current away streak".uppercase()).assertExists()
        rule.onNodeWithText("nights and counting").assertExists()
        rule.onNodeWithContentDescription("Open record facts").performClick()
        assertEquals(listOf(Kind.RECORD), kinds)
    }

    @Test fun thePassportCardCarriesTheCountAndOpensTheBooklet() {
        deck(page = 3)
        rule.onNodeWithText("Stamped".uppercase()).assertExists()
        rule.onNodeWithText("${ui.passport!!.earnedIds.size} / ${ui.passport.total}").assertExists()
        rule.onNodeWithContentDescription("Open passport").performClick()
        assertEquals(1, passport)
    }

    @Test fun compareStartsOnTheLatestTwoYearsAndAChipCyclesPastTheOther() {
        deck(page = 4)
        rule.onNodeWithContentDescription("Compare year 2024").assertExists()
        rule.onNodeWithContentDescription("Compare year 2025").assertExists()
        rule.onNodeWithContentDescription("Compare year 2024").performClick()
        rule.onNodeWithContentDescription("Compare year 2022").assertExists()
        rule.onNodeWithContentDescription("Compare year 2025").performClick()
        rule.onNodeWithContentDescription("Compare year 2023").assertExists()
        rule.onNodeWithContentDescription("Nights away: 5 against 8").assertExists()
        rule.onNodeWithContentDescription("Open scale facts").performClick()
        assertEquals(listOf(Kind.SCALE), kinds)
    }

    @Test fun rhythmNamesTheBusiestHourOverTheGridAndOpensTheHabits() {
        deck(page = 5)
        val p = ui.hub!!.peak!!
        rule.onNodeWithText("Busiest: ", substring = true).assertTextContains("${"%02d".format(p.h)}:00", substring = true)
        rule.onNodeWithContentDescription("Heat grid of hours away from home by weekday and hour").assertExists()
        rule.onNodeWithContentDescription("Open habit facts").performClick()
        assertEquals(listOf(Kind.HABIT), kinds)
    }

    @Test fun aDayWithNothingOnRecordPrintsAQuietCardWithoutADoor() {
        val quiet = data.copy(visits = data.visits.filter { it.placeId == "sydney-hotel" }, daily = data.daily.take(1))
        val qui = factsUi(quiet, quiet, null, today = "2025-11-15")
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { HubDeck(qui, qui.hub!!, page = 1) } } }
        rule.onNodeWithText("QUIET DAY").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Open on the map").assertCountEquals(0)
    }

    @Test fun theScreenKeepsEveryFactBehindAllFactsUntilAskedAndOpenUnfoldsItToTheKind() {
        val opened = ArrayList<Door>()
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false, LocalOpenDoor provides { opened.add(it) }) { FactsScreen(ui) } } }
        rule.onNode(hasContentDescription("All facts") and hasStateDescription("Collapsed")).assertExists()
        rule.onAllNodesWithContentDescription("Show only Scale").assertCountEquals(0)
        val pick = todaysFact(ui.all, "2025-11-15")!!
        rule.onNodeWithContentDescription("Open ${KIND_LABEL.getValue(pick.kind).lowercase()} facts").performClick()
        rule.onNode(hasContentDescription("All facts") and hasStateDescription("Expanded")).assertExists()
        rule.onNodeWithContentDescription("Show only ${KIND_LABEL.getValue(pick.kind)}").performScrollTo().assertIsOn()
        val shown = ui.all.count { it.kind == pick.kind }
        rule.onNodeWithText("1 of $shown").performScrollTo().assertExists()
    }
}
