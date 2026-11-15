package com.elsewhere.app.ingest

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// Port of web/src/ingest/wikimedia.ts.

@Serializable data class ImageInfo(val thumburl: String? = null, val url: String? = null)
@Serializable data class WikiPage(val title: String? = null, val imageinfo: List<ImageInfo>? = null)

private val NOT_A_PHOTO = Regex("""map|plan|logo|flag|diagram|\.svg$|\.pdf$""", RegexOption.IGNORE_CASE)

/** The first picture that is a photo, not a map, plan or flag. */
fun pickPhoto(pages: List<WikiPage>): String? {
    for (p in pages) {
        if (NOT_A_PHOTO.containsMatchIn(p.title ?: "")) continue
        val u = p.imageinfo?.firstOrNull()?.let { it.thumburl ?: it.url }
        if (u != null) return u
    }
    return null
}

/**
 * Photos from Wikimedia Commons, free and keyless: the nearest geotagged picture within a short walk.
 * Good for landmarks, stations and hotels; useless for a taco place, which is why callers pick the category.
 */
@Singleton
class Wikimedia(
    private val client: OkHttpClient,
    private val base: String = "https://commons.wikimedia.org",
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    @Inject constructor(client: OkHttpClient) : this(client, "https://commons.wikimedia.org", { delay(it) })

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val cache = HashMap<String, String?>()

    @Serializable private data class Query(val pages: Map<String, WikiPage> = emptyMap())
    @Serializable private data class Reply(val query: Query? = null)

    private fun keyOf(lat: Double, lng: Double) = String.format(Locale.US, "%.4f,%.4f", lat, lng)

    private suspend fun fetchOne(lat: Double, lng: Double): String? {
        val url = "$base/w/api.php?action=query&generator=geosearch&ggscoord=$lat%7C$lng&ggsradius=$RADIUS_M&ggsnamespace=6&ggslimit=5&prop=imageinfo&iiprop=url&iiurlwidth=900&format=json"
        client.newCall(Request.Builder().url(url).header("User-Agent", Nominatim.USER_AGENT).build()).await().use { res ->
            if (!res.isSuccessful) return null
            return pickPhoto(json.decodeFromString<Reply>(res.body?.string() ?: "").query?.pages?.values?.toList() ?: emptyList())
        }
    }

    /** Commons photo near a point, from cache or the queue; null when there is none. */
    suspend fun photo(lat: Double, lng: Double): String? = lock.withLock {
        val k = keyOf(lat, lng)
        if (k in cache) return cache[k]
        val u = runCatching { fetchOne(lat, lng) }.getOrNull()
        cache[k] = u
        sleep(GAP_MS)
        u
    }

    companion object { const val RADIUS_M = 120; const val GAP_MS = 150L }
}
