package com.elsewhere.app.ui.tabs.flights

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.Airports
import com.elsewhere.app.share.LocalPrinting
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlightsShareTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val airports = Airports.fromJson(javaClass.classLoader.getResource("airports.json")!!.readText())
    private val ui by lazy { flightsUi(SampleRecord.record, SampleRecord.profile, airports, null) }

    // The globe's pulses animate for as long as the screen is up, so the clock is driven by hand rather than waited on.
    private fun show(printing: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false, LocalPrinting provides printing) { FlightsScreen(ui, ui.centre, {}, null, {}, 0, {}, scroll = false) } } }
        rule.mainClock.advanceTimeBy(500)
    }

    @Test fun `the globe hero has a save menu and shows its drag hint on screen`() {
        show(printing = false)
        rule.onNodeWithText("DRAG TO TURN · TAP A ROUTE").assertIsDisplayed()
        // Hero, From, Routes, Airports: every bill on the page can be saved now, the globe hero included.
        rule.onAllNodesWithContentDescription("Save this card").assertCountEquals(4)
    }

    @Test fun `while printing, the hero drops the hint and the save menu`() {
        show(printing = true)
        rule.onNodeWithText("DRAG TO TURN · TAP A ROUTE").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("Save this card").assertCountEquals(0)
    }
}
