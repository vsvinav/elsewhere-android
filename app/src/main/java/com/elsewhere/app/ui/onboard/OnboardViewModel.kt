package com.elsewhere.app.ui.onboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.Move
import com.elsewhere.app.analytics.Suggestion
import com.elsewhere.app.analytics.moveMonths
import com.elsewhere.app.analytics.sleepPlaces
import com.elsewhere.app.analytics.weekdayPlaces
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.ImportReport
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.ReadyState
import com.elsewhere.app.data.shellReady
import com.elsewhere.app.data.Ready
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.Work
import com.elsewhere.app.ingest.Geocoder
import com.elsewhere.app.ingest.KeyVerifier
import com.elsewhere.app.ingest.PlaceNamer
import com.elsewhere.app.ingest.Progress
import com.elsewhere.app.ingest.properName
import com.elsewhere.app.ingest.KeyReport
import com.elsewhere.app.record.RecordControl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import javax.inject.Inject

// Port of web/src/profile/Onboarding.tsx: the state machine behind the front door.

enum class Step { Record, Import, Key, Name, Home, Moves, Work, Units, Done }

/** The door's two openings: start recording, or bring an export. Everything after is editing. */
val ENTRY = setOf(Step.Record, Step.Import)

/** The key page's three faces: the field, the probes running, the verdict (or the no-key acknowledgement). */
enum class KeyPhase { Entry, Checking, Result }

/** The five questions, in order; `Import` and `Key` come before, `Done` after. */
val QUESTIONS = listOf(Step.Name, Step.Home, Step.Moves, Step.Work, Step.Units)

enum class Sure(val label: String) { LIKELY("Likely home"), MAYBE("Maybe home"), STAYED("Stayed here") }

/** A ranked place with the name we can print for it. */
data class Candidate(
    val placeId: String, val lat: Double, val lng: Double, val city: String?, val cc: String?,
    val label: String, val address: String?, val score: Double, val share: Double, val memberIds: List<String>,
    /** True once the label is a real name (shop, flat, park) rather than a city or a street number. */
    val named: Boolean = true,
    /** The phone's position right now, standing in for a home when the record has no nights yet. */
    val here: Boolean = false,
) {
    val members: Int get() = memberIds.size
    val sure: Sure get() = when { share >= 0.6 -> Sure.LIKELY; share >= 0.3 -> Sure.MAYBE; else -> Sure.STAYED }
    fun matches(h: Home) = h.lat == lat && h.lng == lng
}

data class OnboardState(
    val step: Step = Step.Record,
    val editing: Boolean = false,
    /** Whether the phone is recording; the import can be skipped only then. */
    val recording: Boolean = false,
    val importing: Float? = null,
    val importError: String? = null,
    val report: ImportReport? = null,
    val range: LongRange = 0L..0L,
    val sleep: List<Candidate> = emptyList(),
    /** Set while the shared lookups are still naming candidate cards (the web's `useNaming`). */
    val naming: Progress? = null,
    /** True while a naming run is in flight, so cards still saying a city can show they are being asked about. */
    val asking: Boolean = false,
    val weekday: List<Candidate> = emptyList(),
    val moves: List<Move> = emptyList(),
    val name: String = "",
    val homes: List<Home> = emptyList(),
    val works: List<Work> = emptyList(),
    val units: Units = Units.KM,
    val key: String = "",
    val mapId: String = "",
    val keyPhase: KeyPhase = KeyPhase.Entry,
    /** The last check's verdict; null on the Result face means the key was skipped. */
    val keyReport: KeyReport? = null,
    val limitedAck: Boolean = false,
) {
    val questionIndex: Int get() = QUESTIONS.indexOf(step)
}

/** Everything the question screens can do; the view model is the real one, tests pass a no-op. */
interface OnboardActions {
    fun setName(s: String)
    fun pickHome(c: Candidate)
    fun addHome(c: Candidate)
    fun setHomeFrom(i: Int, m: YearMonth)
    fun setHomeTo(i: Int, m: YearMonth?)
    fun removeHome(i: Int)
    fun toggleWork(c: Candidate)
    fun setWorkFrom(i: Int, m: YearMonth)
    fun setWorkTo(i: Int, m: YearMonth?)
    fun removeWork(i: Int)
    fun setUnits(u: Units)
    fun next()
    fun back()
    fun skip()
    fun finish()
}

private val MILES = setOf("US", "GB", "LR", "MM")

fun YearMonth.epoch(): Long = atDay(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
fun monthOf(ts: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(Math.floorDiv(ts, 86400L)))

@HiltViewModel
class OnboardViewModel @Inject constructor(
    private val store: RecordStore, private val maps: MapsSettings, private val verifier: KeyVerifier,
    private val namer: PlaceNamer, private val geocoder: Geocoder, private val control: RecordControl, readyState: ReadyState = ReadyState(),
) : ViewModel(), OnboardActions, KeyActions {
    /** How far the current naming run is, so the readiness bar keeps going while the cards get their names. */
    private val cards = MutableStateFlow<Progress?>(null)
    val ready: StateFlow<Ready?> = shellReady(readyState.ready.value, null).let { initial ->
        combine(readyState.ready, cards, ::Pair)
            .runningFold(initial) { prev, (r, n) -> shellReady(r, n, prev) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initial)
    }
    private val _state = MutableStateFlow(OnboardState())
    val state: StateFlow<OnboardState> = _state
    /** The record the cards were ranked from, kept so a new key can name them again. */
    private var rec: Record? = null
    private var naming: Job? = null

    init {
        viewModelScope.launch { namer.progress.collect { p -> _state.update { it.copy(naming = p) } } }
        viewModelScope.launch { control.recording.collect { r -> _state.update { it.copy(recording = r.enabled) } } }
        // The web's naming effect is keyed on `mapsKey`: a key verified after import names the cards again.
        viewModelScope.launch {
            maps.settings.map { it.mapsKey to it.keyPlaces }.distinctUntilChanged().collect { (key, places) -> rename(key, places) }
        }
    }
    /**
     * Open the door at `step`: `Record` for a new record (which becomes `Import` when the phone is already
     * recording, or the first question when there is already something to rank), a question or the key when editing.
     */
    fun start(step: Step) {
        val editing = step !in ENTRY
        val resume = !editing && store.hasImport.value
        val p = store.profile.value
        _state.value = OnboardState(step = if (resume) Step.Name else step, editing = editing, recording = _state.value.recording, name = p?.name ?: "", homes = p?.homes ?: emptyList(), works = p?.works ?: emptyList(), units = p?.units ?: Units.KM)
        viewModelScope.launch {
            val s = maps.settings.first()
            val on = control.recording.first().enabled
            _state.update { it.copy(key = s.mapsKey, mapId = s.mapId, recording = on, step = if (it.step == Step.Record && on) Step.Import else it.step) }
            if (store.hasImport.value) suggest()
        }
    }

    /** "Start recording": the recorder comes on and the door moves to the export. */
    fun startRecording() {
        viewModelScope.launch { control.start(); _state.update { it.copy(step = Step.Import) } }
    }

    /** "I have a Google Timeline export instead": straight to the export, nothing recording. */
    fun skipRecord() { _state.update { it.copy(step = Step.Import) } }

    /** "Skip for now" on the export, while recording: the key, then the questions, with the phone's position for a home. */
    fun skipImport() {
        viewModelScope.launch {
            here()
            _state.update { it.copy(step = if (it.key.isBlank()) Step.Key else Step.Name) }
        }
    }

    fun import(open: () -> InputStream, fileName: String) {
        if (_state.value.importing != null) return
        _state.update { it.copy(importing = 0f, importError = null, report = null) }
        viewModelScope.launch {
            try {
                val report = store.import(open, fileName) { f -> _state.update { it.copy(importing = f) } }
                suggest()
                _state.update { it.copy(importing = null, report = report, step = if (it.key.isBlank()) Step.Key else Step.Name) }
            } catch (e: Exception) {
                _state.update { it.copy(importing = null, importError = e.message ?: "Couldn't read that file") }
            }
        }
    }

    private suspend fun suggest() {
        val rec = store.provisionalRecord() ?: return
        this.rec = rec
        _state.update {
            it.copy(range = rec.meta.range, sleep = sleepPlaces(rec, 8).map { s -> candidate(s, rec) }, weekday = weekdayPlaces(rec).map { s -> candidate(s, rec) }, moves = moveMonths(rec))
        }
        val s = maps.settings.first()
        rename(s.mapsKey, s.keyPlaces)
        if (_state.value.sleep.isEmpty()) here()
    }

    /** No nights to rank: the phone's latest fix, named by its street, is the one card for home. */
    private suspend fun here() {
        val fix = control.latestFix() ?: return
        val label = attempt { geocoder.address(fix.lat, fix.lng) } ?: "Where you are now"
        val card = Candidate("here", fix.lat, fix.lng, null, null, label, null, 0.0, 0.0, listOf("here"), here = true)
        _state.update { if (it.sleep.isEmpty()) it.copy(sleep = listOf(card)) else it }
    }

    /** The web's `label(s)`: a Places name, else the stored name, else the city. */
    private fun candidate(s: Suggestion, rec: Record): Candidate {
        val p = rec.places[s.placeId]
        val named = p != null && properName(p.name, p.types, p.primaryType)
        return Candidate(s.placeId, s.lat, s.lng, s.city, s.cc, p?.name ?: s.city ?: "Unnamed place", p?.address, s.score, s.share, s.members, named)
    }

    /** Name whatever cards still only say a city; one run at a time, the last word on the key winning. */
    private fun rename(key: String, keyPlaces: Boolean) {
        val rec = rec ?: return
        val prev = naming
        naming = viewModelScope.launch {
            prev?.cancelAndJoin()
            val todo = (_state.value.sleep + _state.value.weekday).filter { !it.named }.distinctBy { it.placeId }
            if (todo.isEmpty()) return@launch
            _state.update { it.copy(asking = true) }
            cards.value = Progress(0, todo.size)
            // Only the run still on record lowers the flag, so an older job's unwind cannot clear a newer run's.
            try { nameCandidates(todo, rec, key, key.isNotBlank() && keyPlaces) } finally {
                if (naming === coroutineContext[Job]) { _state.update { it.copy(asking = false) }; cards.value = null }
            }
        }
    }

    /**
     * Cards that only say a city get a better line, as the web's Onboarding does: a Places name when the key
     * works, else a street and neighbourhood from Nominatim. Each lands as it arrives.
     */
    private suspend fun nameCandidates(todo: List<Candidate>, rec: Record, key: String, paid: Boolean) {
        for ((i, c) in todo.withIndex()) { name(c, rec, key, paid); cards.value = Progress(i + 1, todo.size) }
    }

    private suspend fun name(c: Candidate, rec: Record, key: String, paid: Boolean) {
        val name = if (paid) attempt { namer.name(c.memberIds, rec.places, key) } else null
        val patch: (Candidate) -> Candidate = if (name != null) {
            { it.copy(label = name, named = true) }
        } else {
            if (c.address != null) return
            val addr = attempt { geocoder.address(c.lat, c.lng) } ?: return
            { it.copy(address = it.address ?: addr) }
        }
        _state.update { st -> st.copy(sleep = st.sleep.map { if (it.placeId == c.placeId) patch(it) else it }, weekday = st.weekday.map { if (it.placeId == c.placeId) patch(it) else it }) }
    }

    /** A failed lookup is just a card left as it was; a cancellation must still stop the run. */
    private inline fun <T> attempt(block: () -> T): T? = try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

    /** Save the key, then ask Google about it; the page shows the probes running, then the verdict. */
    override fun saveKey(key: String, mapId: String) {
        val k = key.trim()
        if (k.isBlank()) return skipKey()
        if (_state.value.keyPhase == KeyPhase.Checking) return
        _state.update { it.copy(key = k, mapId = mapId.trim(), keyPhase = KeyPhase.Checking, keyReport = null, limitedAck = false) }
        viewModelScope.launch {
            val report = verifier.verify(k, mapId.trim())
            _state.update { it.copy(keyPhase = KeyPhase.Result, keyReport = report) }
        }
    }

    /** Settings → Key's "Re-check": the probes again on the key already saved. */
    override fun recheckKey() { saveKey(_state.value.key, _state.value.mapId) }

    /** No key: the limited-mode acknowledgement, with nothing to report. */
    override fun skipKey() { _state.update { it.copy(keyPhase = KeyPhase.Result, keyReport = null, limitedAck = false) } }

    /** Back to the field from a verdict. */
    override fun editKey() { _state.update { it.copy(keyPhase = KeyPhase.Entry) } }

    override fun setLimitedAck(on: Boolean) { _state.update { it.copy(limitedAck = on) } }

    private fun leaveKey() { _state.update { it.copy(keyPhase = KeyPhase.Entry, step = if (it.editing) Step.Done else Step.Name) } }

    /** Both probes passed: on to the questions (or back to the app). */
    override fun continueKey() { if (_state.value.keyReport?.accepted == true) leaveKey() }

    /** The user ticked "I understand" on limited maps: remember it and move on. */
    override fun acceptLimited() {
        if (!_state.value.limitedAck) return
        viewModelScope.launch {
            verifier.acceptLimited()
            leaveKey()
        }
    }

    override fun setName(s: String) { _state.update { it.copy(name = s.take(24)) } }

    private fun asHome(c: Candidate, from: Long) = Home(c.lat, c.lng, c.city ?: "", c.cc ?: "", from, null, c.label)

    override fun pickHome(c: Candidate) {
        _state.update { s ->
            val from = if (c.here) LocalDate.now(ZoneOffset.UTC).toEpochDay() * 86400L else s.range.first
            s.copy(homes = listOf(asHome(c, from)), units = if (s.editing) s.units else if (c.cc in MILES) Units.MI else Units.KM)
        }
    }

    /** Add a later stint; the home that was open until now closes the month this one begins. */
    override fun addHome(c: Candidate) {
        _state.update { s ->
            val last = s.homes.filter { c.matches(it) }.maxByOrNull { it.from }
            if (last != null && last.to == null) return@update s
            val mv = s.moves.firstOrNull { it.to == c.placeId }
            val from = last?.to ?: mv?.let { YearMonth.parse(it.month).epoch() } ?: s.range.first
            val closed = s.homes.map { h -> if (h.to == null && h.from < from) h.copy(to = from) else h }
            s.copy(homes = closed + asHome(c, from))
        }
    }

    override fun setHomeFrom(i: Int, m: YearMonth) { _state.update { s -> s.copy(homes = s.homes.mapIndexed { k, h -> if (k == i) h.copy(from = m.epoch()) else h }) } }
    override fun setHomeTo(i: Int, m: YearMonth?) { _state.update { s -> s.copy(homes = s.homes.mapIndexed { k, h -> if (k == i) h.copy(to = m?.epoch()) else h }) } }
    override fun removeHome(i: Int) { _state.update { s -> s.copy(homes = s.homes.filterIndexed { k, _ -> k != i }) } }

    override fun toggleWork(c: Candidate) {
        _state.update { s ->
            val on = s.works.any { it.placeId == c.placeId }
            s.copy(works = if (on) s.works.filter { it.placeId != c.placeId } else s.works + Work(c.lat, c.lng, c.placeId, c.label, 0, null))
        }
    }

    override fun setWorkFrom(i: Int, m: YearMonth) { _state.update { s -> s.copy(works = s.works.mapIndexed { k, w -> if (k == i) w.copy(from = m.epoch()) else w }) } }
    override fun setWorkTo(i: Int, m: YearMonth?) { _state.update { s -> s.copy(works = s.works.mapIndexed { k, w -> if (k == i) w.copy(to = m?.epoch()) else w }) } }
    override fun removeWork(i: Int) { _state.update { s -> s.copy(works = s.works.filterIndexed { k, _ -> k != i }) } }

    override fun setUnits(u: Units) { _state.update { it.copy(units = u) } }

    override fun next() {
        _state.update { s ->
            val i = s.questionIndex
            if (i < 0) s else if (i + 1 < QUESTIONS.size) s.copy(step = QUESTIONS[i + 1]) else s
        }
    }

    override fun back() { _state.update { s -> val i = s.questionIndex; if (i > 0) s.copy(step = QUESTIONS[i - 1]) else s } }

    /** Skip answers the question with nothing: work is cleared, the rest just moves on. */
    override fun skip() {
        if (_state.value.step == Step.Work) _state.update { it.copy(works = emptyList()) }
        next()
    }

    /** Sort homes by start and close each one where the next begins, then print. */
    override fun finish() {
        val s = _state.value
        val byFrom = s.homes.sortedBy { it.from }
        val sorted = byFrom.mapIndexed { k, h ->
            val nxt = byFrom.getOrNull(k + 1)?.from
            h.copy(to = if (h.to != null && nxt != null) minOf(h.to, nxt) else h.to ?: nxt)
        }
        val profile = Profile(s.name.trim().ifEmpty { null }, s.units, sorted, s.works)
        viewModelScope.launch {
            store.saveProfile(profile)
            _state.update { it.copy(step = Step.Done) }
        }
    }
}
