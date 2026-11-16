package com.elsewhere.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.crew.CrewPublisher
import com.elsewhere.app.crew.CrewStore
import com.elsewhere.app.crew.Friend
import com.elsewhere.app.crew.FriendFix
import com.elsewhere.app.crew.Grants
import com.elsewhere.app.crew.Pairing
import com.elsewhere.app.crew.Pending
import com.elsewhere.app.crew.RoomHub
import com.elsewhere.app.crew.ShareCode
import com.elsewhere.app.crew.ShareLength
import com.elsewhere.app.crew.ShareSession
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.record.RecordControl
import com.elsewhere.app.sonar.SonarEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the pairing bill is doing, so the screen can say so plainly. */
sealed interface PairState {
    data object Idle : PairState
    data object Working : PairState
    data class Confirm(val pending: Pending) : PairState
    data class Failed(val message: String) : PairState
}

/** Everything the Crew screen prints; null in the view model until the stores have been read. */
data class CrewUi(
    val friends: List<Friend>,
    val code: ShareCode?,
    val pair: PairState,
    val share: ShareSession?,
    val relaySet: Boolean,
    val recording: Recording,
    val fixes: Map<String, FriendFix>,
    val now: Long,
) {
    val sharing: Boolean get() = share?.active(now) == true
    fun sharingWith(f: Friend) = share?.takeIf { it.active(now) }?.friendIds?.contains(f.deviceId) == true
    /** Friends the share sheet lists: anyone allowed to see something live. */
    val shareable: List<Friend> get() = friends.filter { it.grants.liveCity || it.grants.livePrecise || it.grants.sonar }
}

/** Everything the Crew screen can do; the view model is the real one, snapshots pass a no-op. */
interface CrewActions {
    fun newCode()
    fun claim(raw: String)
    fun confirmPair()
    fun dismissPair()
    fun setGrants(deviceId: String, grants: Grants)
    fun remove(deviceId: String)
    fun muteToday(deviceId: String)
    fun unmute(deviceId: String)
    fun startShare(friendIds: Set<String>, length: ShareLength)
    fun stopShare()
    fun startRecording()
}

@HiltViewModel
class CrewViewModel @Inject constructor(
    private val store: CrewStore,
    private val pairing: Pairing,
    private val publisher: CrewPublisher,
    private val hub: RoomHub,
    private val engine: SonarEngine,
    settings: SettingsStore,
    private val record: RecordControl,
) : ViewModel(), CrewActions {
    var clock: () -> Long = System::currentTimeMillis
    var io: CoroutineDispatcher = Dispatchers.IO
    /** How often the owner's open code asks the relay whether someone has claimed it. */
    var pollMs: Long = 5_000L

    private val pair = MutableStateFlow<PairState>(PairState.Idle)
    private val tick = MutableStateFlow(clock())
    private var poll: Job? = null
    private val watching = MutableStateFlow(false)

    /** The Map's Friends layer, and which stamp is open. */
    val layer = MutableStateFlow(false)
    val selected = MutableStateFlow<String?>(null)
    /** Friends whose Sonar hit is still ringing on the map: evaluated once per ping, cleared after [RING_MS]. */
    private val hitsFlow = MutableStateFlow<Set<String>>(emptySet())
    val hits: StateFlow<Set<String>> = hitsFlow

    val ui: StateFlow<CrewUi?> = combine(
        combine(store.friends, store.code, pair) { f, c, p -> Triple(f, c, p) },
        combine(store.share, settings.settings.map { it.relayUrl.isNotBlank() }, record.recording) { s, r, rec -> Triple(s, r, rec) },
        hub.fixes, tick,
    ) { (friends, code, p), (share, relay, rec), fixes, now ->
        CrewUi(friends, code?.takeIf { !it.expired(now) }, p, share, relay, rec, fixes, now)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { while (isActive) { delay(30_000L); tick.value = clock() } }
        viewModelScope.launch {
            // An open code is only useful if we notice the claim; while the Crew screen shows one, ask the relay.
            combine(store.code, watching) { c, w -> c?.takeIf { w } }.collect { code -> poll?.cancel(); poll = if (code != null) launch { pollClaim(code) } else null }
        }
        viewModelScope.launch {
            engine.hits.collect { h ->
                hitsFlow.update { it + h.friend.deviceId }
                launch { delay(RING_MS.toLong() + 300); hitsFlow.update { it - h.friend.deviceId } }
            }
        }
    }

    fun setLayer(on: Boolean) { layer.value = on; if (!on) selected.value = null }
    fun select(id: String?) { selected.value = id }

    private suspend fun pollClaim(code: ShareCode) {
        while (currentCoroutineContext().isActive && !code.expired(clock())) {
            if (pair.value is PairState.Idle) {
                val claimed = withContext(io) { runCatching { pairing.poll(code) }.getOrNull() }
                if (claimed != null) { pair.value = PairState.Confirm(claimed); return }
            }
            delay(pollMs)
        }
    }

    override fun newCode() {
        viewModelScope.launch {
            withContext(io) { runCatching { store.saveCode(null); pairing.openCode() } }
                .onFailure { pair.value = PairState.Failed(it.message ?: "The relay didn't answer") }
            tick.value = clock()
        }
    }

    /** Mint a code the first time the bill is shown; later visits reuse the open one. */
    fun ensureCode() {
        watching.value = true
        viewModelScope.launch {
            if (store.code.first()?.takeIf { !it.expired(clock()) } == null) withContext(io) { runCatching { pairing.openCode() } }
        }
    }

    override fun claim(raw: String) {
        if (ShareCode.parse(raw) == null) { pair.value = PairState.Failed("That's not a pair code"); return }
        pair.value = PairState.Working
        viewModelScope.launch {
            pair.value = withContext(io) { runCatching { pairing.claim(raw) } }
                .fold({ PairState.Confirm(it) }, { PairState.Failed(it.message ?: "The relay didn't answer") })
        }
    }

    override fun confirmPair() {
        val p = (pair.value as? PairState.Confirm)?.pending ?: return
        pair.value = PairState.Working
        viewModelScope.launch {
            withContext(Dispatchers.Default) { runCatching { pairing.confirm(p) } }
                .onSuccess { pair.value = PairState.Idle }
                .onFailure { pair.value = PairState.Failed(it.message ?: "Couldn't save that friend") }
        }
    }

    override fun dismissPair() { pair.value = PairState.Idle }

    override fun setGrants(deviceId: String, grants: Grants) { viewModelScope.launch { store.setGrants(deviceId, grants) } }

    override fun remove(deviceId: String) {
        viewModelScope.launch {
            val f = store.friend(deviceId) ?: return@launch
            withContext(io) { publisher.revoke(f) }
        }
    }

    override fun muteToday(deviceId: String) { viewModelScope.launch { engine.muteToday(deviceId) } }
    override fun unmute(deviceId: String) { viewModelScope.launch { engine.unmute(deviceId) } }

    override fun startShare(friendIds: Set<String>, length: ShareLength) {
        viewModelScope.launch { withContext(io) { publisher.startShare(friendIds, length) }; tick.update { clock() } }
    }

    override fun stopShare() { viewModelScope.launch { withContext(io) { publisher.stopShare() } } }

    override fun startRecording() { viewModelScope.launch { record.start() } }
}
