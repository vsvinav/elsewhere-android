package com.elsewhere.app.ingest

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One API's answer to the user's key. */
sealed interface Probe {
    data object Ok : Probe
    /** `message` is Google's own wording, `status` its RPC code, `hint` what we make of it. */
    data class Failed(val message: String, val status: String?, val hint: String?) : Probe
}

data class KeyReport(val places: Probe, val tiles: Probe) {
    val accepted: Boolean get() = places == Probe.Ok && tiles == Probe.Ok
}

/** Two probes run with a key; the real one talks to Google, tests hand in a canned report. */
interface KeyChecker {
    suspend fun run(key: String): KeyReport
}

/** What to do about Google's refusal, in our words; null when the message says nothing we recognise. */
fun hintFor(message: String, status: String?): String? {
    val m = message.lowercase()
    val api = Regex("""((?:[A-Z][\w()]*\s)*?[A-Z][\w()]*\sAPI(?:\s\(New\))?)\s(?:has not been used|is not enabled)""").find(message)?.groupValues?.get(1)
    return when {
        "referer" in m || "referrer" in m -> "This key is restricted to websites. In Google Cloud → Credentials → this key, set Application restrictions to None (or create a second key for the phone)."
        api != null -> "Enable the $api in Google Cloud → APIs & Services → Library."
        "api key not valid" in m -> "The key was typed wrong or deleted."
        "billing" in m -> "Enable billing on the project in Cloud Console."
        status == "RESOURCE_EXHAUSTED" || "quota" in m -> "Raise the quota or wait for it to reset in APIs & Services → Quotas."
        else -> null
    }
}

/**
 * Checks a key against the two Google APIs the app uses, in parallel: Places (New) with an id-only
 * field mask (the free SKU) and a Map Tiles session. Each probe gives back [Probe.Ok] or Google's
 * message with a hint; a dead network is a failure too, never an exception.
 */
const val PROBE_TIMEOUT_MS = 6_000L

@Singleton
class KeyCheck(
    client: OkHttpClient,
    private val placesBase: String = "https://places.googleapis.com",
    private val tilesBase: String = "https://tile.googleapis.com",
) : KeyChecker {
    @Inject constructor(client: OkHttpClient) : this(client, "https://places.googleapis.com", "https://tile.googleapis.com")

    /** A ceiling per probe unless the caller already set one: the web gives Google six seconds to draw a map. */
    internal val client = if (client.callTimeoutMillis == 0) client.newBuilder().callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS).build() else client
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun run(key: String): KeyReport = coroutineScope {
        val places = async { probe(placesRequest(key)) { true } }
        val tiles = async { probe(tilesRequest(key)) { body -> runCatching { json.parseToJsonElement(body).jsonObject["session"]?.jsonPrimitive?.content }.getOrNull()?.isNotBlank() == true } }
        KeyReport(places.await(), tiles.await())
    }

    private fun placesRequest(key: String) = Request.Builder()
        .url("$placesBase/v1/places/$SAMPLE_PLACE").get()
        .header("X-Goog-Api-Key", key).header("X-Goog-FieldMask", "id").build()

    private fun tilesRequest(key: String) = Request.Builder()
        .url("$tilesBase/v1/createSession?key=$key")
        .post("""{"mapType":"roadmap","language":"en-US","region":"US"}""".toRequestBody("application/json".toMediaType())).build()

    private suspend fun probe(req: Request, okBody: (String) -> Boolean): Probe {
        val res = try { client.newCall(req).await() } catch (e: IOException) { return Probe.Failed(e.message ?: "Network error", null, OFFLINE) }
        res.use { r ->
            val body = runCatching { r.body?.string() }.getOrNull() ?: ""
            if (r.isSuccessful) return if (okBody(body)) Probe.Ok else Probe.Failed("No session token in the reply", null, null)
            val err = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
            val message = err?.get("message")?.jsonPrimitive?.content ?: "HTTP ${r.code}"
            val status = err?.get("status")?.jsonPrimitive?.content
            return Probe.Failed(message, status, hintFor(message, status))
        }
    }

    companion object {
        /** Google's own documentation sample: the Sydney office. */
        const val SAMPLE_PLACE = "ChIJN1t_tDeuEmsRUsoyG83frY4"
        const val OFFLINE = "No internet connection; try again."
    }
}

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
    })
    cont.invokeOnCancellation { cancel() }
}
