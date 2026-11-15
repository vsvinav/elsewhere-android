package com.elsewhere.app.ui.tabs.facts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class FactsPassTest {
    @get:Rule val rule = createComposeRule()

    private val door = Door.Range(0L, 86_399L, "22 nights in your own bed")
    private val doorFact = Fact("beds", Kind.HABIT, "22 nights in your own bed", "Detail.", card = true, quip = "Home is where the bed is", door = door)

    @Test fun theHeadlineOnThePassOpensTheFactsDoor() {
        val opened = ArrayList<Door>()
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalOpenDoor provides { opened.add(it) }) { FactPass(doorFact, 1, 1) } } }
        rule.onNodeWithContentDescription("Open on the map: 22 nights in your own bed").performClick()
        assertEquals(listOf<Door>(door), opened)
    }
}
