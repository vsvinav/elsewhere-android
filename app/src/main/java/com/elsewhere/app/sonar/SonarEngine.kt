package com.elsewhere.app.sonar

import com.elsewhere.app.crew.CrewPublisher
import com.elsewhere.app.crew.CrewStore
import com.elsewhere.app.crew.Friend
import com.elsewhere.app.crew.Grade
import com.elsewhere.app.crew.Message
import com.elsewhere.app.crew.Ping
import com.elsewhere.app.crew.RoomHub
import com.elsewhere.app.data.db.ElsewhereDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Where the engine hands off to the phone: a heads-up for the person. */
interface SonarAlerts {
    /** [test] marks a hit fired on purpose; [sent] when it was this phone that fired it. */
    fun nearby(friend: Friend, test: Boolean = false, sent: Boolean = false)
}

/** A hit as the Map wants it: who, when it landed on this phone (ms), and whether it was fired on purpose. */
data class SonarHit(val friend: Friend, val hitId: String, val at: Long, val test: Boolean = false, val rehearsal: Boolean = false)

/** The one Sonar sheet the app raises over whatever is on screen; the engine is the real one, tests pass a stand-in. */
interface SonarSheets {
    val sheet: StateFlow<SonarHit?>
    /** Dismiss puts the sheet away and keeps this friend's sheets quiet for a while. */
    fun dismissSheet()
    /** Snap from the sheet: the hit is remembered as seen, but the sheet stays up until Dismiss, as the camera may take a while. */
    fun sheetSnapped()

    object None : SonarSheets {
        override val sheet: StateFlow<SonarHit?> = MutableStateFlow(null)
        override fun dismissSheet() = Unit
        override fun sheetSnapped() = Unit
    }
}

/**
 * Listens to the rooms and runs [decide] once per incoming ping — never on a timer, never per frame.
 * Fire posts the alert and sends the hit straight back; Echo mirrors a friend's hit so both phones agree.
 */
class SonarEngine(
    private val store: CrewStore,
    private val hub: RoomHub,
    private val publisher: CrewPublisher,
    private val db: ElsewhereDb,
    private val alerts: SonarAlerts,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** When Sonar rehearsal lapses (epoch ms); 0 or past means off. */
    private val rehearsalUntil: suspend () -> Long = { 0L },
) : SonarSheets {
    private val hitsFlow = MutableSharedFlow<SonarHit>(extraBufferCapacity = 8)
    val hits: SharedFlow<SonarHit> = hitsFlow.asSharedFlow()
    private val sheetFlow = MutableStateFlow<SonarHit?>(null)
    override val sheet: StateFlow<SonarHit?> = sheetFlow
    /** Per friend, when a dismissed sheet may be followed by another. */
    private val sheetQuietUntil = HashMap<String, Long>()
    /** Per pair, the last rehearsal hit acted on; rehearsal pings arrive every 30 s and must not each ring. */
    private val rehearsalFiredAt = HashMap<String, Long>()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch { hub.messages.collect { m -> if (m is Message.Pinged) runCatching { evaluate(m) } } }
    }

    /** Hit ids already acted on; a ping that carries one again (a replay, or our own id reflected) carries nothing. */
    private val seenHits = object : LinkedHashMap<String, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > SEEN_HITS
    }
    private val seenLock = Mutex()

    private suspend fun freshHit(hit: Ping.Hit?): String? = seenLock.withLock {
        val id = hit?.id ?: return null
        if (seenHits.put(id, Unit) != null) null else id
    }

    suspend fun evaluate(m: Message.Pinged): Decision {
        val p = m.ping
        val incomingHit = freshHit(p.hit)
        if (p.grade == Grade.city) return Decision.Quiet
        val friend = store.friend(m.from) ?: return Decision.Quiet
        if (!friend.grants.sonar) return Decision.Quiet
        val now = clock()
        if (p.test) return decideTest(incomingHit).also { if (it is Decision.Echo) land(friend, it.hitId, now, test = true) }
        if (p.lat == null || p.lng == null) return Decision.Quiet
        val mine = db.rawFixes().latest()?.toSonarFix() ?: return Decision.Quiet
        val friendFix = Fix(p.t * 1000, p.lat, p.lng)
        val rehearsal = rehearsalUntil() > now
        val decision = withContext(Dispatchers.Default) {
            val history = dayRows(db.visits().since(now / 1000 - 30L * 86_400))
            val recent = db.rawFixes().since(now / 1000 - 6L * 3600).map { it.toSonarFix() }
            val me = mySide(mine, history, recent, friendFix, now)
            val them = Side(friendFix, p.habitat, p.absenceDays)
            decide(me, them, friend.pairId, now, store.debounce(listOf(friend.pairId)), incomingHit, rehearsal)
        }
        if (rehearsal && decision !is Decision.Quiet) {
            val last = rehearsalFiredAt[friend.pairId]
            if (last != null && now - last < REHEARSAL_GAP_MS) return Decision.Quiet
            rehearsalFiredAt[friend.pairId] = now
        }
        when (decision) {
            is Decision.Fire -> {
                seenLock.withLock { seenHits[decision.hitId] = Unit }
                store.recordHit(friend.pairId, now)
                publisher.carryHit(friend, Ping.Hit(decision.hitId, now / 1000, rehearsal))
                land(friend, decision.hitId, now, rehearsal = rehearsal)
            }
            // The firing phone says whether it was rehearsing; both sheets then read the same.
            is Decision.Echo -> { store.recordHit(friend.pairId, now); land(friend, decision.hitId, now, rehearsal = p.hit?.rehearsal == true) }
            Decision.Quiet -> Unit
        }
        return decision
    }

    private suspend fun land(friend: Friend, hitId: String, now: Long, test: Boolean = false, sent: Boolean = false, rehearsal: Boolean = false) {
        val hit = SonarHit(friend, hitId, now, test, rehearsal)
        hitsFlow.emit(hit)
        if (friend.muted(now)) return
        alerts.nearby(friend, test, sent)
        raiseSheet(hit, now)
    }

    /** One sheet at a time, never swapped under the person, and none for a friend whose sheet was just put away. */
    private suspend fun raiseSheet(hit: SonarHit, now: Long) {
        if (sheetFlow.value != null) return
        if ((sheetQuietUntil[hit.friend.deviceId] ?: 0L) > now) return
        if (hit.hitId in store.sheetSeen()) return
        sheetFlow.value = hit
    }

    override fun dismissSheet() {
        val h = sheetFlow.value ?: return
        sheetFlow.value = null
        sheetQuietUntil[h.friend.deviceId] = clock() + SHEET_QUIET_MS
        scope.launch { store.markSheetSeen(h.hitId) }
    }

    override fun sheetSnapped() {
        val h = sheetFlow.value ?: return
        scope.launch { store.markSheetSeen(h.hitId) }
    }

    /** Fire on purpose: ring here as if [decide] had fired, and send the hit to the friend, who echoes it the same way. */
    suspend fun testFire(deviceId: String): String? {
        val friend = store.friend(deviceId)?.takeIf { it.grants.sonar } ?: return null
        val now = clock()
        val id = ByteArray(4).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        seenLock.withLock { seenHits[id] = Unit }
        land(friend, id, now, test = true, sent = true)
        publisher.carryTest(friend, Ping.Hit(id, now / 1000))
        return id
    }

    /** Quiet until local midnight. */
    suspend fun muteToday(deviceId: String) {
        val now = clock()
        val midnight = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zone()).toInstant().toEpochMilli()
        store.mute(deviceId, midnight)
    }

    suspend fun unmute(deviceId: String) = store.mute(deviceId, null)

    companion object {
        const val SEEN_HITS = 64
        /** In rehearsal, one hit per pair per this long. */
        const val REHEARSAL_GAP_MS = 10 * 60_000L
        /** After a dismiss, no sheet for that friend for this long. */
        const val SHEET_QUIET_MS = 5 * 60_000L
    }
}
