package com.elsewhere.app.ui.tabs.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.Grain
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.analytics.cumulative
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.map.Arrival
import com.elsewhere.app.map.Basemap
import com.elsewhere.app.map.Bounds
import com.elsewhere.app.map.Cam
import com.elsewhere.app.map.MapEvent
import com.elsewhere.app.map.MapHost
import com.elsewhere.app.map.PlayState
import com.elsewhere.app.map.ReplayTrails
import com.elsewhere.app.map.Tap
import com.elsewhere.app.Fx
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test

/** The Replay tab's chrome: lens chips, arrival tags, the HUD with date, play, speed, grain chips and scrub, over a plain stock box. */
class ReplaySnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    // The HUD finds the stay by binary search, as a parsed record is sorted by start; the sample is not.
    private val data = SampleRecord.record.let { it.copy(visits = it.visits.sortedBy { v -> v.start }) }
    private val at = Fx.utc(2024, 4, 15) + 12 * Fx.H

    private fun shoot(data: Record, state: PlayState, tags: List<Arrival> = emptyList()) = paparazzi.snapshot {
        ElsewhereTheme {
            CompositionLocalProvider(LocalMotion provides false) {
                ReplayScreen(data, state, tags, ReplayActions(), Modifier.height(760.dp)) { m ->
                    Box(m.fillMaxSize().background(Poster.Stock))
                }
            }
        }
    }

    @Test fun chrome() = shoot(
        data, PlayState(playhead = at.toDouble(), playing = true, speed = 8.0, lens = Lens.FLY, follow = false),
        listOf(Arrival(1, "lisbon", 1, at - 3000), Arrival(2, "lisbon-cafe", 1, at)),
    )

    /** A finger on the strip just past the record's last milestone: paused, the rail printed with its red line, the year in force. */
    @Test fun scrubbing() = shoot(data, PlayState(playhead = cumulative(data).milestones.last().ts + 60, playing = false, grain = Grain.YEAR, scrubbing = true))

    /** No trips on record: the Year chip is dead and the window falls to Month. */
    @Test fun noTrips() = shoot(data.copy(trips = emptyList()), PlayState(playhead = at.toDouble(), playing = false))

    /** The year, framed on the northern hemisphere: Lisbon's two hops in red, the hop out to Tokyo fading in under the playhead, no trail, dots or stamp. */
    @Test fun year() {
        val tokyo = data.trips[1]
        val state = PlayState(playhead = tokyo.start + 1.0 * Fx.D, playing = false, follow = false, grain = Grain.YEAR)
        val host = StillHost(Cam(45.0, -100.0, 1.0f))
        paparazzi.snapshot {
            ElsewhereTheme {
                CompositionLocalProvider(LocalMotion provides false) {
                    ReplayScreen(data, state, emptyList(), ReplayActions(), Modifier.height(760.dp)) { m ->
                        Box(m.fillMaxSize().background(Poster.Stock)) { ReplayCanvas(host, data, ReplayTrails(data), MutableStateFlow(state), mutableLongStateOf(0L), Modifier.fillMaxSize()) }
                    }
                }
            }
        }
    }
}

/** A host whose camera never moves, so the canvas alone is on trial. */
private class StillHost(cam: Cam) : MapHost {
    override val camera: StateFlow<Cam> = MutableStateFlow(cam)
    override val events: SharedFlow<MapEvent> = MutableSharedFlow()
    override val taps: SharedFlow<Tap> = MutableSharedFlow()
    override val viewport: StateFlow<Viewport?> = MutableStateFlow(null)
    override fun moveTo(cam: Cam, animateMs: Int) {}
    override fun fit(bounds: Bounds, paddingPx: Int) {}
    override fun project(lat: Double, lng: Double): Offset? = null
    @Composable override fun Content(modifier: Modifier, basemap: Basemap, copyright: String?, overlays: @Composable () -> Unit) = overlays()
}
