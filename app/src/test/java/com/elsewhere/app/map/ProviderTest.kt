package com.elsewhere.app.map

import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderTest {
    @Test fun `auto with a key is Google`() = assertEquals(Provider.GOOGLE, resolve(Settings(mapsKey = "AIza")))
    @Test fun `auto without a key is OSM`() = assertEquals(Provider.OSM, resolve(Settings(mapsKey = "  ")))
    @Test fun `auto with a refused key is OSM`() = assertEquals(Provider.OSM, resolve(Settings(mapsKey = "AIza", googleRejected = true)))
    @Test fun `forced Google is honoured even after a refusal`() =
        assertEquals(Provider.GOOGLE, resolve(Settings(mapsKey = "AIza", mapProvider = MapProvider.GOOGLE, googleRejected = true)))
    @Test fun `forced Google with no key still falls back to OSM`() =
        assertEquals(Provider.OSM, resolve(Settings(mapProvider = MapProvider.GOOGLE)))
    @Test fun `forced OSM ignores the key`() = assertEquals(Provider.OSM, resolve(Settings(mapsKey = "AIza", mapProvider = MapProvider.OSM)))
}
