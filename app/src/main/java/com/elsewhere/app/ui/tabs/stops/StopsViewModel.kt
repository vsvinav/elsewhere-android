package com.elsewhere.app.ui.tabs.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.analytics.categoryOf
import com.elsewhere.app.analytics.placeName
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Role
import com.elsewhere.app.ingest.Geocoder
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Sort(val label: String) { VISITS("Most visits"), HOURS("Most time"), RECENT("Recently seen"), FIRST("Newest") }

data class StopsFilter(val cat: Cat? = null, val sort: Sort = Sort.VISITS, val namedOnly: Boolean = false)

data class Stop(val id: String, val place: Place, val cat: Cat) {
    val name get() = placeName(place, id)
    val sub get() = listOfNotNull(place.city, place.countryCode).joinToString(", ").ifEmpty { "city not on record" }
    /** The street line a tag shows, given what Nominatim said for it so far (see [stopAddress]). */
    fun address(nominatim: String?) = stopAddress(place.address, place.city, nominatim)
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
class StopsViewModel @Inject constructor(private val range: RangeStore, private val lookups: PlaceLookups, private val geocoder: Geocoder) : ViewModel() {
    private val filter = MutableStateFlow(StopsFilter())
    private val asked = HashSet<String>()
    /** Tags on screen whose Places lookup has not settled yet; Nominatim is only asked once it has. */
    private val waiting = LinkedHashSet<String>()
    private val streets = MutableStateFlow<Map<String, String?>>(emptyMap())
    /** What Nominatim said per place id, asked only once a tag without a Places street was on screen; null means it had nothing. */
    val addresses: StateFlow<Map<String, String?>> = streets
    val ui: StateFlow<StopsUi?> = combine(range.filtered, filter) { d, f -> d?.let { it to f } }
        .mapLatest { it?.let { (d, f) -> stopsUi(d, f) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init { viewModelScope.launch { merge(range.filtered, lookups.progress).collect { sweep() } } }

    fun setFilter(f: StopsFilter) = filter.update { f }

    /** A tag has scrolled into view: ask for its name and photo, and for its street once Places has had its say. */
    fun seen(id: String) {
        val places = range.filtered.value?.places ?: return
        lookups.need(listOf(id), places)
        if (places[id] == null || !asked.add(id)) return
        waiting += id
        sweep()
    }

    /** The Places street, from what has landed in the lookups or in the record. */
    private fun street(id: String, p: Place) = stopAddress(lookups.known(id)?.address ?: p.address, p.city, null)

    /** Tags whose Places lookup is over, or never ran, go to Nominatim if they still have no street. */
    private fun sweep() {
        val places = range.filtered.value?.places ?: return
        for (id in waiting.toList()) {
            if (lookups.pending(id)) continue
            waiting -= id
            val p = places[id] ?: continue
            if (street(id, p) != null) continue
            viewModelScope.launch {
                val now = range.filtered.value?.places?.get(id) ?: p
                if (street(id, now) != null) return@launch
                val a = geocoder.address(now.lat, now.lng)
                streets.update { it + (id to a) }
            }
        }
    }
}
