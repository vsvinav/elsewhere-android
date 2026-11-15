package com.elsewhere.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Visit
import com.elsewhere.app.ui.onboard.OnboardViewModel
import com.elsewhere.app.ui.onboard.OnboardingGraph
import com.elsewhere.app.ui.shell.RangeViewModel
import com.elsewhere.app.ui.shell.ShellViewModel
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RootFlowTest {
    @get:Rule val rule = createComposeRule()

    private val start = Fx.utc(2024, 0, 1)
    private val record = Fx.record(
        visits = (0 until 40).map { d -> Fx.visit(start + d * Fx.D + 20 * Fx.H, start + (d + 1) * Fx.D + 8 * Fx.H, "boston", lat = 42.36, lng = -71.06) },
        places = mapOf("boston" to Fx.place(city = "Boston", cc = "US", lat = 42.36, lng = -71.06, name = "The flat")),
        range = start..Fx.utc(2024, 11, 31),
    )

    @Test fun `through the door, into the app, and back in through Change answers`() {
        val store = Fakes.Store(record); val maps = Fakes.Maps()
        // One onboarding view model for the whole activity, as Hilt scopes it.
        val onboard = OnboardViewModel(store, maps)
        rule.setContent {
            val root = remember { RootViewModel(store, Fakes.Land) }
            val shell = remember { ShellViewModel(store, maps) }
            val range = remember { RangeViewModel(Fakes.Source(record)) }
            ElsewhereTheme {
                Root(
                    vm = root,
                    door = { step, close -> OnboardingGraph(step, close, onboard) },
                    app = { answers, key -> AppScaffold(answers, key, shell, range) },
                )
            }
        }
        rule.onNodeWithText("TAP TO CHOOSE YOUR TIMELINE.JSON").assertIsDisplayed()
        onboard.import({ ByteArrayInputStream("{}".toByteArray()) }, "Timeline.json")
        rule.waitForIdle()

        rule.onNodeWithText("SKIP").performClick()                       // key
        rule.onNodeWithText("WHOSE RECORD IS THIS?").assertIsDisplayed()
        rule.onNodeWithText("NEXT").performClick()                       // name
        rule.onNode(isSelectable() and hasText("THE FLAT", substring = true)).performClick()
        rule.onNodeWithText("NEXT").performClick()                       // home
        rule.onNodeWithText("NEXT").performClick()                       // moves
        rule.onNodeWithText("SKIP").performClick()                       // work
        rule.onNodeWithText("PRINT MY RECORD").performClick()            // units
        rule.waitForIdle()

        rule.onNodeWithText("ELSEWHERE · YOUR RECORD").assertIsDisplayed()
        rule.onNodeWithContentDescription("Menu").performClick()
        rule.onNodeWithText("CHANGE ANSWERS").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("WHOSE RECORD IS THIS?").assertIsDisplayed()
    }
}
