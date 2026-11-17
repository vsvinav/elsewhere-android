package com.elsewhere.app.ui.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.Fakes
import com.elsewhere.app.account.AccountException
import com.elsewhere.app.auth.GoogleSignInFailed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric only because the Credential Manager sheet wants an Activity-ish Context; the fakes never look at it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountViewModelGoogleTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun up() = Dispatchers.setMain(dispatcher)
    @After fun down() = Dispatchers.resetMain()

    @Test fun `continue with Google lands on the vault step like a finished code`() = runTest(dispatcher) {
        val acct = Fakes.Account(hasVault = true); val google = Fakes.Google(); val vm = AccountViewModel(acct, google)
        assertTrue(vm.begin(AccountEntry.Google))
        vm.google(context); advanceUntilIdle()
        assertEquals(1, google.asked)
        assertEquals(listOf("id.tok"), acct.googleTokens)
        assertEquals(AccountStep.Unlock, vm.ui.value.step)
        assertEquals("google@example.com", acct.account.value?.session?.email)
        assertNull(vm.ui.value.error)
    }

    @Test fun `a dismissed sheet or a refused token prints the Google line and stays on the page`() = runTest(dispatcher) {
        val acct = Fakes.Account(); val google = Fakes.Google(fail = GoogleSignInFailed()); val vm = AccountViewModel(acct, google)
        vm.begin(AccountEntry.Google)
        vm.google(context); advanceUntilIdle()
        assertEquals(GOOGLE_FAIL, vm.ui.value.error)
        assertEquals(AccountStep.Email, vm.ui.value.step)
        assertTrue(acct.googleTokens.isEmpty())

        google.fail = null; acct.fail = AccountException(401, "bad token")
        vm.google(context); advanceUntilIdle()
        assertEquals(GOOGLE_FAIL, vm.ui.value.error)
        assertFalse(vm.ui.value.busy)
        assertFalse(vm.ui.value.done)
    }
}
