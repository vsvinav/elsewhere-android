package com.elsewhere.app.analytics

import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Trip
import com.elsewhere.app.ingest.jsRound

// Port of web/src/analytics/home.ts

data class Named(val id: String, val place: Place, val name: String)
data class YearRow(val year: Int, val km: Double, val places: Int, val trips: Int, val flights: Int)
data class Hours(val home: Double, val work: Double, val other: Double, val transit: Double)
data class DayKm(val date: String, val km: Double)
data class DayPlaces(val date: String, val places: Int)
data class Farthest(val km: Double, val city: String?)
data class Records(val longestDay: DayKm?, val busiestDay: DayPlaces?, val farthest: Farthest?)
data class LongestTrip(val trip: Trip, val city: String?)
data class HomeFacts(
    val days: Int,
    val range: LongRange,
    val totalKm: Double,
    val kmByMode: List<Pair<String, Double>>,
    val places: Int,
    val cities: Int,
    val countries: Int,
    val topCities: List<String>,
    val thirdPlaces: List<Named>,
    val hours: Hours,
    val flights: Int,
    val trips: Int,
    val longestTrip: LongestTrip?,
    val records: Records,
    val years: List<YearRow>,
)

fun placeName(p: Place?, id: String): String = p?.name ?: p?.label ?: p?.city ?: id.take(8)

private class YearAcc(val year: Int) { var km = 0.0; var trips = 0; var flights = 0; val placeIds = HashSet<String>() }

fun homeFacts(data: Record): HomeFacts {
    val visits = data.visits.filter { it.lvl == 0 }
    val kmByMode = kmByMode(data.activities).entries.sortedByDescending { it.value }.map { it.key to it.value }
    val totalKm = kmByMode.sumOf { it.second }

    // Places, cities and countries count the whole place book, the same rule the Stops tab prints.
    val cityVisits = LinkedHashMap<String, Int>()
    val countries = HashSet<String>()
    for (p in data.places.values) {
        p.city?.let { cityVisits[it] = (cityVisits[it] ?: 0) + p.nVisits }
        p.countryCode?.let { countries.add(it) }
    }
    val topCities = cityVisits.entries.sortedByDescending { it.value }.take(5).map { it.key }

    val thirdPlaces = data.places.entries
        .filter { (_, p) -> p.role == null && p.label == null }
        .sortedByDescending { it.value.nVisits }
        .take(3)
        .map { (id, place) -> Named(id, place, placeName(place, id)) }

    var hHome = 0.0; var hWork = 0.0; var hOther = 0.0; var hTransit = 0.0
    var longestDay: DayKm? = null
    var busiestDay: DayPlaces? = null
    for (d in data.daily) {
        hHome += d.hoursHome; hWork += d.hoursWork; hOther += d.hoursOther; hTransit += d.hoursTransit
        val dayKm = d.kmByMode.values.sum()
        if (longestDay == null || dayKm > longestDay.km) longestDay = DayKm(d.date, dayKm)
        if (busiestDay == null || d.nPlaces > busiestDay.places) busiestDay = DayPlaces(d.date, d.nPlaces)
    }

    val home = data.profile.home
    var farthest: Farthest? = null
    for (v in visits) {
        val d = distanceKm(home, v)
        if (farthest == null || d > farthest.km) farthest = Farthest(d, data.places[v.placeId]?.city)
    }

    val flights = data.activities.filter { it.mode == "FLYING" }
    val lt = data.trips.sortedByDescending { it.nights }.firstOrNull()
    val longestTrip = lt?.let { LongestTrip(it, it.anchorCities.firstOrNull()) }

    val years = LinkedHashMap<Int, YearAcc>()
    fun row(y: Int) = years.getOrPut(y) { YearAcc(y) }
    for (a in data.activities) row(yearOf(a.start)).km += a.m / 1000
    for (v in visits) row(yearOf(v.start)).placeIds.add(v.placeId)
    for (t in data.trips) row(yearOf(t.start)).trips++
    for (f in flights) row(yearOf(f.start)).flights++

    val r0 = data.meta.range.first; val r1 = data.meta.range.last
    return HomeFacts(
        days = jsRound((r1 - r0) / 86400.0).toInt(),
        range = data.meta.range,
        totalKm = totalKm, kmByMode = kmByMode,
        places = data.places.size,
        cities = cityVisits.size,
        countries = countries.size,
        topCities = topCities, thirdPlaces = thirdPlaces, hours = Hours(hHome, hWork, hOther, hTransit),
        flights = flights.size,
        trips = data.trips.size,
        longestTrip = longestTrip,
        records = Records(longestDay, busiestDay, farthest),
        years = years.values.sortedBy { it.year }.map { YearRow(it.year, it.km, it.placeIds.size, it.trips, it.flights) },
    )
}
