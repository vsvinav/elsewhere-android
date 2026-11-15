package com.elsewhere.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.LandSource
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.RecordStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which door the visitor is at: the front door until there is an import and a home. */
@HiltViewModel
class RootViewModel @Inject constructor(repo: RecordStore, landLoader: LandSource) : ViewModel() {
    val ready: StateFlow<Boolean> = repo.ready
    val hasImport: StateFlow<Boolean> = repo.hasImport
    val profile: StateFlow<Profile?> = repo.profile
    private val _land = MutableStateFlow<FeatureCollection?>(null)
    val land: StateFlow<FeatureCollection?> = _land

    init { viewModelScope.launch { _land.value = landLoader.get() } }
}
