package com.elsewhere.app.account

import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.crew.Crypto
import com.elsewhere.app.crew.DeviceKeys
import com.elsewhere.app.crew.Identity
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Units
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryTest {
    private val profile = Profile("Ada", Units.KM, listOf(Home(51.5, -0.12, "London", "GB", 0, null)), emptyList())
    private val info = AccountInfo("acct_0123456789abcdef", "ada@example.org", 1_700_000_000_000L, hasVault = false)

    class FakeApi : AccountApi {
        val calls = mutableListOf<String>()
        var vault: VaultBody? = null
        var hasVault = false
        var fail: Throwable? = null
        var putFail: Throwable? = null
        var session = "s3cret"
        private fun check() { fail?.let { throw it } }
        override suspend fun start(email: String) { calls += "start $email"; check() }
        override suspend fun finish(email: String, code: String): Login { calls += "finish $email $code"; check(); return Login(session, AccountInfo("acct_0123456789abcdef", email, 1L, hasVault)) }
        override suspend fun google(idToken: String): Login { calls += "google $idToken"; check(); return Login(session, AccountInfo("acct_0123456789abcdef", "ada@example.org", 1L, hasVault)) }
        override suspend fun me(session: String): AccountInfo { calls += "me"; check(); return AccountInfo("acct_0123456789abcdef", "ada@example.org", 1L, hasVault) }
        override suspend fun logout(session: String) { calls += "logout $session"; check() }
        override suspend fun putVault(session: String, body: VaultBody) { calls += "put"; putFail?.let { throw it }; check(); vault = body; hasVault = true }
        override suspend fun getVault(session: String): VaultBody? { calls += "get"; check(); return vault }
        override suspend fun deleteMe(session: String) { calls += "deleteMe $session"; check() }
    }

    class FakeStorage(initial: AccountState = AccountState(), var installId: String = "install00000001") : AccountStorage {
        override val state = MutableStateFlow(initial)
        override suspend fun installId(): String = installId
        override suspend fun saveSession(s: Session) = state.update { it.copy(session = s) }
        override suspend fun saveSalt(salt: String?) = state.update { it.copy(salt = salt) }
        override suspend fun saveVault(at: Long, hash: String?) = state.update { it.copy(vaultAt = at, vaultHash = hash) }
        override suspend fun saveDriveKey(b64: String?) = state.update { it.copy(driveKey = b64) }
        override suspend fun markWelcomed() = state.update { it.copy(welcomed = true) }
        /** DataStore really suspends here; a cancelled caller must still get the session off disk. */
        override suspend fun clearSession() { yield(); state.update { it.copy(session = null, salt = null, vaultAt = 0L, vaultHash = null, driveKey = null) } }
    }

    class FakeIdentity(var keys: DeviceKeys = DeviceKeys.fromSeeds(ByteArray(32) { 1 }, ByteArray(32) { 2 })) : Identity {
        var replaced = 0
        override suspend fun keys() = keys
        override suspend fun replaceKeys(k: DeviceKeys) { keys = k; replaced++ }
    }

    private class World(val scope: TestScope, storage: AccountState = AccountState(), withProfile: Boolean = true, profile: Profile) {
        val api = FakeApi()
        val store = FakeStorage(storage)
        val record = Fakes.Store(Fx.record()).apply { if (withProfile) this.profile.value = profile }
        val maps = Fakes.Maps()
        val identity = FakeIdentity()
        var now = 1_000L
        val repo = AccountRepository(api, store, record, maps, identity, scope.backgroundScope, { now }, crypto = StandardTestDispatcher(scope.testScheduler))
    }

    private fun TestScope.world(storage: AccountState = AccountState(), withProfile: Boolean = true) = World(this, storage, withProfile, profile)

    // Background coroutines only move with the clock, so the derived state is read a millisecond on.
    private fun World.vault(): VaultState { scope.advanceTimeBy(1); return repo.vaultState.value }

    private fun contentsOf(w: World, key: ByteArray): VaultContents = VaultContents.decode(Vault.open(key, w.api.vault!!))

    @Test fun `finishing stores the session, setting a passphrase uploads the vault once the debounce has run`() = runTest {
        val w = world()
        assertEquals(info.copy(created = 1L), w.repo.finish("Ada@Example.org", "123456"))
        assertEquals(Session("s3cret", "acct_0123456789abcdef", "ada@example.org"), w.store.state.value.session)
        w.repo.setPassphrase("correct horse battery staple")
        assertNotNull(w.store.state.value.salt)
        assertEquals(0, w.api.calls.count { it == "put" })
        advanceTimeBy(2_001)
        assertEquals(1, w.api.calls.count { it == "put" })
        val key = Vault.deriveKey("correct horse battery staple", Vault.unb64(w.store.state.value.salt!!))
        val c = contentsOf(w, key)
        assertEquals(Crypto.hex(w.identity.keys.signPriv), c.signPriv)
        assertEquals(Crypto.hex(w.identity.keys.kxPriv), c.kxPriv)
        assertEquals(profile, c.profile)
        assertEquals(VaultSettings("km", "https://elsewhere-relay.vinav.workers.dev", "https://elsewhere-account.vinav.workers.dev"), c.settings)
        assertEquals(1_000L, w.store.state.value.vaultAt)
        assertEquals(VaultState.UpToDate, w.vault())
    }

    @Test fun `quick successive changes upload once, two seconds after the last`() = runTest {
        val w = world()
        w.repo.finish("ada@example.org", "123456"); w.repo.setPassphrase("correct horse battery staple")
        advanceTimeBy(2_001)
        assertEquals(1, w.api.calls.count { it == "put" })
        w.record.saveProfile(profile.copy(name = "Ada L."))
        advanceTimeBy(1_000)
        w.maps.saveSettings(w.maps.cur.value.copy(relayUrl = "https://relay.example.org"))
        advanceTimeBy(1_500)
        assertEquals(1, w.api.calls.count { it == "put" }, "still inside the debounce")
        assertEquals(VaultState.OutOfDate, w.vault())
        advanceTimeBy(600)
        assertEquals(2, w.api.calls.count { it == "put" })
        val key = Vault.deriveKey("correct horse battery staple", Vault.unb64(w.store.state.value.salt!!))
        assertEquals("Ada L.", contentsOf(w, key).profile.name)
        assertEquals("https://relay.example.org", contentsOf(w, key).settings!!.relayUrl)
        assertEquals(VaultState.UpToDate, w.vault())
    }

    /** A restart loses the key but not the hash: the same plaintext still reads as up to date, and nothing is re-sent. */
    @Test fun `after a restart an unchanged record is still up to date and the stored hash is the plaintext's sha256`() = runTest {
        val first = world()
        first.repo.finish("ada@example.org", "123456"); first.repo.setPassphrase("correct horse battery staple")
        advanceTimeBy(2_001)
        val hash = first.store.state.value.vaultHash!!
        val key = Vault.deriveKey("correct horse battery staple", Vault.unb64(first.store.state.value.salt!!))
        val plaintext = Vault.open(key, first.api.vault!!)
        assertEquals(Crypto.sha256hex(plaintext.toByteArray()), hash)

        val again = world(first.store.state.value)      // same disk, fresh process, no key
        assertEquals(VaultState.UpToDate, again.vault())
        again.record.saveProfile(profile.copy(name = "Ada L."))
        assertEquals(VaultState.OutOfDate, again.vault())
        again.record.saveProfile(profile)
        assertEquals(VaultState.UpToDate, again.vault())
        advanceTimeBy(5_000)
        assertEquals(0, again.api.calls.count { it == "put" })
    }

    @Test fun `without the key in memory an upload is skipped silently and Settings sees out of date`() = runTest {
        val salt = Vault.b64(Vault.newSalt())
        val w = world(AccountState(session = Session("s3cret", "acct_1", "ada@example.org"), salt = salt, vaultAt = 5L, vaultHash = "stale"))
        advanceTimeBy(1)
        assertEquals(VaultState.OutOfDate, w.vault())
        w.record.saveProfile(profile.copy(name = "Ada L."))
        advanceTimeBy(5_000)
        assertEquals(0, w.api.calls.count { it == "put" })
        // Update: the passphrase is checked against the vault on the server, then the fresh contents go up.
        val key = Vault.deriveKey("correct horse battery staple", Vault.unb64(salt))
        w.api.vault = Vault.seal(key, VaultContents(signPriv = "aa", kxPriv = "bb", profile = profile).encode(), Vault.unb64(salt))
        assertTrue(runCatching { w.repo.unlock("not it") }.exceptionOrNull() is WrongPassphrase)
        w.repo.unlock("correct horse battery staple")
        assertEquals(1, w.api.calls.count { it == "put" })
        assertEquals("Ada L.", contentsOf(w, key).profile.name)
        assertEquals(VaultState.UpToDate, w.vault())
    }

    @Test fun `restoring takes the vault's identity, profile and relay, and a wrong passphrase changes nothing`() = runTest {
        val w = world(AccountState(session = Session("s3cret", "acct_1", "ada@example.org")), withProfile = false)
        val theirs = DeviceKeys.fromSeeds(ByteArray(32) { 9 }, ByteArray(32) { 10 })
        val salt = Vault.newSalt()
        val key = Vault.deriveKey("correct horse battery staple", salt)
        val contents = VaultContents(signPriv = Crypto.hex(theirs.signPriv), kxPriv = Crypto.hex(theirs.kxPriv), profile = profile.copy(name = "Vault Ada"),
            settings = VaultSettings("km", "https://relay.example.org", "https://acct.example.org"))
        w.api.vault = Vault.seal(key, contents.encode(), salt)
        val before = w.identity.keys

        assertTrue(runCatching { w.repo.restore("wrong horse") }.exceptionOrNull() is WrongPassphrase)
        assertEquals(before, w.identity.keys); assertNull(w.record.saved)

        w.repo.restore("correct horse battery staple")
        assertEquals(theirs, w.identity.keys)
        assertEquals("Vault Ada", w.record.saved!!.name)
        assertEquals("https://relay.example.org", w.maps.cur.value.relayUrl)
        assertEquals("https://elsewhere-account.vinav.workers.dev", w.maps.cur.value.accountUrl, "the URL that just worked stays")
        assertEquals(Vault.b64(salt), w.store.state.value.salt)
        advanceTimeBy(3_000)
        assertEquals(VaultState.UpToDate, w.vault())
    }

    @Test fun `no vault on the server reads as missing, not as a wrong passphrase`() = runTest {
        val w = world(AccountState(session = Session("s3cret", "acct_1", "ada@example.org")))
        assertTrue(runCatching { w.repo.restore("correct horse battery staple") }.exceptionOrNull() is NoVault)
    }

    @Test fun `a 401 on the scheduled upload signs the phone out, even though it cancels its own job`() = runTest {
        val w = world()
        w.repo.finish("ada@example.org", "123456"); w.repo.setPassphrase("correct horse battery staple")
        w.api.putFail = AccountException(401, "unauthorized")
        advanceTimeBy(2_001)
        assertNull(w.store.state.value.session)
        assertNull(w.store.state.value.salt)
        assertEquals(VaultState.None, w.vault())
    }

    @Test fun `a failed upload is retried on the next change`() = runTest {
        val w = world()
        w.repo.finish("ada@example.org", "123456"); w.repo.setPassphrase("correct horse battery staple")
        w.api.putFail = java.io.IOException("offline")
        advanceTimeBy(2_001)
        assertEquals(1, w.api.calls.count { it == "put" }); assertNull(w.api.vault)
        assertEquals(VaultState.OutOfDate, w.vault())
        w.api.putFail = null
        w.record.saveProfile(profile.copy(name = "Ada L."))
        advanceTimeBy(2_001)
        assertNotNull(w.api.vault)
        assertEquals(VaultState.UpToDate, w.vault())
    }

    @Test fun `logging out clears the session but keeps the record and identity, deleting tells the server first`() = runTest {
        val w = world()
        w.repo.finish("ada@example.org", "123456"); w.repo.setPassphrase("correct horse battery staple")
        val keys = w.identity.keys
        w.repo.logOut()
        assertEquals("logout s3cret", w.api.calls.last())
        assertNull(w.store.state.value.session); assertNull(w.store.state.value.salt)
        assertEquals(keys, w.identity.keys); assertEquals(profile, w.record.profile.value)
        assertEquals(VaultState.None, w.vault())

        w.repo.finish("ada@example.org", "654321")
        w.repo.deleteAccount()
        assertEquals("deleteMe s3cret", w.api.calls.last())
        assertNull(w.store.state.value.session)
    }

    @Test fun `welcome is remembered and the gate reads it`() = runTest {
        val w = world(withProfile = false)
        advanceTimeBy(1)
        assertEquals(AccountState(), w.repo.account.value)
        w.repo.welcome()
        advanceTimeBy(1)
        assertTrue(w.repo.account.value!!.welcomed)
    }

    @Test fun `start sends the email on`() = runTest {
        val w = world()
        w.repo.start(" Ada@Example.org ")
        assertEquals(listOf("start ada@example.org"), w.api.calls)
    }

    private val driveKey = ByteArray(32) { (it + 1).toByte() }

    @Test fun `a Google user with no vault gets one sealed under the Drive key, without a salt`() = runTest {
        val w = world()
        advanceTimeBy(1)
        w.repo.google("id.tok")
        assertEquals(DriveLink.Ready, w.repo.useDriveKey(Vault.b64(driveKey)))
        advanceTimeBy(1)
        assertEquals(1, w.api.calls.count { it == "put" })
        assertEquals(2, w.api.vault!!.v)
        assertNull(w.api.vault!!.salt)
        assertEquals(profile, contentsOf(w, driveKey).profile)
        assertEquals(VaultState.UpToDate, w.vault())
        w.record.saveProfile(profile.copy(name = "Ada L."))
        advanceTimeBy(2_001)
        assertEquals("Ada L.", contentsOf(w, driveKey).profile.name)
    }

    @Test fun `a Google user whose vault is already under the Drive key is restored from it straight away`() = runTest {
        val other = DeviceKeys.fromSeeds(ByteArray(32) { 7 }, ByteArray(32) { 8 })
        val contents = VaultContents(signPriv = Crypto.hex(other.signPriv), kxPriv = Crypto.hex(other.kxPriv), profile = profile.copy(name = "Grace"), settings = VaultSettings("km", "https://relay.example.org", null))
        val w = world(withProfile = false)
        w.api.vault = com.elsewhere.app.sync.Wire.sealVault(driveKey, contents.encode())
        w.repo.google("id.tok")
        assertEquals(DriveLink.Ready, w.repo.useDriveKey(Vault.b64(driveKey)))
        assertEquals("Grace", w.record.saved!!.name)
        assertEquals(1, w.identity.replaced)
        assertEquals("https://relay.example.org", w.maps.cur.value.relayUrl)
        advanceTimeBy(3_000)
        assertEquals("Grace", contentsOf(w, driveKey).profile.name)
        assertEquals(VaultState.UpToDate, w.vault())
    }

    @Test fun `a Google user with a passphrase vault is asked for it once, then it is re-sealed under the Drive key`() = runTest {
        val other = DeviceKeys.fromSeeds(ByteArray(32) { 7 }, ByteArray(32) { 8 })
        val contents = VaultContents(signPriv = Crypto.hex(other.signPriv), kxPriv = Crypto.hex(other.kxPriv), profile = profile.copy(name = "Grace"))
        val salt = Vault.newSalt()
        val w = world(withProfile = false)
        w.api.vault = Vault.seal(Vault.deriveKey("correct horse battery staple", salt), contents.encode(), salt)
        w.repo.google("id.tok")
        assertEquals(DriveLink.NeedsPassphrase, w.repo.useDriveKey(Vault.b64(driveKey)))
        assertEquals(0, w.identity.replaced)
        w.repo.restore("correct horse battery staple")
        assertEquals(1, w.identity.replaced)
        assertEquals("Grace", w.record.saved!!.name)
        assertEquals(1, w.api.calls.count { it == "put" })
        assertEquals(2, w.api.vault!!.v)
        assertNull(w.store.state.value.salt)
        assertEquals("Grace", contentsOf(w, driveKey).profile.name)
        assertEquals(VaultState.UpToDate, w.vault())
    }

    @Test fun `while the passphrase is still owed, nothing goes up and the Drive key is not kept`() = runTest {
        val salt = Vault.newSalt()
        val w = world(withProfile = false)
        val v1 = Vault.seal(Vault.deriveKey("correct horse battery staple", salt), VaultContents(signPriv = "aa", kxPriv = "bb", profile = profile).encode(), salt)
        w.api.vault = v1
        w.repo.google("id.tok")
        assertEquals(DriveLink.NeedsPassphrase, w.repo.useDriveKey(Vault.b64(driveKey)))
        assertNull(w.store.state.value.driveKey)
        w.record.profile.value = profile.copy(name = "Edited here")
        advanceTimeBy(AccountRepository.DEBOUNCE_MS + 10)
        assertEquals(v1, w.api.vault, "the passphrase vault must not be written over")
        assertEquals(0, w.api.calls.count { it == "put" })
    }

    @Test fun `a vault under another Drive key cannot be opened and says so`() = runTest {
        val w = world(withProfile = false)
        w.api.vault = com.elsewhere.app.sync.Wire.sealVault(ByteArray(32) { 9 }, "{}")
        w.repo.google("id.tok")
        assertTrue(runCatching { w.repo.useDriveKey(Vault.b64(driveKey)) }.exceptionOrNull() is VaultLocked)
        assertEquals(Vault.b64(driveKey), w.store.state.value.driveKey, "the key is kept so Settings can say the record is locked")
    }
}
