package com.elsewhere.app.ui.tabs.facts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

class FactsSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")
    // A fixed today, so the deck's "Today" card stays the same print.
    private val ui = factsUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile, today = "2025-05-20")

    @Test fun hero() = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { FactsHero(ui, null, {}, {}) } }
    }

    @Test fun streaks() = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { Box(Modifier.background(Poster.Stock)) { PlayStrip(ui, ui.hub!!, page = 1) } } }
    }

    @Test fun list() = paparazzi.snapshot {
        ElsewhereTheme {
            CompositionLocalProvider(LocalMotion provides false) {
                Column(Modifier.fillMaxWidth().background(Poster.Stock).padding(12.dp)) {
                    ui.rest.take(2).forEachIndexed { i, f -> FactPass(f, i + 1, ui.rest.size) }
                }
            }
        }
    }
}
