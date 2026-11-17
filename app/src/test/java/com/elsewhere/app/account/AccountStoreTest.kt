package com.elsewhere.app.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elsewhere.app.data.SecretBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class AccountStoreTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DataStore<Preferences>
    private val box = SecretBox.inMemory()
    private lateinit var store: AccountStore

    @BeforeEach fun up() {
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        prefs = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "account.preferences_pb") }
        store = AccountStore(prefs, box)
    }

    @AfterEach fun down() { scope.cancel(); dir.deleteRecursively() }

    @Test fun `a fresh store has no session, no salt, no vault and is not welcomed`() = runTest {
        val s = store.state.first()
        assertNull(s.session); assertNull(s.salt); assertEquals(0L, s.vaultAt); assertNull(s.vaultHash); assertFalse(s.welcomed)
    }

    @Test fun `the session and account round-trip and clear`() = runTest {
        store.saveSession(Session("tok", "acct_0123456789abcdef", "ada@example.org"))
        assertEquals(Session("tok", "acct_0123456789abcdef", "ada@example.org"), store.state.first().session)
        store.saveSalt("c2FsdA==")
        store.saveVault(1_700_000_000_000L, "hash")
        store.clearSession()
        val s = store.state.first()
        assertNull(s.session); assertNull(s.salt); assertEquals(0L, s.vaultAt); assertNull(s.vaultHash)
    }

    @Test fun `welcomed survives a sign-out`() = runTest {
        store.markWelcomed()
        store.saveSession(Session("tok", "id", "a@b.co"))
        store.clearSession()
        assertTrue(store.state.first().welcomed)
    }

    @Test fun `the vault stamp and salt round-trip`() = runTest {
        store.saveSalt("c2FsdA==")
        store.saveVault(42L, "h")
        val s = store.state.first()
        assertEquals("c2FsdA==", s.salt); assertEquals(42L, s.vaultAt); assertEquals("h", s.vaultHash)
    }

    @Test fun `the session token is sealed at rest and a plaintext one is read and sealed`() = runTest {
        val k = stringPreferencesKey("account.session")
        store.saveSession(Session("tok", "id", "a@b.co"))
        val raw = prefs.data.first()[k]!!
        assertNotEquals("tok", raw); assertEquals("tok", box.open(raw))
        prefs.edit { it[k] = "legacy_tok" }
        assertEquals("legacy_tok", store.state.first().session!!.session)
        assertEquals("legacy_tok", box.open(prefs.data.first()[k]!!))
    }

    @Test fun `the Drive key is sealed at rest, read back whole, and forgotten on sign-out`() = runTest {
        val k = stringPreferencesKey("account.driveKey")
        store.saveDriveKey("a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5")
        assertEquals("a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5", store.state.first().driveKey)
        assertEquals("a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5a2V5", box.open(prefs.data.first()[k]!!))
        store.saveDriveKey(null)
        assertNull(store.state.first().driveKey)
        store.saveDriveKey("x")
        store.clearSession()
        assertNull(store.state.first().driveKey)
    }
}
