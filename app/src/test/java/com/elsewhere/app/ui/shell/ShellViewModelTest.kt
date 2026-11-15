package com.elsewhere.app.ui.shell

import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class ShellViewModelTest {
    /** Counts refreshes; each returns only when the test says the rebuild has landed, as the repository's does. */
    private class Source(rec: Record) : RecordSource {
        override val record: StateFlow<Record?> = MutableStateFlow(rec)
        private val gate = CompletableDeferred<Unit>()
        var refreshes = 0
        override suspend fun refresh() { refreshes++; gate.await() }
        fun land() { gate.complete(Unit) }
    }

    private val dispatcher = StandardTestDispatcher()
    private val rec = Fx.record(range = Fx.utc(2024, 0, 1)..Fx.utc(2024, 11, 31))

    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()

    private fun vm(source: Source): ShellViewModel {
        val maps = Fakes.Maps()
        return ShellViewModel(Fakes.Store(rec), source, maps, Fakes.lookups(maps, CoroutineScope(dispatcher)))
    }

    @Test fun `refresh spins until the rebuilt record lands`() = runTest(dispatcher) {
        val source = Source(rec)
        val vm = vm(source)
        assertFalse(vm.refreshing.value)
        vm.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, source.refreshes)
        assertTrue(vm.refreshing.value)
        source.land()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.refreshing.value)
    }

    @Test fun `refresh gives up after fifteen seconds if nothing comes back`() = runTest(dispatcher) {
        val vm = vm(Source(rec))
        vm.refresh()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.refreshing.value)
        advanceTimeBy(14_000)
        assertTrue(vm.refreshing.value)
        advanceTimeBy(1_001)
        assertFalse(vm.refreshing.value)
    }

    @Test fun `a second pull while one is running is ignored`() = runTest(dispatcher) {
        val source = Source(rec)
        val vm = vm(source)
        vm.refresh(); vm.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, source.refreshes)
    }
}
