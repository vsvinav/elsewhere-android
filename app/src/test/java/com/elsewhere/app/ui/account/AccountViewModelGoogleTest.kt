package com.elsewhere.app.ui.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.Fakes
import com.elsewhere.app.account.AccountException
import com.elsewhere.app.account.DriveLink
import com.elsewhere.app.account.VaultLocked
import com.elsewhere.app.auth.DriveAuthFailed
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

/** Robolectric only for a Context to hand the fake sheet; it never looks at it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountViewModelGoogleTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun up() = Dispatchers.setMain(dispatcher)
    @After fun down() = Dispatchers.resetMain()

    @Test fun `continue with Google takes the Drive key as the passphrase and is done`() = runTest(dispatcher) {
        val acct = Fakes.Account(hasVault = true); val google = Fakes.Google(); val drive = Fakes.Drive()
        val vm = AccountViewModel(acct, google, Fakes.linker(acct, drive))
        vm.begin(AccountEntry.LogIn)
        vm.google(context); advanceUntilIdle()
        assertEquals(1, google.asked)
        assertEquals(listOf("id.tok"), acct.googleTokens)
        assertEquals(1, drive.created)
        assertEquals(1, acct.driveKeys.size)
        assertTrue(vm.ui.value.done)
        assertEquals("google@example.com", acct.account.value?.session?.email)
        assertNull(vm.ui.value.error)
    }

    @Test fun `an old passphrase vault asks for the passphrase once more`() = runTest(dispatcher) {
        val acct = Fakes.Account(hasVault = true).apply { driveLink = DriveLink.NeedsPassphrase }
        val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.LogIn)
        vm.google(context); advanceUntilIdle()
        assertEquals(AccountStep.Unlock, vm.ui.value.step)
        assertFalse(vm.ui.value.done)
        vm.unlock("secret-pass"); advanceUntilIdle()
        assertEquals(listOf("secret-pass"), acct.restored)
        assertTrue(vm.ui.value.done)
    }

    @Test fun `when Drive is refused the passphrase pages take over, as after a code`() = runTest(dispatcher) {
        val acct = Fakes.Account(hasVault = false)
        val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct, Fakes.Drive(fail = DriveAuthFailed())))
        vm.begin(AccountEntry.SignUp)
        vm.google(context); advanceUntilIdle()
        assertEquals(AccountStep.SetPassphrase, vm.ui.value.step)
        assertNull(vm.ui.value.error)
        assertTrue(acct.driveKeys.isEmpty())
    }

    @Test fun `a vault under another Drive key still lets the user in`() = runTest(dispatcher) {
        val acct = Fakes.Account(hasVault = true).apply { driveFail = VaultLocked() }
        val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.LogIn)
        vm.google(context); advanceUntilIdle()
        assertTrue(vm.ui.value.done)
    }

    @Test fun `a dismissed sheet or a refused token prints the Google line and stays on the page`() = runTest(dispatcher) {
        val acct = Fakes.Account(); val google = Fakes.Google(fail = GoogleSignInFailed()); val vm = AccountViewModel(acct, google, Fakes.linker(acct))
        vm.begin(AccountEntry.SignUp)
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

    @Test fun `a service fault is the Google line, but a limiter or a dead network keep their own words`() = runTest(dispatcher) {
        val acct = Fakes.Account(); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.LogIn)
        acct.fail = AccountException(500, "boom")
        vm.google(context); advanceUntilIdle()
        assertEquals(GOOGLE_FAIL, vm.ui.value.error)
        acct.fail = AccountException(429, "slow down")
        vm.google(context); advanceUntilIdle()
        assertEquals("Too many tries. Give it an hour.", vm.ui.value.error)
        acct.fail = java.io.IOException("offline")
        vm.google(context); advanceUntilIdle()
        assertEquals("Couldn't reach the account service. Check your connection.", vm.ui.value.error)
        assertEquals(AccountStep.Email, vm.ui.value.step)
    }
}
