package com.elsewhere.app.ui.tabs.numbers

import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.DayType
import com.elsewhere.app.ui.nav.Door
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NumbersUiTest {
    @Test fun `the sample year tallies`() {
        val ui = numbersUi(SampleRecord.record, SampleRecord.profile)
        assertEquals(listOf("IN_PASSENGER_VEHICLE", "WALKING", "CYCLING"), ui.modes, "top three modes by the daily tallies")
        assertEquals(12, ui.stats.months.size)
        assertEquals(ui.maxMonth, ui.best?.total)
        assertTrue(ui.maxMonth >= ui.avg)
        assertEquals(13, ui.stats.dayTypes[DayType.AWAY])
        assertTrue(ui.hasWork)
        assertEquals(100, ui.pct(DayType.AWAY) + ui.pct(DayType.WORK) + ui.pct(DayType.OUT) + ui.pct(DayType.HOME), "rounded shares still sum close to a hundred")
        assertEquals(4, ui.stats.cities.size)
        assertEquals(3, ui.countries)
        assertTrue(ui.hoursAway > 0)
        assertTrue(ui.peak != null && ui.topDay in 0..6)
    }

    @Test fun `an empty record is all blanks`() {
        val ui = numbersUi(Fx.record(), null)
        assertTrue(ui.modes.isEmpty())
        assertNull(ui.best)
        assertEquals(1.0, ui.maxMonth)
        assertEquals(0.0, ui.avg)
        assertNull(ui.peak)
        assertEquals(0, ui.newTotal)
        assertNull(ui.usualMode)
    }

    @Test fun `every figure on the passes has a door, built once with the figures`() {
        val ui = numbersUi(SampleRecord.record, SampleRecord.profile)
        val d = ui.doors
        val whole = SampleRecord.record.meta.range
        // The best month opens on that month, first midnight to the last second.
        val best = d.bestMonth as Door.Range
        assertEquals("${ui.best!!.key}-01", java.time.LocalDate.ofEpochDay(best.from / Fx.D).toString())
        assertEquals(best.from + java.time.YearMonth.parse(ui.best!!.key).lengthOfMonth() * Fx.D - 1, best.to)
        assertTrue(best.label.startsWith("Best month · "))
        assertEquals(d.bestMonth, (d.peakMonth as Door.Range).copy(label = best.label))
        // Window-wide figures open on the window.
        assertEquals(Door.Range(whole.first, whole.last, "12 months on record"), d.months)
        assertEquals(whole, (d.hoursAway as Door.Range).let { it.from..it.to })
        assertEquals(DayType.entries.toSet(), d.dayTypes.keys)
        assertEquals("${ui.pct(DayType.AWAY)}% away days", d.dayTypes.getValue(DayType.AWAY).label)
        assertEquals(whole, (d.legs as Door.Range).let { it.from..it.to })
        // City rows open on the city's places, in the stats' order.
        assertEquals(ui.stats.cities.map { it.city }, d.cities.map { it.label.substringBefore(" · ") })
        assertEquals(Door.Places(listOf("lisbon", "lisbon-cafe"), d.cities[ui.stats.cities.indexOfFirst { it.city == "Lisbon" }].label), d.cities[ui.stats.cities.indexOfFirst { it.city == "Lisbon" }])
    }
}
