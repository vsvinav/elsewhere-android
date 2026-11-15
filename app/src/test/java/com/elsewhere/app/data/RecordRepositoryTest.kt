package com.elsewhere.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.VisitEntity
import com.elsewhere.app.data.db.decodePts
import com.elsewhere.app.data.db.encodePts
import com.elsewhere.app.ingest.Enrichment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordRepositoryTest {
    private lateinit var db: ElsewhereDb
    private lateinit var scope: CoroutineScope
    private lateinit var repo: RecordRepository
    private lateinit var dir: File
    private val ready = ReadyState()

    private val boston = Home(42.3632761, -71.0643721, "Boston", "US", 0, null)
    private val sf = Home(37.7749, -122.4194, "San Francisco", "US", 0, null)
    private fun profile(home: Home) = Profile(null, Units.KM, listOf(home), emptyList())

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
        repo = RecordRepository(db, SettingsStore(store), CitiesLoader(ctx), scope, ready)
    }

    @After fun tearDown() { scope.cancel(); db.close(); dir.deleteRecursively() }

    private fun open() = javaClass.classLoader!!.getResourceAsStream("mini_timeline.json")!!

    @Test fun `the home suggestions finish the readiness bar, so it does not wait on the user`() = runTest {
        repo.import({ open() }, "mini_timeline.json")
        assertEquals(Ready("Finding cities", 60), ready.ready.value)
        repo.provisionalRecord()
        assertEquals(Ready("Working out trips", 100), ready.ready.value)
    }

    @Test fun `import then saveProfile yields a record`() = runTest {
        val report = repo.import({ open() }, "mini_timeline.json")
        assertTrue(repo.hasImport.first { it })
        assertNull(repo.record.value)

        repo.saveProfile(profile(boston))
        val rec = repo.record.first { it != null }!!
        assertTrue(rec.places.isNotEmpty())
        assertEquals(rec.visits.minOf { it.start }, rec.meta.range.first)
        assertTrue(rec.meta.range.last >= rec.visits.maxOf { it.end })
        assertEquals(report.parsed, rec.meta.importReport.parsed)
        assertEquals("mini_timeline.json", rec.meta.sourceFile)
        assertEquals(64, rec.meta.sourceSha256.length)
    }

    @Test fun `sha256 covers the whole file, not just what the parser read`() = runTest {
        repo.import({ open() }, "mini_timeline.json")
        repo.saveProfile(profile(boston))
        val rec = repo.record.first { it != null }!!
        val expected = MessageDigest.getInstance("SHA-256").digest(open().use { it.readBytes() }).joinToString("") { "%02x".format(it) }
        assertEquals(expected, rec.meta.sourceSha256)
    }

    @Test fun `changing the profile re-derives without re-import`() = runTest {
        repo.import({ open() }, "mini_timeline.json")
        repo.saveProfile(profile(boston))
        val first = repo.record.first { it != null }!!
        val visitsBefore = db.visits().count()

        repo.saveProfile(profile(sf))
        val second = repo.record.first { it != null && it.profile.home != first.profile.home }!!
        assertNotEquals(first.profile.home, second.profile.home)
        assertEquals(LatLng(sf.lat, sf.lng), second.profile.home)
        assertEquals(visitsBefore, db.visits().count())
        assertEquals(first.visits.size, second.visits.size)
    }

    @Test fun `a profile change reports deriving but a place lookup landing does not`() = runTest {
        repo.import({ open() }, "mini_timeline.json")
        repo.saveProfile(profile(boston))
        val first = repo.record.first { it != null }!!
        assertEquals(Ready("Working out trips", 100), ready.ready.value)

        ready.clear()
        repo.saveProfile(profile(sf))
        repo.record.first { it != null && it.profile.home != first.profile.home }
        assertEquals(Ready("Working out trips", 100), ready.ready.value)

        ready.clear()
        val id = first.places.keys.first()
        repo.put(mapOf(id to Enrichment("Named stop", null, emptyList(), null, null)))
        repo.record.first { it != null && it.places[id]?.name == "Named stop" }
        assertNull(ready.ready.value)
    }

    @Test fun `pts round-trip through the blob codec`() {
        val pts = listOf(Pt(42.3632761, -71.0643721, 1_741_000_000L), Pt(-33.8688, 151.2093, 1_741_003_600L), Pt(0.0, 0.0, 0L))
        assertEquals(pts, decodePts(encodePts(pts)))
        assertEquals(emptyList<Pt>(), decodePts(encodePts(emptyList())))
    }

    private fun deviceVisit(start: Long) = VisitEntity(
        id = start.toInt(), start = start, end = start + 3600, tz = 0, placeId = "dev:drt2zp2", lat = 42.36, lng = -71.06,
        type = "UNKNOWN", p = 1.0, lvl = 0, city = null, cc = null, dist = null, source = "device",
    )

    @Test fun `re-import keeps device-recorded rows and drops the old import`() = runTest {
        repo.import({ open() }, "mini_timeline.json")
        val imported = db.visits().count()
        repo.insertDevice(listOf(deviceVisit(1_800_000_000L)), emptyList(), emptyList())
        assertEquals(imported + 1, db.visits().count())

        repo.import({ open() }, "mini_timeline.json")
        assertEquals(imported + 1, db.visits().count())
        assertEquals(1, db.visits().all().count { it.source == "device" })
    }

    @Test fun `device rows join the record and bump its version`() = runTest {
        repo.import({ open() }, "mini_timeline.json")
        repo.saveProfile(profile(boston))
        val before = repo.record.first { it != null }!!
        repo.insertDevice(listOf(deviceVisit(1_800_000_000L)), emptyList(), emptyList())
        val after = repo.record.first { it != null && it.visits.size != before.visits.size }!!
        assertEquals(before.visits.size + 1, after.visits.size)
        assertEquals(1_800_000_000L, after.visits.last().start)
        assertEquals(1_800_003_600L, after.meta.range.last)
    }

    @Test fun `device rows alone make a record, with no export ever imported`() = runTest {
        assertFalse(repo.hasImport.first())
        repo.insertDevice(listOf(deviceVisit(1_800_000_000L), deviceVisit(1_800_100_000L)), emptyList(), emptyList())
        assertTrue(repo.hasImport.first { it })
        repo.saveProfile(profile(boston))
        val rec = repo.record.first { it != null }!!
        assertEquals(2, rec.visits.size)
        assertEquals("", rec.meta.sourceFile)
        assertEquals(0, rec.meta.importReport.segmentsTotal)
        assertEquals(2, repo.deviceVisits.value)
    }

    @Test fun `refresh rebuilds from whatever Room holds now, and the bar reads Working out trips`() = runTest {
        repo.import({ open() }, "mini_timeline.json")
        repo.saveProfile(profile(boston))
        val before = repo.record.first { it != null }!!
        // A row that landed behind the repository's back, as the recorder's worker might leave it.
        db.visits().insertAll(listOf(deviceVisit(1_800_000_000L)))
        ready.clear()

        repo.refresh()
        val after = repo.record.first { it != null && it !== before }!!
        assertEquals(before.visits.size + 1, after.visits.size)
        assertEquals(Ready("Working out trips", 100), ready.ready.value)
    }

    @Test fun `reset clears everything`() = runTest {
        repo.import({ open() }, "mini_timeline.json")
        repo.saveProfile(profile(boston))
        repo.record.first { it != null }

        repo.insertDevice(listOf(deviceVisit(1_800_000_000L)), emptyList(), emptyList())
        repo.reset()
        assertFalse(repo.hasImport.first { !it })
        assertNull(repo.record.first { it == null })
        assertNull(repo.profile.first { it == null })
        assertEquals(0, db.visits().count())
    }
}
