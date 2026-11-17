package com.elsewhere.app.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.Fakes
import com.elsewhere.app.account.AccountException
import com.elsewhere.app.account.AccountRepositoryTest
import com.elsewhere.app.account.AccountState
import com.elsewhere.app.account.Accounts
import com.elsewhere.app.account.DriveLink
import com.elsewhere.app.account.Session
import com.elsewhere.app.account.Vault
import com.elsewhere.app.crew.DeviceKeys
import com.elsewhere.app.crew.Identity
import com.elsewhere.app.data.SecretBox
import com.elsewhere.app.data.CitiesLoader
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.RecordRepository
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.SOURCE_DEVICE
import com.elsewhere.app.data.db.SOURCE_SYNC
import com.elsewhere.app.data.db.VisitEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import java.io.IOException
import java.util.UUID

/** The engine over a real Room and record repository, against a worker kept in memory. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {
    /** Behaves as the worker does: 409 when it holds a newer ts, 404 as null. */
    class FakeApi : SyncApi {
        val blobs = LinkedHashMap<String, SyncBlob>()
        val calls = mutableListOf<String>()
        var fail: Throwable? = null
        var deletes = 0
        private fun check() { fail?.let { throw it } }
        override suspend fun list(session: String): List<SyncEntry> { calls += "list"; check(); return blobs.map { (k, b) -> SyncEntry(k, b.ts, b.ct.length) } }
        override suspend fun get(session: String, key: String): SyncBlob? { calls += "get $key"; check(); return blobs[key] }
        override suspend fun put(session: String, key: String, blob: SyncBlob) {
            calls += "put $key"; check()
            val held = blobs[key]
            if (held != null && held.ts > blob.ts) throw AccountException(409, "stale")
            blobs[key] = blob
        }
        override suspend fun deleteAll(session: String) { calls += "delete"; check(); blobs.clear(); deletes++ }
    }

    private class FakeIdentity : Identity {
        var keys = DeviceKeys.fromSeeds(ByteArray(32) { 1 }, ByteArray(32) { 2 })
        override suspend fun keys() = keys
        override suspend fun replaceKeys(k: DeviceKeys) { keys = k }
    }

    private lateinit var db: ElsewhereDb
    private lateinit var scope: CoroutineScope
    private lateinit var repo: RecordRepository
    private lateinit var dir: File
    private val api = FakeApi()
    private val identity = FakeIdentity()
    private val key = ByteArray(32) { 5 }
    private val store = AccountRepositoryTest.FakeStorage(AccountState(session = Session("s", "acct", "a@b.c"), driveKey = Vault.b64(key)))
    private val accounts = Fakes.Account()
    private val drive = Fakes.Drive()
    private var retries = 0
    private var now = 1_700_000_000_000L
    private lateinit var engine: SyncEngine

    private val mySource get() = Wire.deviceSource(identity.keys.deviceId)
    private val profile = Profile("Ada", Units.KM, listOf(Home(51.5, -0.12, "London", "GB", 0, null)), emptyList())

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
        repo = RecordRepository(db, SettingsStore(ds, SecretBox.inMemory()), CitiesLoader(ctx), scope).apply { clock = { now } }
        engine = SyncEngine(ctx, api, db, store, repo, identity, DriveLinker(drive, drive, accounts), scope, { now }, { retries++ }, minIntervalMs = 15 * 60_000L)
    }

    @After fun tearDown() { scope.cancel(); db.close(); dir.deleteRecursively() }

    private fun stay(start: Long, lat: Double = 48.85, lng: Double = 2.35) =
        VisitEntity(start.toInt(), start, start + 3600, 60, "dev:x", lat, lng, "UNKNOWN", 1.0, 0, null, null, null, SOURCE_DEVICE)

    private suspend fun chunk(k: String): MonthChunk = MonthChunk.decode(Wire.open(key, k, api.blobs[k]!!))

    @Test fun `this phone's stays go up as one chunk per UTC month, sorted, and only when they changed`() = runTest {
        repo.insertDevice(listOf(stay(1704067199L), stay(1704067200L), stay(1703000000L)), emptyList(), emptyList())
        repo.saveProfile(profile); repo.profile.first { it != null }
        engine.run(force = true)
        assertEquals(SyncStatus.Idle(now), engine.status.value)
        assertEquals(setOf("$mySource:2023-12", "$mySource:2024-01", "profile"), api.blobs.keys)
        val dec = chunk("$mySource:2023-12")
        assertEquals(listOf(1703000000L, 1704067199L), dec.stays.map { it.start })
        assertEquals(Wire.stayId(mySource, 1703000000L), dec.stays[0].id)
        assertEquals(1704067200L, chunk("$mySource:2024-01").stays.single().start)
        val p = WireProfile.decode(Wire.open(key, "profile", api.blobs["profile"]!!))
        assertEquals("Ada", p.name); assertEquals("London", p.homes.single().name)

        api.calls.clear(); now += 1000
        engine.run(force = true)
        assertEquals("nothing changed, nothing sent", listOf("list"), api.calls)

        repo.insertDevice(listOf(stay(1704067300L)), emptyList(), emptyList())
        engine.run(force = true)
        assertEquals(2, chunk("$mySource:2024-01").stays.size)
        assertTrue(api.calls.none { it == "put $mySource:2023-12" })
    }

    @Test fun `another device's chunk lands as sync stays with their names, is not fetched twice, and is never pushed back`() = runTest {
        repo.saveProfile(profile); repo.profile.first { it != null }
        val other = "d_otherphone0001"
        val plain = MonthChunk(stays = listOf(WireStay(Wire.stayId(other, 1700000000L), 1.5, -2.25, "Café", 1700000000L, 1700003600L))).encode()
        api.blobs["$other:2023-11"] = Wire.seal(key, "$other:2023-11", plain, 5L)
        engine.run(force = true)
        val rows = db.visits().bySource(SOURCE_SYNC)
        assertEquals(1, rows.size)
        assertEquals(1700000000L, rows[0].start)
        assertEquals("Café", db.placeEnrichment().all().single { it.placeId == rows[0].placeId }.name)
        assertTrue(repo.hasImport.first { it })
        assertTrue(api.blobs.keys.none { it.startsWith(mySource) })

        api.calls.clear()
        engine.run(force = true)
        assertTrue(api.calls.none { it.startsWith("get") })
        assertEquals(1, db.visits().bySource(SOURCE_SYNC).size)
    }

    @Test fun `the profile is last writer wins by ts, both ways`() = runTest {
        repo.saveProfile(profile); repo.profile.first { it != null }
        engine.run(force = true)
        val remote = WireProfile(ts = now + 10, name = "Grace", homes = listOf(WireHome("Paris", 48.85, 2.35, "2020-01-01", null)), units = "mi")
        api.blobs["profile"] = Wire.seal(key, "profile", remote.encode(), now + 10)
        now += 20
        engine.run(force = true)
        val p = repo.profile.first { it?.name == "Grace" }!!
        assertEquals(Units.MI, p.units)
        assertEquals("Paris", p.homes.single().city)
        assertEquals(1577836800L, p.homes.single().from)
        assertEquals("the newer remote one was taken, not overwritten", 1, api.calls.count { it == "put profile" })

        repo.saveProfile(p.copy(name = "Grace H.")); repo.profile.first { it?.name == "Grace H." }
        now += 20
        engine.run(force = true)
        val sent = WireProfile.decode(Wire.open(key, "profile", api.blobs["profile"]!!))
        assertEquals("Grace H.", sent.name)
        assertTrue(sent.ts > remote.ts)
    }

    @Test fun `a blob that will not open locks sync, a dead network asks for a retry, no key means off`() = runTest {
        api.blobs["d_otherphone0001:2023-11"] = Wire.seal(ByteArray(32) { 9 }, "d_otherphone0001:2023-11", """{"v":1,"stays":[]}""", 1L)
        engine.run(force = true)
        assertEquals(SyncStatus.Error(SyncError.Locked), engine.status.value)
        assertEquals(0, retries)

        api.blobs.clear(); api.fail = IOException("offline")
        engine.run(force = true)
        assertEquals(SyncStatus.Error(SyncError.Network), engine.status.value)
        assertEquals(1, retries)

        api.fail = null; store.saveDriveKey(null)
        engine.run(force = true)
        assertEquals(SyncStatus.Off, engine.status.value)
    }

    @Test fun `a stale answer on our own chunk takes the worker's ts as pushed and the round goes on`() = runTest {
        val held = Wire.seal(key, "$mySource:2023-12", """{"v":1,"stays":[]}""", now + 5000)
        api.blobs["$mySource:2023-12"] = held
        repo.insertDevice(listOf(stay(1703000000L), stay(1704067200L)), emptyList(), emptyList())
        engine.run(force = true)
        assertEquals("the worker's copy stands", held, api.blobs["$mySource:2023-12"])
        assertEquals(now + 5000, db.syncState().all().single { it.key == "$mySource:2023-12" }.ts)
        assertEquals(1, chunk("$mySource:2024-01").stays.size)
        assertEquals(SyncStatus.Idle(now), engine.status.value)
        api.calls.clear()
        engine.run(force = true)
        assertEquals("counted as pushed", listOf("list"), api.calls)
    }

    @Test fun `a Drive key that differs from the one the bookkeeping was made under sends everything again`() = runTest {
        repo.insertDevice(listOf(stay(1704067200L)), emptyList(), emptyList())
        engine.run(force = true)
        val other = ByteArray(32) { 7 }
        api.blobs.clear(); api.calls.clear()
        store.saveDriveKey(Vault.b64(other))
        engine.run(force = true)
        assertTrue(api.calls.contains("put $mySource:2024-01"))
        assertEquals(1, MonthChunk.decode(Wire.open(other, "$mySource:2024-01", api.blobs["$mySource:2024-01"]!!)).stays.size)
        assertEquals(SyncStatus.Idle(now), engine.status.value)
    }

    @Test fun `a blob that will not open fetches the Drive key again once, then the round goes on under it`() = runTest {
        repo.insertDevice(listOf(stay(1704067200L)), emptyList(), emptyList())
        engine.run(force = true)
        val newKey = ByteArray(32) { 9 }
        drive.key = newKey
        val other = "d_otherphone0001"
        api.blobs["$other:2023-11"] = Wire.seal(newKey, "$other:2023-11", MonthChunk(stays = listOf(WireStay(Wire.stayId(other, 1700000000L), 1.5, -2.25, null, 1700000000L, 1700003600L))).encode(), 2L)
        api.calls.clear()
        engine.run(force = true)
        assertEquals(SyncStatus.Idle(now), engine.status.value)
        assertEquals(listOf(Vault.b64(newKey)), accounts.driveKeys)
        assertEquals(1, db.visits().bySource(SOURCE_SYNC).size)
        assertEquals("our chunk went up again under the new key", 1, MonthChunk.decode(Wire.open(newKey, "$mySource:2024-01", api.blobs["$mySource:2024-01"]!!)).stays.size)

        api.blobs["d_otherphone0002:2023-11"] = Wire.seal(ByteArray(32) { 11 }, "d_otherphone0002:2023-11", """{"v":1,"stays":[]}""", 3L)
        store.saveDriveKey(Vault.b64(newKey))
        engine.run(force = true)
        assertEquals(SyncStatus.Error(SyncError.Locked), engine.status.value)
        assertEquals("asked Drive once more, not in a loop", 2, accounts.driveKeys.size)
    }

    @Test fun `an older profile from the worker does not replace one edited here, which goes up with its edit time`() = runTest {
        api.blobs["profile"] = Wire.seal(key, "profile", WireProfile(ts = now - 10, name = "Grace", homes = emptyList(), units = "mi").encode(), now - 10)
        repo.saveProfile(profile); repo.profile.first { it != null }
        now += 5
        engine.run(force = true)
        assertEquals("Ada", repo.profile.value!!.name)
        val sent = WireProfile.decode(Wire.open(key, "profile", api.blobs["profile"]!!))
        assertEquals("Ada", sent.name)
        assertEquals(now - 5, sent.ts)
    }

    @Test fun `a round that failed offline runs again on the next nudge, interval or not`() = runTest {
        engine.run(force = true)
        api.fail = IOException("offline"); now += 60_000
        engine.run(force = true)
        assertEquals(SyncStatus.Error(SyncError.Network), engine.status.value)
        api.fail = null; api.calls.clear(); now += 60_000
        engine.run(force = false)
        assertEquals(listOf("list"), api.calls)
        assertEquals(SyncStatus.Idle(now), engine.status.value)
    }

    @Test fun `rounds keep fifteen minutes apart unless asked for`() = runTest {
        engine.run(force = true)
        api.calls.clear(); now += 60_000
        engine.run(force = false)
        assertTrue(api.calls.isEmpty())
        now += 15 * 60_000
        engine.run(force = false)
        assertEquals(listOf("list"), api.calls)
    }

    @Test fun `start fresh clears the worker, replaces the Drive key, forgets what was pushed and sends the record again`() = runTest {
        repo.insertDevice(listOf(stay(1704067200L)), emptyList(), emptyList())
        engine.run(force = true)
        api.blobs["d_otherphone0001:2023-11"] = Wire.seal(key, "d_otherphone0001:2023-11", """{"v":1,"stays":[]}""", 1L)
        engine.run(force = true)
        assertEquals(4, db.syncState().all().size)

        engine.startFresh(ApplicationProvider.getApplicationContext())
        assertEquals(1, api.deletes)
        assertEquals(1, drive.deletes)
        assertEquals(1, accounts.driveKeys.size)
        assertEquals(setOf("$mySource:2024-01"), api.blobs.keys)
        assertEquals("what's on this device stays", 1, db.visits().bySource(SOURCE_DEVICE).size)
        assertEquals(SyncStatus.Idle(now), engine.status.value)
    }
}
