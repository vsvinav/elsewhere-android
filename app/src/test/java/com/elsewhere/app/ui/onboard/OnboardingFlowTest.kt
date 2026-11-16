package com.elsewhere.app.ui.onboard

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.KeyVerifier
import com.elsewhere.app.ui.record.Perm
import com.elsewhere.app.ui.record.PermRow
import com.elsewhere.app.ui.record.PermState
import com.elsewhere.app.ui.record.RecordActions
import com.elsewhere.app.ui.record.RecordUi
import com.elsewhere.app.ui.record.RecordStepScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
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

    private val boston = Candidate("b", 42.36, -71.06, "Boston", "US", "The flat", null, 40.0, 0.8, listOf("b"))
    private val austin = Candidate("a", 30.27, -97.74, "Austin", "US", "Austin", null, 15.0, 0.3, listOf("a"))

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

    @Test fun `a card still being named prints its city with an ellipsis, only while asking`() {
        var asking by mutableStateOf(true)
        rule.setContent {
            ElsewhereTheme {
                CompositionLocalProvider(LocalMotion provides false) {
                    QuestionsScreen(OnboardState(step = Step.Home, sleep = listOf(boston, austin.copy(named = false)), asking = asking), Actions(), onCancel = null)
                }
            }
        }
        rule.onNode(hasText("AUSTIN…")).assertExists()
        rule.onNode(hasText("THE FLAT…")).assertDoesNotExist() // already a proper name
        asking = false
        rule.waitForIdle()
        rule.onNode(hasText("AUSTIN…")).assertDoesNotExist()
        rule.onNode(hasText("AUSTIN")).assertExists()
    }

    @Test fun `the phone's position prints as a guessed card, and with no card at all the page says what to do`() {
        var sleep by mutableStateOf(listOf(Candidate("here", 51.5, -0.12, null, null, "Strand, Covent Garden", null, 0.0, 0.0, listOf("here"), here = true)))
        rule.setContent { ElsewhereTheme { QuestionsScreen(OnboardState(step = Step.Home, sleep = sleep), Actions(), onCancel = null) } }
        rule.onNodeWithText("STRAND, COVENT GARDEN").assertIsDisplayed()
        rule.onNodeWithText("Your phone's position right now").assertIsDisplayed()
        rule.onNodeWithText("GUESSED").assertIsDisplayed()
        sleep = emptyList()
        rule.waitForIdle()
        rule.onNodeWithText("Start recording or add an export, then come back.").assertIsDisplayed()
    }

    private fun rows(vararg states: PermState) = Perm.entries.zip(states).map { (p, s) -> PermRow(p, s) }
    private fun recordUi(fine: PermState) = RecordUi(rows(fine, PermState.LOCKED, PermState.ASK, PermState.ASK), false, Recording(), null, true, 1_800_000_000L)

    @Test fun `Start recording waits on location, then the export can be skipped for now, on to the key`() {
        val store = Fakes.Store(Fx.record()); val maps = Fakes.Maps(); val control = Fakes.Control()
        val vm = OnboardViewModel(store, maps, KeyVerifier(maps, Fakes.Checker()), Fakes.Namer(), Fakes.Geo(), control)
        var fine by mutableStateOf(PermState.ASK)
        rule.setContent {
            ElsewhereTheme {
                OnboardingGraph(Step.Record, onClose = null, vm = vm, record = { start, import -> RecordStepScreen(recordUi(fine), RecordActions(onStart = start), onImport = import) })
            }
        }
        rule.onNodeWithText("ELSEWHERE RECORDS AS YOU LIVE.").assertIsDisplayed()
        rule.onNodeWithText("START RECORDING").performScrollTo().assertIsNotEnabled()
        fine = PermState.GRANTED
        rule.waitForIdle()
        rule.onNodeWithText("START RECORDING").performScrollTo().assertIsEnabled().performClick()
        rule.waitForIdle()
        assertTrue(control.recording.value.enabled)
        rule.onNodeWithText("ADD YOUR GOOGLE TIMELINE EXPORT").assertIsDisplayed()
        rule.onNodeWithText("SKIP FOR NOW").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(Step.Key, vm.state.value.step)
    }

    @Test fun `with nothing recording the export has no skip, and the other link gets there without recording`() {
        val store = Fakes.Store(Fx.record()); val maps = Fakes.Maps(); val control = Fakes.Control()
        val vm = OnboardViewModel(store, maps, KeyVerifier(maps, Fakes.Checker()), Fakes.Namer(), Fakes.Geo(), control)
        rule.setContent {
            ElsewhereTheme {
                OnboardingGraph(Step.Record, onClose = null, vm = vm, record = { start, import -> RecordStepScreen(recordUi(PermState.ASK), RecordActions(onStart = start), onImport = import) })
            }
        }
        rule.onNodeWithText("I HAVE A GOOGLE TIMELINE EXPORT INSTEAD →").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("ADD YOUR GOOGLE TIMELINE EXPORT").assertIsDisplayed()
        rule.onNodeWithText("SKIP FOR NOW").assertDoesNotExist()
        assertEquals(false, control.recording.value.enabled)
    }
}
