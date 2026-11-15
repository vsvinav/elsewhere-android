package com.elsewhere.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val mapsKey: String = "", val mapId: String = "", val mapProvider: MapProvider = MapProvider.AUTO,
    val googleRejected: Boolean = false,
)

data class Counters(val detailsCalls: Int = 0, val photoCalls: Int = 0, val mapLoads: Int = 0)

@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) : MapsSettings {
    private object K {
        val profile = stringPreferencesKey("profile")
        val mapsKey = stringPreferencesKey("mapsKey")
        val mapId = stringPreferencesKey("mapId")
        val mapProvider = stringPreferencesKey("mapProvider")
        val googleRejected = booleanPreferencesKey("googleRejected")
        val detailsCalls = intPreferencesKey("detailsCalls")
        val photoCalls = intPreferencesKey("photoCalls")
        val mapLoads = intPreferencesKey("mapLoads")
    }
    enum class Counter(internal val key: Preferences.Key<Int>) { DETAILS(K.detailsCalls), PHOTO(K.photoCalls), MAP_LOADS(K.mapLoads) }

    private val json = Json { ignoreUnknownKeys = true }

    val profile: Flow<Profile?> = store.data.map { p ->
        p[K.profile]?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() }
    }

    override val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            mapsKey = p[K.mapsKey] ?: "", mapId = p[K.mapId] ?: "",
            mapProvider = p[K.mapProvider]?.let { runCatching { MapProvider.valueOf(it) }.getOrNull() } ?: MapProvider.AUTO,
            googleRejected = p[K.googleRejected] ?: false,
        )
    }

    override val counters: Flow<Counters> = store.data.map { p ->
        Counters(p[K.detailsCalls] ?: 0, p[K.photoCalls] ?: 0, p[K.mapLoads] ?: 0)
    }

    suspend fun saveProfile(profile: Profile?) {
        store.edit { if (profile == null) it.remove(K.profile) else it[K.profile] = json.encodeToString(Profile.serializer(), profile) }
    }

    override suspend fun saveSettings(s: Settings) {
        store.edit {
            it[K.mapsKey] = s.mapsKey; it[K.mapId] = s.mapId
            it[K.mapProvider] = s.mapProvider.name; it[K.googleRejected] = s.googleRejected
        }
    }

    suspend fun bump(counter: Counter) { store.edit { it[counter.key] = (it[counter.key] ?: 0) + 1 } }

    suspend fun clearAll() { store.edit { it.clear() } }
}
