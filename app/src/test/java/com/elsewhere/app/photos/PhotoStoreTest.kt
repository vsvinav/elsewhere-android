package com.elsewhere.app.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.RawFixEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class PhotoStoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: ElsewhereDb
    private lateinit var scope: CoroutineScope
    private lateinit var dir: File
    private lateinit var files: PhotoFiles
    private lateinit var store: PhotoStore
    private val now = 1763208000000L

    @Before fun setUp() {
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
        files = PhotoFiles(ctx)
        store = PhotoStore(ctx, db, files, SettingsStore(prefs)).apply { clock = { now } }
    }

    @After fun tearDown() { scope.cancel(); db.close(); files.clear(); dir.deleteRecursively() }

    /** A solid 3000×2000 JPEG in the cache, handed over as a file URI like the camera would. */
    private fun picture(): Uri {
        val f = File(ctx.cacheDir, "pic-${UUID.randomUUID()}.jpg")
        val b = Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFE9B531.toInt()) }
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(f)
    }

    @Test fun `a snap from a place is kept at 2048 px with a thumbnail and the place's coordinates`() = runBlocking {
        val row = store.snap(picture(), PlaceCtx("lis-chafariz", 38.713, -9.131, "Lisbon"))
        assertNotNull(row)
        assertEquals(listOf(row), store.forPlace("lis-chafariz"))
        assertEquals("lis-chafariz", row!!.placeId); assertEquals("Lisbon", row.city); assertEquals(now, row.ts)
        assertEquals(38.713, row.lat!!, 1e-9); assertEquals(-9.131, row.lng!!, 1e-9)
        val o = BitmapFactory.decodeFile(files.original(row.id).path)
        assertEquals(PHOTO_MAX_PX, maxOf(o.width, o.height))
        val t = BitmapFactory.decodeFile(files.thumb(row.id).path)
        assertEquals(THUMB_MAX_PX, maxOf(t.width, t.height))
    }

    @Test fun `without a place a fresh fix places it and a stale one leaves it unplaced`() = runBlocking {
        db.rawFixes().insertAll(listOf(RawFixEntity(ts = now - 60_000, lat = 1.0, lng = 2.0, accuracyM = 5f, speedMps = null, altitude = null, activity = null, batteryPct = null)))
        val fresh = store.snap(picture(), null)!!
        assertEquals(1.0, fresh.lat!!, 1e-9); assertNull(fresh.placeId)
        db.rawFixes().clear()
        db.rawFixes().insertAll(listOf(RawFixEntity(ts = now - FIX_MAX_AGE_MS - 1, lat = 1.0, lng = 2.0, accuracyM = 5f, speedMps = null, altitude = null, activity = null, batteryPct = null)))
        val stale = store.snap(picture(), null)!!
        assertNull(stale.lat); assertNull(stale.lng); assertEquals(now, stale.ts)
    }

    @Test fun `caption is clipped to 80 characters and delete removes the row and both files`() = runBlocking {
        val row = store.snap(picture(), null)!!
        store.caption(row.id, "x".repeat(100))
        assertEquals(80, db.photos().get(row.id)!!.caption!!.length)
        assertTrue(files.original(row.id).exists()); assertTrue(files.thumb(row.id).exists())
        store.delete(row.id)
        assertTrue(store.list().isEmpty())
        assertFalse(files.original(row.id).exists()); assertFalse(files.thumb(row.id).exists())
    }

    @Test fun `the privacy note is shown once`() = runBlocking {
        assertFalse(store.privacySeen())
        store.markPrivacySeen()
        assertTrue(store.privacySeen())
    }
}
