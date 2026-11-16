package com.elsewhere.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.data.Recording
import com.elsewhere.app.record.RecordStats
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

class RecordSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")
    private val now = 1_800_000_000L

    private fun sheet(content: @Composable () -> Unit) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { Column(Modifier.background(Poster.Stock)) { content() } } }
    }

    private fun rows(vararg states: PermState) = Perm.entries.zip(states).map { (p, s) -> PermRow(p, s) }

    @Test fun disclosure() = sheet {
        val ui = RecordUi(rows(PermState.ASK, PermState.LOCKED, PermState.ASK, PermState.ASK), false, Recording(), null, true, now)
        RecordScreen(ui, RecordActions(), scroll = false)
    }

    /** The door's first page: location allowed, the rest still to grant, battery unrestricted not yet. */
    @Test fun onboardRecord() = sheet {
        val ui = RecordUi(rows(PermState.GRANTED, PermState.SETTINGS, PermState.ASK, PermState.ASK), false, Recording(), null, batteryOk = false, now = now)
        RecordStepScreen(ui, RecordActions(), onImport = {})
    }

    @Test fun off() = sheet {
        val ui = RecordUi(rows(PermState.GRANTED, PermState.GRANTED, PermState.GRANTED, PermState.GRANTED), true, Recording(), RecordStats(1_204, 7, now - 3 * 3600), true, now)
        RecordScreen(ui, RecordActions(), scroll = false)
    }

    @Test fun recording() = sheet {
        val ui = RecordUi(
            rows(PermState.GRANTED, PermState.GRANTED, PermState.GRANTED, PermState.SETTINGS), true,
            Recording(true, now - 12 * 86400, 0L), RecordStats(3_412, 58, now - 180), batteryOk = false, now = now,
        )
        RecordScreen(ui, RecordActions(), scroll = false, highlightsAt = 8 * 60)
    }

    @Test fun highlightsOff() = sheet { HighlightsBill(null, {}) }

    @Test fun highlightsOn() = sheet { HighlightsBill(21 * 60 + 15, {}) }
}
