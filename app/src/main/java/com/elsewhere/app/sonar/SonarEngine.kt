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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Where the engine hands off to the phone: a heads-up for the person. */
interface SonarAlerts {
    fun nearby(friend: Friend)
}

/** A hit as the Map wants it: who, and when it landed on this phone (ms). */
data class SonarHit(val friend: Friend, val hitId: String, val at: Long)

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
) {
    private val hitsFlow = MutableSharedFlow<SonarHit>(extraBufferCapacity = 8)
    val hits: SharedFlow<SonarHit> = hitsFlow.asSharedFlow()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch { hub.messages.collect { m -> if (m is Message.Pinged) runCatching { evaluate(m) } } }
    }

    suspend fun evaluate(m: Message.Pinged): Decision {
        val p = m.ping
        if (p.grade == Grade.city) return Decision.Quiet
        val friend = store.friend(m.from) ?: return Decision.Quiet
        if (!friend.grants.sonar) return Decision.Quiet
        val now = clock()
        val share = store.shareNow()?.takeIf { it.active(now) && m.from in it.friendIds } ?: return Decision.Quiet
        val mine = db.rawFixes().latest()?.toSonarFix() ?: return Decision.Quiet
        val friendFix = Fix(p.t * 1000, p.lat, p.lng)
        val decision = withContext(Dispatchers.Default) {
            val history = dayRows(db.visits().since(now / 1000 - 30L * 86_400))
            val recent = db.rawFixes().since(now / 1000 - 6L * 3600).map { it.toSonarFix() }
            val me = mySide(mine, history, recent, friendFix, now)
            val them = Side(friendFix, p.habitat, p.absenceDays)
            decide(me, them, friend.pairId, now, store.debounce(listOf(friend.pairId)), p.hit?.id)
        }
        when (decision) {
            is Decision.Fire -> {
                store.recordHit(friend.pairId, now)
                publisher.carryHit(friend, Ping.Hit(decision.hitId, now / 1000))
                land(friend, decision.hitId, now)
            }
            is Decision.Echo -> { store.recordHit(friend.pairId, now); land(friend, decision.hitId, now) }
            Decision.Quiet -> Unit
        }
        return decision
    }

    private suspend fun land(friend: Friend, hitId: String, now: Long) {
        hitsFlow.emit(SonarHit(friend, hitId, now))
        if (!friend.muted(now)) alerts.nearby(friend)
    }

    /** Quiet until local midnight. */
    suspend fun muteToday(deviceId: String) {
        val now = clock()
        val midnight = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone()).plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zone()).toInstant().toEpochMilli()
        store.mute(deviceId, midnight)
    }

    suspend fun unmute(deviceId: String) = store.mute(deviceId, null)
}
