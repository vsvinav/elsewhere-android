package com.elsewhere.app.record

import com.elsewhere.app.data.CitiesLoader
import com.elsewhere.app.data.RecordRepository
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.db.ActivityEntity
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.PathEntity
import com.elsewhere.app.data.db.RawFixEntity
import com.elsewhere.app.data.db.SOURCE_DEVICE
import com.elsewhere.app.data.db.VisitEntity
import com.elsewhere.app.data.db.encodePts
import com.elsewhere.app.ingest.record.CityResolver
import com.elsewhere.app.ingest.record.Emission
import com.elsewhere.app.ingest.record.Fix
import com.elsewhere.app.ingest.record.Segmenter
import com.elsewhere.app.ingest.record.SegmenterState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class RecordStats(val fixes: Int, val stays: Int, val lastFixTs: Long?)

/**
 * The recorder behind the service: every fix is written raw, run through the segmenter, and whatever
 * closes lands in the record as device rows. The segmenter's open state is persisted after each fix.
 */
@Singleton
class Recorder(
    private val db: ElsewhereDb,
    private val repo: RecordRepository,
    private val settings: SettingsStore,
    private val cities: CitiesLoader,
    private val tzAt: (Long) -> Int,
) {
    @Inject constructor(db: ElsewhereDb, repo: RecordRepository, settings: SettingsStore, cities: CitiesLoader) :
        this(db, repo, settings, cities, { ts -> TimeZone.getDefault().getOffset(ts * 1000) / 60_000 })

    private val lock = Mutex()
    private var segmenter: Segmenter? = null
    private var resolver: CityResolver? = null

    private suspend fun segmenter(): Segmenter = segmenter ?: run {
        val saved = settings.segmenterState()?.let { runCatching { Json.decodeFromString(SegmenterState.serializer(), it) }.getOrNull() }
        Segmenter(saved ?: SegmenterState(), tzAt).also { segmenter = it }
    }

    private suspend fun resolver(): CityResolver = resolver ?: CityResolver(cities.get()).also { resolver = it }

    suspend fun onFix(fix: Fix, altitude: Double? = null, batteryPct: Int? = null) = lock.withLock {
        db.rawFixes().insertAll(listOf(RawFixEntity(
            ts = fix.ts, lat = fix.lat, lng = fix.lng, accuracyM = fix.accM, speedMps = fix.speed,
            altitude = altitude, activity = fix.activityHint, batteryPct = batteryPct,
        )))
        val s = segmenter()
        commit(s.feed(fix))
        persist(s)
    }

    /** The periodic sweep: close a stay nobody has added to for an hour, forget raw fixes older than 90 days. */
    suspend fun sweep(now: Long) = lock.withLock {
        val s = segmenter()
        val last = s.state.lastTs
        if (last != null && now - last >= STALE_S) { commit(s.close()); persist(s) }
        db.rawFixes().pruneBefore(now - KEEP_RAW_S)
    }

    /** Stopping: a confirmed open stay is written, the half leg dropped, and the state forgotten. */
    suspend fun close() = lock.withLock {
        val s = segmenter()
        commit(s.close())
        segmenter = null
        settings.saveSegmenterState(null)
    }

    suspend fun stats(): RecordStats = RecordStats(db.rawFixes().countAll(), db.visits().countDevice(), db.rawFixes().latest()?.ts)

    private suspend fun commit(out: List<Emission>) {
        if (out.isEmpty()) return
        val geo = resolver()
        val visits = ArrayList<VisitEntity>(); val acts = ArrayList<ActivityEntity>(); val paths = ArrayList<PathEntity>()
        for (e in out) when (e) {
            is Emission.Visit -> {
                val v = e.visit
                val (city, cc) = geo.resolve(v.lat, v.lng)
                visits += VisitEntity(v.id, v.start, v.end, v.tz, v.placeId, v.lat, v.lng, v.type, v.p, v.lvl, city, cc, null, SOURCE_DEVICE)
            }
            is Emission.Activity -> {
                val a = e.activity
                acts += ActivityEntity(a.id, a.start, a.end, a.tz, a.sLat, a.sLng, a.eLat, a.eLng, a.m, a.mode, a.p, geo.resolve(a.eLat, a.eLng).first, SOURCE_DEVICE)
                paths += PathEntity(start = e.path.start, end = e.path.end, pts = encodePts(e.path.pts), source = SOURCE_DEVICE)
            }
        }
        repo.insertDevice(visits, acts, paths)
    }

    private suspend fun persist(s: Segmenter) = settings.saveSegmenterState(Json.encodeToString(SegmenterState.serializer(), s.state))

    companion object {
        const val STALE_S = 3600L
        const val KEEP_RAW_S = 90L * 86400
    }
}
