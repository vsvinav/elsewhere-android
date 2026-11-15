package com.elsewhere.app.ui.tabs.dates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Night
import com.elsewhere.app.analytics.bedStrip
import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.world.NightsUi
import com.elsewhere.app.ui.world.nightsUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One trip with everything its pass prints: beds, flights, the modes that carried it, the places it kept returning to. */
data class TripUi(
    val trip: Trip, val flights: Int, val beds: List<Night>, val modes: List<Pair<String, Double>>, val totalKm: Double,
    val visited: List<Pair<Place, Int>>, val visitedIds: List<String>,
)

data class DatesUi(val nights: NightsUi?, val trips: List<TripUi>, val nightsAway: Int, val flights: Int, val route: List<String>, val units: Units)

fun datesUi(data: Record, raw: Record, profile: Profile?): DatesUi {
    val trips = data.trips.sortedByDescending { it.start }.map { t ->
        val km = t.totalKmByMode.entries.sortedByDescending { it.value }.map { it.key to it.value }
        val seen = LinkedHashMap<String, Int>()
        for (v in data.visits) if (v.lvl == 0 && v.start >= t.start && v.start <= t.end && !isHome(data.places[v.placeId])) seen[v.placeId] = (seen[v.placeId] ?: 0) + 1
        val top = seen.entries.sortedByDescending { it.value }.take(8)
        TripUi(
            trip = t,
            flights = data.activities.count { it.mode == "FLYING" && it.start >= t.start && it.start <= t.end },
            beds = bedStrip(raw, t.start..t.end),
            modes = km.take(4), totalKm = km.sumOf { it.second },
            visited = top.mapNotNull { (id, n) -> data.places[id]?.let { it to n } },
            visitedIds = top.map { it.key },
        )
    }
    return DatesUi(
        nights = nightsUi(bedStrip(raw, data.meta.range), data.meta.range, profile?.name ?: ""),
        trips = trips,
        nightsAway = trips.sumOf { it.trip.nights },
        flights = data.activities.count { it.mode == "FLYING" },
        route = trips.mapNotNull { it.trip.anchorCities.firstOrNull() }.distinct().take(4),
        units = profile?.units ?: Units.KM,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DatesViewModel @Inject constructor(range: RangeStore, store: RecordStore) : ViewModel() {
    val ui: StateFlow<DatesUi?> = combine(range.filtered, range.raw, store.profile) { d, r, p -> if (d == null || r == null) null else Triple(d, r, p) }
        .mapLatest { it?.let { (d, r, p) -> datesUi(d, r, p) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
