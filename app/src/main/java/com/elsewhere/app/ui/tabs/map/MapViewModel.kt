package com.elsewhere.app.ui.tabs.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.TileService
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.ingest.CostMeter
import com.elsewhere.app.ingest.PlaceLookups
import com.elsewhere.app.map.Basemap
import com.elsewhere.app.map.Basemaps
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.map.SizeBy
import com.elsewhere.app.ui.nav.DoorNav
import com.elsewhere.app.ui.nav.Opening
import com.elsewhere.app.ui.nav.Origin
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
import javax.inject.Inject

/** The Map tab's state: the pins of the record in the current window, what the chips and search leave of them, and the base map. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(private val range: RangeStore, maps: MapsSettings, tiles: TileService, meter: CostMeter, private val lookups: PlaceLookups, private val doors: DoorNav) : ViewModel() {
    fun seen(id: String) { range.filtered.value?.let { lookups.need(listOf(id), it.places) } }
    private val basemaps = Basemaps(viewModelScope, maps, tiles, meter)
    val basemap: StateFlow<Basemap> get() = basemaps.basemap
    val copyright: StateFlow<String?> get() = basemaps.copyright
    val tilesError: StateFlow<String?> get() = basemaps.tilesError
    fun idle(vp: Viewport) = basemaps.idle(vp)

    /** One pin cache per record; a range change swaps it, and the screen redraws without moving the camera. */
    val layers: StateFlow<PinLayers?> = range.filtered.mapLatest { it?.let(::PinLayers) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val opts = MutableStateFlow(PinOpts())

    fun toggle(cat: Cat) = opts.update { o -> o.copy(hidden = if (cat in o.hidden) o.hidden - cat else o.hidden + cat) }
    fun only(cat: Cat) = opts.update { it.copy(hidden = Cat.entries.toSet() - cat) }
    fun showAll() = opts.update { it.copy(hidden = emptySet()) }
    fun search(q: String) = opts.update { it.copy(query = q) }
    fun select(id: String?) { opts.update { it.copy(selected = id) }; doors.pinChanged(id) }
    fun sizeBy(s: SizeBy) = opts.update { it.copy(sizeBy = s) }

    /** The door the tab was opened through, if any, and what it asks the map to show. */
    val origin: StateFlow<Origin?> get() = doors.origin
    val opening: StateFlow<Opening?> get() = doors.opening
    /** The door's own pin goes on without closing the way back; a door onto pins shows every kind of stop again. */
    fun apply(o: Opening) = opts.update { it.copy(selected = o.select, hidden = if (o.clearHidden) emptySet() else it.hidden) }
    fun opened() = doors.opened()
    fun back() = doors.back()
}
