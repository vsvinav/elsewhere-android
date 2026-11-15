package com.elsewhere.app.map

import androidx.compose.ui.geometry.Offset
import com.elsewhere.app.Fx
import com.elsewhere.app.analytics.Cat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

// The heat palette and kernel; constants and stops mirror web/src/map/heat.ts.

class HeatTest {
    private fun rgba(c: Int) = intArrayOf((c shr 16) and 0xff, (c shr 8) and 0xff, c and 0xff, (c ushr 24) and 0xff)

    @Test fun `the cold end is a transparent blue`() {
        val c = rgba(heatColor(0f))
        assertEquals(0, c[3])
        assertEquals(listOf(43, 75, 209), c.take(3))
    }

    @Test fun `the middle stops land on their colours`() {
        val g = rgba(heatColor(0.4f))
        for (i in 0 until 4) assertTrue(kotlin.math.abs(g[i] - HEAT_STOPS[2][i]) <= 1, "green channel $i: ${g[i]}")
        assertEquals(listOf(214, 60, 32, 255), rgba(heatColor(1f)).toList())
    }

    @Test fun `t clamps to the hot end`() {
        assertEquals(heatColor(1f), heatColor(1.7f))
        assertEquals(heatColor(0f), heatColor(-3f))
    }

    @Test fun `between stops every channel is strictly between its neighbours`() {
        val mid = rgba(heatColor(0.5f))
        val green = HEAT_STOPS[2]; val yellow = HEAT_STOPS[3]
        for (i in 0 until 4) {
            val lo = minOf(green[i], yellow[i]); val hi = maxOf(green[i], yellow[i])
            assertTrue(mid[i] > lo && mid[i] < hi, "channel $i: ${mid[i]} not in ($lo, $hi)")
        }
    }

    @Test fun `heat weighs a stop by how often it was visited`() {
        val d = Dot("x", Fx.place(nVisits = 7), "X", Cat.EAT, Cat.EAT.color)
        assertEquals(7.0, heatWeight(d))
    }

    @Test fun `a single point peaks at its own cell and is normalised to one`() {
        val g = heatGrid(listOf(Offset(100f, 60f)), doubleArrayOf(3.0), 200, 120, 8, HEAT_RADIUS_PX)
        val cols = 200 / 8
        assertEquals(cols * (120 / 8), g.size)
        val peak = g.indices.maxBy { g[it] }
        assertEquals(60 / 8 * cols + 100 / 8, peak)
        assertEquals(1f, g[peak], 1e-6f)
        assertEquals(1f, g.max(), 1e-6f)
    }

    @Test fun `two equal points far apart peak equally, and a cell past the radius stays cold`() {
        val g = heatGrid(listOf(Offset(40f, 40f), Offset(360f, 40f)), doubleArrayOf(2.0, 2.0), 400, 80, 8, HEAT_RADIUS_PX)
        val cols = 400 / 8
        assertEquals(g[5 * cols + 5], g[5 * cols + 45], 1e-6f)
        assertEquals(1f, g[5 * cols + 5], 1e-6f)
        // x = 200px is 160px from either point, four radii out.
        assertEquals(0f, g[5 * cols + 25])
    }

    @Test fun `a hundred thousand points splat into a phone-sized grid well within a frame budget`() {
        val rnd = Random(7)
        val n = 100_000
        val pts = List(n) { Offset(rnd.nextFloat() * 1080, rnd.nextFloat() * 2000) }
        val w = DoubleArray(n) { 1.0 + rnd.nextInt(20) }
        heatGrid(pts.take(1000), w, 1080, 2000, 8, HEAT_RADIUS_PX) // warm the JIT
        val t0 = System.nanoTime()
        val g = heatGrid(pts, w, 1080, 2000, 8, HEAT_RADIUS_PX)
        val ms = (System.nanoTime() - t0) / 1e6
        assertEquals(135 * 250, g.size)
        assertTrue(ms < 300, "took $ms ms")
    }
}
