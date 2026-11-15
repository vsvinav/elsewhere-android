package com.elsewhere.app.analytics

import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Daily
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Located
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Spanned
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Visit
import com.elsewhere.app.ingest.jsRound
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// Port of web/src/analytics/highlights.ts: "On this day" and the questions you can ask it.
//
// Everything is answered off one index built once per record (`HighlightIndex`, cached by identity
// like `legsOf`): visits and legs bucketed by the local day they touch, every night's bed, the first
// day in each city, trips and strokes in sorted arrays for binary search, and a score per day. A
// question is then O(log n + k) in the rows it touches; nothing walks the whole record twice.

private const val DAY = 86400L
private const val WAKE_HOUR = 3
private const val AWAY_KM = 100.0
private const val WIDEN_DAYS = 3
const val MAX_DEPTH = 4

enum class Verb(val text: String) { WOKE_UP("woke up in"), LANDED("landed in"), ARRIVED("arrived in"), WERE_IN("were in") }
enum class FactKind { TRIP, DISTANCE, FIRST, NEW_PLACES, TRANSIT, WEEK }

/** A Tier 1 fact: one line, and the door it opens. */
data class HFact(val kind: FactKind, val label: String, val range: LongRange, val placeIds: List<String>, val strip: List<Night> = emptyList())

data class Highlight(
    /** The past local date, yyyy-MM-dd. */
    val date: String,
    val today: String,
    val yearsAgo: Int,
    /** Found within ±3 days rather than on the day itself. */
    val thisWeek: Boolean,
    /** City of the first visit, or printed coordinates when the place book has no city. */
    val city: String,
    val named: Boolean,
    val verb: Verb,
    val score: Int,
    /** The local day, in absolute seconds. */
    val range: LongRange,
    /** Start of the day's first visit. */
    val playhead: Long,
    val placeIds: List<String>,
    val facts: List<HFact>,
) {
    val headline: String get() {
        val lead = when {
            yearsAgo <= 0 -> "On ${shortDate(date)}"
            thisWeek -> "This week, ${agoText(yearsAgo)}"
            else -> "On this day, ${agoText(yearsAgo)}"
        }
        return "$lead — you ${verb.text} $city."
    }
}

sealed interface HighlightResult {
    data class Found(val highlight: Highlight) : HighlightResult
    /** Nothing scored within ±3 days of this date in any year; `nearest` is the closest day that did. */
    data class Quiet(val today: String, val nearest: Highlight?) : HighlightResult
    /** The record is younger than a year: highlights begin on `start` next year; `best` is its top day meanwhile. */
    data class Short(val start: String, val best: Highlight?) : HighlightResult
}

enum class Question(val label: String, val crumb: String) {
    STAY("How long did I stay?", "the stay"),
    DISTANCE("How far from home?", "from home"),
    TRIP("What else happened that trip?", "the trip"),
    ROUTE("Show me the route", "the route"),
    COMPARE("How does this compare to other years?", "compare years"),
    DID("What did I do that day?", "that day"),
    BACK("Have I been back?", "been back"),
    FARTHEST("Was this my farthest ever?", "farthest"),
    LAST_YEAR("What was I doing this time last year?", "last year"),
}

/** Every answer knows the day range and place ids it was computed from, so the map door is exact. */
sealed interface Answer {
    val title: String
    val detail: String
    val range: LongRange
    val placeIds: List<String>

    data class Stay(val nights: Int, val from: String, val to: String, val strip: List<Night>, override val range: LongRange, override val placeIds: List<String>) : Answer {
        override val title get() = "$nights ${plural(nights, "night")} · ${spanText(from, to)}"
        override val detail get() = "An unbroken run of nights in ${strip.firstOrNull()?.city ?: "the same bed"}."
    }
    data class LegIn(val mode: String, val seconds: Long, val km: Double)
    data class Distance(val km: Double, val leg: LegIn?, override val range: LongRange, override val placeIds: List<String>) : Answer {
        override val title get() = "${n0(km)} km from home"
        override val detail get() = leg?.let { "${legVerb(it.mode)}, ${hm(it.seconds)}" } ?: "No leg on record got you there."
    }
    data class CityNights(val city: String, val nights: Int, val first: Boolean, val range: LongRange, val placeIds: List<String>)
    data class TripSetlist(val trip: Trip, val cities: List<CityNights>, override val range: LongRange, override val placeIds: List<String>) : Answer {
        override val title get() = "${trip.nights} ${plural(trip.nights, "night")} · ${cities.size} ${plural(cities.size, "city", "cities")}"
        override val detail get() = cities.filter { it.first }.takeIf { it.isNotEmpty() }?.let { "First time in ${it.joinToString(", ") { c -> c.city }}." } ?: "All cities you had slept in before."
    }
    data class Route(val trip: Trip, val legs: Int, val playhead: Long, override val range: LongRange, override val placeIds: List<String>) : Answer {
        override val title get() = "$legs ${plural(legs, "leg")} · ${n0(trip.totalKmByMode.values.sum())} km"
        override val detail get() = trip.totalKmByMode.entries.sortedByDescending { it.value }.joinToString(" · ") { "${MODE_LABEL[it.key] ?: it.key} ${n0(it.value)} km" }
    }
    data class YearRow(val year: Int, val city: String?, val km: Double, val now: Boolean)
    data class Compare(val rows: List<YearRow>, override val range: LongRange, override val placeIds: List<String>) : Answer {
        override val title get() = "${rows.size} years on this date"
        override val detail get() = rows.maxByOrNull { it.km }?.let { "Farthest: ${it.city ?: "unknown"}, ${it.year}." } ?: ""
    }
    data class StopRow(val placeId: String, val name: String, val type: String?, val start: Long, val end: Long) { val hours get() = (end - start) / 3600.0 }
    data class Ledger(val stops: List<StopRow>, override val range: LongRange, override val placeIds: List<String>) : Answer {
        override val title get() = "${stops.size} ${plural(stops.size, "stop")}"
        override val detail get() = stops.joinToString(" · ") { it.name }
    }
    data class Back(val times: Int, val last: String?, val yearsSince: Int, override val range: LongRange, override val placeIds: List<String>) : Answer {
        override val title get() = if (times == 0) "Never — ${if (yearsSince <= 1) "a year" else "$yearsSince years"} and counting" else "${timesText(times)} · last ${shortDate(last!!)}"
        override val detail get() = if (times == 0) "No visit to that city since." else "Days in that city since."
    }
    data class Farthest(val isFarthest: Boolean, val city: String?, val km: Double, val date: String, override val range: LongRange, override val placeIds: List<String>) : Answer {
        override val title get() = if (isFarthest) "Yes — ${n0(km)} km, your record" else "No — ${city ?: "unknown"}, ${n0(km)} km, ${date.take(4)}"
        override val detail get() = if (isFarthest) "The farthest from home your record goes." else "That day is one tap away."
    }
    data class LastYear(val highlight: Highlight) : Answer {
        override val title get() = highlight.headline
        override val detail get() = highlight.facts.joinToString(" · ") { it.label }
        override val range get() = highlight.range
        override val placeIds get() = highlight.placeIds
    }
}

data class FollowUp(val question: Question, val answer: Answer, val subject: Highlight)

// ---- index ----

/** How many times the index was built; tests use it to prove a record is indexed once. */
internal var indexBuilds = 0

/** Sorted-by-start rows with a prefix max of `end`, so the rows overlapping a window are found by two binary searches. */
private class SpanIndex<T : Spanned>(val rows: List<T>) {
    private val maxEnd = LongArray(rows.size).also { a -> var m = Long.MIN_VALUE; for (i in rows.indices) { m = maxOf(m, rows[i].end); a[i] = m } }
    fun overlapping(range: LongRange): List<T> {
        if (rows.isEmpty()) return emptyList()
        // first row whose prefix max end reaches the window; last row that starts inside it
        var lo = 0; var hi = rows.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (maxEnd[m] < range.first) lo = m + 1 else hi = m }
        val from = lo
        lo = 0; hi = rows.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (rows[m].start <= range.last) lo = m + 1 else hi = m }
        val to = lo
        if (from >= to) return emptyList()
        val out = ArrayList<T>()
        for (i in from until to) if (rows[i].end >= range.first) out.add(rows[i])
        return out
    }
}

private fun localDay(ts: Long, tz: Int): String = dayKey(ts + tz * 60L)
private fun epochDay(date: String): Long = LocalDate.parse(date).toEpochDay()

private class HighlightIndex(val data: Record) {
    val visits: List<Visit> = data.visits.filter { it.lvl == 0 }
    val spans = SpanIndex(visits)
    val legs = SpanIndex(data.activities)
    val strokes = SpanIndex(if (sorted(data.strokes)) data.strokes else data.strokes.sortedBy { it.start })
    val trips = SpanIndex(if (sorted(data.trips)) data.trips else data.trips.sortedBy { it.start })
    val daily: Map<String, Daily> = data.daily.associateBy { it.date }
    /** Visits touching each waking day (03:00 to 03:00, in the visit's own zone), in start order. */
    val byDay = HashMap<String, ArrayList<Visit>>()
    val legsByDay = HashMap<String, ArrayList<Activity>>()
    val nightAt: Map<String, Night>
    val firstCityDay = HashMap<String, String>()
    /** Top-level visits per city, in start order, so "have I been back" is a binary search. */
    val cityVisits = HashMap<String, ArrayList<Visit>>()
    val cityCentroid: Map<String, LatLng>
    val hasHome: Boolean = data.profile.homes.isNotEmpty() || data.profile.home.lat != 0.0 || data.profile.home.lng != 0.0
    val score = HashMap<String, Int>()
    val scoredDays: LongArray
    val farthestDay: Daily? = data.daily.maxWithOrNull(compareBy<Daily> { it.maxDistFromHomeKm }.thenByDescending { it.date })
    val years: IntRange = run {
        var lo = yearOf(data.meta.range.first); var hi = yearOf(data.meta.range.last)
        for (d in data.daily) { val y = d.date.substring(0, 4).toInt(); if (y < lo) lo = y; if (y > hi) hi = y }
        lo..hi
    }

    init {
        indexBuilds++
        for (v in visits) {
            // Every local day whose 03:00–27:00 window the stay overlaps.
            var d = Math.floorDiv(v.start + v.tz * 60L - WAKE_HOUR * 3600L, DAY)
            val last = Math.floorDiv(v.end - 1 + v.tz * 60L - WAKE_HOUR * 3600L, DAY)
            while (d <= last) { byDay.getOrPut(LocalDate.ofEpochDay(d).toString()) { ArrayList(2) }.add(v); d++ }
            val city = data.places[v.placeId]?.city ?: continue
            cityVisits.getOrPut(city) { ArrayList() }.add(v)
            val day = localDay(v.start, v.tz)
            val prev = firstCityDay[city]
            if (prev == null || day < prev) firstCityDay[city] = day
        }
        for (a in data.activities) {
            var d = Math.floorDiv(a.start + a.tz * 60L - WAKE_HOUR * 3600L, DAY)
            val last = Math.floorDiv(a.end - 1 + a.tz * 60L - WAKE_HOUR * 3600L, DAY)
            while (d <= last) { legsByDay.getOrPut(LocalDate.ofEpochDay(d).toString()) { ArrayList(2) }.add(a); d++ }
        }
        nightAt = if (data.visits.isEmpty()) emptyMap() else bedStrip(data).filter { it.kind != NightKind.NONE }.associateBy { it.date }
        val sums = HashMap<String, DoubleArray>()
        for (p in data.places.values) {
            val c = p.city ?: continue
            val s = sums.getOrPut(c) { DoubleArray(3) }
            s[0] += p.lat; s[1] += p.lng; s[2] += 1
        }
        cityCentroid = sums.mapValues { (_, s) -> LatLng(s[0] / s[2], s[1] / s[2]) }
        for (day in byDay.keys) score[day] = scoreDay(day)
        scoredDays = score.entries.filter { it.value > 0 }.map { epochDay(it.key) }.sorted().toLongArray()
    }

    private fun <T : Spanned> sorted(rows: List<T>): Boolean { for (i in 1 until rows.size) if (rows[i].start < rows[i - 1].start) return false; return true }

    fun tripAt(range: LongRange): Trip? = trips.overlapping(range).firstOrNull()

    fun dayRange(day: String, tz: Int): LongRange { val a = epochDay(day) * DAY - tz * 60L; return a..(a + DAY - 1) }

    fun scoreDay(day: String): Int {
        val vs = byDay[day] ?: return 0
        if (vs.isEmpty()) return 0
        val first = vs[0]
        val d = daily[day]
        var s = 0
        if (d != null) {
            if (d.maxDistFromHomeKm > AWAY_KM) s += 50
            s += 10 * d.nNewPlaces
            s += min(20, jsRound(d.maxDistFromHomeKm / 500).toInt())
            val t = dayType(d)
            if (t == DayType.HOME || t == DayType.WORK) s -= 40
        }
        if (tripAt(dayRange(day, first.tz)) != null) s += 30
        val city = data.places[first.placeId]?.city
        if (city != null && firstCityDay[city] == day) s += 15
        return s
    }

    /** The scored day (epoch day) nearest to `target`, or null when none scored. */
    fun nearestScored(target: Long): Long? {
        if (scoredDays.isEmpty()) return null
        var lo = 0; var hi = scoredDays.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (scoredDays[m] < target) lo = m + 1 else hi = m }
        val cands = listOfNotNull(scoredDays.getOrNull(lo - 1), scoredDays.getOrNull(lo))
        return cands.minByOrNull { abs(it - target) }
    }
}

private class IndexCache(visits: List<Visit>, daily: List<Daily>, places: Map<String, *>, val index: HighlightIndex) {
    val visits = WeakReference(visits); val daily = WeakReference(daily); val places = WeakReference(places)
}
private var CACHE: IndexCache? = null

private fun indexOf(data: Record): HighlightIndex {
    CACHE?.let { if (it.visits.get() === data.visits && it.daily.get() === data.daily && it.places.get() === data.places && it.index.data.profile == data.profile) return it.index }
    val ix = HighlightIndex(data)
    CACHE = IndexCache(data.visits, data.daily, data.places, ix)
    return ix
}

// ---- picking ----

/** The same month and day in year `y`; 29 Feb falls back to 28 Feb. */
internal fun sameDay(date: LocalDate, y: Int): LocalDate = LocalDate.of(y, date.month, min(date.dayOfMonth, LocalDate.of(y, date.month, 1).lengthOfMonth()))

/** The score of one local day, as the spec tallies it; 0 when nothing was visited. */
fun scoreOf(data: Record, date: LocalDate): Int = indexOf(data).score[date.toString()] ?: 0

fun highlightFor(data: Record, date: LocalDate): Highlight? = (highlightsFor(data, date) as? HighlightResult.Found)?.highlight

fun highlightsFor(data: Record, date: LocalDate): HighlightResult {
    val ix = indexOf(data)
    if (ix.visits.isEmpty()) return HighlightResult.Quiet(date.toString(), null)
    pick(ix, date, 0)?.let { return HighlightResult.Found(build(ix, it, date, thisWeek = false)) }
    pick(ix, date, WIDEN_DAYS)?.let { return HighlightResult.Found(build(ix, it, date, thisWeek = true)) }
    val r = data.meta.range
    if (r.last - r.first < 365 * DAY) {
        val best = ix.score.entries.filter { it.value > 0 }.minWithOrNull(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        return HighlightResult.Short(dayKey(r.first), best?.let { build(ix, it.key, date, thisWeek = false) })
    }
    var nearest: String? = null; var bestDist = Long.MAX_VALUE
    for (y in ix.years) {
        if (y >= date.year) continue
        val target = sameDay(date, y).toEpochDay()
        val n = ix.nearestScored(target) ?: continue
        if (n >= date.toEpochDay()) continue
        val dist = abs(n - target)
        if (dist < bestDist) { bestDist = dist; nearest = LocalDate.ofEpochDay(n).toString() }
    }
    return HighlightResult.Quiet(date.toString(), nearest?.let { build(ix, it, date, thisWeek = false) })
}

/** Best day within ±`widen` of this date in every past year: highest score, then oldest year, then nearest the date. */
private fun pick(ix: HighlightIndex, date: LocalDate, widen: Int): String? {
    var best: String? = null; var bestScore = 0
    for (y in ix.years) {
        if (y >= date.year) continue
        val centre = sameDay(date, y)
        for (off in OFFSETS) {
            if (abs(off) > widen) break
            val day = centre.plusDays(off.toLong())
            if (!day.isBefore(date)) continue
            val s = ix.score[day.toString()] ?: continue
            if (s > bestScore) { best = day.toString(); bestScore = s }
        }
    }
    return best
}

/** 0, −1, +1, −2, +2, −3, +3: the day itself first, then outwards. */
private val OFFSETS = (0..WIDEN_DAYS).flatMap { if (it == 0) listOf(0) else listOf(-it, it) }

/** How many days within ±3 of this date, across past years, score above zero: the Sunday "this week in other years" test. */
fun weekCandidates(data: Record, date: LocalDate): Int {
    val ix = indexOf(data)
    var n = 0
    for (y in ix.years) {
        if (y >= date.year) continue
        val centre = sameDay(date, y)
        for (off in -WIDEN_DAYS..WIDEN_DAYS) if ((ix.score[centre.plusDays(off.toLong()).toString()] ?: 0) > 0) n++
    }
    return n
}

private fun coords(p: Located): String =
    String.format(Locale.US, "%.2f°%s %.2f°%s", abs(p.lat), if (p.lat >= 0) "N" else "S", abs(p.lng), if (p.lng >= 0) "E" else "W")

private fun build(ix: HighlightIndex, day: String, today: LocalDate, thisWeek: Boolean): Highlight {
    val data = ix.data
    val vs = ix.byDay.getValue(day)
    val first = vs[0]
    val range = ix.dayRange(day, first.tz)
    val place = data.places[first.placeId]
    val city = place?.city
    val prevNight = ix.nightAt[day]?.city
    val wokeUp = first.start < range.first && nightsIn(first) > 0
    val firstLeg = ix.legsByDay[day]?.firstOrNull()
    val verb = when {
        wokeUp -> Verb.WOKE_UP
        firstLeg != null && firstLeg.mode == "FLYING" && firstLeg.start <= first.start -> Verb.LANDED
        city == null || city != prevNight -> Verb.ARRIVED
        else -> Verb.WERE_IN
    }
    val placeIds = vs.map { it.placeId }.distinct()
    val d = ix.daily[day]
    val facts = ArrayList<HFact>(3)
    fun add(f: HFact) { if (facts.size < 3) facts.add(f) }
    ix.tripAt(range)?.let { t ->
        val n = (epochDay(day) - epochDay(localDay(t.start, first.tz))).toInt()
        if (n >= 1) add(HFact(FactKind.TRIP, "$n ${plural(n, "night")} into a ${t.nights}-night trip", t.start..t.end, placeIds))
    }
    if (ix.hasHome && d != null && d.maxDistFromHomeKm > 0) add(HFact(FactKind.DISTANCE, "${n0(d.maxDistFromHomeKm)} km from home", range, placeIds))
    if (city != null && ix.firstCityDay[city] == day) add(HFact(FactKind.FIRST, "First time in $city", range, placeIds))
    if (d != null && d.nNewPlaces > 0) add(HFact(FactKind.NEW_PLACES, "${d.nNewPlaces} new ${plural(d.nNewPlaces, "place")} that day", range, placeIds))
    if (d != null && d.hoursTransit >= 1) add(HFact(FactKind.TRANSIT, "${jsRound(d.hoursTransit).toInt()} h on the move", range, placeIds))
    if (facts.size < 3) {
        val strip = weekStrip(ix, day)
        if (strip.any { it.kind != NightKind.NONE }) add(HFact(FactKind.WEEK, "Weather of your week", weekRange(day, first.tz), placeIds, strip))
    }
    return Highlight(
        date = day, today = today.toString(), yearsAgo = today.year - LocalDate.parse(day).year, thisWeek = thisWeek,
        city = city ?: coords(first), named = city != null, verb = verb, score = ix.score[day] ?: 0,
        range = range, playhead = first.start, placeIds = placeIds, facts = facts,
    )
}

private fun weekRange(day: String, tz: Int): LongRange { val a = (epochDay(day) - 3) * DAY - tz * 60L; return a..(a + 7 * DAY - 1) }

private fun weekStrip(ix: HighlightIndex, day: String): List<Night> {
    val d0 = LocalDate.parse(day).minusDays(3)
    return List(7) { i -> val d = d0.plusDays(i.toLong()).toString(); ix.nightAt[d] ?: Night(d, NightKind.NONE, null) }
}

/** The seven nights around `day`, one cell each, as the week fact prints them. */
fun weekStrip(data: Record, day: String): List<Night> = weekStrip(indexOf(data), day)

/** The record cut to `range` by binary search, for a scoped map: O(log n + k) rather than a pass over every row. */
fun dayRecord(data: Record, range: LongRange): Record {
    val ix = indexOf(data)
    val visits = ix.spans.overlapping(range)
    val ids = visits.mapTo(HashSet()) { it.placeId }
    return data.copy(
        visits = visits, activities = ix.legs.overlapping(range), strokes = ix.strokes.overlapping(range), paths = emptyList(),
        trips = ix.trips.overlapping(range), places = data.places.filterKeys { it in ids }, daily = emptyList(),
        meta = data.meta.copy(range = range),
    )
}

/** Where you slept that day, in sheet order: the stops the scoped map pins. */
fun dayStops(data: Record, h: Highlight): List<Stop> {
    val ix = indexOf(data)
    val seen = LinkedHashMap<String, Stop>()
    for (v in ix.byDay[h.date] ?: emptyList()) {
        val p = data.places[v.placeId]
        val city = p?.city ?: continue
        if (city !in seen) seen[city] = Stop(city, p.countryCode, nightsIn(v), 1, v.start, v.end, v.lat, v.lng)
    }
    return seen.values.toList()
}

// ---- follow-ups ----

/** The questions this highlight can answer, each already answered. */
fun followUps(data: Record, h: Highlight): List<FollowUp> {
    val ix = indexOf(data)
    val out = ArrayList<FollowUp>()
    fun offer(q: Question, a: Answer?) { if (a != null) out.add(FollowUp(q, a, h)) }
    offer(Question.STAY, stayLength(ix, h))
    offer(Question.DISTANCE, distance(ix, h))
    val trip = ix.tripAt(h.range)
    offer(Question.TRIP, trip?.let { tripSetlist(ix, it) })
    offer(Question.ROUTE, trip?.let { route(ix, it) })
    offer(Question.COMPARE, compareYears(ix, h))
    offer(Question.DID, ledger(ix, h))
    offer(Question.BACK, beenBack(ix, h))
    offer(Question.FARTHEST, farthest(ix, h))
    offer(Question.LAST_YEAR, lastYear(data, h))
    return out
}

/**
 * What an answer at `depth` can be asked next: nothing past [MAX_DEPTH]; a chained highlight card
 * offers its own menu; any other answer offers the rest of its subject's menu.
 */
fun followUps(data: Record, from: FollowUp, depth: Int): List<FollowUp> {
    if (depth >= MAX_DEPTH) return emptyList()
    val a = from.answer
    if (a is Answer.LastYear) return followUps(data, a.highlight)
    return followUps(data, from.subject).filter { it.question != from.question }
}

private fun cityOf(ix: HighlightIndex, h: Highlight): String? = if (h.named) h.city else null

private fun cityPlaceIds(ix: HighlightIndex, range: LongRange, city: String): List<String> =
    ix.spans.overlapping(range).filter { ix.data.places[it.placeId]?.city == city }.map { it.placeId }.distinct()

fun stayLength(data: Record, h: Highlight): Answer.Stay? = stayLength(indexOf(data), h)
private fun stayLength(ix: HighlightIndex, h: Highlight): Answer.Stay? {
    val city = cityOf(ix, h) ?: return null
    val d = LocalDate.parse(h.date)
    fun bed(x: LocalDate) = ix.nightAt[x.toString()]?.city == city
    // The run may start on the morning after: the night belonging to `date` is the one you woke from.
    var a = d; var b = d
    if (!bed(d)) { if (bed(d.plusDays(1))) { a = d.plusDays(1); b = a } else return null }
    while (bed(a.minusDays(1))) a = a.minusDays(1)
    while (bed(b.plusDays(1))) b = b.plusDays(1)
    val strip = ArrayList<Night>()
    var x = a
    while (!x.isAfter(b)) { strip.add(ix.nightAt.getValue(x.toString())); x = x.plusDays(1) }
    val tz = ix.byDay[h.date]?.firstOrNull()?.tz ?: 0
    val range = ((a.toEpochDay() - 1) * DAY - tz * 60L)..(b.toEpochDay() * DAY + DAY - 1 - tz * 60L)
    return Answer.Stay(strip.size, a.toString(), b.toString(), strip, range, cityPlaceIds(ix, range, city))
}

fun distance(data: Record, h: Highlight): Answer.Distance? = distance(indexOf(data), h)
private fun distance(ix: HighlightIndex, h: Highlight): Answer.Distance? {
    if (!ix.hasHome) return null
    val city = cityOf(ix, h) ?: return null
    val centroid = ix.cityCentroid[city] ?: return null
    val km = distanceKm(homeOf(ix.data, h.playhead), centroid)
    // The last leg to end near the city in the three days before you were first there.
    val leg = ix.legs.overlapping((h.playhead - 3 * DAY)..h.playhead)
        .lastOrNull { it.end <= h.playhead && distanceKm(LatLng(it.eLat, it.eLng), centroid) < 50 }
        ?.let { Answer.LegIn(it.mode, it.end - it.start, it.m / 1000) }
    return Answer.Distance(km, leg, h.range, h.placeIds)
}

fun tripSetlist(data: Record, trip: Trip): Answer.TripSetlist = tripSetlist(indexOf(data), trip)
private fun tripSetlist(ix: HighlightIndex, t: Trip): Answer.TripSetlist {
    val range = t.start..t.end
    val nights = HashMap<String, Int>()
    var d = LocalDate.parse(dayKey(t.start))
    val end = LocalDate.parse(dayKey(t.end)).plusDays(1)
    while (!d.isAfter(end)) { ix.nightAt[d.toString()]?.city?.let { nights[it] = (nights[it] ?: 0) + 1 }; d = d.plusDays(1) }
    val visits = ix.spans.overlapping(range)
    val byCity = LinkedHashMap<String, ArrayList<Visit>>()
    for (v in visits) { val c = ix.data.places[v.placeId]?.city ?: continue; byCity.getOrPut(c) { ArrayList() }.add(v) }
    val a = dayKey(t.start); val b = dayKey(t.end)
    // Anchor cities first, then any other city slept in; a home night after the return is not part of the trip.
    val cities = (t.anchorCities + byCity.keys).distinct().filter { it in byCity || it in t.anchorCities }.map { c ->
        val vs = byCity[c] ?: emptyList()
        val r = if (vs.isEmpty()) range else vs.minOf { it.start }..vs.maxOf { it.end }
        val first = ix.firstCityDay[c]?.let { it in a..b } ?: false
        Answer.CityNights(c, nights[c] ?: 0, first, r, vs.map { it.placeId }.distinct())
    }
    return Answer.TripSetlist(t, cities, range, visits.map { it.placeId }.distinct())
}

fun route(data: Record, trip: Trip): Answer.Route? = route(indexOf(data), trip)
private fun route(ix: HighlightIndex, t: Trip): Answer.Route? {
    val range = t.start..t.end
    val legs = ix.legs.overlapping(range)
    if (legs.isEmpty()) return null
    return Answer.Route(t, legs.size, t.start, range, ix.spans.overlapping(range).map { it.placeId }.distinct())
}

fun compareYears(data: Record, h: Highlight): Answer.Compare? = compareYears(indexOf(data), h)
private fun compareYears(ix: HighlightIndex, h: Highlight): Answer.Compare? {
    val date = LocalDate.parse(h.date); val today = LocalDate.parse(h.today)
    val rows = ArrayList<Answer.YearRow>()
    for (y in ix.years) {
        val day = sameDay(date, y).toString()
        val d = ix.daily[day] ?: continue
        val city = d.cities.firstOrNull() ?: ix.nightAt[day]?.city
        rows.add(Answer.YearRow(y, city, d.maxDistFromHomeKm, y == today.year))
    }
    if (rows.size < 2) return null
    return Answer.Compare(rows, h.range, h.placeIds)
}

fun ledger(data: Record, h: Highlight): Answer.Ledger? = ledger(indexOf(data), h)
private fun ledger(ix: HighlightIndex, h: Highlight): Answer.Ledger? {
    val vs = ix.byDay[h.date] ?: return null
    if (vs.isEmpty()) return null
    val stops = vs.map { v -> val p = ix.data.places[v.placeId]; Answer.StopRow(v.placeId, placeName(p, v.placeId), p?.primaryType, v.start, v.end) }
    return Answer.Ledger(stops, h.range, h.placeIds)
}

fun beenBack(data: Record, h: Highlight): Answer.Back? = beenBack(indexOf(data), h)
private fun beenBack(ix: HighlightIndex, h: Highlight): Answer.Back? {
    val city = cityOf(ix, h) ?: return null
    val all = ix.cityVisits[city] ?: return null
    var lo = 0; var hi = all.size
    while (lo < hi) { val m = (lo + hi) ushr 1; if (all[m].start <= h.range.last) lo = m + 1 else hi = m }
    val later = all.subList(lo, all.size)
    val days = later.mapTo(LinkedHashSet()) { localDay(it.start, it.tz) }
    val today = LocalDate.parse(h.today)
    val range = if (later.isEmpty()) h.range else later.first().start..later.maxOf { it.end }
    return Answer.Back(days.size, days.maxOrNull(), today.year - LocalDate.parse(h.date).year, range, later.map { it.placeId }.distinct().ifEmpty { h.placeIds })
}

fun farthest(data: Record, h: Highlight): Answer.Farthest? = farthest(indexOf(data), h)
private fun farthest(ix: HighlightIndex, h: Highlight): Answer.Farthest? {
    if (!ix.hasHome) return null
    val top = ix.farthestDay ?: return null
    val mine = ix.daily[h.date]?.maxDistFromHomeKm ?: 0.0
    val isFarthest = mine >= top.maxDistFromHomeKm
    val tz = ix.byDay[top.date]?.firstOrNull()?.tz ?: 0
    val range = if (isFarthest) h.range else ix.dayRange(top.date, tz)
    val ids = if (isFarthest) h.placeIds else (ix.byDay[top.date]?.map { it.placeId }?.distinct() ?: emptyList()).ifEmpty { h.placeIds }
    return Answer.Farthest(isFarthest, top.cities.firstOrNull() ?: ix.nightAt[top.date]?.city, top.maxDistFromHomeKm, top.date, range, ids)
}

fun lastYear(data: Record, h: Highlight): Answer.LastYear? =
    highlightFor(data, LocalDate.parse(h.date).minusYears(1))?.let { Answer.LastYear(it.copy(today = h.today, yearsAgo = LocalDate.parse(h.today).year - LocalDate.parse(it.date).year)) }

// ---- text ----

private val SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
private val DAY_MON = DateTimeFormatter.ofPattern("d MMM", Locale.US)

private fun n0(x: Double): String = String.format(Locale.US, "%,d", jsRound(x).toLong())
private fun plural(n: Int, one: String, many: String = one + "s"): String = if (n == 1) one else many
internal fun shortDate(date: String): String = LocalDate.parse(date).format(SHORT)
private fun agoText(years: Int): String = if (years == 1) "last year" else "$years years ago"
private fun timesText(n: Int): String = when (n) { 1 -> "Once"; 2 -> "Twice"; else -> "$n times" }
private fun hm(seconds: Long): String { val h = seconds / 3600; val m = (seconds % 3600) / 60; return "$h h ${m.toString().padStart(2, '0')}" }
private fun legVerb(mode: String): String = when (lensOf(mode)) { Lens.FLY -> "Flew in"; Lens.TRANSIT -> "Rode in"; Lens.FOOT -> "Walked in"; else -> "Drove in" }

/** "14–16 Nov 2022", or "28 Feb – 2 Mar 2023" across a month. */
internal fun spanText(from: String, to: String): String {
    val a = LocalDate.parse(from); val b = LocalDate.parse(to)
    if (a == b) return a.format(SHORT)
    return if (a.year == b.year && a.month == b.month) "${a.dayOfMonth}–${b.format(SHORT)}" else "${a.format(DAY_MON)} – ${b.format(SHORT)}"
}
