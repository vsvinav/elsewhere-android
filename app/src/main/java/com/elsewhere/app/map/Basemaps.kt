package com.elsewhere.app.map

import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.TileService
import com.elsewhere.app.data.TileSession
import com.elsewhere.app.data.TilesError
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.data.rasterStyle
import com.elsewhere.app.data.tileTemplate
import com.elsewhere.app.ingest.CostMeter
import com.elsewhere.app.ingest.Kind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The base map every map-bearing screen draws on: Google's native SDK when this build carries its key (no
 * session, nothing counted: dynamic maps are free), else Google's tiles through one Map Tiles session per key
 * (counted as one map load, as the web counts one Maps JS load), else the free style when there is no accepted
 * key or Google refuses the session. One per view model, so each screen is its own map session.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class Basemaps(scope: CoroutineScope, maps: MapsSettings, private val tiles: TileService, meter: CostMeter, sdk: Boolean = Sdk.available) {
    private val _tilesError = MutableStateFlow<String?>(null)
    /** Google's own words when the tile session could not be opened; the screen says so once. */
    val tilesError: StateFlow<String?> = _tilesError

    private val _copyright = MutableStateFlow<String?>(null)
    /** The attribution line for what the camera shows; kept apart from [basemap] so it never reloads the style. */
    val copyright: StateFlow<String?> = _copyright
    private val viewports = MutableSharedFlow<Viewport>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val session: StateFlow<Pair<String, TileSession>?> = maps.settings
        .map { s -> if (resolve(s, sdk) == Provider.GOOGLE) s.mapsKey else null }.distinctUntilChanged()
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
        .stateIn(scope, SharingStarted.Eagerly, null)

    // Starts as the SDK on an SDK build so the first frame already has the right map underneath; the Map ID follows.
    val basemap: StateFlow<Basemap> = combine(maps.settings, session) { s, ks ->
        when {
            resolve(s, sdk) == Provider.SDK -> Basemap.Sdk(s.mapId)
            ks != null -> Basemap.Google(rasterStyle(tileTemplate(ks.second, ks.first)))
            else -> Basemap.Free
        }
    }.stateIn(scope, SharingStarted.Eagerly, if (sdk) Basemap.Sdk("") else Basemap.Free)

    init {
        // The attribution Google's terms want follows the camera, a second after it settles.
        scope.launch {
            viewports.debounce(1000).collect { vp ->
                val (key, s) = session.value ?: return@collect
                _copyright.value = tiles.copyright(s, key, vp)
            }
        }
    }

    /** The camera came to rest showing `vp`. */
    fun idle(vp: Viewport) { viewports.tryEmit(vp) }
}
