package com.elsewhere.app.ui.onboard

import com.elsewhere.app.Fakes
import com.elsewhere.app.data.Settings
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.ImportReport
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Units
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
import com.elsewhere.app.ingest.KeyReport
import com.elsewhere.app.ingest.Probe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardViewModelTest {
    private val start = Fx.utc(2024, 0, 1)
    private val end = Fx.utc(2024, 11, 31)

    /** Two sleep places: a flat in Boston (most nights) and a house in Austin. */
    private fun record(): Record {
        val visits = ArrayList<com.elsewhere.app.data.Visit>()
        for (d in 0 until 40) visits += Fx.visit(start + d * Fx.D + 20 * Fx.H, start + (d + 1) * Fx.D + 8 * Fx.H, "boston", lat = 42.36, lng = -71.06)
        for (d in 60 until 75) visits += Fx.visit(start + d * Fx.D + 20 * Fx.H, start + (d + 1) * Fx.D + 8 * Fx.H, "austin", lat = 30.27, lng = -97.74)
        return Fx.record(
            visits = visits, range = start..end,
            places = mapOf("boston" to Fx.place(city = "Boston", cc = "US", lat = 42.36, lng = -71.06, name = "The flat", types = listOf("lodging")), "austin" to Fx.place(city = "Austin", cc = "US", lat = 30.27, lng = -97.74)),
        )
    }

    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()

    private fun run() = dispatcher.scheduler.runCurrent()

    private val checker = Fakes.Checker()
    private val namer = Fakes.Namer(mapOf("austin" to "Barton Hills Bungalow"))
    private val geo = Fakes.Geo(mapOf((30.27 to -97.74) to "Mary St, Bouldin Creek"))

    private fun imported(): Triple<OnboardViewModel, Fakes.Store, Fakes.Maps> {
        val store = Fakes.Store(record()); val maps = Fakes.Maps()
        val vm = OnboardViewModel(store, maps, checker, namer, geo)
        vm.start(Step.Import); run()
        vm.import({ ByteArrayInputStream("{}".toByteArray()) }, "Timeline.json"); run()
        return Triple(vm, store, maps)
    }

    @Test fun `import lands on the key step, then the questions`() = runTest(dispatcher) {
        val (vm, _, _) = imported()
        val s = vm.state.value
        assertEquals(Step.Key, s.step)
        assertEquals(ImportReport(100, 98, mapOf("bad_json" to 2)), s.report)
        assertNull(s.importing)
        assertEquals(listOf("The flat", "Austin"), s.sleep.map { it.label })
        assertEquals(Sure.LIKELY, s.sleep[0].sure)
        vm.skipKey(); vm.setLimitedAck(true); vm.acceptLimited(); run(); assertEquals(Step.Name, vm.state.value.step)
        vm.next(); assertEquals(Step.Home, vm.state.value.step)
        vm.back(); assertEquals(Step.Name, vm.state.value.step)
    }

    @Test fun `with a working key, unnamed candidates are named through Places`() = runTest(dispatcher) {
        val store = Fakes.Store(record()); val maps = Fakes.Maps()
        maps.cur.value = Settings(mapsKey = "k", keyPlaces = true, keyTiles = true)
        store.hasImport.value = true
        val vm = OnboardViewModel(store, maps, checker, namer, geo)
        vm.start(Step.Name); run()
        assertEquals(listOf("The flat", "Barton Hills Bungalow"), vm.state.value.sleep.map { it.label })
        assertEquals(listOf(listOf("austin")), namer.asked) // the flat already has a proper name
        assertTrue(geo.asked.isEmpty())
    }

    @Test fun `without a key, unnamed candidates get a street from Nominatim`() = runTest(dispatcher) {
        val (vm, _, _) = imported()
        val austin = vm.state.value.sleep[1]
        assertEquals("Austin", austin.label)
        assertEquals("Mary St, Bouldin Creek", austin.address)
        assertTrue(namer.asked.isEmpty())
        assertEquals(listOf(30.27 to -97.74), geo.asked)
    }

    @Test fun `a saved key is checked, remembered and continues to the name`() = runTest(dispatcher) {
        val (vm, _, maps) = imported()
        assertEquals(KeyPhase.Entry, vm.state.value.keyPhase)
        vm.saveKey(" AIza-key ", "")
        assertEquals(KeyPhase.Checking, vm.state.value.keyPhase)
        run()
        assertEquals("AIza-key", maps.cur.value.mapsKey)
        assertEquals(listOf("AIza-key"), checker.asked)
        assertEquals(KeyPhase.Result, vm.state.value.keyPhase)
        assertEquals(KeyReport(Probe.Ok, Probe.Ok), vm.state.value.keyReport)
        assertEquals(Step.Key, vm.state.value.step)
        assertEquals(true, maps.cur.value.keyPlaces); assertEquals(true, maps.cur.value.keyTiles)
        assertTrue(maps.cur.value.keyCheckedAt > 0)
        assertEquals(false, maps.cur.value.limitedAccepted)
        vm.continueKey()
        assertEquals(Step.Name, vm.state.value.step)
    }

    @Test fun `a refused key records what failed and needs the limited-mode acknowledgement`() = runTest(dispatcher) {
        checker.report = KeyReport(Probe.Failed("Requests from referer <empty> are blocked.", "PERMISSION_DENIED", "hint"), Probe.Ok)
        val (vm, _, maps) = imported()
        vm.saveKey("AIza-web", ""); run()
        assertEquals(KeyPhase.Result, vm.state.value.keyPhase)
        assertEquals(false, maps.cur.value.keyPlaces); assertEquals(true, maps.cur.value.keyTiles)
        assertEquals(false, vm.state.value.limitedAck)
        vm.acceptLimited(); run() // nothing happens until the box is ticked
        assertEquals(Step.Key, vm.state.value.step)
        vm.setLimitedAck(true); vm.acceptLimited(); run()
        assertEquals(Step.Name, vm.state.value.step)
        assertEquals(true, maps.cur.value.limitedAccepted)
    }

    @Test fun `try again re-runs the probes with the key in the field`() = runTest(dispatcher) {
        checker.report = KeyReport(Probe.Failed("API key not valid.", "INVALID_ARGUMENT", null), Probe.Failed("API key not valid.", "INVALID_ARGUMENT", null))
        val (vm, _, maps) = imported()
        vm.saveKey("AIzb", ""); run()
        assertEquals(false, vm.state.value.keyReport!!.accepted)
        checker.report = KeyReport(Probe.Ok, Probe.Ok)
        vm.saveKey("AIza", ""); run()
        assertEquals(listOf("AIzb", "AIza"), checker.asked)
        assertEquals(true, vm.state.value.keyReport!!.accepted)
        assertEquals("AIza", maps.cur.value.mapsKey)
    }

    @Test fun `skipping the key lands on the acknowledgement with no report`() = runTest(dispatcher) {
        val (vm, _, maps) = imported()
        vm.skipKey()
        assertEquals(KeyPhase.Result, vm.state.value.keyPhase)
        assertNull(vm.state.value.keyReport)
        assertEquals(Step.Key, vm.state.value.step)
        vm.setLimitedAck(true); vm.acceptLimited(); run()
        assertEquals(Step.Name, vm.state.value.step)
        assertEquals(true, maps.cur.value.limitedAccepted)
    }

    @Test fun `editing the key from settings re-checks and closes the door`() = runTest(dispatcher) {
        val store = Fakes.Store(record()); val maps = Fakes.Maps()
        maps.cur.value = Settings(mapsKey = "old", keyPlaces = true, keyTiles = true, keyCheckedAt = 5)
        val vm = OnboardViewModel(store, maps, checker, namer, geo)
        vm.start(Step.Key); run()
        assertEquals(KeyPhase.Entry, vm.state.value.keyPhase)
        vm.recheckKey(); run()
        assertEquals(listOf("old"), checker.asked)
        assertEquals(KeyPhase.Result, vm.state.value.keyPhase)
        vm.continueKey()
        assertEquals(Step.Done, vm.state.value.step)
    }

    @Test fun `picking a home and printing saves one open-ended home from the record start`() = runTest(dispatcher) {
        val (vm, store, _) = imported()
        vm.skipKey(); vm.setLimitedAck(true); vm.acceptLimited(); run(); vm.next()
        vm.pickHome(vm.state.value.sleep[0])
        assertEquals(Units.MI, vm.state.value.units)
        vm.next(); vm.next(); vm.skip(); vm.setUnits(Units.KM)
        assertEquals(Step.Units, vm.state.value.step)
        vm.setName("  Vinav ")
        vm.finish(); run()
        assertEquals(Step.Done, vm.state.value.step)
        assertEquals(Profile("Vinav", Units.KM, listOf(Home(42.36, -71.06, "Boston", "US", start, null, "The flat")), emptyList()), store.saved)
    }

    @Test fun `adding a move closes the earlier home the month the new one starts`() = runTest(dispatcher) {
        val (vm, store, _) = imported()
        vm.skipKey(); vm.setLimitedAck(true); vm.acceptLimited(); run(); vm.next()
        val (boston, austin) = vm.state.value.sleep
        vm.pickHome(boston); vm.next()
        assertEquals(Step.Moves, vm.state.value.step)
        vm.addHome(austin)
        val m = YearMonth.of(2024, 3).epoch()
        vm.setHomeFrom(1, YearMonth.of(2024, 3))
        vm.finish(); run()
        val homes = store.saved!!.homes
        assertEquals(listOf(start to m, m to null), homes.map { it.from to it.to })
        assertEquals(listOf("Boston", "Austin"), homes.map { it.city })
    }

    @Test fun `a home with an until can be picked again for a later stint`() = runTest(dispatcher) {
        val (vm, _, _) = imported()
        vm.skipKey(); vm.setLimitedAck(true); vm.acceptLimited(); run(); vm.next()
        val (boston, austin) = vm.state.value.sleep
        vm.pickHome(boston); vm.next()
        // The nights shift to Austin in March: that is where the stint starts, and Boston closes there.
        vm.addHome(austin)
        val march = YearMonth.of(2024, 3).epoch()
        assertEquals(listOf(start to march, march to null), vm.state.value.homes.map { it.from to it.to })
        vm.addHome(austin) // still open: nothing happens
        assertEquals(2, vm.state.value.homes.size)
        vm.setHomeTo(1, YearMonth.of(2024, 6))
        vm.addHome(austin)
        assertEquals(3, vm.state.value.homes.size)
        assertEquals(YearMonth.of(2024, 6).epoch(), vm.state.value.homes[2].from)
    }
}
