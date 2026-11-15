package com.elsewhere.app.ui.record

import android.Manifest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.Recording
import com.elsewhere.app.record.PermissionGate
import com.elsewhere.app.record.RecordControl
import com.elsewhere.app.record.RecordStats
import com.elsewhere.app.record.lastFixText
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.plural
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The permissions recording needs, in the order Android lets us ask for them. */
enum class Perm(val permission: String, val minSdk: Int, val title: String, val note: String) {
    FINE(Manifest.permission.ACCESS_FINE_LOCATION, 1, "Location", "precise, while the app is open"),
    BACKGROUND(Manifest.permission.ACCESS_BACKGROUND_LOCATION, 29, "All the time", "so the record grows while the phone is in your pocket"),
    ACTIVITY(Manifest.permission.ACTIVITY_RECOGNITION, 29, "Motion", "walk, cycle or ride: names each leg, saves battery"),
    NOTIFY(Manifest.permission.POST_NOTIFICATIONS, 33, "Notifications", "a quiet line while recording, with Stop"),
}

enum class PermState { GRANTED, ASK, SETTINGS, LOCKED }
enum class Phase { DISCLOSURE, PERMISSIONS, RECORDING, PAUSED }

data class PermRow(val perm: Perm, val state: PermState)

data class RecordUi(
    val rows: List<PermRow>, val canStart: Boolean, val recording: Recording, val stats: RecordStats?, val batteryOk: Boolean, val now: Long,
) {
    val phase: Phase get() = when {
        rows.firstOrNull { it.perm == Perm.FINE }?.state != PermState.GRANTED -> Phase.DISCLOSURE
        !recording.enabled -> Phase.PERMISSIONS
        recording.pausedUntil > now -> Phase.PAUSED
        else -> Phase.RECORDING
    }
    val statusLine: String get() {
        val s = stats ?: return "Recording since ${date(recording.since)}"
        return "Recording since ${date(recording.since)} · ${n0(s.fixes)} ${plural(s.fixes, "fix", "fixes")} · ${n0(s.stays)} ${plural(s.stays, "stay")} · ${lastFixText(s.lastFixTs, now)}"
    }
}

/** Which permission rows to show and what each can do, from what the gate reports and what the user has refused. */
fun permissionRows(gate: PermissionGate, refused: Set<Perm>): List<PermRow> {
    val fine = gate.granted(Perm.FINE.permission)
    return Perm.entries.filter { gate.sdk >= it.minSdk }.map { p ->
        val state = when {
            gate.granted(p.permission) -> PermState.GRANTED
            p == Perm.BACKGROUND && !fine -> PermState.LOCKED
            p in refused -> PermState.SETTINGS
            // Android 11+ no longer shows a dialog for background location; the request itself lands in Settings.
            p == Perm.BACKGROUND && gate.sdk >= 30 -> PermState.SETTINGS
            else -> PermState.ASK
        }
        PermRow(p, state)
    }
}

@HiltViewModel
class RecordViewModel(
    private val gate: PermissionGate, private val control: RecordControl, private val now: () -> Long,
) : ViewModel() {
    @Inject constructor(gate: PermissionGate, control: RecordControl) : this(gate, control, { System.currentTimeMillis() / 1000 })

    private val refused = MutableStateFlow<Set<Perm>>(emptySet())
    private val tick = MutableStateFlow(0)
    private val stats = MutableStateFlow<RecordStats?>(null)

    val ui: StateFlow<RecordUi> = combine(control.recording, refused, tick, stats) { rec, ref, _, st ->
        val rows = permissionRows(gate, ref)
        RecordUi(
            rows = rows, canStart = rows.all { it.perm !in setOf(Perm.FINE, Perm.BACKGROUND) || it.state == PermState.GRANTED },
            recording = rec, stats = st, batteryOk = gate.ignoringBatteryOptimizations(), now = now(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RecordUi(permissionRows(gate, emptySet()), false, Recording(), null, true, now()))

    init { refresh() }

    /** Re-read the gate and the counters: on resume, and after every permission result. */
    fun refresh() {
        tick.update { it + 1 }
        viewModelScope.launch { stats.value = control.stats() }
    }

    fun onResult(perm: Perm, granted: Boolean) {
        refused.update { if (granted) it - perm else it + perm }
        refresh()
    }

    fun start() = viewModelScope.launch { control.start(); refresh() }
    fun stop() = viewModelScope.launch { control.stop(); refresh() }
    fun pause() = viewModelScope.launch { control.pause(); refresh() }
}
