package com.elsewhere.app.ui.tabs.numbers

import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.DayType
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
}
