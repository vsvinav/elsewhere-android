package com.elsewhere.app.record

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.data.CitiesLoader
import com.elsewhere.app.data.RecordRepository
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.ingest.record.Fix
import com.elsewhere.app.data.SecretBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
class RecorderTest {
    private lateinit var db: ElsewhereDb
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    private lateinit var repo: RecordRepository
    private lateinit var cities: CitiesLoader
    private lateinit var dir: File
    private val t0 = 1_800_000_000L
    private val boston = 42.3600 to -71.0600
    private val north = 42.3700 to -71.0600

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }, SecretBox.inMemory())
        cities = CitiesLoader(ctx)
        repo = RecordRepository(db, settings, cities, scope)
    }

    @After fun tearDown() { scope.cancel(); db.close(); dir.deleteRecursively() }

    private fun recorder() = Recorder(db, repo, settings, cities, tzAt = { -300 })
    private fun fix(ts: Long, at: Pair<Double, Double>, dLat: Double = 0.0) = Fix(ts, at.first + dLat, at.second, 10f)

    @Test fun `every fix is kept raw and stays become device visits with a city`() = runTest {
        val r = recorder()
        for (i in 0..10) r.onFix(fix(t0 + i * 60, boston), batteryPct = 80)
        for (i in 1..13) r.onFix(fix(t0 + 10 * 60 + i * 60, boston, dLat = 0.0007 * i))
        for (i in 0..10) r.onFix(fix(t0 + 25 * 60 + i * 60, north))
        assertEquals(35, db.rawFixes().countAll())
        assertEquals(80, db.rawFixes().since(0).first().batteryPct)

        val visits = db.visits().all()
        assertEquals(1, visits.size)
        assertEquals("device", visits[0].source)
        assertEquals("Boston", visits[0].city); assertEquals("US", visits[0].cc)
        assertNull(visits[0].dist)
        val legs = db.activities().all()
        assertEquals(1, legs.size)
        assertEquals("device", legs[0].source); assertEquals("Boston", legs[0].eCity)
        assertEquals(1, db.paths().all().size)
    }

    @Test fun `a new recorder resumes from the persisted segmenter state`() = runTest {
        val r = recorder()
        for (i in 0..10) r.onFix(fix(t0 + i * 60, boston))
        val r2 = recorder()
        r2.onFix(fix(t0 + 11 * 60, north))
        assertEquals(t0, db.visits().all().single().start)
    }

    @Test fun `sweep closes a stale stay and prunes old raw fixes`() = runTest {
        val r = recorder()
        for (i in 0..10) r.onFix(fix(t0 + i * 60, boston))
        r.sweep(now = t0 + 10 * 60 + 30 * 60)
        assertEquals(0, db.visits().count())
        r.sweep(now = t0 + 3 * 3600)
        assertEquals(1, db.visits().count())
        assertEquals(t0 + 10 * 60, db.visits().all().single().end)
        r.sweep(now = t0 + 91 * 86400)
        assertEquals(0, db.rawFixes().countAll())
    }

    @Test fun `stats count fixes and stays`() = runTest {
        val r = recorder()
        for (i in 0..10) r.onFix(fix(t0 + i * 60, boston))
        r.onFix(fix(t0 + 11 * 60, north))
        val s = r.stats()
        assertEquals(12, s.fixes); assertEquals(1, s.stays); assertEquals(t0 + 11 * 60, s.lastFixTs)
        assertTrue(r.stats().fixes > 0)
    }
}
