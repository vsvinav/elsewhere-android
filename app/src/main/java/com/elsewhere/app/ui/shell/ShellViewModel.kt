package com.elsewhere.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.Counters
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.EnrichState
import com.elsewhere.app.ingest.Progress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the masthead menu reads and does: the name on the record, units, usage, start over. */
@HiltViewModel
class ShellViewModel @Inject constructor(private val repo: RecordStore, settings: MapsSettings, enrich: EnrichState) : ViewModel() {
    val naming: StateFlow<Progress?> = enrich.progress
    val profile: StateFlow<Profile?> = repo.profile
    val counters: StateFlow<Counters> = settings.counters.stateIn(viewModelScope, SharingStarted.Eagerly, Counters())

    fun toggleUnits() {
        val p = profile.value ?: return
        viewModelScope.launch { repo.saveProfile(p.copy(units = if (p.units == Units.KM) Units.MI else Units.KM)) }
    }

    fun reset() { viewModelScope.launch { repo.reset() } }
}
