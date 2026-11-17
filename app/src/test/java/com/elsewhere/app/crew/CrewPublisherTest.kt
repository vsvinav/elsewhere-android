package com.elsewhere.app.crew

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.VisitEntity
import com.elsewhere.app.ingest.Cities
import com.elsewhere.app.ingest.record.Fix
import com.elsewhere.app.data.SecretBox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrewPublisherTest {
    private class FakeRooms : Rooms {
        val sent = ArrayList<Pair<String, String>>()
        override suspend fun send(friend: Friend, plaintext: String): Boolean { sent += friend.deviceId to plaintext; return true }
        override fun forget(deviceId: String) = Unit
    }

    private lateinit var db: ElsewhereDb
    private lateinit var scope: CoroutineScope
    private lateinit var store: CrewStore
    private lateinit var dir: File
    private val rooms = FakeRooms()
    private var now = 1_763_222_520_000L
    private val cities = Cities(doubleArrayOf(38.7223), doubleArrayOf(-9.1393), listOf("Lisbon"), listOf("PT"))
    private lateinit var publisher: CrewPublisher

    private fun friend(id: String, grants: Grants) = Friend(id, id, Crypto.hex(ByteArray(32)), Crypto.hex(ByteArray(32)), "PAIR$id", Crypto.b64(ByteArray(32)), 0L, grants)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        store = CrewStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "crew.preferences_pb") }, SecretBox.inMemory())
        publisher = CrewPublisher(store, rooms, MutableSharedFlow(), db, { CityLookup(cities) }, scope, { now }, sleep = { ms -> waits += ms; release.await() })
    }

    private val waits = ArrayList<Long>()
    private var release = CompletableDeferred<Unit>()

    /** Let a held send go, as if its wait had passed. */
    private fun wake(ms: Long) { now += ms; val r = release; release = CompletableDeferred(); r.complete(Unit) }

    private fun await(what: String, cond: () -> Boolean) { repeat(300) { if (cond()) return; Thread.sleep(10) }; assertTrue(what, cond()) }

    @After fun tearDown() { scope.cancel(); db.close(); dir.deleteRecursively() }

    private fun fix(ms: Long) = Fix(ms / 1000, 38.7250, -9.1400, 10f)

    @Test fun `sonar and city friends get pings from every fix, share or not, and stop goes out when a share ends`() = runTest {
        store.addFriend(friend("A", Grants(liveCity = true, sonar = true)))
        store.addFriend(friend("B", Grants(liveCity = true)))
        store.addFriend(friend("P", Grants(livePrecise = true)))
        store.addFriend(friend("N", Grants()))
        publisher.onFix(fix(now))
        assertEquals("precise alone still waits for a share", setOf("A", "B"), rooms.sent.map { it.first }.toSet())
        val byId = rooms.sent.associate { it.first to Ping.decode(it.second) }
        assertEquals(Grade.sonar, byId["A"]!!.grade); assertEquals("Lisbon", byId["A"]!!.city); assertNull(byId["A"]!!.absenceDays); assertEquals(false, byId["A"]!!.habitat)
        assertEquals(Grade.city, byId["B"]!!.grade)
        rooms.sent.clear()

        now += 10_000; publisher.onFix(fix(now))
        assertEquals("inside the 30 s cadence", 0, rooms.sent.size)
        now += 20_000; publisher.onFix(fix(now))
        assertEquals(listOf("A"), rooms.sent.map { it.first })
        rooms.sent.clear()

        publisher.startShare(setOf("P"), ShareLength.HOUR)
        assertEquals(Grade.precise, Ping.decode(rooms.sent.single { it.first == "P" }.second).grade)
        rooms.sent.clear()
        now += 3_600_000L; publisher.onFix(fix(now))
        assertEquals(listOf("P" to STOP_JSON), rooms.sent.filter { it.second == STOP_JSON })
        assertNull(store.shareNow())
        assertEquals("the share ending does not stop sonar pings", setOf("A", "B"), rooms.sent.filter { it.second != STOP_JSON }.map { it.first }.toSet())
    }

    @Test fun `a fix on demand publishes at once and is asked for at most once a minute`() = runTest {
        store.addFriend(friend("A", Grants(liveCity = true, sonar = true)))
        publisher.onFix(fix(now))
        rooms.sent.clear()
        var asked = 0
        val fresh: suspend () -> Fix? = { asked++; fix(now) }
        now += 5_000
        publisher.demand(fresh)
        assertEquals(1, asked)
        await("the demand ping ignores the cadence, but still waits out the relay's gap") { waits == listOf(5_000L) }
        wake(5_000)
        await("sent") { rooms.sent.map { it.first } == listOf("A") }
        now += 30_000
        publisher.demand(fresh)
        assertEquals("debounced", 1, asked)
        now += 30_000
        publisher.demand(fresh)
        assertEquals(2, asked)
        publisher.demand { asked++; null }
        assertEquals("a failed fix neither publishes nor counts against the minute", 2, rooms.sent.size)
    }

    @Test fun `self judgement comes from visits and recent fixes`() = runTest {
        val day = 86_400L
        val s = now / 1000
        db.visits().insertAll((1..5).map { i -> VisitEntity(i, s - i * day, s - i * day + 3600, 0, "p$i", 38.7225, -9.1390, "stay", 1.0, 1, "Lisbon", "PT", null) })
        store.addFriend(friend("A", Grants(sonar = true)))
        publisher.startShare(setOf("A"), ShareLength.UNTIL_STOP)
        publisher.onFix(fix(now))
        val p = Ping.decode(rooms.sent.single().second)
        assertEquals(true, p.habitat)
        assertEquals(1, p.absenceDays)
    }

    @Test fun `a hit goes out at once and stop share tells everyone`() = runTest {
        val a = friend("A", Grants(sonar = true)); val b = friend("B", Grants(livePrecise = true))
        store.addFriend(a); store.addFriend(b)
        publisher.startShare(setOf("A", "B"), ShareLength.UNTIL_STOP)
        publisher.onFix(fix(now))
        rooms.sent.clear()
        now += 15_000
        publisher.carryHit(a, Ping.Hit("PAIRA:1", now / 1000))
        assertEquals(listOf("A"), rooms.sent.map { it.first })
        assertEquals(Ping.Hit("PAIRA:1", now / 1000), Ping.decode(rooms.sent[0].second).hit)
        rooms.sent.clear()
        publisher.stopShare()
        assertEquals(setOf("A" to STOP_JSON, "B" to STOP_JSON), rooms.sent.toSet())
        assertNull(store.shareNow())
    }

    @Test fun `revoke tells the friend and forgets them`() = runTest {
        val a = friend("A", Grants(sonar = true))
        store.addFriend(a)
        publisher.revoke(a)
        assertEquals(listOf("A" to REVOKE_JSON), rooms.sent)
        assertEquals(emptyList<Friend>(), store.friendsNow())
    }

    @Test fun `a hit inside the relay's ten seconds waits for them, and rides a ping sent in the meantime`() = runTest {
        val a = friend("A", Grants(sonar = true))
        store.addFriend(a)
        publisher.onFix(fix(now))
        rooms.sent.clear()
        now += 3_000
        publisher.carryHit(a, Ping.Hit("PAIRA:1", now / 1000))
        assertEquals("too soon for the relay: nothing yet", 0, rooms.sent.size)
        await("the send is held") { waits == listOf(7_000L) }
        wake(7_000)
        await("sent once the gap has passed") { rooms.sent.size == 1 }
        assertEquals("PAIRA:1", Ping.decode(rooms.sent[0].second).hit!!.id)
        rooms.sent.clear()

        now += 3_000
        publisher.carryHit(a, Ping.Hit("PAIRA:2", now / 1000))
        await("held again") { waits.size == 2 }
        now += 2_000
        publisher.demand { fix(now) }
        assertEquals("a forced ping inside the gap waits too", 0, rooms.sent.size)
        val at = now / 1000
        wake(5_000)
        await("one ping carries both the fix and the hit") { rooms.sent.size == 1 }
        assertEquals("PAIRA:2", Ping.decode(rooms.sent[0].second).hit!!.id)
        assertEquals(at, Ping.decode(rooms.sent[0].second).t)
        Thread.sleep(50)
        assertEquals("the hit is sent once, with one wait", 1, rooms.sent.size); assertEquals(2, waits.size)
    }

    @Test fun `two tabs asking for a fix at once fetch it once`() = runTest {
        store.addFriend(friend("A", Grants(sonar = true)))
        var asked = 0
        val gate = CompletableDeferred<Unit>()
        val fresh: suspend () -> Fix? = { asked++; gate.await(); fix(now) }
        val first = scope.launch { publisher.demand(fresh) }
        await("the first fetch is under way") { asked == 1 }
        publisher.demand(fresh)
        assertEquals("the second ask returns without fetching", 1, asked)
        gate.complete(Unit)
        first.join()
        assertEquals(1, rooms.sent.size)
    }

    @Test fun `a fetch that comes back empty leaves the minute open for the next ask`() = runTest {
        store.addFriend(friend("A", Grants(sonar = true)))
        publisher.demand { null }
        var asked = 0
        publisher.demand { asked++; fix(now) }
        assertEquals(1, asked)
    }
}
