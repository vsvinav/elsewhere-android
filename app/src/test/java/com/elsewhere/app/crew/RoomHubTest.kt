package com.elsewhere.app.crew

import com.elsewhere.app.data.SecretBox
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

/** What a room accepts from the wire: only pings that are fresh by the sender's clock and newer than the last one. */
class RoomHubTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var dir: File
    private lateinit var hub: RoomHub
    private var now = 1_763_222_520_000L
    private val key = ByteArray(32) { 9 }
    private val marta = Friend("M", "Marta", Crypto.hex(ByteArray(32)), Crypto.hex(ByteArray(32)), "PAIRM", Crypto.b64(key), 0L, Grants(liveCity = true))

    @BeforeEach fun setUp() {
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        val store = CrewStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "crew.preferences_pb") }, SecretBox.inMemory())
        hub = RoomHub(store, RelayClient(OkHttpClient(), { "" }, { store.keys() }), scope) { now }
    }

    @AfterEach fun tearDown() { scope.cancel(); dir.deleteRecursively() }

    private fun sealed(plain: String) = Crypto.seal(key, "PAIRM", plain, "M").encode()
    private fun pingAt(t: Long, lat: Double = 38.72) = sealed("""{"type":"ping","t":$t,"grade":"city","lat":$lat,"lng":-9.14,"city":"Lisbon"}""")
    private val fix get() = hub.fixes.value["M"]

    @Test fun `an older or repeated time is a replay and leaves the fix alone`() = runTest {
        val t = now / 1000
        hub.receive(marta, pingAt(t - 60, lat = 1.0))
        assertEquals(1.0, fix!!.lat)
        hub.receive(marta, pingAt(t - 120, lat = 2.0))
        assertEquals(1.0, fix!!.lat, "older")
        hub.receive(marta, pingAt(t - 60, lat = 3.0))
        assertEquals(1.0, fix!!.lat, "same time")
        hub.receive(marta, pingAt(t, lat = 4.0))
        assertEquals(4.0, fix!!.lat, "newer")
    }

    @Test fun `a ping outside the freshness window is dropped either way`() = runTest {
        val t = now / 1000
        hub.receive(marta, pingAt(t - 11 * 60))
        assertNull(fix, "too old")
        hub.receive(marta, pingAt(t + 11 * 60))
        assertNull(fix, "from the future")
        hub.receive(marta, pingAt(t - 9 * 60))
        assertEquals(t - 9 * 60, fix!!.t)
    }

    @Test fun `staleness follows the sender's time, not when it arrived`() = runTest {
        val t = now / 1000
        hub.receive(marta, pingAt(t - 9 * 60))
        val f = fix!!
        assertTrue(f.receivedAt == now)
        assertTrue(!f.stale(now))
        assertTrue(f.stale(now + 7 * 60_000L), "nine minutes by the sender plus seven here")
        assertTrue(f.gone(now + 24 * 3_600_000L))
    }

    @Test fun `a ping without a numeric time is ignored`() = runTest {
        hub.receive(marta, sealed("""{"type":"ping","t":"now","grade":"city","lat":1,"lng":2}"""))
        assertNull(fix)
    }

    @Test fun `a test ping is never placed, even when it carries a position`() = runTest {
        val t = now / 1000
        hub.receive(marta, sealed("""{"type":"ping","t":$t,"grade":"sonar","lat":38.72,"lng":-9.14,"hit":{"id":"ab12cd34","t":$t},"test":true}"""))
        assertNull(fix)
        hub.receive(marta, sealed("""{"type":"ping","t":${t + 1},"grade":"sonar","lat":null,"lng":null,"hit":{"id":"ab12cd35","t":$t},"test":true}"""))
        assertNull(fix)
        hub.receive(marta, pingAt(t + 2))
        assertEquals(t + 2, fix!!.t)
    }
}
