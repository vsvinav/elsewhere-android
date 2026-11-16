package com.elsewhere.app.sonar

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.crew.CrewPublisher
import com.elsewhere.app.crew.CrewStore
import com.elsewhere.app.crew.Crypto
import com.elsewhere.app.crew.Friend
import com.elsewhere.app.crew.Grade
import com.elsewhere.app.crew.Grants
import com.elsewhere.app.crew.Message
import com.elsewhere.app.crew.Ping
import com.elsewhere.app.crew.RelayClient
import com.elsewhere.app.crew.RoomHub
import com.elsewhere.app.crew.Rooms
import com.elsewhere.app.crew.ShareLength
import com.elsewhere.app.crew.ShareSession
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.RawFixEntity
import com.elsewhere.app.data.db.VisitEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SonarEngineTest {
    private class Rec : SonarAlerts {
        val names = ArrayList<String>(); val tests = ArrayList<Pair<Boolean, Boolean>>()
        override fun nearby(friend: Friend, test: Boolean, sent: Boolean) { names += friend.name; tests += test to sent }
    }
    private class FakeRooms : Rooms {
        val sent = ArrayList<Pair<String, String>>()
        override suspend fun send(friend: Friend, plaintext: String): Boolean { sent += friend.deviceId to plaintext; return true }
        override fun forget(deviceId: String) = Unit
    }

    private lateinit var ctx: Context
    private lateinit var db: ElsewhereDb
    private lateinit var scope: CoroutineScope
    private lateinit var store: CrewStore
    private lateinit var dir: File
    private val rooms = FakeRooms()
    private val alerts = Rec()
    private var now = 1_763_222_520_000L
    private lateinit var engine: SonarEngine
    private val marta = Friend("M", "Marta", Crypto.hex(ByteArray(32)), Crypto.hex(ByteArray(32)), "PAIRM", Crypto.b64(ByteArray(32)), 0L, Grants(sonar = true))

    @Before fun setUp() = runTest {
        ctx = ApplicationProvider.getApplicationContext()
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        store = CrewStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "crew.preferences_pb") })
        val publisher = CrewPublisher(store, rooms, MutableSharedFlow(), db, { null }, scope) { now }
        val hub = RoomHub(store, RelayClient(OkHttpClient(), { "" }, { store.keys() }), scope)
        engine = SonarEngine(store, hub, publisher, db, alerts, scope, { now }, { ZoneId.of("UTC") })
        store.addFriend(marta)
        store.startShare(ShareSession(setOf("M"), now, ShareLength.UNTIL_STOP.until(now)))
        db.rawFixes().insertAll(listOf(RawFixEntity(ts = now / 1000 - 30, lat = 38.7223, lng = -9.1393, accuracyM = 10f, speedMps = null, altitude = null, activity = null, batteryPct = null)))
    }

    @After fun tearDown() { scope.cancel(); db.close(); dir.deleteRecursively() }

    private fun ping(grade: Grade = Grade.sonar, lat: Double? = 38.7230, lng: Double? = -9.1400, hit: Ping.Hit? = null, habitat: Boolean = false, absence: Int? = 200, t: Long = now / 1000, test: Boolean = false) =
        Message.Pinged("M", Ping(t = t, grade = grade, lat = lat, lng = lng, city = "Lisbon", habitat = habitat, absenceDays = absence, hit = hit, test = test))

    @Test fun `a test ping echoes past every gate, records no debounce, and is not echoed twice`() = runTest {
        store.recordHit("PAIRM", now - 1000)
        store.stopShare()
        val far = ping(lat = null, lng = null, hit = Ping.Hit("ab12cd34", now / 1000), habitat = true, absence = 0, test = true)
        assertEquals(Decision.Echo("ab12cd34"), engine.evaluate(far))
        assertEquals(listOf("Marta"), alerts.names)
        assertEquals(listOf(true to false), alerts.tests)
        assertEquals(0, rooms.sent.size)
        assertEquals(mapOf("PAIRM" to (now - 1000)), store.debounce(listOf("PAIRM")).lastHitByPair)
        assertEquals(Decision.Quiet, engine.evaluate(far))
        assertEquals("a test ping without a hit is nothing", Decision.Quiet, engine.evaluate(ping(test = true)))
        assertEquals(1, alerts.names.size)
    }

    @Test fun `a test fire rings here, sends one test ping with a hit, and leaves the debounce alone`() = runTest {
        val id = engine.testFire("M")!!
        assertTrue(id.matches(Regex("[0-9a-f]{8}")))
        assertEquals(listOf(true to true), alerts.tests)
        assertEquals(1, rooms.sent.size)
        val p = Ping.decode(rooms.sent[0].second)
        assertTrue(p.test)
        assertEquals(id, p.hit!!.id)
        assertEquals(Grade.sonar, p.grade)
        assertEquals(null, p.lat)
        assertEquals(null, p.lng)
        assertTrue(store.debounce(listOf("PAIRM")).lastHitByPair.isEmpty())
        assertEquals("my own id reflected is nothing", Decision.Quiet, engine.evaluate(ping(hit = Ping.Hit(id, now / 1000), test = true)))
        store.setGrants("M", Grants(liveCity = true))
        assertEquals(null, engine.testFire("M"))
        assertEquals(1, rooms.sent.size)
    }

    @Test fun `a real ping without a position is quiet`() = runTest {
        assertEquals(Decision.Quiet, engine.evaluate(ping(lat = null, lng = null)))
    }

    @Test fun `a fresh near newcomer fires, alerts, sends the hit and debounces`() = runTest {
        val d = engine.evaluate(ping())
        assertTrue(d is Decision.Fire)
        assertEquals(listOf("Marta"), alerts.names)
        assertEquals(1, rooms.sent.size)
        assertEquals((d as Decision.Fire).hitId, Ping.decode(rooms.sent[0].second).hit!!.id)
        assertEquals(mapOf("PAIRM" to now), store.debounce(listOf("PAIRM")).lastHitByPair)
        now += 60_000
        assertEquals(Decision.Quiet, engine.evaluate(ping()))
        assertEquals(1, alerts.names.size)
    }

    @Test fun `city pings, friends without sonar and no share stay quiet`() = runTest {
        assertEquals(Decision.Quiet, engine.evaluate(ping(grade = Grade.city)))
        store.setGrants("M", Grants(livePrecise = true))
        assertEquals(Decision.Quiet, engine.evaluate(ping(grade = Grade.precise)))
        store.setGrants("M", Grants(sonar = true))
        store.stopShare()
        assertEquals(Decision.Quiet, engine.evaluate(ping()))
        assertTrue(alerts.names.isEmpty())
    }

    @Test fun `my own habitat around the friend keeps it quiet`() = runTest {
        val s = now / 1000; val day = 86_400L
        db.visits().insertAll((1..5).map { i -> VisitEntity(i, s - i * day, s - i * day + 3600, 0, "p$i", 38.7230, -9.1400, "stay", 1.0, 1, "Lisbon", "PT", null) })
        assertEquals(Decision.Quiet, engine.evaluate(ping()))
    }

    @Test fun `an incoming hit echoes without a sound when muted, and still debounces`() = runTest {
        engine.muteToday("M")
        val d = engine.evaluate(ping(hit = Ping.Hit("PAIRM:7", now / 1000), habitat = true))
        assertEquals(Decision.Echo("PAIRM:7"), d)
        assertTrue(alerts.names.isEmpty())
        assertEquals(0, rooms.sent.size)
        assertEquals(mapOf("PAIRM" to now), store.debounce(listOf("PAIRM")).lastHitByPair)
        val until = store.friend("M")!!.mutedUntil!!
        assertEquals(0L, until % 86_400_000L)
        assertTrue(until > now && until - now <= 86_400_000L)
    }

    @Test fun `the notification lands on the sonar channel with the agreed words and actions`() {
        shadowOf(ctx.applicationContext as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        SonarNotifier(ctx).nearby(marta)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val shadow = shadowOf(nm)
        val channel = nm.getNotificationChannel(SonarNotifier.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals("android.resource://${ctx.packageName}/raw/sonar", channel.sound.toString())
        assertTrue(channel.vibrationPattern.contentEquals(longArrayOf(0, 40, 120, 60)))
        assertFalse(channel.shouldShowLights())
        val n: Notification = shadow.getNotification(SonarNotifier.ID)
        assertNotNull(n)
        assertEquals("Sonar · Marta is nearby", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Within a mile of you, and neither of you is usually around here. Say hi?", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("Open Crew Map", "Mute Sonar for today"), n.actions.map { it.title.toString() })
    }

    @Test fun `a hit id is acted on once, replays carry nothing, and the memory is bounded`() = runTest {
        assertEquals(Decision.Echo("PAIRM:1"), engine.evaluate(ping(hit = Ping.Hit("PAIRM:1", now / 1000), habitat = true)))
        now += 25 * 3_600_000L
        assertEquals(Decision.Quiet, engine.evaluate(ping(hit = Ping.Hit("PAIRM:1", now / 1000), habitat = true)))
        assertEquals(1, alerts.names.size)
        for (i in 2..(SonarEngine.SEEN_HITS + 1)) {
            now += 25 * 3_600_000L
            assertEquals(Decision.Echo("PAIRM:$i"), engine.evaluate(ping(hit = Ping.Hit("PAIRM:$i", now / 1000), habitat = true)))
        }
        now += 25 * 3_600_000L
        assertEquals("the oldest id has been forgotten", Decision.Echo("PAIRM:1"), engine.evaluate(ping(hit = Ping.Hit("PAIRM:1", now / 1000), habitat = true)))
    }
}
