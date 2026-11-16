package com.elsewhere.app.account

import com.elsewhere.app.data.Home
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Work
import com.elsewhere.app.data.Units
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException
import java.util.Base64

/** javax.crypto against WebCrypto: the shared vectors must come out byte for byte on both. */
class VaultTest {
    private val v = Json.parseToJsonElement(javaClass.getResource("/vault-vectors.json")!!.readText()).jsonObject
    private fun s(k: String) = v[k]!!.jsonPrimitive.content
    private fun b64(s: String): ByteArray = Base64.getDecoder().decode(s)
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    @Test fun `the key derives from the passphrase and salt`() {
        assertEquals(310_000, v["iterations"]!!.jsonPrimitive.content.toInt())
        assertEquals(s("key_hex"), Vault.deriveKey(s("passphrase"), b64(s("salt"))).hex())
    }

    @Test fun `the vector ciphertext opens to the plaintext`() {
        val key = Vault.deriveKey(s("passphrase"), b64(s("salt")))
        assertEquals(s("plaintext"), Vault.open(key, VaultBody(salt = s("salt"), iv = s("iv"), ct = s("ct"))))
    }

    @Test fun `sealing the plaintext with the vector's salt and iv gives the vector ciphertext`() {
        val key = Vault.deriveKey(s("passphrase"), b64(s("salt")))
        val body = Vault.seal(key, s("plaintext"), b64(s("salt")), b64(s("iv")))
        assertEquals(s("ct"), body.ct)
        assertEquals(s("salt"), body.salt)
        assertEquals(s("iv"), body.iv)
        assertEquals(1, body.v)
    }

    @Test fun `a wrong key fails to open`() {
        val wrong = Vault.deriveKey("incorrect horse", b64(s("salt")))
        assertThrows(GeneralSecurityException::class.java) { Vault.open(wrong, VaultBody(salt = s("salt"), iv = s("iv"), ct = s("ct"))) }
    }

    @Test fun `a fresh seal uses a random iv and round-trips`() {
        val key = Vault.deriveKey("correct horse battery staple", Vault.newSalt())
        val a = Vault.seal(key, "hello", Vault.newSalt())
        val b = Vault.seal(key, "hello", Vault.newSalt())
        assertNotEquals(a.iv, b.iv)
        assertEquals(12, b64(a.iv).size)
        assertEquals("hello", Vault.open(key, a))
    }

    @Test fun `the body serialises as v, salt, iv, ct and reads back`() {
        val body = VaultBody(salt = "c2FsdA==", iv = "aXY=", ct = "Y3Q=")
        assertEquals("""{"v":1,"salt":"c2FsdA==","iv":"aXY=","ct":"Y3Q="}""", body.encode())
        assertEquals(body, VaultBody.decode(body.encode()))
    }

    @Test fun `the vector plaintext reads as our contents, with the web's lowercase units`() {
        val c = VaultContents.decode(s("plaintext"))
        assertEquals("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60", c.signPriv)
        assertEquals("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a", c.kxPriv)
        assertEquals(Profile("Ada", Units.KM, listOf(Home(51.5, -0.12, "London", "GB", 0, null)), emptyList()), c.profile)
        assertNull(c.settings)
    }

    @Test fun `our contents seal the profile in the canonical shape with lowercase units and place_id`() {
        val p = Profile("Ada", Units.KM, listOf(Home(51.5, -0.12, "London", "GB", 0, null)), listOf(Work(51.52, -0.1, "ChIJx", "The office", 10, null)))
        val c = VaultContents(signPriv = "aa", kxPriv = "bb", profile = p, settings = VaultSettings(units = "km", relayUrl = "https://relay.example.org", accountUrl = "https://acct.example.org"))
        val json = c.encode()
        assertEquals(
            """{"v":1,"signPriv":"aa","kxPriv":"bb","profile":{"name":"Ada","units":"km","homes":[{"lat":51.5,"lng":-0.12,"city":"London","cc":"GB","from":0,"to":null}],"works":[{"lat":51.52,"lng":-0.1,"place_id":"ChIJx","name":"The office","from":10,"to":null}]},"settings":{"units":"km","relayUrl":"https://relay.example.org","accountUrl":"https://acct.example.org"}}""",
            json,
        )
        assertTrue(json.contains(""""place_id""""))
        assertTrue(json.contains(""""units":"km""""))
        assertFalse(json.contains("placeId"))
        assertFalse(json.contains(""""KM""""))
        assertEquals(c, VaultContents.decode(json))
    }

    @Test fun `a web-shaped plaintext opens into the app's Profile`() {
        val web = """{"v":1,"signPriv":"aa","kxPriv":"bb","profile":{"name":null,"units":"mi","homes":[{"lat":40.7,"lng":-74.0,"city":"New York","cc":"US","from":5,"to":9,"name":"Loft"}],"works":[{"lat":40.75,"lng":-73.98,"place_id":"ChIJw","name":"Midtown desk","from":6,"to":null}]}}"""
        val c = VaultContents.decode(web)
        assertEquals(
            Profile(null, Units.MI, listOf(Home(40.7, -74.0, "New York", "US", 5, 9, "Loft")), listOf(Work(40.75, -73.98, "ChIJw", "Midtown desk", 6, null))),
            c.profile,
        )
    }

    @Test fun `older Android plaintext with placeId and uppercase units still opens`() {
        val old = """{"v":1,"signPriv":"aa","kxPriv":"bb","profile":{"name":"Ada","units":"KM","homes":[],"works":[{"lat":1.0,"lng":2.0,"placeId":"ChIJo","from":0,"to":null}]}}"""
        assertEquals(Profile("Ada", Units.KM, emptyList(), listOf(Work(1.0, 2.0, "ChIJo", null, 0, null))), VaultContents.decode(old).profile)
    }
}
