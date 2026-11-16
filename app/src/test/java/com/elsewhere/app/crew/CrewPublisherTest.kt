package com.elsewhere.app.crew

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.VisitEntity
import com.elsewhere.app.ingest.Cities
import com.elsewhere.app.ingest.record.Fix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        store = CrewStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "crew.preferences_pb") })
        publisher = CrewPublisher(store, rooms, MutableSharedFlow(), db, { CityLookup(cities) }, scope) { now }
    }

    @After fun tearDown() { scope.cancel(); db.close(); dir.deleteRecursively() }

    private fun fix(ms: Long) = Fix(ms / 1000, 38.7250, -9.1400, 10f)

    @Test fun `nothing goes out without a share, pings while it runs, stop when it ends`() = runTest {
        store.addFriend(friend("A", Grants(liveCity = true, sonar = true)))
        store.addFriend(friend("B", Grants(liveCity = true)))
        publisher.onFix(fix(now))
        assertEquals(0, rooms.sent.size)

        publisher.startShare(setOf("A"), ShareLength.HOUR)
        assertEquals(listOf("A"), rooms.sent.map { it.first })
        val p = Ping.decode(rooms.sent[0].second)
        assertEquals(Grade.sonar, p.grade); assertEquals("Lisbon", p.city); assertNull(p.absenceDays); assertEquals(false, p.habitat)

        now += 10_000; publisher.onFix(fix(now))
        assertEquals(1, rooms.sent.size)
        now += 20_000; publisher.onFix(fix(now))
        assertEquals(2, rooms.sent.size)

        now += 3_600_000L; publisher.onFix(fix(now))
        assertEquals(STOP_JSON, rooms.sent.last().second)
        assertEquals("A", rooms.sent.last().first)
        assertNull(store.shareNow())
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
        now += 5_000
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
}
