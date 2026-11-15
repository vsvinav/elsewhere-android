package com.elsewhere.app.ui.tabs.replay

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.H
import com.elsewhere.app.data.Counters
import com.elsewhere.app.data.MapsSettings
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {
    private class Source(rec: Record) : RecordSource { override val record: StateFlow<Record?> = MutableStateFlow(rec) }
    private object Maps : MapsSettings {
        override val settings: Flow<Settings> = flowOf(Settings())
        override val counters: Flow<Counters> = flowOf(Counters())
        override suspend fun saveSettings(s: Settings) {}
    }

    private val dispatcher = StandardTestDispatcher()
    private val rec = Fx.record(range = 0L..(48 * H))

    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()

    private fun vm() = PlaybackViewModel(RangeStore(Source(rec), CoroutineScope(dispatcher)), Maps).also { dispatcher.scheduler.runCurrent() }

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
}
