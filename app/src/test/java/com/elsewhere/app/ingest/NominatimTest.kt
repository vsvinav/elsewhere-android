package com.elsewhere.app.ingest

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Twin of web/src/ingest/nominatim.test.ts, plus the queue, cache and headers the usage policy asks for. */
class NominatimTest {
    private val server = MockWebServer()
    private val slept = ArrayList<Long>()
    private val book = MemoryAddressBook()
    private lateinit var nom: Nominatim

    @BeforeEach fun up() { server.start(); nom = Nominatim(OkHttpClient(), book, server.url("/").toString().trimEnd('/'), sleep = { slept += it }) }
    @AfterEach fun down() = server.shutdown()

    @Test fun `short address keeps the number, road and the area people say`() {
        assertEquals("2180 Cranston Road, University Heights", shortAddress(Addr(houseNumber = "2180", road = "Cranston Road", suburb = "University Heights", city = "Cleveland")))
        assertEquals("Main St, Boston", shortAddress(Addr(road = "Main St", city = "Boston")))
        assertNull(shortAddress(Addr(city = "Boston")))
    }

    @Test fun `one request a second, identified, cached by rounded point`() = runTest {
        server.enqueue(MockResponse().setBody("""{"address":{"house_number":"12","road":"Elm Rd","neighbourhood":"Allston"}}"""))
        server.enqueue(MockResponse().setBody("""{"address":{"city":"Nowhere"}}"""))
        assertEquals("12 Elm Rd, Allston", nom.address(42.361234567, -71.06))
        assertNull(nom.address(10.0, 10.0))
        assertEquals(listOf(1100L, 1100L), slept)
        val req = server.takeRequest()
        assertEquals("/reverse?format=jsonv2&zoom=18&lat=42.361234567&lon=-71.06", req.path)
        assertEquals("Elsewhere/0.1 (https://github.com/vsvinav/elsewhere)", req.getHeader("User-Agent"))
        assertEquals("en", req.getHeader("Accept-Language"))
        // Same point to five decimals: answered from the book, no request.
        assertEquals("12 Elm Rd, Allston", nom.address(42.361231, -71.06))
        assertEquals(2, server.requestCount)
        assertEquals(mapOf("42.36123,-71.06000" to "12 Elm Rd, Allston", "10.00000,10.00000" to null), book.saved)
    }

    @Test fun `a failure is null, remembered, and does not throw`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertNull(nom.address(1.0, 1.0))
        assertNull(nom.address(1.0, 1.0))
        assertEquals(1, server.requestCount)
    }
}
