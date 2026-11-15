package com.elsewhere.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.ui.Tab
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ShellSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private fun sheet(content: @Composable () -> Unit) = paparazzi.snapshot {
        ElsewhereTheme { Box(Modifier.background(Poster.Stock).padding(24.dp), contentAlignment = Alignment.TopCenter) { content() } }
    }

    @Test fun ticketStrip() = sheet { TicketStrip(selected = Tab.FACTS, onSelect = {}, onBrand = {}) }

    @Test fun rangeBar() = sheet {
        RangeBar(LocalDate.of(2024, 5, 12), LocalDate.of(2026, 11, 13), listOf(2024, 2025, 2026), { it == Preset.All }, {}, {}, {})
    }

    @Test fun calendar() = sheet {
        Calendar(
            value = LocalDate.of(2026, 11, 13), min = LocalDate.of(2024, 3, 1), max = LocalDate.of(2026, 11, 13),
            other = LocalDate.of(2026, 11, 1), onPick = {},
        )
    }
}
