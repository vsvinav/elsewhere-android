package com.elsewhere.app.map

import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.data.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Pin-layer cases of web/src/map/layers.ts (`pinLayers`), which the web never covered in layers.test.ts.

class PinLayersTest {
    private val data = SampleRecord.record
    private val layers = PinLayers(data)

    @Test fun `every stop but home is a dot, most visited first, and chips count them by kind`() {
        assertEquals(data.places.size - 1, layers.all.size)
        assertTrue(layers.all.none { it.place.role == Role.HOME })
        assertEquals(layers.all.map { it.place.nVisits }.sortedDescending(), layers.all.map { it.place.nVisits })
        assertEquals(listOf("home"), layers.homes.map { it.id })
        assertEquals(layers.all.size, layers.counts.values.sum())
        assertEquals(1, layers.counts[Cat.WORK])
    }

    @Test fun `hidden kinds and the search compose, and homes stay put whatever is toggled`() {
        val eat = PinOpts(hidden = setOf(Cat.EAT))
        assertTrue(layers.shown(eat).none { it.cat == Cat.EAT })
        val both = PinOpts(hidden = setOf(Cat.EAT), query = "the ")
        assertEquals(listOf("work", "nyc"), layers.shown(both).map { it.id })
        assertEquals(listOf("tokyo-cafe"), layers.shown(PinOpts(query = "OMOIDE")).map { it.id })
        val none = layers.scene(10.0, PinOpts(hidden = Cat.entries.toSet()))
        assertTrue(none.pins.isEmpty() && none.clusters.isEmpty())
        assertEquals(listOf("home"), none.homes.map { it.dot.id })
    }

    @Test fun `pins shrink with the map through the k factor`() {
        assertEquals(0.45f, zoomFactor(2.0), 1e-6f)
        assertEquals(1f, zoomFactor(6.0), 1e-6f)
        assertEquals(1.3f, zoomFactor(14.0), 1e-6f)
        assertEquals(1.3f, zoomFactor(30.0), 1e-6f)
        val far = layers.scene(16.0, PinOpts()).pins.first { it.dot.id == "market" }
        val near = layers.scene(2.0, PinOpts()).pins.firstOrNull { it.dot.id == "market" }
        // At zoom 2 the Boston stops share a cell, so the market is in a cluster; compare through the size rule instead.
        assertNull(near)
        assertEquals(pinSize(zoomFactor(16.0), far.dot, SizeBy.VISITS), far.size, 1e-6f)
        assertTrue(pinSize(0.45f, far.dot, SizeBy.VISITS) < far.size)
        val byHours = layers.scene(16.0, PinOpts(sizeBy = SizeBy.HOURS)).pins.first { it.dot.id == "market" }
        assertFalse(byHours.size == far.size)
    }

    @Test fun `clusters are cached per half zoom while sizes follow the exact zoom`() {
        val a = layers.scene(5.1, PinOpts()); val b = layers.scene(5.4, PinOpts()); val c = layers.scene(5.6, PinOpts())
        assertSame(a.clusters, b.clusters)
        assertNotSame(a.clusters, c.clusters)
        assertTrue(a.k < b.k)
        assertTrue(a.clusters.isNotEmpty())
        assertTrue(a.clusters.all { it.count >= 2 })
        assertSame(a.clusters, layers.scene(5.2, PinOpts(selected = "home")).clusters)
    }

    @Test fun `labels start at zoom 6, home first, printed in caps`() {
        assertTrue(layers.scene(5.5, PinOpts()).labels.isEmpty())
        val l = layers.scene(12.0, PinOpts()).labels
        assertEquals("home", l.first().dot.id)
        assertEquals("HOME", l.first().text)
        assertTrue(l.map { it.dot.id }.toSet().size == l.size)
    }

    @Test fun `the open pin gets a halo and a home stays a house`() {
        assertNull(layers.scene(12.0, PinOpts()).selected)
        assertNull(layers.scene(12.0, PinOpts(selected = "nope")).selected)
        val home = layers.scene(12.0, PinOpts(selected = "home")).selected
        assertNotNull(home); assertTrue(home!!.pin.home)
        val pin = layers.scene(12.0, PinOpts(selected = "market")).selected!!
        assertFalse(pin.pin.home)
        assertTrue(pin.topSize > pin.pin.size && pin.backSize > pin.topSize)
        assertEquals(18f, pin.haloRadius)
    }

    @Test fun `fit pins frames the shown stops and always home`() {
        val b = layers.fitBounds(PinOpts())!!
        assertEquals(35.68, b.swLat, 1e-9); assertEquals(139.69, b.neLng, 1e-9)
        val onlyHome = layers.fitBounds(PinOpts(hidden = Cat.entries.toSet()))!!
        assertEquals(Bounds(42.36, -71.06, 42.36, -71.06), onlyHome)
        val homeless = PinLayers(data.copy(places = mapOf("x" to Fx.place(null, nVisits = 3, lat = 1.0, lng = 2.0, types = listOf("cafe")))))
        assertNull(homeless.fitBounds(PinOpts(hidden = setOf(Cat.EAT))))
    }

    @Test fun `a stop summary counts cities and countries of what is shown`() {
        val s = summary(layers.shown(PinOpts(hidden = setOf(Cat.EAT))))
        assertEquals(4, s.cities); assertEquals(3, s.countries)
        assertEquals(layers.shown(PinOpts(hidden = setOf(Cat.EAT))).size, s.stops)
    }

    @Test fun `heat replaces the pins and clusters but keeps the homes, and off again restores them exactly`() {
        val off = layers.scene(5.1, PinOpts())
        val on = layers.scene(5.1, PinOpts(heat = true))
        assertTrue(on.pins.isEmpty() && on.clusters.isEmpty())
        assertEquals(off.homes, on.homes)
        assertEquals(off.labels, on.labels)
        assertEquals(off.k, on.k)
        assertNotNull(layers.scene(12.0, PinOpts(heat = true, selected = "market")).selected)
        assertEquals(off, layers.scene(5.1, PinOpts()))
        // The filter still decides what warms the map.
        assertEquals(layers.shown(PinOpts(hidden = setOf(Cat.EAT))), layers.shown(PinOpts(hidden = setOf(Cat.EAT), heat = true)))
    }
}
