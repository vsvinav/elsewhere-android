package com.elsewhere.app.ui.world.figures

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The clock figure reads like a wall clock: 12 up, 3 right, 6 down, 9 left. */
class DialGeometryTest {
    private fun counts(vararg at: Pair<Int, Int>) = List(24) { h -> at.firstOrNull { it.first == h }?.second ?: 0 }
    private fun deg(a: Float, b: Float) = assertEquals(a, b, 1e-4f)

    @Test fun `hours sit where a wall clock puts them`() {
        deg(-90f, dialAngle(12.0)); deg(-90f, dialAngle(0.0))
        deg(0f, dialAngle(3.0)); deg(90f, dialAngle(6.0)); deg(180f, dialAngle(9.0))
    }

    @Test fun `the afternoon folds onto the same face`() {
        deg(dialAngle(6.5), dialAngle(18.5))
        deg(0f, dialAngle(15.0))
    }

    @Test fun `twelve ticks with the big ones at the quarters, labelled 12 3 6 9`() {
        val g = dialGeometry(counts(), 7.0, null)
        assertEquals(12, g.ticks.size)
        assertEquals(listOf(-90f, 0f, 90f, 180f), g.ticks.filter { it.big }.map { it.angle })
        assertEquals(listOf("12", "3", "6", "9"), g.labels.map { it.text })
        assertEquals(listOf(-90f, 0f, 90f, 180f), g.labels.map { it.angle })
    }

    @Test fun `the hand points at the hour mod 12 and carries the meridiem`() {
        val pm = dialGeometry(counts(), 18.5, null).hand
        deg(dialAngle(6.5), pm.angle); assertEquals("PM", pm.meridiem)
        assertEquals("AM", dialGeometry(counts(), 6.5, null).hand.meridiem)
        assertEquals("PM", dialGeometry(counts(), 12.0, null).hand.meridiem)
        assertEquals("AM", dialGeometry(counts(), 24.5, null).hand.meridiem)
    }

    @Test fun `bars split into an AM inner ring and a PM outer ring scaled to the busiest hour`() {
        val g = dialGeometry(counts(7 to 5, 12 to 10, 19 to 2), 12.0, null)
        assertEquals(listOf(7, 12, 19), g.bars.map { it.hour })
        assertEquals(listOf(DialRing.AM, DialRing.PM, DialRing.PM), g.bars.map { it.ring })
        assertEquals(listOf(0.5f, 1f, 0.2f), g.bars.map { it.len })
        deg(dialAngle(7.5), g.bars[0].angle); deg(dialAngle(0.5), g.bars[1].angle)
        assertTrue(dialGeometry(counts(), 3.0, null).bars.isEmpty())
    }

    @Test fun `a night window past midnight is one short arc through 12`() {
        val a = dialGeometry(counts(), 1.0, 23 to 7).arc!!
        deg(dialAngle(11.0), a.from); deg(240f, a.sweep); assertEquals(false, a.full)
        val b = dialGeometry(counts(), 1.0, 22 to 28).arc!!
        deg(dialAngle(10.0), b.from); deg(180f, b.sweep); assertEquals(false, b.full)
    }

    @Test fun `a window of half a day or more fills the whole face`() {
        val a = dialGeometry(counts(), 1.0, 20 to 34).arc!!
        deg(360f, a.sweep); assertTrue(a.full)
        assertTrue(dialGeometry(counts(), 1.0, 0 to 12).arc!!.full)
        assertNull(dialGeometry(counts(), 1.0, null).arc)
    }
}
