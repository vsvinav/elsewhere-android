package com.elsewhere.app.ui.tabs.dates

import androidx.compose.foundation.background
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

class DatesSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")
    private val ui = datesUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile)

    @Test fun nights() = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { DatesScreen(ui, scroll = false, trips = false) } }
    }

    @Test fun trips() = paparazzi.snapshot {
        ElsewhereTheme {
            CompositionLocalProvider(LocalMotion provides false) {
                Column(Modifier.fillMaxWidth().background(Poster.Stock).padding(12.dp)) {
                    TripsHeader(ui)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
                    TripPass(ui.trips.first(), ui.units, open = true) {}
                }
            }
        }
    }
}
