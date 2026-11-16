package com.elsewhere.app.crew

import com.elsewhere.app.sonar.FRESH_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** A friend's last known position as the Map draws it; `t` in epoch seconds, `receivedAt` in ms. */
data class FriendFix(val deviceId: String, val name: String, val lat: Double, val lng: Double, val city: String?, val grade: Grade, val t: Long, val receivedAt: Long) {
    fun stale(now: Long) = now - t * 1000 > STALE_MS
    fun gone(now: Long) = now - t * 1000 > DROP_MS
    companion object { const val STALE_MS = 15 * 60_000L; const val DROP_MS = 24 * 3_600_000L }
}

/** The sending half of the rooms, so publishers can be tested without sockets. */
interface Rooms {
    suspend fun send(friend: Friend, plaintext: String): Boolean
    fun forget(deviceId: String)
}

/**
 * One socket per friend while we want to listen — app in front or a share running — decrypting what
 * arrives off the main thread and keeping each friend's latest fix for the Map. Sending goes through the
 * same sockets; a revoke from the other side removes them here too.
 */
class RoomHub(
    private val store: CrewStore,
    private val relay: RelayClient,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : Rooms {
    private val messagesFlow = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val messages: SharedFlow<Message> = messagesFlow.asSharedFlow()

    private val fixesFlow = MutableStateFlow<Map<String, FriendFix>>(emptyMap())
    val fixes: StateFlow<Map<String, FriendFix>> = fixesFlow.asStateFlow()

    private val foregroundFlow = MutableStateFlow(false)
    private val sockets = HashMap<String, Conn>()
    private val lock = Mutex()
    /** Newest sender time accepted per friend: an older or repeated ping is a replay and is dropped. */
    private val lastT = HashMap<String, Long>()
    private var supervisor: Job? = null

    private inner class Conn(val friend: Friend) {
        var socket: WebSocket? = null
        var job: Job? = null
        var open = false
    }

    /** Call once at startup; follows friends, foreground and the share to decide which rooms to hold. */
    fun start() {
        if (supervisor != null) return
        supervisor = scope.launch {
            combine(store.friends, foregroundFlow, store.share) { friends, fg, share -> Triple(friends, fg, share) }
                .collect { (friends, fg, share) ->
                    val want = if (fg || share?.active(clock()) == true) friends else emptyList()
                    reconcile(want)
                }
        }
    }

    fun foreground(on: Boolean) { foregroundFlow.value = on }

    private suspend fun reconcile(want: List<Friend>) = lock.withLock {
        val wantIds = want.map { it.deviceId }.toSet()
        for (id in sockets.keys.toList()) if (id !in wantIds) sockets.remove(id)?.let { it.job?.cancel(); it.socket?.close(1000, null) }
        for (f in want) if (f.deviceId !in sockets) sockets[f.deviceId] = Conn(f).also { c -> c.job = scope.launch { hold(c) } }
    }

    private suspend fun hold(c: Conn) {
        var backoff = 5_000L
        while (scope.isActive && !relay.configured()) delay(30_000L)
        while (scope.isActive) {
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
            runCatching {
                c.socket = relay.openSocket(c.friend.pairId, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { c.open = true; backoff = 5_000L; scope.launch { seed(c.friend) } }
                    override fun onMessage(webSocket: WebSocket, text: String) { scope.launch { receive(c.friend, text) } }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { c.open = false; closed.complete(Unit) }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { c.open = false; closed.complete(Unit) }
                })
            }.onFailure { closed.complete(Unit) }
            closed.await()
            if (!scope.isActive) return
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    /** A late joiner takes the friend's last ping from the relay. */
    private suspend fun seed(friend: Friend) {
        runCatching { relay.last(friend.pairId) }.getOrNull()?.forEach { env -> if (env.from == friend.deviceId) handle(friend, env) }
    }

    internal suspend fun receive(friend: Friend, text: String) {
        val env = runCatching { Envelope.decode(text) }.getOrNull() ?: return
        if (env.from != friend.deviceId) return
        handle(friend, env)
    }

    private suspend fun handle(friend: Friend, env: Envelope) {
        val plain = withContext(Dispatchers.Default) { runCatching { Crypto.open(friend.key, friend.pairId, env) }.getOrNull() } ?: return
        when (val m = Message.parse(friend.deviceId, plain) ?: return) {
            is Message.Pinged -> {
                val p = m.ping
                if (!accept(friend.deviceId, p.t)) return
                if (p.lat != null && p.lng != null) fixesFlow.update { it + (friend.deviceId to FriendFix(friend.deviceId, friend.name, p.lat, p.lng, p.city, p.grade, p.t, clock())) }
                messagesFlow.emit(m)
            }
            is Message.Stopped -> { fixesFlow.update { it - friend.deviceId }; messagesFlow.emit(m) }
            is Message.Revoked -> { fixesFlow.update { it - friend.deviceId }; store.removeFriend(friend.deviceId); messagesFlow.emit(m) }
        }
    }

    private suspend fun accept(deviceId: String, t: Long): Boolean = lock.withLock {
        if (kotlin.math.abs(clock() - t * 1000) > FRESH_MS) return false
        if (t <= (lastT[deviceId] ?: Long.MIN_VALUE)) return false
        lastT[deviceId] = t
        true
    }

    /** Seal and send; opens the room first if we are not holding it. True when the socket took it. */
    override suspend fun send(friend: Friend, plaintext: String): Boolean {
        val me = store.keys()
        val env = withContext(Dispatchers.Default) { Crypto.seal(friend.key, friend.pairId, plaintext, me.deviceId) }
        val conn = lock.withLock { sockets[friend.deviceId] }
        val socket = conn?.socket?.takeIf { conn.open } ?: return runCatching { oneShot(friend, env.encode()) }.getOrDefault(false)
        return socket.send(env.encode())
    }

    private suspend fun oneShot(friend: Friend, text: String): Boolean {
        val sent = kotlinx.coroutines.CompletableDeferred<Boolean>()
        relay.openSocket(friend.pairId, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { sent.complete(webSocket.send(text)); webSocket.close(1000, null) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { sent.complete(false) }
        })
        return kotlinx.coroutines.withTimeoutOrNull(10_000L) { sent.await() } ?: false
    }

    /** Drop fixes nobody should see any more; the Map calls this on its own clock. */
    fun prune(now: Long) { fixesFlow.update { m -> m.filterValues { !it.gone(now) } } }

    override fun forget(deviceId: String) { fixesFlow.update { it - deviceId } }

    suspend fun friendsNow(): List<Friend> = store.friends.first()
}
