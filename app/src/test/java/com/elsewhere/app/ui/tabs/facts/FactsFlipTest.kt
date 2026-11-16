package com.elsewhere.app.ui.tabs.facts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.analytics.Fact
import com.elsewhere.app.analytics.Kind
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.LocalOpenDoor
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertEquals
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FactsFlipTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tappingTheCardTurnsItOverToTheLongVersion() {
        val fact = Fact("beds", Kind.HABIT, "22 nights in your own bed", "Most nights of the year ended at home, which is the point of a home.", card = true, quip = "Home is where the bed is")
        rule.setContent { ElsewhereTheme { FlipCard(fact) } }
        rule.onNodeWithText("HOME IS WHERE THE BED IS").assertIsDisplayed()
        rule.onNodeWithText("Most nights of the year ended at home, which is the point of a home.").assertDoesNotExist()
        rule.onNodeWithContentDescription("Fact. Tap to turn over").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Most nights of the year ended at home, which is the point of a home.").assertIsDisplayed()
        rule.onNodeWithText("22 nights in your own bed").assertIsDisplayed()
    }

    private val door = Door.Range(0L, 86_399L, "22 nights in your own bed")
    private val doorFact = Fact("beds", Kind.HABIT, "22 nights in your own bed", "Detail.", card = true, quip = "Home is where the bed is", door = door)

    @Test fun theHeadlineOnTheBackOfTheCardTurnsItBackOverInsteadOfOpeningADoor() {
        val opened = ArrayList<Door>()
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalOpenDoor provides { opened.add(it) }) { FlipCard(doorFact) } } }
        rule.onNodeWithContentDescription("Fact. Tap to turn over").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithContentDescription("Open on the map: 22 nights in your own bed").assertDoesNotExist()
        rule.onNodeWithText("22 nights in your own bed").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithContentDescription("Fact. Tap to turn over").assertExists()
        assertEquals(emptyList<Door>(), opened)
    }

    @Test fun theHeadlineOnThePassOpensTheFactsDoor() {
        val opened = ArrayList<Door>()
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalOpenDoor provides { opened.add(it) }) { FactPass(doorFact, 1, 1) } } }
        rule.onNodeWithContentDescription("Open on the map: 22 nights in your own bed").performClick()
        assertEquals(listOf<Door>(door), opened)
    }
}
