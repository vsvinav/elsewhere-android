package com.elsewhere.app.ui.world

import com.elsewhere.app.Fx
import com.elsewhere.app.analytics.Night
import com.elsewhere.app.analytics.NightKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NightsTest {
    private fun n(d: String, k: NightKind, city: String? = null) = Night(d, k, city)
    private val y2024 = Fx.utc(2024, 0)..Fx.utc(2024, 11, 31)

    @Test fun `nothing on record prints nothing`() {
        assertNull(nightsUi(listOf(n("2024-01-01", NightKind.NONE)), y2024, "Ada"))
    }

    @Test fun `counts away, new and cities and titles the strip`() {
        val ui = nightsUi(listOf(
            n("2024-01-01", NightKind.HOME), n("2024-01-02", NightKind.NEW, "Lisbon"), n("2024-01-03", NightKind.REPEAT, "Lisbon"),
            n("2024-01-04", NightKind.NEW, "Porto"), n("2024-01-05", NightKind.NONE),
        ), y2024, "Ada")!!
        assertEquals(3, ui.away); assertEquals(2, ui.fresh); assertEquals(2, ui.cities); assertEquals(4, ui.known)
        assertEquals("2024", ui.span)
        assertEquals("ADA'S ELSEWHERE 2024", ui.title)
        assertEquals("3 of 4 nights somewhere other than home, across 2 cities. 2 of them in a city you'd never slept in before.", ui.lede)
    }

    @Test fun `a range across years spells both months and no name is no possessive`() {
        val ui = nightsUi(listOf(n("2024-05-01", NightKind.HOME)), Fx.utc(2024, 4)..Fx.utc(2026, 10), null)!!
        assertEquals("May 2024 – Nov 2026", ui.span)
        assertEquals("ELSEWHERE MAY 2024 – NOV 2026", ui.title)
        assertEquals("0 of 1 nights somewhere other than home. None of them anywhere new.", ui.lede)
    }
}
