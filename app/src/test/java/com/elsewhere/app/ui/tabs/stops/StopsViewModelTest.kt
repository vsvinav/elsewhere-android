package com.elsewhere.app.ui.tabs.stops

import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.ingest.Geocoder
import com.elsewhere.app.ui.shell.RangeStore
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

    private val places = mapOf(
        "bare" to Fx.place(city = "Boston", cc = "US", lat = 42.0, lng = -71.0),
        "cityOnly" to Fx.place(city = "Boston", cc = "US", lat = 42.0, lng = -71.0).copy(address = "Boston, MA, USA"),
        "placed" to Fx.place(city = "Boston", cc = "US", lat = 43.0, lng = -71.0, name = "Trident").copy(address = "338 Newbury St, Boston, MA 02115, USA"),
        "nowhere" to Fx.place(city = "Boston", cc = "US", lat = 44.0, lng = -71.0),
    )
    private val streets = Streets()
    private fun vm(): StopsViewModel {
        val scope = CoroutineScope(dispatcher)
        return StopsViewModel(RangeStore(Fakes.Source(Fx.record(places = places)), scope), Fakes.lookups(Fakes.Maps(), scope), streets).also { run() }
    }

    @Test fun `a tag without a Places address asks Nominatim once it is on screen`() = runTest(dispatcher) {
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
}
