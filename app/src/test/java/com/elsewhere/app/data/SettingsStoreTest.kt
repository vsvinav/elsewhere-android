package com.elsewhere.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class SettingsStoreTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DataStore<Preferences>
    private val box = SecretBox.inMemory()
    private lateinit var store: SettingsStore

    @BeforeEach fun up() {
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        prefs = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
        store = SettingsStore(prefs, box)
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
        assertEquals("https://elsewhere-account.vinav.workers.dev", store.settings.first().accountUrl)
        store.saveSettings(store.settings.first().copy(accountUrl = " https://acct.example.org "))
        assertEquals("https://acct.example.org", store.settings.first().accountUrl)
        store.saveSettings(store.settings.first().copy(accountUrl = "   "))
        assertEquals(Settings.DEFAULT_ACCOUNT, store.settings.first().accountUrl)
    }

    @Test fun `the api keys are sealed at rest and plaintext ones are read and sealed`() = runTest {
        val maps = stringPreferencesKey("mapsKey"); val llm = stringPreferencesKey("llmKey")
        store.saveSettings(Settings(mapsKey = "AIza1")); store.saveLlm(LlmConfig(LlmProvider.OPENAI, "sk-1", "", ""))
        val p = prefs.data.first()
        assertNotEquals("AIza1", p[maps]); assertEquals("AIza1", box.open(p[maps]!!))
        assertNotEquals("sk-1", p[llm]); assertEquals("sk-1", box.open(p[llm]!!))
        prefs.edit { it[maps] = "AIza2"; it[llm] = "sk-2" }
        assertEquals("AIza2", store.settings.first().mapsKey)
        assertEquals("sk-2", store.llm.first().key)
        val q = prefs.data.first()
        assertEquals("AIza2", box.open(q[maps]!!)); assertEquals("sk-2", box.open(q[llm]!!))
    }
}
