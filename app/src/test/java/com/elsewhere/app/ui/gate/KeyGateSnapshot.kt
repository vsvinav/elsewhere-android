package com.elsewhere.app.ui.gate

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.ingest.KeyReport
import com.elsewhere.app.ingest.Probe
import com.elsewhere.app.ui.onboard.KeyActions
import com.elsewhere.app.ui.onboard.KeyPhase
import com.elsewhere.app.ui.onboard.OnboardState
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Rule
import org.junit.Test

class KeyGateSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private object NoOp : KeyActions {
        override fun saveKey(key: String, mapId: String) {}
        override fun skipKey() {}
        override fun recheckKey() {}
        override fun editKey() {}
        override fun setLimitedAck(on: Boolean) {}
        override fun continueKey() {}
        override fun acceptLimited() {}
    }

    private fun shoot(state: OnboardState) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { KeyGateScreen(state, NoOp) } }
    }

    @Test fun busy() = shoot(OnboardState(key = "AIzaSyExample", keyPhase = KeyPhase.Checking))

    @Test fun refused() = shoot(
        OnboardState(
            key = "AIzaSyExample", keyPhase = KeyPhase.Result,
            keyReport = KeyReport(Probe.Failed("API key not valid. Please pass a valid API key.", "INVALID_ARGUMENT", "The key was typed wrong or deleted."), Probe.Ok),
        ),
    )
}
