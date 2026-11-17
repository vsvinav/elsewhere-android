package com.elsewhere.app.sync

import android.content.Context
import com.elsewhere.app.account.Accounts
import com.elsewhere.app.account.DriveLink
import com.elsewhere.app.account.Vault
import com.elsewhere.app.auth.DriveAuth
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** The key file in the user's Drive app folder; [DriveKey] is the real one, tests hand in a fake. */
interface DriveKeys {
    suspend fun fetchOrCreate(token: String): ByteArray
    suspend fun delete(token: String)
}

/** Drive access, the key file and the account, tied together: the two moves the screens make. */
@Singleton
class DriveLinker @Inject constructor(private val auth: DriveAuth, private val files: DriveKeys, private val accounts: Accounts) {
    /** After a Google login: the user's key from Drive (made if missing) becomes the account's key. */
    suspend fun link(activity: Context): DriveLink = accounts.useDriveKey(Vault.b64(files.fetchOrCreate(auth.accessToken(activity))))

    /** The key as Drive holds it now, made the account's key again; what sync does when a blob will not open. */
    suspend fun refresh(activity: Context): ByteArray {
        val k = files.fetchOrCreate(auth.accessToken(activity))
        accounts.useDriveKey(Vault.b64(k))
        return k
    }

    /** Delete account: the key file goes too, when Drive lets us; the account goes either way. */
    suspend fun deleteAccount(activity: Context) {
        if (accounts.account.filterNotNull().first().driveKey != null) runCatching { files.delete(auth.accessToken(activity)) }
        accounts.deleteAccount()
    }

    /** Start fresh: the old key file goes, a new one is made, and this phone's vault is re-sealed under it. */
    suspend fun replace(activity: Context) {
        val token = auth.accessToken(activity)
        files.delete(token)
        accounts.useDriveKey(Vault.b64(files.fetchOrCreate(token)), fresh = true)
    }
}
