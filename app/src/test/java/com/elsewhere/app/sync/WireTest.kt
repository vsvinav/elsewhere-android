package com.elsewhere.app.sync

import com.elsewhere.app.account.VaultBody
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.Work
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

/** The bytes that cross the worker, pinned against the web client; the fixed vector is the cross-check. */
class WireTest {
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val nonce = ByteArray(12) { it.toByte() }

    @Test fun `the fixed vector seals to the pinned ciphertext`() {
        val blob = Wire.seal(key, "profile", """{"v":1}""", ts = 1700000000000L, nonce = nonce)
        assertEquals("AAECAwQFBgcICQoL", blob.nonce)
        println("WIRE_VECTOR ct=" + blob.ct)
        assertEquals("""{"v":1,"ts":1700000000000,"nonce":"AAECAwQFBgcICQoL","ct":"$FIXED_CT"}""", blob.encode())
        assertEquals("""{"v":1}""", Wire.open(key, "profile", SyncBlob.decode(blob.encode())))
    }

    @Test fun `the key name is bound as associated data, so a blob moved to another key will not open`() {
        val blob = Wire.seal(key, "d_abc:2024-01", "x", ts = 1L, nonce = nonce)
        assertThrows(GeneralSecurityException::class.java) { Wire.open(key, "d_abc:2024-02", blob) }
        assertThrows(GeneralSecurityException::class.java) { Wire.open(ByteArray(32), "d_abc:2024-01", blob) }
    }

    @Test fun `sources, months and stay ids follow the worker's charset`() {
        assertEquals("d_abcdefghijklmnop", Wire.deviceSource("ABCDEFGHIJKLMNOP"))
        assertEquals("i_0123456789ab", Wire.importSource("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
        assertTrue(Wire.KEY_RE.matches("d_abcdefghijklmnop:2024-12"))
        assertTrue(Wire.KEY_RE.matches("profile"))
        assertFalse(Wire.KEY_RE.matches("d_ABC:2024-12"))
        // 2023-12-31T23:59:59Z and one second later sit in different months, whatever the phone's zone.
        assertEquals("2023-12", Wire.monthOf(1704067199L))
        assertEquals("2024-01", Wire.monthOf(1704067200L))
        assertEquals("d_abc:2024-01", Wire.chunkKey("d_abc", "2024-01"))
        assertEquals(64, Wire.stayId("d_abc", 1704067200L).length)
        assertEquals(Wire.stayId("d_abc", 5L), Wire.stayId("d_abc", 5L))
    }

    @Test fun `a profile maps homes to dates and back, keeping an open window open`() {
        val p = Profile("Ana", Units.MI, listOf(Home(42.36, -71.06, "Boston", "US", 0L, 1704067200L, "The flat"), Home(37.77, -122.42, "San Francisco", "US", 1704067200L, null)), listOf(Work(1.0, 2.0, "w", null, 0L, null)))
        val wire = Wire.profile(p, ts = 7L)
        val json = Json.parseToJsonElement(wire.encode()).jsonObject
        assertEquals(setOf("v", "ts", "name", "homes", "units"), json.keys)
        assertEquals(setOf("name", "lat", "lng", "from", "to"), json["homes"]!!.jsonArray[0].jsonObject.keys)
        assertEquals("mi", wire.units)
        assertNull(wire.homes[0].from)
        assertEquals("2024-01-01", wire.homes[0].to)
        assertEquals("2024-01-01", wire.homes[1].from)
        assertEquals("The flat", wire.homes[0].name)
        assertEquals("San Francisco", wire.homes[1].name)

        val back = Wire.toProfile(WireProfile.decode(wire.encode()), local = p)
        assertEquals(p.homes, back.homes)
        assertEquals(p.works, back.works)
        assertEquals(Units.MI, back.units)
        // Without a matching local home the name stands in for the city.
        val fresh = Wire.toProfile(WireProfile.decode(wire.encode()), local = null)
        assertEquals("The flat", fresh.homes[0].city)
        assertEquals("", fresh.homes[0].cc)
    }

    @Test fun `vault v2 is the drive key over the same plaintext and opens only under it`() {
        val body = Wire.sealVault(key, """{"v":1,"signPriv":"00"}""", iv = nonce)
        assertEquals(2, body.v)
        assertNull(body.salt)
        assertFalse("salt" in body.encode())
        assertEquals("""{"v":1,"signPriv":"00"}""", Wire.openVault(key, VaultBody.decode(body.encode())))
        assertThrows(GeneralSecurityException::class.java) { Wire.openVault(ByteArray(32), body) }
    }

    companion object {
        /** ChaCha20-Poly1305(key 0x01..0x20, nonce 0x00..0x0b, aad "profile") over `{"v":1}`. */
        const val FIXED_CT = "m4nD+sqSabfI2OWW2NaItOpyIZLQH/U="
    }
}
