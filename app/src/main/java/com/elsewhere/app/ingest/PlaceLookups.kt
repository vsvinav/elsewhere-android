package com.elsewhere.app.ingest

import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Port of web/src/ingest/places.ts: one shared store of place names and photos, filled on demand as the tabs ask.

/** How far the current burst of lookups has got; null once there is nothing left to fetch. */
data class Progress(val done: Int, val total: Int)

/** Where enrichment rows live between runs; the repository writes them into Room and re-prints the record. */
interface EnrichmentStore {
    suspend fun cached(): Map<String, Enrichment>
    suspend fun put(rows: Map<String, Enrichment>)
}

class MemoryEnrichmentStore : EnrichmentStore {
    val rows = LinkedHashMap<String, Enrichment>()
    val puts = ArrayList<Map<String, Enrichment>>()
    override suspend fun cached(): Map<String, Enrichment> = rows.toMap()
    override suspend fun put(rows: Map<String, Enrichment>) { this.rows += rows; puts += rows }
}

/** What the lookups are up to, for the stamp in the corner; `error` is the last refusal's message. */
@Singleton
class EnrichState @Inject constructor() {
    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    fun report(p: Progress?) { _progress.value = p }
    fun fail(message: String?) { _error.value = message }
}

/** Names a merged candidate (the web's `clusterName`): a Places lookup when a key works, nothing otherwise. */
interface PlaceNamer {
    suspend fun name(ids: List<String>, all: Map<String, Place>, key: String): String?
    /** How far the shared lookups have got, for the stamp on the candidate cards. */
    val progress: StateFlow<Progress?>
}

private const val CONCURRENCY = 4
private const val SAVE_MS = 300L
private const val BACKOFF_MS = 2_000L
private val FATAL = Regex("API key|PERMISSION_DENIED|billing|not enabled", RegexOption.IGNORE_CASE)

/** Only a refused key stops the pool; anything else is worth another try. */
fun fatal(e: Throwable): Boolean =
    (e is PlacesError && (e.code == 401 || e.code == 403)) || FATAL.containsMatchIn(e.message ?: "")

/**
 * Looks places up four at a time as the screens ask for them, when the visitor's key allows it. Every
 * writer lands in one in-memory cache, so none can overwrite another's results; landings reach Room in
 * batches so the record is re-printed once per burst, not once per lookup.
 */
@Singleton
class PlaceLookups(
    private val source: DetailsSource,
    private val store: EnrichmentStore,
    private val meter: CostMeter,
    private val state: EnrichState,
    private val scope: CoroutineScope,
    settings: Flow<Settings>,
    private val saveMs: Long = SAVE_MS,
    private val backoffMs: Long = BACKOFF_MS,
) : PlaceNamer {
    @Inject constructor(source: GooglePlaces, store: EnrichmentStore, meter: CostMeter, state: EnrichState, scope: CoroutineScope, maps: MapsSettings) :
        this(source, store, meter, state, scope, maps.settings, SAVE_MS, BACKOFF_MS)

    override val progress: StateFlow<Progress?> get() = state.progress

    private val lock = Any()
    private val cache = HashMap<String, Enrichment>()
    private var loaded: Deferred<Unit>? = null
    private val dirty = LinkedHashMap<String, Enrichment>()
    private var saveJob: Job? = null
    private val queue = ArrayDeque<String>()
    private val queued = HashSet<String>()
    /** Ids that failed once for a passing reason (network, 429, 5xx) get one more go. */
    private val retried = HashSet<String>()
    private var running = 0
    private var pending = 0
    private var total = 0
    private var key = ""
    private var keyPlaces = false
    private var stopped = false
    private var paused = false

    init { scope.launch { settings.collect { setKey(it.mapsKey, it.keyPlaces) } } }

    private fun canSpend() = key.isNotBlank() && !stopped && keyPlaces

    /** Lookups are counted for the stamp as they are asked for; the burst's total resets once everything has landed. */
    private fun ask(n: Int = 1) { pending += n; total += n }
    private fun settle(n: Int = 1) { pending -= n; if (pending == 0) total = 0 }
    private fun emit() { state.report(synchronized(lock) { if (total > 0) Progress(total - pending, total) else null }) }

    private fun dropQueue() { settle(queue.size); queue.forEach { queued.remove(it) }; queue.clear() }

    /**
     * Ask for these places to be named and photographed. Cached ids, home, places already named with a
     * photo and ids already waiting are skipped; nothing is spent without a key Places accepted.
     */
    fun need(ids: List<String>, places: Map<String, Place>) {
        synchronized(lock) {
            if (!canSpend()) return
            var added = 0
            for (id in ids) {
                if (id in cache || id in queued) continue
                val p = places[id] ?: continue
                if (isHome(p) || (p.name != null && p.photo != null)) continue
                queued += id; queue += id; added++
            }
            if (added == 0) return
            ask(added)
        }
        emit()
        scope.launch {
            load()
            synchronized(lock) {
                val it = queue.iterator()
                while (it.hasNext()) { val id = it.next(); if (id in cache) { it.remove(); queued.remove(id); settle() } }
            }
            emit(); pump()
        }
    }

    /** The key lookups pay with (blank: none). A new key clears a stopped pool and picks up whatever is still waiting. */
    fun setKey(k: String, places: Boolean = true) {
        synchronized(lock) { key = k; keyPlaces = places; stopped = false; if (!canSpend()) dropQueue() }
        state.fail(null)
        emit(); pump()
    }

    /** Start over: forget what was looked up, so a fresh record asks again. */
    fun forget() { synchronized(lock) { cache.clear(); dirty.clear(); loaded = null; retried.clear() } }

    private fun pump() {
        while (true) {
            val id: String; val k: String
            synchronized(lock) {
                if (running >= CONCURRENCY || queue.isEmpty() || paused || !canSpend()) return
                id = queue.removeFirst(); k = key; running++
            }
            scope.launch { fetch(id, k) }
        }
    }

    private suspend fun fetch(id: String, k: String) {
        fun done() = synchronized(lock) { queued.remove(id); settle() }
        try {
            val e = lookup(id, k)
            put(id, e); done()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (fatal(e)) {
                done()
                synchronized(lock) { stopped = true; dropQueue() }
                state.fail(e.message)
            } else {
                val again = synchronized(lock) { if (id in retried) false else { retried += id; queue.addLast(id); paused = true; true } }
                if (!again) done()
                else scope.launch { delay(backoffMs); synchronized(lock) { paused = false }; pump() }
            }
        } finally {
            synchronized(lock) { running-- }
            emit(); pump()
        }
    }

    private suspend fun lookup(id: String, k: String): Enrichment {
        meter.tally(Kind.DETAILS)
        return source.details(id, k)
    }

    private suspend fun load() {
        val d = synchronized(lock) {
            loaded ?: scope.async {
                val c = runCatching { store.cached() }.getOrDefault(emptyMap())
                synchronized(lock) { for ((id, e) in c) cache.putIfAbsent(id, e) }
            }.also { loaded = it }
        }
        d.await()
    }

    private fun put(id: String, e: Enrichment) {
        synchronized(lock) {
            cache[id] = e; dirty[id] = e
            saveJob?.cancel()
            saveJob = scope.launch { delay(saveMs); flush() }
        }
    }

    /** Every landing was billed, so whatever is pending reaches Room even if the scope is going down. */
    private suspend fun flush() = withContext(NonCancellable) {
        val rows = synchronized(lock) { val r = dirty.toMap(); dirty.clear(); r }
        if (rows.isNotEmpty()) store.put(rows)
    }

    private suspend fun <T> track(block: suspend () -> T): T {
        synchronized(lock) { ask() }; emit()
        try { return block() } finally { synchronized(lock) { settle() }; emit() }
    }

    override suspend fun name(ids: List<String>, all: Map<String, Place>, key: String): String? = clusterName(ids, all, key)

    /** The best name for a cluster of place ids; only pays for lookups that are not cached yet, at most `max` of them. */
    suspend fun clusterName(ids: List<String>, all: Map<String, Place>, k: String, max: Int = 3): String? {
        load()
        fun known(id: String): Enrichment? {
            synchronized(lock) { cache[id] }?.let { return it }
            val p = all[id] ?: return null
            return if (p.name != null) Enrichment(p.name, p.address, p.types, p.primaryType, p.photo) else null
        }
        var got = ids.map(::known)
        got.firstOrNull { it != null && properName(it) }?.let { return it.name }
        if (k.isNotBlank()) {
            var paid = 0
            for (i in ids.indices) {
                if (paid >= max) break
                if (got[i] != null) continue
                val e = try { track { lookup(ids[i], k) } } catch (e: IOException) { break }
                put(ids[i], e); paid++
                if (properName(e)) break
            }
            got = ids.map(::known)
        }
        val named = got.filterNotNull().filter { it.name != null }
        return (named.firstOrNull(::properName) ?: named.firstOrNull())?.name
    }
}
