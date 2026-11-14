package com.elsewhere.app.data

import com.elsewhere.app.analytics.inRange
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

// Port of web/src/data/filter.ts

private fun dayOf(ts: Long): String = LocalDate.ofEpochDay(Math.floorDiv(ts, 86400L)).toString()

private class PlaceAcc(val src: Place, var nVisits: Int, var totalHours: Double, var firstTs: Long, var lastTs: Long)

/** Cut the record to [a, b]; place counts are recomputed from the visits that remain. */
fun filterData(d: Record, range: LongRange): Record {
    val a = range.first; val b = range.last
    val visits = inRange(d.visits, range)
    val dayA = dayOf(a); val dayB = dayOf(b)
    val places = LinkedHashMap<String, PlaceAcc>()
    for (v in visits) {
        val src = d.places[v.placeId] ?: continue
        val p = places.getOrPut(v.placeId) { PlaceAcc(src, 0, 0.0, v.start, v.end) }
        p.nVisits++; p.totalHours += (v.end - v.start) / 3600.0
        p.firstTs = min(p.firstTs, v.start); p.lastTs = max(p.lastTs, v.end)
    }
    return d.copy(
        visits = visits,
        places = places.mapValues { (_, p) -> p.src.copy(nVisits = p.nVisits, totalHours = p.totalHours, firstTs = p.firstTs, lastTs = p.lastTs) },
        activities = inRange(d.activities, range), strokes = inRange(d.strokes, range), paths = inRange(d.paths, range),
        trips = inRange(d.trips, range), daily = d.daily.filter { it.date >= dayA && it.date <= dayB },
        meta = d.meta.copy(range = max(a, d.meta.range.first)..min(b, d.meta.range.last)),
    )
}
