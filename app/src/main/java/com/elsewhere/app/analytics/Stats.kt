package com.elsewhere.app.analytics

import com.elsewhere.app.data.Daily
import com.elsewhere.app.data.Record
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min

// Port of web/src/analytics/stats.ts

data class MonthKm(val key: String, var total: Double, val byMode: MutableMap<String, Double>)
enum class DayType(val key: String) { AWAY("away"), WORK("work"), OUT("out"), HOME("home") }
data class CityRow(val city: String, val country: String?, val visits: Int, val places: Int, val hours: Double, val first: Long, val last: Long)
data class Stats(
    val months: List<MonthKm>,
    /** hours away from home, [weekday 0=Mon..6][hour 0..23], in the visit's local time */
    val rhythm: List<List<Double>>,
    val rhythmMax: Double,
    val dayTypes: Map<DayType, Int>,
    val cities: List<CityRow>,
)

fun dayType(d: Daily): DayType {
    if (d.maxDistFromHomeKm > 100) return DayType.AWAY
    if (d.hoursWork >= 2) return DayType.WORK
    if (d.hoursOther >= 3) return DayType.OUT
    return DayType.HOME
}

private class CityAcc(val city: String, val country: String?, var visits: Int, var hours: Double, var first: Long, var last: Long, val ids: HashSet<String>)

fun stats(data: Record): Stats {
    val byMonth = LinkedHashMap<String, MonthKm>()
    val dayTypes = LinkedHashMap<DayType, Int>().apply { for (t in DayType.entries) put(t, 0) }
    for (d in data.daily) {
        val key = d.date.substring(0, 7)
        val m = byMonth.getOrPut(key) { MonthKm(key, 0.0, LinkedHashMap()) }
        for ((mode, km) in d.kmByMode) { m.byMode[mode] = (m.byMode[mode] ?: 0.0) + km; m.total += km }
        val t = dayType(d); dayTypes[t] = dayTypes.getValue(t) + 1
    }

    val rhythm = List(7) { DoubleArray(24) }
    var rhythmMax = 0.0
    for (v in data.visits) {
        if (v.lvl != 0 || isHome(data.places[v.placeId])) continue
        // walk the visit hour by hour in local time, crediting each cell with the fraction covered
        var t = v.start
        while (t < v.end) {
            val local = Instant.ofEpochSecond(t + v.tz * 60L).atOffset(ZoneOffset.UTC)
            val hourEnd = t + 3600 - ((t + v.tz * 60L) % 3600)
            val frac = (min(v.end, hourEnd) - t) / 3600.0
            val cell = rhythm[local.dayOfWeek.value - 1]
            cell[local.hour] += frac
            rhythmMax = max(rhythmMax, cell[local.hour])
            t = hourEnd
        }
    }

    val cities = LinkedHashMap<String, CityAcc>()
    for (v in data.visits) {
        if (v.lvl != 0) continue
        val p = data.places[v.placeId]
        val city = p?.city ?: continue
        val c = cities.getOrPut(city) { CityAcc(city, p.countryCode, 0, 0.0, v.start, v.end, HashSet()) }
        c.visits++; c.hours += (v.end - v.start) / 3600.0; c.ids.add(v.placeId)
        c.first = min(c.first, v.start); c.last = max(c.last, v.end)
    }

    return Stats(
        months = byMonth.values.sortedBy { it.key },
        rhythm = rhythm.map { it.toList() }, rhythmMax = rhythmMax, dayTypes = dayTypes,
        cities = cities.values.map { CityRow(it.city, it.country, it.visits, it.ids.size, it.hours, it.first, it.last) }.sortedByDescending { it.hours },
    )
}
