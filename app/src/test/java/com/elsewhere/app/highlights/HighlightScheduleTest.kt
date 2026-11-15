package com.elsewhere.app.highlights

import com.elsewhere.app.OnThisDay
import com.elsewhere.app.analytics.Scope
import com.elsewhere.app.analytics.highlightFor
import com.elsewhere.app.analytics.highlightState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HighlightScheduleTest {
    private val zone = ZoneId.of("America/New_York")
    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private val data = OnThisDay.data()

    @Test fun `runs later today when the hour is still ahead`() {
        assertEquals(2 * 3600_000L, nextRunDelayMs(ms(2025, 6, 10, 6), DEFAULT_MINUTE, zone))
    }
    @Test fun `runs tomorrow when the hour has passed, and exactly at the hour counts as passed`() {
        assertEquals(23 * 3600_000L, nextRunDelayMs(ms(2025, 6, 10, 9), DEFAULT_MINUTE, zone))
        assertEquals(24 * 3600_000L, nextRunDelayMs(ms(2025, 6, 10, 8), DEFAULT_MINUTE, zone))
    }
    @Test fun `crosses a daylight-saving change by the clock, not by 24 hours`() {
        // 9 Mar 2025, 02:00 in New York skips to 03:00: 23 real hours separate the two 08:00s.
        assertEquals(23 * 3600_000L, nextRunDelayMs(ms(2025, 3, 8, 8, 30) - 30 * 60_000L, DEFAULT_MINUTE, zone) - 0L)
    }
    @Test fun `honours a minute within the hour`() {
        assertEquals(45 * 60_000L, nextRunDelayMs(ms(2025, 6, 10, 20, 30), 21 * 60 + 15, zone))
    }
    @Test fun `only a found highlight notifies`() {
        assertTrue(shouldNotify(highlightState(data, "2025-11-14")))
        assertFalse(shouldNotify(highlightState(data, "2025-07-20")))
        assertFalse(shouldNotify(highlightState(data.copy(meta = data.meta.copy(range = OnThisDay.at(2023, 3, 3)..OnThisDay.at(2023, 5, 1))), "2023-04-10")))
        assertFalse(shouldNotify(highlightState(data.copy(visits = emptyList()), "2025-11-14")))
    }
    @Test fun `title is the card's headline, this week only for a widened pick, and the body names the facts`() {
        val h = highlightFor(data, "2025-11-14")!!
        assertEquals("On this day, 2 years ago — you woke up in Kyoto.", notificationTitle(h))
        assertEquals("3 nights into a 7-night trip · 10,800 km from home · Weather of your week", notificationBody(h))
        val widened = highlightFor(data, "2025-11-20")!!
        assertEquals(Scope.WEEK, widened.scope)
        assertTrue(notificationTitle(widened).startsWith("This week, 2 years ago — you "))
    }
}
