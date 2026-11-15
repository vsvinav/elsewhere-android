package com.elsewhere.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.ask.LlmClient
import com.elsewhere.app.ask.LlmConfig
import com.elsewhere.app.ask.LlmProvider
import com.elsewhere.app.ask.TEST_PROMPT
import com.elsewhere.app.ask.modelOf
import com.elsewhere.app.data.LlmSettings
import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.MapsSettings
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

/** Everything the settings page shows; null in the view model until the stores have been read. */
data class SettingsUi(
    val maps: Settings, val llm: LlmConfig, val units: Units,
    val keyCheck: KeyCheck = KeyCheck.Idle, val test: LlmTest = LlmTest.Idle,
)

/** Everything the settings page can do; the view model is the real one, snapshots pass a no-op. */
interface SettingsActions {
    fun saveKey(key: String, mapId: String)
    fun setMapProvider(p: MapProvider)
    fun setLlm(c: LlmConfig)
    fun testLlm(c: LlmConfig)
    fun setUnits(u: Units)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val maps: MapsSettings, private val verifier: KeyVerifier, private val repo: RecordStore,
    private val llm: LlmSettings, private val client: LlmClient,
) : ViewModel(), SettingsActions {
    private val keyCheck = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    private val test = MutableStateFlow<LlmTest>(LlmTest.Idle)

    val ui: StateFlow<SettingsUi?> = combine(maps.settings, llm.llm, repo.profile, keyCheck, test) { s, c, p, k, t ->
        SettingsUi(s, c, p?.units ?: Units.KM, k, t)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    override fun setUnits(u: Units) {
        val p = repo.profile.value ?: return
        if (p.units != u) viewModelScope.launch { repo.saveProfile(p.copy(units = u)) }
    }
}
