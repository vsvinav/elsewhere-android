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
import com.elsewhere.app.ui.shell.RangeStore
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
) {
    fun pct(t: DayType): Int = (100.0 * (stats.dayTypes[t] ?: 0) / max(1, days)).roundToInt()
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
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NumbersViewModel @Inject constructor(range: RangeStore, store: RecordStore) : ViewModel() {
    val ui: StateFlow<NumbersUi?> = combine(range.filtered, store.profile) { data, prof -> data?.let { it to prof } }
        .mapLatest { it?.let { (d, p) -> numbersUi(d, p) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
