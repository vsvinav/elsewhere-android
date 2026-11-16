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

    @Test fun `cards and the list below never share a fact`() {
        val all = listOf(f("a", Kind.EXPLORED), f("b", card = true), f("c", Kind.HABIT, card = true), f("d"))
        val (cards, rest) = splitFacts(all)
        assertEquals(listOf("b", "c"), cards.map { it.id })
        assertEquals(listOf("a", "d"), rest.map { it.id })
        assertTrue(cards.none { it in rest })
    }

    @Test fun `a thin record with fewer than two card facts shares everything`() {
        val all = listOf(f("a"), f("b", card = true))
        val (cards, rest) = splitFacts(all)
        assertEquals(all, cards)
        assertEquals(all, rest)
    }

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
        assertTrue(ui.cards.size >= 2)
        assertTrue(ui.rest.isNotEmpty())
        assertTrue(ui.cards.none { it in ui.rest })
        assertTrue(ui.hub!!.yearList.isNotEmpty())
        assertTrue(ui.passport!!.total > 0)
        assertEquals(2024, ui.year)
        assertEquals(Units.KM, ui.units)
        assertTrue(ui.kinds.isNotEmpty())
    }
}
