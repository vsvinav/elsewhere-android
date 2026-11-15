package com.elsewhere.app.ui.shell

import com.elsewhere.app.Fx
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RangeViewModelTest {
    private class Fake(initial: Record?) : RecordSource {
        override val record: StateFlow<Record?> = MutableStateFlow(initial)
        override suspend fun refresh() {}
    }

    private val dispatcher = StandardTestDispatcher()
    // 1 Mar 2024 00:00 → 13 Nov 2026 15:00 UTC
    private val start = Fx.utc(2024, 2, 1)
    private val end = Fx.utc(2026, 10, 13) + 15 * Fx.H
    private val rec = Fx.record(range = start..end)

    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()

    private fun vm() = RangeViewModel(RangeStore(Fake(rec), CoroutineScope(dispatcher))).also { dispatcher.scheduler.runCurrent() }

    @Test fun `opens on the whole record`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(start..end, vm.range.value)
        assertTrue(vm.isPreset(Preset.All))
        assertEquals(listOf(2024, 2025, 2026), vm.years.value)
    }

    @Test fun `last 90 days runs from midnight 89 days before the end`() = runTest(dispatcher) {
        val vm = vm()
        vm.preset(Preset.Last90)
        assertEquals((Fx.utc(2026, 10, 13) - 89 * Fx.D)..end, vm.range.value)
        assertTrue(vm.isPreset(Preset.Last90))
        assertFalse(vm.isPreset(Preset.All))
    }

    @Test fun `presets clamp to the record`() = runTest(dispatcher) {
        val vm = vm()
        vm.preset(Preset.Year(2024))
        assertEquals(start..(Fx.utc(2025, 0, 1) - 1), vm.range.value)
        vm.preset(Preset.Year(2026))
        assertEquals(Fx.utc(2026, 0, 1)..end, vm.range.value)
    }

    @Test fun `from is held at the to day and to covers its whole day`() = runTest(dispatcher) {
        val vm = vm()
        vm.setTo(LocalDate.of(2025, 5, 12))
        assertEquals(start..(Fx.utc(2025, 4, 13) - 1), vm.range.value)
        vm.setFrom(LocalDate.of(2025, 5, 20))
        assertEquals(Fx.utc(2025, 4, 12), vm.range.value.first)
        vm.setFrom(LocalDate.of(2020, 1, 1))
        assertEquals(start, vm.range.value.first)
        vm.setTo(LocalDate.of(2030, 1, 1))
        assertEquals(end, vm.range.value.last)
    }

    @Test fun `the filtered record is cut to the window`() = runTest(dispatcher) {
        val a = Fx.visit(start + Fx.D, start + 2 * Fx.D, "a"); val b = Fx.visit(end - 2 * Fx.H, end - Fx.H, "b")
        val src = Fake(Fx.record(visits = listOf(a, b), range = start..end))
        val vm = RangeViewModel(RangeStore(src, CoroutineScope(dispatcher))).also { dispatcher.scheduler.runCurrent() }
        assertEquals(2, vm.filtered.value!!.visits.size)
        vm.preset(Preset.Last90); dispatcher.scheduler.runCurrent()
        assertEquals(listOf(b), vm.filtered.value!!.visits)
    }
}
