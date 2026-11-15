package com.elsewhere.app.ui.tabs.map

import com.elsewhere.app.Fakes
import com.elsewhere.app.data.Settings
import com.elsewhere.app.data.TileService
import com.elsewhere.app.data.TileSession
import com.elsewhere.app.data.TilesError
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.ingest.Kind
import com.elsewhere.app.ingest.MemoryCostMeter
import com.elsewhere.app.map.Basemap
import com.elsewhere.app.ui.nav.DoorNav
import com.elsewhere.app.ui.shell.RangeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeEach fun up() = Dispatchers.setMain(dispatcher)
    @AfterEach fun down() = Dispatchers.resetMain()
    private fun run() = dispatcher.scheduler.runCurrent()

    class Tiles(var fail: String? = null) : TileService {
        var sessions = 0
        val viewports = ArrayList<Viewport>()
        override suspend fun session(key: String): TileSession { sessions++; fail?.let { throw TilesError(it) }; return TileSession("tok-$key", Long.MAX_VALUE) }
        override suspend fun copyright(s: TileSession, key: String, vp: Viewport): String? { viewports += vp; return "Map data ©2024 Google (${vp.zoom})" }
    }

    private val maps = Fakes.Maps()
    private val tiles = Tiles()
    private val meter = MemoryCostMeter()
    private val range = RangeStore(Fakes.Source(null), CoroutineScope(dispatcher))
    private fun vm() = MapViewModel(range, maps, tiles, meter, Fakes.lookups(maps, CoroutineScope(dispatcher)), DoorNav(range, CoroutineScope(dispatcher))).also { run() }

    @Test fun `without a working key the base map is free and nothing is spent`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(Basemap.Free, vm.basemap.value)
        assertEquals(0, tiles.sessions)
        assertEquals(0, meter.counters.value.mapLoads)
    }

    @Test fun `with a key Map Tiles accepted, a session is opened once and one map load is counted`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "AIza", keyTiles = true)
        val vm = vm()
        val g = vm.basemap.value as Basemap.Google
        assertTrue(g.styleJson.contains("session=tok-AIza&key=AIza"), g.styleJson)
        assertNull(vm.copyright.value)
        assertEquals(1, tiles.sessions)
        assertEquals(1, meter.counters.value.mapLoads)
        vm.idle(Viewport(12, 1.0, 0.0, 1.0, 0.0)); run()
        assertEquals(1, meter.counters.value.mapLoads) // per map session, not per tile or move
    }

    @Test fun `the copyright follows the settled viewport, debounced a second`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "AIza", keyTiles = true)
        val vm = vm()
        vm.idle(Viewport(5, 1.0, 0.0, 1.0, 0.0)); advanceTimeBy(300)
        vm.idle(Viewport(6, 1.0, 0.0, 1.0, 0.0)); advanceTimeBy(1100)
        assertEquals(listOf(6), tiles.viewports.map { it.zoom })
        val before = vm.basemap.value
        assertEquals("Map data ©2024 Google (6)", vm.copyright.value)
        assertEquals(before, vm.basemap.value) // attribution never touches the style
        assertEquals(1, meter.counters.value.mapLoads)
    }

    @Test fun `a session Google refuses falls back to the free map with the message`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "AIza", keyTiles = true)
        tiles.fail = "Map Tiles API has not been used in project 1 before or it is disabled."
        val vm = vm()
        assertEquals(Basemap.Free, vm.basemap.value)
        assertEquals(tiles.fail, vm.tilesError.value)
        assertEquals(0, meter.counters.value.mapLoads)
    }

    @Test fun `a new key reopens the session`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "AIza", keyTiles = true)
        val vm = vm()
        maps.cur.value = Settings(mapsKey = "AIzb", keyTiles = true); run()
        assertTrue((vm.basemap.value as Basemap.Google).styleJson.contains("tok-AIzb"))
        assertEquals(2, tiles.sessions)
    }
}
