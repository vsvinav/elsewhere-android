package com.elsewhere.app.ui.gate

import com.elsewhere.app.Fakes
import com.elsewhere.app.data.Settings
import com.elsewhere.app.ingest.KeyReport
import com.elsewhere.app.ingest.KeyVerifier
import com.elsewhere.app.ingest.Probe
import com.elsewhere.app.ingest.keyGated
import com.elsewhere.app.ui.onboard.KeyPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class KeyGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()
    private fun run() = dispatcher.scheduler.runCurrent()

    private val refused = KeyReport(Probe.Failed("API key not valid.", "INVALID_ARGUMENT", "The key was typed wrong or deleted."), Probe.Ok)

    @Test fun `only a stored key that is unproven and not waved through gates the app`() {
        assertFalse(keyGated(Settings()))
        assertTrue(keyGated(Settings(mapsKey = "k")))
        assertTrue(keyGated(Settings(mapsKey = "k", keyPlaces = true, keyTiles = false, keyCheckedAt = 5)))
        assertFalse(keyGated(Settings(mapsKey = "k", keyPlaces = true, keyTiles = true)))
        assertFalse(keyGated(Settings(mapsKey = "k", limitedAccepted = true)))
    }

    @Test fun `an older install's unchecked key is checked once on arrival and the gate lifts on acceptance`() {
        val maps = Fakes.Maps().apply { cur.value = Settings(mapsKey = "AIza-old", keyCheckedAt = 0) }
        val checker = Fakes.Checker()
        val vm = KeyGateViewModel(maps, KeyVerifier(maps, checker))
        run()
        assertEquals(listOf("AIza-old"), checker.asked)
        assertEquals(KeyPhase.Result, vm.state.value.keyPhase)
        assertTrue(maps.cur.value.keyPlaces && maps.cur.value.keyTiles && maps.cur.value.keyCheckedAt > 0)
        assertEquals(false, vm.gated.value)
    }

    @Test fun `a refused key stays gated until limited maps are accepted`() {
        val maps = Fakes.Maps().apply { cur.value = Settings(mapsKey = "AIza-bad") }
        val checker = Fakes.Checker(refused)
        val vm = KeyGateViewModel(maps, KeyVerifier(maps, checker))
        run()
        assertEquals(refused, vm.state.value.keyReport)
        assertEquals(true, vm.gated.value)
        vm.acceptLimited(); run()
        assertEquals(true, vm.gated.value)                       // not without the acknowledgement
        vm.setLimitedAck(true); vm.acceptLimited(); run()
        assertEquals(false, vm.gated.value)
        assertTrue(maps.cur.value.limitedAccepted)
    }

    @Test fun `trying again with a new key loses the old verdict before its probes run`() {
        val maps = Fakes.Maps().apply { cur.value = Settings(mapsKey = "AIza-bad", keyPlaces = true, keyCheckedAt = 9) }
        val checker = Fakes.Checker(refused)
        var seen: Settings? = null
        checker.onRun = { seen = maps.cur.value }
        val vm = KeyGateViewModel(maps, KeyVerifier(maps, checker))
        run()
        checker.report = KeyReport(Probe.Ok, Probe.Ok)
        vm.saveKey("AIza-new", ""); run()
        assertEquals("AIza-new", seen?.mapsKey)
        assertFalse(seen!!.keyPlaces)
        assertEquals(false, vm.gated.value)
    }

    @Test fun `a proven key never asks Google again`() {
        val maps = Fakes.Maps().apply { cur.value = Settings(mapsKey = "k", keyPlaces = true, keyTiles = true, keyCheckedAt = 9) }
        val checker = Fakes.Checker()
        KeyGateViewModel(maps, KeyVerifier(maps, checker)); run()
        assertTrue(checker.asked.isEmpty())
    }
}
