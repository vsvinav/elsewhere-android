package com.elsewhere.app.ui.account

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SketchTest {
    @Test fun `the hold shows nothing drawn and no kilometres`() {
        val s = sketchAt(0)
        assertEquals(0, s.km); assertEquals(0, s.stamps); assertEquals(0f, s.fraction)
        assertEquals(Sketch(0, 0f, 0, 0), sketchAt(399))
    }

    @Test fun `half way down the first leg the counter is between home and Boston`() {
        val s = sketchAt(1000)
        assertEquals(1, s.leg)
        assertTrue(s.km in 1..419, "km ${s.km}")
        assertTrue(s.fraction > 0f && s.fraction < 0.2f, "fraction ${s.fraction}")
    }

    @Test fun `reaching Boston lands the first stamp on its exact distance`() {
        val s = sketchAt(1600)
        assertEquals(1, s.stamps); assertEquals(420, s.km)
    }

    @Test fun `a far pin's name fades in over the moment its stamp lands and HOME is named from the start`() {
        assertEquals(1f, labelAlpha(0, 0))
        assertEquals(0f, labelAlpha(1, 1599))
        assertEquals(0.5f, labelAlpha(1, 1660))
        assertEquals(1f, labelAlpha(1, 1720))
        assertEquals(1f, labelAlpha(5, SKETCH_TOTAL))
    }

    @Test fun `the end draws the whole route and counts every kilometre`() {
        val s = sketchAt(SKETCH_TOTAL)
        assertEquals(14920, s.km); assertEquals(5, s.stamps); assertEquals(1f, s.fraction)
        assertEquals(s, sketchAt(SKETCH_TOTAL + 5000))
    }
}
