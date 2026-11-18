package com.elsewhere.app.ui.tabs.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.Grain
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.analytics.cumulative
import com.elsewhere.app.data.Record
import com.elsewhere.app.map.Arrival
import com.elsewhere.app.map.PlayState
import com.elsewhere.app.Fx
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
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
}
