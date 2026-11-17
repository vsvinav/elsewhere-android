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

/** What Settings needs of sync; [SyncEngine] is the real one, tests hand in a fake. */
interface Syncer {
    val status: StateFlow<SyncStatus>
    fun syncNow()
    /** `activity` must be the foreground Activity: Drive may ask for consent over it. */
    suspend fun startFresh(activity: Context)
}

/**
 * Keeps this phone's record and the worker's copy in step. Pushes month chunks of the stays recorded here and of the
 * export imported here, plus the profile; pulls every other device's chunks as `sync` stays and the profile when
 * the worker's is newer. Rounds run at most every [minIntervalMs] unless asked for; a failed round asks [retry] to
 * come back when the network allows.
 */
class SyncEngine(
    private val context: Context,
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
) : Syncer {
    companion object {
        const val MIN_INTERVAL_MS = 15 * 60_000L
        /** Local bookkeeping rows, not worker keys: when the last round finished, and a fingerprint of the key it all was sealed under. */
        private const val LAST = "_last"
        private const val KEY = "_key"
    }

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Off)
    override val status: StateFlow<SyncStatus> = _status
    private val lock = Mutex()
    private var lastRun = 0L
    /** A round failed; the next nudge runs one whatever the interval. */
    private var pending = false

    init {
        scope.launch {
            val st = store.state.first()
            lastRun = if (st.session != null && st.driveKey != null) db.syncState().all().firstOrNull { it.key == LAST }?.ts ?: 0L else 0L
            _status.value = if (st.session != null && st.driveKey != null) SyncStatus.Idle(lastRun) else SyncStatus.Off
            combine(record.changes, record.profile, store.state) { c, p, s -> Triple(c, p, s) }.drop(1).collect { sync(force = false) }
        }
    }

    override fun syncNow() = sync(force = true)

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
        if (!force && !pending && now - lastRun < minIntervalMs) return
        record.ready.first { it }
        _status.value = SyncStatus.Syncing
        try {
            keyed(key)
            try { round(session, key, now) } catch (e: GeneralSecurityException) {
                val fresh = refetchKey() ?: throw e
                if (fresh.contentEquals(key)) throw e
                keyed(fresh)
                round(session, fresh, now)
            }
            lastRun = now; pending = false
            db.syncState().upsert(SyncStateEntity(LAST, now, null))
            _status.value = SyncStatus.Idle(now)
        } catch (e: GeneralSecurityException) {
            _status.value = SyncStatus.Error(SyncError.Locked)
        } catch (e: AccountException) {
            if (e.code == 401) { _status.value = SyncStatus.Off; return }
            pending = true; _status.value = SyncStatus.Error(SyncError.Server); retry()
        } catch (e: IOException) {
            pending = true; _status.value = SyncStatus.Error(SyncError.Network); retry()
        }
    }

    /** The bookkeeping only holds under the key it was made with: a different key (another device started fresh) means everything goes up again. */
    private suspend fun keyed(key: ByteArray) {
        val fp = Crypto.sha256hex(key)
        val held = db.syncState().all().firstOrNull { it.key == KEY }
        if (held != null && held.hash == fp) return
        if (held != null) { db.syncState().clear(); lastRun = 0L }
        db.syncState().upsert(SyncStateEntity(KEY, 0L, fp))
    }

    /** Drive asked once for the key as it is now; null when Drive would not answer or the vault under it will not open. */
    private suspend fun refetchKey(): ByteArray? = try { linker.refresh(context) } catch (e: Exception) { null }

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
        val editedAt = record.profileEditedAt().takeIf { it > 0 } ?: now
        val profileState = state[Wire.PROFILE]
        if (local != null) {
            val h = Crypto.sha256hex(Wire.profile(local, 0L).encode().toByteArray())
            if (profileState?.hash != h) {
                try {
                    api.put(session, Wire.PROFILE, Wire.seal(key, Wire.PROFILE, Wire.profile(local, editedAt).encode(), editedAt))
                    remember(state, SyncStateEntity(Wire.PROFILE, editedAt, h))
                    profilePushed = true
                } catch (e: AccountException) { if (e.code != 409) throw e }
            }
        }

        for (entry in api.list(session)) {
            val known = state[entry.key]
            if (known != null && known.ts >= entry.ts) continue
            if (entry.key == Wire.PROFILE) {
                if (profilePushed || (local != null && entry.ts <= editedAt)) continue
                val blob = api.get(session, entry.key) ?: continue
                val w = WireProfile.decode(Wire.open(key, entry.key, blob))
                val p = Wire.toProfile(w, record.profile.value)
                record.adoptProfile(p, entry.ts)
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

    /** A 409 means the worker holds a newer copy of our chunk: its ts is taken as ours and the chunk counts as pushed. */
    private suspend fun putFresh(session: String, key: ByteArray, k: String, plain: String, now: Long): Long {
        try { api.put(session, k, Wire.seal(key, k, plain, now)) } catch (e: AccountException) {
            if (e.code != 409) throw e
            return api.get(session, k)?.ts ?: now
        }
        return now
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
    override suspend fun startFresh(activity: Context) {
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
