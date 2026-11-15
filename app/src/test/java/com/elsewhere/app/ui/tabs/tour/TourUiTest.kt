package com.elsewhere.app.ui.tabs.tour

import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.doorId
import com.elsewhere.app.ui.world.cv
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.n0
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TourUiTest {
    @Test fun `the sample year reads as a tour`() {
        val ui = tourUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile, null)
        assertEquals("Ada", ui.name)
        assertEquals(Units.KM, ui.units)
        assertEquals("2024", ui.yearSpan)
        assertEquals("Tokyo", ui.tour.headline?.city)
        assertEquals("Shinjuku Granbell", ui.actName)
        assertEquals("JP", ui.actSub)
        assertTrue(ui.hasWork)
        assertEquals("Boston", ui.homeCity)
        assertNotNull(ui.nights)
        assertEquals(listOf("Lisbon", "Tokyo", "New York"), ui.tour.stops.map { it.city })
        assertEquals(10, ui.named)
        assertEquals(0, ui.photos)
        // Four legs, two of them returns, print as two arcs.
        assertTrue(ui.trail.lines.isNotEmpty() && ui.trail.flights.size == 2)
    }

    @Test fun `days recorded on this phone are counted from device place ids`() {
        val d0 = Fx.utc(2024, 0, 1)
        val rec = Fx.record(
            range = d0..Fx.utc(2024, 0, 10),
            visits = listOf(
                Fx.visit(d0 + 10 * Fx.H, d0 + 12 * Fx.H, "ChIJabc"),
                Fx.visit(d0 + Fx.D + 10 * Fx.H, d0 + Fx.D + 12 * Fx.H, "dev:drt2zp2"),
                Fx.visit(d0 + Fx.D + 14 * Fx.H, d0 + Fx.D + 15 * Fx.H, "dev:drt2zp3"),
                Fx.visit(d0 + 3 * Fx.D + 10 * Fx.H, d0 + 3 * Fx.D + 12 * Fx.H, "dev:drt2zp2"),
            ),
        )
        assertEquals(2, tourUi(rec, rec, null, null).deviceDays)
        assertEquals(0, tourUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile, null).deviceDays)
    }

    @Test fun `a span across years prints both ends and no profile is a blank bill`() {
        val two = Fx.utc(2023, 11, 30)..Fx.utc(2024, 0, 3)
        val rec = Fx.record(range = two, daily = listOf(Fx.daily("2023-12-31"), Fx.daily("2024-01-01")))
        val ui = tourUi(rec, rec, null, null)
        assertEquals("2023–2024", ui.yearSpan)
        assertEquals("", ui.name)
        assertNull(ui.nights)
        assertNull(ui.homeCity)
    }

    @Test fun `every figure on the poster has a door, built once with the poster`() {
        val ui = tourUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile, null)
        val d = ui.doors
        val whole = SampleRecord.record.meta.range
        val act = ui.tour.headline!!
        // Record-wide figures open on the whole window, labelled as they read on the poster.
        assertEquals(Door.Range(whole.first, whole.last, "366 days of record"), d.days)
        assertEquals(Door.Range(whole.first, whole.last, "${date(whole.first)} → ${date(whole.last)}"), d.record)
        assertEquals("${n0(ui.facts.totalKm)} km", d.distance.label)
        assertEquals("${n0(ui.facts.totalKm)} km on record", d.distanceOnRecord.label)
        assertEquals("${ui.facts.trips} trips", d.trips.label)
        assertEquals("${ui.facts.flights} ${if (ui.facts.flights == 1) "flight" else "flights"}", d.flights.label)
        // Counts of places, cities and countries open as pins: every place, one per city, one per country.
        assertEquals(Door.Places(SampleRecord.record.places.keys.toList(), "${n0(ui.facts.places)} places"), d.places)
        assertEquals(ui.facts.cities, (d.cities as Door.Places).placeIds.size)
        assertEquals(ui.facts.countries, (d.countries as Door.Places).placeIds.size)
        assertEquals("${ui.facts.countries} ${if (ui.facts.countries == 1) "country" else "countries"}", d.countries.label)
        // A stop opens on the nights spent there, in the tour's order.
        assertEquals(listOf("Lisbon", "Tokyo", "New York"), ui.tour.stops.map { it.city })
        assertEquals(Door.Range(ui.tour.stops[0].first, ui.tour.stops[0].last, "Lisbon · 7 nights"), d.stops[0])
        // Nights away open on every bed slept in away; nights home on the homes.
        assertEquals(Door.Places(ui.tour.stops.flatMap { it.placeIds }, "${n0(ui.tour.nightsAway)} nights away"), d.nightsAway)
        assertEquals(Door.Places(listOf("home"), "${n0(ui.tour.nightsHome)} nights home"), d.nightsHome)
        // The headline act opens on its hotel at the night it began; its stay is the window.
        assertEquals(Door.Place("tokyo", act.start, "Tokyo · ${n0(act.km)} km from home"), d.act)
        assertEquals(Door.Range(act.start, act.end, "Tokyo · 1 night"), d.actNights)
        // The longest trip is a trip door, keyed the way the record will find it again.
        assertEquals(Door.Trip(ui.facts.longestTrip!!.trip.doorId, ui.facts.longestTrip!!.trip.start, ui.facts.longestTrip!!.trip.end, "Longest trip · 7 nights"), d.longestTrip)
        // Third places open on themselves; records on their day; year cells on the year, cut to the record.
        assertEquals(ui.facts.thirdPlaces.map { it.id }, d.thirdPlaces.map { (it as Door.Place).placeId })
        assertEquals(ui.facts.thirdPlaces[0].let { "${it.name} · ${it.place.nVisits} visits" }, d.thirdPlaces[0].label)
        val longest = d.longestDay as Door.Range
        assertEquals(Fx.D - 1, longest.to - longest.from)
        assertEquals("Longest day · ${n0(ui.facts.records.longestDay!!.km)} km", longest.label)
        assertEquals("Busiest day · ${ui.facts.records.busiestDay!!.places} places", d.busiestDay!!.label)
        val y = ui.facts.years[0]
        val year = Door.Range(maxOf(whole.first, Fx.utc(2024, 0, 1)), minOf(whole.last, Fx.utc(2025, 0, 1) - 1), "")
        assertEquals(listOf(listOf(
            year.copy(label = "2024 · ${n0(y.km)} km · ${n0(y.places)} places · ${y.trips} trips · ${y.flights} flights"),
            year.copy(label = "2024 · ${n0(y.km)} km"), year.copy(label = "2024 · ${n0(y.places)} places"), year.copy(label = "2024 · ${y.trips} trips"), year.copy(label = "2024 · ${y.flights} flights"),
        )), d.years)
        assertEquals(ui.facts.kmByMode.map { it.first }.toSet(), d.modes.keys)
        assertEquals("${n0(ui.facts.hours.other)} hours out", d.hoursOut.label)
    }

    @Test fun `doors read in the poster's units`() {
        val mi = tourUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile?.copy(units = Units.MI), null).doors
        assertEquals("${n0(Units.MI.cv(tourUi(SampleRecord.record, SampleRecord.record, null, null).facts.totalKm))} mi", mi.distance.label)
    }

    @Test fun `a home the record never names sends nights home to the whole window`() {
        val rec = SampleRecord.record
        val noHome = rec.copy(places = rec.places.mapValues { (_, p) -> if (isHome(p)) p.copy(role = null) else p })
        val d = tourUi(noHome, noHome, null, null).doors
        assertTrue(d.nightsHome is Door.Range)
    }
}
