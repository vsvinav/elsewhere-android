package com.elsewhere.app.ui.onboard

import androidx.lifecycle.viewModelScope
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Recording
import com.elsewhere.app.record.RecordStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaitingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()

    @Test fun `the fix count keeps up while the door stays open`() = runTest(dispatcher) {
        val control = Fakes.Control(Recording(true, 1_800_000_000L, 0L)).apply { stats = RecordStats(3, 0, null) }
        val vm = WaitingViewModel(Fakes.Store(Fx.record()), control)
        dispatcher.scheduler.runCurrent()
        assertEquals(3, vm.state.value.fixesToday)
        control.stats = RecordStats(7, 0, null)
        advanceTimeBy(61_000)
        assertEquals(7, vm.state.value.fixesToday)
        vm.viewModelScope.cancel()
    }
}
