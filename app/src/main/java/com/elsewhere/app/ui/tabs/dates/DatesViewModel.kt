package com.elsewhere.app.ui.tabs.dates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Night
import com.elsewhere.app.analytics.bedStrip
import com.elsewhere.app.analytics.dayKey
import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.LandSource
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.PlaceLookups
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.world.NightsUi
import com.elsewhere.app.ui.world.nightsUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One trip as its pass prints it closed: nights, flights, the modes that carried it. */
data class TripUi(val trip: Trip, val flights: Int, val modes: List<Pair<String, Double>>, val totalKm: Double)

/** A stop on a trip; `place` is null when the record never described it, and `placeName` falls back to the id. */
data class TripStop(val id: String, val place: Place?, val n: Int)

/** What an opened trip adds: where it slept and the places it kept returning to. Computed on demand. */
data class TripDetail(val beds: List<Night>, val visited: List<TripStop>)

data class DatesUi(
    val nights: NightsUi?, val trips: List<TripUi>, val nightsAway: Int, val flights: Int, val route: List<String>, val units: Units,
    val data: Record, val strip: List<Night>,
    /** The deck's cards, in the same order as `trips`; the land outlines are for their trail thumbnails. */
    val cards: List<Card> = emptyList(), val land: FeatureCollection? = null,
)

fun datesUi(data: Record, raw: Record, profile: Profile?, land: FeatureCollection? = null): DatesUi {
    val strip = bedStrip(raw, data.meta.range)
    val cards = deckCards(data)
    val trips = data.trips.sortedByDescending { it.start }.mapIndexed { i, t ->
        val km = t.totalKmByMode.entries.sortedByDescending { it.value }.map { it.key to it.value }
        TripUi(trip = t, flights = cards[i].flights, modes = km.take(4), totalKm = cards[i].km)
    }
    return DatesUi(
        nights = nightsUi(strip, data.meta.range, profile?.name ?: ""),
        trips = trips,
        nightsAway = trips.sumOf { it.trip.nights },
        flights = data.activities.count { it.mode == "FLYING" },
        route = trips.mapNotNull { it.trip.anchorCities.firstOrNull() }.distinct().take(4),
        units = profile?.units ?: Units.KM,
        data = data, strip = strip, cards = cards, land = land,
    )
}

/** Slices the trip's nights out of the whole-range strip and ranks its stops; only run for the open trip. */
fun tripDetail(data: Record, strip: List<Night>, t: Trip): TripDetail {
    val from = dayKey(t.start); val to = dayKey(t.end)
    val beds = strip.filter { it.date in from..to }
    val seen = LinkedHashMap<String, Int>()
    for (v in data.visits) if (v.lvl == 0 && v.start >= t.start && v.start <= t.end && !isHome(data.places[v.placeId])) seen[v.placeId] = (seen[v.placeId] ?: 0) + 1
    val visited = seen.entries.sortedByDescending { it.value }.take(8).map { (id, n) -> TripStop(id, data.places[id], n) }
    return TripDetail(beds, visited)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DatesViewModel @Inject constructor(private val range: RangeStore, store: RecordStore, landSource: LandSource, private val lookups: PlaceLookups) : ViewModel() {
    /** A stop on an open trip has scrolled into view: ask for its name and photo. */
    fun seen(id: String) { range.filtered.value?.let { lookups.need(listOf(id), it.places) } }

    private data class In(val data: Record, val raw: Record, val profile: Profile?, val land: FeatureCollection?)

    val ui: StateFlow<DatesUi?> = combine(range.filtered, range.raw, store.profile, flow { emit(landSource.get()) }) { d, r, p, l -> if (d == null || r == null) null else In(d, r, p, l) }
        .mapLatest { it?.run { datesUi(data, raw, profile, land) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
