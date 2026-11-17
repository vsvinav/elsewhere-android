package com.elsewhere.app.ui.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class WelcomeScreenTest {
    @get:Rule val rule = createComposeRule()

    private var signUps = 0; private var logIns = 0; private var skips = 0; private var googles = 0

    private fun show(welcomed: Boolean, googleClientId: String = "") {
        rule.setContent { ElsewhereTheme { WelcomeScreen({ signUps++ }, { logIns++ }, { skips++ }, onGoogle = { googles++ }, welcomed = welcomed, googleClientId = googleClientId) } }
    }

    @Test fun `a first visit prints the tagline and three ways on`() {
        show(welcomed = false)
        rule.onNodeWithText(WELCOME_LINE).assertIsDisplayed()
        rule.onNodeWithText("SIGN UP").performClick()
        rule.onNodeWithText("LOG IN").performClick()
        rule.onNodeWithText("CONTINUE WITHOUT AN ACCOUNT").performClick()
        assertEquals(listOf(1, 1, 1), listOf(signUps, logIns, skips))
    }

    @Test fun `a return visit offers Not now instead`() {
        show(welcomed = true)
        rule.onNodeWithText("NOT NOW").performClick()
        assertEquals(1, skips)
    }

    @Test fun `with a Google client id the Google way in sits above the others`() {
        show(welcomed = false, googleClientId = "123.apps.googleusercontent.com")
        rule.onNodeWithText(GOOGLE_BUTTON.uppercase()).performClick()
        assertEquals(1, googles)
    }

    @Test fun `without a client id there is no Google button`() {
        show(welcomed = false)
        rule.onNodeWithText(GOOGLE_BUTTON.uppercase()).assertDoesNotExist()
    }
}
