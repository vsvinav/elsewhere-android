package com.elsewhere.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.elsewhere.app.sync.DriveLinker
import com.elsewhere.app.sync.SyncStatus
import com.elsewhere.app.sync.Syncer
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.account.Accounts
import com.elsewhere.app.account.Session
import com.elsewhere.app.account.VaultState
import com.elsewhere.app.account.userMessage
import com.elsewhere.app.ask.LlmClient
import com.elsewhere.app.ask.LlmConfig
import com.elsewhere.app.ask.LlmProvider
import com.elsewhere.app.ask.TEST_PROMPT
import com.elsewhere.app.ask.modelOf
import com.elsewhere.app.data.LlmSettings
import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Settings
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.KeyReport
import com.elsewhere.app.ingest.KeyVerifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

/** Where the Google key check stands on this screen. */
sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Checking : KeyCheck
    data class Done(val report: KeyReport) : KeyCheck
}

/** Where the "Ask Elsewhere" TEST stands. */
sealed interface LlmTest {
    data object Idle : LlmTest
    data object Testing : LlmTest
    data object NeedKey : LlmTest
    data class Ok(val model: String) : LlmTest
    data class Failed(val message: String) : LlmTest
}

/** The Account section: who is signed in, if anyone, and whether the vault matches this phone. */
data class AccountUi(val session: Session? = null, val vault: VaultState = VaultState.None, val error: String? = null)

/** Everything the settings page shows; null in the view model until the stores have been read. */
data class SettingsUi(
    val maps: Settings, val llm: LlmConfig, val units: Units,
    val keyCheck: KeyCheck = KeyCheck.Idle, val test: LlmTest = LlmTest.Idle, val account: AccountUi = AccountUi(),
    val importing: Float? = null, val importError: String? = null,
    val sync: SyncStatus = SyncStatus.Off,
)

/** Everything the settings page can do; the view model is the real one, snapshots pass a no-op. */
interface SettingsActions {
    fun saveKey(key: String, mapId: String)
    fun setMapProvider(p: MapProvider)
    fun setLlm(c: LlmConfig)
    fun testLlm(c: LlmConfig)
    fun setUnits(u: Units)
    fun setRelayUrl(url: String)
    fun setAccountUrl(url: String)
    fun logOut()
    /** `activity` must be the foreground Activity: Drive may ask for consent over it. */
    fun deleteAccount(activity: Context)
    fun import(open: () -> InputStream, fileName: String)
    fun syncNow()
    /** `activity` must be the foreground Activity: Drive may ask for consent over it. */
    fun startFresh(activity: Context)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val maps: MapsSettings, private val verifier: KeyVerifier, private val repo: RecordStore,
    private val llm: LlmSettings, private val client: LlmClient, private val accounts: Accounts, private val syncer: Syncer,
    private val drive: DriveLinker,
) : ViewModel(), SettingsActions {
    private val keyCheck = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    private val test = MutableStateFlow<LlmTest>(LlmTest.Idle)
    private val accountError = MutableStateFlow<String?>(null)

    private val account = combine(accounts.account, accounts.vaultState, accountError) { a, v, e -> AccountUi(a?.session, v, e) }
    private val importing = MutableStateFlow<Float?>(null)
    private val importError = MutableStateFlow<String?>(null)

    val ui: StateFlow<SettingsUi?> = combine(maps.settings, llm.llm, repo.profile, keyCheck, test, account, importing, importError, syncer.status) { arr ->
        @Suppress("UNCHECKED_CAST")
        SettingsUi(arr[0] as Settings, arr[1] as LlmConfig, (arr[2] as Profile?)?.units ?: Units.KM, arr[3] as KeyCheck, arr[4] as LlmTest, arr[5] as AccountUi, arr[6] as Float?, arr[7] as String?, arr[8] as SyncStatus)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override fun import(open: () -> InputStream, fileName: String) {
        if (importing.value != null) return
        importing.value = 0f; importError.value = null
        viewModelScope.launch {
            try { repo.import(open, fileName) { importing.value = it } } catch (e: Exception) { importError.value = e.message ?: "Couldn't read that file" }
            importing.value = null
        }
    }

    /** A blank key still saves: it clears the stored key and its verdict, keeping the Map ID. */
    override fun saveKey(key: String, mapId: String) {
        val k = key.trim(); val id = mapId.trim()
        if (k.isBlank()) {
            keyCheck.value = KeyCheck.Idle
            viewModelScope.launch { maps.saveSettings(maps.settings.first().copy(mapsKey = "", mapId = id, keyPlaces = false, keyTiles = false, keyCheckedAt = 0)) }
            return
        }
        keyCheck.value = KeyCheck.Checking
        viewModelScope.launch { keyCheck.value = KeyCheck.Done(verifier.verify(k, id)) }
    }

    override fun setMapProvider(p: MapProvider) {
        viewModelScope.launch { maps.saveSettings(maps.settings.first().copy(mapProvider = p)) }
    }

    override fun setLlm(c: LlmConfig) {
        test.update { if (it is LlmTest.Testing) it else LlmTest.Idle }
        viewModelScope.launch { llm.saveLlm(c) }
    }

    override fun testLlm(c: LlmConfig) {
        if (c.provider != LlmProvider.CUSTOM && c.key.isBlank()) { test.value = LlmTest.NeedKey; return }
        test.value = LlmTest.Testing
        viewModelScope.launch {
            test.value = runCatching { client.ask(c, TEST_PROMPT) }
                .fold({ LlmTest.Ok(modelOf(c)) }, { LlmTest.Failed(it.message ?: it.javaClass.simpleName) })
        }
    }

    override fun setRelayUrl(url: String) {
        viewModelScope.launch { maps.saveSettings(maps.settings.first().copy(relayUrl = url.trim())) }
    }

    override fun setAccountUrl(url: String) {
        viewModelScope.launch { maps.saveSettings(maps.settings.first().copy(accountUrl = url.trim().ifBlank { Settings.DEFAULT_ACCOUNT })) }
    }

    override fun logOut() = accountCall { accounts.logOut() }

    override fun deleteAccount(activity: Context) = accountCall { drive.deleteAccount(activity) }

    override fun syncNow() = syncer.syncNow()

    override fun startFresh(activity: Context) { viewModelScope.launch { syncer.startFresh(activity) } }

    private fun accountCall(block: suspend () -> Unit) {
        accountError.value = null
        viewModelScope.launch { runCatching { block() }.onFailure { accountError.value = it.userMessage() } }
    }

    override fun setUnits(u: Units) {
        val p = repo.profile.value ?: return
        if (p.units != u) viewModelScope.launch { repo.saveProfile(p.copy(units = u)) }
    }
}
