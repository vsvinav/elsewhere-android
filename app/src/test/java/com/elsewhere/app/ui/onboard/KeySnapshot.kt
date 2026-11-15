package com.elsewhere.app.ui.onboard

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.ingest.KeyReport
import com.elsewhere.app.ingest.Probe
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Rule
import org.junit.Test

class KeySnapshot {
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

    private fun shoot(state: OnboardState, sdk: Boolean = false) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { KeyScreen(state, NoOp, onCancel = null, sdk = sdk) } }
    }

    @Test fun checking() = shoot(OnboardState(step = Step.Key, key = "AIzaSyExample", keyPhase = KeyPhase.Checking))

    @Test fun accepted() = shoot(OnboardState(step = Step.Key, key = "AIzaSyExample", keyPhase = KeyPhase.Result, keyReport = KeyReport(Probe.Ok, Probe.Ok, sdk = false)))

    /** Skipping on an SDK build still gets the Google map, so the page only warns about names and photos. */
    @Test fun skippedWithSdk() = shoot(OnboardState(step = Step.Key, keyPhase = KeyPhase.Result, keyReport = null), sdk = true)

    /** With the Maps SDK built in, Map Tiles is marked not needed rather than failed, and only the Places refusal is explained. */
    @Test fun refusedWithSdk() = shoot(
        OnboardState(
            step = Step.Key, key = "AIzaSyExample", keyPhase = KeyPhase.Result,
            keyReport = KeyReport(
                Probe.Failed("API key not valid. Please pass a valid API key.", "INVALID_ARGUMENT", "The key was typed wrong or deleted."),
                Probe.Failed("Map Tiles API has not been used in project 1234 before or it is disabled.", "PERMISSION_DENIED", "Enable the Map Tiles API in Google Cloud → APIs & Services → Library."),
                sdk = true,
            ),
        ),
        sdk = true,
    )

    @Test fun refused() = shoot(
        OnboardState(
            step = Step.Key, key = "AIzaSyExample", keyPhase = KeyPhase.Result,
            keyReport = KeyReport(
                Probe.Failed(
                    "Requests from referer <empty> are blocked.", "PERMISSION_DENIED",
                    "This key is restricted to websites. In Google Cloud → Credentials → this key, set Application restrictions to None (or create a second key for the phone).",
                ),
                Probe.Ok,
                sdk = false,
            ),
        ),
    )
}
