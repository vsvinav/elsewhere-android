package com.elsewhere.app.ingest

import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Daily
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Memory
import com.elsewhere.app.data.Meta
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Pt
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordProfile
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Visit
import com.elsewhere.app.data.homeAt
import com.elsewhere.app.data.worksAt
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

// Port of pipeline/geo.py + derive.py + export.py: parsed records → the Record the app reads.

private const val TRIP_RADIUS_KM = 50.0
private const val STROKE_POINTS = 8000
const val ROLE_RADIUS_KM = 0.3

private fun localDay(ts: Long, tz: Int): Long = Math.floorDiv(ts + tz * 60L, 86400L)
private fun localDate(ts: Long, tz: Int): String = LocalDate.ofEpochDay(localDay(ts, tz)).toString()

/** Local calendar days a span touches, with the seconds spent in each. */
private fun splitByLocalDay(start: Long, end: Long, tz: Int): Sequence<Pair<String, Long>> = sequence {
    var cur = start
    while (cur < end) {
        val day = localDay(cur, tz)
        val boundary = (day + 1) * 86400L - tz * 60L
        val chunk = min(end, boundary)
        yield(LocalDate.ofEpochDay(day).toString() to (chunk - cur))
        cur = chunk
    }
}
private fun nights(start: Long, end: Long, tz: Int): Int = (localDay(end, tz) - localDay(start, tz)).toInt()
private fun r3(x: Double) = jsRound(x * 1000) / 1000
private fun r2(x: Double) = jsRound(x * 100) / 100

/** Home = within 300 m of the home active on the visit date; work = within 300 m of a work place active then. */
fun roleOf(v: PVisit, profile: Profile): Role? {
    val h = homeAt(profile.homes, v.start)
    if (haversineKm(h.lat, h.lng, v.lat, v.lng) <= ROLE_RADIUS_KM) return Role.HOME
    if (worksAt(profile.works, v.start).any { w -> haversineKm(w.lat, w.lng, v.lat, v.lng) <= ROLE_RADIUS_KM }) return Role.WORK
    return null
}

/** Google splits one address across several place ids. Visits at a home (or work) are re-pointed
 *  at the busiest id of that cluster, so the record has one Home per home. Returns copies. */
fun foldRoles(visits: List<PVisit>, profile: Profile): List<PVisit> {
    // Keyed by coordinates, so a home you came back to stays one Home, and each work place is its own.
    fun key(v: PVisit): String? = when (roleOf(v, profile)) {
        Role.HOME -> { val h = homeAt(profile.homes, v.start); "home:${h.lat},${h.lng}" }
        Role.WORK -> { val w = worksAt(profile.works, v.start).first { w -> haversineKm(w.lat, w.lng, v.lat, v.lng) <= ROLE_RADIUS_KM }; "work:${w.lat},${w.lng}" }
        null -> null
    }
    val keys = visits.map(::key)
    val count = LinkedHashMap<String, Int>()
    keys.forEachIndexed { i, k -> if (k != null) { val ck = "$k\u0000${visits[i].placeId}"; count[ck] = (count[ck] ?: 0) + 1 } }
    val best = HashMap<String, String>()
    for ((ck, _) in count.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })) {
        val (k, pid) = ck.split('\u0000', limit = 2)
        if (k !in best) best[k] = pid
    }
    return visits.mapIndexed { i, v -> keys[i]?.let { v.copy(placeId = best.getValue(it)) } ?: v }
}

private fun distHome(profile: Profile, ts: Long, lat: Double, lng: Double): Double {
    val h = homeAt(profile.homes, ts)
    return haversineKm(h.lat, h.lng, lat, lng)
}

fun derivePlaces(p: Parsed, profile: Profile): Map<String, Place> {
    class Acc(val lat: Double, val lng: Double, val label: String?, val city: String?, val cc: String?, var firstTs: Long, var lastTs: Long) {
        var nVisits = 0; var totalHours = 0.0; var role: Role? = null
    }
    val labels = p.frequentPlaces.associate { it.placeId to it.label }
    val out = LinkedHashMap<String, Acc>()
    for (v in p.visits) {
        val pl = out.getOrPut(v.placeId) { Acc(v.lat, v.lng, labels[v.placeId], v.city, v.cc, v.start, v.end) }
        pl.nVisits++; pl.totalHours += (v.end - v.start) / 3600.0
        pl.firstTs = min(pl.firstTs, v.start); pl.lastTs = max(pl.lastTs, v.end)
        pl.role = pl.role ?: roleOf(v, profile)
    }
    return out.mapValues { (_, a) ->
        Place(lat = a.lat, lng = a.lng, label = a.label, role = a.role, city = a.city, countryCode = a.cc, nVisits = a.nVisits,
            totalHours = a.totalHours, firstTs = a.firstTs, lastTs = a.lastTs, name = null, address = null)
    }
}

fun deriveDaily(p: Parsed, profile: Profile): List<Daily> {
    class Row(val date: String) {
        val kmByMode = LinkedHashMap<String, Double>(); var nVisits = 0; var nNewPlaces = 0; var maxDist = 0.0
        var hoursHome = 0.0; var hoursWork = 0.0; var hoursOther = 0.0; var hoursTransit = 0.0
        val places = HashSet<String>(); val cities = HashSet<String>()
    }
    val days = HashMap<String, Row>()
    fun day(d: String) = days.getOrPut(d) { Row(d) }
    val seen = HashSet<String>()
    for (v in p.visits) {
        val row = day(localDate(v.start, v.tz))
        row.nVisits++
        if (seen.add(v.placeId)) row.nNewPlaces++
        val role = roleOf(v, profile)
        val dist = v.dist ?: distHome(profile, v.start, v.lat, v.lng)
        // Nested visits sit inside a parent stay whose hours are already counted.
        for ((d, secs) in splitByLocalDay(v.start, v.end, v.tz)) {
            val r = day(d)
            if (v.lvl == 0) when (role) {
                Role.HOME -> r.hoursHome += secs / 3600.0
                Role.WORK -> r.hoursWork += secs / 3600.0
                null -> r.hoursOther += secs / 3600.0
            }
            r.places.add(v.placeId); if (v.city != null) r.cities.add(v.city)
            r.maxDist = max(r.maxDist, dist)
        }
    }
    for (a in p.activities) {
        val row = day(localDate(a.start, a.tz))
        row.kmByMode[a.mode] = (row.kmByMode[a.mode] ?: 0.0) + a.m / 1000
        for ((d, secs) in splitByLocalDay(a.start, a.end, a.tz)) day(d).hoursTransit += secs / 3600.0
        row.maxDist = max(row.maxDist, distHome(profile, a.start, a.eLat, a.eLng))
        if (a.eCity != null) row.cities.add(a.eCity)
    }
    return days.keys.sorted().map { d ->
        val r = days.getValue(d)
        Daily(
            date = r.date, kmByMode = r.kmByMode.mapValues { r3(it.value) }, nVisits = r.nVisits, nPlaces = r.places.size,
            nNewPlaces = r.nNewPlaces, maxDistFromHomeKm = r2(r.maxDist), hoursHome = r3(r.hoursHome), hoursWork = r3(r.hoursWork),
            hoursOther = r3(r.hoursOther), hoursTransit = r3(r.hoursTransit), cities = r.cities.sorted(),
        )
    }
}

fun deriveTrips(p: Parsed, profile: Profile): List<Trip> {
    val flights = p.activities.filter { it.mode == "FLYING" }
    class Cur(var start: Long, var end: Long, val tz: Int) { val cities = ArrayList<String>(); var max = 0.0 }
    val trips = ArrayList<Cur>()
    var cur: Cur? = null
    fun close(c: Cur) { if (nights(c.start, c.end, c.tz) >= 1) trips += c }
    for (v in p.visits) {
        val dist = v.dist ?: distHome(profile, v.start, v.lat, v.lng)
        val away = dist > TRIP_RADIUS_KM
        if (cur == null && away) {
            // A trip starts with the flight out when one landed within six hours of the first far visit.
            var start = v.start
            for (f in flights) if (v.start - f.end >= 0 && v.start - f.end <= 6 * 3600) start = min(start, f.start)
            cur = Cur(start, 0, v.tz)
        }
        val c = cur
        if (c != null) {
            if (away) { if (v.city != null && v.city !in c.cities) c.cities += v.city; c.max = max(c.max, dist) }
            else { c.end = v.start; close(c); cur = null }
        }
    }
    cur?.let { c -> if (p.visits.isNotEmpty()) { c.end = p.visits.last().end; close(c) } }
    return trips.map { t ->
        val km = LinkedHashMap<String, Double>()
        for (a in p.activities) if (a.start >= t.start && a.end <= t.end) km[a.mode] = r3((km[a.mode] ?: 0.0) + a.m / 1000)
        val m = p.memories.firstOrNull { it.start < t.end && it.end > t.start }
        Trip(start = t.start, end = t.end, nights = nights(t.start, t.end, t.tz), maxDistFromHomeKm = r2(t.max), totalKmByMode = km,
            anchorCities = t.cities, memory = m?.let { Memory(it.km, it.dests) })
    }
}

/** Tag city/country/distance, then assemble everything the Python export writes. `meta` supplies the
 *  source identity; its range and import report are filled in here. */
fun derive(parsed: Parsed, profile: Profile, cities: Cities, meta: Meta): Record {
    val geo = geocoder(cities)
    val visits = foldRoles(parsed.visits, profile).map { v ->
        val h = geo(v.lat, v.lng)
        v.copy(city = h?.name?.ifEmpty { null }, cc = h?.cc?.ifEmpty { null }, dist = distHome(profile, v.start, v.lat, v.lng))
    }
    val activities = parsed.activities.map { a -> a.copy(eCity = geo(a.eLat, a.eLng)?.name?.ifEmpty { null }) }
    val p = parsed.copy(visits = visits, activities = activities)

    val total = p.paths.sumOf { it.pts.size }
    val k = max(1, Math.ceil(total.toDouble() / STROKE_POINTS).toInt())
    val r4 = { x: Double -> jsRound(x * 1e4) / 1e4 }
    val strokes = p.paths.filter { it.pts.size > 1 }.map { x ->
        Path(x.start, x.end, x.pts.filterIndexed { i, _ -> i % k == 0 || i == x.pts.size - 1 }.map { Pt(r4(it.lat), r4(it.lng), it.ts) })
    }

    val last = profile.homes.last()
    val starts = p.visits.map { it.start } + p.activities.map { it.start }
    val ends = p.visits.map { it.end } + p.activities.map { it.end }
    return Record(
        visits = p.visits.map { v -> Visit(v.id, v.start, v.end, v.tz, v.placeId, v.lat, v.lng, v.type, r3(v.p), v.lvl) },
        activities = p.activities.map { a -> Activity(a.id, a.start, a.end, a.tz, a.sLat, a.sLng, a.eLat, a.eLng, a.m, a.mode, r3(a.p), a.recorded) },
        strokes = strokes, paths = p.paths.map { Path(it.start, it.end, it.pts) },
        places = derivePlaces(p, profile), daily = deriveDaily(p, profile), trips = deriveTrips(p, profile),
        profile = RecordProfile(home = LatLng(last.lat, last.lng), homes = profile.homes, works = profile.works, frequentPlaces = p.frequentPlaces),
        meta = meta.copy(range = (starts.minOrNull() ?: 0L)..(ends.maxOrNull() ?: 0L), importReport = p.report),
    )
}
