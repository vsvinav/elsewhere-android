package com.elsewhere.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.account.AccountState
import com.elsewhere.app.account.Session
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Welcome comes before the front door, once, and only for a phone with no record and no session. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RootWelcomeTest {
    @get:Rule val rule = createComposeRule()

    private fun show(acct: Fakes.Account) {
        val store = Fakes.Store(Fx.record()); val maps = Fakes.Maps()
        rule.setContent {
            val root = remember { RootViewModel(store, Fakes.Land, maps, acct) }
            ElsewhereTheme {
                Root(
                    vm = root,
                    door = { _, _, _ -> Text("THE DOOR") },
                    account = { entry, done, close ->
                        Column { Text("FLOW $entry"); Text("FINISH", Modifier.clickable(onClick = done)); Text("CLOSE", Modifier.clickable(onClick = close)) }
                    },
                )
            }
        }
    }

    @Test fun `a fresh phone meets Welcome and can continue without an account`() {
        val acct = Fakes.Account(AccountState())
        show(acct)
        rule.onNodeWithText(com.elsewhere.app.ui.account.WELCOME_LINE).assertIsDisplayed()
        rule.onNodeWithText("CONTINUE WITHOUT AN ACCOUNT").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("THE DOOR").assertIsDisplayed()
        assertTrue(acct.account.value!!.welcomed)
    }

    @Test fun `Sign up opens the flow and finishing it lands on the door`() {
        show(Fakes.Account(AccountState()))
        rule.onNodeWithText("SIGN UP").performClick()
        rule.onNodeWithText("FLOW SignUp").assertIsDisplayed()
        rule.onNodeWithText("FINISH").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("THE DOOR").assertIsDisplayed()
    }

    @Test fun `Log in can be cancelled back to Welcome`() {
        show(Fakes.Account(AccountState()))
        rule.onNodeWithText("LOG IN").performClick()
        rule.onNodeWithText("FLOW LogIn").assertIsDisplayed()
        rule.onNodeWithText("CLOSE").performClick()
        rule.onNodeWithText(com.elsewhere.app.ui.account.WELCOME_LINE).assertIsDisplayed()
    }

    @Test fun `a phone that already has a session skips Welcome`() {
        show(Fakes.Account(AccountState(session = Session("s", "acct_1", "ana@example.com"))))
        rule.onNodeWithText("THE DOOR").assertIsDisplayed()
    }

    @Test fun `a phone that already said no skips Welcome`() {
        show(Fakes.Account(AccountState(welcomed = true)))
        rule.onNodeWithText("THE DOOR").assertIsDisplayed()
    }
}
