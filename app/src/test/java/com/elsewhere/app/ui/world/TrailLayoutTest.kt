package com.elsewhere.app.ui.world

import com.elsewhere.app.Fx
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Pt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot

class TrailLayoutTest {
    private fun drive(n: Int, lat0: Double, lng0: Double, lat1: Double, lng1: Double, t0: Long = 0) =
        Path(t0, t0 + n, (0 until n).map { i -> val t = i / (n - 1.0); Pt(lat0 + (lat1 - lat0) * t, lng0 + (lng1 - lng0) * t, t0 + i) })

    @Test fun `every stroke keeps its first and last point however many there are`() {
        // Twenty drives of a thousand fixes each, far past the point budget; one short hop of three.
        val drives = (0 until 20).map { i -> drive(1000, 34.0 + i * 0.3, -118.0, 34.0 + i * 0.3 + 0.2, -117.0, i * 2000L) } + drive(3, 36.0, -120.0, 36.0, -119.9)
        val l = trailLayout(Fx.record(strokes = drives), emptyList(), null)
        assertEquals(drives.size, l.lines.size)
        val proj = l.proj!!
        for ((i, d) in drives.withIndex()) {
            val line = l.lines[i]
            val a = d.pts.first(); val b = d.pts.last()
            assertTrue(hypot(line[0] - proj.px(a.lng), line[1] - proj.py(a.lat)) < 1e-3, "stroke $i starts at its first fix")
            assertTrue(hypot(line[line.size - 2] - proj.px(b.lng), line[line.size - 1] - proj.py(b.lat)) < 1e-3, "stroke $i ends at its last fix")
            assertTrue(line.size >= 4)
            // Thinning is by distance on the sheet: neighbours kept are never closer than the gap, bar the last.
            var j = 2
            while (j + 3 < line.size) { assertTrue(hypot(line[j] - line[j - 2], line[j + 1] - line[j - 1]) >= TRAIL_GAP - 1e-3, "stroke $i has a crowded pair at $j"); j += 2 }
        }
        assertTrue(l.lines.sumOf { it.size / 2 } < 20 * 1000, "the lines are thinned")
    }

    @Test fun `a flight and its return print one arc, so the dashes stay dashes`() {
        val out = Fx.act(0, 1, "FLYING", 9_000_000.0, sLng = -122.4, sLat = 37.8, eLng = 139.7, eLat = 35.7)
        val back = Fx.act(2, 3, "FLYING", 9_000_000.0, sLng = 139.71, sLat = 35.69, eLng = -122.39, eLat = 37.79)
        val other = Fx.act(4, 5, "FLYING", 9_000_000.0, sLng = -122.4, sLat = 37.8, eLng = 151.2, eLat = -33.9)
        val l = trailLayout(Fx.record(activities = listOf(out, back, other)), emptyList(), null)
        assertEquals(2, l.flights.size)
    }

    @Test fun `road dots sit a pitch apart along the line and reach its end`() {
        // A 70-unit line at half scale is 35 px: dots at 0, 7, …, 35 → six of them, the last on the end.
        val line = floatArrayOf(0f, 0f, 40f, 0f, 70f, 0f)
        val dots = roadDots(listOf(line), u = 0.5f, pitch = 7f)
        assertEquals(6, dots.size / 2)
        assertEquals(0f, dots[0]); assertEquals(7f, dots[2]); assertEquals(35f, dots[dots.size - 2], 1e-3f)
        // A line ending just past a dot still gets its end marked once it is half a pitch on.
        val short = roadDots(listOf(floatArrayOf(0f, 0f, 11f, 0f)), u = 1f, pitch = 7f)
        assertEquals(listOf(0f, 7f, 11f), listOf(short[0], short[2], short[4])); assertEquals(6, short.size)
    }
}
