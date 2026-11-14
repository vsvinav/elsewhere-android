package com.elsewhere.app.analytics

import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Visit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.min

// Port of web/src/analytics/explore.ts

enum class PlaceStatus(val key: String) { NEW("new"), REPEAT("repeat"), LAPSED("lapsed") }
data class Bucket(val key: String, val start: Long, val end: Long, val new: Int, val repeat: Int, val lapsed: Int)
data class PlaceStatusRow(val placeId: String, val lng: Double, val lat: Double, val status: PlaceStatus, val n: Int)

private const val LAPSE_SECONDS = 90 * 86400L
private const val LAPSE_MIN_VISITS = 3

private fun monthStart(ts: Long): Long =
    Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
private fun nextMonth(ts: Long): Long =
    Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()

internal fun nonRoutine(data: Record): List<Visit> = data.visits.filter { it.lvl == 0 && data.places[it.placeId]?.role == null }

/**
 * Month-by-month exploration: places seen for the first time, places revisited, and places
 * (≥3 lifetime visits) that crossed the 90-day-unvisited threshold during the month.
 */
fun explorationScore(data: Record, range: LongRange): List<Bucket> {
    val visits = nonRoutine(data)
    val out = ArrayList<Bucket>()
    var s = monthStart(range.first)
    while (s <= range.last) {
        val e = min(nextMonth(s), range.last + 1)
        val seen = HashSet<String>()
        var nu = 0; var rep = 0
        for (v in visits) {
            if (v.start < s || v.start >= e || v.placeId in seen) continue
            seen.add(v.placeId)
            val p = data.places[v.placeId]
            if (p != null && p.firstTs >= s) nu++ else rep++
        }
        var lapsed = 0
        for (p in data.places.values) {
            if (p.nVisits < LAPSE_MIN_VISITS || p.role != null) continue
            val crossed = p.lastTs + LAPSE_SECONDS
            if (crossed >= s && crossed < e) lapsed++
        }
        out.add(Bucket(LocalDate.ofEpochDay(Math.floorDiv(s, 86400L)).toString().substring(0, 7), s, e - 1, nu, rep, lapsed))
        s = nextMonth(s)
    }
    return out
}

/** Status of every non-routine place visited in range, for the map. */
fun placeStatuses(data: Record, range: LongRange): List<PlaceStatusRow> {
    val seen = LinkedHashMap<String, Int>()
    for (v in nonRoutine(data)) {
        if (v.start < range.first || v.start > range.last) continue
        seen[v.placeId] = (seen[v.placeId] ?: 0) + 1
    }
    val out = ArrayList<PlaceStatusRow>()
    for ((placeId, n) in seen) {
        val p = data.places[placeId] ?: continue
        val status = if (p.firstTs >= range.first) PlaceStatus.NEW else PlaceStatus.REPEAT
        out.add(PlaceStatusRow(placeId, p.lng, p.lat, status, n))
    }
    for ((placeId, p) in data.places) {
        if (placeId in seen || p.nVisits < LAPSE_MIN_VISITS) continue
        if (p.role != null) continue
        if (p.lastTs < range.last - LAPSE_SECONDS && p.lastTs >= range.first - 365 * 86400L) out.add(PlaceStatusRow(placeId, p.lng, p.lat, PlaceStatus.LAPSED, p.nVisits))
    }
    return out
}
