package com.elsewhere.app.ui.tabs.numbers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Bucket
import com.elsewhere.app.analytics.CommuteStats
import com.elsewhere.app.analytics.DayType
import com.elsewhere.app.analytics.MonthKm
import com.elsewhere.app.analytics.Stats
import com.elsewhere.app.analytics.commuteStats
import com.elsewhere.app.analytics.explorationScore
import com.elsewhere.app.analytics.stats
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.kmByLabel
import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.analytics.isWork
import com.elsewhere.app.ui.nav.CityIndex
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.monthDoor
import com.elsewhere.app.ui.nav.rangeDoor
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.world.cv
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.hours
import com.elsewhere.app.ui.world.month
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.u
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
import kotlin.math.max
import kotlin.math.roundToInt

private const val TOP_MODES = 3

data class Peak(val d: Int, val h: Int, val v: Double)

/** Port of the derived values at the top of web/src/tabs/Stats/Stats.tsx. */
data class NumbersUi(
    val units: Units, val stats: Stats, val commute: CommuteStats, val explore: List<Bucket>, val hasWork: Boolean,
    /** The top modes by printed name, the ones that take a colour. */
    val modes: List<String>, val maxMonth: Double, val best: MonthKm?, val avg: Double,
    val peak: Peak?, val byDay: List<Double>, val topDay: Int, val hoursAway: Double,
    val days: Int, val maxExplore: Int, val newTotal: Int, val lapsedTotal: Int, val hourMax: Int, val usualMode: String?,
    val countries: Int,
    val doors: NumbersDoors,
) {
    fun pct(t: DayType): Int = (100.0 * (stats.dayTypes[t] ?: 0) / max(1, days)).roundToInt()
    /** A month's kilometres by printed name, so the stack looks its colours up the same way `modes` was ranked. */
    fun monthByLabel(m: MonthKm): Map<String, Double> = kmByLabel(m.byMode).toMap()
}

/** A city row's doors, one per cell. */
data class CityDoors(val city: Door, val hours: Door, val visits: Door, val places: Door, val first: Door, val last: Door)

/**
 * Where each figure on the Numbers passes leads, built once with the figures: O(P) for the city and
 * home-or-work indexes, O(M) for the month columns, O(C) for the city rows, O(1) per other figure. Month
 * figures open on their month, city rows on their places, commute figures on home and work, the rest on
 * the window the figures were counted over. Peak hour and busiest weekday are not doors: no window holds them.
 */
class NumbersDoors(data: Record, private val units: Units, s: Stats, commute: CommuteStats, explore: List<Bucket>, best: MonthKm?, avg: Double, maxMonth: Double, hoursAway: Double, private val days: Int, newTotal: Int, lapsedTotal: Int, countries: Int) {
    private val range = data.meta.range
    private val u = units.u
    private fun cv(km: Double) = n0(units.cv(km))
    private val index = CityIndex(data.places)
    private val homeWork = data.places.filterValues { isHome(it) || isWork(it) }.keys.toList()
    fun whole(label: String) = rangeDoor(range, label)
    fun month(key: String, label: String) = monthDoor(key, label, range)
    fun city(city: String, label: String): Door = index.door(city, label)
    fun commute(label: String): Door = if (homeWork.isEmpty()) whole(label) else Door.Places(homeWork, label)
    private fun pct(n: Int) = (100.0 * n / max(1, days)).roundToInt()

    val bestMonth: Door? = best?.let { month(it.key, "Best month · ${month(it.key)} · ${cv(it.total)} $u") }
    val which: Door? = best?.let { month(it.key, "Best month · ${month(it.key)}") }
    val average: Door = whole("Average per month · ${cv(avg)} $u")
    val months: Door = whole("${s.months.size} months on record")
    val peakMonth: Door? = best?.let { month(it.key, "Peak month · ${month(it.key)} · ${cv(maxMonth)} $u") }
    /** Month chart columns, in the stats' order. */
    val monthColumns: List<Door> = s.months.map { m -> month(m.key, "${month(m.key)} · ${cv(m.total)} $u") }
    val hoursAway: Door = whole("${n0(hoursAway)} hours away")
    val dayTypes: Map<DayType, Door> = mapOf(
        DayType.AWAY to whole("${pct(s.dayTypes[DayType.AWAY] ?: 0)}% of days away"),
        DayType.WORK to commute("${pct(s.dayTypes[DayType.WORK] ?: 0)}% of days at work"),
        DayType.OUT to whole("${pct(s.dayTypes[DayType.OUT] ?: 0)}% of days out"),
        DayType.HOME to commute("${pct(s.dayTypes[DayType.HOME] ?: 0)}% of days home"),
    )
    val daysAway: Door = whole("${n0(s.dayTypes[DayType.AWAY] ?: 0)} days away")
    val legs: Door = commute("${n0(commute.legs.size)} commute legs")
    val toWork: Door = commute("To work · ${commute.toWork.medianMin.roundToInt()} min")
    val toHome: Door = commute("To home · ${commute.toHome.medianMin.roundToInt()} min")
    val badDay: Door = commute("Bad day · ${max(commute.toWork.p90Min, commute.toHome.p90Min).roundToInt()} min")
    val median: Door = commute("${commute.medianAll.roundToInt()} min door to door")
    val newPlaces: Door = whole("${n0(newTotal)} new places")
    val perMonth: Door = whole("${n0(newTotal / max(1, explore.size).toDouble())} new places a month")
    val lapsed: Door = whole("${n0(lapsedTotal)} lapsed places")
    /** Exploration chart columns, in the buckets' order. */
    val exploreColumns: List<Door> = explore.map { b -> month(b.key, "${month(b.key)} · ${b.new} new · ${b.repeat} repeat · ${b.lapsed} lapsed") }
    val cityCount: Door = whole("${s.cities.size} cities")
    val countryCount: Door = whole("$countries countries")
    /** City rows, in the stats' order: each cell is a door to the city's places; the dates to its span. */
    val cities: List<CityDoors> = s.cities.map { c ->
        CityDoors(
            city(c.city, "${c.city} · ${hours(c.hours)} · ${n0(c.visits)} visits · ${n0(c.places)} places"),
            city(c.city, "${c.city} · ${hours(c.hours)}"), city(c.city, "${c.city} · ${n0(c.visits)} visits"), city(c.city, "${c.city} · ${n0(c.places)} places"),
            Door.Range(c.first, c.last, "${c.city} · first visit ${date(c.first)}"), Door.Range(c.first, c.last, "${c.city} · last visit ${date(c.last)}"),
        )
    }
}

fun numbersUi(data: Record, profile: Profile?): NumbersUi {
    val s = stats(data)
    val commute = commuteStats(data, data.meta.range)
    val explore = explorationScore(data, data.meta.range)
    val totals = HashMap<String, Double>()
    for (m in s.months) for ((k, v) in m.byMode) totals[k] = (totals[k] ?: 0.0) + v
    // Only named modes take a colour; whatever prints as Other is the grey remainder.
    val modes = kmByLabel(totals).map { it.first }.filter { it != "Other" }.take(TOP_MODES)
    val maxMonth = max(1.0, s.months.maxOfOrNull { it.total } ?: 0.0)
    val best = s.months.maxByOrNull { it.total }
    val avg = s.months.sumOf { it.total } / max(1, s.months.size)
    var peak: Peak? = null
    s.rhythm.forEachIndexed { d, row -> row.forEachIndexed { h, v -> if (v > (peak?.v ?: 0.0)) peak = Peak(d, h, v) } }
    val byDay = s.rhythm.map { it.sum() }
    val topDay = byDay.indexOf(byDay.maxOrNull() ?: 0.0).coerceAtLeast(0)
    val days = s.dayTypes.values.sum()
    val units = profile?.units ?: Units.KM
    val countries = s.cities.mapNotNull { it.country?.takeIf { c -> c.isNotEmpty() } }.toSet().size
    return NumbersUi(
        units = units, stats = s, commute = commute, explore = explore,
        hasWork = !profile?.works.isNullOrEmpty() || data.profile.works.isNotEmpty(),
        modes = modes, maxMonth = maxMonth, best = best, avg = avg, peak = peak, byDay = byDay, topDay = topDay, hoursAway = byDay.sum(),
        days = days, maxExplore = max(1, explore.maxOfOrNull { it.new + it.repeat + it.lapsed } ?: 0),
        newTotal = explore.sumOf { it.new }, lapsedTotal = explore.sumOf { it.lapsed },
        hourMax = max(1, (commute.toWork.byHour + commute.toHome.byHour).maxOrNull() ?: 0),
        usualMode = commute.toWork.byMode.entries.sortedByDescending { it.value }.firstOrNull()?.key,
        countries = countries,
        doors = NumbersDoors(data, units, s, commute, explore, best, avg, maxMonth, byDay.sum(), days, explore.sumOf { it.new }, explore.sumOf { it.lapsed }, countries),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NumbersViewModel @Inject constructor(range: RangeStore, store: RecordStore) : ViewModel() {
    val ui: StateFlow<NumbersUi?> = combine(range.filtered, store.profile) { data, prof -> data?.let { it to prof } }
        .mapLatest { it?.let { (d, p) -> numbersUi(d, p) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
