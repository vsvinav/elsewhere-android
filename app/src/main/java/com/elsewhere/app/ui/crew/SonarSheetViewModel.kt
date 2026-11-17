package com.elsewhere.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.record.Live
import com.elsewhere.app.record.NowStamp
import com.elsewhere.app.sonar.SonarHit
import com.elsewhere.app.sonar.SonarSheets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The app-wide Sonar sheet: the engine's one open hit, and where the phone is while it shows, for the snap's place. */
@HiltViewModel
class SonarSheetViewModel(private val sheets: SonarSheets, stamps: Flow<NowStamp?>) : ViewModel() {
    @Inject constructor(sheets: SonarSheets, live: Live) : this(sheets, live.stamps)

    val sheet: StateFlow<SonarHit?> = sheets.sheet

    @OptIn(ExperimentalCoroutinesApi::class)
    val live: StateFlow<NowStamp?> = sheet.flatMapLatest { if (it == null) flowOf(null) else stamps }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss() = sheets.dismissSheet()
    fun snapped() = sheets.sheetSnapped()
}
