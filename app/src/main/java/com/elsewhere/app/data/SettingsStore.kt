package com.elsewhere.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.elsewhere.app.ask.LlmConfig
import com.elsewhere.app.ask.LlmProvider
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
    /** What the last key check found, and when (epoch ms; 0 = never checked). */
    val keyPlaces: Boolean = false, val keyTiles: Boolean = false, val keyCheckedAt: Long = 0,
    /** The user has read and accepted "limited maps": free tiles, no place names or photos. */
    val limitedAccepted: Boolean = false,
)

/** Whether the phone is recording its own location, since when, and whether that is paused for a while. */
data class Recording(val enabled: Boolean = false, val since: Long = 0L, val pausedUntil: Long = 0L) {
    fun active(now: Long) = enabled && pausedUntil <= now
}

/** Billable Google calls made from this phone since `since` (epoch ms). */
data class Counters(val detailsCalls: Int = 0, val photoCalls: Int = 0, val mapLoads: Int = 0, val since: Long = 0)

@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) : MapsSettings, LlmSettings, CostMeter, AddressBook {
    private object K {
        val profile = stringPreferencesKey("profile")
        val mapsKey = stringPreferencesKey("mapsKey")
        val mapId = stringPreferencesKey("mapId")
        val mapProvider = stringPreferencesKey("mapProvider")
        val keyPlaces = booleanPreferencesKey("keyPlaces")
        val keyTiles = booleanPreferencesKey("keyTiles")
        val keyCheckedAt = longPreferencesKey("keyCheckedAt")
        val limitedAccepted = booleanPreferencesKey("limitedAccepted")
        val detailsCalls = intPreferencesKey("detailsCalls")
        val photoCalls = intPreferencesKey("photoCalls")
        val mapLoads = intPreferencesKey("mapLoads")
        val usageSince = longPreferencesKey("usageSince")
        val recording = booleanPreferencesKey("recording")
        val recordingSince = longPreferencesKey("recordingSince")
        val pausedUntil = longPreferencesKey("pausedUntil")
        val segmenter = stringPreferencesKey("segmenterState")
        val addresses = stringPreferencesKey("addresses")
        val highlightsAt = intPreferencesKey("highlightsAt")
        val llmProvider = stringPreferencesKey("llmProvider")
        val llmKey = stringPreferencesKey("llmKey")
        val llmModel = stringPreferencesKey("llmModel")
        val llmBaseUrl = stringPreferencesKey("llmBaseUrl")
        val passportSeen = stringSetPreferencesKey("passportSeen")
    }
    private fun keyOf(kind: Kind) = when (kind) { Kind.DETAILS -> K.detailsCalls; Kind.PHOTOS -> K.photoCalls; Kind.MAPS -> K.mapLoads }

    private val json = Json { ignoreUnknownKeys = true }

    val profile: Flow<Profile?> = store.data.map { p ->
        p[K.profile]?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() }
    }

    override val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            mapsKey = p[K.mapsKey] ?: "", mapId = p[K.mapId] ?: "",
            mapProvider = MapProvider.parse(p[K.mapProvider]),
            keyPlaces = p[K.keyPlaces] ?: false, keyTiles = p[K.keyTiles] ?: false, keyCheckedAt = p[K.keyCheckedAt] ?: 0,
            limitedAccepted = p[K.limitedAccepted] ?: false,
        )
    }

    override val counters: Flow<Counters> = store.data.map { p ->
        Counters(p[K.detailsCalls] ?: 0, p[K.photoCalls] ?: 0, p[K.mapLoads] ?: 0, p[K.usageSince] ?: 0)
    }

    override val llm: Flow<LlmConfig> = store.data.map { p ->
        LlmConfig(LlmProvider.parse(p[K.llmProvider]), p[K.llmKey] ?: "", p[K.llmModel] ?: "", p[K.llmBaseUrl] ?: "")
    }

    override suspend fun saveLlm(c: LlmConfig) {
        store.edit { it[K.llmProvider] = c.provider.wire; it[K.llmKey] = c.key; it[K.llmModel] = c.model; it[K.llmBaseUrl] = c.baseUrl }
    }

    val recording: Flow<Recording> = store.data.map { p ->
        Recording(p[K.recording] ?: false, p[K.recordingSince] ?: 0L, p[K.pausedUntil] ?: 0L)
    }

    /** Minute of the day the "On this day" notification prints, or null when it is off (the default). */
    val highlightsAt: Flow<Int?> = store.data.map { it[K.highlightsAt] }
    suspend fun saveHighlightsAt(minute: Int?) { store.edit { if (minute == null) it.remove(K.highlightsAt) else it[K.highlightsAt] = minute } }

    suspend fun saveRecording(r: Recording) {
        store.edit { it[K.recording] = r.enabled; it[K.recordingSince] = r.since; it[K.pausedUntil] = r.pausedUntil }
    }

    /** The segmenter's open stay and leg, as JSON, so a killed process picks up where it was. */
    suspend fun segmenterState(): String? = store.data.first()[K.segmenter]
    suspend fun saveSegmenterState(json: String?) { store.edit { if (json == null) it.remove(K.segmenter) else it[K.segmenter] = json } }

    /** Stamp ids the passport has already shown as earned; null until the booklet has been opened once. */
    val passportSeen: Flow<Set<String>?> = store.data.map { it[K.passportSeen] }
    suspend fun savePassportSeen(ids: Set<String>) { store.edit { it[K.passportSeen] = ids } }

    suspend fun saveProfile(profile: Profile?) {
        store.edit { if (profile == null) it.remove(K.profile) else it[K.profile] = json.encodeToString(Profile.serializer(), profile) }
    }

    override suspend fun saveSettings(s: Settings) {
        store.edit {
            it[K.mapsKey] = s.mapsKey; it[K.mapId] = s.mapId
            it[K.mapProvider] = s.mapProvider.name
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
