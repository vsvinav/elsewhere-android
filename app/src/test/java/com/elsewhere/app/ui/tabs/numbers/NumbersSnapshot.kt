package com.elsewhere.app.ui.tabs.numbers

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Rule
import org.junit.Test

class NumbersSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun numbers() = paparazzi.snapshot {
        val ui = numbersUi(SampleRecord.record, SampleRecord.profile)
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { NumbersScreen(ui) } }
    }
}
