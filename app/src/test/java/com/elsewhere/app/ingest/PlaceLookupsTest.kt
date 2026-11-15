package com.elsewhere.app.ingest

import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/** Twin of web/src/ingest/places.test.ts: the shared, on-demand place store. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceLookupsTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = MemoryEnrichmentStore()
    private val meter = MemoryCostMeter()
    private val maps = Fakes.Maps().apply { cur.value = Settings(mapsKey = "k", keyPlaces = true) }
    private val state = EnrichState()

    /** Answers by id, or a gate the test opens; `fail` scripts one exception per id, used once. */
    private inner class Src : DetailsSource {
        val asked = ArrayList<String>()
        val gates = HashMap<String, CompletableDeferred<Unit>>()
        val fail = HashMap<String, ArrayDeque<Exception>>()
        fun fails(id: String, vararg e: Exception) { fail[id] = ArrayDeque(e.toList()) }
        override suspend fun details(id: String, key: String): Enrichment {
            asked += id
            gates[id]?.await()
            fail[id]?.removeFirstOrNull()?.let { throw it }
            return Enrichment(id.uppercase(), null, listOf("cafe"), "cafe", "places/$id/photos/1")
        }
    }
    private val src = Src()

    private fun lookups(saveMs: Long = 300, backoffMs: Long = 2000) = PlaceLookups(src, store, meter, state, scope, maps.settings, saveMs, backoffMs)
    private fun place(n: Int = 2, name: String? = null, role: Role? = null) = Fx.place(nVisits = n, name = name, role = role)
    private val places = mapOf("a" to place(), "b" to place(), "home" to place(role = Role.HOME), "named" to place(name = "Done").copy(photo = "u"))

    @AfterEach fun down() = scope.cancel()

    private fun TestScope.settle() = advanceUntilIdle()

    @Test fun `asks once per id, skipping home, cached and fully named places`() = runTest(dispatcher) {
        val l = lookups(); settle()
        store.rows["c"] = Enrichment.NONE
        l.need(listOf("a", "b", "a", "home", "named", "c", "missing"), places + ("c" to place()))
        l.need(listOf("b"), places)
        settle()
        assertEquals(listOf("a", "b"), src.asked.sorted())
        assertEquals(setOf("a", "b", "c"), store.rows.keys)
        assertEquals("A", store.rows["a"]!!.name)
        assertEquals(2, meter.counters.value.detailsCalls)
    }

    @Test fun `landed lookups reach the store in one batch after the debounce`() = runTest(dispatcher) {
        val l = lookups(saveMs = 300); settle()
        src.gates["b"] = CompletableDeferred()
        l.need(listOf("a", "b"), places)
        advanceTimeBy(100)
        assertTrue(store.puts.isEmpty())
        src.gates["b"]!!.complete(Unit)
        advanceTimeBy(250)
        assertEquals(0, store.puts.size) // b landed at ~100 ms, so the timer restarted
        settle()
        assertEquals(1, store.puts.size)
        assertEquals(setOf("a", "b"), store.puts[0].keys)
    }

    @Test fun `nothing is fetched without a key, or on a key Places refused`() = runTest(dispatcher) {
        maps.cur.value = Settings()
        val l = lookups(); settle()
        l.need(listOf("a"), places); settle()
        assertTrue(src.asked.isEmpty())
        maps.cur.value = Settings(mapsKey = "k", keyPlaces = false); settle()
        l.need(listOf("a"), places); settle()
        assertTrue(src.asked.isEmpty())
        maps.cur.value = Settings(mapsKey = "k", keyPlaces = true); settle()
        l.need(listOf("a"), places); settle()
        assertEquals(listOf("a"), src.asked)
    }

    @Test fun `a refusal stops the pool with Google's message until the key changes`() = runTest(dispatcher) {
        val l = lookups(); settle()
        src.fails("a", PlacesError("Places API (New) has not been used in project 1", 403))
        src.gates["b"] = CompletableDeferred()
        l.need(listOf("a", "b"), places); settle()
        assertEquals("Places API (New) has not been used in project 1", state.error.value)
        l.need(listOf("c"), places + ("c" to place())); settle()
        assertEquals(listOf("a", "b"), src.asked)
        src.gates["b"]!!.complete(Unit); settle()
        assertNull(l.progress.value)
        // A fresh key clears the latch and whatever is asked for next goes out.
        maps.cur.value = Settings(mapsKey = "k2", keyPlaces = true); settle()
        assertNull(state.error.value)
        l.need(listOf("c"), places + ("c" to place())); settle()
        assertEquals(listOf("a", "b", "c"), src.asked)
    }

    @Test fun `a passing failure gets one more go after a backoff, and the pool goes on`() = runTest(dispatcher) {
        val l = lookups(backoffMs = 2000); settle()
        src.fails("a", IOException("reset by peer"))
        l.need(listOf("a"), places)
        advanceTimeBy(1000)
        assertEquals(listOf("a"), src.asked)
        assertEquals(Progress(0, 1), l.progress.value)
        settle()
        assertEquals(listOf("a", "a"), src.asked)
        assertEquals("A", store.rows["a"]!!.name)
        assertNull(l.progress.value)
        // Twice unlucky: dropped, not retried for ever.
        src.fails("b", PlacesError("Places API 503", 503), PlacesError("Places API 503", 503))
        l.need(listOf("b"), places); settle()
        assertEquals(listOf("a", "a", "b", "b"), src.asked)
        assertNull(store.rows["b"])
        assertNull(l.progress.value)
        l.need(listOf("c"), places + ("c" to place())); settle()
        assertEquals("C", store.rows["c"]!!.name)
    }

    @Test fun `progress counts the burst and resets once everything has landed`() = runTest(dispatcher) {
        val l = lookups(); settle()
        src.gates["a"] = CompletableDeferred(); src.gates["b"] = CompletableDeferred()
        l.need(listOf("a"), places); settle()
        assertEquals(Progress(0, 1), l.progress.value)
        l.need(listOf("b"), places); settle()
        assertEquals(Progress(0, 2), l.progress.value)
        src.gates["a"]!!.complete(Unit); settle()
        assertEquals(Progress(1, 2), l.progress.value)
        src.gates["b"]!!.complete(Unit); settle()
        assertNull(l.progress.value)
        l.need(listOf("c"), places + ("c" to place())); settle()
        assertNull(l.progress.value)
    }

    @Test fun `clusterName takes a proper name from the cache and pays for at most max lookups`() = runTest(dispatcher) {
        val ps = mapOf("x" to place(), "y" to place(name = "Main St").copy(types = listOf("route")), "z" to place(), "w" to place())
        store.rows["x"] = Enrichment.NONE
        val addressSrc = src
        // z answers with an address, w with a proper name.
        val fake = object : DetailsSource {
            override suspend fun details(id: String, key: String): Enrichment {
                addressSrc.asked += id
                return if (id == "z") Enrichment("12 Elm Rd", null, listOf("street_address"), "street_address", null) else Enrichment("The Flat", null, listOf("lodging"), "lodging", null)
            }
        }
        val named = PlaceLookups(fake, store, meter, state, scope, maps.settings, 300, 2000); settle()
        assertEquals("The Flat", named.clusterName(listOf("x", "y", "z", "w"), ps, "k", max = 3))
        assertEquals(listOf("z", "w"), src.asked)
        assertEquals(2, meter.counters.value.detailsCalls)
        settle()
        assertEquals(setOf("x", "z", "w"), store.rows.keys)
        // Everything is now cached: no key needed, nothing paid.
        assertEquals("The Flat", named.clusterName(listOf("x", "y", "z", "w"), ps, "", max = 3))
        assertEquals(2, src.asked.size)
        // Nothing proper anywhere: the first plain name wins over null.
        assertEquals("Main St", named.clusterName(listOf("x", "y"), ps, "", max = 3))
    }

    @Test fun `clusterName and need share one cache, and neither loses the other's writes`() = runTest(dispatcher) {
        val l = lookups(); settle()
        src.gates["a"] = CompletableDeferred()
        l.need(listOf("a"), places); settle()
        val seen = ArrayList<Progress?>()
        val job = scope.launch { l.progress.collect { seen += it } }
        var name: String? = null
        scope.launch { name = l.clusterName(listOf("b"), places, "k") }
        settle()
        assertEquals("B", name)
        assertTrue(Progress(1, 2) in seen) // b landed while a was still out: one burst of two
        src.gates["a"]!!.complete(Unit); settle()
        assertEquals(setOf("a", "b"), store.rows.keys)
        assertNull(l.progress.value)
        job.cancel()
        // b is cached now: asking for it again costs nothing.
        l.need(listOf("b"), places); settle()
        assertEquals(listOf("a", "b"), src.asked)
    }
}
