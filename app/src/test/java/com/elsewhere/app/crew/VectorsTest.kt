package com.elsewhere.app.crew

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tink against noble: every derived value in the shared vectors must come out byte for byte. */
class VectorsTest {
    private val v = Json.parseToJsonElement(javaClass.getResource("/sonar-vectors.json")!!.readText()).jsonObject
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun device(k: String) = v[k]!!.jsonObject.let { d -> DeviceKeys.fromSeeds(hex(d["signSeed"]!!.jsonPrimitive.content), hex(d["kxSeed"]!!.jsonPrimitive.content)) to d }
    private val aj = v["a"]!!.jsonObject
    private val bj = v["b"]!!.jsonObject
    private val a = device("a").first
    private val b = device("b").first
    private val pairId = v["pairId"]!!.jsonPrimitive.content
    private val pairKey = hex(v["pairKey"]!!.jsonPrimitive.content)

    @Test fun `public keys and device ids`() {
        for ((k, j) in listOf(a to aj, b to bj)) {
            assertEquals(j["signPub"]!!.jsonPrimitive.content, k.signPub.hex())
            assertEquals(j["kxPub"]!!.jsonPrimitive.content, k.kxPub.hex())
            assertEquals(j["signPubSha256"]!!.jsonPrimitive.content, Crypto.sha256(k.signPub).hex())
            assertEquals(j["deviceId"]!!.jsonPrimitive.content, k.deviceId)
        }
    }

    @Test fun `pair id and pair key from either side`() {
        assertEquals(pairId, Crypto.pairId(a.signPub, b.signPub))
        assertEquals(pairId, Crypto.pairId(b.signPub, a.signPub))
        assertArrayEquals(pairKey, Crypto.pairKey(a.kxPriv, b.kxPub, pairId))
        assertArrayEquals(pairKey, Crypto.pairKey(b.kxPriv, a.kxPub, pairId))
    }

    @Test fun `the envelope seals identically and opens`() {
        val e = v["envelope"]!!.jsonObject
        val plaintext = e["plaintext"]!!.jsonPrimitive.content
        val expected = Envelope.decode(e["envelope"]!!.jsonObject.toString())
        val sealed = Crypto.seal(pairKey, pairId, plaintext, a.deviceId, hex(e["nonceHex"]!!.jsonPrimitive.content))
        assertEquals(expected, sealed)
        assertEquals(plaintext, Crypto.open(pairKey, pairId, expected))
        assertEquals(plaintext, Ping.decode(plaintext).encode(), "our Ping encodes to the shared plaintext")
    }

    @Test fun `the signed request matches`() {
        val r = v["signedRequest"]!!.jsonObject
        val method = r["method"]!!.jsonPrimitive.content; val path = r["path"]!!.jsonPrimitive.content
        val ts = r["ts"]!!.jsonPrimitive.content.toLong(); val body = r["body"]!!.jsonPrimitive.content.toByteArray()
        assertEquals(r["message"]!!.jsonPrimitive.content, String(Crypto.requestMessage(method, path, ts, body)))
        assertEquals(r["signature"]!!.jsonPrimitive.content, Crypto.signRequest(a.signPriv, method, path, ts, body))
        assertTrue(Crypto.verifyRequest(a.signPub, method, path, ts, body, r["signature"]!!.jsonPrimitive.content))
        val h = r["headers"]!!.jsonObject
        assertEquals(a.deviceId, h["X-Elsewhere-Id"]!!.jsonPrimitive.content)
    }
}
