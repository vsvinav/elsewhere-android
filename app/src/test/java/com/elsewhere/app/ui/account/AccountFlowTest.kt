package com.elsewhere.app.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.elsewhere.app.account.AccountException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.Fakes
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AccountFlowTest {
    @get:Rule val rule = createComposeRule()

    private var done = 0
    private var closed = 0

    /** The tag sits on the printed field; the editable node is inside it. */
    private fun field(tag: String) = rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun show(acct: Fakes.Account, entry: AccountEntry, google: Fakes.Google = Fakes.Google(), googleClientId: String = "") {
        rule.setContent {
            val vm = remember { AccountViewModel(acct, google, Fakes.linker(acct)) }
            val ui by vm.ui.collectAsStateWithLifecycle()
            remember { vm.begin(entry); true }
            if (ui.done) done++
            ElsewhereTheme { AccountFlow(ui, vm, onClose = { closed++ }, googleClientId = googleClientId) }
        }
    }

    @Test fun `with a client id the email page offers Google first and a token closes the flow with no passphrase`() {
        val acct = Fakes.Account(); val google = Fakes.Google()
        show(acct, AccountEntry.LogIn, google, googleClientId = "123.apps.googleusercontent.com")
        rule.onNodeWithText(GOOGLE_BUTTON.uppercase()).performClick()
        rule.waitForIdle()
        assertEquals(1, google.asked)
        assertEquals(listOf("id.tok"), acct.googleTokens)
        assertEquals(1, acct.driveKeys.size)
        assertEquals(1, done)
    }

    @Test fun `without a client id the email page has no Google button`() {
        show(Fakes.Account(), AccountEntry.LogIn)
        rule.onNodeWithText(GOOGLE_BUTTON.uppercase()).assertDoesNotExist()
        field("account.email").assertIsDisplayed()
    }

    @Test fun `six digits submit on their own and a new account sets a passphrase`() {
        val acct = Fakes.Account()
        show(acct, AccountEntry.SignUp)
        rule.onNodeWithText("CONTINUE").assertIsNotEnabled()
        field("account.email").performTextInput("ana@example.com")
        rule.onNodeWithText("CONTINUE").performClick()
        rule.onNodeWithText("We sent a code to ana@example.com").assertIsDisplayed()
        rule.onNodeWithText(MAIL_LINK_NOTE).assertIsDisplayed()

        rule.onNodeWithTag("account.code").performTextInput("123456")
        rule.waitForIdle()
        assertEquals(listOf("123456"), acct.codes)
        rule.onNodeWithText("Set a recovery passphrase".uppercase()).assertIsDisplayed()
        rule.onNodeWithText("It locks your vault. $PASSPHRASE_NOTE").assertIsDisplayed()

        field("account.passphrase").performTextInput("correct horse")
        field("account.confirm").performTextInput("correct horse")
        rule.onNodeWithText("SAVE PASSPHRASE").performClick()
        rule.waitForIdle()
        assertEquals(listOf("correct horse"), acct.passphrasesSet)
        assertEquals(1, done)
    }

    @Test fun `a wrong passphrase says That's not it and start over leaves without the vault`() {
        val acct = Fakes.Account(hasVault = true)
        show(acct, AccountEntry.LogIn)
        field("account.email").performTextInput("ana@example.com")
        rule.onNodeWithText("CONTINUE").performClick()
        rule.onNodeWithTag("account.code").performTextInput("654321")
        rule.waitForIdle()
        rule.onNodeWithText("ENTER YOUR RECOVERY PASSPHRASE").assertIsDisplayed()
        field("account.passphrase").performTextInput("wrong one")
        rule.onNodeWithText("RESTORE").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(WRONG_PASSPHRASE).assertIsDisplayed()
        rule.onNodeWithText(START_OVER.uppercase()).performClick()
        rule.waitForIdle()
        assertEquals(0, acct.restored.size)
        assertEquals(1, done)
    }

    /** The Activity comes back after a rotation with the same view model; the page it was on must still be there. */
    @Test fun `recreating the activity at the Code step stays on the Code step`() {
        val acct = Fakes.Account(); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        val restorer = StateRestorationTester(rule)
        restorer.setContent { ElsewhereTheme { AccountRoute(AccountEntry.LogIn, onDone = { done++ }, onClose = null, vm = vm) } }
        field("account.email").performTextInput("ana@example.com")
        rule.onNodeWithText("CONTINUE").performClick()
        rule.onNodeWithText("We sent a code to ana@example.com").assertIsDisplayed()
        restorer.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("We sent a code to ana@example.com").assertIsDisplayed()
        assertEquals(listOf("ana@example.com"), acct.started)
    }

    @Test fun `resend is always offered and a refusal prints the rate-limit copy`() {
        val acct = Fakes.Account()
        show(acct, AccountEntry.LogIn)
        field("account.email").performTextInput("ana@example.com")
        rule.onNodeWithText("CONTINUE").performClick()
        rule.onNodeWithText("RESEND CODE").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("SENT AGAIN").assertIsEnabled()
        acct.fail = AccountException(429, "slow down")
        rule.onNodeWithText("SENT AGAIN").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Too many tries. Give it an hour.").assertIsDisplayed()
    }

    @Test fun `cancel closes the flow`() {
        show(Fakes.Account(), AccountEntry.LogIn)
        rule.onNodeWithText("CANCEL").performClick()
        assertEquals(1, closed)
    }
}
