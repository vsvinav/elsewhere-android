package com.elsewhere.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.LandSource
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Ready
import com.elsewhere.app.data.ReadyState
import com.elsewhere.app.ingest.keyGated
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.RecordStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which door the visitor is at: the front door until there is an import and a home. */
@HiltViewModel
class RootViewModel @Inject constructor(repo: RecordStore, landLoader: LandSource, maps: MapsSettings, readyState: ReadyState = ReadyState()) : ViewModel() {
    /** A returning visitor with an unproven key waits at the gate; null until the settings have been read. */
    val gated: StateFlow<Boolean?> = maps.settings.map(::keyGated).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val readyBar: StateFlow<Ready?> = readyState.ready
    val ready: StateFlow<Boolean> = repo.ready
    val hasImport: StateFlow<Boolean> = repo.hasImport
    val profile: StateFlow<Profile?> = repo.profile
    private val _land = MutableStateFlow<FeatureCollection?>(null)
    val land: StateFlow<FeatureCollection?> = _land

    init { viewModelScope.launch { _land.value = landLoader.get() } }
}
