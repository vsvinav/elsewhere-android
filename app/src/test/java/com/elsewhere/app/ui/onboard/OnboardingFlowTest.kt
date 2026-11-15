package com.elsewhere.app.ui.onboard

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class OnboardingFlowTest {
    @get:Rule val rule = createComposeRule()

    private val boston = Candidate("b", 42.36, -71.06, "Boston", "US", "The flat", null, 40.0, 0.8, 1)
    private val austin = Candidate("a", 30.27, -97.74, "Austin", "US", "Austin", null, 15.0, 0.3, 1)

    /** The screen is stateless; this stands in for the view model and stores the pick. */
    private class Actions : OnboardActions {
        var homes: List<Home> = emptyList()
        override fun setName(s: String) {}
        override fun pickHome(c: Candidate) { homes = listOf(Home(c.lat, c.lng, c.city ?: "", c.cc ?: "", 0, null, c.label)) }
        override fun addHome(c: Candidate) {}
        override fun setHomeFrom(i: Int, m: YearMonth) {}
        override fun setHomeTo(i: Int, m: YearMonth?) {}
        override fun removeHome(i: Int) {}
        override fun toggleWork(c: Candidate) {}
        override fun setWorkFrom(i: Int, m: YearMonth) {}
        override fun setWorkTo(i: Int, m: YearMonth?) {}
        override fun removeWork(i: Int) {}
        override fun setUnits(u: Units) {}
        override fun next() {}
        override fun back() {}
        override fun skip() {}
        override fun finish() {}
    }

    @Test fun `tapping a candidate marks it as the home`() {
        val actions = Actions()
        rule.setContent {
            var homes by remember { mutableStateOf(actions.homes) }
            val wired = object : OnboardActions by actions {
                override fun pickHome(c: Candidate) { actions.pickHome(c); homes = actions.homes }
            }
            ElsewhereTheme {
                QuestionsScreen(OnboardState(step = Step.Home, range = Fx.utc(2024, 0, 1)..Fx.utc(2024, 11, 31), sleep = listOf(boston, austin), homes = homes), wired, onCancel = null)
            }
        }
        val card = { label: String -> rule.onNode(isSelectable() and hasText(label, substring = true)) }
        card("THE FLAT").assertIsNotSelected()
        card("THE FLAT").performClick()
        card("THE FLAT").assertIsSelected()
        card("AUSTIN").assertIsNotSelected()
    }
}
