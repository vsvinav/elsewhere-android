package com.elsewhere.app.crew

import kotlinx.coroutines.flow.first
import java.util.Random

/** The other side of a pairing, shown for confirmation before anything is saved. */
data class Pending(val code: String, val record: PairRecord) {
    val name: String get() = record.name
    val fingerprint: String get() = Crypto.fingerprint(Crypto.unhex(record.signPub), Crypto.unhex(record.kxPub))
}

/**
 * The code dance: mint a code and register it, or claim a friend's, then confirm. Nothing is written until
 * [confirm]; the relay only learns public keys and a display name.
 */
class Pairing(
    private val store: CrewStore,
    private val relay: RelayClient,
    private val myName: suspend () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random(),
) {
    private suspend fun me(): PairRecord {
        val k = store.keys()
        return PairRecord(Crypto.hex(k.signPub), Crypto.hex(k.kxPub), myName(), Crypto.kxSig(k.signPriv, k.kxPub))
    }

    /** The code already open, or a fresh one registered with the relay. */
    suspend fun openCode(): ShareCode {
        val now = clock()
        store.code.first()?.takeIf { !it.expired(now) }?.let { return it }
        val code = ShareCode.create(random, now)
        relay.createCode(code.code, me())
        store.saveCode(code)
        return code
    }

    /** Whoever claimed my code, once someone has. */
    suspend fun poll(code: ShareCode): Pending? = relay.pollCode(code.code)?.let { it.requireBound(); Pending(code.code, it) }

    /** Claim a friend's code; the relay hands back who made it. */
    suspend fun claim(raw: String): Pending {
        val code = ShareCode.parse(raw) ?: throw IllegalArgumentException("That's not a pair code")
        return Pending(code, relay.claimCode(code, me()).also { it.requireBound() })
    }

    /** Save the friend, deriving the pair id and key; my open code is spent if it was the one used. */
    suspend fun confirm(p: Pending): Friend {
        p.record.requireBound()
        val k = store.keys()
        val theirSign = Crypto.unhex(p.record.signPub)
        val theirKx = Crypto.unhex(p.record.kxPub)
        val pairId = Crypto.pairId(k.signPub, theirSign)
        val pairKey = Crypto.pairKey(k.kxPriv, theirKx, pairId)
        val f = Friend(
            deviceId = Crypto.deviceId(theirSign), name = p.record.name.ifBlank { "Friend" },
            signPub = p.record.signPub, kxPub = p.record.kxPub, pairId = pairId, pairKey = Crypto.b64(pairKey), addedAt = clock(),
        )
        store.addFriend(f)
        if (store.code.first()?.code == p.code) store.saveCode(null)
        return f
    }
}

const val KEYS_UNBOUND = "Couldn't verify this friend's keys"

private fun PairRecord.requireBound() { if (!keysBound) throw SecurityException(KEYS_UNBOUND) }
