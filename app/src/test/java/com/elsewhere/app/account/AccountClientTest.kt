package com.elsewhere.app.account

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class AccountClientTest {
    private val server = MockWebServer()
    private lateinit var client: AccountClient

    @BeforeEach fun setUp() {
        server.start()
        client = AccountClient(OkHttpClient(), { server.url("/").toString() })
    }

    @AfterEach fun tearDown() { runCatching { server.shutdown() } }

    @Test fun `start posts the email as JSON`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        client.start("Ada@Example.org ")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/auth/start", req.path)
        assertEquals("""{"email":"ada@example.org"}""", req.body.readUtf8())
        assertNull(req.getHeader("Authorization"))
    }

    @Test fun `finish by code returns the session and the account`() = runTest {
        server.enqueue(MockResponse().setBody("""{"session":"s3cret","account":{"id":"acct_0123456789abcdef","email":"ada@example.org","created":1700000000000,"hasVault":true}}"""))
        val login = client.finish("ada@example.org", "123456")
        assertEquals("""{"email":"ada@example.org","code":"123456"}""", server.takeRequest().body.readUtf8())
        assertEquals("s3cret", login.session)
        assertEquals(AccountInfo("acct_0123456789abcdef", "ada@example.org", 1700000000000L, true), login.account)
    }

    @Test fun `the server's error word rides on the exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"slow down"}"""))
        val e = runCatching { client.start("a@b.co") }.exceptionOrNull() as AccountException
        assertEquals(429, e.code); assertEquals("slow down", e.error)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"wrong code"}"""))
        val w = runCatching { client.finish("a@b.co", "000000") }.exceptionOrNull() as AccountException
        assertEquals(401, w.code); assertEquals("wrong code", w.error)
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val x = runCatching { client.start("a@b.co") }.exceptionOrNull() as AccountException
        assertEquals(500, x.code); assertEquals("boom", x.error)
    }

    @Test fun `authenticated calls carry the bearer`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"acct_1","email":"a@b.co","created":5,"hasVault":false}"""))
        assertEquals(AccountInfo("acct_1", "a@b.co", 5L, false), client.me("tok"))
        val me = server.takeRequest()
        assertEquals("GET", me.method); assertEquals("/me", me.path); assertEquals("Bearer tok", me.getHeader("Authorization"))

        server.enqueue(MockResponse().setResponseCode(204))
        client.logout("tok")
        val out = server.takeRequest()
        assertEquals("DELETE", out.method); assertEquals("/session", out.path); assertEquals("Bearer tok", out.getHeader("Authorization"))

        server.enqueue(MockResponse().setResponseCode(204))
        client.deleteMe("tok")
        val del = server.takeRequest()
        assertEquals("DELETE", del.method); assertEquals("/me", del.path)
    }

    @Test fun `the vault goes up as its body and comes back, or is absent`() = runTest {
        val body = VaultBody(salt = "c2FsdA==", iv = "aXY=", ct = "Y3Q=")
        server.enqueue(MockResponse().setResponseCode(204))
        client.putVault("tok", body)
        val put = server.takeRequest()
        assertEquals("PUT", put.method); assertEquals("/vault", put.path)
        assertEquals(body.encode(), put.body.readUtf8())
        assertTrue(put.getHeader("Content-Type")!!.startsWith("application/json"))

        server.enqueue(MockResponse().setBody(body.encode()))
        assertEquals(body, client.getVault("tok"))
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(client.getVault("tok"))
    }

    @Test fun `a blank base URL fails before any network`() = runTest {
        val none = AccountClient(OkHttpClient(), { "" })
        val e = runCatching { none.start("a@b.co") }.exceptionOrNull()
        assertTrue(e is IOException)
        assertEquals(0, server.requestCount)
    }

    @Test fun `errors read as the shared copy`() {
        assertEquals("Too many tries. Give it an hour.", AccountException(429, "slow down").userMessage())
        assertEquals("That code isn't right.", AccountException(401, "wrong code").userMessage())
        assertEquals("That code expired. Send a new one.", AccountException(400, "expired").userMessage())
        assertEquals("Couldn't reach the account service. Check your connection.", IOException("timeout").userMessage())
        assertEquals("Something went wrong. Try again.", AccountException(500, "boom").userMessage())
    }
}
