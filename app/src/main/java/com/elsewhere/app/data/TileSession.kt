package com.elsewhere.app.data

import com.elsewhere.app.ingest.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A Map Tiles API session token and when it stops working, in epoch millis. */
data class TileSession(val token: String, val expiresAt: Long)

/** The map the tiles describe; Google's viewport call wants it in whole zoom levels and degrees. */
data class Viewport(val zoom: Int, val north: Double, val south: Double, val east: Double, val west: Double)

class TilesError(message: String) : IOException(message)

fun tileTemplate(s: TileSession, key: String): String = "https://tile.googleapis.com/v1/2dtiles/{z}/{x}/{y}?session=${s.token}&key=$key"

/** A MapLibre style with one raster layer: Google's roadmap tiles at their native 256px. */
fun rasterStyle(template: String): String =
    """{"version":8,"sources":{"google":{"type":"raster","tiles":["$template"],"tileSize":256,"maxzoom":22}},"layers":[{"id":"google","type":"raster","source":"google"}]}"""

/** What the map screen needs from the Map Tiles API; a fake stands in for tests. */
interface TileService {
    suspend fun session(key: String): TileSession
    suspend fun copyright(s: TileSession, key: String, vp: Viewport): String?
}

/**
 * Google's Map Tiles API: one session per key, kept in memory until a minute before it expires (they last
 * about two weeks), and the attribution text the terms require for whatever the camera shows.
 */
@Singleton
class GoogleTiles(
    private val client: OkHttpClient,
    private val base: String = "https://tile.googleapis.com",
    private val clock: () -> Long = System::currentTimeMillis,
) : TileService {
    @Inject constructor(client: OkHttpClient) : this(client, "https://tile.googleapis.com", System::currentTimeMillis)

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var cached: Pair<String, TileSession>? = null

    override suspend fun session(key: String): TileSession = lock.withLock {
        cached?.let { (k, s) -> if (k == key && s.expiresAt - clock() > MARGIN_MS) return s }
        val req = Request.Builder().url("$base/v1/createSession?key=$key")
            .post("""{"mapType":"roadmap","language":"en-US","region":"US"}""".toRequestBody("application/json".toMediaType())).build()
        val res = client.newCall(req).await()
        val body = res.body?.string() ?: ""
        if (!res.isSuccessful) throw TilesError(googleMessage(body) ?: "HTTP ${res.code}")
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: throw TilesError("Unreadable session")
        val token = o["session"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: throw TilesError("No session in reply")
        val expiry = o["expiry"]?.jsonPrimitive?.content?.toLongOrNull() ?: (clock() / 1000 + 12 * 3600)
        TileSession(token, expiry * 1000).also { cached = key to it }
    }

    override suspend fun copyright(s: TileSession, key: String, vp: Viewport): String? = runCatching {
        val url = "$base/tile/v1/viewport?session=${s.token}&key=$key&zoom=${vp.zoom}&north=${vp.north}&south=${vp.south}&east=${vp.east}&west=${vp.west}"
        val res = client.newCall(Request.Builder().url(url).get().build()).await()
        if (!res.isSuccessful) return null
        json.parseToJsonElement(res.body?.string() ?: "").jsonObject["copyright"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun googleMessage(body: String): String? =
        runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()

    private companion object { const val MARGIN_MS = 60_000L }
}
