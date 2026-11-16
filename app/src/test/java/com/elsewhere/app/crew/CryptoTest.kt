package com.elsewhere.app.crew

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

class CryptoTest {
    private val a = DeviceKeys.fromSeeds(ByteArray(32) { 1 }, ByteArray(32) { 2 })
    private val b = DeviceKeys.fromSeeds(ByteArray(32) { 3 }, ByteArray(32) { 4 })

    @Test fun `base32 without padding follows RFC 4648`() {
        assertEquals("", Crypto.base32nopad(ByteArray(0)))
        assertEquals("MY", Crypto.base32nopad("f".toByteArray()))
        assertEquals("MZXQ", Crypto.base32nopad("fo".toByteArray()))
        assertEquals("MZXW6", Crypto.base32nopad("foo".toByteArray()))
        assertEquals("MZXW6YQ", Crypto.base32nopad("foob".toByteArray()))
        assertEquals("MZXW6YTB", Crypto.base32nopad("fooba".toByteArray()))
        assertEquals("MZXW6YTBOI", Crypto.base32nopad("foobar".toByteArray()))
    }

    @Test fun `sha256hex of the empty body is the well known digest`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Crypto.sha256hex(ByteArray(0)))
    }

    @Test fun `a device id is sixteen base32 characters of the signing key's hash`() {
        assertEquals(16, a.deviceId.length)
        assertEquals(Crypto.base32nopad(Crypto.sha256(a.signPub)).substring(0, 16), a.deviceId)
        assertNotEquals(a.deviceId, b.deviceId)
        assertEquals(a.deviceId, DeviceKeys.fromSeeds(ByteArray(32) { 1 }, ByteArray(32) { 9 }).deviceId)
    }

    @Test fun `the pair id does not care who is A and who is B`() {
        assertEquals(Crypto.pairId(a.signPub, b.signPub), Crypto.pairId(b.signPub, a.signPub))
        assertEquals(16, Crypto.pairId(a.signPub, b.signPub).length)
    }

    @Test fun `both sides derive the same pair key`() {
        val id = Crypto.pairId(a.signPub, b.signPub)
        val ka = Crypto.pairKey(a.kxPriv, b.kxPub, id)
        val kb = Crypto.pairKey(b.kxPriv, a.kxPub, id)
        assertArrayEquals(ka, kb)
        assertEquals(32, ka.size)
        assertFalse(Crypto.pairKey(a.kxPriv, b.kxPub, "other-pair-id").contentEquals(ka))
    }

    @Test fun `an envelope round-trips and a tampered one is refused`() {
        val id = Crypto.pairId(a.signPub, b.signPub)
        val key = Crypto.pairKey(a.kxPriv, b.kxPub, id)
        val env = Crypto.seal(key, id, """{"type":"ping","t":1}""", a.deviceId)
        assertEquals(1, env.v)
        assertEquals(a.deviceId, env.from)
        assertEquals(12, Crypto.b64decode(env.n).size)
        assertEquals("""{"type":"ping","t":1}""", Crypto.open(key, id, env))
        val tampered = env.copy(c = Crypto.b64(Crypto.b64decode(env.c).also { it[0] = (it[0].toInt() xor 1).toByte() }))
        assertThrows(GeneralSecurityException::class.java) { Crypto.open(key, id, tampered) }
        assertThrows(GeneralSecurityException::class.java) { Crypto.open(key, "other", env) }
        assertThrows(GeneralSecurityException::class.java) { Crypto.open(Crypto.pairKey(a.kxPriv, b.kxPub, "x"), id, env) }
    }

    @Test fun `two seals of the same text differ by nonce alone`() {
        val id = Crypto.pairId(a.signPub, b.signPub)
        val key = Crypto.pairKey(a.kxPriv, b.kxPub, id)
        val e1 = Crypto.seal(key, id, "hi", a.deviceId); val e2 = Crypto.seal(key, id, "hi", a.deviceId)
        assertNotEquals(e1.n, e2.n)
        assertNotEquals(e1.c, e2.c)
    }

    @Test fun `envelope json uses the agreed field names`() {
        val env = Envelope(from = "ABC", n = "bm9uY2Vub25jZQ==", c = "Y3Q=")
        assertEquals("""{"v":1,"from":"ABC","n":"bm9uY2Vub25jZQ==","c":"Y3Q="}""", env.encode())
        assertEquals(env, Envelope.decode(env.encode()))
    }

    @Test fun `a signed request verifies, and not after any field changes`() {
        val sig = Crypto.signRequest(a.signPriv, "GET", "/room/PAIR", 1763222520L, ByteArray(0))
        assertTrue(Crypto.verifyRequest(a.signPub, "GET", "/room/PAIR", 1763222520L, ByteArray(0), sig))
        assertFalse(Crypto.verifyRequest(a.signPub, "POST", "/room/PAIR", 1763222520L, ByteArray(0), sig))
        assertFalse(Crypto.verifyRequest(a.signPub, "GET", "/room/OTHER", 1763222520L, ByteArray(0), sig))
        assertFalse(Crypto.verifyRequest(a.signPub, "GET", "/room/PAIR", 1763222521L, ByteArray(0), sig))
        assertFalse(Crypto.verifyRequest(a.signPub, "GET", "/room/PAIR", 1763222520L, "x".toByteArray(), sig))
        assertFalse(Crypto.verifyRequest(b.signPub, "GET", "/room/PAIR", 1763222520L, ByteArray(0), sig))
    }

    @Test fun `the signing message is method, path, ts and the body hash on separate lines`() {
        assertEquals("GET\n/room/PAIR\n1763222520\n" + Crypto.sha256hex(ByteArray(0)), String(Crypto.requestMessage("GET", "/room/PAIR", 1763222520L, ByteArray(0))))
    }

    @Test fun `keys serialise to base64 and back`() {
        val s = a.encode()
        val back = DeviceKeys.decode(s)
        assertEquals(a, back)
    }

    @Test fun `generated keys are fresh every time`() {
        assertNotEquals(DeviceKeys.generate().deviceId, DeviceKeys.generate().deviceId)
    }

    @Test fun `the kx key is vouched for by the signing key, and not after a swap`() {
        val sig = Crypto.kxSig(a.signPriv, a.kxPub)
        assertEquals(sig, Crypto.kxSig(a.signPriv, a.kxPub), "Ed25519 is deterministic")
        assertTrue(Crypto.verifyKxSig(a.signPub, a.kxPub, sig))
        assertFalse(Crypto.verifyKxSig(a.signPub, b.kxPub, sig), "a substituted kx key")
        assertFalse(Crypto.verifyKxSig(b.signPub, a.kxPub, sig), "someone else's signing key")
        assertFalse(Crypto.verifyKxSig(a.signPub, a.kxPub, null))
        assertFalse(Crypto.verifyKxSig(a.signPub, a.kxPub, "not base64!"))
    }

    @Test fun `the fingerprint covers both keys in Crockford base32`() {
        val fp = Crypto.fingerprint(a.signPub, a.kxPub)
        assertEquals(8, fp.length)
        assertTrue(fp.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" })
        assertNotEquals(fp, Crypto.fingerprint(a.signPub, b.kxPub))
        assertEquals("D1JPRV3F", Crypto.crockford32("hello".toByteArray()))
    }
}
