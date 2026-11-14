package com.elsewhere.app.analytics

import com.elsewhere.app.data.Located
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Visit
import com.elsewhere.app.data.homeAt
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min

// Port of web/src/analytics/tour.ts

data class Stop(val city: String, val country: String?, var nights: Int, var visits: Int, var first: Long, var last: Long, val lat: Double, val lng: Double)
data class HeadlineAct(val city: String, val country: String?, val km: Double, val nights: Int, val start: Long, val end: Long, val placeId: String)
data class Tour(val stops: List<Stop>, val nightsAway: Int, val nightsHome: Int, val headline: HeadlineAct?, val years: Pair<Int, Int>)

private const val NIGHT_HOUR = 3

internal fun yearOf(ts: Long): Int = Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC).year
internal fun ceilDiv(a: Long, b: Long): Long = -Math.floorDiv(-a, b)

/** Local 03:00 instants inside [start, end]: one per night slept there. */
fun nightsIn(start: Long, end: Long, tz: Int): Int {
    val off = tz * 60L
    val first = ceilDiv(start + off - NIGHT_HOUR * 3600L, 86400L) * 86400L + NIGHT_HOUR * 3600L - off
    if (first > end) return 0
    return (Math.floorDiv(end - first, 86400L) + 1).toInt()
}
fun nightsIn(v: Visit): Int = nightsIn(v.start, v.end, v.tz)

/** The home active at `ts`, falling back to the record's single home for records baked before profiles existed. */
internal fun homeOf(data: Record, ts: Long): Located =
    if (data.profile.homes.isNotEmpty()) homeAt(data.profile.homes, ts) else data.profile.home

/** Where you slept: nights per city away from home, like a tour's dates list. */
fun tour(data: Record): Tour {
    val byCity = LinkedHashMap<String, Stop>()
    var nightsHome = 0; var nightsAway = 0
    var headline: HeadlineAct? = null
    for (v in data.visits) {
        if (v.lvl != 0) continue
        val n = nightsIn(v)
        if (n == 0) continue
        val p = data.places[v.placeId]
        if (isHome(p) || distanceKm(homeOf(data, v.start), v) < 30) { nightsHome += n; continue }
        nightsAway += n
        val city = p?.city ?: "Unknown city"
        val s = byCity.getOrPut(city) { Stop(city, p?.countryCode, 0, 0, v.start, v.end, v.lat, v.lng) }
        s.nights += n; s.visits++
        s.first = min(s.first, v.start); s.last = max(s.last, v.end)
        val km = distanceKm(homeOf(data, v.start), v)
        if (headline == null || km > headline.km) headline = HeadlineAct(city, p?.countryCode, km, n, v.start, v.end, v.placeId)
    }
    val stops = byCity.values.sortedWith(compareByDescending<Stop> { it.nights }.thenByDescending { it.visits })
    val range = data.meta.range
    return Tour(stops, nightsAway, nightsHome, headline, yearOf(range.first) to yearOf(range.last))
}
