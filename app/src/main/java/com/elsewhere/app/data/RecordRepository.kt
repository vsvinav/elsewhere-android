package com.elsewhere.app.data

import androidx.room.withTransaction
import com.elsewhere.app.data.db.ActivityEntity
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.FrequentPlaceEntity
import com.elsewhere.app.data.db.ImportMetaEntity
import com.elsewhere.app.data.db.MemoryEntity
import com.elsewhere.app.data.db.PathEntity
import com.elsewhere.app.data.db.PlaceEnrichmentEntity
import com.elsewhere.app.data.db.VisitEntity
import com.elsewhere.app.data.db.decodePts
import com.elsewhere.app.data.db.encodePts
import com.elsewhere.app.ingest.PActivity
import com.elsewhere.app.ingest.PMemory
import com.elsewhere.app.ingest.PPath
import com.elsewhere.app.ingest.PVisit
import com.elsewhere.app.ingest.Parsed
import com.elsewhere.app.ingest.derive
import com.elsewhere.app.ingest.parseTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val CHUNK = 5000
private val strings = ListSerializer(String.serializer())
private val counts = MapSerializer(String.serializer(), Int.serializer())

/**
 * The one place the app reads its data from. A Timeline export is parsed once into Room; the
 * profile lives in DataStore; `record` re-derives from the stored rows whenever either changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RecordRepository @Inject constructor(
    private val db: ElsewhereDb,
    private val settings: SettingsStore,
    private val cities: CitiesLoader,
    scope: CoroutineScope,
) {
    private val importVersion = MutableStateFlow(0)
    private val placesVersion = MutableStateFlow(0)

    val profile: StateFlow<Profile?> = settings.profile.stateIn(scope, SharingStarted.Eagerly, null)

    val hasImport: StateFlow<Boolean> = importVersion
        .map { db.importMeta().get() != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val record: StateFlow<Record?> = combine(importVersion, profile, placesVersion) { _, p, _ -> p }
        .mapLatest { p -> if (p == null || p.homes.isEmpty()) null else build(p) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private suspend fun build(profile: Profile): Record? {
        val meta = db.importMeta().get() ?: return null
        val parsed = Parsed(
            visits = db.visits().all().map { PVisit(it.id, it.start, it.end, it.tz, it.placeId, it.lat, it.lng, it.type, it.p, it.lvl, it.city, it.cc, it.dist) },
            activities = db.activities().all().map { PActivity(it.id, it.start, it.end, it.tz, it.sLat, it.sLng, it.eLat, it.eLng, it.m, it.mode, it.p, it.eCity) },
            paths = db.paths().all().map { PPath(it.start, it.end, decodePts(it.pts)) },
            memories = db.memories().all().map { PMemory(it.start, it.end, it.km, Json.decodeFromString(strings, it.dests)) },
            frequentPlaces = db.frequentPlaces().all().map { FrequentPlace(it.placeId, it.lat, it.lng, it.label) },
            report = ImportReport(meta.segmentsTotal, meta.parsed, Json.decodeFromString(counts, meta.skippedJson)),
        )
        val enrichment = db.placeEnrichment().all()
        val record = derive(parsed, profile, cities.get(), Meta(meta.sourceFile, meta.sourceSha256, meta.builtAt, 0L..0L, parsed.report))
        if (enrichment.isEmpty()) return record
        val places = record.places.toMutableMap()
        for (e in enrichment) {
            val p = places[e.placeId] ?: continue
            places[e.placeId] = p.copy(
                name = e.name ?: p.name, address = e.address ?: p.address,
                types = Json.decodeFromString(strings, e.types), primaryType = e.primaryType ?: p.primaryType, photo = e.photo ?: p.photo,
            )
        }
        return record.copy(places = places)
    }

    /** Parse `open()` once, hashing the bytes as they stream past, and replace whatever was stored. */
    suspend fun import(open: () -> InputStream, fileName: String, onProgress: (Float) -> Unit = {}): ImportReport {
        onProgress(0f)
        val digest = MessageDigest.getInstance("SHA-256")
        val parsed = withContext(Dispatchers.IO) { DigestInputStream(open(), digest).use { parseTimeline(it) } }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        onProgress(0.5f)

        val visits = parsed.visits.map { VisitEntity(it.id, it.start, it.end, it.tz, it.placeId, it.lat, it.lng, it.type, it.p, it.lvl, it.city, it.cc, it.dist) }
        val activities = parsed.activities.map { ActivityEntity(it.id, it.start, it.end, it.tz, it.sLat, it.sLng, it.eLat, it.eLng, it.m, it.mode, it.p, it.eCity) }
        val paths = parsed.paths.map { PathEntity(start = it.start, end = it.end, pts = encodePts(it.pts)) }
        val memories = parsed.memories.map { MemoryEntity(start = it.start, end = it.end, km = it.km, dests = Json.encodeToString(strings, it.dests)) }
        val frequent = parsed.frequentPlaces.map { FrequentPlaceEntity(it.placeId, it.lat, it.lng, it.label) }
        val total = (visits.size + activities.size + paths.size + memories.size + frequent.size).coerceAtLeast(1)
        var done = 0
        val tick = { n: Int -> done += n; onProgress(0.5f + 0.5f * done / total) }

        db.withTransaction {
            clearRows()
            visits.chunked(CHUNK).forEach { db.visits().insertAll(it); tick(it.size) }
            activities.chunked(CHUNK).forEach { db.activities().insertAll(it); tick(it.size) }
            paths.chunked(CHUNK).forEach { db.paths().insertAll(it); tick(it.size) }
            memories.chunked(CHUNK).forEach { db.memories().insertAll(it); tick(it.size) }
            frequent.chunked(CHUNK).forEach { db.frequentPlaces().insertAll(it); tick(it.size) }
            db.importMeta().insertAll(listOf(ImportMetaEntity(
                sourceFile = fileName, sourceSha256 = sha, builtAt = System.currentTimeMillis() / 1000,
                segmentsTotal = parsed.report.segmentsTotal, parsed = parsed.report.parsed,
                skippedJson = Json.encodeToString(counts, parsed.report.skippedByReason),
            )))
        }
        importVersion.update { it + 1 }
        onProgress(1f)
        return parsed.report
    }

    private suspend fun clearRows() {
        db.visits().clear(); db.activities().clear(); db.paths().clear(); db.memories().clear()
        db.frequentPlaces().clear(); db.placeEnrichment().clear(); db.importMeta().clear()
    }

    suspend fun saveProfile(p: Profile) = settings.saveProfile(p)

    suspend fun saveEnrichment(row: PlaceEnrichmentEntity) { db.placeEnrichment().upsert(row); bumpPlacesVersion() }

    suspend fun reset() {
        db.withTransaction { clearRows() }
        settings.clearAll()
        importVersion.update { it + 1 }
    }

    fun bumpPlacesVersion() = placesVersion.update { it + 1 }
}
