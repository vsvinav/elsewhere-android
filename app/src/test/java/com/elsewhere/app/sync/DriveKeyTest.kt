package com.elsewhere.app.sync

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

/** The one file in the Drive app folder: found, created, raced and deleted against a scripted Drive. */
class DriveKeyTest {
    private val server = MockWebServer()
    private lateinit var drive: DriveKey
    private val key = ByteArray(32) { (it * 3).toByte() }
    private val b64 = Base64.getEncoder().encodeToString(key)

    @BeforeEach fun setUp() { server.start(); drive = DriveKey(OkHttpClient(), server.url("/").toString().trimEnd('/')) }
    @AfterEach fun tearDown() { runCatching { server.shutdown() } }

    @Test fun `an existing file is listed in the app folder and read back as the key`() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"f1","createdTime":"2024-01-02T00:00:00.000Z"}]}"""))
        server.enqueue(MockResponse().setBody(b64))
        assertArrayEquals(key, drive.fetchOrCreate("tok"))
        val list = server.takeRequest()
        assertEquals("GET", list.method)
        assertEquals("/drive/v3/files?spaces=appDataFolder&q=name='elsewhere.key'&fields=files(id,createdTime)", java.net.URLDecoder.decode(list.path!!, "UTF-8"))
        assertEquals("Bearer tok", list.getHeader("Authorization"))
        val get = server.takeRequest()
        assertEquals("/drive/v3/files/f1?alt=media", get.path)
        assertEquals(2, server.requestCount)
    }

    @Test fun `no file means a fresh 32-byte key goes up as a multipart upload`() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[]}"""))
        server.enqueue(MockResponse().setBody("""{"id":"new"}"""))
        val k = drive.fetchOrCreate("tok")
        assertEquals(32, k.size)
        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/upload/drive/v3/files?uploadType=multipart", post.path)
        assertTrue(post.getHeader("Content-Type")!!.startsWith("multipart/related"))
        val body = post.body.readUtf8()
        assertTrue(body.contains(""""name":"elsewhere.key""""))
        assertTrue(body.contains(""""parents":["appDataFolder"]"""))
        assertTrue(body.contains("Content-Type: application/octet-stream"))
        assertTrue(body.contains(Base64.getEncoder().encodeToString(k)))
    }

    @Test fun `two files from a race, the oldest wins and the newer one is deleted`() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"newer","createdTime":"2024-03-01T00:00:00.000Z"},{"id":"older","createdTime":"2024-01-02T00:00:00.000Z"}]}"""))
        server.enqueue(MockResponse().setBody(b64))
        server.enqueue(MockResponse().setResponseCode(204))
        assertArrayEquals(key, drive.fetchOrCreate("tok"))
        server.takeRequest()
        assertEquals("/drive/v3/files/older?alt=media", server.takeRequest().path)
        val del = server.takeRequest()
        assertEquals("DELETE", del.method)
        assertEquals("/drive/v3/files/newer", del.path)
    }

    @Test fun `delete removes every copy and a Drive fault is a DriveException with its status`() = runTest {
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"a","createdTime":"2024-01-02T00:00:00.000Z"},{"id":"b","createdTime":"2024-01-03T00:00:00.000Z"}]}"""))
        server.enqueue(MockResponse().setResponseCode(204)); server.enqueue(MockResponse().setResponseCode(204))
        drive.delete("tok")
        server.takeRequest()
        assertEquals(setOf("/drive/v3/files/a", "/drive/v3/files/b"), setOf(server.takeRequest().path, server.takeRequest().path))

        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"insufficient scope"}}"""))
        val e = runCatching { drive.fetchOrCreate("tok") }.exceptionOrNull()
        assertTrue(e is DriveException && e.code == 403, "$e")
    }
}
