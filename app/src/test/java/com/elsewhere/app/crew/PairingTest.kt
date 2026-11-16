package com.elsewhere.app.crew

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

/** The relay is a courier, not a notary: a record whose kx key the signing key never vouched for goes nowhere. */
class PairingTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var dir: File
    private lateinit var store: CrewStore
    private lateinit var pairing: Pairing
    private val marta = DeviceKeys.fromSeeds(ByteArray(32) { 5 }, ByteArray(32) { 6 })
    private val mallory = DeviceKeys.fromSeeds(ByteArray(32) { 7 }, ByteArray(32) { 8 })
    private val now = 1_763_222_520_000L

    private fun record(kxPub: ByteArray = marta.kxPub, sig: String? = Crypto.kxSig(marta.signPriv, marta.kxPub)) =
        """{"signPub":"${Crypto.hex(marta.signPub)}","kxPub":"${Crypto.hex(kxPub)}","name":"Marta"${if (sig == null) "" else ""","kxSig":"$sig""""}}"""

    @BeforeEach fun setUp() {
        server.start()
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        store = CrewStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "crew.preferences_pb") })
        pairing = Pairing(store, RelayClient(OkHttpClient(), { server.url("/").toString() }, { store.keys() }, { now }), { "Me" }, { now })
    }

    @AfterEach fun tearDown() { runCatching { server.shutdown() }; scope.cancel(); dir.deleteRecursively() }

    @Test fun `my record carries a kx signature the other side can check`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        pairing.openCode()
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val me = store.keys()
        assertEquals(Crypto.hex(me.signPub), body["signPub"]!!.jsonPrimitive.content)
        assertEquals(Crypto.hex(me.kxPub), body["kxPub"]!!.jsonPrimitive.content)
        assertTrue(Crypto.verifyKxSig(me.signPub, me.kxPub, body["kxSig"]!!.jsonPrimitive.content))
    }

    @Test fun `a well bound record is claimed and confirmed`() = runTest {
        server.enqueue(MockResponse().setBody(record()))
        val p = pairing.claim("PT-MRT-7Q4K")
        assertEquals(Crypto.fingerprint(marta.signPub, marta.kxPub), p.fingerprint)
        val f = pairing.confirm(p)
        assertEquals(marta.deviceId, f.deviceId)
        assertEquals(Crypto.hex(marta.kxPub), f.kxPub)
    }

    @Test fun `a record with a substituted kx key is refused before any key is derived`() = runTest {
        server.enqueue(MockResponse().setBody(record(kxPub = mallory.kxPub)))
        val e = runCatching { pairing.claim("PT-MRT-7Q4K") }.exceptionOrNull()
        assertEquals(KEYS_UNBOUND, e?.message)
        assertTrue(store.friendsNow().isEmpty())
    }

    @Test fun `a record without a kx signature is refused, on claim and on poll`() = runTest {
        server.enqueue(MockResponse().setBody(record(sig = null)))
        assertEquals(KEYS_UNBOUND, runCatching { pairing.claim("PT-MRT-7Q4K") }.exceptionOrNull()?.message)
        server.enqueue(MockResponse().setBody(record(sig = null)))
        assertEquals(KEYS_UNBOUND, runCatching { pairing.poll(ShareCode("PT-MRT-7Q4K", now)) }.exceptionOrNull()?.message)
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(pairing.poll(ShareCode("PT-MRT-7Q4K", now)))
    }

    @Test fun `confirm itself refuses a tampered pending record`() = runTest {
        val bad = Pending("PT-MRT-7Q4K", PairRecord(Crypto.hex(marta.signPub), Crypto.hex(mallory.kxPub), "Marta", Crypto.kxSig(marta.signPriv, marta.kxPub)))
        assertEquals(KEYS_UNBOUND, runCatching { pairing.confirm(bad) }.exceptionOrNull()?.message)
        assertTrue(store.friendsNow().isEmpty())
    }
}
