package com.elsewhere.app.ui.tabs.replay

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.H
import com.elsewhere.app.analytics.Grain
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordSource
import com.elsewhere.app.data.Settings
import com.elsewhere.app.ui.shell.RangeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.elsewhere.app.Fakes
import com.elsewhere.app.ingest.MemoryCostMeter
import com.elsewhere.app.map.Basemap
import com.elsewhere.app.ui.tabs.map.MapViewModelTest

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {
    private class Source(rec: Record) : RecordSource { override val record: StateFlow<Record?> = MutableStateFlow(rec); override suspend fun refresh() {} }
    private val maps = Fakes.Maps()
    private val tiles = MapViewModelTest.Tiles()

    private val dispatcher = StandardTestDispatcher()
    private val rec = Fx.record(range = 0L..(48 * H))

    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()

    private val range = RangeStore(Source(rec), CoroutineScope(dispatcher))
    private fun vm() = PlaybackViewModel(range, maps, tiles, MemoryCostMeter()).also { dispatcher.scheduler.runCurrent() }

    @Test fun `the first showing plays, a later one leaves a paused, detached replay alone`() = runTest(dispatcher) {
        val vm = vm()
        assertFalse(vm.state.value.playing)
        vm.shown(); assertTrue(vm.state.value.playing)
        vm.setPlaying(false); vm.setFollow(false)
        vm.shown()
        assertFalse(vm.state.value.playing); assertFalse(vm.state.value.follow)
    }

    @Test fun `playing from the end rewinds and the playhead seeds from the record`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(0.0, vm.state.value.playhead)
        vm.scrub(48.0 * H); vm.setPlaying(true)
        assertEquals(0.0, vm.state.value.playhead); assertTrue(vm.state.value.follow)
    }

    @Test fun `the replay draws on Google's tiles once a key has passed the tiles check`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "AIza", keyTiles = true)
        val vm = vm()
        assertTrue(vm.basemap.value is Basemap.Google)
        assertEquals(1, tiles.sessions)
    }

    @Test fun `grain is remembered until the window changes`() = runTest(dispatcher) {
        val vm = vm()
        vm.setGrain(Grain.DAY); assertEquals(Grain.DAY, vm.state.value.grain)
        range.range.value = 0L..(24 * H); dispatcher.scheduler.runCurrent()
        assertNull(vm.state.value.grain)
    }

    @Test fun `scrubbing pauses and is a flag`() = runTest(dispatcher) {
        val vm = vm()
        vm.setPlaying(true); vm.setScrubbing(true)
        assertTrue(vm.state.value.scrubbing); assertFalse(vm.state.value.playing)
        vm.setScrubbing(false); assertFalse(vm.state.value.scrubbing); assertFalse(vm.state.value.playing)
    }
}
