package com.elsewhere.app.ui.tabs.dates

import com.elsewhere.app.Fx
import com.elsewhere.app.data.Trip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeckTest {
    private val d = Fx.D
    private fun trip(start: Long, nights: Int, cities: List<String>, km: Map<String, Double>, far: Double) =
        Trip(start, start + nights * d, nights, far, km, cities, null)
    private fun fly(start: Long, mode: String = "FLYING") = Fx.act(start, start + 3600, mode, 500_000.0)

    private val lisbon = trip(Fx.utc(2024, 4, 10), 7, listOf("Lisbon", "Sintra"), mapOf("FLYING" to 10_000.0, "WALKING" to 60.0), 5180.0)
    private val tokyo = trip(Fx.utc(2023, 9, 2), 4, listOf("Tokyo"), mapOf("FLYING" to 21_600.0), 10_800.0)
    private val nyc = trip(Fx.utc(2025, 1, 14), 2, emptyList(), mapOf("IN_TRAIN" to 700.0, "WALKING" to 18.0), 306.0)
    private val data = Fx.record(
        trips = listOf(lisbon, tokyo, nyc),
        activities = listOf(
            fly(lisbon.start), fly(lisbon.end), fly(lisbon.start - 1), fly(lisbon.end + 1), // both ends inclusive, one second outside is out
            fly(tokyo.start + d), fly(tokyo.start + 2 * d, "IN_TRAIN"), // only flights count
            fly(nyc.start + d, "IN_TRAIN"),
        ).shuffled(),
    )
    private val cards = deckCards(data)

    @Test fun `deals newest first with route, nights, km, flights and the farthest point`() {
        assertEquals(listOf("TRIP", "Lisbon → Sintra", "Tokyo"), cards.map { it.title })
        assertEquals(listOf(2, 7, 4), cards.map { it.nights })
        assertEquals(listOf(718.0, 10_060.0, 21_600.0), cards.map { it.km })
        assertEquals(listOf(306.0, 5180.0, 10_800.0), cards.map { it.farthestKm })
        assertEquals("${lisbon.start}-${lisbon.end}", cards[1].tripId)
        assertEquals(lisbon.start to lisbon.end, cards[1].departed to cards[1].returned)
    }

    @Test fun `counts flights whose start falls inside the trip, boundaries included`() {
        assertEquals(listOf(0, 2, 1), cards.map { it.flights })
    }

    @Test fun `a record with no flights or no trips is fine`() {
        assertEquals(0, deckCards(Fx.record(trips = listOf(lisbon)))[0].flights)
        assertEquals(emptyList<Card>(), deckCards(Fx.record(activities = data.activities)))
    }

    @Test fun `scales - 2000 trips over 200k activities build in one pass, not one scan per trip`() {
        val t0 = Fx.utc(2000, 0)
        val trips = List(2000) { i -> trip(t0 + i * 5 * d, 3, listOf("X"), mapOf("FLYING" to 1.0), 1.0) }
        val acts = List(200_000) { i -> fly(t0 + i * 4320L) }
        val rec = Fx.record(trips = trips, activities = acts)
        deckCards(rec) // warm the JIT once; the timing is for the second build
        val start = System.nanoTime()
        val out = deckCards(rec)
        val ms = (System.nanoTime() - start) / 1e6
        assertEquals(2000, out.size)
        assertTrue(out.all { it.flights == (3 * d / 4320).toInt() + 1 })
        assertTrue(ms < 300, "took $ms ms")
    }

    private fun c(nights: Int = 0, km: Double = 0.0, flights: Int = 0, far: Double = 0.0) = Card("", "", 0, 0, nights, km, flights, far)

    @Test fun `compare gives the sign of a minus b per stat`() {
        assertEquals(Compared(1, 0, -1, 0), compare(c(7, 100.0, 2, 5.0), c(4, 100.0, 3, 5.0)))
        assertEquals(-1, compare(c(nights = 1), c(nights = 2)).nights)
    }

    @Test fun `search matches every token against the route and the month or year departed, case-insensitively`() {
        assertEquals(listOf("Lisbon → Sintra"), searchCards(cards, "lisbon").map { it.title })
        assertEquals(1, searchCards(cards, "LISBON 2024").size)
        assertEquals(0, searchCards(cards, "lisbon 2023").size)
        assertEquals(1, searchCards(cards, "may").size)
        assertEquals(listOf("TRIP"), searchCards(cards, "2025").map { it.title })
        assertEquals(1, searchCards(cards, "sin").size)
    }

    @Test fun `an empty query is every card`() {
        assertSame(cards, searchCards(cards, "  "))
    }
}
