package com.elsewhere.app.analytics

import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.modeInProse
import com.elsewhere.app.data.Visit
import com.elsewhere.app.ingest.jsRound
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.withLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// Port of web/src/analytics/facts.ts. Strings are pinned to the web's en-US Intl output.

enum class Kind(val key: String) { EXPLORED("explored"), RECORD("record"), SCALE("scale"), HABIT("habit") }

sealed interface Figure {
    data class Ring(val pct: Double, val label: String) : Figure
    data class Scale(val value: Double, val ref: Double, val refLabel: String, val unit: String, val valueLabel: String? = null) : Figure
    /** `hours` and `arc` are hour-of-day counts and bounds, integers in the web as well. */
    data class Dial(val hours: List<Int>, val hand: Double, val arc: Pair<Int, Int>? = null, val label: String) : Figure
    /** 7 × 24 cells, Monday first. */
    data class Week(val cells: List<Int>) : Figure
    data class Globe(val lat: Double, val lng: Double, val homeLat: Double, val homeLng: Double) : Figure
    data class Beds(val nights: List<Night>) : Figure
}

/**
 * Card facts are the short, unserious ones that turn on the card up top (`quip` is what it says); the rest print below.
 * `door` is where the figure came from: the window, the place or the places that produced it, for the map to open on.
 */
data class Fact(
    val id: String, val kind: Kind, val headline: String, val detail: String,
    val figure: Figure? = null, val card: Boolean = false, val quip: String? = null, val door: Door? = null,
)

private const val EARTH_KM = 40_075.0
private const val MI = 0.621371
private val LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

private fun n0(x: Double): String = String.format(Locale.US, "%,d", jsRound(x).toLong())
private fun n0(x: Int): String = n0(x.toDouble())
private fun f1(x: Double): String = toFixed(x, 1)
private fun long(d: String): String = LocalDate.parse(d).format(LONG_DATE)
private fun longTs(ts: Long): String = long(dayKey(ts))
private const val DAY = 86_400L
private fun dayStart(d: String): Long = LocalDate.parse(d).toEpochDay() * DAY
/**
 * Doors: the whole window, the local day around an instant, the days between two date keys, or the places behind
 * a figure. Each is labelled with the fact's headline once the fact is made, so the labels here are blank.
 */
private fun whole(c: Ctx) = Door.Range(c.range.first, c.range.last, "")
private fun dayAround(ts: Long, tz: Int): Door.Range { val off = tz * 60L; val d = Math.floorDiv(ts + off, DAY) * DAY - off; return Door.Range(d, d + DAY - 1, "") }
private fun days(from: String, to: String) = Door.Range(dayStart(from), dayStart(to) + DAY - 1, "")
private fun pins(ids: Collection<String>) = Door.Places(ids.toList(), "")
/** Every place the record names in a city, whatever its role or whether the window visited it. O(P). */
private fun placesInCity(d: Record, city: String): List<String> = d.places.entries.filter { it.value.city == city }.map { it.key }
private fun localHour(ts: Long, tz: Int): Int = Instant.ofEpochSecond(ts + tz * 60L).atOffset(ZoneOffset.UTC).hour
private fun localDow(ts: Long, tz: Int): Int = Instant.ofEpochSecond(ts + tz * 60L).atOffset(ZoneOffset.UTC).dayOfWeek.value - 1
private fun pct(a: Double, b: Double): Int = jsRound((100 * a) / max(1.0, b)).toInt()
private fun pct(a: Int, b: Int): Int = pct(a.toDouble(), b.toDouble())
private fun pad2(n: Int): String = n.toString().padStart(2, '0')
/** JS truthiness for the optional strings the web tests with `&&`. */
private fun t(s: String?): Boolean = !s.isNullOrEmpty()

/** Named walks a reader can picture; km. */
private val WALKS: List<Pair<Double, String>> = listOf(
    42.0 to "a marathon", 96.0 to "the West Highland Way", 160.0 to "the length of Long Island", 280.0 to "the Jordan Trail",
    500.0 to "the Pennine Way, twice", 800.0 to "the Camino de Santiago", 1_000.0 to "the Via Francigena from the Alps",
    1_400.0 to "Land's End to John o' Groats", 2_200.0 to "the length of Britain and back", 3_500.0 to "the Appalachian Trail",
    4_300.0 to "the Pacific Crest Trail", 5_000.0 to "the Continental Divide Trail", 6_400.0 to "the Great Wall of China",
    9_000.0 to "Cape Town to Cairo", 14_000.0 to "Patagonia to Alaska",
)

private class Ctx(
    val d: Record, val raw: Record, val u: Units, val dist: (Double) -> String, val cv: (Double) -> Double,
    val nights: List<Night>, val range: LongRange, val isFirst: (Visit) -> Boolean,
) { val unit: String get() = u.name.lowercase() }

private fun sleeps(d: Record) = d.visits.filter { it.lvl == 0 && nightsIn(it) > 0 }
private fun kmOf(d: Record, pred: (String) -> Boolean) = d.activities.sumOf { if (pred(it.mode)) it.m / 1000 else 0.0 }

private fun hrs(h: Double) = if (h >= 10) n0(h) else f1(h)
// Judged on the primary type only, so a hotel with a bar is a hotel. Hours are capped per visit: a bar
// next door soaks up home time in the raw record, and nobody shops for groceries for nine hours.
private fun typed(d: Record, re: Regex): Map<String, Place> = d.places.filterValues { it.role == null && re.containsMatchIn(it.primaryType ?: "") }
private fun hoursOf(ps: Map<String, Place>, capPerVisit: Double = 4.0) = ps.values.sumOf { min(it.totalHours, it.nVisits * capPerVisit) }
private fun visitsOf(ps: Map<String, Place>) = ps.values.sumOf { it.nVisits }
private fun everyDays(n: Int, range: LongRange) = max(1L, jsRound((range.last - range.first) / 86400.0 / max(1, n)).toLong())
private val BAR = Regex("bar$|pub$|night_club|brewery|cocktail"); private val GYM = Regex("^gym$|fitness|yoga|climbing|sports_club")
private val CAFE = Regex("^cafe$|coffee_shop|^coffee"); private val GROCERY = Regex("grocery|supermarket|convenience_store")
private val AIRPORT = Regex("airport"); private val FOOD = Regex("restaurant|diner|pizza|burger|ramen|sushi|taco")
private val SHOP = Regex("store|shop|mall|boutique|market"); private val STATION = Regex("transit_station|bus_station|subway_station|train_station|light_rail")
private const val AUDIOBOOK_H = 117.0

private val GEN: List<(Ctx) -> Fact?> = listOf(
    // Circumnavigations
    { c ->
        val km = kmOf(c.d) { true }
        if (km < 1_000) null else {
            val laps = km / EARTH_KM
            val head = if (laps >= 1) "${f1(laps)}× around the Earth" else "${jsRound(laps * 100).toInt()}% of the way around the Earth"
            Fact("laps", Kind.SCALE, headline = head,
                detail = "${c.dist(km)} on record, over ground, rail, water and air. The equator is ${c.dist(EARTH_KM)}.",
                figure = Figure.Scale(c.cv(km), c.cv(EARTH_KM), "Equator", c.unit), door = whole(c))
        }
    },
    // Walked equals
    { c ->
        val km = kmOf(c.d) { lensOf(it) == Lens.FOOT }
        if (km < 30) null else {
            val (ref, name) = WALKS.reduce { best, w -> if (abs(ln(w.first / km)) < abs(ln(best.first / km))) w else best }
            val ratio = km / ref
            val how = if (ratio > 1.15) "${f1(ratio)}× " else if (ratio < 0.85) "${jsRound(ratio * 100).toInt()}% of " else ""
            Fact("walked", Kind.SCALE, headline = "Walked $how$name",
                detail = "${c.dist(km)} on foot. ${name[0].uppercaseChar()}${name.substring(1)} is about ${c.dist(ref)}.",
                figure = Figure.Scale(c.cv(km), c.cv(ref), name.replaceFirst(Regex("^the "), ""), c.unit), door = whole(c))
        }
    },
    // Longest streak home
    { c ->
        val r = longestRun(c.nights) { it.kind == NightKind.HOME }
        if (r == null || r.n < 14) null else Fact("streak-home", Kind.RECORD,
            headline = "${r.n} nights in your own bed, straight",
            detail = "${long(r.from)} to ${long(r.to)}. Your longest stretch without a night away.",
            figure = Figure.Beds(c.nights.filter { it.date >= r.from && it.date <= r.to }), door = days(r.from, r.to))
    },
    // Longest streak away
    { c ->
        val r = longestRun(c.nights) { it.kind == NightKind.NEW || it.kind == NightKind.REPEAT }
        if (r == null || r.n < 3) null else {
            val cities = c.nights.filter { it.date >= r.from && it.date <= r.to && t(it.city) }.map { it.city!! }.distinct()
            val list = if (cities.isEmpty()) "" else " · ${cities.take(4).joinToString(", ")}${if (cities.size > 4) " and ${cities.size - 4} more" else ""}"
            Fact("streak-away", Kind.RECORD, headline = "${r.n} nights away without a night home",
                detail = "${long(r.from)} to ${long(r.to)}$list.",
                figure = Figure.Beds(c.nights.filter { it.date >= r.from && it.date <= r.to }), door = days(r.from, r.to))
        }
    },
    // Furthest from home
    { c ->
        val home = c.d.profile.home
        var best: Visit? = null; var bestKm = 0.0
        for (v in c.d.visits) { if (v.lvl != 0) continue; val km = distanceKm(home, v); if (km > bestKm) { bestKm = km; best = v } }
        if (best == null || bestKm < 100) null else {
            val p = c.d.places[best.placeId]
            val where = p?.name ?: p?.city ?: "an unnamed place"
            Fact("furthest", Kind.RECORD, headline = "${c.dist(bestKm)} from home",
                detail = "$where${if (t(p?.city) && t(p?.name)) ", ${p!!.city}" else ""}${if (t(p?.countryCode)) " (${p!!.countryCode})" else ""}, ${longTs(best.start)}. The furthest your record gets.",
                figure = Figure.Globe(best.lat, best.lng, home.lat, home.lng), door = Door.Place(best.placeId, best.start, ""))
        }
    },
    // Fastest ground leg
    { c ->
        var bestKmh = 0.0; var bestMode = ""; var bestTs = 0L; var bestEnd = 0L
        for (a in c.d.activities) {
            val lens = lensOf(a.mode)
            // Timeline sometimes labels a flight "walking"; only legs plausible for their mode count.
            if (lens == Lens.FLY || lens == Lens.FOOT || a.end - a.start < 20 * 60 || a.m < 20_000) continue
            val kmh = (a.m / 1000) / ((a.end - a.start) / 3600.0)
            if (kmh > bestKmh && kmh < (if (lens == Lens.DRIVE) 180 else 350)) { bestKmh = kmh; bestMode = a.mode; bestTs = a.start; bestEnd = a.end }
        }
        if (bestKmh < 60) null else {
            val v = if (c.u == Units.MI) bestKmh * MI else bestKmh
            val head = "${n0(v)} ${if (c.u == Units.MI) "mph" else "km/h"} door to door"
            Fact("fastest", Kind.RECORD, headline = head,
                detail = "Your quickest leg over land, ${longTs(bestTs)}, by ${modeInProse(bestMode)}. Averaged over the whole leg, stops included.",
                door = Door.Range(bestTs, bestEnd, ""))
        }
    },
    // Earliest departure from home
    { c ->
        val d = c.d
        val hours = IntArray(24)
        var eh = -1; var em = 0; var ets = 0L; var etz = 0
        for (i in 1 until d.visits.size) {
            val prev = d.visits[i - 1]; val v = d.visits[i]
            if (!isHome(d.places[prev.placeId]) || isHome(d.places[v.placeId]) || prev.lvl != 0 || v.lvl != 0) continue
            val loc = Instant.ofEpochSecond(prev.end + prev.tz * 60L).atOffset(ZoneOffset.UTC)
            val h = loc.hour; val m = loc.minute
            hours[h]++
            val score = if (h < 12) h * 60 + m else 24 * 60
            if (score >= 3 * 60 && (eh < 0 || score < eh * 60 + em)) { eh = h; em = m; ets = prev.end; etz = prev.tz }
        }
        if (eh < 0 || hours.sum() < 20) null else {
            val label = "${pad2(eh)}:${pad2(em)}"
            Fact("earliest", Kind.RECORD, headline = "Out the door at $label",
                detail = "${longTs(ets)}, your earliest start. The clock points at it; the faint bars are the hours you usually leave home.",
                figure = Figure.Dial(hours.toList(), eh + em / 60.0, null, label), door = dayAround(ets, etz))
        }
    },
    // Night owl
    { c ->
        val d = c.d
        val hours = IntArray(24)
        var late = 0
        for (a in d.activities) { val h = localHour(a.start, a.tz); hours[h]++; if (h >= 22 || h < 4) late++ }
        val n = d.activities.size
        if (n < 50) null else {
            val share = pct(late, n)
            val peak = hours.indices.fold(0) { b, h -> if (hours[h] > hours[b]) h else b }
            val head = if (share >= 15) "Night owl · $share% of legs after 10 pm" else "$share% of your legs start after 10 pm"
            Fact("owl", Kind.HABIT, headline = head,
                detail = "${n0(late)} of ${n0(n)} journeys began between 22:00 and 04:00 local time. The hand points at your busiest hour.",
                figure = Figure.Dial(hours.toList(), peak + 0.5, 22 to 28, "${pad2(peak)}:00"), door = whole(c))
        }
    },
    // Weekend explorer
    { c ->
        val cells = IntArray(7 * 24)
        var firsts = 0; var weekend = 0
        val found = LinkedHashSet<String>()
        for (v in nonRoutine(c.d)) {
            if (!c.isFirst(v)) continue
            firsts++
            val dow = localDow(v.start, v.tz)
            cells[dow * 24 + localHour(v.start, v.tz)]++
            if (dow >= 5) { weekend++; found.add(v.placeId) }
        }
        if (firsts < 20) null else {
            val share = pct(weekend, firsts)
            Fact("weekend", Kind.HABIT, headline = "$share% of new places found on a weekend",
                detail = "${n0(firsts)} first visits; ${n0(weekend)} were on a Saturday or Sunday. Two days of seven is ${jsRound(200 / 7.0).toInt()}%.",
                figure = Figure.Week(cells.toList()), door = pins(found))
        }
    },
    // Cities per 1,000 km
    { c ->
        val d = c.d
        val km = kmOf(d) { true }
        val cities = HashSet<String>(); val beds = LinkedHashSet<String>()
        for (v in sleeps(d)) { val p = d.places[v.placeId]; val city = p?.city; if (t(city) && !isHome(p)) { cities.add(city!!); beds.add(v.placeId) } }
        if (km < 2_000 || cities.size < 2) null else {
            val per = (1000 * cities.size) / km
            Fact("density", Kind.SCALE, headline = "A new bed every ${c.dist(km / cities.size)}",
                detail = "${cities.size} cities slept in over ${c.dist(km)} of travel: ${f1(per)} cities per 1,000 km.",
                door = pins(beds))
        }
    },
    // Passed through, never slept
    { c ->
        val d = c.d
        val slept = HashSet<String>()
        for (v in sleeps(d)) { val city = d.places[v.placeId]?.city; if (t(city)) slept.add(city!!) }
        val passed = LinkedHashMap<String, Int>()
        for (v in d.visits) {
            val p = d.places[v.placeId]; val city = p?.city
            if (v.lvl == 0 && t(city) && p!!.role == null && city!! !in slept) passed[city] = (passed[city] ?: 0) + 1
        }
        val top = passed.entries.maxByOrNull { it.value }
        if (top == null || top.value < 5) null else Fact("passed", Kind.HABIT,
            headline = "${top.key}: ${top.value} visits, never a night",
            detail = "The city you keep passing through without ever sleeping there.",
            door = pins(placesInCity(d, top.key)))
    },
    // New Year's Eve
    { c ->
        val d = c.d
        val out = ArrayList<String>()
        val ids = ArrayList<String>()
        val y0 = yearOf(c.range.first); val y1 = yearOf(c.range.last)
        for (y in y0 + 1..y1) {
            val ts = LocalDate.of(y, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
            val v = d.visits.firstOrNull { it.lvl == 0 && it.start - it.tz * 60L <= ts && it.end - it.tz * 60L >= ts } ?: continue
            ids.add(v.placeId)
            val p = d.places[v.placeId]
            val where = if (isHome(p)) "home" else p?.name ?: p?.city ?: "somewhere unnamed"
            out.add("$y: $where${if (!isHome(p) && t(p?.city) && t(p?.name)) ", ${p!!.city}" else ""}")
        }
        if (out.isEmpty()) null else {
            val head = if (out.size == 1) "Midnight on New Year's, ${out[0].take(4)}" else "${out.size} New Year's Eves on record"
            Fact("nye", Kind.HABIT, headline = head, detail = out.joinToString(" · ") + ".", door = pins(ids))
        }
    },
    // Time in motion
    { c ->
        val d = c.d
        val h = d.activities.sumOf { (it.end - it.start) / 3600.0 }
        if (h < 48) null else {
            val days = h / 24
            val head = "${if (days >= 10) n0(days) else f1(days)} whole days in motion"
            Fact("motion", Kind.SCALE, headline = head,
                detail = "${n0(h)} hours in a vehicle or on foot between places: ${pct(h, (d.meta.range.last - d.meta.range.first) / 3600.0)}% of the record.",
                door = whole(c))
        }
    },
    // Bars against gyms
    { c ->
        val barPs = typed(c.d, BAR); val gymPs = typed(c.d, GYM)
        val bars = hoursOf(barPs); val gym = hoursOf(gymPs)
        if (bars < 4 && gym < 4) null else {
            val head = if (gym > bars) "Gym ${hrs(gym)} h · bars ${hrs(bars)} h" else "Bars ${hrs(bars)} h · gym ${hrs(gym)} h"
            val detail = when {
                gym == 0.0 -> "${hrs(bars)} hours in bars and pubs. No gym appears anywhere in this record. Balance is a myth anyway."
                gym > bars -> "The gym is winning, ${f1(gym / max(0.1, bars))} hours of reps to every hour at the bar. Suspicious."
                else -> "${f1(bars / max(0.1, gym))} hours at the bar for every hour at the gym. The science is unsettled on whether that cancels out."
            }
            val quip = when {
                gym == 0.0 -> "${hrs(bars)} hours in bars, zero at the gym. Balanced."
                gym > bars -> "More gym than bar. Who even are you?"
                else -> "${hrs(bars)} hours in bars, ${hrs(gym)} at the gym. Your liver is the one lifting."
            }
            Fact("bars", Kind.HABIT, head, detail, Figure.Scale(bars, gym, "Gym", "h", "Bars"), card = true, quip = quip, door = pins(barPs.keys + gymPs.keys))
        }
    },
    // Coffee runs
    { c ->
        val ps = typed(c.d, CAFE); val n = visitsOf(ps)
        if (n < 8) null else Fact("coffee", Kind.HABIT, card = true,
            headline = "${n0(n)} coffee runs · one every ${everyDays(n, c.range)} days",
            detail = "${hrs(hoursOf(ps, 3.0))} hours in ${ps.size} cafés, waiting for, drinking, or pretending to work over coffee.",
            quip = "${n0(n)} coffee runs. That's not a habit, that's a personality.", door = pins(ps.keys))
    },
    // Airports
    { c ->
        val ps = typed(c.d, AIRPORT); val h = hoursOf(ps, 8.0)
        if (h < 6) null else Fact("airports", Kind.SCALE, headline = "${hrs(h)} hours inside airports",
            detail = "${f1(h / 8)} full working days of boarding groups, gate changes and ${if (h > 40) "overpriced" else "surprisingly fine"} sandwiches.",
            figure = Figure.Scale(h, 40.0, "A working week", "h"), door = pins(ps.keys))
    },
    // Grocery runs
    { c ->
        val ps = typed(c.d, GROCERY); val n = visitsOf(ps)
        if (n < 6) null else Fact("grocery", Kind.HABIT, card = true, headline = "${n0(n)} grocery runs",
            detail = "One every ${everyDays(n, c.range)} days, ${hrs(hoursOf(ps, 2.0))} hours in the aisles. Nobody has ever gone in for just milk.",
            quip = "${n0(n)} grocery runs, zero of them 'just for milk'.", door = pins(ps.keys))
    },
    // Restaurant loyalty
    { c ->
        val e = typed(c.d, FOOD).entries.filter { t(it.value.name) && it.value.nVisits >= 4 }.maxByOrNull { it.value.nVisits }
        val p = e?.value
        if (p == null) null else Fact("regular", Kind.HABIT, card = true, headline = "${p.name}, ${p.nVisits} times", door = Door.Place(e.key, label = ""),
            detail = "Your most loyal restaurant${if (t(p.city)) ", in ${p.city}" else ""}. ${hrs(p.totalHours)} hours at the table. There are other menus; you have chosen not to read them.",
            quip = "${p.name} has seen you ${p.nVisits} times. They know your order.")
    },
    // Longest time without leaving the house
    { c ->
        val d = c.d
        var best: Visit? = null
        for (v in d.visits) if (v.lvl == 0 && isHome(d.places[v.placeId]) && (best == null || v.end - v.start > best.end - best.start)) best = v
        if (best == null || best.end - best.start < 36 * 3600) null else {
            val h = (best.end - best.start) / 3600.0
            Fact("hermit", Kind.RECORD, headline = "${hrs(h)} hours without leaving the house",
                detail = "${longTs(best.start)} to ${longTs(best.end)}. ${if (h >= 72) "The outside world managed without you." else "A proper weekend in."}",
                door = Door.Range(best.start, best.end, ""))
        }
    },
    // Longest single stay away
    { c ->
        val d = c.d
        var best: Visit? = null
        for (v in d.visits) { val p = d.places[v.placeId]; if (v.lvl == 0 && p?.role == null && (best == null || v.end - v.start > best.end - best.start)) best = v }
        if (best == null || best.end - best.start < 20 * 3600) null else {
            val p = d.places[best.placeId]; val h = (best.end - best.start) / 3600.0
            val head = "${hrs(h)} hours straight at ${p?.name ?: p?.city ?: "one place"}"
            Fact("longest-stay", Kind.RECORD, headline = head,
                detail = "From ${longTs(best.start)}. Your longest unbroken stay anywhere that wasn't home or work${if (t(p?.name) && t(p?.city)) ", in ${p!!.city}" else ""}.",
                door = Door.Place(best.placeId, best.start, ""))
        }
    },
    // Days since anywhere new
    { c ->
        val d = c.d
        var last = 0L
        for (v in nonRoutine(d)) if (c.isFirst(v) && v.start > last) last = v.start
        if (last == 0L || nonRoutine(c.raw).size < 20) null else {
            val days = floor((c.range.last - last) / 86400.0).toInt()
            if (days < 3) null else {
                val id = d.visits.firstOrNull { it.start == last }?.placeId
                val p = d.places[id ?: ""]
                Fact("since-new", Kind.HABIT, card = true, headline = "${n0(days)} days since you went anywhere new",
                    door = if (id != null) Door.Place(id, last, "") else whole(c),
                    detail = "Last first visit: ${p?.name ?: p?.city ?: "an unnamed place"}, ${longTs(last)}. ${if (days > 30) "The map is not going to explore itself." else "Not bad. Keep it up."}",
                    quip = "${n0(days)} days since anywhere new. The map is judging you.")
            }
        }
    },
    // Time zones lived in
    { c ->
        val tz = LinkedHashMap<Int, Double>()
        for (v in c.d.visits) if (v.lvl == 0) tz[v.tz] = (tz[v.tz] ?: 0.0) + (v.end - v.start) / 3600.0
        val zones = tz.entries.filter { it.value >= 24 }.map { it.key }
        if (zones.size < 2) null else {
            fun fmt(m: Int) = "UTC${if (m < 0) "−" else "+"}${abs(m) / 60}${if (abs(m) % 60 != 0) ":${pad2(abs(m) % 60)}" else ""}"
            val lo = zones.min(); val hi = zones.max()
            Fact("zones", Kind.EXPLORED, headline = "${zones.size} time zones slept in",
                detail = "Your body clock has run from ${fmt(lo)} to ${fmt(hi)}, a ${f1((hi - lo) / 60.0)}-hour spread. Counting only zones you spent a full day in.",
                figure = Figure.Ring((100.0 * zones.size) / 24, "of 24"), door = whole(c))
        }
    },
    // Hours behind the wheel
    { c ->
        val h = c.d.activities.sumOf { if (lensOf(it.mode) == Lens.DRIVE) (it.end - it.start) / 3600.0 else 0.0 }
        if (h < 20) null else {
            val times = h / AUDIOBOOK_H
            Fact("wheel", Kind.SCALE, headline = "${hrs(h)} hours in a car",
                detail = "Enough to hear all seven Harry Potter audiobooks ${if (times >= 1) "${f1(times)} times over" else "${jsRound(times * 100).toInt()}% of the way through"}. Or sit in silence. Your call.",
                figure = Figure.Scale(h, AUDIOBOOK_H, "All seven audiobooks", "h"), door = whole(c))
        }
    },
    // Homebody share
    { c ->
        val known = c.nights.filter { it.kind != NightKind.NONE }
        if (known.size < 30) null else {
            val home = pct(known.count { it.kind == NightKind.HOME }, known.size)
            Fact("homebody", Kind.HABIT, card = true, headline = "$home% of nights in your own bed", detail = "${n0(known.size)} nights on record.",
                door = whole(c),
                quip = when {
                    home >= 85 -> "$home% of nights in your own bed. Homebody."
                    home >= 60 -> "$home% of nights at home. The other ${100 - home}% is where the stories are."
                    else -> "Only $home% of nights at home. Does your landlord know?"
                })
        }
    },
    // Second city
    { c ->
        val d = c.d
        val h = LinkedHashMap<String, Double>()
        for (v in d.visits) {
            val p = d.places[v.placeId]; val city = p?.city
            if (v.lvl == 0 && t(city) && p!!.role == null && !isHome(p)) h[city!!] = (h[city] ?: 0.0) + (v.end - v.start) / 3600.0
        }
        val homeCity = d.places.values.firstOrNull { isHome(it) }?.city
        val top = h.entries.filter { it.key != homeCity }.maxByOrNull { it.value }
        if (top == null || top.value < 48) null else Fact("second-city", Kind.HABIT, card = true,
            headline = "${top.key}: ${hrs(top.value)} hours", detail = "The city you spend most time in that isn't home.",
            door = pins(placesInCity(d, top.key)),
            quip = "${hrs(top.value)} hours in ${top.key}. At this point it's a situationship.")
    },
    // Shop hours
    { c ->
        val ps = typed(c.d, SHOP); val h = hoursOf(ps, 3.0)
        if (h < 10) null else Fact("shops", Kind.HABIT, card = true, headline = "${hrs(h)} hours in shops", detail = "Capped at three hours a visit.",
            quip = "${hrs(h)} hours in shops. \"Just looking,\" you said, ${n0(visitsOf(ps))} times.", door = pins(ps.keys))
    },
    // Transit stops
    { c ->
        val ps = typed(c.d, STATION); val n = visitsOf(ps)
        if (n < 10) null else Fact("transit", Kind.HABIT, card = true, headline = "${n0(n)} station visits", detail = "${hrs(hoursOf(ps, 1.0))} hours on platforms.",
            quip = "${n0(n)} times through a station, ${hrs(hoursOf(ps, 1.0))} hours on platforms. The train is always \"2 minutes\" away.", door = pins(ps.keys))
    },
)

/**
 * Every fact the range supports, explored → record → scale → habit. Units from the profile.
 * `data` is the range-filtered record; `raw` the whole one, so firsts are judged over all time.
 */
fun facts(data: Record, units: Units = Units.KM, raw: Record = data): List<Fact> {
    val range = data.meta.range
    val dist = { km: Double -> if (units == Units.MI) "${n0(km * MI)} mi" else "${n0(km)} km" }
    val cv = { km: Double -> if (units == Units.MI) km * MI else km }
    // First visit ever to a place: the earliest raw visit starts no sooner than this one.
    val isFirst = { v: Visit -> val f = raw.places[v.placeId]?.firstTs; f == null || v.start <= f }
    val ctx = Ctx(data, raw, units, dist, cv, bedStrip(raw, range), range, isFirst)
    // Every fact's door reads as its headline.
    return GEN.mapNotNull { it(ctx) }.map { f -> f.copy(door = f.door?.withLabel(f.headline)) }.sortedBy { it.kind.ordinal }
}
