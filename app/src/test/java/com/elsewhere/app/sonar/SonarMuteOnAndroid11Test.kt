package com.elsewhere.app.sonar

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.crew.CrewPublisher
import com.elsewhere.app.crew.CrewStore
import com.elsewhere.app.crew.Crypto
import com.elsewhere.app.crew.Friend
import com.elsewhere.app.crew.Grants
import com.elsewhere.app.crew.RelayClient
import com.elsewhere.app.crew.RoomHub
import com.elsewhere.app.crew.Rooms
import com.elsewhere.app.crew.ShareLength
import com.elsewhere.app.data.db.ElsewhereDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId
import java.util.UUID

/** The midnight maths must run on the oldest phone we ship to, where java.time is still API 26's. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SonarMuteOnAndroid11Test {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
    private val now = 1_763_222_520_000L // 2025-11-15 16:02 UTC
    private val lisbon = ZoneId.of("Europe/Lisbon")

    @After fun tearDown() { scope.cancel(); dir.deleteRecursively() }

    @Test fun `mute for today and share until tonight both end at local midnight`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        val store = CrewStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "crew.preferences_pb") })
        val rooms = object : Rooms { override suspend fun send(friend: Friend, plaintext: String) = true; override fun forget(deviceId: String) = Unit }
        val publisher = CrewPublisher(store, rooms, MutableSharedFlow(), db, { null }, scope) { now }
        val hub = RoomHub(store, RelayClient(OkHttpClient(), { "" }, { store.keys() }), scope)
        val engine = SonarEngine(store, hub, publisher, db, object : SonarAlerts { override fun nearby(friend: Friend) = Unit }, scope, { now }, { lisbon })
        store.addFriend(Friend("M", "Marta", Crypto.b64(ByteArray(32)), Crypto.b64(ByteArray(32)), "PAIRM", Crypto.b64(ByteArray(32)), 0L, Grants(sonar = true)))

        engine.muteToday("M")
        val midnight = 1_763_251_200_000L // 2025-11-16 00:00 Europe/Lisbon (UTC+0 in November)
        assertEquals(midnight, store.friend("M")!!.mutedUntil)
        assertEquals(midnight, ShareLength.TONIGHT.until(now, lisbon))
        db.close()
    }
}
