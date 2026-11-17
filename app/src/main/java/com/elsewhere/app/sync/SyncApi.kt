package com.elsewhere.app.sync

import kotlinx.serialization.Serializable

/** One row of `GET /sync`: a key the worker holds, when its blob was sealed and how big it is. */
@Serializable
data class SyncEntry(val key: String, val ts: Long, val size: Int = 0)

/** The worker's sync routes, behind the account session; [com.elsewhere.app.account.AccountClient] is the real one. */
interface SyncApi {
    suspend fun list(session: String): List<SyncEntry>
    /** Null when the worker has nothing under `key`. */
    suspend fun get(session: String, key: String): SyncBlob?
    /** 409 `stale` (an [com.elsewhere.app.account.AccountException]) when the worker holds a newer `ts`. */
    suspend fun put(session: String, key: String, blob: SyncBlob)
    suspend fun deleteAll(session: String)
}
