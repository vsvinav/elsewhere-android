package com.elsewhere.app.ingest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** What Places API (New) tells us about a place, kept without the key so it survives a key change. `photo` is the resource name. */
data class Enrichment(val name: String?, val address: String?, val types: List<String>, val primaryType: String?, val photo: String?) {
    companion object { val NONE = Enrichment(null, null, emptyList(), null, null) }
}

/** A Details call Google refused; `code` 429 or 5xx is worth a retry. */
class PlacesError(message: String, val code: Int) : IOException(message) {
    val retryable: Boolean get() = code == 429 || code >= 500
}

@Singleton
/** One paid Place Details call; the real one talks to Google, tests script answers. */
interface DetailsSource {
    suspend fun details(id: String, key: String): Enrichment
}

class GooglePlaces(client: OkHttpClient, private val base: String) : DetailsSource {
    @Inject constructor(client: OkHttpClient) : this(client, "https://places.googleapis.com")

    private val client = client
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class Text(val text: String? = null)
    @Serializable private data class Photo(val name: String)
    @Serializable private data class Details(val displayName: Text? = null, val formattedAddress: String? = null, val types: List<String> = emptyList(), val primaryType: String? = null, val photos: List<Photo> = emptyList())

    /** One paid Details call. A place Google no longer knows (400/404) is [Enrichment.NONE]; anything else not OK throws. */
    override suspend fun details(id: String, key: String): Enrichment {
        val req = Request.Builder().url("$base/v1/places/${URLEncoder.encode(id, "UTF-8")}")
            .header("X-Goog-Api-Key", key).header("X-Goog-FieldMask", FIELDS).build()
        client.newCall(req).await().use { res ->
            val body = res.body?.string() ?: ""
            if (res.code == 404 || res.code == 400) return Enrichment.NONE
            if (!res.isSuccessful) {
                val msg = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull()
                throw PlacesError(msg?.substringBefore(" of service") ?: "Places API ${res.code}", res.code)
            }
            val d = json.decodeFromString<Details>(body)
            return Enrichment(d.displayName?.text, d.formattedAddress, d.types, d.primaryType, d.photos.firstOrNull()?.name)
        }
    }

    companion object {
        const val FIELDS = "displayName,formattedAddress,types,primaryType,photos"
        fun photoUrl(name: String, key: String) = "https://places.googleapis.com/v1/$name/media?maxWidthPx=900&key=$key"
    }
}

private val ADDRESS_TYPES = setOf("street_address", "premise", "subpremise", "route", "plus_code", "postal_code", "locality", "sublocality", "sublocality_level_1", "neighborhood", "political", "geocode", "intersection")

/** Is this a proper name ("Savoy Apartments"), not an address Google gave back as the display name? */
fun properName(name: String?, types: List<String>, primaryType: String?): Boolean {
    if (name.isNullOrBlank() || name.trim().first().isDigit()) return false
    if (primaryType != null && primaryType !in ADDRESS_TYPES) return true
    return types.any { it !in ADDRESS_TYPES && it != "point_of_interest" && it != "establishment" }
}

fun properName(e: Enrichment) = properName(e.name, e.types, e.primaryType)
