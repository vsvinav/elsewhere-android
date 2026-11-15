package com.elsewhere.app.ui.tabs.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.NightsBill
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

class TourSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")
    private val ui = tourUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile, null)

    // One phone screen per snapshot: the poster is taller than any golden can hold legibly.
    private fun sheet(content: @Composable () -> Unit) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { Column(Modifier.background(Poster.Stock)) { content() } } }
    }

    @Test fun hero() = sheet { TourScreen(ui, onGo = {}) }
    @Test fun nights() = sheet { NightsBill(ui.nights!!, first = true) }
    @Test fun headline() = sheet { HeadlineBill(ui, onGo = {}) }
    @Test fun setlistAndResidency() = sheet { SetlistBill(ui); ResidencyBill(ui) }
    @Test fun recordsAndBackstage() = sheet { RecordsBill(ui); BackstageBill(ui) }
}
