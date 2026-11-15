package com.elsewhere.app.ingest

import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.data.Place
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// Port of web/src/ingest/enrich.ts: names and photographs the busiest places with the visitor's key.

/** How far the lookups have got; the stream ends with null once there is nothing left to fetch. */
data class Progress(val done: Int, val total: Int)

/** Where enrichment rows live between runs; the repository writes them into Room. */
interface EnrichmentStore {
    suspend fun cached(): Map<String, Enrichment>
    suspend fun put(rows: Map<String, Enrichment>)
}

class MemoryEnrichmentStore : EnrichmentStore {
    val rows = LinkedHashMap<String, Enrichment>()
    override suspend fun cached(): Map<String, Enrichment> = rows.toMap()
    override suspend fun put(rows: Map<String, Enrichment>) { this.rows += rows }
}

const val ENRICH_LIMIT = 100
private const val CONCURRENCY = 4
private const val FLUSH_EVERY = 12
private const val RETRIES = 3

/** Which places deserve a lookup: the most visited ones (twice or more) that still lack a name or a photo, home excluded. */
fun wanted(places: Map<String, Place>, cached: Set<String>, limit: Int = ENRICH_LIMIT): List<String> =
    places.entries
        .filter { (id, p) -> !isHome(p) && id !in cached && p.nVisits >= 2 && (p.name == null || p.photo == null) }
        .sortedWith(compareByDescending<Map.Entry<String, Place>> { it.value.nVisits }.thenByDescending { it.value.totalHours })
        .take(limit).map { it.key }

/** Names a merged candidate (the web's `clusterName`): a Places lookup when a key works, nothing otherwise. */
interface PlaceNamer {
    suspend fun name(ids: List<String>, all: Map<String, Place>, key: String): String?
}

@Singleton
class Enricher(
    private val places: DetailsSource,
    private val store: EnrichmentStore,
    private val meter: CostMeter,
    /** List-price ceiling on everything this phone has spent; lookups stop short of it. */
    private val capDollars: Double = CAP_DOLLARS,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : PlaceNamer {
    @Inject constructor(places: GooglePlaces, store: EnrichmentStore, meter: CostMeter) : this(places, store, meter, CAP_DOLLARS, { delay(it) })

    /** One paid lookup with backoff on 429/5xx (1 s, 2 s, 4 s), tallied once per attempt. */
    private suspend fun lookup(id: String, key: String): Enrichment {
        var attempt = 0
        while (true) {
            meter.tally(Kind.DETAILS)
            try { return places.details(id, key) } catch (e: PlacesError) {
                if (!e.retryable || attempt >= RETRIES) throw e
                sleep(1000L shl attempt); attempt++
            }
        }
    }

    /**
     * Looks up what [wanted] picks, four at a time, writing to the store every twelve and at the end.
     * `onProgress` gets (0, total) first, each landing, then null. A refusal or a dead network throws after reporting null.
     */
    suspend fun run(all: Map<String, Place>, key: String, onProgress: (Progress?) -> Unit) {
        if (key.isBlank()) return
        val cached = store.cached()
        val budget = meter.counters.first().detailsBudget(capDollars)
        val queue = ArrayDeque(wanted(all, cached.keys).take(budget))
        if (queue.isEmpty()) return
        val total = queue.size
        val lock = Mutex()
        val pending = LinkedHashMap<String, Enrichment>()
        var landed = 0
        onProgress(Progress(0, total))
        // Every landing was billed, so whatever is pending reaches Room however the run ends, even mid-cancellation.
        suspend fun flush() = withContext(NonCancellable) { val rows = lock.withLock { val r = pending.toMap(); pending.clear(); r }; if (rows.isNotEmpty()) store.put(rows) }
        try {
            coroutineScope {
                List(CONCURRENCY) {
                    async {
                        while (true) {
                            val id = lock.withLock { queue.removeFirstOrNull() } ?: break
                            val e = lookup(id, key)
                            val n = lock.withLock { pending[id] = e; ++landed }
                            onProgress(Progress(n, total))
                            if (n % FLUSH_EVERY == 0) flush()
                        }
                    }
                }.forEach { it.await() }
            }
        } finally {
            flush(); onProgress(null)
        }
    }

    /** The best name for a cluster of place ids; only pays for lookups that are not cached yet, at most `max` of them. */
    override suspend fun name(ids: List<String>, all: Map<String, Place>, key: String): String? = clusterName(ids, all, key)

    suspend fun clusterName(ids: List<String>, all: Map<String, Place>, key: String, max: Int = 3): String? {
        val cache = store.cached().toMutableMap()
        fun known(id: String): Enrichment? {
            cache[id]?.let { return it }
            val p = all[id] ?: return null
            return if (p.name != null) Enrichment(p.name, p.address, p.types, p.primaryType, p.photo) else null
        }
        var got = ids.map(::known)
        got.firstOrNull { it != null && properName(it) }?.let { return it.name }
        if (key.isNotBlank()) {
            var paid = 0
            val fresh = LinkedHashMap<String, Enrichment>()
            for (i in ids.indices) {
                if (paid >= max) break
                if (got[i] != null) continue
                val e = try { lookup(ids[i], key) } catch (e: PlacesError) { break }
                cache[ids[i]] = e; fresh[ids[i]] = e; paid++
                if (properName(e)) break
            }
            if (fresh.isNotEmpty()) store.put(fresh)
            got = ids.map(::known)
        }
        val named = got.filterNotNull().filter { it.name != null }
        return (named.firstOrNull(::properName) ?: named.firstOrNull())?.name
    }

    companion object { const val CAP_DOLLARS = 10.0 }
}
