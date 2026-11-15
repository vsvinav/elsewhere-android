package com.elsewhere.app.ask

import com.elsewhere.app.ingest.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Who answers "Ask Elsewhere"; `wire` is the name stored and shared with the web. */
enum class LlmProvider(val wire: String) {
    OPENAI("openai"), ANTHROPIC("anthropic"), GEMINI("gemini"), CUSTOM("custom");

    companion object {
        fun parse(wire: String?): LlmProvider = entries.firstOrNull { it.wire == wire } ?: OPENAI
    }
}

/** The user's LLM setup; the key never leaves the phone except for the provider itself. */
data class LlmConfig(val provider: LlmProvider = LlmProvider.OPENAI, val key: String = "", val model: String = "", val baseUrl: String = "")

val DEFAULT_MODEL: Map<LlmProvider, String> = mapOf(
    LlmProvider.OPENAI to "gpt-4o-mini",
    LlmProvider.ANTHROPIC to "claude-3-5-haiku-latest",
    LlmProvider.GEMINI to "gemini-2.0-flash",
    LlmProvider.CUSTOM to "llama3.1",
)

const val DEFAULT_CUSTOM_BASE_URL = "http://localhost:11434/v1"
const val TEST_PROMPT = "Reply with the single word OK."
const val LLM_MAX_TOKENS = 256
private const val OPENAI_BASE_URL = "https://api.openai.com/v1"
private const val LLM_TIMEOUT_MS = 30_000L

fun modelOf(c: LlmConfig): String = c.model.ifBlank { DEFAULT_MODEL.getValue(c.provider) }

data class LlmRequest(val url: String, val headers: Map<String, String>, val body: String)

private val codec = Json { ignoreUnknownKeys = true }

/** The provider's own HTTP shape for a single user prompt. */
fun llmRequest(c: LlmConfig, prompt: String): LlmRequest {
    val model = modelOf(c)
    val key = c.key.trim()
    return when (c.provider) {
        LlmProvider.OPENAI, LlmProvider.CUSTOM -> {
            val base = (if (c.provider == LlmProvider.CUSTOM) c.baseUrl.trim() else "").ifBlank { OPENAI_BASE_URL }.trimEnd('/')
            LlmRequest(
                url = "$base/chat/completions",
                headers = if (key.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $key"),
                body = buildJsonObject {
                    put("model", model)
                    put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) })
                    put("max_tokens", LLM_MAX_TOKENS)
                }.toString(),
            )
        }
        LlmProvider.ANTHROPIC -> LlmRequest(
            url = "https://api.anthropic.com/v1/messages",
            headers = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
            body = buildJsonObject {
                put("model", model)
                put("max_tokens", LLM_MAX_TOKENS)
                put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) })
            }.toString(),
        )
        LlmProvider.GEMINI -> LlmRequest(
            url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key",
            headers = emptyMap(),
            body = buildJsonObject {
                put("contents", buildJsonArray { add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) }) }) })
            }.toString(),
        )
    }
}

private fun JsonObject.arr(k: String): JsonArray? = this[k]?.jsonArray
private fun JsonObject.obj(k: String): JsonObject? = this[k]?.jsonObject
private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.content

/** The first reply text in a provider's response; [IllegalStateException] "Empty reply" when there is none. */
fun parseLlmReply(p: LlmProvider, json: String): String {
    val root = runCatching { codec.parseToJsonElement(json).jsonObject }.getOrNull()
    val text = runCatching {
        when (p) {
            LlmProvider.OPENAI, LlmProvider.CUSTOM -> root?.arr("choices")?.firstOrNull()?.jsonObject?.obj("message")?.str("content")
            LlmProvider.ANTHROPIC -> root?.arr("content")?.firstOrNull()?.jsonObject?.str("text")
            LlmProvider.GEMINI -> root?.arr("candidates")?.firstOrNull()?.jsonObject?.obj("content")?.arr("parts")?.firstOrNull()?.jsonObject?.str("text")
        }
    }.getOrNull()
    return text?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Empty reply")
}

private val defaultClient by lazy { OkHttpClient() }

/** One prompt, one answer. A non-2xx status is an [IOException] "HTTP <code>"; an empty body an [IllegalStateException]. */
suspend fun askLlm(c: LlmConfig, prompt: String, client: OkHttpClient = defaultClient): String {
    val r = llmRequest(c, prompt)
    val req = Request.Builder().url(r.url).post(r.body.toRequestBody("application/json".toMediaType()))
        .apply { r.headers.forEach { (k, v) -> header(k, v) } }.build()
    val http = if (client.callTimeoutMillis == 0) client.newBuilder().callTimeout(LLM_TIMEOUT_MS, TimeUnit.MILLISECONDS).build() else client
    http.newCall(req).await().use { res ->
        if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
        return parseLlmReply(c.provider, res.body?.string() ?: "")
    }
}

/** The seam the settings screen tests through; the real one goes out over HTTP. */
interface LlmClient {
    suspend fun ask(c: LlmConfig, prompt: String): String
}

class HttpLlmClient(private val client: OkHttpClient) : LlmClient {
    override suspend fun ask(c: LlmConfig, prompt: String): String = askLlm(c, prompt, client)
}
