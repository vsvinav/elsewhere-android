package com.elsewhere.app.map

import com.elsewhere.app.Fx
import com.elsewhere.app.data.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RevealTest {
    private val data = Fx.record(
        places = mapOf(
            "home" to Fx.place(role = Role.HOME, nVisits = 90, firstTs = 0),
            "once" to Fx.place(nVisits = 1, firstTs = 10),
            "cafe" to Fx.place(name = " Cafe ", nVisits = 5, firstTs = 300),
            "gym" to Fx.place(name = "Gym", nVisits = 20, firstTs = 100),
            "pub" to Fx.place(label = "Pub", nVisits = 3, firstTs = 200),
        ),
    )

    @Test fun `only places gone back to count, reached ones come most visited first, and the list is reused until it grows`() {
        val dots = RevealDots(data)
        assertEquals(3, dots.size)
        assertEquals(emptyList<String>(), dots.reachedBy(50).map { it.id })
        val at250 = dots.reachedBy(250)
        assertEquals(listOf("gym", "pub"), at250.map { it.id })
        assertSame(at250, dots.reachedBy(299))
        assertEquals(listOf("gym", "cafe", "pub"), dots.reachedBy(300).map { it.id })
        assertEquals("Cafe", dots.reachedBy(300)[1].title)
        assertEquals(listOf("gym"), dots.reachedBy(150).map { it.id })
    }
}
