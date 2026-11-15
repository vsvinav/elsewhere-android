package com.elsewhere.app.ui.record

import com.elsewhere.app.data.Recording
import com.elsewhere.app.record.PermissionGate
import com.elsewhere.app.record.RecordControl
import com.elsewhere.app.record.RecordStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {
    private class Gate(override val sdk: Int = 34, var battery: Boolean = true) : PermissionGate {
        val granted = HashSet<String>()
        override fun granted(permission: String) = permission in granted
        override fun ignoringBatteryOptimizations() = battery
    }

    private class Control : RecordControl {
        override val recording = MutableStateFlow(Recording())
        var starts = 0; var stops = 0; var pauses = 0
        var stats = RecordStats(0, 0, null)
        override suspend fun start() { starts++; recording.value = Recording(true, 1_800_000_000L, 0L) }
        override suspend fun stop() { stops++; recording.value = Recording() }
        override suspend fun pause(seconds: Long) { pauses++; recording.value = recording.value.copy(pausedUntil = 1_800_000_000L + seconds) }
        override suspend fun stats() = stats
    }

    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()
    private fun run() = dispatcher.scheduler.runCurrent()

    private fun vm(gate: Gate, control: Control = Control()) = RecordViewModel(gate, control, now = { 1_800_000_000L }).also { run() }

    @Test fun `before fine location is granted the disclosure is shown and nothing else can be asked`() {
        val ui = vm(Gate()).ui.value
        assertEquals(Phase.DISCLOSURE, ui.phase)
        assertEquals(PermState.ASK, ui.rows.single { it.perm == Perm.FINE }.state)
        assertEquals(PermState.LOCKED, ui.rows.single { it.perm == Perm.BACKGROUND }.state)
        assertFalse(ui.canStart)
    }

    @Test fun `background location opens settings on API 30+ and start needs it`() {
        val gate = Gate(sdk = 34)
        gate.granted += Perm.FINE.permission
        val v = vm(gate)
        var ui = v.ui.value
        assertEquals(Phase.PERMISSIONS, ui.phase)
        assertEquals(PermState.SETTINGS, ui.rows.single { it.perm == Perm.BACKGROUND }.state)
        assertEquals(PermState.ASK, ui.rows.single { it.perm == Perm.ACTIVITY }.state)
        assertEquals(PermState.ASK, ui.rows.single { it.perm == Perm.NOTIFY }.state)
        assertFalse(ui.canStart)

        gate.granted += Perm.BACKGROUND.permission
        v.refresh(); run()
        ui = v.ui.value
        assertTrue(ui.canStart)
        assertEquals(PermState.GRANTED, ui.rows.single { it.perm == Perm.BACKGROUND }.state)
    }

    @Test fun `permissions the platform does not have are not listed`() {
        val gate = Gate(sdk = 30)
        gate.granted += Perm.FINE.permission
        val ui = vm(gate).ui.value
        assertFalse(ui.rows.any { it.perm == Perm.NOTIFY })
        assertEquals(PermState.ASK, ui.rows.single { it.perm == Perm.ACTIVITY }.state)
    }

    @Test fun `a refusal turns Allow into Open settings`() {
        val gate = Gate()
        gate.granted += Perm.FINE.permission
        val v = vm(gate)
        v.onResult(Perm.ACTIVITY, granted = false); run()
        assertEquals(PermState.SETTINGS, v.ui.value.rows.single { it.perm == Perm.ACTIVITY }.state)
    }

    @Test fun `start, stats, pause and stop`() {
        val gate = Gate(battery = false)
        gate.granted += listOf(Perm.FINE.permission, Perm.BACKGROUND.permission)
        val control = Control().apply { stats = RecordStats(120, 4, 1_800_000_000L - 180) }
        val v = vm(gate, control)
        v.start(); run()
        var ui = v.ui.value
        assertEquals(1, control.starts)
        assertEquals(Phase.RECORDING, ui.phase)
        assertEquals("Recording since JAN 15, 2027 · 120 fixes · 4 stays · last fix 3 min ago", ui.statusLine)
        assertFalse(ui.batteryOk)

        v.pause(); run()
        ui = v.ui.value
        assertEquals(1, control.pauses)
        assertEquals(Phase.PAUSED, ui.phase)

        v.stop(); run()
        assertEquals(1, control.stops)
        assertEquals(Phase.PERMISSIONS, v.ui.value.phase)
    }
}
