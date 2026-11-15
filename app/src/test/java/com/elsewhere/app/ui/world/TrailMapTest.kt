package com.elsewhere.app.ui.world

import com.elsewhere.app.Fx
import com.elsewhere.app.analytics.Stop
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Pt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrailMapTest {
    private fun stop(city: String, lat: Double, lng: Double, nights: Int) = Stop(city, "US", nights, 1, 0, 0, lat, lng)
    // A long drive from LA to Boston, dense enough that the 1%–99% fit still frames both ends.
    private val stroke = Path(0, 200, (0..200).map { i -> val t = i / 200.0; Pt(34.05 + (42.36 - 34.05) * t, -118.24 + (-71.06 + 118.24) * t, i.toLong()) })

    @Test fun `an empty record lays out nothing`() {
        val l = trailLayout(Fx.record(), emptyList(), null)
        assertTrue(l.lines.isEmpty() && l.flights.isEmpty() && l.pins.isEmpty() && l.land.isEmpty())
    }

    @Test fun `west is left and north is up inside the sheet`() {
        val l = trailLayout(Fx.record(strokes = listOf(stroke)), listOf(stop("Boston", 42.36, -71.06, 3), stop("Los Angeles", 34.05, -118.24, 2)), null)
        assertEquals(1, l.lines.size)
        val boston = l.pins.first { it.city == "Boston" }; val la = l.pins.first { it.city == "Los Angeles" }
        assertTrue(la.x < boston.x, "LA prints left of Boston")
        assertTrue(boston.y < la.y, "Boston prints above LA")
        for (p in l.pins) assertTrue(p.x in 0f..TRAIL_W && p.y in 0f..TRAIL_H)
        assertTrue(boston.first && !la.first)
        assertTrue(boston.alignEnd, "a pin at the right edge labels to its left")
    }

    @Test fun `only long flights become arcs`() {
        val short = Fx.act(0, 1, "FLYING", 50_000.0, sLng = -71.0, sLat = 42.0, eLng = -72.0, eLat = 42.5)
        val long = Fx.act(0, 1, "FLYING", 400_000.0, sLng = -71.0, sLat = 42.0, eLng = -74.0, eLat = 40.7)
        val drive = Fx.act(0, 1, "IN_PASSENGER_VEHICLE", 400_000.0, sLng = -71.0, sLat = 42.0, eLng = -74.0, eLat = 40.7)
        val l = trailLayout(Fx.record(activities = listOf(short, long, drive)), emptyList(), null)
        assertEquals(1, l.flights.size)
    }

    @Test fun `cities that would print on top of one another keep the one with more nights`() {
        val l = trailLayout(
            Fx.record(strokes = listOf(stroke)),
            listOf(stop("Boston", 42.36, -71.06, 5), stop("Cambridge", 42.37, -71.1, 2), stop("Los Angeles", 34.05, -118.24, 1)), null,
        )
        assertEquals(listOf("Boston", "Los Angeles"), l.pins.map { it.city })
    }

    @Test fun `a ring that crosses the antimeridian is cut into one piece per side`() {
        // Chukotka-like: east of 180, across to the west side and back, then home.
        val ring = doubleArrayOf(170.0, 60.0, 180.0, 60.0, -180.0, 60.0, -170.0, 60.0, -170.0, 70.0, -180.0, 70.0, 180.0, 70.0, 170.0, 70.0, 170.0, 60.0)
        val parts = splitAntimeridian(ring)
        assertEquals(2, parts.size)
        for (part in parts) {
            val lngs = (part.indices step 2).map { part[it] }
            assertTrue(lngs.all { it >= 0 } || lngs.all { it <= 0 }, "a part must not straddle: $lngs")
            assertTrue(lngs.max() - lngs.min() < 180)
        }
        val l = trailLayout(Fx.record(strokes = listOf(stroke)), emptyList(), FeatureCollection(listOf(ring)))
        assertEquals(2, l.land.size)
        val plain = doubleArrayOf(-71.0, 42.0, -72.0, 42.0, -72.0, 41.0)
        assertEquals(1, splitAntimeridian(plain).size)
    }

    @Test fun `land rings are projected into the same space`() {
        val land = FeatureCollection(listOf(doubleArrayOf(-71.0, 42.0, -72.0, 42.0, -72.0, 41.0)))
        val l = trailLayout(Fx.record(strokes = listOf(stroke)), emptyList(), land)
        assertEquals(1, l.land.size)
        assertEquals(6, l.land[0].size)
    }
}

class TrailLabelsTest {
    private fun stop(city: String, lat: Double, lng: Double, nights: Int) = Stop(city, "US", nights, 1, 0, 0, lat, lng)
    private val stroke = Path(0, 200, (0..200).map { i -> val t = i / 200.0; Pt(34.05 + (42.36 - 34.05) * t, -118.24 + (-71.06 + 118.24) * t, i.toLong()) })

    @Test fun `far cities are still named when the busiest nine all share one metro`() {
        val metro = (0 until 12).map { stop("Suburb $it", 42.36 + it * 0.001, -71.06 + it * 0.001, 50 - it) }
        val l = trailLayout(Fx.record(strokes = listOf(stroke)), metro + stop("Los Angeles", 34.05, -118.24, 2), null)
        assertEquals(listOf("Suburb 0", "Los Angeles"), l.pins.map { it.city })
        assertTrue(l.pins.size <= 7)
    }

    @Test fun `every city slept in is framed, even one with hardly any fixes`() {
        // A year of Boston errands; the Los Angeles weekend is under one percent of the record.
        val boston = Path(0, 400, (0..400).map { i -> Pt(42.36 + (i % 7) * 0.003, -71.06 + (i % 5) * 0.003, i.toLong()) })
        val l = trailLayout(Fx.record(strokes = listOf(boston)), listOf(stop("Boston", 42.36, -71.06, 50), stop("Los Angeles", 34.05, -118.24, 2)), null)
        val la = l.pins.first { it.city == "Los Angeles" }
        assertTrue(la.x >= 36f && la.x <= TRAIL_W - 36f && la.y >= 36f && la.y <= TRAIL_H - 36f, "LA pin at ${la.x},${la.y} should sit inside the padding")
    }

    @Test fun `a label that would cover another slides to the other side or is hidden`() {
        val a = LabelAnchor(100f, 50f, 60f, 10f, alignEnd = false)
        val b = LabelAnchor(100f, 52f, 60f, 10f, alignEnd = false)
        val c = LabelAnchor(104f, 48f, 60f, 10f, alignEnd = false)
        val boxes = labelBoxes(listOf(a, b, c), 400f, 100f, gap = 4f)
        assertTrue(boxes[0]!!.left > 100f, "the first label sits to the right of its pin")
        assertTrue(boxes[1]!!.right < 100f, "the second slides to the left of its pin")
        assertEquals(null, boxes[2], "the third has no room and is hidden")
    }

    @Test fun `labels stay inside the sheet`() {
        val edge = LabelAnchor(390f, 50f, 60f, 10f, alignEnd = false)
        val top = LabelAnchor(50f, 2f, 60f, 10f, alignEnd = false)
        val boxes = labelBoxes(listOf(edge, top), 400f, 100f, gap = 4f)
        assertTrue(boxes[0]!!.right <= 400f && boxes[0]!!.right < 390f, "a label at the right edge flips left")
        assertTrue(boxes[1]!!.top >= 0f, "a label at the top is pushed down inside the sheet")
    }
}
