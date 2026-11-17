package com.elsewhere.app.sync

import android.content.Context
import com.elsewhere.app.account.AccountException
import com.elsewhere.app.account.AccountStorage
import com.elsewhere.app.account.Vault
import com.elsewhere.app.crew.Crypto
import com.elsewhere.app.crew.Identity
import com.elsewhere.app.data.RecordRepository
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.SOURCE_DEVICE
import com.elsewhere.app.data.db.SOURCE_IMPORT
import com.elsewhere.app.data.db.SOURCE_SYNC
import com.elsewhere.app.data.db.SyncStateEntity
import com.elsewhere.app.data.db.VisitEntity
import com.elsewhere.app.ingest.record.devicePlaceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import kotlin.math.roundToInt

/** What Settings prints. `Off` until there is a session and a Drive key; `Idle(0)` before the first round. */
sealed interface SyncStatus {
    data object Off : SyncStatus
    data class Idle(val lastTs: Long) : SyncStatus
    data object Syncing : SyncStatus
    data class Error(val kind: SyncError) : SyncStatus
}

/** `Locked`: a blob on the worker would not open under our Drive key; only Start fresh gets out of it. */
enum class SyncError { Network, Locked, Server }

/**
 * Keeps this phone's record and the worker's copy in step. Pushes month chunks of the stays recorded here and of the
 * export imported here, plus the profile; pulls every other device's chunks as `sync` stays and the profile when
 * the worker's is newer. Rounds run at most every [minIntervalMs] unless asked for; a failed round asks [retry] to
 * come back when the network allows.
 */
class SyncEngine(
    private val api: SyncApi,
    private val db: ElsewhereDb,
    private val store: AccountStorage,
    private val record: RecordRepository,
    private val identity: Identity,
    private val linker: DriveLinker,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retry: () -> Unit = {},
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
) {
    companion object {
        const val MIN_INTERVAL_MS = 15 * 60_000L
        /** Local bookkeeping row: when the last round finished; not a worker key. */
        private const val LAST = "_last"
    }

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Off)
    val status: StateFlow<SyncStatus> = _status
    private val lock = Mutex()
    private var lastRun = 0L

    init {
        scope.launch {
            val st = store.state.first()
            lastRun = if (st.session != null && st.driveKey != null) db.syncState().all().firstOrNull { it.key == LAST }?.ts ?: 0L else 0L
            _status.value = if (st.session != null && st.driveKey != null) SyncStatus.Idle(lastRun) else SyncStatus.Off
            combine(record.changes, record.profile, store.state) { c, p, s -> Triple(c, p, s) }.drop(1).collect { sync(force = false) }
        }
    }

    fun syncNow() = sync(force = true)

    /** The app came to the front: a round if the last was long enough ago. */
    fun foreground() = sync(force = false)

    private fun sync(force: Boolean) { scope.launch { run(force) } }

    /** One round at a time; a round asked for while one runs waits its turn rather than being dropped. */
    suspend fun run(force: Boolean): Unit = lock.withLock {
        val st = store.state.first()
        val session = st.session?.session
        val key = st.driveKey?.let(Vault::unb64)
        if (session == null || key == null) { _status.value = SyncStatus.Off; return }
        if (_status.value is SyncStatus.Error && (_status.value as SyncStatus.Error).kind == SyncError.Locked && !force) return
        val now = clock()
        if (!force && now - lastRun < minIntervalMs) return
        _status.value = SyncStatus.Syncing
        try {
            round(session, key, now)
            lastRun = now
            db.syncState().upsert(SyncStateEntity(LAST, now, null))
            _status.value = SyncStatus.Idle(now)
        } catch (e: GeneralSecurityException) {
            _status.value = SyncStatus.Error(SyncError.Locked)
        } catch (e: AccountException) {
            if (e.code == 401) { _status.value = SyncStatus.Off; return }
            _status.value = SyncStatus.Error(SyncError.Server); retry()
        } catch (e: IOException) {
            _status.value = SyncStatus.Error(SyncError.Network); retry()
        }
    }

    private suspend fun round(session: String, key: ByteArray, now: Long) {
        val state = db.syncState().all().associateBy { it.key }.toMutableMap()
        val mine = ownSources()
        val names = db.placeEnrichment().all().associate { it.placeId to it.name }
        var profilePushed = false

        for ((source, visits) in mine) {
            val byMonth = visits.groupBy { Wire.monthOf(it.start) }
            for ((month, stays) in byMonth) {
                val k = Wire.chunkKey(source, month)
                val plain = MonthChunk(stays = stays.map { WireStay(Wire.stayId(source, it.start), it.lat, it.lng, names[it.placeId], it.start, it.end) }.sortedWith(compareBy({ it.start }, { it.id }))).encode()
                val h = Crypto.sha256hex(plain.toByteArray())
                if (state[k]?.hash == h) continue
                val ts = putFresh(session, key, k, plain, now)
                remember(state, SyncStateEntity(k, ts, h))
            }
        }

        val local = record.profile.value
        val profileState = state[Wire.PROFILE]
        if (local != null) {
            val h = Crypto.sha256hex(Wire.profile(local, 0L).encode().toByteArray())
            if (profileState?.hash != h) {
                try {
                    val ts = maxOf(now, (profileState?.ts ?: 0L) + 1)
                    api.put(session, Wire.PROFILE, Wire.seal(key, Wire.PROFILE, Wire.profile(local, ts).encode(), ts))
                    remember(state, SyncStateEntity(Wire.PROFILE, ts, h))
                    profilePushed = true
                } catch (e: AccountException) { if (e.code != 409) throw e }
            }
        }

        for (entry in api.list(session)) {
            val known = state[entry.key]
            if (known != null && known.ts >= entry.ts) continue
            if (entry.key == Wire.PROFILE) {
                if (profilePushed) continue
                val blob = api.get(session, entry.key) ?: continue
                val w = WireProfile.decode(Wire.open(key, entry.key, blob))
                val p = Wire.toProfile(w, record.profile.value)
                record.saveProfile(p)
                remember(state, SyncStateEntity(Wire.PROFILE, entry.ts, Crypto.sha256hex(Wire.profile(p, 0L).encode().toByteArray())))
                continue
            }
            val source = entry.key.substringBefore(':')
            if (source in mine.keys) continue
            val blob = api.get(session, entry.key) ?: continue
            val chunk = MonthChunk.decode(Wire.open(key, entry.key, blob))
            val rows = chunk.stays.map { s -> VisitEntity(localId(s.id), s.start, s.end, tzGuess(s.lng), devicePlaceId(s.lat, s.lng), s.lat, s.lng, "UNKNOWN", 1.0, 0, null, null, null, SOURCE_SYNC) }
            record.insertSynced(rows, chunk.stays.mapNotNull { s -> s.name?.let { devicePlaceId(s.lat, s.lng) to it } }.toMap())
            remember(state, SyncStateEntity(entry.key, entry.ts, null))
        }
    }

    /** Our own chunk is authoritative: a 409 only means another copy of this device pushed later, so it goes up again, newer. */
    private suspend fun putFresh(session: String, key: ByteArray, k: String, plain: String, now: Long): Long {
        var ts = now
        try { api.put(session, k, Wire.seal(key, k, plain, ts)) } catch (e: AccountException) {
            if (e.code != 409) throw e
            ts = maxOf(now, (api.get(session, k)?.ts ?: 0L) + 1)
            api.put(session, k, Wire.seal(key, k, plain, ts))
        }
        return ts
    }

    private suspend fun remember(state: MutableMap<String, SyncStateEntity>, row: SyncStateEntity) { state[row.key] = row; db.syncState().upsert(row) }

    /** The stays this phone is responsible for, by wire source: what it recorded and the export it imported. */
    private suspend fun ownSources(): Map<String, List<VisitEntity>> {
        val out = LinkedHashMap<String, List<VisitEntity>>()
        out[Wire.deviceSource(identity.keys().deviceId)] = db.visits().bySource(SOURCE_DEVICE)
        db.importMeta().get()?.takeIf { it.sourceSha256.isNotBlank() }?.let { out[Wire.importSource(it.sourceSha256)] = db.visits().bySource(SOURCE_IMPORT) }
        return out
    }

    /**
     * Start fresh: the worker's copy and the Drive key go, a new key is made and the vault re-sealed under it, this
     * phone forgets what it had pushed and sends its record again. Stays already on the phone stay.
     */
    suspend fun startFresh(activity: Context) {
        val session = store.state.first().session?.session ?: return
        _status.value = SyncStatus.Syncing
        try {
            api.deleteAll(session)
            linker.replace(activity)
            db.syncState().clear()
            lastRun = 0L
        } catch (e: IOException) { _status.value = SyncStatus.Error(SyncError.Network); retry(); return }
        run(force = true)
    }

    private fun localId(stayId: String): Int = ByteBuffer.wrap(Crypto.unhex(stayId.take(8))).int

    /** Whole hours from the longitude: close enough to place a stay on its local day. */
    private fun tzGuess(lng: Double): Int = (lng / 15.0).roundToInt() * 60
}
