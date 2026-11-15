package com.elsewhere.app.ui.shell

import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Ready
import com.elsewhere.app.data.ReadyState
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordSource
import kotlinx.coroutines.CompletableDeferred
import com.elsewhere.app.ingest.DetailsSource
import com.elsewhere.app.ingest.EnrichState
import com.elsewhere.app.ingest.Enrichment
import com.elsewhere.app.ingest.MemoryCostMeter
import com.elsewhere.app.ingest.MemoryEnrichmentStore
import com.elsewhere.app.ingest.PlaceLookups
import com.elsewhere.app.ingest.Progress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    private val readyState = ReadyState()
    private val enrich = EnrichState()

    private fun vm(source: Source = Source(rec)): ShellViewModel {
        val maps = Fakes.Maps()
        val lookups = PlaceLookups(object : DetailsSource { override suspend fun details(id: String, key: String) = Enrichment.NONE }, MemoryEnrichmentStore(), MemoryCostMeter(), enrich, CoroutineScope(dispatcher), maps.settings)
        return ShellViewModel(Fakes.Store(rec), source, maps, lookups, readyState)
    }

    @Test fun `a naming burst carries the bar from the names to done, and never shows the old done bar again`() = runTest(dispatcher) {
        readyState.deriving(); readyState.derived()
        val vm = vm(); dispatcher.scheduler.runCurrent()
        val seen = ArrayList<Ready?>()
        val job = launch { vm.ready.collect { seen += it } }
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(Ready("Working out trips", 100)), seen)

        enrich.report(Progress(0, 1)); dispatcher.scheduler.runCurrent()
        enrich.report(Progress(1, 1)); dispatcher.scheduler.runCurrent()
        enrich.report(null); dispatcher.scheduler.runCurrent()
        assertEquals(listOf(Ready("Working out trips", 100), Ready("Naming places 0/1", 0), Ready("Naming places 1/1", 100)), seen)

        // A second burst whose done-of-total the collector never sees: still lands on done, not on the old trips bar.
        enrich.report(Progress(0, 2)); dispatcher.scheduler.runCurrent()
        enrich.report(Progress(2, 2)); enrich.report(null); dispatcher.scheduler.runCurrent()
        assertEquals(listOf(Ready("Naming places 0/2", 0), Ready("Naming places 2/2", 100)), seen.drop(3))
        job.cancel()
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
