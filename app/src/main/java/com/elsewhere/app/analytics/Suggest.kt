package com.elsewhere.app.analytics

import com.elsewhere.app.data.Record
import com.elsewhere.app.ingest.ROLE_RADIUS_KM
import com.elsewhere.app.ingest.haversineKm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max

// Port of web/src/profile/suggest.ts: home and work candidates for onboarding.

data class Suggestion(
    val placeId: String, val lat: Double, val lng: Double, val city: String?, val cc: String?, var score: Double, var last: Long,
    /** Every Google place id folded into this one. */
    val members: MutableList<String>,
    /** Nights here over all nights slept anywhere in the months this place was slept at, 0–1. Sleep places only. */
    var share: Double,
)
data class Move(val month: String, val from: String, val to: String)

private const val SLEEP_H = 3
private fun monthOf(day: Long): String = LocalDate.ofEpochDay(day).toString().substring(0, 7)

/** Local-day index → the visit covers 03:00 local that day. */
private fun sleptNights(start: Long, end: Long, tz: Int): Sequence<Long> = sequence {
    val off = tz * 60L
    val d0 = Math.floorDiv(start + off, 86400L); val d1 = Math.floorDiv(end + off, 86400L)
    for (day in d0..d1) { val t3 = day * 86400L + SLEEP_H * 3600L - off; if (t3 >= start && t3 < end) yield(day) }
}

/** Google often gives one address several place ids; fold each into the busiest within 300 m. */
private fun fold(list: List<Suggestion>): List<Suggestion> {
    val out = ArrayList<Suggestion>()
    for (s in list) {
        val hit = out.firstOrNull { o -> haversineKm(o.lat, o.lng, s.lat, s.lng) <= ROLE_RADIUS_KM }
        if (hit != null) { hit.score += s.score; hit.last = max(hit.last, s.last); hit.members.addAll(s.members) }
        else out.add(s.copy(members = ArrayList(s.members)))
    }
    return out
}

private val BY_SCORE_THEN_LAST = compareByDescending<Suggestion> { it.score }.thenByDescending { it.last }

/** Places ranked by nights slept (the lvl-0 visit covering 03:00 local). */
fun sleepPlaces(d: Record, max: Int = 6): List<Suggestion> {
    val by = LinkedHashMap<String, Suggestion>()
    val nights = HashMap<String, LinkedHashMap<String, Int>>() // place → month → nights
    val total = HashMap<String, Int>() // month → nights anywhere
    for (v in d.visits) {
        if (v.lvl != 0) continue
        val p = d.places[v.placeId] ?: continue
        var n = 0
        for (day in sleptNights(v.start, v.end, v.tz)) {
            n++
            val m = monthOf(day); val pm = nights.getOrPut(v.placeId) { LinkedHashMap() }
            pm[m] = (pm[m] ?: 0) + 1; total[m] = (total[m] ?: 0) + 1
        }
        if (n == 0) continue
        val s = by.getOrPut(v.placeId) { Suggestion(v.placeId, p.lat, p.lng, p.city, p.countryCode, 0.0, 0, arrayListOf(v.placeId), 0.0) }
        s.score += n; s.last = max(s.last, v.end)
    }
    val out = fold(by.values.sortedWith(BY_SCORE_THEN_LAST)).sortedWith(BY_SCORE_THEN_LAST).take(max)
    for (s in out) {
        val months = s.members.flatMap { nights[it]?.keys ?: emptySet() }.toSet()
        val all = months.sumOf { total[it] ?: 0 }
        s.share = if (all != 0) s.score / all else 0.0
    }
    return out
}

/** Weekday 09:00–17:00 local hours per place, excluding sleep places. */
fun weekdayPlaces(d: Record, max: Int = 4): List<Suggestion> {
    val sleep = sleepPlaces(d, 50).mapTo(HashSet()) { it.placeId }
    val by = LinkedHashMap<String, Suggestion>()
    for (v in d.visits) {
        if (v.lvl != 0 || v.placeId in sleep) continue
        val p = d.places[v.placeId] ?: continue
        val local = Instant.ofEpochSecond(v.start + v.tz * 60L).atOffset(ZoneOffset.UTC)
        val dow = local.dayOfWeek.value % 7; val h = local.hour
        if (dow == 0 || dow == 6 || h < 7 || h > 17) continue
        val s = by.getOrPut(v.placeId) { Suggestion(v.placeId, p.lat, p.lng, p.city, p.countryCode, 0.0, 0, arrayListOf(v.placeId), 0.0) }
        s.score += (v.end - v.start) / 3600.0; s.last = max(s.last, v.end)
    }
    return fold(by.values.sortedByDescending { it.score }).sortedByDescending { it.score }.take(max)
}

/** Months where the most-slept place changes from the previous month (≥10 nights at the new one). */
fun moveMonths(d: Record): List<Move> {
    val perMonth = HashMap<String, LinkedHashMap<String, Int>>()
    for (v in d.visits) {
        if (v.lvl != 0) continue
        for (day in sleptNights(v.start, v.end, v.tz)) {
            val b = perMonth.getOrPut(monthOf(day)) { LinkedHashMap() }
            b[v.placeId] = (b[v.placeId] ?: 0) + 1
        }
    }
    val months = perMonth.keys.sorted()
    fun modal(m: String) = perMonth.getValue(m).entries.maxBy { it.value }.key
    val out = ArrayList<Move>()
    for (i in 1 until months.size) {
        val a = modal(months[i - 1]); val b = modal(months[i])
        if (a != b && perMonth.getValue(months[i]).getValue(b) >= 10 && out.none { it.to == b }) out.add(Move(months[i], a, b))
    }
    return out
}
