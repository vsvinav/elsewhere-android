package com.elsewhere.app.ui.onboard

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Rule
import org.junit.Test

/** The week at the door: three days inked, the fourth in red, the recorder running. */
class WaitingSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun waitingDay3() = paparazzi.snapshot {
        ElsewhereTheme { WaitingScreen(WaitingState(days = 3, recording = true, fixesToday = 212), {}, {}, {}) }
    }
}
