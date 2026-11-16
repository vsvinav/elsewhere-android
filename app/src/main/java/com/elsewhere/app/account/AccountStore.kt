package com.elsewhere.app.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The bearer token and who it belongs to. */
data class Session(val session: String, val id: String, val email: String)

/**
 * Everything the phone remembers about the account. `vaultAt` is when the vault was last uploaded and `vaultHash`
 * what it held, so a restart can tell whether the vault is behind; the passphrase key itself never touches disk.
 */
data class AccountState(val session: Session? = null, val salt: String? = null, val vaultAt: Long = 0L, val vaultHash: String? = null, val welcomed: Boolean = false)

/** What the account layer reads and writes; the DataStore one is real, tests keep theirs in memory. */
interface AccountStorage {
    val state: Flow<AccountState>
    suspend fun saveSession(s: Session)
    suspend fun saveSalt(salt: String?)
    suspend fun saveVault(at: Long, hash: String?)
    suspend fun markWelcomed()
    /** Sign out: forget the session, the salt and the vault stamp, but stay welcomed. */
    suspend fun clearSession()
}

@Singleton
class AccountStore @Inject constructor(private val store: DataStore<Preferences>) : AccountStorage {
    private object K {
        val session = stringPreferencesKey("account.session")
        val id = stringPreferencesKey("account.id")
        val email = stringPreferencesKey("account.email")
        val salt = stringPreferencesKey("account.salt")
        val vaultAt = longPreferencesKey("account.vaultAt")
        val vaultHash = stringPreferencesKey("account.vaultHash")
        val welcomed = booleanPreferencesKey("account.welcomed")
    }

    override val state: Flow<AccountState> = store.data.map { p ->
        val tok = p[K.session]; val id = p[K.id]; val email = p[K.email]
        AccountState(
            session = if (tok != null && id != null && email != null) Session(tok, id, email) else null,
            salt = p[K.salt], vaultAt = p[K.vaultAt] ?: 0L, vaultHash = p[K.vaultHash], welcomed = p[K.welcomed] ?: false,
        )
    }

    override suspend fun saveSession(s: Session) { store.edit { it[K.session] = s.session; it[K.id] = s.id; it[K.email] = s.email } }
    override suspend fun saveSalt(salt: String?) { store.edit { if (salt == null) it.remove(K.salt) else it[K.salt] = salt } }
    override suspend fun saveVault(at: Long, hash: String?) { store.edit { it[K.vaultAt] = at; if (hash == null) it.remove(K.vaultHash) else it[K.vaultHash] = hash } }
    override suspend fun markWelcomed() { store.edit { it[K.welcomed] = true } }
    override suspend fun clearSession() {
        store.edit { it.remove(K.session); it.remove(K.id); it.remove(K.email); it.remove(K.salt); it.remove(K.vaultAt); it.remove(K.vaultHash) }
    }
}
