package com.elsewhere.app.analytics

import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Visit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RangeTest {
    private fun act(start: Long, end: Long, mode: String, m: Double) = Activity(0, start, end, 0, 0.0, 0.0, 0.0, 0.0, m, mode, 1.0)
    private fun vis(start: Long, placeId: String) = Visit(0, start, start + 10, 0, placeId, 0.0, 0.0, "UNKNOWN", 1.0, 0)

    @Test fun `inRange keeps overlapping rows only`() {
        val rows = listOf(act(0, 10, "WALKING", 1.0), act(20, 30, "WALKING", 1.0), act(5, 25, "WALKING", 1.0))
        assertEquals(1, inRange(rows, 12L..18L).size)
        assertEquals(3, inRange(rows, 0L..30L).size)
        assertTrue(inRange(rows, 100L..200L).isEmpty())
    }

    @Test fun `kmByMode sums metres into km per mode`() {
        assertEquals(
            mapOf("WALKING" to 2.0, "FLYING" to 10.0),
            kmByMode(listOf(act(0, 1, "WALKING", 1500.0), act(0, 1, "WALKING", 500.0), act(0, 1, "FLYING", 10000.0))),
        )
        assertEquals(emptyMap<String, Double>(), kmByMode(emptyList()))
    }

    @Test fun `distinctPlaces counts unique place ids`() {
        assertEquals(2, distinctPlaces(listOf(vis(0, "a"), vis(1, "a"), vis(2, "b"))))
    }
}
