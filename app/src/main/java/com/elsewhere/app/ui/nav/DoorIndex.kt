package com.elsewhere.app.ui.nav

import com.elsewhere.app.data.Place
import java.time.LocalDate
import java.time.ZoneOffset

const val DAY_S = 86_400L

/** Place ids by city, built once per record in O(P) so a city figure can open its stops in O(1). */
class CityIndex(places: Map<String, Place>) {
    private val byCity: Map<String, List<String>> = run {
        val m = HashMap<String, ArrayList<String>>()
        for ((id, p) in places) { val c = p.city ?: continue; m.getOrPut(c) { ArrayList() }.add(id) }
        m
    }
    fun ids(city: String?): List<String> = if (city == null) emptyList() else byCity[city].orEmpty()

    /** The stops of a city as a door, or `fallback` when the record names no place there. */
    fun door(city: String?, label: String, fallback: Door? = null): Door? {
        val ids = ids(city)
        return when (ids.size) { 0 -> fallback; 1 -> Door.Place(ids[0], label = label); else -> Door.Places(ids, label) }
    }
}

fun dayDoor(ts: Long, label: String): Door.Range { val d = Math.floorDiv(ts, DAY_S) * DAY_S; return Door.Range(d, d + DAY_S - 1, label) }
fun dayDoor(iso: String, label: String): Door.Range = dayDoor(LocalDate.parse(iso).toEpochDay() * DAY_S, label)
fun yearDoor(year: Int, label: String = "$year"): Door.Range =
    Door.Range(LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond(), LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - 1, label)
/** A `YYYY-MM` month key as a window. */
fun monthDoor(key: String, label: String): Door.Range {
    val m = LocalDate.parse("$key-01")
    return Door.Range(m.atStartOfDay(ZoneOffset.UTC).toEpochSecond(), m.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - 1, label)
}
fun rangeDoor(r: LongRange, label: String): Door.Range = Door.Range(r.first, r.last, label)
