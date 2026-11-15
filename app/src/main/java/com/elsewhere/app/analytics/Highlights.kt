package com.elsewhere.app.analytics

import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Daily
import com.elsewhere.app.data.Located
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Visit
import com.elsewhere.app.data.homeAt
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Twin of web/src/analytics/highlights.ts: "On this day", which past day the record should talk
 * about today, and the questions it can answer about it. Everything here is pure and reads a
 * per-record index built once (O(n) in visits + days, cached by record identity), so a highlight
 * and all of its answers cost O(years · k) where k is the handful of visits on a day.
 */

private const val DAY = 86400L
private const val NIGHT_HOUR = 3
private const val HOME_RADIUS_KM = 30
/** A follow-up chain is cut after this many answers. */
const val MAX_DEPTH = 4

enum class Verb(val text: String) { WOKE_UP_IN("woke up in"), LANDED_IN("landed in"), ARRIVED_IN("arrived in"), WERE_IN("were in") }
enum class Scope { DAY, WEEK }
enum class FactId { TRIP, KM, FIRST, NEW, TRANSIT, WEEK }

/** Anything the map can open exactly: the day range and the places the answer was computed from. */
interface Door { val range: LongRange; val placeIds: List<String>; val playhead: Long? }

data class HFact(val id: FactId, val label: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long? = null, val strip: List<Night>? = null) : Door

data class Highlight(
    /** Local date the highlight is about. */
    val date: String,
    val yearsAgo: Int,
    /** WEEK when the pick came from the ±3-day widening. */
    val scope: Scope,
    val verb: Verb,
    val city: String,
    /** false when the city is unknown and `city` prints coordinates. */
    val cityKnown: Boolean,
    val score: Int,
    val facts: List<HFact>,
    override val range: LongRange,
    override val placeIds: List<String>,
    override val playhead: Long,
    val firstVisit: Visit,
    val trip: Trip?,
    val daily: Daily?,
    val tz: Int,
) : Door

enum class FollowUpId(val question: String) {
    STAY("How long did I stay?"),
    DISTANCE("How far from home?"),
    TRIP("What else happened that trip?"),
    ROUTE("Show me the route"),
    COMPARE("How does this compare to other years?"),
    DID("What did I do that day?"),
    BACK("Have I been back?"),
    FARTHEST("Was this my farthest ever?"),
    LAST_YEAR("What was I doing this time last year?"),
}

sealed interface Answer : Door {
    val title: String
    val detail: String

    data class Stay(val nights: Int, val from: String, val to: String, val strip: List<Night>, override val title: String, override val detail: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long? = null) : Answer
    data class Distance(val km: Double, val leg: LegInfo?, override val title: String, override val detail: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long? = null) : Answer
    data class TripSetlist(val trip: Trip, val cities: List<SetlistCity>, override val title: String, override val detail: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long? = null) : Answer
    data class Route(val legs: Int, val km: Double, val kmByMode: Map<String, Double>, override val title: String, override val detail: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long?) : Answer
    data class Compare(val years: List<YearCol>, override val title: String, override val detail: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long? = null) : Answer
    data class Did(val stops: List<DidStop>, override val title: String, override val detail: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long? = null) : Answer
    data class Back(val times: Int, val last: String?, override val title: String, override val detail: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long? = null) : Answer
    data class Farthest(val isFarthest: Boolean, val date: String, val km: Double, val city: String?, override val title: String, override val detail: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long?) : Answer
    data class HighlightAnswer(val highlight: Highlight, override val title: String, override val detail: String, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long?) : Answer
}

data class LegInfo(val mode: String, val from: String, val to: String, val hours: Int, val range: LongRange)
data class SetlistCity(val city: String, val nights: Int, val first: Boolean, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long? = null) : Door
/** `km` is null when no home is set, so there is nothing to measure from. */
data class YearCol(val year: Int, val city: String?, val km: Double?, val now: Boolean)
data class DidStop(val placeId: String, val name: String, val type: String?, val hours: Double, val start: Long, override val range: LongRange, override val placeIds: List<String>, override val playhead: Long?) : Door
data class FollowUp(val id: FollowUpId, val question: String, val answer: Answer)

sealed interface HighlightState {
    data class Found(val highlight: Highlight) : HighlightState
    data class Quiet(val date: String, val nearest: Highlight?) : HighlightState
    data class Short(val starts: String, val highlight: Highlight) : HighlightState
    data object None : HighlightState
}

// ---------- dates ----------

/** Local calendar date of an instant, given the visit's UTC offset in minutes. */
fun localDay(ts: Long, tz: Int): String = dateOf(dayNum(ts, tz))
private fun midnightUTC(date: String): Long = numOf(date) * DAY
private fun dayStartIn(date: String, tz: Int): Long = midnightUTC(date) - tz * 60L
/** Days since the epoch, in local time: integer arithmetic for the hot loops. */
private fun dayNum(ts: Long, tz: Int): Int = Math.floorDiv(ts + tz * 60L, DAY).toInt()
private fun numOf(date: String): Int = LocalDate.parse(date).toEpochDay().toInt()
private fun dateOf(n: Int): String = LocalDate.ofEpochDay(n.toLong()).toString()
private fun shiftDay(date: String, n: Int): String = dateOf(numOf(date) + n)
private fun yearOf(date: String): Int = date.substring(0, 4).toInt()
private fun yearOfTs(ts: Long): Int = LocalDate.ofEpochDay(Math.floorDiv(ts, DAY)).year
private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
/** The same month/day in year y; 29 Feb falls back to 28 Feb. */
fun sameDayIn(date: String, y: Int): String {
    val md = date.substring(5)
    return "$y-${if (md == "02-29" && !isLeap(y)) "02-28" else md}"
}
/** Today's date in the device zone, as YYYY-MM-DD. */
fun localToday(): String = LocalDate.now().toString()

private val MON = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
/** "12 Nov 2023" from YYYY-MM-DD. */
fun dmy(date: String): String { val (y, m, d) = date.split("-").map { it.toInt() }; return "$d ${MON[m - 1]} $y" }
/** "12–17 Nov 2023", "28 Feb – 2 Mar 2023", "30 Dec 2022 – 2 Jan 2023". */
fun fmtSpan(from: String, to: String): String {
    if (from == to) return dmy(from)
    val (fy, fm, fd) = from.split("-").map { it.toInt() }; val (ty, tm, td) = to.split("-").map { it.toInt() }
    if (fy == ty && fm == tm) return "$fd–$td ${MON[fm - 1]} $fy"
    if (fy == ty) return "$fd ${MON[fm - 1]} – $td ${MON[tm - 1]} $ty"
    return "${dmy(from)} – ${dmy(to)}"
}
private fun n0(x: Double): String = String.format(Locale.US, "%,d", x.roundToLong())
private fun plural(n: Int, one: String) = if (n == 1) one else one + "s"

// ---------- index ----------

internal class HighlightIndex(val data: Record) {
    /** Top-level visits covering each local day number, in start order. */
    val byDay = HashMap<Int, ArrayList<Visit>>()
    val daily = HashMap<String, Daily>()
    /** Where you slept, keyed by the day number of the 03:00 instant. */
    val nights = HashMap<Int, Night>()
    /** The place that claimed each night, same first-come rule as bedStrip. */
    val nightPid = HashMap<Int, String>()
    /** Local date of the first top-level visit to each city. */
    val firstInCity = HashMap<String, String>()
    /** Top-level visits per city in start order. */
    val byCity = HashMap<String, ArrayList<Visit>>()
    /** Sorted distinct local day numbers covered by a visit in each city. */
    val cityDays = HashMap<String, IntArray>()
    val centroid = HashMap<String, LatLng>()
    var farthest: Daily? = null
    val hasHome: Boolean = data.profile.homes.isNotEmpty()
    /** Score of every daily row, filled once in init so readers on any thread only look up. O(days). */
    val scores = HashMap<String, Int>()

    fun homeOf(ts: Long): Located = if (data.profile.homes.isNotEmpty()) homeAt(data.profile.homes, ts) else data.profile.home
    fun atHome(v: Visit): Boolean = isHome(data.places[v.placeId]) || (hasHome && distanceKm(homeOf(v.start), v) < HOME_RADIUS_KM)

    init {
        indexBuilds++
        val dayLists = HashMap<String, ArrayList<Int>>()
        val sums = HashMap<String, DoubleArray>()
        for (v in data.visits) {
            if (v.lvl != 0) continue
            val a = dayNum(v.start, v.tz); val b = dayNum(v.end, v.tz)
            for (d in a..b) byDay.getOrPut(d) { ArrayList() }.add(v)
            // The 03:00 instants inside the stay, each claimed by the first visit that covers it.
            var d = Math.floorDiv(v.start + v.tz * 60L - NIGHT_HOUR * 3600L + DAY - 1, DAY).toInt()
            while (d * DAY - v.tz * 60L + NIGHT_HOUR * 3600L <= v.end) { nightPid.putIfAbsent(d, v.placeId); d++ }
            val city = data.places[v.placeId]?.city ?: continue
            byCity.getOrPut(city) { ArrayList() }.add(v)
            // Visits come in start order, so a day at or before the last pushed one is already covered.
            val cd = dayLists.getOrPut(city) { ArrayList() }
            for (d2 in maxOf(a, (cd.lastOrNull() ?: Int.MIN_VALUE) + 1)..b) cd.add(d2)
            if (!atHome(v) && city !in firstInCity) firstInCity[city] = dateOf(a)
            val s = sums.getOrPut(city) { DoubleArray(3) }
            s[0] += v.lat; s[1] += v.lng; s[2]++
        }
        for ((c, l) in dayLists) cityDays[c] = l.toIntArray()
        for ((c, s) in sums) centroid[c] = LatLng(s[0] / s[2], s[1] / s[2])
        for (d in data.daily) { daily[d.date] = d; if (farthest.let { it == null || d.maxDistFromHomeKm > it.maxDistFromHomeKm }) farthest = d }
        if (data.visits.isNotEmpty()) for (n in bedStrip(data)) if (n.kind != NightKind.NONE) nights[numOf(n.date)] = n
        for (d in data.daily) scores[d.date] = computeScore(data, this, d.date)
    }
}

/** How many times the index was built; tests use it to prove a record is indexed once. */
internal var indexBuilds = 0

private var CACHE: Pair<WeakReference<Record>, HighlightIndex>? = null

/** The per-record index. Built once per `data` identity, O(visits + days covered). */
@Synchronized
internal fun indexOf(data: Record): HighlightIndex {
    CACHE?.let { (ref, ix) -> if (ref.get() === data) return ix }
    val ix = HighlightIndex(data)
    CACHE = WeakReference(data) to ix
    return ix
}

/** Index of the last item with start <= t, or -1. Items sorted by start. */
private fun <T : com.elsewhere.app.data.Spanned> lastStarted(items: List<T>, t: Long): Int {
    var lo = 0; var hi = items.size - 1; var out = -1
    while (lo <= hi) { val m = (lo + hi) ushr 1; if (items[m].start <= t) { out = m; lo = m + 1 } else hi = m - 1 }
    return out
}

// ---------- one day ----------

/** The visit the day is about: the earliest one, skipping a home stay when the day went anywhere else. */
private fun leadVisit(ix: HighlightIndex, visits: List<Visit>): Visit = visits.firstOrNull { !ix.atHome(it) } ?: visits[0]

private fun coords(v: Located) = String.format(Locale.US, "%.2f°, %.2f°", v.lat, v.lng)
private fun cityOf(data: Record, v: Visit): Pair<String, Boolean> {
    val c = data.places[v.placeId]?.city
    return if (c != null) c to true else coords(v) to false
}

/** The first trip overlapping [start, end]. Linear like the web twin: trips number in the dozens. */
private fun tripOn(data: Record, start: Long, end: Long): Trip? = data.trips.firstOrNull { it.start <= end && it.end >= start }

private fun scoreDay(data: Record, ix: HighlightIndex, date: String): Int = ix.scores[date] ?: computeScore(data, ix, date)

private fun computeScore(data: Record, ix: HighlightIndex, date: String): Int {
    val visits = ix.byDay[numOf(date)]
    var s = 0
    if (!visits.isNullOrEmpty()) {
        val lead = leadVisit(ix, visits)
        val d = ix.daily[date]
        val start = dayStartIn(date, lead.tz); val end = start + DAY
        if (d != null) {
            if (d.maxDistFromHomeKm > 100) s += 50
            s += 10 * d.nNewPlaces
            s += min(20, (d.maxDistFromHomeKm / 500).roundToLong().toInt())
            val t = dayType(d)
            if (t == DayType.HOME || t == DayType.WORK) s -= 40
        }
        if (tripOn(data, start, end) != null) s += 30
        val city = data.places[lead.placeId]?.city
        if (city != null && ix.firstInCity[city] == date) s += 15
    }
    return s
}

private fun verbFor(data: Record, ix: HighlightIndex, date: String, lead: Visit, start: Long, end: Long): Verb {
    if (lead.start <= start + NIGHT_HOUR * 3600L && lead.end >= start + NIGHT_HOUR * 3600L) return Verb.WOKE_UP_IN
    val acts = data.activities
    var i = lastStarted(acts, start)
    if (i < 0 || acts[i].end < start) i++
    // The day's first leg is a flight that got you to the first stop: it ended before the stop, or at it.
    if (i < acts.size && acts[i].start < end && lensOf(acts[i].mode) == Lens.FLY && (acts[i].end <= lead.start || distanceKm(LatLng(acts[i].eLat, acts[i].eLng), lead) < 150)) return Verb.LANDED_IN
    val city = data.places[lead.placeId]?.city
    if (ix.nights[numOf(date)]?.city != city) return Verb.ARRIVED_IN
    return Verb.WERE_IN
}

private fun weekStrip(ix: HighlightIndex, date: String): List<Night> {
    val c = numOf(date)
    return (-3..3).map { k -> ix.nights[c + k] ?: Night(dateOf(c + k), NightKind.NONE, null) }
}

private fun factsFor(data: Record, ix: HighlightIndex, date: String, lead: Visit, city: String, known: Boolean, d: Daily?, trip: Trip?, range: LongRange, placeIds: List<String>, playhead: Long): List<HFact> {
    val out = ArrayList<HFact>()
    if (trip != null) {
        val into = ((midnightUTC(date) - midnightUTC(localDay(trip.start, lead.tz))).toDouble() / DAY).roundToLong().toInt()
        out.add(HFact(FactId.TRIP, "${if (into == 0) "Day one of" else "$into ${plural(into, "night")} into"} a ${trip.nights}-night trip", trip.start..trip.end, trip.memory?.destinationPlaceIds ?: placeIds))
    }
    if (ix.hasHome && d != null && d.maxDistFromHomeKm >= 1) out.add(HFact(FactId.KM, "${n0(d.maxDistFromHomeKm)} km from home", range, placeIds, playhead))
    if (known && ix.firstInCity[city] == date) out.add(HFact(FactId.FIRST, "First time in $city", range, placeIds, playhead))
    if (d != null && d.nNewPlaces > 0) out.add(HFact(FactId.NEW, "${d.nNewPlaces} new ${plural(d.nNewPlaces, "place")} that day", range, placeIds, playhead))
    if (d != null && d.hoursTransit >= 0.5) out.add(HFact(FactId.TRANSIT, "${if (d.hoursTransit < 10) String.format(Locale.US, "%.1f", d.hoursTransit) else d.hoursTransit.roundToLong().toString()} h on the move", range, placeIds, playhead))
    if (out.size < 3 && ix.nights.isNotEmpty()) out.add(HFact(FactId.WEEK, "Weather of your week", (range.first - 3 * DAY)..(range.last + 3 * DAY), placeIds, strip = weekStrip(ix, date)))
    return out.take(3)
}

/** The highlight for one specific record day, whatever it scores. Throws when nothing was recorded that day. */
fun highlightOn(data: Record, date: String, yearsAgo: Int, scope: Scope = Scope.DAY): Highlight {
    val ix = indexOf(data)
    val visits = ix.byDay[numOf(date)]
    require(!visits.isNullOrEmpty()) { "no visits on $date" }
    val lead = leadVisit(ix, visits)
    val start = dayStartIn(date, lead.tz); val end = start + DAY
    val (city, known) = cityOf(data, lead)
    val d = ix.daily[date]
    val trip = tripOn(data, start, end)
    val placeIds = visits.map { it.placeId }.distinct()
    val range = start..end
    return Highlight(
        date, yearsAgo, scope, verbFor(data, ix, date, lead, start, end), city, known,
        scoreDay(data, ix, date), factsFor(data, ix, date, lead, city, known, d, trip, range, placeIds, lead.start),
        range, placeIds, lead.start, lead, trip, d, lead.tz,
    )
}

private fun hasVisits(ix: HighlightIndex, date: String) = !ix.byDay[numOf(date)].isNullOrEmpty()

/** Years strictly before `date`'s year that the record touches. */
private fun pastYears(data: Record, date: String): IntRange = yearOfTs(data.meta.range.first)..min(yearOfTs(data.meta.range.last), yearOf(date) - 1)

/**
 * The past day the record should talk about on `date` (YYYY-MM-DD, local). Same month/day in each
 * past year, scored; ties go to the oldest year; nothing positive widens to ±3 days ("this week").
 * Null on a quiet day. O(years · visits-per-day) after the index.
 */
fun highlightFor(data: Record, date: String): Highlight? {
    val ix = indexOf(data)
    val years = pastYears(data, date)
    fun pick(offsets: IntArray, scope: Scope): Highlight? {
        var bestDate: String? = null; var bestY = 0; var bestOff = 0; var bestScore = 0
        for (y in years) for (off in offsets) {
            val d = shiftDay(sameDayIn(date, y), off)
            if (!hasVisits(ix, d)) continue
            val score = scoreDay(data, ix, d)
            if (score <= 0) continue
            if (bestDate == null || score > bestScore || (score == bestScore && (y < bestY || (y == bestY && abs(off) < abs(bestOff))))) { bestDate = d; bestY = y; bestOff = off; bestScore = score }
        }
        return bestDate?.let { highlightOn(data, it, yearOf(date) - yearOf(it), scope) }
    }
    return pick(intArrayOf(0), Scope.DAY) ?: pick(intArrayOf(-3, -2, -1, 1, 2, 3), Scope.WEEK)
}

/** Calendar distance between two month/days, ignoring the year (0..183). */
private fun calendarGap(a: String, b: String): Int {
    val d = abs(numOf(sameDayIn(a, 2001)) - numOf(sameDayIn(b, 2001)))
    return min(d, 365 - d)
}

/** The highlight plus its empty states: quiet day (with the nearest card) and a record younger than a year. */
fun highlightState(data: Record, date: String): HighlightState {
    val ix = indexOf(data)
    if (data.daily.isEmpty() || data.visits.isEmpty()) return HighlightState.None
    val r0 = data.meta.range.first; val r1 = data.meta.range.last
    val positive = data.daily.filter { hasVisits(ix, it.date) && scoreDay(data, ix, it.date) > 0 }
    if (r1 - r0 < 365 * DAY && pastYears(data, date).all { y -> midnightUTC(sameDayIn(date, y)) + 3 * DAY < r0 }) {
        val top = positive.sortedWith(compareByDescending<Daily> { scoreDay(data, ix, it.date) }.thenBy { it.date }).firstOrNull() ?: return HighlightState.None
        // The record starts on the first visit's own local day, not the UTC day of the range.
        val first = data.visits.getOrNull(lastStarted(data.visits, r0 - 1) + 1)
        return HighlightState.Short(if (first != null) localDay(first.start, first.tz) else localDay(r0, 0), highlightOn(data, top.date, yearOf(date) - yearOf(top.date)))
    }
    highlightFor(data, date)?.let { return HighlightState.Found(it) }
    var near: Daily? = null; var gap = Int.MAX_VALUE
    for (d in positive) { val g = calendarGap(d.date, date); if (g < gap) { gap = g; near = d } }
    return HighlightState.Quiet(date, near?.let { highlightOn(data, it.date, yearOf(date) - yearOf(it.date)) })
}

// ---------- follow-ups ----------

/** Contiguous run of nights in the highlight's city around its day, via the nights index. O(run). */
fun stayLength(data: Record, h: Highlight): Answer.Stay? {
    val ix = indexOf(data)
    if (!h.cityKnown) return null
    val d0 = numOf(h.date)
    val seed = listOf(d0, d0 + 1).firstOrNull { ix.nights[it]?.city == h.city } ?: return null
    var from = seed; var to = seed
    while (ix.nights[from - 1]?.city == h.city) from--
    while (ix.nights[to + 1]?.city == h.city) to++
    val strip = ArrayList<Night>(); val ids = LinkedHashSet<String>()
    for (d in from..to) { strip.add(ix.nights.getValue(d)); ix.nightPid[d]?.let { ids.add(it) } }
    val n = strip.size; val fromDate = dateOf(from); val toDate = dateOf(to)
    return Answer.Stay(n, fromDate, toDate, strip, "$n ${plural(n, "night")}", fmtSpan(fromDate, toDate), ((from - 1) * DAY - h.tz * 60L)..((to + 1) * DAY - h.tz * 60L), ids.toList())
}

/** Name for a point: the home city when it is home, else the closest city you have visited within 100 km, else coordinates. O(cities). */
private fun cityNear(data: Record, ix: HighlightIndex, p: Located, ts: Long): String {
    if (ix.hasHome && distanceKm(ix.homeOf(ts), p) < HOME_RADIUS_KM) return if (data.profile.homes.isNotEmpty()) homeAt(data.profile.homes, ts).city else "home"
    var best: String? = null; var bestKm = 100.0
    for ((city, c) in ix.centroid) { val km = distanceKm(c, p); if (km < bestKm) { bestKm = km; best = city } }
    return best ?: coords(p)
}

private val MODE_VERB = mapOf(Lens.FLY to "flew", Lens.TRANSIT to "rode", Lens.DRIVE to "drove", Lens.FOOT to "walked", Lens.ALL to "went")

/** Distance from home to the city's centroid, and the flight or train that ended there. O(log n + legs back to the trip start). */
fun distanceFromHome(data: Record, h: Highlight): Answer.Distance? {
    val ix = indexOf(data)
    if (!ix.hasHome || !h.cityKnown) return null
    val c = ix.centroid[h.city] ?: return null
    val km = distanceKm(ix.homeOf(h.firstVisit.start), c)
    val acts = data.activities
    val floor = h.trip?.start ?: (h.range.first - 14 * DAY)
    var leg: LegInfo? = null
    var i = lastStarted(acts, h.range.last)
    while (i >= 0 && acts[i].start >= floor) {
        val a = acts[i]
        if ((lensOf(a.mode) == Lens.FLY || a.mode == "IN_TRAIN") && distanceKm(LatLng(a.eLat, a.eLng), c) < 150) {
            leg = LegInfo(a.mode, cityNear(data, ix, LatLng(a.sLat, a.sLng), a.start), h.city, ((a.end - a.start).toDouble() / 3600).roundToLong().toInt(), a.start..a.end)
            break
        }
        i--
    }
    val hrs = if (leg != null && leg.hours >= 1) ", ${leg.hours} h" else ""
    return Answer.Distance(
        km, leg, "${n0(km)} km", if (leg != null) "${MODE_VERB.getValue(lensOf(leg.mode))} ${leg.from} → ${leg.to}$hrs" else "from home",
        if (leg != null) leg.range.first..h.range.last else h.range, h.placeIds,
    )
}

/** Where the whole trip went: cities in order with nights slept, firsts flagged. O(trip days). */
fun tripSetlist(data: Record, h: Highlight): Answer.TripSetlist? {
    val ix = indexOf(data)
    val t = h.trip ?: return null
    class Acc(val city: String, var nights: Int, val first: Boolean, val start: Long, var end: Long, val ids: ArrayList<String>)
    val cities = LinkedHashMap<String, Acc>()
    val a = dayNum(t.start, h.tz); val b = dayNum(t.end, h.tz); val aDate = dateOf(a)
    for (d in a + 1..b) {
        val n = ix.nights[d] ?: continue
        if (n.kind == NightKind.HOME || n.city == null) continue
        val pid = ix.nightPid[d]
        val nightStart = (d - 1) * DAY - h.tz * 60L; val nightEnd = (d + 1) * DAY - h.tz * 60L
        val s = cities[n.city]
        if (s != null) { s.nights++; s.end = nightEnd; if (pid != null && pid !in s.ids) s.ids.add(pid) }
        else cities[n.city] = Acc(n.city, 1, (ix.firstInCity[n.city] ?: "") >= aDate, nightStart, nightEnd, if (pid != null) arrayListOf(pid) else ArrayList())
    }
    val list = cities.values.map { SetlistCity(it.city, it.nights, it.first, it.start..it.end, it.ids.toList()) }
    val km = t.totalKmByMode.values.sum()
    return Answer.TripSetlist(
        t, list, list.joinToString(" · ") { it.city }.ifEmpty { t.anchorCities.joinToString(" · ") }, "${t.nights} ${plural(t.nights, "night")} · ${n0(km)} km",
        t.start..t.end, t.memory?.destinationPlaceIds ?: list.flatMap { it.placeIds },
    )
}

/** The legs within the trip (or the day), for a scoped map. O(log n + legs). */
fun routeRange(data: Record, h: Highlight): Answer.Route? {
    val range = h.trip?.let { it.start..it.end } ?: h.range
    val acts = data.activities
    val kmByMode = LinkedHashMap<String, Double>()
    var legs = 0; var km = 0.0
    var i = maxOf(0, lastStarted(acts, range.first))
    while (i < acts.size && acts[i].start <= range.last) {
        val a = acts[i++]
        if (a.end < range.first) continue
        legs++; km += a.m / 1000; kmByMode[a.mode] = (kmByMode[a.mode] ?: 0.0) + a.m / 1000
    }
    if (legs == 0) return null
    return Answer.Route(legs, km, kmByMode, "$legs ${plural(legs, "leg")}", "${n0(km)} km · ${fmtSpan(localDay(range.first, h.tz), localDay(range.last, h.tz))}", range, h.placeIds, range.first)
}

/** Where you were on this month/day in every year of the record; `now` marks today's year. O(years). */
fun compareYears(data: Record, h: Highlight, today: String = localToday()): Answer.Compare? {
    val ix = indexOf(data)
    val years = ArrayList<YearCol>(); val ids = LinkedHashSet<String>()
    for (y in yearOfTs(data.meta.range.first)..yearOfTs(data.meta.range.last)) {
        val date = sameDayIn(h.date, y)
        val d = ix.daily[date] ?: continue
        val visits = ix.byDay[numOf(date)]
        val city = if (!visits.isNullOrEmpty()) cityOf(data, leadVisit(ix, visits)).first else d.cities.firstOrNull()
        years.add(YearCol(y, city, if (ix.hasHome) d.maxDistFromHomeKm else null, y == yearOf(today)))
        visits?.forEach { ids.add(it.placeId) }
    }
    if (years.size < 2) return null
    val far = years.reduce { a, b -> if ((b.km ?: 0.0) > (a.km ?: 0.0)) b else a }
    val detail = if (ix.hasHome) "farthest ${far.year}: ${far.city ?: "unknown"}, ${n0(far.km ?: 0.0)} km" else years.joinToString(" · ") { it.city ?: "—" }
    return Answer.Compare(
        years, "${years.size} years", detail,
        midnightUTC(sameDayIn(h.date, years.first().year))..(midnightUTC(sameDayIn(h.date, years.last().year)) + DAY), ids.toList(),
    )
}

/** Every stop that day with its hours, clipped to the day. O(visits on the day). */
fun whatDidIDo(data: Record, h: Highlight): Answer.Did? {
    val ix = indexOf(data)
    val visits = ix.byDay[numOf(h.date)]
    if (visits.isNullOrEmpty()) return null
    val stops = visits.map { v ->
        val p = data.places[v.placeId]
        val s = maxOf(v.start, h.range.first); val e = min(v.end, h.range.last)
        DidStop(v.placeId, p?.name ?: p?.city ?: cityOf(data, v).first, p?.primaryType, (e - s) / 3600.0, v.start, s..e, listOf(v.placeId), s)
    }
    return Answer.Did(stops, "${stops.size} ${plural(stops.size, "stop")}", stops.joinToString(" · ") { it.name }, h.range, stops.map { it.placeId })
}

/** Separate returns to the city after this stay ended, and the last of them. O(log n + distinct later days in the city). */
fun haveIBeenBack(data: Record, h: Highlight): Answer.Back? {
    val ix = indexOf(data)
    if (!h.cityKnown) return null
    val days = ix.cityDays[h.city] ?: IntArray(0); val list = ix.byCity[h.city] ?: emptyList()
    val d0 = numOf(h.date)
    var lo = 0; var hi = days.size - 1; var i = days.size
    while (lo <= hi) { val m = (lo + hi) ushr 1; if (days[m] > d0) { i = m; hi = m - 1 } else lo = m + 1 }
    // Consecutive days continue the same stay; a gap starts a return.
    var edge = d0; var times = 0
    while (i < days.size) { if (days[i] > edge + 1) times++; edge = days[i]; i++ }
    val last = list.lastOrNull()
    if (times == 0 || last == null || last.start <= h.range.last) {
        val yrs = Math.floorDiv(data.meta.range.last - h.range.last, 365 * DAY).toInt()
        return Answer.Back(0, null, "Never", "$yrs ${plural(yrs, "year")} and counting", h.range.last..data.meta.range.last, emptyList())
    }
    val lastDay = localDay(last.end, last.tz)
    return Answer.Back(
        times, lastDay, when (times) { 1 -> "Once"; 2 -> "Twice"; else -> "$times times" }, "last ${dmy(lastDay)}",
        dayStartIn(localDay(last.start, last.tz), last.tz)..(dayStartIn(lastDay, last.tz) + DAY), listOf(last.placeId), last.start,
    )
}

/** This day's distance against the record's farthest. O(1) after the index. */
fun farthestEver(data: Record, h: Highlight): Answer.Farthest? {
    val ix = indexOf(data)
    val far = ix.farthest
    if (!ix.hasHome || far == null || h.daily == null) return null
    val isFarthest = h.daily.maxDistFromHomeKm >= far.maxDistFromHomeKm
    val visits = ix.byDay[numOf(far.date)]
    val city = if (!visits.isNullOrEmpty()) cityOf(data, leadVisit(ix, visits)).first else far.cities.firstOrNull()
    val tz = visits?.firstOrNull()?.tz ?: h.tz
    return Answer.Farthest(
        isFarthest, far.date, far.maxDistFromHomeKm, city, if (isFarthest) "Yes" else "No",
        if (isFarthest) "${n0(far.maxDistFromHomeKm)} km, the farthest on record" else "${city ?: far.date}, ${n0(far.maxDistFromHomeKm)} km, ${yearOf(far.date)}",
        dayStartIn(far.date, tz)..(dayStartIn(far.date, tz) + DAY), (visits ?: emptyList()).map { it.placeId }.distinct(), visits?.firstOrNull()?.start,
    )
}

/** The same day one year before the highlight, as another highlight, if it scores. */
fun thisTimeLastYear(data: Record, h: Highlight): Answer.HighlightAnswer? {
    val ix = indexOf(data)
    val date = sameDayIn(h.date, yearOf(h.date) - 1)
    if (!hasVisits(ix, date) || scoreDay(data, ix, date) <= 0) return null
    val hl = highlightOn(data, date, h.yearsAgo + 1)
    return Answer.HighlightAnswer(hl, "${hl.verb.text} ${hl.city}", dmy(date), hl.range, hl.placeIds, hl.playhead)
}

private val QUESTIONS: List<Pair<FollowUpId, (Record, Highlight, String) -> Answer?>> = listOf(
    FollowUpId.STAY to { d, h, _ -> stayLength(d, h) },
    FollowUpId.DISTANCE to { d, h, _ -> distanceFromHome(d, h) },
    FollowUpId.TRIP to { d, h, _ -> tripSetlist(d, h) },
    FollowUpId.ROUTE to { d, h, _ -> routeRange(d, h) },
    FollowUpId.COMPARE to ::compareYears,
    FollowUpId.DID to { d, h, _ -> whatDidIDo(d, h) },
    FollowUpId.BACK to { d, h, _ -> haveIBeenBack(d, h) },
    FollowUpId.FARTHEST to { d, h, _ -> farthestEver(d, h) },
    FollowUpId.LAST_YEAR to { d, h, _ -> thisTimeLastYear(d, h) },
)

/** Only the questions that have an answer for this highlight, answers pre-computed. Empty past MAX_DEPTH. */
fun followUps(data: Record, h: Highlight, depth: Int = 0, exclude: Set<FollowUpId> = emptySet(), today: String = localToday()): List<FollowUp> {
    if (depth >= MAX_DEPTH) return emptyList()
    val out = ArrayList<FollowUp>()
    for ((id, fn) in QUESTIONS) {
        if (id in exclude) continue
        val answer = fn(data, h, today) ?: continue
        out.add(FollowUp(id, id.question, answer))
    }
    return out
}
