package com.elsewhere.app.map

import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderTest {
    private val good = Settings(mapsKey = "AIza", keyTiles = true, keyPlaces = true)

    @Test fun `auto with a key Map Tiles accepted is Google`() = assertEquals(Provider.GOOGLE, resolve(good, sdk = false))
    @Test fun `auto without a key is free`() = assertEquals(Provider.FREE, resolve(Settings(mapsKey = "  "), sdk = false))
    @Test fun `auto with a key Map Tiles refused is free`() = assertEquals(Provider.FREE, resolve(good.copy(keyTiles = false), sdk = false))
    @Test fun `forced Google tiles are honoured even after a refusal`() =
        assertEquals(Provider.GOOGLE, resolve(good.copy(keyTiles = false, mapProvider = MapProvider.GOOGLE_TILES), sdk = false))
    @Test fun `forced Google tiles with no key still fall back to free`() =
        assertEquals(Provider.FREE, resolve(Settings(mapProvider = MapProvider.GOOGLE_TILES), sdk = false))
    @Test fun `forced free ignores the key`() = assertEquals(Provider.FREE, resolve(good.copy(mapProvider = MapProvider.FREE), sdk = false))

    @Test fun `auto on a build with the Maps SDK is the SDK whatever the key says`() {
        assertEquals(Provider.SDK, resolve(good, sdk = true))
        assertEquals(Provider.SDK, resolve(good.copy(keyTiles = false), sdk = true))
        assertEquals(Provider.SDK, resolve(Settings(), sdk = true))
    }
    @Test fun `a forced provider ignores the SDK`() {
        assertEquals(Provider.GOOGLE, resolve(good.copy(mapProvider = MapProvider.GOOGLE_TILES), sdk = true))
        assertEquals(Provider.FREE, resolve(good.copy(mapProvider = MapProvider.FREE), sdk = true))
    }

    @Test fun `stored names from before the rename still read`() {
        assertEquals(MapProvider.GOOGLE_TILES, MapProvider.parse("GOOGLE"))
        assertEquals(MapProvider.FREE, MapProvider.parse("OSM"))
        assertEquals(MapProvider.FREE, MapProvider.parse("FREE"))
        assertEquals(MapProvider.AUTO, MapProvider.parse(null))
        assertEquals(MapProvider.AUTO, MapProvider.parse("garbage"))
    }
}
