package com.elsewhere.app

import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Daily
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Meta
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Pt
import com.elsewhere.app.data.ImportReport
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.Visit
import com.elsewhere.app.data.Work

/**
 * A small but complete year on record for screens and snapshots: home in Boston, a week in
 * Lisbon in May, four nights in Tokyo in September, a weekend in New York, commutes between.
 */
object SampleRecord {
    private val boston = LatLng(42.36, -71.06)
    private val lisbon = LatLng(38.72, -9.14)
    private val tokyo = LatLng(35.68, 139.69)
    private val nyc = LatLng(40.71, -74.0)
    val range = Fx.utc(2024, 0)..(Fx.utc(2024, 11, 31) + Fx.D - 1)

    val profile = Profile(
        "Ada", Units.KM,
        listOf(Home(boston.lat, boston.lng, "Boston", "US", range.first, null)),
        listOf(Work(42.35, -71.05, "work", "The Office", range.first, null)),
    )

    private fun night(day: Long, placeId: String, lat: Double, lng: Double, lvl: Int = 0) =
        Fx.visit(day + 20 * Fx.H, day + 32 * Fx.H, placeId, lat = lat, lng = lng, lvl = lvl)

    val record: Record by lazy {
        val visits = ArrayList<Visit>()
        val acts = ArrayList<Activity>()
        val daily = ArrayList<Daily>()
        var day = range.first
        var i = 0
        while (day < range.last) {
            val date = java.time.LocalDate.ofEpochDay(day / Fx.D).toString()
            val week = i / 7
            val away = when {
                week == 19 -> Triple("lisbon", lisbon, "Lisbon")
                week == 37 && i % 7 < 4 -> Triple("tokyo", tokyo, "Tokyo")
                week == 44 && i % 7 >= 5 -> Triple("nyc", nyc, "New York")
                else -> null
            }
            if (away != null) {
                visits.add(night(day, away.first, away.second.lat, away.second.lng))
                visits.add(Fx.visit(day + 10 * Fx.H, day + 14 * Fx.H, "${away.first}-cafe", lat = away.second.lat + 0.01, lng = away.second.lng, lvl = 0))
                daily.add(Fx.daily(date, mapOf("WALKING" to 9.0), nPlaces = 3, cities = listOf(away.third), hoursOther = 14.0, hoursTransit = 2.0, maxDistFromHomeKm = 5000.0))
            } else {
                visits.add(night(day, "home", boston.lat, boston.lng))
                if (i % 7 < 5) {
                    visits.add(Fx.visit(day + 9 * Fx.H, day + 17 * Fx.H, "work", lat = 42.35, lng = -71.05))
                    acts.add(Fx.act(day + 8 * Fx.H, day + 9 * Fx.H, "IN_PASSENGER_VEHICLE", 12_000.0, boston.lng, boston.lat, -71.05, 42.35))
                    daily.add(Fx.daily(date, mapOf("IN_PASSENGER_VEHICLE" to 24.0, "WALKING" to 2.0), nPlaces = 2, cities = listOf("Boston"), hoursHome = 13.0, hoursWork = 8.0, hoursOther = 1.0, hoursTransit = 2.0, maxDistFromHomeKm = 3.0))
                } else {
                    visits.add(Fx.visit(day + 11 * Fx.H, day + 13 * Fx.H, if (i % 14 < 7) "market" else "park", lat = 42.37, lng = -71.1))
                    daily.add(Fx.daily(date, mapOf("WALKING" to 6.0, "CYCLING" to 10.0), nPlaces = 2, cities = listOf("Boston"), hoursHome = 18.0, hoursOther = 4.0, hoursTransit = 2.0, maxDistFromHomeKm = 6.0))
                }
            }
            day += Fx.D; i++
        }
        fun fly(day: Long, a: LatLng, b: LatLng, km: Double) = Fx.act(day, day + 8 * Fx.H, "FLYING", km * 1000, a.lng, a.lat, b.lng, b.lat)
        val lis0 = range.first + 19 * 7 * Fx.D; val tok0 = range.first + 37 * 7 * Fx.D; val ny0 = range.first + (44 * 7 + 5) * Fx.D
        acts += listOf(fly(lis0, boston, lisbon, 5180.0), fly(lis0 + 7 * Fx.D, lisbon, boston, 5180.0), fly(tok0, boston, tokyo, 10_800.0), fly(tok0 + 4 * Fx.D, tokyo, boston, 10_800.0))
        acts.add(Fx.act(ny0, ny0 + 4 * Fx.H, "IN_TRAIN", 350_000.0, boston.lng, boston.lat, nyc.lng, nyc.lat))
        val trips = listOf(
            Trip(lis0, lis0 + 7 * Fx.D, 7, 5180.0, mapOf("FLYING" to 10_360.0, "WALKING" to 63.0), listOf("Lisbon"), null),
            Trip(tok0, tok0 + 4 * Fx.D, 4, 10_800.0, mapOf("FLYING" to 21_600.0, "WALKING" to 36.0), listOf("Tokyo"), null),
            Trip(ny0, ny0 + 2 * Fx.D, 2, 306.0, mapOf("IN_TRAIN" to 700.0, "WALKING" to 18.0), listOf("New York"), null),
        )
        val places = mapOf(
            "home" to Fx.place(Role.HOME, "Boston", "US", 300, name = "Home", lat = boston.lat, lng = boston.lng, totalHours = 4000.0),
            "work" to Fx.place(Role.WORK, "Boston", "US", 220, name = "The Office", lat = 42.35, lng = -71.05, totalHours = 1700.0, types = listOf("office")),
            "market" to Fx.place(null, "Boston", "US", 26, name = "Haymarket", lat = 42.37, lng = -71.1, totalHours = 52.0, primaryType = "market", types = listOf("market", "store")),
            "park" to Fx.place(null, "Boston", "US", 26, name = "Boston Common", lat = 42.355, lng = -71.065, totalHours = 52.0, primaryType = "park", types = listOf("park")),
            "lisbon" to Fx.place(null, "Lisbon", "PT", 7, name = "Casa do Bairro", lat = lisbon.lat, lng = lisbon.lng, totalHours = 84.0, primaryType = "lodging", types = listOf("lodging")),
            "lisbon-cafe" to Fx.place(null, "Lisbon", "PT", 7, name = "Pastelaria Alfama", lat = lisbon.lat + 0.01, lng = lisbon.lng, totalHours = 28.0, primaryType = "cafe", types = listOf("cafe")),
            "tokyo" to Fx.place(null, "Tokyo", "JP", 4, name = "Shinjuku Granbell", lat = tokyo.lat, lng = tokyo.lng, totalHours = 48.0, primaryType = "hotel", types = listOf("hotel", "lodging")),
            "tokyo-cafe" to Fx.place(null, "Tokyo", "JP", 4, name = "Omoide Yokocho", lat = tokyo.lat + 0.01, lng = tokyo.lng, totalHours = 16.0, primaryType = "restaurant", types = listOf("restaurant")),
            "nyc" to Fx.place(null, "New York", "US", 2, name = "The Jane", lat = nyc.lat, lng = nyc.lng, totalHours = 24.0, primaryType = "hotel", types = listOf("hotel")),
            "nyc-cafe" to Fx.place(null, "New York", "US", 2, name = "Katz's", lat = nyc.lat + 0.01, lng = nyc.lng, totalHours = 8.0, primaryType = "restaurant", types = listOf("restaurant")),
        )
        val strokes = listOf(
            Path(range.first, range.first + Fx.H, listOf(Pt(boston.lat, boston.lng, range.first), Pt(42.35, -71.05, range.first + Fx.H))),
            Path(ny0, ny0 + 4 * Fx.H, listOf(Pt(boston.lat, boston.lng, ny0), Pt(41.76, -72.68, ny0 + 2 * Fx.H), Pt(nyc.lat, nyc.lng, ny0 + 4 * Fx.H))),
        )
        Fx.record(visits, acts, places, daily, trips, strokes = strokes, home = boston, range = range)
            .copy(meta = Meta("Timeline.json", "", range.last, range, ImportReport(4200, 4100, mapOf("no position" to 100))))
    }
}
