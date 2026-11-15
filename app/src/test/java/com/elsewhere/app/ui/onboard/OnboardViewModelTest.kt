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
            places = mapOf("boston" to Fx.place(city = "Boston", cc = "US", lat = 42.36, lng = -71.06, name = "The flat"), "austin" to Fx.place(city = "Austin", cc = "US", lat = 30.27, lng = -97.74)),
        )
    }

    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()

    private fun run() = dispatcher.scheduler.runCurrent()

    private fun imported(): Triple<OnboardViewModel, Fakes.Store, Fakes.Maps> {
        val store = Fakes.Store(record()); val maps = Fakes.Maps()
        val vm = OnboardViewModel(store, maps)
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
        vm.skipKey(); assertEquals(Step.Name, vm.state.value.step)
        vm.next(); assertEquals(Step.Home, vm.state.value.step)
        vm.back(); assertEquals(Step.Name, vm.state.value.step)
    }

    @Test fun `a saved key is remembered and skips straight to the name`() = runTest(dispatcher) {
        val (vm, _, maps) = imported()
        vm.saveKey(" AIza-key ", ""); run()
        assertEquals("AIza-key", maps.cur.value.mapsKey)
        assertEquals(Step.Name, vm.state.value.step)
    }

    @Test fun `a new key clears Google's refusal, the same key keeps it`() = runTest(dispatcher) {
        val (vm, _, maps) = imported()
        maps.cur.value = Settings(mapsKey = "old", googleRejected = true)
        vm.saveKey("old", "map-1"); run()
        assertEquals(true, maps.cur.value.googleRejected)
        vm.saveKey("new", ""); run()
        assertEquals(false, maps.cur.value.googleRejected)
    }

    @Test fun `picking a home and printing saves one open-ended home from the record start`() = runTest(dispatcher) {
        val (vm, store, _) = imported()
        vm.skipKey(); vm.next()
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
        vm.skipKey(); vm.next()
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
        vm.skipKey(); vm.next()
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
