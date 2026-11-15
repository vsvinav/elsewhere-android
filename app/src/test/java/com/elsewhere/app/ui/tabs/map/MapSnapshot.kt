package com.elsewhere.app.ui.tabs.map

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
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

/** The Map tab's chrome: chips, search, HUD and an open pin, with a plain stock box where the host would draw. */
class MapSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun chrome() = paparazzi.snapshot {
        val layers = PinLayers(SampleRecord.record)
        ElsewhereTheme {
            CompositionLocalProvider(LocalMotion provides false) {
                MapScreen(layers, PinOpts(hidden = setOf(Cat.EAT), selected = "market", query = "ha"), MapActions(), Modifier.height(640.dp)) { m ->
                    Box(m.fillMaxSize().background(Poster.Stock))
                }
            }
        }
    }
}
