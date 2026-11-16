package com.elsewhere.app.ui.tabs.facts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.elsewhere.app.OnThisDay
import com.elsewhere.app.analytics.Kind
import com.elsewhere.app.analytics.todaysFact
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
    private var passport = 0

    private fun deck(page: Int = 0) = rule.setContent {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { HubDeck(ui, ui.hub!!, onOpenKind = { kinds.add(it) }, onOpenPassport = { passport++ }, page = page) } }
    }

    @Test fun dealsFiveCardsInOrderWithTheFirstInFrontAndNoOnThisDay() {
        deck()
        rule.onNodeWithContentDescription("Card: Today").assertIsSelected()
        rule.onNodeWithText("1 OF 5").assertExists()
        rule.onNodeWithContentDescription("Card 1 of 5").assertIsSelected()
        rule.onAllNodesWithContentDescription("Card: On this day").assertCountEquals(0)
        rule.onNodeWithContentDescription("Next card").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("2 OF 5").assertExists()
        rule.onNodeWithContentDescription("Card: Streaks").assertIsSelected()
        rule.onNodeWithContentDescription("Card 5 of 5").performClick()
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

    @Test fun streaksPrintTheRunsAndOpenTheRecords() {
        deck(page = 1)
        rule.onNodeWithText("Longest away streak".uppercase()).assertExists()
        rule.onNodeWithText("7").assertExists()
        rule.onNodeWithText("Current away streak".uppercase()).assertExists()
        rule.onNodeWithText("nights and counting").assertExists()
        rule.onNodeWithContentDescription("Open record facts").performClick()
        assertEquals(listOf(Kind.RECORD), kinds)
    }

    @Test fun thePassportCardCarriesTheCountAndOpensTheBooklet() {
        deck(page = 2)
        rule.onNodeWithText("Stamped".uppercase()).assertExists()
        rule.onNodeWithText("${ui.passport!!.earnedIds.size} / ${ui.passport.total}").assertExists()
        rule.onNodeWithContentDescription("Open passport").performClick()
        assertEquals(1, passport)
    }

    @Test fun compareStartsOnTheLatestTwoYearsAndAChipCyclesPastTheOther() {
        deck(page = 3)
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
        deck(page = 4)
        val p = ui.hub!!.peak!!
        rule.onNodeWithText("Busiest: ", substring = true).assertTextContains("${"%02d".format(p.h)}:00", substring = true)
        rule.onNodeWithContentDescription("Heat grid of hours away from home by weekday and hour").assertExists()
        rule.onNodeWithContentDescription("Open habit facts").performClick()
        assertEquals(listOf(Kind.HABIT), kinds)
    }

    @Test fun theScreenPrintsOnThisDayAboveTheHeroThenTheDeckUnderPlayThenEveryPass() {
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { FactsScreen(ui, scroll = false) } } }
        val day = rule.onNodeWithContentDescription("On this day").fetchSemanticsNode().positionInRoot.y
        val hero = rule.onAllNodesWithText("FUN").onFirst().fetchSemanticsNode().positionInRoot.y
        val play = rule.onNodeWithText("PLAY").fetchSemanticsNode().positionInRoot.y
        val deck = rule.onNodeWithContentDescription("Play deck").fetchSemanticsNode().positionInRoot.y
        val list = rule.onNodeWithContentDescription("Open on the map: ${ui.rest.first().headline}").fetchSemanticsNode().positionInRoot.y
        assertTrue("$day < $hero", day < hero)
        assertTrue("$hero < $play", hero < play)
        assertTrue("$play <= $deck", play <= deck)
        assertTrue("$deck < $list", deck < list)
        rule.onAllNodesWithContentDescription("All facts").assertCountEquals(0)
        rule.onNodeWithContentDescription("Show only ${KIND_LABEL.getValue(ui.kinds.first())}").assertExists()
    }

    @Test fun openOnTodaysFactFiltersTheListToItsKind() {
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { FactsScreen(ui) } } }
        val pick = todaysFact(ui.all, "2025-11-15")!!
        val other = ui.rest.first { it.kind != pick.kind }
        val pass = { f: com.elsewhere.app.analytics.Fact -> "Open on the map: ${f.headline}" }
        rule.onNodeWithContentDescription(pass(other)).assertExists()
        // The deck sits below the fold here; fire its OPEN action rather than aiming a tap through the scroll.
        rule.onNodeWithContentDescription("Open ${KIND_LABEL.getValue(pick.kind).lowercase()} facts").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithContentDescription("Show only ${KIND_LABEL.getValue(pick.kind)}").performScrollTo().assertIsOn()
        rule.onAllNodesWithContentDescription(pass(other)).assertCountEquals(0)
        for (f in ui.rest.filter { it.kind == pick.kind }) rule.onNodeWithContentDescription(pass(f)).assertExists()
    }
}
