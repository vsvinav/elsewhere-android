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
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.map.Arrival
import com.elsewhere.app.map.PlayState
import com.elsewhere.app.Fx
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

/** The Replay tab's chrome: lens chips, arrival tags, the HUD with date, play, speed and scrub, over a plain stock box. */
class ReplaySnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun chrome() = paparazzi.snapshot {
        val data = SampleRecord.record
        val at = Fx.utc(2024, 4, 15) + 12 * Fx.H
        val state = PlayState(playhead = at.toDouble(), playing = true, speed = 8.0, lens = Lens.FLY, follow = false)
        val tags = listOf(Arrival(1, "lisbon", 1, at - 3000), Arrival(2, "lisbon-cafe", 1, at))
        ElsewhereTheme {
            CompositionLocalProvider(LocalMotion provides false) {
                ReplayScreen(data, state, tags, ReplayActions(), Modifier.height(640.dp)) { m ->
                    Box(m.fillMaxSize().background(Poster.Stock))
                }
            }
        }
    }
}
