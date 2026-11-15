package com.elsewhere.app.map

import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.Cat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeatCacheTest {
    private var projections = 0
    private val cache = HeatCache { cam, w, h, d -> val p = Projector(cam, w, h, d); HeatProjector { lat, lng -> projections++; p.at(lat, lng) } }
    private val layers = PinLayers(SampleRecord.record)
    private val boston = Cam(42.36, -71.06, 12f)

    @Test fun `the same key is free, a new camera or filter regrids, and a filter seen before reuses its list`() {
        val shown = layers.shown(PinOpts())
        val a = cache.image(HeatKey(boston, shown, 400, 800), 2f)
        assertNotNull(a); assertEquals(1, cache.regrids)
        val n = projections
        assertTrue(n in 1..shown.size)
        assertSame(a, cache.image(HeatKey(boston, layers.shown(PinOpts()), 400, 800), 2f))
        assertEquals(1, cache.regrids); assertEquals(n, projections)
        cache.image(HeatKey(boston.copy(zoom = 13f), shown, 400, 800), 2f)
        assertEquals(2, cache.regrids)
        cache.image(HeatKey(boston.copy(zoom = 13f), layers.shown(PinOpts(hidden = setOf(Cat.EAT))), 400, 800), 2f)
        assertEquals(3, cache.regrids)
        cache.image(HeatKey(boston.copy(zoom = 13f), layers.shown(PinOpts(hidden = setOf(Cat.EAT))), 400, 800), 2f)
        assertEquals(3, cache.regrids)
        cache.image(HeatKey(boston.copy(zoom = 13f), layers.shown(PinOpts(hidden = setOf(Cat.STAY))), 400, 800), 2f)
        assertEquals(4, cache.regrids)
    }

    @Test fun `a view with no size has no picture`() {
        assertNull(cache.image(HeatKey(boston, layers.shown(PinOpts()), 0, 800), 2f))
        assertNull(cache.image(HeatKey(boston, layers.shown(PinOpts()), 400, 0), 2f))
        assertEquals(0, cache.regrids); assertEquals(0, projections)
    }

    @Test fun `only the stops near the view are projected, so a hundred thousand far away cost almost nothing`() {
        val rnd = Random(3)
        val n = 100_000
        // 1% around Boston, within the view; the rest spread over the world and the far side of the date line.
        val places = (0 until n).associate { i ->
            val near = i % 100 == 0
            val lat = if (near) 42.36 + (rnd.nextDouble() - 0.5) * 0.05 else rnd.nextDouble() * 140 - 70
            val lng = if (near) -71.06 + (rnd.nextDouble() - 0.5) * 0.08 else rnd.nextDouble() * 360 - 180
            "p$i" to Fx.place(nVisits = 1 + rnd.nextInt(9), lat = lat, lng = lng, types = listOf("cafe"))
        }
        val big = PinLayers(SampleRecord.record.copy(places = places))
        val shown = big.shown(PinOpts())
        cache.image(HeatKey(boston, shown, 1080, 2000), 3f) // warm the JIT
        projections = 0
        val t0 = System.nanoTime()
        cache.image(HeatKey(boston.copy(zoom = 12.5f), shown, 1080, 2000), 3f)
        val ms = (System.nanoTime() - t0) / 1e6
        assertTrue("projected $projections", projections in 1000..1500)
        assertTrue("took $ms ms", ms < 15)
    }
}
