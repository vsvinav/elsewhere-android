package com.elsewhere.app.sonar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SonarTest {
    private val lisbon = Fix(ts = 1_763_222_520_000L, lat = 38.7223, lng = -9.1393)
    private val now = lisbon.ts
    private fun day(offset: Int) = localDay(now - offset * 86_400_000L)
    private fun at(lat: Double, lng: Double, ts: Long = now) = Fix(ts, lat, lng)

    @Test fun `metres is haversine on 6371 km`() {
        val d = metres(at(38.7223, -9.1393), at(38.7223, -9.1393 + 0.01))
        assertTrue(abs(d - 868.5) < 2.0, "$d")
        assertEquals(0.0, metres(lisbon, lisbon))
    }

    @Test fun `within uses one mile`() {
        assertTrue(within(lisbon, at(38.7223, -9.1393 + 0.0185)))
        assertFalse(within(lisbon, at(38.7223, -9.1393 + 0.019)))
    }

    @Test fun `habitat needs four distinct days in the window`() {
        val hist = listOf(DayAt(day(1), 38.7225, -9.1390), DayAt(day(2), 38.7225, -9.1390), DayAt(day(3), 38.7225, -9.1390))
        assertFalse(isHabitat(hist, lisbon, now))
        assertTrue(isHabitat(hist + DayAt(day(10), 38.7225, -9.1390), lisbon, now))
        assertFalse(isHabitat(hist + DayAt(day(3), 38.7225, -9.1390), lisbon, now), "same day twice is one day")
        assertFalse(isHabitat(hist + DayAt(day(31), 38.7225, -9.1390), lisbon, now), "outside the window")
        assertFalse(isHabitat(hist + DayAt(day(5), 38.80, -9.1390), lisbon, now), "far away")
    }

    @Test fun `absence is counted from the stint start back to the last earlier visit`() {
        val hist = listOf(DayAt(day(212), 38.7225, -9.1390), DayAt(day(300), 38.7225, -9.1390), DayAt(day(0), 38.7225, -9.1390))
        assertEquals(212, absenceDays(hist, lisbon, stintStart = now))
        assertNull(absenceDays(listOf(DayAt(day(0), 38.7225, -9.1390)), lisbon, now), "today's own row does not count")
        assertNull(absenceDays(emptyList(), lisbon, now))
        assertNull(absenceDays(listOf(DayAt(day(5), 40.0, -9.1390)), lisbon, now))
    }

    @Test fun `stint start walks back through fixes that stay near`() {
        val fixes = listOf(
            at(38.7223, -9.1393, now - 60_000),
            at(38.7224, -9.1393, now - 120_000),
            at(38.9, -9.1393, now - 180_000),
            at(38.7224, -9.1393, now - 240_000),
        )
        assertEquals(now - 120_000, stintStart(fixes, lisbon))
        assertEquals(now, stintStart(emptyList(), lisbon))
        assertEquals(now, stintStart(listOf(at(38.7223, -9.1393, now + 5_000)), lisbon), "future fixes are ignored")
    }

    @Test fun `the newcomer is the later arriver and never-been counts as forever`() {
        val me = Side(at(38.7223, -9.1393, now - 1000), false, 3)
        val friend = Side(at(38.7223, -9.1393, now), false, null)
        assertEquals(Int.MAX_VALUE, newcomerAbsence(me, friend))
        assertEquals(Int.MAX_VALUE, newcomerAbsence(friend, me))
        assertEquals(3, newcomerAbsence(me.copy(fix = me.fix.copy(ts = now)), friend.copy(absenceDays = 9)), "a tie goes to me")
        assertEquals(30, newcomerAbsence(me.copy(fix = me.fix.copy(ts = now), absenceDays = 30), friend.copy(absenceDays = 9)), "a tie goes to me even when mine is longer")
        assertEquals(3, newcomerAbsence(me.copy(fix = me.fix.copy(ts = now + 1)), friend.copy(absenceDays = 9)))
    }

    @Test fun `eligibility table`() {
        val me = Side(lisbon, false, 212)
        val friend = Side(at(38.7230, -9.1400, now + 1), false, 20)
        assertTrue(eligible(me, friend, now + 1))
        assertFalse(eligible(me.copy(habitat = true), friend, now), "my habitat")
        assertFalse(eligible(me, friend.copy(habitat = true), now), "their habitat")
        assertFalse(eligible(me, friend.copy(absenceDays = 13), now), "newcomer back too soon")
        assertTrue(eligible(me.copy(absenceDays = 1), friend, now), "only the newcomer needs the gap")
        assertFalse(eligible(me, friend.copy(fix = at(38.80, -9.1400, now + 1)), now), "too far")
        val edge = at(38.7223 + 1700.0 / 111_195.0, -9.1393, now + 1)
        assertTrue(abs(metres(lisbon, edge) - 1700.0) < 5.0)
        assertFalse(eligible(me, friend.copy(fix = edge), now), "1700 m, just outside the mile")
        assertTrue(eligible(me, friend.copy(absenceDays = null), now), "never been here counts as forever")
        assertEquals(Decision.Fire("PAIR:$now"), decide(me, friend.copy(absenceDays = null), "PAIR", now, Debounce(), null))
        assertFalse(eligible(me, friend, now + FRESH_MS + 1), "stale")
        assertFalse(eligible(me.copy(fix = lisbon.copy(ts = now - FRESH_MS - 1)), friend, now), "my fix stale")
    }

    @Test fun `debounce is per pair for a day`() {
        val d = recordHit(Debounce(), "PAIR", now)
        assertTrue(debounced(d, "PAIR", now))
        assertTrue(debounced(d, "PAIR", now + DEBOUNCE_MS - 1))
        assertFalse(debounced(d, "PAIR", now + DEBOUNCE_MS))
        assertFalse(debounced(d, "OTHER", now))
        assertFalse(debounced(Debounce(), "PAIR", now))
    }

    @Test fun `decide fires, echoes, and stays quiet`() {
        val me = Side(lisbon, false, 212)
        val friend = Side(at(38.7230, -9.1400, now + 1), false, 20)
        assertEquals(Decision.Fire("PAIR:$now"), decide(me, friend, "PAIR", now, Debounce(), null))
        assertEquals(Decision.Echo("PAIR:1"), decide(me.copy(habitat = true), friend, "PAIR", now, Debounce(), "PAIR:1"))
        assertEquals(Decision.Quiet, decide(me, friend, "PAIR", now, recordHit(Debounce(), "PAIR", now - 1000), "PAIR:1"))
        assertEquals(Decision.Quiet, decide(me.copy(habitat = true), friend, "PAIR", now, Debounce(), null))
        assertEquals(Decision.Echo("ab12cd34"), decideTest("ab12cd34"), "a test ping echoes whatever the sides say")
        assertEquals(Decision.Quiet, decideTest(null), "a test ping whose hit was already seen is nothing")
    }

    @Test fun `habitat over a hundred thousand rows is quick`() {
        val rows = (0 until 100_000).map { i -> DayAt(day(i % 29), 38.0 + (i % 100) * 0.01, -9.0 - (i % 37) * 0.01) }
        val t0 = System.nanoTime()
        repeat(3) { isHabitat(rows, lisbon, now) }
        val ms = (System.nanoTime() - t0) / 3 / 1_000_000.0
        assertTrue(ms < 50.0, "took $ms ms")
    }
}
