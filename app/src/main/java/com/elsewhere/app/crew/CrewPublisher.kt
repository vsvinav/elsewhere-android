package com.elsewhere.app.crew

import com.elsewhere.app.data.CitiesLoader
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.record.Recorder
import com.elsewhere.app.sonar.Fix
import com.elsewhere.app.sonar.absenceDays
import com.elsewhere.app.sonar.dayRows
import com.elsewhere.app.sonar.isHabitat
import com.elsewhere.app.sonar.stintStart
import com.elsewhere.app.sonar.toSonarFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.elsewhere.app.ingest.record.Fix as RecordFix

/**
 * Turns the recorder's fixes into pings for every friend with a grant, and ends a timed share on time.
 * Habitat and absence around my own spot are recomputed at most every 30 s, from visits and recent fixes.
 */
class CrewPublisher(
    private val store: CrewStore,
    private val hub: Rooms,
    private val fixes: Flow<RecordFix>,
    private val db: ElsewhereDb,
    private val cities: suspend () -> CityLookup?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private var planner: PublishPlanner? = null
    private val lock = Mutex()
    private var job: Job? = null
    private var lastFix: RecordFix? = null
    private var selfAt = 0L
    private var demandAt: Long? = null
    /** When each friend's room last took a ping from us; the relay drops anything sooner than [RELAY_GAP_MS] after. */
    private val sentAt = HashMap<String, Long>()
    /** Pings waiting out the relay's gap, newest fix wins and a hit is kept until it has gone. */
    private val held = HashMap<String, Ping>()
    private var self: Pair<Boolean, Int?> = false to null

    val share: Flow<ShareSession?> get() = store.share

    fun start() {
        if (job != null) return
        job = scope.launch { fixes.collect { onFix(it) } }
    }

    private suspend fun planner(): PublishPlanner = planner ?: PublishPlanner(runCatching { cities() }.getOrNull()).also { planner = it }

    suspend fun onFix(fix: RecordFix) = lock.withLock { publishNow(fix, force = false) }

    /**
     * A fix asked for on purpose, when a tab that shows the crew comes into view: fetched at most once a minute and
     * published straight away, outside the cadence. A fetch that comes back empty leaves the minute open.
     */
    suspend fun demand(fresh: suspend () -> RecordFix?) {
        val asked = clock()
        val before = demandAt
        if (before?.let { asked - it < DEMAND_MS } == true) return
        // Claim the minute before asking, so two tabs coming into view together ask the phone once.
        demandAt = asked
        val fix = fresh() ?: run { if (demandAt == asked) demandAt = before; return }
        lock.withLock { publishNow(fix, force = true) }
    }

    private suspend fun publishNow(fix: RecordFix, force: Boolean) {
        lastFix = fix
        val now = clock()
        val session = store.shareNow()
        if (session != null && !session.active(now)) endShare(session)
        publish(fix, session?.takeIf { it.active(now) }, now, force)
    }

    private suspend fun publish(fix: RecordFix, session: ShareSession?, now: Long, force: Boolean = false) {
        val (habitat, absence) = selfJudgement(fix, now)
        val my = MyFix(fix.ts, fix.lat, fix.lng, habitat, absence)
        val friends = store.friendsNow()
        for ((friend, ping) in planner().plan(my, friends, session, now, force)) send(friend, ping, now)
    }

    /** Under [lock]. A ping inside the relay's gap is held and sent when the gap has passed, so a hit is never dropped. */
    private suspend fun send(friend: Friend, ping: Ping, now: Long) {
        val id = friend.deviceId
        val last = sentAt[id]
        if (last != null && now - last < RELAY_GAP_MS) {
            val waiting = held.put(id, ping.copy(hit = ping.hit ?: held[id]?.hit))
            if (waiting == null) scope.launch { sleep(last + RELAY_GAP_MS - now); lock.withLock { release(friend) } }
            return
        }
        hub.send(friend, ping.encode())
        sentAt[id] = now
    }

    private suspend fun release(friend: Friend) {
        val ping = held.remove(friend.deviceId) ?: return
        hub.send(friend, ping.encode())
        sentAt[friend.deviceId] = clock()
    }

    private suspend fun selfJudgement(fix: RecordFix, now: Long): Pair<Boolean, Int?> {
        if (now - selfAt < PublishPlanner.LIVE_MS) return self
        val at = Fix(fix.ts * 1000, fix.lat, fix.lng)
        val history = dayRows(db.visits().since(fix.ts - 30L * 86_400))
        val mine = db.rawFixes().since(fix.ts - 6L * 3600).map { it.toSonarFix() }
        self = isHabitat(history, at, now) to absenceDays(history, at, stintStart(mine, at))
        selfAt = now
        return self
    }

    suspend fun startShare(friendIds: Set<String>, length: ShareLength) {
        val now = clock()
        store.startShare(ShareSession(friendIds, now, length.until(now)))
        planner().reset()
        lastFix?.let { f -> lock.withLock { store.shareNow()?.let { publish(f, it, now) } } }
    }

    suspend fun stopShare() = lock.withLock { store.shareNow()?.let { endShare(it) } }

    private suspend fun endShare(session: ShareSession) {
        store.stopShare()
        planner().reset()
        for (f in store.friendsNow()) if (f.deviceId in session.friendIds) hub.send(f, STOP_JSON)
    }

    /** A Sonar hit rides on an immediate ping, outside the normal cadence. */
    suspend fun carryHit(friend: Friend, hit: Ping.Hit) = lock.withLock {
        planner().carryHit(friend.deviceId, hit)
        val fix = lastFix ?: db.rawFixes().latest()?.let { RecordFix(it.ts, it.lat, it.lng, it.accuracyM) } ?: return@withLock
        val now = clock()
        publish(fix, store.shareNow()?.takeIf { it.active(now) }, now)
    }

    /** A test hit goes straight to the friend's room, share or not, and never with a position: nothing is placed or recorded. */
    suspend fun carryTest(friend: Friend, hit: Ping.Hit) = lock.withLock {
        hub.send(friend, Ping(t = clock() / 1000, grade = Grade.sonar, hit = hit, test = true).encode())
    }

    /** Removing a friend tells them so, then forgets them here. */
    suspend fun revoke(friend: Friend) {
        hub.send(friend, REVOKE_JSON)
        store.removeFriend(friend.deviceId)
        hub.forget(friend.deviceId)
        planner().forget(friend.deviceId)
        lock.withLock { held.remove(friend.deviceId); sentAt.remove(friend.deviceId) }
    }

    companion object {
        const val DEMAND_MS = 60_000L
        /** The relay takes one ping per room per ten seconds. */
        const val RELAY_GAP_MS = 10_000L

        fun forApp(store: CrewStore, hub: RoomHub, recorder: Recorder, db: ElsewhereDb, cities: CitiesLoader, scope: CoroutineScope) =
            CrewPublisher(store, hub, recorder.fixes, db, { CityLookup(cities.get()) }, scope)
    }
}
