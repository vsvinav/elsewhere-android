package com.elsewhere.app.analytics

import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Record
import java.time.LocalDate
import java.time.ZoneOffset

// Port of web/src/analytics/timeline.ts

/** How far the map steps back when the finger pinches the strip: a whole year of trips, a month of places, a day of trail. */
enum class Grain(val label: String) { YEAR("Year"), MONTH("Month"), DAY("Day") }

/** Prefix sums over the record's events, so any playhead reads its "so far" figures in one binary search. */
class Cumulative(
    val ts: DoubleArray, val km: DoubleArray, val cities: IntArray, val nightsAway: IntArray, val flights: IntArray,
    val milestones: List<Milestone>, val span: Double,
)
data class Milestone(val ts: Double, val text: String)
data class Rail(val km: Double, val cities: Int, val nightsAway: Int, val flights: Int, val milestone: String?)

/** How long a milestone stays on the rail, as a share of the window: about a second of scrubbing. */
private const val LINGER = 0.02
private val CITY_MARKS = setOf(1, 10, 25, 50, 100, 200)
private val NIGHT_MARKS = setOf(100, 365)
private val FLIGHT_MARKS = setOf(10, 50, 100)
private const val KM_EVERY = 10_000.0

private fun ord(n: Int) = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

private class Ev(val ts: Double, val km: Double = 0.0, val city: String? = null, val night: Boolean = false, val flight: Boolean = false)

/**
 * The record counted up in event order: a leg lands its km (and a flight) at its last point, a city counts at the
 * first lvl-0 visit that names it, a night away counts at the UTC midnight of its date. Pure; callers `remember` it per Record.
 */
fun cumulative(data: Record): Cumulative {
    val evs = ArrayList<Ev>()
    legsOf(data).forEachIndexed { i, l ->
        if (l.ts.isEmpty()) return@forEachIndexed
        // Recorded metres beat a straight guess between two places.
        val m = data.activities.getOrNull(i)?.m ?: 0.0
        evs += Ev(l.ts.last(), km = if (m > 0) m / 1000 else pathKm(l.path), flight = l.lens == Lens.FLY)
    }
    for (v in data.visits) if (v.lvl == 0) data.places[v.placeId]?.city?.let { evs += Ev(v.start.toDouble(), city = it) }
    for (n in bedStrip(data)) if (n.kind == NightKind.NEW || n.kind == NightKind.REPEAT)
        evs += Ev(LocalDate.parse(n.date).atStartOfDay(ZoneOffset.UTC).toEpochSecond().toDouble(), night = true)
    evs.sortBy { it.ts }
    val ts = DoubleArray(evs.size); val km = DoubleArray(evs.size); val cities = IntArray(evs.size)
    val nights = IntArray(evs.size); val flights = IntArray(evs.size)
    val miles = ArrayList<Milestone>(); val seen = HashSet<String>()
    var k = 0.0; var c = 0; var n = 0; var f = 0; var kmMark = KM_EVERY
    evs.forEachIndexed { i, e ->
        if (e.km > 0) { k += e.km; while (k >= kmMark) { miles += Milestone(e.ts, "%,d km so far".format(kmMark.toInt())); kmMark += KM_EVERY } }
        if (e.flight) { f++; if (f in FLIGHT_MARKS) miles += Milestone(e.ts, "your ${ord(f)} flight") }
        if (e.city != null && seen.add(e.city)) { c++; if (c in CITY_MARKS) miles += Milestone(e.ts, if (c == 1) "your first city" else "…and that's your ${ord(c)} city") }
        if (e.night) { n++; if (n in NIGHT_MARKS) miles += Milestone(e.ts, "$n nights away") }
        ts[i] = e.ts; km[i] = k; cities[i] = c; nights[i] = n; flights[i] = f
    }
    return Cumulative(ts, km, cities, nights, flights, miles, (data.meta.range.last - data.meta.range.first).toDouble())
}

/** The figures as they stood at `t`, with the latest milestone while it still lingers. */
fun atPlayhead(c: Cumulative, t: Double): Rail {
    var lo = 0; var hi = c.ts.size
    while (lo < hi) { val mid = (lo + hi) ushr 1; if (c.ts[mid] <= t) lo = mid + 1 else hi = mid }
    val i = lo - 1
    val m = c.milestones.lastOrNull { it.ts <= t }?.takeIf { t - it.ts <= LINGER * c.span }?.text
    return if (i < 0) Rail(0.0, 0, 0, 0, m) else Rail(c.km[i], c.cities[i], c.nightsAway[i], c.flights[i], m)
}

/** Which grains have something to show in this window, and the one in force: Replay opens on DAY, the trail, unless a coarser grain is picked. */
fun resolveGrain(data: Record, picked: Grain?): Pair<Grain, List<Grain>> {
    val span = data.meta.range.last - data.meta.range.first
    val allowed = when {
        span <= 21L * 86400 -> listOf(Grain.DAY)
        data.trips.isNotEmpty() -> Grain.entries
        else -> listOf(Grain.MONTH, Grain.DAY)
    }
    return (picked?.takeIf { it in allowed } ?: Grain.DAY) to allowed
}

private fun pathKm(path: List<LatLng>): Double {
    var km = 0.0
    for (i in 1 until path.size) km += distanceKm(path[i - 1], path[i])
    return km
}
