package com.elsewhere.app.analytics

import com.elsewhere.app.data.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CategoryTest {
    @Test fun `reads a kind of stop off Google types`() {
        assertEquals(Cat.EAT, categoryOf("pizza_restaurant", emptyList(), null))
        assertEquals(Cat.TRANSIT, categoryOf("subway_station", listOf("transit_station"), null))
        assertEquals(Cat.STAY, categoryOf(null, listOf("point_of_interest", "hotel"), null))
        assertEquals(Cat.TRANSIT, categoryOf("bus_stop", emptyList(), null))
        assertEquals(Cat.WORK, categoryOf(null, emptyList(), Role.WORK))
        assertEquals(Cat.OTHER, categoryOf(null, emptyList(), null))
    }
}
