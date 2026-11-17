package com.elsewhere.app.sync

import com.elsewhere.app.account.VaultBody
import com.elsewhere.app.account.VaultContents
import com.elsewhere.app.account.VaultSettings
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
    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { it.toByte() }

    @Test fun `the fixed vectors seal to the web's ciphertexts`() {
        val blob = Wire.seal(key, CHUNK_KEY, CHUNK_PLAIN, ts = 1700000000000L, nonce = nonce)
        assertEquals("AAECAwQFBgcICQoL", blob.nonce)
        println("WIRE_VECTOR chunk ct=" + blob.ct)
        assertEquals(CHUNK_CT, blob.ct)
        assertEquals("""{"v":1,"ts":1700000000000,"nonce":"AAECAwQFBgcICQoL","ct":"$CHUNK_CT"}""", blob.encode())
        assertEquals(CHUNK_PLAIN, Wire.open(key, CHUNK_KEY, SyncBlob.decode(blob.encode())))
        assertEquals("8879991759aa282c25416e9018227a8b2739ff579e1322baebf4a281be245a8b", Wire.stayId("i_ab12cd34ef56", 1700000000L))

        val vault = Wire.sealVault(key, VAULT_PLAIN, iv = nonce)
        println("WIRE_VECTOR vault ct=" + vault.ct)
        assertEquals(VaultBody(2, null, "AAECAwQFBgcICQoL", VAULT_CT), vault)
        assertEquals(VAULT_PLAIN, Wire.openVault(key, vault))
        val contents = VaultContents(signPriv = "00", kxPriv = "11", profile = Profile(null, Units.KM, emptyList(), emptyList()), settings = VaultSettings("km", ""))
        assertEquals(VAULT_PLAIN, contents.encode(), "the phone writes the vault plaintext in the web's shape")
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

    /** Key 0x00..0x1f, nonce 0x00..0x0b, as published by the web client. */
    private companion object {
        const val CHUNK_KEY = "i_ab12cd34ef56:2023-11"
        const val CHUNK_PLAIN = """{"v":1,"stays":[{"id":"ab","lat":1.5,"lng":-2.25,"name":"Café","start":1700000000,"end":1700003600}]}"""
        const val CHUNK_CT = "8tl+IhMmiWLE916K6z80OLJS24NzTo/YhLUD5xmh0h7WsQLGyjzd9lAQTPVXur0WUqbHFZBNmqEw+7hhU+a8+lsLyKyXYA7T9SF9jChM7MHq7msQCgSUHDRmxz7oTTKMe+5cjqqkgX8d93Rpevk8oDnohku4ZQ=="
        const val VAULT_PLAIN = """{"v":1,"signPriv":"00","kxPriv":"11","profile":{"name":null,"units":"km","homes":[],"works":[]},"settings":{"units":"km","relayUrl":""}}"""
        const val VAULT_CT = "8tl+IhMmiWLE6lidyG9nFetKkNdhVoGbje9/txy2hAbOsR3RyjzB6lhUH7QAtrVYXOrIGZgKgvVn1LUrsjrwvw0LnvfHf0HL6DQl03UZr9PghRoeTR2fTGUk0jSDIH+Tb60Jh4OwSZPCecnMgOTZ6Z0cdAKrNi3aY09GuhVexhPTCuGHixGiEZM9PV38sdi376aZZlJfxhc="
    }
}
