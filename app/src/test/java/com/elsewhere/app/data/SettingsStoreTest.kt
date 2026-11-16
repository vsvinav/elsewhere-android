package com.elsewhere.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.elsewhere.app.ask.LlmConfig
import com.elsewhere.app.ask.LlmProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class SettingsStoreTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: SettingsStore

    @BeforeEach fun up() {
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        store = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
    }

    @AfterEach fun down() { scope.cancel(); dir.deleteRecursively() }

    @Test fun `missing prefs read as the OpenAI defaults`() = runTest {
        assertEquals(LlmConfig(LlmProvider.OPENAI, "", "", ""), store.llm.first())
    }

    @Test fun `the llm config round-trips`() = runTest {
        val c = LlmConfig(LlmProvider.CUSTOM, key = "k", model = "llama3.1:8b", baseUrl = "http://box:11434/v1")
        store.saveLlm(c)
        assertEquals(c, store.llm.first())
        store.saveLlm(c.copy(provider = LlmProvider.GEMINI, baseUrl = ""))
        assertEquals(LlmProvider.GEMINI, store.llm.first().provider)
        assertEquals("", store.llm.first().baseUrl)
    }

    @Test fun `the account service URL defaults and round-trips trimmed`() = runTest {
        assertEquals("https://account.elsewhere.app", store.settings.first().accountUrl)
        store.saveSettings(store.settings.first().copy(accountUrl = " https://acct.example.org "))
        assertEquals("https://acct.example.org", store.settings.first().accountUrl)
        store.saveSettings(store.settings.first().copy(accountUrl = "   "))
        assertEquals(Settings.DEFAULT_ACCOUNT, store.settings.first().accountUrl)
    }
}
