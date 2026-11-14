package com.elsewhere.app

import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Daily
import com.elsewhere.app.data.ImportReport
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Meta
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordProfile
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Visit
import java.time.LocalDate
import java.time.ZoneOffset

/** Builders mirroring the loose `as unknown as Data` fixtures of the web tests. */
object Fx {
    const val H = 3600L
    const val D = 86400L

    fun utc(year: Int, month0: Int, day: Int = 1): Long = LocalDate.of(year, month0 + 1, day).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
    fun iso(s: String): Long = java.time.Instant.parse(s).epochSecond

    fun visit(
        start: Long, end: Long, placeId: String = "", type: String = "UNKNOWN", lvl: Int = 0,
        lng: Double = 0.0, lat: Double = 0.0, tz: Int = 0, id: Int = 0,
    ) = Visit(id, start, end, tz, placeId, lat, lng, type, 1.0, lvl)

    fun act(
        start: Long, end: Long, mode: String = "UNKNOWN", m: Double = 0.0,
        sLng: Double = 0.0, sLat: Double = 0.0, eLng: Double = 1.0, eLat: Double = 1.0, tz: Int = 0,
    ) = Activity(0, start, end, tz, sLat, sLng, eLat, eLng, m, mode, 1.0)

    fun place(
        role: Role? = null, city: String? = null, cc: String? = null, nVisits: Int = 1, firstTs: Long = 0, lastTs: Long = 0,
        name: String? = null, lat: Double = 0.0, lng: Double = 0.0, totalHours: Double = 1.0, label: String? = null,
        types: List<String> = emptyList(), primaryType: String? = null,
    ) = Place(lat, lng, label, role, city, cc, nVisits, totalHours, firstTs, lastTs, name, null, types, primaryType, null)

    fun daily(
        date: String, kmByMode: Map<String, Double> = emptyMap(), nPlaces: Int = 0, cities: List<String> = emptyList(),
        hoursWork: Double = 0.0, hoursOther: Double = 0.0, maxDistFromHomeKm: Double = 0.0, hoursHome: Double = 0.0, hoursTransit: Double = 0.0,
    ) = Daily(date, kmByMode, 0, nPlaces, 0, maxDistFromHomeKm, hoursHome, hoursWork, hoursOther, hoursTransit, cities)

    fun record(
        visits: List<Visit> = emptyList(), activities: List<Activity> = emptyList(), places: Map<String, Place> = emptyMap(),
        daily: List<Daily> = emptyList(), trips: List<Trip> = emptyList(), paths: List<Path> = emptyList(), strokes: List<Path> = emptyList(),
        home: LatLng = LatLng(0.0, 0.0), range: LongRange = 0L..0L,
    ) = Record(
        visits, activities, strokes, paths, places, daily, trips,
        RecordProfile(home, emptyList(), emptyList(), emptyList()),
        Meta("", "", 0, range, ImportReport(0, 0, emptyMap())),
    )
}
