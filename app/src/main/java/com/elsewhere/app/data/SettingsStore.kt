package com.elsewhere.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elsewhere.app.ingest.AddressBook
import com.elsewhere.app.ingest.CostMeter
import com.elsewhere.app.ingest.Kind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val mapsKey: String = "", val mapId: String = "", val mapProvider: MapProvider = MapProvider.AUTO,
    val googleRejected: Boolean = false,
    /** Sessions in which Google failed to come up on this key; two in a row persist `googleRejected`. */
    val googleFailures: Int = 0,
    /** What the last key check found, and when (epoch ms; 0 = never checked). */
    val keyPlaces: Boolean = false, val keyTiles: Boolean = false, val keyCheckedAt: Long = 0,
    /** The user has read and accepted "limited maps": free tiles, no place names or photos. */
    val limitedAccepted: Boolean = false,
)

/** Billable Google calls made from this phone since `since` (epoch ms). */
data class Counters(val detailsCalls: Int = 0, val photoCalls: Int = 0, val mapLoads: Int = 0, val since: Long = 0)

@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) : MapsSettings, CostMeter, AddressBook {
    private object K {
        val profile = stringPreferencesKey("profile")
        val mapsKey = stringPreferencesKey("mapsKey")
        val mapId = stringPreferencesKey("mapId")
        val mapProvider = stringPreferencesKey("mapProvider")
        val googleRejected = booleanPreferencesKey("googleRejected")
        val googleFailures = intPreferencesKey("googleFailures")
        val keyPlaces = booleanPreferencesKey("keyPlaces")
        val keyTiles = booleanPreferencesKey("keyTiles")
        val keyCheckedAt = longPreferencesKey("keyCheckedAt")
        val limitedAccepted = booleanPreferencesKey("limitedAccepted")
        val detailsCalls = intPreferencesKey("detailsCalls")
        val photoCalls = intPreferencesKey("photoCalls")
        val mapLoads = intPreferencesKey("mapLoads")
        val usageSince = longPreferencesKey("usageSince")
        val addresses = stringPreferencesKey("addresses")
    }
    private fun keyOf(kind: Kind) = when (kind) { Kind.DETAILS -> K.detailsCalls; Kind.PHOTOS -> K.photoCalls; Kind.MAPS -> K.mapLoads }

    private val json = Json { ignoreUnknownKeys = true }

    val profile: Flow<Profile?> = store.data.map { p ->
        p[K.profile]?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() }
    }

    override val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            mapsKey = p[K.mapsKey] ?: "", mapId = p[K.mapId] ?: "",
            mapProvider = p[K.mapProvider]?.let { runCatching { MapProvider.valueOf(it) }.getOrNull() } ?: MapProvider.AUTO,
            googleRejected = p[K.googleRejected] ?: false, googleFailures = p[K.googleFailures] ?: 0,
            keyPlaces = p[K.keyPlaces] ?: false, keyTiles = p[K.keyTiles] ?: false, keyCheckedAt = p[K.keyCheckedAt] ?: 0,
            limitedAccepted = p[K.limitedAccepted] ?: false,
        )
    }

    override val counters: Flow<Counters> = store.data.map { p ->
        Counters(p[K.detailsCalls] ?: 0, p[K.photoCalls] ?: 0, p[K.mapLoads] ?: 0, p[K.usageSince] ?: 0)
    }

    suspend fun saveProfile(profile: Profile?) {
        store.edit { if (profile == null) it.remove(K.profile) else it[K.profile] = json.encodeToString(Profile.serializer(), profile) }
    }

    override suspend fun saveSettings(s: Settings) {
        store.edit {
            it[K.mapsKey] = s.mapsKey; it[K.mapId] = s.mapId
            it[K.mapProvider] = s.mapProvider.name; it[K.googleRejected] = s.googleRejected; it[K.googleFailures] = s.googleFailures
            it[K.keyPlaces] = s.keyPlaces; it[K.keyTiles] = s.keyTiles; it[K.keyCheckedAt] = s.keyCheckedAt; it[K.limitedAccepted] = s.limitedAccepted
        }
    }

    override suspend fun tally(kind: Kind, n: Int) {
        store.edit { val k = keyOf(kind); it[k] = (it[k] ?: 0) + n; if (it[K.usageSince] == null) it[K.usageSince] = System.currentTimeMillis() }
    }

    override suspend fun reset() {
        store.edit { it.remove(K.detailsCalls); it.remove(K.photoCalls); it.remove(K.mapLoads); it[K.usageSince] = System.currentTimeMillis() }
    }

    private val addressBook = MapSerializer(String.serializer(), String.serializer().nullable)
    override suspend fun load(): Map<String, String?> =
        store.data.first()[K.addresses]?.let { runCatching { json.decodeFromString(addressBook, it) }.getOrNull() } ?: emptyMap()
    override suspend fun save(book: Map<String, String?>) { store.edit { it[K.addresses] = json.encodeToString(addressBook, book) } }

    suspend fun clearAll() { store.edit { it.clear() } }
}
