package com.elsewhere.app.ingest

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Twin of web/src/ingest/wikimedia.test.ts, plus the fetch. */
class WikimediaTest {
    private val server = MockWebServer()
    private lateinit var wiki: Wikimedia

    @BeforeEach fun up() { server.start(); wiki = Wikimedia(OkHttpClient(), server.url("/").toString().trimEnd('/'), sleep = {}) }
    @AfterEach fun down() = server.shutdown()

    @Test fun `pickPhoto skips maps, plans and vector files for the first real photograph`() {
        val pages = listOf(
            WikiPage("File:Boston map.svg", listOf(ImageInfo(thumburl = "a"))),
            WikiPage("File:Station plan.png", listOf(ImageInfo(thumburl = "b"))),
            WikiPage("File:North Station facade.jpg", listOf(ImageInfo(thumburl = "c"))),
        )
        assertEquals("c", pickPhoto(pages))
        assertNull(pickPhoto(emptyList()))
        assertEquals("full", pickPhoto(listOf(WikiPage("File:x.jpg", listOf(ImageInfo(url = "full"))))))
    }

    @Test fun `a geosearch near the point, cached, null on failure`() = runTest {
        server.enqueue(MockResponse().setBody("""{"query":{"pages":{"1":{"title":"File:Boston map.svg","imageinfo":[{"thumburl":"a"}]},"2":{"title":"File:Hall.jpg","imageinfo":[{"thumburl":"b","url":"B"}]}}}}"""))
        assertEquals("b", wiki.photo(42.36, -71.06))
        val path = server.takeRequest().path!!
        assertTrue(path.contains("generator=geosearch") && path.contains("ggscoord=42.36%7C-71.06") && path.contains("ggsradius=120"), path)
        assertEquals("b", wiki.photo(42.36001, -71.06001))
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(wiki.photo(0.0, 0.0))
    }

    @Test fun `a lookup cancelled mid-flight is not remembered as "no photo"`() {
        server.enqueue(MockResponse().setBody("""{"query":{"pages":{}}}""").setHeadersDelay(2, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setBody("""{"query":{"pages":{"2":{"title":"File:Hall.jpg","imageinfo":[{"thumburl":"b"}]}}}}"""))
        runBlocking {
            val job = launch { wiki.photo(1.0, 1.0) }
            delay(300); job.cancel(); job.join()
            assertEquals("b", wiki.photo(1.0, 1.0))
        }
        assertEquals(2, server.requestCount)
    }
}
