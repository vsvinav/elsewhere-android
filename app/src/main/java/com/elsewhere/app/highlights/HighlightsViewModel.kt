package com.elsewhere.app.highlights

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The one setting behind the daily notification: the minute it prints, or null for off. */
@HiltViewModel
class HighlightsViewModel @Inject constructor(private val settings: SettingsStore, @ApplicationContext private val ctx: Context) : ViewModel() {
    val at: StateFlow<Int?> = settings.highlightsAt.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun set(minute: Int?) = viewModelScope.launch {
        settings.saveHighlightsAt(minute)
        if (minute == null) HighlightWorker.cancel(ctx) else HighlightWorker.schedule(ctx, minute)
    }
}
