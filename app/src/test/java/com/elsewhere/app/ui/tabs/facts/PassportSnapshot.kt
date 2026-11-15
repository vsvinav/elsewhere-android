package com.elsewhere.app.ui.tabs.facts

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
import com.elsewhere.app.analytics.passport
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

class PassportSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")
    private val pass = passport(SampleRecord.record)

    private fun snap(content: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { Column(Modifier.fillMaxWidth().background(Poster.Stock).padding(12.dp)) { content() } } }
    }

    @Test fun country() = snap { PassportPage(pass.pages[0], 0, pass.pages.size) }
    @Test fun milestones() = snap { PassportPage(pass.pages.last(), pass.pages.size - 1, pass.pages.size) }
}
