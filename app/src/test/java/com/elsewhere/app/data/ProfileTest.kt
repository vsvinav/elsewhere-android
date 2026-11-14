package com.elsewhere.app.data

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ProfileTest {
    private val a = Home(lat = 42.36, lng = -71.06, city = "Boston", cc = "US", from = 0, to = 1_700_000_000)
    private val b = Home(lat = 37.37, lng = -122.04, city = "Sunnyvale", cc = "US", from = 1_700_000_000, to = null)

    @Test fun `homeAt picks the home whose window covers ts, open-ended last`() {
        assertSame(a, homeAt(listOf(a, b), 1_600_000_000))
        assertSame(b, homeAt(listOf(a, b), 1_700_000_000))
        assertSame(b, homeAt(listOf(a, b), 1_900_000_000))
    }

    @Test fun `homeAt falls back to the first home before every window`() {
        val late = a.copy(from = 100)
        assertSame(late, homeAt(listOf(late, b), 50))
    }

    @Test fun `a home can recur as a later stint after someone else was home`() {
        val b2 = b.copy(to = 1_800_000_000)
        val again = a.copy(from = 1_800_000_000, to = null)
        assertSame(b2, homeAt(listOf(a, b2, again), 1_750_000_000))
        assertSame(again, homeAt(listOf(a, b2, again), 1_850_000_000))
    }
}
