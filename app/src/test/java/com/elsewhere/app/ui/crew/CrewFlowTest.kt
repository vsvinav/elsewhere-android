package com.elsewhere.app.ui.crew

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.Fakes
import com.elsewhere.app.crew.CrewPublisher
import com.elsewhere.app.crew.CrewStore
import com.elsewhere.app.crew.Crypto
import com.elsewhere.app.crew.DeviceKeys
import com.elsewhere.app.crew.Friend
import com.elsewhere.app.crew.Grants
import com.elsewhere.app.crew.Ping
import com.elsewhere.app.crew.Pairing
import com.elsewhere.app.crew.REVOKE_JSON
import com.elsewhere.app.crew.RelayClient
import com.elsewhere.app.crew.RoomHub
import com.elsewhere.app.crew.Rooms
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.sonar.SonarAlerts
import com.elsewhere.app.sonar.SonarEngine
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.data.SecretBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Pairing end to end against a stub relay: type a code, confirm, see the friend, grant Sonar, remove. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrewFlowTest {
    @get:Rule val rule = createComposeRule()

    private class FakeRooms : Rooms {
        val sent = ArrayList<Pair<String, String>>()
        override suspend fun send(friend: Friend, plaintext: String): Boolean { sent += friend.deviceId to plaintext; return true }
        override fun forget(deviceId: String) = Unit
    }

    private val server = MockWebServer()
    private val rooms = FakeRooms()
    private lateinit var store: CrewStore
    private lateinit var settings: SettingsStore
    private lateinit var scope: CoroutineScope
    private lateinit var db: ElsewhereDb
    private lateinit var vm: CrewViewModel
    private val martaKeys = DeviceKeys.generate()
    private val martaSign = Crypto.hex(martaKeys.signPub)
    private val martaKx = Crypto.hex(martaKeys.kxPub)
    private val martaKxSig = Crypto.kxSig(martaKeys.signPriv, martaKeys.kxPub)

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.endsWith("/claim") -> MockResponse().setBody("""{"signPub":"$martaSign","kxPub":"$martaKx","name":"Marta","kxSig":"$martaKxSig"}""")
                request.method == "POST" -> MockResponse().setResponseCode(200)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val ctx = ApplicationProvider.getApplicationContext() as Context
        val dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        store = CrewStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "crew.preferences_pb") }, SecretBox.inMemory())
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }, SecretBox.inMemory())
        val relay = RelayClient(OkHttpClient(), { server.url("/").toString() }, { store.keys() })
        val publisher = CrewPublisher(store, rooms, MutableSharedFlow(), db, { null }, scope)
        val hub = RoomHub(store, relay, scope)
        val engine = SonarEngine(store, hub, publisher, db, object : SonarAlerts { override fun nearby(friend: Friend, test: Boolean, sent: Boolean) = Unit }, scope)
        vm = CrewViewModel(store, Pairing(store, relay, { "Me" }), publisher, hub, engine, settings, Fakes.Control(Recording(enabled = true)))
        vm.pollMs = 60_000L
    }

    @After fun tearDown() { runCatching { server.shutdown() }; db.close() }

    /** Background work lands on the main looper, which Robolectric only drains when asked. */
    private fun await(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out: ui=${vm.ui.value}")
            Thread.sleep(25); shadowOf(Looper.getMainLooper()).idle(); rule.waitForIdle()
        }
    }

    private fun node(text: String) = rule.onNodeWithText(text).performScrollTo()
    private fun setContent() = rule.setContent { ElsewhereTheme { CrewRoute(onBack = {}, vm = vm) } }

    @Test fun `a typed code is claimed, confirmed and the friend's grants and removal round-trip`() {
        setContent()
        await { vm.ui.value?.code != null }
        node("PT-XXX-XXXX").performTextInput("PT-MRT-7Q4K")
        node("PAIR").performClick()
        await { vm.ui.value?.pair is PairState.Confirm }
        rule.onNodeWithText("PAIR WITH MARTA?").assertIsDisplayed()
        rule.onNode(hasText("PAIR") and hasAnyAncestor(isDialog())).performClick()
        await { vm.ui.value?.friends?.size == 1 }
        val marta = runBlocking { store.friendsNow().single() }
        assertEquals("Marta", marta.name)
        assertEquals(Crypto.deviceId(Crypto.unhex(martaSign)), marta.deviceId)

        node("MARTA").performClick()
        node("Sonar").performClick()
        await { vm.ui.value?.friends?.single()?.grants?.sonar == true }
        assertEquals("Sonar implies city-level live", Grants(liveCity = true, sonar = true), runBlocking { store.friend(marta.deviceId)!!.grants })
        node("Sonar").performClick()
        await { vm.ui.value?.friends?.single()?.grants?.sonar == false }
        assertEquals("turning Sonar off leaves Live · city alone", Grants(liveCity = true), runBlocking { store.friend(marta.deviceId)!!.grants })

        node("REMOVE").performClick()
        await { vm.ui.value?.friends?.isEmpty() == true }
        assertEquals(listOf(marta.deviceId to REVOKE_JSON), rooms.sent)
    }

    @Test fun `test sonar shows only with sonar on, sends one test ping with a hit, and rings here`() {
        val marta = Friend("M", "Marta", Crypto.hex(ByteArray(32) { 1 }), Crypto.hex(ByteArray(32) { 2 }), "PAIRM", Crypto.b64(ByteArray(32)), 0L, Grants(liveCity = true))
        runBlocking { store.addFriend(marta) }
        setContent()
        await { vm.ui.value?.friends?.size == 1 }
        node("MARTA").performClick()
        rule.onNodeWithText("TEST SONAR").assertDoesNotExist()
        rule.onNodeWithText(TEST_SONAR_COPY).assertDoesNotExist()
        node("Sonar").performClick()
        await { vm.ui.value?.friends?.single()?.grants?.sonar == true }
        node(TEST_SONAR_COPY).assertIsDisplayed()
        node("TEST SONAR").performClick()
        await { rooms.sent.size == 1 && vm.hits.value.contains("M") }
        val (to, plain) = rooms.sent.single()
        assertEquals("M", to)
        val p = Ping.decode(plain)
        assertTrue(p.test)
        assertTrue(p.hit!!.id.matches(Regex("[0-9a-f]{8}")))
        assertTrue(runBlocking { store.debounce(listOf("PAIRM")) }.lastHitByPair.isEmpty())
    }

    @Test fun `without a relay the code bill tells you where to set one`() {
        runBlocking { settings.saveSettings(settings.settings.first().copy(relayUrl = "")) }
        setContent()
        await { vm.ui.value?.relaySet == false }
        node(NO_RELAY).assertIsDisplayed()
    }
}
