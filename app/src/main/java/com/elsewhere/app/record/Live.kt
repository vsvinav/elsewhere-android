package com.elsewhere.app.record

import com.elsewhere.app.data.CitiesLoader
import com.elsewhere.app.ingest.record.CityResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A city counts as "here" within this many kilometres of the fix. */
const val NOW_CITY_KM = 5.0

/**
 * The map's live stamp: while recording, the newest fix is re-read every half minute and printed with its
 * age; the city is looked up once per fix, not per tick. Off, paused or stale, the flow holds null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class Live(
    private val control: RecordControl,
    private val cityOf: suspend (Double, Double) -> String?,
    private val now: () -> Long,
    private val zone: () -> ZoneId,
    private val tickMs: Long,
) {
    @Inject constructor(control: RecordControl, cities: CitiesLoader) :
        this(control, nearestCity(cities), { System.currentTimeMillis() / 1000 }, { ZoneId.systemDefault() }, TICK_MS)

    val stamps: Flow<NowStamp?> = control.recording.transformLatest { rec ->
        if (!rec.enabled) { emit(null); return@transformLatest }
        var seenTs: Long? = null
        var city: String? = null
        while (true) {
            val t = now()
            val fix = if (rec.active(t)) control.latestFix() else null
            if (fix != null && fix.ts != seenTs) { seenTs = fix.ts; city = cityOf(fix.lat, fix.lng) }
            emit(nowStamp(fix, t, { _, _ -> city }, zone(), recording = rec.active(t)))
            delay(tickMs)
        }
    }.distinctUntilChanged()

    companion object {
        const val TICK_MS = 30_000L

        /** The nearest listed city within [NOW_CITY_KM], over an index built on first use. */
        fun nearestCity(cities: CitiesLoader): suspend (Double, Double) -> String? {
            var resolver: CityResolver? = null
            return { lat, lng -> (resolver ?: CityResolver(cities.get(), NOW_CITY_KM).also { resolver = it }).resolve(lat, lng).first }
        }
    }
}
