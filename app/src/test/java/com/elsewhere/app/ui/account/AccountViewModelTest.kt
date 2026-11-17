package com.elsewhere.app.ui.account

import com.elsewhere.app.Fakes
import com.elsewhere.app.account.AccountException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()

    @Test fun `sign up walks email, code, then a new passphrase`() = runTest(dispatcher) {
        val acct = Fakes.Account(); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.SignUp)
        vm.sendCode("  Ana@Example.COM ")
        advanceUntilIdle()
        assertEquals(listOf("Ana@Example.COM"), acct.started)
        assertEquals(AccountStep.Code, vm.ui.value.step)
        assertEquals("ana@example.com", vm.ui.value.email)

        vm.submitCode("12345")            // too short: ignored
        advanceUntilIdle()
        assertTrue(acct.codes.isEmpty())
        vm.submitCode("123456")
        advanceUntilIdle()
        assertEquals(AccountStep.SetPassphrase, vm.ui.value.step)

        vm.setPassphrase("eleven char", "eleven char")
        assertEquals(PASSPHRASE_TOO_SHORT, vm.ui.value.error)
        vm.setPassphrase("password1234", "password1234")
        assertEquals(PASSPHRASE_TOO_EASY, vm.ui.value.error)
        vm.setPassphrase("long enough now", "long enuff now")
        assertEquals(PASSPHRASES_DIFFER, vm.ui.value.error)
        vm.setPassphrase("long enough now", "long enough now")
        advanceUntilIdle()
        assertEquals(listOf("long enough now"), acct.passphrasesSet)
        assertTrue(vm.ui.value.done)
    }

    @Test fun `logging in to an account with a vault asks to unlock it, and a wrong passphrase says so`() = runTest(dispatcher) {
        val acct = Fakes.Account(hasVault = true); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.LogIn)
        vm.sendCode("ana@example.com"); advanceUntilIdle()
        vm.submitCode("654321"); advanceUntilIdle()
        assertEquals(AccountStep.Unlock, vm.ui.value.step)
        vm.unlock("nope nope"); advanceUntilIdle()
        assertEquals(WRONG_PASSPHRASE, vm.ui.value.error)
        assertFalse(vm.ui.value.done)
        vm.unlock("secret-pass"); advanceUntilIdle()
        assertEquals(listOf("secret-pass"), acct.restored)
        assertTrue(vm.ui.value.done)
    }

    @Test fun `begin is idempotent for the same entry until the flow is done`() = runTest(dispatcher) {
        val acct = Fakes.Account(); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.LogIn)
        vm.sendCode("ana@example.com"); advanceUntilIdle()
        vm.begin(AccountEntry.LogIn)
        assertEquals(AccountStep.Code, vm.ui.value.step)
        vm.begin(AccountEntry.Update)                 // a different entry does start afresh
        assertEquals(AccountStep.Unlock, vm.ui.value.step)
    }

    @Test fun `cancelling resets the flow so the next begin starts at Email without stale state`() = runTest(dispatcher) {
        val acct = Fakes.Account(); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.LogIn)
        vm.sendCode("ana@example.com"); advanceUntilIdle()
        acct.fail = AccountException(400, "wrong code")
        vm.submitCode("000000"); advanceUntilIdle()
        assertEquals(AccountStep.Code, vm.ui.value.step)
        vm.reset()
        vm.begin(AccountEntry.LogIn)
        assertEquals(AccountStep.Email, vm.ui.value.step)
        assertNull(vm.ui.value.error)
        assertEquals("", vm.ui.value.email)
    }

    @Test fun `a rate-limited finish prints the same copy as a rate-limited start`() = runTest(dispatcher) {
        val acct = Fakes.Account(); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.LogIn)
        vm.sendCode("ana@example.com"); advanceUntilIdle()
        acct.fail = AccountException(429, "slow down")
        vm.submitCode("123456"); advanceUntilIdle()
        assertEquals("Too many tries. Give it an hour.", vm.ui.value.error)
        assertEquals(AccountStep.Code, vm.ui.value.step)
    }

    @Test fun `start over keeps the session and leaves the vault alone`() = runTest(dispatcher) {
        val acct = Fakes.Account(hasVault = true); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.LogIn)
        vm.sendCode("ana@example.com"); advanceUntilIdle()
        vm.submitCode("654321"); advanceUntilIdle()
        vm.startOver()
        assertTrue(vm.ui.value.done)
        assertTrue(acct.restored.isEmpty())
        assertTrue(acct.account.value?.session != null)
    }

    @Test fun `update from settings goes straight to the passphrase and re-sends the vault`() = runTest(dispatcher) {
        val acct = Fakes.Account(); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.Update)
        assertEquals(AccountStep.Unlock, vm.ui.value.step)
        vm.unlock("secret-pass"); advanceUntilIdle()
        assertEquals(listOf("secret-pass"), acct.unlocked)
        assertTrue(vm.ui.value.done)
    }

    @Test fun `service errors print the parity copy and a bad email never leaves the phone`() = runTest(dispatcher) {
        val acct = Fakes.Account(); val vm = AccountViewModel(acct, Fakes.Google(), Fakes.linker(acct))
        vm.begin(AccountEntry.LogIn)
        vm.sendCode("not an email"); advanceUntilIdle()
        assertEquals(NOT_AN_EMAIL, vm.ui.value.error)
        assertTrue(acct.started.isEmpty())

        acct.fail = AccountException(429, "slow down")
        vm.sendCode("ana@example.com"); advanceUntilIdle()
        assertEquals("Too many tries. Give it an hour.", vm.ui.value.error)
        assertEquals(AccountStep.Email, vm.ui.value.step)

        acct.fail = null
        vm.sendCode("ana@example.com"); advanceUntilIdle()
        assertNull(vm.ui.value.error)
        acct.fail = AccountException(400, "wrong code")
        vm.submitCode("000000"); advanceUntilIdle()
        assertEquals("That code isn't right.", vm.ui.value.error)
        acct.fail = AccountException(400, "expired")
        vm.submitCode("000000"); advanceUntilIdle()
        assertEquals("That code expired. Send a new one.", vm.ui.value.error)

        acct.fail = null
        vm.resend(); advanceUntilIdle()
        assertTrue(vm.ui.value.resent)
        assertEquals(2, acct.started.size)
        vm.back()
        assertEquals(AccountStep.Email, vm.ui.value.step)
        assertFalse(vm.ui.value.resent)
    }
}
