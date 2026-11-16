package com.elsewhere.app.record

import android.Manifest
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.data.CitiesLoader
import com.elsewhere.app.data.RecordRepository
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.ingest.record.Fix
import com.elsewhere.app.data.SecretBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordControllerTest {
    private class Gate(var location: Boolean = true) : PermissionGate {
        override val sdk = 34
        override fun granted(permission: String) = location || permission != Manifest.permission.ACCESS_FINE_LOCATION
        override fun ignoringBatteryOptimizations() = true
    }

    private class Link : ServiceLink {
        val calls = ArrayList<String>()
        override fun start() { calls += "start" }
        override fun stop() { calls += "stop" }
        override fun pause() { calls += "pause" }
        override fun schedule() { calls += "schedule" }
        override fun cancelWork() { calls += "cancelWork" }
        override fun resumeAt(seconds: Long) { calls += "resumeAt" }
    }

    private lateinit var db: ElsewhereDb
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    private lateinit var recorder: Recorder
    private lateinit var dir: File
    private val gate = Gate()
    private val link = Link()
    private val t0 = 1_800_000_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }, SecretBox.inMemory())
        val cities = CitiesLoader(ctx)
        recorder = Recorder(db, RecordRepository(db, settings, cities, scope), settings, cities, tzAt = { 0 })
    }

    @After fun tearDown() { scope.cancel(); db.close(); dir.deleteRecursively() }

    private fun controller() = RecordController(settings, recorder, gate, link)

    @Test fun `stop writes the open confirmed stay before the service goes`() = runTest {
        val c = controller()
        c.start()
        for (i in 0..10) recorder.onFix(Fix(t0 + i * 60, 42.36, -71.06, 10f))
        assertEquals(0, db.visits().count())
        c.stop()
        assertEquals(1, db.visits().count())
        assertEquals(t0 + 600, db.visits().all().single().end)
        assertEquals(listOf("start", "schedule", "stop", "cancelWork"), link.calls)
    }

    @Test fun `pause keeps the service alive and books the resume`() = runTest {
        val c = controller()
        c.start()
        c.pause(3600)
        assertEquals(listOf("start", "schedule", "pause", "resumeAt"), link.calls)
        assertFalse(settings.recording.first().active(System.currentTimeMillis() / 1000))
    }

    @Test fun `resume without location permission switches recording off instead of starting`() = runTest {
        settings.saveRecording(Recording(enabled = true, since = t0))
        gate.location = false
        controller().resumeIfEnabled()
        assertEquals(emptyList<String>(), link.calls)
        assertFalse(settings.recording.first().enabled)
    }

    @Test fun `resume with permission starts the service`() = runTest {
        settings.saveRecording(Recording(enabled = true, since = t0))
        controller().resumeIfEnabled()
        assertEquals(listOf("start"), link.calls)
    }
}
