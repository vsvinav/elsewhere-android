package com.elsewhere.app.map

import com.elsewhere.app.data.Located
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Twin of web/src/map/cluster.test.ts

private data class P(override val lng: Double, override val lat: Double, val id: String) : Located

class ClusterTest {
    private val near = listOf(P(0.0, 0.0, "a"), P(0.001, 0.001, "b"), P(10.0, 10.0, "c"))

    @Test fun `close points merge at low zoom and split when zoomed in`() {
        val low = cluster(near, 2.0)
        assertEquals(1, low.groups.size)
        assertEquals(listOf("a", "b"), low.groups[0].items.map { it.id }.sorted())
        assertEquals(listOf("c"), low.singles.map { it.id })
        val high = cluster(near, 16.0)
        assertTrue(high.groups.isEmpty())
        assertEquals(3, high.singles.size)
    }

    @Test fun `a group sits at the mean of its members`() {
        val g = cluster(near, 2.0).groups.single()
        assertEquals(0.0005, g.lng, 1e-9); assertEquals(0.0005, g.lat, 1e-9)
    }
}
