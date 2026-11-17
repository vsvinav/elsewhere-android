package com.elsewhere.app.ui.photos

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.photos.PhotoFiles
import com.elsewhere.app.photos.PhotoStore
import com.elsewhere.app.data.SecretBox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnapViewModelTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: ElsewhereDb
    private lateinit var scope: CoroutineScope
    private lateinit var dir: File
    private lateinit var store: PhotoStore
    private lateinit var files: PhotoFiles

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        resetFileProvider()
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
        files = PhotoFiles(ctx)
        store = PhotoStore(ctx, db, files, SettingsStore(prefs, SecretBox.inMemory()))
        runBlocking { store.markPrivacySeen() }
    }

    @After fun tearDown() { Dispatchers.resetMain(); scope.cancel(); db.close(); dir.deleteRecursively(); resetFileProvider() }

    // FileProvider caches its root strategy per JVM; each Robolectric test gets a fresh cache dir, so drop it around this test.
    private fun resetFileProvider() {
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    @Test fun `a caption handed to begin lands on the photo once it is picked`() {
        val handle = SavedStateHandle()
        val vm = SnapViewModel(store, files, handle)
        val launched = CompletableDeferred<Source>()
        vm.begin(SnapPlace("sonar:h1", "Lisbon", 38.72, -9.14, "Lisbon"), Source.CAMERA, caption = "with Marta") { launched.complete(it) }
        runBlocking { withTimeout(5_000) { launched.await() } }
        assertEquals("with Marta", SnapViewModel(store, files, handle).pendingCaption)
        val f = File(ctx.cacheDir, "pic-${UUID.randomUUID()}.jpg")
        Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFE9B531.toInt()) }.let { b -> f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        vm.picked(Uri.fromFile(f))
        val open = runBlocking { withTimeout(5_000) { while (vm.open == null) kotlinx.coroutines.delay(20); vm.open!! } }
        assertEquals("with Marta", open.photo.caption)
        assertEquals("with Marta", runBlocking { db.photos().get(open.photo.id)!!.caption })
        assertNull(vm.pendingCaption)
    }

    @Test fun `the place and camera file behind an open camera survive the view model being rebuilt`() {
        val handle = SavedStateHandle()
        val place = SnapPlace("lis-chafariz", "Chafariz d'El Rei", 38.713, -9.131, "Lisbon", "Alfama")
        val first = SnapViewModel(store, files, handle)
        val launched = CompletableDeferred<Source>()
        first.begin(place, Source.CAMERA) { launched.complete(it) }
        assertEquals(Source.CAMERA, runBlocking { withTimeout(5_000) { launched.await() } })
        val uri = first.cameraTarget()
        assertNotNull(first.cameraFile)

        // Process death: a new view model from the same saved state.
        val second = SnapViewModel(store, files, handle)
        assertEquals(place, second.pending)
        assertEquals(first.cameraFile, second.cameraFile)
        assertEquals(uri.lastPathSegment, second.cameraFile!!.name)

        second.picked(null)
        assertNull(second.pending); assertNull(second.cameraFile)
        assertNull(SnapViewModel(store, files, handle).pending)
    }
}
