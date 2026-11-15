package com.elsewhere.app.ui.tabs.tour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.HomeFacts
import com.elsewhere.app.analytics.Tour
import com.elsewhere.app.analytics.bedStrip
import com.elsewhere.app.analytics.homeFacts
import com.elsewhere.app.analytics.placeName
import com.elsewhere.app.analytics.tour
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.LandSource
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.PlaceLookups
import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.dayDoor
import com.elsewhere.app.ui.nav.doorId
import com.elsewhere.app.ui.nav.onePerCity
import com.elsewhere.app.ui.nav.onePerCountry
import com.elsewhere.app.ui.nav.rangeDoor
import com.elsewhere.app.ui.nav.yearDoor
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.world.cv
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.label
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.plural
import com.elsewhere.app.analytics.toFixed
import com.elsewhere.app.ui.world.u
import com.elsewhere.app.ui.world.NightsUi
import com.elsewhere.app.ui.world.TrailLayout
import com.elsewhere.app.ui.world.nightsUi
import com.elsewhere.app.ui.world.trailLayout
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

data class TourUi(
    val name: String, val units: Units, val facts: HomeFacts, val tour: Tour, val trail: TrailLayout,
    val nights: NightsUi?, val actPlace: Place?, val actName: String?, val actSub: String?,
    val hasWork: Boolean, val homeCity: String?, val record: Record, val named: Int, val photos: Int,
    /** Days with at least one stay the phone recorded itself, as opposed to the export. */
    val deviceDays: Int = 0,
    val doors: TourDoors = TourDoors(record, units),
    /** The whole record's span, the year wheel's track; the range only ever cuts inside it. */
    val bounds: LongRange = record.meta.range,
) {
    val yearSpan: String get() = tour.years.let { (a, b) -> if (a == b) "$a" else "$a–$b" }
}

/**
 * Where each figure on the Tour poster leads, worked out once with the rest of the poster: O(P) for the city
 * index, then O(1) per figure, so the bills build no doors while they draw.
 */
class TourDoors(data: Record, private val units: Units, facts: HomeFacts = homeFacts(data), t: Tour = tour(data)) {
    private val range = data.meta.range
    private val u = units.u
    private fun cv(km: Double) = n0(units.cv(km))
    fun whole(label: String) = rangeDoor(range, label)
    fun year(y: Int, label: String) = yearDoor(y, label, range)
    fun day(date: String, label: String) = dayDoor(date, label)

    val days: Door = whole("${n0(facts.days)} days of record")
    val places: Door = Door.Places(data.places.keys.toList(), "${n0(facts.places)} places")
    val cities: Door = Door.Places(onePerCity(data.places), "${n0(facts.cities)} cities")
    val countries: Door = Door.Places(onePerCountry(data.places), "${n0(facts.countries)} ${plural(facts.countries, "country", "countries")}")
    val distance: Door = whole("${cv(facts.totalKm)} $u")
    val distanceOnRecord: Door = whole("${cv(facts.totalKm)} $u on record")
    /** Stops, in the tour's order: each opens on the nights spent there. */
    val stops: List<Door> = t.stops.map { s -> Door.Range(s.first, s.last, "${s.city} · ${s.nights} ${plural(s.nights, "night")}") }
    val act: Door? = t.headline?.let { Door.Place(it.placeId, it.start, "${it.city} · ${cv(it.km)} $u from home") }
    val actNights: Door? = t.headline?.let { Door.Range(it.start, it.end, "${it.city} · ${it.nights} ${plural(it.nights, "night")}") }
    val longestTrip: Door? = facts.longestTrip?.let { Door.Trip(it.trip.doorId, it.trip.start, it.trip.end, "Longest trip · ${it.trip.nights} nights") }
    val nightsAway: Door = Door.Places(t.stops.flatMap { it.placeIds }, "${n0(t.nightsAway)} nights away")
    val nightsHome: Door = data.places.filterValues { isHome(it) }.keys.toList().let { homes -> if (homes.isEmpty()) whole("${n0(t.nightsHome)} nights home") else Door.Places(homes, "${n0(t.nightsHome)} nights home") }
    val trips: Door = whole("${facts.trips} ${plural(facts.trips, "trip")}")
    val laps: Door = whole("${toFixed(facts.totalKm / 40075, 1)}× around the Earth")
    val flights: Door = whole("${facts.flights} ${plural(facts.flights, "flight")}")
    val modes: Map<String, Door> = facts.kmByMode.associate { (m, km) -> m to whole("${label(m)} · ${cv(km)} $u") }
    val hoursOut: Door = whole("${n0(facts.hours.other)} hours out")
    val thirdPlaces: List<Door> = facts.thirdPlaces.map { Door.Place(it.id, label = "${it.name} · ${it.place.nVisits} visits") }
    val longestDay: Door? = facts.records.longestDay?.let { day(it.date, "Longest day · ${cv(it.km)} $u") }
    val busiestDay: Door? = facts.records.busiestDay?.let { day(it.date, "Busiest day · ${it.places} places") }
    /** The year table, a door per cell: the year itself names the whole row. */
    val years: List<List<Door>> = facts.years.map { y ->
        fun yr(label: String) = year(y.year, "${y.year} · $label")
        listOf(yr("${cv(y.km)} $u · ${n0(y.places)} places · ${y.trips} trips · ${y.flights} flights"), yr("${cv(y.km)} $u"), yr("${n0(y.places)} places"), yr("${y.trips} trips"), yr("${y.flights} flights"))
    }
    val record: Door = whole("${date(range.first)} → ${date(range.last)}")
}

/** Everything the Tour poster prints, derived once per record, range, profile and land. */
fun tourUi(data: Record, raw: Record, profile: Profile?, land: FeatureCollection?): TourUi {
    val f = homeFacts(data)
    val t = tour(data)
    val act = t.headline
    val actPlace = act?.let { data.places[it.placeId] }
    val name = profile?.name ?: ""
    return TourUi(
        name = name, units = profile?.units ?: Units.KM, facts = f, tour = t,
        trail = trailLayout(data, t.stops, land),
        nights = nightsUi(bedStrip(raw, data.meta.range), data.meta.range, name),
        actPlace = actPlace, actName = act?.let { placeName(actPlace, it.placeId) },
        actSub = act?.let { listOfNotNull(actPlace?.address, it.country).joinToString(" · ").ifEmpty { null } },
        hasWork = !profile?.works.isNullOrEmpty(), homeCity = profile?.homes?.lastOrNull()?.city,
        record = data, named = data.places.values.count { it.name != null }, photos = data.places.values.count { it.photo != null },
        deviceDays = deviceDays(data),
        doors = TourDoors(data, profile?.units ?: Units.KM, f, t),
        bounds = raw.meta.range,
    )
}

/** Device stays carry `dev:` place ids; count the distinct local days they fall on. */
fun deviceDays(r: Record): Int = r.visits.asSequence().filter { it.placeId.startsWith("dev:") }
    .map { Math.floorDiv(it.start + it.tz * 60L, 86400L) }.distinct().count()

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TourViewModel @Inject constructor(private val range: RangeStore, store: RecordStore, landSource: LandSource, private val lookups: PlaceLookups) : ViewModel() {
    /** A third-place row has scrolled into view: ask for its name and photo. */
    fun seen(id: String) { range.filtered.value?.let { lookups.need(listOf(id), it.places) } }

    /** The date window the wheel scrubs. */
    val window: StateFlow<LongRange> = range.range
    fun setRange(r: LongRange) { range.range.value = r }

    private data class In(val data: Record, val raw: Record, val profile: Profile?, val land: FeatureCollection?)

    val ui: StateFlow<TourUi?> = combine(range.filtered, range.raw, store.profile, flow { emit(landSource.get()) }) { data, raw, prof, l ->
        if (data == null || raw == null) null else In(data, raw, prof, l)
    }.mapLatest { it?.run { tourUi(data, raw, profile, land) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
