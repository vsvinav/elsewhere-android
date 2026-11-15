package com.elsewhere.app.ui.tabs.facts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Passport
import com.elsewhere.app.analytics.passport
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.ui.shell.RangeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The booklet and the stamps it has already shown as earned (null before the first open). */
data class PassportUi(val passport: Passport, val seen: Set<String>?)

/** The passport is judged over the whole record, like the firsts in Facts, so it reads `raw` and not the ranged view. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PassportViewModel @Inject constructor(range: RangeStore, private val settings: SettingsStore) : ViewModel() {
    private val booklet = range.raw.mapLatest { it?.let(::passport) }.flowOn(Dispatchers.Default)
    val ui: StateFlow<PassportUi?> = combine(booklet, settings.passportSeen.distinctUntilChanged()) { p, seen -> p?.let { PassportUi(it, seen) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun seen(ids: Set<String>) { viewModelScope.launch { settings.savePassportSeen(ids) } }
}
