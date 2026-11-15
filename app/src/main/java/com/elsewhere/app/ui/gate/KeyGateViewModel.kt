package com.elsewhere.app.ui.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.ingest.KeyVerifier
import com.elsewhere.app.ingest.keyGated
import com.elsewhere.app.ui.onboard.KeyActions
import com.elsewhere.app.ui.onboard.KeyPhase
import com.elsewhere.app.ui.onboard.OnboardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The startup gate for a returning visitor whose stored key is unproven: check it once, show the verdict,
 * and lift as soon as the settings say the key is good or limited maps are accepted (web KeyGate + KeyCheck).
 */
@HiltViewModel
class KeyGateViewModel @Inject constructor(maps: MapsSettings, private val verifier: KeyVerifier) : ViewModel(), KeyActions {
    private val _state = MutableStateFlow(OnboardState(keyPhase = KeyPhase.Checking))
    val state: StateFlow<OnboardState> = _state

    /** Null until the settings have been read, so a cold start never flashes the gate or the app by mistake. */
    val gated: StateFlow<Boolean?> = maps.settings.map(::keyGated).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val s = maps.settings.first()
            _state.update { it.copy(key = s.mapsKey, mapId = s.mapId) }
            if (keyGated(s)) saveKey(s.mapsKey, s.mapId)
        }
    }

    override fun saveKey(key: String, mapId: String) {
        val k = key.trim()
        if (k.isBlank()) return
        _state.update { it.copy(key = k, mapId = mapId.trim(), keyPhase = KeyPhase.Checking, keyReport = null, limitedAck = false) }
        viewModelScope.launch {
            val report = verifier.verify(k, mapId.trim())
            _state.update { it.copy(keyPhase = KeyPhase.Result, keyReport = report) }
        }
    }

    override fun recheckKey() = saveKey(_state.value.key, _state.value.mapId)
    override fun setLimitedAck(on: Boolean) { _state.update { it.copy(limitedAck = on) } }
    override fun acceptLimited() { if (_state.value.limitedAck) viewModelScope.launch { verifier.acceptLimited() } }
    override fun skipKey() = Unit
    override fun editKey() = Unit
    override fun continueKey() = Unit
}
