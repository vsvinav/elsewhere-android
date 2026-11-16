package com.elsewhere.app.crew

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test fun `the kx binding signature and the fingerprint match byte for byte`() {
        for ((k, j) in listOf(a to aj, b to bj)) {
            val sig = j["kxSig"]!!.jsonPrimitive.content
            assertEquals(sig, Crypto.kxSig(k.signPriv, k.kxPub))
            assertTrue(Crypto.verifyKxSig(k.signPub, k.kxPub, sig))
            assertEquals(j["fingerprint"]!!.jsonPrimitive.content, Crypto.fingerprint(k.signPub, k.kxPub))
        }
        assertFalse(Crypto.verifyKxSig(a.signPub, b.kxPub, aj["kxSig"]!!.jsonPrimitive.content))
    }

    @Test fun `the pair record on the wire is hex keys plus the base64 kx signature`() {
        val sent = Json.parseToJsonElement(RelayClient.encode(PairRecord(Crypto.hex(a.signPub), Crypto.hex(a.kxPub), "A", Crypto.kxSig(a.signPriv, a.kxPub)))).jsonObject
        val expected = JsonObject(mapOf("signPub" to aj["signPub"]!!, "kxPub" to aj["kxPub"]!!, "name" to JsonPrimitive("A"), "kxSig" to aj["kxSig"]!!))
        assertEquals(expected, sent)
        val theirs = RelayClient.decode("""{"signPub":"${bj["signPub"]!!.jsonPrimitive.content}","kxPub":"${bj["kxPub"]!!.jsonPrimitive.content}","name":"B","kxSig":"${bj["kxSig"]!!.jsonPrimitive.content}"}""")
        assertTrue(theirs.keysBound)
        assertEquals(bj["fingerprint"]!!.jsonPrimitive.content, Pending("PT-AAA-0000", theirs).fingerprint)
        assertFalse(RelayClient.decode(RelayClient.encode(theirs.copy(kxPub = aj["kxPub"]!!.jsonPrimitive.content))).keysBound)
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
