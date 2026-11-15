package com.elsewhere.app.ui.tabs.facts

import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.Fact
import com.elsewhere.app.analytics.Kind
import com.elsewhere.app.data.Units
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FactsUiTest {
    private fun f(id: String, kind: Kind = Kind.SCALE, card: Boolean = false) = Fact(id, kind, id, "about $id", card = card)

    @Test fun `shuffling keeps the explored to habit arc and seed zero keeps the order`() {
        val all = listOf(f("h1", Kind.HABIT), f("e1", Kind.EXPLORED), f("e2", Kind.EXPLORED), f("r1", Kind.RECORD), f("s1", Kind.SCALE), f("e3", Kind.EXPLORED))
        assertEquals(all, arrange(all, 0))
        val shuffled = arrange(all, 1234)
        assertEquals(listOf(Kind.EXPLORED, Kind.EXPLORED, Kind.EXPLORED, Kind.RECORD, Kind.SCALE, Kind.HABIT), shuffled.map { it.kind })
        assertEquals(all.toSet(), shuffled.toSet())
        assertEquals(shuffled, arrange(all, 1234), "same seed, same deal")
    }

    @Test fun `the sample year yields facts, the deck figures and the passport in one pass`() {
        val ui = factsUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile)
        assertTrue(ui.all.isNotEmpty())
        assertTrue(ui.hub!!.yearList.isNotEmpty())
        assertTrue(ui.passport!!.total > 0)
        assertTrue(ui.days > 300)
        assertEquals(2024, ui.year)
        assertEquals(Units.KM, ui.units)
        assertTrue(ui.kinds.isNotEmpty())
    }
}
