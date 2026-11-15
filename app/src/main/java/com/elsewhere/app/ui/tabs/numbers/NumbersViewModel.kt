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
import com.elsewhere.app.ui.nav.CityIndex
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.monthDoor
import com.elsewhere.app.ui.nav.rangeDoor
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.world.WEEKDAYS
import com.elsewhere.app.ui.world.hours
import com.elsewhere.app.ui.world.month
import com.elsewhere.app.ui.world.n0
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
    val modes: List<String>, val maxMonth: Double, val best: MonthKm?, val avg: Double,
    val peak: Peak?, val byDay: List<Double>, val topDay: Int, val hoursAway: Double,
    val days: Int, val maxExplore: Int, val newTotal: Int, val lapsedTotal: Int, val hourMax: Int, val usualMode: String?,
    val countries: Int,
    val doors: NumbersDoors,
) {
    fun pct(t: DayType): Int = (100.0 * (stats.dayTypes[t] ?: 0) / max(1, days)).roundToInt()
}

/**
 * Where each figure on the Numbers passes leads, built once with the figures: O(P) for the city index, O(C)
 * for the city rows, O(1) per other figure. Month figures open on their month, city rows on their places, the
 * rest on the window the figures were counted over.
 */
class NumbersDoors(data: Record, s: Stats, commute: CommuteStats, best: MonthKm?, avg: Double, peak: Peak?, topDay: Int, hoursAway: Double, dayTypes: Map<DayType, Int>, days: Int, newTotal: Int, lapsedTotal: Int, months: Int) {
    private val range = data.meta.range
    private fun whole(label: String) = rangeDoor(range, label)
    private val index = CityIndex(data.places)

    val bestMonth: Door? = best?.let { monthDoor(it.key, "Best month · ${month(it.key)}") }
    val average: Door = whole("${n0(avg)} km a month on average")
    val months: Door = whole("$months months on record")
    val peakMonth: Door? = s.months.maxByOrNull { it.total }?.let { monthDoor(it.key, "${n0(it.total)} km peak · ${month(it.key)}") }
    val peakHour: Door? = peak?.let { whole("Peak hour · ${WEEKDAYS[it.d]} ${it.h.toString().padStart(2, '0')}:00") }
    val busiestDay: Door = whole("Busiest day out · ${WEEKDAYS.getOrElse(topDay) { "" }}")
    val hoursAway: Door = whole("${n0(hoursAway)} hours away")
    val dayTypes: Map<DayType, Door> = DayType.entries.associateWith { t -> whole("${(100.0 * (dayTypes[t] ?: 0) / max(1, days)).roundToInt()}% ${t.key} days") }
    val daysAway: Door = whole("${n0(dayTypes[DayType.AWAY] ?: 0)} days away")
    val legs: Door = whole("${n0(commute.legs.size)} commute legs")
    val toWork: Door = whole("To work · ${commute.toWork.medianMin.roundToInt()} min")
    val toHome: Door = whole("To home · ${commute.toHome.medianMin.roundToInt()} min")
    val badDay: Door = whole("Bad day · ${max(commute.toWork.p90Min, commute.toHome.p90Min).roundToInt()} min")
    val median: Door = whole("${n0(commute.medianAll)} min door to door")
    val newPlaces: Door = whole("${n0(newTotal)} new places")
    val perMonth: Door = whole("New places per month")
    val lapsed: Door = whole("${n0(lapsedTotal)} lapsed places")
    /** City rows, in the stats' order: the city's places, or its span when the record names none. */
    val cities: List<Door> = s.cities.map { c -> index.door(c.city, "${c.city} · ${hours(c.hours)}", Door.Range(c.first, c.last, c.city))!! }
}

fun numbersUi(data: Record, profile: Profile?): NumbersUi {
    val s = stats(data)
    val commute = commuteStats(data, data.meta.range)
    val explore = explorationScore(data, data.meta.range)
    val totals = HashMap<String, Double>()
    for (m in s.months) for ((k, v) in m.byMode) totals[k] = (totals[k] ?: 0.0) + v
    val modes = totals.entries.sortedByDescending { it.value }.take(TOP_MODES).map { it.key }
    val maxMonth = max(1.0, s.months.maxOfOrNull { it.total } ?: 0.0)
    val best = s.months.maxByOrNull { it.total }
    val avg = s.months.sumOf { it.total } / max(1, s.months.size)
    var peak: Peak? = null
    s.rhythm.forEachIndexed { d, row -> row.forEachIndexed { h, v -> if (v > (peak?.v ?: 0.0)) peak = Peak(d, h, v) } }
    val byDay = s.rhythm.map { it.sum() }
    val topDay = byDay.indexOf(byDay.maxOrNull() ?: 0.0).coerceAtLeast(0)
    val days = s.dayTypes.values.sum()
    return NumbersUi(
        units = profile?.units ?: Units.KM, stats = s, commute = commute, explore = explore,
        hasWork = !profile?.works.isNullOrEmpty() || data.profile.works.isNotEmpty(),
        modes = modes, maxMonth = maxMonth, best = best, avg = avg, peak = peak, byDay = byDay, topDay = topDay, hoursAway = byDay.sum(),
        days = days, maxExplore = max(1, explore.maxOfOrNull { it.new + it.repeat + it.lapsed } ?: 0),
        newTotal = explore.sumOf { it.new }, lapsedTotal = explore.sumOf { it.lapsed },
        hourMax = max(1, (commute.toWork.byHour + commute.toHome.byHour).maxOrNull() ?: 0),
        usualMode = commute.toWork.byMode.entries.sortedByDescending { it.value }.firstOrNull()?.key,
        countries = s.cities.mapNotNull { it.country?.takeIf { c -> c.isNotEmpty() } }.toSet().size,
        doors = NumbersDoors(data, s, commute, best, avg, peak, topDay, byDay.sum(), s.dayTypes, days, explore.sumOf { it.new }, explore.sumOf { it.lapsed }, s.months.size),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NumbersViewModel @Inject constructor(range: RangeStore, store: RecordStore) : ViewModel() {
    val ui: StateFlow<NumbersUi?> = combine(range.filtered, store.profile) { data, prof -> data?.let { it to prof } }
        .mapLatest { it?.let { (d, p) -> numbersUi(d, p) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
