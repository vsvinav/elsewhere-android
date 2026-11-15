package com.elsewhere.app.analytics

import com.elsewhere.app.data.Record
import com.elsewhere.app.ui.tabs.numbers.Peak
import java.time.LocalDate

// Port of web/src/analytics/hub.ts: the figures the Play deck prints that no other tab already has.
// Everything is one pass over the record; the per-year index is built once and the cards read it.

/** FNV-1a over the text, the same walk the barcode takes. */
fun fnv(text: String): Long {
    var h = 2166136261L
    for (c in text) { h = h xor c.code.toLong(); h = (h * 16777619L) and 0xffffffffL }
    return h
}

/** The fact for the day: the date hashed onto the list, so it turns daily and reads the same all day. */
fun <T> todaysFact(list: List<T>, date: String): T? = if (list.isEmpty()) null else list[(fnv(date) % list.size).toInt()]

data class Streaks(
    /** Longest run of consecutive nights away from home. */
    val away: Run?,
    /** Longest run of consecutive weeks (Monday to Sunday) with a new place in each; dates span those weeks. */
    val newWeeks: Run?,
    /** Nights away in a row at the end of the record; 0 when it ends at home. */
    val current: Int,
)

private fun dayNum(date: String): Long = LocalDate.parse(date).toEpochDay()
private fun dateOf(n: Long): String = LocalDate.ofEpochDay(n).toString()
/** Monday-based week index: the epoch fell on a Thursday, three days after a Monday. */
private fun weekOf(date: String): Long = Math.floorDiv(dayNum(date) + 3, 7L)
private fun mondayOf(week: Long): String = dateOf(week * 7 - 3)

private fun isAway(n: Night) = n.kind == NightKind.NEW || n.kind == NightKind.REPEAT

fun streaks(data: Record): Streaks {
    if (data.visits.isEmpty() && data.daily.isEmpty()) return Streaks(null, null, 0)
    val nights = if (data.visits.isEmpty()) emptyList() else bedStrip(data)
    val away = longestRun(nights, ::isAway)
    // Nights the record has not reached yet are skipped at the tail; a gap inside the run ends it.
    var i = nights.lastIndex
    while (i >= 0 && nights[i].kind == NightKind.NONE) i--
    var current = 0
    while (i >= 0 && isAway(nights[i])) { current++; i-- }
    // Daily rows come in date order; distinct weeks with a new place, then the longest chain of adjacent ones.
    var newWeeks: Run? = null
    var w0 = 0L; var w1 = 0L; var open = false
    for (d in data.daily) {
        if (d.nNewPlaces <= 0) continue
        val w = weekOf(d.date)
        if (open && w == w1) continue
        if (open && w == w1 + 1) w1 = w else { w0 = w; w1 = w; open = true }
        val n = (w1 - w0 + 1).toInt()
        if (newWeeks == null || n > newWeeks.n) newWeeks = Run(mondayOf(w0), dateOf(w1 * 7 - 3 + 6), n)
    }
    return Streaks(away, newWeeks, current)
}

data class YearFacts(val year: Int, val places: Int, val nightsAway: Int, val km: Double, val countries: Int)

private fun localYear(ts: Long, tz: Int): Int = LocalDate.ofEpochDay(Math.floorDiv(ts + tz * 60L, 86400L)).year

/** Per calendar year, oldest first: distinct places stopped at away from home, nights away, kilometres moved, countries. */
fun yearIndex(data: Record): Map<Int, YearFacts> {
    class Acc(val year: Int) { val places = HashSet<String>(); val countries = HashSet<String>(); var nights = 0; var km = 0.0 }
    val acc = HashMap<Int, Acc>()
    fun of(y: Int) = acc.getOrPut(y) { Acc(y) }
    for (v in data.visits) {
        if (v.lvl != 0) continue
        val p = data.places[v.placeId]
        if (isHome(p)) continue
        val a = of(localYear(v.start, v.tz))
        a.places.add(v.placeId)
        p?.countryCode?.let { a.countries.add(it) }
    }
    for (x in data.activities) of(localYear(x.start, x.tz)).km += x.m / 1000
    if (data.visits.isNotEmpty()) for (n in bedStrip(data)) if (isAway(n)) of(n.date.substring(0, 4).toInt()).nights++
    return acc.values.sortedBy { it.year }.associate { it.year to YearFacts(it.year, it.places.size, it.nights, it.km, it.countries.size) }
        .let { LinkedHashMap(it) }
}

/** Years the record touches, oldest first. */
fun yearsWithData(index: Map<Int, YearFacts>): List<Int> = index.keys.toList()

/** Two years side by side; a year with nothing on record prints zeros. */
fun compareYears(index: Map<Int, YearFacts>, a: Int, b: Int): Pair<YearFacts, YearFacts> {
    fun of(y: Int) = index[y] ?: YearFacts(y, 0, 0, 0.0, 0)
    return of(a) to of(b)
}

fun compareYears(data: Record, a: Int, b: Int): Pair<YearFacts, YearFacts> = compareYears(yearIndex(data), a, b)

/** The next year with data after `cur`, wrapping, never the one on the other chip. */
fun cycleYear(years: List<Int>, cur: Int, other: Int): Int {
    val n = years.size
    if (n < 2) return cur
    var i = years.indexOf(cur)
    repeat(n) { i = (i + 1) % n; if (years[i] != other) return years[i] }
    return cur
}

/** The hottest cell of a weekday × hour grid, or null when nothing was away. */
fun busiestHour(rhythm: List<List<Double>>): Peak? {
    var best: Peak? = null
    rhythm.forEachIndexed { d, row -> row.forEachIndexed { h, v -> if (v > 0 && (best == null || v > best!!.v)) best = Peak(d, h, v) } }
    return best
}

/** Everything the deck prints, computed once per record. */
data class HubFigures(val streaks: Streaks, val years: Map<Int, YearFacts>, val rhythm: List<List<Double>>, val rhythmMax: Double, val peak: Peak?) {
    val yearList: List<Int> get() = yearsWithData(years)
    /** The latest two years with data, the pair the Compare card opens on. */
    val latestPair: Pair<Int, Int>? get() = yearList.let { if (it.size >= 2) it[it.size - 2] to it.last() else it.firstOrNull()?.let { y -> y to y } }
}

fun hubFigures(data: Record): HubFigures {
    val s = stats(data)
    return HubFigures(streaks(data), yearIndex(data), s.rhythm, s.rhythmMax, busiestHour(s.rhythm))
}
