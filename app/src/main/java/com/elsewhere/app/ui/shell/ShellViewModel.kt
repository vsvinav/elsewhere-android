package com.elsewhere.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.crew.CrewPresence
import com.elsewhere.app.data.Counters
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.ReadyState
import com.elsewhere.app.data.Ready
import com.elsewhere.app.data.RecordSource
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.shellReady
import com.elsewhere.app.ingest.PlaceLookups
import com.elsewhere.app.ingest.Progress
import com.elsewhere.app.ui.nav.DoorNav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val REFRESH_TIMEOUT_MS = 15_000L

/** What the masthead menu reads and does: the name on the record, usage, start over; and the doors the figures open. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val repo: RecordStore, private val source: RecordSource, settings: MapsSettings, private val lookups: PlaceLookups,
    /** Every figure on the posters opens through here. */
    val doors: DoorNav, readyState: ReadyState = ReadyState(), presence: CrewPresence = CrewPresence.None,
) : ViewModel() {
    /** Someone in the crew has a fresh fix: the Crew stub wears a dot. */
    val crewLive: StateFlow<Boolean> = presence.live.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _refreshing = MutableStateFlow(false)
    /** True from a pull until the rebuilt record has landed, or the wait has given up. */
    val refreshing: StateFlow<Boolean> = _refreshing
    val naming: StateFlow<Progress?> = lookups.progress
    /** The bar waits for the names too: derive finishes long before slow Places lookups do, and the user needs to see something is still happening. */
    val ready: StateFlow<Ready?> = shellReady(readyState.ready.value, lookups.progress.value).let { initial ->
        combine(readyState.ready, lookups.progress, ::Pair)
            .runningFold(initial) { prev, (r, n) -> shellReady(r, n, prev) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initial)
    }
    val profile: StateFlow<Profile?> = repo.profile
    val counters: StateFlow<Counters> = settings.counters.stateIn(viewModelScope, SharingStarted.Eagerly, Counters())

    /** Pull to refresh: rebuild from Room (export plus device rows) and ask again for any names that failed. */
    fun refresh() {
        if (!_refreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                // A guard so the indicator never spins for ever, whatever the rebuild does.
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) { source.refresh() }
                lookups.retry()
            } finally { _refreshing.value = false }
        }
    }

    fun reset() { viewModelScope.launch { repo.reset(); lookups.forget() } }
}
