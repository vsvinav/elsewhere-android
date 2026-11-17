package com.elsewhere.app.ui.tabs.stops

import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Settings
import com.elsewhere.app.ingest.DetailsSource
import com.elsewhere.app.ingest.EnrichState
import com.elsewhere.app.ingest.Enrichment
import com.elsewhere.app.ingest.Geocoder
import com.elsewhere.app.ingest.MemoryCostMeter
import com.elsewhere.app.ingest.MemoryEnrichmentStore
import com.elsewhere.app.ingest.PlaceLookups
import com.elsewhere.app.ui.shell.RangeStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StopsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()
    private fun run() = dispatcher.scheduler.runCurrent()

    private class Streets : Geocoder {
        val asked = ArrayList<Pair<Double, Double>>()
        override suspend fun address(lat: Double, lng: Double): String? { asked += lat to lng; return if (lat == 42.0) "2180 Cranston Rd, University Heights" else null }
    }

    /** Places answers by id once the test opens the gate; ids without an answer come back empty. */
    private class Places : DetailsSource {
        val gate = CompletableDeferred<Unit>()
        val answers = HashMap<String, Enrichment>()
        val asked = ArrayList<String>()
        override suspend fun details(id: String, key: String): Enrichment { asked += id; gate.await(); return answers[id] ?: Enrichment.NONE }
    }

    private val places = mapOf(
        "bare" to Fx.place(city = "Boston", cc = "US", lat = 42.0, lng = -71.0),
        "cityOnly" to Fx.place(city = "Boston", cc = "US", lat = 42.0, lng = -71.0).copy(address = "Boston, MA, USA"),
        "placed" to Fx.place(city = "Boston", cc = "US", lat = 43.0, lng = -71.0, name = "Trident").copy(address = "338 Newbury St, Boston, MA 02115, USA"),
        "nowhere" to Fx.place(city = "Boston", cc = "US", lat = 44.0, lng = -71.0),
        "home" to Fx.place(role = Role.HOME, label = "Home", city = "Boston", cc = "US", lat = 42.0, lng = -71.0),
    )
    private val streets = Streets()
    private val google = Places()
    private val source = Fakes.Source(Fx.record(places = places))
    private val maps = Fakes.Maps()

    private fun vm(): StopsViewModel {
        val scope = CoroutineScope(dispatcher)
        val lookups = PlaceLookups(google, MemoryEnrichmentStore(), MemoryCostMeter(), EnrichState(), scope, maps.settings)
        return StopsViewModel(RangeStore(source, scope), lookups, streets).also { run() }
    }

    /** The repository re-prints the record once a Places row lands. */
    private fun landed(id: String, p: Place) { source.record.value = Fx.record(places = places + (id to p)) }

    @Test fun `without a Places key a tag with no street asks Nominatim once it is on screen`() = runTest(dispatcher) {
        val vm = vm()
        vm.seen("bare"); run()
        assertEquals("2180 Cranston Rd, University Heights", vm.addresses.value["bare"])
        assertEquals(1, streets.asked.size)
    }

    @Test fun `a Places address that is only the city counts as missing`() = runTest(dispatcher) {
        val vm = vm()
        vm.seen("cityOnly"); run()
        assertEquals("2180 Cranston Rd, University Heights", vm.addresses.value["cityOnly"])
    }

    @Test fun `a tag with a Places address never reaches Nominatim`() = runTest(dispatcher) {
        val vm = vm()
        vm.seen("placed"); run()
        assertTrue(streets.asked.isEmpty())
        assertTrue(vm.addresses.value.isEmpty())
    }

    @Test fun `each tag is asked for once and a blank answer is kept`() = runTest(dispatcher) {
        val vm = vm()
        vm.seen("nowhere"); run(); vm.seen("nowhere"); run()
        assertEquals(1, streets.asked.size)
        assertTrue("nowhere" in vm.addresses.value)
        assertNull(vm.addresses.value["nowhere"])
    }

    @Test fun `nothing is asked for until a tag is seen`() = runTest(dispatcher) {
        vm()
        assertTrue(streets.asked.isEmpty())
    }

    @Test fun `with a key Nominatim waits for Places, and stays quiet when Places brings a street`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "k", keyPlaces = true)
        val vm = vm()
        vm.seen("bare"); run()
        assertEquals(listOf("bare"), google.asked)
        assertTrue(streets.asked.isEmpty())
        google.answers["bare"] = Enrichment("Trident", "338 Newbury St, Boston, MA 02115, USA", emptyList(), null, null)
        google.gate.complete(Unit); run()
        landed("bare", places["bare"]!!.copy(name = "Trident", address = "338 Newbury St, Boston, MA 02115, USA")); run()
        assertTrue(streets.asked.isEmpty())
        assertTrue(vm.addresses.value.isEmpty())
    }

    @Test fun `when Places lands without a street Nominatim is asked exactly once`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "k", keyPlaces = true)
        val vm = vm()
        vm.seen("bare"); run()
        assertTrue(streets.asked.isEmpty())
        google.gate.complete(Unit); run()
        assertEquals(1, streets.asked.size)
        assertEquals("2180 Cranston Rd, University Heights", vm.addresses.value["bare"])
        vm.seen("bare"); run()
        assertEquals(1, streets.asked.size)
    }

    @Test fun `home is never looked up on Places so its street comes straight from Nominatim`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "k", keyPlaces = true)
        val vm = vm()
        vm.seen("home"); run()
        assertTrue(google.asked.isEmpty())
        assertEquals("2180 Cranston Rd, University Heights", vm.addresses.value["home"])
    }
}
