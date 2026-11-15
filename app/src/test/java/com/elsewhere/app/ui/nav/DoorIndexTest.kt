package com.elsewhere.app.ui.nav

import com.elsewhere.app.Fx
import com.elsewhere.app.data.Place
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DoorIndexTest {
    private fun p(city: String?, cc: String?, n: Int) = Fx.place(city = city, cc = cc, nVisits = n)
    private val places = mapOf("a" to p("Lisbon", "PT", 3), "b" to p("Lisbon", "PT", 9), "c" to p("Porto", "PT", 1), "d" to p("Paris", "FR", 2), "e" to p(null, null, 50))

    @Test fun `onePerCountry picks the most visited pin in each country`() {
        assertEquals(listOf("b", "d"), onePerCountry(places).sorted())
    }

    @Test fun `onePerCity picks the most visited pin in each city`() {
        assertEquals(listOf("b", "c", "d"), onePerCity(places).sorted())
    }

    @Test fun `the city index lists every pin in a city, and none for a city the record never names`() {
        val ix = CityIndex(places)
        assertEquals(listOf("a", "b"), ix.ids("Lisbon").sorted())
        assertEquals(emptyList<String>(), ix.ids("Rome"))
        assertEquals(Door.Places(emptyList(), "Rome"), ix.door("Rome", "Rome"))
        assertEquals(emptyList<String>(), ix.ids(null))
    }

    @Test fun `a day door covers the UTC day around an instant`() {
        val ts = Fx.utc(2025, 5, 10) + 12 * 3600
        assertEquals(Door.Range(Fx.utc(2025, 5, 10), Fx.utc(2025, 5, 11) - 1, "d"), dayDoor(ts, "d"))
        assertEquals(dayDoor(ts, "d"), dayDoor("2025-06-10", "d"))
    }

    @Test fun `month and year doors are cut to the record`() {
        val whole = Fx.utc(2025, 0, 1)..(Fx.utc(2026, 0, 1) - 1)
        assertEquals(Door.Range(Fx.utc(2025, 1, 1), Fx.utc(2025, 2, 1) - 1, "m"), monthDoor("2025-02", "m", whole))
        assertEquals(Door.Range(Fx.utc(2025, 0, 1), Fx.utc(2025, 0, 1) + Fx.D * 20, "y"), yearDoor(2025, "y", Fx.utc(2025, 0, 1)..(Fx.utc(2025, 0, 1) + Fx.D * 20)))
        assertEquals(Door.Range(Fx.utc(2025, 0, 1), Fx.utc(2025, 0, 1) + Fx.D * 20, "m"), monthDoor("2025-01", "m", Fx.utc(2025, 0, 1)..(Fx.utc(2025, 0, 1) + Fx.D * 20)))
        assertEquals(Door.Range(Fx.utc(2025, 2, 1), whole.last, "y"), yearDoor(2025, "y", Fx.utc(2025, 2, 1)..Fx.utc(2026, 5, 1)))
    }

    @Test fun `the door helpers stay linear on a place book the size of a long record`() {
        val big = HashMap<String, Place>(600_000)
        for (i in 0 until 300_000) big["p$i"] = p("city${i % 5000}", "C${i % 190}", i % 37)
        val t0 = System.nanoTime()
        assertEquals(190, onePerCountry(big).size)
        assertEquals(5000, onePerCity(big).size)
        val ix = CityIndex(big)
        assertEquals(60, ix.ids("city7").size)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 1500)
    }
}
