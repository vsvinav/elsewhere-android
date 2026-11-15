package com.elsewhere.app.analytics

import com.elsewhere.app.data.Record
import com.elsewhere.app.ui.nav.Door
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min

// Port of web/src/analytics/passport.ts

data class Stamp(val id: String, val label: String, val earnedAt: Long?, val door: Door?, val sub: String? = null) {
    val earned: Boolean get() = earnedAt != null
}
enum class PageKind { COUNTRY, MILESTONE }
data class Page(val kind: PageKind, val id: String, val title: String, val stamps: List<Stamp>) {
    val earned: Int get() = stamps.count { it.earned }
}
data class Passport(val pages: List<Page>) {
    val earnedIds: List<String> get() = pages.flatMap { p -> p.stamps.filter { it.earned }.map { it.id } }
    val total: Int get() = pages.sumOf { it.stamps.size }
}

private const val DAY = 86400L
private const val EARTH_KM = 40_075.0
private val NIGHTS = listOf(10, 100, 1_000)
private val CITIES = listOf(5, 25, 100)
private val COUNTRIES = listOf(3, 10, 25)
private const val ZONES = 10
/** Stops with no country go on a page of their own. */
const val ELSEWHERE = "ELSEWHERE"

private fun n0(x: Int) = String.format(Locale.US, "%,d", x)
private fun nights(n: Int) = if (n == 1) "1 night" else "$n nights"

/** A milestone opens the map on the day it was earned, with a day either side. */
private fun around(earnedAt: Long?, label: String): Door? = earnedAt?.let { Door.Range(it - DAY, it + DAY, label) }
private fun milestone(id: String, label: String, earnedAt: Long?) = Stamp("ms:$id", label, earnedAt, around(earnedAt, label))

/**
 * Pages per country and per milestone, every stamp dated when it was earned or left as an outline to aim for.
 * One sorted pass over the stops, one over the activities, one over the visits: O(S log S + A + V).
 */
/** A time zone by meridian, 15° wide and centred on it, so daylight saving never counts a place twice. */
fun zoneOf(lng: Double): Int = floor((lng + 187.5) / 15).toInt()

fun passport(data: Record): Passport {
    val stops = tour(data).stops.sortedBy { it.first }
    // Insertion order is first-earned order, since the stops were sorted before grouping.
    val byCountry = LinkedHashMap<String, MutableList<Stop>>()
    for (s in stops) byCountry.getOrPut(s.country ?: ELSEWHERE) { ArrayList() }.add(s)
    val pages = ArrayList<Page>()
    for ((cc, list) in byCountry) {
        pages += Page(
            PageKind.COUNTRY, "country:$cc", cc.uppercase(Locale.US),
            list.map { s -> Stamp("city:${s.city}", s.city.uppercase(Locale.US), s.first, Door.Places(s.placeIds.toList(), s.city), nights(s.nights)) },
        )
    }

    // Cities and countries fall out of the stops; nights need the visits themselves, since a city's stop spans every trip there.
    val cityAt = arrayOfNulls<Long>(CITIES.size)
    val countryAt = arrayOfNulls<Long>(COUNTRIES.size)
    val seen = HashSet<String>()
    stops.forEachIndexed { i, s ->
        CITIES.forEachIndexed { k, n -> if (i + 1 == n) cityAt[k] = s.first }
        val c = s.country
        if (c != null && seen.add(c)) COUNTRIES.forEachIndexed { k, n -> if (seen.size == n) countryAt[k] = s.first }
    }

    val nightAt = arrayOfNulls<Long>(NIGHTS.size)
    var cum = 0
    for ((v, n) in sleptAway(data).away.sortedBy { it.v.start }) {
        NIGHTS.forEachIndexed { k, want -> if (nightAt[k] == null && cum < want && cum + n >= want) nightAt[k] = min(v.start + (want - cum - 1) * DAY, v.end) }
        cum += n
    }

    var km = 0.0
    var lapAt: Long? = null
    for (a in data.activities) { km += a.m / 1000; if (km >= EARTH_KM) { lapAt = a.end; break } }

    val zones = HashSet<Int>()
    var zoneAt: Long? = null
    for (v in data.visits) {
        if (v.lvl != 0 || !zones.add(zoneOf(v.lng))) continue
        if (zones.size == ZONES) { zoneAt = v.start; break }
    }

    pages += Page(
        PageKind.MILESTONE, "milestones", "MILESTONES",
        buildList {
            add(milestone("first-night", "FIRST NIGHT AWAY", stops.firstOrNull()?.first))
            NIGHTS.forEachIndexed { k, n -> add(milestone("nights-$n", "${n0(n)} NIGHTS AWAY", nightAt[k])) }
            CITIES.forEachIndexed { k, n -> add(milestone("cities-$n", "${n0(n)} CITIES", cityAt[k])) }
            COUNTRIES.forEachIndexed { k, n -> add(milestone("countries-$n", "${n0(n)} COUNTRIES", countryAt[k])) }
            add(milestone("lap", "1 LAP OF EARTH", lapAt))
            add(milestone("zones", "$ZONES TIME ZONES", zoneAt))
        },
    )
    return Passport(pages)
}

/** A stamp's lean on the page, ±6°, the same every time it prints. FNV-1a over the id, as on the web. */
fun tilt(id: String): Float {
    var h = 2166136261L.toInt()
    for (c in id) { h = h xor c.code; h *= 16777619 }
    val u = h.toLong() and 0xffffffffL
    return ((u % 1201) / 100.0 - 6).toFloat()
}
