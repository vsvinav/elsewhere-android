package com.elsewhere.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Base64
import java.util.UUID

class SecretBoxTest {
    private val box = SecretBox.inMemory()
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope

    @BeforeEach fun up() {
        dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterEach fun down() { scope.cancel(); dir.deleteRecursively() }

    @Test fun `seal and open round-trip, and the ciphertext is not the text`() {
        val sealed = box.seal("tok_secret")
        assertNotEquals("tok_secret", sealed)
        assertTrue(sealed.indexOf("tok_secret") < 0)
        assertEquals("tok_secret", box.open(sealed))
        assertNotEquals(sealed, box.seal("tok_secret"), "every seal uses a fresh iv")
        assertEquals("", box.open(box.seal("")))
    }

    @Test fun `a tampered or foreign value opens as null`() {
        val bytes = Base64.getDecoder().decode(box.seal("tok_secret"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull(box.open(Base64.getEncoder().encodeToString(bytes)))
        assertNull(box.open("plain text"))
        assertNull(box.open("{\"friends\":[]}"))
        assertNull(box.open(SecretBox.inMemory().seal("tok_secret")), "another key cannot open it")
    }

    @Test fun `legacy plaintext reads as itself and is sealed in place`() = runTest {
        val key = stringPreferencesKey("k"); val other = stringPreferencesKey("o")
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "s.preferences_pb") }
        store.edit { it[key] = "legacy"; it[other] = "untouched" }
        assertEquals("legacy", box.openOrPlain("legacy"))
        store.sealLegacy(box, key)
        val p = store.data.first()
        assertNotEquals("legacy", p[key])
        assertEquals("legacy", box.open(p[key]!!))
        assertEquals("untouched", p[other])
        val sealedOnce = p[key]
        store.sealLegacy(box, key)
        assertEquals(sealedOnce, store.data.first()[key], "an already sealed value is left alone")
    }
}
