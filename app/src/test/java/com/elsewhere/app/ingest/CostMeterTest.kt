package com.elsewhere.app.ingest

import com.elsewhere.app.data.Counters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Twin of web/src/usage/usage.test.ts. */
class CostMeterTest {
    @Test fun `tally adds up and prices at list rates`() = runTest {
        val m = MemoryCostMeter()
        m.tally(Kind.DETAILS, 100); m.tally(Kind.PHOTOS, 100); m.tally(Kind.MAPS, 10)
        val u = m.counters.first()
        assertEquals(listOf(100, 100, 10), listOf(u.detailsCalls, u.photoCalls, u.mapLoads))
        assertEquals(1.7 + 0.7 + 0.07, u.dollars(), 1e-9)
        m.reset()
        assertEquals(Counters(since = m.counters.first().since), m.counters.first())
    }

    @Test fun `the details budget is what the cap leaves at list price`() {
        assertEquals(588, Counters().detailsBudget(10.0))
        assertEquals(0, Counters(detailsCalls = 600).detailsBudget(10.0))
        assertEquals(100, Counters(detailsCalls = 100, photoCalls = 1000, mapLoads = 0).detailsBudget(10.0 * 0 + 1.7 + 7.0 + 1.7))
    }
}
