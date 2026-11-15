package com.elsewhere.app.ingest

import com.elsewhere.app.data.Counters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

// Port of web/src/usage/usage.ts: a rough tally of billable Google calls, so a key owner sees what the app costs.

/** `rate` is USD per 1,000 calls at list price, before Google's monthly free allowance. */
enum class Kind(val rate: Int, val label: String) { DETAILS(17, "Place lookups"), PHOTOS(7, "Place photos"), MAPS(7, "Map loads") }

fun Counters.dollars(): Double = (detailsCalls * Kind.DETAILS.rate + photoCalls * Kind.PHOTOS.rate + mapLoads * Kind.MAPS.rate) / 1000.0

/** How many more Details calls fit under `capDollars` at list price; never negative. */
fun Counters.detailsBudget(capDollars: Double): Int = kotlin.math.floor(((capDollars - dollars()) * 1000) / Kind.DETAILS.rate + 1e-9).toInt().coerceAtLeast(0)

/** Where the counts live; `SettingsStore` persists them, tests keep them in memory. */
interface CostMeter {
    val counters: Flow<Counters>
    suspend fun tally(kind: Kind, n: Int = 1)
    suspend fun reset()
}

class MemoryCostMeter(initial: Counters = Counters()) : CostMeter {
    override val counters = MutableStateFlow(initial)
    override suspend fun tally(kind: Kind, n: Int) { counters.update { it.plus(kind, n) } }
    override suspend fun reset() { counters.value = Counters(since = System.currentTimeMillis()) }
}

fun Counters.plus(kind: Kind, n: Int): Counters = when (kind) {
    Kind.DETAILS -> copy(detailsCalls = detailsCalls + n)
    Kind.PHOTOS -> copy(photoCalls = photoCalls + n)
    Kind.MAPS -> copy(mapLoads = mapLoads + n)
}
