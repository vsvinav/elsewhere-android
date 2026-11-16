package com.elsewhere.app.sonar

import com.elsewhere.app.data.db.VisitEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryTest {
    private fun visit(start: Long, end: Long, tz: Int = 0) = VisitEntity(1, start, end, tz, "p", 38.7, -9.1, "stay", 1.0, 1, "Lisbon", "PT", null)

    @Test fun `a visit yields one row per local day it touches`() {
        val day = 86_400L
        assertEquals(listOf("1970-01-01"), dayRows(listOf(visit(0, 3600))).map { it.localDay })
        assertEquals(listOf("1970-01-01", "1970-01-02", "1970-01-03"), dayRows(listOf(visit(3600, 2 * day + 3600))).map { it.localDay })
        assertEquals(listOf("1970-01-01"), dayRows(listOf(visit(0, day))).map { it.localDay }, "ending at midnight stays on one day")
    }

    @Test fun `the zone offset moves the day`() {
        assertEquals(listOf("1970-01-02"), dayRows(listOf(visit(23 * 3600, 23 * 3600 + 600, tz = 120))).map { it.localDay })
        assertEquals(listOf("1969-12-31"), dayRows(listOf(visit(0, 600, tz = -300))).map { it.localDay })
    }

    @Test fun `my side is judged around the friend`() {
        val now = 1_763_222_520_000L
        val friendAt = Fix(now, 38.7223, -9.1393)
        val mine = Fix(now - 1000, 38.7230, -9.1400)
        val hist = (1..5).map { DayAt(localDay(now - it * 86_400_000L), 38.7225, -9.1390) }
        val s = mySide(mine, hist, listOf(mine), friendAt, now)
        assertEquals(true, s.habitat)
        assertEquals(1, s.absenceDays)
        assertEquals(mine, s.fix)
    }
}
