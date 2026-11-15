package com.elsewhere.app.ui.nav

import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.D
import com.elsewhere.app.data.Trip
import com.elsewhere.app.ui.Tab
import com.elsewhere.app.ui.shell.RangeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoorNavTest {
    private val dispatcher = StandardTestDispatcher()
    private val jan = Fx.utc(2024, 0, 1)
    private val bounds = jan..(jan + 365 * D - 1)
    private val trip = Trip(jan + 40 * D, jan + 47 * D, 7, 800.0, emptyMap(), listOf("Lisbon"), null)
    private val rec = Fx.record(range = bounds, trips = listOf(trip), places = mapOf("cafe" to Fx.place(city = "Lisbon", lat = 38.7, lng = -9.1)))
    private val store = RangeStore(Fakes.Source(rec), CoroutineScope(dispatcher)).also { it.range.value = bounds }
    private val nav = DoorNav(store, CoroutineScope(dispatcher))
    private fun run() = dispatcher.scheduler.runCurrent()

    @Test fun `a range door moves the window, asks the map to frame every pin, and remembers where it came from`() = runTest(dispatcher) {
        nav.go(Door.Range(jan + 10 * D, jan + 20 * D, "11 days"), from = Tab.NUMBERS); run()
        assertEquals(Tab.MAP, nav.jump.value)
        assertEquals((jan + 10 * D)..(jan + 20 * D), store.range.value)
        assertEquals(Opening(select = null, fit = null, range = (jan + 10 * D)..(jan + 20 * D)), nav.opening.value)
        assertEquals(Origin("11 days", Tab.NUMBERS), nav.origin.value)
    }

    @Test fun `a range door never leaves the record's bounds`() = runTest(dispatcher) {
        nav.go(Door.Range(jan - 30 * D, jan + 400 * D, "all"), from = Tab.TOUR); run()
        assertEquals(bounds, store.range.value)
    }

    @Test fun `a trip door becomes the trip's window`() = runTest(dispatcher) {
        nav.go(Door.Trip(trip.doorId, "7 nights"), from = Tab.TOUR); run()
        assertEquals(Tab.MAP, nav.jump.value)
        assertEquals(trip.start..trip.end, store.range.value)
        assertEquals(Origin("7 nights", Tab.TOUR), nav.origin.value)
    }

    @Test fun `a trip door for a trip the record has not got goes nowhere`() = runTest(dispatcher) {
        nav.go(Door.Trip("nope", "x"), from = Tab.TOUR); run()
        assertNull(nav.jump.value); assertNull(nav.origin.value)
        assertEquals(bounds, store.range.value)
    }

    @Test fun `a place door selects and frames the pin and leaves the window alone when the moment is inside it`() = runTest(dispatcher) {
        nav.go(Door.Place("cafe", at = jan + 100 * D, label = "Café"), from = Tab.FACTS); run()
        assertEquals(Tab.MAP, nav.jump.value)
        assertEquals(bounds, store.range.value)
        assertEquals(Opening(select = "cafe", fit = listOf("cafe"), range = bounds), nav.opening.value)
    }

    @Test fun `a place door widens a window that misses its moment`() = runTest(dispatcher) {
        store.range.value = (jan + 200 * D)..(jan + 300 * D)
        nav.go(Door.Place("cafe", at = jan + 100 * D + 3600, label = "Café"), from = Tab.FACTS); run()
        assertEquals((jan + 100 * D)..(jan + 300 * D), store.range.value)
    }

    @Test fun `a places door frames the set and leaves the window alone`() = runTest(dispatcher) {
        nav.go(Door.Places(listOf("a", "b"), "2 bars"), from = Tab.FACTS); run()
        assertEquals(Opening(select = null, fit = listOf("a", "b"), range = bounds), nav.opening.value)
        assertEquals(Origin("2 bars", Tab.FACTS), nav.origin.value)
    }

    @Test fun `the map takes the jump and the opening once, the origin stays until the reader goes back`() = runTest(dispatcher) {
        nav.go(Door.Range(jan, jan + D, "a day"), from = Tab.TOUR); run()
        nav.jumped(); nav.opened()
        assertNull(nav.jump.value); assertNull(nav.opening.value)
        assertEquals(Origin("a day", Tab.TOUR), nav.origin.value)
        nav.back()
        assertNull(nav.origin.value)
        assertEquals(Tab.TOUR, nav.jump.value)
    }

    @Test fun `moving the window by hand dismisses the origin but the door's own move does not`() = runTest(dispatcher) {
        nav.go(Door.Range(jan + 10 * D, jan + 20 * D, "11 days"), from = Tab.NUMBERS); run()
        assertEquals(Origin("11 days", Tab.NUMBERS), nav.origin.value)
        store.range.value = (jan + 10 * D)..(jan + 30 * D); run()
        assertNull(nav.origin.value)
    }

    @Test fun `picking another pin dismisses the origin but the door's own pin does not`() = runTest(dispatcher) {
        nav.go(Door.Place("cafe", label = "Café"), from = Tab.FACTS); run()
        nav.pinChanged("cafe")
        assertEquals(Origin("Café", Tab.FACTS), nav.origin.value)
        nav.pinChanged("bar")
        assertNull(nav.origin.value)
    }

    @Test fun `leaving the map by the strip forgets the origin`() = runTest(dispatcher) {
        nav.go(Door.Range(jan, jan + D, "a day"), from = Tab.TOUR); run()
        nav.leave()
        assertNull(nav.origin.value); assertNull(nav.opening.value)
    }
}
