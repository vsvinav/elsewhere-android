package com.elsewhere.app.ui.tabs.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Settings
import com.elsewhere.app.data.noteGoogleFailed
import com.elsewhere.app.data.noteGoogleLoaded
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.map.SizeBy
import com.elsewhere.app.ui.shell.RangeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Map tab's state: the pins of the record in the current window, what the chips and search leave of them,
 * and the map settings a host needs, plus the one write a host makes: counting sessions where Google failed to load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(range: RangeStore, private val maps: MapsSettings) : ViewModel() {
    val settings: StateFlow<Settings?> = maps.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** One pin cache per record; a range change swaps it, and the screen redraws without moving the camera. */
    val layers: StateFlow<PinLayers?> = range.filtered.mapLatest { it?.let(::PinLayers) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val opts = MutableStateFlow(PinOpts())

    fun toggle(cat: Cat) = opts.update { o -> o.copy(hidden = if (cat in o.hidden) o.hidden - cat else o.hidden + cat) }
    fun only(cat: Cat) = opts.update { it.copy(hidden = Cat.entries.toSet() - cat) }
    fun showAll() = opts.update { it.copy(hidden = emptySet()) }
    fun search(q: String) = opts.update { it.copy(query = q) }
    fun select(id: String?) = opts.update { it.copy(selected = id) }
    fun sizeBy(s: SizeBy) = opts.update { it.copy(sizeBy = s) }

    fun googleFailed() { viewModelScope.launch { maps.noteGoogleFailed() } }
    fun googleLoaded() { viewModelScope.launch { maps.noteGoogleLoaded() } }
}
