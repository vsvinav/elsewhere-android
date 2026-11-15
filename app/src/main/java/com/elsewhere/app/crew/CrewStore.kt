package com.elsewhere.app.crew

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elsewhere.app.sonar.Debounce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** How long a share runs: an hour, to local midnight, or until the owner stops it. */
enum class ShareLength { HOUR, TONIGHT, UNTIL_STOP;
    fun until(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = when (this) {
        HOUR -> now + 3_600_000L
        TONIGHT -> LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zone).toInstant().toEpochMilli()
        UNTIL_STOP -> Long.MAX_VALUE
    }
}

@Serializable
data class ShareSession(val friendIds: Set<String>, val startedAt: Long, val until: Long) {
    fun active(now: Long) = now < until && friendIds.isNotEmpty()
    fun elapsedLabel(now: Long): String {
        val m = ((now - startedAt) / 60_000L).coerceAtLeast(0)
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }
}

/** Everything Crew keeps on the phone: my keys, my friends, the open code, the share window and Sonar's debounce. */
@Singleton
class CrewStore @Inject constructor(private val store: DataStore<Preferences>) {
    private object K {
        val keys = stringPreferencesKey("crew.keys")
        val friends = stringPreferencesKey("crew.friends")
        val code = stringPreferencesKey("crew.code")
        val share = stringPreferencesKey("crew.share")
        fun lastHit(pairId: String) = longPreferencesKey("sonar.lastHit.$pairId")
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val keysLock = Mutex()

    /** This phone's keys, minted on first use and never rotated. */
    suspend fun keys(): DeviceKeys = keysLock.withLock {
        store.data.first()[K.keys]?.let { runCatching { DeviceKeys.decode(it) }.getOrNull() }
            ?: DeviceKeys.generate().also { k -> store.edit { it[K.keys] = k.encode() } }
    }

    val friends: Flow<List<Friend>> = store.data.map { p -> decodeFriends(p[K.friends]) }
    suspend fun friendsNow(): List<Friend> = friends.first()
    suspend fun friend(deviceId: String): Friend? = friendsNow().firstOrNull { it.deviceId == deviceId }

    suspend fun addFriend(f: Friend) = update { list -> list.filterNot { it.deviceId == f.deviceId } + f }
    suspend fun removeFriend(deviceId: String) = update { list -> list.filterNot { it.deviceId == deviceId } }
    suspend fun setGrants(deviceId: String, grants: Grants) = update { list -> list.map { if (it.deviceId == deviceId) it.copy(grants = grants) else it } }
    suspend fun mute(deviceId: String, until: Long?) = update { list -> list.map { if (it.deviceId == deviceId) it.copy(mutedUntil = until) else it } }

    private suspend fun update(f: (List<Friend>) -> List<Friend>) = store.edit { p ->
        p[K.friends] = json.encodeToString(ListSerializer(Friend.serializer()), f(decodeFriends(p[K.friends])))
    }

    private fun decodeFriends(s: String?): List<Friend> =
        s?.let { runCatching { json.decodeFromString(ListSerializer(Friend.serializer()), it) }.getOrNull() } ?: emptyList()

    val code: Flow<ShareCode?> = store.data.map { p -> p[K.code]?.let { runCatching { json.decodeFromString(Stored.serializer(), it) }.getOrNull()?.let { s -> ShareCode(s.code, s.createdAt) } } }
    suspend fun saveCode(code: ShareCode?) = store.edit { p -> if (code == null) p.remove(K.code) else p[K.code] = json.encodeToString(Stored.serializer(), Stored(code.code, code.createdAt)) }
    @Serializable private data class Stored(val code: String, val createdAt: Long)

    val share: Flow<ShareSession?> = store.data.map { p -> p[K.share]?.let { runCatching { json.decodeFromString(ShareSession.serializer(), it) }.getOrNull() } }
    suspend fun shareNow(): ShareSession? = share.first()
    suspend fun startShare(s: ShareSession) = store.edit { it[K.share] = json.encodeToString(ShareSession.serializer(), s) }
    suspend fun stopShare() = store.edit { it.remove(K.share) }

    suspend fun debounce(pairIds: Collection<String>): Debounce {
        val p = store.data.first()
        return Debounce(pairIds.mapNotNull { id -> p[K.lastHit(id)]?.let { id to it } }.toMap())
    }
    suspend fun recordHit(pairId: String, at: Long) = store.edit { it[K.lastHit(pairId)] = at }

    /** A tombstone of our own: forget friends, the code, the share and the debounce, but keep the keys. */
    suspend fun clearFriends() = store.edit { p -> p.remove(K.friends); p.remove(K.share) }
}
