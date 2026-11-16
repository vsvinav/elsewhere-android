package com.elsewhere.app.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.account.Session
import com.elsewhere.app.account.VaultState
import com.elsewhere.app.ask.LlmConfig
import com.elsewhere.app.ask.LlmProvider
import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.Settings
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Rule
import org.junit.Test

class SettingsSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 3900), theme = "android:Theme.Material.Light.NoActionBar")

    private object NoOp : SettingsActions {
        override fun saveKey(key: String, mapId: String) {}
        override fun setMapProvider(p: MapProvider) {}
        override fun setLlm(c: LlmConfig) {}
        override fun testLlm(c: LlmConfig) {}
        override fun setUnits(u: Units) {}
        override fun setRelayUrl(url: String) {}
        override fun setAccountUrl(url: String) {}
        override fun logOut() {}
        override fun deleteAccount() {}
    }

    private fun shoot(ui: SettingsUi) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { SettingsScreen(ui, NoOp, onBack = {}, scroll = false, sdk = false) } }
    }

    @Test fun openai() = shoot(
        SettingsUi(
            Settings(mapsKey = "AIzaSyExample", keyPlaces = true, keyTiles = true, keyCheckedAt = 5),
            LlmConfig(LlmProvider.OPENAI, key = "sk-example"), Units.KM, test = LlmTest.Ok("gpt-4o-mini"),
        ),
    )

    /** Signed in with a stale vault: the email, the red standing and its Update, Log out and Delete. */
    @Test fun signedIn() = shoot(
        SettingsUi(
            Settings(), LlmConfig(LlmProvider.OPENAI, key = "sk-example"), Units.KM,
            account = AccountUi(Session("s", "acct_1", "ana@example.com"), VaultState.OutOfDate),
        ),
    )

    @Test fun custom() = shoot(
        SettingsUi(Settings(), LlmConfig(LlmProvider.CUSTOM, baseUrl = "http://localhost:11434/v1"), Units.MI, test = LlmTest.Failed("HTTP 401")),
    )
}
