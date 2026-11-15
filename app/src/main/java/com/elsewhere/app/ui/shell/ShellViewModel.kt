package com.elsewhere.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.Counters
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.RecordRepository
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the masthead menu reads and does: the name on the record, units, usage, start over. */
@HiltViewModel
class ShellViewModel @Inject constructor(private val repo: RecordRepository, settings: SettingsStore) : ViewModel() {
    val profile: StateFlow<Profile?> = repo.profile
    val counters: StateFlow<Counters> = settings.counters.stateIn(viewModelScope, SharingStarted.Eagerly, Counters())

    fun toggleUnits() {
        val p = profile.value ?: return
        viewModelScope.launch { repo.saveProfile(p.copy(units = if (p.units == Units.KM) Units.MI else Units.KM)) }
    }

    fun reset() { viewModelScope.launch { repo.reset() } }
}

/** USD at list price per 1,000 calls, before Google's monthly free allowance; mirrors web/src/usage/usage.ts. */
fun Counters.dollars(): Double = (detailsCalls * 17 + photoCalls * 7 + mapLoads * 7) / 1000.0
