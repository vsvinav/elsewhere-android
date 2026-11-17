package com.elsewhere.app.account

import com.elsewhere.app.crew.Crypto
import com.elsewhere.app.crew.DeviceKeys
import com.elsewhere.app.crew.Identity
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Settings
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Units
import com.elsewhere.app.sync.Wire
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import kotlin.coroutines.coroutineContext

/** What Settings prints next to "Recovery vault". */
enum class VaultState { None, UpToDate, OutOfDate }

/** The passphrase does not open the vault on the server. */
class WrongPassphrase : Exception("That's not it")

/** The account has no vault to restore from. */
class NoVault : Exception("No vault on this account")

/** The vault on the server is sealed under a Drive key other than the one in the user's Drive. */
class VaultLocked : Exception("Can't unlock your synced record.")

/** What a Google login still needs after the Drive key is at hand: nothing, or the old passphrase once to move over. */
enum class DriveLink { Ready, NeedsPassphrase }

/** Everything the screens need from the account layer; the repository is the real one, tests hand in a fake. */
interface Accounts {
    /** Null until the store has been read, so the gate never flashes the wrong door. */
    val account: StateFlow<AccountState?>
    val vaultState: StateFlow<VaultState>
    suspend fun welcome()
    suspend fun start(email: String)
    suspend fun finish(email: String, code: String): AccountInfo
    suspend fun google(idToken: String): AccountInfo
    suspend fun setPassphrase(passphrase: String)
    suspend fun restore(passphrase: String)
    suspend fun unlock(passphrase: String)
    /**
     * The Drive key takes over from the passphrase: a vault already under it is restored, a passphrase vault asks for
     * the passphrase once (then [restore] re-seals it), none means one goes up. `fresh` skips the server and re-seals
     * what this phone has, for Start fresh.
     */
    suspend fun useDriveKey(b64: String, fresh: Boolean = false): DriveLink
    suspend fun logOut()
    suspend fun deleteAccount()
}

/**
 * The account on this phone: the session, the recovery vault and when it goes up. The vault is uploaded when the
 * identity, a profile and the passphrase key are all at hand, two seconds after the last change; without the key
 * (after a restart) nothing happens and Settings says so. The key lives only here, never on disk.
 */
class AccountRepository(
    private val api: AccountApi,
    private val store: AccountStorage,
    private val record: RecordStore,
    private val settings: MapsSettings,
    private val identity: Identity,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val crypto: CoroutineDispatcher = Dispatchers.Default,
) : Accounts {
    companion object { const val DEBOUNCE_MS = 2_000L }

    private val key = MutableStateFlow<ByteArray?>(null)
    private var pending: Job? = null
    /** A Drive key waiting on the passphrase that opens the old vault; it becomes the account's only once [restore] has moved the vault over. */
    private var owedDrive: String? = null

    override val account: StateFlow<AccountState?> = store.state.stateIn(scope, SharingStarted.Eagerly, null)

    override val vaultState: StateFlow<VaultState> = combine(store.state, record.profile, settings.settings, key) { st, p, s, _ ->
        when {
            st.session == null || p == null -> VaultState.None
            st.vaultHash != null && st.vaultHash == hash(plaintext(identity.keys(), p, s)) -> VaultState.UpToDate
            else -> VaultState.OutOfDate
        }
    }.stateIn(scope, SharingStarted.Eagerly, VaultState.None)

    init {
        // Every change to what the vault holds (and app start) arms one upload, two seconds out.
        scope.launch { combine(record.profile, settings.settings) { p, s -> p to s }.collect { schedule(debounceMs) } }
    }

    override suspend fun welcome() = store.markWelcomed()

    override suspend fun start(email: String) = api.start(normalizeEmail(email))

    override suspend fun finish(email: String, code: String): AccountInfo = signedIn(api.finish(normalizeEmail(email), code))

    override suspend fun google(idToken: String): AccountInfo = signedIn(api.google(idToken))

    /** A new session: whatever vault stamp the last account left is wrong for this one. */
    private suspend fun signedIn(login: Login): AccountInfo {
        key.value = null
        store.saveSalt(null); store.saveVault(0L, null); store.saveDriveKey(null)
        store.saveSession(Session(login.session, login.account.id, login.account.email))
        return login.account
    }

    /** A fresh salt and key; the vault goes up as soon as there is a profile to put in it. */
    override suspend fun setPassphrase(passphrase: String) {
        val salt = Vault.newSalt()
        val k = withContext(crypto) { Vault.deriveKey(passphrase, salt) }
        store.saveSalt(Vault.b64(salt)); store.saveVault(0L, null)
        key.value = k
        schedule(0L)
    }

    /** Log in on a new device: become the identity in the vault, take its profile and relay. */
    override suspend fun restore(passphrase: String) {
        val (k, body, contents) = open(passphrase) ?: throw NoVault()
        adopt(contents)
        val drive = owedDrive
        if (drive != null) { owedDrive = null; store.saveDriveKey(drive); store.saveSalt(null); store.saveVault(0L, null); uploadNow(); return }
        store.saveSalt(body.salt); store.saveVault(clock(), hash(contents.encode()))
        key.value = k
    }

    private suspend fun adopt(contents: VaultContents) {
        identity.replaceKeys(DeviceKeys.fromSeeds(Crypto.unhex(contents.signPriv), Crypto.unhex(contents.kxPriv)))
        record.saveProfile(contents.profile)
        // The account URL that just worked stays; a vault from another service would lock us out of this one.
        contents.settings?.relayUrl?.let { url -> settings.saveSettings(settings.settings.first().copy(relayUrl = url)) }
    }

    /** Settings → Update: prove the passphrase against the vault on the server, then send what this phone has. */
    override suspend fun unlock(passphrase: String) {
        val opened = open(passphrase)
        if (opened == null) { setPassphrase(passphrase); return }
        store.saveSalt(opened.second.salt)
        key.value = opened.first
        uploadNow()
    }

    override suspend fun useDriveKey(b64: String, fresh: Boolean): DriveLink {
        val k = Vault.unb64(b64)
        owedDrive = null
        if (fresh) { store.saveDriveKey(b64); store.saveSalt(null); store.saveVault(0L, null); schedule(0L); return DriveLink.Ready }
        val session = session() ?: throw AccountException(401, "signed out")
        val body = authed { api.getVault(session) }
        when {
            body == null -> { store.saveDriveKey(b64); store.saveSalt(null); store.saveVault(0L, null); schedule(0L) }
            // The passphrase vault stays whole until the passphrase has opened it; a Drive key kept now would write a new one over it.
            body.v < 2 -> { owedDrive = b64; return DriveLink.NeedsPassphrase }
            else -> {
                store.saveDriveKey(b64); store.saveSalt(null)
                val plaintext = try { withContext(crypto) { Vault.open(k, body) } } catch (e: GeneralSecurityException) { throw VaultLocked() }
                adopt(VaultContents.decode(plaintext))
                store.saveVault(clock(), hash(plaintext))
            }
        }
        return DriveLink.Ready
    }

    private suspend fun open(passphrase: String): Triple<ByteArray, VaultBody, VaultContents>? {
        val session = session() ?: throw AccountException(401, "signed out")
        val body = authed { api.getVault(session) } ?: return null
        val k = withContext(crypto) { Vault.deriveKey(passphrase, Vault.unb64(body.salt ?: throw NoVault())) }
        val plaintext = try { Vault.open(k, body) } catch (e: GeneralSecurityException) { throw WrongPassphrase() }
        return Triple(k, body, VaultContents.decode(plaintext))
    }

    /** Clears the session here whatever the server says; the record and the identity stay on the phone. */
    override suspend fun logOut() {
        session()?.let { s -> runCatching { api.logout(s) } }
        signOutLocally()
    }

    override suspend fun deleteAccount() {
        val s = session() ?: return
        authed { api.deleteMe(s) }
        signOutLocally()
    }

    /** Sends the vault now if everything is at hand; true when the server holds the current contents afterwards. */
    suspend fun uploadNow(): Boolean {
        val st = store.state.first()
        val session = st.session?.session ?: return false
        val drive = st.driveKey?.let(Vault::unb64)
        val k = drive ?: key.value ?: return false
        val salt = if (drive == null) st.salt ?: return false else null
        val p = record.profile.value ?: return false
        val plaintext = plaintext(identity.keys(), p, settings.settings.first())
        val h = hash(plaintext)
        if (h == st.vaultHash) return true
        val body = withContext(crypto) { if (salt == null) Wire.sealVault(k, plaintext) else Vault.seal(k, plaintext, Vault.unb64(salt)) }
        authed { api.putVault(session, body) }
        store.saveVault(clock(), h)
        return true
    }

    private fun schedule(after: Long) {
        pending?.cancel()
        pending = scope.launch { delay(after); runCatching { uploadNow() } }
    }

    private suspend fun session(): String? = store.state.first().session?.session

    /** A 401 means the session is gone: forget it here too. */
    private suspend fun <T> authed(block: suspend () -> T): T = try { block() } catch (e: AccountException) {
        if (e.code == 401) signOutLocally()
        throw e
    }

    /** The store first: cancelling `pending` from inside the scheduled upload would otherwise cut this short. */
    private suspend fun signOutLocally() {
        key.value = null; owedDrive = null
        store.clearSession()
        val job = pending
        if (job != null && job !== coroutineContext[Job]) job.cancel()
    }

    private fun plaintext(keys: DeviceKeys, p: Profile, s: Settings): String =
        VaultContents(
            signPriv = Crypto.hex(keys.signPriv), kxPriv = Crypto.hex(keys.kxPriv), profile = p,
            settings = VaultSettings(units = if (p.units == Units.MI) "mi" else "km", relayUrl = s.relayUrl, accountUrl = s.accountUrl),
        ).encode()

    private fun hash(plaintext: String): String = Crypto.sha256hex(plaintext.toByteArray())
}
