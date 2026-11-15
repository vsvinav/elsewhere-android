package com.elsewhere.app.ui.tabs.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.TileService
import com.elsewhere.app.data.TileSession
import com.elsewhere.app.data.TilesError
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.data.rasterStyle
import com.elsewhere.app.data.tileTemplate
import com.elsewhere.app.ingest.CostMeter
import com.elsewhere.app.ingest.Kind
import com.elsewhere.app.map.Basemap
import com.elsewhere.app.map.Provider
import com.elsewhere.app.map.resolve
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.map.SizeBy
import com.elsewhere.app.ui.shell.RangeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
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
 * and the base map: Google's tiles through one Map Tiles session per key (counted as one map load, as the
 * web counts one Maps JS load) or the free style when there is no accepted key or Google refuses the session.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(range: RangeStore, private val maps: MapsSettings, private val tiles: TileService, private val meter: CostMeter) : ViewModel() {
    private val _tilesError = MutableStateFlow<String?>(null)
    /** Google's own words when the tile session could not be opened; the screen says so once. */
    val tilesError: StateFlow<String?> = _tilesError

    private val _copyright = MutableStateFlow<String?>(null)
    /** The attribution line for what the camera shows; kept apart from [basemap] so it never reloads the style. */
    val copyright: StateFlow<String?> = _copyright
    private val viewports = MutableSharedFlow<Viewport>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val session: StateFlow<Pair<String, TileSession>?> = maps.settings
        .map { s -> if (resolve(s) == Provider.GOOGLE) s.mapsKey else null }.distinctUntilChanged()
        .mapLatest { key ->
            if (key == null) return@mapLatest null
            try {
                val s = tiles.session(key)
                _tilesError.value = null
                meter.tally(Kind.MAPS)
                key to s
            } catch (e: TilesError) {
                _tilesError.value = e.message; null
            } catch (e: IOException) {
                _tilesError.value = e.message ?: "No internet connection"; null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val basemap: StateFlow<Basemap> = session.map { ks ->
        ks?.let { (key, s) -> Basemap.Google(rasterStyle(tileTemplate(s, key))) } ?: Basemap.Free
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Basemap.Free)

    init {
        // The attribution Google's terms want follows the camera, a second after it settles.
        viewModelScope.launch {
            viewports.debounce(1000).collect { vp ->
                val (key, s) = session.value ?: return@collect
                _copyright.value = tiles.copyright(s, key, vp)
            }
        }
    }

    /** The camera came to rest showing `vp`. */
    fun idle(vp: Viewport) { viewports.tryEmit(vp) }

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
}
