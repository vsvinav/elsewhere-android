package com.elsewhere.app.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class TileSessionTest {
    private val server = MockWebServer()
    private var now = 1_700_000_000_000L
    private lateinit var tiles: GoogleTiles

    @BeforeEach fun up() {
        server.start()
        tiles = GoogleTiles(OkHttpClient.Builder().callTimeout(500, TimeUnit.MILLISECONDS).build(), base = server.url("/").toString().trimEnd('/'), clock = { now })
    }
    @AfterEach fun down() = server.shutdown()

    private fun session(token: String = "tok", expiry: Long = 1_700_100_000L) =
        MockResponse().setBody("""{"session":"$token","expiry":"$expiry","tileWidth":256,"tileHeight":256,"imageFormat":"png"}""")

    @Test fun `createSession is asked once per key and the token is kept until it expires`() = runTest {
        server.enqueue(session()); server.enqueue(session("tok2"))
        val a = tiles.session("AIza")
        assertEquals(TileSession("tok", 1_700_100_000_000L), a)
        assertEquals(a, tiles.session("AIza"))
        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertEquals("POST", req.method); assertEquals("/v1/createSession?key=AIza", req.path)
        assertTrue(req.body.readUtf8().contains("\"mapType\":\"roadmap\""))
        now = 1_700_100_000_000L - 30_000 // inside the renewal margin
        assertEquals("tok2", tiles.session("AIza").token)
        assertEquals(2, server.requestCount)
    }

    @Test fun `a different key gets its own session`() = runTest {
        server.enqueue(session("a")); server.enqueue(session("b"))
        assertEquals("a", tiles.session("k1").token)
        assertEquals("b", tiles.session("k2").token)
    }

    @Test fun `a refusal carries Google's message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":403,"message":"Map Tiles API has not been used in project 1 before or it is disabled.","status":"PERMISSION_DENIED"}}"""))
        val e = assertThrows(TilesError::class.java) { kotlinx.coroutines.runBlocking { tiles.session("AIza") } }
        assertEquals("Map Tiles API has not been used in project 1 before or it is disabled.", e.message)
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        assertEquals("HTTP 500", assertThrows(TilesError::class.java) { kotlinx.coroutines.runBlocking { tiles.session("AIza") } }.message)
    }

    @Test fun `the tile template and style carry the session and key`() {
        val t = tileTemplate(TileSession("tok", 0), "AIza")
        assertEquals("https://tile.googleapis.com/v1/2dtiles/{z}/{x}/{y}?session=tok&key=AIza", t)
        val style = rasterStyle(t)
        assertTrue(style.contains("\"tiles\":[\"$t\"]"), style)
        assertTrue(style.contains("\"tileSize\":256"), style)
        assertTrue(style.contains("\"type\":\"raster\""), style)
    }

    @Test fun `viewport returns the copyright for the visible map, or nothing when it fails`() = runTest {
        server.enqueue(MockResponse().setBody("""{"copyright":"Map data ©2024 Google","maxZoomRects":[]}"""))
        val vp = Viewport(zoom = 12, north = 42.4, south = 42.3, east = -71.0, west = -71.1)
        assertEquals("Map data ©2024 Google", tiles.copyright(TileSession("tok", 0), "AIza", vp))
        val req = server.takeRequest()
        assertEquals("/tile/v1/viewport?session=tok&key=AIza&zoom=12&north=42.4&south=42.3&east=-71.0&west=-71.1", req.path)
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        assertNull(tiles.copyright(TileSession("tok", 0), "AIza", vp))
    }
}
