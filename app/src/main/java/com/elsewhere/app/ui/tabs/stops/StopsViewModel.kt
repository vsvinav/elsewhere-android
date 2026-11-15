package com.elsewhere.app.ui.tabs.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.analytics.categoryOf
import com.elsewhere.app.analytics.placeName
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Role
import com.elsewhere.app.ingest.PlaceLookups
import com.elsewhere.app.ui.shell.RangeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class Sort(val label: String) { VISITS("Most visits"), HOURS("Most time"), RECENT("Recently seen"), FIRST("Newest") }

data class StopsFilter(val cat: Cat? = null, val sort: Sort = Sort.VISITS, val namedOnly: Boolean = false)

data class Stop(val id: String, val place: Place, val cat: Cat) {
    val name get() = placeName(place, id)
    val sub get() = listOfNotNull(place.city, place.countryCode).joinToString(", ").ifEmpty { "city not on record" }
    /** What Google calls it, or its role at home. */
    val kind: String? get() = when (place.role) { Role.HOME -> "Home"; Role.WORK -> "Work"; null -> place.primaryType?.replace('_', ' ') }
}

data class StopsUi(val list: List<Stop>, val total: Int, val named: Int, val cities: Int, val catCounts: Map<Cat, Int>, val filter: StopsFilter)

fun stopsUi(data: Record, filter: StopsFilter): StopsUi {
    val all = data.places.map { (id, p) -> Stop(id, p, categoryOf(p)) }
    val cmp: Comparator<Stop> = when (filter.sort) {
        Sort.VISITS -> compareByDescending { it.place.nVisits }
        Sort.HOURS -> compareByDescending { it.place.totalHours }
        Sort.RECENT -> compareByDescending { it.place.lastTs }
        Sort.FIRST -> compareByDescending { it.place.firstTs }
    }
    val list = all.filter { (filter.cat == null || it.cat == filter.cat) && (!filter.namedOnly || it.place.name != null) }.sortedWith(cmp)
    return StopsUi(
        list, all.size, all.count { it.place.name != null }, all.mapNotNull { it.place.city }.toSet().size,
        all.groupingBy { it.cat }.eachCount(), filter,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StopsViewModel @Inject constructor(private val range: RangeStore, private val lookups: PlaceLookups) : ViewModel() {
    private val filter = MutableStateFlow(StopsFilter())
    val ui: StateFlow<StopsUi?> = combine(range.filtered, filter) { d, f -> d?.let { it to f } }
        .mapLatest { it?.let { (d, f) -> stopsUi(d, f) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setFilter(f: StopsFilter) = filter.update { f }

    /** A tag has scrolled into view: ask for its name and photo. */
    fun seen(id: String) { range.filtered.value?.let { lookups.need(listOf(id), it.places) } }
}
