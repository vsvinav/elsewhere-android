package com.elsewhere.app

import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Daily
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.ImportReport
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Meta
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordProfile
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Visit
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Twin of web/src/test/onThisDay.ts: four years of a made-up record for the "on this day" tests.
 * Boston home, a Sydney week, a Paris day trip on 15 Nov 2022, a Kyoto + Osaka week in Nov 2023 (hotel 11–17, temple on the 15th, dinner out on the 16th),
 * an ordinary work day on 15 Nov 2024 and a return to Kyoto in May 2025. Each `data()` call builds a fresh copy.
 */
object OnThisDay {
    /** UTC seconds; like `Date.UTC` in the web fixture, a day of 0 or a minute of 60 rolls over. */
    fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long = LocalDateTime.of(y, m, 1, 0, 0).plusDays(d - 1L).plusHours(h.toLong()).plusMinutes(min.toLong()).toEpochSecond(ZoneOffset.UTC)
    val BOS = LatLng(42.36, -71.06); val KYO = LatLng(35.01, 135.77); val OSA = LatLng(34.69, 135.5)
    val PAR = LatLng(48.86, 2.35); val SYD = LatLng(-33.87, 151.21); val NYC = LatLng(40.71, -74.01)

    private var id = 0
    fun v(start: Long, end: Long, placeId: String, p: LatLng, type: String = "UNKNOWN") = Visit(id++, start, end, 0, placeId, p.lat, p.lng, type, 1.0, 0)
    fun a(start: Long, end: Long, mode: String, m: Double, s: LatLng, e: LatLng) = Activity(id++, start, end, 0, s.lat, s.lng, e.lat, e.lng, m, mode, 1.0)
    fun place(p: LatLng, city: String?, cc: String?, name: String? = null, role: Role? = null, primaryType: String? = null) =
        Place(p.lat, p.lng, null, role, city, cc, 1, 1.0, 0, 0, name, null, emptyList(), primaryType, null)
    fun day(
        date: String, maxDistFromHomeKm: Double = 0.0, cities: List<String> = emptyList(), nNewPlaces: Int = 0,
        hoursHome: Double = 0.0, hoursWork: Double = 0.0, hoursOther: Double = 0.0, hoursTransit: Double = 0.0,
    ) = Daily(date, emptyMap(), 1, 1, nNewPlaces, maxDistFromHomeKm, hoursHome, hoursWork, hoursOther, hoursTransit, cities)
    fun trip(start: Long, end: Long, nights: Int, cities: List<String>, km: Double) = Trip(start, end, nights, km, mapOf("FLYING" to km * 2), cities, null)

    fun homeNight(y: Int, m: Int, d: Int) = v(at(y, m, d - 1, 20), at(y, m, d, 8), "home", BOS, "INFERRED_HOME")

    fun data(): Record {
        id = 0
        val visits = listOf(
            // Sydney, the farthest the record goes.
            v(at(2022, 3, 10, 10), at(2022, 3, 13, 10), "sydney-hotel", SYD),
            // Boston → Paris day: woke at home, flew, Louvre, hotel.
            homeNight(2022, 11, 14), homeNight(2022, 11, 15),
            v(at(2022, 11, 15, 13), at(2022, 11, 15, 18), "louvre", PAR),
            v(at(2022, 11, 15, 20), at(2022, 11, 17, 9), "paris-hotel", PAR),
            // A night in New York on 28 Feb 2023 (29 Feb fallback).
            v(at(2023, 2, 27, 22), at(2023, 2, 28, 20), "nyc-hotel", NYC),
            // Kyoto week, Nov 2023.
            v(at(2023, 11, 11, 23), at(2023, 11, 17, 9), "kyoto-hotel", KYO),
            v(at(2023, 11, 15, 10), at(2023, 11, 15, 13), "kinkakuji", KYO),
            v(at(2023, 11, 16, 19), at(2023, 11, 16, 21), "kyoto-restaurant", KYO),
            v(at(2023, 11, 17, 12), at(2023, 11, 18, 9), "osaka-hotel", OSA),
            // An ordinary work day in 2024.
            homeNight(2024, 11, 15),
            v(at(2024, 11, 15, 9), at(2024, 11, 15, 17), "work", BOS, "INFERRED_WORK"),
            v(at(2024, 11, 15, 18), at(2024, 11, 16, 8), "home", BOS, "INFERRED_HOME"),
            // Back to Kyoto in 2025.
            v(at(2025, 5, 2, 15), at(2025, 5, 4, 9), "kyoto-hotel", KYO),
        ).sortedBy { it.start }
        val activities = listOf(
            a(at(2022, 3, 9, 10), at(2022, 3, 10, 8), "FLYING", 16_200_000.0, BOS, SYD),
            a(at(2022, 11, 15, 6), at(2022, 11, 15, 12), "FLYING", 5_500_000.0, BOS, PAR),
            a(at(2023, 11, 11, 8), at(2023, 11, 11, 22), "FLYING", 10_800_000.0, BOS, KYO),
            a(at(2023, 11, 17, 10), at(2023, 11, 17, 11), "IN_TRAIN", 45_000.0, KYO, OSA),
            a(at(2023, 11, 18, 12), at(2023, 11, 19, 2), "FLYING", 10_800_000.0, OSA, BOS),
        )
        val kyotoDays = (11..17).map { d -> day("2023-11-$d", maxDistFromHomeKm = 10_800.0, cities = listOf("Kyoto"), nNewPlaces = if (d == 11 || d == 15 || d == 17) 1 else 0, hoursOther = 20.0) }
        return Record(
            visits, activities, emptyList(), emptyList(),
            mapOf(
                "home" to place(BOS, "Boston", "US", null, Role.HOME), "work" to place(BOS, "Boston", "US", null, Role.WORK),
                "sydney-hotel" to place(SYD, "Sydney", "AU", "Sydney Hotel"),
                "louvre" to place(PAR, "Paris", "FR", "Louvre", null, "museum"), "paris-hotel" to place(PAR, "Paris", "FR", "Paris Hotel", null, "lodging"),
                "nyc-hotel" to place(NYC, "New York", "US", "NYC Hotel"),
                "kyoto-hotel" to place(KYO, "Kyoto", "JP", "Kyoto Hotel", null, "lodging"), "kinkakuji" to place(KYO, "Kyoto", "JP", "Kinkaku-ji", null, "temple"),
                "kyoto-restaurant" to place(KYO, "Kyoto", "JP", "Izakaya", null, "restaurant"),
                "osaka-hotel" to place(OSA, "Osaka", "JP", "Osaka Hotel", null, "lodging"),
            ),
            listOf(
                day("2022-03-10", maxDistFromHomeKm = 16_200.0, cities = listOf("Sydney"), nNewPlaces = 1),
                day("2022-11-14", maxDistFromHomeKm = 5.0, cities = listOf("Boston"), hoursHome = 12.0),
                day("2022-11-15", maxDistFromHomeKm = 5500.0, cities = listOf("Boston", "Paris"), nNewPlaces = 2, hoursTransit = 6.0, hoursOther = 7.0),
                day("2023-02-28", maxDistFromHomeKm = 300.0, cities = listOf("New York"), nNewPlaces = 1, hoursOther = 20.0),
            ) + kyotoDays + listOf(
                day("2023-11-18", maxDistFromHomeKm = 10_700.0, cities = listOf("Osaka"), hoursOther = 9.0, hoursTransit = 14.0),
                day("2024-11-14", maxDistFromHomeKm = 5.0, cities = listOf("Boston"), hoursHome = 12.0),
                day("2024-11-15", maxDistFromHomeKm = 5.0, cities = listOf("Boston"), hoursWork = 8.0, hoursHome = 14.0),
                day("2025-05-03", maxDistFromHomeKm = 10_800.0, cities = listOf("Kyoto"), hoursOther = 24.0),
            ),
            listOf(
                trip(at(2022, 3, 9, 10), at(2022, 3, 13, 10), 3, listOf("Sydney"), 16_200.0),
                trip(at(2022, 11, 15, 6), at(2022, 11, 17, 9), 2, listOf("Paris"), 5500.0),
                trip(at(2023, 11, 11, 8), at(2023, 11, 19, 2), 7, listOf("Kyoto", "Osaka"), 10_800.0),
                trip(at(2025, 5, 2, 15), at(2025, 5, 4, 9), 2, listOf("Kyoto"), 10_800.0),
            ),
            RecordProfile(BOS, listOf(Home(BOS.lat, BOS.lng, "Boston", "US", 0, null)), emptyList(), emptyList()),
            Meta("", "", 0, at(2022, 1, 1)..at(2025, 6, 1), ImportReport(0, 0, emptyMap())),
        )
    }

    /** The same record with no home on file. */
    fun Record.noHome(): Record = copy(profile = profile.copy(homes = emptyList()))
}
