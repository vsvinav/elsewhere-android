package com.elsewhere.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.Record
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

const val DAY = 86400L

/** Midnight UTC of the day holding `ts`; the web cuts days on UTC too. */
fun midnight(ts: Long): Long = Math.floorDiv(ts, DAY) * DAY
fun LocalDate.epoch(): Long = atStartOfDay(ZoneOffset.UTC).toEpochSecond()
fun dayOf(ts: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(ts, DAY))

sealed interface Preset {
    data object All : Preset
    data object Last90 : Preset
    data class Year(val year: Int) : Preset
}

/**
 * The date window every tab reads through. Port of the range half of web/src/store/useStore and
 * app/RangeBar.tsx: it starts as the whole record and never leaves the record's bounds.
 */
@HiltViewModel
class RangeViewModel @Inject constructor(store: RangeStore) : ViewModel() {
    private val _bounds = MutableStateFlow(0L..0L)
    private val _range = store.range
    /** The whole record's span. */
    val bounds: StateFlow<LongRange> = _bounds
    val range: StateFlow<LongRange> = _range

    init { store.raw.onEach { it?.let(::adopt) }.launchIn(viewModelScope) }

    /** The record cut to `range`; the whole record passes through untouched. */
    val filtered: StateFlow<Record?> = store.filtered

    /** Every calendar year the record touches, for the year chips. */
    val years: StateFlow<List<Int>> = _bounds.map { b ->
        if (b.last <= 0) emptyList() else yearsOf(b)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun adopt(rec: Record) {
        val b = rec.meta.range
        // A new import opens on the whole record; a re-derive of the same one keeps the window.
        val fresh = _bounds.value != b
        _bounds.value = b
        _range.value = if (fresh) b else clamp(_range.value.first, _range.value.last)
    }

    private fun clamp(a: Long, b: Long): LongRange {
        val r = _bounds.value
        return max(r.first, a)..min(r.last, b)
    }

    /** Both ends at once, for a door that opens an exact span. */
    fun setRange(r: LongRange) { _range.value = clamp(r.first, r.last) }

    fun setFrom(day: LocalDate) {
        val cur = _range.value
        _range.value = clamp(min(day.epoch(), midnight(cur.last)), cur.last)
    }

    fun setTo(day: LocalDate) {
        val cur = _range.value
        _range.value = clamp(cur.first, max(day.epoch() + DAY - 1, cur.first))
    }

    fun preset(p: Preset) { _range.value = rangeOf(p) }

    fun isPreset(p: Preset): Boolean = _range.value == rangeOf(p)

    private fun rangeOf(p: Preset): LongRange {
        val r = _bounds.value
        return when (p) {
            Preset.All -> r
            Preset.Last90 -> clamp(midnight(r.last) - 89 * DAY, r.last)
            is Preset.Year -> yearSpan(r, p.year)
        }
    }
}

