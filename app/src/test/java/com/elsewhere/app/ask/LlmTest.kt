package com.elsewhere.app.ask

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class LlmTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun body(r: LlmRequest) = json.parseToJsonElement(r.body).jsonObject

    @Test fun `openai posts a chat completion with a bearer key and the default model`() {
        val r = llmRequest(LlmConfig(LlmProvider.OPENAI, key = "sk-1"), "hi")
        assertEquals("https://api.openai.com/v1/chat/completions", r.url)
        assertEquals("Bearer sk-1", r.headers["Authorization"])
        val b = body(r)
        assertEquals("gpt-4o-mini", b["model"]!!.jsonPrimitive.content)
        assertEquals(256, b["max_tokens"]!!.jsonPrimitive.content.toInt())
        val m = b["messages"]!!.jsonArray[0].jsonObject
        assertEquals("user", m["role"]!!.jsonPrimitive.content)
        assertEquals("hi", m["content"]!!.jsonPrimitive.content)
    }

    @Test fun `anthropic posts to messages with its own headers`() {
        val r = llmRequest(LlmConfig(LlmProvider.ANTHROPIC, key = "ak", model = "claude-x"), "hi")
        assertEquals("https://api.anthropic.com/v1/messages", r.url)
        assertEquals("ak", r.headers["x-api-key"])
        assertEquals("2023-06-01", r.headers["anthropic-version"])
        val b = body(r)
        assertEquals("claude-x", b["model"]!!.jsonPrimitive.content)
        assertEquals(256, b["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals("hi", b["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test fun `gemini puts the model and key in the url and the prompt in parts`() {
        val r = llmRequest(LlmConfig(LlmProvider.GEMINI, key = "gk"), "hi")
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=gk", r.url)
        assertFalse("Authorization" in r.headers)
        val parts = body(r)["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals("hi", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test fun `custom trims the base url's trailing slash and sends no Authorization without a key`() {
        val r = llmRequest(LlmConfig(LlmProvider.CUSTOM, baseUrl = "http://localhost:11434/v1/"), "hi")
        assertEquals("http://localhost:11434/v1/chat/completions", r.url)
        assertFalse("Authorization" in r.headers)
        assertEquals("llama3.1", body(r)["model"]!!.jsonPrimitive.content)
    }

    @Test fun `the model falls back to the provider default when blank`() {
        assertEquals("gpt-4o-mini", modelOf(LlmConfig(LlmProvider.OPENAI)))
        assertEquals("claude-3-5-haiku-latest", modelOf(LlmConfig(LlmProvider.ANTHROPIC)))
        assertEquals("gemini-2.0-flash", modelOf(LlmConfig(LlmProvider.GEMINI)))
        assertEquals("llama3.1", modelOf(LlmConfig(LlmProvider.CUSTOM)))
        assertEquals("gpt-4.1", modelOf(LlmConfig(LlmProvider.OPENAI, model = "gpt-4.1")))
    }

    @Test fun `each provider's first reply text is read`() {
        assertEquals("OK", parseLlmReply(LlmProvider.OPENAI, """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}"""))
        assertEquals("OK", parseLlmReply(LlmProvider.CUSTOM, """{"choices":[{"message":{"content":"OK"}}]}"""))
        assertEquals("OK", parseLlmReply(LlmProvider.ANTHROPIC, """{"content":[{"type":"text","text":"OK"}]}"""))
        assertEquals("OK", parseLlmReply(LlmProvider.GEMINI, """{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}"""))
    }

    @Test fun `an empty reply throws`() {
        val e = assertThrows(IllegalStateException::class.java) { parseLlmReply(LlmProvider.OPENAI, """{"choices":[]}""") }
        assertEquals("Empty reply", e.message)
        assertThrows(IllegalStateException::class.java) { parseLlmReply(LlmProvider.ANTHROPIC, """{"content":[]}""") }
        assertThrows(IllegalStateException::class.java) { parseLlmReply(LlmProvider.GEMINI, """{}""") }
    }

    @Test fun `askLlm sends the request and returns the reply`() = runTest {
        val server = MockWebServer().apply { enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"OK"}}]}""")); start() }
        try {
            val c = LlmConfig(LlmProvider.CUSTOM, key = "k", baseUrl = server.url("/v1").toString())
            assertEquals("OK", askLlm(c, "Reply with the single word OK.", OkHttpClient()))
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/v1/chat/completions", sent.path)
            assertEquals("Bearer k", sent.getHeader("Authorization"))
            assertEquals("application/json; charset=utf-8", sent.getHeader("Content-Type"))
        } finally { server.shutdown() }
    }

    @Test fun `a refused request is an IOException naming the status`() = runTest {
        val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key"}""")); start() }
        try {
            val c = LlmConfig(LlmProvider.CUSTOM, baseUrl = server.url("/v1").toString())
            val e = assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { askLlm(c, "hi", OkHttpClient()) } }
            assertEquals("HTTP 401", e.message)
        } finally { server.shutdown() }
    }
}
