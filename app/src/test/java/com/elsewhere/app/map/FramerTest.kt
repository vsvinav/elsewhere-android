package com.elsewhere.app.map

import androidx.compose.ui.unit.IntSize
import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.D
import com.elsewhere.app.Fx.place
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.analytics.Grain
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Trip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FramerTest {
    private val moves = ArrayList<Pair<Cam, Int>>()
    private val framer = Framer { cam, ms -> moves += cam to ms }
    private val paris = Bounds(48.8, 2.3, 48.9, 2.4)

    @Test fun `a fit asked before the view has a size runs once the size arrives, animated`() {
        framer.fit(paris, 40)
        assertEquals(emptyList<Pair<Cam, Int>>(), moves)
        framer.size = IntSize(1080, 1920)
        assertEquals(listOf(camForBounds(paris, 1080, 1920, 40) to Framer.ANIMATE_MS), moves)
        framer.size = IntSize(1920, 1080) // a rotation later does not re-frame on its own
        assertEquals(1, moves.size)
    }

    @Test fun `only the latest fit asked before layout is kept`() {
        framer.fit(Bounds(0.0, 0.0, 1.0, 1.0), 0)
        framer.fit(paris, 0)
        framer.size = IntSize(800, 600)
        assertEquals(listOf(camForBounds(paris, 800, 600, 0) to Framer.ANIMATE_MS), moves)
    }

    @Test fun `with a size known, every fit moves the camera right away`() {
        framer.size = IntSize(800, 600)
        framer.fit(paris, 10); framer.fit(paris, 10)
        assertEquals(2, moves.size)
        assertEquals(camForBounds(paris, 800, 600, 10), moves[0].first)
    }

    @Test fun `the frame is solved in dp, so a sharper screen does not zoom in tighter`() {
        val sharp = Framer { cam, _ -> moves += cam to 0 }.apply { density = 2f; size = IntSize(1024, 1024) }
        sharp.fit(paris, 80)
        assertEquals(camForBounds(paris, 512, 512, 40), moves.single().first)
    }

    @Test fun `a host that frames bounds itself gets them in physical px, once the view has a size`() {
        val asked = ArrayList<Triple<Bounds, IntSize, Int>>()
        val own = Framer { b, size, pad -> asked += Triple(b, size, pad) }.apply { density = 2f }
        own.fit(paris, 80)
        assertEquals(emptyList<Triple<Bounds, IntSize, Int>>(), asked)
        own.size = IntSize(1080, 1920)
        assertEquals(listOf(Triple(paris, IntSize(1080, 1920), 80)), asked)
    }

    // ---- What a grain holds in frame ----

    private val home = LatLng(51.5, -0.1); private val porto = LatLng(41.15, -8.61); private val madrid = LatLng(40.42, -3.70)
    private val t1 = Fx.utc(2024, 2, 10); private val t2 = Fx.utc(2024, 7, 1)
    private val data = Fx.record(
        trips = listOf(Trip(t1, t1 + 4 * D, 3, 1000.0, emptyMap(), listOf("Porto"), null), Trip(t2, t2 + 6 * D, 3, 1000.0, emptyMap(), listOf("Madrid"), null)),
        visits = listOf(
            visit(t1 + D, t1 + 3 * D, "porto-hotel", lng = porto.lng, lat = porto.lat),
            visit(t2 + 2 * D, t2 + 5 * D, "madrid-flat", lng = madrid.lng, lat = madrid.lat),
            visit(Fx.utc(2024, 7, 31), Fx.utc(2024, 8, 2), "home", lng = home.lng, lat = home.lat),
        ),
        places = mapOf("porto-hotel" to place(city = "Porto", lat = porto.lat, lng = porto.lng), "madrid-flat" to place(city = "Madrid", lat = madrid.lat, lng = madrid.lng), "home" to place(lat = home.lat, lng = home.lng)),
        home = home, range = t1..(t2 + 6 * D),
    )

    @Test fun `monthOf is the UTC calendar month holding the instant`() {
        assertEquals(Fx.utc(2024, 1) until Fx.utc(2024, 2), monthOf(Fx.utc(2024, 1, 29) + 5 * 3600))
        assertEquals(Fx.utc(2024, 11) until Fx.utc(2025, 0), monthOf(Fx.utc(2024, 11, 31) + 86399))
    }

    @Test fun `the year frames the trip under the playhead, else every trip`() {
        val one = grainBounds(data, (t1 + D).toDouble(), Grain.YEAR)!!
        assertEquals(porto.lat, one.swLat, 1e-6); assertEquals(porto.lng, one.swLng, 1e-6); assertEquals(home.lng, one.neLng, 1e-6)
        assertTrue(one.neLat >= home.lat) // the great circle bows north of both ends
        val all = grainBounds(data, (t1 + 30 * D).toDouble(), Grain.YEAR)!!
        assertEquals(porto.lng, all.swLng, 1e-6); assertEquals(madrid.lat, all.swLat, 1e-6)
        assertTrue(all.neLng > one.neLng - 1e-9)
        assertNull(grainBounds(data.copy(trips = emptyList()), t1.toDouble(), Grain.YEAR))
    }

    @Test fun `the month frames the stops that touch that calendar month, and the day frames nothing`() {
        // Every stop touching the month counts, wherever in it the playhead stands: the home stay straddles Aug 31 to Sep 2.
        assertEquals(Bounds(madrid.lat, madrid.lng, home.lat, home.lng), grainBounds(data, Fx.utc(2024, 7, 20).toDouble(), Grain.MONTH))
        assertEquals(Bounds(home.lat, home.lng, home.lat, home.lng), grainBounds(data, Fx.utc(2024, 8, 15).toDouble(), Grain.MONTH))
        assertNull(grainBounds(data, Fx.utc(2024, 5).toDouble(), Grain.MONTH))
        assertNull(grainBounds(data, t1.toDouble(), Grain.DAY))
    }

    @Test fun `the key changes exactly when the frame would`() {
        assertEquals(grainKey(data, (t1 + D).toDouble(), Grain.YEAR), grainKey(data, (t1 + 2 * D).toDouble(), Grain.YEAR))
        assertTrue(grainKey(data, (t1 + D).toDouble(), Grain.YEAR) != grainKey(data, (t1 + 30 * D).toDouble(), Grain.YEAR))
        assertEquals(grainKey(data, Fx.utc(2024, 7, 2).toDouble(), Grain.MONTH), grainKey(data, Fx.utc(2024, 7, 30).toDouble(), Grain.MONTH))
        assertTrue(grainKey(data, Fx.utc(2024, 7, 30).toDouble(), Grain.MONTH) != grainKey(data, Fx.utc(2024, 8, 1).toDouble(), Grain.MONTH))
        assertEquals(grainKey(data, t1.toDouble(), Grain.DAY), grainKey(data, t2.toDouble(), Grain.DAY))
    }

    @Test fun `stops either side of the date line frame the short way round, over the Pacific`() {
        val m = Fx.utc(2024, 3)
        val rec = Fx.record(visits = listOf(visit(m + D, m + 2 * D, "fiji", lng = 170.0, lat = -18.0), visit(m + 3 * D, m + 4 * D, "samoa", lng = -170.0, lat = -14.0)), range = m..m + 10 * D)
        val b = grainBounds(rec, (m + 2 * D).toDouble(), Grain.MONTH)!!
        assertEquals(170.0, b.swLng, 1e-9); assertEquals(190.0, b.neLng, 1e-9)
        val f = frameOf(b, 1080, 1600)
        assertEquals(180.0, f.center.lng, 1e-9)
        assertTrue(f.zoom > 3.0)
    }
}
