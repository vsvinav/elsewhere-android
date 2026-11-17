package com.elsewhere.app.ui.tabs.numbers

import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.DayType
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.hours
import com.elsewhere.app.ui.world.month
import com.elsewhere.app.ui.world.n0
import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NumbersUiTest {
    @Test fun `the sample year tallies`() {
        val ui = numbersUi(SampleRecord.record, SampleRecord.profile)
        assertEquals(listOf("Car", "Walk", "Cycle"), ui.modes, "top three modes by the daily tallies, already named")
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

    @Test fun `unnamed modes never take a colour, they are all the grey remainder`() {
        val days = listOf(
            Fx.daily("2024-01-02", mapOf("IN_VEHICLE" to 300.0, "UNKNOWN" to 200.0, "FLYING" to 50.0, "WALKING" to 1.0)),
            Fx.daily("2024-02-02", mapOf("IN_TAXI" to 400.0, "CYCLING" to 2.0)),
        )
        val ui = numbersUi(Fx.record(daily = days, range = Fx.utc(2024, 0, 1)..Fx.utc(2024, 2, 1)), null)
        assertEquals(listOf("Flight", "Cycle", "Walk"), ui.modes)
        assertEquals(0, ui.modes.count { it == "Other" })
        assertEquals(mapOf("Other" to 500.0, "Flight" to 50.0, "Walk" to 1.0), ui.monthByLabel(ui.stats.months[0]))
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
        // The best month opens on that month, first midnight to the last second, cut to the record.
        val best = d.bestMonth as Door.Range
        val m0 = java.time.LocalDate.parse("${ui.best!!.key}-01").toEpochDay() * Fx.D
        assertEquals(maxOf(whole.first, m0), best.from)
        assertEquals(minOf(whole.last, m0 + java.time.YearMonth.parse(ui.best!!.key).lengthOfMonth() * Fx.D - 1), best.to)
        assertEquals("Best month · ${month(ui.best!!.key)} · ${n0(ui.best!!.total)} km", best.label)
        assertEquals("Best month · ${month(ui.best!!.key)}", d.which!!.label)
        assertEquals(best.copy(label = "Peak month · ${month(ui.best!!.key)} · ${n0(ui.maxMonth)} km"), d.peakMonth)
        // Chart columns open on their month, labelled as the tip reads.
        assertEquals(ui.stats.months.size, d.monthColumns.size)
        assertEquals("${month(ui.stats.months[0].key)} · ${n0(ui.stats.months[0].total)} km", d.monthColumns[0].label)
        assertEquals(ui.explore.size, d.exploreColumns.size)
        assertEquals(ui.explore[0].let { "${month(it.key)} · ${it.new} new · ${it.repeat} repeat · ${it.lapsed} lapsed" }, d.exploreColumns[0].label)
        // Window-wide figures open on the window.
        assertEquals(Door.Range(whole.first, whole.last, "12 months on record"), d.months)
        assertEquals("Average per month · ${n0(ui.avg)} km", d.average.label)
        assertEquals(whole, (d.hoursAway as Door.Range).let { it.from..it.to })
        assertEquals("${n0(ui.hoursAway)} hours away", d.hoursAway.label)
        assertEquals(DayType.entries.toSet(), d.dayTypes.keys)
        assertEquals("${ui.pct(DayType.AWAY)}% of days away", d.dayTypes.getValue(DayType.AWAY).label)
        assertEquals("${ui.pct(DayType.OUT)}% of days out", d.dayTypes.getValue(DayType.OUT).label)
        assertEquals("${n0(ui.newTotal)} new places", d.newPlaces.label)
        assertEquals("${n0(ui.newTotal / maxOf(1, ui.explore.size).toDouble())} new places a month", d.perMonth.label)
        assertEquals("${ui.stats.cities.size} cities", d.cityCount.label)
        assertEquals("${ui.countries} countries", d.countryCount.label)
        // Commute figures, and the home and work day shares, open on home and work.
        val homeWork = Door.Places(listOf("home", "work"), "")
        assertEquals(homeWork.copy(label = "${ui.pct(DayType.WORK)}% of days at work"), d.dayTypes.getValue(DayType.WORK))
        assertEquals(homeWork.copy(label = "${ui.pct(DayType.HOME)}% of days home"), d.dayTypes.getValue(DayType.HOME))
        assertEquals(homeWork.copy(label = "${n0(ui.commute.legs.size)} commute legs"), d.legs)
        assertEquals(homeWork.copy(label = "To work · ${ui.commute.toWork.medianMin.roundToInt()} min"), d.toWork)
        assertEquals(homeWork.copy(label = "${ui.commute.medianAll.roundToInt()} min door to door"), d.median)
        // City rows open on the city's places cell by cell, the dates on the city's span, in the stats' order.
        assertEquals(ui.stats.cities.map { it.city }, d.cities.map { it.city.label.substringBefore(" · ") })
        val li = ui.stats.cities.indexOfFirst { it.city == "Lisbon" }
        val lisbon = ui.stats.cities[li]
        val pins = Door.Places(listOf("lisbon", "lisbon-cafe"), "")
        assertEquals(pins.copy(label = "Lisbon · ${hours(lisbon.hours)} · ${n0(lisbon.visits)} visits · ${n0(lisbon.places)} places"), d.cities[li].city)
        assertEquals(pins.copy(label = "Lisbon · ${hours(lisbon.hours)}"), d.cities[li].hours)
        assertEquals(pins.copy(label = "Lisbon · ${n0(lisbon.visits)} visits"), d.cities[li].visits)
        assertEquals(pins.copy(label = "Lisbon · ${n0(lisbon.places)} places"), d.cities[li].places)
        assertEquals(Door.Range(lisbon.first, lisbon.last, "Lisbon · first visit ${date(lisbon.first)}"), d.cities[li].first)
        assertEquals(Door.Range(lisbon.first, lisbon.last, "Lisbon · last visit ${date(lisbon.last)}"), d.cities[li].last)
    }

    @Test fun `a city the record names no place in still opens as pins, framing them all`() {
        val d = numbersUi(SampleRecord.record, null).doors
        assertEquals(Door.Places(emptyList(), "Atlantis · 2h"), d.city("Atlantis", "Atlantis · 2h"))
    }

    @Test fun `commute figures fall back to the window when the record names no home or work`() {
        val rec = SampleRecord.record
        val anon = rec.copy(places = rec.places.mapValues { (_, p) -> p.copy(role = null) })
        val d = numbersUi(anon, null).doors
        assertTrue(d.legs is Door.Range)
        assertTrue(d.dayTypes.getValue(DayType.HOME) is Door.Range)
    }
}
