package com.elsewhere.app.ui.account

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Rule
import org.junit.Test

class AccountSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private object NoOp : AccountActions {
        override fun begin(entry: AccountEntry) {}
        override fun sendCode(email: String) {}
        override fun resend() {}
        override fun submitCode(code: String) {}
        override fun setPassphrase(passphrase: String, confirm: String) {}
        override fun unlock(passphrase: String) {}
        override fun startOver() {}
        override fun back() {}
        override fun reset() {}
    }

    private fun shoot(ui: AccountUi, close: Boolean = true) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { AccountFlow(ui, NoOp, onClose = if (close) ({}) else null) } }
    }

    @Test fun welcome() = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { WelcomeScreen({}, {}, {}) } }
    }

    @Test fun email() = shoot(AccountUi(entry = AccountEntry.SignUp, step = AccountStep.Email), close = false)

    @Test fun code() = shoot(AccountUi(step = AccountStep.Code, email = "ana@example.com", error = "That code isn't right."))

    @Test fun setPassphrase() = shoot(AccountUi(entry = AccountEntry.SignUp, step = AccountStep.SetPassphrase, email = "ana@example.com"))

    @Test fun unlock() = shoot(AccountUi(step = AccountStep.Unlock, email = "ana@example.com", error = WRONG_PASSPHRASE))

    @Test fun update() = shoot(AccountUi(entry = AccountEntry.Update, step = AccountStep.Unlock))
}
