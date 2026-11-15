package com.elsewhere.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.decodePts
import com.elsewhere.app.data.db.encodePts
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordRepositoryTest {
    private lateinit var db: ElsewhereDb
    private lateinit var scope: CoroutineScope
    private lateinit var repo: RecordRepository
    private lateinit var dir: File

    private val boston = Home(42.3632761, -71.0643721, "Boston", "US", 0, null)
    private val sf = Home(37.7749, -122.4194, "San Francisco", "US", 0, null)
    private fun profile(home: Home) = Profile(null, Units.KM, listOf(home), emptyList())

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
        repo = RecordRepository(db, SettingsStore(store), CitiesLoader(ctx), scope)
    }

    @After fun tearDown() { scope.cancel(); db.close(); dir.deleteRecursively() }

    private fun open() = javaClass.classLoader!!.getResourceAsStream("mini_timeline.json")!!

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

    @Test fun `pts round-trip through the blob codec`() {
        val pts = listOf(Pt(42.3632761, -71.0643721, 1_741_000_000L), Pt(-33.8688, 151.2093, 1_741_003_600L), Pt(0.0, 0.0, 0L))
        assertEquals(pts, decodePts(encodePts(pts)))
        assertEquals(emptyList<Pt>(), decodePts(encodePts(emptyList())))
    }

    @Test fun `reset clears everything`() = runTest {
        repo.import({ open() }, "mini_timeline.json")
        repo.saveProfile(profile(boston))
        repo.record.first { it != null }

        repo.reset()
        assertFalse(repo.hasImport.first { !it })
        assertNull(repo.record.first { it == null })
        assertNull(repo.profile.first { it == null })
        assertEquals(0, db.visits().count())
    }
}
