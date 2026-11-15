package com.elsewhere.app.ingest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// Port of web/src/ingest/nominatim.ts.

@Serializable
data class Addr(
    @SerialName("house_number") val houseNumber: String? = null, val road: String? = null, val neighbourhood: String? = null,
    val suburb: String? = null, val city: String? = null, val town: String? = null, val village: String? = null,
)

/** "2180 Cranston Rd, University Heights" — the bits a person would say. */
fun shortAddress(a: Addr): String? {
    val street = listOfNotNull(a.houseNumber, a.road).joinToString(" ")
    val area = a.neighbourhood ?: a.suburb ?: a.city ?: a.town ?: a.village
    return if (street.isEmpty()) null else if (area != null) "$street, $area" else street
}

/** Answers already fetched, by rounded point; null means OSM had nothing usable. `SettingsStore` keeps it in DataStore. */
interface AddressBook {
    suspend fun load(): Map<String, String?>
    suspend fun save(book: Map<String, String?>)
}

class MemoryAddressBook : AddressBook {
    var saved: Map<String, String?> = emptyMap()
    override suspend fun load() = saved
    override suspend fun save(book: Map<String, String?>) { saved = book }
}

/** Street addresses from OpenStreetMap's Nominatim: free, no key, one request a second by its usage policy. */
@Singleton
class Nominatim(
    private val client: OkHttpClient,
    private val book: AddressBook,
    private val base: String = "https://nominatim.openstreetmap.org",
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : Geocoder {
    @Inject constructor(client: OkHttpClient, book: AddressBook) : this(client, book, "https://nominatim.openstreetmap.org", { delay(it) })

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var cache: MutableMap<String, String?>? = null

    @Serializable private data class Reply(val address: Addr? = null)

    private fun keyOf(lat: Double, lng: Double) = String.format(Locale.US, "%.5f,%.5f", lat, lng)

    private suspend fun fetchOne(lat: Double, lng: Double): String? {
        val req = Request.Builder().url("$base/reverse?format=jsonv2&zoom=18&lat=$lat&lon=$lng")
            .header("Accept", "application/json").header("Accept-Language", "en").header("User-Agent", USER_AGENT).build()
        client.newCall(req).await().use { res ->
            if (!res.isSuccessful) return null
            return json.decodeFromString<Reply>(res.body?.string() ?: "").address?.let(::shortAddress)
        }
    }

    /** Address for a point, from the book or the queue. Null when OSM has nothing usable or the call fails. */
    override suspend fun address(lat: Double, lng: Double): String? = lock.withLock {
        val c = cache ?: book.load().toMutableMap().also { cache = it }
        val k = keyOf(lat, lng)
        if (k in c) return c[k]
        // A cancelled lookup is nobody's answer: only a real reply or failure goes in the book.
        val a = try { fetchOne(lat, lng) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        currentCoroutineContext().ensureActive()
        c[k] = a
        runCatching { book.save(c.toMap()) }
        sleep(GAP_MS)
        a
    }

    companion object {
        const val GAP_MS = 1100L
        const val USER_AGENT = "Elsewhere/0.1 (https://github.com/vsvinav/elsewhere)"
    }
}

/** A street address for a point; Nominatim is the real one, tests hand in a map. */
interface Geocoder {
    suspend fun address(lat: Double, lng: Double): String?
}
