package com.elsewhere.app.ui.nav

import com.elsewhere.app.data.Place
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min

const val DAY_S = 86_400L

/** Place ids by city, built once per record in O(P) so a city figure can open its stops in O(1). */
class CityIndex(places: Map<String, Place>) {
    private val byCity: Map<String, List<String>> = run {
        val m = HashMap<String, ArrayList<String>>()
        for ((id, p) in places) { val c = p.city ?: continue; m.getOrPut(c) { ArrayList() }.add(id) }
        m
    }
    fun ids(city: String?): List<String> = if (city == null) emptyList() else byCity[city].orEmpty()

    /** The stops of a city as a door; a city the record names no place in frames every pin. */
    fun door(city: String?, label: String): Door.Places = Door.Places(ids(city), label)
}

/** The most visited pin in each group, so a count of countries or cities opens as that many pins. O(P). */
private fun topPer(places: Map<String, Place>, key: (Place) -> String?): List<String> {
    val best = LinkedHashMap<String, Pair<String, Int>>()
    for ((id, p) in places) {
        val k = key(p) ?: continue
        val cur = best[k]
        if (cur == null || p.nVisits > cur.second) best[k] = id to p.nVisits
    }
    return best.values.map { it.first }
}

fun onePerCountry(places: Map<String, Place>) = topPer(places) { it.countryCode }
fun onePerCity(places: Map<String, Place>) = topPer(places) { it.city }

fun dayDoor(ts: Long, label: String): Door.Range { val d = Math.floorDiv(ts, DAY_S) * DAY_S; return Door.Range(d, d + DAY_S - 1, label) }
fun dayDoor(iso: String, label: String): Door.Range = dayDoor(LocalDate.parse(iso).toEpochDay() * DAY_S, label)
/** A calendar year as a window, cut to the record's `bounds`. */
fun yearDoor(year: Int, label: String, bounds: LongRange): Door.Range =
    Door.Range(max(bounds.first, LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()), min(bounds.last, LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - 1), label)
/** A `YYYY-MM` month key as a window, cut to the record's `bounds`. */
fun monthDoor(key: String, label: String, bounds: LongRange): Door.Range {
    val m = LocalDate.parse("$key-01")
    return Door.Range(max(bounds.first, m.atStartOfDay(ZoneOffset.UTC).toEpochSecond()), min(bounds.last, m.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - 1), label)
}
fun rangeDoor(r: LongRange, label: String): Door.Range = Door.Range(r.first, r.last, label)
