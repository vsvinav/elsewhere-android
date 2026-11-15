package com.elsewhere.app.data

import com.elsewhere.app.ingest.Progress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Where the local work has got to: reading, parsing, deriving, drawing paths. API lookups are not counted here (web ReadyBar.tsx). */
data class Ready(val label: String, val pct: Int)

private const val TRIPS = "Working out trips"
private const val CITIES = "Finding cities"
private const val PATHS = "Drawing paths"

/** `RecordRepository.import()` reports 0..1: parsing until a half, then rows going into the database, then the cities pass. */
fun importReady(progress: Float): Ready = when {
    progress < 0.5f -> Ready("Reading file", 10)
    progress < 1f -> Ready("Finding stops", 35)
    else -> Ready(CITIES, 60)
}

/** Deriving after an import carries on from the cities stage; a rebuild on its own starts half way, as the web's REDERIVE does. */
fun derivingReady(prev: Ready?): Ready = Ready(TRIPS, if (prev?.label == CITIES) 85 else 50)

/**
 * What the shell's bar shows: the local work while it is still going, then the Places lookups, so the
 * bar does not lift while names are still landing slowly. Short label so it fits one line on a narrow phone.
 */
fun shellReady(r: Ready?, n: Progress?): Ready? = when {
    r != null && r.pct < 100 -> r
    n != null && n.total > 0 -> Ready("Naming places ${n.done}/${n.total}", n.done * 100 / n.total)
    else -> r
}

@Singleton
class ReadyState @Inject constructor() {
    private val _ready = MutableStateFlow<Ready?>(null)
    val ready: StateFlow<Ready?> = _ready
    private var drawing = 0

    fun importing(progress: Float) = _ready.update { importReady(progress) }
    fun deriving() = _ready.update { derivingReady(it) }
    fun derived() = _ready.update { Ready(TRIPS, 100) }
    fun clear() = _ready.update { null }

    @Synchronized fun drawing() { drawing++; _ready.value = Ready(PATHS, 0) }
    @Synchronized fun drawn() { if (--drawing <= 0) { drawing = 0; _ready.value = Ready(PATHS, 100) } }
}

/** Wrap a lazy asset load so the bar reads "Drawing paths" until every such load has landed. */
suspend inline fun <T> ReadyState.drawing(block: () -> T): T {
    drawing()
    return try { block() } finally { drawn() }
}
