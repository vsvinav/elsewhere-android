package com.elsewhere.app.ui.tabs.replay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.TileService
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.ingest.CostMeter
import com.elsewhere.app.map.Basemap
import com.elsewhere.app.map.Basemaps
import com.elsewhere.app.map.ChromeGate
import com.elsewhere.app.map.chromeGated
import com.elsewhere.app.map.PlayState
import com.elsewhere.app.map.ReplayTrails
import com.elsewhere.app.map.tickPlayback
import com.elsewhere.app.ui.shell.RangeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Replay tab's state: the record in the window, its trails, and the playback store of web/src/store/useStore.ts
 * (playhead, playing, speed, the eased rate, follow, lens). The screen's frame loop calls [tick]; everything else is a tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaybackViewModel @Inject constructor(range: RangeStore, maps: MapsSettings, tiles: TileService, meter: CostMeter) : ViewModel() {
    private val basemaps = Basemaps(viewModelScope, maps, tiles, meter)
    private var prepare: Job? = null
    val basemap: StateFlow<Basemap> get() = basemaps.basemap
    val copyright: StateFlow<String?> get() = basemaps.copyright
    val tilesError: StateFlow<String?> get() = basemaps.tilesError
    fun idle(vp: Viewport) = basemaps.idle(vp)
    val data: StateFlow<Record?> = range.filtered

    /**
     * Legs thinned per zoom, one cache per record; a range change swaps it. The trails show at once from the raw legs
     * while every zoom bucket is thinned behind them on a worker, so no frame ever thins on the main thread.
     */
    val trails: StateFlow<ReplayTrails?> = range.filtered.mapLatest { rec ->
        rec?.let(::ReplayTrails)?.also { t -> prepare?.cancel(); prepare = viewModelScope.launch(Dispatchers.Default) { t.prepare { isActive } } }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val state = MutableStateFlow(PlayState(playhead = Double.NaN, playing = false))

    private val gate = ChromeGate()
    /** The state as the chrome sees it: the clock at most four times a second, everything else at once. */
    val chrome: StateFlow<PlayState> = state.chromeGated(gate) { System.nanoTime() / 1_000_000 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), state.value)
    private var shownOnce = false

    init {
        // A new window rewinds to its start, as the web's setRange does; the first record also seeds the playhead.
        viewModelScope.launch { range.filtered.collect { rec -> if (rec != null) state.update { it.copy(playhead = rec.meta.range.first.toDouble()) } } }
    }

    /** One real-time frame of `dt` seconds while playing. */
    fun tick(dt: Double) {
        val rec = data.value ?: return
        state.update { s -> if (s.playing && !s.playhead.isNaN()) tickPlayback(rec, s, dt) else s }
    }

    /**
     * The tab plays itself the first time it comes on screen, as the web's setTab does; a recreated Activity shows the
     * same view model again and must not restart a replay the user paused or a camera they took over.
     */
    fun shown() { if (!shownOnce) { shownOnce = true; setPlaying(true) } }

    /** Playing hands the camera back to the follow-cam; starting from the end rewinds first. */
    fun setPlaying(on: Boolean) {
        val rec = data.value
        state.update { s ->
            val rewind = on && rec != null && s.playhead >= rec.meta.range.last
            s.copy(playing = on, follow = if (on) true else s.follow, playhead = if (rewind) rec.meta.range.first.toDouble() else s.playhead)
        }
    }

    fun setSpeed(speed: Double) = state.update { it.copy(speed = speed) }

    /** Scrubbing moves the playhead and takes the camera along. */
    fun scrub(playhead: Double) = state.update { it.copy(playhead = playhead, follow = true) }

    fun setFollow(on: Boolean) = state.update { it.copy(follow = on) }
    fun setLens(lens: Lens) = state.update { it.copy(lens = lens) }
}
