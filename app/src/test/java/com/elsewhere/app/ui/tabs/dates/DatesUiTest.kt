package com.elsewhere.app.ui.tabs.dates

import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.bedStrip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatesUiTest {
    private val ui = datesUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile)

    @Test fun `trips run newest first with nights and flights added up`() {
        assertEquals(SampleRecord.record.trips.size, ui.trips.size)
        assertEquals(ui.trips.map { it.trip.start }.sortedDescending(), ui.trips.map { it.trip.start })
        assertEquals(SampleRecord.record.trips.sumOf { it.nights }, ui.nightsAway)
        assertEquals(SampleRecord.record.activities.count { it.mode == "FLYING" }, ui.flights)
        assertEquals(ui.flights, ui.trips.sumOf { it.flights }, "every flight in the sample happens on a trip")
        assertNotNull(ui.nights)
    }

    @Test fun `a trip knows where it slept and which places it kept coming back to`() {
        val tokyo = ui.trips.first { "Tokyo" in it.trip.anchorCities }
        val d = tripDetail(ui.data, ui.strip, tokyo.trip)
        assertEquals(bedStrip(SampleRecord.record, tokyo.trip.start..tokyo.trip.end), d.beds, "slicing the whole strip matches a per-trip strip")
        assertTrue(d.beds.isNotEmpty())
        assertTrue(d.visited.isNotEmpty())
        assertTrue(d.visited.none { it.place?.role != null }, "home never counts as a stop on a trip")
        assertEquals(d.visited.map { it.n }.sortedDescending(), d.visited.map { it.n })
        assertTrue(tokyo.modes.isNotEmpty() && tokyo.modes.size <= 4)
        assertEquals(tokyo.modes.map { it.second }.sortedDescending(), tokyo.modes.map { it.second })
    }
}
