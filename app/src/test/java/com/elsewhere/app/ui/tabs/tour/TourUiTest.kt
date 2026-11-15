package com.elsewhere.app.ui.tabs.tour

import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.data.Units
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
        assertTrue(ui.trail.lines.isNotEmpty() && ui.trail.flights.size == 4)
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
}
