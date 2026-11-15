package com.elsewhere.app.ingest

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class KeyCheckTest {
    private val server = MockWebServer()
    private lateinit var check: KeyCheck

    @BeforeEach fun up() {
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        check = KeyCheck(OkHttpClient.Builder().callTimeout(500, TimeUnit.MILLISECONDS).build(), placesBase = base, tilesBase = base)
    }
    @AfterEach fun down() = server.shutdown()

    private fun googleError(status: String, message: String) =
        MockResponse().setResponseCode(403).setBody("""{"error":{"code":403,"message":"$message","status":"$status"}}""")

    /** The two probes run in parallel, so answers are routed by path rather than queued. */
    private fun serve(places: MockResponse, tiles: MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if (request.path!!.startsWith("/v1/places/")) places else tiles
        }
    }

    @Test fun `a probe is given six seconds unless the caller set its own ceiling`() {
        assertEquals(6_000L, PROBE_TIMEOUT_MS)
        assertEquals(6_000, KeyCheck(OkHttpClient()).client.callTimeoutMillis)
        assertEquals(500, check.client.callTimeoutMillis)
    }

    @Test fun `both probes answering 200 make an accepted key`() = runTest {
        serve(MockResponse().setBody("""{"id":"ChIJN1t_tDeuEmsRUsoyG83frY4"}"""), MockResponse().setBody("""{"session":"abc","expiry":"1700000000","tileWidth":256,"tileHeight":256,"imageFormat":"png"}"""))
        val r = check.run("AIza-ok")
        assertEquals(KeyReport(Probe.Ok, Probe.Ok), r)
        assertTrue(r.accepted)
        val reqs = List(2) { server.takeRequest() }
        val places = reqs.first { it.path!!.startsWith("/v1/places/") }
        assertEquals("/v1/places/ChIJN1t_tDeuEmsRUsoyG83frY4", places.path)
        assertEquals("AIza-ok", places.getHeader("X-Goog-Api-Key"))
        assertEquals("id", places.getHeader("X-Goog-FieldMask"))
        val tiles = reqs.first { it.path!!.startsWith("/v1/createSession") }
        assertEquals("POST", tiles.method)
        assertEquals("/v1/createSession?key=AIza-ok", tiles.path)
        assertTrue(tiles.body.readUtf8().contains("\"mapType\":\"roadmap\""))
    }

    @Test fun `with the Maps SDK built in, Map Tiles need not answer but Places still must`() {
        val no = Probe.Failed("Map Tiles API has not been used", "PERMISSION_DENIED", null)
        assertTrue(KeyReport(Probe.Ok, no, sdk = true).accepted)
        assertEquals(false, KeyReport(Probe.Ok, no, sdk = false).accepted)
        assertEquals(false, KeyReport(no, Probe.Ok, sdk = true).accepted)
    }

    @Test fun `a referrer-restricted key is explained`() = runTest {
        serve(googleError("PERMISSION_DENIED", "Requests from referer <empty> are blocked."), googleError("PERMISSION_DENIED", "Requests from referer <empty> are blocked."))
        val r = check.run("AIza-web")
        val p = r.places as Probe.Failed
        assertEquals("Requests from referer <empty> are blocked.", p.message)
        assertEquals("PERMISSION_DENIED", p.status)
        assertTrue(p.hint!!.contains("restricted to websites"))
        assertEquals(false, r.accepted)
    }

    @Test fun `an API that is not enabled names itself in the hint`() = runTest {
        serve(googleError("PERMISSION_DENIED", "Places API (New) has not been used in project 1234 before or it is disabled. Enable it by visiting https://console.developers.google.com/apis/api/places.googleapis.com/overview?project=1234 then retry."), MockResponse().setBody("""{"session":"abc"}"""))
        val r = check.run("k")
        assertEquals(Probe.Ok, r.tiles)
        assertEquals("Enable the Places API (New) in Google Cloud → APIs & Services → Library.", (r.places as Probe.Failed).hint)
    }

    @Test fun `a mistyped key is called out`() = runTest {
        serve(MockResponse().setBody("""{"id":"x"}"""), MockResponse().setResponseCode(400).setBody("""{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""))
        val r = check.run("k")
        assertEquals("The key was typed wrong or deleted.", (r.tiles as Probe.Failed).hint)
    }

    @Test fun `a body that is not Google's error shape still reports the status`() = runTest {
        serve(MockResponse().setResponseCode(500).setBody("<html>Server Error</html>"), MockResponse().setResponseCode(200).setBody("not json"))
        val r = check.run("k")
        assertEquals(Probe.Failed("HTTP 500", null, null), r.places)
        // A 200 without a session token is not a working tiles key either.
        assertEquals(Probe.Failed("No session token in the reply", null, null), r.tiles)
    }

    @Test fun `a dead network is a failure with the offline hint, not a crash`() = runTest {
        serve(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE), MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val r = check.run("k")
        assertEquals("No internet connection; try again.", (r.places as Probe.Failed).hint)
        assertEquals("No internet connection; try again.", (r.tiles as Probe.Failed).hint)
        assertNull((r.tiles as Probe.Failed).status)
    }

    @Test fun `hints from Google's wording`() {
        assertEquals("Enable the Map Tiles API in Google Cloud → APIs & Services → Library.", hintFor("Map Tiles API has not been used in project 1 before or it is disabled.", "PERMISSION_DENIED"))
        assertEquals("Enable the Places API (New) in Google Cloud → APIs & Services → Library.", hintFor("Places API (New) is not enabled for this project.", null))
        assertTrue(hintFor("Requests from referer https://x are blocked", null)!!.contains("Application restrictions"))
        assertTrue(hintFor("API_KEY_HTTP_REFERRER_BLOCKED", null)!!.contains("Application restrictions"))
        assertEquals("The key was typed wrong or deleted.", hintFor("API key not valid. Please pass a valid API key.", "INVALID_ARGUMENT"))
        assertNull(hintFor("Something else entirely", "UNKNOWN"))
    }

    @Test fun `billing and quota refusals get their own hints, by status or by wording`() {
        val billing = "Enable billing on the project in Cloud Console."
        assertEquals(billing, hintFor("This API method requires billing to be enabled. Please enable billing on project #1.", "PERMISSION_DENIED"))
        assertEquals(billing, hintFor("Billing account for project is disabled", null))
        val quota = "Raise the quota or wait for it to reset in APIs & Services → Quotas."
        assertEquals(quota, hintFor("Quota exceeded for quota metric 'Requests'", "RESOURCE_EXHAUSTED"))
        assertEquals(quota, hintFor("Something opaque", "RESOURCE_EXHAUSTED"))
        assertEquals(quota, hintFor("You have exceeded your daily request quota for this API.", null))
    }
}
