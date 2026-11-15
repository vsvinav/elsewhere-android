package com.elsewhere.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.ask.LlmProvider
import com.elsewhere.app.ask.TEST_PROMPT
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.KeyVerifier
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val store = Fakes.Store(Fx.record()).apply { profile.value = Profile("Ana", Units.KM, emptyList(), emptyList()) }
    private val maps = Fakes.Maps()
    private val llm = Fakes.Llm()
    private val asker = Fakes.Asker()
    private var closed = 0

    private fun show() {
        rule.setContent {
            val vm = remember { SettingsViewModel(maps, KeyVerifier(maps, Fakes.Checker()), store, llm, asker) }
            val ui by vm.ui.collectAsStateWithLifecycle()
            ElsewhereTheme { SettingsScreen(ui, vm, onBack = { closed++ }, sdk = false) }
        }
        rule.waitForIdle()
    }

    /** The page scrolls, so a node is brought into view before it is read or pressed. */
    private fun node(text: String) = rule.onNodeWithText(text).performScrollTo()

    @Test fun `the three sections print under the title`() {
        show()
        node("SETTINGS").assertIsDisplayed()
        node("GOOGLE MAPS").assertIsDisplayed()
        node("ASK ELSEWHERE").assertIsDisplayed()
        node("UNITS").assertIsDisplayed()
        rule.onAllNodesWithText("DATA").assertCountEquals(0)
        node(LLM_CAPTION).assertIsDisplayed()
    }

    @Test fun `choosing a custom endpoint swaps the API key for a base URL and remembers it`() {
        show()
        node("API KEY").assertIsDisplayed()
        rule.onAllNodesWithText("BASE URL").assertCountEquals(0)
        node("Ollama / custom").performClick()
        rule.waitForIdle()
        node("BASE URL").assertIsDisplayed()
        rule.onAllNodesWithText("API KEY").assertCountEquals(0)
        node("http://localhost:11434/v1").assertIsDisplayed()
        assertEquals(LlmProvider.CUSTOM, llm.cur.value.provider)
        assertEquals("http://localhost:11434/v1", llm.cur.value.baseUrl)
    }

    @Test fun `TEST asks for OK and reports the model it reached`() {
        show()
        node("paste your key").performTextInput("sk-test")
        node("TEST").performClick()
        rule.waitForIdle()
        node("Connected · gpt-4o-mini").assertIsDisplayed()
        assertEquals(1, asker.asked.size)
        assertEquals(TEST_PROMPT, asker.asked[0].second)
        assertEquals("sk-test", asker.asked[0].first.key)
        assertEquals("sk-test", llm.cur.value.key)
    }

    @Test fun `a failed TEST prints the error`() {
        asker.reply = Result.failure(IOException("HTTP 401"))
        show()
        node("paste your key").performTextInput("bad")
        node("TEST").performClick()
        rule.waitForIdle()
        node("HTTP 401").assertIsDisplayed()
    }

    @Test fun `the units radio writes to the profile`() {
        show()
        node("km").assertIsSelected()
        node("mi").performClick()
        rule.waitForIdle()
        node("mi").assertIsSelected()
        assertEquals(Units.MI, store.saved?.units)
        node("km").performClick()
        rule.waitForIdle()
        assertEquals(Units.KM, store.saved?.units)
    }

    @Test fun `SAVE runs the key through the verifier and prints the verdict`() {
        show()
        node("AIza…").performTextInput("AIzaNew")
        node("SAVE").performClick()
        rule.waitForIdle()
        node("Key accepted · Places ✓ · Map tiles ✓").assertIsDisplayed()
        assertEquals("AIzaNew", maps.cur.value.mapsKey)
    }
}
