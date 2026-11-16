package com.elsewhere.app.crew

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.ZoneId
import java.util.UUID

class CrewStoreTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: CrewStore

    @BeforeEach fun setUp() {
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        store = CrewStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "crew.preferences_pb") })
    }

    @AfterEach fun tearDown() { scope.cancel(); dir.deleteRecursively() }

    private fun friend(id: String, name: String = "Marta") = Friend(
        deviceId = id, name = name, signPub = Crypto.b64(ByteArray(32) { 1 }), kxPub = Crypto.b64(ByteArray(32) { 2 }),
        pairId = "PAIR$id", pairKey = Crypto.b64(ByteArray(32) { 3 }), addedAt = 1_000L,
    )

    @Test fun `keys are minted once and then reused`() = runTest {
        val a = store.keys(); val b = store.keys()
        assertEquals(a, b)
        assertEquals(16, a.deviceId.length)
    }

    @Test fun `friends are added, updated, and removed`() = runTest {
        assertEquals(emptyList<Friend>(), store.friendsNow())
        store.addFriend(friend("A")); store.addFriend(friend("B", "Ásta"))
        assertEquals(listOf("A", "B"), store.friendsNow().map { it.deviceId })
        store.setGrants("A", Grants(liveCity = true, sonar = true))
        assertEquals(Grants(liveCity = true, sonar = true), store.friend("A")!!.grants)
        assertEquals(Grants(), store.friend("B")!!.grants)
        store.mute("A", 5_000L)
        assertTrue(store.friend("A")!!.muted(4_000L)); assertFalse(store.friend("A")!!.muted(5_000L))
        store.addFriend(friend("A", "Marta R."))
        assertEquals("Marta R.", store.friend("A")!!.name)
        assertEquals(2, store.friendsNow().size)
        store.removeFriend("A")
        assertEquals(listOf("B"), store.friendsNow().map { it.deviceId })
    }

    @Test fun `the open code and the share window persist`() = runTest {
        assertNull(store.code.first())
        store.saveCode(ShareCode("PT-MRT-7Q4K", 9L))
        assertEquals(ShareCode("PT-MRT-7Q4K", 9L), store.code.first())
        store.saveCode(null); assertNull(store.code.first())
        val s = ShareSession(setOf("A"), startedAt = 0L, until = 3_600_000L)
        store.startShare(s)
        assertEquals(s, store.shareNow())
        store.stopShare(); assertNull(store.shareNow())
    }

    @Test fun `sonar debounce lives per pair`() = runTest {
        store.recordHit("P1", 42L)
        val d = store.debounce(listOf("P1", "P2"))
        assertEquals(mapOf("P1" to 42L), d.lastHitByPair)
    }

    @Test fun `share lengths`() {
        val zone = ZoneId.of("Europe/Lisbon")
        val now = 1_763_222_520_000L
        assertEquals(now + 3_600_000L, ShareLength.HOUR.until(now, zone))
        assertEquals(Long.MAX_VALUE, ShareLength.UNTIL_STOP.until(now, zone))
        val tonight = ShareLength.TONIGHT.until(now, zone)
        assertTrue(tonight > now && tonight - now <= 86_400_000L)
        assertEquals(0L, (tonight / 1000) % 60)
        val s = ShareSession(setOf("A"), now, tonight)
        assertEquals("42 min", s.elapsedLabel(now + 42 * 60_000L))
        assertEquals("1 h 5 min", s.elapsedLabel(now + 65 * 60_000L))
        assertTrue(s.active(now)); assertFalse(s.active(tonight))
    }

    @Test fun `friend helpers`() {
        val f = friend("A", "marta")
        assertEquals("M", f.initial)
        assertEquals(8, f.fingerprint.length)
        assertTrue(Grants(sonar = true).any); assertFalse(Grants().any)
    }

    @Test fun `the notification line names who hears you and at what grade`() {
        val a = friend("A").copy(grants = Grants(liveCity = true))
        val b = friend("B", "Ásta").copy(grants = Grants(livePrecise = true))
        val s = ShareSession(setOf("A", "B"), startedAt = 0L, until = 3_600_000L)
        assertEquals("Sharing precise with Marta, Ásta · 42 min", s.line(listOf(a, b), 42 * 60_000L))
        assertEquals("Sharing city-level with Marta · 42 min", s.copy(friendIds = setOf("A")).line(listOf(a, b), 42 * 60_000L))
        assertNull(s.line(listOf(a, b), 3_600_001L))
        assertNull(s.line(emptyList(), 60_000L))
    }
}
