package com.elsewhere.app.analytics

import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Visit
import java.time.LocalDate

// Port of web/src/analytics/beds.ts

enum class NightKind(val key: String) { HOME("home"), NEW("new"), REPEAT("repeat"), NONE("none") }
data class Night(val date: String, val kind: NightKind, val city: String?)
data class Run(val from: String, val to: String, val n: Int)

private const val NIGHT_HOUR = 3
private const val HOME_RADIUS_KM = 30

fun dayKey(ts: Long): String = LocalDate.ofEpochDay(Math.floorDiv(ts, 86400L)).toString()

/** Each local 03:00 instant inside a stay, with the local date it belongs to. */
private fun nightsOf(v: Visit): List<String> {
    val off = v.tz * 60L
    var t = ceilDiv(v.start + off - NIGHT_HOUR * 3600L, 86400L) * 86400L + NIGHT_HOUR * 3600L - off
    val out = ArrayList<String>()
    while (t <= v.end) { out.add(dayKey(t + off)); t += 86400L }
    return out
}

private class Slept(val kind: NightKind, val city: String?)

/**
 * One entry per local night in `range`: where the visitor slept, and whether that city was
 * new to them at the time. Firsts are judged over the whole record, so a January trip stays
 * "new" when the range is cut to January.
 */
fun bedStrip(data: Record, range: LongRange = data.meta.range): List<Night> {
    val slept = HashMap<String, Slept>()
    val seenCity = HashSet<String>()
    for (v in data.visits) {
        if (v.lvl != 0) continue
        val p = data.places[v.placeId]
        val atHome = isHome(p) || distanceKm(homeOf(data, v.start), v) < HOME_RADIUS_KM
        val city = p?.city
        val key = city ?: v.placeId
        // Judged once per stay, so every night of a first visit reads as new.
        val kind = if (atHome) NightKind.HOME else if (key in seenCity) NightKind.REPEAT else NightKind.NEW
        val nights = nightsOf(v)
        if (!atHome && nights.isNotEmpty()) seenCity.add(key)
        for (d in nights) slept.getOrPut(d) { Slept(kind, city) }
    }
    val out = ArrayList<Night>()
    val b = dayKey(range.last)
    var day = LocalDate.parse(dayKey(range.first))
    while (day.toString() <= b) {
        val d = day.toString()
        val s = slept[d]
        out.add(Night(d, s?.kind ?: NightKind.NONE, s?.city))
        day = day.plusDays(1)
    }
    return out
}

/** Longest unbroken run of nights matching `pred`, with its dates. */
fun longestRun(nights: List<Night>, pred: (Night) -> Boolean): Run? {
    var best: Run? = null
    var cur: Run? = null
    for (night in nights) {
        if (pred(night)) {
            cur = cur?.let { Run(it.from, night.date, it.n + 1) } ?: Run(night.date, night.date, 1)
            if (best == null || cur.n > best.n) best = cur
        } else cur = null
    }
    return best
}
