package com.elsewhere.app.map

import com.elsewhere.app.Fakes
import com.elsewhere.app.data.Settings
import com.elsewhere.app.data.TileService
import com.elsewhere.app.data.TileSession
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.ingest.MemoryCostMeter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BasemapsTest {
    private val dispatcher = StandardTestDispatcher()

    class Tiles : TileService {
        var sessions = 0
        override suspend fun session(key: String): TileSession { sessions++; return TileSession("tok-$key", Long.MAX_VALUE) }
        override suspend fun copyright(s: TileSession, key: String, vp: Viewport): String? = "Map data ©2024 Google"
    }

    private val maps = Fakes.Maps()
    private val tiles = Tiles()
    private val meter = MemoryCostMeter()

    @Test fun `on a build with the Maps SDK the base map is the SDK with the user's map id, and no tile session or map load is spent`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "AIza", mapId = "poster-1", keyTiles = true)
        val b = Basemaps(CoroutineScope(dispatcher), maps, tiles, meter, sdk = true)
        dispatcher.scheduler.runCurrent()
        assertEquals(Basemap.Sdk("poster-1"), b.basemap.value)
        assertEquals(0, tiles.sessions)
        assertEquals(0, meter.counters.value.mapLoads)
        b.idle(Viewport(3, 1.0, 0.0, 1.0, 0.0)); advanceTimeBy(1100)
        assertNull(b.copyright.value)
    }

    @Test fun `without the SDK the same settings open a tile session as before`() = runTest(dispatcher) {
        maps.cur.value = Settings(mapsKey = "AIza", keyTiles = true)
        val b = Basemaps(CoroutineScope(dispatcher), maps, tiles, meter, sdk = false)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, tiles.sessions)
        assert(b.basemap.value is Basemap.Google)
    }
}
