package com.elsewhere.app.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.Fakes
import com.elsewhere.app.account.AccountState
import com.elsewhere.app.account.Session
import com.elsewhere.app.auth.DriveAuthFailed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveLinkerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val google = AccountState(session = Session("s", "acct", "a@b.c"), driveKey = "a2V5", welcomed = true)

    @Test fun `deleting the account takes the Drive key file with it`() = runTest {
        val acct = Fakes.Account(google); val drive = Fakes.Drive()
        Fakes.linker(acct, drive).deleteAccount(ctx)
        assertEquals(1, drive.deletes)
        assertEquals(1, acct.deletes)
        assertNull(acct.account.value!!.session)
    }

    @Test fun `deleting waits for the account to have been read before deciding about Drive`() = runTest {
        val acct = Fakes.Account(google); val drive = Fakes.Drive()
        acct.account.value = null
        launch { delay(50); acct.account.value = google }
        Fakes.linker(acct, drive).deleteAccount(ctx)
        assertEquals(1, drive.deletes)
        assertEquals(1, acct.deletes)
    }

    @Test fun `the account still goes when Drive will not answer, and an email account never asks Drive`() = runTest {
        val acct = Fakes.Account(google); val drive = Fakes.Drive(fail = DriveAuthFailed())
        Fakes.linker(acct, drive).deleteAccount(ctx)
        assertEquals(0, drive.deletes)
        assertEquals(1, acct.deletes)

        val email = Fakes.Account(google.copy(driveKey = null)); val drive2 = Fakes.Drive()
        Fakes.linker(email, drive2).deleteAccount(ctx)
        assertEquals(0, drive2.deletes)
        assertEquals(1, email.deletes)
    }
}
