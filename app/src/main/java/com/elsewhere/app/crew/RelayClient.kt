package com.elsewhere.app.crew

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The public half of a device, as exchanged through a pair code; keys are lowercase hex, kxSig base64. */
@Serializable
data class PairRecord(val signPub: String, val kxPub: String, val name: String, val kxSig: String? = null) {
    /** False when the relay, or anyone between, has handed us a kx key the signing key never vouched for. */
    val keysBound: Boolean get() = runCatching { Crypto.verifyKxSig(Crypto.unhex(signPub), Crypto.unhex(kxPub), kxSig) }.getOrDefault(false)
}

class RelayException(val code: Int, message: String) : IOException("$code $message")

/** What the room is doing right now, so the UI can say "connecting" honestly. */
sealed interface RoomEvent {
    data object Open : RoomEvent
    data class Text(val text: String) : RoomEvent
    data class Closed(val reason: String) : RoomEvent
}

/**
 * Signed REST and WebSocket access to the relay. Every request carries the device id, a timestamp and an
 * Ed25519 signature; the relay never sees anything else about us. All network work runs off the main thread.
 */
class RelayClient(
    private val http: OkHttpClient,
    private val baseUrl: () -> String,
    private val keys: suspend () -> DeviceKeys,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json get() = wire

    companion object {
        private val wire = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        fun encode(r: PairRecord): String = wire.encodeToString(PairRecord.serializer(), r)
        fun decode(body: String): PairRecord = wire.decodeFromString(PairRecord.serializer(), body)
    }

    suspend fun createCode(code: String, me: PairRecord) {
        call("POST", "/pair/$code", encode(me))
    }

    /** The owner's record, so the claimer can confirm before saving anything. */
    suspend fun claimCode(code: String, me: PairRecord): PairRecord =
        decode(call("POST", "/pair/$code/claim", encode(me)) ?: "")

    /** The claimer's record once someone has claimed; null while nobody has. */
    suspend fun pollCode(code: String): PairRecord? {
        val body = call("GET", "/pair/$code", null, allow404 = true) ?: return null
        if (body.isBlank() || body == "null" || body == "{}") return null
        return runCatching { decode(body) }.getOrNull()
    }

    suspend fun last(pairId: String): List<Envelope> {
        val body = call("GET", "/room/$pairId/last", null, allow404 = true) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Envelope.serializer()), body) }.getOrElse { emptyList() }
    }

    suspend fun deleteMe() { call("DELETE", "/devices/me", "") }

    fun configured(): Boolean = baseUrl().isNotBlank()

    /** Joins the pair's room; a Text event per incoming envelope, Closed when the socket goes. */
    fun room(pairId: String): Flow<RoomEvent> = callbackFlow {
        val k = keys()
        val ts = clock() / 1000
        val sig = Crypto.signRequest(k.signPriv, "GET", "/room/$pairId", ts, ByteArray(0))
        val url = wsUrl("/room/$pairId") + "?id=${k.deviceId}&ts=$ts&sig=${java.net.URLEncoder.encode(sig, "UTF-8")}"
        val socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { trySend(RoomEvent.Open) }
            override fun onMessage(webSocket: WebSocket, text: String) { trySend(RoomEvent.Text(text)) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { trySend(RoomEvent.Closed(reason)); close() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { trySend(RoomEvent.Closed(t.message ?: "failure")); close() }
        })
        awaitClose { socket.close(1000, null) }
    }

    /** A connected socket for sending; the caller owns its lifetime. */
    suspend fun openSocket(pairId: String, listener: WebSocketListener): WebSocket {
        val k = keys()
        val ts = clock() / 1000
        val sig = Crypto.signRequest(k.signPriv, "GET", "/room/$pairId", ts, ByteArray(0))
        val url = wsUrl("/room/$pairId") + "?id=${k.deviceId}&ts=$ts&sig=${java.net.URLEncoder.encode(sig, "UTF-8")}"
        return http.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private fun wsUrl(path: String): String {
        val base = baseUrl().trimEnd('/')
        return base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + path
    }

    private suspend fun call(method: String, path: String, body: String?, allow404: Boolean = false): String? = withContext(Dispatchers.IO) {
        val base = baseUrl().trimEnd('/')
        if (base.isBlank()) throw RelayException(0, "no relay")
        val k = keys()
        val bytes = (body ?: "").toByteArray()
        val ts = clock() / 1000
        val req = Request.Builder().url(base + path)
            .header("X-Elsewhere-Id", k.deviceId)
            .header("X-Elsewhere-Ts", ts.toString())
            .header("X-Elsewhere-Sig", Crypto.signRequest(k.signPriv, method, path, ts, bytes))
            .method(method, if (method == "GET") null else bytes.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).await().use { r ->
            when {
                r.isSuccessful -> r.body?.string() ?: ""
                r.code == 404 && allow404 -> null
                else -> throw RelayException(r.code, r.body?.string()?.take(200) ?: r.message)
            }
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
    cont.invokeOnCancellation { cancel() }
}
