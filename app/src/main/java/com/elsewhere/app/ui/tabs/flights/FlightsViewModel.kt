package com.elsewhere.app.ui.tabs.flights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Airport
import com.elsewhere.app.analytics.Flights
import com.elsewhere.app.analytics.Hop
import com.elsewhere.app.analytics.Port
import com.elsewhere.app.analytics.flights
import com.elsewhere.app.data.AirportsLoader
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.LandSource
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.shell.RangeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Everything the Flights page prints, the derived values at the top of web/src/tabs/Flights/Flights.tsx. */
data class FlightsUi(
    val units: Units, val flights: Flights, val land: FeatureCollection?,
    /** Legs flown, return legs counted separately. */
    val legs: Int, val km: Double, val longest: Hop?,
    /** Routes, most flown first; the list and the globe share this order. */
    val byN: List<Hop>, val centre: Rotation,
) {
    val hops: List<Hop> get() = flights.hops
    val ports: List<Port> get() = flights.ports

    /** The airports with a town, busiest first: the ones the From bill can pick between. */
    val named: List<Port> get() = namedPorts(ports)

    /** The town of a runway, or "somewhere" when no city was visited near it. */
    fun name(p: LatLng): String = ports.firstOrNull { near(it.p, p) }?.name?.ifEmpty { null } ?: "somewhere"

    /** The From bill for the `pick`th named airport, or null when no airport has a town. */
    fun from(pick: Int): FromAirport? = fromAirport(hops, ports, pick, ::name)
}

fun flightsUi(data: Record, profile: Profile?, airports: List<Airport>, land: FeatureCollection?): FlightsUi {
    val f = flights(data, airports)
    return FlightsUi(
        units = profile?.units ?: Units.KM, flights = f, land = land,
        legs = f.hops.sumOf { it.n }, km = f.hops.sumOf { it.km * it.n }, longest = f.hops.maxByOrNull { it.km },
        byN = f.hops.sortedWith(compareByDescending<Hop> { it.n }.thenByDescending { it.km }), centre = bestCentre(f.ports),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FlightsViewModel @Inject constructor(range: RangeStore, store: RecordStore, airportsLoader: AirportsLoader, landSource: LandSource) : ViewModel() {
    private data class In(val data: Record, val profile: Profile?, val airports: List<Airport>, val land: FeatureCollection?)

    val ui: StateFlow<FlightsUi?> = combine(range.filtered, store.profile, flow { emit(airportsLoader.get()) }, flow { emit(landSource.get()) }) { data, prof, a, l ->
        data?.let { In(it, prof, a, l) }
    }.mapLatest { it?.run { flightsUi(data, profile, airports, land) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The route picked on the globe or in the ledger, as an index into `FlightsUi.byN`. */
    val selected = MutableStateFlow<Int?>(null)

    /** Picking the open route again closes it. */
    fun select(i: Int?) { selected.value = if (i == selected.value) null else i }

    private val pickState = ResettingPick(ui)

    /** The airport the From bill shows, as an index into `FlightsUi.named`; a new window puts it back to the busiest. */
    val pick: StateFlow<Int> = pickState.index.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun pick(i: Int) = pickState.set(i)
}
