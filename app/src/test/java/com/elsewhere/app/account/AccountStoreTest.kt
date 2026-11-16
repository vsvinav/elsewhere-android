package com.elsewhere.app.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class AccountStoreTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: AccountStore

    @BeforeEach fun up() {
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        store = AccountStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "account.preferences_pb") })
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
}
