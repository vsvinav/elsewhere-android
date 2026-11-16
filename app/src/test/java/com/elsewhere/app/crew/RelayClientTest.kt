package com.elsewhere.app.crew

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RelayClientTest {
    private val server = MockWebServer()
    private val keys = DeviceKeys.fromSeeds(ByteArray(32) { 1 }, ByteArray(32) { 2 })
    private val now = 1_763_222_520_000L
    private lateinit var client: RelayClient
    private val http = OkHttpClient()

    @BeforeEach fun setUp() {
        server.start()
        client = RelayClient(http, { server.url("/").toString() }, { keys }, { now })
    }

    @AfterEach fun tearDown() { runCatching { server.shutdown() } }

    @Test fun `create signs method, path, timestamp and body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val me = PairRecord(Crypto.b64(keys.signPub), Crypto.b64(keys.kxPub), "Vinav")
        client.createCode("PT-MRT-7Q4K", me)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/pair/PT-MRT-7Q4K", req.path)
        assertEquals(keys.deviceId, req.getHeader("X-Elsewhere-Id"))
        assertEquals("1763222520", req.getHeader("X-Elsewhere-Ts"))
        val body = req.body.readByteArray()
        assertEquals("""{"signPub":"${me.signPub}","kxPub":"${me.kxPub}","name":"Vinav"}""", String(body))
        assertTrue(Crypto.verifyRequest(keys.signPub, "POST", "/pair/PT-MRT-7Q4K", 1763222520L, body, req.getHeader("X-Elsewhere-Sig")!!))
    }

    @Test fun `claim returns the owner's record and poll returns the claimer's or nothing`() = runTest {
        server.enqueue(MockResponse().setBody("""{"signPub":"QQ==","kxPub":"Qg==","name":"Marta"}"""))
        assertEquals(PairRecord("QQ==", "Qg==", "Marta"), client.claimCode("PT-MRT-7Q4K", PairRecord("a", "b", "me")))
        assertEquals("/pair/PT-MRT-7Q4K/claim", server.takeRequest().path)
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(client.pollCode("PT-MRT-7Q4K"))
        server.enqueue(MockResponse().setBody("null"))
        assertNull(client.pollCode("PT-MRT-7Q4K"))
        server.enqueue(MockResponse().setBody("""{"signPub":"QQ==","kxPub":"Qg==","name":"Marta"}"""))
        assertEquals("Marta", client.pollCode("PT-MRT-7Q4K")!!.name)
        val get = server.takeRequest(); server.takeRequest()
        assertEquals("GET", get.method)
        assertTrue(Crypto.verifyRequest(keys.signPub, "GET", "/pair/PT-MRT-7Q4K", 1763222520L, ByteArray(0), get.getHeader("X-Elsewhere-Sig")!!))
    }

    @Test fun `errors surface with their status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("slow down"))
        val e = runCatching { client.createCode("PT-MRT-7Q4K", PairRecord("a", "b", "c")) }.exceptionOrNull()
        assertEquals(429, (e as RelayException).code)
    }

    @Test fun `an empty relay url refuses before touching the network`() = runTest {
        val c = RelayClient(OkHttpClient(), { "" }, { keys }, { now })
        assertEquals(false, c.configured())
        assertTrue(runCatching { c.last("PAIR") }.exceptionOrNull() is RelayException)
        assertEquals(0, server.requestCount)
    }

    @Test fun `last parses envelopes`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"v":1,"from":"A","n":"bm9uY2Vub25jZQ==","c":"Y3Q="}]"""))
        assertEquals(listOf(Envelope(from = "A", n = "bm9uY2Vub25jZQ==", c = "Y3Q=")), client.last("PAIR"))
        assertEquals("/room/PAIR/last", server.takeRequest().path)
    }

    @Test fun `the room socket is authenticated through the query`() = runTest {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) { webSocket.send("hello") }
        }))
        val text = client.room("PAIR").first { it is RoomEvent.Text } as RoomEvent.Text
        assertEquals("hello", text.text)
        http.dispatcher.executorService.shutdown()
        val req = server.takeRequest()
        val url = req.requestUrl!!
        assertEquals("/room/PAIR", url.encodedPath)
        assertEquals(keys.deviceId, url.queryParameter("id"))
        assertEquals("1763222520", url.queryParameter("ts"))
        assertTrue(Crypto.verifyRequest(keys.signPub, "GET", "/room/PAIR", 1763222520L, ByteArray(0), url.queryParameter("sig")!!))
    }
}
