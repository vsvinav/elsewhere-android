package com.elsewhere.app.ui.tabs.facts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Fact
import com.elsewhere.app.analytics.HubFigures
import com.elsewhere.app.analytics.Kind
import com.elsewhere.app.analytics.Passport
import com.elsewhere.app.analytics.hubFigures
import com.elsewhere.app.analytics.passport
import com.elsewhere.app.analytics.facts
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.PlaceLookups
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.world.yearOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

val KIND_LABEL = mapOf(Kind.EXPLORED to "Explored", Kind.RECORD to "Record", Kind.SCALE to "Scale", Kind.HABIT to "Habit")

/** The card gets its own short facts; the list never repeats them. A thin record falls back to sharing. */
fun splitFacts(all: List<Fact>): Pair<List<Fact>, List<Fact>> {
    val cards = all.filter { it.card }
    return if (cards.size >= 2) cards to all.filter { !it.card } else all to all
}

/** Deterministic shuffle within each kind, so a reshuffle keeps the explored → habit arc. */
fun arrange(list: List<Fact>, seed: Int): List<Fact> {
    if (seed == 0) return list
    var s = seed.toLong() and 0xffffffffL
    fun rnd(): Double { s = (s * 1664525 + 1013904223) and 0xffffffffL; return s.toDouble() / 4294967296.0 }
    return Kind.entries.flatMap { k ->
        val part = list.filter { it.kind == k }.toMutableList()
        for (i in part.indices.reversed()) { if (i == 0) break; val j = (rnd() * (i + 1)).toInt(); part[i] = part[j].also { part[j] = part[i] } }
        part
    }
}

/** Everything the Facts tab prints. `hub` and `passport` are built once per record here, never on a swipe. */
data class FactsUi(val all: List<Fact>, val cards: List<Fact>, val rest: List<Fact>, val units: Units, val year: Int, val raw: Record? = null, val today: String? = null, val hub: HubFigures? = null, val passport: Passport? = null) {
    val kinds: List<Kind> get() = Kind.entries.filter { k -> rest.any { it.kind == k } }
    fun count(k: Kind) = rest.count { it.kind == k }
}

/** The thirty most visited places, the ones the facts are likeliest to name. */
fun busiest(places: Map<String, Place>, n: Int = 30): List<String> = places.entries.sortedByDescending { it.value.nVisits }.take(n).map { it.key }

fun factsUi(data: Record, raw: Record, profile: Profile?, today: String? = null): FactsUi {
    val units = profile?.units ?: Units.KM
    val all = facts(data, units, raw)
    val (cards, rest) = splitFacts(all)
    return FactsUi(all, cards, rest, units, yearOf(data.meta.range.last), raw, today, hubFigures(data), passport(raw))
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FactsViewModel @Inject constructor(range: RangeStore, store: RecordStore, lookups: PlaceLookups) : ViewModel() {
    // The facts quote the busiest places by name, so the top thirty are asked for as the tab opens (Facts.tsx).
    val ui: StateFlow<FactsUi?> = combine(range.filtered, range.raw, store.profile) { data, raw, prof -> if (data == null || raw == null) null else Triple(data, raw, prof) }
        .onEach { it?.second?.places?.let { ps -> lookups.need(busiest(ps), ps) } }
        .mapLatest { it?.let { (d, r, p) -> factsUi(d, r, p) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
