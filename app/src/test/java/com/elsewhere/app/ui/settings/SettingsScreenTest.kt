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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.Fakes
import com.elsewhere.app.account.AccountState
import com.elsewhere.app.account.Session
import com.elsewhere.app.account.VaultState
import androidx.compose.material3.Text
import com.elsewhere.app.ui.account.AccountEntry
import com.elsewhere.app.ui.account.WELCOME_LINE
import com.elsewhere.app.data.Settings
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

    private val account = Fakes.Account()
    private var opened: AccountEntry? = null

    private fun show() {
        rule.setContent {
            val vm = remember { SettingsViewModel(maps, KeyVerifier(maps, Fakes.Checker()), store, llm, asker, account) }
            val ui by vm.ui.collectAsStateWithLifecycle()
            ElsewhereTheme { SettingsScreen(ui, vm, onBack = { closed++ }, sdk = false, onAccount = { opened = it }) }
        }
        rule.waitForIdle()
    }

    @Test fun `signed out, Log in or sign up opens Welcome, whose Not now comes straight back`() {
        rule.setContent {
            val vm = remember { SettingsViewModel(maps, KeyVerifier(maps, Fakes.Checker()), store, llm, asker, account) }
            ElsewhereTheme { SettingsRoute(onBack = { closed++ }, vm = vm, account = { entry, _, _ -> Text("FLOW $entry") }) }
        }
        node("LOG IN OR SIGN UP").performClick()
        rule.onNodeWithText(WELCOME_LINE).assertIsDisplayed()
        rule.onNodeWithText("NOT NOW").performClick()
        rule.waitForIdle()
        node("LOG IN OR SIGN UP").assertIsDisplayed()
        assertEquals(true, account.account.value!!.welcomed)
        node("LOG IN OR SIGN UP").performClick()
        rule.onNodeWithText("SIGN UP").performClick()
        rule.onNodeWithText("FLOW SignUp").assertIsDisplayed()
        assertEquals(0, closed)
    }

    @Test fun `signed out, the Account section offers to log in and saves the service URL`() {
        show()
        node("ACCOUNT").assertIsDisplayed()
        node("LOG IN OR SIGN UP").performClick()
        assertEquals(AccountEntry.LogIn, opened)
        rule.onNodeWithText(Settings.DEFAULT_ACCOUNT).performScrollTo().performTextReplacement("https://account.example.org")
        node("SAVE SERVICE").performClick()
        rule.waitForIdle()
        assertEquals("https://account.example.org", maps.cur.value.accountUrl)
        rule.onNodeWithText("https://account.example.org").performScrollTo().performTextClearance()
        node("SAVE SERVICE").performClick()
        rule.waitForIdle()
        assertEquals(Settings.DEFAULT_ACCOUNT, maps.cur.value.accountUrl)
    }

    @Test fun `signed in, the section shows the email, the vault's standing, and a two-tap delete`() {
        account.account.value = AccountState(session = Session("s", "acct_1", "ana@example.com"), welcomed = true)
        account.vaultState.value = VaultState.OutOfDate
        show()
        node("ana@example.com").assertIsDisplayed()
        node(VAULT_OUT_OF_DATE).assertIsDisplayed()
        node("UPDATE").performClick()
        assertEquals(AccountEntry.Update, opened)
        account.vaultState.value = VaultState.UpToDate
        rule.waitForIdle()
        node(VAULT_UP_TO_DATE).assertIsDisplayed()
        rule.onAllNodesWithText("UPDATE").assertCountEquals(0)

        node("DELETE ACCOUNT").performClick()
        node(DELETE_CAPTION).assertIsDisplayed()
        node("KEEP IT").performClick()
        assertEquals(0, account.deletes)
        node("DELETE ACCOUNT").performClick()
        node("DELETE FOR GOOD").performClick()
        rule.waitForIdle()
        assertEquals(1, account.deletes)
        node("LOG IN OR SIGN UP").assertIsDisplayed()
    }

    @Test fun `signed in before any answers, the vault has nothing to keep yet and offers no Update`() {
        account.account.value = AccountState(session = Session("s", "acct_1", "ana@example.com"), welcomed = true)
        account.vaultState.value = VaultState.None
        show()
        node(VAULT_NONE).assertIsDisplayed()
        rule.onAllNodesWithText("UPDATE").assertCountEquals(0)
    }

    @Test fun `log out drops the session`() {
        account.account.value = AccountState(session = Session("s", "acct_1", "ana@example.com"), welcomed = true)
        show()
        node("LOG OUT").performClick()
        rule.waitForIdle()
        assertEquals(1, account.logOuts)
        node("LOG IN OR SIGN UP").assertIsDisplayed()
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

    @Test fun `the Crew section saves a relay URL`() {
        show()
        node("CREW").assertIsDisplayed()
        node("RELAY URL").assertIsDisplayed()
        rule.onNodeWithText(Settings.DEFAULT_RELAY).performScrollTo().performTextReplacement("https://relay.example.org")
        node("SAVE RELAY").performClick()
        rule.waitForIdle()
        assertEquals("https://relay.example.org", maps.cur.value.relayUrl)
    }

    @Test fun `choosing a custom endpoint adds a base URL, makes the key optional and remembers it`() {
        show()
        // Two "API key" labels: Google's and the LLM's.
        rule.onAllNodesWithText("API KEY").assertCountEquals(2)
        rule.onAllNodesWithText("BASE URL").assertCountEquals(0)
        node("Ollama / custom").performClick()
        rule.waitForIdle()
        node("BASE URL").assertIsDisplayed()
        node("API KEY · OPTIONAL").assertIsDisplayed()
        rule.onAllNodesWithText("API KEY").assertCountEquals(1)
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

    @Test fun `TEST without a key asks for one instead of going out`() {
        show()
        node("TEST").performClick()
        rule.waitForIdle()
        node("Add a key first").assertIsDisplayed()
        assertEquals(0, asker.asked.size)
    }

    @Test fun `switching provider keeps the typed model`() {
        show()
        node("Ollama / custom").performClick()
        rule.waitForIdle()
        node("llama3.1").performTextInput("my-model")
        node("OpenAI").performClick()
        rule.waitForIdle()
        node("my-model").assertIsDisplayed()
        assertEquals("my-model", llm.cur.value.model)
        assertEquals(LlmProvider.OPENAI, llm.cur.value.provider)
    }

    @Test fun `saving with a blank key clears it but keeps the Map ID`() {
        maps.cur.value = maps.cur.value.copy(mapsKey = "AIzaOld", keyPlaces = true, keyTiles = true, keyCheckedAt = 5)
        show()
        rule.onAllNodesWithText("SHOW")[0].performScrollTo().performClick()
        node("AIzaOld").performTextClearance()
        node("none").performTextInput("map-7")
        node("SAVE").performClick()
        rule.waitForIdle()
        assertEquals("", maps.cur.value.mapsKey)
        assertEquals("map-7", maps.cur.value.mapId)
        assertEquals(0L, maps.cur.value.keyCheckedAt)
        node("No key saved").assertIsDisplayed()
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
