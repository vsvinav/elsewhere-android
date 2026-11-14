package com.elsewhere.app.analytics

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.D
import com.elsewhere.app.Fx.H
import com.elsewhere.app.Fx.place
import com.elsewhere.app.Fx.visit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

/** Twin of web/src/profile/suggest.test.ts. */
class SuggestTest {
    private val T0 = Fx.utc(2024, 10) // 2024-11-01
    private fun pl(lat: Double, city: String) = place(null, city, "US", 1, 0, 0, null, lat, 0.0, 1.0)
    private fun v(id: Int, start: Long, end: Long, placeId: String) = visit(start, end, placeId, id = id)
    private val visits =
        (0 until 40).map { i -> v(i, T0 + i * D + 20 * H, T0 + (i + 1) * D + 7 * H, "H1") } +            // 40 nights Nov–Dec 2024
        (0 until 30).map { i -> v(100 + i, T0 + (62 + i) * D + 20 * H, T0 + (63 + i) * D + 7 * H, "H2") } + // 30 nights from Jan 2 2025
        (0 until 20).map { i -> v(200 + i, T0 + i * D + 10 * H, T0 + i * D + 16 * H, "W1") }.filter {
            val d = Instant.ofEpochSecond(it.start).atOffset(ZoneOffset.UTC).dayOfWeek; d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY
        }
    private val fx = Fx.record(visits = visits, places = mapOf("H1" to pl(1.0, "Boston"), "H2" to pl(2.0, "Sunnyvale"), "W1" to pl(3.0, "Boston")))

    @Test fun `sleepPlaces ranks by nights`() {
        val s = sleepPlaces(fx)
        assertEquals(listOf("H1", "H2"), s.map { it.placeId })
        assertEquals(40.0, s[0].score)
    }

    @Test fun `weekdayPlaces excludes sleep places`() {
        assertEquals(listOf("W1"), weekdayPlaces(fx).map { it.placeId })
    }

    @Test fun `moveMonths finds the month the modal sleep place changes`() {
        assertEquals(listOf(Move("2025-01", "H1", "H2")), moveMonths(fx))
    }

    @Test fun `sleepPlaces keeps folded members and a share of nights`() {
        val twin = fx.copy(
            visits = fx.visits + (0 until 5).map { i -> v(300 + i, T0 + (45 + i) * D + 20 * H, T0 + (46 + i) * D + 7 * H, "H1b") },
            places = fx.places + ("H1b" to pl(1.0001, "Boston")),
        )
        val s = sleepPlaces(twin)[0]
        assertEquals(listOf("H1", "H1b"), s.members.sorted())
        assertEquals(45.0, s.score)
        assertEquals(1.0, s.share)
        assertEquals(1.0, sleepPlaces(fx)[1].share)
    }
}
