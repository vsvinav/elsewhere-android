package com.elsewhere.app.ui.shell

import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordSource
import com.elsewhere.app.data.filterData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one date window the whole app reads through. `RangeViewModel` moves it; every tab's view
 * model reads `filtered`. Filtering runs once, on the app scope, however many tabs are listening.
 */
@Singleton
class RangeStore @Inject constructor(source: RecordSource, scope: CoroutineScope) {
    /** The whole record, untouched by the window: firsts and "ever" judgements need it. */
    val raw: StateFlow<Record?> = source.record
    val range = MutableStateFlow(0L..0L)

    /** The record cut to `range`; the whole record passes through untouched. */
    val filtered: StateFlow<Record?> = combine(raw, range) { rec, r ->
        if (rec == null || r == rec.meta.range) rec else filterData(rec, r)
    }.stateIn(scope, SharingStarted.Eagerly, null)
}
