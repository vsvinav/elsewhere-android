package com.elsewhere.app.ui.tabs.tour

import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.doorId
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
        // Record-wide figures open on the whole window.
        assertEquals(Door.Range(whole.first, whole.last, "366 days of record"), d.record)
        assertEquals(whole.first, (d.distance as Door.Range).from)
        assertEquals(whole.last, (d.nightsAway as Door.Range).to)
        // A stop opens on the places the record names in its city, in the tour's order.
        assertEquals(listOf("Lisbon", "Tokyo", "New York"), ui.tour.stops.map { it.city })
        assertEquals(Door.Places(listOf("lisbon", "lisbon-cafe"), "Lisbon · 7 nights"), d.stops[0])
        // The headline act opens on its hotel at the night it began; its stay is the window.
        assertEquals(Door.Place("tokyo", ui.tour.headline!!.start, "Tokyo"), d.headline)
        assertEquals(Door.Range(ui.tour.headline!!.start, ui.tour.headline!!.end, "1 night in Tokyo"), d.headlineStay)
        // The longest trip is a trip door, keyed the way the record will find it again.
        assertEquals(Door.Trip(ui.facts.longestTrip!!.trip.doorId, ui.facts.longestTrip!!.trip.start, ui.facts.longestTrip!!.trip.end, "Longest trip · 7 nights"), d.longestTrip)
        // Third places open on themselves; records on their day; years on the year.
        assertEquals(ui.facts.thirdPlaces.map { it.id }, d.thirdPlaces.map { (it as Door.Place).placeId })
        val longest = d.longestDay as Door.Range
        assertEquals(Fx.D - 1, longest.to - longest.from)
        assertEquals(Door.Place("tokyo", label = "Farthest · ${com.elsewhere.app.ui.world.n0(ui.facts.records.farthest!!.km)} km"), d.farthest)
        assertEquals(listOf(Door.Range(Fx.utc(2024, 0, 1), Fx.utc(2025, 0, 1) - 1, "2024")), d.years)
        assertEquals(ui.facts.kmByMode.map { it.first }.toSet(), d.modes.keys)
    }

    @Test fun `a stop in a city the record names no place for opens on its nights instead`() {
        val rec = SampleRecord.record
        val stripped = rec.copy(places = rec.places.filterKeys { !it.startsWith("nyc") })
        val d = tourUi(stripped, stripped, null, null).doors
        val ny = tourUi(stripped, stripped, null, null).tour.stops.first { it.city == "Unknown city" }
        assertEquals(Door.Range(ny.first, ny.last, "Unknown city"), d.stops[2])
    }
}
