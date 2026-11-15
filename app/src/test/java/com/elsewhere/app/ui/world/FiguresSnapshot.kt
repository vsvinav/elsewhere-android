package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.analytics.Night
import com.elsewhere.app.analytics.NightKind
import com.elsewhere.app.ui.world.figures.Beds
import com.elsewhere.app.ui.world.figures.Dial
import com.elsewhere.app.ui.world.figures.Ring
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class FiguresSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private fun sheet(content: @Composable () -> Unit) = paparazzi.snapshot {
        ElsewhereTheme { Box(Modifier.background(Poster.Stock).padding(24.dp), contentAlignment = Alignment.TopCenter) { content() } }
    }

    @Test fun pass() = sheet {
        Pass(Modifier.fillMaxWidth()) {
            CapsText("World tour")
            Spacer(Modifier.height(12.dp))
            DisplayText("Elsewhere", 64.sp, misreg = true)
            Spacer(Modifier.height(16.dp))
            Sticker("Printed in Boston")
            Spacer(Modifier.height(16.dp))
            Stamp("Record\nverified", animate = false)
        }
    }

    @Test fun dial() = sheet {
        val hours = listOf(1, 0, 0, 0, 0, 2, 6, 14, 22, 12, 8, 9, 11, 7, 6, 8, 12, 17, 13, 8, 5, 3, 2, 1)
        Dial(hours, 7.5, 22 to 28, "07:30", Modifier.width(220.dp))
    }

    @Test fun beds() = sheet {
        val kinds = listOf(NightKind.HOME, NightKind.HOME, NightKind.HOME, NightKind.NEW, NightKind.NEW, NightKind.REPEAT, NightKind.HOME, NightKind.NONE)
        val nights = (0 until 60).map { i -> Night(LocalDate.of(2025, 3, 5).plusDays(i.toLong()).toString(), kinds[(i * 7 + i / 3) % kinds.size], null) }
        Beds(nights, Modifier.fillMaxWidth())
    }

    @Test fun ring() = sheet { Ring(3.0, "of nights away", Modifier.width(220.dp)) }
}
