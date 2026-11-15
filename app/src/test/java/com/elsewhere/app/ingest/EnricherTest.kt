package com.elsewhere.app.ingest

import com.elsewhere.app.Fx
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Role
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/** Twin of web/src/ingest/enrich.test.ts, plus the selection, cap and backoff rules. */
class EnricherTest {
    private val server = MockWebServer()
    private lateinit var places: GooglePlaces
    private val store = MemoryEnrichmentStore()
    private val meter = MemoryCostMeter()
    private val slept = ArrayList<Long>()

    @BeforeEach fun up() { server.start(); places = GooglePlaces(OkHttpClient(), server.url("/").toString().trimEnd('/')) }
    @AfterEach fun down() = server.shutdown()

    private fun enricher(cap: Double = 10.0) = Enricher(places, store, meter, capDollars = cap, sleep = { slept += it })
    private fun place(n: Int, name: String? = null, role: Role? = null) = Fx.place(nVisits = n, name = name, role = role)
    private fun named(text: String) = MockResponse().setBody("""{"displayName":{"text":"$text"},"formattedAddress":"1 Main St","types":["cafe","point_of_interest"],"primaryType":"cafe","photos":[{"name":"places/x/photos/p1"}]}""")
    private fun always(res: () -> MockResponse) { server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = res() } }

    @Test fun `counts each lookup and ends with null`() = runTest {
        always { named("X") }
        val seen = ArrayList<Progress?>()
        enricher().run(mapOf("a" to place(5), "b" to place(3), "c" to place(1)), "k") { seen += it }
        assertEquals(Progress(0, 2), seen.first())
        assertTrue(Progress(2, 2) in seen)
        assertNull(seen.last())
        assertEquals(setOf("a", "b"), store.rows.keys)
        assertEquals(Enrichment("X", "1 Main St", listOf("cafe", "point_of_interest"), "cafe", "places/x/photos/p1"), store.rows["a"])
        assertEquals(2, meter.counters.value.detailsCalls)
        val req = server.takeRequest()
        assertEquals("k", req.getHeader("X-Goog-Api-Key"))
        assertEquals("displayName,formattedAddress,types,primaryType,photos", req.getHeader("X-Goog-FieldMask"))
    }

    @Test fun `reports nothing without a key`() = runTest {
        val seen = ArrayList<Progress?>()
        enricher().run(mapOf("a" to place(5)), "") { seen += it }
        assertEquals(emptyList<Progress?>(), seen)
        assertEquals(0, server.requestCount)
    }

    @Test fun `wanted places are the busiest unnamed ones, home and cached excluded, capped at the limit`() {
        val ps = mapOf(
            "home" to place(50, role = Role.HOME), "once" to place(1), "cached" to place(9), "named" to place(8, name = "Done").copy(photo = "u"),
            "b" to place(3), "a" to place(5), "noPhoto" to place(4, name = "Half"),
        )
        assertEquals(listOf("a", "noPhoto", "b"), wanted(ps, setOf("cached")))
        assertEquals(listOf("a"), wanted(ps, setOf("cached"), limit = 1))
    }

    @Test fun `the cost cap bounds how many lookups one run may pay for`() = runTest {
        always { named("X") }
        meter.tally(Kind.DETAILS, 587) // $9.979 spent: one lookup left under $10
        enricher(cap = 10.0).run(mapOf("a" to place(5), "b" to place(3)), "k") {}
        assertEquals(1, server.requestCount)
        assertEquals(setOf("a"), store.rows.keys)
    }

    @Test fun `a place Google no longer knows is cached as empty so it is not asked again`() = runTest {
        always { MockResponse().setResponseCode(404).setBody("""{"error":{"message":"not found"}}""") }
        enricher().run(mapOf("a" to place(5)), "k") {}
        assertEquals(Enrichment.NONE, store.rows["a"])
    }

    @Test fun `429 and 5xx back off and retry, then give up`() = runTest {
        val n = AtomicInteger()
        always { if (n.incrementAndGet() <= 2) MockResponse().setResponseCode(429).setBody("""{"error":{"message":"Quota exceeded for quota metric 'Requests' of service 'places.googleapis.com'"}}""") else named("Late") }
        enricher().run(mapOf("a" to place(5)), "k") {}
        assertEquals(listOf(1000L, 2000L), slept)
        assertEquals("Late", store.rows["a"]!!.name)

        slept.clear(); n.set(0)
        always { MockResponse().setResponseCode(503).setBody("oops") }
        val e = assertThrows(PlacesError::class.java) { kotlinx.coroutines.runBlocking { enricher().run(mapOf("b" to place(5)), "k") {} } }
        assertEquals("Places API 503", e.message)
        assertEquals(listOf(1000L, 2000L, 4000L), slept)
    }

    @Test fun `a refusal stops the run with Google's message, trimmed`() = runTest {
        always { MockResponse().setResponseCode(403).setBody("""{"error":{"message":"Places API (New) has not been used in project 1 before or it is disabled. Enable it of service places.googleapis.com","status":"PERMISSION_DENIED"}}""") }
        val seen = ArrayList<Progress?>()
        val e = assertThrows(PlacesError::class.java) { kotlinx.coroutines.runBlocking { enricher().run(mapOf("a" to place(5)), "k") { seen += it } } }
        assertEquals("Places API (New) has not been used in project 1 before or it is disabled. Enable it", e.message)
        assertNull(seen.last())
        assertTrue(store.rows.isEmpty())
    }

    @Test fun `properName tells a business from an address Google echoed back`() {
        assertTrue(properName("Savoy Apartments", listOf("lodging", "point_of_interest"), "lodging"))
        assertFalse(properName("2180 Cranston Rd", listOf("street_address"), "street_address"))
        assertFalse(properName("Boston", listOf("locality", "political"), "locality"))
        assertFalse(properName(null, listOf("cafe"), "cafe"))
        assertFalse(properName("Somewhere", listOf("point_of_interest", "establishment"), null))
        assertTrue(properName("Somewhere", listOf("point_of_interest", "cafe"), null))
    }

    @Test fun `clusterName takes a proper name from the cache and pays for at most max lookups`() = runTest {
        val ps = mapOf("x" to place(2), "y" to place(2, name = "Main St").copy(types = listOf("route")), "z" to place(2))
        store.rows["x"] = Enrichment.NONE
        val n = AtomicInteger()
        always { if (n.incrementAndGet() == 1) MockResponse().setBody("""{"displayName":{"text":"12 Elm Rd"},"types":["street_address"]}""") else named("The Flat") }
        assertEquals("The Flat", enricher().clusterName(listOf("x", "y", "z", "w"), ps, "k", max = 3))
        // x cached, y known from the record, z paid (an address), w paid (proper) and found.
        assertEquals(2, server.requestCount)
        assertEquals(2, meter.counters.value.detailsCalls)
        // Everything is now cached: no key needed, nothing paid.
        assertEquals("The Flat", enricher().clusterName(listOf("x", "y", "z", "w"), ps, "", max = 3))
        assertEquals(2, server.requestCount)
        // Nothing proper anywhere: the first plain name wins over null.
        assertEquals("Main St", enricher().clusterName(listOf("x", "y"), ps, "", max = 3))
    }
}
