package com.elsewhere.app.ui.tabs.facts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.analytics.Fact
import com.elsewhere.app.analytics.Kind
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
}
