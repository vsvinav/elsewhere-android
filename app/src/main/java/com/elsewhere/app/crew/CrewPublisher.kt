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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.elsewhere.app.crew.PublishPlanner.Companion.cellCentre
import com.elsewhere.app.ingest.record.Fix as RecordFix

/**
 * Turns the recorder's fixes into pings for whoever we are sharing with, and ends the share on time.
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
) {
    private var planner: PublishPlanner? = null
    private val lock = Mutex()
    private var job: Job? = null
    private var lastFix: RecordFix? = null
    private var selfAt = 0L
    private var self: Pair<Boolean, Int?> = false to null

    val share: Flow<ShareSession?> get() = store.share

    fun start() {
        if (job != null) return
        job = scope.launch { fixes.collect { onFix(it) } }
    }

    private suspend fun planner(): PublishPlanner = planner ?: PublishPlanner(runCatching { cities() }.getOrNull()).also { planner = it }

    suspend fun onFix(fix: RecordFix) = lock.withLock {
        lastFix = fix
        val now = clock()
        val session = store.shareNow() ?: return
        if (!session.active(now)) { endShare(session); return }
        publish(fix, session, now)
    }

    private suspend fun publish(fix: RecordFix, session: ShareSession, now: Long) {
        val (habitat, absence) = selfJudgement(fix, now)
        val my = MyFix(fix.ts, fix.lat, fix.lng, habitat, absence)
        val friends = store.friendsNow()
        for ((friend, ping) in planner().plan(my, friends, session, now)) hub.send(friend, ping.encode())
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
        val session = store.shareNow()?.takeIf { it.active(now) } ?: return@withLock
        publish(fix, session, now)
    }

    /**
     * A test hit goes straight to the friend's room, share or not, at Sonar's cell-level precision when a fix is known
     * and with no position at all when none is. It never touches the cadence or the debounce.
     */
    suspend fun carryTest(friend: Friend, hit: Ping.Hit) = lock.withLock {
        val fix = lastFix ?: db.rawFixes().latest()?.let { RecordFix(it.ts, it.lat, it.lng, it.accuracyM) }
        val (lat, lng) = fix?.let { cellCentre(it.lat, it.lng) } ?: (null to null)
        hub.send(friend, Ping(t = clock() / 1000, grade = Grade.sonar, lat = lat, lng = lng, hit = hit, test = true).encode())
    }

    /** Removing a friend tells them so, then forgets them here. */
    suspend fun revoke(friend: Friend) {
        hub.send(friend, REVOKE_JSON)
        store.removeFriend(friend.deviceId)
        hub.forget(friend.deviceId)
        planner().forget(friend.deviceId)
    }

    companion object {
        fun forApp(store: CrewStore, hub: RoomHub, recorder: Recorder, db: ElsewhereDb, cities: CitiesLoader, scope: CoroutineScope) =
            CrewPublisher(store, hub, recorder.fixes, db, { CityLookup(cities.get()) }, scope)
    }
}
