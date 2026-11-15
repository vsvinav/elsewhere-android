package com.elsewhere.app.ui.tabs.stops

import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.Cat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StopsUiTest {
    private val data = SampleRecord.record

    @Test fun `default view lists every place, most visited first`() {
        val ui = stopsUi(data, StopsFilter())
        assertEquals(data.places.size, ui.total)
        assertEquals(ui.total, ui.list.size)
        assertEquals(ui.list.map { it.place.nVisits }.sortedDescending(), ui.list.map { it.place.nVisits })
        assertEquals(data.places.count { it.value.name != null }, ui.named)
    }

    @Test fun `a category chip keeps only stops of that kind and counts are per category`() {
        val ui = stopsUi(data, StopsFilter(cat = Cat.EAT))
        assertTrue(ui.list.isNotEmpty())
        assertTrue(ui.list.all { it.cat == Cat.EAT })
        assertEquals(ui.list.size, ui.catCounts[Cat.EAT])
        assertTrue(ui.catCounts.values.sum() == ui.total)
    }

    @Test fun `named only and other sorts apply`() {
        val named = stopsUi(data, StopsFilter(namedOnly = true))
        assertTrue(named.list.all { it.place.name != null })
        val recent = stopsUi(data, StopsFilter(sort = Sort.RECENT))
        assertEquals(recent.list.map { it.place.lastTs }.sortedDescending(), recent.list.map { it.place.lastTs })
        val time = stopsUi(data, StopsFilter(sort = Sort.HOURS))
        assertEquals(time.list.map { it.place.totalHours }.sortedDescending(), time.list.map { it.place.totalHours })
    }
}
