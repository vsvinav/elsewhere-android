package com.elsewhere.app.ui.tabs.dates

import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.data.Activity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatesViewModelTest {
    private val base = SampleRecord.record
    private val ny = base.trips.maxBy { it.start }

    private fun leg(id: Int, offsetH: Long, m: Double, p: Double, recorded: Boolean, mode: String = "IN_PASSENGER_VEHICLE"): Activity =
        Fx.act(ny.start + offsetH * Fx.H, ny.start + offsetH * Fx.H + Fx.H / 2, mode, m).copy(id = id, p = p, recorded = recorded)

    private fun ui(vararg legs: Activity) = datesUi(base.copy(activities = base.activities + legs), base, SampleRecord.profile)

    @Test fun `a trip asks about its recorded legs that were only guessed, longest first, three at most`() {
        val u = ui(
            leg(1, 5, 4_000.0, 0.6, recorded = true),
            leg(2, 7, 12_000.0, 0.6, recorded = true),
            leg(3, 9, 8_000.0, 0.7, recorded = true, mode = "IN_TRAIN"),
            leg(4, 11, 20_000.0, 0.6, recorded = true),
        )
        val trip = u.trips.first { it.trip.start == ny.start }
        assertEquals(listOf(4, 2, 3), trip.prompts.map { it.id })
    }

    @Test fun `a leg the phone was sure of, or that an export described, is not asked about`() {
        val u = ui(
            leg(1, 5, 4_000.0, 0.6, recorded = true),
            leg(2, 7, 12_000.0, 0.9, recorded = true),
            leg(3, 9, 30_000.0, 0.6, recorded = false),
            leg(4, 11, 20_000.0, 0.75, recorded = true),
        )
        val trip = u.trips.first { it.trip.start == ny.start }
        assertEquals(listOf(1), trip.prompts.map { it.id })
    }

    @Test fun `a guessed leg outside the trip's dates belongs to no trip card`() {
        val u = ui(leg(1, 5, 4_000.0, 0.6, recorded = true).let { it.copy(start = ny.end + Fx.D, end = ny.end + Fx.D + Fx.H) })
        assertTrue(u.trips.all { it.prompts.isEmpty() })
    }
}
