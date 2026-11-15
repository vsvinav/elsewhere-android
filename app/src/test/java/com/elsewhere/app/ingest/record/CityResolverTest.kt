package com.elsewhere.app.ingest.record

import com.elsewhere.app.ingest.Cities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CityResolverTest {
    private val cities = Cities(
        lat = doubleArrayOf(42.3601, 37.7749), lng = doubleArrayOf(-71.0589, -122.4194),
        name = listOf("Boston", "San Francisco"), cc = listOf("US", "US"),
    )
    private val resolver = CityResolver(cities)

    @Test fun `names the nearest city within 50 km`() {
        assertEquals("Boston" to "US", resolver.resolve(42.3632761, -71.0643721))
        assertEquals("San Francisco" to "US", resolver.resolve(37.80, -122.27)) // Oakland, ~13 km
    }

    @Test fun `far from any city both halves are null`() {
        assertEquals(null to null, resolver.resolve(41.0, -71.06)) // 150 km south of Boston, at sea
        assertEquals(null to null, resolver.resolve(0.0, -160.0))
    }
}
