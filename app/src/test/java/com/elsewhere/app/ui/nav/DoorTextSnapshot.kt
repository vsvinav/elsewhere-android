package com.elsewhere.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.Lede
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

class DoorTextSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private val spans = listOf(
        span("2019–2024, live from "), span("1,826 days", Door.Range(0L, 1L, "1,826 days of record")),
        span(" of record. Everywhere your phone slept, every leg between. "), span("412 places", Door.Places(emptyList(), "412 places")),
        span(", "), span("38 cities", Door.Places(emptyList(), "38 cities")), span(", "), span("12 countries", Door.Places(emptyList(), "12 countries")), span("."),
    )

    /** At rest a lede with doors prints exactly as one without; held down, the figure under the finger underlines in red. */
    @Test fun restingAndPressed() = paparazzi.snapshot {
        ElsewhereTheme {
            Column(Modifier.fillMaxWidth().background(Poster.Stock).padding(24.dp)) {
                Lede(spans)
                Lede(spans, Modifier.padding(top = 24.dp), pressed = 3)
            }
        }
    }
}
